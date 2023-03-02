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
package com.android.processmonitor.monitor.ddmlib

import com.android.adblib.testing.FakeAdbLoggerFactory
import com.android.adblib.testing.FakeAdbSession
import com.android.processmonitor.monitor.ddmlib.DeviceMonitorEvent.Disconnected
import com.android.processmonitor.monitor.ddmlib.DeviceMonitorEvent.Online
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests for [ProcessNameMonitorDdmlib]
 */
@Suppress("OPT_IN_IS_NOT_ENABLED")
@OptIn(ExperimentalCoroutinesApi::class) // runTest is experimental (replaced runTestTest)
class ProcessNameMonitorDdmlibTest {
  private val device1 = mockDevice("device1")
  private val device2 = mockDevice("device2")
  private val process1 = ProcessInfo(1, "package1", "process1")
  private val process2 = ProcessInfo(2, "package2", "process2")
  private val process3 = ProcessInfo(3, "package3", "process3")

  private val fakeAdbSession = FakeAdbSession()

  @Test
  fun devicesOnline(): Unit = runTest {
    FakeProcessNameMonitorFlows().use { flows ->
      val monitor = processNameMonitor(flows)

      flows.sendDeviceEvents(Online(device1))
      flows.sendDeviceEvents(Online(device2))
      flows.sendClientEvents(device1.serialNumber, clientsAddedEvent(process1))
      flows.sendClientEvents(device2.serialNumber, clientsAddedEvent(process2))

      advanceUntilIdle()
      assertThat(monitor.getProcessNames(device1.serialNumber, 1)).isEqualTo(process1.names)
      assertThat(monitor.getProcessNames(device2.serialNumber, 2)).isEqualTo(process2.names)
    }
  }

  @Test
  fun deviceDisconnected(): Unit = runTest {
    FakeProcessNameMonitorFlows().use { flows ->
      val monitor = processNameMonitor(flows)

      flows.sendDeviceEvents(Online(device1))
      flows.sendDeviceEvents(Online(device2))
        flows.sendClientEvents(device1.serialNumber, clientsAddedEvent(process1))
        flows.sendClientEvents(device2.serialNumber, clientsAddedEvent(process2))
        advanceUntilIdle()
        flows.sendDeviceEvents(Disconnected(device1))

        advanceUntilIdle()
        assertThat(monitor.getProcessNames(device2.serialNumber, 2)).isEqualTo(process2.names)
    }
  }

    @Test
    fun propagatesMaxProcessRetention(): Unit = runTest {
        FakeProcessNameMonitorFlows().use { flows ->
            val monitor = processNameMonitor(flows, maxProcessRetention = 1)

            flows.sendDeviceEvents(Online(device1))
            flows.sendClientEvents(
                device1.serialNumber,
                clientsAddedEvent(process1, process2, process3),
                clientsRemovedEvent(process1.pid, process2.pid)
            )

            advanceUntilIdle()
            assertThat(monitor.getProcessNames(device1.serialNumber, 1)).isNull()
            assertThat(monitor.getProcessNames(device1.serialNumber, 2)).isEqualTo(process2.names)
            assertThat(monitor.getProcessNames(device1.serialNumber, 3)).isEqualTo(process3.names)
        }
    }

    @Test
    fun disconnect_terminatesClientFlow(): Unit = runTest {
        val flows = TerminationTrackingProcessNameMonitorFlows()
        processNameMonitor(flows)
        flows.sendDeviceEvents(Online(device1))
        advanceTimeBy(2000) // Let the flow run a few cycles
        assertThat(flows.isClientFlowStarted(device1.serialNumber)).isTrue()

        flows.sendDeviceEvents(Disconnected(device1))

        advanceUntilIdle()
    assertThat(flows.isClientFlowTerminated(device1.serialNumber)).isTrue()
  }

    private fun CoroutineScope.processNameMonitor(
        flows: ProcessNameMonitorFlows = FakeProcessNameMonitorFlows(),
        maxProcessRetention: Int = 1000,
    ): ProcessNameMonitorDdmlib {
        return ProcessNameMonitorDdmlib(
            this,
            fakeAdbSession,
            flows,
            maxProcessRetention,
            FakeAdbLoggerFactory().logger
        ).apply {
            start()
        }
    }
}
