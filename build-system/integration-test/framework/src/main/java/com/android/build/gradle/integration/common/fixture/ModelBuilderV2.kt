/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.build.gradle.integration.common.fixture

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_NDK_SIDE_BY_SIDE_VERSION
import com.android.build.gradle.integration.common.fixture.ModelContainerV2.ModelInfo
import com.android.build.gradle.integration.common.fixture.gradle_project.ProjectLocation
import com.android.build.gradle.integration.common.fixture.model.FileNormalizer
import com.android.build.gradle.integration.common.fixture.model.normalizeVersionsOfCommonDependencies
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.Option
import com.android.builder.model.v2.ide.SyncIssue
import com.android.builder.model.v2.models.ModelBuilderParameter
import com.google.common.collect.Sets
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildActionExecuter
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.model.GradleProject
import org.junit.Assert
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.Serializable
import java.nio.file.Path
import java.util.function.Consumer

/**
 * Builder for actions that get the gradle model from a [GradleTestProject].
 *
 * This returns the v2 model as a [FetchResult]
 */
class ModelBuilderV2 internal constructor(
    location: ProjectLocation,
    gradleTestInfo: GradleTestInfo,
    gradleOptions: GradleOptions,
    projectConnection: ProjectConnection,
    lastBuildResultConsumer: Consumer<GradleBuildResult>
) : BaseGradleExecutor<ModelBuilderV2>(
    location,
    gradleTestInfo,
    projectConnection,
    lastBuildResultConsumer,
    gradleOptions.mutate { withConfigurationCaching(ConfigurationCaching.ON) }
) {
    private val explicitlyAllowedOptions = mutableSetOf<String>()
    private var maxSyncIssueSeverityLevel = 0

    data class FetchResult<T>(
        val container: T,
        val normalizer: FileNormalizer
    )

    data class NativeModuleParams(
        /**
         * Names of the variants to sync for the given modules. Use null to sync all variants
         */
        val nativeVariants: List<String>? = null,
        /**
         * Names of the ABIs to sync for the given modules and variants. Use null to sync all ABIs.
         */
        val nativeAbis: List<String>? = null
    ): Serializable {
        companion object {
            @JvmStatic
            private val serialVersionUID: Long = 1L
        }
    }

    /**
     * Do not fail if there are sync issues.
     *
     * Equivalent to `ignoreSyncIssues(SyncIssue.SEVERITY_ERROR)`.
     */
    @JvmOverloads
    fun ignoreSyncIssues(severity: Int = SyncIssue.SEVERITY_ERROR): ModelBuilderV2 {
        maxSyncIssueSeverityLevel = severity
        return this
    }

    fun allowOptionWarning(option: Option<*>): ModelBuilderV2 {
        explicitlyAllowedOptions.add(option.propertyName)
        return this
    }

    /**
     * Fetches the model for each project and return them as a [ModelContainerV2]
     * @param variantName the name of the variant for which to return [VariantDependencies]
     * @param nativeParams the [NativeModuleParams] to configure the native model query
     */
    fun fetchModels(
        variantName: String? = null,
        parameterMutator: (ModelBuilderParameter) -> Unit = { it.buildAllRuntimeClasspaths() },
        nativeParams: NativeModuleParams? = null
    ): FetchResult<ModelContainerV2> {
        val container =
                assertNoSyncIssues(
                    buildModelV2(
                        GetAndroidModelV2Action(
                            variantName,
                            parameterMutator,
                            nativeParams
                        )
                    )
                )

        return FetchResult(
            container,
            normalizer = getFileNormalizer(container)
        )
    }

    /** Java interop friendly signature, as default parameters are not respected. */
    fun fetchModels() = fetchModels(null, parameterMutator = { it.buildAllRuntimeClasspaths() }, null)

    /** Java interop friendly signature, as default parameters are not respected. */
    fun fetchModels(
        variantName: String?,
        nativeParams: NativeModuleParams?
    ) = fetchModels(variantName, parameterMutator = { it.buildAllRuntimeClasspaths() }, nativeParams)

    /**
     * Fetches the [AndroidProject], [VariantDependencies] and [ProjectSyncIssues] for each project
     * and return them as a [ModelContainerV2]
     *
     * @param variantName the name of the variant for which to return [VariantDependencies]
     */
    fun fetchVariantDependencies(variantName: String): FetchResult<ModelContainerV2> =
            fetchModels(variantName = variantName, nativeParams = null)

    /**
     * Fetches the [AndroidProject], [NativeModule] and [ProjectSyncIssues] for each project and
     * return them as a [ModelContainerV2]

     * @param nativeParams the [NativeModuleParams] to configure the native model query
     * @return modules whose build information are generated
     */
    fun fetchNativeModules(nativeParams: NativeModuleParams): FetchResult<ModelContainerV2> =
            fetchModels(variantName = null, nativeParams = nativeParams)

    private fun getFileNormalizer(container: ModelContainerV2): FileNormalizerImpl {
        return FileNormalizerImpl(
            buildMap = container.buildMap,
            gradleUserHome = location.testLocation.gradleUserHome.toFile(),
            gradleCacheDir = location.testLocation.gradleCacheDir,
            androidSdkDir = gradleTestInfo.androidSdkDir,
            androidPrefsDir = preferencesRootDir,
            androidNdkSxSRoot = gradleTestInfo.androidNdkSxSRootSymlink,
            localRepos = GradleTestProject.localRepositories,
            additionalMavenRepo = gradleTestInfo.additionalMavenRepoDir,
            defaultNdkSideBySideVersion = DEFAULT_NDK_SIDE_BY_SIDE_VERSION
        )
    }

    /** Return a list of all task names of the project.  */
    fun fetchTaskList(): List<String> = fetchGradleProject().tasks.map { it.name }

    private fun fetchGradleProject(): GradleProject =
        projectConnection.model(GradleProject::class.java).withArguments(getArguments()).get()

    /**
     * Returns a project model for each sub-project;
     *
     * @param action the build action to gather the model
     */
    private fun <T> buildModelV2(action: BuildAction<T>): T {
        val executor = projectConnection.action(action)
        return buildModel(executor).first
    }

    /**
     * Returns a project model container and the build result.
     *
     *
     * Can be used both when just fetching models or when also scheduling tasks to be run.
     */
    private fun <T> buildModel(executor: BuildActionExecuter<T>): Pair<T, GradleBuildResult> {
        with(BooleanOption.IDE_BUILD_MODEL_ONLY_V2, true)
        with(BooleanOption.IDE_INVOKED_FROM_IDE, true)
        setJvmArguments(executor)

        val stdErrFile = File.createTempFile("stdOut", "log")
        val stdOutFile = File.createTempFile("stdErr", "log")
        val progressListener = CollectingProgressListener()
        executor.addProgressListener(progressListener, OperationType.TASK)
        return try {
            val model: T =
                BufferedOutputStream(FileOutputStream(stdOutFile)).use { stdout ->
                    BufferedOutputStream(FileOutputStream(stdErrFile)).use { stderr ->
                        setStandardOut(executor, stdout)
                        setStandardError(executor, stderr)
                        executor.withArguments(getArguments())
                        runBuild(executor) { executor: BuildActionExecuter<T>, resultHandler: ResultHandler<T> ->
                            executor.run(resultHandler)
                        }
                    }
                }

            val buildResult = GradleBuildResult(
                stdOutFile, stdErrFile, progressListener.getEvents(), null
            )

            lastBuildResultConsumer.accept(buildResult)

            model to buildResult
        } catch (e: GradleConnectionException) {
            lastBuildResultConsumer.accept(
                GradleBuildResult(stdOutFile, stdErrFile, progressListener.getEvents(), e)
            )
            maybePrintJvmLogs(e)
            throw e
        }
    }

    private fun assertNoSyncIssues(
        container: ModelContainerV2
    ): ModelContainerV2 {
        val allowedOptions: Set<String> =
            Sets.union(
                explicitlyAllowedOptions,
                optionPropertyNames
            )
        val errors = container.infoMaps
            .entries
            .asSequence()
            .flatMap { buildEntry: Map.Entry<String, Map<String, ModelInfo>> ->
                buildEntry
                    .value
                    .entries
                    .asSequence()
                    .map { projectEntry: Map.Entry<String, ModelInfo> ->
                        "${buildEntry.key}@@${projectEntry.key}" to removeAllowedIssues(projectEntry.value.issues?.syncIssues, allowedOptions)
                    }
            }
            .filter { it.second.isNotEmpty() }
            .map {
                "project ${it.first} has Sync Issues: ${it.second.joinToString(separator = ", ", prefix = "[", postfix = "]")} "
            }
            .toList()

        if (errors.isNotEmpty()) {
            Assert.fail(errors.joinToString(separator = "\n"))
        }

        return container
    }

    private fun removeAllowedIssues(
        issues: Collection<SyncIssue>?,
        allowedOptions: Set<String>
    ): List<SyncIssue> {
        if (issues == null) {
            return listOf()
        }
        return issues
            .asSequence()
            .filter { syncIssue: SyncIssue -> syncIssue.severity > maxSyncIssueSeverityLevel }
            .filter { syncIssue: SyncIssue -> syncIssue.type != SyncIssue.TYPE_DEPRECATED_DSL }
            .filter { syncIssue: SyncIssue ->
                (syncIssue.type
                        != SyncIssue.TYPE_UNSUPPORTED_PROJECT_OPTION_USE
                        || !allowedOptions.contains(syncIssue.data))
            }
            .toList()
    }
}

