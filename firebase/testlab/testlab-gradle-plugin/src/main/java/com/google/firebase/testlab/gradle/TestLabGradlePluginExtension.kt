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

package com.google.firebase.testlab.gradle

import com.android.build.api.dsl.ManagedDevices
import org.gradle.api.Incubating
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.RegularFileProperty

/** A DSL for Firebase Test Lab Gradle plugin. */
@Incubating
interface TestLabGradlePluginExtension {
  /**
   * A path to a JSON file that contains service account credentials to access to a Firebase TestLab
   * project.
   */
  @get:Incubating val serviceAccountCredentials: RegularFileProperty

  /**
   * Convenience container for specifying FTL managed devices of the type [ManagedDevice].
   *
   * Any devices created as a part of this container are automatically added to the list of devices
   * contained in the [managedDevices Block][ManagedDevices.devices].
   */
  @get:Incubating val managedDevices: NamedDomainObjectContainer<ManagedDevice>

  /** A configuration block for test options. */
  @get:Incubating val testOptions: TestOptions

  @Incubating fun testOptions(action: TestOptions.() -> Unit)
}
