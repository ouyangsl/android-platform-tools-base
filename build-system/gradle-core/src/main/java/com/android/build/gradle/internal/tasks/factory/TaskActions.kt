/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks.factory

import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.internal.tasks.BaseTask
import com.android.build.gradle.internal.tasks.AndroidGlobalTask
import com.android.build.gradle.internal.tasks.VariantAwareTask
import com.android.build.gradle.internal.tasks.configureVariantProperties
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

/**
 * Basic task information for creation
 */
interface TaskInformation<TaskT: Task> {
    /** The name of the task to be created.  */
    val name: String

    /** The class type of the task to created.  */
    val type: Class<TaskT>
}

/** Creation action for a [Task]. */
abstract class TaskCreationAction<TaskT : Task> :
    TaskInformation<TaskT>, PreConfigAction, TaskProviderCallback<TaskT>, TaskConfigAction<TaskT> {

    override fun preConfigure(taskName: String) {
        // default does nothing
    }

    override fun handleProvider(taskProvider: TaskProvider<TaskT>) {
        // default does nothing
    }

    override fun configure(task: TaskT) {
        // default does nothing
    }
}

/** [TaskCreationAction] for [BaseTask]. */
abstract class BaseTaskCreationAction<TaskT: BaseTask> : TaskCreationAction<TaskT>() {

    override fun configure(task: TaskT) {
        super.configure(task)

        configureBaseTask(task)
    }

    companion object {

        fun configureBaseTask(task: BaseTask) {
            task.projectPath.setDisallowChanges(task.project.path)
        }
    }
}

/** [TaskCreationAction] for a variant task. */
abstract class VariantTaskCreationAction<TaskT, CreationConfigT : ComponentCreationConfig>
    @JvmOverloads
    constructor(
        @JvmField protected val creationConfig: CreationConfigT,
        private val dependsOnPreBuildTask: Boolean = true
    ) : TaskCreationAction<TaskT>() where TaskT : Task, TaskT : VariantAwareTask {

    protected fun computeTaskName(prefix: String, suffix: String): String =
        creationConfig.computeTaskNameInternal(prefix, suffix)

    protected fun computeTaskName(prefix: String): String =
        creationConfig.computeTaskNameInternal(prefix)

    override fun configure(task: TaskT) {
        super.configure(task)

        if (task is BaseTask) {
            BaseTaskCreationAction.configureBaseTask(task)
        }

        task.configureVariantProperties(creationConfig.name, creationConfig.services.buildServiceRegistry)

        if (dependsOnPreBuildTask) {
            task.dependsOn(creationConfig.taskContainer.preBuildTask)
        }
    }
}

/**
 * [TaskCreationAction] for an [AndroidVariantTask].
 *
 * IMPORTANT: Use [VariantTaskCreationAction] instead if possible, which allows using
 * [ComponentCreationConfig] to configure the task.
 */
abstract class AndroidVariantTaskCreationAction<TaskT: AndroidVariantTask> : BaseTaskCreationAction<TaskT>() {

    override fun configure(task: TaskT) {
        super.configure(task)

        task.configureVariantProperties("", task.project.gradle.sharedServices)
    }
}

/** [TaskCreationAction] for an [AndroidGlobalTask]. */
abstract class GlobalTaskCreationAction<TaskT: AndroidGlobalTask>(
    @JvmField protected val creationConfig: GlobalTaskCreationConfig
) : BaseTaskCreationAction<TaskT>() {

    override fun configure(task: TaskT) {
        super.configure(task)

        task.analyticsService.setDisallowChanges(
            getBuildService(creationConfig.services.buildServiceRegistry)
        )
    }
}

/**
 * Configuration Action for tasks.
 */
interface TaskConfigAction<TaskT: Task> {

    /** Configures the task. */
    fun configure(task: TaskT)
}

/**
 * Pre-Configuration Action for lazily created tasks.
 */
interface PreConfigAction {
    /**
     * Pre-configures the task, acting on the taskName.
     *
     * This is meant to handle configuration that must happen always, even when the task
     * is configured lazily.
     *
     * @param taskName the task name
     */
    fun preConfigure(taskName: String)
}

/**
 * Callback for [TaskProvider]
 *
 * Once a TaskProvider is created this is called to process it.
 */
interface TaskProviderCallback<TaskT: Task> {
    fun handleProvider(taskProvider: TaskProvider<TaskT>)
}
