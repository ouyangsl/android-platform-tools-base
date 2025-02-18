/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.fakeadbserver.shellcommandhandlers

import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.ShellProtocolType
import com.android.fakeadbserver.services.ShellCommandOutput

/**
 * A shell command handler that writes its argument(s) to `stdout`, followed by a newline
 */
class EchoCommandHandler(shellProtocolType: ShellProtocolType) : SimpleShellHandler(
    shellProtocolType, "echo"
) {

    override fun execute(
      fakeAdbServer: FakeAdbServer,
      statusWriter: StatusWriter,
      shellCommandOutput: ShellCommandOutput,
      device: DeviceState,
      shellCommand: String,
      shellCommandArgs: String?
    ) {
        statusWriter.writeOk()
        shellCommandArgs?.also {
            shellCommandOutput.writeStdout(
                expandEnvironmentalVariables(
                    device, shellCommandArgs
                )
            )
        }
        shellCommandOutput.writeStdout("\n")
        shellCommandOutput.writeExitCode(0)
    }

    private fun expandEnvironmentalVariables(
        device: DeviceState,
        shellCommandArgs: String
    ): String {
        return shellCommandArgs.replace("\$USER_ID", if (device.isRoot) "0" else "2000")
    }
}
