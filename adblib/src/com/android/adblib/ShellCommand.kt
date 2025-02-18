/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.adblib

import com.android.adblib.impl.InputChannelShellOutputImpl
import com.android.adblib.impl.LineCollector
import com.android.adblib.impl.channels.AdbOutputStreamChannel
import com.android.adblib.utils.AdbBufferDecoder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.time.Duration
import java.util.concurrent.TimeoutException

/**
 * Supports customization of various aspects of the execution of a shell command on a device,
 * including automatically falling back from [AdbDeviceServices.shellV2] to legacy protocols
 * on older devices.
 *
 * Once a [ShellCommand] is configured with various `withXxx` methods, use the [execute]
 * method to launch the shell command execution, returning a [Flow&lt;T&gt;][Flow].
 *
 * @see [AdbDeviceServices.shellCommand]
 * @see [AdbDeviceServices.shellV2]
 * @see [AdbDeviceServices.exec]
 * @see [AdbDeviceServices.shell]
 */
interface ShellCommand<T> {

    val session: AdbSession

    /**
     * Applies a [ShellV2Collector] to transfer the raw binary shell command output.
     * This change the type of this [ShellCommand] from [T] to the final target type [U].
     */
    fun <U> withCollector(collector: ShellV2Collector<U>): ShellCommand<U>

    /**
     * Applies a legacy [ShellCollector] to transfer the raw binary shell command output.
     * This change the type of this [ShellCommand] from [T] to the final target type [U].
     */
    fun <U> withLegacyCollector(collector: ShellCollector<U>): ShellCommand<U>

    /**
     * The [AdbInputChannel] to send to the device for `stdin`.
     *
     * The default value is `null`.
     */
    fun withStdin(stdinChannel: AdbInputChannel?): ShellCommand<T>

    /**
     * Applies a [timeout] that triggers [TimeoutException] exception if the shell command
     * does not terminate within the specified [Duration].
     *
     * The default value is [INFINITE_DURATION].
     */
    fun withCommandTimeout(timeout: Duration): ShellCommand<T>

    /**
     * Applies a [timeout] that triggers a [TimeoutException] exception when the command does
     * not generate any output (`stdout` or `stderr`) for the specified [Duration].
     *
     * The default value is [INFINITE_DURATION].
     */
    fun withCommandOutputTimeout(timeout: Duration): ShellCommand<T>

    /**
     * Overrides the default buffer size used for buffering `stdout`, `stderr` and `stdin`.
     *
     * The default value is [AdbLibProperties.DEFAULT_SHELL_BUFFER_SIZE].
     */
    fun withBufferSize(size: Int): ShellCommand<T>

    /**
     * Allows [execute] to use [AdbDeviceServices.shellV2] if available.
     *
     * The default value is `true`.
     */
    fun allowShellV2(value: Boolean): ShellCommand<T>

    /**
     * Allows [execute] to fall back to [AdbDeviceServices.exec] if [AdbDeviceServices.shellV2]
     * is not available or not allowed.
     *
     * The default value is `false`, because exec is not as general purpose as [allowShellV2]
     * and [allowLegacyExec] (see [AdbDeviceServices.exec] for documentation).
     */
    fun allowLegacyExec(value: Boolean): ShellCommand<T>

    /**
     * Allows [execute] to fall back to [AdbDeviceServices.shell] if [AdbDeviceServices.shellV2]
     * and [AdbDeviceServices.exec] are not available or not allowed.
     *
     * The default value is `true`.
     */
    fun allowLegacyShell(value: Boolean): ShellCommand<T>

    /**
     * Force [execute] to using [AdbDeviceServices.shellV2].
     *
     * The default value is `false`.
     */
    fun forceShellV2(): ShellCommand<T>

    /**
     * Force [execute] to using [AdbDeviceServices.exec].
     *
     * The default value is `false`.
     */
    fun forceLegacyExec(): ShellCommand<T>

