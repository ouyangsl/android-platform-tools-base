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

import com.android.tools.lint.checks.infrastructure.TestFiles.rClass
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import org.junit.Test

class ResourceTypeDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector = ResourceTypeDetector()

  fun testDocumentationExample() {
    lint()
      .files(
        kotlin(
            """
                import androidx.annotation.DrawableRes
                import androidx.annotation.StringRes

                fun setLabels(@StringRes titleId: Int) {
                    // ...
                }

                fun setIcon(@DrawableRes iconId: Int, @StringRes descId: Int) {
                    setLabels(iconId) // bug: should have passed descId. Both are ints but of wrong type.
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
            src/test.kt:9: Error: Expected resource of type string [ResourceType]
                setLabels(iconId) // bug: should have passed descId. Both are ints but of wrong type.
                          ~~~~~~
            1 errors, 0 warnings
            """
      )
  }

  fun testColorInt() {
    // Needs updated annotations!
    val expected =
      """
src/test/pkg/WrongColor.java:9: Error: Should pass resolved color instead of resource id here: getResources().getColor(R.color.blue) [ResourceAsColor]
        paint2.setColor(R.color.blue);
                        ~~~~~~~~~~~~
src/test/pkg/WrongColor.java:11: Error: Should pass resolved color instead of resource id here: getResources().getColor(R.color.red) [ResourceAsColor]
        textView.setTextColor(R.color.red);
                              ~~~~~~~~~~~
src/test/pkg/WrongColor.java:12: Error: Should pass resolved color instead of resource id here: getResources().getColor(android.R.color.black) [ResourceAsColor]
        textView.setTextColor(android.R.color.black);
                              ~~~~~~~~~~~~~~~~~~~~~
src/test/pkg/WrongColor.java:13: Error: Should pass resolved color instead of resource id here: getResources().getColor(R.color.blue) [ResourceAsColor]
        textView.setTextColor(foo > 0 ? R.color.green : R.color.blue);
                                                        ~~~~~~~~~~~~
src/test/pkg/WrongColor.java:13: Error: Should pass resolved color instead of resource id here: getResources().getColor(R.color.green) [ResourceAsColor]
        textView.setTextColor(foo > 0 ? R.color.green : R.color.blue);
                                        ~~~~~~~~~~~~~
src/test/pkg/WrongColor.java:21: Error: Should pass resolved color instead of resource id here: getResources().getColor(R.color.blue) [ResourceAsColor]
        foo2(R.color.blue);
             ~~~~~~~~~~~~
src/test/pkg/WrongColor.java:20: Error: Expected resource of type color [ResourceType]
        foo1(0xffff0000);
             ~~~~~~~~~~
7 errors, 0 warnings
"""
    lint()
      .files(
        java(
            "src/test/pkg/WrongColor.java",
            """
                package test.pkg;
                import android.app.Activity;
                import android.graphics.Paint;
                import android.widget.TextView;
                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class WrongColor extends Activity {
                    public void foo(TextView textView, int foo) {
                        Paint paint2 = new Paint();
                        paint2.setColor(R.color.blue);
                        // Wrong
                        textView.setTextColor(R.color.red);
                        textView.setTextColor(android.R.color.black);
                        textView.setTextColor(foo > 0 ? R.color.green : R.color.blue);
                        // OK
                        textView.setTextColor(getResources().getColor(R.color.red));
                        // OK
                        foo1(R.color.blue);
                        foo2(0xffff0000);
                        // Wrong
                        foo1(0xffff0000);
                        foo2(R.color.blue);
                    }

                    private void foo1(@androidx.annotation.ColorRes int c) {
                    }

                    private void foo2(@androidx.annotation.ColorInt int c) {
                    }
                }
                """,
          )
          .indented(),
        rClass("test.pkg", "@color/red", "@color/green", "@color/blue"),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(expected)
  }

  fun testColorInt2() {
    val expected =
      """
src/test/pkg/ColorTest.java:22: Error: Should pass resolved color instead of resource id here: getResources().getColor(actualColor) [ResourceAsColor]
        setColor2(actualColor); // ERROR
                  ~~~~~~~~~~~
src/test/pkg/ColorTest.java:23: Error: Should pass resolved color instead of resource id here: getResources().getColor(getColor1()) [ResourceAsColor]
        setColor2(getColor1()); // ERROR
                  ~~~~~~~~~~~
src/test/pkg/ColorTest.java:16: Error: Expected a color resource id (R.color.) but received an RGB integer [ResourceType]
        setColor1(actualColor); // ERROR
                  ~~~~~~~~~~~
src/test/pkg/ColorTest.java:17: Error: Expected a color resource id (R.color.) but received an RGB integer [ResourceType]
        setColor1(getColor2()); // ERROR
                  ~~~~~~~~~~~
4 errors, 0 warnings
"""
    lint()
      .files(
        java(
            "src/test/pkg/ColorTest.java",
            """
                package test.pkg;
                import androidx.annotation.ColorInt;
                import androidx.annotation.ColorRes;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public abstract class ColorTest {
                    @ColorRes
                    public abstract int getColor1();
                    public abstract void setColor1(@ColorRes int color);
                    @ColorInt
                    public abstract int getColor2();
                    public abstract void setColor2(@ColorInt int color);

                    public void test1() {
                        int actualColor = getColor2();
                        setColor1(actualColor); // ERROR
                        setColor1(getColor2()); // ERROR
                        setColor1(getColor1()); // OK
                    }
                    public void test2() {
                        int actualColor = getColor1();
                        setColor2(actualColor); // ERROR
                        setColor2(getColor1()); // ERROR
                        setColor2(getColor2()); // OK
                    }
                }
                """,
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(expected)
  }

  fun testColorInt3() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=176321
    val expected =
      """
src/test/pkg/ColorTest.java:11: Error: Expected a color resource id (R.color.) but received an RGB integer [ResourceType]
        setColor(actualColor);
                 ~~~~~~~~~~~
1 errors, 0 warnings
"""
    lint()
      .files(
        java(
            "src/test/pkg/ColorTest.java",
            """
                package test.pkg;
                import android.content.Context;
                import android.content.res.Resources;
                import androidx.annotation.ColorRes;
                @SuppressWarnings("ClassNameDiffersFromFileName")
                public abstract class ColorTest {
                    public abstract void setColor(@ColorRes int color);

                    public void test(Context context, @ColorRes int id) {
                        int actualColor = context.getResources().getColor(id, null);
                        setColor(actualColor);
                    }
                }
                """,
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(expected)
  }

  fun testPx() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import static androidx.annotation.Dimension.DP;
                import static androidx.annotation.Dimension.PX;
                import static androidx.annotation.Dimension.SP;

                import androidx.annotation.DimenRes;
                import androidx.annotation.Dimension;
                import androidx.annotation.Px;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public abstract class PxTest {
                    @DimenRes public abstract int getDimensionId();
                    public abstract void setDimensionId(@DimenRes int dimension);

                    @Px public abstract int getDimensionPx();
                    public abstract void setDimensionPx(@Px int dimension);

                    public abstract void setDimension(@Dimension int dimension); // alias for @Px
                    public abstract void setDimensionPx2(@Dimension(unit = PX) int dimension); // alias for @Px
                    public abstract void setDimensionDp(@Dimension(unit = DP) int dimension);
                    @Dimension(unit = SP) public abstract int getDimensionSp();
                    public abstract void setDimensionSp(@Dimension(unit = SP) int dimension);

                    public void test1() {
                        int actualSize = getDimensionPx();
                        setDimensionId(actualSize);       // @Px to @DimenRes: ERROR 1
                        setDimensionId(getDimensionPx()); // @Px to @DimenRes: ERROR 2
                        setDimensionId(getDimensionId()); // @DimenRes to @DimenRes: OK 1
                    }

                    public void test2() {
                        int actualSize = getDimensionId();
                        setDimensionPx(actualSize);       // @DimenRes to @Px: ERROR 3
                        setDimensionPx(getDimensionId()); // @DimenRes to @Px: ERROR 4
                        setDimensionPx(getDimensionPx()); // @Px to @Px: OK 2
                    }

                    public void test3() {
                        setDimension(getDimensionPx());    // @Px to @Dimension: OK 3
                        setDimensionPx2(getDimensionPx()); // @Px to @Dimension(PX): OK 4
                        setDimensionDp(getDimensionPx());  // @Px to @Dimension(DP): ERROR 5
                        setDimensionSp(getDimensionPx());  // @Px to @Dimension(SP): ERROR 6
                        setDimensionSp(getDimensionSp());  // @Dimension(SP) to @Dimension(SP): OK 5
                        setDimensionDp(getDimensionSp());  // @Dimension(SP) to @Dimension(DP): ERROR 7
                        setDimensionPx(getDimensionSp());  // @Dimension(SP) to @Px: ERROR 8
                        setDimension(getDimensionId());    // @DimenRes to @Dimension: ERROR 9
                    }

                    public void binaryTest(android.app.Notification.BubbleMetadata builder,
                                           android.widget.ProgressBar progressBar) {
                        progressBar.setMaxHeight( // annotated @PX
                                builder.getDesiredHeight() // annotated @DP  // ERROR 10
                        );
                    }
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .allowDuplicates()
      .run()
      .expect(
        """
            src/test/pkg/PxTest.java:27: Error: Expected a dimension resource id (R.dimen.) but received a pixel integer [ResourceType]
                    setDimensionId(actualSize);       // @Px to @DimenRes: ERROR 1
                                   ~~~~~~~~~~
            src/test/pkg/PxTest.java:28: Error: Expected a dimension resource id (R.dimen.) but received a pixel integer [ResourceType]
                    setDimensionId(getDimensionPx()); // @Px to @DimenRes: ERROR 2
                                   ~~~~~~~~~~~~~~~~
            src/test/pkg/PxTest.java:34: Error: Should pass resolved pixel dimension instead of resource id here: getResources().getDimension*(actualSize) [ResourceType]
                    setDimensionPx(actualSize);       // @DimenRes to @Px: ERROR 3
                                   ~~~~~~~~~~
            src/test/pkg/PxTest.java:35: Error: Should pass resolved pixel dimension instead of resource id here: getResources().getDimension*(getDimensionId()) [ResourceType]
                    setDimensionPx(getDimensionId()); // @DimenRes to @Px: ERROR 4
                                   ~~~~~~~~~~~~~~~~
            src/test/pkg/PxTest.java:42: Error: Mismatched @Dimension units here; expected density-independent (dp) integer but received a pixel integer [ResourceType]
                    setDimensionDp(getDimensionPx());  // @Px to @Dimension(DP): ERROR 5
                                   ~~~~~~~~~~~~~~~~
            src/test/pkg/PxTest.java:43: Error: Mismatched @Dimension units here; expected a scale-independent (sp) integer but received a pixel integer [ResourceType]
                    setDimensionSp(getDimensionPx());  // @Px to @Dimension(SP): ERROR 6
                                   ~~~~~~~~~~~~~~~~
            src/test/pkg/PxTest.java:45: Error: Mismatched @Dimension units here; expected density-independent (dp) integer but received a scale-independent (sp) integer [ResourceType]
                    setDimensionDp(getDimensionSp());  // @Dimension(SP) to @Dimension(DP): ERROR 7
                                   ~~~~~~~~~~~~~~~~
            src/test/pkg/PxTest.java:46: Error: Mismatched @Dimension units here; expected a pixel integer but received a scale-independent (sp) integer [ResourceType]
                    setDimensionPx(getDimensionSp());  // @Dimension(SP) to @Px: ERROR 8
                                   ~~~~~~~~~~~~~~~~
            src/test/pkg/PxTest.java:47: Error: Should pass resolved pixel dimension instead of resource id here: getResources().getDimension*(getDimensionId()) [ResourceType]
                    setDimension(getDimensionId());    // @DimenRes to @Dimension: ERROR 9
                                 ~~~~~~~~~~~~~~~~
            src/test/pkg/PxTest.java:53: Error: Mismatched @Dimension units here; expected a pixel integer but received density-independent (dp) integer [ResourceType]
                            builder.getDesiredHeight() // annotated @DP  // ERROR 10
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~
            10 errors, 0 warnings
            """
      )
  }

  fun testPx2() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=229189

    lint()
      .files(
        java(
          "" +
            "\n" +
            "package com.example;\n" +
            "\n" +
            "import android.app.Activity;\n" +
            "import android.content.Context;\n" +
            "import androidx.annotation.Dimension;\n" +
            "import androidx.annotation.Px;\n" +
            "import android.util.TypedValue;\n" +
            "import android.widget.TextView;\n" +
            "\n" +
            "public class X extends Activity {\n" +
            "    public void test(TextView someView, boolean condition) {\n" +
            "        someView.setPadding(0, 0, 0, condition ? (int) convertDpToPixels(8) : 0);\n" +
            "        someView.setPadding(0, 0, 0, condition ? (int) convertDpToPixelsNoAnnotation(8) : 0);\n" +
            "        setPadding(0, 0, 0, condition ? (int) convertDpToPixelsNoAnnotation(8) : 0);\n" +
            "    }\n" +
            "\n" +
            "    @Dimension(unit = Dimension.PX)\n" +
            "    public float convertDpToPixels(final float dp) {\n" +
            "        Context context = this;\n" +
            "        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());\n" +
            "    }\n" +
            "\n" +
            "    public float convertDpToPixelsNoAnnotation(final float dp) {\n" +
            "        Context context = this;\n" +
            "        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());\n" +
            "    }\n" +
            "\n" +
            "    private void setPadding(@Px int a, @Px int b, @Px int c, @Px int d) {\n" +
            "    }\n" +
            "}\n"
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun testResourceType() {
    val expected =
      """
src/p1/p2/Flow.java:13: Error: Expected resource of type drawable [ResourceType]
        resources.getDrawable(10); // ERROR
                              ~~
src/p1/p2/Flow.java:18: Error: Expected resource of type drawable [ResourceType]
        resources.getDrawable(R.string.my_string); // ERROR
                              ~~~~~~~~~~~~~~~~~~
src/p1/p2/Flow.java:22: Error: Expected resource of type drawable [ResourceType]
        myMethod(R.string.my_string, null); // ERROR
                 ~~~~~~~~~~~~~~~~~~
src/p1/p2/Flow.java:26: Error: Expected resource of type drawable [ResourceType]
        resources.getDrawable(R.string.my_string); // ERROR
                              ~~~~~~~~~~~~~~~~~~
src/p1/p2/Flow.java:32: Error: Expected resource identifier (R.type.name) [ResourceType]
        myAnyResMethod(50); // ERROR
                       ~~
src/p1/p2/Flow.java:43: Error: Expected resource of type drawable [ResourceType]
        resources.getDrawable(s1); // ERROR
                              ~~
src/p1/p2/Flow.java:50: Error: Expected resource of type drawable [ResourceType]
        resources.getDrawable(MimeTypes.style); // ERROR
                              ~~~~~~~~~~~~~~~
src/p1/p2/Flow.java:60: Error: Expected resource of type drawable [ResourceType]
        resources.getDrawable(MimeTypes.getAnnotatedString()); // Error
                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
src/p1/p2/Flow.java:68: Error: Expected resource of type drawable [ResourceType]
        myMethod(z, null); // ERROR
                 ~
src/p1/p2/Flow.java:71: Error: Expected resource of type drawable [ResourceType]
        myMethod(w, null); // ERROR
                 ~
src/p1/p2/Flow.java:120: Error: Expected resource of type font [ResourceType]
            myFontResMethod(R.drawable.my_drawable);
                            ~~~~~~~~~~~~~~~~~~~~~~
11 errors, 0 warnings
"""
    lint()
      .files(
        java(
            "src/p1/p2/Flow.java",
            """
                import android.content.res.Resources;
                import androidx.annotation.DrawableRes;
                import androidx.annotation.FontRes;
                import androidx.annotation.StringRes;
                import androidx.annotation.StyleRes;
                import java.util.Random;

                @SuppressWarnings({"UnusedDeclaration", "ClassNameDiffersFromFileName", "MethodMayBeStatic", "UnnecessaryLocalVariable"})
                public class Flow {
                    public void testLiterals(Resources resources) {
                        resources.getDrawable(0); // OK
                        resources.getDrawable(-1); // OK
                        resources.getDrawable(10); // ERROR
                    }

                    public void testConstants(Resources resources) {
                        resources.getDrawable(R.drawable.my_drawable); // OK
                        resources.getDrawable(R.string.my_string); // ERROR
                    }

                    public void testLocalAnnotation() {
                        myMethod(R.string.my_string, null); // ERROR
                    }

                    private void myMethod(@DrawableRes int arg, Resources resources) {
                        resources.getDrawable(R.string.my_string); // ERROR
                    }

                    private void testAnyRes() {
                        myAnyResMethod(R.drawable.my_drawable); // OK
                        myAnyResMethod(R.string.my_string); // OK
                        myAnyResMethod(50); // ERROR
                    }

                    private void myAnyResMethod(@androidx.annotation.AnyRes int arg) {
                    }

                    public void testFields(String fileExt, Resources resources) {
                        int mimeIconId = MimeTypes.styleAndDrawable;
                        resources.getDrawable(mimeIconId); // OK

                        int s1 = MimeTypes.style;
                        resources.getDrawable(s1); // ERROR
                        int s2 = MimeTypes.styleAndDrawable;
                        resources.getDrawable(s2); // OK
                        int w3 = MimeTypes.drawable;
                        resources.getDrawable(w3); // OK

                        // Direct reference
                        resources.getDrawable(MimeTypes.style); // ERROR
                        resources.getDrawable(MimeTypes.styleAndDrawable); // OK
                        resources.getDrawable(MimeTypes.drawable); // OK
                    }

                    public void testCalls(String fileExt, Resources resources) {
                        int mimeIconId = MimeTypes.getIconForExt(fileExt);
                        resources.getDrawable(mimeIconId); // OK
                        resources.getDrawable(MimeTypes.getInferredString()); // OK (wrong but can't infer type)
                        resources.getDrawable(MimeTypes.getInferredDrawable()); // OK
                        resources.getDrawable(MimeTypes.getAnnotatedString()); // Error
                        resources.getDrawable(MimeTypes.getAnnotatedDrawable()); // OK
                        resources.getDrawable(MimeTypes.getUnknownType()); // OK (unknown/uncertain)
                    }

                    public void testFlow() {
                        int x = R.string.my_string;
                        int z = x;
                        myMethod(z, null); // ERROR

                        int w = MY_RESOURCE;
                        myMethod(w, null); // ERROR
                    }

                    private static final int MY_RESOURCE = R.string.my_string;

                    private static class MimeTypes {
                        @androidx.annotation.StyleRes
                        @androidx.annotation.DrawableRes
                        public static int styleAndDrawable;

                        @androidx.annotation.StyleRes
                        public static int style;

                        @androidx.annotation.DrawableRes
                        public static int drawable;

                        @androidx.annotation.DrawableRes
                        public static int getIconForExt(String ext) {
                            return R.drawable.my_drawable;
                        }

                        public static int getInferredString() {
                            // Implied string - can we handle this?
                            return R.string.my_string;
                        }

                        public static int getInferredDrawable() {
                            // Implied drawable - can we handle this?
                            return R.drawable.my_drawable;
                        }

                        @androidx.annotation.StringRes
                        public static int getAnnotatedString() {
                            return R.string.my_string;
                        }

                        @androidx.annotation.DrawableRes
                        public static int getAnnotatedDrawable() {
                            return R.drawable.my_drawable;
                        }

                        public static int getUnknownType() {
                            return new Random(1000).nextInt();
                        }

                        private void myFontResMethod(@FontRes int arg) {
                        }

                        private static void myTestFont() {
                            myFontResMethod(R.drawable.my_drawable);
                        }
                    }

                    public static final class R {
                        public static final class drawable {
                            public static final int my_drawable =0x7f020057;
                        }
                        public static final class string {
                            public static final int my_string =0x7f0a000e;
                        }
                    }
                }
                """,
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(expected)
  }

  fun testTypes2() {
    val expected =
      """
src/test/pkg/ActivityType.java:5: Error: Expected resource of type drawable [ResourceType]
    SKI(1),
        ~
src/test/pkg/ActivityType.java:6: Error: Expected resource of type drawable [ResourceType]
    SNOWBOARD(2);
              ~
2 errors, 0 warnings
"""
    lint()
      .files(
        java(
            "src/test/pkg/ActivityType.java",
            """
                import androidx.annotation.DrawableRes;
                @SuppressWarnings({"ClassNameDiffersFromFileName","FieldCanBeLocal"})
                enum ActivityType {

                    SKI(1),
                    SNOWBOARD(2);

                    private final int mIconResId;

                    ActivityType(@DrawableRes int iconResId) {
                        mIconResId = iconResId;
                    }
                }""",
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(expected)
  }

  fun testConstructor() {
    val expected =
      """
src/test/pkg/ConstructorTest.java:14: Error: Expected resource of type drawable [ResourceType]
        new ConstructorTest(1, 3);
                            ~
1 errors, 0 warnings
"""
    lint()
      .files(
        java(
            "src/test/pkg/ConstructorTest.java",
            """
                package test.pkg;
                import androidx.annotation.DrawableRes;
                import androidx.annotation.IntRange;
                import androidx.annotation.UiThread;
                import androidx.annotation.WorkerThread;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic", "ResultOfObjectAllocationIgnored"})
                public class ConstructorTest {
                    @UiThread
                    ConstructorTest(@DrawableRes int iconResId, @IntRange(from = 5) int start) {
                    }

                    public void testParameters() {
                        new ConstructorTest(1, 3);
                    }

                    @WorkerThread
                    public void testMethod(int res, int range) {
                        new ConstructorTest(res, range);
                    }
                }
                """,
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(expected)
  }

  fun testColorAsDrawable() {

    lint()
      .files(
        java(
            """
                package p1.p2;
                import android.content.Context;
                import android.view.View;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class ColorAsDrawable {
                    static void test(Context context) {
                        View separator = new View(context);
                        separator.setBackgroundResource(android.R.color.black);
                    }
                }
                """
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testIdResource() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=220612
    lint()
      .files(
        java(
            "src/com./example/myapplication/Test1.java",
            """
                package com.example.myapplication;
                import androidx.annotation.IdRes;
                import androidx.annotation.LayoutRes;

                @SuppressWarnings({"ClassNameDiffersFromFileName","FieldCanBeLocal"})
                public class Test1 {

                    private final int layout;
                    private final int id;
                    private boolean visible;

                    public Test1(@LayoutRes int layout, @IdRes int id) {
                        this.layout = layout;
                        this.id = id;
                        this.visible = true;
                    }
                }""",
          )
          .indented(),
        java(
            "src/com/example/myapplication/Test2.java",
            """
                package com.example.myapplication;
                import androidx.annotation.IdRes;
                import androidx.annotation.StringRes;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class Test2 extends Test1 {

                    public Test2(@IdRes int id, @StringRes int titleResId) {
                        super(R.layout.somelayout, id);
                    }
                    public static final class R {
                        public static final class layout {
                            public static final int somelayout = 0x7f0a0000;
                        }
                    }
                }""",
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun testMultipleResourceTypes() {
    // Regression test for
    //   https://code.google.com/p/android/issues/detail?id=187181
    // Make sure that parameters which specify multiple resource types are handled
    // correctly.
    val expected =
      "src/test/pkg/ResourceTypeTest.java:14: Error: Expected resource of type drawable or string [ResourceType]\n" +
        "        new ResourceTypeTest(res, R.raw.my_raw_file); // ERROR\n" +
        "                                  ~~~~~~~~~~~~~~~~~\n" +
        "1 errors, 0 warnings\n"
    lint()
      .files(
        java(
          "src/test/pkg/ResourceTypeTest.java",
          "" +
            "package test.pkg;\n" +
            "\n" +
            "import android.content.res.Resources;\n" +
            "import androidx.annotation.DrawableRes;\n" +
            "import androidx.annotation.StringRes;\n" +
            "\n" +
            "public class ResourceTypeTest {\n" +
            "    public ResourceTypeTest(Resources res, @DrawableRes @StringRes int id) {\n" +
            "    }\n" +
            "\n" +
            "    public static void test(Resources res) {\n" +
            "        new ResourceTypeTest(res, R.drawable.ic_announcement_24dp); // OK\n" +
            "        new ResourceTypeTest(res, R.string.action_settings); // OK\n" +
            "        new ResourceTypeTest(res, R.raw.my_raw_file); // ERROR\n" +
            "    }\n" +
            "\n" +
            "    public static final class R {\n" +
            "        public static final class drawable {\n" +
            "            public static final int ic_announcement_24dp = 0x7f0a0000;\n" +
            "        }\n" +
            "        public static final class string {\n" +
            "            public static final int action_settings = 0x7f0a0001;\n" +
            "        }\n" +
            "        public static final class raw {\n" +
            "            public static final int my_raw_file = 0x7f0a0002;\n" +
            "        }\n" +
            "    }" +
            "}",
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(expected)
  }

  fun testComparingResourceTypesJava() {
    val expected =
      "" +
        "src/test/pkg/ResourceTypeTest.java:9: Error: Comparing resource types (@DrawableRes) other than equality is dangerous and usually wrong;  some resource types set top bit which turns the value negative [ResourceType]\n" +
        "        if (id < 0) { // ERROR\n" +
        "            ~~~~~~\n" +
        "src/test/pkg/ResourceTypeTest.java:11: Error: Comparing resource types (@DrawableRes) other than equality is dangerous and usually wrong;  some resource types set top bit which turns the value negative [ResourceType]\n" +
        "        if (0 >= id) { // ERROR\n" +
        "            ~~~~~~~\n" +
        "2 errors, 0 warnings\n"

    lint()
      .files(
        java(
          "src/test/pkg/ResourceTypeTest.java",
          "" +
            "package test.pkg;\n" +
            "\n" +
            "import android.content.res.Resources;\n" +
            "import androidx.annotation.DrawableRes;\n" +
            "import androidx.annotation.StringRes;\n" +
            "\n" +
            "public class ResourceTypeTest {\n" +
            "    public void test(Resources res, @DrawableRes @StringRes int id) {\n" +
            "        if (id < 0) { // ERROR\n" +
            "        }\n" +
            "        if (0 >= id) { // ERROR\n" +
            "        }\n" +
            "        if (id == 0) { // OK\n" +
            "        }\n" +
            "        if (id != 0) { // OK\n" +
            "        }\n" +
            "    }\n" +
            "}",
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .allowDuplicates()
      .run()
      .expect(expected)
  }

  fun testComparingResourceTypesKotlin() {
    val expected =
      "" +
        "src/test/pkg/ResourceTypeTest.kt:9: Error: Comparing resource types (@DrawableRes) other than equality is dangerous and usually wrong;  some resource types set top bit which turns the value negative [ResourceType]\n" +
        "        if (id < 0) { // ERROR\n" +
        "            ~~~~~~\n" +
        "src/test/pkg/ResourceTypeTest.kt:11: Error: Comparing resource types (@DrawableRes) other than equality is dangerous and usually wrong;  some resource types set top bit which turns the value negative [ResourceType]\n" +
        "        if (0 >= id) { // ERROR\n" +
        "            ~~~~~~~\n" +
        "2 errors, 0 warnings\n"

    lint()
      .files(
        kotlin(
          "src/test/pkg/ResourceTypeTest.kt",
          "" +
            "package test.pkg\n" +
            "\n" +
            "import android.content.res.Resources\n" +
            "import androidx.annotation.DrawableRes\n" +
            "import androidx.annotation.StringRes\n" +
            "\n" +
            "class ResourceTypeTest {\n" +
            "    fun test(res: Resources, @DrawableRes @StringRes id: Int) {\n" +
            "        if (id < 0) { // ERROR\n" +
            "        }\n" +
            "        if (0 >= id) { // ERROR\n" +
            "        }\n" +
            "        if (id == 0) { // OK\n" +
            "        }\n" +
            "        if (id != 0) { // OK\n" +
            "        }\n" +
            "    }\n" +
            "}",
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .allowDuplicates()
      .run()
      .expect(expected)
  }

  fun testHalfFloats() {
    lint()
      .files(
        java(
          "" +
            "package test.pkg;\n" +
            "\n" +
            "\n" +
            "import androidx.annotation.ColorInt;\n" +
            "import androidx.annotation.DimenRes;\n" +
            "import androidx.annotation.HalfFloat;\n" +
            "import androidx.annotation.Px;\n" +
            "import androidx.annotation.StringRes;\n" +
            "\n" +
            "@SuppressWarnings({\"UnnecessaryLocalVariable\", \"ResultOfMethodCallIgnored\", \"WeakerAccess\"})\n" +
            "public class HalfFloatTest {\n" +
            "    public void method1(@HalfFloat short foo) {\n" +
            "    }\n" +
            "\n" +
            "    @HalfFloat\n" +
            "    public short method2() {\n" +
            "    }\n" +
            "\n" +
            "    public void testHalfFloat() {\n" +
            "        int myFloat = method2(); // WARN: widening\n" +
            "        short myFloat2 = method2();\n" +
            "        boolean x1 = myFloat2 != 0; // implicit conversion\n" +
            "        method1(getDimension1()); // ERROR\n" +
            "        method1(getDimension2()); // ERROR\n" +
            "        method1(getActualColor()); // ERROR\n" +
            "        method1(getTextId()); // ERROR\n" +
            "    }\n" +
            "\n" +
            "    public void testConstants() {\n" +
            "        // TODO: Look for constant usages\n" +
            "    }\n" +
            "\n" +
            "    public void testOperations(@HalfFloat short float1, short float2) {\n" +
            "        boolean x1 = float1 < float2;\n" +
            "        boolean x2 = float2 > float1;\n" +
            "        // because implicit promotions to int\n" +
            "    }\n" +
            "\n" +
            "    public void testWidening(@HalfFloat short float1, short float2) {\n" +
            "        short result1 = float1; // ok\n" +
            "        int result2 = float2; // error: widening\n" +
            "        Math.abs(float2); // error: widening\n" +
            "        int result3 = float1 + 1; // error: widening\n" +
            "        boolean result4 = float1 + 1 > 5; // error: widening\n" +
            "        byte b = 1;\n" +
            "        method1(b); // ERROR: widening\n" +
            "    }\n" +
            "\n" +
            "    public void testWrongMethod(@HalfFloat short float1) {\n" +
            "        Math.round(float1); // Error: should use Half.round\n" +
            "    }\n" +
            "\n" +
            "\n" +
            "    @DimenRes\n" +
            "    public abstract int getDimension1();\n" +
            "    @ColorInt\n" +
            "    public abstract int getActualColor();\n" +
            "    @StringRes\n" +
            "    public abstract int getTextId();\n" +
            "    public abstract void setDimension1(@DimenRes int dimension);\n" +
            "    @Px\n" +
            "    public abstract int getDimension2();\n" +
            "    public abstract void setDimension2(@Px int dimension);\n" +
            "\n" +
            "    // TODO: Arrays\n" +
            "    // TODO: Add cast\n" +
            "}\n"
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        "" +
          "src/test/pkg/HalfFloatTest.java:23: Error: Expected a half float here, not a resource id [HalfFloat]\n" +
          "        method1(getDimension1()); // ERROR\n" +
          "                ~~~~~~~~~~~~~~~\n" +
          "src/test/pkg/HalfFloatTest.java:24: Error: Expected a half float here, not a dimension [HalfFloat]\n" +
          "        method1(getDimension2()); // ERROR\n" +
          "                ~~~~~~~~~~~~~~~\n" +
          "src/test/pkg/HalfFloatTest.java:25: Error: Expected a half float here, not a color [HalfFloat]\n" +
          "        method1(getActualColor()); // ERROR\n" +
          "                ~~~~~~~~~~~~~~~~\n" +
          "src/test/pkg/HalfFloatTest.java:26: Error: Expected a half float here, not a resource id [HalfFloat]\n" +
          "        method1(getTextId()); // ERROR\n" +
          "                ~~~~~~~~~~~\n" +
          "src/test/pkg/HalfFloatTest.java:43: Error: Half-float type in expression widened to int [HalfFloat]\n" +
          "        int result3 = float1 + 1; // error: widening\n" +
          "                      ~~~~~~\n" +
          "src/test/pkg/HalfFloatTest.java:44: Error: Half-float type in expression widened to int [HalfFloat]\n" +
          "        boolean result4 = float1 + 1 > 5; // error: widening\n" +
          "                          ~~~~~~\n" +
          "src/test/pkg/HalfFloatTest.java:50: Error: Half-float type in expression widened to int [HalfFloat]\n" +
          "        Math.round(float1); // Error: should use Half.round\n" +
          "                   ~~~~~~\n" +
          "7 errors, 0 warnings\n"
      )
  }

  fun testHalfFloatConstruction() {
    // Regression test for https://issuetracker.google.com/72509078
    lint()
      .files(
        kotlin(
            """
                    package test.pkg

                    import android.os.Build
                    import androidx.annotation.RequiresApi
                    import android.util.Half

                    @Suppress("unused", "UNUSED_VARIABLE")
                    @RequiresApi(Build.VERSION_CODES.O)
                    fun halfFloat(x: Short) {
                        val v1 = Half.valueOf(x)
                        val v2 = Half(x)
                    }
                    """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun testAnyRes() {
    // Make sure error messages for @AnyRes are handled right since it's now an
    // enum set containing all possible resource types
    val expected =
      "src/test/pkg/AnyResTest.java:14: Error: Expected resource identifier (R.type.name) [ResourceType]\n" +
        "        new AnyResTest(res, 52); // ERROR\n" +
        "                            ~~\n" +
        "1 errors, 0 warnings\n"
    lint()
      .files(
        java(
          "src/test/pkg/AnyResTest.java",
          "" +
            "package test.pkg;\n" +
            "\n" +
            "import android.content.res.Resources;\n" +
            "import androidx.annotation.AnyRes;\n" +
            "\n" +
            "public class AnyResTest {\n" +
            "    public AnyResTest(Resources res, @AnyRes int id) {\n" +
            "    }\n" +
            "\n" +
            "    public static void test(Resources res) {\n" +
            "        new AnyResTest(res, R.drawable.ic_announcement_24dp); // OK\n" +
            "        new AnyResTest(res, R.string.action_settings); // OK\n" +
            "        new AnyResTest(res, R.raw.my_raw_file); // OK\n" +
            "        new AnyResTest(res, 52); // ERROR\n" +
            "    }\n" +
            "\n" +
            "    public static final class R {\n" +
            "        public static final class drawable {\n" +
            "            public static final int ic_announcement_24dp = 0x7f0a0000;\n" +
            "        }\n" +
            "        public static final class string {\n" +
            "            public static final int action_settings = 0x7f0a0001;\n" +
            "        }\n" +
            "        public static final class raw {\n" +
            "            public static final int my_raw_file = 0x7f0a0002;\n" +
            "        }\n" +
            "    }" +
            "}",
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(expected)
  }

  fun testObtainStyledAttributes() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=201882
    // obtainStyledAttributes normally expects a styleable but you can also supply a
    // custom int array
    lint()
      .files(
        java(
          "src/test/pkg/ObtainTest.java",
          "" +
            "package test.pkg;\n" +
            "\n" +
            "import android.app.Activity;\n" +
            "import android.content.Context;\n" +
            "import android.content.res.TypedArray;\n" +
            "import android.graphics.Color;\n" +
            "import android.util.AttributeSet;\n" +
            "\n" +
            "public class ObtainTest {\n" +
            "    public static void test1(Activity activity, float[] foregroundHsv, float[] backgroundHsv) {\n" +
            "        TypedArray attributes = activity.obtainStyledAttributes(\n" +
            "                new int[] {\n" +
            "                        R.attr.setup_wizard_navbar_theme,\n" +
            "                        android.R.attr.colorForeground,\n" +
            "                        android.R.attr.colorBackground });\n" +
            "        Color.colorToHSV(attributes.getColor(1, 0), foregroundHsv);\n" +
            "        Color.colorToHSV(attributes.getColor(2, 0), backgroundHsv);\n" +
            "        attributes.recycle();\n" +
            "    }\n" +
            "\n" +
            "    public static void test2(Context context, AttributeSet attrs, int defStyle) {\n" +
            "        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.BezelImageView,\n" +
            "                defStyle, 0);\n" +
            "        a.getDrawable(R.styleable.BezelImageView_maskDrawable);\n" +
            "        a.recycle();\n" +
            "    }\n" +
            "\n" +
            "    public void test(Context context, AttributeSet attrs) {\n" +
            "        int[] attrsArray = new int[] {\n" +
            "                android.R.attr.entries, // 0\n" +
            "                android.R.attr.labelFor\n" +
            "        };\n" +
            "        TypedArray ta = context.obtainStyledAttributes(attrs, attrsArray);\n" +
            "        if(null == ta) {\n" +
            "            return;\n" +
            "        }\n" +
            "        CharSequence[] entries = ta.getTextArray(0);\n" +
            "        CharSequence label = ta.getText(1);\n" +
            "    }\n" +
            "\n" +
            "    public static class R {\n" +
            "        public static class attr {\n" +
            "            public static final int setup_wizard_navbar_theme = 0x7f01003b;\n" +
            "        }\n" +
            "        public static class styleable {\n" +
            "            public static final int[] BezelImageView = {\n" +
            "                    0x7f01005d, 0x7f01005e, 0x7f01005f\n" +
            "            };\n" +
            "            public static final int BezelImageView_maskDrawable = 0;\n" +
            "        }\n" +
            "    }\n" +
            "}\n",
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun testExtensionMethods() {
    // Regression test for https://issuetracker.google.com/65602862
    lint()
      .files(
        kotlin(
          "" +
            "package test.pkg\n" +
            "\n" +
            "import android.app.Activity\n" +
            "import android.content.Context\n" +
            "import android.content.res.Resources\n" +
            "import androidx.annotation.AttrRes\n" +
            "import androidx.annotation.ColorInt\n" +
            "\n" +
            "class TestActivity: Activity() {\n" +
            "\n" +
            "    @ColorInt\n" +
            "    fun Context.getColor1(@AttrRes attrId: Int, @ColorInt defaultColor: Int) = theme.getColor(attrId, defaultColor)\n" +
            "    @ColorInt\n" +
            "    fun Context.getColor2(@AttrRes attrId: Int, @ColorInt defaultColor: Int) { return theme.getColor(attrId, defaultColor) }\n" +
            "    @ColorInt\n" +
            "    fun Context.getColor3(@AttrRes attrId: Int, @ColorInt defaultColor: Int) { return theme.getColor(defaultColor = defaultColor, attrId = attrId) }\n" +
            "    fun Context.getColor4() { return theme.getColor(defaultColor = 0xFFFFFF00, attrId = 0) }\n" +
            "    @ColorInt\n" +
            "    fun Resources.Theme.getColor(@AttrRes attrId: Int, @ColorInt defaultColor: Int): Int {\n" +
            "        return 0;\n" +
            "    }\n" +
            "}"
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .checkMessage({ context, issue, severity, location, message, fixData ->
        this.checkReportedError(context, issue, severity, location, message, fixData)
      })
      .run()
      .expectClean()
  }

  fun testArgumentMapping() {
    // Check that the argument mapping (where parameters are not in the same order
    // as arguments, thanks to named and default parameters as well as extension methods)
    // works correctly.
    lint()
      .files(
        kotlin(
          "@file:Suppress(\"unused\", \"UNUSED_PARAMETER\")\n" +
            "\n" +
            "package test.pkg\n" +
            "\n" +
            "import androidx.annotation.DimenRes\n" +
            "import androidx.annotation.DrawableRes\n" +
            "import androidx.annotation.StringRes\n" +
            "\n" +
            "fun target(@DrawableRes icon: Int = 0,\n" +
            "           @StringRes string: Int = 0,\n" +
            "           vararg @DimenRes dimensions: Int = IntArray(0)) {\n" +
            "}\n" +
            "\n" +
            "fun String.handleResourceTypes(@DrawableRes icon: Int = 0,\n" +
            "                               @StringRes string: Int = 0,\n" +
            "                               vararg @DimenRes dimensions: Int = IntArray(0)) {\n" +
            "}\n" +
            "\n" +
            "fun testNamedParametersAndDefaults(@DrawableRes myIcon: Int,\n" +
            "                                   @StringRes myString: Int,\n" +
            "                                   @DimenRes myDimension1: Int,\n" +
            "                                   @DimenRes myDimension2: Int) {\n" +
            "    target(myIcon) // OK\n" +
            "    target(myIcon, myString, myDimension1) // OK\n" +
            "    target(myIcon, myString, myDimension1, myDimension1) // OK\n" +
            "    target(icon = myIcon, string = myString, dimensions = myDimension1) // OK\n" +
            "    target(string = myString, dimensions = myDimension1, icon = myIcon) // OK\n" +
            "    target(icon = myIcon) // OK\n" +
            "    target(string = myString) // OK\n" +
            "    target(dimensions = myDimension1) // OK\n" +
            "    target(myIcon, dimensions = myDimension1) // OK\n" +
            "\n" +
            "    target(myString) // ERROR\n" +
            "    target(dimensions = myIcon) // ERROR\n" +
            "    target(myIcon, dimensions = myString) // ERROR\n" +
            "}\n" +
            "\n" +
            "fun testExtensionMethods(\n" +
            "        string: String,\n" +
            "        @DrawableRes myIcon: Int,\n" +
            "        @StringRes myString: Int,\n" +
            "        @DimenRes myDimension1: Int,\n" +
            "        @DimenRes myDimension2: Int) {\n" +
            "    string.handleResourceTypes(myIcon) // OK\n" +
            "    string.handleResourceTypes(myIcon, myString, myDimension1) // OK\n" +
            "    string.handleResourceTypes(myIcon, myString, myDimension1, myDimension1) // OK\n" +
            "    string.handleResourceTypes(icon = myIcon, string = myString, dimensions = myDimension1) // OK\n" +
            "    string.handleResourceTypes(string = myString, dimensions = myDimension1, icon = myIcon) // OK\n" +
            "    string.handleResourceTypes(icon = myIcon) // OK\n" +
            "    string.handleResourceTypes(string = myString) // OK\n" +
            "    string.handleResourceTypes(dimensions = myDimension1) // OK\n" +
            "    string.handleResourceTypes(myIcon, dimensions = myDimension1) // OK\n" +
            "\n" +
            "    string.handleResourceTypes(myString) // ERROR\n" +
            "    string.handleResourceTypes(dimensions = myIcon) // ERROR\n" +
            "    string.handleResourceTypes(myIcon, dimensions = myString) // ERROR\n" +
            "}\n"
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        "src/test/pkg/test.kt:33: Error: Expected resource of type drawable [ResourceType]\n" +
          "    target(myString) // ERROR\n" +
          "           ~~~~~~~~\n" +
          "src/test/pkg/test.kt:34: Error: Expected resource of type dimen [ResourceType]\n" +
          "    target(dimensions = myIcon) // ERROR\n" +
          "                        ~~~~~~\n" +
          "src/test/pkg/test.kt:35: Error: Expected resource of type dimen [ResourceType]\n" +
          "    target(myIcon, dimensions = myString) // ERROR\n" +
          "                                ~~~~~~~~\n" +
          "src/test/pkg/test.kt:54: Error: Expected resource of type drawable [ResourceType]\n" +
          "    string.handleResourceTypes(myString) // ERROR\n" +
          "                               ~~~~~~~~\n" +
          "src/test/pkg/test.kt:55: Error: Expected resource of type dimen [ResourceType]\n" +
          "    string.handleResourceTypes(dimensions = myIcon) // ERROR\n" +
          "                                            ~~~~~~\n" +
          "src/test/pkg/test.kt:56: Error: Expected resource of type dimen [ResourceType]\n" +
          "    string.handleResourceTypes(myIcon, dimensions = myString) // ERROR\n" +
          "                                                    ~~~~~~~~\n" +
          "6 errors, 0 warnings\n"
      )
  }

  fun testTypes() {
    lint()
      .files(
        java(
          "package p1.p2;\n" +
            "import android.annotation.SuppressLint;\n" +
            "import android.annotation.TargetApi;\n" +
            "import android.app.Notification;\n" +
            "import android.content.Context;\n" +
            "import android.content.Intent;\n" +
            "import android.content.ServiceConnection;\n" +
            "import android.content.res.Resources;\n" +
            "import android.os.Build;\n" +
            "import androidx.annotation.*;\n" +
            "import android.view.View;\n" +
            "\n" +
            "import static android.content.Context.CONNECTIVITY_SERVICE;\n" +
            "\n" +
            "@SuppressWarnings(\"UnusedDeclaration\")\n" +
            "public class X {\n" +
            "    public void testResourceTypeParameters(Context context, int unknown) {\n" +
            "        Resources resources = context.getResources();\n" +
            "        String ok1 = resources.getString(R.string.app_name);\n" +
            "        String ok2 = resources.getString(unknown);\n" +
            "        String ok3 = resources.getString(android.R.string.ok);\n" +
            "        int ok4 = resources.getColor(android.R.color.black);\n" +
            "        if (testResourceTypeReturnValues(context, true) == R.drawable.ic_launcher) { // ok\n" +
            "        }\n" +
            "\n" +
            "        //String ok2 = resources.getString(R.string.app_name, 1, 2, 3);\n" +
            "        float error1 = resources.getDimension(/*Expected resource of type dimen*/R.string.app_name/**/);\n" +
            "        boolean error2 = resources.getBoolean(/*Expected resource of type bool*/R.string.app_name/**/);\n" +
            "        boolean error3 = resources.getBoolean(/*Expected resource of type bool*/android.R.drawable.btn_star/**/);\n" +
            "        if (testResourceTypeReturnValues(context, true) == /*Expected resource of type drawable*/R.string.app_name/**/) {\n" +
            "        }\n" +
            "        @SuppressWarnings(\"UnnecessaryLocalVariable\")\n" +
            "        int flow = R.string.app_name;\n" +
            "        @SuppressWarnings(\"UnnecessaryLocalVariable\")\n" +
            "        int flow2 = flow;\n" +
            "        boolean error4 = resources.getBoolean(/*Expected resource of type bool*/flow2/**/);\n" +
            "    }\n" +
            "\n" +
            "    @androidx.annotation.DrawableRes\n" +
            "    public int testResourceTypeReturnValues(Context context, boolean useString) {\n" +
            "        if (useString) {\n" +
            "            return /*Expected resource of type drawable*/R.string.app_name/**/; // error\n" +
            "        } else {\n" +
            "            return R.drawable.ic_launcher; // ok\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    @ColorInt\n" +
            "    public int testResourceTypeReturnValues(Context context, @ColorRes int colorId, boolean useColor) {\n" +
            "        if (useColor) {\n" +
            "            return /*Should pass resolved color instead of resource id here: getResources().getColor(colorId)*/colorId/**/;\n" +
            "        } else {\n" +
            "            return /*Should pass resolved color instead of resource id here: getResources().getColor(R.color.my_color)*/R.color.my_color/**/;\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    public static final class R {\n" +
            "        public static final class drawable {\n" +
            "            public static final int ic_launcher=0x7f020057;\n" +
            "        }\n" +
            "        public static final class string {\n" +
            "            public static final int app_name=0x7f0a000e;\n" +
            "        }\n" +
            "        public static final class color {\n" +
            "            public static final int my_color=0x7f0a300e;\n" +
            "        }\n" +
            "    }\n" +
            "}\n"
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectInlinedMessages()
  }

  fun testFlow() {

    lint()
      .files(
        java(
          "" +
            "package test.pkg;\n" +
            "import android.content.res.Resources;\n" +
            "import androidx.annotation.DrawableRes;\n" +
            "import androidx.annotation.StringRes;\n" +
            "import androidx.annotation.StyleRes;\n" +
            "\n" +
            "import java.util.Random;\n" +
            "\n" +
            "@SuppressWarnings(\"UnusedDeclaration\")\n" +
            "public class X {\n" +
            "    public void testLiterals(Resources resources) {\n" +
            "        resources.getDrawable(0); // OK\n" +
            "        resources.getDrawable(-1); // OK\n" +
            "        resources.getDrawable(/*Expected resource of type drawable*/10/**/); // ERROR\n" +
            "    }\n" +
            "\n" +
            "    public void testConstants(Resources resources) {\n" +
            "        resources.getDrawable(R.drawable.my_drawable); // OK\n" +
            "        resources.getDrawable(/*Expected resource of type drawable*/R.string.my_string/**/); // ERROR\n" +
            "    }\n" +
            "\n" +
            "    public void testFields(String fileExt, Resources resources) {\n" +
            "        int mimeIconId = MimeTypes.styleAndDrawable;\n" +
            "        resources.getDrawable(mimeIconId); // OK\n" +
            "\n" +
            "        int s1 = MimeTypes.style;\n" +
            "        resources.getDrawable(/*Expected resource of type drawable*/s1/**/); // ERROR\n" +
            "        int s2 = MimeTypes.styleAndDrawable;\n" +
            "        resources.getDrawable(s2); // OK\n" +
            "        int w3 = MimeTypes.drawable;\n" +
            "        resources.getDrawable(w3); // OK\n" +
            "\n" +
            "        // Direct reference\n" +
            "        resources.getDrawable(/*Expected resource of type drawable*/MimeTypes.style/**/); // ERROR\n" +
            "        resources.getDrawable(MimeTypes.styleAndDrawable); // OK\n" +
            "        resources.getDrawable(MimeTypes.drawable); // OK\n" +
            "    }\n" +
            "\n" +
            "    public void testCalls(String fileExt, Resources resources) {\n" +
            "        int mimeIconId = MimeTypes.getIconForExt(fileExt);\n" +
            "        resources.getDrawable(mimeIconId); // OK\n" +
            "        resources.getDrawable(MimeTypes.getInferredString()); // OK (wrong but can't infer type)\n" +
            "        resources.getDrawable(MimeTypes.getInferredDrawable()); // OK\n" +
            "        resources.getDrawable(/*Expected resource of type drawable*/MimeTypes.getAnnotatedString()/**/); // Error\n" +
            "        resources.getDrawable(MimeTypes.getAnnotatedDrawable()); // OK\n" +
            "        resources.getDrawable(MimeTypes.getUnknownType()); // OK (unknown/uncertain)\n" +
            "    }\n" +
            "\n" +
            "    private static class MimeTypes {\n" +
            "        @androidx.annotation.StyleRes\n" +
            "        @androidx.annotation.DrawableRes\n" +
            "        public static int styleAndDrawable;\n" +
            "\n" +
            "        @androidx.annotation.StyleRes\n" +
            "        public static int style;\n" +
            "\n" +
            "        @androidx.annotation.DrawableRes\n" +
            "        public static int drawable;\n" +
            "\n" +
            "        @androidx.annotation.DrawableRes\n" +
            "        public static int getIconForExt(String ext) {\n" +
            "            return R.drawable.my_drawable;\n" +
            "        }\n" +
            "\n" +
            "        public static int getInferredString() {\n" +
            "            // Implied string - can we handle this?\n" +
            "            return R.string.my_string;\n" +
            "        }\n" +
            "\n" +
            "        public static int getInferredDrawable() {\n" +
            "            // Implied drawable - can we handle this?\n" +
            "            return R.drawable.my_drawable;\n" +
            "        }\n" +
            "\n" +
            "        @androidx.annotation.StringRes\n" +
            "        public static int getAnnotatedString() {\n" +
            "            return R.string.my_string;\n" +
            "        }\n" +
            "\n" +
            "        @androidx.annotation.DrawableRes\n" +
            "        public static int getAnnotatedDrawable() {\n" +
            "            return R.drawable.my_drawable;\n" +
            "        }\n" +
            "\n" +
            "        public static int getUnknownType() {\n" +
            "            return new Random(1000).nextInt();\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    public static final class R {\n" +
            "        public static final class drawable {\n" +
            "            public static final int my_drawable =0x7f020057;\n" +
            "        }\n" +
            "        public static final class string {\n" +
            "            public static final int my_string =0x7f0a000e;\n" +
            "        }\n" +
            "    }\n" +
            "}\n"
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectInlinedMessages()
  }

  fun testMipmap() {
    lint()
      .files(
        java(
          "" +
            "package p1.p2;\n" +
            "\n" +
            "import android.app.Activity;\n" +
            "\n" +
            "public class X extends Activity {\n" +
            "  public void test() {\n" +
            "    Object o = getResources().getDrawable(R.mipmap.ic_launcher);\n" +
            "  }\n" +
            "\n" +
            "  public static final class R {\n" +
            "    public static final class drawable {\n" +
            "      public static int icon=0x7f020000;\n" +
            "    }\n" +
            "    public static final class mipmap {\n" +
            "      public static int ic_launcher=0x7f020001;\n" +
            "    }\n" +
            "  }\n" +
            "}"
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun testColorAndDrawable() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=197411

    lint()
      .files(
        java(
          "" +
            "\n" +
            "package test.pkg;\n" +
            "\n" +
            "import android.app.Activity;\n" +
            "import androidx.annotation.ColorRes;\n" +
            "import androidx.annotation.DrawableRes;\n" +
            "import android.widget.TextView;\n" +
            "\n" +
            "public class X extends Activity {\n" +
            "    @ColorRes int getSwipeColor() {\n" +
            "        return android.R.color.black;\n" +
            "    }\n" +
            "\n" +
            "    @DrawableRes int getDrawableIcon() {\n" +
            "        return android.R.drawable.ic_delete;\n" +
            "    }\n" +
            "    \n" +
            "    public void test(TextView view) {\n" +
            "        getResources().getColor(getSwipeColor()); // OK: color to color\n" +
            "        view.setBackgroundResource(getSwipeColor()); // OK: color promotes to drawable\n" +
            "        getResources().getColor(/*Expected resource of type color*/getDrawableIcon()/**/); // Not OK: drawable doesn't promote to color\n" +
            "    }\n" +
            "}\n"
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectInlinedMessages()
  }

  fun testObtainStyleablesFromArray() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=201882
    // obtainStyledAttributes normally expects a styleable but you can also supply a
    // custom int array

    lint()
      .files(
        java(
          "" +
            "package test.pkg;\n" +
            "\n" +
            "import android.app.Activity;\n" +
            "import android.content.Context;\n" +
            "import android.content.res.TypedArray;\n" +
            "import android.graphics.Color;\n" +
            "import android.util.AttributeSet;\n" +
            "\n" +
            "@SuppressWarnings(\"unused\")\n" +
            "public class X {\n" +
            "    public void test1(Activity activity, float[] foregroundHsv, float[] backgroundHsv) {\n" +
            "        TypedArray attributes = activity.obtainStyledAttributes(\n" +
            "                new int[] {\n" +
            "                        R.attr.setup_wizard_navbar_theme,\n" +
            "                        android.R.attr.colorForeground,\n" +
            "                        android.R.attr.colorBackground });\n" +
            "        Color.colorToHSV(attributes.getColor(1, 0), foregroundHsv);\n" +
            "        Color.colorToHSV(attributes.getColor(2, 0), backgroundHsv);\n" +
            "        attributes.recycle();\n" +
            "    }\n" +
            "\n" +
            "    public void test2(Context context, AttributeSet attrs, int defStyle) {\n" +
            "        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.BezelImageView,\n" +
            "                defStyle, 0);\n" +
            "        a.getDrawable(R.styleable.BezelImageView_maskDrawable);\n" +
            "        a.recycle();\n" +
            "    }\n" +
            "\n" +
            "    public static class R {\n" +
            "        public static class attr {\n" +
            "            public static final int setup_wizard_navbar_theme = 0x7f01003b;\n" +
            "        }\n" +
            "        public static class styleable {\n" +
            "            public static final int[] BezelImageView = {\n" +
            "                    0x7f01005d, 0x7f01005e, 0x7f01005f\n" +
            "            };\n" +
            "            public static final int BezelImageView_maskDrawable = 0;\n" +
            "        }\n" +
            "    }\n" +
            "}\n"
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun testSuppressNames() {
    lint()
      .files(
        java(
          "" +
            "package test.pkg;\n" +
            "\n" +
            "import androidx.annotation.BinderThread;\n" +
            "import androidx.annotation.CheckResult;\n" +
            "import androidx.annotation.ColorInt;\n" +
            "import androidx.annotation.ColorRes;\n" +
            "import androidx.annotation.FloatRange;\n" +
            "import androidx.annotation.IntDef;\n" +
            "import androidx.annotation.IntRange;\n" +
            "import androidx.annotation.RequiresPermission;\n" +
            "import androidx.annotation.Size;\n" +
            "import androidx.annotation.StringRes;\n" +
            "import androidx.annotation.UiThread;\n" +
            "\n" +
            "import java.lang.annotation.Retention;\n" +
            "import java.lang.annotation.RetentionPolicy;\n" +
            "\n" +
            "import static android.Manifest.permission.ACCESS_COARSE_LOCATION;\n" +
            "import static android.Manifest.permission.ACCESS_FINE_LOCATION;\n" +
            "\n" +
            "@SuppressWarnings(\"unused\")\n" +
            "public class X {\n" +
            "\n" +
            "    @ColorInt private int colorInt;\n" +
            "    @ColorRes private int colorRes;\n" +
            "    @StringRes private int stringRes;\n" +
            "\n" +
            "    @BinderThread\n" +
            "    public void testOk() {\n" +
            "        setColor(colorRes); // OK\n" +
            "        setColorInt(colorInt); // OK\n" +
            "        printBetween(5); // OK\n" +
            "        printBetweenFromInclusiveToInclusive(3.0f); // OK\n" +
            "        printMinMultiple(new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9 }); // OK\n" +
            "        int result = checkMe(); // OK\n" +
            "        setDuration(LENGTH_LONG); // OK\n" +
            "    }\n" +
            "\n" +
            "    @BinderThread\n" +
            "    public void testErrors() {\n" +
            "        setColor(/*Expected a color resource id (R.color.) but received an RGB integer*/colorInt/**/); // ERROR\n" +
            "        setColorInt(/*Should pass resolved color instead of resource id here: getResources().getColor(colorRes)*/colorRes/**/); // ERROR\n" +
            "        printBetween(/*Value must be ≥ 4 (was 1)*/1/**/); // ERROR\n" +
            "        printBetweenFromInclusiveToInclusive(/*Value must be ≥ 2.5 (was 1.0)*/1.0f/**/); // ERROR\n" +
            "        printMinMultiple(/*Expected size to be a multiple of 3 (was 8 and should be either 6 or 9)*/new int[] { 1, 2, 3, 4, 5, 6, 7, 8 }/**/); // ERROR\n" +
            "        /*The result of checkMe is not used*/checkMe()/**/; // ERROR\n" +
            "        /*Missing permissions required by X.requiresPermission: android.permission.ACCESS_FINE_LOCATION or android.permission.ACCESS_COARSE_LOCATION*/requiresPermission()/**/; // ERROR\n" +
            "        /*Method requiresUiThread must be called from the UI thread, currently inferred thread is binder thread*/requiresUiThread()/**/; // ERROR\n" +
            "        setDuration(/*Must be one of: X.LENGTH_INDEFINITE, X.LENGTH_SHORT, X.LENGTH_LONG*/5/**/); // ERROR\n" +
            "    }\n" +
            "\n" +
            "    @BinderThread\n" +
            "    public void testSuppressedViaComment() {\n" +
            "        //noinspection ResourceType\n" +
            "        setColor(colorInt); // ERROR\n" +
            "        //noinspection ResourceAsColor\n" +
            "        setColorInt(colorRes); // ERROR\n" +
            "        //noinspection Range\n" +
            "        printBetween(1); // ERROR\n" +
            "        //noinspection Range\n" +
            "        printBetweenFromInclusiveToInclusive(1.0f); // ERROR\n" +
            "        //noinspection Range\n" +
            "        printMinMultiple(new int[] { 1, 2, 3, 4, 5, 6, 7, 8 }); // ERROR\n" +
            "        //noinspection CheckResult\n" +
            "        checkMe(); // ERROR\n" +
            "        //noinspection MissingPermission\n" +
            "        requiresPermission(); // ERROR\n" +
            "        //noinspection WrongThread\n" +
            "        requiresUiThread(); // ERROR\n" +
            "        //noinspection WrongConstant\n" +
            "        setDuration(5); // ERROR\n" +
            "    }\n" +
            "\n" +
            "    @BinderThread\n" +
            "    public void testSuppressedViaOldInspectionName() {\n" +
            "        //noinspection ResourceType\n" +
            "        setColor(colorInt); // SUPPRESSED\n" +
            "        //noinspection ResourceType\n" +
            "        setColorInt(colorRes); // SUPPRESSED\n" +
            "        //noinspection ResourceType\n" +
            "        printBetween(1); // SUPPRESSED\n" +
            "        //noinspection ResourceType\n" +
            "        printBetweenFromInclusiveToInclusive(1.0f); // SUPPRESSED\n" +
            "        //noinspection ResourceType\n" +
            "        printMinMultiple(new int[] { 1, 2, 3, 4, 5, 6, 7, 8 }); // SUPPRESSED\n" +
            "        //noinspection ResourceType\n" +
            "        checkMe(); // SUPPRESSED\n" +
            "        //noinspection ResourceType\n" +
            "        requiresUiThread(); // SUPPRESSED\n" +
            "        //noinspection ResourceType\n" +
            "        setDuration(5); // SUPPRESSED\n" +
            "    }\n" +
            "\n" +
            "    @SuppressWarnings({\"ResourceAsColor\", \"Range\", \"CheckResult\", \"MissingPermission\", \"WrongThread\", \"WrongConstant\"})\n" +
            "    @BinderThread\n" +
            "    public void testSuppressedViaAnnotation() {\n" +
            "        setColorInt(colorRes); // SUPPRESSED\n" +
            "        printBetween(1); // SUPPRESSED\n" +
            "        printBetweenFromInclusiveToInclusive(1.0f); // SUPPRESSED\n" +
            "        printMinMultiple(new int[] { 1, 2, 3, 4, 5, 6, 7, 8 }); // SUPPRESSED\n" +
            "        checkMe(); // SUPPRESSED\n" +
            "        requiresPermission(); // SUPPRESSED\n" +
            "        requiresUiThread(); // SUPPRESSED\n" +
            "        setDuration(5); // SUPPRESSED\n" +
            "    }\n" +
            "\n" +
            "    @SuppressWarnings(\"ResourceType\")\n" +
            "    @BinderThread\n" +
            "    public void testSuppressedViaOldAnnotation() {\n" +
            "        setColorInt(colorRes); // SUPPRESSED\n" +
            "        printBetween(1); // SUPPRESSED\n" +
            "        printBetweenFromInclusiveToInclusive(1.0f); // SUPPRESSED\n" +
            "        printMinMultiple(new int[] { 1, 2, 3, 4, 5, 6, 7, 8 }); // SUPPRESSED\n" +
            "        checkMe(); // SUPPRESSED\n" +
            "        requiresUiThread(); // SUPPRESSED\n" +
            "        setDuration(5); // SUPPRESSED\n" +
            "    }\n" +
            "\n" +
            "\n" +
            "    private void setColor(@ColorRes int color) { }\n" +
            "    private void setColorInt(@ColorInt int color) { }\n" +
            "    public void printBetween(@IntRange(from=4,to=7) int arg) { }\n" +
            "    public void printMinMultiple(@Size(min=4,multiple=3) int[] arg) { }\n" +
            "    public void printBetweenFromInclusiveToInclusive(@FloatRange(from=2.5,to=5.0) float arg) { }\n" +
            "    @CheckResult\n" +
            "    public int checkMe() { return 0; }\n" +
            "\n" +
            "    @RequiresPermission(anyOf = {ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION})\n" +
            "    public void requiresPermission() { }\n" +
            "\n" +
            "    @UiThread\n" +
            "    public void requiresUiThread() { }\n" +
            "\n" +
            "    @IntDef({LENGTH_INDEFINITE, LENGTH_SHORT, LENGTH_LONG})\n" +
            "    @Retention(RetentionPolicy.SOURCE)\n" +
            "    public @interface Duration {}\n" +
            "\n" +
            "    public static final int LENGTH_INDEFINITE = -2;\n" +
            "    public static final int LENGTH_SHORT = -1;\n" +
            "    public static final int LENGTH_LONG = 0;\n" +
            "    public void setDuration(@Duration int duration) {\n" +
            "    }\n" +
            "}\n"
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .issues(
        ResourceTypeDetector.RESOURCE_TYPE,
        ResourceTypeDetector.COLOR_USAGE,
        RangeDetector.RANGE,
        CheckResultDetector.CHECK_RESULT,
        PermissionDetector.MISSING_PERMISSION,
        ThreadDetector.THREAD,
        TypedefDetector.TYPE_DEF,
      )
      .run()
      .expectInlinedMessages()
  }

  fun testNavigationRes() {
    val expected =
      "" +
        "src/test/pkg/Flow.java:16: Error: Expected resource of type navigation [ResourceType]\n" +
        "        nav(string); // ERROR\n" +
        "            ~~~~~~\n" +
        "src/test/pkg/Flow.java:17: Error: Expected resource of type navigation [ResourceType]\n" +
        "        nav(R.string.my_string); // ERROR\n" +
        "            ~~~~~~~~~~~~~~~~~~\n" +
        "src/test/pkg/Flow.java:18: Error: Expected resource of type string [ResourceType]\n" +
        "        str(R.navigation.my_navigation); // ERROR\n" +
        "            ~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "3 errors, 0 warnings\n"
    lint()
      .files(
        java(
          "" +
            "package test.pkg;\n" +
            "import android.content.res.Resources;\n" +
            "import androidx.annotation.NavigationRes;\n" +
            "import androidx.annotation.StringRes;\n" +
            "import androidx.annotation.StyleRes;\n" +
            "\n" +
            "import java.util.Random;\n" +
            "\n" +
            "@SuppressWarnings(\"UnusedDeclaration\")\n" +
            "public class Flow {\n" +
            // Fully qualified name because @NavigationRes is not yet
            // in the support library bundled with this test
            "    public void nav(@androidx.annotation.NavigationRes int id) { }\n" +
            "    public void str(@StringRes int id) { }\n" +
            "    public void test(@StringRes int string, @androidx.annotation.NavigationRes int navigation) {\n" +
            "        nav(navigation); // OK\n" +
            "        str(string); // OK\n" +
            "        nav(string); // ERROR\n" +
            "        nav(R.string.my_string); // ERROR\n" +
            "        str(R.navigation.my_navigation); // ERROR\n" +
            "    }\n" +
            "}"
        ),
        java(
          "" +
            "package test.pkg;\n" +
            "public final class R {\n" +
            "    public static final class navigation {\n" +
            "        public static final int my_navigation = 0x7f020057;\n" +
            "    }\n" +
            "    public static final class string {\n" +
            "        public static final int my_string =0x7f0a000e;\n" +
            "    }\n" +
            "}\n"
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(expected)
  }

  fun testKotlinImplicitReturn() {
    lint()
      .files(
        kotlin(
            """
                    package test.pkg

                    import androidx.annotation.StringRes

                    @StringRes
                    fun getResource(): Int = android.R.drawable.ic_delete
                    """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        "" +
          "src/test/pkg/test.kt:6: Error: Expected resource of type string [ResourceType]\n" +
          "fun getResource(): Int = android.R.drawable.ic_delete\n" +
          "                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
          "1 errors, 0 warnings"
      )
  }

  fun testStyleable() {
    // R.styleable is not like the other resource types
    lint()
      .files(
        java(
            """
                    package test.pkg;

                    import androidx.annotation.StyleableRes;

                    @SuppressWarnings("ClassNameDiffersFromFileName")
                    public class TestStyleable {
                        @StyleableRes
                        public static final int MY_STYLE = 1;

                        public void process(@StyleableRes int arg) {
                        }

                        public void process(@StyleableRes int[] arg) {
                        }

                        public void test() {
                            process(R.string.my_string); // ERROR
                            process(R.styleable.MyStyleable); // OK
                            process(R.styleable.MyStyleable_fontProviderCerts); // OK
                        }
                    }
                """
          )
          .indented(),
        java(
            """
                    package test.pkg;

                    @SuppressWarnings("ClassNameDiffersFromFileName")
                    public final class R {
                        public static final class string {
                            public static final int my_string = 0x7f0b0021;
                        }
                        public static final class style {
                            public static final int MyStyle = 0x7f0c00fa;
                            public static final int MyStyle_Info = 0x7f0c00fb;
                        }
                        public static final class styleable {
                            public static final int[] MyStyleable = { 0x7f020072, 0x7f020073, 0x7f020074, 0x7f020075, 0x7f020076, 0x7f020077 };
                            public static final int MyStyleable_fontProviderAuthority = 0;
                            public static final int MyStyleable_fontProviderCerts = 1;
                            public static final int MyStyleable_fontProviderFetchStrategy = 2;
                            public static final int MyStyleable_fontProviderFetchTimeout = 3;
                        }
                    }

                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        "" +
          "src/test/pkg/TestStyleable.java:8: Error: Expected resource of type styleable [ResourceType]\n" +
          "    public static final int MY_STYLE = 1;\n" +
          "                                       ~\n" +
          "src/test/pkg/TestStyleable.java:17: Error: Expected resource of type styleable [ResourceType]\n" +
          "        process(R.string.my_string); // ERROR\n" +
          "                ~~~~~~~~~~~~~~~~~~\n" +
          "2 errors, 0 warnings"
      )
  }

  fun testKotlinExtensionsFromJava() {
    // Regression test for
    // 78283842: False positive lint errors when calling Kotlin extension method from Java
    //    with Android resource annotations on parameters
    lint()
      .files(
        rClass("test.pkg", "@drawable/some_img", "@string/some_string"),
        java(
            """
                package test.pkg;

                import android.app.Activity;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class ExtensionUsageFromJava {
                    public void test(Activity activity) {
                        ExtensionsKt.foobar(this, R.string.some_string, R.drawable.some_img);
                    }
                }
                    """
          )
          .indented(),
        kotlin(
            "src/test/pkg/Extensions.kt",
            """
                    package test.pkg

                    import android.content.Context
                    import androidx.annotation.DrawableRes
                    import androidx.annotation.StringRes

                    fun Context.foobar(@StringRes msgId: Int, @DrawableRes imgId: Int) {  }
                """,
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun test127955232() {
    // Regression test for https://issuetracker.google.com/127955232
    // 127955232: Unexpected failure during lint analysis - NullPointerException
    lint()
      .files(
        kotlin(
            """
                package com.example.myapplication

                import android.content.Context

                data class Test(val context: Context,
                                val testInt: Int,
                                val testString: String = context.getString(if (testInt == 0) R.string.test_string_1 else R.string.test_string_2))
                """
          )
          .indented(),
        java(
            """
                package test.pkg;

                public final class R {
                    public static final class string {
                        public static final int test_string_1 = 0x7f0a000e;
                        public static final int test_string_2 = 0x7f020057;
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

  fun testAnnotationArrays() {
    // Regression test for
    // https://issuetracker.google.com/37065470
    lint()
      .files(
        java(
            """
                package test.pkg;
                import androidx.annotation.IdRes;
                public @interface MySweetAnnotation {
                    @IdRes
                    int[] ids() default {};
                }
                """
          )
          .indented(),
        java(
            """
                package test.pkg;
                public class AnnotationUsageJava {
                    @MySweetAnnotation() // OK
                    public void test1() {
                    }
                    @MySweetAnnotation(ids = R.string.app_name) // ERROR
                    public void test2() {
                    }
                    @MySweetAnnotation(ids = { R.string.app_name }) // ERROR
                    public void test3() {
                    }
                    @MySweetAnnotation(ids = { R.id.left, R.string.app_name }) // OK - has id
                    public void test4() {
                    }
                    @MySweetAnnotation(ids = { R.string.app_name, R.id.left }) // OK
                    public void test5() {
                    }
                    @MySweetAnnotation(ids = { R.string.app_name, R.drawable.my_drawable }) // ERROR
                    public void test6() {
                    }
                }
                """
          )
          .indented(),
        kotlin(
            """
                package test.pkg
                class AnnotationUsageKotlin {
                    @MySweetAnnotation // OK
                    fun test1() {
                    }
                    @MySweetAnnotation(ids = [R.string.app_name]) // ERROR
                    fun test3() {
                    }
                    @MySweetAnnotation(ids = [R.id.left, R.string.app_name]) // OK - has id
                    fun test4() {
                    }
                    @MySweetAnnotation(ids = [R.string.app_name, R.id.left]) // OK
                    fun test5() {
                    }
                    @MySweetAnnotation(ids = [R.string.app_name, R.drawable.my_drawable]) // ERROR
                    fun test6() {
                    }
                }
                """
          )
          .indented(),
        java(
            """
                    package test.pkg;
                    @SuppressWarnings("ClassNameDiffersFromFileName")
                    public final class R {
                        public static final class string {
                            public static final int app_name = 0x7f0b0021;
                        }
                        public static final class id {
                            public static final int left = 0x7f0c00fa;
                        }
                        public static final class drawable {
                            public static final int my_drawable = 0x7f0c00fb;
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
            src/test/pkg/AnnotationUsageJava.java:6: Error: Expected resource of type id [ResourceType]
                @MySweetAnnotation(ids = R.string.app_name) // ERROR
                                         ~~~~~~~~~~~~~~~~~
            src/test/pkg/AnnotationUsageJava.java:9: Error: Expected resource of type id [ResourceType]
                @MySweetAnnotation(ids = { R.string.app_name }) // ERROR
                                         ~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/AnnotationUsageJava.java:18: Error: Expected resource of type id [ResourceType]
                @MySweetAnnotation(ids = { R.string.app_name, R.drawable.my_drawable }) // ERROR
                                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/AnnotationUsageKotlin.kt:6: Error: Expected resource of type id [ResourceType]
                @MySweetAnnotation(ids = [R.string.app_name]) // ERROR
                                         ~~~~~~~~~~~~~~~~~~~
            src/test/pkg/AnnotationUsageKotlin.kt:15: Error: Expected resource of type id [ResourceType]
                @MySweetAnnotation(ids = [R.string.app_name, R.drawable.my_drawable]) // ERROR
                                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            5 errors, 0 warnings
            """
      )
  }

  fun testGenerated() {
    // Regression test for
    // https://issuetracker.google.com/140699627
    lint()
      .files(
        kotlin(
            "generated/java/test/pkg/TestFragmentArgs.kt",
            """
                package test.pkg
                data class TestFragmentArgs(val myenum: MYENUM)
                """,
          )
          .indented(),
        kotlin(
            """
                package test.pkg
                import androidx.annotation.StringRes
                enum class MYENUM(@StringRes val title: Int) {
                    FIRST(R.string.first_title)
                }
                """
          )
          .indented(),
        kotlin(
            """
                package test.pkg
                import android.app.Fragment
                class TestFragment : Fragment() {
                }
                """
          )
          .indented(),
        kotlin(
            """
                package test.pkg
                import android.os.Bundle
                import kotlinx.android.synthetic.main.activity_main.*
                class MainActivity : android.app.Activity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        setContentView(R.layout.activity_main)
                        val test = TestFragmentArgs(MYENUM.FIRST).myenum
                        test.apply {
                            textView.setText(title)
                            textView.text = getString(MYENUM.FIRST.title)
                            textView.text = getString(title)
                        }
                        getString(test.title)
                        test.apply {
                            getString(title)
                        }
                    }
                }
                """
          )
          .indented(),
        java(
            """
                    package test.pkg;
                    @SuppressWarnings("ClassNameDiffersFromFileName")
                    public final class R {
                        public static final class layout {
                            public static final int activity_main = 0x7f0b0021;
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

  fun testAosp() {
    lint()
      .files(
        kotlin(
            """
                import android.annotation.DrawableRes
                import android.annotation.StringRes

                fun setLabels(@StringRes titleId: Int) {
                    // ...
                }

                fun setIcon(@DrawableRes iconId: Int, @StringRes descId: Int) {
                    setLabels(iconId) // bug: should have passed descId. Both are ints but of wrong type.
                }
                """
          )
          .indented(),
        java(
            """
                package android.annotation;
                import java.lang.annotation.*;
                import static java.lang.annotation.ElementType.METHOD;
                import static java.lang.annotation.RetentionPolicy.SOURCE;
                @Retention(SOURCE) @Target({METHOD})
                public @interface StringRes {
                }
                """
          )
          .indented(),
        java(
            """
                package android.annotation;
                import java.lang.annotation.*;
                import static java.lang.annotation.ElementType.METHOD;
                import static java.lang.annotation.RetentionPolicy.SOURCE;
                @Retention(SOURCE) @Target({METHOD})
                public @interface DrawableRes {
                }
                """
          )
          .indented(),
      )
      .run()
      .expect(
        """
            src/test.kt:9: Error: Expected resource of type string [ResourceType]
                setLabels(iconId) // bug: should have passed descId. Both are ints but of wrong type.
                          ~~~~~~
            1 errors, 0 warnings
            """
      )
  }

  fun testArrayAccessOverloading() {
    // Regression test for https://issuetracker.google.com/173628041
    lint()
      .files(
        kotlin(
            """
                import androidx.annotation.ColorRes
                import androidx.annotation.StringRes

                class X {
                    operator fun get(@ColorRes id: Int) = 0
                }

                fun test(@ColorRes myColor: Int, @StringRes myString: Int) {
                    val x = X()

                    // Intended type: color
                    x.get(myColor) // OK 1
                    x[myColor] // OK 2

                    // Wrong type: string
                    x.get(myString) // ERROR 1
                    x[myString] // ERROR 2
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
            src/X.kt:16: Error: Expected resource of type color [ResourceType]
                x.get(myString) // ERROR 1
                      ~~~~~~~~
            src/X.kt:17: Error: Expected resource of type color [ResourceType]
                x[myString] // ERROR 2
                  ~~~~~~~~
            2 errors, 0 warnings
            """
      )
  }

  fun testOperatorOverloading() {
    lint()
      .files(
        kotlin(
            """
                import androidx.annotation.ColorRes
                import androidx.annotation.StringRes

                class Resource {
                    operator fun contains(@ColorRes id: Int): Boolean = false
                    operator fun times(@ColorRes id: Int): Int = 0
                    operator fun rangeTo(@ColorRes id: Int): Int = 0
                }

                fun testBinary(resource: Resource, @ColorRes color: Int, @StringRes string: Int) {
                    println(color in resource) // OK 1
                    println(string in resource) // ERROR 1
                    println(string !in resource) // ERROR 2
                    println(resource * color) // OK 2
                    println(resource * string) // ERROR 3
                    println(resource..color) // OK 3
                    println(resource..string) // ERROR 4
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
            src/Resource.kt:12: Error: Expected resource of type color [ResourceType]
                println(string in resource) // ERROR 1
                        ~~~~~~
            src/Resource.kt:13: Error: Expected resource of type color [ResourceType]
                println(string !in resource) // ERROR 2
                        ~~~~~~
            src/Resource.kt:15: Error: Expected resource of type color [ResourceType]
                println(resource * string) // ERROR 3
                                   ~~~~~~
            src/Resource.kt:17: Error: Expected resource of type color [ResourceType]
                println(resource..string) // ERROR 4
                                  ~~~~~~
            4 errors, 0 warnings
            """
      )
  }

  fun testArrayOperatorResolution() {
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import androidx.annotation.ColorRes
                import androidx.annotation.StringRes

                class Test {
                    operator fun get(@StringRes key: Int) {}
                    operator fun get(key: String) {}
                    operator fun get(@StringRes key: Int, @ColorRes key2: Int) {}
                    operator fun get(@StringRes key: Int, key2: A) {}
                    operator fun get(@StringRes key: Int, key2: List<CharSequence>) {}

                    operator fun set(@StringRes key: Int, @ColorRes value: Int) {}
                    operator fun set(@StringRes key: Int, value: String) {}
                    operator fun set(@StringRes key: Int, @ColorRes color: Int, value: String) {}
                }

                open class A
                class B : A()

                fun test(test: Test, @StringRes string: Int, @ColorRes color: Int) {
                    // Getters
                    test[string] // OK 1
                    test[color] // ERROR 1
                    test[string, color] // OK 2
                    test[color, string] // ERROR 2 and 3

                    // Setters
                    test[string] = color // OK 3
                    test[string] = "string" // OK 4
                    test[string] = string // ERROR 3
                    test[string, color] = "test" // OK 5
                    test[color, string] = "test" // ERROR 4 and 5

                    // Subclass and wildcard matching
                    val b = B()
                    test[string, b] // OK 6
                    test[color, b] // ERROR 6

                    val sb = listOf<StringBuilder>()
                    test[string, sb] // OK 7
                    test[color, sb] // ERROR 7

                    test[string]
                    // Other
                    val calendar = java.util.GregorianCalendar()
                    calendar[2016, 2, 4, 18, 52] = 58 // OK 7
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
            src/test/pkg/Test.kt:24: Error: Expected resource of type string [ResourceType]
                test[color] // ERROR 1
                     ~~~~~
            src/test/pkg/Test.kt:26: Error: Expected resource of type color [ResourceType]
                test[color, string] // ERROR 2 and 3
                            ~~~~~~
            src/test/pkg/Test.kt:26: Error: Expected resource of type string [ResourceType]
                test[color, string] // ERROR 2 and 3
                     ~~~~~
            src/test/pkg/Test.kt:31: Error: Expected resource of type color [ResourceType]
                test[string] = string // ERROR 3
                               ~~~~~~
            src/test/pkg/Test.kt:33: Error: Expected resource of type color [ResourceType]
                test[color, string] = "test" // ERROR 4 and 5
                            ~~~~~~
            src/test/pkg/Test.kt:33: Error: Expected resource of type string [ResourceType]
                test[color, string] = "test" // ERROR 4 and 5
                     ~~~~~
            src/test/pkg/Test.kt:38: Error: Expected resource of type string [ResourceType]
                test[color, b] // ERROR 6
                     ~~~~~
            src/test/pkg/Test.kt:42: Error: Expected resource of type string [ResourceType]
                test[color, sb] // ERROR 7
                     ~~~~~
            8 errors, 0 warnings
            """
      )
  }

  fun testAnnotationReceiver() {
    // Regression test for b/195014464
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import androidx.annotation.ColorRes
                import androidx.annotation.DrawableRes
                import androidx.annotation.StringRes

                fun @receiver:StringRes Int.colorize1(o: Int) { }
                infix fun @receiver:StringRes Int.colorize2(o: Int) { }

                fun testExtensionFunction1(@DrawableRes panel: Int, c: Int) {
                    panel.colorize1(c) // ERROR 1
                }
                fun testExtensionFunction2(@DrawableRes panel: Int, c: Int) {
                    panel colorize2 c // ERROR 2
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
            src/test/pkg/test.kt:11: Error: Expected resource of type string [ResourceType]
                panel.colorize1(c) // ERROR 1
                ~~~~~
            src/test/pkg/test.kt:14: Error: Expected resource of type string [ResourceType]
                panel colorize2 c // ERROR 2
                ~~~~~
            2 errors, 0 warnings
            """
      )
  }

  fun testCornerCase() {
    // This test case was broken for parenthesis mode until the corresponding fix went in
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Context;

                import androidx.annotation.DrawableRes;

                public abstract class ResourceTypes {
                    public void testResourceTypeParameters(Context context) {
                        if (testResourceTypeReturnValues(context, true) == R.string.app_name) {
                        }
                    }

                    @DrawableRes abstract int testResourceTypeReturnValues(Context context, boolean useString);
                }
                """
          )
          .indented(),
        rClass("test.pkg", "@string/app_name"),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
            src/test/pkg/ResourceTypes.java:9: Error: Expected resource of type drawable [ResourceType]
                    if (testResourceTypeReturnValues(context, true) == R.string.app_name) {
                                                                       ~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
      )
  }

  fun testVarAnnotations() {
    // Regression test for https://issuetracker.google.com/200048369
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import androidx.annotation.ColorRes
                import androidx.annotation.StringRes

                class AnnotationTest(@ColorRes var color0: Int = android.R.color.background_dark) {
                    fun test() {
                        checkString(color0) // ERROR 1
                        checkString(color1) // ERROR 2
                        checkString(color2) // ERROR 3
                        checkString(color3) // ERROR 4
                        checkString(string) // OK 1
                    }

                    companion object {
                        fun test(@StringRes string: Int) {
                            val test = AnnotationTest(string) // ERROR 5
                            test.color0 = string // ERROR 6
                            test.color2 = string // ERROR 7
                            test.string = string // OK 2
                        }
                    }

                    @ColorRes
                    val color1: Int = android.R.color.background_dark
                    @ColorRes
                    var color2: Int = android.R.color.background_light
                    @get:ColorRes
                    var color3: Int = android.R.color.transparent
                    @StringRes
                    var string: Int = android.R.string.ok

                    private fun checkString(@StringRes string: Int) { }
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expect(
        """
            src/test/pkg/AnnotationTest.kt:8: Error: Expected resource of type string [ResourceType]
                    checkString(color0) // ERROR 1
                                ~~~~~~
            src/test/pkg/AnnotationTest.kt:9: Error: Expected resource of type string [ResourceType]
                    checkString(color1) // ERROR 2
                                ~~~~~~
            src/test/pkg/AnnotationTest.kt:10: Error: Expected resource of type string [ResourceType]
                    checkString(color2) // ERROR 3
                                ~~~~~~
            src/test/pkg/AnnotationTest.kt:11: Error: Expected resource of type string [ResourceType]
                    checkString(color3) // ERROR 4
                                ~~~~~~
            src/test/pkg/AnnotationTest.kt:17: Error: Expected resource of type color [ResourceType]
                        val test = AnnotationTest(string) // ERROR 5
                                                  ~~~~~~
            src/test/pkg/AnnotationTest.kt:18: Error: Expected resource of type color [ResourceType]
                        test.color0 = string // ERROR 6
                                      ~~~~~~
            src/test/pkg/AnnotationTest.kt:18: Error: Expected resource of type string [ResourceType]
                        test.color0 = string // ERROR 6
                        ~~~~~~~~~~~
            src/test/pkg/AnnotationTest.kt:19: Error: Expected resource of type color [ResourceType]
                        test.color2 = string // ERROR 7
                                      ~~~~~~
            src/test/pkg/AnnotationTest.kt:19: Error: Expected resource of type string [ResourceType]
                        test.color2 = string // ERROR 7
                        ~~~~~~~~~~~
            9 errors, 0 warnings
            """
      )
  }

  fun testVarAnnotations2() {
    lint()
      .files(
        java(
            """
                package test.pkg;
                import test.api.Simple;
                import androidx.annotation.DrawableRes;

                public class JavaTest {
                    public void test(@DrawableRes int drawable, Simple simple) {
                        simple.setFoo(drawable);                 // WARN 1
                        simple.setBar(drawable);                 // WARN 2
                        Simple simple2 = new Simple(drawable);   // WARN 3
                        if (simple.getFoo() == drawable) {       // TODO: WARN 4
                            System.out.println("test");
                        }
                    }
                }
                """
          )
          .indented(),
        kotlin(
            """
                package test.api

                import androidx.annotation.StringRes

                class Simple(
                    @StringRes
                    var foo: Int = 0
                ) {
                    @StringRes
                    var bar: Int = 0
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
            src/test/pkg/JavaTest.java:7: Error: Expected resource of type string [ResourceType]
                    simple.setFoo(drawable);                 // WARN 1
                                  ~~~~~~~~
            src/test/pkg/JavaTest.java:8: Error: Expected resource of type string [ResourceType]
                    simple.setBar(drawable);                 // WARN 2
                                  ~~~~~~~~
            src/test/pkg/JavaTest.java:9: Error: Expected resource of type string [ResourceType]
                    Simple simple2 = new Simple(drawable);   // WARN 3
                                                ~~~~~~~~
            3 errors, 0 warnings
            """
      )
  }

  fun testDimensions216139975() {
    // Regression test for issue 216139975
    lint()
      .files(
        java(
            """
                package test.pkg;
                import android.content.Context;
                import androidx.annotation.Dimension;

                public class Test {
                    static void calculateTabViewContentBounds(Context tabView, @Dimension(unit = Dimension.DP) int minWidth) {
                        int minWidthPx = (int) ViewUtils.dpToPx(tabView, minWidth);
                    }
                }
                """
          )
          .indented(),
        java(
            """
                package test.pkg;
                import android.content.Context;
                import android.content.res.Resources;
                import android.util.TypedValue;

                class ViewUtils {
                    public static float dpToPx(Context context,
                          // Note that we don't import Dimension properly, so we should get a resolve error here
                          // to deliberately test that we don't fall back to a @Px assumption
                          @androidx.annotation.Dimension(unit = androidx.annotation.Dimension.UNKNOWN) int dp) {
                        Resources r = context.getResources();
                        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, r.getDisplayMetrics());
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

  @Test
  fun testBinaryDimension() {
    // Regression test for https://issuetracker.google.com/231344506
    lint()
      .files(
        java(
            """
                package test.pkg;

                import androidx.annotation.Dimension;

                import test.pkg2.Api;

                public class Test {
                    public void test(@Dimension(unit = Dimension.SP) int textSize, @Dimension(unit = Dimension.DP) int dp, @Dimension(unit = Dimension.PX) int w) {
                        Api api = new Api();
                        api.api(textSize, dp, w); // OK
                        api.api(w,        // ERROR 1
                                textSize, // ERROR 2
                                dp);      // ERROR 3

                    }
                }
                """
          )
          .indented(),
        bytecode(
          "libs/api.jar",
          java(
              """
                    package test.pkg2;

                    import androidx.annotation.Dimension;

                    public class Api {
                        public void api(@Dimension(unit = Dimension.SP) int textSize,
                                        @Dimension(unit = Dimension.DP) int dp,
                                        @Dimension(unit = Dimension.PX) int w) {
                        }
                    }
                    """
            )
            .indented(),
          0x37117cd1,
          """
                test/pkg2/Api.class:
                H4sIAAAAAAAAAF1OTUvDQBB906RNG1tbvw4eRBQP1UMXvHhQhKAIgaKi4n3T
                LGVrswnJpvi3PAke/AH+KHG2ggUP8+brvXnz9f3xCeAMOyE8bAbYCrBNaF1o
                o+0lwRsePxP8qzxVhP5YG3VbZ4kqn2Qy54knC83sYRzHjnf0UBurMxWbha40
                M+5lKTNlVRkZk1tpdW4qwv5YmrTMdfoq5N9cXLPQVFyds2HN9h4/1nAAB0QI
                H/O6nKgb7azbUaFHM7mQXfhoEnpWVVYUL9NTEbmnBm4n5tJMxV0yUxOLAzSW
                l/gqyIkYW9ztcSbOzZN30JuzQsDY+h0yttFhiaPucviO8Z/WQch46HGzxtGN
                0VuV66uyz2mw/GDjBxc+8B57AQAA
                """,
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
            src/test/pkg/Test.java:11: Error: Mismatched @Dimension units here; expected a scale-independent (sp) integer but received a pixel integer [ResourceType]
                    api.api(w,        // ERROR 1
                            ~
            src/test/pkg/Test.java:12: Error: Mismatched @Dimension units here; expected density-independent (dp) integer but received a scale-independent (sp) integer [ResourceType]
                            textSize, // ERROR 2
                            ~~~~~~~~
            src/test/pkg/Test.java:13: Error: Mismatched @Dimension units here; expected a pixel integer but received density-independent (dp) integer [ResourceType]
                            dp);      // ERROR 3
                            ~~
            3 errors, 0 warnings
            """
      )
  }

  fun testComposeDimensions() {
    // Regression test for b/329691637
    lint()
      .files(
        kotlin(
            """
            package test.pkg

            import androidx.annotation.Dimension
            import androidx.annotation.Px
            import androidx.compose.ui.unit.Dp
            import androidx.compose.ui.unit.TextUnit
            import androidx.compose.ui.unit.dp
            import androidx.compose.ui.unit.sp

            @Px
            private fun getSizePx(): Int {
                return 0
            }

            @Dimension(unit = Dimension.DP)
            private fun getSizeDp(): Int {
                return 0
            }

            @Dimension(unit = Dimension.SP)
            private fun getSizeSp(): Int {
                return 0
            }

            fun print(dp: Dp) {
            }

            fun print(sp: TextUnit) {
            }

            fun test(@Px px: Int, @Dimension(unit = Dimension.SP) sp: Int) {
                print(getSizeDp().dp) // OK 1
                print(getSizePx().dp) // ERROR 1
                print(getSizeSp().dp) // ERROR 2
                print(getSizeDp().sp) // ERROR 3
                print(getSizeSp().sp) // OK 2

                print(px.dp) // ERROR 4
                print(sp.dp) // ERROR 5
                print(sp.sp) // OK 3
                // What about `.em` ? We don't have a @Dimension unit for that. Should we create one?
            }
            """
          )
          .indented(),
        kotlin(
            """
            // Compose Stubs
            package androidx.compose.ui.unit

            @kotlin.jvm.JvmInline
            value class Dp(val value: Float)

            @kotlin.jvm.JvmInline
            value class TextUnit internal constructor(internal val packedValue: Long)

            inline val Int.dp: Dp get() = Dp(value = this.toFloat())
            inline val Double.dp: Dp get() = Dp(value = this.toFloat())
            inline val Float.dp: Dp get() = Dp(value = this)
            inline val Int.sp: TextUnit get() = TextUnit(this.toLong())
            """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
        src/test/pkg/test.kt:33: Error: Mismatched @Dimension units here; expected density-independent (dp) integer but received a pixel integer [ResourceType]
            print(getSizePx().dp) // ERROR 1
                  ~~~~~~~~~~~
        src/test/pkg/test.kt:34: Error: Mismatched @Dimension units here; expected density-independent (dp) integer but received a scale-independent (sp) integer [ResourceType]
            print(getSizeSp().dp) // ERROR 2
                  ~~~~~~~~~~~
        src/test/pkg/test.kt:35: Error: Mismatched @Dimension units here; expected a scale-independent (sp) integer but received density-independent (dp) integer [ResourceType]
            print(getSizeDp().sp) // ERROR 3
                  ~~~~~~~~~~~
        src/test/pkg/test.kt:38: Error: Mismatched @Dimension units here; expected density-independent (dp) integer but received a pixel integer [ResourceType]
            print(px.dp) // ERROR 4
                  ~~
        src/test/pkg/test.kt:39: Error: Mismatched @Dimension units here; expected density-independent (dp) integer but received a scale-independent (sp) integer [ResourceType]
            print(sp.dp) // ERROR 5
                  ~~
        5 errors, 0 warnings
        """
      )
  }
}
