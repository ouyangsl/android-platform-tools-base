/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.tests

import com.android.build.api.attributes.ProductFlavorAttr
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.fixture.TestConstants
import com.android.build.gradle.internal.fixture.TestProjects
import com.android.build.gradle.internal.fixture.createAndConfig
import com.android.build.gradle.internal.plugins.AppPlugin
import com.android.build.gradle.internal.plugins.runAfterEvaluate
import com.google.common.truth.Truth
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test to validate that the configurations have the right flavor attributes based on the DSL
 * usage.
 */
@RunWith(Parameterized::class)
class FlavorSelectionTest(val variantApi: VariantApiType) {
    @get:Rule val projectDirectory = TemporaryFolder()

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun variantAPI() = VariantApiType.values()
    }

    private lateinit var project: Project
    private lateinit var plugin: AppPlugin
    private lateinit var android: AppExtension
    private lateinit var variantConfiguration : Configuration
    private lateinit var attributeKeys: MutableSet<Attribute<*>>

    @Before
    fun setUp() {
        project = TestProjects.builder(projectDirectory.newFolder("project").toPath())
                .withPlugin(TestProjects.Plugin.APP)
                .build()
        android = project.extensions.getByType(TestProjects.Plugin.APP.extensionClass) as AppExtension
        android.setCompileSdkVersion(TestConstants.COMPILE_SDK_VERSION)
        android.buildToolsVersion = TestConstants.BUILD_TOOL_VERSION
        android.namespace = "com.example.namespace"
        plugin = project.plugins.getPlugin(TestProjects.Plugin.APP.pluginClass) as AppPlugin

        // manually call the DSL to configure the project.

        // set some flavor selection on default config. Some that will be only applied here,
        // and some that will be overriden in different ways.
        val defaultConfig = android.defaultConfig
        defaultConfig.missingDimensionStrategy("default", "defaultValue")
        defaultConfig.missingDimensionStrategy("flavor", "defaultValue")
        defaultConfig.missingDimensionStrategy("variant", "defaultValue")

        // add selection on flavors
        android.flavorDimensions("dimension")
        android.productFlavors.createAndConfig("flavor") {
            missingDimensionStrategy("flavor", "other-flavor")
            missingDimensionStrategy("flavor-only", "other-flavor-only")
        }

        // now use the variant API to configure a specific variant
        when (variantApi) {
            VariantApiType.OLD -> {
                android.applicationVariants.all {
                    it.missingDimensionStrategy("variant", "variant")
                    it.missingDimensionStrategy("variant-only", "variant-only")
                }
            }
            VariantApiType.NEW -> {
                val androidComponents = project.extensions.getByType(
                    ApplicationAndroidComponentsExtension::class.java)
                androidComponents.onVariants {
                    it.missingDimensionStrategy("variant", "variant")
                    it.missingDimensionStrategy("variant-only", "variant-only")
                }
            }
        }

        plugin.runAfterEvaluate(project)

        // get the configuration for a given variant
        variantConfiguration = android.applicationVariants.stream()
                .filter { it.name == "flavorDebug"}
                .map { it.compileConfiguration}
                .findAny()?.get() ?: throw RuntimeException("can't find flavorDebug")

        attributeKeys = variantConfiguration.attributes.keySet()

    }

    @Test
    fun testBasicAttribute() {
        checkAttribute("default", "defaultValue")
    }

    @Test
    fun testFlavorAttribute() {
        checkAttribute("flavor", "flavor")
        checkAttribute("flavor-only", "flavor")

        // TODO: we should check the strategies but there's no API for it right now.
    }

    @Test
    fun testVariantAttribute() {
        checkAttribute("variant", "flavorDebug")
        checkAttribute("variant-only", "flavorDebug")
    }

    private fun checkAttribute(dimension: String, value: String) {
        // check the key is present.
        val key = ProductFlavorAttr.of(dimension)
        Truth.assertThat(attributeKeys).contains(key as Attribute<*>)

        // check the value is correct
        val attrValue = variantConfiguration.attributes.getAttribute(key)
        Truth.assertThat(attrValue).named("Value of attribute $dimension").isNotNull()
        Truth.assertThat(attrValue!!.name).named("Value of attribute $dimension").isEqualTo(value)
    }
}
