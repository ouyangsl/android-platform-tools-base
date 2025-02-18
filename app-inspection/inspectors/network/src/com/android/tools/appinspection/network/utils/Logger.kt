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

package com.android.tools.appinspection.network.utils

import android.util.Log

// A Log tag that should not be filtered out by Android Studio.
private const val TAG = "Network Inspector"
// A Log tag that should be filtered out by Android Studio.
private const val HIDDEN_TAG = "studio.inspectors"

/** A simple logger interface */
internal object Logger {
  fun debug(msg: String) {
    debug(TAG, msg)
  }

  fun debugHidden(msg: String) {
    debug(HIDDEN_TAG, msg)
  }

  fun error(msg: String, t: Throwable? = null) {
    error(TAG, msg, t)
  }

  fun debug(tag: String, msg: String) {
    Log.d(tag, msg)
  }

  fun error(tag: String, msg: String, t: Throwable?) {
    Log.e(tag, msg, t)
  }
}
