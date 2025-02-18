/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.application;

import static com.android.testutils.truth.PathSubject.assertThat;

import static org.junit.Assert.assertEquals;

import com.android.SdkConstants;
import com.android.build.api.artifact.SingleArtifact;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.ScannerSubject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.OptionalBooleanOption;
import com.android.utils.FileUtils;

import com.google.common.truth.Truth;

import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Integration tests for manifest merging.
 */
public class ManifestMergingTest {

    @Rule
    public GradleTestProject libsTest = GradleTestProject.builder()
            .withName("libsTest")
            .fromTestProject("libsTest")
            .create();

    @Rule
    public GradleTestProject flavors = GradleTestProject.builder()
            .withName("flavors")
            .fromTestProject("flavors")
            .create();

    @Rule
    public GradleTestProject navigation =
            GradleTestProject.builder()
                    .withName("navigation")
                    .fromTestProject("navigation")
                    .create();

    @Test
    public void checkManifestMergingForLibraries() throws Exception {
        libsTest.executor().run("clean", "build");
        File fileOutput =
                libsTest.file(
                        "libapp/build/"
                                + SdkConstants.FD_INTERMEDIATES
                                + File.separator
                                + SingleArtifact.MERGED_MANIFEST.INSTANCE.getFolderName()
                                + "/debug/processDebugManifest/AndroidManifest.xml");

        assertThat(fileOutput).isFile();

        fileOutput =
                libsTest.file(
                        "libapp/build/"
                                + SdkConstants.FD_INTERMEDIATES
                                + File.separator
                                + SingleArtifact.MERGED_MANIFEST.INSTANCE.getFolderName()
                                + "/release/processReleaseManifest/AndroidManifest.xml");

        assertThat(fileOutput).isFile();
    }

    @Test
    public void checkManifestMergerReport() throws Exception {
        flavors.executor().run("clean", "assemble");
        File logs = new File(flavors.getOutputFile("apk").getParentFile(), "logs");
        File[] reports = logs.listFiles(file -> file.getName().startsWith("manifest-merger"));
        assertEquals(8, reports.length);
    }

    @Test
    public void checkManifestMergeBlameReport() throws Exception {
        flavors.executor().run("clean", "assemble");

        File dirs =
                FileUtils.join(
                        flavors.getOutputFile("apk").getParentFile().getParentFile(),
                        "intermediates",
                        "manifest_merge_blame_file");

        Truth.assertThat(dirs.listFiles()).hasLength(8);

        for (File dir : Objects.requireNonNull(dirs.listFiles())) {
            Truth.assertThat(Objects.requireNonNull(dir.listFiles())).hasLength(1);
            File taskFolder = Objects.requireNonNull(dir.listFiles())[0];

            Truth.assertThat(Objects.requireNonNull(taskFolder.listFiles())[0].getName())
                    .startsWith("manifest-merger-blame");
        }
    }

    @Test
    public void checkTestOnlyAttribute() throws Exception {
        flavors.executor()
                .with(OptionalBooleanOption.IDE_TEST_ONLY, false)
                .run("clean", "assembleF1FaDebug");

        assertThat(
                        flavors.file(
                                "build/intermediates/packaged_manifests/f1FaDebug/processF1FaDebugManifestForPackage/AndroidManifest.xml"))
                .doesNotContain("android:testOnly=\"true\"");

        flavors.executor()
                .with(OptionalBooleanOption.IDE_TEST_ONLY, true)
                .run("clean", "assembleF1FaDebug");

        assertThat(
                        flavors.file(
                                "build/intermediates/packaged_manifests/f1FaDebug/processF1FaDebugManifestForPackage/AndroidManifest.xml"))
                .contains("android:testOnly=\"true\"");
    }

