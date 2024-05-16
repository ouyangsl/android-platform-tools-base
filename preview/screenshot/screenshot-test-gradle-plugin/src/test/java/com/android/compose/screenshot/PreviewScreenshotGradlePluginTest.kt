/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.compose.screenshot

import com.android.build.api.AndroidPluginVersion
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.gradle.api.AndroidBasePlugin
import com.android.testutils.MockitoKt.argumentCaptor
import com.android.testutils.MockitoKt.capture
import com.android.testutils.MockitoKt.eq
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Action
import org.gradle.api.Project
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

/**
 * Unit tests for [PreviewScreenshotGradlePlugin]
 */
class PreviewScreenshotGradlePluginTest {
    @get:Rule
    val mockitoJUnitRule: MockitoRule = MockitoJUnit.rule()
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    lateinit var mockProject: Project
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    lateinit var mockAndroidPlugin: AndroidComponentsExtension<*, *, *>
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    lateinit var mockCommonExtension: CommonExtension<*, *, *, *, *, *>
    @Before
    fun setupMocks() {
        `when`(mockProject.extensions.getByType(eq(AndroidComponentsExtension::class.java))).thenReturn(mockAndroidPlugin)
        `when`(mockProject.extensions.getByType(eq(CommonExtension::class.java))).thenReturn(mockCommonExtension)
        `when`(mockProject.findProperty(PreviewScreenshotGradlePlugin.ST_SOURCE_SET_ENABLED)).thenReturn(true)
    }
    private fun applyScreenshotPlugin(
            agpVersion: AndroidPluginVersion = AndroidPluginVersion(8, 1)) {
        `when`(mockAndroidPlugin.pluginVersion).thenReturn(agpVersion)
        val plugin = PreviewScreenshotGradlePlugin()

        plugin.apply(mockProject)
        val captor = argumentCaptor<Action<AndroidBasePlugin>>()
        verify(mockProject.plugins, atLeastOnce())
                .withType(eq(AndroidBasePlugin::class.java), capture(captor))
        captor.value.execute(AndroidBasePlugin())
    }
    @Test
    fun agpVersionCheck() {
        val unsupportedVersionsTooOld = listOf(
                AndroidPluginVersion(8, 5, 0).alpha(8),
                AndroidPluginVersion(8, 4),
        )
        val supportedVersions = listOf(
                AndroidPluginVersion(8, 5).dev(),
                AndroidPluginVersion(8, 5, 0).beta(1),
                AndroidPluginVersion(8, 6, 0).alpha(1),
        )
        val unsupportedVersionsTooNew = listOf(
            AndroidPluginVersion(8, 7, 0).alpha(1),
            AndroidPluginVersion(8, 7),
        )
        unsupportedVersionsTooOld.forEach {
            val e = assertThrows(IllegalStateException::class.java) {
                applyScreenshotPlugin(it)
            }
            assertThat(e).hasMessageThat()
                    .contains("requires Android Gradle plugin version between 8.5.0-beta01 and 8.6.")
        }
        unsupportedVersionsTooNew.forEach {
            val e = assertThrows(IllegalStateException::class.java) {
                applyScreenshotPlugin(it)
            }
            assertThat(e).hasMessageThat()
                .contains("requires Android Gradle plugin version between 8.5.0-beta01 and 8.6.")
        }
        supportedVersions.forEach {
            applyScreenshotPlugin(it)
        }
    }
}

