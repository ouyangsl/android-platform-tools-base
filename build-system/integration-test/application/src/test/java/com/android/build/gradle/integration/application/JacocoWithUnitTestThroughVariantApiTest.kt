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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class JacocoWithUnitTestThroughVariantApiTest {
    @get:Rule
    val testProject = GradleTestProjectBuilder()
        .fromTestProject("unitTesting")
        .create()

    @Before
    fun setup() {
        // Make sure you can turn on code coverage though the variant API.
        TestFileUtils.appendToFile(
            testProject.buildFile,
            """
            androidComponents {
                beforeVariants(selector().withBuildType("debug")) {
                    it.hostTests.get(
                        com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE
                    ).enableCodeCoverage = true
                }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `test expected report contents`() {
        testProject.executor().run("createDebugUnitTestCoverageReport")
        val generatedCoverageReport = FileUtils.join(
            testProject.buildDir,
            "reports",
            "coverage",
            "test",
            "debug",
            "index.html"
        )
        Truth.assertThat(generatedCoverageReport.exists()).isTrue()
    }
}
