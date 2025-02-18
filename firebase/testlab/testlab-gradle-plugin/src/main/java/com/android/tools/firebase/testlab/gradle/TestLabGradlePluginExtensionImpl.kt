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

package com.android.tools.firebase.testlab.gradle

import com.android.build.api.dsl.Device
import com.android.build.api.dsl.ManagedDevices
import com.google.firebase.testlab.gradle.ManagedDevice
import com.google.firebase.testlab.gradle.TestLabGradlePluginExtension
import com.google.firebase.testlab.gradle.TestOptions
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory

abstract class TestLabGradlePluginExtensionImpl
@Inject
constructor(objectFactory: ObjectFactory, devicesBlock: ManagedDevices) :
  TestLabGradlePluginExtension {

  override val managedDevices: NamedDomainObjectContainer<ManagedDevice> =
    objectFactory
      .domainObjectContainer(ManagedDevice::class.java, ManagedDeviceFactory(objectFactory))
      .apply {
        whenObjectAdded { device: ManagedDevice -> devicesBlock.devices.add(device) }
        whenObjectRemoved { device: ManagedDevice -> devicesBlock.devices.remove(device) }
      }

  init {
    devicesBlock.devices.apply {
      whenObjectAdded { device: Device ->
        if (device is ManagedDevice) {
          managedDevices.add(device)
        }
      }
      whenObjectRemoved { device: Device ->
        if (device is ManagedDevice) {
          managedDevices.remove(device)
        }
      }
    }
  }

  override val testOptions: TestOptions = objectFactory.newInstance(TestOptionsImpl::class.java)

  override fun testOptions(action: TestOptions.() -> Unit) {
    testOptions.action()
  }

  // Runtime only for groovy decorator to generate the closure based block.
  fun testOptions(action: Action<TestOptions>) {
    action.execute(testOptions)
  }
}
