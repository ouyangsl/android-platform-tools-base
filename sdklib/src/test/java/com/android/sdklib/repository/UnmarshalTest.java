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
package com.android.sdklib.repository;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.repository.Revision;
import com.android.repository.api.Checksum;
import com.android.repository.api.License;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.Repository;
import com.android.repository.api.SchemaModule;
import com.android.repository.impl.meta.Archive;
import com.android.repository.impl.meta.RemotePackageImpl;
import com.android.repository.impl.meta.SchemaModuleUtil;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.testutils.file.InMemoryFileSystems;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import junit.framework.TestCase;

/** Tests for unmarshalling an xml repository */
public class UnmarshalTest extends TestCase {

    public void testLoadRepoV3() throws Exception {
        String filename = "/repository2-3_sample.xml";
        InputStream xmlStream = getClass().getResourceAsStream(filename);
        assertNotNull("Missing test file: " + filename, xmlStream);

        SchemaModule repoEx = AndroidSdkHandler.getRepositoryModule();
        SchemaModule addonEx = AndroidSdkHandler.getAddonModule();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        Repository repo =
                (Repository)
                        SchemaModuleUtil.unmarshal(
                                xmlStream,
                                ImmutableList.of(repoEx, addonEx, RepoManager.getGenericModule()),
                                true,
                                progress,
                                filename);
        progress.assertNoErrorsOrWarnings();
        List<? extends License> licenses = repo.getLicense();
        assertEquals(licenses.size(), 2);
        Map<String, String> licenseMap = Maps.newHashMap();
        for (License license : licenses) {
            licenseMap.put(license.getId(), license.getValue());
        }
        assertEquals(licenseMap.get("license1").trim(), "This is the license for this platform.");
        assertEquals(
                licenseMap.get("license2").trim(),
                "Licenses are only of type 'text' right now, so this is implied.");

        List<? extends RemotePackage> packages = repo.getRemotePackage();
        assertEquals(3, packages.size());
        Map<String, RemotePackage> packageMap = Maps.newHashMap();
        for (RemotePackage p : packages) {
            packageMap.put(p.getPath(), p);
        }

        RemotePackage platform22 = packageMap.get("platforms;android-22");
        assertEquals(platform22.getDisplayName(), "Lollipop MR1");

        assertTrue(platform22.getTypeDetails() instanceof DetailsTypes.PlatformDetailsType);
        DetailsTypes.PlatformDetailsType details =
                (DetailsTypes.PlatformDetailsType) platform22.getTypeDetails();
        assertEquals(22, details.getApiLevel());
        assertEquals("22x", details.getApiLevelString());
        assertEquals(2, details.getExtensionLevel().intValue());
        assertFalse(details.isBaseExtension());
        assertThat(details.getAndroidVersion()).isNotEqualTo(new AndroidVersion(1));
        assertEquals(details.getAndroidVersion(), new AndroidVersion(22, null, 2, false));
        assertEquals(5, details.getLayoutlib().getApi());

        List<Archive> archives = ((RemotePackageImpl) platform22).getAllArchives();
        assertEquals(2, archives.size());
        Archive archive = archives.get(1);
        assertEquals("x64", archive.getHostArch());
        assertEquals("windows", archive.getHostOs());
        Archive.CompleteType complete = archive.getComplete();
        assertEquals(65536, complete.getSize());
        Checksum checksum = complete.getTypedChecksum();
        assertEquals("1234ae37115ebf13412bbef91339ee0d9454525e", checksum.getValue());
        assertEquals("sha-1", checksum.getType());

        RemotePackage sourcePackage = packageMap.get("sources;android-1");
        Checksum checksum2 =
                sourcePackage.getArchive().getComplete().getTypedChecksum();
        DetailsTypes.SourceDetailsType sourcePackageTypeDetails =
                (DetailsTypes.SourceDetailsType) sourcePackage.getTypeDetails();
        assertEquals(1, sourcePackageTypeDetails.getApiLevel());
        assertEquals("1", sourcePackageTypeDetails.getApiLevelString());
        assertNull(sourcePackageTypeDetails.getExtensionLevel());
        assertTrue(sourcePackageTypeDetails.isBaseExtension());
        assertNull(sourcePackageTypeDetails.getExtensionLevel());
        assertEquals(sourcePackageTypeDetails.getAndroidVersion(), new AndroidVersion(1));
        assertEquals(
                sourcePackageTypeDetails.getAndroidVersion(),
                new AndroidVersion(1, null, null, true));
        assertEquals(
                sourcePackageTypeDetails.getAndroidVersion(),
                new AndroidVersion(1, null, 101, true));
        assertEquals(
                "1234ae37115ebf13412bbef91339ee0d945412341339ee0d9454123494541234",
                checksum2.getValue());
        assertEquals("sha-256", checksum2.getType());
    }

