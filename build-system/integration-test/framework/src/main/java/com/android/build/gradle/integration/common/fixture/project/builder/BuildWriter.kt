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

package com.android.build.gradle.integration.common.fixture.project.builder

import org.gradle.internal.extensions.stdlib.capitalized

/**
 * An object that can write a Gradle build file.
 *
 * It contains a list of methods that can be called to write specific instructions.
 *
 * The API is not meant to write arbitrary strings into the file.
 */
interface BuildWriter: BooleanNameHandler {
    /** Assignment: a = b */
    fun set(name: String, value: Any?): BuildWriter

    /** Single argument method */
    fun method(name: String, singleParam: Any)
    /** Calls a method with the given parameters. */
    fun method(name: String, params: List<Any?>, isVarArg: Boolean)
    /** Calls a method with named parameters */
    fun method(name: String, params: List<Pair<String, Any>>)
    fun pluginId(id: String, version: String?, apply: Boolean = true)
    fun dependency(scope: String, value:Any)

    fun writeCollectionAddAll(name: String, items: Collection<*>)
    fun writeCollectionAdd(name:String, value: Any?)
    fun writeMapPutAll(name: String, items: Map<*,*>)
    fun writeMapPut(name: String, key: Any, value: Any?)

    /** Build a [RawString] from a string. */
    fun rawString(value: String): RawString
    /** Build a [RawString] from a method call. */
    fun rawMethod(name: String, vararg params: Any): RawString
    /** Build a [RawString] from a method call. */
    fun rawMethod(name: String, params: List<Pair<String, Any>>): RawString

    /** Writes a block with a sub item */
    fun <T> block(name: String, parameters: List<Any>, item: T, action: BuildWriter.(T) -> Unit): BuildWriter
    /** Writes a block with a sub item, and parameters passed to the block */
    fun <T> block(name: String, item: T, action: BuildWriter.(T) -> Unit): BuildWriter
    /** Writes a block without an item or parameters */
    fun block(name: String, action: BuildWriter.() -> Unit): BuildWriter

    /** Returns the file name of the build file for this writer */
    val buildFileName: String
    /** Returns the file name of the settings file for this writer */
    val settingsFileName: String

    /**
     * A String that is not quoted when written into the file
     */
    data class RawString(val value: String)
}

interface BooleanNameHandler {
    /**
     * Converts a property name to a boolean property name as needed
     */
    fun toIsBooleanName(name: String): String
}

/**
 * A class that handles writing indented lines into a file.
 *
 * It's not meant to be used directly. See [BaseBuildWriter]
 */
internal abstract class IndentHandler(protected val indentLevel: Int) {

    private val buffer = StringBuilder()
    private var lineEnded = true

    protected fun indent(): IndentHandler {
        if (!lineEnded) throw RuntimeException("BuildWriter Line not ended")
        lineEnded = false
        for (i in 1..indentLevel) {
            buffer.append(' ')
        }
        return this
    }

    fun endLine() {
        buffer.append('\n')
        lineEnded = true
    }

    fun put(value: String): IndentHandler {
        buffer.append(value)
        return this
    }

    fun put(value: Char): IndentHandler {
        buffer.append(value)
        return this
    }

    fun put(value: Boolean): IndentHandler {
        buffer.append(value)
        return this
    }

    fun put(value: Int): IndentHandler {
        buffer.append(value)
        return this
    }

    fun flatten(block: IndentHandler) {
        buffer.append(block.toString())
    }

    override fun toString(): String {
        if (!lineEnded) throw RuntimeException("BuildWriter Line not ended")
        return buffer.toString()
    }
}

/**
 * Base [BuildWriter] implementation over [IndentHandler].
 *
 * This is not meant to be used directly. see [KtsBuildWriter] or [GroovyBuildWriter].
 */
internal abstract class BaseBuildWriter(indentLevel: Int): IndentHandler(indentLevel), BuildWriter {

