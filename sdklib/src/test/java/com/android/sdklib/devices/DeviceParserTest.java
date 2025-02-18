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

package com.android.sdklib.devices;

import static com.google.common.truth.Truth.assertThat;

import com.android.dvlib.DeviceSchemaTest;
import com.android.resources.Density;
import com.android.resources.Keyboard;
import com.android.resources.KeyboardState;
import com.android.resources.Navigation;
import com.android.resources.NavigationState;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenRatio;
import com.android.resources.ScreenRound;
import com.android.resources.ScreenSize;
import com.android.resources.TouchScreen;
import com.android.sdklib.devices.Storage.Unit;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.google.common.io.CharStreams;

import junit.framework.TestCase;

import org.xml.sax.SAXParseException;

import java.awt.Dimension;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DeviceParserTest extends TestCase {

    public void testValidDevicesMinimal() throws Exception {
        InputStream stream = DeviceSchemaTest.class.getResourceAsStream("devices_minimal.xml");
        try {
            Table<String, String, Device> devices = DeviceParser.parse(stream);
            assertEquals("Parsing devices_minimal.xml produces the wrong number of devices",
                    1, devices.size());

            Device device = devices.get("Galaxy Nexus", "Samsung");
            assertEquals("Galaxy Nexus", device.getDisplayName());
            assertEquals("Samsung", device.getManufacturer());

            // Test Meta information
            Meta meta = device.getMeta();
            assertFalse(meta.hasIconSixtyFour());
            assertFalse(meta.hasIconSixteen());
            assertFalse(meta.hasFrame());

            // Test Hardware information
            Hardware hw = device.getDefaultHardware();
            Screen screen = hw.getScreen();
            assertEquals(screen.getSize(), ScreenSize.NORMAL);
            assertEquals(4.65, screen.getDiagonalLength());
            assertEquals(Density.XHIGH, screen.getPixelDensity());
            assertEquals(ScreenRatio.LONG, screen.getRatio());
            assertEquals(720, screen.getXDimension());
            assertEquals(1280, screen.getYDimension());
            assertEquals(316.0, screen.getXdpi());
            assertEquals(316.0, screen.getYdpi());
            assertEquals(Multitouch.JAZZ_HANDS, screen.getMultitouch());
            assertEquals(TouchScreen.FINGER, screen.getMechanism());
            assertEquals(ScreenType.CAPACITIVE, screen.getScreenType());
            Set<Network> networks = hw.getNetworking();
            assertTrue(networks.contains(Network.BLUETOOTH));
            assertTrue(networks.contains(Network.WIFI));
            assertTrue(networks.contains(Network.NFC));
            Set<Sensor> sensors = hw.getSensors();
            assertTrue(sensors.contains(Sensor.ACCELEROMETER));
            assertTrue(sensors.contains(Sensor.BAROMETER));
            assertTrue(sensors.contains(Sensor.GYROSCOPE));
            assertTrue(sensors.contains(Sensor.COMPASS));
            assertTrue(sensors.contains(Sensor.GPS));
            assertTrue(sensors.contains(Sensor.PROXIMITY_SENSOR));
            assertTrue(hw.hasMic());
            assertEquals(2, hw.getCameras().size());
            Camera c = hw.getCamera(CameraLocation.FRONT);
            assertTrue(c != null);
            assert c != null;
            assertEquals(c.getLocation(), CameraLocation.FRONT);
            assertFalse(c.hasFlash());
            assertTrue(c.hasAutofocus());
            c = hw.getCamera(CameraLocation.BACK);
            assertTrue(c != null);
            assert c != null;
            assertEquals(c.getLocation(), CameraLocation.BACK);
            assertTrue(c.hasFlash());
            assertTrue(c.hasAutofocus());
            assertEquals(Keyboard.NOKEY, hw.getKeyboard());
            assertEquals(Navigation.NONAV, hw.getNav());
            assertEquals(new Storage(1, Unit.GiB), hw.getRam());
            assertEquals(ButtonType.SOFT, hw.getButtonType());
            List<Storage> storage = hw.getInternalStorage();
            assertEquals(1, storage.size());
            assertEquals(new Storage(16, Unit.GiB), storage.get(0));
            storage = hw.getRemovableStorage();
            assertEquals(0, storage.size());
            assertTrue(hw.hasSdCard());
            assertEquals("OMAP 4460", hw.getCpu());
            assertEquals("PowerVR SGX540", hw.getGpu());
            List<Abi> abis = hw.getSupportedAbis();
            assertEquals(2, abis.size());
            List<Abi> translatedAbis = hw.getTranslatedAbis();
            assertEquals(0, translatedAbis.size());
            assertTrue(abis.contains(Abi.ARMEABI));
            assertTrue(abis.contains(Abi.ARMEABI_V7A));
            assertEquals(0, hw.getSupportedUiModes().size());
            assertEquals(PowerType.BATTERY, hw.getChargeType());

            // Test Software
            assertEquals(1, device.getAllSoftware().size());
            Software sw = device.getSoftware(15);
            assertEquals(15, sw.getMaxSdkLevel());
            assertEquals(15, sw.getMinSdkLevel());
            assertTrue(sw.hasLiveWallpaperSupport());
            assertEquals(12, sw.getBluetoothProfiles().size());
            assertTrue(sw.getBluetoothProfiles().contains(BluetoothProfile.A2DP));
            assertEquals("2.0", sw.getGlVersion());
            assertEquals(29, sw.getGlExtensions().size());
            assertTrue(sw.getGlExtensions().contains("GL_OES_depth24"));

            // Test States
            assertEquals(2, device.getAllStates().size());
            State s = device.getDefaultState();
            assertEquals("Portrait", s.getName());
            assertTrue(s.isDefaultState());
            assertEquals("The phone in portrait view", s.getDescription());
            assertEquals(ScreenOrientation.PORTRAIT, s.getOrientation());
            assertEquals(KeyboardState.SOFT, s.getKeyState());
            assertEquals(NavigationState.HIDDEN, s.getNavState());
            s = device.getState("Landscape");
            assertEquals("Landscape", s.getName());
            assertFalse(s.isDefaultState());
            assertEquals(ScreenOrientation.LANDSCAPE, s.getOrientation());

            // Test tag-id
            assertEquals("tag-id", device.getTagId());

            // Test boot-properties
            assertEquals("{boot.prop.property=boot.prop.value}", device.getBootProps().toString());
        } finally {
            stream.close();
        }
    }

    public void testValidDevicesFull_v1() throws Exception {
        InputStream stream = DeviceSchemaTest.class.getResourceAsStream("devices.xml");
        try {
            Table<String, String, Device> devices = DeviceParser.parse(stream);
            assertEquals("Parsing devices.xml produces the wrong number of devices",
                    4, devices.size());

            Device device0 = devices.get("galaxy_nexus", "Samsung");
            assertEquals(null, device0.getTagId());
            assertEquals("{}", device0.getBootProps().toString());
            assertEquals("OMAP 4460", device0.getDefaultHardware().getCpu());
            assertEquals(
                    "[armeabi, armeabi-v7a]",
                    device0.getDefaultHardware().getSupportedAbis().toString());

            Device device1 = devices.get("Droid", "Motorola");
            assertEquals(null, device1.getTagId());
            assertEquals("{}", device1.getBootProps().toString());
            assertEquals("OMAP 3430", device1.getDefaultHardware().getCpu());
            assertEquals(
                    "[armeabi, armeabi-v7a]",
                    device1.getDefaultHardware().getSupportedAbis().toString());

            Device device2 = devices.get("Nexus 5", "Google");
            assertEquals("tag-1", device2.getTagId());
            assertEquals("{ro-myservice-port=1234, " +
                          "ro.RAM.Size=1024 MiB, " +
                          "ro.build.display.id=sdk-eng 4.3 JB_MR2 774058 test-keys}",
                         device2.getBootProps().toString());
            assertEquals("Snapdragon 800 (MSM8974)", device2.getDefaultHardware().getCpu());
            assertEquals(
                    "[armeabi-v7a, armeabi]",
                    device2.getDefaultHardware().getSupportedAbis().toString());
            assertEquals(device2.getChinSize(), 0);
            assertFalse(device2.isScreenRound());

            Device device3 = devices.get("wear_round_chin", "Google");
            assertEquals("android-wear", device3.getTagId());
            assertEquals(device3.getDefaultHardware().getScreen().getChin(), 30);
            assertEquals(device3.getChinSize(), 30);
            assertTrue(device3.isScreenRound());
            assertEquals(device3.getDefaultHardware().getScreen().getScreenRound(), ScreenRound.ROUND);
        } finally {
            stream.close();
        }
    }

    public void testValidDevicesFull_v2() throws Exception {
        InputStream stream = DeviceSchemaTest.class.getResourceAsStream("devices_v2.xml");
        try {
            Table<String, String, Device> devices = DeviceParser.parse(stream);
            assertEquals("Parsing devices.xml produces the wrong number of devices",
                    4, devices.size());

            Device device0 = devices.get("galaxy_64", "Gnusmas");
            assertEquals(null, device0.getTagId());
            assertEquals("{}", device0.getBootProps().toString());
            assertEquals("arm64", device0.getDefaultHardware().getCpu());
            assertEquals("[arm64-v8a]", device0.getDefaultHardware().getSupportedAbis().toString());

            Device device1 = devices.get("Droid X86", "Letni");
            assertEquals("tag-1", device1.getTagId());
            assertEquals("{ro-myservice-port=1234, " +
                          "ro.RAM.Size=1024 MiB, " +
                          "ro.build.display.id=sdk-eng 4.3 JB_MR2 774058 test-keys}",
                         device1.getBootProps().toString());
            assertEquals("Intel Atom 64", device1.getDefaultHardware().getCpu());
            assertEquals("[x86_64]", device1.getDefaultHardware().getSupportedAbis().toString());

            Device device2 = devices.get("Mips 64", "Mips");
            assertEquals("tag-2", device2.getTagId());
            assertEquals("{ro-myservice-port=1234, " +
                          "ro.RAM.Size=1024 MiB, " +
                          "ro.build.display.id=sdk-eng 4.3 JB_MR2 774058 test-keys}",
                         device2.getBootProps().toString());
            assertEquals("MIPS32+64", device2.getDefaultHardware().getCpu());
            assertEquals(
                    "[mips, mips64]", device2.getDefaultHardware().getSupportedAbis().toString());

            assertEquals(device2.getChinSize(), 0);
            assertFalse(device2.isScreenRound());

            Device device3 = devices.get("wear_round_chin", "Google");
            assertEquals("android-wear", device3.getTagId());
            assertEquals(device3.getDefaultHardware().getScreen().getChin(), 30);
            assertEquals(device3.getChinSize(), 30);
            assertTrue(device3.isScreenRound());
            assertEquals(device3.getDefaultHardware().getScreen().getScreenRound(), ScreenRound.ROUND);
        } finally {
            stream.close();
        }
    }

    public void testValidDevicesFull_v4() throws Exception {
        InputStream stream = DeviceSchemaTest.class.getResourceAsStream("devices_v4.xml");
        try {
            Table<String, String, Device> devices = DeviceParser.parse(stream);
            assertEquals("Parsing devices.xml produces the wrong number of devices",
                         5, devices.size());

            Device device0 = devices.get("foldable", "Phoney");
            assertEquals(null, device0.getTagId());
            assertEquals("{}", device0.getBootProps().toString());
            assertEquals("arm64", device0.getDefaultHardware().getCpu());
            assertEquals("[arm64-v8a]", device0.getDefaultHardware().getSupportedAbis().toString());
            assertEquals(300, device0.getDefaultHardware().getScreen().getFoldedXOffset());
            assertEquals(600, device0.getDefaultHardware().getScreen().getFoldedYOffset());
            assertEquals(640, device0.getDefaultHardware().getScreen().getFoldedWidth());
            assertEquals(720, device0.getDefaultHardware().getScreen().getFoldedHeight());

            Device device1 = devices.get("Droid X86", "Letni");
            assertEquals("tag-1", device1.getTagId());
            assertEquals("{ro-myservice-port=1234, " +
                         "ro.RAM.Size=1024 MiB, " +
                         "ro.build.display.id=sdk-eng 4.3 JB_MR2 774058 test-keys}",
                         device1.getBootProps().toString());
            assertEquals("Intel Atom 64", device1.getDefaultHardware().getCpu());
            assertEquals("[x86_64]", device1.getDefaultHardware().getSupportedAbis().toString());

            Device device2 = devices.get("Mips 64", "Mips");
            assertEquals("tag-2", device2.getTagId());
            assertEquals("{ro-myservice-port=1234, " +
                         "ro.RAM.Size=1024 MiB, " +
                         "ro.build.display.id=sdk-eng 4.3 JB_MR2 774058 test-keys}",
                         device2.getBootProps().toString());
            assertEquals("MIPS32+64", device2.getDefaultHardware().getCpu());
            assertEquals(
                    "[mips, mips64]", device2.getDefaultHardware().getSupportedAbis().toString());

            assertEquals(device2.getChinSize(), 0);
            assertFalse(device2.isScreenRound());

            Device device3 = devices.get("wear_round_chin", "Google");
            assertEquals("android-wear", device3.getTagId());
            assertEquals(device3.getDefaultHardware().getScreen().getChin(), 30);
            assertEquals(device3.getChinSize(), 30);
            assertTrue(device3.isScreenRound());
            assertEquals(device3.getDefaultHardware().getScreen().getScreenRound(), ScreenRound.ROUND);
        } finally {
            stream.close();
        }
    }

    public void testValidDevicesFull_v5() throws Exception {
        InputStream stream = DeviceSchemaTest.class.getResourceAsStream("devices_v5.xml");
        try {
            Table<String, String, Device> devices = DeviceParser.parse(stream);
            assertEquals(
                    "Parsing devices.xml produces the wrong number of devices", 2, devices.size());

            Device device0 = devices.get("automotive_1024p_landscape", "Google");
            assertEquals(null, device0.getTagId());
            assertEquals("{}", device0.getBootProps().toString());
            assertEquals("Generic CPU", device0.getDefaultHardware().getCpu());
            assertEquals(
                    "[armeabi-v7a, x86]",
                    device0.getDefaultHardware().getSupportedAbis().toString());

            assertTrue(!device0.getDefaultHardware().hasSdCard());

            Device device1 = devices.get("automotive_1080p_landscape", "Google");
            assertEquals("android-automotive", device1.getTagId());
            assertEquals(Collections.emptyMap(), device1.getBootProps());
            assertEquals("Generic CPU", device1.getDefaultHardware().getCpu());
            assertEquals(
                    Lists.newArrayList(Abi.ARM64_V8A, Abi.X86_64),
                    device1.getDefaultHardware().getSupportedAbis());

            assertTrue(!device1.getDefaultHardware().hasSdCard());
        } finally {
            stream.close();
        }
    }

    public void testDevicesV8Abis() throws Exception {
        try (InputStream stream = DeviceSchemaTest.class.getResourceAsStream("devices_v8.xml")) {
            Table<String, String, Device> devices = DeviceParser.parse(stream);
            assertEquals(
                    "Parsing devices.xml produces the wrong number of devices", 2, devices.size());

            Device device0 = devices.get("no_abis", "Generic");
            assertThat(device0.getDefaultHardware().getSupportedAbis()).isEmpty();

            Device device1 = devices.get("various_abis", "Generic");
            assertThat(device1.getDefaultHardware().getSupportedAbis())
                    .containsExactly(Abi.ARM64_V8A, Abi.ARMEABI_V7A);
            assertThat(device1.getDefaultHardware().getTranslatedAbis())
                    .containsExactly(Abi.RISCV64);
        }
    }

    public void testApiRange() throws Exception {
        Map<String, String> replacements = new HashMap<String, String>();
        replacements.put("api-level", "1-");
        InputStream stream = DeviceSchemaTest.getReplacedStream(replacements);
        try {
            Table<String, String, Device> devices = DeviceParser.parse(stream);
            assertEquals(1, devices.size());
            Device device = devices.get("Galaxy Nexus", "Samsung");
            assertTrue(device.getSoftware(1) != null);
            assertTrue(device.getSoftware(2) != null);
            assertTrue(device.getSoftware(0) == null);
            replacements.put("api-level", "-2");
            stream = DeviceSchemaTest.getReplacedStream(replacements);
            device = DeviceParser.parse(stream).get("Galaxy Nexus", "Samsung");
            assertTrue(device.getSoftware(2) != null);
            assertTrue(device.getSoftware(3) == null);
            replacements.put("api-level", "1-2");
            stream = DeviceSchemaTest.getReplacedStream(replacements);
            device = DeviceParser.parse(stream).get("Galaxy Nexus", "Samsung");
            assertTrue(device.getSoftware(0) == null);
            assertTrue(device.getSoftware(1) != null);
            assertTrue(device.getSoftware(2) != null);
            assertTrue(device.getSoftware(3) == null);
            replacements.put("api-level", "-");
            stream = DeviceSchemaTest.getReplacedStream(replacements);
            device = DeviceParser.parse(stream).get("Galaxy Nexus", "Samsung");
            assertTrue(device.getSoftware(0) != null);
            assertTrue(device.getSoftware(15) != null);
        } finally {
            stream.close();
        }
    }

    public void testBadNetworking() throws Exception {
        Map<String, String> replacements = new HashMap<String, String>();
        replacements.put("networking", "NFD");
        InputStream stream = DeviceSchemaTest.getReplacedStream(replacements);
        try {
            Table<String, String, Device> devices = DeviceParser.parse(stream);
            assertEquals(1, devices.size());
            assertEquals(
                    0,
                    devices.get("Galaxy Nexus", "Samsung")
                            .getDefaultHardware()
                            .getNetworking()
                            .size());
            fail();
        } catch (SAXParseException e) {
            assertTrue(e.getMessage().startsWith("cvc-enumeration-valid: Value 'NFD'"));
        } finally {
            stream.close();
        }
    }

    public void testScreenDimension() throws Exception {
        InputStream stream = DeviceSchemaTest.class.getResourceAsStream(
                "devices_minimal.xml");
        try {
            Table<String, String, Device> devices = DeviceParser.parse(stream);
            assertEquals("Parsing devices_minimal.xml produces the wrong number of devices", 1,
                    devices.size());

            Device device = devices.get("Galaxy Nexus", "Samsung");
            assertEquals("Galaxy Nexus", device.getDisplayName());

            assertEquals(new Dimension(1280, 720), device.getScreenSize(ScreenOrientation.LANDSCAPE));
            assertEquals(new Dimension(720, 1280), device.getScreenSize(ScreenOrientation.PORTRAIT));
        } finally {
            stream.close();
        }
    }

    public void testDPI() throws Exception {
        Map<String, Density> dpis =
                ImmutableMap.of(
                        "l", Density.LOW,
                        "m", Density.MEDIUM,
                        "tv", Density.TV,
                        "h", Density.HIGH,
                        "xh", Density.XHIGH,
                        "xxh", Density.XXHIGH,
                        "xxxh", Density.XXXHIGH,
                        "123", Density.create(123));
        try (InputStream stream =
                DeviceSchemaTest.class.getResourceAsStream("devices_dpitest_template.xml")) {
            String template = CharStreams.toString(new InputStreamReader(stream));
            for (Map.Entry<String, Density> entry : dpis.entrySet()) {
                Table<String, String, Device> devices =
                        DeviceParser.parse(
                                new ByteArrayInputStream(
                                        String.format(template, entry.getKey()).getBytes()));
                assertEquals(devices.size(), 1);
                assertEquals(
                        devices.get("testDevice-" + entry.getKey(), "Google")
                                .getDefaultState()
                                .getHardware()
                                .getScreen()
                                .getPixelDensity(),
                        entry.getValue());
            }
            try {
                DeviceParser.parse(
                        new ByteArrayInputStream(String.format(template, "error").getBytes()));
                fail("Excepted exception for invalid dpi");
            } catch (Exception expected) {
            }
            try {
                DeviceParser.parse(
                        new ByteArrayInputStream(String.format(template, "1").getBytes()));
                fail("Excepted exception for invalid dpi");
            } catch (Exception expected) {
            }
        }
    }
}
