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

package com.android.build.gradle.integration.lint

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.GradleTaskSubject.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Integration test running lint on generated resources
 */
class LintGeneratedResourcesTest {

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestApp(
                MinimalSubProject.app("com.example.app")
                    .appendToBuild(
                        """
                            android {
                                lint {
                                    abortOnError = false
                                    textOutput = file("lint-results.txt")
                                    checkGeneratedSources = true
                                }
                            }

                            public class GenerateRes extends DefaultTask {
                                @Input
                                String value

                                @OutputFile
                                File outputFile

                                @TaskAction
                                void taskAction() {
                                    getOutputFile().text =
                                        '<?xml version="1.0" encoding="utf-8"?>\n' + getValue()
                                }
                            }

                            android.applicationVariants.all { variant ->
                                ConfigurableFileCollection resFolder = files("${"$"}{buildDir}/customRes/${"$"}{variant.dirName}")
                                def resGenerationTask = tasks.create(name: "generateResFor${"$"}{variant.name.capitalize()}", type: GenerateRes) {
                                    value '<resources>\n' +
                                            '    <!-- xml comment -->\n' +
                                            '    <string\n' +
                                            '        name="foo">Foo</string>\n' +
                                            '</resources>'
                                    outputFile file("${"$"}{resFolder.singleFile.absolutePath}/values/generated.xml")
                                }
                                resFolder.builtBy(resGenerationTask)
                                variant.registerGeneratedResFolders(resFolder)
                            }

                            android.testVariants.all { variant ->
                                ConfigurableFileCollection resFolder = files("${"$"}{buildDir}/customRes/${"$"}{variant.name}")
                                def resGenerationTask = tasks.create(name: "generateResFor${"$"}{variant.name.capitalize()}", type: GenerateRes) {
                                    value '<resources>\n' +
                                            '    <!-- xml comment -->\n' +
                                            '    <string\n' +
                                            '        name="foo">Foo</string>\n' +
                                            '</resources>'
                                    outputFile file("${"$"}{resFolder.singleFile.absolutePath}/values/generated.xml")
                                }
                                resFolder.builtBy(resGenerationTask)
                                variant.registerGeneratedResFolders(resFolder)
                            }
                        """.trimIndent()
                    )
            ).create()

    /** Test that changes to generated resources cause the lint tasks to re-run as expected. */
    @Test
    fun testNotUpToDate() {
        project.executor().run("clean", "lintDebug")
        assertThat(project.buildResult.getTask(":lintReportDebug")).didWork()
        assertThat(project.buildResult.getTask(":lintAnalyzeDebug")).didWork()
        val lintReport = project.file("lint-results.txt")
        assertThat(lintReport).exists()
        assertThat(lintReport).doesNotContain(
            "generated.xml:3: Error: Found byte-order-mark in the middle of a file [ByteOrderMark]"
        )

        // Add a byteOrderMark to the generated resources
        val byteOrderMark = "\ufeff"
        TestFileUtils.searchAndReplace(project.buildFile, "xml comment", byteOrderMark)

        project.executor().run("lintDebug")
        assertThat(project.buildResult.getTask(":lintReportDebug")).didWork()
        assertThat(project.buildResult.getTask(":lintAnalyzeDebug")).didWork()
        assertThat(lintReport).exists()
        assertThat(lintReport).contains(
            "generated.xml:3: Error: Found byte-order-mark in the middle of a file [ByteOrderMark]"
        )
    }

    /** Regression test for b/337776938 */
    @Test
    fun testDependencyOnGeneratedResForAndroidTest() {
        project.executor().run("clean", "lintDebug")
        assertThat(project.buildResult.getTask(":generateResForDebugAndroidTest")).didWork()
    }
}
