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

package com.android.build.gradle.integration.attribution

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.StringOption
import com.android.buildanalyzer.common.AndroidGradlePluginAttributionData
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BuildAttributionDataTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @get:Rule
    var project = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create()

    private fun setUpProject() {
        TestFileUtils.appendToFile(project.buildFile, """
          abstract class SampleTask extends DefaultTask {
              @OutputDirectory
              abstract DirectoryProperty getOutputDir()

              @TaskAction
              def run() {
                  // do nothing
              }
          }

          task sample1(type: SampleTask) {
              outputDir = file("${"$"}buildDir/outputs/shared_output")
          }

          task sample2(type: SampleTask) {
              outputDir = file("${"$"}buildDir/outputs/shared_output")
          }

          afterEvaluate { project ->
              android.applicationVariants.all { variant ->
                  def mergeResourcesTask = tasks.getByPath("merge${"$"}{variant.name.capitalize()}Resources")
                  mergeResourcesTask.dependsOn sample1
                  mergeResourcesTask.dependsOn sample2
              }
              sample2.dependsOn sample1
          }
        """.trimIndent())
    }

    @Test
    fun testBuildAttributionReport() {
        setUpProject()

        val attributionFileLocation = temporaryFolder.newFolder()

        project.executor()
                .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
                .with(StringOption.IDE_ATTRIBUTION_FILE_LOCATION,
                        attributionFileLocation.absolutePath)
                .run("mergeDebugResources")

        val originalAttributionData =
                AndroidGradlePluginAttributionData.load(attributionFileLocation)!!

        assertThat(originalAttributionData.taskNameToTaskInfoMap).isNotEmpty()
        assertThat(originalAttributionData.tasksSharingOutput).isNotEmpty()

        // delete the report and re-run

        FileUtils.deleteDirectoryContents(attributionFileLocation)

        project.executor()
                .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
                .with(StringOption.IDE_ATTRIBUTION_FILE_LOCATION,
                        attributionFileLocation.absolutePath)
                .run("mergeDebugResources")

        val newAttributionData =
                AndroidGradlePluginAttributionData.load(attributionFileLocation)!!

        assertThat(newAttributionData.taskNameToTaskInfoMap).containsExactlyEntriesIn(
                originalAttributionData.taskNameToTaskInfoMap)
        assertThat(newAttributionData.tasksSharingOutput).containsExactlyEntriesIn(
                originalAttributionData.tasksSharingOutput)
    }
}
