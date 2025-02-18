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
package com.android.tools.lint.detector.api

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Node

/**
 * Interface to be implemented by lint detectors that want to analyze Java source files (or other
 * similar source files, such as Kotlin files.)
 *
 * There are several different common patterns for detecting issues:
 * <ul>
 * <li> Checking calls to a given method. For this see [getApplicableMethodNames] and
 *   [visitMethodCall]</li>
 * <li> Instantiating a given class. For this, see [getApplicableConstructorTypes] and
 *   [visitConstructor]</li>
 * <li> Referencing a given constant. For this, see [getApplicableReferenceNames] and
 *   [visitReference]</li>
 * <li> Extending a given class or implementing a given interface. For this, see
 *   [applicableSuperClasses] and [visitClass]</li>
 * <li> More complicated scenarios: perform a general AST traversal with a visitor. In this case,
 *   first tell lint which AST node types you're interested in with the [getApplicableUastTypes]
 *   method, and then provide a [UElementHandler] from the [createUastHandler] where you override
 *   the various applicable handler methods. This is done rather than a general visitor from the
 *   root node to avoid having to have every single lint detector (there are hundreds) do a full
 *   tree traversal on its own.</li>
 * </ul>
 *
 * [SourceCodeScanner] exposes the UAST API to lint checks. UAST is short for "Universal AST" and is
 * an abstract syntax tree library which abstracts away details about Java versus Kotlin versus
 * other similar languages and lets the client of the library access the AST in a unified way.
 *
 * UAST isn't actually a full replacement for PSI; it **augments** PSI. Essentially, UAST is used
 * for the **inside** of methods (e.g. method bodies), and things like field initializers. PSI
 * continues to be used at the outer level: for packages, classes, and methods (declarations and
 * signatures). There are also wrappers around some of these for convenience.
 *
 * The [SourceCodeScanner] interface reflects this fact. For example, when you indicate that you
 * want to check calls to a method named {@code foo}, the call site node is a UAST node (in this
 * case, [UCallExpression], but the called method itself is a [PsiMethod], since that method might
 * be anywhere (including in a library that we don't have source for, so UAST doesn't make sense.)
 *
 * ### Handlers versus visitors
 * Most lint checks don't do their own full AST traversal; they instead participate in a shared
 * traversal of the tree, registering element types they're interested with using
 * [getApplicableUastTypes] and then providing a visitor where they implement the corresponding
 * visit methods. However, from these visitors you should **not** be calling super.visitX. To remove
 * this whole confusion, lint now provides a separate class, [UElementHandler]. For the shared
 * traversal, just provide this handler instead and implement the appropriate visit methods. It will
 * throw an error if you register element types in [getApplicableUastTypes] that you don't override.
 */
interface SourceCodeScanner : FileScanner {

  /**
   * Return the list of method names this detector is interested in, or null. If this method returns
   * non-null, then any AST nodes that match a method call in the list will be passed to the
   * [visitMethodCall] method for processing.
   *
   * This makes it easy to write detectors that focus on some fixed calls. For example, the
   * StringFormatDetector uses this mechanism to look for "format" calls, and when found it looks
   * around (using the UAST's [UElement.uastParent] method) to see if it's called on a String class
   * instance, and if so do its normal processing. Note that since it doesn't need to do any other
   * AST processing, that detector does not actually supply a visitor.
   *
   * @return a set of applicable method names, or null.
   */
  fun getApplicableMethodNames(): List<String>?

  /**
   * Method invoked for any method calls found that matches any names returned by
   * [.getApplicableMethodNames]. This also passes back the visitor that was created by
   * [.createJavaVisitor], but a visitor is not required. It is intended for detectors that need to
   * do additional AST processing, but also want the convenience of not having to look for method
   * names on their own.
   *
   * @param context the context of the lint request
   * @param node the [PsiMethodCallExpression] node for the invoked method
   * @param method the [PsiMethod] being called
   * @deprecated This is really visiting calls, not methods, so has been renamed to
   *   [visitMethodCall] instead
   */
  @Deprecated("Rename to visitMethodCall instead when targeting 3.3+")
  fun visitMethod(context: JavaContext, node: UCallExpression, method: PsiMethod)

