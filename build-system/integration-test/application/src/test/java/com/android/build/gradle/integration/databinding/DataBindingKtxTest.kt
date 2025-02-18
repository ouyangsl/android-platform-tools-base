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

package com.android.build.gradle.integration.databinding

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.model.SyncIssue
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertNull

@RunWith(Parameterized::class)
class DataBindingKtxTest(
    private val useKotlin: Boolean,
    private val useAndroidX: Boolean
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "useKotlin={0}, useAndroidX={1}")
        fun modes() = listOf(
            arrayOf(true, true),
            arrayOf(true, false),
            arrayOf(false, true),
            arrayOf(false, false)
        )
    }

    private val app = if (useKotlin) {
        KotlinHelloWorldApp.forPlugin("com.android.application")
    } else {
        HelloWorldApp.forPlugin("com.android.application")
    }.apply {
        replaceFile(
            getFile("build.gradle").appendContent(
                """
            android {
                buildFeatures {
                    dataBinding = true
                }
                dataBinding {
                    addKtx = true
                }
            }
            """.trimIndent()
            )
        )
    }

    @get:Rule
    val project =
        GradleTestProject
            .builder()
            .fromTestApp(app)
            .create()

    @Test
    fun `Databinding KTX gives a warning if the project doesn't use Kotlin or AndroidX`() {
        TestFileUtils.appendToFile(
            project.gradlePropertiesFile,
            "android.useAndroidX=${useAndroidX}"
        )

        val model = project.modelV2().ignoreSyncIssues().fetchModels()
        val issueModel = model.container.getProject(":").issues ?: throw RuntimeException("Failed to get issues from model")
        val issue = issueModel.syncIssues.find { it.message.contains("addKtx")}

        if (useKotlin && useAndroidX) {
            assertNull(issue)
        } else {
            assertThat(issue!!.severity).isEqualTo(SyncIssue.SEVERITY_WARNING)
            assertThat(issue.message).isEqualTo(ERROR_MESSAGE)
        }
    }
}

private const val ERROR_MESSAGE = "The `android.dataBinding.addKtx` DSL option has no effect " +
        "because the `android.useAndroidX` property is not enabled or the project " +
        "does not use Kotlin."
