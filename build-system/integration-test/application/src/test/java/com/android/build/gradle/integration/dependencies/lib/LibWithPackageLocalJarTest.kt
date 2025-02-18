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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.model.v2.ide.SyncIssue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LibWithPackageLocalJarTest : ModelComparator() {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestProject("projectWithLocalDeps")
        .create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                apply plugin: "com.android.library"
                android {
                    namespace 'com.android.tests.libWithProvidedLocalJar'
                    compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
                    buildToolsVersion "${GradleTestProject.DEFAULT_BUILD_TOOL_VERSION}"
                }
                dependencies {
                    runtimeOnly files("libs/util-1.0.jar")
                }
            """.trimIndent()
        )
    }

    @Test
    fun `test VariantDependencies model`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug")

        with(result).compareVariantDependencies(goldenFile = "VariantDependencies")
    }

    @Test
    fun `check provided local jar is packaged`() {
        project.execute("clean", "assembleDebug")
        project.testAar(
            GradleTestProject.ApkType.DEBUG.buildType
        ) { aar -> aar.containsSecondaryClass("Lcom/example/android/multiproject/person/People;") }
    }
}
