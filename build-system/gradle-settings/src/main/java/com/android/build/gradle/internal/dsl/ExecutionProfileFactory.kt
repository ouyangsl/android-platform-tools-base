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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.ExecutionProfile
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.model.ObjectFactory

/** Factory to create ExecutionProfileExtension object using an [ObjectFactory] to add the DSL methods.  */
class ExecutionProfileFactory(private val objectFactory: ObjectFactory) :
    NamedDomainObjectFactory<ExecutionProfile> {

    override fun create(name: String): ExecutionProfile {
        return objectFactory.newInstance(ExecutionProfileImpl::class.java, name, objectFactory)
    }
}
