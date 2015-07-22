/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.pipeline;

import com.android.annotations.NonNull;

import java.util.Collections;
import java.util.List;

/**
 */
public class StreamImpl implements Stream {

    @NonNull
    private final StreamType type;
    @NonNull
    private final StreamScope scope;
    @NonNull
    private final String taskName;

    public StreamImpl(
            @NonNull StreamType type,
            @NonNull StreamScope scope,
            @NonNull String taskName) {
        this.type = type;
        this.scope = scope;
        this.taskName = taskName;
    }

    @NonNull
    @Override
    public StreamType getType() {
        return type;
    }

    @NonNull
    @Override
    public StreamScope getScope() {
        return scope;
    }

    @NonNull
    @Override
    public Object getInputs() {
        return null;
    }

    @NonNull
    @Override
    public List<Object> getDependencies() {
        return Collections.singletonList((Object) taskName);
    }
}
