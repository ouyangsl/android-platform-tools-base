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

package com.android.build.gradle.integration.testing

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import org.junit.Rule
import org.junit.Test

class TestWithDepTest : ModelComparator() {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestProject("testWithDep")
        .create()

    @Test
    fun `test VariantDependencies model`() {
        val result = project.modelV2()
            .fetchModels(variantName = "debug")

        with(result).compareVariantDependencies(goldenFile = "VariantDependencies")
    }
}
