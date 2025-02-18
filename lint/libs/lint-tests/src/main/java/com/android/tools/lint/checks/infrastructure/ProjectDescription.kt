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

package com.android.tools.lint.checks.infrastructure

import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_KT
import com.android.tools.lint.ClassName
import com.android.tools.lint.checks.infrastructure.TestFile.GradleTestFile
import com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile
import com.android.tools.lint.checks.infrastructure.TestFile.KotlinTestFile
import com.android.tools.lint.checks.infrastructure.TestFile.XmlTestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.LibraryReferenceTestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.utils.NullLogger
import com.google.common.base.Joiner
import java.io.File
import java.io.IOException
import org.junit.Assert
import org.junit.Assert.fail

/** A description of a lint test project. */
class ProjectDescription : Comparable<ProjectDescription> {
  var files: Array<out TestFile> = emptyArray()
  val dependsOn: MutableList<ProjectDescription> = mutableListOf()
  val dependsOnNames: MutableList<String> = mutableListOf()
  var dependencyGraph: String? = null
  var name: String = ""
  var type = Type.APP
    set(value) {
      field = value
      haveSetType = true
    }

  var haveSetType = false
  var report: Boolean = true
  var primary: Boolean = true
  var under: ProjectDescription? = null
  var variantName: String? = null

  /** Creates a new project description. */
  constructor()

  /** Creates a new project with the given set of files. */
  constructor(vararg files: TestFile) {
    files(*files)
  }

  /**
   * Names the project; most useful in multi-project tests where the project name will be part of
   * the error output
   *
   * @param name the name for the project
   * @return this for constructor chaining
   */
  fun name(name: String): ProjectDescription {
    this.name = name
    return this
  }

  /**
   * Sets the given set of test files as the project contents
   *
   * @param files the test files
   * @return this for constructor chaining
   */
  fun files(vararg files: TestFile): ProjectDescription {
    this.files = files
    return this
  }

  /**
   * Adds the given project description as a direct dependency for this project
   *
   * @param library the project to depend on
   * @return this for constructor chaining
   */
  fun dependsOn(library: ProjectDescription): ProjectDescription {
    if (!dependsOn.contains(library)) {
      dependsOn.add(library)
      if (library.type == Type.APP) {
        library.type = Type.LIBRARY
      }
    }
    return this
  }

  /** Adds a dependency on the given named project. */
  fun dependsOn(name: String): ProjectDescription {
    if (!dependsOnNames.contains(name)) {
      dependsOnNames.add(name)
    }
    return this
  }

  /** Adds the given test file into this project. */
  fun addFile(file: TestFile): ProjectDescription {
    if (!files.contains(file)) {
      files = (files.asSequence() + file).toList().toTypedArray()
    }
    return this
  }

  /**
   * Adds the given dependency graph (the output of the Gradle dependency task) to be constructed
   * when mocking a Gradle model for this project.
   *
   * To generate this, run for example
   *
   * ```
   *     ./gradlew :app:dependencies
   * ```
   *
   * and then look at the debugCompileClasspath (or other graph that you want to model).
   *
   * @param dependencyGraph the graph description
   * @return this for constructor chaining
   */
  fun withDependencyGraph(dependencyGraph: String): ProjectDescription {
    this.dependencyGraph = dependencyGraph
    return this
  }

  /**
   * Marks the project as an app, library or Java module
   *
   * @param type the type of project to create
   * @return this for constructor chaining
   */
  fun type(type: Type): ProjectDescription {
    this.type = type
    return this
  }

  /**
   * Places this project in a subdirectory (determined by the project name) of the given [parent]
   * project.
   */
  fun under(parent: ProjectDescription): ProjectDescription {
    this.under = parent
    return this
  }

  /**
   * Tells lint to select a particular Gradle variant. This only applies when using Gradle mocks.
   *
   * @param variantName the name of the variant to use
   * @return this, for constructor chaining
   */
  fun variant(variantName: String?): ProjectDescription {
    this.variantName = variantName
    return this
  }

  /**
   * Marks this project as reportable (the default) or non-reportable. Lint projects are usually
   * reportable, but if they depend on libraries (such as appcompat) those dependencies are marked
   * as non-reportable. Lint will still analyze those projects (for example, an unused resource
   * analysis should list resources pulled in from these libraries) but issues found within those
   * libraries will not be reported.
   *
   * @param report whether we should report issues for this project
   * @return this for constructor chaining
   */
  fun report(report: Boolean): ProjectDescription {
    this.report = report
    return this
  }

