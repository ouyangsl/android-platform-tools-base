/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.utp.plugins.deviceprovider.ddmlib

import com.android.ddmlib.AvdData
import com.android.ddmlib.IDevice
import com.android.ddmlib.MultiLineReceiver
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.google.testing.platform.api.device.Device
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

/**
 * Unit tests for [DdmlibAndroidDevice].
 */
class DdmlibAndroidDeviceTest {

    @get:Rule val mockitoJUnitRule = MockitoJUnit.rule()

    @Mock
    private lateinit var mockIDevice: IDevice
    @Mock
    private lateinit var mockAvdData: AvdData

    @Before
    fun setUpMocks() {
        `when`(mockIDevice.avdData).thenReturn(Futures.immediateFuture(mockAvdData))
        `when`(mockAvdData.name).thenReturn("mockAvdName")
    }

    @Test
    fun physicalDevice() {
        `when`(mockIDevice.isEmulator).thenReturn(false)

        val device = DdmlibAndroidDevice(mockIDevice)

        assertThat(device.type).isEqualTo(Device.DeviceType.PHYSICAL)
    }

    @Test
    fun virtualDevice() {
        `when`(mockIDevice.isEmulator).thenReturn(true)

        val device = DdmlibAndroidDevice(mockIDevice)

        assertThat(device.type).isEqualTo(Device.DeviceType.VIRTUAL)
        assertThat(device.properties.avdName).isEqualTo("mockAvdName")
    }

    @Test
    fun serial() {
        `when`(mockIDevice.serialNumber).thenReturn("serial-1234")

        val device = DdmlibAndroidDevice(mockIDevice)

        assertThat(device.serial).isEqualTo("serial-1234")
    }

    @Test
    fun properties() {
        fun MultiLineReceiver.addOutput(message: String) {
            val bytes = message.toByteArray()
            addOutput(bytes, 0, bytes.size)
            flush()
        }
        `when`(mockIDevice.executeShellCommand(eq("printenv"), any())).then {
            it.getArgument<MultiLineReceiver>(1).addOutput("""
                _=/system/bin/printenv
                ANDROID_DATA=/data
                DOWNLOAD_CACHE=/data/cache
            """.trimIndent())
        }
        `when`(mockIDevice.executeShellCommand(eq("getprop"), any())).then {
            it.getArgument<MultiLineReceiver>(1).addOutput("""
                [dalvik.vm.appimageformat]: [lz4]
                [dalvik.vm.dex2oat-Xms]: [64m]
                [dalvik.vm.dex2oat-Xmx]: [512m]
            """.trimIndent())
        }

        val device = DdmlibAndroidDevice(mockIDevice)

        assertThat(device.properties.map).containsExactly(
                "_", "/system/bin/printenv",
                "ANDROID_DATA", "/data",
                "DOWNLOAD_CACHE", "/data/cache",
                "dalvik.vm.appimageformat", "lz4",
                "dalvik.vm.dex2oat-Xms", "64m",
                "dalvik.vm.dex2oat-Xmx", "512m",
        )
    }

    @Test
    fun testInstallPackages() {
        doNothing(). `when`(mockIDevice).installPackages(
            Mockito.anyList(),
            Mockito.anyBoolean(),
            Mockito.anyList())
        val device = DdmlibAndroidDevice(mockIDevice)
        device.installPackages(listOf(), true, listOf())
        verify(mockIDevice).installPackages(listOf(), true, listOf())
    }
}
