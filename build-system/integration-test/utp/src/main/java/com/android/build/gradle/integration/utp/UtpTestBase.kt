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

package com.android.build.gradle.integration.utp

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.LoggingLevel
import com.android.build.gradle.integration.common.utils.SdkHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.PathSubject.assertThat
import com.android.tools.utp.plugins.host.device.info.proto.AndroidTestDeviceInfoProto.AndroidTestDeviceInfo
import com.google.common.truth.Truth.assertThat
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import org.junit.Ignore
import java.io.File
import java.nio.file.Files
import org.junit.Rule
import org.junit.Test

/**
 * A base test class for UTP integration tests. Tests defined in this class will be
 * executed against both connected check and managed devices to ensure the feature
 * parity.
 */
abstract class UtpTestBase {

    lateinit var testTaskName: String
    lateinit var testResultXmlPath: String
    lateinit var testReportPath: String
    lateinit var testResultPbPath: String
    lateinit var utpProfilePath: String
    lateinit var aggTestResultPbPath: String
    lateinit var testCoverageXmlPath: String
    lateinit var testLogcatPath: String
    lateinit var testAdditionalOutputPath: String

    @get:Rule
    var project = GradleTestProject.builder()
        .fromTestProject("utp")
        .enableProfileOutput()
        .create()

    open val executor: GradleTaskExecutor
        get() = project.executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
            .withLoggingLevel(LoggingLevel.LIFECYCLE)

    abstract fun selectModule(moduleName: String, isDynamicFeature: Boolean)

    companion object {
        const val ANDROIDX_TEST_VERSION = "1.5.0-alpha02"
    }

    private fun enableAndroidTestOrchestrator(subProjectName: String) {
        val subProject = project.getSubproject(subProjectName)
        TestFileUtils.appendToFile(
            subProject.buildFile,
            """
            android.testOptions.execution 'ANDROIDX_TEST_ORCHESTRATOR'
            // Orchestrator requires some setup time and it usually takes
            // about an minute. Increase the timeout for running "am instrument" command
            // to 3 minutes.
            android.adbOptions.timeOutInMs=180000

            android.defaultConfig.testInstrumentationRunnerArguments useTestStorageService: 'true'
            android.defaultConfig.testInstrumentationRunnerArguments clearPackageData: 'true'

            dependencies {
              androidTestUtil 'androidx.test:orchestrator:$ANDROIDX_TEST_VERSION'
              androidTestUtil 'androidx.test.services:test-services:$ANDROIDX_TEST_VERSION'
            }
            """.trimIndent())
    }

    private fun enableForceCompilation(subProjectName: String) {
        val subProject = project.getSubproject(subProjectName)
        TestFileUtils.appendToFile(
            subProject.buildFile,
            """
            android.experimentalProperties["android.experimental.force-aot-compilation"] = true
            """.trimIndent())
    }

    private fun enableCodeCoverage(subProjectName: String) {
        val subProject = project.getSubproject(subProjectName)
        TestFileUtils.appendToFile(
            subProject.buildFile,
            """
            android.buildTypes.debug.testCoverageEnabled true
            android.defaultConfig.testInstrumentationRunnerArguments useTestStorageService: 'true'

            dependencies {
              androidTestUtil 'androidx.test.services:test-services:$ANDROIDX_TEST_VERSION'
            }
            """.trimIndent())
    }

    private fun enableTestStorageService(subProjectName: String) {
        val subProject = project.getSubproject(subProjectName)
        TestFileUtils.appendToFile(
            subProject.buildFile,
            """
            dependencies {
              androidTestUtil 'androidx.test.services:test-services:$ANDROIDX_TEST_VERSION'
            }
            """.trimIndent())
    }

    private fun enableDynamicFeature(subProjectName: String) {
        val appProject = project.getSubproject("app")
        TestFileUtils.appendToFile(
            appProject.buildFile,
            """
                android.dynamicFeatures = [':$subProjectName']
            """.trimIndent())
    }

