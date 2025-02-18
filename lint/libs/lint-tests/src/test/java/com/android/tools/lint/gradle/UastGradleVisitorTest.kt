/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.lint.gradle

import com.android.testutils.TestUtils
import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.parse
import com.android.tools.lint.client.api.UastGradleVisitor
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.JavaContext
import com.intellij.openapi.util.Disposer
import java.io.File
import org.intellij.lang.annotations.Language
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// Tests in this class should reflect, as though through a somewhat
// distorted mirror, the tests in GroovyGradleVisitorTest and
// LintIdeGradleVisitorTest.  The correspondence cannot be exact, as
// the syntactic and semantic differences are too big for compatibility
// to be layered on top: but test additions here should almost certainly
// be added also to the Groovy tests, and new Groovy tests should at least
// cause consideration of adding an analogue here.
class UastGradleVisitorTest {
  @get:Rule var temporaryFolder = TemporaryFolder()

  @Test
  fun testBasic() {
    check(
      """
      dependencies {
          implementation(platform(libs.compose.bom))
          implementation(platform("androidx.compose:compose-bom:2022.12.00"))
      }
      """,
      """
      checkMethodCall(statement="dependencies", unnamedArguments="{ implementation(platform(libs.compose.bom)) implementation(platform("androidx.compose:compose-bom:2022.12.00")) }")
      checkMethodCall(statement="implementation", parent="dependencies", unnamedArguments="platform(libs.compose.bom)")
      checkDslPropertyAssignment(property="implementation", value="platform(libs.compose.bom)", parent="dependencies")
      checkMethodCall(statement="platform", parent="dependencies", unnamedArguments="libs.compose.bom")
      checkMethodCall(statement="implementation", parent="dependencies", unnamedArguments="platform("androidx.compose:compose-bom:2022.12.00")")
      checkDslPropertyAssignment(property="implementation", value="platform("androidx.compose:compose-bom:2022.12.00")", parent="dependencies")
      checkMethodCall(statement="platform", parent="dependencies", unnamedArguments=""androidx.compose:compose-bom:2022.12.00"")
      """,
    )
  }

  @Test
  fun testNesting() {
    // Make sure we treat "dependencies.x" as a property, but not y (e.g. dependencies.x.y); that
    // should only be the case for x { y { ... } }
    check(
      """
      dependencies {
          x(y(z(a(b(c("hello world"))))))
          x {
             y {
                z("hello world")
             }
          }
      }
      """,
      """
      checkMethodCall(statement="dependencies", unnamedArguments="{ x(y(z(a(b(c("hello world")))))) x { y { z("hello world") } } }")
      checkMethodCall(statement="x", parent="dependencies", unnamedArguments="y(z(a(b(c("hello world")))))")
      checkDslPropertyAssignment(property="x", value="y(z(a(b(c("hello world")))))", parent="dependencies")
      checkMethodCall(statement="y", parent="dependencies", unnamedArguments="z(a(b(c("hello world"))))")
      checkMethodCall(statement="z", parent="dependencies", unnamedArguments="a(b(c("hello world")))")
      checkMethodCall(statement="a", parent="dependencies", unnamedArguments="b(c("hello world"))")
      checkMethodCall(statement="b", parent="dependencies", unnamedArguments="c("hello world")")
      checkMethodCall(statement="c", parent="dependencies", unnamedArguments=""hello world"")
      checkMethodCall(statement="x", parent="dependencies", unnamedArguments="{ y { z("hello world") } }")
      checkMethodCall(statement="y", parent="x", parentParent="dependencies", unnamedArguments="{ z("hello world") }")
      checkMethodCall(statement="z", parent="y", parentParent="x", unnamedArguments=""hello world"")
      checkDslPropertyAssignment(property="z", value=""hello world"", parent="y", parentParent="x")
      """,
    )
  }

