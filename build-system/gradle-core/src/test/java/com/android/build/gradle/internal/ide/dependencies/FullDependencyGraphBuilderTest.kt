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

package com.android.build.gradle.internal.ide.dependencies

import com.android.build.api.attributes.BuildTypeAttr
import com.android.build.gradle.internal.dependency.AdditionalArtifactType
import com.android.build.gradle.internal.dependency.ResolutionResultProvider
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.build.gradle.internal.ide.dependencies.ResolvedArtifact.DependencyType.ANDROID
import com.android.build.gradle.internal.ide.dependencies.ResolvedArtifact.DependencyType.NO_ARTIFACT_FILE
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.builder.model.v2.ide.ArtifactDependencies
import com.android.builder.model.v2.ide.Library
import com.android.builder.model.v2.ide.LibraryType
import com.google.common.truth.Truth
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

internal class FullDependencyGraphBuilderTest {

    val objectFactory = FakeObjectFactory.factory

    @Test
    fun `basic dependency`() {
        val (graphs, libraries) = buildModelGraph {
            module("foo", "bar", "1.0") {
                file = File("path/to/bar-1.0.jar")
                setupDefaultCapability()
            }
        }

        Truth
            .assertThat(graphs.compileDependencies.map { it.key })
            .containsExactly("foo|bar|1.0||foo:bar:1.0")
        val item = graphs.compileDependencies.first()
        Truth.assertThat(item.dependencies).isEmpty()
    }

    @Test
    fun `basic attributes`() {
        val (graphs, libraries) = buildModelGraph {
            module("foo", "bar", "1.0") {
                file = File("path/to/bar-1.0.jar")
                setupDefaultCapability()
                attribute(
                    BuildTypeAttr.ATTRIBUTE,
                    objectFactory.named(BuildTypeAttr::class.java, "debug")
                )
            }
        }

        Truth
            .assertThat(graphs.compileDependencies.map { it.key })
            .containsExactly("foo|bar|1.0|debug||foo:bar:1.0")
        val item = graphs.compileDependencies.first()
        Truth.assertThat(item.dependencies).isEmpty()
    }

    @Test
    fun `graph with java module test fixtures`() {
        val (graphs, libraries) = buildModelGraph {
            val mainLib = module("foo", "bar", "1.0") {
                file = File("path/to/bar-1.0.jar")
                setupDefaultCapability()
            }
            module("foo", "bar", "1.0") {
                file = File("path/to/bar-test-fixtures-1.0.jar")
                capability("foo", "bar-test-fixtures", "1.0")

                dependency(mainLib)
            }
        }

        val compileDependencies = graphs.compileDependencies
        Truth
            .assertThat(compileDependencies.map { it.key })
            .containsExactly(
                "foo|bar|1.0||foo:bar:1.0",
                "foo|bar|1.0||foo:bar-test-fixtures:1.0"
            )

        // check that the dependency instance of the fixture is the same instance as the main
        val fixture =
            compileDependencies.single { it.key == "foo|bar|1.0||foo:bar-test-fixtures:1.0" }
        val main = compileDependencies.single { it.key == "foo|bar|1.0||foo:bar:1.0" }

        Truth.assertThat(fixture.dependencies.single()).isSameInstanceAs(main)
    }

    @Test
    fun `graph with android project test fixtures`() {
        val (graphs, libraries) = buildModelGraph {
            val mainLib = project(":foo") {
                dependencyType = ANDROID
                file = File("path/to/mergedManifest/debug/AndroidManifest.xml")
                capability("foo", "bar", "1.0")
            }
            project(":foo") {
                dependencyType = ANDROID
                file = File("path/to/mergedManifest/debugTestFixtures/AndroidManifest.xml")
                capability("foo", "bar-test-fixtures", "1.0")

                dependency(mainLib)
            }
        }

        val compileDependencies = graphs.compileDependencies
        Truth
            .assertThat(compileDependencies.map { it.key })
            .containsExactly(
                "defaultBuildPath|:foo||foo:bar:1.0",
                "defaultBuildPath|:foo||foo:bar-test-fixtures:1.0"
            )

        // check that the dependency instance of the fixture is the same instance as the main
        val fixture =
            compileDependencies.single { it.key == "defaultBuildPath|:foo||foo:bar-test-fixtures:1.0" }
        val main = compileDependencies.single { it.key == "defaultBuildPath|:foo||foo:bar:1.0" }

        Truth.assertThat(fixture.dependencies.single()).isSameInstanceAs(main)
    }

