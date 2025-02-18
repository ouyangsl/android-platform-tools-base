/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.deployer.devices;

import com.android.tools.deployer.devices.shell.Am;
import com.android.tools.deployer.devices.shell.BasicPm;
import com.android.tools.deployer.devices.shell.Chmod;
import com.android.tools.deployer.devices.shell.Chown;
import com.android.tools.deployer.devices.shell.Cmd;
import com.android.tools.deployer.devices.shell.Cp;
import com.android.tools.deployer.devices.shell.DumpPm;
import com.android.tools.deployer.devices.shell.Echo;
import com.android.tools.deployer.devices.shell.GetProp;
import com.android.tools.deployer.devices.shell.Id;
import com.android.tools.deployer.devices.shell.Ls;
import com.android.tools.deployer.devices.shell.Mkdir;
import com.android.tools.deployer.devices.shell.Rm;
import com.android.tools.deployer.devices.shell.SessionPm;
import com.android.tools.deployer.devices.shell.Stat;
import com.android.tools.deployer.devices.shell.Su;
import com.android.tools.deployer.devices.shell.Xargs;

import java.io.IOException;
import java.util.HashSet;

public class FakeDeviceLibrary {

    public FakeDevice build(DeviceId id) throws IOException {
        FakeDevice device = new FakeDevice(id);
        addShellCommands(id, device);
        return device;
    }

    public FakeDevice build(DeviceId id, String manufacturer, String model, String serial)
            throws IOException {
        FakeDevice device = new FakeDevice(id.version(), id.api(), manufacturer, model, serial);
        addShellCommands(id, device);
        return device;
    }

    private static void addShellCommands(DeviceId id, FakeDevice device) {
        switch (id) {
            case API_19:
                device.getShell().addCommand(new BasicPm());
                break;
            case API_21:
            case API_22:
            case API_23:
                device.getShell().addCommand(new SessionPm());
                break;
            case API_24:
            case API_25:
            case API_26:
            case API_27:
            case API_28:
            case API_29:
            case API_30:
            case API_31:
            case API_32:
            case API_33:
            case API_34:
            case API_35:
                device.getShell().addCommand(new Cmd());
                device.getShell().addCommand(new DumpPm());
                break;
            default:
                throw new IllegalStateException("No Shell set");
        }

        device.getShell().addCommand(new Am());
        device.getShell().addCommand(new GetProp());
        device.getShell().addCommand(new Mkdir());
        device.getShell().addCommand(new Chmod());
        device.getShell().addCommand(new Chown());
        device.getShell().addCommand(new Rm());
        device.getShell().addCommand(new Id());
        device.getShell().addCommand(new Cp());
        device.getShell().addCommand(new Ls());
        device.getShell().addCommand(new Stat());
        device.getShell().addCommand(new Su());
        device.getShell().addCommand(new Xargs());
        device.getShell().addCommand(new Echo());
    }
}
