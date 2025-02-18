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

package com.android.tools.firebase.testlab.gradle.device

import com.android.tools.firebase.testlab.gradle.ManagedDeviceImpl.Orientation
import com.android.tools.firebase.testlab.gradle.services.TestLabBuildService
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

abstract class DeviceTestRunInput :
  com.android.build.api.instrumentation.manageddevice.DeviceTestRunInput {

  @get:Input abstract val device: Property<String>

  @get:Input abstract val apiLevel: Property<Int>

  @get:Input abstract val orientation: Property<Orientation>

  @get:Input abstract val locale: Property<String>

  @get:Internal abstract val buildService: Property<TestLabBuildService>

  @get:Input abstract val numUniformShards: Property<Int>

  @get:PathSensitive(PathSensitivity.NONE)
  @get:InputFile
  abstract val extraDeviceUrlsFile: RegularFileProperty
}
