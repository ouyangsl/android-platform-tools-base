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

package com.android.build.gradle.integration.dependencies.lib

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.model.v2.ide.SyncIssue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LibWithProvidedAarAsJarTest : ModelComparator() {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestProject("projectWithModules")
        .create()

    @Before
    fun setUp() {
        project.setIncludedProjects("library", "library2")

        TestFileUtils.appendToFile(
            project.getSubproject("library").buildFile,
            """
                dependencies {
                   compileOnly project(path: ":library2", configuration: "fakeJar")
                }
            """.trimIndent()
        )

        TestFileUtils.appendToFile(
            project.getSubproject("library2").buildFile,
            """
                configurations {
                    fakeJar
                }
                task makeFakeJar(type: Jar) {
                    from "src/main/java"
                }
                artifacts {
                    fakeJar makeFakeJar
                }
            """.trimIndent()
        )
    }

    @Test
    fun `test VariantDependencies model`() {
        val result =
            project.modelV2()
                .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
                .fetchModels(variantName = "debug")

        with(result).compareVariantDependencies(
            projectAction = { getProject(":library") }, goldenFile = "library_VariantDependencies"
        )
    }

    @Test
    fun `check project jar is not packaged`() {
        project.executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
            .run("clean", ":library:assembleDebug")
        project.getSubproject("library")
            .testAar(
                GradleTestProject.ApkType.DEBUG.buildType
            ) { aar ->
                aar.doesNotContainClass(
                    "Lcom/example/android/multiproject/library2/PersionView2;"
                )
            }
    }
}
