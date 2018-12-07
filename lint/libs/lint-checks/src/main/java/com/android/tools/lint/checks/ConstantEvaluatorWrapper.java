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

import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.JavaContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UExpression;

/**
 * Helper class for evaluating constants.
 *
 * <p>For further details please read the paper "Security code smells in Android ICC", available at
 * http://scg.unibe.ch/archive/papers/Gadi18a.pdf
 *
 * <p>University of Bern, Software Composition Group
 */
class ConstantEvaluatorWrapper {

    @Nullable
    static Long resolveAsLong(@Nullable UExpression expression, @NotNull JavaContext context) {
        ConstantEvaluator evaluator = new ConstantEvaluator();
        evaluator.allowFieldInitializers();

        Object value = expression.evaluate();
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Integer) {
            return new Long((Integer) value);
        }

        return null;
    }

    @Nullable
    static String resolveAsString(@Nullable UExpression expression, @NotNull JavaContext context) {
        ConstantEvaluator evaluator = new ConstantEvaluator();
        evaluator.allowFieldInitializers();
        Object value = evaluator.evaluate(expression);
        return value instanceof String ? (String) value : null;
    }
}
