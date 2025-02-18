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
import com.android.build.gradle.integration.common.fixture.model.SnapshotItemWriter.Companion.NULL_STRING
import com.android.build.gradle.internal.ide.dependencies.LOCAL_AAR_GROUPID
import com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.GRADLE_TEST_VERSION
import com.android.build.gradle.internal.ide.dependencies.LOCAL_ASAR_GROUPID
import com.android.testutils.TestUtils.KOTLIN_VERSION_FOR_TESTS
import java.io.File

/**
 * Main entry point of the snapshot feature.
 *
 * if a reference model is provided, only the properties that are different are snapshotted.
 *
 * Each instance only handles the direct properties of the provided model. For nested models,
 * new instances are created with their own matching reference model (if applicable)
 *
 * @param modelName the name of the root model
 * @param modelAction an action to get the model from a [ModelBuilderV2.FetchResult]
 * @param project the main [ModelBuilderV2.FetchResult]
 * @param referenceProject and optional reference [ModelBuilderV2.FetchResult]
 * @param action the action configuring a [ModelSnapshotter]
 *
 * @return the strings with the dumped model
 */
internal fun <ModelT> snapshotModel(
    modelName: String,
    modelAction: ModelContainerV2.() -> ModelT,
    project: ModelBuilderV2.FetchResult<ModelContainerV2>,
    referenceProject: ModelBuilderV2.FetchResult<ModelContainerV2>? = null,
    action: ModelSnapshotter<ModelT>.() -> Unit
): String {

    val projectSnapshotContainer = getSnapshotContainer(
        modelName,
        modelAction,
        project,
        action
    )

    val finalContainer = if (referenceProject != null) {
        val reference = getSnapshotContainer(
            modelName,
            modelAction,
            referenceProject,
            action
        )
        projectSnapshotContainer.subtract(reference)
    } else {
        projectSnapshotContainer
    }

    return if (finalContainer != null) {
        val writer = SnapshotItemWriter()
        writer.write(finalContainer)
    } else {
        ""
    }
}

private fun <ModelT> getSnapshotContainer(
    modelName: String,
    modelAction: ModelContainerV2.() -> ModelT,
    project: ModelBuilderV2.FetchResult<ModelContainerV2>,
    action: ModelSnapshotter<ModelT>.() -> Unit
): SnapshotContainer {

    val registrar =
            SnapshotItemRegistrarImpl(modelName, SnapshotContainer.ContentType.OBJECT_PROPERTIES)
    action(ModelSnapshotter(registrar, modelAction(project.container), project.normalizer))

    return registrar
}

internal fun <ModelT> checkEmptyDelta(
    modelName: String,
    modelAction: ModelContainerV2.() -> ModelT,
    project: ModelBuilderV2.FetchResult<ModelContainerV2>,
    referenceProject: ModelBuilderV2.FetchResult<ModelContainerV2>,
    action: ModelSnapshotter<ModelT>.() -> Unit,
    failureAction: (SnapshotContainer) -> Unit
) {

    val projectSnapshotContainer = getSnapshotContainer(
        modelName,
        modelAction,
        project,
        action
    )

    val referenceSnapshotContainer = getSnapshotContainer(
        modelName,
        modelAction,
        referenceProject,
        action
    )

    val diffSnapshotContainer = projectSnapshotContainer.subtract(referenceSnapshotContainer)

    if (diffSnapshotContainer != null && !diffSnapshotContainer.items.isNullOrEmpty()) {
        failureAction(diffSnapshotContainer)
    }
}

/**
 * Class providing method to snapshot the content of a model.
 *
 * This allows dumping basic property, list, etc.. but also mode complex objects.
 *
 * The instance is used only for the provided [model] object. Each new nested object
 * will use its own instance.
 */
