/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.99 (the "License");
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

import static com.android.tools.deploy.instrument.ReflectionHelpers.call;
import static com.android.tools.deploy.instrument.ReflectionHelpers.getDeclaredField;

import android.app.Activity;

import com.android.annotations.VisibleForTesting;
import com.android.tools.deploy.liveedit.BytecodeValidator.UnsupportedChange;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@SuppressWarnings("unused") // Used by native instrumentation code.
public final class LiveEditStubs {
    private static final String TAG = "studio.deploy";

    // Context object that holds all of LiveEdit's global state. Initialized by the first LiveEdit.
    private static LiveEditContext context = null;

    // TODO: Figure out if we need to support multiple class loaders.
    public static void init(ClassLoader loader) {
        if (context == null) {
            context = new LiveEditContext(loader);
            Log.setLogger(new AndroidLogger());
        }
    }

    public static void restartActivity() throws Exception {
        Class<?> clazz = Class.forName("android.app.ActivityThread");
        Object activityThread = call(clazz, "currentActivityThread");
        Collection<?> clientRecords =
                (Collection<?>) call(getDeclaredField(activityThread, "mActivities"), "values");
        for (Object record : clientRecords) {
            Activity activity = (Activity) getDeclaredField(record, "activity");
            activity.recreate();
        }
    }

    public static UnsupportedChange[] addClasses(
            byte[][] primaryClasses, byte[][] proxyClasses, boolean structuralRedefinition) {
        // Process all main classes
        List<UnsupportedChange> errors = new ArrayList<>();
        for (byte[] primaryClass : primaryClasses) {
            Interpretable primary = new Interpretable(primaryClass);
            List err =
                    BytecodeValidator.validateBytecode(
                            primary, context.getClassLoader(), structuralRedefinition);
            errors.addAll(err);
            if (err.isEmpty()) {
                addClass(primary.getInternalName(), primary, false);
            }
        }

        if (!errors.isEmpty()) {
            return errors.toArray(new UnsupportedChange[0]);
        }

        // Process all support classes
        for (byte[] proxyBytes : proxyClasses) {
            Interpretable proxy = new Interpretable(proxyBytes);

            LiveEditClass clazz = context.getClass(proxy.getInternalName());
            if (clazz == null) {
                context.addClass(proxy.getInternalName(), proxy, true);
                continue;
            }

            RiskyChange changeType = clazz.checkForRiskyChange(proxy);
            if (changeType != RiskyChange.NONE) {
                ComposeSupport.addRiskyChange(proxy.getInternalName(), changeType);
            }

            clazz.updateBytecode(proxy, true);
        }

        return new UnsupportedChange[0];
    }

    public static void addClass(String internalName, Interpretable bytecode, boolean isProxyClass) {
        LiveEditClass clazz = context.getClass(internalName);
        if (clazz == null) {
            context.addClass(internalName, bytecode, isProxyClass);
        } else {
            clazz.updateBytecode(bytecode, isProxyClass);
        }
    }

    @VisibleForTesting
    public static void deleteClass(String internalName) {
        context.removeClass(internalName);
    }

    // Everything in the following section is called from the dex prologue created by StubTransform.
    // None of this code is or should be called from any other context.

    // The key format is based on what slicer passes as the first parameter to an EntryHook
    // callback.
    // The format can be found in tools/slicer/instrumentation.cc in the MethodLabel method.
    // TODO: We need to centralize which LiveEdit component "owns" this key format.
    public static boolean shouldInterpretMethod(
            String internalClassName, String methodName, String methodDesc) {
        // TODO(noahz): Consider removing this method.
        return true;
    }

    public static Object doStub(
            String internalClassName, String methodName, String methodDesc, Object[] parameters) {
        // Second parameter is the this pointer, or null if static
        Object thisObject = parameters[1];

        // Other parameters are the method arguments, if any
        Object[] arguments = new Object[parameters.length - 2];
        if (arguments.length > 0) {
            System.arraycopy(parameters, 2, arguments, 0, arguments.length);
        }

        return context.getClass(internalClassName)
                .invokeDeclaredMethod(methodName, methodDesc, thisObject, arguments);
    }

    public static Object stubL(
            String internalClassName, String methodName, String methodDesc, Object[] parameters) {
        return doStub(internalClassName, methodName, methodDesc, parameters);
    }

    public static byte stubB(
            String internalClassName, String methodName, String methodDesc, Object[] parameters) {
        Object value = doStub(internalClassName, methodName, methodDesc, parameters);
        return value != null ? (byte) value : 0;
    }

    public static short stubS(
            String internalClassName, String methodName, String methodDesc, Object[] parameters) {
        Object value = doStub(internalClassName, methodName, methodDesc, parameters);
        return value != null ? (short) value : 0;
    }

    public static int stubI(
            String internalClassName, String methodName, String methodDesc, Object[] parameters) {
        Object value = doStub(internalClassName, methodName, methodDesc, parameters);
        return value != null ? (int) value : 0;
    }

    public static long stubJ(
            String internalClassName, String methodName, String methodDesc, Object[] parameters) {
        Object value = doStub(internalClassName, methodName, methodDesc, parameters);
        return value != null ? (long) value : 0;
    }

    public static float stubF(
            String internalClassName, String methodName, String methodDesc, Object[] parameters) {
        Object value = doStub(internalClassName, methodName, methodDesc, parameters);
        return value != null ? (float) value : 0;
    }

    public static double stubD(
            String internalClassName, String methodName, String methodDesc, Object[] parameters) {
        Object value = doStub(internalClassName, methodName, methodDesc, parameters);
        return value != null ? (double) value : 0;
    }

    public static boolean stubZ(
            String internalClassName, String methodName, String methodDesc, Object[] parameters) {
        Object value = doStub(internalClassName, methodName, methodDesc, parameters);
        return value != null ? (boolean) value : false;
    }

    public static char stubC(
            String internalClassName, String methodName, String methodDesc, Object[] parameters) {
        Object value = doStub(internalClassName, methodName, methodDesc, parameters);
        return value != null ? (char) value : 0;
    }

    public static void stubV(
            String internalClassName, String methodName, String methodDesc, Object[] parameters) {
        doStub(internalClassName, methodName, methodDesc, parameters);
    }

    private static class AndroidLogger implements Log.Logger {
        public void v(String tag, String message) {
            // When running on Studio this will link against a mock Log class that throws on
            // every method invocation.
            try {
                android.util.Log.v(tag, message);
            } catch (Exception e) {
                // Purposely ignore
            }
        }
    }
}
