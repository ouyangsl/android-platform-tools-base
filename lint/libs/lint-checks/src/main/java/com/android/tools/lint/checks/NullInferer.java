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
import com.android.tools.lint.detector.api.JavaContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import org.jetbrains.uast.UBinaryExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UDoWhileExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UForExpression;
import org.jetbrains.uast.UIfExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UVariable;
import org.jetbrains.uast.UWhileExpression;
import org.jetbrains.uast.UastBinaryOperator;
import org.jetbrains.uast.java.JavaUMethod;
import org.jetbrains.uast.visitor.AbstractUastVisitor;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static com.android.SdkConstants.SUPPORT_ANNOTATIONS_PREFIX;

/**
 * Traverses the AST of a file and tries to infer which of the values are nullable.
 * Used to check for nullability in other detectors (e.g., NullPointerExceptionDetector)
 */
public class NullInferer extends AbstractUastVisitor {
    // The maximum number of times to run the infer step.
    private static final int MAX_RUNS = 20;

    private final Set<PsiIdentifier> mIsNullable = new HashSet<>();

    private JavaContext mJavaContext;

    /**
     * Tries to infer which variables and methods might be nullable.
     * @param mJavaContext The context that contains the file. Also used to resolve expressions.
     */
    public void infer(@NonNull final JavaContext mJavaContext) {
        this.mJavaContext = mJavaContext;

        // Repeatedly run until we stop finding more things that could be nullable, or until we hit
        // the max number of runs.
        int i = 0;
        while (true) {
             final int nullableMethodCount = mIsNullable.size();

             // Do another check.
             mJavaContext.getUastFile().accept(this);

             // This means we didn't find anything new on this pass.
             if (nullableMethodCount == mIsNullable.size()) {
                 break;
             }

             i++;
             if (i >= MAX_RUNS) {
                 break;
             }
        }
    }

    /**
     * If the method has the @Nullable annotation, will add to the list of potentially nullable things.
     * @param node The method to check.
     */
    @Override
    public boolean visitMethod(UMethod node) {
        if (node.findAnnotation(SUPPORT_ANNOTATIONS_PREFIX + "Nullable") != null) {
            mIsNullable.add(node.getNameIdentifier());
        }

        return super.visitMethod(node);
    }

    /**
     * If the variable has the @Nullable annotation, will add to the list of potentially nullable things.
     * @param node The variable to check.
     */
    @Override
    public boolean visitVariable(UVariable node) {
        if (node.findAnnotation(SUPPORT_ANNOTATIONS_PREFIX + "Nullable") != null) {
            mIsNullable.add(node.getNameIdentifier());
        }

        if (node.getUastInitializer() != null) {
            if (isValueNullable(node.getUastInitializer())) {
                mIsNullable.add(node.getNameIdentifier());
            }
        }

        return super.visitVariable(node);
    }

    /**
     * Checks if we are assigning a potentially null value to a variable. If we are, then that variable
     * is nullable.
     * @param node The binary expression to check. Only looks for assignment, ignoring all other operators.
     */
    @Override
    public boolean visitBinaryExpression(UBinaryExpression node) {
        if (node.getOperator() == UastBinaryOperator.ASSIGN) {
            if (node.getRightOperand() != null && isValueNullable(node.getRightOperand())) {
                if (node.getLeftOperand() != null) {
                    final JavaEvaluator evaluator = mJavaContext.getEvaluator();
                    final PsiElement element = evaluator.resolve(node.getLeftOperand().getPsi());

                    if (element != null && element instanceof PsiVariable) {
                        mIsNullable.add(((PsiVariable) element).getNameIdentifier());
                    }
                }
            }
        }

        return super.visitBinaryExpression(node);
    }

    /**
     * If this UReturnExpression returns a potentially nullable value, adds the method to the list of
     * nullable methods.
     * @param node The return expression to check.
     */
    @Override
    public boolean visitReturnExpression(UReturnExpression node) {
        if (node.getReturnExpression() != null && isValueNullable(node.getReturnExpression())) {
            @NonNull final Optional<JavaUMethod> containingMethod =
                    (Optional<JavaUMethod>) ChecksUtils.getContainingElementOfType(node, JavaUMethod.class);

            containingMethod.ifPresent(method -> mIsNullable.add(method.getNameIdentifier()));
        }

        return super.visitReturnExpression(node);
    }

