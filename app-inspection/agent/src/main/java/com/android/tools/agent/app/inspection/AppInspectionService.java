/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.agent.app.inspection;

import static com.android.tools.agent.app.inspection.InspectorContext.CrashListener;
import static com.android.tools.agent.app.inspection.NativeTransport.*;

import android.app.Application;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.inspection.ArtTooling;
import androidx.inspection.ArtTooling.EntryHook;
import androidx.inspection.ArtTooling.ExitHook;

import com.android.tools.agent.app.inspection.version.CompatibilityChecker;
import com.android.tools.agent.app.inspection.version.CompatibilityCheckerResult;
import com.android.tools.agent.app.inspection.version.LibraryCompatibility;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** This service controls all app inspectors */
// Suppress Convert2Lambda: Lambdas may incur penalty hit on older Android devices
// Suppress unused: Methods invoked via jni
// Suppress raw types: Service doesn't care about specific types, works with Objects
@SuppressWarnings({"Convert2Lambda", "unused", "rawtypes"})
public class AppInspectionService {

    private static AppInspectionService sInstance;
    // Lock to prevent race condition when registering hooks. See b/376717110.
    private static final Object lock = new Object();

    public static AppInspectionService instance() {
        if (sInstance == null) {
            sInstance = createAppInspectionService();
        }
        return sInstance;
    }

    // will be passed to jni method to call methods on the instance
    @SuppressWarnings("FieldCanBeLocal")
    // currently AppInspectionService is singleton, and it is never destroyed, so we don't clean
    // this reference.
    private final long mNativePtr;

    private final Map<String, InspectorBridge> mInspectorBridges = new ConcurrentHashMap<>();

    private final Map<String, List<HookInfo<ExitHook>>> mExitTransforms = new ConcurrentHashMap<>();
    private final Map<String, List<HookInfo<EntryHook>>> mEntryTransforms =
            new ConcurrentHashMap<>();

    private static final String INSPECTOR_ID_MISSING_ERROR =
            "Argument inspectorId must not be null";

    private final CrashListener mCrashListener = this::doDispose;

    private final ClassLoader mClassLoader;
    private final CompatibilityChecker mCompatibilityChecker;

    /**
     * Construct an instance referencing some native (JVMTI) resources.
     *
     * <p>A user shouldn't call this directly - instead, call {@link #instance()}, which delegates
     * work to JNI which ultimately calls this constructor.
     */
    AppInspectionService(long nativePtr) {
        mNativePtr = nativePtr;
        mClassLoader = findClassLoader();
        mCompatibilityChecker = new CompatibilityChecker(mClassLoader);
    }

    /**
     * Creates and launches an inspector on device.
     *
     * <p>This will respond with error when an inspector with the same ID already exists, when the
     * dex cannot be located, and when an exception is encountered while loading classes.
     *
     * @param inspectorId the unique id of the inspector being launched
     * @param dexPath the path to the .dex file of the inspector
     * @param projectName the name of the studio project that is trying to launch the inspector
     * @param libraryCompatibility represents the targeted library artifact.
     * @param force if true, create the inspector even if one is already running
     * @param commandId unique id of this command in the context of app inspection service
     */
    public void createInspector(
            String inspectorId,
            String dexPath,
            LibraryCompatibility libraryCompatibility,
            String projectName,
            boolean force,
            int commandId) {
        if (inspectorId == null) {
            sendCreateInspectorResponseError(commandId, INSPECTOR_ID_MISSING_ERROR);
            return;
        }
        if (mInspectorBridges.containsKey(inspectorId)) {
            if (!force) {
                String alreadyLaunchedProjectName = mInspectorBridges.get(inspectorId).getProject();
                sendCreateInspectorResponseError(
                        commandId,
                        "Inspector with the given id "
                                + inspectorId
                                + " already exists. It was launched by project: "
                                + alreadyLaunchedProjectName
                                + "\n\n"
                                + "This could happen if you launched the same inspector from two"
                                + " different projects at the same time, or if a previous run of"
                                + " the current project crashed unexpectedly and didn't shut down"
                                + " properly.");
                return;
            }

            doDispose(inspectorId);
        }

        if (!doCheckVersion(commandId, libraryCompatibility)) {
            return;
        }

        if (!new File(dexPath).exists()) {
            sendCreateInspectorResponseError(
                    commandId, "Failed to find a file with path: " + dexPath);
            return;
        }

        InspectorBridge bridge =
                InspectorBridge.create(inspectorId, projectName, mCrashListener, mClassLoader);
        mInspectorBridges.put(inspectorId, bridge);
        bridge.initializeInspector(
                dexPath,
                mNativePtr,
                (error) -> {
                    if (error != null) {
                        mInspectorBridges.remove(inspectorId);
                        sendCreateInspectorResponseError(commandId, error);
                    } else {
                        sendCreateInspectorResponseSuccess(commandId);
                        Log.i("studio.inspectors", "Inspector installed: " + inspectorId);
                    }
                });
    }

