/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.apk.analyzer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class VirtualEntryCalculator {
    private final Path path;

    public VirtualEntryCalculator(Path path) {
        this.path = path;
    }

    public long getCount() throws IOException {
        long count = 0;
        try (ZipInputStream zis =
                new ZipInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if (GzipSizeCalculatorTest.isVirtualEntry(ze.getName())) {
                    count += 1;
                }
            }
        }
        return count;
    }
}
