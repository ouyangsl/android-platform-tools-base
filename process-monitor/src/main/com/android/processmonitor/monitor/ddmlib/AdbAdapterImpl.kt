/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.processmonitor.monitor.ddmlib

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.AndroidDebugBridge.IClientChangeListener
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener
import com.android.ddmlib.IDevice
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.guava.await

/**
 * Implementation of [AdbAdapter]
 */
class AdbAdapterImpl(
    private val androidDebugBridgeFuture: ListenableFuture<AndroidDebugBridge>,
) : AdbAdapter {

    override suspend fun getDevices(): List<IDevice> =
        androidDebugBridgeFuture.await().devices.asList()

    override fun addDeviceChangeListener(listener: IDeviceChangeListener) {
        AndroidDebugBridge.addDeviceChangeListener(listener)
    }

    override fun removeDeviceChangeListener(listener: IDeviceChangeListener) {
        AndroidDebugBridge.removeDeviceChangeListener(listener)
    }

    override fun addClientChangeListener(listener: IClientChangeListener) {
        AndroidDebugBridge.addClientChangeListener(listener)
    }

    override fun removeClientChangeListener(listener: IClientChangeListener) {
        AndroidDebugBridge.removeClientChangeListener(listener)
    }
}
