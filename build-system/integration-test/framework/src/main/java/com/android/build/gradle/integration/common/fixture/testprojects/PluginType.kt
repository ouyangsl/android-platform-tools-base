/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.integration.common.fixture.testprojects

import com.android.Version
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.privacysandbox.androidxPrivacySandboxLibraryPluginVersion
import com.android.build.gradle.internal.utils.ANDROID_BUILT_IN_KOTLIN_PLUGIN_ID
import com.android.build.gradle.internal.utils.KOTLIN_ANDROID_PLUGIN_ID
import com.android.build.gradle.internal.utils.KOTLIN_KAPT_PLUGIN_ID
import com.android.testutils.TestUtils

sealed class PluginType(
    val id: String,
    val oldId: String = id,
    val kotlinId: String? = null,
    val isAndroid: Boolean = false,
    val isKotlin: Boolean = false,
    val isJava: Boolean = false,
    val useNewDsl: Boolean = true,
    val last: Boolean = false,
    val version: String? = null
) {
    object JAVA_LIBRARY: PluginType(
        id = "java-library",
        isJava = true
    )
    object JAVA: PluginType(
        id = "java",
        isJava = true
    )
    object JAVA_PLATFORM: PluginType(
        id = "java-platform",
        isJava = true
    )
    object APPLICATION: PluginType(
        id = "application",
        isJava = true,
    )
    object KOTLIN_JVM: PluginType(
        id = "org.jetbrains.kotlin.jvm",
        oldId = "kotlin",
        kotlinId = "jvm",
        isKotlin = true,
        version = TestUtils.KOTLIN_VERSION_FOR_TESTS
    )
    object KOTLIN_ANDROID: PluginType(
        id = "org.jetbrains.kotlin.android",
        isAndroid = true,
        isKotlin = true,
        useNewDsl = false,
        last = true,
        version = TestUtils.KOTLIN_VERSION_FOR_TESTS
    )
    object KAPT: PluginType(
        id = "kotlin-kapt",
        isKotlin = true,
        useNewDsl = false,
        last = true,
        version = TestUtils.KOTLIN_VERSION_FOR_TESTS
    )
    object KSP: PluginType(
        id = "com.google.devtools.ksp",
        isKotlin = true,
        useNewDsl = true,
        last = true,
        version = TestUtils.KSP_VERSION_FOR_TESTS
    )
    object KOTLIN_MPP: PluginType(
        id = "org.jetbrains.kotlin.multiplatform",
        oldId = "kotlin-multiplatform",
        isKotlin = true,
        last = true,
        version = TestUtils.KOTLIN_VERSION_FOR_TESTS
    )
    object ANDROID_APP: PluginType(
        id = "com.android.application",
        isAndroid = true,
        useNewDsl = false,
        version = Version.ANDROID_GRADLE_PLUGIN_VERSION
    )
    object ANDROID_LIB: PluginType(
        id = "com.android.library",
        isAndroid = true,
        useNewDsl = false,
        version = Version.ANDROID_GRADLE_PLUGIN_VERSION
    )
    object ANDROID_TEST: PluginType(
        id = "com.android.test",
        isAndroid = true,
        useNewDsl = false,
        version = Version.ANDROID_GRADLE_PLUGIN_VERSION
    )
    object ANDROID_DYNAMIC_FEATURE: PluginType(
        id = "com.android.dynamic-feature",
        isAndroid = true,
        useNewDsl = false,
        version = Version.ANDROID_GRADLE_PLUGIN_VERSION
    )
    object FUSED_LIBRARY: PluginType(
        id = "com.android.fused-library",
        isAndroid = true,
        useNewDsl = true,
        version = Version.ANDROID_GRADLE_PLUGIN_VERSION
    )
    object PRIVACY_SANDBOX_SDK: PluginType(
        id = "com.android.privacy-sandbox-sdk",
        isAndroid = true,
        useNewDsl = true,
        version = Version.ANDROID_GRADLE_PLUGIN_VERSION
    )
    object ANDROIDX_PRIVACY_SANDBOX_LIBRARY: PluginType(
        id = "androidx.privacysandbox.library",
        isAndroid = true,
        useNewDsl = true,
        version = androidxPrivacySandboxLibraryPluginVersion,
    )
    object ANDROID_SETTINGS: PluginType(
        id = "com.android.settings",
        isAndroid = true,
        useNewDsl = true,
        version = Version.ANDROID_GRADLE_PLUGIN_VERSION
    )
    object JAVA_TEST_FIXTURES: PluginType(
        id = "java-test-fixtures",
        useNewDsl = true
    )
    object MAVEN_PUBLISH: PluginType(
        id= "maven-publish",
    )
    object JAVA_GRADLE_PLUGIN: PluginType(
        id="java-gradle-plugin"
    )
    object ANDROID_BUILT_IN_KOTLIN: PluginType(
        id = ANDROID_BUILT_IN_KOTLIN_PLUGIN_ID,
        isAndroid = true,
        useNewDsl = true,
        version = Version.ANDROID_GRADLE_PLUGIN_VERSION
    )
    object JETBRAINS_KOTLIN_ANDROID: PluginType(
        id = KOTLIN_ANDROID_PLUGIN_ID,
        isAndroid = true,
        isKotlin = true,
        useNewDsl = true,
        version = TestUtils.KSP_VERSION_FOR_TESTS,
    )
    class Custom(id: String): PluginType(id)
}

internal fun Iterable<PluginType>.containsAndroid() = any { it.isAndroid }
internal fun Iterable<PluginType>.containsKotlin() = any { it.isKotlin }
internal fun Iterable<PluginType>.containsJava() = any { it.isJava }
