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
import org.jetbrains.uast.UBinaryExpression;
import org.jetbrains.uast.UDoWhileExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UForExpression;
import org.jetbrains.uast.UIfExpression;
import org.jetbrains.uast.UWhileExpression;
import org.jetbrains.uast.UastBinaryOperator;
import org.jetbrains.uast.visitor.AbstractUastVisitor;
import java.util.Arrays;
import java.util.List;

/**
 * Checks if the assignment operator, =, is used rather than an actual equality check, ==
 */
public class AssignmentInConditionalDetector extends Detector implements Detector.UastScanner {
    @NonNull
    public static final Issue ISSUE =
            Issue.create("AssignmentInCondition",
                    "Used = rather than == in a condition.",
                    "You should use == for comparison rather than =, as using = will assign " +
                            "a new value to your variable, rather than comparing it.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(AssignmentInConditionalDetector.class,
                                       Scope.JAVA_FILE_SCOPE));

    /**
     * @return A list of UElements that this detector will traverse.
     */
    @NonNull
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UIfExpression.class,
                             UWhileExpression.class,
                             UDoWhileExpression.class,
                             UForExpression.class);
    }

    /**
     * @param context The context used to report errors.
     * @return Creates a new UElementHandler that will search for our specific issues.
     */
    @NonNull
    @Override
    public UElementHandler createUastHandler(@NonNull final JavaContext context) {
        return new AssignmentInConditionalHandler(context);
    }

    private class AssignmentChecker extends AbstractUastVisitor {
        private boolean foundAssignment = false;

        @Override
        public boolean visitBinaryExpression(UBinaryExpression node) {
            if (node.getOperator() == UastBinaryOperator.ASSIGN) {
                foundAssignment = true;
            }

            return super.visitBinaryExpression(node);
        }
    }

    private class AssignmentInConditionalHandler extends UElementHandler {
        @NonNull private final JavaContext context;

        public AssignmentInConditionalHandler(@NonNull final JavaContext context) {
            this.context = context;
        }

        /**
         * Checks if the node passed in contains an assignment, and reports an issue if it does.
         * @param node The node to check.
         */
        private void checkCondition(@NonNull final UElement node) {
            final AssignmentChecker checker = new AssignmentChecker();
            node.accept(checker);

            if (checker.foundAssignment) {
                context.report(ISSUE,
                        node,
                        context.getLocation(node),
                        "Use == rather than = in a condition.");
            }
        }

        /**
         * Checks if condition contains an assignment.
         * @param node The if statement node.
         */
        @Override
        public void visitIfExpression(@NonNull final UIfExpression node) {
            checkCondition(node.getCondition());
        }

        /**
         * Checks if condition contains an assignment.
         * @param node The if statement node.
         */
        @Override
        public void visitWhileExpression(@NonNull final UWhileExpression node) {
            checkCondition(node.getCondition());
        }

        /**
         * Checks if condition contains an assignment.
         * @param node The if statement node.
         */
        @Override
        public void visitDoWhileExpression(@NonNull final UDoWhileExpression node) {
            checkCondition(node.getCondition());
        }

        /**
         * Checks if condition contains an assignment.
         * @param node The if statement node.
         */
        @Override
        public void visitForExpression(@NonNull final UForExpression node) {
            checkCondition(node.getCondition());
        }
    }
}
