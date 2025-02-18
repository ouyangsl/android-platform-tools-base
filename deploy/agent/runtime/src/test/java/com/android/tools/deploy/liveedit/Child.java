/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.deploy.liveedit;

public class Child extends Parent {

    public static String staticField = "Child";

    public Child(int value) {
        super(value);
        id = 0xBAF;
    }

    int callSuperMethod(int a) {
        return super.protectedInc(a);
    }

    int callInheritedProtectedMethod(int a) {
        return this.protectedInc(a);
    }

    Parent callProtectedConstructor() {
        return new Parent(1, 2);
    }

    int accessParentProtectedField(int v) {
        super.protectedField = v;
        return super.protectedField;
    }

    int accessParentProtectedFieldWithoutSuper(int v) {
        protectedField = v;
        return protectedField;
    }
}
