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

import com.android.ddmlib.AndroidDebugBridge.IClientChangeListener
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener
import com.android.ddmlib.IDevice

/**
 * Provides some basic [com.android.ddmlib.AndroidDebugBridge] functionality in a testable way.
 */
interface AdbAdapter {
  suspend fun getDevices(): List<IDevice>

  fun addDeviceChangeListener(listener: IDeviceChangeListener)

  fun removeDeviceChangeListener(listener: IDeviceChangeListener)

  fun addClientChangeListener(listener: IClientChangeListener)

  fun removeClientChangeListener(listener: IClientChangeListener)
}
