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

package com.android.build.gradle.internal.tasks

import com.android.Version
import com.android.build.gradle.internal.fixtures.FakeGradleWorkExecutor
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.google.common.truth.Truth.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.workers.WorkerExecutor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.inject.Inject

/**
 * Unit tests for [AndroidLintAnalysisTask].
 */
class AndroidLintAnalysisTaskTest {

    @get: Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var task: AndroidLintAnalysisTask

    abstract class TaskForTest @Inject constructor(testWorkerExecutor: WorkerExecutor) :
        AndroidLintAnalysisTask() {
        override val workerExecutor = testWorkerExecutor
    }

    @Before
    fun setUp() {
        val project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        task = project.tasks.register(
            "androidLintAnalysisTask",
            TaskForTest::class.java,
            FakeGradleWorkExecutor(project.objects, temporaryFolder.newFolder())
        ).get()
        task.analyticsService.set(FakeNoOpAnalyticsService())
    }

    @Test
    fun testGenerateCommandLineArguments() {
        task.fatalOnly.set(false)
        task.systemPropertyInputs.javaHome.set("javaHome")
        task.androidSdkHome.set("androidSdkHome")
        task.lintModelDirectory.set(temporaryFolder.newFolder())
        task.printStackTrace.set(true)
        task.lintTool.lintCacheDirectory.set(temporaryFolder.newFolder())
        task.lintTool.versionKey.set(Version.ANDROID_TOOLS_BASE_VERSION + "_foo")
        task.offline.set(true)
        task.uastInputs.useK2UastManualSetting.set(true)
        val commandLineArguments = task.generateCommandLineArguments().joinToString(" ")
        assertThat(commandLineArguments).contains("--client-id gradle")
        assertThat(commandLineArguments).contains("--client-name AGP")
        assertThat(commandLineArguments)
            .contains("--client-version ${Version.ANDROID_GRADLE_PLUGIN_VERSION}")
        assertThat(commandLineArguments).contains("--offline")
        assertThat(commandLineArguments).contains("--stacktrace")
        assertThat(commandLineArguments).contains("--XuseK2Uast")
    }
}
