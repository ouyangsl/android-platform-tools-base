/*
 * Copyright (C) 2023 The Android Open Source Project
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

public class ProxyMissingMethodException extends RuntimeException {
    private final String className;
    private final String methodName;
    private final String methodDesc;

    public ProxyMissingMethodException(LiveEditClass clazz, String methodName, String methodDesc) {
        super(
                "No such method '"
                        + methodName
                        + "' found in class '"
                        + clazz.getClassInternalName()
                        + "'");
        this.className = clazz.getClassInternalName();
        this.methodName = methodName;
        this.methodDesc = methodDesc;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getMethodDesc() {
        return methodDesc;
    }
}
