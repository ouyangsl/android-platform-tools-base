/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.SdkConstants.ANDROID_PKG
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlScannerConstants
import com.android.tools.lint.detector.api.acceptSourceFile
import com.android.tools.lint.detector.api.asCall
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.android.tools.lint.detector.api.interprocedural.CallGraphVisitor
import com.android.tools.lint.detector.api.interprocedural.ClassHierarchyVisitor
import com.android.tools.lint.detector.api.interprocedural.IntraproceduralDispatchReceiverVisitor
import com.android.tools.lint.detector.api.isDuplicatedOverload
import com.google.common.base.Joiner
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UArrayAccessExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithPattern
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UBreakExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UCatchClause
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UClassInitializer
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UContinueExpression
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UDeclarationsExpression
import org.jetbrains.uast.UDoWhileExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UEnumConstant
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UExpressionList
import org.jetbrains.uast.UField
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UForEachExpression
import org.jetbrains.uast.UForExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.ULabeledExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UNamedExpression
import org.jetbrains.uast.UObjectLiteralExpression
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPatternExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UPostfixExpression
import org.jetbrains.uast.UPrefixExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.USwitchClauseExpression
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UWhileExpression
import org.jetbrains.uast.UYieldExpression
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.util.isConstructorCall
import org.jetbrains.uast.util.isMethodCall
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Specialized visitor for running detectors on a Java AST. It operates in three phases:
 * 1. First, it computes a set of maps where it generates a map from each significant AST attribute
 *    (such as method call names) to a list of detectors to consult whenever that attribute is
 *    encountered. Examples of "attributes" are method names, Android resource identifiers, and
 *    general AST node types such as "cast" nodes etc. These are defined on the [SourceCodeScanner]
 *    interface.
 * 2. Second, it iterates over the document a single time, delegating to the detectors found at each
 *    relevant AST attribute.
 * 3. Finally, it calls the remaining visitors (those that need to process a whole document on their
 *    own).
 *
 * It also notifies all the detectors before and after the document is processed such that they can
 * do pre- and post-processing.
 */
