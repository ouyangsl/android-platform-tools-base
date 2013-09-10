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

package com.android.sdklib.local;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.repository.FullRevision;

public class LocalBuildToolPkgInfo extends LocalFullRevisionPkgInfo {
    private final BuildToolInfo mBuildToolInfo;

    public LocalBuildToolPkgInfo(@NonNull FullRevision revision, @Nullable BuildToolInfo btInfo) {
        super(revision);
        mBuildToolInfo = btInfo;
    }

    @Override
    public int getType() {
        return LocalSdk.PKG_BUILD_TOOLS;
    }

    public BuildToolInfo getBuildToolInfo() {
        return mBuildToolInfo;
    }
}
