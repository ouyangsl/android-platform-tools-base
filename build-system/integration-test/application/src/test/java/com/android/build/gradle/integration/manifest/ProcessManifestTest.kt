/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.integration.manifest

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.testutils.truth.PathSubject
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.io.File

class ProcessManifestTest {
    @JvmField @Rule
    var project: GradleTestProject = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.library"))
            .create()

    @Test
    fun build() {
        project.buildFile.appendText("""
            import com.android.build.api.variant.AndroidVersion

            androidComponents {
                beforeVariants(selector().all(), { variant ->
                    variant.minSdk = 21
                    variant.maxSdk = 29
                    variant.targetSdk = 22
                })
            }
        """.trimIndent())

        project.executor().run("processDebugManifest")
        val manifestContent =
            File(project.buildDir, "intermediates/merged_manifest/debug/processDebugManifest/AndroidManifest.xml")
                .readLines().joinToString("\n")
        Truth.assertThat(manifestContent).contains("android:minSdkVersion=\"21\"")
        Truth.assertThat(manifestContent).doesNotContain("android:targetSdkVersion")
        Truth.assertThat(manifestContent).contains("android:maxSdkVersion=\"29\"")
    }

    // Regression test for b/237450413
    @Test
    fun testSourceManifestDeletion() {
        val manifestFile = project.file("src/main/AndroidManifest.xml")
        PathSubject.assertThat(manifestFile).isFile()
        project.executor().run("processDebugManifest")
        FileUtils.deleteIfExists(manifestFile)
        PathSubject.assertThat(manifestFile).doesNotExist()
        project.executor().run("processDebugManifest")
    }

    // This should eventually be a warning, but not until there's AUA support (b/272815813)
    @Test
    fun testNoWarningsForApplicationAttributes() {
        val manifestFile = project.file("src/main/AndroidManifest.xml")
        FileUtils.deleteIfExists(manifestFile)
        FileUtils.createFile(
            manifestFile,
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application android:extractNativeLibs="true"/>
                </manifest>
            """.trimIndent())
        val result = project.executor().run("assembleDebug")
        result.stdout.use {
            assertThat(it).doesNotContain("android:extractNativeLibs should not be specified")
        }
    }
}
