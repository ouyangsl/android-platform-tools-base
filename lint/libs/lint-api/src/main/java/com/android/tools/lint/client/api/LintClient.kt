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

package com.android.tools.lint.client.api

import com.android.SdkConstants
import com.android.SdkConstants.CLASS_FOLDER
import com.android.SdkConstants.CURRENT_PLATFORM
import com.android.SdkConstants.DOT_AAR
import com.android.SdkConstants.DOT_JAR
import com.android.SdkConstants.DOT_KLIB
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.FD_ASSETS
import com.android.SdkConstants.FD_DATA
import com.android.SdkConstants.FD_GRADLE
import com.android.SdkConstants.FN_ANNOTATIONS_ZIP
import com.android.SdkConstants.GEN_FOLDER
import com.android.SdkConstants.LIBS_FOLDER
import com.android.SdkConstants.PLATFORM_LINUX
import com.android.SdkConstants.RES_FOLDER
import com.android.SdkConstants.SRC_FOLDER
import com.android.ide.common.repository.AgpVersion
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceRepository
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.util.PathString
import com.android.manifmerger.Actions
import com.android.prefs.AndroidLocationsSingleton
import com.android.resources.FolderTypeRelationship
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.SdkVersionInfo
import com.android.tools.lint.detector.api.Constraint
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Desugaring
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.endsWith
import com.android.tools.lint.detector.api.getCommonParent
import com.android.tools.lint.detector.api.getFileNameWithParent
import com.android.tools.lint.detector.api.getLanguageLevel
import com.android.tools.lint.detector.api.isManifestFolder
import com.android.tools.lint.model.LintModelAndroidLibrary
import com.android.tools.lint.model.LintModelLibrary
import com.android.tools.lint.model.PathVariables
import com.android.utils.CharSequences
import com.android.utils.Pair
import com.android.utils.SdkUtils
import com.android.utils.XmlUtils
import com.android.utils.findGradleBuildFile
import com.google.common.base.Splitter
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.common.collect.Sets
import com.intellij.openapi.util.Computable
import com.intellij.pom.java.LanguageLevel
import com.intellij.pom.java.LanguageLevel.JDK_1_7
import com.intellij.pom.java.LanguageLevel.JDK_1_8
import java.io.ByteArrayInputStream
import java.io.File
import java.io.File.separatorChar
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLClassLoader
import java.net.URLConnection
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.Locale
import kotlin.math.max
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.kxml2.io.KXmlParser
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xmlpull.v1.XmlPullParser

/**
 * Information about the tool embedding the lint analyzer. IDEs and other tools implementing lint
 * support will extend this to integrate logging, displaying errors, etc.
 */
abstract class LintClient {

  protected constructor(clientName: String) {
    LintClient.clientName = clientName
  }

  protected constructor() {
    clientName = "unknown"
  }

  /** Configurations referenced by this client. */
  @Suppress("LeakingThis")
  open val configurations: ConfigurationHierarchy = ConfigurationHierarchy(this)

  /**
   * Returns a configuration for use by the given project. The configuration provides information
   * about which issues are enabled, any customizations to the severity of an issue, etc.
   *
   * By default this method returns a [LintXmlConfiguration].
   *
   * @param project the project to obtain a configuration for
   * @param driver the current driver, if any
   * @return a configuration, never null.
   */
  open fun getConfiguration(project: Project, driver: LintDriver?): Configuration =
    configurations.getConfigurationForProject(project)

  /**
   * Returns a configuration for use for the given file.
   *
   * @param file the source file to obtain a configuration for
   * @return a configuration, or null if no configuration is found
   */
  open fun getConfiguration(file: File): Configuration? =
    configurations.getConfigurationForFolder(file.parentFile)

  /**
   * Report the given issue. This method will only be called if the configuration provided by
   * [getConfiguration] has reported the corresponding issue as enabled and has not filtered out the
   * issue with its [Configuration.ignore] method.
   *
   * @param context the context used by the detector when the issue was found
   * @param issue the issue that was found
   * @param severity the severity of the issue
   * @param location the location of the issue
   * @param message the associated user message
   * @param format the format of the description and location descriptions
   * @param fix an optional set of extra data provided by the detector for this issue; this is
   *   intended to pass metadata to the IDE to help construct quickfixes without having to parse
   *   error messages (which is brittle) or worse having to include information in the error message
   *   (for later parsing) which is required by the quickfix but not really helpful in the error
   *   message itself (such as the maxVersion for a permission tag to be added to the
   */
  @Deprecated(
    "Use the new report(Incident) method instead",
    ReplaceWith(
      "report(context, Incident(issue, message, location, fix), format)",
      "com.android.tools.lint.detector.api.Incident",
    ),
  )
  fun report(
    context: Context,
    issue: Issue,
    severity: Severity,
    location: Location,
    message: String,
    format: TextFormat,
    fix: LintFix?,
  ) {
    val incident = Incident(issue, message, location, fix)
    incident.severity = severity
    report(context, incident, format)
  }

  /** Report the given [incident] */
  abstract fun report(context: Context, incident: Incident, format: TextFormat = TextFormat.RAW)

  /**
   * Reports the given incident to lint as a conditional incident, meaning that it is not yet
   * conclusive. The [constraint] specifies a condition to check in the reporting project's context.
   * For example, using [com.android.tools.lint.detector.api.minSdkAtLeast](23) will only report
   * this incident in consuming projects where the minSdkVersion is 23 or higher.
   */
  open fun report(context: Context, incident: Incident, constraint: Constraint) {}

  /**
   * Reports the given incident to lint as a provisional incident, meaning that it is not yet
   * conclusive. Lint will later call back to the detector (corresponding to the reported incident)
   * and ask it via the [Detector.filterIncident] method to decide whether the incident should
   * really be reported or not, and optionally to customize the incident such as updating the
   * message.
   *
   * The purpose of this is to allow lint to process source files in libraries once (and report
   * potential errors via this method), and then later, when generating reports in downstream
   * modules such as the app module, quickly consider (in [Detector.filterIncident]) all the
   * potential methods and simply filter them by the local environment, such as the real app
   * minSdkVersion.
   *
   * This allows lint to only analyze the library once, and then reuse those results multiple times,
   * for each transitive usage of the library.
   */
  open fun report(context: Context, incident: Incident, map: LintMap) {}

  /**
   * Returns a [PartialResult] where a [Detector] can write state about the current project, and/or
   * read state about dependent projects. See [supportsPartialAnalysis]. [Detector]s should only
   * write state for the current project being analyzed; any changes made to other project's state
   * will not be persisted. Instead of this method, [Detector]s should call
   * [Context.getPartialResults], which ensures the correct [project] parameter is passed.
   *
   * The [project] parameter is used to load state from dependent projects, and so should always be
   * the current project that is being analyzed.
   *
   * The returned [PartialResult] contains a reference to the requested [project] so that
   * [PartialResult.map] returns the [LintMap] for [project].
   */
  open fun getPartialResults(project: Project, issue: Issue): PartialResult {
    // the default client does not support partial analysis
    error("Partial analysis is not supported by $this; must override LintClient.getPartialResults")
  }

  /**
   * Whether this client supports the "partial analysis" mechanism. When this is true, a module is
   * analyzed by lint in isolation, so [Detector]s cannot see the sources, resources, nor manifests
   * of other modules. [Detector]s _can_ retrieve [PartialResult] objects from dependent modules
   * that were already analyzed via Context.getPartialResults(ISSUE).map() and can process the
   * partial results from all modules in the final reporting phase via
   * [Detector.checkPartialResults]. [Detector]s can also report issues **provisionally** (via
   * [Context.report] with either a [Constraint] or [LintMap]). Lint will store these issues, and,
   * later, when the app module report is generated, load the provisional errors and give the
   * detectors a chance to (cheaply) filter their previously provisionally reported incidents given
   * (for example) the actual minSdkVersion, merged manifest, etc., of the final app. For
   * conditions, this is handled automatically; for manual logic via [LintMap], override
   * [Detector.filterIncident] which returns the persisted map with state to consider.
   *
   * In the IDE, for example, where we always have access to all project metadata, this returns
   * false. When this returns false, all provisionally reported issues are directly passed over to
   * the conditions or [Detector.filterIncident] for immediate filtering and reporting. This lets
   * detectors handle both scenarios in a single way.
   */
  open fun supportsPartialAnalysis(): Boolean = false

  /**
   * After partial analysis has completed, lint will call this method to store the (partial)
   * results.
   *
   * How and where the incidents are stored is left up to the client.
   *
   * Only intended for lint's own usage.
   */
  open fun storeState(project: Project) {
    error("This client does not support partial lint analysis.")
  }

  /**
   * After partial analysis has completed for all dependencies, lint will call this method during
   * error reporting to merge and validate the partial results. Only intended for lint's own usage.
   */
  open fun mergeState(roots: Collection<Project>, driver: LintDriver) {
    error("This client does not support partial lint analysis.")
  }

