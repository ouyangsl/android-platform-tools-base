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
package com.android.tools.debuggertests

/**
 * A simple [Engine] that launches and connects to a program.
 *
 * This is the easiest way to debug a program. It uses a [com.sun.jdi.connect.LaunchingConnector]
 * which launches and connects to the process.
 */
internal class SimpleEngine : Engine("jvm") {

  override suspend fun startDebugger(mainClass: String): Debugger {
    return DebuggerImpl.launch(mainClass, Resources.TEST_CLASSES_JAR).waitForStart()
  }
}
