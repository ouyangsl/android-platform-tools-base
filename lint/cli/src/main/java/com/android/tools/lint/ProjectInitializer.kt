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

package com.android.tools.lint

import com.android.SdkConstants
import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.SdkConstants.ATTR_PATH
import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_KT
import com.android.SdkConstants.FD_JARS
import com.android.SdkConstants.FD_RES
import com.android.SdkConstants.FN_PUBLIC_TXT
import com.android.SdkConstants.FN_RESOURCE_TEXT
import com.android.SdkConstants.VALUE_FALSE
import com.android.SdkConstants.VALUE_TRUE
import com.android.ide.common.repository.ResourceVisibilityLookup
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.AndroidTargetHash.PLATFORM_HASH_PREFIX
import com.android.sdklib.SdkVersionInfo
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.client.api.UastParser
import com.android.tools.lint.detector.api.Desugaring
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Platform
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Project.DependencyKind
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceSetType
import com.android.tools.lint.model.LintModelSerialization
import com.android.tools.lint.model.LintModelVariant
import com.android.utils.XmlUtils
import com.android.utils.XmlUtils.getFirstSubTag
import com.android.utils.XmlUtils.getNextTag
import com.android.utils.usLocaleCapitalize
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import com.google.common.io.ByteStreams
import com.google.common.io.Files
import com.intellij.pom.java.LanguageLevel
import java.io.File
import java.io.File.pathSeparatorChar
import java.io.File.separator
import java.io.File.separatorChar
import java.io.IOException
import java.util.EnumSet
import java.util.HashSet
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlin.math.max
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

private const val TAG_PROJECT = "project"
private const val TAG_MODULE = "module"
private const val TAG_CLASSES = "classes"
private const val TAG_CLASSPATH = "classpath"
private const val TAG_KLIB = "klib"
private const val TAG_SRC = "src"
private const val TAG_DEP = "dep"
private const val TAG_ROOT = "root"
private const val TAG_MANIFEST = "manifest"
private const val TAG_RESOURCE = "resource"
private const val TAG_AAR = "aar"
private const val TAG_JAR = "jar"
private const val TAG_SDK = "sdk"
private const val TAG_JDK = "jdk"
private const val TAG_JDK_BOOT_CLASS_PATH = "jdk-boot-classpath"
private const val TAG_LINT_CHECKS = "lint-checks"
private const val TAG_BASELINE = "baseline"
private const val TAG_MERGED_MANIFEST = "merged-manifest"
private const val TAG_CACHE = "cache"
private const val TAG_AIDL = "aidl"
private const val TAG_PROGUARD = "proguard"
private const val TAG_ANNOTATIONS = "annotations"
private const val ATTR_COMPILE_SDK_VERSION = "compile-sdk-version"
private const val ATTR_TEST = "test"
private const val ATTR_GENERATED = "generated"
private const val ATTR_NAME = "name"
private const val ATTR_FILE = "file"
private const val ATTR_EXTRACTED = "extracted"
private const val ATTR_DIR = "dir"
private const val ATTR_JAR = "jar"
private const val ATTR_MODULE = "module"
private const val ATTR_INCOMPLETE = "incomplete"
private const val ATTR_JAVA8_LIBS = "android_java8_libs"
private const val ATTR_DESUGAR = "desugar"
private const val ATTR_JAVA_LEVEL = "javaLanguage"
private const val ATTR_KOTLIN_LEVEL = "kotlinLanguage"
private const val ATTR_MODEL = "model"
private const val ATTR_PARTIAL_RESULTS_DIR = "partial-results-dir"
private const val ATTR_KIND = "kind"

/**
 * Compute a list of lint [Project] instances from the given XML descriptor files. Each descriptor
 * is considered completely separate from the other (e.g. you can't have library definitions in one
 * referenced from another descriptor.)
 */
fun computeMetadata(client: LintClient, descriptor: File): ProjectMetadata {
  val initializer = ProjectInitializer(client, descriptor, descriptor.parentFile ?: descriptor)
  return initializer.computeMetadata()
}

/**
 * Result data passed from parsing a project metadata XML file - returns the set of projects, any
 * SDK or cache directories configured within the file, etc.
 */
data class ProjectMetadata(
  /** List of projects. Will be empty if there was an error in the configuration. */
  val projects: List<Project> = emptyList(),
  /** A baseline file to apply, if any. */
  val baseline: File? = null,
  /** The SDK to use, if overriding the default. */
  val sdk: File? = null,
  /** The JDK to use, if overriding the default. */
  val jdk: File? = null,
  /** The cache directory to use, if overriding the default. */
  val cache: File? = null,
  /** A map from module to a merged manifest for that module, if any. */
  val mergedManifests: Map<Project, File?> = emptyMap(),
  /** A map from module to a baseline to apply to that module, if any. */
  val moduleBaselines: Map<Project, File?> = emptyMap(),
  /** List of custom check JAR files to apply everywhere. */
  val globalLintChecks: List<File> = emptyList(),
  /** A map from module to a list of custom rule JAR files to apply, if any. */
  val lintChecks: Map<Project, List<File>> = emptyMap(),
  /** list of boot classpath jars to use for non-Android projects */
  val jdkBootClasspath: List<File> = emptyList(),
  /** Target platforms we're analyzing. */
  val platforms: EnumSet<Platform>? = null,
  /** Set of external annotations.zip files or external annotation directories. */
  val externalAnnotations: List<File> = emptyList(),
  /**
   * If true, the project metadata being passed in only represents a small subset of the real
   * project sources, so only lint checks which can be run without full project context should be
   * attempted. This is what happens for "on-the-fly" checks running in the IDE.
   */
  val incomplete: Boolean = false,
  /**
   * A client name to use instead of the default; this is written into baseline files, can be
   * queried by detectors from [LintClient] etc.
   */
  val clientName: String? = null,
)

