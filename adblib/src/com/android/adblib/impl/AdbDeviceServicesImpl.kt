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
package com.android.adblib.impl

import com.android.adblib.AdbChannel
import com.android.adblib.AdbDeviceServices
import com.android.adblib.AdbDeviceSyncServices
import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbServerChannelProvider
import com.android.adblib.AdbSession
import com.android.adblib.AdbSessionHost
import com.android.adblib.AppProcessEntry
import com.android.adblib.DeviceSelector
import com.android.adblib.ProcessIdList
import com.android.adblib.ReverseSocketList
import com.android.adblib.RootResult
import com.android.adblib.ShellCollector
import com.android.adblib.ShellV2Collector
import com.android.adblib.SocketSpec
import com.android.adblib.adbLogger
import com.android.adblib.forwardTo
import com.android.adblib.impl.StdoutByteBufferProcessor.DirectProcessor
import com.android.adblib.impl.StdoutByteBufferProcessor.StripCrLfProcessor
import com.android.adblib.impl.services.AdbServiceRunner
import com.android.adblib.impl.services.OkayDataExpectation
import com.android.adblib.impl.services.TrackAppService
import com.android.adblib.impl.services.TrackJdwpService
import com.android.adblib.read
import com.android.adblib.readRemaining
import com.android.adblib.utils.AdbProtocolUtils
import com.android.adblib.utils.AdbProtocolUtils.bufferToByteDumpString
import com.android.adblib.utils.ResizableBuffer
import com.android.adblib.utils.closeOnException
import com.android.adblib.utils.launchCancellable
import com.android.adblib.withPrefix
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.TimeUnit

private const val ABB_ARG_SEPARATOR = "\u0000"

