/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.build.gradle.internal.variant;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.BasePlugin;
import com.android.build.gradle.tasks.Dex;
import com.android.builder.core.VariantConfiguration;

import org.gradle.api.DefaultTask;

/**
 * Base data about a variant that generates an APK file.
 */
public abstract class ApkVariantData extends BaseVariantData<ApkVariantOutputData> {

    public Dex dexTask;
    public DefaultTask installTask;
    public DefaultTask uninstallTask;

    protected ApkVariantData(
            @NonNull BasePlugin basePlugin,
            @NonNull VariantConfiguration config) {
        super(basePlugin, config);
    }

    @Override
    @NonNull
    protected ApkVariantOutputData doCreateOutput(
            @Nullable String densityFilter,
            @Nullable String abiFilter) {
        return new ApkVariantOutputData(densityFilter, abiFilter, this);
    }

    @Override
    @NonNull
    public String getDescription() {
        if (getVariantConfiguration().hasFlavors()) {
            return String.format("%s build for flavor %s",
                    getCapitalizedBuildTypeName(),
                    getCapitalizedFlavorName());
        } else {
            return String.format("%s build", getCapitalizedBuildTypeName());
        }
    }

    public boolean isSigned() {
        return getVariantConfiguration().isSigningReady();
    }

    public boolean getZipAlign() {
        return getVariantConfiguration().getBuildType().isZipAlign();
    }
}
