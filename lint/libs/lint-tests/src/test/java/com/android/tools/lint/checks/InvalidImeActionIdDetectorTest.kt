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

import com.android.tools.lint.detector.api.Detector

class InvalidImeActionIdDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector {
    return InvalidImeActionIdDetector()
  }

  fun testNoWarnings() {
    lint()
      .files(
        xml(
            "res/layout/namespace.xml",
            """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical">
                    <EditText android:layout_width="match_parent" android:layout_height="wrap_content" android:imeActionId="6"/>
                </LinearLayout>
                """,
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testInvalidResourceType() {
    lint()
      .files(
        xml(
            "res/layout/namespace.xml",
            """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical">
                    <EditText android:layout_width="match_parent" android:layout_height="wrap_content" android:imeActionId="@+id/login"/>
                </LinearLayout>
                """,
          )
          .indented()
      )
      .run()
      .expect(
        """
            res/layout/namespace.xml:2: Error: Invalid resource type, expected integer value [InvalidImeActionId]
                <EditText android:layout_width="match_parent" android:layout_height="wrap_content" android:imeActionId="@+id/login"/>
                                                                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
      )
  }

  fun testInvalidResourceValue() {
    lint()
      .files(
        xml(
            "res/layout/namespace.xml",
            """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical">
                    <EditText android:layout_width="match_parent" android:layout_height="wrap_content" android:imeActionId="mmm"/>
                </LinearLayout>
                """,
          )
          .indented()
      )
      .run()
      .expect(
        """
            res/layout/namespace.xml:2: Error: "mmm" is not an integer [InvalidImeActionId]
                <EditText android:layout_width="match_parent" android:layout_height="wrap_content" android:imeActionId="mmm"/>
                                                                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
      )
  }
}
