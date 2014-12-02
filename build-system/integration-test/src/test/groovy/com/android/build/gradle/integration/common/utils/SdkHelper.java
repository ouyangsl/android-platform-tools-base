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

package com.android.build.gradle.integration.common.utils;

import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.builder.core.ApkInfoParser;
import com.android.ide.common.internal.CommandLineRunner;
import com.android.ide.common.internal.LoggedErrorException;
import com.android.utils.StdLogger;

import org.gradle.api.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Helper for SDK related functions.
 */
public class SdkHelper {
    /**
     * Returns the SDK folder as built from the Android source tree.
     */
    public static File findSdkDir() {
        String androidHome = System.getenv("ANDROID_HOME");
        if (androidHome != null) {
            File f = new File(androidHome);
            if (f.isDirectory()) {
                return f;
            } else {
                System.out.println("Failed to find SDK in ANDROID_HOME=" + androidHome);
            }
        }
        return null;
    }

    public static File getAapt() {
        File aapt = new File(findSdkDir(), "build-tools/20.0.0/aapt");
        assertTrue("Test requires build-tools 20.0.0", aapt.isFile());
        return aapt;
    }
}
