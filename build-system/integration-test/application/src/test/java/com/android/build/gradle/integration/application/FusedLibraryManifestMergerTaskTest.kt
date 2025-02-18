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
import com.android.build.gradle.integration.common.fixture.TemporaryProjectModification
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.internal.manifest.parseManifest
import com.android.build.gradle.options.BooleanOption
import com.android.builder.errors.EvalIssueException
import com.android.builder.errors.IssueReporter
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.generateAarWithContent
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.charset.Charset

/* Tests for [FusedLibraryManifestMergerTask] */
internal class FusedLibraryManifestMergerTaskTest {

    private val testAar = generateAarWithContent("com.externaldep.externalaar",
            resources = mapOf("values/strings.xml" to
                    // language=XML
                    """<?xml version="1.0" encoding="utf-8"?>
                    <resources>
                    <string name="string_from_external_lib">Remote String</string>
                    <string name='external_permission_label'>External label</string>
                    <string name='external_permission_description'>External description</string>
                    </resources>""".trimIndent().toByteArray(Charset.defaultCharset())
            ),
            manifest =
            // language=XML
            """<?xml version="1.0" encoding="utf-8"?>
                <manifest
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.externaldep.externalaar">

                <permission
                  android:name="com.externaldep.permission.REMOTE_PERMISSION"
                  android:label="@string/external_permission_label"
                  android:description="@string/external_permission_label" />
                </manifest>
            """.trimIndent()
    )

