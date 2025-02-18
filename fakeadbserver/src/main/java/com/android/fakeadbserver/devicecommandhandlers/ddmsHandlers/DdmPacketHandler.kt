/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers

import com.android.fakeadbserver.ClientState
import com.android.fakeadbserver.DeviceState
import kotlinx.coroutines.CoroutineScope

fun interface DdmPacketHandler {

    /**
     * Interface for fake debugger to handle incoming packets
     *
     * @param device The device associated with the client
     * @param client The client associated with the connection
     * @param packet The packet that is being handled
     * @param jdwpHandlerOutput The stream to write the response to
     * @param socketScope CoroutineScope with a lifecycle of a client socket
     * @return If true the fake debugger should continue accepting packets, if false it should
     * terminate the session
     */
    fun handlePacket(
        device: DeviceState,
        client: ClientState,
        packet: DdmPacket,
        jdwpHandlerOutput: JdwpHandlerOutput,
        socketScope: CoroutineScope
    ): Boolean

    fun replyDdmFail(jdwpHandlerOutput: JdwpHandlerOutput, packetId: Int) {
        // Android seems to always reply to invalid DDM commands with an empty JDWP reply packet
        val packet = JdwpPacket(packetId, true, 0.toShort(), ByteArray(0), 0, 0)
        packet.write(jdwpHandlerOutput)
    }
}
