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

package com.android.tools.gradle.eclipse;

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.BIN_FOLDER;
import static com.android.SdkConstants.DOT_AIDL;
import static com.android.SdkConstants.DOT_FS;
import static com.android.SdkConstants.DOT_RS;
import static com.android.SdkConstants.DOT_RSH;
import static com.android.SdkConstants.FD_AIDL;
import static com.android.SdkConstants.FD_ASSETS;
import static com.android.SdkConstants.FD_JAVA;
import static com.android.SdkConstants.FD_MAIN;
import static com.android.SdkConstants.FD_RENDERSCRIPT;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FN_PROJECT_PROPERTIES;
import static com.android.SdkConstants.GEN_FOLDER;
import static com.android.SdkConstants.LIBS_FOLDER;
import static com.android.SdkConstants.SRC_FOLDER;
import static com.android.tools.gradle.eclipse.GradleImport.ECLIPSE_DOT_CLASSPATH;
import static com.android.tools.gradle.eclipse.GradleImport.ECLIPSE_DOT_PROJECT;
import static java.io.File.separator;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.utils.SdkUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

abstract class ImportModule implements Comparable<ImportModule> {
    // TODO: Tie libraries to the compile SDK?
    //private static final String LATEST = GradleImport.CURRENT_COMPILE_VERSION + ".0.0";
    private static final String LATEST = "+";

    private static final String APPCOMPAT_DEP = "com.android.support:appcompat-v7:" + LATEST;
    private static final String GRID_LAYOUT_DEP = "com.android.support:gridlayout-v7:" + LATEST;
    private static final String SUPPORT_LIB_DEP = "com.android.support:support-v4:" + LATEST;

    private static final String SHERLOCK_DEP = "com.actionbarsherlock:actionbarsherlock:4.4.0@aar";
    private static final String PLAY_SERVICES_DEP = "com.google.android.gms:play-services:+";

    protected final GradleImport mImporter;
    protected final List<GradleCoordinate> mDependencies = Lists.newArrayList();
    protected final List<File> mJarDependencies = Lists.newArrayList();
    protected List<GradleCoordinate> mReplaceWithDependencies;
    private String mModuleName;

    public ImportModule(@NonNull GradleImport importer) {
        mImporter = importer;
    }

    protected abstract boolean isLibrary();
    protected abstract boolean isApp();
    protected abstract boolean isAndroidLibrary();
    protected abstract boolean isAndroidProject();
    protected abstract boolean isJavaLibrary();
    protected abstract int getCompileSdkVersion();
    protected abstract int getMinSdkVersion();
    protected abstract int getTargetSdkVersion();
    @NonNull protected abstract String getOriginalName();
    @NonNull protected abstract List<File> getSourcePaths();
    @NonNull protected abstract File resolveFile(@NonNull File file);
    @NonNull protected abstract File getCanonicalModuleDir();
    @NonNull protected abstract List<File> getLocalProguardFiles();
    @NonNull protected abstract List<File> getSdkProguardFiles();
    @NonNull protected abstract String getLanguageLevel();
    @Nullable protected abstract String getPackage();
    @Nullable protected abstract File getOutputDir();
    @Nullable protected abstract File getManifestFile();
    @Nullable protected abstract File getResourceDir();
    @Nullable protected abstract File getAssetsDir();

    public void initialize() {
        initDependencies();
        initReplaceWithDependency();
    }

    protected void initDependencies() {
    }

    @Nullable
    public static GradleCoordinate guessDependency(@NonNull File jar) {
        // Make guesses based on library. For now, we do simple name checks, but
        // later consider looking at jar contents, md5 sums etc, especially to
        // pick up exact version numbers of popular libraries
        String name = jar.getName().toLowerCase(Locale.US);
        if (name.equals("android-support-v4.jar")) {
            return GradleCoordinate.parseCoordinateString(SUPPORT_LIB_DEP);
        } else if (name.equals("android-support-v7-gridlayout.jar")) {
            return GradleCoordinate.parseCoordinateString(GRID_LAYOUT_DEP);
        } else if (name.equals("android-support-v7-appcompat.jar")) {
            return GradleCoordinate.parseCoordinateString(APPCOMPAT_DEP);
        } else if (name.equals("com_actionbarsherlock.jar") ||
                name.equalsIgnoreCase("actionbarsherlock.jar")) {
            return GradleCoordinate.parseCoordinateString(SHERLOCK_DEP);
        } else if (name.equals("guava.jar") || name.startsWith("guava-")) {
            String version = getVersion("guava-", name, "15.0");
            if (version.startsWith("r")) { // really old versions
                version = "15.0";
            }
            return GradleCoordinate.parseCoordinateString("com.google.guava:guava:" + version);
        } else if (name.startsWith("joda-time")) {
            // Convert joda-time-2.1 jar into joda-time:joda-time:2.1 etc
            String version = getVersion("joda-time-", name, "2.3");
            return GradleCoordinate.parseCoordinateString("joda-time:joda-time:" + version);
        } else if (name.startsWith("robotium-solo-")) {
            String version = getVersion("robotium-solo-", name, "4.3.1");
            return GradleCoordinate.parseCoordinateString(
                    "com.jayway.android.robotium:robotium-solo:" + version);
        } else if (name.startsWith("protobuf-java-")) {
            String version = getVersion("protobuf-java-", name, "2.5");
            return GradleCoordinate.parseCoordinateString("com.google.protobuf:protobuf-java:"
                    + version);
        } else if (name.startsWith("gson-")) {
            String version = getVersion("gson-", name, "2.2.4");
            return GradleCoordinate.parseCoordinateString("com.google.code.gson:gson:" + version);
        } else if (name.startsWith("google-http-client-gson-")) {
            return GradleCoordinate.parseCoordinateString(
                    "com.google.http-client:google-http-client-gson:1.17.0-rc");
        } else if (name.startsWith("svg-android")) {
            return GradleCoordinate.parseCoordinateString(
                    "com.github.japgolly.android:svg-android:2.0.5");
        } else if (name.equals("gcm.jar")) {
            return GradleCoordinate.parseCoordinateString(PLAY_SERVICES_DEP);
        }

        // TODO: Consider other libraries if and when they get Gradle dependencies:
        // analytics, volley, ...

        return null;
    }

