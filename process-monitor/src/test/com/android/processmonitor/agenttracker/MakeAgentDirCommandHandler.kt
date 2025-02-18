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
package com.android.processmonitor.agenttracker

import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.ShellProtocolType
import com.android.fakeadbserver.services.ShellCommandOutput
import com.android.fakeadbserver.shellcommandhandlers.ShellHandler
import com.android.fakeadbserver.shellcommandhandlers.StatusWriter
import com.android.processmonitor.agenttracker.AgentProcessTracker.Companion.AGENT_DIR

private const val CMD = "mkdir -p $AGENT_DIR; chmod 755 $AGENT_DIR; chown shell:shell $AGENT_DIR"

/** Simulates the execution of the command that creates the directory for the tracking agent */
internal class MakeAgentDirCommandHandler : ShellHandler(ShellProtocolType.SHELL_V2) {

    val invocations = mutableListOf<String>()

    override fun shouldExecute(
        shellCommand: String,
        shellCommandArgs: String?
    ): Boolean {
        return "$shellCommand $shellCommandArgs" == CMD
    }

    override fun execute(
        fakeAdbServer: FakeAdbServer,
        statusWriter: StatusWriter,
        shellCommandOutput: ShellCommandOutput,
        device: DeviceState,
        shellCommand: String,
        shellCommandArgs: String?
    ) {
        statusWriter.writeOk()
        invocations.add(device.deviceId)
        shellCommandOutput.writeStdout("")
        shellCommandOutput.writeExitCode(0)
    }
}
