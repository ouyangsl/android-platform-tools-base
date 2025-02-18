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
package com.android.adblib.ddmlibcompatibility.testutils

import com.android.adblib.AdbServerChannelProvider
import com.android.adblib.AdbSession
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.TestingAdbSessionHost
import com.android.ddmlib.IDevice
import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.DeviceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.time.Duration

fun FakeAdbRule.createAdbSession(closeables: CloseablesRule): AdbSession {
    val host = TestingAdbSessionHost()
    val channelProvider =
        AdbServerChannelProvider.createOpenLocalHost(host) { this.fakeAdbServerPort }
    return closeables.register(AdbSession.create(host, channelProvider))
}

suspend fun FakeAdbRule.connectTestDevice(
    deviceId: String = "1234",
    sdk: String = "31",
    timeout: Duration = Duration.ofSeconds(2)
): Pair<IDevice, DeviceState> {
    return withTimeout(timeout.toMillis()) {
        val deviceState = this@connectTestDevice.attachDevice(
            deviceId,
            "manuf",
            "model",
            "2022",
            sdk,
            hostConnectionType = DeviceState.HostConnectionType.USB
        )

        // Ensure device is online
        deviceState.deviceStatus = DeviceState.DeviceStatus.ONLINE
        waitForOnlineDevice(deviceState)
    }
}

suspend fun FakeAdbRule.waitForOnlineDevice(
    deviceState: DeviceState,
    timeout: Duration = Duration.ofSeconds(2)
): Pair<IDevice, DeviceState> {
    suspend fun FakeAdbRule.waitOnline(deviceState: DeviceState): Pair<IDevice, DeviceState> {
        while (true) {
            val device = this.bridge.devices.find {
                it.isOnline && it.serialNumber == deviceState.deviceId
            }
            if (device != null) {
                return Pair(device, deviceState)
            }
            delay(20)
        }
    }

    return withTimeout(timeout.toMillis()) {
        waitOnline(deviceState)
    }
}

suspend fun FakeAdbRule.disconnectTestDevice(
    deviceSerial: String,
    timeout: Duration = Duration.ofSeconds(2)
) {
    this.disconnectDevice(deviceSerial)

    withTimeout(timeout.toMillis()) {
        while (true) {
            this@disconnectTestDevice.bridge.devices.find {
                it.serialNumber == deviceSerial
            } ?: break
            delay(20)
        }
    }
}

