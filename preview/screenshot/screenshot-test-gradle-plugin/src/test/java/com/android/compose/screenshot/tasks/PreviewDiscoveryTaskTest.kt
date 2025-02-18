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

package com.android.compose.screenshot.tasks

import com.android.compose.screenshot.services.AnalyticsService
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Answers
import org.mockito.kotlin.mock
import java.io.File

class PreviewDiscoveryTaskTest {
    @get:Rule
    val tempDirRule = TemporaryFolder()

    private lateinit var task: PreviewDiscoveryTask

    @Before
    fun setUp() {
        val project = ProjectBuilder.builder().withProjectDir(tempDirRule.newFolder()).build()
        task = project.tasks.create("debugPreviewTest", PreviewDiscoveryTask::class.java)
    }

    @Test
    fun testPreviewDiscovery() {
        // create a new folder to hold the results
        val rootForResult = tempDirRule.newFolder("results")

        // uncreated paths for results
        val resultsDir = File(rootForResult, "results")
        task.resultsDir.set(resultsDir)
        task.previewsOutputFile.set(File(rootForResult,"previews_discovered.json"))

        task.analyticsService.set(object: AnalyticsService() {
            override val buildServiceRegistry: BuildServiceRegistry =
                mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
            override fun getParameters(): Params = mock()
        })

        task.run()

        assert(resultsDir.isDirectory)
    }
}
