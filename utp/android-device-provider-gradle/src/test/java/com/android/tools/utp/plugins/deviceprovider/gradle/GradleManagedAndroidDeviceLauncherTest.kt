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

import com.android.tools.utp.plugins.deviceprovider.gradle.GradleManagedAndroidDeviceLauncher.Companion.MANAGED_DEVICE_NAME_KEY
import com.android.tools.utp.plugins.deviceprovider.gradle.proto.GradleManagedAndroidDeviceProviderProto.GradleManagedAndroidDeviceProviderConfig
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.Any
import com.google.testing.platform.api.context.Context
import com.google.testing.platform.api.device.Device
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.api.provider.DeviceProviderConfigImpl
import com.google.testing.platform.core.device.DeviceProviderException
import com.google.testing.platform.proto.api.config.AndroidSdkProto
import com.google.testing.platform.proto.api.config.EnvironmentProto
import com.google.testing.platform.proto.api.config.SetupProto
import com.google.testing.platform.proto.api.core.PathProto
import com.google.testing.platform.runtime.android.device.AndroidDevice
import java.util.function.Supplier
import java.util.logging.Logger
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock

/**
 * Tests for [GradleManagedAndroidDeviceLauncher]
 */
@RunWith(JUnit4::class)
class GradleManagedAndroidDeviceLauncherTest {
    private companion object {
        const val ADB_SERVER_PORT = 5037
        const val deviceName = "device1"
        const val deviceId = "myapp_myDeviceAndroidDebugTest"
    }

    @get:Rule
    val tempFolder = TemporaryFolder()

    @get:Rule
    val mockitoRule: MockitoRule = MockitoJUnit.rule()

    lateinit var logDir: String

    @Mock
    lateinit var emulatorHandle: EmulatorHandle

    @Mock
    lateinit var adbManager: GradleAdbManager

    @Mock
    lateinit var deviceControllerFactory: DeviceControllerFactory

    @Mock
    lateinit var deviceController: DeviceController

    @Mock
    lateinit var mockLogger: Logger

    private lateinit var managedDeviceLauncher: GradleManagedAndroidDeviceLauncher

    private var androidDevice: AndroidDevice? = null

    private val deviceProviderConfig = with(GradleManagedAndroidDeviceProviderConfig.newBuilder()) {
        managedDeviceBuilder.apply {
            avdFolder = Any.pack(PathProto.Path.newBuilder().apply {
                path = "path/to/gradle/avd"
            }.build())
            avdName = deviceName
            avdId = deviceId
            enableDisplay = false
            emulatorPath = Any.pack(PathProto.Path.newBuilder().apply {
                path = "path/to/emulator"
            }.build())
            gradleDslDeviceName = "device1"
        }
        adbServerPort = ADB_SERVER_PORT
        build()
    }

    @Before
    fun setUp() {
        logDir = tempFolder.newFolder().absolutePath
        androidDevice = null
        `when`(
                deviceControllerFactory.getController(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                )
        ).thenReturn(deviceController)
        `when`(deviceController.setDevice(any())).thenAnswer {
            androidDevice = it.getArgument(0) as AndroidDevice
            androidDevice
        }
        `when`(deviceController.getDevice()).thenAnswer {
            androidDevice!!
        }

        managedDeviceLauncher = GradleManagedAndroidDeviceLauncher(
                adbManager,
                emulatorHandle,
                deviceControllerFactory,
                maxDelayMillis = 0,
                mockLogger
        )
    }