    private fun getDeviceInfo(aggTestResultPb: File): AndroidTestDeviceInfo? {
        val testSuiteResult = aggTestResultPb.inputStream().use {
            TestSuiteResult.parseFrom(it)
        }
        return testSuiteResult.testResultList.asSequence()
            .flatMap { testResult ->
                testResult.outputArtifactList
            }
            .filter { artifact ->
                artifact.label.label == "device-info" && artifact.label.namespace == "android"
            }
            .map { artifact ->
                File(artifact.sourcePath.path).inputStream().use {
                    AndroidTestDeviceInfo.parseFrom(it)
                }
            }
            .firstOrNull()
    }

    @Test
    @Throws(Exception::class)
    fun androidTestWithCodeCoverage() {
        selectModule("app", false)
        enableCodeCoverage("app")

        executor.run(testTaskName)

        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()
        assertThat(project.file(utpProfilePath)).exists()
        assertThat(project.file(testCoverageXmlPath)).contains(
            """<method name="stubFuncForTestingCodeCoverage" desc="()V" line="9">"""
        )
        assertThat(project.file(testCoverageXmlPath)).contains(
            """<counter type="INSTRUCTION" missed="3" covered="5"/>"""
        )
    }

    @Test
    @Throws(Exception::class)
    fun androidTestWithTestFailures() {
        selectModule("appWithTestFailures", false)

        executor.expectFailure().run(testTaskName)
        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()
        assertThat(project.file(utpProfilePath)).exists()
    }

    @Test
    @Throws(Exception::class)
    fun androidTestWithOrchestrator() {
        selectModule("app", false)
        enableAndroidTestOrchestrator("app")

        executor.run(testTaskName)

        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()
        assertThat(project.file(utpProfilePath)).exists()
    }

    @Test
    @Throws(Exception::class)
    fun androidTestWithOrchestratorAndCodeCoverage() {
        selectModule("app", false)
        enableAndroidTestOrchestrator("app")
        enableCodeCoverage("app")

        executor.run(testTaskName)

        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()
        assertThat(project.file(utpProfilePath)).exists()
        assertThat(project.file(testCoverageXmlPath)).contains(
            """<method name="stubFuncForTestingCodeCoverage" desc="()V" line="9">"""
        )
        assertThat(project.file(testCoverageXmlPath)).contains(
            """<counter type="INSTRUCTION" missed="3" covered="5"/>"""
        )
    }

    @Test
    @Throws(Exception::class)
    fun connectedAndroidTestWithLogcat() {
        selectModule("app", false)

        executor.run(testTaskName)

        assertThat(project.file(testLogcatPath)).exists()
        val logcatText = project.file(testLogcatPath).readText()
        assertThat(logcatText).contains("TestRunner: started: useAppContext(com.example.android.kotlin.ExampleInstrumentedTest)")
        assertThat(logcatText).contains("TestLogger: test logs")
        assertThat(logcatText).contains("TestRunner: finished: useAppContext(com.example.android.kotlin.ExampleInstrumentedTest)")
    }

    @Test
    @Throws(Exception::class)
    fun connectedAndroidTestFromTestOnlyModule() {
        selectModule("testOnlyModule", false)

        executor.run(testTaskName)

        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()
    }

