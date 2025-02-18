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

import com.android.tools.utp.plugins.host.additionaltestoutput.proto.AndroidAdditionalTestOutputConfigProto.AndroidAdditionalTestOutputConfig
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.Any
import com.google.testing.platform.api.config.ProtoConfig
import com.google.testing.platform.api.context.Context
import com.google.testing.platform.api.device.CommandResult
import com.google.testing.platform.api.device.Device
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.api.event.Events
import com.google.testing.platform.api.plugin.sendTestResultUpdate
import com.google.testing.platform.proto.api.core.ExtensionProto
import com.google.testing.platform.proto.api.core.TestArtifactProto
import com.google.testing.platform.proto.api.core.TestResultProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import com.google.testing.platform.runtime.android.device.AndroidDeviceProperties
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness
import java.io.File
import java.time.Duration
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Unit tests for [AndroidAdditionalTestOutputPlugin].
 */
@RunWith(JUnit4::class)
class AndroidAdditionalTestOutputPluginTest {
    @get:Rule var mockitoJUnitRule: MockitoRule =
        MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @get:Rule var tempDirs = TemporaryFolder()

    @Mock private lateinit var mockDeviceController: DeviceController
    @Mock private lateinit var mockLogger: Logger
    @Mock private lateinit var mockContext: Context
    @Mock private lateinit var mockEvents: Events

    private lateinit var testResultAfterEach: TestResultProto.TestResult

    private var commandResult: CommandResult = CommandResult(0, listOf())

    @Before
    fun setUpMocks() {
        `when`(mockDeviceController.execute(anyList(), nullable(Duration::class.java))).then {
            commandResult
        }
        `when`(mockContext[Context.EVENTS_KEY]).thenReturn(mockEvents)
        val mockDevice = mock<Device>()
        `when`(mockDevice.properties).thenReturn(AndroidDeviceProperties(deviceApiLevel = "30"))
        `when`(mockDeviceController.getDevice()).thenReturn(mockDevice)
    }

    private fun installTestStorageService() {
        `when`(mockDeviceController.execute(
            eq(listOf("shell", "pm", "list", "packages", "androidx.test.services")),
            nullable(Duration::class.java))
        ).thenReturn(CommandResult(0, listOf("package:androidx.test.services")))
    }

    private fun createTestArtifact(
        filePathOnDevice: String, filePathOnHost: String): TestArtifactProto.Artifact {
        return TestArtifactProto.Artifact.newBuilder().apply {
            sourcePathBuilder.path = filePathOnHost
            destinationPathBuilder.path = filePathOnDevice
        }.build()
    }

    private fun createPlugin(config: AndroidAdditionalTestOutputConfig)
    : AndroidAdditionalTestOutputPlugin {
        val packedConfig = Any.pack(config)
        val protoConfig = object: ProtoConfig {
            override val configProto: Any
                get() = packedConfig
            override val configResource: ExtensionProto.ConfigResource?
                get() = null
        }
        `when`(mockContext[Context.CONFIG_KEY]).thenReturn(protoConfig)
        return AndroidAdditionalTestOutputPlugin(mockLogger).apply {
            configure(mockContext)
        }
    }

    private fun runPlugin(
        testResult: TestResultProto.TestResult = TestResultProto.TestResult.getDefaultInstance(),
        configFunc: AndroidAdditionalTestOutputConfig.Builder.() -> Unit) {
        val config = AndroidAdditionalTestOutputConfig.newBuilder().apply {
            configFunc(this)
        }.build()
        createPlugin(config).apply {
            beforeAll(mockDeviceController)
            beforeEach(null, mockDeviceController)
            testResultAfterEach = afterEachWithReturn(testResult, mockDeviceController)
            verify(mockEvents).sendTestResultUpdate(testResultAfterEach)
            afterAll(TestSuiteResultProto.TestSuiteResult.getDefaultInstance(), mockDeviceController)
        }
    }

