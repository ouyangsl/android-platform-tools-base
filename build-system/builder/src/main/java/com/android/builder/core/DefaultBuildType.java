/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.builder.core;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.internal.BaseConfigImpl;
import com.android.builder.model.BuildType;
import com.android.builder.model.SigningConfig;
import com.google.common.base.Objects;

public abstract class DefaultBuildType extends BaseConfigImpl implements BuildType {
    private static final long serialVersionUID = 1L;

    private final String mName;
    private boolean mDebuggable = false;
    private boolean mTestCoverageEnabled = false;
    private boolean mJniDebuggable = false;
    private boolean mRenderscriptDebuggable = false;
    private int mRenderscriptOptimLevel = 3;
    private String mApplicationIdSuffix = null;
    private String mVersionNameSuffix = null;
    private boolean mMinifyEnabled = false;
    private SigningConfig mSigningConfig = null;
    private boolean mEmbedMicroApp = true;

    private boolean mZipAlign = true;

    public DefaultBuildType(@NonNull String name) {
        mName = name;
    }

    public DefaultBuildType initWith(DefaultBuildType that) {
        _initWith(that);

        setDebuggable(that.isDebuggable());
        setTestCoverageEnabled(that.isTestCoverageEnabled());
        setJniDebuggable(that.isJniDebuggable());
        setRenderscriptDebuggable(that.isRenderscriptDebuggable());
        setRenderscriptOptimLevel(that.getRenderscriptOptimLevel());
        setApplicationIdSuffix(that.getApplicationIdSuffix());
        setVersionNameSuffix(that.getVersionNameSuffix());
        setMinifyEnabled(that.isMinifyEnabled() );
        setZipAlignEnabled(that.isZipAlignEnabled());
        setSigningConfig(that.getSigningConfig());
        setEmbedMicroApp(that.isEmbedMicroApp());

        return this;
    }

    @Override
    @NonNull
    public String getName() {
        return mName;
    }

    /** Whether this build type should generate a debuggable apk. */
    @NonNull
    public BuildType setDebuggable(boolean debuggable) {
        mDebuggable = debuggable;
        return this;
    }

    @Override
    public boolean isDebuggable() {
        return mDebuggable;
    }


    public void setTestCoverageEnabled(boolean testCoverageEnabled) {
        mTestCoverageEnabled = testCoverageEnabled;
    }

    @Override
    public boolean isTestCoverageEnabled() {
        return mTestCoverageEnabled;
    }

    /**
     * Whether this build type is configured to generate an APK with debuggable native code.
     */
    @NonNull
    public BuildType setJniDebugBuild(boolean jniDebugBuild) {
        warn("WARNING: jniDebugBuild is deprecated (and will soon stop working); change to \"jniDebuggable\" instead");
        mJniDebuggable = jniDebugBuild;
        return this;
    }

    /**
     * Whether this build type is configured to generate an APK with debuggable native code.
     */
    @NonNull
    public BuildType setJniDebuggable(boolean jniDebugBuild) {
        mJniDebuggable = jniDebugBuild;
        return this;
    }

    @Override
    public boolean isJniDebuggable() {
        return mJniDebuggable;
    }

    /**
     * Whether the build type is configured to generate an apk with debuggable RenderScript code.
     */
    public void setRenderscriptDebugBuild(boolean renderscriptDebugBuild) {
        warn("WARNING: renderscriptDebugBuild is deprecated (and will soon stop working); change to \"renderscriptDebuggable\" instead");
        mRenderscriptDebuggable = renderscriptDebugBuild;
    }

    @Override
    public boolean isRenderscriptDebuggable() {
        return mRenderscriptDebuggable;
    }

    /**
     * Whether the build type is configured to generate an apk with debuggable RenderScript code.
     */
    public void setRenderscriptDebuggable(boolean renderscriptDebugBuild) {
        mRenderscriptDebuggable = renderscriptDebugBuild;
    }

    @Override
    public int getRenderscriptOptimLevel() {
        return mRenderscriptOptimLevel;
    }

    /** Optimization level to use by the renderscript compiler. */
    public void setRenderscriptOptimLevel(int renderscriptOptimLevel) {
        mRenderscriptOptimLevel = renderscriptOptimLevel;
    }

    /**
     * Application id suffix applied to this build type.
     */
    @NonNull
    public BuildType setApplicationIdSuffix(@Nullable String applicationIdSuffix) {
        mApplicationIdSuffix = applicationIdSuffix;
        return this;
    }

    @Override
    @Nullable
    public String getApplicationIdSuffix() {
        return mApplicationIdSuffix;
    }

    /** Version name suffix. */
    @NonNull
    public BuildType setVersionNameSuffix(@Nullable String versionNameSuffix) {
        mVersionNameSuffix = versionNameSuffix;
        return this;
    }

    @Override
    @Nullable
    public String getVersionNameSuffix() {
        return mVersionNameSuffix;
    }

