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

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.java
import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class IgnoreWithoutReasonDetectorTest {
    val stubJUnitTest = TestFiles.java("""
        package org.junit;

        public @interface Test { }""").indented()

    val stubJUnitIgnore = TestFiles.java("""
        package org.junit;

        public @interface Test { }""").indented()

    @Test
    fun testNoAnnotations() {
        lint()
            .files(stubJUnitTest, java("""
                package foo;

                import org.junit.Test;

                class MyTest {
                  @Test fun something() {
                  }
                }""").indented()
            )
            .issues(IgnoreWithoutReasonDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun testAnnotationWithReasonOnFunction() {
        lint()
            .files(stubJUnitTest, stubJUnitIgnore, java("""
                package foo;

                import org.junit.Ignore;
                import org.junit.Test;

                class MyTest {
                  @Test @Ignore("reason") fun something() {
                  }
                }""").indented()
            )
            .issues(IgnoreWithoutReasonDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun testAnnotationWithReasonOnClass() {
        lint()
            .files(stubJUnitTest, stubJUnitIgnore, java("""
                package foo;

                import org.junit.Ignore;
                import org.junit.Test;

                @Ignore("reason") class MyTest {
                  @Test fun something() {
                  }
                }""").indented()
            )
            .issues(IgnoreWithoutReasonDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun testAnnotationWithoutReasonOnClass() {
        lint()
            .files(stubJUnitTest, stubJUnitIgnore, java("""
                package foo;

                import org.junit.Ignore;
                import org.junit.Test;

                @Ignore class MyTest {
                  @Test fun something() {
                  }
                }""").indented()
            )
            .issues(IgnoreWithoutReasonDetector.ISSUE)
            .run()
            .expect("""
                |src/foo/MyTest.java:6: Warning: Test is ignored without given any explanation. [IgnoreWithoutReason]
                |@Ignore class MyTest {
                |~~~~~~~
                |0 errors, 1 warnings""".trimMargin())
    }

    @Test
    fun testAnnotationWithoutReasonOnFunction() {
        lint()
            .files(stubJUnitTest, stubJUnitIgnore, java("""
                package foo;

                import org.junit.Ignore;
                import org.junit.Test;

                class MyTest {
                  @Test @Ignore fun something() {
                  }
                }""").indented()
            )
            .issues(IgnoreWithoutReasonDetector.ISSUE)
            .run()
            .expect("""
                |src/foo/MyTest.java:7: Warning: Test is ignored without given any explanation. [IgnoreWithoutReason]
                |  @Test @Ignore fun something() {
                |        ~~~~~~~
                |0 errors, 1 warnings""".trimMargin())
    }
}
