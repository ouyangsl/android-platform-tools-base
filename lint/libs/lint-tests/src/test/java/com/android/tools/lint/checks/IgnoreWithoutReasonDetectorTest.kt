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

import com.android.testutils.TestUtils
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.junit.Test

class IgnoreWithoutReasonDetectorTest {
  private fun lint(): TestLintTask {
    return TestLintTask().sdkHome(TestUtils.getSdk().toFile())
  }

  private val stubJUnitTest: TestFile =
    java(
        """
        package org.junit;

        @SuppressWarnings("ClassNameDiffersFromFileName")
        public @interface Test { }"""
      )
      .indented()

  private val stubJUnitIgnore: TestFile =
    java(
        """
        package org.junit;

        @SuppressWarnings("ClassNameDiffersFromFileName")
        public @interface Ignore {
            String value() default "";
        }"""
      )
      .indented()

  @Test
  fun testNoAnnotations() {
    lint()
      .files(
        stubJUnitTest,
        java(
            """
                package foo;

                import org.junit.Test;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                class MyTest {
                  @Test void something() {
                  }
                }"""
          )
          .indented(),
      )
      .issues(IgnoreWithoutReasonDetector.ISSUE)
      .run()
      .expectClean()
  }

  @Test
  fun testAnnotationWithReasonOnFunction() {
    lint()
      .files(
        stubJUnitTest,
        stubJUnitIgnore,
        java(
            """
                package foo;

                import org.junit.Ignore;
                import org.junit.Test;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                class MyTest {
                  @Test @Ignore("reason") public void something() {
                  }
                }"""
          )
          .indented(),
      )
      .issues(IgnoreWithoutReasonDetector.ISSUE)
      .run()
      .expectClean()
  }

  @Test
  fun testAnnotationWithReasonOnClass() {
    lint()
      .files(
        stubJUnitTest,
        stubJUnitIgnore,
        java(
            """
                package foo;

                import org.junit.Ignore;
                import org.junit.Test;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                @Ignore("reason") class MyTest {
                  @Test public void something() {
                  }
                }"""
          )
          .indented(),
      )
      .issues(IgnoreWithoutReasonDetector.ISSUE)
      .run()
      .expectClean()
  }

  @Test
  fun testAnnotationWithoutReasonOnClass() {
    lint()
      .files(
        stubJUnitTest,
        stubJUnitIgnore,
        java(
            """
                package foo;

                import org.junit.Ignore;
                import org.junit.Test;

                @SuppressWarnings("ClassNameDiffersFromFileName")
                @Ignore class MyTest {
                  @Test public void something() {
                  }
                }"""
          )
          .indented(),
      )
      .issues(IgnoreWithoutReasonDetector.ISSUE)
      .run()
      .expect(
        """
                src/foo/MyTest.java:7: Warning: Test is ignored without giving any explanation [IgnoreWithoutReason]
                @Ignore class MyTest {
                ~~~~~~~
                0 errors, 1 warnings
                """
      )
  }

  @Test
  fun testAnnotationWithoutReasonOnClassKotlin() {
    lint()
      .files(
        stubJUnitTest,
        stubJUnitIgnore,
        kotlin(
            """
                    package foo

                    import org.junit.Ignore
                    import org.junit.Test

                    @Ignore class MyTest {
                      @Test fun something() {
                      }
                      @Ignore companion object { // b/170228047
                      }
                    }
                    """
          )
          .indented(),
      )
      .issues(IgnoreWithoutReasonDetector.ISSUE)
      .run()
      .expect(
        """
                src/foo/MyTest.kt:6: Warning: Test is ignored without giving any explanation [IgnoreWithoutReason]
                @Ignore class MyTest {
                ~~~~~~~
                0 errors, 1 warnings
                """
      )
  }

