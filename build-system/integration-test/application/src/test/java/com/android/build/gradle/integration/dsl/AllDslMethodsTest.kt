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

package com.android.build.gradle.integration.dsl

import com.android.build.api.dsl.ApplicationBuildType
import com.android.build.api.dsl.ApplicationDefaultConfig
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.BaselineProfile
import com.android.build.api.dsl.DefaultConfig
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidDeviceTest
import com.android.build.api.dsl.KotlinMultiplatformAndroidHostTest
import com.android.build.api.dsl.LibraryBuildType
import com.android.build.api.dsl.LibraryDefaultConfig
import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.utils.FileUtils
import com.android.utils.appendCapitalized
import com.android.utils.usLocaleDecapitalize
import com.google.common.collect.ListMultimap
import com.google.common.truth.Truth
import org.gradle.api.JavaVersion
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.process.CommandLineArgumentProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * A test that generates projects with all different dsl values applicable for a plugin and makes
 * sure that configuration is successful.
 *
 * The point of this test is to make sure dsl invocation (even with placeholder values) shouldn't
 * break configuration.
 */
@RunWith(Parameterized::class)
class AllDslMethodsTest(
    private val pluginId: String,
    private val extensionClass: Class<*>,
    private val extensionAccessor: String,
    private val buildTypeType: Class<*>?,
    private val defaultConfigType: Class<*>?,
) {
    companion object {
        @Parameterized.Parameters(name = "plugin_{0}")
        @JvmStatic
        fun parameters() = listOf(
            arrayOf(
                "com.android.kotlin.multiplatform.library",
                KotlinMultiplatformAndroidLibraryExtension::class.java,
                "kotlin.androidLibrary",
                null,
                null
            ),
            arrayOf(
                "com.android.library",
                LibraryExtension::class.java,
                "android",
                LibraryBuildType::class.java,
                LibraryDefaultConfig::class.java
            ),
            arrayOf(
                "com.android.application",
                ApplicationExtension::class.java,
                "android",
                ApplicationBuildType::class.java,
                ApplicationDefaultConfig::class.java
            ),
        )
    }

    private val allProjects = mutableListOf<String>()

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestApp(
            MultiModuleTestProject.builder().build()
        )
        .withKotlinGradlePlugin(true)
        .create()

    @Before
    fun prepareProject() {
        val generator = DslScriptGenerator(
            buildTypeType, defaultConfigType
        )
        generator.visit(
            extensionClass,
            null,
            extensionAccessor
        )

        for (index in 1..generator.numberOfScriptsGenerated) {
            val projectName = "project$index"
            allProjects.add(projectName)
            TestFileUtils.appendToFile(
                project.settingsFile,
                "include '$projectName'"
            )

            val buildFile = FileUtils.join(
                project.projectDir, projectName, "build.gradle.kts"
            )
            buildFile.parentFile.mkdirs()
            FileUtils.writeToFile(
                buildFile,
                """
                    plugins {
                        id("$pluginId")
                        ${"kotlin(\"multiplatform\")".takeIf { extensionClass == KotlinMultiplatformAndroidLibraryExtension::class.java } ?: ""}
                    }
                """.trimIndent() + generator.getScript(index - 1)
            )
        }
    }

    @Test
    fun configureAllProjects() {
        project
            .executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
            .withFailOnWarning(false)
            .run(
                allProjects.map { projectName -> ":$projectName:clean" }
            )
    }
}


