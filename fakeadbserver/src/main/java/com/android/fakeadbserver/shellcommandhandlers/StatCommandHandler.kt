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
package com.android.fakeadbserver.shellcommandhandlers

import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.ShellProtocolType
import com.android.fakeadbserver.services.ShellCommandOutput

class StatCommandHandler(shellProtocolType: ShellProtocolType) : SimpleShellHandler(
    shellProtocolType,
    "stat"
) {

    val PROC_ID_REG = Regex("/proc/(\\d+)")

    override fun execute(
      fakeAdbServer: FakeAdbServer,
      statusWriter: StatusWriter,
      shellCommandOutput: ShellCommandOutput,
      device: DeviceState,
      shellCommand: String,
      shellCommandArgs: String?
    ) {
        val appId = getAppId(device, shellCommandArgs)
        if (appId == null) {
            statusWriter.writeFail()
            return
        }

        statusWriter.writeOk()
        shellCommandOutput.writeStdout("package:$appId ")
    }

    private fun getAppId(device: DeviceState, args: String?): String? {
        if (args == null) {
            return null
        }

        val matchResult = PROC_ID_REG.find(args) ?: return null
        val pid = matchResult.groups[1]!!.value.toInt()
        return device.getClient(pid)?.processName
    }
}
