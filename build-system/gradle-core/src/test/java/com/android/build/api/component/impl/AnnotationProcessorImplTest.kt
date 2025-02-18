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

package com.android.build.api.component.impl

import android.databinding.tool.DataBindingBuilder
import com.android.build.gradle.api.AnnotationProcessorOptions
import com.android.build.gradle.internal.fixtures.FakeListProperty
import com.android.build.gradle.internal.fixtures.FakeMapProperty
import com.android.build.gradle.internal.services.VariantServices
import com.google.common.truth.Truth
import org.gradle.process.CommandLineArgumentProvider
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

internal class AnnotationProcessorImplTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    private val annotationProcessorOptions: AnnotationProcessorOptions = mock()

    private val internalServices: VariantServices = mock()

    private fun initMocks(
        classNames: List<String> = listOf(),
        arguments: Map<String, String> = mapOf(),
        providers: List<CommandLineArgumentProvider> = listOf()) {
        whenever(annotationProcessorOptions.classNames).thenReturn(classNames)
        whenever(internalServices.listPropertyOf(String::class.java, classNames, false))
            .thenReturn(FakeListProperty(classNames.toMutableList()))
        whenever(annotationProcessorOptions.arguments).thenReturn(arguments)
        whenever(internalServices.mapPropertyOf(String::class.java, String::class.java, arguments, false))
            .thenReturn(FakeMapProperty(arguments.toMutableMap()))
        whenever(annotationProcessorOptions.compilerArgumentProviders).thenReturn(providers)
    }

    @Test
    fun testFinalListOfClassNames_empty() {
        initMocks()
        val annotationProcessorImpl = AnnotationProcessorImpl(
            annotationProcessorOptions,
            true,
            internalServices
        )

        Truth.assertThat(annotationProcessorImpl.finalListOfClassNames.get())
            .doesNotContain(DataBindingBuilder.PROCESSOR_NAME)
    }

    @Test
    fun testFinalListOfClassNames_with_random_processors() {
        initMocks(mutableListOf("com.foo.RandomProcessor"))
        val annotationProcessorImpl = AnnotationProcessorImpl(
            annotationProcessorOptions,
            true,
            internalServices
        )

        Truth.assertThat(annotationProcessorImpl.finalListOfClassNames.get())
            .contains(DataBindingBuilder.PROCESSOR_NAME)
        Truth.assertThat(annotationProcessorImpl.finalListOfClassNames.get())
            .contains("com.foo.RandomProcessor")
    }

    @Test
    fun testFinalListOfClassNames_with_random_processors_including_databinding() {
        initMocks(
            mutableListOf("com.foo.RandomProcessor", DataBindingBuilder.PROCESSOR_NAME),
        )
        val annotationProcessorImpl = AnnotationProcessorImpl(
            annotationProcessorOptions,
            true,
            internalServices
        )

        Truth.assertThat(annotationProcessorImpl.finalListOfClassNames.get())
            .contains(DataBindingBuilder.PROCESSOR_NAME)
        Truth.assertThat(annotationProcessorImpl.finalListOfClassNames.get())
            .contains("com.foo.RandomProcessor")
    }

    @Test
    fun testFinalListOfClassNames_withArguments() {
        initMocks(arguments = mutableMapOf("-processor" to "foo.bar.SomeProcessor"))
        val annotationProcessorImpl = AnnotationProcessorImpl(
            annotationProcessorOptions,
            true,
            internalServices
        )

        Truth.assertThat(annotationProcessorImpl.finalListOfClassNames.get())
            .contains(DataBindingBuilder.PROCESSOR_NAME)
    }

    @Test
    fun testFinalListOfClassNames_withArguments_including_databinding() {
        initMocks(
            arguments = mutableMapOf("-processor" to "foo.bar.SomeProcessor:${DataBindingBuilder.PROCESSOR_NAME}"),
        )
        val annotationProcessorImpl = AnnotationProcessorImpl(
            annotationProcessorOptions,
            true,
            internalServices
        )

        // since it is present in arguments, it should not be in the final class names.
        Truth.assertThat(annotationProcessorImpl.finalListOfClassNames.get())
            .doesNotContain(DataBindingBuilder.PROCESSOR_NAME)
    }

    @Test
    fun testFinalListOfClassNames_withArgumentProviders() {
        val argumentProvider =
            CommandLineArgumentProvider { listOf("-processor", "com.foo.SomeProcessor") }
        initMocks(providers = listOf(argumentProvider))
        val annotationProcessorImpl = AnnotationProcessorImpl(
            annotationProcessorOptions,
            true,
            internalServices
        )

        Truth.assertThat(annotationProcessorImpl.finalListOfClassNames.get())
            .contains(DataBindingBuilder.PROCESSOR_NAME)
    }

    @Test
    fun testFinalListOfClassNames_withArgumentProviders_including_databinding() {
        val argumentProvider =
            CommandLineArgumentProvider {
                listOf("-processor", "com.foo.SomeProcessor:${DataBindingBuilder.PROCESSOR_NAME}") }
        initMocks(providers = listOf(argumentProvider))
        val annotationProcessorImpl = AnnotationProcessorImpl(
            annotationProcessorOptions,
            true,
            internalServices
        )

        // since it is present in argumentProviders, it should not be in the final class names
        Truth.assertThat(annotationProcessorImpl.finalListOfClassNames.get())
            .doesNotContain(DataBindingBuilder.PROCESSOR_NAME)
    }
}
