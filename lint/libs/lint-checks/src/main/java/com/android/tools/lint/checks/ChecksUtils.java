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

package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethodReferenceType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeVisitor;
import org.jetbrains.uast.UElement;
import java.util.Optional;

public class ChecksUtils {
    /**
     * @param uElement The element to check. Will check the parent if not of the correct class.
     * @param cls The class to look for.
     * @return The first element of the class you passed in, checking parents if the element is not
     *         of the class.
     */
    @NonNull
    public static Optional<? extends UElement> getContainingElementOfType(
            @NonNull final UElement uElement,
            @NonNull final Class<? extends UElement> cls) {
        if (uElement.getClass().equals(cls)) {
            return Optional.ofNullable(uElement);
        } else {
            // If it's null, then we've reached the top of the tree and are done.
            if (uElement.getUastParent() != null) {
                return getContainingElementOfType(uElement.getUastParent(), cls);
            } else {
                return Optional.empty();
            }
        }
    }
}
