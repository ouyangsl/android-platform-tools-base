/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.tools.lint.checks.ChromeOSDetector.PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE;
import static com.android.tools.lint.checks.ChromeOSDetector.UNSUPPORTED_CHROMEOS_HARDWARE;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("javadoc")
public class ChromeOSDetectorTest extends AbstractCheckTest {
    private Set<Issue> mEnabled = new HashSet<Issue>();

    @Override
    protected Detector getDetector() {
        return new AndroidTvDetector();
    }

    @Override
    protected TestConfiguration getConfiguration(LintClient client, Project project) {
        return new TestConfiguration(client, project, null) {
            @Override
            public boolean isEnabled(@NonNull Issue issue) {
                return super.isEnabled(issue) && mEnabled.contains(issue);
            }
        };
    }

    public void testInvalidUnsupportedHardware() throws Exception {
        mEnabled = Collections.singleton(UNSUPPORTED_CHROMEOS_HARDWARE);
        String expected =
                "AndroidManifest.xml:6: Error: Expecting android:required=\"false\" for this hardware feature that may not be supported by all Chrome OS devices. [UnsupportedChromeOSHardware]\n"
                + "        android:name=\"android.hardware.touchscreen\" android:required=\"true\"/>\n"
                + "                                                    ~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n";
        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\">\n"
                        + "    <uses-feature\n"
                        + "        android:name=\"android.hardware.touchscreen\" android:required=\"true\"/>\n"
                        + "\n"
                        + "</manifest>\n"));
        assertEquals(expected, result);
    }

    public void testValidUnsupportedHardware() throws Exception {
        mEnabled = Collections.singleton(UNSUPPORTED_CHROMEOS_HARDWARE);
        String expected = "No warnings.";
        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                        + "    <uses-feature\n"
                        + "        android:name=\"android.hardware.touchscreen\"\n"
                        + "        android:required=\"false\" />\n"
                        + "</manifest>\n"));
        assertEquals(expected, result);
    }

    public void testValidPermissionImpliesNotMissingUnsupportedHardware() throws Exception {
        mEnabled = Collections.singleton(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE);
        String expected = "No warnings.";
        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                        + "    <uses-permission android:name=\"android.permission.CALL_PHONE\"/>\n"
                        + "    <uses-feature android:required=\"false\" android:name=\"android.hardware.telephony\"/>\n"
                        + "</manifest>\n"));
        assertEquals(expected, result);
    }

    public void testInvalidPermissionImpliesNotMissingUnsupportedHardware() throws Exception {
        mEnabled = Collections.singleton(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE);
        String expected =
                "AndroidManifest.xml:4: Error: Permission exists without corresponding hardware <uses-feature android:name=\"android.hardware.telephony\" required=\"false\"> tag. [PermissionImpliesUnsupportedChromeOSHardware]\n"
                + "    <uses-permission android:name=\"android.permission.CALL_PHONE\"/>\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n";
        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                        + "    <uses-permission android:name=\"android.permission.CALL_PHONE\"/>\n"
                        + "</manifest>\n"));
        assertEquals(expected, result);
    }

    public void testInvalidPermissionImpliesMissingUnsupportedHardware() throws Exception {
        mEnabled = Collections.singleton(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE);
        String expected =
                "AndroidManifest.xml:4: Error: Permission exists without corresponding hardware <uses-feature android:name=\"android.hardware.telephony\" required=\"false\"> tag. [PermissionImpliesUnsupportedChromeOSHardware]\n"
                + "    <uses-permission android:name=\"android.permission.CALL_PHONE\"/>\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n";
        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                        + "    <uses-permission android:name=\"android.permission.CALL_PHONE\"/>\n"
                        + "</manifest>\n"));
        assertEquals(expected, result);
    }

    public void testValidPermissionImpliesUnsupportedHardware() throws Exception {
        mEnabled = Collections.singleton(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE);
        String expected = "No warnings.";
        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                        + "    <uses-permission android:name=\"android.permission.WRITE_EXTERNAL_STORAGE\"/>\n"
                        + "</manifest>\n"));
        assertEquals(expected, result);
    }

    public void testOldTargetSdkVersion() throws Exception {
        mEnabled = Collections.singleton(TARGET_SDK_PRIOR_TO_N);
        String expected =
                "AndroidManifest.xml:4: Warning: The <uses-sdk> element should specify a targetSdkVersion of 24 or higher, and the application should support the window management features made available in Android 7.0. [TargetSdkPriorToN]\n"
                + "    <uses-sdk android:targetSdkVersion=\"23\" />\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n";
        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                        + "    <uses-sdk android:targetSdkVersion=\"23\" />\n"
                        + "</manifest>\n"));
        assertEquals(expected, result);
    }

    public void testNewTargetSdkVersion() throws Exception {
        mEnabled = Collections.singleton(TARGET_SDK_PRIOR_TO_N);
        String expected = "No warnings.";
        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                        + "    <uses-sdk android:targetSdkVersion=" 24 " />\n"
                        + "</manifest>\n"));
        assertEquals(expected, result);
    }

    public void testNoTargetSdkVersion() throws Exception {
        mEnabled = Collections.singleton(TARGET_SDK_PRIOR_TO_N);
        String expected = "No warnings.";
        String result = lintProject(xml(FN_ANDROID_MANIFEST_XML, ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                        + "</manifest>\n"));
        assertEquals(expected, result);
    }
}
