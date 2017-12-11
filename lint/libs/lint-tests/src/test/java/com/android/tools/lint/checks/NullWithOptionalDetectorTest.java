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

public class NullWithOptionalDetectorTest extends LintDetectorTest {
    public void testInitializeToNull() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "import java.util.Optional;\n" +
                        "public class TestClass1 {\n" +
                        "   public static void main(String[] args) {\n" +
                        "       Optional<Integer> x = null;\n" +
                        "   }\n" +
                        "}"))
                .run()
                .expect("src/test/pkg/TestClass1.java:5: Warning: You should not initialize a variable of Optional type to a possibly null value. Consider using Optional.empty() instead. [NullUsageWithOptional]\n" +
                        "       Optional<Integer> x = null;\n" +
                        "       ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n" +
                        "0 errors, 1 warnings");
    }

    public void testReturnNull() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "import java.util.Optional;\n" +
                        "public class TestClass1 {\n" +
                        "   public static void main(String[] args) {\n" +
                        "       Optional<Integer> x;\n" +
                        "       x = null;\n" +
                        "   }\n" +
                        "}"))
                .run()
                .expect("src/test/pkg/TestClass1.java:6: Warning: You should not assign a possibly null to an Optional variable.Consider using Optional.empty() instead. [NullUsageWithOptional]\n" +
                        "       x = null;\n" +
                        "       ~~~~~~~~\n" +
                        "0 errors, 1 warnings");

    }
    public void testAssignToNull() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "import java.util.Optional;\n" +
                        "public class TestClass1 {\n" +
                        "   public static Optional<String> test() {\n" +
                        "       return null;\n" +
                        "   }\n" +
                        "}"))
                .run()
                .expect("src/test/pkg/TestClass1.java:5: Warning: You should not return a possibly null value from a method returning an Optional value. Consider using Optional.empty() instead. [NullUsageWithOptional]\n" +
                        "       return null;\n" +
                        "       ~~~~~~~~~~~~\n" +
                        "0 errors, 1 warnings");

    }

    @Override
    protected Detector getDetector() {
        return new NullWithOptionalDetector();
    }

    @Override
    protected List<Issue> getIssues() {
        return Collections.singletonList(NullWithOptionalDetector.ISSUE);
    }
}
