/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.integration.connected.application;

import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.connected.utils.EmulatorUtils;
import java.io.IOException;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

/** Connected test for kotlinApp */
public class KotlinAppConnectedTest {

    @ClassRule public static final ExternalResource EMULATOR = EmulatorUtils.getEmulator();

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("kotlinApp")
                    .create();

    @Before
    public void setUp() throws IOException, InterruptedException {
        // fail fast if no response
        project.addAdbTimeout();
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        project.executor()
                .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
                .run("uninstallAll");
    }

    @Test
    public void connectedAndroidTest() throws Exception {
        project.executor()
                .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
                .withArgument("-Dorg.gradle.unsafe.configuration-cache.max-problems=10000")
                .run("connectedAndroidTest");

        String testReportPath =
                "build/reports/androidTests/connected/debug/com.example.android.kotlin.html";
        assertThat(project.file("app/" + testReportPath)).exists();
        assertThat(project.file("library/" + testReportPath)).exists();
        assertThat(project.file("libraryNoTests/" + testReportPath)).doesNotExist();
    }
}
