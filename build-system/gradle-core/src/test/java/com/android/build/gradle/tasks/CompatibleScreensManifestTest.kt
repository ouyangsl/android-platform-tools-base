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

package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.impl.ComponentIdentityImpl
import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.impl.AndroidVersionImpl
import com.android.build.api.variant.impl.ApplicationVariantImpl
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.api.variant.impl.FilterConfigurationImpl
import com.android.build.api.variant.impl.VariantOutputConfigurationImpl
import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.build.api.variant.impl.VariantOutputList
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.createProjectServices
import com.android.build.gradle.internal.services.createTaskCreationServices
import com.android.build.gradle.internal.services.getBuildServiceName
import com.android.builder.core.ComponentTypeImpl
import com.android.builder.profile.NameAnonymizer
import com.android.builder.profile.NameAnonymizerSerializer
import com.google.common.base.Joiner
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.GradleBuildProfile
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.Base64

/** Tests for the [CompatibleScreensManifest] class  */
class CompatibleScreensManifestTest {

    @get:Rule var projectFolder = TemporaryFolder()
    @get:Rule var temporaryFolder = TemporaryFolder()

    private val artifacts: ArtifactsImpl = mock()
    private val taskContainer: MutableTaskContainer = mock()
    private val appVariant: ApplicationVariantImpl = mock()

    private lateinit var task: CompatibleScreensManifest

    @Before
    @Throws(IOException::class)
    fun setUp() {
        val testDir = projectFolder.newFolder()
        val project = ProjectBuilder.builder().withProjectDir(testDir).build()
        project.gradle.sharedServices.registerIfAbsent(
            getBuildServiceName(AnalyticsService::class.java),
            AnalyticsService::class.java
        ) {
            val profile = GradleBuildProfile.newBuilder().build().toByteArray()
            it.parameters.profile.set(Base64.getEncoder().encodeToString(profile))
            it.parameters.anonymizer.set(NameAnonymizerSerializer().toJson(NameAnonymizer()))
            it.parameters.projects.set(mutableMapOf())
            it.parameters.enableProfileJson.set(true)
            it.parameters.taskMetadata.set(mutableMapOf())
            it.parameters.rootProjectPath.set("/path")
        }
        task = project.tasks.create("test", CompatibleScreensManifest::class.java)

        val services = createTaskCreationServices(
            createProjectServices(project)
        )

        whenever(appVariant.name).thenReturn("fullVariantName")
        whenever(appVariant.baseName).thenReturn("baseName")
        whenever(appVariant.artifacts).thenReturn(artifacts)
        whenever(appVariant.taskContainer).thenReturn(taskContainer)
        whenever(appVariant.componentType).thenReturn(ComponentTypeImpl.BASE_APK)
        whenever(appVariant.services).thenReturn(services)
        whenever(appVariant.minSdk).thenReturn(AndroidVersionImpl(21))


        whenever(taskContainer.preBuildTask).thenReturn(project.tasks.register("preBuildTask"))
        task.outputFolder.set(temporaryFolder.root)
        whenever(appVariant.componentType).thenReturn(ComponentTypeImpl.BASE_APK)
        whenever(appVariant.componentIdentity).thenReturn(
            ComponentIdentityImpl(
                "fullVariantName",
                "flavorName",
                "debug"
            )
        )
        val applicationId = project.objects.property(String::class.java)
        applicationId.set("com.foo")
        whenever(appVariant.applicationId).thenReturn(applicationId)
    }

    @Test
    fun testConfigAction() {
        val configAction = CompatibleScreensManifest.CreationAction(
                appVariant, setOf("xxhpi", "xxxhdpi")
        )
        val variantOutputList = VariantOutputList(listOf(fakeVariantOutput()))
        whenever(appVariant.outputs).thenReturn(variantOutputList)

        configAction.configure(task)

        assertThat(task.variantName).isEqualTo("fullVariantName")
        assertThat(task.name).isEqualTo("test")
        assertThat(task.minSdkVersion.get()).isEqualTo("21")
        assertThat(task.screenSizes.get()).containsExactly("xxhpi", "xxxhdpi")
        assertThat(task.outputFolder.get().asFile).isEqualTo(temporaryFolder.root)
        assertThat(task.applicationId.get()).isEqualTo("com.foo")
        assertThat(task.analyticsService.get()).isInstanceOf(AnalyticsService::class.java)
    }

