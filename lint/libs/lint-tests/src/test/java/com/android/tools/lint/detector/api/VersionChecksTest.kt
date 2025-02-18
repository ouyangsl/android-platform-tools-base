/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.lint.detector.api

import com.android.tools.lint.checks.AbstractCheckTest
import com.android.tools.lint.checks.ApiDetector
import com.android.tools.lint.checks.ApiLookupTest
import com.android.tools.lint.checks.SdkIntDetector
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.binaryStub
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.checks.infrastructure.TestMode.Companion.PARTIAL
import com.android.tools.lint.detector.api.VersionChecks.Companion.getMinSdkVersionFromMethodName
import com.android.tools.lint.useFirUast

/** Unit tests for [VersionChecks]. This is using the ApiDetector to drive the analysis. */
class VersionChecksTest : AbstractCheckTest() {
  fun testConditionalApi0() {
    // See https://code.google.com/p/android/issues/detail?id=137195
    lint()
      .files(
        classpath(),
        manifest().minSdk(14),
        java(
            """
                package test.pkg;

                import android.animation.RectEvaluator;
                import android.graphics.Rect;
                import android.os.Build;

                @SuppressWarnings("unused")
                public class ConditionalApiTest {
                    private void test(Rect rect) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            new RectEvaluator(rect); // OK
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            if (rect != null) {
                                new RectEvaluator(rect); // OK
                            }
                        }
                    }

                    private void test2(Rect rect) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            new RectEvaluator(rect); // OK
                        }
                    }

                    private void test3(Rect rect) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                            new RectEvaluator(); // ERROR 1
                        }
                    }

                    private void test4(Rect rect) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            System.out.println("Something");
                            new RectEvaluator(rect); // OK
                        } else {
                            new RectEvaluator(rect); // ERROR 2
                        }
                    }

                    private void test5(Rect rect) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CUPCAKE) {
                            new RectEvaluator(rect); // ERROR 3
                        } else {
                            // Here we know that SDK_INT < 3, *and* (from minSdkVersion) SDK_INT >= 14;
                            // an impossibility. We should consider flagging this as unused code!
                            new RectEvaluator(rect); // OK
                        }
                    }
                }
                """
          )
          .indented(),
      )
      // We *don't* want to use provisional computation for this:
      // limit suggestions around SDK_INT checks to those implied
      // by the minSdkVersion of the library.
      .skipTestModes(PARTIAL)
      .run()
      .expect(
        """
                src/test/pkg/ConditionalApiTest.java:28: Error: Call requires API level 18 (current min is 14): new android.animation.RectEvaluator [NewApi]
                            new RectEvaluator(); // ERROR 1
                            ~~~~~~~~~~~~~~~~~
                src/test/pkg/ConditionalApiTest.java:37: Error: Call requires API level 21 (current min is 14): new android.animation.RectEvaluator [NewApi]
                            new RectEvaluator(rect); // ERROR 2
                            ~~~~~~~~~~~~~~~~~
                src/test/pkg/ConditionalApiTest.java:43: Error: Call requires API level 21 (current min is 14): new android.animation.RectEvaluator [NewApi]
                            new RectEvaluator(rect); // ERROR 3
                            ~~~~~~~~~~~~~~~~~
                src/test/pkg/ConditionalApiTest.java:27: Warning: Unnecessary; SDK_INT is always >= 14 [ObsoleteSdkInt]
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/ConditionalApiTest.java:42: Warning: Unnecessary; SDK_INT is always >= 14 [ObsoleteSdkInt]
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CUPCAKE) {
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                3 errors, 2 warnings
                """
      )
  }

  fun testConditionalApi1() {
    // See https://code.google.com/p/android/issues/detail?id=137195
    lint()
      .files(
        classpath(),
        manifest().minSdk(4),
        java(
            """
                package test.pkg;

                import android.os.Build;
                import android.widget.GridLayout;

                import static android.os.Build.VERSION;
                import static android.os.Build.VERSION.SDK_INT;
                import static android.os.Build.VERSION_CODES;
                import static android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH;
                import static android.os.Build.VERSION_CODES.JELLY_BEAN;

                @SuppressWarnings({"UnusedDeclaration", "ConstantConditions"})
                public class VersionConditional1 {
                    public void test(boolean priority) {
                        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }

                        if (SDK_INT >= ICE_CREAM_SANDWICH) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }

                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                            new GridLayout(null).getOrientation(); // Flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Not flagged
                        }

                        if (Build.VERSION.SDK_INT >= 14) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }

                        if (VERSION.SDK_INT >= VERSION_CODES.ICE_CREAM_SANDWICH) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }

                        // Nested conditionals
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                            if (priority) {
                                new GridLayout(null).getOrientation(); // Flagged
                            } else {
                                new GridLayout(null).getOrientation(); // Flagged
                            }
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }

                        // Nested conditionals 2
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            if (priority) {
                                new GridLayout(null).getOrientation(); // Not flagged
                            } else {
                                new GridLayout(null).getOrientation(); // Not flagged
                            }
                        } else {
                            new GridLayout(null); // Flagged
                        }
                    }

                    public void test2(boolean priority) {
                        if (android.os.Build.VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (android.os.Build.VERSION.SDK_INT >= 16) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (android.os.Build.VERSION.SDK_INT >= 13) {
                            new GridLayout(null).getOrientation(); // Flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (SDK_INT >= JELLY_BEAN) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                            new GridLayout(null); // Flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Not flagged
                        }

                        if (Build.VERSION.SDK_INT >= 16) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }
                    }
                }
                """
          )
          .indented(),
      )
      .run()
      .expect(
        """
                src/test/pkg/VersionConditional1.java:18: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:18: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:24: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:24: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:30: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:30: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:36: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:36: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:40: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:40: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:48: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:48: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:54: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:54: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:60: Error: Call requires API level 14 (current min is 11): android.widget.GridLayout#getOrientation [NewApi]
                                new GridLayout(null).getOrientation(); // Flagged
                                                     ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:60: Error: Call requires API level 14 (current min is 11): new android.widget.GridLayout [NewApi]
                                new GridLayout(null).getOrientation(); // Flagged
                                ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:62: Error: Call requires API level 14 (current min is 11): android.widget.GridLayout#getOrientation [NewApi]
                                new GridLayout(null).getOrientation(); // Flagged
                                                     ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:62: Error: Call requires API level 14 (current min is 11): new android.widget.GridLayout [NewApi]
                                new GridLayout(null).getOrientation(); // Flagged
                                ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:65: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:65: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:76: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:84: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:90: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:94: Error: Call requires API level 14 (current min is 13): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:94: Error: Call requires API level 14 (current min is 13): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:96: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:102: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:108: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:114: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:118: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:126: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1.java:132: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                32 errors, 0 warnings
                """
      )
  }

  fun testConditionalApi1b() {
    // See https://code.google.com/p/android/issues/detail?id=137195
    // This is like testConditionalApi1, but with each logical lookup call extracted into
    // a single method. This makes debugging through the control flow graph a lot easier.
    lint()
      .files(
        classpath(),
        manifest().minSdk(4),
        java(
            """
                package test.pkg;

                import android.os.Build;
                import android.widget.GridLayout;

                import static android.os.Build.VERSION;
                import static android.os.Build.VERSION.SDK_INT;
                import static android.os.Build.VERSION_CODES;
                import static android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH;
                import static android.os.Build.VERSION_CODES.JELLY_BEAN;

                @SuppressWarnings({"UnusedDeclaration", "ConstantConditions"})
                public class VersionConditional1b {
                    private void m9(boolean priority) {
                        // Nested conditionals 2
                        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
                            if (priority) {
                                new GridLayout(null).getOrientation(); // Not flagged
                            } else {
                                new GridLayout(null).getOrientation(); // Not flagged
                            }
                        } else {
                            new GridLayout(null); // Flagged
                        }
                    }

                    private void m8(boolean priority) {
                        // Nested conditionals
                        if (VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB) {
                            if (priority) {
                                new GridLayout(null).getOrientation(); // Flagged
                            } else {
                                new GridLayout(null).getOrientation(); // Flagged
                            }
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }
                    }

                    private void m7() {
                        if (VERSION.SDK_INT >= VERSION_CODES.ICE_CREAM_SANDWICH) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }
                    }

                    private void m6() {
                        if (VERSION.SDK_INT >= 14) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }
                    }

                    private void m5() {
                        if (VERSION.SDK_INT < VERSION_CODES.ICE_CREAM_SANDWICH) {
                            new GridLayout(null).getOrientation(); // Flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Not flagged
                        }
                    }

                    private void m4() {
                        if (VERSION.SDK_INT >= VERSION_CODES.ICE_CREAM_SANDWICH) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }
                    }

                    private void m3() {
                        if (SDK_INT >= ICE_CREAM_SANDWICH) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }
                    }

                    private void m2() {
                        if (VERSION.SDK_INT >= VERSION_CODES.ICE_CREAM_SANDWICH) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }
                    }

                    private void m1() {
                        if (VERSION.SDK_INT >= VERSION_CODES.ICE_CREAM_SANDWICH) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Flagged
                        }
                    }

                    public void test2(boolean priority) {
                        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (VERSION.SDK_INT >= 16) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (VERSION.SDK_INT >= 13) {
                            new GridLayout(null).getOrientation(); // Flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (SDK_INT >= JELLY_BEAN) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (VERSION.SDK_INT < VERSION_CODES.JELLY_BEAN) {
                            new GridLayout(null); // Flagged
                        } else {
                            new GridLayout(null).getOrientation(); // Not flagged
                        }

                        if (VERSION.SDK_INT >= 16) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }

                        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
                            new GridLayout(null).getOrientation(); // Not flagged
                        } else {
                            new GridLayout(null); // Flagged
                        }
                    }
                }
                """
          )
          .indented(),
      )
      .run()
      .expect(
        """
                src/test/pkg/VersionConditional1b.java:23: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:31: Error: Call requires API level 14 (current min is 11): android.widget.GridLayout#getOrientation [NewApi]
                                new GridLayout(null).getOrientation(); // Flagged
                                                     ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:31: Error: Call requires API level 14 (current min is 11): new android.widget.GridLayout [NewApi]
                                new GridLayout(null).getOrientation(); // Flagged
                                ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:33: Error: Call requires API level 14 (current min is 11): android.widget.GridLayout#getOrientation [NewApi]
                                new GridLayout(null).getOrientation(); // Flagged
                                                     ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:33: Error: Call requires API level 14 (current min is 11): new android.widget.GridLayout [NewApi]
                                new GridLayout(null).getOrientation(); // Flagged
                                ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:36: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:36: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:44: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:44: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:52: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:52: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:58: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:58: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:68: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:68: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:76: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:76: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:84: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:84: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:92: Error: Call requires API level 14 (current min is 4): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:92: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:100: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:106: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:110: Error: Call requires API level 14 (current min is 13): android.widget.GridLayout#getOrientation [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                                                 ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:110: Error: Call requires API level 14 (current min is 13): new android.widget.GridLayout [NewApi]
                            new GridLayout(null).getOrientation(); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:112: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:118: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:124: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:130: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:134: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:142: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional1b.java:148: Error: Call requires API level 14 (current min is 4): new android.widget.GridLayout [NewApi]
                            new GridLayout(null); // Flagged
                            ~~~~~~~~~~~~~~
                32 errors, 0 warnings
                """
      )
  }

  fun testConditionalApi2() {
    // See https://code.google.com/p/android/issues/detail?id=137195
    lint()
      .files(
        classpath(),
        manifest().minSdk(4),
        java(
            """
                package test.pkg;

                import android.graphics.drawable.Drawable;
                import android.view.View;

                import static android.os.Build.VERSION.SDK_INT;
                import static android.os.Build.VERSION_CODES;
                import static android.os.Build.VERSION_CODES.GINGERBREAD;
                import static android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH;
                import static android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1;
                import static android.os.Build.VERSION_CODES.JELLY_BEAN;

                @SuppressWarnings({"ConstantConditions", "StatementWithEmptyBody"})
                public class VersionConditional2 {
                    // Requires API 16 (JELLY_BEAN)
                    // root.setBackground(background);

                    private void testGreaterThan(View root, Drawable background) {
                        if (SDK_INT > GINGERBREAD) {
                            root.setBackground(background); // Flagged
                        }

                        if (SDK_INT > ICE_CREAM_SANDWICH) {
                            root.setBackground(background); // Flagged
                        }

                        if (SDK_INT > ICE_CREAM_SANDWICH_MR1) { // => SDK_INT >= JELLY_BEAN
                            root.setBackground(background); // Not flagged
                        }

                        if (SDK_INT > JELLY_BEAN) {
                            root.setBackground(background); // Not flagged
                        }

                        if (SDK_INT > VERSION_CODES.JELLY_BEAN_MR1) {
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void testGreaterThanOrEquals(View root, Drawable background) {
                        if (SDK_INT >= GINGERBREAD) {
                            root.setBackground(background); // Flagged
                        }

                        if (SDK_INT >= ICE_CREAM_SANDWICH) {
                            root.setBackground(background); // Flagged
                        }

                        if (SDK_INT >= ICE_CREAM_SANDWICH_MR1) {
                            root.setBackground(background); // Flagged
                        }

                        if (SDK_INT >= JELLY_BEAN) {
                            root.setBackground(background); // Not flagged
                        }

                        if (SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void testLessThan(View root, Drawable background) {
                        if (SDK_INT < GINGERBREAD) {
                            // Other
                        } else {
                            root.setBackground(background); // Flagged
                        }

                        if (SDK_INT < ICE_CREAM_SANDWICH) {
                            // Other
                        } else {
                            root.setBackground(background); // Flagged
                        }

                        if (SDK_INT < ICE_CREAM_SANDWICH_MR1) {
                            // Other
                        } else {
                            root.setBackground(background); // Flagged
                        }

                        if (SDK_INT < JELLY_BEAN) {
                            // Other
                        } else {
                            root.setBackground(background); // Not flagged
                        }

                        if (SDK_INT < VERSION_CODES.JELLY_BEAN_MR1) {
                            // Other
                        } else {
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void testLessThanOrEqual(View root, Drawable background) {
                        if (SDK_INT <= GINGERBREAD) {
                            // Other
                        } else {
                            root.setBackground(background); // Flagged
                        }

                        if (SDK_INT <= ICE_CREAM_SANDWICH) {
                            // Other
                        } else {
                            root.setBackground(background); // Flagged
                        }

                        if (SDK_INT <= ICE_CREAM_SANDWICH_MR1) {
                            // Other
                        } else { // => SDK_INT >= JELLY_BEAN
                            root.setBackground(background); // Not flagged
                        }

                        if (SDK_INT <= JELLY_BEAN) {
                            // Other
                        } else {
                            root.setBackground(background); // Not flagged
                        }

                        if (SDK_INT <= VERSION_CODES.JELLY_BEAN_MR1) {
                            // Other
                        } else {
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void testEquals(View root, Drawable background) {
                        if (SDK_INT == GINGERBREAD) {
                            root.setBackground(background); // Flagged
                        }

                        if (SDK_INT == ICE_CREAM_SANDWICH) {
                            root.setBackground(background); // Flagged
                        }

                        if (SDK_INT == ICE_CREAM_SANDWICH_MR1) {
                            root.setBackground(background); // Flagged
                        }

                        if (SDK_INT == JELLY_BEAN) {
                            root.setBackground(background); // Not flagged
                        }

                        if (SDK_INT == VERSION_CODES.JELLY_BEAN_MR1) {
                            root.setBackground(background); // Not flagged
                        }
                    }
                }
                """
          )
          .indented(),
      )
      .run()
      .expect(
        """
                src/test/pkg/VersionConditional2.java:20: Error: Call requires API level 16 (current min is 10): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2.java:24: Error: Call requires API level 16 (current min is 15): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2.java:42: Error: Call requires API level 16 (current min is 9): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2.java:46: Error: Call requires API level 16 (current min is 14): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2.java:50: Error: Call requires API level 16 (current min is 15): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2.java:66: Error: Call requires API level 16 (current min is 9): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2.java:72: Error: Call requires API level 16 (current min is 14): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2.java:78: Error: Call requires API level 16 (current min is 15): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2.java:98: Error: Call requires API level 16 (current min is 10): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2.java:104: Error: Call requires API level 16 (current min is 15): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2.java:128: Error: Call requires API level 16 (current min is 9): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2.java:132: Error: Call requires API level 16 (current min is 14): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2.java:136: Error: Call requires API level 16 (current min is 15): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                13 errors, 0 warnings
                """
      )
  }

  fun testConditionalApi2b() {
    // See https://code.google.com/p/android/issues/detail?id=137195
    // This is like testConditionalApi2, but with each logical lookup call extracted into
    // a single method. This makes debugging through the control flow graph a lot easier.
    lint()
      .files(
        classpath(),
        manifest().minSdk(4),
        java(
            """
                package test.pkg;

                import android.graphics.drawable.Drawable;
                import android.view.View;

                import static android.os.Build.VERSION.SDK_INT;
                import static android.os.Build.VERSION_CODES;
                import static android.os.Build.VERSION_CODES.GINGERBREAD;
                import static android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH;
                import static android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1;
                import static android.os.Build.VERSION_CODES.JELLY_BEAN;

                @SuppressWarnings({"ConstantConditions", "StatementWithEmptyBody"})
                public class VersionConditional2b {
                    private void gt5(View root, Drawable background) {
                        if (SDK_INT > GINGERBREAD) {
                            root.setBackground(background); // Flagged
                        }
                    }

                    private void gt4(View root, Drawable background) {
                        if (SDK_INT > ICE_CREAM_SANDWICH) {
                            root.setBackground(background); // Flagged
                        }
                    }

                    private void gt3(View root, Drawable background) {
                        if (SDK_INT > ICE_CREAM_SANDWICH_MR1) { // => SDK_INT >= JELLY_BEAN
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void gt2(View root, Drawable background) {
                        if (SDK_INT > JELLY_BEAN) {
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void gt1(View root, Drawable background) {
                        if (SDK_INT > VERSION_CODES.JELLY_BEAN_MR1) {
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void gte5(View root, Drawable background) {
                        if (SDK_INT >= GINGERBREAD) {
                            root.setBackground(background); // Flagged
                        }
                    }

                    private void gte4(View root, Drawable background) {
                        if (SDK_INT >= ICE_CREAM_SANDWICH) {
                            root.setBackground(background); // Flagged
                        }
                    }

                    private void gte3(View root, Drawable background) {
                        if (SDK_INT >= ICE_CREAM_SANDWICH_MR1) {
                            root.setBackground(background); // Flagged
                        }
                    }

                    private void gte2(View root, Drawable background) {
                        if (SDK_INT >= JELLY_BEAN) {
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void gte1(View root, Drawable background) {
                        if (SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void lt5(View root, Drawable background) {
                        if (SDK_INT < GINGERBREAD) {
                            // Other
                        } else {
                            root.setBackground(background); // Flagged
                        }
                    }

                    private void lt4(View root, Drawable background) {
                        if (SDK_INT < ICE_CREAM_SANDWICH) {
                            // Other
                        } else {
                            root.setBackground(background); // Flagged
                        }
                    }

                    private void lt3(View root, Drawable background) {
                        if (SDK_INT < ICE_CREAM_SANDWICH_MR1) {
                            // Other
                        } else {
                            root.setBackground(background); // Flagged
                        }
                    }

                    private void lt2(View root, Drawable background) {
                        if (SDK_INT < JELLY_BEAN) {
                            // Other
                        } else {
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void lt1(View root, Drawable background) {
                        if (SDK_INT < VERSION_CODES.JELLY_BEAN_MR1) {
                            // Other
                        } else {
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void lte5(View root, Drawable background) {
                        if (SDK_INT <= GINGERBREAD) {
                            // Other
                        } else {
                            root.setBackground(background); // Flagged
                        }
                    }

                    private void lte4(View root, Drawable background) {
                        if (SDK_INT <= ICE_CREAM_SANDWICH) {
                            // Other
                        } else {
                            root.setBackground(background); // Flagged
                        }
                    }

                    private void lte3(View root, Drawable background) {
                        if (SDK_INT <= ICE_CREAM_SANDWICH_MR1) {
                            // Other
                        } else { // => SDK_INT >= JELLY_BEAN
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void lte2(View root, Drawable background) {
                        if (SDK_INT <= JELLY_BEAN) {
                            // Other
                        } else {
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void lte1(View root, Drawable background) {
                        if (SDK_INT <= VERSION_CODES.JELLY_BEAN_MR1) {
                            // Other
                        } else {
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void eq5(View root, Drawable background) {
                        if (SDK_INT == GINGERBREAD) {
                            root.setBackground(background); // Flagged
                        }
                    }

                    private void eq4(View root, Drawable background) {
                        if (SDK_INT == ICE_CREAM_SANDWICH) {
                            root.setBackground(background); // Flagged
                        }
                    }

                    private void eq3(View root, Drawable background) {
                        if (SDK_INT == ICE_CREAM_SANDWICH_MR1) {
                            root.setBackground(background); // Flagged
                        }
                    }

                    private void eq2(View root, Drawable background) {
                        if (SDK_INT == JELLY_BEAN) {
                            root.setBackground(background); // Not flagged
                        }
                    }

                    private void eq1(View root, Drawable background) {
                        if (SDK_INT == VERSION_CODES.JELLY_BEAN_MR1) {
                            root.setBackground(background); // Not flagged
                        }
                    }
                }
                """
          )
          .indented(),
      )
      .run()
      .expect(
        """
                src/test/pkg/VersionConditional2b.java:17: Error: Call requires API level 16 (current min is 10): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2b.java:23: Error: Call requires API level 16 (current min is 15): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2b.java:47: Error: Call requires API level 16 (current min is 9): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2b.java:53: Error: Call requires API level 16 (current min is 14): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2b.java:59: Error: Call requires API level 16 (current min is 15): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2b.java:79: Error: Call requires API level 16 (current min is 9): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2b.java:87: Error: Call requires API level 16 (current min is 14): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2b.java:95: Error: Call requires API level 16 (current min is 15): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2b.java:119: Error: Call requires API level 16 (current min is 10): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2b.java:127: Error: Call requires API level 16 (current min is 15): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2b.java:157: Error: Call requires API level 16 (current min is 9): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2b.java:163: Error: Call requires API level 16 (current min is 14): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                src/test/pkg/VersionConditional2b.java:169: Error: Call requires API level 16 (current min is 15): android.view.View#setBackground [NewApi]
                            root.setBackground(background); // Flagged
                                 ~~~~~~~~~~~~~
                13 errors, 0 warnings
                """
      )
  }

