/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.lint.checks.InteroperabilityDetector.Issues.KOTLIN_PROPERTY
import com.android.tools.lint.checks.InteroperabilityDetector.Issues.PLATFORM_NULLNESS
import com.android.tools.lint.detector.api.Detector

class InteroperabilityDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector {
    return InteroperabilityDetector()
  }

  fun testKeywords() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class Test {
                    public void fun() { }
                    public void foo(int fun, int internalName) { }
                    public Object object = null;
                }
                """
          )
          .indented(),
        java(
            """
                package test.pkg;

                import org.json.JSONException;
                import org.json.JSONStringer;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                 public class Keywords extends JSONStringer {
                    // Using Kotlin hard keyword, but can't be helped; overrides library name
                    @Override
                    public JSONStringer object() throws JSONException {
                        return super.object();
                    }
                }
                """
          )
          .indented(),
      )
      .issues(InteroperabilityDetector.NO_HARD_KOTLIN_KEYWORDS)
      .run()
      .expect(
        """
            src/test/pkg/Test.java:5: Warning: Avoid method names that are Kotlin hard keywords ("fun"); see https://android.github.io/kotlin-guides/interop.html#no-hard-keywords [NoHardKeywords]
                public void fun() { }
                            ~~~
            src/test/pkg/Test.java:7: Warning: Avoid field names that are Kotlin hard keywords ("object"); see https://android.github.io/kotlin-guides/interop.html#no-hard-keywords [NoHardKeywords]
                public Object object = null;
                              ~~~~~~
            0 errors, 2 warnings
            """
      )
  }

  fun testLambdaLast() {
    lint()
      .files(
        java(
            """
                package test.pkg;
                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class Test {
                    public void ok1() { }
                    public void ok1(int x) { }
                    public void ok2(int x, int y) { }
                    public void ok3(Runnable run) { }
                    public void ok4(int x, Runnable run) { }
                    public void ok5(Runnable run1, Runnable run2) { }
                    public void ok6(java.util.List list, boolean b) { }
                    public void error1(Runnable run, int x) { }
                    public void error2(SamInterface sam, int x) { }

                    public interface SamInterface {
                        void samMethod();
                        @Override String toString();
                        default void other() {  }
                    }
                }
                """
          )
          .indented(),
        kotlin(
            """
                    package test.pkg

                    fun ok1(bar: (Int) -> Int) { }
                    fun ok2(foo: Int) { }
                    fun ok3(foo: Int, bar: (Int) -> Int) { }
                    fun ok4(foo: Int, bar: (Int) -> Int, baz: (Int) -> Int) { }
                    // Lamda not last, but we're not flagging issues in Kotlin files for the
                    // interoperability issue
                    fun error(bar: (Int) -> Int, foo: Int) { }
                """
          )
          .indented(),
      )
      .issues(InteroperabilityDetector.LAMBDA_LAST)
      .run()
      .expect(
        """
            src/test/pkg/Test.java:11: Warning: Functional interface parameters (such as parameter 1, "run", in test.pkg.Test.error1) should be last to improve Kotlin interoperability; see https://kotlinlang.org/docs/reference/java-interop.html#sam-conversions [LambdaLast]
                public void error1(Runnable run, int x) { }
                                                 ~~~~~
            src/test/pkg/Test.java:12: Warning: Functional interface parameters (such as parameter 1, "sam", in test.pkg.Test.error2) should be last to improve Kotlin interoperability; see https://kotlinlang.org/docs/reference/java-interop.html#sam-conversions [LambdaLast]
                public void error2(SamInterface sam, int x) { }
                                                     ~~~~~
            0 errors, 2 warnings
            """
      )
  }

  fun testLambdaLast2() {
    // Regression test for https://issuetracker.google.com/135275901
    lint()
      .files(
        java(
            """
                package test.pkg;

                import java.util.concurrent.Executor;

                public class LambdaLastTest {
                    public void registerCallback(Executor executor, Callback callback) {
                    }
                }

                class Callback {
                    public void action() {
                    }
                }
                """
          )
          .indented()
      )
      .issues(InteroperabilityDetector.LAMBDA_LAST)
      .run()
      .expectClean()
  }

  fun testInheritedMethods() {
    // 213362704: Skip flagging overridden methods
    // For both lambda-should-be-last and name-is-kotlin-keyword, don't flag APIs that
    // are inherited where you don't have the ability to rename it anyway.
    lint()
      .files(
        java(
            """
                package test.pkg;
                @SuppressWarnings("LambdaLast")
                public class Parent {
                    public void error1(Runnable run, int x) { } // Suppressed, don't flag
                    public void error2(SamInterface sam, int x) { } // Suppressed, don't flag
                    public void fun() { } // Suppressed, don't flag

                    public interface SamInterface {
                        void samMethod();
                        @Override String toString();
                        default void other() {  }
                    }
                }
                """
          )
          .indented(),
        java(
            """
                package test.pkg;
                public class Child extends Parent {
                    @Override public void error1(Runnable run, int x) { } // Override, don't flag
                    @Override public void error2(SamInterface sam, int x) { } // Override, don't flag
                    @Override public void fun() { } // Override, don't flag
                }
                """
          )
          .indented(),
      )
      .issues(InteroperabilityDetector.LAMBDA_LAST)
      .run()
      .expectClean()
  }

  fun testNullness() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import androidx.annotation.NonNull;
                import androidx.annotation.Nullable;
                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class Test {
                    public void ok(int x, float y, boolean z) { }
                    @Nullable public Object ok2(@NonNull Integer i, @NonNull int[] array) { return null; }
                    private Object ok3(Integer i) { return null; }
                    public Object error1(Integer error2, int[] error3) { return null; }
                    @NonNull public Float ok4 = 5;
                    @NonNull protected Float ok5 = 5;
                    private Float ok6 = 5;
                    public Float error4;
                    /** Field comment */
                    public Float error5;
                    /** Method comment */
                    public Object error6() { return null; }
                    protected Float error7;

                    // Don't flag public methods and fields in non-public classes or
                    // in anonymous inner classes
                    @SuppressWarnings("ResultOfObjectAllocationIgnored")
                    class Inner {
                        public void ok(Integer i) {
                            new Runnable() {
                                @Override public void run() {
                                }
                                public void ok2(Integer i) {  }
                            };
                        }
                    }
                    @Deprecated
                    public Float error8;
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .issues(PLATFORM_NULLNESS)
      .run()
      .expect(
        """
            src/test/pkg/Test.java:10: Warning: Unknown nullability; explicitly declare as @Nullable or @NonNull to improve Kotlin interoperability; see https://developer.android.com/kotlin/interop#nullability_annotations [UnknownNullness]
                public Object error1(Integer error2, int[] error3) { return null; }
                       ~~~~~~
            src/test/pkg/Test.java:10: Warning: Unknown nullability; explicitly declare as @Nullable or @NonNull to improve Kotlin interoperability; see https://developer.android.com/kotlin/interop#nullability_annotations [UnknownNullness]
                public Object error1(Integer error2, int[] error3) { return null; }
                                     ~~~~~~~
            src/test/pkg/Test.java:10: Warning: Unknown nullability; explicitly declare as @Nullable or @NonNull to improve Kotlin interoperability; see https://developer.android.com/kotlin/interop#nullability_annotations [UnknownNullness]
                public Object error1(Integer error2, int[] error3) { return null; }
                                                     ~~~~~
            src/test/pkg/Test.java:14: Warning: Unknown nullability; explicitly declare as @Nullable or @NonNull to improve Kotlin interoperability; see https://developer.android.com/kotlin/interop#nullability_annotations [UnknownNullness]
                public Float error4;
                       ~~~~~
            src/test/pkg/Test.java:16: Warning: Unknown nullability; explicitly declare as @Nullable or @NonNull to improve Kotlin interoperability; see https://developer.android.com/kotlin/interop#nullability_annotations [UnknownNullness]
                public Float error5;
                       ~~~~~
            src/test/pkg/Test.java:18: Warning: Unknown nullability; explicitly declare as @Nullable or @NonNull to improve Kotlin interoperability; see https://developer.android.com/kotlin/interop#nullability_annotations [UnknownNullness]
                public Object error6() { return null; }
                       ~~~~~~
            src/test/pkg/Test.java:19: Warning: Unknown nullability; explicitly declare as @Nullable or @NonNull to improve Kotlin interoperability; see https://developer.android.com/kotlin/interop#nullability_annotations [UnknownNullness]
                protected Float error7;
                          ~~~~~
            src/test/pkg/Test.java:34: Warning: Unknown nullability; explicitly declare as @Nullable or @NonNull to improve Kotlin interoperability; see https://developer.android.com/kotlin/interop#nullability_annotations [UnknownNullness]
                public Float error8;
                       ~~~~~
            0 errors, 8 warnings
            """
      )
      .expectFixDiffs(
        // The unit testing infrastructure doesn't support shortening identifiers so
        // here we see fully qualified annotations inserted; in the IDE, the annotations
        // would get imported at the top of the compilation unit and shortened names
        // used here
        """
            Fix for src/test/pkg/Test.java line 10: Annotate @NonNull:
            @@ -10 +10
            -     public Object error1(Integer error2, int[] error3) { return null; }
            +     @NonNull public Object error1(Integer error2, int[] error3) { return null; }
            Fix for src/test/pkg/Test.java line 10: Annotate @Nullable:
            @@ -10 +10
            -     public Object error1(Integer error2, int[] error3) { return null; }
            +     @Nullable public Object error1(Integer error2, int[] error3) { return null; }
            Fix for src/test/pkg/Test.java line 10: Annotate @NonNull:
            @@ -10 +10
            -     public Object error1(Integer error2, int[] error3) { return null; }
            +     public Object error1(@NonNull Integer error2, int[] error3) { return null; }
            Fix for src/test/pkg/Test.java line 10: Annotate @Nullable:
            @@ -10 +10
            -     public Object error1(Integer error2, int[] error3) { return null; }
            +     public Object error1(@Nullable Integer error2, int[] error3) { return null; }
            Fix for src/test/pkg/Test.java line 10: Annotate @NonNull:
            @@ -10 +10
            -     public Object error1(Integer error2, int[] error3) { return null; }
            +     public Object error1(Integer error2, @NonNull int[] error3) { return null; }
            Fix for src/test/pkg/Test.java line 10: Annotate @Nullable:
            @@ -10 +10
            -     public Object error1(Integer error2, int[] error3) { return null; }
            +     public Object error1(Integer error2, @Nullable int[] error3) { return null; }
            Fix for src/test/pkg/Test.java line 14: Annotate @NonNull:
            @@ -14 +14
            -     public Float error4;
            +     @NonNull public Float error4;
            Fix for src/test/pkg/Test.java line 14: Annotate @Nullable:
            @@ -14 +14
            -     public Float error4;
            +     @Nullable public Float error4;
            Fix for src/test/pkg/Test.java line 16: Annotate @NonNull:
            @@ -16 +16
            -     public Float error5;
            +     @NonNull public Float error5;
            Fix for src/test/pkg/Test.java line 16: Annotate @Nullable:
            @@ -16 +16
            -     public Float error5;
            +     @Nullable public Float error5;
            Fix for src/test/pkg/Test.java line 18: Annotate @NonNull:
            @@ -18 +18
            -     public Object error6() { return null; }
            +     @NonNull public Object error6() { return null; }
            Fix for src/test/pkg/Test.java line 18: Annotate @Nullable:
            @@ -18 +18
            -     public Object error6() { return null; }
            +     @Nullable public Object error6() { return null; }
            Fix for src/test/pkg/Test.java line 19: Annotate @NonNull:
            @@ -19 +19
            -     protected Float error7;
            +     @NonNull protected Float error7;
            Fix for src/test/pkg/Test.java line 19: Annotate @Nullable:
            @@ -19 +19
            -     protected Float error7;
            +     @Nullable protected Float error7;
            Fix for src/test/pkg/Test.java line 34: Annotate @NonNull:
            @@ -33 +33
            -     @Deprecated
            +     @NonNull @Deprecated
            Fix for src/test/pkg/Test.java line 34: Annotate @Nullable:
            @@ -33 +33
            -     @Deprecated
            +     @Nullable @Deprecated
            """
      )
  }

  fun testNullnessWithoutDeprecatedElements() {
    // Normally everything is flagged, but via options (and envvars) you can
    // filter out deprecated elements; this tests that
    lint()
      .files(
        xml(
            "lint.xml",
            """
                <lint>
                    <issue id="UnknownNullness">
                        <option name="ignore-deprecated" value="true" />
                    </issue>
                </lint>
                """,
          )
          .indented(),
        xml(
            "src/other/pkg/lint.xml",
            """
                <lint>
                    <issue id="TooManyViews">
                        <option name="maxCount" value="20" />
                    </issue>
                </lint>
                """,
          )
          .indented(),
        java(
            """
                package other.pkg;

                import androidx.annotation.NonNull;
                import androidx.annotation.Nullable;
                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class Test2 {
                    @Deprecated
                    public Object error1(Integer error2, @Deprecated int[] error3) { return null; }
                    @Deprecated
                    public Float error4;
                    /** @deprecated */
                    public Float error5;
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .issues(PLATFORM_NULLNESS)
      .run()
      .expectClean()
  }

  fun testNullnessWithoutDeprecatedElementsOldTestDSL() {
    PLATFORM_NULLNESS.setEnabledByDefault(true)

    // Like testNullnessWithoutDeprecatedElements, but uses the old Lint Test API.
    // This is less a test of the detector and more a test of lint's test infrastructure;
    // all the old tests have been used, but this test verifies that things still work.
    @Suppress("DEPRECATION")
    assertEquals(
      "No warnings.",
      lintProject(
        xml(
            "lint.xml",
            """
                    <lint>
                        <issue id="UnknownNullness">
                            <option name="ignore-deprecated" value="true" />
                        </issue>
                    </lint>
                    """,
          )
          .indented(),
        xml(
            "src/other/pkg/lint.xml",
            """
                    <lint>
                        <issue id="TooManyViews">
                            <option name="maxCount" value="20" />
                        </issue>
                    </lint>
                    """,
          )
          .indented(),
        java(
            """
                    package other.pkg;
                    import androidx.annotation.NonNull;
                    import androidx.annotation.Nullable;
                    @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                    public class Test2 {
                        @Deprecated
                        public Object error1(Integer error2, @Deprecated int[] error3) { return null; }
                        @Deprecated
                        public Float error4;
                        /** @deprecated */
                        public Float error5;
                    }
                    """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      ),
    )

    PLATFORM_NULLNESS.setEnabledByDefault(false)
  }

  fun testTypeUseNullness() {
    lint()
      .files(
        java(
            """
              package test.annotation;
              import java.lang.annotation.ElementType.TYPE_USE;
              import java.lang.annotation.Target;
              @Target(TYPE_USE)
              public @interface NonNull {}
            """
          )
          .indented(),
        java(
            """
              package test.annotation;
              import java.lang.annotation.ElementType.TYPE_USE;
              import java.lang.annotation.Target;
              @Target(TYPE_USE)
              public @interface Nullable {}
            """
          )
          .indented(),
        java(
            """
              package test.pkg;
              import test.annotation.NonNull;
              import test.annotation.Nullable;
              public class Foo {
                  public java.lang.@NonNull String okString() { return ""; }
                  public java.util.@Nullable List<String> okList() { return null; }
                  public int @Nullable [] okArray() { return null; }
                  public String @Nullable [][] ok2dArray() { return null; }

                  public String errorString() { return ""; }
                  public java.util.List<@NonNull String> errorList() { return null; }
                  public int[] errorArray() { return null; }
                  public String[] @Nullable [] error2dArray() { return null; }
              }
            """
          )
          .indented(),
        java(
            """
              package test.pkg;
              import test.annotation.NonNull;
              import test.annotation.Nullable;
              public class OkToStringAndEquals {
                  public java.lang.@NonNull String toString() { return ""; }
                  public boolean equals(java.lang.@Nullable Object other) { return false; }
              }
            """
          )
          .indented(),
        java(
            """
              package test.pkg;
              import test.annotation.NonNull;
              import test.annotation.Nullable;
              public class ErrorToStringAndEquals {
                  public java.lang.@Nullable String toString() { return ""; }
                  public boolean equals(java.lang.@NonNull Object other) { return false; }
              }
            """
          )
          .indented(),
        kotlin(
            """
              package test.pkg
              fun propagatesNonNull(foo: Foo) = foo.okString() // OK
              fun propagatesNullable(foo: Foo) = foo.okList() // OK
              fun propagatesUnknown(foo: Foo) = foo.errorString() // ERROR
            """
          )
          .indented(),
        java(
            """
              package android.test;
              import test.annotation.NonNull;
              import test.annotation.Nullable;
              public class PlatformSuperClass {
                  public @Nullable String declaresReturnNullness() {
                      return null;
                  }
                  public void declaresParameterNullness(@NonNull String str) {
                  }
                  @SuppressWarnings("UnknownNullness")
                  public String noNullness(String str) {
                      return str;
                  }
              }
            """
          )
          .indented(),
        java(
            """
              package test.pkg;
              import android.test.PlatformSuperClass;
              public class ExtendsPlatformClass extends PlatformSuperClass {
                  @Override
                  public String declaresReturnNullness() { // ERROR
                      return null;
                  }
                  @Override
                  public void declaresParameterNullness(String str) {  // ERROR
                  }
                  @Override
                  public String noNullness(String str) { // OK
                      return str;
                  }
              }
            """
          )
          .indented(),
      )
      .issues(PLATFORM_NULLNESS)
      .run()
      .expect(
        """
          src/test/pkg/ErrorToStringAndEquals.java:5: Warning: Unexpected @Nullable: toString should never return null [UnknownNullness]
              public java.lang.@Nullable String toString() { return ""; }
                               ~~~~~~~~~
          src/test/pkg/ErrorToStringAndEquals.java:6: Warning: Unexpected @NonNull: The equals contract allows the parameter to be null [UnknownNullness]
              public boolean equals(java.lang.@NonNull Object other) { return false; }
                                              ~~~~~~~~
          src/test/pkg/ExtendsPlatformClass.java:5: Warning: Unknown nullability; explicitly declare as @Nullable or @NonNull to improve Kotlin interoperability; see https://developer.android.com/kotlin/interop#nullability_annotations [UnknownNullness]
              public String declaresReturnNullness() { // ERROR
                     ~~~~~~
          src/test/pkg/ExtendsPlatformClass.java:9: Warning: Unknown nullability; explicitly declare as @Nullable or @NonNull to improve Kotlin interoperability; see https://developer.android.com/kotlin/interop#nullability_annotations [UnknownNullness]
              public void declaresParameterNullness(String str) {  // ERROR
                                                    ~~~~~~
          src/test/pkg/Foo.java:10: Warning: Unknown nullability; explicitly declare as @Nullable or @NonNull to improve Kotlin interoperability; see https://developer.android.com/kotlin/interop#nullability_annotations [UnknownNullness]
              public String errorString() { return ""; }
                     ~~~~~~
          src/test/pkg/Foo.java:11: Warning: Unknown nullability; explicitly declare as @Nullable or @NonNull to improve Kotlin interoperability; see https://developer.android.com/kotlin/interop#nullability_annotations [UnknownNullness]
              public java.util.List<@NonNull String> errorList() { return null; }
                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/test/pkg/Foo.java:12: Warning: Unknown nullability; explicitly declare as @Nullable or @NonNull to improve Kotlin interoperability; see https://developer.android.com/kotlin/interop#nullability_annotations [UnknownNullness]
              public int[] errorArray() { return null; }
                     ~~~~~
          src/test/pkg/Foo.java:13: Warning: Unknown nullability; explicitly declare as @Nullable or @NonNull to improve Kotlin interoperability; see https://developer.android.com/kotlin/interop#nullability_annotations [UnknownNullness]
              public String[] @Nullable [] error2dArray() { return null; }
                     ~~~~~~~~~~~~~~~~~~~~~
          src/test/pkg/test.kt:4: Warning: Should explicitly declare type here since implicit type does not specify nullness [UnknownNullness]
          fun propagatesUnknown(foo: Foo) = foo.errorString() // ERROR
              ~~~~~~~~~~~~~~~~~
          0 errors, 9 warnings
        """
      )
      .expectFixDiffs(
        """
          Fix for src/test/pkg/ExtendsPlatformClass.java line 5: Annotate @NonNull:
          @@ -3 +3
          + import androidx.annotation.NonNull;
          @@ -4 +5
          -     @Override
          +     @NonNull @Override
          Fix for src/test/pkg/ExtendsPlatformClass.java line 5: Annotate @Nullable:
          @@ -3 +3
          + import androidx.annotation.Nullable;
          @@ -4 +5
          -     @Override
          +     @Nullable @Override
          Fix for src/test/pkg/ExtendsPlatformClass.java line 9: Annotate @NonNull:
          @@ -3 +3
          + import androidx.annotation.NonNull;
          @@ -9 +10
          -     public void declaresParameterNullness(String str) {  // ERROR
          +     public void declaresParameterNullness(@NonNull String str) {  // ERROR
          Fix for src/test/pkg/ExtendsPlatformClass.java line 9: Annotate @Nullable:
          @@ -3 +3
          + import androidx.annotation.Nullable;
          @@ -9 +10
          -     public void declaresParameterNullness(String str) {  // ERROR
          +     public void declaresParameterNullness(@Nullable String str) {  // ERROR
          Fix for src/test/pkg/Foo.java line 10: Annotate @NonNull:
          @@ -10 +10
          -     public String errorString() { return ""; }
          +     @androidx.annotation.NonNull public String errorString() { return ""; }
          Fix for src/test/pkg/Foo.java line 10: Annotate @Nullable:
          @@ -10 +10
          -     public String errorString() { return ""; }
          +     @androidx.annotation.Nullable public String errorString() { return ""; }
          Fix for src/test/pkg/Foo.java line 11: Annotate @NonNull:
          @@ -11 +11
          -     public java.util.List<@NonNull String> errorList() { return null; }
          +     @androidx.annotation.NonNull public java.util.List<@NonNull String> errorList() { return null; }
          Fix for src/test/pkg/Foo.java line 11: Annotate @Nullable:
          @@ -11 +11
          -     public java.util.List<@NonNull String> errorList() { return null; }
          +     @androidx.annotation.Nullable public java.util.List<@NonNull String> errorList() { return null; }
          Fix for src/test/pkg/Foo.java line 12: Annotate @NonNull:
          @@ -12 +12
          -     public int[] errorArray() { return null; }
          +     @androidx.annotation.NonNull public int[] errorArray() { return null; }
          Fix for src/test/pkg/Foo.java line 12: Annotate @Nullable:
          @@ -12 +12
          -     public int[] errorArray() { return null; }
          +     @androidx.annotation.Nullable public int[] errorArray() { return null; }
          Fix for src/test/pkg/Foo.java line 13: Annotate @NonNull:
          @@ -13 +13
          -     public String[] @Nullable [] error2dArray() { return null; }
          +     @androidx.annotation.NonNull public String[] @Nullable [] error2dArray() { return null; }
          Fix for src/test/pkg/Foo.java line 13: Annotate @Nullable:
          @@ -13 +13
          -     public String[] @Nullable [] error2dArray() { return null; }
          +     @androidx.annotation.Nullable public String[] @Nullable [] error2dArray() { return null; }
        """
      )
  }

  fun testPropertyAccess() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "unused", "MethodMayBeStatic", "NonBooleanMethodNameMayNotStartWithQuestion"})
                public class GetterSetter {
                    // Correct Java Bean - get-prefix
                    public void setOk1(String s) { }
                    public String getOk1() { return ""; }

                    // Correct Java Bean - is-prefix
                    public void setOk2(String s) {}
                    public String isOk2() { return ""; }

                    // This is a read-only bean but we don't interpret these
                    public String getOk2() { return ""; }

                    // This is *potentially* an incorrectly named read-only Java Bean but we don't flag these
                    public String hasOk3() { return ""; }

                    // This is a write-only Java Bean we but we don't flag these
                    public void setOk4(String s) { }

                    // Using "wrong" return type on the setter is fine, Kotlin doesn't care
                    public String setOk5(String s) { return s; }
                    public String getOk5() { return ""; }

                    // Now the errors

                    // Using "has" instead of is
                    public void setError1(String s) { }
                    public String hasError1() { return ""; }

                    // Using property name itself
                    public void setError2(String s) { }
                    public String error2() { return ""; }

                    // Using some other suffix
                    public void setError3(String s) { }
                    public String hazzError3() { return ""; }

                    // Mismatched getter and setter types
                    public void setError4(String s) { }
                    public Integer getError4() { return 0; }

                    // Wrong access modifier
                    public void setError5(String s) { }
                    protected String getError5() { return ""; }

                    // Wrong static
                    public void setError6(String s) { }
                    public static String getError6() { return ""; }

                    private class NonApi {
                        // Not valid java bean but we don't flag stuff in private classes
                        public String setOk1(String s) { return ""; }
                        public String getOk1() { return ""; }
                    }

                    public static class SuperClass {
                        public Number getNumber1() { return 0.0; }
                        public void setNumber1(Number number) { }
                        public Number getNumber2() { return 0.0; }
                        public void setNumber2(Number number) { }
                        public Number getNumber3() { return 0.0; }
                        public void setNumber3(Number number) { }
                    }

                    public static class SubClass extends SuperClass {
                        @Override public Float getNumber1() { return 0.0f; } // OK
                        @Override public void setNumber2(Number number) { } // OK
                        @Override public Float getNumber3() { return 0.0f; } // ERROR (even though we have corresponding setter)
                        public void setNumber3(Float number) { } // OK
                        public Float getNumber4() { return 0.0f; } // OK
                        public void setNumber4(Float number) { } // OK
                    }
                }
                """
          )
          .indented()
      )
      .issues(KOTLIN_PROPERTY)
      .run()
      .expect(
        """
            src/test/pkg/GetterSetter.java:30: Warning: This method should be called getError1 such that error1 can be accessed as a property from Kotlin; see https://android.github.io/kotlin-guides/interop.html#property-prefixes [KotlinPropertyAccess]
                public String hasError1() { return ""; }
                              ~~~~~~~~~
            src/test/pkg/GetterSetter.java:34: Warning: This method should be called getError2 such that error2 can be accessed as a property from Kotlin; see https://android.github.io/kotlin-guides/interop.html#property-prefixes [KotlinPropertyAccess]
                public String error2() { return ""; }
                              ~~~~~~
            src/test/pkg/GetterSetter.java:38: Warning: This method should be called getError3 such that error3 can be accessed as a property from Kotlin; see https://android.github.io/kotlin-guides/interop.html#property-prefixes [KotlinPropertyAccess]
                public String hazzError3() { return ""; }
                              ~~~~~~~~~~
            src/test/pkg/GetterSetter.java:42: Warning: The getter return type (Integer) and setter parameter type (String) getter and setter methods for property error4 should have exactly the same type to allow be accessed as a property from Kotlin; see https://android.github.io/kotlin-guides/interop.html#property-prefixes [KotlinPropertyAccess]
                public Integer getError4() { return 0; }
                               ~~~~~~~~~
                src/test/pkg/GetterSetter.java:41: Setter here
                public void setError4(String s) { }
                            ~~~~~~~~~
            src/test/pkg/GetterSetter.java:46: Warning: This getter should be public such that error5 can be accessed as a property from Kotlin; see https://android.github.io/kotlin-guides/interop.html#property-prefixes [KotlinPropertyAccess]
                protected String getError5() { return ""; }
                                 ~~~~~~~~~
            src/test/pkg/GetterSetter.java:50: Warning: This getter should not be static such that error6 can be accessed as a property from Kotlin; see https://android.github.io/kotlin-guides/interop.html#property-prefixes [KotlinPropertyAccess]
                public static String getError6() { return ""; }
                       ~~~~~~
            src/test/pkg/GetterSetter.java:70: Warning: The getter return type (Float) is not the same as the super return type (Number); they should have exactly the same type to allow number3 be accessed as a property from Kotlin; see https://android.github.io/kotlin-guides/interop.html#property-prefixes [KotlinPropertyAccess]
                    @Override public Float getNumber3() { return 0.0f; } // ERROR (even though we have corresponding setter)
                                           ~~~~~~~~~~
                src/test/pkg/GetterSetter.java:63: Super method here
                    public Number getNumber3() { return 0.0; }
                                  ~~~~~~~~~~
            0 errors, 7 warnings
            """
      )
  }

  fun testInflexibleGetter() {
    // Regression test for
    // 78097965: KotlinPropertyAccess lint rule wants me to change Activity.java
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.app.Activity;
                import androidx.annotation.NonNull;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                public class MyActivity extends Activity {
                    public void setTitle(@NonNull String title) {
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

  fun testNonPropertyAccess1() {
    // Regression test for
    // 78650191: KotlinPropertyAccess should ignore void methods when attempting to make matches
    lint()
      .files(
        java(
            """
                package test.pkg;

                import java.io.FileDescriptor;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class PropertyAccess1 {
                    public Object setDataSource(FileDescriptor fd) {
                        return null;
                    }

                    private void resetDataSource() {
                    }
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .issues(KOTLIN_PROPERTY)
      .run()
      .expectClean()
  }

  fun testNonPropertyAccess2() {
    // Regression test for
    // 78649678: KotlinPropertyAccess should ignore private methods when matching
    lint()
      .files(
        java(
            """
                package test.pkg;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class PropertyAccess2 {
                    private boolean hasName() { return false; }
                    public void setName(boolean name) {}
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .issues(KOTLIN_PROPERTY)
      .run()
      .expectClean()
  }

  fun testNonPropertyAccess3() {
    // Regression test for
    // 78644287: KotlinPropertyAccess not resolving/comparing generic type parameters correctly
    lint()
      .files(
        java(
            """
                package test.pkg;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class PropertyAccess3 {
                    class LoaderInfo<D> extends MutableLiveData<D> {
                        @Override
                        public void setValue(D value) { }
                    }

                    public class MutableLiveData<T> extends LiveData<T> {
                        @Override
                        public void setValue(T value) {
                        }
                    }

                    public abstract class LiveData<T> {
                        public T getValue() {
                            return null;
                        }
                    }
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .issues(KOTLIN_PROPERTY)
      .run()
      .expectClean()
  }

  fun testNonPropertyAccess4() {
    // Regression test for
    // 78632440: KotlinPropertyAccess false positive with overloaded setter
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.res.ColorStateList;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class PropertyAccess {
                    public void setCardBackgroundColor(int color) {
                    }

                    public void setCardBackgroundColor(ColorStateList color) {
                    }

                    public ColorStateList getCardBackgroundColor() {
                        return null;
                    }
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .issues(KOTLIN_PROPERTY)
      .run()
      .expectClean()
  }

  fun testNonPropertyAccess5() {
    // Regression test for
    // 80088526: KotlinPropertyAccess false positive with private getter
    lint()
      .files(
        java(
            """
                package test.pkg;

                import androidx.annotation.ColorRes;
                import androidx.annotation.VisibleForTesting;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class PropertyAccess {
                    public void setThumbColor(@ColorRes int color) {
                    }

                    private void setScrollbarThumbColor(@ColorRes int color) {
                    }

                    @VisibleForTesting
                    int getScrollbarThumbColor() {
                        return 0;
                    }
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .issues(KOTLIN_PROPERTY)
      .run()
      .expectClean()
  }

  fun testNonPropertyAccess6() {
    // Regression test for
    // 80092799: KotlinPropertyAccess false positive on framework method override
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.content.Context;
                import androidx.annotation.Nullable;
                import android.util.AttributeSet;
                import android.view.View;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class BaseGridView extends View {
                    private boolean mHasOverlappingRendering;

                    public PropAccessTest(Context context, @Nullable AttributeSet attributeSet, int i) {
                        super(context, attributeSet, i);
                    }

                    @Override
                    public boolean hasOverlappingRendering() {
                        return mHasOverlappingRendering;
                    }

                    public void setHasOverlappingRendering(boolean hasOverlapping) {
                        mHasOverlappingRendering = hasOverlapping;
                    }
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .issues(KOTLIN_PROPERTY)
      .run()
      .expectClean()
  }

  fun testNonPropertyAccess7() {
    // Regression test for
    // 80092906: KotlinPropertyAccess targeting getter which already has perfectly matching setter
    lint()
      .files(
        java(
            """
                    package test.pkg;

                    @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                    public class FullWidthDetailsOverviewRowPresenter {
                        public void setOnActionClickedListener(OnActionClickedListener listener) {
                        }

                        public OnActionClickedListener getOnActionClickedListener() {
                            return null;
                        }

                        public final void setListener(Listener listener) {
                        }


                        public interface OnActionClickedListener {
                            void onActionClicked();
                        }

                        public abstract static class Listener {
                            public void onBindLogo() {
                            }
                        }
                    }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .issues(KOTLIN_PROPERTY)
      .run()
      .expectClean()
  }

  fun testNonPropertyAccess8() {
    // Regression test for
    // 80092802: KotlinPropertyAccess should ignore constructors
    lint()
      .files(
        java(
            """
                    package test.pkg;

                    @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                    public class ItemBridgeAdapter {

                        public ItemBridgeAdapter() {
                        }

                        public void setAdapter(ObjectAdapter adapter) {
                        }

                        public class ObjectAdapter {
                        }
                    }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .issues(KOTLIN_PROPERTY)
      .run()
      .expectClean()
  }

  fun testNonPropertyAccess9() {
    // Regression test for
    // 80092804: KotlinPropertyAccess should prefer matching getters with the same type
    lint()
      .files(
        java(
            """
                    package test.pkg;

                    import android.content.SharedPreferences;
                    import android.preference.PreferenceScreen;

                    @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                    public class PreferenceManager {
                        public PreferenceManager() {
                        }

                        public SharedPreferences getSharedPreferences() {
                            return null;
                        }

                        public PreferenceScreen getPreferenceScreen() {
                            return null;
                        }

                        public boolean setPreferences(PreferenceScreen preferenceScreen) {
                        }
                    }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .issues(KOTLIN_PROPERTY)
      .run()
      .expectClean()
  }

  fun testNonPropertyAccess10() {
    // Regression test for
    // 80088529: KotlinPropertyAccess should suggest removing "is" from setter when is-er is present
    lint()
      .files(
        java(
            """
                    package test.pkg;

                    @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                    public class RecyclerView {
                        public final void setIsRecyclable(boolean recyclable) {
                        }

                        public final boolean isRecyclable() {
                            return false;
                        }
                    }

                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .issues(KOTLIN_PROPERTY)
      .run()
      .expect(
        """
            src/test/pkg/RecyclerView.java:5: Warning: This method should be called setRecyclable such that (along with the isRecyclable getter) Kotlin code can access it as a property (recyclable); see https://android.github.io/kotlin-guides/interop.html#property-prefixes [KotlinPropertyAccess]
                public final void setIsRecyclable(boolean recyclable) {
                                  ~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
      )
  }

  fun testEqualsAndToString1() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName"})
                public class NullnessTest {
                    @Override
                    public boolean equals(Object obj) {
                        return super.equals(obj);
                    }

                    @Override
                    public String toString() {
                        return super.toString();
                    }
                }
            """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .issues(PLATFORM_NULLNESS)
      .run()
      .expectClean()
  }

  fun testInitializedConstants() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName", "NonConstantFieldWithUpperCaseName"})
                public class NullnessTest {
                    public static final String MY_CONSTANT1 = "constant"; // Not nullable
                    public final String MY_CONSTANT2 = "constant"; // Not nullable
                    public String MY_CONSTANT3 = "constant"; // Unknown
                }
            """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .issues(PLATFORM_NULLNESS)
      .run()
      .expect(
        """
            src/test/pkg/NullnessTest.java:7: Warning: Unknown nullability; explicitly declare as @Nullable or @NonNull to improve Kotlin interoperability; see https://developer.android.com/kotlin/interop#nullability_annotations [UnknownNullness]
                public String MY_CONSTANT3 = "constant"; // Unknown
                       ~~~~~~
            0 errors, 1 warnings
            """
      )
  }

  fun testIncorrectNullnessAnnotations() {
    lint()
      .files(
        java(
            """
                package test.pkg;

                import androidx.annotation.NonNull;
                import androidx.annotation.Nullable;

                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName"})
                public class NullnessTest {
                    @Override
                    public boolean equals(@NonNull Object obj) {
                        return super.equals(obj);
                    }

                    @Nullable
                    @Override
                    public String toString() {
                        return super.toString();
                    }
                }
            """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .issues(PLATFORM_NULLNESS)
      .run()
      .expect(
        """
            src/test/pkg/NullnessTest.java:9: Warning: Unexpected @NonNull: The equals contract allows the parameter to be null [UnknownNullness]
                public boolean equals(@NonNull Object obj) {
                                      ~~~~~~~~
            src/test/pkg/NullnessTest.java:13: Warning: Unexpected @Nullable: toString should never return null [UnknownNullness]
                @Nullable
                ~~~~~~~~~
            0 errors, 2 warnings
            """
      )
  }

  fun testSkipDeprecated() {
    // Regression test for https://issuetracker.google.com/112126735
    val result =
      lint()
        .files(
          java(
              """
                package test.pkg;

                @SuppressWarnings({"unused", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
                public class DeprecatedNullnessTest {
                    @Deprecated
                    public Object error1() { return null; }

                    @Deprecated
                    public class Inner {
                        public void error2(Integer error2) { return null; }
                    }
                }
            """
            )
            .indented(),
          SUPPORT_ANNOTATIONS_JAR,
        )
        .issues(PLATFORM_NULLNESS)
        .run()
    if (InteroperabilityDetector.IGNORE_DEPRECATED) {
      result.expectClean()
    } else {
      result.expect(
        """
                src/test/pkg/DeprecatedNullnessTest.java:6: Warning: Unknown nullability; explicitly declare as @Nullable or @NonNull to improve Kotlin interoperability; see https://developer.android.com/kotlin/interop#nullability_annotations [UnknownNullness]
                    public Object error1() { return null; }
                           ~~~~~~
                src/test/pkg/DeprecatedNullnessTest.java:10: Warning: Unknown nullability; explicitly declare as @Nullable or @NonNull to improve Kotlin interoperability; see https://developer.android.com/kotlin/interop#nullability_annotations [UnknownNullness]
                        public void error2(Integer error2) { return null; }
                                           ~~~~~~~
                0 errors, 2 warnings
                """
      )
    }
  }

  fun testAnnotationMemberNonNull() {
    // Regression test for https://issuetracker.google.com/112185120
    lint()
      .files(
        // Don't flag annotation members as platform types
        java(
          """
                package test.pkg;
                @SuppressWarnings("ClassNameDiffersFromFileName")
                public @interface ClassType {
                    Class value();
                }
            """
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .issues(PLATFORM_NULLNESS)
      .run()
      .expectClean()
  }

  fun testPlatformPropagation() {
    // Regression test for https://issuetracker.google.com/134237547
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import java.util.concurrent.LinkedBlockingQueue
                import java.util.concurrent.TimeUnit

                class Foo(val requestQueue: LinkedBlockingQueue<String>) {
                    fun takeRequest(timeout: Long, unit: TimeUnit) = requestQueue.poll(timeout, unit) // ERROR
                    fun something() = listOf<String>("foo", "bar") // OK
                    fun takeRequestOk(timeout: Long, unit: TimeUnit): String = requestQueue.poll(timeout, unit) // OK
                    fun takeRequestOkTransitive(timeout: Long, unit: TimeUnit) = takeRequestOk(timeout, unit) // OK
                    val type = Integer.TYPE // ERROR
                    val typeClz: Class<Int> = Integer.TYPE // OK
                    val typeClz2 = typeClz // OK
                    fun ok() = Bar.getString() // OK
                }
                """
          )
          .indented(),
        java(
          """
                package test.pkg;

                public class Bar {
                    @androidx.annotation.NonNull
                    public static String getString() { return "hello"; }
                }
                """
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .issues(PLATFORM_NULLNESS)
      .run()
      .expect(
        """
            src/test/pkg/Foo.kt:7: Warning: Should explicitly declare type here since implicit type does not specify nullness [UnknownNullness]
                fun takeRequest(timeout: Long, unit: TimeUnit) = requestQueue.poll(timeout, unit) // ERROR
                    ~~~~~~~~~~~
            src/test/pkg/Foo.kt:11: Warning: Should explicitly declare type here since implicit type does not specify nullness [UnknownNullness]
                val type = Integer.TYPE // ERROR
                    ~~~~
            0 errors, 2 warnings
            """
      )
  }

  fun testPlatformPropagation2() {
    // Regression test for
    // 202559682: UnknownNullness check false positives on kotlin properties
    lint()
      .files(
        java(
            """
                package test.pkg;

                import androidx.annotation.NonNull;
                import androidx.annotation.Nullable;

                public class MyClass {
                    @NonNull
                    public static String nonnull() {
                        return "";
                    }
                    @Nullable
                    public static String nullable() {
                        return null;
                    }
                    public static String platform() { // ERROR 1
                        return null;
                    }
                }
                """
          )
          .indented(),
        java(
            """
                package test.pkg;
                import androidx.annotation.Nullable;
                public interface Answer<T> {
                    T answer(@Nullable String invocation) throws Throwable;
                }
                """
          )
          .indented(),
        kotlin(
            """
                package test.pkg

                fun kotlinNonNull() = MyClass.nonnull() // OK 1
                fun kotlinNullable() = MyClass.nullable() // OK 2
                fun kotlinPlatform(): String? = MyClass.platform() // OK 3
                fun kotlinPlatform2() = MyClass.platform() // ERROR 2
                fun kotlinNonNull2() = run { MyClass.nonnull() } // OK 4
                fun kotlinPlatform3() = run { MyClass.platform() } // ERROR 3

                var kotlinNonNullProp = MyClass.nonnull() // OK 5
                var kotlinNonNullProp2: String = MyClass.nonnull() // OK 6
                var kotlinPlatformProp = MyClass.platform() // ERROR 4
                var kotlinPlatformProp2: String = MyClass.platform() // OK 7
                val lazyValue by lazy { MyClass.platform() } // ERROR 5
                val lazyValue2 by lazy { MyClass.nullable() } // OK 8

                val ANSWER_THROWS = Answer { 42 } // OK 9
                val ANSWER_THROWS: Answer<Int?> = Answer { 42 } // OK 10
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
            src/test/pkg/MyClass.java:15: Warning: Unknown nullability; explicitly declare as @Nullable or @NonNull to improve Kotlin interoperability; see https://developer.android.com/kotlin/interop#nullability_annotations [UnknownNullness]
                public static String platform() { // ERROR 1
                              ~~~~~~
            src/test/pkg/test.kt:6: Warning: Should explicitly declare type here since implicit type does not specify nullness [UnknownNullness]
            fun kotlinPlatform2() = MyClass.platform() // ERROR 2
                ~~~~~~~~~~~~~~~
            src/test/pkg/test.kt:8: Warning: Should explicitly declare type here since implicit type does not specify nullness [UnknownNullness]
            fun kotlinPlatform3() = run { MyClass.platform() } // ERROR 3
                ~~~~~~~~~~~~~~~
            src/test/pkg/test.kt:12: Warning: Should explicitly declare type here since implicit type does not specify nullness [UnknownNullness]
            var kotlinPlatformProp = MyClass.platform() // ERROR 4
                ~~~~~~~~~~~~~~~~~~
            src/test/pkg/test.kt:14: Warning: Should explicitly declare type here since implicit type does not specify nullness (Lazy<(String..String?)>) [UnknownNullness]
            val lazyValue by lazy { MyClass.platform() } // ERROR 5
                ~~~~~~~~~
            0 errors, 5 warnings
            """
      )
  }

  fun testUnknownNullnessForTypeParameters() {
    // Regression test for https://issuetracker.google.com/169691664.
    lint()
      .files(
        java(
            """
                    package test.pkg;

                    public class Foo<T> {
                        public T foo() { return null; }
                    }
                """
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testOverridePlatform() {
    // Regression test for 206454502: UnknownNullness check shouldn't trigger on overrides of
    // un-annotated platform APIs
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.util.AttributeSet;
                import android.view.View;
                import android.view.ViewGroup;

                public abstract class MyView extends ViewGroup {
                    public MyView() {
                        super(null);
                    }

                    @Override
                    public void addView(View child) {
                        if (getChildCount() > 0) {
                            throw new IllegalStateException("ScrollView can host only one direct child");
                        }

                        super.addView(child);
                    }

                    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
                        throw new RuntimeException("Stub!");
                    }
                }
                """
          )
          .indented(),
        java(
            """
                package test.pkg;

                import android.util.AttributeSet;
                import android.view.View;
                import android.view.ViewGroup;

                public abstract class MyIndirectView extends MyView {
                    @Override
                    public void addView(View child) {
                        if (getChildCount() > 0) {
                            throw new IllegalStateException("ScrollView can host only one direct child");
                        }

                        super.addView(child);
                    }

                    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
                        throw new RuntimeException("Stub!");
                    }
                }
                """
          )
          .indented(),
      )
      .run()
      .expectClean()
  }

  fun testCoroutines() {
    // Regression test for https://issuetracker.google.com/236498269
    lint()
      .files(
        java(
            """
                package com.google.common.util.concurrent;
                import java.util.concurrent.Executor;
                import java.util.concurrent.Future;

                @SuppressWarnings("UnknownNullness")
                public interface ListenableFuture<V> extends Future<V> {
                    void addListener(Runnable var1, Executor var2);
                }
                """
          )
          .indented(),
        java(
            """
                package test.pkg;
                public final class Data {
                }
                """
          )
          .indented(),
        java(
            """
                package test.pkg;
                import com.google.common.util.concurrent.ListenableFuture;
                @SuppressWarnings("UnknownNullness")
                public abstract class ListenableWorker {
                    public ListenableFuture<Void> setProgressAsync(Data data) {
                        return null;
                    }
                }
                """
          )
          .indented(),
        kotlin(
            """
                package androidx.work
                import com.google.common.util.concurrent.ListenableFuture
                public suspend inline fun <R> ListenableFuture<R>.await(): R {
                    TODO()
                }
                """
          )
          .indented(),
        kotlin(
            """
                package test.pkg
                import androidx.work.await
                public abstract class RemoteCoroutineWorker() : ListenableWorker() {
                        public suspend fun setProgress(data: Data) {
                            setProgressAsync(data).await()
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
