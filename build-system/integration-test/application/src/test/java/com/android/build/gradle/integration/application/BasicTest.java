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

package com.android.build.gradle.integration.application;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.category.SmokeTests;
import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;

import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Assemble tests for basic.
 */
@Category(SmokeTests.class)
public class BasicTest {

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("basic")
                    .create();

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void report() throws Exception {
        project.executor().withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
                .run("androidDependencies");
    }

    @Test
    public void weDontFailOnLicenceDotTxtWhenPackagingDependencies() {
        project.execute("assembleAndroidTest");
    }

    @Test
    public void testRenderscriptDidNotRun() throws Exception {
        // First enable renderscript, then execute renderscript task and check if it was skipped
        TestFileUtils.appendToFile(
                project.getBuildFile(), "android.buildFeatures.renderScript true");
        GradleBuildResult result = project.execute("compileDebugRenderscript");
        assertThat(result.getTask(":compileDebugRenderscript").getExecutionState().toString())
                .isEqualTo("SKIPPED");
    }
}
