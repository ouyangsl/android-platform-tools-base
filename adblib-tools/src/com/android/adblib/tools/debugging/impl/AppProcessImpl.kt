/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.adblib.AppProcessEntry
import com.android.adblib.ConnectedDevice
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.adbLogger
import com.android.adblib.scope
import com.android.adblib.tools.debugging.AppProcess
import com.android.adblib.tools.debugging.JdwpProcess
import com.android.adblib.withPrefix

/**
 * Implementation of [AppProcess]
 */
internal class AppProcessImpl(
    override val device: ConnectedDevice,
    private val process: AppProcessEntry,
    override val jdwpProcess: JdwpProcess?,
) : AppProcess, AutoCloseable {

    private val logger = adbLogger(device.session)
        .withPrefix("${device.session} - $device - pid=$pid -")

    override val cache = CoroutineScopeCache.create(device.scope)

    override val pid: Int
        get() = process.pid

    override val debuggable: Boolean
        get() = process.debuggable

    override val profileable: Boolean
        get() = process.profileable

    override val architecture: String
        get() = process.architecture

    override fun close() {
        logger.debug { "close()" }
        cache.close()
    }

    override fun toString(): String {
        return "AppProcess(device=$device, pid=$pid, " +
                "debuggable=$debuggable, profileable=$profileable, architecture=$architecture, " +
                "jdwpProcess=$jdwpProcess)"
    }
}
