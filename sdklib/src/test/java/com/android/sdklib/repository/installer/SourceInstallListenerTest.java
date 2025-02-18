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
package com.android.sdklib.repository.installer;

import static com.android.sdklib.IAndroidTarget.SOURCES;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.repository.api.Installer;
import com.android.repository.api.InstallerFactory;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.Uninstaller;
import com.android.repository.impl.installer.BasicInstallerFactory;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakeDownloader;
import com.android.repository.testframework.FakeInstallListenerFactory;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeRepoManager;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.testutils.file.InMemoryFileSystems;

import com.google.common.collect.ImmutableList;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Tests for {@link SourceInstallListener}
 */
public class SourceInstallListenerTest extends TestCase {

    private static final Path sdkRoot =
            InMemoryFileSystems.createInMemoryFileSystemAndFolder("sdk");

    public void testSourcePathUpdatedWithInstall() throws Exception {
        // Create remote package for sources;android-23
        FakePackage.FakeRemotePackage remote =
                new FakePackage.FakeRemotePackage("sources;android-23");
        URL archiveUrl = new URL("http://www.example.com/plat23/sources.zip");
        remote.setCompleteUrl(archiveUrl.toString());

        DetailsTypes.SourceDetailsType sourceDetails =
                AndroidSdkHandler.getRepositoryModule().createLatestFactory()
                        .createSourceDetailsType();
        sourceDetails.setApiLevel(23);
        remote.setTypeDetails((TypeDetails) sourceDetails);

        // Create local package for platform;android-23
        LocalPackage local = getLocalPlatformPackage();

        FakeRepoManager mgr =
                new FakeRepoManager(
                        sdkRoot,
                        new RepositoryPackages(ImmutableList.of(local), ImmutableList.of(remote)));
        mgr.registerSchemaModule(AndroidSdkHandler.getCommonModule());
        mgr.registerSchemaModule(AndroidSdkHandler.getRepositoryModule());

        // Create the archive and register the URL
        FakeDownloader downloader = new FakeDownloader(sdkRoot.getRoot().resolve("tmp"));
        ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("top-level/sources"));
            zos.write("contents".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        ByteArrayInputStream is = new ByteArrayInputStream(baos.toByteArray());
        downloader.registerUrl(archiveUrl, is);

        FakeProgressIndicator progress = new FakeProgressIndicator();

        // Assert that before installation, source path is not set thus deprecated value is returned
        AndroidSdkHandler mockHandler = new AndroidSdkHandler(sdkRoot, null, mgr);
        IAndroidTarget target = mockHandler.getAndroidTargetManager(progress)
                .getTargetFromHashString("android-23", progress);
        assertNotNull(target);
        assertThat(target.getPath(SOURCES).toString())
                .isEqualTo(
                        InMemoryFileSystems.getPlatformSpecificPath(
                                "/sdk/platforms/android-23/sources"));

        // Install
        InstallerFactory factory = SdkInstallerUtil.findBestInstallerFactory(remote, mockHandler);
        Installer installer = factory.createInstaller(remote, mgr, downloader);
        installer.prepare(progress);
        installer.complete(progress);

        // Assert that source path is updated to correct value
        progress.assertNoErrorsOrWarnings();
        assertThat(target.getPath(SOURCES).toString())
                .isEqualTo(InMemoryFileSystems.getPlatformSpecificPath("/sdk/sources/android-23"));
    }

    public void testSourcePathUpdatedWithUnInstall() {
        // Create local packages for platform;android-23 and sources;android-23
        LocalPackage localPlatform = getLocalPlatformPackage();
        LocalPackage localSource = getLocalSourcePackage();

        FakeRepoManager mgr =
                new FakeRepoManager(
                        sdkRoot,
                        new RepositoryPackages(
                                ImmutableList.of(localPlatform, localSource), ImmutableList.of()));
        mgr.registerSchemaModule(AndroidSdkHandler.getCommonModule());
        mgr.registerSchemaModule(AndroidSdkHandler.getRepositoryModule());

        FakeProgressIndicator progress = new FakeProgressIndicator();

        // Assert that before un-installation, source path is set to correct value
        AndroidSdkHandler mockHandler = new AndroidSdkHandler(sdkRoot, null, mgr);
        IAndroidTarget target = mockHandler.getAndroidTargetManager(progress)
                .getTargetFromHashString("android-23", progress);
        assertNotNull(target);
        assertThat(target.getPath(SOURCES).toString())
                .isEqualTo(InMemoryFileSystems.getPlatformSpecificPath("/sdk/sources/android-23"));

        // Uninstall
        BasicInstallerFactory factory = new BasicInstallerFactory();
        factory.setListenerFactory(
                new FakeInstallListenerFactory(new SourceInstallListener(mockHandler)));
        RepositoryPackages pkgs = mgr.getPackages();
        LocalPackage p = pkgs.getLocalPackages().get("sources;android-23");

        assertNotNull(p);
        Uninstaller uninstaller = factory.createUninstaller(p, mgr);
        uninstaller.prepare(progress);
        uninstaller.complete(progress);

        // Assert that source path is set back to null thus deprecated value is returned
        progress.assertNoErrorsOrWarnings();
        assertThat(target.getPath(SOURCES).toString())
                .isEqualTo(
                        InMemoryFileSystems.getPlatformSpecificPath(
                                "/sdk/platforms/android-23/sources"));
    }

    @NonNull
    private static LocalPackage getLocalPlatformPackage() {
        InMemoryFileSystems.recordExistingFile(sdkRoot.resolve("platforms/android-23/build.prop"));
        FakePackage.FakeLocalPackage local =
                new FakePackage.FakeLocalPackage(
                        "platforms;android-23", sdkRoot.resolve("platforms/android-23"));

        DetailsTypes.PlatformDetailsType platformDetails =
                AndroidSdkHandler.getRepositoryModule().createLatestFactory()
                        .createPlatformDetailsType();
        platformDetails.setApiLevel(23);
        local.setTypeDetails((TypeDetails) platformDetails);
        return local;
    }

    @NonNull
    private static LocalPackage getLocalSourcePackage() {
        InMemoryFileSystems.recordExistingFile(sdkRoot.resolve("sources/android-23/build.prop"));
        FakePackage.FakeLocalPackage local =
                new FakePackage.FakeLocalPackage(
                        "sources;android-23", sdkRoot.resolve("sources/android-23"));

        DetailsTypes.SourceDetailsType sourceDetails =
                AndroidSdkHandler.getRepositoryModule().createLatestFactory()
                        .createSourceDetailsType();
        sourceDetails.setApiLevel(23);
        local.setTypeDetails((TypeDetails) sourceDetails);
        return local;
    }
}
