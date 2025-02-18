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

package com.android.build.gradle.integration.lint

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import org.junit.Rule
import org.junit.Test

class LintConfigurationCacheTest {

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .fromTestApp(
                MinimalSubProject.lib("com.example.lib")
                    .appendToBuild(
                        """
                            android {
                                libraryVariants.all { variant ->
                                    if (variant.name == "debug") {
                                        FileTree cTree =
                                            project.fileTree(
                                                new File(
                                                    project.buildDir,
                                                    "generated/source/kapt/debug"
                                                )
                                            )
                                        cTree.builtBy(tasks.findByName("generateSrcs"))
                                        cTree.include("**/*.java")
                                        registerExternalAptJavaOutput(cTree)
                                    }
                                }
                            }

                            tasks.register("generateSrcs") {
                                File myOutputDir =
                                    new File(project.buildDir, "generated/source/kapt/debug")
                                doFirst {
                                    myOutputDir.deleteDir()
                                    myOutputDir.mkdirs()
                                    new File(myOutputDir, "Foo.java").text = "public class Foo {}"
                                }
                            }

                        """.trimIndent()
                    )
            )
            .create()

    /** Regression test for b/285320724. */
    @Test
    fun testLintConfigurationCache() {
        project.executor().run("generateDebugLintModel")
        project.executor().run("generateDebugLintModel")
        project.buildResult.assertConfigurationCacheHit()
    }
}