    /** Whether Minify is enabled for this build type. */
    @NonNull
    public BuildType setMinifyEnabled(boolean enabled) {
        mMinifyEnabled = enabled;
        return this;
    }

    @Override
    public boolean isMinifyEnabled() {
        return mMinifyEnabled;
    }

    /** Whether zipalign is enabled for this build type. */
    @NonNull
    public BuildType setZipAlign(boolean zipAlign) {
        warn("WARNING: zipAlign is deprecated (and will soon stop working); change to \"zipAlignEnabled\" instead");
        return setZipAlignEnabled(zipAlign);
    }

    /** Whether zipalign is enabled for this build type. */
    @NonNull
    public BuildType setZipAlignEnabled(boolean zipAlign) {
        mZipAlign = zipAlign;
        return this;
    }

    @Override
    public boolean isZipAlignEnabled() {
        return mZipAlign;
    }

    /** Sets the signing configuration. e.g.: {@code signingConfig signingConfigs.myConfig} */
    @NonNull
    public BuildType setSigningConfig(@Nullable SigningConfig signingConfig) {
        mSigningConfig = signingConfig;
        return this;
    }

    @Override
    @Nullable
    public SigningConfig getSigningConfig() {
        return mSigningConfig;
    }

    @Override
    public boolean isEmbedMicroApp() {
        return mEmbedMicroApp;
    }

    public void setEmbedMicroApp(boolean embedMicroApp) {
        mEmbedMicroApp = embedMicroApp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        DefaultBuildType buildType = (DefaultBuildType) o;

        if (!mName.equals(buildType.mName)) return false;
        if (mDebuggable != buildType.mDebuggable) return false;
        if (mTestCoverageEnabled != buildType.mTestCoverageEnabled) return false;
        if (mJniDebuggable != buildType.mJniDebuggable) return false;
        if (mRenderscriptDebuggable != buildType.mRenderscriptDebuggable) return false;
        if (mRenderscriptOptimLevel != buildType.mRenderscriptOptimLevel) return false;
        if (mMinifyEnabled != buildType.mMinifyEnabled) return false;
        if (mZipAlign != buildType.mZipAlign) return false;
        if (mApplicationIdSuffix != null ?
                !mApplicationIdSuffix.equals(buildType.mApplicationIdSuffix) :
                buildType.mApplicationIdSuffix != null)
            return false;
        if (mVersionNameSuffix != null ?
                !mVersionNameSuffix.equals(buildType.mVersionNameSuffix) :
                buildType.mVersionNameSuffix != null)
            return false;
        if (mSigningConfig != null ?
                !mSigningConfig.equals(buildType.mSigningConfig) :
                buildType.mSigningConfig != null)
            return false;
        if (mEmbedMicroApp != buildType.mEmbedMicroApp) return false;

        return true;
    }

    // warn the user
    protected abstract void warn(String message);

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (mName.hashCode());
        result = 31 * result + (mDebuggable ? 1 : 0);
        result = 31 * result + (mTestCoverageEnabled ? 1 : 0);
        result = 31 * result + (mJniDebuggable ? 1 : 0);
        result = 31 * result + (mRenderscriptDebuggable ? 1 : 0);
        result = 31 * result + mRenderscriptOptimLevel;
        result = 31 * result + (mApplicationIdSuffix != null ? mApplicationIdSuffix.hashCode() : 0);
        result = 31 * result + (mVersionNameSuffix != null ? mVersionNameSuffix.hashCode() : 0);
        result = 31 * result + (mMinifyEnabled ? 1 : 0);
        result = 31 * result + (mZipAlign ? 1 : 0);
        result = 31 * result + (mSigningConfig != null ? mSigningConfig.hashCode() : 0);
        result = 31 * result + (mEmbedMicroApp ? 1 : 0);
        return result;
    }

    @Override
    @NonNull
    public String toString() {
        return Objects.toStringHelper(this)
                .add("name", mName)
                .add("debuggable", mDebuggable)
                .add("testCoverageEnabled", mTestCoverageEnabled)
                .add("jniDebugBuild", mJniDebuggable)
                .add("renderscriptDebugBuild", mRenderscriptDebuggable)
                .add("renderscriptOptimLevel", mRenderscriptOptimLevel)
                .add("applicationIdSuffix", mApplicationIdSuffix)
                .add("versionNameSuffix", mVersionNameSuffix)
                .add("minify", mMinifyEnabled)
                .add("zipAlign", mZipAlign)
                .add("signingConfig", mSigningConfig)
                .add("embedMicroApp", mEmbedMicroApp)
                .add("mBuildConfigFields", getBuildConfigFields())
                .add("mResValues", getResValues())
                .add("mProguardFiles", getProguardFiles())
                .add("mConsumerProguardFiles", getConsumerProguardFiles())
                .add("mManifestPlaceholders", getManifestPlaceholders())
                .toString();
    }
}