/**
 * Class which handles initialization of a project hierarchy from a config XML file.
 *
 * Note: This code uses both the term "projects" and "modules". That's because lint internally uses
 * the term "project" for what Studio (and these XML config files) refers to as a "module".
 *
 * @param client the lint handler
 * @param file the XML description file
 * @param root the root project directory (relative paths in the config file are considered relative
 *   to this directory)
 */
private class ProjectInitializer(val client: LintClient, val file: File, var root: File) {

  /** map from module name to module instance */
  private val modules = mutableMapOf<String, ManualProject>()

  /** list of global classpath jars to add to all modules */
  private val globalClasspath = mutableListOf<File>()

  /** list of global klibs to add to all modules */
  private val globalKlibs = mutableMapOf<File, DependencyKind>()

  /** map from module instance to names of modules it depends on, along with dependency kinds */
  private val dependencies: Multimap<ManualProject, Pair<String, DependencyKind>> =
    ArrayListMultimap.create()

  /** map from module to the merged manifest to use, if any */
  private val mergedManifests = mutableMapOf<Project, File?>()

  /** map from module to a list of custom lint rules to apply for that module, if any */
  private val lintChecks = mutableMapOf<Project, List<File>>()

  /** External annotations. */
  private val externalAnnotations: MutableList<File> = mutableListOf()

  /** map from module to a baseline to use for a given module, if any */
  private val baselines = mutableMapOf<Project, File?>()

  /**
   * map from aar or jar file to wrapper module name (which in turn can be looked up in
   * [dependencies])
   */
  private val jarAarMap = mutableMapOf<File, String>()

  /** Map from module name to resource visibility lookup. */
  private val visibility = mutableMapOf<String, ResourceVisibilityLookup>()

  /** A cache directory to use, if specified. */
  private var cache: File? = null

  /** Whether we're analyzing an Android project. */
  private var android: Boolean = false

  /** Desugaring operations to enable. */
  private var desugaring: EnumSet<Desugaring>? = null

  /** Compute a list of lint [Project] instances from the given XML descriptor. */
  fun computeMetadata(): ProjectMetadata {
    val document = client.getXmlDocument(file)

    if (document == null || document.documentElement == null) {
      // Lint isn't quite up and running yet, so create a placeholder context
      // in order to be able to report an error
      reportError("Failed to parse project descriptor $file")
      return ProjectMetadata()
    }

    return parseModules(document.documentElement)
  }

  /** Reports the given error message as an error to lint, with an optional element location. */
  private fun reportError(message: String, node: Node? = null) {
    // To report an error using the lint infrastructure, we have to have
    // an associated "project", but there isn't an actual project yet
    // (that's what this whole class is attempting to compute and initialize).
    // Therefore, we'll make a placeholder project. A project has to have an
    // associated directory, so we'll just randomly pick the folder containing
    // the project.xml folder itself. (In case it's not a full path, e.g.
    // just "project.xml", get the current directory instead.)
    val location =
      when {
        node != null -> client.xmlParser.getLocation(file, node)
        else -> Location.create(file)
      }
    LintClient.report(
      client = client,
      issue = IssueRegistry.LINT_ERROR,
      message = message,
      location = location,
      file = file,
    )
  }

