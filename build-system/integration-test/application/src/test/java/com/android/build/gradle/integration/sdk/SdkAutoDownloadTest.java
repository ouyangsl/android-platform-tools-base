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

package com.android.build.gradle.integration.sdk;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;
import static org.junit.Assert.assertNotNull;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelBuilderV2;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.IntegerOption;
import com.android.builder.core.ToolsRevisionUtils;
import com.android.builder.model.v2.ide.SyncIssue;
import com.android.repository.Revision;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.testutils.AssumeUtil;
import com.android.testutils.TestUtils;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;
import com.android.utils.PathUtils;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Tests for automatic SDK download from Gradle. */
public class SdkAutoDownloadTest {

    private static String cmakeLists = "cmake_minimum_required(VERSION 3.4.1)"
            + System.lineSeparator()
            + "file(GLOB SRC src/main/cpp/hello-jni.cpp)"
            + System.lineSeparator()
            + "set(CMAKE_VERBOSE_MAKEFILE ON)"
            + System.lineSeparator()
            + "add_library(hello-jni SHARED ${SRC})"
            + System.lineSeparator()
            + "target_link_libraries(hello-jni log)";

    // These all need to be kept in sync with the sibling BUILD file
    private static final String BUILD_TOOLS_VERSION = SdkConstants.CURRENT_BUILD_TOOLS_VERSION;
    private static final String PLATFORM_VERSION = "31";
    private static final String NDK_VERSION = "23.0.7344513";
    private static final String CMAKE_VERSION = "3.22.1";

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(
                            HelloWorldJniApp.builder()
                                    .withNativeDir("cpp")
                                    .useCppSource(true)
                                    .build())
                    .addGradleProperties(IntegerOption.ANDROID_SDK_CHANNEL.getPropertyName() + "=3")
                    .withSdk(false)
                    .create();

    private File mSdkHome;
    private File licenseFile;
    private File previewLicenseFile;

