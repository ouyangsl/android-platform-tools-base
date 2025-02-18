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
package com.android.fakeadbserver.devicecommandhandlers

import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.services.ExecOutput
import kotlinx.coroutines.CoroutineScope
import java.net.Socket

class AbbExecCommandHandler : DeviceCommandHandler("abb_exec") {

    override fun invoke(
        server: FakeAdbServer,
        socketScope: CoroutineScope,
        socket: Socket,
        device: DeviceState,
        args: String
    ) {

        // Acknowledge only if "abb_exec" is supported
        // TODO: Even though it is equivalent to use API level to check for abb_exec the answer
        //       should come from the list of features contained in [deviceState].
        if (device.buildVersionSdk.toInt() < 30) {
            writeFail(socket.getOutputStream())
            return
        }

        writeOkay(socket.getOutputStream())

        // Save command to logs so tests can consult them.
        device.addAbbLog(args.trim())

        // Wrap stdin/stdout and execute abb command
        val serviceOutput = ExecOutput(socket, device)
        device.serviceManager.processCommand(args.split("\u0000"), serviceOutput)
    }
}
