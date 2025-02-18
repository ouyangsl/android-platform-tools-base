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

package com.android.build.gradle.internal.cxx.model

import com.android.build.gradle.internal.cxx.RandomInstanceGenerator
import com.android.build.gradle.internal.cxx.gradle.generator.tryCreateConfigurationParameters
import com.android.build.gradle.internal.cxx.logging.PassThroughRecordingLoggingEnvironment
import com.android.build.gradle.internal.fixtures.FakeProviderFactory
import com.android.utils.FileUtils.join
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import java.io.File

class CreateCxxModuleModelTest {

    @Test
    fun `no native build`() {
        BasicModuleModelMock().let {
            assertThat(
            tryCreateConfigurationParameters(
                it.projectOptions,
                it.variantImpl,
            )
            ).isNull()
        }
    }

    @Test
    fun `simplest cmake`() {
        BasicModuleModelMock().let {
            doReturn(File("./CMakeLists.txt")).whenever(it.cmake).path
            assertThat(createCxxModuleModel(
                it.sdkComponents,
                it.configurationParameters,
            )).isNotNull()
        }
    }

    @Test
    fun `simplest ndk-build`() {
        BasicModuleModelMock().let {
            doReturn(File("./Android.mk")).whenever(it.ndkBuild).path
            assertThat(createCxxModuleModel(
                it.sdkComponents,
                it.configurationParameters,
            )).isNotNull()
        }
    }

    @Test
    fun `both cmake and ndk-build`() {
        BasicCmakeMock().let {
            doReturn(join(it.projectRootDir, "Android.mk")).whenever(it.ndkBuild).path
            PassThroughRecordingLoggingEnvironment().use { logEnvironment ->
                assertThat(
                    tryCreateConfigurationParameters(
                            it.projectOptions,
                            it.variantImpl,
                    )
                ).isNull()
                assertThat(logEnvironment.errors).hasSize(1)
                assertThat(logEnvironment.errors[0]).contains("More than one")
            }
        }
    }

    @Test
    fun `remap of buildStagingDirectory`() {
        BasicCmakeMock().let {
            PassThroughRecordingLoggingEnvironment().use { logEnvironment ->
                doReturn(File(it.projectInfo.buildDirectory.asFile.get(), "my-build-staging-directory"))
                    .whenever(it.cmake).buildStagingDirectory
                val componentModel =
                    tryCreateConfigurationParameters(
                            it.projectOptions,
                            it.variantImpl,
                    )!!
                val module = createCxxModuleModel(
                    it.sdkComponents,
                    it.configurationParameters,
                )
                val finalStagingDir = module.cxxFolder
                assertThat(logEnvironment.errors).hasSize(0)
                assertThat(finalStagingDir.path).contains("my-build-staging-directory")
            }
        }
    }

    @Test
    fun `remap of buildStagingDirectory into build folder`() {
        PassThroughRecordingLoggingEnvironment().use { logEnvironment ->
            BasicCmakeMock().let {
                doReturn(File(it.projectInfo.buildDirectory.asFile.get(), "my-build-staging-directory"))
                    .whenever(it.cmake).buildStagingDirectory
                val configurationParameters = tryCreateConfigurationParameters(
                        it.projectOptions,
                        it.variantImpl,
                )!!
                val module = createCxxModuleModel(
                    it.sdkComponents,
                    configurationParameters,
                )
                val finalStagingDir = module.cxxFolder
                assertThat(logEnvironment.warnings).hasSize(1)
                assertThat(logEnvironment.warnings[0])
                    .contains("The build staging directory you specified")
                assertThat(finalStagingDir.path).contains("my-build-staging-directory")
                assertThat(finalStagingDir.path).doesNotContain(".cxx")
            }
        }
    }

    @Test
    fun `round trip random instance`() {
        RandomInstanceGenerator()
            .synthetics(CxxModuleModel::class.java)
            .forEach { module ->
                val abiString = module.toJsonString()
                val recoveredAbi = createCxxModuleModelFromJson(abiString)
                val recoveredAbiString = recoveredAbi.toJsonString()
                assertThat(abiString).isEqualTo(recoveredAbiString)
            }
    }
}
