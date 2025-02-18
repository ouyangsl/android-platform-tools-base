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

package com.android.build.gradle.integration.common.truth;

import static com.android.testutils.truth.DexClassSubject.assertThat;

import com.android.annotations.NonNull;
import com.android.testutils.apk.Dex;
import com.android.testutils.apk.DexAndroidArchive;
import com.android.testutils.truth.DexClassSubject;
import com.android.testutils.truth.DexSubject;
import com.android.testutils.truth.IndirectSubject;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedClassDef;
import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

/** Truth support for apk files. */
@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
public class AbstractDexAndroidSubject<
                S extends AbstractDexAndroidSubject<S, T>, T extends DexAndroidArchive>
        extends AbstractAndroidSubject<S, T> {

    public AbstractDexAndroidSubject(@NonNull FailureMetadata failureMetadata, @NonNull T subject) {
        super(failureMetadata, subject);
    }

    @NonNull
    public final IndirectSubject<DexSubject> hasMainDexFile() {
        try {
            Optional<Dex> dex = actual().getMainDexFile();
            if (!dex.isPresent()) {
                failWithoutActual(
                        Fact.simpleFact(String.format("'%s' does not contain main dex", actual())));
            }
            return () -> DexSubject.assertThat(dex.get());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @NonNull
    public final IndirectSubject<DexClassSubject> hasMainClass(
            @NonNull final String expectedClassName) {
        DexBackedClassDef dexBackedClassDef = getMainClass(expectedClassName);
        if (dexBackedClassDef == null) {
            fail("contains class", expectedClassName);
        }
        return () -> assertThat(dexBackedClassDef);
    }

    @NonNull
    public final IndirectSubject<DexClassSubject> hasSecondaryClass(
            @NonNull final String expectedClassName) {
        DexBackedClassDef dexBackedClassDef = getSecondaryClass(expectedClassName);
        if (dexBackedClassDef == null) {
            fail("contains class", expectedClassName);
        }
        return () -> assertThat(dexBackedClassDef);
    }

    @NonNull
    public final IndirectSubject<DexClassSubject> hasClass(@NonNull final Class clazz) {
        return hasClass("L" + clazz.getName().replace('.', '/') + ";");
    }

    @NonNull
    public final IndirectSubject<DexClassSubject> hasClass(
            @NonNull final String expectedClassName) {
        DexBackedClassDef mainClassDef = getMainClass(expectedClassName);
        DexBackedClassDef classDef =
                mainClassDef != null ? mainClassDef : getSecondaryClass(expectedClassName);
        if (classDef == null) {
            fail("contains class", expectedClassName);
        }
        return () -> assertThat(classDef);
    }

    public void hasDexVersion(int expectedDexVersion) {
        try {
            int presentDexVersion =
                    actual().getMainDexFile().orElseThrow(AssertionError::new).getVersion();
            if (expectedDexVersion != presentDexVersion) {
                fail(
                        String.format(
                                "expected dex version %s, but has %s",
                                expectedDexVersion, presentDexVersion));
            }
        } catch (IOException e) {
            failWithoutActual(
                    Fact.simpleFact(String.format("'%s' does not contain main dex", actual())));
        }
    }

    private DexBackedClassDef getMainClass(@NonNull String className) {
        try {
            Optional<Dex> classesDex = actual().getMainDexFile();
            if (!classesDex.isPresent()) {
                fail("has main dex file");
                return null;
            }
            return classesDex.get().getClasses().get(className);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private DexBackedClassDef getSecondaryClass(@NonNull String className) {
        try {
            for (Dex dex : actual().getSecondaryDexFiles()) {
                DexBackedClassDef classDef = dex.getClasses().get(className);
                if (classDef != null) {
                    return classDef;
                }
            }
            return null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
