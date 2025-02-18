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

import com.google.common.truth.Truth.assertThat
import com.google.testing.platform.lib.process.Handle
import com.google.testing.platform.lib.process.Subprocess
import com.google.testing.platform.lib.process.inject.SubprocessComponent
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyList
import org.mockito.Mockito.anyMap
import org.mockito.Mockito.nullable
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.eq
import java.time.Duration

/**
 * Tests for [GradleAdbManagerImpl]
 */
@RunWith(JUnit4::class)
class GradleAdbManagerTest {
    private companion object {
        const val adbPath = "/path/to/adb"
    }

    @Mock
    private lateinit var subprocessComponent: SubprocessComponent

    @Mock
    private lateinit var subprocess: Subprocess

    private lateinit var adbManager: GradleAdbManager

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        `when`(subprocessComponent.subprocess()).thenReturn(subprocess)

        adbManager = GradleAdbManagerImpl(subprocessComponent)
        adbManager.configure(adbPath)
    }

    @Test
    fun getAllSerials_noSerialsProvided() {
        setAdbOutput(
                listOf(
                        "List of devices attached",
                        ""
                )
        )
        val serials = adbManager.getAllSerials()
        assertThat(serials).isEmpty()
    }

    @Test
    fun getAllSerials_serialsProvided() {
        setAdbOutput(
                listOf(
                        "List of devices attached",
                        "emulator-5554\tdevice",
                        "emulator-5556\tdevice",
                        "someConnectedDevice\tdevice",
                        ""
                )
        )
        val serials = adbManager.getAllSerials()
        assertThat(serials).containsExactly(
                "emulator-5554",
                "emulator-5556",
                "someConnectedDevice"
        )
    }

    @Test
    fun getAllSerials_offlineSerialsSkipped() {
        setAdbOutput(
                listOf(
                        "List of devices attached",
                        "emulator-5554\toffline",
                        "emulator-5556\tdevice",
                        ""
                )
        )
        val serials = adbManager.getAllSerials()
        assertThat(serials).containsExactly("emulator-5556")
    }

    @Test
    fun getId_loadsIdCorrectly() {
        // Unix style adb output.
        setAdbOutput(
            listOf(
                "someDeviceIdHere",
                "OK"
            )
        )
        var id = adbManager.getId("emulator-5554")
        assertThat(id).isEqualTo("someDeviceIdHere")

        // Windows adb output.
        setAdbOutput(
                listOf(
                        "someDeviceIdHere",
                        "",
                        "OK"
                )
        )
        id = adbManager.getId("emulator-5554")
        assertThat(id).isEqualTo("someDeviceIdHere")
    }

    @Test
    fun isBootLoaded_allCases() {
        // Successful boot
        setBootStates(sysBootCompleted = true, packageManagerRunning = true)
        assertThat(adbManager.isBootLoaded("emulator-5554")).isTrue()

        setBootStates(devBootComplete = true, packageManagerRunning = true)
        assertThat(adbManager.isBootLoaded("emulator-5554")).isTrue()

        // Not booted yet
        setBootStates()
        assertThat(adbManager.isBootLoaded("emulator-5554")).isFalse()

        // Booted but package manager hasn't started yet.
        setBootStates(sysBootCompleted = true)
        assertThat(adbManager.isBootLoaded("emulator-5554")).isFalse()

        // Device disconnected
        setAdbOutput(listOf("adb: device 'emulator-5554' not found"))
        assertThat(adbManager.isBootLoaded("emulator-5554")).isFalse()
    }

    private fun setBootStates(
        sysBootCompleted: Boolean = false,
        devBootComplete: Boolean = false,
        packageManagerRunning: Boolean = false,
    ) {
        setAdbOutput(
            listOf(if (sysBootCompleted) "1" else "0"),
            eq(listOf(adbPath, "-s", "emulator-5554", "shell", "getprop", "sys.boot_completed")))
        setAdbOutput(
            listOf(if (devBootComplete) "1" else "0"),
            eq(listOf(adbPath, "-s", "emulator-5554", "shell", "getprop", "dev.bootcomplete")))
        setAdbOutput(
            listOf(if (packageManagerRunning) "package:" else ""),
            eq(listOf(adbPath, "-s", "emulator-5554", "shell", "/system/bin/pm", "path", "android")))
    }

    private fun setAdbOutput(output: List<String>, args: List<String> = anyList()) {
        `when`(
                subprocess.executeAsync(
                        args,
                        anyMap(),
                        nullable(),
                        nullable()
                )
        ).thenAnswer {
            val outProcess = it.getArgument(2) as ((String) -> Unit)?

            for (line in output) {
                outProcess?.invoke(line)
            }

            object : Handle {
                var isRunning: Boolean = true

                override fun exitCode(): Int = 0

                override fun destroy() {
                    isRunning = false
                }

                override fun pid(): Long = 0L

                override fun isAlive(): Boolean = isRunning

                override fun waitFor(timeout: Duration?) = destroy()
            }
        }
    }
}

private inline fun <reified T : Any> nullable(): T? {
    return nullable(T::class.java)
}
