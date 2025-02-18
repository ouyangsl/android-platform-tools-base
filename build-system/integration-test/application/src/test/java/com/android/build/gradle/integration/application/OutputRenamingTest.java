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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.builder.core.BuilderConstants.DEBUG;
import static com.android.builder.core.BuilderConstants.RELEASE;
import static com.android.testutils.truth.PathSubject.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.build.api.variant.BuiltArtifact;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtilsV2;
import com.android.build.gradle.integration.common.utils.ProjectBuildOutputUtilsV2;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.v2.ide.SyncIssue;
import com.android.builder.model.v2.ide.Variant;
import com.android.builder.model.v2.models.AndroidProject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/** Assemble tests for class densitySplitInL */
public class OutputRenamingTest {

    private static AndroidProject model;

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("densitySplit").create();

    @BeforeClass
    public static void setup() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "applicationVariants.all { variant ->\n"
                        + "    // Custom APK names (do not do this for 'dev' build type)\n"
                        + "    println variant.buildType.name\n"
                        + "    variant.outputs.all { output -> \n"
                        + "      def baseFileName = \"project-${variant.flavorName}-${output.versionCode}-${variant.buildType.name}\"\n"
                        + "      output.outputFileName = \"${baseFileName}-${output.getFilter(com.android.build.VariantOutput.FilterType.DENSITY)}-signed.apk\"\n"
                        + "    }\n"
                        + "  }\n"
                        + "}");
        project.executor().run("clean", "assemble");
        model =
                project.modelV2()
                        .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
                        .fetchModels()
                        .getContainer()
                        .getProject(null, ":")
                        .getAndroidProject();
        Collection<SyncIssue> syncIssues =
                project.modelV2()
                        .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
                        .fetchModels()
                        .getContainer()
                        .getProject(null, ":")
                        .getIssues()
                        .getSyncIssues();
        assertThat(syncIssues).hasSize(1);
        assertThat(syncIssues.iterator().next().getMessage())
                .contains("Density-based apk split feature is deprecated");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        model = null;
    }

    @Test
    public void checkSplitOutputs() {
        Collection<Variant> variants = model.getVariants();
        assertEquals("Variant Count", 2, variants.size());

        assertFileRenaming(DEBUG);
        assertFileRenaming(RELEASE);
    }

    private static void assertFileRenaming(String buildType) {
        Collection<Variant> variantBuildOutputs =
                model.getVariants();
        assertThat(variantBuildOutputs).hasSize(2);
        Variant buildOutput =
                AndroidProjectUtilsV2.getVariantByName(model, buildType);

        // get the outputs.
        Collection<String> outputs = ProjectBuildOutputUtilsV2.getApkFolderOutput(buildOutput);
        assertNotNull(outputs);
        assertThat(outputs).hasSize(5);

        List<String> expectedFileNames =
                Arrays.asList(
                        "project--112-" + buildType + "-null-signed.apk",
                        "project--212-" + buildType + "-mdpi-signed.apk",
                        "project--312-" + buildType + "-hdpi-signed.apk",
                        "project--412-" + buildType + "-xhdpi-signed.apk",
                        "project--512-" + buildType + "-xxhdpi-signed.apk");

        List<String> actualFileNames = new ArrayList<>();
        File assembleTaskOutputListingFile =
                buildOutput.getMainArtifact().getAssembleTaskOutputListingFile();
        for (BuiltArtifact builtArtifact :
                ProjectBuildOutputUtilsV2.getBuiltArtifacts(assembleTaskOutputListingFile)
                        .getElements()) {
            File outputFile = new File(builtArtifact.getOutputFile());
            actualFileNames.add(outputFile.getName());
            assertThat(outputFile).exists();
        }

        assertThat(actualFileNames).containsExactlyElementsIn(expectedFileNames);
    }
}
