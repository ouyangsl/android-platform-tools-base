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

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.ApkSubject;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtilsV2;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtilsV2;
import com.android.builder.model.v2.ide.Variant;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/** Test for unresolved placeholders in libraries. */
public class PlaceholderInLibsTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("placeholderInLibsTest").create();

    @Test
    public void testLibraryPlaceholderSubstitutionInFinalApk()
            throws IOException, InterruptedException {
        project.executor()
                .run(
                "clean",
                ":examplelibrary:generateDebugAndroidTestSources",
                "app:assembleDebug"
        );
        com.android.builder.model.v2.models.AndroidProject androidProject = project.modelV2()
                .fetchModels()
                .getContainer()
                .getProject(":app")
                .getAndroidProject();

        Collection<Variant> variants = androidProject.getVariants();
        assertThat(variants).named("Variant Count").hasSize(2);

        // get the main artifact of the debug artifact
        Variant debug = AndroidProjectUtilsV2.getVariantByName(
                androidProject, "flavorDebug");

        // get the outputs.
        List<String> debugOutputs = ProjectBuildOutputUtilsV2.getApkFolderOutput(debug);
        assertNotNull(debugOutputs);

        assertEquals(1, debugOutputs.size());
        String output = debugOutputs.iterator().next();

        List<String> apkBadging = ApkSubject.getBadging(new File(output));

        for (String line : apkBadging) {
            if (line.contains("uses-permission: name=" +
                    "'com.example.manifest_merger_example.flavor.permission.C2D_MESSAGE'")) {
                return;
            }
        }
        Assert.fail("failed to find the permission with the right placeholder substitution.");
    }

    // Regression test for b/316057932
    @Test
    public void testVerifyResourcesWithLibraryManifestPlaceholders()
            throws IOException, InterruptedException {
        project.executor().run("examplelibrary:verifyReleaseResources");
    }
}