    /** Check that setting targetSdkVersion to a preview version mark the manifest with testOnly. */
    @Test
    public void checkPreviewTargetSdkVersion() throws Exception {
        GradleTestProject appProject = libsTest.getSubproject("app");
        TestFileUtils.appendToFile(
                appProject.getBuildFile(),
                "android{\n"
                    + "    compileSdkVersion 24\n"
                    + "    defaultConfig{\n"
                    + "        minSdkVersion 15\n"
                    + "        //noinspection ExpiringTargetSdkVersion,ExpiredTargetSdkVersion\n"
                    + "        targetSdkVersion 'N'\n"
                    + "    }\n"
                    + "}");
        libsTest.executor().run("clean", ":app:build");
        assertThat(
                        appProject.file(
                                "build/intermediates/packaged_manifests/debug/processDebugManifestForPackage/AndroidManifest.xml"))
                .containsAllOf(
                        "android:minSdkVersion=\"15\"",
                        "android:targetSdkVersion=\"N\"",
                        "android:testOnly=\"true\"");

        libsTest.executor()
                .with(OptionalBooleanOption.IDE_TEST_ONLY, false)
                .run(":app:assembleDebug");

        assertThat(
                        appProject.file(
                                "build/intermediates/packaged_manifests/debug/processDebugManifestForPackage/AndroidManifest.xml"))
                .doesNotContain("android:testOnly=\"true\"");
    }

    /** Check that setting minSdkVersion to a preview version mark the manifest with testOnly */
    @Test
    public void checkPreviewMinSdkVersion() throws Exception {
        GradleTestProject appProject = libsTest.getSubproject("app");
        TestFileUtils.appendToFile(
                appProject.getBuildFile(),
                "android{\n"
                    + "    compileSdkVersion 24\n"
                    + "    defaultConfig{\n"
                    + "        minSdkVersion 'N'\n"
                    + "        //noinspection ExpiringTargetSdkVersion,ExpiredTargetSdkVersion\n"
                    + "        targetSdkVersion 15\n"
                    + "    }\n"
                    + "}");
        libsTest.executor().run("clean", ":app:assembleDebug");
        assertThat(
                        appProject.file(
                                "build/intermediates/packaged_manifests/debug/processDebugManifestForPackage/AndroidManifest.xml"))
                .containsAllOf(
                        "android:minSdkVersion=\"N\"",
                        "android:targetSdkVersion=\"15\"",
                        "android:testOnly=\"true\"");
    }

    @Test
    public void checkMinAndTargetSdkVersion_WithTargetDeviceApi() throws Exception {
        // Regression test for https://issuetracker.google.com/issues/37133933
        TestFileUtils.appendToFile(
                flavors.getBuildFile(),
                "android {\n"
                    + "    compileSdkVersion 24\n"
                    + "    defaultConfig {\n"
                    + "        minSdkVersion 15\n"
                    + "        //noinspection ExpiringTargetSdkVersion,ExpiredTargetSdkVersion\n"
                    + "        targetSdkVersion 24\n"
                    + "    }\n"
                    + "}");
        flavors.executor()
                .with(IntegerOption.IDE_TARGET_DEVICE_API, 22)
                .run("clean", "assembleF1FaDebug");
        File manifestFile =
                flavors.file(
                        "build/intermediates/packaged_manifests/f1FaDebug/processF1FaDebugManifestForPackage/AndroidManifest.xml");
        assertThat(manifestFile)
                .containsAllOf("android:minSdkVersion=\"15\"", "android:targetSdkVersion=\"24\"");
    }

    /**
     * Check that navigation files added to the app's source sets override each other as expected
     * and generate the expected <intent-filter> elements in the app's merged manifest
     */
    @Test
    public void checkManifestMergingWithNavigationFiles() throws Exception {
        navigation.executor().run("clean", ":app:assembleF1Debug");
        File manifestFile =
                navigation.file(
                        "app/build/intermediates/packaged_manifests/f1Debug/processF1DebugManifestForPackage/AndroidManifest.xml");
        assertThat(manifestFile).contains("/main/nav1");
        assertThat(manifestFile).contains("/f1/nav2");
        assertThat(manifestFile).contains("/debug/nav3");
        assertThat(manifestFile).contains("/f1Debug/nav4");
        assertThat(manifestFile).contains("/library/nav5");
        assertThat(manifestFile).doesNotContain("/library/nav1");
        assertThat(manifestFile).doesNotContain("/main/nav2");
        assertThat(manifestFile).doesNotContain("/main/nav3");
        assertThat(manifestFile).doesNotContain("/main/nav4");
        assertThat(manifestFile).doesNotContain("/f1/nav3");
        assertThat(manifestFile).doesNotContain("/f1/nav4");
        assertThat(manifestFile).doesNotContain("/debug/nav4");
    }

