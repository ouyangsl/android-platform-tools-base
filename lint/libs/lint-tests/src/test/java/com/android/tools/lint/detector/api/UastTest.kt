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

package com.android.tools.lint.detector.api

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.bytecode
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.klib
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.dos2unix
import com.android.tools.lint.helpers.DefaultJavaEvaluator
import com.android.tools.lint.useFirUast
import com.intellij.codeInsight.AnnotationTargetUtil
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.util.Disposer
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiAnnotation.TargetType
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import junit.framework.TestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.asJava.elements.KotlinLightTypeParameterBuilder
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UForEachExpression
import org.jetbrains.uast.ULabeledExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UPostfixExpression
import org.jetbrains.uast.UPrefixExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.analysis.KotlinExtensionConstants
import org.jetbrains.uast.getParameterForArgument
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.util.isAssignment
import org.jetbrains.uast.util.isConstructorCall
import org.jetbrains.uast.visitor.AbstractUastVisitor

// Misc tests to verify type handling in the Kotlin UAST initialization.
class UastTest : TestCase() {
  private fun check(
    source: TestFile,
    javaLanguageLevel: LanguageLevel? = null,
    kotlinLanguageLevel: LanguageVersionSettings? = null,
    check: (UFile) -> Unit,
  ) {
    check(
      sources = arrayOf(source),
      javaLanguageLevel = javaLanguageLevel,
      kotlinLanguageLevel = kotlinLanguageLevel,
      check = check,
    )
  }

  private fun check(
    vararg sources: TestFile,
    android: Boolean = true,
    library: Boolean = false,
    javaLanguageLevel: LanguageLevel? = null,
    kotlinLanguageLevel: LanguageVersionSettings? = null,
    check: (UFile) -> Unit = {},
  ) {
    val pair =
      LintUtilsTest.parse(
        testFiles = sources,
        javaLanguageLevel = javaLanguageLevel,
        kotlinLanguageLevel = kotlinLanguageLevel,
        android = android,
        library = library,
      )
    val uastFile = pair.first.uastFile
    assertNotNull(uastFile)
    check(uastFile!!)

    // Validity check: everything should be convertible
    pair.first.psiFile?.accept(
      object : PsiRecursiveElementVisitor() {
        override fun visitElement(element: PsiElement) {
          try {
            element.toUElement()
          } catch (e: Throwable) {
            System.err.println(
              "Converting element " + element + " of class " + element.javaClass + ":"
            )
            throw e
          }
          super.visitElement(element)
        }
      }
    )

    Disposer.dispose(pair.second)
  }

