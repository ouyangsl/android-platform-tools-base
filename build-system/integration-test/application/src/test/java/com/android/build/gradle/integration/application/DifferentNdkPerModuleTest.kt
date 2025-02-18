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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DifferentNdkPerModuleTest {
    @Rule
    @JvmField
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestApp(
            MultiModuleTestProject(
                mapOf(
                    ":libA" to MinimalSubProject.lib("com.example.androidLibA"),
                    ":libB" to MinimalSubProject.lib("com.example.androidLibB"),
                    ":libC" to MinimalSubProject.lib("com.example.androidLibC")
                )
            )
        ).create()

    @Before
    fun setUp() {
        setupLibrary(":libA", "19", "com.example.androidLibA")
        setupLibrary(":libB", "24", "com.example.androidLibB")
        setupLibrary(":libC", "23", "com.example.androidLibC")
    }

    @Test
    fun build() {
        project.executor().run("help")
    }

    private fun setupLibrary(
        name: String,
        ndkVersion: String,
        namespace: String
    ): GradleTestProject {
        return project.getSubproject(name).also { project ->
            project.buildFile.also {
                it.writeText(
                    """
                |apply plugin: 'com.android.library'
                |android {
                |   namespace "$namespace"
                |   compileSdkVersion 24
                |   ndkVersion "$ndkVersion"
                |}
                |import com.android.build.gradle.internal.dsl.SdkComponentsImpl
                |
                |androidComponents {
                |
                |   beforeVariants(selector().all(), { variant ->
                |       String registeredValue = (getSdkComponents() as SdkComponentsImpl).ndkVersion.get()
                |       if (registeredValue != android.ndkVersion) {
                |           throw new RuntimeException("Invalid value for ndkVersion : ${'$'}registeredValue, expected ${'$'}android.ndkVersion")
                |      }
                |   })
                |}
            """.trimMargin()
                )
            }
        }
    }
}
