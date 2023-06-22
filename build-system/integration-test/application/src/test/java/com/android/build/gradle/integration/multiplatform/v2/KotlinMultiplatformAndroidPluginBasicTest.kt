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

package com.android.build.gradle.integration.multiplatform.v2

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class KotlinMultiplatformAndroidPluginBasicTest {

    @Suppress("DEPRECATION") // kmp doesn't support configuration caching for now (b/276472789)
    @get:Rule
    val project = GradleTestProjectBuilder()
        .fromTestProject("kotlinMultiplatform")
        .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
        .create()

    @Test
    fun creatingArbitraryCompilationShouldFail() {
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin {
                    androidExperimental {
                        compilations.create("release")
                    }
                }
            """.trimIndent()
        )

        val result =
            project.executor().expectFailure().run(":kmpFirstLib:assembleAndroidMain")

        Truth.assertThat(result.failureMessage).contains(
            "Kotlin multiplatform android plugin doesn't support creating arbitrary compilations."
        )
    }

    @Test
    fun creatingTwoUnitTestCompilationsShouldFail() {
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin {
                    androidExperimental {
                        withAndroidTestOnJvm()
                    }
                }
            """.trimIndent()
        )

        val result =
            project.executor().expectFailure().run(":kmpFirstLib:assembleAndroidMain")

        Truth.assertThat(result.failureMessage).contains(
            "Android tests on jvm has already been enabled, and a corresponding compilation (`unitTest`) has already been created."
        )
    }

    @Test
    fun `accessing predefined compilations should succeed`() {
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin {
                    androidExperimental {
                        afterEvaluate {
                            compilations {
                                val main by getting {
                                }
                                val unitTest by getting {
                                }
                                val instrumentedTest by getting {
                                }
                            }
                        }
                    }
                }
            """.trimIndent()
        )

        project.executor().run(":kmpFirstLib:androidPrebuild")
    }
}
