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

package com.android.build.gradle.integration.model

import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class KotlinSourcesModelTest {

    @get:Rule
    val project = builder()
            .fromTestApp(KotlinHelloWorldApp.forPlugin("com.android.application"))
            .create()

    @Test
    fun kotlinSourcesLocationInAndroidBlock() {
        project.buildFile.appendText("""

            android {
                sourceSets {
                    main {
                         kotlin.srcDirs = ["src/main/kotlinDir"]
                    }
                }
            }
        """.trimIndent())

        val basicProject =
            project.modelV2().fetchModels().container.singleProjectInfo.basicAndroidProject!!
        assertThat(basicProject.mainSourceSet!!.sourceProvider.kotlinDirectories)
                .containsExactly(
                        project.file("src/main/kotlinDir"),
                        project.file("src/main/java"),
                        project.file("src/main/kotlin"),
                )
    }

    @Test
    fun kotlinSourcesLocation() {
        project.buildFile.appendText("""

            kotlin {
                sourceSets {
                    main {
                         kotlin {
                             srcDir "src/main/kotlinDir"
                         }
                    }
                }
            }
        """.trimIndent())

        val basicProject =
            project.modelV2().fetchModels().container.singleProjectInfo.basicAndroidProject!!
        assertThat(basicProject.mainSourceSet!!.sourceProvider.kotlinDirectories)
                .containsExactly(
                        project.file("src/main/kotlinDir"),
                        project.file("src/main/java"),
                        project.file("src/main/kotlin"),
                )
    }

    @Test
    fun testKotlinMultiplatform() {
        val updatedBuildFileContents = project.buildFile.readText()
            .replace("kotlin-android", "kotlin-multiplatform")
            .replace("    kotlinOptions {\n" + "        jvmTarget = JavaVersion.VERSION_1_8\n" + "    }\n", "")
        project.buildFile.writeText(updatedBuildFileContents)
        project.buildFile.appendText(
            """

            kotlin {
                android()

                sourceSets {
                    named("androidInstrumentedTest") {
                        dependencies {
                        }
                    }
                }

                android {
                    compilations.all {
                        kotlinOptions.jvmTarget = "1.8"
                    }
                }
            }

            // TODO workaround for https://youtrack.jetbrains.com/issue/KT-43944 is fixed
            configurations.create("testApi")
            configurations.create("testDebugApi")
            configurations.create("testReleaseApi")
            """.trimIndent()
        )

        val basicProject =
            project.modelV2()
                .fetchModels().container.singleProjectInfo.basicAndroidProject!!
        val deviceTestsKotlinDirs =
                basicProject.mainSourceSet!!.androidTestSourceProvider!!.kotlinDirectories
        assertThat(deviceTestsKotlinDirs)
                .containsExactly(
                        project.file("src/androidTest/java"),
                        project.file("src/androidTest/kotlin"),
                        project.file("src/androidInstrumentedTest/kotlin"),
                )
        val unitTestsKotlinDirs =
            basicProject.mainSourceSet!!.unitTestSourceProvider!!.kotlinDirectories
        assertThat(unitTestsKotlinDirs)
                .containsExactly(
                        project.file("src/test/java"),
                        project.file("src/test/kotlin"),
                        project.file("src/androidUnitTest/kotlin"),
                )
    }
}
