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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.AvdComponentsBuildService
import com.android.build.gradle.internal.dsl.ManagedVirtualDevice
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfigImpl
import org.mockito.kotlin.any
import com.android.testutils.SystemPropertyOverrides
import com.android.utils.Environment
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.gradle.api.Project
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.logging.Logger
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildServiceRegistration
import org.gradle.testfixtures.ProjectBuilder
import org.mockito.Answers.CALLS_REAL_METHODS
import org.mockito.Answers.RETURNS_DEEP_STUBS
import org.mockito.Mock
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.junit.MockitoJUnit

class ManagedDeviceCleanTaskTest {

    @get:Rule
    val mockitoRule = MockitoJUnit.rule()

    @get:Rule
    val temporaryFolderRule = TemporaryFolder()

    private val globalConfig: GlobalTaskCreationConfigImpl = mock(defaultAnswer = RETURNS_DEEP_STUBS)

    private val avdService: AvdComponentsBuildService = mock()

    private lateinit var project: Project

    @Before
    fun setup() {
        Environment.initialize()

        // Setup Build Services for configuration.
        val mockGeneralRegistration = mock<BuildServiceRegistration<*, *>>(defaultAnswer = RETURNS_DEEP_STUBS)
        whenever(globalConfig.services.buildServiceRegistry.registrations.getByName(any()))
            .thenReturn(mockGeneralRegistration)

        project = ProjectBuilder.builder().withProjectDir(temporaryFolderRule.newFolder()).build()
    }

    private inline fun <reified ValueClass> realPropertyFor(
        providedValue: ValueClass): Property<ValueClass> {

        val property = project.objects.property(ValueClass::class.java)
        property.set(providedValue)
        return property
    }

    private inline fun <reified ValueClass> realListPropertyFor(
        vararg provided: ValueClass
    ): ListProperty<ValueClass> = realListPropertyFor(provided.toList())

    private inline fun <reified ValueClass> realListPropertyFor(
        providedList: List<ValueClass>): ListProperty<ValueClass> {

        val property = project.objects.listProperty(ValueClass::class.java)
        property.set(providedList)
        return property
    }

    private fun <T> mockEmptyProperty(): Property<T> {
        @Suppress("UNCHECKED_CAST")
        return mock<Property<T>>()
    }

    private fun <T> mockEmptyListProperty(): ListProperty<T> {
        return mock<ListProperty<T>>()
    }

    /**
     * Returns a clean task that is ready to use for testing.
     */
    private fun basicTaskSetup(): ManagedDeviceCleanTask {
        val task = mock<ManagedDeviceCleanTask>(defaultAnswer = CALLS_REAL_METHODS)

        // Need to use a real property for all variables passed into the ManagedDeviceCleanRunnable
        // Because internal to Gradle's implementation of ProfileAwareWorkAction
        // a forced cast occurs to cast Provider to ProviderInternal which we
        // do not have access to directly.
        doReturn(realPropertyFor(mock<AnalyticsService>()))
            .whenever(task).analyticsService
        doReturn(realPropertyFor("project_path")).whenever(task).projectPath

        doReturn("path").whenever(task).path
        doReturn(mock<TaskOutputsInternal>(defaultAnswer = RETURNS_DEEP_STUBS))
            .whenever(task).outputs
        doReturn(mock<Logger>()).whenever(task).logger

        doReturn(realPropertyFor(avdService)).whenever(task).avdService
        doReturn(realListPropertyFor<String>()).whenever(task).dslDevices
        doReturn(realPropertyFor(false)).whenever(task).preserveDefined
        doReturn(listOf<String>()).whenever(avdService).allAvds()

        doReturn(FakeGradleWorkExecutor(project.objects, temporaryFolderRule.newFolder()))
            .whenever(task).workerExecutor
        return task
    }