    private static String getVersion(String prefix, String jarName, String defaultVersion) {
        if (jarName.matches(prefix + "([\\d\\.]+)\\.jar")) {
            return jarName.substring(prefix.length(), jarName.length() - 4);
        }

        return defaultVersion;
    }

    /**
     * See if this is a library that looks like a known dependency; if so, just
     * use a dependency instead of the library
     */
    private void initReplaceWithDependency() {
        if (isLibrary() && mImporter.isReplaceLibs()) {
            String pkg = getPackage();
            if (pkg != null) {
                if (pkg.equals("com.actionbarsherlock")) {
                    mReplaceWithDependencies = Arrays.asList(
                            GradleCoordinate.parseCoordinateString(SHERLOCK_DEP),
                            GradleCoordinate.parseCoordinateString(SUPPORT_LIB_DEP));
                } else if (pkg.equals("android.support.v7.gridlayout")) {
                    mReplaceWithDependencies = Collections.singletonList(
                            GradleCoordinate.parseCoordinateString(GRID_LAYOUT_DEP));
                } else if (pkg.equals("com.google.android.gms")) {
                    mReplaceWithDependencies = Collections.singletonList(
                            GradleCoordinate.parseCoordinateString(
                                    PLAY_SERVICES_DEP));
                } else if (pkg.equals("android.support.v7.appcompat")) {
                    mReplaceWithDependencies = Collections.singletonList(
                            GradleCoordinate.parseCoordinateString(APPCOMPAT_DEP));
                } else if (pkg.equals("android.support.v7.mediarouter")) {
                    mReplaceWithDependencies = Collections.singletonList(
                            GradleCoordinate.parseCoordinateString(
                                    "com.android.support:support-v7-mediarouter:+"));
                }


                if (mReplaceWithDependencies != null) {
                    mImporter.getSummary().replacedLib(getOriginalName(),
                            mReplaceWithDependencies);
                }
            }
        }
    }

    public boolean isReplacedWithDependency() {
        return mReplaceWithDependencies != null && !mReplaceWithDependencies.isEmpty();
    }

    public List<GradleCoordinate> getReplaceWithDependencies() {
        return mReplaceWithDependencies;
    }


    public String getModuleName() {
        if (mModuleName == null) {
            String moduleName = SdkUtils.stripWhitespace(getOriginalName());
            if (mImporter.isGradleNameStyle()) {
                moduleName = Character.toLowerCase(moduleName.charAt(0)) + moduleName.substring(1);
            }
            mModuleName = moduleName;
        }
        return mModuleName;
    }

    public String getModuleReference() {
        return ':' + getModuleName();
    }

