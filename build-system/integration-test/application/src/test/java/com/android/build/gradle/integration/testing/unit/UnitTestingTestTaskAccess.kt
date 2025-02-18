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

package com.android.build.gradle.integration.testing.unit

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class UnitTestingTestTaskAccess {
    @JvmField
    @Rule
    val project = GradleTestProject.builder().fromTestProject("unitTesting").create()

    @Test
    fun testTestTaskAccess() {
        project.buildFile.appendText(
            """
            androidComponents {
                onVariants(selector().all()) { variant ->
                    variant.unitTest?.configureTestTask { testTask ->
                        testTask.beforeTest { descriptor ->
                            println("Running test: " + descriptor)
                        }
                    }
                }
            }
            """.trimIndent()
        )
        var result = project.executor().run("testDebug")
        Truth.assertThat(result.stdout.findAll("Running test: *").count())
            .isGreaterThan(10)

    }
}