  /**
   * Send an exception or error message (with warning severity) to the log
   *
   * @param exception the exception, possibly null
   * @param format the error message using [java.lang.String.format] syntax, possibly null (though
   *   in that case the exception should not be null)
   * @param args any arguments for the format string
   */
  open fun log(exception: Throwable?, format: String?, vararg args: Any) =
    log(Severity.WARNING, exception, format, *args)

  /**
   * Send an exception or error message to the log
   *
   * @param severity the severity of the warning
   * @param exception the exception, possibly null
   * @param format the error message using [java.lang.String.format] syntax, possibly null (though
   *   in that case the exception should not be null)
   * @param args any arguments for the format string
   */
  abstract fun log(severity: Severity, exception: Throwable?, format: String?, vararg args: Any)

  /**
   * Returns a [XmlParser] to use to parse XML
   *
   * @return a new [XmlParser], or null if this client does not support XML analysis
   */
  abstract val xmlParser: XmlParser

  /**
   * Reads the given [file] (or in the IDE, fetches the possibly edited editor contents
   * corresponding to the given file) ad parses it into an XML document. This is a convenience
   * wrapper around [xmlParser] but allows the client to do some caching. If the optional [contents]
   * parameter is non null, it will be taken to be the content of the file.
   */
  open fun getXmlDocument(file: File, contents: CharSequence? = null): Document? {
    return try {
      if (contents?.isNotBlank() == true) {
        xmlParser.parseXml(contents, file)
      } else {
        xmlParser.parseXml(file)
      }
    } catch (exception: Exception) {
      val message =
        exception.message ?: "${exception.javaClass.simpleName} attempting to read and parse $file"
      report(
        client = this,
        issue = IssueRegistry.LINT_ERROR,
        message =
          TextFormat.TEXT.convertTo(message, TextFormat.RAW), // ensure \ paths are escaped etc
        file = file,
      )
      null
    }
  }

  /**
   * Returns a [UastParser] to use to parse Java
   *
   * @param project the project to parse, if known (this can be used to look up the class path for
   *   type attribution etc, and it can also be used to more efficiently process a set of files, for
   *   example to perform type attribution for multiple units in a single pass)
   * @return a new [UastParser]
   */
  abstract fun getUastParser(project: Project?): UastParser

  /** Returns a visitor to use to analyze Gradle build scripts. */
  abstract fun getGradleVisitor(): GradleVisitor

  /** Returns a lint TOML parser */
  fun getTomlParser(): LintTomlParser = LintTomlParser()

  /**
   * Reads the given text file and returns the content as a string
   *
   * @param file the file to read
   * @return the string to return, never null (will be empty if there is an I/O error)
   */
  abstract fun readFile(file: File): CharSequence

  /** Returns whether the given file exists */
  open fun fileExists(file: File): Boolean = file.isFile()

  open fun fileExists(
    file: File,
    requireFile: Boolean = false,
    requireDirectory: Boolean = false,
  ): Boolean {
    return when {
      requireFile -> file.isFile
      requireDirectory -> file.isDirectory
      else -> file.exists()
    }
  }

  /**
   * Reads the given binary file and returns the content as a byte array. By default this method
   * will read the bytes from the file directly, but this can be customized by a client if for
   * example I/O could be held in memory and not flushed to disk yet.
   *
   * @param file the file to read
   * @return the bytes in the file, never null
   * @throws IOException if the file does not exist, or if the file cannot be read for some reason
   */
  @Throws(IOException::class) open fun readBytes(file: File): ByteArray = file.readBytes()

  /**
   * Reads and returns the contents of a file resource.
   *
   * @param resourcePath the path to a file
   * @return the contents of the file resource
   * @throws FileNotFoundException if the resource doesn't exist
   * @throws IOException in case of an I/O error
   */
  @Throws(IOException::class)
  open fun readBytes(resourcePath: PathString): ByteArray {
    val file = resourcePath.toFile() ?: throw FileNotFoundException(resourcePath.toString())
    return readBytes(file)
  }

  /**
   * Returns whether the given [file] has been edited since the last save, or recently saved (within
   * the last [savedSinceMsAgo] milliseconds, which defaults to 5 minutes; if -1 it will not
   * consider unmodified files.)
   *
   * If unknown or unsupported by this [LintClient], returns [returnIfUnknown].
   */
  open fun isEdited(
    file: File,
    returnIfUnknown: Boolean = true,
    savedSinceMsAgo: Long = 5 * 60 * 1000L,
  ): Boolean {
    return returnIfUnknown
  }

  /**
   * Returns the list of source folders for Java source files
   *
   * @param project the project to look up Java source file locations for
   * @return a list of source folders to search for .java files
   */
  open fun getJavaSourceFolders(project: Project): List<File> = getClassPath(project).sourceFolders

  /**
   * Returns the list of generated source folders
   *
   * @param project the project to look up generated source file locations for
   * @return a list of generated source folders to search for source files
   */
  open fun getGeneratedSourceFolders(project: Project): List<File> =
    getClassPath(project).generatedFolders

  /**
   * Returns the list of output folders for class files
   *
   * @param project the project to look up class file locations for
   * @return a list of output folders to search for .class files
   */
  open fun getJavaClassFolders(project: Project): List<File> = getClassPath(project).classFolders

  /**
   * Returns the list of Java libraries
   *
   * @param project the project to look up jar dependencies for
   * @param includeProvided If true, included provided libraries too (libraries that are not
   *   packaged with the app, but are provided for compilation purposes and are assumed to be
   *   present in the running environment)
   * @return a list of jar dependencies containing .class files
   */
  open fun getJavaLibraries(project: Project, includeProvided: Boolean): List<File> =
    getClassPath(project).getLibraries(includeProvided)

  /** Returns ths list of klibs */
  open fun getKlibs(project: Project): List<File> = getClassPath(project).klibs

  /**
   * Returns the list of source folders for test source files
   *
   * @param project the project to look up test source file locations for
   * @return a list of source folders to search for .java files
   */
  open fun getTestSourceFolders(project: Project): List<File> =
    getClassPath(project).testSourceFolders

  /**
   * Returns the list of libraries needed to compile the test source files
   *
   * @param project the project to look up test source file locations for
   * @return a list of jar files to add to the regular project dependencies when compiling the test
   *   sources
   */
  open fun getTestLibraries(project: Project): List<File> = getClassPath(project).testLibraries

  /**
   * Returns the resource folders.
   *
   * @param project the project to look up the resource folders for
   * @return a list of files pointing to the resource folders, possibly empty
   */
  open fun getResourceFolders(project: Project): List<File> {
    val res = File(project.dir, RES_FOLDER)
    if (res.exists()) {
      return listOf(res)
    }

    return emptyList()
  }

  /**
   * Returns the generated resource folders.
   *
   * @param project the project to look up the generated resource folders for
   * @return a list of files pointing to the generated resource folders, possibly empty
   */
  open fun getGeneratedResourceFolders(project: Project): List<File> {
    return emptyList()
  }

  /**
   * Returns the asset folders.
   *
   * @param project the project to look up the asset folder for
   * @return a list of files pointing to the asset folders, possibly empty
   */
  open fun getAssetFolders(project: Project): List<File> {
    val assets = File(project.dir, FD_ASSETS)
    if (assets.exists()) {
      return listOf(assets)
    }

    return emptyList()
  }

  /**
   * Returns the [SdkInfo] to use for the given project.
   *
   * @param project the project to look up an [SdkInfo] for
   * @return an [SdkInfo] for the project
   */
  open fun getSdkInfo(project: Project): SdkInfo = // By default no per-platform SDK info
  DefaultSdkInfo()

  /**
   * Returns a suitable location for storing cache files of a given named type. The named type is
   * typically created as a directory within the shared cache directory. For example, from the
   * command line, lint will typically store its cache files in ~/.android/cache/. In order to avoid
   * files colliding, caches that create a lot of files should provide a specific name such that the
   * cache is isolated to a sub directory.
   *
   * Note that in some cases lint may interpret that name to provide an alternate cache. For
   * example, when lint runs in the IDE it normally uses the same cache as lint on the command line
   * (~/.android/cache), but specifically for the cache for maven.google.com repository versions, it
   * will instead point to the same cache directory as the IDE is already using for non-lint
   * purposes, in order to share data that may already exist there.
   *
   * Note that the cache directory may not exist. You can override the default location using
   * `$ANDROID_SDK_CACHE_DIR` (though note that specific lint integrations may not honor that
   * environment variable; for example, in Gradle the cache directory will **always** be
   * build/intermediates/lint-cache/.)
   *
   * @param create if true, attempt to create the cache dir if it does not exist
   * @return a suitable location for storing cache files, which may be null if the create flag was
   *   false, or if for some reason the directory could not be created
   */
  open fun getCacheDir(name: String?, create: Boolean): File? {
    var path: String? = System.getenv("ANDROID_SDK_CACHE_DIR")
    if (path != null) {
      if (name != null) {
        path += File.separator + name
      }
      val dir = File(path)
      if (create && !dir.exists()) {
        if (!dir.mkdirs()) {
          return null
        }
      }
      return dir
    }

    val home = System.getProperty("user.home")
    var relative = ".android" + File.separator + "cache"
    if (name != null) {
      relative += File.separator + name
    }
    val dir = File(home, relative)
    if (create && !dir.exists()) {
      if (!dir.mkdirs()) {
        return null
      }
    }
    return dir
  }

