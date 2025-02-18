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

class ScrollViewChildDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector {
    return ScrollViewChildDetector()
  }

  fun testDocumentationExample() {
    lint()
      .files(
        xml(
            "res/layout/wrong_dimension.xml",
            """
                <HorizontalScrollView
                    xmlns:android="http://schemas.android.com/apk/res/android"

                    android:layout_width="match_parent"
                    android:layout_height="match_parent">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="match_parent" />

                </HorizontalScrollView>
                """,
          )
          .indented()
      )
      .run()
      .expect(
        """
            res/layout/wrong_dimension.xml:8: Warning: This LinearLayout should use android:layout_width="wrap_content" [ScrollViewSize]
                    android:layout_width="match_parent"
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
      )
  }

  fun testWebViewChild() {
    // Regression test for https://issuetracker.google.com/37090639
    lint()
      .files(
        xml(
            "res/layout/layout.xml",
            """
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical"
                android:visibility="visible"
                android:id='@+id/statisticsActivity'>

                <ScrollView
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">

                    <!-- If you replace the layout_height to wrap_content, like lint
                    wants you to, then you get a warning in Android Studio 2.1 Preview 4.
                    -->
                    <LinearLayout android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:orientation="vertical">

                        <TextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:gravity="center"
                            android:textAppearance="?android:attr/textAppearanceMedium"
                            android:text="@string/statsCalculationInProgress"
                            android:id="@+id/statsCalculationInProgress"/>

                        <WebView
                            android:layout_width="match_parent"
                            android:layout_height="match_parent"
                            android:id="@+id/statisticsWebView"
                            android:visibility="gone"/>
                    </LinearLayout>

                </ScrollView>
            </LinearLayout>
            """,
          )
          .indented()
      )
      .run()
      .expectClean()
  }
}
