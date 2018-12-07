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

import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;

import com.android.tools.lint.checks.infrastructure.LintDetectorTest;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import java.util.Collections;
import java.util.List;

public class CustomSchemaChannelDetectorTest extends LintDetectorTest {
    @Override
    protected Detector getDetector() {
        return new CustomSchemeChannelDetector();
    }

    @Override
    protected List<Issue> getIssues() {
        return Collections.singletonList(CustomSchemeChannelDetector.ISSUE);
    }

    public void testWithCustomSchemeChannel() {
        lint().files(
                        xml(
                                FN_ANDROID_MANIFEST_XML,
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest package=\"com.example.test.myapplication\"\n"
                                        + "          xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "    <application>\n"
                                        + "        <activity android:name=\"com.example.android.custom-lint-rules"
                                        + ".OtherActivity\">\n"
                                        + "        </activity>\n"
                                        + "\n"
                                        + "        <activity android:name=\"com.example.android.custom-lint-rules"
                                        + ".MainActivity\">\n"
                                        + "            <intent-filter>\n"
                                        + "                <action android:name=\"android.intent.action.MAIN\"/>\n"
                                        + "                <category android:name=\"android.intent.category.LAUNCHER\"/>\n"
                                        + "                <data android:scheme=\"myapp\" android:host=\"path\" />\n"
                                        + "            </intent-filter>\n"
                                        + "        </activity>\n"
                                        + "    </application>\n"
                                        + "</manifest>"))
                .run()
                .expectCount(1, Severity.WARNING)
                .expectMatches(CustomSchemeChannelDetector.MESSAGE);
    }

    public void testWithMultipleCustomSchemeChannel() {
        lint().files(
                        xml(
                                FN_ANDROID_MANIFEST_XML,
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest package=\"com.example.test.myapplication\"\n"
                                        + "          xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "    <application>\n"
                                        + "        <activity android:name=\"com.example.android.custom-lint-rules"
                                        + ".OtherActivity\">\n"
                                        + "        </activity>\n"
                                        + "\n"
                                        + "        <activity android:name=\"com.example.android.custom-lint-rules"
                                        + ".MainActivity\">\n"
                                        + "            <intent-filter>\n"
                                        + "                <action android:name=\"android.intent.action.MAIN\"/>\n"
                                        + "                <category android:name=\"android.intent.category.LAUNCHER\"/>\n"
                                        + "                <data android:scheme=\"myapp\" android:host=\"path\" />\n"
                                        + "                <data android:scheme=\"another\" android:host=\"path\" />\n"
                                        + "            </intent-filter>\n"
                                        + "        </activity>\n"
                                        + "    </application>\n"
                                        + "</manifest>"))
                .run()
                .expectCount(2, Severity.WARNING);
    }

    public void testWithKnownSchemeChannel() {
        lint().files(
                        xml(
                                FN_ANDROID_MANIFEST_XML,
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest package=\"com.example.test.myapplication\"\n"
                                        + "          xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "    <application>\n"
                                        + "        <activity android:name=\"com.example.android.custom-lint-rules"
                                        + ".OtherActivity\">\n"
                                        + "        </activity>\n"
                                        + "\n"
                                        + "        <activity android:name=\"com.example.android.custom-lint-rules"
                                        + ".MainActivity\">\n"
                                        + "            <intent-filter>\n"
                                        + "                <action android:name=\"android.intent.action.MAIN\"/>\n"
                                        + "                <category android:name=\"android.intent.category.LAUNCHER\"/>\n"
                                        + "                <data android:scheme=\"file\" android:host=\"path\" />\n"
                                        + "            </intent-filter>\n"
                                        + "        </activity>\n"
                                        + "    </application>\n"
                                        + "</manifest>"))
                .run()
                .expectCount(0);
    }

