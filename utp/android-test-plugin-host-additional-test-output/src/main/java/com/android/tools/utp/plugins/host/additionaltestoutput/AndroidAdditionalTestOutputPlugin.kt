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
package com.android.tools.utp.plugins.host.additionaltestoutput

import com.android.tools.utp.plugins.common.HostPluginAdapter
import com.android.tools.utp.plugins.host.additionaltestoutput.proto.AndroidAdditionalTestOutputConfigProto.AndroidAdditionalTestOutputConfig
import com.google.common.io.Files
import com.google.testing.platform.api.config.ProtoConfig
import com.google.testing.platform.api.context.Context
import com.google.testing.platform.api.context.events
import com.google.testing.platform.api.device.CommandResult
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.api.plugin.sendTestResultUpdate
import com.google.testing.platform.lib.logging.jvm.getLogger
import com.google.testing.platform.proto.api.core.TestArtifactProto
import com.google.testing.platform.proto.api.core.TestCaseProto.TestCase
import com.google.testing.platform.proto.api.core.TestResultProto.TestResult
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import com.google.testing.platform.runtime.android.controller.ext.deviceShell
import com.google.testing.platform.runtime.android.controller.ext.isTestServiceInstalled
import com.google.testing.platform.runtime.android.device.AndroidDeviceProperties
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/**
 * A UTP plugin that retrieves additional test outputs from a test device into a host machine.
 */
class AndroidAdditionalTestOutputPlugin(private val logger: Logger = getLogger()) : HostPluginAdapter() {

    companion object {
        const private val BENCHMARK_TEST_METRICS_KEY = "android.studio.display.benchmark"
        const private val BENCHMARK_V2_TEST_METRICS_KEY = "android.studio.v2display.benchmark"
        const private val BENCHMARK_PATH_TEST_METRICS_KEY = "android.studio.v2display.benchmark.outputDirPath"
        private val benchmarkPrefixRegex = "^benchmark:( )?".toRegex(RegexOption.MULTILINE)
        // Valid string: Benchmark test took [200 ms](path/to/my.trace) to run.
        // text = 200 ms, url = path/to/my.trace, title = "", total = "[200 ms](path/to/my.trace)"
        private val benchmarkUrlRegex =
            "(?<total>\\[(?<text>.+?)\\]\\((?<link>[^ ]+?)(?: \"(?<title>.+?)\")?\\))".toRegex()
        // Workaround for the Kotlin issue KT-20865.
        // matchResult.groups.get(BENCHMARK_LINK_REGEX_GROUP_INDEX)?.value is equivalent to
        // matchResult.groups["link"]?.value.
        const private val BENCHMARK_LINK_REGEX_GROUP_INDEX = 3
        const private val BENCHMARK_TRACE_FILE_PREFIX = "file://"

        // AndroidX Test Storage service's output directory on device.
        // This constant value is a copy from TestStorageConstants.ON_DEVICE_PATH_TEST_OUTPUT.
        const val TEST_STORAGE_SERVICE_OUTPUT_DIR = "/sdcard/googletest/test_outputfiles"
    }

    lateinit var config: AndroidAdditionalTestOutputConfig
    private lateinit var context: Context

    override fun configure(context: Context) {
        val config = context[Context.CONFIG_KEY] as ProtoConfig
        this.config = AndroidAdditionalTestOutputConfig.parseFrom(config.configProto!!.value)
        this.context = context
    }

    override fun beforeAll(deviceController: DeviceController) {
        createEmptyDirectoryOnHost()
        createEmptyDirectoryOnDevice(deviceController)

        if (deviceController.isTestServiceInstalled()) {
            val apiLevel = getApiLevel(deviceController) ?: 0
            if (apiLevel >= 30) {
                // Grant MANAGE_EXTERNAL_STORAGE permission to androidx.test.services so that it
                // can write test artifacts in external storage.
                deviceController.deviceShellAndCheckSuccess(
                    "appops set androidx.test.services MANAGE_EXTERNAL_STORAGE allow")
            }
        }
    }