  @Test
  fun testAnnotationWithoutReasonOnFunction() {
    lint()
      .files(
        stubJUnitTest,
        stubJUnitIgnore,
        java(
            """
                package foo;

                import org.junit.Ignore;
                import org.junit.Test;

                @SuppressWarnings({"ClassNameDiffersFromFileName", "DefaultAnnotationParam"})
                class MyTest {
                  @Test @Ignore public void something() {
                  }

                  @Test @Ignore("") public void something() {
                  }

                  @Test @Ignore("TODO") public void something() {
                  }
                }
                """
          )
          .indented(),
      )
      .issues(IgnoreWithoutReasonDetector.ISSUE)
      .run()
      .expect(
        """
                src/foo/MyTest.java:8: Warning: Test is ignored without giving any explanation [IgnoreWithoutReason]
                  @Test @Ignore public void something() {
                        ~~~~~~~
                src/foo/MyTest.java:11: Warning: Test is ignored without giving any explanation [IgnoreWithoutReason]
                  @Test @Ignore("") public void something() {
                        ~~~~~~~~~~~
                src/foo/MyTest.java:14: Warning: Test is ignored without giving any explanation [IgnoreWithoutReason]
                  @Test @Ignore("TODO") public void something() {
                        ~~~~~~~~~~~~~~~
                0 errors, 3 warnings
                """
      )
      .expectFixDiffs(
        """
                Fix for src/foo/MyTest.java line 8: Give reason:
                @@ -8 +8
                -   @Test @Ignore public void something() {
                +   @Test @Ignore("[TODO]|") public void something() {
                """
      )
  }

  @Test
  fun testIgnoredWithComment() {
    lint()
      .files(
        stubJUnitTest,
        stubJUnitIgnore,
        java(
            """
                    package test.pkg;

                    import org.junit.Ignore;
                    import org.junit.Test;

                    class MyJavaTest {
                      @Ignore // comment after
                      @Test public void test1() {
                      }

                      // comment before
                      @Ignore @Test public void test2() {
                      }

                      /* comment before */
                      @Ignore @Test public void test3() {
                      }
                    }
                    """
          )
          .indented(),
        kotlin(
            """
                    package test.pkg

                    import org.junit.Ignore
                    import org.junit.Test

                    class MyKotlinTest {
                      @Ignore // comment after
                      @Test fun test1() {
                      }

                      // comment before
                      @Ignore @Test fun test2() {
                      }

                      /* comment before */
                      @Ignore @Test fun test3() {
                      }

                      @Ignore /* comment inline */ @Test fun test4() {
                      }

                      // Comment a blank line above doesn't count

                      @Ignore
                      // comment on the next line doesn't count
                      @Test fun testError() {
                      }

                    }
                    """
          )
          .indented(),
      )
      .issues(IgnoreWithoutReasonDetector.ISSUE)
      .configureOption(IgnoreWithoutReasonDetector.ALLOW_COMMENT, true)
      .run()
      .expect(
        """
                src/test/pkg/MyKotlinTest.kt:24: Warning: Test is ignored without giving any explanation [IgnoreWithoutReason]
                  @Ignore
                  ~~~~~~~
                0 errors, 1 warnings
                """
          .trimIndent()
      )
  }

  @Test
  fun testIgnoredWithCommentDisabled() {
    lint()
      .files(
        stubJUnitTest,
        stubJUnitIgnore,
        java(
            "test/test/pkg/MyJavaTest.java",
            """
                    package test.pkg;

                    import org.junit.Ignore;
                    import org.junit.Test;

                    class MyJavaTest {
                      @Ignore // comment after
                      @Test public void test1() {
                      }
                    }
                    """,
          )
          .indented(),
      )
      .issues(IgnoreWithoutReasonDetector.ISSUE)
      .configureOption(IgnoreWithoutReasonDetector.ALLOW_COMMENT, false)
      .run()
      .expect(
        """
                test/test/pkg/MyJavaTest.java:7: Warning: Test is ignored without giving any explanation [IgnoreWithoutReason]
                  @Ignore // comment after
                  ~~~~~~~
                0 errors, 1 warnings
                """
      )
  }

  @Test
  fun testInstrumentationTest() {
    lint()
      .files(
        stubJUnitTest,
        stubJUnitIgnore,
        java(
            "androidTest/test/pkg/MyJavaTest.java",
            """
                    package test.pkg;

                    import org.junit.Ignore;
                    import org.junit.Test;

                    class MyJavaTest {
                      @Ignore // comment after
                      @Test public void test1() {
                      }
                    }
                    """,
          )
          .indented(),
      )
      .issues(IgnoreWithoutReasonDetector.ISSUE)
      .configureOption(IgnoreWithoutReasonDetector.ALLOW_COMMENT, false)
      .run()
      .expect(
        """
                androidTest/test/pkg/MyJavaTest.java:7: Warning: Test is ignored without giving any explanation [IgnoreWithoutReason]
                  @Ignore // comment after
                  ~~~~~~~
                0 errors, 1 warnings
                """
      )
  }
}
