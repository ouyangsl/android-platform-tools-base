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

package com.android.tools.lint.checks.studio

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category.Companion.CORRECTNESS
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.isKotlin
import com.intellij.psi.CommonClassNames.JAVA_LANG_STRING
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/** Flags certain APIs that are forbidden in our codebase. */
class ForbiddenStudioCallDetector : Detector(), SourceCodeScanner {

  companion object Issues {
    private val IMPLEMENTATION =
      Implementation(ForbiddenStudioCallDetector::class.java, Scope.JAVA_FILE_SCOPE)

    const val ADD_TO_STDLIB_PACKAGE_FQ_NAME = "org.jetbrains.kotlin.utils.addToStdlib"

    @JvmField
    val ADD_TO_STDLIB_USAGE =
      Issue.create(
        id = "AddToStdlibUsage",
        briefDescription = "Do not use `addToStdlib`",
        explanation = "The `addToStdlib` package is unstable and should be avoided.",
        category = CORRECTNESS,
        severity = Severity.WARNING,
        platforms = STUDIO_PLATFORMS,
        implementation = IMPLEMENTATION,
      )

    @JvmField
    val INTERN =
      Issue.create(
        id = "NoInterning",
        briefDescription = "Do not intern strings",
        explanation =
          """
                Strings should not be interned; you are better off managing \
                your own string cache.
                """,
        category = CORRECTNESS,
        severity = Severity.ERROR,
        platforms = STUDIO_PLATFORMS,
        implementation = IMPLEMENTATION,
        //noinspection LintImplUnexpectedDomain
        moreInfo = "https://shipilev.net/jvm/anatomy-quarks/10-string-intern/",
      )

    @JvmField
    val FILES_COPY =
      Issue.create(
        id = "NoNioFilesCopy",
        briefDescription = "Do not use `java.nio.file.Files.copy(Path, Path)`",
        explanation =
          """
                `java.nio.file.Files.copy(Path, Path)` propagates the readonly bit \
                on Windows, this can result in a file that can't be overwritten the \
                next time. Instead, use `FileUtils.copyFile(Path, Path)` or Kotlin's \
                `File#copyTo(File)`.
                """,
        category = CORRECTNESS,
        severity = Severity.ERROR,
        platforms = STUDIO_PLATFORMS,
        implementation = IMPLEMENTATION,
        moreInfo = "https://issuetracker.google.com/182063560",
      )

    @JvmField
    val MOCKITO_WHEN =
      Issue.create(
        id = "MockitoWhen",
        briefDescription = "Do not use Mockito's `when` from Kotlin",
        explanation =
          """
                Using Mockito's `when` from Kotlin requires you to surround the method \
                call in backticks, since `when` is a hard keyword in Kotlin. Instead, use \
                the `whenever` extension method.
                """,
        category = CORRECTNESS,
        enabledByDefault = false,
        severity = Severity.ERROR,
        platforms = STUDIO_PLATFORMS,
        implementation = IMPLEMENTATION,
      )

    @JvmField
    val ADD_DEPENDENCY =
      Issue.create(
        id = "AddDependencyUsage",
        briefDescription = "Consider using DependenciesHelper for adding dependencies",
        explanation =
          """
            Often adding artifacts or plugins to project require changing catalog file. \
            There are some specific cases when catalog should be omitted. All this logic \
            lives inside `DependenciesHelper` so prefer using it instead of adding artifact \
            (with `DependenciesModel.addArtifact/addPlatformArtifact`) or \
            plugin (with `PluginsModel.applyPlugin`) directly.
                """,
        category = CORRECTNESS,
        enabledByDefault = false,
        severity = Severity.ERROR,
        platforms = STUDIO_PLATFORMS,
        implementation = IMPLEMENTATION,
      )
  }

  override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler {
    return object : UElementHandler() {
      override fun visitCallExpression(node: UCallExpression) {
        val resolved = node.resolve() ?: return
        if (
          context.evaluator.getPackage(resolved)?.qualifiedName == ADD_TO_STDLIB_PACKAGE_FQ_NAME
        ) {
          context.report(
            ADD_TO_STDLIB_USAGE,
            node,
            context.getCallLocation(node, includeReceiver = false, includeArguments = true),
            "Avoid using methods from the unstable `addToStdlib` package",
          )
        }
      }
    }
  }

  override fun getApplicableMethodNames(): List<String> =
    listOf("intern", "copy", "when", "addArtifact", "applyPlugin", "addPlatformArtifact")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    // String#intern
    if (method.name == "intern" && context.evaluator.isMemberInClass(method, JAVA_LANG_STRING)) {
      context.report(
        INTERN,
        node,
        context.getCallLocation(node, includeReceiver = false, includeArguments = true),
        "Do not intern strings; if reusing strings is truly necessary build a local cache",
      )
    }
    // Files#copy
    if (
      method.name == "copy" &&
        method.isVarArgs() &&
        context.evaluator.isMemberInClass(method, "java.nio.file.Files") &&
        context.evaluator.parameterHasType(method, 0, "java.nio.file.Path") &&
        context.evaluator.parameterHasType(method, 1, "java.nio.file.Path")
    ) {
      context.report(
        FILES_COPY,
        node,
        context.getCallLocation(node, includeReceiver = false, includeArguments = true),
        "Do not use `java.nio.file.Files.copy(Path, Path)`. " +
          "Instead, use `FileUtils.copyFile(Path, Path)` or Kotlin's `File#copyTo(File)`",
      )
    }

    // Mockito#when
    if (
      method.name == "when" &&
        isKotlin(node.lang) &&
        (context.evaluator.isMemberInClass(method, "org.mockito.Mockito") ||
          context.evaluator.isMemberInClass(method, "org.mockito.stubbing.Stubber"))
    ) {
      val fix =
        fix()
          .replace()
          .name("Use `whenever`")
          .range(context.getCallLocation(node, includeReceiver = true, includeArguments = false))
          .all()
          .with("whenever")
          .imports("org.mockito.kotlin.whenever")
          .build()
      context.report(
        MOCKITO_WHEN,
        node,
        context.getCallLocation(node, includeReceiver = false, includeArguments = true),
        "Do not use `Mockito.when` from Kotlin; use `org.mockito.kotlin.whenever` instead",
        fix,
      )
    }
    // DependenciesModel#addArtifact/addPlatformArtifact
    if (
      (method.name == "addArtifact" || method.name == "addPlatformArtifact") &&
        context.evaluator.isMemberInClass(
          method,
          "com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel",
        )
    ) {
      context.report(
        ADD_DEPENDENCY,
        node,
        context.getCallLocation(node, includeReceiver = false, includeArguments = true),
        "Do not use `addArtifact` or `addPlatformArtifact`, prefer `DependenciesHelper`",
      )
    }
    // PluginsModel#applyPlugin
    if (
      (method.name == "applyPlugin" || method.name == "addPlatformArtifact") &&
        context.evaluator.isMemberInClass(
          method,
          "com.android.tools.idea.gradle.dsl.api.settings.PluginsModel",
        )
    ) {
      context.report(
        ADD_DEPENDENCY,
        node,
        context.getCallLocation(node, includeReceiver = false, includeArguments = true),
        "Do not use `PluginsModel.applyPlugin`, prefer `DependenciesHelper`",
      )
    }
  }
}
