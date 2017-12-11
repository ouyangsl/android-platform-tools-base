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

public class AssignmentInConditionalDetectorTest extends LintDetectorTest {
    public void testAssignInIf() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "public class TestClass1 {\n" +
                        "   public static void main(String[] args) {\n" +
                        "       boolean a = false;\n" +
                        "       boolean b = false;\n" +
                        "       if (a = b) {\n" +
                        "           System.out.println(\"Hello\");\n" +
                        "       }" +
                        "   }" +
                        "}"))
                .run()
                .expect("src/test/pkg/TestClass1.java:6: Warning: Use == rather than = in a condition. [AssignmentInCondition]\n" +
                        "       if (a = b) {\n" +
                        "           ~~~~~\n" +
                        "0 errors, 1 warnings");
    }

    public void testAssignInWhile() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "public class TestClass1 {\n" +
                        "   public static void main(String[] args) {\n" +
                        "       boolean a = false;\n" +
                        "       boolean b = false;\n" +
                        "       while (a = b) {\n" +
                        "           System.out.println(\"Hello\");\n" +
                        "       }" +
                        "   }" +
                        "}"))
                .run()
                .expect("src/test/pkg/TestClass1.java:6: Warning: Use == rather than = in a condition. [AssignmentInCondition]\n" +
                        "       while (a = b) {\n" +
                        "              ~~~~~\n" +
                        "0 errors, 1 warnings");
    }

    public void testAssignInDoWhile() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "public class TestClass1 {\n" +
                        "   public static void main(String[] args) {\n" +
                        "       boolean a = false;\n" +
                        "       boolean b = false;\n" +
                        "       do {\n" +
                        "           System.out.println(\"Hello\");\n" +
                        "       } while (a = b);\n" +
                        "   }" +
                        "}"))
                .run()
                .expect("src/test/pkg/TestClass1.java:8: Warning: Use == rather than = in a condition. [AssignmentInCondition]\n" +
                        "       } while (a = b);\n" +
                        "                ~~~~~\n" +
                        "0 errors, 1 warnings");
    }

    public void testAssignInFor() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "public class TestClass1 {\n" +
                        "   public static void main(String[] args) {\n" +
                        "       boolean a = false;\n" +
                        "       boolean b = false;\n" +
                        "       for (;a = b;) {\n" +
                        "           System.out.println(\"Hello\");\n" +
                        "       }" +
                        "   }" +
                        "}"))
                .run()
                .expect("src/test/pkg/TestClass1.java:6: Warning: Use == rather than = in a condition. [AssignmentInCondition]\n" +
                        "       for (;a = b;) {\n" +
                        "             ~~~~~\n" +
                        "0 errors, 1 warnings");
    }

    @Override
    protected Detector getDetector() {
        return new AssignmentInConditionalDetector();
    }

    @Override
    protected List<Issue> getIssues() {
        return Collections.singletonList(AssignmentInConditionalDetector.ISSUE);
    }
}
