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

package com.android.build.api.variant.impl

import com.android.build.api.dsl.Packaging
import com.android.build.gradle.internal.dsl.decorator.androidPluginDslDecorator
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.createDslServices
import com.android.build.gradle.internal.services.createProjectServices
import com.android.build.gradle.internal.services.createVariantPropertiesApiServices
import com.google.common.collect.Sets
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class JniLibsTestedComponentPackagingImplTest {

    private lateinit var dslPackaging: Packaging
    private val projectServices = createProjectServices()
    private val dslServices: DslServices = createDslServices(projectServices)
    private val variantPropertiesApiServices = createVariantPropertiesApiServices(projectServices)

    interface PackagingOptionsWrapper {
        val packaging: Packaging
    }

    @Before
    fun setUp() {
        dslPackaging = androidPluginDslDecorator.decorate(PackagingOptionsWrapper::class.java)
            .getDeclaredConstructor(DslServices::class.java)
            .newInstance(dslServices)
            .packaging
    }

    @Test
    fun testTestOnly() {
        dslPackaging.jniLibs.testOnly.add("foo")
        // test setTestOnly method too
        val dslJniLibsPackagingOptionsImpl =
            dslPackaging.jniLibs as com.android.build.gradle.internal.dsl.JniLibsPackagingImpl
        dslJniLibsPackagingOptionsImpl.setTestOnly(
            Sets.union(dslPackaging.jniLibs.testOnly, setOf("bar"))
        )

        val jniLibsTestedComponentPackagingOptions =
            JniLibsTestedComponentPackagingImpl(dslPackaging, variantPropertiesApiServices)

        assertThat(jniLibsTestedComponentPackagingOptions.testOnly.get())
            .containsExactly("foo", "bar")
    }
}