  /**
   * Returns the File pointing to the user's SDK install area. This is generally the root directory
   * containing the lint tool (but also platforms/ etc).
   *
   * @return a file pointing to the user's Android SDK install area
   */
  open fun getSdkHome(): File? {
    val binDir = lintBinDir
    if (binDir != null) {
      val root = binDir.parentFile
      if (root != null && root.isDirectory) {
        return root
      }
    }

    @Suppress("DEPRECATION")
    return getFileFromEnvVar(SdkConstants.ANDROID_SDK_ROOT_ENV)
      ?: getFileFromEnvVar(SdkConstants.ANDROID_HOME_ENV)
  }

  /** Returns the JDK to use when analyzing non-Android code for the given project. */
  open fun getJdkHome(project: Project? = null): File? {
    // Non android project, e.g. perhaps a pure Kotlin project
    // Gradle doesn't let you configure separate SDKs; it runs the Gradle
    // daemon on the JDK that should be used for compilation so look up the
    // current environment:
    var javaHome = System.getProperty("java.home")
    if (javaHome == null) {
      javaHome = System.getenv("JAVA_HOME")
    }
    if (javaHome != null) { // but java.home should always be set...
      val jdkHome = File(javaHome)
      if (jdkHome.isDirectory) {
        return jdkHome
      }
    }

    return null
  }

  private fun getFileFromEnvVar(name: String): File? {
    val home = System.getenv(name) ?: return null
    val file = File(home)

    if (!file.isDirectory && isUnitTest) {
      error("`\$$name` points to non-existent directory $file")
    }

    return file
  }

  /**
   * Returns the most recent platform (e.g. something like the target for
   * $ANDROID_HOME/platforms/android-31)
   */
  open fun getLatestSdkTarget(minApi: Int = 1, includePreviews: Boolean = true): IAndroidTarget? {
    return getPlatformLookup()?.getLatestSdkTarget(minApi, includePreviews)
  }

  /**
   * Locates an SDK resource (relative to the SDK root directory).
   *
   * @param relativePath A relative path (using [File.separator] to separate path components) to the
   *   given resource
   * @return a [File] pointing to the resource, or null if it does not exist
   *
   * TODO: Consider switching to a [URL] return type instead.
   */
  open fun findResource(relativePath: String): File? {
    val top =
      getSdkHome()
        ?: run {
          val file = File(relativePath)
          return when {
            file.exists() -> file.absoluteFile
            else -> null
          }
        }

    // Looked up by ExternalAnnotationRepository
    if ("annotations.zip" == relativePath) {
      // Allow Gradle builds etc to point to a specific location
      val path = System.getenv("SDK_ANNOTATIONS")
      if (path != null) {
        val file = File(path)
        if (file.exists()) {
          return file
        }
      }

      val latestPlatform = getLatestSdkTarget(minApi = SDK_DATABASE_MIN_VERSION)
      if (latestPlatform != null) {
        val file = File(latestPlatform.location, FD_DATA + File.separator + relativePath)
        if (file.isFile) {
          return file
        }
      }

      // Fallback to looking in the old location: platform-tools/api/<name> under the SDK
      val file =
        File(top, "platform-tools" + File.separator + "api" + File.separator + relativePath)
      if (file.exists()) {
        return file
      }

      return null
    }

    val file = File(top, relativePath)
    return when {
      file.exists() -> file
      else -> null
    }
  }

  private val projectInfo: MutableMap<Project, ClassPathInfo> = mutableMapOf()

  /**
   * Returns true if this project is a Gradle-based Android project
   *
   * @param project the project to check
   * @return true if this is a Gradle-based project
   */
  open fun isGradleProject(project: Project): Boolean {
    // This is not an accurate test; specific LintClient implementations (e.g.
    // IDEs or a gradle-integration of lint) have more context and can perform a more accurate
    // check
    if (
      File(project.dir, SdkConstants.FN_BUILD_GRADLE).exists() ||
        File(project.dir, SdkConstants.FN_BUILD_GRADLE_KTS).exists() ||
        File(project.dir, SdkConstants.FN_BUILD_GRADLE_DECLARATIVE).exists()
    ) {
      return true
    }

    val parent = project.dir.parentFile
    if (parent != null && parent.name == SdkConstants.FD_SOURCES) {
      val root = parent.parentFile
      if (
        root != null &&
          (File(root, SdkConstants.FN_BUILD_GRADLE).exists() ||
            File(root, SdkConstants.FN_BUILD_GRADLE_KTS).exists() ||
            File(root, SdkConstants.FN_BUILD_GRADLE_DECLARATIVE).exists())
      ) {
        return true
      }
    }

    if (parent != null && File(parent, FD_GRADLE).exists()) {
      return true
    }

    return false
  }

  /**
   * Information about class paths (sources, class files and libraries) usually associated with a
   * project.
   */
  class ClassPathInfo(
    val sourceFolders: List<File>,
    val classFolders: List<File>,
    private val libraries: List<File>,
    private val nonProvidedLibraries: List<File>,
    val testSourceFolders: List<File>,
    val testLibraries: List<File>,
    val generatedFolders: List<File>,
    val klibs: List<File> = listOf(),
  ) {

    fun getLibraries(includeProvided: Boolean): List<File> =
      if (includeProvided) libraries else nonProvidedLibraries
  }

  /**
   * Considers the given project as an Eclipse project and returns class path information for the
   * project - the source folder(s), the output folder and any libraries.
   *
   * Callers will not cache calls to this method, so if it's expensive to compute the classpath
   * info, this method should perform its own caching.
   *
   * @param project the project to look up class path info for
   * @return a class path info object, never null
   */
  protected open fun getClassPath(project: Project): ClassPathInfo {
    var info = projectInfo[project]
    if (info == null) {
      val sources = ArrayList<File>(2)
      val classes = ArrayList<File>(1)
      val generated = ArrayList<File>(1)
      val libraries = ArrayList<File>()
      val klibs = ArrayList<File>()
      // No test folders in Eclipse:
      // https://bugs.eclipse.org/bugs/show_bug.cgi?id=224708
      val tests = emptyList<File>()

      val projectDir = project.dir
      val classpathFile = File(projectDir, ".classpath")
      val haveClassPath = classpathFile.exists()
      if (haveClassPath) {
        val classpathXml = readFile(classpathFile)
        val document = CharSequences.parseDocumentSilently(classpathXml, false)
        if (document != null) {
          val tags = document.getElementsByTagName("classpathentry")
          var i = 0
          val n = tags.length
          while (i < n) {
            val element = tags.item(i) as Element
            val kind = element.getAttribute("kind")
            var addTo: MutableList<File>? = null
            when (kind) {
              "src" -> addTo = sources
              "output" -> addTo = classes
              "lib" -> addTo = libraries
            }
            if (addTo != null) {
              val path = element.getAttribute("path")
              val folder = File(projectDir, path)
              if (folder.exists()) {
                addTo.add(folder)
              } else {
                val file = File(path)
                if (file.isAbsolute && file.exists()) {
                  addTo.add(file)
                }
              }
            }
            i++
          }
        }
      }

      // Add in libraries that aren't specified in the .classpath file
      val libs = File(project.dir, LIBS_FOLDER)
      if (libs.isDirectory) {
        val jars = libs.listFiles()
        if (jars != null) {
          for (jar in jars) {
            if (endsWith(jar.path, DOT_JAR) && !libraries.contains(jar)) {
              libraries.add(jar)
            } else if (endsWith(jar.path, DOT_KLIB) && !klibs.contains(jar)) {
              klibs.add(jar)
            }
          }
        }
      }

      if (classes.isEmpty() || !haveClassPath) {
        var folder = File(projectDir, CLASS_FOLDER)
        if (folder.exists()) {
          classes.add(folder)
        } else {
          // Maven checks
          folder = File(projectDir, "target" + File.separator + "classes")
          if (folder.exists()) {
            classes.add(folder)

            // If it's maven, also correct the source path, "src" works but
            // it's in a more specific subfolder
            if (sources.isEmpty()) {
              var src = File(projectDir, "src" + File.separator + "main" + File.separator + "java")
              if (src.exists()) {
                sources.add(src)
              } else {
                src = File(projectDir, SRC_FOLDER)
                if (src.exists()) {
                  sources.add(src)
                }
              }

              val gen =
                File(
                  projectDir,
                  "target" + File.separator + "generated-sources" + File.separator + "r",
                )
              if (gen.exists()) {
                generated.add(gen)
              }
            }
          }
        }
      }

      // Fallback, in case there is no Eclipse project metadata here
      if (sources.isEmpty() || !haveClassPath) {
        val src = File(projectDir, SRC_FOLDER)
        if (src.exists()) {
          sources.add(src)
        }
        val gen = File(projectDir, GEN_FOLDER)
        if (gen.exists()) {
          generated.add(gen)
        }
      }

      info =
        ClassPathInfo(sources, classes, libraries, libraries, tests, emptyList(), generated, klibs)
      projectInfo[project] = info
    }

    return info
  }

