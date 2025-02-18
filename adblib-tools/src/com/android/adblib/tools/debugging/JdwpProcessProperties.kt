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
package com.android.adblib.tools.debugging

import com.android.adblib.tools.debugging.impl.JdwpSessionProxy
import com.android.adblib.tools.debugging.packets.ddms.chunks.DdmsFeatChunk
import java.net.InetSocketAddress

/**
 * List of known properties corresponding to a [JdwpProcess] instance.
 */
data class JdwpProcessProperties(

    /**
     * The process ID. This is the only property that is guaranteed to be valid, all other
     * properties can be `null` or have default value until more is known about a process.
     */
    val pid: Int,

    /**
     * The process name that uniquely identifies the process on the device, or `null` if the process
     * name is not known (yet) due to debugger latency or an error connecting to the process and
     * retrieving data about it.
     *
     * The process name is often equal to [packageName], except when a `android:process`
     * process name entry is specified in the
     * [AndroidManifest.xml](https://developer.android.com/guide/topics/manifest/application-element)
     * file.
     */
    val processName: String? = null,

    /**
     * The package name of the process, or `null` if the value is not known yet or if the device
     * does not support retrieving this information (R+ only)
     */
    val packageName: String? = null,

    /**
     * The User ID this process is running in context of, or `null` if the value is not known yet or
     * the device does not support retrieving this information (R+ only).
     */
    val userId: Int? = null,

    /**
     * The Android VM identifier, or `null` if the value is not known yet.
     */
    val vmIdentifier: String? = null,

    /**
     * The ABI identifier, or `null` if the value is not known yet.
     */
    val abi: String? = null,

    /**
     * The JVM flags, or `null` if the value is not known yet.
     */
    val jvmFlags: String? = null,

    /**
     * Whether legacy native debugging is supported.
     */
    @Deprecated("This property was never fully supported and is now completely deprecated")
    val isNativeDebuggable: Boolean = false,

    /**
     * `true` if the `WAIT` command was received.
     */
    val waitCommandReceived: Boolean = false,

    /**
     * `true` if the process is waiting for a debugger to attach.
     * `false` if we don't know or if a debugger is already attached.
     */
    val isWaitingForDebugger: Boolean = false,

    /**
     * The status of JDWP session proxy between an external debugger and the Android Process.
     *
     * @see JdwpSessionProxy
     */
    val jdwpSessionProxyStatus: JdwpSessionProxyStatus = JdwpSessionProxyStatus(),

    /**
     * List of features reported by the [DdmsFeatChunk] packet
     */
    val features: List<String> = emptyList(),

    /**
     * Whether this [JdwpProcessProperties] instance is fully populated, i.e. there is no pending
     * operation to collect more information. See the [exception] property for additional
     * information about the status.
     */
    val completed: Boolean = false,

    /**
     * The error related to retrieving properties (other than [pid]), or `null`.
     *
     * This value is only set when [completed] is `true`, and remains `null` unless
     * there was an error retrieving some property values.
     *
     * For example, it is sometimes not possible to retrieve any information about a process ID
     * from the Android VM if there is already a JDWP session active for that process.
     */
    val exception: Throwable? = null,
)

/**
 * Status of JDWP Session proxy external Java debuggers can use to connect to a
 * [JdwpProcess].
 *
 * @see JdwpProcess
 * @see JdwpProcessProperties.jdwpSessionProxyStatus
 */
data class JdwpSessionProxyStatus(
    /**
     * The [InetSocketAddress] (typically on `localhost`) a Java debugger can use to open a
     * JDWP debugging session with the Android process. If the value is `null`, the debugger
     * connection is not ready yet.
     *
     * @see JdwpSessionProxy
     */
    val socketAddress: InetSocketAddress? = null,

    /**
     * `true` if there is an active JDWP debugging session on [socketAddress].
     *
     * @see JdwpSessionProxy
     */
    val isExternalDebuggerAttached: Boolean = false,
)

internal fun JdwpProcessProperties.mergeWith(other: JdwpProcessProperties): JdwpProcessProperties {
    val source = this
    @Suppress("DEPRECATION")
    return source.copy(
        processName = source.processName.mergeWith(other.processName),
        userId = source.userId.mergeWith(other.userId),
        packageName = source.packageName.mergeWith(other.packageName),
        vmIdentifier = source.vmIdentifier.mergeWith(other.vmIdentifier),
        abi = source.abi.mergeWith(other.abi),
        jvmFlags = source.jvmFlags.mergeWith(other.jvmFlags),
        isNativeDebuggable = source.isNativeDebuggable.mergeWith(other.isNativeDebuggable),
        waitCommandReceived = source.waitCommandReceived.mergeWith(other.waitCommandReceived),
        features = source.features.mergeWith(other.features),
        completed = source.completed.mergeWith(other.completed),
        exception = source.exception.mergeWith(other.exception),
        isWaitingForDebugger = source.isWaitingForDebugger.mergeWith(other.isWaitingForDebugger),
        jdwpSessionProxyStatus = source.jdwpSessionProxyStatus.mergeWith(
            other.jdwpSessionProxyStatus
        ),
    )
}

private fun JdwpSessionProxyStatus.mergeWith(other: JdwpSessionProxyStatus): JdwpSessionProxyStatus {
    return JdwpSessionProxyStatus(
        isExternalDebuggerAttached = this.isExternalDebuggerAttached.mergeWith(other.isExternalDebuggerAttached),
        socketAddress = this.socketAddress ?: other.socketAddress
    )
}

private fun String?.mergeWith(other: String?): String? {
    return this ?: other
}

private fun Throwable?.mergeWith(other: Throwable?): Throwable? {
    return this ?: other
}

private fun Int?.mergeWith(other: Int?): Int? {
    return this ?: other
}

private fun Boolean.mergeWith(other: Boolean): Boolean {
    return if (this) true else other
}

private fun List<String>.mergeWith(other: List<String>): List<String> {
    return this.ifEmpty { other }
}
