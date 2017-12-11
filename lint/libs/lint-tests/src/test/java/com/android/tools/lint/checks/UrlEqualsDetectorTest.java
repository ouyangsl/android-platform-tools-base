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

public class UrlEqualsDetectorTest extends LintDetectorTest {
    public void testUseOfEquals() {
        lint().files(java("" +
                "package test.pkg;\n" +
                "import java.net.URL;\n" +
                "import java.net.MalformedURLException;\n" +
                "public class TestClass1 {\n" +
                "   public static void main(String[] args) {\n" +
                "       try {\n" +
                "           URL url1 = new URL(\"https://www.google.com\");\n" +
                "           URL url2 = new URL(\"https://www.youtube.com\");\n" +
                "           if (url1.equals(url2)) {\n" +
                "               System.out.println(\"test\");\n" +
                "           }\n" +
                "       } catch (MalformedURLException ignored) {}\n" +
                "   }\n" +
                "}\n"))
                .run()
                .expect("src/test/pkg/TestClass1.java:9: Warning: You should not compare URLs with .equals(). This can lead to unpredictable and undesired behavior. [URLCompareEquals]\n" +
                        "           if (url1.equals(url2)) {\n" +
                        "               ~~~~~~~~~~~~~~~~~\n" +
                        "0 errors, 1 warnings");
    }

    @Override
    protected Detector getDetector() {
        return new UrlEqualsDetector();
    }

    @Override
    protected List<Issue> getIssues() {
        return Collections.singletonList(UrlEqualsDetector.ISSUE);
    }
}
