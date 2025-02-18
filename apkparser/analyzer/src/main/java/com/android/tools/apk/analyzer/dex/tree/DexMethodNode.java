/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.apk.analyzer.dex.tree;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.apk.analyzer.dex.PackageTreeCreator;
import com.android.tools.apk.analyzer.internal.SigUtils;
import com.android.tools.proguard.ProguardMap;
import com.android.tools.proguard.ProguardSeedsMap;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;

public class DexMethodNode extends DexElementNode {
    private long size;

    public DexMethodNode(
            @NonNull String displayName, @Nullable ImmutableMethodReference reference) {
        super(displayName, false, reference);
    }

    @Nullable
    @Override
    public MethodReference getReference() {
        return (MethodReference) super.getReference();
    }

    @Override
    public boolean isSeed(
            @Nullable ProguardSeedsMap seedsMap, @Nullable ProguardMap map, boolean checkChildren) {
        if (seedsMap != null) {
            MethodReference reference = getReference();
            if (reference != null) {
                String className =
                        PackageTreeCreator.decodeClassName(reference.getDefiningClass(), map);
                String methodName = PackageTreeCreator.decodeMethodName(reference, map);
                String params = PackageTreeCreator.decodeMethodParams(reference, map);
                if ("<init>".equals(methodName)) {
                    methodName = SigUtils.getSimpleName(className);
                }
                return seedsMap.hasMethod(className, methodName + params);
            }
        }
        return false;
    }

    @Override
    public int getMethodDefinitionsCount() {
        return isDefined() ? 1 : 0;
    }

    @Override
    public int getMethodReferencesCount() {
        return isRemoved() ? 0 : 1;
    }

    @Override
    public void update() {}

    public void setSize(long size) {
        this.size = size;
    }

    @Override
    public long getSize() {
        return size;
    }
}
