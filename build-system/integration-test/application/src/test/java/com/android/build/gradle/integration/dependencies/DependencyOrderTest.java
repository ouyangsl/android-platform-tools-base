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

package com.android.build.gradle.integration.dependencies;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import java.io.File;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * test for dependency order
 */
public class DependencyOrderTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();

    @BeforeClass
    public static void setUp() throws Exception {
        project.setIncludedProjects("app", "library", "library2");

        // add the library2 to make sure that whether it's before or after library, it'll still be
        // ordered after (since library depends on library2.)
        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n"
                        + "dependencies {\n"
                        + "    api project(\":library2\")\n"
                        + "    api project(\":library\")\n"
                        + "    api project(\":library2\")\n"
                        + "}\n");

        final GradleTestProject libraryProject = project.getSubproject("library");
        appendToFile(
                libraryProject.getBuildFile(),
                "\n" + "dependencies {\n" + "    api project(\":library2\")\n" + "}\n");

        File mainDir = libraryProject.getMainSrcDir().getParentFile();
        File assetFile = FileUtils.join(mainDir, "assets", "foo.txt");
        FileUtils.mkdirs(assetFile.getParentFile());
        appendToFile(assetFile, "library");

        final GradleTestProject library2Project = project.getSubproject("library2");
        mainDir = library2Project.getMainSrcDir().getParentFile();
        assetFile = FileUtils.join(mainDir, "assets", "foo.txt");
        FileUtils.mkdirs(assetFile.getParentFile());
        appendToFile(assetFile, "library2");
        appendToFile(FileUtils.join(mainDir, "assets", "foo2.txt"), "library2");

        project.execute("clean", ":app:assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkCompiledLibraryIsPackaged() throws Exception {
        Apk apk = project.getSubproject("app").getApk("debug");
        assertThat(apk).containsFileWithContent("assets/foo.txt", "library");
        assertThat(apk).containsFileWithContent("assets/foo2.txt", "library2");
    }
}
