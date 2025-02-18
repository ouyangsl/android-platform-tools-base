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

package com.android.build.gradle.integration.common.fixture.model

import com.android.build.gradle.integration.common.fixture.ModelBuilderV2
import com.android.build.gradle.integration.common.fixture.ModelContainerV2
import com.android.builder.model.v2.AndroidModel
import com.android.testutils.TestUtils
import com.android.utils.FileUtils
import com.google.common.base.Charsets
import com.google.common.io.Resources
import com.google.common.truth.Truth
import junit.framework.Assert.fail
import java.io.File
import java.nio.file.Files
import kotlin.io.path.Path

/**
 * Base interface for classes using [Comparator]
 */
interface BaseModelComparator

/**
 * Compare models to golden files.
 * Also allows recreating the golden files when passing a special properties
 *
 * This is meant to be used as a base class for tests running sync.
 */
open class ModelComparator: BaseModelComparator {

    open fun with(
        result: ModelBuilderV2.FetchResult<ModelContainerV2>,
        referenceResult: ModelBuilderV2.FetchResult<ModelContainerV2>? = null): Comparator {
        return Comparator(this, result, referenceResult)
    }
}

abstract class BasicComparator(
    private val testClass: BaseModelComparator,
) {
    /**
     * Runs the comparison
     *
     * @param name a display name for the dump model class
     * @param actualContent the result of the model dump
     * @param goldenFile the test specific portion of the golden file name.
     */
    protected fun runComparison(
        name: String,
        actualContent: String,
        goldenFile: String
    ) {
        if (System.getenv("GENERATE_MODEL_GOLDEN_FILES").isNullOrEmpty()) {
            val expectedText = loadGoldenFile(goldenFile).trimEnd()
            val actualText = actualContent.trimEnd()
            if (TestUtils.runningFromBazel()) {
                // Populate additional test output if the file needs updating
                // This is a bit of a hack, but since we use resources for snapshot files
                // Some assumptions have to be made and hardcoded.
                // Assumptions made:
                // - TEST_BINARY is a relative path to file where the source code lives
                // - Resources are located under 'src/test/resources'
                val snapshotFileToIncludeInBazelOutputs =
                    TestUtils.getTestOutputDir().toFile().resolve(System.getenv("TEST_BINARY"))
                        .parentFile
                        .resolve("src/test/resources")
                        .resolve(testClass.javaClass.name.replace(".", File.separator))
                        .parentFile
                        .resolve(getResourceName(goldenFile))

                if (expectedText != actualText) {
                    println("""
                        Writing updated snapshot file to bazel additional test output.
                        ${snapshotFileToIncludeInBazelOutputs.relativeTo(TestUtils.getTestOutputDir().toFile())}
                    """.trimIndent())
                    snapshotFileToIncludeInBazelOutputs.run {
                        Files.createDirectories(Path(parent))
                        writeText(actualText)
                    }
                }
            }

            Truth.assertWithMessage("Dumped $name (full version in stdout)")
                .that(actualText)
                .isEqualTo(expectedText.trimEnd())

        } else {
            val file = findGoldenFileLocation(goldenFile)
            file.writeText(actualContent)
        }
    }

    protected fun generateStdoutHeader(
        normalizer: FileNormalizer
    ) {
        println("--------------------------------------------------")
        println("To regenerate the golden files use:")
        println("$ GENERATE_MODEL_GOLDEN_FILES=true ./gradlew ...")
        println("--------------------------------------------------")
        println(normalizer)
        println("--------------------------------------------------")
    }

    private fun findGoldenFileLocation(name: String): File {
        val sep = File.separatorChar
        val path = testClass.javaClass.name.replace('.', sep)
        val root = System.getenv("PROJECT_ROOT")

        val fullPath = if (name.isNotBlank()) {
            "$root${sep}src${sep}test${sep}resources${sep}${path}_$name.txt"
        } else {
            "$root${sep}src${sep}test${sep}resources${sep}${path}.txt"
        }

        return File(fullPath).also {
            FileUtils.mkdirs(it.parentFile)
        }
    }

    private fun loadGoldenFile(name: String): String {
        val resourceName = getResourceName(name)
        println("Golden file loaded from $resourceName")
        return Resources.toString(
            Resources.getResource(testClass.javaClass, resourceName
            ), Charsets.UTF_8
        )
    }

    private fun getResourceName(name: String) = if (name.isNotBlank()) {
        "${testClass.javaClass.simpleName}_${name}.txt"
    } else {
        "${testClass.javaClass.simpleName}.txt"
    }
}

