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

package com.android.build.gradle.integration.multiplatform

import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProjectBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.build.gradle.internal.TaskManager.Companion.COMPOSE_UI_VERSION
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.TestUtils
import com.android.testutils.TestUtils.KOTLIN_VERSION_FOR_COMPOSE_TESTS
import org.junit.Rule
import org.junit.Test

/** Check Compose works with KMP projects. */
class KotlinMultiplatformComposeTest {

    @get:Rule
    val project = createGradleProjectBuilder {
        rootProject {
            plugins.add(PluginType.ANDROID_LIB)
            plugins.add(PluginType.KOTLIN_MPP)
            android {
                setUpHelloWorld()
                minSdk = 24
            }
            dependencies {
                implementation("androidx.compose.ui:ui-tooling:$COMPOSE_UI_VERSION")
                implementation("androidx.compose.material:material:$COMPOSE_UI_VERSION")
            }
            appendToBuildFile {
                """
                    kotlin {
                        android {
                           compilations.all {
                              it.compilerOptions.options.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
                           }
                        }
                    }
                    android {
                        buildFeatures {
                            compose true
                        }
                        compileOptions {
                            sourceCompatibility JavaVersion.VERSION_1_8
                            targetCompatibility JavaVersion.VERSION_1_8
                        }
                        composeOptions {
                            useLiveLiterals false
                            kotlinCompilerExtensionVersion = "${TestUtils.COMPOSE_COMPILER_FOR_TESTS}"
                        }
                    }
                """.trimIndent()
            }
            addFile(
                "src/main/kotlin/com/Example.kt", """
                package foo

                import androidx.compose.foundation.layout.Column
                import androidx.compose.material.Text
                import androidx.compose.runtime.Composable

                @Composable
                fun MainView() {
                    Column {
                        Text(text = "Hello World")
                    }
                }
            """.trimIndent()
            )
        }
    }
        .withKotlinGradlePlugin(true)
        .withKotlinVersion(KOTLIN_VERSION_FOR_COMPOSE_TESTS)
        .create()

    /** Regression test for b/203594737. */
    @Test
    fun testLibraryBuilds() {
        project.executor()
            .with(BooleanOption.USE_ANDROID_X, true)
            .run("assembleDebug")
    }
}
