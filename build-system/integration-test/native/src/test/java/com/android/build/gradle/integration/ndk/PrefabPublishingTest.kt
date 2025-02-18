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

package com.android.build.gradle.integration.ndk

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.model.recoverExistingCxxAbiModels
import com.android.build.gradle.internal.core.Abi
import com.android.build.gradle.internal.cxx.configure.CMakeVersion
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.minSdkVersion
import com.android.build.gradle.internal.cxx.model.name
import com.android.build.gradle.tasks.NativeBuildSystem
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class PrefabPublishingTest(
    private val variant: String,
    private val buildSystem: NativeBuildSystem,
    private val cmakeVersion: String,
) {

    private val projectName = "prefabPublishing"
    private val gradleModuleName = "foo"

    @Rule
    @JvmField
    val project = GradleTestProject.builder().fromTestProject(projectName)
        .setSideBySideNdkVersion(GradleTestProject.DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
        .create()

    private val ndkMajor = GradleTestProject.DEFAULT_NDK_SIDE_BY_SIDE_VERSION.split(".").first()

    private val expectedAbis = listOf(Abi.ARMEABI_V7A, Abi.ARM64_V8A, Abi.X86, Abi.X86_64)

    enum class LibraryType {
        Shared,
        Static,
        HeaderOnly,
    }

    companion object {
        @Parameterized.Parameters(name = "variant = {0}, build system = {1}, cmake = {2}")
        @JvmStatic
        fun data() = arrayOf("debug", "release").map { config ->
            CMakeVersion.FOR_TESTING.map {
                arrayOf(
                    config,
                    NativeBuildSystem.CMAKE,
                    it.version
                )
            } + arrayOf(arrayOf(config, NativeBuildSystem.NDK_BUILD, "N/A"))
        }.flatten()
    }

    @Before
    fun setUp() {
        val appBuild = project.buildFile.parentFile.resolve("foo/build.gradle")
        if (buildSystem == NativeBuildSystem.NDK_BUILD) {
            appBuild.appendText(
                """
                android.externalNativeBuild.ndkBuild.path="src/main/cpp/Android.mk"
                """.trimIndent()
            )
        } else {
            appBuild.appendText(
                """
                android.externalNativeBuild.cmake.path="src/main/cpp/CMakeLists.txt"
                android.externalNativeBuild.cmake.version="$cmakeVersion"
                android.defaultConfig.externalNativeBuild.cmake.arguments.add("-DANDROID_STL=c++_shared")
                """.trimIndent()
            )
        }
    }

    private fun verifyModule(
        project: GradleTestProject,
        packageDir: File,
        moduleName: String,
        libraryType: LibraryType,
        libraryName: String? = null
    ) {
        val moduleDir = packageDir.resolve("modules/$moduleName")
        val moduleMetadata = moduleDir.resolve("module.json").readText()
        if (libraryName != null) {
            Truth.assertThat(moduleMetadata).isEqualTo(
                """
                {
                  "export_libraries": [],
                  "library_name": "$libraryName",
                  "android": {}
                }
                """.trimIndent()
            )
        } else {
            Truth.assertThat(moduleMetadata).isEqualTo(
                """
                {
                  "export_libraries": [],
                  "android": {}
                }
                """.trimIndent()
            )
        }

        val header = moduleDir.resolve("include/$gradleModuleName/$gradleModuleName.h").readText()
        Truth.assertThat(header).isEqualTo(
            """
            #pragma once

            void $gradleModuleName();

            """.trimIndent()
        )

        for (abiName in expectedAbis) {
            val abi = project.recoverExistingCxxAbiModels(abiName)
            verifyLibrariesForAbi(
                abi,
                moduleDir,
                moduleName,
                libraryName,
                libraryType
            )
        }
    }

    private fun verifyLibrariesForAbi(
        abi: CxxAbiModel,
        moduleDir: File,
        moduleName: String,
        libraryName: String?,
        libraryType: LibraryType,
    ) {
        val prefix = libraryName ?: "lib$moduleName"
        val suffix = when (libraryType) {
            LibraryType.Static -> ".a"
            LibraryType.Shared -> ".so"
            LibraryType.HeaderOnly -> return
        }

        val abiDir = moduleDir.resolve("libs/android.${abi.name}")
        val abiMetadata = abiDir.resolve("abi.json").readText()
        val apiLevel = abi.minSdkVersion

        Truth.assertThat(abiMetadata).isEqualTo(
            """
            {
              "abi": "${abi.name}",
              "api": $apiLevel,
              "ndk": $ndkMajor,
              "stl": "c++_shared",
              "static": ${libraryType == LibraryType.Static}
            }
            """.trimIndent()
        )

        val library = abiDir.resolve("$prefix$suffix")
        assertThat(library).exists()
    }

    @Test
    fun `project builds`() {
        executor().run("clean", "assemble$variant")
    }

    @Test
    fun `prefab package was constructed correctly`() {
        executor().run("assemble$variant")

        val packageDir = project.getSubproject(gradleModuleName)
            .getIntermediateFile("prefab_package", variant, "prefab")
        val packageMetadata = packageDir.resolve("prefab.json").readText()
        Truth.assertThat(packageMetadata).isEqualTo(
            """
            {
              "name": "$gradleModuleName",
              "schema_version": 2,
              "dependencies": [],
              "version": "1.0"
            }
            """.trimIndent()
        )

        verifyModule(project, packageDir, gradleModuleName, libraryType = LibraryType.Shared)
        verifyModule(project, packageDir, "${gradleModuleName}_static", libraryType = LibraryType.Static)
    }

    @Test
    fun `AAR contains the prefab packages`() {
        executor().run("clean", "assemble$variant")
        project.getSubproject(gradleModuleName).assertThatAar(variant) {
            containsFile("prefab/prefab.json")
            containsFile("prefab/modules/$gradleModuleName/module.json")
            containsFile("prefab/modules/${gradleModuleName}_static/module.json")
            // Regression test for b/232117952
            doesNotContain("/modules/$gradleModuleName/")
            doesNotContain("/modules/$gradleModuleName/include/$gradleModuleName/")
        }
    }

    // Before the fix for b/203448887, this test failed because BundleAar did not declare a
    // dependency on PREFAB_PACKAGE_CONFIGURATION
    @Test
    fun `Bundle AAR has no dependency warnings `() {
        executor().run("bundle${variant}Aar", "prefab${variant}ConfigurePackage")
    }

    // See b/203448887
    @Test
    fun `Bundle local lint AAR has no dependency warnings `() {
        executor().run("bundle${variant}LocalLintAar", "prefab${variant}ConfigurePackage")
    }

    @Test
    fun `adding a new header causes a rebuild`() {
        executor().run("assemble${variant.lowercase()}")
        val packageDir = project.getSubproject(gradleModuleName)
            .getIntermediateFile("prefab_package", variant, "prefab")
        val moduleDir = packageDir.resolve("modules/$gradleModuleName")
        val headerSubpath = File("include/bar.h")
        val header = moduleDir.resolve(headerSubpath)
        assertThat(header).doesNotExist()

        val headerSrc =
            project.getSubproject(gradleModuleName).getMainSrcDir("cpp").resolve(headerSubpath)
        headerSrc.writeText(
            """
                #pragma once
                void bar();
                """.trimIndent()
        )

        executor().run("assemble$variant")
        assertThat(header).exists()
    }

    @Test
    fun `removing a header causes a rebuild`() {
        val packageDir = project.getSubproject(gradleModuleName)
            .getIntermediateFile("prefab_package", variant, "prefab")
        val moduleDir = packageDir.resolve("modules/$gradleModuleName")
        val headerSubpath = File("include/bar.h")
        val header = moduleDir.resolve(headerSubpath)
        val headerSrc =
            project.getSubproject(gradleModuleName).getMainSrcDir("cpp").resolve(headerSubpath)
        headerSrc.writeText(
            """
            #pragma once
            void bar();
            """.trimIndent()
        )

        executor().run("assemble$variant")
        assertThat(header).exists()

        headerSrc.delete()
        executor().run("assemble$variant")
        assertThat(header).doesNotExist()
    }

    @Test
    fun `changing a header causes a rebuild`() {
        val packageDir = project.getSubproject(gradleModuleName)
            .getIntermediateFile("prefab_package", variant, "prefab")
        val moduleDir = packageDir.resolve("modules/$gradleModuleName")
        val headerSubpath = File("include/bar.h")
        val header = moduleDir.resolve(headerSubpath)
        val headerSrc =
            project.getSubproject(gradleModuleName).getMainSrcDir("cpp").resolve(headerSubpath)
        headerSrc.writeText(
            """
                #pragma once
                void bar();
                """.trimIndent()
        )

        executor().run("assemble$variant")
        assertThat(header).exists()

        val newHeaderContents = """
                #pragma once
                void bar(int);
                """.trimIndent()

        headerSrc.writeText(newHeaderContents)
        executor().run("assemble$variant")
        Truth.assertThat(header.readText()).isEqualTo(newHeaderContents)
    }

    @Test
    fun `modules with libraryName are constructed correctly`() {
        // The ndk-build importer isn't able to determine the name of a module if its
        // LOCAL_MODULE_FILENAME is altered.
        Assume.assumeTrue(buildSystem != NativeBuildSystem.NDK_BUILD)
        val subproject = project.getSubproject(gradleModuleName)

        subproject.buildFile.writeText(
            """
            plugins {
                id 'com.android.library'
            }

            android {
                namespace "com.example.foo"
                compileSdkVersion libs.versions.latestCompileSdk.get().toInteger()
                buildToolsVersion = libs.versions.buildToolsVersion.get()

                defaultConfig {
                    minSdkVersion 21
                    targetSdkVersion libs.versions.latestCompileSdk.get()


                    externalNativeBuild {
                        if (!project.hasProperty("ndkBuild")) {
                            cmake {
                                arguments "-DANDROID_STL=c++_shared"
                            }
                        }
                    }
                }

                externalNativeBuild {
                    if (project.hasProperty("ndkBuild")) {
                        ndkBuild {
                            path "src/main/cpp/Android.mk"
                        }
                    } else {
                        cmake {
                            path "src/main/cpp/CMakeLists.txt"
                        }
                    }
                }

                buildFeatures {
                    version "1.0.0-rc01"
                    prefabPublishing true
                }

                prefab {
                    foo {
                        headers "src/main/cpp/include"
                        libraryName "libfoo_static"
                    }
                }
            }
            """.trimIndent()
        )
        subproject.getMainSrcDir("cpp").resolve("CMakeLists.txt").writeText(
            """
            cmake_minimum_required(VERSION 3.6)
            project(foo VERSION 1.0.0 LANGUAGES CXX)

            add_library(foo STATIC foo.cpp)
            target_include_directories(foo PUBLIC include)
            set_target_properties(foo PROPERTIES OUTPUT_NAME "foo_static")
            """.trimIndent()
        )
        subproject.getMainSrcDir("cpp").resolve("Android.mk").writeText(
            """
            LOCAL_PATH := $(call my-dir)

            include $(CLEAR_VARS)
            LOCAL_MODULE := foo
            LOCAL_MODULE_FILENAME := libfoo_static
            LOCAL_SRC_FILES := foo.cpp
            LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
            LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
            include $(BUILD_STATIC_LIBRARY)
            """.trimIndent()
        )

        executor().run("assemble$variant")

        project.getSubproject(gradleModuleName).assertThatAar(variant) {
            containsFile("prefab/prefab.json")
            containsFile("prefab/modules/$gradleModuleName/module.json")
        }

        val packageDir = project.getSubproject(gradleModuleName)
            .getIntermediateFile("prefab_package", variant, "prefab")
        verifyModule(project, packageDir, gradleModuleName, LibraryType.Static, "libfoo_static")
        val packageMetadata = packageDir.resolve("prefab.json").readText()
        Truth.assertThat(packageMetadata).isEqualTo(
            """
            {
              "name": "$gradleModuleName",
              "schema_version": 2,
              "dependencies": [],
              "version": "1.0.0"
            }
            """.trimIndent()
        )
    }

    @Test
    fun `modules with hyphenated names that are prefixes of other modules match appropriately`() {
        val subproject = project.getSubproject(gradleModuleName)

        subproject.getMainSrcDir("cpp").resolve("CMakeLists.txt").appendText(
            """

            add_library(foo-jni SHARED foo.cpp)
            target_include_directories(foo-jni PUBLIC include)
            """.trimIndent()
        )
        subproject.getMainSrcDir("cpp").resolve("Android.mk").appendText(
            """

            include $(CLEAR_VARS)
            LOCAL_MODULE := foo-jni
            LOCAL_SRC_FILES := foo.cpp
            LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
            LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
            include $(BUILD_SHARED_LIBRARY)
            """.trimIndent()
        )

        executor().run("assemble$variant")

        val packageDir = project.getSubproject(gradleModuleName)
            .getIntermediateFile("prefab_package", variant, "prefab")

        verifyModule(project, packageDir, gradleModuleName, libraryType = LibraryType.Shared)
    }

    @Test
    fun `header only libraries are packaged appropriately`() {
        val subproject = project.getSubproject(gradleModuleName)

        subproject.buildFile.writeText(
            """
            plugins {
                id 'com.android.library'
            }

            android {
                namespace "com.example.foo"
                compileSdkVersion libs.versions.latestCompileSdk.get().toInteger()
                buildToolsVersion = libs.versions.buildToolsVersion.get()

                defaultConfig {
                    minSdkVersion 21
                    targetSdkVersion libs.versions.latestCompileSdk.get()

                    externalNativeBuild {
                        if (!project.hasProperty("ndkBuild")) {
                            cmake {}
                        }
                    }
                }

                externalNativeBuild {
                    if (project.hasProperty("ndkBuild")) {
                        ndkBuild {
                            path "src/main/cpp/Android.mk"
                        }
                    } else {
                        cmake {
                            path "src/main/cpp/CMakeLists.txt"
                        }
                    }
                }

                buildFeatures {
                    prefabPublishing true
                }

                prefab {
                    foo {
                        headers "src/main/cpp/include"
                        headerOnly true
                    }
                }
            }
            """.trimIndent()
        )

        subproject.getMainSrcDir("cpp").resolve("CMakeLists.txt").writeText(
            """
            cmake_minimum_required(VERSION 3.6)
            project(foo VERSION 1.0.0 LANGUAGES CXX)

            add_library(foo INTERFACE)
            target_include_directories(foo INTERFACE include)
            """.trimIndent()
        )
        subproject.getMainSrcDir("cpp").resolve("Android.mk").writeText(
            """
            LOCAL_PATH := $(call my-dir)

            include $(CLEAR_VARS)
            LOCAL_MODULE := foo
            LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
            include $(BUILD_STATIC_LIBRARY)
            """.trimIndent()
        )

        executor().run("assemble$variant")

        val packageDir = project.getSubproject(gradleModuleName)
            .getIntermediateFile("prefab_package", variant, "prefab")

        verifyModule(project, packageDir, gradleModuleName, libraryType = LibraryType.HeaderOnly)
    }

    private fun executor(): GradleTaskExecutor {
        return project.executor().withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
    }
}
