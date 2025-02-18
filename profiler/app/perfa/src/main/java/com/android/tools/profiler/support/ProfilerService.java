/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.profiler.support;

import com.android.tools.profiler.support.profilers.EventProfiler;
import com.android.tools.profiler.support.profilers.ProfilerComponent;
import java.util.ArrayList;
import java.util.List;

/** WIP: This is the JVMTI version of the profiler service. */
@SuppressWarnings("unused") // This class is used via instrumentation
public class ProfilerService {
    private static ProfilerService sInstance;
    private final List<ProfilerComponent> mComponents;

    /** Entry point for legacy profilers in the app. This is called via JNI from JVMTI agent. */
    public static void initialize(boolean keyboard_event_enabled) {
        if (sInstance != null) {
            return;
        }
        sInstance = new ProfilerService(keyboard_event_enabled);
    }

    public ProfilerService(boolean keyboard_event_enabled) {
        mComponents = new ArrayList<ProfilerComponent>();
        // TODO: Remove when fully moved to JVMTI
        mComponents.add(new EventProfiler(keyboard_event_enabled));

        // TODO handle shutdown properly
    }
}
