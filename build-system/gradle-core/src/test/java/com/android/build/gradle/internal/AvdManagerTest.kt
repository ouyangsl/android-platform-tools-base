/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal

import com.android.SdkConstants
import com.android.build.gradle.internal.fixtures.FakeGradleDirectory
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeGradleRegularFile
import com.android.build.gradle.internal.testing.AdbHelper
import com.android.prefs.AndroidLocationsSingleton
import com.android.repository.io.FileOpUtils
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.testutils.file.recordExistingFile
import com.android.utils.ILogger
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Answers.RETURNS_DEEP_STUBS
import org.mockito.kotlin.any
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.junit.MockitoJUnit

@RunWith(JUnit4::class)
class AvdManagerTest {

    @get:Rule
    val mockitoRule = MockitoJUnit.rule()

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var rootDir: Path
    private lateinit var manager: AvdManager
    private lateinit var sdkFolder: Path
    private lateinit var systemImageFolder: Path
    private lateinit var emulatorFolder: Path
    private lateinit var androidPrefsFolder: Path
    private lateinit var avdFolder: Path
    private lateinit var adbExecutable: Path
    private lateinit var snapshotHandler: AvdSnapshotHandler
    private lateinit var versionedSdkLoader: SdkComponentsBuildService.VersionedSdkLoader

    private val lockManager: ManagedVirtualDeviceLockManager = mock(defaultAnswer = RETURNS_DEEP_STUBS)

    private val adbHelper: AdbHelper = mock()

    @Before
    fun setup() {
        rootDir = tmpFolder.newFolder().toPath()
        sdkFolder = Files.createDirectories(rootDir.resolve("sdk"))
        systemImageFolder = sdkFolder.resolve("system-images/android-29/default/x86")
        Files.createDirectories(systemImageFolder)
        val vendorImage = systemImageFolder.resolve("system.img")
        vendorImage.recordExistingFile()
        val userImg =
            systemImageFolder.resolve(com.android.sdklib.internal.avd.AvdManager.USERDATA_IMG)
        userImg.recordExistingFile()
        emulatorFolder = sdkFolder.resolve("tools/lib/emulator")
        Files.createDirectories(emulatorFolder)
        androidPrefsFolder = rootDir.resolve("android-home")
        avdFolder = rootDir.resolve("avd")
        adbExecutable = rootDir.resolve("adb")
        Files.createDirectories(avdFolder)

        snapshotHandler = mock<AvdSnapshotHandler>()

        versionedSdkLoader = setupVersionedSdkLoader()
        val sdkHandler = setupSdkHandler()

        manager = AvdManager(
            FileOpUtils.toFile(avdFolder),
            FakeGradleProvider(versionedSdkLoader),
            sdkHandler,
            AndroidLocationsSingleton,
            snapshotHandler,
            lockManager,
            adbHelper
        )
    }

    @Test
    fun noDevicesFromEmptyFolder() {
        assertThat(manager.allAvds()).hasSize(0)
    }

    @Test
    fun addSingleDevice() {
        manager.createOrRetrieveAvd(
            FakeGradleProvider(FakeGradleDirectory(FileOpUtils.toFile(systemImageFolder))),
            "system-images;android-29;default;x86",
            "device1",
            "Pixel 2")

        var allAvds = manager.allAvds()
        assertThat(allAvds).hasSize(1)
        assertThat(allAvds.first()).isEqualTo("device1")
        // Ensure the lock file is also created.
        assertThat(avdFolder.toFile().resolve("device1.lock").exists()).isTrue()

        // Since, the device exists, create or retrieve should not make another device.
        manager.createOrRetrieveAvd(
            FakeGradleProvider(FakeGradleDirectory(FileOpUtils.toFile(systemImageFolder))),
            "system-images;android-29;default;x86",
            "device1",
            "Pixel 2")

        allAvds = manager.allAvds()
        assertThat(allAvds).hasSize(1)
        assertThat(allAvds.first()).isEqualTo("device1")
    }

