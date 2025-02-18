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
package com.android.fakeadbserver

import com.android.fakeadbserver.services.ExecOutput
import com.android.fakeadbserver.services.LegacyShellOutput
import com.android.fakeadbserver.services.ShellCommandOutput
import com.android.fakeadbserver.services.ShellV2Output
import com.google.common.base.Charsets
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val HEADER_SIZE = 5

enum class ShellProtocolType {
    SHELL {

        override val command: String
            get() = "shell"

        override fun createServiceOutput(socket: Socket, device: DeviceState): ShellCommandOutput {
            return LegacyShellOutput(socket, device)
        }
    },
    EXEC {

        override val command: String
            get() = "exec"

        override fun createServiceOutput(socket: Socket, device: DeviceState): ShellCommandOutput {
            return ExecOutput(socket, device)
        }
    },
    SHELL_V2 {

        override val command: String
            get() = "shell,v2"

        override fun createServiceOutput(socket: Socket, device: DeviceState): ShellCommandOutput {
            return ShellV2Output(socket, device)
        }
    };

    abstract val command: String
    abstract fun createServiceOutput(socket: Socket, device: DeviceState) : ShellCommandOutput
}

class ShellV2Protocol(private val socket: Socket) {

    private val outputStream = socket.getOutputStream()
    private val inputStream = socket.getInputStream()

    fun writeOkay() {
        outputStream.write("OKAY".toByteArray(Charsets.UTF_8))
    }

    fun readPacket(): Packet {
        val header = inputStream.readExactly(HEADER_SIZE)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val kind = PacketKind.fromValue(buffer.get().toInt())
        val length = buffer.getInt()
        val payload = inputStream.readExactly(length)
        return Packet(kind, payload)
    }

    fun writePacket(packet: Packet) {
        val buffer = ByteBuffer
            .allocate(packet.bytes.size + HEADER_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(packet.kind.value.toByte())
        buffer.putInt(packet.bytes.size)
        buffer.put(packet.bytes)
        buffer.flip()
        outputStream.write(buffer.array(), 0, buffer.limit())
    }

    fun writeStdout(bytes: ByteArray) {
        val packet = Packet(PacketKind.STDOUT, bytes)
        writePacket(packet)
    }

    fun writeStdout(text: String) {
        writeStdout(text.toByteArray(Charsets.UTF_8))
    }

    fun writeStderr(bytes: ByteArray) {
        val packet = Packet(PacketKind.STDERR, bytes)
        writePacket(packet)
    }

    fun writeStderr(text: String) {
        writeStderr(text.toByteArray(Charsets.UTF_8))
    }

    fun writeExitCode(exitCode: Int) {
        val packet = Packet(PacketKind.EXIT_CODE, byteArrayOf(exitCode.toByte()))
        writePacket(packet)
        socket.shutdownOrderly()
    }

    class Packet(val kind: PacketKind, val bytes: ByteArray)

    /**
     * Value of the "packet kind" byte in a shell v2 packet
     */
    enum class PacketKind(val value: Int) {

        STDIN(0),
        STDOUT(1),
        STDERR(2),
        EXIT_CODE(3),
        CLOSE_STDIN(4),
        WINDOW_SIZE_CHANGE(HEADER_SIZE),
        INVALID(255);

        companion object {

            fun fromValue(id: Int): PacketKind {
                return values().firstOrNull { it.value == id } ?: INVALID
            }
        }
    }

    private fun InputStream.readExactly(len: Int): ByteArray {
        val buffer = ByteArray(len)
        if (len == 0) {
            return buffer
        }
        var pos = 0
        while (pos < len) {
            val byteCount = read(buffer, pos, len - pos)
            if (byteCount < 0) {
                throw EOFException("Unexpected EOF")
            }
            if (byteCount == 0) {
                throw IOException("Unexpected stream implementation")
            }
            pos += byteCount
        }
        return buffer
    }
}

private fun Socket.shutdownOrderly() {
    shutdownOutput()

    // TODO: See b/228517909, we read input stream until EOF is reached
    // to ensure the all the data sent to the socket is received by the other side
    // of the socket.
    val bytes = ByteArray(16)
    val inputStream = getInputStream()
    while (inputStream.read(bytes, 0, bytes.size) >= 0) {
        // Keep reading
    }

    // Close the socket
    close()
}
