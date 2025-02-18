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

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Test checking for error when 2 APKs in multi-APK project package the same library
 */
class CheckMultiApkLibrariesTaskTest {

    private val lib = MinimalSubProject.lib("com.example.lib")

    private val baseModule = MinimalSubProject.app("com.example.baseModule")
        .appendToBuild(
            """
                            android {
                                dynamicFeatures =
                                    [':otherFeature1', ':otherFeature2', ':otherFeature3']
                                defaultConfig.minSdkVersion 14
                            }
                            """)

    private val otherFeature1 = createFeatureSplit("com.example.otherFeature1")

    private val otherFeature2 = createFeatureSplit("com.example.otherFeature2")

    private val otherFeature3 = createFeatureSplit("com.example.otherFeature3")

    private val testApp =
        MultiModuleTestProject.builder()
            .subproject(":lib", lib)
            .subproject(":baseModule", baseModule)
            .subproject(":otherFeature1", otherFeature1)
            .subproject(":otherFeature2", otherFeature2)
            .subproject(":otherFeature3", otherFeature3)
            .dependency(otherFeature1, lib)
            .dependency(otherFeature2, lib)
            .dependency(otherFeature3, lib)
            .dependency(otherFeature1, baseModule)
            .dependency(otherFeature2, baseModule)
            .dependency(otherFeature3, baseModule)
            .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Test
    fun `test library collision yields error`() {
        val result = project.executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
            .expectFailure().run("assembleDebug")

        assertThat(result.failureMessage).contains(
            "[:otherFeature1, :otherFeature2, :otherFeature3] all package the same library [:lib;Capability: group='project', name='lib'].")
        assertThat(result.failureMessage).contains(
            "[:otherFeature1, :otherFeature2, :otherFeature3] all package the same library [com.android.support:support-core-utils;Capability: group='com.android.support', name='support-core-utils'].")
        assertThat(result.failureMessage).contains(
            "Multiple APKs packaging the same library can cause runtime errors."
        )
    }

    private fun createFeatureSplit(packageName: String) = MinimalSubProject.dynamicFeature(packageName)
        .appendToBuild(
                """
                    android.defaultConfig.minSdkVersion 14
                    dependencies {
                        implementation 'com.android.support:support-core-utils:' + libs.versions.supportLibVersion.get()
                    }
                    """)
}
