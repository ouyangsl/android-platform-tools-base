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
package com.android.tools.deployer;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.SimpleConnectedSocket;
import com.android.ddmlib.TimeoutException;
import com.android.utils.ILogger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AdbInstallerChannelManager {

    private static final HashMap<String, AdbInstallerChannel> channels = new HashMap<>();

    private AdbInstallerChannelManager() {}

    public static AdbInstallerChannel getChannel(
            AdbClient client, String version, ILogger logger, AdbInstaller.Mode mode)
            throws IOException {
        String deviceId = client.getSerial();

        // Make sure the Channel was not closed. Returning a closed Channel would not fail the
        // Installer client but it would cause the installer binary to be pushed again to the
        // device. This is an optimization.
        if (channels.containsKey(deviceId)) {
            AdbInstallerChannel channel = channels.get(deviceId);
            if (channel.isClosed()) {
                channels.remove(deviceId);
            }
        }

        if (!channels.containsKey(deviceId)) {
            logger.info("Created SocketChannel to '" + deviceId + "'");
            AdbInstallerChannel channel = createChannel(client, version, logger, mode);
            channels.put(deviceId, channel);
        }
        return channels.get(deviceId);
    }

    private static AdbInstallerChannel createChannel(
            AdbClient client, String version, ILogger logger, AdbInstaller.Mode mode)
            throws IOException {
        SimpleConnectedSocket channel = null;
        List<String> parameters = new ArrayList<>();
        parameters.add("-version=" + version);
        if (mode == AdbInstaller.Mode.DAEMON) {
            parameters.add("-daemon");
        }

        try {
            channel =
                    client.rawExec(AdbInstaller.INSTALLER_PATH, parameters.toArray(new String[0]));
        } catch (AdbCommandRejectedException | TimeoutException e) {
            try (SimpleConnectedSocket c = channel) {}
            throw new IOException(e);
        }
        return new AdbInstallerChannel(channel, logger);
    }

    public static void reset(AdbClient client, ILogger logger) throws IOException {
        String serial = client.getSerial();
        logger.info("Reset SocketChannel to '" + serial + "'");
        try (AdbInstallerChannel c = channels.get(serial)) {
            channels.remove(serial);
        }
    }
}