    /**
     * User may include navigation graphs in the library manifests but we do not resolve them into
     * intent filters until application manifest
     */
    @Test
    public void checkManifestMergingKeepsNavGraphs_ifLibraryIncludesNavGraphs() throws Exception {
        TestFileUtils.searchAndReplace(
                new File(
                        navigation.getSubproject("library").getMainSrcDir().getParent(),
                        "AndroidManifest.xml"),
                "</activity>",
                "        <nav-graph android:value=\"@navigation/nav1\"/>\n    </activity>");

        navigation.executor().run("clean", ":library:processDebugManifest");

        File manifestFile =
                navigation.file(
                        "library/build/intermediates"
                                + File.separator
                                + SingleArtifact.MERGED_MANIFEST.INSTANCE.getFolderName()
                                + "/debug/processDebugManifest/AndroidManifest.xml");
        // Deep links from nav graph are NOT resolved into intent filters at the lib level
        assertThat(manifestFile).contains("nav-graph android:value=\"@navigation/nav1\"");
    }

    /**
     * Deep links from nav xml included in the library manifest are resolved into intent filters at
     * the application level
     */
    @Test
    public void checkManifestMergingAddsDeepLinks_ifLibraryIncludesNavGraphs() throws Exception {
        File libManifest =
                new File(
                        navigation.getSubproject("library").getMainSrcDir().getParent(),
                        "AndroidManifest.xml");
        TestFileUtils.searchAndReplace(
                libManifest,
                "</activity>",
                "        <nav-graph android:value=\"@navigation/nav1\"/>\n    </activity>");
        File appManifest =
                new File(
                        navigation.getSubproject("app").getMainSrcDir().getParent(),
                        "AndroidManifest.xml");
        TestFileUtils.searchAndReplace(
                appManifest, "<nav-graph android:value=\"@navigation/nav1\" />", "");
        TestFileUtils.searchAndReplace(
                appManifest, "<nav-graph android:value=\"@navigation/nav3\" />", "");
        TestFileUtils.searchAndReplace(
                appManifest, "<nav-graph android:value=\"@navigation/nav4\" />", "");
        TestFileUtils.searchAndReplace(
                appManifest, "<nav-graph android:value=\"@navigation/nav5\" />", "");
        File navFolder =
                FileUtils.join(
                        navigation.getSubproject("app").getMainSrcDir().getParentFile(),
                        "res",
                        "navigation");
        FileUtils.delete(FileUtils.join(navFolder, "nav1.xml"));
        FileUtils.delete(FileUtils.join(navFolder, "nav3.xml"));
        FileUtils.delete(FileUtils.join(navFolder, "nav4.xml"));
        // Remove flavors
        String srcFolder =
                navigation.getSubproject("app").getMainSrcDir().getParentFile().getParent();
        FileUtils.deleteRecursivelyIfExists(new File(srcFolder, "debug"));
        FileUtils.deleteRecursivelyIfExists(new File(srcFolder, "f1"));
        FileUtils.deleteRecursivelyIfExists(new File(srcFolder, "f1Debug"));
        for (int i = 31; i <= 37; ++i) {
            TestFileUtils.replaceLine(navigation.getSubproject("app").getBuildFile(), i, "");
        }

        navigation.executor().run("clean", ":app:processDebugManifest");

        File manifestFile =
                navigation.file(
                        "app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml");

        // Deep links from nav graph ARE resolved into intent filters at the app level
        assertThat(manifestFile).contains("library/nav1");
        assertThat(manifestFile).contains("main/nav2");
    }

