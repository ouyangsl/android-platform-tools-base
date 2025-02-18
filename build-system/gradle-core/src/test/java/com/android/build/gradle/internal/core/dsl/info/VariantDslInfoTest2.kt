/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.core.dsl.info

import com.android.build.api.variant.ComponentIdentity
import com.android.build.gradle.internal.core.dsl.AndroidTestComponentDslInfo
import com.android.build.gradle.internal.core.dsl.ApplicationVariantDslInfo
import com.android.build.gradle.internal.core.dsl.ComponentDslInfo
import com.android.build.gradle.internal.core.dsl.impl.AndroidTestComponentDslInfoImpl
import com.android.build.gradle.internal.core.dsl.impl.ApplicationVariantDslInfoImpl
import com.android.build.gradle.internal.core.dsl.impl.DynamicFeatureVariantDslInfoImpl
import com.android.build.gradle.internal.core.dsl.impl.LibraryVariantDslInfoImpl
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.InternalApplicationExtension
import com.android.build.gradle.internal.dsl.InternalDynamicFeatureExtension
import com.android.build.gradle.internal.dsl.InternalLibraryExtension
import com.android.build.gradle.internal.dsl.InternalTestedExtension
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.manifest.ManifestData
import com.android.build.gradle.internal.manifest.ManifestDataProvider
import com.android.build.gradle.internal.publishing.VariantPublishingInfo
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.createDslServices
import com.android.build.gradle.internal.services.createProjectServices
import com.android.build.gradle.internal.services.createVariantPropertiesApiServices
import com.android.build.gradle.internal.variant.Container
import com.android.build.gradle.internal.variant.ContainerImpl
import com.android.builder.core.BuilderConstants
import com.android.builder.core.ComponentType
import com.android.builder.core.ComponentTypeImpl
import com.android.builder.dexing.DexingType
import com.android.testutils.AbstractBuildGivenBuildExpectTest
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(Parameterized::class)
class VariantDslInfoTest2(
    private val componentType: ComponentType
): AbstractBuildGivenBuildExpectTest<
            VariantDslInfoTest2.GivenData,
            VariantDslInfoTest2.ResultData>() {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "Component type: {0}")
        fun parameters() = listOf(
            ComponentTypeImpl.BASE_APK,
            ComponentTypeImpl.OPTIONAL_APK,
            ComponentTypeImpl.LIBRARY
        )

        private const val DEFAULT_NAMESPACE = "com.example.namespace"
    }

    @Test
    fun `versionCode from defaultConfig`() {
        assumeTrue(componentType == ComponentTypeImpl.BASE_APK)
        given {
            // no specific manifest info
            manifestData {  }

            defaultConfig {
                versionCode = 12
            }
        }

        expect {
            versionCode = 12
        }
    }

    @Test
    fun `versionCode from manifest`() {
        assumeTrue(componentType == ComponentTypeImpl.BASE_APK)
        given {
            manifestData {
                versionCode = 12
            }
        }

        expect {
            versionCode = 12
        }
    }

    @Test
    fun `versionCode defaultConfig overrides manifest`() {
        assumeTrue(componentType == ComponentTypeImpl.BASE_APK)
        given {
            manifestData {
                versionCode = 12
            }

            defaultConfig {
                versionCode = 13
            }
        }

        expect {
            versionCode = 13
        }
    }

    @Test
    fun `versionCode from flavor overrides all`() {
        assumeTrue(componentType == ComponentTypeImpl.BASE_APK)
        given {
            manifestData {
                versionCode = 12
            }

            defaultConfig {
                versionCode = 13
            }
            productFlavors {
                create("higherPriority") {
                    versionCode = 20
                }
                create("lowerPriority") {
                    versionCode = 14
                }
            }
        }

        expect {
            versionCode = 20
        }
    }

    @Test
    fun `versionName from defaultConfig`() {
        assumeTrue(componentType == ComponentTypeImpl.BASE_APK)
        given {
            // no specific manifest info
            manifestData { }

            defaultConfig {
                versionName = "foo"
            }
        }

        expect {
            versionName = "foo"
        }
    }

    @Test
    fun `versionName from manifest`() {
        assumeTrue(componentType == ComponentTypeImpl.BASE_APK)
        given {
            manifestData {
                versionName = "foo"
            }
        }

        expect {
            versionName = "foo"
        }
    }

    @Test
    fun `versionName defaultConfig overrides manifest`() {
        assumeTrue(componentType == ComponentTypeImpl.BASE_APK)
        given {
            manifestData {
                versionName = "foo"
            }

            defaultConfig {
                versionName = "bar"
            }
        }

        expect {
            versionName = "bar"
        }
    }

    @Test
    fun `versionName from flavor overrides all`() {
        assumeTrue(componentType == ComponentTypeImpl.BASE_APK)
        given {
            manifestData {
                versionName = "foo"
            }

            defaultConfig {
                versionName = "bar3"
            }
            productFlavors {
                create("higherPriority") {
                    versionName = "bar1"
                }
                create("lowerPriority") {
                    versionName = "bar2"
                }
            }
        }

        expect {
            versionName = "bar1"
        }
    }

    @Test
    fun `versionName from manifest with suffix from defaultConfig`() {
        assumeTrue(componentType == ComponentTypeImpl.BASE_APK)
        given {
            manifestData {
                versionName = "foo"
            }

            defaultConfig {
                versionNameSuffix = "-bar"
            }
        }

        expect {
            versionName = "foo-bar"
        }
    }

    @Test
    fun `versionName from manifest with full suffix`() {
        assumeTrue(componentType == ComponentTypeImpl.BASE_APK)
        given {
            manifestData {
                versionName = "foo"
            }

            defaultConfig {
                versionNameSuffix = "-bar1"
            }
            productFlavors {
                create("higherPriority") {
                    versionNameSuffix = "-bar3"
                }
                create("lowerPriority") {
                    versionNameSuffix = "-bar2"
                }
            }

            buildType {
                versionNameSuffix = "-bar4"
            }
        }

        expect {
            versionName = "foo-bar1-bar3-bar2-bar4"
        }
    }

    @Test
    fun `instrumentationRunner defaults`() {
        given {
            // no specific manifest info
            manifestData { }
            testManifestData { }

            componentType = ComponentTypeImpl.ANDROID_TEST
        }

        expect {
            instrumentationRunner = "android.test.InstrumentationTestRunner"
        }
    }

    @Test
    fun `instrumentationRunner defaults with legacy multidex`() {
        given {
            // no specific manifest info
            manifestData { }
            testManifestData { }

            componentType = ComponentTypeImpl.ANDROID_TEST

            defaultConfig {
                minSdk = 20
                multiDexEnabled = true
            }

            dexingType = DexingType.LEGACY_MULTIDEX
        }

        expect {
            instrumentationRunner = "com.android.test.runner.MultiDexTestRunner"
        }
    }

    @Test
    fun `instrumentationRunner from defaultConfig`() {
        given {
            // no specific manifest info
            manifestData { }
            testManifestData { }

            componentType = ComponentTypeImpl.ANDROID_TEST

            defaultConfig {
                testInstrumentationRunner = "foo"
            }
        }

        expect {
            instrumentationRunner = "foo"
        }
    }

    @Test
    fun `instrumentationRunner from manifest`() {
        given {
            manifestData { }
            testManifestData {
                instrumentationRunner = "foo"
            }

            componentType = ComponentTypeImpl.ANDROID_TEST
        }

        expect {
            instrumentationRunner = "foo"
        }
    }

    @Test
    fun `instrumentationRunner defaultConfig overrides manifest`() {
        given {
            manifestData { }
            testManifestData {
                instrumentationRunner = "foo"
            }

            componentType = ComponentTypeImpl.ANDROID_TEST

            defaultConfig {
                testInstrumentationRunner = "bar"
            }
        }

        expect {
            instrumentationRunner = "bar"
        }
    }

    @Test
    fun `instrumentationRunner from flavor overrides all`() {
        given {
            manifestData { }
            testManifestData {
                instrumentationRunner = "foo"
            }

            componentType = ComponentTypeImpl.ANDROID_TEST

            defaultConfig {
                testInstrumentationRunner = "bar3"
            }
            productFlavors {
                create("higherPriority") {
                    testInstrumentationRunner = "bar1"
                }
                create("lowerPriority") {
                    testInstrumentationRunner = "bar2"
                }
            }
        }

        expect {
            instrumentationRunner = "bar1"
        }
    }

    @Test
    fun `handleProfiling defaults`() {
        given {
            // no specific manifest info
            manifestData { }
            testManifestData { }

            componentType = ComponentTypeImpl.ANDROID_TEST
        }

        expect {
            handleProfiling = false
        }
    }

    @Test
    fun `handleProfiling from defaultConfig`() {
        given {
            // no specific manifest info
            manifestData { }
            testManifestData { }

            componentType = ComponentTypeImpl.ANDROID_TEST

            defaultConfig {
                testHandleProfiling = true
            }
        }

        expect {
            handleProfiling = true
        }
    }

    @Test
    fun `handleProfiling from manifest`() {
        given {
            manifestData { }
            testManifestData {
                handleProfiling = true
            }

            componentType = ComponentTypeImpl.ANDROID_TEST
        }

        expect {
            handleProfiling = true
        }
    }

    @Test
    fun `handleProfiling defaultConfig overrides manifest`() {
        given {
            manifestData { }
            testManifestData {
                handleProfiling = true
            }

            componentType = ComponentTypeImpl.ANDROID_TEST

            defaultConfig {
                testHandleProfiling = false
            }
        }

        expect {
            handleProfiling = false
        }
    }

    @Test
    fun `handleProfiling from flavor overrides all`() {
        given {
            manifestData { }
            testManifestData {
                handleProfiling = true
            }

            componentType = ComponentTypeImpl.ANDROID_TEST

            defaultConfig {
                testHandleProfiling = true
            }
            productFlavors {
                create("higherPriority") {
                    testHandleProfiling = false
                }
                create("lowerPriority") {
                    testHandleProfiling = false
                }
            }
        }

        expect {
            handleProfiling = false
        }
    }

    @Test
    fun `functionalTest defaults`() {
        given {
            // no specific manifest info
            manifestData { }
            testManifestData { }

            componentType = ComponentTypeImpl.ANDROID_TEST
        }

        expect {
            functionalTest = false
        }
    }

    @Test
    fun `functionalTest from defaultConfig`() {
        given {
            // no specific manifest info
            manifestData { }
            testManifestData { }

            componentType = ComponentTypeImpl.ANDROID_TEST

            defaultConfig {
                testFunctionalTest = true
            }
        }

        expect {
            functionalTest = true
        }
    }

    @Test
    fun `functionalTest from manifest`() {
        given {
            manifestData { }
            testManifestData {
                functionalTest = true
            }

            componentType = ComponentTypeImpl.ANDROID_TEST
        }

        expect {
            functionalTest = true
        }
    }

    @Test
    fun `functionalTest defaultConfig overrides manifest`() {
        given {
            manifestData { }
            testManifestData {
                functionalTest = true
            }

            componentType = ComponentTypeImpl.ANDROID_TEST

            defaultConfig {
                testFunctionalTest = false
            }
        }

        expect {
            functionalTest = false
        }
    }

    @Test
    fun `functionalTest from flavor overrides all`() {
        given {
            manifestData { }
            testManifestData {
                functionalTest = true
            }

            componentType = ComponentTypeImpl.ANDROID_TEST

            defaultConfig {
                testFunctionalTest = true
            }
            productFlavors {
                create("higherPriority") {
                    testFunctionalTest = false
                }
                create("lowerPriority") {
                    testFunctionalTest = false
                }
            }
        }

        expect {
            functionalTest = false
        }
    }

    @Test
    fun `namespace from DSL overrides manifest`() {
        given {
            manifestData {
                packageName = "com.example.fromManifest"
            }

            namespace = "com.example.fromDsl"
        }

        expect {
            namespace = "com.example.fromDsl"
        }
    }

    @Test
    fun `testNamespace from DSL overrides namespace and manifest`() {
        given {
            manifestData {
                packageName = "com.example.fromManifest"
            }
            testManifestData {
                packageName = "com.example.fromTestManifest"
            }

            componentType = ComponentTypeImpl.ANDROID_TEST

            testNamespace = "com.example.testNamespace"
        }

        expect {
            namespace = "com.example.testNamespace"
        }
    }

    @Test
    fun `testNamespace derived from namespace`() {
        given {
            manifestData {
                packageName = "com.example.fromManifest"
            }
            testManifestData {
                packageName = "com.example.fromTestManifest"
            }

            componentType = ComponentTypeImpl.ANDROID_TEST

            defaultConfig {
                applicationId = "com.applicationId"
            }
        }

        expect {
            namespace = "${DEFAULT_NAMESPACE}.test"
        }
    }

    // ---------------------------------------------------------------------------------------------

    @get:Rule
    val exceptionRule : ExpectedException = ExpectedException.none()

    private val projectServices = createProjectServices()
    private val services = createVariantPropertiesApiServices(projectServices)
    private val dslServices: DslServices = createDslServices(projectServices)
    private val buildDirectory: DirectoryProperty = mock()

    override fun instantiateGiven() = GivenData(componentType, dslServices)
    override fun instantiateResult() = ResultData()

    private fun configureExtension(
        extension: InternalTestedExtension<*, *, *, *, *, *>,
        given: GivenData
    ) {
        whenever(extension.namespace).thenReturn(given.namespace)
        if (given.componentType.isTestComponent) {
            whenever(extension.testNamespace).thenReturn(given.testNamespace)
        }
    }

    override fun defaultWhen(given: GivenData): ResultData {
        val componentIdentity = mock<ComponentIdentity>()
        whenever(componentIdentity.name).thenReturn("compIdName")

        val extension = when (given.mainComponentType) {
            ComponentTypeImpl.BASE_APK ->
                mock<InternalApplicationExtension>().also {
                    configureExtension(it, given)
                }
            ComponentTypeImpl.LIBRARY ->
                mock<InternalLibraryExtension>().also {
                    configureExtension(it, given)
                }
            ComponentTypeImpl.OPTIONAL_APK ->
                mock<InternalDynamicFeatureExtension>().also {
                    configureExtension(it, given)
                }
            else -> {
                throw RuntimeException("Unexpected type")
            }
        } as InternalTestedExtension<*, *, *, *, *, *>

        val mainVariant = when (given.mainComponentType) {
            ComponentTypeImpl.BASE_APK -> {
                ApplicationVariantDslInfoImpl(
                    componentIdentity = componentIdentity,
                    componentType = given.mainComponentType,
                    defaultConfig = given.defaultConfig,
                    buildTypeObj = given.buildType,
                    productFlavorList = given.flavors,
                    dataProvider = DirectManifestDataProvider(given.manifestData, projectServices),
                    services = services,
                    buildDirectory = buildDirectory,
                    publishInfo = VariantPublishingInfo(emptyList()),
                    extension = extension as InternalApplicationExtension,
                    signingConfigOverride = null,
                )
            }
            ComponentTypeImpl.LIBRARY -> {
                LibraryVariantDslInfoImpl(
                    componentIdentity = componentIdentity,
                    componentType = given.mainComponentType,
                    defaultConfig = given.defaultConfig,
                    buildTypeObj = given.buildType,
                    productFlavorList = given.flavors,
                    dataProvider = DirectManifestDataProvider(given.manifestData, projectServices),
                    services = services,
                    buildDirectory = buildDirectory,
                    publishInfo = VariantPublishingInfo(emptyList()),
                    extension = extension as InternalLibraryExtension,
                )
            }
            ComponentTypeImpl.OPTIONAL_APK -> {
                DynamicFeatureVariantDslInfoImpl(
                    componentIdentity = componentIdentity,
                    componentType = given.mainComponentType,
                    defaultConfig = given.defaultConfig,
                    buildTypeObj = given.buildType,
                    productFlavorList = given.flavors,
                    dataProvider = DirectManifestDataProvider(given.manifestData, projectServices),
                    services = services,
                    buildDirectory = buildDirectory,
                    extension = extension as InternalDynamicFeatureExtension,
                )
            }
            else -> {
                throw RuntimeException("Unexpected type")
            }
        }

        val dslInfo = when (given.componentType) {
            ComponentTypeImpl.ANDROID_TEST -> {
                AndroidTestComponentDslInfoImpl(
                    componentIdentity = componentIdentity,
                    componentType = given.mainComponentType,
                    defaultConfig = given.defaultConfig,
                    buildTypeObj = given.buildType,
                    productFlavorList = given.flavors,
                    dataProvider = DirectManifestDataProvider(given.testManifestData, projectServices),
                    mainVariantDslInfo = mainVariant,
                    signingConfigOverride = null,
                    services = services,
                    buildDirectory = buildDirectory,
                    extension = extension,
                )
            }
            ComponentTypeImpl.BASE_APK -> {
                mainVariant
            }
            else -> {
                throw RuntimeException("Unexpected type")
            }
        }

        return instantiateResult().also {
            if (convertAction != null) {
                convertAction?.invoke(it, dslInfo)
            } else {
                it.versionCode = (dslInfo as? ApplicationVariantDslInfo)?.versionCode?.orNull ?: -1
                it.versionName = (dslInfo as? ApplicationVariantDslInfo)?.versionName?.orNull ?: ""
                // only query these if this is not a test.
                if (dslInfo is AndroidTestComponentDslInfo) {
                    it.instrumentationRunner = dslInfo.getInstrumentationRunner(given.dexingType).orNull
                    it.handleProfiling = dslInfo.handleProfiling.get()
                    it.functionalTest = dslInfo.functionalTest.get()
                }
                it.namespace = dslInfo.namespace.get()
            }
        }
    }

    override fun initResultDefaults(given: GivenData, result: ResultData) {
        // if the variant type is a test, then make sure that the result is initialized
        // with the right defaults.
        if (given.componentType.isForTesting) {
            result.instrumentationRunner = "android.test.InstrumentationTestRunner" // DEFAULT_TEST_RUNNER
            result.handleProfiling = false // DEFAULT_HANDLE_PROFILING
            result.functionalTest = false //DEFAULT_FUNCTIONAL_TEST
            result.namespace = "${DEFAULT_NAMESPACE}.test"
        } else {
            result.namespace = DEFAULT_NAMESPACE
        }
    }

    /** optional conversion action from variantDslInfo to result Builder. */
    private var convertAction: (ResultData.(variantInfo: ComponentDslInfo) -> Unit)? = null

    /**
     * registers a custom conversion from variantDslInfo to ResultBuilder.
     * This avoid having to use when {} which requires implementing all that defaultWhen()
     * does.
     */
    private fun convertToResult(action: ResultData.(variantInfo: ComponentDslInfo) -> Unit) {
        convertAction = action
    }

    class GivenData(
        val mainComponentType: ComponentType,
        private val dslServices: DslServices
    ) {
        /** the manifest data that represents values coming from the manifest file */
        val manifestData = ManifestData()

        /** Configures the manifest data. */
        fun manifestData(action: ManifestData.() -> Unit) {
            action(manifestData)
        }

        /** the manifest data that represents values coming from the test manifest file */
        val testManifestData = ManifestData()

        /** Configures the manifest data. */
        fun testManifestData(action: ManifestData.() -> Unit) {
            action(testManifestData)
        }

        /** Variant type for the test */
        var componentType = ComponentTypeImpl.BASE_APK

        var dexingType = DexingType.NATIVE_MULTIDEX

        var namespace: String = DEFAULT_NAMESPACE
        var testNamespace: String? = null

        /** default Config values */
        val defaultConfig: DefaultConfig = dslServices.newDecoratedInstance(DefaultConfig::class.java, BuilderConstants.MAIN, dslServices)

        /** configures the default config */
        fun defaultConfig(action: DefaultConfig.() -> Unit) {
            action(defaultConfig)
        }

        val buildType: BuildType = dslServices.newDecoratedInstance(BuildType::class.java, "Build-Type", dslServices, componentType)

        fun buildType(action: BuildType.() -> Unit) {
            action(buildType)
        }

        private val productFlavors: ContainerImpl<ProductFlavor> = ContainerImpl { name ->
            dslServices.newDecoratedInstance(ProductFlavor::class.java, name, dslServices)
        }
        val flavors: List<ProductFlavor>
            get() = productFlavors.values.toList()

        /**
         * add/configures flavors. The earlier items have higher priority over the later ones.
         */
        fun productFlavors(action: Container<ProductFlavor>.() -> Unit) {
            action(productFlavors)
        }
    }

    class ResultData(
        var versionCode: Int = -1,
        var versionName: String = "",
        var instrumentationRunner: String? = null,
        var handleProfiling: Boolean? = null,
        var functionalTest: Boolean? = null,
        var namespace: String? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ResultData

            if (versionCode != other.versionCode) return false
            if (versionName != other.versionName) return false
            if (instrumentationRunner != other.instrumentationRunner) return false
            if (handleProfiling != other.handleProfiling) return false
            if (functionalTest != other.functionalTest) return false
            if (namespace != other.namespace) return false

            return true
        }

        override fun hashCode(): Int {
            var result = versionCode ?: 0
            result = 31 * result + (versionName?.hashCode() ?: 0)
            result = 31 * result + (instrumentationRunner?.hashCode() ?: 0)
            result = 31 * result + (handleProfiling?.hashCode() ?: 0)
            result = 31 * result + (functionalTest?.hashCode() ?: 0)
            result = 31 * result + (namespace?.hashCode() ?: 0)
            return result
        }

        override fun toString(): String {
            return "ResultData(versionCode=$versionCode, versionName=$versionName, instrumentationRunner=$instrumentationRunner, handleProfiling=$handleProfiling, functionalTest=$functionalTest, namespace=$namespace)"
        }
    }

    /**
     * Use the ManifestData provider in the given as a ManifestDataProvider in order to
     * instantiate the ManifestBackedVariantValues object.
     */
    class DirectManifestDataProvider(data: ManifestData, projectServices: ProjectServices) :
        ManifestDataProvider {

        override val manifestData: Provider<ManifestData> =
            projectServices.providerFactory.provider { data }

        override val manifestLocation: String
            get() = "manifest-location"
    }
}