    @Test
    @Throws(Exception::class)
    fun additionalTestOutputWithTestStorageService() {
        selectModule("app", false)
        enableTestStorageService("app")

        val testSrc = """
            package com.example.helloworld

            import androidx.test.ext.junit.runners.AndroidJUnit4
            import androidx.test.services.storage.TestStorage
            import org.junit.Test
            import org.junit.runner.RunWith

            @RunWith(AndroidJUnit4::class)
            class TestStorageServiceExampleTest {
                @Test
                fun writeFileUsingTestStorageService() {
                    TestStorage().openOutputFile("myTestStorageOutputFile1").use {
                        it.write("output message1".toByteArray())
                    }
                    TestStorage().openOutputFile("myTestStorageOutputFile2.txt").use {
                        it.write("output message2".toByteArray())
                    }
                    TestStorage().openOutputFile("subdir/myTestStorageOutputFile3").use {
                        it.write("output message3".toByteArray())
                    }
                    TestStorage().openOutputFile("subdir/nested/myTestStorageOutputFile4").use {
                        it.write("output message4".toByteArray())
                    }
                    TestStorage().openOutputFile("subdir/white space/myTestStorageOutputFile5").use {
                        it.write("output message5".toByteArray())
                    }
                }
            }
        """.trimIndent()
        val testStorageServiceExampleTest = project.projectDir
            .toPath()
            .resolve("app/src/androidTest/java/com/example/helloworld/TestStorageServiceExampleTest.kt")
        Files.createDirectories(testStorageServiceExampleTest.parent)
        Files.write(testStorageServiceExampleTest, testSrc.toByteArray())

        executor.run(testTaskName)

        assertThat(project.file("${testAdditionalOutputPath}/myTestStorageOutputFile1"))
            .contains("output message1")
        assertThat(project.file("${testAdditionalOutputPath}/myTestStorageOutputFile2.txt"))
            .contains("output message2")
        assertThat(project.file("${testAdditionalOutputPath}/subdir/myTestStorageOutputFile3"))
            .contains("output message3")
        assertThat(project.file("${testAdditionalOutputPath}/subdir/nested/myTestStorageOutputFile4"))
            .contains("output message4")
        assertThat(project.file("${testAdditionalOutputPath}/subdir/white space/myTestStorageOutputFile5"))
            .contains("output message5")
    }

    @Test
    @Throws(Exception::class)
    fun additionalTestOutputWithoutTestStorageService() {
        selectModule("app", false)

        val testSrc = """
            package com.example.helloworld

            import androidx.test.ext.junit.runners.AndroidJUnit4
            import org.junit.Test
            import org.junit.runner.RunWith
            import java.io.File

            @RunWith(AndroidJUnit4::class)
            class AdditionalTestOutputExampleTest {
                @Test
                fun writeFileWithoutTestStorageService() {
                    val dir = File("/sdcard/Android/media/com.example.android.kotlin/additional_test_output").also {
                        it.mkdirs()
                    }
                    File(dir,"myTestFile1").apply {
                        createNewFile()
                        writeText("output message 1")
                    }
                }
            }
        """.trimIndent()
        val exampleTest = project.projectDir
            .toPath()
            .resolve("app/src/androidTest/java/com/example/helloworld/AdditionalTestOutputExampleTest.kt")
        Files.createDirectories(exampleTest.parent)
        Files.write(exampleTest, testSrc.toByteArray())

        executor.run(testTaskName)

        assertThat(project.file("${testAdditionalOutputPath}/myTestFile1")).contains("output message 1")
    }

    @Test
    @Throws(Exception::class)
    fun additionalTestOutputWithBenchmarkFiles() {
        selectModule("app", false)

        val testSrc = """
            package com.example.helloworld

            import android.os.Bundle
            import android.os.Environment
            import androidx.test.platform.app.InstrumentationRegistry
            import androidx.test.ext.junit.runners.AndroidJUnit4

            import org.junit.Test
            import org.junit.runner.RunWith

            import java.io.File

            @RunWith(AndroidJUnit4::class)
            class AdditionalTestOutputExampleTest {
                @Test
                fun createSampleFileAndReportIt() {
                    val instrumentation = InstrumentationRegistry.getInstrumentation()
                    // Tries to report a bundle with additional test output
                    @Suppress("DEPRECATION")
                    val outputFolder = instrumentation
                        .targetContext
                        .externalMediaDirs.firstOrNull {
                            Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED
                        }
                        ?: throw Exception("Cannot get external storage due to not mounted")

                    val sampleFile = File(outputFolder, "sampleFile_1")
                        .apply { writeText("This is a sample file.") }

                    // Note that the path used here should be relative to outputFolder, so just the filename.
                    val summary = "[sample file](file://" + sampleFile.name + ")"

                    val bundle = Bundle().apply {
                        putString("android.studio.display.benchmark", summary)
                        putString("android.studio.v2display.benchmark", summary)
                        putString("android.studio.v2display.benchmark.outputDirPath", outputFolder.absolutePath)
                        putString("additionalTestOutputFile_sampleFile", sampleFile.absolutePath)
                    }
                    InstrumentationRegistry
                        .getInstrumentation()
                        .sendStatus(2, bundle)
                }
            }
        """.trimIndent()
        val exampleTest = project.projectDir
            .toPath()
            .resolve("app/src/androidTest/java/com/example/helloworld/AdditionalTestOutputExampleTest.kt")
        Files.createDirectories(exampleTest.parent)
        Files.write(exampleTest, testSrc.toByteArray())

        executor.run(testTaskName)

        assertThat(project.file("${testAdditionalOutputPath}/sampleFile_1")).contains("This is a sample file.")
        assertThat(project.file("${testAdditionalOutputPath}/" +
            "additionaltestoutput.benchmark.message_com.example.helloworld" +
            ".AdditionalTestOutputExampleTest.createSampleFileAndReportIt.txt"))
            .contains("[sample file](file://sampleFile_1)")
    }