    @Test
    fun provideDevice_ensureDeviceProvided() {
        `when`(adbManager.getAllSerials()).thenReturn(listOf("emulator-5554"))
        `when`(adbManager.getId("emulator-5554")).thenReturn(deviceId)
        `when`(adbManager.isBootLoaded("emulator-5554")).thenReturn(true)

        val mockContext = mock<Context>()
        `when`(mockContext[Context.CONFIG_KEY]).thenReturn(
            GradleManagedAndroidDeviceLauncher.DataBoundArgs(
                deviceProviderConfig,
                makeConfigFromDeviceProviderConfig(deviceProviderConfig)
            )
        )
        managedDeviceLauncher.configure(mockContext)

        val device = managedDeviceLauncher.provideDevice().getDevice() as AndroidDevice

        assertThat(device.serial).isEqualTo("emulator-5554")
        assertThat(device.port).isEqualTo(5555)
        assertThat(device.type).isEqualTo(Device.DeviceType.VIRTUAL)
        assertThat(device.emulatorPort).isEqualTo(5554)
        assertThat(device.serverPort).isEqualTo(5037)
        assertThat(device.properties.map[MANAGED_DEVICE_NAME_KEY]).isEqualTo("device1")

        verifyCallToEmulator()
    }

    @Test
    fun provideDevice_ensureEnableDisplayWorks() {
        `when`(adbManager.getAllSerials()).thenReturn(listOf("emulator-5554"))
        `when`(adbManager.getId("emulator-5554")).thenReturn(deviceId)
        `when`(adbManager.isBootLoaded("emulator-5554")).thenReturn(true)

        val enableDisplayConfig =
                with(GradleManagedAndroidDeviceProviderConfig.newBuilder(deviceProviderConfig)) {
                    managedDeviceBuilder.apply {
                        enableDisplay = true
                    }
                    build()
                }

        val mockContext = mock<Context>()
        `when`(mockContext[Context.CONFIG_KEY]).thenReturn(
            GradleManagedAndroidDeviceLauncher.DataBoundArgs(
                enableDisplayConfig,
                makeConfigFromDeviceProviderConfig(enableDisplayConfig)
            )
        )
        managedDeviceLauncher.configure(mockContext)

        val device = managedDeviceLauncher.provideDevice().getDevice() as AndroidDevice

        assertThat(device.serial).isEqualTo("emulator-5554")
        assertThat(device.port).isEqualTo(5555)
        assertThat(device.type).isEqualTo(Device.DeviceType.VIRTUAL)
        assertThat(device.emulatorPort).isEqualTo(5554)
        assertThat(device.serverPort).isEqualTo(5037)
        assertThat(device.properties.map[MANAGED_DEVICE_NAME_KEY]).isEqualTo("device1")

        verifyCallToEmulator(enableDisplay = true)
    }

    @Test
    fun provideDevice_ensureCorrectDeviceProvidedWithMultipleDevices() {
        `when`(adbManager.getAllSerials()).thenReturn(
                listOf("emulator-5554", "emulator-5556")
        )
        `when`(adbManager.getId("emulator-5554")).thenReturn("myapp_myDeviceAndroidVariantTest")
        `when`(adbManager.getId("emulator-5556")).thenReturn(deviceId)
        `when`(adbManager.isBootLoaded("emulator-5556")).thenReturn(true)

        val mockContext = mock<Context>()
        `when`(mockContext[Context.CONFIG_KEY]).thenReturn(
            GradleManagedAndroidDeviceLauncher.DataBoundArgs(
                deviceProviderConfig,
                makeConfigFromDeviceProviderConfig(deviceProviderConfig)
            )
        )
        managedDeviceLauncher.configure(mockContext)

        val device = managedDeviceLauncher.provideDevice().getDevice() as AndroidDevice

        assertThat(device.serial).isEqualTo("emulator-5556")
        assertThat(device.port).isEqualTo(5557)
        assertThat(device.type).isEqualTo(Device.DeviceType.VIRTUAL)
        assertThat(device.emulatorPort).isEqualTo(5556)
        assertThat(device.serverPort).isEqualTo(5037)
        assertThat(device.properties.map[MANAGED_DEVICE_NAME_KEY]).isEqualTo("device1")
        verifyCallToEmulator()
    }

