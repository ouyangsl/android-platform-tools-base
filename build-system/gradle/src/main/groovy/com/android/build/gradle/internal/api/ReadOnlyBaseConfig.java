/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.internal.api;

import com.android.annotations.NonNull;
import com.android.builder.model.BaseConfig;
import com.android.builder.model.ClassField;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.io.File;
import java.util.Collection;
import java.util.Map;

/**
 * Read-only version of the BaseConfig wrapping another BaseConfig.
 *
 * In the variant API, it is important that the objects returned by the variants
 * are read-only.
 *
 * However, even though the API is defined to use the base interfaces as return
 * type (which all contain only getters), the dynamics of Groovy makes it easy to
 * actually use the setters of the implementation classes.
 *
 * This wrapper ensures that the returned instance is actually just a strict implementation
 * of the base interface and is read-only.
 */
abstract class ReadOnlyBaseConfig implements BaseConfig {

    @NonNull
    private BaseConfig baseConfig;

    protected ReadOnlyBaseConfig(@NonNull BaseConfig baseConfig) {
        this.baseConfig = baseConfig;
    }

    @NonNull
    @Override
    public String getName() {
        return baseConfig.getName();
    }

    @NonNull
    @Override
    public Map<String, ClassField> getBuildConfigFields() {
        // TODO: cache immutable map?
        return ImmutableMap.copyOf(baseConfig.getBuildConfigFields());
    }

    @NonNull
    @Override
    public Map<String, ClassField> getResValues() {
        return ImmutableMap.copyOf(baseConfig.getResValues());
    }

    @NonNull
    @Override
    public Collection<File> getProguardFiles() {
        return ImmutableList.copyOf(baseConfig.getProguardFiles());
    }

    @NonNull
    @Override
    public Collection<File> getConsumerProguardFiles() {
        return ImmutableList.copyOf(baseConfig.getConsumerProguardFiles());
    }

    @NonNull
    @Override
    public Map<String, String> getManifestPlaceholders() {
        return ImmutableMap.copyOf(baseConfig.getManifestPlaceholders());
    }
}
