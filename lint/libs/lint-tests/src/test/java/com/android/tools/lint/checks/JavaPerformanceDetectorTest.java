/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.tools.lint.checks;

import static com.android.tools.lint.checks.JavaPerformanceDetector.USE_VALUE_OF;

import com.android.tools.lint.checks.infrastructure.TestFile;
import com.android.tools.lint.checks.infrastructure.TestMode;
import com.android.tools.lint.detector.api.Detector;

public class JavaPerformanceDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new JavaPerformanceDetector();
    }

    @Override
    protected boolean allowCompilationErrors() {
        // Some of these unit tests are still relying on source code that references
        // unresolved symbols etc.
        return true;
    }

    public void testDocumentationExampleDrawAllocation() {
      lint().files(
          java(
              ""
                  + "package test.pkg;\n"
                  + "\n"
                  + "import android.content.Context;\n"
                  + "import android.graphics.Bitmap;\n"
                  + "import android.graphics.Canvas;\n"
                  + "import android.util.AttributeSet;\n"
                  + "import android.view.View;\n"
                  + "\n"
                  + "public abstract class MyView extends View {\n"
                  + "    public MyView(Context context, AttributeSet attrs, int defStyle) {\n"
                  + "        super(context, attrs, defStyle);\n"
                  + "    }\n"
                  + "\n"
                  + "    @Override\n"
                  + "    protected void onDraw(Canvas canvas) {\n"
                  + "        super.onDraw(canvas);\n"
                  + "\n"
                  + "        bitmap = Bitmap.createBitmap(100, 100, null);\n"
                  + "    }\n"
                  + "\n"
                  + "    private Bitmap bitmap;\n"
                  + "}\n"
          )
      )      .run()
          .expect(
              ""
                  + "src/test/pkg/MyView.java:18: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead) [DrawAllocation]\n"
                  + "        bitmap = Bitmap.createBitmap(100, 100, null);\n"
                  + "                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                  + "0 errors, 1 warnings"
          );
    }

  public void testDocumentationExampleUseValueOf() {
    lint().files(
            java(
                ""
                    + "package test.pkg;\n"
                    + "\n"
                    + "public class MyClass {\n"
                    + "    Double accuracy = new Double(1.0);\n"
                    + "}\n"
            )
        )      .run()
        .expect(
            ""
                + "src/test/pkg/MyClass.java:4: Warning: Use Double.valueOf(1.0) instead [UseValueOf]\n"
                + "    Double accuracy = new Double(1.0);\n"
                + "                      ~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings"
        );
  }

  @SuppressWarnings("all")
    public void test() {
        String expected =
                ""
                        + "src/test/pkg/JavaPerformanceTest.java:31: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead) [DrawAllocation]\n"
                        + "        new String(\"foo\");\n"
                        + "        ~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/JavaPerformanceTest.java:32: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead) [DrawAllocation]\n"
                        + "        String s = new String(\"bar\");\n"
                        + "                   ~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/JavaPerformanceTest.java:95: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead) [DrawAllocation]\n"
                        + "        new String(\"flag me\");\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/JavaPerformanceTest.java:101: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead) [DrawAllocation]\n"
                        + "        new String(\"flag me\");\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/JavaPerformanceTest.java:104: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead) [DrawAllocation]\n"
                        + "        Bitmap.createBitmap(100, 100, null);\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/JavaPerformanceTest.java:105: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead) [DrawAllocation]\n"
                        + "        android.graphics.Bitmap.createScaledBitmap(null, 100, 100, false);\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/JavaPerformanceTest.java:106: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead) [DrawAllocation]\n"
                        + "        BitmapFactory.decodeFile(null);\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/JavaPerformanceTest.java:108: Warning: Avoid object allocations during draw operations: Use Canvas.getClipBounds(Rect) instead of Canvas.getClipBounds() which allocates a temporary Rect [DrawAllocation]\n"
                        + "        canvas.getClipBounds(); // allocates on your behalf\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/JavaPerformanceTest.java:132: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead) [DrawAllocation]\n"
                        + "            new String(\"foo\");\n"
                        + "            ~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/JavaPerformanceTest.java:182: Warning: Use new SparseIntArray(...) instead for better performance [UseSparseArrays]\n"
                        + "        new SparseArray<Integer>(); // Use SparseIntArray instead\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/JavaPerformanceTest.java:184: Warning: Use new SparseBooleanArray(...) instead for better performance [UseSparseArrays]\n"
                        + "        new SparseArray<Boolean>(); // Use SparseBooleanArray instead\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/JavaPerformanceTest.java:36: Warning: Use Integer.valueOf(5) instead [UseValueOf]\n"
                        + "        Integer i = new Integer(5);\n"
                        + "                    ~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/JavaPerformanceTest.java:137: Warning: Use Integer.valueOf(42) instead [UseValueOf]\n"
                        + "        Integer i1 = new Integer(42);\n"
                        + "                     ~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/JavaPerformanceTest.java:138: Warning: Use Long.valueOf(42L) instead [UseValueOf]\n"
                        + "        Long l1 = new Long(42L);\n"
                        + "                  ~~~~~~~~~~~~~\n"
                        + "src/test/pkg/JavaPerformanceTest.java:139: Warning: Use Boolean.valueOf(true) instead [UseValueOf]\n"
                        + "        Boolean b1 = new Boolean(true);\n"
                        + "                     ~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/JavaPerformanceTest.java:140: Warning: Use Character.valueOf('c') instead [UseValueOf]\n"
                        + "        Character c1 = new Character('c');\n"
                        + "                       ~~~~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/JavaPerformanceTest.java:141: Warning: Use Float.valueOf(1.0f) instead [UseValueOf]\n"
                        + "        Float f1 = new Float(1.0f);\n"
                        + "                   ~~~~~~~~~~~~~~~\n"
                        + "src/test/pkg/JavaPerformanceTest.java:142: Warning: Use Double.valueOf(1.0) instead [UseValueOf]\n"
                        + "        Double d1 = new Double(1.0);\n"
                        + "                    ~~~~~~~~~~~~~~~\n"
                        + "0 errors, 18 warnings";

        //noinspection all // Sample code
        lint().files(
                        java(
                                "src/test/pkg/JavaPerformanceTest.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.annotation.SuppressLint;\n"
                                        + "import android.content.Context;\n"
                                        + "import android.graphics.Bitmap;\n"
                                        + "import android.graphics.BitmapFactory;\n"
                                        + "import android.graphics.Canvas;\n"
                                        + "import android.graphics.LinearGradient;\n"
                                        + "import android.graphics.Rect;\n"
                                        + "import android.graphics.Shader.TileMode;\n"
                                        + "import android.util.AttributeSet;\n"
                                        + "import android.util.SparseArray;\n"
                                        + "import android.widget.Button;\n"
                                        + "import java.util.HashMap;\n"
                                        + "import java.util.Map;\n"
                                        + "\n"
                                        + "/** Some test data for the JavaPerformanceDetector */\n"
                                        + "@SuppressWarnings(\"unused\")\n"
                                        + "public class JavaPerformanceTest extends Button {\n"
                                        + "    public JavaPerformanceTest(Context context, AttributeSet attrs, int defStyle) {\n"
                                        + "        super(context, attrs, defStyle);\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    private Rect cachedRect;\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    protected void onDraw(android.graphics.Canvas canvas) {\n"
                                        + "        super.onDraw(canvas);\n"
                                        + "\n"
                                        + "        // Various allocations:\n"
                                        + "        new String(\"foo\");\n"
                                        + "        String s = new String(\"bar\");\n"
                                        + "\n"
                                        + "        // This one should not be reported:\n"
                                        + "        @SuppressLint(\"DrawAllocation\")\n"
                                        + "        Integer i = new Integer(5);\n"
                                        + "\n"
                                        + "        // Cached object initialized lazily: should not complain about these\n"
                                        + "        if (cachedRect == null) {\n"
                                        + "            cachedRect = new Rect(0, 0, 100, 100);\n"
                                        + "        }\n"
                                        + "        if (cachedRect == null || cachedRect.width() != 50) {\n"
                                        + "            cachedRect = new Rect(0, 0, 50, 100);\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        boolean b = Boolean.valueOf(true); // auto-boxing\n"
                                        + "        sample(1, 2);\n"
                                        + "\n"
                                        + "        // Non-allocations\n"
                                        + "        super.animate();\n"
                                        + "        sample2(1, 2);\n"
                                        + "        int x = 4 + '5';\n"
                                        + "\n"
                                        + "        // This will involve allocations, but we don't track\n"
                                        + "        // inter-procedural stuff here\n"
                                        + "        someOtherMethod();\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    void sample(Integer foo, int bar) {\n"
                                        + "        sample2(foo, bar);\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    void sample2(int foo, int bar) {\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    void someOtherMethod() {\n"
                                        + "        // Allocations are okay here\n"
                                        + "        new String(\"foo\");\n"
                                        + "        String s = new String(\"bar\");\n"
                                        + "        boolean b = Boolean.valueOf(true); // auto-boxing\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec,\n"
                                        + "                             boolean x) { // wrong signature\n"
                                        + "        new String(\"not an error\");\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    protected void onMeasure(int widthMeasureSpec) { // wrong signature\n"
                                        + "        new String(\"not an error\");\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    protected void onLayout(boolean changed, int left, int top, int right,\n"
                                        + "                            int bottom, int wrong) { // wrong signature\n"
                                        + "        new String(\"not an error\");\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    protected void onLayout(boolean changed, int left, int top, int right) {\n"
                                        + "        // wrong signature\n"
                                        + "        new String(\"not an error\");\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    protected void onLayout(boolean changed, int left, int top, int right,\n"
                                        + "                            int bottom) {\n"
                                        + "        new String(\"flag me\");\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @SuppressWarnings(\"null\") // not real code\n"
                                        + "    @Override\n"
                                        + "    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {\n"
                                        + "        new String(\"flag me\");\n"
                                        + "\n"
                                        + "        // Forbidden factory methods:\n"
                                        + "        Bitmap.createBitmap(100, 100, null);\n"
                                        + "        android.graphics.Bitmap.createScaledBitmap(null, 100, 100, false);\n"
                                        + "        BitmapFactory.decodeFile(null);\n"
                                        + "        Canvas canvas = null;\n"
                                        + "        canvas.getClipBounds(); // allocates on your behalf\n"
                                        + "        canvas.getClipBounds(null); // NOT an error\n"
                                        + "\n"
                                        + "        final int layoutWidth = getWidth();\n"
                                        + "        final int layoutHeight = getHeight();\n"
                                        + "        if (mAllowCrop && (mOverlay == null || mOverlay.getWidth() != layoutWidth ||\n"
                                        + "                mOverlay.getHeight() != layoutHeight)) {\n"
                                        + "            mOverlay = Bitmap.createBitmap(layoutWidth, layoutHeight, Bitmap.Config.ARGB_8888);\n"
                                        + "            mOverlayCanvas = new Canvas(mOverlay);\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (widthMeasureSpec == 42) {\n"
                                        + "            throw new IllegalStateException(\"Test\"); // NOT an allocation\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        // More lazy init tests\n"
                                        + "        boolean initialized = false;\n"
                                        + "        if (!initialized) {\n"
                                        + "            new String(\"foo\");\n"
                                        + "            initialized = true;\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        // NOT lazy initialization\n"
                                        + "        if (!initialized || mOverlay == null) {\n"
                                        + "            new String(\"foo\");\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    void factories() {\n"
                                        + "        Integer i1 = new Integer(42);\n"
                                        + "        Long l1 = new Long(42L);\n"
                                        + "        Boolean b1 = new Boolean(true);\n"
                                        + "        Character c1 = new Character('c');\n"
                                        + "        Float f1 = new Float(1.0f);\n"
                                        + "        Double d1 = new Double(1.0);\n"
                                        + "\n"
                                        + "        // The following should not generate errors:\n"
                                        + "        Object i2 = new foo.bar.Integer(42);\n"
                                        + "        Integer i3 = Integer.valueOf(42);\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    private boolean mAllowCrop;\n"
                                        + "    private Canvas mOverlayCanvas;\n"
                                        + "    private Bitmap mOverlay;\n"
                                        + "private abstract class JavaPerformanceTest1 extends JavaPerformanceTest {\n"
                                        + "    @Override\n"
                                        + "    public void layout(int l, int t, int r, int b) {\n"
                                        + "        // Using \"this.\" to reference fields\n"
                                        + "        if (this.shader == null)\n"
                                        + "            this.shader = new LinearGradient(0, 0, getWidth(), 0, GRADIENT_COLORS, null,\n"
                                        + "                    TileMode.REPEAT);\n"
                                        + "    }\n"
                                        + "} private abstract class JavaPerformanceTest2 extends JavaPerformanceTest {\n"
                                        + "        @Override\n"
                                        + "    public void layout(int l, int t, int r, int b) {\n"
                                        + "        int width = getWidth();\n"
                                        + "        int height = getHeight();\n"
                                        + "\n"
                                        + "        if ((shader == null) || (lastWidth != width) || (lastHeight != height))\n"
                                        + "        {\n"
                                        + "            lastWidth = width;\n"
                                        + "            lastHeight = height;\n"
                                        + "\n"
                                        + "            shader = new LinearGradient(0, 0, width, 0, GRADIENT_COLORS, null, TileMode.REPEAT);\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "} private abstract class JavaPerformanceTest3 extends JavaPerformanceTest {\n"
                                        + "    @Override\n"
                                        + "    public void layout(int l, int t, int r, int b) {\n"
                                        + "        if ((shader == null) || (lastWidth != getWidth()) || (lastHeight != getHeight())) {\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}\n"
                                        + "    public void inefficientSparseArray() {\n"
                                        + "        new SparseArray<Integer>(); // Use SparseIntArray instead\n"
                                        + "        new SparseArray<Long>();    // Use SparseLongArray instead\n"
                                        + "        new SparseArray<Boolean>(); // Use SparseBooleanArray instead\n"
                                        + "        new SparseArray<Object>();  // OK\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public void longSparseArray() { // but only minSdkVersion >= 17 or if has v4 support lib\n"
                                        + "        Map<Long, String> myStringMap = new HashMap<Long, String>();\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public void byteSparseArray() { // bytes easily apply to ints\n"
                                        + "        Map<Byte, String> myByteMap = new HashMap<Byte, String>();\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    protected LinearGradient shader;\n"
                                        + "    protected int lastWidth;\n"
                                        + "    protected int lastHeight;\n"
                                        + "    protected int[] GRADIENT_COLORS;\n"
                                        + "\n"
                                        + "    private static class foo {\n"
                                        + "        private static class bar {\n"
                                        + "            private static class Integer {\n"
                                        + "                public Integer(int val) {\n"
                                        + "                }\n"
                                        + "            }\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "    public JavaPerformanceTest() {\n"
                                        + "        super(null);\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expect(expected)
                .expectFixDiffs(
                        ""
                                + "Autofix for src/test/pkg/JavaPerformanceTest.java line 36: Replace with valueOf():\n"
                                + "@@ -36 +36\n"
                                + "-         Integer i = new Integer(5);\n"
                                + "+         Integer i = Integer.valueOf(5);\n"
                                + "Autofix for src/test/pkg/JavaPerformanceTest.java line 137: Replace with valueOf():\n"
                                + "@@ -137 +137\n"
                                + "-         Integer i1 = new Integer(42);\n"
                                + "+         Integer i1 = Integer.valueOf(42);\n"
                                + "Autofix for src/test/pkg/JavaPerformanceTest.java line 138: Replace with valueOf():\n"
                                + "@@ -138 +138\n"
                                + "-         Long l1 = new Long(42L);\n"
                                + "+         Long l1 = Long.valueOf(42L);\n"
                                + "Autofix for src/test/pkg/JavaPerformanceTest.java line 139: Replace with valueOf():\n"
                                + "@@ -139 +139\n"
                                + "-         Boolean b1 = new Boolean(true);\n"
                                + "+         Boolean b1 = Boolean.valueOf(true);\n"
                                + "Autofix for src/test/pkg/JavaPerformanceTest.java line 140: Replace with valueOf():\n"
                                + "@@ -140 +140\n"
                                + "-         Character c1 = new Character('c');\n"
                                + "+         Character c1 = Character.valueOf('c');\n"
                                + "Autofix for src/test/pkg/JavaPerformanceTest.java line 141: Replace with valueOf():\n"
                                + "@@ -141 +141\n"
                                + "-         Float f1 = new Float(1.0f);\n"
                                + "+         Float f1 = Float.valueOf(1.0f);\n"
                                + "Autofix for src/test/pkg/JavaPerformanceTest.java line 142: Replace with valueOf():\n"
                                + "@@ -142 +142\n"
                                + "-         Double d1 = new Double(1.0);\n"
                                + "+         Double d1 = Double.valueOf(1.0);");
    }

    public void testNoLongSparseArray() {
        lint().files(manifest().minSdk(1), mLongSparseArray).run().expectClean();
    }

    public void testNoSparseArrayOutsideAndroid() {
        //noinspection all // Sample code
        lint().files(manifest().minSdk(17), mLongSparseArray, gradle("apply plugin: 'java'\n"))
                // source file references Android classes not available outside of Android
                .allowCompilationErrors()
                .run()
                .expectClean();
    }

    public void testUseValueOfOnArrays() {
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import junit.framework.Assert;\n"
                                        + "\n"
                                        + "import java.util.Arrays;\n"
                                        + "import java.util.Calendar;\n"
                                        + "import java.util.List;\n"
                                        + "\n"
                                        + "public class TestValueOf {\n"
                                        + "    public Integer[] getAffectedDays(List<Integer> mAffectedDays) {\n"
                                        + "        return mAffectedDays.toArray(new Integer[mAffectedDays.size()]);\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    public void test2(Integer[] x) {\n"
                                        + "        Assert.assertTrue(Arrays.equals(x, new Integer[]{Calendar.MONDAY}));\n"
                                        + "    }\n"
                                        + "}\n"),
                        // junit stub:
                        java(
                                ""
                                        + "package junit.framework;\n"
                                        + "public class Assert {\n"
                                        + "    public static void assertTrue(boolean condition) {}\n"
                                        + "}"))
                .run()
                .expectClean();
    }

    public void testAllocationForArrays() {
        //noinspection all // Sample code
        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.content.Context;\n"
                                        + "import android.content.res.TypedArray;\n"
                                        + "import android.graphics.Canvas;\n"
                                        + "import android.util.AttributeSet;\n"
                                        + "import android.widget.Button;\n"
                                        + "\n"
                                        + "public class MyButton extends Button {\n"
                                        + "\n"
                                        + "    public MyButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {\n"
                                        + "        super(context, attrs, defStyleAttr, defStyleRes);\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    protected void onDraw(Canvas canvas) {\n"
                                        + "        super.onDraw(canvas);\n"
                                        + "\n"
                                        + "        char[] text;\n"
                                        + "\n"
                                        + "        if (isInEditMode()) {\n"
                                        + "            text = new char[0];\n"
                                        + "        } else {\n"
                                        + "            text = getText().toString().toCharArray();\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        TypedArray array = getContext().obtainStyledAttributes(new int[] { android.R.attr.listPreferredItemHeight });\n"
                                        + "        array.recycle();\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expect(
                        ""
                                + "src/test/pkg/MyButton.java:22: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead) [DrawAllocation]\n"
                                + "            text = new char[0];\n"
                                + "                   ~~~~~~~~~~~\n"
                                + "src/test/pkg/MyButton.java:27: Warning: Avoid object allocations during draw/layout operations (preallocate and reuse instead) [DrawAllocation]\n"
                                + "        TypedArray array = getContext().obtainStyledAttributes(new int[] { android.R.attr.listPreferredItemHeight });\n"
                                + "                                                               ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "0 errors, 2 warnings\n");
    }

    public void testKotlin() {
        // Regression test for issue 117793969:
        // Unit test from the Kotlin plugin.
        //noinspection all // Sample code
        lint().files(
                        kotlin(
                                ""
                                        + "import android.annotation.SuppressLint\n"
                                        + "import java.util.HashMap\n"
                                        + "import android.content.Context\n"
                                        + "import android.graphics.*\n"
                                        + "import android.util.AttributeSet\n"
                                        + "import android.util.SparseArray\n"
                                        + "import android.widget.Button\n"
                                        + "\n"
                                        + "@SuppressWarnings(\"unused\")\n"
                                        + "@Suppress(\"UsePropertyAccessSyntax\", \"UNUSED_VARIABLE\", \"unused\", \"UNUSED_PARAMETER\", \"DEPRECATION\")\n"
                                        + "class JavaPerformanceTest(context: Context, attrs: AttributeSet, defStyle: Int) : Button(context, attrs, defStyle) {\n"
                                        + "\n"
                                        + "    private var cachedRect: Rect? = null\n"
                                        + "    private var shader: LinearGradient? = null\n"
                                        + "    private var lastHeight: Int = 0\n"
                                        + "    private var lastWidth: Int = 0\n"
                                        + "\n"
                                        + "    override fun onDraw(canvas: android.graphics.Canvas) {\n"
                                        + "        super.onDraw(canvas)\n"
                                        + "\n"
                                        + "        // Various allocations:\n"
                                        + "        /*Avoid object allocations during draw/layout operations (preallocate and reuse instead)*/java.lang.String(\"foo\")/**/\n"
                                        + "        val s = /*Avoid object allocations during draw/layout operations (preallocate and reuse instead)*/java.lang.String(\"bar\")/**/\n"
                                        + "\n"
                                        + "        // This one should not be reported:\n"
                                        + "        @SuppressLint(\"DrawAllocation\")\n"
                                        + "        val i = 5\n"
                                        + "\n"
                                        + "        // Cached object initialized lazily: should not complain about these\n"
                                        + "        if (cachedRect == null) {\n"
                                        + "            cachedRect = Rect(0, 0, 100, 100)\n"
                                        + "        }\n"
                                        + "        if (cachedRect == null || cachedRect!!.width() != 50) {\n"
                                        + "            cachedRect = Rect(0, 0, 50, 100)\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        val b = java.lang.Boolean.valueOf(true)!! // auto-boxing\n"
                                        + "        sample(1, 2)\n"
                                        + "\n"
                                        + "        // Non-allocations\n"
                                        + "        super.animate()\n"
                                        + "        sample2(1, 2)\n"
                                        + "\n"
                                        + "        // This will involve allocations, but we don't track\n"
                                        + "        // inter-procedural stuff here\n"
                                        + "        someOtherMethod()\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    internal fun sample(foo: Int?, bar: Int) {\n"
                                        + "        sample2(foo!!, bar)\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    internal fun sample2(foo: Int, bar: Int) {\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    internal fun someOtherMethod() {\n"
                                        + "        // Allocations are okay here\n"
                                        + "        java.lang.String(\"foo\")\n"
                                        + "        val s = java.lang.String(\"bar\")\n"
                                        + "        val b = java.lang.Boolean.valueOf(true)!! // auto-boxing\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    protected fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int,\n"
                                        + "                            x: Boolean) {\n"
                                        + "        // wrong signature\n"
                                        + "        java.lang.String(\"not an error\")\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    protected fun onMeasure(widthMeasureSpec: Int) {\n"
                                        + "        // wrong signature\n"
                                        + "        java.lang.String(\"not an error\")\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    protected fun onLayout(changed: Boolean, left: Int, top: Int, right: Int,\n"
                                        + "                           bottom: Int, wrong: Int) {\n"
                                        + "        // wrong signature\n"
                                        + "        java.lang.String(\"not an error\")\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    protected fun onLayout(changed: Boolean, left: Int, top: Int, right: Int) {\n"
                                        + "        // wrong signature\n"
                                        + "        java.lang.String(\"not an error\")\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int,\n"
                                        + "                          bottom: Int) {\n"
                                        + "        /*Avoid object allocations during draw/layout operations (preallocate and reuse instead)*/java.lang.String(\"flag me\")/**/\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    @SuppressWarnings(\"null\") // not real code\n"
                                        + "    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {\n"
                                        + "        /*Avoid object allocations during draw/layout operations (preallocate and reuse instead)*/java.lang.String(\"flag me\")/**/\n"
                                        + "\n"
                                        + "        // Forbidden factory methods:\n"
                                        + "        /*Avoid object allocations during draw/layout operations (preallocate and reuse instead)*/Bitmap.createBitmap(100, 100, null)/**/\n"
                                        + "        /*Avoid object allocations during draw/layout operations (preallocate and reuse instead)*/android.graphics.Bitmap.createScaledBitmap(null, 100, 100, false)/**/\n"
                                        + "        /*Avoid object allocations during draw/layout operations (preallocate and reuse instead)*/BitmapFactory.decodeFile(null)/**/\n"
                                        + "        val canvas: Canvas? = null\n"
                                        + "        /*Avoid object allocations during draw operations: Use `Canvas.getClipBounds(Rect)` instead of `Canvas.getClipBounds()` which allocates a temporary `Rect`*/canvas!!.getClipBounds()/**/ // allocates on your behalf\n"
                                        + "        /*Avoid object allocations during draw operations: Use `Canvas.getClipBounds(Rect)` instead of `Canvas.getClipBounds()` which allocates a temporary `Rect`*/canvas.clipBounds/**/ // allocates on your behalf\n"
                                        + "        canvas.getClipBounds(null) // NOT an error\n"
                                        + "\n"
                                        + "        val layoutWidth = width\n"
                                        + "        val layoutHeight = height\n"
                                        + "        if (mAllowCrop && (mOverlay == null || mOverlay!!.width != layoutWidth ||\n"
                                        + "                           mOverlay!!.height != layoutHeight)) {\n"
                                        + "            mOverlay = Bitmap.createBitmap(layoutWidth, layoutHeight, Bitmap.Config.ARGB_8888)\n"
                                        + "            mOverlayCanvas = Canvas(mOverlay!!)\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        if (widthMeasureSpec == 42) {\n"
                                        + "            throw IllegalStateException(\"Test\") // NOT an allocation\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        // More lazy init tests\n"
                                        + "        var initialized = false\n"
                                        + "        if (!initialized) {\n"
                                        + "            java.lang.String(\"foo\")\n"
                                        + "            initialized = true\n"
                                        + "        }\n"
                                        + "\n"
                                        + "        // NOT lazy initialization\n"
                                        + "        if (!initialized || mOverlay == null) {\n"
                                        + "            /*Avoid object allocations during draw/layout operations (preallocate and reuse instead)*/java.lang.String(\"foo\")/**/\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    internal fun factories() {\n"
                                        + "        val i1 = 42\n"
                                        + "        val l1 = 42L\n"
                                        + "        val b1 = true\n"
                                        + "        val c1 = 'c'\n"
                                        + "        val f1 = 1.0f\n"
                                        + "        val d1 = 1.0\n"
                                        + "\n"
                                        + "        // The following should not generate errors:\n"
                                        + "        val i3 = Integer.valueOf(42)\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    private val mAllowCrop: Boolean = false\n"
                                        + "    private var mOverlayCanvas: Canvas? = null\n"
                                        + "    private var mOverlay: Bitmap? = null\n"
                                        + "\n"
                                        + "    override fun layout(l: Int, t: Int, r: Int, b: Int) {\n"
                                        + "        // Using \"this.\" to reference fields\n"
                                        + "        if (this.shader == null)\n"
                                        + "            this.shader = LinearGradient(0f, 0f, width.toFloat(), 0f, intArrayOf(0), null,\n"
                                        + "                                         Shader.TileMode.REPEAT)\n"
                                        + "\n"
                                        + "        val width = width\n"
                                        + "        val height = height\n"
                                        + "\n"
                                        + "        if (shader == null || lastWidth != width || lastHeight != height) {\n"
                                        + "            lastWidth = width\n"
                                        + "            lastHeight = height\n"
                                        + "\n"
                                        + "            shader = LinearGradient(0f, 0f, width.toFloat(), 0f, intArrayOf(0), null, Shader.TileMode.REPEAT)\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    fun inefficientSparseArray() {\n"
                                        + "        /*Use `new SparseIntArray(...)` instead for better performance*/SparseArray<Int>()/**/ // Use SparseIntArray instead\n"
                                        + "        SparseArray<Long>()    // Use SparseLongArray instead\n"
                                        + "        /*Use `new SparseBooleanArray(...)` instead for better performance*/SparseArray<Boolean>()/**/ // Use SparseBooleanArray instead\n"
                                        + "        SparseArray<Any>()  // OK\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    fun longSparseArray() {\n"
                                        + "        // but only minSdkVersion >= 17 or if has v4 support lib\n"
                                        + "        val myStringMap = HashMap<Long, String>()\n"
                                        + "    }\n"
                                        + "}\n"))
                // lazy initialization in onDraw using a when statement is unlikely
                .skipTestModes(TestMode.IF_TO_WHEN)
                .run()
                .expectInlinedMessages(true);
    }

    public void testKotlin2() {
        lint().files(
                        kotlin(
                                        "package test.pkg\n"
                                                + "\n"
                                                + "fun test(libs: List<String>) {\n"
                                                + "    @Suppress(\"PLATFORM_CLASS_MAPPED_TO_KOTLIN\") \n"
                                                + "    val libraryToIndexMap = HashMap<String, Integer>()\n"
                                                + "\n"
                                                + "    libs.asSequence().forEachIndexed { index,lib ->\n"
                                                + "        libraryToIndexMap.put(lib, Integer(index))\n"
                                                + "        \n"
                                                + "    }\n"
                                                + "}")
                                .indented())
                .issues(USE_VALUE_OF)
                .run()
                .expect(
                        ""
                                + "src/test/pkg/test.kt:8: Warning: Use Integer.valueOf(index) instead [UseValueOf]\n"
                                + "        libraryToIndexMap.put(lib, Integer(index))\n"
                                + "                                   ~~~~~~~~~~~~~~\n"
                                + "0 errors, 1 warnings");
    }

    public void testIsInitialized() {
        // Regression test for
        // 130892328: False positive for DrawAllocation with Kotlin nullable.isInitialized
        lint().files(
                        kotlin(
                                ""
                                        + "package com.android.demo\n"
                                        + "\n"
                                        + "import android.content.Context\n"
                                        + "import android.graphics.Bitmap\n"
                                        + "import android.graphics.Canvas\n"
                                        + "import android.view.View\n"
                                        + "\n"
                                        + "private lateinit var bitmap: Bitmap\n"
                                        + "\n"
                                        + "class MyView(context: Context) : View(context) {\n"
                                        + "    override fun onDraw(canvas: Canvas) {\n"
                                        + "        if (!::bitmap.isInitialized)\n"
                                        + "            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)\n"
                                        + "    }\n"
                                        + "}\n"))
                // lazy initialization in onDraw using a when statement is unlikely
                .skipTestModes(TestMode.IF_TO_WHEN)
                .run()
                .expectClean();
    }

    public void testWildcards() {
        String expected =
                ""
                        + "src/test/pkg/SparseLongArray.java:8: Warning: Use new SparseIntArray(...) instead for better performance [UseSparseArrays]\n"
                        + "        SparseArray<Integer, String> myStringMap = new SparseArray<>();\n"
                        + "                                                   ~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings";
        //noinspection all // Sample code
        lint().files(
                        manifest().minSdk(1),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.util.SparseArray;\n"
                                        + "import android.content.Context;\n"
                                        + "\n"
                                        + "public class SparseLongArray {\n"
                                        + "    public void test() { // but only minSdkVersion >= 18\n"
                                        + "        SparseArray<Integer, String> myStringMap = new SparseArray<>();\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expect(expected);
    }

    public void test173468525() {
        lint().files(
                        kotlin(
                                ""
                                        + "package test.pkg\n"
                                        + "private fun PackageState.appDerivedApkId() =\n"
                                        + "    AppDerivedApkId(\n"
                                        + "        with(installedDerivedApkId()) {\n"
                                        + "            when {\n"
                                        + "                isPresent -> asInt\n"
                                        + "                else -> null\n"
                                        + "            }\n"
                                        + "        }\n"
                                        + "    )\n"
                                        + "inline class AppDerivedApkId(val value: Int?)\n"),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "import java.util.OptionalInt;\n"
                                        + "\n"
                                        + "public abstract class PackageState {\n"
                                        + "    public abstract OptionalInt installedDerivedApkId();\n"
                                        + "\n"
                                        + "}"))
                .run()
                .expectClean();
    }

    public void testValueOfLongArgumentQuickfix() {
        // Makes sure we're correctly using source elements in quickfix
        lint().files(
                        kotlin(
                                ""
                                        + "package test.pkg.play\n"
                                        + "\n"
                                        + "import java.util.*\n"
                                        + "\n"
                                        + "private fun foo(installedDerivedApkId: OptionalInt) =\n"
                                        + "    Integer(\n"
                                        + "        with(installedDerivedApkId) {\n"
                                        + "            when {\n"
                                        + "                isPresent -> asInt\n"
                                        + "                else -> 0\n"
                                        + "            }\n"
                                        + "        }\n"
                                        + "    )\n"))
                .run()
                .expect(
                        ""
                                + "src/test/pkg/play/test.kt:6: Warning: Use Integer.valueOf(...) instead [UseValueOf]\n"
                                + "    Integer(\n"
                                + "    ^\n"
                                + "0 errors, 1 warnings")
                .expectFixDiffs(
                        ""
                                + "Autofix for src/test/pkg/play/test.kt line 6: Replace with valueOf():\n"
                                + "@@ -6 +6\n"
                                + "-     Integer(\n"
                                + "+     Integer.valueOf(");
    }

    public void testValueOfKotlinQuickfixes() {
        lint().files(
                        kotlin(
                                ""
                                        + "package test.pkg\n"
                                        + "\n"
                                        + "fun factories() {\n"
                                        + "    val i1 = Integer(42);\n"
                                        + "    val l1 = java.lang.Long(42L);\n"
                                        + "    val b1 = java.lang.Boolean(true);\n"
                                        + "    val c1 = Character('c');\n"
                                        + "    val f1 = java.lang.Float(1.0f);\n"
                                        + "    val d1 = java.lang.Double(1.0);\n"
                                        + "\n"
                                        + "    // The following should not generate errors:\n"
                                        + "    val i2 = foo.bar.Integer(42);\n"
                                        + "    val i3 = Integer.valueOf(42);\n"
                                        + "}\n"
                                        + "\n"
                                        + "class foo {\n"
                                        + "    class bar {\n"
                                        + "        class Integer(`val`: Int)\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expect(
                        ""
                                + "src/test/pkg/foo.kt:4: Warning: Use Integer.valueOf(42) instead [UseValueOf]\n"
                                + "    val i1 = Integer(42);\n"
                                + "             ~~~~~~~~~~~\n"
                                + "src/test/pkg/foo.kt:5: Warning: Use Long.valueOf(42L) instead [UseValueOf]\n"
                                + "    val l1 = java.lang.Long(42L);\n"
                                + "             ~~~~~~~~~~~~~~~~~~~\n"
                                + "src/test/pkg/foo.kt:6: Warning: Use Boolean.valueOf(true) instead [UseValueOf]\n"
                                + "    val b1 = java.lang.Boolean(true);\n"
                                + "             ~~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "src/test/pkg/foo.kt:7: Warning: Use Character.valueOf('c') instead [UseValueOf]\n"
                                + "    val c1 = Character('c');\n"
                                + "             ~~~~~~~~~~~~~~\n"
                                + "src/test/pkg/foo.kt:8: Warning: Use Float.valueOf(1.0f) instead [UseValueOf]\n"
                                + "    val f1 = java.lang.Float(1.0f);\n"
                                + "             ~~~~~~~~~~~~~~~~~~~~~\n"
                                + "src/test/pkg/foo.kt:9: Warning: Use Double.valueOf(1.0) instead [UseValueOf]\n"
                                + "    val d1 = java.lang.Double(1.0);\n"
                                + "             ~~~~~~~~~~~~~~~~~~~~~\n"
                                + "0 errors, 6 warnings")
                .expectFixDiffs(
                        ""
                                + "Autofix for src/test/pkg/foo.kt line 4: Replace with valueOf():\n"
                                + "@@ -4 +4\n"
                                + "-     val i1 = Integer(42);\n"
                                + "+     val i1 = Integer.valueOf(42);\n"
                                + "Autofix for src/test/pkg/foo.kt line 7: Replace with valueOf():\n"
                                + "@@ -7 +7\n"
                                + "-     val c1 = Character('c');\n"
                                + "+     val c1 = Character.valueOf('c');");
    }

    public void testInlineClasses() {
        // Regression test for
        // 203387678: "DrawAllocation" warning reported for value classes
        lint().files(
                        kotlin(
                                ""
                                        + "package test.pkg\n"
                                        + "\n"
                                        + "import android.annotation.SuppressLint\n"
                                        + "import android.graphics.Canvas\n"
                                        + "import android.widget.TextView\n"
                                        + "\n"
                                        + "@JvmInline\n"
                                        + "value class Minute1(val value: Int) {\n"
                                        + "}\n"
                                        + "inline class Minute2(val value: Int) { // 1.4\n"
                                        + "}\n"
                                        + "\n"
                                        + "@SuppressLint(\"ViewConstructor\", \"AppCompatCustomView\")\n"
                                        + "class InlineTest() : TextView(null) {\n"
                                        + "    override fun onDraw(canvas: Canvas?) {\n"
                                        + "        val minute1 = Minute1(0)\n"
                                        + "        val minute2 = Minute2(0)\n"
                                        + "        super.onDraw(canvas)\n"
                                        + "    }\n"
                                        + "}"))
                .run()
                .expectClean();
    }

    @SuppressWarnings("all") // Sample code
    private TestFile mLongSparseArray =
            java(
                    ""
                            + "package test.pkg;\n"
                            + "\n"
                            + "import java.util.HashMap;\n"
                            + "import java.util.Map;\n"
                            + "\n"
                            + "import android.content.Context;\n"
                            + "\n"
                            + "public class LongSparseArray {\n"
                            + "    public void test() { // but only minSdkVersion >= 17\n"
                            + "        Map<Long, String> myStringMap = new HashMap<Long, String>();\n"
                            + "    }\n"
                            + "}\n");

    @SuppressWarnings("all") // Sample code
    private TestFile mSparseLongArray =
            java(
                    ""
                            + "package test.pkg;\n"
                            + "\n"
                            + "import java.util.HashMap;\n"
                            + "import java.util.Map;\n"
                            + "\n"
                            + "import android.content.Context;\n"
                            + "\n"
                            + "public class SparseLongArray {\n"
                            + "    public void test() { // but only minSdkVersion >= 18\n"
                            + "        Map<Integer, Long> myStringMap = new HashMap<Integer, Long>();\n"
                            + "    }\n"
                            + "}\n");
}
