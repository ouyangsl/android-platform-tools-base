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
package com.android.jdwpscache

import java.util.logging.Level
import java.util.logging.Logger

class SCacheTestLogger : SCacheLogger {

  private val logger = Logger.getLogger("SCacheTestLogger")

  override fun info(message: String) {
    logger.info(message)
  }

  override fun warn(message: String) {
    logger.warning(message)
  }

  override fun error(message: String) {
    logger.severe(message)
  }

  override fun error(message: String, t: Throwable) {
    logger.log(Level.SEVERE, message, t)
  }
}
