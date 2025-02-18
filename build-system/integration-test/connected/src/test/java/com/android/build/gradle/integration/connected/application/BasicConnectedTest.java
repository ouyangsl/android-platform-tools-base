/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.connected.utils.EmulatorUtils;
import com.android.build.gradle.options.BooleanOption;
import java.io.IOException;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

public class BasicConnectedTest {

    @ClassRule public static final ExternalResource emulator = EmulatorUtils.getEmulator();

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("basic").create();

    @Before
    public void setUp() throws IOException {
        // fail fast if no response
        project.addAdbTimeout();
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        project.execute("uninstallAll");
    }

    @Test
    public void install() throws Exception {
        TestFileUtils.appendToFile(
                project.getGradlePropertiesFile(),
                BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT.getPropertyName() + "=false");
        project.execute("installDebug", "uninstallAll");
        // b/37498215 - Try again.  Behavior may be different when tasks are up-to-date.
        project.execute("installDebug", "uninstallAll");
    }

    @Test // Regression test for b/304312888
    public void installWithPrivacySandboxEnabled() throws IOException {
        TestFileUtils.appendToFile(
                project.getGradlePropertiesFile(),
                BooleanOption.PRIVACY_SANDBOX_SDK_SUPPORT.getPropertyName() + "=true");
        project.execute("installDebug", "uninstallAll");
    }

    @Test
    public void connectedCheck() throws Exception {
        project.executor().run("connectedCheck");
    }
}
