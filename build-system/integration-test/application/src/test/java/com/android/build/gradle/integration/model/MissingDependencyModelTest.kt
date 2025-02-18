/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.integration.model

import com.android.Version
import com.android.build.gradle.integration.common.fixture.project.GradleRule
import com.android.build.gradle.integration.common.fixture.project.prebuilts.HelloWorldAndroid
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.build.gradle.internal.ide.v2.UnresolvedDependencyImpl
import com.android.builder.model.v2.ide.SyncIssue
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class MissingDependencyModelTest {

    @get:Rule
    val rule = GradleRule.from {
        androidApplication(":app") {
            HelloWorldAndroid.setupJava(files)
            dependencies {
                implementation("foo:bar:1.1")
            }
        }
    }

    @Test
    fun `test models`() {
        val result = rule.build.modelBuilder
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug")

        Truth.assertThat(
            result.container.getProject().variantDependencies?.mainArtifact?.unresolvedDependencies?.map {
                UnresolvedDependencyImpl(it.name, it.cause)
            }
        ).containsExactly(UnresolvedDependencyImpl("foo:bar:1.1", null))
    }
}

class UnresolvedVariantDependencyModelTest {

    @get:Rule
    val project = createGradleProject {
        subProject(":app") {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
                buildTypes {
                    named("staging") {}
                }
            }
            dependencies {
                implementation(project(":lib"))
            }
        }
        subProject(":lib") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                setUpHelloWorld()
            }
        }
    }

    @Test
    fun `test models`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "staging")

        val appInfo = result.container.rootInfoMap[":app"] ?: throw RuntimeException("No app info")
        val variantDependencies =
            appInfo.variantDependencies ?: throw RuntimeException("No variant dep")

        val unresolvedDeps = variantDependencies.mainArtifact.unresolvedDependencies.map {
            UnresolvedDependencyImpl(it.name, it.cause?.fixLineEndings()?.fixAgpVersion())
        }

        Truth.assertThat(unresolvedDeps).hasSize(1)
        Truth.assertThat(unresolvedDeps.single().name).isEqualTo("project :lib")
        Truth.assertThat(unresolvedDeps.single().cause).isEqualTo(
                """
No matching variant of project :lib was found. The consumer was configured to find a library for use during compile-time, preferably optimized for Android, as well as attribute 'com.android.build.api.attributes.AgpVersionAttr' with value '{AGP-VERSION}', attribute 'com.android.build.api.attributes.BuildTypeAttr' with value 'staging', attribute 'org.jetbrains.kotlin.platform.type' with value 'androidJvm' but:
  - Variant 'debugApiElements' declares a component for use during compile-time, as well as attribute 'com.android.build.api.attributes.AgpVersionAttr' with value '{AGP-VERSION}':
      - Incompatible because this component declares a component, as well as attribute 'com.android.build.api.attributes.BuildTypeAttr' with value 'debug' and the consumer needed a component, as well as attribute 'com.android.build.api.attributes.BuildTypeAttr' with value 'staging'
      - Other compatible attributes:
          - Doesn't say anything about its component category (required a library)
          - Doesn't say anything about its target Java environment (preferred optimized for Android)
          - Doesn't say anything about org.jetbrains.kotlin.platform.type (required 'androidJvm')
  - Variant 'debugRuntimeElements' declares a component for use during runtime, as well as attribute 'com.android.build.api.attributes.AgpVersionAttr' with value '{AGP-VERSION}':
      - Incompatible because this component declares a component, as well as attribute 'com.android.build.api.attributes.BuildTypeAttr' with value 'debug' and the consumer needed a component, as well as attribute 'com.android.build.api.attributes.BuildTypeAttr' with value 'staging'
      - Other compatible attributes:
          - Doesn't say anything about its component category (required a library)
          - Doesn't say anything about its target Java environment (preferred optimized for Android)
          - Doesn't say anything about org.jetbrains.kotlin.platform.type (required 'androidJvm')
  - Variant 'releaseApiElements' declares a component for use during compile-time, as well as attribute 'com.android.build.api.attributes.AgpVersionAttr' with value '{AGP-VERSION}':
      - Incompatible because this component declares a component, as well as attribute 'com.android.build.api.attributes.BuildTypeAttr' with value 'release' and the consumer needed a component, as well as attribute 'com.android.build.api.attributes.BuildTypeAttr' with value 'staging'
      - Other compatible attributes:
          - Doesn't say anything about its component category (required a library)
          - Doesn't say anything about its target Java environment (preferred optimized for Android)
          - Doesn't say anything about org.jetbrains.kotlin.platform.type (required 'androidJvm')
  - Variant 'releaseRuntimeElements' declares a component for use during runtime, as well as attribute 'com.android.build.api.attributes.AgpVersionAttr' with value '{AGP-VERSION}':
      - Incompatible because this component declares a component, as well as attribute 'com.android.build.api.attributes.BuildTypeAttr' with value 'release' and the consumer needed a component, as well as attribute 'com.android.build.api.attributes.BuildTypeAttr' with value 'staging'
      - Other compatible attributes:
          - Doesn't say anything about its component category (required a library)
          - Doesn't say anything about its target Java environment (preferred optimized for Android)
          - Doesn't say anything about org.jetbrains.kotlin.platform.type (required 'androidJvm')
""".trimIndent()
        )
    }
}

private fun String.fixLineEndings(): String = this.replace("\r\n", "\n")

private fun String.fixAgpVersion(): String = this.replace(Version.ANDROID_GRADLE_PLUGIN_VERSION, "{AGP-VERSION}")
