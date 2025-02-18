/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.adblib.impl.channels

import com.android.adblib.AdbInputChannel
import com.android.adblib.read
import com.android.adblib.skipRemaining
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.testingutils.TestingAdbSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.io.IOException
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.math.min

class AdbReadAheadInputChannelTest {

    @JvmField
    @Rule
    var exceptionRule: ExpectedException = ExpectedException.none()

    @JvmField
    @Rule
    val closeables = CloseablesRule()

    private fun <T : AutoCloseable> registerCloseable(item: T): T {
        return closeables.register(item)
    }

    @Test
    fun readAheadConsumesAsManyBytesAsPossible(): Unit = runBlockingWithTimeout {
        // Prepare
        val session = registerCloseable(TestingAdbSession())
        val channelFactory = AdbChannelFactoryImpl(session)
        val input = TestingInputChannel(length = 1_000, throwOnEOF = false)
        val readAheadChannel = channelFactory.createReadAheadChannel(input, 1_000)

        // Act
        val counts = (0 until 10)
            .map {
                val buffer = ByteBuffer.allocate(100)
                readAheadChannel.read(buffer)
            }.toList()

        // Assert
        yieldUntil {
            // There should be only 2 `read` invocations: 1 read of 1_000 bytes, 1 read for EOF
            input.readCounter == 2
        }
        Assert.assertTrue(input.eofReached)
        Assert.assertEquals(10, counts.size)
        repeat(counts.size) { index ->
            Assert.assertEquals(100, counts[index])
        }
    }

    @Test
    fun readAheadWorksWithManyReads(): Unit = runBlockingWithTimeout {
        // Prepare
        val session = registerCloseable(TestingAdbSession())
        val channelFactory = AdbChannelFactoryImpl(session)
        val inputSize = 100 * 1_024 // 100KB
        val input = TestingInputChannel(length = inputSize, throwOnEOF = false)
        val readAheadChannel = channelFactory.createReadAheadChannel(input, 16)

        // Act:
        // This is a "mini" stress test: we read 10 bytes at a time (as fast as possible)
        // from a read-ahead channel that reads 16 bytes at a time and stores them in the
        // circular byte buffer/AdbPipedInputChannel used by the AdbReadAheadInputChannel
        // implementation.
        val count = readAheadChannel.skipRemaining(bufferSize = 10)

        // Assert
        Assert.assertEquals(inputSize, count)
    }

    @Test
    fun exceptionFromInputChannelIsNotReportedToReaderIfReaderStopsReading(): Unit =
        runBlockingWithTimeout {
            // Prepare
            val session = registerCloseable(TestingAdbSession())
            val channelFactory = AdbChannelFactoryImpl(session)
            val input = TestingInputChannel(length = 1_000, throwOnEOF = true)
            val readAheadChannel = channelFactory.createReadAheadChannel(input, 1_000)

            // Act
            val counts = (0 until 10)
                .map {
                    val buffer = ByteBuffer.allocate(100)
                    readAheadChannel.read(buffer)
                }.toList()

            // Assert
            Assert.assertEquals(10, counts.size)
            repeat(counts.size) { index ->
                Assert.assertEquals(100, counts[index])
            }
        }

    @Test
    fun exceptionFromInputChannelIsReportedToReader(): Unit = runBlockingWithTimeout {
        // Prepare
        val session = registerCloseable(TestingAdbSession())
        val channelFactory = AdbChannelFactoryImpl(session)
        val input = TestingInputChannel(length = 1_000, throwOnEOF = true)
        val readAheadChannel = channelFactory.createReadAheadChannel(input, 1_000)

        // Act
        val buffer = ByteBuffer.allocate(100)
        repeat(10) {
            buffer.clear()
            readAheadChannel.read(buffer)
        }

        exceptionRule.expect(IOException::class.java)
        exceptionRule.expectMessage("My input channel exception")
        buffer.clear()
        readAheadChannel.read(buffer)

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun closeCancelsPendingInputChannelRead(): Unit = runBlockingWithTimeout {
        // Prepare
        val session = registerCloseable(TestingAdbSession())
        val channelFactory = AdbChannelFactoryImpl(session)
        val input = TestingInputChannel(length = 1_000, readDelay = Duration.ofSeconds(10))
        val readAheadChannel = channelFactory.createReadAheadChannel(input, 1_000)

        // Act
        val buffer = ByteBuffer.allocate(100)
        val count = withTimeoutOrNull(100) {
            readAheadChannel.read(buffer)
        }
        readAheadChannel.close()

        // Assert
        Assert.assertNull(count)
        Assert.assertTrue(input.closed)
    }

    @Test
    fun cancellationFromInputChannelIsReportedToReader(): Unit = runBlockingWithTimeout {
        // Prepare
        val session = registerCloseable(TestingAdbSession())
        val channelFactory = AdbChannelFactoryImpl(session)
        val input = object: AdbInputChannel {
            override suspend fun readBuffer(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
                cancel("Input Channel read is cancelled")
            }

            override fun close() {
            }
        }
        val readAheadChannel = channelFactory.createReadAheadChannel(input, 1_000)

        // Act
        exceptionRule.expect(CancellationException::class.java)
        exceptionRule.expectMessage("Input Channel read is cancelled")
        val buffer = ByteBuffer.allocate(100)
        readAheadChannel.read(buffer)

        // Assert
        Assert.fail("Should not reach")
    }

    private class TestingInputChannel(
        val length: Int,
        private val throwOnEOF: Boolean = false,
        private val readDelay: Duration? = null
    ) : AdbInputChannel {

        var closed = false
            private set

        var offset = 0
            private set

        var readCounter = 0
            private set

        var eofReached = false
            private set

        override suspend fun readBuffer(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
            readCounter++
            readDelay?.also {
                delay(it.toMillis())
            }
            when {
                offset < length -> {
                    val byteCount = min(buffer.remaining(), length - offset)
                    repeat(byteCount) {
                        buffer.put(1)
                        offset++
                    }
                }

                else -> {
                    eofReached = true
                    if (throwOnEOF) {
                        throw IOException("My input channel exception")
                    }
                }
            }
        }

        override fun close() {
            closed = true
        }
    }
}
