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

import static com.android.tools.lint.checks.AnnotationDetectorTest.SUPPORT_ANNOTATIONS_JAR_BASE64_GZIP;

public class NullAnnotationUsageDetectorTest extends LintDetectorTest {
    public void testAssignNonNullToNull() throws Exception {
        lint().files(
                java("package test.pkg;\n" +
                        "import android.support.annotation.NonNull;\n" +
                        "public class TestClass1 {\n" +
                        "   public static void main(String[] args) {\n" +
                        "       @NonNull String y = \"\";\n" +
                        "       y = null;\n" +
                        "   }\n" +
                        "}"),
                mSupportClasspath,
                mSupportJar)
        .run()
        .expect("src/test/pkg/TestClass1.java:6: Warning: You should not assign a possibly null value to a variable annotated with @NonNull. [ImproperNullAnnotationUsage]\n" +
                "       y = null;\n" +
                "       ~~~~~~~~\n" +
                "0 errors, 1 warnings");
    }

    public void testAssignNonNullToNullable() {
        lint().files(
                java("package test.pkg;\n" +
                        "import android.support.annotation.NonNull;\n" +
                        "import android.support.annotation.Nullable;\n" +
                        "public class TestClass1 {\n" +
                        "   public static void main(String[] args) {\n" +
                        "       @Nullable String x = null;\n" +
                        "       @NonNull String y = \"\";\n" +
                        "       y = x;\n" +
                        "   }\n" +
                        "}"),
                mSupportClasspath,
                mSupportJar)
                .run()
                .expect("src/test/pkg/TestClass1.java:8: Warning: You should not assign a possibly null value to a variable annotated with @NonNull. [ImproperNullAnnotationUsage]\n" +
                        "       y = x;\n" +
                        "       ~~~~~\n" +
                        "0 errors, 1 warnings");
    }

    public void testAssignNonNullToNullableFunction() {
        lint().files(
                java("package test.pkg;\n" +
                        "import android.support.annotation.NonNull;\n" +
                        "import android.support.annotation.Nullable;\n" +
                        "public class TestClass1 {\n" +
                        "   public static void main(String[] args) {\n" +
                        "       @NonNull String y = \"\";\n" +
                        "       y = nullableFunction();\n" +
                        "   }\n" +
                        "   @Nullable\n" +
                        "   public static String nullableFunction() {\n" +
                        "       return null;\n" +
                        "   }\n" +
                        "}"),
                mSupportClasspath,
                mSupportJar)
                .run()
                .expect("src/test/pkg/TestClass1.java:7: Warning: You should not assign a possibly null value to a variable annotated with @NonNull. [ImproperNullAnnotationUsage]\n" +
                        "       y = nullableFunction();\n" +
                        "       ~~~~~~~~~~~~~~~~~~~~~~\n" +
                        "0 errors, 1 warnings");
    }

    public void testAssignNonNullToNullReturningFunction() {
        lint().files(
                java("package test.pkg;\n" +
                        "import android.support.annotation.NonNull;\n" +
                        "public class TestClass1 {\n" +
                        "   public static void main(String[] args) {\n" +
                        "       @NonNull String y = \"\";\n" +
                        "       y = nullableFunction();\n" +
                        "   }\n" +
                        "   public static String nullableFunction() {\n" +
                        "       return null;\n" +
                        "   }\n" +
                        "}"),
                mSupportClasspath,
                mSupportJar)
                .run()
                .expect("src/test/pkg/TestClass1.java:6: Warning: You should not assign a possibly null value to a variable annotated with @NonNull. [ImproperNullAnnotationUsage]\n" +
                        "       y = nullableFunction();\n" +
                        "       ~~~~~~~~~~~~~~~~~~~~~~\n" +
                        "0 errors, 1 warnings");
    }

    public void testNoInitializeNonNull() {
        lint().files(
                java("package test.pkg;\n" +
                        "import android.support.annotation.NonNull;\n" +
                        "public class TestClass1 {\n" +
                        "   public static void main(String[] args) {\n" +
                        "       @NonNull String y;\n" +
                        "   }\n" +
                        "}"),
                mSupportClasspath,
                mSupportJar)
                .run()
                .expect("src/test/pkg/TestClass1.java:5: Warning: You should initialize a @NonNull variable otherwise it will be null when declared. [ImproperNullAnnotationUsage]\n" +
                        "       @NonNull String y;\n" +
                        "       ~~~~~~~~~~~~~~~~~~\n" +
                        "0 errors, 1 warnings");
    }

