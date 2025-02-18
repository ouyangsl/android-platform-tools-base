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
import com.android.tools.proguard.ProguardMap;
import com.android.tools.proguard.ProguardSeedsMap;
import com.android.tools.smali.dexlib2.iface.reference.TypeReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableTypeReference;

import java.util.Comparator;

public class DexClassNode extends DexElementNode {
    private long size = 0;

    public DexClassNode(@NonNull String displayName, @Nullable ImmutableTypeReference reference) {
        super(displayName, true, reference);
    }

    @Override
    public boolean isSeed(
            @Nullable ProguardSeedsMap seedsMap, @Nullable ProguardMap map, boolean checkChildren) {
        if (seedsMap != null) {
            TypeReference reference = getReference();
            if (reference != null) {
                if (seedsMap.hasClass(
                        PackageTreeCreator.decodeClassName(reference.getType(), map))) {
                    return true;
                }
            }
        }
        return super.isSeed(seedsMap, map, checkChildren);
    }

    @Override
    public long getSize() {
        long size = this.size;
        for (int i = 0, n = getChildCount(); i < n; i++) {
            DexElementNode node = getChildAt(i);
            //defined child nodes are already counted in size
            if (!node.isDefined()) {
                size += node.getSize();
            }
        }
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    @Nullable
    @Override
    public TypeReference getReference() {
        return (TypeReference) super.getReference();
    }

    @Nullable
    public DexMethodNode getMethod(@NonNull String qualifiedMethodName) {
        return getChildByType(qualifiedMethodName, DexMethodNode.class);
    }

    @Nullable
    public DexFieldNode getField(@NonNull String qualifiedFieldName) {
        return getChildByType(qualifiedFieldName, DexFieldNode.class);
    }

    @Override
    public void update() {
        super.update();
        int methodDefinitions = 0;
        int methodReferences = 0;

        for (int i = 0, n = getChildCount(); i < n; i++) {
            DexElementNode node = getChildAt(i);
            methodDefinitions += node.getMethodDefinitionsCount();
            methodReferences += node.getMethodReferencesCount();
        }
        setMethodDefinitionsCount(methodDefinitions);
        setMethodReferencesCount(methodReferences);
    }

    /*
     * Ensure that DexReferencesNode is always the first node.
     */
    @Override
    public void sort(Comparator<DexElementNode> comparator) {
        if (getChildCount() == 0) {
            return;
        }
        DexElementNode first = getChildAt(0);
        if (first instanceof DexReferencesNode) {
            remove(0);
        }
        super.sort(comparator);
        if (first instanceof DexReferencesNode) {
            insert(first, 0);
        }
    }
}
