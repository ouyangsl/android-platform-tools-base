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

import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Tests for {@link ProcessManifest}
 */
class ProcessLibraryManifestTest {

    @Rule @JvmField var temporaryFolder = TemporaryFolder()

    internal lateinit var task: ProcessLibraryManifest

    abstract class TestProcessLibraryManifest @Inject constructor(testWorkerExecutor: WorkerExecutor) :
            ProcessLibraryManifest() {
        override val workerExecutor = testWorkerExecutor
    }

    @Before
    @Throws(IOException::class)
    fun setUp() {

        val project: Project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        val taskProvider = project.tasks.register("fooRelease", TestProcessLibraryManifest::class.java,
                FakeGradleWorkExecutor(project.objects, temporaryFolder.newFolder()))
        task = taskProvider.get()

        task.minSdkVersion.set("1")
        task.maxSdkVersion.set(1)
        task.targetSdkVersion.set("1")
        task.namespace.set("com.example.foo")
        task.manifestPlaceholders.set(mapOf())
        task.analyticsService.set(FakeNoOpAnalyticsService())
    }

    @Test
    fun testInputsAreAnnotatedCorrectly() {
        assertThat(task.inputs.properties).containsKey("maxSdkVersion")
        assertThat(task.inputs.properties).containsKey("minSdkVersion")
        assertThat(task.inputs.properties).containsKey("targetSdkVersion")
        assertThat(task.inputs.properties).containsKey("manifestPlaceholders")
        assertThat(task.inputs.properties).containsKey("namespace")
    }

    @Test
    fun testNoSourceManifest() {
        task.variantName = "release"
        task.namespace.set("random.word")
        task.tmpDir.set(temporaryFolder.newFolder("a", "b", "c"))
        task.manifestOutputFile.set(temporaryFolder.newFile())
        task.reportFile.set(temporaryFolder.newFile())
        task.mergeBlameFile.set(temporaryFolder.newFile())
        task.disableMinSdkVersionCheck.set(false)
        task.mainManifest.set(File("/does/not/exist"))
        task.taskAction()
        assertThat(task.manifestOutputFile.get().asFile.readText(Charsets.UTF_8))
                .contains("package=\"random.word\"")
        assertThat(task.tmpDir.get().asFileTree.files).isEmpty()
    }
}
