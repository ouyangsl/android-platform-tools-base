/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.buildsrc
import org.gradle.api.Project

class CloneArtifactsPlugin implements org.gradle.api.Plugin<Project> {

    private Extension extension

    @Override
    void apply(Project project) {

        extension = project.extensions.create('cloneArtifacts', Extension)

        def cloneArtifactsTask = project.tasks.add("cloneArtifacts", CloneArtifactsTask)
        cloneArtifactsTask.setDescription("Clone dependencies")
        cloneArtifactsTask.project = project
        cloneArtifactsTask.conventionMapping.repo =  { project.file(extension.repoLocation) }
    }
}
