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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.ZipFileSubject.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Sanity tests for the new compile R class flow pipeline with non-transitive R classes.
 */
class NonTransitiveCompileRClassFlowTest {

    private val lib1 = MinimalSubProject.lib("com.example.lib1")
        .withFile(
            "src/main/res/values/values.xml",
            """<resources>
                   <string name="lib1String">Lib1 string</string>
                   <attr name="lib1attr" format="reference"/>
               </resources>""".trimMargin()
        )
        .withFile(
            "src/main/java/com/example/lib1/Example.java",
            """package com.example.lib1;
                    public class Example {
                        public static final int LIB1_STRING = R.string.lib1String;
                        public static final int SUPPORT_LIB_STRING = com.google.android.material.R.string.appbar_scrolling_view_behavior;
                    }"""
        )

    private val lib2 = MinimalSubProject.lib("com.example.lib2")
        .withFile(
            "src/main/res/values/values.xml",
            """<resources>
                        <string name="lib2String">Lib2 string</string>
                        <string name="reference">@string/lib1String</string>
                        <color name="lib2color"/>
                    </resources>"""
        )
        .withFile(
            "src/main/java/com/example/lib2/Example.java",
            """package com.example.lib2;
                    public class Example {
                        public static final int LIB2_STRING = R.string.lib2String;
                        public static final int LIB1_STRING = com.example.lib1.R.string.lib1String;
                        public static final int SUPPORT_LIB_STRING = com.google.android.material.R.string.appbar_scrolling_view_behavior;
                    }"""
        )

    /** Included to make sure that the ids on an app compilation R class are constant expressions */
    private val app = MinimalSubProject.app("com.example.app")
        .withFile(
            "src/main/java/com/example/app/Example.java",
            """package com.example.app;
                    public class Example {
                        public void checkLibRFilesConstant() {
                            int x = com.example.lib2.R.string.lib2String;
                            switch(x) {
                                // These must be constant expressions.
                                case com.example.lib1.R.string.lib1String:
                                case com.example.lib2.R.string.lib2String:
                            }
                        }
                    }
                    """
        )

    private val testApp =
        MultiModuleTestProject.builder()
            .subproject(":lib1", lib1)
            .subproject(":lib2", lib2)
            .subproject(":app", app)
            .dependency(lib1, "com.google.android.material:material:1.9.0")
            .dependency(lib2, lib1)
            .dependency(app, lib2)
            .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp)
        .addGradleProperties("${BooleanOption.USE_ANDROID_X.propertyName}=true")
        // Enforcing unique package names to prevent regressions. Remove when b/116109681 fixed.
        .addGradleProperties("${BooleanOption.ENFORCE_UNIQUE_PACKAGE_NAMES.propertyName}=true")
        // consider using default heap size when b/339837484 is resolved
        .withHeap("2048m")
        .create()

    @Test
    fun runtimeRClassFlowTestWithNonTransitive() {

        val tasks = listOf(
            ":app:assembleDebug",
            ":app:assembleDebugAndroidTest",
            ":lib1:assembleDebugAndroidTest",
            ":lib2:assembleDebug",
            ":lib2:assembleDebugAndroidTest"
        )

        // Need to change the dependency on the support lib implementation to api.
        TestFileUtils.searchAndReplace(
            project.file("lib1/build.gradle"),
            "implementation '",
            "api '"
        )

        val lib2RJar = project.getSubproject("lib2")
            .getIntermediateFile("compile_r_class_jar", "debug", "generateDebugRFile", "R.jar")

        project.executor()
            .with(BooleanOption.NON_TRANSITIVE_R_CLASS, true)
            .with(BooleanOption.USE_NON_FINAL_RES_IDS, false)
            .run(tasks)
        assertThat(lib2RJar) {
            // It shouldn't contain any other R classes other than the local one
            it.doesNotContain("com/example/lib1/R.class")
            it.contains("com/example/lib2/R.class")
            // The R class should only have local resources
            it.contains("com/example/lib2/R\$color.class")
            it.doesNotContain("com/example/lib2/R\$attr.class")
        }

        project.executor()
            .with(BooleanOption.NON_TRANSITIVE_R_CLASS, false)
            .with(BooleanOption.USE_NON_FINAL_RES_IDS, false)
            .run(tasks)
        assertThat(lib2RJar) {
            // It shouldn't contain any other R classes other than the local one
            it.doesNotContain("com/example/lib1/R.class")
            it.contains("com/example/lib2/R.class")
            // The R class should contain all resources
            it.contains("com/example/lib2/R\$color.class")
            it.contains("com/example/lib2/R\$attr.class")
        }
    }
}

