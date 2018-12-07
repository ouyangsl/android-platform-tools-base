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

import com.android.annotations.VisibleForTesting;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

import javax.annotation.Nullable;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.NS_RESOURCES;
import static com.android.SdkConstants.TAG_USES_PERMISSION;


/**
 * Checks if the "android.permission.BROADCAST_STICKY" is used.
 * Example:
 * <uses-permission android:name="android.permission.BROADCAST_STICKY" />
 * in the manifest file.
 *
 * For further details please read the paper "Security code smells in Android ICC",
 * available at http://scg.unibe.ch/archive/papers/Gadi18a.pdf
 *
 * University of Bern, Software Composition Group
 *
 */
public class BroadcastStickyPermissionDetector extends Detector implements Detector.XmlScanner {
    @VisibleForTesting
    public static final String REPORT_MESSAGE = "The usage of sticky broadcasts is discouraged due to its weak security. " +
                                                "Replace usages of sticky broadcasts with alternatives and remove this permission";

    public static final Issue ISSUE = Issue.create("StickyBroadcast", //$NON-NLS-1$
            "SM05: Sticky Broadcast | The usage of sticky broadcasts is discouraged",

            "Sticky broadcasts offer no security as anyone can access and modify them." +
            " Note that they are also deprecated as of API Level 21." +
            " The recommended pattern is to use a non-sticky broadcast to report " +
            " that something has changed, with another mechanism for apps " +
            " to retrieve the current value whenever desired, e.g., an explicit intent (see the provided link).",
            Category.SECURITY,
            6,
            Severity.WARNING,
            new Implementation(
                    BroadcastStickyPermissionDetector.class,
                    Scope.MANIFEST_SCOPE))
            .addMoreInfo("https://developer.android.com/reference/android/content/Context.html");


    private static final String BROADCAST_STICKY = "android.permission.BROADCAST_STICKY";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singleton(TAG_USES_PERMISSION);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element usesPermissionElement) {
        Attr permissionAttr = findPermissionNameAttr(usesPermissionElement);
        if (permissionAttr != null && permissionAttr.getValue() != null
                && permissionAttr.getValue().equals(BROADCAST_STICKY)) {
            context.report(ISSUE, usesPermissionElement, context.getLocation(permissionAttr), REPORT_MESSAGE);
        }
    }

    @Nullable
    private Attr findPermissionNameAttr(@NotNull Element usesPermissionElement) {
        Attr nameAttribute = usesPermissionElement.getAttributeNodeNS(NS_RESOURCES, ATTR_NAME);
        if (nameAttribute != null) {
            return nameAttribute;
        }
        return null;
    }

}
