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

package com.android.tools.lint.checks;

import com.android.tools.lint.checks.infrastructure.LintDetectorTest;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;

import java.util.Collections;
import java.util.List;

public class UnrestrictedInterceptRequestDetectorTest extends LintDetectorTest {
    public void testAlwaysReturnNull() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "import android.webkit.WebResourceResponse;"+
                        "import android.webkit.WebResourceRequest;\n"+
                        "import android.webkit.WebView;\n"+
                        "import android.webkit.WebViewClient;\n"+
                        "public class TestClass1 extends WebViewClient{\n" +
                        "   public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {\n" +
                        "       return null;\n" +
                        "   }\n" +
                        "}"))
                .run()
                .expectCount(1, Severity.WARNING).expectMatches(WebViewClientMethodMisuseDetector.ShouldInterceptRequestVisitor.MESSAGE);
    }

    public void testAlwaysReturnNullAndLog() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "import android.webkit.WebResourceResponse;"+
                        "import android.webkit.WebResourceRequest;\n"+
                        "import android.webkit.WebView;\n"+
                        "import android.webkit.WebViewClient;\n"+
                        "public class TestClass1 extends WebViewClient{\n" +
                        "   public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {\n" +
                        "       System.out.println(\"I don't care\");\n" +
                        "       return null;\n" +
                        "   }\n" +
                        "}"))
                .run()
                .expectCount(1, Severity.WARNING).expectMatches(WebViewClientMethodMisuseDetector.ShouldInterceptRequestVisitor.MESSAGE);
    }

    public void testCallSuperMethod() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "import android.webkit.WebResourceResponse;"+
                        "import android.webkit.WebResourceRequest;\n"+
                        "import android.webkit.WebView;\n"+
                        "import android.webkit.WebViewClient;\n"+
                        "public class TestClass1 extends WebViewClient{\n" +
                        "   public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {\n" +
                        "       return super.shouldInterceptRequest(view, request);\n" +
                        "   }\n" +
                        "}"))
                .run()
                .expectCount(0);
    }

    public void testDecisionInMethod() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "import android.webkit.WebResourceResponse;"+
                        "import android.webkit.WebResourceRequest;\n"+
                        "import android.webkit.WebView;\n"+
                        "import android.webkit.WebViewClient;\n"+
                        "public class TestClass1 extends WebViewClient{\n" +
                        "   public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {\n" +
                        "       if(\"http://real.server.test/\".equals(request.getUrl().toString()))\n"+
                        "          return null;\n"+
                        "       else\n"+
                        "          return super.shouldInterceptRequest(view, request);\n"+
                        "   }\n" +
                        "}"))
                .run()
                .expectCount(0);
    }

    public void testDecisionOutsideMethod() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "import android.webkit.WebResourceResponse;"+
                        "import android.webkit.WebResourceRequest;\n"+
                        "import android.webkit.WebView;\n"+
                        "import android.webkit.WebViewClient;\n"+
                        "public class TestClass1 extends WebViewClient {\n" +
                        "   public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {\n" +
                        "       return decide(view, request);\n"+
                        "   }\n" +
                        "   public WebResourceResponse decide(WebView view, WebResourceRequest request) {\n" +
                        "       if(\"http://real.server.test/\".equals(request.getUrl().toString()))\n"+
                        "          return null;\n"+
                        "       else\n"+
                        "          return super.shouldInterceptRequest(view, request);\n"+
                        "   }\n" +
                        "}"))
                .run()
                .expectCount(0);
    }

    @Override
    protected Detector getDetector() {
        return new WebViewClientMethodMisuseDetector();
    }

    @Override
    protected List<Issue> getIssues() {
        return Collections.singletonList(WebViewClientMethodMisuseDetector.UNRESTRICTED_INTERCEPT_REQUEST);
    }
}
