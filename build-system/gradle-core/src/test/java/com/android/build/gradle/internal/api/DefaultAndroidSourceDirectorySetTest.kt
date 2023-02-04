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

package com.android.build.gradle.internal.api

import com.android.build.gradle.internal.api.artifact.SourceArtifactType
import com.android.build.gradle.internal.fixtures.ProjectFactory
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DefaultAndroidSourceDirectorySetTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Before
    fun setUp() {
        temporaryFolder.newFolder("java_1").let {
            File(it, "Test_1_1.java").createNewFile()
            File(it, "Test_1_2.java").createNewFile()
        }
        temporaryFolder.newFolder("java_2").let {
            File(it, "Test_2_1.java").createNewFile()
            File(it, "Test_2_2.java").createNewFile()
        }
    }

    @Test
    fun testIterableFiles() {
        val sourceDirectorySet = DefaultAndroidSourceDirectorySet(
            "Java",
            "Java source",
            ProjectFactory.project,
            SourceArtifactType.JAVA_SOURCES
        )

        sourceDirectorySet.srcDir(
            listOf(
                File(temporaryFolder.root, "java_1"),
                File(temporaryFolder.root, "java_2"),
            )
        )

        val files = sourceDirectorySet.getSourceFiles().files.map { it.name }

        assertThat(files).hasSize(4)
        assertThat(files).containsExactlyElementsIn(
            listOf("Test_1_1.java", "Test_1_2.java", "Test_2_1.java", "Test_2_2.java")
        )
    }

    @Test
    fun testIterablePaths() {
        val sourceDirectorySet = DefaultAndroidSourceDirectorySet(
            "Java",
            "Java source",
            ProjectFactory.project,
            SourceArtifactType.JAVA_SOURCES
        )

        sourceDirectorySet.srcDir(
            listOf(
                File(temporaryFolder.root, "java_1").toPath(),
                File(temporaryFolder.root, "java_2").toPath(),
            )
        )

        val files = sourceDirectorySet.getSourceFiles().files.map { it.name }

        assertThat(files).hasSize(4)
        assertThat(files).containsExactlyElementsIn(
            listOf("Test_1_1.java", "Test_1_2.java", "Test_2_1.java", "Test_2_2.java")
        )
    }
}