  /**
   * A map from directory to existing projects. Usually, each directory will map to exactly one
   * project. However, in some build systems (e.g. Bazel), multiple projects can have the same root
   * directory. In this case, the directory will map to the _first_ registered project with this
   * directory. While this may not seem particularly useful, this map is generally only used when
   * the build system is known to have one project per directory (e.g. Gradle) or as a heuristic to
   * associate source files with the nearest project.
   */
  protected val dirToProject: MutableMap<File, Project> = HashMap()

  /**
   * Used for [knownProjects]. We cannot use [dirToProject] because there can be multiple projects
   * per dir.
   */
  private val projects: MutableList<Project> = ArrayList()

  /**
   * Returns a project for the given directory. This should return the same project for the same
   * directory if called repeatedly.
   *
   * @param dir the directory containing the project
   * @param referenceDir See [Project.getReferenceDir].
   * @return a project, never null
   */
  open fun getProject(dir: File, referenceDir: File): Project {
    val canonicalDir =
      try {
        // Attempt to use the canonical handle for the file, in case there
        // are symlinks etc present (since when handling library projects,
        // we also call getCanonicalFile to compute the result of appending
        // relative paths, which can then resolve symlinks and end up with
        // a different prefix)
        dir.canonicalFile
      } catch (ioe: IOException) {
        dir
      }

    val existingProject: Project? = dirToProject[canonicalDir]
    if (existingProject != null) {
      return existingProject
    }

    val project = createProject(dir, referenceDir)
    dirToProject[canonicalDir] = project
    projects.add(project)
    return project
  }

  /** Returns true if this is a known project directory. */
  fun isKnownProjectDir(dir: File): Boolean {
    val canonicalDir =
      try {
        // See getProject()
        dir.canonicalFile
      } catch (ioe: IOException) {
        dir
      }
    return dirToProject[canonicalDir] != null
  }

  /**
   * Returns the list of known projects (projects registered via [getProject] or [registerProject]).
   *
   * @return a collection of projects in any order
   */
  val knownProjects: Collection<Project>
    get() {
      return projects
    }

  /** Path variables to use when reading and writing paths */
  open val pathVariables: PathVariables
    // Lazy initialization since we'll want to overridden state (like this.sdkHome()) which
    // is not yet initialized when the class is instantiated
    get() =
      _pathVariables
        ?: PathVariables().apply {
          addDefaultPathVariables(this)
          _pathVariables = this
        }

  private var _pathVariables: PathVariables? = null // backing field for [pathVariables]

  /**
   * Registers the given project for the given directory. This can be used when projects are
   * initialized outside the client itself.
   *
   * @param dir the directory of the project, which might not be unique
   * @param project the project
   */
  open fun registerProject(dir: File, project: Project) {
    val canonicalDir =
      try {
        // Attempt to use the canonical handle for the file, in case there
        // are symlinks etc present (since when handling library projects,
        // we also call getCanonicalFile to compute the result of appending
        // relative paths, which can then resolve symlinks and end up with
        // a different prefix)
        dir.canonicalFile
      } catch (ioe: IOException) {
        dir
      }
    if (!dirToProject.containsKey(canonicalDir)) {
      dirToProject[canonicalDir] = project
    }
    projects.add(project)
  }

  protected val projectDirs: MutableSet<File> = Sets.newHashSet<File>()

  /**
   * Create a project for the given directory
   *
   * @param dir the root directory of the project
   * @param referenceDir See [Project.getReferenceDir].
   * @return a new project
   */
  protected open fun createProject(dir: File, referenceDir: File): Project {
    if (projectDirs.contains(dir)) {
      throw CircularDependencyException(
        "Circular library dependencies; check your project.properties files carefully"
      )
    }
    projectDirs.add(dir)
    return Project.create(this, dir, referenceDir)
  }

  /**
   * Perform any startup initialization of the full set of projects that lint will be run on, if
   * necessary.
   *
   * @param knownProjects the list of projects
   */
  protected open fun initializeProjects(driver: LintDriver?, knownProjects: Collection<Project>) =
    Unit

  /**
   * Perform any post-analysis cleanup of the full set of projects that lint was run on, if
   * necessary.
   *
   * @param knownProjects the list of projects
   */
  protected open fun disposeProjects(knownProjects: Collection<Project>) = Unit

  /** Trampoline method to let [LintDriver] access protected method. */
  internal fun performGetClassPath(project: Project): ClassPathInfo = getClassPath(project)

  /** Trampoline method to let [LintDriver] access protected method. */
  internal fun performInitializeProjects(driver: LintDriver, knownProjects: Collection<Project>) =
    initializeProjects(driver, knownProjects)

  /** Trampoline method to let [LintDriver] access protected method. */
  internal fun performDisposeProjects(knownProjects: Collection<Project>) =
    disposeProjects(knownProjects)

  /**
   * Returns the name of the given project
   *
   * @param project the project to look up
   * @return the name of the project
   */
  open fun getProjectName(project: Project): String = project.dir.name

  /**
   * Returns all the [IAndroidTarget] versions installed in the user's SDK install area.
   *
   * @return all the installed targets
   */
  open fun getTargets(): List<IAndroidTarget> = getPlatformLookup()?.getTargets() ?: emptyList()

  /** Cache for [getPlatformLookup] */
  private var platformLookup: PlatformLookup? = null

  /**
   * Returns a service to look up [IAndroidTarget] instances by criteria like specific API levels,
   * compileSdkVersions, hash strings and "most recent"
   */
  open fun getPlatformLookup(): PlatformLookup? {
    // Use cheaper implementation than the full AndroidSdkHandler
    // when running outside of the IDE, where there isn't a live
    // instance already; this is overridden in the IDE client
    return platformLookup
      ?: run {
        val sdkHome = getSdkHome()
        if (sdkHome != null) {
          SimplePlatformLookup.get(sdkHome).also { platformLookup = it }
        } else {
          null
        }
      }
  }

  /**
   * Returns the compile target to use for the given project
   *
   * @param project the project in question
   * @return the compile target to use to build the given project
   */
  open fun getCompileTarget(project: Project): IAndroidTarget? {
    if (!project.isAndroidProject) {
      return null
    }

    val lookup = getPlatformLookup() ?: return null
    val buildTargetHash = project.buildTargetHash
    if (buildTargetHash != null) {
      val target = lookup.getTarget(buildTargetHash)
      if (target != null) {
        return target
      }
    }

    // Match by API level instead
    val buildSdk = project.buildSdk
    val target = lookup.getTarget(buildSdk)
    if (target != null) {
      return target
    }

    // Pick the highest compilation target we can find; the build API level
    // is not known or not found, but having *any* SDK is better than not (without
    // it, most symbol resolution will fail.)
    return lookup.getLatestSdkTarget(includePreviews = false) // prefer stable
    ?: lookup.getLatestSdkTarget(includePreviews = true)
  }

  /** The highest known API level. */
  val highestKnownApiLevel: Int
    get() {
      val maxTarget = getLatestSdkTarget()
      val maxTargetApi = maxTarget?.version?.apiLevel ?: 1
      return max(maxTargetApi, SdkVersionInfo.HIGHEST_KNOWN_STABLE_API)
    }

  /** Returns the expected language level for Java source files in the given project. */
  open fun getJavaLanguageLevel(project: Project): LanguageLevel {
    val model = project.buildModule
    if (model != null) {
      val sourceCompatibility = model.javaSourceLevel
      val javaLanguageLevel = LanguageLevel.parse(sourceCompatibility)
      if (javaLanguageLevel != null) {
        return javaLanguageLevel
      }
    }

    return if (project.isAndroidProject) JDK_1_7 else LanguageLevel.JDK_11
  }

  /** Returns the expected language level for Kotlin source files in the given project. */
  open fun getKotlinLanguageLevel(project: Project): LanguageVersionSettings {
    return LanguageVersionSettingsImpl.DEFAULT
  }

  /** Returns the set of desugaring operations in effect for the given project. */
  open fun getDesugaring(project: Project): Set<Desugaring> {
    // If there's no gradle version, you're using some other build system;
    // the most likely candidate is bazel which already supports desugaring
    // so we default to true. (Proper lint integration should extend LintClient
    // anyway and override the getDesugaring method above; this is the default
    // handling.)
    val version = project.gradleModelVersion ?: return Desugaring.DEFAULT
    val languageLevel = getLanguageLevel(project, project.javaLanguageLevel)
    return getGradleDesugaring(version, languageLevel, project.isCoreLibraryDesugaringEnabled)
  }

