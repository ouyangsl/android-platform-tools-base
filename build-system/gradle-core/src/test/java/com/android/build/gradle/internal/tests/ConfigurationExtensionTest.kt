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

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.fixture.TestConstants
import com.android.build.gradle.internal.fixture.TestProjects
import com.android.build.gradle.internal.fixture.createAndConfig
import com.android.build.gradle.internal.plugins.BasePlugin
import com.android.build.gradle.internal.plugins.runAfterEvaluate
import com.android.ide.common.util.multimapOf
import com.android.ide.common.util.multimapWithSingleKeyOf
import com.google.common.collect.ImmutableSetMultimap
import com.google.common.collect.Multimap
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests that specific configurations properly extendFrom others.
 */
@RunWith(Parameterized::class)
class ConfigurationExtensionTest(private val pluginType: TestProjects.Plugin) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "plugin_{0}")
        fun params(): Collection<TestProjects.Plugin> = listOf(
                TestProjects.Plugin.APP, TestProjects.Plugin.LIBRARY)
    }

    @get:Rule
    val projectDirectory = TemporaryFolder()

    private lateinit var project: Project
    private lateinit var plugin: BasePlugin<*, *, *, *, *, *, *, *, *, *, *, *>
    private lateinit var android: BaseExtension
    private lateinit var configExtensionMap: Multimap<String, String>

    // basic relationship
    private val appBasics = multimapOf("implementation" to "api")

    private val libBasics = multimapOf("implementation" to "api")

    // variant to basic relationship
    private val compileToRaw = multimapWithSingleKeyOf(
            "lollipopDemoDebugCompileClasspath",
            "api",
            "compileOnlyApi",
            "implementation",
            "compileOnly",
            "lollipopImplementation",
            "debugImplementation",
            "demoImplementation",
            "lollipopDemoImplementation",
            "lollipopDemoDebugImplementation")

    private val runtimeToRaw = multimapWithSingleKeyOf(
            "lollipopDemoDebugRuntimeClasspath",
            "api",
            "implementation",
            "runtimeOnly",
            "lollipopImplementation",
            "debugImplementation",
            "demoImplementation",
            "lollipopDemoImplementation",
            "lollipopDemoDebugImplementation")

    /** Compile Test to prod relationship, exhaustively listed. */
    private val testCompileToRaw = multimapWithSingleKeyOf(
            "lollipopDemoDebugUnitTestCompileClasspath",
            "api",
            "debugApi",
            "debugImplementation",
            "demoApi",
            "demoImplementation",
            "implementation",
            "lollipopApi",
            "lollipopDemoApi",
            "lollipopDemoDebugApi",
            "lollipopDemoDebugImplementation",
            "lollipopDemoImplementation",
            "lollipopImplementation",
            "testCompileOnly",
            "testDebugCompileOnly",
            "testDebugImplementation",
            "testDemoCompileOnly",
            "testDemoImplementation",
            "testImplementation",
            "testLollipopCompileOnly",
            "testLollipopDemoCompileOnly",
            "testLollipopDemoDebugCompileOnly",
            "testLollipopDemoDebugImplementation",
            "testLollipopDemoImplementation",
            "testLollipopImplementation")

    /**
     * Runtime Test to prod relationship, exhaustively listed.
     *
     * Modelled after
     * [https://docs.gradle.org/current/userguide/java_library_plugin.html#sec:java_library_configurations_graph]
     * Note that `*testCompileClasspath` extends `implementation` directly, rather than via
     * `*testImplementation`.
     */
    private val testRuntimeToRaw = multimapWithSingleKeyOf(
            "lollipopDemoDebugUnitTestRuntimeClasspath",
            "testDebugImplementation",
            "testDebugRuntimeOnly",
            "testDemoImplementation",
            "testDemoRuntimeOnly",
            "testImplementation",
            "testLollipopDemoDebugImplementation",
            "testLollipopDemoDebugRuntimeOnly",
            "testLollipopDemoImplementation",
            "testLollipopDemoRuntimeOnly",
            "testLollipopImplementation",
            "testLollipopRuntimeOnly",
            "testRuntimeOnly")

    // forbidden relationship
    private val forbiddenVariantToRaw = multimapOf(
            "lollipopDemoDebugCompileClasspath" to "runtimeOnly",
            "lollipopDemoDebugRuntimeClasspath" to "compileOnly")

    @Before
    fun setUp() {
        project = TestProjects.builder(projectDirectory.newFolder("project").toPath())
                .withPlugin(pluginType)
                .build()
        android = project.extensions.getByType(pluginType.extensionClass) as BaseExtension
        android.setCompileSdkVersion(TestConstants.COMPILE_SDK_VERSION)
        android.buildToolsVersion = TestConstants.BUILD_TOOL_VERSION
        android.namespace = "com.example.namespace"
        plugin = project.plugins.getPlugin(pluginType.pluginClass)
            as BasePlugin<*,*,*,*,*,*,*,*,*,*,*,*>

        // manually call the DSL to configure the project.
        android.flavorDimensions("api", "mode")

        android.productFlavors.createAndConfig("demo") {
            dimension = "mode"
        }

        android.productFlavors.createAndConfig("full") {
            dimension = "mode"
        }

        android.productFlavors.createAndConfig("mnc") {
            dimension = "api"
        }

        android.productFlavors.createAndConfig("lollipop") {
            dimension = "api"
        }

        plugin.runAfterEvaluate(project)

        configExtensionMap = getConfigurationExtensions()
}

    @Test
    fun testBasicRelationships() {
        checkValidExtensions(
                if (pluginType == TestProjects.Plugin.APP) appBasics else libBasics,
                configExtensionMap)
    }

    @Test
    fun testVariantToRawRelationships() {
        checkValidExtensions(compileToRaw, configExtensionMap)
        checkValidExtensions(runtimeToRaw, configExtensionMap)
    }

    @Test
    fun testForbiddenRelationships() {
        checkInvalidExtensions(forbiddenVariantToRaw, configExtensionMap)
    }

    @Test
    fun testMainTestRelationships() {
        checkValidExtensionsCensus(testCompileToRaw)
        checkValidExtensionsCensus(testRuntimeToRaw)
    }

    private fun getConfigurationExtensions(): Multimap<String, String> {
        val map = ImmutableSetMultimap.builder<String, String>()
        for (config in project.configurations) {
            fillConfigMap(map, config.name, config.extendsFrom)
        }
        return map.build()
    }

    private fun fillConfigMap(
            map: ImmutableSetMultimap.Builder<String, String>,
            name: String,
            children: Set<Configuration>) {
        for (config in children) {
            map.put(name, config.name)
            fillConfigMap(map, name, config.extendsFrom)
        }
    }

    private fun checkValidExtensions(
            expected: Multimap<String, String>,
            actual: Multimap<String, String>) {

        for ((key, value) in expected.entries()) {
            assertThat(actual).containsEntry(key, value)
        }
    }

    private fun checkValidExtensionsCensus(expected: Multimap<String, String>) {
        for (key in expected.keys()) {
            assertThat(configExtensionMap.get(key))
                    .named("Configuration $key extends")
                    .containsExactlyElementsIn(expected.get(key))
        }
    }

    private fun checkInvalidExtensions(
            expected: Multimap<String, String>,
            actual: Multimap<String, String>) {

        for ((key, value) in expected.entries()) {
            Truth.assertThat(actual).doesNotContainEntry(key, value)
        }
    }
}
