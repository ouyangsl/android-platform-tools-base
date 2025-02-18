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
package com.android.adblib

import com.android.adblib.impl.channels.AdbInputChannelReader
import com.android.adblib.impl.channels.AdbInputStreamChannel
import com.android.adblib.impl.channels.AdbOutputStreamChannel
import com.android.adblib.testingutils.AnyExceptionOfMatcher.Companion.anyExceptionOf
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.testingutils.FakeAdbServerProviderRule
import com.android.adblib.testingutils.TestingAdbSessionHost
import com.android.adblib.testingutils.TimeWaitSocketsThrottler
import com.android.adblib.testingutils.asAdbInputChannel
import com.android.adblib.testingutils.setTestLoggerMinLevel
import com.android.adblib.utils.AdbProtocolUtils
import com.android.adblib.utils.ResizableBuffer
import com.android.adblib.utils.launchCancellable
import com.android.fakeadbserver.ClientState
import com.android.fakeadbserver.DeviceFileState
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.ProfileableProcessState
import com.android.fakeadbserver.devicecommandhandlers.SyncCommandHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission.OWNER_READ
import java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class AdbDeviceServicesTest {

    @JvmField
    @Rule
    var exceptionRule: ExpectedException = ExpectedException.none()

    @JvmField
    @Rule
    val fakeAdbRule = FakeAdbServerProviderRule {
        installDefaultCommandHandlers()
        installDeviceHandler(SyncCommandHandler())
    }

    private val fakeAdb get() = fakeAdbRule.fakeAdb
    private val deviceServices get() = fakeAdbRule.adbSession.deviceServices

    @Test
    fun testShell(): Unit = runBlockingWithTimeout {
        // Prepare
        val device = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)
        val collector = ByteBufferShellCollector()

        // Act
        val bytes = deviceServices.shell(deviceSelector, "getprop", collector).first()

        // Assert
        Assert.assertNull(deviceSelector.transportId)
        val expectedOutput = """
            # This is some build info
            # This is more build info

            [ro.build.version.release]: [model]
            [ro.build.version.sdk]: [30]
            [ro.product.cpu.abi]: [x86_64]
            [ro.product.manufacturer]: [test1]
            [ro.product.model]: [test2]
            [ro.serialno]: [1234]

        """.trimIndent()
        Assert.assertEquals(expectedOutput, AdbProtocolUtils.byteBufferToString(bytes))
    }

    @Test
    fun testShellAllowsNonAscii(): Unit = runBlockingWithTimeout {
        // Prepare
        val device = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)
        val collector = TextShellCollector()

        // Act
        val text = deviceServices.shell(deviceSelector, "echo sh -c 'test -e Аудиокнига'", collector).first()

        // Assert
        val expectedOutput = "sh -c 'test -e Аудиокнига'\n"
        Assert.assertEquals(expectedOutput, text)
    }

    @Test
    fun testShellCanStripCrLf(): Unit = runBlockingWithTimeout {
        // Prepare
        val device = addFakeDevice(fakeAdb, 23) // older device to force \r\n newlines
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)
        val collector = ByteBufferShellCollector()

        // Act
        val bytes = deviceServices.shell(deviceSelector, "getprop", collector, stripCrLf = true).first()

        // Assert
        Assert.assertNull(deviceSelector.transportId)
        val expectedOutput = """
            # This is some build info
            # This is more build info

            [ro.build.version.release]: [model]
            [ro.build.version.sdk]: [23]
            [ro.product.cpu.abi]: [x86_64]
            [ro.product.manufacturer]: [test1]
            [ro.product.model]: [test2]
            [ro.serialno]: [1234]

        """.trimIndent()
        Assert.assertEquals(expectedOutput, AdbProtocolUtils.byteBufferToString(bytes))
    }

    @Test
    fun testShellCanKeepCrLf(): Unit = runBlockingWithTimeout {
        // Prepare
        val device = addFakeDevice(fakeAdb, 23) // older device to force \r\n newlines
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)
        val collector = ByteBufferShellCollector()

        // Act
        val bytes = deviceServices.shell(deviceSelector, "getprop", collector, stripCrLf = false).first()

        // Assert
        Assert.assertNull(deviceSelector.transportId)
        val expectedOutput = """
            # This is some build info
            # This is more build info

            [ro.build.version.release]: [model]
            [ro.build.version.sdk]: [23]
            [ro.product.cpu.abi]: [x86_64]
            [ro.product.manufacturer]: [test1]
            [ro.product.model]: [test2]
            [ro.serialno]: [1234]

        """.trimIndent().replace("\n", "\r\n")
        Assert.assertEquals(expectedOutput, AdbProtocolUtils.byteBufferToString(bytes))
    }

    @Test
    fun testShellToText(): Unit = runBlockingWithTimeout {
        // Prepare
        val device = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)

        // Act
        val commandOutput = deviceServices.shellAsText(deviceSelector, "getprop")

        // Assert
        val expectedOutput = """
            # This is some build info
            # This is more build info

            [ro.build.version.release]: [model]
            [ro.build.version.sdk]: [30]
            [ro.product.cpu.abi]: [x86_64]
            [ro.product.manufacturer]: [test1]
            [ro.product.model]: [test2]
            [ro.serialno]: [1234]

        """.trimIndent()
        Assert.assertEquals(expectedOutput, commandOutput.stdout)
        Assert.assertEquals("", commandOutput.stderr)
        Assert.assertEquals(0, commandOutput.exitCode)
    }

    @Test
    fun testShellToLines(): Unit = runBlockingWithTimeout {
        // Prepare
        val device = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)

        // Act
        val lines =
            deviceServices.shellAsLines(deviceSelector, "getprop", bufferSize = 10)
                .filterIsInstance<ShellCommandOutputElement.StdoutLine>()
                .map { it.contents }
                .toList()

        // Assert
        val expectedOutput = """
            # This is some build info
            # This is more build info

            [ro.build.version.release]: [model]
            [ro.build.version.sdk]: [30]
            [ro.product.cpu.abi]: [x86_64]
            [ro.product.manufacturer]: [test1]
            [ro.product.model]: [test2]
            [ro.serialno]: [1234]

        """.trimIndent()
        Assert.assertEquals(expectedOutput.lines(), lines)
    }

    @Test
    fun testShellWithArguments(): Unit = runBlockingWithTimeout {
        // Prepare
        val device = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)

        // Act
        val commandOutput =
            deviceServices.shell(
                deviceSelector,
                "cmd package install-create",
                TextShellCollector()
            ).first()

        // Assert
        val expectedOutput = "Success: created install session [1234]"
        Assert.assertEquals(expectedOutput, commandOutput)
    }

    @Test
    fun testShellWithTimeout() {
        // Prepare
        val device = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)
        val collector = ByteBufferShellCollector()

        // Act
        exceptionRule.expect(TimeoutException::class.java)
        /*val bytes = */runBlocking {
            deviceServices.shell(
                deviceSelector,
                "write-no-stop",
                collector,
                null,
                Duration.ofMillis(10)
            ).first()
        }

        // Assert
        Assert.fail("Should not be reached")
    }

    @Test
    fun testShellWithStdin(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val input = """
            This is some text with
            split in multiple lines
            and containing non-ascii
            characters such as
            - ඒ (SINHALA LETTER EEYANNA (U+0D92))
            - ⿉ (KANGXI RADICAL MILLET (U+2FC9))

        """.trimIndent()

        // Act
        val commandOutput = deviceServices.shellAsText(
            deviceSelector,
            "cat",
            stdinChannel = input.asAdbInputChannel(deviceServices.session)
        )

        // Assert
        Assert.assertEquals(input, commandOutput.stdout)
        Assert.assertEquals("", commandOutput.stderr)
        Assert.assertEquals(0, commandOutput.exitCode)
    }

    @Test
    fun testShellWithNoShutdownOutput(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val input = "foo"

        // Act
        val collector = TextShellCollector()
        val stdinChannel = input.asAdbInputChannel(deviceServices.session)
        stdinChannel.use {
            val timeout = Duration.ofSeconds(2)
            val flow =
                deviceServices.shell(
                    deviceSelector,
                    "cat",
                    collector,
                    stdinChannel = it,
                    timeout,
                    shutdownOutput = false
                )
            exceptionRule.expect(TimeoutException::class.java)
            flow.first()
            Assert.fail("Call to 'cat' should have timed out")
        }
    }

    @Test
    fun testShellWithLargeInputAndSmallBufferSize(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        // To ensure we don't spam the log during this test
        deviceServices.session.host.setTestLoggerMinLevel(
            deviceServices.session.host.logger.minLevel.coerceAtLeast(AdbLogger.Level.INFO)
        )
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val input = """
            This is some text with
            split in multiple lines
            and containing non-ascii
            characters such as
            - ඒඒඒඒඒඒ (SINHALA LETTER EEYANNA (U+0D92))
            - ⿉⿉⿉⿉⿉⿉⿉ (KANGXI RADICAL MILLET (U+2FC9))

        """.trimIndent().repeat(100)

        // Act
        val commandOutput =
            deviceServices.shellAsText(
                deviceSelector,
                "cat",
                input.asAdbInputChannel(deviceServices.session),
                INFINITE_DURATION,
                15
            )

        // Assert
        Assert.assertEquals(input, commandOutput.stdout)
        Assert.assertEquals("", commandOutput.stderr)
        Assert.assertEquals(0, commandOutput.exitCode)
    }

    @Test
    fun testShellWithErroneousStdinMaintainsInitialException(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val errorInputChannel = object : AdbInputChannel {
            private var firstCall = true
            override suspend fun readBuffer(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
                if (firstCall) {
                    firstCall = false
                    buffer.put('a'.code.toByte())
                    buffer.put('a'.code.toByte())
                } else {
                    throw MyTestException("hello")
                }
            }

            override fun close() {
                // Nothing
            }
        }

        // Act
        exceptionRule.expect(MyTestException::class.java)
        /*val ignored = */deviceServices.shell(
              deviceSelector,
              "cat",
              TextShellCollector(),
              stdinChannel = errorInputChannel
            ).first()

        // Assert
        Assert.fail() // Should not reach
    }

    @Test
    fun testShellWithCancelledStdinMaintainsCancellation() {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val errorInputChannel = object : AdbInputChannel {
            private var firstCall = true
            override suspend fun readBuffer(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
                if (firstCall) {
                    firstCall = false
                    buffer.put('a'.code.toByte())
                    buffer.put('a'.code.toByte())
                } else {
                    // We force a cancellation after the first call
                    throw CancellationException("hello")
                }
            }

            override fun close() {
                // Nothing
            }
        }

        // Act
        exceptionRule.expect(CancellationException::class.java)
        /*val ignored = */runBlocking {
            deviceServices.shell(
              deviceSelector,
              "cat",
              TextShellCollector(),
              stdinChannel = errorInputChannel
            ).first()
        }

        // Assert
        Assert.fail() // Should not reach
    }

    /**
     * Ensures the [Flow] returned by [AdbDeviceServices.shellAsLines] has the expected "streaming"
     * behavior, i.e. that lines of output are emitted to the [Flow] as soon as they are received
     * from the shell output (`stdin` in this case).
     *
     * This test could fail as an infinite wait if the input channel was read "ahead" before
     * the shell output is passed to the underlying [ShellCollector] and [Flow].
     * This could easily happen if the [AdbDeviceServices.shell] implementation had the
     * coroutines forwarding stdin and stdout run inside coroutine scopes that wait on each other,
     * e.g. parent/child scopes.
     */
    @Test
    fun testShellStdinIsForwardedConcurrentlyWithStdout(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        // Channel used to coordinate our custom AdbInputChannel and consuming the flow
        val inputOutputCoordinator = Channel<String>(1)

        // AdbInputChannel implementation that simulates an input of 10 lines
        val testInputChannel = object : AdbInputChannel {
            val lineCount = 10
            var currentLineIndex = 0
            override suspend fun readBuffer(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
                // Wait until we are given "go-go"
                inputOutputCoordinator.receive()

                // Read lineCount lines, then EOF
                if (currentLineIndex >= lineCount) {
                    return
                }

                val bytes =
                    "line ${currentLineIndex + 1}\n".toByteArray(AdbProtocolUtils.ADB_CHARSET)
                buffer.put(bytes)
                currentLineIndex++
            }

            override fun close() {
                // Nothing
            }
        }

        // Act
        val flow = deviceServices.shell(deviceSelector, "cat", LineShellCollector(), testInputChannel)

        // Assert
        Assert.assertEquals(
            "Input should be left alone before the flow is active",
            0, testInputChannel.currentLineIndex,
        )
        // Tell test input channel to process one `read` request
        inputOutputCoordinator.send("go-go")

        // Collect all text lines from the flow, while verifying our custom AdbInputChannel
        // is read from concurrently
        flow.collectIndexed { index, line ->
            if (index == 10) {
                // Last line is empty, since there is a trailing '\n' in the previous line
                Assert.assertEquals(
                    "Last line index should be 10",
                    10, testInputChannel.currentLineIndex
                )
                Assert.assertEquals(
                    "Last line should be empty",
                    "", line
                )
            } else {
                Assert.assertEquals(
                    "Input channel should advance one line at a time",
                    index + 1, testInputChannel.currentLineIndex
                )
                Assert.assertEquals("line ${index + 1}", line)
            }

            // Tell test input channel to process one `read` request
            inputOutputCoordinator.send("go-go")
        }
    }

    @Test
    fun testShellWithMonitoringCanDetectInactiveCommand(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val slowInputChannel = object : AdbInputChannel {
            var firstCall = true
            override suspend fun readBuffer(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
                if (firstCall) {
                    firstCall = false
                    delay(10)
                    buffer.put('a'.code.toByte())
                } else {
                    // Delay second call, so that the "cat" command does not receive
                    // input for a while so that it does not emit any output. We essentially
                    // force the "cat" command to be inactive for a while.
                    delay(10_000)
                }
            }

            override fun close() {
            }
        }

        // Act
        exceptionRule.expect(TimeoutException::class.java)
        /*val ignored = */deviceServices.shellCommand(deviceSelector, "cat")
                .withLegacyCollector(LineShellCollector())
                .withStdin(slowInputChannel)
                .withCommandOutputTimeout(Duration.ofMillis(100))
                .execute()
                .first()

        // Assert
        Assert.fail() // Should not reach
    }

    @Test
    fun testShellWithMonitoringWorksAsLongAsTimeoutIsNotExceeded(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val slowInputChannel = object : AdbInputChannel {
            var callCount = 0
            override suspend fun readBuffer(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
                // In total, this will take 20 * 10 = 200 msec, but each call is less
                // than the inactivity timeout of 100 msec we use for the test
                if (callCount < 20) {
                    delay(10)
                    buffer.put(('a'.code.toByte() + callCount.toByte()).toByte())
                    buffer.put('\n'.code.toByte())
                    callCount++
                } else {
                    // Nothing to do
                }
            }

            override fun close() {
                // Nothing
            }
        }

        // Act
        val text = deviceServices.shellCommand(deviceSelector, "cat")
                .withLegacyCollector(TextShellCollector())
                .withStdin(slowInputChannel)
                // We have an "inactivity" timeout of 100 msec, but each read takes only 10 msec
                .withCommandOutputTimeout(Duration.ofMillis(100))
                .execute()
                .first()

        // Assert
        Assert.assertEquals("a\nb\nc\nd\ne\nf\ng\nh\ni\nj\nk\nl\nm\nn\no\np\nq\nr\ns\nt\n", text)
    }

    @Test
    fun testShellCollectorServiceOutputIsNotBlockedOnEmit(): Unit = runBlockingWithTimeout {
        // Prepare
        val device = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)

        // Act
        for (dispatcher in listOf(Dispatchers.IO, Dispatchers.Default)) {
            println("Running ShellCollector test for dispatcher: $dispatcher")
            var shellCollectorHasCollected = false
            val collector = object : ShellCollector<ByteBuffer> {
                override suspend fun start(collector: FlowCollector<ByteBuffer>) {
                    collector.emit(ByteBuffer.allocate(10))
                }

                override suspend fun collect(
                    collector: FlowCollector<ByteBuffer>,
                    stdout: ByteBuffer
                ) {
                    shellCollectorHasCollected = true
                }

                override suspend fun end(collector: FlowCollector<ByteBuffer>) {}
            }

            val job = launch(dispatcher) {
                deviceServices.shell(deviceSelector, "getprop", collector)
                    .collect {
                        // Collecting the emit called from `collector.start()`
                        // This infinite delay shouldn't prevent
                        delay(Long.MAX_VALUE)
                    }
            }

            // Assert
            // Test failure is indicated by this yield timing out
            yieldUntil { shellCollectorHasCollected }

            // Cleanup
            job.cancelAndJoin()
        }
    }

    @Test
    fun testShellV2CollectorServiceOutputIsNotBlockedOnEmit(): Unit = runBlockingWithTimeout {
        // Prepare
        val device = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)

        // Act
        for (dispatcher in listOf(Dispatchers.IO, Dispatchers.Default)) {
            println("Running ShellCollector test for dispatcher: $dispatcher")
            var shellCollectorHasCollected = false
            val collector = object : ShellV2Collector<ByteBuffer> {
                override suspend fun start(collector: FlowCollector<ByteBuffer>) {
                    collector.emit(ByteBuffer.allocate(10))
                }

                override suspend fun collectStdout(
                    collector: FlowCollector<ByteBuffer>,
                    stdout: ByteBuffer
                ) {
                    shellCollectorHasCollected = true
                }

                override suspend fun collectStderr(
                    collector: FlowCollector<ByteBuffer>,
                    stderr: ByteBuffer
                ) {
                    shellCollectorHasCollected = true
                }

                override suspend fun end(collector: FlowCollector<ByteBuffer>, exitCode: Int) { }

            }

            val job = launch(dispatcher) {
                deviceServices.shellV2(deviceSelector, "getprop", collector)
                    .collect {
                        // Collecting the emit called from `collector.start()`
                        // This infinite delay shouldn't prevent
                        delay(Long.MAX_VALUE)
                    }
            }

            // Assert
            // Test failure is indicated by this yield timing out
            yieldUntil { shellCollectorHasCollected }

            // Cleanup
            job.cancelAndJoin()
        }
    }

    @Test
    fun testExec(): Unit = runBlockingWithTimeout {
        // Prepare
        val device = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)
        val collector = ByteBufferShellCollector()

        // Act
        val bytes = deviceServices.exec(deviceSelector, "getprop", collector).first()

        // Assert
        Assert.assertNull(deviceSelector.transportId)
        val expectedOutput = """
            # This is some build info
            # This is more build info

            [ro.build.version.release]: [model]
            [ro.build.version.sdk]: [30]
            [ro.product.cpu.abi]: [x86_64]
            [ro.product.manufacturer]: [test1]
            [ro.product.model]: [test2]
            [ro.serialno]: [1234]

        """.trimIndent()
        Assert.assertEquals(expectedOutput, AdbProtocolUtils.byteBufferToString(bytes))
    }

    @Test
    fun testRawExec(): Unit = runBlockingWithTimeout {
        // Prepare
        val device = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)

        // Act
        deviceServices.rawExec(deviceSelector, "getprop").use {channel ->
            // Assert
            Assert.assertNull(deviceSelector.transportId)
            val expectedOutput = """
                # This is some build info
                # This is more build info

                [ro.build.version.release]: [model]
                [ro.build.version.sdk]: [30]
                [ro.product.cpu.abi]: [x86_64]
                [ro.product.manufacturer]: [test1]
                [ro.product.model]: [test2]
                [ro.serialno]: [1234]

            """.trimIndent()
            val adbChannelOutput = ByteBuffer.allocate(1024)
            channel.read(adbChannelOutput)
            adbChannelOutput.flip()
            Assert.assertEquals(
                expectedOutput,
                AdbProtocolUtils.ADB_CHARSET.decode(adbChannelOutput).toString()
            )
        }
    }

    @Test
    fun testShellV2Works(): Unit = runBlockingWithTimeout {
        // Prepare
        val device = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)
        val collector = ShellV2ResultCollector()

        // Act
        val shellV2Result = deviceServices.shellV2(deviceSelector, "getprop", collector).first()

        // Assert
        Assert.assertNull(deviceSelector.transportId)
        val expectedOutput = """
            # This is some build info
            # This is more build info

            [ro.build.version.release]: [model]
            [ro.build.version.sdk]: [30]
            [ro.product.cpu.abi]: [x86_64]
            [ro.product.manufacturer]: [test1]
            [ro.product.model]: [test2]
            [ro.serialno]: [1234]

        """.trimIndent()
        Assert.assertEquals(
            expectedOutput,
            AdbProtocolUtils.byteBufferToString(shellV2Result.stdout)
        )
        Assert.assertEquals("", AdbProtocolUtils.byteBufferToString(shellV2Result.stderr))
        Assert.assertEquals(0, shellV2Result.exitCode)
    }

    @Test
    fun testShellV2SplitsShellPackets(): Unit = runBlockingWithTimeout {
        // Prepare
        val device = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)
        val collector = ShellV2ResultCollector()
        val input = """
            stdout: This is some text with
            stdout: split in multiple lines
            stderr: and containing non-ascii
            stdout: characters such as
            stderr: - ඒ (SINHALA LETTER EEYANNA (U+0D92))
            stdout: - ⿉ (KANGXI RADICAL MILLET (U+2FC9))
            exit: 10
        """.trimIndent()

        // Act
        val shellV2Result = deviceServices.shellV2(
                deviceSelector,
                "shell-protocol-echo",
                collector,
                stdinChannel = input.asAdbInputChannel(deviceServices.session)
            ).first()

        // Assert
        Assert.assertNull(deviceSelector.transportId)
        val expectedStdout = """
            This is some text with
            split in multiple lines
            characters such as
            - ⿉ (KANGXI RADICAL MILLET (U+2FC9))

        """.trimIndent()

        val expectedStderr = """
            and containing non-ascii
            - ඒ (SINHALA LETTER EEYANNA (U+0D92))

        """.trimIndent()

        Assert.assertEquals(
            expectedStdout,
            AdbProtocolUtils.byteBufferToString(shellV2Result.stdout)
        )
        Assert.assertEquals(
            expectedStderr,
            AdbProtocolUtils.byteBufferToString(shellV2Result.stderr)
        )
        Assert.assertEquals(10, shellV2Result.exitCode)
    }

    @Test
    fun testShellV2WithErroneousStdinMaintainsInitialException(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val errorInputChannel = object : AdbInputChannel {
            private var firstCall = true
            override suspend fun readBuffer(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
                if (firstCall) {
                    firstCall = false
                    buffer.put('a'.code.toByte())
                    buffer.put('a'.code.toByte())
                } else {
                    throw MyTestException("hello")
                }
            }

            override fun close() {
                // Nothing
            }
        }

        // Act
        exceptionRule.expect(MyTestException::class.java)
        /*val ignored = */deviceServices.shellV2(
                deviceSelector,
                "cat",
                TextShellV2Collector(),
                stdinChannel = errorInputChannel
            ).first()

        // Assert
        Assert.fail() // Should not reach
    }

    @Test
    fun testShellV2WithCancelledStdinMaintainsCancellation(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val errorInputChannel = object : AdbInputChannel {
            private var firstCall = true
            override suspend fun readBuffer(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
                if (firstCall) {
                    firstCall = false
                    buffer.put('a'.code.toByte())
                    buffer.put('a'.code.toByte())
                } else {
                    // We force a cancellation after the first call
                    throw CancellationException("hello")
                }
            }

            override fun close() {
                // Nothing
            }
        }

        // Act
        exceptionRule.expect(CancellationException::class.java)
        /*val ignored = */deviceServices.shellV2(
                deviceSelector,
                "cat",
                TextShellV2Collector(),
                stdinChannel = errorInputChannel
            ).first()

        // Assert
        Assert.fail() // Should not reach
    }

    @Test
    fun testShellAsTextWorks(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val input = """
            stdout: This is some text with
            stdout: split in multiple lines
            stderr: and containing non-ascii
            stdout: characters such as
            stderr: - ඒ (SINHALA LETTER EEYANNA (U+0D92))
            stdout: - ⿉ (KANGXI RADICAL MILLET (U+2FC9))
            exit: 10
        """.trimIndent()

        // Act
        val commandOutput = deviceServices.shellAsText(
                deviceSelector,
                "shell-protocol-echo",
                stdinChannel = input.asAdbInputChannel(deviceServices.session)
            )

        // Assert
        val expectedStdout = """
            This is some text with
            split in multiple lines
            characters such as
            - ⿉ (KANGXI RADICAL MILLET (U+2FC9))

        """.trimIndent()

        val expectedStderr = """
            and containing non-ascii
            - ඒ (SINHALA LETTER EEYANNA (U+0D92))

        """.trimIndent()

        Assert.assertEquals(expectedStdout, commandOutput.stdout)
        Assert.assertEquals(expectedStderr, commandOutput.stderr)
        Assert.assertEquals(10, commandOutput.exitCode)
    }

    @Test
    fun testShellV2AsLineWorks(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val input = """
            stdout: This is some text with
            stdout: split in multiple lines
            stderr: and containing non-ascii
            stdout: characters such as
            stderr: - ඒ (SINHALA LETTER EEYANNA (U+0D92))
            stdout: - ⿉ (KANGXI RADICAL MILLET (U+2FC9))
            exit: 10
        """.trimIndent()

        // Act
        val collectedStdout = ArrayList<String>()
        val collectedStderr = ArrayList<String>()
        var collectedExitCode = -1
        deviceServices.shellAsLines(
            deviceSelector,
            "shell-protocol-echo",
            stdinChannel = input.asAdbInputChannel(deviceServices.session)
        ).collect { entry ->
            when (entry) {
                is ShellCommandOutputElement.StdoutLine -> collectedStdout.add(entry.contents)
                is ShellCommandOutputElement.StderrLine -> collectedStderr.add(entry.contents)
                is ShellCommandOutputElement.ExitCode -> collectedExitCode = entry.exitCode
            }
        }

        // Assert
        val expectedStdout = """
            This is some text with
            split in multiple lines
            characters such as
            - ⿉ (KANGXI RADICAL MILLET (U+2FC9))

        """.trimIndent()

        val expectedStderr = """
            and containing non-ascii
            - ඒ (SINHALA LETTER EEYANNA (U+0D92))

        """.trimIndent()

        Assert.assertEquals(expectedStdout, collectedStdout.joinToString(separator = "\n"))
        Assert.assertEquals(expectedStderr, collectedStderr.joinToString(separator = "\n"))
        Assert.assertEquals(10, collectedExitCode)
    }

    @Test
    fun testShellInputChannelCollectorWorks(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val input = """
            stdout: This is some text with
            stdout: split in multiple lines
            stderr: and containing non-ascii
            stdout: characters such as
            stderr: - ඒ (SINHALA LETTER EEYANNA (U+0D92))
            stdout: - ⿉ (KANGXI RADICAL MILLET (U+2FC9))
            exit: 10
        """.trimIndent()

        // Act
        val collectedStdout = ArrayList<String>()
        val collectedStderr = ArrayList<String>()
        var collectedExitCode = -1
        deviceServices.shellCommand(deviceSelector, "shell-protocol-echo")
            .withStdin(input.asAdbInputChannel(deviceServices.session))
            .withInputChannelCollector()
            .executeAsSingleOutput { inputChannelOutput ->
                // The first element we collect is the only one.
                // We read data from stdin and stdout input channels until EOF
                // Then we get the exitCode
                val job1 = launchCancellable {
                    AdbInputChannelReader(inputChannelOutput.stdout).use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            collectedStdout.add(line)
                        }
                    }
                }

                val job2 = launchCancellable {
                    AdbInputChannelReader(inputChannelOutput.stderr).use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            collectedStderr.add(line)
                        }
                    }
                }

                joinAll(job1, job2)
                collectedExitCode = inputChannelOutput.exitCode.filterNotNull().first()
            }

        // Assert
        val expectedStdout = """
            This is some text with
            split in multiple lines
            characters such as
            - ⿉ (KANGXI RADICAL MILLET (U+2FC9))
        """.trimIndent()

        val expectedStderr = """
            and containing non-ascii
            - ඒ (SINHALA LETTER EEYANNA (U+0D92))
        """.trimIndent()

        Assert.assertEquals(expectedStdout, collectedStdout.joinToString(separator = "\n"))
        Assert.assertEquals(expectedStderr, collectedStderr.joinToString(separator = "\n"))
        Assert.assertEquals(10, collectedExitCode)
    }

    @Test
    fun testShellInputChannelCollectorWorksOnAnyDispatcher(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val input = "stdout: This is some text"

        // Act
        for (dispatcher in listOf(Dispatchers.IO, Dispatchers.Default)) {
            withContext(dispatcher) {
                println("Running test for dispatcher: $dispatcher")
                var collectedStdout: String
                deviceServices.shellCommand(deviceSelector, "shell-protocol-echo")
                    .withStdin(input.asAdbInputChannel(deviceServices.session))
                    .withInputChannelCollector()
                    .executeAsSingleOutput { inputChannelOutput ->
                        AdbInputChannelReader(inputChannelOutput.stdout).use { reader ->
                            collectedStdout = reader.readLine() ?: ""
                        }

                        // Assert
                        val expectedStdout = "This is some text"
                        Assert.assertEquals(expectedStdout, collectedStdout)
                    }
            }
        }
    }

    @Test
    fun testShellCommandExecuteSingleOutputIsTransparentToShellExceptions(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        // Act: We disconnect the device while waiting on a "echo" command that will
        // never produce any output.
        val input = deviceServices.session.channelFactory.createPipedChannel()
        // Note: In theory, we should get an EOFException here, but on Windows we (sometimes)
        // get a "java.io.IOException: The specified network name is no longer available", which
        // corresponds to Win32 error code 64 ("ERROR_NETNAME_DELETED"). This is due do the
        // fact adblib uses async. socket channel API, which sometimes lead to this behavior
        // when a socket is closed while a async i/o operation is pending.
        exceptionRule.expect(IOException::class.java)
        deviceServices.shellCommand(deviceSelector, "shell-protocol-echo")
            .withStdin(input)
            .withInputChannelCollector()
            .executeAsSingleOutput { inputChannelOutput ->
                // A device disconnect should result in an `EOFException` for the shell service.
                fakeAdb.disconnectDevice(fakeDevice.deviceId)

                // Wait forever: the "echo" does not terminate on its own since we
                // don't send it an "exit:" command
                inputChannelOutput.exitCode.first { it != null }
            }

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testShellCommandExecuteSingleOutputIsTransparentToExceptions(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val input = """
            stdout: This is some text with
            stdout: split in multiple lines
            stderr: and containing non-ascii
            stdout: characters such as
            stderr: - ඒ (SINHALA LETTER EEYANNA (U+0D92))
            stdout: - ⿉ (KANGXI RADICAL MILLET (U+2FC9))
            exit: 10
        """.trimIndent()

        // Act
        // Note: 99% of the time, we get an [MyTestException], but we sometimes
        // get a [ClosedChannelException] coming from the cancellation of the underlying
        // [AdbPipedInputChannelImpl] used by the [InputChannelCollector] to forward
        // stdout and stderr. It is unclear why we get this exception when the cause
        // of cancellation is always [MyCancellationException].
        exceptionRule.expect(anyExceptionOf(MyTestException::class.java,
                                            ClosedChannelException::class.java))
        val collectedStdout = ArrayList<String>()
        val collectedStderr = ArrayList<String>()
        deviceServices.shellCommand(deviceSelector, "shell-protocol-echo")
            .withStdin(input.asAdbInputChannel(deviceServices.session))
            .withInputChannelCollector()
            .executeAsSingleOutput<Unit> { inputChannelOutput ->
                // The first element we collect is the only one.
                // We read data from stdin and stdout input channels until EOF
                // Then we get the exitCode
                launchCancellable {
                    AdbInputChannelReader(inputChannelOutput.stdout).use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            collectedStdout.add(line)
                        }
                    }
                }

                launchCancellable {
                    AdbInputChannelReader(inputChannelOutput.stderr).use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            collectedStderr.add(line)
                        }
                    }
                }
                throw MyTestException("My Message")
            }

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testShellCommandExecuteSingleOutputIsTransparentToExceptionsInStdoutReader(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val input = """
            stdout: This is some text with
            exit: 10
        """.trimIndent()

        // Act
        // Note: 99% of the time, we get an [MyTestException], but we sometimes
        // get a [ClosedChannelException] coming from the cancellation of the underlying
        // [AdbPipedInputChannelImpl] used by the [InputChannelCollector] to forward
        // stdout and stderr. It is unclear why we get this exception when the cause
        // of cancellation is always [MyCancellationException].
        exceptionRule.expect(anyExceptionOf(MyTestException::class.java,
                                            ClosedChannelException::class.java))
        coroutineScope {
            deviceServices.shellCommand(deviceSelector, "shell-protocol-echo")
                .withStdin(input.asAdbInputChannel(deviceServices.session))
                .withInputChannelCollector()
                .executeAsSingleOutput { inputChannelOutput ->
                    launchCancellable {
                        AdbInputChannelReader(inputChannelOutput.stdout).use {
                            throw MyTestException("My Message")
                        }
                    }

                    launchCancellable {
                        AdbInputChannelReader(inputChannelOutput.stderr).use {
                        }
                    }
                }
        }

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testShellCommandExecuteSingleOutputIsTransparentToExceptionsInStderrReader(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val input = """
            stdout: This is some text with
            exit: 10
        """.trimIndent()

        // Act
        // Note: 99% of the time, we get an [MyTestException], but we sometimes
        // get a [ClosedChannelException] coming from the cancellation of the underlying
        // [AdbPipedInputChannelImpl] used by the [InputChannelCollector] to forward
        // stdout and stderr. It is unclear why we get this exception when the cause
        // of cancellation is always [MyCancellationException].
        exceptionRule.expect(anyExceptionOf(MyTestException::class.java,
                                            ClosedChannelException::class.java))
        coroutineScope {
            deviceServices.shellCommand(deviceSelector, "shell-protocol-echo")
                .withStdin(input.asAdbInputChannel(deviceServices.session))
                .withInputChannelCollector()
                .executeAsSingleOutput { inputChannelOutput ->
                    launchCancellable {
                        AdbInputChannelReader(inputChannelOutput.stdout).use {
                        }
                    }

                    launchCancellable {
                        AdbInputChannelReader(inputChannelOutput.stderr).use {
                            throw MyTestException("My Message")
                        }
                    }
                }
        }

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testShellCommandExecuteSingleOutputAllowsCancellation(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val input = """
            stdout: This is some text with
            stdout: split in multiple lines
            stderr: and containing non-ascii
            stdout: characters such as
            stderr: - ඒ (SINHALA LETTER EEYANNA (U+0D92))
            stdout: - ⿉ (KANGXI RADICAL MILLET (U+2FC9))
            exit: 10
        """.trimIndent()

        // Act
        // Note: 99% of the time, we get a [CancellationException], but we sometimes
        // get a [ClosedChannelException] coming from the cancellation of the underlying
        // [AdbPipedInputChannelImpl] used by the [InputChannelCollector] to forward
        // stdout and stderr. It is unclear why we get this exception when the cause
        // of cancellation is always [CancellationException].
        exceptionRule.expect(anyExceptionOf(CancellationException::class.java,
                                            ClosedChannelException::class.java))
        val collectedStdout = ArrayList<String>()
        val collectedStderr = ArrayList<String>()
        coroutineScope {
            deviceServices.shellCommand(deviceSelector, "shell-protocol-echo")
                .withStdin(input.asAdbInputChannel(deviceServices.session))
                .withInputChannelCollector()
                .executeAsSingleOutput { inputChannelOutput ->
                    // The first element we collect is the only one.
                    // We read data from stdin and stdout input channels until EOF
                    // Then we get the exitCode
                    launchCancellable {
                        AdbInputChannelReader(inputChannelOutput.stdout).use { reader ->
                            while (true) {
                                val line = reader.readLine() ?: break
                                collectedStdout.add(line)
                            }
                        }
                    }

                    launchCancellable {
                        AdbInputChannelReader(inputChannelOutput.stderr).use { reader ->
                            while (true) {
                                val line = reader.readLine() ?: break
                                collectedStderr.add(line)
                            }
                        }
                    }
                    cancel("My Message")
                }
        }

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testShellCommandExecuteSingleOutputAllowsCancellationInStdoutReader(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val input = """
            stdout: This is some text with
            stdout: split in multiple lines
            stderr: and containing non-ascii
            stdout: characters such as
            stderr: - ඒ (SINHALA LETTER EEYANNA (U+0D92))
            stdout: - ⿉ (KANGXI RADICAL MILLET (U+2FC9))
            exit: 10
        """.trimIndent()

        // Act
        // Note: 99% of the time, we get an [MyCancellationException], but we sometimes
        // get a [ClosedChannelException] coming from the cancellation of the underlying
        // [AdbPipedInputChannelImpl] used by the [InputChannelCollector] to forward
        // stdout and stderr. It is unclear why we get this exception when the cause
        // of cancellation is always [MyCancellationException].
        exceptionRule.expect(anyExceptionOf(MyCancellationException::class.java,
                                            ClosedChannelException::class.java))
        coroutineScope {
            deviceServices.shellCommand(deviceSelector, "shell-protocol-echo")
                .withStdin(input.asAdbInputChannel(deviceServices.session))
                .withInputChannelCollector()
                .executeAsSingleOutput { inputChannelOutput ->
                    // The first element we collect is the only one.
                    // We read data from stdin and stdout input channels until EOF
                    // Then we get the exitCode
                    launchCancellable {
                        AdbInputChannelReader(inputChannelOutput.stdout).use {
                            throw MyCancellationException("My Message")
                        }
                    }

                    launchCancellable {
                        AdbInputChannelReader(inputChannelOutput.stderr).use {
                        }
                    }
                    delay(1_000)
                }
        }

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testShellCommandExecuteSingleOutputAllowsCancellationInStderrReader(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val input = """
            stdout: This is some text with
            stderr: This is some stderr
            exit: 10
        """.trimIndent()

        // Act
        // Note: 99% of the time, we get an [MyCancellationException], but we sometimes
        // get a [ClosedChannelException] coming from the cancellation of the underlying
        // [AdbPipedInputChannelImpl] used by the [InputChannelCollector] to forward
        // stdout and stderr. It is unclear why we get this exception when the cause
        // of cancellation is always [MyCancellationException].
        exceptionRule.expect(anyExceptionOf(MyCancellationException::class.java,
                                            ClosedChannelException::class.java))
        coroutineScope {
            deviceServices.shellCommand(deviceSelector, "shell-protocol-echo")
                .withStdin(input.asAdbInputChannel(deviceServices.session))
                .withInputChannelCollector()
                .executeAsSingleOutput { inputChannelOutput ->
                    launchCancellable {
                        AdbInputChannelReader(inputChannelOutput.stdout).use {
                        }
                    }

                    launchCancellable {
                        AdbInputChannelReader(inputChannelOutput.stderr).use {
                            throw MyCancellationException("My Message")
                        }
                    }
                    delay(1_000)
                }
        }

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testShellCommandUsesLegacyExecIfSupported(): Unit = runBlockingWithTimeout {
        // Prepare
        // "exec" is supported starting API 21
        val fakeDevice = addFakeDevice(fakeAdb, 21)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        // Act
        var effectiveProtocol = ShellCommand.Protocol.SHELL_V2
        deviceServices.shellCommand(deviceSelector, "getprop")
            .allowLegacyExec(true)
            .withTextCollector()
            .withCommandOverride { command, protocol ->
                effectiveProtocol = protocol
                command
            }.execute().collect()

        // Assert
        Assert.assertEquals(ShellCommand.Protocol.EXEC, effectiveProtocol)
    }

    @Test
    fun testShellCommandUsesLegacyShellIfSupported(): Unit = runBlockingWithTimeout {
        // Prepare
        // Below API 21, only "shell" is supported
        val fakeDevice = addFakeDevice(fakeAdb, 19)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        // Act
        var effectiveProtocol = ShellCommand.Protocol.SHELL_V2
        deviceServices.shellCommand(deviceSelector, "getprop")
            .withTextCollector()
            .withCommandOverride { command, protocol ->
                effectiveProtocol = protocol
                command
            }.execute()
            .collect()

        // Assert
        Assert.assertEquals(ShellCommand.Protocol.SHELL, effectiveProtocol)
    }

    @Test
    fun testShellCommandWithCommandOutputTimeoutUsesLegacyExecIfSupported(): Unit = runBlockingWithTimeout {
        // Prepare
        // "exec" is supported starting API 21
        val fakeDevice = addFakeDevice(fakeAdb, 21)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        // Act
        var effectiveProtocol = ShellCommand.Protocol.SHELL_V2
        deviceServices.shellCommand(deviceSelector, "getprop")
            .allowLegacyExec(true)
            .withTextCollector()
            .withCommandOutputTimeout(Duration.ofSeconds(2))
            .withCommandOverride { command, protocol ->
                effectiveProtocol = protocol
                command
            }.execute().collect()

        // Assert
        Assert.assertEquals(ShellCommand.Protocol.EXEC, effectiveProtocol)
    }

    @Test
    fun testShellCommandWithCommandOutputTimeoutUsesLegacyShellIfSupported(): Unit = runBlockingWithTimeout {
        // Prepare
        // Below API 21, only "shell" is supported
        val fakeDevice = addFakeDevice(fakeAdb, 19)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        // Act
        var effectiveProtocol = ShellCommand.Protocol.SHELL_V2
        deviceServices.shellCommand(deviceSelector, "getprop")
            .withTextCollector()
            .withCommandOutputTimeout(Duration.ofSeconds(2))
            .withCommandOverride { command, protocol ->
                effectiveProtocol = protocol
                command
            }.execute()
            .collect()

        // Assert
        Assert.assertEquals(ShellCommand.Protocol.SHELL, effectiveProtocol)
    }

    @Test
    fun testShellCommandThrowsIfNoCollectorSet(): Unit = runBlockingWithTimeout {
        // Prepare
        // Below API 21, only "shell" is supported
        val fakeDevice = addFakeDevice(fakeAdb, 19)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        // Act
        exceptionRule.expect(IllegalArgumentException::class.java)
        deviceServices.shellCommand(deviceSelector, "getprop")
            .execute()
            .collect()

        // Assert
        Assert.fail("Should not be reached")
    }

    @Test
    fun testShellCommandThrowsIfLegacyShellProtocolNotAllowedOnOldDevice(): Unit = runBlockingWithTimeout {
        // Prepare
        // Below API 21, only "shell" is supported
        val fakeDevice = addFakeDevice(fakeAdb, 19)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        // Act
        exceptionRule.expect(IllegalArgumentException::class.java)
        deviceServices.shellCommand(deviceSelector, "getprop")
            .allowLegacyShell(false)
            .execute()
            .collect()

        // Assert
        Assert.fail("Should not be reached")
    }

    @Test
    fun testShellCommandStripsCrLfOnOldDevices(): Unit = runBlockingWithTimeout {
        // Prepare
        // Below API 21, only "shell" is supported
        val fakeDevice = addFakeDevice(fakeAdb, 19)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        // Act
        val output = deviceServices.shellCommand(deviceSelector, "getprop")
                .withTextCollector()
                .execute()
                .first()
                .stdout

        // Assert
        val expectedOutput = """
            # This is some build info
            # This is more build info

            [ro.build.version.release]: [model]
            [ro.build.version.sdk]: [19]
            [ro.product.cpu.abi]: [x86_64]
            [ro.product.manufacturer]: [test1]
            [ro.product.model]: [test2]
            [ro.serialno]: [1234]

        """.trimIndent()
        Assert.assertEquals(expectedOutput, output)
    }

    @Test
    fun testShellCommandAllowsCrLfOnOldDevices(): Unit = runBlockingWithTimeout {
        // Prepare
        // Below API 21, only "shell" is supported
        val fakeDevice = addFakeDevice(fakeAdb, 19)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        // Act
        val output = deviceServices.shellCommand(deviceSelector, "getprop")
                .withTextCollector()
                .allowStripCrLfForLegacyShell(false)
                .execute()
                .first()
                .stdout

        // Assert
        val expectedOutput = """
            # This is some build info
            # This is more build info

            [ro.build.version.release]: [model]
            [ro.build.version.sdk]: [19]
            [ro.product.cpu.abi]: [x86_64]
            [ro.product.manufacturer]: [test1]
            [ro.product.model]: [test2]
            [ro.serialno]: [1234]

        """.trimIndent().replace("\n", "\r\n")
        Assert.assertEquals(expectedOutput, output)
    }

    // Checks public contract of the ShellCommandOutputElement.*.toString methods.
    @Test
    fun testShellCommandOutputElement(): Unit = runBlockingWithTimeout {
        Assert.assertEquals(
            "contents",
            ShellCommandOutputElement.StdoutLine("contents").toString()
        )
        Assert.assertEquals(
            "contents",
            ShellCommandOutputElement.StderrLine("contents").toString()
        )
        Assert.assertEquals(
            "42",
            ShellCommandOutputElement.ExitCode(42).toString()
        )
    }

    @Test
    fun testDevicePropertiesAllWorks(): Unit = runBlockingWithTimeout {
        // Prepare
        val device = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)

        // Act
        val props = deviceServices.deviceProperties(deviceSelector).all().associate { it.name to it.value }

        // Assert
        Assert.assertEquals("30", props["ro.build.version.sdk"])
        Assert.assertEquals("model", props["ro.build.version.release"])
        Assert.assertEquals("test2", props["ro.product.model"])
    }

    @Test
    fun testDevicePropertiesAllWorksOnApi16(): Unit = runBlockingWithTimeout {
        // Prepare
        val device = addFakeDevice(fakeAdb, sdk = 16)
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)

        // Act
        val props = deviceServices.deviceProperties(deviceSelector).all().associate { it.name to it.value }

        // Assert
        Assert.assertEquals("16", props["ro.build.version.sdk"])
        Assert.assertEquals("model", props["ro.build.version.release"])
        Assert.assertEquals("test2", props["ro.product.model"])
    }

    @Test
    fun testDevicePropertiesAllReadonlyWorks() = runBlockingWithTimeout {
        // Prepare
        val device = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)
        // Wait until device shows up as a connected device
        yieldUntil {
            deviceServices.session.connectedDevicesTracker.connectedDevices.value.isNotEmpty()
        }

        // Act
        // Call 3 times so that we can assert later there was only one connection
        val props = deviceServices.deviceProperties(deviceSelector).allReadonly()

        val connectionCountBefore = fakeAdb.channelProvider.createdChannels.size
        val props2 = deviceServices.deviceProperties(deviceSelector).allReadonly()
        val props3 = deviceServices.deviceProperties(deviceSelector).allReadonly()
        val connectionCountAfter = fakeAdb.channelProvider.createdChannels.size

        // Assert
        Assert.assertEquals("30", props["ro.build.version.sdk"])
        Assert.assertEquals("model", props["ro.build.version.release"])
        Assert.assertEquals("test2", props["ro.product.model"])

        // Assert we made no additional connections
        Assert.assertEquals(connectionCountBefore, connectionCountAfter)
        Assert.assertSame(props, props2)
        Assert.assertSame(props, props3)
    }

    @Test
    fun testDevicePropertiesApiWorks(): Unit = runBlockingWithTimeout {
        // Prepare
        val device = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)

        // Act
        val api = deviceServices.deviceProperties(deviceSelector).api()

        // Assert
        Assert.assertEquals(30, api)
    }

    @Test
    fun testSyncSendFileWorks(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        val filePath = "/sdcard/foo/bar.bin"
        val fileBytes = createFileBytes(128_000)
        val fileMode = RemoteFileMode.fromPosixPermissions(OWNER_READ, OWNER_WRITE)
        val fileDate = FileTime.from(1_000_000, TimeUnit.SECONDS)
        val progress = TestSyncProgress()

        // Act
        deviceServices.syncSend(
            deviceSelector,
            AdbInputStreamChannel(deviceServices.session.host, fileBytes.inputStream()),
            filePath,
            fileMode,
            fileDate,
            progress,
            bufferSize = 1_024
        )

        // Assert
        Assert.assertTrue(progress.started)
        Assert.assertTrue(progress.progress)
        Assert.assertTrue(progress.done)

        Assert.assertNotNull(fakeDevice.getFile(filePath))
        fakeDevice.getFile(filePath)?.run {
            Assert.assertEquals(filePath, path)
            Assert.assertEquals(fileMode.modeBits, permission)
            Assert.assertEquals(fileDate.toMillis() / 1_000, modifiedDate.toLong())
            Assert.assertEquals(fileBytes.size, bytes.size)
        }
    }

    @Test
    fun testSyncSendEmptyFileWorks(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        val filePath = "/sdcard/foo/bar.bin"
        val fileBytes = createFileBytes(0)
        val fileMode = RemoteFileMode.fromPosixPermissions(OWNER_READ, OWNER_WRITE)
        val fileDate = FileTime.from(1_000_000, TimeUnit.SECONDS)
        val progress = TestSyncProgress()

        // Act
        deviceServices.syncSend(
            deviceSelector,
            AdbInputStreamChannel(deviceServices.session.host, fileBytes.inputStream()),
            filePath,
            fileMode,
            fileDate,
            progress,
            bufferSize = 1_024
        )

        // Assert
        Assert.assertTrue(progress.started)
        Assert.assertFalse(progress.progress)
        Assert.assertTrue(progress.done)

        Assert.assertNotNull(fakeDevice.getFile(filePath))
        fakeDevice.getFile(filePath)?.run {
            Assert.assertEquals(filePath, path)
            Assert.assertEquals(fileMode.modeBits, permission)
            Assert.assertEquals(fileDate.toMillis() / 1_000, modifiedDate.toLong())
            Assert.assertEquals(fileBytes.size, bytes.size)
        }
    }

    @Test
    fun testSyncSendWithTimeoutWorks(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        val filePath = "/sdcard/foo/bar.bin"
        val fileMode = RemoteFileMode.fromPosixPermissions(OWNER_READ, OWNER_WRITE)
        val fileDate = FileTime.from(1_000_000, TimeUnit.SECONDS)
        val progress = TestSyncProgress()
        val slowInputChannel = object : AdbInputChannel {
            var firstCall = true
            override suspend fun readBuffer(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
                if (firstCall) {
                    firstCall = false
                    buffer.putInt(5)
                } else {
                    delay(200)
                }
            }

            override fun close() {
            }
        }

        // Act
        exceptionRule.expect(TimeoutException::class.java)
        deviceServices.session.host.timeProvider.withErrorTimeout(Duration.ofMillis(100)) {
            deviceServices.syncSend(
                deviceSelector,
                slowInputChannel,
                filePath,
                fileMode,
                fileDate,
                progress,
                bufferSize = 1_024
            )
        }

        // Assert
        Assert.fail() // Should not be reached
    }

    @Test
    fun testSyncSendRethrowsProgressException(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        val filePath = "/sdcard/foo/bar.bin"
        val fileBytes = createFileBytes(1_024)
        val fileMode = RemoteFileMode.fromPosixPermissions(OWNER_READ, OWNER_WRITE)
        val fileDate = FileTime.from(1_000_000, TimeUnit.SECONDS)
        val progress = object : TestSyncProgress() {
            override suspend fun transferProgress(remotePath: String, totalBytesSoFar: Long) {
                throw MyTestException("An error in progress callback")
            }
        }

        // Act
        exceptionRule.expect(MyTestException::class.java)
        deviceServices.syncSend(
            deviceSelector,
            AdbInputStreamChannel(deviceServices.session.host, fileBytes.inputStream()),
            filePath,
            fileMode,
            fileDate,
            progress,
            bufferSize = 1_024
        )

        // Assert
        Assert.fail() // Should not be reached
    }

    @Test
    fun testSyncSendTwoFilesInSameSessionWorks(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        val filePath = "/sdcard/foo/bar.bin"
        val fileBytes = createFileBytes(128_000)
        val fileMode = RemoteFileMode.fromPosixPermissions(OWNER_READ, OWNER_WRITE)
        val fileDate = FileTime.from(1_000_000, TimeUnit.SECONDS)
        val fileProgress = TestSyncProgress()

        val filePath2 = "/sdcard/foo/bar2.bin"
        val fileBytes2 = createFileBytes(96_000)
        val fileMode2 = RemoteFileMode.fromPosixPermissions(OWNER_READ, OWNER_WRITE)
        val fileDate2 = FileTime.from(2_000_000, TimeUnit.SECONDS)
        val fileProgress2 = TestSyncProgress()

        // Act
        deviceServices.sync(deviceSelector).use {
            it.send(
                AdbInputStreamChannel(deviceServices.session.host, fileBytes.inputStream()),
                filePath,
                fileMode,
                fileDate,
                fileProgress,
                bufferSize = 1_024
            )

            it.send(
                AdbInputStreamChannel(deviceServices.session.host, fileBytes2.inputStream()),
                filePath2,
                fileMode2,
                fileDate2,
                fileProgress2,
                bufferSize = 1_024
            )
        }

        // Assert
        Assert.assertTrue(fileProgress.started)
        Assert.assertTrue(fileProgress.progress)
        Assert.assertTrue(fileProgress.done)

        Assert.assertNotNull(fakeDevice.getFile(filePath))
        fakeDevice.getFile(filePath)?.run {
            Assert.assertEquals(filePath, path)
            Assert.assertEquals(fileMode.modeBits, permission)
            Assert.assertEquals(fileDate.toMillis() / 1_000, modifiedDate.toLong())
            Assert.assertArrayEquals(fileBytes, bytes)
        }

        Assert.assertTrue(fileProgress2.started)
        Assert.assertTrue(fileProgress2.progress)
        Assert.assertTrue(fileProgress2.done)

        Assert.assertNotNull(fakeDevice.getFile(filePath2))
        fakeDevice.getFile(filePath2)?.run {
            Assert.assertEquals(filePath2, path)
            Assert.assertEquals(fileMode2.modeBits, permission)
            Assert.assertEquals(fileDate2.toMillis() / 1_000, modifiedDate.toLong())
            Assert.assertArrayEquals(fileBytes2, bytes)
        }
    }

    @Test
    fun testSyncSendFileWithNoDateSetsModifiedDateToCurrentTime(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        val filePath = "/sdcard/foo/bar.bin"
        val fileBytes = createFileBytes(1_024)
        val fileMode = RemoteFileMode.fromPosixPermissions(OWNER_READ, OWNER_WRITE)
        val progress = TestSyncProgress()

        val currentTime = Instant.ofEpochSecond(1_234_567L)
        (deviceServices.session.host as TestingAdbSessionHost).overrideUtcNow = currentTime

        // Act
        deviceServices.syncSend(
            deviceSelector,
            AdbInputStreamChannel(deviceServices.session.host, fileBytes.inputStream()),
            filePath,
            fileMode,
            null,
            progress,
            bufferSize = 1_024
        )

        // Assert
        Assert.assertTrue(progress.started)
        Assert.assertTrue(progress.progress)
        Assert.assertTrue(progress.done)

        Assert.assertNotNull(fakeDevice.getFile(filePath))
        fakeDevice.getFile(filePath)?.run {
            Assert.assertEquals(currentTime.epochSecond, modifiedDate.toLong())
        }
    }

    @Test
    fun testSyncRecvFileWorks(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        val filePath = "/sdcard/foo/bar.bin"
        val fileBytes = createFileBytes(128_000)
        val fileMode = RemoteFileMode.fromPosixPermissions(OWNER_READ, OWNER_WRITE)
        val fileDate = FileTime.from(1_000_000, TimeUnit.SECONDS)
        fakeDevice.createFile(
            DeviceFileState(
                filePath,
                fileMode.modeBits,
                (fileDate.toMillis() / 1_000).toInt(),
                fileBytes
            )
        )
        val progress = TestSyncProgress()
        val outputStream = ByteArrayOutputStream()
        val outputChannel = AdbOutputStreamChannel(deviceServices.session, outputStream)

        // Act
        deviceServices.syncRecv(
            deviceSelector,
            filePath,
            outputChannel,
            progress,
            bufferSize = 1_024
        )

        // Assert
        Assert.assertTrue(progress.started)
        Assert.assertTrue(progress.progress)
        Assert.assertTrue(progress.done)

        Assert.assertArrayEquals(fileBytes, outputStream.toByteArray())
    }

    @Test
    fun testSyncRecvTwoFileInSameSessionWorks(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        val filePath = "/sdcard/foo/bar.bin"
        val fileBytes = createFileBytes(10)
        val fileMode = RemoteFileMode.fromPosixPermissions(OWNER_READ, OWNER_WRITE)
        val fileDate = FileTime.from(1_000_000, TimeUnit.SECONDS)
        fakeDevice.createFile(
            DeviceFileState(
                filePath,
                fileMode.modeBits,
                (fileDate.toMillis() / 1_000).toInt(),
                fileBytes
            )
        )
        val progress = TestSyncProgress()
        val outputStream = ByteArrayOutputStream()
        val outputChannel = AdbOutputStreamChannel(deviceServices.session, outputStream)

        val filePath2 = "/sdcard/foo/bar2.bin"
        val fileBytes2 = createFileBytes(12)
        val fileMode2 = RemoteFileMode.fromPosixPermissions(OWNER_READ, OWNER_WRITE)
        val fileDate2 = FileTime.from(2_000_000, TimeUnit.SECONDS)
        fakeDevice.createFile(
            DeviceFileState(
                filePath2,
                fileMode2.modeBits,
                (fileDate2.toMillis() / 1_000).toInt(),
                fileBytes2
            )
        )
        val progress2 = TestSyncProgress()
        val outputStream2 = ByteArrayOutputStream()
        val outputChannel2 = AdbOutputStreamChannel(deviceServices.session, outputStream2)

        // Act
        deviceServices.sync(deviceSelector).use {
            it.recv(
                filePath,
                outputChannel,
                progress,
                bufferSize = 1_024
            )

            it.recv(
                filePath2,
                outputChannel2,
                progress2,
                bufferSize = 1_024
            )
        }

        // Assert
        Assert.assertTrue(progress.started)
        Assert.assertTrue(progress.progress)
        Assert.assertTrue(progress.done)

        Assert.assertArrayEquals(fileBytes, outputStream.toByteArray())

        Assert.assertTrue(progress2.started)
        Assert.assertTrue(progress2.progress)
        Assert.assertTrue(progress2.done)

        Assert.assertArrayEquals(fileBytes2, outputStream2.toByteArray())
    }

    @Test
    fun testSyncSendThenRecvFileInSameSessionWorks(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        val filePath = "/sdcard/foo/bar.bin"
        val fileBytes = createFileBytes(10_000)
        val fileMode = RemoteFileMode.fromPosixPermissions(OWNER_READ, OWNER_WRITE)
        val fileDate = FileTime.from(1_000_000, TimeUnit.SECONDS)
        val sendProgress = TestSyncProgress()

        val recvProgress = TestSyncProgress()
        val outputStream = ByteArrayOutputStream()
        val outputChannel = AdbOutputStreamChannel(deviceServices.session, outputStream)

        // Act
        deviceServices.sync(deviceSelector).use {
            it.send(
                AdbInputStreamChannel(deviceServices.session.host, fileBytes.inputStream()),
                filePath,
                fileMode,
                fileDate,
                sendProgress,
                bufferSize = 1_024
            )
            it.recv(
                filePath,
                outputChannel,
                recvProgress,
                bufferSize = 1_024
            )
        }

        // Assert
        Assert.assertTrue(sendProgress.started)
        Assert.assertTrue(sendProgress.progress)
        Assert.assertTrue(sendProgress.done)

        Assert.assertNotNull(fakeDevice.getFile(filePath))
        fakeDevice.getFile(filePath)?.run {
            Assert.assertEquals(filePath, path)
            Assert.assertEquals(fileMode.modeBits, permission)
            Assert.assertEquals(fileDate.toMillis() / 1_000, modifiedDate.toLong())
            Assert.assertArrayEquals(fileBytes, bytes)
        }

        Assert.assertTrue(recvProgress.started)
        Assert.assertTrue(recvProgress.progress)
        Assert.assertTrue(recvProgress.done)

        Assert.assertArrayEquals(fileBytes, outputStream.toByteArray())
    }

    /**
     * This test tries do download a file that does not exist on the device
     */
    @Test
    fun testSyncRecvFileThrowsExceptionIfFileDoesNotExist(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        val filePath = "/sdcard/foo/bar.bin"
        val progress = TestSyncProgress()
        val outputStream = ByteArrayOutputStream()
        val outputChannel = AdbOutputStreamChannel(deviceServices.session, outputStream)

        // Act
        exceptionRule.expect(AdbFailResponseException::class.java)
        deviceServices.syncRecv(
            deviceSelector,
            filePath,
            outputChannel,
            progress,
            bufferSize = 1_024
        )

        // Assert
        Assert.fail() // Should not be reachable
    }

    @Test
    fun testSyncRecvRethrowsProgressException(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        val filePath = "/sdcard/foo/bar.bin"
        val fileBytes = createFileBytes(128_000)
        val fileMode = RemoteFileMode.fromPosixPermissions(OWNER_READ, OWNER_WRITE)
        val fileDate = FileTime.from(1_000_000, TimeUnit.SECONDS)
        fakeDevice.createFile(
            DeviceFileState(
                filePath,
                fileMode.modeBits,
                (fileDate.toMillis() / 1_000).toInt(),
                fileBytes
            )
        )
        val progress = object : TestSyncProgress() {
            override suspend fun transferProgress(remotePath: String, totalBytesSoFar: Long) {
                throw MyTestException("An error in progress callback")
            }
        }
        val outputStream = ByteArrayOutputStream()
        val outputChannel = AdbOutputStreamChannel(deviceServices.session, outputStream)

        // Act
        exceptionRule.expect(MyTestException::class.java)
        deviceServices.syncRecv(
            deviceSelector,
            filePath,
            outputChannel,
            progress,
            bufferSize = 1_024
        )

        // Assert
        Assert.fail() // Should not be reached
    }

    @Test
    fun testSyncRecvRethrowsOutputChannelException(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        val filePath = "/sdcard/foo/bar.bin"
        val fileBytes = createFileBytes(128_000)
        val fileMode = RemoteFileMode.fromPosixPermissions(OWNER_READ, OWNER_WRITE)
        val fileDate = FileTime.from(1_000_000, TimeUnit.SECONDS)
        fakeDevice.createFile(
            DeviceFileState(
                filePath,
                fileMode.modeBits,
                (fileDate.toMillis() / 1_000).toInt(),
                fileBytes
            )
        )
        val progress = TestSyncProgress()
        val outputChannel = object : AdbOutputChannel {
            override suspend fun writeBuffer(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
                throw MyTestException("this stream simulates an error writing to local storage")
            }

            override fun close() {
            }
        }

        // Act
        exceptionRule.expect(MyTestException::class.java)
        deviceServices.syncRecv(
            deviceSelector,
            filePath,
            outputChannel,
            progress,
            bufferSize = 1_024
        )

        // Assert
        Assert.fail() // Should not be reached
    }

    @Test
    fun testSyncStatFileWorks(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        val filePath = "/sdcard/foo/bar.bin"
        val fileBytes = createFileBytes(128_000)
        val fileMode = RemoteFileMode.fromPosixPermissions(OWNER_READ, OWNER_WRITE)
        val fileDate = FileTime.from(1_000_000, TimeUnit.SECONDS)
        fakeDevice.createFile(
            DeviceFileState(
                filePath,
                fileMode.modeBits,
                (fileDate.toMillis() / 1_000).toInt(),
                fileBytes
            )
        )

        // Act
        val fileStat = deviceServices.syncStat(
            deviceSelector,
            filePath
        )

        // Assert
        Assert.assertNotNull(fileStat)
        Assert.assertEquals(fileMode.modeBits, fileStat!!.remoteFileMode.modeBits)
        Assert.assertEquals(128_000, fileStat!!.size)
        Assert.assertEquals(1_000_000, fileStat!!.lastModified.toInstant().epochSecond)
    }

    @Test
    fun testReverseForward(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        // Act
        val port = deviceServices.reverseForward(
                deviceSelector,
                SocketSpec.Tcp(),
                SocketSpec.Tcp(4000)
            )

        // Assert
        Assert.assertTrue(port != null && port.toInt() > 0)
    }

    @Test
    fun testReverseForwardNoRebind(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        val port = deviceServices.reverseForward(
                deviceSelector,
                SocketSpec.Tcp(),
                SocketSpec.Tcp(4000)
            )?.toIntOrNull()
            ?: throw AssertionError("Port should have been an integer")

        // Act
        exceptionRule.expect(AdbFailResponseException::class.java)
        runBlocking {
            deviceServices.reverseForward(
                DeviceSelector.any(),
                SocketSpec.Tcp(port),
                SocketSpec.Tcp(4000),
                rebind = false
            )
        }

        // Assert
        Assert.fail()
    }

    @Test
    fun testReverseForwardRebind(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        val port = runBlocking {
            deviceServices.reverseForward(
                deviceSelector,
                SocketSpec.Tcp(),
                SocketSpec.Tcp(4000)
            )
        }?.toIntOrNull() ?: throw AssertionError("Port should have been an integer")

        // Act
        val port2 = deviceServices.reverseForward(
                deviceSelector,
                SocketSpec.Tcp(port),
                SocketSpec.Tcp(4000),
                rebind = true
            )

        // Assert
        Assert.assertNull(port2)
    }

    @Test
    fun testReverseKillForward(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        val port = runBlocking {
            deviceServices.reverseForward(
                deviceSelector,
                SocketSpec.Tcp(),
                SocketSpec.Tcp(4000)
            )
        } ?: throw Exception("`forward` command should have returned a port")
        Assert.assertEquals(1, fakeDevice.allReversePortForwarders.size)

        // Act
        deviceServices.reverseKillForward(
            deviceSelector,
            SocketSpec.Tcp(port.toInt())
        )

        // Assert
        Assert.assertEquals(0, fakeDevice.allReversePortForwarders.size)
    }

    @Test
    fun testReverseKillForwardAll(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        deviceServices.reverseForward(
            deviceSelector,
            SocketSpec.Tcp(),
            SocketSpec.Tcp(4000)
        )
        Assert.assertEquals(1, fakeDevice.allReversePortForwarders.size)

        // Act
        runBlocking {
            deviceServices.reverseKillForwardAll(deviceSelector)
        }

        // Assert
        Assert.assertEquals(0, fakeDevice.allPortForwarders.size)
    }

    @Test
    fun testReverseListForward(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        deviceServices.reverseForward(
            deviceSelector,
            SocketSpec.Tcp(1000),
            SocketSpec.Tcp(4000)
        )
        Assert.assertEquals(1, fakeDevice.allReversePortForwarders.size)

        // Act
        val reverseList = runBlocking {
            deviceServices.reverseListForward(deviceSelector)
        }

        // Assert
        Assert.assertEquals(1, reverseList.size)
        Assert.assertEquals(0, reverseList.errors.size)
        reverseList[0].let { forwardEntry ->
            Assert.assertEquals("UsbFfs", forwardEntry.transportName)
            Assert.assertEquals("tcp:1000", forwardEntry.remote.toQueryString())
            Assert.assertEquals("tcp:4000", forwardEntry.local.toQueryString())
        }
    }

    @Test
    fun testAbbExec(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val appId = "com.foo.bar.app"

        // Act
        val resp = StringBuilder()
        val flow = deviceServices.abb_exec(
            deviceSelector,
            listOf("package", "path", appId),
            TextShellCollector()
        )
        flow.collect {
            resp.append(it)
        }

        // Assert
        Assert.assertEquals("/data/app/$appId/base.apk", resp.toString())
    }

    @Test
    fun testAbb(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val appId = "com.foo.bar.app"

        // Act
        val shellOutput = run {
            val flow = deviceServices.abb(
                deviceSelector,
                listOf("package", "path", appId),
                TextShellV2Collector()
            )
            // With TextShellV2Collector, there is always a single result
            flow.first()
        }

        // Assert
        Assert.assertEquals("/data/app/$appId/base.apk", shellOutput.stdout)
    }

    @Test
    fun testAbbProvidesStderrAndExitCode(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        // Act
        val shellOutput = run {
            val flow = deviceServices.abb(
                deviceSelector,
                listOf("invalid_service_name"),
                TextShellV2Collector()
            )
            // With TextShellV2Collector, there is always a single result
            flow.first()
        }

        // Assert
        Assert.assertEquals("", shellOutput.stdout)
        Assert.assertEquals(
            "Error: Service 'invalid_service_name' is not supported",
            shellOutput.stderr
        )
        Assert.assertEquals(5, shellOutput.exitCode)
    }

    val SESSION_PATTERN = Pattern.compile("""Success: .*\[(\d*)\].*""")
    private fun extractSessionID(output: String) : String {
        val matcher: Matcher = SESSION_PATTERN.matcher(output.trim())
        when {
            matcher.matches() -> return matcher.group(1)
            else -> throw IllegalStateException("Unexpected session ID message ($output)")
        }
    }

    @Test
    fun testAbbWithStdin(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val dataToSend = ByteArray(1_000_000)
        val stdin = AdbInputStreamChannel(deviceServices.session.host, dataToSend.inputStream())

        // Run a create session. It should create session 0 in the package manager
        val sessionOutput = run {
            val flow = deviceServices.abb(
                deviceSelector,
                listOf("package", "install-create"),
                TextShellV2Collector(),
            )
            flow.first().stdout
        }
        val sessionID = extractSessionID(sessionOutput)

        // Act
        val shellOutput = run {
            val flow = deviceServices.abb(
                deviceSelector,
                listOf("package", "install-write", "-S", dataToSend.size.toString(), sessionID, "-"),
                TextShellV2Collector(),
                stdinChannel = stdin
            )
            // With TextShellV2Collector, there is always a single result
            flow.first()
        }

        // Assert
        Assert.assertEquals("Success: streamed ${dataToSend.size} bytes\n", shellOutput.stdout)
        Assert.assertEquals("", shellOutput.stderr)
        Assert.assertEquals(0, shellOutput.exitCode)
    }

    @Test
    fun testRoot(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        // Act
        val status = deviceServices.root(deviceSelector)

        // Assert
        Assert.assertTrue(status.restarting)
        Assert.assertTrue(status.status.startsWith("restarting"))
        Assert.assertEquals(status.rawStatus, status.status + '\n')
    }

    @Test
    fun testUnRoot(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        fakeAdb.fakeAdbServer.restartDeviceAsync(fakeDevice) {
            it.copy(isRoot = true)
        }.await()

        // Act
        val status = deviceServices.unRoot(deviceSelector)

        // Assert
        Assert.assertTrue(status.restarting)
        Assert.assertTrue(status.status.startsWith("restarting"))
        Assert.assertEquals(status.rawStatus, status.status + '\n')
    }

    @Test
    fun testUnRootIfAlreadyUnRoot(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        // Act
        val status = deviceServices.unRoot(deviceSelector)

        // Assert
        Assert.assertFalse(status.restarting)
        Assert.assertEquals(status.rawStatus, status.status + '\n')
    }

    @Test
    fun testRootAndWait(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        // Act
        val status = deviceServices.rootAndWait(deviceSelector)

        // Assert
        Assert.assertTrue(status.restarting)
        Assert.assertTrue(status.status.startsWith("restarting"))
        Assert.assertEquals(status.rawStatus, status.status + '\n')
    }

    @Test
    fun testUnRootAndWait(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        fakeAdb.fakeAdbServer.restartDeviceAsync(fakeDevice) {
            it.copy(isRoot = true)
        }.await()

        // Act
        val status = deviceServices.unRootAndWait(deviceSelector)

        // Assert
        Assert.assertTrue(status.restarting)
        Assert.assertTrue(status.status.startsWith("restarting"))
        Assert.assertEquals(status.rawStatus, status.status + '\n')
    }

    @Test
    fun testTrackJdwp(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        addClient(fakeDevice, 50) // Add a single client to start with

        // Act: Collect 4 times, adding 1 client each time
        val changeCount = 4
        val lists = run {
            val flow = deviceServices.trackJdwp(deviceSelector)
            flow.takeSome(changeCount) { index, _ ->
                addClient(fakeDevice, 100 + index * 2)
            }.toList()
        }

        // Assert: We should have 4 lists of 1, 2, 3 and 4 elements
        Assert.assertEquals(changeCount, lists.size)
        Assert.assertEquals(listOf(50), lists[0].toList())
        Assert.assertEquals(listOf(50, 100), lists[1].toList())
        Assert.assertEquals(listOf(50, 100, 102), lists[2].toList())
        Assert.assertEquals(listOf(50, 100, 102, 104), lists[3].toList())
    }

    @Test
    fun testTrackAppFlowWorks(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb, sdk = 31)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        addProfileableProcess(fakeDevice, 50) // Add a single client to start with

        // Act: Collect 4 times, adding 1 client each time
        val changeCount = 4
        val lists = run {
            val flow = deviceServices.trackApp(deviceSelector)
            flow.takeSome(changeCount) { index, _ ->
                addProfileableProcess(fakeDevice, 100 + index * 2)
            }.toList()
        }

        // Assert: We should have 4 lists of 1, 2, 3 and 4 elements
        Assert.assertEquals(changeCount, lists.size)
        Assert.assertEquals(listOf(50), lists[0].map { it.pid }.toList())
        Assert.assertEquals(listOf(50, 100), lists[1].map { it.pid }.toList())
        Assert.assertEquals(listOf(50, 100, 102), lists[2].map { it.pid }.toList())
        Assert.assertEquals(listOf(50, 100, 102, 104), lists[3].map { it.pid }.toList())
    }

    @Test
    fun testTrackAppFlowThrowsOnOlderDevices(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb, sdk = 30)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        // Act
        exceptionRule.expect(AdbFailResponseException::class.java)
        deviceServices.trackApp(deviceSelector)
            .collect {
            }

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testTrackAppFlowThrowsIfInvalidDeviceSelector(): Unit = runBlockingWithTimeout {
        // Prepare

        // Act
        exceptionRule.expect(AdbFailResponseException::class.java)
        deviceServices.trackApp(DeviceSelector.fromSerialNumber("1"))
            .collect {
            }

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testTrackAppFlowIsTransparentToExceptions(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb, sdk = 31)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        addProfileableProcess(fakeDevice, 50) // Add a single client to start with

        // Act
        exceptionRule.expect(IOException::class.java)
        exceptionRule.expectMessage("MyMessage")
        deviceServices.trackApp(deviceSelector).collect {
            throw IOException("MyMessage")
        }

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testTrackAppFlowIsTransparentToCancellation(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb, sdk = 31)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        addProfileableProcess(fakeDevice, 50) // Add a single client to start with

        // Act
        exceptionRule.expect(CancellationException::class.java)
        exceptionRule.expectMessage("MyMessage")
        deviceServices.trackApp(deviceSelector).collect {
            cancel("MyMessage")
        }

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testJdwpSessionOpens(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val pid = 10
        addClient(fakeDevice, pid)

        // Act
        val sessionOpened =
            deviceServices.jdwp(deviceSelector, pid).use {
                true
            }

        // Assert
        Assert.assertTrue(sessionOpened)
    }

    @Test
    fun testShellCommandLegacyExecSupportedDefaultsToFalse(): Unit = runBlockingWithTimeout {
        // Prepare
        // "exec" is supported starting API 21
        val fakeDevice = addFakeDevice(fakeAdb, 21)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        // Act
        var effectiveProtocol = ShellCommand.Protocol.SHELL_V2
        deviceServices.shellCommand(deviceSelector, "getprop")
            .withTextCollector()
            .withCommandOverride { command, protocol ->
                effectiveProtocol = protocol
                command
            }.execute().collect()

        // Assert: "shell_v2" fallbacks to "shell" and not to "exec"
        Assert.assertEquals(ShellCommand.Protocol.SHELL, effectiveProtocol)
    }

    @Test
    fun testShellCommandForceShellV2(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        // Act
        var effectiveProtocol = ShellCommand.Protocol.EXEC
        deviceServices.shellCommand(deviceSelector, "getprop")
            .forceShellV2()
            .withTextCollector()
            .withCommandOverride { command, protocol ->
                effectiveProtocol = protocol
                command
            }.execute().collect()

        // Assert
        Assert.assertEquals(ShellCommand.Protocol.SHELL_V2, effectiveProtocol)
    }

    @Test
    fun testShellCommandForceLegacyExec(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        // Act
        var effectiveProtocol = ShellCommand.Protocol.SHELL_V2
        deviceServices.shellCommand(deviceSelector, "getprop")
            .forceLegacyExec()
            .withTextCollector()
            .withCommandOverride { command, protocol ->
                effectiveProtocol = protocol
                command
            }.execute().collect()

        // Assert: "exec" is chosen even though the device supports shell_v2
        Assert.assertEquals(ShellCommand.Protocol.EXEC, effectiveProtocol)
    }

    @Test
    fun testShellCommandForceLegacyShell(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        // Act
        var effectiveProtocol = ShellCommand.Protocol.SHELL_V2
        deviceServices.shellCommand(deviceSelector, "getprop")
            .forceLegacyShell()
            .withTextCollector()
            .withCommandOverride { command, protocol ->
                effectiveProtocol = protocol
                command
            }.execute().collect()

        // Assert: "shell" is chosen even though the device supports shell_v2 and exec
        Assert.assertEquals(ShellCommand.Protocol.SHELL, effectiveProtocol)
    }

    @Test
    fun testShellCommandForceShellV2_throwsWhenDeviceDoesNotSupportShellV2(): Unit =
        runBlockingWithTimeout {
            // Prepare
            val fakeDevice = addFakeDevice(fakeAdb, sdk = 21)
            val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

            // Act
            exceptionRule.expect(IllegalArgumentException::class.java)
            deviceServices.shellCommand(deviceSelector, "getprop")
                .forceShellV2()
                .withTextCollector()
                .execute().collect()

            // Assert
            Assert.fail("Should not be reached")
        }

    @Test
    fun testShellCommandForceLegacyExec_throwsWhenDeviceDoesNotSupportExec(): Unit =
        runBlockingWithTimeout {
            // Prepare
            val fakeDevice = addFakeDevice(fakeAdb, sdk = 19)
            val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

            // Act
            exceptionRule.expect(IllegalArgumentException::class.java)
            deviceServices.shellCommand(deviceSelector, "getprop")
                .forceLegacyExec()
                .withTextCollector()
                .execute().collect()

            // Assert
            Assert.fail("Should not be reached")
        }

    @Test
    fun testAbbCommandThrows_whenAbbIsNotSupported(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb, sdk = 19)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val appId = "com.foo.bar.app"

        // Act
        exceptionRule.expect(IllegalArgumentException::class.java)
        deviceServices.abbCommand(deviceSelector, listOf("package", "path", appId))
            .withTextCollector()
            .execute()
            .first()
            .stdout

        // Assert
        Assert.fail("Should not be reached")
    }

    @Test
    fun testAbbCommandWithStdin(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val dataToSend = ByteArray(1_000_000)
        val stdin = AdbInputStreamChannel(deviceServices.session.host, dataToSend.inputStream())

        // Run a create session. It should create session 0 in the package manager
        val sessionOutput = deviceServices.abbCommand(deviceSelector, listOf("package", "install-create"))
            .withTextCollector()
            .execute()
            .first()
            .stdout
        val sessionID = extractSessionID(sessionOutput)

        // Act
        val shellOutput =
            deviceServices.abbCommand(
                deviceSelector,
                listOf("package", "install-write", "-S", dataToSend.size.toString(), sessionID, "-")
            )
                .withTextCollector()
                .withStdin(stdin)
                .execute()
                .first()

        // Assert
        Assert.assertEquals("Success: streamed ${dataToSend.size} bytes\n", shellOutput.stdout)
        Assert.assertEquals("", shellOutput.stderr)
        Assert.assertEquals(0, shellOutput.exitCode)
    }

    @Test
    fun testAbbCommandWithMonitoringCanDetectInactiveCommand(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        fakeDevice.delayStdout = 10000.toDuration(DurationUnit.MILLISECONDS)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        // Act
        exceptionRule.expect(TimeoutException::class.java)
        /*val ignored = */deviceServices.abbCommand(deviceSelector, listOf("package", "-l"))
                .forceShellV2Protocol()
                .withLegacyCollector(TextShellCollector())
                .withCommandOutputTimeout(Duration.ofMillis(100))
                .execute()
                .first()

        // Assert
        Assert.fail() // Should not reach
    }

    @Test
    fun testAbbExecCommandWithMonitoringCanDetectInactiveCommand(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        fakeDevice.delayStdout = 10000.toDuration(DurationUnit.MILLISECONDS)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        // Act
        exceptionRule.expect(TimeoutException::class.java)
        /*val ignored = */deviceServices.abbCommand(deviceSelector, listOf("package", "-l"))
                .forceExecProtocol()
                .withLegacyCollector(TextShellCollector())
                .withCommandOutputTimeout(Duration.ofMillis(100))
                .execute()
                .first()

        // Assert
        Assert.fail() // Should not reach
    }

    @Test
    fun testAbbWithMonitoringWorksAsLongAsTimeoutIsNotExceeded(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        fakeDevice.delayStdout = 10.toDuration(DurationUnit.MILLISECONDS)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        // Act
        val text =
            deviceServices.abbCommand(deviceSelector, listOf("package", "-l"))
                .forceShellV2Protocol()
                .withLegacyCollector(TextShellCollector())
                // We have an "inactivity" timeout of 100 msec, but each read takes only 10 msec
                .withCommandOutputTimeout(Duration.ofMillis(100))
                .execute()
                .first()

        // Assert
        Assert.assertEquals("package:one\npackage:two\npackage:three\n", text)
    }

    @Test
    fun testAbbExecWithMonitoringWorksAsLongAsTimeoutIsNotExceeded(): Unit = runBlockingWithTimeout {
        // Prepare
        val fakeDevice = addFakeDevice(fakeAdb)
        fakeDevice.delayStdout = 10.toDuration(DurationUnit.MILLISECONDS)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        // Act
        val text =
            deviceServices.abbCommand(deviceSelector, listOf("package", "-l"))
                .forceExecProtocol()
                .withLegacyCollector(TextShellCollector())
                // We have an "inactivity" timeout of 100 msec, but each read takes only 10 msec
                .withCommandOutputTimeout(Duration.ofMillis(100))
                .execute()
                .first()

        // Assert
        Assert.assertEquals("package:one\npackage:two\npackage:three\n", text)
    }

    @Test
    fun testShellToOutputStreams(): Unit = runBlockingWithTimeout {
        // Prepare
        val device = addFakeDevice(fakeAdb)
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val input = """
            stdout: output
            stderr: error
            exit: 5
        """.trimIndent()

        // Act
        val exitCode = deviceServices.shellCommand(deviceSelector, "shell-protocol-echo")
            .withStdin(input.asAdbInputChannel(deviceServices.session))
            .withCollector(OutputStreamCollector(fakeAdbRule.adbSession, stdout, stderr))
            .execute()
            .first()

        // Assert
        Assert.assertEquals("output\n", stdout.toString())
        Assert.assertEquals("error\n", stderr.toString())
        Assert.assertEquals(5, exitCode)
    }


    /**
     * Similar to [Flow.take], but allows for a [block] to process each collected element.
     */
    private suspend fun <T> Flow<T>.takeSome(count: Int, block: suspend (Int, T) -> Unit): Flow<T> {
        var consumed = 0
        return takeWhile {
            if (consumed < count) {
                block(consumed++, it)
                true
            } else {
                false
            }
        }
    }

    open class TestSyncProgress : SyncProgress {

        var started = false
        var progress = false
        var done = false

        override suspend fun transferStarted(remotePath: String) {
            started = true
        }

        override suspend fun transferProgress(remotePath: String, totalBytesSoFar: Long) {
            progress = true
        }

        override suspend fun transferDone(remotePath: String, totalBytes: Long) {
            done = true
        }
    }

    private fun createFileBytes(size: Int): ByteArray {
        val result = ByteArray(size)
        for (i in 0 until size) {
            result[i] = (i and 0xff).toByte()
        }
        return result
    }

    private fun addFakeDevice(fakeAdb: FakeAdbServerProvider, sdk: Int = 30): DeviceState {
        val fakeDevice =
            fakeAdb.connectDevice(
                "1234",
                "test1",
                "test2",
                "model",
                "$sdk",
                DeviceState.HostConnectionType.USB
            )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        return fakeDevice
    }

    private fun addClient(fakeDevice: DeviceState, pid: Int): ClientState {
        return fakeDevice.startClient(
            pid,
            pid * 2,
            "package-$pid",
            "app-$pid",
            true
        )
    }

    private fun addProfileableProcess(fakeDevice: DeviceState, pid: Int): ProfileableProcessState {
        return fakeDevice.startProfileableProcess(pid, "x86", "")
    }

    class ByteBufferShellCollector : ShellCollector<ByteBuffer> {

        private val buffer = ResizableBuffer()

        override suspend fun start(collector: FlowCollector<ByteBuffer>) {
        }

        override suspend fun collect(collector: FlowCollector<ByteBuffer>, stdout: ByteBuffer) {
            buffer.appendBytes(stdout)
        }

        override suspend fun end(collector: FlowCollector<ByteBuffer>) {
            collector.emit(buffer.forChannelWrite())
        }
    }

    class ShellV2ResultCollector : ShellV2Collector<ShellV2Result> {

        private val stdoutBuffer = ResizableBuffer()
        private val stderrBuffer = ResizableBuffer()

        override suspend fun start(collector: FlowCollector<ShellV2Result>) {
        }

        override suspend fun collectStdout(
            collector: FlowCollector<ShellV2Result>,
            stdout: ByteBuffer
        ) {
            stdoutBuffer.appendBytes(stdout)
        }

        override suspend fun collectStderr(
            collector: FlowCollector<ShellV2Result>,
            stderr: ByteBuffer
        ) {
            stderrBuffer.appendBytes(stderr)
        }

        override suspend fun end(
            collector: FlowCollector<ShellV2Result>,
            exitCode: Int
        ) {
            val result =
                ShellV2Result(
                    stdoutBuffer.forChannelWrite(),
                    stderrBuffer.forChannelWrite(),
                    exitCode
                )
            collector.emit(result)
        }
    }

    class ShellV2Result(
        val stdout: ByteBuffer,
        val stderr: ByteBuffer,
        val exitCode: Int
    )

    class MyTestException(message: String) : IOException(message)

    class MyCancellationException(message: String): CancellationException(message)

    companion object {

        @JvmStatic
        @BeforeClass
        fun before() {
            TimeWaitSocketsThrottler.throttleIfNeeded()
        }
    }
}