    /**
     * Force [execute] to using [AdbDeviceServices.shell].
     *
     * The default value is `false`.
     */
    fun forceLegacyShell(): ShellCommand<T>

    /**
     * When [execute] falls back to using the `shell v1` or `exec` protocols, this method
     * allows to specify whether the device channel output is shutdown after piping `stdinChannel`.
     *
     * The default value is `true`.
     */
    fun shutdownOutputForLegacyShell(shutdownOutput: Boolean): ShellCommand<T>

    /**
     * When [execute] falls back to using the [AdbDeviceServices.shell] service,
     * and when the device API <= 23, this option allows [execute] to automatically
     * convert '\r\n' newlines (as emitted by [AdbDeviceServices.shell]) to '\n'.
     *
     * The default value is `true`.
     */
    fun allowStripCrLfForLegacyShell(value: Boolean): ShellCommand<T>

    /**
     * Allows overriding the shell command to [execute] on the device just before
     * execution starts, when the [Protocol] to be used is known.
     *
     * This can be useful, for example, for providing custom shell handling in case
     * [AdbDeviceServices.shellV2] is not supported and [execute] has to fall back to
     * [AdbDeviceServices.exec].
     *
     * The default value is a `no-op`.
     */
    fun withCommandOverride(commandOverride: (String, Protocol) -> String): ShellCommand<T>

    /**
     * Returns a [Flow] that executes the shell command on the device, according to the
     * various customization rules set by the `withXxx` methods.
     *
     * If [withCollector] or [withLegacyCollector] was not invoked before [execute],
     * an [IllegalArgumentException] is thrown, as a shell collector is mandatory.
     *
     * Once [execute] is called, further customization is not allowed.
     */
    fun execute(): Flow<T>

    /**
     * Execute the shell command on the device, assuming there is a single output
     * emitted by [ShellCommand.withCollector]. The single output is passed as
     * an argument to [block] **while the shell command is still active**.
     *
     * Note: This operator is reserved for [ShellV2Collector] that collect a single value.
     */
    suspend fun <R> executeAsSingleOutput(block: suspend (T) -> R): R

    /**
     * The protocol used for [executing][execute] a [ShellCommand]
     */
    enum class Protocol {

        SHELL_V2,
        SHELL,
        EXEC
    }
}

fun <T> ShellCommand<T>.withLineCollector(): ShellCommand<ShellCommandOutputElement> {
    return this.withCollector(LineShellV2Collector())
}

fun <T> ShellCommand<T>.withLineBatchCollector(): ShellCommand<BatchShellCommandOutputElement> {
    return this.withCollector(LineBatchShellV2Collector())
}

fun <T> ShellCommand<T>.withTextCollector(): ShellCommand<ShellCommandOutput> {
    return this.withCollector(TextShellV2Collector())
}

fun <T> ShellCommand<T>.withInputChannelCollector(): ShellCommand<InputChannelShellOutput> {
    return this.withCollector(InputChannelShellCollector(this.session))
}

/**
 * A [ShellCollector] implementation that concatenates the entire `stdout` into a single [String].
 *
 * Note: This should be used only if the output of a shell command is expected to be somewhat
 *       small and can easily fit into memory.
 */
class TextShellCollector(bufferCapacity: Int = 256)
    : ShellCollector<String>, ShellCollectorCapabilities {

    private val decoder = AdbBufferDecoder(bufferCapacity)

    /**
     * Characters accumulated during calls to [collectCharacters]
     */
    private val stringBuilder = StringBuilder()

    /**
     * We store the lambda in a field to avoid allocating an new lambda instance for every
     * invocation of [AdbBufferDecoder.decodeBuffer]
     */
    private val characterCollector = this::collectCharacters

    /**
     * See [ShellCollectorCapabilities.isSingleOutput]
     */
    override val isSingleOutput: Boolean
        get() = true

    override suspend fun start(collector: FlowCollector<String>) {
        // Nothing to do
    }

    override suspend fun collect(collector: FlowCollector<String>, stdout: ByteBuffer) {
        decoder.decodeBuffer(stdout, characterCollector)
    }

    override suspend fun end(collector: FlowCollector<String>) {
        collector.emit(stringBuilder.toString())
    }

    private fun collectCharacters(charBuffer: CharBuffer) {
        stringBuilder.append(charBuffer)
    }
}