  /**
   * Returns the super class for the given class name, which should be in VM format (e.g.
   * java/lang/Integer, not java.lang.Integer, and using $ rather than . for inner classes). If the
   * super class is not known, returns null.
   *
   * This is typically not necessary, since lint analyzes all the available classes. However, if
   * this lint client is invoking lint in an incremental context (for example, an IDE offering
   * incremental analysis of a single source file), then lint may not see all the classes, and the
   * client can provide its own super class lookup.
   *
   * @param project the project containing the class
   * @param name the fully qualified class name
   * @return the corresponding super class name (in VM format), or null if not known
   */
  open fun getSuperClass(project: Project, name: String): String? {
    assert(name.indexOf('.') == -1) { "Use VM signatures, e.g. java/lang/Integer" }

    if ("java/lang/Object" == name) {
      return null
    }

    val superClass: String? = project.superClassMap[name]
    if (superClass != null) {
      return superClass
    }

    for (library in project.allLibraries) {
      val librarySuperClass = library.superClassMap[name]
      if (librarySuperClass != null) {
        return librarySuperClass
      }
    }

    return null
  }

  /**
   * Creates a super class map for the given project. The map maps from internal class name (e.g.
   * java/lang/Integer, not java.lang.Integer) to its corresponding super class name. The root
   * class, java/lang/Object, is not in the map.
   *
   * @param project the project to initialize the super class with; this will include local classes
   *   as well as any local .jar libraries; not transitive dependencies
   * @return a map from class to its corresponding super class; never null
   */
  open fun createSuperClassMap(project: Project): Map<String, String> {
    val libraries = project.getJavaLibraries(true)
    val classFolders = project.javaClassFolders
    val classEntries = ClassEntry.fromClassPath(this, classFolders)
    if (libraries.isEmpty()) {
      return ClassEntry.createSuperClassMap(this, classEntries)
    }
    val libraryEntries = ClassEntry.fromClassPath(this, libraries)
    return ClassEntry.createSuperClassMap(this, libraryEntries, classEntries)
  }

  /**
   * Checks whether the given name is a subclass of the given super class. If the method does not
   * know, it should return null, and otherwise return [java.lang.Boolean.TRUE] or
   * [java.lang.Boolean.FALSE].
   *
   * Note that the class names are in internal VM format (java/lang/Integer, not java.lang.Integer,
   * and using $ rather than . for inner classes).
   *
   * @param project the project context to look up the class in
   * @param name the name of the class to be checked
   * @param superClassName the name of the super class to compare to
   * @return true if the class of the given name extends the given super class
   */
  open fun isSubclassOf(project: Project, name: String, superClassName: String): Boolean? = null

  /**
   * Finds any custom lint rule jars that should be included for analysis, regardless of project.
   *
   * The default implementation locates custom lint jars set via $ANDROID_LINT_JARS
   *
   * @return a list of rule jars (possibly empty).
   */
  open fun findGlobalRuleJars(driver: LintDriver?, warnDeprecated: Boolean): List<File> {
    // If isGradle, these jars are passed to lint via --lint-rule-jars.
    if (isUnitTest || isGradle) {
      return emptyList()
    }

    // Look for additional detectors registered by the user, via
    // an environment variable
    var files: MutableList<File>? = null
    val lintClassPath = System.getenv("ANDROID_LINT_JARS")
    if (lintClassPath != null && lintClassPath.isNotEmpty()) {
      val paths = lintClassPath.split(File.pathSeparator)
      for (path in paths) {
        val jarFile = File(path)
        if (jarFile.exists()) {
          if (files == null) {
            files = mutableListOf()
          } else if (files.contains(jarFile)) {
            continue
          }
          files.add(jarFile)
        }
      }
    }

    return files ?: emptyList()
  }

  /**
   * Finds any custom lint rule jars that should be included for analysis in the given project
   *
   * @param project the project to look up rule jars from
   * @return a list of rule jars (possibly empty).
   */
  open fun findRuleJars(project: Project): Iterable<File> {
    if (project.isGradleProject) {
      if (project.isLibrary && project.buildLibraryModel != null) {
        val model = project.buildLibraryModel
        if (model != null) {
          val lintJar = model.lintJar
          if (lintJar != null && lintJar.exists()) {
            return listOf(lintJar)
          }
        }
      } else if (project.subset != null) {
        // Probably just analyzing a single file: we still want to look for custom
        // rules applicable to the file
        val variant = project.buildVariant
        if (variant != null) {
          val rules = ArrayList<File>(4)
          addLintJarsFromDependencies(rules, variant.artifact.dependencies.getAll())
          val model = variant.module

          // Locally packaged jars
          rules.addAll(model.lintRuleJars.filter { it.exists() })

          if (rules.isNotEmpty()) {
            return rules
          }
        }
      } else if (project.dir.path.endsWith(DOT_AAR)) {
        val lintJar = File(project.dir, "lint.jar")
        if (lintJar.exists()) {
          return listOf(lintJar)
        }
      }
    }

    return emptyList()
  }

  /**
   * Recursively add all lint jars found recursively from the given collection of
   * [LintModelAndroidLibrary] instances into the given [lintJars] list.
   */
  private fun addLintJarsFromDependencies(
    lintJars: MutableList<File>,
    libraries: Collection<LintModelLibrary>,
  ) {
    for (library in libraries) {
      addLintJarsFromDependency(lintJars, library)
    }
  }

  /**
   * Recursively add all lint jars found from the given [LintModelAndroidLibrary] **or its
   * dependencies** into the given [lintJars] list.
   */
  private fun addLintJarsFromDependency(lintJars: MutableList<File>, library: LintModelLibrary) {
    val lintJar = library.lintJar
    if (lintJar != null && lintJar.exists()) {
      lintJars.add(lintJar)
    }

    if (library is LintModelAndroidLibrary) {
      val folder = library.folder
      if (folder.isDirectory) {
        // Local project: might have locally packaged lint jar
        // Kept for backward compatibility, see b/66166521
        val buildDir = folder.path.substringBefore("intermediates")
        val lintPaths =
          arrayOf(
            Paths.get(buildDir, "intermediates", "lint", SdkConstants.FN_LINT_JAR),
            Paths.get(
              buildDir,
              "intermediates",
              "lint_publish_jar",
              "global",
              SdkConstants.FN_LINT_JAR,
            ),
            Paths.get(
              buildDir,
              "intermediates",
              "lint_publish_jar",
              "global",
              "prepareLintJarForPublish",
              SdkConstants.FN_LINT_JAR,
            ),
          )
        for (lintPath in lintPaths) {
          val manualLintJar = lintPath.toFile()
          if (manualLintJar.exists()) {
            lintJars.add(manualLintJar)
          }
        }
      }
    }
  }

  /**
   * Opens a URL connection.
   *
   * Clients such as IDEs can override this to for example consider the user's IDE proxy settings.
   *
   * @param url the URL to read
   * @return a [URLConnection] or null
   * @throws IOException if any kind of IO exception occurs
   */
  @Throws(IOException::class)
  open fun openConnection(url: URL): URLConnection? = openConnection(url, 0)

  /**
   * Opens a URL connection.
   *
   * Clients such as IDEs can override this to for example consider the user's IDE proxy settings.
   *
   * @param url the URL to read
   * @param timeout the timeout to apply for HTTP connections (or 0 to wait indefinitely)
   * @return a [URLConnection] or null
   * @throws IOException if any kind of IO exception occurs including timeouts
   */
  @Throws(IOException::class)
  open fun openConnection(url: URL, timeout: Int): URLConnection? {
    val connection = url.openConnection()
    if (timeout > 0) {
      connection.connectTimeout = timeout
      connection.readTimeout = timeout
    }
    return connection
  }

  /** Closes a connection previously returned by [openConnection] */
  open fun closeConnection(connection: URLConnection) {
    (connection as? HttpURLConnection)?.disconnect()
  }

  /**
   * Returns true if the given directory is a lint project directory. By default, a project
   * directory is the directory containing a manifest file, but in Gradle projects for example it's
   * the root gradle directory.
   *
   * @param dir the directory to check
   * @return true if the directory represents a lint project
   */
  open fun isProjectDirectory(dir: File): Boolean =
    isManifestFolder(dir) || findGradleBuildFile(dir).exists()

  /**
   * Returns whether lint should look for suppress comments. Tools that already do this on their own
   * can return false here to avoid doing unnecessary work.
   */
  open fun checkForSuppressComments(): Boolean = true

  /**
   * Adds in any custom lint rules and returns the result as a new issue registry, or the same one
   * if no custom rules were found
   *
   * @param registry the main registry to add rules to
   * @return a new registry containing the passed in rules plus any custom rules, or the original
   *   registry if no custom rules were found
   */
  open fun addCustomLintRules(
    registry: IssueRegistry,
    driver: LintDriver?,
    warnDeprecated: Boolean,
  ): IssueRegistry {
    val jarFiles = findGlobalRuleJars(driver, warnDeprecated)
    if (jarFiles.isNotEmpty()) {
      val extraRegistries = JarFileIssueRegistry.get(this, jarFiles, null, driver)
      if (extraRegistries.isNotEmpty()) {
        return JarFileIssueRegistry.join(registry, *extraRegistries.toTypedArray())
      }
    }

    return registry
  }

