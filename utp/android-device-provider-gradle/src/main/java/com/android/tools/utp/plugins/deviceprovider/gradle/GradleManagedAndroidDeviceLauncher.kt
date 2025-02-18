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

import com.android.tools.utp.plugins.deviceprovider.gradle.proto.GradleManagedAndroidDeviceProviderProto.GradleManagedAndroidDeviceProviderConfig
import com.android.tools.utp.plugins.deviceprovider.profile.DeviceProviderProfileManager
import com.google.testing.platform.api.config.AndroidSdk
import com.google.testing.platform.api.config.Config
import com.google.testing.platform.api.config.ConfigBase
import com.google.testing.platform.api.config.Environment
import com.google.testing.platform.api.config.Setup
import com.google.testing.platform.api.config.environment
import com.google.testing.platform.api.context.Context
import com.google.testing.platform.api.device.Device
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.core.device.DeviceProviderErrorSummary
import com.google.testing.platform.core.device.DeviceProviderException
import com.google.testing.platform.lib.logging.jvm.getLogger
import com.google.testing.platform.lib.process.inject.DaggerSubprocessComponent
import com.google.testing.platform.lib.process.logger.DefaultSubprocessLogger
import com.google.testing.platform.lib.process.logger.SubprocessLogger
import com.google.testing.platform.proto.api.config.AdbConfigProto
import com.google.testing.platform.proto.api.core.PathProto
import com.google.testing.platform.runtime.android.AndroidDeviceProvider
import com.google.testing.platform.runtime.android.device.AndroidDevice
import com.google.testing.platform.runtime.android.device.AndroidDeviceProperties
import java.util.logging.Logger
import kotlin.math.min

/**
 * Creates an emulator using the [GradleManagedAndroidDeviceProvider] configuration proto and the
 * [com.google.testing.platform.proto.api.config.RunnerConfigProto].
 * Booting and killing the emulator is done directly from UTP for use by the Gradle Plugin for
 * Android.
 *
 * Ports for the emulator is decided by the emulator directly, where the device is detected by a
 * unique id created by the device launcher.
 */