    @Test
    fun testNoSplit() {
        task.variantOutputs.add(fakeVariantOutput())
        task.variantName = "variant"
        task.minSdkVersion.set("22" )
        task.screenSizes.set(setOf("mdpi", "xhdpi"))
        task.applicationId.set("com.foo")
        task.analyticsService.set(FakeNoOpAnalyticsService())

        task.taskAction()
        val buildElements = BuiltArtifactsLoaderImpl.loadFromDirectory(
            temporaryFolder.root)
        assertThat(buildElements?.elements).isEmpty()
    }

    @Test
    @Throws(IOException::class)
    fun testSingleSplitWithMinSdkVersion() {
        task.variantOutputs.add(fakeVariantOutput(withSplits = true))
        task.variantName = "variant"
        task.minSdkVersion.set("22")
        task.screenSizes.set(setOf("xhdpi"))
        task.applicationId.set("com.foo")
        task.analyticsService.set(FakeNoOpAnalyticsService())

        task.taskAction()

        val xml = Joiner.on("\n")
            .join(Files.readAllLines(
                    findManifest(temporaryFolder.root, "xhdpi").toPath()))
        assertThat(xml).contains("<uses-sdk android:minSdkVersion=\"22\"/>")
        assertThat(xml).contains("<compatible-screens>")
        assertThat(xml)
            .contains(
                    "<screen android:screenSize=\"xhdpi\" android:screenDensity=\"xhdpi\" />"
            )
    }

    @Test
    @Throws(IOException::class)
    fun testSingleSplitWithoutMinSdkVersion() {
        task.variantOutputs.add(fakeVariantOutput(withSplits = true))
        task.variantName = "variant"
        task.minSdkVersion.set(task.project.provider { null })
        task.screenSizes.set(setOf("xhdpi"))
        task.applicationId.set("com.foo")
        task.analyticsService.set(FakeNoOpAnalyticsService())

        task.taskAction()

        val xml = Joiner.on("\n")
            .join(
                    Files.readAllLines(
                            findManifest(temporaryFolder.root, "xhdpi").toPath()
                    )
            )
        assertThat(xml).doesNotContain("<uses-sdk")
    }

    @Test
    @Throws(IOException::class)
    fun testMultipleSplitsWithMinSdkVersion() {
        task.variantOutputs.add(fakeVariantOutput(withSplits = true))
        task.variantOutputs.add(
            fakeVariantOutput(withSplits = true).copy(
                variantOutputConfiguration = VariantOutputConfigurationImpl(
                    false,
                    listOf(FilterConfigurationImpl(FilterConfiguration.FilterType.DENSITY, "xxhdpi"))
                )
            )
        )

        task.variantName = "variant"
        task.minSdkVersion.set("23")
        task.screenSizes.set(setOf("xhdpi", "xxhdpi"))
        task.applicationId.set("com.foo")
        task.analyticsService.set(FakeNoOpAnalyticsService())

        task.taskAction()

        var xml = Joiner.on("\n")
            .join(Files.readAllLines(
                    findManifest(temporaryFolder.root, "xhdpi").toPath()))
        assertThat(xml).contains("<uses-sdk android:minSdkVersion=\"23\"/>")
        assertThat(xml).contains("<compatible-screens>")
        assertThat(xml)
            .contains(
                    "<screen android:screenSize=\"xhdpi\" android:screenDensity=\"xhdpi\" />"
            )

        xml = Joiner.on("\n")
            .join(Files.readAllLines(
                    findManifest(temporaryFolder.root, "xxhdpi").toPath()))
        assertThat(xml).contains("<uses-sdk android:minSdkVersion=\"23\"/>")
        assertThat(xml).contains("<compatible-screens>")
        assertThat(xml)
            .contains("<screen android:screenSize=\"xxhdpi\" android:screenDensity=\"480\" />")
    }

    private fun fakeVariantOutput(withSplits: Boolean = false): VariantOutputImpl =
        VariantOutputImpl(
            versionCode = FakeGradleProperty(1),
            versionName = FakeGradleProperty("version_name"),
            enabled = FakeGradleProperty(true),
            variantOutputConfiguration = VariantOutputConfigurationImpl(
                isUniversal = false,
                filters = if (withSplits) {
                    listOf(FilterConfigurationImpl(FilterConfiguration.FilterType.DENSITY, "xhdpi"))
                } else emptyList()
            ),
            baseName = "base_name",
            fullName = "full_name",
            outputFileName = FakeGradleProperty("output_file_name"),
        )

    companion object {
        private fun findManifest(taskOutputDir: File, splitName: String): File {
            val splitDir = File(taskOutputDir, splitName)
            assertThat(splitDir.exists()).isTrue()
            val manifestFile = File(splitDir, SdkConstants.ANDROID_MANIFEST_XML)
            assertThat(manifestFile.exists()).isTrue()
            return manifestFile
        }
    }
}
