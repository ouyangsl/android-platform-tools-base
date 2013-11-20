/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.ide.common.rendering;

import static com.android.SdkConstants.DOT_CLASS;
import static com.android.SdkConstants.DOT_JAR;

import com.android.annotations.Nullable;

import java.io.File;
import java.io.FileDescriptor;
import java.net.InetAddress;
import java.security.Permission;

/**
 * A {@link java.lang.SecurityManager} which is used for layout lib rendering, to
 * prevent custom views from accidentally exiting the whole IDE if they call
 * {@code System.exit}, as well as unintentionally writing files etc.
 * <p>
 * The security manager only checks calls on the current thread for which it
 * was made active with a call to {@link #setActive(boolean)}, as well as any
 * threads constructed from the render thread.
 */
public class RenderSecurityManager extends SecurityManager {
    /**
     * Thread local data which indicates whether the current thread is relevant for
     * this security manager. This is an inheritable thread local such that any threads
     * spawned from this thread will also apply the security manager; otherwise code
     * could just create new threads and execute code separate from the security manager
     * there.
     */
    private static ThreadLocal<Boolean> sIsRenderThread = new InheritableThreadLocal<Boolean>() {
        @Override protected synchronized Boolean initialValue() {
            return Boolean.FALSE;
        }
        @Override protected synchronized Boolean childValue(Boolean parentValue) {
            return parentValue;
        }
    };

    private boolean mAllowSetSecurityManager;
    private boolean mDisabled;
    private String mSdkPath;
    private String mProjectPath;
    private SecurityManager myPreviousSecurityManager;

    /**
     * Returns the current render security manager, if any. This will only return
     * non-null if there is an active {@linkplain RenderSecurityManager} as the
     * current global security manager.
     */
    @Nullable
    public static RenderSecurityManager getCurrent() {
        SecurityManager securityManager = System.getSecurityManager();
        if (securityManager instanceof RenderSecurityManager) {
            return (RenderSecurityManager) securityManager;
        }

        return null;
    }

    /**
     * Creates a security manager suitable for controlling access to custom views
     * being rendered by layoutlib, ensuring that they don't accidentally try to
     * write files etc (which could corrupt data if they for example assume device
     * paths that are not the same for the running IDE; for example, they could try
     * to clear out their own local app storage, which in the IDE could be the
     * user's home directory.)
     *
     * @param sdkPath an optional path to the SDK install being used by layoutlib;
     *                this is used to white-list path prefixes for layoutlib resource
     *                lookup
     * @param projectPath a path to the project directory, used for similar purposes
     */
    public RenderSecurityManager(
            @Nullable String sdkPath,
            @Nullable String projectPath) {
        mSdkPath = sdkPath;
        mProjectPath = projectPath;
    }

    public void setActive(boolean active) {
        SecurityManager current = System.getSecurityManager();
        boolean isActive = current == this;
        if (active == isActive) {
            return;
        }

        if (active) {
            // Enable
            assert !(current instanceof RenderSecurityManager);
            myPreviousSecurityManager = current;
            sIsRenderThread.set(true);
            mDisabled = false;
            System.setSecurityManager(this);
        } else {
            // Disable
            mAllowSetSecurityManager = true;
            sIsRenderThread.set(false);
            mDisabled = true;
            try {
                // Only reset the security manager if it hasn't already been set to
                // something else. If other threads try to do the same thing we could have
                // a problem; if they sampled the render security manager while it was globally
                // active, replaced it with their own, and sometime in the future try to
                // set it back, it will be active when we didn't intend for it to be. That's
                // why there is also the {@code mDisabled} flag, used to ignore any requests
                // later on.
                if (current instanceof RenderSecurityManager) {
                    System.setSecurityManager(myPreviousSecurityManager);
                }
            } finally {
                mAllowSetSecurityManager = false;
            }
        }
    }

    private boolean isRelevant() {
        return !mDisabled && sIsRenderThread.get();
    }

    public void dispose() {
        setActive(false);
    }

    // Permitted by custom views: access any package or member, read properties

    @Override
    public void checkPackageAccess(String pkg) {
    }

    @Override
    public void checkMemberAccess(Class<?> clazz, int which) {
    }

    @Override
    public void checkPropertyAccess(String property) {
    }

    @Override
    public void checkLink(String lib) {
        // Needed to for example load the "fontmanager" library from layout lib (from the
        // BiDiRenderer's layoutGlyphVector call
    }

    @Override
    public void checkCreateClassLoader() {
        // TODO: Layoutlib makes heavy use of this, so we can't block it yet.
        // To fix this we should make a local class loader, passed to layoutlib, which
        // knows how to reset the security manager
    }

    //------------------------------------------------------------------------------------------
    // Reading is permitted for certain files only
    //------------------------------------------------------------------------------------------

    @Override
    public void checkRead(String file) {
        if (isRelevant() && !isReadingAllowed(file)) {
            throw new RenderSecurityException("Read", file);
        }
    }

    @Override
    public void checkRead(String file, Object context) {
        if (isRelevant() && !isReadingAllowed(file)) {
            throw new RenderSecurityException("Read", file);
        }
    }

