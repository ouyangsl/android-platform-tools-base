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

package com.android.build.gradle.integration

import com.android.build.gradle.integration.common.utils.checkBazelTargetsMatchTestSourceFiles
import org.junit.Test

class BazelTargetTest {

    /**
     * Regression test for b/171017713.
     *
     * This test prevents someone adding a new test file without also adding a corresponding bazel
     * target.
     */
    @Test
    fun testBazelTargetsMatchTestSourceFiles() {
        checkBazelTargetsMatchTestSourceFiles(
            "tools/base/build-system/integration-test/native/src/test",
            "tools/base/build-system/integration-test/native/BUILD.bazel",
            ignoredBazelTargets = listOf("all_test_files", "prebuilts"),
            ignoreTestSourceFiles = listOf("AbstractModuleToModuleDepsTest")
        )
    }
}
