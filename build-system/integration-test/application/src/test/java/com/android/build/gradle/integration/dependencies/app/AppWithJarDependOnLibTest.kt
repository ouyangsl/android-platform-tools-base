/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.gradle.integration.dependencies.app

import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.builder.model.v2.ide.SyncIssue
import org.junit.Rule
import org.junit.Test

class AppWithJarDependOnLibTest : ModelComparator() {

    @get:Rule
    val project = createGradleProject {
        subProject(":app") {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
            dependencies {
                api(project(":jar"))
            }
        }
        subProject(":library") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                setUpHelloWorld()
            }
        }
        subProject(":jar") {
            plugins.add(PluginType.JAVA_LIBRARY)
            dependencies {
                api(project(":library"))
            }
        }
    }

    @Test
    fun `test VariantDependencies model`() {
        val result =
            project.modelV2()
                .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
                .fetchModels(variantName = "debug")

        with(result).compareVariantDependencies(
            projectAction = { getProject(":app") }, goldenFile = "app_VariantDependencies"
        )
    }
}
