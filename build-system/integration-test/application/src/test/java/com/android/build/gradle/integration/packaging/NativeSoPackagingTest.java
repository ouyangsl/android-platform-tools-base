/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.integration.packaging;

import static com.android.build.gradle.integration.common.fixture.TemporaryProjectModification.doTest;
import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.AbstractAndroidSubject;
import com.android.build.gradle.integration.common.truth.ScannerSubject;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.dsl.ModulePropertyKey;
import com.android.build.gradle.options.StringOption;
import com.android.bundle.Config;
import com.android.testutils.TestUtils;
import com.android.testutils.apk.Aab;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import kotlin.io.FilesKt;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Scanner;

/** test for packaging of asset files. */
public class NativeSoPackagingTest {

    @Rule
    public GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();

    private GradleTestProject appProject;
    private GradleTestProject libProject;
    private GradleTestProject libProject2;
    private GradleTestProject testProject;
    private GradleTestProject jarProject;
    private GradleTestProject jarProject2;

    private GradleBuildResult execute(String... tasks) throws Exception {
        // TODO: Remove once we understand the cause of flakiness.
        TestUtils.waitForFileSystemTick();
        return project.executor().run(tasks);
    }

    @Before
    public void setUp() throws Exception {
        appProject = project.getSubproject("app");
        libProject = project.getSubproject("library");
        libProject2 = project.getSubproject("library2");
        testProject = project.getSubproject("test");
        jarProject = project.getSubproject("jar");
        jarProject2 = project.getSubproject("jar2");

        // rewrite settings.gradle to remove un-needed modules
        project.setIncludedProjects("app", "library", "library2", "test", "jar", "jar2");

        // setup dependencies.
        TestFileUtils.appendToFile(
                appProject.getBuildFile(),
                "android {\n"
                        + "    publishNonDefault true\n"
                        + "}\n"
                        + "\n"
                        + "dependencies {\n"
                        + "    api project(':library')\n"
                        + "    api project(':jar')\n"
                        + "    api project(':jar2')\n"
                        + "}\n");

        TestFileUtils.appendToFile(
                libProject.getBuildFile(), "dependencies { api project(':library2') }\n");

        TestFileUtils.appendToFile(
                testProject.getBuildFile(),
                "android {\n"
                        + "    targetProjectPath ':app'\n"
                        + "    targetVariant 'debug'\n"
                        + "}\n");

        // put some default files in the 4 projects, to check non incremental packaging as well,
        // and to provide files to change to test incremental support.
        File appDir = appProject.getProjectDir();
        createOriginalSoFile(appDir,  "main",        "libapp.so",         "app:abcd");
        createOriginalSoFile(appDir,  "androidTest", "libapptest.so",     "appTest:abcd");

        File testDir = testProject.getProjectDir();
        createOriginalSoFile(testDir, "main",        "libtest.so",        "test:abcd");

        File libDir = libProject.getProjectDir();
        createOriginalSoFile(libDir,  "main",        "liblibrary.so",      "library:abcd");
        createOriginalSoFile(libDir,  "androidTest", "liblibrarytest.so",  "libraryTest:abcd");

        File lib2Dir = libProject2.getProjectDir();
        createOriginalSoFile(lib2Dir, "main",        "liblibrary2.so",     "library2:abcd");
        createOriginalSoFile(lib2Dir, "androidTest", "liblibrary2test.so", "library2Test:abcd");

        File jarDir = jarProject.getProjectDir();
        File resFolder = FileUtils.join(jarDir, "src", "main", "resources", "lib", "x86");
        FileUtils.mkdirs(resFolder);
        Files.asCharSink(new File(resFolder, "libjar.so"), Charsets.UTF_8).write("jar:abcd");

        File jar2Dir = jarProject2.getProjectDir();
        File res2Folder = FileUtils.join(jar2Dir, "src", "main", "resources", "lib", "x86");
        FileUtils.mkdirs(res2Folder);
        Files.asCharSink(new File(res2Folder, "libjar2.so"), Charsets.UTF_8).write("jar2:abcd");
    }

    private static void createOriginalSoFile(
            @NonNull File projectFolder,
            @NonNull String dimension,
            @NonNull String filename,
            @NonNull String content)
            throws Exception {
        File assetFolder = FileUtils.join(projectFolder, "src", dimension, "jniLibs", "x86");
        FileUtils.mkdirs(assetFolder);
        Files.asCharSink(new File(assetFolder, filename), Charsets.UTF_8).write(content);
    }