    @Test
    @Throws(Exception::class)
    fun androidTestWithDynamicFeature() {
        selectModule("dynamicfeature1", true)
        enableDynamicFeature("dynamicfeature1")

        executor.run(testTaskName)

        assertThat(project.file(testResultXmlPath)).exists()
        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()
        assertThat(project.file(aggTestResultPbPath)).exists()

        val deviceInfo = getDeviceInfo(project.file(aggTestResultPbPath))
        assertThat(deviceInfo).isNotNull()
        assertThat(deviceInfo?.name).isNotEmpty()

        // Run the task again after clean. This time the task configuration is
        // restored from the configuration cache. We expect no crashes.
        executor.run("clean")

        assertThat(project.file(testResultXmlPath)).doesNotExist()
        assertThat(project.file(testReportPath)).doesNotExist()
        assertThat(project.file(testResultPbPath)).doesNotExist()
        assertThat(project.file(aggTestResultPbPath)).doesNotExist()

        executor.run(testTaskName)

        assertThat(project.file(testResultXmlPath)).exists()
        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()
        assertThat(project.file(aggTestResultPbPath)).exists()
    }

    @Test
    @Throws(Exception::class)
    fun androidTestWithOrchestratorWithDynamicFeature() {
        selectModule("dynamicfeature1", true)
        enableDynamicFeature("dynamicfeature1")
        enableAndroidTestOrchestrator("dynamicfeature1")

        executor.run(testTaskName)


        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()
    }


    @Test
    @Throws(Exception::class)
    fun connectedAndroidTestWithLogcatWithDynamicFeature() {
        selectModule("dynamicfeature1", true)
        enableDynamicFeature("dynamicfeature1")

        executor.run(testTaskName)

        assertThat(project.file(testLogcatPath)).exists()
        val logcatText = project.file(testLogcatPath).readText()
        assertThat(logcatText).contains("TestRunner: started: useAppContext(com.example.android.kotlin.feature.ExampleInstrumentedTest)")
        assertThat(logcatText).contains("TestLogger: test logs")
        assertThat(logcatText).contains("TestRunner: finished: useAppContext(com.example.android.kotlin.feature.ExampleInstrumentedTest)")
    }

