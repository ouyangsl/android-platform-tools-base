/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.external.gnumake

import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValue
import com.google.common.collect.Maps
import com.google.common.truth.Truth

/**
 * Compares all commands in the -nB output file to the commands that could be classified.
 * They should be the same. If there's a test-only case, then add a test-only classifier to
 * [NdkSampleTest.extraTestClassifiers].
 */
internal fun checkAllCommandsRecognized(
    allCommands: List<CommandLine?>,
    classifiableCommands: List<BuildStepInfo>
) {
    // Check that outputs occur only once
    val outputs: MutableMap<String?, BuildStepInfo> = Maps.newHashMap()
    for (classifiableCommand in classifiableCommands) {
        for (output in classifiableCommand.outputs) {
            // Check for duplicate names
            Truth.assertThat(outputs.keys).doesNotContain(output)
            outputs[output] = classifiableCommand
        }
    }
    if (allCommands.size != classifiableCommands.size) {
        // Build a set of executable commands that were classified.
        val classifiableCommandExecutables = classifiableCommands
            .map { it.command.executable }

        // Build a set of executable commands that were classified.
        val unclassifiedExecutables = allCommands
            .filterNotNull()
            .map { it.executable }
            .filter { !classifiableCommandExecutables.contains(it) }
            .toSet()

        error("Could not classify $unclassifiedExecutables")
    }
}

/**
 * Check whether the ABIs found match recognized ABIs. Even very old ABIs like "mips" are
 * recognized here because we have test samples that reference them.
 */
fun checkOutputsHaveAllowedAbis(configs: List<NativeBuildConfigValue>) {
    val abis = listOf("x86", "x86_64", "arm64-v8a", "mips", "mips64", "armeabi-v7a", "armeabi")
    for (config in configs) {
        for (library in config.libraries!!.values) {
            if (!abis.contains(library.abi)) {
                error("Library ABI ${library.abi} is unexpected")
            }
        }
    }
}

/**
 * Check that all output files have recognized extensions.
 */
fun checkOutputsHaveAllowedExtensions(configs: List<NativeBuildConfigValue>) {
    for (config in configs) {
        for (library in config.libraries!!.values) {
            // These are the three extensions that should occur:
            // .so -- shared object
            // .a -- archive
            // <none> -- executable
            if (library.output.toString().endsWith(".so")) {
                continue
            }
            if (library.output.toString().endsWith(".a")) {
                continue
            }
            if (!library.output.toString().contains(".")) {
                continue
            }
            error("Library output ${library.output} had an unexpected extension")
        }
    }
}
