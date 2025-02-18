/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.integration.resources

import com.android.build.gradle.integration.common.fixture.ANDROIDX_APPCOMPAT_APPCOMPAT_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/**
 * Integration test covering namespaced R class handling.
 */
class NamespacedApplicationLightRClassesTest {
    private val lib = MinimalSubProject.lib("com.example.lib")
        .appendToBuild(
            """
                android.aaptOptions.namespaced = true
                """
        )
        .withFile(
            "src/main/res/values/strings.xml",
            """<resources>
                        <string name="lib_string">lib string</string>
                    </resources>""")
        .withFile(
            "src/main/res/values/public.xml",
            """
            <resources>
                <public type="string" name="lib_string"/>
            </resources>
            """)
        .withFile(
            "src/main/java/com/example/lib/Example.java",
            """package com.example.lib;
                    public class Example {
                        public static int LOCAL_RES = R.string.lib_string;
                        public static int DEP_RES = androidx.appcompat.R.attr.actionBarDivider;
                    }
                    """)

    private val app = MinimalSubProject.app("com.example.app")
        .appendToBuild("android.aaptOptions.namespaced = true")
        .withFile(
            "src/main/res/values/strings.xml",
            """<resources>
                        <string name="app_string">app string</string>
                    </resources>""")
        .withFile(
            "src/main/java/com/example/app/Example.java",
            """package com.example.app;
                    public class Example {
                        public static int LOCAL_RES = R.string.app_string;
                        public static int LIB_RES = com.example.lib.R.string.lib_string;
                        public static int DEP_RES = androidx.appcompat.R.attr.actionBarDivider;
                    }
                    """)

    private val testApp =
        MultiModuleTestProject.builder()
            .subproject(":lib", lib)
            .subproject(":app", app)
            .dependency(app, lib)
            .dependency(app, "androidx.appcompat:appcompat:$ANDROIDX_APPCOMPAT_APPCOMPAT_VERSION")
            .dependency(lib, "androidx.appcompat:appcompat:$ANDROIDX_APPCOMPAT_APPCOMPAT_VERSION")
            .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp)
        .addGradleProperties("${BooleanOption.USE_ANDROID_X.propertyName}=true")
        // Enforcing unique package names to prevent regressions. Remove when b/116109681 fixed.
        .addGradleProperties("${BooleanOption.ENFORCE_UNIQUE_PACKAGE_NAMES.propertyName}=true")
        .create()

    @Test
    fun testResourcesCompiled() {
        project.execute(":app:assembleDebug")

        // Check library resources
        val libFiles = project.getSubproject("lib")
        assertThat(
            libFiles.getIntermediateFile(
                "compile_r_class_jar",
                "debug",
                "generateDebugRFile",
                "R.jar")).exists()

        assertThat(
            libFiles.getIntermediateFile(
                "partial_r_files",
                "debug",
                "compileMainResourcesForDebug",
                "values_strings.arsc.flat-R.txt")).exists()

        assertThat(
            libFiles.getIntermediateFile(
                "partial_r_files",
                "debug",
                "compileMainResourcesForDebug",
                "values_strings.arsc.flat-R.txt")).contains("lib_string")

        assertThat(
            libFiles.getIntermediateFile(
                "partial_r_files",
                "debug",
                "compileMainResourcesForDebug",
                "values_public.arsc.flat-R.txt")).exists()

        assertThat(
            libFiles.getIntermediateFile(
                "partial_r_files",
                "debug",
                "compileMainResourcesForDebug",
                "values_public.arsc.flat-R.txt")).contains("lib_string")

        // Application resources
        val appFiles = project.getSubproject("app")
        assertThat(
            appFiles.getIntermediateFile(
                "compile_r_class_jar",
                "debug",
                "generateDebugRFile",
                "R.jar")).exists()

        // TODO: check that it does not exist when b/130110629 is fixed
        assertThat(
            FileUtils.join(
                appFiles.generatedDir,
                "runtime_r_class_sources",
                "debug",
                "out",
                "com",
                "example",
                "lib",
                "R.java")).exists()

        assertThat(
            FileUtils.join(
                appFiles.generatedDir,
                "runtime_r_class_sources",
                "debug",
                "out",
                "com",
                "example",
                "app",
                "R.java")).exists()

        assertThat(
            appFiles.getIntermediateFile(
                "partial_r_files",
                "debug",
                "compileMainResourcesForDebug",
                "values_strings.arsc.flat-R.txt")).exists()

        assertThat(
            appFiles.getIntermediateFile(
                "partial_r_files",
                "debug",
                "compileMainResourcesForDebug",
                "values_strings.arsc.flat-R.txt")).contains("app_string")
    }
}