    @Before
    public void setUp() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator() + "apply plugin: 'com.android.application'");

        mSdkHome = project.file("local-sdk-for-test");
        FileUtils.mkdirs(mSdkHome);

        File licensesFolder = new File(mSdkHome, "licenses");
        FileUtils.mkdirs(licensesFolder);
        licenseFile = new File(licensesFolder, "android-sdk-license");
        previewLicenseFile = new File(licensesFolder, "android-sdk-preview-license");

        // noinspection SpellCheckingInspection SHAs.
        String licensesHash =
                String.format(
                        "8933bad161af4178b1185d1a37fbf41ea5269c55%n"
                                + "d56f5187479451eabf01fb78af6dfcb131a6481e%n"
                                + "24333f8a63b6825ea9c5514f83c2829b004d1fee%n");

        String previewLicenseHash = String.format("84831b9409646a918e30573bab4c9c91346d8abd%n");

        Files.write(licenseFile.toPath(), licensesHash.getBytes(StandardCharsets.UTF_8));
        Files.write(
                previewLicenseFile.toPath(), previewLicenseHash.getBytes(StandardCharsets.UTF_8));
        TestFileUtils.appendToFile(
                project.getLocalProp(),
                System.lineSeparator()
                        + SdkConstants.SDK_DIR_PROPERTY
                        + " = "
                        + mSdkHome.getAbsolutePath().replace("\\", "\\\\"));

        // Work round that build tools depends on 'tools' by creating an empty 'tools' install.
        Path toolsDirectory = mSdkHome.toPath().resolve("tools");
        Files.createDirectory(toolsDirectory);
        Path toolsSourceProperties = toolsDirectory.resolve("source.properties");
        Files.write(
                toolsSourceProperties,
                ("Pkg.UserSrc=false\n"
                                + "Pkg.Revision=26.1.1\n"
                                + "Platform.MinPlatformToolsRev=20\n"
                                + "Pkg.Dependencies=emulator\n"
                                + "Pkg.Path=tools\n"
                                + "Pkg.Desc=Android SDK Tools\n")
                        .getBytes(StandardCharsets.UTF_8));

        Path remoteSdk = TestUtils.getRemoteSdk();
        Path addOnsListFile = remoteSdk.resolve("addons_list-5.xml");
        Files.write(
                addOnsListFile,
                ("<?xml version=\"1.0\" ?>\n"
                                + "<common:site-list xmlns:common=\"http://schemas.android.com/repository/android/sites-common/1\" xmlns:sdk=\"http://schemas.android.com/sdk/android/addons-list/5\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                                + "\t<site xsi:type=\"sdk:addonSiteType\">\n"
                                + "\t\t<displayName>Google Inc.</displayName>\n"
                                + "\t\t<url>addon2-3.xml</url>\n"
                                + "\t</site>\n"
                                + "</common:site-list>")
                        .getBytes(StandardCharsets.UTF_8));

        TestFileUtils.appendToFile(
                project.getBuildFile(), "android.defaultConfig.minSdkVersion = 30");
        TestFileUtils.appendToFile(
                project.getBuildFile(), "android.namespace \"com.example.hellojni\"");
    }

    /** Tests that the compile SDK target and build tools are automatically downloaded. */
    @Test
    public void checkCompileSdkAndBuildToolsDownloading() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\""
                        + System.lineSeparator()
                        // Tests that calling getBootClasspath() doesn't break auto-download.
                        + "println(android.bootClasspath)");

        File platformTarget = getPlatformFolder();
        File buildTools =
                FileUtils.join(mSdkHome, SdkConstants.FD_BUILD_TOOLS, BUILD_TOOLS_VERSION);

        // Not installed.
        assertThat(buildTools).doesNotExist();
        assertThat(platformTarget).doesNotExist();

        // ---------- Build ----------
        getExecutor().run("assembleDebug");

        // Installed platform
        assertThat(platformTarget).isDirectory();
        File androidJarFile = FileUtils.join(getPlatformFolder(), "android.jar");
        assertThat(androidJarFile).exists();

        // Installed build tools.
        assertThat(buildTools).isDirectory();
        File aidl =
                FileUtils.join(
                        mSdkHome,
                        SdkConstants.FD_BUILD_TOOLS,
                        BUILD_TOOLS_VERSION,
                        SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS
                                ? "aidl.exe"
                                : "aidl");
        assertThat(aidl).exists();
    }

    /**
     * Tests that the compile SDK target was automatically downloaded in the case that the target
     * was an addon target. It also checks that the platform that the addon is dependent on was
     * downloaded.
     */
    @Test
    public void checkCompileSdkAddonDownloading() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion \"Google Inc.:Google APIs:24\""
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\"");

        getExecutor().run("assembleDebug");

        File platformBase = FileUtils.join(mSdkHome, SdkConstants.FD_PLATFORMS, "android-24");
        assertThat(platformBase).isDirectory();

        File addonTarget =
                FileUtils.join(mSdkHome, SdkConstants.FD_ADDONS, "addon-google_apis-google-24");
        assertThat(addonTarget).isDirectory();
    }

    /** Tests that we don't crash when a codename is used for the compile SDK level. */
    @Test
    public void checkCompileSdkCodename() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion 'MadeUp'"
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\"");

        GradleBuildResult result = getExecutor().expectFailure().run("assembleDebug");
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("Unsupported value: MadeUp. Format must be one of:");
    }

    /**
     * Tests that missing platform tools don't break the build, and that the platform tools were
     * automatically downloaded, when they weren't already installed.
     */
    @Test
    public void checkPlatformToolsDownloading() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\"");

        File platformTools = FileUtils.join(mSdkHome, SdkConstants.FD_PLATFORM_TOOLS);

        // Run one build to setup the SDK with it auto-downloaded
        getExecutor().run("assembleDebug");
        assertThat(platformTools).isDirectory();

        PathUtils.deleteRecursivelyIfExists(platformTools.toPath());

        getOfflineExecutor().run("assembleDebug");
        assertThat(platformTools).doesNotExist();
    }

    @Test
    public void checkCmakeDownloading() throws Exception {
        AssumeUtil.assumeIsLinux();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.ndkVersion '"
                        + NDK_VERSION
                        + "'"
                        + System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\""
                        + System.lineSeparator()
                        + "android.externalNativeBuild.cmake.path \"CMakeLists.txt\""
                        + System.lineSeparator()
                        + "android.externalNativeBuild.cmake.version \""
                        + CMAKE_VERSION
                        + "\"");

        Files.write(project.file("CMakeLists.txt").toPath(),
                cmakeLists.getBytes(StandardCharsets.UTF_8));

        getExecutor().run("assemble");

        File cmakeDirectory = FileUtils.join(mSdkHome, SdkConstants.FD_CMAKE);
        assertThat(cmakeDirectory).isDirectory();
        File ndkDirectory = FileUtils.join(mSdkHome, SdkConstants.FD_NDK_SIDE_BY_SIDE);
        assertThat(ndkDirectory).isDirectory();
    }

    @Test
    public void checkNdkDownloading() throws Exception {
        AssumeUtil.assumeIsLinux();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.ndkVersion "
                        + "\""
                        + NDK_VERSION
                        + "\""
                        + System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\""
                        + System.lineSeparator()
                        + "android.externalNativeBuild.cmake.path \"CMakeLists.txt\""
                        + System.lineSeparator()
                        + "android.externalNativeBuild.cmake.version \""
                        + CMAKE_VERSION
                        + "\"");

        Files.write(
                project.file("CMakeLists.txt").toPath(),
                cmakeLists.getBytes(StandardCharsets.UTF_8));

        getExecutor().run("assembleDebug");

        // Check the cmake build actually worked here, as the NDK can fail to be configured in a
        // way that doesn't actully fail the build https://issuetracker.google.com/169742131
        try (Apk apk = project.getApk(GradleTestProject.ApkType.DEBUG)) {
            assertThat(apk).contains("lib/arm64-v8a/libhello-jni.so");
        }

        // Hard to get the NDK SxS flag here. Just check whether either expected directory exists.
        File legacyFolder = FileUtils.join(mSdkHome, SdkConstants.FD_NDK);
        File sxsFolder = FileUtils.join(mSdkHome, SdkConstants.FD_NDK_SIDE_BY_SIDE);
        if (!legacyFolder.isDirectory() && !sxsFolder.isDirectory()) {
            assertThat(sxsFolder).isDirectory();
        }
    }

    @Test
    public void checkNdkMissingLicense() throws Exception {
        AssumeUtil.assumeIsLinux();
        deleteLicense();
        deletePreviewLicense();

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.ndkVersion "
                        + "\""
                        + NDK_VERSION
                        + "\""
                        + System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\""
                        + System.lineSeparator()
                        + "android.externalNativeBuild.cmake.path \"CMakeLists.txt\""
                        + System.lineSeparator()
                        + "android.externalNativeBuild.cmake.version \""
                        + CMAKE_VERSION
                        + "\"");

        Files.write(
                project.file("CMakeLists.txt").toPath(),
                cmakeLists.getBytes(StandardCharsets.UTF_8));

        GradleBuildResult result = getExecutor().expectFailure().run("assembleDebug");

        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains(
                        "Failed to install the following Android SDK packages as some licences have not been accepted");
        assertThat(Throwables.getRootCause(result.getException()).getMessage()).contains("ndk");
    }
    @NonNull
    private GradleTaskExecutor getExecutor() {
        return getOfflineExecutor().withoutOfflineFlag();
    }

    private GradleTaskExecutor getOfflineExecutor() {
        return project.executor()
                .withSdkAutoDownload()
                .withArgument(
                        String.format(
                                "-D%1$s=file:///%2$s/",
                                AndroidSdkHandler.SDK_TEST_BASE_URL_PROPERTY,
                                TestUtils.getRemoteSdk()));
    }

    @NonNull
    private ModelBuilderV2 getModel() {
        return project.modelV2()
                .withSdkAutoDownload()
                .withArgument(
                        String.format(
                                "-D%1$s=file:///%2$s/",
                                AndroidSdkHandler.SDK_TEST_BASE_URL_PROPERTY,
                                TestUtils.getRemoteSdk()))
                .withoutOfflineFlag();
    }

    @Test
    public void checkDependencies_invalidDependency() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\""
                        + System.lineSeparator()
                        + "dependencies { api 'foo:bar:baz' }");

        GradleBuildResult result = getExecutor().expectFailure().run("assembleDebug");
        assertNotNull(result.getException());

        // Make sure the standard gradle error message is what the user sees.
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .startsWith("Could not find foo:bar:baz.");
    }

    @Test
    public void checkNoLicenseError_PlatformTarget() throws Exception {
        deleteLicense();

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\"");

        GradleBuildResult result = getExecutor().expectFailure().run("assembleDebug");
        assertNotNull(result.getException());

        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("Android SDK Platform " + PLATFORM_VERSION);
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("missing components");
    }

    private void deleteLicense() throws Exception {
        FileUtils.delete(licenseFile);
    }

    private void deletePreviewLicense() throws Exception {
        FileUtils.delete(previewLicenseFile);
    }

    @Test
    public void checkNoLicenseError_AddonTarget() throws Exception {
        deleteLicense();
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion \"Google Inc.:Google APIs:24\""
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\"");

        GradleBuildResult result = getExecutor().expectFailure().run("assembleDebug");
        assertNotNull(result.getException());

        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("Google APIs");
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("missing components");
    }

    @Test
    public void checkNoLicenseError_BuildTools() throws Exception {
        deleteLicense();
        deletePreviewLicense();

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion "
                        + PLATFORM_VERSION
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\"");

        GradleBuildResult result = getExecutor().expectFailure().run("assembleDebug");
        assertNotNull(result.getException());

        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains(
                        "Build-Tools "
                                + Revision.parseRevision(BUILD_TOOLS_VERSION).toShortString());
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("missing components");

        // Check that
        Collection<SyncIssue> syncIssues =
                getModel().ignoreSyncIssues().fetchModels().getContainer().getProject().getIssues().getSyncIssues();
        List<SyncIssue> syncErrors =
                syncIssues
                        .stream()
                        .filter(issue -> issue.getSeverity() == SyncIssue.SEVERITY_ERROR)
                        .collect(Collectors.toList());
        assertThat(syncErrors).hasSize(1);

        assertThat(syncErrors.get(0).getType()).isEqualTo(SyncIssue.TYPE_MISSING_SDK_PACKAGE);
        String data = syncErrors.get(0).getData();
        assertNotNull(data);
        assertThat(Splitter.on(' ').split(data))
                .containsExactly(
                        "platforms;android-" + PLATFORM_VERSION,
                        "build-tools;" + BUILD_TOOLS_VERSION);
    }

    @Test
    public void checkNoLicenseError_MultiplePackages() throws Exception {
        deleteLicense();

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                System.lineSeparator()
                        + "android.compileSdkVersion \"Google Inc.:Google APIs:24\""
                        + System.lineSeparator()
                        + "android.buildToolsVersion \""
                        + BUILD_TOOLS_VERSION
                        + "\"");

        GradleBuildResult result = getExecutor().expectFailure().run("assembleDebug");
        assertNotNull(result.getException());

        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("missing components");
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("Build-Tools " + ToolsRevisionUtils.MIN_BUILD_TOOLS_REV.toShortString());
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("Android SDK Platform 24");
        assertThat(Throwables.getRootCause(result.getException()).getMessage())
                .contains("Google APIs");
    }

    @Test
    public void checkPermissions_BuildTools() throws Exception {
        Assume.assumeFalse(SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS);

        // Change the permissions.
        Path sdkHomePath = mSdkHome.toPath();
        Set<PosixFilePermission> readOnlyDir =
                ImmutableSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE);

        Files.walk(sdkHomePath).forEach(path -> {
            try {
                Files.setPosixFilePermissions(path, readOnlyDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        try {
            // Request a new version of build tools.
            TestFileUtils.appendToFile(
                    project.getBuildFile(),
                    System.lineSeparator()
                            + "android.compileSdkVersion "
                            + PLATFORM_VERSION
                            + System.lineSeparator()
                            + "android.buildToolsVersion \""
                            + BUILD_TOOLS_VERSION
                            + "\"");

            GradleBuildResult result = getExecutor().expectFailure().run("assembleDebug");
            assertNotNull(result.getException());

            assertThat(Throwables.getRootCause(result.getException()).getMessage())
                    .contains(
                            "Build-Tools "
                                    + Revision.parseRevision(BUILD_TOOLS_VERSION).toShortString());
            assertThat(Throwables.getRootCause(result.getException()).getMessage())
                    .contains("not writable");
        } finally {
            Set<PosixFilePermission> readWriteDir =
                    ImmutableSet.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE);

            Files.walk(sdkHomePath).forEach(path -> {
                try {
                    Files.setPosixFilePermissions(path, readWriteDir);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private File getPlatformFolder() {
        return FileUtils.join(mSdkHome, SdkConstants.FD_PLATFORMS, "android-" + PLATFORM_VERSION);
    }

}