  /**
   * Creates a [ClassLoader] which can load in a set of Jar files.
   *
   * @param urls the URLs
   * @param parent the parent class loader
   * @return a new class loader
   * @deprecated Use List<File> version
   */
  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Use the List<File> version")
  open fun createUrlClassLoader(urls: Array<URL>, parent: ClassLoader): ClassLoader =
    URLClassLoader(urls, parent)

  /**
   * Creates a [ClassLoader] which can load in a set of Jar files.
   *
   * @param files the jar files
   * @param parent the parent class loader
   * @return a new class loader
   */
  open fun createUrlClassLoader(files: List<File>, parent: ClassLoader): ClassLoader =
    URLClassLoader(files.mapNotNull { SdkUtils.fileToUrl(it) }.toTypedArray(), parent)

  /**
   * Returns the merged manifest of the given project. This may return null if not called on the
   * main project. Note that the file reference in the merged manifest isn't accurate; the merged
   * manifest accumulates information from a wide variety of locations.
   *
   * @return The merged manifest, if available.
   */
  open fun getMergedManifest(project: Project): Document? {
    val manifestFiles = project.manifestFiles
    if (manifestFiles.size == 1) {
      val primary = manifestFiles[0]
      try {
        val xml = primary.readText()
        val document = XmlUtils.parseDocumentSilently(xml, true)
        document?.setUserData(File::class.java.name, primary, null)
        return document
      } catch (e: IOException) {
        log(Severity.ERROR, e, "Could not read manifest $primary")
      }
    }

    return null
  }

  /**
   * Record that the given document corresponds to a merged manifest file; locations from this
   * document should attempt to resolve back to the original source location
   *
   * @param mergedManifest the document for the merged manifest
   * @param reportFile the manifest merger report file, or the report itself
   */
  open fun resolveMergeManifestSources(mergedManifest: Document, reportFile: Any) {
    mergedManifest.setUserData(MERGED_MANIFEST, reportFile, null)
  }

  /**
   * Returns true if the given node is part of a merged manifest document (already configured via
   * [resolveMergeManifestSources])
   *
   * @param node the node to look up
   * @return true if this node is part of a merged manifest document
   */
  fun isMergeManifestNode(node: Node): Boolean =
    node.ownerDocument?.getUserData(MERGED_MANIFEST) != null

  /** Cache used by [findManifestSourceNode] */
  @Suppress("MemberVisibilityCanBePrivate")
  protected val reportFileCache: MutableMap<Any, BlameFile> = Maps.newHashMap<Any, BlameFile>()

  /** Cache used by [findManifestSourceNode] */
  @Suppress("MemberVisibilityCanBePrivate")
  protected val sourceNodeCache: MutableMap<Node, Pair<File, out Node>> =
    Maps.newIdentityHashMap<Node, Pair<File, out Node>>()

  /**
   * For the given node from a merged manifest, find the corresponding source manifest node, if
   * possible
   *
   * @param mergedNode the node from the merged manifest
   * @return the corresponding manifest node in one of the source files, if possible
   */
  open fun findManifestSourceNode(mergedNode: Node): Pair<File, out Node>? {
    val doc = mergedNode.ownerDocument ?: return null
    val report = doc.getUserData(MERGED_MANIFEST) ?: return null
    val cached = sourceNodeCache[mergedNode]
    if (cached != null) {
      if (cached === NOT_FOUND) {
        return null
      }
      return cached
    }

    var blameFile = reportFileCache[report]
    if (blameFile == null) {
      try {
        when (report) {
          is File -> {
            if (report.path.endsWith(DOT_XML)) {
              // Single manifest file: no manifest merging, passed source document
              // straight through
              return Pair.of(report, mergedNode)
            }
            blameFile = BlameFile.parse(report)
          }
          is String -> {
            val lines = Splitter.on('\n').splitToList(report)
            blameFile = BlameFile.parse(lines)
          }
          is Actions -> blameFile = BlameFile.parse(report)
          else -> {
            assert(false) { report }
            blameFile = BlameFile.NONE
          }
        }
      } catch (ignore: IOException) {
        blameFile = BlameFile.NONE
      }

      @Suppress("ALWAYS_NULL") blameFile!!

      reportFileCache[report] = blameFile
    }

    var source: Pair<File, out Node>? = null
    if (blameFile !== BlameFile.NONE) {
      source = blameFile.findSourceNode(this, mergedNode)
    }

    // Cache for next time
    val cacheValue = source ?: NOT_FOUND
    sourceNodeCache[mergedNode] = cacheValue

    return source
  }

  /**
   * Returns the location for a given node from a merged manifest file. Convenience wrapper around
   * [findManifestSourceNode] and [XmlParser.getLocation].
   */
  open fun findManifestSourceLocation(mergedNode: Node): Location? {
    val source = findManifestSourceNode(mergedNode)
    if (source != null) {
      return xmlParser.getLocation(source.first, source.second)
    }

    return null
  }

  /**
   * Formats the given path
   *
   * @param file the path to compute a display name for
   * @param project the associated project, if any
   * @param format the message format to format as; defaults to [TextFormat.RAW], e.g. with
   *   backslashes and asterisks in the path escaped
   * @return a path formatted for user display
   */
  open fun getDisplayPath(
    file: File,
    project: Project? = null,
    format: TextFormat = TextFormat.TEXT,
  ): String {
    val base = project?.referenceDir ?: getRootDir()
    if (base != null) {
      val basePath = base.path
      val path = file.path
      if (path.startsWith(basePath)) {
        var length = basePath.length
        if (path.length > length && path[length] == separatorChar) {
          length++
        }
        val relative = path.substring(length)
        return TextFormat.TEXT.convertTo(relative, format)
      }
    }

    return TextFormat.TEXT.convertTo(file.path, format)
  }

  /**
   * Returns the display path of a given resource item. This is just the path relative to the
   * resource folder; not to the project. This is typically used in error messages to for example
   * reference other variations (as in "also seen in values-fr/strings.xml"). Note that like
   * [getFileNameWithParent] we deliberately use Unix file separators.
   */
  open fun getDisplayPath(item: ResourceItem, format: TextFormat = TextFormat.TEXT): String {
    if (item is Location.LocationAware) {
      return getFileNameWithParent(this, item.getLocation().file)
    } else {
      val source = item.originalSource ?: item.source
      if (source != null) {
        return getFileNameWithParent(this, source)
      } else {
        val configuration: FolderConfiguration = item.configuration
        val folder =
          FolderTypeRelationship.getRelatedFolders(item.type).firstOrNull()?.getName() ?: return ""
        val folderName = if (configuration.isDefault) folder else "$folder-$configuration"
        return folderName + '/' + item.type.getName() + "s" + DOT_XML
      }
    }
  }

  fun getDisplayPath(project: Project?, file: File, fullPath: Boolean): String {
    project ?: return file.path
    val referenceDir = project.referenceDir
    return getDisplayPath(referenceDir, file, fullPath)
  }

  fun getDisplayPath(referenceDir: File, file: File, fullPath: Boolean): String {
    var path = file.path
    if (!fullPath && path.startsWith(referenceDir.path)) {
      var chop = referenceDir.path.length
      if (path.length > chop && path[chop] == File.separatorChar) {
        chop++
        path = path.substring(chop)
        if (path.isEmpty()) {
          path = file.name
        }
        return path
      } else if (path.length == chop) {
        return file.name
      }
    }

    if (fullPath) {
      path = file.absoluteFile.toPath().normalize().toString()
    } else if (file.isAbsolute) {
      path = getRelativePath(referenceDir, file) ?: file.path
      if (containsEmbeddedParentRef(path)) {
        path = getRelativePath(referenceDir.canonicalFile, file.canonicalFile) ?: file.path
      }
    }

    return path
  }

  /**
   * Is there an embedded parent path in the given path? Should return true for "foo/bar/../baz" and
   * "..\\foo\\bar" but not "../../foo/bar".
   */
  private fun containsEmbeddedParentRef(path: String): Boolean {
    var index = 0
    while (index < path.length) {
      if (isParentRef(path, index)) {
        index += 3
      } else {
        while (index < path.length) {
          val next = path.indexOf("..", index)
          if (isParentRef(path, next)) {
            return true
          } else {
            index += 2
          }
        }
      }
    }

    return false
  }

  /**
   * Is the string at the given [index] in the given [path] a parent reference, e.g. "../" or "..\"
   * ?
   */
  private fun isParentRef(path: String, index: Int): Boolean {
    return path.startsWith("..", index) &&
      (index == path.length - 2 || path[index + 2] == '/' || path[index + 2] == '\\')
  }

