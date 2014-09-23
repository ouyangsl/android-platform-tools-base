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

package com.android.build.gradle;

import static com.android.SdkConstants.DOT_ANDROID_PACKAGE;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FD_RES_RAW;
import static com.android.builder.core.BuilderConstants.ANDROID_WEAR_MICRO_APK;
import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;
import static com.android.builder.model.AndroidProject.FD_OUTPUTS;
import static com.android.builder.model.AndroidProject.PROPERTY_SIGNING_KEY_ALIAS;
import static com.android.builder.model.AndroidProject.PROPERTY_SIGNING_KEY_PASSWORD;
import static com.android.builder.model.AndroidProject.PROPERTY_SIGNING_STORE_FILE;
import static com.android.builder.model.AndroidProject.PROPERTY_SIGNING_STORE_PASSWORD;

import com.android.annotations.Nullable;
import com.android.ide.common.internal.CommandLineRunner;
import com.android.ide.common.internal.LoggedErrorException;
import com.android.utils.StdLogger;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

/**
 * Some manual tests for building projects.
 *
 * This requires an SDK, found through the ANDROID_HOME environment variable or present in the
 * Android Source tree under out/host/<platform>/sdk/... (result of 'make sdk')
 */
public class ManualBuildTest extends BuildTest {

    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFF0000FF;

    public void testOverlay1Content() throws Exception {
        File project = buildProject("overlay1", BasePlugin.GRADLE_TEST_VERSION);
        File drawableOutput = new File(project, "build/" + FD_INTERMEDIATES + "/res/debug/drawable");

        checkImageColor(drawableOutput, "no_overlay.png", GREEN);
        checkImageColor(drawableOutput, "type_overlay.png", GREEN);
    }

    public void testOverlay2Content() throws Exception {
        File project = buildProject("overlay2", BasePlugin.GRADLE_TEST_VERSION);
        File drawableOutput = new File(project, "build/" + FD_INTERMEDIATES + "/res/one/debug/drawable");

        checkImageColor(drawableOutput, "no_overlay.png", GREEN);
        checkImageColor(drawableOutput, "type_overlay.png", GREEN);
        checkImageColor(drawableOutput, "flavor_overlay.png", GREEN);
        checkImageColor(drawableOutput, "type_flavor_overlay.png", GREEN);
        checkImageColor(drawableOutput, "variant_type_flavor_overlay.png", GREEN);
    }

    public void testOverlay3Content() throws Exception {
        File project = buildProject("overlay3", BasePlugin.GRADLE_TEST_VERSION);
        File drawableOutput = new File(project, "build/" + FD_INTERMEDIATES + "/res/freebeta/debug/drawable");

        checkImageColor(drawableOutput, "no_overlay.png", GREEN);
        checkImageColor(drawableOutput, "debug_overlay.png", GREEN);
        checkImageColor(drawableOutput, "beta_overlay.png", GREEN);
        checkImageColor(drawableOutput, "free_overlay.png", GREEN);
        checkImageColor(drawableOutput, "free_beta_overlay.png", GREEN);
        checkImageColor(drawableOutput, "free_beta_debug_overlay.png", GREEN);
        checkImageColor(drawableOutput, "free_normal_overlay.png", RED);

        drawableOutput = new File(project, "build/" + FD_INTERMEDIATES + "/res/freenormal/debug/drawable");

        checkImageColor(drawableOutput, "no_overlay.png", GREEN);
        checkImageColor(drawableOutput, "debug_overlay.png", GREEN);
        checkImageColor(drawableOutput, "beta_overlay.png", RED);
        checkImageColor(drawableOutput, "free_overlay.png", GREEN);
        checkImageColor(drawableOutput, "free_beta_overlay.png", RED);
        checkImageColor(drawableOutput, "free_beta_debug_overlay.png", RED);
        checkImageColor(drawableOutput, "free_normal_overlay.png", GREEN);

        drawableOutput = new File(project, "build/" + FD_INTERMEDIATES + "/res/paidbeta/debug/drawable");

        checkImageColor(drawableOutput, "no_overlay.png", GREEN);
        checkImageColor(drawableOutput, "debug_overlay.png", GREEN);
        checkImageColor(drawableOutput, "beta_overlay.png", GREEN);
        checkImageColor(drawableOutput, "free_overlay.png", RED);
        checkImageColor(drawableOutput, "free_beta_overlay.png", RED);
        checkImageColor(drawableOutput, "free_beta_debug_overlay.png", RED);
        checkImageColor(drawableOutput, "free_normal_overlay.png", RED);
    }

