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
package com.android.adblib

/**
 * Identifier of optional features supported by the ADB host and/or ADB daemon.
 * See [AdbHostServices.features].
 */
@Suppress("SpellCheckingInspection")
object AdbFeatures {

    /**
     * If the result of [AdbHostServices.features] contains this value, the
     * [AdbDeviceServices.abb] invocation is supported by the specified device.
     */
    const val ABB = "abb"

    /**
     * If the result of [AdbHostServices.features] contains this value, the
     * [AdbDeviceServices.abb_exec] invocation is supported by the specified device.
     */
    const val ABB_EXEC = "abb_exec"

    /**
     * If the result of [AdbHostServices.features] contains this value, the
     * [AdbDeviceServices.shellV2] invocation is supported by the specified device.
     */
    const val SHELL_V2 = "shell_v2"

    /**
     * If the result of [AdbHostServices.features] contains this value, the
     * device supports listening for device events via host:track-devices-proto-text
     * and host:track-devices-proto-binary.
     */
    const val DEVICE_LIST_BINARY_PROTO = "devicetracker_proto_format"

    /**
     * If the result of [AdbHostServices.features] contains this value, the
     * [AdbDeviceServices.trackApp] invocation is supported by the specified device.
     *
     * Note: "track-app" was added in API 31 (Android "S")
     */
    const val TRACK_APP = "track_app"

    /**
     * If the result of [AdbHostServices.features] contains this value, the
     * [AdbHostServices.serverStatus] invocation is supported by the host.
     *
     * Note: "server-status" was added in adb v35.0.2
     */
    const val SERVER_STATUS = "server_status"
}
