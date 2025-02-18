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

package com.android.build.gradle.integration.library;

import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;

import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

/** Assemble tests for libMinifyLibDep. */
public class LibMinifyLibDepTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("libMinifyLibDep").create();

    @Test
    public void lint() throws IOException, InterruptedException {
        executor().run("lint");
    }

    @Test
    public void checkMapping() throws Exception {
        executor().run("assembleDebug");
        File mapping = project.getSubproject("lib").file("build/outputs/mapping/debug/mapping.txt");
        // Check classes are obfuscated unless it is kept by the proguard configuration.
        assertThat(mapping)
                .containsAllOf(
                        "com.android.tests.basic.StringGetter ->"
                                + " com.android.tests.basic.StringGetter",
                        "com.android.tests.internal.StringGetterInternal ->"
                                + " com.android.tests.internal.StringGetterInternal");
    }

    @Test
    public void checkTestAssemblyWithR8() throws Exception {
        executor().run("assembleAndroidTest");
    }

    public GradleTaskExecutor executor() {
        return project.executor();
    }
}
