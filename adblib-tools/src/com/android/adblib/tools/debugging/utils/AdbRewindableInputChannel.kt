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
package com.android.adblib.tools.debugging.utils

import com.android.adblib.AdbInputChannel
import com.android.adblib.ByteBufferAdbInputChannel
import com.android.adblib.read
import com.android.adblib.readRemaining
import com.android.adblib.skipRemaining
import com.android.adblib.tools.debugging.packets.copy
import com.android.adblib.utils.ResizableBuffer
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * An [AdbRewindableInputChannel] is an [AdbInputChannel] that supports a [rewind] operation
 */
internal interface AdbRewindableInputChannel : AdbInputChannel {

    /**
     * Rewind this [AdbRewindableInputChannel] to the beginning, so that [read] operations can
     * be executed again.
     */
    suspend fun rewind()

    companion object {

        /**
         * The empty [AdbRewindableInputChannel]
         */
        fun empty(): AdbRewindableInputChannel = Empty

        /**
         * A [AdbRewindableInputChannel] that wraps a [ByteBuffer], from [ByteBuffer.position]
         * to [ByteBuffer.limit]. [rewind] resets the [ByteBuffer.position] to its original
         * value.
         */
        fun forByteBuffer(buffer: ByteBuffer): AdbRewindableInputChannel {
            return ForByteBuffer(buffer)
        }

        /**
         * A [AdbRewindableInputChannel] that wraps an [AdbInputChannel], keeping data read
         * from the channel in-memory to support [rewind].
         */
        fun forInputChannel(
            input: AdbInputChannel,
            bufferHolder: ByteBufferHolder = ByteBufferHolder()
        ): AdbRewindableInputChannel {
            return ForInputChannel(input, bufferHolder)
        }

        /**
         * The empty [AdbRewindableInputChannel]
         */
        private object Empty : AdbRewindableInputChannel {

            override suspend fun rewind() {
                // Nothing to do
            }

            override suspend fun readBuffer(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
                // Nothing to do
            }

            override fun close() {
                // Nothing to do
            }
        }

        private class ForByteBuffer(
            private val sourceBuffer: ByteBuffer
        ) : AdbRewindableInputChannel, SupportsOffline<AdbRewindableInputChannel> {
            private val buffer = sourceBuffer.asReadOnlyBuffer()
            private val rewindPosition = buffer.position()
            private val input = ByteBufferAdbInputChannel(buffer)

            override suspend fun rewind() {
                buffer.position(rewindPosition)
            }

            override suspend fun readBuffer(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
                input.readBuffer(buffer, timeout, unit)
            }

            override suspend fun readExactly(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
                return input.readExactly(buffer, timeout, unit)
            }

            override fun close() {
                input.close()
            }

            override suspend fun toOffline(workBuffer: ResizableBuffer): AdbRewindableInputChannel {
                // Use the same source buffer, same position, same limit
                return forByteBuffer(sourceBuffer)
            }

            override fun toString(): String {
                return "${this::class.simpleName}(buffer=$buffer, rewindPosition=$rewindPosition, input=$input)"
            }
        }

        /**
         * A [AdbRewindableInputChannel] that wraps an [AdbInputChannel] and buffers data in memory
         * to support [rewinding][rewind].
         */
        private class ForInputChannel(
            private val input: AdbInputChannel,
            /**
             * The internal buffer used to store data read from [input].
             * * `(0..limit)` is the (optional) data buffered from [input]
             * * `(position..limit)` is (optional) data to return from the next [read]
             */
            private val bufferHolder: ByteBufferHolder = ByteBufferHolder(),
        ) : AdbRewindableInputChannel, SupportsOffline<AdbRewindableInputChannel> {

            /**
             * Whether [closed] has been called
             */
            private var closed: Boolean = false

            init {
                bufferHolder.clear() // [position=0, limit=capacity]
                bufferHolder.limit(0) // [position-0, limit=0]: No data
            }

            override suspend fun rewind() {
                throwIfClosed()

                bufferHolder.position(0)
            }

            private fun throwIfClosed() {
                check(!closed) {
                    throw IllegalStateException("${this::class.simpleName} has been closed")
                }
            }

            override suspend fun readBuffer(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
                throwIfClosed()

                // Read from `bufferHolder` first if available
                if (bufferHolder.remaining() > 0) {
                    val count = min(bufferHolder.remaining(), buffer.remaining())
                    val slice = bufferHolder.slice()
                    slice.limit(slice.position() + count)
                    buffer.put(slice)
                    bufferHolder.position(bufferHolder.position() + count)
                    return
                }

                // Read from underlying channel and append data to internal buffer
                val count = input.read(buffer, timeout, unit)
                if (count > 0) {
                    // Buffer has received data from [position - count, position],
                    // create a slice for that range, so we can buffer it.
                    val bufferSlice = buffer.duplicate()
                    bufferSlice.limit(buffer.position())
                    bufferSlice.position(buffer.position() - count)
                    assert(bufferSlice.remaining() == count)

                    // `bufferHolder` data is currently from [0, position==limit], append data
                    // to [limit, capacity] then update range to [0, newPosition==newLimit].
                    ensureRoom(count)
                    assert(bufferHolder.position() == bufferHolder.limit())
                    assert(bufferHolder.limit() + count <= bufferHolder.capacity())
                    bufferHolder.limit(bufferHolder.position() + count)
                    assert(bufferHolder.remaining() == count)
                    bufferHolder.put(bufferSlice)
                    assert(bufferHolder.remaining() == 0)
                }
            }

            override suspend fun toOffline(workBuffer: ResizableBuffer): AdbRewindableInputChannel {
                throwIfClosed()

                // Buffer everything into `bufferHolder`
                rewind()
                workBuffer.clear()
                skipRemaining(workBuffer)
                rewind()

                // `bufferHolder` contains bytes from [position=0, limit], copy that content into
                // a new `ByteBuffer` that we can then wrap with a `AdbRewindableInputChannel`
                assert(bufferHolder.position() == 0)
                return forByteBuffer(bufferHolder.copyBuffer())
            }

            override fun close() {
                closed = true
                input.close()
            }

            private fun ensureRoom(count: Int) {
                val minCapacity = bufferHolder.limit() + count
                bufferHolder.ensureCapacity(minCapacity)
                assert(bufferHolder.capacity() >= minCapacity)
            }
        }
    }
}

/**
 * Returns an [SupportsOffline.toOffline] version of this [AdbRewindableInputChannel] instance.
 */
internal suspend fun AdbRewindableInputChannel.toOffline(
    workBuffer: ResizableBuffer = ResizableBuffer()
): AdbRewindableInputChannel {
    return toOfflineOrNull(workBuffer) ?: run {
        // General purpose, less efficient, code path
        rewind()
        workBuffer.clear()
        readRemaining(workBuffer)
        rewind()
        val buffer = workBuffer.afterChannelRead(useMarkedPosition = false)
        AdbRewindableInputChannel.forByteBuffer(buffer.copy())
    }
}
