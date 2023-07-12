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

@file:JvmName("BuildMappingUtils")
package com.android.build.gradle.internal.ide.dependencies

import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier

// For Gradle source dependencies we cannot get a name, so use this one.
const val UNKNOWN_BUILD_NAME = "__unknown__"

fun ProjectComponentIdentifier.getIdString(): String {
    return projectPath.apply {
        (build as BuildIdentifier?)?.let { plus(":${it.name}") }
    }
}
