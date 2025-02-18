/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.integration.common.fixture.dsl

import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.ProductFlavor
import com.android.build.gradle.integration.common.fixture.project.builder.BooleanNameHandler
import com.android.build.gradle.integration.common.fixture.project.builder.BuildWriter
import org.gradle.api.provider.Property

/**
 * Class that contains the actual content of a DSL class that's generated on the fly.
 */
interface DslContentHolder {
    val name: String

    /**
     * Records a = b.
     *
     * For boolean, see [setBoolean]
     */
    fun set(name: String, value: Any?, parentChain: List<String> = listOf())

    /**
     * Records a = (boolean)
     *
     * This handles the case where the boolean is call `isName` because this is written
     * differently in kts and groovy file.
     */
    fun setBoolean(
        name: String,
        value: Any?,
        usingIsNotation: Boolean,
        parentChain: List<String> = listOf()
    )

    fun call(name: String, args: List<Any?>, isVarArgs: Boolean, parentChain: List<String> = listOf())

    /**
     * Records Collection.addAll
     */
    fun collectionAddAll(
        name: String,
        value: Collection<Any?>?,
        parentChain: List<String> = listOf()
    )
    /**
     * Records Collection.add
     */
    fun collectionAdd(name: String, value: Any?, parentChain: List<String> = listOf())

    /**
     * Records Map.putAll
     */
    fun mapPutAll(
        name: String,
        value: Map<out Any?, Any?>,
        parentChain: List<String> = listOf()
    )

    /**
     * Records Map.put
     */
    fun mapPut(name: String, key: Any, value: Any?, parentChain: List<String> = listOf())

    /**
     * Returns a proxied [MutableList]
     */
    fun getList(name: String): MutableList<*>

    /**
     * Returns a proxied [MutableSet]
     */
    fun getSet(name: String): MutableSet<*>

    /**
     * Returns a proxied [MutableMap]
     */
    fun getMap(name: String): MutableMap<*,*>

    /**
     * Returns a proxied Gradle Property
     */
    fun getProperty(name: String): Property<Any>

    fun <T : BuildType> buildTypes(
        theInterface: Class<T>,
        parentChain: List<String> = listOf(),
        action: NamedDomainObjectContainerProxy<T>.() -> Unit,
    )

    fun <T : ProductFlavor> productFlavors(
        theInterface: Class<T>,
        parentChain: List<String> = listOf(),
        action: NamedDomainObjectContainerProxy<T>.() -> Unit,
    )

    /**
     * records a nested block. The block must be run as part of `action`
     */
    fun <T> runNestedBlock(
        name: String,
        parameters: List<Any>,
        theInterface: Class<T>,
        parentChain: List<String> = listOf(),
        action: T.() -> Unit,
    ): T

    /**
     * Create an instance of T via a chained proxy.
     *
     * This method is used to handle getters on nested blocks, to handle things like
     *
     * ```
     * person {
     *   address.city = "Mountain View"
     * }
     * ```
     *
     * In this case the proxy for `person`, will call [chainedProxy] to instantiate the result
     * of `getAddress()`.
     *
     * The `Address` instance` is returned as a [DslProxy] using a [ChainedDslContentHolder]
     * that sends its event to the parent holder (ie the `person` object).
     *
     * @param name the name of the nested property in the parent object
     * @param theInterface the type of the nested property in the parent object.
     */
    fun <T> chainedProxy(name: String, theInterface: Class<T>): T

    // returns the content to write the
    fun writeContent(writer: BuildWriter)
}

internal class DefaultDslContentHolder(override val name: String = ""): DslContentHolder {

    enum class EventType {
        ASSIGNMENT,
        CALL,
        NESTED_BLOCK,
        COLLECTION_ADD_ALL,
        COLLECTION_ADD,
        MAP_PUT_ALL,
        MAP_PUT,
    }

    data class Event(
        val type: EventType,
        val payload: NamedPayload,
        val parentChain: List<String> = listOf()
    )

    interface NamedPayload {
        val name: String

        fun nameWithParents(parentChain: List<String>, booleanNameHandler: BooleanNameHandler): String =
            computeParentChain(name, parentChain)
    }

    open class NamedData(
        override val name: String,
        val value: Any? = null
   ): NamedPayload {
        override fun toString(): String {
            return "NamedData(name='$name', value=$value)"
        }
    }

    data class MethodInfo(
        override val name: String,
        val args: List<Any?>,
        val isVarArgs: Boolean
    ): NamedPayload

    class BooleanData(
        name: String,
        value: Any?,
        private val usingIsNotation: Boolean
    ): NamedData(name, value) {

        override fun nameWithParents(
            parentChain: List<String>,
            booleanNameHandler: BooleanNameHandler
        ): String {
            // need to convert the name with isX as needed
            val propName =
                if (usingIsNotation) booleanNameHandler.toIsBooleanName(name) else name

            return computeParentChain(propName, parentChain)
        }

        override fun toString(): String {
            return "BooleanData(usingIsNotation=$usingIsNotation) ${super.toString()}"
        }
    }

