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
package com.android.tools.lint.checks

import com.android.testutils.TestUtils
import com.android.tools.lint.checks.GradleDetectorTest.Companion.createRelativePaths
import com.android.tools.lint.checks.infrastructure.ProjectDescription
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import java.io.File

class ManifestDetectorTest : AbstractCheckTest() {
  private var sdkDir: File? = null

  override fun getDetector(): Detector {
    return ManifestDetector()
  }

  override fun tearDown() {
    super.tearDown()
    if (sdkDir != null) {
      deleteFile(sdkDir)
      sdkDir = null
    }
  }

  fun testOrderOk() {
    lint().files(manifest().minSdk(14), strings).issues(ManifestDetector.ORDER).run().expectClean()
  }

  fun testBrokenOrder() {
    val expected =
      """
            AndroidManifest.xml:15: Warning: <uses-sdk> tag appears after <application> tag [ManifestOrder]
               <uses-sdk android:minSdkVersion="Froyo" />
                ~~~~~~~~
            0 errors, 1 warnings
            """
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                     package="com.example.helloworld"
                     android:versionCode="1"
                     android:versionName="1.0">
                   <application android:icon="@drawable/icon" android:label="@string/app_name">
                       <activity android:name=".HelloWorld"
                                 android:label="@string/app_name">
                           <intent-filter>
                               <action android:name="android.intent.action.MAIN" />
                               <category android:name="android.intent.category.LAUNCHER" />
                           </intent-filter>
                       </activity>

                   </application>
                   <uses-sdk android:minSdkVersion="Froyo" />

                </manifest>
                """
          )
          .indented(),
        strings,
      )
      .issues(ManifestDetector.ORDER)
      .run()
      .expect(expected)
  }

  fun testMissingUsesSdkInGradle() {
    lint()
      .files(missingUsesSdk, library) // placeholder; only name counts
      .issues(ManifestDetector.SET_VERSION)
      .run()
      .expectClean()
  }

  fun testMultipleSdk() {
    val expected =
      """
            AndroidManifest.xml:7: Error: There should only be a single <uses-sdk> element in the manifest: merge these together [MultipleUsesSdk]
                <uses-sdk android:targetSdkVersion="14" />
                 ~~~~~~~~
                AndroidManifest.xml:6: Also appears here
                <uses-sdk android:minSdkVersion="5" />
                 ~~~~~~~~
                AndroidManifest.xml:8: Also appears here
                <uses-sdk android:maxSdkVersion="15" />
                 ~~~~~~~~
            1 errors, 0 warnings
            """
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.bytecode"
                    android:versionCode="1"
                    android:versionName="1.0" >

                    <uses-sdk android:minSdkVersion="5" />
                    <uses-sdk android:targetSdkVersion="14" />
                    <uses-sdk android:maxSdkVersion="15" />

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                        <activity
                            android:name=".BytecodeTestsActivity"
                            android:label="@string/app_name" >
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />

                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """
          )
          .indented(),
        strings,
      )
      .issues(ManifestDetector.MULTIPLE_USES_SDK)
      .run()
      .expect(expected)
  }

  fun testWrongLocation() {
    val expected =
      """
            AndroidManifest.xml:7: Error: The <uses-sdk> element must be a direct child of the <manifest> root element [WrongManifestParent]
                   <uses-sdk android:minSdkVersion="Froyo" />
                    ~~~~~~~~
            AndroidManifest.xml:8: Error: The <uses-permission> element must be a direct child of the <manifest> root element [WrongManifestParent]
                   <uses-permission />
                    ~~~~~~~~~~~~~~~
            AndroidManifest.xml:9: Error: The <permission> element must be a direct child of the <manifest> root element [WrongManifestParent]
                   <permission />
                    ~~~~~~~~~~
            AndroidManifest.xml:10: Error: The <permission-tree> element must be a direct child of the <manifest> root element [WrongManifestParent]
                   <permission-tree />
                    ~~~~~~~~~~~~~~~
            AndroidManifest.xml:11: Error: The <permission-group> element must be a direct child of the <manifest> root element [WrongManifestParent]
                   <permission-group />
                    ~~~~~~~~~~~~~~~~
            AndroidManifest.xml:13: Error: The <uses-sdk> element must be a direct child of the <manifest> root element [WrongManifestParent]
                   <uses-sdk />
                    ~~~~~~~~
            AndroidManifest.xml:14: Error: The <uses-configuration> element must be a direct child of the <manifest> root element [WrongManifestParent]
                   <uses-configuration />
                    ~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:15: Error: The <uses-feature> element must be a direct child of the <manifest> root element [WrongManifestParent]
                   <uses-feature />
                    ~~~~~~~~~~~~
            AndroidManifest.xml:16: Error: The <supports-screens> element must be a direct child of the <manifest> root element [WrongManifestParent]
                   <supports-screens />
                    ~~~~~~~~~~~~~~~~
            AndroidManifest.xml:17: Error: The <compatible-screens> element must be a direct child of the <manifest> root element [WrongManifestParent]
                   <compatible-screens />
                    ~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:18: Error: The <supports-gl-texture> element must be a direct child of the <manifest> root element [WrongManifestParent]
                   <supports-gl-texture />
                    ~~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:23: Error: The <uses-library> element must be a direct child of the <application> element [WrongManifestParent]
               <uses-library />
                ~~~~~~~~~~~~
            AndroidManifest.xml:24: Error: The <activity> element must be a direct child of the <application> element [WrongManifestParent]
               <activity android:name=".HelloWorld"
                ~~~~~~~~
            13 errors, 0 warnings
            """
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                     package="com.example.helloworld"
                     android:versionCode="1"
                     android:versionName="1.0">
                   <application android:icon="@drawable/icon" android:label="@string/app_name">
                       <!-- Wrong declaration locations -->
                       <uses-sdk android:minSdkVersion="Froyo" />
                       <uses-permission />
                       <permission />
                       <permission-tree />
                       <permission-group />
                       <instrumentation />
                       <uses-sdk />
                       <uses-configuration />
                       <uses-feature />
                       <supports-screens />
                       <compatible-screens />
                       <supports-gl-texture />

                   </application>

                   <!-- Wrong declaration locations -->
                   <uses-library />
                   <activity android:name=".HelloWorld"
                                 android:label="@string/app_name" />

                </manifest>
                """
          )
          .indented()
      )
      .issues(ManifestDetector.WRONG_PARENT)
      .run()
      .expect(expected)
  }

  fun test112063828() {
    // Regression test for
    // 112063828: Lint flags "uses-feature" from my own namespace is not a child of <manifest>
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:dist="http://schemas.android.com/apk/distribution"
                    package="test.pkg.nullnessmigrationtest">

                    <uses-permission android:name="android.permission.INTERNET"/>

                    <dist:module
                        dist:onDemand="false"
                        dist:title="@string/title_arsolarfeature">
                        <dist:fusing include="true" />
                        <dist:conditions>
                            <dist:uses-feature android:name="android.hardware.camera.ar" android:required="true"/>
                        </dist:conditions>
                    </dist:module>
                </manifest>
                """
          )
          .indented()
      )
      .issues(ManifestDetector.WRONG_PARENT)
      .run()
      .expectClean()
  }

  fun testDuplicateActivity() {
    val expected =
      """
            AndroidManifest.xml:15: Error: Duplicate registration for activity com.example.helloworld.HelloWorld [DuplicateActivity]
                   <activity android:name="com.example.helloworld.HelloWorld"
                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                     package="com.example.helloworld"
                     android:versionCode="1"
                     android:versionName="1.0">
                   <uses-sdk android:minSdkVersion="14" />
                   <application android:icon="@drawable/icon" android:label="@string/app_name">
                       <activity android:name=".HelloWorld"
                                 android:label="@string/app_name">
                           <intent-filter>
                               <action android:name="android.intent.action.MAIN" />
                               <category android:name="android.intent.category.LAUNCHER" />
                           </intent-filter>
                       </activity>

                       <activity android:name="com.example.helloworld.HelloWorld"
                                 android:label="@string/app_name">
                       </activity>

                   </application>

                </manifest>
                """
          )
          .indented(),
        strings,
      )
      .issues(ManifestDetector.DUPLICATE_ACTIVITY)
      .run()
      .expect(expected)
  }

  fun testDuplicateActivityAcrossSourceSets() {
    val library =
      project(manifest().minSdk(14), projectProperties().library(true), libraryCode, libraryStrings)
        .name("LibraryProject")
    val main =
      project(
          manifest().minSdk(14),
          projectProperties()
            .property("android.library.reference.1", "../LibraryProject")
            .property("manifestmerger.enabled", "true"),
          mainCode,
        )
        .name("MainProject")
        .dependsOn(library)
    lint().projects(library, main).issues(ManifestDetector.DUPLICATE_ACTIVITY).run().expectClean()
  }

  fun testIgnoreDuplicateActivity() {
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                     xmlns:tools="http://schemas.android.com/tools"
                     package="com.example.helloworld"
                     android:versionCode="1"
                     android:versionName="1.0">
                   <uses-sdk android:minSdkVersion="14" />
                   <application android:icon="@drawable/icon" android:label="@string/app_name" tools:ignore="DuplicateActivity">
                       <activity android:name=".HelloWorld"
                                 android:label="@string/app_name">
                           <intent-filter>
                               <action android:name="android.intent.action.MAIN" />
                               <category android:name="android.intent.category.LAUNCHER" />
                           </intent-filter>
                       </activity>

                       <activity android:name="com.example.helloworld.HelloWorld"
                                 android:label="@string/app_name">
                       </activity>

                   </application>

                </manifest>
                """
          )
          .indented(),
        strings,
      )
      .issues(ManifestDetector.DUPLICATE_ACTIVITY)
      .run()
      .expectClean()
  }

  fun testAllowBackup() {
    // No longer flagging this; it's noisy and many users just suppress it
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.bytecode"
                    android:versionCode="1"
                    android:versionName="1.0" >

                    <uses-sdk android:minSdkVersion="14" />

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                    </application>

                </manifest>
                """
          )
          .indented(),
        strings,
      )
      .issues(ManifestDetector.DATA_EXTRACTION_RULES)
      .run()
      .expectClean()
  }

  fun testAllowBackupOk() {
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="foo.bar2"
                    android:versionCode="1"
                    android:versionName="1.0" >

                    <uses-sdk android:minSdkVersion="14" />

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name"
                        android:allowBackup="true" >
                        <activity
                            android:label="@string/app_name"
                            android:name=".Foo2Activity" >
                            <intent-filter >
                                <action android:name="android.intent.action.MAIN" />

                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """
          )
          .indented(),
        strings,
      )
      .issues(ManifestDetector.DATA_EXTRACTION_RULES)
      .run()
      .expectClean()
  }

  fun testAllowBackupUnnecessary() {
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="foo.bar2"
                    android:versionCode="1"
                    android:versionName="1.0" >

                    <uses-sdk android:minSdkVersion="31" />

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name"
                        android:allowBackup="true" >
                    </application>

                </manifest>
                """
          )
          .indented(),
        strings,
      )
      .issues(ManifestDetector.DATA_EXTRACTION_RULES)
      .run()
      .expect(
        """
            AndroidManifest.xml:11: Warning: The attribute android:allowBackup is deprecated from Android 12 and the default allows backup [DataExtractionRules]
                    android:allowBackup="true" >
                                         ~~~~
            0 errors, 1 warnings
            """
      )
      .expectFixDiffs(
        """
            Fix for AndroidManifest.xml line 11: Delete allowBackup:
            @@ -10 +10
            -         android:allowBackup="true"
            """
      )
  }

  fun testAllowBackupOk3() {
    // Not flagged in library projects
    lint()
      .files(manifest().minSdk(14), projectProperties().library(true), strings)
      .issues(ManifestDetector.DATA_EXTRACTION_RULES)
      .run()
      .expectClean()
  }

  fun testAllowIgnore() {
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="foo.bar2"
                    android:versionCode="1"
                    android:versionName="1.0" >

                    <uses-sdk android:minSdkVersion="14" />

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name"
                        tools:ignore="AllowBackup">
                        <activity
                            android:label="@string/app_name"
                            android:name=".Foo2Activity" >
                            <intent-filter >
                                <action android:name="android.intent.action.MAIN" />

                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """
          )
          .indented(),
        strings,
      )
      .issues(ManifestDetector.DATA_EXTRACTION_RULES)
      .run()
      .expectClean()
  }

  fun testNoApplication() {
    lint()
      .files(
        manifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="test.pkg"
                android:versionCode="1"
                android:versionName="1.0" >
            </manifest>
            """
          )
          .indented()
      )
      .issues(ManifestDetector.DATA_EXTRACTION_RULES, ManifestDetector.APPLICATION_ICON)
      .run()
      .expectClean()
  }

  fun testDuplicatePermissions() {
    val expected =
      """
            AndroidManifest.xml:11: Error: Permission name SEND_SMS is not unique (appears in both foo.permission.SEND_SMS and bar.permission.SEND_SMS) [UniquePermission]
                <permission android:name="bar.permission.SEND_SMS"
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:8: Previous permission here
                <permission android:name="foo.permission.SEND_SMS"
                                          ~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
    lint()
      .files(
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
                    <permission android:name="bar.permission.SEND_SMS"
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
        strings,
      )
      .issues(ManifestDetector.UNIQUE_PERMISSION)
      .run()
      .expect(expected)
  }

  fun testDuplicatePermissionGroups() {
    val expected =
      """
            AndroidManifest.xml:11: Error: Permission group name STORAGE is not unique (appears in both foo.permissiongroup.STORAGE and bar.permissiongroup.STORAGE) [UniquePermission]
                <permission-group android:name="bar.permissiongroup.STORAGE"
                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:8: Previous permission group here
                <permission-group android:name="foo.permissiongroup.STORAGE"
                                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="foo.bar2"
                    android:versionCode="1"
                    android:versionName="1.0" >

                    <uses-sdk android:minSdkVersion="14" />

                    <permission-group android:name="foo.permissiongroup.STORAGE"
                        android:label="@string/foo"
                        android:description="@string/foo" />
                    <permission-group android:name="bar.permissiongroup.STORAGE"
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
        strings,
      )
      .issues(ManifestDetector.UNIQUE_PERMISSION)
      .run()
      .expect(expected)
  }

  fun testDuplicatePermissionsMultiProject() {
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

                </manifest>
                """
            )
            .indented()
        )
        .type(ProjectDescription.Type.LIBRARY)
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
    lint()
      .projects(main, library)
      .incremental("App/AndroidManifest.xml")
      .issues(ManifestDetector.UNIQUE_PERMISSION)
      .run()
      .expect(
        """
                ../Library/AndroidManifest.xml:8: Error: Permission name SEND_SMS is not unique (appears in both foo.permission.SEND_SMS and bar.permission.SEND_SMS) [UniquePermission]
                    <permission android:name="bar.permission.SEND_SMS"
                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    AndroidManifest.xml:8: Previous permission here
                    <permission android:name="foo.permission.SEND_SMS"
                                              ~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
      )
  }

  fun testUniquePermissionsPrunedViaManifestRemove() {
    // Actually checks 4 separate things:
    // (1) The unique permission check looks across multiple projects via the
    //     manifest merge (in an incremental way)
    // (2) It allows duplicate permission names if the whole package, not just
    //     the base name is the same
    // (3) It flags permissions that vary by package name, not base name, across
    //     manifests
    // (4) It ignores permissions that have been removed via manifest merger
    //     directives. This is a regression test for
    //     https://code.google.com/p/android/issues/detail?id=227683
    // (5) Using manifest placeholders
    val library =
      project(
          manifest(
              """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg.library" >
                    <permission
                        android:name="a.b.c.SHARED_ACCESS"
                        android:label="Shared Access"
                        android:protectionLevel="signature"/>
                    <permission android:name="pkg1.PERMISSION_NAME_1"/>
                    <permission android:name="＄{applicationId}.permission.PERMISSION_NAME_2"/>
                    <permission android:name="＄{unknownPlaceHolder1}.permission.PERMISSION_NAME_3"/>
                </manifest>
                """
            )
            .indented()
        )
        .type(ProjectDescription.Type.LIBRARY)
        .name("Library")
    val main =
      project(
          manifest(
              """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="test.pkg.app" >
                    <permission
                        android:name="a.b.c.SHARED_ACCESS"
                        tools:node="remove"/>
                    <permission android:name="pkg2.PERMISSION_NAME_1"/>
                    <permission android:name="test.pkg.app.permission.PERMISSION_NAME_2"/>
                    <permission android:name="＄{unknownPlaceHolder2}.permission.PERMISSION_NAME_3"/>
                </manifest>
                """
            )
            .indented()
        )
        .name("App")
        .dependsOn(library)
    lint()
      .projects(main, library)
      .incremental("App/AndroidManifest.xml")
      .issues(ManifestDetector.UNIQUE_PERMISSION)
      .run()
      .expect(
        """
                ../Library/AndroidManifest.xml:7: Error: Permission name PERMISSION_NAME_1 is not unique (appears in both pkg2.PERMISSION_NAME_1 and pkg1.PERMISSION_NAME_1) [UniquePermission]
                    <permission android:name="pkg1.PERMISSION_NAME_1"/>
                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    AndroidManifest.xml:7: Previous permission here
                    <permission android:name="pkg2.PERMISSION_NAME_1"/>
                                              ~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
      )
  }

  fun testMissingVersion() {
    val expected =
      """
            AndroidManifest.xml:1: Warning: Should set android:versionCode to specify the application version [MissingVersion]
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
             ~~~~~~~~
            AndroidManifest.xml:1: Warning: Should set android:versionName to specify the application version [MissingVersion]
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
             ~~~~~~~~
            0 errors, 2 warnings
            """
    lint()
      .files(noVersion)
      .issues(ManifestDetector.SET_VERSION)
      .run()
      .expect(expected)
      .verifyFixes()
      .window(1)
      .expectFixDiffs(
        """
                Fix for AndroidManifest.xml line 1: Set versionCode:
                @@ -3 +3
                  <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                -     package="foo.bar2" >
                +     package="foo.bar2"
                +     android:versionCode="[TODO]|" >
                Fix for AndroidManifest.xml line 1: Set versionName:
                @@ -3 +3
                  <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                -     package="foo.bar2" >
                +     package="foo.bar2"
                +     android:versionName="[TODO]|" >
                """
      )
  }

  fun testVersionNotMissingInGradleProjects() {
    lint()
      .files(noVersion, library) // placeholder; only name counts
      .issues(ManifestDetector.SET_VERSION)
      .run()
      .expectClean()
  }

  fun testIllegalReference() {
    val expected =
      """
            AndroidManifest.xml:3: Warning: The android:versionCode cannot be a resource url, it must be a literal integer [IllegalResourceRef]
                android:versionCode="@dimen/versionCode"
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:6: Warning: The android:minSdkVersion cannot be a resource url, it must be a literal integer (or string if a preview codename) [IllegalResourceRef]
                <uses-sdk android:minSdkVersion="@dimen/minSdkVersion" android:targetSdkVersion="@dimen/targetSdkVersion" />
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:6: Warning: The android:targetSdkVersion cannot be a resource url, it must be a literal integer (or string if a preview codename) [IllegalResourceRef]
                <uses-sdk android:minSdkVersion="@dimen/minSdkVersion" android:targetSdkVersion="@dimen/targetSdkVersion" />
                                                                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 3 warnings
            """
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="foo.bar2"
                    android:versionCode="@dimen/versionCode"
                    android:versionName="@dimen/versionName" >

                    <uses-sdk android:minSdkVersion="@dimen/minSdkVersion" android:targetSdkVersion="@dimen/targetSdkVersion" />

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                        <activity
                            android:label="@string/app_name"
                            android:name=".Foo2Activity" >
                            <intent-filter >
                                <action android:name="android.intent.action.MAIN" />

                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """
          )
          .indented()
      ) // Looking for a version in the manifest that is replaced by the provisional test
      // infrastructure
      .skipTestModes(TestMode.PARTIAL)
      .issues(ManifestDetector.ILLEGAL_REFERENCE)
      .run()
      .expect(expected)
  }

  fun testDuplicateUsesFeature() {
    val expected =
      """
            AndroidManifest.xml:9: Warning: Duplicate declaration of uses-feature android.hardware.camera [DuplicateUsesFeature]
                <uses-feature android:name="android.hardware.camera"/>
                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="foo.bar2"
                    android:versionCode="1"
                    android:versionName="1.0">

                    <uses-sdk android:minSdkVersion="14" />

                    <uses-feature android:name="android.hardware.camera"/>
                    <uses-feature android:name="android.hardware.camera"/>

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                    </application>

                </manifest>
                """
          )
          .indented(),
        strings,
      )
      .issues(ManifestDetector.DUPLICATE_USES_FEATURE)
      .run()
      .expect(expected)
  }

  fun testDuplicateUsesFeatureOk() {
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="foo.bar2"
                    android:versionCode="1"
                    android:versionName="1.0">

                    <uses-sdk android:minSdkVersion="14" />

                    <uses-feature android:name="android.hardware.camera"/>

                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                    </application>

                </manifest>
                """
          )
          .indented(),
        strings,
      )
      .issues(ManifestDetector.DUPLICATE_USES_FEATURE)
      .run()
      .expectClean()
  }

  fun testMissingApplicationIcon() {
    val expected =
      """
            AndroidManifest.xml:8: Warning: Should explicitly set android:icon, there is no default [MissingApplicationIcon]
                <application
                 ~~~~~~~~~~~
            0 errors, 1 warnings
            """
    lint()
      .files(missingApplicationIcon, strings)
      .issues(ManifestDetector.APPLICATION_ICON)
      .run()
      .expect(expected)
      .verifyFixes()
      .window(1)
      .expectFixDiffs(
        """
                Fix for AndroidManifest.xml line 8: Set icon:
                @@ -9 +9

                -     <application android:label="@string/app_name" >
                +     <application
                +         android:icon="@mipmap/|"
                +         android:label="@string/app_name" >
                          <activity
                """
      )
  }

  fun testMissingApplicationIconInLibrary() {
    lint()
      .files(missingApplicationIcon, projectProperties().library(true), strings)
      .issues(ManifestDetector.APPLICATION_ICON)
      .run()
      .expectClean()
  }

  fun testMissingApplicationIconOk() {
    lint()
      .files(manifest().minSdk(14), strings)
      .issues(ManifestDetector.APPLICATION_ICON)
      .run()
      .expectClean()
  }

  fun testDeviceAdmin() {
    val expected =
      """
            AndroidManifest.xml:30: Warning: You must have an intent filter for action android.app.action.DEVICE_ADMIN_ENABLED [DeviceAdmin]
                        <meta-data android:name="android.app.device_admin"
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:43: Warning: You must have an intent filter for action android.app.action.DEVICE_ADMIN_ENABLED [DeviceAdmin]
                        <meta-data android:name="android.app.device_admin"
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:55: Warning: You must have an intent filter for action android.app.action.DEVICE_ADMIN_ENABLED [DeviceAdmin]
                        <meta-data android:name="android.app.device_admin"
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 3 warnings
            """
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                          xmlns:tools="http://schemas.android.com/tools"
                          package="foo.bar2"
                          android:versionCode="1"
                          android:versionName="1.0">

                    <uses-sdk android:minSdkVersion="14"/>

                    <application
                            android:icon="@drawable/ic_launcher"
                            android:label="@string/app_name">

                        <!-- OK -->
                        <receiver android:name=".DeviceAdminTestReceiver"
                                  android:label="@string/app_name"
                                  android:description="@string/app_name"
                                  android:permission="android.permission.BIND_DEVICE_ADMIN">
                            <meta-data android:name="android.app.device_admin"
                                       android:resource="@xml/device_admin"/>
                            <intent-filter>
                                <action android:name="android.app.action.DEVICE_ADMIN_ENABLED"/>
                            </intent-filter>
                        </receiver>

                        <!-- Specifies data -->
                        <receiver android:name=".DeviceAdminTestReceiver"
                                  android:label="@string/app_name"
                                  android:description="@string/app_name"
                                  android:permission="android.permission.BIND_DEVICE_ADMIN">
                            <meta-data android:name="android.app.device_admin"
                                       android:resource="@xml/device_admin"/>
                            <intent-filter>
                                <action android:name="android.app.action.DEVICE_ADMIN_ENABLED"/>
                                <data android:scheme="content" />
                            </intent-filter>
                        </receiver>

                        <!-- Missing right intent-filter -->
                        <receiver android:name=".DeviceAdminTestReceiver"
                                  android:label="@string/app_name"
                                  android:description="@string/app_name"
                                  android:permission="android.permission.BIND_DEVICE_ADMIN">
                            <meta-data android:name="android.app.device_admin"
                                       android:resource="@xml/device_admin"/>
                            <intent-filter>
                                <action android:name="com.test.foo.DEVICE_ADMIN_ENABLED"/>
                            </intent-filter>
                        </receiver>

                        <!-- Missing intent-filter -->
                        <receiver android:name=".DeviceAdminTestReceiver"
                                  android:label="@string/app_name"
                                  android:description="@string/app_name"
                                  android:permission="android.permission.BIND_DEVICE_ADMIN">
                            <meta-data android:name="android.app.device_admin"
                                       android:resource="@xml/device_admin"/>
                        </receiver>

                        <!-- Suppressed -->
                        <receiver android:name=".DeviceAdminTestReceiver"
                                  android:label="@string/app_name"
                                  android:description="@string/app_name"
                                  android:permission="android.permission.BIND_DEVICE_ADMIN"
                                  tools:ignore="DeviceAdmin">
                            <meta-data android:name="android.app.device_admin"
                                       android:resource="@xml/device_admin"/>
                            <intent-filter>
                                <action android:name="com.test.foo.DEVICE_ADMIN_ENABLED"/>
                            </intent-filter>
                        </receiver>

                    </application>

                </manifest>
                """
          )
          .indented()
      )
      .issues(ManifestDetector.DEVICE_ADMIN)
      .run()
      .expect(expected)
  }

  fun testMockLocations() {
    val expected =
      """
            src/main/AndroidManifest.xml:8: Error: Mock locations should only be requested in a test or debug-specific manifest file (typically src/debug/AndroidManifest.xml) [MockLocation]
                <uses-permission android:name="android.permission.ACCESS_MOCK_LOCATION" />
                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
    lint()
      .files(
        xml(
            "src/main/AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="foo.bar2"
                    android:versionCode="1"
                    android:versionName="1.0" >

                    <uses-sdk android:minSdkVersion="14" />
                    <uses-permission android:name="com.example.helloworld.permission" />
                    <uses-permission android:name="android.permission.ACCESS_MOCK_LOCATION" />

                </manifest>
                """,
          )
          .indented(),
        xml(
            "src/debug/AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="foo.bar2"
                    android:versionCode="1"
                    android:versionName="1.0" >

                    <uses-sdk android:minSdkVersion="14" />
                    <uses-permission android:name="com.example.helloworld.permission" />
                    <uses-permission android:name="android.permission.ACCESS_MOCK_LOCATION" />

                </manifest>
                """,
          )
          .indented(),
        xml(
            "src/test/AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="foo.bar2"
                    android:versionCode="1"
                    android:versionName="1.0" >

                    <uses-sdk android:minSdkVersion="14" />
                    <uses-permission android:name="com.example.helloworld.permission" />
                    <uses-permission android:name="android.permission.ACCESS_MOCK_LOCATION" />

                </manifest>
                """,
          )
          .indented(),
        gradle(
            """
                android {
                    compileSdkVersion 25
                    defaultConfig {
                        applicationId "com.android.tools.test"
                        minSdkVersion 5
                        targetSdkVersion 16
                        versionCode 2
                        versionName "MyName"
                    }
                }
                """
          )
          .indented(),
      )
      .issues(ManifestDetector.MOCK_LOCATION)
      .run()
      .expect(expected)

    // TODO: When we have an instantiatable gradle model, test with real model and verify
    // that a manifest file in a debug build type does not get flagged.
  }

  fun testMockLocationsOk() {
    lint()
      .files( // Not a Gradle project
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="foo.bar2"
                    android:versionCode="1"
                    android:versionName="1.0" >

                    <uses-sdk android:minSdkVersion="14" />
                    <uses-permission android:name="com.example.helloworld.permission" />
                    <uses-permission android:name="android.permission.ACCESS_MOCK_LOCATION" />

                </manifest>
                """
          )
          .indented()
      )
      .issues(ManifestDetector.MOCK_LOCATION)
      .run()
      .expectClean()
  }

  fun testGradleOverrides() {
    val expected =
      """
            src/main/AndroidManifest.xml:6: Warning: This minSdkVersion value (14) is not used; it is always overridden by the value specified in the Gradle build script (5) [GradleOverrides]
                <uses-sdk android:minSdkVersion="14" android:targetSdkVersion="17" />
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/main/AndroidManifest.xml:6: Warning: This targetSdkVersion value (17) is not used; it is always overridden by the value specified in the Gradle build script (16) [GradleOverrides]
                <uses-sdk android:minSdkVersion="14" android:targetSdkVersion="17" />
                                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
    lint()
      .files(
        xml("src/main/" + gradleOverride.targetRelativePath, gradleOverride.contents),
        gradle(
            """
                android {
                    compileSdkVersion 25
                    defaultConfig {
                        applicationId "com.android.tools.test"
                        minSdkVersion 5
                        targetSdkVersion 16
                        versionCode 2
                        versionName "MyName"
                    }
                }
                """
          )
          .indented(),
      )
      .issues(
        ManifestDetector.GRADLE_OVERRIDES
      ) // Exclude because the testing framework for partial analysis will
      // change a string in the error message that is just a manifestation
      // of the way it mutates the project (to lower the minSdkVersion)
      .skipTestModes(TestMode.PARTIAL)
      .run()
      .expect(expected)
  }

  fun testGradleOverridesOk() {
    lint()
      .files(gradleOverride, gradle("android {\n}"))
      .issues(ManifestDetector.GRADLE_OVERRIDES)
      .run()
      .expectClean()
  }

  fun testGradleOverrideManifestMergerOverride() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=186762
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="test.pkg">

                    <uses-sdk android:minSdkVersion="14" tools:overrideLibrary="lib.pkg" />

                </manifest>
                """
          )
          .indented(),
        projectProperties().library(true),
        gradle(
            """
                android {
                    compileSdkVersion 25
                    defaultConfig {
                        applicationId "com.android.tools.test"
                        minSdkVersion 5
                        targetSdkVersion 16
                        versionCode 2
                        versionName "MyName"
                    }
                }
                """
          )
          .indented(),
      )
      .issues(ManifestDetector.GRADLE_OVERRIDES)
      .run()
      .expectClean()
  }

  fun testManifestPackagePlaceholder() {
    val expected =
      """
            src/main/AndroidManifest.xml:2: Warning: Cannot use placeholder for the package in the manifest; set applicationId in build.gradle instead [GradleOverrides]
                package="＄{packageName}" >
                ~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="＄{packageName}" >
                    <uses-sdk android:minSdkVersion="14" android:targetSdkVersion="17" />
                    <application
                        android:icon="@drawable/ic_launcher"
                        android:label="@string/app_name" >
                    </application>
                </manifest>
                """
          )
          .indented(),
        gradle("android {\n}"),
      )
      .issues(ManifestDetector.GRADLE_OVERRIDES)
      .run()
      .expect(expected)
  }

  fun testMipMap() {
    lint().files(mipmap).issues(ManifestDetector.MIPMAP).run().expectClean()
  }

  fun testMipMapWithDensityFiltering() {
    val expected =
      """
            src/main/AndroidManifest.xml:8: Warning: Should use @mipmap instead of @drawable for launcher icons [MipmapIcons]
                    android:icon="@drawable/ic_launcher"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/main/AndroidManifest.xml:13: Warning: Should use @mipmap instead of @drawable for launcher icons [MipmapIcons]
                        android:icon="@drawable/activity1"
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
    lint()
      .files(
        mipmap,
        gradle(
            """
                android {
                    defaultConfig {
                        resConfigs "cs"
                    }
                    flavorDimensions  "pricing", "releaseType"
                    productFlavors {
                        beta {
                            dimension "releaseType"
                            resConfig "en", "de"
                            resConfigs "nodpi", "hdpi"
                        }
                        normal { dimension "releaseType" }
                        free { dimension "pricing" }
                        paid { dimension "pricing" }
                    }
                }
                """
          )
          .indented(),
      )
      .issues(ManifestDetector.MIPMAP)
      .variant("freeBetaDebug")
      .run()
      .expect(expected)
  }

  fun testFullBackupContentBoolean() {
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:fullBackupContent="true"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                    </application>

                </manifest>
                """
          )
          .indented()
      )
      .issues(ManifestDetector.DATA_EXTRACTION_RULES)
      .incremental()
      .run()
      .expectClean()
  }

  fun testFullBackupContentMissingInLibrary() {
    lint()
      .files(
        projectProperties().library(true),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:fullBackupContent="@xml/backup"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                    </application>

                </manifest>
                """
          )
          .indented(),
      )
      .issues(ManifestDetector.DATA_EXTRACTION_RULES)
      .incremental("AndroidManifest.xml")
      .run()
      .expectClean()
  }

  fun testFullBackupContentOk() {
    lint()
      .files(
        projectProperties().library(true),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:fullBackupContent="@xml/backup"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                    </application>

                </manifest>
                """
          )
          .indented(),
        xml(
            "res/xml/backup.xml",
            """
                <full-backup-content>
                     <include domain="file" path="dd"/>
                     <exclude domain="file" path="dd/fo3o.txt"/>
                     <exclude domain="file" path="dd/ss/foo.txt"/>
                </full-backup-content>
                """,
          )
          .indented(),
      )
      .issues(ManifestDetector.DATA_EXTRACTION_RULES)
      .incremental("AndroidManifest.xml")
      .run()
      .expectClean()
  }

  fun testHasBackupSpecifiedInTarget23() {
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >
                    <uses-sdk android:targetSdkVersion="23" />
                    <application
                        android:allowBackup="true"
                        android:fullBackupContent="no"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                    </application>

                </manifest>
                """
          )
          .indented()
      )
      .issues(ManifestDetector.DATA_EXTRACTION_RULES)
      .run()
      .expectClean()
  }

  fun testMissingFullContentBackupInTarget23() {
    // No longer flagging this; it's noisy and many users just suppress it
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >
                    <uses-sdk android:targetSdkVersion="23" />
                    <application
                        android:allowBackup="true"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                    </application>

                </manifest>
                """
          )
          .indented()
      )
      .issues(ManifestDetector.DATA_EXTRACTION_RULES)
      .run()
      .expectClean()
  }

  fun testMissingFullContentBackupInPreTarget23() {
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >
                    <uses-sdk android:targetSdkVersion="21" />
                    <application
                        android:allowBackup="true"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                    </application>

                </manifest>
                """
          )
          .indented()
      )
      .issues(ManifestDetector.DATA_EXTRACTION_RULES)
      .run()
      .expectClean()
  }

  fun testMissingFullContentBackupWithoutGcmPreTarget23() {
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >
                    <uses-sdk android:targetSdkVersion="21" />
                    <application
                        android:allowBackup="true"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                    </application>

                </manifest>
                """
          )
          .indented()
      )
      .issues(ManifestDetector.DATA_EXTRACTION_RULES)
      .run()
      .expectClean()
  }

  fun testMissingFullContentBackupWithoutGcmPostTarget23() {
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >
                    <uses-sdk android:targetSdkVersion="23" />
                    <application
                        android:allowBackup="true"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                    </application>

                </manifest>
                """
          )
          .indented()
      )
      .issues(ManifestDetector.DATA_EXTRACTION_RULES)
      .run()
      .expectClean()
  }

  fun testMissingFullContentBackupWithGcmPreTarget23() {
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >
                    <uses-sdk android:targetSdkVersion="21" />
                    <application
                        android:allowBackup="true"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >        <receiver
                            android:name=".GcmBroadcastReceiver"
                            android:permission="com.google.android.c2dm.permission.SEND" >
                            <intent-filter>
                                <action android:name="com.google.android.c2dm.intent.RECEIVE" />
                                <category android:name="com.example.gcm" />
                            </intent-filter>
                        </receiver>
                    </application>

                </manifest>
                """
          )
          .indented()
      )
      .issues(ManifestDetector.DATA_EXTRACTION_RULES)
      .run()
      .expectClean()
  }

  fun testMissingFullContentBackupWithGcmPostTarget23() {
    // No longer flagging this; it's noisy and many users just suppress it
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >
                    <uses-sdk android:targetSdkVersion="23" />
                    <application
                        android:allowBackup="true"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >        <receiver
                            android:name=".GcmBroadcastReceiver"
                            android:permission="com.google.android.c2dm.permission.SEND" >
                            <intent-filter>
                                <action android:name="com.google.android.c2dm.intent.RECEIVE" />
                                <category android:name="com.example.gcm" />
                            </intent-filter>
                        </receiver>
                    </application>

                </manifest>
                """
          )
          .indented()
      )
      .issues(ManifestDetector.DATA_EXTRACTION_RULES)
      .run()
      .expectClean()
  }

  fun testNoMissingFullBackupWithDoNotAllowBackup() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=181805
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >
                    <uses-sdk android:targetSdkVersion="21" />
                    <application
                        android:label="@string/app_name"
                        android:allowBackup="false"
                        android:theme="@style/AppTheme" >        <receiver
                            android:name=".GcmBroadcastReceiver"
                            android:permission="com.google.android.c2dm.permission.SEND" >
                            <intent-filter>
                                <action android:name="com.google.android.c2dm.intent.RECEIVE" />
                                <category android:name="com.example.gcm" />
                            </intent-filter>
                        </receiver>
                    </application>

                </manifest>
                """
          )
          .indented()
      )
      .issues(ManifestDetector.DATA_EXTRACTION_RULES)
      .run()
      .expectClean()
  }

  fun testFullBackupContentMissingIgnored() {
    // Make sure now that we look at the merged manifest that we correctly handle tools:ignore
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="com.example.helloworld" >

                    <application
                        tools:ignore="DataExtractionRules"
                        android:allowBackup="true"
                        android:fullBackupContent="@xml/backup"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                    </application>

                </manifest>
                """
          )
          .indented()
      )
      .issues(ManifestDetector.DATA_EXTRACTION_RULES)
      .incremental()
      .run()
      .expectClean()
  }

  fun testBackupAttributeFromMergedManifest() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=236584
    // Library project specifies backup descriptor, main project does not.
    val library =
      project(
          manifest(
              """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg.library" >
                    <uses-sdk android:targetSdkVersion="23" />
                    <application
                        android:allowBackup="true"
                        android:fullBackupContent="@xml/backup">
                    </application>

                </manifest>
                """
            )
            .indented(),
          xml(
              "res/xml/backup.xml",
              """
                <full-backup-content>
                     <include domain="file" path="dd"/>
                     <exclude domain="file" path="dd/fo3o.txt"/>
                     <exclude domain="file" path="dd/ss/foo.txt"/>
                </full-backup-content>
                """,
            )
            .indented(),
        )
        .type(ProjectDescription.Type.LIBRARY)
        .name("LibraryProject")
    val main =
      project(
          manifest(
              """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg.app" >
                    <uses-sdk android:targetSdkVersion="23" />
                    <application
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                    </application>

                </manifest>
                """
            )
            .indented()
        )
        .dependsOn(library)
    lint()
      .projects(main, library)
      .issues(ManifestDetector.DATA_EXTRACTION_RULES)
      .run()
      .expectClean()
  }

  fun testWearableBindListener() {
    val expected =
      """
            src/main/AndroidManifest.xml:10: Error: The com.google.android.gms.wearable.BIND_LISTENER action is deprecated [WearableBindListener]
                              <action android:name="com.google.android.gms.wearable.BIND_LISTENER" />
                                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
    lint()
      .files(
        xml(
            "src/main/AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >
                    <uses-sdk android:targetSdkVersion="22" />
                    <application
                        android:label="@string/app_name"
                        android:allowBackup="false"
                        android:theme="@style/AppTheme" >
                        <service android:name=".WearMessageListenerService">
                              <intent-filter>
                                  <action android:name="com.google.android.gms.wearable.BIND_LISTENER" />
                              </intent-filter>
                        </service>
                    </application>

                </manifest>
                """,
          )
          .indented(),
        gradle(
            """
                apply plugin: 'com.android.application'

                dependencies {
                    compile 'com.google.android.gms:play-services-wearable:8.4.0'
                }
                """
          )
          .indented(),
      )
      .issues(ManifestDetector.WEARABLE_BIND_LISTENER)
      .run()
      .expect(expected)
  }

  // No warnings here because the variant points to a gms dependency version 8.1.0
  fun testWearableBindListenerNoWarn() {
    lint()
      .files(
        xml(
            "src/main/AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >
                    <uses-sdk android:targetSdkVersion="22" />
                    <application
                        android:label="@string/app_name"
                        android:allowBackup="false"
                        android:theme="@style/AppTheme" >
                        <service android:name=".WearMessageListenerService">
                              <intent-filter>
                                  <action android:name="com.google.android.gms.wearable.BIND_LISTENER" />
                              </intent-filter>
                        </service>
                    </application>

                </manifest>
                """,
          )
          .indented(),
        gradle(
            """
                apply plugin: 'com.android.application'

                android {
                    compileSdkVersion 19
                }
                dependencies {
                    compile 'com.google.android.gms:play-services-wearable:8.1.+'
                }
                """
          )
          .indented(),
      )
      .issues(ManifestDetector.WEARABLE_BIND_LISTENER)
      .run()
      .expectClean()
  }

  fun testWearableBindListenerCompileSdk24() {
    val expected =
      """
            src/main/AndroidManifest.xml:10: Error: The com.google.android.gms.wearable.BIND_LISTENER action is deprecated. Please upgrade to the latest available version of play-services-wearable: 8.4.0 [WearableBindListener]
                              <action android:name="com.google.android.gms.wearable.BIND_LISTENER" />
                                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
    lint()
      .files(
        // When not specifying compileSdkVersion, it will always be >= 24 (so we don't need to pick
        // a specific one)
        xml(
            "src/main/AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >
                    <uses-sdk android:targetSdkVersion="22" />
                    <application
                        android:label="@string/app_name"
                        android:allowBackup="false"
                        android:theme="@style/AppTheme" >
                        <service android:name=".WearMessageListenerService">
                              <intent-filter>
                                  <action android:name="com.google.android.gms.wearable.BIND_LISTENER" />
                              </intent-filter>
                        </service>
                    </application>

                </manifest>
                """,
          )
          .indented(),
        gradle(
            """
                apply plugin: 'com.android.application'

                dependencies {
                    compile 'com.google.android.gms:play-services-wearable:8.1.+'
                }
                """
          )
          .indented(),
      )
      .issues(
        ManifestDetector.WEARABLE_BIND_LISTENER
      ) // This test uses a mock SDK home to ensure that the latest expected
      // version is 8.4.0 rather than whatever happens to actually be the
      // latest version at the time (such as 9.6.1 at the moment of this writing)
      .sdkHome(mockSupportLibraryInstallation)
      // the mock support installation doesn't contain an actual android.jar etc
      .requireCompileSdk(false)
      .run()
      .expect(expected)
  }

  fun testAppIndexingNoWarn() {
    lint()
      .files(
        manifest(
            "src/main/AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >
                    <uses-sdk android:targetSdkVersion="25" />
                    <application
                        android:label="@string/app_name"
                        android:allowBackup="false"
                        android:theme="@style/AppTheme" >
                        <service android:name=".MyService">
                              <intent-filter>
                                  <action android:name="com.google.firebase.appindexing.UPDATE_INDEX" />
                              </intent-filter>
                        </service>
                    </application>

                </manifest>
                """,
          )
          .indented(),
        gradle(
            """
                apply plugin: 'com.android.application'

                dependencies {
                    compile 'com.google.firebase:firebase-appindexing:11.0.4'
                }
                """
          )
          .indented(),
      )
      .issues(ManifestDetector.APP_INDEXING_SERVICE)
      .run()
      .expectClean()
  }

  fun testAppIndexingTargetSdk26() {
    val expected =
      """
            src/main/AndroidManifest.xml:10: Warning: UPDATE_INDEX is configured as a service in your app, which is no longer supported for the API level you're targeting. Use a BroadcastReceiver instead. [AppIndexingService]
                              <action android:name="com.google.firebase.appindexing.UPDATE_INDEX" />
                                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
    lint()
      .files(
        manifest(
            "src/main/AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >
                    <uses-sdk android:targetSdkVersion="26" />
                    <application
                        android:label="@string/app_name"
                        android:allowBackup="false"
                        android:theme="@style/AppTheme" >
                        <service android:name=".MyService">
                              <intent-filter>
                                  <action android:name="com.google.firebase.appindexing.UPDATE_INDEX" />
                              </intent-filter>
                        </service>
                    </application>

                </manifest>
                """,
          )
          .indented(),
        gradle(
            """
                apply plugin: 'com.android.application'

                dependencies {
                    compile 'com.google.firebase:firebase-appindexing:11.0.4'
                }
                """
          )
          .indented(),
      )
      .issues(ManifestDetector.APP_INDEXING_SERVICE)
      .run()
      .expect(expected)
  }

  fun testVersionCodeNotRequiredInLibraries() {
    // Regression test for b/144803800
    lint()
      .files(
        projectProperties().library(true),
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >
                    <uses-sdk android:targetSdkVersion="26" />
                    <application
                        android:label="@string/app_name"
                        android:allowBackup="false"
                        android:theme="@style/AppTheme" >
                        <service android:name=".MyService">
                              <intent-filter>
                                  <action android:name="com.google.firebase.appindexing.UPDATE_INDEX" />
                              </intent-filter>
                        </service>
                    </application>

                </manifest>
                """
          )
          .indented(),
      )
      .issues(ManifestDetector.SET_VERSION)
      .run()
      .expectClean()
  }

  fun testProviderTag() {
    // Regression test for b/154309642
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >
                    <provider android:authorities="com.example.provider" /><!-- ERROR -->
                    <application>
                        <provider android:authorities="com.example.provider" /><!-- OK -->
                    </application>
                    <queries>
                        <provider android:authorities="com.example.provider" /><!-- OK -->
                    </queries>
                </manifest>
                """
          )
          .indented()
      )
      .issues(ManifestDetector.WRONG_PARENT)
      .run()
      .expect(
        """
                AndroidManifest.xml:3: Error: The <provider> element must be a direct child of the <application> element or the <queries> element [WrongManifestParent]
                    <provider android:authorities="com.example.provider" /><!-- ERROR -->
                     ~~~~~~~~
                1 errors, 0 warnings
                """
      )
  }

  fun testDataExtractionRules1() {
    // allowBackup disabled and dataExtractionRules not present
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                  <uses-sdk android:minSdkVersion="28" android:targetSdkVersion="31" />
                  <application android:allowBackup="false">
                  </application>
                </manifest>
                """
          )
          .indented()
      )
      .issues(ManifestDetector.DATA_EXTRACTION_RULES)
      .run()
      .expect(
        """
            AndroidManifest.xml:3: Warning: The attribute android:allowBackup is deprecated from Android 12 and higher and may be removed in future versions. Consider adding the attribute android:dataExtractionRules specifying an @xml resource which configures cloud backups and device transfers on Android 12 and higher. [DataExtractionRules]
              <application android:allowBackup="false">
                                                ~~~~~
            0 errors, 1 warnings
            """
      )
  }

  fun testDataExtractionMigrateFullBackupContent() {
    // fullBackupContent set and dataExtractionRules not present
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                  <uses-sdk android:minSdkVersion="28" android:targetSdkVersion="31" />
                  <application
                      android:allowBackup="true"
                      android:fullBackupContent="@xml/full_backup_content">
                  </application>
                </manifest>
                """
          )
          .indented()
          .indented(),
        fullBackup,
      )
      .issues(ManifestDetector.DATA_EXTRACTION_RULES)
      .run()
      .expect(
        """
            AndroidManifest.xml:5: Warning: The attribute android:fullBackupContent is deprecated from Android 12 and higher and may be removed in future versions. Consider adding the attribute android:dataExtractionRules specifying an @xml resource which configures cloud backups and device transfers on Android 12 and higher. [DataExtractionRules]
                  android:fullBackupContent="@xml/full_backup_content">
                                             ~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
      )
      .expectFixDiffs(
        """
            Fix for AndroidManifest.xml line 5: Create data_extraction_rules.xml:
            @@ -11 +11
            +         android:dataExtractionRules="@xml/data_extraction_rules"
            res/xml/data_extraction_rules.xml:
            @@ -1 +1
            + <data-extraction-rules>
            +     <cloud-backup>
            +          |<include domain="file" path="dd"/>
            +          <exclude domain="file" path="dd/fo3o.txt"/>
            +          <exclude domain="file" path="dd/ss/foo.txt"/>
            +     </cloud-backup>
            + </data-extraction-rules>
            """
      )
  }

  fun testFullContentMigration() {
    // fullBackupContent set and dataExtractionRules not present; quickfix should migrate existing
    // rules
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                  <uses-sdk android:minSdkVersion="28" android:targetSdkVersion="31" />
                  <application
                      android:allowBackup="true"
                      android:fullBackupContent="@xml/full_backup_content">
                  </application>
                </manifest>
                """
          )
          .indented()
          .indented(),
        xml(
            "res/xml/full_backup_content.xml",
            """
                <!-- Our copyright here -->
                <full-backup-content>
                     <!-- Some comment -->
                     <include domain="file" path="dd"/>
                     <exclude domain="file" path="dd/fo3o.txt" requireFlags="deviceToDeviceTransfer"/>
                     <exclude domain="file" path="dd/fo3o2.txt" requireFlags="clientSideEncryption"/>
                     <exclude domain="file" path="dd/fo3o3.txt"/>
                     <exclude domain="file" path="dd/ss/foo.txt" requireFlags="deviceToDeviceTransfer"/>
                     <!--<exclude domain="file" path="dd/ss/foo.txt" requireFlags="clientSideEncryption|deviceToDeviceTransfer" />-->
                     <!-- Final comment -->
                </full-backup-content>
                """,
          )
          .indented(),
      )
      .issues(ManifestDetector.DATA_EXTRACTION_RULES)
      .run()
      .expect(
        """
            AndroidManifest.xml:5: Warning: The attribute android:fullBackupContent is deprecated from Android 12 and higher and may be removed in future versions. Consider adding the attribute android:dataExtractionRules specifying an @xml resource which configures cloud backups and device transfers on Android 12 and higher. [DataExtractionRules]
                  android:fullBackupContent="@xml/full_backup_content">
                                             ~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
      )
      .expectFixDiffs(
        """
            Fix for AndroidManifest.xml line 5: Create data_extraction_rules.xml:
            @@ -11 +11
            +         android:dataExtractionRules="@xml/data_extraction_rules"
            res/xml/data_extraction_rules.xml:
            @@ -1 +1
            + <!-- Our copyright here -->
            + <data-extraction-rules>
            +     <cloud-backup disableIfNoEncryptionCapabilities="true">
            +          <!-- Some comment -->
            +          |<include domain="file" path="dd"/>
            +          <!-- <exclude domain="file" path="dd/fo3o.txt" requireFlags="deviceToDeviceTransfer"/> -->
            +          <exclude domain="file" path="dd/fo3o2.txt" requireFlags="clientSideEncryption"/>
            +          <exclude domain="file" path="dd/fo3o3.txt"/>
            +          <!-- <exclude domain="file" path="dd/ss/foo.txt" requireFlags="deviceToDeviceTransfer"/> -->
            +          <!--<exclude domain="file" path="dd/ss/foo.txt" requireFlags="clientSideEncryption|deviceToDeviceTransfer" />-->
            +          <!-- Final comment -->
            +     </cloud-backup>
            + </data-extraction-rules>
            """
      )
  }

  fun testDataExtractionRulesRemove() {
    // only allowBackup set to false and dataExtractionRules not present; create default contents
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                  <uses-sdk android:minSdkVersion="28" android:targetSdkVersion="31" />
                  <application
                      android:allowBackup="false">
                  </application>
                </manifest>
                """
          )
          .indented()
          .indented(),
        fullBackup,
      )
      .issues(ManifestDetector.DATA_EXTRACTION_RULES)
      .run()
      .expect(
        """
            AndroidManifest.xml:4: Warning: The attribute android:allowBackup is deprecated from Android 12 and higher and may be removed in future versions. Consider adding the attribute android:dataExtractionRules specifying an @xml resource which configures cloud backups and device transfers on Android 12 and higher. [DataExtractionRules]
                  android:allowBackup="false">
                                       ~~~~~
            0 errors, 1 warnings
            """
      )
      .expectFixDiffs(
        """
            Fix for AndroidManifest.xml line 4: Create data_extraction_rules.xml:
            @@ -9 +9
            -     <application android:allowBackup="false" >
            +     <application
            +         android:allowBackup="false"
            +         android:dataExtractionRules="@xml/data_extraction_rules" >
            res/xml/data_extraction_rules.xml:
            @@ -1 +1
            + <?xml version="1.0" encoding="utf-8"?>
            + <!--
            +    Sample data extraction rules file; uncomment and customize as necessary.
            +    See https://developer.android.com/about/versions/12/backup-restore#xml-changes
            +    for details.
            + -->
            + <data-extraction-rules>
            +     <cloud-backup>
            +         <!--
            +         |TODO: Use <include> and <exclude> to control what is backed up.
            +         The domain can be file, database, sharedpref, external or root.
            +         Examples:
            +
            +         <include domain="file" path="file_to_include"/>
            +         <exclude domain="file" path="file_to_exclude"/>
            +         <include domain="file" path="include_folder"/>
            +         <exclude domain="file" path="include_folder/file_to_exclude"/>
            +         <exclude domain="file" path="exclude_folder"/>
            +         <include domain="file" path="exclude_folder/file_to_include"/>
            +
            +         <include domain="sharedpref" path="include_shared_pref1.xml"/>
            +         <include domain="database" path="db_name/file_to_include"/>
            +         <exclude domain="database" path="db_name/include_folder/file_to_exclude"/>
            +         <include domain="external" path="file_to_include"/>
            +         <exclude domain="external" path="file_to_exclude"/>
            +         <include domain="root" path="file_to_include"/>
            +         <exclude domain="root" path="file_to_exclude"/>
            +         -->
            +     </cloud-backup>
            +     <!--
            +     <device-transfer>
            +         <include .../>
            +         <exclude .../>
            +     </device-transfer>
            +     -->
            + </data-extraction-rules>
            """
      )
  }

  fun disabled_testDataExtractionRules3() {
    // See TEMPORARILY DISABLED comment in ManifestDetector: not yet enforced
    // allowBackup set when min SDK is S+
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                  <uses-sdk android:minSdkVersion="31" android:targetSdkVersion="31" />
                  <application
                          android:allowBackup="true"
                          android:dataExtractionRules="@xml/data_extraction_rules">
                  </application>
                </manifest>
                """
          )
          .indented(),
        dataExtractionRules,
      )
      .issues(ManifestDetector.DATA_EXTRACTION_RULES)
      .run()
      .expect(
        """
            AndroidManifest.xml:4: Warning: This attribute is unused; dataExtractionRules will take precedence since minSdkVersion is 31 or higher [DataExtractionRules]
                      android:allowBackup="true"
                                           ~~~~
            0 errors, 1 warnings
            """
      )
  }

  fun ignored_testDataExtractionRules4() {
    // See TEMPORARILY DISABLED comment in ManifestDetector: not yet enforced
    // fullBackupContent set when min SDK is S+
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                  <uses-sdk android:minSdkVersion="31" android:targetSdkVersion="31" />
                  <application
                      android:allowBackup="true"
                      android:fullBackupContent="@xml/full_backup_content"
                      android:dataExtractionRules="@xml/data_extraction_rules">
                  </application>
                </manifest>
                """
          )
          .indented(),
        fullBackup,
        dataExtractionRules,
      )
      .issues(ManifestDetector.DATA_EXTRACTION_RULES)
      .run()
      .expect(
        """
            AndroidManifest.xml:4: Warning: This attribute is unused; dataExtractionRules will take precedence since minSdkVersion is 31 or higher [DataExtractionRules]
                  android:allowBackup="true"
                                       ~~~~
            AndroidManifest.xml:5: Warning: This attribute is unused; dataExtractionRules will take precedence since minSdkVersion is 31 or higher [DataExtractionRules]
                  android:fullBackupContent="@xml/full_backup_content"
                                             ~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 2 warnings
            """
      )
  }

  fun testDataExtractionWithoutFullBackupContent() {
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">
                  <uses-sdk android:minSdkVersion="29" android:targetSdkVersion="35" />
                  <application android:dataExtractionRules="@xml/data_extraction_rules" />
                </manifest>
                """
          )
          .indented(),
        dataExtractionRules,
      )
      .issues(ManifestDetector.DATA_EXTRACTION_RULES)
      .run()
      .expect(
        """
            AndroidManifest.xml:4: Warning: The attribute android:dataExtractionRules only applies for Android 12 and higher; since minSdkVersion is API 29 you should also set android:fullBackupContent [DataExtractionRules]
              <application android:dataExtractionRules="@xml/data_extraction_rules" />
                                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
      )
  }

  fun testNoAllowBackupWithBuildApi31() {
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                    <uses-sdk android:minSdkVersion="25" android:targetSdkVersion="29" />
                    <application>
                    </application>
                </manifest>
                """
          )
          .indented()
      )
      .issues(ManifestDetector.DATA_EXTRACTION_RULES)
      .run()
      .expectClean()
  }

  fun testRedundantLabelOnActivity() {
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.lb.myapplication">

                    <application
                        android:allowBackup="true" android:icon="@mipmap/ic_launcher" android:label="@string/app_name"
                        android:roundIcon="@mipmap/ic_launcher_round" android:supportsRtl="true"
                        android:theme="@style/Theme.MyApplication">
                        <activity android:name=".MainActivity" android:label="@string/app_name">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />

                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """
          )
          .indented()
      )
      .issues(ManifestDetector.REDUNDANT_LABEL)
      .run()
      .expect(
        """
            AndroidManifest.xml:7: Warning: Redundant label can be removed [RedundantLabel]
                    <activity android:name=".MainActivity" android:label="@string/app_name">
                                                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings"""
      )
      .expectFixDiffs(
        """
            Fix for AndroidManifest.xml line 7: Delete label:
            @@ -12 +12
            -         <activity
            -             android:name=".MainActivity"
            -             android:label="@string/app_name" >
            +         <activity android:name=".MainActivity" >
        """
      )
  }

  fun testDuplicateMissingPackage() {
    lint()
      .files(
        manifest(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">

                <application>
                    <activity
                        android:name=".MainActivity"
                        android:theme="@style/Theme.MyApplication">
                    </activity>

                    <activity
                        android:name=".MainActivity"
                        android:theme="@style/Theme.MyApplication">
                    </activity>
                </application>

            </manifest>
            """
          )
          .indented(),
        kts(
          """
          android {
              namespace = "test.pkg"
          }
          """
        ),
      )
      .issues(ManifestDetector.DUPLICATE_ACTIVITY)
      .run()
      .expect(
        """
        src/main/AndroidManifest.xml:10: Error: Duplicate registration for activity test.pkg.MainActivity [DuplicateActivity]
                    android:name=".MainActivity"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """
      )
  }

  private val fullBackup =
    xml(
        "res/xml/full_backup_content.xml",
        """
                <full-backup-content>
                     <include domain="file" path="dd"/>
                     <exclude domain="file" path="dd/fo3o.txt"/>
                     <exclude domain="file" path="dd/ss/foo.txt"/>
                </full-backup-content>
                """,
      )
      .indented()

  private val dataExtractionRules =
    xml(
        "res/xml/data_extraction_rules.xml",
        """
                <full-backup-content>
                     <include domain="file" path="dd"/>
                     <exclude domain="file" path="dd/fo3o.txt"/>
                     <exclude domain="file" path="dd/ss/foo.txt"/>
                </full-backup-content>
                """,
      )
      .indented()

  // Make fake SDK "installation" such that we can predict the set
  // of Maven repositories discovered by this test
  private val mockSupportLibraryInstallation: File?
    get() {
      if (sdkDir == null) {
        // Make fake SDK "installation" such that we can predict the set
        // of Maven repositories discovered by this test
        try {
          sdkDir = TestUtils.createTempDirDeletedOnExit().toFile()
        } catch (e: Exception) {
          fail(e.message)
        }
        val paths =
          arrayOf(
            "extras/google/m2repository/com/google/android/gms/play-services-wearable/8.4.0/play-services-wearable-8.4.0.aar"
          )
        createRelativePaths(sdkDir!!, paths)
      }
      return sdkDir
    }

  // Sample code
  private val gradleOverride =
    manifest(
        """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="foo.bar2"
            android:versionCode="1"
            android:versionName="1.0" >

            <uses-sdk android:minSdkVersion="14" android:targetSdkVersion="17" />
            <uses-permission android:name="com.example.helloworld.permission" />
            <uses-permission android:name="android.permission.ACCESS_MOCK_LOCATION" />

            <application
                android:icon="@drawable/ic_launcher"
                android:label="@string/app_name" >
                <activity
                    android:label="@string/app_name"
                    android:name=".Foo2Activity" >
                    <intent-filter >
                        <action android:name="android.intent.action.MAIN" />

                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity>
            </application>

        </manifest>
        """
      )
      .indented()

  // Sample code
  private val library = gradle("build.gradle", "")

  // Sample code
  private val mipmap =
    manifest(
        """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="test.mipmap"
            android:versionCode="1"
            android:versionName="1.0" >

            <!-- Wrong icon resource type -->
            <application
                android:icon="@drawable/ic_launcher"
                android:label="@string/app_name" >
                <!-- Wrong icon resource type -->
                <activity
                    android:name=".Activity1"
                    android:icon="@drawable/activity1"
                    android:label="@string/activity1" >
                    <intent-filter>
                        <action android:name="android.intent.action.MAIN" />
                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity>
                <!-- Already a @mipmap resource -->
                <activity
                    android:name=".Activity2"
                    android:icon="@mipmap/activity2"
                    android:label="@string/activity2" >
                </activity>
                <!-- Not a launchable activity -->
                <activity
                    android:name=".Activity3"
                    android:icon="@drawable/activity3"
                    android:label="@string/activity3" >
                </activity>
            </application>

        </manifest>
        """
      )
      .indented()

  // Sample code
  private val missingApplicationIcon =
    manifest(
        """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="foo.bar2"
            android:versionCode="1"
            android:versionName="1.0" >

            <uses-sdk android:minSdkVersion="14" />

            <application
                android:label="@string/app_name" >
                <activity
                    android:label="@string/app_name"
                    android:name=".Foo2Activity" >
                    <intent-filter >
                        <action android:name="android.intent.action.MAIN" />

                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity>
            </application>

        </manifest>
        """
      )
      .indented()

  // Sample code
  private val missingUsesSdk =
    manifest(
        """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="test.bytecode"
            android:versionCode="1"
            android:versionName="1.0" >

            <application
                android:icon="@drawable/ic_launcher"
                android:label="@string/app_name" >
                <activity
                    android:name=".BytecodeTestsActivity"
                    android:label="@string/app_name" >
                    <intent-filter>
                        <action android:name="android.intent.action.MAIN" />

                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity>
            </application>

        </manifest>
        """
      )
      .indented()

  // Sample code
  private val noVersion =
    manifest(
        """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="foo.bar2" >

            <uses-sdk android:minSdkVersion="14" />

            <application
                android:icon="@drawable/ic_launcher"
                android:label="@string/app_name" >
                <activity
                    android:label="@string/app_name"
                    android:name=".Foo2Activity" >
                    <intent-filter >
                        <action android:name="android.intent.action.MAIN" />

                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity>
            </application>

        </manifest>
        """
      )
      .indented()

  // Sample code
  private val strings =
    xml(
        "res/values/strings.xml",
        """
        <!-- Copyright (C) 2007 The Android Open Source Project

             Licensed under the Apache License, Version 2.0 (the "License");
             you may not use this file except in compliance with the License.
             You may obtain a copy of the License at

                  http://www.apache.org/licenses/LICENSE-2.0

             Unless required by applicable law or agreed to in writing, software
             distributed under the License is distributed on an "AS IS" BASIS,
             WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
             See the License for the specific language governing permissions and
             limitations under the License.
        -->

        <resources>
            <!-- Home -->
            <string name="home_title">Home Sample</string>
            <string name="show_all_apps">All</string>

            <!-- Home Menus -->
            <string name="menu_wallpaper">Wallpaper</string>
            <string name="menu_search">Search</string>
            <string name="menu_settings">Settings</string>
            <string name="sample" translatable="false">Ignore Me</string>

            <!-- Wallpaper -->
            <string name="wallpaper_instructions">Tap picture to set portrait wallpaper</string>
        </resources>

        """,
      )
      .indented()

  // Sample code
  private val libraryCode =
    java(
        """
        package foo.library;

        public class LibraryCode {
            static {
                System.out.println(R.string.string1);
            }
        }
        """
      )
      .indented()

  // Sample code
  private val mainCode =
    java(
        """
        package foo.main;

        public class MainCode {
            static {
                System.out.println(R.string.string2);
            }
        }
        """
      )
      .indented()

  // Sample code
  private val libraryStrings =
    xml(
        "res/values/strings.xml",
        """
        <resources>

            <string name="app_name">LibraryProject</string>
            <string name="string1">String 1</string>
            <string name="string2">String 2</string>
            <string name="string3">String 3</string>

        </resources>
        """,
      )
      .indented()
}
