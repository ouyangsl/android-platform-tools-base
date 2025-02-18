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
package com.android.adblib.tools.debugging.impl

import com.android.adblib.AdbFailResponseException
import com.android.adblib.AdbSession
import com.android.adblib.AdbUsageTracker
import com.android.adblib.ByteBufferAdbOutputChannel
import com.android.adblib.ConnectedDevice
import com.android.adblib.adbLogger
import com.android.adblib.property
import com.android.adblib.tools.AdbLibToolsProperties.PROCESS_PROPERTIES_COLLECTOR_DELAY_DEFAULT
import com.android.adblib.tools.AdbLibToolsProperties.PROCESS_PROPERTIES_COLLECTOR_DELAY_SHORT
import com.android.adblib.tools.AdbLibToolsProperties.PROCESS_PROPERTIES_COLLECTOR_DELAY_USE_SHORT
import com.android.adblib.tools.AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT
import com.android.adblib.tools.AdbLibToolsProperties.PROCESS_PROPERTIES_RETRY_DURATION
import com.android.adblib.tools.debugging.AtomicStateFlow
import com.android.adblib.tools.debugging.JdwpProcessProperties
import com.android.adblib.tools.debugging.SharedJdwpSession
import com.android.adblib.tools.debugging.packets.JdwpPacketConstants.PACKET_HEADER_LENGTH
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkType
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkView
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants.DDMS_CMD
import com.android.adblib.tools.debugging.packets.ddms.DdmsPacketConstants.DDMS_CMD_SET
import com.android.adblib.tools.debugging.packets.ddms.EphemeralDdmsChunk
import com.android.adblib.tools.debugging.packets.ddms.chunks.DdmsApnmChunk
import com.android.adblib.tools.debugging.packets.ddms.chunks.DdmsFeatChunk
import com.android.adblib.tools.debugging.packets.ddms.chunks.DdmsHeloChunk
import com.android.adblib.tools.debugging.packets.ddms.chunks.DdmsWaitChunk
import com.android.adblib.tools.debugging.packets.ddms.clone
import com.android.adblib.tools.debugging.packets.ddms.ddmsChunks
import com.android.adblib.tools.debugging.packets.ddms.writeToChannel
import com.android.adblib.tools.debugging.packets.impl.MutableJdwpPacket
import com.android.adblib.tools.debugging.packets.impl.PayloadProvider
import com.android.adblib.tools.debugging.receiveWhile
import com.android.adblib.tools.debugging.rethrowCancellation
import com.android.adblib.utils.ResizableBuffer
import com.android.adblib.withPrefix
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException

/**
 * Reads [JdwpProcessProperties] from a JDWP connection.
 */
