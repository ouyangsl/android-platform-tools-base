/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.AndroidXConstants.SUPPORT_ANNOTATIONS_PREFIX
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.KOTLIN_SUPPRESS
import com.android.tools.lint.client.api.AndroidPlatformAnnotations.Companion.fromPlatformAnnotation
import com.android.tools.lint.client.api.AndroidPlatformAnnotations.Companion.isPlatformAnnotation
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationOrigin
import com.android.tools.lint.detector.api.AnnotationOrigin.CLASS
import com.android.tools.lint.detector.api.AnnotationOrigin.FIELD
import com.android.tools.lint.detector.api.AnnotationOrigin.FILE
import com.android.tools.lint.detector.api.AnnotationOrigin.METHOD
import com.android.tools.lint.detector.api.AnnotationOrigin.OUTER_CLASS
import com.android.tools.lint.detector.api.AnnotationOrigin.PACKAGE
import com.android.tools.lint.detector.api.AnnotationOrigin.PARAMETER
import com.android.tools.lint.detector.api.AnnotationOrigin.PROPERTY_DEFAULT
import com.android.tools.lint.detector.api.AnnotationOrigin.SELF
import com.android.tools.lint.detector.api.AnnotationOrigin.VARIABLE
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.AnnotationUsageType.ANNOTATION_REFERENCE
import com.android.tools.lint.detector.api.AnnotationUsageType.ASSIGNMENT_LHS
import com.android.tools.lint.detector.api.AnnotationUsageType.ASSIGNMENT_RHS
import com.android.tools.lint.detector.api.AnnotationUsageType.BINARY
import com.android.tools.lint.detector.api.AnnotationUsageType.CLASS_REFERENCE
import com.android.tools.lint.detector.api.AnnotationUsageType.CLASS_REFERENCE_AS_DECLARATION_TYPE
import com.android.tools.lint.detector.api.AnnotationUsageType.CLASS_REFERENCE_AS_IMPLICIT_DECLARATION_TYPE
import com.android.tools.lint.detector.api.AnnotationUsageType.DEFINITION
import com.android.tools.lint.detector.api.AnnotationUsageType.EQUALITY
import com.android.tools.lint.detector.api.AnnotationUsageType.EXTENDS
import com.android.tools.lint.detector.api.AnnotationUsageType.FIELD_REFERENCE
import com.android.tools.lint.detector.api.AnnotationUsageType.IMPLICIT_CONSTRUCTOR
import com.android.tools.lint.detector.api.AnnotationUsageType.IMPLICIT_CONSTRUCTOR_CALL
import com.android.tools.lint.detector.api.AnnotationUsageType.METHOD_CALL
import com.android.tools.lint.detector.api.AnnotationUsageType.METHOD_CALL_PARAMETER
import com.android.tools.lint.detector.api.AnnotationUsageType.METHOD_OVERRIDE
import com.android.tools.lint.detector.api.AnnotationUsageType.METHOD_REFERENCE
import com.android.tools.lint.detector.api.AnnotationUsageType.METHOD_RETURN
import com.android.tools.lint.detector.api.AnnotationUsageType.VARIABLE_REFERENCE
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.asCall
import com.android.tools.lint.detector.api.hasImplicitDefaultConstructor
import com.android.tools.lint.detector.api.isKotlin
import com.android.tools.lint.detector.api.resolveOperator
import com.android.tools.lint.detector.api.resolveOverloadedOperator
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeElement
import com.intellij.psi.PsiTypeVisitor
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.elements.KtLightMember
import org.jetbrains.kotlin.asJava.elements.KtLightParameter
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtConstructorDelegationCall
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtTreeVisitor
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UArrayAccessExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UEnumConstant
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UObjectLiteralExpression
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastEmptyExpression
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.isNullLiteral
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.skipParenthesizedExprUp
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.util.isAssignment
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.w3c.dom.Node

