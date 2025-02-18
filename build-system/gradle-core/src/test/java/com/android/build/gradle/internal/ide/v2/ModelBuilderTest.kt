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

package com.android.build.gradle.internal.ide.v2

import com.android.AndroidProjectTypes
import com.android.build.api.dsl.ApplicationAndroidResources
import com.android.build.api.dsl.ApplicationBuildFeatures
import com.android.build.api.dsl.ApplicationBuildType
import com.android.build.api.dsl.ApplicationDefaultConfig
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.ApplicationInstallation
import com.android.build.api.dsl.ApplicationProductFlavor
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.dsl.ApplicationBuildFeaturesImpl
import com.android.build.gradle.internal.dsl.ApplicationExtensionImpl
import com.android.build.gradle.internal.errors.SyncIssueReporterImpl
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeLogger
import com.android.build.gradle.internal.fixtures.ProjectFactory
import com.android.build.gradle.internal.scope.BuildFeatureValuesImpl
import com.android.build.gradle.internal.scope.DelayedActionsExecutor
import com.android.build.gradle.internal.services.AndroidLocationsBuildService
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.createDslServices
import com.android.build.gradle.internal.services.createProjectServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfigImpl
import com.android.build.gradle.internal.variant.LegacyVariantInputManager
import com.android.build.gradle.internal.variant.VariantInputModelBuilder
import com.android.build.gradle.internal.variant.VariantModel
import com.android.build.gradle.internal.variant.VariantModelImpl
import com.android.build.gradle.options.SyncOptions
import com.android.builder.core.ComponentTypeImpl
import com.android.builder.errors.IssueReporter
import com.android.builder.model.v2.ide.ProjectType
import com.android.builder.model.v2.ide.SyncIssue
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.ModelBuilderParameter
import com.android.builder.model.v2.models.ProjectSyncIssues
import com.android.builder.model.v2.models.VariantDependencies
import com.android.builder.model.v2.models.Versions
import com.google.common.truth.Truth
import org.gradle.api.Project
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ModelBuilderTest {

    @get:Rule
    val thrown: ExpectedException = ExpectedException.none()

    private val project: Project = ProjectFactory.project
    private val projectServices: ProjectServices = createProjectServices(
        issueReporter = SyncIssueReporterImpl(SyncOptions.EvaluationMode.IDE, SyncOptions.ErrorFormatMode.HUMAN_READABLE, FakeLogger())
    )

    @Before
    fun setUp() {
    }

    @Test
    fun `test canBuild`() {
        fun testModel(name: String) {
            Truth.assertThat(createApplicationModelBuilder().canBuild(name))
                .named("can build '$name")
                .isTrue()
        }

        testModel(Versions::class.java.name)
        testModel(AndroidProject::class.java.name)
        testModel(AndroidDsl::class.java.name)
        testModel(VariantDependencies::class.java.name)
        testModel(ProjectSyncIssues::class.java.name)
    }

    @Test
    fun `test wrong model`() {
        val modelBuilder = createApplicationModelBuilder()

        Truth.assertThat(modelBuilder.canBuild("com.FooModel"))
            .named("can build com.FooModel")
            .isFalse()

        thrown.expect(RuntimeException::class.java)
        thrown.expectMessage("Does not support model 'com.FooModel'")
        modelBuilder.buildAll("com.FooModel", project)
    }

    @Test
    fun `test wrong query with VariantDependencies`() {
        thrown.expect(RuntimeException::class.java)
        thrown.expectMessage("Please use parameterized Tooling API to obtain VariantDependencies model.")
        createApplicationModelBuilder().buildAll(VariantDependencies::class.java.name, project)
    }

    @Test
    fun `test wrong query with AndroidProject`() {
        thrown.expect(RuntimeException::class.java)
        val name = AndroidProject::class.java.name
        thrown.expectMessage("Please use non-parameterized Tooling API to obtain $name model.")
        createApplicationModelBuilder().buildAll(name, FakeModelBuilderParameter(), project)
    }

    @Test
    fun `test wrong query with Versions`() {
        thrown.expect(RuntimeException::class.java)
        val name = Versions::class.java.name
        thrown.expectMessage("Please use non-parameterized Tooling API to obtain $name model.")
        createApplicationModelBuilder().buildAll(name, FakeModelBuilderParameter(), project)
    }

    @Test
    fun `test wrong query with AndroidDsl`() {
        thrown.expect(RuntimeException::class.java)
        val name = AndroidDsl::class.java.name
        thrown.expectMessage("Please use non-parameterized Tooling API to obtain $name model.")
        createApplicationModelBuilder().buildAll(name, FakeModelBuilderParameter(), project)
    }

    @Test
    fun `test wrong query with ProjectSyncIssues`() {
        thrown.expect(RuntimeException::class.java)
        val name = ProjectSyncIssues::class.java.name
        thrown.expectMessage("Please use non-parameterized Tooling API to obtain $name model.")
        createApplicationModelBuilder().buildAll(name, FakeModelBuilderParameter(), project)
    }

    @Test
    fun `test issueReporter`() {
        projectServices.issueReporter.reportWarning(IssueReporter.Type.GENERIC, "warning!")

        val model = createApplicationModelBuilder().query(ProjectSyncIssues::class.java, project)

        Truth.assertThat(model.syncIssues).hasSize(1)
        val issue = model.syncIssues.single()
        Truth.assertThat(issue.severity).named("severity").isEqualTo(SyncIssue.SEVERITY_WARNING)
        Truth.assertThat(issue.type).named("type").isEqualTo(SyncIssue.TYPE_GENERIC)
        Truth.assertThat(issue.message).named("message").isEqualTo("warning!")
    }

    @Test
    fun `test issueReporter lockdown`() {
        thrown.expect(IllegalStateException::class.java)
        thrown.expectMessage("Issue registered after handler locked.")

        // This should lock down the issue handler.
        createApplicationModelBuilder().buildAll(ProjectSyncIssues::class.java.name, project)

        // And then trying to report anything after fetching the sync issues model should throw.
        projectServices.issueReporter.reportWarning(IssueReporter.Type.GENERIC, "Should Fail")
    }

    //---------------

    fun <T> ModelBuilder<*,*,*,*,*,*,*>.query(modelClass: Class<T>, project: Project) : T {
        return modelClass.cast(buildAll(modelClass.name, project))
    }

    private val variantList: MutableList<VariantCreationConfig> = mutableListOf()
    private val testComponentList: MutableList<TestComponentCreationConfig> = mutableListOf()

    private val sdkComponents = mock<SdkComponentsBuildService>()
    private val sdkComponentProvider = FakeGradleProvider(sdkComponents)
    private val dslServices = createDslServices(
        projectServices = projectServices,
        sdkComponents = sdkComponentProvider
    )

    private fun createApplicationModelBuilder() :
            ModelBuilder<
                    ApplicationBuildFeatures,
                    ApplicationBuildType,
                    ApplicationDefaultConfig,
                    ApplicationProductFlavor,
                    ApplicationAndroidResources,
                    ApplicationInstallation,
                    ApplicationExtension> {

        // for now create an app extension

        AndroidLocationsBuildService.RegistrationAction(project).execute()

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

        val extension = dslServices.newDecoratedInstance(
            ApplicationExtensionImpl::class.java,
            dslServices,
            variantInputModel
        )

        // make sure the global issue reporter is registered
        SyncIssueReporterImpl.GlobalSyncIssueService.RegistrationAction(
            project, SyncOptions.EvaluationMode.IDE, SyncOptions.ErrorFormatMode.MACHINE_PARSABLE
        ).execute()

        return ModelBuilder(project, createVariantModel(), extension)
    }

    private fun createVariantModel() : VariantModel {
        val globalConfig = mock<GlobalTaskCreationConfigImpl>()
        whenever(globalConfig.services).thenReturn(dslServices)

        return VariantModelImpl(
            VariantInputModelBuilder(ComponentTypeImpl.BASE_APK, dslServices).toModel(),
            { "debug" },
            { variantList },
            { testComponentList },
            {
                BuildFeatureValuesImpl(
                    dslServices.newInstance(ApplicationBuildFeaturesImpl::class.java),
                    mock<ProjectServices>()
                )
            },
            AndroidProjectTypes.PROJECT_TYPE_APP,
            ProjectType.APPLICATION,
            globalConfig
        )
    }

    data class FakeModelBuilderParameter(
        override var variantName: String = "foo",
        override var dontBuildRuntimeClasspath: Boolean = false,
        override var dontBuildUnitTestRuntimeClasspath: Boolean = false,
        override var dontBuildScreenshotTestRuntimeClasspath: Boolean = false,
        override var dontBuildAndroidTestRuntimeClasspath: Boolean = false,
        override var dontBuildTestFixtureRuntimeClasspath: Boolean = false,
        override var dontBuildHostTestRuntimeClasspath: Map<String, Boolean> =
            mapOf("UnitTest" to false, "ScreenshotTest" to false)
    ) : ModelBuilderParameter
}
