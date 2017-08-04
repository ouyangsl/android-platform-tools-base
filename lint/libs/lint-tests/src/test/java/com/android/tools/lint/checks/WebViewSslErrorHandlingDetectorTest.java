package com.android.tools.lint.checks;

import com.android.tools.lint.checks.infrastructure.LintDetectorTest;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;

import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;

public class WebViewSslErrorHandlingDetectorTest extends LintDetectorTest {
    @Override
    protected Detector getDetector() {
        return new WebViewSslErrorHandlingDetector();
    }

    @Override
    protected List<Issue> getIssues() {
        return Collections.singletonList(WebViewSslErrorHandlingDetector.ISSUE);
    }

    public void testOkNoOverride() throws Exception {
        String result = lintProject(
                xml(FN_ANDROID_MANIFEST_XML,
                        "" +
                                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                "    package=\"test.pkg\"\n" +
                                "    android:versionCode=\"1\"\n" +
                                "    android:versionName=\"1.0\" >\n" +
                                "    <uses-sdk android:minSdkVersion=\"14\" />\n" +
                                "    <application" +
                                "        android:icon=\"@mipmap/ic_launcher\"" +
                                "        android:label=\"@string/app_name\">" +
                                "        <service\n" +
                                "            android:name=\".TestService\" >\n" +
                                "        </service>\n" +
                                "    </application>\n" +
                                "</manifest>"),
                java("src/test/pkg/TestService.java", ""+
                        "package test.pkg;\n" +
                        "\n" +
                        "import android.app.IntentService;\n" +
                        "import android.content.Intent;\n" +
                        "import android.net.http.SslError;\n" +
                        "import android.webkit.SslErrorHandler;\n" +
                        "import android.webkit.WebView;\n" +
                        "import android.webkit.WebViewClient;\n" +
                        "\n" +
                        "public class TestService extends IntentService {\n" +
                        "    public TestService() {\n" +
                        "        super(\"Test\");\n" +
                        "        WebView webView = new WebView(getApplication());\n" +
                        "        webView.setWebViewClient(new WebViewClient() {\n" +
                        "        });\n" +
                        "        webView.loadUrl(\"http://www.example.com/\");\n" +
                        "    }\n" +
                        "    @Override\n" +
                        "    protected void onHandleIntent(Intent intent) {\n" +
                        "    }\n" +
                        "}\n"));
        assertEquals("No warnings.", result);
    }

    public void testOkNoProceed() throws Exception {
        String result = lintProject(
                xml(FN_ANDROID_MANIFEST_XML,
                        "" +
                                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                "    package=\"test.pkg\"\n" +
                                "    android:versionCode=\"1\"\n" +
                                "    android:versionName=\"1.0\" >\n" +
                                "    <uses-sdk android:minSdkVersion=\"14\" />\n" +
                                "    <application" +
                                "        android:icon=\"@mipmap/ic_launcher\"" +
                                "        android:label=\"@string/app_name\">" +
                                "        <service\n" +
                                "            android:name=\".TestService\" >\n" +
                                "        </service>\n" +
                                "    </application>\n" +
                                "</manifest>"),
                java("src/test/pkg/TestService.java", ""+
                        "package test.pkg;\n" +
                        "\n" +
                        "import android.app.IntentService;\n" +
                        "import android.content.Intent;\n" +
                        "import android.net.http.SslError;\n" +
                        "import android.webkit.SslErrorHandler;\n" +
                        "import android.webkit.WebView;\n" +
                        "import android.webkit.WebViewClient;\n" +
                        "\n" +
                        "public class TestService extends IntentService {\n" +
                        "    public TestService() {\n" +
                        "        super(\"Test\");\n" +
                        "        WebView webView = new WebView(getApplication());\n" +
                        "        webView.setWebViewClient(new WebViewClient() {\n" +
                        "            @Override\n" +
                        "            public void onReceivedSslError(WebView view, final SslErrorHandler handler, SslError error) {\n" +
                        "                ;\n" +
                        "            }\n" +
                        "        });\n" +
                        "        webView.loadUrl(\"http://www.example.com/\");\n" +
                        "    }\n" +
                        "    @Override\n" +
                        "    protected void onHandleIntent(Intent intent) {\n" +
                        "    }\n" +
                        "}\n"));
        assertEquals("No warnings.", result);
    }

    public void testNg() throws Exception {
        String result = lintProject(
                xml(FN_ANDROID_MANIFEST_XML,
                        "" +
                                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                "    package=\"test.pkg\"\n" +
                                "    android:versionCode=\"1\"\n" +
                                "    android:versionName=\"1.0\" >\n" +
                                "    <uses-sdk android:minSdkVersion=\"14\" />\n" +
                                "    <application" +
                                "        android:icon=\"@mipmap/ic_launcher\"" +
                                "        android:label=\"@string/app_name\">" +
                                "        <service\n" +
                                "            android:name=\".TestService\" >\n" +
                                "        </service>\n" +
                                "    </application>\n" +
                                "</manifest>"),
                java("src/test/pkg/TestService.java", ""+
                        "package test.pkg;\n" +
                        "\n" +
                        "import android.app.IntentService;\n" +
                        "import android.content.Intent;\n" +
                        "import android.net.http.SslError;\n" +
                        "import android.webkit.SslErrorHandler;\n" +
                        "import android.webkit.WebView;\n" +
                        "import android.webkit.WebViewClient;\n" +
                        "\n" +
                        "public class TestService extends IntentService {\n" +
                        "    public TestService() {\n" +
                        "        super(\"Test\");\n" +
                        "        WebView webView = new WebView(getApplication());\n" +
                        "        webView.setWebViewClient(new WebViewClient() {\n" +
                        "            @Override\n" +
                        "            public void onReceivedSslError(WebView view, final SslErrorHandler handler, SslError error) {\n" +
                        "                handler.proceed();\n" +
                        "            }\n" +
                        "        });\n" +
                        "        webView.loadUrl(\"http://www.example.com/\");\n" +
                        "    }\n" +
                        "    @Override\n" +
                        "    protected void onHandleIntent(Intent intent) {\n" +
                        "    }\n" +
                        "}\n"));
        assertEquals("src/test/pkg/TestService.java:16: Warning: The WebViewClient.onReceivedSslError methods skips SSL Error handling because of insecure usage of handler.proceed(). It could result in insecure network traffic caused by trusting arbitrary TLS/SSL certificates presented by peers. [WebViewSslErrorHandling]\n" +
                "            public void onReceivedSslError(WebView view, final SslErrorHandler handler, SslError error) {\n" +
                "                        ~~~~~~~~~~~~~~~~~~\n" +
                "0 errors, 1 warnings\n", result);
    }

}
