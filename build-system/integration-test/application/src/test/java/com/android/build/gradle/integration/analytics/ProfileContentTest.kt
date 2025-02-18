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

package com.android.build.gradle.integration.analytics

import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_MIN_SDK
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.ProfileCapturer
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp
import com.android.Version
import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.testutils.TestUtils
import com.google.common.collect.Iterables
import com.google.wireless.android.sdk.stats.GradleBuildProject
import com.google.wireless.android.sdk.stats.GradleBuildProject.GradlePlugin.ORG_JETBRAINS_KOTLIN_GRADLE_PLUGIN_KOTLINANDROIDPLUGINWRAPPER
import com.google.wireless.android.sdk.stats.GradleBuildProject.GradlePlugin.COM_ANDROID_BUILD_GRADLE_APPPLUGIN
import java.util.HashSet
import org.junit.Rule
import org.junit.Test
import java.util.regex.Pattern

/**
 * This test exists to make sure that the profiles we get back from the Android Gradle Plugin meet
 * the expectations we have for them in our benchmarking infrastructure.
 */
class ProfileContentTest {
    @get:Rule
    var project = GradleTestProject.builder()
            .fromTestApp(KotlinHelloWorldApp.forPlugin("com.android.application"))
            .enableProfileOutput()
            .create()

    @Test
    fun testProfileProtoContentMakesSense() {
        val capturer = ProfileCapturer(project)

        val getModel = Iterables.getOnlyElement(
            capturer.capture { project.modelV2().fetchModels() })

        val cleanBuild = Iterables.getOnlyElement(
            capturer.capture { project.executor()
                .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
                .run("assembleRelease") })

        val noOpBuild = Iterables.getOnlyElement(
            capturer.capture { project.executor()
                .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
                .run("assembleRelease") })

        for (profile in listOf(getModel, cleanBuild, noOpBuild)) {
            assertThat(profile.spanCount).isGreaterThan(0)

            assertThat(profile.projectCount).isGreaterThan(0)
            assertThat(profile.osName).containsMatch(Pattern.compile("Linux|Mac|Windows"))
            assertThat(profile.osVersion).isNotEmpty()
            assertThat(profile.javaVersion).isNotEmpty()
            assertThat(profile.javaVmVersion).isNotEmpty()
            assertThat(profile.parallelTaskExecution).isFalse()
            assertThat(profile.maxMemory).isGreaterThan(0)
            assertThat(profile.gradleVersion).isNotEmpty()
            val gbp = profile.getProject(0)
            assertThat(gbp.compileSdk).isEqualTo(GradleTestProject.compileSdkHash)
            assertThat(gbp.kotlinPluginVersion).isEqualTo(project.kotlinVersion)
            assertThat<GradleBuildProject.GradlePlugin,
                    Iterable<GradleBuildProject.GradlePlugin>>(gbp.pluginList)
                .containsAtLeast(
                        ORG_JETBRAINS_KOTLIN_GRADLE_PLUGIN_KOTLINANDROIDPLUGINWRAPPER,
                        COM_ANDROID_BUILD_GRADLE_APPPLUGIN
                )
            assertThat(gbp.variantCount).isGreaterThan(0)
            val gbv = gbp.getVariant(0)
            assertThat(gbv.minSdkVersion.apiLevel).isEqualTo(SUPPORT_LIB_MIN_SDK)
            assertThat(gbv.hasTargetSdkVersion()).named("has target sdk version").isTrue()
            assertThat(gbv.targetSdkVersion.apiLevel).named("target sdk version").isEqualTo(SUPPORT_LIB_MIN_SDK)
            assertThat(gbv.hasMaxSdkVersion()).named("has max sdk version").isFalse()
            assertThat(gbp.appliedPluginsList.any {
                it.className == "com.android.build.gradle.AppPlugin" &&
                        it.jarName == "gradle-api-${Version.ANDROID_GRADLE_PLUGIN_VERSION}"
            }).isTrue()
            assertThat(gbp.appliedPluginsList.any {
                it.className == "org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper" &&
                        it.jarName.startsWith("kotlin-gradle-plugin-${TestUtils.KOTLIN_VERSION_FOR_TESTS}")
            }).isTrue()
        }
        for (profile in listOf(cleanBuild, noOpBuild)) {
            assertThat(HashSet(profile.rawProjectIdList))
                .containsExactly("com.example.helloworld")
        }
    }
}

