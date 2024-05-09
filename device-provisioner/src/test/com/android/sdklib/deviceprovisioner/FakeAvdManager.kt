/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.sdklib.deviceprovisioner

import com.android.adblib.ConnectedDevice
import com.android.adblib.DeviceInfo
import com.android.adblib.DeviceList
import com.android.adblib.DevicePropertyNames
import com.android.adblib.DeviceSelector
import com.android.adblib.DeviceState
import com.android.adblib.serialNumber
import com.android.adblib.testing.FakeAdbSession
import com.android.sdklib.AndroidVersion
import com.android.sdklib.ISystemImage
import com.android.sdklib.SystemImageTags
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdManager
import com.android.sdklib.repository.IdDisplay
import java.nio.file.Path

class FakeAvdManager(val session: FakeAdbSession, val avdRoot: Path) :
  LocalEmulatorProvisionerPlugin.AvdManager {
  val avds = mutableListOf<AvdInfo>()
  val runningDevices = mutableSetOf<FakeEmulatorConsole>()
  var avdIndex = 1
  var avdEditor: (AvdInfo) -> AvdInfo = { avdInfo: AvdInfo ->
    avdInfo.copy(
      properties =
        avdInfo.properties + (AvdManager.AVD_INI_DISPLAY_NAME to avdInfo.displayName + " Edited")
    )
  }

  override suspend fun rescanAvds(): List<AvdInfo> = synchronized(avds) { avds.toList() }

  override suspend fun createAvd(): AvdInfo {
    return makeAvdInfo(avdIndex++).also { createAvd(it) }
  }

  fun makeAvdInfo(
    index: Int,
    androidVersion: AndroidVersion = LocalEmulatorProvisionerPluginTest.API_LEVEL,
    hasPlayStore: Boolean = true,
    avdStatus: AvdInfo.AvdStatus = AvdInfo.AvdStatus.OK,
    tag: IdDisplay = SystemImageTags.DEFAULT_TAG,
  ): AvdInfo = makeAvdInfo(avdRoot, index, androidVersion, hasPlayStore, avdStatus, tag)

  fun createAvd(avdInfo: AvdInfo) {
    synchronized(avds) { avds += avdInfo }
  }

  override suspend fun editAvd(avdInfo: AvdInfo): AvdInfo? =
    synchronized(avds) {
      avds.remove(avdInfo)
      val newAvdInfo = avdEditor(avdInfo)
      avds += newAvdInfo
      return newAvdInfo
    }

  override suspend fun startAvd(avdInfo: AvdInfo) = boot(avdInfo, false, null)

  override suspend fun coldBootAvd(avdInfo: AvdInfo) = boot(avdInfo, true, null)

  override suspend fun bootAvdFromSnapshot(avdInfo: AvdInfo, snapshot: LocalEmulatorSnapshot) =
    boot(avdInfo, false, snapshot)

  private fun boot(avdInfo: AvdInfo, coldBoot: Boolean, snapshot: LocalEmulatorSnapshot?) {
    avdInfo.properties[LAUNCH_EXCEPTION_MESSAGE]?.let { throw DeviceActionException(it) }

    val device =
      FakeEmulatorConsole(avdInfo.name, avdInfo.dataFolderPath.toString()) { doStopAvd(avdInfo) }
    val selector = DeviceSelector.fromSerialNumber("emulator-${device.port}")
    session.deviceServices.configureDeviceProperties(
      selector,
      properties +
        mapOf(
          "ro.test.coldboot" to if (coldBoot) "1" else "0",
          "ro.test.snapshot" to (snapshot?.path?.toString() ?: ""),
          "dev.bootcomplete" to if (coldBoot) "" else "1",
        ),
    )
    device.start()
    runningDevices += device
    updateDevices()
  }

  fun finishBoot(device: ConnectedDevice) {
    session.deviceServices.configureDeviceProperties(
      DeviceSelector.fromSerialNumber(device.serialNumber),
      properties + mapOf("ro.test.coldboot" to "1", "dev.bootcomplete" to "1"),
    )
  }

  override suspend fun stopAvd(avdInfo: AvdInfo) {
    doStopAvd(avdInfo)
  }

  override suspend fun showOnDisk(avdInfo: AvdInfo) {
    // no-op
  }

  override suspend fun duplicateAvd(avdInfo: AvdInfo) {
    // not used
  }

  override suspend fun wipeData(avdInfo: AvdInfo) {
    // not used
  }

  private fun doStopAvd(avdInfo: AvdInfo) {
    runningDevices.removeIf { it.avdPath == avdInfo.dataFolderPath.toString() }
    updateDevices()
  }

  override suspend fun deleteAvd(avdInfo: AvdInfo) {
    synchronized(avds) { avds.remove(avdInfo) }
  }

  override suspend fun downloadAvdSystemImage(avdInfo: AvdInfo) {
    avds[avds.indexOf(avdInfo)] =
      AvdInfo(
        avdInfo.name,
        avdInfo.iniFile,
        avdInfo.dataFolderPath,
        avdInfo.systemImage,
        avdInfo.properties,
        avdInfo.userSettings,
        AvdInfo.AvdStatus.OK,
      )
  }

  fun close() {
    runningDevices.forEach(FakeEmulatorConsole::close)
  }

  private fun updateDevices() {
    session.hostServices.devices =
      DeviceList(
        runningDevices.map { DeviceInfo("emulator-${it.port}", DeviceState.ONLINE) },
        emptyList(),
      )
  }

  private val properties =
    mapOf(
      "ro.serialno" to "EMULATOR31X3X7X0",
      DevicePropertyNames.RO_BUILD_VERSION_SDK to
        LocalEmulatorProvisionerPluginTest.API_LEVEL.apiStringWithoutExtension,
      DevicePropertyNames.RO_BUILD_VERSION_RELEASE to LocalEmulatorProvisionerPluginTest.RELEASE,
      DevicePropertyNames.RO_PRODUCT_MANUFACTURER to
        LocalEmulatorProvisionerPluginTest.MANUFACTURER,
      DevicePropertyNames.RO_PRODUCT_MODEL to LocalEmulatorProvisionerPluginTest.MODEL,
      DevicePropertyNames.RO_PRODUCT_CPU_ABI to LocalEmulatorProvisionerPluginTest.ABI.toString(),
      "ro.kernel.qemu" to "1",
    )
}

