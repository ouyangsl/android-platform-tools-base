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
import com.google.common.io.Files
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DifferentCompileSdkPerModuleTest {
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
        setupLibrary(":libB", "24", "com.example.androidLibB").also {
            it.file("src/main/java/libA").mkdirs()
            Files.asCharSink(
                it.file("src/main/java/libA/TestClass.java"),
                Charsets.UTF_8)
                .write("""
                    |package libA;
                    |
                    |import android.os.Build;
                    |
                    |public class TestClass {
                    |    public void test() {
                    |        int ver = Build.VERSION_CODES.N;
                    |    }
                    |}
                """.trimMargin())
        }
        setupLibrary(":libC", "23", "com.example.androidLibC")
    }

    @Test
    fun build() {
        project.executor().run("assembleDebug")
    }

    @Test
    fun modelTest() {
        project.execute("help")
        val container = project.modelV2().fetchModels().container
        Truth.assertThat(
            container.getProject(":libA").basicAndroidProject?.bootClasspath?.first()?.absolutePath
        ).contains("android-19")
        Truth.assertThat(
            container.getProject(":libB").basicAndroidProject?.bootClasspath?.first()?.absolutePath
        ).contains("android-24")
        Truth.assertThat(
            container.getProject(":libC").basicAndroidProject?.bootClasspath?.first()?.absolutePath
        ).contains("android-23")
    }

    private fun setupLibrary(
        name: String,
        compileSdkVersion: String,
        namespace: String
    ): GradleTestProject {
        return project.getSubproject(name).also { project ->
            project.buildFile.also {
                it.writeText(
                    """
                    |apply plugin: 'com.android.library'
                    |android {
                    |    namespace "$namespace"
                    |    compileSdkVersion $compileSdkVersion
                    |}
                    |""".trimMargin()
                )
            }
        }

    }
}