  fun testConditionalApi3() {
    // See https://code.google.com/p/android/issues/detail?id=137195
    lint()
      .files(
        classpath(),
        manifest().minSdk(4),
        java(
            """
                package test.pkg;
                import android.os.Build;
                import android.os.Build.VERSION_CODES;
                import android.view.ViewDebug;

                import static android.os.Build.VERSION_CODES.KITKAT_WATCH;
                import static android.os.Build.VERSION_CODES.LOLLIPOP;

                @SuppressWarnings({"unused", "StatementWithEmptyBody"})
                public class VersionConditional3 {
                    public void test(ViewDebug.ExportedProperty property) {
                        // Test short circuit evaluation
                        if (Build.VERSION.SDK_INT > 18 && property.hasAdjacentMapping()) { // ERROR
                        }
                        if (Build.VERSION.SDK_INT > 19 && property.hasAdjacentMapping()) { // ERROR
                        }
                        if (Build.VERSION.SDK_INT > 20 && property.hasAdjacentMapping()) { // OK
                        }
                        if (Build.VERSION.SDK_INT > 21 && property.hasAdjacentMapping()) { // OK
                        }
                        if (Build.VERSION.SDK_INT > 22 && property.hasAdjacentMapping()) { // OK
                        }

                        if (Build.VERSION.SDK_INT >= 18 && property.hasAdjacentMapping()) { // ERROR
                        }
                        if (Build.VERSION.SDK_INT >= 19 && property.hasAdjacentMapping()) { // ERROR
                        }
                        if (Build.VERSION.SDK_INT >= 20 && property.hasAdjacentMapping()) { // ERROR
                        }
                        if (Build.VERSION.SDK_INT >= 21 && property.hasAdjacentMapping()) { // OK
                        }
                        if (Build.VERSION.SDK_INT >= 22 && property.hasAdjacentMapping()) { // OK
                        }

                        if (Build.VERSION.SDK_INT == 18 && property.hasAdjacentMapping()) { // ERROR
                        }
                        if (Build.VERSION.SDK_INT == 19 && property.hasAdjacentMapping()) { // ERROR
                        }
                        if (Build.VERSION.SDK_INT == 20 && property.hasAdjacentMapping()) { // ERROR
                        }
                        if (Build.VERSION.SDK_INT == 21 && property.hasAdjacentMapping()) { // OK
                        }
                        if (Build.VERSION.SDK_INT == 22 && property.hasAdjacentMapping()) { // OK
                        }

                        if (Build.VERSION.SDK_INT < 18 && property.hasAdjacentMapping()) { // ERROR
                        }
                        if (Build.VERSION.SDK_INT < 22 && property.hasAdjacentMapping()) { // ERROR
                        }
                        if (Build.VERSION.SDK_INT <= 18 && property.hasAdjacentMapping()) { // ERROR
                        }
                        if (Build.VERSION.SDK_INT <= 22 && property.hasAdjacentMapping()) { // ERROR
                        }

                        // Symbolic names instead
                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD && property.hasAdjacentMapping()) { // ERROR
                        }
                        if (Build.VERSION.SDK_INT > VERSION_CODES.KITKAT && property.hasAdjacentMapping()) { // ERROR
                        }
                        if (Build.VERSION.SDK_INT > KITKAT_WATCH && property.hasAdjacentMapping()) { // OK
                        }
                        if (Build.VERSION.SDK_INT > LOLLIPOP && property.hasAdjacentMapping()) { // OK
                        }

                        // Wrong operator
                        if (Build.VERSION.SDK_INT > 21 || property.hasAdjacentMapping()) { // ERROR
                        }

                        // Test multiple conditions in short circuit evaluation
                        if (Build.VERSION.SDK_INT > 21 &&
                                System.getProperty("something") != null &&
                                property.hasAdjacentMapping()) { // OK
                        }

                        // Test order (still before call)
                        if (System.getProperty("something") != null &&
                                Build.VERSION.SDK_INT > 21 &&
                                property.hasAdjacentMapping()) { // OK
                        }

                        // Test order (after call)
                        if (System.getProperty("something") != null &&
                                property.hasAdjacentMapping() && // ERROR
                                Build.VERSION.SDK_INT > 21) {
                        }

                        if (Build.VERSION.SDK_INT > 21 && System.getProperty("something") == null) { // OK
                            boolean p = property.hasAdjacentMapping(); // OK
                        }
                    }
                }
                """
          )
          .indented(),
      )
      .run()
      .expect(
        """
                src/test/pkg/VersionConditional3.java:13: Error: Call requires API level 21 (current min is 19): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT > 18 && property.hasAdjacentMapping()) { // ERROR
                                                                   ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:15: Error: Call requires API level 21 (current min is 20): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT > 19 && property.hasAdjacentMapping()) { // ERROR
                                                                   ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:24: Error: Call requires API level 21 (current min is 18): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT >= 18 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:26: Error: Call requires API level 21 (current min is 19): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT >= 19 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:28: Error: Call requires API level 21 (current min is 20): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT >= 20 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:35: Error: Call requires API level 21 (current min is 18): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT == 18 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:37: Error: Call requires API level 21 (current min is 19): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT == 19 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:39: Error: Call requires API level 21 (current min is 20): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT == 20 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:46: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT < 18 && property.hasAdjacentMapping()) { // ERROR
                                                                   ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:48: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT < 22 && property.hasAdjacentMapping()) { // ERROR
                                                                   ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:50: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT <= 18 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:52: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT <= 22 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:56: Error: Call requires API level 21 (current min is 10): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD && property.hasAdjacentMapping()) { // ERROR
                                                                                                ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:58: Error: Call requires API level 21 (current min is 20): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT > VERSION_CODES.KITKAT && property.hasAdjacentMapping()) { // ERROR
                                                                                     ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:66: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT > 21 || property.hasAdjacentMapping()) { // ERROR
                                                                   ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3.java:83: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                                property.hasAdjacentMapping() && // ERROR
                                         ~~~~~~~~~~~~~~~~~~
                16 errors, 0 warnings
                """
      )
  }

  fun testConditionalApi3b() {
    // See https://code.google.com/p/android/issues/detail?id=137195
    // This is like testConditionalApi3, but with each logical lookup call extracted into
    // a single method. This makes debugging through the control flow graph a lot easier.
    lint()
      .files(
        classpath(),
        manifest().minSdk(4),
        java(
            """
                package test.pkg;

                import android.os.Build;
                import android.os.Build.VERSION_CODES;
                import android.view.ViewDebug;

                import static android.os.Build.VERSION_CODES.KITKAT_WATCH;
                import static android.os.Build.VERSION_CODES.LOLLIPOP;

                @SuppressWarnings({"unused", "StatementWithEmptyBody"})
                public class VersionConditional3b {
                    private void m28(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT > 21 && System.getProperty("something") == null) { // OK
                            boolean p = property.hasAdjacentMapping(); // OK
                        }
                    }

                    private void m27(ViewDebug.ExportedProperty property) {
                        // Test order (after call)
                        if (System.getProperty("something") != null &&
                                property.hasAdjacentMapping() && // ERROR
                                Build.VERSION.SDK_INT > 21) {
                        }
                    }

                    private void m26(ViewDebug.ExportedProperty property) {
                        // Test order (still before call)
                        if (System.getProperty("something") != null &&
                                Build.VERSION.SDK_INT > 21 &&
                                property.hasAdjacentMapping()) { // OK
                        }
                    }

                    private void m25(ViewDebug.ExportedProperty property) {
                        // Test multiple conditions in short circuit evaluation
                        if (Build.VERSION.SDK_INT > 21 &&
                                System.getProperty("something") != null &&
                                property.hasAdjacentMapping()) { // OK
                        }
                    }

                    private void m24(ViewDebug.ExportedProperty property) {
                        // Wrong operator
                        if (Build.VERSION.SDK_INT > 21 || property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m23(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT > LOLLIPOP && property.hasAdjacentMapping()) { // OK
                        }
                    }

                    private void m22(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT > KITKAT_WATCH && property.hasAdjacentMapping()) { // OK
                        }
                    }

                    private void m21(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT > VERSION_CODES.KITKAT && property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m20(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT > VERSION_CODES.GINGERBREAD && property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m19(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT <= 22 && property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m18(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT <= 18 && property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m17(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT < 22 && property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m16(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT < 18 && property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m15(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT == 22 && property.hasAdjacentMapping()) { // OK
                        }
                    }

                    private void m14(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT == 21 && property.hasAdjacentMapping()) { // OK
                        }
                    }

                    private void m13(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT == 20 && property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m12(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT == 19 && property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m11(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT == 18 && property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m10(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT >= 22 && property.hasAdjacentMapping()) { // OK
                        }
                    }

                    private void m9(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT >= 21 && property.hasAdjacentMapping()) { // OK
                        }
                    }

                    private void m8(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT >= 20 && property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m7(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT >= 19 && property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m6(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT >= 18 && property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m5(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT > 22 && property.hasAdjacentMapping()) { // OK
                        }
                    }

                    private void m4(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT > 21 && property.hasAdjacentMapping()) { // OK
                        }
                    }

                    private void m3(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT > 20 && property.hasAdjacentMapping()) { // OK
                        }
                    }

                    private void m2(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT > 19 && property.hasAdjacentMapping()) { // ERROR
                        }
                    }

                    private void m1(ViewDebug.ExportedProperty property) {
                        if (Build.VERSION.SDK_INT > 18 && property.hasAdjacentMapping()) { // ERROR
                        }
                    }
                }
                """
          )
          .indented(),
      )
      .run()
      .expect(
        """
                src/test/pkg/VersionConditional3b.java:21: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                                property.hasAdjacentMapping() && // ERROR
                                         ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:44: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT > 21 || property.hasAdjacentMapping()) { // ERROR
                                                                   ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:59: Error: Call requires API level 21 (current min is 20): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT > VERSION_CODES.KITKAT && property.hasAdjacentMapping()) { // ERROR
                                                                                     ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:64: Error: Call requires API level 21 (current min is 10): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT > VERSION_CODES.GINGERBREAD && property.hasAdjacentMapping()) { // ERROR
                                                                                          ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:69: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT <= 22 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:74: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT <= 18 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:79: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT < 22 && property.hasAdjacentMapping()) { // ERROR
                                                                   ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:84: Error: Call requires API level 21 (current min is 4): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT < 18 && property.hasAdjacentMapping()) { // ERROR
                                                                   ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:99: Error: Call requires API level 21 (current min is 20): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT == 20 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:104: Error: Call requires API level 21 (current min is 19): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT == 19 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:109: Error: Call requires API level 21 (current min is 18): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT == 18 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:124: Error: Call requires API level 21 (current min is 20): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT >= 20 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:129: Error: Call requires API level 21 (current min is 19): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT >= 19 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:134: Error: Call requires API level 21 (current min is 18): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT >= 18 && property.hasAdjacentMapping()) { // ERROR
                                                                    ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:154: Error: Call requires API level 21 (current min is 20): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT > 19 && property.hasAdjacentMapping()) { // ERROR
                                                                   ~~~~~~~~~~~~~~~~~~
                src/test/pkg/VersionConditional3b.java:159: Error: Call requires API level 21 (current min is 19): android.view.ViewDebug.ExportedProperty#hasAdjacentMapping [NewApi]
                        if (Build.VERSION.SDK_INT > 18 && property.hasAdjacentMapping()) { // ERROR
                                                                   ~~~~~~~~~~~~~~~~~~
                16 errors, 0 warnings
                """
      )
  }

  fun testConditionalApi4() {
    lint()
      .files(
        manifest().minSdk(4),
        java(
            """
                package test.pkg;

                import androidx.annotation.RequiresApi;
                import androidx.core.os.BuildCompat;

                import static android.os.Build.VERSION.SDK_INT;
                import static android.os.Build.VERSION_CODES.M;
                import static android.os.Build.VERSION_CODES.N;
                import static android.os.Build.VERSION_CODES.N_MR1;

                @SuppressWarnings({"unused", "WeakerAccess", "StatementWithEmptyBody"})
                public class VersionConditionals4 {
                    public void testOrConditionals(int x) {
                        if (SDK_INT < N || x < 5 || methodN()) { } // OK
                        if (SDK_INT < N || methodN()) { } // OK
                        if (methodN() || SDK_INT < N) { } // ERROR
                    }

                    public void testVersionCheckMethods() {
                        if (SDK_INT >= N) { methodN(); } // OK
                        if (getBuildSdkInt() >= N) {  methodN();  }// OK
                        if (isNougat()) {  methodN(); } // OK
                        if (isAtLeast(N)) { methodN(); } // OK
                        if (isAtLeast(10)) { methodN(); } // ERROR
                        if (isAtLeast(23)) { methodN(); } // ERROR
                        if (isAtLeast(24)) { methodN(); } // OK
                        if (isAtLeast(25)) { methodN(); } // OK
                        if (BuildCompat.isAtLeastN()) { methodM(); } // OK
                        if (BuildCompat.isAtLeastN()) { methodN(); } // OK
                        if (BuildCompat.isAtLeastN()) { methodN_MR1(); } // ERROR
                        if (BuildCompat.isAtLeastNMR1()) { methodN_MR1(); } // OK
                        if (isAtLeastN()) { methodN(); } // OK
                        if (BuildCompat.isAtLeastNMR1()) { methodN(); } // OK
                        if (BuildCompat.isAtLeastP()) { methodP(); } // OK
                        if (BuildCompat.isAtLeastQ()) { methodQ(); } // OK
                        if (isAtLeastZ()) { methodZ(); } // OK
                    }

                    public void testAndConditionals(int x) {
                        if (methodN() && SDK_INT >= N) { } // ERROR
                        if (true && methodN() && SDK_INT >= N) { } // ERROR
                        if (true && SDK_INT >= N && methodN()) { } // OK
                    }

                    // Data-binding adds this method
                    public static int getBuildSdkInt() {
                        return SDK_INT;
                    }

                    public static boolean isNougat() {
                        return SDK_INT >= N;
                    }

                    public static boolean isAtLeast(int api) {
                        return SDK_INT >= api;
                    }

                    public static boolean isAtLeastN() {
                        return BuildCompat.isAtLeastN();
                    }

                    public static boolean isAtLeastZ() {
                        return SDK_INT >= 36;
                    }

                    @RequiresApi(M)
                    public boolean methodM() {
                        return true;
                    }

                    @RequiresApi(N)
                    public boolean methodN() {
                        return true;
                    }

                    @RequiresApi(N_MR1)
                    public boolean methodN_MR1() {
                        return true;
                    }

                    @RequiresApi(28)
                    public boolean methodP() {
                        return true;
                    }

                    @RequiresApi(29)
                    public boolean methodQ() {
                        return true;
                    }

                    @RequiresApi(29)
                    public boolean methodZ() {
                        return true;
                    }
                }
                """
          )
          .indented(),
        jar(
          "libs/build-compat.jar",
          base64gzip(
            "androidx/core/os/BuildCompat.class",
            "" +
              "H4sIAAAAAAAAAIWUz08TQRTHv9MuXVoXqKBIKYIoYotKRbxhjLXFpLE/hJKa" +
              "4MFMt5N2cNklu1PjnyPx4MWLHDTx4B/gH2V8uy1txZbuYebNzHuf75v3Jvv7" +
              "z89fALaxHYOOpShuYtkfVmK4hVV/uK3jjo41hshTaUv1jCGcStcYtJzTEAwz" +
              "RWmLcvu4LtwDXrdoZ7bomNyqcVf66+6mplrSY1gucrvhOrLxMWM6rsg4XuZF" +
              "W1qNnHN8wtUOQ0x6WVUU3FPlQOiQYaqquPm+xE+6qFhenLjC5Eo0GBL7bVvJ" +
              "Y1GTnqTTrG07iivp2CQ2XzziH3jG4nYz048hkam+SGl/a1C0MnhYuXD4enCx" +
              "R4uq03ZN8VL6WcUH7rHp6xqYRFTHuoE0NgzcxwMdDw1sImPgEQi8dFkpCNhP" +
              "vlI/EqbS8ZhhoRvUc1+r7e5XCxWqlt6zjIJtCzdncc8TVAe9mn/1rlA+YGAF" +
              "hslcJb9bzpZ2/U71NarKlXZz5x/dzh513hJ2U7WChhAhYra4m6UUtVQhnaOI" +
              "izlhFRF6Tf6nI+TXAVSt4JlRDjRPbPwA+0ZGCFdojNEMLEJDEgZZRscJU5im" +
              "OYoZxBEOAFuBJzB9hlB84RS69gVa+GuPFAki7+FqEB8yntMlg4C57uG1AHh9" +
              "ODAxCvhkHHB+OHBxFDA7DnhjODA5ClgeB1wYDlwaBXw7DpggYKerb7rA1BnC" +
              "36Fpn7BCFpkT8b1TzJ3bh5//k5qllgMtarOk9h9R41rnsuskG6JN3zWJVPBK" +
              "GP2Y7pJT9C+SvhI3tgQAAA==",
          ),
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
                src/test/pkg/VersionConditionals4.java:16: Error: Call requires API level 24 (current min is 4): methodN [NewApi]
                        if (methodN() || SDK_INT < N) { } // ERROR
                            ~~~~~~~
                src/test/pkg/VersionConditionals4.java:24: Error: Call requires API level 24 (current min is 10): methodN [NewApi]
                        if (isAtLeast(10)) { methodN(); } // ERROR
                                             ~~~~~~~
                src/test/pkg/VersionConditionals4.java:25: Error: Call requires API level 24 (current min is 23): methodN [NewApi]
                        if (isAtLeast(23)) { methodN(); } // ERROR
                                             ~~~~~~~
                src/test/pkg/VersionConditionals4.java:30: Error: Call requires API level 25 (current min is 24): methodN_MR1 [NewApi]
                        if (BuildCompat.isAtLeastN()) { methodN_MR1(); } // ERROR
                                                        ~~~~~~~~~~~
                src/test/pkg/VersionConditionals4.java:40: Error: Call requires API level 24 (current min is 4): methodN [NewApi]
                        if (methodN() && SDK_INT >= N) { } // ERROR
                            ~~~~~~~
                src/test/pkg/VersionConditionals4.java:41: Error: Call requires API level 24 (current min is 4): methodN [NewApi]
                        if (true && methodN() && SDK_INT >= N) { } // ERROR
                                    ~~~~~~~
                6 errors, 0 warnings
                """
      )
  }

  fun testConditionalApi5() {
    // Regression test for
    //   -- https://issuetracker.google.com/issues/37103139
    //   -- https://issuetracker.google.com/issues/37078078
    // Handle version checks in conditionals.
    lint()
      .files(
        manifest().minSdk(4),
        java(
            """
                package test.pkg;

                import android.Manifest;
                import android.app.Activity;
                import android.app.ActivityOptions;
                import android.content.Intent;
                import android.content.pm.PackageManager;
                import android.os.Build.VERSION;
                import android.os.Build.VERSION_CODES;
                import android.view.View;

                public class VersionConditionals5 extends Activity {
                    public boolean test() {
                        return VERSION.SDK_INT < 23
                                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                    }

                    public static void startActivity(final Activity activity, View searchCardView) {
                        final Intent intent = new Intent(activity, VersionConditionals5.class);
                        if (VERSION.SDK_INT < VERSION_CODES.LOLLIPOP || searchCardView == null)
                            activity.startActivity(intent);
                        else {
                            final String transitionName = activity.getString(android.R.string.ok);
                            searchCardView.setTransitionName(transitionName);
                            final ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(activity,
                                    searchCardView, transitionName);
                            activity.startActivity(intent, options.toBundle());
                            activity.getWindow().getSharedElementExitTransition().setDuration(100);
                        }
                    }
                }
                """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testConditionalApi6() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=207289
    lint()
      .files(
        manifest().minSdk(4),
        java(
            """
                package test.pkg;

                import android.animation.*;
                import android.os.Build;
                import android.view.View;

                class Test {
                    View mSelection;
                    void f() {
                        final View flashView = mSelection;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                            ObjectAnimator whiteFlashIn = ObjectAnimator.ofObject(flashView,
                                    "backgroundColor", new ArgbEvaluator(), 0x00FFFFFF, 0xAAFFFFFF);
                            ObjectAnimator whiteFlashOut = ObjectAnimator.ofObject(flashView,
                                    "backgroundColor", new ArgbEvaluator(), 0xAAFFFFFF, 0x00000000);
                            whiteFlashIn.setDuration(200);
                            whiteFlashOut.setDuration(300);
                            AnimatorSet whiteFlash = new AnimatorSet();
                            whiteFlash.playSequentially(whiteFlashIn, whiteFlashOut);
                            whiteFlash.addListener(new AnimatorListenerAdapter() {
                                @SuppressWarnings("deprecation")
                                @Override public void onAnimationEnd(Animator animation) {
                                    flashView.setBackgroundDrawable(null);
                                }
                            });
                            whiteFlash.start();
                        }
                    }
                }"""
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testConditionalOnConstant() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=221586
    lint()
      .files(
        manifest().minSdk(4),
        java(
            """
                package test.pkg;

                import android.app.Activity;
                import android.os.Build;
                import android.widget.TextView;

                public class VersionConditionals6 extends Activity {
                    public static final boolean SUPPORTS_LETTER_SPACING = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;

                    public void test(TextView textView) {
                        if (SUPPORTS_LETTER_SPACING) {
                            textView.setLetterSpacing(1f); // OK
                        }
                        textView.setLetterSpacing(1f); // ERROR
                    }
                }
                """
          )
          .indented(),
      )
      .run()
      .expect(
        """
            src/test/pkg/VersionConditionals6.java:14: Error: Call requires API level 21 (current min is 4): android.widget.TextView#setLetterSpacing [NewApi]
                    textView.setLetterSpacing(1f); // ERROR
                             ~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
      )
  }

