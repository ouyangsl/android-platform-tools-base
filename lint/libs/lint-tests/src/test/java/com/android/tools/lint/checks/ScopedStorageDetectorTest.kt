/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.lint.detector.api.Detector

class ScopedStorageDetectorTest : AbstractCheckTest() {

  override fun getDetector(): Detector = ScopedStorageDetector()

  fun testWriteExternalStorage() {
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                    <uses-sdk android:targetSdkVersion="29"/>
                    <uses-permission/><!-- Test for NPEs -->
                    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/><!-- ERROR -->
                    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/><!-- OK -->
                </manifest>
                """
          )
          .indented()
      )
      .run()
      .expect(
        """
                    AndroidManifest.xml:4: Warning: WRITE_EXTERNAL_STORAGE no longer provides write access when targeting Android 10, unless you use requestLegacyExternalStorage [ScopedStorage]
                        <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/><!-- ERROR -->
                                                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    0 errors, 1 warnings
                """
      )
  }

  fun testManageExternalStorage() {
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                    <uses-sdk android:targetSdkVersion="29"/>
                    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/><!-- OK; permission below takes priority. -->
                    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"/><!-- ERROR -->
                    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/><!-- OK -->
                </manifest>
                """
          )
          .indented()
      )
      .run()
      .expect(
        """
                    AndroidManifest.xml:4: Warning: The Google Play store has a policy that limits usage of MANAGE_EXTERNAL_STORAGE [ScopedStorage]
                        <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"/><!-- ERROR -->
                                                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    0 errors, 1 warnings
                """
      )
  }

  fun testAndroid11() {
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                    <uses-sdk android:targetSdkVersion="30"/>
                    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/><!-- ERROR -->
                    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/><!-- OK -->
                </manifest>
                """
          )
          .indented()
      )
      .run()
      .expect(
        """
                    AndroidManifest.xml:3: Warning: WRITE_EXTERNAL_STORAGE no longer provides write access when targeting Android 10+ [ScopedStorage]
                        <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/><!-- ERROR -->
                                                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    0 errors, 1 warnings
                """
      )
  }

  fun testAndroid13() {
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                    <uses-sdk android:targetSdkVersion="33"/>
                    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/><!-- ERROR -->
                    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/><!-- ERROR -->
                </manifest>
                """
          )
          .indented()
      )
      .run()
      .expect(
        """
                AndroidManifest.xml:3: Warning: READ_EXTERNAL_STORAGE is deprecated (and is not granted) when targeting Android 13+. If you need to query or interact with MediaStore or media files on the shared storage, you should instead use one or more new storage permissions: READ_MEDIA_IMAGES, READ_MEDIA_VIDEO or READ_MEDIA_AUDIO. [ScopedStorage]
                    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/><!-- ERROR -->
                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:4: Warning: WRITE_EXTERNAL_STORAGE is deprecated (and is not granted) when targeting Android 13+. If you need to write to shared storage, use the MediaStore.createWriteRequest intent. [ScopedStorage]
                    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/><!-- ERROR -->
                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 2 warnings
                """
      )
      .expectFixDiffs(
        """
                Fix for AndroidManifest.xml line 3: Set maxSdkVersion="32":
                @@ -7 +7
                -     <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" /> <!-- ERROR -->
                +     <uses-permission
                +         android:name="android.permission.READ_EXTERNAL_STORAGE"
                +         android:maxSdkVersion="32" /> <!-- ERROR -->
                Fix for AndroidManifest.xml line 4: Set maxSdkVersion="32":
                @@ -8 +8
                -     <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" /> <!-- ERROR -->
                +     <uses-permission
                +         android:name="android.permission.WRITE_EXTERNAL_STORAGE"
                +         android:maxSdkVersion="32" /> <!-- ERROR -->
                """
      )
  }

  fun testAndroid11Legacy() {
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                    <uses-sdk android:targetSdkVersion="30"/>
                    <application android:requestLegacyExternalStorage="true"/>
                    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/><!-- ERROR -->
                    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/><!-- OK -->
                </manifest>
                """
          )
          .indented()
      )
      .run()
      .expect(
        """
                    AndroidManifest.xml:4: Warning: WRITE_EXTERNAL_STORAGE no longer provides write access when targeting Android 11+, even when using requestLegacyExternalStorage [ScopedStorage]
                        <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/><!-- ERROR -->
                                                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    0 errors, 1 warnings
                """
      )
  }

  fun testAndroid10Legacy() {
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                    <uses-sdk android:targetSdkVersion="29"/>
                    <application android:requestLegacyExternalStorage="true"/>
                    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/><!-- OK -->
                    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/><!-- OK -->
                </manifest>
                """
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testLowSdk() {
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                    <uses-sdk android:targetSdkVersion="28"/>
                    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/><!-- OK -->
                    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/><!-- OK -->
                </manifest>
                """
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testMaxSdkVersionAttr() {
    // Regression test for https://issuetracker.google.com/169483540.
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.pkg">
                    <uses-sdk android:targetSdkVersion="29"/>
                    <uses-permission
                        android:name="android.permission.WRITE_EXTERNAL_STORAGE"
                        android:maxSdkVersion="28"/><!-- OK -->
                </manifest>
                """
          )
          .indented()
      )
      .run()
      .expectClean()
  }
}