    public void testInitializeNonNullToNull() {
        lint().files(
                java("package test.pkg;\n" +
                        "import android.support.annotation.NonNull;\n" +
                        "public class TestClass1 {\n" +
                        "   public static void main(String[] args) {\n" +
                        "       @NonNull String y = null;\n" +
                        "   }\n" +
                        "}"),
                mSupportClasspath,
                mSupportJar)
                .run()
                .expect("src/test/pkg/TestClass1.java:5: Warning: You should not assign a possibly null value to a variable annotated with @NonNull. [ImproperNullAnnotationUsage]\n" +
                        "       @NonNull String y = null;\n" +
                        "       ~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                        "0 errors, 1 warnings");
    }

    public void testInitializeNonNullToNullable() {
        lint().files(
                java("package test.pkg;\n" +
                        "import android.support.annotation.NonNull;\n" +
                        "import android.support.annotation.Nullable;\n" +
                        "public class TestClass1 {\n" +
                        "   public static void main(String[] args) {\n" +
                        "       @Nullable String x = null;\n" +
                        "       @NonNull String y = x;\n" +
                        "   }\n" +
                        "}"),
                mSupportClasspath,
                mSupportJar)
                .run()
                .expect("src/test/pkg/TestClass1.java:7: Warning: You should not assign a possibly null value to a variable annotated with @NonNull. [ImproperNullAnnotationUsage]\n" +
                        "       @NonNull String y = x;\n" +
                        "       ~~~~~~~~~~~~~~~~~~~~~~\n" +
                        "0 errors, 1 warnings");
    }

    public void testInitializeNonNullToNullableFunction() {
        lint().files(
                java("package test.pkg;\n" +
                        "import android.support.annotation.NonNull;\n" +
                        "import android.support.annotation.Nullable;\n" +
                        "public class TestClass1 {\n" +
                        "   public static void main(String[] args) {\n" +
                        "       @NonNull String y = nullableFunction();\n" +
                        "   }\n" +
                        "   @Nullable\n" +
                        "   public static String nullableFunction() {\n" +
                        "       return null;\n" +
                        "   }\n" +
                        "}"),
                mSupportClasspath,
                mSupportJar)
                .run()
                .expect("src/test/pkg/TestClass1.java:6: Warning: You should not assign a possibly null value to a variable annotated with @NonNull. [ImproperNullAnnotationUsage]\n" +
                        "       @NonNull String y = nullableFunction();\n" +
                        "       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                        "0 errors, 1 warnings");
    }

    public void testInitializeNonNullToNullReturningFunction() {
        lint().files(
                java("package test.pkg;\n" +
                        "import android.support.annotation.NonNull;\n" +
                        "public class TestClass1 {\n" +
                        "   public static void main(String[] args) {\n" +
                        "       @NonNull String y = nullableFunction();\n" +
                        "   }\n" +
                        "   public static String nullableFunction() {\n" +
                        "       return null;\n" +
                        "   }\n" +
                        "}"),
                mSupportClasspath,
                mSupportJar)
                .run()
                .expect("src/test/pkg/TestClass1.java:5: Warning: You should not assign a possibly null value to a variable annotated with @NonNull. [ImproperNullAnnotationUsage]\n" +
                        "       @NonNull String y = nullableFunction();\n" +
                        "       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                        "0 errors, 1 warnings");
    }

    public void testReturnNullFromNonNull() {
        lint().files(
                java("package test.pkg;\n" +
                        "import android.support.annotation.NonNull;\n" +
                        "public class TestClass1 {\n" +
                        "   @NonNull\n" +
                        "   public static String nullableFunction() {\n" +
                        "       return null;\n" +
                        "   }\n" +
                        "}"),
                mSupportClasspath,
                mSupportJar)
                .run()
                .expect("src/test/pkg/TestClass1.java:6: Warning: You should not return a possibly null value from a method annotated with @NonNull. [ImproperNullAnnotationUsage]\n" +
                        "       return null;\n" +
                        "       ~~~~~~~~~~~~\n" +
                        "0 errors, 1 warnings");
    }

    public void testReturnNullableFromNonNull() {
        lint().files(
                java("package test.pkg;\n" +
                        "import android.support.annotation.NonNull;\n" +
                        "import android.support.annotation.Nullable;" +
                        "public class TestClass1 {\n" +
                        "   @NonNull\n" +
                        "   public static String nullableFunction() {\n" +
                        "       @Nullable String x = null;\n" +
                        "       return x;\n" +
                        "   }\n" +
                        "}"),
                mSupportClasspath,
                mSupportJar)
                .run()
                .expect("src/test/pkg/TestClass1.java:7: Warning: You should not return a possibly null value from a method annotated with @NonNull. [ImproperNullAnnotationUsage]\n" +
                        "       return x;\n" +
                        "       ~~~~~~~~~\n" +
                        "0 errors, 1 warnings");
    }

    public void testReturnNullableFunctionFromNonNull() {
        lint().files(
                java("package test.pkg;\n" +
                        "import android.support.annotation.NonNull;\n" +
                        "import android.support.annotation.Nullable;" +
                        "public class TestClass1 {\n" +
                        "   @NonNull\n" +
                        "   public static String nullableFunction() {\n" +
                        "       return nullReturningFunction();\n" +
                        "   }\n" +
                        "   @Nullable\n" +
                        "   public static String nullReturningFunction() {\n" +
                        "       return null;\n" +
                        "   }\n" +
                        "}"),
                mSupportClasspath,
                mSupportJar)
                .run()
                .expect("src/test/pkg/TestClass1.java:6: Warning: You should not return a possibly null value from a method annotated with @NonNull. [ImproperNullAnnotationUsage]\n" +
                        "       return nullReturningFunction();\n" +
                        "       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                        "0 errors, 1 warnings");
    }

    public void testReturnNullReturningFunctionFromNonNull() {
        lint().files(
                java("package test.pkg;\n" +
                        "import android.support.annotation.NonNull;\n" +
                        "public class TestClass1 {\n" +
                        "   @NonNull\n" +
                        "   public static String nullableFunction() {\n" +
                        "       return nullReturningFunction();\n" +
                        "   }\n" +
                        "   public static String nullReturningFunction() {\n" +
                        "       return null;\n" +
                        "   }\n" +
                        "}"),
                mSupportClasspath,
                mSupportJar)
                .run()
                .expect("src/test/pkg/TestClass1.java:6: Warning: You should not return a possibly null value from a method annotated with @NonNull. [ImproperNullAnnotationUsage]\n" +
                        "       return nullReturningFunction();\n" +
                        "       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                        "0 errors, 1 warnings");
    }

    public void testPassNullToNonNullParam() {
        lint().files(
                java("package test.pkg;\n" +
                        "import android.support.annotation.NonNull;\n" +
                        "public class TestClass1 {\n" +
                        "   public static void main(String[] args) {\n" +
                        "       System.out.println(testFunction(null));\n" +
                        "   }\n" +
                        "   public static String[] testFunction(@NonNull String x) {\n" +
                        "       return x.split(\",\");\n" +
                        "   }\n" +
                        "}"),
                mSupportClasspath,
                mSupportJar)
                .run()
                .expect("src/test/pkg/TestClass1.java:5: Warning: This argument is marked @NonNull but the value passed in is nullable. [ImproperNullAnnotationUsage]\n" +
                        "       System.out.println(testFunction(null));\n" +
                        "                                       ~~~~\n" +
                        "0 errors, 1 warnings");
    }

    @Override
    protected Detector getDetector() {
        return new NullAnnotationUsageDetector();
    }

    @Override
    protected List<Issue> getIssues() {
        return Collections.singletonList(NullAnnotationUsageDetector.ISSUE);
    }

    // Copied from AnnotationDetectorTest.java in studio-master-dev
    private TestFile mSupportJar = base64gzip(SUPPORT_JAR_PATH,
            SUPPORT_ANNOTATIONS_JAR_BASE64_GZIP);
    private TestFile mSupportClasspath = classpath(SUPPORT_JAR_PATH);

    public static final String SUPPORT_JAR_PATH = "libs/support-annotations.jar";
}
