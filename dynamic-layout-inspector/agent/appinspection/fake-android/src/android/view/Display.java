/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.view;

import android.graphics.Point;

public class Display {

    public static final int DEFAULT_DISPLAY = 0;

    private final Point mRealSize;

    public Display(Point realSize) {
        mRealSize = realSize;
    }

    public void getRealSize(Point outSize) {
        outSize.x = mRealSize.x;
        outSize.y = mRealSize.y;
    }

    public int getRotation() {
        return Surface.ROTATION_90;
    }

    public int getDisplayId() {
        return DEFAULT_DISPLAY;
    }
}
