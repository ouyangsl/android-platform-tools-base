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
package com.android.tools.lint

import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_KT
import junit.framework.TestCase.assertEquals
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertNull
import org.junit.Test

class ClassNameTest {
  private fun getPackage(source: String): String {
    return ClassName(source).packageName!!
  }

  private fun getClassName(source: String): String {
    return ClassName(source).className!!
  }

  @Test
  fun testGetPackage() {
    assertEquals("foo.bar", getPackage("package foo.bar;"))
    assertEquals("foo.bar", getPackage("package foo.bar"))
  }

  @Test
  fun testGetClass() {
    assertEquals("Foo", getClassName("package foo.bar;\nclass Foo { }"))
    assertEquals("Foo", getClassName("class Foo<T> { }"))
    assertEquals("Foo", getClassName("object Foo : Bar() { }"))
    assertEquals("Foo", getClassName("class Foo(val foo: String) : Bar() { }"))
    assertEquals(
      "ApiCallTest3",
      getClassName(
        // language=JAVA
        """
        /**
         * Call test where the parent class is some other project class which in turn
         * extends the public API
         */
        public class ApiCallTest3 extends Intermediate {}
        """
      ),
    )
  }

  @Test
  fun testAnnotationAttribute() {
    val source =
      ClassName(
        // language=KT
        """
        package test.pkg
        import androidx.annotation.Discouraged
        @Discouraged(message="Don't use this class")
        open class Button
        open class ToggleButton : Button
        """,
        DOT_KT,
      )
    assertEquals("test.pkg", source.packageName)
    assertEquals("Button", source.className)
    assertEquals("test/pkg/Button.kt", source.relativePath())
  }

  @Test
  fun testJavaInterface() {
    val source =
      ClassName(
        // language=JAVA
        """
        import java.lang.annotation.*;
        import static java.lang.annotation.ElementType.FIELD;
        import static java.lang.annotation.RetentionPolicy.CLASS;
        @Retention(CLASS) @Target(FIELD)
        public @interface BindColor {
          int value();
        }
        """,
        DOT_JAVA,
      )
    assertEquals(null, source.packageName)
    assertEquals("BindColor", source.className)
    assertEquals("BindColor.java", source.relativePath())
  }

  @Test
  fun testClassLiterals() {
    // Make sure that in Kotlin where there may be no class declaration (e.g. package
    // functions) we don't accidentally match on T::class.java in the source code.
    assertNull(
      "Foo",
      ClassName(
          // language=KT
          """
          package test.pkg
          import android.content.Context
          inline fun <reified T> Context.systemService1() = getSystemService(T::class.java)
          inline fun Context.systemService2() = getSystemService(String::class.java)
          """
        )
        .className,
    )
  }

  @Test
  fun testObjectInPackage() {
    // https://groups.google.com/g/lint-dev/c/MF1KJP4hijo/m/3QkHST3IAAAJ
    assertEquals(
      "com.test.classes.test",
      getPackage("package com.test.classes.test; class Foo { }"),
    )
    assertEquals(
      "com.test.objects.test",
      getPackage("package com.test.objects.test; class Foo { }"),
    )
    assertEquals("Foo", getClassName("package com.test.objects.test; class Foo { }"))
  }

  @Test
  fun testImportInPackage() {
    // http://b/119884022 ClassName#CLASS_PATTERN invalid regexp
    assertEquals(
      "foo",
      getPackage(
        // language=JAVA
        """
        package foo;
        import foo.interfaces.ThisIsNotClassName;
        public class NavigationView extends View {
        }
        """
          .trimIndent()
      ),
    )
    assertEquals(
      "NavigationView",
      getClassName(
        // language=JAVA
        """
        package foo;
        import foo.interfaces.ThisIsNotClassName;
        public class NavigationView extends View {
        }
        """
          .trimIndent()
      ),
    )
  }

  @Test
  fun testAnnotations() {
    assertEquals("Asdf", getClassName("package foo;\n@Anno(SomeClass.cass)\npublic class Asdf { }"))
  }

  @Test
  fun testAnnotations2() {
    assertEquals(
      "MyClassName",
      getClassName(
        // language=JAVA
        """
        @Anno('\u0000')
        /* class Comment */
        public class MyClassName { }

        @interface Anno {
            char value();
        }"""
      ),
    )
  }

  @Test
  fun testGetClassName() {
    assertEquals(
      "ClickableViewAccessibilityTest",
      getClassName(
        // language=JAVA
        """
        package test.pkg;

        import android.content.Context;
        import android.view.MotionEvent;
        import android.view.View;

        public class ClickableViewAccessibilityTest {
            // Fails because onTouch does not call view.performClick().
            private static class InvalidOnTouchListener implements View.OnTouchListener {
                public boolean onTouch(View v, MotionEvent event) {
                    return false;
                }
            }
        }
        """
      ),
    )
  }

  @Test
  fun testStripComments() {
    assertEquals(
      """
      public class MyClass { String s = "/* This comment is \"in\" a string */" }
      """
        .trimIndent()
        .trim(),
      stripComments(
          // language=JAVA
          """
          /** Comment */
          // Line comment
          public class MyClass { String s = "/* This comment is \"in\" a string */" }""",
          DOT_JAVA,
        )
        .trimIndent()
        .trim(),
    )
  }

  @Test
  fun testStripCommentsNesting() {
    assertEquals(
      """
      fun test1() { }

      fun test2() { }
      """
        .trimIndent()
        .trim(),
      stripComments(
          // language=KT
          """
          // Line comment /*
          /**/ /***/ fun test1() { }
          /* /* */ fun wrong() { } */
          fun test2() { }
          """,
          DOT_KT,
        )
        .trimIndent()
        .trim(),
    )
  }

  @Test
  fun testGetEnumClass() {
    @Language("kotlin")
    val source =
      """
      package com.android.tools.lint.detector.api
      enum class Severity { FATAL, ERROR, WARNING, INFORMATIONAL, IGNORE }
      """
        .trimIndent()
    val className = ClassName(source, DOT_KT)
    assertEquals("com.android.tools.lint.detector.api", className.packageName)
    assertEquals("Severity", className.className)
    assertEquals("com/android/tools/lint/detector/api/Severity.kt", className.relativePath())
  }

  @Test
  fun test195004772() {
    @Language("java")
    val source =
      """
      // Copyright 2007, Google Inc.
      /** The classes in this is package provide a variety of utility services. */
      @CheckReturnValue
      @ParametersAreNonnullByDefault
      @NullMarked
      package com.google.common.util;

      import javax.annotation.ParametersAreNonnullByDefault;
      import org.jspecify.nullness.NullMarked;
      """
        .trimIndent()
    assertEquals("com.google.common.util", ClassName(source).packageName)
    assertNull(ClassName(source).className)
  }

  @Test
  fun testJvmName() {
    @Language("KT")
    val source =
      """
      @file:kotlin.jvm.JvmName("PreconditionsKt")
      package kotlin

      fun assert(value: Boolean) {
          @Suppress("Assert", "KotlinAssert")
          assert(value) { "Assertion failed" }
      }

      fun assert(value: Boolean, lazyMessage: () -> Any) {
      }
      """
        .trimIndent()
    val cls = ClassName(source, DOT_KT)
    assertEquals("PreconditionsKt", cls.jvmName)
    assertEquals("kotlin", cls.packageName)
    assertEquals(null, cls.className)
    assertEquals("kotlin/PreconditionsKt.kt", cls.relativePath())
  }
}
