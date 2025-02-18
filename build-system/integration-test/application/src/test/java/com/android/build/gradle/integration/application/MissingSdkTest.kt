/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.SdkConstants.FN_LOCAL_PROPERTIES
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.builder.model.SyncIssue
import com.google.common.base.Throwables.getRootCause
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

class MissingSdkTest {

    @Rule
    @JvmField
    val testProject =
        GradleTestProject.builder()
            .fromTestApp(HelloWorldApp.forPlugin("com.android.application")).withSdk(false)
            .create()

    @Test
    fun missingSdkSyncIssueCreated() {
        val localPropertiesPath = File(testProject.projectDir, FN_LOCAL_PROPERTIES).absolutePath
        val exception = testProject.executeExpectingFailure("assembleDebug")
        val rootCause = getRootCause(exception)
        assertThat(rootCause.message).isEqualTo(
            "SDK location not found. Define a valid SDK location with an ANDROID_HOME " +
                    "environment variable or by setting the sdk.dir path in " +
                    "your project's local properties file at '$localPropertiesPath'."
        )

        val syncIssues =
            testProject.modelV2()
                .ignoreSyncIssues()
                .fetchModels().container.getProject().issues?.syncIssues!!

        assertThat(syncIssues.size).isEqualTo(1)
        val syncIssue = syncIssues.first()
        assertThat(syncIssue.type).isEqualTo(SyncIssue.TYPE_SDK_NOT_SET)
        assertThat(syncIssue.data).isEqualTo(localPropertiesPath)
        assertThat(syncIssue.severity).isEqualTo(SyncIssue.SEVERITY_ERROR)
    }
}
