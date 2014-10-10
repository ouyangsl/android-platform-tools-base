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

package com.android.build.gradle.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.MainOutputFile;

import java.io.File;
import java.util.Collection;
import java.util.concurrent.Callable;

/**
 * Created by jedo on 10/9/14.
 */
public class MainApkOutputFile extends ApkOutputFile implements MainOutputFile {

    public MainApkOutputFile(@NonNull OutputType outputType,
            @NonNull Collection<FilterData> filters,
            @Nullable String suffix, @NonNull Callable<File> outputFile) {
        super(outputType, filters, suffix, outputFile);
    }
}