    public void copyInto(@NonNull File destDir) throws IOException {
        ImportSummary summary = mImporter.getSummary();

        Set<File> copied = Sets.newHashSet();

        final File main = new File(destDir, SRC_FOLDER + separator + FD_MAIN);
        mImporter.mkdirs(main);
        if (isAndroidProject()) {
            File srcManifest = getManifestFile();
            if (srcManifest != null && srcManifest.exists()) {
                File destManifest = new File(main, ANDROID_MANIFEST_XML);
                Files.copy(srcManifest, destManifest);
                summary.reportMoved(this, srcManifest, destManifest);
                recordCopiedFile(copied, srcManifest);
            }
            File srcRes = getResourceDir();
            if (srcRes != null && srcRes.exists()) {
                File destRes = new File(main, FD_RES);
                mImporter.mkdirs(destRes);
                mImporter.copyDir(srcRes, destRes, null);
                summary.reportMoved(this, srcRes, destRes);
                recordCopiedFile(copied, srcRes);
            }
            File srcAssets = getAssetsDir();
            if (srcAssets != null && srcAssets.exists()) {
                File destAssets = new File(main, FD_ASSETS);
                mImporter.mkdirs(destAssets);
                mImporter.copyDir(srcAssets, destAssets, null);
                summary.reportMoved(this, srcAssets, destAssets);
                recordCopiedFile(copied, srcAssets);
            }
        }

        for (final File src : getSourcePaths()) {
            final File srcJava = resolveFile(src);
            File destJava = new File(main, FD_JAVA);
            // Merge all the separate source folders into a single one; they aren't allowed
            // to contain source file conflicts anyway
            mImporter.mkdirs(destJava);
            mImporter.copyDir(srcJava, destJava, new GradleImport.CopyHandler() {
                // Handle moving .rs/.rsh/.fs files to main/rs/ and .aidl files to the
                // corresponding aidl package under main/aidl
                @Override
                public boolean handle(@NonNull File source, @NonNull File dest)
                        throws IOException {
                    String sourcePath = source.getPath();
                    if (sourcePath.endsWith(DOT_AIDL)) {
                        File aidlDir = new File(main, FD_AIDL);
                        File relative = GradleImport.computeRelativePath(srcJava, source);
                        if (relative == null) {
                            relative = GradleImport.computeRelativePath(
                                    srcJava.getCanonicalFile(), source);
                        }
                        if (relative != null) {
                            File destAidl = new File(aidlDir, relative.getPath());
                            mImporter.mkdirs(destAidl.getParentFile());
                            Files.copy(source, destAidl);
                            mImporter.getSummary().reportMoved(ImportModule.this, source,
                                    destAidl);
                            return true;
                        }
                    } else if (sourcePath.endsWith(DOT_RS) ||
                            sourcePath.endsWith(DOT_RSH) ||
                            sourcePath.endsWith(DOT_FS)) {
                        // Copy to flattened rs dir
                        // TODO: Ensure the file names are unique!
                        File destRs = new File(main, FD_RENDERSCRIPT + separator +
                                source.getName());
                        mImporter.mkdirs(destRs.getParentFile());
                        Files.copy(source, destRs);
                        mImporter.getSummary().reportMoved(ImportModule.this, source, destRs);
                        return true;
                    }
                    return false;
                }
            });
            summary.reportMoved(this, srcJava, destJava);
            recordCopiedFile(copied, srcJava);
        }

        if (isAndroidProject()) {
            for (File srcProguard : getLocalProguardFiles()) {
                File destProguard = new File(destDir, srcProguard.getName());
                if (!destProguard.exists()) {
                    Files.copy(srcProguard, destProguard);
                    summary.reportMoved(this, srcProguard, destProguard);
                    recordCopiedFile(copied, srcProguard);
                } else {
                    mImporter.reportWarning(this, destProguard,
                            "Local proguard config file name is not unique");
                }
            }
        }

        reportIgnored(copied);
    }

    private static void recordCopiedFile(@NonNull Set<File> copied, @NonNull File file)
            throws IOException {
        copied.add(file);
        copied.add(file.getCanonicalFile());
    }

    private void reportIgnored(Set<File> copied) throws IOException {
        File canonicalDir = getCanonicalModuleDir();

        // Ignore output folder (if not under bin/ as usual)
        File outputDir = getOutputDir();
        if (outputDir != null) {
            copied.add(resolveFile(outputDir).getCanonicalFile());
        }

        // These files are either not useful (bin, gen) or already handled (project metadata files)
        copied.add(new File(canonicalDir, BIN_FOLDER));
        copied.add(new File(canonicalDir, GEN_FOLDER));
        copied.add(new File(canonicalDir, ECLIPSE_DOT_CLASSPATH));
        copied.add(new File(canonicalDir, ECLIPSE_DOT_PROJECT));
        copied.add(new File(canonicalDir, FN_PROJECT_PROPERTIES));
        copied.add(new File(canonicalDir, FN_PROJECT_PROPERTIES));
        copied.add(new File(canonicalDir, LIBS_FOLDER));

        reportIgnored(canonicalDir, copied, 0);
    }

    private void reportIgnored(@NonNull File file, @NonNull Set<File> copied, int depth)
            throws IOException {
        if (depth > 0) {
            if (copied.contains(file)) {
                return;
            }
            File relative = GradleImport.computeRelativePath(getCanonicalModuleDir(), file);
            if (relative == null) {
                relative = file;
            }
            mImporter.getSummary().reportIgnored(getOriginalName(), relative);
        }

        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    reportIgnored(child, copied, depth + 1);
                }
            }
        }
    }

    public List<File> getJarDependencies() {
        return mJarDependencies;
    }

    public List<GradleCoordinate> getDependencies() {
        return mDependencies;
    }

    @Nullable
    public File computeProjectRelativePath(@NonNull File file) throws IOException {
        return GradleImport.computeRelativePath(getCanonicalModuleDir(), file);
    }

    protected abstract boolean dependsOn(@NonNull ImportModule other);

    // Sort by dependency order
    @Override
    public int compareTo(@NonNull ImportModule other) {
        if (dependsOn(other)) {
            return 1;
        } else if (other.dependsOn(this)) {
            return -1;
        } else {
            return getModuleName().compareTo(other.getModuleName());
        }
    }
}
