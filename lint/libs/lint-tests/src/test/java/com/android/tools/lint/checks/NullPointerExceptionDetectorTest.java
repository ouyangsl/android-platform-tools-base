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

public class NullPointerExceptionDetectorTest extends LintDetectorTest {
    public void testMethodCallOnNullable() {
        lint().files(java("" +
                "package test.pkg;\n" +
                "public class TestClass1 {\n" +
                "   public static void main(String[] args) {\n" +
                "       String x = null;\n" +
                "       if (x.equals(\"test\")) {\n" +
                "           System.out.println(\"hi\");\n" +
                "       }\n" +
                "   }\n" +
                "}\n"))
                .run()
                .expect("src/test/pkg/TestClass1.java:5: Warning: You should check this value before referencing something inside it, as it is potentially null. [PossibleNullPointerException]\n" +
                        "       if (x.equals(\"test\")) {\n" +
                        "           ~~~~~~~~~~~~~~~~\n" +
                        "0 errors, 1 warnings");
    }

    public void testCheckedMethodCallOnNullable() {
        lint().files(java("" +
                "package test.pkg;\n" +
                "public class TestClass1 {\n" +
                "   public static void main(String[] args) {\n" +
                "       String x = null;\n" +
                "       if (x != null && x.equals(\"test\")) {\n" +
                "           System.out.println(\"hi\");\n" +
                "       }\n" +
                "   }\n" +
                "}\n"))
                .run()
                .expect("No warnings.");
    }

    @Override
    protected Detector getDetector() {
        return new NullPointerExceptionDetector();
    }

    @Override
    protected List<Issue> getIssues() {
        return Collections.singletonList(NullPointerExceptionDetector.ISSUE);
    }
}
