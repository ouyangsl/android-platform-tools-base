/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.adblib.tools.debugging.impl

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.adbLogger
import com.android.adblib.scope
import com.android.adblib.tools.debugging.AtomicStateFlow
import com.android.adblib.tools.debugging.JdwpProcessProperties
import com.android.adblib.tools.debugging.SharedJdwpSession
import com.android.adblib.tools.debugging.appProcessTracker
import com.android.adblib.tools.debugging.externalJdwpProcessPropertiesCollectorFactoryList
import com.android.adblib.tools.debugging.jdwpProcessTracker
import com.android.adblib.tools.debugging.utils.logIOCompletionErrors
import com.android.adblib.withPrefix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Implementation of [AbstractJdwpProcess] performing the actual JDWP connection.
 *
 * Note: The [close] method is called by [appProcessTracker] or [jdwpProcessTracker] when
 * the process is terminated, or when the [ConnectedDevice.scope] completes.
 */
internal class JdwpProcessImpl(
    override val device: ConnectedDevice,
    override val pid: Int
) : AbstractJdwpProcess() {

    private val processDescription = "${device.session} - $device - pid=$pid"

    private val logger = adbLogger(device.session).withPrefix("$processDescription - ")

    private val session: AdbSession
        get() = device.session

    private val stateFlow = AtomicStateFlow(MutableStateFlow(JdwpProcessProperties(pid)))

    override val cache = CoroutineScopeCache.create(device.scope, processDescription)

    override val propertiesFlow = stateFlow.asStateFlow()

    /**
     * Provides concurrent and on-demand access to the `jdwp` session of the device.
     *
     * We use a [SharedJdwpSessionProvider] to ensure only one session is created at a time,
     * while at the same time allowing multiple consumers to access the jdwp session concurrently.
     *
     * We currently have 2 consumers:
     * * A [JdwpProcessPropertiesCollector] that opens a jdwp session for a few seconds to collect
     *   the process properties (package name, process name, etc.)
     * * A [JdwpSessionProxy] that opens a jdwp session "on demand" when a Java debugger wants
     *   to connect to the process on the device.
     *
     * Typically, both consumers don't overlap, but if a debugger tries to attach to the process
     * just after its creation, before we are done collecting properties, the [JdwpSessionProxy]
     * ends up trying to open a jdwp session before [JdwpProcessPropertiesCollector] is done
     * collecting process properties. When this happens, we open a single JDWP connection that
     * is used for collecting process properties and for a debugging session. The connection
     * lasts until the debugging session ends.
     */
    private val sharedJdwpSessionProvider = SharedJdwpSessionProvider.create(device, pid)

    private val propertyCollector = JdwpProcessPropertiesCollector(device, scope, pid, sharedJdwpSessionProvider)

    private val jdwpSessionProxy = JdwpSessionProxy(device, pid, sharedJdwpSessionProvider)

    private val lazyStartMonitoring by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        logger.debug { "Start monitoring" }

        scope.launch(session.ioDispatcher) {
            runCatching {
                jdwpSessionProxy.execute(stateFlow)
            }.onFailure { throwable ->
                logger.logIOCompletionErrors(throwable)
            }
        }

        val localCollectorJob = scope.launch(session.ioDispatcher) {
            runCatching {
                propertyCollector.execute(stateFlow)
            }.onFailure { throwable ->
                logger.logIOCompletionErrors(throwable)
            }
        }

        // Launch external collectors (e.g. out of process inventory) if available
        scope.launch(session.ioDispatcher) {
            session.externalJdwpProcessPropertiesCollectorFactoryList.mapNotNull { factory ->
                factory.create(this@JdwpProcessImpl)
            }.forEach { externalCollector ->
                runCatching {
                    val handler = ExternalPropertiesCollectorHandler(
                        externalCollector,
                        localCollectorJob,
                        stateFlow,
                        jdwpSessionProxy.proxyStatus
                    )
                    handler.execute()
                }.onFailure { throwable ->
                    logger.logIOCompletionErrors(throwable)
                }
            }
        }
    }

    override val jdwpSessionActivationCount: StateFlow<Int>
        get() = sharedJdwpSessionProvider.activationCount

    override fun startMonitoring() {
        lazyStartMonitoring
    }

    override suspend fun <T> withJdwpSession(block: suspend SharedJdwpSession.() -> T): T {
        return sharedJdwpSessionProvider.withSharedJdwpSession {
            it.block()
        }
    }

    override suspend fun awaitReadyToClose() {
        // Wait until no active JDWP session
        sharedJdwpSessionProvider.activationCount.first { it == 0 }
        logger.debug { "Ready to close" }
    }

    override fun close() {
        logger.debug { "close()" }
        sharedJdwpSessionProvider.close()
        cache.close()
    }

    override fun toString(): String {
        return "${this::class.simpleName}(session=${device.session}, device=$device, pid=$pid)"
    }
}
