/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.idea.wizard.template.impl.activities.googleMapsActivity

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.activityToLayout
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.addSecretsGradlePlugin
import com.android.tools.idea.wizard.template.impl.activities.common.addViewBindingSupport
import com.android.tools.idea.wizard.template.impl.activities.googleMapsActivity.res.layout.activityMapXml
import com.android.tools.idea.wizard.template.impl.activities.googleMapsActivity.res.values.stringsXml
import com.android.tools.idea.wizard.template.impl.activities.googleMapsActivity.src.app_package.mapActivityJava
import com.android.tools.idea.wizard.template.impl.activities.googleMapsActivity.src.app_package.mapActivityKt
import java.io.File

fun RecipeExecutor.googleMapsActivityRecipe(
  moduleData: ModuleTemplateData,
  activityClass: String,
  isLauncher: Boolean,
  layoutName: String,
  packageName: String
) {
  val (projectData, srcOut, resOut, manifestOut) = moduleData
  val appCompatVersion = moduleData.apis.appCompatVersion
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  val simpleName = activityToLayout(activityClass)
  addAllKotlinDependencies(moduleData)
  addViewBindingSupport(moduleData.viewBindingSupport, true)
  addSecretsGradlePlugin()

  addDependency("com.google.android.gms:play-services-maps:+", toBase = moduleData.isDynamic)
  addDependency("com.android.support:appcompat-v7:${appCompatVersion}.+")
  addDependency("com.android.support.constraint:constraint-layout:+")

  mergeXml(androidManifestXml(activityClass, isLauncher, moduleData.isLibrary, packageName, simpleName, moduleData.isNewModule),
           manifestOut.resolve("AndroidManifest.xml"))

  save(activityMapXml(activityClass, packageName), resOut.resolve("layout/${layoutName}.xml"))

  lateinit var finalResOut: File
  if (moduleData.isDynamic) {
    finalResOut = moduleData.baseFeature!!.resDir
  }
  else {
    finalResOut = resOut
  }

  mergeXml(stringsXml(activityClass, simpleName), finalResOut.resolve("values/strings.xml"))
  val isViewBindingSupported = moduleData.viewBindingSupport.isViewBindingSupported()
  val mapActivity = when (projectData.language) {
    Language.Java -> mapActivityJava(
      activityClass = activityClass,
      layoutName = layoutName,
      packageName = packageName,
      applicationPackage = projectData.applicationPackage,
      useAndroidX = useAndroidX,
      isViewBindingSupported = isViewBindingSupported)
    Language.Kotlin -> mapActivityKt(
      activityClass = activityClass,
      layoutName = layoutName,
      packageName = packageName,
      applicationPackage = projectData.applicationPackage,
      useAndroidX = useAndroidX,
      isViewBindingSupported = isViewBindingSupported)
  }
  save(mapActivity, srcOut.resolve("${activityClass}.${ktOrJavaExt}"))

  open(srcOut.resolve("${activityClass}.${ktOrJavaExt}"))

  /* Display the API key instructions. */
  open(manifestOut.resolve("AndroidManifest.xml"))
}
