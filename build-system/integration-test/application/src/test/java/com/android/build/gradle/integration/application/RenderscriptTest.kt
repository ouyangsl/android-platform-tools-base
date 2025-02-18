/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.options.BooleanOption
import org.junit.Rule
import org.junit.Test

/** Assemble tests for renderscript with NDK mode enabled. */
class RenderscriptTest {

    @get:Rule
    var project = GradleTestProject.builder()
            .fromTestProject("BasicRenderScript")
            // Enforcing unique package names to prevent regressions. Remove when b/116109681 fixed.
            .addGradleProperties(
                    "${BooleanOption.ENFORCE_UNIQUE_PACKAGE_NAMES.propertyName}=true")
            .addGradleProperties("${BooleanOption.USE_ANDROID_X.propertyName}=true")
            .create()

    @Test
    fun build() {
        project.execute("clean", "assembleDebug")
    }

    @Test
    fun testApiLevelDoesNotTriggerError() {
        project.buildFile.appendText("android.defaultConfig.minSdkVersion 30")
        project.execute("clean", "assembleDebug")
    }
}