    private boolean isReadingAllowed(String path) {
        // Allow reading files in the SDK install (fonts etc)
        if (mSdkPath != null && path.startsWith(mSdkPath)) {
            return true;
        }

        // Allowing reading resources in the project, such as icons
        if (mProjectPath != null && path.startsWith(mProjectPath)) {
            return true;
        }

        if (path.startsWith("#") && path.indexOf(File.separatorChar) == -1) {
            // It's really layoutlib's ResourceHelper.getColorStateList which calls isFile()
            // on values to see if it's a file or a color.
            return true;
        }

        // Needed by layoutlib's class loader. Note that we've locked down the ability to create
        // new class loaders.
        if (path.endsWith(DOT_CLASS) || path.endsWith(DOT_JAR)) {
            return true;
        }

        String javaHome = System.getProperty("java.home");
        if (path.startsWith(javaHome)) { // Allow JDK to load its own classes
            return true;
        } else if (javaHome.endsWith("/Contents/Home")) {
            // On Mac, Home lives two directory levels down from the real home, and we sometimes
            // need to read from sibling directories (e.g. ../Libraries/ etc)
            if (path.regionMatches(0, javaHome, 0, javaHome.length() - "Contents/Home".length())) {
                return true;
            }
        }

        return false;
    }

    //------------------------------------------------------------------------------------------
    // Not permitted:
    //------------------------------------------------------------------------------------------

    @Override
    public void checkExit(int status) {
        // Probably not intentional in a custom view; would take down the whole IDE!
        if (isRelevant()) {
            throw new RenderSecurityException("Exit", String.valueOf(status));
        }

        super.checkExit(status);
    }

    @Override
    public void checkPropertiesAccess() {
        if (isRelevant()) {
            throw new RenderSecurityException("Property", null);
        }
    }

    // Prevent code execution/linking/loading

    @Override
    public void checkPackageDefinition(String pkg) {
        if (isRelevant()) {
            throw new RenderSecurityException("Package", pkg);
        }
    }

    @Override
    public void checkExec(String cmd) {
        if (isRelevant()) {
            throw new RenderSecurityException("Exec", cmd);
        }
    }

    // Prevent network access

    @Override
    public void checkConnect(String host, int port) {
        if (isRelevant()) {
            throw new RenderSecurityException("Socket", host + ":" + port);
        }
    }

    @Override
    public void checkConnect(String host, int port, Object context) {
        if (isRelevant()) {
            throw new RenderSecurityException("Socket", host + ":" + port);
        }
    }

    @Override
    public void checkListen(int port) {
        if (isRelevant()) {
            throw new RenderSecurityException("Socket", "port " + port);
        }
    }

    @Override
    public void checkAccept(String host, int port) {
        if (isRelevant()) {
            throw new RenderSecurityException("Socket", host + ":" + port);
        }
    }

    @Override
    public void checkSetFactory() {
        if (isRelevant()) {
            throw new RenderSecurityException("Socket", null);
        }
    }

    @Override
    public void checkMulticast(InetAddress inetAddress) {
        if (isRelevant()) {
            throw new RenderSecurityException("Socket", inetAddress.getCanonicalHostName());
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void checkMulticast(InetAddress inetAddress, byte ttl) {
        if (isRelevant()) {
            throw new RenderSecurityException("Socket", inetAddress.getCanonicalHostName());
        }
    }

    // Prevent file access

    @Override
    public void checkDelete(String file) {
        if (isRelevant()) {
            throw new RenderSecurityException("Delete", file);
        }
    }

    @Override
    public void checkAwtEventQueueAccess() {
        if (isRelevant()) {
            throw new RenderSecurityException("Event", null);
        }
    }

    // Prevent writes

    @Override
    public void checkWrite(FileDescriptor fileDescriptor) {
        if (isRelevant()) {
            throw new RenderSecurityException("Write", fileDescriptor.toString());
        }
    }

    @Override
    public void checkWrite(String file) {
        if (isRelevant()) {
            throw new RenderSecurityException("Write", file);
        }
    }

    // Misc

    @Override
    public void checkPrintJobAccess() {
        if (isRelevant()) {
            throw new RenderSecurityException("Print", null);
        }
    }

    @Override
    public void checkSystemClipboardAccess() {
        if (isRelevant()) {
            throw new RenderSecurityException("Clipboard", null);
        }
    }

    @Override
    public boolean checkTopLevelWindow(Object context) {
        if (isRelevant()) {
            throw new RenderSecurityException("Window", null);
        }
        return false;
    }

    @Override
    public void checkAccess(Thread thread) {
        // Turns out layoutlib sometimes creates asynchronous calls, for example
        //       java.lang.Thread.<init>(Thread.java:521)
        //       at android.os.AsyncTask$1.newThread(AsyncTask.java:189)
        //       at java.util.concurrent.ThreadPoolExecutor.addThread(ThreadPoolExecutor.java:670)
        //       at java.util.concurrent.ThreadPoolExecutor.addIfUnderCorePoolSize(ThreadPoolExecutor.java:706)
        //       at java.util.concurrent.ThreadPoolExecutor.execute(ThreadPoolExecutor.java:650)
        //       at android.os.AsyncTask$SerialExecutor.scheduleNext(AsyncTask.java:244)
        //       at android.os.AsyncTask$SerialExecutor.execute(AsyncTask.java:238)
        //       at android.os.AsyncTask.execute(AsyncTask.java:604)
        //       at android.widget.TextView.updateTextServicesLocaleAsync(TextView.java:8078)
    }

    @Override
    public void checkAccess(ThreadGroup threadGroup) {
        // See checkAccess(Thread)
    }

    @Override
    public void checkPermission(Permission permission) {
        String name = permission.getName();
        String actions = permission.getActions();
        if ("read".equals(actions)) {
            if (!isReadingAllowed(name)) {
                throw new RenderSecurityException("Read", name);
            }
        } else if ("setSecurityManager".equals(name)) {
            if (!mAllowSetSecurityManager) {
                throw new RenderSecurityException("Security", null);
            }
        }
    }
}
