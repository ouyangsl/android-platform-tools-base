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
package com.android.adblib.impl

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.DeviceInfo
import com.android.adblib.DeviceState
import com.android.adblib.deviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class ConnectedDeviceImpl(
  override val session: AdbSession,
  deviceInfo: DeviceInfo
) : ConnectedDevice, AutoCloseable {

    private val deviceInfoStateFlow = MutableStateFlow(deviceInfo)

    private val cacheImpl =
        CoroutineScopeCache.create(session.scope, "$session - device-serial='${deviceInfo.serialNumber}'")

    override val cache: CoroutineScopeCache
        get() = cacheImpl

    override val deviceInfoFlow = deviceInfoStateFlow.asStateFlow()

    override fun close() {
        // Ensure last state we expose is "disconnected"
        deviceInfoStateFlow.update { it.copy(deviceState = DeviceState.DISCONNECTED) }
        cacheImpl.close()
    }

    override fun toString(): String {
        return "${ConnectedDevice::class.simpleName}(serial='${deviceInfo.serialNumber}')"
    }

    fun updateDeviceInfo(deviceInfo: DeviceInfo) {
        assert(deviceInfo.serialNumber == deviceInfoStateFlow.value.serialNumber)
        deviceInfoStateFlow.value = deviceInfo
    }
}
