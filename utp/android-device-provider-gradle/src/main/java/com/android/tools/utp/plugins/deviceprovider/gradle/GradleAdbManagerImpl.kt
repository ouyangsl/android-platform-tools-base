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

package com.android.tools.utp.plugins.deviceprovider.gradle

import com.google.testing.platform.lib.logging.jvm.getLogger
import com.google.testing.platform.lib.process.inject.SubprocessComponent
import java.util.logging.Logger

/**
 * Component of the [GradleManagedAndroidDeviceLauncher] to handle calls to adb.
 */
class GradleAdbManagerImpl(private val subprocessComponent: SubprocessComponent) : GradleAdbManager {
    private val logger: Logger = getLogger()
    private lateinit var adbPath: String

    override fun configure(adbPath: String) {
        this.adbPath = adbPath
    }

    private fun getAdbIdArgs(serial: String): List<String> =
            listOf(
                    adbPath,
                    "-s",
                    serial,
                    "emu",
                    "avd",
                    "id"
            )

    private fun getAdbCloseArgs(serial: String): List<String> =
            listOf(
                    adbPath,
                    "-s",
                    serial,
                    "emu",
                    "kill"
            )

    /**
     * Returns the serials of all active devices on adb.
     */
    override fun getAllSerials(): List<String> {
        val serials = mutableListOf<String>()

        subprocessComponent.subprocess().executeAsync(
                listOf(adbPath, "devices"),
                environment = System.getenv(),
                stdoutProcessor = { line ->
                    val trimmed = line.trim()
                    val values = trimmed.split("\\s+".toRegex())
                    // Looking for "<serial>    device"
                    if (values.size == 2 && values[1] == "device") {
                        serials.add(values[0])
                    }
                }
        ).waitFor()

        return serials
    }

    /**
     * Returns whether the given device has booted or not
     */
    override fun isBootLoaded(deviceSerial: String): Boolean {
        return isBootCompleted(deviceSerial) && isPackageManagerStarted(deviceSerial)
    }

    private fun isBootCompleted(deviceSerial: String): Boolean {
        var success = false

        subprocessComponent.subprocess().executeAsync(
            args = listOf(
                adbPath,
                "-s",
                deviceSerial,
                "shell",
                "getprop",
                "sys.boot_completed"
            ),
            environment = System.getenv(),
            stdoutProcessor = { line ->
                val trimmed = line.trim()
                if (trimmed == "1") {
                    logger.info("sys.boot_completed=1 ($deviceSerial)")
                    success = true
                }
            }
        )

        if (success) {
            return true
        }

        subprocessComponent.subprocess().executeAsync(
            args = listOf(
                adbPath,
                "-s",
                deviceSerial,
                "shell",
                "getprop",
                "dev.bootcomplete"
            ),
            environment = System.getenv(),
            stdoutProcessor = { line ->
                val trimmed = line.trim()
                if (trimmed == "1") {
                    logger.info("dev.bootcomplete=1 ($deviceSerial)")
                    success = true
                }
            }
        ).waitFor()

        return success
    }

    private fun isPackageManagerStarted(deviceSerial: String): Boolean {
        var success = false

        subprocessComponent.subprocess().executeAsync(
            args = listOf(
                adbPath,
                "-s",
                deviceSerial,
                "shell",
                "/system/bin/pm",
                "path",
                "android"
            ),
            environment = System.getenv(),
            stdoutProcessor = { line ->
                if (line.contains("package:")) {
                    logger.info("Package Manager is ready ($deviceSerial)")
                    success = true
                }
            }
        ).waitFor()

        return success
    }

    /**
     * Returns the id associated with the corresponding serial.
     */
    override fun getId(deviceSerial: String): String? {
        var id: String? = null
        subprocessComponent.subprocess().executeAsync(
                args = getAdbIdArgs(deviceSerial),
                environment = System.getenv(),
                stdoutProcessor = { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && trimmed != "OK") {
                        id = trimmed
                    }
                }
        ).waitFor()
        return id
    }

    override fun closeDevice(deviceSerial: String) {
        subprocessComponent.subprocess().execute(
                args = getAdbCloseArgs(deviceSerial),
                environment = System.getenv()
        )
    }
}