/**
 * A [ShellV2Collector] implementation that concatenates the entire output (and `stderr`) of
 * the command execution into a single [ShellCommandOutput] instance
 *
 * Note: This should be used only if the output of a shell command is expected to be somewhat
 *       small and can easily fit into memory.
 */
class TextShellV2Collector(bufferCapacity: Int = 256)
    : ShellV2Collector<ShellCommandOutput>, ShellCollectorCapabilities {

    private val stdoutCollector = TextShellCollector(bufferCapacity)
    private val stderrCollector = TextShellCollector(bufferCapacity)
    private val stdoutFlowCollector = StringFlowCollector()
    private val stderrFlowCollector = StringFlowCollector()

    /**
     * See [ShellCollectorCapabilities.isSingleOutput]
     */
    override val isSingleOutput: Boolean
        get() = true

    override suspend fun start(collector: FlowCollector<ShellCommandOutput>) {
        stdoutCollector.start(stdoutFlowCollector)
        stderrCollector.start(stderrFlowCollector)
    }

    override suspend fun collectStdout(
        collector: FlowCollector<ShellCommandOutput>,
        stdout: ByteBuffer
    ) {
        stdoutCollector.collect(stdoutFlowCollector, stdout)
    }

    override suspend fun collectStderr(
        collector: FlowCollector<ShellCommandOutput>,
        stderr: ByteBuffer
    ) {
        stderrCollector.collect(stderrFlowCollector, stderr)
    }

    override suspend fun end(collector: FlowCollector<ShellCommandOutput>, exitCode: Int) {
        stdoutCollector.end(stdoutFlowCollector)
        stderrCollector.end(stderrFlowCollector)

        val result = ShellCommandOutput(
            stdoutFlowCollector.value ?: "",
            stderrFlowCollector.value ?: "",
            exitCode
        )
        collector.emit(result)
    }

    class StringFlowCollector : FlowCollector<String> {

        var value: String? = null

        override suspend fun emit(value: String) {
            this.value = value
        }
    }
}

/**
 * The result of [AdbDeviceServices.shellAsText]
 */
class ShellCommandOutput(
    /**
     * The shell command output ("stdout") captured as a single string.
     */
    val stdout: String,
    /**
     * The shell command error output ("stderr") captured as a single string, only set if
     * [ShellCommand.Protocol] is [ShellCommand.Protocol.SHELL_V2].
     *
     * @see ShellCommand.Protocol
     */
    val stderr: String,
    /**
     * The shell command exit code, only set if [ShellCommand.Protocol] is
     * [ShellCommand.Protocol.SHELL_V2].
     */
    val exitCode: Int
)

/**
 * A [ShellCollector] implementation that collects `stdout` as a sequence of lines
 */
class LineShellCollector(bufferCapacity: Int = 256) : ShellCollector<String> {

    private val decoder = AdbBufferDecoder(bufferCapacity)

    private val lineCollector = LineCollector()

    /**
     * We store the lambda in a field to avoid allocating a new lambda instance for every
     * invocation of [AdbBufferDecoder.decodeBuffer]
     */
    private val lineCollectorLambda: (CharBuffer) -> Unit = { lineCollector.collectLines(it) }

    override suspend fun start(collector: FlowCollector<String>) {
        // Nothing to do
    }

    override suspend fun collect(collector: FlowCollector<String>, stdout: ByteBuffer) {
        decoder.decodeBuffer(stdout, lineCollectorLambda)

        val lines = lineCollector.getLines()
        if (lines.isEmpty()) {
            return
        }

        // The following is intentionally a tail-call so that the current method does not need to allocate
        // a continuation state.
        emitLines(lines, collector)
    }

