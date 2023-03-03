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
package com.android.processmonitor.monitor.testing

import com.android.processmonitor.monitor.ProcessNameMonitor
import com.android.processmonitor.monitor.ProcessNames
import org.jetbrains.annotations.TestOnly

/**
 * A fake implementation of [ProcessNameMonitor] for tests
 */
@TestOnly
class FakeProcessNameMonitor : ProcessNameMonitor {

    private val deviceToProcessesMap = mutableMapOf<String, MutableMap<Int, ProcessNames>>()

    fun addProcessName(serialNumber: String, pid: Int, applicationId: String, processName: String) {
        deviceToProcessesMap.computeIfAbsent(serialNumber) { mutableMapOf() }[pid] =
            ProcessNames(applicationId, processName)
    }

    override fun start() {}

    override fun getProcessNames(serialNumber: String, pid: Int): ProcessNames? =
        deviceToProcessesMap[serialNumber]?.get(pid)
}
