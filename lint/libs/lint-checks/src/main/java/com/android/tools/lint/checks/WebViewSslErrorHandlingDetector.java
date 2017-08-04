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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.ClassScanner;
import com.android.tools.lint.detector.api.Detector.JavaPsiScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiMethod;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class WebViewSslErrorHandlingDetector extends Detector implements JavaPsiScanner,
        ClassScanner {
    private static final Implementation IMPLEMENTATION =
            new Implementation(WebViewSslErrorHandlingDetector.class,
                    EnumSet.of(Scope.JAVA_LIBRARIES, Scope.JAVA_FILE),
                    Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create("WebViewSslErrorHandling",
            "Insecure WebView SSL Error Handling",
            "WebViewClient.onReceivedSslError is overridden with handler.proceed()." +
                    " It could result in insecure network traffic caused by trusting" +
                    " arbitrary TLS/SSL certificates presented by peers.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            IMPLEMENTATION);

    private static final String message = "The WebViewClient.onReceivedSslError methods skips" +
            " SSL Error handling because of insecure usage of handler.proceed()." +
            " It could result in insecure network traffic caused by trusting" +
            " arbitrary TLS/SSL certificates presented by peers.";

    public WebViewSslErrorHandlingDetector() {
    }

    // ---- Implements JavaScanner ----
    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.webkit.WebViewClient");
    }

    @Override
    public void checkClass(@NonNull JavaContext context, @NonNull PsiClass cls) {
        JavaEvaluator evaluator = context.getEvaluator();
        for (PsiMethod method : cls.findMethodsByName("onReceivedSslError", true)) {
            if (evaluator.isAbstract(method)) {
                continue;
            }
            PsiCodeBlock body = method.getBody();
            if (body != null
                    && body.getStatements().length != 0
                    && body.getStatements()[0].getText().equals("handler.proceed()")) {
                Location location = context.getNameLocation(method);
                context.report(ISSUE, method, location, message);
            }
        }
    }
}