class FileNormalizerImpl(
    buildMap: Map<String, ModelContainerV2.BuildInfo>,
    gradleUserHome: File,
    gradleCacheDir: File,
    androidSdkDir: File?,
    androidPrefsDir: File?,
    androidNdkSxSRoot: File?,
    localRepos: List<Path>,
    additionalMavenRepo: Path?,
    defaultNdkSideBySideVersion: String,
    private val extraNormalizer: ((String) -> String)? = null
) : FileNormalizer {

    private data class RootData(
        val root: File,
        val varName: String,
        val stringModifier: ((String) -> String)? = null
    )

    private val rootDataList: List<RootData>

    init {
        val mutableList = mutableListOf<RootData>()

        // The order of the following must go from leaf to root.
        // So first we include the project themselves
        for ((buildName, buildInfo) in buildMap) {
            for ((projectPath, projectDir) in buildInfo.projects) {
                mutableList.add(
                    RootData(
                        File(File(projectDir, "build"), ".transforms"),
                        "BUILD_FOLDER($buildName|${projectPath})"
                    ) {
                        // Remove the actual checksum (size 32)
                        // incoming string is "XXXX/..." so removing XXX leaves a leading /
                        "{CHECKSUM}${it.substring(32)}"
                    }
                )
            }
        }

        // then the included builds
        for (buildInfo in buildMap.values.sortedByDescending { it.name }) {
            // skip the root project so that they appear as PROJECT instead of INCLUDED_BUILD(:)
            if (buildInfo.name != ModelContainerV2.ROOT_BUILD_ID) {
                mutableList.add(RootData(buildInfo.rootDir, "INCLUDED_BUILD(${buildInfo.name})"))
            }
        }

        // then the root build (in case the included ones are inside the root build.
        val rootBuildInfo = buildMap[ModelContainerV2.ROOT_BUILD_ID]
            ?: throw RuntimeException("Unable to find BuildInfo for root build")
        mutableList.add(RootData(rootBuildInfo.rootDir, "PROJECT"))

        mutableList.add(RootData(gradleCacheDir, "GRADLE_CACHE") {
            // Remove the actual checksum (size 32)
            // incoming string is "XXXX/..." so removing XXX leaves a leading /
            "{CHECKSUM}${it.substring(32)}"
        })
        mutableList.add(RootData(gradleUserHome, "GRADLE"))

        androidNdkSxSRoot?.resolve(defaultNdkSideBySideVersion)?.let {
            mutableList.add(RootData(it, "ANDROID_NDK"))
            // Some tools in NDK follows symbolic links. So do the same and add the real location
            // of NDK to the known roots.
            mutableList.add(
                RootData(
                    it.resolve("source.properties").canonicalFile.parentFile,
                    "ANDROID_NDK"
                )
            )
        }

        val defaultPlatformLocation =
            "platforms/android-${DEFAULT_COMPILE_SDK_VERSION}/"
        androidSdkDir?.let {
            mutableList.add(
                RootData(it, "ANDROID_SDK") { string ->
                    string.replace(defaultPlatformLocation, "platforms/android-{COMPILE_SDK_VERSION}/")
                }
            )
        }
        androidPrefsDir?.let {
            mutableList.add(RootData(it, "ANDROID_PREFS"))
        }

        // Sort by length to make sure that the longest path is always chosen first
        // (example: android_gradle_plugin vs android_gradle_plugin_runtime_dependencies)
        localRepos.asSequence().map { it.toFile() }.sortedByDescending {
            it.absolutePath.length
        }.forEach {
            mutableList.add(RootData(it, "LOCAL_REPO"))
        }

        additionalMavenRepo?.let {
            mutableList.add(RootData(it.toFile(), "ADDITIONAL_MAVEN_REPO"))
        }

        rootDataList = mutableList.toList()
    }

    override fun normalize(file: File): String {
        val filePath = rootDataList.firstNotNullOfOrNull {
            file.relativeToOrNull(it.root, it.varName, it.stringModifier)
        } ?: file.invariantSeparatorsPath

        val suffix = when {
            file.isFile -> "{F}"
            file.isDirectory -> "{D}"
            else -> "{!}"
        }

        return filePath.normalizeVersionsOfCommonDependencies() + suffix
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("Path variables:")
        val len = rootDataList.map { it.varName.length }.maxOrNull()?.dec() ?: 10
        for (rootData in rootDataList) {
            sb.append('\n')
            sb.append(rootData.varName)
            for (i in rootData.varName.length..len) {
                sb.append(' ')
            }

            sb.append(": ${rootData.root}")
        }

        return sb.toString()
    }

    override fun normalize(value: JsonElement): JsonElement = when (value) {
        is JsonNull -> value
        is JsonPrimitive -> when {
            value.isString -> JsonPrimitive(normalize(value.asString))
            else -> value
        }
        is JsonArray -> JsonArray().apply {
            value.map(::normalize).forEach(::add)
        }
        is JsonObject -> JsonObject().apply {
            value.entrySet().forEach { (key, value) ->
                add(key, normalize(value))
            }
        }
        else -> throw IllegalArgumentException("Unrecognized JsonElement")
    }

    private fun normalize(string: String): String {
        // On windows, various native tools use '\' and '/' interchangeably. Hence we unscrupulously
        // normalize them.
        var s = string.replace('\\', '/')
        for ((root, varName, modifier) in rootDataList) {
            val normalizedRootPath = root.absolutePath.replace('\\', '/')

            if (s.contains(normalizedRootPath)) {
                if (s.length == normalizedRootPath.length) {
                    return "{$varName}"
                }

                val stringAfter = s.substringAfter(normalizedRootPath).removePrefix("/")
                val stringBefore = s.substringBefore(normalizedRootPath)

                val modifiedStringAfter = modifier?.let {
                    modifier(stringAfter)
                } ?: stringAfter

                s = "$stringBefore{$varName}/$modifiedStringAfter"
            }
        }

        extraNormalizer?.let {
            s = it(s)
        }
        return s
    }
}

fun File.relativeToOrNull(
    root: File,
    varName: String,
    action: ((String) -> String)? = null
): String? {
    // check first that the file is inside the root, otherwise relativeToOrNull can still
    // return something that starts with a bunch of ../
    if (startsWith(root)) {
        val relativeFile = relativeToOrNull(root)
        if (relativeFile != null) {
            val osNormalizedString = if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
                relativeFile.toString().replace("\\", "/")
            } else {
                relativeFile.toString()
            }

            val finalString = if (action != null) {
                action(osNormalizedString)
            } else {
                osNormalizedString
            }

            return "{$varName}/$finalString"
        }
    }

    return null
}


fun ModelBuilderParameter.buildAllRuntimeClasspaths() {
    dontBuildRuntimeClasspath = false
    dontBuildUnitTestRuntimeClasspath = false
    dontBuildScreenshotTestRuntimeClasspath = false
    dontBuildAndroidTestRuntimeClasspath = false
    dontBuildTestFixtureRuntimeClasspath = false
    dontBuildHostTestRuntimeClasspath = mapOf("UnitTest" to false, "ScreenshotTest" to false)
}
