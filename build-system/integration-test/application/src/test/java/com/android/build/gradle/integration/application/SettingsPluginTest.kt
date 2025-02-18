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

import com.android.build.gradle.integration.common.fixture.DEFAULT_COMPILE_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.DEFAULT_MIN_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.options.StringOption
import com.android.builder.model.v2.ide.ApiVersion
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class SettingsPluginTest {

    @get:Rule
    var project = createGradleProject {
        settings {
            plugins.add(PluginType.ANDROID_SETTINGS)
        }
        subProject(":lib") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                namespace = "com.example.lib"
            }
        }
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld(setupDefaultCompileSdk = false)
            }
        }
    }

    data class Profile(
            val name: String,
            val r8JvmOptions: List<String>,
            val r8RunInSeparateProcess: Boolean)

    private val defaultProfiles: List<Profile> =
            listOf(
                    Profile("low", listOf("-Xms200m", "-Xmx200m"), false),
                    Profile("high", listOf("-Xms800m", "-Xmx800m"), true),
                    Profile("pizza",
                            listOf("-Xms801m", "-Xmx801m", "-XX:+HeapDumpOnOutOfMemoryError"),
                            false),
            )

    private fun addSettingsBlock(
            minSdk: Int = DEFAULT_MIN_SDK_VERSION,
            compileSdk: Int = DEFAULT_COMPILE_SDK_VERSION,
            targetSdk: Int = compileSdk,
            execProfile: String?,
            profiles: List<Profile> = defaultProfiles
    ) {
        var profileBlocks = ""
        val expandList = { it: List<String> ->
            if (it.isNotEmpty()) "\"${it.joinToString("\", \"")}\"" else ""
        }
        profiles.forEach {
            profileBlocks +=
                    """|
                |            ${it.name} {
                |                r8 {
                |                    jvmOptions = [${expandList(it.r8JvmOptions)}]
                |                    runInSeparateProcess ${it.r8RunInSeparateProcess}
                |                }
                |            }
                """.trimMargin("|")
        }
        project.settingsFile.appendText(
                """|
                |
                |android {
                |    compileSdk $compileSdk
                |    minSdk $minSdk
                |    targetSdk $targetSdk
                |    execution {
                |        profiles {
                |$profileBlocks
                |        }
                |        ${execProfile?.run { """defaultProfile "$execProfile"""" } ?: ""}
                |    }
                |}
            """.trimMargin("|")
        )
    }

    private fun withShrinker() {
        project.buildFile.appendText("android.buildTypes.debug.minifyEnabled true")
    }

    @Test
    fun testInvalidProfile() {
        withShrinker()
        addSettingsBlock(execProfile = "invalid")
        val result = project.executor().expectFailure().run("assembleDebug")

        result.stderr.use {
            ScannerSubject.assertThat(it).contains("Selected profile 'invalid' does not exist")
        }
    }

    @Test
    fun testProfileOverride() {
        withShrinker()
        // First try to build with invalid profile
        addSettingsBlock(execProfile = "invalid")
        var result = project.executor().expectFailure().run("assembleDebug")

        result.stderr.use {
            ScannerSubject.assertThat(it).contains("Selected profile 'invalid' does not exist")
        }

        // Make sure it builds when overriding the profile
        result =
                project.executor()
                        .with(StringOption.EXECUTION_PROFILE_SELECTION, "low")
                        .run("clean", "assembleDebug")

        result.stdout.use {
            ScannerSubject.assertThat(it)
                    .contains("Using execution profile from android.settings.executionProfile 'low'")
        }
    }

    @Test
    fun testProfileAutoSelection() {
        withShrinker()
        // Building with no profiles and no selection should go to default
        addSettingsBlock(execProfile = null, profiles = listOf())
        project.execute("assembleDebug")

        // Adding one profile should auto-select it, so it should also work
        project.settingsFile.appendText(
                """|
                |android.execution.profiles {
                |    profileOne {
                |        r8.jvmOptions = []
                |        r8.runInSeparateProcess false
                |    }
                |}
            """.trimMargin()
        )
        var result = project.executor().run("clean", "assembleDebug")

        result.stdout.use {
            ScannerSubject.assertThat(it).contains("Using only execution profile 'profileOne'")
        }

        // Adding another profile with no selection should fail
        project.settingsFile.appendText(
                """|
                |android.execution.profiles {
                |    profileTwo {
                |    }
                |}
            |""".trimMargin()
        )
        result = project.executor().expectFailure().run("clean", "assembleDebug")

        result.stderr.use {
            ScannerSubject.assertThat(it)
                    .contains("Found 2 execution profiles [profileOne, profileTwo], but no profile was selected.\n")
        }

        // Selecting a profile through override should work
        project.executor()
                .with(StringOption.EXECUTION_PROFILE_SELECTION, "profileOne")
                .run("clean", "assembleDebug")

        // So should adding the profile selection to the settings file
        project.settingsFile.appendText(
                """android.execution.defaultProfile "profileTwo" """
        )
        project.execute("clean", "assembleDebug")
    }

    // regression test for b/258704137
    @Test
    fun testJvmOptionsAreUsed() {
        addSettingsBlock(
                execProfile = "mid",
                profiles = listOf(
                        Profile("mid", listOf(":pizza/foo"), true)
                )
        )

        project.buildFile.appendText(
                """|
                |android.buildTypes {
                |    debug {
                |        minifyEnabled true
                |    }
                |}
            """.trimMargin()
        )

        val result = project.executor().expectFailure().run("clean", "minifyDebugWithR8")

        // If the jvm args used, r8 will be unable to create the separate process
        // with invalid arguments
        result.stderr.use {
            ScannerSubject.assertThat(it).contains("Error: Could not find or load main class :pizza.foo")
        }
    }

    @Test
    fun checkCompileMinAndTargetAreSet() {
        val compileSdk = DEFAULT_COMPILE_SDK_VERSION
        val targetSdk = DEFAULT_COMPILE_SDK_VERSION - 1
        val minSdk = DEFAULT_COMPILE_SDK_VERSION - 2
        addSettingsBlock(compileSdk = compileSdk,
                targetSdk = targetSdk,
                minSdk = minSdk,
                execProfile = null,
                profiles = listOf())

        // First check app
        val modelInfo = project.modelV2().fetchModels().container.getProject(":")
        val androidDsl = modelInfo.androidDsl ?: error("failed to fetch android DSL model")
        assertThat(androidDsl.compileTarget)
                .named("androidDsl.compileTarget")
                .isEqualTo("android-$compileSdk")
        assertThat(androidDsl.defaultConfig.targetSdkVersion?.apiLevel)
                .named("androidDsl.defaultConfig.targetSdkVersion.apiLevel")
                .isEqualTo(targetSdk)
        assertThat(androidDsl.lintOptions.targetSdk?.apiLevel)
            .named("androidDsl.lintOptions.targetSdk.apiLevel")
            .isEqualTo(null)
        val androidProject = modelInfo.androidProject ?: error("Failed to fetch android project")
        assertThat(androidProject.variants).isNotEmpty()
        for (variant in androidProject.variants) {
            assertThat(variant.mainArtifact.minSdkVersion.apiLevel)
                    .named("variant %s mainArtifact.minSdkVersion.apiLevel", variant.name)
                    .isEqualTo(minSdk)
        }

        // Then check library
        val libModelInfo = project.modelV2().fetchModels().container.getProject(":lib")
        val libAndroidDsl = libModelInfo.androidDsl ?: error("failed to fetch android DSL model")
        assertThat(libAndroidDsl.compileTarget)
            .named("libAndroidDsl.compileTarget")
            .isEqualTo("android-$compileSdk")
        assertThat(libAndroidDsl.defaultConfig.targetSdkVersion)
            .named("libAndroidDsl.defaultConfig.targetSdkVersion")
            .isEqualTo(null)
        assertThat(libAndroidDsl.lintOptions.targetSdk?.apiLevel)
            .named("libAndroidDsl.lintOptions.targetSdk.apiLevel")
            .isEqualTo(targetSdk)
        val libAndroidProject = modelInfo.androidProject ?: error("Failed to fetch android project")
        assertThat(libAndroidProject.variants).isNotEmpty()
        for (variant in libAndroidProject.variants) {
            assertThat(variant.mainArtifact.minSdkVersion.apiLevel)
                .named("variant %s mainArtifact.minSdkVersion.apiLevel", variant.name)
                .isEqualTo(minSdk)
        }
    }
}
