/*
 * Copyright (C) 2013 The Android Open Source Project
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

class CallSuperDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector {
    return CallSuperDetector()
  }

  fun testDocumentationExample() {
    lint()
      .files(
        kotlin(
            """
                import androidx.annotation.CallSuper

                open class ParentClass {
                    @CallSuper
                    open fun someMethod(arg: Int) {
                        // ...
                    }
                }

                class MyClass : ParentClass() {
                    override fun someMethod(arg: Int) {
                        // Bug: required to call super.someMethod(arg)
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
            src/ParentClass.kt:11: Error: Overriding method should call super.someMethod [MissingSuperCall]
                override fun someMethod(arg: Int) {
                             ~~~~~~~~~~
            1 errors, 0 warnings
            """
      )
  }

  fun testCallSuper() {
    val expected =
      """
            src/test/pkg/CallSuperTest.java:11: Error: Overriding method should call super.test1 [MissingSuperCall]
                    protected void test1() { // ERROR
                                   ~~~~~
            src/test/pkg/CallSuperTest.java:14: Error: Overriding method should call super.test2 [MissingSuperCall]
                    protected void test2() { // ERROR
                                   ~~~~~
            src/test/pkg/CallSuperTest.java:17: Error: Overriding method should call super.test3 [MissingSuperCall]
                    protected void test3() { // ERROR
                                   ~~~~~
            src/test/pkg/CallSuperTest.java:20: Error: Overriding method should call super.test4 [MissingSuperCall]
                    protected void test4(int arg) { // ERROR
                                   ~~~~~
            src/test/pkg/CallSuperTest.java:26: Error: Overriding method should call super.test5 [MissingSuperCall]
                    protected void test5(int arg1, boolean arg2, Map<List<String>,?> arg3,  // ERROR
                                   ~~~~~
            src/test/pkg/CallSuperTest.java:30: Error: Overriding method should call super.test5 [MissingSuperCall]
                    protected void test5() { // ERROR
                                   ~~~~~
            6 errors, 0 warnings
            """
    lint()
      .files(
        java(
            """
                package test.pkg;

                import androidx.annotation.CallSuper;

                import java.util.List;
                import java.util.Map;

                @SuppressWarnings("UnusedDeclaration")
                public class CallSuperTest {
                    private static class Child extends Parent {
                        protected void test1() { // ERROR
                        }

                        protected void test2() { // ERROR
                        }

                        protected void test3() { // ERROR
                        }

                        protected void test4(int arg) { // ERROR
                        }

                        protected void test4(String arg) { // OK
                        }

                        protected void test5(int arg1, boolean arg2, Map<List<String>,?> arg3,  // ERROR
                                             int[][] arg4, int... arg5) {
                        }

                        protected void test5() { // ERROR
                            super.test6(); // (wrong super)
                        }

                        protected void test6() { // OK
                            int x = 5;
                            super.test6();
                            System.out.println(x);
                        }
                    }

                    private static class Parent extends ParentParent {
                        @CallSuper
                        protected void test1() {
                        }

                        protected void test3() {
                            super.test3();
                        }

                        @CallSuper
                        protected void test4(int arg) {
                        }

                        protected void test4(String arg) {
                        }

                        @CallSuper
                        protected void test5() {
                        }

                        @CallSuper
                        protected void test5(int arg1, boolean arg2, Map<List<String>,?> arg3,
                                             int[][] arg4, int... arg5) {
                        }
                    }

                    private static class ParentParent extends ParentParentParent {
                        @CallSuper
                        protected void test2() {
                        }

                        @CallSuper
                        protected void test3() {
                        }

                        @CallSuper
                        protected void test6() {
                        }

                        @CallSuper
                        protected void test7() {
                        }
                    }

                    private static class ParentParentParent {
                    }
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(expected)
  }

  fun testForeignSuperAnnotations() {
    val expected =
      """
            src/test/pkg/OverrideTest.java:9: Error: Overriding method should call super.test [MissingSuperCall]
                    protected void test() { // ERROR
                                   ~~~~
            src/test/pkg/OverrideTest.java:21: Error: Overriding method should call super.test [MissingSuperCall]
                    protected void test() { // ERROR
                                   ~~~~
            2 errors, 0 warnings
            """
    lint()
      .files(
        java(
            """
                package test.pkg;

                import javax.annotation.OverridingMethodsMustInvokeSuper;
                import edu.umd.cs.findbugs.annotations.OverrideMustInvoke;

                @SuppressWarnings("UnusedDeclaration")
                public class OverrideTest {
                    private static class Child1 extends Parent1 {
                        protected void test() { // ERROR
                        }

                    }

                    private static class Parent1 {
                        @OverrideMustInvoke
                        protected void test() {
                        }
                    }

                    private static class Child2 extends Parent2 {
                        protected void test() { // ERROR
                        }

                    }

                    private static class Parent2 {
                        @OverridingMethodsMustInvokeSuper
                        protected void test() {
                        }
                    }
                }
                """
          )
          .indented(),
        java(
            """
                /* HIDE-FROM-DOCUMENTATION */
                package edu.umd.cs.findbugs.annotations;

                import java.lang.annotation.Retention;
                import java.lang.annotation.Target;

                import static java.lang.annotation.ElementType.CONSTRUCTOR;
                import static java.lang.annotation.ElementType.METHOD;
                import static java.lang.annotation.RetentionPolicy.CLASS;

                @Retention(CLASS)
                @Target({METHOD,CONSTRUCTOR})
                public @interface OverrideMustInvoke {
                }

                """
          )
          .indented(),
        java(
            """
                /* HIDE-FROM-DOCUMENTATION */
                package javax.annotation;

                import java.lang.annotation.Retention;
                import java.lang.annotation.Target;

                import static java.lang.annotation.ElementType.CONSTRUCTOR;
                import static java.lang.annotation.ElementType.METHOD;
                import static java.lang.annotation.RetentionPolicy.CLASS;

                @Retention(CLASS)
                @Target({METHOD,CONSTRUCTOR})
                public @interface OverridingMethodsMustInvokeSuper {
                }

                """
          )
          .indented(),
      )
      .run()
      .expect(expected)
  }

  fun testCallSuperIndirect() {
    // Ensure that when the @CallSuper is on an indirect super method,
    // we correctly check that you call the direct super method, not the ancestor.
    //
    // Regression test for
    //    https://code.google.com/p/android/issues/detail?id=174964
    lint()
      .files(
        java(
            "src/test/pkg/CallSuperTest.java",
            """
                package test.pkg;

                import androidx.annotation.CallSuper;

                import java.util.List;
                import java.util.Map;

                @SuppressWarnings("UnusedDeclaration")
                public class CallSuperTest {
                    private static class Child extends Parent {
                        @Override
                        protected void test1() {
                            super.test1();
                        }
                    }

                    private static class Parent extends ParentParent {
                        @Override
                        protected void test1() {
                            super.test1();
                        }
                    }

                    private static class ParentParent extends ParentParentParent {
                        @CallSuper
                        protected void test1() {
                        }
                    }

                    private static class ParentParentParent {

                    }
                }
                """,
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun testDetachFromWindow() {
    val expected =
      """
            src/test/pkg/DetachedFromWindow.java:7: Error: Overriding method should call super.onDetachedFromWindow [MissingSuperCall]
                    protected void onDetachedFromWindow() {
                                   ~~~~~~~~~~~~~~~~~~~~
            src/test/pkg/DetachedFromWindow.java:26: Error: Overriding method should call super.onDetachedFromWindow [MissingSuperCall]
                    protected void onDetachedFromWindow() {
                                   ~~~~~~~~~~~~~~~~~~~~
            2 errors, 0 warnings
            """
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.view.View;

                public class DetachedFromWindow {
                    private static class Test1 extends ViewWithDefaultConstructor {
                        protected void onDetachedFromWindow() {
                            // Error
                        }
                    }

                    private static class Test2 extends ViewWithDefaultConstructor {
                        protected void onDetachedFromWindow(int foo) {
                            // OK: not overriding the right method
                        }
                    }

                    private static class Test3 extends ViewWithDefaultConstructor {
                        protected void onDetachedFromWindow() {
                            // OK: Calling super
                            super.onDetachedFromWindow();
                        }
                    }

                    private static class Test4 extends ViewWithDefaultConstructor {
                        protected void onDetachedFromWindow() {
                            // Error: missing detach call
                            int x = 1;
                            x++;
                            System.out.println(x);
                        }
                    }

                    private static class Test5 extends Object {
                        protected void onDetachedFromWindow() {
                            // OK - not in a view
                            // Regression test for http://b.android.com/73571
                        }
                    }

                    public class ViewWithDefaultConstructor extends View {
                        public ViewWithDefaultConstructor() {
                            super(null);
                        }
                    }
                }
                """
          )
          .indented()
      )
      .run()
      .expect(expected)
  }

  fun testMultipleOverrides() {
    val expected =
      """
        src/Bar.kt:13: Error: Overriding method should call super.foo [MissingSuperCall]
            override fun foo() { // ERROR: Missing super call
                         ~~~
        1 errors, 0 warnings
      """
    lint()
      .files(
        kotlin(
          """
          import androidx.annotation.CallSuper

          open class Bar {
              open fun foo() {}
          }

          interface Foo {
              @CallSuper
              fun foo() {}
          }

          class FooImpl : Bar(), Foo {
              override fun foo() { // ERROR: Missing super call
              }
          }
        """
        ).indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(expected)
  }

  fun testWatchFaceVisibility() {
    val expected =
      """
            src/test/pkg/WatchFaceTest.java:9: Error: Overriding method should call super.onVisibilityChanged [MissingSuperCall]
                    public void onVisibilityChanged(boolean visible) { // ERROR: Missing super call
                                ~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
    lint()
      .files(
        java(
            """
                package test.pkg;

                import android.support.wearable.watchface.CanvasWatchFaceService;

                @SuppressWarnings("UnusedDeclaration")
                public class WatchFaceTest extends CanvasWatchFaceService {
                    private static class MyEngine1 extends CanvasWatchFaceService.Engine {
                        @Override
                        public void onVisibilityChanged(boolean visible) { // ERROR: Missing super call
                        }
                    }

                    private static class MyEngine2 extends CanvasWatchFaceService.Engine {
                        @Override
                        public void onVisibilityChanged(boolean visible) { // OK: Super called
                            super.onVisibilityChanged(visible);
                        }
                    }

                    private static class MyEngine3 extends CanvasWatchFaceService.Engine {
                        @Override
                        public void onVisibilityChanged(boolean visible) { // OK: Super called sometimes
                            boolean something = System.currentTimeMillis() % 1 != 0;
                            if (visible && something) {
                                super.onVisibilityChanged(true);
                            }
                        }
                    }

                    private static class MyEngine4 extends CanvasWatchFaceService.Engine {
                        public void onVisibilityChanged() { // OK: Different signature
                        }
                        public void onVisibilityChanged(int flags) { // OK: Different signature
                        }
                        public void onVisibilityChanged(boolean visible, int flags) { // OK: Different signature
                        }
                    }
                }
                """
          )
          .indented(),
        java(
            """
                package android.support.wearable.watchface;

                // Unit testing stub
                public class WatchFaceService {
                    public static class Engine {
                        public void onVisibilityChanged(boolean visible) {
                        }
                    }
                }
                """
          )
          .indented(),
        java(
            """
                package android.support.wearable.watchface;

                public class CanvasWatchFaceService extends WatchFaceService {
                    public static class Engine extends WatchFaceService.Engine {
                        public void onVisibilityChanged(boolean visible) {
                            super.onVisibilityChanged(visible);
                        }
                    }
                }
                """
          )
          .indented(),
      )
      .run()
      .expect(expected)
  }

  fun testKotlinMissing() {
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import android.content.Context
                import android.view.View
                class MissingSuperCallLibrary(context: Context) : View(context) {
                    override fun onDetachedFromWindow() {
                    }
                }"""
          )
          .indented()
      )
      .incremental()
      .run()
      .expect(
        """
            src/test/pkg/MissingSuperCallLibrary.kt:6: Error: Overriding method should call super.onDetachedFromWindow [MissingSuperCall]
                override fun onDetachedFromWindow() {
                             ~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
      )
  }

  fun testKotlinOk() {
    lint()
      .files(
        kotlin(
            """package test.pkg

                import android.content.Context
                import android.view.View
                class MissingSuperCallLibrary(context: Context) : View(context) {
                    override fun onDetachedFromWindow() {
                        super.onDetachedFromWindow();
                    }
                }"""
          )
          .indented()
      )
      .incremental()
      .run()
      .expectClean()
  }

  fun testMultipleSuperCalls() {
    // Regression test for
    //  37133950: new Lint check: calling the same super function more than once
    lint()
      .files(
        kotlin(
            """
                package test.pkg

                import android.content.Context
                import android.app.Activity
                import android.os.Bundle
                class MyActivity(context: Context) : Activity(context) {
                    private var suspend = false
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState) // OK
                        super.onCreate(savedInstanceState) // ERROR
                    }
                }

                class MyActivity2(context: Context) : Activity(context) {
                    private var suspend = false
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState) // OK
                        if (!suspend) {
                            super.onResume()
                            super.onCreate(savedInstanceState) // ERROR
                        }
                    }
                }

                class MyActivity3(context: Context) : Activity(context) {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        if (savedInstanceState != null) {
                            super.onCreate(savedInstanceState) // OK
                        } else {
                            super.onCreate(savedInstanceState) // OK
                        }
                    }
                }
                """
          )
          .indented()
      )
      .incremental()
      .run()
      .expect(
        """
            src/test/pkg/MyActivity.kt:10: Error: Calling super.onCreate more than once can lead to crashes [MissingSuperCall]
                    super.onCreate(savedInstanceState) // ERROR
                    ~~~~~
            src/test/pkg/MyActivity.kt:20: Error: Calling super.onCreate more than once can lead to crashes [MissingSuperCall]
                        super.onCreate(savedInstanceState) // ERROR
                        ~~~~~
            2 errors, 0 warnings
            """
      )
  }

  fun testWorkaround180509152() {
    // We have a temporary workaround for 180509152; this tests verifies that
    // workaround. When the bug is fixed the super.onCreate call in MainActivity
    // below should be uncommented.
    lint()
      .files(
        kotlin(
            """
                package androidx.fragment.app
                open class FragmentActivity {
                }
                """
          )
          .indented(),
        kotlin(
            """
                package androidx.appcompat.app
                import android.os.Bundle
                import androidx.annotation.CallSuper
                open class AppCompatActivity : androidx.fragment.app.FragmentActivity() {
                    protected fun unrelated() {}
                    @CallSuper
                    fun onCreate(savedInstanceState: Bundle?) {
                        println("Hello")
                    }
                }
                """
          )
          .indented(),
        kotlin(
            """
                package test.pkg

                import android.os.Bundle
                import androidx.appcompat.app.AppCompatActivity

                class MainActivity : AppCompatActivity() {
                    override fun onCreate(savedInstanceState: Bundle?) { // OK
                        // Deliberately not calling super. In a normal
                        // scenario, lint should flag this as an error.
                        // But because of unknown recent problems (described
                        // in 180509152) we're temporarily filtering out this
                        // specific instance to avoid a lot of false positives
                        // until we track this down.
                        //super.onCreate(savedInstanceState)
                        // The warning *will* kick in if there are no
                        // super calls at all in the method, so make sure
                        // it finds at least one super call
                        super.unrelated()
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

  fun testAbstractMethods() {
    // Regression test for b/266700164
    lint()
      .files(
        kotlin(
            """
                import androidx.annotation.CallSuper

                open abstract class ParentClass {
                    @CallSuper
                    open abstract fun someMethod(arg: Int) {
                        // ...
                    }

                    @CallSuper
                    open fun otherMethod(arg: Int) {
                      //
                    }
                }

                abstract class MyClass : ParentClass() {
                    override fun someMethod(arg: Int) {
                        // OK because parent is abstract
                    }

                    abstract override fun otherMethod(arg: Int) // OK because is abstract
                }
                """
          )
          .indented(),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expectClean()
  }

  fun testSuperCallInNestedObject() {
    // Regression test for b/266700164
    lint()
      .files(
        kotlin(
            """
                import androidx.annotation.CallSuper

                open class Parent {
                  @CallSuper
                  open fun someMethod(arg: Int) {
                    //
                  }
                }

                class Child: Parent {
                  override fun someMethod(arg: Int) {
                    object: Parent() {
                      override fun someMethod(arg: Int) {
                        super.someMethod(arg)
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
      .expect(
        """
            src/Parent.kt:11: Error: Overriding method should call super.someMethod [MissingSuperCall]
              override fun someMethod(arg: Int) {
                           ~~~~~~~~~~
            1 errors, 0 warnings
            """
      )
  }

  fun testIndirectSuperCallCompiled() {
    // Regression test for b/189433125.
    lint()
      .files(
        kotlin(
            """
                open class A : Middle() {
                    override fun foo() {
                        super.foo() // OK
                    }
                }

                open class B : Middle() {
                    override fun foo() {
                        // ERROR
                    }
                }
                """
          )
          .indented(),
        compiled(
          "libs/lib1.jar",
          kotlin(
              """
                    open class Middle : Base()
                    """
            )
            .indented(),
          0xb76b5946,
          """
                Middle.class:
                H4sIAAAAAAAAAC1QTUsDMRB9k2237Vrth1rrB4g39WBr8aYIVhAKWw8qvfSU
                dhcM3WahScVjf4v/wJPgQYpHf5Q42RrIY96bycyb/Px+fgG4wAHB76soSuIC
                iJDrSsORx+qV0speE7zjk0EZefgBcihwiX1WhlAMV88uCbVwktpE6VY/tjKS
                VrImpi8eDyAHeQJNWHpVjrU5is4Jh8tFEIimyO5yUWw0l4uOaFM3//3mi6pw
                ZR32ETpL3LG0mnc2sezhNo1iQiVUOr6fT0fx7EmOElbqYTqWyUDOlOP/YvCY
                zmfj+E45svsw11ZN44EyirM3WqdWWpVqgyMIXtEdNuw2Ztxh1so473H6geI7
                BwJNRj8TuSNjeVWAEoIsv5dhA/vZJxPWOFcewuthvYcNRlQcVHuooT4EGWxi
                i/MGgcG2gf8H2auBiqEBAAA=
                """,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAAAGNgYGBmYGBgBGJWKM2gxKDFAABNj30wGAAAAA==
                """,
        ),
        compiled(
          "libs/lib2.jar",
          kotlin(
              """
                    import androidx.annotation.CallSuper

                    open class Base {
                        @CallSuper
                        open fun foo() {}
                    }
                    """
            )
            .indented(),
          0xdd7e8dee,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAAAGNgYGBmYGBgBGJWKM2gxKDFAABNj30wGAAAAA==
                """,
          """
                Base.class:
                H4sIAAAAAAAAAGVQTU8bMRB99mY36RJgoQXCR0HcCkjdFPVUUCVAqppqoVKp
                csnJyZpisrGrXSfimN/Sf9BTpR5QxJEfVTFeIlSBJT/PvHnPmpm7f39vALzH
                FkPlWBSyCsYQXYmRiDOhf8Rfu1eyZ6vwGIJDpZX9yOC92WnX4SMIUUGVjPZS
                FVRP3AcHVL8whmErETrNjUqvY6G1scIqo+MTkWXnw58yJ91C0jc2Uzo+lVak
                wgri+GDkUUPMgc/A+kRdK5c1KUrfMcSTcRTyBg95NBmHvOYCXltuTMb7vMmO
                /dtfARFfapG3xpuVzyvOts/oS1Rdf2/7ljo+MalkmE+UlmfDQVfm30U3I2Yx
                MT2RtUWuXD4l178NtVUD2dIjVSiijh7nobHDczPMe/KTctLVqbT9TIhtcFqX
                O5x6oe0RrlIW08vctLt/UPtdltcIg5L0sE5YfxDgBUK3G8xQlZfmvTKn+9To
                /2dkU+PGtFovta9LbGCT3g/EzpJnrgOvhfkWIkIsOFhs4SVedcAKLGG5A79A
                WGClQFBghoJ7aI1RU0ICAAA=
                """,
        ),
        SUPPORT_ANNOTATIONS_JAR,
      )
      .run()
      .expect(
        """
            src/A.kt:8: Error: Overriding method should call super.foo [MissingSuperCall]
                override fun foo() {
                             ~~~
            1 errors, 0 warnings
            """
      )
  }
}
