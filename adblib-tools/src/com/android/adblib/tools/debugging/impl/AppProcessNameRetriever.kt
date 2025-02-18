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

import com.android.adblib.adbLogger
import com.android.adblib.selector
import com.android.adblib.shellCommand
import com.android.adblib.tools.debugging.AppProcess
import com.android.adblib.tools.debugging.JdwpProcess
import com.android.adblib.tools.debugging.rethrowCancellation
import com.android.adblib.tools.debugging.scope
import com.android.adblib.withTextCollector
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Duration

internal class AppProcessNameRetriever(private val process: AppProcess) {

    val logger = adbLogger(process.device.session)

    suspend fun retrieve(retryCount: Int, retryDelay: Duration): String {
        return process.jdwpProcess?.let {
            retrieveProcessNameFromJdwpProcess(it)
        } ?: run {
            retrieveProcessNameFromProc(retryCount, retryDelay)
        }
    }

    private suspend fun retrieveProcessNameFromJdwpProcess(jdwpProcess: JdwpProcess): String {
        logger.debug { "Looking for JDWP process name of $this" }
        return withContext(process.scope.coroutineContext) {
            // Wait for the process name to be valid (i.e. not empty)
            val processName = jdwpProcess.propertiesFlow.mapNotNull { props ->
                val name = props.processName
                if (name.isNullOrEmpty()) {
                    null
                } else {
                    name
                }
            }.first()

            logger.debug { "Found JDWP process name of $this: $processName" }
            processName
        }
    }

    private suspend fun retrieveProcessNameFromProc(retryCount: Int, retryDelay: Duration): String {
        return withContext(process.scope.coroutineContext) {
            val cmd = "cat /proc/${process.pid}/cmdline"
            logger.debug { "Looking for process name of $process using '$cmd'" }
            var lastValidName = ""
            var retryAttempt = 0
            while (retryAttempt <= retryCount) {
                val name = execProcCmdLineAndReturnValidOrEmpty()
                if (name.isEmpty()) {
                    // Try again a little later
                    delay(retryDelay.toMillis())
                    ++retryAttempt
                    lastValidName = ""
                    continue
                }

                // NOTE: To mitigate the issue raised in b/316931621 we retry `cat proc/<pid>/cmdline`
                // to ensure we are not seeing a temporarily invalid cmdline value
                if (name == lastValidName) {
                    return@withContext name
                } else {
                    lastValidName = name
                    // We got a valid name, but need to quickly recheck it.
                    // Don't count it as a retry
                    delay (100)
                }
            }

            throw IOException("Process name could not be retrieved from the device")
        }
    }

    private suspend fun execProcCmdLineAndReturnValidOrEmpty(): String {
        val cmd = "cat /proc/${process.pid}/cmdline"
        logger.debug { "Looking for process name of $process using '$cmd'" }
        val output = try {
            process.device.session.deviceServices
                .shellCommand(process.device.selector, cmd)
                .withTextCollector()
                .execute()
                .first()
        } catch (t: Throwable) {
            t.rethrowCancellation()
            logger.warn(
                t,
                "Error executing shell command '$cmd' on device ${process.device}"
            )
            // try again
            return ""
        }

        // 'cmdline' is argv, where each arg is terminated by a `nul`, so
        // the process name is all characters of the output until the first `nul`.
        val name = output.stdout.takeWhile { it != 0.toChar() }
        when {
            name.isBlank() || name == "<pre-initialized>" -> {
                return ""
            }

            output.stderr.contains("No such file or directory") -> {
                logger.debug { "`cmdline` file for process ${process.pid} does not exist: stderr=${output.stderr}" }
                throw IOException("Unable to retrieve process name of process ${process.pid}, the process has exited")
            }

            else -> {
                logger.debug { "Found app process name of $process: '$name'" }
                return name
            }
        }
    }
}