    protected abstract fun quoteString(value: String): String
    protected abstract fun newBuilder(indentLevel: Int): BaseBuildWriter

    protected fun Any?.toFormattedString(isVarArg: Boolean = false): String {

        // have to check this outside of when due to scope conflict between the
        // this for this method and the this of the builder.
        val enum = this?.javaClass?.isEnum ?: false
        if (enum) {
            return "${this?.javaClass?.typeName}.$this"
        }

        return when (this) {
            null -> "null"
            is String -> quoteString(this)
            is BuildWriter.RawString -> value
            is Array<*> -> {
                val allItems = joinToString(separator = ", ") { it ->
                    it.toFormattedString()
                }

                // var args, we're going to expect these to be at the end, and all we'll do
                // is add all of them
                if (!isVarArg) {
                    arrayOf(allItems)
                } else {
                    allItems
                }
            }
            else -> toString()

        }
    }

    override fun set(name: String, value: Any?): BuildWriter {
        indent().put(name).put(" = ").put(value.toFormattedString()).endLine()
        return this
    }

    override fun method(name: String, singleParam: Any) {
        indent()
            .put(name)
            .put('(')
            .put(singleParam.toFormattedString())
            .put(')')
            .endLine()
    }

    override fun method(name: String, params: List<Any?>, isVarArg: Boolean) {
        indent()
            .put(name)
            .put('(')
            .put(params.joinToString(separator = ", ") { it.toFormattedString(isVarArg) })
            .put(')')
            .endLine()
    }

    override fun method(name: String, params: List<Pair<String, Any>>) {
        val namedParams = toNamedParams(params)
        indent()
            .put(name)
            .put('(').put(namedParams.joinToString(separator = ", ")).put(')')
            .endLine()
    }

    override fun pluginId(id: String, version: String?, apply: Boolean) {
        val writer = indent()
            .put("id(")
            .put(quoteString(id))
            .put(')')

        version?.let {
            writer.put(" version ").put(quoteString(it))
        }

        if (!apply) {
            writer.put(" apply false")
        }

        writer.endLine()
    }

    override fun dependency(scope: String, value: Any) {
        method(scope, value)
    }

    abstract fun listOf(value: String): String
    abstract fun setOf(value: String): String
    abstract fun arrayOf(value: String): String
    abstract fun mapOf(value: Map<*, *>): String

    override fun writeCollectionAdd(name: String, value: Any?) {
        indent().put(name).put(" += ").put(value.toFormattedString()).endLine()
    }

    override fun writeCollectionAddAll(name: String, items: Collection<*>) {
        val writer = indent().put(name).put(" += ")

        val itemStr = items.joinToString(separator = ", ") { it ->
            it.toFormattedString()
        }

        when (items) {
            is List<*> -> {
                writer.put(listOf(itemStr))
            }
            is Set<*> -> {
                writer.put(setOf(itemStr))
            }
            else -> {
                throw RuntimeException("Unexpected collection type (${items.javaClass}) in writeListAddAll")
            }
        }

        writer.endLine()
    }

    override fun writeMapPut(name: String, key: Any, value: Any?) {
        indent()
            .put(name)
            .put("[").put(key.toFormattedString()).put("] = ")
            .put(value.toFormattedString()).endLine()
    }

    override fun writeMapPutAll(name: String, items: Map<*, *>) {
        indent().put(name).put(" += ").put(mapOf(items)).endLine()
    }

    override fun rawString(value: String) = BuildWriter.RawString(value)

    override fun rawMethod(name: String, vararg params: Any): BuildWriter.RawString {
        val sb = StringBuilder()
        sb.append(name).append('(').append(concatMethodParams(*params)).append(')')
        return BuildWriter.RawString(sb.toString())
    }

    override fun rawMethod(name: String, params: List<Pair<String, Any>>): BuildWriter.RawString {
        val namedParams = toNamedParams(params)

        val sb = StringBuilder()
        sb.append(name).append('(').append(namedParams.joinToString(separator = ", ")).append(')')
        return BuildWriter.RawString(sb.toString())
    }