    @Test
    fun addMultipleDevices() {
        manager.createAvd(
            FakeGradleProvider(FakeGradleDirectory(FileOpUtils.toFile(systemImageFolder))),
            "system-images;android-29;default;x86",
            "device1",
            "Pixel 2")
        manager.createAvd(
            FakeGradleProvider(FakeGradleDirectory(FileOpUtils.toFile(systemImageFolder))),
            "system-images;android-29;default;x86",
            "device2",
            "Pixel 3")
        manager.createAvd(
            FakeGradleProvider(FakeGradleDirectory(FileOpUtils.toFile(systemImageFolder))),
            "system-images;android-29;default;x86",
            "device3",
            "Pixel 2")

        val allAvds = manager.allAvds()
        assertThat(allAvds).hasSize(3)
        assertThat(allAvds).contains("device1")
        assertThat(allAvds).contains("device2")
        assertThat(allAvds).contains("device3")
    }

    @Test
    fun addSameDeviceDoesNotDuplicate() {
        manager.createAvd(
            FakeGradleProvider(FakeGradleDirectory(FileOpUtils.toFile(systemImageFolder))),
            "system-images;android-29;default;x86",
            "device1",
            "Pixel 2")
        manager.createAvd(
            FakeGradleProvider(FakeGradleDirectory(FileOpUtils.toFile(systemImageFolder))),
            "system-images;android-29;default;x86",
            "device1",
            "Pixel 2")

        val allAvds = manager.allAvds()
        assertThat(allAvds).hasSize(1)
        assertThat(allAvds.first()).isEqualTo("device1")
    }

    @Test
    fun testDeleteDevices() {
        manager.createOrRetrieveAvd(
            FakeGradleProvider(FakeGradleDirectory(FileOpUtils.toFile(systemImageFolder))),
            "system-images;android-29;default;x86",
            "device1",
            "Pixel 2")
        manager.createOrRetrieveAvd(
            FakeGradleProvider(FakeGradleDirectory(FileOpUtils.toFile(systemImageFolder))),
            "system-images;android-29;default;x86",
            "device2",
            "Pixel 3")

        var allAvds = manager.allAvds()
        assertThat(allAvds).hasSize(2)
        // Ensure the lock files exists
        assertThat(avdFolder.toFile().resolve("device1.lock").exists()).isTrue()
        assertThat(avdFolder.toFile().resolve("device2.lock").exists()).isTrue()

        manager.deleteAvds(listOf("device1"))

        allAvds = manager.allAvds()
        assertThat(allAvds).hasSize(1)
        assertThat(allAvds.first()).isEqualTo("device2")
        // Ensure the lock file is also deleted.
        assertThat(avdFolder.toFile().resolve("device1.lock").exists()).isFalse()
        // The other lock should be preserved.
        assertThat(avdFolder.toFile().resolve("device2.lock").exists()).isTrue()
    }

    @Test
    fun testSnapshotCreation() {
        whenever(
            snapshotHandler.checkSnapshotLoadable(
                any(),
                any<File>(),
                any(),
                any<ILogger>(),
                any()))
            // first return false to force generation, then return true to assert success.
            .thenReturn(false, true)

        manager.createAvd(
            FakeGradleProvider(FakeGradleDirectory(FileOpUtils.toFile(systemImageFolder))),
            "system-images;android-29;default;x86",
            "device1",
            "Pixel 2")

        manager.loadSnapshotIfNeeded("device1", "auto-no-window")

        verify(snapshotHandler)
            .generateSnapshot(
                any(),
                any<File>(),
                any(),
                any<com.android.sdklib.internal.avd.AvdManager>(),
                any<ILogger>())
    }

    @Test
    fun testSnapshotSkippedIfValid() {
        whenever(
            snapshotHandler.checkSnapshotLoadable(
                any(),
                any<File>(),
                any(),
                any<ILogger>(),
                any()))
            .thenReturn(true)

        manager.createAvd(
            FakeGradleProvider(FakeGradleDirectory(FileOpUtils.toFile(systemImageFolder))),
            "system-images;android-29;default;x86",
            "device1",
            "Pixel 2")

        manager.loadSnapshotIfNeeded("device1", "auto-no-window")

        verify(snapshotHandler, times(0))
            .generateSnapshot(
                any(),
                any<File>(),
                any(),
                any<com.android.sdklib.internal.avd.AvdManager>(),
                any<ILogger>())
    }

