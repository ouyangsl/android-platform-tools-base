/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.internal

import org.gradle.api.JavaVersion

/**
 * Compilation options
 */
class CompileOptions {

    private JavaVersion sourceCompatibility = JavaVersion.VERSION_1_6
    private JavaVersion targetCompatibility = JavaVersion.VERSION_1_6
    String encoding = "UTF-8"

    void setSourceCompatibility(JavaVersion sourceCompatibility) {
        this.sourceCompatibility = sourceCompatibility
        setExplicitly = true
    }

    /**
     * Language level of the source code.
     *
     * <p>Similar to what <a href="http://www.gradle.org/docs/current/userguide/java_plugin.html">
     * Gradle Java plugin</a> uses.
     */
    JavaVersion getSourceCompatibility() {
        return sourceCompatibility
    }

    void setTargetCompatibility(JavaVersion targetCompatibility) {
        this.targetCompatibility = targetCompatibility
        setExplicitly = true
    }

    /**
     * Version of the generated Java bytecode.
     *
     * <p>Similar to what <a href="http://www.gradle.org/docs/current/userguide/java_plugin.html">
     * Gradle Java plugin</a> uses.
     */
    JavaVersion getTargetCompatibility() {
        return targetCompatibility
    }

    boolean setExplicitly = false
    boolean ndkCygwinMode = false
}
