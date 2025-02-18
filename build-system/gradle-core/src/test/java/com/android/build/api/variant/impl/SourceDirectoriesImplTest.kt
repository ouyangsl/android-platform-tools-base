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

package com.android.build.api.variant.impl

import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.services.VariantServices
import com.google.common.truth.Truth
import java.io.File
import java.util.concurrent.Callable
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.mockito.stubbing.Answer

internal class SourceDirectoriesImplTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val variantServices: VariantServices = mock()

    @Captor
    lateinit var callableCaptor: ArgumentCaptor<Callable<*>>

    private lateinit var project: Project

    @Before
    fun setup() {
        project = ProjectBuilder.builder()
            .withProjectDir(temporaryFolder.newFolder())
            .build()

        val projectInfo = mock<ProjectInfo>()
        whenever(variantServices.projectInfo).thenReturn(projectInfo)
        whenever(projectInfo.projectDirectory).thenReturn(project.layout.projectDirectory)
        whenever(projectInfo.buildDirectory).thenReturn(project.layout.buildDirectory)

        whenever(variantServices.newListPropertyForInternalUse(DirectoryEntry::class.java))
            .thenReturn(project.objects.listProperty(DirectoryEntry::class.java))
        whenever(variantServices.newListPropertyForInternalUse(Directory::class.java))
            .thenReturn(project.objects.listProperty(Directory::class.java))
    }

    @Test
    fun testAsFileTree() {
        whenever(variantServices.fileTreeFactory()).thenReturn(
            { project.objects.fileTree() },
            { project.objects.fileTree() },
        )
        val addedSourceFromTask = project.layout.buildDirectory.dir("generated/_for_test/srcAddingTask").get().asFile
        val addedSrcDir = temporaryFolder.newFolder("somewhere/safe")
        val testTarget = createTestTarget(addedSrcDir)
        val fileTrees = testTarget.getAsFileTreesForOldVariantAPI().get()
        Truth.assertThat(fileTrees).hasSize(2)
        Truth.assertThat(fileTrees.map { it.dir.absolutePath }).containsExactly(
            addedSourceFromTask.absolutePath,
            addedSrcDir.absolutePath
        )
    }

    @Test
    fun testVariantSourcesForModel() {
        whenever(variantServices.fileCollection())
            .thenReturn(project.objects.fileCollection())
        val addedSourceFromTask = project.layout.buildDirectory.dir("generated/_for_test/srcAddingTask").get().asFile
        val addedSrcDir = temporaryFolder.newFolder("somewhere/safe")
        val testTarget = createTestTarget(addedSrcDir)
        val fileTrees = testTarget.variantSourcesForModel { it.shouldBeAddedToIdeModel }
        Truth.assertThat(fileTrees).hasSize(2)
        Truth.assertThat(fileTrees.map { it.absolutePath }).containsExactly(
            addedSourceFromTask.absolutePath,
            addedSrcDir.absolutePath
        )
    }

    @Test
    fun testVariantSourcesWithFilteringForModel() {
        whenever(variantServices.fileCollection())
            .thenReturn(project.objects.fileCollection())
        val addedSourceFromTask = project.layout.buildDirectory.dir("generated/_for_test/srcAddingTask").get().asFile
        val addedSrcDir = temporaryFolder.newFolder("somewhere/safe")
        val testTarget = createTestTarget(addedSrcDir)
        val fileTrees = testTarget.variantSourcesForModel { entry ->
            entry.isGenerated
        }
        Truth.assertThat(fileTrees).hasSize(1)
        Truth.assertThat(fileTrees.map { it.absolutePath }).containsExactly(
            addedSourceFromTask.absolutePath,
        )
    }

    fun <T> capture(argumentCaptor: ArgumentCaptor<T>): T = argumentCaptor.capture()

    private fun createTestTarget(
        addedSrcDir: File,
        patternFilterable: PatternFilterable? = null,
    ): FlatSourceDirectoriesImpl {

        val testTarget = FlatSourceDirectoriesImpl(
            "_for_test",
            variantServices,
            patternFilterable,
        )
        abstract class AddingTask: DefaultTask() {
            @get:OutputFiles
            abstract val output: DirectoryProperty
        }

        val taskProvider = project.tasks.register("srcAddingTask", AddingTask::class.java)
        whenever(variantServices.provider(capture(callableCaptor))).thenAnswer(
            Answer() {
                project.provider(callableCaptor.value)
            }
        )

        testTarget.addGeneratedSourceDirectory(taskProvider, AddingTask::output)

        testTarget.addStaticSourceDirectory(addedSrcDir.absolutePath)

        return testTarget
    }
}
