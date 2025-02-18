/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tools.lint.detector.api;

import com.android.tools.lint.UastEnvironment;
import com.android.tools.lint.checks.infrastructure.TestFile;
import com.android.tools.lint.checks.infrastructure.TestFiles;
import com.android.utils.Pair;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiVariable;

import junit.framework.TestCase;

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtProperty;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UFile;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UVariable;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("ClassNameDiffersFromFileName")
public class ConstantEvaluatorTest extends TestCase {
    private static void checkJavaUast(
            Object expected, @Language("JAVA") String source, final String targetVariable) {
        Pair<JavaContext, Disposable> pair =
                LintUtilsTest.parse(source, new File("src/test/pkg/Test.java"));
        checkUast(expected, pair, source, targetVariable);
    }

    private static void checkKotlinUast(
            Object expected, @Language("Kt") String source, final String targetVariable) {
        Pair<JavaContext, Disposable> pair =
                LintUtilsTest.parseKotlin(source, new File("src/test/pkg/Test.kt"));
        checkUast(expected, pair, source, targetVariable);
    }

    private static void checkUast(
            Object expected,
            Pair<JavaContext, Disposable> pair,
            String source,
            final String targetVariable) {
        JavaContext context = pair.getFirst();
        Disposable disposable = pair.getSecond();
        assertNotNull(context);
        UFile uFile = context.getUastFile();
        assertNotNull(uFile);

        // Find the expression
        final AtomicReference<UExpression> reference = new AtomicReference<>();
        uFile.accept(
                new AbstractUastVisitor() {
                    @Override
                    public boolean visitVariable(UVariable variable) {
                        String name = variable.getName();
                        if (name != null && name.equals(targetVariable)) {
                            reference.set(variable.getUastInitializer());
                        }

                        return super.visitVariable(variable);
                    }
                });

        UExpression expression = reference.get();
        Object actual = ConstantEvaluator.evaluate(context, expression);
        if (expected == null) {
            assertNull(actual);
        } else {
            assertNotNull(
                    "Couldn't compute value for "
                            + source
                            + ", expected "
                            + expected
                            + " but was "
                            + actual,
                    actual);
            assertSame(expected.getClass(), actual.getClass());
            if (expected instanceof Object[] && actual instanceof Object[]) {
                assertEquals(
                        Arrays.toString((Object[]) expected), Arrays.toString((Object[]) actual));
                assertTrue(Arrays.equals((Object[]) expected, (Object[]) actual));
            } else if (expected instanceof int[] && actual instanceof int[]) {
                assertEquals(Arrays.toString((int[]) expected), Arrays.toString((int[]) actual));
            } else if (expected instanceof boolean[] && actual instanceof boolean[]) {
                assertEquals(
                        Arrays.toString((boolean[]) expected), Arrays.toString((boolean[]) actual));
            } else if (expected instanceof byte[] && actual instanceof byte[]) {
                assertEquals(Arrays.toString((byte[]) expected), Arrays.toString((byte[]) actual));
            } else {
                assertEquals(expected.toString(), actual.toString());
                assertEquals(expected, actual);
            }
        }
        if (expected instanceof String) {
            assertEquals(expected, ConstantEvaluator.evaluateString(context, expression, false));
        }
        Disposer.dispose(disposable);
    }

