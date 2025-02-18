/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import org.junit.Rule
import org.junit.Test

class KotlinTestCompilationTest {
    @Rule
    @JvmField
    var project =
        builder().fromTestProject("kotlinApp")
            .create()

    /** Regression test for b/150500779. */
    @Test
    fun testAccessingInternalMembersForApp() {
        project.getSubproject("app").mainSrcDir.resolve("com/app/Data.kt").also {
            it.parentFile.mkdirs()
            it.writeText(
                """
                package com.app

                class Data {
                    internal fun printData() {}
                }
            """.trimIndent()
            )
        }
        project.getSubproject("app").file("src/androidTest/java").resolve("com/app/DataTest.kt")
            .also {
                it.parentFile.mkdirs()
                it.writeText(
                """
                package com.app

                class DataTest {
                    fun testPrintData() {
                        Data().printData()
                    }
                }
            """.trimIndent()
                )
            }
        project.getSubproject("app").file("src/test/java").resolve("com/app/DataUnitTest.kt")
            .also {
                it.parentFile.mkdirs()
                it.writeText(
                    """
                package com.app

                class DataUnitTest {
                    fun testPrintData() {
                        Data().printData()
                    }
                }
            """.trimIndent()
                )
            }
        project.executor().run("app:assembleDebugAndroidTest", "app:compileDebugUnitTestJavaWithJavac")
    }

    /** Regression test for b/150500779. */
    @Test
    fun testAccessingInternalMembersForLibrary() {
        project.getSubproject("library").mainSrcDir.resolve("com/app/Data.kt").also {
            it.parentFile.mkdirs()
            it.writeText(
                """
                package com.app

                class Data {
                    internal fun printData() {}
                }
            """.trimIndent()
            )
        }
        project.getSubproject("library").file("src/androidTest/java").resolve("com/app/DataTest.kt")
            .also {
                it.parentFile.mkdirs()
                it.writeText(
                    """
                package com.app

                class DataTest {
                    fun testPrintData() {
                        Data().printData()
                    }
                }
            """.trimIndent()
                )
            }
        project.getSubproject("library").file("src/test/java").resolve("com/app/DataUnitTest.kt")
            .also {
                it.parentFile.mkdirs()
                it.writeText(
                    """
                package com.app

                class DataUnitTest {
                    fun testPrintData() {
                        Data().printData()
                    }
                }
            """.trimIndent()
                )
            }
        project.executor().run("library:assembleDebugAndroidTest", "library:compileDebugUnitTestJavaWithJavac")
    }

    /**
     * Regression test for b/269585156.
     *
     * The fasterxml dependency cannot parse the file successfully. With the fix, adding it to the
     * classpath here should not trigger any exception.
     */
    @Test
    fun testXmlParserResourceCompilation() {
        project.buildFile.writeText(
            """
                apply from: "../commonHeader.gradle"

                buildscript {
                    apply from: "../commonHeader.gradle"  // for ${'$'}kotlinVersion
                    apply from: "../commonBuildScript.gradle"

                    dependencies {
                        classpath "com.fasterxml:aalto-xml:1.3.0"
                        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:${'$'}{libs.versions.kotlinVersion.get()}"
                    }
                }
            """.trimIndent()
        )
        project.executor().run("asDeb")
    }
}
