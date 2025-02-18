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

import com.android.tools.debuggertests.EngineType.ANDROID
import com.android.tools.debuggertests.EngineType.ART
import com.android.tools.debuggertests.EngineType.SIMPLE
import kotlin.time.Duration.Companion.seconds
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.optional
import kotlinx.cli.vararg
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Updates golden files.
 *
 * Run with `bazel run //tools/base/debugger-tests:update-golden`
 *
 * When running from Intellij, add to VM options:
 * ```
 * --add-opens=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED
 * ```
 */
fun main(args: Array<String>) {
  val parser = ArgParser("UpdateGolden")
  val verbose by parser.option(ArgType.Boolean, shortName = "v").default(false)
  val noop by parser.option(ArgType.Boolean, shortName = "n").default(false)
  val type by parser.option(ArgType.Choice<EngineType>(), shortName = "t")
  val serialNumber by parser.option(ArgType.String, shortName = "s")
  val tests by parser.argument(ArgType.String).vararg().optional()
  parser.parse(args)

  val testClasses = tests.takeIf { it.isNotEmpty() } ?: Resources.findTestClasses()

  val t = type // so we can smart-cast
  val engineTypes = if (t != null) listOf(t) else listOf(SIMPLE, ART)
  engineTypes.forEach { engineType ->
    val engine =
      when (engineType) {
        ANDROID -> AndroidEngine(serialNumber)
        ART -> ArtEngine()
        SIMPLE -> SimpleEngine()
      }
    println("Running tests for engine: $engineType")

    testClasses.forEach { testClass ->
      println("  Test $testClass")
      val localVariablesListener = LocalVariablesFrameListener()
      val inlineFramesListener = InlineStackFrameFrameListener()
      runBlocking {
        withTimeout(30.seconds) {
          engine.runTest(testClass) {
            localVariablesListener.onFrame(it)
            inlineFramesListener.onFrame(it)
          }
        }
      }
      val localVariablesText = localVariablesListener.getText()
      val inlineFramesText = inlineFramesListener.getText()
      if (!noop) {
        Resources.writeGolden(testClass, localVariablesText, engine.vmName)
        Resources.writeGolden(testClass, inlineFramesText, "inline-stack-frames")
      }
      if (verbose) {
        println(localVariablesText)
        println(inlineFramesText)
      }
    }
  }
}
