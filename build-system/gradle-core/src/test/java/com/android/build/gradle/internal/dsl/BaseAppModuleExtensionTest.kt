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
package com.android.build.gradle.internal.dsl

import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.dsl.decorator.androidPluginDslDecorator
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.ProjectFactory
import com.android.build.gradle.internal.plugins.DslContainerProvider
import com.android.build.gradle.internal.scope.DelayedActionsExecutor
import com.android.build.gradle.internal.services.AndroidLocationsBuildService
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.createDslServices
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfig
import com.android.build.gradle.internal.variant.LegacyVariantInputManager
import com.android.builder.core.ComponentTypeImpl
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.GradleBuildProject
import groovy.util.Eval
import org.gradle.api.NamedDomainObjectContainer
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Tests for [BaseAppModuleExtension]
 */
class BaseAppModuleExtensionTest {
    private lateinit var appExtension: BaseAppModuleExtension
    private lateinit var statsBuilder: GradleBuildProject.Builder
    @Suppress("UNCHECKED_CAST")
    @Before
    fun setUp() {
        val sdkComponents = mock<SdkComponentsBuildService>()
        val dslServices = createDslServices(sdkComponents = FakeGradleProvider(sdkComponents))
        AndroidLocationsBuildService.RegistrationAction(ProjectFactory.project).execute()
        val variantInputModel = LegacyVariantInputManager(
            dslServices,
            ComponentTypeImpl.BASE_APK,
            SourceSetManager(
                ProjectFactory.project,
                false,
                dslServices,
                DelayedActionsExecutor()
            )
        )

        val extension = androidPluginDslDecorator.decorate(ApplicationExtensionImpl::class)
            .getDeclaredConstructor(DslServices::class.java, DslContainerProvider::class.java)
            .newInstance(dslServices, variantInputModel)
        statsBuilder = GradleBuildProject.newBuilder()
        appExtension = BaseAppModuleExtension(
            dslServices,
            mock<BootClasspathConfig>(),
            mock<NamedDomainObjectContainer<BaseVariantOutput>>(),
            variantInputModel.sourceSetManager,
            mock<ExtraModelInfo>(),
            extension,
            statsBuilder
        )
    }

    @Test
    fun `check dynamic features`() {
        appExtension.dynamicFeatures += ":df1"
        assertThat(appExtension.dynamicFeatures).containsExactly(":df1")
        Eval.me("android", appExtension, "android.dynamicFeatures = [':other']")
        assertThat(appExtension.dynamicFeatures).containsExactly(":other")
    }

    @Test
    fun `check asset packs`() {
        appExtension.assetPacks += ":ap"
        assertThat(appExtension.assetPacks).containsExactly(":ap")
        Eval.me("android", appExtension, "android.assetPacks = [':other']")
        assertThat(appExtension.assetPacks).containsExactly(":other")
    }

    @Test
    fun `check old variant api tracking`() {
        assertThat(statsBuilder.hasOldVariantApiInUse()).isFalse()
        Eval.me("android", appExtension, "android.applicationVariants.all { }")
        assertThat(statsBuilder.oldVariantApiInUse).isTrue()
    }
}
