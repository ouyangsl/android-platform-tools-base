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
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

internal class AbstractSourceDirectoriesImplTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    val listOfSources = mutableListOf<DirectoryEntry>()
    val listOfStaticSources = mutableListOf<DirectoryEntry>()

    private lateinit var project: Project

    @Before
    fun setup() {
        project = ProjectBuilder.builder()
            .withProjectDir(temporaryFolder.newFolder())
            .build()
    }

    @Test
    fun testGetName() {
        Truth.assertThat(createTestTarget().name).isEqualTo("_for_test")
    }

   @Test
   fun testAddSrcDir() {
       val testTarget = createTestTarget()
       val addedSource = temporaryFolder.newFolder("somewhere/safe")
       testTarget.addStaticSourceDirectory(
           addedSource.absolutePath
       )

       Truth.assertThat(listOfSources.size).isEqualTo(1)
       val directoryProperty = listOfSources.single().asFiles(
         project.provider { project.layout.projectDirectory })
       Truth.assertThat(directoryProperty.get().single().asFile.absolutePath).isEqualTo(
           addedSource.absolutePath
       )

       Truth.assertThat(listOfStaticSources.size).isEqualTo(1)
       val staticDirectoryProperty = listOfStaticSources.single().asFiles(
           project.provider { project.layout.projectDirectory })
       Truth.assertThat(staticDirectoryProperty.get().single().asFile.absolutePath).isEqualTo(
           addedSource.absolutePath
       )
   }

    @Test
    fun testAddNonExistentSrcDir() {
        val testTarget = createTestTarget()
        val addedSource = File(temporaryFolder.root, "somewhere/not/existing")
        testTarget.addStaticSourceDirectory(
            addedSource.absolutePath
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAddIllegalFileAsSrcDir() {
        val testTarget = createTestTarget()
        val addedSource = temporaryFolder.newFile("new_file")
        testTarget.addStaticSourceDirectory(
            addedSource.absolutePath
        )
    }

   @Test
   fun testAddSrcDirFromTask() {
       abstract class AddingTask: DefaultTask() {
           @get:OutputFiles
           abstract val output: DirectoryProperty
       }

       val taskProvider = project.tasks.register("srcAddingTask", AddingTask::class.java)

       val testTarget = createTestTarget()
       testTarget.addGeneratedSourceDirectory(taskProvider, AddingTask::output)
       Truth.assertThat(listOfSources.size).isEqualTo(1)
       val directoryProperty = listOfSources.single().asFiles(
         project.provider { project.layout.projectDirectory }
       )
       Truth.assertThat(directoryProperty).isNotNull()

       Truth.assertThat(listOfStaticSources.size).isEqualTo(0)
   }

    @Test
    fun testFiltering() {
        val pattern = mock<PatternFilterable>()
        whenever(pattern.includes).thenReturn(setOf("*.java", "*.kt"))
        whenever(pattern.excludes).thenReturn(setOf("*.bak"))
        val testTarget = createTestTarget(pattern)
        val addedSource = temporaryFolder.newFolder("somewhere/safe")
        testTarget.addStaticSourceDirectory(
            addedSource.absolutePath
        )

        Truth.assertThat(listOfSources.size).isEqualTo(1)
        val filter = listOfSources.single().filter
        Truth.assertThat(filter).isNotNull()
        Truth.assertThat(filter?.includes).containsExactly("*.java", "*.kt")
        Truth.assertThat(filter?.excludes).containsExactly("*.bak")
    }

    private fun createTestTarget(patternFilterable: PatternFilterable? = null): SourceDirectoriesImpl {
        val variantServices = mock<VariantServices>()
        val projectInfo = mock<ProjectInfo>()
        whenever(variantServices.projectInfo).thenReturn(projectInfo)
        whenever(variantServices.fileCollection()).then { project.files() }
        whenever(projectInfo.projectDirectory).thenReturn(project.layout.projectDirectory)
        whenever(projectInfo.buildDirectory).thenReturn(project.layout.buildDirectory)

        return object : SourceDirectoriesImpl(
            "_for_test",
            variantServices,
            patternFilterable
        ) {
            override fun addSource(directoryEntry: DirectoryEntry) {
                listOfSources.add(directoryEntry)
            }

            override fun addStaticSource(directoryEntry: DirectoryEntry) {
                listOfSources.add(directoryEntry)
                listOfStaticSources.add(directoryEntry)
            }

            override fun variantSourcesForModel(filter: (DirectoryEntry) -> Boolean): List<File> =
                emptyList()

            override fun forAllSources(action: (DirectoryEntry) -> Unit) {
                listOfSources.forEach(action::invoke)
            }
        }
    }
}
