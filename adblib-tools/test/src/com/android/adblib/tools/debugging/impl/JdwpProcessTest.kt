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
package com.android.adblib.tools.debugging.impl

import com.android.adblib.AdbUsageTracker
import com.android.adblib.AdbUsageTracker.JdwpProcessPropertiesCollectorEvent
import com.android.adblib.ConnectedDevice
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.serialNumber
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.testingutils.TestingAdbUsageTracker
import com.android.adblib.tools.AdbLibToolsProperties
import com.android.adblib.tools.debugging.JdwpProcessProperties
import com.android.adblib.tools.debugging.flow
import com.android.adblib.tools.debugging.jdwpProcessFlow
import com.android.adblib.tools.debugging.packets.impl.JdwpCommands
import com.android.adblib.tools.debugging.packets.impl.MutableJdwpPacket
import com.android.adblib.tools.debugging.packets.payloadLength
import com.android.adblib.tools.debugging.packets.withPayload
import com.android.adblib.tools.debugging.properties
import com.android.adblib.tools.debugging.sendDdmsExit
import com.android.adblib.tools.debugging.toByteArray
import com.android.adblib.tools.testutils.AdbLibToolsTestBase
import com.android.adblib.tools.testutils.waitForOnlineConnectedDevice
import com.android.fakeadbserver.ClientState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant

class JdwpProcessTest : AdbLibToolsTestBase() {

    @Test
    fun basicStuffWorks() = runBlockingWithTimeout {
        // Prepare
        val (_, device, process) = createJdwpProcess()

        // Act
        val key = object : CoroutineScopeCache.Key<Int>("foo") {}
        val cachedValue = process.cache.getOrPut(key) { 11 }
        val cachedValue2 = process.cache.getOrPutSuspending(key) { 12 }

        // Assert
        assertEquals(10, process.pid)
        assertSame(device, process.device)
        assertTrue(process.scope.isActive)
        assertEquals(11, cachedValue)
        assertEquals(12, cachedValue2)
    }

    @Test
    fun withJdwpSessionWorks() = runBlockingWithTimeout {
        // Prepare
        val (_, _, process) = createJdwpProcess()

        // Act
        val reply = process.withJdwpSession {
            val versionCommand = MutableJdwpPacket.createCommandPacket(
                nextPacketId(),
                JdwpCommands.CmdSet.SET_VM.value,
                JdwpCommands.VmCmd.CMD_VM_VERSION.value,
                ByteBuffer.allocate(0)
            )
            newPacketReceiver()
                .withActivation {
                    sendPacket(versionCommand)
                }.flow()
                .first { reply -> reply.id == versionCommand.id }
        }

        // Assert
        assertEquals(true, reply.isReply)
        assertEquals(42, reply.payloadLength)
        assertEquals(42, reply.withPayload { it.toByteArray(reply.payloadLength).size })
    }

    @Test
    fun propertiesFlowInitialStateIsEmpty() = runBlockingWithTimeout {
        // Prepare
        val (_, _, process) = createJdwpProcess()

        // Act
        val properties = process.propertiesFlow.value
        val properties2 = process.properties

        // Assert
        assertSame(properties, properties2)
        assertProcessPropertiesIsSkeleton(properties)
    }

    @Test
    fun startMonitoringWorks() = runBlockingWithTimeout {
        // Prepare
        val (_, _, process) = createJdwpProcess()
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
            Duration.ofSeconds(2)
        )

        // Act
        process.startMonitoring()
        yieldUntil { process.properties.completed }