    data class NestedBlockData(
        override val name: String,
        val contentHolder: DslContentHolder,
        val args: List<Any>
    ): NamedPayload

    private val eventList = mutableListOf<Event>()

    override fun set(name: String, value: Any?, parentChain: List<String>) {
        eventList += Event(EventType.ASSIGNMENT, NamedData(name, value), parentChain)
    }

    override fun setBoolean(
        name: String,
        value: Any?,
        usingIsNotation: Boolean,
        parentChain: List<String>
    ) {
        eventList += Event(EventType.ASSIGNMENT, BooleanData(name, value, usingIsNotation), parentChain)
    }

    override fun call(name: String, args: List<Any?>, isVarArgs: Boolean, parentChain: List<String>) {
        eventList += Event(EventType.CALL, MethodInfo(name, args, isVarArgs), parentChain)
    }

    override fun getList(name: String): MutableList<*> {
        return ListProxy<Any>(name, this)
    }

    override fun getSet(name: String): MutableSet<*> {
        return SetProxy<Any>(name, this)
    }

    override fun getMap(name: String): MutableMap<*, *> {
        return MapProxy<Any,Any>(name, this)
    }

    override fun getProperty(name: String): Property<Any> {
        return PropertyProxy<Any>(name, this)
    }

    override fun collectionAddAll(
        name: String,
        value: Collection<Any?>?,
        parentChain: List<String>
    ) {
        eventList += Event(EventType.COLLECTION_ADD_ALL, NamedData(name, value), parentChain)
    }

    override fun collectionAdd(name: String, value: Any?, parentChain: List<String>) {
        eventList += Event(EventType.COLLECTION_ADD, NamedData(name, value), parentChain)
    }

    override fun mapPutAll(name: String, value: Map<out Any?, Any?>, parentChain: List<String>) {
        eventList += Event(EventType.MAP_PUT_ALL, NamedData(name, value), parentChain)
    }

    override fun mapPut(name: String, key: Any, value: Any?, parentChain: List<String>) {
        eventList += Event(EventType.MAP_PUT, NamedData(name, key to value), parentChain)
    }

    override fun <T : BuildType> buildTypes(
        theInterface: Class<T>,
        parentChain: List<String>,
        action: NamedDomainObjectContainerProxy<T>.() -> Unit
    ) {
        runNestedBlock(
            name = "buildTypes",
            parameters = listOf(),
            instanceProvider = {
                NamedDomainObjectContainerProxy<T>(theInterface, it)
            },
            parentChain = parentChain,
            action = action,
        )
    }

    override fun <T : ProductFlavor> productFlavors(
        theInterface: Class<T>,
        parentChain: List<String>,
        action: NamedDomainObjectContainerProxy<T>.() -> Unit
    ) {
        runNestedBlock(
            name = "productFlavors",
            parameters = listOf(),
            instanceProvider = {
                NamedDomainObjectContainerProxy<T>(theInterface, it)
            },
            parentChain = parentChain,
            action = action,
        )
    }

    override fun <T> runNestedBlock(
        name: String,
        parameters: List<Any>,
        theInterface: Class<T>,
        parentChain: List<String>,
        action: T.() -> Unit,
    ): T {
        return runNestedBlock(
            name = name,
            parameters = parameters,
            instanceProvider = {
                DslProxy.createProxy(theInterface, it)
            },
            parentChain = parentChain,
            action = action,
        )
    }

    // for testing
    internal fun <T> runNestedBlock(
        name: String,
        parameters: List<Any>,
        instanceProvider: (DslContentHolder) -> T,
        parentChain: List<String> = listOf(),
        action: T.() -> Unit,
    ): T {
        val contentHolder = DefaultDslContentHolder(name)

        val instance = instanceProvider(contentHolder)
        action(instance)

        eventList += Event(
            EventType.NESTED_BLOCK,
            NestedBlockData(name, contentHolder, parameters),
            parentChain
        )

        return instance
    }

    override fun <T> chainedProxy(name: String, theInterface: Class<T>): T =
        DslProxy.createProxy(theInterface, ChainedDslContentHolder(name, this))

