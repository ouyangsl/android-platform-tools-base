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

package com.android.tools.idea.wizard.template.impl.activities.tabbedActivity.src.app_package

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.getMaterialComponentName
import com.android.tools.idea.wizard.template.escapeKotlinIdentifier
import com.android.tools.idea.wizard.template.impl.activities.common.findViewById
import com.android.tools.idea.wizard.template.impl.activities.common.importViewBindingClass
import com.android.tools.idea.wizard.template.impl.activities.common.layoutToViewBindingClass
import com.android.tools.idea.wizard.template.renderIf

fun tabsActivityKt(
  activityClass: String,
  layoutName: String,
  packageName: String,
  applicationPackage: String?,
  useAndroidX: Boolean,
  isViewBindingSupported: Boolean
): String {

  val contentViewBlock = if (isViewBindingSupported) """
     binding = ${layoutToViewBindingClass(layoutName)}.inflate(layoutInflater)
     setContentView(binding.root)
  """ else "setContentView(R.layout.$layoutName)"

  return """package ${escapeKotlinIdentifier(packageName)}

import android.os.Bundle
import ${getMaterialComponentName("android.support.design.widget.FloatingActionButton", useAndroidX)}
import ${getMaterialComponentName("android.support.design.widget.Snackbar", useAndroidX)}
import ${getMaterialComponentName("android.support.design.widget.TabLayout", useAndroidX)}
import ${getMaterialComponentName("android.support.v4.view.ViewPager", useAndroidX)}
import ${getMaterialComponentName("android.support.v7.app.AppCompatActivity", useAndroidX)}
import android.view.Menu
import android.view.MenuItem
import ${packageName}.ui.main.SectionsPagerAdapter
${importViewBindingClass(isViewBindingSupported, packageName, applicationPackage, layoutName, Language.Kotlin)}

class ${activityClass} : AppCompatActivity() {

${renderIf(isViewBindingSupported) {"""
    private lateinit var binding: ${layoutToViewBindingClass(layoutName)}
"""}}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        $contentViewBlock
        val sectionsPagerAdapter = SectionsPagerAdapter(this, supportFragmentManager)
        val viewPager: ViewPager = ${findViewById(
          Language.Kotlin,
          isViewBindingSupported = isViewBindingSupported,
          id = "view_pager")}
        viewPager.adapter = sectionsPagerAdapter
        val tabs: TabLayout = ${findViewById(
          Language.Kotlin,
          isViewBindingSupported = isViewBindingSupported,
          id = "tabs")}
        tabs.setupWithViewPager(viewPager)
        val fab: FloatingActionButton = ${findViewById(
          Language.Kotlin,
          isViewBindingSupported = isViewBindingSupported,
          id = "fab")}

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null)
                    .setAnchorView(R.id.fab).show()
        }
    }
}"""
}
