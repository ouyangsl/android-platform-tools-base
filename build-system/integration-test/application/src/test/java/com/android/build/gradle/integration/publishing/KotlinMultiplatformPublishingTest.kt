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

package com.android.build.gradle.integration.publishing

import com.android.build.gradle.integration.common.fixture.model.normalizeVersionsOfCommonDependencies
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProjectBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Test expected publishing output when AGP is used in Kotlin MPP projects. */
class KotlinMultiplatformPublishingTest {

    @get:Rule
    val project = createGradleProjectBuilder {
        rootProject {
            plugins.add(PluginType.ANDROID_LIB)
            plugins.add(PluginType.KOTLIN_MPP)
            plugins.add(PluginType.MAVEN_PUBLISH)
            android {
                setUpHelloWorld()
                minSdk = 24
            }
            appendToBuildFile {
                """
                    group = "com.example"
                    version = "0.1.2"
                    publishing {
                        repositories {
                            maven {
                                url = new File(buildDir, "testRepo")
                                name = "buildDir"
                            }
                        }
                    }

                    kotlin {
                        android { publishAllLibraryVariants() }
                    }
                """.trimIndent()
            }
        }
    }.withKotlinGradlePlugin(true).create()

    @Test
    fun testKotlinMultiplatform() {
        project.executor()
            .run("publishAllPublicationsToBuildDirRepository")

        val mainModule =
            project.projectDir.resolve("build/testRepo/com/example/project/0.1.2/project-0.1.2.module")
        val androidModule =
            project.projectDir.resolve("build/testRepo/com/example/project-android/0.1.2/project-android-0.1.2.module")
        val androidDebugModule =
            project.projectDir.resolve("build/testRepo/com/example/project-android-debug/0.1.2/project-android-debug-0.1.2.module")

        assertThat(normalizeModuleFile(mainModule)).isEqualTo(getExpectedFile("project.module"))
        assertThat(normalizeModuleFile(androidModule)).isEqualTo(getExpectedFile("project-android.module"))
        assertThat(normalizeModuleFile(androidDebugModule)).isEqualTo(getExpectedFile("project-android-debug.module"))
    }

    private fun getExpectedFile(fileName: String): String {
        return KotlinMultiplatformPublishingTest::class.java.let { klass ->
            klass.getResourceAsStream("${klass.simpleName}/$fileName")!!.reader().use {
                it.readText().trim()
            }
        }
    }

    private fun normalizeModuleFile(file: File): String {
        val original = file.readText().trim()
        return original.normalizeVersionsOfCommonDependencies()
            .replace(Regex("\"sha512\": \".*\""), "\"sha512\": \"{DIGEST}\"")
            .replace(Regex("\"sha256\": \".*\""), "\"sha256\": \"{DIGEST}\"")
            .replace(Regex("\"sha1\": \".*\""), "\"sha1\": \"{DIGEST}\"")
            .replace(Regex("\"md5\": \".*\""), "\"md5\": \"{DIGEST}\"")
            .replace(Regex("\"size\": .*,"), "\"size\": {SIZE},")
    }
}
