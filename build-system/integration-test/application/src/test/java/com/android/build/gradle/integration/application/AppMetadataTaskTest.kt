/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.SdkConstants.ANDROID_GAME_DEVELOPMENT_EXTENSION_VERSION_PROPERTY
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.build.gradle.internal.tasks.AppMetadataTask
import com.android.build.gradle.options.StringOption
import com.android.builder.internal.packaging.IncrementalPackager.APP_METADATA_ENTRY_PATH
import com.android.testutils.apk.Zip
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.util.Properties
import java.util.function.Consumer
import kotlin.io.path.bufferedReader

/**
 * Tests for [AppMetadataTask]
 */
class AppMetadataTaskTest {

    private val app =
        MinimalSubProject.app("com.example.app")
            .appendToBuild("\n\nandroid.dynamicFeatures = [':feature']\n\n")
            .withFile(
                "src/main/res/values/strings.xml",
                """
                    <resources>
                        <string name="feature_title">Dynamic Feature Title</string>
                    </resources>
                """.trimIndent()
            )

    private val feature =
        MinimalSubProject.dynamicFeature("com.example.feature")
            .apply {
                replaceFile(
                    TestSourceFile(
                        "src/main/AndroidManifest.xml",
                        // language=XML
                        """
                            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                                xmlns:dist="http://schemas.android.com/apk/distribution">
                                <dist:module
                                    dist:onDemand="true"
                                    dist:title="@string/feature_title">
                                    <dist:fusing dist:include="true" />
                                </dist:module>
                                <application />
                            </manifest>
                            """.trimIndent()
                    )
                )
            }

    private val lib = MinimalSubProject.lib("com.example.lib")

    @JvmField
    @Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(
                MultiModuleTestProject.builder()
                    .subproject(":app", app)
                    .subproject(":feature", feature)
                    .subproject(":lib", lib)
                    .dependency(feature, app)
                    .dependency(app, lib)
                    .build()
            ).create()

    @Test
    fun testNoAppMetadataInAar() {
        project.executor().run(":lib:assembleDebug")
        project.getSubproject("lib").getAar(
            "debug",
            Consumer { assertThat(it.getJavaResource(APP_METADATA_ENTRY_PATH)).isNull() }
        )
    }

    @Test
    fun testNoAppMetadataInDynamicFeatureApk() {
        project.executor().run(":feature:assembleDebug")
        project.getSubproject("feature").getApk(GradleTestProject.ApkType.DEBUG).use {
            assertThat(it.getJavaResource(APP_METADATA_ENTRY_PATH)).isNull()
        }
    }

    @Test
    fun testAppMetadataInApk() {
        project.executor().run(":app:assembleDebug")
        project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG).use {
            assertThat(it.getJavaResource(APP_METADATA_ENTRY_PATH)).isNotNull()
        }
    }

    @Test
    fun testAppMetadataInBundle() {
        project.executor().run(":app:bundleDebug")
        val bundleFile = project.locateBundleFileViaModel("debug", ":app")
        Zip(bundleFile).use {
            assertThat(
                it.getEntry(
                    "BUNDLE-METADATA/com.android.tools.build.gradle/app-metadata.properties"
                )
            ).isNotNull()
        }
    }


    @Test
    fun testAppMetadataWithAgdeVersionInApk() {
        project.executor().with(StringOption.IDE_AGDE_VERSION, "2.72").run(":app:assembleDebug")
        project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
            val metadataFile = apk.getJavaResource(APP_METADATA_ENTRY_PATH)
            assertThat(metadataFile).isNotNull()

            // Load the App Metadata File as java Properties object
            val properties = Properties().apply {metadataFile!!.bufferedReader().use {load(it)}}
            assertThat(properties.getProperty(ANDROID_GAME_DEVELOPMENT_EXTENSION_VERSION_PROPERTY))
                    .isEqualTo("2.72")
        }
    }

    @Test
    fun testAppMetadataWithAgdeVersionInBundle() {
        project.executor().with(StringOption.IDE_AGDE_VERSION, "9.81").run(":app:bundleDebug")
        val bundleFile = project.locateBundleFileViaModel("debug", ":app")
        Zip(bundleFile).use {
            val metadataFile =
                it.getEntry(
                    "BUNDLE-METADATA/com.android.tools.build.gradle/app-metadata.properties")
            assertThat(metadataFile).named("App Metadata file inside Bundle").isNotNull()

            // Load the App Metadata File as java Properties object
            val properties = Properties().apply {metadataFile!!.bufferedReader().use {load(it)}}
            assertThat(properties.getProperty(ANDROID_GAME_DEVELOPMENT_EXTENSION_VERSION_PROPERTY))
                .isEqualTo("9.81")
        }
    }
}
