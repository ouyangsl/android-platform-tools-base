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
import static com.android.SdkConstants.ATTR_SHOW_AS_ACTION;
import static com.android.SdkConstants.ATTR_TITLE;
import static com.android.SdkConstants.ATTR_VISIBLE;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.VALUE_FALSE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector.JavaScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

/**
 * Check that the right namespace is used for app compat menu items
 *
 * Using app:showAsAction instead of android:showAsAction leads to problems, but
 * isn't caught by the API Detector since it's not in the Android namespace.
 */
public class AppCompatResourceDetector extends ResourceXmlDetector implements JavaScanner {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "AppCompatResource", //$NON-NLS-1$
            "Menu namespace",
            "Ensures that menu items are using the right namespace",

            "TODO: Write explanation",

            Category.USABILITY,
            5,
            Severity.ERROR,
            new Implementation(
                    AppCompatResourceDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Constructs a new {@link com.android.tools.lint.checks.AppCompatResourceDetector} */
    public AppCompatResourceDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MENU;
    }

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_SHOW_AS_ACTION);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Project mainProject = context.getMainProject();
        if (mainProject.isGradleProject()) {
            Boolean appCompat = mainProject.dependsOn("com.android.support:appcompat-v7");
            if (ANDROID_URI.equals(attribute.getNamespaceURI())) {
                if (context.getFolderVersion() >= 14) {
                    return;
                }
                if (appCompat == Boolean.TRUE) {
                    context.report(ISSUE, attribute,
                            context.getLocation(attribute),
                            "Should use app:showAsAction with the appcompat library with "
                                    + "xmlns:app=\"http://schemas.android.com/apk/res-auto\"",
                            null);
                }
            } else {
                if (appCompat == Boolean.FALSE) {
                    context.report(ISSUE, attribute,
                            context.getLocation(attribute),
                            "Should use android:showAsAction when not using the appcompat library",
                            null);
                }
            }
        }
    }
}
