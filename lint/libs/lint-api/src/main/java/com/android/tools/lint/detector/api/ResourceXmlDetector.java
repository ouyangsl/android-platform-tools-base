/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tools.lint.detector.api;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;

/**
 * Specialized detector intended for XML resources. Detectors that apply to XML resources should
 * extend this detector instead since it provides special iteration hooks that are more efficient.
 */
public abstract class ResourceXmlDetector extends Detector implements XmlScanner {

    /**
     * Returns whether this detector applies to the given folder type. This allows the detectors to
     * be pruned from iteration, so for example when we are analyzing a string value file we don't
     * need to look up detectors related to layout. Doesn't run checks in res/raw folder unless
     * there is an explicit override.
     *
     * @param folderType the folder type to be visited
     * @return true if this detector can apply to resources in folders of the given type
     */
    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType != ResourceFolderType.RAW;
    }

    @Override
    public void run(@NonNull Context context) {
        // The infrastructure should never call this method on an xml detector since
        // it will run the various visitors instead
        assert false : this.getClass().getName();
    }
}
