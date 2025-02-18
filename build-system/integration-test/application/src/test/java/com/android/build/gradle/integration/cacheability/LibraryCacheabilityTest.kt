/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.cacheability

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.DID_WORK
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.FAILED
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.FROM_CACHE
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.SKIPPED
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.UP_TO_DATE
import com.android.build.gradle.integration.common.utils.CacheabilityTestHelper
import com.android.build.gradle.options.BooleanOption
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Similar to [CacheabilityTest], but targeting the release version of a library module to verify a
 * different set of tasks.
 */
class LibraryCacheabilityTest {

    companion object {

        private const val GRADLE_BUILD_CACHE_DIR = "gradle-build-cache"

        /**
         * The expected states of tasks when running a second build with the Gradle build cache
         * enabled from an identical project at a different location.
         */
        private val EXPECTED_TASK_STATES =
            mapOf(
                // Sort by alphabetical order for easier searching
                UP_TO_DATE to setOf(
                    ":app:clean",
                    ":lib:clean",
                    ":lib:generateReleaseAssets",
                    ":lib:preBuild",
                    ":lib:preReleaseBuild"
                ),
                FROM_CACHE to setOf(
                    ":lib:compileReleaseJavaWithJavac",
                    ":lib:extractDeepLinksForAarRelease",
                    ":lib:extractReleaseAnnotations",
                    ":lib:generateReleaseResources",
                    ":lib:generateReleaseResValues",
                    ":lib:generateReleaseRFile",
                    ":lib:javaPreCompileRelease",
                    ":lib:mergeReleaseResources",
                    ":lib:packageReleaseResources",
                    ":lib:parseReleaseLocalResources",
                    ":lib:processReleaseManifest",
                    ":lib:syncReleaseLibJars",
                    ":lib:verifyReleaseResources",
                ),
                /*
                 * Tasks that should be cacheable but are not yet cacheable.
                 *
                 * If you add a task to this list, remember to file a bug for it.
                 */
                DID_WORK to setOf(
                    ":lib:copyReleaseJniLibsProjectAndLocalJars", /** Intentionally not cacheable. See [com.android.build.gradle.internal.tasks.LibraryJniLibsTask] */
                    ":lib:prepareReleaseArtProfile", /* No Bug, task is just file copy */
                    ":lib:bundleReleaseAar" /*Bug 121275773 */,
                    ":lib:mapReleaseSourceSetPaths", /* Intentionally not cacheable */
                    ":lib:mergeReleaseConsumerProguardFiles", /** Intentionally not cacheable. See [com.android.build.gradle.internal.tasks.MergeConsumerProguardFilesTask] */
                    ":lib:mergeReleaseGeneratedProguardFiles", /** Intentionally not cacheable. See [com.android.build.gradle.internal.tasks.MergeGeneratedProguardFilesCreationAction] */
                    ":lib:mergeReleaseJavaResource", /* Bug 181142260 */
                    ":lib:mergeReleaseJniLibFolders",
                    ":lib:mergeReleaseShaders",
                    ":lib:packageReleaseAssets",
                    ":lib:prepareLintJarForPublish", /* Bug 120413672 */
                    /** Intentionally not cacheable. See [com.android.build.gradle.internal.tasks.AarMetadataTask] */
                    ":lib:writeReleaseAarMetadata",
                ),
                SKIPPED to setOf(
                    ":lib:assembleRelease",
                    ":lib:compileReleaseShaders",
                    ":lib:mergeReleaseNativeLibs",
                    ":lib:processReleaseJavaRes",
                    ":lib:stripReleaseDebugSymbols"
                ),
                FAILED to setOf()
            )
    }

    @get:Rule
    val projectCopy1 = setUpTestProject("projectCopy1")

    @get:Rule
    val projectCopy2 = setUpTestProject("projectCopy2")

    @get:Rule
    val buildCacheDirRoot = TemporaryFolder()

    private fun setUpTestProject(projectName: String): GradleTestProject {
        return GradleTestProject.builder()
            .fromTestApp(HelloWorldLibraryApp())
            .withName(projectName)
            .dontOutputLogOnFailure()
            .create()
    }

    @Test
    fun testRelocatability() {
        val buildCacheDir = buildCacheDirRoot.root.resolve(GRADLE_BUILD_CACHE_DIR)

        CacheabilityTestHelper(projectCopy1, projectCopy2, buildCacheDir)
            .runTasks("clean", ":lib:assembleRelease")
            .assertTaskStatesByGroups(EXPECTED_TASK_STATES, exhaustive = true)
    }
}
