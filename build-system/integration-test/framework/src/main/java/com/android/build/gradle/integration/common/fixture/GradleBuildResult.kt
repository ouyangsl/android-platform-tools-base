/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.common.fixture

import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.truth.TaskStateList
import com.google.common.base.Preconditions
import com.google.common.base.Throwables
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.internal.serialize.ContextualPlaceholderException
import org.gradle.internal.serialize.PlaceholderException
import org.gradle.tooling.BuildException
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.events.ProgressEvent
import java.io.File
import java.util.Scanner

/**
 * The result from running a build.
 * See [GradleTestProject.executor] and [GradleTaskExecutor].
 *
 * @property exception The exception from the build, null if the build succeeded.
 */
class GradleBuildResult(
    private val stdoutFile: File,
    private val stderrFile: File,
    private val taskEvents: List<ProgressEvent>,
    val exception: GradleConnectionException?,
) {
    /**
     * Returns a new [Scanner] for the stderr messages. This instance MUST be closed when done.
     */
    val stderr
        get() = Scanner(stderrFile)

    @Suppress("unused") // Keep this property as it is useful for debugging
    val stderrAsText: String by lazy { stderr.asText() }

    /**
     * Returns a new [Scanner] for the stdout messages. This instance MUST be closed when done.
     */
    val stdout
        get() = Scanner(stdoutFile)

    @Suppress("unused") // Keep this property as it is useful for debugging
    @Deprecated(
        "This property is used for debugging only," +
                " do not actually use it in tests because stdout is often large" +
                " and can cause memory issues if loaded as a string" +
                " (stderr is fine)"
    )
    val stdoutAsTextForDebug: String by lazy { stdout.asText() }

    private fun Scanner.asText(): String = use {
        StringBuilder().apply {
            while (it.hasNextLine()) {
                appendLine(it.nextLine())
            }
        }.toString()
    }

    /**
     * Most tests don't examine the state of the build's tasks and [TaskStateList] is relatively
     * expensive to initialize, so this is done lazily.
     */
    private val taskStateList: TaskStateList by lazy {
        TaskStateList(taskEvents, this.stdout)
    }

    /**
     * Returns the short (single-line) message that Gradle would print out in the console, without
     * `--stacktrace`. If the build succeeded, returns null.
     */
    val failureMessage: String?
        get() = exception?.let {
            val causalChain = Throwables.getCausalChain(exception)
            // Try the common scenarios: configuration or task failure.
            for (throwable in causalChain) {
                // Because of different class loaders involved, we are forced to do stringly-typed
                // programming.
                val throwableType = throwable.javaClass.name
                if (throwableType == ProjectConfigurationException::class.java.name) {
                    return throwable.cause?.message ?: throw AssertionError(
                        "Exception had unexpected structure.",
                        exception
                    )
                } else if (isPlaceholderEx(throwableType)) {
                    if (throwable.toString().startsWith(TaskExecutionException::class.java.name)) {
                        var cause = throwable
                        // there can be several levels of PlaceholderException when dealing with
                        // Worker API failures.
                        while (isPlaceholderEx(throwableType) && cause.cause != null) {
                            cause = cause.cause
                        }
                        return cause.message
                    }
                }
            }

            // Look for any BuildException, for other cases.
            for (throwable in causalChain) {
                val throwableType = throwable.javaClass.name
                if (throwableType == BuildException::class.java.name) {
                    return throwable.cause?.message ?: throw AssertionError(
                        "Exception had unexpected structure.",
                        exception
                    )
                }
            }

            throw AssertionError("Failed to determine the failure message.", exception)
        }

    val tasks: List<String>
        get() = taskStateList.tasks

    val taskStates: Map<String, TaskStateList.ExecutionState>
        get() = taskStateList.taskStates

    val upToDateTasks: Set<String>
        get() = taskStateList.upToDateTasks

    val fromCacheTasks: Set<String>
        get() = taskStateList.fromCacheTasks

    val didWorkTasks: Set<String>
        get() = taskStateList.didWorkTasks

    val skippedTasks: Set<String>
        get() = taskStateList.skippedTasks

    val failedTasks: Set<String>
        get() = taskStateList.failedTasks

    /**
     * Returns the task info given the task name, or null if the task is not found (if it is not in
     * the task execution plan).
     *
     * @see getTask
     */
    fun findTask(name: String): TaskStateList.TaskInfo? {
        return taskStateList.findTask(name)
    }

    /**
     * Returns the task info given the task name. The task must exist (it must be in the task
     * execution plan).
     *
     * @see findTask
     */
    fun getTask(name: String): TaskStateList.TaskInfo {
        Preconditions.checkArgument(name.startsWith(":"), "Task name must start with :")
        return taskStateList.getTask(name)
    }

    private fun isPlaceholderEx(throwableType: String) =
        throwableType == PlaceholderException::class.java.name
                || throwableType == ContextualPlaceholderException::class.java.name

    fun assertOutputContains(text: String) {
        stdout.use {
            ScannerSubject.assertThat(it).contains(text)
        }
    }

    fun assertErrorContains(text: String) {
        stderr.use {
            ScannerSubject.assertThat(it).contains(text)
        }
    }

    fun assertOutputDoesNotContain(text: String) {
        stdout.use {
            ScannerSubject.assertThat(it).doesNotContain(text)
        }
    }

    /** Checks that the [GradleBuildResult] hit the configuration cache */
    fun assertConfigurationCacheHit() {
        assertOutputContains("Reusing configuration cache")
        assertOutputDoesNotContain("Calculating task graph")
    }

    /** Checks that the [GradleBuildResult] did not hit the configuration cache */
    fun assertConfigurationCacheMiss() {
        assertOutputContains("Calculating task graph")
        assertOutputDoesNotContain("Reusing configuration cache")
    }
}