internal class UElementVisitor
constructor(driver: LintDriver, private val parser: UastParser, detectors: List<Detector>) {

  private val methodDetectors =
    Maps.newHashMapWithExpectedSize<String, MutableList<SourceCodeScanner>>(120)
  private val constructorDetectors =
    Maps.newHashMapWithExpectedSize<String, MutableList<SourceCodeScanner>>(16)
  private val referenceDetectors =
    Maps.newHashMapWithExpectedSize<String, MutableList<SourceCodeScanner>>(12)
  private val resourceFieldDetectors = ArrayList<SourceCodeScanner>()
  private val allDetectors = ArrayList<SourceCodeScannerWithContext>(detectors.size)
  private val uastHandlerDetectors =
    Maps.newHashMapWithExpectedSize<Class<out UElement>, MutableList<SourceCodeScannerWithContext>>(
      25
    )
  private val superClassDetectors = HashMap<String, MutableList<SourceCodeScanner>>(40)
  private val annotationHandler: AnnotationHandler?
  private val callGraphDetectors = ArrayList<SourceCodeScanner>()

  init {
    fun <K, V> Iterable<K>.associateWith(target: V, grouping: MutableMap<K, MutableList<V>>) {
      for (k in this) grouping.getOrPut(k) { ArrayList(SAME_TYPE_COUNT) }.add(target)
    }

    fun <X : List<String>> X.ensureNotAll(): X = apply {
      // not supported in Java visitors; adding a method invocation node is trivial
      // for that case.
      assert(this !== XmlScannerConstants.ALL)
    }

    val annotationScanners = HashMap<String, MutableList<SourceCodeScanner>>()

    for (detector in detectors) {
      val v = SourceCodeScannerWithContext(detector as SourceCodeScanner)

      allDetectors.add(v)
      if (detector.appliesToResourceRefs()) resourceFieldDetectors.add(detector)
      if (detector.isCallGraphRequired()) callGraphDetectors.add(detector)

      with(detector) {
        getApplicableMethodNames()?.ensureNotAll()?.associateWith(detector, methodDetectors)
        applicableSuperClasses()?.associateWith(detector, superClassDetectors)
        getApplicableUastTypes()?.associateWith(v, uastHandlerDetectors)
        getApplicableConstructorTypes()
          ?.ensureNotAll()
          ?.associateWith(detector, constructorDetectors)
        getApplicableReferenceNames()?.ensureNotAll()?.associateWith(detector, referenceDetectors)
        applicableAnnotations()?.associateWith(detector, annotationScanners)
      }
    }

    annotationHandler =
      when {
        annotationScanners.isEmpty() -> null
        else -> AnnotationHandler(driver, annotationScanners)
      }
    parser.evaluator.setRelevantAnnotations(annotationHandler?.relevantAnnotations)
  }

  fun visitFile(context: JavaContext) {
    try {
      val uastParser = context.uastParser

      val uFile = uastParser.parse(context) ?: return

      // (Immediate return if null: No need to log this; the parser should be reporting
      // a full warning (such as IssueRegistry#PARSER_ERROR) with details, location, etc.)

      val client = context.client
      try {
        val sourcePsi = uFile.sourcePsi
        context.setJavaFile(sourcePsi) // needed for getLocation
        context.uastFile = uFile

        client.runReadAction {
          for (v in allDetectors) {
            v.setContext(context)
            v.beforeCheckFile(context)
          }
        }

        if (superClassDetectors.isNotEmpty()) {
          client.runReadAction {
            val visitor = SuperclassUastVisitor(context)
            uFile.acceptSourceFile(visitor)
          }
        }

        if (
          methodDetectors.isNotEmpty() ||
            resourceFieldDetectors.isNotEmpty() ||
            constructorDetectors.isNotEmpty() ||
            referenceDetectors.isNotEmpty() ||
            annotationHandler != null
        ) {
          client.runReadAction {
            // TODO: Do we need to break this one up into finer grain locking units
            val visitor = DelegatingUastVisitor(context)
            uFile.acceptSourceFile(visitor)
          }
        } else {
          // Note that the DelegatingPsiVisitor is a subclass of DispatchPsiVisitor
          // so the above includes the below as well (through super classes)
          if (uastHandlerDetectors.isNotEmpty()) {
            client.runReadAction {
              val visitor = DispatchUastVisitor()
              uFile.acceptSourceFile(visitor)
            }
          }
        }

        client.runReadAction {
          for (v in allDetectors) {
            ProgressManager.checkCanceled()
            v.afterCheckFile(context)
          }
        }
      } finally {
        context.setJavaFile(null)
        context.uastFile = null
      }
    } catch (e: Throwable) {
      // Don't allow lint bugs to take down the whole build. TRY to log this as a
      // lint error instead!
      LintDriver.handleDetectorError(context, context.driver, e)
    }
  }

  fun visitGroups(projectContext: Context, allContexts: List<JavaContext>) {
    if (allContexts.isNotEmpty() && callGraphDetectors.isNotEmpty()) {
      val callGraph =
        projectContext.client.runReadAction(
          Computable { generateCallGraph(projectContext, parser, allContexts) }
        )
      if (callGraph != null) {
        for (scanner in callGraphDetectors) {
          projectContext.client.runReadAction {
            ProgressManager.checkCanceled()
            scanner.analyzeCallGraph(projectContext, callGraph)
          }
        }
      }
    }
  }

  private fun generateCallGraph(
    projectContext: Context,
    parser: UastParser,
    contexts: List<JavaContext>,
  ): CallGraphResult? {
    if (contexts.isEmpty()) {
      return null
    }

    try {
      val chaVisitor = ClassHierarchyVisitor()
      val receiverEvalVisitor = IntraproceduralDispatchReceiverVisitor(chaVisitor.classHierarchy)
      val callGraphVisitor =
        CallGraphVisitor(receiverEvalVisitor.receiverEval, chaVisitor.classHierarchy, false)

      for (context in contexts) {
        val uFile = parser.parse(context)
        uFile?.accept(chaVisitor)
      }
      for (context in contexts) {
        val uFile = parser.parse(context)
        uFile?.accept(receiverEvalVisitor)
      }
      for (context in contexts) {
        val uFile = parser.parse(context)
        uFile?.accept(callGraphVisitor)
      }

      val callGraph = callGraphVisitor.callGraph
      val receiverEval = receiverEvalVisitor.receiverEval
      return CallGraphResult(callGraph, receiverEval)
    } catch (oom: OutOfMemoryError) {
      val detectors = Lists.newArrayList<String>()
      for (detector in callGraphDetectors) {
        detectors.add(detector.javaClass.simpleName)
      }
      val detectorNames = "[" + Joiner.on(", ").join(detectors) + "]"
      var message =
        "Lint ran out of memory while building a callgraph (requested by " +
          "these detectors: " +
          detectorNames +
          "). You can either disable these " +
          "checks, or give lint more heap space."
      if (LintClient.isGradle) {
        message +=
          " For example, to set the Gradle daemon to use 4 GB, edit " +
            "`gradle.properties` to contains `org.gradle.jvmargs=-Xmx4g`"
      }
      projectContext.report(
        IssueRegistry.LINT_ERROR,
        Location.create(projectContext.project.dir),
        message,
      )
      return null
    }
  }

  /**
   * A stateful [SourceCodeScanner] that can [setContext] then remember a (lazily created)
   * [uastHandler]. (In contrast, a vanilla [SourceCodeScanner] would [createUastHandler] each
   * time).
   */
  private class SourceCodeScannerWithContext(private val uastScanner: SourceCodeScanner) :
    SourceCodeScanner by uastScanner {
    private var cache: UElementHandler? = null
    private lateinit var context: JavaContext

    val uastHandler: UElementHandler
      get() {
        if (cache == null) {
          cache = uastScanner.createUastHandler(context) ?: UElementHandler.NONE
        }
        return cache!!
      }

    fun setContext(context: JavaContext) {
      this.context = context
      cache = null
    }
  }

  private inner class SuperclassUastVisitor(private val context: JavaContext) :
    AbstractUastVisitor() {

    override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
      val type = node.functionalInterfaceType
      if (type is PsiClassType) {
        val resolved = type.resolve()
        if (resolved != null) {
          for (uastScanner in getRelevantDetectors(resolved)) {
            uastScanner.visitClass(context, node)
          }
        }
      }
      return super.visitLambdaExpression(node)
    }

    override fun visitClass(node: UClass): Boolean {
      val result = super.visitClass(node)
      for (uastScanner in getRelevantDetectors(node)) {
        uastScanner.visitClass(context, node)
      }
      return result
    }

    private fun getRelevantDetectors(klass: PsiClass): Sequence<SourceCodeScanner> {
      if (klass is PsiTypeParameter)
        return sequenceOf() // See Javadoc for SourceCodeScanner.visitClass.
      val superClasses = InheritanceUtil.getSuperClasses(klass).asSequence()
      return (superClasses + klass) // Include self.
        .mapNotNull { it.qualifiedName?.let(superClassDetectors::get) }
        .flatten()
        .distinct()
    }
  }

  private open inner class DispatchUastVisitor : AbstractUastVisitor() {

    override fun visitAnnotation(node: UAnnotation): Boolean {
      eachDetectorVisit(node, UElementHandler::visitAnnotation)
      return super.visitAnnotation(node)
    }

    override fun visitArrayAccessExpression(node: UArrayAccessExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitArrayAccessExpression)
      return super.visitArrayAccessExpression(node)
    }

    override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitBinaryExpression)
      return super.visitBinaryExpression(node)
    }

    override fun visitBinaryExpressionWithType(node: UBinaryExpressionWithType): Boolean {
      eachDetectorVisit(node, UElementHandler::visitBinaryExpressionWithType)
      return super.visitBinaryExpressionWithType(node)
    }

    override fun visitBlockExpression(node: UBlockExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitBlockExpression)
      return super.visitBlockExpression(node)
    }

    override fun visitBreakExpression(node: UBreakExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitBreakExpression)
      return super.visitBreakExpression(node)
    }

    override fun visitCallExpression(node: UCallExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitCallExpression)
      return super.visitCallExpression(node)
    }

    override fun visitCallableReferenceExpression(node: UCallableReferenceExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitCallableReferenceExpression)
      return super.visitCallableReferenceExpression(node)
    }

    override fun visitCatchClause(node: UCatchClause): Boolean {
      eachDetectorVisit(node, UElementHandler::visitCatchClause)
      return super.visitCatchClause(node)
    }

    override fun visitClass(node: UClass): Boolean {
      eachDetectorVisit(node, UElementHandler::visitClass)
      return super.visitClass(node)
    }

    override fun visitClassLiteralExpression(node: UClassLiteralExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitClassLiteralExpression)
      return super.visitClassLiteralExpression(node)
    }

    override fun visitContinueExpression(node: UContinueExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitContinueExpression)
      return super.visitContinueExpression(node)
    }

    override fun visitDeclaration(node: UDeclaration): Boolean {
      eachDetectorVisit(node, UElementHandler::visitDeclaration)
      return super.visitDeclaration(node)
    }

    override fun visitDeclarationsExpression(node: UDeclarationsExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitDeclarationsExpression)
      return super.visitDeclarationsExpression(node)
    }

    override fun visitDoWhileExpression(node: UDoWhileExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitDoWhileExpression)
      return super.visitDoWhileExpression(node)
    }

    override fun visitElement(node: UElement): Boolean {
      eachDetectorVisit(node, UElementHandler::visitElement)
      return super.visitElement(node)
    }

    override fun visitEnumConstant(node: UEnumConstant): Boolean {
      eachDetectorVisit(node, UElementHandler::visitEnumConstant)
      return super.visitEnumConstant(node)
    }

    override fun visitExpression(node: UExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitExpression)
      return super.visitExpression(node)
    }

    override fun visitExpressionList(node: UExpressionList): Boolean {
      eachDetectorVisit(node, UElementHandler::visitExpressionList)
      return super.visitExpressionList(node)
    }

    override fun visitField(node: UField): Boolean {
      eachDetectorVisit(node, UElementHandler::visitField)
      return super.visitField(node)
    }

    override fun visitFile(node: UFile): Boolean {
      eachDetectorVisit(node, UElementHandler::visitFile)
      return super.visitFile(node)
    }

    override fun visitForEachExpression(node: UForEachExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitForEachExpression)
      return super.visitForEachExpression(node)
    }

    override fun visitForExpression(node: UForExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitForExpression)
      return super.visitForExpression(node)
    }

    override fun visitIfExpression(node: UIfExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitIfExpression)
      return super.visitIfExpression(node)
    }

    override fun visitImportStatement(node: UImportStatement): Boolean {
      eachDetectorVisit(node, UElementHandler::visitImportStatement)
      return super.visitImportStatement(node)
    }

    override fun visitInitializer(node: UClassInitializer): Boolean {
      eachDetectorVisit(node, UElementHandler::visitInitializer)
      return super.visitInitializer(node)
    }

    override fun visitLabeledExpression(node: ULabeledExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitLabeledExpression)
      return super.visitLabeledExpression(node)
    }

    override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitLambdaExpression)
      return super.visitLambdaExpression(node)
    }

    override fun visitLiteralExpression(node: ULiteralExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitLiteralExpression)
      return super.visitLiteralExpression(node)
    }

    override fun visitLocalVariable(node: ULocalVariable): Boolean {
      eachDetectorVisit(node, UElementHandler::visitLocalVariable)
      return super.visitLocalVariable(node)
    }

    override fun visitMethod(node: UMethod): Boolean {
      if (node.isDuplicatedOverload()) {
        return true
      }
      eachDetectorVisit(node, UElementHandler::visitMethod)
      return super.visitMethod(node)
    }

    override fun visitNamedExpression(node: UNamedExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitNamedExpression)
      return super.visitNamedExpression(node)
    }

    override fun visitObjectLiteralExpression(node: UObjectLiteralExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitObjectLiteralExpression)
      return super.visitObjectLiteralExpression(node)
    }

    override fun visitParameter(node: UParameter): Boolean {
      eachDetectorVisit(node, UElementHandler::visitParameter)
      return super.visitParameter(node)
    }

    override fun visitParenthesizedExpression(node: UParenthesizedExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitParenthesizedExpression)
      return super.visitParenthesizedExpression(node)
    }

    override fun visitPolyadicExpression(node: UPolyadicExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitPolyadicExpression)
      return super.visitPolyadicExpression(node)
    }

    override fun visitPostfixExpression(node: UPostfixExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitPostfixExpression)
      return super.visitPostfixExpression(node)
    }

    override fun visitPrefixExpression(node: UPrefixExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitPrefixExpression)
      return super.visitPrefixExpression(node)
    }

    override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitQualifiedReferenceExpression)
      return super.visitQualifiedReferenceExpression(node)
    }

    override fun visitReturnExpression(node: UReturnExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitReturnExpression)
      return super.visitReturnExpression(node)
    }

    override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitSimpleNameReferenceExpression)
      return super.visitSimpleNameReferenceExpression(node)
    }

    override fun visitSuperExpression(node: USuperExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitSuperExpression)
      return super.visitSuperExpression(node)
    }

    override fun visitSwitchClauseExpression(node: USwitchClauseExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitSwitchClauseExpression)
      return super.visitSwitchClauseExpression(node)
    }

    override fun visitSwitchExpression(node: USwitchExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitSwitchExpression)
      return super.visitSwitchExpression(node)
    }

    override fun visitThisExpression(node: UThisExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitThisExpression)
      return super.visitThisExpression(node)
    }

    override fun visitThrowExpression(node: UThrowExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitThrowExpression)
      return super.visitThrowExpression(node)
    }

    override fun visitTryExpression(node: UTryExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitTryExpression)
      return super.visitTryExpression(node)
    }

    override fun visitTypeReferenceExpression(node: UTypeReferenceExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitTypeReferenceExpression)
      return super.visitTypeReferenceExpression(node)
    }

    override fun visitUnaryExpression(node: UUnaryExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitUnaryExpression)
      return super.visitUnaryExpression(node)
    }

    override fun visitVariable(node: UVariable): Boolean {
      eachDetectorVisit(node, UElementHandler::visitVariable)
      return super.visitVariable(node)
    }

    override fun visitWhileExpression(node: UWhileExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitWhileExpression)
      return super.visitWhileExpression(node)
    }

    @Suppress("UnstableApiUsage")
    override fun visitYieldExpression(node: UYieldExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitYieldExpression)
      return super.visitYieldExpression(node)
    }

    @Suppress("UnstableApiUsage")
    override fun visitBinaryExpressionWithPattern(node: UBinaryExpressionWithPattern): Boolean {
      eachDetectorVisit(node, UElementHandler::visitBinaryExpressionWithPattern)
      return super.visitBinaryExpressionWithPattern(node)
    }

    @Suppress("UnstableApiUsage")
    override fun visitPatternExpression(node: UPatternExpression): Boolean {
      eachDetectorVisit(node, UElementHandler::visitPatternExpression)
      return super.visitPatternExpression(node)
    }

    private inline fun <reified Node : UElement> eachDetectorVisit(
      node: Node,
      visit: UElementHandler.(Node) -> Unit,
    ) = uastHandlerDetectors[Node::class.java]?.forEach { it.uastHandler.visit(node) }
  }

  /**
   * Performs common AST searches for method calls and R-type-field references. Note that this is a
   * specialized form of the [DispatchUastVisitor].
   */
  private inner class DelegatingUastVisitor constructor(private val mContext: JavaContext) :
    DispatchUastVisitor() {
    private val mVisitResources: Boolean = resourceFieldDetectors.isNotEmpty()
    private val mVisitMethods: Boolean = methodDetectors.isNotEmpty()
    private val mVisitConstructors: Boolean = constructorDetectors.isNotEmpty()
    private val mVisitReferences: Boolean = referenceDetectors.isNotEmpty()
    private var aliasedImports = false

    override fun visitImportStatement(node: UImportStatement): Boolean {
      (node.sourcePsi as? KtImportDirective)?.alias?.name?.let { aliasedImports = true }
      return super.visitImportStatement(node)
    }

    override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
      if (mVisitReferences || mVisitResources) {
        ProgressManager.checkCanceled()
      }

      if (mVisitReferences) {
        val identifier = node.identifier
        val list = referenceDetectors[identifier]
        if (list != null) {
          val referenced = node.resolve()
          if (referenced != null) {
            for (uastScanner in list) {
              uastScanner.visitReference(mContext, node, referenced)
            }
          }
        }

        if (aliasedImports) {
          val referenced = node.resolve()
          if (referenced is PsiNamedElement) {
            val name = referenced.name
            if (name != null && name != identifier) {
              referenceDetectors[name]?.forEach { uastScanner ->
                uastScanner.visitReference(mContext, node, referenced)
              }
            }
          }
        }
      }

      if (mVisitResources) {
        val reference = ResourceReference.get(node)
        if (reference != null) {
          for (uastScanner in resourceFieldDetectors) {
            uastScanner.visitResourceReference(
              mContext,
              reference.node,
              reference.type,
              reference.name,
              reference.`package` == ANDROID_PKG,
            )
          }
        } else if (aliasedImports && node.resolve() == null) {
          val identifier = node.identifier
          // Resolving a reference failed, but we're in a compilation unit that has
          // aliased imports; as a workaround check the import statements
          for (import in mContext.uastFile?.imports ?: emptyList()) {
            if (import.sourcePsi is KtImportDirective) {
              val ktImport = import.sourcePsi as KtImportDirective
              if (identifier == ktImport.alias?.name) {
                val resource =
                  ktImport.importedReference
                    ?.let { it.toUElement() }
                    ?.let { ResourceReference.get(it) } ?: continue
                for (uastScanner in resourceFieldDetectors) {
                  uastScanner.visitResourceReference(
                    mContext,
                    resource.node,
                    resource.type,
                    resource.name,
                    resource.`package` == ANDROID_PKG,
                  )
                }
                break
              }
            }
          }
        }
      }

      annotationHandler?.visitSimpleNameReferenceExpression(mContext, node)

      return super.visitSimpleNameReferenceExpression(node)
    }

    override fun visitCallExpression(node: UCallExpression): Boolean {
      val result = super.visitCallExpression(node)

      ProgressManager.checkCanceled()

      if (node.isMethodCall()) {
        visitMethodCallExpression(node)
      } else if (node.isConstructorCall()) {
        visitNewExpression(node)
      }

      annotationHandler?.visitCallExpression(mContext, node)

      return result
    }

    private fun visitMethodCallExpression(node: UCallExpression) {
      if (mVisitMethods) {
        val methodName = node.methodName ?: node.methodIdentifier?.name
        if (methodName != null) {
          val list = methodDetectors[methodName]
          if (list != null) {
            val function = node.resolve()
            if (function != null) {
              for (scanner in list) {
                scanner.visitMethodCall(mContext, node, function)
              }
            }
          }
        }
      }
    }

    override fun visitCallableReferenceExpression(node: UCallableReferenceExpression): Boolean {
      annotationHandler?.visitCallableReferenceExpression(mContext, node)
      return super.visitCallableReferenceExpression(node)
    }

    private fun visitNewExpression(node: UCallExpression) {
      if (mVisitConstructors) {
        val method = node.resolve() ?: return

        val resolvedClass = method.containingClass
        if (resolvedClass != null) {
          val list = constructorDetectors[resolvedClass.qualifiedName]
          if (list != null) {
            for (javaPsiScanner in list) {
              javaPsiScanner.visitConstructor(mContext, node, method)
            }
          }
        }
      }
    }

    // Annotations

    // (visitCallExpression handled above)

    override fun visitMethod(node: UMethod): Boolean {
      if (node.isDuplicatedOverload()) {
        return true
      }
      annotationHandler?.visitMethod(mContext, node)
      return super.visitMethod(node)
    }

    override fun visitAnnotation(node: UAnnotation): Boolean {
      annotationHandler?.visitAnnotation(mContext, node)

      return super.visitAnnotation(node)
    }

    override fun visitEnumConstant(node: UEnumConstant): Boolean {
      annotationHandler?.visitEnumConstant(mContext, node)
      return super.visitEnumConstant(node)
    }

    override fun visitArrayAccessExpression(node: UArrayAccessExpression): Boolean {
      node.asCall()?.let(::visitMethodCallExpression)
      annotationHandler?.visitArrayAccessExpression(mContext, node)

      return super.visitArrayAccessExpression(node)
    }

    override fun visitVariable(node: UVariable): Boolean {
      annotationHandler?.visitVariable(mContext, node)

      return super.visitVariable(node)
    }

    override fun visitClass(node: UClass): Boolean {
      annotationHandler?.visitClass(mContext, node)

      return super.visitClass(node)
    }

    override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
      node.asCall()?.let(::visitMethodCallExpression)
      annotationHandler?.visitBinaryExpression(mContext, node)

      return super.visitBinaryExpression(node)
    }

    override fun visitPrefixExpression(node: UPrefixExpression): Boolean {
      node.asCall()?.let(::visitMethodCallExpression)
      annotationHandler?.visitUnaryExpression(mContext, node)

      return super.visitPrefixExpression(node)
    }

    override fun visitPostfixExpression(node: UPostfixExpression): Boolean {
      node.asCall()?.let(::visitMethodCallExpression)
      annotationHandler?.visitUnaryExpression(mContext, node)

      return super.visitPostfixExpression(node)
    }

    override fun visitClassLiteralExpression(node: UClassLiteralExpression): Boolean {
      annotationHandler?.visitClassLiteralExpression(mContext, node)
      return super.visitClassLiteralExpression(node)
    }

    override fun visitTypeReferenceExpression(node: UTypeReferenceExpression): Boolean {
      annotationHandler?.visitTypeReferenceExpression(mContext, node)
      return super.visitTypeReferenceExpression(node)
    }

    override fun visitObjectLiteralExpression(node: UObjectLiteralExpression): Boolean {
      annotationHandler?.visitObjectLiteralExpression(mContext, node)
      return super.visitObjectLiteralExpression(node)
    }

    override fun visitParameter(node: UParameter): Boolean {
      annotationHandler?.visitParameter(mContext, node)
      return super.visitParameter(node)
    }
  }

  companion object {
    /** Default size of lists holding detectors of the same type for a given node type. */
    private const val SAME_TYPE_COUNT = 8
  }
}