    private static void checkPsi(
            Object expected,
            JavaContext context,
            Disposable disposable,
            String source,
            String targetVariable) {
        PsiFile file = context.getPsiFile();
        // Find the expression
        final AtomicReference<PsiElement> reference = new AtomicReference<>();
        file.accept(
                new PsiRecursiveElementVisitor() {
                    @Override
                    public void visitElement(@NotNull PsiElement element) {
                        super.visitElement(element);
                        if (element instanceof PsiVariable) {
                            PsiVariable variable = (PsiVariable) element;
                            if (variable.getName().equals(targetVariable)) {
                                reference.set(variable.getInitializer());
                            }
                        } else if (element instanceof KtProperty) {
                            KtProperty property = (KtProperty) element;
                            if (property.getName().equals(targetVariable)) {
                                reference.set(property);
                            }
                        }
                    }
                });
        PsiElement expression = reference.get();
        assertNotNull(expression);
        Object actual = ConstantEvaluator.evaluate(context, expression);
        if (expected == null) {
            assertNull(actual);
        } else {
            assertNotNull(
                    "Couldn't compute value for "
                            + source
                            + ", expected "
                            + expected
                            + " but was "
                            + actual,
                    actual);
            assertSame(expected.getClass(), actual.getClass());
            if (expected instanceof Object[] && actual instanceof Object[]) {
                assertEquals(
                        Arrays.toString((Object[]) expected), Arrays.toString((Object[]) actual));
                assertTrue(Arrays.equals((Object[]) expected, (Object[]) actual));
            } else if (expected instanceof int[] && actual instanceof int[]) {
                assertEquals(Arrays.toString((int[]) expected), Arrays.toString((int[]) actual));
            } else if (expected instanceof boolean[] && actual instanceof boolean[]) {
                assertEquals(
                        Arrays.toString((boolean[]) expected), Arrays.toString((boolean[]) actual));
            } else if (expected instanceof byte[] && actual instanceof byte[]) {
                assertEquals(Arrays.toString((byte[]) expected), Arrays.toString((byte[]) actual));
            } else {
                assertEquals(expected.toString(), actual.toString());
                assertEquals(expected, actual);
            }
        }
        if (expected instanceof String) {
            assertEquals(expected, ConstantEvaluator.evaluateString(context, expression, false));
        }
        Disposer.dispose(disposable);
    }

    private static void checkJavaPsi(
            Object expected, @Language("JAVA") String source, String targetVariable) {
        Pair<JavaContext, Disposable> pair =
                LintUtilsTest.parse(source, new File("src/test/pkg/Test.java"));
        JavaContext context = pair.getFirst();
        Disposable disposable = pair.getSecond();
        assertNotNull(context);
        PsiFile javaFile = context.getPsiFile();
        assertNotNull(javaFile);

        checkPsi(expected, context, disposable, source, targetVariable);
    }

    private static void checkKotlinPsi(
            Object expected, @Language("Kt") String source, String targetVariable) {
        Pair<JavaContext, Disposable> pair =
                LintUtilsTest.parseKotlin(source, new File("src/test/pkg/Test.kt"));
        JavaContext context = pair.getFirst();
        Disposable disposable = pair.getSecond();
        assertNotNull(context);
        PsiFile javaFile = context.getPsiFile();
        assertNotNull(javaFile);

        checkPsi(expected, context, disposable, source, targetVariable);
    }

    private static void check(
            Object expected, @Language("JAVA") String source, final String targetVariable) {
        checkJavaUast(expected, source, targetVariable);
        checkJavaPsi(expected, source, targetVariable);
        UastEnvironment.disposeApplicationEnvironment();
    }

    private static void checkKotlin(
            Object expected, @Language("Kt") String source, String targetVariable) {
        checkKotlinUast(expected, source, targetVariable);
        checkKotlinPsi(expected, source, targetVariable);
        UastEnvironment.disposeApplicationEnvironment();
    }

    private static void checkStatements(
            Object expected, String statementsSource, final String targetVariable) {
        @Language("JAVA")
        String source =
                ""
                        + "package test.pkg;\n"
                        + "public class Test {\n"
                        + "    public void test() {\n"
                        + "        "
                        + statementsSource
                        + "\n"
                        + "    }\n"
                        + "    public static final int MY_INT_FIELD = 5;\n"
                        + "    public static final boolean MY_BOOLEAN_FIELD = true;\n"
                        + "    public static final String MY_STRING_FIELD = \"test\";\n"
                        + "}\n";

        check(expected, source, targetVariable);
    }