    public void testLoadRepoV2() throws Exception {
        String filename = "/repository2-2_sample.xml";
        InputStream xmlStream = getClass().getResourceAsStream(filename);
        assertNotNull("Missing test file: " + filename, xmlStream);

        SchemaModule repoEx = AndroidSdkHandler.getRepositoryModule();
        SchemaModule addonEx = AndroidSdkHandler.getAddonModule();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        Repository repo =
                (Repository)
                        SchemaModuleUtil.unmarshal(
                                xmlStream,
                                ImmutableList.of(repoEx, addonEx, RepoManager.getGenericModule()),
                                true,
                                progress,
                                filename);
        progress.assertNoErrorsOrWarnings();
        List<? extends License> licenses = repo.getLicense();
        assertEquals(licenses.size(), 2);
        Map<String, String> licenseMap = Maps.newHashMap();
        for (License license : licenses) {
            licenseMap.put(license.getId(), license.getValue());
        }
        assertEquals(licenseMap.get("license1").trim(),
                "This is the license for this platform.");
        assertEquals(licenseMap.get("license2").trim(),
                "Licenses are only of type 'text' right now, so this is implied.");

        List<? extends RemotePackage> packages = repo.getRemotePackage();
        assertEquals(3, packages.size());
        Map<String, RemotePackage> packageMap = Maps.newHashMap();
        for (RemotePackage p : packages) {
            packageMap.put(p.getPath(), p);
        }

        RemotePackage platform22 = packageMap.get("platforms;android-22");
        assertEquals(platform22.getDisplayName(), "Lollipop MR1");

        assertTrue(platform22.getTypeDetails() instanceof DetailsTypes.PlatformDetailsType);
        DetailsTypes.PlatformDetailsType details = (DetailsTypes.PlatformDetailsType) platform22
                .getTypeDetails();
        assertEquals(1, details.getApiLevel());
        assertEquals(5, details.getLayoutlib().getApi());

        List<Archive> archives = ((RemotePackageImpl) platform22).getAllArchives();
        assertEquals(2, archives.size());
        Archive archive = archives.get(1);
        assertEquals("x64", archive.getHostArch());
        assertEquals("windows", archive.getHostOs());
        Archive.CompleteType complete = archive.getComplete();
        assertEquals(65536, complete.getSize());
        Checksum checksum = complete.getTypedChecksum();
        assertEquals("1234ae37115ebf13412bbef91339ee0d9454525e", checksum.getValue());
        assertEquals("sha-1", checksum.getType());

        Checksum checksum2 =
                packageMap.get("sources;android-1").getArchive().getComplete().getTypedChecksum();
        assertEquals(
                "1234ae37115ebf13412bbef91339ee0d945412341339ee0d9454123494541234",
                checksum2.getValue());
        assertEquals("sha-256", checksum2.getType());
    }

    public void testLoadRepoV1() throws Exception {
        String filename = "/repository2-1_sample.xml";
        InputStream xmlStream = getClass().getResourceAsStream(filename);
        assertNotNull("Missing test file: " + filename, xmlStream);

        SchemaModule repoEx = AndroidSdkHandler.getRepositoryModule();
        SchemaModule addonEx = AndroidSdkHandler.getAddonModule();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        Repository repo =
                (Repository)
                        SchemaModuleUtil.unmarshal(
                                xmlStream,
                                ImmutableList.of(repoEx, addonEx, RepoManager.getGenericModule()),
                                true,
                                progress,
                                filename);
        progress.assertNoErrorsOrWarnings();
        List<? extends License> licenses = repo.getLicense();
        assertEquals(licenses.size(), 2);
        Map<String, String> licenseMap = Maps.newHashMap();
        for (License license : licenses) {
            licenseMap.put(license.getId(), license.getValue());
        }
        assertEquals(licenseMap.get("license1").trim(), "This is the license for this platform.");
        assertEquals(
                licenseMap.get("license2").trim(),
                "Licenses are only of type 'text' right now, so this is implied.");

        List<? extends RemotePackage> packages = repo.getRemotePackage();
        assertEquals(3, packages.size());
        Map<String, RemotePackage> packageMap = Maps.newHashMap();
        for (RemotePackage p : packages) {
            packageMap.put(p.getPath(), p);
        }

        RemotePackage platform22 = packageMap.get("platforms;android-22");
        assertEquals(platform22.getDisplayName(), "Lollipop MR1");

        assertTrue(platform22.getTypeDetails() instanceof DetailsTypes.PlatformDetailsType);
        DetailsTypes.PlatformDetailsType details =
                (DetailsTypes.PlatformDetailsType) platform22.getTypeDetails();
        assertEquals(1, details.getApiLevel());
        assertEquals(5, details.getLayoutlib().getApi());

        List<Archive> archives = ((RemotePackageImpl) platform22).getAllArchives();
        assertEquals(2, archives.size());
        Archive archive = archives.get(1);
        assertEquals("x64", archive.getHostArch());
        assertEquals("windows", archive.getHostOs());
        Archive.CompleteType complete = archive.getComplete();
        assertEquals(65536, complete.getSize());
        Checksum checksum = complete.getTypedChecksum();
        assertEquals("1234ae37115ebf13412bbef91339ee0d9454525e", checksum.getValue());
        assertEquals("sha-1", checksum.getType());

        // TODO: add other extension types as below
        /*
        filename = "/com/android/sdklib/testdata/addon2_sample_1.xml";
        xmlStream = getClass().getResourceAsStream(filename);
        repo = SchemaModule.unmarshal(xmlStream, ImmutableList.of(repoEx, addonEx));
        assertTrue(repo.getPackage().get(0).getTypeDetails() instanceof DetailsTypes.AddonDetailsType);*/
    }

