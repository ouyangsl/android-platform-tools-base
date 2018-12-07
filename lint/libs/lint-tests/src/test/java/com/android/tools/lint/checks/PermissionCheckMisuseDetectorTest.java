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

public class PermissionCheckMisuseDetectorTest extends LintDetectorTest {

    public void testCheckPermissionWithBinderCalls() {
        lint().files(
                java("package com.example.test;\n"+
                        "import android.app.Service;\n"+
                        "import android.content.Intent;\n"+
                        "import android.os.Binder;\n"+
                        "import android.util.Log;\n"+
                        "import android.app.IntentService;\n"+
                        "import android.content.pm.PackageManager;\n"+
                        "\n"+
                        "public class MyService extends IntentService {\n"+
                        "    public MyService() {\n"+
                        "		super(\"MyService\");\n"+
                        "    }\n"+
                        "\n"+
                        "    @Override\n"+
                        "    protected void onHandleIntent(Intent intent){\n"+
                        "		if(checkPermission(\"santos.benign.permission\",Binder.getCallingPid(),Binder.getCallingUid())==PackageManager.PERMISSION_GRANTED)\n"+
                        "			Log.d(\"MyService\",\"Permission granted\");\n"+
                        "		else\n"+
                        "			Log.d(\"MyService\",\"Permission denied\");\n"+
                        "    }\n"+
                        "}\n"))
                .run()
                .expectCount(1, Severity.WARNING).expectMatches(PermissionCheckMisuseDetector.MESSAGE);
    }

    public void testCheckPermissionLocaleFieldsForBinderCalls() {
        lint().files(
                java("package com.example.test;\n"+
                        "import android.app.Service;\n"+
                        "import android.content.Intent;\n"+
                        "import android.os.Binder;\n"+
                        "import android.util.Log;\n"+
                        "import android.app.IntentService;\n"+
                        "import android.content.pm.PackageManager;\n"+
                        "\n"+
                        "public class MyService extends IntentService {\n"+
                        "    public MyService() {\n"+
                        "		super(\"MyService\");\n"+
                        "    }\n"+
                        "\n"+
                        "    @Override\n"+
                        "    protected void onHandleIntent(Intent intent){\n"+
                        "       int pid = Binder.getCallingPid();\n"+
                        "       int uid = Binder.getCallingUid();\n"+
                        "		if(checkPermission(\"santos.benign.permission\",pid,uid)==PackageManager.PERMISSION_GRANTED)\n"+
                        "			Log.d(\"MyService\",\"Permission granted\");\n"+
                        "		else\n"+
                        "			Log.d(\"MyService\",\"Permission denied\");\n"+
                        "    }\n"+
                        "}\n"))
                .run()
                .expectCount(1, Severity.WARNING).expectMatches(PermissionCheckMisuseDetector.MESSAGE);
    }

    public void testCheckUriPermissionLocaleFieldsForBinderCalls() {
        lint().files(
                java("package com.example.test;\n"+
                        "import android.app.Service;\n"+
                        "import android.content.Intent;\n"+
                        "import android.os.Binder;\n"+
                        "import android.util.Log;\n"+
                        "import android.app.IntentService;\n"+
                        "import android.content.pm.PackageManager;\n"+
                        "import android.net.Uri;\n"+
                        "\n"+
                        "public class MyService extends IntentService {\n"+
                        "    public MyService() {\n"+
                        "		super(\"MyService\");\n"+
                        "    }\n"+
                        "\n"+
                        "    @Override\n"+
                        "    protected void onHandleIntent(Intent intent){\n"+
                        "       int pid = Binder.getCallingPid();\n"+
                        "       int uid = Binder.getCallingUid();\n"+
                        "		if(checkUriPermission(Uri.parse(\"content://test.app.userdetails/user/secret\"),pid,uid,0)==PackageManager.PERMISSION_GRANTED)\n"+
                        "			Log.d(\"MyService\",\"Permission granted\");\n"+
                        "		else\n"+
                        "			Log.d(\"MyService\",\"Permission denied\");\n"+
                        "    }\n"+
                        "}\n"))
                .run()
                .expectCount(1, Severity.WARNING).expectMatches(PermissionCheckMisuseDetector.MESSAGE);
    }

    public void testCheckUriPermissionWithAdditionalPermissions() {
        lint().files(
                java("package com.example.test;\n"+
                        "import android.app.Service;\n"+
                        "import android.content.Intent;\n"+
                        "import android.os.Binder;\n"+
                        "import android.util.Log;\n"+
                        "import android.app.IntentService;\n"+
                        "import android.content.pm.PackageManager;\n"+
                        "import android.net.Uri;\n"+
                        "\n"+
                        "public class MyService extends IntentService {\n"+
                        "    public MyService() {\n"+
                        "		super(\"MyService\");\n"+
                        "    }\n"+
                        "\n"+
                        "    @Override\n"+
                        "    protected void onHandleIntent(Intent intent){\n"+
                        "       int pid = Binder.getCallingPid();\n"+
                        "       int uid = Binder.getCallingUid();\n"+
                        "		if(checkUriPermission(Uri.parse(\"content://test.app.userdetails/user/secret\"),\"santos.benign.readpermission\",\"santos.benign.writepermission\",pid,uid,0)==PackageManager.PERMISSION_GRANTED)\n"+
                        "			Log.d(\"MyService\",\"Permission granted\");\n"+
                        "		else\n"+
                        "			Log.d(\"MyService\",\"Permission denied\");\n"+
                        "    }\n"+
                        "}\n"))
                .run()
                .expectCount(1, Severity.WARNING).expectMatches(PermissionCheckMisuseDetector.MESSAGE);
    }


    @Override
    protected Detector getDetector() {
        return new PermissionCheckMisuseDetector();
    }

    @Override
    protected List<Issue> getIssues() {
        return Collections.singletonList(PermissionCheckMisuseDetector.ISSUE);
    }
}
