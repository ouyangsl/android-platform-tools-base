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

package com.android.testutils.truth;

import static com.android.tools.smali.dexlib2.Opcode.INVOKE_DIRECT;
import static com.android.tools.smali.dexlib2.Opcode.INVOKE_DIRECT_RANGE;
import static com.android.tools.smali.dexlib2.Opcode.INVOKE_INTERFACE;
import static com.android.tools.smali.dexlib2.Opcode.INVOKE_INTERFACE_RANGE;
import static com.android.tools.smali.dexlib2.Opcode.INVOKE_STATIC;
import static com.android.tools.smali.dexlib2.Opcode.INVOKE_STATIC_RANGE;
import static com.android.tools.smali.dexlib2.Opcode.INVOKE_SUPER;
import static com.android.tools.smali.dexlib2.Opcode.INVOKE_SUPER_RANGE;
import static com.android.tools.smali.dexlib2.Opcode.INVOKE_VIRTUAL;
import static com.android.tools.smali.dexlib2.Opcode.INVOKE_VIRTUAL_RANGE;
import static com.google.common.truth.Truth.assertAbout;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.smali.dexlib2.DebugItemType;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedClassDef;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedField;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedMethod;
import com.android.tools.smali.dexlib2.iface.debug.DebugItem;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.google.common.collect.Lists;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@SuppressWarnings("NonBooleanMethodNameMayNotStartWithQuestion")
public class DexClassSubject extends Subject<DexClassSubject, DexBackedClassDef> {

    public static Subject.Factory<DexClassSubject, DexBackedClassDef> dexClasses() {
        return DexClassSubject::new;
    }

    public static DexClassSubject assertThat(DexBackedClassDef subject) {
        return assertAbout(dexClasses()).that(subject);
    }

    private DexClassSubject(
            @NonNull FailureMetadata failureStrategy, @Nullable DexBackedClassDef subject) {
        super(failureStrategy, subject);
    }

    public void hasSuperclass(@NonNull String name) {
        if (assertSubjectIsNonNull() && !name.equals(actual().getSuperclass())) {
            fail("has superclass", name);
        }
    }

    public void hasMethod(@NonNull String name) {
        if (assertSubjectIsNonNull() && !checkHasMethod(name)) {
            fail("contains method", name);
        }
    }

    public void hasMethods(@NonNull String... names) {
        if (assertSubjectIsNonNull()) {
            for (String name : names) {
                hasMethod(name);
            }
        }
    }

    public void hasMethodWithLineInfoCount(@NonNull String name, int lineInfoCount) {
        assertSubjectIsNonNull();
        for (DexBackedMethod method : actual().getMethods()) {
            if (method.getName().equals(name)) {
                if (method.getImplementation() == null) {
                    fail("contain method implementation for method " + name);
                    return;
                }
                int actualLineCnt = 0;
                for (DebugItem debugItem : method.getImplementation().getDebugItems()) {
                    if (debugItem.getDebugItemType() == DebugItemType.LINE_NUMBER) {
                        actualLineCnt++;
                    }
                }
                if (actualLineCnt != lineInfoCount) {
                    fail(
                            "method has "
                                    + lineInfoCount
                                    + " debug items, "
                                    + actualLineCnt
                                    + " are found.");
                }
                return;
            }
        }
        fail("contains method", name);
    }

    public void hasMethodThatInvokes(@NonNull String name, String descriptor) {
        hasMethodWithInvokeThatSatisfies(
                name,
                reference -> reference.toString().equals(descriptor),
                "invokes a method with the descriptor `" + descriptor + "`");
    }

    public void hasMethodThatInvokesMethod(
            @NonNull String name,
            String invokedMethodName,
            List<String> argumentsType,
            String returnType) {
        Predicate<MethodReference> predicate =
                reference -> {
                    if (!(reference.getName().equals(invokedMethodName)
                            && reference.getReturnType().toString().equals(returnType))) {
                        return false;
                    }
                    for (int i = 0; i < argumentsType.size(); i++) {
                        if (!reference
                                .getParameterTypes()
                                .get(i)
                                .toString()
                                .equals(argumentsType.get(i))) {
                            return false;
                        }
                    }
                    return true;
                };
        hasMethodWithInvokeThatSatisfies(
                name, predicate, "invokes a method with the name `" + invokedMethodName + "`");
    }

    private void hasMethodWithInvokeThatSatisfies(
            @NonNull String name,
            Predicate<MethodReference> predicate,
            @NonNull String predicateMessage) {
        assertSubjectIsNonNull();
        hasMethod(name);
        List<DexBackedMethod> methods = findMethodWithImplementation(name);
        if (methods.isEmpty()) {
            fail("contains an implementation for a method named `" + name + "`");
            return;
        }
        if (!checkHasMethodInvokes(methods, predicate)) {
            fail(predicateMessage + " from `" + name + "`");
        }
    }