  private fun parseModules(projectElement: Element): ProjectMetadata {
    if (projectElement.tagName != TAG_PROJECT) {
      reportError("Expected <project> as the root tag", projectElement)
      return ProjectMetadata()
    }

    val incomplete = projectElement.getAttribute(ATTR_INCOMPLETE) == VALUE_TRUE
    android = projectElement.getAttribute(ATTR_ANDROID) == VALUE_TRUE
    desugaring = handleDesugaring(projectElement)
    val client = projectElement.getAttribute(ATTR_CLIENT)

    val globalLintChecks = mutableListOf<File>()

    // First gather modules and sources, and collect dependency information.
    // The dependency information is captured into a separate map since dependencies
    // may refer to modules we have not encountered yet.
    var child = getFirstSubTag(projectElement)
    var sdk: File? = null
    var jdk: File? = null
    var baseline: File? = null
    val jdkBootClasspath = mutableListOf<File>()
    while (child != null) {
      when (val tag = child.tagName) {
        TAG_MODULE -> {
          parseModule(child)
        }
        TAG_CLASSPATH -> {
          globalClasspath.add(getFile(child, this.root))
        }
        TAG_KLIB -> {
          globalKlibs[getFile(child, this.root)] = getDependencyKind(child)
        }
        TAG_LINT_CHECKS -> {
          globalLintChecks.add(getFile(child, this.root))
        }
        TAG_ANNOTATIONS -> {
          externalAnnotations += getFile(child, this.root)
        }
        TAG_SDK -> {
          sdk = getFile(child, this.root)
        }
        TAG_JDK -> {
          jdk = getFile(child, this.root)
        }
        TAG_JDK_BOOT_CLASS_PATH -> {
          val path = child.getAttribute(ATTR_PATH)
          if (path.isNotEmpty()) {
            for (s in path.split(pathSeparatorChar)) {
              jdkBootClasspath += getFile(s, child, this.root)
            }
          } else {
            jdkBootClasspath += getFile(child, this.root)
          }
        }
        TAG_BASELINE -> {
          baseline = getFile(child, this.root)
        }
        TAG_CACHE -> {
          cache = getFile(child, this.root)
        }
        TAG_ROOT -> {
          val dir = File(child.getAttribute(ATTR_DIR))
          if (dir.isDirectory) {
            root = dir.canonicalFile
          } else {
            reportError("$dir does not exist", child)
          }
        }
        else -> {
          reportError("Unexpected top level tag $tag in $file", child)
        }
      }

      child = getNextTag(child)
    }

    // Now that we have all modules recorded, process our dependency map
    // and add dependencies between the modules
    for ((module, dependency) in dependencies.entries()) {
      val (dependencyName, dependencyKind) = dependency
      val to = modules[dependencyName]
      if (to != null) {
        module.addDirectDependency(to)
        module.setDependencyKind(to, dependencyKind)
      } else {
        reportError("No module $dependencyName found (depended on by ${module.name}")
      }
    }

    val allModules = modules.values

    // Partition the projects up such that we only return projects that aren't
    // included by other projects (e.g. because they are library projects)
    val roots = HashSet(allModules)
    for (project in allModules) {
      roots.removeAll(project.allLibraries.toSet())
    }

    val sortedModules = roots.toMutableList()
    sortedModules.sortBy { it.name }

    // Initialize the classpath. We have a single massive jar for all
    // modules instead of individual folders...
    if (globalClasspath.isNotEmpty()) {
      var useForAnalysis = true
      for (module in sortedModules) {
        if (module.getJavaLibraries(true).isEmpty()) {
          module.setClasspath(globalClasspath, false)
        }
        if (module.javaClassFolders.isEmpty()) {
          module.setClasspath(globalClasspath, useForAnalysis)
          // Only allow the .class files in the classpath to be bytecode analyzed once;
          // for the remainder we only use it for type resolution
          useForAnalysis = false
        }
      }
    }

    if (globalKlibs.isNotEmpty()) {
      for (module in sortedModules) {
        if (module.klibs.isEmpty()) {
          module.klibs += globalKlibs
        }
      }
    }

    computeResourceVisibility()

    return ProjectMetadata(
      projects = sortedModules,
      sdk = sdk,
      jdk = jdk,
      baseline = baseline,
      globalLintChecks = globalLintChecks,
      lintChecks = lintChecks,
      externalAnnotations = externalAnnotations,
      cache = cache,
      moduleBaselines = baselines,
      mergedManifests = mergedManifests,
      incomplete = incomplete,
      jdkBootClasspath = jdkBootClasspath,
      platforms = if (android) Platform.ANDROID_SET else Platform.JDK_SET,
      clientName = client,
    )
  }

  private fun handleDesugaring(element: Element): EnumSet<Desugaring>? {
    var desugaring: EnumSet<Desugaring>? = null

    if (VALUE_TRUE == element.getAttribute(ATTR_JAVA8_LIBS)) {
      desugaring = EnumSet.of(Desugaring.JAVA_8_LIBRARY)
    }

    val s = element.getAttribute(ATTR_DESUGAR)
    if (s.isNotEmpty()) {
      for (option in s.split(",")) {
        var found = false
        for (v in Desugaring.values()) {
          if (option.equals(other = v.name, ignoreCase = true)) {
            if (desugaring == null) {
              desugaring = EnumSet.of(v)
            } else {
              desugaring.add(v)
            }
            found = true
            break
          }
        }
        if (!found) {
          // One of the built-in constants? Desugaring.FULL etc
          try {
            val fieldName = option.uppercase(Locale.ROOT)
            val cls = Desugaring::class.java

            @Suppress("UNCHECKED_CAST")
            val v = cls.getField(fieldName).get(null) as? EnumSet<Desugaring> ?: continue
            if (desugaring == null) {
              desugaring = EnumSet.noneOf(Desugaring::class.java)
            }
            desugaring?.addAll(v)
          } catch (ignore: Throwable) {}
        }
      }
    }

    return desugaring
  }

  private fun computeResourceVisibility() {
    // TODO: We don't have dependency information from one AAR to another; we'll
    // need to assume that all of them are in a flat hierarchy
    for (module in modules.values) {
      val aarDeps = mutableListOf<ResourceVisibilityLookup>()
      for ((dependencyName, _) in dependencies.get(module)) {
        val visibility = visibility[dependencyName] ?: continue
        aarDeps.add(visibility)
      }

      if (aarDeps.isEmpty()) {
        continue
      } else {
        val visibilityLookup =
          if (aarDeps.size == 1) {
            aarDeps[0]
          } else {
            // Must create a composite
            ResourceVisibilityLookup.create(aarDeps)
          }
        module.setResourceVisibility(visibilityLookup)
      }
    }
  }

  private val moduleDirectories: MutableSet<File> = HashSet()

