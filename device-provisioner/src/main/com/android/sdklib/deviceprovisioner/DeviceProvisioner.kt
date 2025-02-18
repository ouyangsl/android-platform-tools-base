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
package com.android.sdklib.deviceprovisioner

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.DeviceSelector
import com.android.adblib.DeviceState
import com.android.adblib.adbLogger
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.isKnownDevice
import com.android.adblib.scope
import com.android.adblib.serialNumber
import com.android.sdklib.deviceprovisioner.SetChange.Add
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Central access point for devices and device templates. [DeviceProvisionerPlugin] instances
 * provide collections of [DeviceHandle] and [DeviceTemplate] instances, which are collected by this
 * class and provided to consumers.
 */
class DeviceProvisioner
private constructor(
  val scope: CoroutineScope,
  private val adbSession: AdbSession,
  private val provisioners: List<DeviceProvisionerPlugin>,
) {
  companion object {
    fun create(
      coroutineScope: CoroutineScope,
      adbSession: AdbSession,
      provisioners: List<DeviceProvisionerPlugin>,
    ) =
      DeviceProvisioner(coroutineScope, adbSession, provisioners.sortedByDescending { it.priority })
  }

  private val logger = adbLogger(adbSession)

  private val offerMutex = Mutex()

  private val combinedDevices = combine(provisioners.map { it.devices }) { it.flatMap { it } }
  private val combinedTemplates = combine(provisioners.map { it.templates }) { it.flatMap { it } }

  /** The [device handles][DeviceHandle] known to this class, provided by its plugins. */
  val devices: StateFlow<List<DeviceHandle>> =
    combinedDevices.stateIn(scope, SharingStarted.Eagerly, emptyList())

  /** The [device templates][DeviceTemplate] known to this class, provided by its plugins. */
  val templates: StateFlow<List<DeviceTemplate>> =
    combinedTemplates.stateIn(scope, SharingStarted.Eagerly, emptyList())

  /**
   * The ConnectedDevices that are not claimed by any plugin (probably because the device is not yet
   * online, perhaps waiting for authorization).
   */
  val unclaimedDevices: StateFlow<List<ConnectedDevice>> =
    adbSession.connectedDevicesTracker.connectedDevices
      .combine(devices) { connectedDevices, deviceHandles ->
        val claimedDevices = deviceHandles.mapNotNull { it.state.connectedDevice }.toSet()
        connectedDevices - claimedDevices
      }
      .stateIn(scope, SharingStarted.Eagerly, emptyList())

  /**
   * Finds the DeviceHandle of the connected device with the given serial number, waiting up to
   * [timeout] for it to appear.
   *
   * This is intended for interoperability with DDMLib, or AdbLib's ConnectedDevicesTracker. It's
   * possible for a device to be visible by these interfaces before it becomes visible via the
   * DeviceProvisioner; thus, we wait a short time for it to show up before giving up.
   *
   * This has the potential to introduce unnecessary delays if invoked for a device that no longer
   * exists, thus it should be used with care. Generally, it is preferable to obtain a
   * [DeviceHandle] by collecting the [devices] flow.
   */
  suspend fun findConnectedDeviceHandle(
    deviceSelector: DeviceSelector,
    timeout: Duration = 2.seconds,
  ): DeviceHandle? =
    withTimeoutOrNull(timeout) {
      val serialNumber = adbSession.hostServices.getSerialNo(deviceSelector)
      val connectedDevices = adbSession.connectedDevicesTracker.connectedDevices
      // If it's not present in the ConnectedDevicesTracker, make a call to ADB to be sure.
      val isPresent =
        connectedDevices.value.any { it.serialNumber == serialNumber } ||
          adbSession.hostServices.isKnownDevice(serialNumber)
      when {
        isPresent ->
          // ADB is aware of this serial number; wait for it to appear in the provisioner.
          devices
            .mapNotNull { handles ->
              handles.firstOrNull {
                it.state.connectedDevice?.serialNumber == serialNumber && it.state.isOnline()
              }
            }
            .firstOrNull()
        else -> null
      }
    }

  init {
    scope.launch {
      adbSession.connectedDevicesTracker.connectedDevices
        .map { it.toSet() }
        .trackSetChanges()
        .collect {
          if (it is Add) {
            offerWhileConnected(this, it.value)
          }
        }
    }
  }

  /**
   * Launches a coroutine that runs for the lifetime of the device, offering it to the plugins in
   * priority order. When it is claimed, wait to see if it becomes unclaimed, then offer it again.
   */
  private fun offerWhileConnected(scope: CoroutineScope, device: ConnectedDevice) {
    val job =
      scope.launch {
        logger.debug { "Offering ${device.serialNumber}" }
        while (device.scope.isActive) {
          // Wait until the device becomes online, then offer it to a plugin.
          val handle =
            device.deviceInfoFlow
              .map { it.deviceState == DeviceState.ONLINE }
              .transformLatest { isOnline ->
                if (isOnline) {
                  when (val handle = offer(device)) {
                    null ->
                      logger.warn("Device ${device.serialNumber} not claimed by any provisioner")
                    else -> emit(handle)
                  }
                } else {
                  logger.debug { "Device ${device.serialNumber} is offline" }
                }
              }
              .first()

          // Once it is claimed, it's the plugin's responsibility; we wait until the plugin no
          // longer holds the device before re-offering it.
          handle.awaitRelease(device)
          logger.debug { "Re-offering ${device.serialNumber}" }
        }
      }
    // When the device terminates, cancel the above job, which may be stuck collecting a StateFlow
    // if we don't do this.
    device.scope.coroutineContext.job.invokeOnCompletion { job.cancel() }
  }

  /**
   * Offers the device to the plugins in priority order. It is expected to be claimed by the default
   * plugin if no other plugin claims it first, unless an exception occurs (e.g. the device goes
   * offline during claim).
   *
   * To simplify plugin implementation, we only offer one device to one plugin at a time; we may
   * want to relax this in the future to support identifying devices in parallel.
   */
  private suspend fun offer(device: ConnectedDevice): DeviceHandle? =
    offerMutex.withLock {
      provisioners.firstNotNullOfOrNull {
        try {
          it.claim(device)
        } catch (e: CancellationException) {
          throw e
        } catch (t: Throwable) {
          logger.warn(t, "Offering ${device.serialNumber}")
          null
        }
      }
    }

  /**
   * A composite list of the [CreateDeviceAction]s from all plugins that support device creation.
   */
  fun createDeviceActions(): List<CreateDeviceAction> =
    provisioners.mapNotNull { it.createDeviceAction }

  /**
   * A composite list of the [CreateDeviceTemplateAction]s from all plugins that support template
   * creation.
   */
  fun createTemplateActions(): List<CreateDeviceTemplateAction> =
    provisioners.mapNotNull { it.createDeviceTemplateAction }

  /** Provides access to extensions, which may be defined and implemented in higher layers. */
  fun <T : Extension> extensions(extensionClass: Class<T>): List<T> =
    provisioners.mapNotNull { it.extension(extensionClass) }
}

inline fun <reified T : Extension> DeviceProvisioner.extensions() = extensions(T::class.java)
