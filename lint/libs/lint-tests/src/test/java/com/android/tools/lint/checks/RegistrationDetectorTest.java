/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static com.android.tools.lint.checks.infrastructure.ProjectDescription.Type.LIBRARY;

import com.android.tools.lint.checks.infrastructure.ProjectDescription;
import com.android.tools.lint.checks.infrastructure.TestFile;
import com.android.tools.lint.detector.api.Detector;

@SuppressWarnings({"javadoc", "ClassNameDiffersFromFileName", "MethodMayBeStatic"})
public class RegistrationDetectorTest extends AbstractCheckTest {

    public void testDocumentationExample() {
        lint().files(
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.app.Activity;\n"
                                        + "\n"
                                        + "public class MyActivity1 extends Activity {\n"
                                        + "}\n"),
                        java(
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.app.Activity;\n"
                                        + "\n"
                                        + "public class MyActivity2 extends Activity {\n"
                                        + "}\n"),
                        manifest(
                                "<manifest"
                                    + " xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                    + "    package=\"test.pkg\">\n"
                                    + "    <application>\n"
                                    + "        <activity android:name=\".MyActivity1\" />\n"
                                    + "    </application>\n"
                                    + "</manifest>\n"))
                .run()
                .expect(
                        "src/test/pkg/MyActivity2.java:5: Warning: The <activity>"
                            + " test.pkg.MyActivity2 is not registered in the manifest"
                            + " [Registered]\n"
                            + "public class MyActivity2 extends Activity {\n"
                            + "             ~~~~~~~~~~~\n"
                            + "0 errors, 1 warnings");
    }

    public void testRegistered() {
        lint().files(
                        xml(
                                "AndroidManifest.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"test.pkg\">\n"
                                        + "    <application\n"
                                        + "        android:name=\".MyApplication\">\n"
                                        + "        <activity android:name=\".TestActivity\" />\n"
                                        + "        <service android:name=\".TestService\" />\n"
                                        + "        <provider android:name=\".TestProvider\" />\n"
                                        + "        <provider android:name=\".TestProvider2\" />\n"
                                        + "        <receiver android:name=\".TestReceiver\" />\n"
                                        + "    </application>\n"
                                        + "</manifest>\n"),
                        mApplication,
                        mTestActivity,
                        mTestService,
                        mTestProvider,
                        mTestProvider2,
                        mTestReceiver)
                .run()
                .expectClean();
    }

    public void testNotRegistered() {
        String expected =
                ""
                        + "src/test/pkg/MyApplication.java:5: Warning: The <application> test.pkg.MyApplication is not registered in the manifest [Registered]\n"
                        + "public class MyApplication extends Application {\n"
                        + "             ~~~~~~~~~~~~~\n"
                        + "src/test/pkg/TestActivity.java:3: Warning: The <activity> test.pkg.TestActivity is not registered in the manifest [Registered]\n"
                        + "public class TestActivity extends Activity {\n"
                        + "             ~~~~~~~~~~~~\n"
                        + "src/test/pkg/TestProvider.java:8: Warning: The <provider> test.pkg.TestProvider is not registered in the manifest [Registered]\n"
                        + "public class TestProvider extends ContentProvider {\n"
                        + "             ~~~~~~~~~~~~\n"
                        + "src/test/pkg/TestProvider2.java:3: Warning: The <provider> test.pkg.TestProvider2 is not registered in the manifest [Registered]\n"
                        + "public class TestProvider2 extends TestProvider {\n"
                        + "             ~~~~~~~~~~~~~\n"
                        + "src/test/pkg/TestService.java:7: Warning: The <service> test.pkg.TestService is not registered in the manifest [Registered]\n"
                        + "public class TestService extends Service {\n"
                        + "             ~~~~~~~~~~~\n"
                        + "0 errors, 5 warnings\n";
        ProjectDescription desc =
                project(
                        // no manifest
                        mApplication,
                        mTestActivity,
                        mTestService,
                        mTestProvider,
                        mTestProvider2,
                        mTestReceiver,
                        mSuppressedApplication);
        lint().projects(desc)
                // TODO: Make it possible to test it directly as provisional.
                // This requires me to finally clean up the separation between analysis and
                // reporting.
                //                .provisional(desc)
                .run()
                .expect(expected);
    }

    public void testNoDot() {
        lint().files(
                        xml(
                                "AndroidManifest.xml",
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                        + "    package=\"test.pkg\">\n"
                                        + "    <application>\n"
                                        + "        <activity android:name=\"TestActivity\" />\n"
                                        + "    </application>\n"
                                        + "</manifest>\n"),
                        mTestActivity)
                .run()
                .expectClean();
    }

    public void testLibraryProjects() {
        // If a library project provides additional activities, it is not an error to
        // not register all of those here
        ProjectDescription library =
                project(
                                // Library project
                                manifest().pkg("foo.library").minSdk(14),
                                projectProperties().library(true),
                                java(
                                        ""
                                                + "package test.pkg;\n"
                                                + "import android.app.Activity;\n"
                                                + "public class TestActivity extends Activity {\n"
                                                + "}\n"))
                        .type(LIBRARY)
                        .name("LibraryProject");

        //noinspection all // Sample code
        ProjectDescription main =
                project(
                        // Main project
                        manifest().pkg("foo.main").minSdk(14),
                        java(
                                ""
                                        + "package foo.main;\n"
                                        + "\n"
                                        + "public class MainCode {\n"
                                        + "}\n"));

        main.dependsOn(library);

        lint().projects(main, library).run().expectClean();
    }

    public void testSkipReceivers() {
        lint().files(
                        java(
                                "src/test/pkg/MyReceiver.java",
                                ""
                                        + "package test.pkg;\n"
                                        + "\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.content.BroadcastReceiver;\n"
                                        + "import android.content.Context;\n"
                                        + "import android.content.Intent;\n"
                                        + "\n"
                                        + "public class MyReceiver extends BroadcastReceiver {\n"
                                        + "    @Override\n"
                                        + "    public void onReceive(Context context, Intent intent) {\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    private static class MyActivity extends Activity {\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expectClean();
    }

    @Override
    protected Detector getDetector() {
        return new RegistrationDetector();
    }

    private final TestFile mTestActivity =
            java(
                    "src/test/pkg/TestActivity.java",
                    ""
                            + "package test.pkg;\n"
                            + "import android.app.Activity;\n"
                            + "public class TestActivity extends Activity {\n"
                            + "}\n");

    private final TestFile mTestService =
            java(
                    "src/test/pkg/TestService.java",
                    ""
                            + "package test.pkg;\n"
                            + "\n"
                            + "import android.app.Service;\n"
                            + "import android.content.Intent;\n"
                            + "import android.os.IBinder;\n"
                            + "\n"
                            + "public class TestService extends Service {\n"
                            + "\n"
                            + "    @Override\n"
                            + "    public IBinder onBind(Intent intent) {\n"
                            + "        return null;\n"
                            + "    }\n"
                            + "\n"
                            + "}\n");

    private final TestFile mTestProvider =
            java(
                    "src/test/pkg/TestProvider.java",
                    "package test.pkg;\n"
                            + "\n"
                            + "import android.content.ContentProvider;\n"
                            + "import android.content.ContentValues;\n"
                            + "import android.database.Cursor;\n"
                            + "import android.net.Uri;\n"
                            + "\n"
                            + "public class TestProvider extends ContentProvider {\n"
                            + "    @Override\n"
                            + "    public int delete(Uri uri, String selection, String[] selectionArgs) {\n"
                            + "        return 0;\n"
                            + "    }\n"
                            + "\n"
                            + "    @Override\n"
                            + "    public String getType(Uri uri) {\n"
                            + "        return null;\n"
                            + "    }\n"
                            + "\n"
                            + "    @Override\n"
                            + "    public Uri insert(Uri uri, ContentValues values) {\n"
                            + "        return null;\n"
                            + "    }\n"
                            + "\n"
                            + "    @Override\n"
                            + "    public boolean onCreate() {\n"
                            + "        return false;\n"
                            + "    }\n"
                            + "\n"
                            + "    @Override\n"
                            + "    public Cursor query(Uri uri, String[] projection, String selection,\n"
                            + "            String[] selectionArgs, String sortOrder) {\n"
                            + "        return null;\n"
                            + "    }\n"
                            + "\n"
                            + "    @Override\n"
                            + "    public int update(Uri uri, ContentValues values, String selection,\n"
                            + "            String[] selectionArgs) {\n"
                            + "        return 0;\n"
                            + "    }\n"
                            + "}\n");

    private final TestFile mTestProvider2 =
            java(
                    "src/test/pkg/TestProvider2.java",
                    ""
                            + "package test.pkg;\n"
                            + "\n"
                            + "public class TestProvider2 extends TestProvider {\n"
                            + "}\n");

    private final TestFile mTestReceiver =
            java(
                    "src/test/pkg/TestReceiver.java",
                    ""
                            + "package test.pkg;\n"
                            + "\n"
                            + "import android.content.BroadcastReceiver;\n"
                            + "import android.content.Context;\n"
                            + "import android.content.Intent;\n"
                            + "\n"
                            + "public class TestReceiver extends BroadcastReceiver {\n"
                            + "\n"
                            + "    @Override\n"
                            + "    public void onReceive(Context context, Intent intent) {\n"
                            + "    }\n"
                            + "\n"
                            + "    // Anonymous classes should NOT be counted as a must-register\n"
                            + "    private BroadcastReceiver sample() {\n"
                            + "        return new BroadcastReceiver() {\n"
                            + "            @Override\n"
                            + "            public void onReceive(Context context, Intent intent) {\n"
                            + "            }\n"
                            + "        };\n"
                            + "    }\n"
                            + "}\n");

    private final TestFile mApplication =
            java(
                    "src/test/pkg/MyApplication.java",
                    ""
                            + "package test.pkg;\n"
                            + "\n"
                            + "import android.app.Application;\n"
                            + "\n"
                            + "public class MyApplication extends Application {\n"
                            + "}\n");

    private final TestFile mSuppressedApplication =
            java(
                    "src/test/pkg/MySuppressedApplication.java",
                    ""
                            + "package test.pkg;\n"
                            + "\n"
                            + "import android.app.Application;\n"
                            + "import android.annotation.SuppressLint;\n"
                            + "\n"
                            + "@SuppressLint(\"Registered\")\n"
                            + "public class MySuppressedApplication extends Application {\n"
                            + "}\n");
}
