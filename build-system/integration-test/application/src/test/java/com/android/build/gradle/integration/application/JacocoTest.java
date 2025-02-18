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

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleProject;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TemporaryProjectModification;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.truth.ScannerSubject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.coverage.JacocoConfigurations;
import com.android.testutils.TestInputsGenerator;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.Dex;
import com.android.testutils.truth.DexClassSubject;
import com.android.testutils.truth.DexSubject;
import com.android.utils.FileUtils;
import com.google.common.base.Charsets;
import com.google.common.truth.Truth8;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Scanner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class JacocoTest {

    private static final String CLASS_NAME = "com/example/B";
    private static final String CLASS_FULL_TYPE = "L" + CLASS_NAME + ";";
    private static final String CLASS_SRC_LOCATION = "src/main/java/" + CLASS_NAME + ".java";

    private static final String CLASS_CONTENT =
            "package com.example;\n"
                    + "public class B { }";

    private static final GradleProject TEST_APP =
            HelloWorldApp.forPlugin("com.android.application");

    @Rule
    public final GradleTestProject project =
            GradleTestProject.builder().fromTestApp(TEST_APP).create();

    @Before
    public void setup() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\nandroid.buildTypes.debug.enableAndroidTestCoverage true\n");
    }

    @Test
    public void addAndRemoveClass() throws Exception {
        project.execute("assembleDebug");
        assertThat(project.getApk("debug")).doesNotContainClass(CLASS_FULL_TYPE);

        TemporaryProjectModification.doTest(
                project,
                modifiedProject -> {
                    modifiedProject.addFile(CLASS_SRC_LOCATION, CLASS_CONTENT);
                    project.execute("assembleDebug");
                    assertThat(project.getApk("debug")).containsClass(CLASS_FULL_TYPE);
                });
        project.execute("assembleDebug");
        assertThat(project.getApk("debug")).doesNotContainClass(CLASS_FULL_TYPE);
    }

    /** Regression test for http://b/65573026. */
    @Test
    public void testDisablingAndEnablingJacoco() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\nandroid.buildTypes.debug.enableAndroidTestCoverage false\n");
        project.executor().run("assembleDebug");

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\nandroid.buildTypes.debug.enableAndroidTestCoverage true\n");
        project.executor().run("assembleDebug");
    }

    @Test
    public void testJarIsProceedByJacoco() throws Exception {
        TestInputsGenerator.jarWithEmptyClasses(
                new File(project.getProjectDir(), "generated-classes.jar").toPath(),
                Collections.singleton("test/A"));

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.applicationVariants.all { variant ->\n"
                        + "    def generated = project.files(\"generated-classes.jar\")\n"
                        + "    variant.registerPostJavacGeneratedBytecode(generated)\n"
                        + "}\n"
                        + "android.buildTypes.debug.enableAndroidTestCoverage = true\n");

        project.executor().run("assembleDebug");

        Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG);
        Truth8.assertThat(apk.getMainDexFile()).isPresent();
        Dex dexClasses = apk.getMainDexFile().get();
        DexSubject.assertThat(dexClasses).containsClass("Ltest/A;");
        DexClassSubject.assertThat(dexClasses.getClasses().get("Ltest/A;")).hasField("$jacocoData");
    }

    /** Regression test for http://b/152872138. */
    @Test
    public void testDisablingBuildFeaturesInAppAndLib() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n\n"
                        + "android {\n"
                        + "  buildFeatures {\n"
                        + "    aidl = false\n"
                        + "    renderScript = false\n"
                        + "  }\n"
                        + "}\n");
        project.executor()
                .withArgument("--dry-run")
                // https://issuetracker.google.com/146163513
                .run("createDebugAndroidTestCoverageReport");

        TestFileUtils.searchAndReplace(
                project.getBuildFile(), "com.android.application", "com.android.library");
        project.executor()
                .withArgument("--dry-run")
                // https://issuetracker.google.com/146163513
                .run("createDebugAndroidTestCoverageReport");
    }

    /** Regression test for b/154069245. */
    @Test
    public void testIncrementalChangesProcessedOnce() throws Exception {
        project.execute("assembleDebug");

        File dataSrc = new File(project.getMainSrcDir(), "test/Data.java");
        FileUtils.mkdirs(dataSrc.getParentFile());
        String dataSrcContent = "package test; public class Data {} class AnotherClass {}";
        java.nio.file.Files.write(dataSrc.toPath(), dataSrcContent.getBytes(Charsets.UTF_8));

        GradleBuildResult result =
                project.executor().withEnableInfoLogging(true).run("assembleDebug");
        int numberOfFilesProcessed = 0;
        try (Scanner scanner = result.getStdout()) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.contains("Instrumenting file: ")) {
                    numberOfFilesProcessed++;
                }
            }
        }
        assertThat(numberOfFilesProcessed).named("number of files with Jacoco").isEqualTo(2);
    }

    @Test
    public void testChangedJars() throws Exception {
        project.executor().run("assembleDebug");

        File strings = new File(project.getProjectDir(), "src/main/res/values/strings.xml");
        FileUtils.mkdirs(strings.getParentFile());
        String dataSrcContent =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources>\n"
                        + "    <string name=\"app_name\">HelloWorld</string>\n"
                        + "    <string name=\"app_name2\">HelloWorld2</string>\n"
                        + "</resources>\n";
        java.nio.file.Files.write(strings.toPath(), dataSrcContent.getBytes(Charsets.UTF_8));

        GradleBuildResult result =
                project.executor().withEnableInfoLogging(true).run("assembleDebug");
        int numberOfJarsProcessed = 0;
        int numberOfFilesProcessed = 0;
        try (Scanner scanner = result.getStdout()) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.contains("Instrumenting jar: ")) {
                    numberOfJarsProcessed++;
                } else if (line.contains("Instrumenting file: ")) {
                    numberOfFilesProcessed++;
                }
            }
        }
        assertThat(numberOfJarsProcessed)
                .named("number of jars processed with Jacoco")
                .isEqualTo(1);
        assertThat(numberOfFilesProcessed)
                .named("number of files processed with Jacoco")
                .isEqualTo(0);
    }

    @Test
    public void androidTestApksDoNotIncludeOfflineInstrumentation() throws Exception {
        project.executor().run("assembleDebug", "assembleDebugAndroidTest");

        try (Apk androidTestApk = project.getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG)) {
            Dex dexClasses = androidTestApk.getMainDexFile().get();
            DexClassSubject.assertThat(
                            dexClasses.getClasses().get("Lcom/example/helloworld/HelloWorldTest;"))
                    .doesNotHaveField("\\$jacoco");
        }
        try (Apk mainApk = project.getApk(GradleTestProject.ApkType.DEBUG)) {
            Dex mainDexClasses = mainApk.getMainDexFile().get();
            DexClassSubject.assertThat(
                    mainDexClasses.getClasses().get("Lcom/example/helloworld/HelloWorld;")
            ).hasField("$jacocoData");
        }

        // Check library androidTest apk test classes do not contain instrumentation.
        TestFileUtils.searchAndReplace(
                project.getBuildFile(), "com.android.application", "com.android.library");
        project.executor().run("clean", "assembleDebugAndroidTest");
        try (Apk libraryAndroidTestApk = project.getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG)) {
            Dex libraryDexClasses = libraryAndroidTestApk.getMainDexFile().get();
            DexClassSubject.assertThat(
                            libraryDexClasses.getClasses().get("Lcom/example/helloworld/HelloWorldTest;"))
                    .doesNotHaveField("$jacocoData");
        }
    }

    @Test
    public void checkJacocoAntConfiguration() throws IOException, InterruptedException {
        // Verify jacoco ant configuration is not set when android test coverage disabled.
        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "android.buildTypes.debug.enableAndroidTestCoverage true",
                "android.buildTypes.debug.enableAndroidTestCoverage false");
        GradleBuildResult result = project.executor().run(":dep");
        try (Scanner scanner = result.getStdout()) {
            ScannerSubject.assertThat(scanner)
                    .doesNotContain(JacocoConfigurations.ANT_CONFIGURATION_NAME);
        }

        // Once enableAndroidTestCoverage is enabled in a variant, we expect the configuration to be
        // present.
        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "android.buildTypes.debug.enableAndroidTestCoverage false",
                "android.buildTypes.debug.enableAndroidTestCoverage true");
        result = project.executor().run(":dep");
        try (Scanner scanner = result.getStdout()) {
            ScannerSubject.assertThat(scanner)
                    .contains(JacocoConfigurations.ANT_CONFIGURATION_NAME);
        }
    }
}