    public void testWithoutSchemeChannel() {
        lint().files(
                        xml(
                                FN_ANDROID_MANIFEST_XML,
                                ""
                                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<manifest package=\"com.example.test.myapplication\"\n"
                                        + "          xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "    <application>\n"
                                        + "        <activity android:name=\"com.example.android.custom-lint-rules"
                                        + ".OtherActivity\">\n"
                                        + "        </activity>\n"
                                        + "\n"
                                        + "        <activity android:name=\"com.example.android.custom-lint-rules"
                                        + ".MainActivity\">\n"
                                        + "            <intent-filter>\n"
                                        + "                <action android:name=\"android.intent.action.MAIN\"/>\n"
                                        + "                <category android:name=\"android.intent.category.LAUNCHER\"/>\n"
                                        + "            </intent-filter>\n"
                                        + "        </activity>\n"
                                        + "    </application>\n"
                                        + "</manifest>"))
                .run()
                .expectClean();
    }

    public void testIntentFilterInCodeWithCustomScheme() {
        lint().files(
                        java(
                                "package com.example.test;\n"
                                        + "\n"
                                        + "import android.content.BroadcastReceiver;\n"
                                        + "import android.content.Context;\n"
                                        + "import android.content.Intent;\n"
                                        + "import android.content.IntentFilter;\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.os.Bundle;\n"
                                        + "\n"
                                        + "public class MainActivity extends Activity {\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    protected void onCreate(Bundle savedInstanceState) {\n"
                                        + "        super.onCreate(savedInstanceState);\n"
                                        + "\n"
                                        + "        BroadcastReceiver br = new BroadcastReceiver() {\n"
                                        + "            @Override\n"
                                        + "            public void onReceive(Context context, Intent intent) {\n"
                                        + "                //do nothing\n"
                                        + "            }\n"
                                        + "        };\n"
                                        + "        IntentFilter filter = new IntentFilter();\n"
                                        + "        filter.addDataScheme(\"testscheme\");\n"
                                        + "        registerReceiver(br, filter);\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expectCount(1, Severity.WARNING)
                .expectMatches(CustomSchemeChannelDetector.MESSAGE);
    }

    public void testIntentFilterInCodeWithKnownScheme() {
        lint().files(
                        java(
                                "package com.example.test;\n"
                                        + "\n"
                                        + "import android.content.BroadcastReceiver;\n"
                                        + "import android.content.Context;\n"
                                        + "import android.content.Intent;\n"
                                        + "import android.content.IntentFilter;\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.os.Bundle;\n"
                                        + "\n"
                                        + "public class MainActivity extends Activity {\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    protected void onCreate(Bundle savedInstanceState) {\n"
                                        + "        super.onCreate(savedInstanceState);\n"
                                        + "\n"
                                        + "        BroadcastReceiver br = new BroadcastReceiver() {\n"
                                        + "            @Override\n"
                                        + "            public void onReceive(Context context, Intent intent) {\n"
                                        + "                //do nothing\n"
                                        + "            }\n"
                                        + "        };\n"
                                        + "        IntentFilter filter = new IntentFilter();\n"
                                        + "        filter.addDataScheme(\"content\");\n"
                                        + "        registerReceiver(br, filter);\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expectCount(0);
    }

    public void testIntentFilterInCodeWithoutScheme() {
        lint().files(
                        java(
                                "package com.example.test;\n"
                                        + "\n"
                                        + "import android.content.BroadcastReceiver;\n"
                                        + "import android.content.Context;\n"
                                        + "import android.content.Intent;\n"
                                        + "import android.content.IntentFilter;\n"
                                        + "import android.app.Activity;\n"
                                        + "import android.os.Bundle;\n"
                                        + "\n"
                                        + "public class MainActivity extends Activity {\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    protected void onCreate(Bundle savedInstanceState) {\n"
                                        + "        super.onCreate(savedInstanceState);\n"
                                        + "\n"
                                        + "        BroadcastReceiver br = new BroadcastReceiver() {\n"
                                        + "            @Override\n"
                                        + "            public void onReceive(Context context, Intent intent) {\n"
                                        + "                //do nothing\n"
                                        + "            }\n"
                                        + "        };\n"
                                        + "        IntentFilter filter = new IntentFilter();\n"
                                        + "        filter.addAction(\"com.example.test.ACTION\");\n"
                                        + "        registerReceiver(br, filter);\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expectCount(0);
    }
}