    /**
     * Check that a navigation file added to a library's source set generates the expected
     * <intent-filter> element in the merged manifest for the library's androidTest APK
     */
    @Test
    public void checkManifestMergingForLibraryAndroidTestWithNavigationFiles() throws Exception {
        TestFileUtils.searchAndReplace(
                new File(
                        navigation.getSubproject("library").getMainSrcDir().getParent(),
                        "AndroidManifest.xml"),
                "</activity>",
                "        <nav-graph android:value=\"@navigation/nav1\"/>\n    </activity>");

        navigation.executor().run("clean", ":library:assembleDebugAndroidTest");

        File manifestFile =
                navigation.file(
                        "library/build/intermediates/packaged_manifests/debugAndroidTest/processDebugAndroidTestManifest/AndroidManifest.xml");
        assertThat(manifestFile).contains("/library/nav1");
    }

    @Test
    public void checkManifestFile_doesNotRebuildWhenNonNavigationResourceAreChanged()
            throws Exception {
        navigation.executor().run("clean", ":app:assembleDebug");
        File manifestFile =
                navigation.file(
                        "app/build/intermediates/packaged_manifests/f1Debug/AndroidManifest.xml");

        long timestampAfterFirstBuild = manifestFile.lastModified();

        // Change and add different resources but not navigation xmls
        TestFileUtils.searchAndReplace(
                navigation.getSubproject("library").file("src/main/res/values/strings.xml"),
                "</resources>",
                "    <string name=\"library_string1\">string1</string>\n</resources>");
        TestFileUtils.searchAndReplace(
                navigation.getSubproject("app").file("src/main/res/values/strings.xml"),
                "</resources>",
                "    <string name=\"added_string\">added string</string>\n</resources>");
        FileUtils.createFile(
                navigation.getSubproject("app").file("src/main/res/layout/additional.xml"),
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                    + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                    + "    android:layout_width=\"fill_parent\"\n"
                    + "    android:layout_height=\"fill_parent\"\n"
                    + "    android:orientation=\"vertical\" >\n"
                    + "</LinearLayout>");

        navigation.executor().run(":app:assembleDebug");

        Truth.assertThat(timestampAfterFirstBuild).isEqualTo(manifestFile.lastModified());
    }

    @Test
    public void checkManifestFile_rebuildsWhenNavigationResourceAreChanged() throws Exception {
        navigation.executor().run("clean", ":app:assembleDebug");
        File manifestFile =
                navigation.file(
                        "app/build/intermediates/packaged_manifests/f1Debug/processF1DebugManifestForPackage/AndroidManifest.xml");

        long timestampAfterFirstBuild = manifestFile.lastModified();

        // Change and add different resources but not navigation xmls
        TestFileUtils.searchAndReplace(
                navigation.getSubproject("library").file("src/main/res/navigation/nav5.xml"),
                "<deepLink app:uri=\"www.example.com/library/nav5\" />",
                "<deepLink app:uri=\"www.example.com/library_updated/nav5\" />");

        navigation.executor().run(":app:assembleDebug");

        Truth.assertThat(timestampAfterFirstBuild).isLessThan(manifestFile.lastModified());
    }

    @Test
    public void checkManifestFile_rebuildsWhenInjectedVersionCodeIsChanged() throws Exception {
        // Regression test for https://issuetracker.google.com/issues/148525428
        TestFileUtils.appendToFile(
                flavors.getBuildFile(),
                "android {\n"
                        + "    compileSdkVersion 24\n"
                        + "    defaultConfig {\n"
                        + "        versionCode 123\n"
                        + "    }\n"
                        + "}");
        flavors.executor().run("clean", "assembleF1FaDebug");

        TestFileUtils.searchAndReplace(
                flavors.getBuildFile(), "versionCode 123", "versionCode 124");

        GradleBuildResult buildResult = flavors.executor().run(":assembleF1FaDebug");
        Truth.assertThat(buildResult.getDidWorkTasks()).contains(":processF1FaDebugManifest");
        assertThat(
                        flavors.getIntermediateFile(
                                "merged_manifests",
                                "f1FaDebug",
                                "processF1FaDebugManifest",
                                "AndroidManifest.xml"))
                .contains("android:versionCode=\"124\"");
    }

