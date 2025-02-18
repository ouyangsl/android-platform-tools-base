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

package com.android.build.gradle.integration.common.utils;

import static org.junit.Assert.assertEquals;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.v2.ide.SourceProvider;
import com.google.common.truth.Truth;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public final class SourceProviderHelper {

    @NonNull
    private final String projectName;
    @NonNull private final String configName;
    @NonNull private final File projectDir;
    @Nullable private final SourceProvider sourceProvider;

    private String javaDir;
    private List<String> kotlinDirs;
    private String resourcesDir;
    private String manifestFile;
    private String resDir;
    private String assetsDir;
    private String aidlDir;
    private String renderscriptDir;
    private String baselineProfileDir;
    private String jniDir;

    public SourceProviderHelper(
            @NonNull String projectName,
            @NonNull File projectDir,
            @NonNull String configName,
            @Nullable SourceProvider sourceProvider) {
        this.projectName = projectName;
        this.projectDir = projectDir;
        this.configName = configName;
        this.sourceProvider = sourceProvider;
        // configure tester with default relative paths
        setJavaDir("src/" + configName + "/java");
        setKotlinDirs("src/" + configName + "/java", "src/" + configName + "/kotlin");
        setResourcesDir("src/" + configName + "/resources");
        setManifestFile("src/" + configName + "/AndroidManifest.xml");
        setResDir("src/" + configName + "/res");
        setAssetsDir("src/" + configName + "/assets");
        setAidlDir("src/" + configName + "/aidl");
        setRenderscriptDir("src/" + configName + "/rs");
        setBaselineProfileDir("src/" + configName + "/baselineProfiles");
        setJniDir("src/" + configName + "/jniLibs");
    }

    @NonNull
    public SourceProviderHelper setJavaDir(String javaDir) {
        this.javaDir = javaDir;
        return this;
    }

    @NonNull
    public SourceProviderHelper setKotlinDirs(String... kotlinDirs) {
        this.kotlinDirs = Arrays.asList(kotlinDirs);
        return this;
    }

    @NonNull
    public SourceProviderHelper setResourcesDir(String resourcesDir) {
        this.resourcesDir = resourcesDir;
        return this;
    }

    @NonNull
    public SourceProviderHelper setManifestFile(String manifestFile) {
        this.manifestFile = manifestFile;
        return this;
    }

    @NonNull
    public SourceProviderHelper setResDir(String resDir) {
        this.resDir = resDir;
        return this;
    }

    @NonNull
    public SourceProviderHelper setAssetsDir(String assetsDir) {
        this.assetsDir = assetsDir;
        return this;
    }

    @NonNull
    public SourceProviderHelper setAidlDir(String aidlDir) {
        this.aidlDir = aidlDir;
        return this;
    }

    @NonNull
    public SourceProviderHelper setRenderscriptDir(String renderscriptDir) {
        this.renderscriptDir = renderscriptDir;
        return this;
    }

    @NonNull
    public SourceProviderHelper setBaselineProfileDir(String baselineProfileDir) {
        this.baselineProfileDir = baselineProfileDir;
        return this;
    }

    @NonNull
    public SourceProviderHelper setJniDir(String jniDir) {
        this.jniDir = jniDir;
        return this;
    }

    public void testV2() {
        testSinglePathCollection("java", javaDir, sourceProvider.getJavaDirectories());
        testSinglePathCollection(
                "resources", resourcesDir, sourceProvider.getResourcesDirectories());
        testSinglePathCollection("res", resDir, sourceProvider.getResDirectories());
        testSinglePathCollection("assets", assetsDir, sourceProvider.getAssetsDirectories());
        testSinglePathCollection("jniLibs", jniDir, sourceProvider.getJniLibsDirectories());
        testSinglePathCollection(
                "baselineProfiles",
                baselineProfileDir,
                sourceProvider.getBaselineProfileDirectories());
        testSinglePathCollection("aidl", aidlDir, sourceProvider.getAidlDirectories());
        testSinglePathCollection(
                "rs", renderscriptDir, sourceProvider.getRenderscriptDirectories());

        Truth.assertWithMessage("AndroidManifest")
                .that((Comparable<Path>) new File(projectDir, manifestFile).toPath())
                .isEquivalentAccordingToCompareTo(sourceProvider.getManifestFile().toPath());

        Truth.assertThat(sourceProvider.getKotlinDirectories())
                .containsExactlyElementsIn(
                        kotlinDirs.stream()
                                .map(f -> new File(projectDir, f))
                                .collect(Collectors.toList()));
    }

    private void testSinglePathCollection(
            @NonNull String setName,
            @NonNull String referencePath,
            @Nullable Collection<File> pathSet) {
        if (pathSet == null) return;
        assertEquals(1, pathSet.size());
        Truth.assertWithMessage(projectName + ": " + configName + "/" + setName)
                .that((Comparable<Path>) new File(projectDir, referencePath).toPath())
                .isEquivalentAccordingToCompareTo(pathSet.iterator().next().toPath());
    }

}