    private fun getApiLevel(deviceController: DeviceController): Int? {
        return (deviceController.getDevice().properties as? AndroidDeviceProperties)
            ?.deviceApiLevel?.toIntOrNull()
    }

    /**
     * Creates an empty directory on host machine. If a directory exists at the given path,
     * it removes all contents in the directory.
     */
    private fun createEmptyDirectoryOnHost() {
        val dir = File(config.additionalOutputDirectoryOnHost)
        if(dir.exists()) {
            dir.deleteRecursively()
        }
        dir.mkdirs()
    }

    /**
     * Creates an empty directory on test device. If a directory exists at the given path,
     * it removes all contents in the directory.
     */
    private fun createEmptyDirectoryOnDevice(deviceController: DeviceController) {
        val dir = config.additionalOutputDirectoryOnDevice
        if (dir.isNotBlank()) {
            deviceController.deviceShellAndCheckSuccess("rm -rf \"${dir}\"")
            deviceController.deviceShellAndCheckSuccess("mkdir -p \"${dir}\"")
        }

        if (deviceController.isTestServiceInstalled()) {
            deviceController.deviceShellAndCheckSuccess(
                "rm -rf \"${TEST_STORAGE_SERVICE_OUTPUT_DIR}\"")
            deviceController.deviceShellAndCheckSuccess(
                "mkdir -p \"${TEST_STORAGE_SERVICE_OUTPUT_DIR}\"")
        }
    }

    override fun beforeEach(testCase: TestCase?, deviceController: DeviceController) {
    }

    override fun afterEachWithReturn(
        testResult: TestResult,
        deviceController: DeviceController,
        cancelled: Boolean
    ): TestResult {
        val builder = testResult.toBuilder()
        addBenchmarkOutput(testResult, deviceController, builder)
        return builder.build().also {
            context.events.sendTestResultUpdate(it)
        }
    }

    /**
     * Retrieves benchmark output text from a given [testResult] and appends them into
     * the [builder].
     */
    private fun addBenchmarkOutput(
        testResult: TestResult, deviceController: DeviceController,
        builder: TestResult.Builder) {
        // Workaround solution for b/154322086.
        // Newer libraries output strings on both BENCHMARK_TEST_METRICS_KEY and
        // BENCHMARK_V2_OUTPUT_TEST_METRICS_KEY. The V2 supports linking while the V1 does not.
        // This is done to maintain backward compatibility with older versions of studio.
        val key = if (testResult.detailsList.find { entry ->
                entry.key == BENCHMARK_V2_TEST_METRICS_KEY
            } != null) {
            BENCHMARK_V2_TEST_METRICS_KEY
        } else {
            BENCHMARK_TEST_METRICS_KEY
        }

        val benchmarkMessage = testResult.detailsList.find { entry ->
            entry.key == key
        }?.value ?: ""
        val benchmarkMessageWithoutPrefix = benchmarkPrefixRegex.replace(benchmarkMessage, "")
        val benchmarkOutputDir = testResult.detailsList.find { entry ->
            entry.key == BENCHMARK_PATH_TEST_METRICS_KEY
        }?.value ?: ""

        addBenchmarkMessage(benchmarkMessageWithoutPrefix, builder, testResult)
        addBenchmarkFiles(benchmarkMessageWithoutPrefix, benchmarkOutputDir, deviceController,
                          builder)
    }