    private static void checkKotlinStatements(
            Object expected, String statements, String targetVariable) {
        @Language("Kt")
        String source =
                ""
                        + "package test.pkg\n"
                        + "class Test {\n"
                        + "    fun test() {\n"
                        + statements
                        + "\n"
                        + "    }\n"
                        + "    const val MY_INT_FIELD = 5;\n"
                        + "    const val MY_BOOLEAN_FIELD = true;\n"
                        + "    const val MY_STRING_FIELD = \"test\";\n"
                        + "    companion object {\n"
                        + "        val someField = \"something\";\n"
                        + "    }\n"
                        + "}\n";

        checkKotlin(expected, source, targetVariable);
    }

    private static void checkExpression(Object expected, String expressionSource) {
        checkExpression(expected, expressionSource, true);
    }

    private static void checkExpression(
            Object expected, String expressionSource, boolean includePsi) {
        @Language("JAVA")
        String source =
                ""
                        + "package test.pkg;\n"
                        + "public class Test {\n"
                        + "    static final Object expression = "
                        + expressionSource
                        + ";\n"
                        + "    static final Object reference = expression;\n"
                        + "    public static final int MY_INT_FIELD = 5;\n"
                        + "    public static final boolean MY_BOOLEAN_FIELD = true;\n"
                        + "    public static final String MY_STRING_FIELD = \"test\";\n"
                        + "}\n";

        check(expected, source, "expression");
        if (includePsi) {
            check(expected, source, "reference");
        }
    }

    private static void checkKotlinExpression(
            Object expected, String expressionSource, boolean handlePsiRef) {
        @Language("Kt")
        String source =
                ""
                        + "package test.pkg\n"
                        + "class Test {\n"
                        + "    companion object {\n"
                        + "        val expression = "
                        + expressionSource
                        + "\n"
                        + "        private val expressionField = "
                        + expressionSource
                        + "\n"
                        + "        val methodRef = expression\n"
                        + "        val fieldRef = expressionField\n"
                        + "        const val MY_INT_FIELD = 5;\n"
                        + "        const val MY_BOOLEAN_FIELD = true;\n"
                        + "        const val MY_STRING_FIELD = \"test\";\n"
                        + "        val someField = \"something\";\n"
                        + "    }\n"
                        + "}\n";

        checkKotlinUast(expected, source, "expression");
        if (handlePsiRef) {
            // Also reference this via variables; this causes different constant evaluator
            // machinery to run (when we resolve, we end up in PSI, and evaluate
            // from there). And there are different paths for method properties
            // and field properties, so try both.
            checkKotlinUast(expected, source, "methodRef");
            checkKotlinUast(expected, source, "fieldRef");
        }
        UastEnvironment.disposeApplicationEnvironment();
    }

    public void testStrings() {
        checkExpression(null, "null");
        checkExpression("hello", "\"hello\"");
        checkExpression("abcd", "\"ab\" + \"cd\"");
    }

    public void testArrays() {
        checkExpression(new int[] {1, 2, 3}, "new int[] { 1,2,3] }", false);
        checkExpression(new int[0], "new int[0]");
        checkExpression(new byte[0], "new byte[0]");
    }

    public void testLargeArrays() {
        checkExpression(ArrayReference.of(Byte.TYPE, 100, 2), "new byte[100][]");
        checkExpression(ArrayReference.of(Byte.TYPE, 100, 1), "new byte[100]");
        checkExpression(ArrayReference.of("java.lang.Integer", 100, 1), "new Integer[100]");
        checkExpression(100, "(new byte[100]).length");
        checkExpression(100, "(new Integer[100]).length");
    }

