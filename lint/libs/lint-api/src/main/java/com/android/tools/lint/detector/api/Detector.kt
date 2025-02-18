/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.client.api.LintBaseline
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import java.io.File
import java.util.EnumSet
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * A detector is able to find a particular problem (or a set of related problems). Each problem type
 * is uniquely identified as an [Issue].
 *
 * Detectors will be called in a predefined order:
 * 1. Manifest file
 * 2. Resource files, in alphabetical order by resource type (therefore, "layout" is checked before
 *    "values", "values-de" is checked before "values-en" but after "values", and so on.
 * 3. Java sources
 * 4. Java classes
 * 5. Gradle files
 * 6. Generic files
 * 7. Proguard files
 * 8. Property files
 *
 * If a detector needs information when processing a file type that comes from a type of file later
 * in the order above, they can request a second phase; see [LintDriver.requestRepeat].
 */
abstract class Detector {
  /**
   * See [com.android.tools.lint.detector.api.SourceCodeScanner]; this class is (temporarily) here
   * for backwards compatibility.
   */
  interface UastScanner : SourceCodeScanner

  /**
   * See [com.android.tools.lint.detector.api.ClassScanner]; this class is (temporarily) here for
   * backwards compatibility.
   */
  interface ClassScanner : com.android.tools.lint.detector.api.ClassScanner

  /**
   * See [com.android.tools.lint.detector.api.BinaryResourceScanner]; this class is (temporarily)
   * here for backwards compatibility.
   */
  interface BinaryResourceScanner : com.android.tools.lint.detector.api.BinaryResourceScanner

  /**
   * See [com.android.tools.lint.detector.api.ResourceFolderScanner]; this class is (temporarily)
   * here for backwards compatibility.
   */
  interface ResourceFolderScanner : com.android.tools.lint.detector.api.ResourceFolderScanner

  /**
   * See [com.android.tools.lint.detector.api.XmlScanner]; this class is (temporarily) here for
   * backwards compatibility.
   */
  interface XmlScanner : com.android.tools.lint.detector.api.XmlScanner

  /**
   * See [com.android.tools.lint.detector.api.GradleScanner]; this class is (temporarily) here for
   * backwards compatibility.
   */
  interface GradleScanner : com.android.tools.lint.detector.api.GradleScanner

  /**
   * See [com.android.tools.lint.detector.api.OtherFileScanner]; this class is (temporarily) here
   * for backwards compatibility.
   */
  interface OtherFileScanner : com.android.tools.lint.detector.api.OtherFileScanner

  /**
   * Runs the detector. This method will not be called for certain specialized detectors, such as
   * [XmlScanner] and [SourceCodeScanner], where there are specialized analysis methods instead such
   * as [XmlScanner.visitElement].
   *
   * @param context the context describing the work to be done
   */
  open fun run(context: Context) {}

  /**
   * Returns true if this detector applies to the given file
   *
   * @param context the context to check
   * @param file the file in the context to check
   * @return true if this detector applies to the given context and file
   */
  @Suppress("DeprecatedCallableAddReplaceWith", "UNUSED_PARAMETER")
  @Deprecated("Slated for removal") // Slated for removal in lint 2.0 - this method isn't used
  fun appliesTo(context: Context, file: File): Boolean = false

  /**
   * Analysis is about to begin for the given root project; perform any setup steps.
   *
   * A root project that is not being depended on by any other project. For example, in a Gradle
   * tree that has two app modules, and five libraries, where each of the apps depend on one ore
   * more libraries, all seven Gradle modules are lint projects, and the two app modules are lint
   * root projects.
   *
   * You typically place your analysis where you want to consult not just data from a given module,
   * but data gathered during analysis of all the dependent library modules as well, in
   * [afterCheckRootProject]. For analysis that is local to a given module, just place it in
   * [afterCheckEachProject].
   *
   * @param context the context for the check referencing the project, lint client, etc
   */
  open fun beforeCheckRootProject(context: Context) {
    // Backwards compatibility for a while
    @Suppress("DEPRECATION") beforeCheckProject(context)
  }

  /**
   * Analysis is about to begin for the given project (which may be a root project or a library
   * project). Perform any setup steps.
   *
   * @param context the context for the check referencing the project, lint client, etc
   */
  open fun beforeCheckEachProject(context: Context) {
    // preserve old semantics of afterCheckLibraryProject
    if (!context.isMainProject()) {
      @Suppress("DEPRECATION") beforeCheckLibraryProject(context)
    }
  }

  /**
   * Analysis has just been finished for the given root project; perform any cleanup or report
   * issues that require project-wide analysis (including its dependencies).
   *
   * A root project that is not being depended on by any other project. For example, in a Gradle
   * tree that has two app modules, and five libraries, where each of the apps depend on one ore
   * more libraries, all seven Gradle modules are lint projects, and the two app modules are lint
   * root projects.
   *
   * You typically place your analysis where you want to consult not just data from a given module,
   * but data gathered during analysis of all the dependent library modules as well, in
   * [afterCheckRootProject]. For analysis that is local to a given module, just place it in
   * [afterCheckEachProject].
   *
   * Given an app module and several library module dependencies:
   * * In global analysis mode: this method will be called once on the app module.
   * * In partial analysis mode (assuming Lint's `--analyze-only` phase is run on each module, and
   *   then the `--report-only` phase is run on just the app module): this method will be called in
   *   the `--analyze-only` phase for every module.
   * * In isolated ("on-the-fly") mode: this method will be called on the containing module, even if
   *   it is not a root module.
   *
   * @param context the context for the check referencing the project, lint client, etc
   */
  open fun afterCheckRootProject(context: Context) {
    // Backwards compatibility for a while
    @Suppress("DEPRECATION") afterCheckProject(context)
  }

  /**
   * Analysis has just been finished for the given project (which may be a root project or a library
   * project); perform any cleanup or report issues that require library-project-wide analysis.
   *
   * @param context the context for the check referencing the project, lint client, etc
   */
  open fun afterCheckEachProject(context: Context) {
    // preserve old semantics of afterCheckLibraryProject
    if (!context.isMainProject()) {
      @Suppress("DEPRECATION") afterCheckLibraryProject(context)
    }
  }

  /**
   * Analysis is about to begin, perform any setup steps.
   *
   * @param context the context for the check referencing the project, lint client, etc
   * @deprecated This method is deprecated because the semantics of [beforeCheckLibraryProject] was
   *   unfortunate (it included all libraries *except* the root project, and typically you want to
   *   either act on each and every project, or just the root projects. Therefore, there is a new
   *   method, [beforeCheckEachProject], which applies to each project and [beforeCheckRootProject]
   *   which applies to just the root projects; [beforeCheckProject] has a name that sounds like
   *   [beforeCheckEachProject] but just reusing that name would have been an incompatible change.
   */
  @Deprecated(
    "If you want to override the event that each root project is about " +
      "to be analyzed, override beforeCheckRootProject; if you want to override the event " +
      "that each project (both root projects and their dependencies, override " +
      "beforeCheckEachProject",
    replaceWith = ReplaceWith("beforeCheckRootProject(context)"),
  )
  open fun beforeCheckProject(context: Context) {}

  /**
   * Analysis has just been finished for the whole project, perform any cleanup or report issues
   * that require project-wide analysis.
   *
   * @param context the context for the check referencing the project, lint client, etc
   * @deprecated This method is deprecated because the semantics of [afterCheckLibraryProject] was
   *   unfortunate (it included all libraries *except* the root project, and typically you want to
   *   either act on each and every project, or just the root projects. Therefore, there is a new
   *   method, [afterCheckEachProject], which applies to each project and [afterCheckRootProject]
   *   which applies to just the root projects; [afterCheckProject] has a name that sounds like
   *   [afterCheckEachProject] but just reusing that name would have been an incompatible change.
   */
  @Deprecated(
    "If you want to override the event that each root project is about " +
      "to be analyzed, override afterCheckRootProject; if you want to override the event " +
      "that each project (both root projects and their dependencies, override " +
      "afterCheckEachProject",
    replaceWith = ReplaceWith("afterCheckRootProject(context)"),
  )
  open fun afterCheckProject(context: Context) {}

  /**
   * Analysis is about to begin for the given library project, perform any setup steps.
   *
   * @param context the context for the check referencing the project, lint client, etc
   * @deprecated This method is deprecated because the semantics of [beforeCheckLibraryProject] was
   *   unfortunate (it included all libraries *except* the root project, and typically you want to
   *   either act on each and every project, or just the root projects. Therefore, there is a new
   *   method, [beforeCheckEachProject], which applies to each project and [beforeCheckRootProject]
   *   which applies to just the root projects; [beforeCheckProject] has a name that sounds like
   *   [beforeCheckEachProject] but just reusing that name would have been an incompatible change.
   */
  @Deprecated(
    "Use beforeCheckEachProject instead (which now includes the root projects too)",
    replaceWith = ReplaceWith("beforeCheckEachProject(context)"),
  )
  open fun beforeCheckLibraryProject(context: Context) {}

  /**
   * Analysis has just been finished for the given library project, perform any cleanup or report
   * issues that require library-project-wide analysis.
   *
   * @param context the context for the check referencing the project, lint client, etc
   * @deprecated This method is deprecated because the semantics of [afterCheckLibraryProject] was
   *   unfortunate (it included all libraries *except* the root project, and typically you want to
   *   either act on each and every project, or just the root projects. Therefore, there is a new
   *   method, [afterCheckEachProject], which applies to each project and [afterCheckRootProject]
   *   which applies to just the root projects; [afterCheckProject] has a name that sounds like
   *   [afterCheckEachProject] but just reusing that name would have been an incompatible change.
   */
  @Deprecated(
    "Use afterCheckEachProject instead (which now includes the root projects too)",
    replaceWith = ReplaceWith("afterCheckEachProject(context)"),
  )
  open fun afterCheckLibraryProject(context: Context) {}

  /**
   * Analysis is about to be performed on a specific file, perform any setup steps.
   *
   * Note: When this method is called at the beginning of checking an XML file, the context is
   * guaranteed to be an instance of [XmlContext], and similarly for a Java source file, the context
   * will be a [JavaContext] and so on.
   *
   * @param context the context for the check referencing the file to be checked, the project, etc.
   */
  open fun beforeCheckFile(context: Context) {}

  /**
   * Analysis has just been finished for a specific file, perform any cleanup or report issues found
   *
   * Note: When this method is called at the end of checking an XML file, the context is guaranteed
   * to be an instance of [XmlContext], and similarly for a Java source file, the context will be a
   * [JavaContext] and so on.
   *
   * @param context the context for the check referencing the file to be checked, the project, etc.
   */
  open fun afterCheckFile(context: Context) {}

  /**
   * Returns the expected speed of this detector. The issue parameter is made available for
   * subclasses which analyze multiple issues and which need to distinguish implementation cost by
   * issue. If the detector does not analyze multiple issues or does not vary in speed by issue
   * type, just override [getSpeed] instead.
   *
   * @param issue the issue to look up the analysis speed for
   * @return the expected speed of this detector
   */
  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Slated for removal") // Slated for removal in Lint 2.0
  open fun getSpeed(issue: Issue): Speed = Speed.NORMAL

  // ---- Empty implementations to make implementing XmlScanner easier: ----

  open fun visitDocument(context: XmlContext, document: Document) {}

  open fun visitElement(context: XmlContext, element: Element) {
    // This method must be overridden if your detector returns
    // tag names from getApplicableElements
    assert(false)
  }

  open fun visitElementAfter(context: XmlContext, element: Element) {}

  open fun visitAttribute(context: XmlContext, attribute: Attr) {
    // This method must be overridden if your detector returns
    // attribute names from getApplicableAttributes
    assert(false)
  }

  // ---- Empty implementations to make implementing a ClassScanner easier: ----

  open fun checkClass(context: ClassContext, classNode: ClassNode) {}

  open fun checkCall(
    context: ClassContext,
    classNode: ClassNode,
    method: MethodNode,
    call: MethodInsnNode,
  ) {}

  open fun checkInstruction(
    context: ClassContext,
    classNode: ClassNode,
    method: MethodNode,
    instruction: AbstractInsnNode,
  ) {}

  // ---- Empty implementations to make implementing an GradleScanner easier: ----

  open val customVisitor: Boolean = false

  open fun visitBuildScript(context: Context) {}

  open fun checkDslPropertyAssignment(
    context: GradleContext,
    property: String,
    value: String,
    parent: String,
    parentParent: String?,
    valueCookie: Any,
    statementCookie: Any,
  ) {}

  open fun checkDslPropertyAssignment(
    context: GradleContext,
    property: String,
    value: String,
    parent: String,
    parentParent: String?,
    propertyCookie: Any,
    valueCookie: Any,
    statementCookie: Any,
  ) {
    // Backward compatibility
    checkDslPropertyAssignment(
      context,
      property,
      value,
      parent,
      parentParent,
      valueCookie,
      statementCookie,
    )
  }

  open fun checkMethodCall(
    context: GradleContext,
    statement: String,
    parent: String?,
    namedArguments: Map<String, String>,
    unnamedArguments: List<String>,
    cookie: Any,
  ) {}

  open fun checkMethodCall(
    context: GradleContext,
    statement: String,
    parent: String?,
    parentParent: String?,
    namedArguments: Map<String, String>,
    unnamedArguments: List<String>,
    cookie: Any,
  ) {
    // Backward compatibility
    checkMethodCall(context, statement, parent, namedArguments, unnamedArguments, cookie)
  }

  // ---- Empty implementations to make implementing a resource folder scanner easier: ----

  open fun checkFolder(context: ResourceContext, folderName: String) {}

  // ---- Empty implementations to make implementing a binary resource scanner easier: ----

  open fun checkBinaryResource(context: ResourceContext) {}

  open fun appliesTo(folderType: ResourceFolderType): Boolean = true

  open fun appliesToResourceRefs(): Boolean = false

  open fun applicableSuperClasses(): List<String>? = null

  // Deprecated methods from the old JavaScanner PSI-based interface; these should no longer
  // be used.

  @Deprecated("Use UAST instead of PSI")
  open fun visitMethod(
    context: JavaContext,
    visitor: JavaElementVisitor?,
    call: PsiMethodCallExpression,
    method: PsiMethod,
  ) {}

  @Deprecated("Use UAST instead of PSI")
  open fun visitConstructor(
    context: JavaContext,
    visitor: JavaElementVisitor?,
    node: PsiNewExpression,
    constructor: PsiMethod,
  ) {}

  @Deprecated("Use UAST instead of PSI")
  open fun visitResourceReference(
    context: JavaContext,
    visitor: JavaElementVisitor?,
    node: PsiElement,
    type: ResourceType,
    name: String,
    isFramework: Boolean,
  ) {}

  @Deprecated("Use UAST instead of PSI")
  open fun checkClass(context: JavaContext, declaration: PsiClass) {}

  @Deprecated("Use UAST instead of PSI", ReplaceWith("createUastHandler"))
  open fun createPsiVisitor(context: JavaContext): JavaElementVisitor? = null

  @Deprecated("Use UAST instead of PSI")
  open fun visitReference(
    context: JavaContext,
    visitor: JavaElementVisitor?,
    reference: PsiJavaCodeReferenceElement,
    referenced: PsiElement,
  ) {}

  // ---- Empty implementations to make implementing UastScanner easier: ----

  open fun visitClass(context: JavaContext, declaration: UClass) {}

  open fun visitClass(context: JavaContext, lambda: ULambdaExpression) {}

  open fun visitReference(
    context: JavaContext,
    reference: UReferenceExpression,
    referenced: PsiElement,
  ) {}

  open fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {}

  @Deprecated("Rename to visitMethodCall instead when targeting 3.3+")
  open fun visitMethod(context: JavaContext, node: UCallExpression, method: PsiMethod) {}

  open fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    // Backwards compatibility
    @Suppress("DEPRECATION") visitMethod(context, node, method)
  }

  open fun createUastHandler(context: JavaContext): UElementHandler? = null

  open fun visitResourceReference(
    context: JavaContext,
    node: UElement,
    type: ResourceType,
    name: String,
    isFramework: Boolean,
  ) {}

  @Deprecated(
    "Migrate to visitAnnotationUsage(JavaContext, UElement, AnnotationInfo, AnnotationUsageInfo)"
  )
  open fun visitAnnotationUsage(
    context: JavaContext,
    usage: UElement,
    type: AnnotationUsageType,
    annotation: UAnnotation,
    qualifiedName: String,
    method: PsiMethod?,
    annotations: List<UAnnotation>,
    allMemberAnnotations: List<UAnnotation>,
    allClassAnnotations: List<UAnnotation>,
    allPackageAnnotations: List<UAnnotation>,
  ) {}

  @Deprecated(
    "Migrate to visitAnnotationUsage(JavaContext, UElement, AnnotationInfo, AnnotationUsageInfo)"
  )
  open fun visitAnnotationUsage(
    context: JavaContext,
    usage: UElement,
    type: AnnotationUsageType,
    annotation: UAnnotation,
    qualifiedName: String,
    method: PsiMethod?,
    referenced: PsiElement?,
    annotations: List<UAnnotation>,
    allMemberAnnotations: List<UAnnotation>,
    allClassAnnotations: List<UAnnotation>,
    allPackageAnnotations: List<UAnnotation>,
  ) {
    // Backwards compatibility
    @Suppress("DEPRECATION")
    visitAnnotationUsage(
      context,
      usage,
      type,
      annotation,
      qualifiedName,
      method,
      annotations,
      allMemberAnnotations,
      allClassAnnotations,
      allPackageAnnotations,
    )
  }

  open fun visitAnnotationUsage(
    context: XmlContext,
    reference: Node,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo,
  ) {}

  open fun visitAnnotationUsage(
    context: JavaContext,
    element: UElement,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo,
  ) {
    // Temporary backwards compatibility. If you have overridden visitAnnotationUsage, do NOT
    // invoke this code since it will redundantly compute parameters and invoke the older
    // no-op visitAnnotationUsage method for backwards compatibility.

    val annotations = mutableListOf<UAnnotation>()
    val memberAnnotations = mutableListOf<UAnnotation>()
    val classAnnotations = mutableListOf<UAnnotation>()
    val packageAnnotations = mutableListOf<UAnnotation>()

    for (info in usageInfo.annotations) {
      val list: MutableList<UAnnotation> =
        when (info.origin) {
          AnnotationOrigin.METHOD,
          AnnotationOrigin.FIELD -> {
            annotations.add(info.annotation)
            memberAnnotations
          }
          AnnotationOrigin.CLASS,
          AnnotationOrigin.OUTER_CLASS -> classAnnotations
          AnnotationOrigin.FILE,
          AnnotationOrigin.PACKAGE -> packageAnnotations
          else -> annotations
        }
      list.add(info.annotation)
    }

    val annotation = annotationInfo.annotation
    if (!annotations.contains(annotation)) {
      annotations.add(annotation)
    }

    @Suppress("DEPRECATION")
    val usageType =
      when (usageInfo.type) {
        AnnotationUsageType.METHOD_CALL ->
          when (annotationInfo.origin) {
            AnnotationOrigin.CLASS -> AnnotationUsageType.METHOD_CALL_CLASS
            AnnotationOrigin.PACKAGE -> AnnotationUsageType.METHOD_CALL_PACKAGE
            else -> usageInfo.type
          }
        else -> usageInfo.type
      }

    @Suppress("DEPRECATION")
    visitAnnotationUsage(
      context,
      element,
      usageType,
      annotation,
      annotationInfo.qualifiedName,
      usageInfo.referenced as? PsiMethod,
      usageInfo.referenced,
      annotations,
      memberAnnotations,
      classAnnotations,
      packageAnnotations,
    )
  }

  open fun applicableAnnotations(): List<String>? = null

  open fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
    // Some annotation usages are off by default (typically because they were introduced
    // later and might introduce false positives in third party checks; checks should
    // opt into these.
    return when (type) {
      AnnotationUsageType.BINARY,
      AnnotationUsageType.EQUALITY,
      AnnotationUsageType.DEFINITION,
      AnnotationUsageType.IMPLICIT_CONSTRUCTOR,
      AnnotationUsageType.IMPLICIT_CONSTRUCTOR_CALL -> false
      else -> true
    }
  }

  open fun inheritAnnotation(annotation: String): Boolean = true

  open fun getApplicableElements(): Collection<String>? = null

  open fun getApplicableAttributes(): Collection<String>? = null

  open fun getApplicableCallNames(): List<String>? = null

  open fun getApplicableCallOwners(): List<String>? = null

  open fun getApplicableAsmNodeTypes(): IntArray? = null

  open fun getApplicableFiles(): EnumSet<Scope> = Scope.OTHER_SCOPE

  open fun getApplicableMethodNames(): List<String>? = null

  open fun getApplicableConstructorTypes(): List<String>? = null

  open fun getApplicablePsiTypes(): List<Class<out PsiElement>>? = null

  open fun getApplicableReferenceNames(): List<String>? = null

  open fun getApplicableUastTypes(): List<Class<out UElement>>? = null

  open fun isCallGraphRequired(): Boolean = false

  open fun analyzeCallGraph(context: Context, callGraph: CallGraphResult) {}

  /** Creates a lint fix builder. Just a convenience wrapper around [LintFix.create]. */
  protected open fun fix(): LintFix.Builder = LintFix.create()

  /**
   * Creates a [LintMap]. This is here for convenience to make the syntax for reporting incidents
   * with maps concise, e.g.
   *
   *     context.report(incident, map().put(KEY_REQ_QUERY_ALL, false))
   *
   * (and is similar to the existing [fix] method.)
   */
  protected fun map(): LintMap = LintMap()

  /**
   * Callback to detectors that add partial results (by adding entries to the map returned by
   * [Context.getPartialResults]). This is where the data should be analyzed and merged and results
   * reported (via [Context.report]) to lint.
   *
   * Given an app module and several library module dependencies:
   * * In global analysis mode: this method is not called by Lint, but your Detector may wish to
   *   conditionally call this method from [checkMergedProject] when [Context.isGlobalAnalysis]
   *   returns true so that your Detector works in both global and partial analysis modes, without
   *   having to specialize the logic for each mode.
   * * In partial analysis mode (assuming Lint's `--analyze-only` phase is run on each module, and
   *   then the `--report-only` phase is run on just the app module): this method will be called in
   *   the `--report-only` phase on the app module (for each [Issue] with partial results), but only
   *   if the Detector has added partial results for the [Issue].
   * * In isolated ("on-the-fly") mode: this method is not called by Lint, but see global analysis
   *   mode.
   */
  open fun checkPartialResults(context: Context, partialResults: PartialResult) {
    // Don't call super.checkPartialResults! This is here to make sure you
    // don't accidentally forget to override this if your detector reports
    // partial results.
    error(
      this.javaClass.simpleName +
        ": You must override " +
        "Detector.checkPartialResults(Context, data: List<LintMap>) " +
        "when you report data via Context.reportPartialResult (and don't " +
        "call super.checkPartialResults!)"
    )
  }

  /**
   * Lint is aggregating provisional data; perform any additional checks which are allowed now
   * (looking at global data like [Project.getMergedManifest] etc. This serves a similar purpose to
   * [afterCheckRootProject], but is a separate method because this method will **not** be invoked
   * on the same instance of the detector, so you cannot accumulate state while looking at other
   * files and then process it in this method; instead, any state storage has to go through
   * [LintClient.getPartialResults], and then handle that data in [Detector.checkPartialResults].
   *
   * However, there are cases where you don't actually depend on any earlier results; you simply
   * want to look at state which is only available when the merged project is known, such as the
   * merged manifest.
   *
   * You could add some fake partial results to trigger a callback to
   * [Detector.checkPartialResults], but that's not very clean. Therefore, you can instead override
   * this method, which will be called for all detectors when merging in partial results, and where
   * you can report any issues discovered in the merged project context.
   *
   * Given an app module and several library module dependencies:
   * * In global analysis mode: this method will be called once on the app module.
   * * In partial analysis mode (assuming Lint's `--analyze-only` phase is run on each module, and
   *   then the `--report-only` phase is run on just the app module): this method will be called in
   *   the `--report-only` phase on the app module.
   * * In isolated ("on-the-fly") mode: this method will be called on the containing module, even if
   *   it is not a root module.
   */
  open fun checkMergedProject(context: Context) {}

  /**
   * Filter which looks at incidents previously reported via [Context.report] with a [LintMap], and
   * returns false if the issue does not apply in the current reporting project context, or true if
   * the issue should be reported. For issues that are accepted, the detector is also allowed to
   * mutate the issue, such as customizing the error message further.
   */
  open fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
    // Don't call super.accept! This is here to make sure you
    // don't accidentally forget to override this if your detector reports
    // issues conditionally.
    error(
      this.javaClass.simpleName +
        ": You must override " +
        "Detector.filterIncident(Context, Incident, LintMap) " +
        "when you report conditional incidents via Context.report(Incident, LintMap)"
    )
  }

  /**
   * Returns true if the given new error message reported by this detector for the given [issue] is
   * equivalent to a message previously created by the detector.
   *
   * This is used to allow error messages to change without invalidating older lint baseline
   * warnings. If there is no match found in the baseline, the detector is consulted via this method
   * to check whether it should be treated as the same.
   *
   * Note that the baseline mechanism will first try some simple checks on its own, such as allowing
   * messages to append new details, so you don't have to explicitly check for equality.
   *
   * **Avoid directly checking strings for equality here**. Instead, consider calling for example
   * [LintBaseline.stringsEquivalent].
   *
   * Here are some examples:
   *
   * Use search/replace to tweak the wording in a message to the new one:
   * ```kotlin
   * override fun sameMessage(issue: Issue, new: String, old: String): Boolean {
   *   // Handle the case where we dropped a prefix at some point:
   *   //   "[Accessibility] Empty contentDescription attribute on image"
   *   //   ==
   *   //   "Empty contentDescription attribute on image"
   *   return stringsEquivalent(old.removePrefix("[Accessibility] "), new)
   * }
   * ```
   *
   * Allow a number or version to drift:
   * ```kotlin
   * override fun sameMessage(issue: Issue, new: String, old: String): Boolean {
   *   // The error message includes a version number which drifts over time; allow this:
   *   // "To support older versions than API 17 (project specifies 11) you should also ...
   *   //  ==
   *   // "To support older versions than API 17 (project specifies 14) you should also ...
   *   // Allow the single token right after "project specifies" to differ:
   *   return stringsEquivalent(old, new) { s, i -> s.tokenPrecededBy("project specifies ", i) }
   * }
   * ```
   */
  open fun sameMessage(issue: Issue, new: String, old: String): Boolean {
    return false
  }
}