    /**
     * Finds a file path to a profiler output file from [benchmarkMessage], pulls files from the
     * test device to the host machine and adds them to the [builder].
     */
    private fun addBenchmarkFiles(
        benchmarkMessage: String, benchmarkOutputDir: String, deviceController: DeviceController,
        builder: TestResult.Builder) {
        if (benchmarkMessage.isBlank() || benchmarkOutputDir.isBlank()) {
            return;
        }

        val benchmarkFileRelativePaths = benchmarkMessage.split("\n")
            .flatMap { line ->
                benchmarkUrlRegex.findAll(line)
            }
            .map { matchResult ->
                matchResult.groups.get(BENCHMARK_LINK_REGEX_GROUP_INDEX)?.value
            }
            .filterNotNull()
            .filter { matchValue ->
                matchValue.startsWith(BENCHMARK_TRACE_FILE_PREFIX)
            }
            .map { matchValue ->
                matchValue.replace(BENCHMARK_TRACE_FILE_PREFIX, "")
            }
            .toSet()

        benchmarkFileRelativePaths.forEach { relativeFilePath ->
            val deviceFilePath = "${benchmarkOutputDir}/${relativeFilePath}"
            val hostFilePath =
                File(config.additionalOutputDirectoryOnHost).absolutePath +
                        "${File.separator}${relativeFilePath}"
            deviceController.pull(TestArtifactProto.Artifact.newBuilder().apply {
                destinationPathBuilder.path = deviceFilePath
                sourcePathBuilder.path = hostFilePath
            }.build())

            builder.addOutputArtifactBuilder().apply {
                labelBuilder.apply {
                    namespace = "android"
                    label = "additionaltestoutput.benchmark.trace"
                }
                sourcePathBuilder.apply {
                    path = hostFilePath
                }
            }
        }
    }

    private fun addBenchmarkMessage(benchmarkMessage: String, builder: TestResult.Builder, testResult: TestResult) {
        if (benchmarkMessage.isBlank()) {
            return
        }
        val fileNameSuffix = "${testResult.testCase.testPackage}.${testResult.testCase.testClass}.${testResult.testCase.testMethod}"
        val benchmarkMessageOutputFile = File(config.additionalOutputDirectoryOnHost,
                                              "additionaltestoutput.benchmark.message_${fileNameSuffix}.txt")
        Files.asCharSink(benchmarkMessageOutputFile, Charsets.UTF_8).write(benchmarkMessage)
        builder.addOutputArtifactBuilder().apply {
            labelBuilder.apply {
                namespace = "android"
                label = "additionaltestoutput.benchmark.message"
            }
            sourcePathBuilder.apply {
                path = benchmarkMessageOutputFile.absolutePath
            }
        }
    }

    override fun afterAllWithReturn(
        testSuiteResult: TestSuiteResult,
        deviceController: DeviceController,
        cancelled: Boolean
    ): TestSuiteResult {
        // TODO: Currently, this plugin copies additional test outputs after all test cases.
        //       It can be moved to afterEach method and include them in the TestResult so that
        //       Android Studio can display them in TestMatrix streamingly.
        try {
            copyAdditionalTestOutputsFromDeviceToHost(deviceController)
        } catch (e: Exception) {
            logger.log(Level.WARNING, e) {
                "Failed to retrieve additional test outputs from device."
            }
        }
        try {
            if (deviceController.isTestServiceInstalled()) {
                copyTestStorageServiceOutputFilesFromDeviceToHost(deviceController)
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, e) {
                "Failed to retrieve test storage service outputs from device."
            }
        }
        return testSuiteResult
    }

    private fun copyAdditionalTestOutputsFromDeviceToHost(deviceController: DeviceController) {
        val deviceDir = config.additionalOutputDirectoryOnDevice
        if (deviceDir.isBlank()) {
            return
        }

        val hostDir = File(config.additionalOutputDirectoryOnHost).absolutePath
        copyFilesFromDeviceToHost(deviceController, deviceDir, hostDir)
    }

    private fun copyTestStorageServiceOutputFilesFromDeviceToHost(deviceController: DeviceController) {
        val deviceDir = TEST_STORAGE_SERVICE_OUTPUT_DIR
        val hostDir = File(config.additionalOutputDirectoryOnHost).absolutePath
        copyFilesFromDeviceToHost(deviceController, deviceDir, hostDir)
    }

