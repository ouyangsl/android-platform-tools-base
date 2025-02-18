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
package com.android.tools.deployer.rules;


import com.android.ddmlib.AndroidDebugBridge;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.fakeadbserver.hostcommandhandlers.FeaturesCommandHandler;
import com.android.fakeadbserver.hostcommandhandlers.HostFeaturesCommandHandler;
import com.android.fakeadbserver.hostcommandhandlers.TrackDevicesCommandHandler;
import com.android.tools.deployer.devices.DeviceId;
import com.android.tools.deployer.devices.FakeDevice;
import com.android.tools.deployer.devices.FakeDeviceHandler;
import com.android.tools.deployer.devices.FakeDeviceLibrary;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class FakeDeviceConnection implements TestRule {

    private final DeviceId deviceId;
    private FakeDevice device;
    private FakeAdbServer server;

    public FakeDeviceConnection(DeviceId deviceId) {
        this.deviceId = deviceId;
    }

    public DeviceId getDeviceId() { return deviceId; }

    public FakeDevice getDevice() {
        return device;
    }

    public FakeAdbServer getServer() {
        return server;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                startFakeAdbServer();
                try {
                    base.evaluate();
                } finally {
                    stopFakeAdbServer();
                }
            }
        };
    }

    private void startFakeAdbServer() throws Exception {
        device = new FakeDeviceLibrary().build(deviceId);
        FakeAdbServer.Builder builder = new FakeAdbServer.Builder();
        builder.addHostHandler(new TrackDevicesCommandHandler());

        FakeDeviceHandler handler = new FakeDeviceHandler();
        builder.addDeviceHandler(handler);
        builder.addHostHandler(new FeaturesCommandHandler());
        builder.addHostHandler(new HostFeaturesCommandHandler());

        server = builder.build();
        handler.connect(device, server);
        server.start();

        AndroidDebugBridge.enableFakeAdbServerMode(server.getPort());
    }

    private void stopFakeAdbServer() throws Exception {
        device.shutdown();
        server.close();
        AndroidDebugBridge.terminate();
        AndroidDebugBridge.disableFakeAdbServerMode();
    }
}