  /** Obsolete; here for backwards compatibility. */
  @Deprecated("Resource repositories are now always supported", replaceWith = ReplaceWith("true"))
  fun supportsProjectResources(): Boolean = true

  /**
   * Returns the project resources, if available
   *
   * @param includeModuleDependencies if true, include merged view of all module dependencies
   * @param includeLibraries if true, include merged view of all library dependencies (this also
   *   requires all module dependencies)
   * @return the project resources, or null if not available
   */
  @Deprecated(
    "Use getResources(project, scope) instead",
    replaceWith = ReplaceWith("getResources(project, scope"),
  )
  fun getResourceRepository(
    project: Project,
    includeModuleDependencies: Boolean,
    includeLibraries: Boolean,
  ): ResourceRepository {
    val scope =
      when {
        includeLibraries -> ResourceRepositoryScope.ALL_DEPENDENCIES
        includeModuleDependencies -> ResourceRepositoryScope.LOCAL_DEPENDENCIES
        else -> ResourceRepositoryScope.PROJECT_ONLY
      }

    return getResources(project, scope)
  }

  /** Returns the resources from the given [project] with the given [scope] */
  abstract fun getResources(project: Project, scope: ResourceRepositoryScope): ResourceRepository

  /**
   * For a lint client which supports resource items (via [supportsProjectResources]) return a
   * handle for a resource item.
   *
   * @param item the resource item to look up a location handle for
   * @return a corresponding handle
   */
  open fun createResourceItemHandle(
    item: ResourceItem,
    nameOnly: Boolean = false,
    valueOnly: Boolean = true,
  ): Location.ResourceItemHandle = Location.ResourceItemHandle(this, item, nameOnly, valueOnly)

  /**
   * Creates a [XmlPullParser] for the given XML file resource.
   *
   * @param resourcePath the path to a file
   * @return the parser for the resource, or null if the resource does not exist.
   */
  @Throws(IOException::class)
  open fun createXmlPullParser(resourcePath: PathString): XmlPullParser? {
    val bytes =
      try {
        readBytes(resourcePath)
      } catch (e: FileNotFoundException) {
        return null
      }
    val parser = KXmlParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
    parser.setInput(ByteArrayInputStream(bytes), StandardCharsets.UTF_8.name())
    return parser
  }

  /** Returns the version number of this lint client, if known. */
  open fun getClientRevision(): String? = null

  /** Returns the display name of this lint client, if known. */
  open fun getClientDisplayName(): String {
    // Fallback implementation if not overridden by LintClient
    return when (clientName) {
      // This will never be CLIENT_STUDIO because this method
      // is overridden in the IDE to return the right IDE name,
      // which could be Android Studio, or IntelliJ, etc.
      CLIENT_GRADLE -> "Android Gradle Plugin"
      CLIENT_UNIT_TESTS -> "Lint Unit Test"
      CLIENT_CLI,
      CLIENT_UNKNOWN -> "Lint"
      else -> clientName
    }
  }

  /**
   * Returns the version number of this lint client, if known. This is the one meant to be displayed
   * to users; e.g. for Studio, client revision may be "3.4.0.0" and display revision might be "3.4
   * Canary 1".
   */
  open fun getClientDisplayRevision(): String? = getClientRevision()

  /**
   * Runs the given runnable under a read lock such that it can access the PSI
   *
   * @param runnable the runnable to be run
   */
  open fun runReadAction(runnable: Runnable) = runnable.run()

  /**
   * Runs the given computation under a read lock such that it can access the PSI
   *
   * @param computable the computation to perform
   * @return the result returned by the computation
   */
  open fun <T> runReadAction(computable: Computable<T>): T = computable.compute()

  /** Returns the external annotation zip files for the given projects (transitively), if any. */
  open fun getExternalAnnotations(projects: Collection<Project>): List<File> {
    val files = Lists.newArrayListWithExpectedSize<File>(2)
    for (project in projects) {
      val variant = project.buildVariant ?: continue
      for (library in variant.artifact.dependencies.getAll()) {
        if (library is LintModelAndroidLibrary) {
          // As of 1.2 this is available in the model:
          //  https://android-review.googlesource.com/#/c/137750/
          // Switch over to this when it's in more common usage
          // (until it is, we'll pay for failed proxying errors)
          try {
            val zip = library.externalAnnotations
            if (zip.exists()) {
              files.add(zip)
            }
          } catch (ignore: Throwable) {
            // Using some older version than 1.2
            val zip = File(library.resFolder.parent, FN_ANNOTATIONS_ZIP)
            if (zip.exists()) {
              files.add(zip)
            }
          }
        }
      }
    }

    return files
  }

  /** Returns the path to a given [file], given a [baseFile] to make it relative to. */
  open fun getRelativePath(baseFile: File?, file: File?): String? {
    // Based on similar code in com.intellij.openapi.util.io.FileUtilRt
    var base = baseFile
    if (base == null || file == null) {
      return null
    }
    if (!base.isDirectory) {
      base = base.parentFile
      if (base == null) {
        return null
      }
    }

    if (base.path == file.path) {
      return "."
    }

    val filePath = file.absolutePath
    var basePath = base.absolutePath

    // TODO: Make this return null if we go all the way to the root!

    basePath =
      if (!basePath.isEmpty() && basePath[basePath.length - 1] == separatorChar) basePath
      else basePath + separatorChar

    // Whether filesystem is case sensitive. Technically on OSX you could create a
    // sensitive one, but it's not the default.
    val caseSensitive = CURRENT_PLATFORM == PLATFORM_LINUX
    val l = Locale.getDefault()
    val basePathToCompare = if (caseSensitive) basePath else basePath.lowercase(l)
    val filePathToCompare = if (caseSensitive) filePath else filePath.lowercase(l)
    if (
      basePathToCompare ==
        (if (
          !filePathToCompare.isEmpty() &&
            filePathToCompare[filePathToCompare.length - 1] == separatorChar
        )
          filePathToCompare
        else filePathToCompare + separatorChar)
    ) {
      return "."
    }
    var len = 0
    var lastSeparatorIndex = 0

    while (
      len < filePath.length &&
        len < basePath.length &&
        filePathToCompare[len] == basePathToCompare[len]
    ) {
      if (basePath[len] == separatorChar) {
        lastSeparatorIndex = len
      }
      len++
    }
    if (len == 0) {
      return null
    }

    val relativePath = StringBuilder()
    for (i in len until basePath.length) {
      if (basePath[i] == separatorChar) {
        relativePath.append("..")
        relativePath.append(separatorChar)
      }
    }
    relativePath.append(filePath.substring(lastSeparatorIndex + 1))
    return relativePath.toString()
  }

  /**
   * Returns the root directory for analysis, if known. The actual source projects can be below this
   * directory, not necessarily directly.
   */
  open fun getRootDir(): File? {
    var root: File? = null
    for (project in knownProjects) {
      if (!project.reportIssues) {
        continue
      }
      root =
        if (root == null) {
          project.dir
        } else {
          getCommonParent(root, project.dir)
        }
    }

    // Workaround: we need the root project; it's not yet part of the model,
    // and adding it now would clash with simultaneous edits to decouple Gradle
    // and lint
    val parent = root?.parentFile
    if (parent != null) {
      if (
        File(parent, SdkConstants.FN_BUILD_GRADLE).exists() ||
          File(parent, SdkConstants.FN_BUILD_GRADLE_KTS).exists() ||
          File(parent, SdkConstants.FN_BUILD_GRADLE_DECLARATIVE).exists()
      ) {
        return parent
      }
    }

    return root
  }

  private fun addDefaultPathVariables(variables: PathVariables) {
    // A few additional path variables for lint checks that issues warnings files in home
    // directories,
    // gradle downloaded directories etc. This is defined here such that Studio can interpret these
    // in baselines as well.
    with(variables) {
      val userHome = System.getProperty("user.home")
      add("ANDROID_PREFS", AndroidLocationsSingleton.prefsLocation.toFile(), false)
      getSdkHome()?.let { add("ANDROID_HOME", it, false) }
      // See org.gradle.wrapper.GradleUserHomeLookup
      val gradleUserHome =
        System.getProperty("gradle.user.home")
          ?: System.getenv("GRADLE_USER_HOME")
          ?: "$userHome/.gradle"
      add("GRADLE_USER_HOME", File(gradleUserHome), false)
      add("HOME", File(userHome), false)
      sort()
    }
  }