    @Test
    public void testNonIncrementalPackaging() throws Exception {
        project.executor().run("clean", "assembleDebug", "assembleAndroidTest");

        // check the files are there. Start from the bottom of the dependency graph
        checkAar(    libProject2, "liblibrary2.so",     "library2:abcd");
        checkTestApk(libProject2, "liblibrary2.so",     "library2:abcd");
        checkTestApk(libProject2, "liblibrary2test.so", "library2Test:abcd");

        checkAar(    libProject,  "liblibrary.so",     "library:abcd");
        // aar does not contain dependency's assets
        checkAar(    libProject, "liblibrary2.so",     null);
        // test apk contains both test-ony assets, lib assets, and dependency assets.
        checkTestApk(libProject, "liblibrary.so",      "library:abcd");
        checkTestApk(libProject, "liblibrary2.so",     "library2:abcd");
        checkTestApk(libProject, "liblibrarytest.so",  "libraryTest:abcd");
        // but not the assets of the dependency's own test
        checkTestApk(libProject, "liblibrary2test.so", null);

        // app contain own assets + all dependencies' assets.
        checkApk(    appProject, "libapp.so",         "app:abcd");
        checkApk(    appProject, "liblibrary.so",      "library:abcd");
        checkApk(    appProject, "liblibrary2.so",     "library2:abcd");
        checkApk(    appProject, "libjar.so",          "jar:abcd");
        checkApk(appProject, "libjar2.so", "jar2:abcd");
        checkTestApk(appProject, "libapptest.so",     "appTest:abcd");
        // app test does not contain dependencies' own test assets.
        checkTestApk(appProject, "liblibrarytest.so",  null);
        checkTestApk(appProject, "liblibrary2test.so", null);
    }

    // ---- APP DEFAULT ---