    @Test
    public void checkNamespaceFromDsl() throws Exception {
        GradleBuildResult buildResult = flavors.executor().run("clean", "assembleF1FaDebug");
        Truth.assertThat(buildResult.getDidWorkTasks()).contains(":processF1FaDebugManifest");
        assertThat(
                        flavors.getIntermediateFile(
                                "merged_manifest",
                                "f1FaDebug",
                                "processF1FaDebugMainManifest",
                                "AndroidManifest.xml"))
                .contains("package=\"com.android.tests.flavors\" >");
    }

    @Test
    public void checkNoAvailableNamespace() throws Exception {
        TestFileUtils.searchAndReplace(
                flavors.getBuildFile(), "namespace \"com.android.tests.flavors\"", "");
        GradleBuildResult buildResult =
                flavors.executor().expectFailure().run("clean", "assembleF1FaDebug");
        ScannerSubject.assertThat(buildResult.getStderr()).contains("Namespace not specified");
    }

    // an integration test to make sure using tool:ignore_warning doesn't cause any failures
    @Test
    public void checkUseIgnoreWarning() throws Exception {
        File appManifest = new File(flavors.getMainSrcDir().getParent(), "AndroidManifest.xml");
        TestFileUtils.searchAndReplace(
                appManifest,
                "<application\n"
                        + "        android:icon=\"@drawable/icon\"\n"
                        + "        android:label=\"@string/app_name\" >",
                "<application\n"
                        + "        android:icon=\"@drawable/icon\"\n"
                        + "        tools:replace=\"android:icon\"\n"
                        + "        tools:ignore_warning=\"true\"\n"
                        + "        android:label=\"@string/app_name\" >");
        GradleBuildResult buildResult = flavors.executor().run("clean", "assembleDebug");
        Truth.assertThat(buildResult.getFailedTasks()).isEmpty();
    }

    @Test
    public void checkWarningIfPackageSpecified() throws Exception {
        TestFileUtils.searchAndReplace(
                new File(flavors.getLocation().getProjectDir(), "src/main/AndroidManifest.xml"),
                "<manifest",
                "<manifest package=\"com.android.tests.flavors\"");
        GradleBuildResult buildResult = flavors.executor().run("clean", "assembleF1FaDebug");
        ScannerSubject.assertThat(buildResult.getStdout())
                .contains(
                        "package=\"com.android.tests.flavors\" found in source"
                                + " AndroidManifest.xml");

        // Validate that the warning is not present with SUPPRESS_MANIFEST_PACKAGE_WARNING flag
        buildResult =
                flavors.executor()
                        .with(BooleanOption.SUPPRESS_MANIFEST_PACKAGE_WARNING, true)
                        .run("clean", "assembleF1FaDebug");
        ScannerSubject.assertThat(buildResult.getStdout())
                .doesNotContain(
                        "package=\"com.android.tests.flavors\" found in source"
                                + " AndroidManifest.xml");
    }

    @Test
    public void checkErrorIfWrongPackageSpecified() throws Exception {
        TestFileUtils.searchAndReplace(
                new File(flavors.getLocation().getProjectDir(), "src/main/AndroidManifest.xml"),
                "<manifest",
                "<manifest package=\"com.wrong\"");
        GradleBuildResult buildResult =
                flavors.executor().expectFailure().run("clean", "assembleF1FaDebug");
        ScannerSubject.assertThat(buildResult.getStderr())
                .contains("Incorrect package=\"com.wrong\" found in source AndroidManifest.xml");
    }

