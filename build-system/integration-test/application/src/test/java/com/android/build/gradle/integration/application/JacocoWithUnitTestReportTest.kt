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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.gradle.tooling.BuildException
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class JacocoWithUnitTestReportTest(
    private val isJacocoPluginAppliedFromBuildFile: Boolean) {

    @get:Rule
    val testProject = GradleTestProjectBuilder()
        .fromTestProject("unitTesting")
        .create()

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name="isJacocoPluginAppliedFromBuildFile_{0}")
        fun params() = arrayOf(true, false)
    }

    @Before
    fun setup() {
        if (isJacocoPluginAppliedFromBuildFile) {
            TestFileUtils.appendToFile(
                testProject.buildFile,
                "apply plugin: 'jacoco'\n"
            )
        }

        // Only UnitTest coverage is needed for these tests, but validate that classes are not
        // instrumented twice when AndroidTest coverage is enabled: b/281266702
        TestFileUtils.appendToFile(
            testProject.buildFile,
            "android.buildTypes.debug.enableUnitTestCoverage true\n" +
                    "android.buildTypes.debug.enableAndroidTestCoverage true"
        )
    }

    @Test
    fun `test expected report contents`() {
        val run = testProject.executor().run("createDebugUnitTestCoverageReport")
        checkHighlightedSourceCodeReportFiles(testProject.buildDir)
        val generatedCoverageReport = FileUtils.join(
            testProject.buildDir,
            "reports",
            "coverage",
            "test",
            "debug",
            "index.html"
        )
        run.stdout.use {
            assertThat(
                ScannerSubject.assertThat(it)
                    .contains("View coverage report at ${generatedCoverageReport.toURI()}"))
        }
        assertThat(generatedCoverageReport.exists()).isTrue();
        val generatedCoverageReportHTML = generatedCoverageReport.readLines().joinToString("\n")
        val reportTitle = Regex("<span class=\"el_report\">(.*?)</span")
            .find(generatedCoverageReportHTML)
        val totalCoverageMetricsContents = Regex("<tfoot>(.*?)</tfoot>")
            .find(generatedCoverageReportHTML)
        val totalCoverageInfo = Regex("<td class=\"ctr2\">(.*?)</td>")
            .find(totalCoverageMetricsContents?.groups?.first()!!.value)
        val totalUnitTestCoveragePercentage = totalCoverageInfo!!.groups[1]!!.value
        // Checks if the report title is expected.
        assertThat(reportTitle!!.groups[1]!!.value).isEqualTo("debug")
        // Checks if the total line coverage on unit tests exceeds 0% i.e
        assertThat(totalUnitTestCoveragePercentage.trimEnd('%').toInt() > 0).isTrue()

        // Verify that only the debug reports have been created.
        val testReports = FileUtils.join(
            testProject.buildDir,
            "reports",
            "tests"
        )
        assertThat(testReports.listFiles().map(File::getName))
            .containsExactly("testDebugUnitTest")
    }

    // Regression test for b/188953818.
    private fun checkHighlightedSourceCodeReportFiles(buildDir: File) {
        val reportPackageInfoDir = FileUtils.join(
            buildDir, "reports", "coverage", "test", "debug", "com.android.tests")
        assertThat(FileUtils.join(reportPackageInfoDir, "MainActivity.java.html").exists())
            .isTrue()
        assertThat(FileUtils.join(reportPackageInfoDir, "someKotlinCode.kt.html").exists())
            .isTrue()

    }

    @Test(expected = BuildException::class)
    fun `report not generated for build types with unit test coverage disabled`() {
        // Build fails as the code coverage report task has not been registered as there is no
        // code coverage data for the release build type.
        testProject.execute("createReleaseUnitTestCoverageReport")
    }
}