    /**
     * @param uExpression The expression to check.
     * @return If the expression is a null value, or if it is a method call that might return null,
     *         or if it is referencing a variable that might be null.
     */
    public boolean isValueNullable(final UExpression uExpression) {
        if (uExpression == null) {
            return false;
        }

        // Check if we've already checked for nullness manually, if so, then we don't need to worry about it
        if (checkedForNull(uExpression, uExpression)) {
            return false;
        }

        // If we're calling a method, check if that method is on our list
        if (uExpression instanceof UCallExpression) {
            final PsiMethod method = ((UCallExpression) uExpression).resolve();

            if (method != null) {
                if (mIsNullable.contains(method.getNameIdentifier())) {
                    return true;
                }
            }
        } else if (uExpression instanceof UReferenceExpression) {
            // If we're referencing a variable, resolve that reference and check if it's a variable
            // that's on our list.
            final PsiElement node = ((UReferenceExpression) uExpression).resolve();

            if (node != null) {
                if (node instanceof PsiVariable) {
                    if (mIsNullable.contains(((PsiVariable) node).getNameIdentifier())) {
                        return true;
                    }
                }
            }
        }

        return uExpression.getExpressionType() == PsiType.NULL;
    }

    /**
     * Looks through a expression to find a null check.
     * @param needsChecking The expression that should be checked for nullability.
     * @param expression The expression to check. Should be the condition of some control structure
     *                   (e.g., UIfExpression)
     * @return true if 'needsChecking' is checked for nullability, false if not.
     */
    private boolean lookForNullCheck(UExpression needsChecking, UExpression expression) {
        if (expression instanceof UBinaryExpression) {
            final UBinaryExpression cond = (UBinaryExpression) expression;

            final UExpression lhs = cond.getLeftOperand();
            final UExpression rhs = cond.getRightOperand();

            // If it's &&, then if either side contains the null check it's fine.
            if (cond.getOperator() == UastBinaryOperator.LOGICAL_AND) {
                return lookForNullCheck(needsChecking, lhs) || lookForNullCheck(needsChecking, rhs);
            } else if (cond.getOperator() == UastBinaryOperator.LOGICAL_OR) {
                // If it's ||, then both sides need to have the null check.
                return lookForNullCheck(needsChecking, lhs) && lookForNullCheck(needsChecking, lhs);
            } else if (cond.getOperator() == UastBinaryOperator.IDENTITY_NOT_EQUALS) {
                final JavaEvaluator evaluator = mJavaContext.getEvaluator();

                // Checks if the right thing is being checked.
                if (lhs != null && lhs.getExpressionType() == PsiType.NULL) {
                    if (evaluator.resolve(rhs.getPsi()).isEquivalentTo(evaluator.resolve(needsChecking.getPsi()))) {
                        return true;
                    }
                } else if (rhs != null && rhs.getExpressionType() == PsiType.NULL) {
                    if (evaluator.resolve(lhs.getPsi()).isEquivalentTo(evaluator.resolve(needsChecking.getPsi()))) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Recursively checks up the parents of 'uExpression' to see if there is a condition that checks
     * if 'needsChecking' is nullable.
     * @param needsChecking The expression that should be checked for nullability.
     * @param uExpression The expression to look for conditions in/in the parents of.
     * @return true if 'needsChecking' is checked for nullability, false if not.
     */
    private boolean checkedForNull(UExpression needsChecking, UElement uExpression) {
        // If it's one of the types that has a condition, check it.
        if (uExpression instanceof UIfExpression) {
            final UExpression condition = ((UIfExpression) uExpression).getCondition();

            if (lookForNullCheck(needsChecking, condition)) {
                return true;
            }
        } else if (uExpression instanceof UWhileExpression) {
            final UExpression condition = ((UWhileExpression) uExpression).getCondition();

            if (lookForNullCheck(needsChecking, condition)) {
                return true;
            }
        } else if (uExpression instanceof UForExpression) {
            final UExpression condition = ((UForExpression) uExpression).getCondition();

            if (lookForNullCheck(needsChecking, condition)) {
                return true;
            }
        } else if (uExpression instanceof UDoWhileExpression) {
            final UExpression condition = ((UDoWhileExpression) uExpression).getCondition();

            if (lookForNullCheck(needsChecking, condition)) {
                return true;
            }
        }

        // Recurse upwards to look for more checks.
        if (uExpression.getUastParent() != null) {
            return checkedForNull(needsChecking, uExpression.getUastParent());
        } else {
            return false;
        }
    }
}
