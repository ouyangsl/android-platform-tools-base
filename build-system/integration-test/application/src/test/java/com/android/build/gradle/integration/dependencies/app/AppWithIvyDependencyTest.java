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

package com.android.build.gradle.integration.dependencies.app;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test for Ivy dependencies.
 */
public class AppWithIvyDependencyTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithIvyDependency")
            .create();

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkCompilationDependingOnIvyJarFile() throws Exception {
        project.execute("assembleDebug");
    }
}
