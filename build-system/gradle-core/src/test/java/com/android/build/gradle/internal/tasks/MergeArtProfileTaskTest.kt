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

import com.android.build.gradle.internal.fixtures.FakeArtifactCollection
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.inject.Inject

internal class MergeArtProfileTaskTest {

    @get: Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var task: MergeArtProfileTask
    private lateinit var outputFile: File
    private lateinit var project: Project
    private lateinit var inputFiles: ConfigurableFileCollection
    private lateinit var artProfileSourceFile: RegularFileProperty
    private lateinit var baselineProfileSourceDirs: ListProperty<Directory>

    abstract class MergeArtProfileTaskForTest @Inject constructor(
        testWorkerExecutor: WorkerExecutor,
    ) : MergeArtProfileTask() {

        override val workerExecutor = testWorkerExecutor
    }

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        inputFiles = project.files()
        artProfileSourceFile =
            project.objects.fileProperty().also { it.set(File("/does/not/exist")) }
        baselineProfileSourceDirs = project.objects.listProperty(Directory::class.java).empty()
        task = project.tasks.register(
            "mergeArtProfileTask",
            MergeArtProfileTaskForTest::class.java,
            FakeGradleWorkExecutor(project.objects, temporaryFolder.newFolder()),
        ).get()
        task.inputFiles.from(inputFiles)
        task.profileSourceDirectories.set(baselineProfileSourceDirs)
        task.profileSource.set(artProfileSourceFile)
        task.analyticsService.set(FakeNoOpAnalyticsService())
        task.ignoreFrom.set(emptySet())
        task.ignoreFromAllExternalDependencies.set(false)
        task.libraryArtifacts = FakeArtifactCollection(mutableSetOf())
        outputFile = temporaryFolder.newFile()
        task.outputFile.set(outputFile)
    }

    @Test
    fun `test with no files`() {
        task.inputFiles.from(project.files())
        task.taskAction()
        assertThat(outputFile.exists()).isFalse()
    }

    @Test
    fun `test single file`() {
        val singleFile = temporaryFolder.newFile().also {
            it.writeText("file 1 - line 1")
        }
        inputFiles.from(singleFile)
        task.taskAction()
        assertThat(task.outputFile.get().asFile.readText()).isEqualTo(singleFile.readText())
    }

    @Test
    fun `test single file with art profile source`() {
        val singleFile = temporaryFolder.newFile().also {
            it.writeText("file 1 - line 1")
        }
        inputFiles.from(singleFile)
        artProfileSourceFile.set(
            temporaryFolder.newFile().also {
                it.writeText("source-file 1 - line 1")
            }
        )
        task.taskAction()
        assertThat(task.outputFile.get().asFile.readText()).isEqualTo(
            "${singleFile.readText()}\n${artProfileSourceFile.asFile.get().readText()}"
        )
    }

    @Test
    fun `test multiple files`() {
        inputFiles.from(
            mutableListOf<File>().apply {
                repeat(5) { this.add(createTestFile(it)) }
            }
        )

        task.taskAction()

        val expectedResult = StringBuilder().apply {
            repeat(5) { this.append("file $it - line 1\n") }
        }.toString().trimEnd()
        assertThat(task.outputFile.get().asFile.readText()).isEqualTo(expectedResult)
    }

    @Test
    fun `test with multiple baseline profiles`() {
        val singleFile = temporaryFolder.newFile().also {
            it.writeText("file 1 - line 1")
        }
        inputFiles.from(singleFile)

        repeat(5) {
            val dir = project.objects.directoryProperty().fileValue(
                createTestFile(it)
            )
            baselineProfileSourceDirs.add(dir)
        }

        artProfileSourceFile.set(
            temporaryFolder.newFile().also {
                it.writeText("art-profile-source-file 1 - line 1")
            }
        )
        task.taskAction()

        val expectedResult = StringBuilder().apply {
            this.append("${singleFile.readText()}\n")
            repeat(5) { this.append("file $it - line 1\n") }
            this.append("${artProfileSourceFile.asFile.get().readText()}")
        }.toString()

        assertThat(task.outputFile.get().asFile.readText()).isEqualTo(expectedResult)
    }

    private fun createTestFile(fileIndex: Int) = temporaryFolder.newFile().also {
        it.writeText("file $fileIndex - line 1")
    }
}