  private fun pickDirectory(moduleName: String): File {
    // Special case: if the module is a path (with an optional :suffix),
    // use this as the module directory, otherwise fall back to the default root
    val separatorIndex = moduleName.lastIndexOf(separator)
    val index = moduleName.indexOf(':', separatorIndex + 1)
    val dir =
      if (separatorIndex != -1 && index != -1) {
        File(moduleName.substring(0, moduleName.indexOf(':', separatorIndex)))
      } else if (index != -1 && index < moduleName.length - 1) {
        File(root, moduleName.substring(index + 1))
      } else {
        File(root, moduleName)
      }

    // Make sure it's unique to this module
    if (moduleDirectories.add(dir)) {
      return dir
    }
    var count = 2
    while (true) {
      val unique = File(dir.path + count)
      if (moduleDirectories.add(unique)) {
        return unique
      }
      count++
    }
  }

  private fun parseModule(moduleElement: Element) {
    val name: String = moduleElement.getAttribute(ATTR_NAME)
    val library = moduleElement.getAttribute(ATTR_LIBRARY) == VALUE_TRUE
    val android = moduleElement.getAttribute(ATTR_ANDROID) != VALUE_FALSE
    val buildApi: String = moduleElement.getAttribute(ATTR_COMPILE_SDK_VERSION)
    val desugaring = handleDesugaring(moduleElement) ?: this.desugaring

    if (android) {
      this.android = true
    }

    val javaLanguageLevel =
      moduleElement.getAttribute(ATTR_JAVA_LEVEL).let { level ->
        if (level.isNotBlank()) {
          val languageLevel =
            LanguageLevel.parse(level)
              ?: run {
                reportError("Invalid Java language level \"$level\"", moduleElement)
                null
              }
          languageLevel
        } else {
          null
        }
      }

    val kotlinLanguageLevel =
      moduleElement.getAttribute(ATTR_KOTLIN_LEVEL).let { level ->
        if (level.isNotBlank()) {
          val languageLevel =
            LanguageVersion.fromVersionString(level)
              ?: run {
                reportError("Invalid Kotlin language level \"$level\"", moduleElement)
                null
              }
          if (languageLevel != null) {
            LanguageVersionSettingsImpl(
              languageLevel,
              ApiVersion.createByLanguageVersion(languageLevel),
            )
          } else {
            null
          }
        } else {
          null
        }
      }

    val dir = pickDirectory(name).let { if (it.isDirectory) it else root }

    val partialResultsDir: File? =
      if (moduleElement.hasAttribute(ATTR_PARTIAL_RESULTS_DIR)) {
        getFile(moduleElement.getAttribute(ATTR_PARTIAL_RESULTS_DIR), moduleElement, dir)
      } else {
        null
      }

    val generatedSources = mutableListOf<File>()
    val testSources = mutableListOf<File>()

    val model =
      if (moduleElement.hasAttribute(ATTR_MODEL)) {
        LintModelSerialization.readModule(getFile(moduleElement, dir, ATTR_MODEL, false))
      } else {
        null
      }

    val module =
      ManualProject(
        client,
        dir,
        name,
        library,
        android,
        partialResultsDir,
        testSources,
        generatedSources,
        model?.defaultVariant(),
      )
    modules[name] = module

    val sources = mutableListOf<File>()
    val resources = mutableListOf<File>()
    val manifests = mutableListOf<File>()
    val classes = mutableListOf<File>()
    val classpath = mutableListOf<File>()
    val lintChecks = mutableListOf<File>()
    var baseline: File? = null
    var mergedManifest: File? = null

    var child = getFirstSubTag(moduleElement)
    while (child != null) {
      when (child.tagName) {
        TAG_MANIFEST -> {
          manifests.add(getFile(child, dir))
        }
        TAG_MERGED_MANIFEST -> {
          mergedManifest = getFile(child, dir)
        }
        TAG_SRC -> {
          val file = getFile(child, dir)
          when {
            child.getAttribute(ATTR_GENERATED) == VALUE_TRUE -> generatedSources.add(file)
            child.getAttribute(ATTR_TEST) == VALUE_TRUE -> testSources.add(file)
            else -> sources.add(file)
          }
        }
        TAG_RESOURCE -> {
          resources.add(getFile(child, dir))
        }
        TAG_CLASSES -> {
          classes.add(getFile(child, dir))
        }
        TAG_CLASSPATH -> {
          classpath.add(getFile(child, dir))
        }
        TAG_AAR -> {
          // Specifying an <aar> dependency in the file is an implicit dependency
          val aar = parseAar(child, dir)
          aar?.let { dependencies.put(module, aar to DependencyKind.Regular) }
        }
        TAG_JAR -> {
          // Specifying a <jar> dependency in the file is an implicit dependency
          val jar = parseJar(child, dir)
          jar?.let { dependencies.put(module, jar to DependencyKind.Regular) }
        }
        TAG_KLIB -> {
          module.klibs[getFile(child, dir)] = getDependencyKind(child)
        }
        TAG_BASELINE -> {
          baseline = getFile(child, dir)
        }
        TAG_LINT_CHECKS -> {
          lintChecks.add(getFile(child, dir))
        }
        TAG_ANNOTATIONS -> {
          externalAnnotations += getFile(child, this.root)
        }
        TAG_DEP -> {
          val target = child.getAttribute(ATTR_MODULE)
          if (target.isEmpty()) {
            reportError("Invalid module dependency in ${module.name}", child)
          }
          dependencies.put(module, target to getDependencyKind(child))
        }
        TAG_AIDL,
        TAG_PROGUARD -> {
          // Not currently checked by lint
        }
        else -> {
          reportError("Unexpected tag ${child.tagName}", child)
          return
        }
      }

      child = getNextTag(child)
    }

    // Compute source roots
    val sourceRoots = computeSourceRoots(sources)
    val testSourceRoots = computeUniqueSourceRoots("test", testSources, sourceRoots)
    val generatedSourceRoots = computeUniqueSourceRoots("generated", generatedSources, sourceRoots)

    val resourceRoots = mutableListOf<File>()
    if (resources.isNotEmpty()) {
      // Compute resource roots. Note that these directories are not allowed
      // to overlap.
      for (file in resources) {
        val typeFolder = file.parentFile ?: continue
        val res = typeFolder.parentFile ?: continue
        if (!resourceRoots.contains(res)) {
          resourceRoots.add(res)
        }
      }
    }

    module.setManifests(manifests)
    module.setResources(resourceRoots, resources)
    module.setTestSources(testSourceRoots, testSources)
    module.setGeneratedSources(generatedSourceRoots, generatedSources)
    module.setSources(sourceRoots, sources)
    module.setClasspath(classes, true)
    module.setClasspath(classpath, false)
    module.desugaring = desugaring
    javaLanguageLevel?.let { module.javaLanguageLevel = it }
    kotlinLanguageLevel?.let { module.kotlinLanguageLevel = it }
    module.setCompileSdkVersion(buildApi)
    module.initializeSdkLevelInfo(mergedManifest, manifests.getOrNull(0))

    this.lintChecks[module] = lintChecks
    this.mergedManifests[module] = mergedManifest
    this.baselines[module] = baseline

    client.registerProject(module.dir, module)
  }

