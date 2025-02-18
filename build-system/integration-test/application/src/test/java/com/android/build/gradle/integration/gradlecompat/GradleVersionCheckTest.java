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

package com.android.build.gradle.integration.gradlecompat;

import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.google.common.base.Throwables;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;

/** Tests whether the Gradle version check takes effect. */
public class GradleVersionCheckTest {

    /**
     * An old version of Gradle to use in this test.
     *
     * <p>(This can't be lower than 8.4 as those Gradle versions do not support JDK 21, see bug
     * 243592738.)
     */
    private static final String OLD_GRADLE_VERSION = "8.4";

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .setTargetGradleVersion(OLD_GRADLE_VERSION)
                    .create();

    @Test
    public void testGradleVersionCheck() throws IOException {
        // Run the build twice, it should fail with the same message
        // (regression test for bug 265296706)
        for (int i = 1; i <= 2; i++) {
            try {
                project.executor().run("help");
            } catch (Exception e) {
                assertThat(Throwables.getRootCause(e).getMessage())
                        .contains(
                                String.format(
                                        "Minimum supported Gradle version is %s. Current version is %s.",
                                        SdkConstants.GRADLE_LATEST_VERSION, OLD_GRADLE_VERSION));
            }
        }
    }
}