internal class JdwpProcessPropertiesCollector(
    private val device: ConnectedDevice,
    private val processScope: CoroutineScope,
    private val pid: Int,
    private val jdwpSessionProvider: SharedJdwpSessionProvider
) {

    private val session: AdbSession
        get() = device.session

    private val logger = adbLogger(device.session)
        .withPrefix("${device.session} - $device - pid=$pid - ")

    /**
     * Collects [JdwpProcessProperties] for the process [pid] emits them to [stateFlow],
     * retrying as many times as necessary if there is contention on acquiring JDWP sessions
     * to the process.
     */
    suspend fun execute(stateFlow: AtomicStateFlow<JdwpProcessProperties>) {
        // Delay opening the JDWP session for a small amount of time in case
        // another instance wants to go first.
        delay(
            if (session.property(PROCESS_PROPERTIES_COLLECTOR_DELAY_USE_SHORT)) {
                session.property(PROCESS_PROPERTIES_COLLECTOR_DELAY_SHORT).toMillis()
            } else {
                session.property(PROCESS_PROPERTIES_COLLECTOR_DELAY_DEFAULT).toMillis()
            }
        )

        // Opens a JDWP session to the process, and collect as many properties as we
        // can for a short period of time, then close the session and try again
        // later if needed.
        var previouslyFailedCollectingCount = 0
        var previouslyFailedThrowable: Throwable? = null
        while (true) {

            val collectState = CollectState(stateFlow)
            try {
                withTimeout(session.property(PROCESS_PROPERTIES_READ_TIMEOUT).toMillis()) {
                    collect(collectState)
                }

                // We reached EOF, which can happen for at least 2 common cases:
                // 1. For API levels below 28 or starting again with 35 Android VM terminates a JDWP
                //    connection right away if there is already an active JDWP connection (note that
                //    this is not the case for APIs 28 to 34, where Android VM queues JDWP
                //    connections if there is already an active one).
                // 2. If the process terminates before the timeout expires. This is sort of a race
                //    condition between us trying to retrieve data about a process, and the process
                //    terminating.
                logger.debug { "EOF while receiving JDWP packet" }
                throw EOFException("JDWP session ended prematurely")
            } catch (throwable: Throwable) {
                val exceptionToRecord = when (throwable) {
                    is TimeoutCancellationException -> {
                        // On API 28+, we get a timeout if there is an active JDWP connection to the
                        // process **or** if the collector did not collect all the data it wanted
                        // (which is common in case there is no WAIT DDMS packet).
                        logger.debug { "Timeout exceeded while collecting process properties" }
                        null // Don't record timeout, it is "normal" termination
                    }

                    is CancellationException -> {
                        logger.debug { "Cancellation while collecting process properties" }
                        throw throwable // don't retry, this is a real cancellation
                    }

                    is EOFException -> {
                        null // Don't record EOF, it is "normal" termination
                    }

                    else -> {
                        logger.info(throwable) {
                            "Exception while collecting process properties (${stateFlow.value.summaryForLogging()})"
                        }
                        throwable // Record any other unexpected exception
                    }
                }

                if (collectState.shouldRetryCollecting) {
                    logUsage(isSuccess = false,
                             throwable = throwable,
                             previouslyFailedCount = previouslyFailedCollectingCount++,
                             previouslyFailedThrowable = previouslyFailedThrowable)
                    previouslyFailedThrowable = throwable

                    // Delay and retry if we did not collect all properties we want
                    delay(session.property(PROCESS_PROPERTIES_RETRY_DURATION).toMillis())
                    logger.info {
                        "Retrying JDWP process properties collection (${stateFlow.value.summaryForLogging()}), " +
                                "because previous attempt failed with an error ('${throwable.message}')"
                    }
                } else {
                    logUsage(isSuccess = true,
                             throwable = null, // Do not record a throwable since property collection was successful
                             previouslyFailedCount = previouslyFailedCollectingCount,
                             previouslyFailedThrowable = previouslyFailedThrowable)
                    collectState.propertiesFlow.update {
                        it.copy(
                            completed = true,
                            exception = exceptionToRecord
                        )
                    }
                    logger.debug { "Successfully retrieved JDWP process properties: ${stateFlow.value}" }
                    break
                }
            }
        }
        assert(stateFlow.value.completed) {
            "Properties flow should have been set to `completed`"
        }
    }

    private suspend fun logUsage(
        isSuccess: Boolean,
        throwable: Throwable?,
        previouslyFailedCount: Int,
        previouslyFailedThrowable: Throwable?
    ) {
        val deviceInfo = AdbUsageTracker.DeviceInfo.createFrom(device)
        session.host.usageTracker.logUsage(
            AdbUsageTracker.Event(
                deviceInfo = deviceInfo,
                jdwpProcessPropertiesCollector = AdbUsageTracker.JdwpProcessPropertiesCollectorEvent(
                    isSuccess = isSuccess,
                    failureType = throwable?.toAdbUsageTrackerFailureType(),
                    previouslyFailedCount = previouslyFailedCount,
                    previousFailureType = previouslyFailedThrowable?.toAdbUsageTrackerFailureType(),
                )
            )
        )
    }

    /**
     * Collects [JdwpProcessProperties] from a new JDWP session to the process [pid]
     * and emits them to [CollectState.propertiesFlow].
     */
    private suspend fun collect(collectState: CollectState) {
        jdwpSessionProvider.withSharedJdwpSession { jdwpSession ->
            collectWithSession(jdwpSession, collectState)

            if (collectState.hasCollectedEverything) {
                if (collectState.waitReceived) {
                    // See b/271466829: We need to keep the JDWP session open until an external
                    // debugger attaches to the Android process, because a JDWP session in a
                    // `WAIT` state needs to remain open until at least one "real" JDWP command
                    // is sent to eh Android debugger.
                    launchJdwpSessionHolder(collectState.propertiesFlow)
                }
            }
        }
    }

    private suspend fun collectWithSession(
        jdwpSession: SharedJdwpSession,
        collectState: CollectState
    ) {
        // Send a few ddms commands to collect process information
        val commands = IntroCommands(
            heloCommand = createHeloPacket(jdwpSession),
            featCommand = createFeatPacket(jdwpSession),
        )

        val workBuffer = ResizableBuffer()
        jdwpSession.newPacketReceiver()
            .withName("properties collector")
            .withActivation {
                // Send packets to JDWP session
                with(commands) {
                    jdwpSession.sendPacket(heloCommand)
                    jdwpSession.sendPacket(featCommand)
                }
            }.receiveWhile { packet ->
                logger.debug { "Processing JDWP packet: $packet" }
                // Any DDMS command is a packet sent from the Android VM that we should
                // replay in case we connect later on again (e.g. for a retry)
                if (packet.isCommand(DDMS_CMD_SET, DDMS_CMD)) {
                    jdwpSession.addReplayPacket(packet)
                }
                processReceivedPacket(packet, collectState, commands, workBuffer)

                // "Take while we have not collected everything"
                !collectState.hasCollectedEverything
            }
    }

    private suspend fun processReceivedPacket(
        jdwpPacket: JdwpPacketView,
        collectState: CollectState,
        commands: IntroCommands,
        workBuffer: ResizableBuffer
    ) {
        // Here is the situation of DDMS packets we receive from an Android VM:
        // * Sometimes (i.e. depending on the Android API level) we receive both "HELO"
        //   and "APNM", where "HELO" *or* "APNM" contain a "fake" process name, i.e. the
        //   "<pre-initialized>" string or the "" string. The order is non-deterministic,
        //   i.e. it looks like there is a race condition somewhere in the Android VM.
        //   So, basically, we need to ignore any instance of this "fake" name and hope we
        //   get a valid one at some point.
        // * Sometimes we consistently receive "HELO" with all the data correct. This seems
        //   to happen when re-attaching to the same process a 2nd time, for example.
        // * We have seen case where both "HELO" and "APNM" messages contain no data at all.
        //   This seems to be how the Android VM signals that the corresponding command
        //   packet were invalid. This should only happen if there is a bug in the code
        //   sending DDMS packet, i.e. if the code in this source file sends malformed
        //   packets (e.g. incorrect length).
        // * Wrt to the "WAIT" packet, we only receive it if the Android VM is waiting for
        //   a debugger to attach. The definition of "attaching a debugger" according to the
        //   "Android VM" is "receiving any valid JDWP command packet". Unfortunately, there
        //   is no "negative" version of the "WAIT" packet, meaning if the process is not
        //   in the "waiting for a debugger" state, there is no packet sent.
        //
        // `HELO` packet is a reply to the `HELO` command we sent to the VM
        if (jdwpPacket.isReply && jdwpPacket.id == commands.heloCommand.id) {
            processHeloReply(collectState, jdwpPacket, workBuffer)
            collectState.heloReplyReceived = true
        }

        // `FEAT` packet is a reply to the `FEAT` command we sent to the VM
        if (jdwpPacket.isReply && jdwpPacket.id == commands.featCommand.id) {
            processFeatReply(collectState, jdwpPacket, workBuffer)
            collectState.featReplyReceived = true
        }

        // `WAIT` and `APNM` are DDMS chunks embedded in a JDWP command packet sent from
        // the VM to us
        if (jdwpPacket.isCommand(DDMS_CMD_SET, DDMS_CMD)) {
            // For completeness, we process all chunks embedded in a JDWP packet, even
            // though in practice there is always one and only one DDMS chunk per JDWP
            // packet.
            jdwpPacket.ddmsChunks().collect { chunk ->
                when (chunk.type) {
                    DdmsChunkType.WAIT -> {
                        processWaitCommand(collectState, chunk.clone(), workBuffer)
                        collectState.waitReceived = true
                    }

                    DdmsChunkType.APNM -> {
                        processApnmCommand(collectState, chunk.clone(), workBuffer)
                    }

                    else -> {
                        logger.debug { "Skipping unexpected chunk: $chunk" }
                    }
                }
            }
        }
    }

    private suspend fun processHeloReply(
        collectState: CollectState,
        packet: JdwpPacketView,
        workBuffer: ResizableBuffer
    ) {
        val heloChunk = processJdwpPacket(packet, "HELO") {
            val heloChunkView = packet.ddmsChunks().map { it.clone() }.first()
            DdmsHeloChunk.parse(heloChunkView, workBuffer)
        }
        logger.debug { "`HELO` reply: $heloChunk" }
        collectState.propertiesFlow.update {
            it.copy(
                processName = filterFakeName(heloChunk.processName),
                userId = heloChunk.userId,
                packageName = filterFakeName(heloChunk.packageName),
                vmIdentifier = heloChunk.vmIdentifier,
                abi = heloChunk.abi,
                jvmFlags = heloChunk.jvmFlags,
                isNativeDebuggable = heloChunk.isNativeDebuggable
            )
        }
        logger.verbose { "Updated stateflow: ${collectState.propertiesFlow.value}" }
    }

    private suspend fun processFeatReply(
        collectState: CollectState,
        packet: JdwpPacketView,
        workBuffer: ResizableBuffer
    ) {
        val featChunk = processJdwpPacket(packet, "FEAT") {
            val featChunkView = packet.ddmsChunks().map { it.clone() }.first()
            DdmsFeatChunk.parse(featChunkView, workBuffer)
        }
        logger.debug { "`FEAT` reply: $featChunk" }
        collectState.propertiesFlow.update { it.copy(features = featChunk.features) }
        logger.verbose { "Updated stateflow: ${collectState.propertiesFlow.value}" }
    }

    private suspend fun processWaitCommand(
        collectState: CollectState,
        chunkCopy: DdmsChunkView,
        workBuffer: ResizableBuffer
    ) {
        val waitChunk = processDdmsChunk(chunkCopy, "WAIT") {
            DdmsWaitChunk.parse(chunkCopy, workBuffer)
        }
        logger.debug { "`WAIT` command: $waitChunk" }
        collectState.propertiesFlow.update {
            it.copy(
                waitCommandReceived = true,
                isWaitingForDebugger = true
            )
        }
        logger.verbose { "Updated stateflow: ${collectState.propertiesFlow.value}" }
    }

    private suspend fun processApnmCommand(
        collectState: CollectState,
        chunkCopy: DdmsChunkView,
        workBuffer: ResizableBuffer
    ) {
        val apnmChunk = processDdmsChunk(chunkCopy, "APNM") {
            DdmsApnmChunk.parse(chunkCopy, workBuffer)
        }
        logger.debug { "`APNM` command: $apnmChunk" }
        collectState.propertiesFlow.update {
            it.copy(
                processName = filterFakeName(apnmChunk.processName),
                userId = apnmChunk.userId,
                packageName = filterFakeName(apnmChunk.packageName)
            )
        }
        logger.verbose { "Updated stateflow: ${collectState.propertiesFlow.value}" }
    }

    private suspend fun createHeloPacket(jdwpSession: SharedJdwpSession): JdwpPacketView {
        // Prepare chunk payload buffer
        val payload = ResizableBuffer().order(DdmsPacketConstants.DDMS_CHUNK_BYTE_ORDER)
        payload.appendInt(DdmsHeloChunk.SERVER_PROTOCOL_VERSION)

        // Return is as a packet
        return createDdmsChunkPacket(
            jdwpSession.nextPacketId(),
            DdmsChunkType.HELO,
            payload.forChannelWrite()
        )
    }

    private suspend fun createFeatPacket(jdwpSession: SharedJdwpSession): JdwpPacketView {
        // Prepare chunk payload buffer
        val payload = ResizableBuffer().order(DdmsPacketConstants.DDMS_CHUNK_BYTE_ORDER)

        // Return is as a packet
        return createDdmsChunkPacket(
            jdwpSession.nextPacketId(),
            DdmsChunkType.FEAT,
            payload.forChannelWrite()
        )
    }

    private suspend fun createDdmsChunkPacket(
        packetId: Int,
        chunkType: DdmsChunkType,
        chunkData: ByteBuffer
    ): JdwpPacketView {
        val chunk = EphemeralDdmsChunk(
            type = chunkType,
            length = chunkData.remaining(),
            payloadProvider = PayloadProvider.forByteBuffer(chunkData)
        )
        val workBuffer = ResizableBuffer()
        val outputChannel = ByteBufferAdbOutputChannel(workBuffer)
        chunk.writeToChannel(outputChannel)
        val serializedChunk = workBuffer.forChannelWrite()

        val packet = MutableJdwpPacket()
        packet.id = packetId
        packet.length = PACKET_HEADER_LENGTH + serializedChunk.remaining()
        packet.cmdSet = DDMS_CMD_SET
        packet.cmd = DDMS_CMD
        packet.payloadProvider = PayloadProvider.forByteBuffer(serializedChunk)

        logger.debug { "Preparing to send $chunk" }
        return packet
    }

    private fun filterFakeName(processOrPackageName: String?): String? {
        return if (EARLY_PROCESS_NAMES.contains(processOrPackageName)) {
            return null
        } else {
            processOrPackageName
        }
    }

    private suspend fun launchJdwpSessionHolder(propertiesFlow: AtomicStateFlow<JdwpProcessProperties>) {
        logger.debug { "JDWP session holder: launching coroutine" }
        val deferred = CompletableDeferred<Unit>()
        processScope.launch {
            jdwpSessionProvider.withSharedJdwpSession {
                logger.debug { "JDWP session holder: JDWP session has been acquired" }

                // Ok to exit function now, as session has been acquired
                deferred.complete(Unit)

                // Wait until a debugger is attached, then keep session open as long as
                // debugger is attached. This gives time for the external debugger
                // to acquire its own SharedJdwpSession and keep it open as long as
                // it is active. If/when the external debugger detaches from the process,
                // we also release the SharedJdwpSession in case another debug session
                // needs to be started later on.
                with(propertiesFlow.asStateFlow()) {
                    waitUntil { jdwpSessionProxyStatus.isExternalDebuggerAttached }
                    waitWhile { jdwpSessionProxyStatus.isExternalDebuggerAttached }
                }

                logger.debug { "JDWP session holder: JDWP session about to be released as debugger has detached" }
            }
        }

        // Wait until session has been acquired
        deferred.await()
        logger.debug { "JDWP session holder: successfully started" }
    }

    private suspend fun <R> processJdwpPacket(
        packet: JdwpPacketView,
        packetName: String,
        block: suspend (JdwpPacketView) -> R
    ): R {
        if (packet.isEmpty) {
            throw JdwpProtocolErrorException("Invalid '$packetName' packet: packet is empty")
        }
        return try {
            block(packet)
        } catch (t: Throwable) {
            t.rethrowCancellation()
            throw JdwpProtocolErrorException("Invalid '$packetName' packet: unsupported format", t)
        }
    }

    private suspend fun <R> processDdmsChunk(
        packet: DdmsChunkView,
        packetName: String,
        block: suspend (DdmsChunkView) -> R
    ): R {
        return try {
            block(packet)
        } catch (t: Throwable) {
            t.rethrowCancellation()
            throw JdwpProtocolErrorException("Invalid '$packetName' ddms packet: unsupported format", t)
        }
    }

    private suspend fun <T> StateFlow<T>.waitUntil(predicate: T.() -> Boolean) {
        if (!value.predicate()) {
            first { it.predicate() }
        }
    }

    private suspend fun <T> StateFlow<T>.waitWhile(predicate: T.() -> Boolean) {
        takeWhile { it.predicate() }.collect()
    }

    private fun JdwpProcessProperties.summaryForLogging() =
        "processName=${processName ?: "<not yet received>"}, waitCommandReceived=${waitCommandReceived}"

    /**
     * List of DDMS requests sent to the Android VM.
     */
    data class IntroCommands(
        val heloCommand: JdwpPacketView,
        val featCommand: JdwpPacketView,
    )

    private class CollectState(val propertiesFlow: AtomicStateFlow<JdwpProcessProperties>) {

        var heloReplyReceived = false
        var featReplyReceived = false
        var waitReceived = false

        /**
         * Whether we collected all the data we need
         */
        val hasCollectedEverything: Boolean
            get() {
                if (!isDone) {
                    return false
                }

                // We can only say we collected everything we need
                // if we received the `WAIT` packet (because there is
                // no such thing as a `NO_WAIT` packet).
                return waitReceived
            }

        /**
         * Whether there is a need to retry collecting process properties
         */
        val shouldRetryCollecting: Boolean
            get() = !isDone

        private val isDone: Boolean
            get() {
                return heloReplyReceived &&
                        featReplyReceived &&
                        propertiesFlow.value.processName != null
            }
    }

    companion object {

        /**
         * The process name (and package name) can be set to this value when the process is not yet fully
         * initialized. We should ignore this value to make sure we only return "valid" process/package name.
         * Note that sometimes the process name (or package name) can also be empty.
         */
        private val EARLY_PROCESS_NAMES = arrayOf("<pre-initialized>", "")
    }
}

private fun Throwable.toAdbUsageTrackerFailureType(): AdbUsageTracker.JdwpProcessPropertiesCollectorFailureType {
    // This regex matches errors like `'closed' error on device serial #emulator-5554 executing service 'jdwp:2900'`
    // which are mentioned in b/311788428 and b/322467516
    val closedFailResponseExecutingService = Regex(".*'closed' error on .* executing service .*")

    return when {
        this is TimeoutCancellationException ->
            AdbUsageTracker.JdwpProcessPropertiesCollectorFailureType.NO_RESPONSE

        this is ClosedChannelException ->
            AdbUsageTracker.JdwpProcessPropertiesCollectorFailureType.CLOSED_CHANNEL_EXCEPTION

        this is AdbFailResponseException && closedFailResponseExecutingService.matches(message) ->
            AdbUsageTracker.JdwpProcessPropertiesCollectorFailureType.CONNECTION_CLOSED_ERROR

        this is IOException ->
            AdbUsageTracker.JdwpProcessPropertiesCollectorFailureType.IO_EXCEPTION

        else -> AdbUsageTracker.JdwpProcessPropertiesCollectorFailureType.OTHER_ERROR
    }
}