  private fun parseAar(element: Element, dir: File): String? {
    val aarFile = getFile(element, dir)

    val moduleName = jarAarMap[aarFile]
    if (moduleName != null) {
      // This AAR is already a known module in the module map -- just reference it
      return moduleName
    }

    val name = aarFile.name

    // Find expanded AAR (and cache into cache dir if necessary
    val expanded = run {
      val expanded = getFile(element, dir, ATTR_EXTRACTED, false)
      if (expanded.path.isEmpty()) {
        // Expand into temp dir
        val cacheDir =
          if (cache != null) {
            File(cache, "aars")
          } else {
            client.getCacheDir("aars", true)
          }
        val target = File(cacheDir, name)
        if (!target.isDirectory) {
          unpackZipFile(aarFile, target)
        }
        target
      } else {
        if (!expanded.isDirectory) {
          reportError("Expanded AAR path $expanded is not a directory")
        }
        expanded
      }
    }

    val partialResultsDir: File? =
      if (element.hasAttribute(ATTR_PARTIAL_RESULTS_DIR)) {
        getFile(element.getAttribute(ATTR_PARTIAL_RESULTS_DIR), element, dir)
      } else {
        null
      }

    // Create module wrapper
    val project =
      ManualProject(client, expanded, name, true, true, partialResultsDir, emptyList(), emptyList())
    project.reportIssues = false
    val manifest = File(expanded, ANDROID_MANIFEST_XML)
    if (manifest.isFile) {
      project.setManifests(listOf(manifest))
      project.initializeSdkLevelInfo(manifest, null)
    }
    val resources = File(expanded, FD_RES)
    if (resources.isDirectory) {
      project.setResources(listOf(resources), emptyList())
    }

    val jarList = mutableListOf<File>()
    val jarsDir = File(expanded, FD_JARS)
    if (jarsDir.isDirectory) {
      jarsDir.listFiles()?.let {
        jarList.addAll(it.filter { file -> file.name.endsWith(DOT_JAR) }.toList())
      }
    }
    val classesJar = File(expanded, "classes.jar")
    if (classesJar.isFile) {
      jarList.add(classesJar)
    }
    if (jarList.isNotEmpty()) {
      project.setClasspath(jarList, false)
    }

    jarAarMap[aarFile] = name
    modules[name] = project

    val publicResources = File(expanded, FN_PUBLIC_TXT)
    val allResources = File(expanded, FN_RESOURCE_TEXT)
    visibility[name] = ResourceVisibilityLookup.create(publicResources, allResources, name)

    return name
  }

  private fun parseJar(element: Element, dir: File): String? {
    val jarFile = getFile(element, dir)

    val moduleName = jarAarMap[jarFile]
    if (moduleName != null) {
      // This jar is already a known module in the module map -- just reference it
      return moduleName
    }

    val name = jarFile.name

    val partialResultsDir: File? =
      if (element.hasAttribute(ATTR_PARTIAL_RESULTS_DIR)) {
        getFile(element.getAttribute(ATTR_PARTIAL_RESULTS_DIR), element, dir)
      } else {
        null
      }

    // Create module wrapper
    val project =
      ManualProject(client, jarFile, name, true, false, partialResultsDir, emptyList(), emptyList())
    project.reportIssues = false
    project.setClasspath(listOf(jarFile), false)
    jarAarMap[jarFile] = name
    modules[name] = project
    return name
  }

