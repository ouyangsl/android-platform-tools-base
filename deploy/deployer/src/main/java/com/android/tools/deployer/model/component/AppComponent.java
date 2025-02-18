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

package com.android.tools.deployer.model.component;

import com.android.annotations.NonNull;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.tools.deployer.DeployerException;
import com.android.tools.manifest.parser.components.ManifestAppComponentInfo;
import com.android.utils.ILogger;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public abstract class AppComponent {
    @NonNull protected final String appId;

    @NonNull protected final ManifestAppComponentInfo info;

    @NonNull protected final ILogger logger;

    // The timeout is quite large to accommodate ARM emulators.
    private final long SHELL_TIMEOUT = 15;

    private final TimeUnit SHELL_TIMEUNIT = TimeUnit.SECONDS;

    protected String getFQEscapedName() {
        return getFQEscapedName(appId, info.getQualifiedName());
    }

    protected AppComponent(
            @NonNull String appId,
            @NonNull ManifestAppComponentInfo info,
            @NonNull ILogger logger) {
        this.appId = appId;
        this.info = info;
        this.logger = logger;
    }

    public abstract void activate(
            @NonNull String extraFlags,
            Mode activationMode,
            @NonNull IShellOutputReceiver receiver,
            @NonNull IDevice device)
            throws DeployerException;

    protected void runShellCommand(
            @NonNull String command,
            @NonNull IShellOutputReceiver receiver,
            @NonNull IDevice device)
            throws DeployerException {
        try {
            device.executeShellCommand(command, receiver, SHELL_TIMEOUT, SHELL_TIMEUNIT);
        }
        catch (TimeoutException
                | AdbCommandRejectedException
                | ShellCommandUnresponsiveException
                | IOException e) {
            throw DeployerException.componentActivationException(e.getMessage());
        }
    }

    @NonNull
    public static String getFQEscapedName(@NonNull String appId, @NonNull String componentFqName) {
        // Escape name declared as inner class name (resulting in foo.bar.Activity$SubActivity).
        return appId + "/" + componentFqName.replace("$", "\\$");
    }

    public enum Mode {
        RUN,
        DEBUG
    }
}
