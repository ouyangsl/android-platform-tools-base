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

package com.android.tools.lint.checks;

import com.android.tools.lint.checks.infrastructure.LintDetectorTest;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import java.util.Collections;
import java.util.List;

public class ReferenceEqualityDetectorTest extends LintDetectorTest {
    public void testCompareArrays() {
        lint().files(
                java("" +
                    "package test.pkg;\n" +
                    "public class TestClass1 {" +
                    "   public static void main(String[] args) {" +
                    "       int[] arr1 = {3,4,5};\n" +
                        "   int[] arr2 = {3,4,5};\n" +
                        "   if (arr1 == arr2) {\n" +
                        "       System.out.println(\"Hello\");\n" +
                        "   }" +
                        "}" +
                    "}"))
                .run()
                .expect("src/test/pkg/TestClass1.java:4: Warning: You should compare arrays using .equals() rather than ==. [ImproperReferenceEquality]\n" +
                        "   if (arr1 == arr2) {\n" +
                        "       ~~~~\n" +
                        "src/test/pkg/TestClass1.java:4: Warning: You should compare arrays using .equals() rather than ==. [ImproperReferenceEquality]\n" +
                        "   if (arr1 == arr2) {\n" +
                        "               ~~~~\n" +
                        "0 errors, 2 warnings");
    }

