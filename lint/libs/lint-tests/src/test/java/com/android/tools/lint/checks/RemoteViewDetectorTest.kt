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

import com.android.tools.lint.checks.infrastructure.TestFiles.rClass
import com.android.tools.lint.detector.api.Detector

class RemoteViewDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector {
    return RemoteViewDetector()
  }

  private val kotlinSample =
    kotlin(
        """
        package test.pkg
        import android.widget.RemoteViews

        fun test(packageName: String) {
            val remoteView = RemoteViews(packageName, R.layout.test)
        }
        """
      )
      .indented()

  fun testDocumentationExample() {
    lint()
      .files(
        kotlinSample,
        xml(
            "res/layout/test.xml",
            """
                    <merge>
                        <Button />
                        <AdapterViewFlipper />
                        <FrameLayout />
                        <GridLayout />
                        <GridView />
                        <LinearLayout />
                        <ListView />
                        <RelativeLayout />
                        <StackView />
                        <ViewFlipper />
                        <AnalogClock />
                        <Button />
                        <Chronometer />
                        <ImageButton />
                        <ImageView />
                        <ProgressBar />
                        <TextClock />
                        <TextView />
                        <DatePicker />
                        <CheckBox />
                        <Switch />
                        <RadioButton />
                        <RadioGroup />
                        <androidx.appcompat.widget.AppCompatTextView />
                    </merge>
                    """,
          )
          .indented(),
        rClass("test.pkg", "@layout/test"),
      )
      .run()
      .expect(
        """
                src/test/pkg/test.kt:5: Error: @layout/test includes views not allowed in a RemoteView: CheckBox, DatePicker, RadioButton, RadioGroup, Switch, androidx.appcompat.widget.AppCompatTextView [RemoteViewLayout]
                    val remoteView = RemoteViews(packageName, R.layout.test)
                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
      )
  }

  fun testLayoutFolder31() {
    // http://b/200165599 Update RemoteViewDetector for new @RemoteViews added in API 31
    lint()
      .files(
        manifest().minSdk(31),
        kotlinSample,
        xml(
            "res/layout-v31/test.xml",
            """
                <merge>
                    <CheckBox />
                    <Switch />
                    <RadioButton />
                    <RadioGroup />
                </merge>
                """,
          )
          .indented(),
        rClass("test.pkg", "@layout/test"),
      )
      .run()
      .expectClean()
  }

  fun testMin31() {
    lint()
      .files(
        kotlinSample,
        xml(
            "res/layout-v31/test.xml",
            """
                <merge>
                    <CheckBox />
                    <Switch />
                    <RadioButton />
                    <RadioGroup />
                </merge>
                """,
          )
          .indented(),
        rClass("test.pkg", "@layout/test"),
      )
      .run()
      .expectClean()
  }

  fun testFullyQualifiedBuiltinViews() {
    // Regression test for 233226291
    lint()
      .files(
        rClass("test.pkg", "@layout/cct_article_toolbar"),
        java(
          """
                package test.pkg;

                import android.content.Context;
                import android.widget.RemoteViews;

                public class ArticleCustomTab {
                    private Context context;

                    public void buildSecondaryToolbar() {
                        RemoteViews remoteViews =
                                new RemoteViews(context.getPackageName(), R.layout.cct_article_toolbar);
                    }
                }
                """
        ),
        xml(
            "res/layout/cct_article_toolbar.xml",
            """
                <android.widget.RelativeLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:theme="@style/Theme.MaterialComponents.DayNight">
                  <android.widget.LinearLayout
                      android:id="@+id/hero_action_button"
                      android:minWidth="@dimen/min_touch_size"
                      android:minHeight="@dimen/min_touch_size"
                      android:background="@drawable/card_action_button_oval_bg">
                  </android.widget.LinearLayout>
                </android.widget.RelativeLayout>
                """,
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testSplitAcrossModules() {
    // Test where the remote layout is in a different module (same
    // as testDocumentationExample but with code reference in its own downstream
    // module.)
    val lib =
      project(
        xml(
            "res/layout/test.xml",
            """
            <merge>
                <Button />
                <AdapterViewFlipper />
                <FrameLayout />
                <GridLayout />
                <GridView />
                <LinearLayout />
                <ListView />
                <RelativeLayout />
                <StackView />
                <ViewFlipper />
                <AnalogClock />
                <Button />
                <Chronometer />
                <ImageButton />
                <ImageView />
                <ProgressBar />
                <TextClock />
                <TextView />
                <DatePicker />
                <CheckBox />
                <Switch />
                <RadioButton />
                <RadioGroup />
                <androidx.appcompat.widget.AppCompatTextView />
            </merge>
            """,
          )
          .indented()
      )

    val main = project(kotlinSample, rClass("test.pkg", "@layout/test")).dependsOn(lib)

    lint()
      .projects(lib, main)
      .run()
      .expect(
        """
        src/test/pkg/test.kt:5: Error: @layout/test includes views not allowed in a RemoteView: CheckBox, DatePicker, RadioButton, RadioGroup, Switch, androidx.appcompat.widget.AppCompatTextView [RemoteViewLayout]
            val remoteView = RemoteViews(packageName, R.layout.test)
                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """
      )
  }
}