class ModelSnapshotter<ModelT>(
    private val registrar: SnapshotItemRegistrar,
    private val model: ModelT,
    private val normalizer: FileNormalizer,
) {

    fun <PropertyT> item(
        name: String,
        propertyAction: ModelT.() -> PropertyT?,
        modifyAction: (PropertyT?) -> Any? = { it }
    ) {
        basicProperty(propertyAction, modifyAction) {
            registrar.item(name, it)
        }
    }

    fun value(propertyAction: ModelT.() -> Any?) {
        basicProperty(propertyAction) {
            registrar.value(it)
        }
    }

    fun pathAsAString(name: String, propertyAction: ModelT.() -> String?) {
        // Use the modifyAction to convert the String to a file so that the normalizer
        // can normalize it. Only do this if the path actually points to a file, which
        // may or may not exist. Otherwise, keep as string so that it does not get normalized
        basicProperty(propertyAction, { it?.let {
            val f = File(it)
            if (f.isAbsolute) {
                f
            } else {
                it
            }
        } }) {
            registrar.item(name, it)
        }
    }

    fun artifactKey(
        propertyAction: ModelT.() -> String,
    ): String = normalizeArtifactAddress(propertyAction(model)).also {
        registrar.item("key", it)
    }

    fun normalizeArtifactAddress(
        propertyAction: ModelT.() -> String,
    ): String = normalizeArtifactAddress(propertyAction(model))

    internal fun normalizeArtifactAddress(address: String): String {
        // normalize the value if it contains a local jar path,

        return if (address.startsWith(LOCAL_JAR_PREFIX)) {
            // extract the path. The format is __local_aars__|PATH|...
            // so we search for the 2nd | char
            val secondPipe = address.indexOf('|', LOCAL_JAR_PREFIX_LENGTH)

            val path = File(address.subSequence(LOCAL_JAR_PREFIX_LENGTH, secondPipe).toString())

            // reformat the address with the normalized path
            "$LOCAL_JAR_PREFIX${path.toNormalizedStrings(normalizer)}${address.subSequence(IntRange(secondPipe, address.length - 1))}"
        } else if (address.startsWith(LOCAL_ASAR_PREFIX)) {
            // extract the path. The format is __local_asars__|PATH|...
            // so we search for the 2nd | char
            val secondPipe = address.indexOf('|', LOCAL_ASAR_PREFIX_LENGTH)

            val path = File(address.subSequence(LOCAL_ASAR_PREFIX_LENGTH, secondPipe).toString())

            // reformat the address with the normalized path
            "$LOCAL_ASAR_PREFIX${path.toNormalizedStrings(normalizer)}${address.subSequence(IntRange(secondPipe, address.length - 1))}"
        } else {
            address
        }.normalizeVersionsOfCommonDependencies()
    }

    fun <PropertyT> list(
        name: String,
        propertyAction: (ModelT) -> Collection<PropertyT>?,
        sorted: Boolean = true,
        formatAction: (PropertyT.() -> String)? = null
    ) {
        val valueObject =
                propertyAction(model)
                    ?.format(formatAction)
                    ?.map { it.toValueString(normalizer) }
                    ?.let { if (sorted) it.sorted() else it }

        registrar.item(name, valueObject?.toString() ?: NULL_STRING)
    }

    fun <PropertyT> dataObject(
        name: String,
        propertyAction: (ModelT) -> PropertyT?,
        action: ModelSnapshotter<PropertyT>.() -> Unit
    ) {
        registrar.dataObject(name, propertyAction(model)) {
            action(
                ModelSnapshotter(
                    registrar = it,
                    model = this,
                    normalizer = normalizer,
                )
            )
        }
    }

    private fun <PropertyT> Collection<PropertyT>?.format(formatAction: (PropertyT.() -> String)?): Collection<Any?>? =
            formatAction?.let { this?.map(it)} ?: this

    /**
     * Displays a list on multiple lines
     *
     * If the list content is different from the referenceModel, the whole list is displayed
     */
    fun <PropertyT> valueList(
        name: String,
        propertyAction: ModelT.() -> Collection<PropertyT>?,
        formatAction: (PropertyT.() -> String)? = null,
        sortAction: (Collection<PropertyT>?) -> Collection<PropertyT>? = { it }
    ) {
        registrar.valueList(
            name,
            sortAction(propertyAction(model))
                .format(formatAction)
                ?.map { it.toNormalizedStrings(normalizer) })
    }

    fun <PropertyT> objectList(
        name: String,
        propertyAction: ModelT.() -> Collection<PropertyT>?,
        nameAction: PropertyT.(ModelSnapshotter<ModelT>) -> String,
        sortAction: (Collection<PropertyT>?) -> Collection<PropertyT>? = { it },
        action: ModelSnapshotter<PropertyT>.() -> Unit
    ) {
        val objects = sortAction(propertyAction(model))

        registrar.objectList(name, objects) { itemList ->
            for (item in itemList) {
                // no reference, just output the object
                dataObject(nameAction(item, this@ModelSnapshotter), item) { itemHolder ->
                    action(
                        ModelSnapshotter(
                            registrar = itemHolder,
                            model = this,
                            normalizer = normalizer,
                        )
                    )
                }
            }
        }
    }

    fun <PropertyT, ConvertedPropertyT> convertedObjectList(
        name: String,
        propertyAction: ModelT.() -> Collection<PropertyT>?,
        nameAction: PropertyT.(ModelSnapshotter<ModelT>) -> String,
        objectAction: PropertyT.() -> ConvertedPropertyT?,
        sortAction: (Collection<PropertyT>?) -> Collection<PropertyT>? = { it },
        action: ModelSnapshotter<ConvertedPropertyT>.() -> Unit
    ) {
        val objects = sortAction(propertyAction(model))

        registrar.objectList(name, objects) { itemList ->
            for (item in itemList) {
                dataObject(nameAction(item, this@ModelSnapshotter), objectAction(item)) { itemHolder ->
                    action(
                        ModelSnapshotter(
                            registrar = itemHolder,
                            model = this,
                            normalizer = normalizer,
                        )
                    )
                }
            }
        }
    }

    private fun <PropertyT> basicProperty(
        propertyAction: ModelT.() -> PropertyT?,
        modifyAction: (PropertyT?) -> Any? = { it },
        action: (String?) -> Unit
    ) {
        val property = propertyAction(model)
        if (property is Collection<*>) {
            throw RuntimeException("Do not call item/entry/value with collections. Use list instead")
        }

        val modifiedProperty = modifyAction(property)
        if (modifiedProperty is Collection<*>) {
            throw RuntimeException("Do not use a modifyAction that returns a Collection. Use List instead")
        }

        action(modifiedProperty.toValueString(normalizer))
    }
}

