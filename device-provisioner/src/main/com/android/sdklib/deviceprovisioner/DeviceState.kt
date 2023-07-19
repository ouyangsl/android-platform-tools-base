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

import com.android.adblib.ConnectedDevice
import com.android.adblib.deviceInfo
import com.google.common.base.Stopwatch
import java.time.Duration
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withTimeout

/** Identifies the ADB connection state of a provisionable device and its characteristics. */
sealed interface DeviceState {

  val properties: DeviceProperties

  /** Information about the current remote device reservation, if any. */
  val reservation: Reservation?

  /** The [ConnectedDevice] provided by adblib, if we are connected. */
  val connectedDevice: ConnectedDevice?
    get() = null

  /**
   * Indicates that the device is in the process of changing state; generally, this would correspond
   * to a spinner in the UI.
   */
  val isTransitioning: Boolean

  /**
   * A very short, user-visible summary of the state of the device, e.g. "Offline", "Starting up",
   * "Connecting", "Connected".
   */
  val status: String

  /**
   * If present, there is a problem with the device. Some operations may be unavailable. The
   * [RepairDeviceAction] may be able to resolve it.
   */
  val error: DeviceError?

  fun isOnline(): Boolean =
    connectedDevice?.deviceInfo?.deviceState == com.android.adblib.DeviceState.ONLINE

  open class Disconnected(
    override val properties: DeviceProperties,
    override val isTransitioning: Boolean,
    override val status: String,
    override val reservation: Reservation? = null,
    override val error: DeviceError? = null,
  ) : DeviceState {
    constructor(properties: DeviceProperties) : this(properties, false, "Offline")

    open fun copy(
      properties: DeviceProperties = this.properties,
      isTransitioning: Boolean = this.isTransitioning,
      status: String = this.status,
      reservation: Reservation? = this.reservation,
      error: DeviceError? = this.error,
    ) = Disconnected(properties, isTransitioning, status, reservation, error)
  }

  /**
   * The state of a device that is connected to ADB. The device may not be usable yet; most clients
   * will want to wait for it to be [online][isOnline].
   */
  open class Connected(
    override val properties: DeviceProperties,
    override val isTransitioning: Boolean,
    override val status: String,
    override val connectedDevice: ConnectedDevice,
    override val reservation: Reservation? = null,
    override val error: DeviceError? = null,
  ) : DeviceState {
    constructor(
      properties: DeviceProperties,
      connectedDevice: ConnectedDevice,
      reservation: Reservation? = null,
    ) : this(properties, false, "Connected", connectedDevice, reservation)

    open fun copy(
      properties: DeviceProperties = this.properties,
      isTransitioning: Boolean = this.isTransitioning,
      status: String = this.status,
      connectedDevice: ConnectedDevice = this.connectedDevice,
      reservation: Reservation? = this.reservation,
      error: DeviceError? = this.error,
    ) = Connected(properties, isTransitioning, status, connectedDevice, reservation, error)
  }
}

/** A problem with a device. */
interface DeviceError {
  enum class Severity {
    INFO,
    WARNING,
    ERROR
  }
  val severity: Severity

  /** User-visible representation of the device error. */
  val message: String
}

inline fun <R> DeviceState.ifOnline(block: (ConnectedDevice) -> R): R? =
  connectedDevice?.let { connectedDevice ->
    when (connectedDevice.deviceInfo.deviceState) {
      com.android.adblib.DeviceState.ONLINE -> block(connectedDevice)
      else -> null
    }
  }

class TimeoutTracker(private val duration: Duration) {
  private val stopwatch = Stopwatch.createStarted()

  fun isTimedOut() = stopwatch.elapsed() >= duration
}

/**
 * Utility method intended for advancing DeviceState to an intermediate state (e.g. Activating,
 * Deactivating), and then reverting back to the original state if it stays in that state for too
 * long.
 *
 * First, we conditionally and atomically update to the intermediate state using [updateState]: this
 * should return the new state given the current state, or null if the current state cannot be
 * updated (which aborts the entire operation).
 *
 * Then, we invoke [advanceAction]: this is an arbitrary action that should cause the state to
 * advance out of the intermediate state before the [timeout].
 *
 * We then listen for updates to the state: if the state advances before [timeout], we are done. If
 * the state does not advance before [timeout] or an exception is thrown by [advanceAction], the
 * original state is restored.
 *
 * This uses atomic compareAndSet operations to ensure that we do not clobber concurrent state
 * updates from elsewhere.
 *
 * @return true if we advanced to the final state, false if we failed to update to the intermediate
 *   state
 * @throws TimeoutCancellationException if we timed out before advancing past the intermediate state
 */
suspend fun <T> MutableStateFlow<T>.advanceStateWithTimeout(
  updateState: (T) -> T?,
  timeout: Duration,
  advanceAction: suspend () -> Unit
): Boolean {
  while (true) {
    val originalState = value
    val intermediateState = updateState(originalState) ?: return false
    if (compareAndSet(originalState, intermediateState)) {
      try {
        withTimeout(timeout.toMillis()) {
          advanceAction()
          takeWhile { it == intermediateState }.collect()
        }
        return true
      } catch (e: Exception) {
        if (!compareAndSet(intermediateState, originalState) && e is TimeoutCancellationException) {
          // This is unlikely, but it means we advanced to the final state right after cancellation.
          return true
        }
        throw e
      }
    }
  }
}
