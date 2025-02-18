/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.ddmlib.IDevice;
import com.android.tools.deployer.tasks.Canceller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class InstallOptions {
    // This value comes from the framework and should not be changed.
    public static final String CURRENT_USER = "current";

    private final List<String> allFlags;
    private final List<String> userFlags;
    private final boolean assumeVerified;

    private final Canceller canceller;

    private InstallOptions(
            List<String> allFlags,
            List<String> userFlags,
            boolean assumeVerified,
            Canceller canceller) {
        this.allFlags = allFlags;
        this.userFlags = userFlags;
        this.assumeVerified = assumeVerified;
        this.canceller = canceller;
    }

    public List<String> getFlags() {
        return allFlags;
    }

    public List<String> getUserFlags() {
        return userFlags;
    }

    public Canceller getCancelChecker() {
        return canceller;
    }

    public boolean getAssumeVerified() {
        return assumeVerified;
    }

    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.flags.addAll(this.allFlags);
        builder.userFlags.addAll(this.userFlags);
        return builder;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<String> flags;
        private final List<String> userFlags;
        private boolean assumeVerified = false;

        private Canceller canceller = Canceller.NO_OP;

        private Builder() {
            this.flags = new ArrayList<>();
            this.userFlags = new ArrayList<>();
        }

        // Allows test packages to be installed.
        public Builder setAllowDebuggable() {
            flags.add("-t");
            return this;
        }

        public Builder setAllowDowngrade() {
            flags.add("-d");
            return this;
        }

        // Grants all runtime permissions listed in the application manifest to the application upon install.
        public Builder setGrantAllPermissions() {
            flags.add("-g");
            return this;
        }

        // Allows the package to be visible from other packages.
        public Builder setForceQueryable() {
            flags.add("--force-queryable");
            return this;
        }

        // Installs application as non-ephemeral full app.
        public Builder setInstallFullApk() {
            flags.add("--full");
            return this;
        }

        public Builder setInstallOnUser(String targetUserId) {
            flags.add("--user");
            flags.add(targetUserId);
            return this;
        }

        // Instruct PM to not kill the process on install.
        public Builder setDontKill() {
            flags.add("--dont-kill");
            return this;
        }

        // Skips package verification if possible.
        public Builder setSkipVerification(IDevice device, String packageName) {
            String skipVerificationString =
                    ApkVerifierTracker.getSkipVerificationInstallationFlag(device, packageName);
            if (skipVerificationString != null) {
                flags.add(skipVerificationString);
            }
            return this;
        }

        // Sets a string of user-specified installation flags to be passed to the installer.
        public Builder setUserInstallOptions(String[] userSpecifiedFlags) {
            if (userSpecifiedFlags == null) {
                return this;
            }
            flags.addAll(Arrays.asList(userSpecifiedFlags));
            userFlags.addAll(Arrays.asList(userSpecifiedFlags));
            return this;
        }

        public Builder setUserInstallOptions(String userSpecifiedFlag) {
            if (userSpecifiedFlag == null) {
                return this;
            }
            flags.add(userSpecifiedFlag);
            userFlags.add(userSpecifiedFlag);
            return this;
        }

        public Builder setCancelChecker(Canceller canceller) {
            this.canceller = canceller;
            return this;
        }

        public Builder setAssumeVerified(boolean assumeVerified) {
            this.assumeVerified = assumeVerified;
            return this;
        }

        public InstallOptions build() {
            return new InstallOptions(flags, userFlags, assumeVerified, canceller);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        InstallOptions that = (InstallOptions) o;
        return allFlags.equals(that.allFlags);
    }

    @Override
    public int hashCode() {
        return allFlags.hashCode();
    }
}
