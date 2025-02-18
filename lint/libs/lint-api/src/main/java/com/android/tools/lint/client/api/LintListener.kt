/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tools.lint.client.api

import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Project

/** Interface implemented by listeners to be notified of lint events. */
interface LintListener {
  /** The various types of events provided to lint listeners. */
  enum class EventType {
    REGISTERED_PROJECT,

    /** A lint check is about to begin. */
    STARTING,

    /** Lint is about to check the given project. */
    SCANNING_PROJECT,

    /** Lint is about to check the given library project. */
    SCANNING_LIBRARY_PROJECT,

    /** Lint is about to check the given file, see [Context.file] */
    SCANNING_FILE,

    /** A new pass was initiated. */
    NEW_PHASE,

    /** Lint is about to merge results. */
    MERGING,

    /** The lint check is done. */
    COMPLETED,
  }

  /**
   * Notifies listeners that the event of the given type has occurred. Additional information, such
   * as the file being scanned, or the project being scanned, is available in the [Context] object
   * (except for the [EventType.STARTING] and [EventType.COMPLETED] events which are fired outside
   * of project contexts.)
   *
   * @param driver the driver running through the checks
   * @param type the type of event that occurred
   * @param project the applicable project, if any
   * @param context the context providing additional information
   */
  fun update(
    driver: LintDriver,
    type: EventType,
    project: Project? = null,
    context: Context? = null,
  )
}
