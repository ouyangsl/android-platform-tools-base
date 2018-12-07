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

public class UnrestrictedWebViewDetectorTest extends LintDetectorTest {

    public void testSetDefaultWebViewClient() {
        lint().files(
            java("package com.example.test;\n" +
                    "\n" +
                    "import android.webkit.WebView;\n"+
                    "import android.webkit.WebViewClient;\n"+
                    "import android.content.Context;\n" +
                    "import android.app.Activity;\n" +
                    "import android.os.Bundle;\n" +
                    "\n" +
                    "public class MainActivity extends Activity {\n" +
                    "\n" +
                    "    @Override\n" +
                    "    protected void onCreate(Bundle savedInstanceState) {\n" +
                    "        super.onCreate(savedInstanceState);\n" +
                    "\n" +
                    "        WebView myWebView = new WebView(getApplicationContext());\n"+
                    "        myWebView.setWebViewClient(new WebViewClient());\n"+
                    "    }\n" +
                    "}\n"))
            .run()
            .expectCount(1, Severity.WARNING).expectMatches(UnrestrictedWebViewDetector.MESSAGE);
    }

    public void testSetInlineRestrictedWebViewClient() {
        lint().files(
                java("package com.example.test;\n" +
                        "\n" +
                        "import android.webkit.WebView;\n"+
                        "import android.webkit.WebViewClient;\n"+
                        "import android.content.Context;\n" +
                        "import android.app.Activity;\n" +
                        "import android.os.Bundle;\n" +
                        "import android.webkit.WebResourceRequest; \n;"+
                        "\n" +
                        "public class MainActivity extends Activity {\n" +
                        "\n" +
                        "    @Override\n" +
                        "    protected void onCreate(Bundle savedInstanceState) {\n" +
                        "        super.onCreate(savedInstanceState);\n" +
                        "\n" +
                        "        WebView myWebView = new WebView(getApplicationContext());\n"+
                        "        myWebView.setWebViewClient(new WebViewClient(){\n"+
                        "            @Override\n"+
                        "            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {\n"+
                        "                return true;\n"+
                        "            }\n"+
                        "        });\n"+
                        "    }\n"+
                        "}\n"))
                .run()
                .expectCount(0);
    }

    public void testSetInlineUnrestrictedWebViewClient() {
        lint().files(
                java("package com.example.test;\n" +
                        "\n" +
                        "import android.webkit.WebView;\n"+
                        "import android.webkit.WebViewClient;\n"+
                        "import android.content.Context;\n" +
                        "import android.app.Activity;\n" +
                        "import android.os.Bundle;\n" +
                        "import android.webkit.WebResourceRequest;\n"+
                        "import android.net.http.SslError;\n"+
                        "import android.webkit.SslErrorHandler;\n"+
                        "\n" +
                        "public class MainActivity extends Activity {\n" +
                        "\n" +
                        "    @Override\n" +
                        "    protected void onCreate(Bundle savedInstanceState) {\n" +
                        "        super.onCreate(savedInstanceState);\n" +
                        "\n" +
                        "        WebView myWebView = new WebView(getApplicationContext());\n"+
                        "        myWebView.setWebViewClient(new WebViewClient(){\n"+
                        "            @Override\n"+
                        "            public void onReceivedSslError(WebView view , final SslErrorHandler handler, SslError error){\n"+
                        "                handler.proceed();\n"+
                        "            }\n"+
                        "        });\n"+
                        "    }\n"+
                        "}\n"))
                .run()
                .expectCount(0);
    }

