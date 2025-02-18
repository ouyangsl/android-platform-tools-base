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
package com.android.fakeadbserver.statechangehubs

/**
 * This class is the primary class that effects the changes to client states and propagates the
 * changes to existing, registered monitoring connections. It acts mainly as a multiplexer for
 * for events to existing client/server connections.
 */
class ClientStateChangeHub : StateChangeHub<ClientStateChangeHandlerFactory>() {

    fun clientListChanged() {
        synchronized(mHandlers) {
            mHandlers.forEach { (stateChangeQueue: StateChangeQueue, clientStateChangeHandlerFactory: ClientStateChangeHandlerFactory) ->
                stateChangeQueue.add(
                    clientStateChangeHandlerFactory
                        .createClientListChangedHandler()
                )
            }
        }
    }

    fun appProcessListChanged() {
        synchronized(mHandlers) {
            mHandlers.forEach { (stateChangeQueue: StateChangeQueue, clientStateChangeHandlerFactory: ClientStateChangeHandlerFactory) ->
                stateChangeQueue.add(
                    clientStateChangeHandlerFactory
                        .createAppProcessListChangedHandler()
                )
            }
        }
    }

    fun logcatMessageAdded(message: String) {
        synchronized(mHandlers) {
            mHandlers.forEach { (stateChangeQueue: StateChangeQueue, clientStateChangeHandlerFactory: ClientStateChangeHandlerFactory) ->
                stateChangeQueue
                    .add(
                        clientStateChangeHandlerFactory
                            .createLogcatMessageAdditionHandler(message)
                    )
            }
        }
    }
}