    @Test
    fun runPluginAndSuccess() {
        val hostDir = tempDirs.newFolder().absolutePath
        val deviceDir = "/onDevice/outputDir/"

        `when`(mockDeviceController.execute(
            eq(listOf("shell", "ls \"${deviceDir}\" | cat")),
            nullable(Duration::class.java)
        )).thenReturn(CommandResult(0, listOf("output1.txt", "output2.txt", "subdir")))

        `when`(mockDeviceController.execute(
            eq(listOf("shell", "[[ -d \"${deviceDir}/output1.txt\" ]]")),
            nullable(Duration::class.java)
        )).thenReturn(CommandResult(1, listOf()))

        `when`(mockDeviceController.execute(
            eq(listOf("shell", "[[ -d \"${deviceDir}/output2.txt\" ]]")),
            nullable(Duration::class.java)
        )).thenReturn(CommandResult(1, listOf()))

        `when`(mockDeviceController.execute(
            eq(listOf("shell", "ls \"${deviceDir}/subdir\" | cat")),
            nullable(Duration::class.java)
        )).thenReturn(CommandResult(0, listOf("output3.txt")))

        `when`(mockDeviceController.execute(
            eq(listOf("shell", "[[ -d \"${deviceDir}/subdir/output3.txt\" ]]")),
            nullable(Duration::class.java)
        )).thenReturn(CommandResult(1, listOf()))

        runPlugin {
            additionalOutputDirectoryOnHost = hostDir
            additionalOutputDirectoryOnDevice = deviceDir
        }

        inOrder(mockDeviceController).apply {
            verify(mockDeviceController).execute(listOf("shell", "rm -rf \"${deviceDir}\""))
            verify(mockDeviceController).execute(listOf("shell", "mkdir -p \"${deviceDir}\""))
            verify(mockDeviceController).execute(listOf("shell", "ls \"${deviceDir}\" | cat"))
            verify(mockDeviceController).pull(createTestArtifact(
                "${deviceDir}/output1.txt",
                "${hostDir}${File.separator}output1.txt"))
            verify(mockDeviceController).pull(createTestArtifact(
                "${deviceDir}/output2.txt",
                "${hostDir}${File.separator}output2.txt"))
            verify(mockDeviceController).pull(createTestArtifact(
                "${deviceDir}/subdir/output3.txt",
                "${hostDir}${File.separator}subdir${File.separator}output3.txt"))
        }
    }

    @Test
    fun runPluginAndFailsOnFileCopy() {
        val exception = IllegalStateException("some error")
        val hostDir = tempDirs.newFolder().absolutePath
        val deviceDir = "/onDevice/outputDir/"

        `when`(mockDeviceController.execute(
            eq(listOf("shell", "ls \"${deviceDir}\" | cat")),
            nullable(Duration::class.java)
        )).thenReturn(CommandResult(0, listOf("output1.txt")))

        `when`(mockDeviceController.pull(eq(createTestArtifact(
            "${deviceDir}/output1.txt",
            "${hostDir}${File.separator}output1.txt"))))
            .thenThrow(exception)

        `when`(mockDeviceController.execute(
            eq(listOf("shell", "[[ -d \"${deviceDir}/output1.txt\" ]]")),
            nullable(Duration::class.java)
        )).thenReturn(CommandResult(1, listOf()))

        runPlugin {
            additionalOutputDirectoryOnHost = hostDir
            additionalOutputDirectoryOnDevice = deviceDir
        }

        inOrder(mockDeviceController, mockLogger).apply {
            verify(mockDeviceController).execute(listOf("shell", "rm -rf \"${deviceDir}\""))
            verify(mockDeviceController).execute(listOf("shell", "mkdir -p \"${deviceDir}\""))
            verify(mockDeviceController).execute(listOf("shell", "ls \"${deviceDir}\" | cat"))
            verify(mockDeviceController).pull(createTestArtifact(
                "${deviceDir}/output1.txt",
                "${hostDir}${File.separator}output1.txt"))
            verify(mockLogger).log(eq(Level.WARNING), eq(exception), any())
        }
    }