    @Test
    fun `relocated artifact`() {
        val (graphs, libraries) = buildModelGraph {
            module("foo", "bar", "1.0") {
                setupDefaultCapability()

                availableAt = module("relocated-foo", "bar", "1.0") {
                    file = File("path/to/bar-1.0.jar")
                    setupDefaultCapability()
                }
            }
        }

        Truth
            .assertThat(graphs.compileDependencies.map { it.key })
            .containsExactly("foo|bar|1.0||foo:bar:1.0")

        val rootLibrary =
            libraries["foo|bar|1.0||foo:bar:1.0"]
                ?: throw RuntimeException("Failed to find root library")
        Truth.assertWithMessage("root library").that(rootLibrary.artifact).isNull()
        Truth.assertWithMessage("root library").that(rootLibrary.type).isEqualTo(LibraryType.RELOCATED)

        val children = graphs.compileDependencies.single().dependencies
        Truth
            .assertThat(children.map { it.key })
            .containsExactly("relocated-foo|bar|1.0||relocated-foo:bar:1.0")
    }

    @Test
    fun testDependencyConstraint() {
        val (graphs, libraries) = buildModelGraph {
            val barParentLib = module("foo", "bar-parent", "1.0") {
                file = File("path/to/bar-parent-1.0.jar")
            }

            val barLib = module("foo", "bar", "1.0") {
                file = File("path/to/bar-1.0.jar")
                dependency(barParentLib)
            }
            barParentLib.dependencyConstraint(barLib)
        }

        Truth
            .assertThat(graphs.compileDependencies.map { it.key })
            .containsExactly("foo|bar-parent|1.0||", "foo|bar|1.0||")
    }

    /** Regression test for http://b/230648123. */
    @Test
    fun testDependencyWithoutFileWithDependencies() {
        val (graphs, _) = buildModelGraph {
            module("foo", "bar", "1.0") {
                file = null
                dependencyType = NO_ARTIFACT_FILE
                // intentionally no file present, just a dependency on parent
                dependency(
                    module("foo", "bar-parent", "1.0") {
                        file = File("path/to/bar-parent-1.0.jar")
                    }
                )
            }
        }

        Truth
            .assertThat(graphs.compileDependencies.map { it.key })
            .containsExactly("foo|bar|1.0||")
        Truth
            .assertThat(graphs.compileDependencies.single().dependencies.map { it.key })
            .containsExactly("foo|bar-parent|1.0||")
    }

    /**
     * Ensure model builder for variant dependencies respects the parameter to disable the resolving
     * of the runtime classpath.
     */
    @Test
    fun testBuildCompileClasspathOnly() {
        val (graphs, libraries) = buildModelGraph(true) {
            module("foo", "bar", "1.0") {
                file = File("path/to/bar-1.0.jar")
                setupDefaultCapability()
                attribute(
                    BuildTypeAttr.ATTRIBUTE,
                    objectFactory.named(BuildTypeAttr::class.java, "debug")
                )
            }
        }

        Truth
            .assertThat(graphs.runtimeDependencies).isNull()
        Truth
            .assertThat(graphs.compileDependencies.map { it.key })
            .containsExactly("foo|bar|1.0|debug||foo:bar:1.0")
    }
}

// -------------

private fun buildModelGraph(
    dontBuildRuntimeClasspath: Boolean = false,
    action: DependencyBuilder.() -> Unit
): Pair<ArtifactDependencies, Map<String, Library>> {
    val stringCache = StringCacheImpl()
    val localJarCache = LocalJarCacheImpl()
    val libraryService = LibraryServiceImpl(LibraryCacheImpl(stringCache, localJarCache))

    val (dependencyResults, resolvedArtifacts) = buildGraph(action)

    val builder = FullDependencyGraphBuilder(
        { _, _  -> resolvedArtifacts },
        ":path:to:project",
        getResolutionResultProvider(dependencyResults),
        libraryService,
        GraphEdgeCacheImpl(),
        addAdditionalArtifactsInModel = true,
        dontBuildRuntimeClasspath,
    )

    return builder.build() to libraryService.getAllLibraries()
}


private fun getResolutionResultProvider(
    compileResultsResults: Set<DependencyResult>
): ResolutionResultProvider {
    val result = mock<ResolutionResult>()
    val root = mock<ResolvedComponentResult>()
    val additionalArtifacts = mock<ArtifactCollection>()
    val iterator = mock<MutableIterator<ResolvedArtifactResult>>()
    whenever(result.root).thenReturn(root)
    whenever(root.dependencies).thenReturn(compileResultsResults)
    whenever(additionalArtifacts.iterator()).thenReturn(iterator)
    whenever(iterator.hasNext()).thenReturn(false)

    return ResolutionResultProviderImpl(result, result, additionalArtifacts)
}

private class ResolutionResultProviderImpl(
    private val compileResult: ResolutionResult,
    private val runtimeResult: ResolutionResult,
    private val additionalArtifacts: ArtifactCollection
): ResolutionResultProvider {

    override fun getResolutionResult(
        configType: AndroidArtifacts.ConsumedConfigType
    ): ResolutionResult =
            when (configType) {
                AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH -> compileResult
                AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH -> runtimeResult
                else -> throw RuntimeException("Unsupported ConsumedConfigType value: $configType")
            }

    override fun getAdditionalArtifacts(
            configType: AndroidArtifacts.ConsumedConfigType,
            type: AdditionalArtifactType
    ): ArtifactCollection {
        return additionalArtifacts
    }
}