    public void disposeInspector(String inspectorId, int commandId) {
        if (inspectorId == null) {
            sendDisposeInspectorResponseError(commandId, INSPECTOR_ID_MISSING_ERROR);
            return;
        }
        if (!mInspectorBridges.containsKey(inspectorId)) {
            sendDisposeInspectorResponseError(
                    commandId, "Inspector with id " + inspectorId + " wasn't previously created");
            return;
        }
        doDispose(inspectorId);
        sendDisposeInspectorResponseSuccess(commandId);
    }

    public void sendCommand(String inspectorId, int commandId, byte[] rawCommand) {
        if (inspectorId == null) {
            sendRawResponseError(commandId, INSPECTOR_ID_MISSING_ERROR);
            return;
        }
        InspectorBridge bridge = mInspectorBridges.get(inspectorId);
        if (bridge == null) {
            sendRawResponseError(
                    commandId, "Inspector with id " + inspectorId + " wasn't previously created");
            return;
        }
        bridge.sendCommand(commandId, rawCommand);
    }

    public void cancelCommand(int cancelledCommandId) {
        // broadcast cancellation to every inspector even if only one of handled this command
        for (InspectorBridge bridge : mInspectorBridges.values()) {
            bridge.cancelCommand(cancelledCommandId);
        }
    }

    /**
     * This command allows the IDE to query for the versions of the libraries it wants to inspect
     * that are present in this app.
     *
     * @param commandId the unique commandId associated with this command
     * @param coordinates the libraries Studio wants version information for
     */
    public void getLibraryCompatibilityInfoCommand(
            int commandId, LibraryCompatibility[] coordinates) {
        List<CompatibilityCheckerResult> results = new ArrayList<>();
        for (LibraryCompatibility coordinate : coordinates) {
            CompatibilityCheckerResult result =
                    mCompatibilityChecker.checkCompatibility(coordinate);
            results.add(result);
        }
        NativeTransport.sendGetLibraryCompatibilityInfoResponse(
                commandId, results.toArray(), results.size());
    }

    private void doDispose(String inspectorId) {
        doDispose(inspectorId, null);
    }

    private void doDispose(String inspectorId, String errorMessage) {
        removeHooks(inspectorId, mEntryTransforms);
        removeHooks(inspectorId, mExitTransforms);
        InspectorBridge inspector = mInspectorBridges.remove(inspectorId);
        if (inspector != null) {
            sendDisposedEvent(inspectorId, errorMessage);
            inspector.disposeInspector();
        }
    }

    /**
     * Checks whether the inspector we are trying to create is compatible with the library.
     *
     * <p>This will compare the provided minVersion with the version string located in the version
     * file in the APK's META-INF directory.
     *
     * <p>In the case the provided version targeting information is null, return true because the
     * inspector is targeting the Android framework.
     *
     * <p>Note, this method will send the appropriate response to the command if the check failed in
     * any way. In other words, callers don't need to send a response if this method returns false.
     *
     * @param commandId the id of the command
     * @param libraryCoordinate represents the minimum supported library artifact. Null if the
     *     inspector is not targeting any particular library.
     * @return true if check passed. false if check failed for any reason.
     */
    private boolean doCheckVersion(int commandId, LibraryCompatibility libraryCoordinate) {
        if (libraryCoordinate == null) {
            return true;
        }
        CompatibilityCheckerResult versionResult =
                mCompatibilityChecker.checkCompatibility(libraryCoordinate);
        if (versionResult.status == CompatibilityCheckerResult.Status.INCOMPATIBLE) {
            sendCreateInspectorResponseVersionIncompatible(commandId, versionResult.message);
            return false;
        } else if (versionResult.status == CompatibilityCheckerResult.Status.NOT_FOUND) {
            sendCreateInspectorResponseVersionMissing(commandId, versionResult.message);
            return false;
        } else if (versionResult.status == CompatibilityCheckerResult.Status.LIBRARY_NOT_FOUND) {
            sendCreateInspectorResponseLibraryMissing(commandId, versionResult.message);
            return false;
        } else if (versionResult.status == CompatibilityCheckerResult.Status.ERROR) {
            sendCreateInspectorResponseError(commandId, versionResult.message);
            return false;
        } else if (versionResult.status == CompatibilityCheckerResult.Status.PROGUARDED) {
            sendCreateInspectorResponseAppProguarded(commandId, versionResult.message);
            return false;
        }
        return true;
    }

    private static String createLabel(Class origin, String method) {
        return origin.getName() + "->" + method;
    }

    public static void addEntryHook(
            String inspectorId, Class origin, String method, EntryHook hook) {
        List<HookInfo<EntryHook>> hooks =
                sInstance.mEntryTransforms.computeIfAbsent(
                        createLabel(origin, method),
                        new Function<String, List<HookInfo<EntryHook>>>() {

                            @Override
                            public List<HookInfo<EntryHook>> apply(String key) {
                                synchronized (lock) {
                                    nativeRegisterEntryHook(sInstance.mNativePtr, origin, method);
                                }
                                return new CopyOnWriteArrayList<>();
                            }
                        });
        hooks.add(new HookInfo<>(inspectorId, hook));
    }

