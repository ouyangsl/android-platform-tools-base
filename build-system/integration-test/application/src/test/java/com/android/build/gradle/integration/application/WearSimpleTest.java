/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.SdkConstants.DOT_ANDROID_PACKAGE;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FD_RES_RAW;
import static com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.DEBUG;
import static com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.RELEASE;
import static com.android.build.gradle.integration.common.truth.ApkSubject.assertThat;
import static com.android.builder.core.BuilderConstants.ANDROID_WEAR_MICRO_APK;
import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.testutils.apk.Apk;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Assemble tests for embedded wear app with a single app.
 */
public class WearSimpleTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("simpleMicroApp")
                    .create();

    @BeforeClass
    public static void setUp() throws Exception {
        File mainAppBuildGradle = project.file("main/build.gradle");

        TestFileUtils.appendToFile(mainAppBuildGradle,
                "dependencies {\n"
                + "  wearApp project(':wear')\n"
                + "}\n");

    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkDefaultEmbedding() throws Exception {
        project.execute("clean", ":main:assemble");
        boolean optimizedResEnabled =
                project.getSubproject("main")
                        .getIntermediateFile(
                                InternalArtifactType.OPTIMIZED_PROCESSED_RES.INSTANCE
                                        .getFolderName())
                        .exists();
        String embeddedApkPath =
                optimizedResEnabled
                        ? FD_RES + '/' + "q-.apk"
                        : FD_RES
                                + '/'
                                + FD_RES_RAW
                                + '/'
                                + ANDROID_WEAR_MICRO_APK
                                + DOT_ANDROID_PACKAGE;

        // each micro app has a different version name to distinguish them from one another.
        // here we record what we expect from which.
        Map<ApkType, String> variants = new HashMap<>();
        // Apk Type           Version name
        //---------------     ------------
        variants.put(RELEASE, "default");
        variants.put(DEBUG, null);

        for (Map.Entry<ApkType, String> variantData : variants.entrySet()) {
            Apk fullApk = project.getSubproject("main").getApk(variantData.getKey());
            assertThat(fullApk).isNotNull();
            assertThat(fullApk.getFile()).isFile();

            String versionName = variantData.getValue();

            if (versionName == null) {
                assertThat(fullApk).doesNotContain(embeddedApkPath);
            } else {
                Apk embeddedApk = new Apk(fullApk.getEntryAsZip(embeddedApkPath).getFile());
                assertThat(embeddedApk)
                        .named("Embedded apk '" + embeddedApkPath + "' for:" + fullApk.getFile())
                        .isNotNull();

                // check for the versionName
                assertThat(embeddedApk).hasVersionName(versionName);
            }
        }
    }

    @Test
    public void checkWearReleaseNotBuildWithMainDebug() throws Exception {
        GradleBuildResult result = project.executor().run("clean", ":main:assembleDebug");
        assertThat(result.findTask(":wear:packageRelease")).isNull();
    }
}
