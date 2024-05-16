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

package com.android.build.gradle.integration.dependencies.app

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.model.v2.ide.SyncIssue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AppWithResolutionStrategyForAarTest : ModelComparator() {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestProject("projectWithModules")
        .create()

    @Before
    fun setUp() {
        project.setIncludedProjects("app", "library")
        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile,
            """
                dependencies {
                    debugImplementation project(":library")
                    releaseImplementation project(":library")
                }
                android.applicationVariants.all { variant ->
                  if (variant.buildType.name == "debug") {
                    variant.getCompileConfiguration().resolutionStrategy {
                      eachDependency { DependencyResolveDetails details ->
                        if (details.requested.name == "jdeferred-android-aar") {
                          details.useVersion "1.2.2"
                        }
                      }
                    }
                    variant.getRuntimeConfiguration().resolutionStrategy {
                      eachDependency { DependencyResolveDetails details ->
                        if (details.requested.name == "jdeferred-android-aar") {
                          details.useVersion "1.2.2"
                        }
                      }
                    }
                  }
                }
            """.trimIndent())

        TestFileUtils.appendToFile(
            project.getSubproject("library").buildFile,
            """
                dependencies {
                    api "org.jdeferred:jdeferred-android-aar:1.2.3"
                }
            """.trimIndent())
    }

    @Test
    fun `test debug VariantDependencies model`() {
        val result =
            project.modelV2()
                .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
                .fetchModels(variantName = "debug")

        with(result).compareVariantDependencies(
            projectAction = { getProject(":app") }, goldenFile = "app_debugVariantDependencies"
        )
    }

    @Test
    fun `test release VariantDependencies model`() {
        val result =
            project.modelV2()
                .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
                .fetchModels(variantName = "release")

        with(result).compareVariantDependencies(
            projectAction = { getProject(":app") }, goldenFile = "app_releaseVariantDependencies"
        )
    }
}