  /**
   * Method invoked for any method calls found that matches any names returned by
   * [.getApplicableMethodNames]. This also passes back the visitor that was created by
   * [.createJavaVisitor], but a visitor is not required. It is intended for detectors that need to
   * do additional AST processing, but also want the convenience of not having to look for method
   * names on their own.
   *
   * @param context the context of the lint request
   * @param node the [PsiMethodCallExpression] node for the invoked method
   * @param method the [PsiMethod] being called
   */
  fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod)

  /**
   * Return the list of constructor types this detector is interested in, or null. If this method
   * returns non-null, then any AST nodes that match a constructor call in the list will be passed
   * to the [visitConstructor] method for processing.
   *
   * This makes it easy to write detectors that focus on some fixed constructors.
   *
   * @return a set of applicable fully qualified types, or null.
   */
  fun getApplicableConstructorTypes(): List<String>?

  /**
   * Method invoked for any constructor calls found that matches any names returned by
   * [.getApplicableConstructorTypes]. This also passes back the visitor that was created by
   * [.createPsiVisitor], but a visitor is not required. It is intended for detectors that need to
   * do additional AST processing, but also want the convenience of not having to look for method
   * names on their own.
   *
   * @param context the context of the lint request
   * @param node the [PsiNewExpression] node for the invoked method
   * @param constructor the called constructor method
   */
  fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod)

  /**
   * Return the list of reference names types this detector is interested in, or null. If this
   * method returns non-null, then any AST elements that match a reference in the list will be
   * passed to the [visitReference] method for processing.
   *
   * This makes it easy to write detectors that focus on some fixed references.
   *
   * @return a set of applicable reference names, or null.
   */
  fun getApplicableReferenceNames(): List<String>?

  /**
   * Method invoked for any references found that matches any names returned by
   * [.getApplicableReferenceNames]. This also passes back the visitor that was created by
   * [.createPsiVisitor], but a visitor is not required. It is intended for detectors that need to
   * do additional AST processing, but also want the convenience of not having to look for method
   * names on their own.
   *
   * @param context the context of the lint request
   * @param reference the [PsiJavaCodeReferenceElement] element
   * @param referenced the referenced element
   */
  fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement)

  /**
   * Returns whether this detector cares about Android resource references (such as `R.layout.main`
   * or `R.string.app_name`). If it does, then the visitor will look for these patterns, and if
   * found, it will invoke [visitResourceReference] passing the resource type and resource name.
   *
   * @return true if this detector wants to be notified of R resource identifiers found in the code.
   */
  fun appliesToResourceRefs(): Boolean

  /**
   * Called for any resource references (such as `R.layout.main`) found in Java code, provided this
   * detector returned `true` from [.appliesToResourceRefs].
   *
   * @param context the lint scanning context
   * @param node the variable reference for the resource
   * @param type the resource type, such as "layout" or "string"
   * @param name the resource name, such as "main" from `R.layout.main`
   * @param isFramework whether the resource is a framework resource (android.R) or a local project
   *   resource (R)
   */
  fun visitResourceReference(
    context: JavaContext,
    node: UElement,
    type: ResourceType,
    name: String,
    isFramework: Boolean,
  )

  /**
   * Returns a list of fully qualified names for super classes that this detector cares about. If
   * not null, this detector will **only** be called if the current class is a subclass of one of
   * the specified superclasses. Lint will invoke [visitClass] when it encounters subclasses and
   * lambdas for these types.
   *
   * @return a list of fully qualified names
   */
  fun applicableSuperClasses(): List<String>?

  /**
   * Called for each class that extends one of the super classes specified with
   * [.applicableSuperClasses].
   *
   * Note: This method will not be called for [PsiTypeParameter] classes. These aren't really
   * classes in the sense most lint detectors think of them, so these are excluded to avoid having
   * lint checks that don't defensively code for these accidentally report errors on type
   * parameters. If you really need to check these, use [.getApplicablePsiTypes] with
   * `PsiTypeParameter.class` instead.
   *
   * @param context the lint scanning context
   * @param declaration the class declaration node, or null for anonymous classes
   */
  fun visitClass(context: JavaContext, declaration: UClass)

  /**
   * Like [visitClass], but used for lambdas in SAM (single abstract method) types. For example, if
   * you have this method:
   * ```
   * void enqueue(Runnable runnable) { ... }
   * ...
   * enqueue({ something(); })
   * ```
   *
   * then the lambda being passed to the call can be thought of as a class implementing the Runnable
   * interface.
   *
   * The set of target types for the lambda are provided in [applicableSuperClasses]
   *
   * @param context the lint scanning context
   * @param lambda the lambda
   */
  fun visitClass(context: JavaContext, lambda: ULambdaExpression)

  /**
   * Returns a list of names for annotations that this detector cares about. Names may be either
   * simple or fully-qualified; simple names will match *all* annotations of the same name. Lint
   * looks for elements that are **associated** with an annotated element, and will invoke
   * [visitAnnotationUsage] for each usage.
   *
   * **Note**: This doesn't visit the annotations themselves; this visits *usages* of the
   * annotation. For example, let's say you have a method that is annotated with an annotation. The
   * annotation element itself, and the method declaration, will not be visited, but the return
   * statement, as well as any calls to the method, will. To check annotation declarations
   * themselves, return UAnnotation::class.java from [getApplicableConstructorTypes] and return a
   * [UElementHandler] from [createUastHandler] where you override
   * [UElementHandler.visitAnnotation].
   *
   * @return a list of annotation names
   */
  fun applicableAnnotations(): List<String>?

  /**
   * Returns whether this detector cares about an annotation usage of the given type. Most detectors
   * are interested in all types except for [AnnotationUsageType.BINARY] and
   * [AnnotationUsageType.EQUALITY], which only apply for annotations that are expressing a "type",
   * e.g. it would be suspicious to combine (compare, add, etc.) resources of different type.
   */
  fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean

  /**
   * Whether this lint detector wants to consider annotations on an element that were inherited
   * (e.g. defined on a super class or super method)
   */
  fun inheritAnnotation(annotation: String): Boolean

  /**
   * Called whenever the given [element] references a [referenced] element that has been annotated
   * with one of the annotations returned from [applicableAnnotations], pointed to by
   * [annotationInfo].
   *
   * The element itself may not be annotated; it can also be a member in a class which has been
   * annotated, or within an outer class which has been annotated, or in a file that has been
   * annotated with an annotation, or in a package, and so on.
   *
   * The [usageInfo] data provides additional context; most importantly, it will include all
   * relevant annotations in the hierarchy, in scope order, and an index pointing to which specific
   * annotation the callback is pointing to. This can be used to handle scoping when there are
   * multiple related annotations. For example, let's say you have two annotations, `@Mutable` and
   * `@Immutable`. When you're visiting an `@Immutable` annotation, that annotation could be coming
   * from an outer class where a closer class or immediate method annotation is marked @Mutable. In
   * this case, you'll want to visit the [usageInfo] and make sure that none of the annotations
   * leading up to the [AnnotationUsageInfo.index] are the `@Mutable` annotation which would
   * override the `@Immutable` annotation on the class. You don't need to do this for repeated
   * occurrences of the same annotation; lint will already skip any later or outer scope usages of
   * the same annotation since it's almost always the case that the closer annotation is a
   * redefinition which overrides the outer one, and leaving this up to detectors to worry about
   * would probably lead to subtle bugs. Note that these annotations *are* included in the
   * [AnnotationUsageInfo.annotations] list, so you can look for them in the callback to the
   * innermost one if you *do* want to consider outer occurrences of the same annotation.
   *
   * For more information, see the annotations chapter of the lint api guide.
   */
  fun visitAnnotationUsage(
    context: JavaContext,
    element: UElement,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo,
  )

  /**
   * Called whenever the given XML element or attribute [reference] of a class, method or field
   * element that has been annotated with one of the annotations returned from
   * [applicableAnnotations], pointed to by [annotationInfo].
   *
   * The element itself may not be annotated; it can also be a member in a class which has been
   * annotated, or within an outer class which has been annotated, or in a file that has been
   * annotated with an annotation, or in a package, and so on.
   *
   * For more information, see the annotations chapter of the lint api guide.
   */
  fun visitAnnotationUsage(
    context: XmlContext,
    reference: Node,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo,
  )

  /**
   * Called whenever the given element references an element that has been annotated with one of the
   * annotations returned from [.applicableAnnotations].
   *
   * The element itself may not be annotated; it can also be a member in a class which has been
   * annotated, or a package which has been annotated.
   *
   * The call is handed the annotations found at each level (member, class, package) so that it can
   * decide how to handle them.
   *
   * @param context the lint scanning context
   * @param usage the element to be checked
   * @param type the type of annotation usage lint has found
   * @param annotation the annotation this detector is interested in
   * @param qualifiedName the annotation's qualified name
   * @param method the method, if any
   * @param annotations the annotations to check. These are the annotations you've registered an
   *   interest in with [.applicableAnnotations], whether they were specified as a parameter
   *   annotation, method annotation, class annotation or package annotation. The various
   *   annotations available in those contexts are also supplied. This lets you not only see where
   *   an annotation was specified, but you can check the relative priorities of annotations. For
   *   example, let's say you have a `@WorkerThread` annotation on a class. If you also happen to
   *   have a `@UiThread` annotation on a member you shouldn't enforce worker thread semantics on
   *   the member.
   * @param allMemberAnnotations all member annotations (may include other annotations than the ones
   *   you've registered an interest in with [.applicableAnnotations])
   * @param allClassAnnotations all annotations in the target surrounding class
   * @param allPackageAnnotations all annotations in the target surrounding package
   * @deprecated Migrate to the new visitAnnotationUsage callback which uses the [AnnotationInfo]
   *   mechanism
   */
  @Deprecated(
    "Migrate to visitAnnotationUsage(JavaContext, UElement, AnnotationInfo, AnnotationUsageInfo)"
  )
  fun visitAnnotationUsage(
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
  )

  /**
   * Called whenever the given element references an element that has been annotated with one of the
   * annotations returned from [applicableAnnotations].
   *
   * The element itself may not be annotated; it can also be a member in a class which has been
   * annotated, or a package which has been annotated.
   *
   * The call is handed the annotations found at each level (member, class, package) so that it can
   * decide how to handle them.
   *
   * @param context the lint scanning context
   * @param usage the element to be checked
   * @param type the type of annotation usage lint has found
   * @param annotation the annotation this detector is interested in
   * @param qualifiedName the annotation's qualified name
   * @param method the method, if any
   * @param referenced the referenced element (method, field, etc.), if any
   * @param annotations the annotations to check. These are the annotations you've registered an
   *   interest in with [.applicableAnnotations], whether they were specified as a parameter
   *   annotation, method annotation, class annotation or package annotation. The various
   *   annotations available in those contexts are also supplied. This lets you not only see where
   *   an annotation was specified, but you can check the relative priorities of annotations. For
   *   example, let's say you have a `@WorkerThread` annotation on a class. If you also happen to
   *   have a `@UiThread` annotation on a member you shouldn't enforce worker thread semantics on
   *   the member.
   * @param allMemberAnnotations all member annotations (may include other annotations than the ones
   *   you've registered an interest in with [.applicableAnnotations])
   * @param allClassAnnotations all annotations in the target surrounding class
   * @param allPackageAnnotations all annotations in the target surrounding package
   * @deprecated Migrate to the new visitAnnotationUsage callback which uses the [AnnotationInfo]
   *   mechanism
   */
  @Deprecated(
    "Migrate to visitAnnotationUsage(JavaContext, UElement, AnnotationInfo, AnnotationUsageInfo)"
  )
  fun visitAnnotationUsage(
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
  )

  /**
   * Return the types of AST nodes that the visitor returned from [.createJavaVisitor] should visit.
   * See the documentation for [.createJavaVisitor] for details on how the shared visitor is used.
   *
   * If you return null from this method, then the visitor will process the full tree instead.
   *
   * Note that for the shared visitor, the return codes from the visit methods are ignored:
   * returning true will **not** prune iteration of the subtree, since there may be other node types
   * interested in the children. If you need to ensure that your visitor only processes a part of
   * the tree, use a full visitor instead. See the OverdrawDetector implementation for an example of
   * this.
   *
   * @return the list of applicable node types (AST node classes), or null
   */
  fun getApplicableUastTypes(): List<Class<out UElement>>?

  /**
   * Create a parse tree visitor to process the parse tree. All [SourceCodeScanner] detectors must
   * provide a visitor, unless they either return true from [appliesToResourceRefs] or return
   * non-null from [getApplicableMethodNames].
   *
   * If you return specific AST node types from [getApplicableUastTypes], then the visitor will
   * **only** be called for the specific requested node types. This is more efficient, since it
   * allows many detectors that apply to only a small part of the AST (such as method call nodes) to
   * share iteration of the majority of the parse tree.
   *
   * If you return null from [getApplicableUastTypes], then your visitor will be called from the top
   * and all node types visited.
   *
   * Note that a new visitor is created for each separate compilation unit, so you can store per
   * file state in the visitor.
   *
   * @param context the [Context] for the file being analyzed
   * @return a visitor, or null.
   */
  fun createUastHandler(context: JavaContext): UElementHandler?

  /**
   * Whether this implementation wants to access the global call graph with a call to
   * [analyzeCallGraph].
   *
   * **NOTE: Highly experimental as well as resource intensive!**
   */
  fun isCallGraphRequired(): Boolean

  /**
   * Analyze the call graph requested with [isCallGraphRequired]
   *
   * **NOTE: Highly experimental as well as resource intensive!**
   */
  fun analyzeCallGraph(context: Context, callGraph: CallGraphResult)
}
