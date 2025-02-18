/*
 * Copyright (C) 2024 The Android Open Source Project
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

public class ZipEntryInfo {
    public enum Alignment {
        ALIGNMENT_NONE(""),
        ALIGNMENT_4K("4k"),
        ALIGNMENT_16K("16k"),
        ;

        public final String text;

        Alignment(String text) {
            this.text = text;
        }
    }

    public long size;
    public Alignment alignment;
    public boolean isCompressed;

    public ZipEntryInfo(long size, Alignment alignment, boolean isCompressed) {
        this.size = size;
        this.alignment = alignment;
        this.isCompressed = isCompressed;
    }
}