    public void hasMethodThatDoesNotInvoke(@NonNull String name, String descriptor) {
        assertSubjectIsNonNull();
        hasMethod(name);
        List<DexBackedMethod> methods = findMethodWithImplementation(name);
        if (methods.isEmpty()) {
            fail("contains an implementation for a method named `" + name + "`");
            return;
        }
        if (checkHasMethodInvokes(methods, reference -> reference.toString().equals(descriptor))) {
            fail(
                    "does not invoke a method with the descriptor `"
                            + descriptor
                            + "` from `"
                            + name
                            + "`");
        }
    }

    @NonNull
    private List<DexBackedMethod> findMethodWithImplementation(@NonNull String name) {
        List methods = Lists.newArrayList();
        for (DexBackedMethod method : actual().getMethods()) {
            if (method.getName().equals(name)) {
                if (method.getImplementation() != null) {
                    methods.add(method);
                }
            }
        }
        return methods;
    }

    private static boolean checkHasMethodInvokes(
            @NonNull List<DexBackedMethod> methods, Predicate<MethodReference> predicate) {
        for (DexBackedMethod method : methods) {
            if (checkMethodInvokes(method, predicate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean checkMethodInvokes(
            @NonNull DexBackedMethod method, Predicate<MethodReference> predicate) {
        for (Instruction instruction : method.getImplementation().getInstructions()) {
            Opcode opcode = instruction.getOpcode();
            boolean isInvoke =
                    opcode == INVOKE_VIRTUAL
                            || opcode == INVOKE_SUPER
                            || opcode == INVOKE_DIRECT
                            || opcode == INVOKE_STATIC
                            || opcode == INVOKE_INTERFACE;
            boolean isInvokeRange =
                    opcode == INVOKE_VIRTUAL_RANGE
                            || opcode == INVOKE_SUPER_RANGE
                            || opcode == INVOKE_DIRECT_RANGE
                            || opcode == INVOKE_STATIC_RANGE
                            || opcode == INVOKE_INTERFACE_RANGE;
            if (isInvoke || isInvokeRange) {
                MethodReference reference =
                        isInvoke
                                ? ((MethodReference) ((Instruction35c) instruction).getReference())
                                : ((MethodReference) ((Instruction3rc) instruction).getReference());
                if (predicate.test(reference)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void hasExactFields(@NonNull Set<String> names) {
        if (assertSubjectIsNonNull() && !checkHasExactFields(names)) {
            fail("Expected exactly " + names + " fields but have " + getAllFieldNames());
        }
    }

    public void hasField(@NonNull String name) {
        if (assertSubjectIsNonNull() && !checkHasField(name)) {
            fail("contains field", name);
        }
    }

    public void hasFieldWithType(@NonNull String name, @NonNull String type) {
        if (assertSubjectIsNonNull() && !checkHasField(name, type)) {
            fail("contains field ", name + ":" + type);
        }
    }

    public void doesNotHaveField(@NonNull String name) {
        if (assertSubjectIsNonNull() && checkHasField(name)) {
            fail("does not contain field", name);
        }
    }

    public void doesNotHaveFieldWithType(@NonNull String name, @NonNull String type) {
        if (assertSubjectIsNonNull() && checkHasField(name, type)) {
            fail("does not contain field ", name + ":" + type);
        }
    }

    public void doesNotHaveMethod(@NonNull String name) {
        if (assertSubjectIsNonNull() && checkHasMethod(name)) {
            fail("does not contain method", name);
        }
    }

    public void hasAnnotations() {
        if (assertSubjectIsNonNull() && !checkHasAnnotations()) {
            fail("has annotations");
        }
    }

    public void doesNotHaveAnnotations() {
        if (assertSubjectIsNonNull() && checkHasAnnotations()) {
            fail(" does not have annotations");
        }
    }

    private boolean checkHasAnnotations() {
        return !actual().getAnnotations().isEmpty();
    }

    /** Check if the class has method with the specified name. */
    private boolean checkHasMethod(@NonNull String name) {
        for (DexBackedMethod method : actual().getMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Check if the class has field with the specified name. */
    private boolean checkHasField(@NonNull String name) {
        for (DexBackedField field : actual().getFields()) {
            if (field.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Check if the class has field with the specified name and type. */
    private boolean checkHasField(@NonNull String name, @NonNull String type) {
        for (DexBackedField field : actual().getFields()) {
            if (field.getName().equals(name) && field.getType().equals(type)) {
                return true;
            }
        }
        return false;
    }

    /** Checks the subject has the given fields and no other fields. */
    private boolean checkHasExactFields(@NonNull Set<String> names) {
        return getAllFieldNames().equals(names);
    }

    /** Returns all of the field names */
    private Set<String> getAllFieldNames() {
        return StreamSupport.stream(actual().getFields().spliterator(), false)
                .map(DexBackedField::getName)
                .collect(Collectors.toSet());
    }

    private boolean assertSubjectIsNonNull() {
        if (actual() == null) {
            fail("Cannot assert about the contents of a dex class that does not exist.");
            return false;
        }
        return true;
    }

    @Override
    protected String actualCustomStringRepresentation() {
        String subjectName = null;
        if (actual() != null) {
            subjectName = actual().getType();
        }
        if (internalCustomName() != null) {
            return internalCustomName() + " (<" + subjectName + ">)";
        } else {
            return "<" + subjectName + ">";
        }
    }
}
