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

package com.android.build.api.variant.impl

import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.VariantOutput
import com.android.build.api.variant.VariantOutputConfiguration
import org.gradle.api.tasks.Nested

/**
 * Implementation of [List] of [VariantOutput] with added private services for AGP.
 */
class VariantOutputList(
    @get:Nested
    val variantOutputs: List<VariantOutputImpl>,
        private val targetConfigurations: Collection<FilterConfiguration>? = null): List<VariantOutputImpl> by variantOutputs {

    /**
     * Returns the list of enabled [VariantOutput]
     */
    fun getEnabledVariantOutputs(): List<VariantOutputImpl> =
            variantOutputs.filter { it.enabled.get() }

    /**
     * Finds the main split in the current variant context or throws a [RuntimeException] if there
     * are none.
     */
    fun getMainSplit(): VariantOutputImpl =
            getMainSplitOrNull()
                    ?: throw RuntimeException("Cannot determine main split information, file a bug.")

    /**
     * Finds the main split in the current variant context or null if there are no variant output.
     */
    fun getMainSplitOrNull(): VariantOutputImpl? =
            variantOutputs.find { variantOutput ->
                variantOutput.outputType == VariantOutputConfiguration.OutputType.SINGLE
            }
                    ?: variantOutputs.find {
                        it.outputType == VariantOutputConfiguration.OutputType.UNIVERSAL
                    }
                    ?: targetConfigurations?.let {
                        variantOutputs
                                .asSequence()
                                .filter { it.outputType == VariantOutputConfiguration.OutputType.ONE_OF_MANY }
                                .maxByOrNull {
                                    it.variantOutputConfiguration.matchScore(targetConfigurations)
                                }
                    }
                    ?: variantOutputs.find {
                        it.outputType == VariantOutputConfiguration.OutputType.ONE_OF_MANY
                    }
}

fun VariantOutputConfiguration.matchScore(filters: Collection<FilterConfiguration>): Int = filters.intersect(this.filters.toSet()).size
