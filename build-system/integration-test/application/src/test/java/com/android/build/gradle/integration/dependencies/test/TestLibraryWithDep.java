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

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import org.junit.Rule;
import org.junit.Test;

/** test for building a test APK for a library module with dependencies. */
public class TestLibraryWithDep {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("libTestDep").create();

    @Test
    public void checkLibDependencyJarIsPackaged() throws Exception {
        project.execute("clean", "assembleDebugAndroidTest");
        // Check for class from the library's Guava dependency
        TruthHelper.assertThat(project.getTestApk())
                .containsClass("Lcom/google/common/base/Splitter;");
    }

    @Test
    public void checkLocalAarDependencyJarIsPackaged() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n" + "dependencies {\n" + "    api files(\"libs/local.aar\")\n" + "}\n");
        project.execute("clean", "assembleDebugAndroidTest");
        TruthHelper.assertThat(project.getTestApk())
                .containsClass("Lcom/example/locallib/BuildConfig;");
        TruthHelper.assertThat(project.getTestApk())
                .containsJavaResource("com/example/localLibJavaRes.txt");
    }
}

