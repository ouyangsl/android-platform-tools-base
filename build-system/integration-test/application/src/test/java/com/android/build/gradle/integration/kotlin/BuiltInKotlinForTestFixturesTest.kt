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

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.VERSION_CATALOG
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProjectBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.TestUtils
import com.android.testutils.apk.Aar
import org.junit.Rule
import org.junit.Test

class BuiltInKotlinForTestFixturesTest {

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
        enableTestFixturesKotlinSupport()
        val lib = project.getSubproject(":lib")
        TestFileUtils.appendToFile(
            lib.buildFile,
            """
                dependencies {
                    testFixturesImplementation("androidx.compose.ui:ui-tooling-preview:1.6.5")
                    testFixturesImplementation(project(":lib2"))
                }
                """.trimIndent()
        )
        lib.file("src/testFixtures/kotlin/LibTestFixtureFoo.kt").let {
            it.parentFile.mkdirs()
            it.writeText(
                """
                    package com.foo.library

                    import com.foo.library.two.LibTwoClass
                    import androidx.compose.ui.tooling.preview.Preview

                    class LibTestFixtureFoo
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
            .run(":lib:assembleDebugTestFixtures")
        val aar = lib.outputDir.resolve("aar").listFiles().single()
        Aar(aar).use {
            assertThat(it).containsMainClass("Lcom/foo/library/LibTestFixtureFoo;")
        }
    }

    @Test
    fun testInternalModifierAccessible() {
        enableTestFixturesKotlinSupport()
        val lib = project.getSubproject(":lib")
        lib.file("src/testFixtures/kotlin/LibTestFixtureFoo.kt").let {
            it.parentFile.mkdirs()
            it.writeText(
                """
                    package com.foo.library
                    class LibTestFixtureFoo {
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
        lib.executor().run(":lib:assembleDebugTestFixtures")
    }

    @Test
    fun testLowKotlinVersion() {
        enableTestFixturesKotlinSupport()
        TestFileUtils.searchAndReplace(
            project.projectDir.parentFile.resolve(VERSION_CATALOG),
            "version('kotlinVersion', '${TestUtils.KOTLIN_VERSION_FOR_TESTS}')",
            "version('kotlinVersion', '1.8.10')"
        )
        val lib = project.getSubproject(":lib")
        val result = lib.executor().expectFailure().run(":lib:assembleDebugTestFixtures")
        result.assertErrorContains(
            "The current Kotlin Gradle plugin version (1.8.10) is below the required"
        )
    }

    @Test
    fun testLowKotlinVersionWithNoBuiltInKotlinSupport() {
        TestFileUtils.searchAndReplace(
            project.projectDir.parentFile.resolve(VERSION_CATALOG),
            "version('kotlinVersion', '${TestUtils.KOTLIN_VERSION_FOR_TESTS}')",
            "version('kotlinVersion', '1.8.10')"
        )
        val lib = project.getSubproject(":lib")
        lib.buildFile.appendText(
            """
                android.testFixtures.enable = true
                """.trimIndent()
        )
        // We expect no build failure in this case.
        // Set failOnWarning to false because Gradle warns about deprecated feature(s) used by KGP 1.8.10.
        lib.executor().withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
            .withFailOnWarning(false).run(":lib:assembleDebugTestFixtures")
    }

    // Regression test for b/364331837
    @Test
    fun testJvmTarget() {
        enableTestFixturesKotlinSupport()
        val lib = project.getSubproject(":lib")
        // Add a simple kotlin source file so that kotlin compilation task does work.
        lib.file("src/testFixtures/kotlin/LibTestFixtureFoo.kt").let {
            it.parentFile.mkdirs()
            it.writeText(
                """
                    package com.foo.library
                    class LibTestFixtureFoo {}
                    """.trimIndent()
            )
        }

        // First check setting jvmTarget via the compilerOptions DSL
        TestFileUtils.searchAndReplace(
            lib.buildFile,
            "jvmToolchain(17)",
             "compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)"
        )
        TestFileUtils.appendToFile(
            lib.buildFile,
            // language=groovy
            """
               afterEvaluate {
                    tasks.named("compileDebugTestFixturesKotlin") {
                        doLast {
                            def jvmTarget = it.compilerOptions.jvmTarget.get().target
                            println("My jvmTarget: " + jvmTarget)
                        }
                    }
               }
            """.trimIndent()
        )
        ScannerSubject.assertThat(
            lib.executor().run(":lib:compileDebugTestFixturesKotlin").stdout
        ).contains("My jvmTarget: 17")

        // Then check that setting jvmTarget on the task overrides the compilerOptions DSL
        TestFileUtils.appendToFile(
            lib.buildFile,
            // language=groovy
            """
                tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile.class).configureEach {
                    compilerOptions {
                        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
                    }
                }
                """.trimIndent()
        )

        ScannerSubject.assertThat(
            lib.executor().run(":lib:compileDebugTestFixturesKotlin").stdout
        ).contains("My jvmTarget: 21")
    }


    private fun enableTestFixturesKotlinSupport() {
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            "${BooleanOption.ENABLE_TEST_FIXTURES_KOTLIN_SUPPORT.propertyName}=true"
        )
        val lib = project.getSubproject(":lib")
        TestFileUtils.appendToFile(
            lib.buildFile,
            """
                android.testFixtures.enable = true

                dependencies {
                    testFixturesImplementation("org.jetbrains.kotlin:kotlin-stdlib:${TestUtils.KOTLIN_VERSION_FOR_TESTS}")
                }
                """.trimIndent()
        )
    }
}
