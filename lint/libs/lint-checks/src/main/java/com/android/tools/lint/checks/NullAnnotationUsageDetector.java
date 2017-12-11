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
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiVariable;
import org.jetbrains.uast.UBinaryExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UParameter;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UVariable;
import org.jetbrains.uast.UastBinaryOperator;
import org.jetbrains.uast.java.JavaUMethod;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.android.SdkConstants.SUPPORT_ANNOTATIONS_PREFIX;

/**
 * Checks for improper usage of @NonNull/@Nullable annotations. This includes things like:
 * - Passing a null value to a parameter that is annotated @NonNull.
 * - Returning a null value from a method annotated @NonNull.
 */
public class NullAnnotationUsageDetector extends Detector implements Detector.UastScanner {
    @NonNull
    public static final Issue ISSUE =
            Issue.create("ImproperNullAnnotationUsage",
                    "@NonNull variable might have a null value.",
                    "A variable is annotated @NonNull, but the code could give it a null value.",
                    Category.CORRECTNESS,
                    8,
                    Severity.WARNING,
                    new Implementation(NullAnnotationUsageDetector.class,
                                       Scope.JAVA_FILE_SCOPE));

    @NonNull private NullInferer nullInferer;

    /**
     * @return A list of UElements that this detector will traverse.
     */
    @NonNull
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UVariable.class,
                             UReturnExpression.class,
                             UBinaryExpression.class,
                             UCallExpression.class);
    }

    /**
     * @param context The context used to report errors.
     * @return Creates a new UElementHandler that will search for our specific issues.
     */
    @NonNull
    @Override
    public UElementHandler createUastHandler(@NonNull final JavaContext context) {
        nullInferer = new NullInferer();
        nullInferer.infer(context);

        return new NullnessHandler(context);
    }

    /**
     * The class that does the actual detecting.
     */
    private class NullnessHandler extends UElementHandler {
        private static final String FQ_NONNULL_ANNOTATION = SUPPORT_ANNOTATIONS_PREFIX + "NonNull";
        @NonNull private final JavaContext context;

        public NullnessHandler(@NonNull final JavaContext context) {
            this.context = context;
        }

        /**
         * Check if returning null in a method annotated @NonNull
         */
        @Override
        public void visitReturnExpression(UReturnExpression node) {
            // Get the containing method to check if it's annotated @NonNull
            @NonNull final Optional<JavaUMethod> containingMethod =
                    (Optional<JavaUMethod>) ChecksUtils.getContainingElementOfType(node, JavaUMethod.class);

            containingMethod.ifPresent(method -> {
                // Check if annotated @NonNull
                if (method.findAnnotation(FQ_NONNULL_ANNOTATION) != null) {
                    if (node.getReturnExpression() != null) {
                        // Check if returning a value that could be/is null
                        if (nullInferer.isValueNullable(node.getReturnExpression())) {
                            context.report(ISSUE,
                                    node,
                                    context.getLocation(node),
                                    "You should not return a possibly null value from a method annotated with @NonNull.");
                        }
                    }
                }
            });
        }

        /**
         * Checks if the variable is initialized to null.
         * @param uVariable The variable to check.
         */
        private void checkAssignToNull(@NonNull final UVariable uVariable) {
            final UExpression initializer = uVariable.getUastInitializer();

            // Make sure the variable is initialized at all.
            if (initializer != null) {
                if (nullInferer.isValueNullable(initializer)) {
                    context.report(ISSUE,
                            (UElement) uVariable,
                            context.getLocation((UElement) uVariable),
                            "You should not assign a possibly null value to a variable annotated with @NonNull.");
                }
            } else if (!(uVariable instanceof UParameter)) {
                // If not initialized, it will be null.
                context.report(ISSUE,
                        (UElement) uVariable,
                        context.getLocation((UElement) uVariable),
                        "You should initialize a @NonNull variable otherwise it will be null when declared.");
            }
        }

        /**
         * Checks the variable for various obvious nullness issues if it is annotated with @NonNull.
         * @param uVariable The variable declaration to check.
         */
        @Override
        public void visitVariable(@NonNull final UVariable uVariable) {
            if (uVariable.findAnnotation(FQ_NONNULL_ANNOTATION) != null) {
                checkAssignToNull(uVariable);
            }
        }

        /**
         * Checks if we are assigning a value that could be null to a variable that is annotated @NonNull.
         * @param node The node to check.
         */
        @Override
        public void visitBinaryExpression(UBinaryExpression node) {
            // We only care if we're assigning to it.
            if (node.getOperator() == UastBinaryOperator.ASSIGN && node.getLeftOperand() != null) {
                // Resolve the left operand so we can check what it's annotated with.
                final JavaEvaluator evaluator = context.getEvaluator();
                final PsiElement element = evaluator.resolve(node.getLeftOperand().getPsi());

                if (element != null && element instanceof PsiVariable) {
                    final PsiModifierList modifierList = ((PsiVariable) element).getModifierList();

                    if (modifierList.findAnnotation(FQ_NONNULL_ANNOTATION) != null) {
                        if (nullInferer.isValueNullable(node.getRightOperand())) {
                            context.report(ISSUE,
                                    node,
                                    context.getLocation(node),
                                    "You should not assign a possibly null value to a variable annotated with @NonNull.");
                        }
                    }
                }
            }
        }

        /**
         * Checks if a nullable value is passed into a @NonNull parameter
         */
        @Override
        public void visitCallExpression(UCallExpression node) {
            final PsiMethod method = node.resolve();

            if (method != null) {
                final PsiParameter[] parameters = method.getParameterList().getParameters();

                for (int i = 0; i < node.getValueArgumentCount(); i++) {
                    final PsiModifierList modifierList = parameters[i].getModifierList();

                    if (modifierList.findAnnotation(FQ_NONNULL_ANNOTATION) != null) {
                        if (nullInferer.isValueNullable(node.getValueArguments().get(i))) {
                            context.report(ISSUE,
                                    node.getValueArguments().get(i),
                                    context.getLocation(node.getValueArguments().get(i)),
                                    "This argument is marked @NonNull but the value passed in is nullable.");
                        }
                    }
                }
            }
        }
    }
}
