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

package com.android.tools.lint.helpers

import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.computeKotlinArgumentMapping
import com.android.tools.lint.detector.api.isKotlin
import com.android.tools.lint.model.LintModelDependencies
import com.google.common.collect.Sets
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierList
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import com.intellij.psi.impl.file.PsiPackageImpl
import com.intellij.psi.impl.source.tree.java.PsiCompositeModifierList
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.MethodSignatureUtil
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.util.io.URLUtil
import java.io.File
import kotlin.math.min
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.getContainingUFile
import org.jetbrains.uast.kotlin.psi.UastFakeLightMethodBase

open class DefaultJavaEvaluator(
  private val myProject: com.intellij.openapi.project.Project?,
  private val myLintProject: Project?,
) : JavaEvaluator() {
  // cache of package name to package-info.class.
  private val packageInfoCache = mutableMapOf<String, PsiPackage>()

  override val dependencies: LintModelDependencies?
    get() {
      if (myLintProject != null && myLintProject.isAndroidProject) {
        val variant = myLintProject.buildVariant
        if (variant != null) {
          return variant.artifact.dependencies
        }
      }
      return null
    }

  override fun extendsClass(cls: PsiClass?, className: String, strict: Boolean): Boolean {
    // TODO: This checks interfaces too. Let's find a cheaper method which only checks direct super
    // classes!
    return InheritanceUtil.isInheritor(cls, strict, className)
  }

  override fun implementsInterface(cls: PsiClass, interfaceName: String, strict: Boolean): Boolean {
    // TODO: This checks superclasses too. Let's find a cheaper method which only checks interfaces.
    return InheritanceUtil.isInheritor(cls, strict, interfaceName)
  }

  override fun inheritsFrom(cls: PsiClass?, className: String, strict: Boolean): Boolean {
    cls ?: return false
    return InheritanceUtil.isInheritor(cls, strict, className)
  }

  override fun findClass(qualifiedName: String): PsiClass? {
    myProject ?: return null
    try {
      return JavaPsiFacade.getInstance(myProject)
        .findClass(qualifiedName, GlobalSearchScope.allScope(myProject))
    } catch (ex: Exception) {
      // For example, ProcessCanceledException.
      if (ex is ControlFlowException) throw ex

      if (LintClient.isUnitTest) {
        myLintProject
          ?.client
          ?.log(Severity.ERROR, ex, "Exception thrown for qualified class name: $qualifiedName")
      }

      return null
    }
  }

  override fun getClassType(psiClass: PsiClass?): PsiClassType? {
    return if (myProject != null && psiClass != null)
      JavaPsiFacade.getElementFactory(myProject).createType(psiClass)
    else null
  }

  override fun getTypeClass(psiType: PsiType?): PsiClass? {
    return (psiType as? PsiClassType)?.resolve()
  }

  @Suppress("ExternalAnnotations")
  override fun getAllAnnotations(owner: UAnnotated, inHierarchy: Boolean): List<UAnnotation> {
    if (owner is UDeclaration) {
      // Going to PSI means we drop vital context from Kotlin annotations.
      // Therefore, call into UAST to get the full Kotlin annotations, but also
      // merge in external annotations and inherited annotations from the class
      // files, and pick unique.
      val annotations = owner.uAnnotations
      val mergeAnnotations =
        getAnnotations(owner.javaPsi as? PsiModifierListOwner, inHierarchy, owner)
      if (annotations.isNotEmpty()) {
        if (mergeAnnotations.isEmpty()) {
          return annotations
        }

        var modified = false
        val map = LinkedHashMap<String, UAnnotation>()
        for (annotation in annotations) {
          val name = annotation.qualifiedName ?: annotation.uastAnchor?.name ?: continue
          map[name] = annotation
        }
        for (annotation in mergeAnnotations) {
          val signature = annotation.qualifiedName ?: continue
          if (map[signature] == null) {
            map[signature] = annotation
            modified = true
          }
        }
        return if (modified) {
          map.values.toList()
        } else {
          annotations
        }
      }

      return mergeAnnotations
    }

    return owner.uAnnotations
  }

  @Suppress("DEPRECATION", "OverridingDeprecatedMember")
  override fun getAllAnnotations(
    owner: PsiModifierListOwner,
    inHierarchy: Boolean,
  ): Array<PsiAnnotation> {
    if (owner is UDeclaration) {
      // Work around bug: Passing in a UAST node to this method generates a
      // "class JavaUParameter not found among parameters: [PsiParameter:something]" error
      val psi = owner.javaPsi as? PsiModifierListOwner ?: return emptyArray()
      return getAllAnnotations(psi, inHierarchy)
    }

    // withInferred=false when running outside the IDE: we don't have
    // an InferredAnnotationsManager
    return AnnotationUtil.getAllAnnotations(owner, inHierarchy, null, false)
  }

  override fun getAnnotations(
    owner: PsiModifierListOwner?,
    inHierarchy: Boolean,
    parent: UElement?,
  ): List<UAnnotation> {
    owner ?: return emptyList()

    if (owner is UDeclaration) {
      // Work around bug: Passing in a UAST node to this method generates a
      // "class JavaUParameter not found among parameters: [PsiParameter:something]" error
      // (See DefaultJavaEvaluatorTest#DefaultJavaEvaluatorTest)
      val psi = owner.javaPsi as? PsiModifierListOwner ?: return emptyList()
      return getAnnotations(psi, inHierarchy)
    }

    // withInferred=false when running outside the IDE: we don't have an InferredAnnotationsManager
    val withInferred = false
    val psiAnnotations =
      when (owner) {
        is UastFakeLightMethodBase -> {
          // For `reified inline` or `@Deprecated(Hidden)`, UAST creates a "fake" PSI
          // since LC doesn't model that. Such modeling has a separate list of annotations,
          // while [AnnotationUtil] expects to retrieve annotations from modifier list.
          // As a stopgap, we better read the underlying annotations directly here.
          owner.annotations
        }
        else -> AnnotationUtil.getAllAnnotations(owner, inHierarchy, null, withInferred)
      }
    return psiAnnotations.mapNotNull { psi ->
      UastFacade.convertElement(psi, if (inHierarchy) null else parent, UAnnotation::class.java)
        as? UAnnotation
    }
  }

  @Suppress("DEPRECATION", "OverridingDeprecatedMember")
  override fun findAnnotationInHierarchy(
    listOwner: PsiModifierListOwner,
    vararg annotationNames: String,
  ): PsiAnnotation? {
    if (listOwner is UDeclaration) {
      // Work around UAST bug
      val psi = listOwner.javaPsi as? PsiModifierListOwner ?: return null
      return findAnnotationInHierarchy(psi, *annotationNames)
    }
    return AnnotationUtil.findAnnotationInHierarchy(listOwner, Sets.newHashSet(*annotationNames))
  }

  override fun getAnnotationInHierarchy(
    listOwner: PsiModifierListOwner,
    vararg annotationNames: String,
  ): UAnnotation? {
    @Suppress("DEPRECATION")
    return findAnnotationInHierarchy(listOwner, *annotationNames)?.let { psi ->
      UastFacade.convertElement(psi, listOwner as? UElement) as? UAnnotation
    }
  }

  @Suppress("DEPRECATION", "OverridingDeprecatedMember")
  override fun findAnnotation(
    listOwner: PsiModifierListOwner?,
    vararg annotationNames: String,
  ): PsiAnnotation? {
    if (listOwner is UDeclaration) {
      // Work around UAST bug
      val psi = listOwner.javaPsi as? PsiModifierListOwner ?: return null
      return findAnnotation(psi, *annotationNames)
    }
    return AnnotationUtil.findAnnotation(listOwner, false, *annotationNames)
  }

  override fun getAnnotation(
    listOwner: PsiModifierListOwner?,
    vararg annotationNames: String,
  ): UAnnotation? {
    @Suppress("DEPRECATION")
    return findAnnotation(listOwner, *annotationNames)?.let { psi ->
      UastFacade.convertElement(psi, listOwner as? UElement) as? UAnnotation
    }
  }

  override fun areSignaturesEqual(method1: PsiMethod, method2: PsiMethod): Boolean {
    return MethodSignatureUtil.areSignaturesEqual(method1, method2)
  }

  override fun getProject(element: PsiElement): Project? {
    val virtualFile = element.containingFile?.virtualFile ?: return null
    val file = VfsUtilCore.virtualToIoFile(virtualFile)
    return getProject(file)
  }

  fun getProject(file: File): Project? {
    // TODO: We could change the filter below to consider source files instead of just
    //  Project.dir (which is not always useful in some build systems). However, this function
    //  appears to be used for finding the Gradle group id, where filtering based on Project.dir
    //  is probably fine.
    val projects = myLintProject?.client?.knownProjects ?: return null
    if (projects.isEmpty()) {
      return null
    }

    val path = file.path
    return projects
      .asSequence()
      .filter { path == it.dir.path || path.startsWith(it.dir.path + File.separator) }
      .maxByOrNull { it.dir.path.length }
  }

  override fun findJarPath(element: PsiElement): String? {
    val containingFile = element.containingFile
    @Suppress("USELESS_CAST") return findJarPath(containingFile as PsiFile?)
  }

  override fun findJarPath(element: UElement): String? {
    val uFile = element.getContainingUFile()
    return if (uFile != null) findJarPath(uFile.sourcePsi as PsiFile?) else null
  }

  private fun findJarPath(containingFile: PsiFile?): String? {
    if (containingFile != null) {
      // This code is roughly similar to the following:
      //      VirtualFile jarVirtualFile = PsiUtil.getJarFile(containingFile);
      //      if (jarVirtualFile != null) {
      //        return jarVirtualFile.getPath();
      //      }
      // However, the above methods will do some extra string manipulation and
      // VirtualFile lookup which we don't actually need (we're just after the
      // raw URL suffix)
      val file = containingFile.virtualFile
      if (file != null && file.fileSystem.protocol == URLUtil.JAR_PROTOCOL) {
        val path = file.path
        val separatorIndex = path.indexOf(URLUtil.JAR_SEPARATOR)
        if (separatorIndex >= 0) {
          return path.substring(0, separatorIndex)
        }
      }
    }

    return null
  }

  override fun getPackage(node: PsiElement): PsiPackage? {
    val containingFile = node as? PsiFile ?: node.containingFile
    if (containingFile != null) {
      // Optimization: JavaDirectoryService can be slow so try to compute it directly
      if (containingFile is PsiJavaFile) {
        return packageInfoCache.computeIfAbsent(containingFile.packageName) { name ->
          val pkg = JavaPsiFacade.getInstance(containingFile.project).findPackage(name)
          if (pkg != null) {
            pkg
          } else {
            val cls = findClass(name + '.' + PsiPackage.PACKAGE_INFO_CLASS)
            val modifierList = cls?.modifierList
            object : PsiPackageImpl(node.manager, name) {
              override fun getAnnotationList(): PsiModifierList? {
                return if (modifierList != null) {
                  // Use composite even if we just have one such that we don't
                  // pass a modifier list tied to source elements in the class
                  // (modifier lists can be part of the AST)
                  PsiCompositeModifierList(manager, listOf(modifierList))
                } else null
              }
            }
          }
        }
      } else if (containingFile is KtFile) {
        val packageFqName = containingFile.packageFqName
        return JavaPsiFacade.getInstance(node.project).findPackage(packageFqName.asString())
      }

      val dir = containingFile.parent
      if (dir != null) {
        return JavaDirectoryService.getInstance().getPackage(dir)
      }
    }
    return null
  }

  override fun getPackage(node: UElement): PsiPackage? {
    val uFile = node.getContainingUFile() ?: return null
    val psi =
      if (isKotlin(uFile.lang)) {
        // [KotlinUFile.javaPsi] is a delegation to (U|S)LC's [FakeFileForLightClass]
        // while we already have sourcePsi of [KtFile]
        // through which we can easily get package fq name.
        uFile.sourcePsi
      } else {
        uFile.javaPsi ?: uFile.sourcePsi
      }
    return getPackage(psi)
  }

  override fun getQualifiedName(psiClassType: PsiClassType): String? {
    val erased = erasure(psiClassType)
    return if (erased is PsiClassType) {
      super.getQualifiedName((erased as PsiClassType?)!!)
    } else super.getQualifiedName(psiClassType)
  }

  override fun getQualifiedName(psiClass: PsiClass): String? {
    return psiClass.qualifiedName
  }

  @Suppress("OverridingDeprecatedMember", "DEPRECATION")
  override fun getInternalName(psiClassType: PsiClassType): String? {
    val erased = erasure(psiClassType)
    return if (erased is PsiClassType) super.getInternalName(erased)
    else super.getInternalName(psiClassType)
  }

  @Suppress("OverridingDeprecatedMember")
  override fun getInternalName(psiClass: PsiClass): String? {
    return com.android.tools.lint.detector.api.getInternalName(psiClass)
  }

  override fun erasure(type: PsiType?): PsiType? {
    return TypeConversionUtil.erasure(type)
  }

  override fun computeArgumentMapping(
    call: UCallExpression,
    method: PsiMethod,
  ): Map<UExpression, PsiParameter> {
    val parameterList = method.parameterList
    if (parameterList.parametersCount == 0) {
      return emptyMap()
    }

    // Call into lint-kotlin to look up the argument mapping if this call is a Kotlin method.
    val kotlinMap = computeKotlinArgumentMapping(call, method)
    if (kotlinMap != null) {
      return kotlinMap
    }

    val arguments = call.valueArguments
    val parameters = parameterList.parameters

    var j = 0
    val first = parameters.firstOrNull()?.name
    // check if "$self" for UltraLightParameter
    if (
      (first?.startsWith("\$this") == true || first?.startsWith("\$self") == true) &&
        isKotlin(call.lang)
    ) {
      // Kotlin extension method.
      j++
    }

    var i = 0
    val n = min(parameters.size, arguments.size)
    val map = HashMap<UExpression, PsiParameter>(2 * n)

    /* Here is a UAST supported way to compute this, but it doesn't handle
       varargs well (some unit tests fail showing the particular scenarios)
    if (call is UCallExpressionEx) {
        for (index in 0 until parameters.size) {
            val argument = call.getArgumentForParameter(index) ?: continue
            val parameter = parameters[index]
            map[argument] = parameter
        }

        return map
    }
     */

    while (j < n) {
      val argument = arguments[i]
      val parameter = parameters[j]
      map[argument] = parameter
      i++
      j++
    }

    if (i < arguments.size && j > 0) {
      // last parameter is varargs (same parameter annotations)
      j--
      while (i < arguments.size) {
        val argument = arguments[i]
        val parameter = parameters[j]
        map[argument] = parameter
        i++
      }
    }

    return map
  }
}
