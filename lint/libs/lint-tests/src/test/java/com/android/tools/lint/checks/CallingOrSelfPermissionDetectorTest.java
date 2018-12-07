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

import com.android.tools.lint.checks.infrastructure.LintDetectorTest;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import java.util.Collections;
import java.util.List;

public class CallingOrSelfPermissionDetectorTest extends LintDetectorTest {

    public void testContextCheckSelfOrCallingPermission() {
        lint().files(
                        java(
                                "package test.pkg;\n"
                                        + "\n"
                                        + "import android.content.pm.PackageManager;\n"
                                        + "import android.os.Bundle;\n"
                                        + "import android.content.Context;\n"
                                        + "import android.app.Activity;\n"
                                        + "\n"
                                        + "public class MainActivity extends Activity {\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    protected void onCreate(Bundle savedInstanceState) {\n"
                                        + "        super.onCreate(savedInstanceState);\n"
                                        + "        int permissionCheck = checkCallingOrSelfPermission(\"test.pkg.SOME_PERMISSION\");\n"
                                        + "        if(permissionCheck == PackageManager.PERMISSION_GRANTED){\n"
                                        + "            //do something\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expectCount(1, Severity.WARNING)
                .expectMatches(CallingOrSelfPermissionCheckDetector.MESSAGE);
    }

    public void testContextCheckCallingPermission() {
        lint().files(
                        java(
                                "package test.pkg;\n"
                                        + "\n"
                                        + "import android.content.pm.PackageManager;\n"
                                        + "import android.os.Bundle;\n"
                                        + "import android.content.Context;\n"
                                        + "import android.app.Activity;\n"
                                        + "\n"
                                        + "public class MainActivity extends Activity {\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    protected void onCreate(Bundle savedInstanceState) {\n"
                                        + "        super.onCreate(savedInstanceState);\n"
                                        + "        int permissionCheck = checkCallingPermission(\"test.pkg.SOME_PERMISSION\");\n"
                                        + "        if(permissionCheck == PackageManager.PERMISSION_GRANTED){\n"
                                        + "            //do something\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expectCount(0);
    }

    public void testContextEnforceSelfOrCallingPermission() {
        lint().files(
                        java(
                                "package test.pkg;\n"
                                        + "\n"
                                        + "import android.os.Bundle;\n"
                                        + "import android.content.Context;\n"
                                        + "import android.app.Activity;\n"
                                        + "\n"
                                        + "public class MainActivity extends Activity {\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    protected void onCreate(Bundle savedInstanceState) {\n"
                                        + "        super.onCreate(savedInstanceState);\n"
                                        + "        enforceCallingOrSelfPermission(\"test.pkg.SOME_PERMISSION\", \"not granted\");\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expectCount(1, Severity.WARNING)
                .expectMatches(CallingOrSelfPermissionCheckDetector.MESSAGE);
    }

    public void testContextEnforceCallingPermission() {
        lint().files(
                        java(
                                "package test.pkg;\n"
                                        + "\n"
                                        + "import android.os.Bundle;\n"
                                        + "import android.content.Context;\n"
                                        + "import android.app.Activity;\n"
                                        + "\n"
                                        + "public class MainActivity extends Activity {\n"
                                        + "\n"
                                        + "    @Override\n"
                                        + "    protected void onCreate(Bundle savedInstanceState) {\n"
                                        + "        super.onCreate(savedInstanceState);\n"
                                        + "        enforceCallingPermission(\"test.pkg.SOME_PERMISSION\", \"not granted\");\n"
                                        + "    }\n"
                                        + "}\n"))
                .run()
                .expectCount(0);
    }

    @Override
    protected Detector getDetector() {
        return new CallingOrSelfPermissionCheckDetector();
    }

    @Override
    protected List<Issue> getIssues() {
        return Collections.singletonList(CallingOrSelfPermissionCheckDetector.ISSUE);
    }
}
