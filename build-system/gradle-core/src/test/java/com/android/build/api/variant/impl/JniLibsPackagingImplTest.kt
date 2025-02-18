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

class JniLibsPackagingImplTest {

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
    fun testExcludes() {
        dslPackaging.excludes.add("foo")
        dslPackaging.jniLibs.excludes.add("bar")
        // test setExcludes method too
        val dslJniLibsPackagingOptionsImpl =
            dslPackaging.jniLibs
                as com.android.build.gradle.internal.dsl.JniLibsPackagingImpl
        dslJniLibsPackagingOptionsImpl.setExcludes(
            Sets.union(dslPackaging.jniLibs.excludes, setOf("baz"))
        )

        val jniLibsPackagingOptions =
            JniLibsPackagingImpl(dslPackaging, variantPropertiesApiServices)

        assertThat(jniLibsPackagingOptions.excludes.get()).containsExactly("foo", "bar", "baz")
    }

    @Test
    fun testPickFirsts() {
        dslPackaging.pickFirsts.add("foo")
        dslPackaging.jniLibs.pickFirsts.add("bar")
        // test setPickFirsts method too
        val dslJniLibsPackagingOptionsImpl =
            dslPackaging.jniLibs
                as com.android.build.gradle.internal.dsl.JniLibsPackagingImpl
        dslJniLibsPackagingOptionsImpl.setPickFirsts(
            Sets.union(dslPackaging.jniLibs.pickFirsts, setOf("baz"))
        )

        val jniLibsPackagingOptions =
            JniLibsPackagingImpl(dslPackaging, variantPropertiesApiServices)

        assertThat(jniLibsPackagingOptions.pickFirsts.get()).containsExactly("foo", "bar", "baz")
    }

    @Test
    fun testKeepDebugSymbols() {
        dslPackaging.doNotStrip.add("foo")
        dslPackaging.jniLibs.keepDebugSymbols.add("bar")
        // test setKeepDebugSymbols method too
        val dslJniLibsPackagingOptionsImpl =
            dslPackaging.jniLibs
                as com.android.build.gradle.internal.dsl.JniLibsPackagingImpl
        dslJniLibsPackagingOptionsImpl.setKeepDebugSymbols(
            Sets.union(dslPackaging.jniLibs.keepDebugSymbols, setOf("baz"))
        )

        val jniLibsPackagingOptions =
            JniLibsPackagingImpl(dslPackaging, variantPropertiesApiServices)

        assertThat(jniLibsPackagingOptions.keepDebugSymbols.get())
            .containsExactly("foo", "bar", "baz")
    }
}
