/*
 * Copyright (C) 2024 The Android Open Source Project
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

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.Configuring

class SourceSetContainer(
    private val delegate: NamedDomainObjectContainer<AndroidSourceSet>
) : NamedDomainObjectContainer<AndroidSourceSet> by delegate {


    @Configuring
    fun main(action: AndroidSourceSet.() -> Unit) {
        getByName("main", action)
    }

    @Configuring
    fun androidTest(action: AndroidSourceSet.() -> Unit) {
        getByName("androidTest", action)
    }

    @Adding
    fun configure(name: String, action: AndroidSourceSet.() -> Unit): AndroidSourceSet {
        return getByName(name, action)
    }

}