  fun test263980844() {
    val testFiles =
      arrayOf(
        bytecode(
          "libs/lib1.jar",
          kotlin(
            "src/test/pkg/KotlinFoo.kt",
            """
                    package test.pkg
                    open class KotlinFoo {
                        fun kotlinBar() {}
                    }
                    class SubKotlinFoo : KotlinFoo()
                    """,
          ),
          0xa46d4086,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGIOBijg4uJiEGILSS0u8S5RYtBiAAB9et6n
                JAAAAA==
                """,
          """
                test/pkg/KotlinFoo.class:
                H4sIAAAAAAAA/2VQXU8aQRQ9dxYWXLAuaC1Q28Q3bdMuGp+sMakmJCjaRBte
                eBpgQ4eP3YYZjI/8Fv+BT036YIiP/ijjncUQo5Psufecu+fOnfvw+P8OwB4+
                E4om1Cb4O+gFp7EZqqgWxxkQwe/LKxkMZdQLfrX7Ycdk4BDcAxUpc0hwtrab
                eaThekghQ0iZP0oT1hpv2/0gLA0SciTHhEJjToKz0MiuNJLrYnTl8EBkIWsB
                BBqwfq0sq3LW3SF8m019T5SEJ/zZ1BNZm4jsemk23RVVOkrf37gsnLi+UxHV
                lDXtkm2VX8zyfWB41uO4GxJWGioKzyejdjj+LdtDVoqNuCOHTTlWlj+L3mU8
                GXfCmrKkfDGJjBqFTaUVV39GUWykUXGksQnBq7BH8JW8GcYys8C+hWP6yz9k
                b5NyhdFNRAcfGfPzH7AEj2MBuYX5a7IK/l4bUy+M9GwU2EiwhE8c9+3Duely
                C04d7+pYqcNHgVMU61jFWguk8R7rLaQ1PI0PGq5GjpMnUjk4fyACAAA=
                """,
          """
                test/pkg/SubKotlinFoo.class:
                H4sIAAAAAAAA/21Ry07CQBQ9t4WCtcpDUFDZqwsLxJ3GRE1IGqsLMWxYFWh0
                UugYOjUu+Rb/wJWJC0Nc+lHG20qIiS7m5DxuZk7ufH69vQM4QoNQVX6k7Ifg
                zu7Gg0upxiLsSJkDEcrL6JevE4wTEQp1StD39nsWsjBMZJAjZNS9iAhb7r93
                HhNKbpAq+8pX3shTHnva5FHnMpRAPgEQKGD/SSSqyWzUIjTmM9PUalp65rPa
                fNbWmnSe/Xg2tKKWDLUJFfdvZX7CWorDQHHNCznyCQVXhP51PBn401tvMGan
                7MqhN+55U5HohWl2ZTwd+h2RiPpNHCox8XsiEpyehaFUnhIyjNCCxltY9E+W
                wlhjZacayB68Iv/CREOd0UhNA9uM1s8AVmCm+U6KW9hNP4mwypnVh+5gzcG6
                gwKKTFFyUMZGHxShgirnEcwImxGMb1x6FCzhAQAA
                """,
        ),
        kotlin(
          """
                import test.pkg.SubKotlinFoo

                fun test(instance: SubKotlinFoo) {
                    instance.kotlinBar()
                }
                """
        ),
      )

    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            if (node.sourcePsi is KtSuperTypeCallEntry) return super.visitCallExpression(node)

            val bar = node.resolve()
            assertNotNull(bar)
            assertEquals("kotlinBar", bar!!.name)
            assertEquals("KotlinFoo", bar.containingClass?.name)

            return super.visitCallExpression(node)
          }
        }
      )
    }
  }

  fun test257514416() {
    val testFiles =
      arrayOf(
        bytecode(
          "libs/lib1.jar",
          kotlin(
            "src/test/Dependency.kt",
            """
                    package test

                    object Dependency {
                        fun foo() {}
                        fun String.bar(): Int = this.length
                    }
                    """,
          ),
          0xbd459443,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGIOBijg4uJiEGILSS0u8S5RYtBiAAB9et6n
                JAAAAA==
                """,
          """
                test/Dependency.class:
                H4sIAAAAAAAA/21S227TQBA96yS266ZtWugdyqWB3qBOS7lIqYpKAeEqBESr
                SqhPjrOkmzg2sjcRvPWJD+GZlwqJIpBQBW98FGLWiXoDWZ6Z3T1zZubs/v7z
                7QeAFTxgGJA8lvZj/pYHVR547w0whlzdbbu27wY1+0Wlzj1pIMWgr4pAyDWG
                1OzcThYZ6BbSMBjSck/EDIOlc1xFgr4JQ7IVN2IYni2d8G7JSAS14pzDMF0K
                o5pd57ISuSKIbTcIQulKEVJcDmW55ftFVV1VWTMxwDDVCKUvArvebtoikDwK
                XN92AkUZCy82MEjVvD3uNbr5L93IbXICMsyc7qIzXfE/fdGAF3DRwhCGz+jR
                OTcwSh35PKjJvUQPJ4txTFgYwyRDT161mk+GHvqXm8Fc9fxESwuaEtB0ylvb
                6+WNJ1lcg9VDm9eVnN0pn3PpVl3pUqLWbKfo5pgypjJgYA3afyfUqkBRdYnh
                2dH+hKWNaZaWO9q3NFMFmqXCnEl/v/nrgzZ2tL+sFdgjw9R+ftTpfFPPpSa0
                QnrTymXI6/NawVB8y0xV6Tu51sWGZJh81QqkaHInaItYVHy+fnJr9CI2wiqn
                x1USAS+3mhUebbuEUXKEnuvvuJFQ6+5m/jzX8XWdIbW2wlbk8adC5Yx3c3b+
                qY4l0i+dSDOu5CS/RCudfD/5NJ1mktUyrWwlIPnM/CHMAwo03OmCFXSFbLYD
                QA9RAYPoPU5eSIrQfz4xcyqRHSdm0UcolfiQvEa+d2Eo9xUjC19w6dMZCp0+
                RTHSgXUpVHQZU3R+l2KDdScyceW4pdEkgYDfob0+xNXPmD5INjTcS2wB98lv
                EDxPbd7YRcrBTQczDmYxRyHmHZrr1i5YjNtY3IUZw4phx9Bj9CZBNrH2X1tj
                1gpGBAAA
                """,
        ),
        kotlin(
          """
                import test.Dependency.foo
                import test.Dependency.bar

                fun test() {
                    foo()
                    "42".bar()
                }
                """
        ),
      )

    val names = setOf("foo", "bar")

    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            val resolved = node.resolve()
            assertNotNull(resolved)
            assertTrue(resolved!!.name in names)
            assertEquals("Dependency", resolved.containingClass?.name)

            return super.visitCallExpression(node)
          }
        }
      )
    }
  }

  fun testJavaAnnotationTarget_fromSource() {
    // Regression test from b/266740119: not applicable only for project-type dependency.
    val testFiles =
      arrayOf(
        java(
            """
                package test;

                class Test {
                    @MyNullable
                    String foo(CharSequence cs) {
                        return cs.toString();
                    }
                }
                """
          )
          .indented(),
        kotlin(
            """
                package test

                import java.lang.annotation.ElementType.METHOD
                import java.lang.annotation.ElementType.PACKAGE

                @Retention(AnnotationRetention.BINARY)
                @Target(
                    AnnotationTarget.FUNCTION,
                    AnnotationTarget.FILE,
                )
                @Suppress("DEPRECATED_JAVA_ANNOTATION")
                @java.lang.annotation.Target(
                  METHOD,
                  PACKAGE,
                )
                annotation class MyNullable
                """
          )
          .indented(),
      )

    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitAnnotation(node: UAnnotation): Boolean {
            if (node.qualifiedName == "test.MyNullable" && node.javaPsi != null) {
              val targets = AnnotationTargetUtil.getTargetsForLocation(node.javaPsi?.owner)
              val applicable = AnnotationTargetUtil.findAnnotationTarget(node.javaPsi!!, *targets)
              // https://youtrack.jetbrains.com/issue/KTIJ-24597
              // annotation target found for K2 only
              if (useFirUast()) {
                assertEquals(TargetType.METHOD, applicable)
              } else {
                assertNull(applicable)
              }
            }

            return super.visitAnnotation(node)
          }
        }
      )
    }
  }

  fun testJavaAnnotationTarget_fromBytecode() {
    // Regression test from b/266740119: applicable if prebuilt bytecode is given.
    val testFiles =
      arrayOf(
        java(
            """
                package test;

                class Test {
                    @MyNullable
                    String foo(CharSequence cs) {
                        return cs.toString();
                    }
                }
                """
          )
          .indented(),
        bytecode(
          "libs/lib1.jar",
          kotlin(
              """
                    package test

                    import java.lang.annotation.ElementType.METHOD
                    import java.lang.annotation.ElementType.PACKAGE

                    @Retention(AnnotationRetention.BINARY)
                    @Target(
                        AnnotationTarget.FUNCTION,
                        AnnotationTarget.FILE,
                    )
                    @Suppress("DEPRECATED_JAVA_ANNOTATION")
                    @java.lang.annotation.Target(
                      METHOD,
                      PACKAGE,
                    )
                    annotation class MyNullable
                    """
            )
            .indented(),
          0xf7411d45,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJOBihQYtBiAAD1Iry9GAAAAA==
                """,
          """
                test/MyNullable.class:
                H4sIAAAAAAAA/4VSTU9aQRQ99yGC1A/UfoDW4ifuxBp3rp4U6ot8GKBNDKsR
                JubJ8J7xDbTs2PU/uTCkS3+U6R1NgTQvdXPnzL3nnLl3Zh6f7h8AHGOfsKRl
                oHPlQaWnlLhSMgYiJG9EX+SU8K5z1asb2dIxRAiZSVZ4nq+Fdn0vZ49hDFHC
                Rqnja+V605Sa1NIz6IQQ7QvVk4T9EN7Ealoxe+pU7NolYS1E0hB311Iza1Eo
                5f+Q7ZdEQNj97wFjXbz4rZJvONUKYabolApmgtA5x4rt8HpByS733BjcStN0
                udA4q34hxC7s/Ln9lX03w3XTo2ZfoVz4ym0NzC3mS3a9Tlj+O2RZatEWWnDN
                6vYj/LpkwpwJIFCH8z9dsztk1P5MSI+G8YSVshJWcj3++5eVGg2PrEM6HQ0N
                4ch4//M12JutFiaJg44mJOp+764li67iV03XetxoV353A5cJk/sOsuyKGdbP
                mo4Y7z3HXWR57SIK/niIS8whgTcM55uwJBawaMISkqb6nFrGigmrePsieIf3
                +MD6VBMRB2kHaw7W8ZEhNhx8QqYJCrCJLVYH2A6w8wdjiabo/wIAAA==
                """,
        ),
      )

    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitAnnotation(node: UAnnotation): Boolean {
            if (node.qualifiedName == "test.MyNullable" && node.javaPsi != null) {
              val targets = AnnotationTargetUtil.getTargetsForLocation(node.javaPsi?.owner)
              val applicable = AnnotationTargetUtil.findAnnotationTarget(node.javaPsi!!, *targets)
              assertEquals(TargetType.METHOD, applicable)
            }

            return super.visitAnnotation(node)
          }
        }
      )
    }
  }

  fun testExpressionTypeOfCallToInternalOperator() {
    // Regression test from b/270595352.
    val testFiles =
      arrayOf(
        bytecode(
          "libs/lib1.jar",
          kotlin(
            """
                      package test

                      object Dependency {
                          internal operator fun unaryPlus() = Any()
                          operator fun unaryMinus() = Any()
                      }

                      class OtherDependency {
                          internal operator fun inc() = this
                          operator fun dec() = this
                      }
                  """
          ),
          0x6fcad72,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijg4uJiEGILSS0u8S5RYtBiAABz6lUC
                JAAAAA==
                """,
          """
                test/Dependency.class:
                H4sIAAAAAAAA/41RTU8TURQ973U6nQ6lTBGlgN+gFhcONO5EE0SNJaUaIU0M
                q9f2BV87nTGdN43suuKH+AskLkwkMQR3/ijjfUMDBBc6i/t5znn33vn1+/sP
                AI9RYZjSMtb+C/lRhh0ZtvdzYAxeVwyFH4hwz3/T6sq2ziHDYK+pUOlnDJnK
                crOALGwXFnIMlv6gYoZS/ZLWE4ZiEorB/tsgiZf6QoUMM5Xl+mV1wi3Wo8Ge
                35W6NSBY7IswjLTQKqK4EelGEgSEclO1LRUm9Jyz1g7SgVxwM4VTa2zvrDc2
                XhZQgpun4rSZqRdpgvlbUouO0IJUeH+YofWZMXljwMB6VP+kTLZCUWeVYeN4
                VHB5mbvcOx653LGcnwe8fDyq8hX2POfwk8829/hm3svMU+X1yQHfLHrWaTxK
                u07WSFWZeSB/dgiGyfMTPepphoV3SahVX9bCoYpVK5Dr59vTdTeijqQfVVeh
                bCT9lhzsCMIwTNejtgiaYqBMPi6621EyaMtXyiRzY+HmX7JYpftY6epz5lzk
                71Jmk58kb1E3m2aLlPnmQOSzD7/BOaSAY2kMNtB7ZAunAORJCiQ4QRWekqvk
                Tc85gvWe+F8u8bMX+M6Yf3GUEor/rWX/Q8vB1NlSs8Q038QROGl5X3HlMC1w
                3E/tHTwg/5TgMzTk1V1karhWw2wNZcxRiPkaFnB9FyzGDdzchR3DjXErNkEx
                xu0YE38ApJb8UmoDAAA=
                """,
          """
                test/OtherDependency.class:
                H4sIAAAAAAAA/41RyW4TQRB93eN17DjjEIKTsCY5JBFinIgbm1iEmGhwJEC+
                +NQet5K2xz1opm3BzSc+hC+AExIHZOXIRyGqzUhBLII+vKp69eqpq/vrt89f
                ANzGNsOqkZnxj82pTJ/I11IPpI7elsEYvKGYCj8W+sQ/7g9lZMpwGEp3lVbm
                PoOzu9eto4iSiwLKDAVzqjKGtfBPhncYKkpHO2OhNENrd+9vqu0wSU/8oTT9
                lKSZL7ROjDAqobyTmM4kjknlDGTE0AxHiYmV9p9LIwbCCOrw8dSh1ZiFqgUw
                sBHxb5St2pQNDhgezGd1l7e4y735zOWVQms+O+Rt9qh49r7EPX5U9ZwN3p7P
                np2940cNr5AXM277laK1OWTW3KG1GJbOl7g1MvQYj5OBZFgOlZadybgv01ei
                HxOzEiaRiLsiVbbOyc0XE23UWAZ6qjJF1MPzrRncl8kkjeRTZaXrubT7mxAH
                4PQT9jh0L/oYwitU+fYJKBb3P6HykRKOq4SlBVnFNcL6DwFVLsUmasTwxfDN
                fJjvf/hl0v1pkueT1/Pu0sKl8R8utX+4cNxY4GVsUbxH7DLd0+vBCdAMsBLg
                AlYpxcUAa7jUA8vQwnoPpQxuho3MJo0Mmxlq3wFRZP9W9wIAAA==
                """,
        ),
        kotlin(
          """
                 import test.Dependency
                 import test.OtherDependency

                 fun test() {
                     +Dependency
                     Dependency.unaryPlus()
                     -Dependency
                     Dependency.unaryMinus()

                     var x = OtherDependency()
                     x++
                     x.inc()
                     x--
                     x.dec()
                 }
              """
        ),
      )

    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            if (node.isConstructorCall()) return super.visitCallExpression(node)

            if (node.methodName?.startsWith("unary") == true) {
              assertEquals("java.lang.Object", node.getExpressionType()?.canonicalText)
            } else {
              assertEquals("test.OtherDependency", node.getExpressionType()?.canonicalText)
            }

            return super.visitCallExpression(node)
          }

          override fun visitPrefixExpression(node: UPrefixExpression): Boolean {
            val t = node.getExpressionType()
            assertEquals("java.lang.Object", t?.canonicalText)

            return super.visitPrefixExpression(node)
          }

          override fun visitPostfixExpression(node: UPostfixExpression): Boolean {
            val t = node.getExpressionType()
            assertEquals("test.OtherDependency", t?.canonicalText)

            return super.visitPostfixExpression(node)
          }
        }
      )
    }
  }

  fun testPropertiesInCompanionObject_fromBytecode() {
    // Regression test from b/301453029
    val testFiles =
      arrayOf(
        bytecode(
          "libs/lib1.jar",
          kotlin(
              """
              package some

              interface Flag<T>

              class Dependency {
                  companion object {
                      @JvmField val JVM_FIELD_FLAG: Flag<*> = TODO()
                      @JvmStatic val JVM_STATIC_FLAG: Flag<*> = TODO()
                      val VAL_FLAG: Flag<*> = TODO()
                      var varFlag: Flag<*> = TODO()
                  }
              }

              class OtherDependency {
                  companion object Named {
                      @JvmField val JVM_FIELD_FLAG: Flag<*> = TODO()
                      @JvmStatic val JVM_STATIC_FLAG: Flag<*> = TODO()
                      val VAL_FLAG: Flag<*> = TODO()
                      var varFlag: Flag<*> = TODO()
                  }
              }

              object DependencyObject {
                  val VAL_FLAG: Flag<*> = TODO()
                  var varFlag: Flag<*> = TODO()
              }

              val DEPENDENCY_TOP_LEVEL_VAL_FLAG: Flag<*> = TODO()
            """
            )
            .indented(),
          0xc7b66f2d,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijgEuNiKc7PTRVic8tJTPcuEWILSS0u
                8S5RYtBiAAD0O465MAAAAA==
                """,
          """
                some/Dependency＄Companion.class:
                H4sIAAAAAAAA/5VVTVMbRxB9sxLSahFikYHw4YA/lFiAjYA4iRMIGAtjCy8k
                hSgVVRyoYZnghdUutbNSJTdOzv/IJddwCpVDiuKYH5VKz2oBIUiVo8N89Hv9
                eqa7Z/X3P3/+BeA51hmGpV8XpRVxLLx94dk/F8p+/Zh7ju+lwRjMQ97kJZd7
                B6Xv9w6FHaaRYEgtOJ4TLjIkihO1LLqQMpBEmiEZvnckw6j1n6rzDPkDEa7V
                1nerW8tblfLuqrX8hqGnONFyWnX5AZHM9v3C5CKZHlt+cFA6FOFewB1Plrjn
                +SEPSVSWNvxwo+G6xOrtCJ1GL8N9bttCysLtyAX7OIs+ZA2YyDOM3cFoi8Mw
                aB35oet4pcNmvbTWrFcVYlPcbvKsLVvxdQauA14ao0iftCINMRgK4oG6HkN/
                G71li9ijLfZ9Yss2dq7YlqqJGkNf8WaulC29QC7PlhZ1PKRrtR3a8UIReNwt
                VbwwoDw6tkzjMR3Zfi/soziRP/CA1wURGZ4Urc4mmG+zVJWIOkYWn+FzAwU8
                ub6PvHmfCYyr+0xStm4Ue7Zo3XXAFfEjb7hhmRIfBg079IN1HhyJgIIZ0FS/
                3SvY1+BuPUIZpv+fGuXv0mFdhHyfh5xsWr2ZoEfC1JBRAxjYEdl/ctRuhlb7
                swy/nZ/0G9qQZmjm+Ymh6Vproxv6xYfE0PnJnDbDXqV17eLXlGZqm/1mYiSV
                1/TETPJFavvil6SyG+cnmw/MrltAjoB8Uk+Z2khST5v65oCZuSQRrBNMgEHA
                mNl9CWxffMjFvgRmTf1RUu8xc+q4c0xdIqf6e7Xy2lqJe7X31mPUrxs53bzs
                uszVGyarMk0fhfTiy/6+IA3L8cRGo74ngi2+55Ilb/k2d6n6jtrHxkzVOfB4
                2AhoPbrZ8EKnLipe05EOwcvt78ygFxwIm4dinz5SMbV2B7HQKXPVujdo2Yrn
                iaDscimFkq/6jcAWq44rMEu9lFQFRppW9DGjJJVpV1IVp7lr8g/op7TQsEJj
                KjKm8JrGbIuADAya+9BNlkTkPB07J89w7/cOX73NNxn7rkacHryJWTnAzKCf
                1i29HrR+7DSa3kZjJSYPRMEH7wo+3Bk883HBSW/kLr1PO/WMj9Ybw3ist0So
                RnP3VP7RGYpTZ5i6md9ULDvYosWyavUQTwlvBXiANVU0FqeLUSGexbVTsxbX
                bvo0erzXel1Xel1U5BnCEngXXUU5mVjGMKwowqsoxdtknyXu3A4SFXxRwfMK
                vsRXtMTXFbzANztgEt9ifof+fehTjQWJlMR3Ek8lFiX6JbolshJLEoMSLyVG
                JMYkxv8F0/QddIkHAAA=
                """,
          """
                some/Dependency.class:
                H4sIAAAAAAAA/51UW28TRxT+Zu3YzmaJTUhoHMKlxG0dCtkkpTdsAsaJ6aJN
                KjWRJZQHNFlP3U32Eu2OrfYtv6W/gFKpSEVqoz7yoxBn1gs2ufQBS56Zc853
                vjPnMvv6zd//ALiLHxiKcegLc10ciqAjAue3PBhDaZ/3uenxoGv+uLcvHJlH
                hiFXdwNXrjFkqottA2PI6cgiz5CVv7gxw0X7BFeNYaor5JP25rPtncaO1XzW
                shuPGS5UFwfQlse7BCqNyvVba6RasMOoa+4LuRdxN4hNHgSh5NIN6bwVyq2e
                5xFqvBn6hzwgLcOVk9Er7401A0WUxqHhIkP5XFgelwxMwNAxjRmGee44Io4r
                pzOoOIdUuFNpTYwkZWAWZRVxjmFmSNRu2EOGwjvJwNUB+BrD9AiYR4orweb7
                A8HApwPozSE0/hA6WR25x2KbfOuEuPOAGleoO17aw5WqfRBKksz9vm+6gRRR
                wD2qy8+858km1VlGPUeG0SaPDkRUG3S8qmqzyDCX+lIrLP/QE74ggs5GFIVR
                Hl8yrFft4Qhty8gNujXrYwLe0XEbS5SUqnbL2rDX02JPnpyYmVH6J32/5Qqv
                U1NTmeo3heQdLjnpNL+foQfA1DKuFjCwA9L/6ippmU6dFYbK8ZGha7OarpWO
                j3StoM0eH93IrGrL7B7LPBr77/ecVtIUdpUphry6zNKBpFn8qRdI1xdW0Hdj
                d88TjeH40tRuu92Ay14k6Ok0ww5tRdsNxFbP3xPRDic8PRw7dLhHfXWVnCoN
                KwhE1PR4HAsi0rfDXuSIlqts5TRm+1RErNC8ZCmvMZTVQ6CrPiQpR/sk7WUs
                019Dg84aYZVNp9Mq7co+p0b5hP2d75ya3HNsuhrVc2z0lVHfD1ofkWSq8qv7
                3XqJwh8JvJmC1aXXaTXS8zjRqivTM0Umcb6UWIjyBab+wuXniTCIeWE0ZqmM
                TxIa5TSfOmVf4MrzpP0jAQk5fxby+lnIG2chF85CVvDZqXz/xML/5/s55QoU
                8MX7Yi2RVv0uv8L0U/YSxDH1CrefsiwJ5r8fBJ6gQmewkTSDvhcUp0h8rSTi
                Azym/VvSf0WMd3eRsfC1hW8s0n1HR3xv4R5qu2Ax6ri/i2wMPcZajFyM4ltZ
                loBxQwYAAA==
                """,
          """
                some/DependencyObject.class:
                H4sIAAAAAAAA/41UTW/bRhB9S9EkRSk25fhTad02SRPZbkzH/a5Vp4odFwIU
                NbADI4EPwZraKrQoMiBXQnvzKT+kv6BtDi4aoDXSW39U0VmRTiTFBcoDd+fN
                zJu3s0P+/c/vfwD4BHcZZpOoK9wd8UyELRF6P353dCw8aYIxOMe8z92Ah233
                HM0xGFU/9OUWQ66yfFDEBAwbOkwGXT71E4b5xoWMmwyFtpAHtcaT3UbtW4ZL
                leU0cjfgbfI6w3Z1ZYuga40obrvHQh7F3A8Tl4dhJLn0I9o3I9nsBQFFWW84
                C0OMRUzByUNDicFWlXmscAazn+6KmEkDZikgGQqYrAzRLB8wlCqjyhRmVinl
                1p0tC2WGpU4kAz90j/td1w+liEMeuPVQxqTa9xIT71CjvafC62SyH/CYdwUF
                MtysNMb7vDmE7CsSJaOIJbxn4128T0euesHgFmyST6236s39h7Xm9r0irsNW
                Z/qQoZyJopL17rNAdAUpa92L4yg2cZNhp/J2mXrjopPsiO95L5Db1HcZ9zwZ
                xfd53BHxZjoAyzYqWKG+jd9f6ZztvpC8xSUnTOv2czR7TL3y6gUG1iH8B19Z
                67Rr3Wbwz05mbG1BszXn7MTWLC01LMP667m2cHayoa2zu6alvfrJ0Bxtb9bJ
                lY1pzcqt64RY9tlJWbcmHGNvyTHPHY9ePZ8k52TqtBzjqm7lHVsV3GBKhqmU
                r3Ukw5W9Xij9rqiHfT/xjwJRezN5DPl9vx1y2YsFDf121KJlquGHotnrHon4
                Iad4hulG5PGAZspXdgZeH+d9PQcjBez9qBd7YtdXOYtZzsFbSnCbLlqnpuWw
                qO6djvAVWQatk7SW1QdAEZu01/DRiM9Ws/8fvglCJgZWlSxX3Y9CV05h/TII
                /zoLBuaxRe9iGoA80QIlFAjJDZLXsmT9V0z/PJa7MJSrZ7mpnEsjckq4fBHf
                3Djf4v/mm6faKd8d8mq0Flanr/yGD1ZfYG70iEZGO5eGZbRqV6b+sazAIhFB
                fYdpBQtXX7fvFvVFPbMvoT0+xbUXuPESlcdMZ6dY/XMw/ee1bJKl4ZsB45eo
                0dogVBGsHSJXh1vHep3ufIO2+LhOv+9PD8ESfIbPD1FI6B+GLxIYCaYSOIlC
                iglmElxOMJ9g4V8LzDpB9QUAAA==
                """,
          """
                some/Flag.class:
                H4sIAAAAAAAA/2VQS0/CQBicb0EKxUfxWU9ejQeLxJMaEy8kTTAmQrxwWmBt
                Fso2YbfEY3+XB9OzP8r4FRMPuoeZb2Yns4/Pr/cPANcICS2bLVXUT2XigQjn
                d6ObwVyuZZRKk0RPk7mautv7/xYh+Ot5qBM6g0XmUm2iR+XkTDrJSbFc1/g8
                qqBVAQi0YP9NV6rL0+yKcFYWvi9C4TOLoCyar2FZXNSbZRFQT3RFFesR2oPf
                K3M3jaiq8yp5uXD8oKFOjHT5ShH8YZavpqqvUxanz7lxeqletNWTVD0Ykznp
                dGZsg5uxhZ9VwxGjYD7e8CFONn9FaHDGG6MWoxmjFcNHm0dsx9jB7hhksYeA
                9y06FvsWB98ARADnaAEAAA==
                """,
          """
                some/FlagKt.class:
                H4sIAAAAAAAA/4VRXU8TQRQ9s1va7ZaPgqK0iCD40frggjHxAUJCaGs2LoUI
                aUJ4aKbt2Gy7H2Z2tvGR3+IvML6YaKLER3+U8U6tgvLgJDNz77nnnJmb+/3H
                py8AnqHKUEjiUDiNgPdfqhwYQ3HAR9wJeNR3DjsD0SXUZFjrC1WrH9WbtXpz
                /7R9cnjU9uqtutdu7Xnthrf3gmGmUvX+mG2T0dV85/EuQRteLPvOQKiO5H6U
                ODyKYsWVH1PcjFUzDQJirfznocKVZ6aRh52HgQKDtdMN/MhXuwxmpdpiKA9j
                RYi2dsM3gQhFpESvLmUsc5hjyO5M6LWKd9n1sZJ+1N92vYl6MAodn4Qy4oFT
                E695Gqh9+rGSaVfF8oDLoZDb1dY05rFgo4gbDLP/dj7/2+1AKN7jihNmhCOT
                xsD0kdcHGNhQBwYV3/o62qSot8VQuTi3bdrGkmEblrG+WLw4L2cXDMvcZN/e
                ZS2qlTOWUTQ1/ylDTj/9ZKgY8sd+P+IqlYJh+VUaKT8UbjTyE78TiL3LATBk
                9uMekeY8PxLNNOwIecKJw2Afx6nsioavk9LEo3XNAVs0h8y4jbIeC2Ubuhks
                4j7dWcKtcV7CFGUmHlB2h1C9Mh8w/X6sfTjh6tovfe4vvYUZzFKs1RvkpFfh
                M4qnLMM+4ubXayYGHo1t1lGh+zmht0h2+wymiyUXJZe+u+wSccXFXayegSVY
                w70zZBLkE9gJphJkfwJs1hl+NQMAAA==
                """,
          """
                some/OtherDependency＄Named.class:
                H4sIAAAAAAAA/5VVS1MbRxD+ZvVaFiEW8QhgB/xQYgE2AuIkTiAQDMYRWXAK
                USqqOFDDMoGF1cq1M1IlN07O/8gl13AKlUOK4pgflUrPajEClCpHh3n09/XX
                M909q7//+fMvAM+xwTAq6zVReqOORLgq3orgQATuz4VNXhMHGTAG+5g3ecnn
                wWHpzf6xcFUGCYb0ghd4apEhUZyoZpFC2kISGYakOvIkw33nv2XnGfKHQq1X
                N/Yq28vb5ZW9NWf5NUNPcaLltebzQyLZ7fuFyUUyPXbq4WHpWKj9kHuBLPEg
                qCuuvDqtN+tqs+H7xBroFDuDXjoWd10hZeFu+IL7Nos+ZC3YyDOMdWC0BWMY
                ck7qyveC0nGzVlpv1ioacSl4N3lWl534ToPXAa+MUaSPWpGGGSwN8VDfkU7e
                Rm/ZIva9Fvs+sWUbO1dsy9dElaGveDNh2pZZIJdnS4smHtK12g7tBUqEAfdL
                5UCFlEzPlRk8piO7R8I9ibP5Aw+pZERkeFJ0brfCfJulokX0MbL4BJ9aKODJ
                9X3kzftMYFzfZ5KydaPis0Wn0wFXxY+84asVSrwKG66qhxs8PBEhBbNg6K7r
                L7jX4F4tQhmm/58a5e/KYUMofsAVJ5tRayborTA9dOkBDOyE7D95ejdDq4NZ
                ht8uTgcsY9iwDPvi1DJMo7UxLfPyXWL44nTOmGEvM6Zx+WvasI2tATsxms4b
                ZmIm+SK9c/lLUtuti9OtB3bqDpAjIJ8007YxmjQztrk1aHddkQg2CSbAImDM
                7r4Cdi7f5WJfArO2+Shp9tg5fdw5pi+R0/29Vn7lrMa92nvnRZrXjZxpXnVd
                KnrEZNHb6RNFb36lfiDI3/ECsdmo7Ytwm+/7ZMk7dZf7VHlP72NjV8U7DLhq
                hLS+t9UIlFcT5aDpSY/g5fY3ZtHrDYXLlQ43ElOrHYiF2zLv2/YGLVsOAhGu
                +FxKoeUr9UboijXPF5ilPkrq4iJDK/qcUYJWaFfS1aY5NfkHzDNaGFilMR0Z
                e/GKxmyLgC5YNPehmyyJyHk6dk6eo//3W759bb7J2Hct4vTgdczKAXYXBmjd
                0utB68fOoum7aCzH5MFIdKhT8JHbwfMfFpz0RjvpfXxbr/+D9cYwHustEWrQ
                3D2Vf3SO4tQ5pm7mNx3LDrVosaxePcRTwlsBHmBdF43F6aL2xLO4dno24tpN
                n0UP91ov9V4vRUWeISyB72lnRU42ljECJ4rwMkrxDtlniTu3i0QZn5XxvIzP
                8QUt8WUZL/DVLpjE15jfRa+kzzQWJNIS30g8lViU9J+EbomsxJLEkMS3kv54
                MSYx/i/sb3NVjAcAAA==
                """,
          """
                some/OtherDependency.class:
                H4sIAAAAAAAA/51UW08bRxT+Zm1ss2ywQ4BiQtskuI3JhQWa3mKXxLFxutFC
                pIIsRTxEw3rqLOwF7Y6t9o3f0l+QplIjNVKL+tgfVfXM2tROMHmIJc/MOeeb
                71xn//n3jz8B3MP3DLNx6AvzqXwhooY4FkFbBM7PWTCGwiHvcdPjQcd8enAo
                HJlFiiFTdQNXbjKkyistAxPI6Egjy5CWL9yYYd4eR1hhmOkI+aS1/Xx3r7Zn
                1Z837dpjhkvllT6+6fEOgQqjcvXWJqmW7TDqmIdCHkTcDWKTB0EouXRDOu+E
                cqfreYSa2OG+aDMsjXVfSqwVA3kUJqHhMsPixbgsrhiYgqFjFnNEyR1HxHHp
                fPwl55ghfy6pqZGUDCygqFwuMswNiVo1e8iQO5MMfNwHf0J9GQHzSHEl2Gyv
                Lxi43ofeGELjt6HT5ZE4Vlp0t0qIuw+od7mq4w3auF62j0JJknnY8003kCIK
                uGc2xI+868k6VVlGXUeG0TaPjkRU6Te9rGqzQlUc3KVGWP6xJ3xBBO2tKAqj
                LG4zNMr2cIp2ZeQGnYr1IQ7v6riDVUpKVbtpbdmNQbGn352XuVH6Jz2/6Qqv
                TfrLZ/ptIXmbS046ze+l6CEwtUyqBQzsiPQ/uUpao1N7naF0emLo2oKma4XT
                E13LaQunJ9dSG9oau89Sjyb+/iWjFTSF3WCKIauCWT2SDFd/6AbS9YUV9NzY
                PfBEbTi8DJO7bifgshsJej31sE1b3nYDsdP1D0S0xwlPz8YOHe5RX10lD5SG
                FQQiqns8jgUR6bthN3JE01W24sBn65xHrNO8pCmvCRTVS6BQH5KUoX2a9iLW
                6K+hRmeNsMqm02mDdmVfVKP8jv3s7qKa3AtsuhrVC2z0oVGfEFofkWSq8qv4
                br1G7tcEXh+AVQgNWo0+AJNEq0KmZ4pUcvlKYiHKV5j5HfMvE6Hv89Koz0IR
                HyU06tLS4FL6Fa6+TNo/4pCQS+OQn45DXhuHXB6HLOGzc/n+huX35/s55Qrk
                cPP/Yq2SVv3m32D2GXsN4ph5gzvPWJoE86+3HE9Rs1PYSppB3wvykye+ZuLx
                AR7T/jXpvyDGe/tIWfjSwlcW6b6hI761cB+VfbAYVXy3j3QMPcZmjEyM/H+K
                owqASwYAAA==
                """,
        ),
        kotlin(
          """
            package some

            private fun consumeFlag(p: Flag<*>) {
              println(p)
            }

            fun test() {
              consumeFlag(Dependency.JVM_FIELD_FLAG)
              consumeFlag(Dependency.JVM_STATIC_FLAG)
              consumeFlag(Dependency.VAL_FLAG)
              consumeFlag(Dependency.varFlag)
              consumeFlag(OtherDependency.JVM_FIELD_FLAG)
              consumeFlag(OtherDependency.JVM_STATIC_FLAG)
              consumeFlag(OtherDependency.VAL_FLAG)
              consumeFlag(OtherDependency.varFlag)
              consumeFlag(DependencyObject.VAL_FLAG)
              consumeFlag(DependencyObject.varFlag)
              consumeFlag(DEPENDENCY_TOP_LEVEL_VAL_FLAG)
            }
          """
        ),
      )

    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            // Test call-sites of `consumeFlag`, not `consumeFlag` itself.
            if (node.methodName != "consumeFlag") {
              return super.visitCallExpression(node)
            }

            val arg = node.valueArguments.singleOrNull()
            val selector = arg?.findSelector() as? USimpleNameReferenceExpression
            val resolved = selector?.resolve()
            assertNotNull(resolved)
            assertTrue(resolved is PsiField)

            return super.visitCallExpression(node)
          }
        }
      )
    }
  }

  fun test126439418() {
    // Regression test for https://issuetracker.google.com/126439418 /
    //  https://youtrack.jetbrains.com/issue/KT-35801
    val source =
      kotlin(
          """
                private val variable: Any = Object()

                fun foo1() {

                    @Suppress("MoveLambdaOutsideParentheses")
                    foo2({ variable.hashCode() })
                }

                fun foo2(function: () -> Int) {}
            """
        )
        .indented()

    check(source) { file ->
      assertEquals(
        "" +
          "public final class TestKt {\n" +
          "    @org.jetbrains.annotations.NotNull private static final var variable: java.lang.Object = Object()\n" +
          "    public static final fun foo1() : void {\n" +
          // Using plain string literal such that we can have our trailing space
          // here without IntelliJ removing it every time we save this file:
          "        foo2({ \n" +
          "            return variable.hashCode()\n" +
          "        })\n" +
          "    }\n" +
          "    public static final fun foo2(@org.jetbrains.annotations.NotNull function: kotlin.jvm.functions.Function0<java.lang.Integer>) : void {\n" +
          "    }\n" +
          "}",
        file.asSourceString().dos2unix().trim(),
      )
    }
  }

  fun testKt25298() {
    // Regression test for
    // 	KT-25298 UAST: NPE ClsFileImpl.getMirror during lambda inference session
    val source =
      java(
          """
            package test.pkg;
            import java.util.concurrent.Executors;
            import java.util.concurrent.ScheduledExecutorService;
            import java.util.concurrent.TimeUnit;
            public class MyTestCase
            {
                private final ScheduledExecutorService mExecutorService;
                public MyTestCase()
                {
                    mExecutorService = Executors.newSingleThreadScheduledExecutor();
                }
                public void foo()
                {
                    mExecutorService.schedule(this::initBar, 10, TimeUnit.SECONDS);
                }
                private boolean initBar()
                {
                    //...
                    return true;
                }
            }"""
        )
        .indented()

    val expType = if (useFirUast()) " : PsiType:ScheduledFuture<Boolean>" else ""

    check(source) { file ->
      assertEquals(
        "" +
          "UFile (package = test.pkg) [package test.pkg...]\n" +
          "    UImportStatement (isOnDemand = false) [import java.util.concurrent.Executors]\n" +
          "    UImportStatement (isOnDemand = false) [import java.util.concurrent.ScheduledExecutorService]\n" +
          "    UImportStatement (isOnDemand = false) [import java.util.concurrent.TimeUnit]\n" +
          "    UClass (name = MyTestCase) [public class MyTestCase {...}]\n" +
          "        UField (name = mExecutorService) [private final var mExecutorService: java.util.concurrent.ScheduledExecutorService] : PsiType:ScheduledExecutorService\n" +
          "        UMethod (name = MyTestCase) [public fun MyTestCase() {...}]\n" +
          "            UBlockExpression [{...}]\n" +
          "                UBinaryExpression (operator = =) [mExecutorService = Executors.newSingleThreadScheduledExecutor()] : PsiType:ScheduledExecutorService\n" +
          "                    USimpleNameReferenceExpression (identifier = mExecutorService) [mExecutorService] : PsiType:ScheduledExecutorService\n" +
          "                    UQualifiedReferenceExpression [Executors.newSingleThreadScheduledExecutor()] : PsiType:ScheduledExecutorService\n" +
          "                        USimpleNameReferenceExpression (identifier = Executors) [Executors]\n" +
          "                        UCallExpression (kind = UastCallKind(name='method_call'), argCount = 0)) [newSingleThreadScheduledExecutor()] : PsiType:ScheduledExecutorService\n" +
          "                            UIdentifier (Identifier (newSingleThreadScheduledExecutor)) [UIdentifier (Identifier (newSingleThreadScheduledExecutor))]\n" +
          "        UMethod (name = foo) [public fun foo() : void {...}] : PsiType:void\n" +
          "            UBlockExpression [{...}]\n" +
          "                UQualifiedReferenceExpression [mExecutorService.schedule(this::initBar, 10, TimeUnit.SECONDS)]" +
          expType +
          "\n" +
          "                    USimpleNameReferenceExpression (identifier = mExecutorService) [mExecutorService] : PsiType:ScheduledExecutorService\n" +
          "                    UCallExpression (kind = UastCallKind(name='method_call'), argCount = 3)) [schedule(this::initBar, 10, TimeUnit.SECONDS)]" +
          expType +
          "\n" +
          "                        UIdentifier (Identifier (schedule)) [UIdentifier (Identifier (schedule))]\n" +
          "                        UCallableReferenceExpression (name = initBar) [this::initBar] : PsiType:<method reference>\n" +
          "                            UThisExpression (label = null) [this] : PsiType:MyTestCase\n" +
          "                        ULiteralExpression (value = 10) [10] : PsiType:int\n" +
          "                        UQualifiedReferenceExpression [TimeUnit.SECONDS] : PsiType:TimeUnit\n" +
          "                            USimpleNameReferenceExpression (identifier = TimeUnit) [TimeUnit]\n" +
          "                            USimpleNameReferenceExpression (identifier = SECONDS) [SECONDS]\n" +
          "        UMethod (name = initBar) [private fun initBar() : boolean {...}] : PsiType:boolean\n" +
          "            UBlockExpression [{...}]\n" +
          "                UReturnExpression [return true]\n" +
          "                    ULiteralExpression (value = true) [true] : PsiType:boolean\n",
        file.asLogTypes(),
      )
    }
  }

  fun testNodeIsConstructor() {
    // Regression test for
    // 206982645: UMethod#isConstructor returns false on actual constructor
    // https://youtrack.jetbrains.com/issue/KTIJ-20200
    val source =
      kotlin(
          """
            class Test(private val parameter: Int)  {
                @Deprecated(message = "Binary compatibility", level = DeprecationLevel.HIDDEN)
                constructor() : this(42)
            }
          """
        )
        .indented()

    check(source) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitMethod(node: UMethod): Boolean {
            if (node.sourcePsi is KtConstructor<*>) {
              assertTrue("`${node.name}` is not marked as a UAST constructor", node.isConstructor)
            }
            return super.visitMethod(node)
          }
        }
      )
    }
  }

  fun disabledTestThisResolve() {
    // TODO(https://issuetracker.google.com/338553901): Invoking resolve() on a UThisExpression that
    //  references a lambda receiver (and then converting to a UElement) should yield the implicit
    //  UParameter named `<this>` from the appropriate parent lambda expression. However, this does
    //  not work in K1 or K2 when the lambda expression also has an explicit parameter; `this`
    //  instead resolves to the first explicit lambda parameter.
    //  AnalysisApiLintUtilsKt.getThisParameter shows an approach to always correctly get the
    //  implicit parameter named `<this>` from a lambda expression. This approach could probably be
    //  used in the upstream fix.
    val source =
      kotlin(
          """
            fun foo(p: Any.(Any) -> Any) { }

            class Hello {
              fun hello() {
                val a = Any()
                a.apply {
                  this // 0
                }
                a.apply a@ {
                  this // 1
                  this@a // 2
                  this@Hello // 3
                }
                a.let {
                  this // 4
                }
                foo {
                  this // 5
                }
                foo b@ {
                  this // 6
                  this@b // 7
                  this@Hello // 8
                }
                foo { thing ->
                  this // 9
                }
                foo c@ { thing ->
                  this // 10
                  this@c // 11
                  this@Hello // 12
                }
                foo { it ->
                  this // 13
                }
                foo d@ { it ->
                  this // 14
                  this@d // 15
                  this@Hello // 16
                }
              }
            }"""
        )
        .indented()

    check(source) { file ->
      val resolved = mutableListOf<UElement>()
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitThisExpression(node: UThisExpression): Boolean {
            val r = node.resolve()
            resolved.add(r!!.toUElement()!!)
            return super.visitThisExpression(node)
          }
        }
      )

      fun isUParameterNamedThis(element: UElement) =
        element is UParameter && element.name == KotlinExtensionConstants.LAMBDA_THIS_PARAMETER_NAME
      fun isUClassNamedHello(element: UElement) = element is UClass && element.name == "Hello"

      // Each `this` expression from the code above (numbered 0, 1, 2, etc.) should resolve to
      // either the UClass named `Hello` or the UParameter named `<this>` from the parent lambda
      // expression. Expressions like `this@Hello` should resolve to the UClass. Expressions like
      // `this` or `this@d` should resolve to a UParameter.

      assertTrue(isUParameterNamedThis(resolved[0]))
      assertTrue(isUParameterNamedThis(resolved[1]))
      assertTrue(isUParameterNamedThis(resolved[2]))
      assertTrue(isUClassNamedHello(resolved[3]))
      assertTrue(isUClassNamedHello(resolved[4]))
      assertTrue(isUParameterNamedThis(resolved[5]))
      assertTrue(isUParameterNamedThis(resolved[6]))
      assertTrue(isUParameterNamedThis(resolved[7]))
      assertTrue(isUClassNamedHello(resolved[8]))
      assertTrue(isUParameterNamedThis(resolved[9]))
      assertTrue(isUParameterNamedThis(resolved[10]))
      assertTrue(isUParameterNamedThis(resolved[11]))
      assertTrue(isUClassNamedHello(resolved[12]))
      assertTrue(isUParameterNamedThis(resolved[13]))
      assertTrue(isUParameterNamedThis(resolved[14]))
      assertTrue(isUParameterNamedThis(resolved[15]))
      assertTrue(isUClassNamedHello(resolved[16]))
    }
  }

  fun test123923544() {
    // Regression test for
    //  https://youtrack.jetbrains.com/issue/KT-30033
    // 	https://issuetracker.google.com/123923544
    val source =
      kotlin(
          """
            interface Base {
                fun print()
            }

            class BaseImpl(val x: Int) : Base {
                override fun print() { println(x) }
            }

            fun createBase(i: Int): Base {
                return BaseImpl(i)
            }

            class Derived(b: Base) : Base by createBase(10)
            """
        )
        .indented()

    check(source) { file ->
      assertEquals(
        """
                public final class BaseKt {
                    public static final fun createBase(@org.jetbrains.annotations.NotNull i: int) : Base {
                        return BaseImpl(i)
                    }
                }

                public abstract interface Base {
                    public abstract fun print() : void = UastEmptyExpression
                }

                public final class BaseImpl : Base {
                    @org.jetbrains.annotations.NotNull private final var x: int
                    public fun print() : void {
                        println(x)
                    }
                    public final fun getX() : int = UastEmptyExpression
                    public fun BaseImpl(@org.jetbrains.annotations.NotNull x: int) = UastEmptyExpression
                }

                public final class Derived : Base {
                    public fun Derived(@org.jetbrains.annotations.NotNull b: Base) = UastEmptyExpression
                }

                """
          .trimIndent(),
        file.asSourceString().dos2unix(),
      )

      assertEquals(
        """
                UFile (package = ) [public final class BaseKt {...]
                    UClass (name = BaseKt) [public final class BaseKt {...}]
                        UMethod (name = createBase) [public static final fun createBase(@org.jetbrains.annotations.NotNull i: int) : Base {...}] : PsiType:Base
                            UParameter (name = i) [@org.jetbrains.annotations.NotNull var i: int] : PsiType:int
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                            UBlockExpression [{...}] : PsiType:Void
                                UReturnExpression [return BaseImpl(i)] : PsiType:Void
                                    UCallExpression (kind = UastCallKind(name='constructor_call'), argCount = 1)) [BaseImpl(i)] : PsiType:BaseImpl
                                        UIdentifier (Identifier (BaseImpl)) [UIdentifier (Identifier (BaseImpl))]
                                        USimpleNameReferenceExpression (identifier = BaseImpl, resolvesTo = PsiClass: BaseImpl) [BaseImpl]
                                        USimpleNameReferenceExpression (identifier = i) [i] : PsiType:int
                    UClass (name = Base) [public abstract interface Base {...}]
                        UMethod (name = print) [public abstract fun print() : void = UastEmptyExpression] : PsiType:void
                    UClass (name = BaseImpl) [public final class BaseImpl : Base {...}]
                        UField (name = x) [@org.jetbrains.annotations.NotNull private final var x: int] : PsiType:int
                            UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                        UMethod (name = print) [public fun print() : void {...}] : PsiType:void
                            UBlockExpression [{...}] : PsiType:void
                                UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println(x)] : PsiType:Unit
                                    UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]
                                    USimpleNameReferenceExpression (identifier = x) [x] : PsiType:int
                        UMethod (name = getX) [public final fun getX() : int = UastEmptyExpression] : PsiType:int
                        UMethod (name = BaseImpl) [public fun BaseImpl(@org.jetbrains.annotations.NotNull x: int) = UastEmptyExpression]
                            UParameter (name = x) [@org.jetbrains.annotations.NotNull var x: int] : PsiType:int
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                    UClass (name = Derived) [public final class Derived : Base {...}]
                        UExpressionList (super_delegation) [super_delegation Base : createBase(10)]
                            UTypeReferenceExpression (name = Base) [Base]
                            UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [createBase(10)] : PsiType:Base
                                UIdentifier (Identifier (createBase)) [UIdentifier (Identifier (createBase))]
                                ULiteralExpression (value = 10) [10] : PsiType:int
                        UMethod (name = Derived) [public fun Derived(@org.jetbrains.annotations.NotNull b: Base) = UastEmptyExpression]
                            UParameter (name = b) [@org.jetbrains.annotations.NotNull var b: Base] : PsiType:Base
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]

                """
          .trimIndent(),
        file.asLogTypes(),
      )
    }
  }

  fun test13Features() {
    check(
      kotlin(
          """
                package test.pkg

                // Assignment in when
                fun test(s: String) =
                        when (val something = s.hashCode()) {
                            is Int -> println(something)
                            else -> ""
                        }

                interface FooInterface {
                    companion object {
                        @JvmField
                        val answer: Int = 42

                        @JvmStatic
                        fun sayHello() {
                            println("Hello, world!")
                        }
                    }
                }

                // Nested declarations in annotation classes
                annotation class FooAnnotation {
                    enum class Direction { UP, DOWN, LEFT, RIGHT }

                    annotation class Bar

                    companion object {
                        fun foo(): Int = 42
                        val bar: Int = 42
                    }
                }

                // Inline classes
                inline class Name(val s: String)

                @JvmInline
                value class Name2(val n: String)

                // Unsigned
                // You can define unsigned types using literal suffixes
                val uint = 42u
                val ulong = 42uL
                val ubyte: UByte = 255u


                // @JvmDefault
                interface FooInterface2 {
                    // Will be generated as 'default' method
                    @JvmDefault
                    fun foo(): Int = 42
                }
                """
        )
        .indented()
    ) { file ->
      // val ubyte: UByte = 255u
      val uByteValue = if (useFirUast()) 255 else -1
      // K2/SLC puts synthetic members---values, valueOf, and entries---together,
      // followed by enum constructor.
      // It seems K1/ULC puts [entries] at the end in an ad-hoc manner:
      //  values, valueOf, members, constructor, and then entries.
      val enumEntriesBeforeValues =
        if (useFirUast()) ""
        else
          """
                            UMethod (name = Direction) [private fun Direction() = UastEmptyExpression]
                            UMethod (name = getEntries) [public static fun getEntries() : kotlin.enums.EnumEntries<test.pkg.FooAnnotation.Direction> = UastEmptyExpression] : PsiType:EnumEntries<Direction>"""
      val enumEntriesAfterValueOf =
        if (useFirUast())
          """
                            UMethod (name = getEntries) [public static fun getEntries() : kotlin.enums.EnumEntries<test.pkg.FooAnnotation.Direction> = UastEmptyExpression] : PsiType:EnumEntries<Direction>
                            UMethod (name = Direction) [private fun Direction() = UastEmptyExpression]"""
        else ""
      val inlineClassDiff =
        if (useFirUast())
          """
                        UField (name = s) [@org.jetbrains.annotations.NotNull private final var s: java.lang.String] : PsiType:String
                            UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                        UMethod (name = toString) [public fun toString() : java.lang.String = UastEmptyExpression] : PsiType:String
                        UMethod (name = hashCode) [public fun hashCode() : int = UastEmptyExpression] : PsiType:int
                        UMethod (name = equals) [public fun equals(@org.jetbrains.annotations.Nullable other: java.lang.Object) : boolean = UastEmptyExpression] : PsiType:boolean
                            UParameter (name = other) [@org.jetbrains.annotations.Nullable var other: java.lang.Object] : PsiType:Object
                                UAnnotation (fqName = org.jetbrains.annotations.Nullable) [@org.jetbrains.annotations.Nullable]"""
        else ""
      val valueClassDiff =
        if (useFirUast())
          """
                        UMethod (name = toString) [public fun toString() : java.lang.String = UastEmptyExpression] : PsiType:String
                        UMethod (name = hashCode) [public fun hashCode() : int = UastEmptyExpression] : PsiType:int
                        UMethod (name = equals) [public fun equals(@org.jetbrains.annotations.Nullable other: java.lang.Object) : boolean = UastEmptyExpression] : PsiType:boolean
                            UParameter (name = other) [@org.jetbrains.annotations.Nullable var other: java.lang.Object] : PsiType:Object
                                UAnnotation (fqName = org.jetbrains.annotations.Nullable) [@org.jetbrains.annotations.Nullable]"""
        else ""
      val valueClassConstructor =
        if (useFirUast()) ""
        else
          """
                        UMethod (name = Name2) [public fun Name2(@org.jetbrains.annotations.NotNull n: java.lang.String) = UastEmptyExpression]
                            UParameter (name = n) [@org.jetbrains.annotations.NotNull var n: java.lang.String] : PsiType:String
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]"""
      assertEquals(
        """
                UFile (package = test.pkg) [package test.pkg...]
                    UClass (name = FooInterfaceKt) [public final class FooInterfaceKt {...}]
                        UField (name = uint) [@org.jetbrains.annotations.NotNull private static final var uint: int = 42] : PsiType:int
                            UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                            ULiteralExpression (value = 42) [42] : PsiType:int
                        UField (name = ulong) [@org.jetbrains.annotations.NotNull private static final var ulong: long = 42] : PsiType:long
                            UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                            ULiteralExpression (value = 42) [42] : PsiType:long
                        UField (name = ubyte) [@org.jetbrains.annotations.NotNull private static final var ubyte: byte = $uByteValue] : PsiType:byte
                            UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                            ULiteralExpression (value = $uByteValue) [$uByteValue] : PsiType:byte
                        UMethod (name = test) [public static final fun test(@org.jetbrains.annotations.NotNull s: java.lang.String) : java.lang.Object {...}] : PsiType:Object
                            UParameter (name = s) [@org.jetbrains.annotations.NotNull var s: java.lang.String] : PsiType:String
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                            UBlockExpression [{...}]
                                UReturnExpression [return switch (var something: int = s.hashCode())  {...]
                                    USwitchExpression [switch (var something: int = s.hashCode())  {...] : PsiType:Object
                                        UDeclarationsExpression [var something: int = s.hashCode()]
                                            ULocalVariable (name = something) [var something: int = s.hashCode()] : PsiType:int
                                                UQualifiedReferenceExpression [s.hashCode()] : PsiType:int
                                                    USimpleNameReferenceExpression (identifier = s) [s] : PsiType:String
                                                    UCallExpression (kind = UastCallKind(name='method_call'), argCount = 0)) [hashCode()] : PsiType:int
                                                        UIdentifier (Identifier (hashCode)) [UIdentifier (Identifier (hashCode))]
                                        UExpressionList (when) [    it is java.lang.Integer -> {...    ] : PsiType:Object
                                            USwitchClauseExpressionWithBody [it is java.lang.Integer -> {...]
                                                UBinaryExpressionWithType [it is java.lang.Integer]
                                                    USimpleNameReferenceExpression (identifier = it) [it]
                                                    UTypeReferenceExpression (name = java.lang.Integer) [java.lang.Integer]
                                                UExpressionList (when_entry) [{...]
                                                    UYieldExpression [yield println(something)]
                                                        UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println(something)] : PsiType:Unit
                                                            UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]
                                                            USimpleNameReferenceExpression (identifier = something) [something] : PsiType:int
                                            USwitchClauseExpressionWithBody [ -> {...]
                                                UExpressionList (when_entry) [{...]
                                                    UYieldExpression [yield ""]
                                                        UPolyadicExpression (operator = +) [""] : PsiType:String
                                                            ULiteralExpression (value = "") [""] : PsiType:String
                        UMethod (name = getUint) [public static final fun getUint() : int = UastEmptyExpression] : PsiType:int
                        UMethod (name = getUlong) [public static final fun getUlong() : long = UastEmptyExpression] : PsiType:long
                        UMethod (name = getUbyte) [public static final fun getUbyte() : byte = UastEmptyExpression] : PsiType:byte
                    UClass (name = FooInterface) [public abstract interface FooInterface {...}]
                        UField (name = Companion) [@null public static final var Companion: test.pkg.FooInterface.Companion] : PsiType:Companion
                            UAnnotation (fqName = null) [@null]
                        UField (name = answer) [@org.jetbrains.annotations.NotNull @kotlin.jvm.JvmField public static final var answer: int = 42] : PsiType:int
                            UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                            UAnnotation (fqName = kotlin.jvm.JvmField) [@kotlin.jvm.JvmField]
                            ULiteralExpression (value = 42) [42] : PsiType:int
                        UMethod (name = sayHello) [public static fun sayHello() : void {...}] : PsiType:void
                            UBlockExpression [{...}] : PsiType:void
                                UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println("Hello, world!")] : PsiType:Unit
                                    UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]
                                    UPolyadicExpression (operator = +) ["Hello, world!"] : PsiType:String
                                        ULiteralExpression (value = "Hello, world!") ["Hello, world!"] : PsiType:String
                        UClass (name = Companion) [public static final class Companion {...}]
                            UMethod (name = sayHello) [@kotlin.jvm.JvmStatic...}] : PsiType:void
                                UAnnotation (fqName = kotlin.jvm.JvmStatic) [@kotlin.jvm.JvmStatic]
                                UBlockExpression [{...}] : PsiType:void
                                    UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println("Hello, world!")] : PsiType:Unit
                                        UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]
                                        UPolyadicExpression (operator = +) ["Hello, world!"] : PsiType:String
                                            ULiteralExpression (value = "Hello, world!") ["Hello, world!"] : PsiType:String
                            UMethod (name = Companion) [private fun Companion() = UastEmptyExpression]
                    UClass (name = FooAnnotation) [public abstract annotation FooAnnotation {...}]
                        UField (name = Companion) [@null public static final var Companion: test.pkg.FooAnnotation.Companion] : PsiType:Companion
                            UAnnotation (fqName = null) [@null]
                        UClass (name = Direction) [public static final enum Direction {...}]
                            UEnumConstant (name = UP) [@null UP]
                                UAnnotation (fqName = null) [@null]
                                USimpleNameReferenceExpression (identifier = Direction) [Direction]
                            UEnumConstant (name = DOWN) [@null DOWN]
                                UAnnotation (fqName = null) [@null]
                                USimpleNameReferenceExpression (identifier = Direction) [Direction]
                            UEnumConstant (name = LEFT) [@null LEFT]
                                UAnnotation (fqName = null) [@null]
                                USimpleNameReferenceExpression (identifier = Direction) [Direction]
                            UEnumConstant (name = RIGHT) [@null RIGHT]
                                UAnnotation (fqName = null) [@null]
                                USimpleNameReferenceExpression (identifier = Direction) [Direction]$enumEntriesBeforeValues
                            UMethod (name = values) [public static fun values() : test.pkg.FooAnnotation.Direction[] = UastEmptyExpression] : PsiType:Direction[]
                            UMethod (name = valueOf) [public static fun valueOf(value: java.lang.String) : test.pkg.FooAnnotation.Direction = UastEmptyExpression] : PsiType:Direction
                                UParameter (name = value) [var value: java.lang.String] : PsiType:String$enumEntriesAfterValueOf
                        UClass (name = Bar) [public static abstract annotation Bar {...}]
                        UClass (name = Companion) [public static final class Companion {...}]
                            UField (name = bar) [@org.jetbrains.annotations.NotNull private static final var bar: int = 42] : PsiType:int
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                                ULiteralExpression (value = 42) [42] : PsiType:int
                            UMethod (name = foo) [public final fun foo() : int {...}] : PsiType:int
                                UBlockExpression [{...}]
                                    UReturnExpression [return 42]
                                        ULiteralExpression (value = 42) [42] : PsiType:int
                            UMethod (name = getBar) [public final fun getBar() : int = UastEmptyExpression] : PsiType:int
                            UMethod (name = Companion) [private fun Companion() = UastEmptyExpression]
                    UClass (name = Name) [public final class Name {...}]$inlineClassDiff
                        UMethod (name = getS) [public final fun getS() : java.lang.String = UastEmptyExpression] : PsiType:String
                    UClass (name = Name2) [public final class Name2 {...}]
                        UAnnotation (fqName = kotlin.jvm.JvmInline) [@kotlin.jvm.JvmInline]
                        UField (name = n) [@org.jetbrains.annotations.NotNull private final var n: java.lang.String] : PsiType:String
                            UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]$valueClassDiff
                        UMethod (name = getN) [public final fun getN() : java.lang.String = UastEmptyExpression] : PsiType:String$valueClassConstructor
                    UClass (name = FooInterface2) [public abstract interface FooInterface2 {...}]
                        UMethod (name = foo) [@kotlin.jvm.JvmDefault...}] : PsiType:int
                            UAnnotation (fqName = kotlin.jvm.JvmDefault) [@kotlin.jvm.JvmDefault]
                            UBlockExpression [{...}]
                                UReturnExpression [return 42]
                                    ULiteralExpression (value = 42) [42] : PsiType:int

                """
          .trimIndent(),
        file.asLogTypes(),
      )
    }
  }

  fun testSuspend() {
    // Regression test for
    // https://youtrack.jetbrains.com/issue/KT-32031:
    // UAST: Method body missing for suspend functions
    val source =
      kotlin(
          """
            package test.pkg
            import android.widget.TextView
            class Test : android.app.Activity {
                private suspend fun setUi(x: Int, y: Int) {
                    val z = x + y
                }
            }
            """
        )
        .indented()

    check(source) { file ->
      assertEquals(
        """
                package test.pkg

                import android.widget.TextView

                public final class Test : android.app.Activity {
                    private final fun setUi(@org.jetbrains.annotations.NotNull x: int, @org.jetbrains.annotations.NotNull y: int, ${'$'}completion: kotlin.coroutines.Continuation<? super kotlin.Unit>) : java.lang.Object {
                        var z: int = x + y
                    }
                    public fun Test() = UastEmptyExpression
                }

                """
          .trimIndent(),
        file.asSourceString().dos2unix(),
      )

      assertEquals(
        """
                UFile (package = test.pkg) [package test.pkg...]
                    UImportStatement (isOnDemand = false) [import android.widget.TextView]
                    UClass (name = Test) [public final class Test : android.app.Activity {...}]
                        UMethod (name = setUi) [private final fun setUi(@org.jetbrains.annotations.NotNull x: int, @org.jetbrains.annotations.NotNull y: int, ${"$"}completion: kotlin.coroutines.Continuation<? super kotlin.Unit>) : java.lang.Object {...}] : PsiType:Object
                            UParameter (name = x) [@org.jetbrains.annotations.NotNull var x: int] : PsiType:int
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                            UParameter (name = y) [@org.jetbrains.annotations.NotNull var y: int] : PsiType:int
                                UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                            UParameter (name = ${"$"}completion) [var ${"$"}completion: kotlin.coroutines.Continuation<? super kotlin.Unit>] : PsiType:Continuation<? super Unit>
                            UBlockExpression [{...}] : PsiType:void
                                UDeclarationsExpression [var z: int = x + y]
                                    ULocalVariable (name = z) [var z: int = x + y] : PsiType:int
                                        UBinaryExpression (operator = +) [x + y] : PsiType:int
                                            USimpleNameReferenceExpression (identifier = x) [x] : PsiType:int
                                            USimpleNameReferenceExpression (identifier = y) [y] : PsiType:int
                        UMethod (name = Test) [public fun Test() = UastEmptyExpression]

                """
          .trimIndent(),
        file.asLogTypes(),
      )
    }
  }

  fun testReifiedTypes() {
    // Regression test for
    // https://youtrack.jetbrains.com/issue/KT-35610:
    // UAST: Some reified methods have null returnType
    val source =
      kotlin(
          """
            package test.pkg
            inline fun <T> function1(t: T) { }                  // return type void (PsiPrimitiveType)
            inline fun <T> function2(t: T): T = t               // return type T (PsiClassReferenceType)
            inline fun <reified T> function3(t: T) { }          // return type null
            inline fun <reified T> function4(t: T): T = t       // return type null
            inline fun <reified T> function5(t: T): Int = 42    // return type null
            // Other variations that also have a null return type
            inline fun <reified T : Activity> T.function6(t: T): T = t
            inline fun <reified T> function7(t: T): T = t
            private inline fun <reified T> function8(t: T): T = t
            internal inline fun <reified T> function9(t: T): T = t
            public inline fun <reified T> function10(t: T): T = t
            inline fun <reified T> T.function11(t: T): T = t
            fun <reified T> function12(t: T) { }
            """
        )
        .indented()

    // With the upper bound `T : Activity`, T is not null.
    val nonNull = if (useFirUast()) "@${NotNull::class.java.name} " else ""
    check(source) { file ->
      assertEquals(
        """
                package test.pkg

                public final class TestKt {
                    public static final fun function1(t: T) : void {
                    }
                    public static final fun function2(t: T) : T {
                        return t
                    }
                    public static final fun function3(t: T) : void {
                    }
                    public static final fun function4(t: T) : T {
                        return t
                    }
                    public static final fun function5(t: T) : int {
                        return 42
                    }
                    public static final fun function6($nonNull${"$"}this${"$"}function6: T, ${nonNull}t: T) : T {
                        return t
                    }
                    public static final fun function7(t: T) : T {
                        return t
                    }
                    private static final fun function8(t: T) : T {
                        return t
                    }
                    public static final fun function9(t: T) : T {
                        return t
                    }
                    public static final fun function10(t: T) : T {
                        return t
                    }
                    public static final fun function11(${"$"}this${"$"}function11: T, t: T) : T {
                        return t
                    }
                    public static final fun function12(t: T) : void {
                    }
                }

                """
          .trimIndent(),
        file.asSourceString().dos2unix(),
      )
    }
  }

  fun testModifiers() {
    // Regression test for
    // https://youtrack.jetbrains.com/issue/KT-35610:
    // UAST: Some reified methods have null returnType
    val moduleName = if (useFirUast()) "app" else "lint_module"
    val source =
      kotlin(
          """
            @file:Suppress("all")
            package test.pkg
            class Test {
                inline fun <T> function1(t: T) { }                  // return type void (PsiPrimitiveType)
                inline fun <T> function2(t: T): T = t               // return type T (PsiClassReferenceType)
                inline fun <reified T> function3(t: T) { }          // return type null
                inline fun <reified T> function4(t: T): T = t       // return type null
                inline fun <reified T> function5(t: T): Int = 42    // return type null
                // Other variations that also have a null return type
                inline fun <reified T : Activity> T.function6(t: T): T = t
                inline fun <reified T> function7(t: T): T = t
                private inline fun <reified T> function8(t: T): T = t
                internal inline fun <reified T> function9(t: T): T = t
                public inline fun <reified T> functionA(t: T): T = t
                inline fun <reified T> T.functionB(t: T): T = t
                fun <reified T> functionC(t: T) { }

                suspend fun suspendMethod() { }
                infix fun combine(a: Int): Int { return 0 }
                internal fun myInternal() { }
                operator fun get(index: Int): Int = 0
                @JvmField lateinit var delayed: String
                const val constant = 42
                open fun isOpen() { }
                noinline fun notInlined() { }
                tailrec fun me() { me() }
                inline fun f(crossinline body: () -> Unit) {
                    val f = object: Runnable {
                        override fun run() = body()
                    }
                }
                fun multiarg(vararg arg: String) { }

                // Multiplatform stuff
                external fun fromElsewhere()
                actual fun randomUUID() = "not random"
                expect fun randomUUID(): String

                companion object NamedCompanion { }
            }
            sealed class Sealed
            data class Data(val prop: String)

            interface List<out E> : Collection<E>
            interface Comparator<in T> {
                fun compare(e1: T, e2: T): Int = 0
            }
            """
        )
        .indented()

    check(source) { file ->

      // type parameter lookup methods; these would ideally go in JavaEvaluator
      // but can't yet because they're relying on some patches only available
      // in the kotlin-compiler fork (i.e. KotlinLightTypeParameterBuilder).

      fun hasTypeParameterKeyword(
        element: PsiTypeParameter?,
        keyword: KtModifierKeywordToken,
      ): Boolean {
        val ktOrigin =
          when (element) {
            is KotlinLightTypeParameterBuilder -> element.origin
            else -> element?.unwrapped as? KtTypeParameter ?: return false
          }
        return ktOrigin.hasModifier(keyword)
      }

      fun isReified(element: PsiTypeParameter?): Boolean {
        return hasTypeParameterKeyword(element, KtTokens.REIFIED_KEYWORD)
      }

      fun isInVariance(element: PsiTypeParameter?): Boolean {
        return hasTypeParameterKeyword(element, KtTokens.IN_KEYWORD)
      }

      fun isOutVariance(element: PsiTypeParameter?): Boolean {
        return hasTypeParameterKeyword(element, KtTokens.OUT_KEYWORD)
      }

      val evaluator = DefaultJavaEvaluator(null, null)
      val sb = StringBuilder()
      for (cls in file.classes.sortedBy { it.name }) {
        for (declaration in cls.uastDeclarations) {
          if (declaration is UClass) {
            sb.append("nested class ")
            sb.append(declaration.name).append(":")
            if (evaluator.isCompanion(declaration)) {
              sb.append(" companion")
            }
            sb.append("\n")
          }
        }
        sb.append("class ")
        sb.append(cls.name).append(":")
        if (evaluator.isData(cls)) {
          sb.append(" data")
        }
        if (evaluator.isSealed(cls)) {
          sb.append(" sealed")
        }
        if (evaluator.isCompanion(cls)) {
          sb.append(" companion")
        }
        for (typeParameter in cls.typeParameters) {
          sb.append(" ")
          if (isOutVariance(typeParameter)) {
            sb.append("out ")
          }
          if (isInVariance(typeParameter)) {
            sb.append("in ")
          }
          val parameterName = typeParameter.name ?: "arg"
          sb.append(parameterName.replace('$', '＄'))
        }
        sb.append("\n")
        if (evaluator.isData(cls)) {
          continue
        }
        for (method in cls.methods.sortedBy { it.name }) {
          if (method.isConstructor) {
            continue
          }
          val methodName = method.name.replace('$', '＄')
          sb.append("    method ").append(methodName)
          sb.append("(")
          var first = true
          for (parameter in method.uastParameters) {
            if (first) {
              first = false
            } else {
              sb.append(",")
            }
            if (evaluator.isCrossInline(parameter)) {
              sb.append("crossinline ")
            }
            if (evaluator.isVararg(parameter)) {
              sb.append("vararg ")
            }
            sb.append(parameter.name.replace('$', '＄'))
          }
          sb.append(")")
          sb.append(":")
          if (evaluator.isInline(method)) {
            sb.append(" inline")
          }
          if (evaluator.isNoInline(method)) {
            sb.append(" noinline")
          }
          if (evaluator.isTailRec(method)) {
            sb.append(" tailrec")
          }
          if (evaluator.isSuspend(method)) {
            sb.append(" suspend")
          }
          if (evaluator.isInfix(method)) {
            sb.append(" infix")
          }
          if (evaluator.isInternal(method)) {
            sb.append(" internal")
          }
          if (evaluator.isOperator(method)) {
            sb.append(" operator")
          }
          if (evaluator.isOpen(method)) {
            sb.append(" open")
          }
          if (evaluator.isExpect(method)) {
            sb.append(" expect")
          }
          if (evaluator.isActual(method)) {
            sb.append(" actual")
          }
          if (evaluator.isExternal(method)) {
            sb.append(" external")
          }
          first = true
          for (typeParam in method.typeParameters) {
            if (first) {
              first = false
            } else {
              sb.append(",")
            }

            if (isReified(typeParam)) {
              sb.append(" reified")
            }
            if (isOutVariance(typeParam)) {
              sb.append(" out")
            }
            if (isInVariance(typeParam)) {
              sb.append(" in")
            }
            sb.append(" ")
            sb.append(typeParam.name)
          }

          sb.append("\n")
        }
        for (method in cls.fields.sortedBy { it.name }) {
          sb.append("    field ").append(method.name).append(":")
          if (evaluator.isLateInit(method)) {
            sb.append(" lateinit")
          }
          if (evaluator.isConst(method)) {
            sb.append(" const")
          }
          sb.append("\n")
        }
      }

      // function1 and function2 do not have reified types;
      // the rest do
      assertEquals(
        """
                class Comparator: in T
                    method compare(e1,e2):
                class Data: data
                class List: out E
                class Sealed: sealed
                nested class NamedCompanion: companion
                class Test:
                    method combine(a): infix
                    method f(crossinline body): inline
                    method fromElsewhere(): external
                    method function1(t): inline T
                    method function2(t): inline T
                    method function3(t): inline reified T
                    method function4(t): inline reified T
                    method function5(t): inline reified T
                    method function6(＄this＄function6,t): inline reified T
                    method function7(t): inline reified T
                    method function8(t): inline reified T
                    method function9(t): inline internal reified T
                    method functionA(t): inline reified T
                    method functionB(＄this＄functionB,t): inline reified T
                    method functionC(t): reified T
                    method get(index): operator
                    method isOpen(): open
                    method me(): tailrec
                    method multiarg(vararg arg):
                    method myInternal＄$moduleName(): internal
                    method notInlined(): noinline
                    method randomUUID(): actual
                    method randomUUID(): expect
                    method suspendMethod(＄completion): suspend
                    field NamedCompanion:
                    field constant: const
                    field delayed: lateinit
                """
          .trimIndent()
          .trim(),
        sb.toString().trim(),
      )
    }
  }

  fun testKtParameters() {
    // Regression test for
    // https://issuetracker.google.com/134093981
    val source =
      kotlin(
          """
            package test.pkg
            inline class GraphVariables(val set: MutableSet<GraphVariable<*>>) {
                fun <T> variable(name: String, graphType: String, value: T) {
                    this.set.add(GraphVariable(name, graphType, value))
                }
            }
            class GraphVariable<T>(name: String, graphType: String, value: T) {
            }
            """
        )
        .indented()

    val inlineClassDiff =
      if (useFirUast())
        """
                    @org.jetbrains.annotations.NotNull private final var set: java.util.Set<test.pkg.GraphVariable<?>>
                    public fun toString() : java.lang.String = UastEmptyExpression
                    public fun hashCode() : int = UastEmptyExpression
                    public fun equals(@org.jetbrains.annotations.Nullable other: java.lang.Object) : boolean = UastEmptyExpression"""
      else ""
    check(source) { file ->
      assertEquals(
        """
                package test.pkg

                public final class GraphVariables {$inlineClassDiff
                    public final fun getSet() : java.util.Set<test.pkg.GraphVariable<?>> = UastEmptyExpression
                    public final fun variable(@org.jetbrains.annotations.NotNull name: java.lang.String, @org.jetbrains.annotations.NotNull graphType: java.lang.String, value: T) : void {
                        this.set.add(GraphVariable(name, graphType, value))
                    }
                }

                public final class GraphVariable {
                    public fun GraphVariable(@org.jetbrains.annotations.NotNull name: java.lang.String, @org.jetbrains.annotations.NotNull graphType: java.lang.String, value: T) = UastEmptyExpression
                }

                """
          .trimIndent(),
        file.asSourceString().dos2unix(),
      )
    }
  }

  fun testCatchClausesKotlin() {
    // Regression test for
    // https://issuetracker.google.com/140154274
    // and https://youtrack.jetbrains.com/issue/KT-35804
    val source =
      kotlin(
          """
            package test.pkg

            class TryCatchKotlin {
                @java.lang.SuppressWarnings("Something")
                fun catches() {
                    try {
                        catches()
                    } catch (@java.lang.SuppressWarnings("Something") e: Throwable) {
                    }
                }

                fun throws() {
                }
            }
            """
        )
        .indented()

    check(source) { file ->
      assertEquals(
        """
                package test.pkg

                public final class TryCatchKotlin {
                    @java.lang.SuppressWarnings(value = "Something")
                    public final fun catches() : void {
                        try {
                            catches()
                        }
                        catch (@org.jetbrains.annotations.NotNull @java.lang.SuppressWarnings(value = "Something") var e: java.lang.Throwable) {
                        }
                    }
                    public final fun throws() : void {
                    }
                    public fun TryCatchKotlin() = UastEmptyExpression
                }
                """
          .trimIndent()
          .trim(),
        file.asSourceString().dos2unix().trim().replace("\n        \n", "\n"),
      )
    }

    // Java is OK:
    val javaSource =
      java(
          """
            public class TryCatchJava {
                @SuppressWarnings("Something")
                public void test() {
                    try {
                        canThrow();
                    } catch(@SuppressWarnings("Something") Throwable t) {
                    }
                }
                public void canThrow() {
                }
            }
            """
        )
        .indented()

    check(javaSource) { file ->
      assertEquals(
        // The annotations work in Java, as checked by
        // ApiDetectorTest#testConditionalAroundExceptionSuppress
        // However, in pretty printing catch clause parameters are not
        // visited, as described in https://youtrack.jetbrains.com/issue/KT-35803
        """
                public class TryCatchJava {
                    @java.lang.SuppressWarnings(null = "Something")
                    public fun test() : void {
                        try {
                            canThrow()
                        }
                        catch (@java.lang.SuppressWarnings(null = "Something") var t: java.lang.Throwable) {
                        }
                    }
                    public fun canThrow() : void {
                    }
                }
                """
          .trimIndent()
          .trim(),
        file.asSourceString().dos2unix().trim().replace("\n        \n", "\n"),
      )
    }
  }

  fun testSamAst() { // See KT-28272
    val source =
      kotlin(
          """
            //@file:Suppress("RedundantSamConstructor", "MoveLambdaOutsideParentheses", "unused", "UNUSED_VARIABLE")

            package test.pkg

            fun test1() {
                val thread1 = Thread({ println("hello") })
            }

            fun test2() {
                val thread2 = Thread(Runnable { println("hello") })
            }
            """
        )
        .indented()

    // [Unit] as a function return type should be mapped to void.
    // Otherwise, e.g., lambda return, can be still [Unit].
    val lambdaBlockReturnType = if (useFirUast()) " : PsiType:Unit" else ""

    check(source) { file ->
      assertEquals(
        """
                UFile (package = test.pkg) [package test.pkg...]
                  UClass (name = TestKt) [public final class TestKt {...}]
                    UMethod (name = test1) [public static final fun test1() : void {...}] : PsiType:void
                      UBlockExpression [{...}] : PsiType:void
                        UDeclarationsExpression [var thread1: java.lang.Thread = Thread({ ...})]
                          ULocalVariable (name = thread1) [var thread1: java.lang.Thread = Thread({ ...})] : PsiType:Thread
                            UCallExpression (kind = UastCallKind(name='constructor_call'), argCount = 1)) [Thread({ ...})] : PsiType:Thread
                              UIdentifier (Identifier (Thread)) [UIdentifier (Identifier (Thread))]
                              USimpleNameReferenceExpression (identifier = Thread, resolvesTo = PsiClass: Thread) [Thread]
                              ULambdaExpression [{ ...}] : PsiType:Function0<? extends Unit>
                                UBlockExpression [{...}]$lambdaBlockReturnType
                                  UReturnExpression [return println("hello")]
                                    UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println("hello")] : PsiType:Unit
                                      UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]
                                      UPolyadicExpression (operator = +) ["hello"] : PsiType:String
                                        ULiteralExpression (value = "hello") ["hello"] : PsiType:String
                    UMethod (name = test2) [public static final fun test2() : void {...}] : PsiType:void
                      UBlockExpression [{...}] : PsiType:void
                        UDeclarationsExpression [var thread2: java.lang.Thread = Thread(Runnable({ ...}))]
                          ULocalVariable (name = thread2) [var thread2: java.lang.Thread = Thread(Runnable({ ...}))] : PsiType:Thread
                            UCallExpression (kind = UastCallKind(name='constructor_call'), argCount = 1)) [Thread(Runnable({ ...}))] : PsiType:Thread
                              UIdentifier (Identifier (Thread)) [UIdentifier (Identifier (Thread))]
                              USimpleNameReferenceExpression (identifier = Thread, resolvesTo = PsiClass: Thread) [Thread]
                              UCallExpression (kind = UastCallKind(name='constructor_call'), argCount = 1)) [Runnable({ ...})] : PsiType:Runnable
                                UIdentifier (Identifier (Runnable)) [UIdentifier (Identifier (Runnable))]
                                USimpleNameReferenceExpression (identifier = Runnable, resolvesTo = PsiClass: Runnable) [Runnable]
                                ULambdaExpression [{ ...}] : PsiType:Function0<? extends Unit>
                                  UBlockExpression [{...}]$lambdaBlockReturnType
                                    UReturnExpression [return println("hello")]
                                      UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println("hello")] : PsiType:Unit
                                        UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]
                                        UPolyadicExpression (operator = +) ["hello"] : PsiType:String
                                          ULiteralExpression (value = "hello") ["hello"] : PsiType:String
                """
          .trimIndent(),
        file.asLogTypes(indent = "  ").trim(),
      )

      try {
        file.accept(
          object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
              val resolved = node.resolve()
              if (resolved == null) {
                throw IllegalStateException("Could not resolve this call: ${node.asSourceString()}")
              }
              return super.visitCallExpression(node)
            }
          }
        )
        fail("Expected unresolved error: see KT-28272")
      } catch (failure: IllegalStateException) {
        assertEquals(
          "Could not resolve this call: Runnable { println(\"hello\") }",
          failure.message,
        )
      }
    }
  }

  fun testJava11() {
    val source =
      java(
          """
            package test.pkg;
            import java.util.function.IntFunction;
            public class Java11Test {
                // Private methods in interfaces
                public interface MyInterface {
                    private String getHello() {
                        return "hello";
                    }
                    default String getMessage() {
                        return getHello();
                    }
                }
                // Var keyword
                public void varStuff() {
                    var name = "Name";
                    var meaning = 42;
                    for (var line : name.split("\n")) {
                        System.out.println(line);
                    }
                }
                // Lambda vars
                IntFunction<Integer> doubler = (var x) -> x * 2;
            }
            """
        )
        .indented()

    check(
      source,
      javaLanguageLevel = LanguageLevel.JDK_11,
      android = false,
      check = { file ->
        assertEquals(
          """
                    UFile (package = test.pkg) [package test.pkg...]
                      UImportStatement (isOnDemand = false) [import java.util.function.IntFunction]
                      UClass (name = Java11Test) [public class Java11Test {...}]
                        UField (name = doubler) [var doubler: java.util.function.IntFunction<java.lang.Integer> = { var x: int ->...}] : PsiType:IntFunction<Integer>
                          ULambdaExpression [{ var x: int ->...}] : PsiType:<lambda expression>
                            UParameter (name = x) [var x: int] : PsiType:int
                            UBlockExpression [{...}]
                              UReturnExpression [return x * 2]
                                UBinaryExpression (operator = *) [x * 2] : PsiType:int
                                  USimpleNameReferenceExpression (identifier = x) [x] : PsiType:int
                                  ULiteralExpression (value = 2) [2] : PsiType:int
                        UMethod (name = varStuff) [public fun varStuff() : void {...}] : PsiType:void
                          UBlockExpression [{...}]
                            UDeclarationsExpression [var name: java.lang.String = "Name"]
                              ULocalVariable (name = name) [var name: java.lang.String = "Name"] : PsiType:String
                                ULiteralExpression (value = "Name") ["Name"] : PsiType:String
                            UDeclarationsExpression [var meaning: int = 42]
                              ULocalVariable (name = meaning) [var meaning: int = 42] : PsiType:int
                                ULiteralExpression (value = 42) [42] : PsiType:int
                            UForEachExpression [for (line : name.split("\n")) {...}]
                              UQualifiedReferenceExpression [name.split("\n")] : PsiType:String[]
                                USimpleNameReferenceExpression (identifier = name) [name] : PsiType:String
                                UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [split("\n")] : PsiType:String[]
                                  UIdentifier (Identifier (split)) [UIdentifier (Identifier (split))]
                                  ULiteralExpression (value = "\n") ["\n"] : PsiType:String
                              UBlockExpression [{...}]
                                UQualifiedReferenceExpression [System.out.println(line)] : PsiType:void
                                  UQualifiedReferenceExpression [System.out] : PsiType:PrintStream
                                    USimpleNameReferenceExpression (identifier = System) [System]
                                    USimpleNameReferenceExpression (identifier = out) [out]
                                  UCallExpression (kind = UastCallKind(name='method_call'), argCount = 1)) [println(line)] : PsiType:void
                                    UIdentifier (Identifier (println)) [UIdentifier (Identifier (println))]
                                    USimpleNameReferenceExpression (identifier = line) [line] : PsiType:String
                        UClass (name = MyInterface) [public static abstract interface MyInterface {...}]
                          UMethod (name = getHello) [private fun getHello() : java.lang.String {...}] : PsiType:String
                            UBlockExpression [{...}]
                              UReturnExpression [return "hello"]
                                ULiteralExpression (value = "hello") ["hello"] : PsiType:String
                          UMethod (name = getMessage) [public default fun getMessage() : java.lang.String {...}] : PsiType:String
                            UBlockExpression [{...}]
                              UReturnExpression [return getHello()]
                                UCallExpression (kind = UastCallKind(name='method_call'), argCount = 0)) [getHello()] : PsiType:String
                                  UIdentifier (Identifier (getHello)) [UIdentifier (Identifier (getHello))]
                    """
            .trimIndent(),
          file.asLogTypes(indent = "  ").trim(),
        )

        // Make sure that all calls correctly resolve
        file.accept(
          object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
              val resolved = node.resolve()
              assertNotNull(resolved)
              return super.visitCallExpression(node)
            }
          }
        )
      },
    )
  }

  fun test125138962() {
    // Regression test for https://issuetracker.google.com/125138962
    val source =
      kotlin(
          """
            package test.pkg

            class SimpleClass() {
                var foo: Int
                init {
                    @Suppress("foo")
                    foo = android.R.layout.activity_list_item
                }
            }
            """
        )
        .indented()

    check(
      source,
      check = { file ->
        assertEquals(
          """
                package test.pkg

                public final class SimpleClass {
                    @org.jetbrains.annotations.NotNull private var foo: int
                    public final fun getFoo() : int = UastEmptyExpression
                    public final fun setFoo(<set-?>: int) : void = UastEmptyExpression
                    public fun SimpleClass() {
                        {
                            foo = android.R.layout.activity_list_item
                        }
                    }
                }
                    """
            .trimIndent(),
          file.asSourceString().dos2unix().trim(),
        )
      },
    )
  }

  fun testIdea234484() {
    // Regression test for https://youtrack.jetbrains.com/issue/KT-37200
    val source =
      kotlin(
          """
            package test.pkg

            inline fun <reified F> ViewModelContext.viewModelFactory(): F {
                return activity as? F ?: throw IllegalStateException("Boo!")
            }

            sealed class ViewModelContext {
                abstract val activity: Number
            }
            """
        )
        .indented()

    check(
      source,
      check = { file ->
        val newFile = file.sourcePsi.toUElement()
        newFile?.accept(
          object : AbstractUastVisitor() {
            override fun visitLocalVariable(node: ULocalVariable): Boolean {
              val initializerType = node.uastInitializer?.getExpressionType()
              val interfaceType = node.type
              @Suppress("UNUSED_VARIABLE")
              val equals = initializerType == interfaceType // Stack overflow!

              return super.visitLocalVariable(node)
            }
          }
        )
      },
    )
  }

  fun testKt27935() {
    // Regression test for https://youtrack.jetbrains.com/issue/KT-27935
    val source =
      kotlin(
          """
            package test.pkg

            typealias IndexedDistance = Pair<Int, Double>
            typealias Window = (Array<IndexedDistance>) -> List<IndexedDistance>
            class Core(
                    val window: Window
            )
            """
        )
        .indented()

    check(source, check = {})
  }

  fun testKt36275() {
    // Regression test for https://youtrack.jetbrains.com/issue/KT-36275
    val source =
      kotlin(
          """
            package test.pkg

             fun foo() {
                fun bar() {
                }
                bar()
            }
            """
        )
        .indented()

    check(
      source,
      check = { file ->
        // Make sure that all calls correctly resolve
        file.accept(
          object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
              val resolved = node.resolve()
              assertNotNull(resolved)
              return super.visitCallExpression(node)
            }
          }
        )
      },
    )
  }

  fun testKt34187() {
    // Regression test for https://youtrack.jetbrains.com/issue/KT-34187
    val source =
      kotlin(
          """
            package test.pkg

            class Publisher<T> { }

            fun usedAsArrayElement() {
                val a = arrayOfNulls<Publisher<String>>(10)
                a[0] = Publisher()
            }
            """
        )
        .indented()

    check(
      source,
      check = { file ->
        // Make sure that all calls correctly resolve
        file.accept(
          object : AbstractUastVisitor() {
            override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
              if (node.isAssignment()) {
                val type = node.leftOperand.getExpressionType()
                assertNotNull("type of ${node.leftOperand.sourcePsi?.text} is null", type)
              }
              return super.visitBinaryExpression(node)
            }
          }
        )
      },
    )
  }

  fun testKt45676() {
    // Regression test for https://youtrack.jetbrains.com/issue/KT-45676,
    // in which backing field annotations were missing their attribute values.
    val source =
      kotlin(
          """
            @Target(AnnotationTarget.FIELD)
            annotation class MyFieldAnnotation(val value: String)

            @MyFieldAnnotation("SomeStringValue")
            var myProperty = 0
            """
        )
        .indented()

    check(source) { file ->
      assertEquals(
        """
                UFile (package = ) [public final class MyFieldAnnotationKt {...]
                  UClass (name = MyFieldAnnotationKt) [public final class MyFieldAnnotationKt {...}]
                    UField (name = myProperty) [@org.jetbrains.annotations.NotNull @MyFieldAnnotation(value = "SomeStringValue") private static var myProperty: int = 0] : PsiType:int
                      UAnnotation (fqName = org.jetbrains.annotations.NotNull) [@org.jetbrains.annotations.NotNull]
                      UAnnotation (fqName = MyFieldAnnotation) [@MyFieldAnnotation(value = "SomeStringValue")]
                        UNamedExpression (name = value) [value = "SomeStringValue"]
                          UPolyadicExpression (operator = +) ["SomeStringValue"] : PsiType:String
                            ULiteralExpression (value = "SomeStringValue") ["SomeStringValue"] : PsiType:String
                      ULiteralExpression (value = 0) [0] : PsiType:int
                    UMethod (name = getMyProperty) [public static final fun getMyProperty() : int = UastEmptyExpression] : PsiType:int
                    UMethod (name = setMyProperty) [public static final fun setMyProperty(<set-?>: int) : void = UastEmptyExpression] : PsiType:void
                      UParameter (name = <set-?>) [var <set-?>: int] : PsiType:int
                  UClass (name = MyFieldAnnotation) [public abstract annotation MyFieldAnnotation {...}]
                    UAnnotation (fqName = kotlin.annotation.Target) [@kotlin.annotation.Target(allowedTargets = AnnotationTarget.FIELD)]
                      UNamedExpression (name = allowedTargets) [allowedTargets = AnnotationTarget.FIELD]
                        UQualifiedReferenceExpression [AnnotationTarget.FIELD] : PsiType:AnnotationTarget
                          USimpleNameReferenceExpression (identifier = AnnotationTarget) [AnnotationTarget]
                          USimpleNameReferenceExpression (identifier = FIELD) [FIELD] : PsiType:AnnotationTarget
                    UAnnotationMethod (name = value) [public abstract fun value() : java.lang.String = UastEmptyExpression] : PsiType:String
                """
          .trimIndent(),
        file.asLogTypes(indent = "  ").trim(),
      )
    }
  }

  fun testResolveLambdaVar() { // See KT-46628
    val source =
      kotlin(
          """
            package test.pkg

            fun test1(s: String?) {
                s?.let {
                    println(it)
                }
            }

            fun test2(s: String?) {
                s?.let { it ->
                    println(it)
                }
            }

            fun test3(s: String?) {
                s?.let { t ->
                    println(t)
                }
            }
            """
        )
        .indented()

    check(source) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            val argument = node.valueArguments.firstOrNull()
            (argument as? UReferenceExpression)?.let {
              val resolved = argument.resolve()
              assertNotNull(
                "Couldn't resolve `${argument.sourcePsi?.text ?: argument.asSourceString()}`",
                resolved,
              )
            }

            return super.visitCallExpression(node)
          }
        }
      )
    }
  }

  fun testSamConstructorCallKind() {
    val source =
      kotlin(
          """
            val r = java.lang.Runnable {  }
            """
        )
        .indented()

    check(source) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            assertEquals("Runnable", node.methodName)
            assertEquals(UastCallKind.CONSTRUCTOR_CALL, node.kind)

            return super.visitCallExpression(node)
          }
        }
      )
    }
  }

  fun testCommentOnDataClass() {
    val source =
      kotlin(
          """
            // Single-line comment on data class
            data class DataClass(val id: String)
            """
        )
        .indented()

    check(source) { file ->
      val commentMap: MutableMap<String, MutableSet<UElement>> = mutableMapOf()
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitElement(node: UElement): Boolean {
            node.comments.forEach {
              val boundUElement = commentMap.computeIfAbsent(it.text) { mutableSetOf() }
              boundUElement.add(node)
            }
            return super.visitElement(node)
          }
        }
      )
      assertTrue(commentMap.keys.isNotEmpty())
      commentMap.forEach { (_, uElementSet) -> assertEquals(1, uElementSet.size) }
    }
  }

  fun testTextOfModifierListOfFunction() {
    val source =
      kotlin(
          """
            annotation class MyComposable
            class Test {
                @MyComposable
                fun foo() {
                }
            }
            """
        )
        .indented()

    check(source) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitMethod(node: UMethod): Boolean {
            if (node.isConstructor) return super.visitMethod(node)

            val javaPsiModifierList = node.modifierList
            assertTrue(javaPsiModifierList.textOffset > 0)
            assertFalse(javaPsiModifierList.textRange.isEmpty)
            assertEquals(javaPsiModifierList.textOffset, javaPsiModifierList.textRange.startOffset)

            val sourceModifierList = (node.sourcePsi as? KtModifierListOwner)?.modifierList
            assertNotNull(sourceModifierList)
            sourceModifierList!!
            assertTrue(sourceModifierList.textOffset > 0)
            assertFalse(sourceModifierList.textRange.isEmpty)
            assertEquals(sourceModifierList.textOffset, sourceModifierList.textRange.startOffset)

            assertEquals(sourceModifierList.text, javaPsiModifierList.text)
            assertEquals("@MyComposable", sourceModifierList.text)
            return super.visitMethod(node)
          }
        }
      )
    }
  }

  fun testTextOfModifierListOfPropertyAccessor() {
    val source =
      kotlin(
          """
            annotation class MyComposable
            object Test {
                var foo3: Boolean
                    @MyComposable
                    get() = LocalFoo.current

                class LocalFoo {
                  companion object {
                      const val current: Boolean = true
                  }
                }
            }
            """
        )
        .indented()

    check(source) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitMethod(node: UMethod): Boolean {
            if (node.sourcePsi !is KtPropertyAccessor) return super.visitMethod(node)

            val javaPsiModifierList = node.modifierList
            assertTrue(javaPsiModifierList.textOffset > 0)
            assertFalse(javaPsiModifierList.textRange.isEmpty)
            assertEquals(javaPsiModifierList.textOffset, javaPsiModifierList.textRange.startOffset)

            val sourceModifierList = (node.sourcePsi as? KtModifierListOwner)?.modifierList
            assertNotNull(sourceModifierList)
            sourceModifierList!!
            assertTrue(sourceModifierList.textOffset > 0)
            assertFalse(sourceModifierList.textRange.isEmpty)
            assertEquals(sourceModifierList.textOffset, sourceModifierList.textRange.startOffset)

            assertEquals(sourceModifierList.text, javaPsiModifierList.text)
            assertEquals("@MyComposable", sourceModifierList.text)
            return super.visitMethod(node)
          }
        }
      )
    }
  }

  fun testConstructorDelegationType() {
    // https://youtrack.jetbrains.com/issue/KTIJ-31633
    val source =
      kotlin(
          """
          class Constructors {
            class ThisCall {
              constructor() // (1)
              constructor(i: Int) : this() // (2)
            }
            open class SuperCallToImplicit {
              class C : SuperCallToImplicit {
                constructor() : super() // (3)
              }
            }
            open class SuperCallToExplicit {
              constructor() // (4)
              class C : SuperCallToExplicit {
                constructor() : super() // (5)
              }
            }
          }
        """
        )
        .indented()
    var count = 0
    check(source) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            val t = node.getExpressionType()
            assertNull(node.sourcePsi?.text ?: "<null source PSI>", t)
            count++
            return super.visitCallExpression(node)
          }
        }
      )
    }
    assertEquals(5, count)
  }

  fun testConstructorReferences() {
    val source =
      kotlin(
          """
            class Foo(val p : Int)
            class Boo
            data class Bar(val isEnabled: Boolean = true)

            fun test() {
              val x = ::Foo
              x(42)
              val y = ::Boo
              y()
              val z = ::Bar
              z(false)
            }
            """
        )
        .indented()

    check(source) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitMethod(node: UMethod): Boolean {
            if (!node.isConstructor) return super.visitMethod(node)

            // Bar should have default arg constructor as per default value in parameter
            // E.g., Bar() // == Bar(true)
            // K2 creates that constructor properly, whereas K1 missed that.
            val expectedTypes = if (useFirUast()) listOf("Boo", "Bar") else listOf("Boo")
            assertTrue(
              node.sourcePsi is KtConstructor<*> ||
                (node.sourcePsi is KtClassOrObject &&
                  node.parameterList.isEmpty &&
                  node.name in expectedTypes)
            )
            return super.visitMethod(node)
          }

          override fun visitCallableReferenceExpression(
            node: UCallableReferenceExpression
          ): Boolean {
            val resolved = node.resolve()
            assertNotNull(resolved)

            // If a class doesn't have its own primary constructor,
            // the reference will be resolved to the class itself.
            assertTrue(
              (resolved as? PsiMethod)?.isConstructor == true ||
                (resolved as? PsiClass)?.constructors?.single()?.isPhysical == false
            )

            return super.visitCallableReferenceExpression(node)
          }
        }
      )
    }
  }

  fun test263887242() {
    val source =
      kotlin(
          """
            inline fun <T> remember(calc: () -> T): T = calc()

            fun test() {
                val x = remember { UnknownClass() }
                val y = remember { 42 }
                val z = remember {
                    val local = UnknownClass()
                    42
                }
            }
            """
        )
        .indented()

    check(source) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            if (node.methodName != "remember") return super.visitCallExpression(node)

            // Due to coercion-to-Unit (in FE1.0), a type error is hidden, and Unit is returned.
            // In contrast, in K2, we can see the unsubstituted type parameter.
            // The point is that `RememberDetector` should not rely on `Unit` as the call type,
            // and should rather manually investigate the last expression of the lambda argument.
            val callExpressionType = node.getExpressionType()
            val coerced = if (useFirUast()) "T" else "kotlin.Unit"
            assertTrue(
              node.sourcePsi?.text + " returns " + callExpressionType?.canonicalText,
              callExpressionType?.canonicalText in listOf(coerced, "int"),
            )

            // We can go deeper into the last expression of the lambda argument.
            val sourcePsi = node.sourcePsi
            if (sourcePsi is KtCallExpression) {
              val tailLambda = sourcePsi.valueArguments.lastOrNull() as? KtLambdaArgument
              val lambda = tailLambda?.getLambdaExpression()
              val lastExp = lambda?.bodyExpression?.statements?.lastOrNull()
              val lastExpType =
                lastExp?.let { it.toUElementOfType<UExpression>()?.getExpressionType() }
              // Since unresolved, the expression type will be actually `null`.
              val isReallyUnit =
                callExpressionType?.canonicalText == "kotlin.Unit" &&
                  callExpressionType == lastExpType
              assertFalse(isReallyUnit)
            }

            return super.visitCallExpression(node)
          }
        }
      )
    }
  }

  fun testKT59564() {
    // Regression test from KT-59564
    val source =
      kotlin(
          """
            fun test(a: Int, b: Int) {
                for (i in a..<b step 1) {
                    println(i)
                }
            }
            """
        )
        .indented()

    check(source) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitForEachExpression(node: UForEachExpression): Boolean {
            when (val exp = node.iteratedValue.skipParenthesizedExprDown()) {
              is UBinaryExpression -> {
                assertEquals("kotlin.ranges.IntProgression", exp.getExpressionType()?.canonicalText)
                assertEquals(
                  "kotlin.ranges.IntRange",
                  exp.leftOperand.getExpressionType()?.canonicalText,
                )
              }
            }

            return super.visitForEachExpression(node)
          }
        }
      )
    }
  }

  fun testFindAnnotationOnObjectFunWithJvmStatic() {
    // Regression test from b/296891200
    val source =
      kotlin(
          """
            object ObjectModule {
              @JvmStatic
              fun provideFoo(): String {
                return "Foo"
              }
            }
        """
        )
        .indented()

    val jvmStatic = "kotlin.jvm.JvmStatic"

    check(source) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitMethod(node: UMethod): Boolean {
            if (node.isConstructor) return super.visitMethod(node)

            // https://youtrack.jetbrains.com/issue/KTIJ-26803
            assertFalse(node.hasAnnotation(jvmStatic))
            assertNull(node.findAnnotation(jvmStatic))
            // Workaround to retrieve @JvmStatic
            val findAnnotation =
              node.findAnnotation(jvmStatic)?.javaPsi
                ?: node.javaPsi.modifierList.findAnnotation(jvmStatic)
            assertNotNull(findAnnotation)
            assertEquals(jvmStatic, findAnnotation!!.qualifiedName)

            return super.visitMethod(node)
          }
        }
      )
    }
  }

  fun testInheritedMethodsInJava() {
    // from b/296638723
    val source =
      java(
        """
          class Parent {
            void foo() {}
          }

          class Test extends Parent {
            void callNoQualifier() {
              foo();
            }

            void callWithThis() {
              this.foo();
            }

            void callWithSuper() {
              super.foo();
            }
          }
        """
      )

    var count = 0
    check(source) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            val callee = node.resolve()
            assertNotNull(callee)
            val containingClass = callee!!.containingClass
            assertNotNull(containingClass)

            val id = "${containingClass!!.name}#${callee.name}"
            assertEquals("Parent#foo", id)

            val uMethod = callee.toUElement(UMethod::class.java)
            assertNotNull(uMethod)
            assertEquals(callee, uMethod!!.javaPsi)

            count++

            return super.visitCallExpression(node)
          }
        }
      )
    }
    assertEquals(3, count)
  }

  fun testImplicitLambdaParameterInTest() {
    // Example from b/302708854
    val testFiles =
      arrayOf(
        kotlin(
            "src/test/pkg/sub/MyClass.kt",
            """
            package pkg.sub

            class MyClass {
              lateinit var manager: Manager
                private set

              init {
                Manager().use {
                  manager = it
                }
              }
            }
          """,
          )
          .indented(),
        kotlin(
            "src/main/pkg/sub/Manager.kt",
            """
            package pkg.sub
            import java.io.Closeable
            class Manager : Closeable
          """,
          )
          .indented(),
      )

    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitSimpleNameReferenceExpression(
            node: USimpleNameReferenceExpression
          ): Boolean {
            if (node.identifier != "it") return super.visitSimpleNameReferenceExpression(node)

            // No source for implicit lambda parameter.
            // Expect to be resolved to fake PsiParameter used inside ULambdaExpression
            val resolved = node.resolve() as? PsiParameter
            assertEquals("it", resolved?.name)

            return super.visitSimpleNameReferenceExpression(node)
          }
        }
      )
    }
  }

  @OptIn(KaExperimentalApi::class)
  fun testFunInterfaceTypeForLambdaWithLabels() {
    // https://youtrack.jetbrains.com/issue/KT-69453
    // Regression test from b/347626696
    val source =
      kotlin(
          """
          package test.pkg

          import java.io.Closeable

          internal class CloseMe() : Closeable {
            override fun close() {
            }
          }

          internal fun makeCloseMe(): CloseMe = CloseMe()

          fun interface ResourceFactory {
            fun getResource(): Closeable
          }

          fun consumeCloseable(factory: ResourceFactory) = factory.getResource().use {  }

          fun testReturnToImplicitLambdaLabel() {
            consumeCloseable {
              return@consumeCloseable makeCloseMe()
            }
          }

          fun testReturnToExplicitLambdaLabel() {
            consumeCloseable label@{
              return@label makeCloseMe()
            }
          }
        """
        )
        .indented()

    check(source) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitReturnExpression(node: UReturnExpression): Boolean {
            // Skip implicit return
            if (node.sourcePsi == null) {
              return super.visitReturnExpression(node)
            }
            val type =
              when (val jumpTarget = node.jumpTarget) {
                is ULambdaExpression -> {
                  getFunctionalInterfaceType(jumpTarget.sourcePsi as KtExpression, jumpTarget)
                }
                is ULabeledExpression -> {
                  val lambda = jumpTarget.expression as ULambdaExpression
                  getFunctionalInterfaceType(lambda.sourcePsi as KtExpression, lambda)
                }
                else -> null
              }
            assertEquals("test.pkg.ResourceFactory", type?.canonicalText)
            return super.visitReturnExpression(node)
          }

          // Copied from google3 utils
          private fun getFunctionalInterfaceType(
            ktExpression: KtExpression,
            source: UExpression,
          ): PsiClassType? {
            return analyze(ktExpression) {
              val samType = getSamType(ktExpression) ?: return null
              val psiTypeParent =
                source.getParentOfType(UDeclaration::class.java, strict = false)?.javaPsi
                  as? PsiModifierListOwner ?: ktExpression
              try {
                samType.asPsiType(psiTypeParent, allowErrorTypes = true) as? PsiClassType
              } catch (_: IllegalArgumentException) {
                // E.g., kotlin/Array<out ft<kotlin/Any, kotlin/Any?>>?>
                // non-simple array argument
                null
              }
            }
          }

          // Copied from google3 utils
          private fun KaSession.getSamType(ktExpression: KtExpression): KaType? {
            // E.g. `FunInterface(::method)` or `call(..., ::method, ...)`
            return ktExpression.expectedType
              ?.takeIf { it !is KaClassErrorType && it.isFunctionalInterface }
              ?.lowerBoundIfFlexible()
          }
        }
      )
    }
  }

  fun testResolveToInlineInLibrary() {
    val testFiles =
      arrayOf(
        bytecode(
          "libs/lib1.jar",
          kotlin(
            "src/test/Mocking.kt",
            """
                    package test

                    inline fun <reified T : Any> mock(): T = TODO()

                    object Mock {
                      inline fun <reified T : Any> mock(): T = TODO()
                    }
                    """,
          ),
          0x9c8bcf60,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijg4uViEOL0rXTOSSwu9i7hEuRiKUkt
                LgEK5SdnZ+ale5coMWgxAAD10MNROgAAAA==
                """,
          """
                test/Mock.class:
                H4sIAAAAAAAA/2VRS08TURT+7u1rOi22IEIpPqFqi8oAcSWVBAHDmFIT25AQ
                VrftUG87nTEzt43Lrvgh/gPigkQSbXDnjzKemVZ8sDnv853znfPj55evAJ5j
                jSGpLF8ZB26zmwBjyHbEQBi2cNrG20bHaqoEIgzxsnSk2mKIFEuHacQQ1xFF
                giGq3kufIVW5QtmkYI80w2yxVPkfjbKL5fqL6/GtYqlep2y+6ypbOkbVVWbv
                g231LEdZrT3Pc70EbjDsFv/qrSlPOu1NszJp6gx6hqR6zxG2sWudiL6tdlzH
                V16/qVzvQHhdy9scM8jqyGCa+Bdk4aQw3piZDFq5aYdkdfCAoWZWa/Xt6s5e
                GvPQkxTMMSxXXK9tdCzV8IR0fEM4jquEkjQr2Lzat23iMv17rwNLiZZQgmK8
                N4jQ6VkgkoEAjaXR/KMMPHoIb60TzdEwrfMc13l2NNS5FtW+n/LcaLjB19ir
                hMYvP8V5lr/JZyN5vh9d0rXRMBvNsRVK71+eamE6FmBtsHBCnUEPvkPnWu0q
                esK7vqNkzzKdgfRlw7a2/zCgB+64LYshU5GOVe33GpZXF1TDMFNxm8I+FJ4M
                /EkwWZNtR6i+R7Zec/te03otg8TCZMjhtRFYpztGQ/ILwVlJl8iLk86TjlA2
                Fnor5BkBAdKxlXNoZ2RwPJkUB/IpyfS4AEmCQnYaKYrwsHmbopz0VKR8gcwR
                i7JzzHz7B4TeHILMkcWp9SZmQ7ipMRyehdVT0HDraqf5EBRIXYAfnWPuMxbO
                wgDHaiiLtDbwksoXaYPbx4iYuGPirol7uE8mHphYwvIxmI8CHh4j7kP38cgP
                jJSPxz7SvwCvcXQwpwMAAA==
                """,
          """
                test/MockingKt.class:
                H4sIAAAAAAAA/2VSXWsTQRQ9d5PmY5u2aa3apH7WCIkPbiuCYEpBWqWLSQUT
                ApKnSTINk+zOyuxs8DFP/hD/hKCgoY/+KPFujCD6MHfOvefcw53L/Pj55RuA
                p3hI2LQytl47Gk6VHr+2eRChPBEz4QVCj703g4kccjVDyIYsIuzWG61/+SZh
                /7j7/P/6Sb3R7TJbnUY2UNq7iKwfvg9kKLWVo5fGRCaPAiF3rLSyJ4Sz+l8m
                HWt4qKbfWnVPZqGnuNFoEXhn8lIkgT2NdGxNMrSRaQszlabZ6JXgYt1FESVC
                saZql7Xfo5NP2P5j1pZWjIQVPJ0TzjK8D0pDMQ1g7TQFDpMfVIoOGY2OCN5i
                7rpOwXGdMqPFvFrlu+qc04FbWMzLtEePnEPn/Opj4epTziln0rYnbNel1NVd
                Lfrx1PJCT6ORJGy1lJYXSTiQpisGAVd2WtFQBD1hVJqvisWOGmthE8PY7USJ
                GcpXKiUqbxNtVSh7KlasfKF1ZIVVvBccwUEWyweVK1hDjvO7nD1jzPNgI9P8
                iuI7ytJnbHxP34t7HHNMpo33GZeWeB2b2OLsYKnJ83mwRHdQW9rxn2HL7T4y
                PnZ8XPOxi+s+buCmjz1U+qAYVez3kY2xFuNWjNsxcr8Ai+me74gCAAA=
                """,
        ),
        kotlin(
          """
                import test.Mock
                import test.mock as tMock

                class MyClass

                fun test(): Boolean {
                  val instance1 = Mock.mock<MyClass>()
                  val instance2 = tMock<MyClass>()
                  return instance1 == instance2
                }
                """
        ),
      )

    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          var first: Boolean = true

          override fun visitCallExpression(node: UCallExpression): Boolean {
            val resolved = node.resolve()
            assertNotNull(resolved)
            assertEquals("mock", resolved!!.name)
            if (first) {
              assertEquals("Mock", resolved.containingClass?.name)
              assertFalse(resolved.hasModifier(JvmModifier.STATIC))
              first = false
            } else {
              assertEquals("MockingKt", resolved.containingClass?.name)
              assertTrue(resolved.hasModifier(JvmModifier.STATIC))
            }

            assertEquals(1, resolved.typeParameters.size)
            val typeParam = resolved.typeParameters.single()
            assertEquals("T", typeParam.name)

            return super.visitCallExpression(node)
          }
        }
      )
    }
  }

  fun testAbstractDelegateToInterface() {
    val testFiles =
      arrayOf(
        kotlin(
          "src/main/impl/DynamicFeatureExtension.kt",
          """
            package main.impl

            abstract class DynamicFeatureExtension(
                private val publicExtensionImpl: DynamicFeatureExtensionImpl
            ) : InternalDynamicFeatureExtension by publicExtensionImpl {
                // fun sandbox(action: Action<Sandbox>) // delegate
            }
          """,
        ),
        bytecode(
          "libs/lib1.jar",
          kotlin(
              """
               package pkg.api

               interface Action<T> {
                 fun execute(t: T)
               }
            """
            )
            .indented(),
          0x2326f350,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJOBihQYtBiAAD1Iry9GAAAAA==
                """,
          """
                pkg/api/Action.class:
                H4sIAAAAAAAA/2VQzU7CQBic3UJbqmJRUEDPBj1YJB6MGiMxMWIwJkK4cFpq
                JUtLS+iWcOyz+BgeTOPRhzJuqxflMt/M5Pv//Hp7B3CKfYLizB1bbMatti14
                4GsgBI3L/nl3whbM8pg/th5HE8cWF1erFoH539OQI9CcpWNHwiGoNFarDgcE
                aqPfz0ip6wbC47714Aj2zASTTel0ocj9SAqFFEBAXOkveaqakj2fENwkcdmg
                VWoksUFNCVmkBtWp/lJN4qOcnsQmadEmva+YSp02cy3VzNfpWRLfHXy8qtRU
                01YteUb37xvkEqRPsrmCoPBjHrsp7/Gxz0Q0l8cZvSCa284t96SoPUW+4FNn
                wEM+8py27weCpWVhOgR5ZHfI56iQT0ZNKgodyi9TUM9iFXsyXsuMgqwxhlA6
                WOtgvYMNFCXFZgcmSkOQEFvYHkILUQ5RCbETQs1wN4T2DWW/IWXjAQAA
                """,
        ),
        bytecode(
          "libs/lib2.jar",
          kotlin(
              "src/my/SandBox.kt",
              """
              package my

              interface Sandbox
            """,
            )
            .indented(),
          0x3dae47f7,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJOBihQYtBiAAD1Iry9GAAAAA==
                """,
          """
                my/Sandbox.class:
                H4sIAAAAAAAA/0WNQUvDQBCF32y0adeqqVqof8K0xZunehACFUHBS06bZpVt
                kl0w2xJv/V0epGd/lDirB2fgzXsz8M3X98cngGuMCbJ5T5+ULQvXxSBCslZb
                ldbKvqYPxVqvfIyIMFpWztfGpvfaq1J5dUMQzTZiCgUZBAGBKt53JqQpu3JG
                GO93fSkmQoqE3ctkv5uLKYXjnDBc/r9nJBNkSLeuu6p8CG7zttJ3ptaEy8eN
                9abRz6Y1Ra0X1jqvvHG27TENB/grgfNfPcMFzxkjD7l7OaIMcYZ+hgEkWxxl
                GOI4B7U4wWkO0SJpMfoBSLYswx0BAAA=
                """,
        ),
        // To reproduce b/314320270, remove this dependency such that
        // `DynamicFeatureExtension` as a super type of `InternalDynamicFeatureExtension`
        // would be another one in the same package, resulting in circular super type.
        bytecode(
          "libs/api.jar",
          kotlin(
              "src/main/api/DynamicFeatureExtension.kt",
              """
              package main.api

              import my.Sandbox

              interface DynamicFeatureExtension {
                val sandbox: Sandbox
                fun sandbox(action: Sandbox.() -> Unit)
              }
            """,
            )
            .indented(),
          0x7e98aaf4,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJOBihQYtBiAAD1Iry9GAAAAA==
                """,
          """
                main/api/DynamicFeatureExtension.class:
                H4sIAAAAAAAA/4VRUW8SQRD+doG746z1StVSqi1tjWkf9CjxrUZjokQMVlO0
                MeFpgS054PYadiHwxm/xwR/hgyF99EcZ567QWo1pspmd+fabb2Znfv76/gPA
                M+wwFEMRKF+cBf7riRJh0KpIYYYD+WZspNJBpGwwBq8rRsLvC9XxPzS7smVs
                pBjcjjR1odrNaMywvLdfCyf+PD5k2K1Fg47flaY5oBLaF0pFRhiS1P5RZI6G
                /T6xbL0Q2N2r9SLTp266o9A/HarWBbcy9w4O908YXt7Eev7kzzYW5M8qMIcv
                EoWVBfZeGtEWRlAXPBylaCIsNtnYgIH1CB8HcVQir33AEMymRZfnucu92dSl
                k/hOHPPLMM+d0/xsWuYl9m7b4wVessprnl1YzaVzvJRKrFVyzr9a3Mm+fXyc
                SzipL+ff0oRZhbST9jJxwTLDTu2m9VDz1OvS9dFbIpkFQ27x16shMmwuwEuR
                xeunyZkkQuE/tZ72DMPG8VCZIJRVNQp00OzLV1d7ZcjWg45Kshge/c38KAYi
                lEYOrqW49Wg4aMlK0Kec9XnOyT/aFo0E6Xg14GmGDCz690OK4tsGCHOQJc5m
                zICLLbptdvFIQDGxD7BNd5XQWySx1ECqittVLFdxBx65WKkih9UGmMZd3GvA
                1bivsabhaOQ1MhpWEq5rFDQ2fgNSA6T+SwMAAA==
                """,
        ),
        kotlin(
          "src/main/test/InternalTestExtension.kt",
          """
            package main.test

            interface InternalTestExtension
          """,
        ),
        kotlin(
          "src/main/impl/InternalDynamicFeatureExtension.kt",
          """
            package main.impl

            import pkg.api.Action
            import my.Sandbox
            import main.api.DynamicFeatureExtension
            import main.test.InternalTestExtension

            interface InternalDynamicFeatureExtension : DynamicFeatureExtension, InternalTestExtension {
              fun sandbox(action: Action<Sandbox>)
            }
          """,
        ),
        kotlin(
          "src/main/impl/DynamicFeatureExtensionImpl.kt",
          """
            package main.impl

            abstract class DynamicFeatureExtensionImpl : InternalDynamicFeatureExtension {
              // fun sandbox(action: Action<Sandbox>) // abstract
            }
          """,
        ),
      )
    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitClass(node: UClass): Boolean {
            val delegate = node.javaPsi.methods.find { it.name == "sandbox" }

            // If `DynamicFeatureExtension` in api package is missing,
            // circular super type will bother the compiler frontend.
            // That is, this delegation won't exist: change to assertNull.
            // However, K1 gracefully ignored internal errors, whereas
            // K2 raised ISE that hid the true root cause.
            assertNotNull(delegate)

            return super.visitClass(node)
          }
        }
      )
    }
  }

  fun testRetrievingPsiOfLocalFun() {
    val source =
      kotlin(
          """
          fun target(i: Int) {
            fun localFun() {
              println("hello")
            }
            localFun()
          }
        """
        )
        .indented()

    check(source) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            val resolved = node.resolve() ?: return super.visitCallExpression(node)
            if (resolved.name == "println") {
              // Ugh... not this one.
              return super.visitCallExpression(node)
            }
            val sourcePsi = node.sourcePsi as? KtElement ?: return super.visitCallExpression(node)
            analyze(sourcePsi) {
              // from AnalysisApiLintUtils.kt
              // val functionSymbol = getFunctionLikeSymbol(sourcePsi)
              val callInfo = sourcePsi.resolveToCall() ?: return super.visitCallExpression(node)
              val functionSymbol =
                callInfo.singleFunctionCallOrNull()?.symbol
                  ?: return super.visitCallExpression(node)
              val psi = functionSymbol.psi
              assertEquals("fun localFun() {\n    println(\"hello\")\n  }", psi?.text)
            }

            return super.visitCallExpression(node)
          }
        }
      )
    }
  }

  fun testFunctionalInterfaceTypeForInterfaceWithoutFun() {
    // Regression test from b/325123657
    val testFiles =
      arrayOf(
        kotlin(
          """
            package test.pkg

            class Test {
              fun f(): A = {}
              fun g(): B = {}
            }
          """
        ),
        kotlin(
          """
            package test.pkg

            interface A {
              fun f()
            }
          """
        ),
        kotlin(
          """
            package test.pkg

            fun interface B {
              fun g()
            }
          """
        ),
      )

    // function name -> (K1, K2)
    val expectedTypes =
      mapOf(
        "f" to ("test.pkg.A" to "kotlin.jvm.functions.Function0<? extends kotlin.Unit>"),
        "g" to ("test.pkg.B" to "test.pkg.B"),
      )

    var count = 0
    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          var method: UMethod? = null

          override fun visitMethod(node: UMethod): Boolean {
            method = node
            return super.visitMethod(node)
          }

          override fun afterVisitMethod(node: UMethod) {
            method = null
            super.afterVisitMethod(node)
          }

          override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
            count++
            val type = node.functionalInterfaceType ?: node.getExpressionType()
            val expectedType =
              expectedTypes[method!!.name]?.let { if (useFirUast()) it.second else it.first }
            assertEquals(expectedType, type?.canonicalText)
            return super.visitLambdaExpression(node)
          }
        }
      )
    }
    assertEquals(2, count)
  }

  fun testIncorrectImplicitReturnInLambda() {
    val testFiles =
      arrayOf(
        kotlin(
            """
          import android.content.Context
          import android.widget.Toast
          //import kotlinx.coroutines.CompletableDeferred
          import my.coroutines.CompletableDeferred
          //import kotlinx.coroutines.async
          import my.coroutines.async
          //import kotlinx.coroutines.coroutineScope
          import my.coroutines.coroutineScope
          //import org.junit.Assert.assertThrows
          import my.junit.Assert.assertThrows
          //import org.junit.function.ThrowingRunnable
          import my.junit.function.ThrowingRunnable

          fun test(c: Context, r: Int, d: Int) {
            with(Toast.makeText(c, r, d)) { show() } // 1 // Unit

            // Returning context object
            Toast.makeText(c, r, d).also { println("it") }.show() // 2 // Unit
            Toast.makeText(c, r, d).apply { println("it") }.show() // 3 // Unit
            // Returning lambda result
            with("hello") { Toast.makeText(c, r, d) }.show() // 4 // Toast
            "hello".let { Toast.makeText(c, r, d) }.show() // 5 // Toast
            "hello".run { Toast.makeText(c, r, d) }.show() // 6 // Toast
          }

          fun testContextReturns(c: Context, r: Int, d: Int) {
            val toast1 = Toast.makeText(c, r, d)
            toast1.apply {
              Toast.makeText(c, r, d) // 7 // Unit
            }.show()

            Toast.makeText(c, r, d).also {
              Toast.makeText(c, r, d) // 8 // Unit
            }.show()
          }

          fun testThrowing() {
            assertThrows<RuntimeException>(RuntimeException::class.java) {
              CompletableDeferred<Any?>("later/assertThrows") // 9 // void
            }
            assertThrows<RuntimeException>(
              RuntimeException::class.java,
              ThrowingRunnable {
                CompletableDeferred<Any?>("later/ThrowingRunnable") // 10 // void
              }
            )
          }

          suspend fun testLaunchSuggestionWithUnusedAsync(): Unit = coroutineScope { // 11 // Object, suspend lambda
            async { "Deferred value" } // 12 // Object, suspend lambda
          }
        """
          )
          .indented(),
        kotlin(
            "my/coroutines/CompletableDeferred.kt",
            """
            package my.coroutines
            interface CompletableDeferred<T> : Deferred<T> {}
          """,
          )
          .indented(),
        kotlin(
            "my/coroutines/Deferred.kt",
            """
            package my.coroutines
            interface Deferred<T> {}
          """,
          )
          .indented(),
        kotlin(
            "my/coroutines/CoroutineScope.kt",
            """
            package my.coroutines
            interface CoroutineScope {}
            suspend fun <R> coroutineScope(block: suspend CoroutineScope.() -> R): R = TODO()
            fun <T> CoroutineScope.async(block: suspend CoroutineScope.() -> T): Deferred<T> = TODO()
          """,
          )
          .indented(),
        java(
            "my/junit/function/ThrowingRunnable.java",
            """
              package my.junit.function;

              public interface ThrowingRunnable {
                void run() throws Throwable;
              }
            """,
          )
          .indented(),
        java(
            "my/junit/Assert.java",
            """
            package my.junit;

            import my.junit.function.ThrowingRunnable;

            public class Assert {
              public static <T extends Throwable> T assertThrows(Class<T> expectedThrowable, ThrowingRunnable runnable) {
                throw new AssertionError();
              }
            }
          """,
          )
          .indented(),
      )

    val expectedReturnValues =
      listOf(
        true, // 1 // Unit
        true, // 2 // Unit
        true, // 3 // Unit
        false, // 4 // Toast
        false, // 5 // Toast
        false, // 6 // Toast
        true, // 7 // Unit
        true, // 8 // Unit
        true, // 9 // void
        true, // 10 // void
        true, // 11 // Object, suspend lambda
        true, // 12 // Object, suspend lambda
      )

    val expectedCount = expectedReturnValues.size
    check(*testFiles) { file ->
      var lambdaCount = 0
      var returnCount = 0
      file.accept(
        object : AbstractUastVisitor() {
          var lambdaType: PsiType? = null

          override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
            lambdaCount++
            lambdaType = node.functionalInterfaceType ?: node.getExpressionType()
            return super.visitLambdaExpression(node)
          }

          override fun afterVisitLambdaExpression(node: ULambdaExpression) {
            lambdaType = null
            super.afterVisitLambdaExpression(node)
          }

          override fun visitReturnExpression(node: UReturnExpression): Boolean {
            // Skip an implicit return for body expression, e.g.,
            //   suspend fun test...(): Unit = ...
            if (
              node.uastParent !is UBlockExpression ||
                node.uastParent?.uastParent !is ULambdaExpression
            )
              return super.visitReturnExpression(node)

            assertEquals(
              "Comparison[${returnCount+1}]: ${node.returnExpression?.sourcePsi?.text}",
              expectedReturnValues[returnCount],
              node.isIncorrectImplicitReturnInLambda(),
            )
            returnCount++
            return super.visitReturnExpression(node)
          }
        }
      )
      assertEquals(expectedCount, lambdaCount)
      assertEquals(lambdaCount, returnCount)
    }
  }

  fun testReferenceQualifierType() {
    val testFiles =
      arrayOf(
        kotlin(
            """
            import my.math.IntMath

            interface MyInterface

            fun test1() = MyInterface::class
            fun test2(x: MyInterface) = x::class
            fun test3() = Int::class
            fun test4() = Number::toInt
            fun test5(a: Number) = a::toInt
            fun test6() = IntMath::factorial
          """
          )
          .indented(),
        java(
            """
            package my.math;
            public final class IntMath {
              public static int factorial(int n) {
                return 42;
              }
            }
          """
          )
          .indented(),
      )

    val expectedTypes =
      mapOf(
        "test1" to "MyInterface",
        "test2" to "MyInterface",
        "test3" to "java.lang.Integer",
        "test4" to "java.lang.Number",
        "test5" to "java.lang.Number",
        "test6" to "my.math.IntMath",
      )

    var count = 0
    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          var currentMethod: String? = null

          override fun visitMethod(node: UMethod): Boolean {
            currentMethod = node.name
            return super.visitMethod(node)
          }

          override fun afterVisitMethod(node: UMethod) {
            currentMethod = null
            super.afterVisitMethod(node)
          }

          override fun visitClassLiteralExpression(node: UClassLiteralExpression): Boolean {
            count++
            assertEquals(expectedTypes[currentMethod], node.type?.canonicalText)
            return super.visitClassLiteralExpression(node)
          }

          override fun visitCallableReferenceExpression(
            node: UCallableReferenceExpression
          ): Boolean {
            count++
            assertEquals(expectedTypes[currentMethod], node.qualifierType?.canonicalText)
            return super.visitCallableReferenceExpression(node)
          }
        }
      )
    }
    assertEquals(expectedTypes.size, count)
  }

  fun testReferenceQualifierTypeForDispatchers() {
    // Regression test from b/325107804
    val testFiles =
      arrayOf(
        kotlin(
            """
        package test.pkg

        //import kotlinx.coroutines.Dispatchers
        import my.coroutines.Dispatchers

        fun example() {
          Dispatchers.IO
          Dispatchers.Default
          Dispatchers.Unconfined
          Dispatchers.Main
          Dispatchers::IO
          Dispatchers::Default
          Dispatchers::Unconfined
          Dispatchers::Main
        }
      """
          )
          .indented(),
        kotlin(
            """
            package my.coroutines

            object Dispatchers {
              val IO = "IO"
              val Default = "Default"
              val Unconfined = "Unconfined"
              val Main = "Main"
            }
          """
          )
          .indented(),
      )
    var count = 0
    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitQualifiedReferenceExpression(
            node: UQualifiedReferenceExpression
          ): Boolean {
            count++
            val expressionType =
              node.receiver.getExpressionType() as? PsiClassType
                ?: return super.visitQualifiedReferenceExpression(node)
            assertEquals("my.coroutines.Dispatchers", expressionType.resolve()?.qualifiedName)
            return super.visitQualifiedReferenceExpression(node)
          }

          override fun visitCallableReferenceExpression(
            node: UCallableReferenceExpression
          ): Boolean {
            count++
            assertEquals("my.coroutines.Dispatchers", node.qualifierType?.canonicalText)
            return super.visitCallableReferenceExpression(node)
          }
        }
      )
    }
    assertEquals(8, count)
  }

  fun testResolutionToFunWithValueClass() {
    // Regression test from b/324087645
    val testFiles =
      arrayOf(
        kotlin(
            """
              package com.example.myapp

              import com.example.graphics.Color
              import com.example.unit.ColorProvider

              fun test() {
                val color = ColorProvider(color = Color.Blue)
              }
          """
          )
          .indented(),
        bytecode(
          "libs/lib1.jar",
          kotlin(
              """
            package com.example.graphics

              @JvmInline
              value class Color(val value: Int) {
                companion object {
                  val Blue = Color(42)
                }
              }
          """
            )
            .indented(),
          0xce342cff,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijgkuYSTM7P1UutSMwtyEnVy61MLCgQ
                YgtJLS7xLuGS5RJAlizNyywR4nTOzytJrQBKKzFoMQAAHqJ9pVQAAAA=
                """,
          """
                com/example/graphics/Color＄Companion.class:
                H4sIAAAAAAAA/5VSzU8TURD/vbfbdlkqLR8irYiiFQGFLcSLwZhIjUmTogma
                GsPBPLYPWLofzb5XwrHx4P+hZy+cJB5MU2/+UcbZdiXGRKOHnY/fvN/M7Mx8
                +/75C4D7WGOouFHgyFMRdHzpHMaic+S5yqlFfhRXalHQEaEXhTkwhuKxOBGO
                L8JD5/n+sXR1DgZD9qEXevoRg7G80swjg6wNEzkGUx95imGp8S8FthgKh1Jv
                +125dvDqwenrZnWYsc5Q/jM/h0sMk8J1pVKVlF5xO3kUkLcxgSLDxnKjHWnf
                C53jk8DxQi3jUPjOE3kgur6uRaHScdfVUbwj4raMt1aaNnjS/jQN5iL4JhhG
                Gdb/Lxt195OwI7VoCS0I48GJQeNniRhLBBhYm/BTL/Hoz3lrg6HV783YfI7b
                vNjv2dziI8cyrME7Y67f2+RVtp2z+OBDlhf57nzRKPOq+fWc9XskGH1JyCZ6
                rmxamWJ28JYXiF+yM9bg/UKVkb2Y1NpkSQdmMj2G+b/si3pntJGxi7UxWMPA
                eltTglrUogSFhhfKZ91gX8Yvxb5PyFQjcoXfFLGX+CmYr4ehjGu+UErSmdgv
                om7syqdeEivtdkPtBbLpKY8ePw7DSAtN9RQ2aDtmMjIYZNG1Ued3yHOSGZLO
                rH6CdUYGxzLJ7AjECsl8ao/BJj2J8SGSkNdTsnmOyY+/cbO/cM0Rl84qg6mU
                u0aap4Wnz4a7TAizIzAtllgzuEwxA6vk2UPSBG6jhLvDgku4R7pG+Cy9vbIH
                o465Okp1lHGVTMzXcQ0Le2AK13FjD5aCrbCokFW4qXBLYVwhr1D5AUXv1Mbd
                AwAA
                """,
          """
                com/example/graphics/Color.class:
                H4sIAAAAAAAA/31V3VMbVRT/3c3XZrPQJaWUBLRf2IaPNhRrrdJiS2rt0kAV
                Kkrx6xK2YWGzG3c3mT4yvuhf4IMvOr740gdrFZh2xsH2zb/JcTx3d0kw0M5k
                9tx77vn4nd859+bvf5/+CeASHIZ8xakVjYe8VreMYtXl9TWz4hVLjuW4KTAG
                bZ03edHidrV4d2XdqPgpxBjkquEvcqthMMQKwzpDohnumK4iBTkNCWmGuL9m
                egyD5ZcnmWTo8p0F3zXt6nmTDBiOFfThcjtteEZ2xzt10w3TWjUI5xGG5FXT
                Nv2pAM+iih5kFWg4yqAGaQoBvmsyjpEpr9cNe5XhfOFgmoOZoyyTKo6jXwTN
                UUWHQdxvOCAMB4Vh6dWGrwvDE8ToHgkMvYVDyldxCqeF7RmilbvVcRVd6FaI
                57PE4Br31krOqhExGCd41JSedhTd9o2qoGqEUu1ZqxjDsIJRnFdRECsJRYaM
                8XWDW14Uqq+glztHYHL4PoPSsFech4GVijeRFN6XaBAcf81wGbIHvYj5MLRo
                8WFBVVzEhIjzbljCooK4aKFWcWzPdxsV33EjWPJeboYTohevGDAxDFdF1Bs0
                nk0aiH31jRPggq6LeqT6RfGZINp4pWJ43hDN+DQNzVClTnDESoUejvYMAbha
                saKJG3p58qGSU6tz23TsFGYZLhbKG45PfsX1Zq1oUk9cm1vFm8YD3rD8UrvK
                We5u0ICEk3xXwRw+ZEi3gjGcfUXF7aRU+zwWBOJ7KqbCDi8ynCk7brW4bvgr
                Ljdtr8ht2/G5Tw5ecc7x5xqWRc3q2491plnTbdoYdNCzdzBr+HyV+5x0Uq0Z
                oyeFiU9afEBkb5D+oSl2xLO0Svw+3d08rUj9kiJpu5sK/SRNViQ5QTJDMkmy
                m2RMfv7t9f7dzZNHJ6Rx9i47Op3NJjUpL43HXuyw3c3nPyfjclxLzOQ1mZTp
                CVlT8vF+Ns5uv/g+FpxmNHVG07qEC+lYoOsmD007QjqtpevRsvM9YWjay4Qp
                H5eTWur5d0wKc30jxQlSTlRAw0F1yQHLFzZ8hoH5hu2bNUO3m6ZnrljGjTaV
                NDTikjEcKRNvc43aiuHe42QjLodT4dYid02xj5RdCz6vbMzyerRXdds23JLF
                Pc+gYMqC03Arxi1TnOWivIsHstIVkujWADHkRPMJb5V2SZKfksyK15lkXowy
                yW6ar0Rwuka7ougaycTI71Ae0UKCGTmDgq7TVw0NkKEVtVo8QpHz22QtznLP
                oC1tozfbt4V8fguvacNbOLmFoV+DqWgHyeGNAAMTT1sU5GyEQBYIdnCu00du
                JaYHK/I5s4c6v4MLjzocEq0kY60yO5KMd/q0k9B7FPncperEJOdH/4L0AxKx
                R6O7kLbw1rX84I9iGw/52qBvClL6H3SHIftIKfgOYYjVZaJKALiCd6LgomfC
                Ki0Aje5gso0odE9HiMQqcNck8apF7lORuzKyjWsjA39AeXxo78JYSiuWEgwD
                PSzibYhinYy4kfKdrEjh6Gg5vIfrkfU54kScpZ9BWspvY7qzX2mUAqce8RfR
                2a+9KWOHTFYON/F+R32Z/MBPSMV/QTzWJjtBZF/fz1UGtyKqM/hA1EeIb7eS
                DwZZiJHfcCfM3WYpRtZluiKh5VjQb6D3GeaW2DY+eoKPtZEdfPIEdx7/zzND
                3xisgFFGb7VEFy+HWgDxAWySG7RaInmf4C4vI6bjMx2f6/gCX9ISX+ngWFkG
                81DB6jJ6PageDA+p4DvlYdhDwkPSw5VAc5nuuYcJD2MeCh5OBcouD90e5v8D
                n7znY94JAAA=
                """,
        ),
        bytecode(
          "libs/lib2.jar",
          kotlin(
              """
            package com.example.unit

              import com.example.graphics.Color

              interface Context

              interface ColorProvider {
                fun getColor(context: Context): Color
              }

              fun ColorProvider(color: Color): ColorProvider {
                  return FixedColorProvider(color)
              }

              fun ColorProvider(resId: Int): ColorProvider {
                  return ResourceColorProvider(resId)
              }

              data class FixedColorProvider(val color: Color) : ColorProvider {
                  override fun getColor(context: Context) = color
              }

              data class ResourceColorProvider(val resId: Int) : ColorProvider {
                  override fun getColor(context: Context): Color = Color(resId)
              }
          """
            )
            .indented(),
          0x94484c75,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijgkuYSTM7P1UutSMwtyEnVy61MLCgQ
                YgtJLS7xLuGS5RJAlizNyywR4nTOzytJrQBKKzFoMQAAHqJ9pVQAAAA=
                """,
          """
                com/example/unit/ColorProvider.class:
                H4sIAAAAAAAA/3VQTW/TQBB9s0ls13zU5TMJbdUKhOAATlNuPaFKCKNQKpC4
                5LR2lrCJY0feTZRjxD+BX4E4oCjc+FGIcRJEkYq0O/Nm9N7uzPv569t3AM+w
                R9hP8lGoZnI0TlU4ybQNT/M0L86LfKp7qnBBhGAgpzJMZdYP38QDlVgXFe72
                lV1xn8THY3V8ZAh7jzqXPJdZNbMnjyPC/U5e9MOBsnEhdWZCmWW5lVbnjM9y
                ezZJ0xPCTmeY21Rn4WtlZU9ayT0xmlZ4ZCrDVhlAoCH3Z7qsWox6R4R4Ma/7
                oi58ESzmPp8Sb5LwhPehvpi3RYteHQSiKVqVthNUOddePvzxlRZzDsRXLL84
                Vc8J3OUnsc26Q7/mLT/vt4hxo/ypTTi4bNMLxvHMPKL3xyPC7j+CfiHHH3Vi
                1iImu8naJ0Lz/x4S/A18OmTmg7eTzOqRirKpNjpO1bks5EhZVTz/ayxr3uWT
                IlEvdKoIjY3m/VpxgejwZqhi5W2VUIPDCzS5ctlquAwE7q1iA7ucT8v9mLbV
                RSWCH+FKhKu4xhDXI2wj6IIMdnCjC8/gpsEtg9sGdwzuGtQNagbObyI3xySK
                AgAA
                """,
          """
                com/example/unit/Context.class:
                H4sIAAAAAAAA/3VOTUvDQBB9s9F+xK9ULUR/hNsWb55EEAIVQcFLTtt0lW02
                u2I2Jcf+Lg/Ssz9KnKhXZ+DNe2/gzXx+vX8AuMSYkBa+krpV1avVsnEmyBvv
                gm5DH0RIVmqtpFXuRd4vVrpgNyKM5qUP1jh5p4NaqqCuCKJaR5xJHQw7AIFK
                9lvTqQmz5ZQw3m4GsUhFLBJmz+l2MxMT6pYzwvn8v2f4AOfFf+qiDCweffNW
                6FtjNeHsoXHBVPrJ1GZh9bVzPqhgvKt7nI0d/JbAyQ8e45TnlCN3uXs5ogz9
                DIMMQ8RMsZdhHwc5qMYhjnKIGkmN0TfcwIZuOQEAAA==
                """,
          """
                com/example/unit/ContextKt.class:
                H4sIAAAAAAAA/4VSXU8TQRQ9s/1kKbAgKF0QURRBhQU0GqMxMTXEjQUNGhKC
                L9N2rNNud3Fn2vSx8Z/oL/ANI4lp8M0fZby7FAFL9OWemXPPnLn3zvz89e07
                gHtwGOxy0HBEmzf2POE0famdQuBr0dYvdAaMwarxFnc87ledl6WaKBObYJgo
                BF4QvgqDlqyIcOn5h9pd/bDKMLfgLhbPcTwlfkSqYhBWnZrQpZBLXznc9wPN
                tQxovRnozabnRao+n3XZFpUzZhlkGdKPJWWfMKwtuMV6oD3pO7VWw5HUR+hz
                z3km3vGmp6kxpcNmWQfhBg/rVMridg4mBk0MIMcw8+/CMxhmSJUjioG5DENn
                0gzzfee3hAqaYVn85XOBIUmDii+fMDGOi2QcCuVWGEaPO9gQmle45jQJo9FK
                0HuxKAxEAVRAPVoYlGzLaLVCq8oqw9tuZ9zsdkxj0jgGI5swDStrT1ndjm2s
                sLW0ZRAmfnxl3Q4Fdvg5ncwmrZQ9fKJIE2YOPxqDZip7+GlmhZFNPrpjjWH2
                /088fUZSDfnee1lWRzJKUwNm758t1zWNoxBUBMNIUfpis9koifANL3nEjBWD
                Mve2eSijfY+c2mr6WjaE67ekkkQ9PflAZPw6nvm6jKT5nnS7T4hVGEhGwyTM
                I4U04SLtHhBLFWLoAAM7NtvHUBcjX2LZLYrpOJnD7TjGQlgYJbzTy2YI8xj7
                Y3gfiaMzBxjfsfdx6Rw/65RfDpN9fgaW4riAZcICsVHJ9i4SLqZcTLu4jBkX
                VzDr4iqu7YIpzOH6LrIKYwo3FCyFeYWUQlphUuHmb+Hl9oAFBAAA
                """,
          """
                com/example/unit/FixedColorProvider.class:
                H4sIAAAAAAAA/5VW3VMTVxT/3c3XkgRZwveHgoISghAIttXiRxVrWQpoxWKR
                trokK1mS7Ma9GwZfOk4f/BM60761D33yQWdacOpMh+Jb/6ZOp+fuLgGT6NiH
                3Hvuuefjd8/53bv5+98//gRwDhbDUNYqpfVtrVQu6umKaTjpG8a2npu1ipZ9
                y7a2jJxuR8AYlE1tS0sXNXMjfXN9U886EQQYTtS513iGGMIXDdq4zBBMqqMr
                DIHk6EocETRFEUSUIZQVLgxMjSOO5iZIOEbGTt7gDKcX3gPgDMHb0B1XN/7w
                7oXt1ZVJN496dGN9uqxPT1HM48n6oLOW6ejbzoxwGVqw7I30pu6s25ph8rRm
                mpajOYZF8pLlLFWKRcoYyXouMrqoDgXLKRpmenOrlDZIbZtaMa2ajk3+RpZH
                0MPQkc3r2YIf4JZmayWdDBlGkgu1tZ05olkWQTZmRM360B9FL44z9L79BAwJ
                2itbpm46U4fViGet8uPxuUeb086FDYYk9eI9S5tJqguNjnddf6hVilRckzt2
                JetY9qJmF8jFa+9QlBo5zNB+NPFwznNieNCgCQ3Sq2p9cd4PeBwncUpgSDLI
                juWVkeAkR+uLy9BVq7tWMYoug89GMS5o2lOfI+ky95KMNLFcK5d1M8cwnmzQ
                vDqVH55QTiEjMkxTU48ea8PWynmijnehIviAofngFOMGmRChRA/rcsXxEc5H
                8SEu1GCpVu9dWGYElosM/cnZdxteFoZX4khhTEhXqch5jednrZxOSA9EH6m4
                +XS7r+NTAewGVUt/VNGKXJyhAcJ7DMPvuoN0f7T1ok5ti3tx3DRE8lBSVUfv
                xfE5FkSiRdJYTl5cskR9GkFSWRDkC4a24ewhje+XXB4zTPw/3jO0Hjgs6o6W
                0xyNdFJpK0CvLRNDkxhAT12B9NuGWBFqKTfF2MTek0xU6pbc394TT5SjUUmJ
                uUtFDLLk6wOkkD39MXn/qdxN3tIku6YkworUK00GXr9ke0/2fwlLSnC+R5GF
                au71U6GW6McOtoNykxKeH1SiZEAhZFny3MmWvWkXU+LzbUozbR7LyEpLb7Cb
                TSpzr38IkHercMmElQTNbXOd9UnalY75JqWTtrvIhZHcTXKPkG/3HwHse0nk
                JE4e6Q3KISW8/73UQmfuiYbk/Z9PTDKST4qqZRgGG72Dbz5f/Qtvv1a0HT18
                LImn4q1iokPywWeDLPyXdaJAz1bQI3jLgmHqS5XSum7fEVQUBLOyWnFFsw2x
                9pXDtyumY5R01dwyuEGq6rN/9ZDPDH21Zm/sNi87WrawqJX9oNFlq2Jn9RuG
                WPT4rit1jnRHJfrA0l2jX0J8WYltgu7EetJs0OoyrSShTe0ilur7HS0vaCUh
                T6PwAtpg0NjpWUFBq+AvSSIa0Z3itvmx0oLWNIdSv6HlWcMwcc/AD0NPMTrI
                Sjhf8YHExhLdL3GiPkSYjA+RxKpIYujCAO1vkhwRADpJaMVgFdZQLSzWAEqr
                +FT4UDJUM/e0ryCt9rFdnH5e9XpbHQo+SkquNOEMRvzkZ9wOUPz+4Hc/QhYQ
                Lqb6djDqhSzSGACLkUivqJ//Es0C28ArjK/uYiIxuYNzwvMlPt7BJWV0B5/s
                4NrzmqMM+KCOQKFxtlqHM34dZC/UZ7WlkKtdmYPqQ7lPs3iiRlJjvyIUfDb2
                F6SfEAo8G9uDtCgCnXWDLbnKoNexoluXQET+B+0RWh/WbaRatxHcxK2jXZun
                wjHxn8FHe97PHE717eL2iyrWQU9bDRT2GyCkZdxxWVRyYTyESfNj2vuS2ryy
                hoCKuyq+UrGKeyRiTcXX+GYNjONb3F9DL0crxwOOOIfGMcSR4GjjaOZY5xjk
                yHKc5DjFMceR47jpjjpHF8cARztHB8esq0zR+B8Bv/VrZAsAAA==
                """,
          """
                com/example/unit/ResourceColorProvider.class:
                H4sIAAAAAAAA/5VVW1MURxT+evY2Oywwu8hFQKNxxQXUWdDcRE2ERBmyoAFD
                ophLs4wwsDuzmZ6lzEuKyoM/warkJZU85IkHU5WgFatSBN/ye/KYSuX0zNSK
                y2qZh+k+ffqc73x9zumev/79/Q8A57HCMFR2q4Z1j1drFcuoO7ZvzFvCrXtl
                a8qtuN4Nz920VywvBcagr/NNblS4s2pcX163yn4KMYajBxCaPBMMyYs2bVxm
                iBfM4UWGWGF4MYMU0hri0BgSniVMYsPMDDJoT0NBBxn7a7ZgKJRejeMEg7pq
                +fMhFIUwiTIpAqMzy+dq1rkxgjvSAm/KdXzrnj8hXU6UXG/VWLf8ZY/bjjC4
                47g+922X5DnXn6tXKhQpVQ5dVPRSCjZcv2I7xvpm1bBJ7Tm8YpiO75G/XRYp
                HGboLq9Z5Y0I4Ab3eNUiQ4ZThVJzWif2aRYkyOqETNcABjX04whD//4DrHq8
                tkZRwrSn8BqdmsgJ36uXfTq4TVZh4im5x/G6hmM4QRgvzgKDRns117Ecf4xc
                y27ta4ZRQnjVSsjaqhpVcZghI93zK9ZdXq/4DHdfuZymeTAz/4PBKRQkgzPU
                Fb4bppHhUGH4YHIZept1k3W7EjTvmIZx2aGDLcMUgr69pOI89Tiv1SyHOu9M
                oUX9DqiiCET0Tbwlg7xNQWSKX2Z4QRpOSMOplxtekoaXMzBQlNJ7lIQ1Ltam
                3BWLIfvMk5rUWpXnnMpgUvbG+/iAjmJ9VecVuivdLZpz+DZD/mV3hPqbL1cs
                oplw/TXZ47mDKMSiFN2aWcvnK9znpFOqmzF6mZgc0nIAvQkbpL9ny1WRpBXq
                yL93t4qa0qcE3+6WpuiqpqhJmtvCJQ1yjnaloRondYe6d1/t290aV4pssj2X
                1JV+pRjb+ymp6PGZtJ6Sq+mn92MzXbpKMhmqqhIakZqROk2yNq7qbf3xPlbM
                TD99EJs5preTtmM8qXfSrE/3PH3MdrdoYPQphB5Xs3qO8LtCoAeM5EMkd0t5
                PttgoRLb/ria0JN73yqdxPm4llD3fjxaZCQflmcfZzjW6t4+/woifAgDNfVK
                6cWvhbzq0a0/u0GXMx42SGfJdqy5enXZ8m7KUsoKumVeWeSeLdeRMj9fd3y7
                apnOpi1sUjWetSvP+oFhoNnsud32BZ+XN2Z5LQLVFoJrdtWWi8OR6+IBR4zR
                3Y7TSZP05eRPQ7YMyfTw0HiHVpfJQqFZG3mEtpGB39D5C60UfEaj9AJ68TmN
                PaEVdGRlx5Ek0ahBCbcrwjJkI9KcGPkVndstYTKhQQRDTw26yUo6T0ZEOkZz
                fY9xVEI8Rv55lCT69pHpaJDpIPSTtP8FySnJoYeELIYazE40M2Mt2GTlaxix
                GUMsiJF+AuXWwCOMPGw4hcHTjeDpKBNfRiQptp7GKE5HsYeCGhD8YPyb76BK
                BhdHBnZwNoTkNMbA2kikdygKf4FmSW3wCcZvPcK53Bs7eEd67uCiPryDd3dw
                5WHTMQYjRvt40DjZyMFQlAM1TO3V5jSojaJcw3TE4w7N8k3Jj4z+jER8e/RP
                KN8jEdse3YUyK4FO0/eD1MTDUvGgN2Ip9R9kU7R+lrF8I2N5zODD/eUyA9fl
                YFxCmeYqaUtUmdklxEzMmbhu4gY+IhHzJhZwcwlM4GMsLqFHICvwiUBG4FMB
                VSAn0CUwJHBK4FqgnAnGWwK3BXoFTgocEugWmBQwaOs/dAGm7ewJAAA=
                """,
        ),
      )

    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            val resolved = node.resolve()
            assertNotNull(resolved)
            assertEquals("color", resolved!!.parameterList.parameters[0].name)
            return super.visitCallExpression(node)
          }
        }
      )
    }
  }

  fun testResolutionToJavaMock_source() {
    // Regression from b/325564559
    val testFiles =
      arrayOf(
        java(
            "Test.java",
            """
            import static my.mockito.Mockito.mock;

            class Foo {}

            public class Test {
                private Foo fooMock;
                public void foo() {
                    fooMock = mock(Foo.class);
                }
            }
          """,
          )
          .indented(),
        java(
            """
            package my.mockito;

            public final class Mockito {
                @SafeVarargs
                public static <T> T mock(T... reified) {
                    return null;
                }
                public static <T> T mock(Class<T> clazz) {
                    return null;
                }
            }
          """
          )
          .indented(),
      )
    check(*testFiles) { file -> checkJavaMock(file) }
  }

  fun testResolutionToJavaMock_fromBytecode() {
    // Regression from b/325564559
    val testFiles =
      arrayOf(
        java(
            "Test.java",
            """
            import static my.mockito.Mockito.mock;

            class Foo {}

            public class Test {
                private Foo fooMock;
                public void foo() {
                    fooMock = mock(Foo.class);
                }
            }
          """,
          )
          .indented(),
        bytecode(
          "libs/lib1.jar",
          java(
              """
            package my.mockito;

            public final class Mockito {
                @SafeVarargs
                public static <T> T mock(T... reified) {
                    return null;
                }
                public static <T> T mock(Class<T> clazz) {
                    return null;
                }
            }
          """
            )
            .indented(),
          0x8ae22c11,
          """
                my/mockito/Mockito.class:
                H4sIAAAAAAAA/31QwU7CQBB9Q6FYREHUGE2MJ2PhYMNVkMSQeEJNpOHCacGV
                LNJt0m5N/AT/Rk8mHvwAP8o4xR4IqJvsvtm3783MzufX+weAc+yWkINVRL6M
                AmxCdSoehTcTeuLdjKZybAh2W2llOgTLrQ+KWCPUgicvCMcPyoTe1Q8S8t3w
                ThIqPaXldRKMZOSL0YyZfColnLjD3nLyVn2VIjh9NdHCJBGbj9r+2aqm4w59
                v1XnTdi/TbRRgRyoWHG9C61DI4wKdUzYW7D2xb0ciEhEk5hdx+7CU3cm4vj3
                Xpp/1F92t7mXTtZRqR8m0VheqvT35WxAp6kBTRR53umyQOnE+XT4dshIjIXG
                G+iVA87Cpz0nLTxjHWXGVHqQSXP0sqSzsZHy2EQl46qMDnu3mP3f62TeGt+2
                59HON0mxyb0kAgAA
                """,
        ),
      )

    check(*testFiles) { file -> checkJavaMock(file) }
  }

  private fun checkJavaMock(file: UFile) {
    var count = 0
    file.accept(
      object : AbstractUastVisitor() {
        override fun visitCallExpression(node: UCallExpression): Boolean {
          count++
          val resolved = node.resolve()
          assertNotNull(resolved)
          val params = resolved!!.parameterList.parameters
          assertEquals(1, params.size)
          assertEquals("java.lang.Class<T>", params.single().type.canonicalText)
          assertEquals("Foo", node.getExpressionType()?.canonicalText)
          return super.visitCallExpression(node)
        }
      }
    )
    assertEquals(1, count)
  }

  fun testJavaAnonymousClassImportSTR() {
    // Regression test from b/322179541
    val source =
      java(
        "Test.java",
        """
        class Test {
          void enclosingMethod() {
            new Object() {
              Object anonymousClassMethod() {
                return new Object();
              }

              void test() {
                anonymousClassMethod();
              }
            };
          }
        }
      """,
      )

    var count = 0
    check(source, javaLanguageLevel = LanguageLevel.JDK_21) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitClass(node: UClass): Boolean {
            count++
            val superTypes = node.superTypes
            assertEquals(1, superTypes.size)
            assertEquals("java.lang.Object", superTypes.single().canonicalText)
            return super.visitClass(node)
          }
        }
      )
    }
    // Test and anonymous Object
    assertEquals(2, count)
  }

  fun testMethodsBelongToValueClass_source() {
    val testFiles =
      arrayOf(
        kotlin(
          """
            val <T> V<T>.value: T? get() = getOrNull()
            fun <T> force(v: V<T>) = v.isNull()
          """
        ),
        kotlin(
          """
            @JvmInline
            value class V<out T>(
              val value: Any?
            ) {
              inline fun getOrNull(): T? {
                return value as? T
              }

              fun isNull(): Boolean = value == null
            }
          """
        ),
      )
    var getOrNullCheck = true
    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            val resolved = node.resolve()
            assertNotNull(resolved)
            val expectedMethod = if (getOrNullCheck) "getOrNull" else "isNull"
            assertEquals(expectedMethod, resolved?.name)

            val containingClass = resolved?.containingClass
            assertEquals("V", containingClass?.name)

            getOrNullCheck = false
            return super.visitCallExpression(node)
          }
        }
      )
    }
  }

  fun testMethodsBelongToValueClass_binary() {
    val testFiles =
      arrayOf(
        kotlin(
          """
            val <T> V<T>.value: T? get() = getOrNull()
            fun <T> force(v: V<T>) = v.isNull()
          """
        ),
        bytecode(
          "libs/lib.jar",
          kotlin(
              """
            @JvmInline
            value class V<out T>(
              val value: Any?
            ) {
              inline fun getOrNull(): T? {
                return value as? T
              }

              fun isNull(): Boolean = value == null
            }
          """
            )
            .indented(),
          0x94e1a4f6,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijg4uJiEGILSS0u8S5RYtBiAABz6lUC
                JAAAAA==
                """,
          """
                V.class:
                H4sIAAAAAAAA/41V3VMTVxT/3c3X7hLC8k2itbZSDKEaQFsV8QvUGopohcYi
                be0SVlhINnTvJuNLZ5g+tP0L+uBjX3zhwc60yNSZTooPzvR/6b/Q9tzNTYgh
                OD7knnvPnvM73yd///vHnwDOwmFg2QgYQ3JyYWJ23Syb6bzprKbvLK9bOe/i
                5YMsBqOZF0GQQV21vKyZL1kMPcnhVnqDs0V3Nb1uecuuaTs8bTpO0TM9u0j3
                uVI+by7nLRILlasoXQcxotCga1DQxhD01mzOEJjNkk6MjN9xBcgpu7CZZxhK
                HtRu6VR/K8GFBfrSPWgPPhpsBmYZMm26q6MMbTZv+NDbCugBQ7tXnPdc21l9
                S8+qwsKzZt5Uyc6vWG4EcYbwpO3Y3mWKPzmcjeIIjupI4B0qQzbp5++SindJ
                zNzctJwVhlPJgyYOWpUWKM/v4X0BeKJJ81B/GzU/EJpDDEeT028WTArBYfK5
                lqLm1pGORjGCD4XsqSj6MaBTA6QpsWsmX5surlhvKgBVS63J+cnKRHEGZ3WE
                8FEUYxgXYOeolta3JTPPJdTJFlCtq6uXnOXiY18tiouICLhJhmMbRS9vO+n1
                ciFtO57lOmY+nXFEONzO8QiocqrpWjeE1SiuYkLHFVyj5i96a5ZLhav6E8UF
                8UnBjUPiy4pIqOxGjqbIc0s5r+jKICZbT/TbDsaJN01r0ROdT1JqLfpDJomm
                U3TnJyKGOZqeMkO0IdU0RcrmmDjGGfpmG7I2Uy5kHHqIjdBZ+3Db8swV0zOJ
                pxTKAVphTByaOEDoG8R/bIuXAF4h4H8qW0O6MqDolS1dMcShhunSJqkqaYyo
                ou79eHWgspUKq5Utgx1n48qoMtXTpRqBRHCAjSovd1lla++XcFANGqGZhKEm
                lFFtXDX06udbL38O+F/bjOhMl9EuuOcrW7f2flB9doyUDKODlAwSZT6v0+ia
                aTe6BZD/7jF6SaaP3v11mQEjfq+75gNxVIolEVTDRmTvJxaoOvW9EqQI4iJo
                SiRbYCIdWn13UT9VdxXtruzpDY/hyL2S49kFK+OUbW7T4r22X14Sqo5Lxyzl
                f65UWLbcBbGcxU4u5sx81nRt8ZbM9nnPzG3cNjflW5u3Vx3TK7l0H2y2c9d0
                zYJFE/GaQX2+WHJz1k1b6MelTvaAZzSwCvW7CK5L/BUQtegVJqoSjaGT7hGS
                eUSvtOgIoqHUb4g+o4uCVSkMBLBGZ7QqgHZSxWtQcXTAICkBtECaClE9MJla
                fIXQffbMR7DpNMD+I0+UCEJkjc66DYVOYaPP11bJ4W7fmo4e+gHrvkedTVZ7
                SZ75Vq9I9/XUK0SCTxEMbNethqCoV1lDOFpDODUDmtiVEuwcmRLf4i+QWHyO
                Y13HdzCY2sFJY3gHRE8/85O6DxKXIEysXgkyJD1SRUJ3Mdqso8o8amKvSp0J
                aTgmMqfeDzxFeAcfb/ux276OMkNZm2rEidVt06Zubfv89qG2aWFKnTlZt6Mj
                f0F5glBge6QCZQeXUg93MeUzgvueBKFoRr1ipCW9ELdpv9UYrterMyaxNeHP
                yC5u7jtUVdekQ+LmqxuK2INS/XKtpVLPcSs18juivzY1qNKApdexdNn1GjLU
                ekEf67hMjZJqLojiS9dababeap8S3aBfhFX7zohjFrelayepx/yWegFlkdy7
                04yq4a5ANTrF/51UOtE8a6zFfMXxWb02p2UCwilK3lRz8sK4J3MfxrwfcEAG
                EUDepysoEP2ObgtEPycb2SUEMrifwRcZLOIBXbGUwZf4agmM42s8XEKCI8bx
                DYfJoXFkOAyOMEeE47rPnOa4wDHBsczRwXHGZ45xjHPkOHo5+jhGfGY/x8D/
                BVL5rEYLAAA=
                """,
        ),
      )
    var getOrNullCheck = true
    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            val resolved = node.resolve()
            assertNotNull(resolved)
            var expectedMethod = if (getOrNullCheck) "getOrNull" else "isNull"
            if (useFirUast()) {
              expectedMethod += "-impl"
            }
            assertEquals(expectedMethod, resolved?.name)

            val containingClass = resolved?.containingClass
            val expectedClass = if (useFirUast()) "V" else "Object"
            assertEquals(expectedClass, containingClass?.name)

            getOrNullCheck = false
            return super.visitCallExpression(node)
          }
        }
      )
    }
  }

  fun testMethodsBelongToResultValueClass() {
    // b/343519623
    val source =
      kotlin(
        "main.kt",
        """
          val <T> Result<T>.value: T? get() = getOrNull()
          fun <T> force(r: Result<T>) = r.getOrThrow()
        """,
      )
    var getOrNullCheck = true
    check(source) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            val resolved = node.resolve()
            assertNotNull(resolved)
            val expectedMethod =
              if (getOrNullCheck) {
                if (useFirUast()) "getOrNull-impl" else "getOrNull"
              } else "getOrThrow"
            assertEquals(expectedMethod, resolved?.name)

            val containingClass = resolved?.containingClass
            val expectedClass =
              if (getOrNullCheck) {
                if (useFirUast()) "Result" else "Object"
              } else "ResultKt"
            assertEquals(expectedClass, containingClass?.name)

            getOrNullCheck = false
            return super.visitCallExpression(node)
          }
        }
      )
    }
  }

  fun testResolutionToConstructorWithGenericInBinary() {
    // b/343257595
    val testFiles =
      arrayOf(
        kotlin(
          """
            import test.pkg.*

            fun test() {
              val x = Foo()
              val y = FooWithGeneric<Int>(42)
              val z = FooWithGeneric<String>(42)
            }
          """
        ),
        bytecode(
          "libs/lib.jar",
          kotlin(
              """
              package test.pkg

              class Foo

              class FooWithGeneric<T>(
                val value: T? = null,
                val flag: Boolean = false,
              ) {
                constructor(p: Any) : this(p as? T, true)
              }
            """
            )
            .indented(),
          0x9eb7ed78,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijg4uJiEGILSS0u8S5RYtBiAABz6lUC
                JAAAAA==
                """,
          """
                test/pkg/Foo.class:
                H4sIAAAAAAAA/01Qu04CQRQ9dxYWWVdevsBXrRYuEDuNiZqQkKyaqKGhGmCD
                w2PHsAOx5Fv8AysTC0Ms/Sjj3ZXC5uQ87tzHfP98fAI4xT7BNUFkvOdh32to
                nQERCgM5k95Ihn3vrjMIuiYDi2Cfq1CZC4J1eNRykYbtIIUMIWWeVETI+f8b
                nRGK/lCbkQq9m8DInjSSPTGeWTyYYsjGAAIN2X9Rsaoy69UIB4u544iycESB
                2WJeXszrokpX6a9XWxREXFWn+K3No06Ghpe41r2AkPdVGNxOx51g8ig7I3ZK
                vu7KUUtOVKyXpvOgp5Nu0FCxqNxPQ6PGQUtFitPLMNRGGqXDCDUIvnG5Znwy
                Y5mVl2ggffyOlTcmAhVGOzEt7DC6fwXIwkny3QS3sZf8O2GVM7cNq4m1JnJN
                5FFgimITJay3QRE2sMl5BCfCVgT7F16fV820AQAA
                """,
          """
                test/pkg/FooWithGeneric.class:
                H4sIAAAAAAAA/31T30/bVhT+ruPYjknAySBA+NHSshHCVgPrtq5QtsLGFCl0
                E2RMg6dLcIOJsZF9E+1pyuP+hb3ueQ+rtIppD1O0x/1R0851TNpCVlm658c9
                5zvfOff4n3///AvAQ+wwTAonEvZlq2nvBsF3rjj7yvGd0G3oYAzlzfrj2jnv
                cNvjftP++uTcaYiNrdsuBuumT4fKoG26viu2GIrl20lHy4cMerle72uLtSBs
                2ueOOAm560c29/1AcOEGpD9rex4/8RwqlCovH2ahwTSRxghDusO9tsNQuF0g
                ixxGM1AwxqA+93iTgR1lkUdBOt8hpzhzI4bp2v8Mgcp9OYx4tdYKhOf69nnn
                wnZ94YQ+9+wvnOe87YkdIizCdkME4R4PW0640Sesm1R0isFoOuKwz3m8vDxs
                lunyMg2FZkORuzFtavqIYWIIFzm5+2+bXCDk8AiNXRpYYJgfRr3qi5Ay3Uak
                4z4Vapw5jVaS+g0P+YVDgQxLQwi85jmQIM243XfxnolFLMnGJ2Xjy9RE3FT+
                enZ7juCnXHDyKRedFC0kk0dGHiC6LfL/4EprlbTTNYafet2iqUwpZq9rKpY8
                DJWUEZLaVK9bUY1e12LryqqyPWYUC5qVKpH+9y+aYqnbMwXTUKx0SZ1ij3rd
                gkGGRtd6fG3s5weWQfAl1chY5v7EIJ78JpXK0cWIlZV81olinUmmGi3Ng5Zg
                yBy4TZ+LdkhPO7Pf9oV74VT9jhu5tLpPXz0KLd5OcEpBYzXXd561L06csC7X
                W25x0ODeIQ9daSfOxZtYgyd5AzR3IHijtccvkzTzIGiHDWfXlcZ0gnF4iw3W
                6HnSNGYFBfnDkHwcWyvYIKlRhxmSBfnfJDYtM1LYJOt7ipNPNFa5Qray8hJW
                ZfYlxl/EAE/iNJXONJ0awegEn8YWee720zCBonxv0mRpFmuy0DUJA5/JpVAS
                FrBk9UmyZPU9ApHMJ+fUH39GmtXm0lKmnlRWZq8w3WfxOZ0qFEOP+ZQoXfLR
                6DNIjhIni2QRJtUtYSZpzJYrKCMrv8P6bdCOFju1uIVsPyBpoU939o2Z5TGH
                +YTr64Djv94A1IcAykbvUJRMfkqsFJK5lcK9P1CurKiD9q5B5HglSLEfOBhs
                DgvxYOUYdUnhboJtJsSWqKKsm6mwlMauUHkRB7wilEmwUkmPKaIj5afYJvkt
                xbxPEB8cI1XFgyrsKlaxRirWq/gQD4/BInyEj48xGqEY4ZMIjyIsRLgTIRch
                H5t6hLkI8xFKEWYijP4HpofXVKMGAAA=
                """,
        ),
      )
    val expectedClassNames = mutableListOf("Foo", "FooWithGeneric", "FooWithGeneric")
    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            val resolved = node.resolve()
            assertNotNull(resolved)
            assertTrue(resolved!!.isConstructor)
            assertEquals(expectedClassNames.removeFirst(), resolved.name)
            return super.visitCallExpression(node)
          }
        }
      )
    }
  }

  fun testResolutionToInternal_binary() {
    // b/347623812
    val testFiles =
      arrayOf(
        kotlin(
          """
            package pkg

            fun foo() {
              Lib.internalFun()
              Lib.internalVal
            }
          """
        ),
        bytecode(
          "libs/lib.jar",
          kotlin(
              """
              package pkg

              object Lib {
                internal fun internalFun() {}
                internal val internalVal = 42
              }
            """
            )
            .indented(),
          0xcc4d4078,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijg4uViLshOF2ILSS0u8S5RYtBiAABU
                vEmiJwAAAA==
                """,
          """
                pkg/Lib.class:
                H4sIAAAAAAAA/2VRTW/TQBB9u3YSx0lbpy2QpHy3QBpQ3VbcqJBKocIoDVJb
                RaBISE5ihU0cG8WbiGNO/BDOXEoPlUBCUbnxoxCzxrRVseydmbfz3qzf/vr9
                7QeAx3jEkPnQ79o10cqAMVg9d+zavht07detnteWGWgM6S0RCPmUQausNvJI
                IW1CR4ZBl+9FxJCtJRpPSEEE0hsGrr87ClYGrggYFrqedBK04foJSloOQ06c
                bzAwJ49ZzGXBYTEYW20/HmxSTdMMp35wuF3feZHHIkzVdIVhuRYOu3bPk60h
                yUa2GwShdKUIKa+Hsj7yfTpVodYPJYnZe550O650CeODsUYmMLVk1QI6QJ/w
                j0JV65R1NhjeTSdlkxe5ya3pxOSGSgyKGkVuTifGz0+8OJ1s8nX2LGPw089p
                wl/NWJkyXzdeTicKMLL7i5ZGgP7mdPKcEIOIZd1IWWk1ZZOp2bkLzpHnZOda
                XzIs7Y8CKQaeE4xFJFq+t33+h3QDO2HHY5iricCrjwYtb3joUg/DfC1sK1eH
                QtUJaB6Eo2Hb2xWqKCXCjf9ksUHe6uQBR0lZTYe7T1Wa4jWKZXVHFHXaT8Xo
                A6psZR/FVPUExlFMriQkQMMqrfm/DciSJFBA7oz8MHafvstE/QKRnRHzmEmI
                a8lU/SsKXy5xUxe4esI1MH82tELd6rG+g789wcIxrlrVYxSO4ln/dEzS4ajG
                2vfigzqEFgktNaE5KDtYcnAdNyjFTQe3cLsJFuEO7jZhRupdjpCOMBsn+Qgz
                EVbiPPcH4t7VsogDAAA=
                """,
        ),
      )
    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            val resolved = node.resolve()
            assertNotNull(resolved)
            assertEquals("internalFun\$main", resolved?.name)
            return super.visitCallExpression(node)
          }

          override fun visitSimpleNameReferenceExpression(
            node: USimpleNameReferenceExpression
          ): Boolean {
            if (node.sourcePsi?.text != "internalVal") {
              return super.visitSimpleNameReferenceExpression(node)
            }
            val resolved = node.resolve()
            assertTrue(resolved is PsiField)
            assertEquals("internalVal", (resolved as? PsiField)?.name)
            return super.visitSimpleNameReferenceExpression(node)
          }
        }
      )
    }
  }

  fun testAmbiguousNavOptionBuilderPopUpTo() {
    // Regression test from b/370694831
    val testFiles =
      arrayOf(
        kotlin(
          """
            import my.navigation.NavOptionsBuilder

            fun navigate(builder: NavOptionsBuilder.() -> Unit) {
            }

            fun <T : Any> test(route: T) {
              navigate {
                popUpTo(route) {
                  inclusive = true
                }
              }
            }
          """
        ),
        bytecode(
          "libs/nav.jar",
          kotlin(
            """
              package my.navigation
              import kotlin.reflect.KClass

              class NavOptionsBuilder {
                fun popUpTo(id: Int, popUpToBuilder: PopUpToBuilder.() -> Unit = {}) {
                }
                fun popUpTo(route: String, popUpToBuilder: PopUpToBuilder.() -> Unit = {}) {
                }
                inline fun <reified T : Any> popUpTo(
                  noinline popUpToBuilder: PopUpToBuilder.() -> Unit = {}
                ) {
                }
                // @RestrictTo
                fun <T : Any> popUpTo(klass: KClass<T>, popUpToBuilder: PopUpToBuilder.() -> Unit) {
                }
                fun <T : Any> popUpTo(route: T, popUpToBuilder: PopUpToBuilder.() -> Unit = {}) {
                }
              }

              class PopUpToBuilder {
                var inclusive: Boolean = false
              }
            """
          ),
          0xc0988f76,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijg4uJiEGILSS0u8S5RYtBiAABz6lUC
                JAAAAA==
                """,
          """
                my/navigation/NavOptionsBuilder＄popUpTo＄1.class:
                H4sIAAAAAAAA/41Ua28TRxQ9s3b82CxNSHkkgfJ0wU5a1qEU2to8QkhgkWtQ
                EyJQPo3XgzPxetbahwXf8lv4BYVKBYGEon7kR1XcWRsTEwjI8szdO/eec++c
                u/vu/9dvAVzCXYZS56mteE+2eCR9Zdd5715XW+HNWHpNERS6fvdBd80vLGTB
                GGSt7UeeVPZWr2NLFYlAcc+u8U6jySu7zx7Hyk1w7JWBtVCtjXLd7yMPiIbZ
                D5SMKtcqDLNfpsoizXBif7osMgyZqiS4awypYmmdIV10SusWcjBNjGGcHNGm
                DBnma998DVRYRqqe3xYMp4v7t6Qpz9b8oGVviagRcEn1caX8iPdrrftRPfY8
                gjQLupCCoqccpkZ7G/buqCggCOmGWRxiOOxuCrc9wLjPA94RFMhwvljb4j1u
                e1y17HuNLeFGlV2eVQ3Squh7OIKjJg5jmvj2b4Th3GdAS3tdDMf3A8riuIUJ
                TJowcIJhfJfkWZxiyDn11bXF+tIyw4GRebBwBmfzOI0Cg9FdYJj6HHeu6nqJ
                3lrivCYp6cTv8mTNMxz8APmniHiTR5xSjE4vRe8C00teL2BgbW2k6PCJ1FaZ
                rCZxFna2LXNn2zQmDdOYNsic3NmeNcpszigbd8z/nmWMnM5qXqTWqlz56mnH
                j0OaPgI9+ZUJy4KqmfgwZk3xmMdexPDw0xHbk1hxvvLeUcBe6Uj9q6DKDu3B
                u9Am2vSS3xT6ln2Xe+s8kLzhiTW9UJE1qUQ97jREMPAU/opVJDvCUT0ZSnIN
                p3Hx47QzWI5SIljyeBgKepxYVq7nhzSMpMem32TIr8qW4lEcEKa56seBK1ak
                JpgZEKz34XehokzajtH90ucJM1psEixNfxoA8iySVaAIkgCZufRLWM8TiW/S
                avW9OJDkHNRzSZE6o0K7QXt2fur7V5jRKQaWkmB6++mn04/0Qwbp2prCLJ3f
                0jYNESFicpFQjw3quDFAtebmd/DDvzj5Aj/+PQKNEWhrCG3hHM7TeQ7FYVdH
                kxhg/A2MRy8x9w9+ep44xrBMq0lh/YBprCRXUsV13E7oUriT7Dfg0H6FIn+m
                rAsbSDmwHZQdLOCig19wycGvuLwBFlLUbxtIh/g9xB8hZkNMvAcryKxcRgYA
                AA==
                """,
          """
                my/navigation/NavOptionsBuilder＄popUpTo＄2.class:
                H4sIAAAAAAAA/41U23ITRxA9s5J1WS/YOFxscw8CJDthZYckBAmCMXbYlBBU
                bFyV8tNoNchjrWZVe1GFN39LviBAVaCginLxyEdR9KyEYmFj8rAzvT19zume
                7t33H16/BXAdvzOUOk9txXuyxSPpK7vOew+72grvxtJriqDQ9buPu+t+YTEL
                xiBrbT/ypLK3ex1bqkgEint2jXcaTV7Ze/YkVm7CY68OrIVqbVTrUZ95IDRE
                P1YyqtyuMMx+WSqLNMO5w+WyyDBkqpLobjOkiqUNhnTRKW1YyME0MYZxckRb
                MmSYr/3va6DEMlL1/LZguFg8vCQteanmBy17W0SNgEvKjyvlR7yfa92P6rHn
                EaVZ0IkUFL3lMDVa27B2R0UBUUg3zOI4wwl3S7jtAccjHvCOoECGq8XaNu9x
                2+OqZT9sbAs3quzxrGmSVkXfw0mcMnEC06R3eCEMVw4gLe13MZw5jCiLMxYm
                MGnCwDmG8T0tz+ICQ86pr60v1ZdXGI6MzIOFb3Epj4soMBjdBYapg7RzVddL
                +q1bnNciJQ08midrnuHYJ8oHIuJNHnGCGJ1eir4Fppe8XsDA2tpI0eFfUltl
                spqkWdjdsczdHdOYNExj2iBzcndn1iizOaNs3Dff/Z0xchrVXKTSqlz56mnH
                j0OaPiI9/5UJy4Kymfg0Zk3xhMdeRB/c5yO2D3hAb7/yIVacA3pJ43ALlOrx
                fQLX2pRHetlvCn3tvsu9DR5I3vDEul4o65pUoh53GiIYeAp/xCqSHeGongwl
                uYbjufTf+DNYjlIiWPZ4GAp6nVhRrueHVAE1aMtvMuTXZEvxKA6I01zz48AV
                q1ILzAwENvr0e1hRpmaP0YXT/wozuvvUwTQ9NBHkWSKrQBHUE2Tm0i9hPUt6
                fpdWq+/FkQRzTA8qRWpEhXaD9uz81DevMKMhBpaTYPodELWGn+yHDODamsIs
                nd/TNk0VMWJyiVhPD/K4M2C15uZ3cfZfnH+Oy/+MUGOE2hpSW7iCq3SeQ3FY
                1akkBhh/A+PPl5h7ge+eJY4xrNBqUlg/YBqryZVU8St+S+RSuJ/sd+DQ/jNF
                fk+oa5tIObAdlB0sYNHBD7ju4Ef8tAkWUtSNTaRD/BLiZojZEBMfAVtBfIhX
                BgAA
                """,
          """
                my/navigation/NavOptionsBuilder＄popUpTo＄3.class:
                H4sIAAAAAAAA/8VU23ITRxA9s5J1WQtsFAO2IWCwAroQZBnnAhJOhJFhgyy7
                IuNUyk8jaZHHWs2q9qKCN39LvgCSqpBKqlKuPOajUulZyY6FFcMbD9vTO9t9
                Tk/P6f37n9//BLCCLYZM91Ve8r5oc0/YMl/j/c2e8txHvrBappPq2b3nvW07
                dS8KxiCqHduzhMzv97t5IT3TkdzKV3m30eLFk99e+LIZ4OTXh16hVB3l2hog
                D4mOs59L4RVXiwzz/08VRZjh2tl0UUQYIiVBcKsMoXRmhyGcNjI7CcSg65jA
                JG14e8JlyFU/uA1UWETIvt0xGW6kzz6Solys2k47v296DYcLqo9LaXt8UGvN
                9mq+ZRGknlKFpCS9xZAcPdvx2Q3pOQQhmm4UMwwXm3tmszPE2OIO75oUyHA7
                Xd3nfZ63uGznNxv7ZtMrntipK5B2UfXhEi7ruIhZ4jv7IAy3xoBmTm8xXD0L
                KIqrCUxhWoeGawyTJ648igWGmFGrb5draxWGcyN6SOAmFuO4gRSD1iswJMdx
                x0pNK7hvdcVxRZJRiefj5OUYLhxBbpgeb3GPU4rW7YdoFpgycWXAwDrKCdHH
                l0J5r8lrEef9w4Okfniga9PaYFFmVvnkzWtL7KYeI1+bZVltKURP+Kn+108R
                LTahAJbpvCUubfmqa/suSVIxbTNcf4/0oigxTB3pr2W+4L7lMfzwrvZOJb5v
                HovGmCslVaziG4aZU3B3O8SaHTv/ddt3muZjs+G3Ky89kyRqS+rtRJ9bPo3J
                QX2jvKWPA9SfBWh6tr5w5K3ruYXCwtjgD55RPVvVC4uFO8srDwp6tqLTnK/Z
                LVOpxm5ya4c7gjcsc1sZ6m1VSLPmdxumM9xJfe9LT3RNQ/aFK2jreLrK/00v
                Q8KQ0nTWLO66Jr1OVWTTsl0aLtLXnt1iiNdFW3LPdwhTHzRpXSiCmXEdY5gb
                8u4MWEfIrrxb04mvKJDAJ0hP9I/GnFI8qTZMPk0B2Qq9pSiCJIdINvwWiTeB
                ztfJJga7OBfkXFDDSZEqo0irRms0l/zkN8ypFA1PgmD6BWIySL80CBmmKy+J
                efr+VPmkb0LEdJlQrwzr+HaImsjmDvHpr7j+Mz57PQKNEejEMXQCt3A7OFP6
                +FSXgxjK+APaj2+R/QV33gzrMMjqFDYImMN3QUseUgHPAroQqsFaxgatH12i
                qFEVD6nez+ki7+4iZCBvYMmgq102cA8rBr7Al7tgLr7C17uIuLjv4oGLYmDn
                XerxJkGcJ4hH9KwFoY//BYS5ZXzrBwAA
                """,
          """
                my/navigation/NavOptionsBuilder＄popUpTo＄4.class:
                H4sIAAAAAAAA/41U23IbRRA9s5J1WW+wo9xsE3IViWRDVg6GAKsYHGOTBaGk
                YsdVlJ9Gq4k81mpWtRcVecu38AUEqggFVZSLRz6KomelKBY2goft6e3tPqdn
                +sz++devvwNYw9cM1d5zW/GB7PBYBspu8sGjvvaiB4n02yIs94P+0/5uUF7L
                gzHIRjeIfansw0HPlioWoeK+3eC9Vps7x789S5SX4tjbI2+13pjkejxEHhGN
                q58qGTvrDsPSv1PlkWW4Mp0ujxxDri4Jbp0hU6nuMWQrbnXPQgGmiRnMUiA+
                kBHDSuN/HwM1lpNqEHQFw/XK9C1pypuNIOzYhyJuhVxSf1ypIObDXptB3Ex8
                nyDNsm6krOitgNLk3sZ7d1UcEoT0ojzOM1zwDoTXHWE85iHvCUpkuF1pHPIB
                t32uOvaj1qHwYudYZEeDdBx9DhdxycQFLBDf9I0w3DoFtHoyxHB5GlAely3M
                Yd6EgSsMs8dGnsc1hoLb3NndaG5uMZyZ0IOFG7hZxHWUGYz+KkPpNO5C3fPT
                eesRFzVJVRe+VSRvheHsa8hvRMzbPOZUYvQGGboLTJuiNmBgXe1k6ON3Uns1
                8trEee/oRck8emEa88Zw0WZB++QtGTV2o1Ag31g2ahl6sg/NP77PGYUZXX6X
                dlvnKlDPe0ESkSA1zy7D1f8QXh51hrnX6muLZzzxY7qH/1TeiULnFBFMvzCO
                e8qISSXr+Izh/AmCO13qI7sZtIWeRuBxf4+Hkrd8sasNdd2QSjSTXkuEo0j5
                SaJi2ROuGshIUmis2o03t4LBcpUS4abPo0jQ69yW8vwgItHS3A6CNkNxR3YU
                j5OQMM2dIAk9sS01weKIYG8IfwwVNdLADB06/cawqEVBg83SQ0KhyAPyypRB
                c0FuOfsK1stUCptkrWEUZ9Kas1q/lKkrHFoNWvMrpXO/YFGXGPgiTaa/BKXq
                8ovDlFG59kpYou9b2icRUBrmNwj17VEfn49QreWVI7zzM67+iHd/mIDGBLQ1
                hrZwC7fpewGV8a4upTnA7G8wvn2F5Z/w3ss0MINtsialDRMW8GV6JPepgYcp
                XQZuum7gK1rvU+b7VHVnHxkXtouai1XcdfEB1lx8iI/2wSLcw8f7yEX4JMKn
                EZzULkWY+xuUJTQ8dAYAAA==
                """,
          """
                my/navigation/NavOptionsBuilder.class:
                H4sIAAAAAAAA/7VWXXPTRhQ9K39IVuzEMeSTb3DBSQA7TigUh9AkDcTgGEpM
                oA2lVWzFKHFkjyRn4KWTaWf6H/raf9A+tEwfOpn0rT+q07trOdg4xjGUGWt3
                tXvPvWfvuSvvP//++ReAaRQZzmy/ipvajlHUHKNsxrPazoMKH9nzVaNU0C0Z
                jCG8qe1o8ZJmFuMP1jf1vCPDw+CfMUzDmWXwxMZWg/DBr8ILmcHrvDBshnOZ
                Dr5TDHKlXHlcyZUZorF0ZqvslAwzvrmzHd+omnlhG7/jjiZTY6sMjzqazVx5
                K+7DWoh60Dr8MZFPzQqnFzJlqxjf1J11SzPIl2aaZUer+c2WnWy1VCKuvZUm
                Rwr6GE43cDFMR7dMrRRPm45Fboy8LaOfYSD/Qs9vuX4eapa2rZMhw6VY5u28
                phpmVriTYoqn9hiOq4hggEEyCgwszXC2U7IY+ly+0YK+oVVLDsPTWEdJOoqQ
                biXNUzjWwXG0TmZSxkkGJZ1dyc1lFxYZJjpxegNNBXEaZwI4hbPNqT+EqIzz
                QfQgqEJClOFarDW1R6m3fPe49yhAn1WuOrqCGEOkNR6D0Vm47rf3oUomZVzu
                Rr6kkO9qAFcQ59Jc5NJM0umLHUWI4kzuZivh2U7Y9xAjGDWiG9GDD9OTI+T+
                I2d6Ssb1bjI9JTL9WQA3cJOKK1r7Ht88yJWlb5SIRPz+Qkmz7SOdgx87pL/Z
                5UwuR8n8COdki3tXcJthqM1m3jrq9e/qEba42WaLtJf/fyeR1kjdHvMjbu1D
                i29axp1uim9aFN9SAHeR5sd8nh/z+wz9daLLuqMVNEejDUvbOx66iTDeBHgD
                +nfbovmXBn9L0Kgwydizvd2kKg1LqhTe21UlhQ/oUTy1yfoj5hV6elVuNiwN
                E05KsHnf/i9+Qty7EfaMSgnveUXZ2w37xmkp6Q/7R6UlX3JEkcLy6LGINyIl
                AqL1JhQOU1QB7rk3VgMTIkh96F2IBlvynuh9p+31VlLBcN+ows2WfIlwcqgt
                dMqFqgI6zAR4RFHbAZb2f1L+fs32dvd/kGTVp+z/nEwwnuMkE5nP8bp0VWq8
                SZx+d3lzAxe1+NLR6eZTNuvw3KuKTgbHW+rl6hZdSLwL5YJON5WMYerZ6va6
                buW09ZLOaZTzWmlVswz+7k4GVoyiqTlVi8bRR1XTMbb1tLlj2AYtH1ys5t5c
                3hhCK46W31rWKq6LYNo0dUt8KXRaVlfKVSuv3zH42ojrcrXmsMEPJqmEvbw8
                oVKe6KZL7XN6i/OsUe8b/wPKrzSQ8C21/tokvqM26I4DBAX6+ZUEHgFeJGte
                5fLlSPg1BpvhfsgCfrZm4sL5aAjDwqWMPowQQhO4ENb5GZLEEeoFwgGM4oRL
                8xo58VF//JTv+5+h/oZze7iwPH7i8u/4pBY2T60HLNRA3y/IXnTJ3nfJhiYi
                Y0T2MMpBIvmGcuiAcggxjAuXoSbKlwTlkFQj3EB7oh3thKA9QbSTbWkrYU57
                iha5i7su7cCEIOy51UxZIUp1ygq9X6Pc8hQGDsgHBGXmUp4W/D51+RluVQye
                9Nb4pYhfpjGSp5lhRJirFLqHAqsYaAg/0BB+EDNu+EE3PN/TrVYpPm8rRbiN
                FLcxd4gUs4dIIWLOdyN/pK38XxwSc6Gd/Ivt5L93IH+mrfz9NFGg/jyl+pSI
                P0z3zVp/Q/QjVBS1d104+QYb1D8n9DKFy67Bk8aDNB6m8SUe0RAraeTweA3M
                xiqerKHfhmrjqQ2/jR4x+MpGzEafja9trNl4ZmPexpBYot9tG3P/ASKKcDdz
                DwAA
                """,
          """
                my/navigation/PopUpToBuilder.class:
                H4sIAAAAAAAA/31SQW8SURD+3ttlgYXSBVuktFZt1bRNdGnjSRuNNSEhwdbU
                Sho4PWCDryy7hLcQvXHyh3j2YqIx8WBIj/4o47yFxLQxXma++fabmfcN/Pr9
                4yeAx3jAsDH44AZiInsikmHgvg6Hb4dn4dFY+l1vlARjcC7ERLi+CHruSfvC
                60RJGAzWoQxk9IzB2NltZJGAZcNEksGM3knFsFn/3+CnDNmeF9WCjj9WcuLF
                Y5oMafmXYc0slpBLg2OZ5OqK3Nxp7jYYkodEP3xOz8jX+2Hky8B95UWiKyJB
                K/hgYpBPpkNaB9DUPvHvpa4qhLr7DNXZdMXmJW5zZza1eUqDDGWzNJse8Ao7
                Slx+srjDT8uOUeYV8/zyY46YnD2bls1UwrG2zFTSSelpB0zvWDkWk5Ohtq0W
                fh/1I3rzy7BLT1+uy8A7Hg/a3uhMtH1iCvWwI/yGGEldL0j7TTgedbyq1MXa
                6TiI5MBrSCXp64sgCKP4rgr7dB8ztsb1uQgZhOn3oHiXKlebppzY+47UFwIc
                WxStmFzFNsXsXIA0bMp5ZGLmavNXOJ+vNRf/2ZxHYdH8hNRcq/fWv8G5vnre
                XZwrFt0a3SAP2su9WH8H9ylX9VFpx2oLRg3FGm7WUMIaQZRrWMdGC0zhFjZb
                SCvYCrcVLIWlGGQU/XmQVyj8AXuVoBr4AgAA
                """,
        ),
      )
    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            if (node.sourcePsi?.text?.startsWith("popUpTo") != true) {
              return super.visitCallExpression(node)
            }

            val resolved = node.resolve()
            assertNotNull(resolved)
            assertEquals("popUpTo", resolved!!.name)
            val parameters = resolved.parameterList.parameters
            assertEquals(2, parameters.size)
            val route = parameters[0]
            assertEquals("T", route.type.canonicalText)
            val builder = parameters[1]
            assertEquals(
              "kotlin.jvm.functions.Function1<? super my.navigation.PopUpToBuilder,kotlin.Unit>",
              builder.type.canonicalText,
            )
            return super.visitCallExpression(node)
          }
        }
      )
    }
  }

  fun testAmbiguousExtensionWithSuspend() {
    val testFiles =
      arrayOf(
        kotlin(
          """
            import my.http.*

            suspend fun bar(client: HttpClient) {
              client.webSocket("http://localhost/abc") {}
            }
          """
        ),
        bytecode(
          "libs/lib.jar",
          kotlin(
            """
              package my.http

              interface HttpClient

              interface HttpRequestBuilder

              interface DefaultClientWebSocketSession

              public suspend fun HttpClient.webSocket(
                  request: HttpRequestBuilder.() -> Unit,
                  block: suspend DefaultClientWebSocketSession.() -> Unit
              ) {}

              public suspend fun HttpClient.webSocket(
                  urlString: String,
                  request: HttpRequestBuilder.() -> Unit = {},
                  block: suspend DefaultClientWebSocketSession.() -> Unit
              ) {}
            """
          ),
          0x83cdc0e0,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijg4uJiEGILSS0u8S7hEudiz63Uyygp
                KRDi8QCSzjmZqXlACSUGLQYAc+lpKD0AAAA=
                """,
          """
                my/http/DefaultClientWebSocketSession.class:
                H4sIAAAAAAAA/41OTU/CQBB9s1UKVbSoJPgDjDcLxJsnozE2wZhIogdOCyy6
                tGyNOyV643d5MJz9Ucap/AFnkjdvPt98/3x+AThHm3Cy+EhemF+TazPTZc5X
                uTWOn8x4WEwyw0PjvS1cCCLEc73USa7dc3I/npsJhwgIrUFWcG5dcmdYTzXr
                C4JaLAMRoAoaFYBAmdTfbZV1hU17hPZ6VY9UR0UqFjbrrFd91aWq2SecDv71
                majJ8eatDG4GzjImRMOifJuYG5sbwvFD6dguzKP1dpybS+cK1iy7viZa2MLG
                FA7/8ABHEntydVu8NkKQIkxRT9FAJBQ7KXbRHIE89rA/gvKIPVq/zvWZR1YB
                AAA=
                """,
          """
                my/http/HttpClient.class:
                H4sIAAAAAAAA/2WOT0vDQBDF32y0f6LVtLZQv4TbFm+eRBADFUHBS07bdtVt
                Nhsx06K3fi4P0rMfSpzowYMz8ObNG/gxn1/vHwBOMSD0ijf9xPysr0QuvLOB
                myBCsjRro70Jj/pmtrRzSSNCd5qX7F3Q15bNwrA5I6hiHQmNamnXAgLlkr+6
                ehuJW4wJg+2mFauhilUi7mG43UzUiOrjhNCf/n9D0ELq/AUnORPiu3L1MreX
                zlvC8e0qsCvsvavczNvzEEo27MpQNQSMHfyWwtGP9tCXORbqrnQjQ5SimaKV
                oo1YLPZS7KOTgSoc4DCDqpBU6H4DrRvzVjABAAA=
                """,
          """
                my/http/HttpClientKt＄webSocket＄3.class:
                H4sIAAAAAAAA/41UXXPbVBA9V3b8oSjko19JKG1pTeskUDlpaQGbtqlJiMAY
                pm4zw+TpWr51FMtXRboy7Vt+S9+ZoTBDGZhhMn3sj+qwVzaOM3UTHrx3tXf3
                nF3vkV6/+esfADfxDcOl7jN7V6kn9haZqu8Jqb5VhZ9FsxG4HaEKN7JgDO1a
                J1C+J+29Xtf2pBKh5L5d491mi5dH7x7H0lVeICN7c+CtVmqjFA/ET7GI1P3Y
                81siHNY+kp4q3ykzLL6bKIs0w4XjybLIMGQqHsHdYUgVl7YZ0kVnadtCDqaJ
                CUxSQO16EcPl2kmzUz8ZT/aCjmC4WDxuDs1zpRaEbXtPqGbIPWqKSxko3m+w
                Hqh67PsEaBY0e0HSUw5zRwcaDuxIFRKE50ZZnGY44+4KtzPA+IGHvCsokeFa
                sbbHe9z2uWzb3zf3hKvKI5GGBmmX9fBncc7EGcwznD9uDIarYyCX3g7Rot4N
                k8V5C9OYMWHgAsPkyI6zuMSQc+qNh+v16gbD1BEBWLiMK3l8iAKD8WSVYW4c
                c67i+smC9U7zmmRJF76XJ2+FYfY/yO+E4i2uOJUY3V6KFM+0yWsDBtbRToou
                n3raK5HXIs7Cwb5lHuybxoxhGvMGuTMH+4tGiS0bJWPLfPU8Y+R0VWuNRqtw
                Gchn3SCOSG4EenqcpLKgFmYPddUSj3nsK4ZfimMkOGaDJ7xhJ9yvDe/dIAxi
                5UkR2dVAkhMn8iw7/2vnFr4ETTl12Or1Dg2RrgYtoVcVuNzf5qHHm754qA3D
                dI3I6nG3KcJBpPAgJuKucGTPizwKDeW8fvi6MFiOlCKs+jyKBD1Ob0jXDyL6
                L2ipu0GLId/w2pKrOCRMsxHEoSs2PU2wMCDY7sOPoKJEApmgJdEnDQtaMbT1
                NP1IRRRZJ69AGbRHZJbTL2G9SHRyn6zVj2IqqZnV4qZMXVGm06AzuzJ36k8s
                6BID1SSZPh6UqsvP9lMG5dqbwyLdf6V9UiKlYWadUN8f9HFvgGotrxzggz9w
                8Td89OsRaByBtobQFq7iGt3nUBxOdS7JASb/hvHjSyz/jo9fJIEJbJA1Ka2f
                MI/N5C+p4C6+TuhS2ErOe3DovE2Zn1DV9R2kHNgOSg5WsebgBm46+BS3dsAi
                yvpsB+kIn0f4IsJihOl/ATDfZ7xxBgAA
                """,
          """
                my/http/HttpClientKt.class:
                H4sIAAAAAAAA/+VVzU8bRxT/zXr9CRhjSLGdNNDglgTi2Dj0E0pDTKJYNbSK
                KTlwGtsTsrDepbuzNLlUqIeq/0J77L1SlVPUQ4XIrX9U1TdrYww2uKoitVIP
                ++a9eR/ze2/e2/njz99+B7CINYaJ5ov8Myn384+IlExDWPJzGQZjSOzyA543
                ubWT/6K2K+q0G2CIfiNqVbu+JyTDDzcrvd5LlT1bmoaV3z1o5p96Vl0atuXm
                H7a5hQH6Ykdftx3bk4Yl3HzJtojxuDJYulU5D2yJseI/gbKcO+P0WHztCVfe
                9wyzIZyO81eWIZdWBsHuirUmnnLPlC0MT07KVRWuq+DnBuS3nDt38HxvviuD
                itQTpF/RkK3Yzk5+V8iaww1KhluWLXkrsQ3PNHnNFGQ2c5mZLZUlWQ11HRhG
                jCFS3qhurm6UHjCMnEEzjGGMRDGEOMNoVj4z3GxXU030u0iGsNO6HYbpQQ3G
                EKyZFG2gaVHBztbt5r4plNzlcFHzMfzUv9NOy1uVjmHt/DtjsPZmwP3fByPq
                OWarUgzJ3uoxjHUaNttoJcXwy3+kMcq9GfVNcrrfn/90ErN3w7jOcKNPUmfN
                /HmejmIK7zBcvzy5MGaGEUQqBg3vUhlPclkXkje45ARLax4E6HFiikQVAQPb
                U4xGyueG4grENRYYix8dLsaODmNaQotpKa3Fxv0lpXW+c6KWUBsRLcOJyWgF
                NqcVtGIoESBeL6YjWiKYGU/qSa0Q9ikrhI5/DmmRSHE2Ec3MRJi/O5SMJSNt
                /XAylNRTrDBSiLUsHx1/f+/1K3Z0qMREPPOk+6TJNx1/9Pg7TaeM0qoqRaYK
                ljwpbPdf8dplQ01Xd+Lz4LkUlhrJE+fNF/vqHegJqv6fs39zuukROG2gO3s0
                L3rJbgh6ASrUwhtesyacTfXgqHPsOje3uGMoub0ZrRo7FpeeQ/zVxx71e1OU
                rQPDNUi9evoi0at2Xvsld3hTSOGcMRupSl7fW+f77QOGy5YlnJLJXVeQOla1
                PacuHhpKl26H3Oo5DgvUybrqUgSQptYOkfQJSRW6BbWvv8Tor6p5sUQ0RLvA
                CJaJzrX0SGDM99eRxLiv1zGBK+ShuLcwSZE/9SOEsdKOEaH1M/rSOglRf0bO
                UwUm1QazRXvBC8CM+2AWW/ouMBlcbYNRsLQOrEAHlt6Gle6BNRW8EJZPE1Fc
                w9vEK3CrdHSY1tSVYPDbHxF7iRtHyK7Pzd/OpfVXeK8F+J5fYRb3kcd9FKPk
                Nkb8KEmrJMco4JSPPoX7vtPHKNG6TfuzdMjNbQTKuFXGXBnzuF1GDnfKyKNA
                Buoii9tIuAi6uOtizEXGZ5IuFl287+IDFxMuPnTxkYtJX5VyEfoLiu6pgksL
                AAA=
                """,
          """
                my/http/HttpRequestBuilder.class:
                H4sIAAAAAAAA/3VOwUrDQBB9s9GmjVZTtVDFbzBt6c2TCmKgIlTwktO2WXWb
                7Ua7m6K3fpcH6dmPEid6dgbevJkH783X98cngBG6hJPFe/Ls/UtywzBRr5Vy
                /rLSJlfLEESI53IlEyPtU3I3nauZDxEQOuOi9Ebb5FZ5mUsvzwlisQrYlWpo
                1QACFXx/0/XWZ5YPCN3NuhmJnohEzOyxt1kPRZ9qcUg4Hf//DkewY7sWroxW
                1p8VnhDdl9Vypq61UYTjSWW9XqgH7fTUqAtrSy+9Lq1rcAC28FcCh794gCOe
                A3bd5m5kCFKEKZopWoiYYifFLtoZyGEP+xmEQ+zQ+QE2dI8XQAEAAA==
                """,
        ),
      )
    var encountered = false
    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            val arg = node.getArgumentForParameter(1)
            assertNotNull(arg)
            val psiParam = node.getParameterForArgument(arg!!)
            assertNotNull(psiParam)
            assertEquals("urlString", psiParam!!.name)
            encountered = true
            return super.visitCallExpression(node)
          }
        }
      )
    }
    assertTrue(encountered)
  }

  fun testAmbiguousCompose() {
    // Regression test from b/371686443
    val testFiles =
      arrayOf(
        kotlin(
          """
            import androidx.compose.runtime.LaunchedEffect

            fun test(somethingToDo: () -> Unit) {
              LaunchedEffect(key1 = "some") {
                somethingToDo.invoke()
              }
            }
          """
        ),
        // Borrowed from compose/lint/common-test/src/main/java/androidx/compose/lint/test/Stubs.kt
        // Then replaced EffectsKt.class to include bytecode generated by @Compose compiler plugin
        bytecode(
          "lib/compose-runtime.jar",
          kotlin(
            "Effects.kt",
            """
            package androidx.compose.runtime

            @Composable
            fun SideEffect(
                effect: () -> Unit
            ) {
                effect()
            }

            class DisposableEffectScope {
                inline fun onDispose(
                    crossinline onDisposeEffect: () -> Unit
                ): DisposableEffectResult = object : DisposableEffectResult {
                    override fun dispose() {
                        onDisposeEffect()
                    }
                }
            }

            interface DisposableEffectResult {
                fun dispose()
            }

            private class DisposableEffectImpl(
                private val effect: DisposableEffectScope.() -> DisposableEffectResult
            )

            @Composable
            @Deprecated("Provide at least one key", level = DeprecationLevel.ERROR)
            fun DisposableEffect(
                effect: DisposableEffectScope.() -> DisposableEffectResult
            ): Unit = error("Provide at least one key.")

            @Composable
            fun DisposableEffect(
                key1: Any?,
                effect: DisposableEffectScope.() -> DisposableEffectResult
            ) {
                remember(key1) { DisposableEffectImpl(effect) }
            }

            @Composable
            fun DisposableEffect(
                key1: Any?,
                key2: Any?,
                effect: DisposableEffectScope.() -> DisposableEffectResult
            ) {
                remember(key1, key2) { DisposableEffectImpl(effect) }
            }

            @Composable
            fun DisposableEffect(
                key1: Any?,
                key2: Any?,
                key3: Any?,
                effect: DisposableEffectScope.() -> DisposableEffectResult
            ) {
                remember(key1, key2, key3) { DisposableEffectImpl(effect) }
            }

            @Composable
            fun DisposableEffect(
                vararg keys: Any?,
                effect: DisposableEffectScope.() -> DisposableEffectResult
            ) {
                remember(*keys) { DisposableEffectImpl(effect) }
            }

            internal class LaunchedEffectImpl(
                private val task: suspend () -> Unit
            )

            @Deprecated("Provide at least one key", level = DeprecationLevel.ERROR)
            @Composable
            fun LaunchedEffect(
                block: suspend () -> Unit
            ): Unit = error("Provide at least one key")

            @Composable
            fun LaunchedEffect(
                key1: Any?,
                block: suspend () -> Unit
            ) {
                remember(key1) { LaunchedEffectImpl(block) }
            }

            @Composable
            fun LaunchedEffect(
                key1: Any?,
                key2: Any?,
                block: suspend () -> Unit
            ) {
                remember(key1, key2) { LaunchedEffectImpl(block) }
            }

            @Composable
            fun LaunchedEffect(
                key1: Any?,
                key2: Any?,
                key3: Any?,
                block: suspend () -> Unit
            ) {
                remember(key1, key2, key3) { LaunchedEffectImpl(block) }
            }

            @Composable
            fun LaunchedEffect(
                vararg keys: Any?,
                block: suspend () -> Unit
            ) {
                remember(*keys) { LaunchedEffectImpl(block) }
            }
            """,
          ),
          0x31e832e6,
          """
        META-INF/main.kotlin_module:
        H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijgMuSSSMxLKcrPTKnQS87PLcgvTtUr
        Ks0rycxNFeJ0TUtLTS4p9i4R4gpKzU3NTUot8i7h4uNiKUktLhFiCwGS3iVK
        DFoMAGDKMaZbAAAA
        """,
          """
        androidx/compose/runtime/DisposableEffectImpl.class:
        H4sIAAAAAAAA/51TS08UQRD+enbZx4iyLPJGQEFZQJgFvS0hUYRkkxUNS4gJ
        p2a2gV5me8h0L8Eb0Yu/w3/gwWg8GMLRH2Ws3ocgxgAmM9VV1f1VfV1V/ePn
        t+8AnuIJwxxXlSiUlWPPD2uHoRZeVFdG1oT3Qmqy+U4gVnd3hW+KtcMgCcaQ
        qfIj7gVc7Xmvdqq0k0SMIbEklTTLDBO50kFoAqm86lHN260r38hQaW+tpS0U
        prcYPlx1ammudG1qZT88FIXZ6wM2hK4HprDcoDJRCqM9ryrMTsQlUeBKhYY3
        6ayHZr0eBAW6n2ggU0gzjF6gLpURkeKBV1QmIrj0dRK3GHr9feEftPCvecRr
        gg4yTOVKl+tXuOAp2yB7xKsTt3HHRSe6GGI5a3eg20UcWYbxqyrciTTupuGg
        lyFu9qVm8K5fHdtpuvH7qzp00wb9R38YutssXgrDK9xw8jm1oxgNMLMibQUY
        2AH5j6W18qRVFhii05MR1xlwXCdzeuLS19Av/KcnKWfg9GTRybPno9n+jDPU
        k41nnXy8ITvysbOPCSeVsDKT3Bj71/6bs3dxq1E8m3mRWT7ZNu/zrjDkb1oy
        hoUbV43ms5179dgIGslQtUlsvm0EdZsAPX9gGNJluae4qUeCYXijGbuojqSW
        FPnZ+WOgUVoJK3SoqySVWK/XdkS0abPby4Y+D7Z4JK3dck5ejvX7FfwR1C2H
        9cgXa9JiBluYrb+yI0/jHG+0Omunm6xZshwM4jGtCfKnmoNADyWBGObIKtG+
        Q2tmNut+RWbmC3pmZj+j71MDOU/yDp1MUAwXQ+ii1SNfXxODfgzY2SLN5mOt
        fEliAiRZK6GDhYacwSKtK+QdIgLD24gVMVLEvSJGMUYqxou4jwfbYBoTmNxG
        SmNA46FGWuORxpRGTmNaI/EL6pV/cp8FAAA=
        """,
          """
        androidx/compose/runtime/DisposableEffectResult.class:
        H4sIAAAAAAAA/5VPzU4CMRicrwssrIqLv+gDEL24QEw8eDJR4xqMCSZcOBW2
        mMKyJbQQjjyXB8PZhzJ+C09g0kxnvp/O9Of36xvALc4JkcySudHJKhqa6cxY
        Fc0XmdNTFT1qy1oOUvU0Gqmh6yq7SJ0PIoRjuZRRKrPP6H0w5p4Pj+An2w1F
        8K6ue4RaZ2JcqrPoTTmZSCfvCWK69NiacqjkAAJNuL7SuWoyS1qExmZdDURd
        BCLcrAM+IhTlUX2zbosmvZZDcSma3ksjn24TWp1/foKDsG+wK9mbiWPxYRbz
        oXrWKee/6O7We9pqXn3IMuOk0yazJbZEAdvgBUIRJWYCp1s8wRnfd/y0z51y
        H16MSowgxh72meIgRhWHfZBFiFofBYsji2OLIuMfte9Y6pYBAAA=
        """,
          """
        androidx/compose/runtime/DisposableEffectScope＄onDispose＄1.class:
        H4sIAAAAAAAA/8VUXVPTQBQ9mxZKQ4HwIQIqVkFtA5Km4hcwzDBYxmpRh2p9
        4CltQ1mabpgk7fDk9CfpjI6jD06f/VGON0mRjjry8eJD9t69e/bs3bP35vuP
        r98ALGOVYcUQVcfm1SOtYjcObdfUnKbweMPUnnCX5kbZMnN7e2bFK1bsQ3Pe
        FmHcnNdjYAzKgdEyNMsQNe1l+YBgMUQYtDOz7phu06JNfQz9a1xwb51hLlWo
        257FhXbQamh7TVHxuC1cbavrZVbTJYbsaai14/U3RLu6HmxSTi4Qns+QPO2w
        BGQMxiEhwRBJpUsJxDAsI4oRhqi3z12GtcLFZaRHiFXDCcPsv3OJYYJ04qJl
        1wk8kUoXfpefsp3E5UFcwhQ9wzkVYhg9jmybnlE1PINiUqMVoXJh/hD3BzCw
        OsWPuD97T15VZ0h12iNypy1LU1JgFEllnfaAPNVpZ6UMezagSDNSJvJ00sdn
        GfSzaxYWCaVCJy+dT+kYUgzxX3IzFE8vrnMnloCKBQY5DLpLdSortfcYLjzT
        EYalFe2mUzGfmOVmLXfkmcKlE+lefS3DalJy74rbG696aOTnAYesFpPH3pa8
        kNSTPZCLV56sFmR9Tl/U9eUVmuRkUqrIa8Lwmg4lE920q2RGClyYL5qNsum8
        9skYxgp2xbBKhsP9eTeYyAthOpuW4bomdcRITlQs2+WiRpW0b1dJnfDuW9xH
        T/xNCIbpnTD3Enc5sW4IYXtG8D4MV7predH6YxU6tWeUqqMfTFH8fiVfp+KU
        MEMfdRgGyGYpsk5WIiurC58wpH6G8iHA3aORdqMPw/RnBHVRgMIoxvySJ6+X
        NU7eOCFZwPnY7wiyg+pHDH3BNMPbE1I5IFICKp84EUK7xP24H2BYgAKm8YDG
        KNJYxMOA4y4ekf3PlUFXBOVDr0ACXd1FJI9reczmcR1JcnEjj5uY2wVzMY9b
        u4i6vnvbxbiLO1ihzb5WS/RpASjzE60KhHSEBgAA
        """,
          """
        androidx/compose/runtime/DisposableEffectScope.class:
        H4sIAAAAAAAA/51UW2/TSBT+xrnYdbtNGm5tYaFAgJZC7YT7hkXaLVsRFAoi
        UAn1aeJMy7TOuPI4FY8VD/wHXvkF8AQCCUXljR+12jNOWgo8dIslz7l/Z86c
        M/P134+fAVzFLYY5rtpxJNsvvCDqbERaeHFXJbIjvLtSk8xbofhnZUUESTOI
        NoQNxlBc45vcC7la9R621shkI8OQvy2VTO4wZKZnlkaQQ95FFjZDNnkuNYPf
        OFiqGsNQpPomwdCcbqxHSSiVt7bZ8Va6KkhkpLS3MOD82sz/T/BY6G6YUIbW
        fqi3d+xPqbjanV9KcrYRxavemkhaMZcEzpWKEt5PtBgli90wJK/CbrH9cAcF
        hpN7didVImLFQ6+ukphwZKBtjDEcCZ6LYH0A9IjHvCPIkeHCdOPHRtX2aJoG
        ZLVmenUIh12UcIThj4P1qLy753LFxjEqdf8upbMx4WIckwzeAU/TxgmG0bIs
        r5T3zAarM0ztl5hhbMflgUh4myecdFZnM0N3gZllyCwguHXSv5BG8olrVxiW
        e1uTrjVuuVaxt+VajpUKhk111nhvq2r57O/c9ps8ifdPFDOTlp+tjjrZYm7S
        KWVLlm/7+Xvbr5wvH1hva/ulZbs5Z/t11WcmRZWZxJVfGK7STlF7K3X7Tnpu
        PaH7Nx+16ZAKDanEYrfTEvETg2NCo4CHSzyWRh4oh5pyVfGkGxN//HE/e11t
        Si3J/Ne3yWUo/2jdnb3v3EbqSol4PuRaCxLdZtSNA7EgTbKJAcTST/CowKLn
        w3wWnQy9JrT6JHmmQURzF9/DeZeaK7TmU+UwqrSO9B0wBJfoGGlHCMoELyED
        09bDs6XiBxzN/PkJ489m3+N4D7+/3cVyiToYpWtRSvGmKMYhjJM4RRaKHiAb
        rkBWhitp7G/0pPZ3Mkr0Gv02GwgZXE+BGY29+SZwIw3xcJPoPOlP04bPLCNT
        x9k6ynWcw3licaGOacwsg2lcxOwyHA1X45JGXmNY47JGQWOONP8BWe15VNUF
        AAA=
        """,
          """
        androidx/compose/runtime/EffectsKt.class:
        H4sIAAAAAAAA/+1beXhcV3U/580+Gi0eW45H3sb2ONEyWmY0Gm22I9uRYsWy
        4nhLbGfxSHqWxhrNiHkj28pmE7KQAGExKTi0FEIWMBCytLFN0mASSFpKgZZS
        WtoGmtJCA7SlUOgCmN+9780mjTRjf8n3+Q/8faN337v3nnve/f3uOb/73vNX
        fvP5F4koxPcxrY7ER5KJ6MjR5uHExGRCU5uTU/FUdEJt7j14UB1OaVtTNmKm
        qkORw5HmWCQ+2nzt0CFU2MjE5NwZHVH1hkxX1w6MJ1KxaLz50OGJ5oNT8eFU
        NBHXmvuMUkv3wJyDbdbPk939dXuY9hSztC5dvzseTXVvKNnw2mINI0MxtZtp
        zUAiOdp8SE0NJSNRDByJxxOpiO7EYCI1OBWLoZVvvlZokjZW1Dkxx2VMC6La
        rmRkWO2Pb08mRpOqpjGZauv2uaicKpzkokrTs60PnmZqTVtsMiw2GRabsnh4
        aw0Am8ZTXaH2Oju5mSpTwn7vYTWe2pmKJAHaktr+/v6BLLg7U8lofLS7bo+L
        FlG1GHQx06qid2CjJeBIUh1OJEdyObGmKCfESDW0tIw8tIypPOtgb3xE3j7q
        V9BK4YmXyaoahr3F7DI5fIa3SQFocYYw2X3DY5gEFQNzP+7nqqhmcKJUjgdK
        puJnilla1zi3qZmO7RxOTKrdDaV32KFqU7ELWTkL095epU4C5khKHcGE2SZA
        0sioyvT1mSN4J6a0lHcymTgMOngTcfyS3olEUvVeMa5OX+GdjCQjE2pKTWre
        1Fgk5R1RD0bRKDWmetEBLqSmvYmD8nyWafiM9ug7IbocGVPj3mhKw2Dq4Whi
        SvPqJPFqY4mp2Ih3SPWOSAvqiOwZ8cbVI5k2Yhmg4iC8E2OJKvjXxGSJqYfV
        GJNn5p0DnAFRhfu39O7Yce0O01cWf/qLCC6ba2d6WteVXYRrOqOjh0Yn7RRk
        WqYlppJioWPYCWlxWyQ5riaNRdlZWwoshVdtiNrEWgkzdc0ZJWZNaG6sCATC
        CBYdTCuz9vtjMXU0EoN7KbX36LA6KVy2UZedGrFqUgl9fKZFtXWzvXLRetrg
        JDNdifW7LoqIvYGpuraw+xtpk5O6aXPRFR7A9O/NNaKnpe43a32+eOG2L6UV
        a3qy0xdhChXg5PbaQF0g1N4Tbg10DITaC1G0/+LZExLs2WrqW3ociWrF5s21
        SXVCnRhSkwXXwiBiSCbmVheYcpH9ttN1Ij3sQP5KG1NH9kRiU+pMxhmdXLSL
        dosuwNEhZiUSBz5M8+CTnjtfpjms3EB7HbCyj6nhAjra6EYsilE11TsxmZp2
        0c2020k30S0Yv2S0+ycmYzaKFE+gAWPVDDtpiMQcTk2OYJHumDlPBecWPQ/S
        qJioMaaaOWKSzMOXlxKRhL1DNC7CD+JmrS/qi/gahyPDY2pjRkz6Zt6pL4DJ
        Oiyc9EUPM7lnu4lIpJuKqanGrGTyScu+gOxmiabk0YmWB/UapgpfaiyqGe1k
        bTSOkaIj8oQBqxmhHuOvmH+KbXSUabxQPHjLos833ozRLql4tLtpG7YR4cLx
        yB+sC3SEejoCnW0Dba2FAsUdFx+ROloRkY6Z+paN31Q6K4M6OXC4ozQo3jIq
        vP7WjH9JkeO5FWs/DtkzFzn8rXXBYLAn0BIItAy0dRbix30XzY9gMAB+3G/q
        W77v5dL50arzA4d9tfvfOvC/cBHGLylkn6kMPMjUNocMCbZ19AQCnZ2hgVBH
        IVTff/Gotokt74n0g4upVDTWvDGZjExrNnoIOhT3PX3tQaa6QhPcX1fgoos+
        RB920u/RSdNrq95/BDkxV9yIu+nKPkPIu4vfh37QaZVuPh+zQvAOzJI5qiyd
        GOXZ6iJpUDayy0woiwvy85+8Vi4TZNoPqC8MpcmahQXuGR6kt8SyjaA8JvBx
        5NaBCPg2po6UujMOlsz5p4tZAsH1BsJSMgFs46oGI0ZR53S6TX4TjBefkuIm
        YyT9DKlh9u2Xvj3+dv50vImb3xmGS9z6Zm46ZxM8HIkPq9jIFdgFx4xBZm2D
        TV//za7HwCS5TcbaF4erYXpSSML+uhKmx0Wfo6eExHwaYnZzbf7tFNwTPCvG
        m6lFmTpKkaCFN5V/TM8JUYoNSRlk+c7x6OQkqlx0liqEZ5+H8TmjzIz5z4sx
        nR2IMS/Y6bNYDxqs7krIuYFidtEXaKWwfQ6bFjU+kj91jbXzzJyk726p4nUm
        u+glelnY+lLOk7+5H9f68j32Qb6+wuQrujz79Y3EnzrpVfqzYpo4aKM/R/As
        +SZs9BeY/KnsheI7m6CE7uv0jTL6Gv0lJP5QLDE8XvS5QPCinguUHp1evnDb
        l1y8MvVt+akFZJq1HEUWaw2EewKh1lB4oK1gTv7bi1wtrYEQVst3mJZgDW6c
        nIxNZ2ZA3Kd6NCViSl3BachvhxXxD/SPYkW8Zrpt0Wuh+bbn+Q7p2+p/ys1W
        841TCkE30j876XX6PpwvLB1nLEfsKFwR/e6Nm/aV4gqyf/FWNvq3N2ejWvpy
        +NabMdqlt0DWNN2kStk6e4Fgr9ra2tYTaGsNtQyEQ4WWyL9f7BJpFVvV/zTd
        Vr3v1lL51PpW7VBL58AP3prxLz1W/OqVk19mai/MCmxSW9tCPYFwa7hloL2l
        EC9+ebG8aAuCF/8LXnyHSuVF6KJ2pqWD/qWLMH7pIfrLJ287M2cibG/vCbS3
        BToLP3Jgulg029vq7KxAPs25KZyBZRvTyuGkCu2kOx8V85A/bUyx0lJaCY9x
        SwCJqTo92vapoVhUg7MbJ6O4vrhA52sSQzbGhJm2qtNMywrZRxMfartd7OQy
        BzvYBaIXvx/Rx8YVMA1RkfvhQJE+3aXoDF9vDNDEoTe4iheU0Rvshnop7PzW
        lI0XQeKK+8BuLjIVgzvba+e41byXd+mn8YWnHYpFTYnHA6KbixfzZU6u5iXQ
        A7Nez21Mjk4Jf7Nv6LgGTsy8La82Bf0RxW4vlfCmyZePr3ciMu2NJ1LeaHw4
        NoUtbERsWWHaeygxZOdlTl4qXtEtzLqwayyZOCLctPFKplVFb8XGq8ChYf1a
        9p1iJBabFh8lDBSwLN4F8Rr2lfFqXov9Q1GWCkyuwNZs5krpL2mllLIMXFzH
        9U6u5QamwJwrK/syJr+7jRuZtl6QEp3XYaFGudnJTdwyFwT5w0OLtusxSCrS
        nABUJNqIlzYh4+nS/C1vmQqGYpGJoZGIKHWILzGK05ypanjG3THZs19zLC7M
        WqZDb8onSP2lRcD3F/1KqaEU2ErLVSX5ZGryPtuDrJTzYDK/RV1bW7gn2AY9
        MRAutLnj9UzdJTxiKGzdF7DxlQCqf3Dnro2Dm3uZ1s99a8WNYXVt5E0O7uHN
        xR5HtNi41/Tqyp9hF2aNxg8nxlUXb6HdZdzH/S62sNVJCm9laiosnebwAMtj
        ad7j0pl0cyLpZPhZFxkeVjXNh2v9uJRELCv4tN03yRSc7wFQ4Wf0TMvnNcvU
        cqEmXbyDdzowM7sA2rrhmPFpRtOFmbHx9WIXvNLJN/BeZPeZjQYT28XTzt5k
        MpHMf79rPKFjqsnXO/k9FqQX0TYEiJFIKoIOysRhExGx+OMQf6DFeFwUIKuU
        o1FRQgxURgJsevzc8RHnueNOpapCHpYoTsWOX5WSOU3/5GVx0W5FYQGOphkN
        rMZRv55rTj/WXF5lqVFarMHLqmw1C91mt9Jikn8dLfaXHrEqducWmzhWldW0
        GC3tVa4a8xJuKS/ap6Kma1Yfe1Vlib2raq6ap7e9akGJdtw1Gww7i6sW1rjc
        dju7Zc+WRatLs1Bds6tqcQmeXFF1Wc0aYR02PG6n2y5L1pYat9UYcUme3aU1
        18xj90KtLavpmWXtQm0srxk0bMw1VxdqcUVN2LB4oT1X1iyt8sqeLuCTbr3K
        qF1ds6RqDWo9QWuVD8e1OF6O4xVbuGZNVa2sWWi3VtXVlMuea1tWSev1W166
        T6Jb1bC6AmsHDfmGl+7aJS5hfeCagksmXDJnL5lntHrpk1a/WK0IuCRChLHg
        cz/1ClzwK0rkjLSh3qMpNa7BUtrirmkZUWeNFOyeU6DMSP7eIrvUQtbFR6nB
        ee8D8jdxJPsWcTPOtW7x2ZPe5JakGlMjGmJ9zdxbKBsjGTmzuZ2pPlenRI0k
        0rxTvmK5Sh2aGs3Mj/ikUb5yZKV+57aN23PMOLdKG876nd50qc/Z4A14c5oU
        T/boEfSmVc28XbJvONGn1Zv/arVYN9kOPesHnIE1AX8bNvABZyAQDq8J+sNd
        beGW3JNwzkl7MOekI++kI+ekM5Q9CbfkWAsH8k7Czo7gmlZ/CIY70sVwSzg7
        iXL6L24mxb2FOtYExO34cQ8dbbIcRjkYbBXl9qAot3WKcocotwbaZblDlFvD
        otwZEuU20Rc3IsrtwibuA+W2tnZZDmfttHf4Q+k2LWGU63udkEk7dPf644ej
        WhSTvzH7KTxTORaGlorEU8a3aObNiREcynemIsPj2yKTuwRcYPkAaDw4JYSW
        ccU9kBiOxPZEklFxblx07IyOxiOpqSTKvpnjbk+/WM1zwJn9aJnJY/TZU8BT
        V388DpkXi2iaKvrpa6QvKsZdVGjBUIAUMkNtmKhGqClivhVnVoQxB441vJ9v
        hCa5TSgTWjej7qacuhfz6irJQxacmfl2nE1iBKFovGeo6iS53AuWKe6FZ+iy
        hvqztFwhedV6hlY9LfzgO/DXTEplJd+JUgV6uqiFquDpSgryMWFHt0araY2Q
        Tij5aC1GFqXL6Qr4dFz6ZMv4ZEdcfrvwy2Q4iLmRXT1US3VklW6OG276GtwB
        d+sZajfcDcDdTrj7PHXvda97jnpO01VfyLgKi5dJT8U4FprK8dAHD3vlML6M
        h748D+v5Lin+3oE/btQ34Jpfo0ZqUqkZCrAk//v0aeZ3w38Xrh33u7e4r8n6
        v2W54h6A/373Nve1uOzvsvgx8zsVCltNYZvHcpauZ+qym8KOauvDVOGxP0P7
        n6MDj9MKU9j5PA3tbThNaleZx+IpO0tRhTxlT5DFA5/O0VCX2X+GJjIQipMs
        jD1kuew8BclpI7ONpm3kYRJ/GOeYjJqNOJ6nhbn1NhrKaSEndgPoZKETtIw+
        SE1KC3UpAbpGCdI2nO/G8XqllQ4oIVKVNhpF/Rh+UdTdjj7H6CEJyCSVob2d
        4ph8J12Ps4S81oW5fhuuOagdJEvimp2aqJs0XLMBjg2UAnAWWZqiwwAD80tH
        0I5kSQDMsiQAVmRJAGwyAL56FoTVZgO82UDeagD5GIAsx7X7G923u+9MA1nu
        vr3arLiPA8lG99slko1d1kYdycYGeTgetpnCdo9VR9QB+KptAlFHDqJlAlG/
        QNTlsXpcOqIugahDR9TSmItoYx6ifWRZch6zViYRmxfU/CZz4nqGltNZalfC
        tFlpxz100B6c34zjAaUTvnVRXOmmSdS/Db8k6u5Bn3fS5yWut4LxezC3d2HO
        y+iAgauLNhu4OsFBHVeBsI6rnUIGrlZZErjaxHRncL2f3oESy5JAWJElgbBJ
        lgTCZgPh22YhvNSSQbgwzncbOH8ZOFfg2kmP2X2v+51ZoO+ttijuBwA0Kt4l
        kfaYu2wes461x2yA7TH7DdTF2vXYdNSdgLjaLlB35qDuEqg3CtTLPTZPuY56
        uUDdqaNu9ZhzYZdnWdwH0PA8XU2uNKjzQL9odqs50LfSawjq36VeZR1dp6yn
        iLKBRnAewzGuXElHlB66XdlId6L+GH7HUfcQ+nyYvifRvxvLZATz/m7g4cLa
        1tEvp+sM9Mto0EDfCRR19B20yUDfJksCfbsAIYP+yQz6J+k9KCmyJHhgkiXB
        A7MsCR5YDB7cM4sHq6wzeFCYDQ/qbFC8MFqJa0/53e9zfyAbvt+H8P1BcKG+
        /sUz9HCX2RS2mMJWv/sj7j9AI5O+4s0vInjbqx2P0HKPudoe7MLC93uc6aBw
        t52fOP+6v8tBCAkeh06UMrBCDw9lOUQpzwR8hA1PhU6UCkGUMkKSEAGe5gj3
        I2RDuPeKcP6pDPCfYvEPKfE8prB8Vo1ezGHPgtmNJI88ueTZQ9WYeIUuYxPV
        K5uoUdlMLcpVdKPSi7DRBwJdDeJsoWOovw/H+5V++oByDX1I2UonUf8wfh9B
        24+i78dx/AxsfY7NklQfBgrHAOJHAW45Al45/aEk0P2g08dQqqAY6PVxSbko
        6PWITCQHDHqJ/ymxgT4Bijjwu4MexTWrJKEeZlaDxI/R48BalJ4QBBKY0ydx
        jWQpnVKeyqSUp/JSynsLpxRHAXKdok8b2uZJDCPYe6DB/eRZekahgQb3H52h
        M8vMdz5MLsST55keppUG5Z6E4vmTtOJ50VA8aPRFKJSz9GWmva+Iylf31i87
        TV85R189S3+l4GbM12fJ4CTLigrXsI2+Rhl5RNyQI48O0Dfpr6WnBzLy6ECe
        PPpMYXn02QuQR6foW0a0PWVk1Qf87m+7/y67vr6N9fX3Qh6dpe9iVZj97u8Z
        MsmalklFkqpXJtXX9yIwn6Z/KZxWX4fomlso9UmhlE6rX9XZ/kZubF2Wn1bT
        TWz0+szAugla2cLdtITXkY/XU1gZoE3KNtqhDNJunN+CY0S5lg4p2ymhXIe0
        OkBJ/DTU3Yt+9/OGTGrdjXn8VxlII5nUuglTr6fWKyHt9NQapvVGam1FwtVT
        qyjpnF8Jaf0D+qEMmg9kwuwDEn6WpTTTH8hj+t+ULp5O0Y8MmF82kuqJRvdP
        3P+Rzak/EeLpp0I86ThbGt3/ZYgo20wRVSSdemU6Bd4Wv8C7YEJ9HdJsbhk1
        IGXUpmyinBfyWa3mRH0QV6+ltbydrlR20FZlJ+1XdtHNOB/D8ZCymzRlDx1V
        rqdbUX8bfrej7r3o9wG+LpNSb8a0/kzGt0OZlLrVQL2MthioC/zXGyl1nYG6
        TZb0lOpFnUBdxLcTGdRPZJLrCYm/IktpaXUiT1r9+EKl1Sn6ucGC7xvJ9FEo
        qF+4/ydLg18IafV/UlrpPIDucf9/WmPZ59FYYuF77Dmp05GXOr0ydYIU1kZB
        CuyiZiZPQQrbvCprh1RZ20UGNJfAjEUFG85BDitHaCkPUS0P06ByA+1T9oIQ
        +2gC50dwnFb2013KjUiXNyFd3kAP4Pcu1H0M/T7BI5IcD2JlTWC2fyVT47RB
        jgraZ5DDhZ2VTg6hvNYbCXGrQQ67LAlyOJDk7pLkEPupRzPkeDRDjkczyutR
        SROTLKWV16N5yuu/L055naJfG8prM4xW4doLfvf5hcxZspxfrixkU05uMDSY
        EGAQXlkNJgOG5UVkBke1U2gwS7Uj2IW44RdbZj2m3O3QNZgTGqxMSDNBJBdY
        o0cXVw6RKnKySaXH6anUiVQpiIQdfpdjPhU2QTaQqFYkik/lsiJPiFUUqsyK
        rGVpLVao3Ww5to8Wg19HqYanaRXQCCk3Y0d3C3bqB+igEgHPhuiwMkz3KiNI
        L7fSCRwfUlTIr4P0CWWUHkP94/g9gban0PezOJ6Bvef5Nsm7RwDP/eRkM1Cv
        EOtDSrIy7AV0SVYJTjVLSVYOzgWlJBPBS2dgGY2Cd0KSOdHqmJRkNiTblBGo
        /OCikGQWWXpCCrZV9JFMynohI85eyKSsFzIp64W8lPWbksWZeKuLbkKc/RjD
        iCg5WP8Ml5/jyrO8UKFXqAmC9Ax7tvkHTWFz4/O8dO9CXn6aV5xj71m+XMFa
        85+jN86w/wla3CAJuu15btrrrz/NgXPc+lSGEYtJ6ToPAWWSEHoAHLGNWyV0
        YaGJ+DxVKkxLFYWWKSbyKmZag2MdzpsVO5Q1A1LwTimjjYqLtinlEpYQvF5K
        1RwSMZuWQHG0cRhLdSPuVEyemJ5BbkeJZKlDiDw5QeJ1tthyO4xJKlfyJsnD
        ndxlRPJ7YFBE8mOYhIaFvG4hb8CSW26+8yQ5n+GrznHf1oYuC2qulkG8QT7H
        mhGqs1Hah/VWf5avYUTjNzyWMzxQMFJza5etAavKY+uyyqM1O5u1ZPoVmH4e
        Sbzcxn0y1oqlQ7kRWqwfOb99WEROpYEWKrWYXz/mtRFzOkbdSpT6lEPUj/Pr
        cdyrjNOQEqNRZQKujGHJjNE46qbR906lMaPI+qmCt0mi782E325qMsJvBwWM
        8NuMTKyHXz/ytB5+RUkPvybk/UGJ2nIakrgI/h2DdtCfqB7LCPJjuYKcu9Po
        yS1zGr0F5rxAi39VHt7O1wFsQe9l0hCR+RneLaaRs/sBsU54DwSi3nKNpAxR
        2fN8w97TvO9Z3v30jA79CPt347gavr8qGy/hHmNFVdIb7LQ6eIod7MT5PdLn
        w3wvDr97HfS710Glvg4S281nwLqbwbVb9pOpnw/0c6Sfh3i4H0pI7eeDPLqf
        WOMxju6nRo33a3xI4x0a79T4Jo1qxSmt1nhc45jGExrVaXREXuzT6B0a3arR
        ezS6W6NPyosPanRKo29qHNc4ofGkRj/X6EcafUujX2v0aY0sGr9NI6vGKLRr
        9EONOzRaq3GnxtdqnNS4S4M3Ym1WwO8j+B2V/k3/FubOSLIrSwAA
        """,
          """
        androidx/compose/runtime/LaunchedEffectImpl.class:
        H4sIAAAAAAAA/5VSW08TURD+znbZtitCWQHLRUSLUKiwhfhWQqJETJOKBpSY
        8HS6Xcppt2fJXhp8a/wp/gMfjMYHQ3j0RxnntEUQVEKyO7f9Zubbmfnx89t3
        AE+wylDgshb4onZsO37ryA9dO4hlJFquXeGxdA7d2vODA9eJyq0jLwnGkGnw
        Nrc9Luv2q2qDviSRYDDWhRTRBkMuX2n6kSek3Wi37AMqEQlfhvZW31otLe4x
        iOtQ68tnAMcP/DgS0g3tTZ+YyZgrxDngLTUubZQKlcvEKKh65Sp+ULcbblQN
        uKAeXEo/4r1+2360HXteiUGPeNhMIc0wc4GZkJEbSO7ZZRkFlCycMIlbDGM0
        GKfZz37NA95yCciwkL/K4kJkVxWpE6tB3MaQiUEMMyTyyh/AiAkdFsPsdQMc
        RBqjaWgYU7QPRciwXLnBGulva9eN/6bT/9vwGUbOUC/diNd4xCmmtdoJOj6m
        RFoJMLAmxY+F8opk1egu3590Jk0tq5la5qRj0tO1uz69KS110smedNa0InuW
        s6Yz2mQ2xSzTSlm6pRUHirplWHqWFVkxcfrR0DLGzvz/MO9OP+iE06l6UhFY
        Y4qWdUb/fPYX1vOP0RDE7M06XGlGDOldUZc8igOXYWqnt5SybItQVD336fkt
        0i43/RqBhitUcztuVd3gDSeM4uE73NvjgVB+Pzh3udbvM/yjqLnrx4HjbgmV
        M9HP2bvSHat0T3p3GZY6L/IWydMwgSXSBsVTvVXRpRpIoEBehb5rpDMFy/yK
        zNIX3FkqfMb4p27mY5JDhDSwBRMvMEx6mWLjvRzcRVZtnyzVj/X7JbFCOsn6
        DTXYXZlHkfQmRSeJwNQ+EmVMl3GvjBncJxOzZTzAw32wEDnM7SMVIhviUYh0
        iPkQC13bCDH6C4u/Nd/9BAAA
        """,
        ),
      )
    var encountered = false
    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            val resolved = node.resolve()
            assertNotNull(node.sourcePsi?.text, resolved)
            if (resolved?.name == "invoke") {
              return super.visitCallExpression(node)
            }
            // public static final void LaunchedEffect(
            //   java.lang.Object, // key1: Any?,
            //   kotlin.jvm.functions.Function2<
            //     ? super kotlinx.coroutines.CoroutineScope,
            //     ? super kotlin.coroutines.Continuation<? super kotlin.Unit>,
            //     ? extends java.lang.Object
            //   >, // block: suspend CoroutineScope.() -> Unit,
            //   androidx.compose.runtime.Composer,
            //   int
            // );
            val parameters = resolved!!.parameterList.parameters
            assertEquals(if (useFirUast()) 4 else 2, parameters.size)
            val key1 = parameters[0]
            assertEquals("key1", key1.name)
            assertEquals("java.lang.Object", key1.type.canonicalText)
            val block = parameters[1]
            assertEquals("block", block.name)
            assertEquals(
              "kotlin.jvm.functions.Function2<? super kotlinx.coroutines.CoroutineScope,? super kotlin.coroutines.Continuation<? super kotlin.Unit>,? extends java.lang.Object>",
              block.type.canonicalText,
            )
            // K1 UAST creates a fake [PsiMethod] from deserialized descriptor.
            // K2 UAST finds a matching [ClsMethod] from the jar/class files.
            if (useFirUast()) {
              val composer = parameters[2]
              assertEquals("\$composer", composer.name)
              assertEquals("androidx.compose.runtime.Composer", composer.type.canonicalText)
              val changed = parameters[3]
              assertEquals("\$changed", changed.name)
              assertEquals("int", changed.type.canonicalText)
            }
            encountered = true
            return super.visitCallExpression(node)
          }
        }
      )
    }
    assertTrue(encountered)
  }

  fun testResolutionToExtensionInCompanion() {
    // Regression test from b/360354551
    val testFiles =
      arrayOf(
        kotlin(
          """
            import pkg.Lib
            import pkg.Lib.Companion.foo

            fun test(lib: Lib, other: Any) {
              lib.foo(other::class)
            }
          """
        ),
        bytecode(
          "libs/lib.jar",
          kotlin(
            """
              package pkg

              import kotlin.reflect.KClass

              class Lib {
                fun foo(x: String, y: Any?) {}
                companion object {
                  @JvmStatic
                  fun <T : Any> Lib.foo(klass: KClass<T>) {}
                }
              }
            """
          ),
          0xb1ca39f1,
          """
              META-INF/main.kotlin_module:
              H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijg4uJiEGILSS0u8S5RYtBiAABz6lUC
              JAAAAA==
              """,
          """
              pkg/Lib＄Companion.class:
              H4sIAAAAAAAA/5VUW08TQRT+ZnvZ7VqkFMWCeIOqBZUteLekiWKMlYpGGl5I
              NNNlqEO3s2Z32vjIkz/Ef+CTxgdDfPRHGc8sBe8xPuy538+Z/fL14ycA13CH
              YexVt+M1Zbu8EvZecSVDZYMxFHb4gHsBVx3vSXtH+NpGiiG7LJXUdYZUZW4j
              jwyyLtKwGdL6pYwZxpu/RauR8XYYMsxWDpS1ZjfUgVReJLYDCu2trgQ8jmtz
              Gwz15dad5q+5a/V/+S63WrV6EmDiwGBn0PMeDXrrmmvpUxWzzTDqeDtCtyMu
              VexxpUKjC4leC/VaPwhqpkPTSd1BgeH0D5Gk0iJSPPAaSkfkLv3YRpHhuP9S
              +N2h/1Me8Z4gQ4aLld+7+EGyboJ0amaIx3DcxTgmGDJd04qDEkOubMooJ4PL
              HfbOcOIvo2NYrDT/VO59sc37gV6hLnXU93UYPeZRV0SU2oVlVjde9r8rX/QS
              LcPC/0WjOzpweCw03+Kak8zqDVJ0Z8yAnAFgYF2Sv5aGqxK1tcjwfG932rVK
              lmsV9nZdyzGE5RqydICcz29Spb3dJavK7tmO9fltlkwelQupKauannGcvd1C
              Zp6U9GWX8gV7yimmi9bDTNV5aJssSyzJ3WKwh8O0sUCjPTxT2jwJF7qabnkl
              3BIMo02pxFq/1xZRi7cDkhSboc+DDR5Jww+FuXXZUVz3I6Inn/WVlj2xIWNJ
              yrvfL4yhPNQ11GBfe3gsP5nlG0qJKFmqINZdD/uRLx7IQGCR9pU2Q0SKKHp7
              1NEScZ7pjHBm/j2cd0RYuEowmwgdeuVAft8AObiEx3CEJOnEeZWszS5GLhXH
              PuDE5eIkwZ+D5MnJBDm7bzgMYqgpnEwCj6CEafK4nviN4MbQ8yjhm0ZvETOa
              rJ8gvawMTg2Lv0LYGhZ/+l1iYnJN7AsPc2VwhvLTrwS3iHMTJw9VTOJ2knMx
              yfmA5OfIdmYTqQZmGyg3cB4XiMTFBiqY2wSLMY9Lm8jFcGNcjpGNcSQhrsQ4
              GaMUY/obvtOFxB8FAAA=
              """,
          """
              pkg/Lib.class:
              H4sIAAAAAAAA/4VTbW8bRRB+9mzf2Ve3cVKaOikpITWtk0DPNuUtNi5toJLL
              JVRNFKnKp/Vl42583qvu1lb5lt/CPwhIFIGEIj7yoxCzl2tKkgJf5mVn5pnn
              Zub+/OvX3wHcw0MG58Vw4Pmy74AxVA74hHshVwPvu/6BCLSDHIPdkUrqLkOu
              vrxTRgG2izwchrx+LhOGkp9htCllP4oY7tT9N0hbOpZq0PbPY7eXdxhu+VE8
              8A6E7sdcqsTjSkWaaxmRvRnpzXEYEmrtv7IohfdDQWnsZRFTDDeHkQ6l8g4m
              I08qLWLFQ6+nDI1EBomDaYZrwXMRDLMOT3jMR4ISzzHPeF78FjOGq3jHxQyu
              McxcTCAu358NZFj0xfXTcfkZ0VjshxT0vl0PeZKkc+l2ttcuVnf/r7azvd3u
              pgCz/j+m8Hgy2jLzCqh/aT0aveCKZsdw9TVc7fSxXcYCbpZg4T2G6QthB++X
              cQnzLpZwi8Bq5gRq6dILQ8OA4fq/fBZDsROE2Sk16/7btvS12OfjUK/TYnU8
              DnQUb/B4KOL2yeEtm7YrxOt18YbQfI9rTuDWaJKjo2ZGlIwALWFI7y+l8Rpk
              7TUZNo4P512rarlW5fjQtYrGsFxjVq4Yt3p8uFhsWQ22xooPC3/8YFP4cbWS
              m7ca+ZZdKZC2W8WKM5+vsgYzoC1mWtk0prtDzXDj6VhpORI9NZGJpMN88OZY
              6ZdZj/YEw5Qvldgcj/oi3jbHa24lCni4w2Np/Oyxdh7r9FLPgJa25EBxPY6p
              ZC4r2XlL83JPKRGn2xDkulvROA7EIxkKNGnh+XRmc2b/pL8gzyZ9mXSedCH1
              1sjzzGRJF1ZeoXhEhoV2lmxkh2T5JAEluKSn6WLKlGWKvyFt9uGszlR+wezZ
              chtOWr54kpKVG2sK11NIB1ViaOFLsi9bGT2GKyTnzF0SVdOlSQCWOYQfsbiy
              +jNqR2mawZ49CeCDDLuE27hDsW7K5AbuZ1zeJX2ui5FF1E8HUU1p0tf+hqVn
              7BVWf8LiUfqSw1ckXcpbItAF4vYghf88hX9E7x/SdD7aRa6Huz14PTTQJBOt
              Hj7GvV2wBJ/g012UErgJPktgJ7iUGlOprCZY+BuTJI2bxwUAAA==
              """,
        ),
      )
    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            val resolved = node.resolve()
            assertNotNull(resolved)
            assertEquals("foo", resolved!!.name)
            val parameters = resolved.parameterList.parameters
            assertEquals(2, parameters.size)
            val extReceiver = parameters[0]
            assertEquals("pkg.Lib", extReceiver.type.canonicalText)
            val param = parameters[1]
            assertEquals("kotlin.reflect.KClass<T>", param.type.canonicalText)
            return super.visitCallExpression(node)
          }
        }
      )
    }
  }

  fun testGetJavaClass() {
    val source =
      kotlin(
        """
          class Test {
            fun test() {
              val x = Test::class.java
            }
          }
        """
      )
    check(source) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitSimpleNameReferenceExpression(
            node: USimpleNameReferenceExpression
          ): Boolean {
            if (node.sourcePsi?.text != "java") {
              return super.visitSimpleNameReferenceExpression(node)
            }

            val resolved = node.resolve() as? PsiMethod
            assertNotNull(resolved)
            // With @JvmName("getJavaClass") on getter
            assertEquals("getJavaClass", resolved?.name)
            // Java Class, not KClass
            assertEquals("java.lang.Class<T>", resolved?.returnType?.canonicalText)

            return super.visitSimpleNameReferenceExpression(node)
          }
        }
      )
    }
  }

  fun testResolveJvmNameOnFunctionFromLibrary() {
    val testFiles =
      arrayOf(
        kotlin(
          """
            import test.pkg.LibObj

            fun test() {
              LibObj.foo()
            }
          """
        ),
        bytecode(
          "libs/lib.jar",
          kotlin(
            """
              package test.pkg

              object LibObj {
                @JvmName("notFoo")
                fun foo() {}
              }
            """
          ),
          0x399fe321,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijg4uJiEGILSS0u8S5RYtBiAABz6lUC
                JAAAAA==
                """,
          """
                test/pkg/LibObj.class:
                H4sIAAAAAAAA/2VQ227TQBA9u04cx0nJhUKTlnu5FJBwWvHWgFQqKlwZI9Eq
                EsrTJjFhE19QvIl4zBMfwh9UPFQCCUXwxkchZt0IULHkmXPOzpydnZ+/vnwD
                8BhbDBUVpMp5Px46nuy96o0KYAzVkZgJJxTx0CEp6KsCDAazLWOpnjIYW/c7
                ZeRh2sihwJBT72TKUPPOee1ST5yogyRhWPXGiQpl7IxmkXM4i3wRBXSeiykz
                WO1+mJnb4NrRcv2j4z1//3kZVdhFEmsMm14yGTqjQPUmQsapI2IyF0omhP1E
                +dMw3NVTLC96GSgxEEqQxqOZQQ9mOhR1AAMbk/5BatYiNNhmaC/mdZs3uM2r
                i7nNLQ24vZhbPz7yxmK+w1vsWcHi3z+ZpB+uVI113sq9KGhu5bXHDtPOxlv9
                4OLZDh6NFcPG62msZBS48UymshcGe39Hpx3sJwPaQcWTceBPo14wORZUw1D3
                kr4IO2IiNV+K9lEynfSDA6lJc2nc+c8W27S0XPbSpt4h5VvETMoXKRt0ms/Y
                JjFH74Ny/sEprBMCHLeXxSCTOxTLZwUokhVQQ4lOedb8MLuE/vON5j+NbNmo
                ZygR0+pKStDChT9jrFG7/kpfwd+covIZ9ZNM4LibxZu4R/kJla/SKJe6MFxc
                drHmooEmQay72MCVLliKq7jWhZnCTnE91eBGBkq/ASHyr2b9AgAA
                """,
        ),
      )
    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            val resolved = node.resolve()
            assertNotNull(resolved)
            assertEquals("notFoo", resolved?.name)
            return super.visitCallExpression(node)
          }
        }
      )
    }
  }

  fun testResolveJvmNameOnGetterFromLibrary() {
    val testFiles =
      arrayOf(
        kotlin(
          """
            import test.pkg.*

            fun test() {
              42.prop
            }
          """
        ),
        bytecode(
          "libs/lib.jar",
          kotlin(
            """
              package test.pkg

              val Int.prop: Int
                  @JvmName("ownPropGetter")
                  get() = this * 31
            """
          ),
          0x4413c592,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijg4uJiEGILSS0u8S7hEuLiKAGy9Aqy
                02FiSgxaDACtE4vTOAAAAA==
                """,
          """
                TestKt.class:
                H4sIAAAAAAAA/zVOTU/CQBScbaGVClLEL/Dr4AU8WDDePBkTTSN+RAkeOC2w
                IUthS9oFPfKXvBkPhrM/yvhW4zu8N/NmJpmv749PAGc4ZHDaItU32gVj8Ed8
                zoMxV8PgvjcSffraDBlNDga7Vu8wFA0JptEw+M+5DIX4RT0k8fRaaC0SStTC
                ephHDp6HFawylFpRrMdSBbdC8wHX/JzBmsxtKsHMypkFBhYZYJH4Kg1qEBo0
                GcrLheMtF57lW1XHXy6qVoMZ6ZSZlGs6nUTUMXMZDwSVbEkl7maTnkjavDem
                j/cUz5K+uJKGVB5nSsuJ6MhUknqhVKy5lrFK0YSFDP7KVJCFQ3eX2BFdM65/
                /I7889uvYY+2RwH8Gh1C+6Y9qjig2yRHgZS1LuwQxRB+iBLWQ5SxEWITW12w
                FNvY6cJKkU1R+QEBzkCulwEAAA==
                """,
          """
                test/pkg/TestKt.class:
                H4sIAAAAAAAA/2WPT08TQRjGn5mWpSxgFwSxBcUYEv8cXDAkmngiJpLVCkYJ
                l56m7QSm7c42O9OFI5/Fs5/AAyEe/VDGZyiePLz/fvPMzPv8/vPzGsA+ngg0
                vXY+nYzO0hM2n/w8hEAyVJVKx8qepce9oe6T1gSWiwv7pSwmh9p7XQrUn2cv
                MoG1zqjwY2PTYZWnH6v8SOX6HU8tq0C848+N25nwnoCgfOWf/LP2aqC8olbm
                VY0LiZAWQgK1o9BIHl6a0O2yG+wJPLu5imOGTBqxbMin68nNVVvuipeMt4e/
                vkdRu96QSS3IX3ON2c98cD44fTXyZO+LAVdrdozVR9O8p8sT1RuTrHaKvhqf
                qtKE+Q5ufp1ab3Kd2co4Q3RgbeGVN4V19PetmJZ9/cEEaetOevqfEHuQqGNm
                rYU5RJzbnFJWroe5drJ9/iM4xiZzdAsjbDEvzQRYQMz6iLEY7DAajlji8e21
                FrZZ3xAvki51UcuwnOFehiaSDCtYzXAfa10Ih3U86KLusOHwkG84RH8BOGOs
                DhQCAAA=
                """,
        ),
      )
    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitSimpleNameReferenceExpression(
            node: USimpleNameReferenceExpression
          ): Boolean {
            if (node.sourcePsi?.text != "prop") {
              return super.visitSimpleNameReferenceExpression(node)
            }

            val resolved = node.resolve() as? PsiMethod
            assertNotNull(resolved)
            assertEquals("ownPropGetter", resolved?.name)

            return super.visitSimpleNameReferenceExpression(node)
          }
        }
      )
    }
  }

  fun testStringConcatInAnnotationValue() {
    // https://youtrack.jetbrains.com/issue/KT-69452
    // Regression test from b/347629388
    val testFiles =
      arrayOf(
        kotlin(
          """
            @MyAnnotation(
              password = [
                "nananananana, " +
                  "batman"
              ]
            )
            fun test() {}
          """
            .trimIndent()
        ),
        java(
          """
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Retention(RetentionPolicy.CLASS)
            @Target({ElementType.METHOD})
            public @interface MyAnnotation {
              String[] password() default {};
            }
          """
        ),
      )
    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitMethod(node: UMethod): Boolean {
            val psiAnnotation = node.annotations.single()
            val attributeValue = psiAnnotation.findAttributeValue("password")
            assertNotNull(attributeValue)
            val initializer =
              (attributeValue as PsiArrayInitializerMemberValue).initializers.single()

            val uExpression = initializer.toUElementOfType<UExpression>()
            val uEval = uExpression?.evaluate()
            assertEquals("nananananana, batman", uEval)

            val eval = ConstantEvaluator().evaluate(initializer)
            assertEquals("nananananana, batman", eval)

            return super.visitMethod(node)
          }
        }
      )
    }
  }

  fun testLocalPropertyEvaluation() {
    // https://youtrack.jetbrains.com/issue/KTIJ-30649
    val source =
      kotlin(
        """
          class Test {
            val foo = "foo"

            fun test(): String {
              val bar = "bar"
              return foo + bar
            }

            fun poly(): String {
              val na = "na"
              val b = "batman"
              return na + na + na + na + na + na + na + na + ", " + b
            }
          }
        """
      )
    val names = listOf("foo", "bar", "na", "batman")
    check(source) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitSimpleNameReferenceExpression(
            node: USimpleNameReferenceExpression
          ): Boolean {
            val eval = node.evaluate()
            assertTrue(node.sourcePsi?.text, eval in names)
            return super.visitSimpleNameReferenceExpression(node)
          }

          override fun visitReturnExpression(node: UReturnExpression): Boolean {
            val eval = node.returnExpression?.evaluate()
            if ((node.jumpTarget as? UMethod)?.name == "poly") {
              assertEquals("nananananananana, batman", eval)
            } else {
              assertEquals("foobar", eval)
            }
            return super.visitReturnExpression(node)
          }
        }
      )
    }
  }

  fun testResolveWithTimeout() {
    val testFiles =
      arrayOf(
        kotlin(
          """
            import my.coroutines.*

            suspend fun <T> test(body: suspend CoroutineScope.() -> T): T =
              withTimeout(42) {
                body()
              }
          """
        ),
        bytecode(
          "libs/lib1.jar",
          kotlin(
            """
              package my.time

              @JvmInline
              value class Duration(private val rawValue: Long) { }
            """
          ),
          0x4a472a3d,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijg4uJiEGILSS0u8S7hUuTiza3US84v
                yi8tycxLLRYScIaxg5PzC1K9S5QYtBgAukIp7UcAAAA=
                """,
          """
                my/time/Duration.class:
                H4sIAAAAAAAA/31UUU8bRxD+dn1nn88HHE5CMKQGmoYYB2JD0zQtCRBI09g1
                SQqpG0L7cJgTHNh39O5M0zeUl/a5iio1j33pSyq1UguokSpC3vqbqqqz5zNG
                BlU67c7Mznwz883u/f3vn38BuIYvGfTaNznfqpm5O3XX8C3HjoGRdcPYNnJV
                w17LPVjZMCt+DBGGDt9Z9F3LXhuzaltVhnOZ4kip5dk4m2Q4326brVvVVdON
                IcYQvWnZlj/FEMmMlDXEoapQkGBINivIuMbXZaNaN28p6KAAY2vLtFcZxjIn
                k53MH+aa1NAFXUB3M1w4rdDjjmeE41nhOPf/jj3C8TyD0qSC4WzmFBI0pNAn
                fPsZJMNdyzOwIoU1W9MwgHQcHIMaZERVkt4mT3/d8oiJUvtQiNSOdcNbn3NW
                zZB8iXoqMHS2Upccey2GDGVpumrI4rKKEVwJ6C5oGBY6x1WGhPlV3ah6IVpP
                plhqn/nkyBMGtW6vOE9DL8IoapjAuwLjGoPs+OumKwo+EUtzayQQ1+Q0aA15
                jAucDxu9lFVI4hroFcf2fLde8R33WKuCvFYhwc07QZK4TTcF5Cyxvc2gHWuR
                BiBnikXREt8aF8sEdV3adPyqZec2tmu54natYJNiUu3dzYN50zdWDd8gG69t
                R+jVMLHExQLKskn2p5bQKAFfJeDXBztZlfdylesHOyp9XI+rXInQnqBdol0J
                9U7auXL47Uzvwc4Ez7PZZDKq8z6ej7zZZwc7hz9FJUXS5WKfHiVjbELRlT6p
                l+XZvTc/RILTuK4WdT1BpxrZWGDr0DvJ1kU2/cjWrScXOhvQjw+fSWTjVNrh
                d4wffs+O8j3jVJ2SEo0QOdReoknt1U2fxiCulBi2UzGqZcO1jJWq+UgsdDsX
                faOyOW9shXpXiYi8X6+tmG5oURedulsx71pCSS3UbTG7suVZdHrbth0/SORh
                nOYnBeQmxRshKUFjpTdCls9Jex8R8gBSr6As7UFLdu4imd7FOX1kF727uPBr
                EPyYVo12csRbAQwTTzIEGSYIcaZkf8fQPi62xyh4B5fEnMWDCWMuUoxILKf3
                MfqyLUA+SpLF2OlJcu0xrST0EsKYh9S7THv/6GvwF5AjL0cPwHfx3mx66PmP
                QpcEDMcSrTHw+D/okgLMnqC4/rAOIV0nrkQFN/BBiD4echcXFV3Zx2SrpEZ4
                PCxJSCKc6Vy8qTB8isLFVVeze7iVHfgDQ78FtTyhNRrS0MJSj7DUYJCMkKYw
                HWINhmzy9C9ttPDG2PUUZnA79L5MtAT1vQJfSu9hrn1gcdwJgrrF3+nYwIKi
                RLftWeSwuhQ+wt0wYJqyiKunpQefv0BM+hlSpMW2DK7OHCdLw8ch1xrukSQa
                Wg7cy/iC9nWSCrQXKfSTZUQKKBUwX8B9PCARDwv4FAvLYB4W8WgZZzxc8vCZ
                h4FgnfIw7eFGIF/3kKeX4SEbqMMeLntIBbLsIfofUyexjNAHAAA=
                """,
        ),
        bytecode(
          "libs/lib2.jar",
          kotlin(
            """
              package my.coroutines

              interface CoroutineScope {}
            """
          ),
          0xe72a63a7,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijg4uJiEGILSS0u8S7hEufiza3US84v
                yi8tycxLLYZJKDFoMQAAnaLdHj0AAAA=
                """,
          """
                my/coroutines/CoroutineScope.class:
                H4sIAAAAAAAA/31OTUvDQBB9s9GmjV+JWqgg/gTTFm+eRBACFcGCl5y26Srb
                JLvS3RS99Xd5kJ79UeJE8eDFGXjz5g28Nx+fb+8ALtAnnNavaWGXtvHaKJde
                /9JpYZ9VCCLEC7mSaSXNU3o3W6jChwgIyaS0vtImvVVezqWXlwRRrwL2pRZ6
                LYBAJesvut2GzOYjQn+z7kZiICIRM3scbNZjMaT2OCacTf57iEPYM/krnpee
                EE1tsyzUja4U4eS+MV7X6kE7PavUlTHWS6+tcR2OwRZ+SuDoGw9xzHPEztvc
                nRxBhjBDN0MPEVPsZNjFXg5y2MdBDuEQOyRfbTwqNkgBAAA=
                """,
        ),
        bytecode(
          "libs/lib3.jar",
          kotlin(
            "src/my/coroutines/Timeout.kt",
            """
              package my.coroutines
              import my.time.Duration

              suspend fun <T> withTimeout(timeMillis: Long, block: suspend CoroutineScope.() -> T): T = TODO()

              suspend fun <T> withTimeout(timeout: Duration, block: suspend CoroutineScope.() -> T): T = TODO()
            """,
          ),
          0x23c05912,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijg4uJiEGILSS0u8S7hkuLiza3US84v
                yi8tycxLLRbiDMnMTQVyvEuUGLQYAPhcsp9AAAAA
                """,
          """
                my/coroutines/TimeoutKt.class:
                H4sIAAAAAAAA/6VTX28TRxCfPf87nwM5jj+JTRv+JJQEMGfcPtVR1CoB5cCh
                ETaRqjytL0vY+HyLdvcMvFk89Hu0X6JVK7VW+lb1m/Q7VJ01d+AQIEJ9uLmZ
                2d/8ZnZm9q9/f/0dAL6CLwnMDV76oZAi0Txmyu/yAUP9gS4BIeAe0CH1Ixrv
                +9/1DliI3hyBynOun6ZAAo+X77f7Qkc89g+GA/9JEoeai1j591Kt2crOp/Ks
                ixiVhBpAa6X9bp4Wgb9Xu18f96+dmG213j56pfVM7YTiGWvVT6hmtd7tttZa
                N9+T+6SLpKHvvc5SW8h9/4DpnqQc66VxLDR9XfvDJIpoL2IIW/wYTGiDRFQt
                rQM9weBZxAYs1mzvrpRClsAhUFzlMddrBDaWp2rpaMnj/VYw3UCOgTKmkb/B
                ntAk0ngdpWUSaiG3qOwz2VrZmYEZOOVABU4TcDTOfYtHEVcEyH0ChV4kwj6B
                yyctAe7NUihMtcaeCvjQVhA4O7Vo9fVhf7sTCwIlna3emYxii2m6RzXFGGsw
                zOFuEyPKRgDW2TeKhYcvuNEaqO3dIfDPeLTsWHbOsdyyMx451rz19nMzh/3a
                MR7VNtFXszbJ1bw9HrmkWXStmtXINa+7+dqiTby8ZzWKnuPZRtskjZJX9PLz
                pGE3Coc/FS27vHn4wzd//kLGI2O6Tm37GGEFCWc+mZBMSDPmvH3KdQ5fWRWs
                veoU7MMfFxrEXLmJregS0xEv69z0fBY+/nAMII26+0KzWGFYFt59OQF4hsGM
                x99IZDZFJ53g7T6OLL8u9hiB2TbSPkwGPSa7ZvNNqAhptEMlN3bqLHf4fkx1
                IlG/+CiJDXUQD7niePzt26eBz+vd020q6YDhch+BOR2RyJDd44a9msbsHOOD
                O2BB3iwP/qtQgCJaN9EKUDf+ym9Q+Z7kyc8w+8cEdAtlERsLCKijvAxm0Srg
                wpkJTQU8OIs4o52D83h6exJXAj+NtM1i4jc7WV8ov5FVuPAp+UtH8s/9//wW
                dsPIG9DE/2P0zmMV1V3IBVAL4GIAn8HnASzApQDTXtkFouAqLO7CaQUFBUsK
                XAXXFHgKvlBwXcH5ibmsoKhgTsGKggv/AT68tf+SBgAA
                """,
        ),
      )
    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            if (node.sourcePsi?.text?.startsWith("withTimeout") != true) {
              return super.visitCallExpression(node)
            }
            val resolved = node.resolve()
            assertNotNull(resolved)
            assertEquals("withTimeout", resolved!!.name)
            assertEquals("TimeoutKt", resolved.containingClass?.name)
            // Including compiler-added Continuation
            assertEquals(3, resolved.parameterList.parametersCount)
            // long, not Duration
            assertEquals("long", resolved.parameterList.parameters[0].type.canonicalText)
            return super.visitCallExpression(node)
          }
        }
      )
    }
  }

  fun testResolveProtoDSL() {
    // https://youtrack.jetbrains.com/issue/KTIJ-31140/
    // From `CallGraphTest.protoDataUserKtCallGraphWorks` in boq callgraph contamination check tests
    val testFiles =
      arrayOf(
        kotlin(
          """
            package test

            fun call() {
              val protoObject = protoObject {
                protoType = ProtoType.PROTO_TYPE_A
              }
            }
          """
        ),
        // Generated by the protocol buffer compiler for the following proto
        //
        // message ProtoObject {
        //   ProtoType proto_type = 1;
        // }
        //
        // enum ProtoType {
        //   option features.enum_type = CLOSED;
        //
        //   PROTO_TYPE_UNKNOWN = 0;
        //   PROTO_TYPE_A = 1;
        //   PROTO_TYPE_B = 2;
        //   PROTO_TYPE_C = 3;
        // }
        bytecode(
          "libs/proto_java_object.jar",
          java(
            """
package test;
public final class ProtoObject {
  private ProtoObject() {}

  private static final test.ProtoObject DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new test.ProtoObject();
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static final class Builder {
    public test.ProtoObject build() {
      test.ProtoObject result = DEFAULT_INSTANCE;
      return result;
    }

    private int protoType_ = 0;
    public test.ProtoType getProtoType() {
      test.ProtoType result = test.ProtoType.forNumber(protoType_);
      return result == null ? test.ProtoType.PROTO_TYPE_UNKNOWN : result;
    }

    public Builder setProtoType(test.ProtoType value) {
      protoType_ = value.getNumber();
      return this;
    }
  }
}
            """
          ),
          0xd921b70e,
          """
              test/ProtoObject＄Builder.class:
              H4sIAAAAAAAA/2VSTW/TQBScddK4NtskdVOaQstHPx2nNOJcQLQhFRauExG3
              iFPkpG7lEuzIdpD4RVyh4kscuCLxmxDi2UlF6hy8+zyemTfet7///vgJ4DFq
              MgRkRGQ5ZpBjKF7Y7+xa3/bOa83uhdOLGHKPXM+NnjBk1MqJhFlIImSOG+AM
              5cgJo1or8CN/RN84GLr9UydgkAcxar0fOB0GpkvIoyCiyDEPhfqkhQQ9axzu
              HxtWRzfb1r5ZbzAoRpq2J6OERRE3OZZQZsj/J8StGKQzPzCHb7txhAVVrxjX
              CXsS6W9x3MYK2bdeNq1mx3rdanSOzRdm85VJMdKKuOMdjru4R+7nTnTlTseh
              M2Tr/im1LRiu54y+WHa3T8hMNz4JhpJamf4LBk5OE7EVdSopw1w7sntvjuzB
              2JKH1zQ7aloy3elqHuQmt/1h0HMO3diqOMHZjWfOMGuS9rkf0ii47nlOUO/b
              YeiEDOLYBA9p+gLdG0YrzZ+qLNV0cWi9T29bhDPaZe0bmJb5AvFTwl6LsURZ
              IMUiIevYGGvWEjcgd4k5o/oxxZ8nvkLIJnmP+Adjfkn7DPE7FozqL0iXWP6A
              bKIWJtRLlLeMbao4hD9QRJSe0kMOKioTmYUkc/UrVimyls6wQi6ryEBL3KvY
              SfY8HiQMOjiqd7H8DxzOlppUAwAA
              """,
          """
              test/ProtoObject.class:
              H4sIAAAAAAAA/2WQXUvCUBjH/8ep244rzd60F+hCSLtIus4CMwXBVqAFXcWc
              h5isDbZZX6voQkjoA/ShoufM9YLdnOeF3/k//+f5+Hx7B3CCbY4UFBVpAxlk
              GQpj69Gqu5Z3X78cjoUdMWQbjudEpwxKtXajQmMoRSKM6leBH/lzqHI2cdyR
              CDhUKZYjnUWEg0PR6Vk2kEeBiPN2p3ndG9x1zf6gabbaDMXe4q9jhnTLHwmG
              fM/xhDl5GIpgYA1d6nBPPCVzGXaqtX+fv12RiNaw3WQL3vcngS06jtQo/MEP
              5eoMOZNkLoQcFDIYXc8TQcu1wlBQqSaSOII8HMCwJdehTKGaTkidFap2KTKK
              mYMp2EsMFunNxs00dKxiLUH3YiFAm0G9nUJ/XqANaFj/Ed5P6NwMnGjjFUu/
              8nNPGkWd/GxQnsImSvFIMo9yzMiMk+/yF6CbARQGAgAA
              """,
        ),
        bytecode(
          "libs/proto_java_type.jar",
          java(
            """
package test;
public enum ProtoType {
  /**
   * <code>PROTO_TYPE_UNKNOWN = 0;</code>
   */
  PROTO_TYPE_UNKNOWN(0),
  /**
   * <code>PROTO_TYPE_A = 1;</code>
   */
  PROTO_TYPE_A(1),
  /**
   * <code>PROTO_TYPE_B = 2;</code>
   */
  PROTO_TYPE_B(2),
  /**
   * <code>PROTO_TYPE_C = 3;</code>
   */
  PROTO_TYPE_C(3),
  ;

  public static ProtoType forNumber(int value) {
    switch (value) {
      case 0: return PROTO_TYPE_UNKNOWN;
      case 1: return PROTO_TYPE_A;
      case 2: return PROTO_TYPE_B;
      case 3: return PROTO_TYPE_C;
      default: return null;
    }
  }

  public final int getNumber() {
    return value;
  }

  private final int value;

  private ProtoType(int value) {
    this.value = value;
  }
}
            """
          ),
          0xa2358b1a,
          """
                test/ProtoType.class:
                H4sIAAAAAAAA/21UW08aURD+jsuysK6A4BWl3qgCWqm29gb1GpuQUjBdtCF9
                MCtdCYpgYDHpX2r6YLWtpk0bn/ujms45rkqBkwyTmflmvpnZCX/+/vgN4CXW
                FXQxeCyzbsW3alWrmvt4bLrB4NAgw8ng33qbzWV3c/mtzd3tzOtM9l2GwZf+
                PyHBM1wa3DxDa8pY44FuDVprYJ0HPBq8rYENHujV4EeAQQnvrKW3N3XyvG+l
                VNGPAQUBDYMYYpAL5WrFZOiLRNMHxokRLxuVYjy7d2AWLMIGMaJgVEMI94j3
                DrBZaRwRz4lRbpjZfYalSFP2Rtmo1xNNDt2qlSrFRDMDLyDGH9cwgUlqRNRi
                YCnOGtZwH9MMzmSpUrKWGQYi7fVS0R0XZJWKRDk8xjDYCSVgbhc0F7wcHNfw
                EAt8S4KyzhCIRNv2RNw3YcdG9QN15k2XKmamcbRn1nLGXpk84Q500fZC7v1q
                7TqPU6U6IHp0yygcvjGO7cLuomndZEiRaIo8eqlYMaxGjaIOqrFDh5MslO3t
                EIYcky37TbYSLROVqlcbtYL5qsR5PLexeZ66ukDrCdKBO1eH+RkDpN221mzt
                5ZrujL4a4AvyiyNbIdwieP4jsiZJ86eewfcdfVfoPyWL4TGvLWIS4UNYusV3
                Ca/qZ7FLDF+BdcI/wVMbf0iaexeDnwRPHNdPIhkjmSKZIZk9g3R6BoVEJek5
                FXW7RN2gmGMQ4xhCGMOIkGcOI3Qbo3gmJpLHJXroxXO8sIlDNrEc+4qxzy09
                TlBlOkP65dA56sZBWovNjl5gKhb6hrEvtxke0es0Zc6gm7gToq+kT+W3aZMt
                iXwiVX6B5SWaRc87aBo9L9M8et5JE+nti3LR/9OyXaJIn5G/lZ9UwT8jSReI
                nEMS1qzDISxFWHOyLCxVWA+cTmH1XGL+HL67vgdEQYX676Y9eWnaAG1Lot6v
                J1jBqtBr/wAP5INoKwUAAA==
                """,
        ),
        bytecode(
          "libs/proto_kt.jar",
          kotlin(
            """
package test

@kotlin.jvm.JvmName("-initializeprotoObject")
public inline fun protoObject(block: test.ProtoObjectKt.Dsl.() -> kotlin.Unit): test.ProtoObject =
  test.ProtoObjectKt.Dsl._create(test.ProtoObject.newBuilder()).apply { block() }._build()

public object ProtoObjectKt {
  public class Dsl private constructor(
    private val _builder: test.ProtoObject.Builder
  ) {
    public companion object {
      @kotlin.jvm.JvmSynthetic
      @kotlin.PublishedApi
      internal fun _create(builder: test.ProtoObject.Builder): Dsl = Dsl(builder)
    }

    @kotlin.jvm.JvmSynthetic
    @kotlin.PublishedApi
    internal fun _build(): test.ProtoObject = _builder.build()

    public var protoType: test.ProtoType
      @JvmName("getProtoType")
      get() = _builder.protoType
      @JvmName("setProtoType")
      set(value) {
        _builder.protoType = value
      }
  }
}
            """
          ),
          0x461779f1,
          """
                META-INF/main.kotlin_module:
                H4sIAAAAAAAA/2NgYGBmYGBgBGJOBijgkudiKUktLhHiDyjKL8n3T8pKTS7x
                BkIhthCgsHeJEoMWAwCVVsrnOQAAAA==
                """,
          """
                test/ProtoObjectKt＄Dsl＄Companion.class:
                H4sIAAAAAAAA/5WUW08TQRTH/zNLu2VFWFChgHerFhAKeLdqFIixWpGoITE8
                kOl2lKHbWbIzJT7y5AfxE8iTRhNDfPRDGc8uFURE48uc+2/PXM5++/7pC4Ar
                uMtw2kpjSwtxZKOntVUZ2Me2MGfCwmzUXBNaRdoFY/BXxboohUK/Lm1nuXAY
                sreVVpYgTnFksQsZZD10wGXosCvKMJyt/gteZnCXg1gKKxmuFPflF2ZaKqzL
                uDxyAIoAx6qNyIZKlxZatVCZFVm/v6YScG27NoduhpPtnNX1ZklpK2MtwlJF
                21hpowLjwidQsCKDxnxk51thuCBi0ZSUyHCxWP19++VfPM8TyOtycgJ9OOKh
                F0cZ+v/crot+hsd/2Wf1T33OyVeiFdrZSBsbtwIbxU9E3EhOJT31vIcBDDIM
                HYxlmCr+L9oDT+7ySCHYDS430yjDxP/RGHp/FjyRVtSFFeTjzXWH3iFLls5k
                AQNrkP+NSqxJ0upTDDNbG/0ez3OP+1sbHs/x1EjUPM99fevktzam+SSbcXP8
                67ss9/kj33eG+GTHdNbPkMw+ZAlpmiX8/MEPKX/Q8bkYpxH4PeqiRI+sncLQ
                t5/rgr7pEJyhc+fJE2lP0kTD0sDMRnWagJ6q0nK+1azJ+IWoheTpq0aBCBdF
                rBK77Rx+1tJWNWVFryujyHVf68gKS3Sauq6K1jKeDYUxkkzvedSKA/lAJZWD
                7crFfXWYouvuSO4ADmk0y3RY18kqJZdCMjP6AblNUjhu0JpNnV24ma5pAjrh
                AX4vDpGHp8VzhOQk/bG+no849hkDL8fYBwy938PJoifl9G/nbnNSrRvDFL/V
                zjuMZEwzON5ubZwkb7d2YjN9PruYzA4mg5M4RTEHZbKGSE5gkvY7SGNzmf6C
                g5Q/gKskb6dtXcMdkveo4jTVnlmCU8HZCs5VUMB5UnGhgosoLoEZjGB0Ca6B
                ZzBmkDU4ZHDJoNtg+AfXR2moZgUAAA==
                """,
          """
                test/ProtoObjectKt＄Dsl.class:
                H4sIAAAAAAAA/51Va08bRxQ9szZrezF4cQg1pEkJcRs/UhtIH2lMQ3gEMBhC
                gTpQ2tK12cLCehftrFH6DfVDpf6N/oKitCFqpQrxsT+q6p318rIhUitZe2fu
                3Dlz7pk713//88dfAD7Cdww9rs7d/KJju/bzyo5edefc5CQ3Q2AM6o62r+VN
                zdrKN9ZCCDDII4ZluE8YbqdKzZuT43XD3NSdQrrMEEily1G0IaQgiDBDeKPS
                WGXou35nFAraI5AQZQi62wZnSLREN0gWiEwDk6E7lW6JovWbpV3bNQ0rv1iv
                mAbf1jfH9gzyJ647P4QbDG0eaBQ30aWgGz0MiudJ5XK5dBgJhjs+7M5+LW9Y
                ru5YmpkvWq5jWNyo8hD6GG5Vt/Xq7oLtLtRN89nLPUfn3LCtsmbWdYb7qVKz
                vIULnmUBtVUQCr6L2wpu4Q5DdEt3Pc4rP+wRRPxSzsJHmXWXLlCb3a8taDXh
                DlpkGe6VbGcrv6O7FUcjqnnNsmxXc4kWz/tM6Qb6cVfk/T5D18UT/fTvExF+
                iciNVDMPcf9t+yLTMLJ0DRelWNQc4kKaRfFhI7Ucw4NWiLfVSBqDguEQFWmr
                BHNvKczSVRc3qX+v1U13glRwnXrVtZ15zdn1ylgUsKxQOX5MaSar5xEbNS+E
                IfffIOkdjFRN/wn1X13YyQm7tqdZdCshPGYYSv0f1iMKCvicIXIGxjBwzUs6
                P5C0HcVT8f7G6PZPz53XXW1TczViL9X2A9Q8mPhExAcMbJf8Lw0xG6TRJl3L
                z8cH/YqUkBRJPT5Q6OeNw5JvZc8eHyQorHNYGmSPWed4LCzFZVXqkwYDJ7/I
                khqcDatRmnXMsKXOhn/15MegWKOtS0k1RC55WFbbhH00/XR69eSnTlqO9gXD
                YTUyEAwrarsgNMwEzcjehbdzVbdQm30hfMEQGj9tW/FW9UJYoU5HEtLuSwu5
                XZdawFLdco2aXrT2DW5UTH3s/L3Rm5ywN4lKrGRY+kK9VtGdFY1iBDm7qpll
                zTHE3Hcmm7HO3tEl0GjRsnRnwtQ412mqLNt1p6pPGQKi14cot5DBEN14kO5P
                Qq8oAFJrlWYy2STZuGjKTT6ZYtu82RrNpmgmkVUyR4hksr+j49BD+4q+nfCK
                hfAV+kawTrOeRjStxUQN0cg7Qe2Cii5aE5iPaB8T+zO/oeM13lmL977Be7+e
                4creareHF21E+ninPONku0Q7uxrxg7V46grExDWIMYoaIO89TpBJDzqNDEEK
                6BlfADUbf/AGeXFA9jWGX5zLILqITN0uRh39XAL1TAIVWTz0RRZi+0el8TU5
                Qqwhu0p9lf65mZ+O5EkrZ7JH+OTQe4sCub/hPUOW/esTo0+9XWF8hpCPkvCi
                KeE/UVhjR3jyCuOHnieAb+jbR7YbS1im0pDwJcpkCzQaJfutl90LbHgSMUwQ
                u8l1BIp4VsRUEdOYoSGKRcxibh2Mo4T5dcS4+C1wKBztHDJHluMhx3NSluMu
                Jc2R4VA5FjlG/wXUg+QpsAgAAA==
                """,
          """
                test/ProtoObjectKt.class:
                H4sIAAAAAAAA/2VRXWsTQRQ9M5tsNtvVprW2ibV+tWr0wW2LIGgRalRYjFFs
                CUieJslQJ9nsws4k+Jgnf4j/oPhQUJCgb/4o8c42KJJduB/nnnvu3ru/fn/9
                DuAh6gyrRmoTvs1Sk77pDmTPvDIlMIbKQExEGIvkJDzHS3AY3AOVKPOUwanf
                awcowvVRQImhYD4ozbDWXJR7wuAd9OK80Qe3bC9qHR0fthovAlyAXybwIsN2
                M81OwoE03UyoRIciSVIjjEopbqWmNY5jklppDlNDYuFraURfGEEYH00c2odZ
                U7YGDGxI+Edls12K+nsM9dk08HmV+7wym/rc497PT7w6m+7zXfaYOc9KHv/x
                2eUVbvn7zKo4z3XMsL641A4VSrhGd/oPfjA0DJvvxolRIxklE6VVN5aH/zah
                SzXSvmRYbqpEtsajrsyOBXHoTzTTnojbIlM2n4NBlCQya8RCa0nN/lE6znry
                pbK12nxOe2EK9uikhfwONXth8jcpc8lXyHN6i3l2i7LQXot88f4ZvNO8vD0n
                Ax52yAbnBJRJymJLf5s3iG2fpW/g788QfMHyaQ44uE3WTr5OlC36jju59A3c
                Jf+I8BUSXO3AiXApwlqEy1inEBsRqqh1wDSuYLODgoavcVXD1dj6A0uX2oi6
                AgAA
                """,
          """
                test/ProtoObjectKtKt.class:
                H4sIAAAAAAAA/81U3VMaVxT/3UVcXGg0RI1iTKmiESLyoU1asWmNHw0RiQ3G
                NDWNc8GrWVl2md2Fmj45eelzH/vav6DTh4ztQ8exb/2jOj2LoAg4vnZ29p5z
                z/c595zzz79//gVgDjsM/baw7NiGadjGs/yBKNhr9MlgDH0HvMpjGtf3Y2cc
                GS6Gwaiqq7bKNfVHUb7QYpibyhQNW1P12EG1FNur6AVbNXQrtlrHEqlwptVX
                iuH1dWoL0Ta1NTu0bGmphuILCij1qLP5/mbrT6ulLC8JInfpBBnGM4a5HzsQ
                dt7kKjnlum7Y/CyArGFnK5pGwu68ZhSKHngZ7jZZU3VbmDrXYmndNklbLVgy
                PmIYKLwVhWJdfYOb5IkEGe5NZVormmqi5Bwj+6nwlg+96FNwAzep2p1Tl3GL
                oWfJKJW5TsEyjF1Ro9C5TMqHAQz2oB+36WlbpWUMMyi6+OFxRdV2nWjvTLXX
                M1Tnkq0R3FEQwChD74XW1MzMTNiDjxlGmmuwclg2hWVREFtcqwgfPjnLb4wh
                eF3YMkIM8k7BFNwWtSa7Mqj2eOt94sMk7imYwNTlB+zQbDIiDN2qXjWK5G2y
                w4uF20k+TCPqxX3MkO5O3gmHGq9D/UgyjoRCb5BkCIfUEA9FebmsvYu2zF+o
                abJCCQaWplKF7Leq1czZqSTnNF7K73IHizMMXVUBehDythe6anaD140uw82G
                yLqw+S63OdGkUtVFe4Q5R49zgEItOohEzEPVwSgsaZdSeHNyFFBOjhRpSGqA
                2t8n1UmBSB8dUpwlb/dJgVv+Lr8U76qd7rjr9NduydP95PQnz9/H7OSodpVP
                30uy4vac/pKMM8cLFdV7KS1/p43gbyTSnB0FfrdBXzm0he50a0Ng813Z2RlD
                V3WejDXq0fPJ8bc/gowsg4tegkbvEmOmSGFGMp22Ss6omAWxLPKV/fOInGVU
                dWaI4efc+uKG0mpMWatZUiK5YANbVe4HE8E2wU5rnySTwT1eFI5Ap5hWiUdi
                kYySGE9MzybmE4Qk52eTSmRFoa26ZOxSaL0ZVRfZSikvzE2e14RTc6PAtS1u
                qs69TuzJqfs6tysm4SPPK7qtlkRar6qWSuzFi03MEGrlnm/VS2K+tK4Lc0nj
                liXoqpxVcFV1nPV3KifDcN3yVptXJCChC2dNPQw3uun2gm7vCboJRiJ+3zH8
                roXfMXSMu6/8wWOMf0B4fTrretAVif6BmISX0x8w+5szENiic5A0PWQpgCBG
                CU7QP0n/S8ce2R0l7hw+JSwALx7gIVxEGcNnRGM1GS8+J/htzaKMVwR7CfMQ
                t8ci4woRvqNfpkGsXdzYpjNAdjJ0rlMm/XiGDYIThA0QfF2z9Q2+J/j/ayq8
                obCWKJl5SiW1DVcaC2l8kcYjfJnGV1hM4zGWtsEsLGNlGx4Lqxa+tuC18MTC
                QwtpC0+d0ji1ukGGntOfqyls/gfQiesGCAkAAA==
                """,
        ),
      )
    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
            if (node.operator != UastBinaryOperator.ASSIGN) {
              return super.visitBinaryExpression(node)
            }

            // TODO: when fix for KTIJ-31140 is ready
            /*
            val resolvedOp = node.resolveOperator()
            if (useFirUast()) {
              assertNotNull(resolvedOp)
              assertEquals("setProtoType", resolvedOp!!.name)
            } else {
              assertNull(resolvedOp)
            }
             */

            val resolvedLHS =
              (node.leftOperand as USimpleNameReferenceExpression).resolve() as? PsiMethod
            assertNotNull(resolvedLHS)
            if (useFirUast()) {
              assertEquals("getProtoType", resolvedLHS!!.name)
            } else {
              assertEquals("setProtoType", resolvedLHS!!.name)
            }

            return super.visitBinaryExpression(node)
          }
        }
      )
    }
  }

  // TODO: way to test simple UAST loading with specific project structure
  //  This test only works when source and klib are put into "common" module
  fun disabledTestBasicKlibResolution() {
    // Klib resolution only works for K2 UAST.
    if (!useFirUast()) {
      return
    }
    val testFiles =
      arrayOf(
        kotlin(
          "commonMain/src/test.kt",
          """
            import com.hello.Person

            fun test() {
              Person("Foo", 10).greet("Bar")
            }
          """,
        ),
        klib(
          "libs/common.klib",
          "" +
            "H4sIAAAAAAAA/62YeTzU7dfHRxhbGOtYIlvZ94SSxljCkAnZ584yYxtmxJA1" +
            "ycTYIpEte2gs2d20kCX7UnYVCqGSpdDC1KN+r+e+Z57iqdfr950/vvN9zZz3" +
            "db7XOee6zvWBw6hpGAH/uYQB5Bc9gA2ARDk7+Hrg5M+aMwPoyH+E72rGTmbm" +
            "jfLB+no7oXz+BAAiA+AcvF1QuD8y5/uFuYcbxtf/nP/RI38CEt0T5IZx8vBF" +
            "opB/QhTek4hxwLn5of57PDQWt/PwJzw2Mt6OKRrpgHP4tT0ngJ6e/id78K/s" +
            "PbFIXw/UT5TZmQqD/oG+voHq6hkapXUlUx/v91RwGB09crEazrXzB67fDs4/" +
            "Q3k5OKEdXFDnnLCecq4oDw/snzgv93tEhXM/7nJojOdPcIGrJSwtEE6dj8sx" +
            "KJAHnJn9SQ6f6RXReiADENZpmt6Y6Mx8tfxlRsJIp5QY7NGckXNvM8vM01H4" +
            "BcuzsuZHoSll5dFAx2mNHJNG6fQDztuJg9qvMn0EqxqGMWzhtkC31EOu2jdO" +
            "yciI4BxQi6HFBNlH/DWGfZ5aMjORDZm3BtNVPCVjghZFZVC4i10LRqzIh6pK" +
            "OiG5QRWshc2KvFx0oG8/5rccxCqeu+Ns7X9jNhT3mA3ja02nWxVA7T51s2pj" +
            "N9KFcm+LmhUV47rxOtZQcRp2x7joEXPjd4kRufwc8Q/b8hU+skm198dm8jQU" +
            "CvA0bl10mT7Y1W4C0fTzWfcx42R5lSmZ0p+XPUqsmHiec7OhONUmifWsei3H" +
            "3ESbUFMtV0Tk+6lSaKgpn5nwYMWV6naLlG/+lxyXuKWT5l+pSN/g2Z8pbaQX" +
            "ZJecwf65FpHbXY26OJvFXNwtYKFr2VkTCQ3gg0s9ltl0X5gz2mqD9nwxcvVd" +
            "33xi4lTe2sQm1wh5UEJ/P5U549rjPCbOcDn+WhWWNxph68ExL+l9w736WJ8b" +
            "jV87flclIfodDF00jy9Cj0/XrjMwP0GcZHsdEb9P4MwzIH2/6yhwmVrW1B+N" +
            "p9Hq3nw8nKBRuLouLPFMi846+3pwopii6SWRiMYvj7xiplgj1pMefInxo19i" +
            "/R6x0VK5XF0qAKCOaq+IkS+bng4YN2eUD+7nmPRVMrYogACPSY26UNvoeqSX" +
            "l9d59jEvFRd1fTmVC5Kk4HsSy6yCpw9YFHfrBq+cvhNOPK/EzQyDBiWnk94F" +
            "N2nKrcoEvNTZWKAba8gN12cyMC/n5jB5qCIxcHlUKoT45asC5HArISL05WLg" +
            "uGQ55/63RoWalTWyHh1s6av3zoB65z5qgMN+vJTQW46BiB2Pyvcscyayl3Lz" +
            "/pNy5qS0xAV4oXx20vTnKXGyt7d3tLeHU824CjkDHf2EnKmf2JTu8/RgN7KR" +
            "2SfE3smWCCC+utQ120LPDI9mgq5BJCTpkwG5g2I8EPMoGg7A97eRE+Bm09qB" +
            "xezpkwClT27eOignDwfvnXUfi/nuHPIn51AJA2iOM6CO6cZbz8YM0mP5o3Jd" +
            "QzDaCUXD6yN4FFoXRiQSTrLM6AZKFLisyY+Pg25Hga8EdhXQi/xFktdgfBCw" +
            "ee5xOEF5qw4vxiDc69I4vdKrIYznhMbiOxEFoSb1rINYngNBFTnBvSONS6+1" +
            "k2CHACoQGS9Zr57SDjQcrm0Q7aAd2p8Pdrc6fCaxQ12PuU1BRVLc4XqtsRWK" +
            "GbQFuj7fOMSJMObr8S3X2VjhN8fampxeviFNp52t9wGDbYJNWtYIsPPcb7cw" +
            "pPErcJOfBDLpjrxijs+4m62vA84SHyy/Xjrj/tfqM8aRTP3U0/dbL+QOrb/m" +
            "YaTzxbGBeWmsiAEvGe1mpYsJvcHBbDc+Rz8N8LLUG1xqZ1V7XQUrEnuVeEMy" +
            "CnIp00auVLvTID2R8PnT6bu3V4PFo7sz+vzD89/QfJscFN8eB77BJwJzh0RN" +
            "LNce7B+yrM+6NnwM3DlXOFHD/9hxQ2rj61UrkqKZqnKLu+3hwWodzUwloo7m" +
            "xLNC1JgD875kHf8Cv82RkOy3jW0HlUpbaFUIXcqYW9cXOuqOD9Vlp71bLjVO" +
            "0w4CbA0bLxuPB2v02NkqywVF+WODLsNKF2nHxtPccUd0aDkmjh4JOO3ieJpr" +
            "WkJ14eWSn25g/CmnhVnJBQkfzUxfucQPBQHO2Q/lTnZN4T/ozJT33j0ZdODD" +
            "VDPnt/w8dt3Gqqnt0O+JJnC5w5l5HwDQQf0Hye/s5vEj+Z13S34voKOlnB5Q" +
            "WFpOlpT4fF+IjxOMQ5vajMmJyYeaQ1RZGwYjzW3bkWKHb94MDmEFEVMQIm2t" +
            "D2/fnks6lnlefZZbT/IwUC+B7ajdU3wcvb6sVP8AdVozk5IoVnDWrF0+Ns5I" +
            "SPtHmRxYkhxA7wybtGeZ8FB6j0Q5+roYYJyxv6yQ5AQtWCsEdAWsS/uEMVpQ" +
            "JTCajvZEYa4pOinHtewBQd/a15hb9Z5iGje0c2UNe8ygLhFfJ/Syp1djqtEj" +
            "AlSlQ19ZuW2P0BtNkEXK9wUfxledAIrrjinGD/XG8FB/gIW9eL/OICJ3wvxJ" +
            "U/wB4bedUko6Np2MVdZdpJ5TfjhPt/3I/Oa6TeMFxBx84cBcdd+hyNMkQ88T" +
            "Deyrfk/DEOkGfBXosYZ7Sl/CIxuVeTTj5e49sP341dm8u6G5+xxQTLjavbId" +
            "37Edx8QSxaUXDA3jlc+2M12zKZU8WdikfcJC36pWCZ3xbeK8RPua+VL3nOOd" +
            "NQRtbqJRS5p1Zn+PpBF3GaM5MT1uqqhy2uxg6YSlfAGH/0O+wfutS0FGuh+y" +
            "b3ZsE6Pf3dWSUqjFhqGdN+W/x2D+0jMl2Z2dZGXfXjHgooyBIxbp9iOFHH8K" +
            "QK05Qt9cizPLZ3Lktb1fZu/omb83lvnmTcpO3orS8bQQseImEa5cO5RGSHhy" +
            "UGty0uiI6KFAG1AVxGjx0Ln8yKloAl6kInJRJW8sp6h/ecpvYeaB5Jnupd6b" +
            "aZ/7P4b4zCu1H2lPjaxUyZTZ+PuqTdyIqmTIoVpFd/FSC1RtSawWS+3bWJTx" +
            "h7WKM2FvidcDUjnW3rtm2QfewGrEfo6ylwtcudRe7XuDfo3H/zPh1MBQsyrf" +
            "TL5wviCaeH3ESpAuPONKHidTJBN81ZbWW9a5QyjG5NPiqqtEgM7Hx/G0rQx6" +
            "H2RpPkMVGKpBRq00Sgpfj8RqKTA1z7FdzvU552iPf/wiRjHhkBUoMhiD4W5Y" +
            "JA4dU4UfsymKq+le6b/MRnu/q2ge8lcr03memXmL84ImPWbPPUCI8WjZKnXF" +
            "0g4lmUR+Dv1j89bE1qTATTYasVxTE4uCKs6hbdlHMbebPvIgv7TpC+gK9hdY" +
            "SBxfyxneJm1Hzn07Myvkh3T2Vh3tEO7tO3zmSkHrI6ukivKHhlNGXUO2DHLK" +
            "m8h2aejfyByS22LmUdc+3kD1fAJ//iteEQwvZ0u4Wg5oublfDeflk+LaOp86" +
            "2pmf3o+04s67oj5YXGg+s+XBOB6tR6LZzn0etc34iWFmla1+4jA31bkWZX8q" +
            "iURLhPNQWjRvXWrrSO09vuR99MfaoP05F8FwT15IQ5mexZ2UFb5Nl8IZZJDq" +
            "CVJl6zqbPh11umYfioYfKaAaT+ATL8u8gyo2DS7g+3pgVbK3lSMH5whc1ew0" +
            "UPqWykN3625y7fMl9pGmlRQJSf0kVUnV+11+b99ctajhuVASS5KLRcCHK7Ex" +
            "7owYy1vmjgYHousDIGpVvpUXpehczLUFAVMRh/QvFg8+6Y6pP3ihUw+zyfSK" +
            "12i9eD3ZxctiwAKDyVju69Wh7VZ73FF2SqxvSAtx1uApvt38akplKMOX1Vjo" +
            "qn/L5JiQelHKG/3ZeLHxYv0Dpu447FaWThzLooOu5FC+hKzIC0Odtf1NxLua" +
            "1I2iK9OXyvSkiXriVMYJqvXqEndeHNIQ36pXFwu8Nau86BsEvPrM4HLPJuTB" +
            "m+iQzRSWr8cbLoIb0qvtmmLs3jV1e8KH79xdDv3Rkqs/rag4ubOg2zPsVZK8" +
            "lCXp4+ayc2L09d6lrfE0nYTtlGV22oOgJruxt4iPU9et+8z2GzLMmJ0Y1v4G" +
            "CTWUP7EBuqvFJC1NEOF+o+1bduhRJAKPa46rkOlJUDCoZgW3FWzYfgF5DDek" +
            "TLx/2cE7v92f9mGrT4ueDQ9nzH3sImjMEt76qkIdh7t5QWc9NyPdAFSnqDh7" +
            "ig6qYCT2rHDUEPGZuRf+oMAJFgY9Pw0jdXNhFT2KD1t4lp5BjkaxUzfiyp17" +
            "5O8/59KTKMRH0Ei3TNfL9EFvWeOoTkFm4Y/uX5wiLfSfeCmD9hbrDESYE58l" +
            "59kdBHvdWtRX7vH5HNwQudaUzSiDfGZ5g647A/bcZDl9JXvllnBeF2numrlk" +
            "RdDJaxdHVdVUoxumG3sc9L/2zn3t1bS726YPG29jo1eRuovh3b6OKwEePhZh" +
            "FvV1ZGwsDRrl6k7ns7i6aGBLzD8+qXrZOhWsF8+Yb7Hf8Pjr4eW55a0Mo1cn" +
            "L8yIuN50t/UdFFk0XyOept2oQN+/7A7ks3MzK3ZNLYZWAytSB8QapwQuVsRZ" +
            "ty0cV2o6tjgIyEwLyzyP9UyokBi5vFW9euTkGCI1ZLsyeQR+Au7NYjHlEee3" +
            "ueHxvLk3D6jidjbhVoUqDs73WiplTKGk4BrEXLeFRVCd1WklorBdcymoT0av" +
            "xG04VgCWwOE7MwhQ+zS30Q++Pn2nsh6EVvi0trkJHZiCYXmVO4q6NzAvLgUz" +
            "NYPiFM4Fqmhub091nvUzDPblKMu9oXawm4kIlntaz9t4gTS1LHAza77H3AT9" +
            "CG0sq2AmVWP80QgzZV9E7Sdd46IKHhvb6nBWQNNHJGQf1/YM7cps+p64+1nX" +
            "+R129hE/mr0Sl/v/JC7O2w3j8uus9TU1xg5BQCpu7DaaLMkAhvNEtTAadq8X" +
            "STY2z67jrryrRxfl2VlYWD2Svjlpj8UHzqWsWh5/W2pbwl6VdSo9cyLgw1Rg" +
            "lup7jZWDEU082aJXQRyRHYQiwhjhNWDTI1atdgP9oEwH8ZZpK8b+tBnBmd9B" +
            "08/6oDKSC6zh1LPf6nyfAQ80S35C3tOd6D2kX5uWZlj3tZCt5ba3tGs6WIw5" +
            "TJk+7C8q0TfAUyECjzOOBZqdKijym1MQbLMk/E0QDz/rsRJliz1iFbC0un+t" +
            "c+P++Sfz8YiVto04xOQaJP2JfXtb5kZDCgF81XaAVJOiwGFccrY813zwk8XV" +
            "XNNXenCUhT8+vfdyQ2gRGtKCPYJYVb+ilicpKxH1mrUefLH/BsMAvSgD58Nj" +
            "MUvvvl1YfGFYrJijWydYtEWNlZ5Im/pcV/LRk7v8Pe3zaVxEzLsa9u7gvyDR" +
            "R7lBawROfKRil08WhB1wT/pL1eg9/nzWGMEafxferFC11uaFlZKqMijJ7Eje" +
            "5Fh7Z5nrS9PtqRRffldEjKnw2bA+vSPFpSjqlM38WHACEqY3rtIkEpsoBKye" +
            "DJlBqH8TNX40hkJXCDRGVr4N/ATE/ljOXFjBDM07HYbMjx6Vah8jYDeBkQFA" +
            "ef0rN9L+Jy/2MmenMFclMyeXHX8DBKIA+ZOB/pUffwPDR4H5+xcYchnyN4Ci" +
            "lPNDtReQTI78DbIwBdl0T/I/suQfc/P25P4jT/4Gl42C+56MSyZTknE4Af+u" +
            "ROR6IpiCo7TvF5z/lSv/OD6Zv6L9SpHcxU9yXU6Ogrzye2QKZXKXQcilJMpB" +
            "SNS/NYjibwxCLu1Q1lY5LeAXGtVvTDUTBQYPBFCqQrs4Qq7KcFIQGikJZOrQ" +
            "Lijyc7cAZfXQAf4/UWcXJvlpmNK9ewyAXc7vu6DID3U8FKgARsAeh+ldcOQN" +
            "KRdlmjABdjsX7sIi7xF4KVgJrIC9GtpdeOS7CzcFz4AdsGuf8Q+MFvj9zrXz" +
            "id/5do7z+9P/ANzW3e4EGwAA",
          0x4f666997,
          kotlin(
            "com/hello/Hello.kt",
            """
              package com.hello

              interface Hello {
                val hello : String
                fun greet(name : String) : String
              }
            """,
          ),
          kotlin(
            "com/hello/Person.kt",
            """
              package com.hello

              data class Person(val name : String, val age : Int) : Hello {
                override val hello get() = "hi"
                override fun greet(name : String) = hello + " " + name
             }
            """,
          ),
        ),
      )
    var count = 0
    check(*testFiles) { file ->
      file.accept(
        object : AbstractUastVisitor() {
          override fun visitCallExpression(node: UCallExpression): Boolean {
            val resolved = node.resolve()
            assertNotNull(resolved)

            if (node.isConstructorCall()) {
              assertTrue(resolved!!.isConstructor)
              assertEquals("Person", resolved.name)
            } else {
              assertFalse(resolved!!.isConstructor)
              assertEquals("greet", resolved.name)
            }

            count++
            return super.visitCallExpression(node)
          }
        }
      )
    }
    assertEquals(2, count)
  }
}
