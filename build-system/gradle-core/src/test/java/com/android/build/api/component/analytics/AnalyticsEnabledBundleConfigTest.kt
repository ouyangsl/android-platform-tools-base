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

import com.android.build.api.variant.BundleConfig
import com.android.build.api.variant.CodeTransparency
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

class AnalyticsEnabledBundleConfigTest {
    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    private val delegate: BundleConfig = mock()

    private val stats = GradleBuildVariant.newBuilder()
    private val proxy: AnalyticsEnabledBundleConfig by lazy {
        AnalyticsEnabledBundleConfig(delegate, stats, FakeObjectFactory.factory)
    }

    @Test
    fun testCodeTransparency() {
        val codeTransparency = mock<CodeTransparency>()
        whenever(delegate.codeTransparency).thenReturn(codeTransparency)

        Truth.assertThat((proxy.codeTransparency as AnalyticsEnabledCodeTransparency).delegate)
            .isEqualTo(codeTransparency)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.GET_CODE_TRANSPARENCY_VALUE)
        verify(delegate, times(1))
            .codeTransparency
    }

    @Test
    fun testAddMetadataFile() {
        val provider = mock<Provider<RegularFile>>()
        proxy.addMetadataFile(
            "com.android.build",
            provider,
        )

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.BUNDLE_CONFIG_ADD_METADATA_VALUE)
        verify(delegate, times(1))
            .addMetadataFile("com.android.build", provider )
    }
}