    public void testKotlin() {
        Object expected3 = ArrayReference.of(Integer.TYPE, 100, 1);
        checkKotlinExpression(expected3, "IntArray(100)", false);
        checkKotlinExpression(100, "IntArray(100).size", false);
        checkKotlinExpression(1000, "kotlin.Array<String>(1000).size", false);
        Object expected2 = ArrayReference.of(String.class, 1000, 1);
        checkKotlinExpression(expected2, "Array<String>(1000)", false);
        Object expected1 = ArrayReference.of(String.class, 1000, 1);
        checkKotlinExpression(expected1, "kotlin.Array<String>(1000)", false);
        checkKotlinExpression(new Integer[] {1, 2, 3, 4}, "arrayOf(1,2,3,4)", false);
        checkKotlinExpression(3, "arrayOf(1,2,3,4)[2]", false);
        checkKotlinExpression(4, "arrayOf(1,2,3,4).size", false);
        Object expected = ArrayReference.of(String.class, 1000, 1);
        checkKotlinExpression(expected, "arrayOfNulls<String>(1000)", false);

        checkKotlinExpression(null, "null", true);
        checkKotlinExpression("hello", "\"hello\"", true);
        checkKotlinExpression("abcd", "\"ab\" + \"cd\"", true);
        checkKotlinExpression(1000, "arrayOfNulls<String>(1000).size", false);
        checkKotlinExpression("hello", "   \"\"\"    hello\"\"\".trimIndent()", false);
        checkKotlinExpression("<resources>\n</resources>", "\"<resources>\\n</resources>\"", true);
        checkKotlinExpression(true, "true", true);
        checkKotlinExpression(false, "false", true);
        checkKotlinExpression(-1, "-1", true);
        checkKotlinExpression(false, "false && true", true);
        checkKotlinExpression(true, "false || true", true);
        checkKotlinExpression(true, "!false", true);
        checkKotlinExpression(false, "false && true && true", true);
        checkKotlinExpression(true, "false || false || true", true);
        checkKotlinExpression(-2, "1 - 3", true);
    }

    public void testUltraLightPropertyInitializer() {
        @Language("Kt")
        String source =
                ""
                        + "package test.pkg\n"
                        + "class Test {\n"
                        + "    fun test() {\n"
                        + "        val x = someField\n"
                        + "    }\n"
                        + "    companion object {\n"
                        + "        // effectively constant\n"
                        + "        val someField = \"something\";\n"
                        + "    }\n"
                        + "}\n";

        Pair<JavaContext, Disposable> pair =
                LintUtilsTest.parseKotlin(source, new File("src/test/pkg/Test.kt"));

        JavaContext context = pair.getFirst();
        Disposable disposable = pair.getSecond();
        assertNotNull(context);
        UFile uFile = context.getUastFile();
        assertNotNull(uFile);

        // Find the expression
        final AtomicReference<UExpression> reference = new AtomicReference<>();
        uFile.accept(
                new AbstractUastVisitor() {
                    @Override
                    public boolean visitVariable(UVariable variable) {
                        String name = variable.getName();
                        if (name != null && name.equals("x")) {
                            reference.set(variable.getUastInitializer());
                        }
                        return super.visitVariable(variable);
                    }
                });

        UExpression expression = reference.get();
        Object actual = ConstantEvaluator.evaluate(context, expression);
        assertEquals("something", actual);

        // Resolve test for ultra light
        assertTrue(expression instanceof UReferenceExpression);
        PsiElement resolved = ((UReferenceExpression) expression).resolve();
        assertNotNull(resolved);
        Object psiActual = ConstantEvaluator.evaluate(context, resolved);
        assertEquals("something", psiActual);
        Disposer.dispose(disposable);
        UastEnvironment.disposeApplicationEnvironment();
    }

    public void testEnumClassVal() {
        // Regression test for
        // https://issuetracker.google.com/239767506
        @Language("Kt")
        String source =
                ""
                        + "package test.pkg\n"
                        + "enum class E(val x: Int) {\n"
                        + " A(1), B(2)\n"
                        + "}\n"
                        + "class Test {\n"
                        + "    fun f() {\n"
                        + "        val a = E.A.x\n"
                        + "        val b = E.B.x\n"
                        + "    }\n"
                        + "}";
        checkKotlinUast(1, source, "a");
    }

    public void testBooleans() {
        checkExpression(true, "true");
        checkExpression(false, "false");
        checkExpression(false, "false && true");
        checkExpression(true, "false || true");
        checkExpression(true, "!false");
    }