    @Test
    fun benchmarkResultFilesShouldBeCopiedAfterEachTest() {
        val hostDir = tempDirs.newFolder().absolutePath
        val deviceDir = "/onDevice/outputDir/"

        val benchmarkMessage = """
            WARNING: Running on Emulator
            Benchmark is running on an emulator, which is not representative of
            real user devices. Use a physical device to benchmark. Emulator
            benchmark improvements might not carry over to a real user's
            experience (or even regress real device performance).
            FrameTimingBenchmark_start
            frameTime50thPercentileMs   [min  17](file://FrameTimingBenchmark_start_iter000_2021-07-15-21-32-39.trace),   [median  18](file://FrameTimingBenchmark_start_iter001_2021-07-15-21-33-09.trace),   [max  19](file://FrameTimingBenchmark_start_iter001_2021-07-15-21-33-09.trace)
            frameTime90thPercentileMs   [min  27](file://FrameTimingBenchmark_start_iter000_2021-07-15-21-32-39.trace),   [median  29](file://FrameTimingBenchmark_start_iter001_2021-07-15-21-33-09.trace),   [max  30](file://FrameTimingBenchmark_start_iter001_2021-07-15-21-33-09.trace)
            frameTime95thPercentileMs   [min  32](file://FrameTimingBenchmark_start_iter000_2021-07-15-21-32-39.trace),   [median  32](file://FrameTimingBenchmark_start_iter001_2021-07-15-21-33-09.trace),   [max  32](file://FrameTimingBenchmark_start_iter000_2021-07-15-21-32-39.trace)
            frameTime99thPercentileMs   [min  34](file://FrameTimingBenchmark_start_iter001_2021-07-15-21-33-09.trace),   [median  41](file://FrameTimingBenchmark_start_iter001_2021-07-15-21-33-09.trace),   [max  48](file://FrameTimingBenchmark_start_iter000_2021-07-15-21-32-39.trace)
            totalFrameCount   [min 285](file://FrameTimingBenchmark_start_iter000_2021-07-15-21-32-39.trace),   [median 291](file://FrameTimingBenchmark_start_iter001_2021-07-15-21-33-09.trace),   [max 296](file://FrameTimingBenchmark_start_iter001_2021-07-15-21-33-09.trace)
            Traces: Iteration [0](file://FrameTimingBenchmark_start_iter000_2021-07-15-21-32-39.trace) [1](file://FrameTimingBenchmark_start_iter001_2021-07-15-21-33-09.trace)
        """.trimIndent()
        val benchmarkOutputDir =
            "/sdcard/Android/media/com.example.macrobenchmark/additional_test_output"

        val traceFiles = listOf(
            "FrameTimingBenchmark_start_iter000_2021-07-15-21-32-39.trace",
            "FrameTimingBenchmark_start_iter001_2021-07-15-21-33-09.trace"
        )
        `when`(mockDeviceController.execute(
            eq(listOf("shell", "ls \"${benchmarkOutputDir}\" | cat")),
            nullable(Duration::class.java)
        )).thenReturn(CommandResult(0, traceFiles))
        traceFiles.forEach {
            `when`(mockDeviceController.execute(
                eq(listOf("shell", "[[ -d \"${benchmarkOutputDir}/$it\" ]]")),
                nullable(Duration::class.java)
            )).thenReturn(CommandResult(1, listOf()))
        }

        val testResultWithBenchmark = TestResultProto.TestResult.newBuilder().apply {
            testCaseBuilder.testClass = "TestClass"
            testCaseBuilder.testMethod = "testMethod"
            testCaseBuilder.testPackage = "test.package"
            addDetailsBuilder().apply {
                key = "android.studio.v2display.benchmark"
                value = benchmarkMessage
            }
            addDetailsBuilder().apply {
                key = "android.studio.v2display.benchmark.outputDirPath"
                value = benchmarkOutputDir
            }
        }.build()

        runPlugin(testResultWithBenchmark) {
            additionalOutputDirectoryOnHost = hostDir
            additionalOutputDirectoryOnDevice = deviceDir
        }

        verify(mockDeviceController).pull(createTestArtifact(
            "${benchmarkOutputDir}/FrameTimingBenchmark_start_iter000_2021-07-15-21-32-39.trace",
            "${hostDir}${File.separator}FrameTimingBenchmark_start_iter000_2021-07-15-21-32-39.trace"))
        verify(mockDeviceController).pull(createTestArtifact(
            "${benchmarkOutputDir}/FrameTimingBenchmark_start_iter001_2021-07-15-21-33-09.trace",
            "${hostDir}${File.separator}FrameTimingBenchmark_start_iter001_2021-07-15-21-33-09.trace"))

        assertThat(testResultAfterEach.outputArtifactCount).isEqualTo(3)
        assertThat(testResultAfterEach.getOutputArtifact(0).label.namespace).isEqualTo("android")
        assertThat(testResultAfterEach.getOutputArtifact(0).label.label)
            .isEqualTo("additionaltestoutput.benchmark.message")
        assertThat(testResultAfterEach.getOutputArtifact(0).sourcePath.path)
            .isEqualTo("${hostDir}${File.separator}additionaltestoutput.benchmark.message_test.package.TestClass.testMethod.txt")
        assertThat(testResultAfterEach.getOutputArtifact(1).label.namespace).isEqualTo("android")
        assertThat(testResultAfterEach.getOutputArtifact(1).label.label)
            .isEqualTo("additionaltestoutput.benchmark.trace")
        assertThat(testResultAfterEach.getOutputArtifact(1).sourcePath.path)
            .isEqualTo("${hostDir}${File.separator}FrameTimingBenchmark_start_iter000_2021-07-15-21-32-39.trace")
        assertThat(testResultAfterEach.getOutputArtifact(2).label.namespace).isEqualTo("android")
        assertThat(testResultAfterEach.getOutputArtifact(2).label.label)
            .isEqualTo("additionaltestoutput.benchmark.trace")
        assertThat(testResultAfterEach.getOutputArtifact(2).sourcePath.path)
            .isEqualTo("${hostDir}${File.separator}FrameTimingBenchmark_start_iter001_2021-07-15-21-33-09.trace")
    }

