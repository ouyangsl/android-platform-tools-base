/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.ddmlib.internal;

import static com.android.ddmlib.IntegrationTest.getPathToAdb;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.ddmlib.AdbInitOptions;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.JdwpHandshake;
import com.android.ddmlib.idevicemanager.IDeviceManagerFactory;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.sdklib.AndroidVersion;

import org.junit.rules.ExternalResource;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class FakeAdbTestRule extends ExternalResource {

    public static final String CLIENT_PACKAGE_NAME = "com.test.integration.ddmlib";

    public static final int TEST_TIMEOUT_MS = 5000;

    public static final String SERIAL = "test_device_001";

    public static final String MANUFACTURER = "Google";

    public static final String MODEL = "Nexus Silver";

    public static final String RELEASE = "8.0";

    public static final int PID = 1234;

    private FakeAdbServer myServer;

    private final String sdk;
    private Supplier<IDeviceManagerFactory> iDeviceManagerFactoryFactory;

    public FakeAdbTestRule() {
        this("26");
    }

    public FakeAdbTestRule(String sdk) {
        this.sdk = sdk;
    }

    public FakeAdbTestRule(AndroidVersion androidVersion) {
        this(Integer.toString(androidVersion.getApiLevel()));
    }

    public FakeAdbTestRule withIDeviceManagerFactoryFactory(
            Supplier<IDeviceManagerFactory> iDeviceManagerFactoryFactory) {
        this.iDeviceManagerFactoryFactory = iDeviceManagerFactoryFactory;
        return this;
    }

    @Override
    public void before() throws Throwable {
        FakeAdbServer.Builder builder = new FakeAdbServer.Builder();
        builder.installDefaultCommandHandlers();
        myServer = builder.build();
        // Start server execution.
        myServer.start();
        // Test that we obtain 1 device via the ddmlib APIs
        AndroidDebugBridge.terminate();
        AndroidDebugBridge.enableFakeAdbServerMode(myServer.getPort());
        DdmPreferences.setJdwpProxyPort(getFreePort());

        AdbInitOptions.Builder adbInitOptions =
                AdbInitOptions.builder().setClientSupportEnabled(true);
        if (iDeviceManagerFactoryFactory != null) {
            adbInitOptions.setIDeviceManagerFactory(iDeviceManagerFactoryFactory.get());
        }
        AndroidDebugBridge.init(adbInitOptions.build());

        AndroidDebugBridge bridge = AndroidDebugBridge.createBridge(getPathToAdb().toString(),
                                                                    false);
        assertNotNull("Debug bridge", bridge);
    }

    @Override
    public void after() {
        try {
            // mServer can be null if the FakeAdbTestRule is not being used as a rule but instead
            // as a helper class to setup Adb. This is sometimes done when test want to control the
            // timing of when an adb server is started / stopped
            if (myServer != null) {
                myServer.stop();
                if (!myServer.awaitServerTermination(1000, TimeUnit.MILLISECONDS)) {
                    // Not stopping fake adb server leads to thread leaks,
                    // that's hard to debug if we ignore the fact that we didn't stop.
                    throw new RuntimeException("fake adb server didn't stop");
                }
            }
            AndroidDebugBridge.terminate();
            AndroidDebugBridge.disableFakeAdbServerMode();
        }
        catch (InterruptedException ex) {
            // disregard
        }
    }

    public FakeAdbServer getServer() {
        return myServer;
    }

    public DeviceState connectAndWaitForDevice() throws ExecutionException, InterruptedException {
        CountDownLatch deviceLatch = new CountDownLatch(1);
        AndroidDebugBridge.IDeviceChangeListener
                deviceListener
                = new AndroidDebugBridge.IDeviceChangeListener() {
            @Override
            public void deviceConnected(@NonNull IDevice device) {
                deviceLatch.countDown();
            }

            @Override
            public void deviceDisconnected(@NonNull IDevice device) { }

            @Override
            public void deviceChanged(@NonNull IDevice device, int changeMask) { }
        };
        AndroidDebugBridge.addDeviceChangeListener(deviceListener);
        DeviceState state =
                myServer.connectDevice(
                                SERIAL,
                                MANUFACTURER,
                                MODEL,
                                RELEASE,
                                sdk,
                                DeviceState.HostConnectionType.USB)
                        .get();
        assertThat(deviceLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        AndroidDebugBridge.removeDeviceChangeListener(deviceListener);
        state.setDeviceStatus(DeviceState.DeviceStatus.ONLINE);
        return state;
    }

    public static ClientImpl launchAndWaitForProcess(DeviceState device, boolean waitingForDebugger)
            throws Exception {
        return launchAndWaitForProcess(device, PID, CLIENT_PACKAGE_NAME, waitingForDebugger);
    }

    public static ClientImpl launchAndWaitForProcess(
            DeviceState device, int pid, String packageName, boolean waitingForDebugger)
            throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        ClientImpl[] launchedClient = new ClientImpl[1];
        AndroidDebugBridge.IDeviceChangeListener deviceListener =
                new AndroidDebugBridge.IDeviceChangeListener() {
                    @Override
                    public void deviceConnected(@NonNull IDevice device) { }

                    @Override
                    public void deviceDisconnected(@NonNull IDevice device) { }

                    @Override
                    public void deviceChanged(@NonNull IDevice changedDevice, int changeMask) {
                        if ((changeMask & IDevice.CHANGE_CLIENT_LIST)
                            == IDevice.CHANGE_CLIENT_LIST) {
                            latch.countDown();
                        }
                    }
                };
        // If the client is waiting for debugger attachment then we wait for the debugger status
        // change, otherwise we just wait for the
        // application ID to be returned
        int desiredEvent = waitingForDebugger ? Client.CHANGE_DEBUGGER_STATUS : Client.CHANGE_NAME;

        AndroidDebugBridge.IClientChangeListener clientListener =
                (client, changeMask) -> {
                    if (changeMask == desiredEvent) {
                        assertThat(client.isValid()).isTrue();
                        launchedClient[0] = (ClientImpl) client;
                        latch.countDown();
                    }
                };
        AndroidDebugBridge.addClientChangeListener(clientListener);
        AndroidDebugBridge.addDeviceChangeListener(deviceListener);
        device.startClient(pid, 4321, packageName, waitingForDebugger);
        assertThat(latch.await(TEST_TIMEOUT_MS, TimeUnit.HOURS)).isTrue();
        AndroidDebugBridge.removeClientChangeListener(clientListener);
        AndroidDebugBridge.removeDeviceChangeListener(deviceListener);
        return launchedClient[0];
    }

    public static void issueHandshake(SocketChannel channel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(JdwpHandshake.HANDSHAKE_LEN);
        JdwpHandshake.putHandshake(buffer);
        buffer.position(0);
        channel.write(buffer);
        buffer.position(0);
        channel.socket().setSoTimeout(TEST_TIMEOUT_MS);
        // Need to read from input stream so we can apply timeout.
        channel.socket().getInputStream().read(buffer.array());
        assertThat(JdwpHandshake.findHandshake(buffer)).isNotEqualTo(JdwpHandshake.HANDSHAKE_GOOD);
    }

    private static int getFreePort() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
        catch (IOException e) {
            return -1;
        }
    }
}
