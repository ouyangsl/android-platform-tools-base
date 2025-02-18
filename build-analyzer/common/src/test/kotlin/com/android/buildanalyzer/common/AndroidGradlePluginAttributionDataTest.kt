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

package com.android.buildanalyzer.common

import com.android.SdkConstants
import com.android.buildanalyzer.common.AndroidGradlePluginAttributionData.BuildInfo
import com.android.buildanalyzer.common.AndroidGradlePluginAttributionData.JavaInfo
import com.android.buildanalyzer.common.AndroidGradlePluginAttributionData.TaskInfo
import com.android.buildanalyzer.common.AndroidGradlePluginAttributionData.TaskCategoryInfo
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

class AndroidGradlePluginAttributionDataTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val data = AndroidGradlePluginAttributionData(
        tasksSharingOutput = mapOf("e" to listOf("f", "g")),
        garbageCollectionData = mapOf("gc" to 100L),
        buildSrcPlugins = setOf("h", "i"),
        javaInfo = JavaInfo(
                version = "11.0.8",
                vendor = "JetBrains s.r.o",
                home = "/tmp/test/java/home",
                vmArguments = listOf("-Xmx8G", "-XX:+UseSerialGC")
        ),
        buildscriptDependenciesInfo = setOf(
                "a.a:a:1.0",
                "b.b:b:1.0",
                "c.c:c:1.0"
        ),
        buildInfo = BuildInfo(
                agpVersion = "8.1.0",
                configurationCacheIsOn = true,
                gradleVersion = "8.1.0"
        ),
        taskNameToTaskInfoMap = mapOf("a" to TaskInfo(
            className = "b",
            taskCategoryInfo = TaskCategoryInfo(
                primaryTaskCategory = TaskCategory.ANDROID_RESOURCES,
                secondaryTaskCategories = listOf(
                    TaskCategory.COMPILATION,
                    TaskCategory.SOURCE_PROCESSING
                ))),
            "c" to TaskInfo(className = "d", taskCategoryInfo = TaskCategoryInfo(primaryTaskCategory = TaskCategory.UNCATEGORIZED))
        ),
        taskCategoryIssues = listOf(
            TaskCategoryIssue.MINIFICATION_ENABLED_IN_DEBUG_BUILD,
            TaskCategoryIssue.NON_TRANSITIVE_R_CLASS_DISABLED
        )
    )

    private fun save(outputDir: File, attributionData: AndroidGradlePluginAttributionData) {
        val file = AndroidGradlePluginAttributionData.getAttributionFile(outputDir)
        file.parentFile.mkdirs()
        BufferedWriter(FileWriter(file)).use {
            it.write(AndroidGradlePluginAttributionData.AttributionDataAdapter.toJson(
                attributionData
            ))
        }
    }

    @Test
    fun testDataSerialization() {
        val outputDir = temporaryFolder.newFolder()
        save(outputDir, data)

        val file = FileUtils.join(
            outputDir,
            SdkConstants.FD_BUILD_ATTRIBUTION,
            SdkConstants.FN_AGP_ATTRIBUTION_DATA
        )
        assertThat(file.readLines()[0]).isEqualTo("""
|{
|"tasksSharingOutput":[{"filePath":"e","tasksList":["f","g"]}],
|"garbageCollectionData":[{"gcName":"gc","duration":100}],
|"buildSrcPlugins":["h","i"],
|"javaInfo":{
    |"javaVersion":"11.0.8",
    |"javaVendor":"JetBrains s.r.o",
    |"javaHome":"/tmp/test/java/home",
    |"vmArguments":["-Xmx8G","-XX:+UseSerialGC"]
|},
|"buildscriptDependencies":[
    |"a.a:a:1.0",
    |"b.b:b:1.0",
    |"c.c:c:1.0"
|],
|"buildInfo":{
    |"agpVersion":"8.1.0",
    |"gradleVersion":"8.1.0",
    |"configurationCacheIsOn":true
|},
|"taskNameToTaskInfoMap":[{"taskName":"a","className":"b","primaryTaskCategory":"ANDROID_RESOURCES",
                |"secondaryTaskCategories":["COMPILATION","SOURCE_PROCESSING"]},
                |{"taskName":"c","className":"d","primaryTaskCategory":"UNCATEGORIZED","secondaryTaskCategories":[]}
|],
|"taskCategoryIssues":["MINIFICATION_ENABLED_IN_DEBUG_BUILD","NON_TRANSITIVE_R_CLASS_DISABLED"]
|}
""".trimMargin().replace("\n", "")
        )
    }

    @Test
    fun testDeserializationOfOldAgpData() {
        val outputDir = temporaryFolder.newFolder()
        // Create file of old format with some data missing.
        val file = FileUtils.join(
            outputDir,
            SdkConstants.FD_BUILD_ATTRIBUTION,
            SdkConstants.FN_AGP_ATTRIBUTION_DATA
        )
        file.parentFile.mkdirs()
        file.writeText("""
|{
|"taskNameToClassNameMap":[{"taskName":"a","className":"b"},{"taskName":"c","className":"d"}],
|"tasksSharingOutput":[{"filePath":"e","tasksList":["f","g"]}],
|"buildSrcPlugins":["h","i"]
|}
""".trimMargin().replace("\n", "")
        )

        val deserializedData = AndroidGradlePluginAttributionData.load(outputDir)!!

        fun Map<String, TaskInfo>.normalizeForOlderAgpVersions(): Map<String, TaskInfo> {
            return map {
                it.key to TaskInfo(
                    className = it.value.className,
                    // This data isn't available in older AGP versions
                    taskCategoryInfo = TaskCategoryInfo(TaskCategory.UNCATEGORIZED)
                )
            }.toMap()
        }

        assertThat(
            deserializedData.taskNameToTaskInfoMap
        ).containsExactlyEntriesIn(data.taskNameToTaskInfoMap.normalizeForOlderAgpVersions())

        assertThat(deserializedData.tasksSharingOutput).isEqualTo(data.tasksSharingOutput)
        assertThat(deserializedData.buildSrcPlugins).isEqualTo(data.buildSrcPlugins)

        assertThat(deserializedData.garbageCollectionData).isNotNull()
        assertThat(deserializedData.garbageCollectionData).isEmpty()
    }

    @Test
    fun testDeserializationOfAgp_8_0_DataWorks() {
        val outputDir = temporaryFolder.newFolder()
        // Create file of old format with some data missing.
        val file = FileUtils.join(
            outputDir,
            SdkConstants.FD_BUILD_ATTRIBUTION,
            SdkConstants.FN_AGP_ATTRIBUTION_DATA
        )
        file.parentFile.mkdirs()
        file.writeText("""
|{
|"tasksSharingOutput":[{"filePath":"e","tasksList":["f","g"]}],
|"garbageCollectionData":[{"gcName":"gc","duration":100}],
|"buildSrcPlugins":["h","i"],
|"javaInfo":{
    |"javaVersion":"11.0.8",
    |"javaVendor":"JetBrains s.r.o",
    |"javaHome":"/tmp/test/java/home",
    |"vmArguments":["-Xmx8G","-XX:+UseSerialGC"]
|},
|"buildscriptDependencies":[
    |"a.a:a:1.0",
    |"b.b:b:1.0",
    |"c.c:c:1.0"
|],
|"buildInfo":{
    |"agpVersion":"8.0.0",
    |"configurationCacheIsOn":true
|},
|"taskNameToTaskInfoMap":[{"taskName":"a","className":"b","primaryTaskCategory":"ANDROID_RESOURCES",
                |"secondaryTaskCategories":["COMPILATION","SOURCE_PROCESSING"]},
                |{"taskName":"c","className":"d","primaryTaskCategory":"UNCATEGORIZED","secondaryTaskCategories":[]}
|],
|"taskCategoryIssues":["MINIFICATION_ENABLED_IN_DEBUG_BUILD","NON_TRANSITIVE_R_CLASS_DISABLED"]
|}
""".trimMargin().replace("\n", "")
        )

        val deserializedData = AndroidGradlePluginAttributionData.load(outputDir)
        assertThat(deserializedData).isNotNull()
    }

    @Test
    fun testDeserializationOfNewerAgpData() {
        val outputDir = temporaryFolder.newFolder()
        save(outputDir, data)

        // modify the file to add a new data field at the end
        val file = FileUtils.join(
            outputDir,
            SdkConstants.FD_BUILD_ATTRIBUTION,
            SdkConstants.FN_AGP_ATTRIBUTION_DATA
        )
        file.parentFile.mkdirs()
        file.writeText("""
|{
|"newUndefinedData":{"temp":"test"},
|"taskNameToClassNameMap":[{"taskName":"a","className":"b"},{"taskName":"c","className":"d"}],
|"tasksSharingOutput":[{"filePath":"e","tasksList":["f","g"]}],
|"garbageCollectionData":[{"gcName":"gc","duration":100}],
|"buildSrcPlugins":["h","i"],
|"javaInfo":{
    |"javaVersion":"11.0.8",
    |"javaVendor":"JetBrains s.r.o",
    |"javaHome":"/tmp/test/java/home",
    |"vmArguments":["-Xmx8G","-XX:+UseSerialGC"]
|},
|"newerUndefinedData":{"temp":"test"},
|"buildscriptDependencies":[
    |"a.a:a:1.0",
    |"b.b:b:1.0",
    |"c.c:c:1.0"
|],
|"buildInfo":{
    |"agpVersion":"8.1.0",
    |"gradleVersion":"8.1.0",
    |"configurationCacheIsOn":true
|},
|"taskNameToTaskInfoMap":[{"taskName":"a","className":"b","primaryTaskCategory":"ANDROID_RESOURCES",
|"secondaryTaskCategories":["COMPILATION","SOURCE_PROCESSING"]},
|{"taskName":"c","className":"d","primaryTaskCategory":"UNCATEGORIZED","secondaryTaskCategories":[]}
|],
|"taskCategoryIssues":["MINIFICATION_ENABLED_IN_DEBUG_BUILD","NON_TRANSITIVE_R_CLASS_DISABLED"]
|}
""".trimMargin().replace("\n", "")
        )

        val deserializedData = AndroidGradlePluginAttributionData.load(outputDir)!!

        assertThat(deserializedData).isEqualTo(data)
    }

    @Test
    fun testEmptyBuildInfo() {
        val outputDir = temporaryFolder.newFolder()
        val data = AndroidGradlePluginAttributionData(buildInfo = BuildInfo(null, null, null))

        save(outputDir, data)
        val deserializedData = AndroidGradlePluginAttributionData.load(outputDir)!!

        assertThat(deserializedData).isEqualTo(data)
    }

    @Test
    fun testEmptyData() {
        val outputDir = temporaryFolder.newFolder()
        val data = AndroidGradlePluginAttributionData()

        save(outputDir, data)
        val deserializedData = AndroidGradlePluginAttributionData.load(outputDir)!!

        assertThat(deserializedData).isEqualTo(data)
    }
}
