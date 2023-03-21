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

package com.android.build.gradle.integration.dexing

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.apk.AndroidArchive
import com.google.common.io.Resources
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class GlobalSyntheticsFeatureTest {

    private val app = MinimalSubProject.app("com.example.app").also {
        it.appendToBuild("""

            android.defaultConfig.minSdkVersion = 21
            android.dynamicFeatures = [":feature"]
            android {
                compileOptions {
                    sourceCompatibility JavaVersion.VERSION_14
                    targetCompatibility JavaVersion.VERSION_14
                }
            }
        """.trimIndent())
    }

    private val feature = MinimalSubProject.dynamicFeature("com.example.feature").also {
        it.appendToBuild("""

            android.defaultConfig.minSdkVersion = 21
            android {
                compileOptions {
                    sourceCompatibility JavaVersion.VERSION_14
                    targetCompatibility JavaVersion.VERSION_14
                }
            }
            dependencies {
                implementation project('::app')
                implementation 'com.example:myjar:1'
            }
        """.trimIndent())
    }

    private val recordJarUrl = Resources.getResource(
            GlobalSyntheticsFeatureTest::class.java,
            "GlobalSyntheticsTest/record.jar"
    )

    private val mavenRepo = MavenRepoGenerator(
            listOf(
                    MavenRepoGenerator.Library(
                            "com.example:myjar:1", Resources.toByteArray(recordJarUrl))
            )
    )

    @get:Rule
    val project = GradleTestProject.builder()
        .withAdditionalMavenRepo(mavenRepo)
        .fromTestApp(
            MultiModuleTestProject.builder()
                .subproject("app", app)
                .subproject("feature", feature)
                .build()
        ).create()

    @Test
    fun basicTest() {
        project.executor().run("assembleDebug")

        checkPackagedGlobal("Lcom/android/tools/r8/RecordTag;")
    }

    private fun checkPackagedGlobal(global: String) {
        val apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG)

        // there should only be a single global synthetics of specific type in the apk
        val dexes = apk.allDexes.filter {
            AndroidArchive.checkValidClassName(global)
            it.classes.keys.contains(global)
        }
        Truth.assertThat(dexes.size).isEqualTo(1)
    }
}
