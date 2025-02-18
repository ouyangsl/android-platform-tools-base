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

import com.android.adblib.AdbDeviceServices
import com.android.adblib.AdbInputChannel
import com.android.adblib.DeviceSelector
import com.android.adblib.TextShellCollector
import com.android.adblib.TextShellV2Collector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class PMAbb(deviceServices: AdbDeviceServices) : PM(deviceServices) {

    private val CMD = "package"

    // Adb client reference implementation uses abb_exec. We use the same service even though
    // it has edge cases (like necessity to shutdownOutput=false).
    // TODO: Investigate moving to `abb` service instead of `abb_exec`.
    private val useAbbExecService = true

    override suspend fun createSession(device: DeviceSelector, options: List<String>, size: Long) : Flow<String> {
        val cmd = mutableListOf(CMD, "install-create")
        cmd += options
        cmd += "-S"
        cmd += size.toString()

        return if (useAbbExecService) {
            deviceService.abb_exec(device, cmd, TextShellCollector())
        } else {
            deviceService.abb(device, cmd, TextShellV2Collector()).map{ it.stdout + it.stderr }
        }
    }

    override suspend fun streamApk(device: DeviceSelector, sessionID: String, apk: AdbInputChannel, filename: String, size: Long) : Flow<String> {
        // There is no need to escape apk names here since we never hit the shell and abb uses \0
        // separator instead of space.
        val cmd = listOf(CMD, "install-write", "-S", size.toString(), sessionID, filename, "-")
        return if (useAbbExecService) {
            deviceService.abb_exec(device, cmd, TextShellCollector(), apk, shutdownOutput = false)
        } else {
            deviceService.abb(device, cmd, TextShellV2Collector(), apk).map{ it.stdout + it.stderr}
        }
    }

    override suspend fun commit(device: DeviceSelector, sessionID: String) : Flow<String> {
        val cmd = listOf(CMD, "install-commit", sessionID)
        return if (useAbbExecService) {
            deviceService.abb_exec(device, cmd, TextShellCollector())
        } else {
            deviceService.abb(device, cmd, TextShellV2Collector()) .map { it.stdout + it.stderr }
        }
    }

    override suspend fun abandon(device: DeviceSelector, sessionID: String) : Flow<String>{
        val cmd = listOf(CMD, "install-abandon", sessionID)
        return if (useAbbExecService) {
            deviceService.abb_exec(device, cmd, TextShellCollector())
        } else {
            deviceService.abb(device, cmd, TextShellV2Collector()) .map { it.stdout + it.stderr }
        }
    }

    override suspend fun getStrategy(): String {
        return "abb"
    }
}
