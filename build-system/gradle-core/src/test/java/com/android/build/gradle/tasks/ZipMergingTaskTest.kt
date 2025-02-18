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

import com.android.testutils.truth.PathSubject.assertThat
import com.android.testutils.truth.ZipFileSubject.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Test for ZipMergingTask.  */
class ZipMergingTaskTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    @Throws(IOException::class)
    fun merge() {
        val zip = temporaryFolder.newFile("file1.zip")
        val folder = temporaryFolder.newFolder("file2")

        createZip(zip, "foo.txt", "foo")
        File(folder, "bar.txt").writeText("bar")

        val testDir = temporaryFolder.newFolder()
        val project = ProjectBuilder.builder().withProjectDir(testDir).build()

        val output = File(temporaryFolder.newFolder(), "output.zip")
        val task = project.tasks.create("test", ZipMergingTask::class.java)

        task.libraryInputFile.set(zip)
        task.javaResDirectory.set(folder)
        task.outputFile.set(output)
        task.doTaskAction()

        assertThat(output).exists()

        assertThat(output) {
            it.containsFileWithContent("foo.txt", "foo")
            it.containsFileWithContent("bar.txt", "bar")
        }
    }

    @Test
    fun mergeDuplicates() {
        val zip = temporaryFolder.newFile("file1.zip")
        val folder = temporaryFolder.newFolder("file2")

        createZip(zip, "foo.txt", "foo")
        File(folder, "foo.txt").writeText("foo")

        val testDir = temporaryFolder.newFolder()
        val project = ProjectBuilder.builder().withProjectDir(testDir).build()

        val output = File(temporaryFolder.newFolder(), "output.zip")
        val task = project.tasks.create("test", ZipMergingTask::class.java)

        task.libraryInputFile.set(zip)
        task.javaResDirectory.set(folder)
        task.outputFile.set(output)
        task.doTaskAction()

        assertThat(output).exists()

        assertThat(output) {
            it.containsFileWithContent("foo.txt", "foo")
        }
    }

    @Throws(IOException::class)
    private fun createZip(file: File, entry: String, content: String) {
        FileOutputStream(file).use { fos ->
            BufferedOutputStream(fos).use { bos ->
                ZipOutputStream(bos).use { zos ->
                    zos.putNextEntry(ZipEntry(entry))
                    zos.write(content.toByteArray())
                    zos.closeEntry()
                }
            }
        }
    }
}
