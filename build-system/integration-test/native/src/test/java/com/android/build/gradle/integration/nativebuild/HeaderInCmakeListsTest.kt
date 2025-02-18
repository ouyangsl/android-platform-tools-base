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

package com.android.build.gradle.integration.nativebuild

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_NDK_SIDE_BY_SIDE_VERSION
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2.NativeModuleParams
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import com.android.build.gradle.integration.common.fixture.model.readCompileCommandsJsonBin
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.cxx.configure.CMakeVersion
import com.android.builder.model.v2.ide.SyncIssue
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * This test is related to b/112611156
 *
 * The project set up is equivalent to File/Create New Project except that there is a new file
 * called extra-header.hpp and this file is referenced by CMakeLists.txt.
 *
 * Even though this is valid CMake syntax we shouldn't send the header-file to Android Studio
 * because it won't have flags and Android Studio will reject it. The header file is recorded
 * in android_gradle_build.json but is not in the model sent to Android Studio.
 */
@RunWith(Parameterized::class)
class HeaderInCmakeListsTest(private val cmakeVersionInDsl: String) {

    companion object {
        @Parameterized.Parameters(name = "version={0}")
        @JvmStatic
        fun data() = CMakeVersion.FOR_TESTING.map { it.version }.toTypedArray()
    }

    @Rule
    @JvmField
    val project = GradleTestProject.builder()
        .fromTestApp(
            HelloWorldJniApp.builder()
                .withNativeDir("cpp")
                .useCppSource(true)
                .build()
        )
        .setSideBySideNdkVersion(DEFAULT_NDK_SIDE_BY_SIDE_VERSION)
        .create()

    @Before
    fun setup() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """apply plugin: 'com.android.application'
                        android.namespace "com.example.hellojni"
                        android.compileSdkVersion ${GradleTestProject.DEFAULT_COMPILE_SDK_VERSION}
                        android.defaultConfig.minSdkVersion ${GradleTestProject.DEFAULT_MIN_SDK_VERSION}
                        android.ndkPath "${project.ndkPath}"
                        android.externalNativeBuild.cmake.path "src/main/cpp/CMakeLists.txt"
                        android.externalNativeBuild.cmake.version "$cmakeVersionInDsl"
                """

        )

        val cmakeLists = File(project.buildFile.parent, "src/main/cpp/CMakeLists.txt")
        TestFileUtils.appendToFile(
            cmakeLists,
            """
cmake_minimum_required(VERSION 3.4.1)
add_library(native-lib SHARED hello-jni.cpp extra-header.hpp)
find_library(log-lib log)
target_link_libraries(native-lib ${'$'}{log-lib})
                """
        )

        val extraHeader = File(project.buildFile.parent, "src/main/cpp/extra-header.hpp")
        TestFileUtils.appendToFile(
            extraHeader,
            """
// Extra header file that is referenced in CMakeLists.txt
                """
        )
    }

    @Test
    fun testThatHeaderFileIsExcluded() {
        val nativeModules = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING) // CMake cannot detect compiler attributes
            .fetchNativeModules(NativeModuleParams())
        val nativeModule = nativeModules.container.singleNativeModule
        assertThat(nativeModule.variants.map { it.name }).containsExactly("debug", "release")
        for (variant in nativeModule.variants) {
            for (abi in variant.abis) {
                assertThat(abi.sourceFlagsFile.readCompileCommandsJsonBin(nativeModules.normalizer))
                    .hasSize(1)
            }
        }
    }
}
