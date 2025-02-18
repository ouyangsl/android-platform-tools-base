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

package com.android.build.gradle.internal.variant

import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.impl.FilterConfigurationImpl
import com.android.build.gradle.internal.core.dsl.MultiVariantComponentDslInfo
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.StringOption
import com.android.builder.core.ComponentTypeImpl
import com.google.common.truth.Truth
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.doReturn

internal class VariantPathHelperTest {

    val project: Project = ProjectBuilder.builder().build()

    val buildDirectory: DirectoryProperty by lazy {
        project.layout.buildDirectory
    }

    private val variantDslInfo: MultiVariantComponentDslInfo = mock(lenient = true)

    private val dslServices: DslServices = mock(lenient = true)

    private val projectOptions: ProjectOptions = mock(lenient = true)

    @Before
    fun setup() {
        whenever(dslServices.projectOptions).thenReturn(projectOptions)
        whenever(variantDslInfo.componentType).thenReturn(ComponentTypeImpl.LIBRARY)
        whenever(variantDslInfo.productFlavorList).thenReturn(emptyList())
        whenever(variantDslInfo.buildType).thenReturn("apk_location")
    }

    @Test
    fun testCustomAbiBuildLocation() {
        val variantPathHelper = VariantPathHelper(
            buildDirectory,
            variantDslInfo,
            dslServices
        )
        doReturn("x86").whenever(projectOptions).get(StringOption.IDE_BUILD_TARGET_ABI)
        Truth.assertThat(variantPathHelper.apkLocation.absolutePath).contains("intermediates")
    }

    @Test
    fun testCustomAbiTargetFilterConfiguration() {
        val variantPathHelper = VariantPathHelper(
                buildDirectory,
                variantDslInfo,
                dslServices
        )
        doReturn("x86,armeabi-v7a").whenever(projectOptions).get(StringOption.IDE_BUILD_TARGET_ABI)
        doReturn("hdpi").whenever(projectOptions).get(StringOption.IDE_BUILD_TARGET_DENSITY)
        Truth.assertThat(variantPathHelper.targetFilterConfigurations)
                .containsExactly(
                        FilterConfigurationImpl(FilterConfiguration.FilterType.ABI, "x86,armeabi-v7a"),
                        FilterConfigurationImpl(FilterConfiguration.FilterType.DENSITY, "hdpi"))
    }

    @Test
    fun testCustomAPIBuildLocation() {
        val variantPathHelper = VariantPathHelper(
            buildDirectory,
            variantDslInfo,
            dslServices
        )
        doReturn(21).whenever(projectOptions).get(IntegerOption.IDE_TARGET_DEVICE_API)
        Truth.assertThat(variantPathHelper.apkLocation.absolutePath).contains("intermediates")
    }

    @Test
    fun testIdeBuildLocation() {
        val variantPathHelper = VariantPathHelper(
            buildDirectory,
            variantDslInfo,
            dslServices
        )
        // necessary, otherwise mockito will return 0.
        doReturn(null).whenever(projectOptions).get(IntegerOption.IDE_TARGET_DEVICE_API)
        doReturn(true).whenever(projectOptions).get(BooleanOption.IDE_INVOKED_FROM_IDE)
        Truth.assertThat(variantPathHelper.apkLocation.absolutePath).contains("outputs")
    }

    @Test
    fun testNormalBuildLocation() {
        val variantPathHelper = VariantPathHelper(
            buildDirectory,
            variantDslInfo,
            dslServices
        )
        // necessary, otherwise mockito will return 0.
        doReturn(null).whenever(projectOptions).get(IntegerOption.IDE_TARGET_DEVICE_API)
        Truth.assertThat(variantPathHelper.apkLocation.absolutePath).contains("outputs")
    }
}