class GradleManagedAndroidDeviceLauncher(
    private val adbManager: GradleAdbManager,
    private val emulatorHandle: EmulatorHandle,
    private val deviceControllerFactory: DeviceControllerFactory,
    private val maxDelayMillis: Long = 30000,
    private val logger: Logger = getLogger(),
) : AndroidDeviceProvider {
    private lateinit var context: Context
    private lateinit var environment: Environment
    private lateinit var testSetup: Setup
    private lateinit var androidSdk: AndroidSdk
    private lateinit var customConfig: GradleManagedAndroidDeviceProviderConfig
    private lateinit var avdFolder: String
    private lateinit var avdName: String
    private lateinit var dslName: String
    private lateinit var avdId: String
    private lateinit var profileManager: DeviceProviderProfileManager
    private var enableDisplay: Boolean = false /*lateinit*/
    private var adbServerPort: Int = 0 /*lateinit*/
    private lateinit var device: AndroidDevice

    companion object {
        const val MANAGED_DEVICE_NAME_KEY = "gradleManagedDeviceDslName"

        fun create(config: Config): GradleManagedAndroidDeviceLauncher {
            val subprocessLoggerFactory = object: SubprocessLogger.Factory {
                override fun create() = DefaultSubprocessLogger(
                        config.environment.outputDirectory,
                        flushEagerly = true)
            }
            val subprocessComponent = DaggerSubprocessComponent.builder()
                    .subprocessLoggerFactory(subprocessLoggerFactory)
                    .build()
            return GradleManagedAndroidDeviceLauncher(
                    GradleAdbManagerImpl(subprocessComponent),
                    EmulatorHandleImpl(subprocessComponent),
                    DeviceControllerFactoryImpl(),
            )
        }
    }

    class DataBoundArgs(
            val gradleManagedDeviceProviderConfig: GradleManagedAndroidDeviceProviderConfig,
            val delegateConfigBase: ConfigBase
    ) : ConfigBase by delegateConfigBase

    class EmulatorTimeoutException(
            message: String
    ) : Exception(message)

    override fun configure(context: Context) {
        this.context = context
        val config = context[Context.CONFIG_KEY] as DataBoundArgs
        environment = config.delegateConfigBase.environment
        testSetup = config.delegateConfigBase.setup
        androidSdk = config.delegateConfigBase.androidSdk
        customConfig = config.gradleManagedDeviceProviderConfig
        avdFolder = PathProto.Path.parseFrom(customConfig.managedDevice.avdFolder.value).path
        avdName = customConfig.managedDevice.avdName
        dslName = customConfig.managedDevice.gradleDslDeviceName
        avdId = customConfig.managedDevice.avdId
        enableDisplay = customConfig.managedDevice.enableDisplay
        adbServerPort = customConfig.adbServerPort
        adbManager.configure(androidSdk.adbPath)
        emulatorHandle.configure(
                PathProto.Path.parseFrom(
                        customConfig.managedDevice.emulatorPath.value
                ).path,
                customConfig.managedDevice.emulatorGpu,
                customConfig.managedDevice.showEmulatorKernelLogging)
        profileManager = DeviceProviderProfileManager.forOutputDirectory(
            environment.outputDirectory,
            dslName
        )
    }

    private fun makeDevice(): AndroidDevice {
        // Launch emulator with retry. Emulator process might be crashed
        // by SIGSEGV when it tried to attach to adb (b/314022353). When
        // this happens, the emulator process dies before it becomes
        // visible from adb devices command.
        val targetSerial = retryWithExponentialBackOff(
                maxAttempts = 3,
                conditionFunc = {
                    if (!emulatorHandle.isAlive()) {
                        logger.warning(
                            "Emulator process exited unexpectedly with the exit code " +
                            "${emulatorHandle.exitCode()}.")
                        true
                    } else {
                        // If the emulator process is still alive, the emulator may be hanged up.
                        // We give up if this happens.
                        false
                    }
                }) { attempt ->
            logger.info("Launching Emulator (Attempt $attempt)")
            emulatorHandle.launchInstance(
                    avdName,
                    avdFolder,
                    avdId,
                    enableDisplay,
            )
            findSerial()
        }

        if (targetSerial == null) {
            if (!emulatorHandle.isAlive()) {
                throw EmulatorTimeoutException("""
                    Emulator process exited unexpectedly with the exit code ${emulatorHandle.exitCode()}.

                    Please ensure that you have sufficient resources to run the requested
                    number of devices or request fewer devices.
                    """.trimIndent())
            }
            closeDevice()
            throw EmulatorTimeoutException("""
                    Gradle was unable to attach one or more devices to the adb server.

                    Please ensure that you have sufficient resources to run the requested
                    number of devices or request fewer devices.
                    """.trimIndent())
        }

        if (!establishBootCheck(targetSerial)) {
            closeDevice()
            throw EmulatorTimeoutException("""
                    Gradle was unable to boot one or more devices. If this issue persists,
                    delete existing devices using the "cleanManagedDevices" task and rerun
                    the test.
                    """.trimIndent())
        }

        val emulatorPort = targetSerial.substring("emulator-".length).toInt()
        device = AndroidDevice(
                host = "localhost",
                serial = targetSerial,
                type = Device.DeviceType.VIRTUAL,
                port = emulatorPort + 1,
                emulatorPort = emulatorPort,
                serverPort = adbServerPort,
                properties = AndroidDeviceProperties()
        )
        return device
    }

    /**
     * Searches for the serial of the avd device that has the unique
     * avd id.
     *
     * After the emulator is started with the unique avd id This method is
     * called to use adb to detect which emulator has that id. This is done
     * in two steps:
     *
     * A call to "adb devices"
     * to get a list of all serials of the devices currently attached to adb.
     *
     * Then loop through each device, calling "adb -s <serial> emu avd id" to
     * retrieve each id, checking against the emulators unique id to find the
     * correct serial.
     */
    private fun findSerial(maxAttempts: Int = 20): String? {
        // We may need to retry as the emulator may not have attached to the
        // adb server even though the emulator has booted.
        return retryWithExponentialBackOff(
                maxAttempts, conditionFunc = emulatorHandle::isAlive) { attempt ->
            val serials = adbManager.getAllSerials()
            logger.info {
                "Finding a test device $avdId (attempt $attempt of $maxAttempts).\n" +
                "Found ${serials.size} devices:\n" +
                serials.joinToString("\n") {
                    "${adbManager.getId(it).orEmpty()}($it)"
                }
            }
            for (serial in serials) {
                // ignore non-emulator devices.
                if (!serial.startsWith("emulator-")) {
                    continue
                }
                if (avdId == adbManager.getId(serial)) {
                    return@retryWithExponentialBackOff serial
                }
            }
            null
        }
    }

    private fun <T> retryWithExponentialBackOff(
        maxAttempts: Int = 20,
        initialRetryDelayMillis: Long = 2000,
        retryBackOffBase: Double = 2.0,
        conditionFunc: () -> Boolean = { true },
        runnableFunc: (attempt: Int) -> T
    ): T? {
        var backOffMillis = min(initialRetryDelayMillis, maxDelayMillis)
        for (attempt in 1..maxAttempts) {
            val result = runnableFunc(attempt)
            if (result != null) {
                return result
            }
            if (!conditionFunc()) {
                return null
            }
            Thread.sleep(backOffMillis)
            backOffMillis = min((retryBackOffBase * backOffMillis).toLong(), maxDelayMillis)
        }
        return null
    }

    /**
     * Establishes the device is booted properly with retries or returns false.
     *
     * After the serial is detected for the device, we can then query it to
     * ensure that the device is booted properly. This needs to occur before
     * we attempt to install the test apk, therefore we need it to be
     * established before we supply the Device to the rest of AGP
     */
    private fun establishBootCheck(deviceSerial: String): Boolean {
        // We may need to retry if we default to cold boot.
        return retryWithExponentialBackOff {
            if (adbManager.isBootLoaded(deviceSerial)) {
                true
            } else {
                null
            }
        } ?: false
    }

    override fun provideDevice(): DeviceController {
        return profileManager.recordDeviceProvision {
            val deviceController: DeviceController
            try {
                deviceController = deviceControllerFactory.getController(
                    this,
                    environment,
                    testSetup,
                    androidSdk,
                    AdbConfigProto.AdbConfig.parseFrom(customConfig.adbConfig.value),
                    context,
                )
            } catch (throwable: Throwable) {
                throw DeviceProviderException(
                    "Loading and configuring DeviceController failed, make sure the device controller is" +
                            " present as a part of the same jar the DeviceProvider is part of.",
                    DeviceProviderErrorSummary.UNDETERMINED,
                    throwable
                )
            }

            // As a temporary workaround. We need to add the dslName to the
            // properties here. b/183651101
            // This will be overwritten if setDevice() is called again.
            val device = makeDevice()
            deviceController.setDevice(device)
            device.properties = device.properties.copy(
                map = device.properties.map +
                        mapOf(MANAGED_DEVICE_NAME_KEY to dslName)
            )
            deviceController
        }
    }

    override fun releaseDevice() {
        profileManager.recordDeviceRelease {
            closeDevice()
        }
    }

    /**
     * Closes the device without recording to the profiling record.
     *
     * This should be used when the needs to be closed when an error occurs allocating the device,
     * so as to not pollute the profiling of device release, which records the time it takes for
     * the device to be released after testing.
     */
    private fun closeDevice() {
        emulatorHandle.closeInstance()
        // On Windows, this may not kill the instance. Search for the serial
        // on adb and run a kill command from the adb. We don't want to
        // retry to find the serial because the device either is or is not
        // connected at this point and, ideally, would be disconnected.
        findSerial(1)?.let {
            adbManager.closeDevice(it)
        }
    }

    override fun cancel(): Boolean = false
}
