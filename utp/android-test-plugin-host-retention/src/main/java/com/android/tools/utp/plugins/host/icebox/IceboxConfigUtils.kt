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

package com.android.tools.utp.plugins.host.icebox

import com.google.common.annotations.VisibleForTesting
import com.google.testing.platform.runtime.android.device.AndroidDevice
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import java.util.logging.Logger
import java.util.regex.Pattern

// Emulator gRPC port
const val DEFAULT_EMULATOR_GRPC_PORT = "8554"

private val LOG = Logger.getLogger("IceboxConfigUtils")

/**
 * Info on the GRPC connection to an Android Emulator instance.
 *
 * @param port: The port that the GRPC connection is on.
 * @param token: The GRPC token of the Android Emulator.
 */
data class EmulatorGrpcInfo(
    val port: Int,
    val token: String?,
    val server_cert: String,
    val jwkDirectory: String,
    val jwkActivePath: String
)

@VisibleForTesting
val DEFAULT_EMULATOR_GRPC_INFO =
    EmulatorGrpcInfo(DEFAULT_EMULATOR_GRPC_PORT.toInt(), "", "", "", "")

class GrpcInfoFinder {

    fun findInfo(deviceSerial: String): EmulatorGrpcInfo {
        try {
            val fileNamePattern = Pattern.compile("pid_\\d+.ini")
            val directory = computeRegistrationDirectoryContainer()?.resolve("avd/running")
            for (file in Files.list(directory)) {
                if (fileNamePattern.matcher(file.fileName.toString()).matches()) {
                    val potentialGrpc = findGrpcInfo(deviceSerial, file) ?: continue
                    return potentialGrpc
                }
            }
            return DEFAULT_EMULATOR_GRPC_INFO
        } catch (exception: Throwable) {
            LOG.fine(
                "Failed to parse emulator gRPC port, fallback to default,"
                        + " exception ${exception}"
            )
            return DEFAULT_EMULATOR_GRPC_INFO
        }
    }
}

/**
 * Returns the Emulator registration directory.
 */
private fun computeRegistrationDirectoryContainer(): Path? {
    val os = System.getProperty("os.name").toLowerCase(Locale.ROOT)
    when {
        os.startsWith("mac") -> {
            return Paths.get(
                System.getenv("HOME") ?: "/",
                "Library",
                "Caches",
                "TemporaryItems"
            )
        }

        os.startsWith("win") -> {
            return Paths.get(System.getenv("LOCALAPPDATA") ?: "/", "Temp")
        }

        else -> { // Linux and Chrome OS.
            for (dirstr in arrayOf(
                System.getenv("XDG_RUNTIME_DIR"),
                "/run/user/${getUid()}",
                System.getenv("ANDROID_EMULATOR_HOME"),
                System.getenv("ANDROID_PREFS_ROOT"),
                System.getenv("ANDROID_SDK_HOME"),
                (System.getenv("HOME") ?: "/") + ".android"
            )) {
                if (dirstr == null) {
                    continue
                }
                try {
                    val dir = Paths.get(dirstr)
                    if (Files.isDirectory(dir)) {
                        return dir
                    }
                } catch (exception: InvalidPathException) {
                    LOG.finer("Failed to parse dir ${dirstr}, exception ${exception}")
                }
            }

            return Paths.get(
                FileUtils.getTempDirectory().absolutePath,
                "android-" + System.getProperty("USER")
            )
        }
    }
}

private fun getUid(): String? {
    try {
        val userName = System.getProperty("user.name")
        val command = "id -u $userName"
        val process = Runtime.getRuntime().exec(command)
        process.inputStream.use {
            val result = String(it.readBytes(), StandardCharsets.UTF_8).trim()
            if (result.isEmpty()) {
                return null
            }
            return result
        }
    } catch (e: IOException) {
        return null
    }
}

/**
 * Attempts to parse the EmulatorGrpcInfo from the given device .ini file.
 *
 * If the deviceSerial does not match that in the .ini file, null is returned.
 * Otherwise, the GrpcInfo of the .ini file is returned.
 */
internal fun findGrpcInfo(deviceSerial: String, file: Path): EmulatorGrpcInfo? {
    var discovered = mutableMapOf<String, String>()
    Files.readAllLines(file).forEach { line ->
        val keyValuePair = line.split("=", limit = 2)
        if (keyValuePair.size == 2) {
            discovered[keyValuePair[0]] = keyValuePair[1]
        }
    }
    val serial = discovered.getOrDefault("port.serial", "")
    val matchedAvd = ("emulator-" + serial == deviceSerial)

    if (matchedAvd) {
        return EmulatorGrpcInfo(
            discovered.getOrDefault("grpc.port", DEFAULT_EMULATOR_GRPC_PORT).toInt(),
            discovered.getOrDefault("grpc.token", ""),
            discovered.getOrDefault("grpc.server_cert", ""),
            discovered.getOrDefault("grpc.jwks", ""),
            discovered.getOrDefault("grpc.jwk_active", "")
        )
    } else {
        return null
    }
}