    public void testRepo() {
        File repo = new File(testDir, "repo");

        try {
            runTasksOn(
                    new File(repo, "util"),
                    BasePlugin.GRADLE_TEST_VERSION,
                    "clean", "uploadArchives");
            runTasksOn(
                    new File(repo, "baseLibrary"),
                    BasePlugin.GRADLE_TEST_VERSION,
                    "clean", "uploadArchives");
            runTasksOn(
                    new File(repo, "library"),
                    BasePlugin.GRADLE_TEST_VERSION,
                    "clean", "uploadArchives");
            runTasksOn(
                    new File(repo, "app"),
                    BasePlugin.GRADLE_TEST_VERSION,
                    "clean", "assemble");
        } finally {
            // clean up the test repository.
            File testRepo = new File(repo, "testrepo");
            deleteFolder(testRepo);
        }
    }

    public void testLibsManifestMerging() throws Exception {
        File project = new File(testDir, "libsTest");
        File fileOutput = new File(project, "libapp/build/" + FD_INTERMEDIATES + "/bundles/release/AndroidManifest.xml");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "build");
        assertTrue(fileOutput.exists());
    }

    // test whether a library project has its fields ProGuarded
    public void testLibProguard() throws Exception {
        File project = new File(testDir, "libProguard");
        File fileOutput = new File(project, "build/" + FD_OUTPUTS + "/proguard/release");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "build");
        checkFile(fileOutput, "mapping.txt", new String[]{"int proguardInt -> a"});

    }

    // test whether proguard.txt has been correctly merged
    public void testLibProguardConsumerFile() throws Exception {
        File project = new File(testDir, "libProguardConsumerFiles");
        File debugFileOutput = new File(project, "build/" + FD_INTERMEDIATES + "/bundles/debug");
        File releaseFileOutput = new File(project, "build/" + FD_INTERMEDIATES + "/bundles/release");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "build");
        checkFile(debugFileOutput, "proguard.txt", new String[]{"A"});
        checkFile(releaseFileOutput, "proguard.txt", new String[]{"A", "B", "C"});
    }

    public void testAnnotations() throws Exception {
        File project = new File(testDir, "extractAnnotations");
        File debugFileOutput = new File(project, "build/" + FD_INTERMEDIATES + "/annotations/debug");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "assembleDebug");
        File file = new File(debugFileOutput, "annotations.zip");

        Map<String, String> map = Maps.newHashMap();
        //noinspection SpellCheckingInspection
        map.put("com/android/tests/extractannotations/annotations.xml", ""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<root>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest ExtractTest(int, java.lang.String) 0\">\n"
                + "    <annotation name=\"android.support.annotation.IdRes\" />\n"
                + "  </item>\n"
                // This item should be removed when I start supporting @hide
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest int getHiddenMethod()\">\n"
                + "    <annotation name=\"android.support.annotation.IdRes\" />\n"
                + "  </item>\n"
                // This item should be removed when I start supporting @hide
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest int getPrivate()\">\n"
                + "    <annotation name=\"android.support.annotation.IdRes\" />\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest int getVisibility()\">\n"
                + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                + "      <val name=\"value\" val=\"{com.android.tests.extractannotations.ExtractTest.VISIBLE, com.android.tests.extractannotations.ExtractTest.INVISIBLE, com.android.tests.extractannotations.ExtractTest.GONE, 5, 17, com.android.tests.extractannotations.Constants.CONSTANT_1}\" />\n"
                + "    </annotation>\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest int resourceTypeMethod(int, int)\">\n"
                + "    <annotation name=\"android.support.annotation.StringRes\" />\n"
                + "    <annotation name=\"android.support.annotation.IdRes\" />\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest int resourceTypeMethod(int, int) 0\">\n"
                + "    <annotation name=\"android.support.annotation.DrawableRes\" />\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest int resourceTypeMethod(int, int) 1\">\n"
                + "    <annotation name=\"android.support.annotation.IdRes\" />\n"
                + "    <annotation name=\"android.support.annotation.ColorRes\" />\n"
                + "  </item>\n"
                // This item should be removed when I start supporting @hide
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest java.lang.Object getPackagePrivate()\">\n"
                + "    <annotation name=\"android.support.annotation.IdRes\" />\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest java.lang.String getStringMode(int)\">\n"
                + "    <annotation name=\"android.support.annotation.StringDef\">\n"
                + "      <val name=\"value\" val=\"{com.android.tests.extractannotations.ExtractTest.STRING_1, com.android.tests.extractannotations.ExtractTest.STRING_2, &quot;literalValue&quot;, &quot;concatenated&quot;}\" />\n"
                + "    </annotation>\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest java.lang.String getStringMode(int) 0\">\n"
                + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                + "      <val name=\"value\" val=\"{com.android.tests.extractannotations.ExtractTest.VISIBLE, com.android.tests.extractannotations.ExtractTest.INVISIBLE, com.android.tests.extractannotations.ExtractTest.GONE, 5, 17, com.android.tests.extractannotations.Constants.CONSTANT_1}\" />\n"
                + "    </annotation>\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest void checkForeignTypeDef(int) 0\">\n"
                + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                + "      <val name=\"value\" val=\"{com.android.tests.extractannotations.Constants.CONSTANT_1, com.android.tests.extractannotations.Constants.CONSTANT_2}\" />\n"
                + "      <val name=\"flag\" val=\"true\" />\n"
                + "    </annotation>\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest void resourceTypeMethodWithTypeArgs(java.util.Map&lt;java.lang.String,? extends java.lang.Number&gt;, T, int) 0\">\n"
                + "    <annotation name=\"android.support.annotation.StringRes\" />\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest void resourceTypeMethodWithTypeArgs(java.util.Map&lt;java.lang.String,? extends java.lang.Number&gt;, T, int) 1\">\n"
                + "    <annotation name=\"android.support.annotation.DrawableRes\" />\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest void resourceTypeMethodWithTypeArgs(java.util.Map&lt;java.lang.String,? extends java.lang.Number&gt;, T, int) 2\">\n"
                + "    <annotation name=\"android.support.annotation.IdRes\" />\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest void testMask(int) 0\">\n"
                + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                + "      <val name=\"value\" val=\"{0, com.android.tests.extractannotations.Constants.FLAG_VALUE_1, com.android.tests.extractannotations.Constants.FLAG_VALUE_2}\" />\n"
                + "      <val name=\"flag\" val=\"true\" />\n"
                + "    </annotation>\n"
                + "  </item>\n"
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest void testNonMask(int) 0\">\n"
                + "    <annotation name=\"android.support.annotation.IntDef\">\n"
                + "      <val name=\"value\" val=\"{0, com.android.tests.extractannotations.Constants.CONSTANT_1, com.android.tests.extractannotations.Constants.CONSTANT_3}\" />\n"
                + "    </annotation>\n"
                + "  </item>\n"
                // This should be hidden when we start filtering out hidden classes on @hide!
                + "  <item name=\"com.android.tests.extractannotations.ExtractTest.HiddenClass int getHiddenMember()\">\n"
                + "    <annotation name=\"android.support.annotation.IdRes\" />\n"
                + "  </item>\n"
                + "</root>");

        checkJar(file, map);

        // check the resulting .aar file to ensure annotations.zip inclusion.
        File archiveFile = new File(project, "build/outputs/aar/extractAnnotations-debug.aar");
        assertTrue(archiveFile.isFile());
        ZipFile archive = null;
        try {
            archive = new ZipFile(archiveFile);
            ZipEntry entry = archive.getEntry("annotations.zip");
            assertNotNull(entry);
        } finally {
            if (archive != null) {
                archive.close();
            }
        }
    }

    public void testRsEnabledAnnotations() throws IOException {
        File project = new File(testDir, "extractRsEnabledAnnotations");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "assembleDebug");

        // check the resulting .aar file to ensure annotations.zip inclusion.
        File archiveFile = new File(project, "build/outputs/aar/extractRsEnabledAnnotations-debug.aar");
        assertTrue(archiveFile.isFile());
        ZipFile archive = null;
        try {
            archive = new ZipFile(archiveFile);
            ZipEntry entry = archive.getEntry("annotations.zip");
            assertNotNull(entry);
        } finally {
            if (archive != null) {
                archive.close();
            }
        }
    }

    public void testSimpleManifestMerger() throws IOException {
        File project = new File(testDir, "simpleManifestMergingTask");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "manifestMerger");
    }

    public void test3rdPartyTests() throws Exception {
        // custom because we want to run deviceCheck even without devices, since we use
        // a fake DeviceProvider that doesn't use a device, but only record the calls made
        // to the DeviceProvider and the DeviceConnector.
        runTasksOn(
                new File(testDir, "3rdPartyTests"),
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "deviceCheck");
    }

    public void testEmbedded() throws Exception {
        File project = new File(testDir, "embedded");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", ":main:assembleRelease");

        File mainApk = new File(project, "main/build/" + FD_OUTPUTS + "/apk/main-release-unsigned.apk");

        checkJar(mainApk, Collections.<String, String>singletonMap(
                FD_RES + '/' + FD_RES_RAW + '/' + ANDROID_WEAR_MICRO_APK + DOT_ANDROID_PACKAGE,
                null));
    }

    public void testUserProvidedTestAndroidManifest() throws Exception {
        File project = new File(testDir, "androidManifestInTest");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "assembleDebugTest");

        File testApk = new File(project, "build/" + FD_OUTPUTS + "/apk/androidManifestInTest-debug-test-unaligned.apk");

        File aapt = new File(sdkDir, "build-tools/19.1.0/aapt");

        assertTrue("Test requires build-tools 19.1.0", aapt.isFile());

        String[] command = new String[4];
        command[0] = aapt.getPath();
        command[1] = "l";
        command[2] = "-a";
        command[3] = testApk.getPath();

        CommandLineRunner commandLineRunner = new CommandLineRunner(new StdLogger(StdLogger.Level.ERROR));

        final List<String> aaptOutput = Lists.newArrayList();

        commandLineRunner.runCmdLine(command, new CommandLineRunner.CommandLineOutput() {
            @Override
            public void out(@Nullable String line) {
                if (line != null) {
                    aaptOutput.add(line);
                }
            }
            @Override
            public void err(@Nullable String line) {
                super.err(line);

            }
        }, null /*env vars*/);

        System.out.println("Beginning dump");
        boolean foundPermission = false;
        boolean foundMetadata = false;
        for (String line : aaptOutput) {
            if (line.contains("foo.permission-group.COST_MONEY")) {
                foundPermission = true;
            }
            if (line.contains("meta-data")) {
                foundMetadata = true;
            }
        }
        if (!foundPermission) {
            fail("Could not find user-specified permission group.");
        }
        if (!foundMetadata) {
            fail("Could not find meta-data under instrumentation ");
        }
    }

    public void testDensitySplits() throws Exception {
        File project = new File(testDir, "densitySplit");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "assembleDebug");

        Map<String, Integer> expected = Maps.newHashMapWithExpectedSize(5);
        expected.put("universal", 112);
        expected.put("mdpi", 212);
        expected.put("hdpi", 312);
        expected.put("xhdpi", 412);
        expected.put("xxhdpi", 512);

        checkVersionCode(project, null, expected, "densitySplit");
    }

    public void testDensitySplitWithOldMerger() throws Exception {
        File project = new File(testDir, "densitySplitWithOldMerger");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "assembleDebug");

        Map<String, Integer> expected = Maps.newHashMapWithExpectedSize(5);
        expected.put("universal", 112);
        expected.put("mdpi", 212);
        expected.put("hdpi", 312);
        expected.put("xhdpi", 412);
        expected.put("xxhdpi", 512);

        checkVersionCode(project, null, expected, "densitySplitWithOldMerger");
    }

    public void testAbiSplits() throws Exception {
        File project = new File(testDir, "ndkJniLib");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "app:assembleDebug");

        Map<String, Integer> expected = Maps.newHashMapWithExpectedSize(5);
        expected.put("gingerbread-universal",        1000123);
        expected.put("gingerbread-armeabi-v7a",      1100123);
        expected.put("gingerbread-mips",             1200123);
        expected.put("gingerbread-x86",              1300123);
        expected.put("icecreamSandwich-universal",   2000123);
        expected.put("icecreamSandwich-armeabi-v7a", 2100123);
        expected.put("icecreamSandwich-mips",        2200123);
        expected.put("icecreamSandwich-x86",         2300123);

        checkVersionCode(project, "app/", expected, "app");
    }

    private void checkVersionCode(
            File project,
            String outRoot,
            Map<String, Integer> expected,
            String baseName)
            throws IOException, InterruptedException, LoggedErrorException {
        File aapt = new File(sdkDir, "build-tools/20.0.0/aapt");

        assertTrue("Test requires build-tools 20.0.0", aapt.isFile());

        String[] command = new String[4];
        command[0] = aapt.getPath();
        command[1] = "dump";
        command[2] = "badging";

        CommandLineRunner commandLineRunner = new CommandLineRunner(new StdLogger(StdLogger.Level.ERROR));

        for (Map.Entry<String, Integer> entry : expected.entrySet()) {
            String path = "build/" + FD_OUTPUTS + "/apk/" + baseName + "-" + entry.getKey() + "-debug.apk";
            if (outRoot != null) {
                path = outRoot + path;
            }

            File apk = new File(project, path);

            command[3] = apk.getPath();

            final List<String> aaptOutput = Lists.newArrayList();

            commandLineRunner.runCmdLine(command, new CommandLineRunner.CommandLineOutput() {
                @Override
                public void out(@Nullable String line) {
                    if (line != null) {
                        aaptOutput.add(line);
                    }
                }
                @Override
                public void err(@Nullable String line) {
                    super.err(line);

                }
            }, null /*env vars*/);

            Pattern p = Pattern.compile("^package: name='(.+)' versionCode='([0-9]*)' versionName='(.*)'$");

            String versionCode = null;

            for (String line : aaptOutput) {
                Matcher m = p.matcher(line);
                if (m.matches()) {
                    versionCode = m.group(2);
                    break;
                }
            }

            assertNotNull("Unable to determine version code", versionCode);

            assertEquals("Unexpected version code for split: " + entry.getKey(),
                    entry.getValue().intValue(), Integer.parseInt(versionCode));
        }
    }

    public void testBasicWithSigningOverride() throws Exception {
        File project = new File(testDir, "basic");

        // add prop args for signing override.
        List<String> args = Lists.newArrayListWithExpectedSize(4);
        args.add("-P" + PROPERTY_SIGNING_STORE_FILE + "=" + new File(project, "debug.keystore").getPath());
        args.add("-P" + PROPERTY_SIGNING_STORE_PASSWORD + "=android");
        args.add("-P" + PROPERTY_SIGNING_KEY_ALIAS + "=AndroidDebugKey");
        args.add("-P" + PROPERTY_SIGNING_KEY_PASSWORD + "=android");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                args,
                "clean", ":assembleRelease");

        // check that the output exist. Since the filename is tried to signing/zipaligning
        // this gives us a fairly good idea about signing already.
        File releaseApk = new File(project, "build/" + FD_OUTPUTS + "/apk/basic-release.apk");
        assertTrue(releaseApk.isFile());

        // now check for signing file inside the archive.
        checkJar(releaseApk, Collections.<String,
                String>singletonMap("META-INF/CERT.RSA", null));
    }

    public void testMaxSdkVersion() throws Exception {
        File project = new File(testDir, "maxSdkVersion");

        runTasksOn(
                project,
                BasePlugin.GRADLE_TEST_VERSION,
                "clean", "assembleDebug");
        checkMaxSdkVersion(
                new File(project, "build/" + FD_OUTPUTS + "/apk/maxSdkVersion-f1-debug.apk"), "21");
        checkMaxSdkVersion(
                new File(project, "build/" + FD_OUTPUTS + "/apk/maxSdkVersion-f2-debug.apk"), "19");
    }

    private void checkMaxSdkVersion(File testApk, String version)
            throws InterruptedException, LoggedErrorException, IOException {

        File aapt = new File(sdkDir, "build-tools/19.1.0/aapt");

        assertTrue("Test requires build-tools 19.1.0", aapt.isFile());

        String[] command = new String[4];
        command[0] = aapt.getPath();
        command[1] = "dump";
        command[2] = "badging";
        command[3] = testApk.getPath();

        CommandLineRunner commandLineRunner = new CommandLineRunner(new StdLogger(StdLogger.Level.ERROR));

        final List<String> aaptOutput = Lists.newArrayList();

        commandLineRunner.runCmdLine(command, new CommandLineRunner.CommandLineOutput() {
            @Override
            public void out(@Nullable String line) {
                if (line != null) {
                    aaptOutput.add(line);
                }
            }
            @Override
            public void err(@Nullable String line) {
                super.err(line);

            }
        }, null /*env vars*/);

        System.out.println("Beginning dump");
        for (String line : aaptOutput) {
            if (line.equals("maxSdkVersion:'" + version + "'")) {
                return;
            }
        }
        fail("Could not find uses-sdk:maxSdkVersion set to " + version + " in apk dump");
    }



    private static void checkImageColor(File folder, String fileName, int expectedColor)
            throws IOException {
        File f = new File(folder, fileName);
        assertTrue("File '" + f.getAbsolutePath() + "' does not exist.", f.isFile());

        BufferedImage image = ImageIO.read(f);
        int rgb = image.getRGB(0, 0);
        assertEquals(String.format("Expected: 0x%08X, actual: 0x%08X for file %s",
                expectedColor, rgb, f),
                expectedColor, rgb);
    }

    private static void checkFile(File folder, String fileName, String[] expectedContents)
            throws IOException {
        File f = new File(folder, fileName);
        assertTrue("File '" + f.getAbsolutePath() + "' does not exist.", f.isFile());

        String contents = Files.toString(f, Charsets.UTF_8);
        for (String expectedContent : expectedContents) {
            assertTrue("File '" + f.getAbsolutePath() + "' does not contain: " + expectedContent,
                contents.contains(expectedContent));
        }
    }

    private static void checkJar(File jar, Map<String, String> pathToContents)
            throws IOException {
        assertTrue("File '" + jar.getPath() + "' does not exist.", jar.isFile());
        JarInputStream zis = null;
        FileInputStream fis;
        Set<String> notFound = Sets.newHashSet();
        notFound.addAll(pathToContents.keySet());
        fis = new FileInputStream(jar);
        try {
            zis = new JarInputStream(fis);

            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                String name = entry.getName();

                String expected = pathToContents.get(name);
                if (expected != null) {
                    notFound.remove(name);
                    if (!entry.isDirectory()) {
                        byte[] bytes = ByteStreams.toByteArray(zis);
                        if (bytes != null) {
                            String contents = new String(bytes, Charsets.UTF_8).trim();
                            assertEquals("Contents in " + name + " did not match",
                                    expected, contents);
                        }
                    }
                } else if (pathToContents.keySet().contains(name)) {
                    notFound.remove(name);
                }
                entry = zis.getNextEntry();
            }
        } finally {
            fis.close();
            if (zis != null) {
                zis.close();
            }
        }

        assertTrue("Did not find the following paths in the " + jar.getPath() + " file: " +
            notFound, notFound.isEmpty());
    }
}