  @Test
  fun testNesting2() {
    check(
      """
      android {
          buildTypes {
              debug {
                  packageNameSuffix = ".debug"
              }
          }
      }
      """,
      """
      checkMethodCall(statement="android", unnamedArguments="{ buildTypes { debug { packageNameSuffix = ".debug" } } }")
      checkMethodCall(statement="buildTypes", parent="android", unnamedArguments="{ debug { packageNameSuffix = ".debug" } }")
      checkMethodCall(statement="debug", parent="buildTypes", parentParent="android", unnamedArguments="{ packageNameSuffix = ".debug" }")
      checkDslPropertyAssignment(property="packageNameSuffix", value="".debug"", parent="debug", parentParent="buildTypes")
      """,
    )
  }

  @Test
  fun testLanguageLevels() {
    check(
      """
      plugins {
          id("java")
      }
      java.sourceCompatibility = JavaVersion.VERSION_1_8
      java.targetCompatibility = JavaVersion.VERSION_1_8
      android.compileOptions.sourceCompatibility = JavaVersion.VERSION_1_8
      android.defaultConfig.vectorDrawables.useSupportLibrary = true
      """,
      """
      checkMethodCall(statement="plugins", unnamedArguments="{ id("java") }")
      checkMethodCall(statement="id", parent="plugins", unnamedArguments=""java"")
      checkDslPropertyAssignment(property="id", value=""java"", parent="plugins")
      checkDslPropertyAssignment(property="sourceCompatibility", value="JavaVersion.VERSION_1_8", parent="java")
      checkDslPropertyAssignment(property="targetCompatibility", value="JavaVersion.VERSION_1_8", parent="java")
      checkDslPropertyAssignment(property="sourceCompatibility", value="JavaVersion.VERSION_1_8", parent="compileOptions", parentParent="android")
      checkDslPropertyAssignment(property="useSupportLibrary", value="true", parent="vectorDrawables", parentParent="defaultConfig")
      """,
    )
  }

  @Test
  fun testZeroArgMethod() {
    check(
      """
      buildscript {
        repositories {
          jcenter()
        }
      }
      """,
      """
      checkMethodCall(statement="buildscript", unnamedArguments="{ repositories { jcenter() } }")
      checkMethodCall(statement="repositories", parent="buildscript", unnamedArguments="{ jcenter() }")
      checkMethodCall(statement="jcenter", parent="repositories", parentParent="buildscript")
      """,
    )
  }

  @Test
  fun testPluginsDsl() {
    check(
      """
      plugins {
        id("android") version "2.2.3" apply true
      }
      """,
      """
      checkDslPropertyAssignment(property="id", value=""android"", parent="plugins")
      checkDslPropertyAssignment(property="apply", value="true", parent="android", parentParent="plugins")
      checkDslPropertyAssignment(property="version", value=""2.2.3"", parent="android", parentParent="plugins")
      checkMethodCall(statement="id", parent="plugins", unnamedArguments=""android"")
      checkMethodCall(statement="plugins", unnamedArguments="{ id("android") version "2.2.3" apply true }")
      """,
    )
  }

  @Test
  fun testPluginsAlias() {
    check(
      """
      plugins {
        alias("android-application") apply true
      }
      """,
      """
      checkDslPropertyAssignment(property="alias", value=""android-application"", parent="plugins")
      checkMethodCall(statement="alias", parent="plugins", unnamedArguments=""android-application"")
      checkMethodCall(statement="plugins", unnamedArguments="{ alias("android-application") apply true }")
      """,
    )
  }

  @Test
  fun testPluginsComputedId() {
    check(
      """
      plugins {
        id("org.jetbrains.kotlin" + ".jvm") version "1.9.0"
      }
      """,
      """
      checkDslPropertyAssignment(property="id", value=""org.jetbrains.kotlin" + ".jvm"", parent="plugins")
      checkDslPropertyAssignment(property="version", value=""1.9.0"", parent="org.jetbrains.kotlin.jvm", parentParent="plugins")
      checkMethodCall(statement="id", parent="plugins", unnamedArguments=""org.jetbrains.kotlin" + ".jvm"")
      checkMethodCall(statement="plugins", unnamedArguments="{ id("org.jetbrains.kotlin" + ".jvm") version "1.9.0" }")
      """,
    )
  }