    @Test
    fun invalidHardwareProfileShouldSuggestNearMatchDevices() {
        val errorMessage = assertThrows(IllegalStateException::class.java) {
            manager.createAvd(
                FakeGradleProvider(FakeGradleDirectory(FileOpUtils.toFile(systemImageFolder))),
                "system-images;android-29;default;x86",
                "device1",
                "Pixel 300")
        }

        assertThat(errorMessage.message).isEqualTo("""
            Failed to find hardware profile for name: Pixel 300
            Try one of the following device profiles: Pixel 3, Pixel 3a, Pixel C, Pixel XL, Pixel 2
        """.trimIndent())
    }

    @Test
    fun invalidHardwareProfileShouldSuggestPixel6IfNoGoodOtherCandidates() {
        val errorMessage = assertThrows(IllegalStateException::class.java) {
            manager.createAvd(
                FakeGradleProvider(FakeGradleDirectory(FileOpUtils.toFile(systemImageFolder))),
                "system-images;android-29;default;x86",
                "device1",
                "r4ndom-device")
        }

        assertThat(errorMessage.message).isEqualTo("""
            Failed to find hardware profile for name: r4ndom-device
            Try one of the following device profiles: Pixel 6, Pixel C, Pixel 2, Pixel 3, Pixel 4
        """.trimIndent())
    }

    private fun setupVersionedSdkLoader(): SdkComponentsBuildService.VersionedSdkLoader =
        mock<SdkComponentsBuildService.VersionedSdkLoader>().also {
            whenever(it.sdkDirectoryProvider)
                .thenReturn(FakeGradleProvider(FakeGradleDirectory(FileOpUtils.toFile(sdkFolder))))
            whenever(it.sdkImageDirectoryProvider(any()))
                .thenReturn(FakeGradleProvider(FakeGradleDirectory(FileOpUtils.toFile(systemImageFolder))))
            whenever(it.emulatorDirectoryProvider)
                .thenReturn(FakeGradleProvider(FakeGradleDirectory(FileOpUtils.toFile(emulatorFolder))))
            whenever(it.adbExecutableProvider)
                .thenReturn(FakeGradleProvider(FakeGradleRegularFile(FileOpUtils.toFile(adbExecutable))))
        }

    private fun setupSdkHandler(): AndroidSdkHandler {
        emulatorFolder.resolve("snapshots.img").recordExistingFile()
        emulatorFolder.resolve(SdkConstants.FD_LIB).resolve(SdkConstants.FN_HARDWARE_INI)
                .recordExistingFile()
        recordSysImg()

        return AndroidSdkHandler(sdkFolder, androidPrefsFolder)
    }

    private fun recordSysImg() {
        systemImageFolder.resolve("system.img").recordExistingFile()
        systemImageFolder.resolve(com.android.sdklib.internal.avd.AvdManager.USERDATA_IMG)
                .recordExistingFile(
        )
        systemImageFolder.resolve("skins/res1/layout").recordExistingFile()
        systemImageFolder.resolve("skins/sample").recordExistingFile()
        systemImageFolder.resolve("skins/res2/layout").recordExistingFile()
        systemImageFolder.resolve("package.xml").recordExistingFile(
            0,
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <ns3:sdk-sys-img
                    xmlns:ns2="http://schemas.android.com/sdk/android/repo/repository2/01"
                    xmlns:ns3="http://schemas.android.com/sdk/android/repo/sys-img2/01"
                    xmlns:ns4="http://schemas.android.com/repository/android/common/01"
                    xmlns:ns5="http://schemas.android.com/sdk/android/repo/addon2/01">
                    <license id="license" type="text">A Very Valid License
                    </license><localPackage path="system-images;android-29;default;x86"
                    obsolete="false">
                    <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:type="ns3:sysImgDetailsType"><api-level>29</api-level>
                    <tag><id>default</id><display>Default</display></tag><abi>x86</abi>
                    </type-details><revision><major>5</major></revision>
                    <display-name>Intel x86 Atom System Image</display-name>
                    <uses-license ref="license"/></localPackage>
                    </ns3:sdk-sys-img>
                    """.trimIndent().toByteArray())
    }

    // to fix "cannot be null" issues with argument matchers
    private fun <T> any(type: Class<T>): T = Mockito.any(type)
}