    private fun concatMethodParams(vararg params: Any): String =
        params.joinToString(separator = ", ") { it ->
            it.toFormattedString()
        }

    abstract fun namedParam(name: String): String

    private fun toNamedParams(params: List<Pair<String, Any>>): List<String> {
        return params.map { it ->
            namedParam(it.first) + it.second.toFormattedString()
        }
    }

    override fun <T> block(
        name: String,
        parameters: List<Any>,
        item: T,
        action: BuildWriter.(T) -> Unit
    ): BuildWriter {
        val blockBuilder = newBuilder(indentLevel + 2)

        val writer = indent().put(name)

        if (parameters.isNotEmpty()) {
            writer
                .put('(')
                .put(parameters.joinToString(separator = ", ") { it.toFormattedString(false) })
                .put(')')
        }

        writer.put(" {").endLine()

        action(blockBuilder, item)
        flatten(blockBuilder)

        indent().put('}').endLine()
        return this
    }

    override fun <T> block(
        name: String,
        item: T,
        action: BuildWriter.(T) -> Unit
    ): BuildWriter {
        return block(name, listOf(), item, action)
    }

    override fun block(
        name: String,
        action: BuildWriter.() -> Unit
    ): BuildWriter {
        val blockBuilder = newBuilder(indentLevel + 2)

        indent().put(name).put(" {").endLine()
        action(blockBuilder)
        flatten(blockBuilder)

        indent().put('}').endLine()
        return this
    }
}

internal class KtsBuildWriter(indentLevel: Int = 0): BaseBuildWriter(indentLevel) {
    override fun newBuilder(indentLevel: Int): BaseBuildWriter = KtsBuildWriter(indentLevel)

    override fun quoteString(value: String): String {
        return "\"$value\""
    }

    override fun namedParam(name: String): String = "$name = "

    override fun listOf(value: String): String {
        return "listOf($value)"
    }

    override fun setOf(value: String): String {
        return "setOf($value)"
    }

    override fun arrayOf(value: String): String {
        return "arrayOf($value)"
    }

    override fun mapOf(value: Map<*, *>): String {
        val mapDeclarationContent = value.entries.joinToString(separator = ", ") { (key, value) ->
            "${key.toFormattedString()} to ${value.toFormattedString()}"
        }
        return "mapOf($mapDeclarationContent)"
    }

    override fun toIsBooleanName(name: String): String = "is${name.capitalized()}"


    override val buildFileName: String
        get() = "build.gradle.kts"
    override val settingsFileName: String
        get() = "settings.gradle.kts"
}

internal class GroovyBuildWriter(indentLevel: Int = 0): BaseBuildWriter(indentLevel) {
    override fun newBuilder(indentLevel: Int): BaseBuildWriter = GroovyBuildWriter(indentLevel)

    override fun quoteString(value: String): String {
        return "'$value'"
    }

    /**
     * This is actually a map notation. This is used only(?) for the project() call
     * in dependencies where it's a named param in KTS and a map in groovy.
     */
    override fun namedParam(name: String): String = "$name: "

    override fun listOf(value: String): String {
        return "[$value]"
    }

    override fun setOf(value: String): String {
        // FIXME?
        return "[$value]"
    }

    override fun arrayOf(value: String): String {
        return "[$value]"
    }

    override fun mapOf(value: Map<*, *>): String {
        val mapDeclarationContent = value.entries.joinToString(separator = ", ") { (key, value) ->
            "${key.toFormattedString()}: ${value.toFormattedString()}"
        }
        return "[$mapDeclarationContent]"
    }

    override fun toIsBooleanName(name: String): String = name

    override val buildFileName: String
        get() = "build.gradle"
    override val settingsFileName: String
        get() = "settings.gradle"

}