    private fun copyFilesFromDeviceToHost(
        deviceController: DeviceController,
        deviceDir: String,
        hostDir: String) {
        logger.info("Copying files from device to host: $deviceDir to $hostDir")

        val apiLevel = getApiLevel(deviceController) ?: 0
        if (apiLevel >= 28) {
            val currentAndroidUser = getCurrentAndroidUser(deviceController)
            if (currentAndroidUser != "0") {
                logger.info("Copying files using content provider.")
                copyFilesFromDeviceToHostUsingContentProvider(deviceController, deviceDir, hostDir, currentAndroidUser)
                return
            }
        }

        if (!deviceController.isDirectory(deviceDir)) {
            logger.info("$deviceDir is not a directory")
            return
        }

        // Note: "ls -1" doesn't work on API level 21.
        deviceController.deviceShellAndCheckSuccess("ls \"${deviceDir}\" | cat")
            .output
            .filter(String::isNotBlank)
            .forEach {
                val deviceFilePath = "${deviceDir}/${it}"
                val hostFilePath = "${hostDir}${File.separator}${it}"
                logger.info("Copying $deviceFilePath to $hostFilePath")
                if (deviceController.isDirectory(deviceFilePath)) {
                    File(hostFilePath).let {
                        if (!it.exists()) {
                            it.mkdirs()
                        }
                    }
                    copyFilesFromDeviceToHost(deviceController, deviceFilePath, hostFilePath)
                } else {
                    deviceController.pull(TestArtifactProto.Artifact.newBuilder().apply {
                        destinationPathBuilder.path = deviceFilePath
                        sourcePathBuilder.path = hostFilePath
                    }.build())
                }
            }
    }

    private fun copyFilesFromDeviceToHostUsingContentProvider(
        deviceController: DeviceController,
        deviceDir: String,
        hostDir: String,
        androidUser: String) {
        // Media store's file index might not be up-to-date. b/345801721.
        logger.info("Updating MediaStore's file index")
        deviceController.deviceShellAndCheckSuccess(
            "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://$deviceDir")

        val normalizedDeviceDir = replaceSystemPath(deviceDir, androidUser)
        val regex = Regex("""_id=(\d+), _data=(.*)""")
        deviceController.deviceShellAndCheckSuccess(
            "content query --uri content://media/external/file --user $androidUser --projection _id:_data " +
                "--where \"mime_type IS NOT NULL AND _data LIKE '$normalizedDeviceDir%'\"").output.forEach {
            val matchResult = regex.find(it) ?: return@forEach
            val (id, path) = matchResult.destructured
            val hostFile = File(hostDir + File.separator + path.removePrefix(normalizedDeviceDir))
            hostFile.parentFile.let { parentFile ->
                if (!parentFile.exists()) {
                    parentFile.mkdirs()
                }
            }
            logger.info("Copying $path to ${hostFile.absolutePath}")
            val result = deviceController.deviceShellAndCheckSuccess(
                "content read --user $androidUser --uri content://media/external/file/$id")
            hostFile.outputStream().use { fileOutputStream ->
                result.byteOutputStream.copyTo(fileOutputStream)
            }
        }
    }

    // Replaces system path from the given device dir and returns a user-specific path.
    // Please see https://source.android.com/docs/devices/admin/multi-user-testing#device-paths
    private fun replaceSystemPath(deviceDir: String, androidUser: String): String {
        if (deviceDir.lowercase().startsWith("/sdcard/")) {
            return "/storage/emulated/$androidUser/" + deviceDir.substring("/sdcard/".length)
        }
        if (deviceDir.lowercase().startsWith("/data/data/")) {
            return "/data/user/$androidUser/" + deviceDir.substring("/data/data/".length)
        }
        return deviceDir
    }

    private fun getCurrentAndroidUser(deviceController: DeviceController): String {
        return deviceController.deviceShellAndCheckSuccess("am get-current-user")
            .output.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .firstOrNull() ?: "0"  // Default primary android user.
    }

    override fun canRun(): Boolean = true

    private fun DeviceController.deviceShellAndCheckSuccess(vararg commands: String): CommandResult {
        val result = deviceShell(commands.toList())
        if (result.statusCode != 0) {
            logger.warning {
                "Shell command failed (${result.statusCode}): " +
                        "${commands.joinToString(" ")}\n" +
                        result.output.joinToString("\n")
            }
        }
        return result
    }

    private fun DeviceController.isDirectory(deviceFilePath: String): Boolean {
        val result = deviceShell(listOf("[[ -d \"${deviceFilePath}\" ]]"))
        return result.statusCode == 0
    }
}
