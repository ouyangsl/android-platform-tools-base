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
package com.android.processmonitor.monitor.adblib

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.serialNumber
import com.android.adblib.testing.FakeAdbLoggerFactory
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.testingutils.FakeAdbServerProviderRule
import com.android.fakeadbserver.DeviceState.DeviceStatus.ONLINE
import com.android.fakeadbserver.DeviceState.HostConnectionType.USB
import com.android.processmonitor.common.ProcessEvent
import com.android.processmonitor.common.ProcessEvent.ProcessAdded
import com.android.processmonitor.common.ProcessEvent.ProcessRemoved
import com.android.processmonitor.testutils.toChannel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Duration

/**
 * Tests for [JdwpProcessTracker]
 */
@Suppress("OPT_IN_IS_NOT_ENABLED")
@OptIn(ExperimentalCoroutinesApi::class) // runTest is experimental (replaced runTestTest)
class JdwpProcessTrackerTest {

    @get:Rule
    val fakeAdbRule = FakeAdbServerProviderRule()

    private val adbSession get() = fakeAdbRule.adbSession
    private val logger = FakeAdbLoggerFactory().logger

    @Test
    fun trackProcesses_addProcesses_appProcessFlow(): Unit = runBlocking {
        val device = setupDevice("device1", 33)
        val connectedDevice = adbSession.waitForDevice("device1")
        val tracker = JdwpProcessTracker(connectedDevice, logger)

        device.startClient(101, 1, "processName1", "packageName1", false)
        device.startClient(102, 1, "processName2", "packageName2", false)
        device.startClient(103, 1, "processName3", "packageName3", false)
        val events = tracker.trackProcesses().take(3).toList()

        assertThat(events).containsExactly(
            ProcessAdded(pid = 101, "packageName1", "processName1"),
            ProcessAdded(pid = 102, "packageName2", "processName2"),
            ProcessAdded(pid = 103, "packageName3", "processName3"),
        )
    }

    @Test
    fun trackProcesses_addAndThenRemove_appProcessFlow() = runTest(dispatchTimeoutMs = 5_000) {
        val device = setupDevice("device2", 33)
        val connectedDevice = adbSession.waitForDevice("device2")
        val tracker = JdwpProcessTracker(connectedDevice, logger)

        tracker.trackProcesses().toChannel(this).use { channel ->
            device.startClient(101, 1, "processName1", "packageName1", false)
            device.startClient(102, 1, "processName2", "packageName2", false)
            val addedEvents = channel.take(2)
            device.stopClient(101)
            device.stopClient(102)
            val removedEvents = channel.take(2)

            assertThat(addedEvents).containsExactly(
                ProcessAdded(pid = 101, "packageName1", "processName1"),
                ProcessAdded(pid = 102, "packageName2", "processName2"),
            )
            assertThat(removedEvents).containsExactly(
                ProcessRemoved(pid = 102),
                ProcessRemoved(pid = 101),
            )
        }
    }

    @Test
    fun trackProcesses_addProcesses_jdwpProcessFlow(): Unit = runBlocking {
        val device = setupDevice("device1", 30)
        val connectedDevice = adbSession.waitForDevice("device1")
        val tracker = JdwpProcessTracker(connectedDevice, logger)

        device.startClient(101, 1, "processName1", "packageName1", false)
        device.startClient(102, 1, "processName2", "packageName2", false)
        device.startClient(103, 1, "processName3", "packageName3", false)
        val events = tracker.trackProcesses().take(3).toList()

        assertThat(events).containsExactly(
            ProcessAdded(pid = 101, "packageName1", "processName1"),
            ProcessAdded(pid = 102, "packageName2", "processName2"),
            ProcessAdded(pid = 103, "packageName3", "processName3"),
        )
    }

    @Test
    fun trackProcesses_addAndThenRemove_jdwpProcessFlow() = runTest(dispatchTimeoutMs = 5_000) {
        val device = setupDevice("device2", 30)
        val connectedDevice = adbSession.waitForDevice("device2")
        val tracker = JdwpProcessTracker(connectedDevice, logger)

        tracker.trackProcesses().toChannel(this).use { channel ->
            device.startClient(101, 1, "processName1", "packageName1", false)
            device.startClient(102, 1, "processName2", "packageName2", false)
            val addedEvents = channel.take(2)
            device.stopClient(101)
            device.stopClient(102)
            val removedEvents = channel.take(2)

            assertThat(addedEvents).containsExactly(
                ProcessAdded(pid = 101, "packageName1", "processName1"),
                ProcessAdded(pid = 102, "packageName2", "processName2"),
            )
            assertThat(removedEvents).containsExactly(
                ProcessRemoved(pid = 102),
                ProcessRemoved(pid = 101),
            )
        }
    }

    @Test
    fun trackProcesses_sendsOneAndOnlyOneProcessAddedEvent(): Unit = runBlocking {
        // Setup
        val device = setupDevice("device1", 33)
        val connectedDevice = adbSession.waitForDevice("device1")
        val tracker = JdwpProcessTracker(connectedDevice, logger)

        val clientState =
            device.startClient(101, 1, "processName1", "packageName1", false)
        // Sending a `Wait` command will result in another process update.
        // This test is about making sure that this update does not result
        // in additional `ProcessAdded` event being emitted.
        clientState.sendWaitCommandAfterHelo = Duration.ofMillis(100)

        // Act
        val allEvents = mutableListOf<ProcessEvent>()
        val job = launch {
            tracker.trackProcesses().collect {
                allEvents.add(it)
            }
        }

        // Wait a little longer after getting the first process event
        yieldUntil { allEvents.size >= 1 }
        delay(200)
        job.cancelAndJoin()

        assertThat(allEvents).containsExactly(
            ProcessAdded(pid = 101, "packageName1", "processName1"),
        )
    }

    private fun setupDevice(serialNumber: String, sdk: Int) =
        fakeAdbRule.fakeAdb.connectDevice(serialNumber, "", "", "13", sdk.toString(), USB).apply {
            deviceStatus = ONLINE
        }

    private suspend fun AdbSession.waitForDevice(serialNumber: String): ConnectedDevice {
        return connectedDevicesTracker.connectedDevices.map { it.findDevice(serialNumber) }
            .filterNotNull()
            .first()
    }
}

private fun List<ConnectedDevice>.findDevice(serialNumber: String) =
    firstOrNull { it.serialNumber == serialNumber }
