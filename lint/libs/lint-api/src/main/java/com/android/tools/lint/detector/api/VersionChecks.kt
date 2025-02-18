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
package com.android.tools.lint.detector.api

import com.android.AndroidXConstants
import com.android.SdkConstants
import com.android.sdklib.SdkVersionInfo
import com.android.support.AndroidxName
import com.android.tools.lint.client.api.AndroidPlatformAnnotations
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.ApiConstraint.Companion.above
import com.android.tools.lint.detector.api.ApiConstraint.Companion.atLeast
import com.android.tools.lint.detector.api.ApiConstraint.Companion.atMost
import com.android.tools.lint.detector.api.ApiConstraint.Companion.below
import com.android.tools.lint.detector.api.ApiConstraint.Companion.exactly
import com.android.tools.lint.detector.api.ApiConstraint.Companion.max
import com.android.tools.lint.detector.api.ApiConstraint.Companion.not
import com.android.tools.lint.detector.api.ApiConstraint.Companion.range
import com.android.tools.lint.detector.api.ExtensionSdk.Companion.ANDROID_SDK_ID
import com.android.tools.lint.detector.api.ExtensionSdk.Companion.ANDROID_SDK_ID_WITH_MINOR
import com.android.tools.lint.detector.api.UastLintUtils.Companion.getAnnotationLongValue
import com.android.tools.lint.detector.api.VersionChecks.SdkIntAnnotation.Companion.findSdkIntAnnotation
import com.android.utils.SdkUtils
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiParameterList
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UAnonymousClass
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UField
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UObjectLiteralExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.USwitchClauseExpression
import org.jetbrains.uast.USwitchClauseExpressionWithBody
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.UastPrefixOperator
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.isUastChildOf
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.skipParenthesizedExprUp
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.visitor.AbstractUastVisitor

private typealias ApiLevelLookup = (UElement) -> ApiLevel

/**
 * Helper for checking whether a given element is surrounded (or preceded!) by an API check using
 * SDK_INT (or other version checking utilities such as BuildCompat#isAtLeastN)
 */
