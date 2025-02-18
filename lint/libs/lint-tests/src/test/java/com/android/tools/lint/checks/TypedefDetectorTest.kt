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

import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.client.api.LintBaseline
import com.android.tools.lint.detector.api.Detector
import java.io.File
import org.junit.ComparisonFailure

class TypedefDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector = TypedefDetector()

  override fun lint(): TestLintTask {
    return super.lint().allowNonAlphabeticalFixOrder(true)
  }

  fun testDocumentationExample() {
    lint()
      .files(
        kotlin(
          """
                import android.view.View
                import androidx.annotation.IntDef

                // Example of using Android API already annotated with @IntDef:

                fun setAlignment(view: View) {
                    view.layoutDirection = View.LAYOUT_DIRECTION_RTL // OK
                    view.layoutDirection = View.TEXT_ALIGNMENT_TEXT_START // ERROR - not one of the allowed values
                }

                // Custom example creating your own typedef:

                const val CONST_1 = -1
                const val CONST_2 = 2
                const val CONST_3 = 0
                const val UNRELATED = 1

                @IntDef(CONST_1, CONST_2, CONST_3)
                @Retention(AnnotationRetention.SOURCE)
                annotation class DetailInfoTab

                fun test(@DetailInfoTab tab: Int) {
                }

                fun test() {
                    test(CONST_1) // OK
                    test(UNRELATED) // ERROR - not part of the @DetailsInfoTab list
                }
                """
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
            src/DetailInfoTab.kt:9: Error: Must be one of: View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL, View.LAYOUT_DIRECTION_INHERIT, View.LAYOUT_DIRECTION_LOCALE [WrongConstant]
                                view.layoutDirection = View.TEXT_ALIGNMENT_TEXT_START // ERROR - not one of the allowed values
                                                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/DetailInfoTab.kt:28: Error: Must be one of: DetailInfoTabKt.CONST_1, DetailInfoTabKt.CONST_2, DetailInfoTabKt.CONST_3 [WrongConstant]
                                test(UNRELATED) // ERROR - not part of the @DetailsInfoTab list
                                     ~~~~~~~~~
            2 errors, 0 warnings
            """
      )
  }

  fun testTypeDef() {

    val expected =
      "" +
        "src/test/pkg/IntDefTest.java:31: Error: Must be one of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n" +
        "        setStyle(0, 0); // ERROR\n" +
        "                 ~\n" +
        "src/test/pkg/IntDefTest.java:32: Error: Must be one of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n" +
        "        setStyle(-1, 0); // ERROR\n" +
        "                 ~~\n" +
        "src/test/pkg/IntDefTest.java:33: Error: Must be one of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n" +
        "        setStyle(UNRELATED, 0); // ERROR\n" +
        "                 ~~~~~~~~~\n" +
        "src/test/pkg/IntDefTest.java:34: Error: Must be one of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n" +
        "        setStyle(IntDefTest.UNRELATED, 0); // ERROR\n" +
        "                 ~~~~~~~~~~~~~~~~~~~~\n" +
        "src/test/pkg/IntDefTest.java:35: Error: Flag not allowed here [WrongConstant]\n" +
        "        setStyle(IntDefTest.STYLE_NORMAL|STYLE_NO_FRAME, 0); // ERROR: Not a flag\n" +
        "                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "src/test/pkg/IntDefTest.java:36: Error: Flag not allowed here [WrongConstant]\n" +
        "        setStyle(~STYLE_NO_FRAME, 0); // ERROR: Not a flag\n" +
        "                 ~~~~~~~~~~~~~~~\n" +
        "src/test/pkg/IntDefTest.java:55: Error: Must be one or more of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n" +
        "        setFlags(\"\", UNRELATED); // ERROR\n" +
        "                     ~~~~~~~~~\n" +
        "src/test/pkg/IntDefTest.java:56: Error: Must be one or more of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n" +
        "        setFlags(\"\", UNRELATED|STYLE_NO_TITLE); // ERROR\n" +
        "                     ~~~~~~~~~\n" +
        "src/test/pkg/IntDefTest.java:57: Error: Must be one or more of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n" +
        "        setFlags(\"\", STYLE_NORMAL|STYLE_NO_TITLE|UNRELATED); // ERROR\n" +
        "                                                 ~~~~~~~~~\n" +
        "src/test/pkg/IntDefTest.java:58: Error: Must be one or more of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n" +
        "        setFlags(\"\", 1); // ERROR\n" +
        "                     ~\n" +
        "src/test/pkg/IntDefTest.java:59: Error: Must be one or more of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n" +
        "        setFlags(\"\", arg < 0 ? STYLE_NORMAL : UNRELATED); // ERROR\n" +
        "                                              ~~~~~~~~~\n" +
        "src/test/pkg/IntDefTest.java:60: Error: Must be one or more of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n" +
        "        setFlags(\"\", arg < 0 ? UNRELATED : STYLE_NORMAL); // ERROR\n" +
        "                               ~~~~~~~~~\n" +
        "src/test/pkg/IntDefTest.java:79: Error: Must be one of: IntDefTest.TYPE_1, IntDefTest.TYPE_2 [WrongConstant]\n" +
        "        setTitle(\"\", UNRELATED_TYPE); // ERROR\n" +
        "                     ~~~~~~~~~~~~~~\n" +
        "src/test/pkg/IntDefTest.java:80: Error: Must be one of: IntDefTest.TYPE_1, IntDefTest.TYPE_2 [WrongConstant]\n" +
        "        setTitle(\"\", \"type2\"); // ERROR\n" +
        "                     ~~~~~~~\n" +
        "src/test/pkg/IntDefTest.java:87: Error: Must be one of: IntDefTest.TYPE_1, IntDefTest.TYPE_2 [WrongConstant]\n" +
        "        setTitle(\"\", type); // ERROR\n" +
        "                     ~~~~\n" +
        "src/test/pkg/IntDefTest.java:92: Error: Must be one or more of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT [WrongConstant]\n" +
        "        setFlags(\"\", flag); // ERROR\n" +
        "                     ~~~~\n" +
        "src/test/pkg/IntDefTest.java:99: Error: Must be one of: View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL, View.LAYOUT_DIRECTION_INHERIT, View.LAYOUT_DIRECTION_LOCALE [WrongConstant]\n" +
        "        view.setLayoutDirection(View.TEXT_DIRECTION_LTR); // ERROR\n" +
        "                                ~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "src/test/pkg/IntDefTest.java:100: Error: Must be one of: View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL, View.LAYOUT_DIRECTION_INHERIT, View.LAYOUT_DIRECTION_LOCALE [WrongConstant]\n" +
        "        view.setLayoutDirection(0); // ERROR\n" +
        "                                ~\n" +
        "src/test/pkg/IntDefTest.java:101: Error: Flag not allowed here [WrongConstant]\n" +
        "        view.setLayoutDirection(View.LAYOUT_DIRECTION_LTR|View.LAYOUT_DIRECTION_RTL); // ERROR\n" +
        "                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
        "19 errors, 0 warnings\n"
    lint()
      .files(
        java(
          "" +
            "package test.pkg;\n" +
            "\n" +
            "import android.content.Context;\n" +
            "import androidx.annotation.IntDef;\n" +
            "import androidx.annotation.StringDef;\n" +
            "import android.view.View;\n" +
            "\n" +
            "import java.lang.annotation.Retention;\n" +
            "import java.lang.annotation.RetentionPolicy;\n" +
            "\n" +
            "@SuppressWarnings(\"UnusedDeclaration\")\n" +
            "public class IntDefTest {\n" +
            "    @IntDef({STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT})\n" +
            "    @Retention(RetentionPolicy.SOURCE)\n" +
            "    private @interface DialogStyle {}\n" +
            "\n" +
            "    public static final int STYLE_NORMAL = 0;\n" +
            "    public static final int STYLE_NO_TITLE = 1;\n" +
            "    public static final int STYLE_NO_FRAME = 2;\n" +
            "    public static final int STYLE_NO_INPUT = 3;\n" +
            "    public static final int UNRELATED = 3;\n" +
            "\n" +
            "    public void setStyle(@DialogStyle int style, int theme) {\n" +
            "    }\n" +
            "\n" +
            "    public void testIntDef(int arg) {\n" +
            "        setStyle(STYLE_NORMAL, 0); // OK\n" +
            "        setStyle(IntDefTest.STYLE_NORMAL, 0); // OK\n" +
            "        setStyle(arg, 0); // OK (not sure)\n" +
            "\n" +
            "        setStyle(0, 0); // ERROR\n" +
            "        setStyle(-1, 0); // ERROR\n" +
            "        setStyle(UNRELATED, 0); // ERROR\n" +
            "        setStyle(IntDefTest.UNRELATED, 0); // ERROR\n" +
            "        setStyle(IntDefTest.STYLE_NORMAL|STYLE_NO_FRAME, 0); // ERROR: Not a flag\n" +
            "        setStyle(~STYLE_NO_FRAME, 0); // ERROR: Not a flag\n" +
            "    }\n" +
            "    @IntDef(value = {STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT}, flag=true)\n" +
            "    @Retention(RetentionPolicy.SOURCE)\n" +
            "    private @interface DialogFlags {}\n" +
            "\n" +
            "    public void setFlags(Object first, @DialogFlags int flags) {\n" +
            "    }\n" +
            "\n" +
            "    public void testFlags(int arg) {\n" +
            "        setFlags(\"\", -1); // OK\n" +
            "        setFlags(\"\", 0); // OK\n" +
            "        setFlags(\"\", STYLE_NORMAL); // OK\n" +
            "        setFlags(arg, 0); // OK (not sure)\n" +
            "        setFlags(\"\", IntDefTest.STYLE_NORMAL); // OK\n" +
            "        setFlags(\"\", STYLE_NORMAL|STYLE_NO_TITLE); // OK\n" +
            "        setFlags(\"\", STYLE_NORMAL|STYLE_NO_TITLE|STYLE_NO_INPUT); // OK\n" +
            "        setFlags(\"\", arg < 0 ? STYLE_NORMAL : STYLE_NO_TITLE); // OK\n" +
            "\n" +
            "        setFlags(\"\", UNRELATED); // ERROR\n" +
            "        setFlags(\"\", UNRELATED|STYLE_NO_TITLE); // ERROR\n" +
            "        setFlags(\"\", STYLE_NORMAL|STYLE_NO_TITLE|UNRELATED); // ERROR\n" +
            "        setFlags(\"\", 1); // ERROR\n" +
            "        setFlags(\"\", arg < 0 ? STYLE_NORMAL : UNRELATED); // ERROR\n" +
            "        setFlags(\"\", arg < 0 ? UNRELATED : STYLE_NORMAL); // ERROR\n" +
            "    }\n" +
            "\n" +
            "    public static final String TYPE_1 = \"type1\";\n" +
            "    public static final String TYPE_2 = \"type2\";\n" +
            "    public static final String UNRELATED_TYPE = \"other\";\n" +
            "\n" +
            "    @StringDef({TYPE_1, TYPE_2})\n" +
            "    @Retention(RetentionPolicy.SOURCE)\n" +
            "    private @interface DialogType {}\n" +
            "\n" +
            "    public void setTitle(String title, @DialogType String type) {\n" +
            "    }\n" +
            "\n" +
            "    public void testStringDef(String typeArg) {\n" +
            "        setTitle(\"\", TYPE_1); // OK\n" +
            "        setTitle(\"\", TYPE_2); // OK\n" +
            "        setTitle(\"\", null); // OK\n" +
            "        setTitle(\"\", typeArg); // OK (unknown)\n" +
            "        setTitle(\"\", UNRELATED_TYPE); // ERROR\n" +
            "        setTitle(\"\", \"type2\"); // ERROR\n" +
            "    }\n" +
            "\n" +
            "    public void testFlow() {\n" +
            "        String type = TYPE_1;\n" +
            "        setTitle(\"\", type); // OK\n" +
            "        type = UNRELATED_TYPE;\n" +
            "        setTitle(\"\", type); // ERROR\n" +
            "        int flag = 0;\n" +
            "        flag |= STYLE_NORMAL;\n" +
            "        setFlags(\"\", flag); // OK\n" +
            "        flag = UNRELATED;\n" +
            "        setFlags(\"\", flag); // ERROR\n" +
            "    }\n" +
            "\n" +
            "    public void testExternalAnnotations(View view, Context context) {\n" +
            "        view.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); // OK\n" +
            "        context.getSystemService(Context.ALARM_SERVICE); // OK\n" +
            "\n" +
            "        view.setLayoutDirection(View.TEXT_DIRECTION_LTR); // ERROR\n" +
            "        view.setLayoutDirection(0); // ERROR\n" +
            "        view.setLayoutDirection(View.LAYOUT_DIRECTION_LTR|View.LAYOUT_DIRECTION_RTL); // ERROR\n" +
            "        //context.getSystemService(TYPE_1); // ERROR\n" +
            "    }\n" +
            "}\n"
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(expected)
  }

  fun testTypeDef37324044() {
    // Regression test for issue 37324044
    lint()
      .files(
        java(
          "package test.pkg;\n" +
            "\n" +
            "import java.util.Calendar;\n" +
            "\n" +
            "public class IntDefTest {\n" +
            "    public void test() {\n" +
            "        Calendar.getInstance().get(Calendar.DAY_OF_MONTH);\n" +
            "    }\n" +
            "}\n"
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun testCombinedIntDefAndIntRange() {
    val expected =
      "src/test/pkg/X.java:27: Error: Must be one of: X.LENGTH_INDEFINITE, X.LENGTH_SHORT, X.LENGTH_LONG [WrongConstant]\n" +
        "        setDuration(UNRELATED); /// OK within range\n" +
        "                    ~~~~~~~~~\n" +
        "src/test/pkg/X.java:28: Error: Must be one of: X.LENGTH_INDEFINITE, X.LENGTH_SHORT, X.LENGTH_LONG or value must be ≥ 10 (was -5) [WrongConstant]\n" +
        "        setDuration(-5); // ERROR (not right int def or value\n" +
        "                    ~~\n" +
        "src/test/pkg/X.java:29: Error: Must be one of: X.LENGTH_INDEFINITE, X.LENGTH_SHORT, X.LENGTH_LONG or value must be ≥ 10 (was 8) [WrongConstant]\n" +
        "        setDuration(8); // ERROR (not matching number range)\n" +
        "                    ~\n" +
        "3 errors, 0 warnings\n"
    lint()
      .files(
        java(
          "src/test/pkg/X.java",
          "" +
            "\n" +
            "package test.pkg;\n" +
            "\n" +
            "import androidx.annotation.IntDef;\n" +
            "import androidx.annotation.IntRange;\n" +
            "\n" +
            "import java.lang.annotation.Retention;\n" +
            "import java.lang.annotation.RetentionPolicy;\n" +
            "\n" +
            "@SuppressWarnings({\"UnusedParameters\", \"unused\", \"SpellCheckingInspection\"})\n" +
            "public class X {\n" +
            "\n" +
            "    public static final int UNRELATED = 500;\n" +
            "\n" +
            "    @IntDef({LENGTH_INDEFINITE, LENGTH_SHORT, LENGTH_LONG})\n" +
            "    @IntRange(from = 10)\n" +
            "    @Retention(RetentionPolicy.SOURCE)\n" +
            "    public @interface Duration {}\n" +
            "\n" +
            "    public static final int LENGTH_INDEFINITE = -2;\n" +
            "    public static final int LENGTH_SHORT = -1;\n" +
            "    public static final int LENGTH_LONG = 0;\n" +
            "    public void setDuration(@Duration int duration) {\n" +
            "    }\n" +
            "\n" +
            "    public void test() {\n" +
            "        setDuration(UNRELATED); /// OK within range\n" +
            "        setDuration(-5); // ERROR (not right int def or value\n" +
            "        setDuration(8); // ERROR (not matching number range)\n" +
            "        setDuration(8000); // OK (@IntRange applies)\n" +
            "        setDuration(LENGTH_INDEFINITE); // OK (@IntDef)\n" +
            "        setDuration(LENGTH_LONG); // OK (@IntDef)\n" +
            "        setDuration(LENGTH_SHORT); // OK (@IntDef)\n" +
            "    }\n" +
            "}\n",
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .allowNonAlphabeticalFixOrder(true)
      .run()
      .expect(expected)
      .expectFixDiffs(
        """
            Fix for src/test/pkg/X.java line 27: Change to X.LENGTH_INDEFINITE:
            @@ -27 +27
            -         setDuration(UNRELATED); /// OK within range
            +         setDuration(X.LENGTH_INDEFINITE); /// OK within range
            Fix for src/test/pkg/X.java line 27: Change to X.LENGTH_SHORT:
            @@ -27 +27
            -         setDuration(UNRELATED); /// OK within range
            +         setDuration(X.LENGTH_SHORT); /// OK within range
            Fix for src/test/pkg/X.java line 27: Change to X.LENGTH_LONG:
            @@ -27 +27
            -         setDuration(UNRELATED); /// OK within range
            +         setDuration(X.LENGTH_LONG); /// OK within range
            Fix for src/test/pkg/X.java line 28: Change to X.LENGTH_INDEFINITE:
            @@ -28 +28
            -         setDuration(-5); // ERROR (not right int def or value
            +         setDuration(X.LENGTH_INDEFINITE); // ERROR (not right int def or value
            Fix for src/test/pkg/X.java line 28: Change to X.LENGTH_SHORT:
            @@ -28 +28
            -         setDuration(-5); // ERROR (not right int def or value
            +         setDuration(X.LENGTH_SHORT); // ERROR (not right int def or value
            Fix for src/test/pkg/X.java line 28: Change to X.LENGTH_LONG:
            @@ -28 +28
            -         setDuration(-5); // ERROR (not right int def or value
            +         setDuration(X.LENGTH_LONG); // ERROR (not right int def or value
            Fix for src/test/pkg/X.java line 29: Change to X.LENGTH_INDEFINITE:
            @@ -29 +29
            -         setDuration(8); // ERROR (not matching number range)
            +         setDuration(X.LENGTH_INDEFINITE); // ERROR (not matching number range)
            Fix for src/test/pkg/X.java line 29: Change to X.LENGTH_SHORT:
            @@ -29 +29
            -         setDuration(8); // ERROR (not matching number range)
            +         setDuration(X.LENGTH_SHORT); // ERROR (not matching number range)
            Fix for src/test/pkg/X.java line 29: Change to X.LENGTH_LONG:
            @@ -29 +29
            -         setDuration(8); // ERROR (not matching number range)
            +         setDuration(X.LENGTH_LONG); // ERROR (not matching number range)
            """
      )
  }

  fun testMultipleProjects() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=182179
    // 182179: Lint gives erroneous @StringDef errors in androidTests
    val expected =
      "src/test/zpkg/SomeClassTest.java:10: Error: Must be one of: SomeClass.MY_CONSTANT [WrongConstant]\n" +
        "        SomeClass.doSomething(\"error\");\n" +
        "                              ~~~~~~~\n" +
        "1 errors, 0 warnings\n"
    lint()
      .files(
        java(
          "src/test/pkg/SomeClass.java",
          "" +
            "package test.pkg;\n" +
            "\n" +
            "import androidx.annotation.StringDef;\n" +
            "import android.util.Log;\n" +
            "\n" +
            "import java.lang.annotation.Documented;\n" +
            "import java.lang.annotation.Retention;\n" +
            "import java.lang.annotation.RetentionPolicy;\n" +
            "\n" +
            "public class SomeClass {\n" +
            "\n" +
            "    public static final String MY_CONSTANT = \"foo\";\n" +
            "\n" +
            "    public static void doSomething(@MyTypeDef final String myString) {\n" +
            "        Log.v(\"tag\", myString);\n" +
            "    }\n" +
            "\n" +
            "\n" +
            "    /**\n" +
            "     * Defines the possible values for state type.\n" +
            "     */\n" +
            "    @StringDef({MY_CONSTANT})\n" +
            "    @Documented\n" +
            "    @Retention(RetentionPolicy.SOURCE)\n" +
            "    public @interface MyTypeDef {\n" +
            "\n" +
            "    }\n" +
            "}",
        ),
        // test.zpkg: alphabetically after test.pkg: We want to make sure
        // that the SomeClass source unit is disposed before we try to
        // process SomeClassTest and try to resolve its SomeClass.MY_CONSTANT
        // @IntDef reference
        java(
          "src/test/zpkg/SomeClassTest.java",
          "" +
            "package test.zpkg;\n" +
            "\n" +
            "import test.pkg.SomeClass;\n" +
            "import junit.framework.TestCase;\n" +
            "\n" +
            "public class SomeClassTest extends TestCase {\n" +
            "\n" +
            "    public void testDoSomething() {\n" +
            "        SomeClass.doSomething(SomeClass.MY_CONSTANT);\n" +
            "        SomeClass.doSomething(\"error\");\n" +
            "    }\n" +
            "}",
        ),
        // junit stub:
        java(
            """
                package junit.framework;
                public class TestCase {
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(expected)
      .expectFixDiffs(
        """
            Fix for src/test/zpkg/SomeClassTest.java line 10: Change to SomeClass.MY_CONSTANT:
            @@ -10 +10
            -         SomeClass.doSomething("error");
            +         SomeClass.doSomething(SomeClass.MY_CONSTANT);
            """
      )
  }

  /** Test @IntDef when applied to multiple elements like arrays or varargs. */
  fun testIntDefMultiple() {
    val expected =
      "src/test/pkg/IntDefMultiple.java:24: Error: Must be one of: IntDefMultiple.VALUE_A, IntDefMultiple.VALUE_B [WrongConstant]\n" +
        "        restrictedArray(/*Must be one of: X.VALUE_A, X.VALUE_B*/new int[]{VALUE_A, 0, VALUE_B}/**/); // ERROR;\n" +
        "                                                                                   ~\n" +
        "src/test/pkg/IntDefMultiple.java:26: Error: Must be one of: IntDefMultiple.VALUE_A, IntDefMultiple.VALUE_B [WrongConstant]\n" +
        "        restrictedArray(/*Must be one of: X.VALUE_A, X.VALUE_B*/INVALID_ARRAY/**/); // ERROR\n" +
        "                                                                ~~~~~~~~~~~~~\n" +
        "src/test/pkg/IntDefMultiple.java:27: Error: Must be one of: IntDefMultiple.VALUE_A, IntDefMultiple.VALUE_B [WrongConstant]\n" +
        "        restrictedArray(/*Must be one of: X.VALUE_A, X.VALUE_B*/INVALID_ARRAY2/**/); // ERROR\n" +
        "                                                                ~~~~~~~~~~~~~~\n" +
        "src/test/pkg/IntDefMultiple.java:31: Error: Must be one of: IntDefMultiple.VALUE_A, IntDefMultiple.VALUE_B [WrongConstant]\n" +
        "        restrictedEllipsis(VALUE_A, /*Must be one of: X.VALUE_A, X.VALUE_B*/0/**/, VALUE_B); // ERROR\n" +
        "                                                                            ~\n" +
        "src/test/pkg/IntDefMultiple.java:32: Error: Must be one of: IntDefMultiple.VALUE_A, IntDefMultiple.VALUE_B [WrongConstant]\n" +
        "        restrictedEllipsis(/*Must be one of: X.VALUE_A, X.VALUE_B*/0/**/); // ERROR\n" +
        "                                                                   ~\n" +
        "5 errors, 0 warnings\n"
    lint()
      .files(
        java(
          "src/test/pkg/IntDefMultiple.java",
          "" +
            "package test.pkg;\n" +
            "import androidx.annotation.IntDef;\n" +
            "\n" +
            "public class IntDefMultiple {\n" +
            "    private static final int VALUE_A = 0;\n" +
            "    private static final int VALUE_B = 1;\n" +
            "\n" +
            "    private static final int[] VALID_ARRAY = {VALUE_A, VALUE_B};\n" +
            "    private static final int[] INVALID_ARRAY = {VALUE_A, 0, VALUE_B};\n" +
            "    private static final int[] INVALID_ARRAY2 = {10};\n" +
            "\n" +
            "    @IntDef({VALUE_A, VALUE_B})\n" +
            "    public @interface MyIntDef {}\n" +
            "\n" +
            "    @MyIntDef\n" +
            "    public int a = 0;\n" +
            "\n" +
            "    @MyIntDef\n" +
            "    public int[] b;\n" +
            "\n" +
            "    public void testCall() {\n" +
            "        restrictedArray(new int[]{VALUE_A}); // OK\n" +
            "        restrictedArray(new int[]{VALUE_A, VALUE_B}); // OK\n" +
            "        restrictedArray(/*Must be one of: X.VALUE_A, X.VALUE_B*/new int[]{VALUE_A, 0, VALUE_B}/**/); // ERROR;\n" +
            "        restrictedArray(VALID_ARRAY); // OK\n" +
            "        restrictedArray(/*Must be one of: X.VALUE_A, X.VALUE_B*/INVALID_ARRAY/**/); // ERROR\n" +
            "        restrictedArray(/*Must be one of: X.VALUE_A, X.VALUE_B*/INVALID_ARRAY2/**/); // ERROR\n" +
            "\n" +
            "        restrictedEllipsis(VALUE_A); // OK\n" +
            "        restrictedEllipsis(VALUE_A, VALUE_B); // OK\n" +
            "        restrictedEllipsis(VALUE_A, /*Must be one of: X.VALUE_A, X.VALUE_B*/0/**/, VALUE_B); // ERROR\n" +
            "        restrictedEllipsis(/*Must be one of: X.VALUE_A, X.VALUE_B*/0/**/); // ERROR\n" +
            "        // Suppressed via older Android Studio inspection id:\n" +
            "        //noinspection ResourceType\n" +
            "        restrictedEllipsis(0); // SUPPRESSED\n" +
            "    }\n" +
            "\n" +
            "    private void restrictedEllipsis(@MyIntDef int... test) {}\n" +
            "\n" +
            "    private void restrictedArray(@MyIntDef int[] test) {}\n" +
            "}",
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(expected)
  }

  fun testIntDefInBuilder() {
    // Ensure that we only check constants, not instance fields, when passing
    // fields as arguments to typedef parameters.
    lint()
      .files(
        java(
          "src/test/pkg/Product.java",
          "" +
            "package test.pkg;\n" +
            "\n" +
            "import androidx.annotation.IntDef;\n" +
            "\n" +
            "import java.lang.annotation.Retention;\n" +
            "import java.lang.annotation.RetentionPolicy;\n" +
            "\n" +
            "public class Product {\n" +
            "    @IntDef({\n" +
            "         STATUS_AVAILABLE, STATUS_BACK_ORDER, STATUS_UNAVAILABLE\n" +
            "    })\n" +
            "    @Retention(RetentionPolicy.SOURCE)\n" +
            "    public @interface Status {\n" +
            "    }\n" +
            "    public static final int STATUS_AVAILABLE = 1;\n" +
            "    public static final int STATUS_BACK_ORDER = 2;\n" +
            "    public static final int STATUS_UNAVAILABLE = 3;\n" +
            "\n" +
            "    @Status\n" +
            "    private final int mStatus;\n" +
            "    private final String mName;\n" +
            "\n" +
            "    private Product(String name, @Status int status) {\n" +
            "        mName = name;\n" +
            "        mStatus = status;\n" +
            "    }\n" +
            "    public static class Builder {\n" +
            "        @Status\n" +
            "        private int mStatus;\n" +
            "        private final int mStatus2 = STATUS_AVAILABLE;\n" +
            "        @Status static final int DEFAULT_STATUS = Product.STATUS_UNAVAILABLE;\n" +
            "        private String mName;\n" +
            "\n" +
            "        public Builder(String name, @Status int status) {\n" +
            "            mName = name;\n" +
            "            mStatus = status;\n" +
            "        }\n" +
            "\n" +
            "        public Builder setStatus(@Status int status) {\n" +
            "            mStatus = status;\n" +
            "            return this;\n" +
            "        }\n" +
            "\n" +
            "        public Product build() {\n" +
            "            return new Product(mName, mStatus);\n" +
            "        }\n" +
            "\n" +
            "        public Product build2() {\n" +
            "            return new Product(mName, mStatus2);\n" +
            "        }\n" +
            "\n" +
            "        public static Product build3() {\n" +
            "            return new Product(\"\", DEFAULT_STATUS);\n" +
            "        }\n" +
            "    }\n" +
            "}\n",
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun testWrongConstant() {
    // Regression test for scenario found to be inconsistent between PSI and UAST
    lint()
      .files(
        java(
            """
                    package test.pkg;

                    import androidx.annotation.NonNull;

                    @SuppressWarnings({"ClassNameDiffersFromFileName","FieldCanBeLocal"})
                    public class ViewableDayInterval {
                        @CalendarDay
                        private int mDayCreatedFor;

                        public ViewableDayInterval(long startOffset, long duration, @NonNull @CalendarDay int... startDays) {
                            this(startDays[0], startOffset, duration, startDays);
                        }

                        public ViewableDayInterval(long start, @NonNull @WeekDay int... weekdays) {
                            this(weekdays[0], start, start, weekdays);
                        }

                        public ViewableDayInterval(long start, @NonNull @WeekDay int weekday) {
                            this(weekday, start, start, weekday);
                        }

                        public ViewableDayInterval(@CalendarDay int dayCreatedFor, long startOffset, long duration, @NonNull @CalendarDay int... startDays) {
                            mDayCreatedFor = dayCreatedFor;
                        }
                    }"""
          )
          .indented(),
        java(
            """
                    package test.pkg;

                    import androidx.annotation.IntDef;

                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    import java.util.Calendar;

                    @SuppressWarnings("ClassNameDiffersFromFileName")
                    @Retention(RetentionPolicy.SOURCE)
                    @IntDef({Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY})
                    public @interface CalendarDay {
                    }"""
          )
          .indented(),
        java(
            """
                    package test.pkg;

                    import androidx.annotation.IntDef;

                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    import java.util.Calendar;

                    @SuppressWarnings("ClassNameDiffersFromFileName")
                    @Retention(RetentionPolicy.SOURCE)
                    @IntDef({Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                            Calendar.THURSDAY, Calendar.FRIDAY})
                    public @interface WeekDay {
                    }"""
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun testIndirectTypedef() {
    // Regression test for b/36384014
    lint()
      .files(
        java(
          "package test.pkg;\n" +
            "\n" +
            "import androidx.annotation.IntDef;\n" +
            "\n" +
            "public class Lifecycle {\n" +
            "    public static final int ON_CREATE = 1;\n" +
            "    public static final int ON_START = 2;\n" +
            "    public static final int ON_RESUME = 3;\n" +
            "    public static final int ON_PAUSE = 4;\n" +
            "    public static final int ON_STOP = 5;\n" +
            "    public static final int ON_DESTROY = 6;\n" +
            "    public static final int ANY = 7;\n" +
            "\n" +
            "    @IntDef(value = {ON_CREATE, ON_START, ON_RESUME, ON_PAUSE, ON_STOP, ON_DESTROY, ANY},\n" +
            "            flag = true)\n" +
            "    public @interface Event {\n" +
            "    }\n" +
            "}"
        ),
        java(
          "" +
            "package test.pkg;\n" +
            "\n" +
            "import java.lang.annotation.ElementType;\n" +
            "import java.lang.annotation.Retention;\n" +
            "import java.lang.annotation.RetentionPolicy;\n" +
            "import java.lang.annotation.Target;\n" +
            "\n" +
            "@Retention(RetentionPolicy.RUNTIME)\n" +
            "@Target(ElementType.METHOD)\n" +
            "public @interface OnLifecycleEvent {\n" +
            "    @Lifecycle.Event\n" +
            "    int value();\n" +
            "}\n"
        ),
        java(
          "" +
            "package test.pkg;\n" +
            "\n" +
            "public interface Usage {\n" +
            "    @OnLifecycleEvent(4494823) // this value is not valid\n" +
            "    void addLocationListener();\n" +
            "}\n"
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        "" +
          "src/test/pkg/Usage.java:4: Error: Must be one or more of: Lifecycle.ON_CREATE, Lifecycle.ON_START, Lifecycle.ON_RESUME, Lifecycle.ON_PAUSE, Lifecycle.ON_STOP, Lifecycle.ON_DESTROY, Lifecycle.ANY [WrongConstant]\n" +
          "    @OnLifecycleEvent(4494823) // this value is not valid\n" +
          "                      ~~~~~~~\n" +
          "1 errors, 0 warnings\n"
      )
  }

  fun testCalendar() {
    // Regression test for
    // https://code.google.com/p/android/issues/detail?id=251256 and
    // http://youtrack.jetbrains.com/issue/IDEA-144891

    lint()
      .files(
        java(
          "" +
            "package test.pkg;\n" +
            "\n" +
            "import java.util.Calendar;\n" +
            "\n" +
            "public class CalendarTest {\n" +
            "    public void test() {\n" +
            "        Calendar now = Calendar.getInstance();\n" +
            "        now.get(Calendar.DAY_OF_MONTH);\n" +
            "        now.get(Calendar.HOUR_OF_DAY);\n" +
            "        now.get(Calendar.MINUTE);\n" +
            "        if (now.get(Calendar.MONTH) == Calendar.JANUARY) {\n" +
            "        }\n" +
            "        now.set(Calendar.HOUR_OF_DAY, 50);\n" +
            "        now.set(2017, 3, 29);\n" +
            "    }\n" +
            "}\n"
        )
      )
      .run()
      .expectClean()
  }

  fun testIntDef() {
    lint()
      .files(
        java(
          "" +
            "package test.pkg;\n" +
            "import android.annotation.SuppressLint;\n" +
            "import android.annotation.TargetApi;\n" +
            "import android.app.Notification;\n" +
            "import android.content.Context;\n" +
            "import android.content.Intent;\n" +
            "import android.content.ServiceConnection;\n" +
            "import android.content.res.Resources;\n" +
            "import android.os.Build;\n" +
            "import androidx.annotation.DrawableRes;\n" +
            "import android.view.View;\n" +
            "import java.util.concurrent.Executor;\n" +
            "\n" +
            "import static android.content.Context.CONNECTIVITY_SERVICE;\n" +
            "\n" +
            "@SuppressWarnings(\"UnusedDeclaration\")\n" +
            "public class X {\n" +
            "\n" +
            "    @TargetApi(Build.VERSION_CODES.KITKAT)\n" +
            "    public void testStringDef(Context context, String unknown) {\n" +
            "        Object ok1 = context.getSystemService(unknown);\n" +
            "        Object ok2 = context.getSystemService(Context.CLIPBOARD_SERVICE);\n" +
            "        Object ok3 = context.getSystemService(android.content.Context.WINDOW_SERVICE);\n" +
            "        Object ok4 = context.getSystemService(CONNECTIVITY_SERVICE);\n" +
            "    }\n" +
            "\n" +
            "    @SuppressLint(\"UseCheckPermission\")\n" +
            "    @TargetApi(Build.VERSION_CODES.KITKAT)\n" +
            "    public void testIntDef(Context context, int unknown, View view) {\n" +
            "        view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); // OK\n" +
            "        view.setLayoutDirection(/*Must be one of: View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL, View.LAYOUT_DIRECTION_INHERIT, View.LAYOUT_DIRECTION_LOCALE*/View.TEXT_ALIGNMENT_TEXT_START/**/); // Error\n" +
            "        view.setLayoutDirection(/*Flag not allowed here*/View.LAYOUT_DIRECTION_RTL | View.LAYOUT_DIRECTION_RTL/**/); // Error\n" +
            "    }\n" +
            "\n" +
            "    @TargetApi(Build.VERSION_CODES.KITKAT)\n" +
            "    public void testIntDefFlags(Context context, int unknown, Intent intent, Executor executor,\n" +
            "                           ServiceConnection connection) {\n" +
            "        // Flags\n" +
            "        Object ok1 = context.bindService(intent, connection, 0);\n" +
            "        Object ok2 = context.bindService(intent, connection, -1);\n" +
            "        Object ok3 = context.bindService(intent, connection, Context.BIND_ABOVE_CLIENT);\n" +
            "        Object ok4 = context.bindService(intent, connection, Context.BIND_ABOVE_CLIENT\n" +
            "                | Context.BIND_AUTO_CREATE);\n" +
            "        int flags1 = Context.BIND_ABOVE_CLIENT | Context.BIND_AUTO_CREATE;\n" +
            "        Object ok5 = context.bindService(intent, connection, flags1);\n" +
            "\n" +
            "        Object error1 = context.bindService(intent,\n" +
            "                Context.BIND_ABOVE_CLIENT | /*Must be one or more of: Context.BIND_AUTO_CREATE, Context.BIND_DEBUG_UNBIND, Context.BIND_NOT_FOREGROUND, Context.BIND_ABOVE_CLIENT, Context.BIND_ALLOW_OOM_MANAGEMENT, Context.BIND_WAIVE_PRIORITY, Context.BIND_IMPORTANT, Context.BIND_ADJUST_WITH_ACTIVITY, Context.BIND_NOT_PERCEPTIBLE, Context.BIND_ALLOW_ACTIVITY_STARTS, Context.BIND_INCLUDE_CAPABILITIES, Context.BIND_SHARED_ISOLATED_PROCESS, Context.BIND_EXTERNAL_SERVICE*/Context.CONTEXT_IGNORE_SECURITY/**/,\n" +
            "                executor, connection);\n" +
            "        int flags2 = Context.BIND_ABOVE_CLIENT | Context.CONTEXT_IGNORE_SECURITY;\n" +
            "        Object error2 = context.bindService(intent,\n" +
            "                /*Must be one or more of: Context.BIND_AUTO_CREATE, Context.BIND_DEBUG_UNBIND, Context.BIND_NOT_FOREGROUND, Context.BIND_ABOVE_CLIENT, Context.BIND_ALLOW_OOM_MANAGEMENT, Context.BIND_WAIVE_PRIORITY, Context.BIND_IMPORTANT, Context.BIND_ADJUST_WITH_ACTIVITY, Context.BIND_NOT_PERCEPTIBLE, Context.BIND_ALLOW_ACTIVITY_STARTS, Context.BIND_INCLUDE_CAPABILITIES, Context.BIND_SHARED_ISOLATED_PROCESS, Context.BIND_EXTERNAL_SERVICE*/flags2/**/,\n" +
            "                executor, connection);\n" +
            "    }\n" +
            "}\n"
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .allowCompilationErrors()
      .run()
      .expectInlinedMessages()
  }

  fun testStringDefOnEquals() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=186598

    lint()
      .files(
        java(
          "" +
            "package test.pkg;\n" +
            "\n" +
            "import androidx.annotation.StringDef;\n" +
            "\n" +
            "import java.lang.annotation.Retention;\n" +
            "\n" +
            "@SuppressWarnings({\"unused\", \"StringEquality\"})\n" +
            "public class X {\n" +
            "    public static final String SUNDAY = \"a\";\n" +
            "    public static final String MONDAY = \"b\";\n" +
            "\n" +
            "    @StringDef(value = {\n" +
            "            SUNDAY,\n" +
            "            MONDAY\n" +
            "    })\n" +
            "    @Retention(java.lang.annotation.RetentionPolicy.SOURCE)\n" +
            "    public @interface Day {\n" +
            "    }\n" +
            "\n" +
            "    @Day\n" +
            "    public String getDay() {\n" +
            "        return MONDAY;\n" +
            "    }\n" +
            "\n" +
            "    public void test(Object object) {\n" +
            "        boolean ok1 = this.getDay() == /*Must be one of: X.SUNDAY, X.MONDAY*/\"Any String\"/**/;\n" +
            "        boolean ok2 = this.getDay().equals(MONDAY);\n" +
            "        boolean wrong1 = this.getDay().equals(/*Must be one of: X.SUNDAY, X.MONDAY*/\"Any String\"/**/);\n" +
            "    }\n" +
            "}\n"
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectInlinedMessages()
  }

  fun testAndingWithForeignMasks() {
    // False positive encountered in the support lib codebase (simplified):
    // Allow &'ing flags with masks without restrictions (necessary since in
    // many cases the masks are coming from unknown declarations (class fields
    // where we only have values, not references)
    lint()
      .files(
        java(
            """
                    package test.pkg;

                    import androidx.annotation.IntDef;
                    import android.view.Gravity;
                    import android.view.View;

                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;

                    @SuppressWarnings({"ClassNameDiffersFromFileName", "ConstantConditions", "RedundantIfStatement", "ConstantIfStatement"})
                    public class GravityTest {
                        @IntDef(value = {Gravity.LEFT, Gravity.RIGHT}, flag = true)
                        @Retention(RetentionPolicy.SOURCE)
                        private @interface EdgeGravity {}


                        public void usage(final View child) {
                            @EdgeGravity final int childGravity =
                                    getDrawerViewAbsoluteGravity(child) & Gravity.HORIZONTAL_GRAVITY_MASK;
                            if (true) {
                                throw new IllegalStateException("Child drawer has absolute gravity "
                                        + gravityToString(childGravity) + " but this tag already has a "
                                        + "drawer view along that edge");
                            }
                        }

                        int getDrawerViewAbsoluteGravity(View drawerView) {
                            return Gravity.LEFT; // Wrong
                        }

                        static String gravityToString(@EdgeGravity int gravity) {
                            if ((gravity & Gravity.LEFT) == Gravity.LEFT) {
                                return "LEFT";
                            }
                            if ((gravity & Gravity.RIGHT) == Gravity.RIGHT) {
                                return "RIGHT";
                            }
                            return Integer.toHexString(gravity);
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

  fun testVarIntDef() {
    // Regression test for b/37078720
    lint()
      .files(
        java(
            """
                package test.pkg;

                import androidx.annotation.IntDef;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "UnusedAssignment", "FieldCanBeLocal"})
                public class IntDefVarTest {
                    private static final int TREE_PATH_ONE = 1;
                    private static final int TREE_PATH_TWO = 2;
                    private static final int TREE_PATH_THREE = 3;

                    @IntDef(value = {
                            TREE_PATH_ONE,
                            TREE_PATH_TWO,
                            TREE_PATH_THREE
                    })
                    @Retention(RetentionPolicy.SOURCE)
                    private @interface Tree {
                    }

                    @Tree
                    private int mTreeField = TREE_PATH_ONE;

                    private void problem1() {
                        @Tree int treeInvalid = 12;
                        treeInvalid = 13;
                        // annotations for variables or fields
                        @Tree int treeValid = TREE_PATH_ONE;
                        problem2(mTreeField); // Falsely marked as an error. Lint does not track @IntDef annotations
                        // fields so it does not know the mTreeField is actually a @Tree
                    }

                    @Tree
                    private int mTreeField2 = 14;

                    private void problem2(@Tree int tree) {
                    }


                    @IntDef(value = {1, 2, 3})
                    @Retention(RetentionPolicy.SOURCE)
                    private @interface TestIntDef {
                    }

                    @TestIntDef
                    private int testVar = 4;
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        "" +
          "src/test/pkg/IntDefVarTest.java:26: Error: Must be one of: IntDefVarTest.TREE_PATH_ONE, IntDefVarTest.TREE_PATH_TWO, IntDefVarTest.TREE_PATH_THREE [WrongConstant]\n" +
          "        @Tree int treeInvalid = 12;\n" +
          "                                ~~\n" +
          "src/test/pkg/IntDefVarTest.java:27: Error: Must be one of: IntDefVarTest.TREE_PATH_ONE, IntDefVarTest.TREE_PATH_TWO, IntDefVarTest.TREE_PATH_THREE [WrongConstant]\n" +
          "        treeInvalid = 13;\n" +
          "        ~~~~~~~~~~~\n" +
          "src/test/pkg/IntDefVarTest.java:35: Error: Must be one of: IntDefVarTest.TREE_PATH_ONE, IntDefVarTest.TREE_PATH_TWO, IntDefVarTest.TREE_PATH_THREE [WrongConstant]\n" +
          "    private int mTreeField2 = 14;\n" +
          "                              ~~\n" +
          "src/test/pkg/IntDefVarTest.java:47: Error: Must be one of: 1, 2, 3 [WrongConstant]\n" +
          "    private int testVar = 4;\n" +
          "                          ~\n" +
          "4 errors, 0 warnings\n"
      )
  }

  // Temporarily disabled because PSQ does not seem to have tools/adt/idea
  //  9af9ae6ed2a4fe8d6a4a29726772568cb505b4ed applied. Hiding test for now to
  // unblock integrating bigger change; restore in separate CL.
  fun ignored_testCalendarGet() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=230099

    lint()
      .files(
        java(
          "" +
            "package test.pkg;\n" +
            "import java.util.Calendar;\n" +
            "\n" +
            "@SuppressWarnings({\"unused\", \"StatementWithEmptyBody\"})\n" +
            "public class X  {\n" +
            "    private void check(Calendar lhsCal, Calendar rhsCal) {\n" +
            "        if( lhsCal.get(Calendar.DAY_OF_YEAR) == rhsCal.get(Calendar.DAY_OF_YEAR)+1){\n" +
            "        }\n" +
            "        if( lhsCal.get(Calendar.DAY_OF_YEAR) == 200){\n" +
            "        }\n" +
            "    }\n" +
            "}\n"
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun testEnforceMethodReturnValueConstraints() {
    // Regression test for 69321287
    lint()
      .files(
        java(
            """
                    package test.pkg;

                    import androidx.annotation.IntDef;

                    @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                    public class IntDefTest {
                        public void test() {
                            wantInt(100); // ERROR
                            wantInt(WrongType.NO); // ERROR
                            wantInt(giveRandomInt()); // ERROR
                            wantInt(giveWrongInt()); //ERROR
                            wantInt(giveWrongIntAnnotated()); //ERROR
                            wantInt(giveUnknownInt()); // OK (unknown)
                            wantInt(giveRightInt()); //OK
                        }

                        @IntDef({TestType.LOL})
                        public @interface TestType {
                            int LOL = 1;
                        }

                        @IntDef({WrongType.NO})
                        public @interface WrongType {
                            int NO = 2;
                        }

                        public void wantInt(@TestType int input) {}

                        public int giveRandomInt() {
                            return 100;
                        }

                        public int giveUnknownInt() {
                            return (int) (giveRandomInt() * System.currentTimeMillis());
                        }

                        public int giveWrongInt() {
                            return WrongType.NO;
                        }

                        public int giveRightInt() {
                            return TestType.LOL;
                        }

                        @WrongType public int giveWrongIntAnnotated() {
                            return WrongType.NO;
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
          "src/test/pkg/IntDefTest.java:8: Error: Must be one of: TestType.LOL [WrongConstant]\n" +
          "        wantInt(100); // ERROR\n" +
          "                ~~~\n" +
          "src/test/pkg/IntDefTest.java:9: Error: Must be one of: TestType.LOL [WrongConstant]\n" +
          "        wantInt(WrongType.NO); // ERROR\n" +
          "                ~~~~~~~~~~~~\n" +
          "src/test/pkg/IntDefTest.java:10: Error: Must be one of: TestType.LOL [WrongConstant]\n" +
          "        wantInt(giveRandomInt()); // ERROR\n" +
          "                ~~~~~~~~~~~~~~~\n" +
          "src/test/pkg/IntDefTest.java:11: Error: Must be one of: TestType.LOL [WrongConstant]\n" +
          "        wantInt(giveWrongInt()); //ERROR\n" +
          "                ~~~~~~~~~~~~~~\n" +
          "src/test/pkg/IntDefTest.java:12: Error: Must be one of: TestType.LOL [WrongConstant]\n" +
          "        wantInt(giveWrongIntAnnotated()); //ERROR\n" +
          "                ~~~~~~~~~~~~~~~~~~~~~~~\n" +
          "5 errors, 0 warnings"
      )
  }

  fun testEnforceMethodReturnValueConstraintsKotlin() {
    // Regression test for 69321287
    lint()
      .files(
        kotlin(
            """
                    package test.pkg

                    @Suppress("UseExpressionBody")
                    class IntDefTest {
                        fun test() {
                            wantInt(100) // ERROR
                            wantInt(WrongType.NO) // ERROR
                            wantInt(giveRandomInt()) // ERROR
                            wantInt(giveWrongInt()) //ERROR
                            wantInt(giveWrongIntAnnotated()) //ERROR
                            wantInt(giveUnknownInt()) // OK (unknown)
                            wantInt(giveRightInt()) //OK
                        }

                        fun wantInt(@TestType input: Int) {}

                        fun giveRandomInt(): Int {
                            return 100
                        }

                        fun giveUnknownInt(): Int {
                            return (giveRandomInt() * System.currentTimeMillis()).toInt()
                        }

                        fun giveWrongInt(): Int {
                            return WrongType.NO
                        }

                        fun giveRightInt(): Int {
                            return TestType.LOL
                        }

                        @WrongType
                        fun giveWrongIntAnnotated(): Int {
                            return WrongType.NO
                        }
                    }
                """
          )
          .indented(),
        java(
            """
                    package test.pkg;

                    import androidx.annotation.IntDef;

                    @SuppressWarnings("ClassNameDiffersFromFileName")
                    @IntDef({WrongType.NO})
                    public @interface WrongType {
                        int NO = 2;
                    }
                    """
          )
          .indented(),
        java(
            """
                    package test.pkg;

                    import androidx.annotation.IntDef;

                    @SuppressWarnings("ClassNameDiffersFromFileName")
                    @IntDef({TestType.LOL})
                    public @interface TestType {
                        int LOL = 1;
                    }
                    """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        "" +
          "src/test/pkg/IntDefTest.kt:6: Error: Must be one of: TestType.LOL [WrongConstant]\n" +
          "        wantInt(100) // ERROR\n" +
          "                ~~~\n" +
          "src/test/pkg/IntDefTest.kt:7: Error: Must be one of: TestType.LOL [WrongConstant]\n" +
          "        wantInt(WrongType.NO) // ERROR\n" +
          "                ~~~~~~~~~~~~\n" +
          "src/test/pkg/IntDefTest.kt:8: Error: Must be one of: TestType.LOL [WrongConstant]\n" +
          "        wantInt(giveRandomInt()) // ERROR\n" +
          "                ~~~~~~~~~~~~~~~\n" +
          "src/test/pkg/IntDefTest.kt:9: Error: Must be one of: TestType.LOL [WrongConstant]\n" +
          "        wantInt(giveWrongInt()) //ERROR\n" +
          "                ~~~~~~~~~~~~~~\n" +
          "src/test/pkg/IntDefTest.kt:10: Error: Must be one of: TestType.LOL [WrongConstant]\n" +
          "        wantInt(giveWrongIntAnnotated()) //ERROR\n" +
          "                ~~~~~~~~~~~~~~~~~~~~~~~\n" +
          "5 errors, 0 warnings"
      )
      .expectFixDiffs(
        """
        Fix for src/test/pkg/IntDefTest.kt line 6: Change to TestType.LOL:
        @@ -6 +6
        -         wantInt(100) // ERROR
        +         wantInt(TestType.LOL) // ERROR
        Fix for src/test/pkg/IntDefTest.kt line 7: Change to TestType.LOL:
        @@ -7 +7
        -         wantInt(WrongType.NO) // ERROR
        +         wantInt(TestType.LOL) // ERROR
        Fix for src/test/pkg/IntDefTest.kt line 8: Change to TestType.LOL:
        @@ -8 +8
        -         wantInt(giveRandomInt()) // ERROR
        +         wantInt(TestType.LOL) // ERROR
        Fix for src/test/pkg/IntDefTest.kt line 9: Change to TestType.LOL:
        @@ -9 +9
        -         wantInt(giveWrongInt()) //ERROR
        +         wantInt(TestType.LOL) //ERROR
        Fix for src/test/pkg/IntDefTest.kt line 10: Change to TestType.LOL:
        @@ -10 +10
        -         wantInt(giveWrongIntAnnotated()) //ERROR
        +         wantInt(TestType.LOL) //ERROR
        """
      )
  }

  fun testStringDefInitialization() {
    // Regression test for https://issuetracker.google.com/72756166
    // 72756166: AGP 3.1-beta1 StringDef Lint Error
    lint()
      .files(
        java(
            """
                    package test.pkg;

                    import androidx.annotation.StringDef;

                    import java.lang.annotation.Documented;
                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;

                    @SuppressWarnings("ClassNameDiffersFromFileName")
                    public class StringDefTest {

                        @MyTypeDef
                        public static final String FOO = "foo";

                        @StringDef({FOO})
                        @Retention(RetentionPolicy.SOURCE)
                        @Documented
                        public @interface MyTypeDef {
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

  fun testOpenStringDef() {
    // Regression test for https://issuetracker.google.com/72756166
    // 117529548: MediaMetadataCompat.Builder does not support custom keys
    lint()
      .files(
        java(
            """
                package test.pkg;

                import androidx.annotation.StringDef;

                import java.lang.annotation.Documented;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class StringDefTest2 {

                    public void method(@MyTypeDef String param) {
                    }

                    public void test() {
                        method(FOO); // OK
                        method("bar"); // OK
                    }

                    @StringDef(value = {FOO}, open = true)
                    @Retention(RetentionPolicy.SOURCE)
                    @Documented
                    public @interface MyTypeDef {
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

  fun testOpenStringDef2() {
    // 117529548: MediaMetadataCompat.Builder does not support custom keys
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.support.v4.media.MediaMetadataCompat;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class MediaBuilderTest {
                    public void test() {
                        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
                        builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, "something"); // OK
                        builder.putString("custom-key", "something"); // OK
                        builder.putLong("custom-key", 0L); // OK
                    }
                }

                """
          )
          .indented(),
        java(
            """
                package android.support.v4.media;
                import androidx.annotation.StringDef;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public final class MediaMetadataCompat {
                    public static final String METADATA_KEY_TITLE = "android.media.metadata.TITLE";
                    public static class Builder {
                        public void putString(@TextKey String key, String value) {
                        }
                        public void putLong(@TextKey String key, long value) {
                        }
                    }

                    @StringDef({METADATA_KEY_TITLE})
                    @Retention(RetentionPolicy.SOURCE)
                    public @interface TextKey {}

                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun test75993782() {
    // Regression test for https://issuetracker.google.com/75993782
    // Ensure that we handle finding typedef constants defined in Kotlin
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import androidx.annotation.IntDef

                interface Foo {
                    fun bar(@DetailInfoTab tab: Int = CONST_1)

                    @IntDef(CONST_1, CONST_2, CONST_3)
                    @Retention(AnnotationRetention.SOURCE)
                    annotation class DetailInfoTab

                    companion object {
                        const val CONST_1 = -1
                        const val CONST_2 = 2
                        const val CONST_3 = 0

                        fun foobar(foo: Foo) {
                            foo.bar(CONST_1)
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

  fun testIntDefValuesFromCompanionObject() {
    // Ensure that we handle finding typedef constants defined in Kotlin
    // Similar to https://youtrack.jetbrains.com/issue/KT-61497
    // but from simplified code snippet from one of androidx modules (b/322837849)
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import androidx.annotation.IntDef

                class TableInfo {

                    @Retention(AnnotationRetention.SOURCE)
                    @IntDef(value = [CREATED_FROM_UNKNOWN, CREATED_FROM_ENTITY, CREATED_FROM_DATABASE])
                    internal annotation class CreatedFrom()

                    companion object {
                        const val CREATED_FROM_UNKNOWN = 0
                        const val CREATED_FROM_ENTITY = 1
                        const val CREATED_FROM_DATABASE = 2
                    }

                    class Column(
                        val name: String,
                        @CreatedFrom
                        val createdFrom: Int,
                    ) {
                        constructor(name: String) : this(CREATED_FROM_UNKNOWN)
                    }
                }

                private fun readColumns(
                    tableName: String,
                ): Map<String, TableInfo.Column> {
                    return buildMap {
                        while(true) {
                            put(
                                key = tableName,
                                value = TableInfo.Column(tableName, TableInfo.CREATED_FROM_DATABASE)
                            )
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

  fun test119753493() {
    // Regression test for
    // 119753493: False positive for WrongConstant after AndroidX Migration
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Context;

                import androidx.recyclerview.widget.LinearLayoutManager;

                public class WrongConstantTest {
                    static class MyLayoutManager extends LinearLayoutManager {

                        public MyLayoutManager(Context context) {
                            super(context);
                        }

                        public boolean isVertical() {
                            return getOrientation() == VERTICAL;
                        }
                    }
                }
                """
          )
          .indented(),

        // Binary version of class file. To reproduce
        // this bug we need to use bytecode, not source resolution
        // (since we want to verify what happens with field declarations
        // where the initializer is not present.)
        bytecode(
          "libs/recyclerview.jar",
          java(
              """
                package androidx.recyclerview.widget;

                import android.content.Context;

                public class LinearLayoutManager {
                    // Simulate classfile presence of these constants, where we can't
                    // look at the right hand side (initialization)
                    public static final int HORIZONTAL = RecyclerView.HORIZONTAL;
                    public static final int VERTICAL = RecyclerView.VERTICAL;

                    public LinearLayoutManager(Context context) {
                    }

                    @RecyclerView.Orientation
                    int mOrientation = RecyclerView.DEFAULT_ORIENTATION;

                    @RecyclerView.Orientation
                    public int getOrientation() {
                        return mOrientation;
                    }
                }
                """
            )
            .indented(),
          0x1a10084a,
          "androidx/recyclerview/widget/LinearLayoutManager.class:" +
            "H4sIAAAAAAAAAH1Qy0rDQBQ907SNTdOH1kfrY+Gu7cK4c6EIpSgWYgu1ZOFu" +
            "mg5lSjqBOH39kxtBEVz4AX6UeBOKKIibe+Yc5txzOR+fb+8AznBgYsdCBrs5" +
            "pLFnomqixmDd9Pqd+1530HIZWIeh0A7Vg+ZKezyYCYOsYNjwrvqDTrvlxpwx" +
            "2NNeJIXSXMtQMWQvpJL6kuGw7nI1ikI5cvxQafrhtGNc6vOGx5BuhyPBUHKl" +
            "Et3ZdCiiAR8GpBTHQv/aaNQbdIt1F84iX1zL+E81dvHI5atwpm+54mMRnUz4" +
            "nDM01qFLJxL+yg9ENJdi4SzkiPY6/bXmkWajgH0bNkyG039df6QxlOM8J+Bq" +
            "7PSGE+Hr5FIPx1RphqoxUEMWZtwYsRRyxK0fPE+TspM3VY0izRKxJvEUodV8" +
            "RaqZfoHxlPRepllEXHqeEoq0zyZ1E1tr3xEhI8w0n2E8fluyiVihWUmitr8A" +
            "fb+LPAUCAAA=",
        ),
        bytecode(
          "libs/recyclerview.jar",
          java(
              """
                package androidx.recyclerview.widget;

                import android.widget.LinearLayout;

                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                import androidx.annotation.IntDef;
                import androidx.annotation.RestrictTo;

                import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

                public class RecyclerView {
                    /** @hide */
                    @RestrictTo(LIBRARY_GROUP)
                    @IntDef({HORIZONTAL, VERTICAL})
                    @Retention(RetentionPolicy.SOURCE)
                    public @interface Orientation {}

                    public static final int HORIZONTAL = LinearLayout.HORIZONTAL;
                    public static final int VERTICAL = LinearLayout.VERTICAL;

                    static final int DEFAULT_ORIENTATION = VERTICAL;
                }
                """
            )
            .indented(),
          0x1f852758,
          "androidx/recyclerview/widget/RecyclerView.class:" +
            "H4sIAAAAAAAAAI2QzU7CQBSFz/BXKCAg/oDGhYkx4sKujBtjQhBik9omWFm4" +
            "MUOZkCF1mrQF5LFcmbjwAXwo422DCe7c3Lnnzpxvbs7X98cngCscatjTsK8j" +
            "h5aGtoYDhrITSqFiHstAMVRMpUTY83kUiYhBv3OG5pNju12LgZkM1V6gopir" +
            "eMT9ucgSFQzFUX/omr2ulWjG0LztD7qPlvtM5j55XdOxGQrXUsn4hiF71hkx" +
            "5HrBRDDULKmEPX8Zi9DlY58m+kMwDz0xkIloDIW38nwRjqRYXsz4gtPGtoji" +
            "e5FYaMVDriZhICfGUk6mIjYSHg8tvgrmMUNnfftqhGvQgkC/bzfhFdRQZ6gn" +
            "fxg+V1PDGc+ER4zLfzNONrLEMTKUM5BFG0WUKCedVAZl0pUNXUULW380RZ0s" +
            "Q7VBk6NUA/nzd2Tf0sC3qRbSYZXwzdS2g106czTNQ6NOT7sMCqelH1wLg3sA" +
            "AgAA",
          "androidx/recyclerview/widget/RecyclerView\$Orientation.class:" +
            "H4sIAAAAAAAAAJVQW0sCQRT+xrL1Wl66mUE9mBFR+xQ9+JJJF0FS1hSih1jX" +
            "QUa2GdkdNf9aD/2AflR0FMkEIRs4cy7zne+c+T6/3j8AXGLfwKaBbQM7DJG6" +
            "6nsOvxUuZ0ha3Bk5Lveagg/Pu/bAZshYfanFK28KX7RcXpRSaVsLJX2Gw8oY" +
            "Y7q27Jj2z4Npcc3lOCowBAe22yfq/B/QmnKFM6KGtXq1YZVuGLLTwWU5WDD6" +
            "uGLLtqdE2/T7vZ7y9Dyrrz3h6EdVMJChJeqO6tESsbKU3Cu5tu9z4jhbjiM3" +
            "6abV4pXytVW0nl7urGqjxhB6IMy98rWBPYaLKdmb6U1VHJCK5lC0O1ybv5XN" +
            "VT1Bv57MYYjOZYmZTNVWlzua4WChcjM1DGQZTv/xFYaTpXdlOFqKOc/AECBb" +
            "wfgEsUp3gPwa+TjVDbIQRxgRRKmUoDQ2Lj2DccqSSE0a0tggn6Iojl2sX2UI" +
            "k8ZWPvwNltmVBL4CAAA=",
        ),
        jar(
          "annotations.zip",
          xml(
            "androidx/recyclerview/widget/annotations.xml",
            "" +
              "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
              "<root>\n" +
              "  <item name=\"androidx.recyclerview.widget.LinearLayoutManager int getOrientation()\">\n" +
              "    <annotation name=\"androidx.annotation.IntDef\">\n" +
              "      <val name=\"value\" val=\"{androidx.recyclerview.widget.RecyclerView.HORIZONTAL, androidx.recyclerview.widget.RecyclerView.VERTICAL}\" />\n" +
              "    </annotation>\n" +
              "  </item>\n" +
              "  <item name=\"androidx.recyclerview.widget.LinearLayoutManager mOrientation\">\n" +
              "    <annotation name=\"androidx.annotation.IntDef\">\n" +
              "      <val name=\"value\" val=\"{androidx.recyclerview.widget.RecyclerView.HORIZONTAL, androidx.recyclerview.widget.RecyclerView.VERTICAL}\" />\n" +
              "    </annotation>\n" +
              "  </item>\n" +
              "  <item name=\"androidx.recyclerview.widget.LinearLayoutManager void setOrientation(int) 0\">\n" +
              "    <annotation name=\"androidx.annotation.IntDef\">\n" +
              "      <val name=\"value\" val=\"{androidx.recyclerview.widget.RecyclerView.HORIZONTAL, androidx.recyclerview.widget.RecyclerView.VERTICAL}\" />\n" +
              "    </annotation>\n" +
              "  </item>\n" +
              "</root>\n",
          ),
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun testReturnWithinLambda() {
    // Regression test for https://issuetracker.google.com/140626689
    lint()
      .files(
        kotlin(
            """
                @file:Suppress("UNUSED_PARAMETER")
                package test.pkg
                import android.app.Service
                import android.content.Intent
                import android.os.Parcelable
                abstract class UpdateService : Service() {
                    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
                        val config = intent.getParcelableExtra<UpdateConfig>(KEY_CONFIG)
                        if (config == null) {
                            Timber.warn { "No config present in intent." }
                            return START_NOT_STICKY
                        }
                        return START_REDELIVER_INTENT
                    }
                }
                // Stubs
                abstract class UpdateConfig: Parcelable
                const val KEY_CONFIG = "config"
                class Timber {
                    companion object {
                        fun isLoggable(priority: Int, throwable: Throwable? = null): Boolean = false
                        fun rawLog(priority: Int, throwable: Throwable? = null, throwable2: Throwable? = null, message: String) {
                        }
                        inline fun warn(throwable: Throwable? = null, message: () -> String) {
                            log(0, throwable, message)
                        }
                        inline fun log(priority: Int, throwable: Throwable? = null, message: () -> String) {
                            if (isLoggable(priority, null)) {
                                rawLog(priority, null, throwable, message())
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

  fun test80166964() {
    // 80166964: IntDef annotation for variable not working anymore
    lint()
      .files(
        java(
            """
                package test.pkg.myapplication;

                import androidx.annotation.IntDef;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                @SuppressWarnings({"WeakerAccess", "unused", "ClassNameDiffersFromFileName"})
                public class IntDefTest {

                    public static final int LINE = 0;
                    public static final int CORNER = 1;

                    @IntDef({LINE, CORNER})
                    @Retention(RetentionPolicy.SOURCE)
                    public @interface ShapeTypes {}

                    @ShapeTypes public int shapeType;

                    public void test(IntDefTest myClassObj) {
                        shapeType = 99;
                        myClassObj.shapeType = 99;
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
            src/test/pkg/myapplication/IntDefTest.java:20: Error: Must be one of: IntDefTest.LINE, IntDefTest.CORNER [WrongConstant]
                    shapeType = 99;
                                ~~
            src/test/pkg/myapplication/IntDefTest.java:21: Error: Must be one of: IntDefTest.LINE, IntDefTest.CORNER [WrongConstant]
                    myClassObj.shapeType = 99;
                                           ~~
            2 errors, 0 warnings
            """
      )
      .expectFixDiffs(
        """
            Fix for src/test/pkg/myapplication/IntDefTest.java line 20: Change to IntDefTest.LINE:
            @@ -20 +20
            -         shapeType = 99;
            +         shapeType = IntDefTest.LINE;
            Fix for src/test/pkg/myapplication/IntDefTest.java line 20: Change to IntDefTest.CORNER:
            @@ -20 +20
            -         shapeType = 99;
            +         shapeType = IntDefTest.CORNER;
            Fix for src/test/pkg/myapplication/IntDefTest.java line 21: Change to IntDefTest.LINE:
            @@ -21 +21
            -         myClassObj.shapeType = 99;
            +         myClassObj.shapeType = IntDefTest.LINE;
            Fix for src/test/pkg/myapplication/IntDefTest.java line 21: Change to IntDefTest.CORNER:
            @@ -21 +21
            -         myClassObj.shapeType = 99;
            +         myClassObj.shapeType = IntDefTest.CORNER;
            """
      )
  }

  fun testZeroAlias() {
    val task =
      lint()
        .files(
          java(
              """
                package test.pkg;

                import android.app.PendingIntent;
                import android.content.Context;
                import android.content.Intent;

                public class Test {
                    public static final int UNRELATED = 0;
                    public void test(Intent intent, Context context) {
                        PendingIntent.getActivity(context, 0, intent,
                                PendingIntent.FLAG_IMMUTABLE |
                                Intent.FILL_IN_SELECTOR |
                                PendingIntent.FLAG_MUTABLE |
                                Test.UNRELATED, null);
                    }
                }
                """
            )
            .indented()
        )
        .run()

    try {
      // Correct string as of API level 31. When lint runs from Bazel, it's still picking up API
      // level 30 (because
      // //prebuilts/studio/sdk:platforms/latest still points to android-30), but when running from
      // Studio, since
      // the prebuilts folder actually contains android-31, and the test doesn't specify a
      // compileSdkVersion, it
      // will pick the latest, and in android-31 there is one more allowed constant.
      task.expect(
        """
                src/test/pkg/Test.java:14: Error: Must be one or more of: PendingIntent.FLAG_ONE_SHOT, PendingIntent.FLAG_NO_CREATE, PendingIntent.FLAG_CANCEL_CURRENT, PendingIntent.FLAG_UPDATE_CURRENT, PendingIntent.FLAG_IMMUTABLE, PendingIntent.FLAG_MUTABLE, PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT, Intent.FILL_IN_ACTION, Intent.FILL_IN_DATA, Intent.FILL_IN_CATEGORIES, Intent.FILL_IN_COMPONENT, Intent.FILL_IN_PACKAGE, Intent.FILL_IN_SOURCE_BOUNDS, Intent.FILL_IN_SELECTOR, Intent.FILL_IN_CLIP_DATA [WrongConstant]
                                Test.UNRELATED, null);
                                ~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
      )
    } catch (failure: ComparisonFailure) {
      // This can be deleted once we're using android-31 everywhere.
      task.expect(
        """
                src/test/pkg/Test.java:14: Error: Must be one or more of: PendingIntent.FLAG_ONE_SHOT, PendingIntent.FLAG_NO_CREATE, PendingIntent.FLAG_CANCEL_CURRENT, PendingIntent.FLAG_UPDATE_CURRENT, PendingIntent.FLAG_IMMUTABLE, Intent.FILL_IN_ACTION, Intent.FILL_IN_DATA, Intent.FILL_IN_CATEGORIES, Intent.FILL_IN_COMPONENT, Intent.FILL_IN_PACKAGE, Intent.FILL_IN_SOURCE_BOUNDS, Intent.FILL_IN_SELECTOR, Intent.FILL_IN_CLIP_DATA [WrongConstant]
                                Test.UNRELATED, null);
                                ~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """
      )
    }
  }

  fun test73783847() {
    lint()
      .files(
        java(
            """
                package com.example.android.linttest;

                import androidx.annotation.StringDef;

                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                @Retention(RetentionPolicy.SOURCE)
                @StringDef(value = {
                    FragmentNames.HOME
                })
                public @interface FragmentName {}
                """
          )
          .indented(),
        java(
            """
                package com.example.android.linttest;

                public class FragmentNames {
                    public static final String HOME = "HOME";
                }
                """
          )
          .indented(),
        java(
            """
                package com.example.android.linttest;

                public class FragmentUtils {

                    public static String getSomeTextFromOtherClass() {
                        return "some text";
                    }
                }
                """
          )
          .indented(),
        java(
            """
                package com.example.android.linttest;

                import android.app.Activity;
                import android.os.Bundle;
                import android.widget.Toast;

                public class MainActivity extends Activity {

                    @Override
                    protected void onResume() {
                        super.onResume();

                        toastFragmentNameAndText(FragmentNames.HOME, getSomeTextFromThisClass()); // OK
                        toastFragmentNameAndText(FragmentNames.HOME, FragmentUtils.getSomeTextFromOtherClass()); // OK
                        toastFragmentNameAndText(getSomeTextFromThisClass(), FragmentNames.HOME); // ERROR
                        toastFragmentNameAndText(FragmentUtils.getSomeTextFromOtherClass(), FragmentNames.HOME); // ERROR
                    }

                    private void toastFragmentNameAndText(@FragmentName String fragmentTag, String text) {
                        Toast.makeText(this, fragmentTag, Toast.LENGTH_LONG).show();
                        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
                    }

                    public static String getSomeTextFromThisClass() {
                        return "some text";
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
            src/com/example/android/linttest/MainActivity.java:15: Error: Must be one of: FragmentNames.HOME [WrongConstant]
                    toastFragmentNameAndText(getSomeTextFromThisClass(), FragmentNames.HOME); // ERROR
                                             ~~~~~~~~~~~~~~~~~~~~~~~~~~
            src/com/example/android/linttest/MainActivity.java:16: Error: Must be one of: FragmentNames.HOME [WrongConstant]
                    toastFragmentNameAndText(FragmentUtils.getSomeTextFromOtherClass(), FragmentNames.HOME); // ERROR
                                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
      )
      .expectFixDiffs(
        """
        Fix for src/com/example/android/linttest/MainActivity.java line 15: Change to FragmentNames.HOME:
        @@ -15 +15
        -         toastFragmentNameAndText(getSomeTextFromThisClass(), FragmentNames.HOME); // ERROR
        +         toastFragmentNameAndText(FragmentNames.HOME, FragmentNames.HOME); // ERROR
        Fix for src/com/example/android/linttest/MainActivity.java line 16: Change to FragmentNames.HOME:
        @@ -16 +16
        -         toastFragmentNameAndText(FragmentUtils.getSomeTextFromOtherClass(), FragmentNames.HOME); // ERROR
        +         toastFragmentNameAndText(FragmentNames.HOME, FragmentNames.HOME); // ERROR
        """
      )
  }

  fun testQuickfix() {
    lint()
      .files(
        java(
            """
                package test.pkg;
                import android.app.AlarmManager;
                import android.app.PendingIntent;

                public class ExactAlarmTest {
                    public void test(AlarmManager alarmManager, PendingIntent operation) {
                        alarmManager.setExact(Integer.MAX_VALUE, 0L, operation);
                    }
                }
                """
          )
          .indented(),
        kotlin(
            """
                package test.pkg

                import android.app.PendingIntent

                fun test(alarmManager: android.app.AlarmManager, operation: PendingIntent?) {
                    alarmManager.setExact(1, 0L, operation)
                }
                """
          )
          .indented(),
      )
      .allowNonAlphabeticalFixOrder(true)
      .run()
      .expect(
        """
            src/test/pkg/ExactAlarmTest.java:7: Error: Must be one of: AlarmManager.RTC_WAKEUP, AlarmManager.RTC, AlarmManager.ELAPSED_REALTIME_WAKEUP, AlarmManager.ELAPSED_REALTIME [WrongConstant]
                    alarmManager.setExact(Integer.MAX_VALUE, 0L, operation);
                                          ~~~~~~~~~~~~~~~~~
            src/test/pkg/test.kt:6: Error: Must be one of: AlarmManager.RTC_WAKEUP, AlarmManager.RTC, AlarmManager.ELAPSED_REALTIME_WAKEUP, AlarmManager.ELAPSED_REALTIME [WrongConstant]
                alarmManager.setExact(1, 0L, operation)
                                      ~
            2 errors, 0 warnings
            """
      )
      .expectFixDiffs(
        """
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
            """
      )
  }

  fun testListDifference() {
    // See b//174571734#comment9 for repro: this is extracted from a failure found in AndroidX
    // running :camera:integration-tests:camera-testapp-extensions:lintDebug
    lint()
      .files(
        java(
          """
                package androidx.camera.view;

                import androidx.annotation.IntDef;
                import androidx.camera.core.AspectRatio;
                import androidx.camera.core.impl.ImageOutputConfig;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                @SuppressWarnings({"unused", "FieldCanBeLocal", "FieldMayBeFinal"})
                public class CameraController {
                    private void setTargetOutputSize(ImageOutputConfig.Builder<?> builder,
                                                     OutputSize outputSize) {
                        builder.setTargetAspectRatio(outputSize.getAspectRatio()); // ERROR
                        if (outputSize.getAspectRatio() != OutputSize.UNASSIGNED_ASPECT_RATIO) {
                            builder.setTargetAspectRatio(outputSize.getAspectRatio()); // OK
                        }
                    }

                    public static class OutputSize {
                        public static final int UNASSIGNED_ASPECT_RATIO = -1;

                        @Retention(RetentionPolicy.SOURCE)
                        @IntDef(value = {UNASSIGNED_ASPECT_RATIO, AspectRatio.RATIO_4_3, AspectRatio.RATIO_16_9})
                        public @interface OutputAspectRatio {
                        }

                        @OutputAspectRatio
                        private int mAspectRatio = UNASSIGNED_ASPECT_RATIO;

                        @OutputAspectRatio
                        public int getAspectRatio() {
                            return mAspectRatio;
                        }
                    }
                }
                """
        ),
        java(
          """
                package androidx.camera.core;

                import androidx.annotation.IntDef;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                public class AspectRatio {
                    public static final int RATIO_4_3 = 0;
                    public static final int RATIO_16_9 = 1;

                    private AspectRatio() {
                    }

                    @IntDef({RATIO_4_3, RATIO_16_9})
                    @Retention(RetentionPolicy.SOURCE)
                    public @interface Ratio {
                    }
                }
                """
        ),
        java(
          """
                package androidx.camera.core.impl;

                import androidx.camera.core.AspectRatio;

                @SuppressWarnings("UnusedReturnValue")
                public interface ImageOutputConfig {
                    interface Builder<B> {
                        B setTargetAspectRatio(@AspectRatio.Ratio int aspectRatio);
                    }
                }
                """
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
            src/androidx/camera/view/CameraController.java:14: Error: Must be one of: AspectRatio.RATIO_4_3, AspectRatio.RATIO_16_9, but could be OutputSize.UNASSIGNED_ASPECT_RATIO [WrongConstant]
                                    builder.setTargetAspectRatio(outputSize.getAspectRatio()); // ERROR
                                                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
      )
      .expectFixDiffs(
        """
        Fix for src/androidx/camera/view/CameraController.java line 14: Change to AspectRatio.RATIO_4_3:
        @@ -14 +14
        -                         builder.setTargetAspectRatio(outputSize.getAspectRatio()); // ERROR
        +                         builder.setTargetAspectRatio(AspectRatio.RATIO_4_3); // ERROR
        Fix for src/androidx/camera/view/CameraController.java line 14: Change to AspectRatio.RATIO_16_9:
        @@ -14 +14
        -                         builder.setTargetAspectRatio(outputSize.getAspectRatio()); // ERROR
        +                         builder.setTargetAspectRatio(AspectRatio.RATIO_16_9); // ERROR
        """
      )
  }

  fun test() {
    lint()
      .files(
        java(
            """
                package androidx.camera.camera2.internal.compat.params;

                import android.hardware.camera2.params.SessionConfiguration;
                import android.os.Build;

                import androidx.annotation.IntDef;
                import androidx.annotation.RequiresApi;
                import androidx.camera.camera2.internal.compat.CameraDeviceCompat;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                @SuppressWarnings("unused")
                @RequiresApi(api = Build.VERSION_CODES.P)
                public class SessionConfigurationCompat {
                    public static final int SESSION_REGULAR = CameraDeviceCompat.SESSION_OPERATION_MODE_NORMAL;
                    public static final int SESSION_HIGH_SPEED =
                            CameraDeviceCompat.SESSION_OPERATION_MODE_CONSTRAINED_HIGH_SPEED;

                    private static final class SessionConfigurationCompatApi28Impl implements
                            SessionConfigurationCompatImpl {

                        private final SessionConfiguration mObject;

                        private SessionConfigurationCompatApi28Impl(SessionConfiguration mObject) {
                            this.mObject = mObject;
                        }

                        @Override
                        public int getSessionType() {
                            return mObject.getSessionType();
                        }
                    }

                    @Retention(RetentionPolicy.SOURCE)
                    @IntDef(value = {SESSION_REGULAR, SESSION_HIGH_SPEED})
                    public @interface SessionMode {
                    }

                    private interface SessionConfigurationCompatImpl {
                        @SessionMode
                        int getSessionType();
                    }
                }
                """
          )
          .indented(),
        java(
            """
                package androidx.camera.camera2.internal.compat;

                public class CameraDeviceCompat {
                    public static final int SESSION_OPERATION_MODE_NORMAL =
                            0; // ICameraDeviceUser.NORMAL_MODE;
                    public static final int SESSION_OPERATION_MODE_CONSTRAINED_HIGH_SPEED =
                            1; // ICameraDeviceUser.CONSTRAINED_HIGH_SPEED_MODE;

                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun test208002049() {
    // Regression test for https://issuetracker.google.com/208002049
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.graphics.Paint;
                import android.graphics.Typeface;

                public class StyleTest {
                    private static void apply(Paint paint, String family) {
                        int oldStyle;

                        Typeface old = paint.getTypeface();
                        if (old == null) {
                            oldStyle = 0;
                        } else {
                            oldStyle = old.getStyle();
                        }

                        Typeface tf = Typeface.create(family, oldStyle);
                    }
                }
                """
          )
          .indented()
      )
      .testModes(TestMode.Companion.DEFAULT)
      .run()
      .expectClean()
  }

  fun test210507429() {
    // 210507429: Linter incorrectly asserts `android.content.ContextWrapper#checkCallingPermission`
    // should take in
    //            PackageManager.PERMISSION_GRANTED or PackageManager.PERMISSION_DENIED
    lint()
      .files(
        kotlin(
          """
                package test.api

                import android.Manifest.permission.ACCEPT_HANDOVER
                import android.Manifest.permission.CAMERA
                import android.content.pm.PackageManager.PERMISSION_DENIED
                import android.content.pm.PackageManager.PERMISSION_GRANTED
                import androidx.annotation.CheckResult
                import androidx.annotation.IntDef
                import androidx.annotation.StringDef

                class ParameterTest {
                    fun test() {
                        val permissionResult = checkCallingPermission(CAMERA)
                    }

                    @CheckResult
                    @PermissionResult
                    fun checkCallingPermission(@PermissionName name: String): Int = TODO()

                    @IntDef(value = [PERMISSION_GRANTED, PERMISSION_DENIED])
                    @Retention(AnnotationRetention.SOURCE)
                    annotation class PermissionResult

                    @StringDef(value = [CAMERA, ACCEPT_HANDOVER])
                    @Retention(AnnotationRetention.SOURCE)
                    annotation class PermissionName
                }
                """
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun test167750517() {
    // Make sure we handle specifically allowed constants as well
    // 167750517: @IntDef doesn't support negative values?
    lint()
      .files(
        kotlin(
            """
                import androidx.annotation.IntDef

                @IntDef(1, 0, -1, 42)
                @Retention(AnnotationRetention.SOURCE)
                annotation class Thing

                @Thing const val MINUS_ONE = -1
                @Thing const val ZERO = 0
                @Thing const val ONE = 1
                @Thing const val ANSWER = 42
                @Thing const val HUNDRED = 100

                fun thingConsumer(@Thing t: Int) {
                }

                fun test(b: Boolean, i: Int?) {
                  thingConsumer(if (b) 200 else 300)
                  thingConsumer(i ?: 3_000)
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      // Intended to test if/else expressions
      .skipTestModes(TestMode.BODY_REMOVAL, TestMode.IF_TO_WHEN, TestMode.JVM_OVERLOADS)
      .run()
      .expect(
        """
            src/Thing.kt:11: Error: Must be one of: 1, 0, -1, 42 [WrongConstant]
            @Thing const val HUNDRED = 100
                                       ~~~
            src/Thing.kt:17: Error: Must be one of: 1, 0, -1, 42 [WrongConstant]
              thingConsumer(if (b) 200 else 300)
                                   ~~~
            src/Thing.kt:17: Error: Must be one of: 1, 0, -1, 42 [WrongConstant]
              thingConsumer(if (b) 200 else 300)
                                            ~~~
            src/Thing.kt:18: Error: Must be one of: 1, 0, -1, 42 [WrongConstant]
              thingConsumer(i ?: 3_000)
                                 ~~~~~
            4 errors, 0 warnings
            """
      )
  }

  fun test298283135() {
    lint()
      .files(
        java(
            """
            package test.pkg;

            import androidx.annotation.IntDef;

            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;

            @IntDef({NotificationAction.SHOWN, NotificationAction.COMPLETE,
                        NotificationAction.CANCEL_PRESSED, NotificationAction.DISMISSED})
            @Retention(RetentionPolicy.SOURCE)
            public @interface NotificationAction {
                int SHOWN = 0;
                int COMPLETE = 1;
                int CANCEL_PRESSED = 2;
                int DISMISSED = 3;
                int TAPPED = 4;

                int NUM_ENTRIES = 5;
            }

            class Test {
                public void test() {
                    System.out.println(NotificationAction.NUM_ENTRIES); // OK
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

  fun testTolerateConstantChangesInBaselines() {
    // The set of constants typically evolve over time; try to tolerate these changes.
    // The common scenario is that new constants are matched, so allow skipping extra
    // constants in the new message
    // (This might be a good one to move over to TypedefDetector!
    val baseline = LintBaseline(ToolsBaseTestLintClient(), File(""))
    assertTrue(
      baseline.sameMessage(
        TypedefDetector.TYPE_DEF,
        "Must be one of: IntDefTest.STYLE_NORMAL, IntDefTest.NEW_CONSTANT, IntDefTest.NEW_CONSTANT_2, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT",
        "Must be one of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT",
      )
    )

    // Don't match unrelated items (this is the case if the new constants is not a super set of the
    // old ones
    assertFalse(
      baseline.sameMessage(
        TypedefDetector.TYPE_DEF,
        "Must be one of: IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT",
        "Must be one of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT",
      )
    )

    assertFalse(
      baseline.sameMessage(
        TypedefDetector.TYPE_DEF,
        "Flag not allowed here",
        "Must be one of: IntDefTest.STYLE_NORMAL, IntDefTest.STYLE_NO_TITLE, IntDefTest.STYLE_NO_FRAME, IntDefTest.STYLE_NO_INPUT",
      )
    )
  }

  fun testParameterCompatibility() {
    // Regression test for b/343519613
    lint()
      .files(
        java(
            """
            package test.pkg;

            import androidx.annotation.IntDef;

            public abstract class Playground {
                @IntDef({FirstIntDef.CONST_0})
                public @interface FirstIntDef {
                    int CONST_0 = 0;
                }

                @IntDef({SecondIntDef.ANOTHER_0})
                public @interface SecondIntDef {
                    int ANOTHER_0 = 0;
                }

                public abstract int test(@FirstIntDef int first, @SecondIntDef int second);

                public int swappedIndirection(@FirstIntDef int first, @SecondIntDef int second) {
                    test(first, second); // OK
                    return test(
                            second, // ERROR 1
                            first   // ERROR 2
                    );
                }

                public int conditionalIfUsage(@FirstIntDef int first, @SecondIntDef int second) {
                    if (safe(first) || safe(second)) {
                      test(second, first); // safe because it's been guarded in some way
                    }
                }

                public int reassignment(@FirstIntDef int first, @SecondIntDef int second, int something) {
                    first = something;
                    if (something == 3) {
                      second = something;
                    }
                    test(second, first); // safe because the reassignment means annotation on variable may not apply
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
        src/test/pkg/Playground.java:21: Error: Must be one of: FirstIntDef.CONST_0 [WrongConstant]
                        second, // ERROR 1
                        ~~~~~~
        src/test/pkg/Playground.java:22: Error: Must be one of: SecondIntDef.ANOTHER_0 [WrongConstant]
                        first   // ERROR 2
                        ~~~~~
        2 errors, 0 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for src/test/pkg/Playground.java line 21: Change to FirstIntDef.CONST_0:
        @@ -21 +21
        -                 second, // ERROR 1
        +                 Playground.FirstIntDef.CONST_0, // ERROR 1
        Fix for src/test/pkg/Playground.java line 22: Change to SecondIntDef.ANOTHER_0:
        @@ -22 +22
        -                 first   // ERROR 2
        +                 Playground.SecondIntDef.ANOTHER_0   // ERROR 2
        """
      )
  }

  fun testVariableChecked() {
    lint()
      .files(
        java(
            // Extracted from AndroidX'
            // camera/camera-camera2/src/main/java/androidx/camera/camera2/internal/compat/CameraAccessExceptionCompat.java
            """
            package test.pkg;

            import android.hardware.camera2.CameraAccessException;

            import androidx.annotation.IntDef;
            import androidx.annotation.RestrictTo;

            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.util.Arrays;
            import java.util.Collections;
            import java.util.HashSet;
            import java.util.Set;

            public class CameraAccessExceptionCompat {
                public static final int CAMERA_IN_USE = 4;
                public static final int MAX_CAMERAS_IN_USE = 5;
                public static final int CAMERA_DISABLED = 1;
                public static final int CAMERA_DISCONNECTED = 2;
                public static final int CAMERA_ERROR = 3;
                static final Set<Integer> PLATFORM_ERRORS =
                        Collections.unmodifiableSet(new HashSet<>(Arrays.asList(CAMERA_IN_USE,
                                MAX_CAMERAS_IN_USE, CAMERA_DISABLED, CAMERA_DISCONNECTED, CAMERA_ERROR)));
                public static final int CAMERA_UNAVAILABLE_DO_NOT_DISTURB = 10001;
                public static final int CAMERA_CHARACTERISTICS_CREATION_ERROR = 10002;

                private final CameraAccessException mCameraAccessException;

                @RestrictTo(RestrictTo.Scope.LIBRARY)
                @Retention(RetentionPolicy.SOURCE)
                @IntDef(value = {
                        CAMERA_IN_USE,
                        MAX_CAMERAS_IN_USE,
                        CAMERA_DISABLED,
                        CAMERA_DISCONNECTED,
                        CAMERA_ERROR,
                        CAMERA_UNAVAILABLE_DO_NOT_DISTURB,
                        CAMERA_CHARACTERISTICS_CREATION_ERROR
                })
                public @interface AccessError {
                }

                public CameraAccessExceptionCompat(@AccessError int reason) {
                    mCameraAccessException = PLATFORM_ERRORS.contains(reason)
                            ? new CameraAccessException(reason) : null;
                }
            }

            /*
                In the platform, CameraAccessException(String) is defined like this:
                @Retention(RetentionPolicy.SOURCE)
                @IntDef(prefix = { "CAMERA_", "MAX_CAMERAS_IN_USE" }, value = {
                        CAMERA_IN_USE,
                        MAX_CAMERAS_IN_USE,
                        CAMERA_DISABLED,
                        CAMERA_DISCONNECTED,
                        CAMERA_ERROR
                })
                public @interface AccessError {}

                public CameraAccessException(@AccessError int problem) { ... }

                The above code is safe since we're filtering on PLATFORM_ERRORS.contains, but
                this is hard for lint to check... This means we need to make sure there's
                no "checking" of the value if we're going to assert this!
             */
            """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun testSwitchCheck() {
    lint()
      .files(
        java(
            // Extracted from AndroidX'
            // appcompat/appcompat/src/main/java/androidx/appcompat/app/AppCompatDelegateImpl.java
            """
            package test.pkg;

            import androidx.annotation.IntDef;

            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;

            public class ReturnCheck {
                public static final int MODE_NIGHT_FOLLOW_SYSTEM = -1;
                public static final int MODE_NIGHT_AUTO_TIME = 0;
                public static final int MODE_NIGHT_NO = 1;
                public static final int MODE_NIGHT_YES = 2;
                public static final int MODE_NIGHT_AUTO_BATTERY = 3;
                public static final int MODE_NIGHT_UNSPECIFIED = -100;

                @IntDef({MODE_NIGHT_NO, MODE_NIGHT_YES, MODE_NIGHT_AUTO_TIME, MODE_NIGHT_FOLLOW_SYSTEM,
                        MODE_NIGHT_UNSPECIFIED, MODE_NIGHT_AUTO_BATTERY})
                @Retention(RetentionPolicy.SOURCE)
                public @interface NightMode {}

                @IntDef({MODE_NIGHT_NO, MODE_NIGHT_YES, MODE_NIGHT_FOLLOW_SYSTEM})
                @Retention(RetentionPolicy.SOURCE)
                @interface ApplyableNightMode {}

                @ApplyableNightMode
                int mapNightMode(@NightMode final int mode) {
                    switch (mode) {
                        case MODE_NIGHT_NO:
                        case MODE_NIGHT_YES:
                        case MODE_NIGHT_FOLLOW_SYSTEM:
                            // FALLTHROUGH since these are all valid modes to return
                            return mode; // OK
                        default: throw new IllegalStateException("Error");
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

  fun testToLong_useSite() {
    // b/352609562 and b/364261817
    lint()
      .files(
        kotlin(
          """
            import androidx.annotation.LongDef

            const val CONST_1 = 1
            const val CONST_2 = 4096
            const val UNRELATED = -1

            @LongDef(CONST_1, CONST_2)
            @Retention(AnnotationRetention.SOURCE)
            annotation class DetailInfoTab

            fun test(@DetailInfoTab tab: Long) {
            }

            fun test() {
                test(CONST_1.toLong()) // OK
                test(UNRELATED.toLong()) // ERROR - not part of the @DetailsInfoTab list

                with(CONST_2.toLong()) {
                  test(toLong()) // OK
                }
                with(UNRELATED.toLong()) {
                  test(toLong()) // TODO?
                }
            }
          """
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
src/DetailInfoTab.kt:17: Error: Must be one of: DetailInfoTabKt.CONST_1, DetailInfoTabKt.CONST_2 [WrongConstant]
                test(UNRELATED.toLong()) // ERROR - not part of the @DetailsInfoTab list
                     ~~~~~~~~~
1 errors, 0 warnings
        """
      )
  }

  fun testToLong_declarationSite() {
    // b/367752734
    lint()
      .files(
        kotlin(
          """
            import androidx.annotation.LongDef

            const val CONST_1 = 1
            const val CONST_2 = 4096
            const val UNRELATED = -1

            @LongDef(CONST_1.toLong(), CONST_2.toLong())
            @Retention(AnnotationRetention.SOURCE)
            annotation class DetailInfoTab

            fun test(@DetailInfoTab tab: Long) {
            }

            fun test() {
                test(CONST_1) // OK
                test(UNRELATED) // ERROR - not part of the @DetailsInfoTab list

                with(CONST_2) {
                  test(toLong()) // OK
                }
                with(UNRELATED) {
                  test(toLong()) // TODO?
                }
            }
          """
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .skipTestModes(TestMode.JVM_OVERLOADS)
      .run()
      .expect(
        """
        src/DetailInfoTab.kt:17: Error: Must be one of: CONST_1.toLong(), CONST_2.toLong() [WrongConstant]
                        test(UNRELATED) // ERROR - not part of the @DetailsInfoTab list
                             ~~~~~~~~~
        1 errors, 0 warnings
        """
      )
  }

  fun testToLong_both() {
    // b/367752734
    lint()
      .files(
        kotlin(
          """
            import androidx.annotation.LongDef

            const val CONST_1 = 1
            const val CONST_2 = 4096
            const val UNRELATED = -1

            @LongDef(CONST_1.toLong(), CONST_2.toLong())
            @Retention(AnnotationRetention.SOURCE)
            annotation class DetailInfoTab

            fun test(@DetailInfoTab tab: Long) {
            }

            fun test() {
                test(CONST_1.toLong()) // OK
                test(UNRELATED.toLong()) // ERROR - not part of the @DetailsInfoTab list

                with(CONST_2.toLong()) {
                  test(toLong()) // OK
                }
                with(UNRELATED.toLong()) {
                  test(toLong()) // TODO?
                }
            }
          """
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
src/DetailInfoTab.kt:17: Error: Must be one of: CONST_1.toLong(), CONST_2.toLong() [WrongConstant]
                test(UNRELATED.toLong()) // ERROR - not part of the @DetailsInfoTab list
                     ~~~~~~~~~
1 errors, 0 warnings
        """
      )
  }

  fun testFlagInv() {
    // b/364261817
    lint()
      .files(
        java(
          """
            package my.pkg;
            import androidx.annotation.IntDef;

            public class MyIntent {
              @Retention(RetentionPolicy.SOURCE)
              @IntDef(flag = true, value = {
                FLAG_1, FLAG_2,
              })
              public @interface Flags {}

              public static final int UNRELATED = 0;
              public static final int FLAG_1 = 1;
              public static final int FLAG_1 = 2;

              private int mFlag;
              public @Flags int getFlags() {
                return mFlags;
              }
              public MyIntent setFlags(@Flags int flag) {
                mFlag = flag;
                return this;
              }
            }
          """
        ),
        kotlin(
          """
            import my.pkg.MyIntent
            fun MyIntent.stripUnwantedFlags() {
              flags = flags and MyIntent.FLAG_1.inv() // OK
              flags = flags and MyIntent.UNRELATED.inv() // ERROR 1
              flags = flags.and(MyIntent.UNRELATED.inv()) // ERROR 2
            }

            fun test(i: MyIntent, j: MyIntent?) {
              i.flags = MyIntent.FLAG_1 or i.flags // OK
              i.flags = MyIntent.UNRELATED.or(i.flags) // ERROR 3
              i.flags = i.flags.and(MyIntent.FLAG_2) // OK
              i.flags = i.flags xor MyIntent.UNRELATED // ERROR 4
              i.flags = i.flags.xor(MyIntent.UNRELATED) // ERROR 5
              i.flags = MyIntent.UNRELATED or MyIntent.UNRELATED // ERROR 6
              i.flags = MyIntent.UNRELATED.or(MyIntent.UNRELATED) // ERROR 7
              j?.flags = j?.flags?.or(MyIntent.FLAG_1 or MyIntent.FLAG_2) // OK
              j?.flags = j?.flags?.or(MyIntent.UNRELATED or MyIntent.UNRELATED) // ERROR 8
            }
          """
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .skipTestModes(TestMode.PARENTHESIZED)
      .run()
      .expect(
        """
        src/test.kt:5: Error: Must be one or more of: MyIntent.FLAG_1, FLAG_2 [WrongConstant]
                      flags = flags and MyIntent.UNRELATED.inv() // ERROR 1
                                        ~~~~~~~~~~~~~~~~~~
        src/test.kt:6: Error: Must be one or more of: MyIntent.FLAG_1, FLAG_2 [WrongConstant]
                      flags = flags.and(MyIntent.UNRELATED.inv()) // ERROR 2
                                        ~~~~~~~~~~~~~~~~~~
        src/test.kt:11: Error: Must be one or more of: MyIntent.FLAG_1, FLAG_2 [WrongConstant]
                      i.flags = MyIntent.UNRELATED.or(i.flags) // ERROR 3
                                ~~~~~~~~~~~~~~~~~~
        src/test.kt:13: Error: Must be one or more of: MyIntent.FLAG_1, FLAG_2 [WrongConstant]
                      i.flags = i.flags xor MyIntent.UNRELATED // ERROR 4
                                            ~~~~~~~~~~~~~~~~~~
        src/test.kt:14: Error: Must be one or more of: MyIntent.FLAG_1, FLAG_2 [WrongConstant]
                      i.flags = i.flags.xor(MyIntent.UNRELATED) // ERROR 5
                                            ~~~~~~~~~~~~~~~~~~
        src/test.kt:15: Error: Must be one or more of: MyIntent.FLAG_1, FLAG_2 [WrongConstant]
                      i.flags = MyIntent.UNRELATED or MyIntent.UNRELATED // ERROR 6
                                ~~~~~~~~~~~~~~~~~~
        src/test.kt:15: Error: Must be one or more of: MyIntent.FLAG_1, FLAG_2 [WrongConstant]
                      i.flags = MyIntent.UNRELATED or MyIntent.UNRELATED // ERROR 6
                                                      ~~~~~~~~~~~~~~~~~~
        src/test.kt:16: Error: Must be one or more of: MyIntent.FLAG_1, FLAG_2 [WrongConstant]
                      i.flags = MyIntent.UNRELATED.or(MyIntent.UNRELATED) // ERROR 7
                                ~~~~~~~~~~~~~~~~~~
        src/test.kt:16: Error: Must be one or more of: MyIntent.FLAG_1, FLAG_2 [WrongConstant]
                      i.flags = MyIntent.UNRELATED.or(MyIntent.UNRELATED) // ERROR 7
                                                      ~~~~~~~~~~~~~~~~~~
        src/test.kt:18: Error: Must be one or more of: MyIntent.FLAG_1, FLAG_2 [WrongConstant]
                      j?.flags = j?.flags?.or(MyIntent.UNRELATED or MyIntent.UNRELATED) // ERROR 8
                                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        10 errors, 0 warnings
        """
      )
  }

  fun testDoubleUseSitesAndDoubleDeclarationSiteViolations() {
    // Only 0 is allowed, so both 1s in 1 << 1 are violations.
    // Then there are intentionally two use-sites that refer to
    // an operation with those two violations.
    // b/370778975: If we report that at the use-site,
    //  two violation reports collide in that use-site.
    // b/378128668: If we report that at the declaration-site,
    //  double use-sites trigger duplicate errors.
    lint()
      .files(
        java(
            """
            import androidx.annotation.IntDef;

            public class TestFlags {
                // declaration-site?
                public static final int SHIFT_FLAG = 1 << 1;

                @IntDef(flag = true, value = { 0 })
                public @interface FlagDef {}

                // use-site?
                @FlagDef int flags1 = SHIFT_FLAG;
                @FlagDef int flags2 = SHIFT_FLAG;
            }
          """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
src/TestFlags.java:11: Error: Must be one or more of: 0 [WrongConstant]
    @FlagDef int flags1 = SHIFT_FLAG;
                          ~~~~~~~~~~
src/TestFlags.java:12: Error: Must be one or more of: 0 [WrongConstant]
    @FlagDef int flags2 = SHIFT_FLAG;
                          ~~~~~~~~~~
2 errors, 0 warnings
        """
      )
  }

  fun testConstructorDelegation_differentConstants() {
    // b/373506497
    lint()
      .files(
        java(
            """
            import androidx.annotation.IntDef;
            public class Autocomplete {
              public abstract static class SelectionOption<T> {
                @IntDef(
                  flag = true,
                  value = {Hint.NONE, Hint.GENERATED, Hint.INSECURE_FORM}
                )
                public @interface SelectOptionHint {}

                public static class Hint {
                  public static final int NONE = 0;
                  public static final int GENERATED = 1 << 0;
                  public static final int INSECURE_FORM = 1 << 1;
                }

                public SelectionOption(final T value, final @SelectOptionHint int hint) {
                }
              }

              public static class CreditCardSelectOption extends SelectionOption<String> {
                @IntDef(
                  flag = true,
                  value = {Hint.NONE, Hint.INSECURE_FORM}
                )
                public @interface CreditCardSelectHint {}

                public static class Hint {
                  public static final int NONE = 0;
                  public static final int INSECURE_FORM = 1 << 1;
                }

                CreditCardSelectOption(
                    final String value, final @CreditCardSelectHint int hint) {
                  super(value, hint); // ERROR
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
src/Autocomplete.java:34: Error: Must be one or more of: Hint.NONE, Hint.GENERATED, Hint.INSECURE_FORM, but could be Hint.NONE, Hint.INSECURE_FORM [WrongConstant]
      super(value, hint); // ERROR
                   ~~~~
1 errors, 0 warnings
        """
      )
  }

  fun testConstructorDelegation_sharedConstant() {
    // b/373506497
    lint()
      .files(
        java(
            """
            import androidx.annotation.IntDef;
            public class Autocomplete {
              public static class Hint {
                public static final int NONE = 0;
                public static final int GENERATED = 1 << 0;
                public static final int INSECURE_FORM = 1 << 1;
              }

              public abstract static class SelectionOption<T> {
                @IntDef(
                  flag = true,
                  value = {Hint.NONE, Hint.GENERATED, Hint.INSECURE_FORM}
                )
                public @interface SelectOptionHint {}

                public SelectionOption(final T value, final @SelectOptionHint int hint) {
                }
              }

              public static class CreditCardSelectOption extends SelectionOption<String> {
                @IntDef(
                  flag = true,
                  value = {Hint.NONE, Hint.INSECURE_FORM}
                )
                public @interface CreditCardSelectHint {}

                CreditCardSelectOption(
                    final String value, final @CreditCardSelectHint int hint) {
                  super(value, hint);
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
}
