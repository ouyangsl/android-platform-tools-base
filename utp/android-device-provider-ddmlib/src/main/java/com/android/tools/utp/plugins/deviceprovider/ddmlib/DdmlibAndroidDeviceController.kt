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

import com.android.ddmlib.DdmPreferences
import com.android.ddmlib.InstallException
import com.android.ddmlib.InstallReceiver
import com.android.ddmlib.MultiLineReceiver
import com.google.testing.platform.api.device.CommandHandle
import com.google.testing.platform.api.device.CommandResult
import com.google.testing.platform.api.device.Device
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.api.error.ErrorSummary
import com.google.testing.platform.core.error.ErrorType
import com.google.testing.platform.core.error.UtpException
import com.google.testing.platform.lib.logging.jvm.getLogger
import com.google.testing.platform.proto.api.core.LogMessageProto
import com.google.testing.platform.proto.api.core.TestArtifactProto.Artifact
import com.google.testing.platform.proto.api.core.TestArtifactProto.ArtifactType.ANDROID_APK
import com.google.testing.platform.runtime.android.device.AndroidDevice
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/**
 * Android specific implementation of [DeviceController] using DDMLIB.
 */
class DdmlibAndroidDeviceController(
    private val apkPackageNameResolver: ApkPackageNameResolver,
    private val uninstallIncompatibleApks: Boolean,
    private val logger: Logger = getLogger()
) : DeviceController {

    companion object {
        private const val DEFAULT_ADB_TIMEOUT_SECONDS = 360L
        private const val SHELL_EXIT_CODE_TAG = "utp_shell_exit_code="

        // This list is copied from the com.android.tools.deployer.ApkInstaller. These errors are
        // known error names that are caused by an incompatible APK installation attempt.
        private val INCOMPATIBLE_APK_INSTALLATION_ERROR_NAMES: Set<String> = setOf(
            "INSTALL_FAILED_UPDATE_INCOMPATIBLE",
            "INCONSISTENT_CERTIFICATES",
            "INSTALL_FAILED_PERMISSION_MODEL_DOWNGRADE",
            "INSTALL_FAILED_VERSION_DOWNGRADE",
            "INSTALL_FAILED_DEXOPT",
        )
    }

    init {
        DdmPreferences.setTimeOut(Int.MAX_VALUE)
    }

    /**
     * Error code to be reported in result proto as a platform error when
     * unexpected error happens in this device controller.
     */
    enum class DdmlibAndroidDeviceControllerErrorCode(val errorCode: Int) {
        ERROR_UNKNOWN(0),
        ERROR_APK_INSTALL(1),
    }

    private lateinit var controlledDevice: DdmlibAndroidDevice
    private lateinit var androidDevice: AndroidDevice

    override fun getDevice(): AndroidDevice = androidDevice

    override fun setDevice(device: Device) {
        controlledDevice = device as DdmlibAndroidDevice
        androidDevice = AndroidDevice(
                serial = controlledDevice.serial,
                type = requireNotNull(controlledDevice.type),
                port = controlledDevice.port,
                properties = controlledDevice.properties
        )
    }

    override fun streamLogs(): Flow<LogMessageProto.LogMessage> {
        return emptyFlow()
    }

    override fun install(artifact: Artifact): CommandResult {
        require(ANDROID_APK == artifact.type) {
            "Artifact needs to be of type: $ANDROID_APK, but was ${artifact.type}"
        }
        require(artifact.hasSourcePath()) {
            "Artifact source path needs to be set!"
        }

        controlledDevice.installPackage(
                artifact.sourcePath.path,
                /*reinstall=*/true,
                *listOfNotNull(
                        "-t",
                        "-g".takeIf { controlledDevice.version.apiLevel >= 23 }
                ).toTypedArray()
        )
        return CommandResult(0, listOf())
    }

    override fun execute(args: List<String>, timeout: Duration?): CommandResult {
        val output = mutableListOf<String>()
        val handler = executeAsync(args) { output.add(it) }
        try {
            handler.waitFor(timeout ?: Duration.ofSeconds(DEFAULT_ADB_TIMEOUT_SECONDS))
        } catch (e: TimeoutCancellationException) {
            logger.warning {
                "adb command [${args.joinToString(" ")}] failed due to timeout.\n${e.message}"
            }
            handler.stop()
        }
        return CommandResult(handler.exitCode(), output)
    }

    // If there's no apk to install this function would not be called in the first place
    private fun getInstallOptionsIndex (args: List<String>): Int {
        var index = 0
        for (arg in args) {
            if(arg.endsWith(".apk"))
                return index
            index++
        }
        return index
    }

    override fun executeAsync(args: List<String>, processor: (String) -> Unit): CommandHandle {
        var isCancelled = false
        val deferred = GlobalScope.async {
            val command = args.first().toLowerCase()
            val commandArgs = args.subList(1, args.size)

            // Setting max timeout to 0 (= indefinite) because we control
            // the timeout by the receiver.isCancelled().
            var commandExitCode: Int = 0
            val receiver = when(command) {
                "shell" -> {
                    val receiver = object: MultiLineReceiver() {
                        override fun isCancelled(): Boolean = isCancelled
                        override fun processNewLines(lines: Array<out String>) {
                            lines.forEach { line ->
                                if (line.startsWith(SHELL_EXIT_CODE_TAG)) {
                                    commandExitCode = line.removePrefix(SHELL_EXIT_CODE_TAG)
                                        .trim().toIntOrNull() ?: 0
                                } else {
                                    processor(line)
                                }
                            }
                        }
                    }
                    controlledDevice.executeShellCommand(
                        (commandArgs + "; echo ${SHELL_EXIT_CODE_TAG}$?").joinToString(" "),
                        receiver,
                        /*maxTimeout=*/0,
                        /*maxTimeToOutputResponse=*/0,
                        TimeUnit.SECONDS
                    )
                    receiver
                }
                "install" -> {
                    val installArgs = commandArgs.take(commandArgs.size - 1)
                    val apkPath = commandArgs.last()

                    var installAttempts = 0
                    var retryInstall = true
                    lateinit var receiver: InstallReceiver
                    while (retryInstall) {
                        installAttempts++
                        retryInstall = false
                        receiver = object : InstallReceiver() {
                            override fun isCancelled(): Boolean = isCancelled
                            override fun processNewLines(lines: Array<out String>) {
                                super.processNewLines(lines)
                                lines.forEach(processor)
                            }
                        }
                        try {
                            controlledDevice.installPackage(
                                apkPath,
                                /*reinstall=*/true,
                                receiver,
                                /*maxTimeout=*/0,
                                /*maxTimeToOutputResponse=*/0,
                                TimeUnit.SECONDS,
                                *installArgs.toTypedArray()
                            )
                        } catch (e: InstallException) {
                            val errorSummary = object : ErrorSummary {
                                override val errorCode: Int =
                                    DdmlibAndroidDeviceControllerErrorCode.ERROR_APK_INSTALL.errorCode
                                override val errorName: String = e.errorCode ?: "UNKNOWN"
                                override val errorType: Enum<*> = ErrorType.TEST
                                override val namespace: String = "DdmlibAndroidDeviceController"
                            }

                            if (uninstallIncompatibleApks &&
                                INCOMPATIBLE_APK_INSTALLATION_ERROR_NAMES.contains(errorSummary.errorName)
                            ) {
                                apkPackageNameResolver.getPackageNameFromApk(apkPath)?.let { uninstallPackageName ->
                                    logger.warning("Uninstalling package: ${uninstallPackageName}")
                                    controlledDevice.uninstallPackage(uninstallPackageName)
                                    // Only retry installation after initial failure, otherwise
                                    // it potentially goes into an infinite loop.
                                    retryInstall = installAttempts == 1
                                }
                            }

                            if (!retryInstall) {
                                throw UtpException(
                                    errorSummary,
                                    "Failed to install APK(s): $apkPath",
                                    e
                                )
                            }
                        }
                    }
                    receiver
                }
                "install-multiple" -> {
                    val divideIndex = getInstallOptionsIndex(commandArgs)

                    val additionalInstallOptions = commandArgs.subList(0, divideIndex)
                    val apkPaths = commandArgs.subList(divideIndex, commandArgs.size)

                    var installAttempts = 0
                    var retryInstall = true
                    lateinit var receiver: InstallReceiver
                    while (retryInstall) {
                        installAttempts++
                        retryInstall = false
                        receiver = object : InstallReceiver() {
                            override fun isCancelled(): Boolean = isCancelled
                            override fun processNewLines(lines: Array<out String>) {
                                super.processNewLines(lines)
                                lines.forEach(processor)
                            }
                        }
                        try {
                            controlledDevice.installPackages(
                                apkPaths.map { File(it) },
                                /*reinstall=*/true,
                                additionalInstallOptions
                            )
                        } catch (e: InstallException) {
                            val errorSummary = object : ErrorSummary {
                                override val errorCode: Int =
                                        DdmlibAndroidDeviceControllerErrorCode.ERROR_APK_INSTALL.errorCode
                                override val errorName: String = e.errorCode ?: "UNKNOWN"
                                override val errorType: Enum<*> = ErrorType.TEST
                                override val namespace: String = "DdmlibAndroidDeviceController"
                            }

                            if (uninstallIncompatibleApks &&
                                    INCOMPATIBLE_APK_INSTALLATION_ERROR_NAMES.contains(errorSummary.errorName)
                            ) {
                                // All split APKs are installed under the same package name
                                apkPackageNameResolver.getPackageNameFromApk(apkPaths[0])
                                        ?.let { uninstallPackageName ->
                                            logger.warning("Uninstalling package: $uninstallPackageName")
                                            controlledDevice.uninstallPackage(
                                                    uninstallPackageName)
                                            // Only retry installation after initial failure, otherwise
                                            // it potentially goes into an infinite loop.
                                            retryInstall = installAttempts == 1

                                }
                            }

                            if (!retryInstall) {
                                throw UtpException(
                                        errorSummary,
                                        "Failed to install split APK(s): $apkPaths",
                                        e
                                )
                            }
                        }
                    }
                    receiver
                }
                else -> {
                    throw UnsupportedOperationException("Unsupported ADB command: $command")
                }
            }
            CommandResult(
                    if (receiver.isCancelled) {
                        -1
                    } else {
                        commandExitCode
                    },
                    emptyList()
            )
        }
        return object : CommandHandle {
            override fun exitCode(): Int {
                return if (isCancelled) {
                    -1
                } else {
                    deferred.getCompleted().statusCode
                }
            }

            override fun stop() {
                isCancelled = true
            }

            override fun isRunning(): Boolean = deferred.isActive

            @Throws(TimeoutCancellationException::class)
            override fun waitFor(timeout: Duration?): Unit = runBlocking {
                if (timeout != null) {
                    withTimeout(timeout.toMillis()) {
                        deferred.await()
                    }
                } else {
                    deferred.await()
                }
            }
        }
    }

    override fun push(artifact: Artifact): Int {
        require(artifact.hasSourcePath() && artifact.hasDestinationPath()) {
            "Artifact source and destination paths need to be set!"
        }
        controlledDevice.pushFile(artifact.sourcePath.path, artifact.destinationPath.path)
        return 0
    }

    override fun pull(artifact: Artifact): Int {
        require(artifact.hasSourcePath() && artifact.hasDestinationPath()) {
            "Artifact source and destination paths need to be set!"
        }
        controlledDevice.pullFile(artifact.destinationPath.path, artifact.sourcePath.path)
        return 0
    }
}