    public void testPolyadicBooleans() {
        checkExpression(false, "false && true && true");
        checkExpression(true, "false || false || true");
        checkExpression(true, "false ^ false ^ true");
    }

    public void testChars() {
        checkExpression('a', "'a'");
        checkExpression('\007', "'\007'");
    }

    public void testCasts() {
        checkExpression(1, "(int)1");
        checkExpression(1L, "(long)1");
        checkExpression(1, "(int)1.1f");
        checkExpression((short) 65537, "(short)65537");
        checkExpression((byte) 1023, "(byte)1023");
        checkExpression(1.5, "(double)1.5f");
        checkExpression(-5.0, "(double)-5");
    }

    public void testArithmetic() {
        checkExpression(1, "1");
        checkExpression(1L, "1L");
        checkExpression(4, "1 + 3");
        checkExpression(-2, "1 - 3");
        checkExpression(10, "2 * 5");
        checkExpression(2, "10 / 5");
        checkExpression(1, "11 % 5");
        checkExpression(8, "1 << 3");
        checkExpression(16, "32 >> 1");
        checkExpression(16, "32 >>> 1");
        checkExpression(5, "5 | 1");
        checkExpression(1, "5 & 1");
        checkExpression(~5, "~5");
        checkExpression(~(long) 5, "~(long)5");
        checkExpression(~(short) 5, "~(short)5");
        checkExpression(~(char) 5, "~(char)5");
        checkExpression(~(byte) 5, "~(byte)5");
        checkExpression(-(long) 5, "-(long)5");
        checkExpression(-(short) 5, "-(short)5");
        checkExpression(-(byte) 5, "-(byte)5");
        checkExpression(-(double) 5, "-(double)5");
        checkExpression(-(float) 5, "-(float)5");
        checkExpression(-2, "1 + -3");
        checkExpression(null, "1 / 0");
        checkExpression(null, "1 % 0");

        checkExpression(false, "11 == 5");
        checkExpression(true, "11 == 11");
        checkExpression(true, "11 != 5");
        checkExpression(false, "11 != 11");
        checkExpression(true, "11 > 5");
        checkExpression(false, "5 > 11");
        checkExpression(false, "11 < 5");
        checkExpression(true, "5 < 11");
        checkExpression(true, "11 >= 5");
        checkExpression(false, "5 >= 11");
        checkExpression(false, "11 <= 5");
        checkExpression(true, "5 <= 11");

        checkExpression(3.5f, "1.0f + 2.5f");
    }

    public void testPolyadicArithmetic() {
        checkExpression(9, "1 + 3 + 5");
        checkExpression(94, "100 - 3 - 3");
        checkExpression(100, "2 * 5 * 10");
        checkExpression(1, "10 / 5 / 2");
        checkExpression(null, "10 / 0 / 2");
        checkExpression(null, "10 % 0 % 2");
        checkExpression(16, "1 << 3 << 1");
        checkExpression(8, "32 >> 1 >> 1");
        checkExpression(8, "32 >>> 1 >>> 1");
        checkExpression(5, "5 | 1 | 1");
        checkExpression(1, "5 & 1 & 1");
        checkExpression(true, "true && true && true");
    }

    public void testFieldReferences() {
        checkExpression(5, "MY_INT_FIELD");
        checkExpression("test", "MY_STRING_FIELD");
        checkExpression("prefix-test-postfix", "\"prefix-\" + MY_STRING_FIELD + \"-postfix\"");
        checkExpression(-4, "3 - (MY_INT_FIELD + 2)");
    }

    public void testStatements() {
        checkStatements(
                9,
                ""
                        + "int x = +5;\n"
                        + "int y = x;\n"
                        + "int w;\n"
                        + "w = -1;\n"
                        + "int z = x + 5 + w;\n",
                "z");
        checkStatements(
                "hello world",
                ""
                        + "String initial = \"hello\";\n"
                        + "String other;\n"
                        + "other = \" world\";\n"
                        + "String finalString = initial + other;\n",
                "finalString");
    }

