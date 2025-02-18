/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.application;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkLocation;
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType;
import com.android.build.gradle.integration.common.fixture.ModelContainerV2;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.ProjectSyncIssuesUtilsV2Kt;
import com.android.build.gradle.options.StringOption;
import com.android.builder.model.v2.ide.SyncIssue;
import com.android.builder.model.v2.models.ProjectSyncIssues;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Collection;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

/** Test injected ABI reduces the number of splits being built. */
public class InjectedAbiSplitTest {

    @ClassRule
    public static GradleTestProject sProject = GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
            .create();

    @BeforeClass
    public static void setUp() throws Exception {
        FileUtils.createFile(sProject.file("src/main/jniLibs/x86/libprebuilt.so"), "");
        FileUtils.createFile(sProject.file("src/main/jniLibs/armeabi-v7a/libprebuilt.so"), "");

        Files.append(
                "android {\n"
                        + "    splits {\n"
                        + "        abi {\n"
                        + "            enable true\n"
                        + "            reset()\n"
                        + "            include 'x86', 'armeabi-v7a'\n"
                        + "            universalApk false\n"
                        + "        }\n"
                        + "        density {\n"
                        + "            enable true\n"
                        + "            reset()\n"
                        + "            include \"ldpi\", \"hdpi\"\n"
                        + "        }\n"
                        + "    }\n"
                        + "}",
                sProject.getBuildFile(),
                Charsets.UTF_8);
    }

    @Test
    public void checkAbi() throws Exception {
        sProject.executor()
                .with(StringOption.IDE_BUILD_TARGET_ABI, "armeabi-v7a")
                .run("clean", "assembleDebug");

        assertThat(getApk("armeabi-v7a")).exists();
        assertThat(getApk("armeabi-v7a")).contains("lib/armeabi-v7a/libprebuilt.so");
        assertThat(getApk("ldpiArmeabi-v7a")).doesNotExist();
        assertThat(getApk("hdpiArmeabi-v7a")).doesNotExist();
        assertThat(getApk("x86")).doesNotExist();
        assertThat(getApk("ldpiX86")).doesNotExist();
        assertThat(getApk("hdpiX86")).doesNotExist();
    }

    @Test
    public void checkError() {
        ModelContainerV2 container =
                sProject.modelV2()
                        .with(StringOption.IDE_BUILD_TARGET_ABI, "mips")
                        .ignoreSyncIssues()
                        .fetchModels().getContainer();

        ProjectSyncIssues issues = container.getProject().getIssues();
        Collection<SyncIssue> genericSyncWarnings =
                ProjectSyncIssuesUtilsV2Kt.filter(
                        issues, SyncIssue.SEVERITY_WARNING, SyncIssue.TYPE_GENERIC
                );
        assertThat(genericSyncWarnings).isNotEmpty();
    }

    private static Apk getApk(String filterName) {
        return sProject.getApk(filterName, ApkType.DEBUG, ApkLocation.Intermediates);
    }
}
