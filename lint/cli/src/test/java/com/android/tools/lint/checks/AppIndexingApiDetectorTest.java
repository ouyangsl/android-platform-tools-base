<<<<<<< HEAD   (cdfb32 Merge "Fixing NPE when getting extra package display name" i)
=======
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

package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Detector;

@SuppressWarnings("javadoc")
public class AppIndexingApiDetectorTest extends AbstractCheckTest {

    @Override
    protected Detector getDetector() {
        return new AppIndexingApiDetector();
    }

    public void testOk() throws Exception {
        assertEquals("No warnings.",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/AppTheme\" >\n"
                        + "        <activity\n"
                        + "            android:name=\".FullscreenActivity\"\n"
                        + "            android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                        + "            android:label=\"@string/title_activity_fullscreen\"\n"
                        + "            android:theme=\"@style/FullscreenTheme\" >\n"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                        + "                <!-- Accepts URIs that begin with \"http://example.com/gizmos” -->\n"
                        + "                <data android:scheme=\"http\"\n"
                        + "                    android:host=\"example.com\"\n"
                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "        <meta-data android:name=\"com.google.android.gms.version\" android:value=\"@integer/google_play_services_version\" />"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }

    public void testDataMissing() throws Exception {
        assertEquals(""
                        + "AndroidManifest.xml:15: Error: Missing data node? [AppIndexingError]\n"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "            ^\n"
                        + "1 errors, 0 warnings\n",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/AppTheme\" >\n"
                        + "        <activity\n"
                        + "            android:name=\".FullscreenActivity\"\n"
                        + "            android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                        + "            android:label=\"@string/title_activity_fullscreen\"\n"
                        + "            android:theme=\"@style/FullscreenTheme\" >\n"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "        <meta-data android:name=\"com.google.android.gms.version\" android:value=\"@integer/google_play_services_version\" />"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }

    public void testNoUrl() throws Exception {
        assertEquals(""
                        + "AndroidManifest.xml:18: Error: Missing URL for the intent filter? [AppIndexingError]\n"
                        + "                <data />\n"
                        + "                ~~~~~~~~\n"
                        + "1 errors, 0 warnings\n",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/AppTheme\" >\n"
                        + "        <activity\n"
                        + "            android:name=\".FullscreenActivity\"\n"
                        + "            android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                        + "            android:label=\"@string/title_activity_fullscreen\"\n"
                        + "            android:theme=\"@style/FullscreenTheme\" >\n"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                        + "                <!-- Accepts URIs that begin with \"http://example.com/gizmos” -->\n"
                        + "                <data />\n"
                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "        <meta-data android:name=\"com.google.android.gms.version\" android:value=\"@integer/google_play_services_version\" />"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }

    public void testMimeType() throws Exception {
        assertEquals("No warnings.",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/AppTheme\" >\n"
                        + "        <activity\n"
                        + "            android:name=\".FullscreenActivity\"\n"
                        + "            android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                        + "            android:label=\"@string/title_activity_fullscreen\"\n"
                        + "            android:theme=\"@style/FullscreenTheme\" >\n"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                        + "                <!-- Accepts URIs that begin with \"http://example.com/gizmos” -->\n"
                        + "                <data android:mimeType=\"mimetype\" /> "
                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "        <meta-data android:name=\"com.google.android.gms.version\" android:value=\"@integer/google_play_services_version\" />"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }


    public void testNoActivity() throws Exception {
        assertEquals(
                "No warnings.",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/AppTheme\" >\n"
                        + "        <meta-data android:name=\"com.google.android.gms.version\" android:value=\"@integer/google_play_services_version\" />"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }

    public void testNotBrowsable() throws Exception {
        assertEquals(""
                        + "AndroidManifest.xml:25: Warning: Activity supporting ACTION_VIEW is not set as BROWSABLE [AppIndexingWarning]\n"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "            ^\n"
                        + "0 errors, 1 warnings\n",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/AppTheme\" >\n"
                        + "        <activity\n"
                        + "            android:name=\".MainActivity\"\n"
                        + "            android:label=\"@string/app_name\" >\n"
                        + "            <intent-filter>\n"
                        + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                        + "\n"
                        + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "\n"
                        + "        <activity\n"
                        + "            android:name=\".FullscreenActivity\"\n"
                        + "            android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                        + "            android:label=\"@string/title_activity_fullscreen\"\n"
                        + "            android:theme=\"@style/FullscreenTheme\" >\n"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                        + "                <!-- Accepts URIs that begin with \"http://example.com/gizmos” -->\n"
                        + "                <data android:scheme=\"http\"\n"
                        + "                    android:host=\"example.com\"\n"
                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "        <meta-data android:name=\"com.google.android.gms.version\" android:value=\"@integer/google_play_services_version\" />"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }

    public void testWrongPathPrefix() throws Exception {
        assertEquals(""
                        + "AndroidManifest.xml:20: Error: android:pathPrefix attribute should start with / [AppIndexingError]\n"
                        + "                    android:pathPrefix=\"gizmos\" />\n"
                        + "                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/AppTheme\" >\n"
                        + "        <activity\n"
                        + "            android:name=\".FullscreenActivity\"\n"
                        + "            android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                        + "            android:label=\"@string/title_activity_fullscreen\"\n"
                        + "            android:theme=\"@style/FullscreenTheme\" >\n"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                        + "                <!-- Accepts URIs that begin with \"http://example.com/gizmos” -->\n"
                        + "                <data android:scheme=\"http\"\n"
                        + "                    android:host=\"example.com\"\n"
                        + "                    android:pathPrefix=\"gizmos\" />\n"
                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "        <meta-data android:name=\"com.google.android.gms.version\" android:value=\"@integer/google_play_services_version\" />"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }

    public void testWrongPort() throws Exception {
        assertEquals(""
                        + "AndroidManifest.xml:20: Error: android:port is not a legal number [AppIndexingError]\n"
                        + "                    android:port=\"ABCD\"\n"
                        + "                    ~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/AppTheme\" >\n"
                        + "        <activity\n"
                        + "            android:name=\".FullscreenActivity\"\n"
                        + "            android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                        + "            android:label=\"@string/title_activity_fullscreen\"\n"
                        + "            android:theme=\"@style/FullscreenTheme\" >\n"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                        + "                <!-- Accepts URIs that begin with \"http://example.com/gizmos” -->\n"
                        + "                <data android:scheme=\"http\"\n"
                        + "                    android:host=\"example.com\"\n"
                        + "                    android:port=\"ABCD\"\n"
                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "        <meta-data android:name=\"com.google.android.gms.version\" android:value=\"@integer/google_play_services_version\" />"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }

    public void testSchemeAndHostMissing() throws Exception {
        assertEquals(""
                        + "AndroidManifest.xml:18: Error: Missing URL for the intent filter? [AppIndexingError]\n"
                        + "                <data android:pathPrefix=\"/gizmos\" />\n"
                        + "                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:18: Error: android:host missing [AppIndexingError]\n"
                        + "                <data android:pathPrefix=\"/gizmos\" />\n"
                        + "                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "AndroidManifest.xml:18: Error: android:scheme missing [AppIndexingError]\n"
                        + "                <data android:pathPrefix=\"/gizmos\" />\n"
                        + "                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "3 errors, 0 warnings\n",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/AppTheme\" >\n"
                        + "        <activity\n"
                        + "            android:name=\".FullscreenActivity\"\n"
                        + "            android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                        + "            android:label=\"@string/title_activity_fullscreen\"\n"
                        + "            android:theme=\"@style/FullscreenTheme\" >\n"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                        + "                <!-- Accepts URIs that begin with \"http://example.com/gizmos” -->\n"
                        + "                <data android:pathPrefix=\"/gizmos\" />\n"
                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "        <meta-data android:name=\"com.google.android.gms.version\" android:value=\"@integer/google_play_services_version\" />"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }

    public void testMultiData() throws Exception {
        assertEquals("No warnings.",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/AppTheme\" >\n"
                        + "        <activity\n"
                        + "            android:name=\".FullscreenActivity\"\n"
                        + "            android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                        + "            android:label=\"@string/title_activity_fullscreen\"\n"
                        + "            android:theme=\"@style/FullscreenTheme\" >\n"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                        + "                <!-- Accepts URIs that begin with \"http://example.com/gizmos” -->\n"
                        + "                <data android:scheme=\"http\" />\n"
                        + "                <data android:host=\"example.com\" />\n"
                        + "                <data android:pathPrefix=\"/gizmos\" />\n"
                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "        <meta-data android:name=\"com.google.android.gms.version\" android:value=\"@integer/google_play_services_version\" />"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }

    public void testMultiIntent() throws Exception {
        assertEquals("No warnings.",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/AppTheme\" >\n"
                        + "        <activity\n"
                        + "            android:name=\".FullscreenActivity\"\n"
                        + "            android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                        + "            android:label=\"@string/title_activity_fullscreen\"\n"
                        + "            android:theme=\"@style/FullscreenTheme\" >\n"
                        + "            <intent-filter>\n"
                        + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                        + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                        + "            </intent-filter>"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                        + "                <!-- Accepts URIs that begin with \"http://example.com/gizmos” -->\n"
                        + "                <data android:scheme=\"http\"\n"
                        + "                    android:host=\"example.com\"\n"
                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "        <meta-data android:name=\"com.google.android.gms.version\" android:value=\"@integer/google_play_services_version\" />"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }

    public void testMultiIntentWithError() throws Exception {
        assertEquals(""
                        + "AndroidManifest.xml:21: Error: android:host missing [AppIndexingError]\n"
                        + "                <data android:scheme=\"http\"\n"
                        + "                ^\n"
                        + "1 errors, 0 warnings\n",
                lintProject(xml("AndroidManifest.xml", ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.helloworld\" >\n"
                        + "\n"
                        + "    <application\n"
                        + "        android:allowBackup=\"true\"\n"
                        + "        android:icon=\"@mipmap/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\"\n"
                        + "        android:theme=\"@style/AppTheme\" >\n"
                        + "        <activity\n"
                        + "            android:name=\".FullscreenActivity\"\n"
                        + "            android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                        + "            android:label=\"@string/title_activity_fullscreen\"\n"
                        + "            android:theme=\"@style/FullscreenTheme\" >\n"
                        + "            <intent-filter>\n"
                        + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                        + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                        + "            </intent-filter>"
                        + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                        + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                        + "                <!-- Accepts URIs that begin with \"http://example.com/gizmos” -->\n"
                        + "                <data android:scheme=\"http\"\n"
                        + "                    android:pathPrefix=\"/gizmos\" />\n"
                        + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                        + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "        <meta-data android:name=\"com.google.android.gms.version\" android:value=\"@integer/google_play_services_version\" />"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>\n")));
    }

    public void testNoGMS() throws Exception {
        assertEquals("AndroidManifest.xml:5: Warning: GMS should be added to the app [AppIndexingWarning]\n"
                        + "    <application\n"
                        + "    ^\n"
                        + "0 errors, 1 warnings\n",
                lintProject(xml("AndroidManifest.xml", ""
                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                + "    package=\"com.example.helloworld\" >\n"
                                + "\n"
                                + "    <application\n"
                                + "        android:allowBackup=\"true\"\n"
                                + "        android:icon=\"@mipmap/ic_launcher\"\n"
                                + "        android:label=\"@string/app_name\"\n"
                                + "        android:theme=\"@style/AppTheme\" >\n"
                                + "        <activity\n"
                                + "            android:name=\".AppIndexingApiTest\"\n"
                                + "            android:configChanges=\"orientation|keyboardHidden|screenSize\"\n"
                                + "            android:label=\"@string/title_activity_fullscreen\"\n"
                                + "            android:theme=\"@style/FullscreenTheme\" >\n"
                                + "            <intent-filter android:label=\"@string/title_activity_fullscreen\">\n"
                                + "                <action android:name=\"android.intent.action.VIEW\" />\n"
                                + "                <!-- Accepts URIs that begin with \"http://example.com/gizmos” -->\n"
                                + "                <data android:scheme=\"http\"\n"
                                + "                    android:host=\"example.com\"\n"
                                + "                    android:pathPrefix=\"/gizmos\" />\n"
                                + "                <category android:name=\"android.intent.category.DEFAULT\" />\n"
                                + "                <category android:name=\"android.intent.category.BROWSABLE\" />\n"
                                + "            </intent-filter>\n"
                                + "        </activity>\n"
                                + "    </application>\n"
                                + "\n"
                                + "</manifest>\n")
                ));
    }

    public void testJavaOk() throws Exception {
        assertEquals("No warnings.",
                lintProjectIncrementally(
                        "src/com/example/helloworld/AppIndexingApiTest.java",
                        "app_indexing_api_test.xml=>AndroidManifest.xml",
                        "src/com/example/helloworld/AppIndexingApiTestOk.java.txt=>src/com/example/helloworld/AppIndexingApiTest.java"
                ));
    }

    public void testNoManifest() throws Exception {
        assertEquals("src/com/example/helloworld/AppIndexingApiTest.java:28: Error: Missing app indexing support in manifest [AppIndexingJavaError]\n"
                        + "        AppIndex.AppIndexApi.start(mClient, action);\n"
                        + "                             ~~~~~\n"
                        + "src/com/example/helloworld/AppIndexingApiTest.java:36: Error: Missing app indexing support in manifest [AppIndexingJavaError]\n"
                        + "        AppIndex.AppIndexApi.end(mClient, action);\n"
                        + "                             ~~~\n"
                        + "2 errors, 0 warnings\n",
                lintProjectIncrementally(
                        "src/com/example/helloworld/AppIndexingApiTest.java",
                        "app_indexing_api_test_no_manifest.xml=>AndroidManifest.xml",
                        "src/com/example/helloworld/AppIndexingApiTestOk.java.txt=>src/com/example/helloworld/AppIndexingApiTest.java"
                ));
    }

    public void testNoGoogleApiClient() throws Exception {
        assertEquals("src/com/example/helloworld/AppIndexingApiTest.java:11: Error: Missing GoogleApiClient [AppIndexingJavaError]\n"
                        + "public class AppIndexingApiTest extends Activity {\n"
                        + "             ~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings\n",
                lintProjectIncrementally(
                        "src/com/example/helloworld/AppIndexingApiTest.java",
                        "app_indexing_api_test.xml=>AndroidManifest.xml",
                        "src/com/example/helloworld/AppIndexingApiTestNoGoogleApiClient.java.txt=>src/com/example/helloworld/AppIndexingApiTest.java"
                ));
    }

    public void testNoStartEnd() throws Exception {
        assertEquals("src/com/example/helloworld/AppIndexingApiTest.java:11: Warning: Missing app indexing api call [AppIndexingJavaWARNING]\n"
                        + "public class AppIndexingApiTest extends Activity {\n"
                        + "             ~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n",
                lintProjectIncrementally(
                        "src/com/example/helloworld/AppIndexingApiTest.java",
                        "app_indexing_api_test.xml=>AndroidManifest.xml",
                        "src/com/example/helloworld/AppIndexingApiTestNoStartEnd.java.txt=>src/com/example/helloworld/AppIndexingApiTest.java"
                ));
    }

    public void testStartMatch() throws Exception {
        assertEquals("src/com/example/helloworld/AppIndexingApiTest.java:27: Error: GoogleApiClient mClient has not connected [AppIndexingJavaError]\n"
                        + "        AppIndex.AppIndexApi.start(mClient, action);\n"
                        + "                                   ~~~~~~~\n"
                        + "src/com/example/helloworld/AppIndexingApiTest.java:27: Error: Missing corresponding AppIndex.AppIndexApi.end method [AppIndexingJavaError]\n"
                        + "        AppIndex.AppIndexApi.start(mClient, action);\n"
                        + "                             ~~~~~\n"
                        + "2 errors, 0 warnings\n",
                lintProjectIncrementally(
                        "src/com/example/helloworld/AppIndexingApiTest.java",
                        "app_indexing_api_test.xml=>AndroidManifest.xml",
                        "src/com/example/helloworld/AppIndexingApiTestStartMatch.java.txt=>src/com/example/helloworld/AppIndexingApiTest.java"
                ));
    }

    public void testEndMatch() throws Exception {
        assertEquals("src/com/example/helloworld/AppIndexingApiTest.java:33: Error: GoogleApiClient mClient has not connected [AppIndexingJavaError]\n"
                        + "        AppIndex.AppIndexApi.end(mClient, action);\n"
                        + "                                 ~~~~~~~\n"
                        + "src/com/example/helloworld/AppIndexingApiTest.java:33: Error: Missing corresponding AppIndex.AppIndexApi.start method [AppIndexingJavaError]\n"
                        + "        AppIndex.AppIndexApi.end(mClient, action);\n"
                        + "                             ~~~\n"
                        + "2 errors, 0 warnings\n",
                lintProjectIncrementally(
                        "src/com/example/helloworld/AppIndexingApiTest.java",
                        "app_indexing_api_test.xml=>AndroidManifest.xml",
                        "src/com/example/helloworld/AppIndexingApiTestEndMatch.java.txt=>src/com/example/helloworld/AppIndexingApiTest.java"
                ));
    }

    public void testWrongOrder() throws Exception {
        assertEquals("No warnings.",
                lintProjectIncrementally(
                        "src/com/example/helloworld/AppIndexingApiTest.java",
                        "app_indexing_api_test.xml=>AndroidManifest.xml",
                        "src/com/example/helloworld/AppIndexingApiTestWrongOrder.java.txt=>src/com/example/helloworld/AppIndexingApiTest.java"
                ));
    }
}
>>>>>>> BRANCH (57afbd Add java lint check for app indexing api)
