/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.sdklib.tool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.SdkConstants;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeRepoManager;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.PathFileWrapper;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.internal.avd.EmulatedProperties;
import com.android.sdklib.internal.avd.HardwareProperties;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.targets.SystemImageManager;
import com.android.testutils.MockLog;
import com.android.testutils.file.InMemoryFileSystems;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tests for {@link AvdManagerCli}
 *
 * <p>TODO: tests for command-line input
 */
public class AvdManagerCliTest {

    private final FileSystem fileSystem = InMemoryFileSystems.createInMemoryFileSystem();
    private final Path sdkPath = InMemoryFileSystems.getSomeRoot(fileSystem).resolve("sdk");
    private final Path avdPath = InMemoryFileSystems.getSomeRoot(fileSystem).resolve("avd");
    private final Path emuLibPath = sdkPath.resolve("emulator/lib");
    private final String android25GoogleApisSdkPath = "system-images;android-25;google_apis;x86";
    private final String android25GoogleApisPlayStoreSdkPath =
            "system-images;android-25;google_apis_playstore;x86";
    private final String android26WearSdkPath = "system-images;android-26;android-wear;armeabi-v7a";

    private AndroidSdkHandler mSdkHandler;
    private MockLog mLogger;
    private AvdManagerCli mCli;
    private AvdManager mAvdManager;
    private ISystemImage mGapiImage;
    private ISystemImage mGPlayImage;

    @Before
    public void setUp() throws Exception {
        RepositoryPackages packages = new RepositoryPackages();
        FakePackage.FakeLocalPackage p1 =
                new FakePackage.FakeLocalPackage(
                        android25GoogleApisSdkPath, sdkPath.resolve("gapi"));
        DetailsTypes.SysImgDetailsType details1 =
                AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
        details1.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
        details1.getAbis().add("x86");
        details1.setVendor(IdDisplay.create("google", "Google"));
        details1.setApiLevel(25);
        p1.setTypeDetails((TypeDetails) details1);
        InMemoryFileSystems.recordExistingFile(
                p1.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));
        InMemoryFileSystems.recordExistingFile(p1.getLocation().resolve(AvdManager.USERDATA_IMG));

