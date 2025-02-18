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

package com.android.build.gradle.integration.desugar

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import com.android.build.gradle.integration.desugar.resources.ImplOfInterfaceWithDefaultMethod
import com.android.build.gradle.integration.desugar.resources.InterfaceWithDefaultMethod
import com.android.build.gradle.options.IntegerOption
import com.android.testutils.TestInputsGenerator
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Files

/** Test desugaring for file dependencies, which lack the metadata for artifact transforms.  */
@RunWith(FilterableParameterized::class)
class DesugarFileDependencyTest(var tool: Tool) {

    enum class Tool {
        D8,
        R8
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun getParams(): Array<Tool> = Tool.values()
    }

    private val mainProject = MinimalSubProject.app("com.example.app").apply {
        appendToBuild("""
                android {
                    compileOptions {
                        sourceCompatibility 1.8
                        targetCompatibility 1.8
                    }
                }
                dependencies {
                    implementation files('libs/interface.jar', 'libs/impl.jar')
                }
                """.trimIndent()
        )
    }

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(mainProject).create()

    @Before
    fun setUp() {
        if (tool == Tool.R8) {
            configureR8Desugaring(project)
        }
        addJars()
    }

    @Test
    fun checkBuilds() {
        executor().run("assembleDebug")
        project.getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
            assertThat(apk)
                .hasClass("Lcom/android/build/gradle/integration/desugar/resources/ImplOfInterfaceWithDefaultMethod;")
                .that()
                .hasMethod("myDefaultMethod")
        }
    }

    @Test
    fun checkMinApi24WithArtifactTransformsDoesNotDesugar() {
        Assume.assumeTrue(tool == Tool.D8)
        project.buildFile.appendText("\nandroid.defaultConfig.minSdkVersion 24")
        executor().run("assembleDebug")
        project.getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
            assertThat(apk)
                .hasClass("Lcom/android/build/gradle/integration/desugar/resources/ImplOfInterfaceWithDefaultMethod;")
                .that()
                .doesNotHaveMethod("myDefaultMethod")
        }
    }

    @Test
    fun checkMinApi21WithInjectedDevice() {
        Assume.assumeTrue(tool == Tool.D8)
        project.buildFile.appendText("\nandroid.defaultConfig.minSdkVersion 21")
        executor()
            .with(IntegerOption.IDE_TARGET_DEVICE_API, 24)
            .run("assembleDebug")
        project.getApk(
            GradleTestProject.ApkType.DEBUG,
            GradleTestProject.ApkLocation.Intermediates
        ).use { apk ->
            assertThat(apk)
                .hasClass("Lcom/android/build/gradle/integration/desugar/resources/ImplOfInterfaceWithDefaultMethod;")
                .that()
                .doesNotHaveMethod("myDefaultMethod")
        }
    }

    /** Regression test for http://b/146869072. */
    @Test
    fun checkIncrementalBuild() {
        Assume.assumeTrue(tool == Tool.D8)
        executor().run("assembleDebug")

        val updatedBuildFile = project.buildFile.readText().replace(
            "implementation files('libs/interface.jar', 'libs/impl.jar')",
            "implementation files('libs/impl.jar', 'libs/interface.jar')"
        )
        project.buildFile.writeText(updatedBuildFile)
        executor().run("assembleDebug")
    }

    private fun addJars() {
        val libs = project.file("libs").toPath()
        Files.createDirectory(libs)
        val interfaceLib = libs.resolve("interface.jar")
        val implLib = libs.resolve("impl.jar")

        TestInputsGenerator.pathWithClasses(interfaceLib, listOf(
            InterfaceWithDefaultMethod::class.java))
        TestInputsGenerator.pathWithClasses(implLib, listOf(
            ImplOfInterfaceWithDefaultMethod::class.java))

    }

    private fun executor(): GradleTaskExecutor {
        return project.executor()
    }
}