private class DslScriptGenerator(
    private val buildTypeType: Class<*>? = null,
    private val defaultConfigType: Class<*>? = null
) {

    private val allDslValues: MutableList<List<String>> = mutableListOf()

    val numberOfScriptsGenerated: Int
        get() = allDslValues.maxOf { it.size }

    fun getScript(index: Int) = allDslValues.joinToString("\n") {
        it[index.mod(it.size)]
    }

    private fun visitClosure(callChain: String, closureName: String, closureType: Class<*>) {
        val nestedGenerator = DslScriptGenerator()
        nestedGenerator.visit(
            closureType,
            null,
            "this"
        )

        allDslValues.add(
            List(nestedGenerator.numberOfScriptsGenerated) { index ->
                "$callChain.${closureName} {\n" +
                        nestedGenerator.getScript(index).split("\n").joinToString("\n") { "    $it" } +
                        "\n}"
            }
        )
    }

    fun visit(
        dslType: Class<*>,
        genericType: Type?,
        callChain: String
    ) {
        if (DefaultConfig::class.java.isAssignableFrom(dslType) && dslType != defaultConfigType) {
            visit(
                defaultConfigType!!,
                null,
                callChain
            )
            return
        }

        if (typesToIgnore.any { it.isAssignableFrom(dslType) }) {
            if (callChain == "android.buildTypes" && buildTypeType != null) {
                visitClosure(
                    callChain, "getByName(\"debug\")", buildTypeType
                )
            }
            return
        }

        if (mapTypes.any { it.isAssignableFrom(dslType) }) {
            allDslValues.add(
                getPossibleValues(
                    (genericType as ParameterizedType).actualTypeArguments.first() as Class<*>
                ).flatMap { key ->
                    val valueType = genericType.actualTypeArguments[1]

                    if (valueType is ParameterizedType) {
                        if (Collection::class.java.isAssignableFrom(
                                valueType.rawType as Class<*>
                            )) {
                            getPossibleValues(
                                valueType.actualTypeArguments.first() as Class<*>
                            ).map { value ->
                                "$callChain.put($key, listOf($value))"
                            }
                        } else {
                            throw RuntimeException(valueType.typeName)
                        }
                    } else {
                        getPossibleValues(
                            genericType.actualTypeArguments[1] as Class<*>
                        ).map { value ->
                            "$callChain.put($key, $value)"
                        }
                    }
                }
            )
            return
        }
        if (listTypes.any { it.isAssignableFrom(dslType) }) {
            val nestedType = (genericType as ParameterizedType).actualTypeArguments.first() as Class<*>
            if (nestedType !in typesToIgnore) {
                allDslValues.add(
                    getPossibleValues(nestedType).map {
                        "$callChain.add($it)"
                    }
                )
            }
            return
        }

        val closures = mutableListOf<Method>()
        val getters = mutableListOf<Method>()
        val methodsNames = dslType.methods.map { it.name }.toSet()

        fun mapSetterToProperty(name: String): String {
            val propertyName = name.removePrefix("set").usLocaleDecapitalize()
            return if (methodsNames.contains("get".appendCapitalized(propertyName))) {
                propertyName
            } else {
                "is".appendCapitalized(propertyName)
            }
        }

        dslType.methods.sortedBy { it.name }.forEach { method ->
            if (method.toString() in methodsToIgnore ||
                method.annotations.any { it.annotationClass == Deprecated::class }) {
                return@forEach
            }

            val currentCallChain = "$callChain.${method.name}"

            if (method.returnType.name == "void") {
                if (method.name.startsWith("set")) {
                    Truth.assertThat(method.parameters).hasLength(1)

                    getAllValues(
                        method.toString(),
                        method.parameterTypes.first() as Class<*>,
                        "$callChain.${mapSetterToProperty(method.name)} ="
                    )
                } else if (method.parameters.size == 1 &&
                    method.parameterTypes.first() == Function1::class.java) {
                    closures.add(method)
                    return@forEach
                }
            } else if (method.parameters.isEmpty()) {
                Truth.assertWithMessage(
                    "Unknown $currentCallChain"
                ).that(method.name.startsWith("get") ||
                        method.name.startsWith("is")).isTrue()
                if (!endPoints.contains(method.returnType)) {
                    getters.add(method)
                    visit(
                        method.returnType,
                        method.genericReturnType,
                        "$callChain.${method.name.removePrefix("get").usLocaleDecapitalize()}" +
                                ("?".takeIf { method.toString() in nullableGetters } ?: "")
                    )
                }
            } else {
                throw RuntimeException("Unknown $currentCallChain")
            }
        }

        closures.forEach { closure ->
            if (!getters.any { getter ->
                    closure.name == getter.name.removePrefix("get").usLocaleDecapitalize()
                }) {
                if (closure.name in closuresToManuallyVisit) {
                    visitClosure(callChain, closure.name, closuresToManuallyVisit[closure.name]!!)
                } else {
                    throw RuntimeException(
                        "Found a closure ($closure) without a backing getter."
                    )
                }
            }
        }
    }

    private fun getPossibleValues(
        valueType: Class<*>
    ): List<String> {
        return when (valueType) {
            Any::class.java, String::class.java -> listOf("\"${nextString()}\"")
            Int::class.java, Integer::class.java -> { listOf(nextInt().toString()) }
            Boolean::class.java, java.lang.Boolean::class.java -> { listOf("true", "false") }
            File::class.java -> {
                listOf(
                    "File(\"${FileUtils.escapeSystemDependentCharsIfNecessary(nextFile().absolutePath)}\")"
                )
            }
            JavaVersion::class.java -> { listOf(JavaVersion::class.java.name + ".VERSION_11") }
            else -> throw RuntimeException(valueType.name)
        }
    }

    private fun getAllValues(
        methodName: String,
        valueType: Class<*>,
        callChain: String
    ) {
        if (methodsWithReturnValue[methodName] != null) {
            allDslValues.add(listOf("$callChain \"${methodsWithReturnValue[methodName]}\""))
            return
        }

        allDslValues.add(
            getPossibleValues(valueType).map {
                "$callChain $it"
            }
        )
    }

    companion object {
        private var intIterator = 0

        private fun nextString() = intIterator++.toString()
        private fun nextInt() = intIterator++
        private fun nextFile() = File(FileUtils.join(nextString(), nextString(), nextString()))

        private val closuresToManuallyVisit = mapOf(
            "withAndroidTestOnJvm" to KotlinMultiplatformAndroidHostTest::class.java,
            "withAndroidTestOnDevice" to KotlinMultiplatformAndroidDeviceTest::class.java,
            "withHostTest" to KotlinMultiplatformAndroidHostTest::class.java,
            "withDeviceTest" to KotlinMultiplatformAndroidDeviceTest::class.java,
            "baselineProfile" to BaselineProfile::class.java
        )

        private val endPoints = listOf(
            String::class.java,
            Int::class.java,
            Integer::class.java,
            Boolean::class.java,
            java.lang.Boolean::class.java,
            File::class.java,
            JavaVersion::class.java
        )

        private val mapTypes = setOf(
            Map::class.java,
            MapProperty::class.java,
            ListMultimap::class.java
        )

        private val listTypes = setOf(
            MutableCollection::class.java,
            ListProperty::class.java
        )

        private val typesToIgnore = setOf(
            NamedDomainObjectCollection::class.java,
            ExtensionContainer::class.java,
            CommandLineArgumentProvider::class.java,
            DirectoryProperty::class.java,
            RegularFileProperty::class.java
        )

        private val methodsWithReturnValue = mapOf(
            "public abstract void com.android.build.api.dsl.CommonExtension.setNamespace(java.lang.String)" to "com.example.foo"
        )

        private val methodsToIgnore = setOf(
            // Intentionally ignored
            "public abstract com.android.build.api.dsl.HasConfigurableValue com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension.withAndroidTestOnJvmBuilder(kotlin.jvm.functions.Function1)",
            "public abstract com.android.build.api.dsl.HasConfigurableValue com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension.withAndroidTestOnDeviceBuilder(kotlin.jvm.functions.Function1)",
            "public abstract com.android.build.api.dsl.HasConfigurableValue com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension.withHostTestBuilder(kotlin.jvm.functions.Function1)",
            "public abstract com.android.build.api.dsl.HasConfigurableValue com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension.withDeviceTestBuilder(kotlin.jvm.functions.Function1)",

            "public abstract com.android.build.api.dsl.LibraryPublishing com.android.build.api.dsl.LibraryExtension.getPublishing()",
            "public abstract void com.android.build.api.dsl.LibraryExtension.publishing(kotlin.jvm.functions.Function1)",

            "public abstract com.android.build.api.dsl.ManagedDevices com.android.build.api.dsl.KotlinMultiplatformAndroidDeviceTest.getManagedDevices()",
            "public abstract void com.android.build.api.dsl.KotlinMultiplatformAndroidDeviceTest.managedDevices(kotlin.jvm.functions.Function1)",

            "public abstract void com.android.build.api.dsl.Optimization.keepRules(kotlin.jvm.functions.Function1)",

            "public abstract void com.android.build.api.dsl.TestedExtension.setTestBuildType(java.lang.String)",
            "public abstract void com.android.build.api.dsl.ApplicationVariantDimension.setSigningConfig(com.android.build.api.dsl.ApkSigningConfig)",
            "public abstract void com.android.build.api.dsl.LibraryVariantDimension.setSigningConfig(com.android.build.api.dsl.ApkSigningConfig)",
            "public abstract void com.android.build.api.dsl.BuildType.setShrinkResources(boolean)",
            "public abstract void com.android.build.api.dsl.BuildFeatures.setRenderScript(java.lang.Boolean)",

            // Ignored for configuration time checks

            "public abstract void com.android.build.api.dsl.KotlinMultiplatformAndroidDeviceTest.setExecution(java.lang.String)",
            "public abstract void com.android.build.api.dsl.TestOptions.setExecution(java.lang.String)",
            "public abstract void com.android.build.api.dsl.Ndk.setDebugSymbolLevel(java.lang.String)",

            "public abstract void com.android.build.api.dsl.CommonExtension.setCompileSdkPreview(java.lang.String)",

            "public abstract com.android.build.api.dsl.ExternalNativeBuild com.android.build.api.dsl.CommonExtension.getExternalNativeBuild()",
            "public abstract void com.android.build.api.dsl.CommonExtension.externalNativeBuild(kotlin.jvm.functions.Function1)",
            "public abstract void com.android.build.api.dsl.CommonExtension.setNdkVersion(java.lang.String)",
            "public abstract void com.android.build.api.dsl.CommonExtension.setNdkPath(java.lang.String)",

            "public abstract void com.android.build.api.dsl.TestOptions.setTargetSdk(java.lang.Integer)",
            "public abstract void com.android.build.api.dsl.TestOptions.setTargetSdkPreview(java.lang.String)",

            "public abstract java.util.Set com.android.build.api.dsl.ApplicationExtension.getAssetPacks()",
            "public abstract java.util.Set com.android.build.api.dsl.ApplicationExtension.getDynamicFeatures()",

            "public abstract void com.android.build.api.dsl.ApplicationAndroidResources.setGenerateLocaleConfig(boolean)",

            // Unnatural DSL

            "public abstract void com.android.build.api.dsl.KotlinMultiplatformAndroidHostTest.all(kotlin.jvm.functions.Function1)",
            "public abstract void com.android.build.api.dsl.UnitTestOptions.all(kotlin.jvm.functions.Function1)",

            "public abstract java.util.Collection com.android.build.api.dsl.Splits.getDensityFilters()",
            "public abstract java.util.Collection com.android.build.api.dsl.Splits.getAbiFilters()",

            "public abstract java.lang.Object com.android.build.api.dsl.VariantDimension.proguardFile(java.lang.Object)",
            "public abstract java.lang.Object com.android.build.api.dsl.VariantDimension.proguardFiles(java.lang.Object[])",
            "public abstract java.lang.Object com.android.build.api.dsl.VariantDimension.setProguardFiles(java.lang.Iterable)",
            "public abstract java.lang.Object com.android.build.api.dsl.VariantDimension.testProguardFile(java.lang.Object)",
            "public abstract java.lang.Object com.android.build.api.dsl.VariantDimension.testProguardFiles(java.lang.Object[])",
            "public abstract java.lang.Object com.android.build.api.dsl.LibraryVariantDimension.consumerProguardFile(java.lang.Object)",
            "public abstract java.lang.Object com.android.build.api.dsl.LibraryVariantDimension.consumerProguardFiles(java.lang.Object[])",

            "public abstract java.io.File com.android.build.api.dsl.CommonExtension.getDefaultProguardFile(java.lang.String)",

            "public abstract com.android.build.api.dsl.PostProcessing com.android.build.api.dsl.BuildType.getPostprocessing()",
            "public abstract void com.android.build.api.dsl.BuildType.postprocessing(kotlin.jvm.functions.Function1)",
        )

        private val nullableGetters = listOf(
            "public abstract java.util.List com.android.build.api.dsl.Ndk.getLdLibs()",
            "public abstract java.util.List com.android.build.api.dsl.Shaders.getGlslcArgs()",
            "public abstract java.util.Set com.android.build.api.dsl.VectorDrawables.getGeneratedDensities()",
            "public abstract com.android.build.api.dsl.ApkSigningConfig com.android.build.api.dsl.ApplicationVariantDimension.getSigningConfig()",
            "public abstract com.android.build.api.dsl.ApkSigningConfig com.android.build.api.dsl.LibraryVariantDimension.getSigningConfig()",
        )
    }
}
