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

import static lint.UastHelper.getLastAssignedExpression;
import static lint.UastHelper.methodHasName;

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
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UQualifiedReferenceExpression;

/**
 * Checks the use of Context.checkPermission or Context.enforcePermission where PID and UID are
 * retrieved via Binder.getCallingPid and Binder.getCallingUid. Example:
 * checkPermission("some.permission",Binder.getCallingPid(),Binder.getCallingUid())
 *
 * <p>For further details please read the paper "Security code smells in Android ICC", available at
 * http://scg.unibe.ch/archive/papers/Gadi18a.pdf
 *
 * <p>University of Bern, Software Composition Group
 */
public class PermissionCheckMisuseDetector extends Detector implements Detector.UastScanner {

    private static final String CONTEXT_CLASS = "android.content.Context";
    private static final String BINDER_CLASS = "android.os.Binder";

    @VisibleForTesting
    public static final String MESSAGE =
            "SM07: Broken Service Permission | Binder.getCallingPid and .getCallingUid may return the current apps value"
                    + "which could lead to unexpected behaviour of this permission check.";

    public static final Issue ISSUE =
            Issue.create(
                            "BrokenServicePermission", //$NON-NLS-1$
                            MESSAGE,
                            "Binder.getCallingPid or Binder.getCallingUid will return the current app's"
                                    + " pid and uid if no binder transaction (no IPC) is running. "
                                    + " For example, when your service is started via startService() (and not bindService())"
                                    + " the pid and uid returned by the binder will be the one of the callee"
                                    + " instead of the caller. Therefore they should not be used to check the permission"
                                    + " of the caller."
                                    + ""
                                    + " If possible, we recommend to add the permission for all components statically in the app's manifest."
                                    + " Another alternative is to use Context.checkCallingPermission or Context.enforceCallingPermission."
                                    + " These methods will return PERMISSION_DENIED, or throw a SecurityException respectively, if they are used"
                                    + " without any concurrent IPC.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            new Implementation(
                                    PermissionCheckMisuseDetector.class, Scope.JAVA_FILE_SCOPE))
                    .addMoreInfo("https://developer.android.com/guide/components/services")
                    .addMoreInfo(
                            "https://bitbucket.org/secure-it-i/android-app-vulnerability-benchmarks/src/978fbc17a087/System/EnforcePermission-PrivilegeEscalation-Lean/?at=master")
                    .addMoreInfo(
                            "https://bitbucket.org/secure-it-i/android-app-vulnerability-benchmarks/src/978fbc17a087d77b38474bfbe01a00b5e2217ce6/System/CheckPermission-PrivilegeEscalation-Lean/?at=master");
    private static final String GET_CALLING_PID = "getCallingPid";
    private static final String GET_CALLING_UID = "getCallingUid";

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        JavaEvaluator evaluator = context.getEvaluator();
        if (!evaluator.isMemberInSubClassOf(method, CONTEXT_CLASS, false)) return;
        String methodName = method.getName();
        List<UExpression> argumentList = call.getValueArguments();
        if ("checkPermission".equals(methodName)
                || "enforcePermission".equals(methodName)
                || ("checkUriPermission".equals(methodName) && argumentList.size() == 4)
                || ("enforceUriPermission".equals(methodName) && argumentList.size() == 5)) {
            if (isBinderGetCallingPid(argumentList.get(1), call, context)
                    && isBinderGetCallingUid(argumentList.get(2), call, context))
                context.report(ISSUE, call, context.getLocation(call), MESSAGE);
        }
        if (("enforceUriPermission".equals(methodName) && argumentList.size() == 7)
                || ("checkUriPermission".equals(methodName) && argumentList.size() == 6)) {
            if (isBinderGetCallingPid(argumentList.get(3), call, context)
                    && isBinderGetCallingUid(argumentList.get(4), call, context))
                context.report(ISSUE, call, context.getLocation(call), MESSAGE);
        }
    }

    private boolean isBinderMethod(
            @NonNull UExpression argument,
            @NonNull UCallExpression permissionCheckCall,
            @NonNull String methodName,
            @NonNull JavaContext context) {
        // check if argument is reference to local field
        // if yes, set argument to last assigned value of that field
        UExpression lastAssignedValue = getLastAssignedExpression(argument, permissionCheckCall);
        if (lastAssignedValue != null) argument = lastAssignedValue;

        // check if argument (or the last assignment of the locale field it references, see above)
        // is of form Binder.<methodName>()
        if (argument instanceof UQualifiedReferenceExpression) {
            UQualifiedReferenceExpression referenceExpression =
                    (UQualifiedReferenceExpression) argument;
            UExpression selectorExpression = referenceExpression.getSelector();
            if (selectorExpression instanceof UCallExpression) {
                UCallExpression binderCall = (UCallExpression) selectorExpression;
                JavaEvaluator evaluator = context.getEvaluator();
                PsiMethod resolvedBinderCall = binderCall.resolve();
                if (resolvedBinderCall != null
                        && evaluator.isMemberInSubClassOf(resolvedBinderCall, BINDER_CLASS, false)
                        && methodHasName(binderCall, methodName)) return true;
            }
        }
        return false;
    }

    private boolean isBinderGetCallingUid(
            @NonNull UExpression argument,
            @NonNull UCallExpression permissionCheckCall,
            @NonNull JavaContext context) {
        return isBinderMethod(argument, permissionCheckCall, GET_CALLING_UID, context);
    }

    private boolean isBinderGetCallingPid(
            @NonNull UExpression argument,
            @NonNull UCallExpression permissionCheckCall,
            @NonNull JavaContext context) {
        return isBinderMethod(argument, permissionCheckCall, GET_CALLING_PID, context);
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                "checkPermission",
                "enforcePermission",
                "checkUriPermission",
                "enforceUriPermission");
    }
}
