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
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import java.util.Collections;
import java.util.List;

/**
 * Checks for possibly null pointer exceptions.
 */
public class NullPointerExceptionDetector extends Detector implements Detector.UastScanner {
    @NonNull
    public static final Issue ISSUE =
            Issue.create("PossibleNullPointerException",
                    "This code could cause a NullPointerException.",
                    "The receiver is a possibly null value, so you should check it before trying to reference anything inside it.",
                    Category.CORRECTNESS,
                    10,
                    Severity.WARNING,
                    new Implementation(NullPointerExceptionDetector.class,
                                       Scope.JAVA_FILE_SCOPE));

    @NonNull
    private NullInferer nullInferer;

    /**
     * @return A list of UElements that this detector will traverse.
     */
    @NonNull
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UQualifiedReferenceExpression.class);
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

        return new NullPointerExceptionHandler(context);
    }

    /**
     * The class that does the actual detecting.
     */
    private class NullPointerExceptionHandler extends UElementHandler {
        @NonNull
        private final JavaContext context;

        public NullPointerExceptionHandler(@NonNull final JavaContext context) {
            this.context = context;
        }

        /**
         * Checks this reference expression to see if the receiver is nullable.
         */
        @Override
        public void visitQualifiedReferenceExpression(UQualifiedReferenceExpression node) {
            if (nullInferer.isValueNullable(node.getReceiver())) {
                context.report(ISSUE,
                        node,
                        context.getLocation(node),
                        "You should check this value before referencing something inside it, as it is potentially null.");
            }
        }
    }
}