  companion object {
    @JvmStatic private val PROP_BIN_DIR = "com.android.tools.lint.bindir"

    /**
     * Returns the File corresponding to the system property or the environment variable for
     * [PROP_BIN_DIR]. This property is typically set by the SDK/cmdline-tools/latest/bin/lint
     * wrapper. It denotes the path of the wrapper on disk.
     *
     * @return A new File corresponding to [LintClient.PROP_BIN_DIR] or null.
     */
    private val lintBinDir: File?
      get() {
        // First check the Java properties (e.g. set using "java -jar ... -Dname=value")
        // If not found, check environment variables.
        var path: String? = System.getProperty(PROP_BIN_DIR)
        if (path == null || path.isEmpty()) {
          path = System.getenv(PROP_BIN_DIR)
        }
        if (path != null && !path.isEmpty()) {
          val file = File(path)
          if (file.exists()) {
            return file
          }
        }
        return null
      }

    /**
     * Database moved from platform-tools to SDK in API level 26.
     *
     * This duplicates the constant in [LintClient] but that constant is not public (because it's in
     * the API package and I don't want this part of the API surface; it's an implementation
     * optimization.)
     */
    private const val SDK_DATABASE_MIN_VERSION = 26

    /**
     * Key stashed as user data on merged manifest documents such that we can quickly determine if a
     * node is originally from a merged manifest (this is used to automatically resolve reported
     * errors on the merged manifest back to the corresponding source locations, when possible.)
     */
    const val MERGED_MANIFEST = "lint-merged-manifest"

    @JvmField protected val NOT_FOUND: Pair<File, Node> = Pair.of<File, Node>(null, null)

    /** The client name returned by [clientName] when running in Android Studio/IntelliJ IDEA. */
    @Suppress("MemberVisibilityCanBePrivate") const val CLIENT_STUDIO = "studio"

    /** The client name returned by [clientName] when running in Gradle. */
    const val CLIENT_GRADLE = "gradle"

    /**
     * The client name returned by [clientName] when running in the CLI (command line interface)
     * version of lint, `lint`.
     */
    const val CLIENT_CLI = "cli"

    /** The client name returned by [clientName] when running in unit tests. */
    @Suppress("MemberVisibilityCanBePrivate") const val CLIENT_UNIT_TESTS = "test"

    /** The client name returned by [clientName] when running in some unknown client. */
    @Suppress("MemberVisibilityCanBePrivate") const val CLIENT_UNKNOWN = "unknown"

    /**
     * The name of the embedding client. It could be not just [CLIENT_STUDIO], [CLIENT_GRADLE],
     * [CLIENT_CLI] etc but other values too as lint is integrated in other embedding contexts.
     *
     * This is only intended to be set by the lint infrastructure.
     *
     * Note that if you are getting an [UninitializedPropertyAccessException] here, you're accessing
     * code which should only be run after the lint client name has been initialized. This should be
     * performed early in the initialization of each integration of lint in a tool such as Gradle,
     * Android Studio, etc.
     *
     * @return the name of the embedding client
     */
    @JvmStatic lateinit var clientName: String

    /**
     * Returns true if the embedding client currently running lint is Android Studio (or IntelliJ
     * IDEA)
     *
     * @return true if running in Android Studio / IntelliJ IDEA
     */
    @JvmStatic
    val isStudio: Boolean
      get() = CLIENT_STUDIO == clientName

    /**
     * Returns true if the embedding client currently running lint is Gradle
     *
     * @return true if running in Gradle
     */
    @JvmStatic
    val isGradle: Boolean
      get() = CLIENT_GRADLE == clientName

    /** Returns true if running from unit tests. */
    @JvmStatic
    val isUnitTest: Boolean
      get() = CLIENT_UNIT_TESTS == clientName

    /** Intended only for test infrastructure. */
    fun isClientNameInitialized(): Boolean {
      return this::clientName.isInitialized
    }

    /** Intended only for test infrastructure. */
    fun ensureClientNameInitialized() {
      if (!isClientNameInitialized()) {
        error("The LintClient.clientName must be initialized before running other lint code")
      }
    }

    /** Intended only for test infrastructure. */
    fun resetClientName() {
      // Use reflection to null out a lateinit variable.
      val field = LintClient::class.java.getDeclaredField("clientName")
      field.isAccessible = true
      field.set(null, null)
    }

    /**
     * Returns the desugaring operations that the Gradle plugin will use for a given version of
     * Gradle and a given configured language source level.
     */
    @JvmStatic
    fun getGradleDesugaring(
      version: AgpVersion,
      languageLevel: LanguageLevel?,
      coreLibraryDesugaringEnabled: Boolean,
    ): Set<Desugaring> {
      // Desugar runs if the Gradle plugin is 2.4.0 alpha 8 or higher...
      if (!version.isAtLeast(2, 4, 0, "alpha", 8, true)) {
        return Desugaring.NONE
      }

      // ... *and* the language level is at least 1.8
      // NO: Try with resources applies to JDK_1_7, though in Gradle we don't
      // kick in until Java 8!
      return when {
        languageLevel == null || languageLevel.isLessThan(JDK_1_8) -> Desugaring.NONE
        coreLibraryDesugaringEnabled -> Desugaring.FULL
        else -> Desugaring.DEFAULT
      }
    }

    /**
     * Reports an issue where we don't (necessarily) have a [Context] or [Project]. Detectors should
     * generally not use this facility; it's primarily used to report issues that happen outside of
     * a normal lint analysis, e.g. issues with the project setup itself, or loading custom check
     * jar files, etc.
     *
     * Even though this method takes a [LintClient] instance, it's here on the companion object
     * instead because we don't want this report method to be surfaced along with the normal report
     * methods people access via code completion.
     */
    fun report(
      client: LintClient,
      issue: Issue,
      message: String,
      file: File? = null,
      format: TextFormat = TextFormat.RAW,
      fix: LintFix? = null,
      configuration: Configuration? = null,
      severity: Severity? = null,
      context: Context? = null,
      project: Project? = null,
      mainProject: Project? = null,
      driver: LintDriver? = null,
      location: Location? = null,
    ) {

      val realLocation =
        when {
          location != null -> location
          file != null -> Location.create(file)
          context != null -> Location.create(context.file)
          project != null -> Location.create(project.dir)
          else -> error("Must supply location or file or project")
        }

      val realFile =
        when {
          file != null -> file
          else -> realLocation.file
        }

      val realProject =
        when {
          project != null -> project
          context != null -> context.project
          else -> {
            val dir =
              if (realFile.isDirectory) realFile else realFile.parentFile ?: File("").absoluteFile
            var curr = dir
            var projectDir: File? = null
            // Look through existing projects containing this path
            while (curr != null) {
              if (client.dirToProject.containsKey(curr)) {
                projectDir = curr
                break
              }
              curr = curr.parentFile
            }
            // If no existing project, at least pick the most reasonable guess
            // for a project location (primarily used to make relative paths
            // in error report)
            while (projectDir == null && curr != null) {
              if (client.isProjectDirectory(curr)) {
                projectDir = curr
                break
              }
              curr = curr.parentFile
            }
            client.getProject(projectDir ?: dir, projectDir ?: dir)
          }
        }

      val realSeverity =
        when {
          severity != null -> severity
          configuration != null -> configuration.getSeverity(issue)
          context != null -> context.configuration.getSeverity(issue)
          file != null ->
            client.configurations
              .getConfigurationForFolder(if (file.isFile) file.parentFile else file)
              ?.getSeverity(issue) ?: issue.defaultSeverity
          project != null && driver != null -> project.getConfiguration(driver).getSeverity(issue)
          else -> issue.defaultSeverity
        }

      // Create a context to report this issue against
      val realContext =
        when {
          context != null -> context
          else -> {
            val realDriver =
              if (driver != null) {
                driver
              } else {
                val request = LintRequest(client, emptyList())
                LintDriver(
                  object : IssueRegistry() {
                    override val issues: List<Issue> = emptyList()
                    override val vendor: Vendor = AOSP_VENDOR
                  },
                  client,
                  request,
                )
              }

            Context(
              realDriver,
              realProject,
              mainProject ?: realProject,
              realFile,
              if (realFile.isDirectory) "" else null,
            )
          }
        }

      val incident = Incident(issue, realLocation, message, fix)
      incident.severity = realSeverity
      val reportingClient = driver?.client ?: client
      reportingClient.report(realContext, incident, format)
    }

    /**
     * Convenience helper for Java calls into the above reporting method, since Java does not have
     * default parameters.
     */
    fun report(client: LintClient, issue: Issue, message: String, file: File, project: Project?) {
      report(
        client = client,
        issue = issue,
        message = message,
        file = file,
        project = project,
        // ensure we call the main reporting method, not a recursive call to self:
        driver = null,
      )
    }

    /**
     * Convenience helper for Java calls into the above reporting method, since Java does not have
     * default parameters.
     */
    fun report(
      client: LintClient,
      issue: Issue,
      message: String,
      driver: LintDriver,
      project: Project,
      location: Location?,
      fix: LintFix?,
    ) {
      report(
        client = client,
        issue = issue,
        message = message,
        driver = driver,
        project = project,
        location = location,
        fix = fix,
        // ensure we call the main reporting method, not a recursive call to self:
        file = null,
      )
    }
  }

  /** True if lint should print the full stacktrace of internal errors. */
  open val printInternalErrorStackTrace: Boolean
    get() =
      SdkConstants.VALUE_TRUE == System.getenv("LINT_PRINT_STACKTRACE") ||
        SdkConstants.VALUE_TRUE == System.getProperty("lint.print-stacktrace")
}