    @Test
    public void checkErrorIfDifferentPackageSpecifiedInOverlayManifest() throws Exception {
        Path overlayManifest =
                new File(flavors.getLocation().getProjectDir(), "src/f1/AndroidManifest.xml")
                        .toPath();
        Files.write(overlayManifest, List.of("<manifest package=\"wrong.package\"/>"));
        GradleBuildResult buildResult =
                flavors.executor().expectFailure().run("clean", "assembleF1FaDebug");
        ScannerSubject.assertThat(buildResult.getStderr())
                .contains(
                        "Suggestion: remove the package=\"wrong.package\" declaration at "
                                + overlayManifest);
    }

    @Test
    public void checkAddedManifestWithVariantApi() throws Exception {
        Path overlayManifest =
                new File(flavors.getLocation().getProjectDir(), "AndroidManifest.xml").toPath();
        Files.write(
                overlayManifest,
                List.of(
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                        "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"",
                        " android:versionCode=\"123\"/>"));
        TestFileUtils.appendToFile(
                flavors.getBuildFile(),
                "androidComponents {\n"
                    + "  onVariants(selector().all(),  { variant ->\n"
                    + "    variant.sources.manifests.addStaticManifestFile(\"AndroidManifest.xml\")\n"
                    + "  })\n"
                    + "}\n");

        flavors.executor().run("clean", "assembleF1FaDebug");

        assertThat(
                        flavors.getIntermediateFile(
                                "merged_manifest",
                                "f1FaDebug",
                                "processF1FaDebugMainManifest",
                                "AndroidManifest.xml"))
                .contains("android:versionCode=\"123\"");
    }

    @Test
    public void checkAddedGeneratedManifestWithVariantApi() throws Exception {
        String manifest =
                "<?xml version=\\\"1.0\\\" encoding=\\\"utf-8\\\"?>\\n"
                        + "<manifest"
                        + " xmlns:android=\\\"http://schemas.android.com/apk/res/android\\\"\\n"
                        + " android:versionCode=\\\"123\\\"/>";
        String manifestCreatorTask =
                "abstract class ManifestCreatorTask extends DefaultTask {\n"
                        + "   @OutputFile\n"
                        + "   abstract RegularFileProperty getOutputManifest()\n"
                        + "   @TaskAction\n"
                        + "   void taskAction() { \n"
                        + "      println(\"Writes to \" +"
                        + " getOutputManifest().get().getAsFile().getAbsolutePath())\n"
                        + "      def myFile = getOutputManifest().get().getAsFile()\n"
                        + "      myFile.write('"
                        + manifest
                        + "')\n"
                        + "   }\n"
                        + "}\n";

        TestFileUtils.appendToFile(
                flavors.getBuildFile(),
                manifestCreatorTask
                        + " androidComponents {\n"
                        + "                    onVariants(selector().all()) { variant ->\n"
                        + "                    // use addGeneratedSourceDirectory for adding"
                        + " generated resources\n"
                        + "                    TaskProvider<ManifestCreatorTask>"
                        + " manifestCreatorTask =\n"
                        + "                        project.tasks.register('create' +"
                        + " variant.getName() + 'Manifest', ManifestCreatorTask.class){\n"
                        + "                            getOutputManifest().set(new"
                        + " File(project.layout.buildDirectory.asFile.get(),"
                        + " \"AndroidManifest.xml\"))\n"
                        + "                        }\n"
                        + "\n"
                        + "                   "
                        + " variant.sources.manifests.addGeneratedManifestFile(\n"
                        + "                            manifestCreatorTask,\n"
                        + "                            { it.getOutputManifest() })\n"
                        + "                   }}\n");

        flavors.executor().run("clean", "assembleF1FaDebug");

        assertThat(
                        flavors.getIntermediateFile(
                                "merged_manifest",
                                "f1FaDebug",
                                "processF1FaDebugMainManifest",
                                "AndroidManifest.xml"))
                .contains("android:versionCode=\"123\"");
    }
}