internal class AdbDeviceServicesImpl(
  override val session: AdbSession,
  channelProvider: AdbServerChannelProvider,
  private val timeout: Long,
  private val unit: TimeUnit
) : AdbDeviceServices {

    private val logger = adbLogger(session.host)

    private val host: AdbSessionHost
        get() = session.host

    private val serviceRunner = AdbServiceRunner(session, channelProvider)
    private val trackJdwpService = TrackJdwpService(serviceRunner)
    private val trackAppService = TrackAppService(serviceRunner)
    private val myReverseSocketListParser = ReverseSocketListParser()

    override fun <T> shell(
        device: DeviceSelector,
        command: String,
        shellCollector: ShellCollector<T>,
        stdinChannel: AdbInputChannel?,
        commandTimeout: Duration,
        bufferSize: Int,
        shutdownOutput: Boolean,
        stripCrLf: Boolean
    ): Flow<T> {
        return runServiceWithOutput(
            device,
            ExecService.SHELL,
            { command },
            shellCollector,
            stdinChannel,
            commandTimeout,
            bufferSize,
            shutdownOutput,
            stripCrLf,
        )
    }

    override fun <T> exec(
        device: DeviceSelector,
        command: String,
        shellCollector: ShellCollector<T>,
        stdinChannel: AdbInputChannel?,
        commandTimeout: Duration,
        bufferSize: Int,
        shutdownOutput: Boolean
    ): Flow<T> {
        return runServiceWithOutput(
            device,
            ExecService.EXEC,
            { command },
            shellCollector,
            stdinChannel,
            commandTimeout,
            bufferSize,
            shutdownOutput,
            stripCrLf = false,
        )
    }

    override suspend fun rawExec(device: DeviceSelector, command: String): AdbChannel {
        // Note: We only track the time to launch the shell command, since command execution
        // itself can take an arbitrary amount of time.
        val timeout = TimeoutTracker(host.timeProvider, timeout, unit)
        val workBuffer = serviceRunner.newResizableBuffer()
        val service = getExecServiceString(ExecService.EXEC, command)
        val channel = serviceRunner.switchToTransport(device, workBuffer, service, timeout)
        channel.closeOnException {
            host.logger.info { "\"$service\" - sending local service request to ADB daemon, timeout: $timeout" }
            serviceRunner.sendAdbServiceRequest(channel, workBuffer, service, timeout)
            serviceRunner.consumeOkayFailResponse(
                device,
                service,
                channel,
                workBuffer,
                timeout
            )
        }
        return channel
    }

    override fun <T> shellV2(
        device: DeviceSelector,
        command: String,
        shellCollector: ShellV2Collector<T>,
        stdinChannel: AdbInputChannel?,
        commandTimeout: Duration,
        bufferSize: Int
    ): Flow<T> {
        return runServiceWithShellV2Collector(
            device,
            ExecService.SHELL_V2,
            { command },
            shellCollector,
            stdinChannel,
            commandTimeout,
            bufferSize
        )
    }

    private fun <T> runServiceWithShellV2Collector(
        device: DeviceSelector,
        execService: ExecService,
        commandProvider: () -> String,
        shellCollector: ShellV2Collector<T>,
        stdinChannel: AdbInputChannel?,
        commandTimeout: Duration,
        bufferSize: Int,
    ): Flow<T> {
        // By using a `channelFlow` here (as opposed to a simple `flow`), which always
        // has a buffer of at least one element, we ensure a `shellCollector` emitting
        // elements to the `channelFlow` does not prevent this coroutine from
        // running and collecting the output of the shell command.
        return channelFlow {
            val service = getExecServiceString(execService, commandProvider())
            logger.debug { "Device '${device}' - Start execution of service '$service' (bufferSize=$bufferSize bytes)" }

            // Note: We only track the time to launch the command, since the command execution
            // itself can take an arbitrary amount of time.
            val tracker = TimeoutTracker(host.timeProvider, timeout, unit)
            serviceRunner.runDaemonService(device, service, tracker) { channel, workBuffer ->
                host.timeProvider.withErrorTimeout(commandTimeout) {
                    // Forward `stdin` from channel to adb (in a new coroutine so that we
                    // can also collect `stdout` concurrently)
                    stdinChannel?.let {
                        launchCancellable {
                            forwardStdInputV2Format(channel, stdinChannel, bufferSize)
                        }
                    }

                    // Wraps our `ProducerScope` as `FlowCollector` so that the `shellCollector`
                    // is not aware of this implementation detail.
                    val producerScope = this@channelFlow
                    val flowCollector = FlowCollector<T> { value -> producerScope.send(value) }

                    // Forward `stdout` and `stderr` from adb to flow
                    collectShellCommandOutputV2Format(
                        channel,
                        workBuffer,
                        service,
                        shellCollector,
                        bufferSize,
                        flowCollector
                    )
                }
            }
        }.flowOn(host.ioDispatcher)
    }

    override suspend fun sync(device: DeviceSelector): AdbDeviceSyncServices {
        return AdbDeviceSyncServicesImpl.open(serviceRunner, device, timeout, unit)
    }

    override suspend fun reverseListForward(device: DeviceSelector): ReverseSocketList {
        // ADB Host code, service handler:
        // https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/adb.cpp;l=986
        // ADB client code:
        // https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/client/commandline.cpp;l=1876
        val tracker = TimeoutTracker(host.timeProvider, timeout, unit)
        val service = "reverse:list-forward"
        val data = serviceRunner.runDaemonQuery(device, service, tracker)
        return myReverseSocketListParser.parse(data)
    }

    override suspend fun reverseForward(
        device: DeviceSelector,
        remote: SocketSpec,
        local: SocketSpec,
        rebind: Boolean
    ): String? {
        val tracker = TimeoutTracker(host.timeProvider, timeout, unit)
        val service = "reverse:forward:" +
                (if (rebind) "" else "norebind:") +
                remote.toQueryString() +
                ";" +
                local.toQueryString()
        return serviceRunner.runDaemonQuery2(device, service, tracker, OkayDataExpectation.OPTIONAL)
    }

    override suspend fun reverseKillForward(device: DeviceSelector, remote: SocketSpec) {
        // ADB Host code, service handler:
        // https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/adb.cpp;l=1006
        // ADB client code:
        // https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/client/commandline.cpp;l=1895
        val tracker = TimeoutTracker(host.timeProvider, timeout, unit)
        val service = "reverse:killforward:${remote.toQueryString()}"
        serviceRunner.runDaemonQuery2(
            device,
            service,
            tracker,
            OkayDataExpectation.NOT_EXPECTED
        )
    }

    override suspend fun reverseKillForwardAll(device: DeviceSelector) {
        // ADB Host code, service handler:
        // https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/adb.cpp;l=996
        // ADB client code:
        // https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/client/commandline.cpp;l=1895
        val tracker = TimeoutTracker(host.timeProvider, timeout, unit)
        val service = "reverse:killforward-all"
        serviceRunner.runDaemonQuery2(
            device,
            service,
            tracker,
            OkayDataExpectation.NOT_EXPECTED
        )
    }

    override fun trackJdwp(device: DeviceSelector): Flow<ProcessIdList> {
        return trackJdwpService.invoke(device, timeout, unit)
    }

    override fun trackApp(device: DeviceSelector): Flow<List<AppProcessEntry>> {
        return trackAppService.invoke(device, timeout, unit)
    }

    override suspend fun jdwp(device: DeviceSelector, pid: Int): AdbChannel {
        val tracker = TimeoutTracker(host.timeProvider, timeout, unit)
        val service = "jdwp:$pid"
        return serviceRunner.startDaemonService(device, service, tracker)
    }

    override suspend fun root(device: DeviceSelector): RootResult {
        return rootImpl(device, "root:")
    }

    override suspend fun unRoot(device: DeviceSelector): RootResult {
        return rootImpl(device, "unroot:")
    }

    private suspend fun rootImpl(device: DeviceSelector, service: String): RootResult {
        val tracker = TimeoutTracker(host.timeProvider, timeout, unit)
        // ADB client code:
        // https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/client/commandline.cpp;l=1103
        // ADB Daemon code:
        // https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/daemon/services.cpp;l=282
        val workBuffer = serviceRunner.newResizableBuffer()
        return serviceRunner.startDaemonService(device, service, tracker, workBuffer).use { channel ->
            // The "root:" or "unroot:" services are a special case of services returning a UTF-8
            // string without any length prefix, so we just need to read bytes until EOF.
            workBuffer.clear()
            channel.readRemaining(workBuffer)
            val buffer = workBuffer.afterChannelRead(useMarkedPosition = false)
            val status = AdbProtocolUtils.byteBufferToString(buffer)
            RootResult(status)
        }.also {
            logger.debug { "${device.shortDescription} - \"$service\": $it" }
        }
    }

    override fun <T> abb_exec(
        device: DeviceSelector,
        args: List<String>,
        shellCollector: ShellCollector<T>,
        stdinChannel: AdbInputChannel?,
        commandTimeout: Duration,
        bufferSize: Int,
        shutdownOutput: Boolean
    ): Flow<T> {
        return runServiceWithOutput(
            device,
            ExecService.ABB_EXEC,
            { joinAbbArgs(args) },
            shellCollector,
            stdinChannel,
            commandTimeout,
            bufferSize,
            shutdownOutput,
            stripCrLf = false,
        )
    }

    override fun <T> abb(
        device: DeviceSelector,
        args: List<String>,
        shellCollector: ShellV2Collector<T>,
        stdinChannel: AdbInputChannel?,
        commandTimeout: Duration,
        bufferSize: Int,
    ): Flow<T> {
        return runServiceWithShellV2Collector(
            device,
            ExecService.ABB,
            { joinAbbArgs(args) },
            shellCollector,
            stdinChannel,
            commandTimeout,
            bufferSize
        )
    }

    private fun joinAbbArgs(abbArgs: List<String>): String {
        // Check there are no embedded "NUL" characters
        abbArgs.forEach {
            if (it.contains(ABB_ARG_SEPARATOR)) {
                throw IllegalArgumentException("ABB Exec command argument cannot contain NUL separator")
            }
        }

        // Join all arguments into a single string
        return abbArgs.joinToString(ABB_ARG_SEPARATOR)
    }

    private suspend fun <T> collectShellCommandOutput(
        channel: AdbChannel,
        workBuffer: ResizableBuffer,
        service: String,
        bufferSize: Int,
        stripCrLf: Boolean,
        shellCollector: ShellCollector<T>,
        flowCollector: FlowCollector<T>
    ) {
        logger.debug { "\"${service}\" - Collecting messages from shell command output" }
        shellCollector.start(flowCollector)
        val bufferProcessor = if (stripCrLf) StripCrLfProcessor() else DirectProcessor()
        while (true) {
            logger.verbose { "\"${service}\" - Waiting for next message from shell command output" }

            // Note: We use an infinite timeout here as shell commands can take arbitrary amount
            //       of time to execute and produce output.
            workBuffer.clear()
            val byteCount = channel.read(workBuffer.forChannelRead(bufferSize))
            if (byteCount < 0) {
                // We are done reading from this channel, but we may have left over
                // from our stdout byte processor
                val outputBuffer = bufferProcessor.convertBufferEnd()
                if (outputBuffer != null) {
                    shellCollector.collect(flowCollector, outputBuffer)
                }
                break
            }
            val buffer = workBuffer.afterChannelRead()
            assert(buffer.remaining() == byteCount)

            logger.verbose { "\"${service}\" - Emitting packet of $byteCount bytes: ${bufferToByteDumpString(buffer)}" }
            val outputBuffer = bufferProcessor.convertBuffer(buffer)
            shellCollector.collect(flowCollector, outputBuffer)
        }
        shellCollector.end(flowCollector)
        logger.debug { "\"${service}\" - Done collecting messages from shell command output" }
    }

    private suspend fun <T> collectShellCommandOutputV2Format(
        channel: AdbChannel,
        workBuffer: ResizableBuffer,
        service: String,
        shellCollector: ShellV2Collector<T>,
        bufferSize: Int,
        flowCollector: FlowCollector<T>
    ) {
        val logger = this.logger.withPrefix("\"${service}\" - ")
        logger.debug { "Collecting shell protocol packets" }
        shellCollector.start(flowCollector)
        // Note that we do not close `bufferInputChannel` as we don't want it to close the wrapped `channel`
        session.channelFactory.createBufferedInputChannel(
            channel,
            bufferSize,
            closeInputChannel = false
        ).use { bufferInputChannel ->
            val shellProtocol = ShellV2ProtocolReader(bufferInputChannel, workBuffer, bufferSize)

            while (true) {
                // Note: We use an infinite timeout here, as the only wait to end this request is to close
                //       the underlying ADB socket channel. This is by design.
                logger.debug { "Waiting for next shell protocol packet" }
                val packet = shellProtocol.readPacket()
                when (packet.kind) {
                    ShellV2PacketKind.STDOUT -> {
                        logger.verbose { "Received 'stdout' packet of ${packet.payloadLength} bytes" }
                        processAdbInputChannelSplits(workBuffer, bufferSize, packet.payload) { byteBuffer ->
                            logger.verbose { "Emitting ${byteBuffer.remaining()} bytes to 'stdout' flow collector" }
                            shellCollector.collectStdout(flowCollector, byteBuffer)
                        }
                    }

                    ShellV2PacketKind.STDERR -> {
                        logger.verbose { "Received 'stderr' packet of ${packet.payloadLength} bytes" }
                        processAdbInputChannelSplits(workBuffer, bufferSize, packet.payload) { byteBuffer ->
                            logger.verbose { "Emitting ${byteBuffer.remaining()} bytes to 'stderr' flow collector" }
                            shellCollector.collectStderr(flowCollector, byteBuffer)
                        }
                    }

                    ShellV2PacketKind.EXIT_CODE -> {
                        processAdbInputChannelSplits(workBuffer, bufferSize, packet.payload) { byteBuffer ->
                            // Ensure value is unsigned
                            val exitCode = byteBuffer.get().toInt() and 0xFF
                            logger.debug { "Received shell command exit code=${exitCode}, ending flow" }
                            shellCollector.end(flowCollector, exitCode)
                        }

                        // There should be no messages after the exit code
                        break
                    }

                    ShellV2PacketKind.STDIN,
                    ShellV2PacketKind.CLOSE_STDIN,
                    ShellV2PacketKind.WINDOW_SIZE_CHANGE,
                    ShellV2PacketKind.INVALID -> {
                        logger.warn("Skipping shell protocol packet (kind=\"${packet.kind}\")")
                    }
                }
            }
        }
        logger.debug { "Done collecting shell protocol packets" }
    }

    private suspend inline fun processAdbInputChannelSplits(
        workBuffer: ResizableBuffer,
        bufferSize: Int,
        payload: AdbInputChannel,
        block: (ByteBuffer) -> Unit
    ) {
        while (true) {
            workBuffer.clear()
            val count = payload.read(workBuffer.forChannelRead(bufferSize))
            if (count <= 0) {
                break
            }
            block(workBuffer.afterChannelRead())
        }
    }

    private suspend fun forwardStdInput(
        shellCommandChannel: AdbChannel,
        stdInput: AdbInputChannel,
        bufferSize: Int,
        shutdownOutput: Boolean
    ) {
        val workBuffer = serviceRunner.newResizableBuffer(bufferSize)
        stdInput.forwardTo(shellCommandChannel, workBuffer, bufferSize)
        if (shutdownOutput) {
            logger.debug { "forwardStdInput - input channel has reached EOF, sending EOF to shell host" }
            shellCommandChannel.shutdownOutput()
        }
    }

    private suspend fun forwardStdInputV2Format(
        deviceChannel: AdbChannel,
        stdInput: AdbInputChannel,
        bufferSize: Int
    ) {
        val workBuffer = serviceRunner.newResizableBuffer(bufferSize)
        val shellProtocol = ShellV2ProtocolWriter(deviceChannel, workBuffer)

        while (true) {
            // Reserve the bytes needed for the packet header
            val buffer = shellProtocol.prepareWriteBuffer(bufferSize)

            // Read data from stdin
            // Note: We use an infinite timeout here, as the only wait to end this request is to close
            //       the underlying ADB socket channel, or for `stdin` to reach EOF. This is by design.
            val byteCount = stdInput.read(buffer)
            if (byteCount < 0) {
                // EOF, job is finished
                shellProtocol.writePreparedBuffer(ShellV2PacketKind.CLOSE_STDIN)
                break
            }
            // Buffer contains packet header + data
            shellProtocol.writePreparedBuffer(ShellV2PacketKind.STDIN)
        }
    }

    private fun <T> runServiceWithOutput(
        device: DeviceSelector,
        execService: ExecService,
        commandProvider: () -> String,
        shellCollector: ShellCollector<T>,
        stdinChannel: AdbInputChannel?,
        commandTimeout: Duration,
        bufferSize: Int,
        shutdownOutput: Boolean,
        stripCrLf: Boolean
    ): Flow<T> {
        // By using a `channelFlow` here (as opposed to a simple `flow`), which always
        // has a buffer of at least one element, we ensure a `shellCollector` emitting
        // elements to the `channelFlow` does not prevent this coroutine from
        // running and collecting the output of the shell command.
        return channelFlow {
            val service = getExecServiceString(execService, commandProvider())
            logger.debug { "Device \"${device}\" - Start execution of service \"$service\" (bufferSize=$bufferSize bytes)" }

            // Note: We only track the time to launch the command, since the command execution
            // itself can take an arbitrary amount of time.
            val tracker = TimeoutTracker(host.timeProvider, timeout, unit)
            serviceRunner.runDaemonService(device, service, tracker) { channel, workBuffer ->
                host.timeProvider.withErrorTimeout(commandTimeout) {
                    // Forward `stdin` from channel to adb (in a new coroutine so that we
                    // can also collect `stdout` concurrently)
                    stdinChannel?.let {
                        launchCancellable {
                            forwardStdInput(channel, stdinChannel, bufferSize, shutdownOutput)
                        }
                    }

                    // Wraps our `ProducerScope` as `FlowCollector` so that the `shellCollector`
                    // is not aware of this implementation detail.
                    val producerScope = this@channelFlow
                    val flowCollector = FlowCollector<T> { value -> producerScope.send(value) }
                    collectShellCommandOutput(
                        channel,
                        workBuffer,
                        service,
                        bufferSize,
                        stripCrLf,
                        shellCollector,
                        flowCollector,
                    )
                }
            }
        }.flowOn(host.ioDispatcher)
    }

    private fun getExecServiceString(service: ExecService, command: String): String {
        // Shell service string can look like: shell[,arg1,arg2,...]:[command].
        // See https://cs.android.com/android/platform/superproject/+/fbe41e9a47a57f0d20887ace0fc4d0022afd2f5f:packages/modules/adb/client/commandline.cpp;l=594;drc=fbe41e9a47a57f0d20887ace0fc4d0022afd2f5f

        // We don't escape here, just like ssh(1). http://b/20564385.
        // See https://cs.android.com/android/platform/superproject/+/fbe41e9a47a57f0d20887ace0fc4d0022afd2f5f:packages/modules/adb/client/commandline.cpp;l=776
        return when (service) {
            ExecService.SHELL -> "shell:$command"
            ExecService.SHELL_V2 -> "shell,v2:$command"
            ExecService.EXEC -> "exec:$command"
            ExecService.ABB_EXEC -> "abb_exec:$command"
            ExecService.ABB -> "abb:$command"
        }
    }

    private enum class ExecService {
        SHELL,
        EXEC,
        SHELL_V2,
        ABB_EXEC,
        ABB
    }
}