    override fun writeContent(writer: BuildWriter) {
        for (event in eventList) {
            val nameWithParents = event.payload.nameWithParents(event.parentChain, writer)
            when (event.type) {
                EventType.ASSIGNMENT -> {
                    val payload = event.payload as NamedData
                    writer.set(nameWithParents, payload.value)
                }
                EventType.CALL -> {
                    val info = event.payload as MethodInfo
                    writer.method(nameWithParents, info.args, info.isVarArgs)
                }
                EventType.NESTED_BLOCK -> {
                    val data = event.payload as NestedBlockData
                    writer.block(
                        computeParentChain(data.contentHolder.name, event.parentChain),
                        data.args,
                        data.contentHolder
                    ) {
                        it.writeContent(this)
                    }
                }
                EventType.COLLECTION_ADD -> {
                    val info = event.payload as NamedData
                    writer.writeCollectionAdd(nameWithParents, info.value)
                }
                EventType.COLLECTION_ADD_ALL -> {
                    val info = event.payload as NamedData
                    writer.writeCollectionAddAll(nameWithParents, info.value as Collection<*>)
                }
                EventType.MAP_PUT -> {
                    val info = event.payload as NamedData
                    @Suppress("UNCHECKED_CAST")
                    val pair = info.value as Pair<Any, Any?>
                    writer.writeMapPut(nameWithParents, pair.first, pair.second)
                }
                EventType.MAP_PUT_ALL -> {
                    val info = event.payload as NamedData
                    writer.writeMapPutAll(nameWithParents, info.value as Map<*,*>)
                }
                else -> throw RuntimeException("Unsupported EventType: ${event.type}")
            }
        }
    }
}

private fun computeParentChain(name: String, parents: List<String>): String =
    if (parents.isEmpty()) {
        name
    } else {
        parents.joinToString(separator = ".") + "." + name
    }

/**
 * A [DslContentHolder] that does not actually hold content. It delegate all the operation
 * to its parent holder.
 *
 * This allows handling pattern like:
 *
 * ```
 * person {
 *   name = 'foo'
 *   address.city = "Mountain View"
 * }
 * ```
 *
 * In the example above, getAddress() will use a [ChainedDslContentHolder].
 *
 * setCity on the chained holder, will pass it back to its parent with parentChain containing
 * `address` which will be transformed to a `set("address.city", "value")
 */
internal class ChainedDslContentHolder(
    override val name: String,
    private val parent: DslContentHolder
): DslContentHolder {

    override fun set(name: String, value: Any?, parentChain: List<String>) {
        parent.set(name, value, parentChain + this.name)
    }

    override fun setBoolean(
        name: String,
        value: Any?,
        isNotation: Boolean,
        parentChain: List<String>
    ) {
        parent.setBoolean(name, value, isNotation, parentChain + this.name)
    }

    override fun call(name: String, args: List<Any?>, isVarArgs: Boolean, parentChain: List<String>) {
        parent.call(name, args, isVarArgs, parentChain + this.name)
    }

    override fun collectionAddAll(name: String, value: Collection<Any?>?, parentChain: List<String>) {
        parent.collectionAddAll(name, value, parentChain + this.name)
    }

    override fun collectionAdd(name: String, value: Any?, parentChain: List<String>) {
        parent.collectionAdd(name, value, parentChain + this.name)
    }

    override fun mapPutAll(name: String, value: Map<out Any?, Any?>, parentChain: List<String>) {
        parent.mapPutAll(name, value, parentChain + this.name)
    }

    override fun mapPut(name: String, key: Any, value: Any?, parentChain: List<String>) {
        parent.mapPut(name, key, value, parentChain + this.name)
    }

    override fun getList(name: String): MutableList<*> {
        return ListProxy<Any>(name, this)
    }

    override fun getSet(name: String): MutableSet<*> {
        return SetProxy<Any>(name, this)
    }

    override fun getMap(name: String): MutableMap<*, *> {
        return MapProxy<Any,Any>(name, this)
    }

    override fun getProperty(name: String): Property<Any> {
        return PropertyProxy(name, this)
    }

    override fun <T : BuildType> buildTypes(
        theInterface: Class<T>,
        parentChain: List<String>,
        action: NamedDomainObjectContainerProxy<T>.() -> Unit
    ) {
        parent.buildTypes(theInterface, parentChain + this.name, action)
    }

    override fun <T : ProductFlavor> productFlavors(
        theInterface: Class<T>,
        parentChain: List<String>,
        action: NamedDomainObjectContainerProxy<T>.() -> Unit
    ) {
        parent.productFlavors(theInterface, parentChain + this.name, action)
    }

    override fun <T> runNestedBlock(
        name: String,
        parameters: List<Any>,
        theInterface: Class<T>,
        parentChain: List<String>,
        action: T.() -> Unit,
    ): T {
        return parent.runNestedBlock(
            name,
            parameters,
            theInterface,
            parentChain + this.name,
            action)
    }

    override fun <T> chainedProxy(name: String, theInterface: Class<T>): T =
        DslProxy.createProxy(theInterface, ChainedDslContentHolder(name, this))

    override fun writeContent(writer: BuildWriter) {
        throw RuntimeException("ChainedDslContentHolder do not write their own content")
    }
}
