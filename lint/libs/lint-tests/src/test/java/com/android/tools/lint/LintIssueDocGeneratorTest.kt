/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.lint

import com.android.testutils.TestUtils
import com.android.tools.lint.LintIssueDocGenerator.Companion.computeResultMap
import com.android.tools.lint.LintIssueDocGenerator.Companion.getOutputIncidents
import com.android.tools.lint.LintIssueDocGenerator.Companion.getOutputLines
import com.android.tools.lint.LintIssueDocGenerator.Companion.isStubSource
import com.android.tools.lint.checks.infrastructure.dos2unix
import com.android.tools.lint.client.api.LintClient
import java.io.File
import java.io.File.pathSeparator
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.test.assertContains
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LintIssueDocGeneratorTest {
  @get:Rule var temporaryFolder = TemporaryFolder()

  @Before
  fun setUp() {
    LintClient.clientName = LintClient.CLIENT_UNIT_TESTS
  }

  @Test
  fun testMarkDeep() {
    // (This is the default output format)
    val outputFolder = temporaryFolder.root
    LintIssueDocGenerator.run(
      arrayOf("--no-index", "--issues", "SdCardPath,MissingClass", "--output", outputFolder.path)
    )
    val files = outputFolder.listFiles()!!.sortedBy { it.name }
    val names = files.joinToString { it.name }
    assertEquals("MissingClass.md.html, SdCardPath.md.html", names)
    val text = files[1].readText()
    assertEquals(
      """
            <meta charset="utf-8">
            (#) Hardcoded reference to `/sdcard`

            !!! WARNING: Hardcoded reference to `/sdcard`
               This is a warning.

            Id
            :   `SdCardPath`
            Summary
            :   Hardcoded reference to `/sdcard`
            Severity
            :   Warning
            Category
            :   Correctness
            Platform
            :   Android
            Vendor
            :   Android Open Source Project
            Feedback
            :   https://issuetracker.google.com/issues/new?component=192708
            Affects
            :   Kotlin and Java files
            Editing
            :   This check runs on the fly in the IDE editor
            See
            :   https://developer.android.com/training/data-storage#filesExternal

            Your code should not reference the `/sdcard` path directly; instead use
            `Environment.getExternalStorageDirectory().getPath()`.

            Similarly, do not reference the `/data/data/` path directly; it can vary
            in multi-user scenarios. Instead, use
            `Context.getFilesDir().getPath()`.

            (##) Suppressing

            You can suppress false positives using one of the following mechanisms:

            * Using a suppression annotation like this on the enclosing
              element:

              ```kt
              // Kotlin
              @Suppress("SdCardPath")
              fun method() {
                 problematicStatement()
              }
              ```

              or

              ```java
              // Java
              @SuppressWarnings("SdCardPath")
              void method() {
                 problematicStatement();
              }
              ```

            * Using a suppression comment like this on the line above:

              ```kt
              //noinspection SdCardPath
              problematicStatement()
              ```

            * Using a special `lint.xml` file in the source tree which turns off
              the check in that folder and any sub folder. A simple file might look
              like this:
              ```xml
              &lt;?xml version="1.0" encoding="UTF-8"?&gt;
              &lt;lint&gt;
                  &lt;issue id="SdCardPath" severity="ignore" /&gt;
              &lt;/lint&gt;
              ```
              Instead of `ignore` you can also change the severity here, for
              example from `error` to `warning`. You can find additional
              documentation on how to filter issues by path, regular expression and
              so on
              [here](https://googlesamples.github.io/android-custom-lint-rules/usage/lintxml.md.html).

            * In Gradle projects, using the DSL syntax to configure lint. For
              example, you can use something like
              ```gradle
              lintOptions {
                  disable 'SdCardPath'
              }
              ```
              In Android projects this should be nested inside an `android { }`
              block.

            * For manual invocations of `lint`, using the `--ignore` flag:
              ```
              ${'$'} lint --ignore SdCardPath ...`
              ```

            * Last, but not least, using baselines, as discussed
              [here](https://googlesamples.github.io/android-custom-lint-rules/usage/baselines.md.html).

            <!-- Markdeep: --><style class="fallback">body{visibility:hidden;white-space:pre;font-family:monospace}</style><script src="markdeep.min.js" charset="utf-8"></script><script src="https://morgan3d.github.io/markdeep/latest/markdeep.min.js" charset="utf-8"></script><script>window.alreadyProcessedMarkdeep||(document.body.style.visibility="visible")</script>
            """
        .trimIndent(),
      text,
    )
  }

  @Test
  fun testMarkdown() {
    val outputFolder = temporaryFolder.newFolder("out")
    val sourceFolder = temporaryFolder.newFolder("src")

    val packageFolder =
      File(sourceFolder, "lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks")
    packageFolder.mkdirs()
    File(packageFolder, "SdCardDetector.kt").writeText("// Copyright 1985, 2019, 2016-2018\n")
    // In reality this detector is in Kotlin but here testing that we correctly compute URLs based
    // on actual
    // discovered implementation type
    File(packageFolder, "BatteryDetector.java").writeText("\n/** (C) 2019-2020 */\n")

    LintIssueDocGenerator.run(
      arrayOf(
        "--md",
        "--no-index",
        "--issues",
        "SdCardPath,BatteryLife",
        "--source-url",
        "https://cs.android.com/android-studio/platform/tools/base/+/mirror-goog-studio-master-dev:lint/",
        sourceFolder.path,
        "--output",
        outputFolder.path,
      )
    )
    val files = outputFolder.listFiles()!!.sortedBy { it.name }
    val names = files.joinToString { it.name }
    assertEquals("BatteryLife.md, SdCardPath.md", names)
    val text = files[0].readText()
    assertEquals(
      """
            # Battery Life Issues

            Id             | `BatteryLife`
            ---------------|--------------------------------------------------------
            Summary        | Battery Life Issues
            Severity       | Warning
            Category       | Correctness
            Platform       | Android
            Vendor         | Android Open Source Project
            Feedback       | https://issuetracker.google.com/issues/new?component=192708
            Affects        | Kotlin and Java files and manifest files
            Editing        | This check runs on the fly in the IDE editor
            See            | https://developer.android.com/topic/performance/background-optimization
            Implementation | [Source Code](https://cs.android.com/android-studio/platform/tools/base/+/mirror-goog-studio-master-dev:lint/lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks/BatteryDetector.java)
            Copyright Year | 2020

            This issue flags code that either
            * negatively affects battery life, or
            * uses APIs that have recently changed behavior to prevent background
              tasks from consuming memory and battery excessively.

            Generally, you should be using `WorkManager` instead.

            For more details on how to update your code, please see
            https://developer.android.com/topic/performance/background-optimization.

            (##) Suppressing

            You can suppress false positives using one of the following mechanisms:

            * Using a suppression annotation like this on the enclosing
              element:

              ```kt
              // Kotlin
              @Suppress("BatteryLife")
              fun method() {
                 problematicStatement()
              }
              ```

              or

              ```java
              // Java
              @SuppressWarnings("BatteryLife")
              void method() {
                 problematicStatement();
              }
              ```

            * Using a suppression comment like this on the line above:

              ```kt
              //noinspection BatteryLife
              problematicStatement()
              ```

            * Adding the suppression attribute `tools:ignore="BatteryLife"` on the
              problematic XML element (or one of its enclosing elements). You may
              also need to add the following namespace declaration on the root
              element in the XML file if it's not already there:
              `xmlns:tools="http://schemas.android.com/tools"`.

              ```xml
              <?xml version="1.0" encoding="UTF-8"?>
              <manifest xmlns:tools="http://schemas.android.com/tools">
                  ...
                  <action tools:ignore="BatteryLife" .../>
                ...
              </manifest>
              ```

            * Using a special `lint.xml` file in the source tree which turns off
              the check in that folder and any sub folder. A simple file might look
              like this:
              ```xml
              <?xml version="1.0" encoding="UTF-8"?>
              <lint>
                  <issue id="BatteryLife" severity="ignore" />
              </lint>
              ```
              Instead of `ignore` you can also change the severity here, for
              example from `error` to `warning`. You can find additional
              documentation on how to filter issues by path, regular expression and
              so on
              [here](https://googlesamples.github.io/android-custom-lint-rules/usage/lintxml.md.html).

            * In Gradle projects, using the DSL syntax to configure lint. For
              example, you can use something like
              ```gradle
              lintOptions {
                  disable 'BatteryLife'
              }
              ```
              In Android projects this should be nested inside an `android { }`
              block.

            * For manual invocations of `lint`, using the `--ignore` flag:
              ```
              ${'$'} lint --ignore BatteryLife ...`
              ```

            * Last, but not least, using baselines, as discussed
              [here](https://googlesamples.github.io/android-custom-lint-rules/usage/baselines.md.html).
            """
        .trimIndent(),
      text,
    )
  }

  @Test
  fun testMarkdownIndex() {
    val outputFolder = temporaryFolder.newFolder("out")
    val sourceFolder = temporaryFolder.newFolder("src")

    val packageFolder =
      File(sourceFolder, "lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks")
    packageFolder.mkdirs()
    File(packageFolder, "InteroperabilityDetector.kt")
      .writeText("// Copyright 1985, 2019, 2016-2018\n")
    File(packageFolder, "MissingClassDetector.java").writeText("\n/** (C) 2019-2020 */\n")

    LintIssueDocGenerator.run(
      arrayOf(
        "--md",
        "--issues",
        "SdCardPath,MissingClass,ViewTag,LambdaLast",
        "--source-url",
        "https://cs.android.com/android-studio/platform/tools/base/+/mirror-goog-studio-master-dev:lint/",
        sourceFolder.path,
        "--output",
        outputFolder.path,
      )
    )
    val files = outputFolder.listFiles()!!.sortedBy { it.name }
    val names = files.joinToString { it.name }
    assertEquals(
      "LambdaLast.md, MissingClass.md, SdCardPath.md, ViewTag.md, categories.md, index.md, libraries.md, severity.md, vendors.md, year.md",
      names,
    )
    val alphabetical = files[5].readText()
    assertEquals(
      """
            # Lint Issue Index

            Order: Alphabetical | [By category](categories.md) | [By vendor](vendors.md) | [By severity](severity.md) | [By year](year.md) | [Libraries](libraries.md)

              - [LambdaLast: Lambda Parameters Last](LambdaLast.md)
              - [MissingClass: Missing registered class](MissingClass.md)
              - [SdCardPath: Hardcoded reference to `/sdcard`](SdCardPath.md)

            * Withdrawn or Obsolete Issues (1)

              - [ViewTag](ViewTag.md)
            """
        .trimIndent(),
      alphabetical,
    )
    val categories = files[4].readText()
    assertEquals(
      """
            # Lint Issue Index

            Order: [Alphabetical](index.md) | By category | [By vendor](vendors.md) | [By severity](severity.md) | [By year](year.md) | [Libraries](libraries.md)

            * Correctness (2)

              - [MissingClass: Missing registered class](MissingClass.md)
              - [SdCardPath: Hardcoded reference to `/sdcard`](SdCardPath.md)

            * Interoperability: Kotlin Interoperability (1)

              - [LambdaLast: Lambda Parameters Last](LambdaLast.md)

            * Withdrawn or Obsolete Issues (1)

              - [ViewTag](ViewTag.md)
            """
        .trimIndent(),
      categories,
    )
    val severities = files[7].readText()
    assertEquals(
      """
            # Lint Issue Index

            Order: [Alphabetical](index.md) | [By category](categories.md) | [By vendor](vendors.md) | By severity | [By year](year.md) | [Libraries](libraries.md)

            * Error (1)

              - [MissingClass: Missing registered class](MissingClass.md)

            * Warning (2)

              - [LambdaLast: Lambda Parameters Last](LambdaLast.md)
              - [SdCardPath: Hardcoded reference to `/sdcard`](SdCardPath.md)

            * Disabled By Default (1)

              - [LambdaLast](LambdaLast.md)

            * Withdrawn or Obsolete Issues (1)

              - [ViewTag](ViewTag.md)
            """
        .trimIndent(),
      severities,
    )
    val vendors = files[8].readText()
    assertEquals(
      """
            # Lint Issue Index

            Order: [Alphabetical](index.md) | [By category](categories.md) | By vendor | [By severity](severity.md) | [By year](year.md) | [Libraries](libraries.md)

            * Built In (3)

              - [LambdaLast: Lambda Parameters Last](LambdaLast.md)
              - [MissingClass: Missing registered class](MissingClass.md)
              - [SdCardPath: Hardcoded reference to `/sdcard`](SdCardPath.md)

            * Withdrawn or Obsolete Issues (1)

              - [ViewTag](ViewTag.md)
            """
        .trimIndent(),
      vendors,
    )
    val years = files[9].readText()
    assertEquals(
      """
            # Lint Issue Index

            Order: [Alphabetical](index.md) | [By category](categories.md) | [By vendor](vendors.md) | [By severity](severity.md) | By year | [Libraries](libraries.md)

            * 2020 (1)

              - [MissingClass: Missing registered class](MissingClass.md)

            * 2019 (1)

              - [LambdaLast: Lambda Parameters Last](LambdaLast.md)

            * Unknown (1)

              - [SdCardPath: Hardcoded reference to `/sdcard`](SdCardPath.md)

            * Withdrawn or Obsolete Issues (1)

              - [ViewTag](ViewTag.md)
            """
        .trimIndent(),
      years,
    )
  }

  @Test
  fun testMarkdownDeleted() {
    val outputFolder = temporaryFolder.root
    LintIssueDocGenerator.run(
      arrayOf(
        "--md",
        "--no-index",
        "--issues",
        // MissingRegistered has been renamed, ViewTag has been deleted
        "SdCardPath,MissingRegistered,ViewTag",
        "--output",
        outputFolder.path,
      )
    )
    val files = outputFolder.listFiles()!!.sortedBy { it.name }
    val names = files.joinToString { it.name }
    assertEquals("MissingRegistered.md, SdCardPath.md, ViewTag.md", names)
    val text = files[0].readText()
    assertEquals(
      """
            # MissingRegistered

            This issue id is an alias for [MissingClass](MissingClass.md).

            (Additional metadata not available.)
            """
        .trimIndent(),
      text,
    )
    val text2 = files[2].readText()
    assertEquals(
      """
            # ViewTag

            The issue for this id has been deleted or marked obsolete and can now be
            ignored.

            (Additional metadata not available.)
            """
        .trimIndent(),
      text2,
    )
  }

  @Test
  fun testSingleDoc() {
    val output = temporaryFolder.newFile()
    LintIssueDocGenerator.run(
      arrayOf(
        "--single-doc",
        "--md",
        "--issues",
        "SdCardPath,MissingClass",
        "--output",
        output.path,
      )
    )
    val text = output.readText()
    assertEquals(
      """
            # Lint Issues
            This document lists the built-in issues for Lint. Note that lint also reads additional
            checks directly bundled with libraries, so this is a subset of the checks lint will
            perform.

            ## Correctness

            ### Missing registered class

            Id         | `MissingClass`
            -----------|------------------------------------------------------------
            Previously | MissingRegistered
            Summary    | Missing registered class
            Severity   | Error
            Category   | Correctness
            Platform   | Android
            Vendor     | Android Open Source Project
            Feedback   | https://issuetracker.google.com/issues/new?component=192708
            Affects    | Manifest files and resource files
            Editing    | This check runs on the fly in the IDE editor
            See        | https://developer.android.com/guide/topics/manifest/manifest-intro.html

            If a class is referenced in the manifest or in a layout file, it must
            also exist in the project (or in one of the libraries included by the
            project. This check helps uncover typos in registration names, or
            attempts to rename or move classes without updating the XML references
            properly.

            ### Hardcoded reference to `/sdcard`

            Id       | `SdCardPath`
            ---------|--------------------------------------------------------------
            Summary  | Hardcoded reference to `/sdcard`
            Severity | Warning
            Category | Correctness
            Platform | Android
            Vendor   | Android Open Source Project
            Feedback | https://issuetracker.google.com/issues/new?component=192708
            Affects  | Kotlin and Java files
            Editing  | This check runs on the fly in the IDE editor
            See      | https://developer.android.com/training/data-storage#filesExternal

            Your code should not reference the `/sdcard` path directly; instead use
            `Environment.getExternalStorageDirectory().getPath()`.

            Similarly, do not reference the `/data/data/` path directly; it can vary
            in multi-user scenarios. Instead, use
            `Context.getFilesDir().getPath()`.
            """
        .trimIndent(),
      text,
    )
  }

  @Test
  fun testLintMainIntegration() {
    // Also allow invoking the documentation tool from the main lint
    // binary (so that you don't have to construct a long java command
    // with full classpath etc). This test makes sure this works.
    val outputFolder = temporaryFolder.root
    Main()
      .run(
        arrayOf(
          "--generate-docs", // Flag to lint
          "--md", // the rest of the flags are interpreted by this tool
          "--no-index",
          "--issues",
          "SdCardPath,MissingClass,ViewTag",
          "--output",
          outputFolder.path,
        )
      )
    val files = outputFolder.listFiles()!!.sortedBy { it.name }
    val names = files.joinToString { it.name }
    assertEquals("MissingClass.md, SdCardPath.md, ViewTag.md", names)
    val text = files[2].readText()
    assertEquals(
      """
            # ViewTag

            The issue for this id has been deleted or marked obsolete and can now be
            ignored.

            (Additional metadata not available.)
            """
        .trimIndent(),
      text,
    )
  }

  @Test
  fun testUsage() {
    val writer = StringWriter()
    LintIssueDocGenerator.printUsage(false, PrintWriter(writer))
    val usage = writer.toString().trim().replace("\r\n", "\n")
    assertEquals(
      """
            Usage: lint-issue-docs-generator [flags] --output <directory or file>]

            Flags:

            --help                            This message.
            --output <dir>                    Sets the path to write the documentation to.
                                              Normally a directory, unless --single-doc is
                                              also specified
            --single-doc                      Instead of writing one page per issue into a
                                              directory, write a single page containing
                                              all the issues
            --md                              Write to plain Markdown (.md) files instead
                                              of Markdeep (.md.html)
            --builtins                        Generate documentation for the built-in
                                              issues. This is implied if --lint-jars is
                                              not specified
            --lint-jars <jar-path>            Read the lint issues from the specific path
                                              (separated by $pathSeparator of custom jar files
            --issues [issues]                 Limits the issues documented to the specific
                                              (comma-separated) list of issue id's
            --source-url <url-prefix> <path>  Searches for the detector source code under
                                              the given source folder or folders separated
                                              by semicolons, and if found, prefixes the
                                              path with the given URL prefix and includes
                                              this source link in the issue
                                              documentation.
            --test-url <url-prefix> <path>    Like --source-url, but for detector unit
                                              tests instead. These must be named the same
                                              as the detector class, plus `Test` as a
                                              suffix.
            --no-index                        Do not include index files
            --no-suppress-info                Do not include suppression information
            --no-examples                     Do not include examples pulled from unit
                                              tests, if found
            --no-links                        Do not include hyperlinks to detector source
                                              code
            --no-severity                     Do not include the red, orange or green
                                              informational boxes showing the severity of
                                              each issue
            --verbose                         Verbose output
            """
        .trimIndent()
        .trim(),
      usage.trim(),
    )
  }

  @Test
  fun testCodeSample() {
    val sources = temporaryFolder.newFolder("sources")
    val testSources = temporaryFolder.newFolder("test-sources")
    val outputFolder = temporaryFolder.newFolder("report")

    val sourceFile = File(sources, "com/android/tools/lint/checks/SdCardDetector.kt")
    val testSourceFile = File(testSources, "com/android/tools/lint/checks/SdCardDetectorTest.java")
    sourceFile.parentFile?.mkdirs()
    testSourceFile.parentFile?.mkdirs()
    sourceFile.writeText("// Copyright 2020\n")
    // TODO: Test Kotlin test as well
    testSourceFile.writeText(
      """
            package com.android.tools.lint.checks;

            import com.android.tools.lint.detector.api.Detector;
            import org.intellij.lang.annotations.Language;

            public class SdCardDetectorTest extends AbstractCheckTest {
                @Override
                protected Detector getDetector() {
                    return new SdCardDetector();
                }
                public void testKotlin() {
                    //noinspection all // Sample code
                    lint().files(
                                    kotlin(
                                            ""
                                                    + "package test.pkg\n"
                                                    + "import android.support.v7.widget.RecyclerView // should be rewritten to AndroidX in docs\n"
                                                    + "class MyTest {\n"
                                                    + "    /* Don't reference an /sdcard path here: */\n"
                                                    + "    val s: String = \"/sdcard/mydir\"\n"
                                                    + "    val other: String = \"/other/string\"\n"
                                                    + "}\n"),
                                    gradle(""))
                            .run()
                            .expect(
                                    ""
                                            + "src/main/kotlin/test/pkg/MyTest.kt:4: Warning: Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]\n"
                                            + "    val s: String = \"/sdcard/mydir\"\n"
                                            + "                     ~~~~~~~~~~~~~\n"
                                            + "0 errors, 1 warnings\n");
                }

                public void testSuppressExample() {
                    lint().files(
                                    java(
                                            "src/test/pkg/MyInterface.java",
                                            ""
                                                    + "package test.pkg;\n"
                                                    + "import android.annotation.SuppressLint;\n"
                                                    + "public @interface MyInterface {\n"
                                                    + "    @SuppressLint(\"SdCardPath\")\n"
                                                    + "    String engineer() default \"/sdcard/this/is/wrong\";\n"
                                                    + "}\n"))
                            .run()
                            .expectClean();
                }
            }
            """
    )

    val examples = temporaryFolder.newFile("examples.jsonl")

    LintIssueDocGenerator.run(
      arrayOf(
        "--md",
        "--no-index",
        "--source-url",
        "http://example.com/lint-source-code/src/",
        sources.path,
        "--test-url",
        "http://example.com/lint-source-code/tests/",
        testSources.path,
        "--issues",
        "SdCardPath",
        "--no-suppress-info",
        "--output",
        outputFolder.path,
        "--examples",
        examples.path,
      )
    )
    val files = outputFolder.listFiles()!!.sortedBy { it.name }
    val names = files.joinToString { it.name }
    assertEquals("SdCardPath.md", names)
    val text = files[0].readText()
    assertEquals(
      """
            # Hardcoded reference to `/sdcard`

            Id             | `SdCardPath`
            ---------------|--------------------------------------------------------
            Summary        | Hardcoded reference to `/sdcard`
            Severity       | Warning
            Category       | Correctness
            Platform       | Android
            Vendor         | Android Open Source Project
            Feedback       | https://issuetracker.google.com/issues/new?component=192708
            Affects        | Kotlin and Java files
            Editing        | This check runs on the fly in the IDE editor
            See            | https://developer.android.com/training/data-storage#filesExternal
            Implementation | [Source Code](http://example.com/lint-source-code/src/com/android/tools/lint/checks/SdCardDetector.kt)
            Tests          | [Source Code](http://example.com/lint-source-code/tests/com/android/tools/lint/checks/SdCardDetectorTest.java)
            Copyright Year | 2020

            Your code should not reference the `/sdcard` path directly; instead use
            `Environment.getExternalStorageDirectory().getPath()`.

            Similarly, do not reference the `/data/data/` path directly; it can vary
            in multi-user scenarios. Instead, use
            `Context.getFilesDir().getPath()`.

            (##) Example

            Here is an example of lint warnings produced by this check:
            ```text
            src/main/kotlin/test/pkg/MyTest.kt:4:Warning: Do not hardcode
            "/sdcard/"; use Environment.getExternalStorageDirectory().getPath()
            instead [SdCardPath]
                val s: String = "/sdcard/mydir"
                                 ~~~~~~~~~~~~~
            ```

            Here is the source file referenced above:

            `src/main/kotlin/test/pkg/MyTest.kt`:
            ```kotlin
            package test.pkg
            import androidx.recyclerview.widget.RecyclerView // should be rewritten to AndroidX in docs
            class MyTest {
                /* Don't reference an /sdcard path here: */
                val s: String = "/sdcard/mydir"
                val other: String = "/other/string"
            }
            ```

            You can also visit the
            [source code](http://example.com/lint-source-code/tests/com/android/tools/lint/checks/SdCardDetectorTest.java)
            for the unit tests for this check to see additional scenarios.

            The above example was automatically extracted from the first unit test
            found for this lint check, `SdCardDetector.testKotlin`.
            To report a problem with this extracted sample, visit
            https://issuetracker.google.com/issues/new?component=192708.
            """
        .trimIndent(),
      text,
    )

    val evals =
      examples
        .readText()
        .
        // Trim trailing spaces and remove the added timestamp which varies by run
        lines()
        .filterNot { it.startsWith("  added: ") }
        .joinToString("\n") { it.trimEnd() }
    assertEquals(
      """
      {
          "id": "SdCardPath",
          "summary": "Hardcoded reference to `/sdcard`",
          "explanation": "Your code should not reference the `/sdcard` path directly; instead use `Environment.getExternalStorageDirectory().getPath()`.\n\nSimilarly, do not reference the `/data/data/` path directly; it can vary in multi-user scenarios. Instead, use `Context.getFilesDir().getPath()`.",
          "main-files": [
              {
                  "path": "src/main/kotlin/test/pkg/MyTest.kt",
                  "type": "kotlin",
                  "contents": "package test.pkg\nimport androidx.recyclerview.widget.RecyclerView\nclass MyTest {\n    \n    val s: String = \"/sdcard/mydir\"\n    val other: String = \"/other/string\"\n}"
              }
          ],
          "target-issues": [
            {
              "file": "src/main/kotlin/test/pkg/MyTest.kt",
              "lineNumber": "5",
              "lineContents": "val s: String = \"/sdcard/mydir\"",
              "message": "Do not hardcode \"/sdcard/\"; use Environment.getExternalStorageDirectory().getPath() instead."
            }
          ],
          "year": "2020",
          "severity": "warning",
          "category": "Correctness",
          "documentation": "https://googlesamples.github.io/android-custom-lint-rules/checks/SdCardPath.md.html",
          "priority": "6",
          "enabled-by-default": "true",
          "library": "built-in",
          "languages": "kotlin",
          "more-info-urls": [
              "https://developer.android.com/training/data-storage#filesExternal"
          ],
          "android-specific": "true",
          "source": "SdCardDetector.testKotlin"
      }
      """
        .trimIndent()
        .trim(),
      evals,
    )
  }

  @Test
  fun testCuratedCodeSample() {
    // Like testCodeSample, but here the test has a special name which indicates
    // that it's curated and in that case we include ALL the test files in the
    // output, along with file names, and all the output from that test.
    // (We also test using empty source and test urls.)
    val sources = temporaryFolder.newFolder("sources")
    val testSources = temporaryFolder.newFolder("test-sources")
    val outputFolder = temporaryFolder.newFolder("report")

    val sourceFile = File(sources, "com/android/tools/lint/checks/StringFormatDetector.kt")
    val testSourceFile =
      File(testSources, "com/android/tools/lint/checks/StringFormatDetectorTest.java")
    sourceFile.parentFile?.mkdirs()
    testSourceFile.parentFile?.mkdirs()
    sourceFile.createNewFile()
    // TODO: Test Kotlin test as well
    testSourceFile.writeText(
      """
            package com.android.tools.lint.checks;

            import com.android.tools.lint.detector.api.Detector;
            import org.intellij.lang.annotations.Language;

            public class StringFormatDetectorTest extends AbstractCheckTest {
                @Override
                protected Detector getDetector() {
                    return new StringFormatDetector();
                }
                public void testDocumentationExampleStringFormatMatches() {
                    lint().files(
                            xml(
                                    "res/values/formatstrings.xml",
                                    ""
                                            + "<resources>\n"
                                            + "    <string name=\"score\">Score: %1${'$'}d</string>\n"
                                            + "</resources>\n"),
                            java(
                                    ""
                                            + "import android.app.Activity;\n"
                                            + "\n"
                                            + "public class Test extends Activity {\n"
                                            + "    public void test() {\n"
                                            + "        String score = getString(R.string.score);\n"
                                            + "        String output4 = String.format(score, true);  // wrong\n"
                                            + "    }\n"
                                            + "}"),
                            java(""
                                    + "/*HIDE-FROM-DOCUMENTATION*/public class R {\n"
                                    + "    public static class string {\n"
                                    + "        public static final int score = 1;\n"
                                    + "    }\n"
                                    + "}\n")
                    ).run().expect(""
                            + "src/Test.java:6: Error: Wrong argument type for formatting argument '#1' in score: conversion is 'd', received boolean (argument #2 in method call) (Did you mean formatting character b?) [StringFormatMatches]\n"
                            + "        String output4 = String.format(score, true);  // wrong\n"
                            + "                                              ~~~~\n"
                            + "    res/values/formatstrings.xml:2: Conflicting argument declaration here\n"
                            + "    <string name=\"score\">Score: %1＄d</string>\n"
                            + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                            + "1 errors, 0 warnings");
                }
            }
            """
    )

    LintIssueDocGenerator.run(
      arrayOf(
        "--md",
        "--no-index",
        "--source-url",
        "",
        sources.path,
        "--test-url",
        "",
        testSources.path,
        "--issues",
        "StringFormatMatches",
        "--no-suppress-info",
        "--output",
        outputFolder.path,
      )
    )
    val files = outputFolder.listFiles()!!.sortedBy { it.name }
    val names = files.joinToString { it.name }
    assertEquals("StringFormatMatches.md", names)
    val text = files[0].readText()
    assertEquals(
      """
            # `String.format` string doesn't match the XML format string

            Id       | `StringFormatMatches`
            ---------|------------------------------------------------------------
            Summary  | `String.format` string doesn't match the XML format string
            Severity | Error
            Category | Correctness: Messages
            Platform | Android
            Vendor   | Android Open Source Project
            Feedback | https://issuetracker.google.com/issues/new?component=192708
            Affects  | Kotlin and Java files and resource files
            Editing  | This check runs on the fly in the IDE editor

            This lint check ensures the following:
            (1) If there are multiple translations of the format string, then all
            translations use the same type for the same numbered arguments
            (2) The usage of the format string in Java is consistent with the format
            string, meaning that the parameter types passed to String.format matches
            those in the format string.

            (##) Example

            Here is an example of lint warnings produced by this check:
            ```text
            src/Test.java:6:Error: Wrong argument type for formatting argument '#1'
            in score: conversion is 'd', received boolean (argument #2 in method
            call) (Did you mean formatting character b?) [StringFormatMatches]
                String output4 = String.format(score, true);  // wrong
                                                      ~~~~
            ```

            Here are the relevant source files:

            `res/values/formatstrings.xml`:
            ```xml
            <resources>
                <string name="score">Score: %1${"$"}d</string>
            </resources>
            ```

            `src/Test.java`:
            ```java
            import android.app.Activity;

            public class Test extends Activity {
                public void test() {
                    String score = getString(R.string.score);
                    String output4 = String.format(score, true);  // wrong
                }
            }
            ```
            """
        .trimIndent(),
      text,
    )
  }

  @Test
  fun testOptions() {
    // (This is the default output format)
    val outputFolder = temporaryFolder.root
    LintIssueDocGenerator.run(
      arrayOf(
        "--no-index",
        "--issues",
        "UnknownNullness",
        "--no-suppress-info",
        "--output",
        outputFolder.path,
      )
    )
    val files = outputFolder.listFiles()!!.sortedBy { it.name }
    val names = files.joinToString { it.name }
    assertEquals("UnknownNullness.md.html", names)
    val text = files[0].readText()
    assertEquals(
      """
            <meta charset="utf-8">
            (#) Unknown nullness

            !!! WARNING: Unknown nullness
               This is a warning.

            Id
            :   `UnknownNullness`
            Summary
            :   Unknown nullness
            Note
            :   **This issue is disabled by default**; use `--enable UnknownNullness`
            Severity
            :   Warning
            Category
            :   Interoperability: Kotlin Interoperability
            Platform
            :   Any
            Vendor
            :   Android Open Source Project
            Feedback
            :   https://issuetracker.google.com/issues/new?component=192708
            Affects
            :   Kotlin and Java files
            Editing
            :   This check runs on the fly in the IDE editor
            See
            :   https://developer.android.com/kotlin/interop#nullability_annotations

            To improve referencing this code from Kotlin, consider adding explicit
            nullness information here with either `@NonNull` or `@Nullable`.

            !!! Tip
               This lint check has an associated quickfix available in the IDE.

            (##) Options

            You can configure this lint checks using the following options:

            (###) ignore-deprecated

            Whether to ignore classes and members that have been annotated with `@Deprecated`.
            Normally this lint check will flag all unannotated elements, but by setting this option to `true` it will skip any deprecated elements.

            Default is false.

            Example `lint.xml`:

            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~xml linenumbers
            &lt;lint&gt;
                &lt;issue id="UnknownNullness"&gt;
                    &lt;option name="ignore-deprecated" value="false" /&gt;
                &lt;/issue&gt;
            &lt;/lint&gt;
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


            <!-- Markdeep: --><style class="fallback">body{visibility:hidden;white-space:pre;font-family:monospace}</style><script src="markdeep.min.js" charset="utf-8"></script><script src="https://morgan3d.github.io/markdeep/latest/markdeep.min.js" charset="utf-8"></script><script>window.alreadyProcessedMarkdeep||(document.body.style.visibility="visible")</script>
            """
        .trimIndent(),
      text,
    )
  }

  @Test
  fun testVendor() {
    val outputFolder = temporaryFolder.root
    val fragmentFolder =
      File("${LintIssueDocGenerator.getGmavenCache()}/m2repository/androidx/fragment/fragment")
    if (!fragmentFolder.isDirectory) {
      println("Skipping testVendor: no cache available")
      return
    }
    val jars = fragmentFolder.walkTopDown().filter { it.name.endsWith(".jar") && it.length() > 0L }
    val jarArgument = jars.joinToString(";") { it.path }

    LintIssueDocGenerator.run(arrayOf("--lint-jars", jarArgument, "--output", outputFolder.path))
    val files = outputFolder.listFiles()!!.sortedBy { it.name }
    val names = files.joinToString { it.name }
    assertEquals(
      "DetachAndAttachSameFragment.md.html, DialogFragmentCallbacksDetector.md.html, FragmentAddMenuProvider.md.html, " +
        "FragmentBackPressedCallback.md.html, FragmentLiveDataObserve.md.html, FragmentTagUsage.md.html, " +
        "UnsafeRepeatOnLifecycleDetector.md.html, UseGetLayoutInflater.md.html, UseRequireInsteadOfGet.md.html, " +
        "androidx_fragment_fragment.md.html, categories.md.html, index.md.html, libraries.md.html, " +
        "severity.md.html, vendors.md.html, year.md.html",
      names,
    )
    val vendor = files.first { it.name == "androidx_fragment_fragment.md.html" }
    val text = vendor.readText()
    assertEquals(
      """
      (#) androidx.fragment:fragment

      Name
      :   fragment
      Description
      :   The Support Library is a static library that you can add to your Android
      :   application in order to use APIs that are either not available for older
      :   platform versions or utility APIs that aren't a part of the framework
      :   APIs. Compatible on devices running API 14 or later.
      License
      :   [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt)
      Vendor
      :   Android Open Source Project
      Identifier
      :   androidx.fragment
      Feedback
      :   https://issuetracker.google.com/issues/new?component=460964
      Min
      :   Lint 7.0
      Compiled
      :   Lint 8.0 and 8.1
      Artifact
      :   androidx.fragment:fragment:1.7.0-alpha01

      (##) Included Issues

      |Issue Id                                                                  |Issue Description                                                                                                       |
      |--------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
      |[FragmentTagUsage](FragmentTagUsage.md.html)                              |Use FragmentContainerView instead of the <fragment> tag                                                                 |
      |[FragmentAddMenuProvider](FragmentAddMenuProvider.md.html)                |Use getViewLifecycleOwner() as the LifecycleOwner instead of a Fragment instance.                                       |
      |[FragmentBackPressedCallback](FragmentBackPressedCallback.md.html)        |Use getViewLifecycleOwner() as the LifecycleOwner instead of a Fragment instance.                                       |
      |[FragmentLiveDataObserve](FragmentLiveDataObserve.md.html)                |Use getViewLifecycleOwner() as the LifecycleOwner instead of a Fragment instance when observing a LiveData object.      |
      |[UseRequireInsteadOfGet](UseRequireInsteadOfGet.md.html)                  |Use the 'require_____()' API rather than 'get____()' API for more descriptive error messages when it's null.            |
      |[UseGetLayoutInflater](UseGetLayoutInflater.md.html)                      |Use getLayoutInflater() to get the LayoutInflater instead of calling LayoutInflater.from(Context).                      |
      |[DialogFragmentCallbacksDetector](DialogFragmentCallbacksDetector.md.html)|Use onCancel() and onDismiss() instead of calling setOnCancelListener() and setOnDismissListener() from onCreateDialog()|
      |[UnsafeRepeatOnLifecycleDetector](UnsafeRepeatOnLifecycleDetector.md.html)|RepeatOnLifecycle should be used with viewLifecycleOwner in Fragments.                                                  |
      |[DetachAndAttachSameFragment](DetachAndAttachSameFragment.md.html)        |Separate attach() and detach() into separate FragmentTransactions                                                       |

      (##) Including

      !!!
         This is not a built-in check. To include it, add the below dependency
         to your project.

      ```
      // build.gradle.kts
      implementation("androidx.fragment:fragment:1.7.0-alpha01")

      // build.gradle
      implementation 'androidx.fragment:fragment:1.7.0-alpha01'

      // build.gradle.kts with version catalogs:
      implementation(libs.fragment)

      # libs.versions.toml
      [versions]
      fragment = "1.7.0-alpha01"
      [libraries]
      fragment = {
          module = "androidx.fragment:fragment",
          version.ref = "fragment"
      }
      ```

      1.7.0-alpha01 is the version this documentation was generated from;
      there may be newer versions available.

      (##) Changes

      * 1.2.0: First version includes FragmentLiveDataObserve,
        FragmentTagUsage.
      * 1.2.2: Adds FragmentBackPressedCallback, UseRequireInsteadOfGet.
      * 1.4.0: Adds DetachAndAttachSameFragment,
        DialogFragmentCallbacksDetector, FragmentAddMenuProvider,
        UnsafeRepeatOnLifecycleDetector, UseGetLayoutInflater.

      (##) Version Compatibility

      There are multiple older versions available of this library:

      | Version            | Date     | Issues | Compatible | Compiled      | Requires |
      |-------------------:|----------|-------:|------------|--------------:|---------:|
      |       1.7.0-alpha01|2023/06/07|       9|         Yes|    8.0 and 8.1|8.0 and 8.1|
      |               1.6.0|2023/06/07|       9|         Yes|    8.0 and 8.1|8.0 and 8.1|
      |               1.5.7|2023/04/19|       9|         Yes|    7.3 and 7.4|       7.0|
      |               1.5.6|2023/03/22|       9|         Yes|    7.3 and 7.4|       7.0|
      |               1.5.5|2022/12/07|       9|         Yes|    7.3 and 7.4|       7.0|
      |               1.5.4|2022/10/24|       9|         Yes|    7.3 and 7.4|       7.0|
      |               1.5.3|2022/09/21|       9|         Yes|    7.3 and 7.4|       7.0|
      |               1.5.2|2022/08/10|       9|         Yes|    7.3 and 7.4|       7.0|
      |               1.5.1|2022/07/27|       9|         Yes|    7.3 and 7.4|       7.0|
      |               1.5.0|2022/06/29|       9|      No[^1]|    7.3 and 7.4|       7.0|
      |               1.4.1|2022/01/26|       9|      No[^1]|            7.1|       7.1|
      |               1.4.0|2021/11/17|       9|      No[^1]|            7.1|       7.1|
      |               1.3.6|2021/07/21|       4|         Yes|            4.1|       3.3|
      |               1.3.5|2021/06/16|       4|         Yes|            4.1|       3.3|
      |               1.3.4|2021/05/18|       4|         Yes|            4.1|       3.3|
      |               1.3.3|2021/04/21|       4|         Yes|            4.1|       3.3|
      |               1.3.2|2021/03/24|       4|         Yes|            4.1|       3.3|
      |               1.3.1|2021/03/10|       4|         Yes|            4.1|       3.3|
      |               1.3.0|2021/02/10|       4|         Yes|            4.1|       3.3|
      |               1.2.5|2020/06/10|       4|         Yes|            3.6|       3.3|
      |               1.2.4|2020/04/01|       4|         Yes|            3.6|       3.3|
      |               1.2.3|2020/03/18|       4|         Yes|            3.6|       3.3|
      |               1.2.2|2020/02/19|       4|         Yes|            3.6|       3.3|
      |               1.2.1|2020/02/05|       2|         Yes|            3.6|       3.3|
      |               1.2.0|2020/01/22|       2|         Yes|            3.6|       3.3|

      Compatibility Problems:

      [^1]: org.jetbrains.uast.kotlin.KotlinUClass: org.jetbrains.kotlin.psi.KtClassOrObject getKtClass() is not accessible

      <!-- Markdeep: --><style class="fallback">body{visibility:hidden;white-space:pre;font-family:monospace}</style><script src="markdeep.min.js" charset="utf-8"></script><script src="https://morgan3d.github.io/markdeep/latest/markdeep.min.js" charset="utf-8"></script><script>window.alreadyProcessedMarkdeep||(document.body.style.visibility="visible")</script>
      """
        .trimIndent(),
      text.replace("  \n", "\n"), // intentional trailing spaces to force markdown new lines
    )

    val libraries = files.first { it.name == "libraries.md.html" }
    assertEquals(
      """
      <meta charset="utf-8">
      (#) Lint Issue Index

      Order: [Alphabetical](index.md.html) | [By category](categories.md.html) | [By vendor](vendors.md.html) | [By severity](severity.md.html) | [By year](year.md.html) | Libraries

      Android archive libraries which also contain bundled lint checks:

      * [androidx.fragment:fragment](androidx_fragment_fragment.md.html) (9 checks)

      <!-- Markdeep: --><style class="fallback">body{visibility:hidden;white-space:pre;font-family:monospace}</style><script src="markdeep.min.js" charset="utf-8"></script><script src="https://morgan3d.github.io/markdeep/latest/markdeep.min.js" charset="utf-8"></script><script>window.alreadyProcessedMarkdeep||(document.body.style.visibility="visible")</script>
      """
        .trimIndent(),
      libraries
        .readText()
        .replace("  \n", "\n"), // intentional trailing spaces to force markdown new lines
    )
  }

  @Test
  fun testUsageUpToDate() {
    val root = TestUtils.getWorkspaceRoot().toFile() ?: findSourceTree()
    val relativePath = "tools/base/lint/docs/usage/flags.md.html"
    val flags = File(root, relativePath)
    if (!flags.isFile) {
      // Not yet working from Bazel context; run in IDE to check
      return
    }
    val fileContents = flags.readText()
    val start = fileContents.indexOf("## ")
    val end = fileContents.lastIndexOf("<!-- Markdeep")
    val writer = StringWriter()
    Main.printUsage(PrintWriter(writer), true)
    val usage = writer.toString()
    val newContents =
      fileContents.substring(0, start) +
        usage.substring(usage.indexOf("## ")) +
        fileContents.substring(end)
    if (fileContents != newContents && findSourceTree() != null) {
      flags.writeText(newContents)
      fail("Command line flags changed. Updated $flags document.")
    }
    assertEquals(
      "$relativePath needs to be updated to reflect changes to the lint command line flags.\n" +
        "***If you set the environment variable $ADT_SOURCE_TREE (or set it as a system property " +
        "in the test run config) this test can automatically create/edit the files for you!***",
      newContents.dos2unix(),
      fileContents.dos2unix(),
    )
  }

  @Test
  fun testOutputParsing() {
    // Checks the various output parsing utilities in LintIssueDocGenerator
    val expected =
      """
        src/test/pkg/ConditionalApiTest.java:27: Warning: Unnecessary; SDK_INT is always >= 14 [ObsoleteSdkInt]
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/AlarmTest.java:9: Warning: Value will be forced up to 5000 as of Android 5.1; don't rely on this to be exact [ShortAlarm]
                alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, 50, 10, null); // ERROR
                                                                         ~~
        src/test/pkg/AlarmTest.java:9: Warning: Value will be forced up to 60000 as of Android 5.1; don't rely on this to be exact [ShortAlarm from mylibrary-1.0]
                alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, 50, 10, null); // ERROR
                                                                             ~~
        0 errors, 3 warnings
        """
        .trimIndent()

    val incidents = getOutputIncidents(expected)
    assertEquals(3, incidents.size)
    assertEquals(
      "" +
        "[ReportedIncident(path=src/test/pkg/ConditionalApiTest.java, severity=Warning, lineNumber=27, column=12," +
        " message=Unnecessary; SDK_INT is always >= 14, id=ObsoleteSdkInt," +
        " sourceLine1=        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {," +
        " sourceLine2=            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~), " +
        "ReportedIncident(path=src/test/pkg/AlarmTest.java, severity=Warning, lineNumber=9, column=65," +
        " message=Value will be forced up to 5000 as of Android 5.1; don't rely on this to be exact, id=ShortAlarm," +
        " sourceLine1=        alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, 50, 10, null); // ERROR," +
        " sourceLine2=                                                                 ~~), " +
        "ReportedIncident(path=src/test/pkg/AlarmTest.java, severity=Warning, lineNumber=9, column=69," +
        " message=Value will be forced up to 60000 as of Android 5.1; don't rely on this to be exact, id=ShortAlarm," +
        " sourceLine1=        alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, 50, 10, null); // ERROR," +
        " sourceLine2=                                                                     ~~)]",
      incidents.toString(),
    )

    val lines = getOutputLines(expected)
    assertEquals(3, lines.size)
    assertEquals(
      "" +
        "[Unnecessary; SDK_INT is always >= 14, " +
        "Value will be forced up to 5000 as of Android 5.1; don't rely on this to be exact, " +
        "Value will be forced up to 60000 as of Android 5.1; don't rely on this to be exact]",
      lines.toString(),
    )

    val map = computeResultMap("ShortAlarm", expected)
    assertEquals(
      "" +
        "{" +
        "src/test/pkg/AlarmTest.java={9=[Warning: Value will be forced up to 60000 as of Android 5.1; don't rely on this to be exact [ShortAlarm]," +
        "         alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, 50, 10, null); // ERROR," +
        "                                                                      ~~]}" +
        "}",
      map.toString(),
    )
  }

  @Test
  fun testOutputParsingMultiline() {
    val expected =
      """
        src/test.kt:2: Error: This @Composable function has a modifier parameter but it doesn't have a default value.
        See https://slackhq.github.io/compose-lints/rules/#modifiers-should-have-default-parameters for more information. [ComposeModifierWithoutDefault]
        fun Something(modifier: Modifier) { }
                      ~~~~~~~~~~~~~~~~~~
        src/test.kt:4: Error: This @Composable function has a modifier parameter but it doesn't have a default value.
        See https://slackhq.github.io/compose-lints/rules/#modifiers-should-have-default-parameters for more information. [ComposeModifierWithoutDefault]
        fun Something(modifier: Modifier = Modifier, modifier2: Modifier) { }
                                                     ~~~~~~~~~~~~~~~~~~~
        2 errors, 0 warnings
        """
        .trimIndent()

    val incidents = getOutputIncidents(expected)
    assertEquals(2, incidents.size)
    assertEquals(
      "" +
        "[ReportedIncident(path=src/test.kt, severity=Error, lineNumber=2, column=14," +
        " message=This @Composable function has a modifier parameter but it doesn't have a default value.\n" +
        "See https://slackhq.github.io/compose-lints/rules/#modifiers-should-have-default-parameters for more information.," +
        " id=ComposeModifierWithoutDefault," +
        " sourceLine1=fun Something(modifier: Modifier) { }," +
        " sourceLine2=              ~~~~~~~~~~~~~~~~~~), " +
        "ReportedIncident(path=src/test.kt, severity=Error, lineNumber=4, column=45," +
        " message=This @Composable function has a modifier parameter but it doesn't have a default value.\n" +
        "See https://slackhq.github.io/compose-lints/rules/#modifiers-should-have-default-parameters for more information.," +
        " id=ComposeModifierWithoutDefault," +
        " sourceLine1=fun Something(modifier: Modifier = Modifier, modifier2: Modifier) { }," +
        " sourceLine2=                                             ~~~~~~~~~~~~~~~~~~~)]",
      incidents.toString(),
    )

    val lines = getOutputLines(expected)
    assertEquals(2, lines.size)
    assertEquals(
      "" +
        "[This @Composable function has a modifier parameter but it doesn't have a default value.\n" +
        "See https://slackhq.github.io/compose-lints/rules/#modifiers-should-have-default-parameters for more information., " +
        "This @Composable function has a modifier parameter but it doesn't have a default value.\n" +
        "See https://slackhq.github.io/compose-lints/rules/#modifiers-should-have-default-parameters for more information.]",
      lines.toString(),
    )

    val map = computeResultMap("ComposeModifierWithoutDefault", expected)
    assertEquals(
      "" +
        "{src/test.kt={" +
        "2=[Error: This @Composable function has a modifier parameter but it doesn't have a default value.\n" +
        "See https://slackhq.github.io/compose-lints/rules/#modifiers-should-have-default-parameters for more information. [ComposeModifierWithoutDefault]," +
        " fun Something(modifier: Modifier) { }," +
        "               ~~~~~~~~~~~~~~~~~~], " +
        "4=[Error: This @Composable function has a modifier parameter but it doesn't have a default value.\n" +
        "See https://slackhq.github.io/compose-lints/rules/#modifiers-should-have-default-parameters for more information. [ComposeModifierWithoutDefault]," +
        " fun Something(modifier: Modifier = Modifier, modifier2: Modifier) { }," +
        "                                              ~~~~~~~~~~~~~~~~~~~]}" +
        "}",
      map.toString(),
    )
  }

  @Test
  fun testOutputWithBracketsInErrorMessage() {
    val expected =
      """
      res/layout/layout2.xml:18: Warning: Duplicate id @+id/button1, defined or included multiple times in layout/layout2.xml: [layout/layout2.xml => layout/layout3.xml defines @+id/button1, layout/layout2.xml => layout/layout4.xml defines @+id/button1] [DuplicateIncludedIds]
          <include
          ^
          res/layout/layout3.xml:8: Defined here, included via layout/layout2.xml => layout/layout3.xml defines @+id/button1
              android:id="@+id/button1"
              ~~~~~~~~~~~~~~~~~~~~~~~~~
          res/layout/layout4.xml:8: Defined here, included via layout/layout2.xml => layout/layout4.xml defines @+id/button1
              android:id="@+id/button1"
              ~~~~~~~~~~~~~~~~~~~~~~~~~
      0 errors, 1 warnings
        """
        .trimIndent()

    val incidents = getOutputIncidents(expected)
    assertEquals(1, incidents.size)
    assertEquals(
      "" +
        "[ReportedIncident(path=res/layout/layout2.xml, severity=Warning, lineNumber=18, column=4," +
        " message=Duplicate id @+id/button1, defined or included multiple times in layout/layout2.xml: [layout/layout2.xml => layout/layout3.xml defines @+id/button1, layout/layout2.xml => layout/layout4.xml defines @+id/button1]," +
        " id=DuplicateIncludedIds," +
        " sourceLine1=    <include," +
        " sourceLine2=    ^)]",
      incidents.toString(),
    )
  }

  @Test
  fun testIsStub() {
    assertTrue(isStubSource(""))
    assertTrue(isStubSource(" "))
    assertTrue(isStubSource("package android.app;\nclass Activity {}"))
    assertTrue(isStubSource("class Test { // This is a stub source\n}"))
    assertFalse(isStubSource("class Test { // This stubbornly refuses to work\n}"))
    assertTrue(isStubSource("class Test { /* HIDE-FROM-DOCUMENTATION */ }"))
    assertTrue(isStubSource("class R { };"))
  }

  @Test
  fun testQuickFixFound() {
    val outputFolder = temporaryFolder.newFolder("output")
    val testSources = temporaryFolder.newFolder("test-sources")

    // TypedefDetector has more than one issue id, but from the test case, we can tell
    // that WrongConstant has a quick-fix.
    val testSourceFile = File(testSources, "com/android/tools/lint/checks/TypedefDetectorTest.kt")

    testSourceFile.parentFile?.mkdirs()
    testSourceFile.writeText(
      """
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

      package com.android.tools.lint.checks

      import com.android.tools.lint.checks.infrastructure.TestMode
      import com.android.tools.lint.client.api.LintBaseline
      import com.android.tools.lint.detector.api.Detector
      import java.io.File
      import org.junit.ComparisonFailure

      class TypedefDetectorTest : AbstractCheckTest() {
        override fun getDetector(): Detector = TypedefDetector()
        fun testQuickfix() {
          lint()
            .files(
              java(
                  ""${'"'}
                      package test.pkg;
                      import android.app.AlarmManager;
                      import android.app.PendingIntent;

                      public class ExactAlarmTest {
                          public void test(AlarmManager alarmManager, PendingIntent operation) {
                              alarmManager.setExact(Integer.MAX_VALUE, 0L, operation);
                          }
                      }
                      ""${'"'}
                )
                .indented(),
              kotlin(
                  ""${'"'}
                      package test.pkg

                      import android.app.PendingIntent

                      fun test(alarmManager: android.app.AlarmManager, operation: PendingIntent?) {
                          alarmManager.setExact(1, 0L, operation)
                      }
                      ""${'"'}
                )
                .indented(),
            )
            .allowNonAlphabeticalFixOrder(true)
            .run()
            .expect(
              ""${'"'}
                  src/test/pkg/ExactAlarmTest.java:7: Error: Must be one of: AlarmManager.RTC_WAKEUP, AlarmManager.RTC, AlarmManager.ELAPSED_REALTIME_WAKEUP, AlarmManager.ELAPSED_REALTIME [WrongConstant]
                          alarmManager.setExact(Integer.MAX_VALUE, 0L, operation);
                                                ~~~~~~~~~~~~~~~~~
                  src/test/pkg/test.kt:6: Error: Must be one of: AlarmManager.RTC_WAKEUP, AlarmManager.RTC, AlarmManager.ELAPSED_REALTIME_WAKEUP, AlarmManager.ELAPSED_REALTIME [WrongConstant]
                      alarmManager.setExact(1, 0L, operation)
                                            ~
                  2 errors, 0 warnings
                  ""${'"'}
            )
            .expectFixDiffs(
              ""${'"'}
                  Fix for src/test/pkg/ExactAlarmTest.java line 7: Change to AlarmManager.RTC_WAKEUP:
                  @@ -7 +7
                  -         alarmManager.setExact(Integer.MAX_VALUE, 0L, operation);
                  +         alarmManager.setExact(AlarmManager.RTC_WAKEUP, 0L, operation);
                  Fix for src/test/pkg/ExactAlarmTest.java line 7: Change to AlarmManager.RTC:
                  @@ -7 +7
                  -         alarmManager.setExact(Integer.MAX_VALUE, 0L, operation);
                  +         alarmManager.setExact(AlarmManager.RTC, 0L, operation);
                  Fix for src/test/pkg/ExactAlarmTest.java line 7: Change to AlarmManager.ELAPSED_REALTIME_WAKEUP:
                  @@ -7 +7
                  -         alarmManager.setExact(Integer.MAX_VALUE, 0L, operation);
                  +         alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, 0L, operation);
                  Fix for src/test/pkg/ExactAlarmTest.java line 7: Change to AlarmManager.ELAPSED_REALTIME:
                  @@ -7 +7
                  -         alarmManager.setExact(Integer.MAX_VALUE, 0L, operation);
                  +         alarmManager.setExact(AlarmManager.ELAPSED_REALTIME, 0L, operation);
                  Fix for src/test/pkg/test.kt line 6: Change to AlarmManager.RTC (1):
                  @@ -3 +3
                  + import android.app.AlarmManager
                  @@ -6 +7
                  -     alarmManager.setExact(1, 0L, operation)
                  +     alarmManager.setExact(AlarmManager.RTC, 0L, operation)
                  Fix for src/test/pkg/test.kt line 6: Change to AlarmManager.RTC_WAKEUP:
                  @@ -3 +3
                  + import android.app.AlarmManager
                  @@ -6 +7
                  -     alarmManager.setExact(1, 0L, operation)
                  +     alarmManager.setExact(AlarmManager.RTC_WAKEUP, 0L, operation)
                  Fix for src/test/pkg/test.kt line 6: Change to AlarmManager.ELAPSED_REALTIME_WAKEUP:
                  @@ -3 +3
                  + import android.app.AlarmManager
                  @@ -6 +7
                  -     alarmManager.setExact(1, 0L, operation)
                  +     alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, 0L, operation)
                  Fix for src/test/pkg/test.kt line 6: Change to AlarmManager.ELAPSED_REALTIME:
                  @@ -3 +3
                  + import android.app.AlarmManager
                  @@ -6 +7
                  -     alarmManager.setExact(1, 0L, operation)
                  +     alarmManager.setExact(AlarmManager.ELAPSED_REALTIME, 0L, operation)
                  ""${'"'}
            )
        }
      }
      """
        .trimIndent()
    )

    LintIssueDocGenerator.run(
      arrayOf(
        "--no-index",
        "--test-url",
        "",
        testSources.path,
        "--issues",
        "WrongConstant",
        "--output",
        outputFolder.path,
      )
    )
    val files = outputFolder.listFiles()!!.sortedBy { it.name }
    val names = files.joinToString { it.name }
    assertEquals("WrongConstant.md.html", names)
    val text = files[0].readText()
    assertContains(text, "This lint check has an associated quickfix")
  }

  companion object {
    private const val ADT_SOURCE_TREE = "ADT_SOURCE_TREE"

    private fun findSourceTree(): File? {
      val sourceTree =
        System.getenv(ADT_SOURCE_TREE)
          ?: System.getProperty(ADT_SOURCE_TREE)
          // Tip: you can temporarily set your own path here:
          // ?: "/your/path"
          ?: return null

      return if (sourceTree.isNotBlank()) {
        File(sourceTree).apply {
          if (!File(this, ".repo").isDirectory) {
            fail(
              "Invalid directory $this: should be pointing to the root of a tools checkout directory"
            )
          }
        }
      } else null
    }
  }
}