    @Test
    fun taskAction_removeAllPath() {
        val task = basicTaskSetup()

        val avdList = listOf(
            "an_avd",
            "another_avd",
            "one_more_avd"
        )
        doReturn(avdList).whenever(avdService).allAvds()
        doReturn(realListPropertyFor("an_avd")).whenever(task).dslDevices

        task.doTaskAction()

        verify(avdService).allAvds()
        verify(avdService).deleteAvds(listOf("an_avd", "another_avd", "one_more_avd"))
        verify(avdService).deleteLegacyGradleManagedDeviceAvdDirectory()
        verify(avdService).deleteManagedDeviceTrackingFile()
        verifyNoMoreInteractions(avdService)
    }

    @Test
    fun taskAction_removeOnlyUnused() {
        val task = basicTaskSetup()

        val avdList = listOf(
            "an_avd",
            "another_avd",
            "one_more_avd"
        )
        doReturn(avdList).whenever(avdService).allAvds()
        doReturn(realListPropertyFor("an_avd")).whenever(task).dslDevices
        doReturn(realPropertyFor(true)).whenever(task).preserveDefined

        task.doTaskAction()
        verify(avdService).allAvds()
        // "an_avd" should not be deleted.
        verify(avdService).deleteAvds(listOf("another_avd", "one_more_avd"))
        verify(avdService).deleteLegacyGradleManagedDeviceAvdDirectory()
        verify(avdService).deleteManagedDeviceTrackingFile()
        verifyNoMoreInteractions(avdService)
    }

    @Test
    fun creationAction_configureTask() {
        try {
            // Need to use a custom set up environment to ensure deterministic behavior.
            SystemPropertyOverrides().use { systemPropertyOverrides ->
                // This will ensure the config believes we are running on an x86_64 Linux machine.
                // This will guarantee the x86 system-images are selected.
                systemPropertyOverrides.setProperty("os.name", "Linux")
                Environment.instance = object : Environment() {
                    override fun getVariable(name: EnvironmentVariable): String? =
                        if (name.key == "HOSTTYPE") "x86_64" else null
                }
                systemPropertyOverrides.setProperty("os.arch", "x86_64")

                val config = ManagedDeviceCleanTask.CreationAction(
                    "cleanDevices",
                    globalConfig,
                    listOf(
                        ManagedVirtualDevice("testDevice1").also {
                            it.device = "Pixel 3"
                            it.apiLevel = 28
                            it.systemImageSource = "aosp"
                        },
                        ManagedVirtualDevice("testDevice2").also {
                            it.device = "Nexus 5"
                            it.apiLevel = 30
                            it.systemImageSource = "google"
                            it.require64Bit = true
                        }
                    )
                )

                val task = mock<ManagedDeviceCleanTask>(defaultAnswer = RETURNS_DEEP_STUBS)

                // We need to create mock properties to verify/capture values in the task as
                // RETURNS_DEEP_STUBS does not work as expected with verify. Also, we can't use
                // FakeGradleProperties because they do not support disallowChanges().
                val avdProperty = mockEmptyProperty<AvdComponentsBuildService>()
                val dslDevicesProperty = mockEmptyListProperty<String>()
                val preserveDefinedProperty = mockEmptyProperty<Boolean>()

                whenever(task.avdService).thenReturn(avdProperty)
                whenever(task.dslDevices).thenReturn(dslDevicesProperty)
                whenever(task.preserveDefined).thenReturn(preserveDefinedProperty)

                config.configure(task)

                verify(avdProperty).set(any<Provider<AvdComponentsBuildService>>())
                verify(avdProperty).disallowChanges()
                verifyNoMoreInteractions(avdProperty)

                verify(dslDevicesProperty).set(
                    listOf("dev28_default_x86_Pixel_3", "dev30_google_apis_x86_64_Nexus_5")
                )
                verify(dslDevicesProperty).disallowChanges()
                verifyNoMoreInteractions(dslDevicesProperty)

                verify(preserveDefinedProperty).convention(false)
                verifyNoMoreInteractions(preserveDefinedProperty)
            }
        } finally {
            Environment.instance = Environment.SYSTEM
        }
    }
}