private const val LOCAL_JAR_PREFIX = "$LOCAL_AAR_GROUPID|"
private const val LOCAL_JAR_PREFIX_LENGTH = LOCAL_JAR_PREFIX.length
private const val LOCAL_ASAR_PREFIX = "$LOCAL_ASAR_GROUPID|"
private const val LOCAL_ASAR_PREFIX_LENGTH = LOCAL_ASAR_PREFIX.length

/**
 * Converts a value into a single String depending on its type (null, File, String, Collection, Any)
 */
fun Any?.toValueString(normalizer: FileNormalizer): String {
    fun Collection<*>.toValueString(normalizer: FileNormalizer): String {
        return this.map { it.toValueString(normalizer) }.toString()
    }

    return when (this) {
        null -> NULL_STRING
        is File -> normalizer.normalize(this)
        is Collection<*> -> toValueString(normalizer)
        is String -> "\"$this\""
        is Enum<*> -> this.name
        else -> toString()
    }
}

/**
 * Normalize an object, recursively if a collection.
 *
 * In case of a collection, still return a collection.
 */
fun Any?.toNormalizedStrings(normalizer: FileNormalizer): Any = when (this) {
    null -> NULL_STRING
    is File -> normalizer.normalize(this)
    is Collection<*> -> map { it.toNormalizedStrings(normalizer) }
    is String -> "\"$this\"".normalizeVersionsOfCommonDependencies()
    is Enum<*> -> name
    else -> toString()
}

/** Replaces versions of common dependencies (e.g., APG, KGP, Gradle) with placeholders. */
fun String.normalizeVersionsOfCommonDependencies(): String {
    return this
        .replace(ANDROID_GRADLE_PLUGIN_VERSION, "{AGP_Version}")
        .replace(KOTLIN_VERSION_FOR_TESTS, "{KOTLIN_VERSION_FOR_TESTS}")
        .replace(GRADLE_TEST_VERSION, "{GRADLE_VERSION}")
        .replace(
            "org.gradle.jvm.version>${Runtime.version().feature()}",
            "org.gradle.jvm.version>{Java_Version}"
        )
        .replace(
            "org.gradle.jvm.version -> ${Runtime.version().feature()}",
            "org.gradle.jvm.version -> {Java_Version}"
        )
}
