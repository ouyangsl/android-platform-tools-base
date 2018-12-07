/*
 * Copyright (C) 2011 The Android Open Source Project
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
import com.android.annotations.VisibleForTesting;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;

/**
 * Checks for checkSelfOrCallingPermission and enforceSelfOrCallingPermission. These methods should
 * be avoided as they could erroneously provide caller apps the permission of the callee.
 *
 * <p>For further details please read the paper "Security code smells in Android ICC", available at
 * http://scg.unibe.ch/archive/papers/Gadi18a.pdf
 *
 * <p>University of Bern, Software Composition Group
 */
public class CallingOrSelfPermissionCheckDetector extends Detector implements Detector.UastScanner {

    private static final String CONTEXT_CLASS = "android.content.Context";
    private static final String PERMISSION_CHECKER_CLASS =
            "android.support.v4.content.PermissionChecker";
    @VisibleForTesting public static final String MESSAGE = " could grant access to malicious apps";

    public static final Issue ISSUE =
            Issue.create(
                    "BrokenServicePermission",
                    "SM07: Broken Service Permission | SelfPermission checks could fail",
                    "As the name suggests, checkSelfOrCallingPermission and enforceSelfOrCallingPermission"
                            + " grant the access if either the caller or the callee app has appropriate permissions."
                            + " These methods should not be used with great care as they could erroneously return grants"
                            + " to underprivileged apps.",
                    Category.SECURITY,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            CallingOrSelfPermissionCheckDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        JavaEvaluator evaluator = context.getEvaluator();
        if (!evaluator.isMemberInSubClassOf(method, CONTEXT_CLASS, false)
                && !evaluator.isMemberInSubClassOf(method, PERMISSION_CHECKER_CLASS, false)) return;

        context.report(ISSUE, call, context.getLocation(call), call.getMethodName() + MESSAGE);
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                "checkCallingOrSelfPermission",
                "enforceCallingOrSelfPermission",
                "checkCallingOrSelfUriPermission",
                "enforceCallingOrSelfUriPermission");
    }
}
