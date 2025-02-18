/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tools.lint;

import static com.android.tools.lint.LintCliFlags.ERRNO_CREATED_BASELINE;
import static com.android.tools.lint.LintCliFlags.ERRNO_ERRORS;
import static com.android.tools.lint.LintCliFlags.ERRNO_EXISTS;
import static com.android.tools.lint.LintCliFlags.ERRNO_INVALID_ARGS;
import static com.android.tools.lint.LintCliFlags.ERRNO_SUCCESS;
import static com.android.tools.lint.checks.infrastructure.LintTestUtils.dos2unix;

import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.Version;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.IAndroidTarget;
import com.android.testutils.TestUtils;
import com.android.tools.lint.checks.AbstractCheckTest;
import com.android.tools.lint.checks.AccessibilityDetector;
import com.android.tools.lint.checks.DesugaredMethodLookup;
import com.android.tools.lint.checks.infrastructure.TestFile;
import com.android.tools.lint.client.api.ConfigurationHierarchy;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintListener;
import com.android.tools.lint.client.api.PlatformLookup;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Lint;

import kotlin.io.FilesKt;
import kotlin.text.Charsets;
import kotlin.text.StringsKt;

import org.intellij.lang.annotations.Language;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.stream.Collectors;

@SuppressWarnings("javadoc")
public class MainTest extends AbstractCheckTest {
    public interface Cleanup {
        String cleanup(String s);
    }

    /**
     * Checks the output using the given custom checker, which should throw an exception if the
     * result is not as expected.
     */
    public interface Check {
        void check(String s);
    }

    @Override
    public String cleanup(String result) {
        return super.cleanup(result);
    }

    private void checkDriver(
            String expectedOutput, String expectedError, int expectedExitCode, String[] args) {
        checkDriver(
                expectedOutput,
                expectedError,
                expectedExitCode,
                args,
                MainTest.this::cleanup,
                null);
    }

    public static void checkDriver(
            @Nullable String expectedOutput,
            @Nullable String expectedError,
            int expectedExitCode,
            @NonNull String[] args,
            @Nullable Cleanup cleanup,
            @Nullable LintListener listener) {
        checkDriver(
                expectedOutput,
                expectedError,
                expectedExitCode,
                args,
                cleanup,
                listener,
                null,
                true);
    }

    public static void checkDriver(
            @Nullable String expectedOutput,
            @Nullable String expectedError,
            int expectedExitCode,
            @NonNull String[] args,
            @Nullable Cleanup cleanup,
            @Nullable LintListener listener,
            @Nullable Check check,
            boolean expectedExactMatch) {

        PrintStream previousOut = System.out;
        PrintStream previousErr = System.err;

        String internalProperty = "idea.is.internal";
        String wasInternal = System.getProperty(internalProperty);
        try {
            System.setProperty(internalProperty, SdkConstants.VALUE_TRUE);
            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            System.setOut(new PrintStream(output));
            final ByteArrayOutputStream error = new ByteArrayOutputStream();
            System.setErr(new PrintStream(error));

            Main main =
                    new Main() {
                        @Override
                        protected void initializeDriver(@NonNull LintDriver driver) {
                            super.initializeDriver(driver);
                            if (listener != null) {
                                driver.addLintListener(listener);
                            }
                        }
                    };

            setConfigurationRoot(args);

            int exitCode = main.run(args);

            String stdout = output.toString(Charsets.UTF_8);

            if (check != null) {
                check.check(stdout);
            }

            String stderr = error.toString(Charsets.UTF_8);
            if (cleanup != null) {
                stderr = cleanup.cleanup(stderr);
            }
            if (expectedOutput != null) {
                expectedOutput = StringsKt.trimIndent(expectedOutput);
                stdout = StringsKt.trimIndent(stdout);
                if (cleanup != null) {
                    expectedOutput = cleanup.cleanup(expectedOutput);
                    stdout = cleanup.cleanup(stdout);
                }
                if (!dos2unix(expectedOutput.trim()).equals(dos2unix(stdout.trim()))) {
                    assertEquals(expectedOutput.trim(), stdout.trim());
                }
            }
            if (expectedError != null && !expectedError.trim().equals(stderr.trim())) {
                // TODO: https://youtrack.jetbrains.com/issue/KT-57715
                //  Until then, we can't assert explicit "equals" yet.
                if (!expectedExactMatch
                        || Arrays.stream(args).anyMatch((arg) -> arg == "--XuseK2Uast")) {
                    assertThat(stderr).contains(expectedError);
                } else {
                    // instead of fail: get difference in output
                    assertEquals(expectedError, stderr);
                }
            }
            assertEquals("Unexpected exit code", expectedExitCode, exitCode);
        } finally {
            System.setOut(previousOut);
            System.setErr(previousErr);
            if (wasInternal != null) {
                System.setProperty(internalProperty, wasInternal);
            } else {
                System.clearProperty(internalProperty);
            }
        }
    }

