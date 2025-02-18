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
package com.android.adblib.tools

import com.android.adblib.testing.FakeAdbSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.rules.TemporaryFolder
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.LinkedBlockingQueue
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.io.path.absolutePathString

class EmulatorConsoleTest {

    private val scope = CoroutineScope(EmptyCoroutineContext)
    private val fakeEmulator = FakeEmulator()

    @JvmField
    @Rule
    val exceptionRule: ExpectedException = ExpectedException.none()

    @JvmField
    @Rule
    val folder = TemporaryFolder()

    @After
    fun tearDown() {
        scope.cancel(null)
    }

    private class FakeEmulator : Runnable {

        val server = ServerSocket(0)
        val port = server.localPort
        var socket: Socket? = null

        val inputQueue = LinkedBlockingQueue<String>()
        val outputQueue = LinkedBlockingQueue<String>()

        fun start() {
            Thread(this, "FakeEmulator").start()
        }

        override fun run() {
            try {
                val socket = server.accept()
                this.socket = socket
                val input = BufferedReader(InputStreamReader(socket.getInputStream()))
                val output = PrintWriter(socket.getOutputStream())
                output.write(outputQueue.take())
                output.flush()
                while (true) {
                    val line = input.readLine() ?: return
                    inputQueue.add(line)
                    val response = outputQueue.take()
                    output.write(response)
                    output.flush()
                }
            } catch (e: IOException) {
                // Ignore socket closing
            }
        }

        fun close() {
            socket?.close()
        }
    }

    fun connectNoAuth(): EmulatorConsole {
        fakeEmulator.start()
        fakeEmulator.outputQueue.put("Hello\r\nOK\r\n")

        return runBlockingWithTimeout {
            FakeAdbSession().openEmulatorConsole(localConsoleAddress(fakeEmulator.port))
        }
    }

    @Test
    fun zeroLineOutput() {
        val console = connectNoAuth()
        val rotateAsync = scope.async {
            console.sendCommand("rotate")
        }

        assertEquals("rotate", fakeEmulator.inputQueue.take())
        fakeEmulator.outputQueue.put("OK\r\n")

        val result = runBlockingWithTimeout { rotateAsync.await() }
        assertEquals(0, result.outputLines.size)
        assertNull(result.error)
    }

    @Test
    fun oneLineOutput() {
        val console = connectNoAuth()

        val avdPathAsync = scope.async {
            console.sendCommand("avd path")
        }

        assertEquals("avd path", fakeEmulator.inputQueue.take())
        fakeEmulator.outputQueue.put("/tmp/avd/nexus_5.avd\r\nOK\r\n")

        val avdPath = runBlockingWithTimeout { avdPathAsync.await() }

        assertFalse(avdPath.isError())
        assertEquals(listOf("/tmp/avd/nexus_5.avd"), avdPath.outputLines)

        fakeEmulator.close()
    }

    @Test
    fun isVmStopped() {
        val console = connectNoAuth()

        val stopped1 = scope.async { console.isVmStopped() }
        assertEquals("avd status", fakeEmulator.inputQueue.take())
        fakeEmulator.outputQueue.put("virtual device is running\r\nOK\r\n")
        runBlockingWithTimeout { assertFalse(stopped1.await()) }

        val stopped2 = scope.async { console.isVmStopped() }
        assertEquals("avd status", fakeEmulator.inputQueue.take())
        fakeEmulator.outputQueue.put("virtual device is stopped\r\nOK\r\n")
        runBlockingWithTimeout { assertTrue(stopped2.await()) }
    }

    @Test
    fun screenRecord() {
        val console = connectNoAuth()
        val recordAsync = scope.async {
            console.startScreenRecording(Paths.get("test.webm"), "--size 800x600")
        }

        assertEquals("screenrecord start --size 800x600 test.webm", fakeEmulator.inputQueue.take())
        fakeEmulator.outputQueue.put("OK\r\n")

        runBlockingWithTimeout { recordAsync.await() }
    }

    @Test
    fun error() {
        val console = connectNoAuth()
        val commandAsync = scope.async {
            console.sendCommand("xyzzy")
        }

        assertEquals("xyzzy", fakeEmulator.inputQueue.take())
        fakeEmulator.outputQueue.put("KO: unknown command\r\n")

        val result = runBlockingWithTimeout { commandAsync.await() }
        assertEquals(0, result.outputLines.size)
        assertEquals("unknown command", result.error)
    }

    @Test
    fun connectAuth() {
        val authTokenPath = folder.root.toPath().resolve(".emulator_console_auth_token")
        Files.writeString(authTokenPath, "my secret token")

        fakeEmulator.start()
        fakeEmulator.outputQueue.put(authPrompt(authTokenPath.absolutePathString()))

        val consoleAsync = scope.async {
            FakeAdbSession().openEmulatorConsole(localConsoleAddress(fakeEmulator.port))
        }

        assertEquals("auth my secret token", fakeEmulator.inputQueue.take())
        fakeEmulator.outputQueue.put("OK\r\n")

        runBlockingWithTimeout { consoleAsync.await() }
    }

    @Test
    fun openEmulatorConsoleThrows_whenAuthTokenFileIsNotFound() = runBlockingWithTimeout {
        // Prepare
        fakeEmulator.start()
        val nonExistentPath = folder.root.toPath().resolve("this_file_does_not_exist.txt")
        fakeEmulator.outputQueue.put(authPrompt(nonExistentPath.absolutePathString()))
        exceptionRule.expect(IOException::class.java)

        // Act
        FakeAdbSession().openEmulatorConsole(localConsoleAddress(fakeEmulator.port))

        // Assert
        fail("Should not reach")
    }

    private fun <T> runBlockingWithTimeout(block: suspend CoroutineScope.() -> T) =
        runBlocking {
            withTimeout(5000) {
                block()
            }
        }
}

private fun authPrompt(authTokenPath: String) =
    "Android Console: Authentication required\r\n" +
        "Android Console: you can find your <auth_token> in \r\n" +
        "'$authTokenPath'\r\n" +
        "OK\r\n"
