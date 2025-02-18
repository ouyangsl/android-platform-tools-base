/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.integration.dependencies.test;

import static com.android.build.gradle.integration.common.fixture.TestVersions.SUPPORT_LIB_MIN_SDK;
import static com.android.build.gradle.integration.common.fixture.TestVersions.SUPPORT_LIB_VERSION;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test dependency on a remote library that brings in a transitive
 * dependency that is already present in the main app.
 */
public class TestWithRemoteAndroidLibDepTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();

    @BeforeClass
    public static void setUp() throws Exception {
        project.setIncludedProjects("app", "library");

        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n"
                        + "android.defaultConfig.minSdkVersion "
                        + SUPPORT_LIB_MIN_SDK
                        + "\n"
                        + "dependencies {\n"
                        + "    api 'com.android.support:support-v4:"
                        + SUPPORT_LIB_VERSION
                        + "'\n"
                        + "    androidTestImplementation 'com.android.support:appcompat-v7:"
                        + SUPPORT_LIB_VERSION
                        + "'\n"
                        + "}\n");

        appendToFile(
                project.getSubproject("library").getBuildFile(),
                "\n"
                        + "dependencies {\n"
                        + "    api 'com.android.support:support-v4:"
                        + SUPPORT_LIB_VERSION
                        + "'\n"
                        + "}\n");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkBuild() throws Exception {
        project.execute("app:assembleDebugAndroidTest");
    }
}