  @Throws(ZipException::class, IOException::class)
  fun unpackZipFile(zip: File, dir: File) {
    forEachZippedFile(zip) { zipFile, zipEntry ->
      val targetFile = File(dir, zipEntry.name)
      Files.createParentDirs(targetFile)
      Files.asByteSink(targetFile).openBufferedStream().use {
        ByteStreams.copy(zipFile.getInputStream(zipEntry), it)
      }
    }
  }

  private fun computeUniqueSourceRoots(
    type: String,
    typeSources: MutableList<File>,
    sourceRoots: MutableList<File>,
  ): List<File> {
    when {
      typeSources.isEmpty() -> return emptyList()
      else -> {
        val typeSourceRoots = computeSourceRoots(typeSources)

        // We don't allow the test sources and product sources to overlap
        for (root in typeSourceRoots) {
          if (sourceRoots.contains(root)) {
            reportError(
              "${type.usLocaleCapitalize()} sources cannot be in the same " +
                "source root as production files; " +
                "source root $root is also a $type root"
            )
            break
          }
        }

        typeSourceRoots.removeAll(sourceRoots)
        return typeSourceRoots
      }
    }
  }

  private fun computeSourceRoots(sources: List<File>): MutableList<File> {
    val sourceRoots = mutableListOf<File>()
    if (sources.isNotEmpty()) {
      // Cache for each directory since computing root for a source file is
      // expensive
      val dirToRootCache = mutableMapOf<String, File>()
      for (file in sources) {
        val parent = file.parentFile ?: continue
        val found = dirToRootCache[parent.path]
        if (found != null) {
          continue
        }

        // Find the source root for a file. There are several scenarios.
        // Let's say the original file path is "/a/b/c/d", and findRoot
        // returns "/a/b" - in that case, great, we'll use it.
        //
        // But let's say the file is in the default package; in that case
        // findRoot returns null. Let's say the file we passed in was
        // "src/Foo.java". In that case, we can just take its parent
        // file, "src", as the source root.
        val root = findRoot(file) ?: parent

        dirToRootCache[parent.path] = root

        if (!sourceRoots.contains(root)) {
          sourceRoots.add(root)
        }
      }
    }
    return sourceRoots
  }

  private fun getDependencyKind(node: Element): DependencyKind =
    when (val kindText = node.getAttribute(ATTR_KIND)) {
      "dependsOn" -> DependencyKind.DependsOn
      "regular",
      "" -> DependencyKind.Regular
      else ->
        DependencyKind.Regular.also {
          client.log(
            Severity.WARNING,
            null,
            "Unexpected dependency kind '$kindText' parsed as 'regular'",
          )
        }
    }

  /**
   * Given an element that is expected to have a "file" attribute (or "dir" or "jar"), produces a
   * full path to the file. If [attribute] is specified, only the specific file attribute name is
   * checked.
   */
  private fun getFile(
    element: Element,
    dir: File,
    attribute: String? = null,
    required: Boolean = false,
  ): File {
    var path: String
    if (attribute != null) {
      path = element.getAttribute(attribute)
      if (path.isEmpty() && required) {
        reportError("Must specify $attribute= attribute", element)
      }
    } else {
      path = element.getAttribute(ATTR_FILE)
      if (path.isEmpty()) {
        path = element.getAttribute(ATTR_DIR)
        if (path.isEmpty()) {
          path = element.getAttribute(ATTR_JAR)
        }
      }
    }
    if (path.isEmpty()) {
      if (required) {
        reportError("Must specify file/dir/jar on <${element.tagName}>")
      }
      return File("")
    }

    return getFile(path, element, dir)
  }

  private fun getFile(path: String, element: Element, dir: File): File {
    var source = File(path)
    if (!source.isAbsolute) {
      if (!source.exists()) {
        source = File(dir, path)
        if (!source.exists()) {
          source = File(root, path)
        }
      } else {
        // Relative path: make it absolute
        return source.absoluteFile
      }
    }

    if (!source.exists()) {
      val relativePath =
        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS)
          dir.canonicalPath.replace(separator, "\\\\")
        else dir.canonicalPath
      reportError(
        "$path ${
                if (!File(path).isAbsolute) "(relative to " +
                    relativePath + ") " else ""
                }does not exist",
        element,
      )
    }
    return source
  }

  /**
   * If given a full path to a Java or Kotlin source file, produces the path to the source root if
   * possible.
   */
  private fun findRoot(file: File): File? {
    val path = file.path
    if (path.endsWith(DOT_JAVA) || path.endsWith(DOT_KT)) {
      val pkg = findPackage(file) ?: return null
      val parent = file.parentFile ?: return null
      val packageStart = max(0, parent.path.length - pkg.length)
      if (!pathMatchesPackage(pkg, path, packageStart)) {
        val actual =
          if (path.startsWith(root.path)) {
            val s = path.substring(root.path.length)
            val end = max(0, s.length - pkg.length)
            s.substring(end)
          } else {
            path.substring(packageStart)
          }
        val expected = "$separator${pkg.replace('.', separatorChar)}$separator${file.name}"
        client.log(
          Severity.INFORMATIONAL,
          null,
          "The source file ${file.name} does not appear to be in the right project location; its package implies ...$expected but it was found in ...$actual",
        )
        return null
      }
      return File(path.substring(0, packageStart))
    }

    return null
  }

  private fun pathMatchesPackage(pkg: String, path: String, packageStart: Int): Boolean =
    pkg.indices.all { i -> pkg[i] == path[i + packageStart] || pkg[i] == '.' }

  private fun findPackage(file: File): String? {
    return findPackage(file.readText(), file)
  }
}

