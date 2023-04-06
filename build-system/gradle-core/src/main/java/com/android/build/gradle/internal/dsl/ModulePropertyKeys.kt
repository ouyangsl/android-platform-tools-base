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

package com.android.build.gradle.internal.dsl

import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.StringOption
import com.android.build.gradle.options.parseBoolean
import org.gradle.api.artifacts.Dependency

sealed interface ModulePropertyKey<OutputT> {

    enum class Dependencies(override val key: String) : ModulePropertyKey<List<Dependency>?> {
        /**
         * A [Dependency] providing apigenerator artifact.
         */
        ANDROID_PRIVACY_SANDBOX_SDK_API_GENERATOR(
                StringOption.ANDROID_PRIVACY_SANDBOX_SDK_API_GENERATOR.propertyName),

        /**
         * [ArrayList<Dependency>] of required runtime dependencies of the artifact of the apigenerator.
         */
        ANDROID_PRIVACY_SANDBOX_SDK_API_GENERATOR_GENERATED_RUNTIME_DEPENDENCIES(
                StringOption.ANDROID_PRIVACY_SANDBOX_SDK_API_GENERATOR_GENERATED_RUNTIME_DEPENDENCIES.propertyName),

        ;

        override fun getValue(properties: Map<String, Any>): List<org.gradle.api.artifacts.Dependency>? {
            return when(val value = properties[key]) {
                null -> null
                is Dependency -> listOf(value)
                is List<*> -> value as List<org.gradle.api.artifacts.Dependency>
                else -> throw IllegalArgumentException("Unexpected type ${value::class.qualifiedName} for property $key")
            }
        }
    }

    enum class OptionalBoolean(override val key: String) : ModulePropertyKey<Boolean?> {
        VERIFY_AAR_CLASSES(BooleanOption.VERIFY_AAR_CLASSES.propertyName)
        ;

        override fun getValue(properties: Map<String, Any>): Boolean? {
            return properties[key]?.let { parseBoolean(key, it) }
        }
    }


    enum class BooleanWithDefault(override val key: String, private val default: Boolean) : ModulePropertyKey<Boolean> {
        /**
         * If false - the test APK instruments the target project APK, and the classes are provided.
         * If true - the test APK targets itself (e.g. for macro benchmarks)
         */
        SELF_INSTRUMENTING("android.experimental.self-instrumenting", false),

        /**
         * If false -  R8 will not be provided with the merged art-profile
         * If true - R8 will rewrite the art-profile
         */
        ART_PROFILE_R8_REWRITING("android.experimental.art-profile-r8-rewriting", false),

        /**
         * If false - R8 will not attempt to optimize startup dex
         * If true - R8 will optimize first dex for optimal startup performance.
         */
        R8_DEX_STARTUP_OPTIMIZATION("android.experimental.r8.dex-startup-optimization", false)
        ;

        override fun getValue(properties: Map<String, Any>): Boolean {
            return properties[key]?.let { parseBoolean(key, it) } ?: default
        }
    }

    fun getValue(properties: Map<String, Any>): OutputT

    val key: String
}
