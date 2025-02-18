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

package com.android.tools.idea.wizard.template.impl.other.automotiveMediaService

import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.renderIf

fun buildGradle(
  packageName: String,
  buildApiString: String?,
  generateKotlin: Boolean,
  minApi: String,
  targetApi: String,
  useAndroidX: Boolean
): String {
  val kotlinOptionsBlock = renderIf(generateKotlin) {
    """
    kotlinOptions {
        jvmTarget = '1.8'
    }
    """
  }

  return """
plugins {
    id 'com.android.library'
    ${renderIf(generateKotlin) {"    id 'org.jetbrains.kotlin.android'"}}
}
android {
    namespace '$packageName'
    compileSdkVersion ${buildApiString?.toIntOrNull() ?: "\"$buildApiString\""}

    defaultConfig {
        minSdkVersion ${minApi.toIntOrNull() ?: "\"$minApi\""}
        targetSdkVersion ${targetApi.toIntOrNull() ?: "\"$targetApi\""}

        testInstrumentationRunner "${getMaterialComponentName("android.support.test.runner.AndroidJUnitRunner", useAndroidX)}"
    }
    $kotlinOptionsBlock
}

"""
}
