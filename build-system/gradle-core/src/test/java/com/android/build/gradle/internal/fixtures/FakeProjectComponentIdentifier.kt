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

package com.android.build.gradle.internal.fixtures

import org.gradle.api.Incubating
import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier

data class FakeProjectComponentIdentifier(
    private val projectPath: String,
    private val displayName: String = projectPath,
    private val buildIdentifier: BuildIdentifier,
) : ProjectComponentIdentifier {

    override fun getDisplayName() = displayName
    override fun getProjectPath(): String = projectPath
    @Incubating
    override fun getBuildTreePath(): String {
        TODO("Not yet implemented")
    }

    override fun getBuild(): BuildIdentifier = buildIdentifier

    override fun getProjectName(): String {
        if (projectPath == ":") return projectPath
        return projectPath.split(":").last()
    }
}