        FakePackage.FakeLocalPackage p2 =
                new FakePackage.FakeLocalPackage(
                        android25GoogleApisPlayStoreSdkPath, sdkPath.resolve("play"));
        DetailsTypes.SysImgDetailsType details2 =
                AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
        details2.getTags().add(IdDisplay.create("google_apis_playstore", "Google Play"));
        details2.getAbis().add("x86");
        details2.setVendor(IdDisplay.create("google", "Google"));
        details2.setApiLevel(25);
        p2.setTypeDetails((TypeDetails) details2);
        InMemoryFileSystems.recordExistingFile(
                p2.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));
        InMemoryFileSystems.recordExistingFile(p2.getLocation().resolve(AvdManager.USERDATA_IMG));

        FakePackage.FakeLocalPackage p3 =
                new FakePackage.FakeLocalPackage(android26WearSdkPath, sdkPath.resolve("wear"));
        DetailsTypes.SysImgDetailsType details3 =
                AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
        details3.getTags().add(IdDisplay.create("android-wear", "Google APIs"));
        details3.getAbis().add("armeabi-v7a");
        details3.setApiLevel(26);
        details3.setExtensionLevel(5);
        details3.setBaseExtension(false);
        p3.setTypeDetails((TypeDetails)details3);
        InMemoryFileSystems.recordExistingFile(
                p3.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));
        InMemoryFileSystems.recordExistingFile(p3.getLocation().resolve(AvdManager.USERDATA_IMG));

        // Create a representative hardware configuration file
        String emuPath = "emulator";
        FakePackage.FakeLocalPackage p4 =
                new FakePackage.FakeLocalPackage(emuPath, sdkPath.resolve("emulator"));
        Path hardwareDefs = emuLibPath.resolve(SdkConstants.FN_HARDWARE_INI);
        createHardwarePropertiesFile(hardwareDefs);

        packages.setLocalPkgInfos(ImmutableList.of(p1, p2, p3, p4));

        RepoManager mgr = new FakeRepoManager(sdkPath, packages);

        mSdkHandler = new AndroidSdkHandler(sdkPath, avdPath, mgr);
        mLogger = new MockLog();
        mAvdManager =
                AvdManager.createInstance(
                        mSdkHandler,
                        avdPath,
                        DeviceManager.createInstance(mSdkHandler, mLogger),
                        mLogger);
        mCli =
                new AvdManagerCli(
                        mLogger,
                        mSdkHandler,
                        mAvdManager,
                        sdkPath.toString(),
                        avdPath.toString(),
                        null);

        FakeProgressIndicator progress = new FakeProgressIndicator();
        SystemImageManager systemImageManager = mSdkHandler.getSystemImageManager(progress);
        mGapiImage =
                systemImageManager.getImageAt(
                        mSdkHandler
                                .getLocalPackage(android25GoogleApisSdkPath, progress)
                                .getLocation());
        mGPlayImage =
                systemImageManager.getImageAt(
                        mSdkHandler
                                .getLocalPackage(android25GoogleApisPlayStoreSdkPath, progress)
                                .getLocation());
        Files.createDirectories(sdkPath.resolve("skins").resolve("nexus_s"));
    }

    @Test
    public void createAvd_withoutPlayStore() throws Exception {
        mCli.run(
                new String[] {
                    "create", "avd",
                    "--name", "testAvd",
                    "-k", android25GoogleApisSdkPath,
                    "-d", "Nexus 6P"
                });
        mAvdManager.reloadAvds();
        AvdInfo info = mAvdManager.getAvd("testAvd", true);
        assertEquals("x86", info.getAbiType());
        assertEquals("Google", info.getDeviceManufacturer());
        assertEquals(new AndroidVersion(25, null), info.getAndroidVersion());
        assertEquals(mGapiImage, info.getSystemImage());

        Path avdConfigFile = info.getDataFolderPath().resolve("config.ini");
        assertTrue(
                "Expected config.ini in " + info.getDataFolderPath(), Files.exists(avdConfigFile));
        Map<String, String> config =
                AvdManager.parseIniFile(new PathFileWrapper(avdConfigFile), null);
        assertEquals("123", config.get("integerPropName"));
        assertEquals("none", config.get("runtime.network.latency"));
        assertEquals("full", config.get("runtime.network.speed"));
        assertEquals("false", config.get("PlayStore.enabled"));
        assertEquals(
                EmulatedProperties.MAX_DEFAULT_RAM_SIZE,
                Storage.getStorageFromString(config.get("hw.ramSize")));
        assertEquals(
                new Storage(512, Storage.Unit.MiB),
                Storage.getStorageFromString(config.get("sdcard.size")));
        assertEquals(
                new Storage(384, Storage.Unit.MiB),
                Storage.getStorageFromString(config.get("vm.heapSize")));
    }

    @Test
    public void createAvd_withSkin() throws Exception {
        mCli.run(
                new String[] {
                    "create", "avd",
                    "--name", "testAvd",
                    "-k", "system-images;android-25;google_apis;x86",
                    "-d", "Nexus 6P",
                    "--skin", "nexus_s"
                });
        mAvdManager.reloadAvds();
        AvdInfo info = mAvdManager.getAvd("testAvd", true);

        Path avdConfigFile = info.getDataFolderPath().resolve("config.ini");
        assertTrue(
                "Expected config.ini in " + info.getDataFolderPath(), Files.exists(avdConfigFile));
        Map<String, String> config =
                AvdManager.parseIniFile(new PathFileWrapper(avdConfigFile), null);
        assertEquals("nexus_s", config.get("skin.name"));
        assertEquals(
                sdkPath.resolve("skins").resolve("nexus_s").toString(),
                config.get("skin.path"));
    }

    @Test
    public void createAvd_withPlayStore() throws Exception {
        mCli.run(
                new String[] {
                    "create", "avd",
                    "--name", "testAvd",
                    "-k", android25GoogleApisPlayStoreSdkPath,
                    "-d", "Nexus 6P",
                    "--sdcard", "100M",
                });
        mAvdManager.reloadAvds();
        AvdInfo info = mAvdManager.getAvd("testAvd", true);
        assertEquals("x86", info.getAbiType());
        assertEquals("Google", info.getDeviceManufacturer());
        assertEquals(new AndroidVersion(25, null), info.getAndroidVersion());
        assertEquals(mGPlayImage, info.getSystemImage());

        Path avdConfigFile = info.getDataFolderPath().resolve("config.ini");
        assertTrue(
                "Expected config.ini in " + info.getDataFolderPath(), Files.exists(avdConfigFile));
        Map<String, String> config =
                AvdManager.parseIniFile(new PathFileWrapper(avdConfigFile), null);
        assertEquals("123", config.get("integerPropName"));
        assertEquals("none", config.get("runtime.network.latency"));
        assertEquals("full", config.get("runtime.network.speed"));
        assertEquals("true", config.get("PlayStore.enabled"));
        assertEquals(
                EmulatedProperties.MAX_DEFAULT_RAM_SIZE,
                Storage.getStorageFromString(config.get("hw.ramSize")));
        assertEquals(
                new Storage(100, Storage.Unit.MiB),
                Storage.getStorageFromString(config.get("sdcard.size")));
        assertEquals(
                new Storage(384, Storage.Unit.MiB),
                Storage.getStorageFromString(config.get("vm.heapSize")));
    }

    @Test
    public void deleteAvd() throws Exception {
        mCli.run(
                new String[] {
                    "create", "avd",
                    "--name", "testAvd1",
                    "-k", android25GoogleApisSdkPath,
                    "-d", "Nexus 6P"
                });
        mCli.run(
                new String[] {
                    "create", "avd",
                    "--name", "testAvd2",
                    "-k", android26WearSdkPath,
                    "-d", "Nexus 6P"
                });
        mAvdManager.reloadAvds();
        assertEquals(2, mAvdManager.getAllAvds().length);

        mCli.run(new String[] {"delete", "avd", "--name", "testAvd1"});

        mAvdManager.reloadAvds();
        assertEquals(1, mAvdManager.getAllAvds().length);

        AvdInfo info = mAvdManager.getAvd("testAvd2", true);
        assertNotNull(info);
    }

    @Test
    public void moveAvd() throws Exception {
        mCli.run(
                new String[] {
                    "create", "avd",
                    "--name", "testAvd1",
                    "-k", android25GoogleApisSdkPath,
                    "-d", "Nexus 6P"
                });
        Path moved = avdPath.resolve("moved");
        mCli.run(
                new String[] {
                    "move", "avd",
                    "--name", "testAvd1",
                    "-p", moved.toAbsolutePath().toString(),
                    "-r", "newName"
                });
        mAvdManager.reloadAvds();
        assertEquals(1, mAvdManager.getAllAvds().length);

        AvdInfo info = mAvdManager.getAvd("newName", true);
        assertEquals(moved.toAbsolutePath(), info.getDataFolderPath());
    }

    @Test
    public void listAvds() throws Exception {
        mCli.run(
                new String[] {
                    "create", "avd",
                    "--name", "testGapiAvd",
                    "-k", android25GoogleApisSdkPath,
                    "-d", "Nexus 6P"
                });
        mCli.run(
                new String[] {
                    "create", "avd",
                    "--name", "testWearApi",
                    "-k", android26WearSdkPath,
                    "-d", "wearos_small_round"
                });
        mAvdManager.reloadAvds();
        mLogger.clear();
        mCli.run(new String[] {"list", "avds"});
        assertEquals(
                "P Available Android Virtual Devices:\n"
                        + "P     Name: testGapiAvd\n"
                        + "P   Device: Nexus 6PP  (Google)P \n"
                        + "P     Path: "
                        + InMemoryFileSystems.getPlatformSpecificPath("/avd/testGapiAvd.avd")
                        + "\n"
                        + "P   Target: Google APIs (Google)\n"
                        + "P           Based on: Android 7.1.1 (\"Nougat\")P  Tag/ABI:"
                        + " google_apis/x86\n"
                        + "P   Sdcard: 512 MB\n"
                        + "P ---------\n"
                        + "P     Name: testWearApi\n"
                        + "P   Device: wearos_small_roundP  (Google)P \n"
                        + "P     Path: "
                        + InMemoryFileSystems.getPlatformSpecificPath("/avd/testWearApi.avd")
                        + "\n"
                        + "P   Target: Google APIs\n"
                        + "P           Based on: Android 8.0 (\"Oreo\")P  Tag/ABI:"
                        + " android-wear/armeabi-v7a\n"
                        + "P   Sdcard: 512 MB\n",
                Joiner.on("").join(mLogger.getMessages()));
    }

    @Test
    public void listTargets() {
        RepoManager repoManager = mSdkHandler.getSdkManager(new FakeProgressIndicator());

        String p1Path = "platforms;android-25";
        FakePackage.FakeLocalPackage p1 =
                new FakePackage.FakeLocalPackage(p1Path, sdkPath.resolve("p1"));
        DetailsTypes.PlatformDetailsType details1 =
                AndroidSdkHandler.getRepositoryModule()
                        .createLatestFactory()
                        .createPlatformDetailsType();
        details1.setApiLevel(25);
        p1.setTypeDetails((TypeDetails) details1);
        InMemoryFileSystems.recordExistingFile(
                p1.getLocation().resolve(SdkConstants.FN_BUILD_PROP));
        String p2Path = "platforms;android-O";
        FakePackage.FakeLocalPackage p2 =
                new FakePackage.FakeLocalPackage(p2Path, sdkPath.resolve("p2"));
        DetailsTypes.PlatformDetailsType details2 =
                AndroidSdkHandler.getRepositoryModule()
                        .createLatestFactory()
                        .createPlatformDetailsType();
        details2.setApiLevel(25);
        details2.setCodename("O");
        p2.setTypeDetails((TypeDetails) details2);
        InMemoryFileSystems.recordExistingFile(
                p2.getLocation().resolve(SdkConstants.FN_BUILD_PROP));

        repoManager.getPackages().setLocalPkgInfos(ImmutableList.of(p1, p2));

        mCli.run(new String[] {"list", "targets"});
        assertEquals(
                "P Available Android targets:\n"
                        + "P ----------\n"
                        + "P id: 1 or \"android-25\"\n"
                        + "P      Name: Android API 25\n"
                        + "P      Type: Platform\n"
                        + "P      API level: 25\n"
                        + "P      Revision: 1\n"
                        + "P ----------\n"
                        + "P id: 2 or \"android-O\"\n"
                        + "P      Name: Android API 25, O preview (Preview)\n"
                        + "P      Type: Platform\n"
                        + "P      API level: O\n"
                        + "P      Revision: 1\n",
                Joiner.on("").join(mLogger.getMessages()));
    }

    @Test
    public void listDevices() {
        mCli.run(new String[] {"list", "devices", "-c"});
        assertEquals(
                ImmutableList.of(
                        "P automotive_1024p_landscape\n",
                        "P automotive_1080p_landscape\n",
                        "P automotive_1408p_landscape_with_google_apis\n",
                        "P automotive_1408p_landscape_with_play\n",
                        "P automotive_distant_display\n",
                        "P automotive_distant_display_with_play\n",
                        "P automotive_large_portrait\n",
                        "P automotive_portrait\n",
                        "P automotive_ultrawide\n",
                        "P Galaxy Nexus\n",
                        "P desktop_large\n",
                        "P desktop_medium\n",
                        "P medium_phone\n",
                        "P medium_tablet\n",
                        "P Nexus 10\n",
                        "P Nexus 4\n",
                        "P Nexus 5\n",
                        "P Nexus 5X\n",
                        "P Nexus 6\n",
                        "P Nexus 6P\n",
                        "P Nexus 7 2013\n",
                        "P Nexus 7\n",
                        "P Nexus 9\n",
                        "P Nexus One\n",
                        "P Nexus S\n",
                        "P pixel\n",
                        "P pixel_2\n",
                        "P pixel_2_xl\n",
                        "P pixel_3\n",
                        "P pixel_3_xl\n",
                        "P pixel_3a\n",
                        "P pixel_3a_xl\n",
                        "P pixel_4\n",
                        "P pixel_4_xl\n",
                        "P pixel_4a\n",
                        "P pixel_5\n",
                        "P pixel_6\n",
                        "P pixel_6_pro\n",
                        "P pixel_6a\n",
                        "P pixel_7\n",
                        "P pixel_7_pro\n",
                        "P pixel_7a\n",
                        "P pixel_8\n",
                        "P pixel_8_pro\n",
                        "P pixel_8a\n",
                        "P pixel_9\n",
                        "P pixel_9_pro\n",
                        "P pixel_9_pro_fold\n",
                        "P pixel_9_pro_xl\n",
                        "P pixel_c\n",
                        "P pixel_fold\n",
                        "P pixel_tablet\n",
                        "P pixel_xl\n",
                        "P resizable\n",
                        "P desktop_small\n",
                        "P small_phone\n",
                        "P tv_1080p\n",
                        "P tv_4k\n",
                        "P tv_720p\n",
                        "P wearos_large_round\n",
                        "P wearos_rect\n",
                        "P wearos_small_round\n",
                        "P wearos_square\n",
                        "P 2.7in QVGA\n",
                        "P 2.7in QVGA slider\n",
                        "P 3.2in HVGA slider (ADP1)\n",
                        "P 3.2in QVGA (ADP2)\n",
                        "P 3.3in WQVGA\n",
                        "P 3.4in WQVGA\n",
                        "P 3.7 FWVGA slider\n",
                        "P 3.7in WVGA (Nexus One)\n",
                        "P 4in WVGA (Nexus S)\n",
                        "P 4.65in 720p (Galaxy Nexus)\n",
                        "P 4.7in WXGA\n",
                        "P 5.1in WVGA\n",
                        "P 5.4in FWVGA\n",
                        "P 6.7in Foldable\n",
                        "P 7in WSVGA (Tablet)\n",
                        "P 7.4in Rollable\n",
                        "P 7.6in Foldable\n",
                        "P 8in Foldable\n",
                        "P 10.1in WXGA (Tablet)\n",
                        "P 13.5in Freeform\n"),
                mLogger.getMessages().stream()
                        .filter(s -> s.startsWith("P"))
                        .collect(Collectors.toList()));
        assertTrue(mLogger.getMessages().contains("P wearos_small_round\n"));
        assertTrue(mLogger.getMessages().contains("P Nexus 6P\n"));
        assertTrue(mLogger.getMessages().contains("P tv_1080p\n"));
        mLogger.clear();
        mCli =
                new AvdManagerCli(
                        mLogger,
                        mSdkHandler,
                        mAvdManager,
                        sdkPath.toString(),
                        avdPath.toString(),
                        null);
        mCli.run(new String[] {"list", "devices"});
        assertTrue(
                Joiner.on("")
                        .join(mLogger.getMessages())
                        .contains(
                                "P ---------\n"
                                        + "P id: 71 or \"4in WVGA (Nexus S)\"\n"
                                        + "P     Name: 4\" WVGA (Nexus S)\n"
                                        + "P     OEM : Generic\n"
                                        + "P ---------\n"
                                        + "P id: 72 or \"4.65in 720p (Galaxy Nexus)\"\n"
                                        + "P     Name: 4.65\" 720p (Galaxy Nexus)\n"
                                        + "P     OEM : Generic\n"
                                        + "P ---------"));
    }

    @Test
    public void validateResponse() {
        Path hardwareDefs = emuLibPath.resolve(SdkConstants.FN_HARDWARE_INI);
        Map<String, HardwareProperties.HardwareProperty> hwMap =
                HardwareProperties.parseHardwareDefinitions(
                        new PathFileWrapper(hardwareDefs), mLogger);
        HardwareProperties.HardwareProperty[] hwProperties = hwMap.values().toArray(
          new HardwareProperties.HardwareProperty[0]);

        for (HardwareProperties.HardwareProperty aProperty : hwProperties) {
            switch (aProperty.getName()) {
                case "booleanPropName":
                    assertEquals("Boolean 'yes' should be valid",
                               "yes", AvdManagerCli.validateResponse("yes", aProperty, mLogger));
                    assertEquals("Boolean 'Yes' should be valid",
                               "yes", AvdManagerCli.validateResponse("Yes", aProperty, mLogger));
                    assertEquals("Boolean 'no' should be valid",
                               "no", AvdManagerCli.validateResponse("no", aProperty, mLogger));
                    assertNull(
                            "Boolean 'true' should be invalid",
                            AvdManagerCli.validateResponse("true", aProperty, mLogger));
                    assertNull(
                            "Boolean 'maybe' should be invalid",
                            AvdManagerCli.validateResponse("maybe", aProperty, mLogger));
                    break;
                case "integerPropName":
                    assertEquals("Integer '123' should be valid",
                                 "123", AvdManagerCli.validateResponse("123", aProperty, mLogger));
                    assertNull(
                            "Integer '123x' should be invalid",
                            AvdManagerCli.validateResponse("123x", aProperty, mLogger));
                    break;
                case "integerEnumPropName":
                    assertEquals("Integer enum '40' should be valid",
                                 "40", AvdManagerCli.validateResponse("40", aProperty, mLogger));
                    assertNull(
                            "Integer enum '45' should be invalid",
                            AvdManagerCli.validateResponse("45", aProperty, mLogger));
                    break;
                case "stringPropName":
                    assertEquals(
                            "String 'Whatever$^*)#?!' should be valid",
                            "Whatever$^*)#?!",
                            AvdManagerCli.validateResponse("Whatever$^*)#?!", aProperty, mLogger));
                    break;
                case "stringEnumPropName":
                    assertEquals(
                            "String enum 'okString0' should be valid",
                            "okString0",
                            AvdManagerCli.validateResponse("okString0", aProperty, mLogger));
                    assertNull(
                            "String enum 'okString3' should be invalid",
                            AvdManagerCli.validateResponse("okString3", aProperty, mLogger));
                    break;
                case "stringEnumTemplatePropName":
                    assertEquals(
                            "String enum 'fixedString' should be valid",
                            "fixedString",
                            AvdManagerCli.validateResponse("fixedString", aProperty, mLogger));
                    assertEquals(
                            "String enum 'extensibleString0' should be valid",
                            "extensibleString0",
                            AvdManagerCli.validateResponse(
                                    "extensibleString0", aProperty, mLogger));
                    assertEquals(
                            "String enum 'extensibleString123' should be valid",
                            "extensibleString123",
                            AvdManagerCli.validateResponse(
                                    "extensibleString123", aProperty, mLogger));
                    assertNull(
                            "String enum 'extensibleStringPlus' should be invalid",
                            AvdManagerCli.validateResponse(
                                    "extensibleStringPlus", aProperty, mLogger));
                    assertNull(
                            "String enum '...' should be invalid",
                            AvdManagerCli.validateResponse("...", aProperty, mLogger));
                    assertNull(
                            "String enum 'fixedString3' should be invalid",
                            AvdManagerCli.validateResponse("fixedString3", aProperty, mLogger));
                    break;
                case "diskSizePropName":
                    assertEquals(
                            "Disk size '50MB' should be valid",
                            "50MB",
                            AvdManagerCli.validateResponse("50MB", aProperty, mLogger));
                    break;
                default:
                    fail("Unexpected hardware property type: " + aProperty.getName());
            }
        }
    }

    @Test
    public void packageHelp() {
        try {
            mCli.run(new String[] {"create", "avd", "--name", "testAvd", "-d", "Nexus 6P"});
            fail("Expected exception");
        } catch (Exception expected) {
            // expected
        }

        assertEquals(
                "E Package path (-k) not specified. Valid system image paths are:\n"
                        + android25GoogleApisPlayStoreSdkPath
                        + "\n"
                        + android26WearSdkPath
                        + "\n"
                        + android25GoogleApisSdkPath,
                Joiner.on("").join(mLogger.getMessages()));
        mLogger.clear();
        try {
            mCli.run(
                    new String[] {
                        "create", "avd", "--name", "testAvd", "-d", "Nexus 6P", "-k", "foo"
                    });
            fail("Expected exception");
        } catch (Exception expected) {
            // expected
        }

        assertEquals(
                "E Package path is not valid. Valid system image paths are:\n"
                        + android25GoogleApisPlayStoreSdkPath
                        + "\n"
                        + android26WearSdkPath
                        + "\n"
                        + android25GoogleApisSdkPath,
                Joiner.on("").join(mLogger.getMessages()));
    }

    @Test
    public void isSilent() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalStdout = System.out;

        try {
            // Replace stdout so that we can capture what is logged
            System.setOut(new PrintStream(outputStream));
            mCli.run(
                    new String[] {
                        "--silent",
                        "create",
                        "avd",
                        "--force",
                        "--name",
                        "testAvd",
                        "-k",
                        android25GoogleApisSdkPath,
                        "-d",
                        "Nexus 6P"
                    });
            // Make sure we actually created an AVD
            AvdInfo info = mAvdManager.getAvd("testAvd", true);
            assertNotNull("created AVD", info);

            // Note: it is possible that nothing is logged even without the "--silent" flag being
            // being specified.
            assertEquals("stdout", outputStream.toString(), "");
            assertTrue("isSilent", mCli.isSilent());

        } finally {
            System.setOut(originalStdout);
        }
    }

    @Test
    public void tagHelp() {
        try {
            mCli.run(
                    new String[] {
                        "create", "avd",
                        "--name", "testAvd",
                        "-k", android25GoogleApisSdkPath,
                        "-d", "Nexus 6P",
                        "--tag", "foo"
                    });
            fail("Expected exception");
        } catch (Exception expected) {
            // expected
        }

        assertFalse("isSilent", mCli.isSilent());
        assertEquals(
                "E Invalid --tag foo for the selected package. Valid tags are:\n" + "google_apis",
                Joiner.on("").join(mLogger.getMessages()));
    }

    private void createHardwarePropertiesFile(Path filePath) {
        InMemoryFileSystems.recordExistingFile(
                filePath,
                "name        = booleanPropName\n"
                        + "type        = boolean\n"
                        + "default     = yes\n"
                        + "abstract    = A bool\n"
                        + "description = A bool value\n"
                        + "name        = integerPropName\n"
                        + "type        = integer\n"
                        + "default     = 123\n"
                        + "abstract    = An integer\n"
                        + "description = A value that is integral\n"
                        + "name        = integerEnumPropName\n"
                        + "type        = integer\n"
                        + "enum        = 10, 20, 30, 40\n"
                        + "default     = 10\n"
                        + "abstract    = An integer enum\n"
                        + "description = One of a set of allowed integer values\n"
                        + "name        = stringPropName\n"
                        + "type        = string\n"
                        + "default     = defString\n"
                        + "abstract    = A string\n"
                        + "description = A property that is a string\n"
                        + "name        = stringEnumPropName\n"
                        + "type        = string\n"
                        + "enum        = okString0, okString1\n"
                        + "default     = okString1\n"
                        + "abstract    = A restricted string\n"
                        + "description = One of a set of allowed values\n"
                        + "name        = stringEnumTemplatePropName\n"
                        + "type        = string\n"
                        + "enum        = fixedString, anotherFixedString, extensibleString0, ...\n"
                        + "default     = fixedString\n"
                        + "abstract    = An extensible string\n"
                        + "description = One of a set of extensible allowed values\n"
                        + "name        = diskSizePropName\n"
                        + "type        = diskSize\n"
                        + "default     = 50MB\n"
                        + "abstract    = A size with units\n"
                        + "description = A string-like size with units\n");
    }
}