  // Test infrastructure only below

  private fun check(@Language("kotlin-script") gradleSource: String, expected: String) {
    val (contexts, disposable) =
      parse(
        temporaryFolder = temporaryFolder,
        sdkHome = TestUtils.getSdk().toFile(),
        testFiles =
          arrayOf(
            TestFiles.java(
                // just here to give us a way to construct contexts and projects using the test
                // infrastructure
                """
                package foo;
                public class Foo {
                }
                """
              )
              .indented(),
            TestFiles.kts("build.gradle.kts", gradleSource).indented(),
          ),
      )

    val javaContext = contexts.first()
    val project = javaContext.project
    val driver = javaContext.driver
    val gradleFile = File(project.dir, "build.gradle.kts")
    Assert.assertTrue(gradleFile.isFile)
    val gradleJavaContext = JavaContext(driver, project, null, gradleFile)
    gradleJavaContext.uastParser = project.client.getUastParser(project)
    val uFile = gradleJavaContext.uastParser.parse(gradleJavaContext)!!
    gradleJavaContext.setJavaFile(uFile.sourcePsi)
    gradleJavaContext.uastFile = uFile
    val visitor = UastGradleVisitor(gradleJavaContext)
    val context = GradleContext(visitor, driver, project, null, gradleFile)
    val detector = LoggingGradleDetector()
    visitor.visitBuildScript(context, listOf(detector))

    // The order may vary slightly due to differences in the way we're handling
    // the ASTs (e.g. do we get a property callback or a method callback
    // first?), but the order should not matter to detectors
    Assert.assertEquals(
      expected.trimIndent().trim().lines().sorted().joinToString("\n"),
      detector.toString().trim().lines().sorted().joinToString("\n"),
    )

    Disposer.dispose(disposable)
  }

  // Keep in sync with the detector and tests in LintIdeGradleVisitorTest
  // TODO: Consider including the source spans too?
  class LoggingGradleDetector : Detector(), GradleScanner {
    private val sb = StringBuilder()

    private fun log(method: String, vararg arguments: Pair<String, String?>) {
      sb.append(method).append('(')
      val toList: List<Pair<String, String?>> = arguments.filter { it.second != null }.toList()
      sb.append(toList.joinToString(", ") { (key, value) -> "$key=\"$value\"" })
      sb.append(')')
      sb.append('\n')
    }

    override fun visitBuildScript(context: Context) {
      log("visitBuildScript", "file" to context.file.name)
    }

    override fun checkDslPropertyAssignment(
      context: GradleContext,
      property: String,
      value: String,
      parent: String,
      parentParent: String?,
      propertyCookie: Any,
      valueCookie: Any,
      statementCookie: Any,
    ) {
      log(
        "checkDslPropertyAssignment",
        "property" to property,
        "value" to value,
        "parent" to parent,
        "parentParent" to parentParent,
      )
    }

    override fun checkMethodCall(
      context: GradleContext,
      statement: String,
      parent: String?,
      parentParent: String?,
      namedArguments: Map<String, String>,
      unnamedArguments: List<String>,
      cookie: Any,
    ) {
      log(
        "checkMethodCall",
        "statement" to statement,
        "parent" to parent,
        "parentParent" to parentParent,
        "namedArguments" to namedArguments.log(),
        "unnamedArguments" to unnamedArguments.log(),
      )
    }

    private fun List<String>.log(): String? {
      if (isEmpty()) {
        return null
      }
      return joinToString(", ").replace('\n', ' ')
    }

    private fun Map<String, String>.log(): String? {
      if (isEmpty()) {
        return null
      }
      return this.keys.sorted().joinToString(", ") { "$it=${getValue(it).replace('\n', ' ')}" }
    }

    override fun toString(): String = sb.replace(Regex(" +"), " ").trim()
  }
}
