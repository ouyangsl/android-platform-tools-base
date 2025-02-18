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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;
import static com.android.testutils.truth.ZipFileSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.truth.ScannerSubject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.OptionalBooleanOption;
import com.android.testutils.apk.Zip;

import com.google.common.truth.Truth;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Scanner;

/**
 * Integration test for extracting annotations.
 *
 * <p>Tip: To execute just this test after modifying the annotations extraction code:
 *
 * <pre>
 *     $ cd tools
 *     $ ./gradlew :base:build-system:integration-test:application:test -D:base:build-system:integration-test:application:test.single=ExtractAnnotationTest
 * </pre>
 */
@RunWith(FilterableParameterized.class)
public class ExtractAnnotationsTest {

    @Parameterized.Parameters(name = "useK2Uast = {0}")
    public static Object[] getParameters() {
        return new Object[] {true, false};
    }

    @Parameterized.Parameter(0)
    public boolean useK2Uast;

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("extractAnnotations")
                    .create();

    @Test
    public void checkExtractAnnotation() throws Exception {
        GradleBuildResult result = getExecutor().run("clean", "assembleDebug");

        try (Scanner stderr = result.getStderr()) {
            ScannerSubject.assertThat(stderr).doesNotContain("Unknown flag");
        }

        project.getAar(
                "debug",
                debugAar -> {
                    //noinspection SpellCheckingInspection
                    String expectedContent =
                            ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                                 + "<root>\n"
                                 + "  <item name=\"com.android.tests.extractannotations.ExtractTest"
                                 + " int getVisibility()\">\n"
                                 + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                                 + "      <val name=\"value\""
                                 + " val=\"{com.android.tests.extractannotations.ExtractTest.VISIBLE,"
                                 + " com.android.tests.extractannotations.ExtractTest.INVISIBLE,"
                                 + " com.android.tests.extractannotations.ExtractTest.GONE, 5, 17,"
                                 + " com.android.tests.extractannotations.Constants.CONSTANT_1}\""
                                 + " />\n"
                                 + "    </annotation>\n"
                                 + "  </item>\n"
                                 + "  <item name=\"com.android.tests.extractannotations.ExtractTest"
                                 + " java.lang.String getStringMode(int)\">\n"
                                 + "    <annotation"
                                 + " name=\"android.support.annotation.StringDef\">\n"
                                 + "      <val name=\"value\""
                                 + " val=\"{com.android.tests.extractannotations.ExtractTest.STRING_1,"
                                 + " com.android.tests.extractannotations.ExtractTest.STRING_2,"
                                 + " &quot;literalValue&quot;, &quot;concatenated&quot;}\" />\n"
                                 + "    </annotation>\n"
                                 + "  </item>\n"
                                 + "  <item name=\"com.android.tests.extractannotations.ExtractTest"
                                 + " java.lang.String getStringMode(int) 0\">\n"
                                 + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                                 + "      <val name=\"value\""
                                 + " val=\"{com.android.tests.extractannotations.ExtractTest.VISIBLE,"
                                 + " com.android.tests.extractannotations.ExtractTest.INVISIBLE,"
                                 + " com.android.tests.extractannotations.ExtractTest.GONE, 5, 17,"
                                 + " com.android.tests.extractannotations.Constants.CONSTANT_1}\""
                                 + " />\n"
                                 + "    </annotation>\n"
                                 + "  </item>\n"
                                 + "  <item name=\"com.android.tests.extractannotations.ExtractTest"
                                 + " void checkForeignTypeDef(int) 0\">\n"
                                 + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                                 + "      <val name=\"flag\" val=\"true\" />\n"
                                 + "      <val name=\"value\""
                                 + " val=\"{com.android.tests.extractannotations.Constants.CONSTANT_1,"
                                 + " com.android.tests.extractannotations.Constants.CONSTANT_2}\""
                                 + " />\n"
                                 + "    </annotation>\n"
                                 + "  </item>\n"
                                 + "  <item name=\"com.android.tests.extractannotations.ExtractTest"
                                 + " void testMask(int) 0\">\n"
                                 + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                                 + "      <val name=\"flag\" val=\"true\" />\n"
                                 + "      <val name=\"value\" val=\"{0,"
                                 + " com.android.tests.extractannotations.Constants.FLAG_VALUE_1,"
                                 + " com.android.tests.extractannotations.Constants.FLAG_VALUE_2}\""
                                 + " />\n"
                                 + "    </annotation>\n"
                                 + "  </item>\n"
                                 + "  <item name=\"com.android.tests.extractannotations.ExtractTest"
                                 + " void testNonMask(int) 0\">\n"
                                 + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                                 + "      <val name=\"flag\" val=\"false\" />\n"
                                 + "      <val name=\"value\" val=\"{0,"
                                 + " com.android.tests.extractannotations.Constants.CONSTANT_1,"
                                 + " com.android.tests.extractannotations.Constants.CONSTANT_3}\""
                                 + " />\n"
                                 + "    </annotation>\n"
                                 + "  </item>\n"
                                 + "  <item"
                                 + " name=\"com.android.tests.extractannotations.ExtractTest.StringMode\">\n"
                                 + "    <annotation"
                                 + " name=\"android.support.annotation.StringDef\">\n"
                                 + "      <val name=\"value\""
                                 + " val=\"{com.android.tests.extractannotations.ExtractTest.STRING_1,"
                                 + " com.android.tests.extractannotations.ExtractTest.STRING_2,"
                                 + " &quot;literalValue&quot;, &quot;concatenated&quot;}\" />\n"
                                 + "    </annotation>\n"
                                 + "  </item>\n"
                                 + "  <item"
                                 + " name=\"com.android.tests.extractannotations.ExtractTest.Visibility\">\n"
                                 + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                                 + "      <val name=\"value\""
                                 + " val=\"{com.android.tests.extractannotations.ExtractTest.VISIBLE,"
                                 + " com.android.tests.extractannotations.ExtractTest.INVISIBLE,"
                                 + " com.android.tests.extractannotations.ExtractTest.GONE, 5, 17,"
                                 + " com.android.tests.extractannotations.Constants.CONSTANT_1}\""
                                 + " />\n"
                                 + "    </annotation>\n"
                                 + "  </item>\n"
                                 + "  <item"
                                 + " name=\"com.android.tests.extractannotations.TopLevelTypeDef\">\n"
                                 + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                                 + "      <val name=\"flag\" val=\"true\" />\n"
                                 + "      <val name=\"value\""
                                 + " val=\"{com.android.tests.extractannotations.Constants.CONSTANT_1,"
                                 + " com.android.tests.extractannotations.Constants.CONSTANT_2}\""
                                 + " />\n"
                                 + "    </annotation>\n"
                                 + "  </item>\n"
                                 + "</root>\n");

                    // check the resulting .aar file to ensure annotations.zip inclusion.
                    assertThat(debugAar).contains("annotations.zip");

                    // Check typedefs removals:

                    // public typedef: should be present
                    assertThat(debugAar)
                            .containsClass(
                                    "Lcom/android/tests/extractannotations/ExtractTest$Visibility;");

                    // private/protected typedefs: should have been removed
                    assertThat(debugAar)
                            .doesNotContainClass(
                                    "Lcom/android/tests/extractannotations/ExtractTest$Mask;");
                    assertThat(debugAar)
                            .doesNotContainClass(
                                    "Lcom/android/tests/extractannotations/ExtractTest$NonMaskType;");

                    assertThat(debugAar)
                            .containsClass(
                                    "Lcom/android/tests/extractannotations/ExtractTest$StringMode;");

                    try {
                        assertThat(
                                Objects.requireNonNull(debugAar.getEntryAsFile("annotations.zip")),
                                it -> {
                                    it.containsFileWithContent(
                                            "com/android/tests/extractannotations/annotations.xml",
                                            expectedContent);
                                });
                        // Make sure the NonMask symbol (from a private typedef) is completely gone
                        // from
                        // the
                        // outer class
                        assertThat(
                                Objects.requireNonNull(debugAar.getEntryAsFile("classes.jar")),
                                it -> {
                                    it.containsFileWithoutContent(
                                            "com/android/tests/extractannotations/ExtractTest.class",
                                            "NonMaskType");
                                });
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        Truth.assertThat(getExecutor().run("assembleDebug").getDidWorkTasks()).isEmpty();

        // Make sure that annotations.zip contains no timestamps (for making it binary identical on
        // every build)
        project.getAar(
                "debug",
                debugAar -> {
                    try {
                        Zip annotationZip = debugAar.getEntryAsZip("annotations.zip");

                        Path annotationXml =
                                annotationZip.getEntry(
                                        "com/android/tests/extractannotations/annotations.xml");
                        assertThat(annotationXml).isNotNull();
                        //noinspection ConstantConditions
                        assertThat(
                                        Files.readAttributes(
                                                        annotationXml, BasicFileAttributes.class)
                                                .lastModifiedTime()
                                                .toMillis())
                                .isEqualTo(0L);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }

    /** Regression test for Issue 234865137 */
    @Test
    public void checkNonExistentGeneratedBytecodeDirectory() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n\n"
                        + "android.libraryVariants.all { variant ->\n"
                        + "    variant.registerPreJavacGeneratedBytecode(\n"
                        + "        project.files('/does/not/exist')\n"
                        + "    )\n"
                        + "}\n");
        getExecutor().run("clean", "assembleDebug");
    }

    /** Regression test for b/228751486 */
    @Test
    public void checkContainsTypeDefs() throws Exception {
        File srcFileDir =
                new File(
                        project.getProjectDir(),
                        "src/main/java/com/android/tests/extractannotations");
        File intDefFile = new File(srcFileDir, "TopLevelTypeDef.java");
        File extractTestFile = new File(srcFileDir, "ExtractTest.java");
        TestFileUtils.searchAndReplace(
                intDefFile, "android.support.annotation.IntDef", "android.support.annotation.*");
        assertThat(extractTestFile.delete()).isTrue();

        getExecutor().run("clean", "assembleDebug");
        project.getAar(
                "debug",
                debugAar -> {
                    Zip annotationZip = debugAar.getEntryAsZip("annotations.zip");
                    assertThat(annotationZip).isNotNull();
                });
    }

    private GradleTaskExecutor getExecutor() {
        return project.executor().with(OptionalBooleanOption.LINT_USE_K2_UAST, useK2Uast);
    }
}
