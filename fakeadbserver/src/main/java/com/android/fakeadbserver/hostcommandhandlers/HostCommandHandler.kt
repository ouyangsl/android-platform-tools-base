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
package com.android.fakeadbserver.hostcommandhandlers

import com.android.fakeadbserver.CommandHandler
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.DeviceStateSelector
import com.android.fakeadbserver.FakeAdbServer
import kotlinx.coroutines.CoroutineScope
import java.net.Socket

/**
 * HostCommandHandlers handle commands directly to the ADB Server itself, such as host:kill,
 * host:devices, etc.... This does not include host commands that are directed at a specific device.
 */
abstract class HostCommandHandler : CommandHandler() {

    /**
     * Priority when calling [handles] on each registered [HostCommandHandler].
     * Higher priority handlers are called first.
     */
    open val priority: Int
        get() = 0

    abstract fun handles(command: String): Boolean

    /**
     * This is the main execution method of the command.
     *
     * @param fakeAdbServer  Fake ADB Server itself.
     * @param responseSocket Socket for this connection.
     * @param deviceSelector Provides access to the target device for the command, if any.
     * @param args           Arguments for the command.
     * @return a boolean, with true meaning keep the connection alive, false to close the connection
     */
    abstract fun invoke(
        fakeAdbServer: FakeAdbServer,
        socketScope: CoroutineScope,
        responseSocket: Socket,
        deviceSelector: DeviceStateSelector,
        command: String,
        args: String
    ): Boolean
}

/**
 * A [HostCommandHandler] with a fixed [command].
 */
abstract class SimpleHostCommandHandler(protected val command: String) : HostCommandHandler() {

    override fun handles(command: String): Boolean {
        return command == this.command
    }

    override fun invoke(
        fakeAdbServer: FakeAdbServer,
        socketScope: CoroutineScope,
        responseSocket: Socket,
        deviceSelector: DeviceStateSelector,
        command: String,
        args: String
    ): Boolean {
        val deviceState = when(val deviceResult = deviceSelector.invoke(reportError = true)) {
            DeviceStateSelector.DeviceResult.Ambiguous,
            DeviceStateSelector.DeviceResult.None ->  {
                // Error has been reported, use `null`
                null
            }
            is DeviceStateSelector.DeviceResult.One -> deviceResult.deviceState
        }
        return invoke(fakeAdbServer, responseSocket, deviceState, args)
    }

    /**
     * This is the main execution method of this [SimpleHostCommandHandler].
     *
     * @param fakeAdbServer  Fake ADB Server itself.
     * @param responseSocket Socket for this connection.
     * @param device         Target device for the command, if any.
     * @param args           Arguments for the command.
     * @return a boolean, with true meaning keep the connection alive, false to close the connection
     */
    abstract fun invoke(
        fakeAdbServer: FakeAdbServer,
        responseSocket: Socket,
        device: DeviceState?,
        args: String
    ): Boolean
}