    private suspend fun emitLines(lines: List<String>, collector: FlowCollector<String>) {
        for (line in lines) {
            collector.emit(line)
        }
        lineCollector.clear()
    }

    override suspend fun end(collector: FlowCollector<String>) {
        collector.emit(lineCollector.getLastLine())
    }

}

/**
 * A [ShellV2Collector] implementation that collects `stdout` and `stderr` as sequences of
 * [text][String] lines
 */
class LineShellV2Collector(bufferCapacity: Int = 256) : ShellV2Collector<ShellCommandOutputElement> {

    private val stdoutCollector = LineShellCollector(bufferCapacity)
    private val stderrCollector = LineShellCollector(bufferCapacity)
    private val stdoutFlowCollector = LineFlowCollector { line ->
        ShellCommandOutputElement.StdoutLine(
            line
        )
    }
    private val stderrFlowCollector = LineFlowCollector { line ->
        ShellCommandOutputElement.StderrLine(
            line
        )
    }

    override suspend fun start(collector: FlowCollector<ShellCommandOutputElement>) {
        stdoutFlowCollector.forwardingFlowCollector = collector
        stdoutCollector.start(stdoutFlowCollector)

        stderrFlowCollector.forwardingFlowCollector = collector
        stderrCollector.start(stderrFlowCollector)
    }

    override suspend fun collectStdout(
        collector: FlowCollector<ShellCommandOutputElement>,
        stdout: ByteBuffer
    ) {
        stdoutFlowCollector.forwardingFlowCollector = collector
        stdoutCollector.collect(stdoutFlowCollector, stdout)
    }

    override suspend fun collectStderr(
        collector: FlowCollector<ShellCommandOutputElement>,
        stderr: ByteBuffer
    ) {
        stderrFlowCollector.forwardingFlowCollector = collector
        stderrCollector.collect(stderrFlowCollector, stderr)
    }

    override suspend fun end(collector: FlowCollector<ShellCommandOutputElement>, exitCode: Int) {
        stdoutFlowCollector.forwardingFlowCollector = collector
        stdoutCollector.end(stdoutFlowCollector)

        stderrFlowCollector.forwardingFlowCollector = collector
        stderrCollector.end(stderrFlowCollector)

        collector.emit(ShellCommandOutputElement.ExitCode(exitCode))
    }

    class LineFlowCollector(
        private val builder: (String) -> ShellCommandOutputElement
    ) : FlowCollector<String> {

        var forwardingFlowCollector: FlowCollector<ShellCommandOutputElement>? = null

        override suspend fun emit(value: String) {
            forwardingFlowCollector?.emit(builder(value))
        }
    }
}

/**
 * The base class of each entry of the [Flow] returned by [AdbDeviceServices.shellAsLines].
 */
sealed class ShellCommandOutputElement {

    /**
     * A `stdout` text line of the shell command.
     */
    class StdoutLine(val contents: String) : ShellCommandOutputElement() {

        // Returns the contents of the stdout line.
        override fun toString(): String = contents
    }

    /**
     * A `stderr` text line of the shell command.
     */
    class StderrLine(val contents: String) : ShellCommandOutputElement() {

        // Returns the contents of the stdout line.
        override fun toString(): String = contents
    }

    /**
     * The exit code of the shell command. This is always the last entry of the [Flow] returned by
     * [AdbDeviceServices.shellAsLines].
     */
    class ExitCode(val exitCode: Int) : ShellCommandOutputElement() {

        // Returns the exit code in a text form.
        override fun toString(): String = exitCode.toString()
    }
}

/**
 * A [ShellCollector] implementation that collects `stdout` as a sequence of lists of lines
 */
class LineBatchShellCollector(bufferCapacity: Int = 256) : ShellCollector<List<String>> {

    private val decoder = AdbBufferDecoder(bufferCapacity)

    private val lineCollector = LineCollector()

