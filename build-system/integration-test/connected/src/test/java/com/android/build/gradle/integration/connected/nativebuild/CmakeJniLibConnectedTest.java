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

package com.android.build.gradle.integration.connected.nativebuild;

import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.connected.utils.EmulatorUtils;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

@Ignore
public class CmakeJniLibConnectedTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("ndkJniLib")
                    .addFile(HelloWorldJniApp.cmakeLists("lib"))
                    .create();

    @ClassRule public static final ExternalResource EMULATOR = EmulatorUtils.getEmulator();

    @Before
    public void setUp() throws IOException, InterruptedException {
        new File(project.getProjectDir(), "src/main/jni")
                .renameTo(new File(project.getProjectDir(), "src/main/cxx"));
        GradleTestProject lib = project.getSubproject("lib");
        TestFileUtils.appendToFile(
                lib.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.library'\n"
                        + "android {\n"
                        + "    compileSdkVersion libs.versions.latestCompileSdk.get().toInteger()\n"
                        + "    buildToolsVersion = libs.versions.buildToolsVersion.get()\n"
                        + "}\n");

        // Convert externalNativeBuild { ndkbuild { path "Android.mk" } } to
        // externalNativeBuild { cmake { path "CMakeList.txt" } }
        TestFileUtils.searchAndReplace(lib.getBuildFile(), "ndkBuild", "cmake");
        TestFileUtils.searchAndReplace(lib.getBuildFile(), "Android.mk", "CMakeLists.txt");
        project.execute(
                "clean", "assembleDebug", "generateJsonModelDebug", "generateJsonModelRelease");
        assertThat(project.getSubproject("lib").file("build/intermediates/cmake")).exists();

        // fail fast if no response
        project.addAdbTimeout();
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        project.execute("uninstallAll");
    }

    @Test
    public void connectedCheck() throws IOException, InterruptedException {
        project.executor().run("connectedCheck");
    }
}
