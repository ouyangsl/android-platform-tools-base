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

import com.android.adblib.AdbChannel
import com.android.adblib.AdbDeviceServices
import com.android.adblib.AutoShutdown
import com.android.adblib.ConnectedDevice
import com.android.adblib.selector
import com.android.adblib.tools.debugging.impl.JdwpSessionImpl
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.utils.closeOnException
import kotlinx.coroutines.CoroutineScope
import java.io.EOFException
import java.io.IOException

/**
 * Abstraction over a JDWP session with a device.
 *
 * Note: The session [close] method must be called to terminate the JDWP session.
 * Closing a session only closes the underlying communication channel, without having
 * any effect on the process VM, i.e. the process VM is not terminated.
 *
 * @see [AdbDeviceServices.jdwp]
 */
interface JdwpSession : AutoShutdown {
    /**
     * The [ConnectedDevice] this [JdwpSession] is connected to.
     */
    val device: ConnectedDevice

    /**
     * The [CoroutineScope] corresponding to this [JdwpSession] instance, i.e. the scope
     * is cancelled when the [JdwpSession] is closed.
     */
    val scope: CoroutineScope

    /**
     * Sends a [JdwpPacketView] to the process VM.
     *
     * @throws [IOException] if an I/O error occurs
     * @throws [Exception] if any other error occurs
     */
    suspend fun sendPacket(packet: JdwpPacketView)

    /**
     * Waits for (and returns) the next [JdwpPacketView] from the process VM.
     *
     * @throws [EOFException] if there are no more packets from the process VM,
     *   i.e. the JDWP session has terminated.
     * @throws [IOException] if an I/O error occurs
     * @throws [Exception] if any other error occurs
     */
    suspend fun receivePacket(): JdwpPacketView

    /**
     * Returns a unique [JDWP packet ID][JdwpPacketView.id] to use for sending
     * a [JdwpPacketView], typically a [command packet][JdwpPacketView.isCommand],
     * in this session. Each call returns a new unique value.
     *
     * Note: This method is thread-safe.
     *
     * @throws UnsupportedOperationException if this session is not intended for sending
     * arbitrary [JdwpPacketView].
     */
    fun nextPacketId(): Int

    companion object {

        /**
         * Returns a [JdwpSession] that opens a `JDWP` session for the given process [pid]
         * on the given [device].
         *
         * [nextPacketIdBase] represents the initial value returned by [JdwpSession.nextPacketId].
         * If the value is `null`, the returned [JdwpSession] is not intended to be used for
         * sending custom [JdwpPacketView], and [JdwpSession.nextPacketId] throws an
         * [UnsupportedOperationException] when called.
         *
         * @see [AdbDeviceServices.jdwp]
         */
        suspend fun openJdwpSession(
            device: ConnectedDevice,
            pid: Int,
            nextPacketIdBase: Int?
        ): JdwpSession {
            val channel = device.session.deviceServices.jdwp(device.selector, pid)
            channel.closeOnException {
                return JdwpSessionImpl(device, channel, pid, "device", nextPacketIdBase)
            }
        }

        /**
         * Returns a [JdwpSession] that wraps an existing socket [channel] and allows
         * exchanging `JDWP` packets.
         *
         * [nextPacketIdBase] represents the initial value returned by [JdwpSession.nextPacketId].
         * If the value is `null`, the returned [JdwpSession] is not intended to be used for
         * sending custom [JdwpPacketView], and [JdwpSession.nextPacketId] throws an
         * [UnsupportedOperationException] when called.
         */
        fun wrapSocketChannel(
            device: ConnectedDevice,
            channel: AdbChannel,
            pid: Int,
            nextPacketIdBase: Int?
        ): JdwpSession {
            return JdwpSessionImpl(device, channel, pid, "debugger", nextPacketIdBase)
        }
    }
}
