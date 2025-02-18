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
package com.android.tools.idea.wizard.template.impl.activities.basicActivity

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageName
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.impl.activities.basicActivity.res.layout.fragmentFirstLayout
import com.android.tools.idea.wizard.template.impl.activities.basicActivity.res.layout.fragmentSecondLayout
import com.android.tools.idea.wizard.template.impl.activities.basicActivity.res.layout.fragmentSimpleXml
import com.android.tools.idea.wizard.template.impl.activities.basicActivity.res.navigation.navGraphXml
import com.android.tools.idea.wizard.template.impl.activities.basicActivity.res.values.stringsXml
import com.android.tools.idea.wizard.template.impl.activities.basicActivity.src.basicActivityJava
import com.android.tools.idea.wizard.template.impl.activities.basicActivity.src.basicActivityKt
import com.android.tools.idea.wizard.template.impl.activities.basicActivity.src.firstFragmentJava
import com.android.tools.idea.wizard.template.impl.activities.basicActivity.src.firstFragmentKt
import com.android.tools.idea.wizard.template.impl.activities.basicActivity.src.secondFragmentJava
import com.android.tools.idea.wizard.template.impl.activities.basicActivity.src.secondFragmentKt
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.addMaterial3Dependency
import com.android.tools.idea.wizard.template.impl.activities.common.addViewBindingSupport
import com.android.tools.idea.wizard.template.impl.activities.common.generateAppBar
import com.android.tools.idea.wizard.template.impl.activities.common.generateManifest
import com.android.tools.idea.wizard.template.impl.activities.common.generateMaterial3Themes
import com.android.tools.idea.wizard.template.impl.activities.common.generateSimpleMenu
import com.android.tools.idea.wizard.template.layoutToFragment