    @Test
    fun provideDevice_ensureDeviceProviderSkipsUnnecessaryAdbInvocations() {
        `when`(adbManager.getAllSerials()).thenReturn(
                listOf("a_device", "emulator-5556", "emulator-5558")
        )
        // a-device skipped: non-emulator devices skipped
        `when`(adbManager.getId("emulator-5556")).thenReturn(deviceId)
        // emulator-5558 skipped: correct device already found.
        `when`(adbManager.isBootLoaded("emulator-5556")).thenReturn(true)

        val mockContext = mock<Context>()
        `when`(mockContext[Context.CONFIG_KEY]).thenReturn(
            GradleManagedAndroidDeviceLauncher.DataBoundArgs(
                deviceProviderConfig,
                makeConfigFromDeviceProviderConfig(deviceProviderConfig)
            )
        )
        managedDeviceLauncher.configure(mockContext)

        val device = managedDeviceLauncher.provideDevice().getDevice() as AndroidDevice

        assertThat(device.serial).isEqualTo("emulator-5556")
        assertThat(device.port).isEqualTo(5557)
        assertThat(device.type).isEqualTo(Device.DeviceType.VIRTUAL)
        assertThat(device.emulatorPort).isEqualTo(5556)
        assertThat(device.serverPort).isEqualTo(5037)
        assertThat(device.properties.map[MANAGED_DEVICE_NAME_KEY]).isEqualTo("device1")
        verifyCallToEmulator()
        verify(adbManager).configure(any())
        verify(adbManager).getAllSerials()
        verify(adbManager).getId("emulator-5556")
        verify(adbManager).isBootLoaded("emulator-5556")

        verifyNoMoreInteractions(adbManager)
    }

    @Test
    fun provideDevice_emulatorFailingThrowsProviderException() {
        // if the emulator handle fails to launch, a device provider exception is thrown.
        `when`(emulatorHandle.launchInstance(any(), any(), any(), any())).thenAnswer {
            throw DeviceProviderException("")
        }

        val mockContext = mock<Context>()
        `when`(mockContext[Context.CONFIG_KEY]).thenReturn(
            GradleManagedAndroidDeviceLauncher.DataBoundArgs(
                deviceProviderConfig,
                makeConfigFromDeviceProviderConfig(deviceProviderConfig)
            )
        )
        managedDeviceLauncher.configure(mockContext)

        assertThrows(DeviceProviderException::class.java) {
            managedDeviceLauncher.provideDevice()
        }
    }

    @Test
    fun provideDevice_failToFindIdThrowsTimeoutException() {
        `when`(adbManager.getAllSerials()).thenReturn(listOf("emulator-5554"))
        `when`(adbManager.getId("emulator-5554")).thenReturn("some-other-id")

        val mockContext = mock<Context>()
        `when`(mockContext[Context.CONFIG_KEY]).thenReturn(
            GradleManagedAndroidDeviceLauncher.DataBoundArgs(
                deviceProviderConfig,
                makeConfigFromDeviceProviderConfig(deviceProviderConfig)
            )
        )
        managedDeviceLauncher.configure(mockContext)

        assertThrows(GradleManagedAndroidDeviceLauncher.EmulatorTimeoutException::class.java) {
            managedDeviceLauncher.provideDevice()
        }
    }

    @Test
    fun provideDevice_failToBootThrowsTimeoutException() {
        `when`(adbManager.getAllSerials()).thenReturn(listOf("emulator-5554"))
        `when`(adbManager.getId("emulator-5554")).thenReturn(deviceId)
        `when`(adbManager.isBootLoaded("emulator-5554")).thenReturn(false)

        val mockContext = mock<Context>()
        `when`(mockContext[Context.CONFIG_KEY]).thenReturn(
            GradleManagedAndroidDeviceLauncher.DataBoundArgs(
                deviceProviderConfig,
                makeConfigFromDeviceProviderConfig(deviceProviderConfig)
            )
        )
        managedDeviceLauncher.configure(mockContext)

        assertThrows(GradleManagedAndroidDeviceLauncher.EmulatorTimeoutException::class.java) {
            managedDeviceLauncher.provideDevice()
        }
        verify(emulatorHandle).closeInstance()
    }