  /**
   * Checks that all the files in this project are unique. This catches cases where you've
   * accidentally specified a target more than once (where only the last will be used by lint since
   * it will overwrite any earlier occurrences.)
   */
  fun ensureUnique() {
    val targets = mutableSetOf<String>()
    for (file in files) {
      if (file is BytecodeTestFile) {
        continue // Not a single file; will potentially expand into a number of class files
      }
      val added = targets.add(file.targetRelativePath)
      if (!added) {
        if (
          (file.targetRelativePath.endsWith("/test.kt") || file.targetRelativePath == "test.kt") &&
            ClassName(file.contents, DOT_KT).className == null
        ) {
          // Just a default name assigned to a Kotlin compilation unit with no class: pick a new
          // unique name
          var next = 2
          val base = file.targetRelativePath.substring(0, file.targetRelativePath.length - 7)
          while (true) {
            val name = "${base}test${next++}.kt"
            if (targets.add(name)) {
              file.targetRelativePath = name
              break
            }
          }
          continue
        }

        fail(
          "${file.targetRelativePath} is specified multiple times; files must be unique (in older versions, lint tests would just clobber the earlier files of the same name)"
        )
      }
    }
  }

  override fun toString(): String =
    "$type:${if (name.isNotBlank()) name else ProjectDescription::class.java.simpleName}"

  /** Returns true if this project is nested under (see [under]) the given project. */
  fun isUnder(desc: ProjectDescription): Boolean {
    val under = under ?: return false
    return under == desc || under.isUnder(desc)
  }

  /**
   * Compare by dependency order such that dependencies are always listed before their dependents,
   * and order unrelated projects alphabetically.
   */
  override fun compareTo(other: ProjectDescription): Int {
    return if (this.dependsOn.contains(other)) {
      1
    } else if (other.dependsOn.contains(this)) {
      -1
    } else {
      val t1: Type = this.type
      val t2: Type = other.type
      val delta = t1.compareTo(t2)
      if (delta != 0) {
        -delta
      } else {
        this.name.compareTo(other.name)
      }
    }
  }

