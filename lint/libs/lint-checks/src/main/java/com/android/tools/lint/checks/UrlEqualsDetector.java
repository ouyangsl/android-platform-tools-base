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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import java.util.Collections;
import java.util.List;

/**
 * Checks if URLs are being compared with .equals().
 */
public class UrlEqualsDetector extends Detector implements Detector.UastScanner {
    @NonNull
    public static final Issue ISSUE =
            Issue.create("UrlCompareEquals",
                    "Compared a URL using .equals()",
                    "Comparing URLs using .equals() can give unexpected results. " +
                            "Two hosts are considered equivalent if both can be resolved to the same IP address.",
                    Category.CORRECTNESS,
                    4,
                    Severity.WARNING,
                    new Implementation(UrlEqualsDetector.class,
                            Scope.JAVA_FILE_SCOPE));

    /**
     * @return A list of UElements that this detector will traverse.
     */
    @NonNull
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UCallExpression.class);
    }

    /**
     * @param context The context used to report errors.
     * @return Creates a new UElementHandler that will search for our specific issues.
     */
    @NonNull
    @Override
    public UElementHandler createUastHandler(@NonNull final JavaContext context) {
        return new URLEqualsHandler(context);
    }

    /**
     * The class that does the actual detecting.
     */
    private class URLEqualsHandler extends UElementHandler {
        @NonNull private final JavaContext context;

        public URLEqualsHandler(JavaContext context) {
            this.context = context;
        }

        @Override
        public void visitCallExpression(UCallExpression node) {
            @Nullable final PsiMethod method = node.resolve();

            final JavaEvaluator evaluator = context.getEvaluator();

            if (evaluator.typeMatches(node.getReceiverType(), "java.net.URL")) {
                if (method != null && "equals".equals(method.getName())) {
                    context.report(ISSUE,
                            node,
                            context.getLocation(node),
                            "You should not compare URLs with .equals(). This can lead to unpredictable and undesired behavior.");
                }
            }
        }
    }
}