    @Test
    fun releaseDevice_callsAdbManagerCloseDevice() {
        `when`(adbManager.getAllSerials()).thenReturn(listOf("emulator-5558"))
        `when`(adbManager.getId("emulator-5558")).thenReturn(deviceId)
        `when`(adbManager.isBootLoaded("emulator-5558")).thenReturn(true)

        val mockContext = mock<Context>()
        `when`(mockContext[Context.CONFIG_KEY]).thenReturn(
            GradleManagedAndroidDeviceLauncher.DataBoundArgs(
                deviceProviderConfig,
                makeConfigFromDeviceProviderConfig(deviceProviderConfig)
            )
        )
        managedDeviceLauncher.configure(mockContext)

        val device = managedDeviceLauncher.provideDevice().getDevice() as AndroidDevice

        assertThat(device.serial).isEqualTo("emulator-5558")
        assertThat(device.port).isEqualTo(5559)
        assertThat(device.type).isEqualTo(Device.DeviceType.VIRTUAL)
        assertThat(device.emulatorPort).isEqualTo(5558)
        assertThat(device.serverPort).isEqualTo(5037)
        assertThat(device.properties.map[MANAGED_DEVICE_NAME_KEY]).isEqualTo("device1")

        verifyCallToEmulator()

        managedDeviceLauncher.releaseDevice()

        verify(emulatorHandle).closeInstance()
        verify(adbManager).closeDevice("emulator-5558")
    }

    @Test
    fun logAllAvailableDevices() {
        `when`(adbManager.getAllSerials()).thenReturn(
            listOf("emulator-5554", "emulator-5556")
        )
        `when`(adbManager.getId("emulator-5554")).thenReturn("myapp_myDeviceAndroidVariantTest")
        `when`(adbManager.getId("emulator-5556")).thenReturn(deviceId)
        `when`(adbManager.isBootLoaded("emulator-5556")).thenReturn(true)

        val mockContext = mock<Context>()
        `when`(mockContext[Context.CONFIG_KEY]).thenReturn(
            GradleManagedAndroidDeviceLauncher.DataBoundArgs(
                deviceProviderConfig,
                makeConfigFromDeviceProviderConfig(deviceProviderConfig)
            )
        )
        managedDeviceLauncher.configure(mockContext)

        managedDeviceLauncher.provideDevice()

        verify(mockLogger).info(argThat<Supplier<String>> {
            get().contains("""
                Finding a test device myapp_myDeviceAndroidDebugTest (attempt 1 of 20).
                Found 2 devices:
                myapp_myDeviceAndroidVariantTest(emulator-5554)
                myapp_myDeviceAndroidDebugTest(emulator-5556)
                """.trimIndent())
        })
    }

    private fun makeConfigFromDeviceProviderConfig(
            deviceProviderConfig: GradleManagedAndroidDeviceProviderConfig
    ): DeviceProviderConfigImpl {
        val androidSdkProto = AndroidSdkProto.AndroidSdk.getDefaultInstance()
        val environmentProto = EnvironmentProto.Environment.newBuilder().apply {
            outputDirBuilder.path = logDir
            tmpDirBuilder.path = logDir
            androidEnvironment = EnvironmentProto.AndroidEnvironment.newBuilder().apply {
                this.androidSdk = androidSdkProto
                this.testLogDirBuilder.path = "broker_logs"
                this.testRunLogBuilder.path = "test-results.log"
            }.build()
        }.build()
        val testSetupProto = SetupProto.TestSetup.getDefaultInstance()
        return DeviceProviderConfigImpl(
                environmentProto = environmentProto,
                androidSdkProto = androidSdkProto,
                testSetupProto = testSetupProto,
                configProto = Any.pack(deviceProviderConfig)
        )
    }

    private fun verifyCallToEmulator(enableDisplay: Boolean = false) {
        verify(emulatorHandle).launchInstance(
                deviceName,
                "path/to/gradle/avd",
                deviceId,
                enableDisplay,
        )
    }
}
