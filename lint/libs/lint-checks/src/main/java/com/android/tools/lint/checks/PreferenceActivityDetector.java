/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PACKAGE;
import static com.android.SdkConstants.TAG_ACTIVITY;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.JavaParser.ResolvedNode;
import com.android.tools.lint.client.api.JavaParser.ResolvedClass;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import lombok.ast.AstVisitor;
import lombok.ast.ClassDeclaration;
import lombok.ast.ForwardingAstVisitor;

/**
 * Ensures that PreferenceActivity and its subclasses are never exported.
 */
public class PreferenceActivityDetector extends Detector
        implements Detector.XmlScanner, Detector.JavaScanner {
    // TODO Allow exporting PreferenceActivity if isValidFragment() is also overridden
    //      and the build target is always higher than Android 4.4 (API level 19).

    public static final Issue ISSUE = Issue.create(
            "ExportedPreferenceActivity", //$NON-NLS-1$
            "PreferenceActivity should not be exported",
            "Checks that PreferenceActivity and its subclasses are never exported",
            "Fragment injection gives anyone who can send your PreferenceActivity an intent the "
                + "ability to load any fragment, with any arguments, in your process.",
            Category.SECURITY,
            8,
            Severity.WARNING,
            new Implementation(
                    PreferenceActivityDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)));
    private static final String PREFERENCE_ACTIVITY = "android.preference.PreferenceActivity"; //$NON-NLS-1$

    private final Map<String, PossibleReport> mExportedActivities =
            new HashMap<String, PossibleReport>();
    private String mPackage = null;

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    // ---- Implements XmlScanner ----
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_PACKAGE);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        mPackage = attribute.getValue();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_ACTIVITY);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (SecurityDetector.getExported(element)) {
            String fqcn = getFqcn(element);
            if (fqcn != null) {
                if (fqcn.equals(PREFERENCE_ACTIVITY)) {
                    String message = "PreferenceActivity should not be exported";
                    context.report(ISSUE, context.getLocation(element), message, null);
                }
                mExportedActivities.put(fqcn,
                        new PossibleReport(context, element));
            }
        }
    }

    private String getFqcn(@NonNull Element activityElement) {
        String activityClassName = activityElement.getAttributeNS(ANDROID_URI, ATTR_NAME);

        if (activityClassName == null || activityClassName.isEmpty()) {
            return null;
        }

        // If the activity class name starts with a '.', it is shorthand for prepending the
        // package name specified in the manifest.
        if (activityClassName.startsWith(".")) {
            if (mPackage != null) {
                return mPackage + activityClassName;
            } else {
                return null;
            }
        }

        return activityClassName;
    }

    // ---- Implements JavaScanner ----
    @Override
    public AstVisitor createJavaVisitor(@NonNull JavaContext context) {
        if (!context.getProject().getReportIssues()) {
            return null;
        }
        return new PreferenceActivityVisitor(context);
    }

    private class PreferenceActivityVisitor extends ForwardingAstVisitor {
        private final JavaContext mContext;

        public PreferenceActivityVisitor(JavaContext context) {
            mContext = context;
        }

        @Override
        public boolean visitClassDeclaration(ClassDeclaration node) {
            ResolvedNode resolvedNode = mContext.resolve(node);
            if (!(resolvedNode instanceof ResolvedClass)) {
                return false; // There might be an inner class that we need to inspect.
            }
            ResolvedClass resolvedClass = (ResolvedClass) resolvedNode;
            String className = resolvedClass.getName();
            if (resolvedClass.isSubclassOf(PREFERENCE_ACTIVITY, false)
                    && mExportedActivities.containsKey(className)) {
                mExportedActivities.get(className).report(className);
            }

            return true; // Done: No need to look inside this class
        }
    }

    private static class PossibleReport {
        private final XmlContext mXmlContext;
        private final Node mNode;

        private PossibleReport(XmlContext xmlContext, Node node) {
            mXmlContext = xmlContext;
            mNode = node;
        }

        public void report(String className) {
            String message = String.format(
                    "PreferenceActivity subclass %1$s should not be exported",
                    className);
            mXmlContext.report(ISSUE, mNode, mXmlContext.getLocation(mNode), message, null);
        }
    }
}
