/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.build.gradle.integration.dagger

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests that the hilt plugin is able to resolve flavored dependencies correctly
 */
class DaggerHiltFlavoredTest {
    @get:Rule
    var project = GradleTestProject.builder()
        .fromTestProject("dagger-hilt-flavored-project")
        .create()

    @Before
    fun setAgpVersion() {
        TestFileUtils.searchAndReplace(project.buildFile,
            "version '+'",
            "version '" + GradleTestProject.ANDROID_GRADLE_PLUGIN_VERSION + "'")
    }

    @Test
    fun doBuild() {
        project.executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
            .run(":app:assembleMinApi21DemoDebug")
    }
}
