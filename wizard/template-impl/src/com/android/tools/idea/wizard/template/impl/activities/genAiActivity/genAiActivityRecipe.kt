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
package com.android.tools.idea.wizard.template.impl.activities.genAiActivity

import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.addComposeDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.addSecretsGradlePlugin
import com.android.tools.idea.wizard.template.impl.activities.common.generateManifest
import com.android.tools.idea.wizard.template.impl.activities.composeActivityMaterial3.res.values.themesXml
import com.android.tools.idea.wizard.template.impl.activities.composeActivityMaterial3.src.app_package.ui.colorKt
import com.android.tools.idea.wizard.template.impl.activities.composeActivityMaterial3.src.app_package.ui.themeKt
import com.android.tools.idea.wizard.template.impl.activities.composeActivityMaterial3.src.app_package.ui.typeKt
import com.android.tools.idea.wizard.template.impl.activities.genAiActivity.res.values.stringsXml
import com.android.tools.idea.wizard.template.impl.activities.genAiActivity.src.app_package.bakingScreen
import com.android.tools.idea.wizard.template.impl.activities.genAiActivity.src.app_package.mainActivityKt
import com.android.tools.idea.wizard.template.impl.activities.genAiActivity.src.app_package.uiState
import com.android.tools.idea.wizard.template.impl.activities.genAiActivity.src.app_package.bakingViewModelKt
import java.io.File

fun RecipeExecutor.genAiActivityRecipe(
  moduleData: ModuleTemplateData,
  activityClass: String,
  packageName: String,
  apiKey: String,
) {
  val (_, srcOut, resOut, _) = moduleData
  addAllKotlinDependencies(moduleData)

  addDependency(mavenCoordinate = "androidx.lifecycle:lifecycle-runtime-ktx:+")
  addDependency(mavenCoordinate = "androidx.lifecycle:lifecycle-viewmodel-compose:+")
  addDependency(mavenCoordinate = "androidx.activity:activity-compose:+")

  // Add Compose dependencies, using the BOM to set versions
  addComposeDependencies(moduleData)

  addDependency(mavenCoordinate = "androidx.compose.material3:material3")

  addDependency(mavenCoordinate = "com.google.ai.client.generativeai:generativeai:0.9.0")
  addSecretsGradlePlugin()

  generateManifest(
    moduleData = moduleData,
    activityClass = activityClass,
    activityThemeName = moduleData.themesData.main.name,
    packageName = packageName,
    isLauncher = true,
    hasNoActionBar = true,
    generateActivityTitle = true
  )

  copy(File("genai-activity").resolve("drawable"), resOut.resolve("drawable"))

  mergeXml(
    themesXml(themeName = moduleData.themesData.main.name),
    resOut.resolve("values/themes.xml")
  )
  mergeXml(stringsXml(), resOut.resolve("values/strings.xml"))

  val themeName = "${moduleData.themesData.appName}Theme"

  save(mainActivityKt(activityClass, packageName, themeName), srcOut.resolve("${activityClass}.kt"))
  save(bakingViewModelKt(packageName), srcOut.resolve("BakingViewModel.kt"))
  save(bakingScreen(packageName), srcOut.resolve("BakingScreen.kt"))
  save(uiState(packageName), srcOut.resolve("UiState.kt"))

  val uiThemeFolder = "ui/theme"
  save(colorKt(packageName), srcOut.resolve("$uiThemeFolder/Color.kt"))
  save(themeKt(packageName, themeName), srcOut.resolve("$uiThemeFolder/Theme.kt"))
  save(typeKt(packageName), srcOut.resolve("$uiThemeFolder/Type.kt"))

  append("apiKey=$apiKey", moduleData.projectTemplateData.rootDir.resolve("local.properties"))

  setJavaKotlinCompileOptions(true)
  setBuildFeature("compose", true)
  // Required in Gradle 8+ for generating the BuildConfig class used by the secrets plugin
  setBuildFeature("buildConfig", true)

  open(srcOut.resolve("${activityClass}.kt"))
}
