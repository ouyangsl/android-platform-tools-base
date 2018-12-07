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

public class UnrestrictedOverrideUrlLoadingDetectorTest extends LintDetectorTest {
    public void testAlwaysReturnFalse() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "import android.webkit.WebResourceRequest;\n"+
                        "import android.webkit.WebView;\n"+
                        "import android.webkit.WebViewClient;\n"+
                        "public class TestClass1 extends WebViewClient{\n" +
                            "public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {\n" +
                                "return false;\n" +
                            "}\n" +
                        "}"))
                .run()
                .expectCount(1, Severity.WARNING).expectMatches(WebViewClientMethodMisuseDetector.ShouldOverrideUrlLoadingVisitor.MESSAGE);
    }

    public void testAlwaysReturnFalseAndLog() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "import android.webkit.WebResourceRequest;\n"+
                        "import android.webkit.WebView;\n"+
                        "import android.webkit.WebViewClient;\n"+
                        "public class TestClass1 extends WebViewClient{\n" +
                            "public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {\n" +
                                "System.out.println(\"I don't care\");\n" +
                                "return false;\n" +
                            "}\n" +
                        "}"))
                .run()
                .expectCount(1, Severity.WARNING).expectMatches(WebViewClientMethodMisuseDetector.ShouldOverrideUrlLoadingVisitor.MESSAGE);
    }

    // as seen on http://stacktips.com/tutorials/android/android-webview-example#2-open-link-on-android-devicebrowser
    public void testAlwaysLoadAndReturnTrue() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "import android.webkit.WebResourceRequest;\n"+
                        "import android.webkit.WebView;\n"+
                        "import android.webkit.WebViewClient;\n"+
                        "public class TestClass1 extends WebViewClient{\n" +
                        "   public boolean shouldOverrideUrlLoading(WebView view, String url) {\n" +
                        "       view.loadUrl(url);\n" +
                        "       return true;\n" +
                        "   }\n" +
                        "}"))
                .run()
                .expectCount(1, Severity.WARNING).expectMatches(WebViewClientMethodMisuseDetector.ShouldOverrideUrlLoadingVisitor.MESSAGE);
    }


    public void testLoadUrlConditionallyAndReturnTrue() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "import android.webkit.WebResourceRequest;\n"+
                        "import android.webkit.WebView;\n"+
                        "import android.webkit.WebViewClient;\n"+
                        "import android.net.Uri; \n"+
                        "public class TestClass1 extends WebViewClient{\n" +
                        "   public boolean shouldOverrideUrlLoading(WebView view, String url) {\n" +
                        "       if(Uri.parse(url).getHost().equals(\"www.test.com\"))\n"+
                        "           view.loadUrl(url);\n" +
                        "       return true;\n" +
                        "   }\n" +
                        "}"))
                .run()
                .expectCount(0);
    }

    public void testWithDecisionInSameMethod() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "import android.webkit.WebResourceRequest;\n"+
                        "import android.webkit.WebView;\n"+
                        "import android.webkit.WebViewClient;\n"+
                        "public class TestClass1 extends WebViewClient{\n" +
                        "   public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {\n" +
                        "       if(\"http://real.server.test/\".equals(request.getUrl().toString()))\n"+
                        "          return false;\n"+
                        "       else\n"+
                        "          return true;\n"+
                        "   }\n" +
                        "}"))
                .run()
                .expectCount(0);
    }

    public void testWithDecisionInOtherMethod() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "import android.webkit.WebResourceRequest;\n"+
                        "import android.webkit.WebView;\n"+
                        "import android.webkit.WebViewClient;\n"+
                        "public class TestClass1 extends WebViewClient{\n" +
                        "   public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {\n" +
                        "       return decide(view, request);\n"+
                        "   }\n" +
                        "   public boolean decide(WebView view, WebResourceRequest request) {\n" +
                        "       if(\"http://real.server.test/\".equals(request.getUrl().toString()))\n"+
                        "          return false;\n"+
                        "       else\n"+
                        "          return true;\n"+
                        "   }\n" +
                        "}"))
                .run()
                .expectCount(0);
    }


    public void testAlwaysReturnTrue() {
        lint().files(
                java("" +
                        "package test.pkg;\n" +
                        "import android.webkit.WebResourceRequest;\n"+
                        "import android.webkit.WebView;\n"+
                        "import android.webkit.WebViewClient;\n"+
                        "public class TestClass1 extends WebViewClient{\n" +
                            "public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {\n" +
                                "return true;\n" +
                            "}\n" +
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
        return Collections.singletonList(WebViewClientMethodMisuseDetector.UNRESTRICTED_OVERRIDE_URL_LOADING);
    }
}
