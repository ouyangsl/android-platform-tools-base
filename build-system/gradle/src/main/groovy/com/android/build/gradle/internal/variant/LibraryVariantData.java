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
import com.android.build.gradle.internal.core.GradleVariantConfiguration;

/**
 * Data about a variant that produce a Library bundle (.aar)
 */
public class LibraryVariantData extends BaseVariantData<LibVariantOutputData> implements TestedVariantData {

    @Nullable
    private TestVariantData testVariantData = null;

    public LibraryVariantData(
            @NonNull BasePlugin basePlugin,
            @NonNull GradleVariantConfiguration config) {
        super(basePlugin, config);

        // create default output
        createOutput(null, null);
    }

    @NonNull
    @Override
    protected LibVariantOutputData doCreateOutput(
            @Nullable String densityFilter,
            @Nullable String abiFilter) {
        return new LibVariantOutputData(densityFilter, abiFilter, this);
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

    @Override
    public void setTestVariantData(@Nullable TestVariantData testVariantData) {
        this.testVariantData = testVariantData;
    }

    @Override
    @Nullable
    public TestVariantData getTestVariantData() {
        return testVariantData;
    }
}
