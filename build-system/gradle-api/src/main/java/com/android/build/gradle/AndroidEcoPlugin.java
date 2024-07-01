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

package com.android.build.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.plugins.software.RegistersSoftwareTypes;

import java.util.Map;

@SuppressWarnings("UnstableApiUsage")
@RegistersSoftwareTypes({AppPlugin.class})
class AndroidEcoPlugin implements Plugin<Settings> {

    @Override
    public void apply(Settings target) {
        target.apply(Map.of("plugin", "com.android.internal.ecosystem.plugin"));
    }
}
