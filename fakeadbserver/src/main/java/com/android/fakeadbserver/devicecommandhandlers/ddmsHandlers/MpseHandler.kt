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
package com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers

import com.android.fakeadbserver.ClientState
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.ProfilerState
import kotlinx.coroutines.CoroutineScope

/**
 * MPSE: `Method Profiling Streaming End`
 */
class MpseHandler : DdmPacketHandler {

    override fun handlePacket(
        device: DeviceState,
        client: ClientState,
        packet: DdmPacket,
        jdwpHandlerOutput: JdwpHandlerOutput,
        socketScope: CoroutineScope
    ): Boolean {
        client.profilerState.status = ProfilerState.Status.Off

        // The data is sent in a special "MPSE" command packet
        val responsePacket =
            DdmPacket.createCommand(
                client.nextDdmsCommandId(),
                DdmPacket.encodeChunkType("MPSE"),
                DdmPayload {
                    writeBytes(client.profilerState.instrumentationData.bytes)
                })
        responsePacket.write(jdwpHandlerOutput)

        // Empty response used to be sent out before the release of Android 28
        if (device.apiLevel < 28) {
            JdwpPacket.createEmptyDdmsResponse(packet.id).write(jdwpHandlerOutput)
        }

        // Keep JDWP connection open
        return true
    }

    companion object {

        val CHUNK_TYPE = DdmPacket.encodeChunkType("MPSE")
    }
}
