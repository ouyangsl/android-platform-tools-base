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

import static com.android.tools.lint.checks.ChromeOsDetector.NON_RESIZEABLE_ACTIVITY;
import static com.android.tools.lint.checks.ChromeOsDetector.PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE;
import static com.android.tools.lint.checks.ChromeOsDetector.SETTING_ORIENTATION_ON_ACTIVITY;
import static com.android.tools.lint.checks.ChromeOsDetector.UNSUPPORTED_CHROME_OS_HARDWARE;

import com.android.tools.lint.checks.infrastructure.ProjectDescription;
import com.android.tools.lint.detector.api.Detector;

@SuppressWarnings("javadoc")
public class ChromeOsDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new ChromeOsDetector();
    }

    public void testInvalidUnsupportedHardware() {
        String expected =
                ""
                        + "AndroidManifest.xml:5: Error: Expecting android:required=\"false\" for this hardware feature that may not be supported by all Chrome OS devices [UnsupportedChromeOsHardware]\n"
                        + "        android:name=\"android.hardware.touchscreen\" android:required=\"true\"/>\n"
                        + "                                                    ~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n";
        lint().files(
                        manifest(
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    xmlns:tools=\"http://schemas.android.com/tools\">\n"
                                        + "    <uses-feature\n"
                                        + "        android:name=\"android.hardware.touchscreen\" android:required=\"true\"/>\n"
                                        + "\n"
                                        + "</manifest>\n"))
                .issues(UNSUPPORTED_CHROME_OS_HARDWARE)
                .run()
                .expect(expected)
                .expectFixDiffs(
                        ""
                                + "Autofix for AndroidManifest.xml line 5: Set required=\"false\":\n"
                                + "@@ -7 +7\n"
                                + "-         android:required=\"true\" />\n"
                                + "+         android:required=\"false\" />\n");
    }

    public void testValidUnsupportedHardware() {
        lint().files(
                        manifest(
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                                        + "    <uses-feature\n"
                                        + "        android:name=\"android.hardware.touchscreen\"\n"
                                        + "        android:required=\"false\" />\n"
                                        + "</manifest>\n"))
                .issues(UNSUPPORTED_CHROME_OS_HARDWARE)
                .run()
                .expectClean();
    }

    public void testValidSupportedHardware() {
        lint().files(
                        manifest(
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                                        + "    <uses-feature\n"
                                        + "        android:name=\"android.hardware.microphone\" />\n"
                                        + "</manifest>\n"))
                .issues(UNSUPPORTED_CHROME_OS_HARDWARE)
                .run()
                .expectClean();
    }

    public void testValidPermissionImpliesNotMissingUnsupportedHardware() {
        lint().files(
                        manifest(
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                                        + "    <uses-permission android:name=\"android.permission.CALL_PHONE\"/>\n"
                                        + "    <uses-feature android:required=\"false\" android:name=\"android.hardware.telephony\"/>\n"
                                        + "</manifest>\n"))
                .issues(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE)
                .run()
                .expectClean();
    }

    public void testInvalidPermissionImpliesNotMissingUnsupportedHardware() {
        String expected =
                ""
                        + "AndroidManifest.xml:4: Error: Permission exists without corresponding hardware <uses-feature android:name=\"android.hardware.telephony\" required=\"false\"> tag [PermissionImpliesUnsupportedChromeOsHardware]\n"
                        + "    <uses-permission android:name=\"android.permission.CALL_PHONE\"/>\n"
                        + "     ~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings";
        lint().files(
                        manifest(
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                                        + "    <uses-permission android:name=\"android.permission.CALL_PHONE\"/>\n"
                                        + "</manifest>\n"))
                .issues(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE)
                .run()
                .expect(expected);
    }

    public void testInvalidPermissionImpliesMissingUnsupportedHardware() {
        String expected =
                ""
                        + "AndroidManifest.xml:4: Error: Permission exists without corresponding hardware <uses-feature android:name=\"android.hardware.telephony\" required=\"false\"> tag [PermissionImpliesUnsupportedChromeOsHardware]\n"
                        + "    <uses-permission android:name=\"android.permission.CALL_PHONE\"/>\n"
                        + "     ~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings";
        lint().files(
                        manifest(
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                                        + "    <uses-permission android:name=\"android.permission.CALL_PHONE\"/>\n"
                                        + "</manifest>\n"))
                .issues(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE)
                .run()
                .expect(expected);
    }

    public void testCameraPermissionImpliesMissingUnsupportedCamera() {
        String expected =
                ""
                        + "AndroidManifest.xml:4: Error: Permission exists without corresponding hardware <uses-feature android:name=\"android.hardware.camera\" required=\"false\"> tag [PermissionImpliesUnsupportedChromeOsHardware]\n"
                        + "    <uses-permission android:name=\"android.permission.CAMERA\"/>\n"
                        + "     ~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings";
        lint().files(
                        manifest(
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                                        + "    <uses-permission android:name=\"android.permission.CAMERA\"/>\n"
                                        + "    <uses-feature android:name=\"android.hardware.camera.autofocus\" />\n"
                                        + "</manifest>\n"))
                .issues(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE)
                .run()
                .expect(expected);
    }

    public void testCameraPermissionImpliesEitherCameraFeature() {
        lint().files(
                        manifest(
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                                        + "    <uses-permission android:name=\"android.permission.CAMERA\"/>\n"
                                        + "    <uses-feature android:name=\"android.hardware.camera\" android:required=\"false\"/>\n"
                                        + "</manifest>\n"))
                .issues(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE)
                .run()
                .expectClean();
        lint().files(
                        manifest(
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                                        + "    <uses-permission android:name=\"android.permission.CAMERA\"/>\n"
                                        + "    <uses-feature android:name=\"android.hardware.camera.any\" android:required=\"false\"/>\n"
                                        + "</manifest>\n"))
                .issues(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE)
                .run()
                .expectClean();
    }

    public void testCameraPermissionImpliesNotMissingUnsupportedHardware() {
        lint().files(
                        manifest(
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                                        + "    <uses-permission android:name=\"android.permission.CAMERA\"/>\n"
                                        + "    <uses-feature android:required=\"false\" android:name=\"android.hardware.camera\"/>\n"
                                        + "    <uses-feature android:required=\"false\" android:name=\"android.hardware.camera.autofocus\"/>\n"
                                        + "</manifest>\n"))
                .issues(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE)
                .run()
                .expectClean();
    }

    public void testValidPermissionImpliesUnsupportedHardware() {
        lint().files(
                        manifest(
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                                        + "    <uses-permission android:name=\"android.permission.WRITE_EXTERNAL_STORAGE\"/>\n"
                                        + "</manifest>\n"))
                .issues(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE)
                .run()
                .expectClean();
    }

    public void testValidResizableActivities() {
        lint().files(
                        manifest(
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                                        + "    <application>\n"
                                        + "        <activity android:name=\".MainActivity\" android:resizeableActivity=\"true\"/>\n"
                                        + "    </application>\n"
                                        + "</manifest>\n"))
                .issues(NON_RESIZEABLE_ACTIVITY)
                .run()
                .expectClean();
    }

    public void testInvalidResizableActivities() {

        String expected =
                ""
                        + "AndroidManifest.xml:5: Warning: Expecting android:resizeableActivity=\"true\" for this activity so the user can take advantage of the multi-window environment on all devices. NOTE: beginning with Android 12, all apps run in multi-window mode on large screen devices. [NonResizeableActivity]\n"
                        + "        <activity android:name=\".MainActivity\" android:resizeableActivity=\"false\"/>\n"
                        + "                                               ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n";

        lint().files(
                        manifest(
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                                        + "    <application>\n"
                                        + "        <activity android:name=\".MainActivity\" android:resizeableActivity=\"false\"/>\n"
                                        + "    </application>\n"
                                        + "</manifest>\n"))
                .issues(NON_RESIZEABLE_ACTIVITY)
                .run()
                .expect(expected)
                .expectFixDiffs(
                        ""
                                + "Autofix for AndroidManifest.xml line 5: Set resizeableActivity=\"true\":\n"
                                + "@@ -8 +8\n"
                                + "-             android:resizeableActivity=\"false\" />\n"
                                + "+             android:resizeableActivity=\"true\" />");
    }

    public void testValidOrientationSetOnActivity() {
        lint().files(
                        manifest(
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                                        + "    <application>\n"
                                        + "        <activity android:name=\".MainActivity\" android:screenOrientation=\"fullSensor\"/>\n"
                                        + "    </application>\n"
                                        + "</manifest>\n"))
                .issues(SETTING_ORIENTATION_ON_ACTIVITY)
                .run()
                .expectClean();
    }

    public void testInvalidOrientationSetOnActivity() {

        String expected =
                ""
                        + "AndroidManifest.xml:5: Warning: Expecting android:screenOrientation=\"unspecified\" or \"fullSensor\" for this activity so the user can use the application in any orientation and provide a great experience on Chrome OS devices [LockedOrientationActivity]\n"
                        + "        <activity android:name=\".MainActivity\" android:screenOrientation=\"portrait\"/>\n"
                        + "                                               ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n";
        lint().files(
                        manifest(
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "          xmlns:tools=\"http://schemas.android.com/tools\">\n"
                                        + "    <application>\n"
                                        + "        <activity android:name=\".MainActivity\" android:screenOrientation=\"portrait\"/>\n"
                                        + "    </application>\n"
                                        + "</manifest>\n"))
                .issues(SETTING_ORIENTATION_ON_ACTIVITY)
                .run()
                .expect(expected)
                .expectFixDiffs(
                        ""
                                + "Autofix for AndroidManifest.xml line 5: Set screenOrientation=\"fullSensor\":\n"
                                + "@@ -8 +8\n"
                                + "-             android:screenOrientation=\"portrait\" />\n"
                                + "+             android:screenOrientation=\"fullSensor\" />");
    }

    public void testProvisionalLibrary() {
        // If a library declaring this feature in consumed by a library, the library
        // report should not flag this issue.
        ProjectDescription lib =
                project(
                        manifest(
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    xmlns:tools=\"http://schemas.android.com/tools\">\n"
                                        + "    <uses-feature\n"
                                        + "        android:name=\"android.hardware.touchscreen\" android:required=\"true\"/>\n"
                                        + "\n"
                                        + "</manifest>\n"));
        ProjectDescription main =
                project(manifest().minSdk(15)).type(ProjectDescription.Type.LIBRARY).dependsOn(lib);

        lint().projects(lib, main).issues(UNSUPPORTED_CHROME_OS_HARDWARE).run().expectClean();
    }

    public void testProvisionalApp() {
        // If a library declaring this feature in consumed by an app, the library
        // report should be reported.
        ProjectDescription lib =
                project(
                        manifest(
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    xmlns:tools=\"http://schemas.android.com/tools\">\n"
                                        + "    <uses-feature\n"
                                        + "        android:name=\"android.hardware.touchscreen\" android:required=\"true\"/>\n"
                                        + "\n"
                                        + "</manifest>\n"));
        ProjectDescription main =
                project(manifest().minSdk(15)).type(ProjectDescription.Type.APP).dependsOn(lib);

        lint().projects(lib, main)
                .issues(UNSUPPORTED_CHROME_OS_HARDWARE)
                .run()
                .expect(
                        ""
                                + "../lib/AndroidManifest.xml:5: Error: Expecting android:required=\"false\" for this hardware feature that may not be supported by all Chrome OS devices [UnsupportedChromeOsHardware]\n"
                                + "        android:name=\"android.hardware.touchscreen\" android:required=\"true\"/>\n"
                                + "                                                    ~~~~~~~~~~~~~~~~~~~~~~~\n"
                                + "1 errors, 0 warnings\n");
    }
}
