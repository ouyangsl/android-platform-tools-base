/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.compose.screenshot.tasks

import com.android.compose.screenshot.services.AnalyticsService
import com.google.common.io.Files
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Answers
import org.mockito.kotlin.mock
import java.io.File
import com.google.common.truth.Truth.assertThat
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

class ScreenshotTestReportTaskTest {
    @get:Rule
    val tempDirRule = TemporaryFolder()

    private lateinit var task: ScreenshotTestReportTask

    @Before
    fun setUp() {
        val project = ProjectBuilder.builder().withProjectDir(tempDirRule.newFolder()).build()
        task = project.tasks.create("screenshotTestReport", ScreenshotTestReportTask::class.java)
    }

    @Test
    fun createTestReport() {
        val outputDir = tempDirRule.newFolder()
        val resultsDir = tempDirRule.newFolder("results")
        val reportXml = File(resultsDir, "TEST-valid.xml")
        Files.asCharSink(reportXml, Charsets.UTF_8).write("""
            <?xml version='1.0' encoding='UTF-8' ?>
            <testsuite name="com.example.myapplication.ExampleInstrumentedTest" tests="8" failures="1" errors="0" skipped="0" time="7.169" timestamp="2021-08-10T21:09:43" hostname="localhost">
              <properties>
                <property name="device" value="Previews" />
              </properties>
              <testcase name="useAppContext1" classname="com.example.myapplication.ExampleInstrumentedTest" time="3.272">
              <success>Reference Images saved</success>
              <images>
              <reference path="someReferenceImagePath"/>
              <actual path="actualPath"/>
              <diff message="Images match"/>
              </images>
              </testcase>
              <testcase name="useAppContext2" classname="com.example.myapplication.ExampleInstrumentedTest" time="1.551">
              <failure>Images don't match</failure>
              <images>
              <reference path="someReferencePath"/>
              <actual path="actualPath"/>
              <diff path="diffPath"/>
              </images>
              </testcase>
              <testcase name="useAppContext3" classname="com.example.myapplication.ExampleInstrumentedTest" time="2.112">
              <failure>Images don't match</failure>
              <images>
              <reference path="someReferenceImagePath"/>
              <actual path="actualPath"/>
              <diff message="Size Mismatch. Reference image: 5x5 Actual image: 4x5"/>
              </images>
              </testcase>
              <testcase name="useAppContext4" classname="com.example.myapplication.ExampleInstrumentedTest" time="0.234">
              <failure>No Reference Image</failure>
              <images>
              <reference message="Reference image does not exist"/>
              <actual path="actualPath"/>
              <diff message="No diff"/>
              </images>
              </testcase>
            </testsuite>
        """.trimIndent())
        task.outputDir.set(outputDir)
        task.resultsDir.set(resultsDir)
        task.analyticsService.set(object: AnalyticsService() {
            override val buildServiceRegistry: BuildServiceRegistry =
                mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
            override fun getParameters(): Params = mock()
        })

        task.run()

        assertThat(outputDir.toPath().listDirectoryEntries()
            .filter { it.name.endsWith("html") }
            .map { it.name })
            .containsExactly(
            "index.html",
            "com.example.myapplication.html",
            "com.example.myapplication.ExampleInstrumentedTest.html"
            )
    }

    @Test
    fun createEmptyTestReport() {
        val outputDir = tempDirRule.newFolder()
        val resultsDir = tempDirRule.newFolder("results")
        task.outputDir.set(outputDir)
        task.resultsDir.set(resultsDir)
        task.analyticsService.set(object: AnalyticsService() {
            override val buildServiceRegistry: BuildServiceRegistry =
                mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
            override fun getParameters(): Params = mock()
        })

        task.run()

        assertThat(outputDir.toPath().listDirectoryEntries()
            .filter { it.name.endsWith("html") }
            .map { it.name }).containsExactly(
            "index.html"
        )
    }
}
