/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.lint.client.api

import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.acceptSourceFile
import com.android.tools.lint.detector.api.getMethodName
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.util.isAssignment
import org.jetbrains.uast.visitor.AbstractUastVisitor

/** Gradle visitor for Kotlin Script files. */
class UastGradleVisitor(override val javaContext: JavaContext) : GradleVisitor() {
  override fun visitBuildScript(context: GradleContext, detectors: List<GradleScanner>) {
    val uastFile = javaContext.uastFile ?: return
    uastFile.acceptSourceFile(
      object : AbstractUastVisitor() {
        override fun visitCallExpression(node: UCallExpression): Boolean {
          handleMethodCall(node, detectors, context)
          return super.visitCallExpression(node)
        }

        override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
          handleBinaryExpression(node, detectors, context)
          return super.visitBinaryExpression(node)
        }
      }
    )
  }

  private fun handleBinaryExpression(
    node: UBinaryExpression,
    detectors: List<GradleScanner>,
    context: GradleContext,
  ) {
    if (node.isAssignment()) {
      val hierarchy = getPropertyHierarchy(node.leftOperand)
      val target = hierarchy.firstOrNull() ?: return
      val hierarchyWithParents = hierarchy + getParent(node) + getParentN(node, 2)
      val parentName = hierarchyWithParents[1] ?: ""
      val parentParentName = hierarchyWithParents[2]
      val value = node.rightOperand.getSource()
      for (scanner in detectors) {
        scanner.checkDslPropertyAssignment(
          context,
          target,
          value,
          parentName,
          parentParentName,
          node.leftOperand,
          node.rightOperand,
          node,
        )
      }
    } else if (listOf("version", "apply").contains(node.operatorIdentifier?.name)) {
      // TODO(xof): the above condition is not really right, and this should actually work by
      // knowing that we have a parent
      //  plugins { id .. } call.  However, this is enough to maintain parity with the Groovy
      // visitor for practical purposes.
      val property = node.operatorIdentifier?.name!!
      var call: UExpression = node
      while (call is UBinaryExpression) {
        call = call.leftOperand
      }
      if (
        call is UCallExpression &&
          getMethodName(call) == "id" &&
          call.valueArgumentCount == 1 &&
          getParent(node) == "plugins"
      ) {
        val idExpression = call.valueArguments[0]
        GradleContext.getStringLiteralValue(idExpression.getSource(), idExpression)?.let { id ->
          val value = node.rightOperand.getSource()
          for (scanner in detectors) {
            scanner.checkDslPropertyAssignment(
              context,
              property,
              value,
              id,
              "plugins",
              node.operator,
              node.rightOperand,
              node,
            )
          }
        }
      }
    }
  }

  private fun handleMethodCall(
    node: UCallExpression,
    detectors: List<GradleScanner>,
    context: GradleContext,
  ) {
    val valueArguments = node.valueArguments
    val propertyName = getMethodName(node)
    if (propertyName == null) {
      return
    } else {
      val parentName = getParent(node)
      val parentParentName = getParentN(node, 2)
      val unnamedArguments = valueArguments.map { it.getSource() }
      for (scanner in detectors) {
        scanner.checkMethodCall(
          context,
          propertyName,
          parentName,
          parentParentName,
          mapOf(),
          unnamedArguments,
          node,
        )
      }
      if (valueArguments.size == 1 && valueArguments[0] !is ULambdaExpression) {
        // Some sort of DSL property?
        // Parent should be block, its parent lambda, its parent a call -
        // the name is the parent
        if (isMethodCallInClosure(node)) {
          val value = unnamedArguments[0]
          for (scanner in detectors) {
            scanner.checkDslPropertyAssignment(
              context,
              propertyName,
              value,
              parentName ?: "",
              parentParentName,
              node.methodIdentifier ?: node,
              valueArguments[0],
              node,
            )
          }
        }
      }
    }
  }

  /**
   * Returns the source string for this [UExpression].
   *
   * This is used because [UExpression.asSourceString] doesn't do what it might sound like it does:
   * return the actual source; instead, it runs something like a source printer on the UAST
   * elements; this means for example that the whitespace will be standard instead of what is
   * actually in the source code, and for some constructs, there's a big change (for example,
   * properties will look like Java getters and setters). Instead, we can get the real source code
   * from the [UElement.sourcePsi] property, and from there the true source code via
   * [PsiElement.getText]. We only fall back to [UExpression.asSourceString] for elements missing a
   * source element (e.g. virtual elements).
   */
  private fun UExpression.getSource(): String {
    val sourcePsi = sourcePsi

    if (sourcePsi != null) {
      // Note also that for strings, the GradleVisitor contract expects
      // to get the string literals *with* surrounding quotes; this isn't
      // included in the bounds for string literals in the AST (it *is*
      // from [asSourceString()], so correct for that here.
      if (sourcePsi is KtLiteralStringTemplateEntry) {
        return sourcePsi.parent?.text ?: sourcePsi.text
      }
      return sourcePsi.text
    }

    return asSourceString()
  }

  private fun getPropertyHierarchy(expression: UExpression): List<String> {
    return when (expression) {
      is UQualifiedReferenceExpression -> {
        val result = mutableListOf(expression.selector.getSource())
        var receiver = expression.receiver
        while (receiver is UQualifiedReferenceExpression) {
          result.add(receiver.selector.getSource())
          receiver = receiver.receiver
        }
        result.add(receiver.getSource())
        result
      }
      else -> listOf(expression.getSource())
    }
  }

  private fun getSurroundingNamedBlock(node: UElement): UCallExpression? {
    var parent = node.uastParent
    while (parent is UBinaryExpression || parent is UCallExpression) {
      parent = parent.uastParent
    }
    if (parent is UReturnExpression) {
      // parent may be a UReturnExpression child of UBlockExpression
      parent = parent.uastParent
    }
    if (parent is UBlockExpression) {
      val parentParent = parent.uastParent
      if (parentParent is ULambdaExpression) {
        val parentCall = parentParent.uastParent
        if (parentCall is UCallExpression) {
          return parentCall
        }
      }
    }

    return null
  }

  private fun getParentN(node: UElement, n: Int): String? {
    val parentCall = getSurroundingNamedBlock(node)
    return when {
      parentCall == null -> null
      n == 1 -> getMethodName(parentCall)
      else -> getParentN(parentCall, n - 1)
    }
  }

  private fun getParent(node: UElement) = getParentN(node, 1)

  private fun isMethodCallInClosure(node: UElement): Boolean =
    getSurroundingNamedBlock(node)?.let { block ->
      when (val parent = node.uastParent) {
        is UReturnExpression -> block == parent.uastParent?.uastParent?.uastParent
        is UBinaryExpression -> isMethodCallInClosure(parent)
        else -> block == parent?.uastParent?.uastParent
      }
    } ?: false

  override fun createLocation(context: GradleContext, cookie: Any): Location {
    return if (cookie is UElement) {
      javaContext.getLocation(cookie)
    } else if (cookie is PsiElement) {
      javaContext.getLocation(cookie)
    } else {
      error("Unexpected location node ${cookie.javaClass}")
    }
  }

  override fun getStartOffset(context: GradleContext, cookie: Any): Int {
    val start = javaContext.getLocation(cookie as UElement).start
    return start?.offset ?: -1
  }
}
