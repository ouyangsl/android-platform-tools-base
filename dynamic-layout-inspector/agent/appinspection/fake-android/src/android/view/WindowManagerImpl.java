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
import android.graphics.Rect;
import android.os.Build;
import androidx.annotation.NonNull;

public class WindowManagerImpl implements WindowManager {
    public WindowMetrics mMetrics = new WindowMetrics(new Rect(0, 0, 1440, 3120));
    public Display mDisplay = new Display(new Point(1440, 3120));

    @Override
    @NonNull
    public WindowMetrics getCurrentWindowMetrics() {
        if (Build.VERSION.SDK_INT < 30) {
            throw new RuntimeException();
        }
        return mMetrics;
    }

    @Override
    @NonNull
    public Display getDefaultDisplay() {
        return mDisplay;
    }

    @Override
    @NonNull
    public WindowMetrics getMaximumWindowMetrics() {
        if (Build.VERSION.SDK_INT < 30) {
            throw new RuntimeException();
        }
        return mMetrics;
    }
}
