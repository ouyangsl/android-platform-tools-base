/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.integration.lint

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

class AndroidLintAnalysisTaskTest {

    @get:Rule
    val lintKotlinProject: GradleTestProject =
        GradleTestProject.builder()
            .fromTestProject("lintKotlin")
            .addGradleProperties("${BooleanOption.USE_ANDROID_X.propertyName}=true")
            .create()

    @Test
    fun testApp() {
        // Run twice to catch issues with configuration caching
        lintKotlinProject.execute(":app:clean", ":app:lintAnalyzeDebug")
        lintKotlinProject.execute(":app:clean", ":app:lintAnalyzeDebug")
        lintKotlinProject.buildResult.assertConfigurationCacheHit()
        val partialResultsDir =
            FileUtils.join(
                lintKotlinProject.getSubproject(":app")
                    .getIntermediateFile(InternalArtifactType.LINT_PARTIAL_RESULTS.getFolderName()),
                "debug",
                "lintAnalyzeDebug",
                "out"
            )
        assertThat(partialResultsDir.listFiles()?.asList())
            .containsAtLeastElementsIn(
                listOf(
                    File(partialResultsDir, "lint-definite.xml"),
                    File(partialResultsDir, "lint-issues.xml"),
                    File(partialResultsDir, "lint-partial.xml")
                )
            )
        assertThat(File(partialResultsDir, "lint-definite.xml"))
            .contains("{:app*debug*MAIN*sourceProvider*0*javaDir*2}")
    }

    @Test
    fun testAndroidLibrary() {
        // Run twice to catch issues with configuration caching
        lintKotlinProject.execute(":library:clean", ":library:lintAnalyzeDebug")
        lintKotlinProject.execute(":library:clean", ":library:lintAnalyzeDebug")
        lintKotlinProject.buildResult.assertConfigurationCacheHit()
        val partialResultsDir =
            FileUtils.join(
                lintKotlinProject.getSubproject(":library")
                    .getIntermediateFile(InternalArtifactType.LINT_PARTIAL_RESULTS.getFolderName()),
                "debug",
                "lintAnalyzeDebug",
                "out"
            )
        assertThat(partialResultsDir.listFiles()?.asList())
            .containsAtLeastElementsIn(
                listOf(
                    File(partialResultsDir, "lint-definite.xml"),
                    File(partialResultsDir, "lint-partial.xml")
                )
            )
        assertThat(File(partialResultsDir, "lint-definite.xml"))
            .contains("{:library*debug*MAIN*sourceProvider*0*resDir*0}")
    }
}
