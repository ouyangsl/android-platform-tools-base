/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.build.api.artifact.SingleArtifact
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.scope.getOutputDir
import com.android.build.gradle.options.BooleanOption
import com.android.builder.model.SyncIssue
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils

class NoManifestTest {

    val app = MinimalSubProject.app("com.example.app")
    val lib = MinimalSubProject.lib("com.example.lib")
    init {
        app.removeFileByName("AndroidManifest.xml")
        lib.removeFileByName("AndroidManifest.xml")
    }

    private val testAppAndLib =
            MultiModuleTestProject.builder().subproject(":app", app)
                    .subproject(":lib", lib).build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testAppAndLib).create()

    @Test
    fun noManifestConfigurationPassesTest() {
        project.executor().run("tasks")
        // we should be able to create task list, without a valid manifest.
    }

    // FIXME: Revise this once appId is removed from the model
    //@Test
    fun noManifestSyncNoApplicationIdTest() {
        val issues = getProjectSyncIssuesWithNoManifestParsing(":")

        Truth.assertThat(issues).named("full issues list").hasSize(2)

        val errors = issues.filter { it.severity == SyncIssue.SEVERITY_ERROR}
        Truth.assertThat(errors).named("error-only issues").hasSize(1)
        assertThat(errors.first().type).isEqualTo(SyncIssue.TYPE_GENERIC)

        val warnings = issues.filter { it.severity == SyncIssue.SEVERITY_WARNING }
        Truth.assertThat(errors).named("warning-only issues").hasSize(1)
        assertThat(warnings.first().type).isEqualTo(SyncIssue.TYPE_UNSUPPORTED_PROJECT_OPTION_USE)
    }

    @Test
    fun noManifestSyncWithApplicationIdsTest() {
        TestFileUtils.appendToFile(
            project.getSubproject(":app").buildFile, """
                android {
                    namespace "com.example.app"
                    defaultConfig {
                        applicationId "com.example.app"
                        testApplicationId "com.example.app.test"
                    }
                }
            """.trimIndent()
        )
        TestFileUtils.appendToFile(
                project.getSubproject(":lib").buildFile,
                """
                    android.namespace "com.example.lib"
                    """.trimIndent()
        )
        val issues = getProjectSyncIssuesWithNoManifestParsing(":app")

        Truth.assertThat(issues).named("full issues list").hasSize(1)
        val issue = issues.first()
        assertThat(issue.type).isEqualTo(SyncIssue.TYPE_UNSUPPORTED_PROJECT_OPTION_USE)
        assertThat(issue.severity).isEqualTo(SyncIssue.SEVERITY_WARNING)
    }

    @Test
    fun noManifestSyncWithNamespaceTest() {
        TestFileUtils.appendToFile(
                project.getSubproject(":app").buildFile,
                """
                    android.namespace "com.example.app"
                    """.trimIndent()
        )
        TestFileUtils.appendToFile(
                project.getSubproject(":lib").buildFile,
                """
                    android.namespace "com.example.lib"
                    """.trimIndent()
        )
        val issues = getProjectSyncIssuesWithNoManifestParsing(":app")

        Truth.assertThat(issues).named("full issues list").hasSize(1)
        val issue = issues.first()
        assertThat(issue.type).isEqualTo(SyncIssue.TYPE_UNSUPPORTED_PROJECT_OPTION_USE)
        assertThat(issue.severity).isEqualTo(SyncIssue.SEVERITY_WARNING)
    }

    @Test
    fun noManifestInLibraryModuleTest() {
        val warning = "package=\"com.example.lib\" found in source AndroidManifest.xml"
        TestFileUtils.appendToFile(
                project.getSubproject(":lib").buildFile,
                """
                    android.namespace "com.example.lib"
                    """.trimIndent()
        )
        val result = project.executor().run(":lib:build", ":lib:assembleAndroidTest")
        ScannerSubject.assertThat(result.stdout).doesNotContain(warning)

        assertThat(result.failedTasks).isEmpty()
        val fileOutput =
                FileUtils.join(
                        SingleArtifact.MERGED_MANIFEST.getOutputDir(
                                project.getSubproject(":lib").buildDir
                        ),
                        "debug",
                        "processDebugManifest",
                        "AndroidManifest.xml"
                )
        assertThat(fileOutput.isFile).isTrue()
        assertThat(fileOutput).contains("package=\"com.example.lib\"")

        // Check that we actually see the warning when we should.
        val manifestFile =
            FileUtils.join(
                project.getSubproject(":lib").projectDir, "src", "main", "AndroidManifest.xml"
            )
        manifestFile.parentFile.mkdirs()
        manifestFile.writeText(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.lib" />
            """.trimIndent()
        )
        val result2 = project.executor().run(":lib:build")
        ScannerSubject.assertThat(result2.stdout).contains(warning)
    }

    private fun getProjectSyncIssuesWithNoManifestParsing(projectPath: String) =
            project.modelV2()
            .with(BooleanOption.DISABLE_EARLY_MANIFEST_PARSING, true)
            .ignoreSyncIssues().fetchModels().container.getProject(projectPath).issues?.syncIssues!!
}
