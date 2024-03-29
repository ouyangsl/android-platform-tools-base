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

package com.android.build.gradle.integration.connected.application

import com.android.SdkConstants
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.connected.utils.getEmulator
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.ide.common.build.GenericBuiltArtifactsLoader
import com.android.testutils.truth.PathSubject
import com.android.testutils.truth.ZipFileSubject
import com.android.utils.FileUtils
import com.android.utils.NullLogger
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File

class InstallProfilesPerDeviceApiConnectedTest {
    companion object {
        @JvmField @ClassRule
        val emulator = getEmulator()
    }

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder().fromTestProject("basic").create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(project.buildFile,
            """
                android {
                    defaultConfig {
                        minSdkVersion = 27
                        targetSdkVersion = 28
                    }

                    signingConfigs {
                        myConfig {
                            storeFile file("debug.keystore")
                            storePassword "android"
                            keyAlias "androiddebugkey"
                            keyPassword "android"
                        }
                    }

                    buildTypes {
                        release {
                            signingConfig signingConfigs.myConfig
                        }
                    }
                }
            """.trimIndent()
        )

        val mainBaselineProfileFileContent =
            """
                HSPLcom/google/Foo;->mainMethod(II)I
                HSPLcom/google/Foo;->mainMethod-name-with-hyphens(II)I
            """.trimIndent()

        val releaseBaselineProfileFileContent =
            """
                HSPLcom/google/Foo;->releaseMethod(II)I
                HSPLcom/google/Foo;->releaseMethod-name-with-hyphens(II)I
            """.trimIndent()