    private static final String INVALID_XML =
            "<repo:repository\n"
                    + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                    + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                    + "    <localPackage path=\"sample;foo\" obsolete=\"true\">\n"
                    + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                    + "        <revision>\n"
                    + "            <major>1</major>\n"
                    + "            <minor>2</minor>\n"
                    + "            <micro>3</micro>\n"
                    + "        </revision>\n"
                    + "        <foo bar=\"baz\"/>"
                    + "        <display-name>Test package</display-name>\n"
                    + "    </localPackage>\n"
                    + "</repo:repository>";

    public void testLeniency() throws Exception {
        AndroidSdkHandler handler = getAndroidSdkHandler();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        Repository repo =
                (Repository)
                        SchemaModuleUtil.unmarshal(
                                new ByteArrayInputStream(INVALID_XML.getBytes()),
                                ImmutableList.of(RepoManager.getGenericModule()),
                                false,
                                progress,
                                "Xml");
        assertFalse(progress.getWarnings().isEmpty());
        LocalPackage local = repo.getLocalPackage();
        assertEquals("sample;foo", local.getPath());
        assertEquals(new Revision(1, 2, 3), local.getVersion());

        try {
            SchemaModuleUtil.unmarshal(
                    new ByteArrayInputStream(INVALID_XML.getBytes()),
                    ImmutableList.of(RepoManager.getGenericModule()),
                    true,
                    progress,
                    "Xml");
            fail();
        }
        catch (Exception e) {
            // expected
        }
    }

    private static final String FUTURE_XML =
            "<repo:repository\n"
                    + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/99\"\n"
                    + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                    + "    <localPackage path=\"sample;foo\" obsolete=\"true\">\n"
                    + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                    + "        <revision>\n"
                    + "            <major>1</major>\n"
                    + "            <minor>2</minor>\n"
                    + "            <micro>3</micro>\n"
                    + "        </revision>\n"
                    + "        <display-name>Test package</display-name>\n"
                    + "    </localPackage>\n"
                    + "</repo:repository>";

    public void testNamespaceFallback() throws Exception {
        AndroidSdkHandler handler = getAndroidSdkHandler();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        Repository repo =
                (Repository)
                        SchemaModuleUtil.unmarshal(
                                new ByteArrayInputStream(FUTURE_XML.getBytes()),
                                ImmutableList.of(
                                        RepoManager.getGenericModule(),
                                        RepoManager.getCommonModule()),
                                false,
                                progress,
                                "Xml");
        // Note: if the following assertion fails, it could be because another test has already
        // parsed a future XML version, which sets a static flag suppressing future warnings.
        // The other test will need to either stop parsing future XML versions or be made hermetic.
        assertFalse(progress.getWarnings().isEmpty());
        LocalPackage local = repo.getLocalPackage();
        assertEquals("sample;foo", local.getPath());
        assertEquals(new Revision(1, 2, 3), local.getVersion());

        try {
            SchemaModuleUtil.unmarshal(
                    new ByteArrayInputStream(FUTURE_XML.getBytes()),
                    ImmutableList.of(RepoManager.getCommonModule(), RepoManager.getGenericModule()),
                    true,
                    progress,
                    "Xml");
            fail();
        }
        catch (Exception e) {
            // expected
        }
    }

    @NonNull
    private static AndroidSdkHandler getAndroidSdkHandler() {
        return new AndroidSdkHandler(
                InMemoryFileSystems.createInMemoryFileSystemAndFolder("sdk"), null);
    }
}
