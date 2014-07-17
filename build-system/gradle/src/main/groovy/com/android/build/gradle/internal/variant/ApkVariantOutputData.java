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

package com.android.build.gradle.internal.variant;

import com.android.annotations.NonNull;
import com.android.build.gradle.BasePlugin;
import com.android.build.gradle.tasks.PackageApplication;
import com.android.build.gradle.tasks.ZipAlign;

import org.gradle.api.DefaultTask;

import java.io.File;

/**
 * Base output data for a variant that generates an APK file.
 */
public class ApkVariantOutputData extends BaseVariantOutputData {

    private final ApkVariantData variantData;

    public PackageApplication packageApplicationTask;
    public ZipAlign zipAlignTask;

    public DefaultTask installTask;

    private String densityFilter;
    private String abiFilter;

    public ApkVariantOutputData(ApkVariantData apkVariantData) {
        variantData = apkVariantData;
    }

    @Override
    public void setOutputFile(@NonNull File file) {
        if (zipAlignTask != null) {
            zipAlignTask.setOutputFile(file);
        } else {
            packageApplicationTask.setOutputFile(file);
        }
    }

    @NonNull
    @Override
    public File getOutputFile() {
        if (zipAlignTask != null) {
            return zipAlignTask.getOutputFile();
        }

        return packageApplicationTask.getOutputFile();
    }

    @NonNull
    public ZipAlign createZipAlignTask(@NonNull String taskName, @NonNull File inputFile,
            @NonNull File outputFile) {
        //noinspection VariableNotUsedInsideIf
        if (zipAlignTask != null) {
            throw new RuntimeException(String.format(
                    "ZipAlign task for variant '%s' already exists.", variantData.getName()));
        }

        ZipAlign task = variantData.basePlugin.createZipAlignTask(taskName, inputFile, outputFile);

        // update variant data
        zipAlignTask = task;

        // setup dependencies
        assembleTask.dependsOn(task);

        return task;
    }
}