class Comparator(
    testClass: BaseModelComparator,
    private val result: ModelBuilderV2.FetchResult<ModelContainerV2>,
    private val referenceResult: ModelBuilderV2.FetchResult<ModelContainerV2>?
): BasicComparator(testClass) {

    fun compareVersions(
        projectAction: ModelContainerV2.() -> ModelContainerV2.ModelInfo = { getProject() },
        goldenFile: String
    ) {
        compareModel(
            projectAction = projectAction,
            modelName = "Versions",
            modelAction = { versions },
            snapshotAction = { snapshotVersions() },
            goldenFile = goldenFile
        )
    }

    fun compareBasicAndroidProject(
        projectAction: ModelContainerV2.() -> ModelContainerV2.ModelInfo = { getProject() },
        goldenFile: String
    ) {
        compareModel(
            projectAction = projectAction,
            modelName = "BasicAndroidProject",
            modelAction = { basicAndroidProject },
            snapshotAction = { snapshotBasicAndroidProject() },
            goldenFile = goldenFile
        )
    }

    fun ensureBasicAndroidProjectIsEmpty(
        projectAction: ModelContainerV2.() -> ModelContainerV2.ModelInfo = { getProject() },
    ) {
        ensureModelIsEmpty(
            projectAction = projectAction,
            modelName = "BasicAndroidProject",
            modelAction = { basicAndroidProject },
            snapshotAction = { snapshotBasicAndroidProject() }
        )
    }

    fun compareAndroidProject(
        projectAction: ModelContainerV2.() -> ModelContainerV2.ModelInfo = { getProject() },
        goldenFile: String
    ) {
        compareModel(
            projectAction = projectAction,
            modelName = "AndroidProject",
            modelAction = { androidProject },
            snapshotAction = { snapshotAndroidProject() },
            goldenFile = goldenFile
        )
    }

    fun ensureAndroidProjectIsEmpty(
        projectAction: ModelContainerV2.() -> ModelContainerV2.ModelInfo = { getProject() },
    ) {
        ensureModelIsEmpty(
            projectAction = projectAction,
            modelName = "AndroidProject",
            modelAction = { androidProject },
            snapshotAction = { snapshotAndroidProject() }
        )
    }

    fun compareAndroidDsl(
        projectAction: ModelContainerV2.() -> ModelContainerV2.ModelInfo = { getProject() },
        goldenFile: String
    ) {
        compareModel(
            projectAction = projectAction,
            modelName = "AndroidDsl",
            modelAction = { androidDsl },
            snapshotAction = { snapshotAndroidDsl() },
            goldenFile = goldenFile
        )
    }

    fun ensureAndroidDslIsEmpty(
        projectAction: ModelContainerV2.() -> ModelContainerV2.ModelInfo = { getProject() },
    ) {
        ensureModelIsEmpty(
            projectAction = projectAction,
            modelName = "AndroidDsl",
            modelAction = { androidDsl },
            snapshotAction = { snapshotAndroidDsl() }
        )
    }

    fun compareVariantDependencies(
        projectAction: ModelContainerV2.() -> ModelContainerV2.ModelInfo = { getProject() },
        goldenFile: String
    ) {
        compareModel(
            projectAction = projectAction,
            modelName = "VariantDependencies",
            modelAction = { variantDependencies },
            snapshotAction = { snapshotVariantDependencies() },
            goldenFile = goldenFile
        )
    }

    fun ensureVariantDependenciesIsEmpty(
        projectAction: ModelContainerV2.() -> ModelContainerV2.ModelInfo = { getProject() },
    ) {
        ensureModelIsEmpty(
            projectAction = projectAction,
            modelName = "VariantDependencies",
            modelAction = { variantDependencies },
            snapshotAction = { snapshotVariantDependencies() }
        )
    }

    fun compareNativeModule(
        projectAction: ModelContainerV2.() -> ModelContainerV2.ModelInfo = { getProject() },
        goldenFile: String
    ) {
        compareModel(
            projectAction = projectAction,
            modelName = "NativeModule",
            modelAction = { nativeModule },
            snapshotAction = { snapshotNativeModule() },
            goldenFile = goldenFile
        )
    }

    private fun <ModelT: AndroidModel> compareModel(
        projectAction: ModelContainerV2.() -> ModelContainerV2.ModelInfo,
        modelName: String,
        modelAction: ModelContainerV2.ModelInfo.() -> ModelT?,
        snapshotAction: ModelSnapshotter<ModelT>.() -> Unit,
        goldenFile: String
    ) {
        val content = snapshotModel(
            modelName = modelName,
            modelAction = { modelAction(projectAction(this)) ?: throw RuntimeException("No $modelName model") },
            project = result,
            referenceProject = referenceResult,
            action = snapshotAction
        ).also {
            generateStdoutHeader(result.normalizer)
            println(it)
        }

        runComparison(modelName, content, goldenFile)
    }

    private fun <ModelT: AndroidModel> ensureModelIsEmpty(
        projectAction: ModelContainerV2.() -> ModelContainerV2.ModelInfo = { getProject() },
        modelName: String,
        modelAction: ModelContainerV2.ModelInfo.() -> ModelT?,
        snapshotAction: ModelSnapshotter<ModelT>.() -> Unit,
    ) {
        checkEmptyDelta(
            modelName = modelName,
            modelAction = { modelAction(projectAction(this)) ?: throw RuntimeException("No $modelName model") },
            project = result,
            referenceProject = referenceResult!!,
            action = snapshotAction,
            failureAction =  {
                generateStdoutHeader(result.normalizer)
                println(SnapshotItemWriter().write(it))

                fail("Expected no differences between $modelAction models. See stdout for details")
            }
        )
    }
}
