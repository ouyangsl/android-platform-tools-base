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

@file:JvmName("ModelContainerUtils")
package com.android.build.gradle.integration.common.utils

import com.android.SdkConstants.GRADLE_PATH_SEPARATOR
import com.android.build.gradle.integration.common.fixture.ModelContainerV2
import com.android.builder.core.ComponentTypeImpl
import com.google.common.collect.ImmutableList

/**
 * Returns the generates sources task list.
 *
 * @param projectToVariantName a function that returns the variant for a given project.
 */
fun ModelContainerV2.getGenerateSourcesCommands(projectToVariantName: (String) -> String): List<String> {
    val commands = ImmutableList.builder<String>()

    for ((projectPath, project) in  this.rootInfoMap) {
        val variant = project.androidProject!!.getVariantByName(projectToVariantName(projectPath))
        commands.add(createCommandTask(projectPath, variant.mainArtifact.sourceGenTaskName))
        variant.deviceTestArtifacts[ComponentTypeImpl.ANDROID_TEST.artifactName]?.let {
            commands.add(createCommandTask(projectPath, it.sourceGenTaskName))
        }
        variant.hostTestArtifacts.forEach { (_, v) ->
            for (taskName in v.ideSetupTaskNames) {
                commands.add(createCommandTask(projectPath, taskName))
            }
        }
    }
    return commands.build()
}

/**
 * Returns the generates sources commands for all projects for the debug variant.
 * These are the commands studio will call after sync.
 *
 * For example, for a project with a single app subproject these might be:
 *  * :app:generateDebugSources
 *  * :app:generateDebugAndroidTestSources
 *  * :app:mockableAndroidJar
 *  * :app:prepareDebugUnitTestDependencies
 *
 */
fun ModelContainerV2.getDebugGenerateSourcesCommands() =
    getGenerateSourcesCommands { _ -> "debug" }

private fun createCommandTask(projectPath: String, taskName: String) =
    if (projectPath == GRADLE_PATH_SEPARATOR) ":$taskName" else "$projectPath:$taskName"