    @Test
    @Throws(Exception::class)
    fun connectedAndroidTestWithAdditionalTestOutputUsingTestStorageServiceWithDynamicFeature() {
        selectModule("dynamicfeature1", true)
        enableDynamicFeature("dynamicfeature1")
        enableTestStorageService("dynamicfeature1")

        val testSrc = """
            package com.example.helloworld

            import androidx.test.ext.junit.runners.AndroidJUnit4
            import androidx.test.services.storage.TestStorage
            import org.junit.Test
            import org.junit.runner.RunWith

            @RunWith(AndroidJUnit4::class)
            class TestStorageServiceExampleTest {
                @Test
                fun writeFileUsingTestStorageService() {
                    TestStorage().openOutputFile("myTestStorageOutputFile1").use {
                        it.write("output message1".toByteArray())
                    }
                    TestStorage().openOutputFile("myTestStorageOutputFile2.txt").use {
                        it.write("output message2".toByteArray())
                    }
                    TestStorage().openOutputFile("subdir/myTestStorageOutputFile3").use {
                        it.write("output message3".toByteArray())
                    }
                    TestStorage().openOutputFile("subdir/nested/myTestStorageOutputFile4").use {
                        it.write("output message4".toByteArray())
                    }
                    TestStorage().openOutputFile("subdir/white space/myTestStorageOutputFile5").use {
                        it.write("output message5".toByteArray())
                    }
                }
            }
        """.trimIndent()
        val testStorageServiceExampleTest = project.projectDir
            .toPath()
            .resolve("dynamicfeature1/src/androidTest/java/com/example/helloworld/TestStorageServiceExampleTest.kt")
        Files.createDirectories(testStorageServiceExampleTest.parent)
        Files.write(testStorageServiceExampleTest, testSrc.toByteArray())

        executor.run(testTaskName)

        assertThat(project.file("${testAdditionalOutputPath}/myTestStorageOutputFile1"))
            .contains("output message1")
        assertThat(project.file("${testAdditionalOutputPath}/myTestStorageOutputFile2.txt"))
            .contains("output message2")
        assertThat(project.file("${testAdditionalOutputPath}/subdir/myTestStorageOutputFile3"))
            .contains("output message3")
        assertThat(project.file("${testAdditionalOutputPath}/subdir/nested/myTestStorageOutputFile4"))
            .contains("output message4")
        assertThat(project.file("${testAdditionalOutputPath}/subdir/white space/myTestStorageOutputFile5"))
            .contains("output message5")
    }

    @Test
    @Throws(Exception::class)
    fun androidTestWithForceCompilation() {
        selectModule("app", false)
        enableForceCompilation("app")

        executor.run(testTaskName)

        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()
        assertThat(File(project.file(testResultPbPath).parentFile, "utp.0.log")).contains(
            "INFO: Running force AOT compilation for com.example.android.kotlin")
        assertThat(File(project.file(testResultPbPath).parentFile, "utp.0.log")).contains(
            "INFO: Running force AOT compilation for com.example.android.kotlin.test")
    }

    /**
     * TODO: Enable the test once b/261739458 is fixed.
     */
    @Ignore("b/261739458")
    @Test
    @Throws(Exception::class)
    fun androidTestWithOrchestratorAndCodeCoverageWithDynamicFeature() {
        selectModule("dynamicfeature1", true)
        enableAndroidTestOrchestrator("app")
        enableCodeCoverage("app")
        enableAndroidTestOrchestrator("dynamicfeature1")
        enableDynamicFeature("dynamicfeature1")

        executor.run(testTaskName)

        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()
        assertThat(project.file(testCoverageXmlPath)).contains(
                """<method name="stubDynamicFeature1FuncForTestingCodeCoverage" desc="()V" line="9">"""
        )
        assertThat(project.file(testCoverageXmlPath)).contains(
                """<counter type="INSTRUCTION" missed="3" covered="5"/>"""
        )
    }

    /**
     * TODO: Enable the test once b/261739458 is fixed.
     */
    @Ignore("b/261739458")
    @Test
    @Throws(Exception::class)
    fun androidTestWithCodeCoverageWithDynamicFeature() {
        selectModule("dynamicfeature1", true)
        enableDynamicFeature("dynamicfeature1")
        enableCodeCoverage("app")

        executor.run(testTaskName)

        assertThat(project.file(testReportPath)).exists()
        assertThat(project.file(testResultPbPath)).exists()
        assertThat(project.file(testCoverageXmlPath)).contains(
                """<method name="stubFuncForTestingCodeCoverage" desc="()V" line="9">"""
        )
        assertThat(project.file(testCoverageXmlPath)).contains(
                """<counter type="INSTRUCTION" missed="3" covered="5"/>"""
        )
    }
}
