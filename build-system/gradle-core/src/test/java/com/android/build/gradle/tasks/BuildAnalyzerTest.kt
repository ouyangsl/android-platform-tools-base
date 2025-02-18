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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.internal.tasks.AppPreBuildTask
import com.android.build.gradle.internal.tasks.BaseTask
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.GeneratePrivacySandboxSdkRuntimeConfigFile
import com.android.build.gradle.internal.tasks.AndroidGlobalTask
import com.android.build.gradle.internal.tasks.NewIncrementalTask
import com.android.build.gradle.internal.tasks.NonIncrementalGlobalTask
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.TestPreBuildTask
import com.android.build.gradle.internal.tasks.UnsafeOutputsGlobalTask
import com.android.build.gradle.internal.tasks.UnsafeOutputsTask
import com.android.buildanalyzer.common.TaskCategory
import org.gradle.api.Task
import com.google.common.reflect.ClassPath
import com.google.common.reflect.TypeToken
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class BuildAnalyzerTest {

    // Allow-list of tasks that are defined to not have annotations.
    // Usually due to it being a base task, and hence is implemented by other tasks.
    // Another reason is that it does not fall into any TaskCategory,
    // and the task will not show up in Build Analyzer anyway.
    private val expectedTasksWithoutAnnotations = listOf(
            BaseTask::class.java,
            AndroidVariantTask::class.java,
            NewIncrementalTask::class.java,
            NonIncrementalTask::class.java,
            UnsafeOutputsTask::class.java,
            AndroidGlobalTask::class.java,
            NonIncrementalGlobalTask::class.java,
            UnsafeOutputsGlobalTask::class.java,
            AppPreBuildTask::class.java,
            TestPreBuildTask::class.java,
            PackageAndroidArtifact::class.java,
            BuildPrivacySandboxSdkApks::class.java,
            GeneratePrivacySandboxAsar::class.java,
            GeneratePrivacySandboxSdkRuntimeConfigFile::class.java,
    )

    @Test
    fun `all tasks have build analyzer annotations unless in allow-list`() {
        val allTasks = getAllTasks()
        val actualTasksWithoutAnnotations = allTasks.filter {
            !it.isAnnotationPresent(BuildAnalyzer::class.java)
        }
        assertWithMessage("These tasks do not have BuildAnalyzer annotations, either add this annotation to the task class " +
                "or add to expectedTasksWithoutAnnotations in BuildAnalyzerTest.kt. Refer to TaskCategory.kt " +
                "on guidance on how to label task classes.").that(
                actualTasksWithoutAnnotations).containsExactlyElementsIn(expectedTasksWithoutAnnotations)
    }

    @Test
    fun `BuildAnalyzer annotated tasks does not have unallowed categories`() {
        val allTasks = getAllTasks()
        val unallowedCategories = listOf(TaskCategory.GRADLE, TaskCategory.UNCATEGORIZED)
        val tasksWithUnallowedCategories = allTasks.filter {
            it.isAnnotationPresent(BuildAnalyzer::class.java)
        }.map {
            it.getAnnotation(BuildAnalyzer::class.java) }.filter { annotation ->
            annotation.primaryTaskCategory in unallowedCategories ||
                    annotation.secondaryTaskCategories.any { it in unallowedCategories }
        }
        assertWithMessage("These tasks have GRADLE or UNKNOWN task category, these categories " +
                "should not be added manually as it is handled on the IDE side.").that(
                tasksWithUnallowedCategories).isEmpty()
    }

    @Test
    fun `primaryTaskCategory field only has primary task categories`() {
        val allTasks = getAllTasks()
        val tasksWithIncorrectPrimaryTaskCategories = allTasks.filter {
            it.isAnnotationPresent(BuildAnalyzer::class.java)
        }.filter {
            !it.getAnnotation(BuildAnalyzer::class.java).primaryTaskCategory.isPrimary
        }
        assertWithMessage(
            "These tasks should only have the allowed primary task categories" +
                    " as the primaryTaskCategory field. Refer to TaskCategory on which categories" +
                    " can be primary."
        ).that(
            tasksWithIncorrectPrimaryTaskCategories
        ).isEmpty()
    }

    @Test
    fun `all categories in each BuildAnalyzer annotation are unique`() {
        val allTasks = getAllTasks()
        allTasks.filter {
            it.isAnnotationPresent(BuildAnalyzer::class.java)
        }.map { clazz ->
            clazz.getAnnotation(BuildAnalyzer::class.java)
        }.forEach {
            assertThat(listOf(it.primaryTaskCategory) + it.secondaryTaskCategories).containsNoDuplicates()
        }
    }

    private fun getAllTasks(): List<Class<*>> {
        val classPath = ClassPath.from(this.javaClass.classLoader)
        val taskInterface = TypeToken.of(Task::class.java)
        return classPath
                .getTopLevelClassesRecursive("com.android.build")
                .map{
                    classInfo -> classInfo.load() as Class<*>
                }
                .filter{
                    TypeToken.of(it).getTypes().contains(taskInterface)
                }
    }
}