  fun testVersionCheckInLibrary() {
    // Include SdkIntDetector such that we record partial state about
    // SDK_INT checks in the library which are then later used by
    // the VersionChecks during ApiDetector analysis
    val issues = arrayOf(ApiDetector.UNSUPPORTED, SdkIntDetector.ISSUE)

    lint()
      .files(
        manifest().minSdk(4),
        java(
            """
                package test.pkg;

                import androidx.annotation.RequiresApi;
                import test.utils.Utils;
                import static test.utils.Utils.isNougat;
                import static test.utils.Utils.versionCheck;
                import static test.utils.Utils.CAPABILITIES_FROM_O;
                import static android.os.Build.VERSION.SDK_INT;
                import static android.os.Build.VERSION_CODES.N;

                public class CheckInLibraryTest {
                    public void testVersionCheckMethods() {
                        if (isNougat()) { methodN(); } // OK
                        if (versionCheck(14)) { methodN(); } // ERROR
                        if (versionCheck(28)) { methodN(); } // OK
                        if (CAPABILITIES_FROM_O) { methodN(); } // OK
                    }

                    @RequiresApi(N)
                    public boolean methodN() {
                        return true;
                    }
                }
                """
          )
          .indented(),
        compiled(
          "../lib/bin/classes",
          java(
              "../lib/src/test/utils/Utils.java",
              """
                        package test.utils;
                        import static android.os.Build.VERSION.SDK_INT;
                        import static android.os.Build.VERSION_CODES.N;
                        import static android.os.Build.VERSION_CODES.O;
                        @SuppressWarnings("AnnotateVersionCheck")
                        public class Utils {
                            public static boolean isNougat() {
                                return SDK_INT >= N;
                            }
                            // Not named "isAtLeast" since lint has hardcoded some common names
                            // like that one
                            public static boolean versionCheck(int api) {
                                return SDK_INT >= api;
                            }
                            public static final boolean CAPABILITIES_FROM_O = SDK_INT >= O;

                            public static void runOnNougat(Runnable runnable) {
                                if (SDK_INT >= N) {
                                    runnable.run();
                                }
                            }
                        }
                    """,
            )
            .indented(),
          0x64a854c9,
          """
                    test/utils/Utils.class:
                    H4sIAAAAAAAAAHWTy27TQBSG/0nSTC7OpQmlaaEtpQXSIGGQkECiQrRuKlmk
                    MYpDF91EjmMVt2aMfOn7sGLDJrBAsOABeCjEGctNq6B6MT5zfL7z/3NG/vP3
                    128AL/CYY6UEjrtFrGG9jA3cKyKPTY77HFsMTW3v3d6+3tOHetccHQ6Mo5HB
                    wE4Y8ruucKPXDNn2zjFDTvMnDkOt5wqnH38cO8HQGnuUKbhh349PrSipJLBi
                    RpZ9fmR9SguUCycIXV9oHxz7nBq1dVlVDmJhiEtyud07sy4s1bPEqTqIhZDo
                    K6lb2LW91EjJ9OPAdg5d2bX0PnK98ImkOLYZ1i0xCXx3ovqhuh+73mT7uDsw
                    daM/0oyDrkm25vaKLoQTaJ4Vhk6ooIQyxwMFj9Dm2FHQQVlBAUWGeuSEkRpL
                    OTURpdSVWWN85th0gvq8PkPrJksM/CoyD96O9P6Qhq4zNP6fAo2VRoVNujVO
                    V8qwIm1RtEAxuaZVod0avZnMdn4gM00KK7Tmk2SegCpqaekzZCCf6jdk663P
                    4LkvyGW/UiZzjSmgnohklDeM8EU0UvwplcmCCuGrN9GVS7qc0E3cSumXKV1L
                    xYudn8gxTGd8FVlaG6TfpHgp6ZMFq1JiaXba5+kRFmWXmYnvWJjO+ahdP0UG
                    t5PPy7iTSEgnLaxC/hstPETxH5PQHcg3AwAA
                    """,
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .issues(*issues)
      // If we only supply the bytecode, lint would have to analyze bytecode
      // to recognize version checks inside a compiled method; it doesn't do that;
      // this is what the SdkIntDetector is for (encouraging use of annotations
      // which captures the info). However, when we analyze the source code in a
      // library we can record the information as partial state, so this *does*
      // work in partial analysis which we want to test here.
      .skipTestModes(TestMode.BYTECODE_ONLY)
      .run()
      .expect(
        """
            src/test/pkg/CheckInLibraryTest.java:14: Error: Call requires API level 24 (current min is 14): methodN [NewApi]
                    if (versionCheck(14)) { methodN(); } // ERROR
                                            ~~~~~~~
            1 errors, 0 warnings
            """
      )
  }

  fun testVersionCheckMethodsInBinaryOperator() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=199572
    lint()
      .files(
        manifest().minSdk(10),
        java(
          """
                package test.pkg;

                import android.app.Activity;
                import android.content.Context;
                import android.hardware.camera2.CameraAccessException;
                import android.hardware.camera2.CameraManager;
                import android.os.Build;

                public class VersionConditionals8 extends Activity {
                    private boolean mDebug;

                    public void testCamera() {
                        if (isLollipop() && mDebug) {
                            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                            try {
                                int length = manager.getCameraIdList().length;
                            } catch (Throwable ignore) {
                            }
                        }
                    }

                    private boolean isLollipop() {
                        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
                    }
                }
                """
        ),
      )
      .run()
      .expectClean()
  }

  fun testTernaryOperator() {
    lint()
      .files(
        manifest().minSdk(10),
        java(
            """
                package test.pkg;

                import android.os.Build;
                import android.view.View;
                import android.widget.GridLayout;

                public class TestTernaryOperator {
                    public View getLayout1() {
                        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH
                                ? new GridLayout(null) : null;
                    }

                    public View getLayout2() {
                        return Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH
                                ? null : new GridLayout(null);
                    }
                }
                """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testVersionInVariable() {
    // Regression test for b/35116007:
    // Allow the SDK version to be extracted into a variable or field
    lint()
      .files(
        manifest().minSdk(10),
        java(
            """
                package test.pkg;

                import android.os.Build;
                import android.view.View;
                import android.widget.GridLayout;

                public class TestVersionInVariable {
                    private static final int STASHED_VERSION = Build.VERSION.SDK_INT;
                    public void getLayout1() {
                        final int v = Build.VERSION.SDK_INT;
                        final int version = v;
                        if (version >= 14) {
                            new GridLayout(null);
                        }
                        if (STASHED_VERSION >= 14) {
                            new GridLayout(null);
                        }
                    }
                }
                """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testNegative() {
    lint()
      .files(
        manifest().minSdk(10),
        java(
            """
                package test.pkg;

                import android.app.Activity;
                import android.content.Context;
                import android.hardware.camera2.CameraAccessException;
                import android.hardware.camera2.CameraManager;
                import android.os.Build;

                public class Negative extends Activity {
                    public void testNegative1() throws CameraAccessException {
                        if (!isLollipop()) {
                        } else {
                            ((CameraManager) getSystemService(Context.CAMERA_SERVICE)).getCameraIdList();
                        }
                    }

                    public void testReversedOperator() throws CameraAccessException {
                        if (Build.VERSION_CODES.LOLLIPOP <= Build.VERSION.SDK_INT) {
                            ((CameraManager) getSystemService(Context.CAMERA_SERVICE)).getCameraIdList();
                        }
                    }

                    private boolean isLollipop() {
                        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
                    }
                }
                """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testPrecededBy() {
    lint()
      .files(
        manifest().minSdk(10),
        java(
            """
                package test.pkg;

                import android.os.Build;
                import androidx.annotation.RequiresApi;

                @SuppressWarnings({"WeakerAccess", "unused"})
                public class TestPrecededByVersionCheck {
                    @RequiresApi(22)
                    public boolean requiresLollipop() {
                        return true;
                    }

                    public void test1() {
                        if (Build.VERSION.SDK_INT < 22) {
                            return;
                        }
                        requiresLollipop(); // OK 1
                    }

                    public void test2() {
                        if (Build.VERSION.SDK_INT < 18) {
                            return;
                        }
                        requiresLollipop(); // ERROR 1: API level could be 18-21
                    }

                    public void test3() {
                        requiresLollipop(); // ERROR 2: Version check is after
                        if (Build.VERSION.SDK_INT < 22) {
                            return;
                        }
                        requiresLollipop(); // OK 2
                    }

                    public void test4() {
                        if (Build.VERSION.SDK_INT > 22) {
                            return;
                        }
                        requiresLollipop(); // ERROR 3: Version check is going in the wrong direction: API can be 1
                    }

                    public void test5() {
                        if (Build.VERSION.SDK_INT > 22) {
                            // Something
                        } else {
                            return;
                        }
                        requiresLollipop(); // OK 3
                    }

                    public void test6() {
                        if (Build.VERSION.SDK_INT > 18) {
                            // Something
                        } else {
                            return;
                        }
                        requiresLollipop(); // ERROR 4: API level can be less than 22
                    }

                    public void test7() {
                        if (Build.VERSION.SDK_INT <= 22) {
                            // Something
                        } else {
                            return;
                        }
                        requiresLollipop(); // ERROR 5: API level can be less than 22
                    }
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
                src/test/pkg/TestPrecededByVersionCheck.java:24: Error: Call requires API level 22 (current min is 10): requiresLollipop [NewApi]
                        requiresLollipop(); // ERROR 1: API level could be 18-21
                        ~~~~~~~~~~~~~~~~
                src/test/pkg/TestPrecededByVersionCheck.java:28: Error: Call requires API level 22 (current min is 10): requiresLollipop [NewApi]
                        requiresLollipop(); // ERROR 2: Version check is after
                        ~~~~~~~~~~~~~~~~
                src/test/pkg/TestPrecededByVersionCheck.java:39: Error: Call requires API level 22 (current min is 10): requiresLollipop [NewApi]
                        requiresLollipop(); // ERROR 3: Version check is going in the wrong direction: API can be 1
                        ~~~~~~~~~~~~~~~~
                src/test/pkg/TestPrecededByVersionCheck.java:57: Error: Call requires API level 22 (current min is 10): requiresLollipop [NewApi]
                        requiresLollipop(); // ERROR 4: API level can be less than 22
                        ~~~~~~~~~~~~~~~~
                src/test/pkg/TestPrecededByVersionCheck.java:66: Error: Call requires API level 22 (current min is 10): requiresLollipop [NewApi]
                        requiresLollipop(); // ERROR 5: API level can be less than 22
                        ~~~~~~~~~~~~~~~~
                5 errors, 0 warnings
                """
      )
  }

  fun testNestedChecks() {
    lint()
      .files(
        manifest().minSdk(11),
        java(
            """
                package p1.p2;

                import android.os.Build;
                import android.widget.GridLayout;

                public class Class {
                    public void testEarlyExit1() {
                        // https://code.google.com/p/android/issues/detail?id=37728
                        if (Build.VERSION.SDK_INT < 14) return;

                        new GridLayout(null); // OK 1
                    }

                    public void testEarlyExit2() {
                        if (!Utils.isIcs()) {
                            return;
                        }

                        new GridLayout(null); // OK 2
                    }

                    public void testEarlyExit3(boolean nested) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                            return;
                        }

                        if (nested) {
                            new GridLayout(null); // OK 3
                        }
                    }

                    public void testEarlyExit4(boolean nested) {
                        if (nested) {
                            if (Utils.isIcs()) {
                                return;
                            }
                        }

                        new GridLayout(null); // ERROR

                        if (Utils.isIcs()) { // too late
                            //noinspection UnnecessaryReturnStatement
                            return;
                        }
                    }

                    private static class Utils {
                        public static boolean isIcs() {
                            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
                        }
                        public static boolean isGingerbread() {
                            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD;
                        }
                    }
                }"""
          )
          .indented(),
      )
      .skipTestModes(PARTIAL)
      .run()
      .expect(
        """
                src/p1/p2/Class.java:39: Error: Call requires API level 14 (current min is 11): new android.widget.GridLayout [NewApi]
                        new GridLayout(null); // ERROR
                        ~~~~~~~~~~~~~~
                src/p1/p2/Class.java:52: Warning: Unnecessary; SDK_INT is always >= 11 [ObsoleteSdkInt]
                            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD;
                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 1 warnings
                """
      )
  }

  fun testNestedChecks2() {
    lint()
      .files(
        manifest().minSdk(11),
        java(
            """
            package p1.p2;

            import android.os.Build;

            public class Class {
                public void testEarlyExit3(boolean nested) {
                  if (Build.VERSION.SDK_INT < 31) {
                        if (Build.VERSION.SDK_INT < 31) { // Unnecessary; SDK_INT is always >= 30
                            // something
                        }
                        if (Build.VERSION.SDK_INT > 31) { // Impossible
                            // something
                        }
                    }
                }
            }
            """
          )
          .indented(),
      )
      .skipTestModes(PARTIAL)
      .run()
      .expect(
        """
        src/p1/p2/Class.java:8: Warning: Unnecessary; Build.VERSION.SDK_INT < 31 is never true here (SDK_INT ≥ 11 and < 31) [ObsoleteSdkInt]
                    if (Build.VERSION.SDK_INT < 31) { // Unnecessary; SDK_INT is always >= 30
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/p1/p2/Class.java:11: Warning: Unnecessary; Build.VERSION.SDK_INT > 31 is never true here [ObsoleteSdkInt]
                    if (Build.VERSION.SDK_INT > 31) { // Impossible
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 2 warnings
        """
      )
  }

  fun testNestedChecksKotlin() {
    // Kotlin version of testNestedChecks. There are several important changes here:
    // The version check utility method is now defined as an expression body, so there
    // is no explicit "return" keyword (which the code used to look for).
    // Second, we're accessing the version check using property syntax, not a call, which
    // also required changes to the AST analysis.
    lint()
      .files(
        manifest().minSdk(11),
        kotlin(
            """
                package p1.p2

                import android.os.Build
                import android.widget.GridLayout

                class NestedChecks {
                    fun testEarlyExit1() {
                        // https://code.google.com/p/android/issues/detail?id=37728
                        if (Build.VERSION.SDK_INT < 14) return

                        GridLayout(null) // OK 1
                    }

                    fun testEarlyExit2() {
                        if (!Utils.isIcs) {
                            return
                        }

                        GridLayout(null) // OK 2
                    }

                    fun testEarlyExit3(nested: Boolean) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                            return
                        }

                        if (nested) {
                            GridLayout(null) // OK 3
                        }
                    }

                    fun testEarlyExit4(nested: Boolean) {
                        if (nested) {
                            if (Utils.isIcs) {
                                return
                            }
                        }

                        GridLayout(null) // ERROR

                        if (Utils.isIcs) { // too late
                            return
                        }
                    }

                    private object Utils {
                        val isIcs: Boolean
                            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH
                        val isGingerbread: Boolean
                            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD
                    }
                }"""
          )
          .indented(),
      )
      .skipTestModes(PARTIAL)
      .run()
      .expect(
        """
                src/p1/p2/NestedChecks.kt:39: Error: Call requires API level 14 (current min is 11): android.widget.GridLayout() [NewApi]
                        GridLayout(null) // ERROR
                        ~~~~~~~~~~
                src/p1/p2/NestedChecks.kt:50: Warning: Unnecessary; SDK_INT is always >= 11 [ObsoleteSdkInt]
                            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD
                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 1 warnings
                """
      )
  }

  fun testGetMinSdkVersionFromMethodName() {
    assertEquals(19, getMinSdkVersionFromMethodName("isAtLeastKitKat"))
    assertEquals(19, getMinSdkVersionFromMethodName("isKitKatSdk"))
    assertEquals(19, getMinSdkVersionFromMethodName("isKitKatSDK"))
    assertEquals(19, getMinSdkVersionFromMethodName("isRunningKitkatOrLater"))
    assertEquals(19, getMinSdkVersionFromMethodName("isKeyLimePieOrLater"))
    assertEquals(19, getMinSdkVersionFromMethodName("isKitKatOrHigher"))
    assertEquals(19, getMinSdkVersionFromMethodName("isKitKatOrNewer"))
    assertEquals(17, getMinSdkVersionFromMethodName("isRunningJellyBeanMR1OrLater"))
    assertEquals(20, getMinSdkVersionFromMethodName("isAtLeastKitKatWatch"))
    assertEquals(29, getMinSdkVersionFromMethodName("hasQ"))
    assertEquals(28, getMinSdkVersionFromMethodName("hasApi28"))
    assertEquals(28, getMinSdkVersionFromMethodName("isAtLeastApi28"))
    assertEquals(28, getMinSdkVersionFromMethodName("isAtLeastAPI_28"))
    assertEquals(28, getMinSdkVersionFromMethodName("isApi28OrLater"))
  }

  fun testVersionNameFromMethodName() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.pm.ShortcutManager;

                public abstract class VersionCheck {
                    public void test(ShortcutManager shortcutManager) {
                        // this requires API 26
                        if (isAtLeastOreo()) {
                            shortcutManager.removeAllDynamicShortcuts();
                        }
                        if (isOreoOrLater()) {
                            shortcutManager.removeAllDynamicShortcuts();
                        }
                        if (isOreoOrAbove()) {
                            shortcutManager.removeAllDynamicShortcuts();
                        }
                    }

                    public abstract boolean isAtLeastOreo();
                    public abstract boolean isOreoOrLater();
                    public abstract boolean isOreoOrAbove();
                }
                """
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testKotlinWhenStatement() {
    // Regression test for
    //   67712955: Kotlin when statement fails if subject is Build.VERSION.SDK_INT
    lint()
      .files(
        manifest().minSdk(4),
        kotlin(
            """
                import android.os.Build.VERSION.SDK_INT
                import android.os.Build.VERSION_CODES.N
                import android.text.Html

                fun String.fromHtml() : String
                {
                    return when {
                        SDK_INT >= N -> Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY)
                        else -> Html.fromHtml(this)
                    }.toString()
                }"""
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testWhenFallthrough() {
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import androidx.annotation.RequiresApi
                import android.os.Build.VERSION.SDK_INT
                fun test() {
                    when {
                      SDK_INT > 30 -> { something(); return; }
                      SDK_INT > 20 -> { somethingElse(); return; }
                      SDK_INT > 18 -> { somethingElse(); }
                      else -> return
                    }
                    // Here we know that SDK_INT is 19 or 20.
                    bar1() // OK 1
                    bar2() // ERROR 1
                }

                fun something() {}
                fun somethingElse() {}
                @RequiresApi(19) fun bar1() {}
                @RequiresApi(20) fun bar2() {}
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
                src/test/pkg/test.kt:14: Error: Call requires API level 20 (current min is 1): bar2 [NewApi]
                    bar2() // ERROR 1
                    ~~~~
                1 errors, 0 warnings
                """
      )
  }

  fun testKotlinWhenStatement_logicalOperatorsWithConstants() {
    // Regression test for
    //   242479753: false positives when logical operators and constants are combined
    lint()
      .files(
        manifest().minSdk(4),
        kotlin(
            """
                import android.os.Build.VERSION.SDK_INT
                import android.os.Build.VERSION_CODES.N
                import android.text.Html

                @Suppress("ObsoleteSdkInt")
                fun String.fromHtml() : String
                {
                    return when {
                        false || SDK_INT >= N -> Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY)
                        true || SDK_INT >= N -> Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY) // ERROR
                        false && SDK_INT >= N -> Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY)
                        true && SDK_INT >= N -> Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY)
                        else -> Html.fromHtml(this)
                    }.toString()
                }"""
          )
          .indented(),
      )
      .run()
      .expect(
        """
            src/test.kt:10: Warning: Field requires API level 24 (current min is 4): android.text.Html#FROM_HTML_MODE_LEGACY [InlinedApi]
                    true || SDK_INT >= N -> Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY) // ERROR
                                                                ~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test.kt:10: Error: Call requires API level 24 (current min is 4): android.text.Html#fromHtml [NewApi]
                    true || SDK_INT >= N -> Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY) // ERROR
                                                 ~~~~~~~~
            1 errors, 1 warnings
            """
      )
  }

  fun testKotlinWhenStatement2() {
    // Regression test for issue 69661204
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import android.os.Build
                import androidx.annotation.RequiresApi

                @RequiresApi(21)
                fun requires21() { }

                @RequiresApi(23)
                fun requires23() { }

                fun requiresNothing() { }

                fun test() {
                    when {
                        Build.VERSION.SDK_INT >= 21 -> requires21()
                        Build.VERSION.SDK_INT >= 23 -> requires23() // never possible
                        Build.VERSION.SDK_INT >= 25 -> requires23() // never possible
                        else -> requiresNothing()
                    }
                    when {
                        Build.VERSION.SDK_INT >= 23 -> requires23()
                        Build.VERSION.SDK_INT >= 21 -> requires21()
                        else -> requiresNothing()
                    }
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
        src/test/pkg/test.kt:17: Warning: Unnecessary; Build.VERSION.SDK_INT >= 23 is never true here [ObsoleteSdkInt]
                Build.VERSION.SDK_INT >= 23 -> requires23() // never possible
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/test.kt:18: Warning: Unnecessary; Build.VERSION.SDK_INT >= 25 is never true here [ObsoleteSdkInt]
                Build.VERSION.SDK_INT >= 25 -> requires23() // never possible
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 2 warnings
        """
      )
  }

  fun testSdkIntCheckVariable() {
    // Regression test for https://issuetracker.google.com/262376528
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import android.content.Context
                import android.os.Build
                import androidx.annotation.RequiresApi

                fun check(
                    darkTheme: Boolean,
                    context: Context
                ) {
                    val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    val colorScheme = when {
                        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
                        dynamicColor && !darkTheme -> dynamicLightColorScheme(context)
                        else -> TODO()
                    }
                }

                class ColorScheme
                @RequiresApi(Build.VERSION_CODES.S)
                fun dynamicDarkColorScheme(context: Context): ColorScheme = TODO()

                @RequiresApi(Build.VERSION_CODES.S)
                fun dynamicLightColorScheme(context: Context): ColorScheme = TODO()
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun testSdkIntCheckFields() {
    // Regression test for b/303549797
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import android.content.Context
                import android.os.Build
                import androidx.annotation.RequiresApi

                val dynamicColorVal = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                var dynamicColorVar = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

                fun check(
                    darkTheme: Boolean,
                    context: Context
                ) {
                    // Safe
                    if (dynamicColorVal && darkTheme) dynamicDarkColorScheme(context)
                    if (dynamicColorVal && !darkTheme) dynamicLightColorScheme(context)

                    // Unsafe
                    if (dynamicColorVar && darkTheme) dynamicDarkColorScheme(context)
                    if (dynamicColorVar && !darkTheme) dynamicLightColorScheme(context)
                }

                class ColorScheme
                @RequiresApi(Build.VERSION_CODES.S)
                fun dynamicDarkColorScheme(context: Context): ColorScheme = TODO()

                @RequiresApi(Build.VERSION_CODES.S)
                fun dynamicLightColorScheme(context: Context): ColorScheme = TODO()
                """
          )
          .indented(),
        java(
            """
            package test.pkg;

            import android.content.Context;
            import android.os.Build;
            import androidx.annotation.RequiresApi;

            class C {
                final boolean dynamicColorFinal = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
                boolean dynamicColorNotFinal = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;

                void check(
                    boolean darkTheme,
                    boolean context
                ) {
                    // Safe
                    if (dynamicColorFinal && darkTheme) { dynamicDarkColorScheme(context); }
                    if (dynamicColorFinal && !darkTheme) { dynamicLightColorScheme(context); }

                    // Unsafe
                    if (dynamicColorNotFinal && darkTheme) { dynamicDarkColorScheme(context); }
                    if (dynamicColorNotFinal && !darkTheme) { dynamicLightColorScheme(context); }
                }

                class ColorScheme { }
                @RequiresApi(Build.VERSION_CODES.S)
                ColorScheme dynamicDarkColorScheme(Context context) { TODO(); }

                @RequiresApi(Build.VERSION_CODES.S)
                ColorScheme dynamicLightColorScheme(Context context) { TODO(); }
            }
          """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
          src/test/pkg/C.java:20: Error: Call requires API level 31 (current min is 1): dynamicDarkColorScheme [NewApi]
                  if (dynamicColorNotFinal && darkTheme) { dynamicDarkColorScheme(context); }
                                                           ~~~~~~~~~~~~~~~~~~~~~~
          src/test/pkg/C.java:21: Error: Call requires API level 31 (current min is 1): dynamicLightColorScheme [NewApi]
                  if (dynamicColorNotFinal && !darkTheme) { dynamicLightColorScheme(context); }
                                                            ~~~~~~~~~~~~~~~~~~~~~~~
          src/test/pkg/ColorScheme.kt:19: Error: Call requires API level 31 (current min is 1): dynamicDarkColorScheme [NewApi]
              if (dynamicColorVar && darkTheme) dynamicDarkColorScheme(context)
                                                ~~~~~~~~~~~~~~~~~~~~~~
          src/test/pkg/ColorScheme.kt:20: Error: Call requires API level 31 (current min is 1): dynamicLightColorScheme [NewApi]
              if (dynamicColorVar && !darkTheme) dynamicLightColorScheme(context)
                                                 ~~~~~~~~~~~~~~~~~~~~~~~
          4 errors, 0 warnings
        """
      )
  }

  fun testIfElse() {
    // Regression test for issue 69661204
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import android.os.Build
                import androidx.annotation.RequiresApi

                @RequiresApi(21)
                fun requires21() { }

                @RequiresApi(23)
                fun requires23() { }

                fun requiresNothing() { }

                fun test() {
                    if (Build.VERSION.SDK_INT >= 21) requiresNothing()
                    else if (Build.VERSION.SDK_INT >= 23) requiresNothing() // Warn: never possible; we know SDK_INT < 21
                    else if (Build.VERSION.SDK_INT >= 25) requires23() // Warn: never possible; we know SDK_INT < 21
                    else requiresNothing()

                    if (true || Build.VERSION.SDK_INT >= 21) requiresNothing() // OK
                    else if (Build.VERSION.SDK_INT >= 23) requiresNothing() // Warn: SDK_INT is always < 21 here

                    if (Build.VERSION.SDK_INT >= 21 && false) requiresNothing()
                    else if (Build.VERSION.SDK_INT >= 23) requiresNothing() // OK 2: we're not certain SDK_INT < 21 here

                    // test blocks
                    if (Build.VERSION.SDK_INT < 11) {
                        requiresNothing()
                    } else if (Build.VERSION.SDK_INT < 19) {
                        requiresNothing()
                    } else if (true) {
                        requires23()  // ERROR 1
                    } else {
                        requiresNothing()
                    }

                    // test non-blocks
                    if (Build.VERSION.SDK_INT < 11) requiresNothing()
                    else if (Build.VERSION.SDK_INT < 19) requiresNothing() // never true
                    else if (true) requires23() // ERROR 2
                    else requiresNothing()

                    // more else clauses
                    if (Build.VERSION.SDK_INT < 11) {
                        requiresNothing()
                    } else if (Build.VERSION.SDK_INT < 13) {
                    } else if (Build.VERSION.SDK_INT < 17) {
                    } else if (true) {
                        requires23() // ERROR 3
                    }

                    // nested if's
                    if (Build.VERSION.SDK_INT < 11) {
                    } else if (Build.VERSION.SDK_INT < 13) {
                    } else if (Build.VERSION.SDK_INT < 17) {
                    } else if (true) {
                        // Here we know SDK_INT >= 17
                        if (Build.VERSION.SDK_INT < 11) { // Warn: never possible
                        } else if (Build.VERSION.SDK_INT < 13) { // Warn: never possible
                        } else {
                            requires23() // ERROR 4
                        }
                    }
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
        src/test/pkg/test.kt:32: Error: Call requires API level 23 (current min is 19): requires23 [NewApi]
                requires23()  // ERROR 1
                ~~~~~~~~~~
        src/test/pkg/test.kt:40: Error: Call requires API level 23 (current min is 19): requires23 [NewApi]
            else if (true) requires23() // ERROR 2
                           ~~~~~~~~~~
        src/test/pkg/test.kt:49: Error: Call requires API level 23 (current min is 17): requires23 [NewApi]
                requires23() // ERROR 3
                ~~~~~~~~~~
        src/test/pkg/test.kt:61: Error: Call requires API level 23 (current min is 17): requires23 [NewApi]
                    requires23() // ERROR 4
                    ~~~~~~~~~~
        src/test/pkg/test.kt:16: Warning: Unnecessary; Build.VERSION.SDK_INT >= 23 is never true here [ObsoleteSdkInt]
            else if (Build.VERSION.SDK_INT >= 23) requiresNothing() // Warn: never possible; we know SDK_INT < 21
                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/test.kt:17: Warning: Unnecessary; Build.VERSION.SDK_INT >= 25 is never true here [ObsoleteSdkInt]
            else if (Build.VERSION.SDK_INT >= 25) requires23() // Warn: never possible; we know SDK_INT < 21
                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/test.kt:21: Warning: Unnecessary; Build.VERSION.SDK_INT >= 23 is never true here [ObsoleteSdkInt]
            else if (Build.VERSION.SDK_INT >= 23) requiresNothing() // Warn: SDK_INT is always < 21 here
                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/test.kt:58: Warning: Unnecessary; Build.VERSION.SDK_INT < 11 is never true here [ObsoleteSdkInt]
                if (Build.VERSION.SDK_INT < 11) { // Warn: never possible
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/test/pkg/test.kt:59: Warning: Unnecessary; Build.VERSION.SDK_INT < 13 is never true here [ObsoleteSdkInt]
                } else if (Build.VERSION.SDK_INT < 13) { // Warn: never possible
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~
        4 errors, 5 warnings
        """
      )
  }

  fun testKotlinHelper() {
    // Regression test for issue 64550633
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import android.os.Build
                import android.os.Build.VERSION_CODES.KITKAT

                inline fun fromApi(value: Int, action: () -> Unit) {
                    if (Build.VERSION.SDK_INT >= value) {
                        action()
                    }
                }

                fun fromApiNonInline(value: Int, action: () -> Unit) {
                    if (Build.VERSION.SDK_INT >= value) {
                        action()
                    }
                }

                inline fun notFromApi(value: Int, action: () -> Unit) {
                    if (Build.VERSION.SDK_INT < value) {
                        action()
                    }
                }

                fun test1() {
                    fromApi(KITKAT) {
                        // Example of a Java 7+ field
                        val cjkExtensionC = Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C // OK
                    }
                }

                fun test2() {
                    fromApiNonInline(KITKAT) {
                        val cjkExtensionC = Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C // OK
                    }
                }

                fun test3() {
                    notFromApi(KITKAT) {
                        val cjkExtensionC = Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C // ERROR
                    }
                }
                """
          )
          .indented()
      )
      .run()
      .expect(
        """
            src/test/pkg/test.kt:39: Error: Field requires API level 19 (current min is 1): java.lang.Character.UnicodeBlock#CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C [NewApi]
                    val cjkExtensionC = Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C // ERROR
                                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
      )
  }

  fun testKotlinEarlyExit1() {
    // Regression test for issue 71560541: Wrong API condition
    // Root cause: https://youtrack.jetbrains.com/issue/IDEA-184544
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import android.app.NotificationChannel
                import android.app.NotificationManager
                import android.content.Context
                import android.os.Build

                fun foo1(context: Context) {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    if (Build.VERSION.SDK_INT < 26 || notificationManager == null) {
                        return
                    }

                    val channel = NotificationChannel("id", "Test", NotificationManager.IMPORTANCE_DEFAULT)
                    channel.description = "test"
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun testKotlinEarlyExit2() {
    // Regression test for issue 71560541: Wrong API condition, part 2
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import android.app.NotificationChannel
                import android.app.NotificationManager
                import android.content.Context
                import android.os.Build

                fun foo2(context: Context) {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

                    val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        NotificationChannel("id", "Test", NotificationManager.IMPORTANCE_DEFAULT)
                    } else {
                        return
                    }

                    channel.description = "test"
                }"""
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun testEarlyExit() {
    // Regression test for b/247135738
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import android.os.Build
                import androidx.annotation.RequiresApi

                fun methodWithReflection() {
                    if (Build.VERSION.SDK_INT < 28) return

                    requires27() // OK 1
                    requires28() // OK 2
                    requires29() // ERROR 1
                    try {
                        requires27() // OK 3
                        requires28() // OK 4
                        requires29() // ERROR 2
                    } catch (e: Exception) {
                        return
                    }
                }

                @RequiresApi(27) fun requires27() { }
                @RequiresApi(28) fun requires28() { }
                @RequiresApi(29) fun requires29() { }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
            src/test/pkg/test.kt:11: Error: Call requires API level 29 (current min is 1): requires29 [NewApi]
                requires29() // ERROR 1
                ~~~~~~~~~~
            src/test/pkg/test.kt:15: Error: Call requires API level 29 (current min is 1): requires29 [NewApi]
                    requires29() // ERROR 2
                    ~~~~~~~~~~
            2 errors, 0 warnings
            """
      )
  }

  fun testEarlyExit2() {
    // like testEarlyExit, but with the early exit inside an alternative if.
    //
    // The early exit finder isn't super accurate; in particular, it doesn't enforce that
    // the earlier exit is really going to always run. This normally isn't a problem (code
    // doesn't tend to get written that way), but this test encodes the current behavior
    // both as documentation that this is indeed the current limited behavior, and as a goal
    // for us to improve this.
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import android.os.Build
                import androidx.annotation.RequiresApi

                fun methodWithReflection() {
                    when {
                        false -> {
                            if (Build.VERSION.SDK_INT < 28) return
                        }
                        else -> {
                            // This shouldn't be OK because the above if-check doesn't apply
                            // here but the current exit-finder doesn't limit itself to known-executed
                            // code.
                            requires27() // OK 1
                            requires28() // OK 2
                            requires29() // ERROR 1
                            try {
                                requires27() // OK 3
                                requires28() // OK 4
                                requires29() // ERROR 2
                            } catch (e: Exception) {
                                return
                            }
                        }
                    }
                }

                @RequiresApi(27) fun requires27() { }
                @RequiresApi(28) fun requires28() { }
                @RequiresApi(29) fun requires29() { }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
            src/test/pkg/test.kt:17: Error: Call requires API level 29 (current min is 1): requires29 [NewApi]
                        requires29() // ERROR 1
                        ~~~~~~~~~~
            src/test/pkg/test.kt:21: Error: Call requires API level 29 (current min is 1): requires29 [NewApi]
                            requires29() // ERROR 2
                            ~~~~~~~~~~
            2 errors, 0 warnings
            """
      )
  }

  fun testCombineConstraintAndEarlyExit() {
    // Here we a locally inferred constraint which doesn't satisfy the API requirement,
    // but there's an earlier exit we need to look up.
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.os.Build;
                import androidx.annotation.RequiresApi;

                public class Test {
                    public static void canMutate(Object context, Object mediaSession) {
                        if (Build.VERSION.SDK_INT < 21 || context == null || mediaSession == null) {
                            return;
                        }
                        if (Build.VERSION.SDK_INT >= 29) {
                            requires29();
                        } else if (Build.VERSION.SDK_INT >= 28) {
                            requires28();
                        } else {
                            // API 21+
                            requires21();
                        }
                    }

                    @RequiresApi(21) private static void requires21() { }
                    @RequiresApi(28) private static void requires28() { }
                    @RequiresApi(29) private static void requires29() { }
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun testWhenEarlyReturns() {
    lint()
      .files(
        manifest().minSdk(16),
        kotlin(
            """
                package test.pkg

                import android.os.Build.VERSION.SDK_INT
                import androidx.annotation.RequiresApi

                fun testWhen1() {
                    when {
                        SDK_INT > 30 -> { }
                        else -> return
                    }
                    requires21() // OK 1: SDK_INT is never <= 30 here
                }

                fun testWhen1b() {
                    when {
                        SDK_INT > 30 -> { return }
                        SDK_INT > 21 -> { return }
                    }
                    requires21() // ERROR 2: SDK_INT can be 1
                }

                fun testWhen1c() {
                    when {
                        false -> { return }
                        SDK_INT < 21 -> { return }
                    }
                    requires21() // OK 2 - SDK_INT is always >= 21
                }

                fun testWhen1d() {
                    when {
                        SDK_INT > 30 -> { return }
                    }
                    requires21() // ERROR 3: SDK_INT can be 16 through 30
                }

                fun testWhen2() {
                    when {
                        SDK_INT > 30 -> { }
                        SDK_INT > 21 -> { }
                        else -> return
                    }
                    requires21() // OK 3 -- we return for anything less than 22
                    requires24() // ERROR 4: SDK_INT can be > 21
                }

                fun testWhen3() {
                    when {
                        SDK_INT > 30 -> { }
                        SDK_INT > 22 -> { }
                        else -> return // return if SDK_INT <= 22, meaning after this when, SDK_INT > 22, so SDK_INT >= 23
                    }
                    requires21() // OK 4: SDK_INT > 22
                    requires22() // OK 5: SDK_INT > 22
                    requires23() // OK 6: SDK_INT > 22
                    requires24() // ERROR 5: SDK_INT might be 23
                }

                fun testWhen5() {
                    when {
                        SDK_INT > 20 -> { }
                        SDK_INT > 30 -> { } // never true
                        else -> return
                    }
                    requires20() // OK 7
                    requires21() // OK 8
                    requires22() // ERROR 6 -- SDK_INT can be 21
                    requires23() // ERROR 7
                }

                fun testWhen6() {
                    when {
                        SDK_INT > 30 -> {
                            requires21() // OK 9: We know SDK_INT >= 31
                        }
                        SDK_INT >= 21 -> {
                            requires21() // OK 10: We know SDK_INT >= 21
                        }
                        SDK_INT >= 20 -> {
                            requires21() // ERROR 8: SDK_INT can be 20
                        }
                        SDK_INT >= 19 -> {
                            requires21() // ERROR 9: SDK_INT can be 19
                        }
                        else -> return
                    }
                }

                fun testNestedWhen() {
                    when {
                        SDK_INT < 30 -> {
                            if (true) {
                              val temp = 0
                              return
                            } else {
                              return
                            }
                        }
                    }
                    requires21() // OK 11: SDK_INT always >= 30 here
                }

                fun testVersionUtility() {
                    when {
                        isAtLeast(30) -> requires21()
                        isAtLeast(22) -> {
                            requires24() // ERROR 10
                            requires21() // OK 12
                        }
                        else -> return
                    }
                    requires24() // ERROR 11 -- SDK_INT could be 30 here
                }

                fun testWhenCase(foo: Boolean) {
                    when {
                        SDK_INT < 18 -> { }
                        SDK_INT < 21 -> { }
                        foo -> requires21() // OK 14
                        else -> {
                            requires21() // OK 15
                        }
                    }
                }

                fun testMultipleReturns(foo: Boolean) {
                    when {
                        SDK_INT < 21 -> {
                            if (true) return else return
                        }
                    }
                    requires21() // OK 16
                }

                fun isAtLeast(api: Int): Boolean {
                    return SDK_INT >= api
                }

                @RequiresApi(20) fun requires20() { }
                @RequiresApi(21) fun requires21() { }
                @RequiresApi(22) fun requires22() { }
                @RequiresApi(23) fun requires23() { }
                @RequiresApi(24) fun requires24() { }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      // Error message varies in partial mode
      .skipTestModes(PARTIAL)
      .run()
      .expect(
        """
        src/test/pkg/test.kt:19: Error: Call requires API level 21 (current min is 16): requires21 [NewApi]
            requires21() // ERROR 2: SDK_INT can be 1
            ~~~~~~~~~~
        src/test/pkg/test.kt:34: Error: Call requires API level 21 (current min is 16): requires21 [NewApi]
            requires21() // ERROR 3: SDK_INT can be 16 through 30
            ~~~~~~~~~~
        src/test/pkg/test.kt:44: Error: Call requires API level 24 (current min is 16): requires24 [NewApi]
            requires24() // ERROR 4: SDK_INT can be > 21
            ~~~~~~~~~~
        src/test/pkg/test.kt:56: Error: Call requires API level 24 (current min is 16): requires24 [NewApi]
            requires24() // ERROR 5: SDK_INT might be 23
            ~~~~~~~~~~
        src/test/pkg/test.kt:67: Error: Call requires API level 22 (current min is 16): requires22 [NewApi]
            requires22() // ERROR 6 -- SDK_INT can be 21
            ~~~~~~~~~~
        src/test/pkg/test.kt:68: Error: Call requires API level 23 (current min is 16): requires23 [NewApi]
            requires23() // ERROR 7
            ~~~~~~~~~~
        src/test/pkg/test.kt:80: Error: Call requires API level 21 (current min is 20): requires21 [NewApi]
                    requires21() // ERROR 8: SDK_INT can be 20
                    ~~~~~~~~~~
        src/test/pkg/test.kt:83: Error: Call requires API level 21 (current min is 19): requires21 [NewApi]
                    requires21() // ERROR 9: SDK_INT can be 19
                    ~~~~~~~~~~
        src/test/pkg/test.kt:107: Error: Call requires API level 24 (current min is 22): requires24 [NewApi]
                    requires24() // ERROR 10
                    ~~~~~~~~~~
        src/test/pkg/test.kt:112: Error: Call requires API level 24 (current min is 16): requires24 [NewApi]
            requires24() // ERROR 11 -- SDK_INT could be 30 here
            ~~~~~~~~~~
        src/test/pkg/test.kt:62: Warning: Unnecessary; SDK_INT > 30 is never true here [ObsoleteSdkInt]
                SDK_INT > 30 -> { } // never true
                ~~~~~~~~~~~~
        10 errors, 1 warnings
        """
      )
  }

  fun testUnconditionalExitViaWhen() {
    // Makes sure we correctly detect that you unconditionally return when the statement
    // is a when statement.
    lint()
      .files(
        manifest().minSdk(16),
        kotlin(
            """
                package test.pkg

                import android.os.Build.VERSION.SDK_INT
                import androidx.annotation.RequiresApi

                fun testNestedWhen2() {
                    when {
                        SDK_INT < 30 -> {
                            when {
                                true -> {
                                    val temp = 0
                                    return
                                }

                                else -> {
                                    return
                                }
                            }
                        }
                    }
                    requires21() // OK 1: SDK_INT always >= 30 here
                }

                @RequiresApi(21) fun requires21() { }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun testPreviousWhenStatements() {
    lint()
      .files(
        manifest().minSdk(16),
        kotlin(
            """
                package test.pkg

                import android.os.Build.VERSION.SDK_INT
                import androidx.annotation.RequiresApi

                fun testWhen0() {
                    when {
                        SDK_INT >= 20 -> {
                            requires21() // ERROR 1: SDK_INT can be 20
                        }
                        else -> {
                            requires21() // ERROR 2: SDK_INT can be less than 21
                        }
                    }
                }

                fun testWhen1() {
                    when {
                        SDK_INT >= 21 -> {
                            requires21() // OK 1: We know SDK_INT >= 21
                        }
                        else -> {
                            requires21() // ERROR 3: SDK_INT can be less than 21
                        }
                    }
                }

                fun testWhen1() {
                    when {
                        SDK_INT <= 21 -> {
                            requires21() // ERROR 4: SDK_INT can be less than 21
                        }
                        else -> {
                            requires21() // OK 2: SDK_INT is never less than 21
                        }
                    }
                }

                fun testWhen2() {
                    when {
                        SDK_INT >= 22 -> {
                            requires21() // OK 3: We know SDK_INT >= 22
                        }
                        else -> {
                            requires21() // ERROR 5: SDK_INT can be less than 21
                        }
                    }
                }

                fun testWhen3() {
                    when {
                        SDK_INT >= 23 -> {
                            requires21() // OK 4: We know SDK_INT >= 23
                        }
                        else -> {
                            requires21() // ERROR 6: SDK_INT can be 22
                        }
                    }
                }

                fun testWhen4() {
                    when {
                        SDK_INT >= 24 -> {
                            requires21() // OK 5: We know SDK_INT >= 24
                        }
                        else -> {
                            requires21() // ERROR 7: SDK_INT can be 22 or 23
                        }
                    }
                }

                fun testWhen5() {
                    when {
                        SDK_INT >= 30 -> { }
                        SDK_INT >= 22 -> {
                            requires21() // OK 6: We know SDK_INT >= 22
                        }
                        else -> {
                            requires21() // ERROR 8: SDK_INT can be less than 21
                        }
                    }
                }

                @RequiresApi(21)
                fun requires21() { }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
            src/test/pkg/test.kt:9: Error: Call requires API level 21 (current min is 20): requires21 [NewApi]
                        requires21() // ERROR 1: SDK_INT can be 20
                        ~~~~~~~~~~
            src/test/pkg/test.kt:12: Error: Call requires API level 21 (current min is 16): requires21 [NewApi]
                        requires21() // ERROR 2: SDK_INT can be less than 21
                        ~~~~~~~~~~
            src/test/pkg/test.kt:23: Error: Call requires API level 21 (current min is 16): requires21 [NewApi]
                        requires21() // ERROR 3: SDK_INT can be less than 21
                        ~~~~~~~~~~
            src/test/pkg/test.kt:31: Error: Call requires API level 21 (current min is 16): requires21 [NewApi]
                        requires21() // ERROR 4: SDK_INT can be less than 21
                        ~~~~~~~~~~
            src/test/pkg/test.kt:45: Error: Call requires API level 21 (current min is 16): requires21 [NewApi]
                        requires21() // ERROR 5: SDK_INT can be less than 21
                        ~~~~~~~~~~
            src/test/pkg/test.kt:56: Error: Call requires API level 21 (current min is 16): requires21 [NewApi]
                        requires21() // ERROR 6: SDK_INT can be 22
                        ~~~~~~~~~~
            src/test/pkg/test.kt:67: Error: Call requires API level 21 (current min is 16): requires21 [NewApi]
                        requires21() // ERROR 7: SDK_INT can be 22 or 23
                        ~~~~~~~~~~
            src/test/pkg/test.kt:79: Error: Call requires API level 21 (current min is 16): requires21 [NewApi]
                        requires21() // ERROR 8: SDK_INT can be less than 21
                        ~~~~~~~~~~
            8 errors, 0 warnings
            """
      )
  }

  fun testWhenSubject() {
    lint()
      .files(
        manifest().minSdk(16),
        kotlin(
            """
                package test.pkg

                import android.os.Build.VERSION.SDK_INT
                import androidx.annotation.RequiresApi

                fun testWhenSubject() {
                    when (SDK_INT) {
                        in 1..15 -> { }
                        16 -> { }
                        in 17..20 -> requires21() // ERROR
                        in 24..30 -> requires21() // OK
                    }
                }

                @RequiresApi(21)
                fun requires21() { }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
            src/test/pkg/test.kt:10: Error: Call requires API level 21 (current min is 17): requires21 [NewApi]
                    in 17..20 -> requires21() // ERROR
                                 ~~~~~~~~~~
            1 errors, 0 warnings
            """
      )
  }

  fun testWhenSubject2() {
    // Regression test for b/247146231
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import android.os.Build.VERSION.SDK_INT
                import androidx.annotation.RequiresApi

                fun test1() {
                    if (SDK_INT in 24..28) {
                        requires21() // OK 1
                        requires24() // OK 2
                    } else if (SDK_INT in 21..24) {
                        requires21() // OK 3
                        requires24() // ERROR 1
                    }

                    if (SDK_INT in 21 until 24) {
                        requires21() // OK 4
                        requires24() // ERROR 2
                    }
                }

                @RequiresApi(21) fun requires21() { }
                @RequiresApi(24) fun requires24() { }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
            src/test/pkg/test.kt:12: Error: Call requires API level 24 (current min is 21): requires24 [NewApi]
                    requires24() // ERROR 1
                    ~~~~~~~~~~
            src/test/pkg/test.kt:17: Error: Call requires API level 24 (current min is 21): requires24 [NewApi]
                    requires24() // ERROR 2
                    ~~~~~~~~~~
            2 errors, 0 warnings
            """
      )
  }

  fun testKotlinWhenRange1() {
    // Regression test for b/247135738
    lint()
      .files(
        manifest().minSdk(4),
        kotlin(
            """
                package test.pkg

                import android.os.Build
                import androidx.annotation.RequiresApi

                fun test() {
                    when (Build.VERSION.SDK_INT) {
                        in 1 until 17 -> { }
                        19, in 19..20 -> {
                            requires19() // OK 1
                        }
                        in 21..24 -> {
                            requires21() // OK 2
                            requires24() // ERROR 1
                        }
                        in 25..28 -> {
                            requires21() // OK 3
                            requires24() // OK 4
                        }
                        else -> {
                            requires24() // ERROR 2: API level can be 17 or 18!
                        }
                    }
                }

                @RequiresApi(19) fun requires19() { }
                @RequiresApi(21) fun requires21() { }
                @RequiresApi(24) fun requires24() { }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
            src/test/pkg/test.kt:14: Error: Call requires API level 24 (current min is 21): requires24 [NewApi]
                        requires24() // ERROR 1
                        ~~~~~~~~~~
            src/test/pkg/test.kt:21: Error: Call requires API level 24 (current min is 17): requires24 [NewApi]
                        requires24() // ERROR 2: API level can be 17 or 18!
                        ~~~~~~~~~~
            2 errors, 0 warnings
            """
      )
  }

  fun testKotlinWhenRange2() {
    // Regression test for b/247135738
    lint()
      .files(
        manifest().minSdk(4),
        kotlin(
            """
                package test.pkg

                import android.os.Build
                import androidx.annotation.RequiresApi

                fun test2() {
                    when (Build.VERSION.SDK_INT) {
                        in 1 until 18 -> { return }
                        18 -> { return }
                        in 19..20 -> { return }
                        else -> {
                            requires21() // OK 1
                            requires24() // ERROR 1
                        }
                    }
                    requires21() // OK 2
                    requires24() // ERROR 2
                }

                @RequiresApi(21) fun requires21() { }
                @RequiresApi(24) fun requires24() { }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
            src/test/pkg/test.kt:13: Error: Call requires API level 24 (current min is 21): requires24 [NewApi]
                        requires24() // ERROR 1
                        ~~~~~~~~~~
            src/test/pkg/test.kt:17: Error: Call requires API level 24 (current min is 4): requires24 [NewApi]
                requires24() // ERROR 2
                ~~~~~~~~~~
            2 errors, 0 warnings
            """
      )
  }

  fun testNestedIfs() {
    // Regression test for issue 67553351
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.os.Build;
                import androidx.annotation.RequiresApi;

                @SuppressWarnings({"unused", ClassNameDiffersFromFileName})
                public class NestedIfs {
                    @RequiresApi(20)
                    private void requires20() {
                    }

                    @RequiresApi(23)
                    private void requires23() {
                    }

                    void test() {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                requires23();
                            } else {
                                requires20();
                            }
                        }
                    }
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun testApplyBlock() {
    // Regression test for 71809249: False positive when using lambdas and higher-order functions
    lint()
      .files(
        kotlin(
            """
                package com.example.lintexample

                import android.app.NotificationChannel
                import android.app.NotificationManager
                import android.content.Context
                import android.os.Build
                import android.app.Activity

                class MainActivity : Activity() {

                    fun test(notificationChannel: NotificationChannel) {
                        val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.applyForOreoOrAbove {
                            createNotificationChannel(notificationChannel)
                        }

                    }
                }

                inline fun <T> T.applyForOreoOrAbove(block: T.() -> Unit): T {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        block()
                    }
                    return this
                }
                """
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun test110576968() {
    // Regression test for 110576968: NewApi isn't resolving a static final constant API value
    lint()
      .files(
        manifest().minSdk(15),
        java(
            """
                package test.pkg;

                import android.os.Build;

                @SuppressWarnings("unused")
                public class WorkManagerTest {
                    public void test2() {
                        if (Build.VERSION.SDK_INT >= WorkManager.MIN_JOB_SCHEDULER_API_LEVEL) {
                            SystemJobScheduler scheduler = new SystemJobScheduler(); // OK
                        }
                    }
                }"""
          )
          .indented(),
        java(
            """
                package test.pkg;

                @SuppressWarnings("unused")
                public class WorkManager {
                    public static final int MIN_JOB_SCHEDULER_API_LEVEL = 23;
                }
                """
          )
          .indented(),
        java(
            """
                package test.pkg;

                import androidx.annotation.RequiresApi;
                import android.app.job.JobScheduler;

                @RequiresApi(WorkManager.MIN_JOB_SCHEDULER_API_LEVEL)
                public class SystemJobScheduler {
                    public SystemJobScheduler() { }

                    private JobScheduler mJobScheduler;    public void schedule(int systemId) {
                        mJobScheduler.getPendingJob(systemId);
                    }
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
            src/test/pkg/SystemJobScheduler.java:11: Error: Call requires API level 24 (current min is 23): android.app.job.JobScheduler#getPendingJob [NewApi]
                    mJobScheduler.getPendingJob(systemId);
                                  ~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
      )
  }

  fun test113198297() {
    // Regression test for https://issuetracker.google.com/113198297
    lint()
      .files(
        manifest().minSdk(15),
        kotlin(
            """
                package test.pkg

                import android.os.Build
                import androidx.annotation.RequiresApi

                class UnconditionalReturn2 {
                    fun test() =
                            run {
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                                    println("hello something")
                                    return
                                }
                                requires21()
                            }

                    @RequiresApi(21)
                    fun requires21() {
                    }
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun testExceptionsAndErrorsAsExitPoints() {
    // TODO(b/350536808)
    if (useFirUast()) {
      return
    }
    // Regression lifted from issue 117793069
    lint()
      .files(
        kotlin(
            """
                import android.app.Activity

                import android.os.Build.VERSION.SDK_INT

                class ExitTest: Activity() {

                    fun testThrow() {
                        if (SDK_INT < 11) {
                            throw IllegalStateException()
                        }
                        val actionBar = getActionBar() // OK 1
                    }

                    fun testError() {
                        if (SDK_INT < 11) {
                            error("Api")
                        }
                        val actionBar = getActionBar() // OK 2
                    }

                    fun testTodo() {
                        if (SDK_INT < 11) {
                            TODO()
                        }
                        val actionBar = getActionBar() // OK 3
                    }

                    fun testCustomMethod() {
                        if (SDK_INT < 11) {
                            willThrow()
                        }
                        val actionBar = getActionBar() // OK 4
                    }

                    private fun willThrow(): Nothing {
                    }
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectInlinedMessages(false)
  }

  fun testNotEquals() {
    // Regression test lifted from issue 117793069
    lint()
      .files(
        manifest().minSdk(1),
        kotlin(
          """

                import android.app.Activity

                import android.widget.TextView
                import android.os.Build.VERSION.SDK_INT

                class SameTest : Activity() {

                    fun test(textView: TextView) {
                        if (SDK_INT != 10 || /*Call requires API level 11 (current min is 10): android.app.Activity#getActionBar*/getActionBar/**/() == null) { // ERROR 1
                        }
                        if (SDK_INT != 11 || getActionBar() == null) { // OK 1
                        }
                        if (SDK_INT != 12 || getActionBar() == null) { // OK 2
                        }
                    }
                }
                """
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectInlinedMessages(false)
  }

  fun testNotEquals2() {
    // Regression test for issue 69661204
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import android.os.Build
                import androidx.annotation.RequiresApi
                import android.os.Build.VERSION.SDK_INT

                fun test() {
                    if (SDK_INT != 22 || requires23()) { }    // ERROR 1
                    when {
                        SDK_INT != 22 || requires23() -> { }  // ERROR 2
                    }
                    if (SDK_INT != 23 || requires23()) { }    // OK 1
                    when {
                        SDK_INT != 23 || requires23() -> { }  // OK 2
                    }
                    if (SDK_INT != 24 || requires23()) { }    // OK 1
                    when {
                        SDK_INT != 24 || requires23() -> { }  // OK 2
                    }
                }
                @RequiresApi(23) fun requires23() { }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
            src/test/pkg/test.kt:8: Error: Call requires API level 23 (current min is 22): requires23 [NewApi]
                if (SDK_INT != 22 || requires23()) { }    // ERROR 1
                                     ~~~~~~~~~~
            src/test/pkg/test.kt:10: Error: Call requires API level 23 (current min is 22): requires23 [NewApi]
                    SDK_INT != 22 || requires23() -> { }  // ERROR 2
                                     ~~~~~~~~~~
            2 errors, 0 warnings
            """
      )
  }

  fun test143324759() {
    // Regression test for issue 143324759: NewApi false positive on inline kotlin lambda
    lint()
      .files(
        manifest().minSdk(1),
        kotlin(
            """
                package test.pkg

                import android.content.Context
                import android.content.pm.PackageManager

                data class VersionInfo(
                    val code: Long,
                    val name: String,
                    val timestamp: String
                )

                val Context.versionInfo: VersionInfo
                    get() {
                        val metadataBundle = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                            .metaData
                        val timestamp = metadataBundle.getString("buildTimestamp") ?: "Missing timestamp!"
                        return with(packageManager.getPackageInfo(packageName, 0)) {
                            VersionInfo(
                                code = sdk(28) { longVersionCode } ?: versionCode.toLong(),
                                name = versionName,
                                timestamp = timestamp
                            )
                        }
                    }
                """
          )
          .indented(),
        // TODO: This currently passes. I need to port this to bytecode to have it simulate
        // what's happening in a running app.
        // OR maybe allow a form of @RequiresApi where you indicate that one of the
        // params supplies the  level
        kotlin(
            """
                package test.pkg

                import android.os.Build

                inline fun <T> sdk(level: Int, func: () -> T): T? {
                    return if (Build.VERSION.SDK_INT >= level) {
                        func()
                    } else {
                        null
                    }
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectInlinedMessages(false)
  }

  fun testFailedResolve() {
    // Regression test for https://issuetracker.google.com/120255046
    // Make sure method-name based checks work even if we can't resolve the
    // utility method call
    lint()
      .files(
        kotlin(
            """
                @file:Suppress("RemoveRedundantQualifierName", "unused")

                package test.pkg

                import androidx.annotation.RequiresApi
                import foo.bar.common.os.AndroidVersion
                import foo.bar.common.os.AndroidVersion.isAtLeastQ

                fun foo() {
                    if (AndroidVersion.isAtLeastQ()) {
                        bar()
                    }
                    if (com.evo.common.os.AndroidVersion.isAtLeastQ()) {
                        bar()
                    }
                    if (isAtLeastQ()) {
                        bar()
                    }
                }

                @RequiresApi(25)
                fun bar() {
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .allowCompilationErrors() // Deliberate resolve errors
      .run()
      .expectClean()
  }

  fun testChecksSdkIntAtLeast() {
    // Regression test for
    //  -- https://issuetracker.google.com/120255046
    //  -- https://issuetracker.google.com/239767506
    // The @ChecksSdkIntAtLeast annotation allows annotating methods and
    // fields as version check methods without relying on (a) accessing
    // the method body to see if it's an SDK_INT check, which doesn't work
    // for compiled libraries, and (b) name patterns, which doesn't
    // work for unusually named version methods.
    lint()
      .files(
        kotlin(
            "src/main/java/test/pkg/test.kt",
            """
                package test.pkg

                import androidx.annotation.RequiresApi

                enum class Feature(val apiVersion: Int) {
                    Old(9), New(10)
                }

                fun test() {
                    if(isFeatureAvailable(Feature.New.apiVersion)) {
                        bar() // OK 1
                    }
                    if (versionCheck1) {
                        bar() // OK 2
                    }
                    if (Constants.getVersionCheck2()) {
                        bar() // OK 3
                    }
                    if (Constants.SUPPORTS_LETTER_SPACING) {
                        bar() // OK 4
                    }
                    sdk(28) { bar() } ?: fallback() // OK 5
                    if (Constants.getVersionCheck3("", false, 21)) {
                        bar() // OK 6A
                    }
                    when {
                        Constants.getVersionCheck3("", false, 21) -> {
                            bar() // OK 6B
                        }
                    }
                    "test".applyForOreoOrAbove { bar() } // OK 7
                    fromApi(10) { bar() } // OK 8
                    bar() // ERROR
                    sdk(28, { bar() }) ?: fallback() // OK 9
                    sdk(level = 28, func = { bar() }) ?: fallback() // OK 10
                    sdk( func = { bar() }, level = 28) ?: fallback() // OK 11
                }

                @RequiresApi(10)
                fun bar() {
                }

                fun fallback() {
                }
                """,
          )
          .indented(),
        kotlin(
          "src/main/java/test/pkg/utils.kt",
          """
                @file:Suppress("RemoveRedundantQualifierName", "unused")

                package test.pkg
                import android.os.Build
                import androidx.annotation.ChecksSdkIntAtLeast

                @ChecksSdkIntAtLeast(parameter = 0)
                fun isFeatureAvailable(api: Int): Boolean {
                    return Build.VERSION.SDK_INT >= api
                }

                @ChecksSdkIntAtLeast(parameter = 0, lambda = 1)
                inline fun fromApi(value: Int, action: () -> Unit) {
                }

                @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O, lambda = 0)
                inline fun <T> T.applyForOreoOrAbove(block: T.() -> Unit): T {
                    return this
                }

                @ChecksSdkIntAtLeast(parameter = 0, lambda = 1)
                inline fun <T> sdk(level: Int, func: () -> T): T? {
                    return null
                }

                @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.HONEYCOMB)
                val versionCheck1: Boolean
                    get() = false
                """,
        ),
        binaryStub(
          "libs/library.jar",
          stubSources =
            listOf(
              java(
                """
                        package test.pkg;

                        import android.os.Build;

                        import androidx.annotation.ChecksSdkIntAtLeast;

                        public class Constants {
                            @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.LOLLIPOP)
                            public static boolean getVersionCheck2() {
                                return false;
                            }

                            @ChecksSdkIntAtLeast(parameter = 2)
                            public static boolean getVersionCheck3(String sample, boolean sample2, int apiLevel) {
                                return false;
                            }

                            @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.LOLLIPOP)
                            public static final boolean SUPPORTS_LETTER_SPACING = Boolean.getBoolean("foo");
                        }
                        """
              )
            ),
          compileOnly = listOf(SUPPORT_ANNOTATIONS_JAR),
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
                src/main/java/test/pkg/test.kt:33: Error: Call requires API level 10 (current min is 1): bar [NewApi]
                    bar() // ERROR
                    ~~~
                1 errors, 0 warnings
                """
      )
  }

  // TODO: Test out of order parameters!
  fun testChecksSdkIntAtLeastLambda() {
    // Regression test for https://issuetracker.google.com/120255046
    // The @ChecksSdkIntAtLeast annotation allows annotating methods and
    // fields as version check methods without relying on (a) accessing
    // the method body to see if it's an SDK_INT check, which doesn't work
    // for compiled libraries, and (b) name patterns, which doesn't
    // work for unusually named version methods.
    lint()
      .files(
        java(
            """
                package test.pkg;
                import androidx.annotation.RequiresApi;
                class Scratch {
                    @RequiresApi(24)
                    public static void requiresApiN() {
                    }

                    public static void main(String[] args) {
                        Constants.runOnNougat(new Runnable() {
                            @Override
                            public void run() {
                                requiresApiN(); // OK 1
                            }
                        });
                        Constants.runOnNougat2(new Runnable() {
                            @Override
                            public void run() {
                                requiresApiN(); // OK 2
                            }
                        });
                        Constants.runOnNougat(() -> requiresApiN()); // OK 3
                        Constants.runOnNougat2(() -> requiresApiN()); // OK 4
                    }
                }
                """
          )
          .indented(),
        java(
            """
                package test.pkg;

                import android.os.Build;

                import static android.os.Build.VERSION.SDK_INT;
                import static android.os.Build.VERSION_CODES.N;
                import androidx.annotation.ChecksSdkIntAtLeast;

                public class Constants {
                    @ChecksSdkIntAtLeast(api=Build.VERSION_CODES.N, lambda=0)
                    public static void runOnNougat(Runnable runnable) {
                    }

                    public static void runOnNougat2(Runnable runnable) {
                        if (SDK_INT >= N) {
                            runnable.run();
                        }
                    }
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      // TODO: Add in other positions, maybe even out of order, to make sure we handle it right
      .expectClean()
  }

  fun testChecksSdkIntAtLeastBytecode() {
    // Similar to testChecksSdkIntAtLeast, but with precompiled bytecode
    // for the source files in that test insted of sources, since PSI
    // treats annotations from bytecode and source files quite differently,
    // and we want to have a unit test for the main intended purpose
    // of this functionality: identified compiled version check methods
    // in libraries as version check methods, since here looking inside
    // the method bodies won't work at all.
    // Regression test for https://issuetracker.google.com/120255046
    lint()
      .files(
        kotlin(
            "src/main/java/test/pkg/test.kt",
            """
                package test.pkg

                import test.pkg.constants.Constants;
                import test.pkg.utils.*;
                import androidx.annotation.RequiresApi

                fun test() {
                    if (versionCheck1) {
                        bar() // OK 1
                    }
                    if (Constants.getVersionCheck2()) {
                        bar() // OK 2
                    }
                    if (Constants.SUPPORTS_LETTER_SPACING) {
                        bar() // OK 3
                    }
                    sdk(28) { bar() } ?: fallback() // OK 4
                    if (Constants.getVersionCheck3("", false, 21)) {
                        bar(); // OK 5
                    }
                    "test".applyForOreoOrAbove { bar() } // OK 6
                    fromApi(10) { bar() } // OK 7
                    bar() // ERROR
                }

                @RequiresApi(10)
                fun bar() {
                }

                fun fallback() {
                }
                """,
          )
          .indented(),
        bytecode(
          "libs/lib1.jar",
          kotlin(
            "src/test/pkg/utils/utils.kt",
            """
                    @file:Suppress("RemoveRedundantQualifierName", "unused")

                    package test.pkg.utils
                    import android.os.Build
                    import androidx.annotation.ChecksSdkIntAtLeast

                    @ChecksSdkIntAtLeast(parameter = 0, lambda = 1)
                    inline fun fromApi(value: Int, action: () -> Unit) {
                    }

                    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O, lambda = 0)
                    inline fun <T> T.applyForOreoOrAbove(block: T.() -> Unit): T {
                        return this
                    }

                    @ChecksSdkIntAtLeast(parameter = 0, lambda = 1)
                    inline fun <T> sdk(level: Int, func: () -> T): T? {
                        return null
                    }


                    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.HONEYCOMB)
                    val versionCheck1: Boolean
                        get() = false
                    """,
          ),
          0xdeff4caa,
          "test/pkg/utils/UtilsKt.class:" +
            "H4sIAAAAAAAAAI1UzVcaVxT/vQFhGPwY0fiBrVpDEtDqIEnaNBhbYzVySrWt" +
            "xEVdPWAkI8MMZ97ASXaebvo3dNt1F+0up4sej931P+k/0dP7RjCgNJbFe/fd" +
            "+7v3/u4H89c/v/8BIIcCw5RvCt9o1mtGy7dsYbyU51d+FIxBP+VtbtjcqRkH" +
            "5VOzQtoQQ/TEcxtbTYshlS4U665vW45x2m4YJy2n4luuI4zdjpTNZ44YHt4K" +
            "2+jaXzqWn98MvDJF7lQ916q+NrjjuD6XSGP7lVmpi8NqveD4W37R5MLPM8Sa" +
            "3OMN0ze9ENUFhojNG+Uqly+q427R9WrGqemXPW5R3nfxhLHv+vst26YgER6Q" +
            "UTHKMN/D13IorsNtg1J65G5VRBQ6w52K5NLx/6ZLgOFBuni9b/kezaEMUqMS" +
            "h5HAhIZxTDIMtbndMhkYTWTxtp4yDKes1Enqag4TvNm03+y63oFnugfeVtlt" +
            "U6zdQTzeG3o9n7npwvDtRunpTf1mulS6Ld7Gag+mO1tSMYR405LTSVLtZdut" +
            "1FUsMMym/FeWSA0sJzGI2i29WifITNCrgSFDolpnePw/tnhQ7uf/0ZZbl32F" +
            "OtDtQ+p9q0l7xcu2SbCwjKMiQ92yzbZpM6hBWUEBes30j0xPkFPw91inytKZ" +
            "72V74wzjXTpfmz6vcp9TOKXRltYwSeVAor81q0tBIVWVAvx9fpbWzs80RY9r" +
            "ihqiW7l8qsE1o5A2ennPSEtySQ8nlexQLqJH6I7mhnU1qSbCCdJlY3sXP6p/" +
            "vmUEM3QtGZ5he/GlsHp+pscJP9zBj1zi9+I9+IufI4o+mnykjyWVK59lEnPT" +
            "up6cuMR3soxLsJro951YGtcprZJlT16QIpIMq4oeuvhBiWpD6sVPuSyTBecY" +
            "Rtr9HYTcuE7nev96rDTAIPdsvqvcee2bjgzVtZbeNOUM1eADu1b3aZzbbpX2" +
            "b6xoOeZ+q1E2vZIctIzsVrh9xD1LvjvK2KFVc7jf8kie+67l+FbDLDhtS1hk" +
            "3nq3MLRN161Xn6U+mHbotryKuWvJ6LMdn6Mb8bAOBWH5QaVzFkOI0L1Frz3S" +
            "y2XREtqzlcTYW9z5Ta4OntMZocbFoGGb5MVLFKYwHUTRMIoZsocQpXjT5PFl" +
            "4BfFDt06WVR6xQoEjBcwjF3SxpTggz5C5yySmOtQKHQoxAMKi0Rh+ddrHEZ7" +
            "OMTxEZYCDnEs4G7AQUWqh8MHfRw+LGBectBucriH+wM5LBMHdp3DRB+HlU4f" +
            "4sh0+qDi4x4ODwIOkyT19yFN2hs8VrFGIMkjSTr5U0K/BPZueonaCdLJopgs" +
            "yiCXF0GuL2iKQJPUWZrs+jFClKmAnMz3sPvSCniExwV8gk+PwQSe4LNjTAo8" +
            "FcgLrAqsCQwF8lRwjgpsCNwTeCawIpARuC+QFFgQ2BT4XGDuXy4VqTB7CAAA",
          "META-INF/main.kotlin_module:" +
            "H4sIAAAAAAAAAGNgYGBmYGBgBGIWKM3AJcTFUZJaXKJXkJ0uxBYCZHmXcEly" +
            "8cHE9EpLMnOKhdhDQZR3iRKDFgMA7UDMuUcAAAA=",
        ),
        bytecode(
          "libs/lib1.jar",
          kotlin(
            "src/test/pkg/utils/utils2.kt",
            """
                    @file:Suppress("RemoveRedundantQualifierName", "unused")

                    package test.pkg.utils
                    import android.os.Build
                    import androidx.annotation.ChecksSdkIntAtLeast

                    fun mytest() {
                        println("mytest")
                    }

                    """,
          ),
          0xe95da7a2,
          """
                test/pkg/utils/Utils2Kt.class:
                H4sIAAAAAAAAAG1QyU4CQRB9PQMMjCi44IL7loAHR4g3vBgTEyIuEeXCqZGO
                aZnpMUyP0Ru/pAcPHgxnP8pYPTeNfajlvVfVVfX1/fEJ4BC7DAtaRNp7HNx7
                sZZ+5N0aWz/TDhhD8YE/cc/n6t677D2IO0JthkzwYooY7Eq1k0X6l679EmkR
                OMgSHcYkKrUSUobe1VAq3dZDwYNGHi4mcsghzzD7j8DBFIPzaABfUZNK6+8o
                jWonjyKmXRQwwzDdGoTal8o7F5r3ueYNBit4smlPZgxNyQYmsAh/liY6oKhf
                o//Ho4w7HrlW0SpniuNR2TpghqozqkUuuUt9f0C7pE7CvmAotKQSF3HQE8Mb
                3vMJcdthPLwTp9IkS9ex0jIQHRlJYo+VCjXXMlQRarCQgnkkQxoZ8puUVQm3
                yE/OOGf20Rsm994x+5rItsi6CUnHSqJtswU2sEO+Roo5alTqwm5ivokFslhs
                UvdyE8tY6YJFWMVaF1aEdIT1HzzAfZH9AQAA
                """,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAAAGNgYGBmYGBgBGJWKM3AJcTFUZJaXKJXkJ0uxBYCZHmXcClz
                8cHE9EpLMnOKhThCQZSRd4kQO5jlXaLEoMUAAMCpI5xRAAAA
                """,
        ),
        bytecode(
          "libs/lib2.jar",
          java(
            """
                    package test.pkg.constants;

                    import android.os.Build;

                    import androidx.annotation.ChecksSdkIntAtLeast;

                    public class Constants {
                        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.LOLLIPOP)
                        public static boolean getVersionCheck2() {
                            return false;
                        }

                        @ChecksSdkIntAtLeast(parameter = 2)
                        public static boolean getVersionCheck3(String sample, boolean sample2, int apiLevel) {
                            return false;
                        }

                        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.LOLLIPOP)
                        public static final boolean SUPPORTS_LETTER_SPACING = Boolean.getBoolean("foo");
                    }
                    """
          ),
          0xb5722a1d,
          "test/pkg/constants/Constants.class:" +
            "H4sIAAAAAAAAAG1RTU8bMRB9zoYkm6QQKCnlm5QeSA9dqT30AEIKEa1WWkGU" +
            "DTnkgpyNG0wSO9p1UH9UL4hDEYf+AH4UYnYFoQUOnrGf5817Y9/e3fwF8A0f" +
            "88hgJYfVPNawbmMGG1lsZrHFsOSfNBrHzZZ/6h22WofNU79Rq7tHPxhYh2G1" +
            "OVFGjoSrLmQku0NRU0obbqRWEUPV46oXatn75fAp7tTPRDCI/N7AVaZmPMEj" +
            "s8tg8bG0yEyZIbMnlTT7hO1U2wzpuu4JhjlPKnE0GXVF2OKkxFDqC9MWYURN" +
            "k55fEkbn5cVXGmPHO+cX3Bly1Xd8E0rV3+24cbE95iEfCSPCWD7FkNsLhg8G" +
            "8r6ehIH4LmO52ToNZbgy0ee4VRFvMEuKP7XOolLEB2wXkUWOYc2IyDjjQd8J" +
            "HhnOlEvunowcd89FYBjmn6ADrYeCK9KmIaaH8iv2qx1U6Kcy5JphOZambNOp" +
            "QDlPq+CiiDRtyCjFObpap8woz3z6A3aZMEsUMwmYI/Y8FpBKSlcSjF7E+v2s" +
            "rvBSxcZbLP5DtF4llv4jll28I82lqbnKg6K9kLqGdYX0c4OLFN/HnbF8D6c8" +
            "O3S7AgAA",
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
                src/main/java/test/pkg/test.kt:23: Error: Call requires API level 10 (current min is 1): bar [NewApi]
                    bar() // ERROR
                    ~~~
                1 errors, 0 warnings
                """
      )
  }

  fun testChecksSdkIntAtLeastWithFullVersions() {
    // Uses of @ChecksSdkIntAtLeast with full versions
    lint()
      .files(
        kotlin(
            "src/main/java/test/pkg/test.kt",
            """
            package test.pkg

            import androidx.annotation.RequiresApi

            fun test() {
                if (Constants.getVersionCheck()) {
                    bar() // OK 1
                }
                if (Constants.SUPPORTS_LETTER_SPACING) {
                    bar() // OK 2
                }
                bar() // ERROR
            }

            @RequiresApi(10)
            fun bar() {
            }
            """,
          )
          .indented(),
        binaryStub(
          "libs/library.jar",
          stubSources =
            listOf(
              java(
                """
                package test.pkg;

                import android.os.Build;
                import androidx.annotation.ChecksSdkIntAtLeast;

                public class Constants {
                    @ChecksSdkIntAtLeast(api = 8000002) // Build.VERSION_CODES_FULL.VANILLA_ICE_CREAM_2
                    public static boolean getVersionCheck() {
                        return false;
                    }

                    @ChecksSdkIntAtLeast(api = 8000002) // Build.VERSION_CODES_FULL.VANILLA_ICE_CREAM_2
                    public static final boolean SUPPORTS_LETTER_SPACING = Boolean.getBoolean("foo");
                }
                """
              )
            ),
          compileOnly = listOf(newAndroidOsBuildStub, SUPPORT_ANNOTATIONS_JAR),
        ),
        newAndroidOsBuildStub,
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
        src/main/java/test/pkg/test.kt:12: Error: Call requires API level 10 (current min is 1): bar [NewApi]
            bar() // ERROR
            ~~~
        1 errors, 0 warnings
        """
      )
  }

  fun testPolyadic() {
    lint()
      .files(
        manifest().minSdk(14),
        java(
            """
                package test.pkg;

                import androidx.annotation.RequiresApi;
                import android.os.Build;
                import android.os.Build.VERSION_CODES;
                import static android.os.Build.VERSION.SDK_INT;

                @SuppressWarnings("unused")
                public class PolyadicTest {
                    @RequiresApi(Build.VERSION_CODES.M)
                    public boolean methodM() {
                        return true;
                    }

                    private void testPolyadicAnd(boolean f1, boolean f2, boolean f3) {
                        boolean field1 = f1;
                        boolean field2 = f2;
                        boolean field3 = f3;

                        if (field1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        } else {
                            methodM(); // ERROR 1
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            methodM(); // OK 1
                        }
                        if (field1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            methodM(); // OK 2
                        }
                        if (field1 && field2 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && field3) {
                            methodM(); // OK 3
                        }
                        if (Build.VERSION.SDK_INT > 15 && Build.VERSION.SDK_INT > 17 && Build.VERSION.SDK_INT > 19) {
                            methodM(); // ERROR 2
                        }
                    }

                    private void testPolyadicOr() {
                        if (Build.VERSION.SDK_INT < 27 || Build.VERSION.SDK_INT < 28 || Build.VERSION.SDK_INT < 30) {
                        } else {
                            methodM(); // OK 4
                        }
                        Build.VERSION.SDK_INT < 15 || Build.VERSION.SDK_INT < 17 || methodM() || Build.VERSION.SDK_INT < 19; // ERROR 3
                        if (Build.VERSION.SDK_INT < 15 || Build.VERSION.SDK_INT < 17 || Build.VERSION.SDK_INT < 19) {
                        } else {
                            methodM(); // ERROR 4
                        }
                    }
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
                src/test/pkg/PolyadicTest.java:22: Error: Call requires API level 23 (current min is 14): methodM [NewApi]
                            methodM(); // ERROR 1
                            ~~~~~~~
                src/test/pkg/PolyadicTest.java:34: Error: Call requires API level 23 (current min is 20): methodM [NewApi]
                            methodM(); // ERROR 2
                            ~~~~~~~
                src/test/pkg/PolyadicTest.java:43: Error: Call requires API level 23 (current min is 17): methodM [NewApi]
                        Build.VERSION.SDK_INT < 15 || Build.VERSION.SDK_INT < 17 || methodM() || Build.VERSION.SDK_INT < 19; // ERROR 3
                                                                                    ~~~~~~~
                src/test/pkg/PolyadicTest.java:46: Error: Call requires API level 23 (current min is 19): methodM [NewApi]
                            methodM(); // ERROR 4
                            ~~~~~~~
                4 errors, 0 warnings
                """
      )
  }

  fun testNextPlatformHandling() {
    // Regression test for b/172930073
    // Need to gracefully handle the next version of Android
    lint()
      .files(
        manifest().minSdk(14),
        java(
            """
                package test.pkg;

                import android.os.Build;
                import androidx.annotation.ChecksSdkIntAtLeast;
                import androidx.core.os.BuildCompat;
                import androidx.annotation.RequiresApi;

                public class TestZ {
                    public int test() {
                        if (BuildCompat.isAtLeastZ()) {
                            return ApiZImpl.getChecksums();
                        }
                        if (BuildCompat.isCurrentDev()) {
                            return ApiZImpl.getChecksums();
                        }
                        return 0;
                    }

                    @RequiresApi(Build.VERSION_CODES.Z)
                    private static class ApiZImpl {
                        public static int getChecksums() {
                            return 0;
                        }
                    }
                }
                """
          )
          .indented(),
        java(
            """
                package androidx.core.os;
                import android.os.Build;
                import androidx.annotation.ChecksSdkIntAtLeast;

                public class BuildCompat {
                    @ChecksSdkIntAtLeast(codename = "Z")
                    public static boolean isAtLeastZ() {
                        return Build.VERSION.CODENAME.equals("Z");
                    }
                    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.CUR_DEVELOPMENT)
                    public static boolean isCurrentDev() {
                        return false; // stub only; annotation used for version lookup
                    }
                }
                """
          )
          .indented(),
        java(
            """
                package android.os;

                public class Build {
                    public static class VERSION_CODES {
                        public static final int CUR_DEVELOPMENT = 10000;
                        public static final int S = CUR_DEVELOPMENT;
                    }
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun testNextPlatformHandling2() {
    lint()
      .files(
        manifest().minSdk(14),
        kotlin(
            """
                import android.os.Build
                import androidx.annotation.RequiresApi;
                import androidx.core.os.BuildCompat

                @RequiresApi(Build.VERSION_CODES.Z)
                private fun requiresZFunction() {
                }

                fun testIsAtLeastZ() {
                    if (BuildCompat.isAtLeastZ()) {
                        requiresZFunction();
                    }
                }
                """
          )
          .indented(),
        java(
            """
                package androidx.core.os;
                import android.os.Build;
                import androidx.annotation.ChecksSdkIntAtLeast;

                public class BuildCompat {
                    @ChecksSdkIntAtLeast(codename = "Z")
                    public static boolean isAtLeastZ() {
                        return VERSION.CODENAME.equals("Z");
                    }
                }
                """
          )
          .indented(),
        java(
            """
                package android.os;

                public class Build {
                    public static class VERSION_CODES {
                        public static final int Z = 10000;
                    }
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun testWhen() {
    // Regression test for issue 189459502
    lint()
      .files(
        kotlin(
            """
                import android.os.Build
                import androidx.annotation.RequiresApi

                private val supportsRenderNode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

                private val capture = when {
                    supportsRenderNode -> RenderNodeCapture()
                    else -> "fallback"
                }


                @RequiresApi(29)
                fun RenderNodeCapture() {
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun test189459502() {
    // Regression test for 189459502
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import android.os.Build
                import androidx.annotation.RequiresApi

                val capture = when {
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> "fallback"
                    System.getProperty("foo") != null -> requires30() // OK
                    else -> requires30() // OK
                }

                @RequiresApi(30)
                fun requires30() {}
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun testWhen221488045() {
    // Regression test for https://issuetracker.google.com/221488045
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import android.app.Activity
                import android.os.Build
                import androidx.annotation.RequiresApi

                @RequiresApi(Build.VERSION_CODES.Q)
                class Test(activity: Activity, listener: Listener) {
                    private var callSystemUiHelper: CallSystemUiHelper = when (Build.VERSION.SDK_INT) {
                        Build.VERSION_CODES.R -> CallSystemUiHelperImplR(activity, listener) // OK
                        Build.VERSION_CODES.S -> CallSystemUiHelperImplS(activity, listener) // OK
                        else -> CallSystemUiHelperImplQ(activity, listener) // OK
                    }
                }

                class Listener
                open class CallSystemUiHelper

                @RequiresApi(Build.VERSION_CODES.R)
                class CallSystemUiHelperImplR(activity: Activity, listener: Listener) : CallSystemUiHelper()

                @RequiresApi(Build.VERSION_CODES.S)
                class CallSystemUiHelperImplS(activity: Activity, listener: Listener) : CallSystemUiHelper()

                @RequiresApi(Build.VERSION_CODES.Q)
                class CallSystemUiHelperImplQ(activity: Activity, listener: Listener) : CallSystemUiHelper()
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun testSwitchOnSdkInt() {
    // Additional regression test for https://issuetracker.google.com/221488045
    lint()
      .files(
        manifest().minSdk(8),
        kotlin(
            """
                package test.pkg

                import android.annotation.TargetApi
                import android.os.Build
                import androidx.annotation.RequiresApi

                class Test {
                    @TargetApi(10)
                    fun test() {
                        when (Build.VERSION.SDK_INT) {
                            10, 11, 12 -> {
                                requires9()  // OK 1
                                requires10() // OK 2
                                requires11() // ERROR 1 (could be 10)
                                requires12() // ERROR 2 (could be 10)
                                requires13() // ERROR 3
                            }
                            13, 15 -> { // notice gap (14)
                                requires11() // OK 3
                                requires12() // OK 4
                                requires13() // OK 5
                                requires14() // ERROR 4 (not covered by this case)
                                requires15() // ERROR 5 (could be 13)
                                requires16() // ERROR 6
                            }
                            else -> {
                                requires9()  // OK 6
                                requires12() // OK 7
                                requires13() // OK 8
                                requires14() // OK 9
                                requires16() // ERROR 7
                            }
                        }
                    }
                }

                @RequiresApi(9)  private fun requires9()  { }
                @RequiresApi(10) private fun requires10() { }
                @RequiresApi(11) private fun requires11() { }
                @RequiresApi(12) private fun requires12() { }
                @RequiresApi(13) private fun requires13() { }
                @RequiresApi(14) private fun requires14() { }
                @RequiresApi(15) private fun requires15() { }
                @RequiresApi(16) private fun requires16() { }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .issues(ApiDetector.UNSUPPORTED)
      .run()
      .expect(
        """
            src/test/pkg/Test.kt:14: Error: Call requires API level 11 (current min is 10): requires11 [NewApi]
                            requires11() // ERROR 1 (could be 10)
                            ~~~~~~~~~~
            src/test/pkg/Test.kt:15: Error: Call requires API level 12 (current min is 10): requires12 [NewApi]
                            requires12() // ERROR 2 (could be 10)
                            ~~~~~~~~~~
            src/test/pkg/Test.kt:16: Error: Call requires API level 13 (current min is 10): requires13 [NewApi]
                            requires13() // ERROR 3
                            ~~~~~~~~~~
            src/test/pkg/Test.kt:22: Error: Call requires API level 14 (current min is 13): requires14 [NewApi]
                            requires14() // ERROR 4 (not covered by this case)
                            ~~~~~~~~~~
            src/test/pkg/Test.kt:23: Error: Call requires API level 15 (current min is 13): requires15 [NewApi]
                            requires15() // ERROR 5 (could be 13)
                            ~~~~~~~~~~
            src/test/pkg/Test.kt:24: Error: Call requires API level 16 (current min is 13): requires16 [NewApi]
                            requires16() // ERROR 6
                            ~~~~~~~~~~
            src/test/pkg/Test.kt:31: Error: Call requires API level 16 (current min is 14): requires16 [NewApi]
                            requires16() // ERROR 7
                            ~~~~~~~~~~
            7 errors, 0 warnings
            """
      )
  }

  fun testCurDevelopment() {
    lint()
      .files(
        kotlin(
          """
                import android.os.Build.VERSION.SDK_INT

                fun test() {
                    if (SDK_INT >= 10000) requires10000()          // OK 1
                    if (SDK_INT == 10000) requires10000()          // OK 2
                    if (SDK_INT < 10000) { } else requires10000()  // OK 3
                }
                @RequiresApi(10000) fun requires10000() { }
                """
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun testExtensionSdkCheck() {
    // TODO(b/331978236): Java UAST drops Kotlin annotations on methods
    if (!useFirUast()) {
      return
    }
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.os.Build;
                import android.os.ext.SdkExtensions;

                import androidx.annotation.RequiresApi;
                import androidx.annotation.RequiresExtension;

                @RequiresApi(api = Build.VERSION_CODES.R) // for SdkExtensions.getExtensionVersion
                public class SdkExtensionsTest {
                    public void test() {
                        requiresExtRv4(); // ERROR 1
                        if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 3) {
                            requiresExtRv4(); // ERROR 2
                        }
                        if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 4) {
                            requiresExtRv4(); // OK 1
                        }

                        int version = SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R);
                        if (version > 4) {
                            requiresExtRv4(); // OK 2
                        }
                    }

                    public void testEarlyReturn() {
                        if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) < 4) {
                          return;
                        }
                        requiresExtRv4(); // OK 3
                    }

                    public void testEarlyReturnWrongSdk() {
                        if (SdkExtensions.getExtensionVersion(5) < 4) {
                          return;
                        }
                        requiresExtRv4(); // ERROR 3 (wrong id check)
                    }

                    @RequiresExtension(extension=Build.VERSION_CODES.R, version=4)
                    public static void requiresExtRv4() {
                    }

                    // Test combinations of SDKs
                    @RequiresExtension(extension=Build.VERSION_CODES.R, version=4)
                    @RequiresApi(api = Build.VERSION_CODES.S)
                    public static void requiresExtRv4OrS() {
                        requiresExtRv4(); // OK (because we treat multiple annotations as *all* required/available)
                        requiresExtRv4OrS; // OK 6
                    }

                    // TODO: Test repeatable annotations
                }
                """
          )
          .indented(),
        kotlin(
            """
                package test.pkg
                import android.os.Build
                import android.os.ext.SdkExtensions
                import test.pkg.SdkExtensionsTest.requiresExtRv4
                import test.pkg.SdkExtensionsTest.requiresExtRv4OrS

                fun test(x: Int) {
                    requiresExtRv4() // ERROR 4
                    requiresExtRv4OrS() // ERROR 5
                    when {
                        Build.VERSION.SDK_INT == 15 -> { return }
                        Build.VERSION.SDK_INT < 30 -> { return }
                        SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) <= 4 -> { return }
                        x > 50 -> requiresExtRv4() // OK 7
                        SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 4 -> {
                            requiresExtRv4() // OK 8
                        }
                        else -> return
                    }
                    requiresExtRv4() // OK 9
                }
                """
          )
          .indented(),
        requiresExtensionStub,
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
            src/test/pkg/SdkExtensionsTest.java:12: Error: Call requires version 4 of the R Extensions SDK (current min is 0): requiresExtRv4 [NewApi]
                    requiresExtRv4(); // ERROR 1
                    ~~~~~~~~~~~~~~
            src/test/pkg/SdkExtensionsTest.java:14: Error: Call requires version 4 of the R Extensions SDK (current min is 3): requiresExtRv4 [NewApi]
                        requiresExtRv4(); // ERROR 2
                        ~~~~~~~~~~~~~~
            src/test/pkg/SdkExtensionsTest.java:37: Error: Call requires version 4 of the R Extensions SDK (current min is 0): requiresExtRv4 [NewApi]
                    requiresExtRv4(); // ERROR 3 (wrong id check)
                    ~~~~~~~~~~~~~~
            src/test/pkg/test.kt:8: Error: Call requires version 4 of the R Extensions SDK (current min is 0): requiresExtRv4 [NewApi]
                requiresExtRv4() // ERROR 4
                ~~~~~~~~~~~~~~
            src/test/pkg/test.kt:9: Error: Call requires version 4 of the R Extensions SDK (current min is 0): requiresExtRv4OrS [NewApi]
                requiresExtRv4OrS() // ERROR 5
                ~~~~~~~~~~~~~~~~~
            5 errors, 0 warnings
            """
      )
  }

  fun testMissingAdServices() {
    lint()
      .files(
        kotlin(
            """
            package test.pkg

            import android.os.ext.SdkExtensions
            import android.os.Build.VERSION_CODES.R
            import androidx.annotation.RequiresApi
            import androidx.annotation.RequiresExtension

            @RequiresExtension(extension = R, 4)
            @RequiresApi(34)
            fun test() {
                rAndRb() // ERROR 1
            }

            @RequiresExtension(extension = R, 4)
            @RequiresExtension(extension = SdkExtensions.AD_SERVICES, 4)
            fun rAndRb() {
            }
            """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
        requiresExtensionStub,
      )
      .run()
      .expect(
        """
        src/test/pkg/test.kt:11: Error: Call requires version 4 of the Ad Services Extensions SDK (current min is 0): rAndRb [NewApi]
            rAndRb() // ERROR 1
            ~~~~~~
        1 errors, 0 warnings
        """
      )
  }

  fun testAndOrWithIfs() {
    if (ApiDetector.REPEATED_API_ANNOTATION_REQUIRES_ALL) {
      // Like ApiDetector#testExtensionAndOr(), but we've replaced the
      // annotations with surrounding if-else checks of SDKs.

      // Using a special version of the lookup database here in order to be able
      // to test OR semantics on APIs, which we cannot express with annotations,
      // only in the database (for now).
      ApiLookupTest.runApiCheckWithCustomLookup {
          lint()
            .files(
              kotlin(
                  """
                package test.pkg

                import android.os.Build
                import android.os.Build.VERSION.SDK_INT
                import android.os.Build.VERSION_CODES.R
                import android.os.Build.VERSION_CODES.S
                import android.os.ext.SdkExtensions
                import android.provider.MediaStore

                import androidx.annotation.RequiresApi
                import androidx.annotation.RequiresExtension

                class Test {
                    @RequiresApi(R)
                    fun testViaIfs() {
                        /* Instead of
                        @RequiresExtension.AnyOf(
                            RequiresExtension(extension = R, 4),
                            RequiresApi(34)
                        )
                        */
                        MediaStore.getPickImagesMaxLimit() // ERROR 1
                        if (SDK_INT >= 34 || SdkExtensions.getExtensionVersion(R) >= 2) {
                             // This method requires any of 0:33,30:2,31:2,33:2
                            MediaStore.getPickImagesMaxLimit() // OK 1
                            rOnly()  // ERROR 1: We may not have R, we may only have U
                            uOnly()  // ERROR 2: We may not have U, we may only have R
                        }
                        if (SdkExtensions.getExtensionVersion(R) >= 2 || SdkExtensions.getExtensionVersion(S) >= 2) {
                             // This method requires any of 0:33,30:2,31:2,33:2
                            MediaStore.getPickImagesMaxLimit() // OK 2
                        }

                        // Like the above, but with an extra SDK_INT >= R added in to avoid
                        // warnings on SdkExtensions; make sure we combine multiple
                        // SDK version checks together properly
                        if (SDK_INT >= 34 || SDK_INT >= R && SdkExtensions.getExtensionVersion(R) >= 4) {
                            MediaStore.getPickImagesMaxLimit() // OK 3
                            rOnly()  // ERROR 4: We may not have R, we may only have U
                        }
                        if (SDK_INT >= 34 && SDK_INT >= R && SdkExtensions.getExtensionVersion(R) >= 4) {
                            MediaStore.getPickImagesMaxLimit() // OK 3
                            rOnly()  // OK 4
                            uOnly()  // OK 5
                        }
                    }

                    @RequiresExtension(R, 4)
                    fun rOnly() {
                    }

                    @RequiresApi(34)
                    fun uOnly() {
                    }
                }
                """
                )
                .indented(),
              requiresExtensionStub,
              SUPPORT_ANNOTATIONS_JAR,
            )
        }
        .expect(
          """
          src/test/pkg/Test.kt:22: Error: Call requires API level 33 (current min is 30): android.provider.MediaStore#getPickImagesMaxLimit [NewApi]
                  MediaStore.getPickImagesMaxLimit() // ERROR 1
                             ~~~~~~~~~~~~~~~~~~~~~
          src/test/pkg/Test.kt:26: Error: Call requires version 4 of the R Extensions SDK (current min is 0): rOnly [NewApi]
                      rOnly()  // ERROR 1: We may not have R, we may only have U
                      ~~~~~
          src/test/pkg/Test.kt:27: Error: Call requires API level 34 (current min is 30): uOnly [NewApi]
                      uOnly()  // ERROR 2: We may not have U, we may only have R
                      ~~~~~
          src/test/pkg/Test.kt:37: Warning: Unnecessary; SDK_INT >= R is never true here (SDK_INT ≥ 30 and < 34) [ObsoleteSdkInt]
                  if (SDK_INT >= 34 || SDK_INT >= R && SdkExtensions.getExtensionVersion(R) >= 4) {
                                       ~~~~~~~~~~~~
          src/test/pkg/Test.kt:41: Warning: Unnecessary; SDK_INT is always >= 34 [ObsoleteSdkInt]
                  if (SDK_INT >= 34 && SDK_INT >= R && SdkExtensions.getExtensionVersion(R) >= 4) {
                                       ~~~~~~~~~~~~
          3 errors, 2 warnings
          """
        )
    } else {
      // Like ApiDetector#testExtensionAndOr(), but we've replaced the annotations with surrounding
      // if-else checks of SDKs.
      lint()
        .files(
          kotlin(
              """
              package test.pkg

              import android.os.Build.VERSION.SDK_INT
              import android.os.Build.VERSION_CODES.R
              import android.os.ext.SdkExtensions
              import androidx.annotation.RequiresApi
              import androidx.annotation.RequiresExtension

              class Test {
                  @RequiresApi(R)
                  fun testViaIfs() {
                      /* Instead of
                      @RequiresExtension.AnyOf(
                          RequiresExtension(extension = R, 4),
                          RequiresApi(34)
                      )
                      */
                      if (SDK_INT >= 34 || SdkExtensions.getExtensionVersion(R) >= 4) {
                          either() // OK 1
                          rOnly()  // ERROR 1: We may not have R, we may only have U
                          uOnly()  // ERROR 2: We may not have U, we may only have R
                          other()  // ERROR 3: We may not have R or RB
                      }
                      // Like the above, but with an extra SDK_INT >= R added in to avoid
                      // warnings on SdkExtensions; make sure we combine multiple
                      // SDK version checks together properly
                      if (SDK_INT >= 34 || SDK_INT >= R && SdkExtensions.getExtensionVersion(R) >= 4) {
                          either() // OK 2
                          rOnly()  // ERROR 4: We may not have R, we may only have U
                          uOnly()  // ERROR 5: We may not have U, we may only have R
                          other()  // ERROR 6: We may not have R or RB
                      }
                      if (SDK_INT >= 34 && SDK_INT >= R && SdkExtensions.getExtensionVersion(R) >= 4) {
                          either() // OK 3
                          rOnly()  // OK 4
                          uOnly()  // OK 5
                          other()  // OK 6
                      }
                  }

                  @RequiresApi(R)
                  fun test2() {
                      /* Instead of
                      @RequiresExtension.AnyOf(
                          @RequiresApi(34),
                          @RequiresExtension(R, 4),
                          @RequiresExtension(1000000, 4)
                      )
                      */
                      if (SDK_INT >= 34 ||
                          SdkExtensions.getExtensionVersion(R) >= 4 ||
                          SdkExtensions.getExtensionVersion(1000000) >= 4) {
                          other() // ERROR 7
                      }
                  }

                  fun test3() {
                      // Instead of
                      //   @RequiresExtension(1000000, 4)
                      if (SdkExtensions.getExtensionVersion(1000000) >= 4) { // ERROR 8: getExtensionVersion requires 30
                          other() // OK 7
                      }
                  }

                  @RequiresExtension(R, 4)
                  @RequiresApi(34)
                  fun either() {
                  }

                  @RequiresExtension(R, 4)
                  fun rOnly() {
                  }

                  @RequiresApi(34)
                  fun uOnly() {
                  }

                  @RequiresExtension(R, 4)
                  @RequiresExtension(1000000, 4)
                  fun other() {
                  }
              }
              """
            )
            .indented(),
          requiresExtensionStub,
          SUPPORT_ANNOTATIONS_JAR,
        )
        .run()
        .expect(
          """
          src/test/pkg/Test.kt:20: Error: Call requires version 4 of the R Extensions SDK (current min is 0): rOnly [NewApi]
                      rOnly()  // ERROR 1: We may not have R, we may only have U
                      ~~~~~
          src/test/pkg/Test.kt:21: Error: Call requires API level 34 (current min is 30): uOnly [NewApi]
                      uOnly()  // ERROR 2: We may not have U, we may only have R
                      ~~~~~
          src/test/pkg/Test.kt:22: Error: Call requires version 4 of the R Extensions SDK (current min is 0): other [NewApi]
                      other()  // ERROR 3: We may not have R or RB
                      ~~~~~
          src/test/pkg/Test.kt:30: Error: Call requires API level 34 (current min is 30): uOnly [NewApi]
                      uOnly()  // ERROR 5: We may not have U, we may only have R
                      ~~~~~
          src/test/pkg/Test.kt:31: Error: Call requires version 4 of the R Extensions SDK (current min is 4): other [NewApi]
                      other()  // ERROR 6: We may not have R or RB
                      ~~~~~
          src/test/pkg/Test.kt:53: Error: Call requires version 4 of the R Extensions SDK (current min is 0): other [NewApi]
                      other() // ERROR 7
                      ~~~~~
          src/test/pkg/Test.kt:60: Error: Call requires API level 30 (current min is 1): android.os.ext.SdkExtensions#getExtensionVersion [NewApi]
                  if (SdkExtensions.getExtensionVersion(1000000) >= 4) { // ERROR 8: getExtensionVersion requires 30
                                    ~~~~~~~~~~~~~~~~~~~
          src/test/pkg/Test.kt:27: Warning: Unnecessary; SDK_INT >= R is never true here (SDK_INT ≥ 30 and < 34) [ObsoleteSdkInt]
                  if (SDK_INT >= 34 || SDK_INT >= R && SdkExtensions.getExtensionVersion(R) >= 4) {
                                       ~~~~~~~~~~~~
          src/test/pkg/Test.kt:33: Warning: Unnecessary; SDK_INT is always >= 34 [ObsoleteSdkInt]
                  if (SDK_INT >= 34 && SDK_INT >= R && SdkExtensions.getExtensionVersion(R) >= 4) {
                                       ~~~~~~~~~~~~
          7 errors, 2 warnings
          """
        )
    }
  }

  fun testExtensionSuppressInFieldsAndMethods() {
    // in testExtensionWithChecksSdkIntAtLeast we have the same test scenario,
    // but missing the method bodies and field initializations and instead using
    // @ChecksSdkIntAtLeast annotations.
    lint()
      .files(
        manifest().minSdk(1),
        java(
            """
                package test.pkg;

                import android.os.Build;
                import static android.os.Build.VERSION.SDK_INT;
                import static android.os.Build.VERSION_CODES.TIRAMISU;
                import android.os.ext.SdkExtensions;
                import android.annotation.TargetApi;
                import androidx.annotation.RequiresExtension;

                @TargetApi(33) // For SdkExtensions.getExtensionVersion
                public class SdkExtensionsTest {
                    public void test() {
                        requiresExtRv4(); // ERROR 1
                        if (HAS_R_4) {
                            requiresExtRv4(); // OK 1
                        }
                        if (HAS_R_4B) {
                            requiresExtRv4(); // OK 2
                        }
                        if (R_VERSION >= 3) {
                            requiresExtRv4(); // ERROR 2
                        }
                        if (R_VERSION >= 4) {
                            requiresExtRv4(); // OK 3
                        }
                        if (hasR4()) {
                            requiresExtRv4(); // OK 4
                        }
                        if (hasRn(4)) {
                            requiresExtRv4(); // OK 5
                        }
                        if (hasRB(4)) {
                            requiresExtRv4(); // OK 6
                        }
                        runOnR4(() -> requiresExtRv4()); // OK 7

                        // TODO: Support method references too
                        //runOnR4(this::requiresExtRv4); // OK 8

                        runOnR4(new Runnable() {
                            @Override
                            public void run() {
                                requiresExtRv4(); // OK 9
                            }
                        });
                    }

                    @RequiresExtension(extension=Build.VERSION_CODES.R, version=4)
                    public void requiresExtRv4() {
                    }

                    public static final boolean HAS_R_4 = SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 4;
                    public static final boolean HAS_R_4B = SDK_INT >= TIRAMISU && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 4;
                    public static final boolean R_VERSION = SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R);
                    public boolean hasR4() {
                        return SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 4;
                    }
                    public boolean hasRn(int rev) {
                        return SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= rev;
                    }
                    public static boolean hasRB(int rev) {
                        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                                && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= rev;
                    }
                    public static void runOnR4(Runnable runnable) {
                        if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 4) {
                            runnable.run();
                        }
                    }
                }
                """
          )
          .indented(),
        requiresExtensionStub,
        checksSdkIntWithSdkStub,
      )
      .run()
      .expect(
        """
        src/test/pkg/SdkExtensionsTest.java:13: Error: Call requires version 4 of the R Extensions SDK (current min is 0): requiresExtRv4 [NewApi]
                requiresExtRv4(); // ERROR 1
                ~~~~~~~~~~~~~~
        src/test/pkg/SdkExtensionsTest.java:21: Error: Call requires version 4 of the R Extensions SDK (current min is 3): requiresExtRv4 [NewApi]
                    requiresExtRv4(); // ERROR 2
                    ~~~~~~~~~~~~~~
        src/test/pkg/SdkExtensionsTest.java:53: Warning: Unnecessary; SDK_INT is always >= 33 [ObsoleteSdkInt]
            public static final boolean HAS_R_4B = SDK_INT >= TIRAMISU && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 4;
                                                   ~~~~~~~~~~~~~~~~~~~
        src/test/pkg/SdkExtensionsTest.java:62: Warning: Unnecessary; SDK_INT is always >= 33 [ObsoleteSdkInt]
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        2 errors, 2 warnings
        """
      )
  }

  fun testExtensionWithChecksSdkIntAtLeast() {
    // In testExtensionSuppressInFieldsAndMethods we have the same test scenario, but instead
    // of @ChecksSdkIntAtLeast annotations we have the actual checks implemented as source.
    lint()
      .files(
        manifest().minSdk(1),
        java(
            """
                package test.pkg;

                import android.os.Build;
                import static android.os.Build.VERSION.SDK_INT;
                import static android.os.Build.VERSION_CODES.TIRAMISU;
                import android.os.ext.SdkExtensions;
                import static test.pkg.lib.Utils.requiresExtRv4;
                import static test.pkg.lib.Utils.HAS_R_4;
                import static test.pkg.lib.Utils.R_VERSION;
                import static test.pkg.lib.Utils.hasR4;
                import static test.pkg.lib.Utils.hasR;
                import static test.pkg.lib.Utils.runOnR4;

                public class SdkExtensionsTest {
                    public void test() {
                        requiresExtRv4(); // ERROR 1
                        if (HAS_R_4) {
                            requiresExtRv4(); // OK 1
                        }
                        if (R_VERSION >= 3) {
                            requiresExtRv4(); // ERROR 2
                        }
                        if (R_VERSION >= 4) {
                            requiresExtRv4(); // OK 2
                        }
                        if (hasR4()) {
                            requiresExtRv4(); // OK 3
                        }
                        if (hasR(4)) {
                            requiresExtRv4(); // OK 4
                        }
                        runOnR4(() -> requiresExtRv4()); // OK 5

                        // TODO: Support method references too
                        //runOnR4(this::requiresExtRv4); // OK 6

                        runOnR4(new Runnable() {
                            @Override
                            public void run() {
                                requiresExtRv4(); // OK 7
                            }
                        });
                    }
                }
                """
          )
          .indented(),
        // We deliberately put the version utilities in a separate library such that
        // we also test provisional reporting
        java(
            "../lib/src/test/pkg/lib/Utils.java",
            """
                package test.pkg.lib;

                import android.annotation.TargetApi;
                import android.os.Build;
                import androidx.annotation.RequiresExtension;
                import androidx.annotation.ChecksSdkIntAtLeast;

                @TargetApi(33) // For SdkExtensions.getExtensionVersion
                class Utils {
                    @RequiresExtension(extension=Build.VERSION_CODES.R, version=4)
                    public static void requiresExtRv4() {
                    }

                    @ChecksSdkIntAtLeast(api = 4, extension = Build.VERSION_CODES.R)
                    public static boolean HAS_R_4;

                    @ChecksSdkIntAtLeast(extension = Build.VERSION_CODES.R)
                    public static boolean R_VERSION;

                    @ChecksSdkIntAtLeast(api = 4, extension = Build.VERSION_CODES.R)
                    public static boolean hasR4() {
                        return false;
                    }

                    @ChecksSdkIntAtLeast(parameter=0, extension = Build.VERSION_CODES.R)
                    public static boolean hasR(int rev) {
                        return false;
                    }

                    @ChecksSdkIntAtLeast(api=4, lambda=0, extension=Build.VERSION_CODES.R)
                    public static void runOnR4(Runnable runnable) {
                    }
                }
                """,
          )
          .indented(),
        requiresExtensionStub,
        checksSdkIntWithSdkStub,
        requiresExtensionStub.to("../lib/androidx/annotation/RequiresExtension.kt"),
        checksSdkIntWithSdkStub.to("../lib/androidx/annotation/ChecksSdkIntAtLeast.java"),
      )
      .run()
      .expect(
        """
            src/test/pkg/SdkExtensionsTest.java:16: Error: Call requires version 4 of the R Extensions SDK (current min is 0): requiresExtRv4 [NewApi]
                    requiresExtRv4(); // ERROR 1
                    ~~~~~~~~~~~~~~
            src/test/pkg/SdkExtensionsTest.java:21: Error: Call requires version 4 of the R Extensions SDK (current min is 3): requiresExtRv4 [NewApi]
                        requiresExtRv4(); // ERROR 2
                        ~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
      )
  }

  fun testUncertainOr() {
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import android.os.Build.VERSION.SDK_INT
                import androidx.annotation.RequiresApi

                const val CONSTANT_FALSE = false

                fun testOr(b1: Boolean) {
                    if (SDK_INT >= 21 || b1) {
                        // It's possible for SDK_INT to be < 21 here (if b1 is true)
                        requires21() // ERROR 1
                    }

                    if (SDK_INT >= 21 || CONSTANT_FALSE) {
                        requires21() // OK 1
                    }
                }

                @RequiresApi(21)
                fun requires21() {
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
            src/test/pkg/test.kt:11: Error: Call requires API level 21 (current min is 1): requires21 [NewApi]
                    requires21() // ERROR 1
                    ~~~~~~~~~~
            1 errors, 0 warnings
            """
      )
  }

  fun testUncertainSdkIntCheck() {
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import android.os.Build.VERSION.SDK_INT
                import androidx.annotation.RequiresApi

                const val CONSTANT_TRUE = true
                const val CONSTANT_FALSE = false

                @Suppress("ControlFlowWithEmptyBody", "unused")
                fun testOr(b1: Boolean, b2: Boolean) {
                    if (SDK_INT < 21 && b1) {
                    } else if (SDK_INT < 21 && b2) {
                    } else {
                        // we *don't* know that SDK_INT >= 21 here because it's possible that b2 was false!
                        requires21() // ERROR 1
                    }

                    if (SDK_INT < 21 && b1) {
                    } else if (b2 && SDK_INT < 21) {
                    } else {
                        // we *don't* know that SDK_INT >= 21 here because it's possible that b2 was false!
                        requires21() // ERROR 2
                    }

                    when {
                        SDK_INT < 21 && b1 -> {
                        }
                        b2 && SDK_INT < 21 -> {
                        }
                        else -> {
                            // we *don't* know that SDK_INT >= 21 here because it's possible that b2 was false!
                            requires21() // ERROR 3
                        }
                    }

                    if (SDK_INT < 21 && b1) {
                    } else if (CONSTANT_TRUE && SDK_INT < 21) {
                    } else {
                        requires21() // OK 1
                    }

                    when {
                        SDK_INT < 21 && b1 -> {
                        }
                        CONSTANT_TRUE && SDK_INT < 21 -> {
                        }
                        else -> {
                            requires21() // OK 2
                        }
                    }

                    if (b1 || SDK_INT < 21) {
                    } else if (b2 || SDK_INT < 21) { // Warn: SDK_INT can never be < 21 here
                    } else {
                        requires21() // OK 3
                    }

                    if (SDK_INT < 21 || b1) {
                    } else if (SDK_INT < 21 || b2) { // Warn: SDK_INT can never be < 21 here
                    } else {
                        requires21() // OK 4
                    }

                    when {
                        b1 || SDK_INT < 21 -> {
                        }
                        else -> {
                            // We don't know that SDK_INT >= 21 because it's possible that b1 was true
                            requires21() // OK 5
                        }
                    }

                    if (CONSTANT_FALSE || SDK_INT < 21) {
                    } else {
                        requires21() // OK 6
                    }

                    when {
                        CONSTANT_FALSE || SDK_INT < 21 -> {
                        }
                        else -> {
                            requires21() // OK 7
                        }
                    }
                }

                @RequiresApi(21)
                fun requires21() {
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
        src/test/pkg/test.kt:15: Error: Call requires API level 21 (current min is 1): requires21 [NewApi]
                requires21() // ERROR 1
                ~~~~~~~~~~
        src/test/pkg/test.kt:22: Error: Call requires API level 21 (current min is 1): requires21 [NewApi]
                requires21() // ERROR 2
                ~~~~~~~~~~
        src/test/pkg/test.kt:32: Error: Call requires API level 21 (current min is 1): requires21 [NewApi]
                    requires21() // ERROR 3
                    ~~~~~~~~~~
        src/test/pkg/test.kt:53: Warning: Unnecessary; SDK_INT < 21 is never true here [ObsoleteSdkInt]
            } else if (b2 || SDK_INT < 21) { // Warn: SDK_INT can never be < 21 here
                             ~~~~~~~~~~~~
        src/test/pkg/test.kt:59: Warning: Unnecessary; SDK_INT < 21 is never true here [ObsoleteSdkInt]
            } else if (SDK_INT < 21 || b2) { // Warn: SDK_INT can never be < 21 here
                       ~~~~~~~~~~~~
        3 errors, 2 warnings
        """
      )
  }

  fun testRecursiveUtilityFunction() {
    // Regression test for b/290340814
    lint()
      .files(
        kotlin(
            """
            package test.pkg

            import androidx.annotation.RequiresApi

            fun recursive(i: Int): Boolean {
                return i > 0 && recursive(i - 1)
            }

            fun testSimpleRecursion() {
                if (recursive(5)) {
                    requires30() // ERROR 1
                }
            }

            @RequiresApi(30)
            fun requires30() { }
            """
          )
          .indented(),
        kotlin(
            """
            package test.pkg

            import androidx.annotation.RequiresApi

            fun recursiveA(i: Int): Boolean {
                return recursiveB(i)
            }

            fun recursiveB(i: Int): Boolean {
                return recursiveC(i)
            }

            fun recursiveC(i: Int): Boolean {
                return i > 0 && recursiveA(i - 1)
            }

            fun test() {
                if (recursiveA(5)) {
                    requires30() // ERROR 2
                }
            }
            """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
        src/test/pkg/test.kt:11: Error: Call requires API level 30 (current min is 1): requires30 [NewApi]
                requires30() // ERROR 1
                ~~~~~~~~~~
        src/test/pkg/test2.kt:19: Error: Call requires API level 30 (current min is 1): requires30 [NewApi]
                requires30() // ERROR 2
                ~~~~~~~~~~
        2 errors, 0 warnings
        """
      )
  }

  fun testNestedWithinAnonymousClass() {
    // Regression test for b/350324869
    lint()
      .files(
        kotlin(
            """
            package test.pkg

            import android.os.Build.VERSION.SDK_INT
            import androidx.annotation.RequiresApi

            fun test() {
                if (SDK_INT <= 33) {
                    return
                }
                requires34() // OK 1
                object : Runnable {
                    override fun run() {
                        requires34() // OK 2
                        java.util.zip.CRC32C() // OK 3. (Class also requires 34.)
                    }
                }
            }

            @RequiresApi(34)
            fun requires34() { }
            """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  @Suppress("SimplifyNegatedBinaryExpression")
  fun testInverse() {
    lint()
      .files(
        kotlin(
            """
            package test.pkg

            import android.os.Build.VERSION.SDK_INT
            import androidx.annotation.RequiresApi

            fun testInverseIf() {
                if (SDK_INT >= 24) {
                    requires24() // OK 1: never crashes
                }
                if (!(SDK_INT < 24)) {
                    requires24() // OK 2: never crashes
                }
                if (!(SDK_INT < 24 && System.currentTimeMillis() % 2 == 1L)) {
                    requires24() // ERROR 1: We can crash here
                }
            }

            fun testInverseSwitch(b: Boolean) {
                when {
                    SDK_INT >= 32 -> {} // Here we know SDK_INT >= 32
                    !(SDK_INT < 31) -> {} // Here we know SDK_INT >= 31
                    !(SDK_INT >= 24 && b) -> {} // Here we know SDK_INT < 24 (and b)
                    else -> requires24() // ERROR 2: we don't know that SDK_INT >= 24 here; b could have been false
                }
            }

            @RequiresApi(24)
            private fun requires24() = true
            """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
        src/test/pkg/test.kt:14: Error: Call requires API level 24 (current min is 1): requires24 [NewApi]
                requires24() // ERROR 1: We can crash here
                ~~~~~~~~~~
        src/test/pkg/test.kt:23: Error: Call requires API level 24 (current min is 1): requires24 [NewApi]
                else -> requires24() // ERROR 2: we don't know that SDK_INT >= 24 here; b could have been false
                        ~~~~~~~~~~
        2 errors, 0 warnings
        """
      )
  }

  fun testMinorVersions() {
    lint()
      .files(
        kotlin(
            """
            package test.pkg

            import android.os.Build
            import android.os.Build.VERSION.SDK_INT_FULL
            import android.os.Build.VERSION_CODES
            import android.os.Build.VERSION_CODES_FULL
            import androidx.annotation.RequiresApi

            fun test() {
                requiresApi35()            // ERROR 1
                requiresApi35_2()          // ERROR 2
                if (SDK_INT_FULL >= VERSION_CODES_FULL.VANILLA_ICE_CREAM_0) {
                    requiresApi35()        // OK 1
                    requiresApi35_2()      // ERROR 3
                }
                if (SDK_INT_FULL >= VERSION_CODES_FULL.VANILLA_ICE_CREAM_2) {
                    requiresApi35()        // OK 2
                    requiresApi35_2()      // OK 3
                }
            }

            fun testRangeOperator() {
                if (SDK_INT_FULL in VERSION_CODES_FULL.VANILLA_ICE_CREAM_0 until VERSION_CODES_FULL.VANILLA_ICE_CREAM_2) {
                    requiresApi35()     // OK 4
                    requiresApi35_1()   // ERROR 4
                    requiresApi35_2()   // ERROR 5
                }
                if (SDK_INT_FULL in VERSION_CODES_FULL.VANILLA_ICE_CREAM_1 until VERSION_CODES_FULL.VANILLA_ICE_CREAM_2) {
                    requiresApi35()     // OK 5
                    requiresApi35_1()   // OK 6
                    requiresApi35_2()   // ERROR 6
                }
            }

            @RequiresApi(VERSION_CODES.VANILLA_ICE_CREAM)
            fun requiresApi35() {
            }

            @RequiresApi(VERSION_CODES_FULL.VANILLA_ICE_CREAM_1)
            fun requiresApi35_1() {
            }

            @RequiresApi(VERSION_CODES_FULL.VANILLA_ICE_CREAM_2)
            fun requiresApi35_2() {
            }
            """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
        newAndroidOsBuildStub,
      )
      .run()
      .expect(
        """
        src/test/pkg/test.kt:10: Error: Call requires API level 35 (current min is 1): requiresApi35 [NewApi]
            requiresApi35()            // ERROR 1
            ~~~~~~~~~~~~~
        src/test/pkg/test.kt:11: Error: Call requires API level 35.2 (current min is 1): requiresApi35_2 [NewApi]
            requiresApi35_2()          // ERROR 2
            ~~~~~~~~~~~~~~~
        src/test/pkg/test.kt:14: Error: Call requires API level 35.2 (current min is 35): requiresApi35_2 [NewApi]
                requiresApi35_2()      // ERROR 3
                ~~~~~~~~~~~~~~~
        src/test/pkg/test.kt:25: Error: Call requires API level 35.1 (current min is 35): requiresApi35_1 [NewApi]
                requiresApi35_1()   // ERROR 4
                ~~~~~~~~~~~~~~~
        src/test/pkg/test.kt:26: Error: Call requires API level 35.2 (current min is 35): requiresApi35_2 [NewApi]
                requiresApi35_2()   // ERROR 5
                ~~~~~~~~~~~~~~~
        src/test/pkg/test.kt:31: Error: Call requires API level 35.2 (current min is 35.1): requiresApi35_2 [NewApi]
                requiresApi35_2()   // ERROR 6
                ~~~~~~~~~~~~~~~
        6 errors, 0 warnings
        """
      )
  }

  fun testMinorVersionsOperators() {
    lint()
      .files(
        kotlin(
            """
            package test.pkg

            import android.os.Build
            import android.os.Build.VERSION.SDK_INT
            import android.os.Build.VERSION.SDK_INT_FULL
            import android.os.Build.VERSION_CODES
            import android.os.Build.VERSION_CODES_FULL
            import androidx.annotation.RequiresApi

            fun testOperatorCornerCases() {
                if (SDK_INT_FULL < VERSION_CODE_FULL_351) {
                } else {
                    requiresApi35()    // OK 1
                    requiresApi35_1()  // OK 2
                    requiresApi35_2()  // ERROR 1
                }

                if (SDK_INT <= VERSION_CODES.VANILLA_ICE_CREAM) {
                    // Here, for SDK_INT comparisons, we conclude that
                    // you're comparing whole API levels, so the inverse
                    // will mean VANILLA_ICE_CREAM.next, higher than all the VANILLA_ICE_CREAM
                    // minor versions
                } else {
                    requiresApi35_2()  // OK 3
                }

                // But when comparing full versions, we want to imply
                // minor versions are not implicitly covered by <=
                if (SDK_INT_FULL <= VERSION_CODE_FULL_350) {
                } else {
                    requiresApi35()    // OK 4
                    requiresApi35_1()  // OK 5
                    requiresApi35_2()  // ERROR 2
                }

                if (SDK_INT_FULL > VERSION_CODE_FULL_351) {
                    requiresApi35_1()  // OK 6
                    requiresApi35_2()  // OK 7
                    requiresApi35_3()  // ERROR 3
                }
                if (SDK_INT_FULL > VERSION_CODE_FULL_350) {
                    requiresApi35_1()  // OK 8
                    requiresApi35_2()  // ERROR 4
                }

                if (SDK_INT_FULL == VERSION_CODE_FULL_351) {
                    requiresApi35_1()  // OK 9
                    requiresApi35_2()  // ERROR 5
                }

                if (SDK_INT_FULL != VERSION_CODE_FULL_351) {
                } else {
                    requiresApi35_1()  // OK 10
                }
            }

            val VERSION_CODE_FULL_350 = VERSION_CODES_FULL.VANILLA_ICE_CREAM_0
            val VERSION_CODE_FULL_351 = VERSION_CODES_FULL.VANILLA_ICE_CREAM_1
            val VERSION_CODE_FULL_352 = VERSION_CODES_FULL.VANILLA_ICE_CREAM_2

            @RequiresApi(VERSION_CODES.VANILLA_ICE_CREAM)
            fun requiresApi35() {
            }

            @RequiresApi(VERSION_CODES_FULL.VANILLA_ICE_CREAM_1)
            fun requiresApi35_1() {
            }

            @RequiresApi(VERSION_CODES_FULL.VANILLA_ICE_CREAM_2)
            fun requiresApi35_2() {
            }

            @RequiresApi(VERSION_CODES_FULL.VANILLA_ICE_CREAM_3)
            fun requiresApi35_3() {
            }
            """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
        newAndroidOsBuildStub,
      )
      .run()
      .expect(
        """
        src/test/pkg/test.kt:15: Error: Call requires API level 35.2 (current min is 35.1): requiresApi35_2 [NewApi]
                requiresApi35_2()  // ERROR 1
                ~~~~~~~~~~~~~~~
        src/test/pkg/test.kt:33: Error: Call requires API level 35.2 (current min is 35.1): requiresApi35_2 [NewApi]
                requiresApi35_2()  // ERROR 2
                ~~~~~~~~~~~~~~~
        src/test/pkg/test.kt:39: Error: Call requires API level 35.3 (current min is 35.2): requiresApi35_3 [NewApi]
                requiresApi35_3()  // ERROR 3
                ~~~~~~~~~~~~~~~
        src/test/pkg/test.kt:43: Error: Call requires API level 35.2 (current min is 35.1): requiresApi35_2 [NewApi]
                requiresApi35_2()  // ERROR 4
                ~~~~~~~~~~~~~~~
        src/test/pkg/test.kt:48: Error: Call requires API level 35.2 (current min is 35.1): requiresApi35_2 [NewApi]
                requiresApi35_2()  // ERROR 5
                ~~~~~~~~~~~~~~~
        5 errors, 0 warnings
        """
      )
  }

  override fun getDetector(): Detector {
    return ApiDetector()
  }
}

// Stub; can't use SUPPORT_ANNOTATIONS_JAR because it doesn't yet have the extension= field
private val checksSdkIntWithSdkStub: TestFile =
  java(
      """
      package androidx.annotation;
      import static java.lang.annotation.ElementType.FIELD;
      import static java.lang.annotation.ElementType.METHOD;
      import static java.lang.annotation.RetentionPolicy.CLASS;
      import java.lang.annotation.Documented;
      import java.lang.annotation.Retention;
      import java.lang.annotation.Target;
      @Documented
      @Retention(CLASS)
      @Target({METHOD, FIELD})
      public @interface ChecksSdkIntAtLeast {
          int api() default -1;
          String codename() default "";
          int parameter() default -1;
          int lambda() default -1;
          int extension() default 0;
      }
      """
    )
    .indented()

val newAndroidOsBuildStub: TestFile =
  java(
      // Stub until SDK_MINOR_INT is available everywhere
      """
      // HIDE-FROM-DOCUMENTATION
      package android.os;

      public class Build {
          public static class VERSION {
              public static int SDK_INT;
              public static int SDK_INT_FULL;
          }
          public static class VERSION_CODES {
              public static final int VANILLA_ICE_CREAM = 35;
          }
          public static class VERSION_CODES_FULL {
              private VERSION_CODES_FULL() {}

              private static final int SDK_INT_MULTIPLIER = 100000;

              public static final int VANILLA_ICE_CREAM_0 = SDK_INT_MULTIPLIER * VERSION_CODES.VANILLA_ICE_CREAM;
              public static final int VANILLA_ICE_CREAM_1 = SDK_INT_MULTIPLIER * VERSION_CODES.VANILLA_ICE_CREAM + 1;
              public static final int VANILLA_ICE_CREAM_2 = SDK_INT_MULTIPLIER * VERSION_CODES.VANILLA_ICE_CREAM + 2;
              public static final int VANILLA_ICE_CREAM_3 = SDK_INT_MULTIPLIER * VERSION_CODES.VANILLA_ICE_CREAM + 3;
          }
      }
      """
    )
    .indented()

val requiresExtensionStub: TestFile =
  kotlin(
      """
      package androidx.annotation

      import java.lang.annotation.ElementType.CONSTRUCTOR
      import java.lang.annotation.ElementType.FIELD
      import java.lang.annotation.ElementType.METHOD
      import java.lang.annotation.ElementType.PACKAGE
      import java.lang.annotation.ElementType.TYPE

      @MustBeDocumented
      @Retention(AnnotationRetention.BINARY)
      @Target(
          AnnotationTarget.ANNOTATION_CLASS,
          AnnotationTarget.CLASS,
          AnnotationTarget.FUNCTION,
          AnnotationTarget.PROPERTY_GETTER,
          AnnotationTarget.PROPERTY_SETTER,
          AnnotationTarget.CONSTRUCTOR,
          AnnotationTarget.FIELD,
          AnnotationTarget.FILE
      )
      @Suppress("DEPRECATED_JAVA_ANNOTATION", "SupportAnnotationUsage")
      @java.lang.annotation.Target(TYPE, METHOD, CONSTRUCTOR, FIELD, PACKAGE)
      @Repeatable
      annotation class RequiresExtension(
          val extension: Int,
          val version: Int
      )
      """
    )
    .indented()