    @Test
    fun runPluginWithTestStorageService() {
        installTestStorageService()

        val hostDir = tempDirs.newFolder().absolutePath
        val deviceDir = AndroidAdditionalTestOutputPlugin.TEST_STORAGE_SERVICE_OUTPUT_DIR

        `when`(mockDeviceController.execute(
            eq(listOf("shell", "ls \"${deviceDir}\" | cat")),
            nullable(Duration::class.java)
        )).thenReturn(CommandResult(0, listOf("output1.txt", "output2.txt")))

        `when`(mockDeviceController.execute(
            eq(listOf("shell", "[[ -d \"${deviceDir}/output1.txt\" ]]")),
            nullable(Duration::class.java)
        )).thenReturn(CommandResult(1, listOf()))

        `when`(mockDeviceController.execute(
            eq(listOf("shell", "[[ -d \"${deviceDir}/output2.txt\" ]]")),
            nullable(Duration::class.java)
        )).thenReturn(CommandResult(1, listOf()))

        runPlugin {
            additionalOutputDirectoryOnHost = hostDir
        }

        inOrder(mockDeviceController).apply {
            verify(mockDeviceController).execute(listOf("shell", "rm -rf \"${deviceDir}\""))
            verify(mockDeviceController).execute(listOf("shell", "mkdir -p \"${deviceDir}\""))
            verify(mockDeviceController).execute(listOf(
                "shell",
                "appops set androidx.test.services MANAGE_EXTERNAL_STORAGE allow"
            ))
            verify(mockDeviceController).execute(listOf("shell", "ls \"${deviceDir}\" | cat"))
            verify(mockDeviceController).pull(createTestArtifact(
                "${deviceDir}/output1.txt",
                "${hostDir}${File.separator}output1.txt"))
            verify(mockDeviceController).pull(createTestArtifact(
                "${deviceDir}/output2.txt",
                "${hostDir}${File.separator}output2.txt"))
        }
    }
}
