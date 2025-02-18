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

package com.android.build.gradle.integration.api

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldAppKts
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.utils.FileUtils.toSystemDependentPath
import org.junit.Rule
import org.junit.Test

class RegisterSourceTypeTest {
    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(HelloWorldAppKts.forPlugin("com.android.application"))
            .create()

    @Test
    fun testSourceSetsOutput() {
        project.ktsBuildFile.appendText(
            """
            androidComponents {
                registerSourceType("toml")
            }
            """.trimIndent()
        )

        val result = project.executor().run("sourceSets")
        assertThat(result.stdout).contains("Custom sources: [${toSystemDependentPath("src/androidTest/toml")}]")
        assertThat(result.stdout).contains("Custom sources: [${toSystemDependentPath("src/androidTestDebug/toml")}]")
        assertThat(result.stdout).contains("Custom sources: [${toSystemDependentPath("src/androidTestRelease/toml")}]")
        assertThat(result.stdout).contains("Custom sources: [${toSystemDependentPath("src/debug/toml")}]")
        assertThat(result.stdout).contains("Custom sources: [${toSystemDependentPath("src/release/toml")}]")
        assertThat(result.stdout).contains("Custom sources: [${toSystemDependentPath("src/main/toml")}]")
        assertThat(result.stdout).contains("Custom sources: [${toSystemDependentPath("src/test/toml")}]")
        assertThat(result.stdout).contains("Custom sources: [${toSystemDependentPath("src/testDebug/toml")}]")
        assertThat(result.stdout).contains("Custom sources: [${toSystemDependentPath("src/testFixtures/toml")}]")
        assertThat(result.stdout).contains("Custom sources: [${toSystemDependentPath("src/testFixturesDebug/toml")}]")
        assertThat(result.stdout).contains("Custom sources: [${toSystemDependentPath("src/testFixturesRelease/toml")}]")
        assertThat(result.stdout).contains("Custom sources: [${toSystemDependentPath("src/testRelease/toml")}]")
    }
}