    /**
     * Process the arguments and see if we can find where the project or project descriptor file is
     * and set the configuration hierarchy root to that folder such that we don't pick up any
     * lint.xml files in the environment outside the test
     */
    private static void setConfigurationRoot(String[] args) {
        String prevArg = "";
        File root = null;
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                File file = new File(arg);
                if (prevArg.equals("--project") || prevArg.equals("--config")) {
                    File parentFile = file.getParentFile();
                    if (parentFile != null && parentFile.isDirectory()) {
                        if (root == null) {
                            root = parentFile;
                        } else {
                            root = Lint.getCommonParent(root, parentFile);
                        }
                    }
                }
            }
            prevArg = arg;
        }
        if (root == null
                && args.length > 0
                && Arrays.stream(args).anyMatch(s -> new File(s).exists())) {
            String last = args[args.length - 1];
            File file = new File(last);
            if (file.isDirectory()) {
                root = file.getParentFile();
            }
        }
        if (root != null) {
            ConfigurationHierarchy.Companion.setDefaultRootDir(root);
        }
    }

    public void testArguments() throws Exception {
        checkDriver(
                // Expected output
                "\n"
                        + "res/layout/accessibility.xml:4: Error: Missing contentDescription attribute on image [ContentDescription]\n"
                        + "    <ImageView android:id=\"@+id/android_logo\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                        + "     ~~~~~~~~~\n"
                        + "res/layout/accessibility.xml:5: Error: Missing contentDescription attribute on image [ContentDescription]\n"
                        + "    <ImageButton android:importantForAccessibility=\"yes\" android:id=\"@+id/android_logo2\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                        + "     ~~~~~~~~~~~\n"
                        + "2 errors, 0 warnings\n",

                // Expected error
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {
                    "--check",
                    "ContentDescription",
                    "--error",
                    "ContentDescription",
                    "--disable",
                    "LintError",
                    getProjectDir(null, mAccessibility).getPath()
                });
    }

    public void testShowDescription() {
        checkDriver(
                // Expected output
                "NewApi\n"
                        + "------\n"
                        + "Summary: Calling new methods on older versions\n"
                        + "\n"
                        + "Priority: 6 / 10\n"
                        + "Severity: Error\n"
                        + "Category: Correctness\n"
                        + "Vendor: Android Open Source Project\n"
                        + "Contact: https://groups.google.com/g/lint-dev\n"
                        + "Feedback: https://issuetracker.google.com/issues/new?component=192708\n"
                        + "\n"
                        + "This check scans through all the Android API calls in the application and\n"
                        + "warns about any calls that are not available on all versions targeted by this\n"
                        + "application (according to its minimum SDK attribute in the manifest).\n"
                        + "\n"
                        + "If you really want to use this API and don't need to support older devices\n"
                        + "just set the minSdkVersion in your build.gradle or AndroidManifest.xml files.\n"
                        + "\n"
                        + "If your code is deliberately accessing newer APIs, and you have ensured (e.g.\n"
                        + "with conditional execution) that this code will only ever be called on a\n"
                        + "supported platform, then you can annotate your class or method with the\n"
                        + "@TargetApi annotation specifying the local minimum SDK to apply, such as\n"
                        + "@TargetApi(11), such that this check considers 11 rather than your manifest\n"
                        + "file's minimum SDK as the required API level.\n"
                        + "\n"
                        + "If you are deliberately setting android: attributes in style definitions, make\n"
                        + "sure you place this in a values-vNN folder in order to avoid running into\n"
                        + "runtime conflicts on certain devices where manufacturers have added custom\n"
                        + "attributes whose ids conflict with the new ones on later platforms.\n"
                        + "\n"
                        + "Similarly, you can use tools:targetApi=\"11\" in an XML file to indicate that\n"
                        + "the element will only be inflated in an adequate context.\n"
                        + "\n"
                        + "\n",

                // Expected error
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {"--show", "NewApi"});
    }

    public void testShowDescriptionWithUrl() {
        checkDriver(
                ""
                        // Expected output
                        + "SdCardPath\n"
                        + "----------\n"
                        + "Summary: Hardcoded reference to /sdcard\n"
                        + "\n"
                        + "Priority: 6 / 10\n"
                        + "Severity: Warning\n"
                        + "Category: Correctness\n"
                        + "Vendor: Android Open Source Project\n"
                        + "Contact: https://groups.google.com/g/lint-dev\n"
                        + "Feedback: https://issuetracker.google.com/issues/new?component=192708\n"
                        + "\n"
                        + "Your code should not reference the /sdcard path directly; instead use\n"
                        + "Environment.getExternalStorageDirectory().getPath().\n"
                        + "\n"
                        + "Similarly, do not reference the /data/data/ path directly; it can vary in\n"
                        + "multi-user scenarios. Instead, use Context.getFilesDir().getPath().\n"
                        + "\n"
                        + "More information: \n"
                        + "https://developer.android.com/training/data-storage#filesExternal\n"
                        + "\n",

                // Expected error
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {"--show", "SdCardPath"});
    }

    public void testNonexistentLibrary() {
        File fooJar = new File(getTempDir(), "foo.jar");
        checkDriver(
                "",
                "Library /TESTROOT/foo.jar does not exist.\n",

                // Expected exit code
                ERRNO_INVALID_ARGS,

                // Args
                new String[] {"--libraries", fooJar.getPath(), "prj"});
    }

    public void testMultipleProjects() throws Exception {
        File project = getProjectDir(null, jar("libs/classes.jar"));

        checkDriver(
                "",
                "The --sources, --classpath, --libraries and --resources arguments can only be used with a single project\n",

                // Expected exit code
                ERRNO_INVALID_ARGS,

                // Args
                new String[] {
                    "--libraries",
                    new File(project, "libs/classes.jar").getPath(),
                    "--disable",
                    "LintError",
                    project.getPath(),
                    project.getPath()
                });
    }

    public void testCustomResourceDirs() throws Exception {
        File project = getProjectDir(null, mAccessibility2, mAccessibility3);

        checkDriver(
                ""
                        + "myres1/layout/accessibility1.xml:4: Warning: Missing contentDescription attribute on image [ContentDescription]\n"
                        + "    <ImageView android:id=\"@+id/android_logo\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                        + "     ~~~~~~~~~\n"
                        + "myres2/layout/accessibility1.xml:4: Warning: Missing contentDescription attribute on image [ContentDescription]\n"
                        + "    <ImageView android:id=\"@+id/android_logo\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                        + "     ~~~~~~~~~\n"
                        + "myres1/layout/accessibility1.xml:5: Warning: Missing contentDescription attribute on image [ContentDescription]\n"
                        + "    <ImageButton android:importantForAccessibility=\"yes\" android:id=\"@+id/android_logo2\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                        + "     ~~~~~~~~~~~\n"
                        + "myres2/layout/accessibility1.xml:5: Warning: Missing contentDescription attribute on image [ContentDescription]\n"
                        + "    <ImageButton android:importantForAccessibility=\"yes\" android:id=\"@+id/android_logo2\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                        + "     ~~~~~~~~~~~\n"
                        + "0 errors, 4 warnings\n", // Expected output
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {
                    "--check",
                    "ContentDescription",
                    "--disable",
                    "LintError",
                    "--resources",
                    new File(project, "myres1").getPath(),
                    "--resources",
                    new File(project, "myres2").getPath(),
                    "--compile-sdk-version",
                    "15",
                    "--java-language-level",
                    "11",
                    project.getPath(),
                });
    }

    public void testPathList() throws Exception {
        File project = getProjectDir(null, mAccessibility2, mAccessibility3);

        checkDriver(
                ""
                        + "myres1/layout/accessibility1.xml:4: Warning: Missing contentDescription attribute on image [ContentDescription]\n"
                        + "    <ImageView android:id=\"@+id/android_logo\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                        + "     ~~~~~~~~~\n"
                        + "myres2/layout/accessibility1.xml:4: Warning: Missing contentDescription attribute on image [ContentDescription]\n"
                        + "    <ImageView android:id=\"@+id/android_logo\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                        + "     ~~~~~~~~~\n"
                        + "myres1/layout/accessibility1.xml:5: Warning: Missing contentDescription attribute on image [ContentDescription]\n"
                        + "    <ImageButton android:importantForAccessibility=\"yes\" android:id=\"@+id/android_logo2\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                        + "     ~~~~~~~~~~~\n"
                        + "myres2/layout/accessibility1.xml:5: Warning: Missing contentDescription attribute on image [ContentDescription]\n"
                        + "    <ImageButton android:importantForAccessibility=\"yes\" android:id=\"@+id/android_logo2\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                        + "     ~~~~~~~~~~~\n"
                        + "0 errors, 4 warnings\n", // Expected output
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {
                    "--check",
                    "ContentDescription",
                    "--disable",
                    "LintError",
                    "--resources",
                    // Combine two paths with a single separator here
                    new File(project, "myres1").getPath()
                            + ':'
                            + new File(project, "myres2").getPath(),
                    project.getPath(),
                });
    }

    public void testClassPath() throws Exception {
        File project = getProjectDir(null, manifest().minSdk(1), cipherTestSource, cipherTestClass);
        checkDriver(
                ""
                        + "src/test/pkg/CipherTest1.java:11: Warning: Potentially insecure random numbers on Android 4.3 and older. Read https://android-developers.blogspot.com/2013/08/some-securerandom-thoughts.html for more info. [TrulyRandom]\n"
                        + "        cipher.init(Cipher.WRAP_MODE, key); // FLAG\n"
                        + "               ~~~~\n"
                        + "0 errors, 1 warnings\n",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {
                    "--check",
                    "TrulyRandom",
                    "--classpath",
                    new File(project, "bin/classes.jar").getPath(),
                    "--disable",
                    "LintError",
                    project.getPath()
                });
    }

    public void testList() throws Exception {
        checkDriver(
                "\"XmlEscapeNeeded\": Missing XML Escape",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {
                    "--list",
                    // "SdCardPath",
                    // "--disable",
                    // "LintError"
                },
                s -> {
                    // "--list" produces a massive list -- listing all available issues.
                    // With bug b/277590473, part of the output wasn't emitted, so we were missing
                    // the last few entries.
                    // Instead of checking in the complete expected output here, we'll just
                    // filter all the output down to any lines that contain "XmlEscapeNeeded", which
                    // is the last line of output. When the bug is present, this part is never
                    // included.
                    // With proper buffer flushing, it shows up, so the expected output should be
                    // exactly the output line containing the XmlEscapeNeeded entry.
                    return Arrays.stream(s.split("\n"))
                            .filter(s1 -> s1.contains("XmlEscapeNeeded"))
                            .collect(Collectors.joining("\n"));
                },
                null);
    }

    public void testLibraries() throws Exception {
        File project = getProjectDir(null, manifest().minSdk(1), cipherTestSource, cipherTestClass);
        checkDriver(
                "No issues found.",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {
                    "--check",
                    "TrulyRandom",
                    "--libraries",
                    new File(project, "bin/classes.jar").getPath(),
                    "--disable",
                    "LintError",
                    project.getPath()
                });
    }

    public void testCreateBaseline() throws Exception {
        File baseline = File.createTempFile("baseline", "xml");
        //noinspection ResultOfMethodCallIgnored
        baseline.delete(); // shouldn't exist
        assertFalse(baseline.exists());
        //noinspection ConcatenationWithEmptyString
        checkDriver(
                // Expected output
                null,

                // Expected error
                ""
                        + "Created baseline file "
                        + cleanup(baseline.getPath())
                        + "\n"
                        + "\n"
                        + "Also breaking the build in case this was not intentional. If you\n"
                        + "deliberately created the baseline file, re-run the build and this\n"
                        + "time it should succeed without warnings.\n"
                        + "\n"
                        + "If not, investigate the baseline path in the lintOptions config\n"
                        + "or verify that the baseline file has been checked into version\n"
                        + "control.\n"
                        + "\n"
                        + "You can run lint with -Dlint.baselines.continue=true\n"
                        + "if you want to create many missing baselines in one go.",

                // Expected exit code
                ERRNO_CREATED_BASELINE,

                // Args
                new String[] {
                    "--check",
                    "ContentDescription",
                    "--baseline",
                    baseline.getPath(),
                    "--sdk-home", // SDK is needed to get version number for the baseline
                    TestUtils.getSdk().toString(),
                    "--disable",
                    "LintError",
                    "--client-id",
                    "gradle",
                    "--client-version",
                    "4.2.1",
                    "--client-name",
                    "AGP",
                    getProjectDir(null, mAccessibility).getPath()
                });
        assertTrue(baseline.exists());

        String baselineContents = FilesKt.readText(baseline, Charsets.UTF_8);
        assertThat(baselineContents).contains("client=\"gradle\"");
        assertThat(baselineContents).contains("name=\"AGP (4.2.1)\"");

        //noinspection ResultOfMethodCallIgnored
        baseline.delete();
    }

    public void testUpdateBaseline() throws Exception {
        File baseline = File.createTempFile("baseline", "xml");
        FilesKt.writeText(
                baseline,
                // language=XML
                "<issues></issues>",
                Charsets.UTF_8);

        checkDriver(
                // Expected output
                ""
                        + "res/layout/accessibility.xml:4: Information: Missing contentDescription attribute on image [ContentDescription]\n"
                        + "    <ImageView android:id=\"@+id/android_logo\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                        + "     ~~~~~~~~~\n"
                        + "res/layout/accessibility.xml:5: Information: Missing contentDescription attribute on image [ContentDescription]\n"
                        + "    <ImageButton android:importantForAccessibility=\"yes\" android:id=\"@+id/android_logo2\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                        + "     ~~~~~~~~~~~\n"
                        + "0 errors, 0 warnings\n",

                // Expected error
                "",

                // Expected exit code
                ERRNO_CREATED_BASELINE,

                // Args
                new String[] {
                    "--check",
                    "ContentDescription",
                    "--info",
                    "ContentDescription",
                    "--baseline",
                    baseline.getPath(),
                    "--update-baseline",
                    "--disable",
                    "LintError",
                    getProjectDir(null, mAccessibility).getPath()
                });

        // Skip the first three lines that contain just the version which can change.
        String newBaseline =
                Files.readAllLines(baseline.toPath()).stream()
                        .skip(3)
                        .collect(Collectors.joining("\n"));
        // TODO: See b/209433064
        newBaseline = dos2unix(newBaseline);

        String expected =
                "    <issue\n"
                        + "        id=\"ContentDescription\"\n"
                        + "        message=\"Missing `contentDescription` attribute on image\"\n"
                        + "        errorLine1=\"    &lt;ImageView android:id=&quot;@+id/android_logo&quot; android:layout_width=&quot;wrap_content&quot; android:layout_height=&quot;wrap_content&quot; android:src=&quot;@drawable/android_button&quot; android:focusable=&quot;false&quot; android:clickable=&quot;false&quot; android:layout_weight=&quot;1.0&quot; />\"\n"
                        + "        errorLine2=\"     ~~~~~~~~~\">\n"
                        + "        <location\n"
                        + "            file=\"res/layout/accessibility.xml\"\n"
                        + "            line=\"4\"\n"
                        + "            column=\"6\"/>\n"
                        + "    </issue>\n"
                        + "\n"
                        + "    <issue\n"
                        + "        id=\"ContentDescription\"\n"
                        + "        message=\"Missing `contentDescription` attribute on image\"\n"
                        + "        errorLine1=\"    &lt;ImageButton android:importantForAccessibility=&quot;yes&quot; android:id=&quot;@+id/android_logo2&quot; android:layout_width=&quot;wrap_content&quot; android:layout_height=&quot;wrap_content&quot; android:src=&quot;@drawable/android_button&quot; android:focusable=&quot;false&quot; android:clickable=&quot;false&quot; android:layout_weight=&quot;1.0&quot; />\"\n"
                        + "        errorLine2=\"     ~~~~~~~~~~~\">\n"
                        + "        <location\n"
                        + "            file=\"res/layout/accessibility.xml\"\n"
                        + "            line=\"5\"\n"
                        + "            column=\"6\"/>\n"
                        + "    </issue>\n"
                        + "\n"
                        + "</issues>";

        assertEquals(expected, newBaseline);

        baseline.delete();
    }

    /**
     * This test emulates Google3's `android_lint` setup, and catches regression caused by relative
     * path for JAR files.
     */
    public void testRelativePaths() throws Exception {
        // Project with source only
        File project = getProjectDir(null, manifest().minSdk(1), cipherTestSource);

        // Create external jar somewhere outside of project dir.
        File pwd = new File(System.getProperty("user.dir"));
        assertTrue(pwd.isDirectory());
        File classFile = cipherTestClass.createFile(pwd);

        try {
            checkDriver(
                    ""
                            + "src/test/pkg/CipherTest1.java:11: Warning: Potentially insecure random numbers on Android 4.3 and older. Read https://android-developers.blogspot.com/2013/08/some-securerandom-thoughts.html for more info. [TrulyRandom]\n"
                            + "        cipher.init(Cipher.WRAP_MODE, key); // FLAG\n"
                            + "               ~~~~\n"
                            + "0 errors, 1 warnings\n",
                    "",

                    // Expected exit code
                    ERRNO_SUCCESS,

                    // Args
                    new String[] {
                        "--check",
                        "TrulyRandom",
                        "--classpath",
                        cipherTestClass.targetRelativePath,
                        "--disable",
                        "LintError",
                        project.getPath()
                    });
        } finally {
            classFile.delete();
        }
    }

    @Override
    protected Detector getDetector() {
        // Sample issue to check by the main driver
        return new AccessibilityDetector();
    }

    public void testGradle() throws Exception {
        File project =
                getProjectDir(
                        null,
                        manifest().minSdk(1),
                        source("build.gradle", ""), // placeholder; only name counts
                        // placeholder to ensure we have .class files
                        source("bin/classes/foo/bar/ApiCallTest.class", ""));
        checkDriver(
                ""
                        + "\n"
                        + "build.gradle: Error: \"MainTest_testGradle\" is a Gradle project. To correctly analyze Gradle projects, you should run \"gradlew lint\" instead. [LintError]\n"
                        + "1 errors, 0 warnings\n",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {"--check", "HardcodedText", project.getPath()});
    }

    public void testGradleKts() throws Exception {
        File project =
                getProjectDir(
                        null,
                        manifest().minSdk(1),
                        source("build.gradle.kts", ""), // placeholder; only name counts
                        // placeholder to ensure we have .class files
                        source("bin/classes/foo/bar/ApiCallTest.class", ""));
        checkDriver(
                ""
                        + "\n"
                        + "build.gradle.kts: Error: \"MainTest_testGradleKts\" is a Gradle project. To correctly analyze Gradle projects, you should run \"gradlew lint\" instead. [LintError]\n"
                        + "1 errors, 0 warnings\n",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {"--check", "HardcodedText", project.getPath()});
    }

    public void testIssueAliasing() throws Exception {
        File project =
                getProjectDir(
                        null,
                        manifest().minSdk(28),
                        xml(
                                "res/font/font1.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    android:fontProviderQuery=\"Monserrat\">\n"
                                        + "    <font\n"
                                        + "        android:fontStyle=\"normal\"\n"
                                        + "        android:fontWeight=\"400\"\n"
                                        + "        android:font=\"@font/monserrat\" />\n"
                                        + "</font-family>"
                                        + "\n"));
        String expected =
                ""
                        + "res/font/font1.xml:4: Warning: A downloadable font cannot have a <font> sub tag [FontValidation]\n"
                        + "    <font\n"
                        + "     ~~~~\n"
                        + "0 errors, 1 warnings";
        checkDriver(
                expected,
                "",
                ERRNO_SUCCESS,
                new String[] {
                    // The FontValidationWarning id is an old alias for FontValidation; here
                    // we're testing that reported error applied to FontValidation and changed
                    // its severity to warning
                    "--warning",
                    "FontValidationWarning",
                    "--disable",
                    "UnusedResources",
                    project.getPath()
                });

        File lintXml = new File(project, "lint.xml");
        FilesKt.writeText(
                lintXml,
                ""
                        + "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
                        + "<lint>\n"
                        + "    <issue id=\"FontValidationWarning\" severity=\"warning\"/>\n"
                        + "    <issue id=\"FontValidation\"><ignore path=\"test\"/></issue>\n"
                        + "</lint>",
                Charsets.UTF_8);
        checkDriver(
                expected,
                "",
                ERRNO_SUCCESS,
                new String[] {"--disable", "UnusedResources", project.getPath()});
    }

    public void testWall() throws Exception {
        File project = getProjectDir(null, java("class Test {\n    // STOPSHIP\n}"));
        checkDriver(
                ""
                        + "src/Test.java:2: Error: STOPSHIP comment found; points to code which must be fixed prior to release [StopShip]\n"
                        + "    // STOPSHIP\n"
                        + "       ~~~~~~~~\n"
                        + "1 errors, 0 warnings",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {"-Wall", "--disable", "LintError", project.getPath()});
    }

    public void testWerror() throws Exception {
        File project =
                getProjectDir(null, java("class Test {\n    String s = \"/sdcard/path\";\n}"));
        checkDriver(
                ""
                        + "src/Test.java:2: Error: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                        + "    String s = \"/sdcard/path\";\n"
                        + "               ~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {"-Werror", "--disable", "LintError", project.getPath()});
    }

    public void testNoWarn() throws Exception {
        File project =
                getProjectDir(
                        null,
                        java("" + "class Test {\n    String s = \"/sdcard/path\";\n}"),
                        xml(
                                "res/layout/test.xml",
                                ""
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "    <Button android:id='@+id/duplicated'/>\n"
                                        + "    <Button android:id='@+id/duplicated'/>\n"
                                        + "    <ImageButton/>\n"
                                        + "</LinearLayout>\n"));
        checkDriver(
                ""
                        + "res/layout/test.xml:3: Error: Duplicate id @+id/duplicated, already defined earlier in this layout [DuplicateIds]\n"
                        + "    <Button android:id='@+id/duplicated'/>\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "    res/layout/test.xml:2: Duplicate id @+id/duplicated originally defined here\n"
                        + "1 errors, 0 warnings",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {
                    "--info",
                    "ContentDescription",
                    "-w",
                    "--disable",
                    "LintError",
                    project.getPath()
                });
    }

    public void testWarningsAsErrors() throws Exception {
        // Regression test for 177439519
        // The scenario is that we have warningsAsErrors turned on in an override
        // configuration, and then lintConfig pointing to a lint.xml file which
        // ignores some lint checks. We want the ignored lint checks to NOT be
        // turned on on as errors. We also want any warning-severity issue not
        // otherwise mentioned to turn into errors.
        File project =
                getProjectDir(
                        null,
                        java("class Test {\n    String s = \"/sdcard/path\";\n}"),
                        xml(
                                "res/layout/foo.xml",
                                ""
                                        + "<merge xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "    <Button android:text=\"Button\" android:id=\"@+id/button1\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\"></Button>\n"
                                        + "</merge>\n"));
        File lintXml = new File(project, "res" + File.separator + "lint.xml");
        FilesKt.writeText(
                lintXml,
                ""
                        + "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
                        + "<lint>\n"
                        + "    <issue id=\"HardcodedText\" severity=\"ignore\"/>\n"
                        + "</lint>",
                Charsets.UTF_8);
        checkDriver(
                ""
                        + "src/Test.java:2: Error: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                        + "    String s = \"/sdcard/path\";\n"
                        + "               ~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {
                    "-Werror", "--disable", "LintError,UnusedResources", project.getPath()
                });
    }

    public void testUnicodeFileName() throws Exception {
        File project =
                getProjectDir(
                        null,
                        java(
                                "src/test/pkg/HelløWorld.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "class HelløWorld {\n"
                                        + "    String s = \"/sdcard/path\";\n"
                                        + "}"));
        checkDriver(
                ""
                        + "src/test/pkg/HelløWorld.java:3: Error: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                        + "    String s = \"/sdcard/path\";\n"
                        + "               ~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings",
                "",
                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {
                    "-Werror", "--disable", "LintError,UnusedResources", project.getPath()
                });
    }

    public void testWrongThreadOff() throws Exception {
        // Make sure the wrong thread interprocedural check is not included with -Wall
        File project =
                getProjectDir(
                        null,
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import androidx.annotation.UiThread;\n"
                                        + "import androidx.annotation.WorkerThread;\n"
                                        + "\n"
                                        + "@FunctionalInterface\n"
                                        + "public interface Runnable {\n"
                                        + "  public abstract void run();\n"
                                        + "}\n"
                                        + "\n"
                                        + "class Test {\n"
                                        + "  @UiThread static void uiThreadStatic() { unannotatedStatic(); }\n"
                                        + "  static void unannotatedStatic() { workerThreadStatic(); }\n"
                                        + "  @WorkerThread static void workerThreadStatic() {}\n"
                                        + "\n"
                                        + "  @UiThread void uiThread() { unannotated(); }\n"
                                        + "  void unannotated() { workerThread(); }\n"
                                        + "  @WorkerThread void workerThread() {}\n"
                                        + "\n"
                                        + "  @UiThread void runUi() {}\n"
                                        + "  void runIt(Runnable r) { r.run(); }\n"
                                        + "  @WorkerThread void callRunIt() {\n"
                                        + "    runIt(() -> runUi());\n"
                                        + "  }\n"
                                        + "\n"
                                        + "  public static void main(String[] args) {\n"
                                        + "    Test instance = new Test();\n"
                                        + "    instance.uiThread();\n"
                                        + "  }\n"
                                        + "}\n"),
                        SUPPORT_ANNOTATIONS_JAR);
        checkDriver(
                "No issues found.",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {"-Wall", "--disable", "LintError", project.getPath()});
    }

    public void testInvalidLintXmlId() throws Exception {
        // Regression test for
        // 37070812: Lint does not fail when invalid issue ID is referenced in XML
        File project =
                getProjectDir(
                        null,
                        manifest().minSdk(1),
                        xml(
                                "lint.xml",
                                ""
                                        + "<lint>\n"
                                        + "    <issue id=\"all\" severity=\"warning\" />\n"
                                        + "    <issue id=\"UnknownIssueId\" severity=\"error\" />\n"
                                        + "    <issue id=\"SomeUnknownId\" severity=\"fatal\" />\n"
                                        + "    <issue id=\"Security\" severity=\"fatal\" />\n"
                                        + "    <issue id=\"Interoperability\" severity=\"ignore\" />\n"
                                        + "    <issue id=\"IconLauncherFormat\">\n"
                                        + "        <ignore path=\"src/main/res/mipmap-anydpi-v26/ic_launcher.xml\" />\n"
                                        + "        <ignore path=\"src/main/res/drawable/ic_launcher_foreground.xml\" />\n"
                                        + "        <ignore path=\"src/main/res/drawable/ic_launcher_background.xml\" />\n"
                                        + "    </issue>"
                                        + "</lint>"),
                        // placeholder to ensure we have .class files
                        source("bin/classes/foo/bar/ApiCallTest.class", ""));
        checkDriver(
                ""
                        + "lint.xml:4: Error: Unknown issue id \"SomeUnknownId\". Did you mean 'UnknownId' (Reference to an unknown id) ? [UnknownIssueId]\n"
                        + "    <issue id=\"SomeUnknownId\" severity=\"fatal\" />\n"
                        + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {"--check", "HardcodedText", project.getPath()});
    }

    public void testInvalidMinAndTargetSdk() throws Exception {
        // Regression test for b/353954025
        File project =
                getProjectDir(
                        null,
                        manifest(
                                "<manifest"
                                    + " xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"test.pkg\">\n"
                                    + "    <uses-sdk android:minSdkVersion=\"0\""
                                    + " android:targetSdkVersion=\"0\" />\n"
                                    + "</manifest>"));
        checkDriver(
                "No issues found.",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {
                    "--disable", "MissingVersion,ExpiredTargetSdkVersion", project.getPath()
                });
    }

    public void testSkipAnnotated() throws Exception {
        // Tests --skip-annotated

        // This test tests 2 scenarios:
        // (1) we don't run lint checks inside a class that was annotated @Generated (where the
        // Generated
        //     annotation is configured via the --skip-annotated command line flag)
        // (2) Even from a non-annotated file, for a call from there into a @Generated class, we
        // don't
        //     process any annotations from that @Generated class.
        File project =
                getProjectDir(
                        null,
                        manifest().minSdk(1),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "class Test {\n"
                                        + "    @androidx.annotation.VisibleForTesting\n"
                                        + "    public static void hidden() { }\n"
                                        + "}"),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "@javax.annotation.processing.Generated\n"
                                        + "class TestGenerated {\n"
                                        + "    @androidx.annotation.VisibleForTesting\n"
                                        + "    public static void hidden() { }\n"
                                        + "}"),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "class Test2 {\n"
                                        + "    public static void test1() { Test.hidden(); } // WARN 1\n"
                                        + "    public static void test2() { TestGenerated.hidden(); } // OK 1\n"
                                        + "}"),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "@javax.annotation.processing.Generated\n"
                                        + "class Test3 {\n"
                                        + "    public static void test1() { Test.hidden(); } // OK 2\n"
                                        + "    public static void test2() { TestGenerated.hidden(); } // OK 3\n"
                                        + "}"),
                        kotlin(
                                ""
                                        + "package test.pkg\n"
                                        + "import javax.annotation.processing.Generated\n"
                                        + "@Generated\n"
                                        + "fun test() { Test.hidden(); TestGenerated.hidden() } // OK 4\n"),
                        kotlin(
                                ""
                                        + "package test.pkg\n"
                                        + "import javax.annotation.processing.Generated\n"
                                        + "@Generated\n"
                                        + "class Test4 {\n"
                                        + "    fun test() { Test.hidden(); TestGenerated.hidden() } // OK 5\n"
                                        + "}\n"),
                        kotlin(
                                ""
                                        + "@file:Generated(\"something\")\n"
                                        + "package test.pkg\n"
                                        + "import javax.annotation.processing.Generated\n"
                                        + "class Test5 {\n"
                                        + "    fun test() { Test.hidden(); TestGenerated.hidden() } // OK 6\n"
                                        + "}\n"),
                        SUPPORT_ANNOTATIONS_JAR);
        checkDriver(
                ""
                        + "src/test/pkg/Test2.java:3: Warning: This method should only be accessed from tests or within private scope [VisibleForTests]\n"
                        + "    public static void test1() { Test.hidden(); } // WARN 1\n"
                        + "                                      ~~~~~~\n"
                        + "0 errors, 1 warnings",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {
                    "--check",
                    "VisibleForTests",
                    "--skip-annotated",
                    "a.b.c,javax.annotation.processing.Generated,d.e.f",
                    "--ignore",
                    "LintError",
                    project.getPath()
                });
    }

    public void testNoDesugaring() throws Exception {
        // Tests b/296372320#comment9
        File project =
                getProjectDir(
                        null,
                        manifest().minSdk(1),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "public class Test {\n"
                                        + "    public int test(byte b) {\n"
                                        + "        return java.lang.Byte.hashCode(b);\n"
                                        + "    }\n"
                                        + "}\n"));

        try {
            checkDriver(
                    ""
                            + "src/test/pkg/Test.java:4: Error: Call requires API level 24, or core library desugaring (current min is 1): java.lang.Byte#hashCode [NewApi]\n"
                            + "        return java.lang.Byte.hashCode(b);\n"
                            + "                              ~~~~~~~~\n"
                            + "1 errors, 0 warnings",
                    "",

                    // Expected exit code
                    ERRNO_SUCCESS,

                    // Args
                    new String[] {
                        "--check",
                        "NewApi",
                        "--ignore",
                        "LintError",
                        "--sdk-home",
                        TestUtils.getSdk().toString(),
                        "--Xdesugared-methods",
                        "none",
                        project.getPath()
                    });
        } finally {
            DesugaredMethodLookup.Companion.reset();
        }
    }

    public void testFutureApiVersion() throws Exception {
        // Tests b/296372320#comment9
        File project =
                getProjectDir(
                        null,
                        manifest().minSdk(1),
                        java(
                                "src/test/pkg/Test.java",
                                "package test.pkg;\n"
                                        + "public class Test {\n"
                                        + "    public int test(byte b) {\n"
                                        + "        return java.lang.Byte.hashCode(b);\n"
                                        + "    }\n"
                                        + "}\n"),
                        kotlin(
                                "src/test/pkg/test2.kt",
                                "" + "package test.pkg\n" + "fun someTest() {" + "}"),
                        kotlin("src/blank.kt", ""),
                        kotlin("src/test/pkg/test3.kt", "\n// My file\n"));

        File root = getTempDir();
        String codename = "future";
        int apiLevel = 100;
        // Stub SDK
        File sdkHome = new File(root, "sdk");
        File platformDir = new File(sdkHome, "platforms/" + codename);
        File apiFile = new File(platformDir, "data/api-versions.xml");
        File sourceProp = new File(platformDir, "source.properties");
        TestLintClient client = createClient();
        PlatformLookup platformLookup = client.getPlatformLookup();
        assertNotNull(platformLookup);
        IAndroidTarget target = platformLookup.getLatestSdkTarget(21, false, false);
        assertNotNull(target);
        File androidJar = target.getPath(IAndroidTarget.ANDROID_JAR).toFile();
        FilesKt.copyTo(androidJar, new File(platformDir, androidJar.getName()), false, 1024);

        //noinspection ResultOfMethodCallIgnored
        sourceProp.getParentFile().mkdirs();
        FilesKt.writeText(
                sourceProp,
                "Pkg.Desc=Android SDK Platform "
                        + codename
                        + "\n"
                        + "Pkg.UserSrc=false\n"
                        + "Platform.Version=13\n"
                        + "AndroidVersion.CodeName="
                        + codename
                        + "\n"
                        + "Pkg.Revision=2\n"
                        + "AndroidVersion.ApiLevel="
                        + apiLevel
                        + "\n"
                        + "AndroidVersion.ExtensionLevel=3\n"
                        + "AndroidVersion.IsBaseSdk=true\n"
                        + "Layoutlib.Api=15\n"
                        + "Layoutlib.Revision=1\n"
                        + "Platform.MinToolsRev=22",
                Charsets.UTF_8);

        //noinspection ResultOfMethodCallIgnored
        apiFile.getParentFile().mkdirs();
        FilesKt.writeText(
                apiFile,
                ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<api version=\"100\">\n"
                        + "        <class name=\"java/lang/Object\" since=\"1\">\n"
                        + "                <method name=\"&lt;init>()V\"/>\n"
                        + "                <method name=\"clone()Ljava/lang/Object;\"/>\n"
                        + "                <method name=\"equals(Ljava/lang/Object;)Z\"/>\n"
                        + "                <method name=\"finalize()V\"/>\n"
                        + "        </class>\n"
                        // Not real API; here so we can make sure we're really using this database
                        + "        <class name=\"android/MyTest\" since=\"14\">\n"
                        + "        </class>\n"
                        + "</api>\n",
                Charsets.UTF_8);

        try {
            checkDriver(
                    "src/test/pkg/Test.java:1: Error: Android API 100, future preview (Preview)"
                        + " requires a newer version of Lint than $CURRENT_VERSION: Lint API checks"
                        + " unavailable. [NewApi]\n"
                        + "package test.pkg;\n"
                        + "~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/test2.kt:1: Error: Android API 100, future preview"
                        + " (Preview) requires a newer version of Lint than $CURRENT_VERSION: Lint"
                        + " API checks unavailable. [NewApi]\n"
                        + "package test.pkg\n"
                        + "~~~~~~~~~~~~~~~~\n"
                        + "2 errors, 0 warnings",
                    "",

                    // Expected exit code
                    ERRNO_SUCCESS,

                    // Args
                    new String[] {
                        "--check",
                        "NewApi",
                        "--ignore",
                        "LintError",
                        "--sdk-home",
                        sdkHome.getPath(),
                        project.getPath()
                    },
                    new Cleanup() {
                        @Override
                        public String cleanup(String s) {
                            String revision =
                                    new LintCliClient(ToolsBaseTestLintClient.CLIENT_UNIT_TESTS)
                                            .getClientDisplayRevision();
                            return MainTest.this.cleanup(s).replace(revision, "$CURRENT_VERSION");
                        }
                    },
                    null);
        } finally {
            DesugaredMethodLookup.Companion.reset();
        }
    }

    public void testFatalOnly() throws Exception {
        // This is a lint infrastructure test to make sure we correctly include issues
        // with fatal only
        File project =
                getProjectDir(
                        null,
                        manifest().minSdk(1),
                        xml(
                                "lint.xml",
                                ""
                                        + "<lint>\n"
                                        + "    <issue id=\"DuplicateDefinition\" severity=\"fatal\"/>\n"
                                        + "</lint>\n"),
                        xml(
                                "res/layout/test.xml",
                                ""
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "    <Button android:id='@+id/duplicated'/>\n"
                                        + "    <Button android:id='@+id/duplicated'/>\n"
                                        + "</LinearLayout>\n"),
                        xml(
                                "res/values/duplicates.xml",
                                ""
                                        + "<resources>\n"
                                        + "    <item type=\"id\" name=\"name\" />\n"
                                        + "    <item type=\"id\" name=\"name\" />\n"
                                        + "</resources>\n"),
                        kotlin("val path = \"/sdcard/path\""));

        // Without --fatalOnly: Both errors and warnings are reported.
        checkDriver(
                ""
                        + "res/layout/test.xml:3: Error: Duplicate id @+id/duplicated, already defined earlier in this layout [DuplicateIds]\n"
                        + "    <Button android:id='@+id/duplicated'/>\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "    res/layout/test.xml:2: Duplicate id @+id/duplicated originally defined here\n"
                        + "res/values/duplicates.xml:3: Error: name has already been defined in this folder [DuplicateDefinition]\n"
                        + "    <item type=\"id\" name=\"name\" />\n"
                        + "                    ~~~~~~~~~~~\n"
                        + "    res/values/duplicates.xml:2: Previously defined here\n"
                        + "src/test.kt:1: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                        + "val path = \"/sdcard/path\"\n"
                        + "            ~~~~~~~~~~~~\n"
                        + "res/layout/test.xml:1: Warning: The resource R.layout.test appears to be unused [UnusedResources]\n"
                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "^\n"
                        + "2 errors, 2 warnings",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {"--disable", "LintError,ButtonStyle", project.getPath()});

        // WITH --fatalOnly: Only the DuplicateDefinition issue is flagged, since it is fatal.
        checkDriver(
                // Both an implicitly fatal issue (DuplicateIds) and an error severity issue
                // configured to be fatal via lint.xml (DuplicateDefinition)
                ""
                        + "res/layout/test.xml:3: Error: Duplicate id @+id/duplicated, already defined earlier in this layout [DuplicateIds]\n"
                        + "    <Button android:id='@+id/duplicated'/>\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "    res/layout/test.xml:2: Duplicate id @+id/duplicated originally defined here\n"
                        + "res/values/duplicates.xml:3: Error: name has already been defined in this folder [DuplicateDefinition]\n"
                        + "    <item type=\"id\" name=\"name\" />\n"
                        + "                    ~~~~~~~~~~~\n"
                        + "    res/values/duplicates.xml:2: Previously defined here\n"
                        + "2 errors, 0 warnings",
                "",
                ERRNO_ERRORS,

                // Args
                new String[] {
                    "--disable", "LintError", "--fatalOnly", "--exitcode", project.getPath()
                });
    }

    public void testPrintFirstError() throws Exception {
        // Regression test for 183625575: Lint tasks doesn't output errors anymore
        File project =
                getProjectDir(
                        null,
                        manifest().minSdk(1),
                        xml(
                                "lint.xml",
                                ""
                                        + "<lint>\n"
                                        + "    <issue id=\"DuplicateDefinition\" severity=\"fatal\"/>\n"
                                        + "</lint>\n"),
                        xml(
                                "res/layout/test.xml",
                                ""
                                        + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "    <Button android:id='@+id/duplicated'/>\n"
                                        + "    <Button android:id='@+id/duplicated'/>\n"
                                        + "</LinearLayout>\n"),
                        xml(
                                "res/values/duplicates.xml",
                                ""
                                        + "<resources>\n"
                                        + "    <item type=\"id\" name=\"name\" />\n"
                                        + "    <item type=\"id\" name=\"name\" />\n"
                                        + "</resources>\n"),
                        kotlin("val path = \"/sdcard/path\""));

        File html = File.createTempFile("report", "html");
        html.deleteOnExit();

        checkDriver(
                ""
                        + "Wrote HTML report to file://report.html\n"
                        + "Lint found 2 errors and 4 warnings. First failure:\n"
                        + "res/layout/test.xml:3: Error: Duplicate id @+id/duplicated, already defined earlier in this layout [DuplicateIds]\n"
                        + "    <Button android:id='@+id/duplicated'/>\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "    res/layout/test.xml:2: Duplicate id @+id/duplicated originally defined here",
                "",
                ERRNO_ERRORS,

                // Args
                new String[] {
                    "--disable",
                    "LintError",
                    "--html",
                    html.getPath(),
                    "--exitcode",
                    "--disable", // Regression test for b/182321297
                    "UnknownIssueId",
                    "--enable",
                    "SomeUnknownId",
                    project.getPath()
                },
                s ->
                        s.replace(dos2unix(html.getPath()), "report.html")
                                .replace("file:///", "file://"),
                null);
    }

    public void testValidateOutput() throws Exception {
        if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
            // This test relies on making directories not writable, then
            // running lint pointing the output to that directory
            // and checking that error messages make sense. This isn't
            // supported on Windows; calling file.setWritable(false) returns
            // false; so skip this entire test on Windows.
            return;
        }
        File project = getProjectDir(null, mAccessibility2);

        File outputDir = new File(project, "build");
        if (!outputDir.exists()) {
            assertTrue(outputDir.mkdirs());
        }
        if (!outputDir.canWrite()) {
            assertTrue(outputDir.setWritable(true));
        }

        checkDriver(
                "",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {
                    "--sdk-home", // SDK is needed to get version number for the baseline
                    TestUtils.getSdk().toString(),
                    "--text",
                    new File(outputDir, "foo2.text").getPath(),
                    project.getPath(),
                });

        //noinspection ResultOfMethodCallIgnored
        boolean disabledWrite = outputDir.setWritable(false);
        assertTrue(disabledWrite);

        checkDriver(
                "", // Expected output
                "Cannot write XML output file /TESTROOT/build/foo.xml\n", // Expected error

                // Expected exit code
                ERRNO_EXISTS,

                // Args
                new String[] {
                    "--xml", new File(outputDir, "foo.xml").getPath(), project.getPath(),
                });

        checkDriver(
                "", // Expected output
                "Cannot write HTML output file /TESTROOT/build/foo.html\n", // Expected error

                // Expected exit code
                ERRNO_EXISTS,

                // Args
                new String[] {
                    "--html", new File(outputDir, "foo.html").getPath(), project.getPath(),
                });

        checkDriver(
                "", // Expected output
                "Cannot write text output file /TESTROOT/build/foo.text\n", // Expected error

                // Expected exit code
                ERRNO_EXISTS,

                // Args
                new String[] {
                    "--text", new File(outputDir, "foo.text").getPath(), project.getPath(),
                });
    }

    public void testVersion() throws Exception {
        File project = getProjectDir(null, manifest().minSdk(1));
        checkDriver(
                "lint: version " + Version.ANDROID_GRADLE_PLUGIN_VERSION + "\n",
                "",

                // Expected exit code
                ERRNO_SUCCESS,

                // Args
                new String[] {"--version", "--check", "HardcodedText", project.getPath()});
    }

    @Override
    protected boolean isEnabled(Issue issue) {
        return true;
    }

    @Language("XML")
    private static final String ACCESSIBILITY_XML =
            ""
                    + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                    + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" android:id=\"@+id/newlinear\" android:orientation=\"vertical\" android:layout_width=\"match_parent\" android:layout_height=\"match_parent\">\n"
                    + "    <Button android:text=\"Button\" android:id=\"@+id/button1\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\"></Button>\n"
                    + "    <ImageView android:id=\"@+id/android_logo\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                    + "    <ImageButton android:importantForAccessibility=\"yes\" android:id=\"@+id/android_logo2\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                    + "    <Button android:text=\"Button\" android:id=\"@+id/button2\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\"></Button>\n"
                    + "    <Button android:id=\"@+android:id/summary\" android:contentDescription=\"@string/label\" />\n"
                    + "    <ImageButton android:importantForAccessibility=\"no\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:src=\"@drawable/android_button\" android:focusable=\"false\" android:clickable=\"false\" android:layout_weight=\"1.0\" />\n"
                    + "</LinearLayout>\n";

    private final TestFile mAccessibility = xml("res/layout/accessibility.xml", ACCESSIBILITY_XML);

    private final TestFile mAccessibility2 =
            xml("myres1/layout/accessibility1.xml", ACCESSIBILITY_XML);

    private final TestFile mAccessibility3 =
            xml("myres2/layout/accessibility1.xml", ACCESSIBILITY_XML);

    @SuppressWarnings("all") // Sample code
    private TestFile cipherTestSource =
            java(
                    ""
                            + "package test.pkg;\n"
                            + "\n"
                            + "import java.security.Key;\n"
                            + "import java.security.SecureRandom;\n"
                            + "\n"
                            + "import javax.crypto.Cipher;\n"
                            + "\n"
                            + "@SuppressWarnings(\"all\")\n"
                            + "public class CipherTest1 {\n"
                            + "    public void test1(Cipher cipher, Key key) {\n"
                            + "        cipher.init(Cipher.WRAP_MODE, key); // FLAG\n"
                            + "    }\n"
                            + "\n"
                            + "    public void test2(Cipher cipher, Key key, SecureRandom random) {\n"
                            + "        cipher.init(Cipher.ENCRYPT_MODE, key, random);\n"
                            + "    }\n"
                            + "\n"
                            + "    public void setup(String transform) {\n"
                            + "        Cipher cipher = Cipher.getInstance(transform);\n"
                            + "    }\n"
                            + "}\n");

    @SuppressWarnings("all") // Sample code
    private TestFile cipherTestClass =
            jar(
                    "bin/classes.jar",
                    base64gzip(
                            "test/pkg/CipherTest1.class",
                            ""
                                    + "H4sIAAAAAAAAAI1S227TQBA92zh2kgZSmrTlUqBAgSROa5U+BvFScYkIRSJV"
                                    + "3x13m7pNbMveoOazeCmIBz6Aj0LM2FYSgrnY2pnZ2Zk5M2f3+4+v3wDsY7cE"
                                    + "HVslPMBDFo9YbBt4bOCJgP7c9Vz1QiBXbxwLaAf+iRSodF1PHo5HfRke2f0h"
                                    + "eVa7vmMPj+3Q5X3q1NSZGwmsd5WMlBVcDKwDNzijHNrutQXy7N8TMOvdc/uj"
                                    + "fWk54SRQfhrVjp1WJJ1x6KqJ9VZO2tyD7sTHAmuZWdTqhZwIVDPSBUovLx0Z"
                                    + "KNf3IgNP0xaeCbz+7xYWXD025AfbO/FHSXthbAts/i2SkCOpxgFNkSBbQ9sb"
                                    + "WD0Vut4grlNUVCg69cMRs/tbCI3S88ehI1+5TPXKHLO7HFyGgYKBehkNNFmY"
                                    + "ZbSwI1DLugwqMEN43z+XjiIGZ64pa6l3gSe6an4mAhv1zh9ubT/z5F9kLg+k"
                                    + "6niRsj2HhmxkUZV5b/SE8/Sq+dMgmAqSRdpZpAXpfPMzxCcyllAiqcfOIpZJ"
                                    + "lpMA0tdIC1xHhaI4uYMc/YBh6q0rLM3SS6RByTolcYmtJCwtwdYKbsRlDayi"
                                    + "StG1tLM1WuvYSAGOyKeRLphaa+cKuUWESlyJEZpJ3BShMEUopAhs3cQt6mQe"
                                    + "6zbupFhvaMdd6uYXaO8WkapEQG1uFn2KpGMTdyk3T4sxf53lXlzn/k9RvT9I"
                                    + "XQQAAA=="));
}