    /**
     * We store the lambda in a field to avoid allocating a new lambda instance for every
     * invocation of [AdbBufferDecoder.decodeBuffer]
     */
    private val lineCollectorLambda: (CharBuffer) -> Unit = { lineCollector.collectLines(it) }

    override suspend fun start(collector: FlowCollector<List<String>>) {
        // Nothing to do
    }

    override suspend fun collect(collector: FlowCollector<List<String>>, stdout: ByteBuffer) {
        decoder.decodeBuffer(stdout, lineCollectorLambda)
        val lines = lineCollector.getLines()
        if (lines.isNotEmpty()) {
            collector.emit(lines.toList())
            lineCollector.clear()
        }
    }

    override suspend fun end(collector: FlowCollector<List<String>>) {
        collector.emit(listOf(lineCollector.getLastLine()))
    }
}

/**
 * A [ShellV2Collector] implementation that collects `stdout` and `stderr` as sequences of
 * [text][String] lines
 */
class LineBatchShellV2Collector(bufferCapacity: Int = 256) : ShellV2Collector<BatchShellCommandOutputElement> {

    private val stdoutCollector = LineBatchShellCollector(bufferCapacity)
    private val stderrCollector = LineBatchShellCollector(bufferCapacity)
    private val stdoutFlowCollector = LineBatchFlowCollector { lines ->
        BatchShellCommandOutputElement.StdoutLine(
            lines
        )
    }
    private val stderrFlowCollector = LineBatchFlowCollector { lines ->
        BatchShellCommandOutputElement.StderrLine(
            lines
        )
    }

    override suspend fun start(collector: FlowCollector<BatchShellCommandOutputElement>) {
        stdoutFlowCollector.forwardingFlowCollector = collector
        stdoutCollector.start(stdoutFlowCollector)

        stderrFlowCollector.forwardingFlowCollector = collector
        stderrCollector.start(stderrFlowCollector)
    }

    override suspend fun collectStdout(
        collector: FlowCollector<BatchShellCommandOutputElement>,
        stdout: ByteBuffer
    ) {
        stdoutFlowCollector.forwardingFlowCollector = collector
        stdoutCollector.collect(stdoutFlowCollector, stdout)
    }

    override suspend fun collectStderr(
        collector: FlowCollector<BatchShellCommandOutputElement>,
        stderr: ByteBuffer
    ) {
        stderrFlowCollector.forwardingFlowCollector = collector
        stderrCollector.collect(stderrFlowCollector, stderr)
    }

    override suspend fun end(
        collector: FlowCollector<BatchShellCommandOutputElement>,
        exitCode: Int
    ) {
        stdoutFlowCollector.forwardingFlowCollector = collector
        stdoutCollector.end(stdoutFlowCollector)

        stderrFlowCollector.forwardingFlowCollector = collector
        stderrCollector.end(stderrFlowCollector)

        collector.emit(BatchShellCommandOutputElement.ExitCode(exitCode))
    }

    class LineBatchFlowCollector(
        private val builder: (List<String>) -> BatchShellCommandOutputElement
    ) : FlowCollector<List<String>> {

        var forwardingFlowCollector: FlowCollector<BatchShellCommandOutputElement>? = null

        override suspend fun emit(value: List<String>) {
            forwardingFlowCollector?.emit(builder(value))
        }
    }
}

/**
 * The base class of each entry of the [Flow] returned by [AdbDeviceServices.shellAsLineBatches].
 */
sealed class BatchShellCommandOutputElement {

    /**
     * A `stdout` text lines of the shell command.
     */
    class StdoutLine(val lines: List<String>) : BatchShellCommandOutputElement()

    /**
     * A `stderr` text lines of the shell command.
     */
    class StderrLine(val lines: List<String>) : BatchShellCommandOutputElement()

    /**
     * The exit code of the shell command. This is always the last entry of the [Flow] returned by
     * [AdbDeviceServices.shellAsLineBatches].
     */
    class ExitCode(val exitCode: Int) : BatchShellCommandOutputElement() {

