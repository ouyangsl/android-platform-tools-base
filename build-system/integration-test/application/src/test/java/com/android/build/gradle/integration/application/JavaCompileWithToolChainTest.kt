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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.OsType
import com.android.testutils.TestUtils
import org.junit.Rule
import org.junit.Test

class JavaCompileWithToolChainTest {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .withKotlinGradlePlugin(true)
        .create()

    @Test
    fun basicTest() {
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            """
                org.gradle.java.installations.auto-detect=false
                org.gradle.java.installations.paths=${jdk8LocationInGradleFile}
            """.trimIndent()
        )
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(8)
                    }
                }
            """.trimIndent()
        )

        var result = project.executor().withArgument("--info").run("assembleDebug")

        result.stdout.use {
            ScannerSubject.assertThat(it).contains(
                "Compiling with toolchain '${jdk8LocationFromStdout}'"
            )
        }

        TestFileUtils.searchAndReplace(
            project.gradlePropertiesFile,
            "org.gradle.java.installations.paths=${jdk8LocationInGradleFile}",
            "org.gradle.java.installations.paths=${latestJdkLocationInGradleFile}"
        )

        TestFileUtils.searchAndReplace(
            project.buildFile,
            "languageVersion = JavaLanguageVersion.of(8)",
            "languageVersion = JavaLanguageVersion.of($latestJdkVersion)"
        )

        result = project.executor().withArgument("--info").run("assembleDebug")
        result.stdout.use {
            ScannerSubject.assertThat(it).contains(
                "Compiling with toolchain '${latestJdkLocationFromStdout}'"
            )
        }
    }

    @Test
    fun `test source and target compatibility versions when toolchain is configured`() {
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            """
            org.gradle.java.installations.auto-detect=false
            org.gradle.java.installations.paths=${latestJdkLocationInGradleFile}
            """.trimIndent()
        )
        TestFileUtils.appendToFile(
            project.buildFile,
            """
            apply plugin: 'org.jetbrains.kotlin.android'

            java.toolchain.languageVersion = JavaLanguageVersion.of($latestJdkVersion)
            android.kotlinOptions.allWarningsAsErrors = true

            // Reading targetCompatibility early should fail
            try {
                println android.compileOptions.targetCompatibility
                throw new IllegalStateException("Exception was not thrown")
            } catch (Exception e) {
                if (e.message != "targetCompatibility is not yet finalized") {
                    throw new IllegalStateException("Exception message is not as expected: " + e.message)
                }
            }

            // Reading targetCompatibility after configuration should succeed
            afterEvaluate {
                def targetCompatibility = android.compileOptions.targetCompatibility
                if (targetCompatibility != JavaVersion.toVersion($latestJdkVersion)) {
                    throw new IllegalStateException("Unexpected targetCompatibility: " + targetCompatibility)
                }
            }

            """.trimIndent()
        )
        project.projectDir.resolve("src/main/kotlin/com/example/KotlinClass.kt").also {
            it.parentFile.mkdirs()
            it.writeText(
                """
                package com.example

                class KotlinClass {
                }
                """.trimIndent()
            )
        }

        // Compiling should not throw an error (regression test for bug 260059413)
        project.executor().run("compileDebugJavaWithJavac")

        project.modelV2().fetchModels(variantName = "debug").container.getProject().androidProject!!
            .javaCompileOptions.let {
                assertThat(it.sourceCompatibility).isEqualTo(latestJdkVersion.toString())
                assertThat(it.targetCompatibility).isEqualTo(latestJdkVersion.toString())
            }
    }

    @Test
    fun testDeprecatedSourceAndTarget() {
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            """
            org.gradle.java.installations.auto-detect=false
            org.gradle.java.installations.paths=${TestUtils.getJava21Jdk().toString().replace("\\", "/")}
            """.trimIndent()
        )
        TestFileUtils.appendToFile(
            project.buildFile,
            """
            android {
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_1_7
                    targetCompatibility = JavaVersion.VERSION_1_7
                }
            }
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(21)
                }
            }
            // Remove the default settings in GradleTestProject.computeGradleBuildscript
            tasks.withType(JavaCompile).configureEach {
                options.compilerArgs.remove("-Werror")
                javaCompiler = null
            }
            """.trimIndent()
        )
        // Run JavaCompile 21 targeting Java 7, expect failure
        val resultWithError = project.executor().expectFailure().run("compileDebugJavaWithJavac")
        resultWithError.assertErrorContains("Java compiler version 21 has removed support for compiling with source/target version 7.")

        // Run JavaCompile 21 targeting Java 8, expect a warning
        TestFileUtils.searchAndReplace(
            project.buildFile,
            "JavaVersion.VERSION_1_7",
            "JavaVersion.VERSION_1_8",
        )
        val resultWithWarning = project.executor().run("compileDebugJavaWithJavac")
        val warningMessage = "Java compiler version 21 has deprecated support for compiling with source/target version 8."
        resultWithWarning.assertOutputContains(warningMessage)

        // Run JavaCompile 21 targeting Java 8 again with the suppress-warning option, expect no warning
        val resultWithNoWarning = project.executor()
            .with(BooleanOption.JAVA_COMPILE_SUPPRESS_SOURCE_TARGET_DEPRECATION_WARNING, true)
            .run("compileDebugJavaWithJavac")
        resultWithNoWarning.assertOutputDoesNotContain(warningMessage)
    }

    companion object {
        private val jdk8Location = TestUtils.getJava8Jdk().toString()

        private val latestJdkVersion = Runtime.version().feature()
        private val latestJdkLocation =
            when(latestJdkVersion) {
                17 -> TestUtils.getJava17Jdk()
                21 -> TestUtils.getJava21Jdk()
                else -> throw Exception(
                    "Finding the jdk path of jdk $latestJdkVersion is not supported")
            }.toString()

        val jdk8LocationInGradleFile = jdk8Location.replace("\\", "/")
        val latestJdkLocationInGradleFile = latestJdkLocation.replace("\\", "/")

        val jdk8LocationFromStdout = if (OsType.getHostOs() == OsType.WINDOWS) {
            jdk8Location.replace("/", "\\")
        } else {
            jdk8Location
        }

        val latestJdkLocationFromStdout = if (OsType.getHostOs() == OsType.WINDOWS) {
            latestJdkLocation.replace("/", "\\")
        } else {
            latestJdkLocation
        }
    }
}
