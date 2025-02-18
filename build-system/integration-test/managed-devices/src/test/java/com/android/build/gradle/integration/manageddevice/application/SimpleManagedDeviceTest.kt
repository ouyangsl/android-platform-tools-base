package com.android.build.gradle.integration.manageddevice.application

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.build.gradle.integration.manageddevice.utils.CustomAndroidSdkRule
import com.android.build.gradle.integration.manageddevice.utils.addManagedDevice
import com.android.build.gradle.options.IntegerOption
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SimpleManagedDeviceTest {

    @get:Rule
    val customAndroidSdkRule = CustomAndroidSdkRule()

    @get:Rule
    val project = GradleTestProjectBuilder().fromTestProject("utp").create()

    private val executor: GradleTaskExecutor
        get() = customAndroidSdkRule.run { project.executorWithCustomAndroidSdk() }

    @Before
    fun setUp() {
        project.getSubproject("app").addManagedDevice("device1")
    }

    private fun assertTestReportExists() {
        val reportDir = FileUtils.join(
            project.getSubproject("app").buildDir,
            "reports",
            "androidTests",
            "managedDevice",
            "debug",
            "device1")
        assertThat(File(reportDir, "index.html")).exists()
        assertThat(File(reportDir, "com.example.android.kotlin.html")).exists()
        assertThat(File(reportDir, "com.example.android.kotlin.ExampleInstrumentedTest.html"))
            .exists()

        val mergedTestReportDir = FileUtils.join(
            project.getSubproject("app").buildDir,
            "reports",
            "androidTests",
            "managedDevice",
            "debug",
            "allDevices")
        assertThat(File(mergedTestReportDir, "index.html")).exists()
        assertThat(File(mergedTestReportDir, "com.example.android.kotlin.html")).exists()
        assertThat(File(mergedTestReportDir,
            "com.example.android.kotlin.ExampleInstrumentedTest.html")).exists()
    }

    private fun assertUtpLogExist() {
        val outputDir = FileUtils.join(
            project.getSubproject("app").buildDir,
            "outputs",
            "androidTest-results",
            "managedDevice",
            "debug",
            "device1")
        assertThat(File(outputDir, "utp.0.log")).exists()
        assertThat(File(outputDir, "utp.0.log")).contains(
            "INFO: Execute com.example.android.kotlin.ExampleInstrumentedTest.useAppContext: PASSED")
    }

    @Test
    fun runBasicManagedDevice() {
        executor.run(":app:device1DebugAndroidTest")

        assertTestReportExists()
        assertUtpLogExist()
    }

    @Test
    fun runBasicManagedDeviceWithSharding() {
        val result = executor
            .with(IntegerOption.MANAGED_DEVICE_SHARD_POOL_SIZE, 2)
            .run(":app:device1DebugAndroidTest")

        assertTestReportExists()
        result.stdout.use {
            assertThat(it).contains("tests on device1_0")
        }
        result.stdout.use {
            assertThat(it).contains("tests on device1_1")
        }
    }
}