        // Returns the exit code in a text form.
        override fun toString(): String = exitCode.toString()
    }
}

/**
 * A [ShellV2Collector] that exposes the output of a [shellCommand] as a [InputChannelShellOutput],
 * itself exposing `stdout`, `stderr` as [AdbInputChannel] instances.
 */
class InputChannelShellCollector(
    session: AdbSession,
    bufferSize: Int = DEFAULT_BUFFER_SIZE
) : ShellV2Collector<InputChannelShellOutput>, ShellCollectorCapabilities {

    private val logger = adbLogger(session)

    private val shellOutput = InputChannelShellOutputImpl(session, bufferSize)

    /**
     * See [ShellCollectorCapabilities.isSingleOutput]
     */
    override val isSingleOutput: Boolean
        get() = true

    override suspend fun start(collector: FlowCollector<InputChannelShellOutput>) {
        collector.emit(shellOutput)
    }

    override suspend fun collectStdout(
        collector: FlowCollector<InputChannelShellOutput>,
        stdout: ByteBuffer
    ) {
        while (stdout.remaining() > 0) {
            logger.verbose { "collectStdout(${stdout.remaining()})" }
            shellOutput.writeStdout(stdout)
        }
        logger.verbose { "collectStdout: done" }
    }

    override suspend fun collectStderr(
        collector: FlowCollector<InputChannelShellOutput>,
        stderr: ByteBuffer
    ) {
        while (stderr.remaining() > 0) {
            logger.verbose { "collectStderr(${stderr.remaining()})" }
            shellOutput.writeStderr(stderr)
        }
        logger.verbose { "collectStderr: done" }
    }

    override suspend fun end(collector: FlowCollector<InputChannelShellOutput>, exitCode: Int) {
        logger.verbose { "end(exitCode=$exitCode)" }
        shellOutput.end(exitCode)
    }
}

/**
 * A [ShellV2Collector] that forwards the output of a [shellCommand] to [OutputStream]s.
 *
 * The streams provided are not closed by the collector.
 *
 * The collector flow emits a single [Int] value representing the exit code of the command.
 *
 * @param adbSession An [AdbSession]
 * @param stdoutStream An [OutputStream] where stdout is written to
 * @param stderrStream An [OutputStream] where stderr is written to
 */
class OutputStreamCollector(
    adbSession: AdbSession,
    stdoutStream: OutputStream,
    stderrStream: OutputStream,
) : ShellV2Collector<Int> {

    private val stdoutChannel = AdbOutputStreamChannel(adbSession, stdoutStream)
    private val stderrChannel = AdbOutputStreamChannel(adbSession, stderrStream)

    override suspend fun start(collector: FlowCollector<Int>) {
    }

    override suspend fun collectStdout(collector: FlowCollector<Int>, stdout: ByteBuffer) {
        stdoutChannel.writeExactly(stdout)
    }

    override suspend fun collectStderr(collector: FlowCollector<Int>, stderr: ByteBuffer) {
        stderrChannel.writeExactly(stderr)
    }

    override suspend fun end(collector: FlowCollector<Int>, exitCode: Int) {
        collector.emit(exitCode)
    }
}

/**
 * The [shellCommand] output when using the [InputChannelShellCollector] collector.
 */
interface InputChannelShellOutput {

    /**
     * An [AdbInputChannel] to read the contents of `stdout`. Once the shell command
     * terminates, [stdout] reaches EOF.
     */
    val stdout: AdbInputChannel

    /**
     * An [AdbInputChannel] to read the contents of `stdout`. Once the shell command
     * terminates, [stdout] reaches EOF.
     */
    val stderr: AdbInputChannel

    /**
     * A [StateFlow] for the exit code of the shell command.
     * * While the command is still running, the value is `null`.
     * * Once the command terminates, the value is set to the actual
     *   (and final) exit code.
     */
    val exitCode: StateFlow<Int?>
}
