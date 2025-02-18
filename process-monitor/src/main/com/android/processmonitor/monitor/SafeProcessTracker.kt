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

import com.android.adblib.AdbLogger
import com.android.processmonitor.common.ProcessEvent
import com.android.processmonitor.common.ProcessTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.EOFException

/** A [ProcessTracker] that doesn't fail on exception */
internal class SafeProcessTracker(
    private val delegate: ProcessTracker,
    private val errorMessage: String,
    private val logger: AdbLogger
) : ProcessTracker {

    override fun trackProcesses(): Flow<ProcessEvent> {
        return delegate.trackProcesses().catch {
            if (it is EOFException) {
                logger.info { "Stopping process monitoring" }
            } else {
                logger.warn(it, errorMessage)
            }
        }
    }

    override fun toString(): String {
        return "SafeProcessTracker(${delegate::class.simpleName})"
    }
}
