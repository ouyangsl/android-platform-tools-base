/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.api.component.analytics

import com.android.build.api.variant.SourceDirectories
import com.android.build.api.variant.Sources
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

class AnalyticsEnabledSourcesTest {
    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    private val delegate: Sources = mock()

    private val stats = GradleBuildVariant.newBuilder()
    private val proxy: AnalyticsEnabledSources by lazy {
        object: AnalyticsEnabledSources(delegate, stats, FakeObjectFactory.factory) {}
    }

    @Test
    fun getJava() {
        testAnalytics<SourceDirectories.Flat>(
            Sources::java,
            VariantPropertiesMethodType.SOURCES_JAVA_ACCESS_VALUE
        )
    }

    @Test
    fun getKotlin() {
        testAnalytics<SourceDirectories.Flat>(
            Sources::kotlin,
            VariantPropertiesMethodType.SOURCES_KOTLIN_ACCESS_VALUE
        )
    }

    @Test
    fun getRenderscript() {
        testAnalytics<SourceDirectories.Flat>(
            Sources::renderscript,
            VariantPropertiesMethodType.SOURCES_RENDERSCRIPT_ACCESS_VALUE
        )
    }

    @Test
    fun getMlModels() {
        testAnalytics<SourceDirectories.Layered>(
            Sources::mlModels,
            VariantPropertiesMethodType.SOURCES_ML_MODELS_ACCESS_VALUE
        )
    }

    @Test
    fun getAidl() {
        testAnalytics<SourceDirectories.Flat>(
            Sources::aidl,
            VariantPropertiesMethodType.SOURCES_AIDL_ACCESS_VALUE
        )
    }

    @Test
    fun getRes() {
        testAnalytics<SourceDirectories.Layered>(
            Sources::res,
            VariantPropertiesMethodType.SOURCES_RES_ACCESS_VALUE
        )
    }

    @Test
    fun getJniLibs() {
        testAnalytics<SourceDirectories.Layered>(
            Sources::jniLibs,
            VariantPropertiesMethodType.SOURCES_JNI_ACCESS_VALUE
        )
    }

    @Test
    fun getShaders() {
        testAnalytics<SourceDirectories.Layered>(
            Sources::shaders,
            VariantPropertiesMethodType.SOURCES_SHADERS_ACCESS_VALUE
        )
    }

    @Test
    fun getAssets() {
        testAnalytics<SourceDirectories.Layered>(
            Sources::assets,
            VariantPropertiesMethodType.SOURCES_ASSETS_ACCESS_VALUE
        )
    }

    @Test
    fun getResources() {
        testAnalytics<SourceDirectories.Flat>(
            Sources::resources,
            VariantPropertiesMethodType.SOURCES_RESOURCES_ACCESS_VALUE
        )
    }

    @Test
    fun testNullableApis() {
        Truth.assertThat(proxy.res).isNull()
        Truth.assertThat(proxy.assets).isNull()
        Truth.assertThat(proxy.mlModels).isNull()
        Truth.assertThat(proxy.aidl).isNull()
        Truth.assertThat(proxy.baselineProfiles).isNull()
        Truth.assertThat(proxy.jniLibs).isNull()
        Truth.assertThat(proxy.shaders).isNull()
        Truth.assertThat(proxy.renderscript).isNull()
    }

    private inline fun <reified T: SourceDirectories> testAnalytics(
        accessor: (sources: Sources) -> T?,
        analyticsEnumValue: Int,
    ) {
        val mockedType: T = mock()
        whenever(accessor(delegate)).thenReturn(mockedType)

        val sourcesProxy = accessor(proxy)
        Truth.assertThat(sourcesProxy is AnalyticsEnabledSourceDirectories).isTrue()
        Truth.assertThat((sourcesProxy as AnalyticsEnabledSourceDirectories).delegate)
            .isEqualTo(mockedType)

        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(analyticsEnumValue)
        accessor(verify(delegate, times(1)))
        verifyNoMoreInteractions(delegate)
    }

}
