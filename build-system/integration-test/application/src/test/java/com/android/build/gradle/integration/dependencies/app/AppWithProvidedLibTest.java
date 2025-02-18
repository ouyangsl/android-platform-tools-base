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

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.builder;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** test for compileOnly library in app */
public class AppWithProvidedLibTest {

    @ClassRule
    public static GradleTestProject project =
            builder()
                    .fromTestProject("dynamicApp")
                    .create();

    @BeforeClass
    public static void setUp() throws Exception {
        // create a library module.
        File rootFolder = project.getSettingsFile().getParentFile();
        File libFolder = new File(rootFolder, "library");
        FileUtils.mkdirs(libFolder);

        Files.asCharSink(new File(libFolder, "build.gradle"), Charsets.UTF_8)
                .write(
                        "apply plugin: 'com.android.library'\n"
                                + "\n"
                                + "android {\n"
                                + "    namespace 'com.example.android.multiproject.library.base'\n"
                                + "    compileSdkVersion libs.versions.latestCompileSdk.get().toInteger()\n"
                                + "\n"
                                + "}\n");
        final File mainFolder = new File(libFolder, "src/main");
        FileUtils.mkdirs(mainFolder);
        Files.asCharSink(new File(mainFolder, "AndroidManifest.xml"), Charsets.UTF_8)
                .write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" + "<manifest />\n");

        TestFileUtils.appendToFile(project.getSettingsFile(), "\ninclude 'library'");
        TestFileUtils.appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n" + "dependencies {\n" + "    compileOnly project(\":library\")\n" + "}\n");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkBuildFailure() throws IOException, InterruptedException {
        GradleBuildResult result = project.executor().expectFailure().run("app:assemble");

        assertThat(result.getFailureMessage())
                .isEqualTo(
                        "The following Android dependencies are set to compileOnly which is not supported:\n"
                                + "-> :library");
    }
}
