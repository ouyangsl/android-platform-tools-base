/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.options.BooleanOption
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.net.URLClassLoader

internal class FusedLibraryClassesRewriteTaskTest {

    @JvmField
    @Rule
    val project = createGradleProject {
        subProject(":androidLib1") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                defaultCompileSdk()
                namespace = "com.example.androidLib1"
            }
            addFile(
                "src/main/res/values/strings.xml",
                """<resources>
                <string name="androidlib1_str">A string from androidLib1</string>
              </resources>"""
            )
            addFile("src/main/layout/main_activity.xml", "<root></root>")
        }
        subProject(":androidLib2") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                defaultCompileSdk()
                namespace = "com.example.androidLib2"
            }
            addFile(
                "src/main/res/values/strings.xml",
                """<resources>
                <string name="androidlib2_str">A string from androidLib2</string>
              </resources>"""
            )
            addFile(
                "src/main/java/com/example/androidLib2/MyClass.java",
                // language=JAVA
                """package com.example.androidLib2;
                public class MyClass {
                    public static void methodUsingNamespacedResource() {
                        int string1 = com.example.androidLib1.R.string.androidlib1_str;
                        int string2 = com.example.androidLib2.R.string.androidlib2_str;
                    }
                }
            """.trimIndent()
            )
            dependencies {
                implementation(project(":androidLib1"))
            }
        }
        subProject(":fusedLib1") {
            plugins.add(PluginType.FUSED_LIBRARY)
            appendToBuildFile { """androidFusedLibrary.namespace="com.example.fusedLib1" """ }
            dependencies {
                include(project(":androidLib1"))
                include(project(":androidLib2"))
            }
        }
        gradleProperties {
            set(BooleanOption.FUSED_LIBRARY_SUPPORT, true)
        }
    }

    @Test
    fun rewritesUnderFusedRClass() {
        project.executor().run(":fusedLib1:rewriteClasses")
        val rewrittenClasses =
            project.getSubproject("fusedLib1")
                .getIntermediateFile(
                    FusedLibraryInternalArtifactType.CLASSES_WITH_REWRITTEN_R_CLASS_REFS.getFolderName(),
                    "single",
                    "rewriteClasses"
                )
        val fusedLibraryRjar =
            project.getSubproject("fusedLib1")
                .getIntermediateFile(
                    FusedLibraryInternalArtifactType.FUSED_R_CLASS.getFolderName(),
                    "single",
                    "rewriteClasses",
                    "R.jar"
                )

        URLClassLoader(
            arrayOf(rewrittenClasses.toURI().toURL(), fusedLibraryRjar.toURI().toURL()),
            null
        ).use { classLoader ->
            // Check fused library R class generated and contains fields.
            val fusedLibraryRStringsClass =
                classLoader.loadClass("com.example.fusedLib1.R\$string")
            val fusedLibraryRClassStringFieldNames =
                (fusedLibraryRStringsClass.declaredFields).map { it.name }
                    // Ignore Jacoco instrumentation injected by studio-coverage.
                    .minus("\$jacocoData")
            assertThat(fusedLibraryRClassStringFieldNames)
                .containsExactly("androidlib1_str", "androidlib2_str")

            // Check that R class references use the fused library R Class
            try {
                val myClass = classLoader.loadClass("com.example.androidLib2.MyClass")
                val method = myClass.getMethod("methodUsingNamespacedResource")
                method.invoke(null)
            } catch (e: Exception) {
                throw AssertionError(
                    "Failed to resolve fused library R class reference", e
                )
            }
        }
    }
}
