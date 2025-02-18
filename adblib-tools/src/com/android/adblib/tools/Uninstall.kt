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

import com.android.adblib.AdbChannel
import com.android.adblib.AdbDeviceServices
import com.android.adblib.DeviceSelector
import com.android.adblib.TextShellCollector
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.util.concurrent.TimeoutException

class UninstallResult(val output: String) {

    val status: Status = when {
        (output == "Success") -> Status.SUCCESS
        else -> Status.FAILURE
    }

    enum class Status { SUCCESS, FAILURE }
}

/**
 * Uninstall an application identified by its applicationID [applicationID].
 *
 * Always use device 'pm' CLI over SHELL protocol.
 *
 * @param [device] the [DeviceSelector] corresponding to the target device
 * @param [applicationID] the application idenfier (usually a package formatted name).
 * @param [options] parameters directly passed to package manager
 * @param [timeout] timeout tracking the command execution, tracking starts *after* the
 *   device connection has been successfully established. If the command takes more time than
 *   the timeout, a [TimeoutException] is thrown and the underlying [AdbChannel] is closed.
 */
suspend fun AdbDeviceServices.uninstall(
    // The specific device to talk to via the service above
    device: DeviceSelector,
    // The applicationID on that device for that userID
    applicationID: String,
    // The options for uninstall, passed directly to the package manager
    options: List<String> = emptyList(),
    // Timeout
    timeout : Duration = Duration.ofSeconds(10)
): UninstallResult {
    // TODO Improve perf by supporting 'cmd uninstall' and 'abb package uninstall' variants.
    val opts = options.joinToString(" ")
    var cmd = "pm uninstall $opts $applicationID"
    val flow = this.shell(device, cmd, TextShellCollector(), commandTimeout = timeout)
    val output = flow.first()
    return UninstallResult(output)
}
