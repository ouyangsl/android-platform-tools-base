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
package com.android.adblib.tools.debugging.packets.ddms

import com.android.adblib.AdbInputChannelSlice
import com.android.adblib.AdbOutputChannel
import com.android.adblib.ByteBufferAdbOutputChannel
import com.android.adblib.forwardTo
import com.android.adblib.readNBytes
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.copy
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants.DDMS_CHUNK_BYTE_ORDER
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants.DDMS_CHUNK_HEADER_LENGTH
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants.DDMS_CMD
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants.DDMS_CMD_SET
import com.android.adblib.tools.debugging.packets.impl.PayloadProvider
import com.android.adblib.tools.debugging.packets.withPayload
import com.android.adblib.tools.debugging.toByteBuffer
import com.android.adblib.utils.ResizableBuffer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.EOFException

/**
 * Serialize the [DdmsChunkView] into an [AdbOutputChannel].
 *
 * @throws IllegalArgumentException if [DdmsChunkView.withPayload] does not contain exactly
 * [DdmsChunkView.length] bytes
 *
 * @param workBuffer (Optional) The [ResizableBuffer] used to transfer data
 */
internal suspend fun DdmsChunkView.writeToChannel(
    channel: AdbOutputChannel,
    workBuffer: ResizableBuffer = ResizableBuffer()
) {
    workBuffer.clear()
    workBuffer.order(DDMS_CHUNK_BYTE_ORDER)

    // Write header
    workBuffer.appendInt(type.value)
    workBuffer.appendInt(length)
    channel.writeExactly(workBuffer.forChannelWrite())

    // Write payload
    val byteCount = withPayload { payload -> payload.forwardTo(channel, workBuffer) }
    checkChunkLength(byteCount)
}

/**
 * Returns an in-memory copy of this [DdmsChunkView].
 *
 * @throws IllegalArgumentException if [DdmsChunkView.withPayload] does not contain exactly
 * [DdmsChunkView.length] bytes
 *
 * @param workBuffer (Optional) The [ResizableBuffer] used to transfer data
 */
internal suspend fun DdmsChunkView.clone(
    workBuffer: ResizableBuffer = ResizableBuffer()
): DdmsChunkView {
    // Copy payload to "workBuffer"
    workBuffer.clear()
    val dataCopy = ByteBufferAdbOutputChannel(workBuffer)
    withPayload { payload -> payload.forwardTo(dataCopy) }

    // Make a copy into our own ByteBuffer
    val bufferCopy = workBuffer.forChannelWrite().copy()

    // Create rewindable channel for payload
    return EphemeralDdmsChunk(
        type = type,
        length = length,
        payloadProvider = PayloadProvider.forByteBuffer(bufferCopy)
    )
}

/**
 * Provides a view of a [JdwpPacketView] as a "DDMS" packet. A "DDMS" packet is a
 * "JDWP" packet that contains one or more [chunks][DdmsChunkView].
 *
 * Each chunk starts with an 8 bytes header followed by chunk specific payload
 *
 *  * chunk type (4 bytes)
 *  * chunk length (4 bytes)
 *  * chunk payload (variable, specific to the chunk type)
 */
internal fun JdwpPacketView.ddmsChunks(
    workBuffer: ResizableBuffer = ResizableBuffer()
): Flow<DdmsChunkView> {
    val jdwpPacketView = this
    return flow {
        if (!isReply && !isCommand(DDMS_CMD_SET, DDMS_CMD)) {
            throw IllegalArgumentException("JDWP packet is not a DDMS command packet (and is not a reply packet)")
        }

        jdwpPacketView.withPayload  { jdwpPayload ->
            while (true) {
                workBuffer.clear()
                workBuffer.order(DDMS_CHUNK_BYTE_ORDER)
                try {
                    jdwpPayload.readNBytes(workBuffer, DDMS_CHUNK_HEADER_LENGTH)
                } catch (e: EOFException) {
                    // Regular exit: there are no more chunks to be read
                    break
                }
                val payloadBuffer = workBuffer.afterChannelRead()

                // Prepare chunk source
                val chunkType = DdmsChunkType(payloadBuffer.getInt())
                val payloadLength = payloadBuffer.getInt()
                val payload = AdbInputChannelSlice(jdwpPayload, payloadLength)
                val payloadProvider = PayloadProvider.forInputChannel(payload)
                EphemeralDdmsChunk(chunkType, payloadLength, payloadProvider).use { chunk ->
                    // Emit it to collector
                    emit(chunk)

                    // Ensure we consume all bytes from the chunk payload in case the collector did not
                    // do anything with it
                    chunk.shutdown(workBuffer)
                }
            }
        }
    }
}

val JdwpPacketView.isDdmsCommand: Boolean
    get() = isCommand(DDMS_CMD_SET, DDMS_CMD)

private fun DdmsChunkView.checkChunkLength(byteCount: Int) {
    val expectedByteCount = length
    if (byteCount != expectedByteCount) {
        throw IllegalArgumentException(
            "DDMS packet should contain $expectedByteCount " +
                    "bytes but contains $byteCount bytes instead"
        )
    }
}

internal suspend fun DdmsChunkView.createFailException(): DdmsFailException {
    try {
        // See https://cs.android.com/android/platform/superproject/+/android13-release:libcore/dalvik/src/main/java/org/apache/harmony/dalvik/ddmc/ChunkHandler.java;l=102
        // 0-3: error code
        // 4-7: error message: UTF-16 character count
        // 8-n: error message: UTF-16 characters
        return withPayload { payload ->
            val buffer = payload.toByteBuffer(length).order(DDMS_CHUNK_BYTE_ORDER)
            val errorCode = buffer.getInt()
            val charCount = buffer.getInt()
            val data = CharArray(charCount)
            for (i in 0 until charCount) {
                data[i] = buffer.getChar()
            }
            val message = String(data)
            DdmsFailException(errorCode, message)
        }
    } catch (t: Throwable) {
        // In case the FAIL packet format is invalid, return a generic error, since this method is
        // called in the context of handling an error.
        return DdmsFailException(-1, "Unknown error due to invalid FAIL packet format").also {
            it.addSuppressed(t)
        }
    }
}

internal suspend fun DdmsChunkView.throwFailException(): Nothing {
    throw createFailException()
}

class DdmsFailException(val errorCode: Int, val failMessage: String) : Exception() {

    override val message: String?
        get() = "DDMS Failure on AndroidVM: errorCode=$errorCode, message=$failMessage"
}