    public static void addExitHook(
            String inspectorId, Class origin, String method, ArtTooling.ExitHook<?> hook) {
        List<HookInfo<ExitHook>> hooks =
                sInstance.mExitTransforms.computeIfAbsent(
                        createLabel(origin, method),
                        new Function<String, List<HookInfo<ExitHook>>>() {

                            @Override
                            public List<HookInfo<ExitHook>> apply(String key) {
                                synchronized (lock) {
                                    nativeRegisterExitHook(sInstance.mNativePtr, origin, method);
                                }
                                return new CopyOnWriteArrayList<>();
                            }
                        });
        hooks.add(new HookInfo<>(inspectorId, hook));
    }

    private static <T> T onExitInternal(String label, T returnObject) {
        AppInspectionService instance = AppInspectionService.instance();
        List<HookInfo<ExitHook>> hooks = instance.mExitTransforms.get(label);
        if (hooks == null) {
            return returnObject;
        }

        for (HookInfo<ExitHook> info : hooks) {
            //noinspection unchecked
            returnObject = (T) info.hook.onExit(returnObject);
        }
        return returnObject;
    }

    public static Object onExit(String methodSignature, Object returnObject) {
        return onExitInternal(methodSignature, returnObject);
    }

    public static void onExit(String methodSignature) {
        onExitInternal(methodSignature, null);
    }

    public static boolean onExit(String methodSignature, boolean result) {
        return onExitInternal(methodSignature, result);
    }

    public static byte onExit(String methodSignature, byte result) {
        return onExitInternal(methodSignature, result);
    }

    public static char onExit(String methodSignature, char result) {
        return onExitInternal(methodSignature, result);
    }

    public static short onExit(String methodSignature, short result) {
        return onExitInternal(methodSignature, result);
    }

    public static int onExit(String methodSignature, int result) {
        return onExitInternal(methodSignature, result);
    }

    public static float onExit(String methodSignature, float result) {
        return onExitInternal(methodSignature, result);
    }

    public static long onExit(String methodSignature, long result) {
        return onExitInternal(methodSignature, result);
    }

    public static double onExit(String methodSignature, double result) {
        return onExitInternal(methodSignature, result);
    }

    /**
     * Receives an array where:
     *
     * <ol>
     *   <li>the first parameter is the method signature,
     *   <li>the second parameter is the "this" reference,
     *   <li>all remaining arguments are the function's parameters.
     * </ol>
     *
     * <p>For example, the function {@code Client#sendMessage(Receiver r, String message)} will
     * receive the array:
     *
     * <pre>
     * ["(Lcom/example/Receiver;Ljava/lang/String;)Lcom/example/Client;", this, r, message]
     * </pre>
     */
    public static void onEntry(Object[] signatureThisParams) {
        // Should always at least contain signature and "this"
        assert (signatureThisParams.length >= 2);
        String signature = (String) signatureThisParams[0];
        List<HookInfo<EntryHook>> hooks =
                AppInspectionService.instance().mEntryTransforms.get(signature);

        if (hooks == null) {
            return;
        }

        Object thisObject = signatureThisParams[1];
        List<Object> params = Collections.emptyList();
        if (signatureThisParams.length > 2) {
            params =
                    Arrays.asList(
                            Arrays.copyOfRange(signatureThisParams, 2, signatureThisParams.length));
        }

        for (HookInfo<EntryHook> info : hooks) {
            info.hook.onEntry(thisObject, params);
        }
    }

    private static final class HookInfo<T> {
        private final String inspectorId;
        private final T hook;

        HookInfo(String inspectorId, T hook) {
            this.inspectorId = inspectorId;
            this.hook = hook;
        }
    }

    private static void removeHooks(
            String inspectorId, Map<String, ? extends List<? extends HookInfo<?>>> hooks) {
        for (List<? extends HookInfo<?>> list : hooks.values()) {
            for (HookInfo<?> info : list) {
                if (info.inspectorId.equals(inspectorId)) {
                    list.remove(info);
                }
            }
        }
    }

    private ClassLoader findClassLoader() {
        ArtToolingImpl artTooling = new ArtToolingImpl(mNativePtr, "inspector");
        List<Application> applications = artTooling.findInstances(Application.class);
        ClassLoader looperClassLoader = Looper.getMainLooper().getThread().getContextClassLoader();
        if (applications.isEmpty()) {
            return looperClassLoader;
        }
        Application application = applications.get(0);
        if (application == null) {
            return looperClassLoader;
        }
        try {
            ClassLoader classLoader = application.getClassLoader();
            return classLoader == null ? looperClassLoader : classLoader;
        } catch (Exception e) {
            Log.w(
                    "studio.inspectors",
                    "Failed to get application classloader. Using looper classloader",
                    e);
            return looperClassLoader;
        }
    }

    private static native AppInspectionService createAppInspectionService();

    @GuardedBy("lock")
    private static native void nativeRegisterEntryHook(
            long servicePtr, Class<?> originClass, String originMethod);

    @GuardedBy("lock")
    private static native void nativeRegisterExitHook(
            long servicePtr, Class<?> originClass, String originMethod);

}
