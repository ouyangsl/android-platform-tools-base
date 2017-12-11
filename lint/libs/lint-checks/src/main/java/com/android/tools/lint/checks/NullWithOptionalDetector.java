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
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TypeEvaluator;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UBinaryExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UVariable;
import org.jetbrains.uast.UastBinaryOperator;
import org.jetbrains.uast.java.JavaUMethod;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Checks for usage of null references for values that are of the type java.util.Optional.
 */
public class NullWithOptionalDetector extends Detector implements Detector.UastScanner {
    @NonNull
    public static final Issue ISSUE =
            Issue.create("NullUsageWithOptional",
                    "Use of null with Optional values.",
                    "You should not use a null value with the Optional type, consider using " +
                            "Optional.empty() instead.",
                    Category.CORRECTNESS,
                    5, // 10 is the highest
                    Severity.WARNING,
                    new Implementation(NullWithOptionalDetector.class,
                                       Scope.JAVA_FILE_SCOPE));

    private NullInferer nullInferer = new NullInferer();

    /**
     * @return A list of UElements that this detector will traverse.
     */
    @NonNull
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UVariable.class,
                             UReturnExpression.class,
                             UBinaryExpression.class);
    }

    /**
     * @param context The context used to report errors.
     * @return Creates a new UElementHandler that will search for our specific issues.
     */
    @NonNull
    @Override
    public UElementHandler createUastHandler(@NonNull final JavaContext context) {
        nullInferer.infer(context);

        return new NullWithOptionalHandler(context);
    }

    /**
     * The class that does the actual detecting.
     */
    private class NullWithOptionalHandler extends UElementHandler {
        @NonNull private final JavaContext context;

        public NullWithOptionalHandler(@NonNull final JavaContext context) {
            this.context = context;
        }

        /**
         * Checks if we're returning null from a method that returns an Optional value.
         */
        @Override
        public void visitReturnExpression(UReturnExpression node) {
            // Get the containing method to check it's return type.
            @NonNull final Optional<JavaUMethod> containingMethod =
                    (Optional<JavaUMethod>) ChecksUtils.getContainingElementOfType(node, JavaUMethod.class);

            containingMethod.ifPresent(method -> {
                if (method.getReturnType() != null && method.getReturnType().getCanonicalText().startsWith("java.util.Optional")) {
                    if (nullInferer.isValueNullable(node.getReturnExpression())) {
                        context.report(ISSUE,
                                node,
                                context.getLocation(node),
                                "You should not return a possibly null value from a method " +
                                        "returning an Optional value. Consider using Optional.empty() " +
                                        "instead.");
                    }
                }});
        }

        /**
         * Visit a binary expression to check if we're assigning a null value to an Optional variable.
         */
        @Override
        public void visitBinaryExpression(UBinaryExpression node) {
            if (node.getOperator() == UastBinaryOperator.ASSIGN) {
                if (nullInferer.isValueNullable(node.getRightOperand())) {
                    final PsiType type = TypeEvaluator.evaluate(node.getLeftOperand());

                    if (type != null && type.getCanonicalText().startsWith("java.util.Optional")) {
                        context.report(ISSUE,
                                node,
                                context.getLocation(node),
                                "You should not assign a possibly null to an Optional variable." +
                                        "Consider using Optional.empty() instead.");
                    }
                }
            }
        }

        /**
         * Checks a variable to see if it's being initialized to null.
         */
        @Override
        public void visitVariable(UVariable node) {
            // Check the type.
            if (node.getTypeReference() != null && node.getTypeReference().getQualifiedName() != null) {
                // If it's the Optional type and we're returning null, report a warning.
                if (node.getTypeReference().getQualifiedName().equals("java.util.Optional")) {
                    if (nullInferer.isValueNullable(node.getUastInitializer())) {
                        context.report(ISSUE,
                                (UElement) node,
                                context.getLocation((UElement) node),
                                "You should not initialize a variable of Optional type to a " +
                                        "possibly null value. Consider using Optional.empty() instead.");
                    }
                }
            }
        }
    }
}
