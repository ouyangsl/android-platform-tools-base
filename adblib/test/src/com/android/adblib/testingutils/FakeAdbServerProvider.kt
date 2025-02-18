/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.adblib.testingutils

import com.android.adblib.AdbChannel
import com.android.adblib.AdbServerChannelProvider
import com.android.adblib.AdbSessionHost
import com.android.adblib.impl.channels.AdbSocketChannelImpl
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.DeviceState.HostConnectionType
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.MdnsService
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler
import com.android.fakeadbserver.hostcommandhandlers.HostCommandHandler
import com.android.fakeadbserver.hostcommandhandlers.ListDevicesCommandHandler.Companion.DEFAULT_SPEED
import kotlinx.coroutines.runInterruptible
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

/**
 * Timeout for fake adb server APIs that go through the server's internal
 * sequential executor. In most cases, API calls take only a few milliseconds,
 * but the time can dramatically increase under stress testing.
 */
val FAKE_ADB_SERVER_EXECUTOR_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(2)

class FakeAdbServerProvider: AutoCloseable {

    val inetAddress: InetAddress
        get() = server?.inetAddress ?: throw IllegalStateException("Server not started")

    val port: Int
        get() = server?.port ?: 0

    val socketAddress: InetSocketAddress
        get() = InetSocketAddress(inetAddress, port)

    private var _lastChannelProvider: TestingChannelProvider? = null

    val channelProvider: TestingChannelProvider
        get() = _lastChannelProvider ?: throw IllegalStateException("Channel provider not initialized")

    private val builder = FakeAdbServer.Builder()
    private var server: FakeAdbServer? = null

    val fakeAdbServer: FakeAdbServer
        get() = server ?: throw IllegalStateException("FakeAdbServer not initialized")

    suspend fun device(serialNumber: String): DeviceState {
        return runInterruptible {
            fakeAdbServer.deviceListCopy
                .get(5_000, TimeUnit.MILLISECONDS)
                .first {
                    it.deviceId == serialNumber
                }
        }
    }

    fun buildDefault(): FakeAdbServerProvider {
        // Build the server and configure it to use the default ADB command handlers.
        installDefaultCommandHandlers()
        build()
        return this
    }

    fun setFeatures(vararg features : String) : FakeAdbServerProvider {
        builder.setFeatures(features.toSet())
        return this
    }

    fun buildWithFeatures(features : Set<String>) : FakeAdbServerProvider {
        // Build the server and configure it to use the default ADB command handlers.
        builder.installDefaultCommandHandlers()
        builder.setFeatures(features)
        build()
        return this
    }

    fun installHostHandler(handler: HostCommandHandler): FakeAdbServerProvider {
        builder.addHostHandler(handler)
        return this
    }

    fun installDeviceHandler(handler: DeviceCommandHandler): FakeAdbServerProvider {
        builder.addDeviceHandler(handler)
        return this
    }

    fun installDefaultCommandHandlers(): FakeAdbServerProvider {
        builder.installDefaultCommandHandlers()
        return this
    }

    fun build(): FakeAdbServerProvider {
        server = builder.build()
        return this
    }

    fun connectDevice(
        deviceId: String,
        manufacturer: String,
        deviceModel: String,
        release: String,
        sdk: String,
        hostConnectionType: HostConnectionType,
        maxSpeedMbps: Long = DEFAULT_SPEED,
        negotiatedSpeedMbps: Long = DEFAULT_SPEED,
    ): DeviceState {
        return server?.connectDevice(
            deviceId,
            manufacturer,
            deviceModel,
            release,
            sdk,
            hostConnectionType,
            maxSpeedMbps = maxSpeedMbps,
            negotiatedSpeedMbps = negotiatedSpeedMbps,
        )?.get(FAKE_ADB_SERVER_EXECUTOR_TIMEOUT_MS, TimeUnit.MILLISECONDS) ?: throw IllegalArgumentException()
    }

    fun registerNetworkDevice(
        address: String,
        deviceId: String,
        manufacturer: String,
        deviceModel: String,
        release: String,
        sdk: String
    ) {
        server?.registerNetworkDevice(
            address,
            deviceId,
            manufacturer,
            deviceModel,
            release,
            sdk,
            HostConnectionType.NETWORK,
            maxSpeedMbps = DEFAULT_SPEED,
            negotiatedSpeedMbps = DEFAULT_SPEED,
        )
    }

    fun addMdnsService(service: MdnsService) {
        server?.addMdnsService(service)?.get(FAKE_ADB_SERVER_EXECUTOR_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }

    fun start(): FakeAdbServerProvider {
        server?.start()
        return this
    }

    fun stop(): FakeAdbServerProvider {
        server?.close()
        return this
    }

    fun restart() {
        // Save current server config and close it
        val config = server?.currentConfig
        server?.close()

        // Build a new server, using saved config to restore as much state as possible
        val builder = FakeAdbServer.Builder()
        config?.let { builder.setConfig(it) }
        server = builder.build()
        server?.start()
    }

    fun createChannelProvider(host: AdbSessionHost): TestingChannelProvider {
        return TestingChannelProvider(host, portSupplier = { port }).also {
            _lastChannelProvider = it
        }
    }

    override fun close() {
        server?.close()
    }

    fun awaitTermination() {
        server?.awaitServerTermination()
    }

    fun disconnectDevice(deviceSerial: String) {
        server?.disconnectDevice(deviceSerial)
    }

    class TestingChannelProvider(host: AdbSessionHost, portSupplier: suspend () -> Int) :
      AdbServerChannelProvider {

        private val provider = AdbServerChannelProvider.createOpenLocalHost(host, portSupplier)

        private val createdChannelsField = ArrayList<TestingAdbChannel>()

        val createdChannels: List<TestingAdbChannel>
            get() = synchronized(createdChannelsField) {
                createdChannelsField.toList()
            }

        val lastCreatedChannel: TestingAdbChannel?
            get() {
                return synchronized(createdChannelsField) {
                    createdChannelsField.lastOrNull()
                }
            }

        override suspend fun createChannel(timeout: Long, unit: TimeUnit): AdbChannel {
            val channel = provider.createChannel(timeout, unit)
            return TestingAdbChannel(channel).also {
                synchronized(createdChannelsField) {
                    createdChannelsField.add(it)
                }
            }
        }
    }

    class TestingAdbChannel(private val channel: AdbChannel) : AdbChannel by channel {

        val isOpen: Boolean
            get() = (channel as AdbSocketChannelImpl).isOpen
    }
}
