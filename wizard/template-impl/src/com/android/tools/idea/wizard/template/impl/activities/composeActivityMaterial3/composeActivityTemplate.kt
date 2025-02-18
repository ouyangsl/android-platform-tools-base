/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.idea.wizard.template.impl.activities.composeActivityMaterial3

import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.CheckBoxWidget
import com.android.tools.idea.wizard.template.Constraint.CLASS
import com.android.tools.idea.wizard.template.Constraint.KOTLIN_FUNCTION
import com.android.tools.idea.wizard.template.Constraint.NONEMPTY
import com.android.tools.idea.wizard.template.Constraint.UNIQUE
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.LanguageWidget
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageNameWidget
import com.android.tools.idea.wizard.template.TemplateConstraint
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.booleanParameter
import com.android.tools.idea.wizard.template.impl.defaultPackageNameParameter
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

val composeActivityMaterial3Template
  get() = template {
    name = "Empty Activity"
    description = "Create a new empty activity with Jetpack Compose"
    minApi = 21
    constraints = listOf(
        TemplateConstraint.AndroidX, TemplateConstraint.Kotlin, TemplateConstraint.Material3,
        TemplateConstraint.Compose)

    category = Category.Compose
    formFactor = FormFactor.Mobile
    screens = listOf(WizardUiContext.ActivityGallery, WizardUiContext.MenuEntry, WizardUiContext.NewProject, WizardUiContext.NewModule)

    val activityClass = stringParameter {
      name = "Activity Name"
      default = "MainActivity"
      help = "The name of the activity class to create"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      loggable = true
    }

    val packageName = defaultPackageNameParameter

    val isLauncher = booleanParameter {
      name = "Launcher Activity"
      default = false
      help = "If true, this activity will have a CATEGORY_LAUNCHER intent filter, making it visible in the launcher"
    }

    val greeting = stringParameter {
      name = "Greeting function name"
      default = "Greeting"
      help = "Used for deduplication"
      visible = { false }
      constraints = listOf(UNIQUE, KOTLIN_FUNCTION)
      loggable = true
    }

    val defaultPreview = stringParameter {
      name = "Default Preview function name"
      default = "${greeting.value}Preview"
      help = "Used for deduplication"
      visible = { false }
      constraints = listOf(UNIQUE, KOTLIN_FUNCTION)
      loggable = true
    }

    widgets(
      TextFieldWidget(activityClass),
      PackageNameWidget(packageName),
      CheckBoxWidget(isLauncher),
      // Invisible widgets to pass data
      TextFieldWidget(greeting),
      TextFieldWidget(defaultPreview),
      LanguageWidget()
    )

    thumb { File("compose-activity-material3").resolve("template_compose_empty_activity_material3.png") }

    recipe = { data: TemplateData ->
      composeActivityRecipe(data as ModuleTemplateData, activityClass.value, packageName.value, isLauncher.value,
                            greeting.value, defaultPreview.value)
    }
  }
