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

package com.android.build.api.dsl

import org.gradle.api.JavaVersion
import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.Restricted

/**
 * Java compilation options.
 */
interface CompileOptions {
    // Because expressions that reference enums are not supported, this is a factory func that returns an enum
    @Restricted
    fun getjava17(): JavaVersion {
        return JavaVersion.VERSION_17
    }

    /**
     * Language level of the java source code.
     *
     * Similar to what [Gradle Java plugin](http://www.gradle.org/docs/current/userguide/java_plugin.html)
     * uses. Formats supported are:
     *
     * - `"1.6"`
     * - `1.6`
     * - `JavaVersion.Version_1_6`
     * - `"Version_1_6"`
     */
    @get:Restricted
    var sourceCompatibility: JavaVersion

    /**
     * Language level of the java source code.
     *
     * Similar to what [Gradle Java plugin](http://www.gradle.org/docs/current/userguide/java_plugin.html)
     * uses. Formats supported are:
     *
     * - `"1.6"`
     * - `1.6`
     * - `JavaVersion.Version_1_6`
     * - `"Version_1_6"`
     */
    @Adding
    fun sourceCompatibility(sourceCompatibility: String)

    /**
     * Version of the generated Java bytecode.
     *
     * Similar to what [Gradle Java plugin](http://www.gradle.org/docs/current/userguide/java_plugin.html)
     * uses. Formats supported are:
     *
     * - `"1.6"`
     * - `1.6`
     * - `JavaVersion.Version_1_6`
     * - `"Version_1_6"`
     */
    @get:Restricted
    var targetCompatibility: JavaVersion

    /**
     * Version of the generated Java bytecode.
     *
     * Similar to what [Gradle Java plugin](http://www.gradle.org/docs/current/userguide/java_plugin.html)
     * uses. Formats supported are:
     *
     * - `"1.6"`
     * - `1.6`
     * - `JavaVersion.Version_1_6`
     * - `"Version_1_6"`
     */
    @Adding
    fun targetCompatibility(targetCompatibility: String)

    /** Java source files encoding. */
    @get:Restricted
    var encoding: String

    /** Whether core library desugaring is enabled. */
    @get:Restricted
    var isCoreLibraryDesugaringEnabled: Boolean
}
