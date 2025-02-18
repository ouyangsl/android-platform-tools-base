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

package com.android.tools.render.compose

/**
 * Data class that provider information about the problems happened during a single screenshot
 * creation.
 */
data class ScreenshotError(
    val status: String,
    val message: String,
    val stackTrace: String,
    val problems: List<RenderProblem>,
    val brokenClasses: List<BrokenClass>,
    val missingClasses: List<String>
) {
    constructor(t: Throwable) : this(
        "",
        t.message ?: "",
        t.stackTraceToString(),
        emptyList(),
        emptyList(),
        emptyList()
    )
}

/** Serializable representation of a rendering problem. */
data class RenderProblem(
    val html: String,
    val stackTrace: String?,
)

/** Serializable representation of a broken class found during rendering. */
data class BrokenClass(
    val className: String,
    val stackTrace: String,
)
