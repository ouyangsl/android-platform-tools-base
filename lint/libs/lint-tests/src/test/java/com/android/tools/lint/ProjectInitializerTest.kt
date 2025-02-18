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

package com.android.tools.lint

import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.FN_PUBLIC_TXT
import com.android.SdkConstants.FN_RESOURCE_TEXT
import com.android.testutils.TestUtils
import com.android.tools.lint.LintCliFlags.ERRNO_INVALID_ARGS
import com.android.tools.lint.LintCliFlags.ERRNO_SUCCESS
import com.android.tools.lint.checks.AbstractCheckTest.SUPPORT_ANNOTATIONS_JAR
import com.android.tools.lint.checks.AbstractCheckTest.base64gzip
import com.android.tools.lint.checks.AbstractCheckTest.jar
import com.android.tools.lint.checks.infrastructure.LintDetectorTest.bytes
import com.android.tools.lint.checks.infrastructure.ProjectDescription
import com.android.tools.lint.checks.infrastructure.ProjectDescription.Type.LIBRARY
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.klib
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestFiles.kt
import com.android.tools.lint.checks.infrastructure.TestFiles.manifest
import com.android.tools.lint.checks.infrastructure.TestFiles.source
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.checks.infrastructure.dos2unix
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.client.api.LintListener
import com.android.tools.lint.client.api.LintListener.EventType.REGISTERED_PROJECT
import com.android.tools.lint.client.api.LintListener.EventType.STARTING
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Project
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.google.common.io.Files
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.streams.toList
import kotlin.text.Charsets
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectInitializerTest {
  @Test
  fun testManualProject() {
    val library =
      project(
          manifest(
              """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="foo.bar2"
                    android:versionCode="1"
                    android:versionName="1.0" >

                    <uses-sdk android:minSdkVersion="14" />

                    <permission android:name="bar.permission.SEND_SMS"
                        android:label="@string/foo"
                        android:description="@string/foo" />

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                    </application>

                </manifest>"""
            )
            .indented(),
          java(
              "src/test/pkg/Loader.java",
              """
                package test.pkg;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public abstract class Loader<P> {
                    private P mParam;

                    public abstract void loadInBackground(P val);

                    public void load() {
                        // Invoke a method that takes a generic type.
                        loadInBackground(mParam);
                    }
                }""",
            )
            .indented(),
          java(
              "src/test/pkg/NotInProject.java",
              """
                package test.pkg;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class Foo {
                    private String foo = "/sdcard/foo";
                }
                """,
            )
            .indented(),
        )
        .type(LIBRARY)
        .name("Library")

    val main =
      project(
          manifest(
              """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="foo.bar2"
                    android:versionCode="1"
                    android:versionName="1.0" >

                    <uses-sdk android:minSdkVersion="14" />

                    <permission android:name="foo.permission.SEND_SMS"
                        android:label="@string/foo"
                        android:description="@string/foo" />

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                    </application>

                </manifest>
                """
            )
            .indented(),
          xml(
              "res/values/strings.xml",
              """
                <resources>
                    <string name="string1">String 1</string>
                    <string name="string1">String 2</string>
                    <string name="string3">String 3</string>
                    <string name="string3">String 4</string>
                </resources>
                """,
            )
            .indented(),
          xml(
              "res/values/not_in_project.xml",
              """
                <resources>
                    <string name="string2">String 1</string>
                    <string name="string2">String 2</string>
                </resources>
                """,
            )
            .indented(),
          java(
              "test/Test.java",
              """
                @SuppressWarnings({"MethodMayBeStatic", "ClassNameDiffersFromFileName"})
                public class Test {
                  String path = "/sdcard/file";
                }""",
            )
            .indented(),
          java(
              "generated/Generated.java",
              """
                @SuppressWarnings({"MethodMayBeStatic", "ClassNameDiffersFromFileName"})
                public class Test {
                  String path = "/sdcard/file";
                }""",
            )
            .indented(),
        )
        .name("App")
        .dependsOn(library)

    val root = temp.newFolder().canonicalFile.absoluteFile

    val configFile = File(root, "lint.xml")
    @Language("XML")
    val config =
      """
            <lint
                checkTestSources='false'
                ignoreTestSources='false'
                checkGeneratedSources='true'
                explainIssues='false'
            >
                <issue id="CheckResult" severity="error"/>
                <!-- Reduce severity of UniquePermission from error to warning -->
                <issue id="UniquePermission" severity="warning"/>
            </lint>
            """
    configFile.parentFile?.mkdirs()
    configFile.writeText(config.trimIndent())

    val projects = lint().projects(main, library).createProjects(root)
    // 1: test infrastructure will sort projects by dependency graph
    val appProjectDir = projects[1]
    val appProjectPath = appProjectDir.path

    // TO avoid already existing temp folders
    val suffix = if (useFirUast()) "-k2" else "-k1"
    val sdk = temp.newFolder("fake-sdk$suffix")
    val cacheDir = temp.newFolder("cache$suffix")
    @Language("XML")
    val mergedManifestXml =
      """

            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="foo.bar2"
                android:versionCode="1"
                android:versionName="1.0" >

                <uses-sdk android:minSdkVersion="14" />

                <permission
                    android:name="foo.permission.SEND_SMS"
                    android:description="@string/foo"
                    android:label="@string/foo" />
                <permission
                    android:name="bar.permission.SEND_SMS"
                    android:description="@string/foo"
                    android:label="@string/foo" />

                <application
                    android:icon="@drawable/ic_launcher"
                    android:label="@string/app_name" >
                </application>

            </manifest>
            """
        .trimIndent()

    val mergedManifest = temp.newFile("merged-manifest$suffix")
    Files.asCharSink(mergedManifest, Charsets.UTF_8).write(mergedManifestXml)

    @Language("XML")
    val baselineXml =
      """
            <issues format="4" by="lint unknown">
                <issue
                    id="DuplicateDefinition"
                    message="`string3` has already been defined in this folder"
                    errorLine1="    &lt;string name=&quot;string3&quot;>String 4&lt;/string>"
                    errorLine2="            ~~~~~~~~~~~~~~">
                    <location
                        file="res/values/strings.xml"
                        line="8"
                        column="13"/>
                    <location
                        file="res/values/strings.xml"
                        line="5"
                        column="13"/>
                </issue>
            </issues>
            """
        .trimIndent()
    val baseline = File(appProjectDir, "baseline.xml")
    Files.asCharSink(baseline, Charsets.UTF_8).write(baselineXml)

    @Language("XML")
    val descriptor =
      """
            <project>
            <root dir="$root" />
            <sdk dir='$sdk'/>
            <cache dir='$cacheDir'/>
            <classpath jar="test.jar" />
            <baseline file='$baseline' />
            <module name="$appProjectPath:App" android="true" library="false" compile-sdk-version='18'>
              <manifest file="AndroidManifest.xml" />
              <resource file="res/values/strings.xml" />
              <src file="test/Test.java" test="true" />
              <src file="generated/Generated.java" generated="true" />
              <dep module="Library" />
            </module>
            <module name="Library" android="true" library="true" compile-sdk-version='android-M'>
              <manifest file="Library/AndroidManifest.xml" />
              <merged-manifest file='$mergedManifest'/>
              <src file="Library/src/test/pkg/Loader.java" />
            </module>
            </project>
            """
        .trimIndent()
    Files.asCharSink(File(root, "project.xml"), Charsets.UTF_8).write(descriptor)

    var assertionsChecked = 0
    val listener: LintListener =
      object : LintListener {
        override fun update(
          driver: LintDriver,
          type: LintListener.EventType,
          project: Project?,
          context: Context?,
        ) {
          val client = driver.client
          when (type) {
            REGISTERED_PROJECT -> {
              assertThat(project).isNotNull()
              project!!
              assertThat(project.name).isEqualTo("$appProjectPath:App")
              assertThat(project.buildSdk).isEqualTo(18)
              assertionsChecked++

              // Lib project
              val libProject = project.directLibraries[0]
              assertThat(libProject.name).isEqualTo("Library")

              val manifest = client.getMergedManifest(libProject)
              assertThat(manifest).isNotNull()
              manifest!!
              val permission = getFirstSubTagByName(manifest.documentElement, "permission")!!
              assertThat(permission.getAttributeNS(ANDROID_URI, ATTR_NAME))
                .isEqualTo("foo.permission.SEND_SMS")
              assertionsChecked++

              // compileSdkVersion=android-M -> build API=23
              assertThat(libProject.buildSdk).isEqualTo(23)
              assertionsChecked++
            }
            STARTING -> {
              // Check extra metadata is handled right
              assertThat(client.getSdkHome()).isEqualTo(sdk)
              assertThat(client.getCacheDir(null, false)).isEqualTo(cacheDir)
              assertionsChecked += 2
            }
            else -> {
              // Ignored
            }
          }
        }
      }

    val canonicalRoot = root.canonicalPath

    // TODO: https://youtrack.jetbrains.com/issue/KT-57715
    val expectedError =
      if (useFirUast())
        "WARN: ROOT/test.jar: ROOT/test.jar\n" + "java.nio.file.NoSuchFileException: ROOT/test.jar"
      else "w: Classpath entry points to a non-existent location: ROOT/test.jar"

    MainTest.checkDriver(
      """
            baseline.xml: Information: 1 error was filtered out because it is listed in the baseline file, baseline.xml [LintBaseline]
            project.xml:5: Error: test.jar (relative to ROOT) does not exist [LintError]
            <classpath jar="test.jar" />
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            res/values/strings.xml:3: Error: string1 has already been defined in this folder [DuplicateDefinition]
                <string name="string1">String 2</string>
                        ~~~~~~~~~~~~~~
                res/values/strings.xml:2: Previously defined here
            generated/Generated.java:3: Warning: Do not hardcode "/sdcard/"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]
              String path = "/sdcard/file";
                            ~~~~~~~~~~~~~~
            ../Library/AndroidManifest.xml:8: Warning: Permission name SEND_SMS is not unique (appears in both foo.permission.SEND_SMS and bar.permission.SEND_SMS) [UniquePermission]
                <permission android:name="bar.permission.SEND_SMS"
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:8: Previous permission here
            2 errors, 2 warnings (1 error filtered by baseline baseline.xml)
            """,
      expectedError,

      // Expected exit code
      ERRNO_SUCCESS,

      // Args
      arrayOf(
        if (useFirUast()) "--XuseK2Uast" else "",
        "--check",
        "UniquePermission,DuplicateDefinition,SdCardPath",
        "--config",
        configFile.path,
        "--text",
        "stdout",
        "--project",
        File(root, "project.xml").path,
      ),
      {
        it
          .replace(canonicalRoot, "ROOT")
          .replace(root.path, "ROOT")
          .replace(baseline.parentFile.path, "TESTROOT")
          .dos2unix()
      },
      listener,
      null,
      false,
    )

    // Make sure we hit all our checks with the listener
    assertThat(assertionsChecked).isEqualTo(5)
  }

  @Test
  fun testManualProjectErrorHandling() {
    val root = temp.newFolder().canonicalFile.absoluteFile

    @Language("XML")
    val descriptor =
      """
            <project>
            <sdk dir='${TestUtils.getSdk()}'/>
            <module name="Foo:App" android="true" library="true" javaLanguage="1000" kotlinLanguage="1.3">
              <unknown file="foo.Bar" />
              <resource file="res/values/strings.xml" />
              <dep module="NonExistent" />
            </module>
            </project>
            """
        .trimIndent()
    val folder = File(root, "app")
    folder.mkdirs()
    val projectXml = File(folder, "project.xml")
    Files.asCharSink(projectXml, Charsets.UTF_8).write(descriptor)
    val sourceFile = File(folder, "src/main/java/com/example/Foo.java")
    sourceFile.parentFile.mkdirs()
    Files.asCharSink(sourceFile, Charsets.UTF_8)
      .write(
        """
                package com.example;

                public class Foo {}
            """
          .trimIndent()
      )

    MainTest.checkDriver(
      """
            app: Error: No .class files were found in project "Foo:App", so none of the classfile based checks could be run. Does the project need to be built first? [LintError]
            project.xml:3: Error: Invalid Java language level "1000" [LintError]
            <module name="Foo:App" android="true" library="true" javaLanguage="1000" kotlinLanguage="1.3">
            ^
            project.xml:4: Error: Unexpected tag unknown [LintError]
              <unknown file="foo.Bar" />
              ~~~~~~~~~~~~~~~~~~~~~~~~~~
            3 errors, 0 warnings
            """,
      "",
      ERRNO_SUCCESS,
      arrayOf("--project", projectXml.path),
      null,
      null,
    )
  }

  @Test
  fun testManualProjectErrorHandlingWithoutSourceFiles() {
    // Regression test for https://issuetracker.google.com/180408027
    val root = temp.newFolder().canonicalFile.absoluteFile

    @Language("XML")
    val descriptor =
      """
            <project>
            <sdk dir='${TestUtils.getSdk()}'/>
            <module name="Foo:App" android="true" library="true" javaLanguage="1000" kotlinLanguage="1.3">
              <unknown file="foo.Bar" />
              <resource file="res/values/strings.xml" />
              <dep module="NonExistent" />
            </module>
            </project>
            """
        .trimIndent()
    val folder = File(root, "app")
    folder.mkdirs()
    val projectXml = File(folder, "project.xml")
    Files.asCharSink(projectXml, Charsets.UTF_8).write(descriptor)

    MainTest.checkDriver(
      """
            project.xml:3: Error: Invalid Java language level "1000" [LintError]
            <module name="Foo:App" android="true" library="true" javaLanguage="1000" kotlinLanguage="1.3">
            ^
            project.xml:4: Error: Unexpected tag unknown [LintError]
              <unknown file="foo.Bar" />
              ~~~~~~~~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """,
      "",
      ERRNO_SUCCESS,
      arrayOf("--project", projectXml.path),
      null,
      null,
    )
  }

  @Test
  fun testSimpleProject() {
    val root = temp.newFolder().canonicalFile.absoluteFile
    val projects =
      lint()
        .files(
          java(
              "src/test/pkg/InterfaceMethodTest.java",
              """
                    package test.pkg;

                    @SuppressWarnings({"unused", "ClassNameDiffersFromFileName"})
                    public interface InterfaceMethodTest {
                        void someMethod();
                        default void method2() {
                            System.out.println("test");
                        }
                        static void method3() {
                            System.out.println("test");
                        }
                    }
                    """,
            )
            .indented(),
          java(
              "C.java",
              """
                    import android.app.Fragment;

                    @SuppressWarnings({"MethodMayBeStatic", "ClassNameDiffersFromFileName"})
                    public class C {
                      String path = "/sdcard/file";
                      void test(Fragment fragment) {
                        Object host = fragment.getHost(); // Requires API 23
                      }
                    }""",
            )
            .indented(),
          manifest(
              """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.android.tools.lint.test"
                    android:versionCode="1"
                    android:versionName="1.0" >
                    <uses-sdk
                        android:minSdkVersion="15"
                        android:targetSdkVersion="22" />

                </manifest>"""
            )
            .indented(),
          xml(
              "res/values/not_in_project.xml",
              """
                <resources>
                    <string name="string2">String 1</string>
                    <string name="string2">String 2</string>
                </resources>
                """,
            )
            .indented(),
        )
        .createProjects(root)
    val projectDir = projects[0]

    @Language("XML")
    val descriptor =
      """
            <project incomplete="true">
            <sdk dir='${TestUtils.getSdk()}'/>
            <root dir="$projectDir"/>
            <module name="M" android="true" library="true">
                <manifest file="$projectDir/AndroidManifest.xml" />
                <src file="$projectDir/C.java" />
                <src file="$projectDir/src/test/pkg/InterfaceMethodTest.java" />
            </module>
            </project>
            """
        .trimIndent()
    val descriptorFile = File(root, "out1/out2/out3/project.xml")
    descriptorFile.parentFile?.mkdirs()
    Files.asCharSink(descriptorFile, Charsets.UTF_8).write(descriptor)

    MainTest.checkDriver(
      """
            C.java:7: Error: Call requires API level 23 (current min is 15): android.app.Fragment#getHost [NewApi]
                Object host = fragment.getHost(); // Requires API 23
                                       ~~~~~~~
            C.java:5: Warning: Do not hardcode "/sdcard/"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]
              String path = "/sdcard/file";
                            ~~~~~~~~~~~~~~
            AndroidManifest.xml:7: Error: Google Play requires that apps target API level 33 or higher. [ExpiredTargetSdkVersion]
                    android:targetSdkVersion="22" />
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            2 errors, 1 warnings
            """,
      "",

      // Expected exit code
      ERRNO_SUCCESS,

      // Args
      arrayOf("--project", descriptorFile.path),
      null,
      null,
    )
  }

  @Test
  fun testPaths() {
    // Regression test for https://issuetracker.google.com/159169803
    val root = temp.newFolder().canonicalFile.absoluteFile
    val projects =
      lint()
        .files(
          xml(
              "layout/AndroidManifest.xml",
              """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.android.tools.lint.test"
                    android:versionCode="1"
                    android:versionName="1.0" >
                    <uses-sdk
                        android:minSdkVersion="15"
                        android:targetSdkVersion="29" />

                </manifest>""",
            )
            .indented()
        )
        .createProjects(root)
    val projectDir = projects[0]

    @Language("XML")
    val descriptor =
      """
            <project>
            <sdk dir='${TestUtils.getSdk()}'/>
            <root dir="$projectDir" />
            <module name="M" android="true" library="true">
                <manifest file="layout/AndroidManifest.xml" />
            </module>
            </project>
            """
        .trimIndent()
    val descriptorFile = File(root, "project.xml")
    Files.asCharSink(descriptorFile, Charsets.UTF_8).write(descriptor)

    MainTest.checkDriver(
      "No issues found.",
      "",

      // Expected exit code
      ERRNO_SUCCESS,

      // Args
      arrayOf("--check", "RequiredSize", "--project", descriptorFile.path),
      null,
      null,
    )
  }

  @Test
  fun testGradleDetectorsFiring() { // Regression test for b/132992488
    val root = temp.newFolder().canonicalFile.absoluteFile
    val projects =
      lint()
        .files(
          java(
              "src/main/pkg/MainActivity.java",
              """
                    package pkg;

                    import android.app.Activity;
                    import android.os.Bundle;

                    public class MainActivity extends Activity {
                        @Override
                        public void onCreate(Bundle savedInstanceState) {
                            super.onCreate(savedInstanceState);
                        }
                    }
                    """,
            )
            .indented(),
          manifest(
              """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.android.tools.lint.test"
                    android:versionCode="1"
                    android:versionName="1.0" >
                    <uses-sdk
                        android:minSdkVersion="15"
                        android:targetSdkVersion="22" />

                </manifest>"""
            )
            .indented(),
          xml(
              "res/values/strings.xml",
              """
                <resources xmlns:tools="http://schemas.android.com/tools">
                    <string name="nam${'\ufeff'}e">Value</string>
                </resources>""",
            )
            .indented(),
          bytes("res/raw/sample.txt", "a\uFEFFb".toByteArray()),
        )
        .createProjects(root)
    val projectDir = projects[0]

    @Language("XML")
    val descriptor =
      """
            <project incomplete="true">
            <sdk dir='${TestUtils.getSdk()}'/>
            <root dir="$projectDir" />
            <module name="M" android="true" library="true">
                <manifest file="AndroidManifest.xml" />
                <resource file="res/raw/sample.txt" />
                <resource file="res/values/strings.xml" />
                <src file="src/main/pkg/MainActivity.java" />
            </module>
            </project>
            """
        .trimIndent()
    val descriptorFile = File(root, "project.xml")
    Files.asCharSink(descriptorFile, Charsets.UTF_8).write(descriptor)

    MainTest.checkDriver(
      """
            res/values/strings.xml:2: Error: Found byte-order-mark in the middle of a file [ByteOrderMark]
                <string name="nam﻿e">Value</string>
                                 ~
            1 errors, 0 warnings
            """,
      "",

      // Expected exit code
      ERRNO_SUCCESS,

      // Args
      arrayOf("--check", "ByteOrderMark", "--project", descriptorFile.path),
      null,
      null,
    )
  }

  @Test
  fun testAar() {
    // Check for missing application icon and have that missing icon be supplied by
    // an AAR dependency and make its way into the merged manifest.
    val root = temp.newFolder().canonicalFile.absoluteFile
    val projects =
      lint()
        .files(
          manifest(
              """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="com.android.tools.lint.test"
                        android:versionCode="1"
                        android:versionName="1.0" >
                        <uses-sdk android:minSdkVersion="14" />
                        <application />

                    </manifest>"""
            )
            .indented(),
          xml(
              "res/values/not_in_project.xml",
              """
                    <resources>
                        <string name="string2">String 1</string>
                        <string name="string2">String 2</string>
                    </resources>
                    """,
            )
            .indented(),
          java(
              "src/main/java/test/pkg/Private.java",
              """package test.pkg;
                    @SuppressWarnings("ClassNameDiffersFromFileName")
                    public class Private {
                        void test() {
                            int x = R.string.my_private_string; // ERROR
                            int y = R.string.my_public_string; // OK
                        }
                    }
                    """,
            )
            .indented(),
        )
        .createProjects(root)
    val projectDir = projects[0]

    val aarFile = temp.newFile("foo-bar.aar")
    aarFile.createNewFile()
    val aar = temp.newFolder("aar-exploded")
    @Language("XML")
    val aarManifest =
      """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="com.android.tools.lint.test"
                        android:versionCode="1"
                        android:versionName="1.0" >

                        <uses-sdk android:minSdkVersion="14" />
                        <application android:icon='@mipmap/my_application_icon'/>

                    </manifest>"""
    Files.asCharSink(File(aar, "AndroidManifest.xml"), Charsets.UTF_8).write(aarManifest)

    val allResources =
      ("" +
        "int string my_private_string 0x7f040000\n" +
        "int string my_public_string 0x7f040001\n" +
        "int layout my_private_layout 0x7f040002\n" +
        "int id title 0x7f040003\n" +
        "int style Theme_AppCompat_DayNight 0x7f070004")

    val rFile = File(aar, FN_RESOURCE_TEXT)
    Files.asCharSink(rFile, Charsets.UTF_8).write(allResources)

    val publicResources =
      ("" + "" + "string my_public_string\n" + "style Theme.AppCompat.DayNight\n")

    val publicTxtFile = File(aar, FN_PUBLIC_TXT)
    Files.asCharSink(publicTxtFile, Charsets.UTF_8).write(publicResources)

    @Language("XML")
    val descriptor =
      """
            <project>
            <sdk dir='${TestUtils.getSdk()}'/>
            <root dir="$projectDir" />
                <module name="M" android="true" library="false">
                <manifest file="AndroidManifest.xml" />
                <src file="src/main/java/test/pkg/Private.java" />
                <aar file="$aarFile" extracted="$aar" />
            </module>
            </project>
            """
        .trimIndent()
    val descriptorFile = File(root, "project.xml")
    Files.asCharSink(descriptorFile, Charsets.UTF_8).write(descriptor)

    MainTest.checkDriver(
      "" +
        "src/main/java/test/pkg/Private.java:5: Warning: The resource @string/my_private_string is marked as private in foo-bar.aar [PrivateResource]\n" +
        "                            int x = R.string.my_private_string; // ERROR\n" +
        "                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "0 errors, 1 warnings\n",
      "",

      // Expected exit code
      ERRNO_SUCCESS,

      // Args
      arrayOf(
        "--check",
        "MissingApplicationIcon,PrivateResource",
        "--project",
        descriptorFile.path,
      ),
      { it.dos2unix() },
      null,
    )
  }

  @Test
  fun testJar() {
    // Check for missing application icon and have that missing icon be supplied by
    // an AAR dependency and make its way into the merged manifest.
    val root = temp.newFolder().canonicalFile.absoluteFile
    val projects =
      lint()
        .files(
          java(
              "src/test/pkg/Child.java",
              """
                package test.pkg;

                import android.os.Parcel;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class Child extends Parent {
                    @Override
                    public int describeContents() {
                        return 0;
                    }

                    @Override
                    public void writeToParcel(Parcel dest, int flags) {

                    }
                }
                """,
            )
            .indented()
        )
        .createProjects(root)
    val projectDir = projects[0]

    /*
    Compiled from
        package test.pkg;
        import android.os.Parcelable;
        public abstract class Parent implements Parcelable {
        }
     */
    val jarFile =
      jar(
          "parent.jar",
          base64gzip(
            "test/pkg/Parent.class",
            "" +
              "H4sIAAAAAAAAAF1Pu07DQBCcTRw7cQx5SHwAXaDgipQgmkhUFkRKlP5sn8IF" +
              "cxedL/wXFRJFPoCPQuw5qdBKo53Z2R3tz+/3EcAc0xRdXCYYJRgnmBDiB220" +
              "fyR0ZzcbQrSwlSKMcm3U8+G9UG4ti5qVaW5LWW+k04Gfxci/6oYwyb1qvNi/" +
              "bcVSOmX8PSFd2YMr1ZMOvuFJvtvJD5mhh5gT/q0QxmEqamm24qXYqZKlK2kq" +
              "Z3UlbBNspapDbnSNDn/B8fwScfFBxoSZaDnQu/0CfXLTQZ8xPokYMGbnPsWw" +
              "Xc9a18UfxkO3QyIBAAA=",
          ),
        )
        .createFile(root)

    @Language("XML")
    val descriptor =
      """
            <project>
            <sdk dir='${TestUtils.getSdk()}'/>
            <root dir="$projectDir" />
                <module name="M" android="true" library="false">
                <jar file="$jarFile" />
                <src file="src/test/pkg/Child.java" />
            </module>
            </project>
            """
        .trimIndent()
    val descriptorFile = File(root, "project.xml")
    Files.asCharSink(descriptorFile, Charsets.UTF_8).write(descriptor)

    MainTest.checkDriver(
      "" +
        // We only find this error if we correctly include the jar dependency
        // which provides the parent class which implements Parcelable.
        "src/test/pkg/Child.java:6: Error: This class implements Parcelable but does not provide a CREATOR field [ParcelCreator]\n"
          .replace('/', File.separatorChar) +
        "public class Child extends Parent {\n" +
        "             ~~~~~\n" +
        "1 errors, 0 warnings\n",
      "",

      // Expected exit code
      ERRNO_SUCCESS,

      // Args
      arrayOf("--check", "ParcelCreator", "--project", descriptorFile.path),
      null,
      null,
    )
  }

  @Test
  fun testClasspathJar() {
    // Ensure that class path jars are properly included for type resolution
    val root = temp.newFolder().canonicalFile.absoluteFile

    val projects =
      lint()
        .files(
          java(
              """
                    package test.pkg;

                    import androidx.annotation.RequiresApi;
                    import android.util.Log;

                    @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                    public class RequiresApiFieldTest {
                        @RequiresApi(24)
                        private int Method24() {
                            return 42;
                        }

                        private void ReferenceMethod24() {
                            Log.d("zzzz", "ReferenceField24: " + Method24());
                        }
                    }
                    """
            )
            .indented(),
          SUPPORT_ANNOTATIONS_JAR,
          xml(
              "project.xml",
              """
            <project>
            <sdk dir='${TestUtils.getSdk()}'/>
            <module name="M" android="true" library="false">
            <classpath jar="libs/support-annotations.jar" />
            <src file="src/test/pkg/RequiresApiFieldTest.java" />
            </module>
            </project>
            """,
            )
            .indented(),
        )
        .createProjects(root)
    val projectDir = projects[0]
    val descriptorFile = File(projectDir, "project.xml")

    MainTest.checkDriver(
      "" +
        // We only find this error if we correctly include the jar dependency
        // which provides the parent class which implements Parcelable.
        "src/test/pkg/RequiresApiFieldTest.java:14: Error: Call requires API level 24 (current min is 1): Method24 [NewApi]\n" +
        "        Log.d(\"zzzz\", \"ReferenceField24: \" + Method24());\n" +
        "                                             ~~~~~~~~\n" +
        "1 errors, 0 warnings\n",
      "",

      // Expected exit code
      ERRNO_SUCCESS,

      // Args
      arrayOf("--check", "NewApi", "--project", descriptorFile.path),
      { it.dos2unix() },
      null,
    )
  }

  @Test
  fun testNonAndroidProject() {
    val root = temp.newFolder().canonicalFile.absoluteFile
    val projects =
      lint()
        .files(
          java(
              "C.java",
              """
                    @SuppressWarnings({"MethodMayBeStatic", "ClassNameDiffersFromFileName"})
                    public class C {
                      String path = "/sdcard/file";
                    }""",
            )
            .indented()
        )
        .createProjects(root)
    val projectDir = projects[0]

    @Language("XML")
    val descriptor =
      """
            <project incomplete="true">
            <sdk dir='${TestUtils.getSdk()}'/>
            <root dir="$projectDir" />
            <module name="M" android="false" library="true">
                <src file="C.java" />
            </module>
            </project>
            """
        .trimIndent()
    val descriptorFile = File(root, "project.xml")
    Files.asCharSink(descriptorFile, Charsets.UTF_8).write(descriptor)

    MainTest.checkDriver(
      "No issues found.",
      "",

      // Expected exit code
      ERRNO_SUCCESS,

      // Args
      arrayOf("--project", descriptorFile.path),
      null,
      null,
    )
  }

  @Test
  fun testJava8Libraries() {
    val root = temp.newFolder().canonicalFile.absoluteFile
    val projects =
      lint()
        .files(
          java(
              "C.java",
              """
                    import java.util.ArrayList;
                    import java.util.Arrays;
                    import java.util.Iterator;
                    import java.util.stream.Stream;

                    @SuppressWarnings({"unused", "SimplifyStreamApiCallChains",
                        "OptionalGetWithoutIsPresent", "OptionalUsedAsFieldOrParameterType",
                        "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                    public class C {
                        public void utils(java.util.Collection<String> collection) {
                            collection.removeIf(s -> s.length() > 5);
                        }

                        public void streams(ArrayList<String> list, String[] array) {
                            list.stream().forEach(s -> System.out.println(s.length()));
                            Stream<String> stream = Arrays.stream(array);
                        }

                        public void bannedMembers(java.util.Collection collection) {
                            Stream stream = collection.parallelStream(); // ERROR
                        }
                    }
                    """,
            )
            .indented()
        )
        .createProjects(root)
    val projectDir = projects[0]

    @Language("XML")
    val descriptor =
      """
            <project>
            <sdk dir='${TestUtils.getSdk()}'/>
            <root dir="$projectDir" />
            <!-- We could have specified desugar="full" instead of specifying android_java8_libs -->
            <module desugar="default" android_java8_libs="true" name="M" android="true" library="false">
                <src file="C.java" />
            </module>
            </project>
            """
        .trimIndent()
    val descriptorFile = File(root, "project.xml")
    Files.asCharSink(descriptorFile, Charsets.UTF_8).write(descriptor)

    MainTest.checkDriver(
      """
            C.java:20: Error: Call requires API level 24 (current min is 1): java.util.Collection#parallelStream [NewApi]
                    Stream stream = collection.parallelStream(); // ERROR
                                               ~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """,
      "",

      // Expected exit code
      ERRNO_SUCCESS,

      // Args
      arrayOf("--check", "NewApi", "--project", descriptorFile.path),
      null,
      null,
    )
  }

  @Test
  fun testExternalAnnotations() {
    // Checks that external annotations support works
    val root = temp.newFolder().canonicalFile.absoluteFile

    val projects =
      lint()
        .projects(
          project(
              java(
                  """
                    package test.pkg;
                    import androidx.annotation.WorkerThread;

                    public class Client {
                        @WorkerThread
                        public static void client() {
                            new test.pkg1.Library1().method1();
                            new test.pkg2.Library2().method2();
                        }
                    }
                    """
                )
                .indented(),
              java(
                  """
                package test.pkg1;

                public class Library1 {
                    public void method1() { // externally annotated as @UiThread
                    }
                }
                """
                )
                .indented(),
              java(
                  """
                package test.pkg2;

                public class Library2 {
                    public void method2() { // externally annotated as @UiThread
                    }
                }
                """
                )
                .indented(),
              SUPPORT_ANNOTATIONS_JAR,
              // zip annotations file
              jar(
                "annotations.zip",
                xml(
                    "test/pkg1/annotations.xml",
                    """
                    <root>
                      <item name="test.pkg1.Library1 void method1()">
                        <annotation name="androidx.annotation.UiThread"/>
                      </item>
                    </root>
                    """,
                  )
                  .indented(),
              ),
              // dir annotation files
              xml(
                  "external-annotations/test/pkg2/annotations.xml",
                  """
                <root>
                  <item name="test.pkg2.Library2 void method2()">
                    <annotation name="androidx.annotation.UiThread"/>
                  </item>
                </root>
                """,
                )
                .indented(),
              xml(
                  "project.xml",
                  """
                <project>
                <root dir="$root/project" />
                <sdk dir='${TestUtils.getSdk()}'/>
                <annotations file="annotations.zip"/>
                <annotations dir="external-annotations"/>
                <module name="M" android="true" library="false">
                <classpath jar="libs/support-annotations.jar" />
                <src file="src/test/pkg1/Library1.java" />
                <src file="src/test/pkg2/Library2.java" />
                <src file="src/test/pkg/Client.java" />
                </module>
                </project>
            """,
                )
                .indented(),
            )
            .name("project")
        )
        .createProjects(root)
    val projectDir = projects[0]
    val descriptorFile = File(projectDir, "project.xml")

    MainTest.checkDriver(
      "" +
        "src/test/pkg/Client.java:7: Error: Method method1 must be called from the UI thread, currently inferred thread is worker thread [WrongThread]\n" +
        "        new test.pkg1.Library1().method1();\n" +
        "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "src/test/pkg/Client.java:8: Error: Method method2 must be called from the UI thread, currently inferred thread is worker thread [WrongThread]\n" +
        "        new test.pkg2.Library2().method2();\n" +
        "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "2 errors, 0 warnings",
      "",

      // Expected exit code
      ERRNO_SUCCESS,

      // Args
      arrayOf("--check", "WrongThread", "--project", descriptorFile.path),
      { it.dos2unix() },
      null,
    )
  }

  @Test
  fun testJava14() {
    // Tests Java language support for some recent features, such as
    // switch expressions
    val root = temp.newFolder().canonicalFile.absoluteFile

    val projects =
      lint()
        .projects(
          project(
              java(
                  """
                package test.pkg;
                import androidx.annotation.IntDef;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                public class Java14Test {
                    @IntDef({LENGTH_INDEFINITE, LENGTH_SHORT, LENGTH_LONG})
                    @Retention(RetentionPolicy.SOURCE)
                    public @interface Duration {
                    }
                    public static final int LENGTH_INDEFINITE = -2;
                    public static final int LENGTH_SHORT = -1;
                    public static final int LENGTH_LONG = 0;

                    // Switch expression -- missing one of the cases; should generate warning
                    // when we're correctly handling the AST for this
                    public static boolean test(@Duration int duration) {
                        return switch (duration) {
                            // Missing LENGTH_INDEFINITE handling
                            case LENGTH_SHORT, LENGTH_LONG -> true;
                            default -> false;
                        };
                    }
                }
                """
                )
                .indented(),
              SUPPORT_ANNOTATIONS_JAR,
              xml(
                  "project.xml",
                  """
                <project>
                <root dir="$root/project" />
                <sdk dir='${TestUtils.getSdk()}'/>
                <module name="M" android="true" library="false" javaLanguage="14">
                <classpath jar="libs/support-annotations.jar" />
                <src file="src/test/pkg/Java14Test.java" />
                </module>
                </project>
            """,
                )
                .indented(),
            )
            .name("project")
        )
        .createProjects(root)
    val projectDir = projects[0]
    val descriptorFile = File(projectDir, "project.xml")

    MainTest.checkDriver(
      """
            src/test/pkg/Java14Test.java:17: Warning: Switch statement on an int with known associated constant missing case LENGTH_INDEFINITE [SwitchIntDef]
                    return switch (duration) {
                           ^
            0 errors, 1 warnings
            """,
      "",

      // Expected exit code
      ERRNO_SUCCESS,

      // Args
      arrayOf("--check", "SwitchIntDef", "--project", descriptorFile.path),
      { it.dos2unix() },
      null,
    )
  }

  @Test
  fun testCrLf() {
    // Regression test for bug handling Windows line endings,
    // https://issuetracker.google.com/149490356
    val root = temp.newFolder().canonicalFile.absoluteFile
    val crlf = File(root, "app/src/main/java/ClassCRLF.java")
    crlf.parentFile.mkdirs()
    crlf.writeText(
      """
            package com.example.foo.notification;

            public class AppNotifBlockedReceiver extends BroadcastReceiver {
                // content removed
            }
            """
        .trimIndent()
        .replace("\n", "\r\n")
    )
    val lf = File(root, "app/src/main/java/ClassLF.java")
    lf.writeText(
      """
            package com.example.foo.tester.ui;

            public class DisableActivity extends Activity {
            // Content removed
            }
            """
        .trimIndent()
    )

    @Language("XML")
    val descriptor =
      """
            <project>
               <module android="true" compile-sdk-version="18" name="app">
                  <src file="app/src/main/java/ClassCRLF.java"/>
                  <src file="app/src/main/java/ClassLF.java"/>
               </module>
            </project>
            """
        .trimIndent()
    val descriptorFile = File(root, "descriptor.xml")
    descriptorFile.writeText(descriptor.replace("\n", "\r\n"))

    MainTest.checkDriver(
      "No issues found.",
      "The source file ClassCRLF.java does not appear to be in the right project location; its package implies .../com/example/foo/notification/ClassCRLF.java but it was found in ...src/main/java/ClassCRLF.java",

      // Expected exit code
      ERRNO_SUCCESS,

      // Args
      arrayOf("--project", descriptorFile.path),
      { it.dos2unix() },
      null,
    )
  }

  @Test
  fun testInvalidDescriptorFile() {
    // Make sure we give a suitable error message when you pass in a directory instead of
    // an XML file
    val root = temp.newFolder().canonicalFile.absoluteFile

    MainTest.checkDriver(
      "",
      "Project descriptor ROOT should be an XML descriptor file, not a directory",

      // Expected exit code
      ERRNO_INVALID_ARGS,

      // Args
      arrayOf("--project", root.path),
      { it.replace(root.path, "ROOT") },
      null,
    )
  }

  @Test
  fun testLintXmlOutside() {

    val library =
      project(
          manifest(
              """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="foo.bar2"
                    android:versionCode="1"
                    android:versionName="1.0" >

                    <uses-sdk android:minSdkVersion="14" />

                    <permission android:name="bar.permission.SEND_SMS"
                        android:label="@string/foo"
                        android:description="@string/foo" />

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                    </application>

                </manifest>"""
            )
            .indented()
        )
        .type(LIBRARY)
        .name("Library")

    val main =
      project(
          manifest(
              """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="foo.bar2"
                    android:versionCode="1"
                    android:versionName="1.0" >

                    <uses-sdk android:minSdkVersion="14" />

                    <permission android:name="foo.permission.SEND_SMS"
                        android:label="@string/foo"
                        android:description="@string/foo" />

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                    </application>

                </manifest>
                """
            )
            .indented()
        )
        .name("App")
        .dependsOn(library)

    val root = temp.newFolder().canonicalFile.absoluteFile

    val configFile = File(root, "foobar/lint.xml")
    @Language("XML")
    val config =
      """
            <lint>
                <!-- Reduce severity of UniquePermission from error to warning -->
                <issue id="UniquePermission" severity="warning"/>
            </lint>
            """
    configFile.parentFile?.mkdirs()
    configFile.writeText(config.trimIndent())

    val projects = lint().projects(main, library).createProjects(root)
    // create projects will sort directories in dependency order so
    // the app module comes after lib even though it's the second listed
    // project
    val appProjectDir = projects[1]
    assertEquals("App", appProjectDir.name)
    val appProjectPath = appProjectDir.path

    val sdk = temp.newFolder("fake-sdk-dir")
    val cacheDir = temp.newFolder("cache-dir")

    @Language("XML")
    val descriptor =
      """
            <project>
            <root dir="$root" />
            <sdk dir='$sdk'/>
            <cache dir='$cacheDir'/>
            <module name="$appProjectPath:App" android="true" library="false" compile-sdk-version='18'>
              <manifest file="AndroidManifest.xml" />
              <dep module="Library" />
            </module>
            <module name="Library" android="true" library="true" compile-sdk-version='android-M'>
              <manifest file="Library/AndroidManifest.xml" />
            </module>
            </project>
            """
        .trimIndent()
    Files.asCharSink(File(root, "project.xml"), Charsets.UTF_8).write(descriptor)

    val canonicalRoot = root.canonicalPath
    MainTest.checkDriver(
      """
            ../Library/AndroidManifest.xml:8: Warning: Permission name SEND_SMS is not unique (appears in both foo.permission.SEND_SMS and bar.permission.SEND_SMS) [UniquePermission]
                <permission android:name="bar.permission.SEND_SMS"
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:8: Previous permission here
            0 errors, 1 warnings
            """,
      "",

      // Expected exit code
      ERRNO_SUCCESS,

      // Args
      arrayOf(
        "--check",
        "UniquePermission",
        "--config",
        configFile.path,
        "--text",
        "stdout",
        "--project",
        File(root, "project.xml").path,
      ),
      { it.replace(canonicalRoot, "ROOT").replace(root.path, "ROOT").dos2unix() },
      null,
    )

    val newConfigFile = File(root, "default.xml")
    configFile.renameTo(newConfigFile)
    MainTest.checkDriver(
      """
            ../Library/AndroidManifest.xml:8: Warning: Permission name SEND_SMS is not unique (appears in both foo.permission.SEND_SMS and bar.permission.SEND_SMS) [UniquePermission]
                <permission android:name="bar.permission.SEND_SMS"
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:8: Previous permission here
            0 errors, 1 warnings
            """,
      "",

      // Expected exit code
      ERRNO_SUCCESS,

      // Args
      arrayOf(
        "--check",
        "UniquePermission",
        "--config",
        newConfigFile.path,
        "--text",
        "stdout",
        "--project",
        File(root, "project.xml").path,
      ),
      { it.replace(canonicalRoot, "ROOT").replace(root.path, "ROOT").dos2unix() },
      null,
    )
  }

  @Test
  fun testManifestInFolderNamedLayout() {
    // Regression test for b/214409371:
    // Make sure we don't accidentally interpret something in a folder named "layout"
    // ...and make sure that manifests *not* named AndroidManifestXml
    val root = temp.newFolder().canonicalFile.absoluteFile
    val projects =
      lint()
        .files(
          xml(
              "layout/SomethingNamedAndroidManifest.xml",
              """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.android.tools.lint.test"
                    android:versionCode="1"
                    android:versionName="1.0" >
                    <uses-sdk android:minSdkVersion="10" android:targetSdkVersion="31" />
                    <uses-sdk android:minSdkVersion="10" android:targetSdkVersion="31" />
                </manifest>
                """,
            )
            .indented(),
          xml(
              "res/layout/layout.xml",
              """
                <merge/>
                """,
            )
            .indented(),
        )
        .createProjects(root)
    val projectDir = projects[0]

    @Language("XML")
    val descriptor =
      """
            <project incomplete="false">
            <sdk dir='${TestUtils.getSdk()}'/>
            <root dir="$projectDir"/>
            <module name="M" android="true" library="true">
                <manifest file="layout/SomethingNamedAndroidManifest.xml" />
                <resource file="res/layout/layout.xml" />
            </module>
            </project>
            """
        .trimIndent()
    val descriptorFile = File(root, "out1/out2/out3/project.xml")
    descriptorFile.parentFile?.mkdirs()
    Files.asCharSink(descriptorFile, Charsets.UTF_8).write(descriptor)

    MainTest.checkDriver(
      """
            layout/SomethingNamedAndroidManifest.xml:6: Error: There should only be a single <uses-sdk> element in the manifest: merge these together [MultipleUsesSdk]
                <uses-sdk android:minSdkVersion="10" android:targetSdkVersion="31" />
                 ~~~~~~~~
                layout/SomethingNamedAndroidManifest.xml:5: Also appears here
            1 errors, 0 warnings
            """,
      "Manifest merger failed with multiple errors, see logs",

      // Expected exit code
      ERRNO_SUCCESS,

      // Args
      arrayOf(
        "--check",
        "RequiredSize,ManifestOrder,ContentDescription,MultipleUsesSdk",
        "--project",
        descriptorFile.path,
      ),
      null,
      null,
    )
  }

  @Test
  fun testFindPackage() {
    assertEquals("foo.bar", findPackage("package foo.bar;\n", File("Test.java")))
    assertEquals("foo.bar", findPackage("// Copyright 2021\npackage foo.bar;\n", File("Test.java")))
    assertEquals(
      "foo.bar",
      findPackage("// package wrong; /*\npackage  foo. bar ;\n", File("Test.java")),
    )
    assertEquals(
      "foo.bar",
      findPackage("/* package wrong; */\npackage  foo .bar ;\n", File("Test.java")),
    )
    assertEquals(
      "foo.bar",
      findPackage("/* /* nested comment */ package wrong */\npackage foo.bar \n", File("x.kt")),
    )
    // Regression test for 195004772
    @Language("java")
    val source =
      """
            // Copyright 2007, Google Inc.
            /** The classes in this is package provide a variety of utility services. */
            @CheckReturnValue
            @ParametersAreNonnullByDefault
            @NullMarked
            package com.google.common.util;

            import javax.annotation.CheckReturnValue;
            import javax.annotation.ParametersAreNonnullByDefault;
            import org.jspecify.nullness.NullMarked;
            """
        .trimIndent()
    assertEquals("com.google.common.util", findPackage(source, File("package-info.java")))
  }

  @Test
  fun testIsolatedPartialAnalysisWithSingleProjectRoot() {
    // We simulate integration of partial analysis with a build system like Bazel. As such, this
    // test also acts as a guide for integrating lint with partial analysis support with a build
    // system like Bazel. The integration code would need to generate `project.xml` files and
    // invoke lint in a similar way to what is done below.
    //
    // We create 4 projects: `a`, `b`, `c`, and `onlyres`. We analyze each project in isolation,
    // propagating partial results to dependent targets. We use `--analyze-only` mode, then
    // `--report-only` mode on every project. We want to report definite issues for a target
    // immediately, while partial and provisional issues propagate to dependent targets. We use
    // `UnusedResources` to check that partial issues are reported correctly. We use
    // `LongLogTag` and `MissingSuperCall` to check that provisional and definite issues are
    // reported correctly. We use `ExactAlarm` to check that the project's targetSdkVersion is
    // correctly initialized from the merged manifest file. Each project has an unused resource,
    // as well as one resource that is used in each dependent project. Project `c` is the
    // app/binary project.
    //
    // Depends-on: `a` <- `b` <- `c` -> `onlyres`
    //              ^____________/

    val tempDir = temp.newFolder().canonicalFile.absoluteFile

    // We will check that various output files do NOT contain "buildRoot". In other words, there
    // should be no absolute paths in output files.
    val root = File(tempDir, "buildRoot")

    fun checkFilesDoNotContainBuildRoot(dir: File) {
      val badFiles =
        java.nio.file.Files.list(dir.toPath())
          .filter { it.isRegularFile() && it.readText(Charsets.UTF_8).contains("buildRoot") }
          .toList()
      assertTrue(
        "The following files contain the buildRoot directory, " +
          "which should not happen: ${badFiles.joinToString()}",
        badFiles.isEmpty(),
      )
    }

    fun createFile(path: String, content: String): File {
      val trimmedContent = content.trimIndent()
      val file = File(root, path)
      file.parentFile?.mkdirs()
      Files.asCharSink(file, Charsets.UTF_8).write(trimmedContent)
      return file
    }

    fun createXmlFile(path: String, @Language("XML") content: String): File =
      createFile(path, content)

    fun createJavaFile(path: String, @Language("JAVA") content: String): File =
      createFile(path, content)

    val configFile =
      createXmlFile(
        "configs/config.xml",
        """
            <lint>
                <issue id="all" severity="ignore" />
                <issue id="MissingClass" severity="error" />
                <issue id="UnusedResources" severity="error" />
                <issue id="LongLogTag" severity="error" />
                <issue id="MissingSuperCall" severity="error" />
                <issue id="ExactAlarm" severity="error" />
            </lint>""",
      )

    // Project a:
    createJavaFile(
      "java/com/google/a/Activity.java",
      """
            package com.google.a;

            import android.util.Log;

            public class Activity extends android.app.Activity {

                private static final String TAG = "SuperSuperLongLogTagThatExceedsMax";

                @Override
                protected void onStart() {
                    // Missing super call.
                    this.setTitle(R.string.a_string_used_in_a);
                    Log.d(TAG, "message");
                }
            }""",
    )
    createXmlFile(
      "java/com/google/a/AndroidManifest.xml",
      """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.google.a">

                <uses-sdk
                    android:minSdkVersion="14"
                    android:targetSdkVersion="28" />

                <application>
                    <activity
                        android:name="com.google.a.Activity"
                        android:exported="false" />
                </application>

            </manifest>""",
    )
    createXmlFile(
      "java/com/google/a/res/values/strings.xml",
      """
            <resources>
                <string name="a_string_used_in_a">a string used in a</string>
                <string name="a_string_used_in_b">a string used in b</string>
                <string name="a_string_used_in_c">a string used in c</string>
                <string name="a_string_unused">a string unused</string>
            </resources>""",
    )
    val projectA =
      createXmlFile(
        "out/java/com/google/a/project.xml",
        """
            <project>
            <root dir="$root" />
            <module
                android="true"
                library="true"
                name="//java/com/google/a:a"
                partial-results-dir="out/java/com/google/a/lint_partial_results"
                desugar="full">
            <manifest file="java/com/google/a/AndroidManifest.xml" />
            <merged-manifest file="java/com/google/a/AndroidManifest.xml" />
            <src file="java/com/google/a/Activity.java" />
            <resource file="java/com/google/a/res/values/strings.xml" />
            </module>
            </project>
            """,
      )
    File(root, "out/java/com/google/a/lint_partial_results").mkdirs()

    // Project b:
    createJavaFile(
      "java/com/google/b/Activity.java",
      """
            package com.google.b;

            import android.util.Log;

            public class Activity extends android.app.Activity {

                private static final String TAG = "SuperSuperLongLogTagThatExceedsMax";

                @Override
                protected void onStart() {
                    // Missing super call.
                    this.setTitle(com.google.a.R.string.a_string_used_in_b);
                    this.setTitle(R.string.b_string_used_in_b);
                    Log.d(TAG, "message");
                }
            }""",
    )
    createXmlFile(
      "java/com/google/b/AndroidManifest.xml",
      """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.google.b">

                <uses-sdk
                    android:minSdkVersion="14"
                    android:targetSdkVersion="28" />

                <application>
                    <activity
                        android:name="com.google.b.Activity"
                        android:exported="false" />
                </application>

            </manifest>""",
    )
    createXmlFile(
      "java/com/google/b/res/values/strings.xml",
      """
            <resources>
                <string name="b_string_used_in_b">b string used in b</string>
                <string name="b_string_used_in_c">b string used in c</string>
                <string name="b_string_unused">b string unused</string>
            </resources>""",
    )
    val projectB =
      createXmlFile(
        "out/java/com/google/b/project.xml",
        """
            <project>
            <root dir="$root" />
            <module
                android="true"
                library="true"
                name="//java/com/google/b:b"
                partial-results-dir="out/java/com/google/b/lint_partial_results"
                desugar="full">
            <manifest file="java/com/google/b/AndroidManifest.xml" />
            <merged-manifest file="java/com/google/b/AndroidManifest.xml" />
            <src file="java/com/google/b/Activity.java" />
            <resource file="java/com/google/b/res/values/strings.xml" />
            <dep module="//java/com/google/a:a" />
            </module>
            <module android="true"
                library="true"
                name="//java/com/google/a:a"
                desugar="full"
                partial-results-dir="out/java/com/google/a/lint_partial_results" />
            </project>
            """,
      )
    File(root, "out/java/com/google/b/lint_partial_results").mkdirs()

    // Project onlyres:
    createXmlFile(
      "java/com/google/onlyres/AndroidManifest.xml",
      """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.google.onlyres">

                <uses-sdk
                    android:minSdkVersion="14"
                    android:targetSdkVersion="28" />

            </manifest>""",
    )
    createXmlFile(
      "java/com/google/onlyres/res/values/strings.xml",
      """
            <resources>
                <string name="onlyres_string_used_in_c">onlyres string used in c</string>
                <string name="onlyres_string_unused">onlyres string unused</string>
            </resources>""",
    )
    val projectOnlyRes =
      createXmlFile(
        "out/java/com/google/onlyres/project.xml",
        """
            <project>
            <root dir="$root" />
            <module
                android="true"
                library="true"
                name="//java/com/google/onlyres:onlyres"
                partial-results-dir="out/java/com/google/onlyres/lint_partial_results"
                desugar="full">
            <manifest file="java/com/google/onlyres/AndroidManifest.xml" />
            <merged-manifest file="java/com/google/onlyres/AndroidManifest.xml" />
            <resource file="java/com/google/onlyres/res/values/strings.xml" />
            </module>
            </project>
            """,
      )
    File(root, "out/java/com/google/onlyres/lint_partial_results").mkdirs()

    // Project c (the app):
    createJavaFile(
      "java/com/google/c/Activity.java",
      """
            package com.google.c;

            import android.util.Log;

            public class Activity extends android.app.Activity {

                private static final String TAG = "SuperSuperLongLogTagThatExceedsMax";

                @Override
                protected void onStart() {
                    // Missing super call.
                    this.setTitle(com.google.a.R.string.a_string_used_in_c);
                    this.setTitle(com.google.b.R.string.b_string_used_in_c);
                    this.setTitle(com.google.onlyres.R.string.onlyres_string_used_in_c);
                    this.setTitle(R.string.c_string_used_in_c);
                    Log.d(TAG, "message");
                }
            }""",
    )
    createXmlFile(
      "java/com/google/c/AndroidManifest.xml",
      """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.google.c">

                <uses-sdk
                    android:minSdkVersion="14"
                    android:targetSdkVersion="33" />

                <!-- This permission will trigger an error if the targetSdkVersion fails to be read -->
                <uses-permission android:name="android.permission.USE_EXACT_ALARM" />

                <application>
                    <activity
                        android:name="com.google.c.Activity"
                        android:exported="true" />
                    <action android:name="android.intent.action.MAIN" />
                    <category android:name="android.intent.category.LAUNCHER" />
                </application>

            </manifest>""",
    )
    createXmlFile(
      "java/com/google/c/res/values/strings.xml",
      """
            <resources>
                <string name="c_string_used_in_c">c string used in c</string>
                <string name="c_string_unused">c string unused</string>
            </resources>""",
    )
    createXmlFile(
      "out/java/com/google/c/AndroidManifestMerged.xml",
      """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.google.c">

                <uses-sdk
                    android:minSdkVersion="14"
                    android:targetSdkVersion="33" />

                <!-- This permission will trigger an error if the targetSdkVersion fails to be read -->
                <uses-permission android:name="android.permission.USE_EXACT_ALARM" />

                <application>
                    <activity
                        android:name="com.google.a.Activity"
                        android:exported="false" />
                    <activity
                        android:name="com.google.b.Activity"
                        android:exported="false" />
                    <activity
                        android:name="com.google.c.Activity"
                        android:exported="true" />
                    <action android:name="android.intent.action.MAIN" />
                    <category android:name="android.intent.category.LAUNCHER" />
                </application>

            </manifest>""",
    )
    val projectC =
      createXmlFile(
        "out/java/com/google/c/project.xml",
        """
            <project>
            <root dir="$root" />
            <module
                android="true"
                library="false"
                name="//java/com/google/c:c"
                partial-results-dir="out/java/com/google/c/lint_partial_results"
                desugar="full">
            <manifest file="java/com/google/c/AndroidManifest.xml" />
            <merged-manifest file="out/java/com/google/c/AndroidManifestMerged.xml" />
            <src file="java/com/google/c/Activity.java" />
            <resource file="java/com/google/c/res/values/strings.xml" />
            <dep module="//java/com/google/a:a" />
            <dep module="//java/com/google/b:b" />
            <dep module="//java/com/google/onlyres:onlyres" />
            </module>
            <module
                android="true"
                library="true"
                name="//java/com/google/a:a"
                desugar="full"
                partial-results-dir="out/java/com/google/a/lint_partial_results" />
            <module
                android="true"
                library="true"
                name="//java/com/google/b:b"
                desugar="full"
                partial-results-dir="out/java/com/google/b/lint_partial_results" />
            <module
                android="true"
                library="true"
                name="//java/com/google/onlyres:onlyres"
                desugar="full"
                partial-results-dir="out/java/com/google/onlyres/lint_partial_results" />
            </project>
            """,
      )
    File(root, "out/java/com/google/c/lint_partial_results").mkdirs()

    // Analyze project a.
    MainTest.checkDriver(
      "",
      "",
      // Expected exit code
      ERRNO_SUCCESS,
      // Args
      arrayOf(
        "--config",
        configFile.toString(),
        "--project",
        projectA.toString(),
        "--analyze-only",
        "--sdk-home",
        TestUtils.getSdk().toString(),
      ),
      null,
      null,
    )

    MainTest.checkDriver(
      """
                java/com/google/a/Activity.java:10: Error: Overriding method should call super.onStart [MissingSuperCall]
                    protected void onStart() {
                                   ~~~~~~~
                java/com/google/a/Activity.java:13: Error: The logging tag can be at most 23 characters, was 34 (SuperSuperLongLogTagThatExceedsMax) [LongLogTag]
                        Log.d(TAG, "message");
                              ~~~
                2 errors, 0 warnings""",
      "",
      // Expected exit code
      ERRNO_SUCCESS,
      // Args
      arrayOf(
        "--config",
        configFile.toString(),
        "--project",
        projectA.toString(),
        "--report-only",
        "--sdk-home",
        TestUtils.getSdk().toString(),
      ),
      null,
      null,
    )
    checkFilesDoNotContainBuildRoot(File(root, "out/java/com/google/a/lint_partial_results"))

    // Delete definite issues, as these have definitely already been reported.
    File(root, "out/java/com/google/a/lint_partial_results/lint-definite.xml").delete()

    // Analyze project b.
    MainTest.checkDriver(
      "",
      "",
      // Expected exit code
      ERRNO_SUCCESS,
      // Args
      arrayOf(
        "--config",
        configFile.toString(),
        "--project",
        projectB.toString(),
        "--analyze-only",
        "--sdk-home",
        TestUtils.getSdk().toString(),
      ),
      null,
      null,
    )

    MainTest.checkDriver(
      """
                java/com/google/b/Activity.java:10: Error: Overriding method should call super.onStart [MissingSuperCall]
                    protected void onStart() {
                                   ~~~~~~~
                java/com/google/a/Activity.java:13: Error: The logging tag can be at most 23 characters, was 34 (SuperSuperLongLogTagThatExceedsMax) [LongLogTag]
                        Log.d(TAG, "message");
                              ~~~
                java/com/google/b/Activity.java:14: Error: The logging tag can be at most 23 characters, was 34 (SuperSuperLongLogTagThatExceedsMax) [LongLogTag]
                        Log.d(TAG, "message");
                              ~~~
                3 errors, 0 warnings""",
      "",
      // Expected exit code
      ERRNO_SUCCESS,
      // Args
      arrayOf(
        "--config",
        configFile.toString(),
        "--project",
        projectB.toString(),
        "--report-only",
        "--sdk-home",
        TestUtils.getSdk().toString(),
      ),
      null,
      null,
    )
    checkFilesDoNotContainBuildRoot(File(root, "out/java/com/google/b/lint_partial_results"))
    // Delete definite issues.
    File(root, "out/java/com/google/b/lint_partial_results/lint-definite.xml").delete()

    // Analyze project onlyres.
    MainTest.checkDriver(
      "",
      "",
      // Expected exit code
      ERRNO_SUCCESS,
      // Args
      arrayOf(
        "--config",
        configFile.toString(),
        "--project",
        projectOnlyRes.toString(),
        "--analyze-only",
        "--sdk-home",
        TestUtils.getSdk().toString(),
      ),
      null,
      null,
    )

    MainTest.checkDriver(
      "No issues found.",
      "",
      // Expected exit code
      ERRNO_SUCCESS,
      // Args
      arrayOf(
        "--config",
        configFile.toString(),
        "--project",
        projectOnlyRes.toString(),
        "--report-only",
        "--sdk-home",
        TestUtils.getSdk().toString(),
      ),
      null,
      null,
    )
    checkFilesDoNotContainBuildRoot(File(root, "out/java/com/google/onlyres/lint_partial_results"))
    // Delete definite issues.
    File(root, "out/java/com/google/onlyres/lint_partial_results/lint-definite.xml").delete()

    // Analyze project c.
    MainTest.checkDriver(
      "",
      "",
      // Expected exit code
      ERRNO_SUCCESS,
      // Args
      arrayOf(
        "--config",
        configFile.toString(),
        "--project",
        projectC.toString(),
        "--analyze-only",
        "--sdk-home",
        TestUtils.getSdk().toString(),
      ),
      null,
      null,
    )

    MainTest.checkDriver(
      """
                java/com/google/c/Activity.java:10: Error: Overriding method should call super.onStart [MissingSuperCall]
                    protected void onStart() {
                                   ~~~~~~~
                java/com/google/a/Activity.java:13: Error: The logging tag can be at most 23 characters, was 34 (SuperSuperLongLogTagThatExceedsMax) [LongLogTag]
                        Log.d(TAG, "message");
                              ~~~
                java/com/google/b/Activity.java:14: Error: The logging tag can be at most 23 characters, was 34 (SuperSuperLongLogTagThatExceedsMax) [LongLogTag]
                        Log.d(TAG, "message");
                              ~~~
                java/com/google/c/Activity.java:16: Error: The logging tag can be at most 23 characters, was 34 (SuperSuperLongLogTagThatExceedsMax) [LongLogTag]
                        Log.d(TAG, "message");
                              ~~~
                java/com/google/c/res/values/strings.xml:3: Error: The resource R.string.c_string_unused appears to be unused [UnusedResources]
                    <string name="c_string_unused">c string unused</string>
                            ~~~~~~~~~~~~~~~~~~~~~~
                java/com/google/onlyres/res/values/strings.xml:3: Error: The resource R.string.onlyres_string_unused appears to be unused [UnusedResources]
                    <string name="onlyres_string_unused">onlyres string unused</string>
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                java/com/google/b/res/values/strings.xml:4: Error: The resource R.string.b_string_unused appears to be unused [UnusedResources]
                    <string name="b_string_unused">b string unused</string>
                            ~~~~~~~~~~~~~~~~~~~~~~
                java/com/google/a/res/values/strings.xml:5: Error: The resource R.string.a_string_unused appears to be unused [UnusedResources]
                    <string name="a_string_unused">a string unused</string>
                            ~~~~~~~~~~~~~~~~~~~~~~
                8 errors, 0 warnings""",
      "",
      // Expected exit code
      ERRNO_SUCCESS,
      // Args
      arrayOf(
        "--config",
        configFile.toString(),
        "--project",
        projectC.toString(),
        "--report-only",
        "--sdk-home",
        TestUtils.getSdk().toString(),
      ),
      null,
      null,
    )
    checkFilesDoNotContainBuildRoot(File(root, "out/java/com/google/c/lint_partial_results"))
  }

  @Test
  fun testOverlappingInferred() {
    // Regression test for b/248054901
    val root = temp.newFolder().canonicalFile.absoluteFile
    val projects =
      lint()
        .files(
          xml(
              "project.xml",
              """
                <project>
                <module name="test" android="true" library="false">
                <src file="com/google/b244342092repro/ToBeChecked.java" test="true"/>
                <src file="com/google/b244342092repro/gen/com/google/b244342092repro/ToBeIgnored.java" generated="true"/>
                </module>
                </project>
                """,
            )
            .indented(),
          java(
              "com/google/b244342092repro/ToBeChecked.java",
              """
                package com.google.b244342092repro;
                final class ToBeChecked {
                  final ToBeIgnored ref = new ToBeIgnored();
                  private ToBeChecked() {
                  }
                }
                """,
            )
            .indented(),
          java(
              "com/google/b244342092repro/gen/com/google/b244342092repro/ToBeIgnored.java",
              """
                package com.google.b244342092repro;
                final class ToBeIgnored {
                  @org.junit.Ignore
                  @org.junit.Test
                  public void testFoo() {
                  }
                }
                """,
            )
            .indented(),
        )
        .createProjects(root)
    val descriptorFile = File(projects[0], "project.xml")

    MainTest.checkDriver(
      "No issues found.",
      "",

      // Expected exit code
      ERRNO_SUCCESS,

      // Args
      arrayOf("--check", "IgnoreWithoutReason", "--project", descriptorFile.path),
      null,
      null,
    )
  }

  @Test
  fun testKMPProjectK2() {
    Assume.assumeTrue(useFirUast())
    val shared =
      project(
          kt(
            "src/commonMain/kotlin/pkg/Platform.kt",
            """
            package pkg
            interface Platform {
                val name: String
            }
            expect fun getPlatform(): Platform
          """
              .trimIndent(),
          ),
          kt(
            "src/commonMain/kotlin/pkg/Greeting.kt",
            """
            package pkg
            class Greeting {
                private val platform: Platform = getPlatform()
            }
          """
              .trimIndent(),
          ),
          kt(
            "src/androidMain/kotlin/pkg/Platform.kt",
            """
            package pkg
            class AndroidPlatform : Platform {
                override val name: String = "Android 34"
            }
            actual fun getPlatform(): Platform = AndroidPlatform()
          """
              .trimIndent(),
          ),
          kt(
            "src/iosMain/kotlin/pkg/Platform.kt",
            """
            package pkg
            import platform.UIKit.UIDevice
            class IOSPlatform: Platform {
                override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
            }
            actual fun getPlatform(): Platform = IOSPlatform()
          """
              .trimIndent(),
          ),
          klib(
            "libs/SomeKlib.klib",
            "" +
              "H4sIAAAAAAAA/52Xe1BTVx7HL3kHApMXDykyCUgKKw0BfKwVuwGUAYI0Vsva" +
              "Gprk5iaQJSSQB4MPmIRUjARcFC1ixbVAi1P7h7Bry0zrlmLSggJaaq11dbYG" +
              "7aqruz62re6a7V7CqvdK7uWmlxkeczmf3/d7zsk5359cRqaEA7OPEEA+DIAD" +
              "QFqd2mawZry6IRKgI1/KMYdFIIbpzcFH8gEGgzFn5AL0SIu+wqi22sxai7jK" +
              "aJ3D0ahUKlClkpB9qwU6KlhKc8XRWye8k17PcKpntG942Pu5d1TkGY3i+eQq" +
              "yvsq5/v87Te5jgPDzG/GUlg6SeZA4bbur6Y+3Xq+6fRzC3KbB8IzB2uZjkpR" +
              "HbTi8KvSE4pLea05sFo64yNK+BYdXLEXV33MM+qtZr2xAlf6KOArFXxPAetg" +
              "6bF7X2B4DPJxVg/v/ist97LX33OTue496y3cDvMr5YrLOX5FeY6/KT/Bf7B8" +
              "2qnoLl91UuE0FGdluW8P3peZs+4n3P+VMN6uu0PKjBjPObJKslLlayo0q37a" +
              "2xfTyYf6yZFFE4sdFys7ujpfIx0X8Ecprzed5GxURAYcRn846miDhflxHcah" +
              "HUJa0FZRZNSZYI8QlkeVA5QAgRpnJZ8URMIv+Lg1+Oga1s01+Msvd4A6gY4M" +
              "1rHPgxxVh2oKjK7UOOTSxNylCm5JoPCBqQ0iETxiA27hhejCevNqrcagNqut" +
              "epPRguewkAJm0BjPUSD7Rqary3EhyrVvO3/o1ysuUPanvDWY1seUFN2tra21" +
              "ZjOP2R+wSE0Fra15LdJ3lrXvIedIUxLYKQnSFOlLTvsiOfOsoOAIlSQ1pEBh" +
              "S/+1682m+HF5VFgU+w+5w+tkJcVvylzuHb27mUd0L36at4ncYbdTnZxkXsBj" +
              "WsyDThes6aNQJlenNwQmV4dlrYYO0sUimk64SZzt33OJ1JBviZBZNDIZOTtf" +
              "Y+ElJ0fI8pP9vh7I7/lkb8+jcb7/Eei3e3qunjvo6Tu44cQtrlBHU0emiWgF" +
              "4gKa0CImP3pvZj9ej4X6neOF8Ia8HnuOfIMesBCXuOxyDVy7A9dCNNoCaIL0" +
              "AQ8g5gYMA9fTGCoKWL5rJbc4XjEiLW75vfvyzn29u9e6cu3rfLsonYLC9h3u" +
              "4+7dO52R6ZSAmILov65ZAUPW4IphI8RUq416ndYyd6OunciMIgnZ0i8PnWz3" +
              "Gs41j4yM7L9QaRgT9HZRaDHNE4nK29eV//2pu4e9/LdJoyu/gUz9X1zbXNat" +
              "v3E9jntxa2l929WvF/fncY5uGlh2ytD20s3udWER5soPix5k31nich7iO8+2" +
              "lB5ulB9o8zRY/tJYxqSSGKzG8wVfDu1vdGTMmFEsWO5rhJUcAfDObS7CDHzm" +
              "mmxmjdYSysHPQQAMemMVpLaqQzn+Y4ONrzZBNoN2DmXaN8icPLP2h8mxksE/" +
              "gsfAtKIXxAM+YPcW6rc3ym7t3Jy25mFXWplC3vxG4wDPydvYxSvhBhb2ztGh" +
              "vKUwYDWuFWEwKTVqTZW6QqtUGyGzSQ+FYk1MhCd5/Bu8navnOgbZYDtYP+x9" +
              "0OJelZnxbupvsqkBR9uaPzdHw//Aw3W0iIACsQljvYObSieIlChNluCWBq+B" +
              "L4/L0k6dKbkCnsr4HowoTC8unbgCnp7MGBsbnwbrxo9e9W1niETX+m5qO2tq" +
              "hUN5v1OUkFiK9AQWqVVhSGgllXBZUo5O8PZrduoobedAkaQwM5Vp7mNmZhVK" +
              "GkDoCpileviZN5wHrmGs+g6s93qptvY3Gu8+LxaJymavvtM/n3HAYvpwpy8J" +
              "z6sV/tiLa6oqQpm8xYSAEiX8PejcpUwMhI9I+AVXtpqMmkf98s7vUn0bL11q" +
              "ICmFFwVTU8Vv0+vqbl2t9PwwPfwZ88BXB9WHLi/8kdu/BPrAd7Z0und7a7b8" +
              "zlv/rr/NO//z4RvmEzvqm168x/r7BdnhpPdcd19f3RBdKfu4beW5ktzJPx3L" +
              "2/U1/UfqlpP+P6/9Vqx6SJqZtuq/HfNsg3W4cKctMZhLs8lkVf7faihTFnQL" +
              "o2AS5bwfnbDAoje52dei4Des0NUj1ygU9anzwiSz6z6fg3cXHR+avaQ+Lqte" +
              "yAVmriE8F8hLyqo2V2itIZ3q8UGGw/pt9cr6ZUtCASXjgvRGjcEGaTFO1flP" +
              "6blEuHfQ12FssF/CqzJZ4T+C8cJI4QBWC8UE0M/Thoo6i8EbHoEavhwxPNBY" +
              "IQh84OmuQ7YrC1CEWjTh2QYLg4dsDmJQvIwwALPlwYAhu4A4FIxDAnC6Cwwc" +
              "MtvzUTgbGodoJDBQyAi9EIX6BxoVrDXAYCIzLVreFBnAiOIYKGQijUah9lEA" +
              "rEiMwUIGQjaKlUQFgiRaAluVi8L8E4FBZkkCIA4KJKEBwTIlhi9kuItFcWqC" +
              "cR5nSwKqhCjaf4LR5sZDDJXIwCZGcWV0AtxnYiIB8YtQRSYIFJlNgljnASIx" +
              "paPQAgYx9NNESEB9EqoEi4lTAhHEMLQjY8tiFLicEBgRyAhIT0RVyA8PUuGZ" +
              "MIQhG5lX0KvZPi/0cSgKWW9YxDwzgq0XmUxSUdDSeaHIGERAM/r8+gKBfxp2" +
              "CGDiURgaay4GGXoIAJNRwJdxgYjwE/JZ9A4u+UkICpl7F5f7JAw94VJpMz8T" +
              "4C8qjMmdueOB/wFP4UXFXBYAAA==",
            0x2a5ba622,
            kotlin(
                """
            package test.pkg
            import android.os.Parcelable
            abstract class Parent : Parcelable
                """
              )
              .indented(),
            kotlin(
                """
                  package android.os
                  interface Parcelable
                  interface Parcel
                """
              )
              .indented(),
          ),
        )
        .type(LIBRARY)
        .name("shared")

    val androidApp =
      project(
          source(
              "src/main/$ANDROID_MANIFEST_XML",
              """
          <manifest xmlns:android="http://schemas.android.com/apk/res/android">

              <uses-permission android:name="android.permission.INTERNET"/>

              <application
                  android:allowBackup="false"
                  android:supportsRtl="true"
                  android:theme="@style/AppTheme">
                  <activity
                      android:name=".MainActivity"
                      android:exported="true">
                      <intent-filter>
                          <action android:name="android.intent.action.MAIN" />
                          <category android:name="android.intent.category.LAUNCHER" />
                      </intent-filter>
                  </activity>
              </application>
          </manifest>
        """,
            )
            .indented(),
          xml(
              "src/main/res/values/styles.xml",
              """
            <resources>
                <style name="AppTheme" parent="android:Theme.Material.NoActionBar"/>
            </resources>
          """,
            )
            .indented(),
          kt(
              "src/main/java/pkg/android/MainActivity.kt",
              """
            package pkg.android

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.compose.foundation.layout.fillMaxSize
            import androidx.compose.material.*
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.tooling.preview.Preview
            import com.example.kmptest.Greeting
            import androidx.compose.runtime.*

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent {
                        MyApplicationTheme {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colors.background
                            ) {
                                var text by remember { mutableStateOf("Loading") }
                                LaunchedEffect(true) {
                                    text = try {
                                        Greeting().greet()
                                    } catch (e: Exception) {
                                        e.localizedMessage ?: "error"
                                    }
                                }
                                GreetingView(text)
                            }
                        }
                    }
                }
            }

            @Composable
            fun GreetingView(text: String) {
                Text(text = text)
            }

            @Preview
            @Composable
            fun DefaultPreview() {
                MyApplicationTheme {
                    GreetingView("Hello, Android!")
                }
            }
          """,
            )
            .indented(),
          kt(
              "src/main/java/pkg/android/MyApplicationTheme.kt",
              """
            package pkg.android

            import androidx.compose.foundation.isSystemInDarkTheme
            import androidx.compose.foundation.shape.RoundedCornerShape
            import androidx.compose.material.MaterialTheme
            import androidx.compose.material.Shapes
            import androidx.compose.material.Typography
            import androidx.compose.material.darkColors
            import androidx.compose.material.lightColors
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.graphics.Color
            import androidx.compose.ui.text.TextStyle
            import androidx.compose.ui.text.font.FontFamily
            import androidx.compose.ui.text.font.FontWeight
            import androidx.compose.ui.unit.dp
            import androidx.compose.ui.unit.sp

            @Composable
            fun MyApplicationTheme(
                darkTheme: Boolean = isSystemInDarkTheme(),
                content: @Composable () -> Unit
            ) {
                val colors = if (darkTheme) {
                    darkColors(
                        primary = Color(0xFFBB86FC),
                        primaryVariant = Color(0xFF3700B3),
                        secondary = Color(0xFF03DAC5)
                    )
                } else {
                    lightColors(
                        primary = Color(0xFF6200EE),
                        primaryVariant = Color(0xFF3700B3),
                        secondary = Color(0xFF03DAC5)
                    )
                }
                val typography = Typography(
                    body1 = TextStyle(
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Normal,
                        fontSize = 16.sp
                    )
                )
                val shapes = Shapes(
                    small = RoundedCornerShape(4.dp),
                    medium = RoundedCornerShape(4.dp),
                    large = RoundedCornerShape(0.dp)
                )

                MaterialTheme(
                    colors = colors,
                    typography = typography,
                    shapes = shapes,
                    content = content
                )
            }
          """,
            )
            .indented(),
          kt(
            "src/main/java/pkg/android/expect.kt",
            """
            package pkg
            expect fun getPlatform() : Platform
          """
              .trimIndent(),
          ),
          kt(
            "src/main/java/pkg/android/actual.kt",
            """
            package pkg
            actual fun getPlatform() = TODO()
          """
              .trimIndent(),
          ),
        )
        .name("androidApp")
        .dependsOn(shared)

    val iosApp =
      project(
          source(
            "iosApp/ContentView.swift",
            """
            import SwiftUI
            import shared

            struct ContentView: View {
                @ObservedObject private(set) var viewModel: ViewModel

                var body: some View {
                    Text(viewModel.text)
                }
            }

            extension ContentView {
                class ViewModel: ObservableObject {
                    @Published var text = "Loading..."
                    init() {
                        Greeting().greet { greeting, error in
                                    DispatchQueue.main.async {
                                        if let greeting = greeting {
                                            self.text = greeting
                                        } else {
                                            self.text = error?.localizedDescription ?? "error"
                                        }
                                    }
                                }
                    }
                }
            }
          """
              .trimIndent(),
          ),
          source(
            "iosApp/iOSApp.swift",
            """
            import SwiftUI

            @main
            struct iOSApp: App {
              var body: some Scene {
                WindowGroup {
                        ContentView(viewModel: ContentView.ViewModel())
                }
              }
            }
          """
              .trimIndent(),
          ),
        )
        .name("iosApp")

    val root = temp.newFolder().canonicalFile.absoluteFile
    @Language("XML")
    val descriptor =
      """
        <project>
          <sdk dir='${TestUtils.getSdk()}'/>
          <root dir="$root" />

          <module name="androidApp" android="true" library="false" compile-sdk-version='18'>
            <manifest file="androidApp/src/main/AndroidManifest.xml" />
            <resource file="androidApp/src/main/res/values/styles.xml" />
            <src file="androidApp/src/main/java/pkg/android/MainActivity.kt" />
            <src file="androidApp/src/main/java/pkg/android/MyApplicationTheme.kt" />
            <src file="androidApp/src/main/java/pkg/android/expect.kt" />
            <src file="androidApp/src/main/java/pkg/android/actual.kt" />
            <dep module="commonMain" kind="dependsOn" />
          </module>

          <module name="iosApp" android="false" library="false">
            <src file="iosApp/iosApp/ContentView.swift" />
            <src file="iosApp/iosApp/iOSApp.swift" />
            <dep module="commonMain" kind="dependsOn"/>
          </module>

          <module name="commonMain" android="false">
            <src file="shared/src/commonMain/kotlin/pkg/Platform.kt" />
            <src file="shared/src/androidMain/kotlin/pkg/Platform.kt" />
            <src file="shared/src/iosMain/kotlin/pkg/Platform.kt" />
            <klib file="shared/libs/SomeKlib.klib" />
          </module>
        </project>
      """
        .trimIndent()

    val projects = lint().projects(shared, androidApp, iosApp).createProjects(root)
    Files.asCharSink(File(root, "project.xml"), Charsets.UTF_8).write(descriptor)

    MainTest.checkDriver(
      """
        src/main/res/values/styles.xml:2: Error: android:Theme.Material.NoActionBar requires API level 21 (current min is 1) [NewApi]
    <style name="AppTheme" parent="android:Theme.Material.NoActionBar"/>
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
src/main/AndroidManifest.xml:6: Warning: Attribute allowBackup is only used in API level 4 and higher (current min is 1) [UnusedAttribute]
        android:allowBackup="false"
        ~~~~~~~~~~~~~~~~~~~~~~~~~~~
src/main/AndroidManifest.xml:1: Warning: Should set android:versionCode to specify the application version [MissingVersion]
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
 ~~~~~~~~
src/main/AndroidManifest.xml:1: Warning: Should set android:versionName to specify the application version [MissingVersion]
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
 ~~~~~~~~
src/main/AndroidManifest.xml:5: Warning: Should explicitly set android:icon, there is no default [MissingApplicationIcon]
    <application
     ~~~~~~~~~~~
src/main/AndroidManifest.xml:7: Warning: You must set android:targetSdkVersion to at least 17 when enabling RTL support [RtlEnabled]
        android:supportsRtl="true"
                             ~~~~
1 errors, 5 warnings
      """,
      "",
      ERRNO_SUCCESS,
      arrayOf("--XuseK2Uast", "--project", File(root, "project.xml").path),
      { it.replace(root.canonicalPath, "ROOT").replace(root.path, "ROOT").dos2unix() },
      object : LintListener {
        override fun update(
          driver: LintDriver,
          type: LintListener.EventType,
          project: Project?,
          context: Context?,
        ) {}
      },
    )
  }

  @Test
  fun testKMPProjectK2_common_klib() {
    Assume.assumeTrue(useFirUast())
    val shared =
      project(
          // TODO
          //   Here we leave `androidMain/.../Platform.kt` out of the klib because `kotlinc-native`
          //   gives an error on conflicting overloads of `actual fun getPlatform()`, and it's
          //   strange for the Android-specific file to be passed to `kotlinc-native`.
          //   If we have neither, `kotlinc-native` will complain that there's no corresponding
          //   `actual` to the `expect`.
          klib(
            "build/common.klib",
            "" +
              "H4sIAAAAAAAA/6WXDzzT+R/Hv/N3bJj8G04ZQ8LMn1ZCNyvKQqjLuC4282dh" +
              "Y7ZQd0TKn/zthIQjNS6XSu6a4nR+kjJ/kkJdvy6kf64/SgrxG/e7u+/3bIvf" +
              "77vHY4/H/ryfe79f3/dn79fbc4u0jCLwx4UBwBccUAXoQcFUbjjH8ovtSoA8" +
              "+ENPsWEIUBiDLTpSHYDD4QsitaGR0YwQJpXDZQdF48KYnAWcwICAgOCAAD6y" +
              "5TVxWiv1w/5C6VTnnFBUsDyNUFWIQiEAK7U8FV59Px+tnYzWrk8m2qoZXmjp" +
              "s7P4eDnEgt+Qq4q9ZtDSpngkUJVG25B74HRIAf8+4QN5trrUvRqdSalH22HR" +
              "dZS1FBlKuVx0lMJ+BjPteVjp9K/rP34kh5muL3PIbsly3spzL5l9uztZSrrX" +
              "IF628Gi4Z+WBYlMklyC4XtWpmWx3+c21ZxFNluaNYbK2pV77nWmsl5dK8+Ms" +
              "PeST8moVrGrN8MQM+dr6nIyNylbUOtQGZQuq7c+oAYwJHGHH/9ZKSrmqAKHj" +
              "Fpmgn6SwOwmwT6+s91K3IZWYAEoJmI3vpYVqysOlUkJeyMEA4HMpSepq/kNd" +
              "DpvBDBEtbWbug93NePRVQzUTTTkD5J7HiFElMob3vYyRhave6OnYm1wn91Pt" +
              "HaXN134NGw07nuHm038Vk5I9/aFELblbj/QEwXEKtU3iZk0OqHNUiiiZjXHP" +
              "V5xRIaqUmVw+62jz+YrT3G35Lk9X8RSGbJWP7jBe1ztS9VbZ/M53j4f+3XC7" +
              "w3SnkZdxT0b69I8Uai3166+wGcwhwYsq8lA4fGagJOM3dsO9katdUW7wo9Ya" +
              "5XE+K1TPUm9qDFlfK+4meZIGEBqa97IHXIrirIovrdPP2veg9ebUtoHyumB0" +
              "RZeZu7S6Y48SQh221dzcA9892R92n9pR4/fd3TiAb37UpyXha6IW7dx0qd8p" +
              "9tNnu2Zsxs+O6bW6R77c1gSbE/phORWmLhQ6RaLQaKjQ9CAaN8SVGcwSSk0X" +
              "18Wp8jRN/SwYzUVfOeYNZzzKCw4fU4lUUkzLiGFz3r17G52dAn9sNMLz0xjG" +
              "5p98pPVoVd52TSBu4OleGD63VjAoOCkoFhwhVyedENS2HNl+8EmdoKGlmCIH" +
              "B3xVSf2KKJjVevsmrInNVkv+yZU2lnrZH+Y7J+anddf2CrPokngu1aEFceIi" +
              "JR/Jc1I0jZUdii3hREFWxo8UmX5sYR1F9i5FX31F8rIDnt7L3JCob/upl5ya" +
              "9S0iMx0CVmOP4aTK6djC+Zy2EPw+cxLCqiTmpAfNicHeGBQYTmVTOQwWM1qk" +
              "0kE5W8nSVuqt/ta3ilq9HrT49ZQZCU4cHrBjtvqmrTRgajgmar2S2jaLeeKy" +
              "knlf/RdD/iuDxsQvVTy/YRin6rzXfNk+9ea19H440HoS0aOblpiw+dIOQ2P7" +
              "E5k7E170E6wvmJ/i96XWyDl07uOZHXVrSZ0qvVcUWJ08XXa34PCgU87U3YsF" +
              "Vjj0yZvVMvadnvxy33zXLbvORLomhLa/Nj9+9uUkxs9/LOlq3oE1x+64Vvq/" +
              "LWoo8yiKvdVbmhJ7hOmpxD/O+uI47hBiorC/AF0huJForxPY7rthY8L295o6" +
              "q9Cz0WNZdaSinDu97kTr+1NONDx3/HPssCx2020q0fr5vUr/zcGw692GTqQx" +
              "Zrilvld8aNbG135q+bQveDVcon/XvTx6c/sT3Tn9LaSmBFhhk3fDltATwYzw" +
              "+Z4IFtcTr+QHAf13QzQ6iv24ts/yukX3GlsbAgGPJ9hYr8YT6s7hV9ta1/Za" +
              "4sh3tnZ2dXR0vXEXCG51dVpeh3nLDjydfOH8/fdGx+Ke7h0wIpkjHWph3hlI" +
              "/SGHNhucvsl5rEXTSi36Z2bS2SeOybY5GMvFY3xwutNywSNaanh3MxwsVGe+" +
              "tTAKSeRUYU4fJbaWBrQ0GovOmK+NtqC2iCx3srQT6mD1vpl0MrKE0MIj/uKC" +
              "SU0y1i3ACGAOh00Kj/F7dIeBXcSL0bIq+DVtBJNXRoYncg5NpMc3rFpvQE4N" +
              "VTrVbTI+ax8q4zjG1A5hp5cbmu7Q7qs3CWlmmg59szZv9Dalc6qqO8MsJUnp" +
              "ZUGDUhRGBb3XrE9XrX5sZievS+11w/JnacOZru8jBleGS+8IUMiYLJgJQL5D" +
              "dzvGPIKdMput4Bh5omacnPI6f78FbGqq4d+W3lTd9NkLdIr8pmpKohF+psq9" +
              "IVe7ydal4klrcRT3YePmQETNGUOjH8oOrlv3YE62wlYt3pCw3C0SOwIFki2C" +
              "ymQEB0Uv/Idwzzyv3KyPIk5Mob3dKnBIZWWcB8/Mbti24IrXw9DJD3voe9vb" +
              "95VHe+eP9CVPmq6+/Pj3ccqL7oMJJQ0BxhM4O61EaTOT0/EVd/mpF0Z2e2ga" +
              "nMGer+dZRQFIB7SWTWLZmMuu8/Gux7eYfzMxFXfILV1VQz6BoBm7023Tbypz" +
              "pVh/NX74a2EePECShVkGKkVoP1hcdmBQ9FI8kCoIEM5ghtGpHOpSnJCWqPgI" +
              "Fp0bHrSAMjR4znX+xFy4QDtHw7V5xNCA+W531j34u4bwC2oSU9UT9VOR1MAw" +
              "akiQf2RYyFLSNv4Uy2ruWXiUIhZW0TM86LHVQqHN1dy13fLGo7lCHg/uj66n" +
              "mjeOLsMsxxnDXHAEZTmYsYKcKdV0D0wOcUh+p+A7tC+/EIlmRcLskGevuPxR" +
              "OG84bvlmIdPn/0sWLzZZx25HZjMRJfvbR22WMev4wRtk2ZoqgxqiuoK36sND" +
              "+c367QE5Pzr3YJRzDYd39uZtzxuV4qE7bo1W+pjHl8XcVnRgpjcXjCvy7k5y" +
              "fTdkP/N/xWx81R5xAZZw8fpPbxvHzvnfmv5AGqdtuzL8c5rzT42b+kwr485a" +
              "PHfu0yPZwf7VfH178g+kHR6VE1+O9VtePPNr2Kp/b7zzkpx7Q8XqQEVa8YpQ" +
              "Voevda9nSjnCjZCKVTg5tDanqUstNS5gQjPGlSqQbZtsyMgn+ebNvsVOOmWh" +
              "Ku/NFpcHkXzmpAvc1zZ5R1ihBUxSzywXJR2bxeL4/1e/pTQN9pMwvL/olqGh" +
              "aDm02CtX36cfhs3f+KTDqEfKwk+QEjse/EfFobJDgjhLOts6IsKFeXNj/WMJ" +
              "tksBGUoEMZiB4Vx6EH0pRIxEonCZYuwRc2v+F14YiyN8IYoHk1IExO2UCgD0" +
              "+nvDlP0DIykcAQlfAwqf3zRBBHXg724D70faEEIUlPDPjVMMD7wGaEJ4XTBA" +
              "7I4lBga24GgIbK00IGGPEIMDu2d1CE5BBhDj4sWgwEZQD4IqgaJEmW8xTLAD" +
              "g6anJweIMZRiUGBXogFBvYeiwAZODAtsC1AQliUcEOFqFtGqyyCYSRAG7CgW" +
              "AVKFgOwVAFHOQkxdYAugBeHsE8X502EsIitoQ4yIokFNhJgMwbPaGMK0VvwE" +
              "E2QmxMDB0wwKf/ApOF4kXJwayyFwHaQI+D/Go5iMwRMMC4GGfBL655hcRL7Q" +
              "Hu8Hof8eiIvA6EAwWkoLMeDBuAigIQQYIBEIGpCLIGMg5FqJ5L8G5ZK5UsqS" +
              "uH8NzL+4snLzwgkfpsJ3vOfuPPAfJ0MmAZEVAAA=",
            0x411ab151,
            kt(
              "src/commonMain/kotlin/pkg/Platform.kt",
              """
            package pkg
            interface Platform {
                val name: String
            }
            expect fun getPlatform(): Platform
          """
                .trimIndent(),
            ),
            kt(
              "src/commonMain/kotlin/pkg/Greeting.kt",
              """
            package pkg
            class Greeting {
                private val platform: Platform = getPlatform()
            }
          """
                .trimIndent(),
            ),
            kt(
              "src/iosMain/kotlin/pkg/Platform.kt",
              """
            package pkg
            class IOSPlatform: Platform {
                override val name: String = "iOS platform name"
            }
            actual fun getPlatform(): Platform = IOSPlatform()
          """
                .trimIndent(),
            ),
          ),
          kt(
            "src/androidMain/kotlin/pkg/Platform.kt",
            """
            package pkg
            class AndroidPlatform : Platform {
                override val name: String = "Android 34"
            }
            actual fun getPlatform(): Platform = AndroidPlatform()
          """
              .trimIndent(),
          ),
        )
        .type(LIBRARY)
        .name("shared")

    val androidApp =
      project(
          source(
              "src/main/$ANDROID_MANIFEST_XML",
              """
          <manifest xmlns:android="http://schemas.android.com/apk/res/android">

              <uses-permission android:name="android.permission.INTERNET"/>

              <application
                  android:allowBackup="false"
                  android:supportsRtl="true"
                  android:theme="@style/AppTheme">
                  <activity
                      android:name=".MainActivity"
                      android:exported="true">
                      <intent-filter>
                          <action android:name="android.intent.action.MAIN" />
                          <category android:name="android.intent.category.LAUNCHER" />
                      </intent-filter>
                  </activity>
              </application>
          </manifest>
        """,
            )
            .indented(),
          xml(
              "src/main/res/values/styles.xml",
              """
            <resources>
                <style name="AppTheme" parent="android:Theme.Material.NoActionBar"/>
            </resources>
          """,
            )
            .indented(),
          kt(
              "src/main/java/pkg/android/MainActivity.kt",
              """
            package pkg.android

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.compose.foundation.layout.fillMaxSize
            import androidx.compose.material.*
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.tooling.preview.Preview
            import com.example.kmptest.Greeting
            import androidx.compose.runtime.*

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent {
                        MyApplicationTheme {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colors.background
                            ) {
                                var text by remember { mutableStateOf("Loading") }
                                LaunchedEffect(true) {
                                    text = try {
                                        Greeting().greet()
                                    } catch (e: Exception) {
                                        e.localizedMessage ?: "error"
                                    }
                                }
                                GreetingView(text)
                            }
                        }
                    }
                }
            }

            @Composable
            fun GreetingView(text: String) {
                Text(text = text)
            }

            @Preview
            @Composable
            fun DefaultPreview() {
                MyApplicationTheme {
                    GreetingView("Hello, Android!")
                }
            }
          """,
            )
            .indented(),
          kt(
              "src/main/java/pkg/android/MyApplicationTheme.kt",
              """
            package pkg.android

            import androidx.compose.foundation.isSystemInDarkTheme
            import androidx.compose.foundation.shape.RoundedCornerShape
            import androidx.compose.material.MaterialTheme
            import androidx.compose.material.Shapes
            import androidx.compose.material.Typography
            import androidx.compose.material.darkColors
            import androidx.compose.material.lightColors
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.graphics.Color
            import androidx.compose.ui.text.TextStyle
            import androidx.compose.ui.text.font.FontFamily
            import androidx.compose.ui.text.font.FontWeight
            import androidx.compose.ui.unit.dp
            import androidx.compose.ui.unit.sp

            @Composable
            fun MyApplicationTheme(
                darkTheme: Boolean = isSystemInDarkTheme(),
                content: @Composable () -> Unit
            ) {
                val colors = if (darkTheme) {
                    darkColors(
                        primary = Color(0xFFBB86FC),
                        primaryVariant = Color(0xFF3700B3),
                        secondary = Color(0xFF03DAC5)
                    )
                } else {
                    lightColors(
                        primary = Color(0xFF6200EE),
                        primaryVariant = Color(0xFF3700B3),
                        secondary = Color(0xFF03DAC5)
                    )
                }
                val typography = Typography(
                    body1 = TextStyle(
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Normal,
                        fontSize = 16.sp
                    )
                )
                val shapes = Shapes(
                    small = RoundedCornerShape(4.dp),
                    medium = RoundedCornerShape(4.dp),
                    large = RoundedCornerShape(0.dp)
                )

                MaterialTheme(
                    colors = colors,
                    typography = typography,
                    shapes = shapes,
                    content = content
                )
            }
          """,
            )
            .indented(),
          kt(
            "src/main/java/pkg/android/expect.kt",
            """
            package pkg
            expect fun getPlatform() : Platform
          """
              .trimIndent(),
          ),
          kt(
            "src/main/java/pkg/android/actual.kt",
            """
            package pkg
            actual fun getPlatform() = TODO()
          """
              .trimIndent(),
          ),
        )
        .name("androidApp")
        .dependsOn(shared)

    val iosApp =
      project(
          source(
            "iosApp/ContentView.swift",
            """
            import SwiftUI
            import shared

            struct ContentView: View {
                @ObservedObject private(set) var viewModel: ViewModel

                var body: some View {
                    Text(viewModel.text)
                }
            }

            extension ContentView {
                class ViewModel: ObservableObject {
                    @Published var text = "Loading..."
                    init() {
                        Greeting().greet { greeting, error in
                                    DispatchQueue.main.async {
                                        if let greeting = greeting {
                                            self.text = greeting
                                        } else {
                                            self.text = error?.localizedDescription ?? "error"
                                        }
                                    }
                                }
                    }
                }
            }
          """
              .trimIndent(),
          ),
          source(
            "iosApp/iOSApp.swift",
            """
            import SwiftUI

            @main
            struct iOSApp: App {
              var body: some Scene {
                WindowGroup {
                        ContentView(viewModel: ContentView.ViewModel())
                }
              }
            }
          """
              .trimIndent(),
          ),
        )
        .name("iosApp")

    val root = temp.newFolder().canonicalFile.absoluteFile
    @Language("XML")
    val descriptor =
      """
        <project>
          <sdk dir='${TestUtils.getSdk()}'/>
          <root dir="$root" />

          <module name="androidApp" android="true" library="false" compile-sdk-version='18'>
            <manifest file="androidApp/src/main/AndroidManifest.xml" />
            <resource file="androidApp/src/main/res/values/styles.xml" />
            <src file="androidApp/src/main/java/pkg/android/MainActivity.kt" />
            <src file="androidApp/src/main/java/pkg/android/MyApplicationTheme.kt" />
            <src file="androidApp/src/main/java/pkg/android/expect.kt" />
            <src file="androidApp/src/main/java/pkg/android/actual.kt" />
            <klib file="shared/build/common.klib" kind="dependsOn" />
          </module>

          <module name="iosApp" android="false" library="false">
            <src file="iosApp/iosApp/ContentView.swift" />
            <src file="iosApp/iosApp/iOSApp.swift" />
            <klib file="shared/build/common.klib" kind="dependsOn"/>
          </module>

          <module name="commonMain">
            <src file="shared/src/androidMain/kotlin/pkg/Platform.kt" />
          </module>
        </project>
      """
        .trimIndent()

    val projects = lint().projects(shared, androidApp, iosApp).createProjects(root)
    Files.asCharSink(File(root, "project.xml"), Charsets.UTF_8).write(descriptor)

    MainTest.checkDriver(
      """
        src/main/res/values/styles.xml:2: Error: android:Theme.Material.NoActionBar requires API level 21 (current min is 1) [NewApi]
    <style name="AppTheme" parent="android:Theme.Material.NoActionBar"/>
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
src/main/AndroidManifest.xml:6: Warning: Attribute allowBackup is only used in API level 4 and higher (current min is 1) [UnusedAttribute]
        android:allowBackup="false"
        ~~~~~~~~~~~~~~~~~~~~~~~~~~~
src/main/AndroidManifest.xml:1: Warning: Should set android:versionCode to specify the application version [MissingVersion]
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
 ~~~~~~~~
src/main/AndroidManifest.xml:1: Warning: Should set android:versionName to specify the application version [MissingVersion]
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
 ~~~~~~~~
src/main/AndroidManifest.xml:5: Warning: Should explicitly set android:icon, there is no default [MissingApplicationIcon]
    <application
     ~~~~~~~~~~~
src/main/AndroidManifest.xml:7: Warning: You must set android:targetSdkVersion to at least 17 when enabling RTL support [RtlEnabled]
        android:supportsRtl="true"
                             ~~~~
1 errors, 5 warnings
      """,
      "",
      ERRNO_SUCCESS,
      arrayOf("--XuseK2Uast", "--project", File(root, "project.xml").path),
      { it.replace(root.canonicalPath, "ROOT").replace(root.path, "ROOT").dos2unix() },
      object : LintListener {
        override fun update(
          driver: LintDriver,
          type: LintListener.EventType,
          project: Project?,
          context: Context?,
        ) {}
      },
    )
  }

  @After
  fun tearDown() {
    UastEnvironment.disposeApplicationEnvironment()
  }

  companion object {
    @ClassRule @JvmField var temp = TemporaryFolder()

    fun project(vararg files: TestFile): ProjectDescription = ProjectDescription(*files)
  }
}