/** Finds the package of the given Java/Kotlin source file, if possible. */
@VisibleForTesting
@Suppress("LocalVariableName")
fun findPackage(source: String, file: File): String? {
  // Don't use LintClient.readFile; this will attempt to use VFS in some cases
  // (for example, when encountering Windows file line endings, in order to make
  // sure that lint's text offsets matches PSI's text offsets), but since this
  // is still early in the initialization sequence, and we haven't set up the
  // IntelliJ environment yet, we're not ready. And for the purposes of this file
  // read, we don't actually care about line offsets at all.

  var offset = 0
  val STATE_INIT = 0
  val STATE_SLASH = 1
  val STATE_LINE_COMMENT = 2
  val STATE_BLOCK_COMMENT = 3
  val STATE_BLOCK_COMMENT_STAR = 4
  val STATE_BLOCK_COMMENT_SLASH = 5
  var blockCommentDepth = 0
  var state = STATE_INIT
  val isKotlin = file.path.endsWith(DOT_KT)
  while (offset < source.length) {
    val c = source[offset]
    when (state) {
      STATE_INIT -> {
        if (c == '/') state = STATE_SLASH
        else if (c == 'p' && source.startsWith("package", offset)) {
          val pkg = StringBuilder()
          offset += "package".length
          while (offset < source.length) {
            val p = source[offset++]
            if (p.isJavaIdentifierPart() || p == '.') {
              pkg.append(p)
            } else if (p == ';' || p == '\n' && isKotlin) {
              break
            }
          }
          return pkg.toString()
        }
      }
      STATE_SLASH ->
        when (c) {
          '/' -> {
            state = STATE_LINE_COMMENT
          }
          '*' -> {
            state = STATE_BLOCK_COMMENT
            blockCommentDepth++
          }
          else -> {
            state = STATE_INIT
          }
        }
      STATE_LINE_COMMENT -> if (c == '\n') state = STATE_INIT
      STATE_BLOCK_COMMENT -> {
        when {
          c == '*' -> state = STATE_BLOCK_COMMENT_STAR
          c == '/' && isKotlin -> state = STATE_BLOCK_COMMENT_SLASH
        }
      }
      STATE_BLOCK_COMMENT_STAR -> {
        when {
          c == '/' -> {
            blockCommentDepth--
            state =
              if (blockCommentDepth == 0) {
                STATE_INIT
              } else {
                STATE_BLOCK_COMMENT
              }
          }
          c != '*' -> {
            state = STATE_BLOCK_COMMENT
          }
        }
      }
      STATE_BLOCK_COMMENT_SLASH -> {
        if (c == '*') {
          blockCommentDepth++
        }
        if (c != '/') {
          state = STATE_BLOCK_COMMENT
        }
      }
    }

    offset++
  }
  return null
}

/**
 * A special subclass of lint's [Project] class which can be manually configured with custom source
 * locations, custom library types, etc.
 */
