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

package com.example.lib.testFixtures;

import com.google.common.truth.Truth;

public class LibResourcesTester {
    private int libResourceId;
    private int testFixturesResourceId;

    public LibResourcesTester(int libResourceId, int testFixturesResourceId) {
        this.libResourceId = libResourceId;
        this.testFixturesResourceId = testFixturesResourceId;
    }

    public void test() {
        Truth.assertThat(com.example.lib.R.string.libResourceString).isEqualTo(this.libResourceId);
        Truth.assertThat(com.example.lib.testFixtures.R.string.testFixturesResourceString)
                .isEqualTo(this.testFixturesResourceId);
    }
}
