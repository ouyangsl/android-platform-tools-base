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

import com.android.utils.FileUtils.join
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.SetProperty
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Set up a basic environment that will result in a CMake [CxxModuleModel]
 */
open class BasicCmakeMock(createFakeNinja : Boolean = true) : BasicModuleModelMock() {

    // Walk all vals in the model and invoke them
    val module by lazy {
        createCxxModuleModel(
            sdkComponents,
            configurationParameters,
        )
    }
    val variant by lazy { createCxxVariantModel(configurationParameters, module) }
    val abi by lazy { createCxxAbiModel(sdkComponents, configurationParameters, variant, "x86") }
    val riscvAbi by lazy { createCxxAbiModel(sdkComponents, configurationParameters, variant, "riscv64" ) }

    init {
        doReturn(makeSetProperty(setOf())).whenever(variantExternalNativeBuild).abiFilters
        doReturn(makeListProperty(listOf("-DCMAKE_ARG=1"))).whenever(variantExternalNativeBuild).arguments
        doReturn(makeListProperty(listOf("-DC_FLAG_DEFINED"))).whenever(variantExternalNativeBuild).cFlags
        doReturn(makeListProperty(listOf("-DCPP_FLAG_DEFINED"))).whenever(variantExternalNativeBuild).cppFlags
        doReturn(makeSetProperty(setOf<String>())).whenever(variantExternalNativeBuild).targets
        val makefile = join(allPlatformsProjectRootDir, "CMakeLists.txt")
        doReturn(makefile).whenever(cmake).path
        projectRootDir.mkdirs()
        makefile.writeText("# written by ${BasicCmakeMock::class}")
        if (createFakeNinja) {
            // Create the ninja executable files so that the macro expansion can succeed
            cmakeDir.apply { mkdirs() }.apply {
                resolve("ninja").writeText("whatever")
                resolve("ninja.exe").writeText("whatever")
            }
        }
    }

    private fun makeListProperty(values: List<String>): ListProperty<*> =
        mock<ListProperty<*>>().also {
            doReturn(values).whenever(it).get()
        }

    private fun makeSetProperty(values: Set<String>): SetProperty<*> =
            mock<SetProperty<*>>().also {
                doReturn(values).whenever(it).get()
            }

}