fun RecipeExecutor.generateBasicActivity(
  moduleData: ModuleTemplateData,
  activityClass: String,
  layoutName: String,
  contentLayoutName: String,
  packageName: PackageName,
  menuName: String,
  isLauncher: Boolean,
  firstFragmentLayoutName: String,
  secondFragmentLayoutName: String,
  navGraphName: String
) {
  val (projectData, srcOut, resOut) = moduleData
  val appCompatVersion = moduleData.apis.appCompatVersion
  val useAndroidX = moduleData.projectTemplateData.androidXSupport

  if (projectData.language == Language.Kotlin) {
      addAllKotlinDependencies(moduleData)
  }
  addMaterial3Dependency()
  // Generate the themes for material3 to make sure the Activity created for a flavor has an
  // appropriate set of themes
  generateMaterial3Themes(moduleData.themesData.main.name, moduleData.resDir)

  // TODO: (b/272389296, b/272389537) put the xml in the values/themes.xml when minApi >= 23
  mergeXml(
      """
<resources xmlns:tools="http://schemas.android.com/tools">
  <style name="${moduleData.themesData.main.name}" parent="Base.${moduleData.themesData.main.name}">
    <!-- Transparent system bars for edge-to-edge. -->
    <item name="android:navigationBarColor">@android:color/transparent</item>
    <item name="android:statusBarColor">@android:color/transparent</item>
    <item name="android:windowLightStatusBar">?attr/isLightTheme</item>
  </style>
</resources>""", resOut.resolve("values-v23").resolve("themes.xml")
  )

  generateManifest(
    moduleData = moduleData,
    activityClass = activityClass,
    packageName = packageName,
    isLauncher = isLauncher,
    hasNoActionBar = true,
    activityThemeName = moduleData.themesData.main.name,
    generateActivityTitle = false)
  generateAppBar(
    moduleData, activityClass, packageName, contentLayoutName, layoutName, useAndroidX = useAndroidX, isMaterial3 = true
  )
  addViewBindingSupport(moduleData.viewBindingSupport, true)
  addDependency("com.android.support:appcompat-v7:$appCompatVersion.+")
  addDependency("com.android.support.constraint:constraint-layout:+")

  // navHostFragmentId needs to be unique, thus appending contentLayoutName since it's
  // guaranteed to be unique
  val navHostFragmentId = "nav_host_fragment_${contentLayoutName}"
  save(
      fragmentSimpleXml(
        navGraphName = navGraphName,
        navHostFragmentId = navHostFragmentId,
        useAndroidX = useAndroidX
      ),
      moduleData.resDir.resolve("layout/$contentLayoutName.xml")
  )
  if (moduleData.isNewModule) {
    generateSimpleMenu(packageName, activityClass, moduleData.resDir, menuName)
  }

  val ktOrJavaExt = projectData.language.extension
  val simpleActivityPath = srcOut.resolve("$activityClass.$ktOrJavaExt")
  val generateKotlin = projectData.language == Language.Kotlin
  val isViewBindingSupported = moduleData.viewBindingSupport.isViewBindingSupported()
  val simpleActivity = when (projectData.language) {
    Language.Java ->
      basicActivityJava(
        isNewProject = moduleData.isNewModule,
        applicationPackage = projectData.applicationPackage,
        packageName = packageName,
        useAndroidX = useAndroidX,
        activityClass = activityClass,
        layoutName = layoutName,
        menuName = menuName,
        navHostFragmentId = navHostFragmentId,
        isViewBindingSupported = isViewBindingSupported
      )
    Language.Kotlin ->
      basicActivityKt(
        isNewProject = moduleData.isNewModule,
        applicationPackage = projectData.applicationPackage,
        packageName = packageName,
        useAndroidX = useAndroidX,
        activityClass = activityClass,
        layoutName = layoutName,
        menuName = menuName,
        navHostFragmentId = navHostFragmentId,
        isViewBindingSupported = isViewBindingSupported
      )
  }

  save(simpleActivity, simpleActivityPath)

  val firstFragmentClass = layoutToFragment(firstFragmentLayoutName)
  val secondFragmentClass = layoutToFragment(secondFragmentLayoutName)
  val firstFragmentClassContent = when (projectData.language) {
    Language.Java -> firstFragmentJava(
      packageName = packageName,
      applicationPackage = projectData.applicationPackage,
      useAndroidX = useAndroidX,
      firstFragmentClass = firstFragmentClass,
      secondFragmentClass = secondFragmentClass,
      firstFragmentLayoutName = firstFragmentLayoutName,
      isViewBindingSupported = isViewBindingSupported
    )
    Language.Kotlin -> firstFragmentKt(
      packageName = packageName,
      applicationPackage = projectData.applicationPackage,
      firstFragmentClass = firstFragmentClass,
      secondFragmentClass = secondFragmentClass,
      firstFragmentLayoutName = firstFragmentLayoutName,
      isViewBindingSupported = isViewBindingSupported
    )
  }
  val secondFragmentClassContent = when (projectData.language) {
    Language.Java -> secondFragmentJava(
      packageName = packageName,
      applicationPackage = projectData.applicationPackage,
      useAndroidX = useAndroidX,
      firstFragmentClass = firstFragmentClass,
      secondFragmentClass = secondFragmentClass,
      secondFragmentLayoutName = secondFragmentLayoutName,
      isViewBindingSupported = isViewBindingSupported
    )
    Language.Kotlin -> secondFragmentKt(
      packageName = packageName,
      applicationPackage = projectData.applicationPackage,
      firstFragmentClass = firstFragmentClass,
      secondFragmentClass = secondFragmentClass,
      secondFragmentLayoutName = secondFragmentLayoutName,
      isViewBindingSupported = isViewBindingSupported,
      useAndroidX = useAndroidX
    )
  }
  val firstFragmentLayoutContent = fragmentFirstLayout(useAndroidX, firstFragmentClass)
  val secondFragmentLayoutContent = fragmentSecondLayout(useAndroidX, secondFragmentClass)
  save(firstFragmentClassContent, srcOut.resolve("$firstFragmentClass.$ktOrJavaExt"))
  save(secondFragmentClassContent, srcOut.resolve("$secondFragmentClass.$ktOrJavaExt"))
  save(firstFragmentLayoutContent, resOut.resolve("layout/$firstFragmentLayoutName.xml"))
  save(secondFragmentLayoutContent, resOut.resolve("layout/$secondFragmentLayoutName.xml"))

  val navGraphContent = navGraphXml(
    packageName = packageName,
    firstFragmentClass = firstFragmentClass,
    secondFragmentClass = secondFragmentClass,
    firstFragmentLayoutName = firstFragmentLayoutName,
    secondFragmentLayoutName = secondFragmentLayoutName,
    navGraphName = navGraphName
  )
  mergeXml(navGraphContent, resOut.resolve("navigation/${navGraphName}.xml"))
  mergeXml(stringsXml, resOut.resolve("values/strings.xml"))

  if (generateKotlin) {
    addDependency("android.arch.navigation:navigation-fragment-ktx:+")
    addDependency("android.arch.navigation:navigation-ui-ktx:+")
  }
  else {
    addDependency("android.arch.navigation:navigation-fragment:+")
    addDependency("android.arch.navigation:navigation-ui:+")
  }

  setJavaKotlinCompileOptions(projectData.language == Language.Kotlin)
  open(simpleActivityPath)

  open(resOut.resolve("layout/$contentLayoutName"))
  open(srcOut.resolve("$activityClass.$ktOrJavaExt"))
}