        // Assert
        val properties = process.properties
        assertProcessPropertiesComplete(properties)
    }

    @Test
    fun startMonitoringUsesDefaultDelayByDefault() = runBlockingWithTimeout {
        // Prepare
        val (_, _, process) = createJdwpProcess()
        val delay = Duration.ofSeconds(2)
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_COLLECTOR_DELAY_DEFAULT,
            delay
        )
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_COLLECTOR_DELAY_SHORT,
            Duration.ofSeconds(0)
        )

        // Act
        val start = Instant.now()
        process.startMonitoring()
        yieldUntil { process.properties.processName != null }
        val waitTime = Duration.between(start, Instant.now())

        // Assert: We waited (close to) "delay" for "completed" to get set
        assertTrue(timeoutExceeded(waitTime, delay))
    }

    @Test
    fun startMonitoringUsesShortDelayAccordingToUseShortDelayPropertyValue() = runBlockingWithTimeout {
            // Prepare
            val (_, _, process) = createJdwpProcess()
            val delay = Duration.ofSeconds(2)
            setHostPropertyValue(
                process.device.session.host,
                AdbLibToolsProperties.PROCESS_PROPERTIES_COLLECTOR_DELAY_DEFAULT,
                Duration.ofSeconds(0)
            )
            setHostPropertyValue(
                process.device.session.host,
                AdbLibToolsProperties.PROCESS_PROPERTIES_COLLECTOR_DELAY_SHORT,
                delay
            )
            setHostPropertyValue(
                process.device.session.host,
                AdbLibToolsProperties.PROCESS_PROPERTIES_COLLECTOR_DELAY_USE_SHORT,
                true
            )

            // Act
            val start = Instant.now()
            process.startMonitoring()
            yieldUntil { process.properties.processName != null }
            val waitTime = Duration.between(start, Instant.now())

            // Assert: We waited (close to) "delay" for "completed" to get set
            assertTrue(timeoutExceeded(waitTime, delay))
        }

    @Suppress("unused")
    //@Test // Disabled because of b/271466829
    fun startMonitoringEndsEarlyIfWaitForDebugger() = runBlockingWithTimeout {
        // Prepare
        val (_, _, process) = createJdwpProcess(waitForDebugger = true)

        // Act:
        // When the AndroidVM sends a "WAIT" packet, collecting the process properties
        // should complete fast, i.e. in a much shorter time than the max. timeout
        // specified in PROCESS_PROPERTIES_READ_TIMEOUT
        val longTimeout = Duration.ofSeconds(50)
        val shortTimeout = Duration.ofSeconds(15)
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
            longTimeout
        )
        process.startMonitoring()
        yieldUntil(shortTimeout) { process.properties.completed }

        // Assert
        val properties = process.properties
        assertProcessPropertiesComplete(properties)
    }

    /**
     * Regression test for [b/271466829](https://issuetracker.google.com/issues/271466829)
     */
    @Test
    fun startMonitoringDoesNotReleaseJdwpSessionIfWaitForDebugger() = runBlockingWithTimeout {
        // Prepare
        val (_, _, process) = createJdwpProcess(waitForDebugger = true)

        // Act: When the AndroidVM sends a "WAIT" packet, "completed" is set early,
        // but the SharedJDWPSession should be retained
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
            Duration.ofSeconds(60) // long timeout
        )
        process.startMonitoring()
        yieldUntil { process.properties.completed }
        delay(500) // give JDWP session holder time to launch

        // Assert: The JDWP session should still be in-use, since we received a `WAIT` packet
        assertTrue(process.jdwpSessionActivationCount.value >= 1)
    }

    @Test
    fun startMonitoringNeverEndsIfLongTimeoutAndNoWaitPacket() = runBlockingWithTimeout {
        // Prepare
        val (_, _, process) = createJdwpProcess(waitForDebugger = false)

        // Act:
        // When the AndroidVM does not send a "WAIT" packet, collecting the process properties
        // never completes before the timeout specified in PROCESS_PROPERTIES_READ_TIMEOUT
        val longTimeout = Duration.ofSeconds(50)
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
            longTimeout
        )
        process.startMonitoring()
        yieldUntil {
            process.properties.processName != null &&
                    process.properties.features.isNotEmpty()
        }

        // Assert
        val properties = process.properties
        assertNull(properties.exception)
        assertFalse(properties.completed)
    }

    @Test
    fun startMonitoringDoesRetryWhenProcessNotAvailable() = runBlockingWithTimeout {
        // Prepare
        val (_, _, firstProcess) = createJdwpProcess(waitForDebugger = false)
        setHostPropertyValue(
            firstProcess.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
            Duration.ofSeconds(50)
        )
        firstProcess.startMonitoring() // collect properties for a very long time (50 seconds)
        yieldUntil { firstProcess.properties.processName != null }

        // Act: Set short timeout and observe we get a timeout exception
        // Also set a very short retry timeout, so that we retry when the first
        // process is closed
        setHostPropertyValue(
            firstProcess.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
            Duration.ofMillis(100)
        )
        setHostPropertyValue(
            firstProcess.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_RETRY_DURATION,
            Duration.ofMillis(10)
        )
        val process =
            registerCloseable(
                JdwpProcessImpl(
                    firstProcess.device,
                    firstProcess.pid
                )
            )

        // This will time out and retry because there is another process collecting properties
        process.startMonitoring()

        // Close the other process so that it frees up the JDWP session
        launch {
            delay(1_000)
            firstProcess.close()
        }

        yieldUntil { process.properties.completed }

        // Assert
        val properties = process.properties
        assertProcessPropertiesComplete(properties)
    }

    @Test
    fun startMonitoringStopsOnEOF() = runBlockingWithTimeout {
        // Prepare
        val (fakeAdb, _, process) = createJdwpProcess(waitForDebugger = false)
        setHostPropertyValue(
            process.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
            Duration.ofSeconds(50)
        )
        process.startMonitoring() // collect properties for a very long time (50 seconds)

        // Act: Stop process before timeout expires (they will send EOF to
        // the process properties collector)
        fakeAdb.device(process.device.serialNumber).stopClient(process.pid)
        delay(500)

        // Assert
        val properties = process.properties
        assertFalse(properties.completed)
    }

    @Test
    fun closeClearsCacheAndCancelsScope() = runBlockingWithTimeout {
        // Prepare
        val (_, _, process) = createJdwpProcess()

        // Launch a long job
        val job = process.scope.launch { delay(50_000) }
        // Add cache entries
        val key1 = CoroutineScopeCache.Key<Int>("key1")
        process.cache.getOrPut(key1) { 5 }
        val key2 = CoroutineScopeCache.Key<Int>("key2")
        process.cache.getOrPut(key2) { 10 }

        // Act
        process.close()

        // Assert
        assertTrue(job.isCancelled)
        assertEquals(1_000, process.cache.getOrPut(key1) { 1_000 })
        assertEquals(2_000, process.cache.getOrPut(key2) { 2_000 })
    }

    @Test
    fun startProcessMonitoringLogsUsageStats() = runBlockingWithTimeout {
        // Prepare
        val (_, device, firstProcess) = createJdwpProcess(waitForDebugger = false)
        setHostPropertyValue(
            firstProcess.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
            Duration.ofMillis(100_000L)
        )

        // Act
        firstProcess.startMonitoring() // collect properties for a very long time (100 seconds)
        yieldUntil { firstProcess.properties.processName != null }

        // Prepare/Act: Create a second `JdwpProcessImpl` to monitor the same process. Set the
        // timeouts in a such a way that it will quickly timeout the first time and will retry
        // only after we close `firstProcess` below
        setHostPropertyValue(
            firstProcess.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_READ_TIMEOUT,
            Duration.ofMillis(500)
        )
        setHostPropertyValue(
            firstProcess.device.session.host,
            AdbLibToolsProperties.PROCESS_PROPERTIES_RETRY_DURATION,
            Duration.ofMillis(1000)
        )
        val process =
            registerCloseable(
                JdwpProcessImpl(
                    firstProcess.device,
                    firstProcess.pid
                )
            )

        // This will time out and retry because there is another process collecting properties
        process.startMonitoring()
        yieldUntil {
            ((session.host.usageTracker as? TestingAdbUsageTracker)?.loggedEvents?.size ?: 0) > 0
        }

        // Close the other process so that it frees up the JDWP session
        launch {
            firstProcess.close()
        }

        yieldUntil { process.properties.completed }

        // Assert: We should have logged 2 adb usage events from the `process`. Note that
        // we have closed `firstProcess` before it could have logged any adb usage events.
        val loggedEvents = (session.host.usageTracker as? TestingAdbUsageTracker)?.loggedEvents
        assertEquals(2, loggedEvents?.size)
        assertEquals(
            JdwpProcessPropertiesCollectorEvent(
                isSuccess = false,
                failureType = AdbUsageTracker.JdwpProcessPropertiesCollectorFailureType.NO_RESPONSE,
                previouslyFailedCount = 0,
                previousFailureType = null
            ), loggedEvents!![0].jdwpProcessPropertiesCollector
        )
        assertEquals(device.serialNumber, loggedEvents[0].deviceInfo?.serialNumber)
        assertEquals(
            JdwpProcessPropertiesCollectorEvent(
                isSuccess = true,
                failureType = null,
                previouslyFailedCount = 1,
                previousFailureType = AdbUsageTracker.JdwpProcessPropertiesCollectorFailureType.NO_RESPONSE
            ), loggedEvents[1].jdwpProcessPropertiesCollector
        )
        assertEquals(device.serialNumber, loggedEvents[1].deviceInfo?.serialNumber)
    }

    @Test
    fun sendDdmsExit() = runBlockingWithTimeout {
        // Prepare
        val (_, device, process) = createJdwpProcess()

        // Act
        device.jdwpProcessFlow.first { processes -> processes.isNotEmpty() }
        process.sendDdmsExit(1)

        // Assert
        device.jdwpProcessFlow.first { processes -> processes.isEmpty() }
        Unit
    }

    private suspend fun createJdwpProcess(
        deviceApi: Int = 30,
        pid: Int = 10,
        waitForDebugger: Boolean = true
    ): Triple<FakeAdbServerProvider, ConnectedDevice, JdwpProcessImpl> {
        val device = fakeAdb.addDevice(deviceApi)
        device.createFakeAdbProcess(pid, waitForDebugger)
        val process = JdwpProcessImpl(device, pid)
        return Triple(fakeAdb, device, process)
    }

    private suspend fun FakeAdbServerProvider.addDevice(deviceApi: Int = 30): ConnectedDevice {
        val fakeAdb = this
        val fakeDevice = addFakeDevice(fakeAdb, deviceApi)
        return waitForOnlineConnectedDevice(session, fakeDevice.deviceId)
    }

    private suspend fun ConnectedDevice.createFakeAdbProcess(
        pid: Int = 10,
        waitForDebugger: Boolean = false
    ): ClientState {
        return fakeAdb.device(serialNumber).startClient(pid, 2, "p1", "pkg", waitForDebugger)
    }

    private fun assertProcessPropertiesComplete(properties: JdwpProcessProperties) {
        assertEquals(10, properties.pid)
        assertEquals("p1", properties.processName)
        assertEquals(2, properties.userId)
        assertEquals("pkg", properties.packageName)
        assertEquals("FakeVM", properties.vmIdentifier)
        assertEquals("x86_64", properties.abi)
        assertEquals("-jvmflag=true", properties.jvmFlags)
        @Suppress("DEPRECATION")
        assertFalse(properties.isNativeDebuggable)
        assertFalse(properties.jdwpSessionProxyStatus.isExternalDebuggerAttached)
        assertNotNull(properties.jdwpSessionProxyStatus.socketAddress)
        assertEquals(
            listOf(
                "hprof-heap-dump",
                "method-sample-profiling",
                "view-hierarchy",
                "method-trace-profiling",
                "hprof-heap-dump-streaming",
                "method-trace-profiling-streaming",
                "opengl-tracing"
            ), properties.features
        )
        assertNull(properties.exception)
        assertTrue(properties.completed)
    }

    private fun assertProcessPropertiesIsSkeleton(properties: JdwpProcessProperties) {
        assertEquals(10, properties.pid)
        assertNull(properties.processName)
        assertNull(properties.userId)
        assertNull(properties.packageName)
        assertNull(properties.vmIdentifier)
        assertNull(properties.abi)
        assertNull(properties.jvmFlags)
        @Suppress("DEPRECATION")
        assertFalse(properties.isNativeDebuggable)
        assertFalse(properties.jdwpSessionProxyStatus.isExternalDebuggerAttached)
        assertNull(properties.jdwpSessionProxyStatus.socketAddress)
        assertTrue(properties.features.isEmpty())
        assertNull(properties.exception)
        assertFalse(properties.completed)
    }

    private fun timeoutExceeded(waitTime: Duration, timeout: Duration): Boolean {
        // To deal with potential "corner case" of timeout expiring slightly ahead of time,
        // we assume timeout has been exceeded if the wait was 90% of the timeout.
        // Note: This works only for "short" duration, i.e. [Duration.toNanos] returns a valid value.
        val timeoutLimit = Duration.ofNanos((timeout.toNanos() * 0.9).toLong())
        return waitTime >= timeoutLimit
    }
}