        FileUtils.createFile(
            project.file("src/main/baselineProfiles/file.txt"),
            mainBaselineProfileFileContent
        )
        FileUtils.createFile(
            project.file("src/release/baselineProfiles/file.txt"),
            releaseBaselineProfileFileContent
        )
    }

    @Test
    fun `install base baseline profile`() {
        project.execute("assembleRelease", "installRelease")

        val dexMetadataProperties = FileUtils.join(
            project.buildDir,
            SdkConstants.FD_INTERMEDIATES,
            InternalArtifactType.DEX_METADATA_DIRECTORY.getFolderName(),
            "release",
            "compileReleaseArtProfile",
            SdkConstants.FN_DEX_METADATA_PROP
        )

        Truth.assertThat(dexMetadataProperties.readText()).isEqualTo(
            """
                31=0/.dm
                2147483647=0/.dm
                28=1/.dm
                29=1/.dm
                30=1/.dm
            """.trimIndent()
        )

        ScannerSubject.assertThat(project.buildResult.stdout)
            .contains("Installing APK 'basic-release.apk, basic-release.dm'")

        // Validate that renamed baseline profile file is present
        val renamedBaselineProfile = FileUtils.join(
            project.buildDir,
            SdkConstants.FD_OUTPUTS,
            SdkConstants.EXT_ANDROID_PACKAGE,
            "release",
            SdkConstants.FN_OUTPUT_BASELINE_PROFILES,
            "0",
            "basic-release.dm"
        )
        Truth.assertThat(renamedBaselineProfile.exists()).isTrue()

        // Validate that baseline profile is in app metadata file
        val appMetadataJson = FileUtils.join(
            project.buildDir,
            SdkConstants.FD_OUTPUTS,
            SdkConstants.EXT_ANDROID_PACKAGE,
            "release",
            BuiltArtifactsImpl.METADATA_FILE_NAME
        )
        Truth.assertThat(appMetadataJson.readText())
            .contains(SdkConstants.FN_OUTPUT_BASELINE_PROFILES)
        Truth.assertThat(appMetadataJson.readText()).contains("basic-release.dm")

        val builtArtifacts = GenericBuiltArtifactsLoader.loadFromFile(appMetadataJson, NullLogger())
        val baselineProfileFile =
            builtArtifacts?.baselineProfiles?.lastOrNull()?.baselineProfiles?.firstOrNull()
        Truth.assertThat(baselineProfileFile).isEqualTo(renamedBaselineProfile)
    }

    @Test
    fun `install baseline profile with splits`() {
        TestFileUtils.appendToFile(project.buildFile,
            """
                android.splits {
                    abi {
                      enable true
                      reset()
                      include "x86", "x86_64"
                      universalApk false
                    }
                }
            """.trimIndent()
        )

        project.execute("assembleRelease", "installRelease")

        val dexMetadataProperties = FileUtils.join(
            project.buildDir,
            SdkConstants.FD_INTERMEDIATES,
            InternalArtifactType.DEX_METADATA_DIRECTORY.getFolderName(),
            "release",
            "compileReleaseArtProfile",
            SdkConstants.FN_DEX_METADATA_PROP
        )

        Truth.assertThat(dexMetadataProperties.readText()).isEqualTo(
                """
                31=0/.dm
                2147483647=0/.dm
                28=1/.dm
                29=1/.dm
                30=1/.dm
            """.trimIndent()
        )

        ScannerSubject.assertThat(project.buildResult.stdout)
            .contains("Installing APK 'basic-x86_64-release.apk, basic-x86_64-release.dm'")

        // Validate that renamed baseline profile file is present
        val renamedBaselineProfile = FileUtils.join(
            project.buildDir,
            SdkConstants.FD_OUTPUTS,
            SdkConstants.EXT_ANDROID_PACKAGE,
            "release",
            SdkConstants.FN_OUTPUT_BASELINE_PROFILES,
            "0",
            "basic-x86_64-release.dm"
        )
        Truth.assertThat(renamedBaselineProfile.exists()).isTrue()

        // Validate that baseline profile is in app metadata file
        val appMetadataJson = FileUtils.join(
            project.buildDir,
            SdkConstants.FD_OUTPUTS,
            SdkConstants.EXT_ANDROID_PACKAGE,
            "release",
            BuiltArtifactsImpl.METADATA_FILE_NAME
        )
        Truth.assertThat(appMetadataJson.readText())
            .contains(SdkConstants.FN_OUTPUT_BASELINE_PROFILES)
        Truth.assertThat(appMetadataJson.readText()).contains("basic-x86-release.dm")
        Truth.assertThat(appMetadataJson.readText()).contains("basic-x86_64-release.dm")

        val builtArtifacts = GenericBuiltArtifactsLoader.loadFromFile(appMetadataJson, NullLogger())
        val baselineProfileFile =
            builtArtifacts?.baselineProfiles?.lastOrNull()?.baselineProfiles?.firstOrNull()
        Truth.assertThat(baselineProfileFile).isEqualTo(renamedBaselineProfile)
    }

    @Test
    fun validateOptOut() {
        val oldBaselineProfileFileContent =
            """
                HSPLcom/google/Foo;->mainMethod(II)I
                HSPLcom/google/Foo;->mainMethod-name-with-hyphens(II)I
            """.trimIndent()
        FileUtils.createFile(
            project.file("src/main/baseline-prof.txt"),
            oldBaselineProfileFileContent
        )
        project.execute("assembleRelease")
        val dexMetadataProperties = FileUtils.join(
            project.buildDir,
            SdkConstants.FD_INTERMEDIATES,
            InternalArtifactType.DEX_METADATA_DIRECTORY.getFolderName(),
            "release",
            "compileReleaseArtProfile",
            SdkConstants.FN_DEX_METADATA_PROP
        )
        Truth.assertThat(dexMetadataProperties.exists()).isTrue()

        TestFileUtils.appendToFile(project.buildFile,
            """
                android.installation {
                    enableBaselineProfile true
                }
            """.trimIndent()
        )
        project.execute("clean")
        project.execute("assembleRelease")
        Truth.assertThat(dexMetadataProperties.exists()).isTrue()

        TestFileUtils.searchAndReplace(project.buildFile,
            "enableBaselineProfile true",
            "enableBaselineProfile false"
        )
        project.execute("clean")
        project.execute("assembleRelease")
        Truth.assertThat(dexMetadataProperties.exists()).isFalse()
    }

    @Test
    fun validateConfigurationCacheUsed() {
        // Run twice to verify configuration cache compatibility
        project.executor().run("clean", "assembleRelease")
        val result = project.executor().run("clean", "assembleRelease")
        ScannerSubject.assertThat(result.stdout).contains("Configuration cache entry reused.")

        // Validate that renamed baseline profile file is present
        val renamedBaselineProfile = FileUtils.join(
            project.buildDir,
            SdkConstants.FD_OUTPUTS,
            SdkConstants.EXT_ANDROID_PACKAGE,
            "release",
            SdkConstants.FN_OUTPUT_BASELINE_PROFILES,
            "0",
            "basic-release.dm"
        )
        Truth.assertThat(renamedBaselineProfile.exists()).isTrue()

        // Validate that baseline profile is in app metadata file
        val appMetadataJson = FileUtils.join(
            project.buildDir,
            SdkConstants.FD_OUTPUTS,
            SdkConstants.EXT_ANDROID_PACKAGE,
            "release",
            BuiltArtifactsImpl.METADATA_FILE_NAME
        )
        Truth.assertThat(appMetadataJson.readText())
            .contains(SdkConstants.FN_OUTPUT_BASELINE_PROFILES)
        Truth.assertThat(appMetadataJson.readText()).contains("basic-release.dm")
    }

    // Regression test for b/330593433
    @Test
    fun apkZipPackagingTest() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                apply plugin: 'maven-publish'
                publishing {
                    repositories {
                        maven { url 'testrepo' }
                    }
                }
                android {
                    publishing {
                        singleVariant('release') { publishApk() }
                    }
                }
                afterEvaluate {
                    publishing {
                        publications {
                            app(MavenPublication) {
                                groupId = 'test.basic'
                                artifactId = 'app'
                                version = '1.0'

                                from components.release
                            }
                        }
                    }
                }
            """.trimIndent()
        )
        project.execute("publishAppPublicationToMavenRepository")
        val testRepo = File(project.projectDir, "testrepo")
        val groupIdFolder = FileUtils.join(testRepo, "test", "basic")

        val apkFile = FileUtils.join(groupIdFolder, "app", "1.0", "app-1.0.zip")
        PathSubject.assertThat(apkFile).isFile()

        ZipFileSubject.assertThat(
            apkFile
        ) { it: ZipFileSubject ->
            it.contains(SdkConstants.FN_OUTPUT_BASELINE_PROFILES)
        }
    }

    // This test is disabled and should only be run locally with an API level lower than 28
    //@Test
    fun apiLevelNotSupportedForBaselineProfile() {
        val oldBaselineProfileFileContent =
            """
                HSPLcom/google/Foo;->mainMethod(II)I
                HSPLcom/google/Foo;->mainMethod-name-with-hyphens(II)I
            """.trimIndent()
        FileUtils.createFile(
            project.file("src/main/baseline-prof.txt"),
            oldBaselineProfileFileContent
        )
        project.executor().run("assembleRelease", "installRelease")

        ScannerSubject.assertThat(project.buildResult.stdout).contains(
            "Baseline Profile not found for API level "
        )
    }
}