  companion object {
    /** Returns the path to use for the given project description under a given root. */
    fun getProjectDirectory(project: ProjectDescription, rootDir: File): File {
      var curr = project
      if (curr.under == null) {
        return File(rootDir, curr.name)
      }
      val segments: MutableList<String> = ArrayList()
      with(segments) {
        while (true) {
          add(curr.name)
          curr = curr.under ?: break
        }
        reverse()
      }
      val relativePath = Joiner.on(File.separator).join(segments)
      return File(rootDir, relativePath)
    }

    fun TestLintTask.populateProjectDirectory(
      project: ProjectDescription,
      projectDir: File,
      vararg testFiles: TestFile,
    ) {
      if (!projectDir.exists()) {
        val ok = projectDir.mkdirs()
        if (!ok) {
          throw RuntimeException("Couldn't create $projectDir")
        }
      }
      var haveGradle = false
      for (fp in testFiles) {
        if (fp is GradleTestFile) {
          haveGradle = true
          break
        }
      }
      val jars: MutableList<String> = ArrayList()
      val compiled: MutableList<CompiledSourceFile> = ArrayList()
      var missingClasses = false

      if (!allowClassNameClashes) {
        val files =
          testFiles
            .filter { it.targetPath.endsWith(DOT_JAVA) || it.targetPath.endsWith(DOT_KT) }
            .mapNotNull { testFile ->
              val className = ClassName(testFile.contents)
              val name = className.className ?: className.jvmName
              if (name != null) {
                val prefix = className.packageName?.let { "$it." } ?: ""
                val parent = testFile.targetRelativePath.substringBeforeLast('/')
                val path = "$parent: $prefix$name"
                Pair(path, testFile)
              } else {
                null
              }
            }
            .groupBy { it.first }
        for ((className, locations) in files) {
          if (locations.size > 1) {
            fail(
              "Found more than one Java or Kotlin class in the same " +
                "package that have the same class name (" +
                className.substringAfterLast(": ").replace("/", ".") +
                "), this can lead to subtle errors (and in a real project, would result in " +
                "duplicate class compilation warnings). This scenario often happens when you're " +
                "creating a Kotlin specific test from a Java example, and end up with both versions " +
                "in the same folder. To address this, rename one of the classes, or put it into its " +
                "own package, or set `allowClassNameClashes(true)` on the test lint task."
            )
          }
        }
      }

      for (fp in testFiles) {
        if (haveGradle) {
          fp.adjustGradleSourceSet()
        }
        if (fp is LibraryReferenceTestFile) {
          jars.add(fp.file.path)
          continue
        } else if ((fp is BytecodeTestFile) && fp.targetRelativePath.endsWith(DOT_JAR)) {
          // Add .jar files into .classpath
          jars.add(fp.targetRelativePath)
        }

        if (fp is CompiledSourceFile) {
          // Create these after we've populated the rest of the project,
          // since if they don't all have classfile contents specified,
          // we want to run the compiler to produce the sources which
          // can build the test files, and fail the build.
          compiled.add(fp)
          if (fp.isMissingClasses()) {
            missingClasses = true
          }
          continue
        } else if (fp is StubClassFile) {
          fp.task = this
          if (
            !allowKotlinClassStubs && fp.stubSources.any { it.targetRelativePath.endsWith(DOT_KT) }
          ) {
            error(
              "You cannot use Kotlin in a binaryStub or mavenLibrary unless you also turn on\n" +
                "`lint().allowKotlinClassStubs(true)`. Kotlin stubs work in general, but module\n" +
                "metadata is still missing, which means that if your test relies on this metadata\n" +
                "(for example to call package level functions from Kotlin, or to access things like\n" +
                "default values or inline methods), that will not work."
            )
          }
        }

        fp.createFile(projectDir)

        // Note -- lint-override.xml is only a convention in the test suite; it's
        // not something lint automatically picks up!
        if ("lint-override.xml" == fp.targetRelativePath) {
          overrideConfig = fp
          continue
        }
        if (fp is GradleTestFile) {
          // Record mocking relationship used by createProject lint callback
          var mocker = fp.getMocker(projectDir)
          if (ignoreUnknownGradleConstructs) {
            mocker = mocker.withLogger(NullLogger())
          }
          project.dependencyGraph?.let { dependencyGraph ->
            mocker = mocker.withDependencyGraph(dependencyGraph)
          }
          projectMocks[projectDir] = mocker
          mocker.primary = project.primary
          try {
            projectMocks[projectDir.canonicalFile] = mocker
          } catch (ignore: IOException) {}
        }
      }
      if (jars.isNotEmpty()) {
        val classpath: TestFile = TestFiles.classpath(*jars.toTypedArray())
        classpath.createFile(projectDir)
      }
      val manifest: File =
        if (haveGradle) {
          File(projectDir, "src/main/AndroidManifest.xml")
        } else {
          File(projectDir, ANDROID_MANIFEST_XML)
        }
      if (project.type !== Type.JAVA) {
        addManifestFileIfNecessary(manifest)
      }

      if (missingClasses) {
        // Create all sources before attempting to compile them
        for (fp in compiled) {
          fp.source.createFile(projectDir)
        }
        for (fp in compiled) {
          if (fp.isMissingClasses()) {
            if (fp.compile(projectDir, jars)) {
              break
            }
          }
        }
        fail("One or more compiled source files were missing class file encodings")
      } else {
        CompiledSourceFile.createFiles(projectDir, compiled)
      }

      if (configuredOptions != null) {
        if (testFiles.any { it.targetRelativePath == "lint.xml" }) {
          fail(
            "Cannot combine lint.xml with `configureOption`; add options as <option> elements in your custom lint.xml instead"
          )
        }
        val sb = StringBuilder()
        sb.append("<lint>\n")
        for ((key, value) in configuredOptions) {
          for (issue in issues) {
            for (option in issue.getOptions()) {
              if (option.name == key) {
                sb.append("    <issue id=\"${issue.id}\">\n")
                sb.append("        <option name=\"$key\" value=\"$value\" />\n")
                sb.append("    </issue>\n")
              }
            }
          }
        }
        sb.append("</lint>")
        val config = xml("lint.xml", sb.toString())
        config.createFile(projectDir)
      }
    }

    private fun TestFile.adjustGradleSourceSet() {
      if (ANDROID_MANIFEST_XML == targetRelativePath) {
        // The default should be src/main/AndroidManifest.xml, not just
        // AndroidManifest.xml
        // fp.to("src/main/AndroidManifest.xml");
        within("src/main")
      } else if (this is JavaTestFile && targetRootFolder == "src") {
        within("src/main/java")
      } else if (this is KotlinTestFile && targetRootFolder == "src") {
        within("src/main/kotlin")
      } else if (this is XmlTestFile && targetRootFolder == "res") {
        within("src/main/res")
      } else if (this is BytecodeTestFile) {
        for (source in getSources()) {
          source.adjustGradleSourceSet()
        }
      }
    }

    /**
     * All Android projects must have a manifest file; this one creates it if the test file didn't
     * add an explicit one.
     */
    private fun addManifestFileIfNecessary(manifest: File) {
      // Ensure that there is at least a manifest file there to make it a valid project
      // as far as Lint is concerned:
      if (!manifest.exists()) {
        val parentFile = manifest.parentFile
        if (parentFile != null && !parentFile.isDirectory) {
          val ok = parentFile.mkdirs()
          Assert.assertTrue("Couldn't create directory $parentFile", ok)
        }
        manifest.writeText(
          """
                    <?xml version="1.0" encoding="utf-8"?>
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="lint.test.pkg"
                        android:versionCode="1"
                        android:versionName="1.0" >
                    </manifest>
                    """
            .trimIndent()
        )
      }
    }
  }

  /** Describes different types of lint test projects. */
  enum class Type {
    APP,
    LIBRARY,
    JAVA,
  }
}
