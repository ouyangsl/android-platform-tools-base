/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.integration.kotlin

import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.VERSION_CATALOG
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProjectBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.dsl.ModulePropertyKey.BooleanWithDefault.SCREENSHOT_TEST
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.TestUtils
import com.android.testutils.truth.PathSubject
import org.junit.Rule
import org.junit.Test

class BuiltInKotlinForScreenshotTestTest {

    @get:Rule
    val project =
        createGradleProjectBuilder {
            subProject(":lib") {
                plugins.add(PluginType.ANDROID_LIB)
                plugins.add(PluginType.KOTLIN_ANDROID)
                android {
                    setUpHelloWorld()
                    minSdk = 21
                }
                appendToBuildFile {
                    """
                        kotlin {
                            jvmToolchain(17)
                        }

                        """.trimIndent()
                }
            }
            subProject(":lib2") {
                plugins.add(PluginType.ANDROID_LIB)
                plugins.add(PluginType.KOTLIN_ANDROID)
                android {
                    setUpHelloWorld()
                }
                appendToBuildFile {
                    """
                        kotlin {
                            jvmToolchain(17)
                        }

                        """.trimIndent()
                }
            }
        }.withKotlinGradlePlugin(true)
            .create()

    /**
     * Include dependency on "androidx.compose.ui:ui-tooling-preview:1.6.5" as a regression test for
     * b/338512598
     */
    @Test
    fun testModuleAndExternalDependencies() {
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            "${BooleanOption.ENABLE_SCREENSHOT_TEST.propertyName}=true"
        )
        val lib = project.getSubproject(":lib")
        lib.buildFile.appendText(
            """
                android.experimentalProperties["${SCREENSHOT_TEST.key}"] = true

                dependencies {
                    screenshotTestImplementation("org.jetbrains.kotlin:kotlin-stdlib:${TestUtils.KOTLIN_VERSION_FOR_TESTS}")
                    screenshotTestImplementation("androidx.compose.ui:ui-tooling-preview:1.6.5")
                    screenshotTestImplementation(project(":lib2"))
                }
                """.trimIndent()
        )
        lib.file("src/screenshotTest/kotlin/LibScreenshotTest.kt").let {
            it.parentFile.mkdirs()
            it.writeText(
                """
                    package com.foo.library

                    import com.foo.library.two.LibTwoClass
                    import androidx.compose.ui.tooling.preview.Preview

                    class LibScreenshotTest
                    """.trimIndent()
            )
        }
        val lib2 = project.getSubproject(":lib2")
        lib2.file("src/main/kotlin/LibTwoClass.kt").let {
            it.parentFile.mkdirs()
            it.writeText(
                """
                    package com.foo.library.two
                    class LibTwoClass
                    """.trimIndent()
            )
        }
        lib.executor()
            .with(BooleanOption.USE_ANDROID_X, true)
            .run(":lib:compileDebugScreenshotTestKotlin")
        val screenshotTestClassFile =
            project.getSubproject("lib")
                .getIntermediateFile(
                    "built_in_kotlinc",
                    "debugScreenshotTest",
                    "compileDebugScreenshotTestKotlin",
                    "classes",
                    "com",
                    "foo",
                    "library",
                    "LibScreenshotTest.class"
                )
        PathSubject.assertThat(screenshotTestClassFile).exists()
    }

    @Test
    fun testInternalModifierAccessible() {
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            "${BooleanOption.ENABLE_SCREENSHOT_TEST.propertyName}=true"
        )
        val lib = project.getSubproject(":lib")
        lib.buildFile.appendText(
            """
                android.experimentalProperties["${SCREENSHOT_TEST.key}"] = true

                dependencies {
                    screenshotTestImplementation("org.jetbrains.kotlin:kotlin-stdlib:${TestUtils.KOTLIN_VERSION_FOR_TESTS}")
                }
                """.trimIndent()
        )
        lib.file("src/screenshotTest/kotlin/LibScreenshotTestFoo.kt").let {
            it.parentFile.mkdirs()
            it.writeText(
                """
                    package com.foo.library
                    class LibScreenshotTestFoo {
                      init { LibFoo().bar() }
                    }
                    """.trimIndent()
            )
        }
        lib.getMainSrcDir("java").resolve("LibFoo.kt").let {
            it.parentFile.mkdirs()
            it.writeText(
                """
                    package com.foo.library
                    class LibFoo {
                      internal fun bar() {}
                    }
                """.trimIndent()
            )
        }
        lib.executor().run(":lib:compileDebugScreenshotTestKotlin")
    }

    @Test
    fun testLowKotlinVersion() {
        TestFileUtils.searchAndReplace(
            project.projectDir.parentFile.resolve(VERSION_CATALOG),
            "version('kotlinVersion', '${TestUtils.KOTLIN_VERSION_FOR_TESTS}')",
            "version('kotlinVersion', '1.8.10')"
        )
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            "${BooleanOption.ENABLE_SCREENSHOT_TEST.propertyName}=true"
        )
        val lib = project.getSubproject(":lib")
        lib.buildFile.appendText(
            """
                android.experimentalProperties["${SCREENSHOT_TEST.key}"] = true

                dependencies {
                    screenshotTestImplementation("org.jetbrains.kotlin:kotlin-stdlib:${TestUtils.KOTLIN_VERSION_FOR_TESTS}")
                }
                """.trimIndent()
        )
        val result = lib.executor().expectFailure().run(":lib:compileDebugScreenshotTest")
        result.assertErrorContains(
            "The current Kotlin Gradle plugin version (1.8.10) is below the required"
        )
    }
}
