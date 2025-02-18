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

import com.android.tools.lint.detector.api.Detector

class ChildInNonViewGroupDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector {
    return ChildInNonViewGroupDetector()
  }

  fun `test wrong nesting of TextView within ImageView`() {
    lint()
      .files(
        xml(
            "res/layout/wrong.xml",
            """
              <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                  android:orientation="vertical"
                  android:layout_width="match_parent"
                  android:layout_height="match_parent">

                  <ImageView
                      android:layout_width="wrap_content"
                      android:layout_height="wrap_content">
                      <TextView />
                  </ImageView>

              </LinearLayout>
            """,
          )
          .indented()
      )
      .run()
      .expect(
        """
          res/layout/wrong.xml:9: Error: A ImageView should have no children declared in XML [ChildInNonViewGroup]
                  <TextView />
                   ~~~~~~~~
          1 errors, 0 warnings
        """
      )
  }

  fun `test normal nesting of ImageView within a LinearLayout`() {
    lint()
      .files(
        xml(
            "res/layout/normal.xml",
            """
              <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                  android:orientation="vertical"
                  android:layout_width="match_parent"
                  android:layout_height="match_parent">

                  <ImageView
                      android:layout_width="wrap_content"
                      android:layout_height="wrap_content"
                      />

              </LinearLayout>
            """,
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun `test for nesting a marker`() {
    lint()
      .files(
        xml(
            "res/layout/normal.xml",
            """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:orientation="vertical"
                android:layout_width="match_parent"
                android:layout_height="match_parent">

                <EditText
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content">
                    <requestFocus />
                </EditText>

            </LinearLayout>
          """,
          )
          .indented()
      )
      .run()
      .expectClean()
  }
}
