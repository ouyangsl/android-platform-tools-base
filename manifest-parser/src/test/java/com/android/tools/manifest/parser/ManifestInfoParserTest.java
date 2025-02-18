/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.manifest.parser;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.testutils.TestResources;
import com.android.tools.manifest.parser.components.ManifestActivityInfo;
import com.android.tools.manifest.parser.components.ManifestServiceInfo;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class ManifestInfoParserTest {

    @Test
    public void activityParsing() throws IOException {
        URL url = TestResources.getFile("/activityParsing/manifestWithActivity.bxml")
                .toURI()
                .toURL();
        Assert.assertNotNull(url);
        try (InputStream input = url.openStream()) {
            ManifestInfo manifest = ManifestInfo.parseBinaryFromStream(input);

            Assert.assertEquals("Application id",
                                "com.example.activityapplication",
                                manifest.getApplicationId());

            List<ManifestActivityInfo> activities = manifest.activities();
            Assert.assertEquals("Num activities", 5, activities.size());

            ManifestActivityInfo activity = getActivityByQName(
                    "com.example.activityapplication.MainActivity",
                    activities);
            Assert.assertTrue("Intent Action 0", activity.hasAction("android.intent.action.MAIN"));
            Assert.assertTrue("Intent Catego 0",
                              activity.hasCategory("android.intent.category.LAUNCHER"));
            Assert.assertEquals("Exported 0", true, activity.getExported());
            Assert.assertTrue("Enabled 0", activity.isEnabled());

            // Activity alias
            activity = getActivityByQName("com.example.activityapplication.foo", activities);
            Assert.assertNotEquals("Aliased Activity", null, activity);

            activity = getActivityByQName("com.example.activityapplication.MissingActivity",
                                          activities);
            Assert.assertFalse("Intent Action 1", activity.hasAction("android.intent.action.MAIN"));
            Assert.assertFalse("Intent Catego 1",
                               activity.hasCategory("android.intent.category.LAUNCHER"));
            Assert.assertEquals("Exported 1", false, activity.getExported());
            Assert.assertFalse("Enabled 1", activity.isEnabled());

            activity = getActivityByQName("com.example.activityapplication.MissingActivity2",
                                          activities);
            Assert.assertTrue("Intent Action 2.0",
                              activity.hasAction("android.intent.action.MAIN"));
            Assert.assertTrue("Intent Catego 2.0",
                              activity.hasCategory("android.intent.category.LAUNCHER"));
            Assert.assertTrue("Intent Action 2.1", activity.hasAction("foo"));
            Assert.assertTrue("Intent Catego 2.1", activity.hasCategory("bar"));
            Assert.assertEquals("Exported 2", true, activity.getExported());
            Assert.assertTrue("Enabled 2", activity.isEnabled());

            // Test multiAction, multiCategory activity
            activity = getActivityByQName("com.example.activityapplication.MultiActivity",
                                          activities);
            Assert.assertTrue("Multi Action 1", activity.hasAction("android.intent.action.MAIN"));
            Assert.assertTrue("Multi Action 2", activity.hasAction("android.intent.action.MAIN2"));
            Assert.assertTrue("Multi Catego 1",
                              activity.hasCategory("android.intent.category.LAUNCHER"));
            Assert.assertTrue("Multi Catego 2",
                              activity.hasCategory("android.intent.category.LAUNCHER2"));
        }
    }

    @Test
    public void testServiceParsing() throws IOException {
        URL url = TestResources.getFile("/serviceParsing/AndroidManifest.bxml")
                .toURI()
                .toURL();
        Assert.assertNotNull(url);
        try (InputStream input = url.openStream()) {
            ManifestInfo manifest = ManifestInfo.parseBinaryFromStream(input);
            Assert.assertEquals(2, manifest.services().size());

            ManifestServiceInfo service = getServiceByQName("com.example.myapplication.MyService",
                                                            manifest.services());
            Assert.assertTrue(service.isolatedProcess);
            Assert.assertTrue(service.hasAction("androidx.wear.tiles.action.BIND_TILE_PROVIDER"));
            Assert.assertTrue(service.hasPermission(
                    "com.google.android.wearable.permission.BIND_TILE_PROVIDER"));

            ManifestServiceInfo complication = getServiceByQName(
                    "com.example.myapplication.MyComplication",
                    manifest.services());
            Assert.assertFalse(complication.isolatedProcess);
            Assert.assertTrue(complication.hasAction(
                    "android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"));
        }
    }

    @Test
    public void testReceiverParsing() throws IOException {
        URL url = TestResources.getFile("/receiverParsing/AndroidManifest.bxml").toURI().toURL();
        Assert.assertNotNull(url);
        try (InputStream input = url.openStream()) {
            ManifestInfo manifest = ManifestInfo.parseBinaryFromStream(input);
            Assert.assertEquals(1, manifest.receivers().size());
            manifest.receivers().get(0).hasPermission("android.permission.BIND_DEVICE_ADMIN");
        }
    }

    @Test
    public void testManifestInfo() throws IOException {
        URL url = TestResources.getFile("/manifestInfo/AndroidManifest.bxml")
                .toURI()
                .toURL();
        Assert.assertNotNull(url);
        try (InputStream input = url.openStream()) {
            ManifestInfo manifest = ManifestInfo.parseBinaryFromStream(input);
            Assert.assertEquals(3, manifest.getVersionCode());
            Assert.assertTrue(manifest.getInstrumentationTargetPackages()
                                      .contains("com.android.shell"));
        }
    }

    private static ManifestActivityInfo getActivityByQName(@NonNull String qname,
            @NonNull List<ManifestActivityInfo> activities) {
        for (ManifestActivityInfo activity : activities) {
            if (qname.equals(activity.getQualifiedName())) {
                return activity;
            }
        }
        return null;
    }

    @Nullable
    private static ManifestServiceInfo getServiceByQName(@NonNull String qname,
            @NonNull List<ManifestServiceInfo> services) {
        for (ManifestServiceInfo service : services) {
            if (qname.equals(service.getQualifiedName())) {
                return service;
            }
        }
        return null;
    }
}
