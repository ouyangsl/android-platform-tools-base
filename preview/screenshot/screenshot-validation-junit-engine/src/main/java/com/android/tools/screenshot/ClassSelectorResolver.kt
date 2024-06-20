/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.screenshot

import java.util.function.Predicate
import java.util.Optional
import java.util.Optional.of
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.discovery.ClassSelector
import org.junit.platform.engine.discovery.MethodSelector
import org.junit.platform.engine.discovery.UniqueIdSelector
import org.junit.platform.engine.support.discovery.SelectorResolver
import org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod
import org.junit.platform.engine.support.discovery.SelectorResolver.Context
import org.junit.platform.engine.support.discovery.SelectorResolver.Resolution
import org.junit.platform.engine.support.discovery.SelectorResolver.Match
import java.util.stream.Collectors.toSet

internal class ClassSelectorResolver(
    private val classNameFilter: Predicate<String>,
    private val tests: Tests
) : SelectorResolver {

    override fun resolve(selector: ClassSelector, context: Context): Resolution {
        return resolve(selector.className, context)
    }

    override fun resolve(selector: UniqueIdSelector, context: Context): Resolution {
        val lastSegment: UniqueId.Segment = selector.uniqueId.lastSegment
        if (SEGMENT_TYPE == lastSegment.type) {
            val className: String = lastSegment.value
            return resolve(className, context)
        }
        return Resolution.unresolved()
    }

    private fun resolve(className: String, context: Context): Resolution {
        return if (tests.classes.contains(className)) {
            context.addToParent { parent -> createTestClassDescriptor(className, parent) }
                .map {
                    Match.exact(it) {
                        getMethodSelectors(
                            className
                        )
                    }
                }
                .map(Resolution::match)
                .orElseGet(Resolution::unresolved)
        } else Resolution.unresolved()
    }

    private fun createTestClassDescriptor(
        className: String,
        parent: TestDescriptor
    ): Optional<TestClassDescriptor> {
        val uniqueId: UniqueId = parent.uniqueId.append(SEGMENT_TYPE, className)
        return of<TestClassDescriptor>(TestClassDescriptor(uniqueId, className))
    }

    private fun getMethodSelectors(className: String): Set<MethodSelector?> {
        return tests.getMethods(className).stream()
            .map { testMethod -> selectMethod(className, testMethod.methodName) }
            .collect(toSet())
    }

    companion object {
        private const val SEGMENT_TYPE = "class"
    }
}