    public void testStringConcatenation() {
        checkStatements(
                "hello",
                ""
                        + "String s1 = \"el\";\n"
                        + "char c1 = 'h';\n"
                        + "char c2 = 'l';\n"
                        + "char s2 = \"o\";\n"
                        + "String finalString = ((c1 + s1) + c2) + s2;",
                "finalString");
    }

    public void testConditionals() {
        checkStatements(
                -5,
                ""
                        + "boolean condition = false;\n"
                        + "condition = !condition;\n"
                        + "int z = condition ? -5 : 4;\n",
                "z");
        checkStatements(
                -4, "boolean condition = true && false;\nint z = condition ? 5 : -4;\n", "z");
    }

    public void testIfStatement() {
        checkStatements(
                null,
                ""
                        + "var condition = true;\n"
                        + "var trueBranch = 3;\n"
                        + "var falseBranch = -3;\n"
                        + "var z = 0;\n"
                        + "if (condition) {\n"
                        + "    z = trueBranch;\n"
                        + "} else {\n"
                        + "    z = falseBranch;\n"
                        + "}\n"
                        + "var y = z;\n",
                "y");
    }

    public void testIndirectConstants() {
        // Scenario reduced from VersionChecksTest#testMinorVersionsOperators under
        // TestMode.FULLY_QUALIFIED
        TestFile file1 =
                TestFiles.kotlin(
                        "\n"
                            + "package test.pkg\n"
                            + "\n"
                            + "fun testOperatorCornerCases() {\n"
                            + "    if (1 < test.pkg.CONSTANT_1) { }\n"
                            + "    if (2 < test.pkg.CONSTANT_2) { }\n"
                            + "}\n"
                            + "val CONSTANT_1 ="
                            + " android.os.Build.VERSION_CODES_FULL.VANILLA_ICE_CREAM_1\n"
                            + "const val CONSTANT_2 ="
                            + " android.os.Build.VERSION_CODES_FULL.VANILLA_ICE_CREAM_1\n");

        TestFile file2 =
                TestFiles.java(
                        "package android.os;\n"
                            + "\n"
                            + "public class Build {\n"
                            + "    public static class VERSION_CODES {\n"
                            + "        public static final int VANILLA_ICE_CREAM = 35;\n"
                            + "    }\n"
                            + "    public static class VERSION_CODES_FULL {\n"
                            + "        private VERSION_CODES_FULL() {}\n"
                            + "\n"
                            + "        private static final int SDK_INT_MULTIPLIER = 100000;\n"
                            + "\n"
                            + "        public static final int VANILLA_ICE_CREAM_1 ="
                            + " android.os.Build.VERSION_CODES_FULL.SDK_INT_MULTIPLIER *"
                            + " android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM + 1;\n"
                            + "    }\n"
                            + "}");
        Pair<List<JavaContext>, Disposable> parsed = LintUtilsTest.parseAll(file1, file2);
        List<JavaContext> contexts = parsed.getFirst();
        Disposable disposable = parsed.getSecond();
        try {
            JavaContext kotlinFile =
                    contexts.get(0).file.getName().equals("Build.java")
                            ? contexts.get(1)
                            : contexts.get(0);
            kotlinFile
                    .getUastFile()
                    .accept(
                            new AbstractUastVisitor() {
                                @Override
                                public boolean visitQualifiedReferenceExpression(
                                        @NotNull UQualifiedReferenceExpression node) {
                                    PsiElement sourcePsi = node.getSourcePsi();
                                    if (sourcePsi != null
                                            && sourcePsi
                                                    .getText()
                                                    .startsWith("test.pkg.CONSTANT_")) {
                                        Object constant = ConstantEvaluator.evaluate(null, node);
                                        assertEquals(3500001, constant);
                                    }
                                    return super.visitQualifiedReferenceExpression(node);
                                }
                            });
        } finally {
            Disposer.dispose(disposable);
        }
    }
}
