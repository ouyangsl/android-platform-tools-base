/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.model.v2.ide.SyncIssue
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Sanity tests for the new namespaced resource pipeline with publication and consumption of an aar.
 *
 * Project structured such that app an lib depend on an aar (flatdir) from publishedLib
 * </pre>
 */
class NamespacedAarTest {

    private val buildScriptContent = """
        android.aaptOptions.namespaced = true
    """

    private val publishedLib = MinimalSubProject.lib("com.example.publishedLib")
            .appendToBuild(buildScriptContent)
            .withFile(
                    "src/main/res/values/strings.xml",
                    """<resources>
                        <string name="foo">publishedLib</string>
                        <string name="my_version_name">1.0</string>
                    </resources>""".trimMargin())
            .withFile(
                    "src/main/java/com/example/publishedLib/Example.java",
                    """package com.example.publishedLib;
                    public class Example {
                        public static int CONSTANT = 4;
                        public static int getFooString() { return R.string.foo; }
                    }""")
            .withFile(
                    "src/main/AndroidManifest.xml",
                    """
                                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                                    android:versionName="@com.example.publishedLib:string/my_version_name">
                                </manifest>""")

    private val lib = MinimalSubProject.lib("com.example.lib")
            .appendToBuild(
                    """$buildScriptContent
                    dependencies { implementation name: 'publishedLib-release', ext:'aar' }""")
            .withFile(
                    "src/main/res/values/strings.xml",
                    """<resources>
                        <string name="from_published_lib">@*com.example.publishedLib:string/foo</string>
                    </resources>""")
            .withFile(
                    "src/main/java/com/example/lib2/Example.java",
                    """package com.example.lib2;
                    public class Example {
                        public static int PUBLISHED_LIB_CONSTANT = com.example.publishedLib.Example.CONSTANT;
                        public static int FROM_PUBLISHED_LIB =
                                com.example.publishedLib.R.string.foo;
                    }
                    """)

    private val app = MinimalSubProject.app("com.example.app")
            .appendToBuild(
                    """$buildScriptContent
                    dependencies { implementation name: 'publishedLib-release', ext:'aar' }""")
            .withFile(
                    "src/main/res/values/strings.xml",
                    """<resources>
                        <string name="mystring">My String</string>
                    </resources>""")
            .withFile(
                    "src/main/java/com/example/app/Example.java",
                    """package com.example.app;
                    public class Example {
                        public static int PUBLISHED_LIB_CONSTANT = com.example.publishedLib.Example.CONSTANT;
                        public static int FROM_PUBLISHED_LIB =
                                com.example.publishedLib.R.string.foo;
                        public static final int APP_STRING = R.string.mystring;
                        public static final int PUBLISHED_LIB_STRING =
                                com.example.publishedLib.R.string.foo;
                    }
                    """)

    private val testApp =
            MultiModuleTestProject.builder()
                    .subproject(":publishedLib", publishedLib)
                    .subproject(":lib", lib)
                    .subproject(":app", app)
                    .build()

    @get:Rule val project = GradleTestProject.builder().fromTestApp(testApp).create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
                project.settingsFile,
                """
                    dependencyResolutionManagement {
                        repositories {
                            flatDir { dirs 'publishedLib/build/outputs/aar/' }
                        }
                    }
                """.trimIndent()
        )
    }

    @Test
    fun checkBuilds() {
        project.executor()
            .run(":publishedLib:assembleRelease")

        project.executor()
            .run(":lib:assembleDebug", ":app:assembleDebug")

        val variantDeps = project.modelV2().ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchVariantDependencies("debug")
            .container.getProject(":lib")
            .variantDependencies!!

        val compileDependencies = variantDeps.mainArtifact.compileDependencies
        assertThat(compileDependencies).hasSize(1)

        val publishedLibData = variantDeps.libraries[compileDependencies.first().key]?.androidLibraryData
        assertThat(publishedLibData?.resStaticLibrary).exists()

        val subproject = project.getSubproject("publishedLib")
        subproject.withAar("release") {
            assertThat(entries.map { it.toString() })
                .containsExactly(
                    "/META-INF/com/android/build/gradle/aar-metadata.properties",
                    "/res/values/values.xml",
                    "/classes.jar",
                    "/res.apk",
                    "/AndroidManifest.xml",
                    "/R.txt"
                )
            // Check that the AndroidManifest.xml in the AAR does not contain namespaces.
            val manifest = androidManifestContentsAsString
            assertThat(manifest).contains("@string/my_version_name")
            assertThat(manifest).doesNotContain("@com.example.publishedLib:string/my_version_name")
        }

        subproject.assertThatAar("release") {
            containsFileWithContent(
                "R.txt",
                """
                    int string foo 0x0
                    int string my_version_name 0x0
                """.trimIndent()
            )
            containsFileWithContent(
            "res/values/values.xml",
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <resources>

                        <string name="foo">publishedLib</string>
                        <string name="my_version_name">1.0</string>

                    </resources>
                    """.trimIndent()
            )
        }
    }
}
