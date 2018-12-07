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
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.UastLintUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.UastUtils;

/**
 * Helper class for Uast manipulations.
 *
 * For further details please read the paper "Security code smells in Android ICC",
 * available at http://scg.unibe.ch/archive/papers/Gadi18a.pdf
 *
 * University of Bern, Software Composition Group
 *
 */
class UastHelper {

    static boolean methodHasName(@NonNull UCallExpression methodCall, @Nullable String expectedMethodName) {
        String methodName = methodCall.getMethodName();
        return methodName != null && methodName.equals(expectedMethodName);
    }

    static boolean hasClassOrSuperClass(PsiType type, String qualifiedClassName)
    {
        if(type == null)
            return false;
        if(type.getCanonicalText().equals(qualifiedClassName))
            return true;
        PsiType[] superTypes = type.getSuperTypes();
        for(PsiType superType : superTypes){
            if(superType.getCanonicalText().equals(qualifiedClassName))
                return true;
        }
        return false;
    }

    static UExpression getLastAssignedExpression(@Nullable UExpression variable, @Nullable UCallExpression call) {
        if (variable instanceof USimpleNameReferenceExpression) {
            PsiElement e = UastUtils.tryResolve(variable);
            if (e instanceof PsiVariable) {
                UExpression assignedValue = UastLintUtils.findLastAssignment((PsiVariable) e, call);
                if (assignedValue != null)
                    return assignedValue;
            }
        }
        return null;
    }
}