    public void testCompareInteger() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "public class TestClass1 {" +
                        "   public static void main(String[] args) {" +
                        "       Integer x = new Integer(1);\n" +
                        "       Integer y = new Integer(1);\n" +
                        "   if (x == y) {\n" +
                        "       System.out.println(\"Hello\");\n" +
                        "   }" +
                        "}" +
                        "}"))
                .run()
                .expect("src/test/pkg/TestClass1.java:4: Warning: You should compare values of the type java.lang.Integer using .equals() rather than ==. [ImproperReferenceEquality]\n" +
                        "   if (x == y) {\n" +
                        "       ~\n" +
                        "src/test/pkg/TestClass1.java:4: Warning: You should compare values of the type java.lang.Integer using .equals() rather than ==. [ImproperReferenceEquality]\n" +
                        "   if (x == y) {\n" +
                        "            ~\n" +
                        "0 errors, 2 warnings");
    }

    public void testCompareFloat() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "public class TestClass1 {" +
                        "   public static void main(String[] args) {" +
                        "       Float x = new Float(1.0);\n" +
                        "       Float y = new Float(1.0);\n" +
                        "   if (x == y) {\n" +
                        "       System.out.println(\"Hello\");\n" +
                        "   }" +
                        "}" +
                        "}"))
                .run()
                .expect("src/test/pkg/TestClass1.java:4: Warning: You should compare values of the type java.lang.Float using .equals() rather than ==. [ImproperReferenceEquality]\n" +
                        "   if (x == y) {\n" +
                        "       ~\n" +
                        "src/test/pkg/TestClass1.java:4: Warning: You should compare values of the type java.lang.Float using .equals() rather than ==. [ImproperReferenceEquality]\n" +
                        "   if (x == y) {\n" +
                        "            ~\n" +
                        "0 errors, 2 warnings");
    }

    public void testCompareByte() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "public class TestClass1 {" +
                        "   public static void main(String[] args) {" +
                        "       Byte x = new Byte((byte) 1);\n" +
                        "       Byte y = new Byte((byte) 1);\n" +
                        "   if (x == y) {\n" +
                        "       System.out.println(\"Hello\");\n" +
                        "   }" +
                        "}" +
                        "}"))
                .run()
                .expect("src/test/pkg/TestClass1.java:4: Warning: You should compare values of the type java.lang.Byte using .equals() rather than ==. [ImproperReferenceEquality]\n" +
                        "   if (x == y) {\n" +
                        "       ~\n" +
                        "src/test/pkg/TestClass1.java:4: Warning: You should compare values of the type java.lang.Byte using .equals() rather than ==. [ImproperReferenceEquality]\n" +
                        "   if (x == y) {\n" +
                        "            ~\n" +
                        "0 errors, 2 warnings");
    }

    public void testCompareDouble() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "public class TestClass1 {" +
                        "   public static void main(String[] args) {" +
                        "       Double x = new Double(1.0);\n" +
                        "       Double y = new Double(1.0);\n" +
                        "   if (x == y) {\n" +
                        "       System.out.println(\"Hello\");\n" +
                        "   }" +
                        "}" +
                        "}"))
                .run()
                .expect("src/test/pkg/TestClass1.java:4: Warning: You should compare values of the type java.lang.Double using .equals() rather than ==. [ImproperReferenceEquality]\n" +
                        "   if (x == y) {\n" +
                        "       ~\n" +
                        "src/test/pkg/TestClass1.java:4: Warning: You should compare values of the type java.lang.Double using .equals() rather than ==. [ImproperReferenceEquality]\n" +
                        "   if (x == y) {\n" +
                        "            ~\n" +
                        "0 errors, 2 warnings");
    }

    public void testCompareString() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "public class TestClass1 {" +
                        "   public static void main(String[] args) {" +
                        "       String x = \"test\";\n" +
                        "       String y = \"test\";\n" +
                        "   if (x == y) {\n" +
                        "       System.out.println(\"Hello\");\n" +
                        "   }" +
                        "}" +
                        "}"))
                .run()
                .expect("src/test/pkg/TestClass1.java:4: Warning: You should compare values of the type java.lang.String using .equals() rather than ==. [ImproperReferenceEquality]\n" +
                        "   if (x == y) {\n" +
                        "       ~\n" +
                        "src/test/pkg/TestClass1.java:4: Warning: You should compare values of the type java.lang.String using .equals() rather than ==. [ImproperReferenceEquality]\n" +
                        "   if (x == y) {\n" +
                        "            ~\n" +
                        "0 errors, 2 warnings");
    }

    public void testCompareShort() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "public class TestClass1 {" +
                        "   public static void main(String[] args) {" +
                        "       Short x = new Short((short) 1);\n" +
                        "       Short y = new Short((short) 1);\n" +
                        "   if (x == y) {\n" +
                        "       System.out.println(\"Hello\");\n" +
                        "   }" +
                        "}" +
                        "}"))
                .run()
                .expect("src/test/pkg/TestClass1.java:4: Warning: You should compare values of the type java.lang.Short using .equals() rather than ==. [ImproperReferenceEquality]\n" +
                        "   if (x == y) {\n" +
                        "       ~\n" +
                        "src/test/pkg/TestClass1.java:4: Warning: You should compare values of the type java.lang.Short using .equals() rather than ==. [ImproperReferenceEquality]\n" +
                        "   if (x == y) {\n" +
                        "            ~\n" +
                        "0 errors, 2 warnings");
    }

    public void testCompareLong() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "public class TestClass1 {" +
                        "   public static void main(String[] args) {" +
                        "       Long x = new Long(1);\n" +
                        "       Long y = new Long(1);\n" +
                        "   if (x == y) {\n" +
                        "       System.out.println(\"Hello\");\n" +
                        "   }" +
                        "}" +
                        "}"))
                .run()
                .expect("src/test/pkg/TestClass1.java:4: Warning: You should compare values of the type java.lang.Long using .equals() rather than ==. [ImproperReferenceEquality]\n" +
                        "   if (x == y) {\n" +
                        "       ~\n" +
                        "src/test/pkg/TestClass1.java:4: Warning: You should compare values of the type java.lang.Long using .equals() rather than ==. [ImproperReferenceEquality]\n" +
                        "   if (x == y) {\n" +
                        "            ~\n" +
                        "0 errors, 2 warnings");
    }

    public void testCompareBoolean() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "public class TestClass1 {" +
                        "   public static void main(String[] args) {" +
                        "       Boolean x = new Boolean(true);\n" +
                        "       Boolean y = new Boolean(true);\n" +
                        "   if (x == y) {\n" +
                        "       System.out.println(\"Hello\");\n" +
                        "   }" +
                        "}" +
                        "}"))
                .run()
                .expect("src/test/pkg/TestClass1.java:4: Warning: You should compare values of the type java.lang.Boolean using .equals() rather than ==. [ImproperReferenceEquality]\n" +
                        "   if (x == y) {\n" +
                        "       ~\n" +
                        "src/test/pkg/TestClass1.java:4: Warning: You should compare values of the type java.lang.Boolean using .equals() rather than ==. [ImproperReferenceEquality]\n" +
                        "   if (x == y) {\n" +
                        "            ~\n" +
                        "0 errors, 2 warnings");
    }

    public void testCompareCharacter() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "public class TestClass1 {" +
                        "   public static void main(String[] args) {" +
                        "       Character x = new Character('x');\n" +
                        "       Character y = new Character('x');\n" +
                        "   if (x == y) {\n" +
                        "       System.out.println(\"Hello\");\n" +
                        "   }" +
                        "}" +
                        "}"))
                .run()
                .expect("src/test/pkg/TestClass1.java:4: Warning: You should compare values of the type java.lang.Character using .equals() rather than ==. [ImproperReferenceEquality]\n" +
                        "   if (x == y) {\n" +
                        "       ~\n" +
                        "src/test/pkg/TestClass1.java:4: Warning: You should compare values of the type java.lang.Character using .equals() rather than ==. [ImproperReferenceEquality]\n" +
                        "   if (x == y) {\n" +
                        "            ~\n" +
                        "0 errors, 2 warnings");
    }

    public void testCompareOptional() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "import java.util.Optional;\n" +
                        "public class TestClass1 {" +
                        "   public static void main(String[] args) {" +
                        "       Optional<Integer> x = Optional.of(1);\n" +
                        "       Optional<Integer> y = Optional.of(1);\n" +
                        "   if (x == y) {\n" +
                        "       System.out.println(\"Hello\");\n" +
                        "   }" +
                        "}" +
                        "}"))
                .run()
                .expect("src/test/pkg/TestClass1.java:5: Warning: You should compare values of the type java.util.Optional using .equals() rather than ==. [ImproperReferenceEquality]\n" +
                        "   if (x == y) {\n" +
                        "       ~\n" +
                        "src/test/pkg/TestClass1.java:5: Warning: You should compare values of the type java.util.Optional using .equals() rather than ==. [ImproperReferenceEquality]\n" +
                        "   if (x == y) {\n" +
                        "            ~\n" +
                        "0 errors, 2 warnings");
    }

    @Override
    protected Detector getDetector() {
        return new ReferenceEqualityDetector();
    }

    @Override
    protected List<Issue> getIssues() {
        return Collections.singletonList(ReferenceEqualityDetector.ISSUE);
    }
}
