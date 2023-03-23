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
package com.android.processmonitor.monitor

import com.android.processmonitor.agenttracker.AgentProcessTrackerConfig
import com.android.processmonitor.common.ProcessEvent
import kotlinx.coroutines.flow.Flow

/**
 * Monitors devices and keeps track of process names.
 */
interface ProcessNameMonitor {

    /**
     * Starts the monitor if not already started
     */
    fun start()

    /**
     * Returns a [ProcessNames] for a given pid or null if not found.
     */
    fun getProcessNames(serialNumber: String, pid: Int): ProcessNames?

    /**
     * Track processes on a device.
     */
    suspend fun trackDeviceProcesses(serialNumber: String) : Flow<ProcessEvent>

    class Config(val maxProcessRetention: Int, val agentConfig: AgentProcessTrackerConfig?)
}