    @Test
    public void testAppProjectWithNewAssetFile() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(appProject, project -> {
            project.addFile("src/main/jniLibs/x86/libnewapp.so", "newfile content");
            execute("app:assembleDebug");

            checkApk(appProject, "libnewapp.so", "newfile content");
        });
    }

    @Test
    public void testAppProjectWithRemovedAssetFile() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(appProject, project -> {
            project.removeFile("src/main/jniLibs/x86/libapp.so");
            execute("app:assembleDebug");

            checkApk(appProject, "libapp.so", null);
        });
    }

    @Test
    public void testAppProjectWithRenamedAssetFile() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(
                appProject,
                project -> {
                    project.removeFile("src/main/jniLibs/x86/libapp.so");
                    project.addFile("src/main/jniLibs/x86/moved_libapp.so", "app:abcd");
                    execute("app:assembleDebug");

                    checkApk(appProject, "libapp.so", null);
                    checkApk(appProject, "moved_libapp.so", "app:abcd");
                });
    }

    @Test
    public void testAppProjectWithAssetFileWithChangedAbi() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(
                appProject,
                project -> {
                    project.removeFile("src/main/jniLibs/x86/libapp.so");
                    project.addFile("src/main/jniLibs/x86_64/libapp.so", "app:abcd");
                    execute("app:assembleDebug");

                    checkApk(appProject, "libapp.so", null);
                    checkApk(appProject, "x86_64", "libapp.so", "app:abcd");
                });
    }

    @Test
    public void testAppProjectWithModifiedAssetFile() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(appProject, project -> {
            project.replaceFile("src/main/jniLibs/x86/libapp.so", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "libapp.so", "new content");
        });
    }

    @Test
    public void testAppProjectWithNewAssetFileOverridingDependency() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(
                appProject,
                project -> {
                    project.addFile("src/main/jniLibs/x86/liblibrary.so", "new content");
                    GradleBuildResult result = execute("app:assembleDebug");
                    try (Scanner stdout = result.getStdout()) {
                        ScannerSubject.assertThat(stdout)
                                .contains("2 files found for path 'lib/x86/liblibrary.so'.");
                    }

                    checkApk(appProject, "liblibrary.so", "new content");

                    // now remove it to test it works in the other direction
                    project.removeFile("src/main/jniLibs/x86/liblibrary.so");
                    execute("app:assembleDebug");

                    checkApk(appProject, "liblibrary.so", "library:abcd");
                });
    }

    @Test
    public void testAppProjectWithNewAssetFileInDebugSourceSet() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(appProject, project -> {
            project.addFile("src/debug/jniLibs/x86/libapp.so", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "libapp.so", "new content");

            // now remove it to test it works in the other direction
            project.removeFile("src/debug/jniLibs/x86/libapp.so");
            execute("app:assembleDebug");

            checkApk(appProject, "libapp.so", "app:abcd");
        });
    }

    /**
     * Check for correct behavior when the order of pre-merged so files changes. This must be
     * supported in order to use @Classpath annotations on the MergeNativeLibsTask inputs.
     */
    @Test
    public void testAppProjectWithReorderedDeps() throws Exception {
        execute("app:clean", "app:assembleDebug");

        doTest(
                appProject,
                project -> {
                    // change order of dependencies in app from (jar, jar2) to (jar2, jar).
                    project.replaceInFile("build.gradle", ":jar2", ":tempJar2");
                    project.replaceInFile("build.gradle", ":jar", ":tempJar");
                    project.replaceInFile("build.gradle", ":tempJar2", ":jar");
                    project.replaceInFile("build.gradle", ":tempJar", ":jar2");
                    execute("app:assembleDebug");

                    checkApk(appProject, "liblibrary.so", "library:abcd");
                    checkApk(appProject, "liblibrary2.so", "library2:abcd");
                    checkApk(appProject, "libjar.so", "jar:abcd");
                    checkApk(appProject, "libjar2.so", "jar2:abcd");
                });
    }

    @Test
    public void testAppProjectWithModifiedAssetInDependency() throws Exception {
        execute("app:clean", "library:clean", "app:assembleDebug");

        doTest(libProject, project -> {
            project.replaceFile("src/main/jniLibs/x86/liblibrary.so", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "liblibrary.so", "new content");
        });
    }

    @Test
    public void testAppProjectWithAddedAssetInDependency() throws Exception {
        execute("app:clean", "library:clean", "app:assembleDebug");

        doTest(libProject, project -> {
            project.addFile("src/main/jniLibs/x86/libnewlibrary.so", "new content");
            execute("app:assembleDebug");

            checkApk(appProject, "libnewlibrary.so", "new content");
        });
    }

    @Test
    public void testAppProjectWithRemovedAssetInDependency() throws Exception {
        execute("app:clean", "library:clean", "app:assembleDebug");

        doTest(libProject, project -> {
            project.removeFile("src/main/jniLibs/x86/liblibrary.so");
            execute("app:assembleDebug");

            checkApk(appProject, "liblibrary.so", null);
        });
    }

    // ---- APP TEST ---

    @Test
    public void testAppProjectTestWithNewAssetFile() throws Exception {
        execute("app:clean", "app:assembleAT");

        doTest(appProject, project -> {
            project.addFile("src/androidTest/jniLibs/x86/libnewapp.so", "new file content");
            execute("app:assembleAT");

            checkTestApk(appProject, "libnewapp.so", "new file content");
        });
    }

    @Test
    public void testAppProjectTestWithRemovedAssetFile() throws Exception {
        execute("app:clean", "app:assembleAT");

        doTest(appProject, project -> {
            project.removeFile("src/androidTest/jniLibs/x86/libapptest.so");
            execute("app:assembleAT");

            checkTestApk(appProject, "libapptest.so", null);
        });
    }

    @Test
    public void testAppProjectTestWithModifiedAssetFile() throws Exception {
        execute("app:clean", "app:assembleAT");

        doTest(appProject, project -> {
            project.replaceFile("src/androidTest/jniLibs/x86/libapptest.so", "new content");
            execute("app:assembleAT");

            checkTestApk(appProject, "libapptest.so", "new content");
        });
    }

    // ---- LIB DEFAULT ---

    @Test
    public void testLibProjectWithNewAssetFile() throws Exception {
        execute("library:clean", "library:assembleDebug");

        doTest(libProject, project -> {
            project.addFile("src/main/jniLibs/x86/libnewlibrary.so", "newfile content");
            execute("library:assembleDebug");

            checkAar(libProject, "libnewlibrary.so", "newfile content");
        });
    }

    @Test
    public void testLibProjectWithRemovedAssetFile() throws Exception {
        execute("library:clean", "library:assembleDebug");

        doTest(libProject, project -> {
            project.removeFile("src/main/jniLibs/x86/liblibrary.so");
            execute("library:assembleDebug");

            checkAar(libProject, "liblibrary.so", null);
        });
    }

    @Test
    public void testLibProjectWithModifiedAssetFile() throws Exception {
        execute("library:clean", "library:assembleDebug");

        doTest(libProject, project -> {
            project.replaceFile("src/main/jniLibs/x86/liblibrary.so", "new content");
            execute("library:assembleDebug");

            checkAar(libProject, "liblibrary.so", "new content");
        });
    }

    @Test
    public void testLibProjectWithNewAssetFileInDebugSourceSet() throws Exception {
        execute("library:clean", "library:assembleDebug");

        doTest(libProject, project -> {
            project.addFile("src/debug/jniLibs/x86/liblibrary.so", "new content");
            execute("library:assembleDebug");

            checkAar(libProject, "liblibrary.so", "new content");

            // now remove it to test it works in the other direction
            project.removeFile("src/debug/jniLibs/x86/liblibrary.so");
            execute("library:assembleDebug");

            checkAar(libProject, "liblibrary.so", "library:abcd");
        });
    }

    // ---- LIB TEST ---

    @Test
    public void testLibProjectTestWithNewAssetFile() throws Exception {
        execute("library:clean", "library:assembleAT");

        doTest(libProject, project -> {
            project.addFile("src/androidTest/jniLibs/x86/libnewlibrary.so", "new file content");
            execute("library:assembleAT");

            checkTestApk(libProject, "libnewlibrary.so", "new file content");
        });
    }

    @Test
    public void testLibProjectTestWithRemovedAssetFile() throws Exception {
        execute("library:clean", "library:assembleAT");

        doTest(libProject, project -> {
            project.removeFile("src/androidTest/jniLibs/x86/liblibrarytest.so");
            execute("library:assembleAT");

            checkTestApk(libProject, "liblibrarytest.so", null);
        });
    }

    @Test
    public void testLibProjectTestWithModifiedAssetFile() throws Exception {
        execute("library:clean", "library:assembleAT");

        doTest(libProject, project -> {
            project.replaceFile("src/androidTest/jniLibs/x86/liblibrarytest.so", "new content");
            execute("library:assembleAT");

            checkTestApk(libProject, "liblibrarytest.so", "new content");
        });
    }

    @Test
    public void testLibProjectTestWithNewAssetFileOverridingTestedLib() throws Exception {
        execute("library:clean", "library:assembleAT");

        doTest(
                libProject,
                project -> {
                    project.addFile("src/androidTest/jniLibs/x86/liblibrary.so", "new content");
                    GradleBuildResult result = execute("library:assembleAT");
                    try (Scanner stdout = result.getStdout()) {
                        ScannerSubject.assertThat(stdout)
                                .contains("2 files found for path 'lib/x86/liblibrary.so'.");
                    }

                    checkTestApk(libProject, "liblibrary.so", "new content");

                    // now remove it to test it works in the other direction
                    project.removeFile("src/androidTest/jniLibs/x86/liblibrary.so");
                    execute("library:assembleAT");

                    checkTestApk(libProject, "liblibrary.so", "library:abcd");
                });
    }

    @Test
    public void testLibProjectTestWithNewAssetFileOverridingDepenency() throws Exception {
        execute("library:clean", "library:assembleAT");

        doTest(
                libProject,
                project -> {
                    project.addFile("src/androidTest/jniLibs/x86/liblibrary2.so", "new content");
                    GradleBuildResult result = execute("library:assembleAT");
                    try (Scanner stdout = result.getStdout()) {
                        ScannerSubject.assertThat(stdout)
                                .contains("2 files found for path 'lib/x86/liblibrary2.so'.");
                    }

                    checkTestApk(libProject, "liblibrary2.so", "new content");

                    // now remove it to test it works in the other direction
                    project.removeFile("src/androidTest/jniLibs/x86/liblibrary2.so");
                    execute("library:assembleAT");

                    checkTestApk(libProject, "liblibrary2.so", "library2:abcd");
                });
    }

    // ---- TEST DEFAULT ---

    @Test
    public void testTestProjectWithNewAssetFile() throws Exception {
        project.executor().run("test:clean", "test:assembleDebug");

        doTest(
                testProject,
                project -> {
                    project.addFile("src/main/jniLibs/x86/libnewtest.so", "newfile content");
                    this.project.executor().run("test:assembleDebug");

                    checkApk(testProject, "libnewtest.so", "newfile content");
                });
    }

    @Test
    public void testTestProjectWithRemovedAssetFile() throws Exception {
        project.executor().run("test:clean", "test:assembleDebug");

        doTest(
                testProject,
                project -> {
                    project.removeFile("src/main/jniLibs/x86/libtest.so");
                    this.project.executor().run("test:assembleDebug");

                    checkApk(testProject, "libtest.so", null);
                });
    }

    @Test
    public void testTestProjectWithModifiedAssetFile() throws Exception {
        project.executor().run("test:clean", "test:assembleDebug");

        doTest(
                testProject,
                project -> {
                    project.replaceFile("src/main/jniLibs/x86/libtest.so", "new content");
                    this.project.executor().run("test:assembleDebug");

                    checkApk(testProject, "libtest.so", "new content");
                });
    }

    // ---- SO ALIGNMENT ----
    private void checkBundleAlignment(
            Config.UncompressNativeLibraries.PageAlignment expectedPageAlignment, String pageSize)
            throws Exception {
        File apkSelectConfig = project.file("apkSelectConfig.json");
        FilesKt.writeText(
                apkSelectConfig,
                "{\"sdk_version\":34,\"sdk_runtime\":{\"supported\":\"true\"},\"screen_density\":420,\"supported_abis\":[\"x86_64\",\"x86\",\"arm64-v8a\"],\"supported_locales\":[\"en\"]}",
                StandardCharsets.UTF_8);
        project.executor()
                .with(StringOption.IDE_APK_SELECT_CONFIG, apkSelectConfig.getAbsolutePath())
                .run(":app:bundleDebug", ":app:extractApksFromBundleForDebug");
        try (Aab appBundle = appProject.getBundle(GradleTestProject.ApkType.DEBUG)) {
            try (InputStream bundleConfigStream =
                    new BufferedInputStream(
                            java.nio.file.Files.newInputStream(
                                    appBundle.getEntry("BundleConfig.pb")))) {
                Config.BundleConfig config = Config.BundleConfig.parseFrom(bundleConfigStream);
                assertThat(config.getOptimizations().getUncompressNativeLibraries().getAlignment())
                        .named("bundleConfig optimizations.uncompress_native_libraries.alignment")
                        .isEqualTo(expectedPageAlignment);
            }
        }
        File extractedApks =
                appProject.getIntermediateFile(
                        "extracted_apks", "debug", "extractApksFromBundleForDebug");
        try (Apk extractedBase =
                new Apk(
                        Arrays.stream(Objects.requireNonNull(extractedApks.listFiles()))
                                .filter(it -> it.getName().startsWith("base-master"))
                                .findFirst()
                                .orElseThrow())) {
            PackagingTests.checkZipAlignWithPageAlignedSoFiles(extractedBase, pageSize);
        }
    }

    @Test
    public void testSharedObjectFilesAlignment4k() throws Exception {
        TestFileUtils.searchAndReplace(
                appProject.file("src/main/AndroidManifest.xml"),
                "<application ",
                "<application android:extractNativeLibs=\"false\" ");
        ModulePropertyKey.OptionalString flag =
                ModulePropertyKey.OptionalString.NATIVE_LIBRARY_PAGE_SIZE;
        TestFileUtils.appendToFile(
                appProject.getBuildFile(),
                "\nandroid {\n"
                        + "experimentalProperties[\""
                        + flag.getKey()
                        + "\"]=\"4k\"\n"
                        + "}");
        execute("app:assembleDebug");
        checkApk(appProject, "libapp.so", "app:abcd");
        PackagingTests.checkZipAlignWithPageAlignedSoFiles(appProject.getApk("debug"), "4");
        checkBundleAlignment(Config.UncompressNativeLibraries.PageAlignment.PAGE_ALIGNMENT_4K, "4");
    }

    @Test
    public void testSharedObjectFilesAlignment16k() throws Exception {
        TestFileUtils.searchAndReplace(
                appProject.file("src/main/AndroidManifest.xml"),
                "<application ",
                "<application android:extractNativeLibs=\"false\" ");
        // The default page size is 16k
        execute("app:assembleDebug");

        checkApk(appProject, "libapp.so", "app:abcd");
        PackagingTests.checkZipAlignWithPageAlignedSoFiles(appProject.getApk("debug"), "16");
        checkBundleAlignment(
                Config.UncompressNativeLibraries.PageAlignment.PAGE_ALIGNMENT_16K, "16");
    }

    @Test
    public void testSharedObjectFilesAlignment64k() throws Exception {
        TestFileUtils.searchAndReplace(
                appProject.file("src/main/AndroidManifest.xml"),
                "<application ",
                "<application android:extractNativeLibs=\"false\" ");
        ModulePropertyKey.OptionalString flag =
                ModulePropertyKey.OptionalString.NATIVE_LIBRARY_PAGE_SIZE;
        TestFileUtils.appendToFile(
                appProject.getBuildFile(),
                "\nandroid {\n"
                        + "experimentalProperties[\""
                        + flag.getKey()
                        + "\"]=\"64k\"\n"
                        + "}");
        execute("app:assembleDebug");

        checkApk(appProject, "libapp.so", "app:abcd");
        PackagingTests.checkZipAlignWithPageAlignedSoFiles(appProject.getApk("debug"), "64");
        checkBundleAlignment(
                Config.UncompressNativeLibraries.PageAlignment.PAGE_ALIGNMENT_64K, "64");
    }

    @Test
    public void testSharedObjectFilesInvalidAlignment() throws Exception {
        TestFileUtils.searchAndReplace(
                appProject.file("src/main/AndroidManifest.xml"),
                "<application ",
                "<application android:extractNativeLibs=\"false\" ");
        ModulePropertyKey.OptionalString flag =
                ModulePropertyKey.OptionalString.NATIVE_LIBRARY_PAGE_SIZE;
        TestFileUtils.appendToFile(
                appProject.getBuildFile(),
                "\nandroid {\n"
                        + "experimentalProperties[\""
                        + flag.getKey()
                        + "\"]=\"0k\"\n"
                        + "}");
        TestUtils.waitForFileSystemTick();
        GradleBuildResult result = project.executor().expectFailure().run("app:assembleDebug");
        ScannerSubject.assertThat(result.getStderr())
                .contains(
                        "Invalid value for "
                                + flag.getKey()
                                + ". Supported values are \"4k\", \"16k\", and \"64k\".");
        GradleBuildResult result2 = project.executor().expectFailure().run("app:bundleDebug");
        ScannerSubject.assertThat(result2.getStderr())
                .contains(
                        "Invalid value for "
                                + flag.getKey()
                                + ". Supported values are \"4k\", \"16k\", and \"64k\".");
    }

    /**
     * check an apk has (or not) the given asset file name.
     *
     * <p>If the content is non-null the file is expected to be there with the same content. If the
     * content is null the file is not expected to be there.
     *
     * @param project the project
     * @param filename the filename
     * @param content the content
     */
    private static void checkApk(
            @NonNull GradleTestProject project, @NonNull String filename, @Nullable String content)
            throws Exception {
        checkApk(project, "x86", filename, content);
    }

    /**
     * check an apk has (or not) the given asset file name.
     *
     * <p>If the content is non-null the file is expected to be there with the same content. If the
     * content is null the file is not expected to be there.
     *
     * @param project the project
     * @param abi the abi
     * @param filename the filename
     * @param content the content
     */
    private static void checkApk(
            @NonNull GradleTestProject project,
            @NonNull String abi,
            @NonNull String filename,
            @Nullable String content)
            throws Exception {
        Apk apk = project.getApk("debug");
        check(assertThatApk(apk), "lib", abi, filename, content);
        PackagingTests.checkZipAlign(apk);
    }

    /**
     * check a test apk has (or not) the given asset file name.
     *
     * <p>If the content is non-null the file is expected to be there with the same content. If the
     * content is null the file is not expected to be there.
     *
     * @param project the project
     * @param filename the filename
     * @param content the content
     */
    private void checkTestApk(
            @NonNull GradleTestProject project,
            @NonNull String filename,
            @Nullable String content) {
        check(TruthHelper.assertThat(project.getTestApk()), "lib", "x86", filename, content);
    }

    /**
     * check an aat has (or not) the given asset file name.
     *
     * <p>If the content is non-null the file is expected to be there with the same content. If the
     * content is null the file is not expected to be there.
     *
     * @param project the project
     * @param filename the filename
     * @param content the content
     */
    private static void checkAar(
            @NonNull GradleTestProject project,
            @NonNull String filename,
            @Nullable String content) {
        project.testAar("debug", it -> check(it, "jni", "x86", filename, content));
    }

    private static void check(
            @NonNull AbstractAndroidSubject subject,
            @NonNull String folderName,
            @NonNull String abi,
            @NonNull String filename,
            @Nullable String content) {
        if (content != null) {
            subject.containsFileWithContent(folderName + "/" + abi + "/" + filename, content);
        } else {
            subject.doesNotContain(folderName + "/" + abi + "/" + filename);
        }
    }
}
