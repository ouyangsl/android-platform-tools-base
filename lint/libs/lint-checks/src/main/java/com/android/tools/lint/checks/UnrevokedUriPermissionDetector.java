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
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.uast.UCallExpression;

/**
 * Detector for unrevoked URI permissions that searches for granted URI permissions that miss the
 * corresponding revokings.
 *
 * <p>Call for grant: Context.grantUriPermission(...) Call for revoking:
 * Context.revokeUriPermission(...)
 *
 * <p>The arguments in both calls are currently not inspected.
 *
 * <p>For further details please read the paper "Security code smells in Android ICC", available at
 * http://scg.unibe.ch/archive/papers/Gadi18a.pdf
 *
 * <p>University of Bern, Software Composition Group
 */
public class UnrevokedUriPermissionDetector extends Detector implements Detector.UastScanner {

    private static final String CONTEXT_CLASS = "android.content.Context";

    @VisibleForTesting
    public static final String MESSAGE =
            "SM01: Persisted Dynamic Permission | URI permissions granted through the context class have to be revoked explicitly";

    private List<Location> grantsUriPermissionCallLocations = new ArrayList<>();
    private boolean revokesUriPermission = false;
    private static final String GRANT_URI_PERMISSION = "grantUriPermission";
    private static final String REVOKE_URI_PERMISSION = "revokeUriPermission";

    public static final Issue ISSUE =
            Issue.create(
                            "PersistedDynamicPermission",
                            MESSAGE,
                            "The Context.grantUriPermission grants access for a specific URI to another"
                                    + " app. This grant does not expire, therefore the app granting the permission needs to explicitly revoke the grant"
                                    + " by calling revokeUriPermission(Uri, int).\n"
                                    + "It is recommended to add the Intent.FLAG_GRANT_READ_URI_PERMISSION"
                                    + " or Intent.FLAG_GRANT_WRITE_URI_PERMISSION flag to intents containing a sensitive URI. Following this strategy,"
                                    + " the permission is automatically revoked as soon as the intent receiver finishes.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            new Implementation(
                                    UnrevokedUriPermissionDetector.class, Scope.JAVA_FILE_SCOPE))
                    .addMoreInfo(
                            "https://developer.android.com/reference/android/content/Context.html#grantUriPermission(java.lang.String, android.net.Uri, int)");

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        JavaEvaluator evaluator = context.getEvaluator();
        if (!evaluator.isMemberInSubClassOf(method, CONTEXT_CLASS, false)) return;
        if (call.getMethodName() != null && call.getMethodName().equals(GRANT_URI_PERMISSION)) {
            grantsUriPermissionCallLocations.add(context.getLocation(call));
            return;
        }
        if (call.getMethodName() != null && call.getMethodName().equals(REVOKE_URI_PERMISSION))
            revokesUriPermission = true;
    }

    @Override
    public void afterCheckProject(Context context) {
        if (revokesUriPermission || grantsUriPermissionCallLocations.isEmpty()) return;
        for (Location grantUriPermissionCallLocations : grantsUriPermissionCallLocations) {
            context.report(ISSUE, grantUriPermissionCallLocations, MESSAGE);
        }
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(REVOKE_URI_PERMISSION, GRANT_URI_PERMISSION);
    }
}