/** Looks up annotations on method calls and enforces the various things they express. */
internal class AnnotationHandler(
  private val driver: LintDriver,
  private val scanners: Map<String, Collection<SourceCodeScanner>>,
) {

  val relevantAnnotations: Set<String> = HashSet(scanners.keys)

  init {
    driver.skipAnnotations?.forEach { (relevantAnnotations as MutableSet).add(it) }
  }

  private fun checkContextAnnotations(
    context: JavaContext,
    origCall: UElement,
    annotations: List<AnnotationInfo>,
    annotated: PsiModifierListOwner,
  ) {
    var call = origCall
    // Handle typedefs and resource types: if you're comparing it, check that
    // it's being compared with something compatible
    var p = skipParenthesizedExprUp(call.uastParent) ?: return

    if (p is UQualifiedReferenceExpression) {
      call = p
      p = skipParenthesizedExprUp(p.uastParent) ?: return
    }

    if (p is UBinaryExpression) {
      var check: UExpression? = null
      val binary = p
      val leftOperand = binary.leftOperand.skipParenthesizedExprDown()
      val rightOperand = binary.rightOperand.skipParenthesizedExprDown()
      if (call === leftOperand) {
        check = rightOperand
      } else if (call === rightOperand) {
        check = leftOperand
      }
      if (check != null) {
        val type =
          when (p.operator) {
            UastBinaryOperator.ASSIGN -> if (check === rightOperand) ASSIGNMENT_LHS else BINARY
            UastBinaryOperator.EQUALS,
            UastBinaryOperator.NOT_EQUALS,
            UastBinaryOperator.IDENTITY_EQUALS,
            UastBinaryOperator.IDENTITY_NOT_EQUALS -> EQUALITY
            else -> BINARY
          }
        checkAnnotations(context, check, type, annotated, annotations)
      }
    } else if (p is UQualifiedReferenceExpression) {
      // Handle equals() as a special case: if you're invoking
      //   .equals on a method whose return value annotated with @StringDef
      //   we want to make sure that the equals parameter is compatible.
      // 186598: StringDef don't warn using a getter and equals
      val ref = p as UQualifiedReferenceExpression?
      if ("equals" == ref?.resolvedName) {
        val selector = ref.selector
        if (selector is UCallExpression) {
          val arguments = selector.valueArguments
          if (arguments.size == 1) {
            val argument = arguments[0].skipParenthesizedExprDown()
            if (argument != null) {
              checkAnnotations(context, argument, EQUALITY, annotated, annotations)
            }
          }
        }
      }
    } else if (call is UVariable) {
      val variable = call
      val variablePsi = call.javaPsi
      // TODO: What about fields?
      call
        .getContainingUMethod()
        ?.accept(
          object : AbstractUastVisitor() {
            override fun visitSimpleNameReferenceExpression(
              node: USimpleNameReferenceExpression
            ): Boolean {
              val referencedVariable = node.resolve()
              if (variablePsi == referencedVariable) {
                val expression = node.getParentOfType(UExpression::class.java, true)
                if (expression != null) {
                  val inner = node.getParentOfType(UExpression::class.java, false) ?: return false
                  checkAnnotations(context, inner, VARIABLE_REFERENCE, annotated, annotations)
                  return false
                }

                // TODO: if the reference is the LHS Of an assignment
                //   UastExpressionUtils.isAssignment(expression)
                // then assert the annotations on to the right hand side
              }
              return super.visitSimpleNameReferenceExpression(node)
            }
          }
        )

      val initializer = variable.uastInitializer
      if (initializer != null) {
        checkAnnotations(context, initializer, ASSIGNMENT_RHS, annotated, annotations)
      }
    }
  }

  fun visitBinaryExpression(context: JavaContext, node: UBinaryExpression) {
    // Assigning from/to an annotated field?
    if (node.isAssignment()) {
      // We're only processing fields, not local variables, since those are
      // already visited
      val lhsResolved = node.leftOperand.tryResolve()
      if (
        lhsResolved != null &&
          lhsResolved is PsiModifierListOwner &&
          lhsResolved !is PsiLocalVariable &&
          !isOverloadedMethodCall(node.leftOperand)
      ) {
        val annotations = getMemberAnnotations(context, lhsResolved)
        checkAnnotations(context, node.rightOperand, ASSIGNMENT_RHS, lhsResolved, annotations)
      }

      val rhsResolved = node.rightOperand.tryResolve()
      if (
        rhsResolved != null &&
          rhsResolved is PsiModifierListOwner &&
          rhsResolved !is PsiLocalVariable &&
          !isOverloadedMethodCall(node.rightOperand)
      ) {
        val annotations = getMemberAnnotations(context, rhsResolved)
        checkAnnotations(context, node.leftOperand, ASSIGNMENT_LHS, rhsResolved, annotations)
      }
    }

    // Overloaded operators
    val method = node.resolveOverloadedOperator()
    if (method != null) {
      if (
        (node.operator == UastBinaryOperator.EQUALS ||
          node.operator == UastBinaryOperator.NOT_EQUALS) &&
          (node.rightOperand.isNullLiteral() || node.leftOperand.isNullLiteral())
      ) {
        // If you have "a != null" on a data class technically
        // if there are annotations on that class we'll be
        // applying a method call and checking annotations,
        // but this leads to noise that is unlikely to be
        // interesting (with for example @VisibleForTesting)
        return
      }

      val call = node.asCall(method)
      checkCall(context, method, call)
    }
  }

  fun visitObjectLiteralExpression(context: JavaContext, node: UObjectLiteralExpression) {
    visitCallExpression(context, node)
  }

  fun visitClassLiteralExpression(context: JavaContext, node: UClassLiteralExpression) {
    val containingClass = context.evaluator.getTypeClass(node.type) ?: return
    visitClassReference(context, node, containingClass)
  }

  fun visitTypeReferenceExpression(context: JavaContext, node: UTypeReferenceExpression) {
    if (node.getParentOfType<UClassLiteralExpression>() != null) {
      // To avoid duplicate errors in Java class literal expressions, which contain type reference
      // expressions in their UAST tree
      return
    }
    val cls = context.evaluator.getTypeClass(node.type) ?: return
    visitClassReference(context, node, cls)
  }

  private fun visitClassReference(context: JavaContext, node: UElement, cls: PsiClass) {
    val annotations = getMemberAnnotations(context, cls)
    checkAnnotations(context, node, CLASS_REFERENCE, cls, annotations)
  }

  fun visitUnaryExpression(context: JavaContext, node: UUnaryExpression) {
    // Overloaded operators
    val method = node.resolveOperator()
    if (method != null) {
      val call = node.asCall(method)
      checkCall(context, method, call)
    }
  }

  fun visitParameter(context: JavaContext, node: UParameter) {
    val annotations = mutableListOf<AnnotationInfo>()
    val psiParameter = node.javaPsi as? PsiParameter ?: return
    annotations.addAnnotations(context.evaluator, psiParameter, PARAMETER, inHierarchy = false)
    checkAnnotations(context, node, DEFINITION, psiParameter, annotations)
  }

  /**
   * Adds all the relevant annotations associated with this modifier list owner, and returns the
   * number of annotations added.
   */
  private fun MutableList<AnnotationInfo>.addAnnotations(
    evaluator: JavaEvaluator,
    owner: PsiModifierListOwner,
    source: AnnotationOrigin,
    prepend: Boolean = false,
    inHierarchy: Boolean = true,
  ): Int {
    val annotations = getRelevantAnnotations(evaluator, owner, inHierarchy)
    val count = addAnnotations(owner, annotations, source, prepend)
    if (source == PARAMETER || source == METHOD || source == FIELD) {
      return addDefaultAnnotations(evaluator, owner) + count
    }
    return count
  }

  /**
   * Pick up annotations that the developer may have intended to apply to the getter/setter: those
   * using a default usage site which probably isn't what developers intended (this is usually the
   * parameter or private field; it's never the property getter or setter), or specifying it on the
   * property itself. (If specifying for example get: or set:, then UAST will already propagate the
   * annotations to the correct place.)
   */
  private fun MutableList<AnnotationInfo>.addDefaultAnnotations(
    evaluator: JavaEvaluator,
    owner: PsiModifierListOwner,
  ): Int {
    if (owner is KtLightMember<*>) {
      val origin = owner.unwrapped
      if (origin is KtProperty) {
        return addDefaultSiteAnnotations(evaluator, owner, origin.annotationEntries)
      }
    } else if (owner is KtLightParameter) {
      val origin = owner.method.unwrapped as? KtDeclaration
      if (origin is KtProperty || origin is KtParameter) {
        return addDefaultSiteAnnotations(evaluator, owner, origin.annotationEntries)
      }
    }

    return 0
  }

  private fun MutableList<AnnotationInfo>.addDefaultSiteAnnotations(
    evaluator: JavaEvaluator,
    owner: PsiModifierListOwner,
    entries: List<KtAnnotationEntry>,
  ): Int {
    var count = 0
    for (ktAnnotation in entries) {
      val site = ktAnnotation.useSiteTarget?.getAnnotationUseSiteTarget()
      if (site == null || site == AnnotationUseSiteTarget.PROPERTY) {
        val defaultSiteAnnotation =
          (UastFacade.convertElement(ktAnnotation, null) as? UAnnotation ?: continue).let {
            val signature = it.qualifiedName ?: ""
            if (isPlatformAnnotation(signature)) {
              it.fromPlatformAnnotation(signature)
            } else {
              it
            }
          }
        val relevantAnnotations =
          filterRelevantAnnotations(evaluator, listOf(defaultSiteAnnotation))
        for (annotation in relevantAnnotations) {
          if (addAnnotation(annotation, owner, PROPERTY_DEFAULT, false)) {
            count++
          }
        }
      }
    }

    return count
  }

  /** Adds all the given annotations (if possible) and returns the number of annotations added. */
  private fun MutableList<AnnotationInfo>.addAnnotations(
    owner: PsiElement,
    annotations: List<UAnnotation>,
    source: AnnotationOrigin,
    prepend: Boolean = false,
  ): Int {
    return annotations.count { annotation -> addAnnotation(annotation, owner, source, prepend) }
  }

  /** Adds the given annotation if possible and returns whether the annotation was indeed added. */
  private fun MutableList<AnnotationInfo>.addAnnotation(
    annotation: UAnnotation,
    owner: PsiElement,
    source: AnnotationOrigin,
    prepend: Boolean = false,
  ): Boolean {
    val info = annotation.toAnnotationInfo(owner, source) ?: return false
    if (prepend) add(0, info) else add(info)
    return true
  }

  private fun UAnnotation.toAnnotationInfo(
    owner: PsiElement,
    source: AnnotationOrigin,
  ): AnnotationInfo? {
    val name = qualifiedName ?: return null
    return AnnotationInfo(this, name, owner, source)
  }

  private fun getRelevantAnnotations(
    evaluator: JavaEvaluator,
    annotated: UAnnotated,
    origin: AnnotationOrigin,
  ): List<AnnotationInfo> {
    @Suppress("UElementAsPsi") val owner = annotated as? PsiElement ?: return emptyList()
    val allAnnotations: List<UAnnotation> =
      evaluator.getAllAnnotations(annotated, inHierarchy = true)
    return filterRelevantAnnotations(evaluator, allAnnotations).mapNotNull {
      it.toAnnotationInfo(owner, origin)
    }
  }

  private fun getRelevantAnnotations(
    evaluator: JavaEvaluator,
    owner: PsiModifierListOwner,
    origin: AnnotationOrigin,
  ): List<AnnotationInfo> {
    val allAnnotations = evaluator.getAnnotations(owner, inHierarchy = true)
    return filterRelevantAnnotations(evaluator, allAnnotations).mapNotNull {
      it.toAnnotationInfo(owner, origin)
    }
  }

  private fun getRelevantAnnotations(
    evaluator: JavaEvaluator,
    owner: PsiModifierListOwner,
    inHierarchy: Boolean = true,
  ): List<UAnnotation> {
    val allAnnotations = evaluator.getAnnotations(owner, inHierarchy)
    return filterRelevantAnnotations(evaluator, allAnnotations)
  }

  /** Returns a list of annotations surrounding the given [annotated] element. */
  private fun getMemberAnnotations(
    context: JavaContext,
    annotated: PsiModifierListOwner,
  ): MutableList<AnnotationInfo> {
    return getMemberAnnotations(context.evaluator, annotated)
  }

  private fun getMemberAnnotations(
    evaluator: JavaEvaluator,
    annotated: PsiModifierListOwner,
  ): MutableList<AnnotationInfo> {
    // Using an ArrayDeque such that we can cheaply add/remove from the front of the
    // list (for example, after computing a list of annotations surrounding a call,
    // we prepend each parameter in turn to pass a full context list)
    val list = ArrayDeque<AnnotationInfo>()
    val containingClass: PsiClass =
      when (annotated) {
        is PsiMethod -> {
          list.addAnnotations(evaluator, annotated, METHOD)
          annotated.containingClass ?: return list
        }
        is PsiField -> {
          list.addAnnotations(evaluator, annotated, FIELD)
          annotated.containingClass ?: return list
        }
        is PsiClass -> {
          annotated
        }
        is PsiParameter -> {
          list.addAnnotations(evaluator, annotated, PARAMETER)
          val method = annotated.getParentOfType<PsiMethod>(true) ?: return list
          list.addAnnotations(evaluator, method, METHOD)
          method.containingClass ?: return list
        }
        is PsiPackage -> {
          // Simple name reference from package segments
          // (e.g., `java` or `io` from `java.io.Closeable`)
          // can be resolved to [PsiPackage].
          // Of course no meaningful annotations would be associated.
          return list
        }
        else -> {
          error("Unexpected $annotated")
        }
      }

    // Don't inherit annotations inside annotations; this can lead to false positives like
    // b/298283135 and while possible we don't have examples of annotations today
    // where this behavior would be desirable.
    if (!containingClass.isAnnotationType) {
      list.addAnnotations(evaluator, containingClass, CLASS)
    }

    var topLevelClass = containingClass
    var outerClass = containingClass.containingClass
    while (outerClass != null) {
      if (outerClass is PsiAnonymousClass) {
        break
      }
      list.addAnnotations(evaluator, outerClass, OUTER_CLASS)
      val outer = outerClass.containingClass
      if (outer != null) {
        topLevelClass = outerClass
        outerClass = outer
      } else {
        break
      }
    }

    val ktFile =
      topLevelClass.containingFile as? KtFile
        ?: (topLevelClass as? KtLightClass)?.kotlinOrigin?.containingKtFile
    ktFile?.annotationEntries?.forEach { entry ->
      val annotation = UastFacade.convertElement(entry, null) as? UAnnotation
      if (annotation != null) {
        list.addAnnotation(annotation, ktFile, FILE)
      }
    }

    val pkg = evaluator.getPackage(containingClass)
    if (pkg != null) {
      list.addAnnotations(evaluator, pkg, PACKAGE)
    }

    if (list.isNotEmpty()) {
      val skipAnnotations = driver.skipAnnotations
      if (skipAnnotations != null) {
        if (list.any { skipAnnotations.contains(it.qualifiedName) }) {
          list.clear()
          return list
        }
      }
    }

    return list
  }

  private fun checkAnnotations(
    context: JavaContext,
    argument: UElement,
    type: AnnotationUsageType,
    referenced: UAnnotated?,
    annotations: List<AnnotationInfo>,
  ) {
    // We do not manipulate or consult the element; it's only provided as a source
    @Suppress("UElementAsPsi") val owner = referenced as? PsiElement
    checkAnnotations(context, argument, type, owner, annotations)
  }

  private fun checkAnnotations(
    context: JavaContext,
    argument: UElement,
    type: AnnotationUsageType,
    referenced: PsiElement?,
    annotations: List<AnnotationInfo>,
  ) {
    val usageInfo = AnnotationUsageInfo(0, annotations, argument, referenced, type)

    for (index in annotations.indices) {
      val info = annotations[index]
      usageInfo.index = index
      val signature = info.qualifiedName
      val uastScanners = scanners.get(signature)
      if (!uastScanners.isNullOrEmpty()) {
        checkAnnotations(context, uastScanners, signature, argument, type, info, usageInfo)
      }

      // Also check just the name; we allow annotation checkers to just match by basename
      val name = signature.substringAfterLast('.')
      val simpleNameScanners = scanners.get(name)
      if (!simpleNameScanners.isNullOrEmpty()) {
        checkAnnotations(context, simpleNameScanners, signature, argument, type, info, usageInfo)
      }
    }
  }

  private fun checkAnnotations(
    context: JavaContext,
    uastScanners: Collection<SourceCodeScanner>,
    signature: String,
    argument: UElement,
    type: AnnotationUsageType,
    info: AnnotationInfo,
    usageInfo: AnnotationUsageInfo,
  ) {
    // Don't flag annotations that have already appeared in a closer scope
    if (usageInfo.anyCloser { it.qualifiedName == signature }) {
      return
    }
    for (scanner in uastScanners) {
      if (scanner.isApplicableAnnotationUsage(type)) {
        // Some annotations should not be treated as inherited through
        // the hierarchy: if that's the case for this annotation in
        // this scanner, check whether it's inherited and if so, skip it
        if (type != DEFINITION && !scanner.inheritAnnotation(signature) && info.isInherited()) {
          continue
        }
        scanner.visitAnnotationUsage(context, argument, info, usageInfo)
      }
    }
  }

  private fun checkAnnotations(
    context: XmlContext,
    element: Node,
    argument: UElement,
    type: AnnotationUsageType,
    referenced: PsiElement?,
    annotations: List<AnnotationInfo>,
  ) {
    val usageInfo = AnnotationUsageInfo(0, annotations, argument, referenced, type)

    for (index in annotations.indices) {
      val info = annotations[index]
      usageInfo.index = index
      val signature = info.qualifiedName
      val uastScanners = scanners.get(signature)
      if (!uastScanners.isNullOrEmpty()) {
        checkAnnotations(context, uastScanners, signature, element, type, info, usageInfo)
      }

      // Also check just the name; we allow annotation checkers to just match by basename
      val name = signature.substringAfterLast('.')
      val simpleNameScanners = scanners.get(name)
      if (!simpleNameScanners.isNullOrEmpty()) {
        checkAnnotations(context, simpleNameScanners, signature, element, type, info, usageInfo)
      }
    }
  }

  private fun checkAnnotations(
    context: XmlContext,
    uastScanners: Collection<SourceCodeScanner>,
    signature: String,
    argument: Node,
    type: AnnotationUsageType,
    info: AnnotationInfo,
    usageInfo: AnnotationUsageInfo,
  ) {
    // Don't flag annotations that have already appeared in a closer scope
    if (usageInfo.anyCloser { it.qualifiedName == signature }) {
      return
    }
    for (scanner in uastScanners) {
      if (scanner.isApplicableAnnotationUsage(type)) {
        // Some annotations should not be treated as inherited through
        // the hierarchy: if that's the case for this annotation in
        // this scanner, check whether it's inherited and if so, skip it
        if (type != DEFINITION && !scanner.inheritAnnotation(signature) && info.isInherited()) {
          continue
        }
        scanner.visitAnnotationUsage(context, argument, info, usageInfo)
      }
    }
  }

  // Visit the type of a declaration or parameter
  private fun visitDeclarationTypeReference(
    context: JavaContext,
    reference: UTypeReferenceExpression,
  ) {
    val psi = reference.sourcePsi ?: return
    if (psi is PsiCompiledElement) {
      // Make sure we don't visit binary elements -- b/312177842
      return
    }
    // In the code
    //    val x = List<T>
    // there is no UElement corresponding to "T" in the UAST tree, but there is
    // in the PSI tree. So to make sure that "T" is visited we send a visitor
    // through the PSI tree to look for types and turn them back into UElements
    // that can be passed on to the annotation checker.
    fun handlePsiTypeElement(psi: PsiElement) {
      val uElement = psi.toUElement()
      if (uElement is UTypeReferenceExpression) {
        val cls = context.evaluator.getTypeClass(uElement.type)
        if (cls != null) {
          val annotations = getMemberAnnotations(context, cls)
          checkAnnotations(context, uElement, CLASS_REFERENCE_AS_DECLARATION_TYPE, cls, annotations)
        }
      }
    }
    val psiVisitor: PsiElementVisitor =
      when (reference.lang) {
        is KotlinLanguage -> {
          object : KtTreeVisitor<Void>() {
            override fun visitTypeReference(typeReference: KtTypeReference, data: Void?): Void? {
              handlePsiTypeElement(typeReference)
              return super.visitTypeReference(typeReference, data)
            }
          }
        }
        is JavaLanguage -> {
          object : JavaRecursiveElementVisitor() {
            override fun visitTypeElement(type: PsiTypeElement) {
              handlePsiTypeElement(type)
              super.visitTypeElement(type)
            }
          }
        }
        else -> return
      }

    psi.accept(psiVisitor)
  }

  private fun visitImplicitTypeReference(context: JavaContext, type: PsiType, location: UElement) {
    type.accept(
      object : PsiTypeVisitor<Unit>() {
        override fun visitType(type: PsiType) {
          val cls = context.evaluator.getTypeClass(type) ?: return
          val annotations = getMemberAnnotations(context, cls)
          checkAnnotations(
            context,
            location,
            CLASS_REFERENCE_AS_IMPLICIT_DECLARATION_TYPE,
            cls,
            annotations,
          )
        }
      }
    )
  }

  fun visitXmlClassReference(context: XmlContext, reference: Node, referenced: PsiClass) {
    val parser = context.client.getUastParser(context.project)
    val annotations =
      getMemberAnnotations(parser.evaluator, referenced).ifEmpty {
        return
      }
    checkAnnotations(
      context,
      reference,
      UastEmptyExpression(null),
      AnnotationUsageType.XML_REFERENCE,
      referenced,
      annotations,
    )
  }

  // TODO: visitField too such that we can enforce initializer consistency with
  // declared constraints!

  fun visitMethod(context: JavaContext, method: UMethod) {
    val evaluator = context.evaluator

    // Same as with variables, if there is no return type reference then the typing
    // must be implicit.
    if (method.returnTypeReference?.sourcePsi != null) {
      visitDeclarationTypeReference(context, method.returnTypeReference!!)
    } else if (method.sourcePsi?.isPhysical != true) {
      val returnType = method.returnType
      if (returnType != null) {
        visitImplicitTypeReference(context, returnType, method)
      }
    }

    val methodAnnotations = getRelevantAnnotations(evaluator, method as UAnnotated, METHOD)
    if (methodAnnotations.isNotEmpty()) {
      // Check return values
      val body = method.uastBody?.skipParenthesizedExprDown()
      if (body != null && body !is UBlockExpression && body !is UReturnExpression) {
        // Kotlin implicit method
        checkAnnotations(context, body, METHOD_RETURN, method as UAnnotated, methodAnnotations)
      } else {
        method.accept(
          object : AbstractUastVisitor() {
            // Don't visit inner classes
            override fun visitClass(node: UClass): Boolean {
              return true
            }

            override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
              // Return statements inside the lambda may not refer to this method;
              // see for example 140626689
              return true
            }

            override fun visitReturnExpression(node: UReturnExpression): Boolean {
              val returnValue = node.returnExpression
              if (returnValue != null) {
                checkAnnotations(
                  context,
                  returnValue,
                  METHOD_RETURN,
                  method as UAnnotated,
                  methodAnnotations,
                )
              }
              return super.visitReturnExpression(node)
            }
          }
        )
      }
    }

    val superMethod = evaluator.getSuperMethod(method)
    if (superMethod == null) {
      // getSuperMethod does not return a PsiMethod for implicit default constructors
      if (method.isConstructor) {
        val uClass = method.getContainingUClass() ?: return
        if (!hasImplicitDefaultConstructor(uClass)) { // already reported as IMPLICIT_CONSTRUCTOR
          checkSuperImplicitConstructor(
            context,
            method,
            uClass,
            method,
            METHOD_OVERRIDE,
            method.uastParameters.isEmpty(),
          )
        }
      }
      return
    }
    val annotations = getMemberAnnotations(context, superMethod)
    checkAnnotations(context, method, METHOD_OVERRIDE, superMethod, annotations)
  }

  fun visitClass(context: JavaContext, node: UClass) {
    for (superType in node.uastSuperTypes) {
      // superType.tryResolve() does not work because the type reference
      // expressions are not implementing UResolvable
      val type = superType.type
      val resolved = PsiTypesUtil.getPsiClass(type) ?: continue
      val evaluator = context.evaluator
      val annotations = getRelevantAnnotations(evaluator, resolved, CLASS)
      checkAnnotations(context, superType, EXTENDS, resolved, annotations)
    }

    if (hasImplicitDefaultConstructor(node)) {
      // Search for explicit constructors up to hierarchy
      checkSuperImplicitConstructor(context, node, node, null, IMPLICIT_CONSTRUCTOR, true)
    }
  }

  /**
   * From [uClass], check its super constructor (recursively) to see if it extends/delegates to an
   * annotated super constructor. This is special-cased because when there are implicit
   * constructors, resolve will return null.
   */
  private fun checkSuperImplicitConstructor(
    context: JavaContext,
    argument: UElement,
    uClass: UClass,
    method: UMethod?,
    usageType: AnnotationUsageType,
    canOverride: Boolean,
  ) {
    val base = uClass.uastSuperTypes.firstOrNull() ?: return
    var cls = PsiTypesUtil.getPsiClass(base.type)
    while (cls != null) {
      val constructors = cls.constructors
      if (constructors.isNotEmpty()) {
        val defaultConstructor = constructors.firstOrNull { it.parameterList.parametersCount == 0 }
        if (defaultConstructor != null) {
          val evaluator = context.evaluator
          val annotations = getRelevantAnnotations(evaluator, defaultConstructor, METHOD)

          if (canOverride) {
            checkAnnotations(context, argument, usageType, defaultConstructor, annotations)
          }

          if (method != null && annotations.isNotEmpty() && method.isConstructor) {
            val body = method.uastBody
            if (body is UBlockExpression) {
              val implicit = body.expressions.firstOrNull()?.isSuperCall()?.not() ?: true
              if (implicit) {
                checkAnnotations(
                  context,
                  argument,
                  IMPLICIT_CONSTRUCTOR_CALL,
                  defaultConstructor,
                  annotations,
                )
              }
            }
          }
        }
      }
      cls = cls.superClass
    }
  }

  /**
   * Returns whether this call is a constructor delegation call. It *should* be a
   * [USuperExpression], but sometimes it shows up as a plain [UCallExpression]. Note that this
   * method should only be called for known calls to constructors; it does not check for this. In
   * other words, this method can return true if you call it on a random `super` call in a method
   * referencing a non-constructor.
   */
  private fun UExpression.isSuperCall(): Boolean {
    return this is USuperExpression ||
      sourcePsi is KtSuperTypeCallEntry ||
      sourcePsi is KtConstructorDelegationCall ||
      this is UCallExpression && receiver == null && methodIdentifier?.name == "super"
  }

  fun visitSimpleNameReferenceExpression(
    context: JavaContext,
    node: USimpleNameReferenceExpression,
  ) {
    /* Pending:
    // In a qualified expression like x.y.z, only do field reference checks on z?
    val parent = node.uastParent
    if (parent is UQualifiedReferenceExpression && parent.selector != node) {
        return
    }
    */

    val field = node.resolve() as? PsiModifierListOwner
    if (
      field is PsiField ||
        field is PsiMethod ||
        field?.toUElement()?.sourcePsi is KtObjectDeclaration
    ) {
      if (isOverloadedMethodCall(node)) return
      val annotations = getMemberAnnotations(context, field)
      checkAnnotations(context, node, FIELD_REFERENCE, field, annotations)
    }
  }

  private fun isOverloadedMethodCall(node: UExpression): Boolean {
    var prev: UElement = node
    var parent = prev.uastParent
    while (true) {
      if (
        parent is UParenthesizedExpression ||
          parent is UQualifiedReferenceExpression && parent.selector === prev
      ) {
        prev = parent
        parent = parent.uastParent ?: break
      } else if (parent is UBinaryExpression && parent.leftOperand === prev) {
        val operatorMethod = parent.resolveOverloadedOperator()
        if (operatorMethod != null && parent.operator is UastBinaryOperator.AssignOperator) {
          // The call is just the left hand side expression of an overloaded operator
          // so we won't actually call it (the overloaded operator will instead
          // be called, and that's handled separately via visitBinaryExpression)
          return true
        }
        break
      } else {
        break
      }
    }
    return false
  }

  fun visitCallExpression(context: JavaContext, call: UCallExpression) {
    val method = call.resolve()
    // Implicit, generated, default constructors resolve to null, but we still want to check
    // this case using the class's annotations. So thus, check a null if it represents
    // a constructor call.
    if (method == null) {
      // KotlinUObjectLiteralExpression is resolved to its super type, which is revisited as a call
      // of KtSuperTypeCallEntry
      // To avoid duplicate issues---one on `object : SuperType(...) { }` and the other on
      // `SuperType(...)`, skip here
      // because the latter (an issue on the super type call) is much narrower scope to report an
      // issue.
      if (call is UObjectLiteralExpression && isKotlin(call.lang)) return
      checkCallUnresolved(context, call)
    } else {
      checkCall(context, method, call)
    }
  }

  fun visitCallableReferenceExpression(
    context: JavaContext,
    methodReference: UCallableReferenceExpression,
  ) {
    val method = methodReference.resolve() as? PsiMethod ?: return
    val annotations = getMemberAnnotations(context, method)
    checkAnnotations(context, methodReference, METHOD_REFERENCE, method, annotations)
  }

  fun visitAnnotation(context: JavaContext, annotation: UAnnotation) {
    // Check annotation references; these are a form of method call
    val qualifiedName = annotation.qualifiedName
    if (
      qualifiedName == null || qualifiedName.startsWith("java.") || qualifiedName == KOTLIN_SUPPRESS
    ) {
      return
    }

    val selfInfo =
      AnnotationInfo(annotation, qualifiedName, annotation.sourcePsi ?: annotation.javaPsi, SELF)
    checkAnnotations(context, annotation, DEFINITION, null as PsiElement?, listOf(selfInfo))

    if (SUPPORT_ANNOTATIONS_PREFIX.isPrefix(qualifiedName)) {
      // Don't waste time resolving and looking up methods for the known androidx annotations
      // since we know we don't have annotations on the actual annotation attributes there
      // (except for @IntRange on @RequiresApi but that isn't an interesting attribute
      // for detectors to listen to; we enforce usage there in AnnotationDetector)
      return
    }

    val attributeValues = annotation.attributeValues
    if (attributeValues.isEmpty()) {
      return
    }

    val resolved = annotation.resolve() ?: return

    for (expression in attributeValues) {
      val name = expression.name ?: ATTR_VALUE
      val methods = resolved.findMethodsByName(name, false)
      if (methods.size == 1) {
        val method = methods[0]
        val evaluator = context.evaluator
        val methodAnnotations = getRelevantAnnotations(evaluator, method, METHOD)
        if (methodAnnotations.isNotEmpty()) {
          val value = expression.expression
          checkAnnotations(context, value, ANNOTATION_REFERENCE, method, methodAnnotations)
        }
      }
    }
  }

  fun visitEnumConstant(context: JavaContext, constant: UEnumConstant) {
    val method = constant.resolveMethod()
    if (method != null) {
      checkCall(context, method, constant)
    }
  }

  fun visitArrayAccessExpression(context: JavaContext, expression: UArrayAccessExpression) {
    val arrayExpression = expression.receiver
    if (arrayExpression is UReferenceExpression) {
      val resolved = arrayExpression.resolve()
      if (resolved is PsiModifierListOwner) {
        val evaluator = context.evaluator
        val origin = if (resolved is PsiMethod) METHOD else FIELD
        val methodAnnotations = getRelevantAnnotations(evaluator, resolved, origin)
        if (methodAnnotations.isNotEmpty()) {
          checkContextAnnotations(context, expression, methodAnnotations, resolved)
        }
      }
    }

    // Operator overload?
    val method = expression.resolveOperator() ?: return
    val call = expression.asCall(method)
    checkCall(context, method, call)
  }

  fun visitVariable(context: JavaContext, variable: UVariable) {
    val evaluator = context.evaluator
    val methodAnnotations = getRelevantAnnotations(evaluator, variable as UAnnotated, VARIABLE)
    if (methodAnnotations.isNotEmpty()) {
      checkContextAnnotations(context, variable, methodAnnotations, variable)
    }

    // Handle type annotations--the explicitly specified types of declarations--separately
    // like the T's in "val x: T" and "T t;"
    val typeReference = variable.typeReference
    // The sourcePsi of the UVariable can be a Kotlin object declaration.
    // A Kotlin object declaration annotated with A is a class-like declaration, and the class
    // will be treated as though annotated with A. We don't want to flag the type of the object
    // declaration itself as being a class reference to the class (annotated with A), so we
    // skip object declarations here.
    if (typeReference != null && variable.sourcePsi !is KtObjectDeclaration) {
      if (typeReference.sourcePsi != null) {
        visitDeclarationTypeReference(context, typeReference)
      } else {
        // If the reference has no PSI representation, then it must be an implicit typing
        visitImplicitTypeReference(context, typeReference.type, variable)
      }
    }

    // Check the initializer to see if it is an annotated element
    // (AnnotationUsageType.ASSIGNMENT_LHS)
    val initializer = variable.uastInitializer
    if (initializer is USimpleNameReferenceExpression) {
      val resolved = initializer.tryResolve()
      if (
        resolved != null && resolved is PsiModifierListOwner && !isOverloadedMethodCall(initializer)
      ) {
        val initializerAnnotations =
          if (resolved is PsiLocalVariable)
            getRelevantAnnotations(
              context.evaluator,
              resolved as? UAnnotated ?: resolved.toUElement() as UAnnotated,
              VARIABLE,
            )
          else getMemberAnnotations(context, resolved)
        if (initializerAnnotations.isNotEmpty()) {
          checkAnnotations(context, variable, ASSIGNMENT_LHS, resolved, initializerAnnotations)
        }
      }
    }
  }

  /** Extract the relevant annotations from the call and run the checks on the annotations. */
  private fun checkCall(context: JavaContext, method: PsiMethod, call: UExpression) =
    doCheckCall(context, method, call, null)

  /**
   * Extract the relevant annotations from the call and run the checks on the annotations.<p>
   *
   * This method is used in cases where UCallExpression.resolve() returns null. One important case
   * this happens is when an implicit, generated, default constructor (a class with no explicit
   * constructor in the source code) is called.
   */
  private fun checkCallUnresolved(context: JavaContext, call: UCallExpression) {
    val evaluator = context.evaluator
    val containingClass =
      call.classReference?.resolve() as? PsiClass
        ?: (call.sourcePsi as? PsiNewExpression)?.anonymousClass?.baseClassType?.let {
          evaluator.getTypeClass(it)
        }
    doCheckCall(context, null, call, containingClass)

    if (call.isSuperCall() && call.valueArgumentCount == 0) {
      // When invoking an implicit super constructor, PSI does not resolve the call (because there
      // isn't a modeled
      // light PsiMethod representing the implicit constructor), but we still want to check these
      val method = call.getParentOfType(UMethod::class.java) ?: return
      if (method.isConstructor) {
        val uClass = method.getContainingUClass() ?: return
        checkSuperImplicitConstructor(context, call, uClass, method, IMPLICIT_CONSTRUCTOR, true)
      }
    }
  }

  /** Do the checks of a call based on the method, class, and package annotations given. */
  private fun doCheckCall(
    context: JavaContext,
    method: PsiMethod?,
    call: UExpression,
    containingClass: PsiClass?,
  ) {
    val evaluator = context.evaluator
    if (method != null) {
      val annotations = getMemberAnnotations(context, method)

      if (annotations.isNotEmpty()) {
        checkAnnotations(context, call, METHOD_CALL, method, annotations)
        val local = getRelevantAnnotations(evaluator, method, METHOD)
        if (local.isNotEmpty()) {
          checkContextAnnotations(context, call, local, method)
        }

        // The method annotations should not be passed as "surrounding" the parameter
        // annotations; they're considered return value annotations
        while (true) {
          val first = annotations.firstOrNull()?.origin
          if (first == METHOD || first == PROPERTY_DEFAULT) {
            annotations.removeFirst()
          } else {
            break
          }
        }
      }

      val mapping: Map<UExpression, PsiModifierListOwner> =
        when (call) {
          is UCallExpression -> evaluator.computeArgumentMapping(call, method)
          is UUnaryExpression -> return
          else -> {
            error("Unexpected call type $call")
          }
        }
      for ((argument, parameter) in mapping) {
        val added = annotations.addAnnotations(evaluator, parameter, PARAMETER, prepend = true)
        if (added > 0) {
          checkAnnotations(context, argument, METHOD_CALL_PARAMETER, method, annotations)
          repeat(added) { annotations.removeFirst() }
        }
      }
    } else if (containingClass != null) {
      // No method: this is to a constructor that doesn't actually have source (e.g. a default
      // constructor);
      // we still want to check any annotations for the class, outer classes and the surrounding
      // package.
      val annotations = getMemberAnnotations(context, containingClass)
      checkAnnotations(context, call, METHOD_CALL, null as PsiElement?, annotations)
    }
  }

  private fun filterRelevantAnnotations(
    evaluator: JavaEvaluator,
    annotations: List<UAnnotation>,
  ): List<UAnnotation> {
    var result: MutableList<UAnnotation>? = null
    val length = annotations.size
    if (length == 0) {
      return annotations
    }
    val visitedAnnotations = mutableSetOf<UAnnotation>()
    val wave = mutableListOf<List<UAnnotation>>()
    wave.add(annotations)
    while (wave.isNotEmpty() && result == null) {
      val annotationsAtWave = wave.removeFirst()
      for (annotation in annotationsAtWave) {
        if (!visitedAnnotations.add(annotation)) continue
        val signature = annotation.qualifiedName ?: continue
        val name = signature.substringAfterLast('.')
        val relevant = relevantAnnotations.contains(signature) || relevantAnnotations.contains(name)
        if (relevant) {
          // Common case: there's just one annotation; no need to create a list copy
          if (length == 1) {
            return annotations
          }
          if (result == null) {
            result = ArrayList(2)
          }
          result.add(annotation)
          continue
        } else if (signature.startsWith("java.") || signature.startsWith("kotlin.")) {
          // @Override, @SuppressWarnings etc. Ignore, because they're not a possible typedef match,
          // which
          // is the only remaining thing we're looking for.
          continue
        } else if (isPlatformAnnotation(signature)) {
          if (result == null) {
            result = ArrayList(2)
          }
          result.add(annotation.fromPlatformAnnotation(signature))
          continue
        }

        // Special case @IntDef and @StringDef: These are used on annotations
        // themselves. For example, you create a new annotation named @foo.bar.Baz,
        // annotate it with @IntDef, and then use @foo.bar.Baz in your signatures.
        // Here we want to map from @foo.bar.Baz to the corresponding int def.
        // Don't need to compute this if performing @IntDef or @StringDef lookup
        val cls = annotation.resolve()
        if (cls == null || !cls.isAnnotationType) {
          continue
        }
        val metaAnnotations = evaluator.getAnnotations(cls, inHierarchy = false)
        wave.add(metaAnnotations)
        for (j in metaAnnotations.indices) {
          val inner = metaAnnotations[j]
          val innerName = inner.qualifiedName ?: continue
          if (relevantAnnotations.contains(innerName)) {
            if (result == null) {
              result = ArrayList(2)
            }
            result.add(inner)
          } else if (isPlatformAnnotation(innerName)) {
            if (result == null) {
              result = ArrayList(2)
            }
            result.add(inner.fromPlatformAnnotation(innerName))
          }
        }
      }
    }

    return result ?: emptyList()
  }
}
