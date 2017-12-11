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
import org.jetbrains.uast.UastBinaryOperator;
import java.util.Collections;
import java.util.List;

/**
 * Checks for values being compared with == instead of .equals() when .equals() is generally the right
 * thing to do (e.g., comparing Integers (the boxed type), arrays, or Optional.
 */
public class ReferenceEqualityDetector extends Detector implements Detector.UastScanner {
    @NonNull public static final Issue ISSUE =
            Issue.create("ImproperReferenceEquality",
                    "These values should be compared using .equals() rather than ==",
                    "Comparing certain types of values using == can yield unexpected results, " +
                            "such as Integers, Strings, and arrays.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(ReferenceEqualityDetector.class,
                                       Scope.JAVA_FILE_SCOPE));


    /**
     * @return A list of UElements that this detector will traverse.
     */
    @NonNull
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UBinaryExpression.class);
    }

    /**
     * @param context The context used to report errors.
     * @return Creates a new UElementHandler that will search for our specific issues.
     */
    @NonNull
    @Override
    public UElementHandler createUastHandler(@NonNull final JavaContext context) {
        return new ReferenceEqualityHandler(context);
    }

    /**
     * The class that does the actual detecting.
     */
    private class ReferenceEqualityHandler extends UElementHandler {
        @NonNull
        private final JavaContext context;

        // All of the classes that should be compared using .equals()
        @NonNull
        private final String[] checkClasses =
                {"java.lang.Integer",
                 "java.lang.Float",
                 "java.lang.Byte",
                 "java.lang.Double",
                 "java.lang.String",
                 "java.lang.Short",
                 "java.lang.Long",
                 "java.lang.Boolean",
                 "java.lang.Character",
                 "java.util.Optional"};

        public ReferenceEqualityHandler(@NonNull final JavaContext context) {
            this.context = context;
        }

        /**
         * Checks if the type of the element is one of the types on our list (or arrays).
         * @param node The element to check.
         */
        private void checkElement(@NonNull final UElement node) {
            final PsiType type = TypeEvaluator.evaluate(node);

            // Check if it's an array type.
            // Doesn't have a qualified name, so can't do it the normal way.
            if (type.getCanonicalText().endsWith("[]")) {
                context.report(ISSUE,
                        node,
                        context.getLocation(node),
                        "You should compare arrays using .equals() rather than ==.");
            } else {
                // Check if it's any of these.
                for (@NonNull final String className : checkClasses) {
                    // Using startswith because if type is java.util.Optional with type parameter,
                    // then equals won't match it.
                    if (type.getCanonicalText().startsWith(className)) {
                        context.report(ISSUE,
                                node,
                                context.getLocation(node),
                                "You should compare values of the type " + className + " using .equals() rather than ==.");
                    }
                }
            }
        }

        /**
         * Checks both operands of the binary expression if they're any of the types on the list, if
         * it's checking for reference equality.
         * @param node The binary expression to check.
         */
        @Override
        public void visitBinaryExpression(UBinaryExpression node) {
            if (node.getOperator() == UastBinaryOperator.IDENTITY_EQUALS) {
                checkElement(node.getLeftOperand());
                checkElement(node.getRightOperand());
            }
        }
    }
}