class VersionChecks(
  private val client: LintClient,
  private val evaluator: JavaEvaluator,
  private val project: Project?,
) {
  companion object {
    /**
     * Maximum number of levels we'll check for surrounding version checks.
     *
     * The version checker searches for surrounding version checks, such as an if statement
     * surrounding the dangerous call.
     *
     * It also handles version utility functions, such as this:
     * ```
     * if (enabled() && isBiometricsAvailable()) {
     *     biometricsCall()
     * }
     * ```
     *
     * If `biometricsCall()` requires API level X, we'll go into both the `enabled()` call and the
     * `isBiometricsAvailable()` to see if they look like version checks -- for example `return
     * SDK_INT > 28`. And these methods can themselves make simple references to other version
     * checks.
     *
     * But we need to watch out to make sure we don't have unbounded recursion, so when we're
     * computing version constraints for a method call, we'll keep track of the call depth to ensure
     * that we aren't back in a cycle, or get lost in a pathologically deep hierarchy (which is
     * unlikely for a legitimate version checking utility function).
     *
     * Note that the depth here is only for calls and variable initializers; we don't increment the
     * count when we're searching upwards in the AST hierarchy (which will always be reasonably
     * bounded) or combining terms in a polyadic expression (also reasonably bounded).
     */
    private const val MAX_CALL_DEPTH = 50

    /**
     * The `SdkIntDetector` analyzes methods and looks for SDK_INT checks inside method bodies. If
     * it recognizes that something is a version check, it will record this as partial analysis
     * data. This mechanism needs an associated issue to tie the data to. We want to peek at this
     * data from the version checking utility (such that if we see "if (someFunction())" is actually
     * checking an SDK_INT result), but we cannot reference that detector's issue from here since
     * it's in a downstream dependency, lint-checks rather than lint-api. So instead, we've created
     * a special marker issue here (the "_" prefix in the issue id is a special prefix recognized by
     * lint as meaning it's not a real issue, also used by various tests), and the sdk int detector
     * will store its data using this issue id instead of its reporting issue.
     */
    @JvmField
    val SDK_INT_VERSION_DATA =
      Issue.create(
        id = "_ChecksSdkIntAtLeast",
        briefDescription = "Version Storage",
        explanation = "_",
        category = Category.LINT,
        severity = Severity.INFORMATIONAL,
        androidSpecific = true,
        // Not a real implementation
        implementation = Implementation(FakeDetector::class.java, Scope.EMPTY),
      )

    class FakeDetector : Detector() {
      override fun checkPartialResults(context: Context, partialResults: PartialResult) {}

      override fun filterIncident(context: Context, incident: Incident, map: LintMap) = false
    }

    const val SDK_INT = "SDK_INT"
    const val SDK_INT_FULL = "SDK_INT_FULL"
    private const val CHECKS_SDK_INT_AT_LEAST_NAME = "ChecksSdkIntAtLeast"
    const val CHECKS_SDK_INT_AT_LEAST_ANNOTATION = "androidx.annotation.ChecksSdkIntAtLeast"
    private const val PLATFORM_CHECKS_SDK_INT_AT_LEAST_ANNOTATION =
      "android.annotation.ChecksSdkIntAtLeast"
    private const val SDK_SUPPRESS_ANNOTATION = "android.support.test.filters.SdkSuppress"
    private const val ANDROIDX_SDK_SUPPRESS_ANNOTATION = "androidx.test.filters.SdkSuppress"
    private const val ROBO_ELECTRIC_CONFIG_ANNOTATION = "org.robolectric.annotation.Config"

    @JvmField
    val REQUIRES_API_ANNOTATION =
      AndroidxName.of(AndroidXConstants.SUPPORT_ANNOTATIONS_PREFIX, "RequiresApi")
    const val REQUIRES_EXTENSION_ANNOTATION = "androidx.annotation.RequiresExtension"

    /** SDK int method used by the data binding compiler. */
    private const val GET_BUILD_SDK_INT = "getBuildSdkInt"

    // Used by ClassVerificationFailureDetector.kt in AndroidX
    @Deprecated("Use SdkVersion instead")
    @JvmStatic
    fun codeNameToApi(text: String): Int {
      val dotIndex = text.lastIndexOf('.')
      val buildCode =
        if (dotIndex != -1) {
          text.substring(dotIndex + 1)
        } else {
          text
        }
      return SdkVersionInfo.getApiByBuildCode(buildCode, true)
    }

    @JvmOverloads
    @JvmStatic
    fun getTargetApiAnnotation(
      evaluator: JavaEvaluator,
      scope: UElement?,
      isApiLevelAnnotation: (String) -> Boolean = Companion::isTargetAnnotation,
    ): Pair<UAnnotation?, ApiConstraint?> {
      var current = scope
      while (current != null) {
        if (current is UAnnotated) {
          //noinspection AndroidLintExternalAnnotations
          for (annotation in current.uAnnotations) {
            val target = getTargetApiForAnnotation(annotation, isApiLevelAnnotation)
            if (target != null) {
              return annotation to target
            }
          }
        }
        if (current is UFile) {
          // Also consult any package annotations
          val pkg = evaluator.getPackage(current.javaPsi ?: current.sourcePsi)
          if (pkg != null) {
            for (psiAnnotation in pkg.annotations) {
              val annotation =
                UastFacade.convertElement(psiAnnotation, null) as? UAnnotation ?: continue
              val target = getTargetApiForAnnotation(annotation, isApiLevelAnnotation)
              if (target != null) {
                return annotation to target
              }
            }
          }

          break
        }
        current = current.uastParent
      }

      return NO_ANNOTATION_FOUND
    }

    /** Return value for no annotation found from [getTargetApiAnnotation] */
    private val NO_ANNOTATION_FOUND: Pair<UAnnotation?, ApiConstraint?> = null to null

    fun isTargetAnnotation(fqcn: String): Boolean {
      return fqcn == SdkConstants.FQCN_TARGET_API ||
        isRequiresApiAnnotation(fqcn) ||
        fqcn == SDK_SUPPRESS_ANNOTATION ||
        fqcn == ANDROIDX_SDK_SUPPRESS_ANNOTATION ||
        fqcn == ROBO_ELECTRIC_CONFIG_ANNOTATION ||
        fqcn == SdkConstants.TARGET_API || // with missing imports
        fqcn.startsWith(AndroidPlatformAnnotations.PLATFORM_ANNOTATIONS_PREFIX) &&
          isTargetAnnotation(AndroidPlatformAnnotations.toAndroidxAnnotation(fqcn))
    }

    fun isRequiresApiAnnotation(fqcn: String): Boolean {
      return REQUIRES_API_ANNOTATION.isEquals(fqcn) ||
        fqcn == "RequiresApi" || // With missing imports
        REQUIRES_EXTENSION_ANNOTATION == fqcn ||
        fqcn.startsWith(AndroidPlatformAnnotations.PLATFORM_ANNOTATIONS_PREFIX) &&
          isRequiresApiAnnotation(AndroidPlatformAnnotations.toAndroidxAnnotation(fqcn))
    }

    fun getTargetApiForAnnotation(
      annotation: UAnnotation,
      isApiLevelAnnotation: (String) -> Boolean,
    ): ApiConstraint? {
      val fqcn = annotation.qualifiedName
      if (fqcn != null && isApiLevelAnnotation(fqcn)) {
        if (fqcn == REQUIRES_EXTENSION_ANNOTATION) {
          val sdkId =
            getAnnotationLongValue(annotation, "extension", ANDROID_SDK_ID.toLong()).toInt()
          val value = getAnnotationLongValue(annotation, "version", 0).toInt()
          return atLeast(value, sdkId)
        }
        val sdkId = ANDROID_SDK_ID
        val attributeList = annotation.attributeValues
        for (attribute in attributeList) {
          if (
            fqcn == ROBO_ELECTRIC_CONFIG_ANNOTATION ||
              fqcn == SDK_SUPPRESS_ANNOTATION ||
              fqcn == ANDROIDX_SDK_SUPPRESS_ANNOTATION
          ) {
            val name = attribute.name
            if (name == null || !(name.startsWith("minSdk") || name == "codeName")) {
              continue
            }
          }
          val expression = attribute.expression
          if (expression is ULiteralExpression) {
            val value = expression.value
            if (value is Int) {
              return ApiLevel.getMinConstraint(value, sdkId)
            } else if (value is String) {
              return ApiLevel.getMinConstraint(value, sdkId)
            }
          } else {
            val constant = ConstantEvaluator.evaluate(null, expression) as? Int
            if (constant != null) {
              return ApiLevel.getMinConstraint(constant, sdkId)
            } else if (expression is UReferenceExpression) {
              val name = expression.resolvedName
              if (name != null) {
                return ApiLevel.getMinConstraint(name, sdkId)
              }
            } else {
              return ApiLevel.getMinConstraint(expression.asSourceString(), sdkId)
            }
          }
        }
      } else if (fqcn == null) {
        // Work around bugs in UAST type resolution for file annotations:
        // parse the source string instead.
        val psi = annotation.sourcePsi ?: return null
        if (psi is PsiCompiledElement) {
          return null
        }
        val text = psi.text
        val start = text.indexOf('(')
        if (start == -1) {
          return null
        }
        val colon = text.indexOf(':') // skip over @file: etc
        val annotationString = text.substring(if (colon < start) colon + 1 else 0, start)
        if (isApiLevelAnnotation(annotationString)) {
          val end = text.indexOf(')', start + 1)
          if (end != -1) {
            var name = text.substring(start + 1, end)
            // Strip off attribute name and qualifiers, e.g.
            //   @RequiresApi(api = Build.VERSION.O) -> O
            var index = name.indexOf('=')
            if (index != -1) {
              name = name.substring(index + 1).trim()
            }
            index = name.indexOf('.')
            if (index != -1) {
              name = name.substring(index + 1)
            }
            return ApiLevel.getMinConstraint(name, ANDROID_SDK_ID)
          }
        }
      }

      return null
    }

    @Deprecated(
      "Use the ApiConstraint version instead",
      ReplaceWith(
        "isWithinVersionCheckConditional(context, element, ApiConstraint.get(api), lowerBound)"
      ),
    )
    @JvmStatic
    @JvmOverloads
    fun isWithinVersionCheckConditional(
      context: JavaContext,
      element: UElement,
      api: Int,
      lowerBound: Boolean = true,
    ): Boolean {
      return isWithinVersionCheckConditional(context, element, ApiConstraint.get(api), lowerBound)
    }

    @JvmStatic
    @JvmOverloads
    fun isWithinVersionCheckConditional(
      context: JavaContext,
      element: UElement,
      api: ApiConstraint,
      lowerBound: Boolean = true,
    ): Boolean {
      val client = context.client
      val evaluator = context.evaluator
      val project = context.project
      val check = VersionChecks(client, evaluator, project)
      val constraint =
        check.getWithinVersionCheckConditional(element = element, apiLookup = null, depth = 0)
          ?: return false
      return if (lowerBound) {
        constraint.isAtLeast(api)
      } else {
        constraint.not().isAtLeast(api)
      }
    }

    @JvmStatic
    fun getOuterVersionCheckConstraint(context: JavaContext, element: UElement): ApiConstraint? {
      val client = context.client
      val evaluator = context.evaluator
      val project = context.project
      val check = VersionChecks(client, evaluator, project)
      return check.getWithinVersionCheckConditional(element = element, apiLookup = null, depth = 0)
    }

    @Deprecated(
      "Use the ApiConstraint version instead",
      ReplaceWith(
        "isWithinVersionCheckConditional(client, evaluator, element, ApiConstraint.get(api), lowerBound)"
      ),
    )
    @JvmStatic
    @JvmOverloads
    fun isWithinVersionCheckConditional(
      client: LintClient,
      evaluator: JavaEvaluator,
      element: UElement,
      api: Int,
      lowerBound: Boolean = true,
    ): Boolean {
      return isWithinVersionCheckConditional(
        client,
        evaluator,
        element,
        ApiConstraint.get(api),
        lowerBound,
      )
    }

    @JvmStatic
    @JvmOverloads
    fun isWithinVersionCheckConditional(
      client: LintClient,
      evaluator: JavaEvaluator,
      element: UElement,
      api: ApiConstraint,
      lowerBound: Boolean = true,
    ): Boolean {
      val check = VersionChecks(client, evaluator, null)
      val constraint =
        check.getWithinVersionCheckConditional(element = element, apiLookup = null, depth = 0)
          ?: return false
      return if (lowerBound) {
        constraint.isAtLeast(api)
      } else {
        constraint.not().isAtLeast(api)
      }
    }

    @Deprecated(
      "Use the ApiConstraint version instead",
      ReplaceWith("isPrecededByVersionCheckExit(context, element, ApiConstraint.get(api))"),
    )
    @JvmStatic
    fun isPrecededByVersionCheckExit(context: JavaContext, element: UElement, api: Int): Boolean {
      return isPrecededByVersionCheckExit(context, element, ApiConstraint.get(api))
    }

    @JvmStatic
    fun isPrecededByVersionCheckExit(
      context: JavaContext,
      element: UElement,
      api: ApiConstraint,
    ): Boolean {
      val client = context.client
      val evaluator = context.evaluator
      val project = context.project
      return isPrecededByVersionCheckExit(client, evaluator, element, api, project)
    }

    @Deprecated(
      "Use the ApiConstraint version instead",
      ReplaceWith(
        "isPrecededByVersionCheckExit(client, evaluator, element, ApiConstraint.get(api), project)"
      ),
    )
    @JvmStatic
    fun isPrecededByVersionCheckExit(
      client: LintClient,
      evaluator: JavaEvaluator,
      element: UElement,
      api: Int,
      project: Project? = null,
    ): Boolean {
      return isPrecededByVersionCheckExit(
        client,
        evaluator,
        element,
        ApiConstraint.get(api),
        project,
      )
    }

    @JvmStatic
    fun isPrecededByVersionCheckExit(
      client: LintClient,
      evaluator: JavaEvaluator,
      element: UElement,
      api: ApiConstraint,
      project: Project? = null,
    ): Boolean {
      val check = VersionChecks(client, evaluator, project)
      var prev = element
      var current: UExpression? = prev.parentExpression()
      while (current != null) {
        val visitor = check.VersionCheckWithExitFinder(prev, api)
        current.accept(visitor)
        if (visitor.found()) {
          return true
        }
        prev = current
        current = current.parentExpression()

        // TODO: what about lambdas?
      }
      return false
    }

    /**
     * Returns the parent UAST expressions if any.
     *
     * Normally, this terminates at the method or class level, but as a special case, we allow
     * jumping out through nested anonymous class methods.
     */
    private fun UElement.parentExpression(): UExpression? =
      this.getParentOfType(UExpression::class.java, true)

    /**
     * If the given [element] represents a version lookup, such as SdkInt or getExtensionVersion(),
     * returns the corresponding SDK id, or -1 if it's not an SDK_INT/getExtensionVersion lookup.
     */
    private fun getSdkVersionLookup(
      element: UElement?,
      client: LintClient,
      evaluator: JavaEvaluator,
      project: Project?,
    ): Int {
      if (element is UReferenceExpression) {
        val resolvedName = element.resolvedName
        if (SDK_INT == resolvedName) {
          return ANDROID_SDK_ID
        } else if (SDK_INT_FULL == resolvedName) {
          return ANDROID_SDK_ID_WITH_MINOR
        }
        val selector = element.findSelector()
        if (selector !== element) {
          return getSdkVersionLookup(selector, client, evaluator, project)
        }
        val resolved = element.resolve()
        if (resolved is ULocalVariable) {
          val initializer = resolved.uastInitializer
          if (initializer != null) {
            return getSdkVersionLookup(initializer, client, evaluator, project)
          }
        } else if (resolved is PsiVariable) {
          val initializer = resolved.initializer
          if (initializer != null) {
            initializer.toUElement()?.let {
              return getSdkVersionLookup(it, client, evaluator, project)
            }
          }

          if (resolved is PsiField) {
            SdkIntAnnotation.get(resolved)?.let {
              return it.sdkId
            }
            findChecksSdkInferredAnnotation(resolved, client, evaluator, project)?.let {
              return it.sdkId
            }
          }
        }
      } else if (element is UCallExpression) {
        val methodName = getMethodName(element)
        if (methodName == GET_BUILD_SDK_INT) {
          return ANDROID_SDK_ID
        } else if (methodName == "getExtensionVersion") {
          element.valueArguments.firstOrNull()?.let { firstArg ->
            ConstantEvaluator.evaluate(null, firstArg)?.let { constant ->
              return (constant as? Number)?.toInt() ?: -1
            }
          }
        } // else look inside the body?
      } else if (element is UParenthesizedExpression) {
        return getSdkVersionLookup(element.expression, client, evaluator, project)
      }
      return -1
    }

    /**
     * When we come across SDK_INT comparisons in library, we'll store that as an
     * implied @ChecksSdkIntAtLeast annotation (to match the existing support for
     * actual @ChecksSdkIntAtLeast annotations). Here, when looking up version checks we'll check
     * the given method or field and see if we've stashed any implied version checks when analyzing
     * the dependencies.
     */
    private fun findChecksSdkInferredAnnotation(
      owner: PsiModifierListOwner,
      client: LintClient,
      evaluator: JavaEvaluator,
      project: Project?,
    ): SdkIntAnnotation? {
      if (!client.supportsPartialAnalysis()) {
        return null
      }
      if (project == null || owner !is PsiCompiledElement) {
        return null
      }
      val annotation =
        when (owner) {
          is PsiMethod -> findSdkIntAnnotation(client, evaluator, project, owner) ?: return null
          is PsiField -> findSdkIntAnnotation(client, evaluator, project, owner) ?: return null
          else -> return null
        }

      return annotation
    }

    /** Returns the actual API constraint enforced by the given SDK_INT comparison. */
    @JvmStatic
    fun getVersionCheckConditional(
      binary: UBinaryExpression,
      client: LintClient,
      evaluator: JavaEvaluator,
      project: Project?,
      apiLevelLookup: ApiLevelLookup? = null,
    ): ApiConstraint? {
      var tokenType = binary.operator
      if (
        tokenType === UastBinaryOperator.GREATER ||
          tokenType === UastBinaryOperator.GREATER_OR_EQUALS ||
          tokenType === UastBinaryOperator.LESS_OR_EQUALS ||
          tokenType === UastBinaryOperator.LESS ||
          tokenType === UastBinaryOperator.EQUALS ||
          tokenType === UastBinaryOperator.IDENTITY_EQUALS ||
          tokenType === UastBinaryOperator.NOT_EQUALS ||
          tokenType === UastBinaryOperator.IDENTITY_NOT_EQUALS
      ) {
        val left = binary.leftOperand
        val level: ApiLevel
        val right: UExpression
        var sdkId = getSdkVersionLookup(left, client, evaluator, project)
        if (sdkId == -1) {
          right = binary.rightOperand
          sdkId = getSdkVersionLookup(right, client, evaluator, project)
          if (sdkId == -1) {
            return null
          }
          tokenType = tokenType.flip() ?: tokenType
          level = getApiLevel(left, apiLevelLookup)
        } else {
          right = binary.rightOperand
          level = getApiLevel(right, apiLevelLookup)
        }

        if (level.isValid()) {
          if (sdkId == ANDROID_SDK_ID_WITH_MINOR) {
            sdkId = ANDROID_SDK_ID
          }
          when (tokenType) {
            UastBinaryOperator.GREATER_OR_EQUALS -> return atLeast(level, sdkId)
            UastBinaryOperator.GREATER -> return above(level, sdkId)
            UastBinaryOperator.LESS_OR_EQUALS -> return atMost(level, sdkId)
            UastBinaryOperator.LESS -> return below(level, sdkId)
            UastBinaryOperator.EQUALS,
            UastBinaryOperator.IDENTITY_EQUALS -> return exactly(level, sdkId)
            UastBinaryOperator.NOT_EQUALS,
            UastBinaryOperator.IDENTITY_NOT_EQUALS -> return not(level, sdkId)
            else -> assert(false) { tokenType }
          }
        }
      } else if (tokenType.text == "in") {
        val left = binary.leftOperand
        val sdkId = getSdkVersionLookup(left, client, evaluator, project)
        if (sdkId == -1) {
          return null
        }
        val right: UExpression = binary.rightOperand.skipParenthesizedExprDown()
        if (right is UBinaryExpression) {
          return getSdkIntConstraintFromExpression(right, apiLevelLookup, sdkId)
        }
      }

      return null
    }

    private fun getSdkIntConstraintFromExpression(
      expression: UBinaryExpression,
      apiLevelLookup: ApiLevelLookup? = null,
      sdkId: Int = ANDROID_SDK_ID,
    ): ApiConstraint? {
      if (expression.operands.size == 2) {
        // We assume there's no step, downTo etc -- there's no good reason to do that with API level
        // range checks.
        val lowerBound = getApiLevel(expression.operands[0], apiLevelLookup)
        val upperBound = getApiLevel(expression.operands[1], apiLevelLookup)
        // If it's a range like "1..10", then the end point is included,
        // whereas if it's "1 until 10", it's not.
        val includesEndPoint = expression.operator.text == ".."
        if (lowerBound != ApiLevel.NONE && upperBound != ApiLevel.NONE) {
          if (sdkId == ANDROID_SDK_ID_WITH_MINOR) {
            val (from, fromMinor) = lowerBound
            val (to, toMinor) = upperBound
            return range(
              from,
              fromMinor,
              to,
              toMinor + if (includesEndPoint) 1 else 0,
              ANDROID_SDK_ID,
            )
          } else {
            val adjustedUpperBound = upperBound.major + if (includesEndPoint) 1 else 0
            return range(lowerBound.major, 0, adjustedUpperBound, 0, sdkId)
          }
        }
      }

      return null
    }

    private fun getApiLevel(element: UExpression?, apiLevelLookup: ApiLevelLookup?): ApiLevel {
      var level = ApiLevel.NONE
      if (element is UReferenceExpression) {
        val codeName = element.resolvedName
        if (codeName != null) {
          level = ApiLevel.get(codeName, false)
        }
        if (level.isMissing()) {
          val constant = ConstantEvaluator.evaluate(null, element)
          if (constant is Number) {
            level = ApiLevel.get(constant.toInt())
          }
        }
      } else if (element is ULiteralExpression) {
        val value = element.value
        if (value is Int) {
          level = ApiLevel.get(value)
        }
      } else if (element is UParenthesizedExpression) {
        return getApiLevel(element.expression, apiLevelLookup)
      }
      if (level.isMissing() && apiLevelLookup != null && element != null) {
        level = apiLevelLookup(element)
      }
      return level
    }

    // From "X op Y" to "Y op X" -- e.g. "a > b" = "b < a" and "a >= b" = "b <= a"
    private fun UastBinaryOperator.flip(): UastBinaryOperator? {
      return when (this) {
        UastBinaryOperator.GREATER -> UastBinaryOperator.LESS
        UastBinaryOperator.GREATER_OR_EQUALS -> UastBinaryOperator.LESS_OR_EQUALS
        UastBinaryOperator.LESS_OR_EQUALS -> UastBinaryOperator.GREATER_OR_EQUALS
        UastBinaryOperator.LESS -> UastBinaryOperator.GREATER
        else -> null
      }
    }

    private val VERSION_METHOD_NAME_PREFIXES =
      arrayOf("isAtLeast", "isRunning", "is", "runningOn", "running", "has")

    private val VERSION_METHOD_NAME_SUFFIXES =
      arrayOf("OrLater", "OrAbove", "OrHigher", "OrNewer", "Sdk")

    @VisibleForTesting
    fun getMinSdkVersionFromMethodName(name: String): Int {
      val prefix = VERSION_METHOD_NAME_PREFIXES.firstOrNull { name.startsWith(it) } ?: return -1
      val suffix =
        VERSION_METHOD_NAME_SUFFIXES.firstOrNull { SdkUtils.endsWithIgnoreCase(name, it) }
          ?: if (prefix != "is") "" else null ?: return -1
      val codeName = name.substring(prefix.length, name.length - suffix.length)
      var version = SdkVersionInfo.getApiByPreviewName(codeName, false)
      if (version == -1) {
        version = SdkVersionInfo.getApiByBuildCode(codeName, false)
        if (version == -1 && codeName.length == 1 && Character.isUpperCase(codeName[0])) {
          // Some future API level
          version = SdkVersionInfo.HIGHEST_KNOWN_API + 1
        } else if (SdkUtils.startsWithIgnoreCase(codeName, "api")) {
          val length = codeName.length
          var begin = 3 // "api".length
          if (begin < length && codeName[begin] == '_') {
            begin++
          }
          var end = begin
          while (end < length) {
            if (!Character.isDigit(codeName[end])) {
              break
            }
            end++
          }
          if (begin < end) {
            version = Integer.decode(codeName.substring(begin, end))
          }
        }
      }
      return version
    }
  }

  private fun getVersionCheckConditional(
    binary: UBinaryExpression,
    apiLevelLookup: ApiLevelLookup? = null,
  ): ApiConstraint? = getVersionCheckConditional(binary, client, evaluator, project, apiLevelLookup)

  private fun getSdkVersionLookup(element: UElement?): Int =
    getSdkVersionLookup(element, client, evaluator, project)

  private fun getWithinVersionCheckConditional(
    element: UElement,
    apiLookup: ApiLevelLookup?,
    depth: Int,
  ): ApiConstraint? {
    var current = element.uastParent
    var prev = element
    var constraint: ApiConstraint? = null
    while (current != null) {
      if (current is UMethod || current is UField) {
        if (current.uastParent !is UAnonymousClass) break
      } else if (current is UFile) {
        break
      } else {
        val currentConstraint = getVersionConditional(current, prev, apiLookup, depth)
        constraint = max(currentConstraint, constraint, either = false)
        val parent = skipParenthesizedExprUp(current.uastParent)
        if (parent is USwitchClauseExpression) {
          val ored =
            current is UPolyadicExpression &&
              current.operator == UastBinaryOperator.LOGICAL_OR &&
              prev !== current.operands.first().skipParenthesizedExprDown()
          val fromCondition = parent.caseValues.any { it === current }
          constraint =
            max(
              constraint,
              getCumulativeCaseConstraint(
                parent,
                includeCurrent = !ored && !fromCondition,
                apiLookup = apiLookup,
              ),
            )
          current = current.uastParent ?: break
        }
      }

      prev = current
      current = current.uastParent
    }
    return constraint
  }

  /** Looks up the version conditional for a specific [current] element, coming up from [prev]. */
  private fun getVersionConditional(
    current: UElement,
    prev: UElement?,
    apiLookup: ApiLevelLookup?,
    depth: Int,
  ): ApiConstraint? {
    if (current is UPolyadicExpression) {
      return if (current.operator === UastBinaryOperator.LOGICAL_AND) {
        getAndedWithConstraint(current, prev, apiLookup, depth)
      } else if (current.operator === UastBinaryOperator.LOGICAL_OR) {
        getOredWithConstraint(current, prev, apiLookup, depth)
      } else {
        null
      }
    } else if (current is UIfExpression) {
      val condition = current.condition
      return if (prev !== condition) {
        val fromThen = prev == current.thenExpression
        if (fromThen && uncertainOr(condition)) {
          return null
        } else if (!fromThen && uncertainAnd(condition)) {
          return null
        }
        val thenConstraint =
          getVersionCheckConstraint(
            element = condition,
            prev = prev,
            apiLookup = apiLookup,
            depth = depth,
          )
        thenConstraint?.let { if (fromThen) it else it.not() }
      } else {
        null
      }
    } else if (current is USwitchClauseExpressionWithBody) {
      val includeCurrent = !current.caseValues.any { it === prev }
      return getCumulativeCaseConstraint(
        current,
        includeCurrent = includeCurrent,
        apiLookup = apiLookup,
      )
    } else if (
      current is UCallExpression &&
        (prev as? UExpression)?.skipParenthesizedExprDown() is ULambdaExpression
    ) {
      // If the API violation is in a lambda that is passed to a method,
      // see if the lambda parameter is invoked inside that method, wrapped within
      // a suitable version conditional.
      //
      // Optionally also see if we're passing in the API level as a parameter
      // to the function.
      //
      // Algorithm:
      //  (1) Figure out which parameter we're mapping the lambda argument to.
      //  (2) Find that parameter invoked within the function
      //  (3) From the invocation see if it's a suitable version conditional
      //
      val method = current.resolve()
      if (method != null) {
        val annotation = SdkIntAnnotation.get(method)
        if (annotation != null) {
          val value = annotation.getApiLevel(evaluator, method, current)
          if (value != null) {
            return value.atLeast(annotation.sdkId)
          } // else: lambda
        }

        val mapping = evaluator.computeArgumentMapping(current, method)
        val parameter = mapping[prev]
        if (parameter != null) {
          val lambdaInvocation = getLambdaInvocation(parameter, method)
          if (lambdaInvocation != null) {
            val constraint =
              getWithinVersionCheckConditional(
                element = lambdaInvocation,
                apiLookup = getReferenceApiLookup(current),
                depth = depth + 1,
              )
            if (constraint != null) {
              return constraint
            }
          }
        }
      }
      return null
    } else if (
      current is UCallExpression &&
        (prev as? UExpression)?.skipParenthesizedExprDown() is UObjectLiteralExpression
    ) {
      val method = current.resolve()
      if (method != null) {
        val annotation = SdkIntAnnotation.get(method)
        if (annotation != null) {
          val value = annotation.getApiLevel(evaluator, method, current)
          if (value != null) {
            return value.atLeast(annotation.sdkId)
          } // else: lambda
        }

        val mapping = evaluator.computeArgumentMapping(current, method)
        val parameter = mapping[prev]
        if (parameter != null) {
          val lambdaInvocation = getLambdaInvocation(parameter, method)
          if (lambdaInvocation != null) {
            val constraint =
              getWithinVersionCheckConditional(
                element = lambdaInvocation,
                apiLookup = getReferenceApiLookup(current),
                depth = depth + 1,
              )
            if (constraint != null) {
              return constraint
            }
          }
        }
      }
      return null
    } else {
      return null
    }
  }

  private fun isTrue(element: UElement): Boolean {
    return ConstantEvaluator.evaluate(null, element) as? Boolean ?: return false
  }

  private fun isFalse(element: UElement): Boolean {
    return (ConstantEvaluator.evaluate(null, element) as? Boolean)?.not() ?: return false
  }

  private fun getCumulativeCaseConstraint(
    current: USwitchClauseExpression,
    includeCurrent: Boolean,
    apiLookup: ApiLevelLookup?,
  ): ApiConstraint? {
    val switch = current.getParentOfType(USwitchExpression::class.java, true)
    val entries =
      switch?.body?.expressions?.filterIsInstance<USwitchClauseExpression>() ?: emptyList()
    val switchExpression = switch?.expression
    val sdkId = if (switchExpression != null) getSdkVersionLookup(switchExpression) else -1
    val casesAreApiLevels = sdkId != -1
    var currentConstraint: ApiConstraint? = null
    for (entry in entries) {
      val caseConstraint =
        getCaseConstraint(entry, casesAreApiLevels, sdkId = sdkId, apiLevelLookup = apiLookup)
      if (entry === current) {
        if (!includeCurrent) {
          return currentConstraint
        }

        if (uncertainOr(entry.caseValues.firstOrNull())) {
          return currentConstraint
        }

        return max(caseConstraint, currentConstraint) ?: break
      }

      // Invert the constraint and combine it with the current constraint; this is now the
      // currentConstraint for the next case.
      if (caseConstraint != null) {
        // If this case is and'ed with other values, we can't conclude that the next
        // case is its reverse
        if (uncertainAnd(entry.caseValues.firstOrNull())) {
          continue
        }

        val reverse = caseConstraint.not()
        currentConstraint = max(reverse, currentConstraint, either = false)
      }
    }

    return null
  }

  private fun uncertainAnd(condition: UExpression?): Boolean {
    val expression = condition?.skipParenthesizedExprDown() as? UPolyadicExpression ?: return false
    return expression.operator == UastBinaryOperator.LOGICAL_AND && uncertain(expression, ::isTrue)
  }

  private fun uncertainOr(condition: UExpression?): Boolean {
    val expression = condition?.skipParenthesizedExprDown() as? UPolyadicExpression ?: return false
    return expression.operator == UastBinaryOperator.LOGICAL_OR && uncertain(expression, ::isFalse)
  }

  private fun uncertain(expression: UExpression, constant: (UElement) -> Boolean): Boolean {
    if (expression !is UBinaryExpression) return true
    val left = expression.leftOperand.skipParenthesizedExprDown()
    if (!(constant(left) || getVersionCheckConstraint(left, depth = 0) != null)) {
      return true
    }
    val right = expression.rightOperand.skipParenthesizedExprDown()
    return !(constant(right) || getVersionCheckConstraint(right, depth = 0) != null)
  }

  /**
   * Given a [USwitchClauseExpression] (a case in a when statement), returns the SDK_INT constraint
   * implied by this case, if any. If [casesAreApiLevels], this means the when-statement is
   * switching on SDK_INT as the subject, so the cases will just be integers or ranges understood to
   * refer to SDK_INT. Otherwise, this must be a subject-less when-statement where each case should
   * include the SDK_INT comparison (or version checking utility calls).
   */
  private fun getCaseConstraint(
    entry: USwitchClauseExpression,
    casesAreApiLevels: Boolean,
    apiLevelLookup: ApiLevelLookup? = null,
    sdkId: Int = ANDROID_SDK_ID,
  ): ApiConstraint? {
    val caseValues = entry.caseValues
    if (caseValues.isEmpty()) {
      // It's the else clause: no constraint here.
      return null
    }

    if (!casesAreApiLevels) {
      // When the when-statement has no subject, there's exactly one case (we've ruled out the else
      // clause above).
      val case = caseValues.first().skipParenthesizedExprDown()
      return getVersionCheckConstraint(element = case, apiLookup = apiLevelLookup, depth = 0)
    }

    // When the SDK_INT is the subject of the when, we can have multiple cases, and combinations of
    // specific
    // integers and range-expressions; iterate through each and combine into a single constraint.
    var constraint: ApiConstraint? = null
    for (case in caseValues) {
      val expression = case.skipParenthesizedExprDown()
      val caseConstraint =
        // We have a when (SDK_INT) so we already know it's an SDK_INT version check, here the case
        // is just the range of API levels
        if (expression is UBinaryExpression && expression.operator.text == "in") {
          val range =
            expression.operands.lastOrNull()?.skipParenthesizedExprDown() as? UBinaryExpression
          if (range != null) {
            getSdkIntConstraintFromExpression(range, sdkId = sdkId)
          } else {
            null
          }
        } else {
          val level = getApiLevel(expression, apiLevelLookup)
          if (level.isValid()) {
            exactly(level)
          } else {
            null
          }
        }

      caseConstraint ?: continue
      if (
        constraint != null
      ) { // Work around KT-52913; cannot allow nullable parameter in ApiConstraint.and()
        constraint = caseConstraint or constraint
      } else {
        constraint = caseConstraint
      }
    }

    return constraint
  }

  private fun getReferenceApiLookup(call: UCallExpression): (UElement) -> ApiLevel {
    return { reference ->
      var apiLevel = ApiLevel.NONE
      if (reference is UReferenceExpression) {
        val resolved = reference.resolve()
        if (resolved is PsiParameter) {
          val parameterList = PsiTreeUtil.getParentOfType(resolved, PsiParameterList::class.java)
          if (parameterList != null) {
            call.resolve()?.let { method ->
              val mapping = evaluator.computeArgumentMapping(call, method)
              for ((argument, parameter) in mapping) {
                if (parameter == resolved) {
                  apiLevel = getApiLevel(argument, null)
                  break
                }
              }
            }
              ?: run {
                val index = parameterList.getParameterIndex(resolved)
                val arguments = call.valueArguments
                if (index != -1 && index < arguments.size) {
                  apiLevel = getApiLevel(arguments[index], null)
                }
              }
          }
        }
      }
      apiLevel
    }
  }

  private fun getLambdaInvocation(parameter: PsiParameter, method: PsiMethod): UCallExpression? {
    if (method is PsiCompiledElement) {
      return null
    }
    val uMethod =
      UastFacade.convertElementWithParent(method, UMethod::class.java) as UMethod? ?: return null

    val match = Ref<UCallExpression>()
    val parameterName = parameter.name
    uMethod.accept(
      object : AbstractUastVisitor() {
        override fun visitCallExpression(node: UCallExpression): Boolean {
          val receiver = node.receiver?.skipParenthesizedExprDown()
          if (receiver is USimpleNameReferenceExpression) {
            val name = receiver.identifier
            if (name == parameterName) {
              match.set(node)
            }
          } else if (receiver is UReferenceExpression) {
            val name = receiver.resolvedName
            if (name == parameterName) {
              match.set(node)
            }
          }

          val callName = getMethodName(node)
          if (callName == parameterName) {
            // Potentially not correct due to scopes, but these lambda
            // utility methods tend to be short and for lambda function
            // calls, resolve on call returns null
            match.set(node)
          }

          return super.visitCallExpression(node)
        }
      }
    )

    return match.get()
  }

  private fun getVersionCheckConstraint(
    element: UElement,
    prev: UElement? = null,
    apiLookup: ApiLevelLookup? = null,
    depth: Int,
  ): ApiConstraint? {
    if (depth >= MAX_CALL_DEPTH) {
      return null
    }
    if (element is UPolyadicExpression) {
      if (element is UBinaryExpression) {
        getVersionCheckConditional(binary = element, apiLevelLookup = apiLookup)?.let {
          return it
        }
      }
      val tokenType = element.operator
      if (tokenType === UastBinaryOperator.LOGICAL_AND) {
        val constraint = getAndedWithConstraint(element, prev, apiLookup, depth)
        if (constraint != null) {
          return constraint
        }
      } else if (tokenType === UastBinaryOperator.LOGICAL_OR) {
        val constraint = getOredWithConstraint(element, prev, apiLookup, depth)
        if (constraint != null) {
          return constraint.not()
        }
      }
    } else if (element is UCallExpression) {
      return getValidVersionCall(element, depth + 1)
    } else if (element is UReferenceExpression) {
      // Constant expression for an SDK version check?
      val resolved = element.resolve()
      if (resolved is PsiField) {
        @Suppress("UnnecessaryVariable") val field = resolved

        val validFromAnnotation = getValidFromAnnotation(field)
        if (validFromAnnotation != null) {
          return validFromAnnotation
        }
        val validFromInferredAnnotation = getValidFromInferredAnnotation(field)
        if (validFromInferredAnnotation != null) {
          return validFromInferredAnnotation
        }
        val modifierList = field.modifierList
        if (modifierList != null && modifierList.hasExplicitModifier(PsiModifier.FINAL)) {
          val initializer = UastFacade.getInitializerBody(field)?.skipParenthesizedExprDown()
          if (initializer != null) {
            val constraint = getVersionCheckConstraint(element = initializer, depth = depth + 1)
            if (constraint != null) {
              return constraint
            }
          }
        }
      } else if (resolved is PsiParameter) {
        val validFromAnnotation = getValidFromAnnotation(resolved)
        if (validFromAnnotation != null) {
          return validFromAnnotation
        }
        // Unless use-site is specified, an annotation on a constructor parameter
        // should be associated with the corresponding field, not that parameter.
        // K1/ULC puts annotations on the parameter, without considering use-site,
        // and we may hit the annotation at the above lookup.
        // In contrast, K2/SLC does accurate use-site filtering, and won't see
        // any annotations on the parameter. So, we need to find the corresponding field
        // in the containing class, and attempt to look up annotations one more time.
        val paramList = resolved.parent
        val containingMethod = paramList.parent
        if (containingMethod is PsiMethod && containingMethod.isConstructor) {
          val constructorProperty =
            containingMethod.containingClass?.findFieldByName(
              resolved.name,
              /* checkBases = */ false,
            )
          if (constructorProperty != null) {
            val validFromAnnotationOnField = getValidFromAnnotation(constructorProperty)
            if (validFromAnnotationOnField != null) {
              return validFromAnnotationOnField
            }
          }
        }
      } else if (resolved is PsiLocalVariable) {
        // Technically we should only use the initializer if the variable is final,
        // but it's possible/likely people don't bother with this for local
        // variables, and it's unlikely that an unconditional SDK_INT constraint
        // would be changed.
        val initializer = UastFacade.getInitializerBody(resolved)?.skipParenthesizedExprDown()
        if (initializer != null) {
          val constraint = getVersionCheckConstraint(element = initializer, depth = depth + 1)
          if (constraint != null) {
            return constraint
          }
        }
      } else if (
        resolved is PsiMethod &&
          element is UQualifiedReferenceExpression &&
          element.selector is UCallExpression
      ) {
        val call = element.selector as UCallExpression
        return getValidVersionCall(call, depth + 1)
      } else if (resolved is PsiMethod) {
        // Reference to a Kotlin property?
        val field = (resolved.unwrapped as? KtProperty)?.toUElement() as? UField
        if (field != null && field.isFinal) {
          val javaPsi = field.javaPsi as? PsiModifierListOwner
          if (javaPsi != null) {
            val validFromAnnotation = getValidFromAnnotation(javaPsi)
            if (validFromAnnotation != null) {
              return validFromAnnotation
            }
            val validFromInferredAnnotation = getValidFromInferredAnnotation(javaPsi)
            if (validFromInferredAnnotation != null) {
              return validFromInferredAnnotation
            }
          }
          return field.uastInitializer?.skipParenthesizedExprDown()?.let {
            getVersionCheckConstraint(it, depth = depth + 1)
          }
        } else {
          // Method call via Kotlin property syntax
          return getValidVersionCall(call = element, method = resolved, depth = depth + 1)
        }
      } else if (resolved == null && element is UQualifiedReferenceExpression) {
        val selector = element.selector
        if (selector is UCallExpression) {
          return getValidVersionCall(call = selector, depth = depth + 1)
        }
      }
    } else if (element is UUnaryExpression) {
      if (element.operator === UastPrefixOperator.LOGICAL_NOT) {
        val operand = element.operand
        getVersionCheckConstraint(element = operand, depth = depth)?.let {
          if (!it.negatable()) {
            return null
          } else {
            return it.not()
          }
        }
      }
    } else if (element is UParenthesizedExpression) {
      return getVersionCheckConstraint(element.expression, element, apiLookup, depth)
    }
    return null
  }

  private fun getValidFromAnnotation(
    owner: PsiModifierListOwner,
    call: UCallExpression? = null,
  ): ApiConstraint? {
    val sdkIntAnnotation = SdkIntAnnotation.get(owner) ?: return null
    return sdkIntAnnotation.getApiLevel(evaluator, owner, call)?.atLeast(sdkIntAnnotation.sdkId)
  }

  /**
   * When we come across SDK_INT comparisons in library, we'll store that as an
   * implied @ChecksSdkIntAtLeast annotation (to match the existing support for
   * actual @ChecksSdkIntAtLeast annotations). Here, when looking up version checks we'll check the
   * given method or field and see if we've stashed any implied version checks when analyzing the
   * dependencies.
   */
  private fun getValidFromInferredAnnotation(
    owner: PsiModifierListOwner,
    call: UCallExpression? = null,
  ): ApiConstraint? {
    val annotation =
      findChecksSdkInferredAnnotation(owner, client, evaluator, project) ?: return null
    return annotation.getApiLevel(evaluator, owner, call)?.atLeast(annotation.sdkId)
  }

  private fun getValidVersionCall(call: UCallExpression, depth: Int): ApiConstraint? {
    val method = call.resolve()
    if (method == null) {
      // Fallback when we can't resolve call: Try to guess just based on the method name
      val identifier = call.methodIdentifier
      if (identifier != null) {
        val name = identifier.name
        val version = getMinSdkVersionFromMethodName(name)
        if (version != -1) {
          return atLeast(version)
        }
      }
      return null
    }
    return getValidVersionCall(call, method, depth)
  }

  private fun getValidVersionCall(call: UElement, method: PsiMethod, depth: Int): ApiConstraint? {
    val callExpression = call as? UCallExpression
    val validFromAnnotation = getValidFromAnnotation(method, callExpression)
    if (validFromAnnotation != null) {
      return validFromAnnotation
    }

    val validFromInferredAnnotation = getValidFromInferredAnnotation(method, callExpression)
    if (validFromInferredAnnotation != null) {
      return validFromInferredAnnotation
    }

    val name = method.name
    if (name.startsWith("isAtLeast")) {
      val containingClass = method.containingClass
      if (
        containingClass != null &&
          // android.support.v4.os.BuildCompat,
          // androidx.core.os.BuildCompat
          "BuildCompat" == containingClass.name
      ) {
        when {
          name == "isAtLeastN" -> return atLeast(24)
          name == "isAtLeastNMR1" -> return atLeast(25)
          name == "isAtLeastO" -> return atLeast(26)
          name.startsWith("isAtLeastP") -> return atLeast(28)
          name.startsWith("isAtLeastQ") -> return atLeast(29)
          // Try to guess future API levels before they're announced
          name.startsWith("isAtLeast") &&
            name.length == 10 &&
            Character.isUpperCase(name[9]) &&
            name[9] > 'Q' -> return atLeast(SdkVersionInfo.HIGHEST_KNOWN_API + 1)
        }
      }
    }
    val version = getMinSdkVersionFromMethodName(name)
    if (version != -1) {
      return atLeast(version)
    }

    // Unconditional version utility method? If so just attempt to call it
    if (!method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      val body = UastFacade.getMethodBody(method) ?: return null
      val expressions: List<UExpression> =
        if (body is UBlockExpression) {
          body.expressions
        } else {
          listOf(body)
        }
      if (expressions.size == 1) {
        val statement = expressions[0].skipParenthesizedExprDown()
        val returnValue =
          if (statement is UReturnExpression) {
            statement.returnExpression?.skipParenthesizedExprDown()
          } else {
            // Kotlin: may not have an explicit return statement
            statement
          } ?: return null
        val arguments = if (call is UCallExpression) call.valueArguments else emptyList()
        if (arguments.isEmpty()) {
          if (
            returnValue is UPolyadicExpression ||
              returnValue is UCallExpression ||
              returnValue is UQualifiedReferenceExpression
          ) {
            val constraint = getVersionCheckConstraint(element = returnValue, depth = depth + 1)
            if (constraint != null) {
              return constraint
            }
          }
        } else if (arguments.size == 1) {
          // See if we're passing in a value to the version utility method
          val constraint =
            getVersionCheckConstraint(
              element = returnValue,
              apiLookup = { reference ->
                var apiLevel = ApiLevel.NONE
                if (reference is UReferenceExpression) {
                  val resolved = reference.resolve()
                  if (resolved is PsiParameter) {
                    val parameterList =
                      PsiTreeUtil.getParentOfType(resolved, PsiParameterList::class.java)
                    if (parameterList != null) {
                      val index = parameterList.getParameterIndex(resolved)
                      if (index != -1 && index < arguments.size) {
                        apiLevel = getApiLevel(arguments[index], null)
                      }
                    }
                  }
                }
                apiLevel
              },
              depth = depth + 1,
            )
          if (constraint != null) {
            return constraint
          }
        }
      }
    }

    return null
  }

  @Suppress("SpellCheckingInspection")
  private fun getOredWithConstraint(
    element: UElement,
    before: UElement?,
    apiLookup: ApiLevelLookup?,
    depth: Int,
  ): ApiConstraint? {
    if (element is UBinaryExpression) {
      if (element.operator === UastBinaryOperator.LOGICAL_OR) {
        val left = element.leftOperand
        if (before !== left) {
          val leftConstraint =
            getVersionCheckConstraint(element = left, apiLookup = apiLookup, depth = depth)
          val right = element.rightOperand
          return if (right !== before) {
            val rightConstraint =
              getVersionCheckConstraint(element = right, apiLookup = apiLookup, depth = depth)
            max(leftConstraint?.not(), rightConstraint?.not(), either = false)
          } else {
            leftConstraint?.not()
          }
        }
      }
      getVersionCheckConditional(binary = element)?.let {
        return it.not()
      }
    } else if (element is UPolyadicExpression) {
      if (element.operator === UastBinaryOperator.LOGICAL_OR) {
        var constraint: ApiConstraint? = null
        for (operand in element.operands) {
          if (operand == before) {
            break
          } else {
            constraint =
              max(
                constraint,
                getVersionCheckConstraint(element = operand, apiLookup = apiLookup, depth = depth)
                  ?.not(),
                either = false,
              )
          }
        }
        return constraint
      }
      return null
    } else if (element is UParenthesizedExpression) {
      return getOredWithConstraint(element.expression, element, apiLookup, depth)
    }
    return null
  }

  @Suppress("SpellCheckingInspection")
  private fun getAndedWithConstraint(
    element: UElement,
    before: UElement?,
    apiLookup: ApiLevelLookup?,
    depth: Int,
  ): ApiConstraint? {
    if (element is UBinaryExpression) {
      if (element.operator === UastBinaryOperator.LOGICAL_AND) {
        val left = element.leftOperand
        if (before !== left) {
          val leftConstraint =
            getVersionCheckConstraint(element = left, apiLookup = apiLookup, depth = depth)
          val right = element.rightOperand
          if (right !== before) {
            val rightConstraint =
              getVersionCheckConstraint(element = right, apiLookup = apiLookup, depth = depth)
            val max = max(leftConstraint, rightConstraint)
            if (max != null && (leftConstraint == null || rightConstraint == null)) {
              return max.asNonNegatable()
            }
            return max
          }
          return leftConstraint?.asNonNegatable()
        }
      }
      getVersionCheckConditional(binary = element)?.let {
        return it.asNonNegatable()
      }
    } else if (element is UPolyadicExpression) {
      if (element.operator === UastBinaryOperator.LOGICAL_AND) {
        var constraint: ApiConstraint? = null
        for (operand in element.operands) {
          if (operand == before) {
            break
          } else {
            constraint =
              max(
                constraint,
                getVersionCheckConstraint(operand, apiLookup = apiLookup, depth = depth),
              )
          }
        }
        return constraint?.asNonNegatable()
      }
    } else if (element is UParenthesizedExpression) {
      return getAndedWithConstraint(element.expression, element, apiLookup, depth)
    }
    return null
  }

  private inner class VersionCheckWithExitFinder
  constructor(private val endElement: UElement, private val api: ApiConstraint) :
    AbstractUastVisitor() {
    private var found = false
    private var done = false

    override fun visitElement(node: UElement): Boolean {
      if (done) {
        return true
      }
      if (node === endElement) {
        done = true
      }
      return done
    }

    override fun visitIfExpression(node: UIfExpression): Boolean {
      val exit = super.visitIfExpression(node)
      if (done) {
        return true
      }
      if (endElement.isUastChildOf(node, true)) {
        // Even if there is an unconditional exit, endElement will occur before it!
        done = true
        return true
      }
      val thenBranch = node.thenExpression
      val elseBranch = node.elseExpression
      val constraint = getVersionCheckConstraint(element = node.condition, depth = 0)
      if (thenBranch != null) {
        if (constraint?.not()?.isAtLeast(api) == true) {
          // See if the body does an immediate return
          if (thenBranch.isUnconditionalReturn()) {
            found = true
            done = true
          }
        }
      }
      if (elseBranch != null) {
        if (constraint?.isAtLeast(api) == true) {
          if (elseBranch.isUnconditionalReturn()) {
            found = true
            done = true
          }
        }
      }
      return exit
    }

    override fun visitSwitchExpression(node: USwitchExpression): Boolean {
      super.visitSwitchExpression(node)
      if (done) {
        return true
      }
      if (endElement.isUastChildOf(node, true)) {
        // Even if there is an unconditional exit, endElement will occur before it!
        done = true
        return true
      }

      // when { } instead of when (subject) { } ? If so, check if any condition is an SDK_INT check.
      val subject = node.expression
      if (subject != null) {
        // We only check for SDK_INT constraints in subject-less when statements. In theory
        // you could do a range-based SDK_INT check (when (SDK_INT) 1...21 -> ... etc) but that
        // would need to be handled differently.
        if (getSdkVersionLookup(subject) == -1) {
          return done
        }
      }

      // Known constraint about SDK_INT *after* the switch statement. This is the highest possible
      // SDK_INT.
      var fallthroughConstraint: ApiConstraint? = null

      // Known constraint about SDK_INT before each case.
      // For example, after the when-statement
      //   when {
      //      SDK_INT > 30 -> { something(); return; }
      //      SDK_INT > 20 -> { somethingElse(); }
      //      else -> return;
      //   }
      // here the fallthroughConstraint will be 21 <= SDK_INT <= 30.
      var currentConstraint: ApiConstraint? = null

      for (entry in node.body.expressions) {
        if (entry is USwitchClauseExpression) {
          val body = (entry as? USwitchClauseExpressionWithBody)?.body
          val caseConstraint = getCaseConstraint(entry, casesAreApiLevels = subject != null)
          // If the current element doesn't fall through, update the fallthroughConstraint: take the
          // worst-case combination
          // of currentConstraint and caseConstraint with the current
          if (body != null && caseConstraint != null && !fallsThrough(body)) {
            fallthroughConstraint = max(caseConstraint.not(), fallthroughConstraint, either = false)
          }

          // Invert the constraint and combine it with the current constraint; this is now the
          // currentConstraint for the next case.
          if (caseConstraint != null) {
            val reverse = caseConstraint.not()
            currentConstraint = max(reverse, currentConstraint, either = false)
          }

          // Handle final else clause; if it returns unconditionally, the constraint for after the
          // switch statement
          // would be the negation of the current constraint.
          if (entry.caseValues.isEmpty() && body != null && !fallsThrough(body)) {
            if (currentConstraint != null) {
              currentConstraint = currentConstraint.not()
              fallthroughConstraint =
                if (fallthroughConstraint != null) {
                  max(fallthroughConstraint, currentConstraint, either = false)
                } else {
                  currentConstraint
                }
            }
            break
          }
        }
      }
      val constraint = fallthroughConstraint ?: return done
      if (constraint.isAtLeast(api)) {
        // We've had earlier clauses which checked the API level.
        // No, this isn't right; we need to lower the level
        found = true
        done = true
      }

      return done
    }

    private fun fallsThrough(body: UExpression): Boolean {
      return !body.isUnconditionalReturn()
    }

    fun found(): Boolean {
      return found
    }
  }

  /** Unpacked version of `@androidx.annotation.ChecksSdkIntAtLeast` */
  class SdkIntAnnotation(
    val api: Int?,
    val codename: String?,
    val parameter: Int?,
    val lambda: Int?,
    val sdkId: Int,
  ) {
    constructor(
      annotation: PsiAnnotation
    ) : this(
      annotation.getAnnotationIntValue("api"),
      annotation.getAnnotationStringValue("codename"),
      annotation.getAnnotationIntValue("parameter"),
      annotation.getAnnotationIntValue("lambda"),
      annotation.getAnnotationIntValue("extension", ANDROID_SDK_ID),
    )

    /** Returns the API level for this annotation in the given context. */
    fun getApiLevel(
      evaluator: JavaEvaluator,
      owner: PsiModifierListOwner,
      call: UCallExpression?,
    ): ApiLevel? {
      val apiLevel = apiLevel()
      if (apiLevel.isValid()) {
        return apiLevel
      }

      val index = parameter ?: lambda ?: return null
      if (owner is PsiMethod && call != null) {
        val argument = findArgumentFor(evaluator, owner, index, call)
        if (argument != null) {
          val v = ConstantEvaluator.evaluate(null, argument)
          return (v as? Number)?.toInt()?.let { ApiLevel(it) }
        }
      }

      return null
    }

    private fun apiLevel(): ApiLevel {
      return if (api != null && api != -1) {
        ApiLevel(api)
      } else if (codename != null) {
        ApiLevel.get(codename)
      } else {
        ApiLevel.NONE
      }
    }

    private fun findArgumentFor(
      evaluator: JavaEvaluator,
      calledMethod: PsiMethod,
      parameterIndex: Int,
      call: UCallExpression,
    ): UExpression? {
      val parameters = calledMethod.parameterList.parameters
      if (parameterIndex >= 0 && parameterIndex < parameters.size) {
        val target = parameters[parameterIndex]
        val mapping = evaluator.computeArgumentMapping(call, calledMethod)
        for ((key, value1) in mapping) {
          if (value1 === target || value1.isEquivalentTo(target)) {
            return key
          }
        }
      }

      return null
    }

    companion object {
      /** Looks up the @ChecksSdkIntAtLeast annotation for the given method or field. */
      fun get(owner: PsiModifierListOwner): SdkIntAnnotation? {
        @Suppress("ExternalAnnotations")
        for (annotation in owner.annotations) {
          val referenceName = annotation.nameReferenceElement?.referenceName
          if (referenceName == null || referenceName == CHECKS_SDK_INT_AT_LEAST_NAME) {
            when (annotation.qualifiedName) {
              CHECKS_SDK_INT_AT_LEAST_ANNOTATION,
              PLATFORM_CHECKS_SDK_INT_AT_LEAST_ANNOTATION -> {
                return SdkIntAnnotation(annotation)
              }
            }
          }
        }

        return null
      }

      fun getMethodKey(evaluator: JavaEvaluator, method: UMethod): String {
        val desc =
          evaluator.getMethodDescription(method, includeName = false, includeReturn = false)
        val cls = method.getContainingUClass()?.let { evaluator.getQualifiedName(it) }
        return "$cls#${method.name}$desc"
      }

      fun getFieldKey(evaluator: JavaEvaluator, field: UField): String {
        val cls = field.getContainingUClass()?.let { evaluator.getQualifiedName(it) }
        return "$cls#${field.name}"
      }

      private fun getMethodKey(evaluator: JavaEvaluator, method: PsiMethod): String {
        val desc =
          evaluator.getMethodDescription(method, includeName = false, includeReturn = false)
        val cls = method.containingClass?.let { evaluator.getQualifiedName(it) }
        return "$cls#${method.name}$desc"
      }

      private fun getFieldKey(evaluator: JavaEvaluator, field: PsiField): String {
        val cls = field.containingClass?.let { evaluator.getQualifiedName(it) }
        return "$cls#${field.name}"
      }

      fun findSdkIntAnnotation(
        client: LintClient,
        evaluator: JavaEvaluator,
        project: Project,
        owner: PsiModifierListOwner,
      ): SdkIntAnnotation? {
        val key =
          when (owner) {
            is PsiMethod -> getMethodKey(evaluator, owner)
            is PsiField -> getFieldKey(evaluator, owner)
            else -> return null
          }
        val lintMaps = client.getPartialResults(project, SDK_INT_VERSION_DATA).maps()
        val map = mutableMapOf<String, String>()
        lintMaps.forEach { lintMap ->
          lintMap.keys().forEach { key -> lintMap[key]?.let { map[key] = it } }
        }
        val args = map[key] ?: return null
        val api = findAttribute(args, "api")?.toIntOrNull()
        val codename = findAttribute(args, "codename")
        val parameter = findAttribute(args, "parameter")?.toIntOrNull()
        val lambda = findAttribute(args, "lambda")?.toIntOrNull()
        val sdkId = findAttribute(args, "extension")?.toIntOrNull() ?: ANDROID_SDK_ID
        return SdkIntAnnotation(api, codename, parameter, lambda, sdkId)
      }

      private fun findAttribute(args: String, name: String): String? {
        val key = "$name="
        val index = args.indexOf(key)
        if (index == -1) {
          return null
        }
        val start = index + key.length
        val end = args.indexOf(',', start).let { if (it == -1) args.length else it }
        return args.substring(start, end)
      }
    }
  }
}

fun PsiAnnotation.getAnnotationIntValue(attribute: String, defaultValue: Int = -1): Int {
  val psiValue = findAttributeValue(attribute) ?: return defaultValue
  val value = ConstantEvaluator.evaluate(null, psiValue)
  if (value is Number) {
    return value.toInt()
  }

  return defaultValue
}

fun PsiAnnotation.getAnnotationStringValue(attribute: String, defaultValue: String = ""): String {
  val psiValue = findAttributeValue(attribute) ?: return defaultValue
  return ConstantEvaluator.evaluateString(null, psiValue, false) ?: defaultValue
}
