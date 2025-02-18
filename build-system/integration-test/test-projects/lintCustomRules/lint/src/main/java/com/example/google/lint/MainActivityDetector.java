/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.example.google.lint;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class MainActivityDetector extends ResourceXmlDetector implements XmlScanner {
    public static final Issue ISSUE =
            Issue.create(
                    "UnitTestLintCheck",
                    "Custom Lint Check",
                    "This app should not have any activities.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    new Implementation(MainActivityDetector.class, Scope.MANIFEST_SCOPE)).
                    // Make sure other integration tests don't pick this up.
                    // The unit test will turn it on with android.lintOptions.check <id>
                    setEnabledByDefault(false);

    /** No-args constructor used by the lint framework to instantiate the detector. */
    public MainActivityDetector() {}

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singleton("activity");
    }

    @Override
    public void visitElement(XmlContext context, Element activityElement) {
        context.report(
                ISSUE,
                activityElement,
                context.getLocation(activityElement),
                "Should not specify <activity>.",
                null);
        // placeholder
    }
}