fun AvdInfo.copy(
  name: String = this.name,
  iniFile: Path = this.iniFile,
  folderPath: Path = this.dataFolderPath,
  systemImage: ISystemImage? = this.systemImage,
  properties: Map<String, String> = this.properties,
  userSettings: Map<String, String?>? = this.userSettings,
  status: AvdInfo.AvdStatus = this.status,
): AvdInfo = AvdInfo(name, iniFile, folderPath, systemImage, properties, userSettings, status)

const val LAUNCH_EXCEPTION_MESSAGE = "launch_exception_message"

fun makeAvdInfo(
  avdRoot: Path,
  index: Int,
  androidVersion: AndroidVersion = LocalEmulatorProvisionerPluginTest.API_LEVEL,
  hasPlayStore: Boolean = true,
  avdStatus: AvdInfo.AvdStatus = AvdInfo.AvdStatus.OK,
  tag: IdDisplay = SystemImageTags.DEFAULT_TAG,
): AvdInfo {
  val basePath = avdRoot.resolve("avd_$index")
  return AvdInfo(
    "fake_avd_$index",
    basePath.resolve("config.ini"),
    basePath,
    null,
    mapOf(
      AvdManager.AVD_INI_DEVICE_MANUFACTURER to LocalEmulatorProvisionerPluginTest.MANUFACTURER,
      AvdManager.AVD_INI_DEVICE_NAME to LocalEmulatorProvisionerPluginTest.MODEL,
      AvdManager.AVD_INI_ANDROID_API to androidVersion.apiStringWithoutExtension,
      AvdManager.AVD_INI_ABI_TYPE to LocalEmulatorProvisionerPluginTest.ABI.toString(),
      AvdManager.AVD_INI_DISPLAY_NAME to "Fake Device $index",
      AvdManager.AVD_INI_PLAYSTORE_ENABLED to hasPlayStore.toString(),
      AvdManager.AVD_INI_TAG_ID to tag.id,
      AvdManager.AVD_INI_TAG_DISPLAY to tag.display,
    ),
    null,
    avdStatus,
  )
}
