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
package com.android.processmonitor.common

/**
 * Device tracking events
 */
internal sealed class DeviceEvent<T> {

    /**
     * Sent when a device is and ready to accept ADB request.
     */
    data class DeviceOnline<T>(val device: T) : DeviceEvent<T>()

    /**
     * Sent when a device is disconnected. Note that there is no guarantee this is invoked in all
     * cases. Also note this can be invoked even if a [DeviceOnline] was never sent.
     */
    data class DeviceDisconnected<T>(val serialNumber: String) : DeviceEvent<T>()
}
