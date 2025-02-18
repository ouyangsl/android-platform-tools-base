/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.sdklib.deviceprovisioner.testing

import com.android.adblib.ConnectedDevice
import com.android.adblib.serialNumber
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.utils.createChildScope
import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.ActivationAction
import com.android.sdklib.deviceprovisioner.DeactivationAction
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceProvisionerPlugin
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceState.Connected
import com.android.sdklib.deviceprovisioner.DeviceState.Disconnected
import com.android.sdklib.deviceprovisioner.DeviceTemplate
import com.android.sdklib.deviceprovisioner.EmptyIcon
import com.android.sdklib.deviceprovisioner.TestDefaultDeviceActionPresentation
import com.android.sdklib.deviceprovisioner.awaitDisconnection
import com.android.sdklib.deviceprovisioner.testing.FakeAdbDeviceProvisionerPlugin.FakeDeviceHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A [DeviceProvisionerPlugin] that allows creating [FakeDeviceHandle]s that can be activated and
 * deactivated.
 */
class FakeAdbDeviceProvisionerPlugin(
  val scope: CoroutineScope,
  private val fakeAdb: FakeAdbServerProvider,
  override val priority: Int = 1,
) : DeviceProvisionerPlugin {
  /** If true, devices do not enter ready state after activation until finishBoot is called */
  @get:Synchronized @set:Synchronized var explicitBoot: Boolean = false

  /** Claims any device that has been registered with [addDevice] (based on serial number). */
  override suspend fun claim(device: ConnectedDevice): DeviceHandle? {
    val handle = devices.value.find { it.serialNumber == device.serialNumber } ?: return null
    handle.stateFlow.update {
      Connected(
        it.properties,
        isTransitioning = explicitBoot,
        isReady = !explicitBoot,
        status = if (explicitBoot) "Booting" else "Connected",
        device,
      )
    }
    scope.launch {
      device.awaitDisconnection()
      handle.stateFlow.update { Disconnected(it.properties) }
    }
    return handle
  }

  override val templates = MutableStateFlow<List<DeviceTemplate>>(emptyList())

  override val devices = MutableStateFlow(emptyList<FakeDeviceHandle>())

  /**
   * Creates a FakeDeviceHandle in the Disconnected state.
   *
   * Note that, in order to allow simulating devices becoming known and unknown to the plugin, the
   * returned device will not be initially known to the plugin. That is, it will not be reported in
   * [devices] and it will not be claimed if it is activated.
   *
   * To get the device in the [Connected] state, it needs to be made known to the plugin using
   * [addDevice], and activated using its [ActivationAction], which adds it to FakeAdb.
   */
  fun newDevice(
    serialNumber: String = nextSerial(),
    properties: DeviceProperties = DEFAULT_PROPERTIES,
  ): FakeDeviceHandle {
    return FakeDeviceHandle(scope.createChildScope(true), Disconnected(properties), serialNumber)
  }

  /** Creates a FakeDeviceHandle in the Disconnected state that is already known to the plugin. */
  fun addNewDevice(
    serialNumber: String = nextSerial(),
    properties: DeviceProperties = DEFAULT_PROPERTIES,
  ): FakeDeviceHandle = newDevice(serialNumber, properties).also { addDevice(it) }

  /** Makes the device known to the plugin, in its current state. */
  fun addDevice(device: FakeDeviceHandle) {
    devices.update { it + device }
  }

  /** Makes the device unknown to the plugin; i.e. it will no longer be returned from [devices]. */
  fun removeDevice(device: FakeDeviceHandle) {
    devices.update { it - device }
    device.scope.cancel()
  }

  /** Makes the template known to the plugin */
  fun addTemplate(template: DeviceTemplate) {
    templates.update { it + template }
  }

  /** Makes the template unknown to the plugin */
  fun removeTemplate(template: DeviceTemplate) {
    templates.update { it - template }
  }

  private var serialNumber = 1

  fun nextSerial(): String = "fake-device-${serialNumber++}"

  companion object {
    val DEFAULT_PROPERTIES =
      DeviceProperties.buildForTest {
        manufacturer = "Google"
        model = "Pixel 6"
        androidVersion = AndroidVersion(31)
        androidRelease = "11"
        icon = EmptyIcon.DEFAULT
      }
  }

  inner class FakeDeviceHandle(
    override val scope: CoroutineScope,
    initialState: DeviceState,
    val serialNumber: String,
  ) : DeviceHandle {

    override val id = DeviceId(PLUGIN_ID, false, "serial=$serialNumber")

    var fakeAdbDevice: com.android.fakeadbserver.DeviceState? = null
      get() =
        synchronized(this) {
          return field
        }
      private set(value) = synchronized(this) { field = value }

    override val stateFlow = MutableStateFlow(initialState)
    override var sourceTemplate: DeviceTemplate? = null

    fun finishBoot() {
      stateFlow.update { (it as? Connected)?.copy(isTransitioning = false, isReady = true) ?: it }
    }

    override val activationAction =
      object : ActivationAction {
        override val presentation =
          MutableStateFlow(TestDefaultDeviceActionPresentation.fromContext())

        override suspend fun activate() {
          val properties = state.properties
          fakeAdbDevice =
            fakeAdb
              .connectDevice(
                serialNumber,
                properties.manufacturer ?: "(Unknown manufacturer)",
                properties.model ?: "(Unknown model)",
                properties.androidRelease ?: "(Unknown release)",
                properties.androidVersion?.apiLevel?.toString() ?: "",
                com.android.fakeadbserver.DeviceState.HostConnectionType.USB,
              )
              .also { it.deviceStatus = com.android.fakeadbserver.DeviceState.DeviceStatus.ONLINE }
        }
      }

    override val deactivationAction: DeactivationAction =
      object : DeactivationAction {
        override val presentation =
          MutableStateFlow(TestDefaultDeviceActionPresentation.fromContext())

        override suspend fun deactivate() {
          fakeAdb.disconnectDevice(serialNumber)
          fakeAdbDevice = null
        }
      }
  }
}

private val PLUGIN_ID = "FakeAdb"
