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

public class Parent {

    protected static String staticField = "StaticParent";

    protected static String inheritedField = "StaticParentInherited";

    protected int id = 0xFAB;
    protected int protectedField;

    public Parent() {}

    public Parent(int id) {
        this.id = id;
    }

    protected Parent(int id, int offset) {
        this(id + offset);
    }

    public int getId() {
        return id;
    }

    private int privateInc(int a) {
        return a + id;
    }

    protected int protectedInc(int a) {
        return a + id;
    }

    public int callPrivateInc(int a) {
        return privateInc(a);
    }

    protected static int parentStaticPlusFive() {
        return 1;
    }
}