private class ManualProject(
  client: LintClient,
  dir: File,
  name: String,
  library: Boolean,
  private val android: Boolean,
  partialResultsDir: File?,
  private val testFiles: List<File>,
  private val generatedFiles: List<File>,
  private val variant: LintModelVariant? = null,
) : Project(client, dir, dir, partialResultsDir) {

  init {
    setName(name)
    directLibraries = mutableListOf()
    this.library = library
    // We don't have this info yet; add support to the XML to specify it
    this.buildSdk = SdkVersionInfo.HIGHEST_KNOWN_STABLE_API
    this.mergeManifests = true
  }

  override fun initialize() {
    // Deliberately not calling super; that code is for ADT compatibility
  }

  /** Adds the given project as a dependency from this project. */
  fun addDirectDependency(project: ManualProject) {
    directLibraries.add(project)
  }

  override fun isAndroidProject(): Boolean = android

  override fun isGradleProject(): Boolean = false

  override fun toString(): String = "Project [name=$name]"

  override fun equals(other: Any?): Boolean =
    // Normally Project.equals checks directory equality, but we can't
    // do that here since we don't have guarantees that the directories
    // won't overlap (and furthermore we don't actually have the directory
    // locations of each module)
    this === other || other is ManualProject && name == other.name

  override fun hashCode(): Int = name.hashCode()

  fun setJavaLanguageLevel(level: LanguageLevel) {
    this.javaLanguageLevel = level
  }

  /** Sets the given files as the manifests applicable for this module. */
  fun setManifests(manifests: List<File>) {
    this.manifestFiles = manifests
    addFilteredFiles(manifests)
  }

  /** Sets the given resource files and their roots for this module. */
  fun setResources(resourceRoots: List<File>, resources: List<File>) {
    this.resourceFolders = resourceRoots
    addFilteredFiles(resources)
  }

  /** Sets the given source files and their roots for this module. */
  fun setSources(sourceRoots: List<File>, sources: List<File>) {
    this.javaSourceFolders = sourceRoots
    addFilteredFiles(sources)
  }

  /** Sets the given source files and their roots for this module. */
  fun setTestSources(sourceRoots: List<File>, sources: List<File>) {
    this.testSourceFolders = sourceRoots
    addFilteredFiles(sources)
  }

  /** Sets the given generated source files and their roots for this module. */
  fun setGeneratedSources(sourceRoots: List<File>, sources: List<File>) {
    this.generatedSourceFolders = sourceRoots
    addFilteredFiles(sources)
  }

  /**
   * Adds the given files to the set of filtered files for this project. With a filter applied, lint
   * won't look at all sources in for example the source or resource roots, it will limit itself to
   * these specific files.
   */
  private fun addFilteredFiles(sources: List<File>) {
    if (sources.isNotEmpty()) {
      if (files == null) {
        files = mutableListOf()
      }
      files.addAll(sources)
    }
  }

  /** Sets the global class path for this module. */
  fun setClasspath(allClasses: List<File>, useForAnalysis: Boolean) =
    if (useForAnalysis) {
      this.javaClassFolders = allClasses
    } else {
      this.javaLibraries = allClasses
    }

  fun setCompileSdkVersion(buildApi: String) {
    if (buildApi.isNotEmpty()) {
      buildTargetHash =
        if (Character.isDigit(buildApi[0])) PLATFORM_HASH_PREFIX + buildApi else buildApi
      val version = AndroidTargetHash.getPlatformVersion(buildApi)
      if (version != null) {
        buildSdk = version.featureLevel
      } else {
        client.log(Severity.WARNING, null, "Unexpected build target format: %1\$s", buildApi)
      }
    }
  }

  fun setDesugaring(desugaring: Set<Desugaring>?) {
    this.desugaring = desugaring
  }

  private var resourceVisibility: ResourceVisibilityLookup? = null

  fun setResourceVisibility(resourceVisibility: ResourceVisibilityLookup?) {
    this.resourceVisibility = resourceVisibility
  }

  override fun getResourceVisibility(): ResourceVisibilityLookup {
    return resourceVisibility ?: super.getResourceVisibility()
  }

  override fun getBuildVariant(): LintModelVariant? = variant

  override fun getUastSourceList(driver: LintDriver, main: Project?): UastParser.UastSourceList? {
    val files = files ?: return null
    val initialCapacity = files.size
    val contexts = ArrayList<JavaContext>(initialCapacity)
    val testContexts = ArrayList<JavaContext>(initialCapacity)
    val generatedContexts = ArrayList<JavaContext>(initialCapacity)
    val gradleKtsContexts = ArrayList<JavaContext>(2)
    val testSet = testFiles.toSet()
    val generatedSet = generatedFiles.toSet()
    for (file in files) {
      val path = file.path
      if (path.endsWith(DOT_JAVA) || path.endsWith(DOT_KT) || path.endsWith(SdkConstants.DOT_KTS)) {
        val context = JavaContext(driver, this, main, file)

        when {
          // Figure out if this file is a Gradle .kts context
          path.endsWith(SdkConstants.DOT_KTS) -> {
            gradleKtsContexts.add(context)
          }
          testSet.contains(file) -> {
            context.sourceSetType = SourceSetType.UNIT_TESTS
            context.isTestSource = true
            if (generatedSet.contains(file)) { // files can be both tests and generated
              context.isGeneratedSource = true
            }
            testContexts.add(context)
          }
          generatedSet.contains(file) -> {
            context.sourceSetType = SourceSetType.MAIN
            context.isGeneratedSource = true
            generatedContexts.add(context)
          }
          else -> {
            context.sourceSetType = SourceSetType.MAIN
            contexts.add(context)
          }
        }
      }
    }

    return UastParser.UastSourceList(
      client.getUastParser(this),
      contexts,
      testContexts,
      emptyList(),
      generatedContexts,
      gradleKtsContexts,
    )
  }

  override fun readManifest(document: Document) {
    // Do nothing. LintDriver calls this method (possibly multiple times)
    // passing (possibly multiple) manifest files. The super implementation
    // initializes fields, such as manifestTargetSdk. We instead do this just
    // once from ProjectInitializer via initializeSdkLevelInfo.
  }

  fun initializeSdkLevelInfo(mergedManifest: File?, manifest: File?) {
    if (dom != null) {
      client.log(
        Severity.WARNING,
        IllegalStateException("Tried to initialize project SDK level info more than once"),
        null,
      )
      return
    }

    fun File.parseSilently(): Document? = XmlUtils.parseDocumentSilently(this.readText(), true)

    val mergedManifestDoc = mergedManifest?.parseSilently()
    val manifestDoc = manifest?.parseSilently()

    // There seems to be some ambiguity in whether the merged manifest can be
    // used to initialize manifestTargetSdk and manifestMinTargetSdk. We prefer
    // the merged manifest, but otherwise fall back to the manifest.
    val initFrom = mergedManifestDoc ?: manifestDoc
    initFrom?.let { super.readManifest(it) }

    // In super.readManifest, the dom is set to whichever document was passed,
    // but we set it to the manifest document, if present, as this seems to be
    // used by detectors to get the manifest.
    manifestDoc?.let { dom = it }
  }
}

private fun forEachZippedFile(file: File, step: (ZipFile, ZipEntry) -> Unit) =
  ZipFile(file).use { zipFile ->
    zipFile.entries().asSequence().filterNot(ZipEntry::isDirectory).forEach { step(zipFile, it) }
  }