    public void testSetCustomRestrictedWebViewClient() {
        lint().files(
                java("package com.example.test;\n"+
                        "\n"+
                        "import android.net.http.SslError;\n"+
                        "import android.webkit.SslErrorHandler;\n"+
                        "import android.webkit.WebResourceRequest;\n"+
                        "import android.webkit.WebView;\n"+
                        "import android.webkit.WebViewClient;\n"+
                        "\n"+
                        "public class CustomWebViewClient extends WebViewClient {\n"+
                        "\n"+
                        "    @Override\n"+
                        "    public void onReceivedSslError(WebView view , final SslErrorHandler handler, SslError error){\n"+
                        "        handler.cancel();\n"+
                        "    }\n"+
                        "\n"+
                        "   @Override\n"+
                        "    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {\n"+
                        "        return true;\n"+
                        "   }\n"+
                        "}\n"+
                        "\n"),
                java("package com.example.test;\n" +
                        "\n" +
                        "import android.webkit.WebView;\n"+
                        "import android.webkit.WebViewClient;\n"+
                        "import android.content.Context;\n" +
                        "import android.app.Activity;\n" +
                        "import android.os.Bundle;\n" +
                        "import android.webkit.WebResourceRequest;\n"+
                        "import android.net.http.SslError;\n"+
                        "import android.webkit.SslErrorHandler;\n"+
                        "\n" +
                        "public class MainActivity extends Activity {\n" +
                        "\n" +
                        "    @Override\n" +
                        "    protected void onCreate(Bundle savedInstanceState) {\n" +
                        "        super.onCreate(savedInstanceState);\n" +
                        "\n" +
                        "        WebView myWebView = new WebView(getApplicationContext());\n"+
                        "        myWebView.setWebViewClient(new WebViewClient(){\n"+
                        "            @Override\n"+
                        "            public void onReceivedSslError(WebView view , final SslErrorHandler handler, SslError error){\n"+
                        "                handler.proceed();\n"+
                        "            }\n"+
                        "        });\n"+
                        "    }\n"+
                        "}\n"))
                .run()
                .expectCount(0);
    }

    public void testSetCustomUnrestrictedWebViewClient() {
        lint().files(
                java("package com.example.test;\n"+
                        "\n"+
                        "import android.net.http.SslError;\n"+
                        "import android.webkit.SslErrorHandler;\n"+
                        "import android.webkit.WebResourceRequest;\n"+
                        "import android.webkit.WebView;\n"+
                        "import android.webkit.WebViewClient;\n"+
                        "\n"+
                        "public class CustomWebViewClient extends WebViewClient {\n"+
                        "\n"+
                        "    @Override\n"+
                        "    public void onReceivedSslError(WebView view , final SslErrorHandler handler, SslError error){\n"+
                        "        handler.cancel();\n"+
                        "    }\n"+
                        "}\n"+
                        "\n"),
                java("package com.example.test;\n" +
                        "\n" +
                        "import android.webkit.WebView;\n"+
                        "import android.webkit.WebViewClient;\n"+
                        "import android.content.Context;\n" +
                        "import android.app.Activity;\n" +
                        "import android.os.Bundle;\n" +
                        "import android.webkit.WebResourceRequest;\n"+
                        "import android.net.http.SslError;\n"+
                        "import android.webkit.SslErrorHandler;\n"+
                        "\n" +
                        "public class MainActivity extends Activity {\n" +
                        "\n" +
                        "    @Override\n" +
                        "    protected void onCreate(Bundle savedInstanceState) {\n" +
                        "        super.onCreate(savedInstanceState);\n" +
                        "\n" +
                        "        WebView myWebView = new WebView(getApplicationContext());\n"+
                        "        myWebView.setWebViewClient(new WebViewClient(){\n"+
                        "            @Override\n"+
                        "            public void onReceivedSslError(WebView view , final SslErrorHandler handler, SslError error){\n"+
                        "                handler.proceed();\n"+
                        "            }\n"+
                        "        });\n"+
                        "    }\n"+
                        "}\n"))
                .run()
                .expectCount(1, Severity.WARNING).expectMatches(UnrestrictedWebViewDetector.MESSAGE);
    }

    @Override
    protected Detector getDetector() {
        return new UnrestrictedWebViewDetector();
    }

    @Override
    protected List<Issue> getIssues() {
        return Collections.singletonList(UnrestrictedWebViewDetector.ISSUE);
    }
}