    @JvmField
    @Rule
    val project = createGradleProject {
        // Library dependency at depth 1 with no dependencies.
        subProject(":androidLib1") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                defaultCompileSdk()
                namespace = "com.example.androidLib1"
                minSdk = 12
            }
            addFile("src/main/AndroidManifest.xml",
                    """<?xml version="1.0" encoding="utf-8"?>
                        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                            <uses-permission android:name="android.permission.SEND_SMS"/>
                        </manifest>"""
            )
        }
        // Library dependency at depth 0 with a dependency on androidLib1.
        subProject(":androidLib2") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                defaultCompileSdk()
                namespace = "com.example.androidLib2"
                minSdk = 19
            }
            dependencies {
                implementation(project(":androidLib1"))
            }
        }
        // Library dependency at depth 0 with no dependencies
        subProject(":androidLib3") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                defaultCompileSdk()
                namespace = "com.example.androidLib3"
                minSdk = 18
            }
            dependencies {
                implementation(project(":androidLib1"))
            }
        }
        subProject(":fusedLib1") {
            plugins.add(PluginType.FUSED_LIBRARY)
            androidFusedLibrary {
                namespace = "com.example.fusedLib1"
                minSdk = 19
            }
            dependencies {
                include(project(":androidLib3"))
                include(project(":androidLib2"))
                include(MavenRepoGenerator.Library("com.externaldep:externalaar:1", "aar", testAar))
            }
        }
        subProject(":app") {
            plugins.add(PluginType.ANDROID_APP)
            android {
                defaultCompileSdk()
                namespace = "com.example.app"
                minSdk = 19
            }
            // Add a dependency on the fused library aar in the test if needed.
        }
        gradleProperties {
            set(BooleanOption.FUSED_LIBRARY_SUPPORT, true)
        }
    }

    @Test
    @Ignore("b/236828934")
    fun checkFusedLibraryManifest() {
        val fusedLib1Project = project.getSubproject("fusedLib1")
        project.execute(":fusedLib1:mergeManifest")

        val mergedManifestFile = fusedLib1Project.getIntermediateFile(
                FusedLibraryInternalArtifactType.MERGED_MANIFEST.getFolderName(),
                "single",
                "mergeManifest",
                "AndroidManifest.xml"
        )
        val parsedManifestFile =
                parseManifest(
                    manifestFileContent = mergedManifestFile.readText(),
                    manifestFilePath = mergedManifestFile.absolutePath,
                    manifestFileRequired = true,
                    issueReporter = manifestIssueDataReporter
                )

        assertThat(parsedManifestFile.packageName).isEqualTo("com.example.fusedLib1")
        assertThat(parsedManifestFile.minSdkVersion?.apiLevel).isEqualTo(19)
        assertThat(parsedManifestFile.targetSdkVersion).isNull()
        // Permission merged transitively androidLib1 -> androidLib2 -> fusedLib1
        val mergedManifestContents = mergedManifestFile.readText()
        assertThat(mergedManifestContents)
                .contains("<uses-permission android:name=\"android.permission.SEND_SMS\" />")
        checkManifestBlameLogIsCreated(fusedLib1Project)
        assertThat(mergedManifestContents)
                .contains("    <permission\n" +
                        "        android:name=\"com.externaldep.permission.REMOTE_PERMISSION\"\n" +
                        "        android:description=\"@string/external_permission_label\"\n" +
                        "        android:label=\"@string/external_permission_label\" />")
    }

    @Test
    fun failWhenLibraryMinSdkVersionConflictWithFusedLibrary() {
        val androidLib3 = project.getSubproject("androidLib3")
        TemporaryProjectModification.doTest(
                androidLib3
        ) {
            it.replaceInFile(
                    androidLib3.buildFile.toRelativeString(androidLib3.projectDir),
                    "minSdk = 18", "minSdk = 20")
            val result = project.executor().expectFailure().run(":fusedLib1:mergeManifest")
            result.stderr.use { scanner ->
                assertThat(scanner)
                        .contains(
                                "uses-sdk:minSdkVersion 19 cannot be smaller than version 20 declared in library [:androidLib3]")
            }
        }
    }

    @Test
    fun failWhenFusedLibraryMinSdkVersionConflictWithApp() {
        val fusedLibBuildFile = project.getSubproject("fusedLib1").buildFile
        fusedLibBuildFile.readText().replace("minSdk = 19", "minSdk = 20").also {
            FileUtils.writeToFile(fusedLibBuildFile, it)
        }
        val publishedFusedLibrary = getFusedLibraryAar()
        project.getSubproject("app").buildFile.appendText(
                "dependencies {" +
                        "implementation(files(\'${publishedFusedLibrary.invariantSeparatorsPath}\'))" +
                        "}"
        )
        val result = project.executor().expectFailure().run(":app:processDebugMainManifest")
        result.stderr.use { scanner ->
            assertThat(scanner).contains(
                    "uses-sdk:minSdkVersion 19 cannot be smaller than version 20 declared in library [bundle.aar]"
            )
        }
    }

    @Test
    fun testAppManifestMergesFusedLibraryManifest() {
        project.executor().run(":app:assembleDebug")
        val mergedManifest =
                project.getSubproject("app")
                        .getIntermediateFile("merged_manifest", "debug", "processDebugMainManifest", "AndroidManifest.xml")
        val parsedManifest =
                parseManifest(
                    manifestFileContent = mergedManifest.readText(),
                    manifestFilePath = mergedManifest.absolutePath,
                    manifestFileRequired = true,
                    issueReporter = manifestIssueDataReporter
                )
        assertThat(parsedManifest.minSdkVersion?.apiLevel).isEqualTo(19)
        assertThat(parsedManifest.packageName).isEqualTo("com.example.app")
        assertThat(parsedManifest.targetSdkVersion?.apiLevel).isEqualTo(19)
    }

    private fun checkManifestBlameLogIsCreated(builtFusedLibraryProject: GradleTestProject) {
        val manifestBlameFile = builtFusedLibraryProject.getOutputFile(
                "logs", "manifest-merger-mergeManifest-report.txt")
        assertThat(manifestBlameFile).isNotNull()
        assertThat(manifestBlameFile.length()).isGreaterThan(0)
    }

    private fun getFusedLibraryAar(): File {
        project.getSubproject("fusedLib1").executor().run(":fusedLib1:bundle")
        return FileUtils.join(project.getSubproject("fusedLib1").buildDir, "bundle", "bundle.aar")
    }
}

private val manifestIssueDataReporter: IssueReporter = object : IssueReporter() {
    override fun reportIssue(
            type: Type,
            severity: Severity,
            exception: EvalIssueException) {
        if (severity === Severity.ERROR) {
            throw exception
        }
    }

    override fun hasIssue(type: Type): Boolean {
        return false
    }
}
