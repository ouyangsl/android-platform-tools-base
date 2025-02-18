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

package com.android.sdklib.devices;

import static com.android.sdklib.internal.avd.ConfigKey.CLUSTER_HEIGHT;
import static com.android.sdklib.internal.avd.ConfigKey.CLUSTER_WIDTH;
import static com.android.sdklib.internal.avd.ConfigKey.DISPLAY_SETTINGS_FILE;
import static com.android.sdklib.internal.avd.ConfigKey.DISTANT_DISPLAY_HEIGHT;
import static com.android.sdklib.internal.avd.ConfigKey.DISTANT_DISPLAY_WIDTH;
import static com.android.sdklib.internal.avd.ConfigKey.RESIZABLE_CONFIG;
import static com.android.sdklib.internal.avd.ConfigKey.ROLL;

import static com.google.common.truth.Truth.assertThat;

import static java.util.stream.Collectors.toList;

import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.resources.Navigation;
import com.android.resources.ScreenRound;
import com.android.sdklib.SystemImageTags;
import com.android.sdklib.TempSdkManager;
import com.android.sdklib.devices.Device.Builder;
import com.android.sdklib.devices.DeviceManager.DeviceFilter;
import com.android.sdklib.devices.DeviceManager.DeviceStatus;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.testutils.NoErrorsOrWarningsLogger;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public class DeviceManagerTest {

    static final String WSVGA_HASH             = "MD5:176ce220cc833bcb6dc60ff13b82c716";
    static final String WSVGA_TRACKBALL_HASH   = "MD5:e964715d73bc8b2cc7ca2fadcb12fa41";
    static final String NEXUS_ONE_HASH         = "MD5:ef39e456bf2cab397201c2ac251f35fc";
    static final String NEXUS_ONE_PLUGGED_HASH = "MD5:474a72646a55e61e94f69bdd94758ceb";

    @Rule public final TempSdkManager sdkManager =
            new TempSdkManager("sdk_" + getClass().getSimpleName());

    private DeviceManager dm;

    @Before
    public void setUp() {
        dm = createDeviceManager();
    }

    private DeviceManager createDeviceManager() {
        NoErrorsOrWarningsLogger log = new NoErrorsOrWarningsLogger();
        AndroidSdkHandler sdkHandler = sdkManager.getSdkHandler();
        return DeviceManager.createInstance(
                sdkHandler,
                log);
    }

    /**
     * Returns a list of just the devices' display names, for unit test comparisons.
     */
    private static List<String> listDisplayNames(Collection<Device> devices) {
        return devices.stream().map(Device::getDisplayName).collect(toList());
    }

    @Test
    public final void testGetDevices_Default() {
        // no user devices defined in the test's custom .android home folder
        assertThat(dm.getDevices(DeviceFilter.USER)).isEmpty();

        // no system-images devices defined in the SDK by default
        assertThat(dm.getDevices(DeviceFilter.SYSTEM_IMAGES)).isEmpty();

        // this list comes from devices.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/devices.xml
        assertThat(listDisplayNames(dm.getDevices(DeviceFilter.DEFAULT)))
                .containsExactly(
                        "10.1\" WXGA (Tablet)",
                        "2.7\" QVGA",
                        "2.7\" QVGA slider",
                        "3.2\" HVGA slider (ADP1)",
                        "3.2\" QVGA (ADP2)",
                        "3.3\" WQVGA",
                        "3.4\" WQVGA",
                        "3.7\" FWVGA slider",
                        "3.7\" WVGA (Nexus One)",
                        "4\" WVGA (Nexus S)",
                        "4.65\" 720p (Galaxy Nexus)",
                        "4.7\" WXGA",
                        "5.1\" WVGA",
                        "5.4\" FWVGA",
                        "6.7\" Horizontal Fold-in",
                        "7\" WSVGA (Tablet)",
                        "7.4\" Rollable",
                        "7.6\" Fold-in with outer display",
                        "8\" Fold-out",
                        "Medium Phone",
                        "Medium Tablet",
                        "13.5\" Freeform",
                        "Resizable (Experimental)",
                        "Small Phone");

        assertThat(dm.getDevice("2.7in QVGA", "Generic").getDisplayName()).isEqualTo("2.7\" QVGA");

        // this list comes from the nexus.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/nexus.xml
        assertThat(listDisplayNames(dm.getDevices(DeviceFilter.VENDOR)))
                .containsExactly(
                        "Television (4K)",
                        "Television (1080p)",
                        "Television (720p)",
                        "Large Desktop",
                        "Medium Desktop",
                        "Small Desktop",
                        "Wear OS Rectangular",
                        "Wear OS Small Round",
                        "Wear OS Square",
                        "Wear OS Large Round",
                        "Automotive (1024p landscape)",
                        "Automotive (1080p landscape)",
                        "Automotive (1408p landscape) with Google Play",
                        "Automotive (1408p landscape)",
                        "Automotive Portrait",
                        "Automotive Distant Display",
                        "Automotive Distant Display with Google Play",
                        "Automotive Ultrawide",
                        "Automotive Large Portrait",
                        "Galaxy Nexus",
                        "Nexus 10",
                        "Nexus 4",
                        "Nexus 5",
                        "Nexus 5X",
                        "Nexus 6",
                        "Nexus 6P",
                        "Nexus 7",
                        "Nexus 7 (2012)",
                        "Nexus 9",
                        "Nexus One",
                        "Nexus S",
                        "Pixel C",
                        "Pixel Fold",
                        "Pixel Tablet",
                        "Pixel",
                        "Pixel XL",
                        "Pixel 2",
                        "Pixel 2 XL",
                        "Pixel 3",
                        "Pixel 3 XL",
                        "Pixel 3a",
                        "Pixel 3a XL",
                        "Pixel 4",
                        "Pixel 4 XL",
                        "Pixel 4a",
                        "Pixel 5",
                        "Pixel 6",
                        "Pixel 6 Pro",
                        "Pixel 6a",
                        "Pixel 7",
                        "Pixel 7 Pro",
                        "Pixel 7a",
                        "Pixel 8",
                        "Pixel 8 Pro",
                        "Pixel 8a",
                        "Pixel 9",
                        "Pixel 9 Pro",
                        "Pixel 9 Pro XL",
                        "Pixel 9 Pro Fold");

        assertThat(dm.getDevice("Nexus One", "Google").getDisplayName()).isEqualTo("Nexus One");

        assertThat(listDisplayNames(dm.getDevices(DeviceManager.ALL_DEVICES)))
                .containsExactly(
                        "10.1\" WXGA (Tablet)",
                        "2.7\" QVGA",
                        "2.7\" QVGA slider",
                        "3.2\" HVGA slider (ADP1)",
                        "3.2\" QVGA (ADP2)",
                        "3.3\" WQVGA",
                        "3.4\" WQVGA",
                        "3.7\" FWVGA slider",
                        "3.7\" WVGA (Nexus One)",
                        "4\" WVGA (Nexus S)",
                        "4.65\" 720p (Galaxy Nexus)",
                        "4.7\" WXGA",
                        "5.1\" WVGA",
                        "5.4\" FWVGA",
                        "6.7\" Horizontal Fold-in",
                        "7\" WSVGA (Tablet)",
                        "7.4\" Rollable",
                        "7.6\" Fold-in with outer display",
                        "8\" Fold-out",
                        "Medium Phone",
                        "Medium Tablet",
                        "13.5\" Freeform",
                        "Resizable (Experimental)",
                        "Small Phone",
                        "Television (4K)",
                        "Television (1080p)",
                        "Television (720p)",
                        "Large Desktop",
                        "Medium Desktop",
                        "Small Desktop",
                        "Wear OS Rectangular",
                        "Wear OS Small Round",
                        "Wear OS Square",
                        "Wear OS Large Round",
                        "Automotive (1024p landscape)",
                        "Automotive (1080p landscape)",
                        "Automotive (1408p landscape) with Google Play",
                        "Automotive (1408p landscape)",
                        "Automotive Portrait",
                        "Automotive Distant Display",
                        "Automotive Distant Display with Google Play",
                        "Automotive Ultrawide",
                        "Automotive Large Portrait",
                        "Galaxy Nexus",
                        "Nexus 10",
                        "Nexus 4",
                        "Nexus 5",
                        "Nexus 5X",
                        "Nexus 6",
                        "Nexus 6P",
                        "Nexus 7",
                        "Nexus 7 (2012)",
                        "Nexus 9",
                        "Nexus One",
                        "Nexus S",
                        "Pixel C",
                        "Pixel Fold",
                        "Pixel Tablet",
                        "Pixel",
                        "Pixel XL",
                        "Pixel 2",
                        "Pixel 2 XL",
                        "Pixel 3",
                        "Pixel 3 XL",
                        "Pixel 3a",
                        "Pixel 3a XL",
                        "Pixel 4",
                        "Pixel 4 XL",
                        "Pixel 4a",
                        "Pixel 5",
                        "Pixel 6",
                        "Pixel 6 Pro",
                        "Pixel 6a",
                        "Pixel 7",
                        "Pixel 7 Pro",
                        "Pixel 7a",
                        "Pixel 8",
                        "Pixel 8 Pro",
                        "Pixel 8a",
                        "Pixel 9",
                        "Pixel 9 Pro",
                        "Pixel 9 Pro XL",
                        "Pixel 9 Pro Fold");
    }

    @Test
    public final void testGetDevice() {
        // get a definition from the bundled devices.xml file
        Device d1 = dm.getDevice("7in WSVGA (Tablet)", "Generic");
        assertThat(d1.getDisplayName()).isEqualTo("7\" WSVGA (Tablet)");
        // get a definition from the bundled nexus.xml file
        Device d2 = dm.getDevice("Nexus One", "Google");
        assertThat(d2.getDisplayName()).isEqualTo("Nexus One");
    }

    @Test
    public final void testGetDevices_UserDevice() {

        Device d1 = dm.getDevice("7in WSVGA (Tablet)", "Generic");

        Builder b = new Device.Builder(d1);
        b.setId("MyCustomTablet");
        b.setName("My Custom Tablet");
        b.setManufacturer("OEM");

        Device d2 = b.build();

        dm.addUserDevice(d2);
        dm.saveUserDevices();

        assertThat(dm.getDevice("MyCustomTablet", "OEM").getDisplayName())
                .isEqualTo("My Custom Tablet");

        // create a new device manager, forcing it reload all files
        dm = null;
        DeviceManager dm2 = createDeviceManager();

        assertThat(dm2.getDevice("MyCustomTablet", "OEM").getDisplayName())
                .isEqualTo("My Custom Tablet");

        // 1 user device defined in the test's custom .android home folder
        assertThat(listDisplayNames(dm2.getDevices(DeviceFilter.USER)))
                .containsExactly("My Custom Tablet");

        // no system-images devices defined in the SDK by default
        assertThat(dm2.getDevices(DeviceFilter.SYSTEM_IMAGES)).isEmpty();

        // this list comes from devices.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/devices.xml
        assertThat(listDisplayNames(dm2.getDevices(DeviceFilter.DEFAULT)))
                .containsExactly(
                        "10.1\" WXGA (Tablet)",
                        "2.7\" QVGA",
                        "2.7\" QVGA slider",
                        "3.2\" HVGA slider (ADP1)",
                        "3.2\" QVGA (ADP2)",
                        "3.3\" WQVGA",
                        "3.4\" WQVGA",
                        "3.7\" FWVGA slider",
                        "3.7\" WVGA (Nexus One)",
                        "4\" WVGA (Nexus S)",
                        "4.65\" 720p (Galaxy Nexus)",
                        "4.7\" WXGA",
                        "5.1\" WVGA",
                        "5.4\" FWVGA",
                        "6.7\" Horizontal Fold-in",
                        "7\" WSVGA (Tablet)",
                        "7.4\" Rollable",
                        "7.6\" Fold-in with outer display",
                        "8\" Fold-out",
                        "Medium Phone",
                        "Medium Tablet",
                        "13.5\" Freeform",
                        "Resizable (Experimental)",
                        "Small Phone");

        // this list comes from the nexus.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/nexus.xml
        assertThat(listDisplayNames(dm2.getDevices(DeviceFilter.VENDOR)))
                .containsExactly(
                        "Television (4K)",
                        "Television (1080p)",
                        "Television (720p)",
                        "Large Desktop",
                        "Medium Desktop",
                        "Small Desktop",
                        "Wear OS Rectangular",
                        "Wear OS Small Round",
                        "Wear OS Square",
                        "Wear OS Large Round",
                        "Automotive (1024p landscape)",
                        "Automotive (1080p landscape)",
                        "Automotive (1408p landscape) with Google Play",
                        "Automotive (1408p landscape)",
                        "Automotive Portrait",
                        "Automotive Distant Display",
                        "Automotive Distant Display with Google Play",
                        "Automotive Ultrawide",
                        "Automotive Large Portrait",
                        "Galaxy Nexus",
                        "Nexus 10",
                        "Nexus 4",
                        "Nexus 5",
                        "Nexus 5X",
                        "Nexus 6",
                        "Nexus 6P",
                        "Nexus 7",
                        "Nexus 7 (2012)",
                        "Nexus 9",
                        "Nexus One",
                        "Nexus S",
                        "Pixel C",
                        "Pixel Fold",
                        "Pixel Tablet",
                        "Pixel",
                        "Pixel XL",
                        "Pixel 2",
                        "Pixel 2 XL",
                        "Pixel 3",
                        "Pixel 3 XL",
                        "Pixel 3a",
                        "Pixel 3a XL",
                        "Pixel 4",
                        "Pixel 4 XL",
                        "Pixel 4a",
                        "Pixel 5",
                        "Pixel 6",
                        "Pixel 6 Pro",
                        "Pixel 6a",
                        "Pixel 7",
                        "Pixel 7 Pro",
                        "Pixel 7a",
                        "Pixel 8",
                        "Pixel 8 Pro",
                        "Pixel 8a",
                        "Pixel 9",
                        "Pixel 9 Pro",
                        "Pixel 9 Pro XL",
                        "Pixel 9 Pro Fold");

        assertThat(listDisplayNames(dm2.getDevices(DeviceManager.ALL_DEVICES)))
                .containsExactly(
                        "10.1\" WXGA (Tablet)",
                        "2.7\" QVGA",
                        "2.7\" QVGA slider",
                        "3.2\" HVGA slider (ADP1)",
                        "3.2\" QVGA (ADP2)",
                        "3.3\" WQVGA",
                        "3.4\" WQVGA",
                        "3.7\" FWVGA slider",
                        "3.7\" WVGA (Nexus One)",
                        "4\" WVGA (Nexus S)",
                        "4.65\" 720p (Galaxy Nexus)",
                        "4.7\" WXGA",
                        "5.1\" WVGA",
                        "5.4\" FWVGA",
                        "6.7\" Horizontal Fold-in",
                        "7\" WSVGA (Tablet)",
                        "7.4\" Rollable",
                        "7.6\" Fold-in with outer display",
                        "8\" Fold-out",
                        "Medium Phone",
                        "Medium Tablet",
                        "13.5\" Freeform",
                        "Resizable (Experimental)",
                        "Small Phone",
                        "Television (4K)",
                        "Television (1080p)",
                        "Television (720p)",
                        "Large Desktop",
                        "Medium Desktop",
                        "Small Desktop",
                        "Wear OS Rectangular",
                        "Wear OS Small Round",
                        "Wear OS Square",
                        "Wear OS Large Round",
                        "Automotive (1024p landscape)",
                        "Automotive (1080p landscape)",
                        "Automotive (1408p landscape) with Google Play",
                        "Automotive (1408p landscape)",
                        "Automotive Portrait",
                        "Automotive Distant Display",
                        "Automotive Distant Display with Google Play",
                        "Automotive Ultrawide",
                        "Automotive Large Portrait",
                        "Galaxy Nexus",
                        "My Custom Tablet",
                        "Nexus 10",
                        "Nexus 4",
                        "Nexus 5",
                        "Nexus 5X",
                        "Nexus 6",
                        "Nexus 6P",
                        "Nexus 7",
                        "Nexus 7 (2012)",
                        "Nexus 9",
                        "Nexus One",
                        "Nexus S",
                        "Pixel C",
                        "Pixel Fold",
                        "Pixel Tablet",
                        "Pixel",
                        "Pixel XL",
                        "Pixel 2",
                        "Pixel 2 XL",
                        "Pixel 3",
                        "Pixel 3 XL",
                        "Pixel 3a",
                        "Pixel 3a XL",
                        "Pixel 4",
                        "Pixel 4 XL",
                        "Pixel 4a",
                        "Pixel 5",
                        "Pixel 6",
                        "Pixel 6 Pro",
                        "Pixel 6a",
                        "Pixel 7",
                        "Pixel 7 Pro",
                        "Pixel 7a",
                        "Pixel 8",
                        "Pixel 8 Pro",
                        "Pixel 8a",
                        "Pixel 9",
                        "Pixel 9 Pro",
                        "Pixel 9 Pro XL",
                        "Pixel 9 Pro Fold");
    }

    @Test
    public final void testGetDevices_SysImgDevice() throws Exception {

        AndroidSdkHandler handler = sdkManager.getSdkHandler();
        Path sdkPath = handler.getLocation();
        FakePackage.FakeLocalPackage p = new FakePackage.FakeLocalPackage("sample");

        // Create a system image directory with one device
        DetailsTypes.AddonDetailsType details = AndroidSdkHandler.getAddonModule()
                .createLatestFactory().createAddonDetailsType();
        details.setApiLevel(22);
        details.setVendor(SystemImageTags.DEFAULT_TAG);
        p.setTypeDetails((TypeDetails) details);
        SystemImage imageWithDevice =
                new SystemImage(
                        sdkPath.resolve("system-images/android-22/tag-1/x86"),
                        IdDisplay.create("tag-1", "tag-1"),
                        IdDisplay.create("OEM", "Tag 1 OEM"),
                        Collections.singletonList(Abi.X86.toString()),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        p);

        sdkManager.makeSystemImageFolder(imageWithDevice, "tag-1");

        // no user devices defined in the test's custom .android home folder
        assertThat(dm.getDevices(DeviceFilter.USER)).isEmpty();

        // find the system-images specific device added by makeSystemImageFolder above
        // using both the getDevices() API and the device-specific getDevice() API.
        assertThat(listDisplayNames(dm.getDevices(DeviceFilter.SYSTEM_IMAGES)))
                .containsExactly("Mock Tag 1 Device Name");

        assertThat(dm.getDevice("tag-1", "OEM").getDisplayName())
                .isEqualTo("Mock Tag 1 Device Name");

        // this list comes from devices.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/devices.xml
        assertThat(listDisplayNames(dm.getDevices(DeviceFilter.DEFAULT)))
                .containsExactly(
                        "10.1\" WXGA (Tablet)",
                        "2.7\" QVGA",
                        "2.7\" QVGA slider",
                        "3.2\" HVGA slider (ADP1)",
                        "3.2\" QVGA (ADP2)",
                        "3.3\" WQVGA",
                        "3.4\" WQVGA",
                        "3.7\" FWVGA slider",
                        "3.7\" WVGA (Nexus One)",
                        "4\" WVGA (Nexus S)",
                        "4.65\" 720p (Galaxy Nexus)",
                        "4.7\" WXGA",
                        "5.1\" WVGA",
                        "5.4\" FWVGA",
                        "6.7\" Horizontal Fold-in",
                        "7\" WSVGA (Tablet)",
                        "7.4\" Rollable",
                        "7.6\" Fold-in with outer display",
                        "8\" Fold-out",
                        "Medium Phone",
                        "Medium Tablet",
                        "13.5\" Freeform",
                        "Resizable (Experimental)",
                        "Small Phone");

        // this list comes from the nexus.xml bundled in the JAR
        // cf /sdklib/src/main/java/com/android/sdklib/devices/nexus.xml
        assertThat(listDisplayNames(dm.getDevices(DeviceFilter.VENDOR)))
                .containsExactly(
                        "Television (4K)",
                        "Television (1080p)",
                        "Television (720p)",
                        "Large Desktop",
                        "Medium Desktop",
                        "Small Desktop",
                        "Wear OS Small Round",
                        "Wear OS Rectangular",
                        "Wear OS Square",
                        "Wear OS Large Round",
                        "Automotive (1024p landscape)",
                        "Automotive (1080p landscape)",
                        "Automotive (1408p landscape) with Google Play",
                        "Automotive (1408p landscape)",
                        "Automotive Portrait",
                        "Automotive Distant Display",
                        "Automotive Distant Display with Google Play",
                        "Automotive Ultrawide",
                        "Automotive Large Portrait",
                        "Galaxy Nexus",
                        "Nexus 10",
                        "Nexus 4",
                        "Nexus 5",
                        "Nexus 5X",
                        "Nexus 6",
                        "Nexus 6P",
                        "Nexus 7",
                        "Nexus 7 (2012)",
                        "Nexus 9",
                        "Nexus One",
                        "Nexus S",
                        "Pixel C",
                        "Pixel Fold",
                        "Pixel Tablet",
                        "Pixel",
                        "Pixel XL",
                        "Pixel 2",
                        "Pixel 2 XL",
                        "Pixel 3",
                        "Pixel 3 XL",
                        "Pixel 3a",
                        "Pixel 3a XL",
                        "Pixel 4",
                        "Pixel 4 XL",
                        "Pixel 4a",
                        "Pixel 5",
                        "Pixel 6",
                        "Pixel 6 Pro",
                        "Pixel 6a",
                        "Pixel 7",
                        "Pixel 7 Pro",
                        "Pixel 7a",
                        "Pixel 8",
                        "Pixel 8 Pro",
                        "Pixel 8a",
                        "Pixel 9",
                        "Pixel 9 Pro",
                        "Pixel 9 Pro XL",
                        "Pixel 9 Pro Fold");

        assertThat(listDisplayNames(dm.getDevices(DeviceManager.ALL_DEVICES)))
                .containsExactly(
                        "10.1\" WXGA (Tablet)",
                        "2.7\" QVGA",
                        "2.7\" QVGA slider",
                        "3.2\" HVGA slider (ADP1)",
                        "3.2\" QVGA (ADP2)",
                        "3.3\" WQVGA",
                        "3.4\" WQVGA",
                        "3.7\" FWVGA slider",
                        "3.7\" WVGA (Nexus One)",
                        "4\" WVGA (Nexus S)",
                        "4.65\" 720p (Galaxy Nexus)",
                        "4.7\" WXGA",
                        "5.1\" WVGA",
                        "5.4\" FWVGA",
                        "6.7\" Horizontal Fold-in",
                        "7\" WSVGA (Tablet)",
                        "7.4\" Rollable",
                        "7.6\" Fold-in with outer display",
                        "8\" Fold-out",
                        "Medium Phone",
                        "Medium Tablet",
                        "13.5\" Freeform",
                        "Resizable (Experimental)",
                        "Small Phone",
                        "Television (4K)",
                        "Television (1080p)",
                        "Television (720p)",
                        "Large Desktop",
                        "Medium Desktop",
                        "Small Desktop",
                        "Wear OS Rectangular",
                        "Wear OS Small Round",
                        "Wear OS Square",
                        "Wear OS Large Round",
                        "Automotive (1024p landscape)",
                        "Automotive (1080p landscape)",
                        "Automotive (1408p landscape) with Google Play",
                        "Automotive (1408p landscape)",
                        "Automotive Portrait",
                        "Automotive Distant Display",
                        "Automotive Distant Display with Google Play",
                        "Automotive Ultrawide",
                        "Automotive Large Portrait",
                        "Galaxy Nexus",
                        "Mock Tag 1 Device Name",
                        "Nexus 10",
                        "Nexus 4",
                        "Nexus 5",
                        "Nexus 5X",
                        "Nexus 6",
                        "Nexus 6P",
                        "Nexus 7",
                        "Nexus 7 (2012)",
                        "Nexus 9",
                        "Nexus One",
                        "Nexus S",
                        "Pixel C",
                        "Pixel Fold",
                        "Pixel Tablet",
                        "Pixel",
                        "Pixel XL",
                        "Pixel 2",
                        "Pixel 2 XL",
                        "Pixel 3",
                        "Pixel 3 XL",
                        "Pixel 3a",
                        "Pixel 3a XL",
                        "Pixel 4",
                        "Pixel 4 XL",
                        "Pixel 4a",
                        "Pixel 5",
                        "Pixel 6",
                        "Pixel 6 Pro",
                        "Pixel 6a",
                        "Pixel 7",
                        "Pixel 7 Pro",
                        "Pixel 7a",
                        "Pixel 8",
                        "Pixel 8 Pro",
                        "Pixel 8a",
                        "Pixel 9",
                        "Pixel 9 Pro",
                        "Pixel 9 Pro XL",
                        "Pixel 9 Pro Fold");
    }

    @Test
    public final void testGetDeviceStatus() {
        // get a definition from the bundled devices.xml file
        assertThat(dm.getDeviceStatus("7in WSVGA (Tablet)", "Generic"))
                .isEqualTo(DeviceStatus.EXISTS);

        // get a definition from the bundled oem file
        assertThat(dm.getDeviceStatus("Nexus One", "Google")).isEqualTo(DeviceStatus.EXISTS);

        // try a device that does not exist
        assertThat(dm.getDeviceStatus("My Device", "Custom OEM")).isEqualTo(DeviceStatus.MISSING);
    }

    @Test
    public final void testGetHardwareProperties() {
        final Device pixelDevice = dm.getDevice("pixel", "Google");

        Map<String, String> devProperties = DeviceManager.getHardwareProperties(pixelDevice);
        assertThat(devProperties.get("hw.lcd.density")).isEqualTo("420");
        assertThat(devProperties.get("hw.lcd.width")).isEqualTo("1080");
        assertThat(devProperties.get("hw.ramSize")).isEqualTo("4096"); // In MB, without units
    }

    @Test
    public void testGetFreeformHardwareProperties() {
        Device device = dm.getDevice("13.5in Freeform", "Generic");
        String settingsFile =
                DeviceManager.getHardwareProperties(device).get(DISPLAY_SETTINGS_FILE);
        assertThat(settingsFile).isEqualTo("freeform");
    }

    @Test
    public void testGetRollableHardwareProperties() {
        Device device = dm.getDevice("7.4in Rollable", "Generic");
        assertThat(DeviceManager.getHardwareProperties(device).get(ROLL)).isEqualTo("yes");
    }

    @Test
    public void testResizableHardwareProperties() {
        Device device = dm.getDevice("resizable", "Generic");
        assertThat(DeviceManager.getHardwareProperties(device).get(RESIZABLE_CONFIG)).isNotEmpty();
    }

    @Test
    public void testAutomotiveDeviceProperties() {
        List<Device> automotiveDevices =
                dm.getDevices(DeviceManager.ALL_DEVICES).stream()
                        .filter(Device::isAutomotive)
                        .collect(toList());
        assertThat(automotiveDevices).isNotEmpty();
        for (Device device : automotiveDevices) {
            assertThat(DeviceManager.getHardwareProperties(device)).containsKey(CLUSTER_HEIGHT);
            assertThat(DeviceManager.getHardwareProperties(device)).containsKey(CLUSTER_WIDTH);
        }
    }

    @Test
    public void testAutomotiveDistantDeviceProperties() {
        List<Device> automotiveDistantDisplayDevices =
                dm.getDevices(DeviceManager.ALL_DEVICES).stream()
                        .filter(Device::isAutomotiveDistantDisplay)
                        .collect(toList());
        assertThat(automotiveDistantDisplayDevices).isNotEmpty();
        for (Device device : automotiveDistantDisplayDevices) {
            assertThat(DeviceManager.getHardwareProperties(device))
                    .containsKey(DISTANT_DISPLAY_HEIGHT);
            assertThat(DeviceManager.getHardwareProperties(device))
                    .containsKey(DISTANT_DISPLAY_WIDTH);
        }
    }

    @Test
    public final void testHasHardwarePropHashChanged_Generic() {
        final Device d1 = dm.getDevice("7in WSVGA (Tablet)", "Generic");

        assertThat(DeviceManager.hasHardwarePropHashChanged(d1, "invalid"))
                .isEqualTo(WSVGA_HASH);

        assertThat(DeviceManager.hasHardwarePropHashChanged(
                d1, WSVGA_HASH))
                .isNull();

        // change the device hardware props, this should change the hash
        d1.getDefaultHardware().setNav(Navigation.TRACKBALL);

        assertThat(DeviceManager.hasHardwarePropHashChanged(
                d1, WSVGA_HASH))
                .isEqualTo(WSVGA_TRACKBALL_HASH);

        // change the property back, should revert its hash to the previous one
        d1.getDefaultHardware().setNav(Navigation.NONAV);

        assertThat(DeviceManager.hasHardwarePropHashChanged(
                d1, WSVGA_HASH))
                .isNull();
    }

    @Test
    public final void testHasHardwarePropHashChanged_Oem() {
        final Device d2 = dm.getDevice("Nexus One", "Google");

        assertThat(DeviceManager.hasHardwarePropHashChanged(d2, "invalid"))
                .isEqualTo(NEXUS_ONE_HASH);

        assertThat(DeviceManager.hasHardwarePropHashChanged(
                d2, NEXUS_ONE_HASH))
                .isNull();

        // change the device hardware props, this should change the hash
        d2.getDefaultHardware().setChargeType(PowerType.PLUGGEDIN);

        assertThat(DeviceManager.hasHardwarePropHashChanged(
                d2, NEXUS_ONE_HASH))
                .isEqualTo(NEXUS_ONE_PLUGGED_HASH);

        // change the property back, should revert its hash to the previous one
        d2.getDefaultHardware().setChargeType(PowerType.BATTERY);

        assertThat(DeviceManager.hasHardwarePropHashChanged(
                d2, NEXUS_ONE_HASH))
                .isNull();
    }

    @Test
    public final void testDeviceOverrides() throws Exception {
        AndroidSdkHandler handler = sdkManager.getSdkHandler();
        Path sdkPath = handler.getLocation();
        FakePackage.FakeLocalPackage p =
                new FakePackage.FakeLocalPackage(
                        "sample", sdkManager.getSdkHandler().getLocation().resolve("sample"));

        // Create a local DeviceManager, get the number of devices, and verify one device
        DeviceManager localDeviceManager = createDeviceManager();
        int count = localDeviceManager.getDevices(EnumSet.allOf(DeviceFilter.class)).size();
        Device localDevice = localDeviceManager.getDevice("wearos_small_round", "Google");
        assertThat(localDevice.getDisplayName()).isEqualTo("Wear OS Small Round");

        // Create two system image directories with different definitions of the
        // device that we just checked. The version in android-25 should override
        // the other two versions.
        DetailsTypes.AddonDetailsType details22 = AndroidSdkHandler.getAddonModule()
                .createLatestFactory().createAddonDetailsType();
        details22.setApiLevel(22);
        details22.setVendor(SystemImageTags.DEFAULT_TAG);
        p.setTypeDetails((TypeDetails) details22);
        SystemImage imageWithDevice22 =
                new SystemImage(
                        sdkPath.resolve("system-images/android-22/android-wear/x86"),
                        IdDisplay.create("android-wear", "android-wear"),
                        IdDisplay.create("Google", "Google"),
                        Collections.singletonList(Abi.X86.toString()),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        p);
        sdkManager.makeSystemImageFolder(imageWithDevice22, "wearos_small_round");

        DetailsTypes.AddonDetailsType details25 = AndroidSdkHandler.getAddonModule()
          .createLatestFactory().createAddonDetailsType();
        details25.setApiLevel(25);
        details25.setVendor(SystemImageTags.DEFAULT_TAG);
        p.setTypeDetails((TypeDetails) details25);
        SystemImage imageWithDevice25 =
                new SystemImage(
                        sdkPath.resolve("system-images/android-25/android-wear/x86"),
                        IdDisplay.create("android-wear", "android-wear"),
                        IdDisplay.create("Google", "Google"),
                        Collections.singletonList(Abi.ARMEABI.toString()),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        p);
        sdkManager.makeSystemImageFolder(imageWithDevice25, "wearos_small_round");

        // Re-create the local DeviceManager using the new directory,
        // fetch the device, and verify that it is the right one.
        FakeProgressIndicator progress = new FakeProgressIndicator();
        sdkManager.getSdkHandler().getSdkManager(progress).markLocalCacheInvalid();
        localDeviceManager = createDeviceManager();

        localDevice = localDeviceManager.getDevice("wearos_small_round", "Google");
        // (The "Android wear" part comes from the tag "android-wear")
        assertThat(localDevice.getDisplayName()).isEqualTo("Mock Android wear Device Name");
        assertThat(localDevice.getDefaultState().getHardware().getCpu())
                .isEqualTo(Abi.ARMEABI.toString());

        // Change the name of that device and add it to our local DeviceManager again
        Device dmDevice = dm.getDevice("wearos_small_round", "Google");
        Builder b = new Device.Builder(dmDevice);
        b.setName("Custom");
        localDeviceManager.addUserDevice(b.build());
        localDeviceManager.saveUserDevices();

        // Fetch the device from our local DeviceManager and verify
        // that it has the updated name
        localDevice = localDeviceManager.getDevice("wearos_small_round", "Google");
        assertThat(localDevice.getDisplayName()).isEqualTo("Custom");

        // Verify that the total number of devices is unchanged
        assertThat(localDeviceManager.getDevices(EnumSet.allOf(DeviceFilter.class)).size()).isEqualTo(count);
    }

    @Test
    public final void testWriteUserDevice() {
        Device testDeviceBefore = dm.getDevice("Test Round User Wear Device", "User");
        assertThat(testDeviceBefore).isNull();

        Device squareDevice = dm.getDevice("wearos_square", "Google");
        String squareName = squareDevice.getDisplayName();
        assertThat(squareName).isEqualTo("Wear OS Square");
        assertThat(squareDevice.isScreenRound()).isFalse();
        assertThat(squareDevice.getBootProps().get(DeviceParser.ROUND_BOOT_PROP)).isNull();

        {
            Device.Builder devBuilder = new Device.Builder(squareDevice);
            devBuilder.setId("test_round_dev");
            devBuilder.setName("Test Round User Wear Device");
            devBuilder.setManufacturer("User");

            State stateCopy = squareDevice.getDefaultState().deepCopy();
            stateCopy.setName("Test State");
            stateCopy.setDefaultState(false);
            stateCopy.getHardware().getScreen().setScreenRound(ScreenRound.ROUND);
            assertThat(stateCopy.getHardware().getChargeType()).isEqualTo(PowerType.BATTERY);
            stateCopy.getHardware().setChargeType(PowerType.PLUGGEDIN);
            devBuilder.addState(stateCopy);

            Device roundDevice = devBuilder.build();
            assertThat(roundDevice).isNotNull();
            assertThat(roundDevice.getBootProps().get(DeviceParser.ROUND_BOOT_PROP))
                    .isEqualTo("true");

            dm.addUserDevice(roundDevice);
        }

        Device testDeviceMid = dm.getDevice("test_round_dev", "User");
        assertThat(testDeviceMid).isNotNull();

        // Write the user-defined device definitions to devices.xml
        dm.saveUserDevices();
        dm.removeUserDevice(testDeviceMid);

        // Create a new DeviceManager. It will read the newly-written
        // devices.xml file, so we can check the contents.
        DeviceManager newDM = createDeviceManager();

        Device testDeviceAfter = newDM.getDevice("test_round_dev", "User");
        assertThat(testDeviceAfter).isNotNull();
        String afterName = testDeviceAfter.getDisplayName();
        assertThat(afterName).isEqualTo("Test Round User Wear Device");
        assertThat(testDeviceAfter.isScreenRound()).isTrue();
        assertThat(testDeviceAfter.getBootProps().get(DeviceParser.ROUND_BOOT_PROP))
                .isEqualTo("true");
        assertThat(testDeviceAfter.getState("Test State").getHardware().getChargeType())
                .isEqualTo(PowerType.PLUGGEDIN);
    }
}
