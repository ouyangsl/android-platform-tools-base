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

package com.android.build.gradle.integration.ndk

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_NDK_SIDE_BY_SIDE_VERSION
import com.android.build.gradle.integration.common.fixture.model.recoverExistingCxxAbiModels
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.metadataGenerationCommandFile
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import com.android.build.gradle.internal.cxx.configure.CMakeVersion
import com.android.build.gradle.internal.cxx.model.name

@RunWith(Parameterized::class)
class PrefabTest(private val buildSystem: NativeBuildSystem, val cmakeVersion: String) {
    private val expectedAbis = listOf(Abi.ARMEABI_V7A, Abi.ARM64_V8A, Abi.X86, Abi.X86_64).map { it.tag }.sorted()

    @Rule
    @JvmField
    val project = GradleTestProject.builder().fromTestProject("prefabApp")
        .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
        .create()

    @Rule
    @JvmField
    val prefabNoDepsProject = GradleTestProject.builder().fromTestProject("prefabNoDeps")
        .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
        .create()

    companion object {
        @Parameterized.Parameters(name = "build system = {0}, cmake = {1}")
        @JvmStatic
        fun data() =
            CMakeVersion.FOR_TESTING.map { arrayOf(NativeBuildSystem.CMAKE, it.version) } +
                    listOf(
                        arrayOf(NativeBuildSystem.NDK_BUILD, "N/A")
                    )
    }

    fun setupProject(project: GradleTestProject) {
        val appBuild = project.buildFile.parentFile.resolve("app/build.gradle")
        if (buildSystem == NativeBuildSystem.NDK_BUILD) {
            appBuild.appendText("""
                android.externalNativeBuild.ndkBuild.path="src/main/cpp/Android.mk"
                """.trimIndent())
        } else {
            appBuild.appendText("""
                android.externalNativeBuild.cmake.path="src/main/cpp/CMakeLists.txt"
                android.externalNativeBuild.cmake.version="$cmakeVersion"
                android.defaultConfig.externalNativeBuild.cmake.arguments.add("-DANDROID_STL=c++_shared")
                """.trimIndent())
        }
    }

    @Before
    fun setUp() {
        setupProject(project)
        setupProject(prefabNoDepsProject)
    }

    private fun verifyNdkBuildPackage(pkg: String, abiDir: File) {
        assertThat(abiDir.resolve("prefab/$pkg/Android.mk")).exists()
    }

    private fun verifyNdkBuildArgs(buildCommand: String, abiDir: File) {
        val expectedArgument = "NDK_GRADLE_INJECTED_IMPORT_PATH=$abiDir"
        assertThat(buildCommand).contains(expectedArgument)
    }

    private fun verifyCMakePackage(pkg: String, abi: CxxAbiModel) {
        val triple = abi.info.triple
        assertThat(
            abi.prefabFolder.resolve(
                "prefab/lib/$triple/cmake/$pkg/${pkg}Config.cmake"
            )
        ).exists()
    }

    private fun verifyCMakeArgs(buildCommand: String, abiDir: File) {
        val findRootPath = abiDir.resolve("prefab")
        val expectedArgument = "-DCMAKE_FIND_ROOT_PATH=$findRootPath"
        Truth.assertThat(buildCommand).contains(expectedArgument)
    }

    @Test
    fun `build integrations are passed to build system`() {
        executor(project).run("assembleDebug")
        val abis = project.recoverExistingCxxAbiModels().sortedBy { it.name }
        assertThat(abis.map { it.name }).containsExactlyElementsIn(expectedAbis)
        for (abi in abis) {
            val abiDir = abi.prefabFolder
            assertThat(abiDir).exists()
            val packages = listOf("curl", "jsoncpp", "openssl")
            for (pkg in packages) {
                when (buildSystem) {
                    NativeBuildSystem.CMAKE -> verifyCMakePackage(pkg, abi)
                    NativeBuildSystem.NDK_BUILD -> verifyNdkBuildPackage(pkg, abiDir)
                    else -> error("Unexpected $buildSystem")
                }
            }

            val buildCommand = abi.metadataGenerationCommandFile.readText()
            when (buildSystem) {
                NativeBuildSystem.CMAKE -> verifyCMakeArgs(buildCommand, abiDir)
                NativeBuildSystem.NDK_BUILD -> verifyNdkBuildArgs(buildCommand, abiDir)
                else -> error("Unexpected $buildSystem")
            }
        }
    }

    @Test
    fun `cleaning a cleaned project works`() {
        executor(project).run("clean")
        executor(project).run("clean")
    }

    @Test
    fun `project builds`() {
        executor(project).run("clean", "assembleDebug")
    }

    @Test
    fun `dependencies are copied to the APK`() {
        executor(project).run("clean", "assembleDebug")
        val apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG)
        assertThat(apk.file).exists()
        for (abi in expectedAbis) {
            assertThatApk(apk).contains("lib/$abi/libapp.so")
            assertThatApk(apk).contains("lib/$abi/libcrypto.so")
            assertThatApk(apk).contains("lib/$abi/libcurl.so")
            assertThatApk(apk).contains("lib/$abi/libjsoncpp.so")
            assertThatApk(apk).contains("lib/$abi/libssl.so")
        }
    }

    @Test
    fun `enabling without prefab AARs doesn't break the build`() {
        // https://issuetracker.google.com/183634734
        executor(prefabNoDepsProject).run("clean", "assembleDebug")
    }

    private fun executor(project: GradleTestProject): GradleTaskExecutor {
        return project.executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
    }
}
