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

package com.android.tools.lint.checks

import com.android.tools.lint.checks.infrastructure.TestFiles.getLintClassPath
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector

class UastImplementationDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector {
    return UastImplementationDetector()
  }

  fun testDocumentationExample() {
    lint()
      .files(
        kotlin(
            """
                /* Copyright (C) 2021 The Android Open Source Project */
                package test.pkg

                import com.android.tools.lint.client.api.UElementHandler
                import com.android.tools.lint.detector.api.Detector
                import com.android.tools.lint.detector.api.JavaContext
                import com.android.tools.lint.detector.api.Scope
                import com.android.tools.lint.detector.api.Severity
                import com.intellij.openapi.components.ServiceManager
                import org.jetbrains.kotlin.psi.KtElement
                import org.jetbrains.kotlin.resolve.BindingContext
                import org.jetbrains.uast.UClass
                import org.jetbrains.uast.UElement
                import org.jetbrains.uast.UExpression
                import org.jetbrains.uast.UField
                import org.jetbrains.uast.UImportStatement
                import org.jetbrains.uast.UThisExpression
                import org.jetbrains.uast.kotlin.KotlinUField // ERROR 1
                import org.jetbrains.uast.kotlin.KotlinUImportStatement // ERROR 2
                import org.jetbrains.uast.kotlin.KotlinUThisExpression // ERROR 3
                import org.jetbrains.uast.kotlin.KotlinUastResolveProviderService // ERROR 4
                import org.jetbrains.uast.kotlin.UnknownKotlinExpression // ERROR 5

                class UastImplementationDetectorTestInput {

                  // Partial definition from Google internal
                  abstract class AbstractDetector(private val uastNodes: Iterable<KClass<out UElement>>) :
                    Detector() {

                      constructor(
                        uastNode: KClass<out UElement>,
                        vararg moreUastNodes: KClass<out UElement>
                      ) : this(listOf(uastNode, *moreUastNodes))
                  }

                  class MockSafetyDetector : AbstractDetector(UClass::class) {
                    override fun createUastHandler(context: JavaContext): UElementHandler? {
                      return object : UElementHandler() {
                        override fun visitClass(node: UClass) {
                          node.fields.forEach { field -> checkFieldSafety(field) }
                        }

                        private fun checkFieldSafety(field: UField) {
                          if (field.name.endsWith("delegate")) {
                            val delegateType = (field as? KotlinUField)?.type // ERROR 6
                            assert (delegateType != null)
                          }
                        }
                      }
                    }
                  }

                  class MockCoroutineChecker : AbstractDetector(UImportStatement::class) {
                    override fun createUastHandler(context: JavaContext): UElementHandler? {
                      return object : UElementHandler() {
                        override fun visitImportStatement(node: UImportStatement) {
                          val alias = (node as? KotlinUImportStatement)?.sourcePsi?.alias // ERROR 7
                        }
                      }
                    }
                  }

                  class MockKtThisChecker : AbstractDetector(KotlinUThisExpression::class) { // ERROR 8
                    override fun createUastHandler(context: JavaContext): UElementHandler? {
                      return object : UElementHandler() {
                        override fun visitElement(node: UElement) {
                        }
                      }
                    }
                  }

                  private fun trimTrivialThisExpr(expr: UExpression): UExpression? {
                    var firstElement = expr

                    return when {
                      firstElement is KotlinUThisExpression && firstElement.label != null -> { // ERROR 9
                        expr
                      }
                      firstElement is UThisExpression -> null
                      else -> expr
                    }
                  }

                  fun KtElement.getBindingContext(): BindingContext {
                    // OK for now to retrieve a service. Won't be allowed after switching to K2 UAST
                    val service =
                      ServiceManager.getService(this.project, KotlinUastResolveProviderService::class.java) // ERROR 10
                    return service?.getBindingContext(this) ?: BindingContext.EMPTY
                  }

                  internal val UElement.realUastParent: UElement?
                    get() {
                      return if (this !is UnknownKotlinExpression) this else null // ERROR 11
                    }
                }
                """
          )
          .indented(),
        *getLintClassPath(),
      )
      .skipTestModes(TestMode.IMPORT_ALIAS)
      .run()
      .expect(
        """
                src/test/pkg/UastImplementationDetectorTestInput.kt:18: Warning: org.jetbrains.uast.kotlin.KotlinUField is UAST implementation. Consider using one of its corresponding UAST interfaces: UVariableEx, UVariable, UDeclaration, UAnnotated, UDeclarationEx, UAnchorOwner, UFieldEx, UField [UastImplementation]
                import org.jetbrains.uast.kotlin.KotlinUField // ERROR 1
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/UastImplementationDetectorTestInput.kt:19: Warning: org.jetbrains.uast.kotlin.KotlinUImportStatement is UAST implementation. Consider using one of its corresponding UAST interfaces: UImportStatement, UResolvable [UastImplementation]
                import org.jetbrains.uast.kotlin.KotlinUImportStatement // ERROR 2
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/UastImplementationDetectorTestInput.kt:20: Warning: org.jetbrains.uast.kotlin.KotlinUThisExpression is UAST implementation. Consider using one of its corresponding UAST interfaces: UExpression, UAnnotated, UThisExpression, UInstanceExpression, ULabeled, UResolvable [UastImplementation]
                import org.jetbrains.uast.kotlin.KotlinUThisExpression // ERROR 3
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/UastImplementationDetectorTestInput.kt:21: Warning: org.jetbrains.uast.kotlin.KotlinUastResolveProviderService is UAST implementation. Consider using one of its corresponding UAST interfaces: BaseKotlinUastResolveProviderService [UastImplementation]
                import org.jetbrains.uast.kotlin.KotlinUastResolveProviderService // ERROR 4
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/UastImplementationDetectorTestInput.kt:22: Warning: org.jetbrains.uast.kotlin.UnknownKotlinExpression is UAST implementation. Consider using one of its corresponding UAST interfaces: UExpression, UAnnotated, UUnknownExpression [UastImplementation]
                import org.jetbrains.uast.kotlin.UnknownKotlinExpression // ERROR 5
                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/UastImplementationDetectorTestInput.kt:45: Warning: org.jetbrains.uast.kotlin.KotlinUField is UAST implementation. Consider using one of its corresponding UAST interfaces: UVariableEx, UVariable, UDeclaration, UAnnotated, UDeclarationEx, UAnchorOwner, UFieldEx, UField [UastImplementation]
                            val delegateType = (field as? KotlinUField)?.type // ERROR 6
                                                          ~~~~~~~~~~~~
                src/test/pkg/UastImplementationDetectorTestInput.kt:57: Warning: org.jetbrains.uast.kotlin.KotlinUImportStatement is UAST implementation. Consider using one of its corresponding UAST interfaces: UImportStatement, UResolvable [UastImplementation]
                          val alias = (node as? KotlinUImportStatement)?.sourcePsi?.alias // ERROR 7
                                                ~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/UastImplementationDetectorTestInput.kt:63: Warning: org.jetbrains.uast.kotlin.KotlinUThisExpression is UAST implementation. Consider using one of its corresponding UAST interfaces: UExpression, UAnnotated, UThisExpression, UInstanceExpression, ULabeled, UResolvable [UastImplementation]
                  class MockKtThisChecker : AbstractDetector(KotlinUThisExpression::class) { // ERROR 8
                                                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/UastImplementationDetectorTestInput.kt:76: Warning: org.jetbrains.uast.kotlin.KotlinUThisExpression is UAST implementation. Consider using one of its corresponding UAST interfaces: UExpression, UAnnotated, UThisExpression, UInstanceExpression, ULabeled, UResolvable [UastImplementation]
                      firstElement is KotlinUThisExpression && firstElement.label != null -> { // ERROR 9
                                      ~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/UastImplementationDetectorTestInput.kt:87: Warning: org.jetbrains.uast.kotlin.KotlinUastResolveProviderService is UAST implementation. Consider using one of its corresponding UAST interfaces: BaseKotlinUastResolveProviderService [UastImplementation]
                      ServiceManager.getService(this.project, KotlinUastResolveProviderService::class.java) // ERROR 10
                                                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/UastImplementationDetectorTestInput.kt:93: Warning: org.jetbrains.uast.kotlin.UnknownKotlinExpression is UAST implementation. Consider using one of its corresponding UAST interfaces: UExpression, UAnnotated, UUnknownExpression [UastImplementation]
                      return if (this !is UnknownKotlinExpression) this else null // ERROR 11
                                          ~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 11 warnings
                """
      )
  }
}
