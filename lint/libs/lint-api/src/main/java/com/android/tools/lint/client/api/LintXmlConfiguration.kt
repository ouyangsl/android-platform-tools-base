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

import com.android.SdkConstants.CURRENT_PLATFORM
import com.android.SdkConstants.PLATFORM_WINDOWS
import com.android.SdkConstants.SUPPRESS_ALL
import com.android.SdkConstants.VALUE_FALSE
import com.android.SdkConstants.VALUE_TRUE
import com.android.ide.common.util.PathString
import com.android.tools.lint.client.api.ConfigurationHierarchy.Companion.getLintXmlFile
import com.android.tools.lint.client.api.LintClient.Companion.report
import com.android.tools.lint.detector.api.BooleanOption
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.FileOption
import com.android.tools.lint.detector.api.FloatOption
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.IntOption
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Option
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.StringOption
import com.android.utils.CharSequenceReader
import com.android.utils.SdkUtils
import com.android.utils.iterator
import com.google.common.base.Splitter
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.io.IOException
import java.io.Writer
import java.nio.file.Path
import java.nio.file.Paths
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.Locale
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import kotlin.math.max
import org.kxml2.io.KXmlParser
import org.w3c.dom.Element
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * Default implementation of a [Configuration] which reads and writes configuration data into
 * `lint.xml` from a given file.
 *
 * XML Syntax
 * ===========
 *
 * The root tag is always `<lint>`, and it can contain one or more `<issue>` elements. Each <issue>
 * can specify the following attributes: `id`: The issue id the following configuration applies to.
 * Note that this can be a comma separated list of multiple id's, in which case the configuration
 * applies to all of them. It can also be the special value "all", which will match all issue id's.
 * And when configuring severity, the id is also allowed to be a category, such as "Security".
 *
 * `in`: Specifies that this configuration only applies when lint runs in the given hosts. There are
 * predefined names for various integrations of lint; "gradle" refers to lint running in the Gradle
 * plugin; "studio" refers to lint running in the IDE, "cli" refers to lint running from the command
 * line tools "lint" binary, etc. Like with id's, this can be a comma separated list, which makes
 * the rule match if the lint host is any of the listed hosts. Finally, note that you can also add a
 * "!" in front of each host to negate the check. For example, to enable a check anywhere except
 * when running in Studio, use {@code in="!studio"}.
 *
 * In addition, the <issue> element can specify one or more children:
 *
 * `<ignore path="...">`: Specifies a path to ignore. Can contain the globbing character "*" to
 * match any substring in the path. `<ignore regexp="...">`: Specifies either a regular expression
 * to ignore. The regular expression is matched against both the location of the error and the error
 * message itself. `<option name="..." value="...">`: Specifies an option value. This can be used to
 * configure some lint checks with options.
 *
 * Finally, on the root <lint> element you can specify a number of attributes, such as `lintJars` (a
 * list of jar files containing custom lint checks, separated by a semicolon as a path separator),
 * and flags like warningsAsErrors, checkTestSources, etc (matching most of the flags offered via
 * the lintOptions block in Gradle files.)
 *
 * Nesting & Precedence
 * ======================
 *
 * You can specify configurations for "all", but these will be matched after an exact match has been
 * done. E.g. if we have both `<issue id="all" severity="ignore">` and `<issue id="MyId"
 * severity="error">`, the severity for `MyId` will be "error"" since that's a more exact match.
 *
 * The lint.xml files can be nested in a directory structure, and when lint reports an error, it
 * looks up the closest lint.xml file, and if no configuration is found there, continues searching
 * upwards in the directory tree. This means that the configuration closest to the report location
 * takes precedence. Note however, that this has higher priority than the above all versus id match,
 * so if there is an `all` match in a `lint.xml` file and an exact match in a more distant parent
 * `lint.xml` file, the closest `lint.xml` all match will be used.
 *
 * When there are configurations which specify a host, lint will search in this order:
 * 1. An exact host match. E.g. if you're running in Studio and there is an `<issue>` configuration
 *    which specifies `in="studio"`, then that configuration will be used.
 * 2. A match which does not specify a host. Usually `<issue>` configurations do not specify a host,
 *    and these will be consulted next.
 * 3. A match which specifies other hosts. For example, if you're running in Studio and a
 *    configuration specifies "!gradle", this will match after the other attempts.
 *
 * @sample com.android.tools.lint.client.api.LintXmlConfigurationTest.sampleFile
 */
open class LintXmlConfiguration
protected constructor(
  configurations: ConfigurationHierarchy,
  val configFile: File,
  dir: File? = configFile.parentFile,
  override var fileLevel: Boolean = true,
) : Configuration(configurations) {
  init {
    this.dir = dir
  }

  protected constructor(
    configurations: ConfigurationHierarchy,
    project: Project,
  ) : this(configurations, project.dir.getLintXmlFile(), project.dir, true)

  private var bulkEditing = false

  /**
   * Returns whether lint should check all warnings, including those off by default, or null if not
   * configured in this configuration.
   */
  private var checkAllWarnings: Boolean? = null

  /**
   * Returns whether lint will only check for errors (ignoring warnings), or null if not configured
   * in this configuration.
   */
  private var ignoreWarnings: Boolean? = null

  /**
   * Returns whether lint should treat all warnings as errors, or null if not configured in this
   * configuration.
   */
  private var warningsAsErrors: Boolean? = null
  private var fatalOnly: Boolean? = null
  private var checkTestSources: Boolean? = null
  private var ignoreTestSources: Boolean? = null
  private var checkGeneratedSources: Boolean? = null
  private var checkDependencies: Boolean? = null
  private var explainIssues: Boolean? = null
  private var applySuggestions: Boolean? = null
  private var removeFixedBaselineIssues: Boolean? = null
  private var abortOnError: Boolean? = null
  private var lintJars: List<File>? = null

  /**
   * Specific issue configuration specified in a lint.xml file. This corresponds to configuration
   * for one specific issue. We don't store the issue id in the data class itself; the issue id's
   * are used as map keys instead.
   */
  data class IssueData(
    /** Severity of the issue. */
    var severity: Severity? = null,
    /** Paths (optionally with glob patterns) to treat as ignored/suppressed. */
    var paths: MutableList<String>? = null,
    /**
     * Regular expressions to match against the message and location of errors to suppress or ignore
     * issues.
     */
    var patterns: MutableList<Pattern>? = null,
    /** Optional parameters to the issue checker, defined by the detector. */
    var options: MutableMap<String, String>? = null,
  ) {
    /**
     * Returns true if there is no significant configuration for this issue (so can be skipped in
     * serialization)
     */
    fun isEmpty(): Boolean {
      return severity == null &&
        (paths == null || paths!!.isEmpty()) &&
        (patterns == null || patterns!!.isEmpty()) &&
        (options == null || options!!.isEmpty())
    }

    // For debugging only
    override fun toString(): String {
      return "IssueData(severity=$severity, paths=$paths, patterns=$patterns, options=$options)"
    }

    operator fun plusAssign(other: IssueData) {
      other.severity?.let { this.severity = it }
      other.paths?.let {
        val paths = this.paths
        if (paths == null) this.paths = other.paths else paths.addAll(it)
      }
      other.patterns?.let {
        val patterns = this.patterns
        if (patterns == null) this.patterns = other.patterns else patterns.addAll(it)
      }
      other.options?.let {
        val options = this.options
        if (options == null) this.options = other.options else options.putAll(it)
      }
    }
  }

  /**
   * Map from issue id (or [VALUE_ALL] to specific issue configuration, such as a custom severity or
   * specific paths to ignore.
   */
  private var issueMap: MutableMap<String, IssueData>? = null

  /**
   * Applicable maps to look at: first client-specific maps for the current client, then the (no
   * client specified) map, then any other applicable client maps (e.g. !other)
   */
  private var issueMaps: List<Map<String, IssueData>> = emptyList()

  /**
   * Like [issueMap], but limited to specific integrations of lint, based on the
   * [LintClient.clientName], as well as negated client names.
   */
  private var clientIssueMaps: MutableMap<String, MutableMap<String, IssueData>>? = null

  /**
   * If non null, the whole configuration applies to the given [LintClient]. Could be a comma
   * separated list.
   */
  private var fileClients: String? = null

  /** Invokes the checker for all the applicable issue configurations. */
  private fun checkIgnored(id: String, checker: (IssueData) -> Boolean): Boolean {
    val issueMaps = getIssueMaps()
    for (issueMap in issueMaps) {
      issueMap[id]?.let { if (checker(it)) return true }
    }

    // If you reset the severity of an issue in this file, that basically
    // means you're "overriding" the all-configuration with an implicit un-ignore.
    // Without this there's no way to ignore all paths except for some specific
    // issue you care about
    for (issueMap in issueMaps) {
      val severity = issueMap[id]?.severity ?: continue
      if (severity != Severity.IGNORE) {
        return false
      }
    }

    for (issueMap in issueMaps) {
      issueMap[VALUE_ALL]?.let { if (checker(it)) return true }
    }
    return false
  }

  override fun isIgnored(context: Context, incident: Incident): Boolean {
    val location = incident.location
    if (location == Location.NONE) {
      return parent?.isIgnored(context, incident) ?: false
    }

    val id = incident.issue.id
    val message = incident.message
    if (isPathIgnored(id, location, context) || isPatternIgnored(id, message, location, context)) {
      return true
    }

    return parent?.isIgnored(context, incident) ?: false
  }

  private fun getLocalOption(issue: Issue, name: String, default: String?): String? {
    val id = issue.id
    val issueMaps = getIssueMaps()
    for (issueMap in issueMaps) {
      issueMap[id]?.options?.get(name)?.let {
        return it
      }
    }
    for (issueMap in issueMaps) {
      issueMap[VALUE_ALL]?.options?.get(name)?.let {
        return it
      }
    }

    return default
  }

  override fun getOption(issue: Issue, name: String, default: String?): String? {
    getLocalOption(issue, name, default)?.let {
      return it
    }
    return parent?.getOption(issue, name, default) ?: default
  }

  private fun reportOptionValidationError(option: Option, message: String) {
    val alreadyWarned = warnedOptions ?: HashSet<Option>().also { warnedOptions = it }
    if (!alreadyWarned.add(option)) {
      return
    }

    val location = getLocation { element ->
      element.tagName == TAG_OPTION &&
        element.getAttribute(ATTR_NAME) == option.name &&
        (element.parentNode as? Element)?.getAttribute(ATTR_ID) == option.issue.id
    }

    report(
      client = client,
      issue = IssueRegistry.LINT_ERROR,
      message = message,
      location = location,
    )
  }

  override fun getOption(option: Option): Any? {
    getLocalOption(option.issue, option.name, null)?.let { valueString ->
      return when (option) {
        is StringOption -> valueString
        is BooleanOption -> {
          when {
            valueString.equals(VALUE_TRUE, true) -> true
            valueString.equals(VALUE_FALSE, true) -> false
            else -> {
              // Validate here on read instead of when the XML file is initially parsed because
              // we don't know the associated Issue to look up options for during lint.xml parsing
              // (for third party loaded lint checks)
              reportOptionValidationError(
                option,
                "Option value must be `true` or `false` (was $valueString)",
              )
              option.defaultValue
            }
          }
        }
        is IntOption -> {
          try {
            val value = valueString.toInt()
            if (value < option.min || value >= option.max) {
              reportOptionValidationError(option, "${option.name}: ${option.rangeAsString()}")
            }
            value
          } catch (e: NumberFormatException) {
            reportOptionValidationError(
              option,
              "${option.name} must be an integer (was $valueString)",
            )
            option.defaultValue
          }
        }
        is FloatOption -> {
          try {
            val value = valueString.toFloat()
            if (value < option.min || value >= option.max) {
              reportOptionValidationError(option, "${option.name}: ${option.rangeAsString()}")
            }
            value
          } catch (e: NumberFormatException) {
            reportOptionValidationError(option, "${option.name} must be a float (was $valueString)")
            option.defaultValue
          }
        }
        is FileOption -> {
          val file = File(valueString.replace('/', File.separatorChar))
          if (file.isAbsolute) {
            file
          } else {
            val parent = configFile.parentFile ?: return file
            File(parent, file.path)
          }
        }
      }
    }
    return parent?.getOption(option)
  }

  override fun getOptionAsFile(issue: Issue, name: String, default: File?): File? {
    val value =
      getLocalOption(issue, name, null)
        ?: return parent?.getOptionAsFile(issue, name, null) ?: default

    val file = File(value.replace('/', File.separatorChar))
    if (file.isAbsolute) {
      return file
    } else {
      val parent = configFile.parentFile ?: return file
      return File(parent, file.path)
    }
  }

  /** Sets the given string option to the given value. Intended for [LintFix] usage. */
  fun setOption(issue: Issue, name: String, value: String?) {
    if (value != null) {
      addOption(listOf(issue.id), name, value)
    } else {
      getPrimaryIssueMap()[issue.id]?.options?.remove(name)
    }
  }

  /** Sets the given boolean option to the given value. Intended for [LintFix] usage. */
  fun setBooleanOption(issue: Issue, name: String, value: Boolean?) {
    setOption(issue, name, value?.toString())
  }

  /** Sets the given integer option to the given value. Intended for [LintFix] usage. */
  fun setIntOption(issue: Issue, name: String, value: Int?) {
    setOption(issue, name, value?.toString())
  }

  /** Sets the given File option to the given value. Intended for [LintFix] usage. */
  fun setFileOption(issue: Issue, name: String, value: File?) {
    val relative =
      if (value != null && value.isAbsolute) {
        client.getRelativePath(configFile.parentFile, value)
      } else {
        null
      }
    if (relative != null) {
      setOption(issue, name, relative.replace('\\', '/'))
    } else {
      setOption(issue, name, value?.toString()?.replace('\\', '/'))
    }
  }

  private fun isPathIgnored(id: String, location: Location, context: Context): Boolean {
    return checkIgnored(id) { data -> isPathIgnored(data, location, context) }
  }

  private fun isPathIgnored(issueData: IssueData, location: Location, context: Context): Boolean {
    val paths = issueData.paths
    if (paths == null || paths.isEmpty()) {
      return false
    }
    val file = location.file
    val parentFile = configFile.parentFile
    val relativePath =
      if (parentFile != null) client.getRelativePath(parentFile, file) ?: return false
      else file.path
    for (suppressedPath in paths) {
      if (suppressedPath == relativePath) {
        return true
      }
      // Also allow a prefix
      if (relativePath.startsWith(suppressedPath)) {
        return true
      }
    }

    // A project can have multiple resources folders. The code before this
    // only checks for paths relative to project root (which doesn't work for paths such as
    // res/layout/foo.xml defined in lint.xml - when using gradle where the
    // resource directory points to src/main/res)
    // Here we check if any of the suppressed paths are relative to the resource folders
    // of a project.
    var suppressedPathSet: MutableSet<Path>? = null
    for (p in paths) {
      if (p.startsWith(RES_PATH_START)) {
        val path = Paths.get(p.substring(RES_PATH_START_LEN))
        val set = suppressedPathSet ?: HashSet<Path>().also { suppressedPathSet = it }
        set.add(path)
      }
    }
    val suppressedPaths = suppressedPathSet
    if (suppressedPaths != null && suppressedPaths.isNotEmpty()) {
      val toCheck = file.toPath()
      // Is it relative to any of the resource folders?
      for (resDir in context.project.resourceFolders) {
        val path = resDir.toPath()
        val relative = path.relativize(toCheck)
        if (suppressedPaths.contains(relative)) {
          return true
        }
        // Allow suppress the relativePath if it is a prefix
        if (suppressedPaths.stream().anyMatch { relative.startsWith(it) }) {
          return true
        }
      }
    }

    return false
  }

  private fun getOrCreateIssueMap(client: String?): MutableMap<String, IssueData> {
    ensureInitialized()
    return if (client != null) {
      val clientMap =
        clientIssueMaps
          ?: LinkedHashMap<String, MutableMap<String, IssueData>>().also { clientIssueMaps = it }
      val map = clientMap[client] ?: HashMap<String, IssueData>().also { clientMap[client] = it }
      map
    } else {
      issueMap!!
    }
  }

  private fun getIssueMaps(): List<Map<String, IssueData>> {
    ensureInitialized()
    return issueMaps
  }

  private fun getPrimaryIssueMap(): MutableMap<String, IssueData> {
    ensureInitialized()
    return issueMap!!
  }

  private fun isPatternIgnored(
    id: String,
    message: String,
    location: Location,
    context: Context,
  ): Boolean {
    return checkIgnored(id) { data -> isPatternIgnored(data, message, location, context) }
  }

  private fun isPatternIgnored(
    issueData: IssueData,
    message: String,
    location: Location,
    context: Context,
  ): Boolean {
    val regexps = issueData.patterns
    if (regexps == null || regexps.isEmpty()) {
      return false
    }

    // Check message
    for (regexp in regexps) {
      val matcher = regexp.matcher(message)
      if (matcher.find()) {
        return true
      }
    }

    // Check location
    val file = location.file
    val relativeTo = configFile.parentFile ?: context.project.dir
    var relativePath = Project.getRelativePath(relativeTo, file)
    var checkUnixPath = false
    for (regexp in regexps) {
      val matcher = regexp.matcher(relativePath)
      if (matcher.find()) {
        return true
      } else {
        val pattern = regexp.pattern()
        if (pattern.indexOf('/') != -1) {
          checkUnixPath = true
          // See issue 177044619: Users may be trying to match substrings
          // like ".*/src/main/java" but if the relative path from above
          // is "src/main/java" that won't match; so in that case we'll
          // match "/src/main/java" instead.
          if (
            pattern.startsWith(".*/") || pattern.startsWith("^.*/") || pattern.startsWith("^.*?/")
          ) {
            if (regexp.matcher("/$relativePath").find()) {
              return true
            }
          }
        }
      }
    }
    if (checkUnixPath && CURRENT_PLATFORM == PLATFORM_WINDOWS) {
      relativePath = relativePath.replace('\\', '/')
      for (regexp in regexps) {
        val matcher = regexp.matcher(relativePath)
        if (matcher.find()) {
          return true
        }
      }
    }

    return false
  }

  override fun addConfiguredIssues(
    targetMap: MutableMap<String, Severity>,
    registry: IssueRegistry,
    specificOnly: Boolean,
  ) {
    parent?.addConfiguredIssues(targetMap, registry, specificOnly)

    val issueMaps = getIssueMaps()
    // First handle VALUE_ALL, then handle specific id's afterwards as overrides
    for (issueMap in issueMaps) {
      val issueData = issueMap[VALUE_ALL] ?: continue
      val severity = issueData.severity ?: continue // not setting severity by (say) options
      for (issue in registry.issues) {
        targetMap[issue.id] = severity
      }
    }
    for (issueMap in issueMaps) {
      for ((id, issueData) in issueMap) {
        val severity = issueData.severity ?: continue
        targetMap[id] = severity
      }
    }

    overrides?.addConfiguredIssues(targetMap, registry, specificOnly)
  }

  override fun getLocalIssueConfigLocation(
    issue: String,
    specificOnly: Boolean,
    severityOnly: Boolean,
    source: Configuration,
  ): Location? {
    val issueMaps = getIssueMaps()

    for (issueMap in issueMaps) {
      val data: IssueData? = issueMap[issue]
      if (data != null) {
        return getConfigLocation(issue, severityOnly)
      }
    }

    for (issueMap in issueMaps) {
      issueMap[VALUE_ALL]?.let {
        return if (!specificOnly) {
          return getConfigLocation(VALUE_ALL, severityOnly)
        } else {
          // There's a match in this file via the all attribute, so don't
          // keep searching upwards; that would be misleading since that
          // outer configuration does not apply
          null
        }
      }
    }

    return parent?.getLocalIssueConfigLocation(issue, specificOnly, severityOnly, source)
  }

  override fun getDefinedSeverity(
    issue: Issue,
    source: Configuration,
    visibleDefault: Severity,
  ): Severity? {
    if (issue.suppressNames != null && !issue.suppressNames.contains(issue.id)) {
      // Not allowed to suppress this issue via lint.xml.
      // Consider reporting this as well (not easy here since we don't have
      // a context.)
      // Ideally we'd report this to the user too, but we can't really do
      // that here because we can't access the flag which lets you opt out
      // of the restrictions, where we'd unconditionally continue to
      // report this warning:
      //    if (this.severity.get(issue.getId()) != null) {
      //        LintClient.Companion.report(client, IssueRegistry.LINT_ERROR,
      //                "Issue `" + issue.getId() + "` is not allowed to be suppressed",
      //                configFile, project);
      //    }
      return getDefaultSeverity(issue, visibleDefault)
    }

    val issueMaps = getIssueMaps()
    for (issueMap in issueMaps) {
      val severity =
        issueMap[issue.id]?.severity
          ?: issueMap[issue.category.name]?.severity // id's can also refer to categories
          ?: issueMap[issue.category.fullName]?.severity
          ?: run { // recursively
            var currentCategory = issue.category.parent
            var s: Severity? = null
            while (currentCategory != null) {
              s = issueMap[currentCategory.name]?.severity
              if (s != null) {
                break
              }
              currentCategory = currentCategory.parent
            }
            s
          }
      if (severity != null) {
        return severity
      }
    }

    // if not set, also match by "all"
    for (issueMap in issueMaps) {
      val severity = issueMap[VALUE_ALL]?.severity
      if (severity != null) {
        return severity
      }
    }

    // or inherited?
    return parent?.getDefinedSeverity(issue, source, visibleDefault)
      ?: super.getDefinedSeverity(issue, source, visibleDefault)
  }

  private fun ensureInitialized() {
    if (issueMap == null) {
      readConfig()
    }
  }

  private fun getConfigLocation(id: String, severityOnly: Boolean): Location {
    return getLocation { element ->
      id == element.getAttribute(ATTR_ID) && (!severityOnly || element.hasAttribute(ATTR_SEVERITY))
    }
  }

  private fun getLocation(filter: (Element) -> Boolean): Location {
    val parser = client.xmlParser
    parser.parseXml(configFile)?.documentElement?.let { document ->
      for (element in document) {
        if (filter(element)) {
          return parser.getLocation(configFile, element)
        }
        for (child in element) {
          // Also support nested children of issues: <ignore>, <option>, etc
          if (filter(child)) {
            return parser.getLocation(configFile, child)
          }
        }
      }
    }

    return Location.create(configFile)
  }

  private fun getLocation(parser: XmlPullParser): Location {
    val contents = client.readFile(configFile).toString()
    return Location.create(configFile, contents, max(0, parser.lineNumber - 1))
  }

  private fun getLocation(exception: XmlPullParserException): Location {
    val contents = client.readFile(configFile).toString()
    return Location.create(configFile, contents, max(0, exception.lineNumber - 1))
  }

  private fun reportError(
    message: String,
    parser: XmlPullParser? = null,
    exception: XmlPullParserException? = null,
    location: Location? =
      when {
        parser != null -> getLocation(parser)
        exception != null -> getLocation(exception)
        else -> Location.create(configFile)
      },
    severity: Severity = Severity.WARNING,
  ) {
    report(
      client = client,
      issue = if (severity.isError) IssueRegistry.LINT_ERROR else IssueRegistry.LINT_WARNING,
      message = message,
      location = location,
      driver = null,
    )
  }

  private fun readConfig() {
    if (!client.fileExists(configFile, requireFile = true)) {
      val issueMap = mutableMapOf<String, IssueData>()
      this.issueMap = issueMap
      this.issueMaps = listOf(issueMap)
      return
    }

    val resourcePath = PathString(configFile)
    val parser = client.createXmlPullParser(resourcePath) ?: return
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    readConfig(parser)
  }

  private fun readConfig(parser: XmlPullParser) {
    val issueMap: MutableMap<String, IssueData> =
      HashMap<String, IssueData>().also { this.issueMap = it }
    try {
      fun String.asBoolean(): Boolean? {
        return when {
          this == VALUE_TRUE -> true
          this == VALUE_FALSE -> false
          else -> null
        }
      }

      val splitter = Splitter.on(',').trimResults().omitEmptyStrings()
      var idString = ""
      var idList: Iterable<String> = emptyList()
      var issueClients: String? = null

      while (parser.next() != XmlPullParser.END_DOCUMENT) {
        val eventType = parser.eventType
        if (eventType == XmlPullParser.START_TAG) {
          when (parser.name) {
            TAG_LINT -> {
              fun applies(): Boolean = isApplicableClient(fileClients)
              val n = parser.attributeCount
              for (i in 0 until n) {
                val name = parser.getAttributeName(i)
                val value = parser.getAttributeValue(i)
                when (name) {
                  ATTR_IN -> {
                    fileClients = value
                    if (i > 0) {
                      reportError(
                        "$ATTR_IN for the whole " +
                          "file, if specified, must always be the " +
                          "first attribute in <$TAG_LINT>",
                        parser,
                      )
                    } else if (value == VALUE_ALL) {
                      reportError("$VALUE_ALL not supported for $ATTR_IN", parser)
                    }
                  }
                  ATTR_BASELINE ->
                    if (applies()) this.baselineFile = File(value.replace('/', File.separatorChar))
                  "checkAllWarnings" -> if (applies()) checkAllWarnings = value.asBoolean()
                  "ignoreWarnings" -> if (applies()) ignoreWarnings = value.asBoolean()
                  "warningsAsErrors" -> if (applies()) warningsAsErrors = value.asBoolean()
                  "fatalOnly" -> if (applies()) fatalOnly = value.asBoolean()
                  "checkTestSources" -> if (applies()) checkTestSources = value.asBoolean()
                  "ignoreTestSources" -> if (applies()) ignoreTestSources = value.asBoolean()
                  "checkGeneratedSources" ->
                    if (applies()) checkGeneratedSources = value.asBoolean()
                  "checkDependencies" -> if (applies()) checkDependencies = value.asBoolean()
                  "explainIssues" -> if (applies()) explainIssues = value.asBoolean()
                  "applySuggestions" -> if (applies()) applySuggestions = value.asBoolean()
                  "removeFixedBaselineIssues" ->
                    if (applies()) removeFixedBaselineIssues = value.asBoolean()
                  "abortOnError" -> if (applies()) abortOnError = value.asBoolean()
                  "lintJar",
                  "lintJars" -> {
                    if (fileLevel) {
                      reportError(
                        "`lintJar` can only be specified for lint.xml files at the module level or higher",
                        parser,
                      )
                    } else if (applies()) {
                      // Using ; instead of File.pathSeparator here
                      // because we want to handle both Windows and
                      // Linux interchangeably; we don't want a file
                      // created on Windows (using ; as a path separator
                      // and frequently contains : in paths, such as C:\),
                      // to be split on the : on Linux/Mac.
                      lintJars =
                        value
                          .split(';')
                          .map { path ->
                            val file = File(path)
                            val absolute =
                              if (file.isAbsolute) file else File(configFile.parentFile, path)
                            if (!absolute.exists()) {
                              reportError("lintJar $absolute does not exist")
                            }
                            absolute
                          }
                          .toList()
                    }
                  }
                  else -> reportError("Unexpected attribute `$name`", parser)
                }
              }
            }
            TAG_ISSUE -> {
              val n = parser.attributeCount
              var severityString = ""
              for (i in 0 until n) {
                val name = parser.getAttributeName(i)
                val value = parser.getAttributeValue(i)
                when (name) {
                  ATTR_IN -> {
                    // For now, don't allow specifying clients on both
                    // the root element and on individual issues; we currently
                    // discard baseline/flags from the root element if they
                    // don't apply to the current host so if we allowed this
                    // we'd need to store all the flags on a per-client basis
                    // in order to not drop anything when writing configs
                    // back out
                    if (fileClients != null) {
                      reportError(
                        "If you specify `$ATTR_IN` on the root <$TAG_LINT> element you cannot specify it anywhere else",
                        parser,
                      )
                    } else if (value == VALUE_ALL) {
                      reportError("$VALUE_ALL not supported for $ATTR_IN", parser)
                    }
                    issueClients = value
                  }
                  ATTR_ID -> {
                    idString = value
                    idList = splitter.split(idString)
                  }
                  ATTR_SEVERITY -> severityString = value
                  else ->
                    reportError(
                      "Unexpected attribute `$name`, expected `$ATTR_ID`, `$ATTR_IN` or `$ATTR_SEVERITY`",
                      parser,
                    )
                }
              }
              if (idString.isEmpty()) {
                reportError("Missing required issue `id` attribute", parser)
              } else if (severityString.isNotEmpty()) {
                val severity =
                  Severity.fromName(severityString)
                    ?: if (severityString == "hide" || severityString == "hidden") {
                      Severity.IGNORE
                    } else if (severityString == "info" || severityString == "information") {
                      Severity.INFORMATIONAL
                    } else null
                if (severity != null) {
                  // TODO: If !projectLevel and we're turning on a check
                  // here, report a warning that this can only be done at
                  // the project level. The challenge in implementing this
                  // now is that we don't want to flag changing the severity
                  // of already enabled checks (e.g. enabled in inherited
                  // configurations, or checks already warning/error/fatal
                  // and we're setting it to a different one of those.)
                  addSeverity(idList, severity, fileClients, issueClients)
                } else {
                  reportError("Unknown severity `$severityString`", parser)
                }
              }
            }
            TAG_IGNORE -> {
              val n = parser.attributeCount
              if (parser.depth < 3) {
                reportError("`<$TAG_IGNORE>` tag should be nested within `<$TAG_ISSUE>`", parser)
              } else {
                var path = ""
                var regexp = ""
                for (i in 0 until n) {
                  val name = parser.getAttributeName(i)
                  val value = parser.getAttributeValue(i)
                  when (name) {
                    ATTR_PATH -> path = value
                    ATTR_REGEXP -> regexp = value
                    else ->
                      reportError(
                        "Unexpected attribute `$name`, expected `$ATTR_PATH` or `$ATTR_REGEXP`",
                        parser,
                      )
                  }
                }
                if (path.isEmpty()) {
                  if (regexp.isEmpty()) {
                    reportError("Missing required attribute `$ATTR_PATH` or `$ATTR_REGEXP`", parser)
                  } else {
                    addRegexp(parser, idString, idList, n, regexp, fileClients, issueClients)
                  }
                } else {
                  // Normalize path format to File.separator. Also
                  // handle the file format containing / or \.
                  path =
                    if (File.separatorChar == '/') {
                      path.replace('\\', '/')
                    } else {
                      path.replace('/', File.separatorChar)
                    }
                  if (path.indexOf('*') != -1) {
                    // Convert glob path to pattern
                    addRegexp(
                      parser,
                      idString,
                      idList,
                      n,
                      SdkUtils.globToRegexp(path),
                      fileClients,
                      issueClients,
                    )
                  } else {
                    addPaths(idList, n, path, fileClients, issueClients)
                  }
                }
              }
            }
            TAG_OPTION -> {
              val n = parser.attributeCount
              if (parser.depth < 3) {
                reportError("`<$TAG_OPTION>` tag should be nested within `<$TAG_ISSUE>`", parser)
              } else {
                var optionKey = ""
                var optionValue = ""
                for (i in 0 until n) {
                  val name = parser.getAttributeName(i)
                  val value = parser.getAttributeValue(i)
                  when (name) {
                    ATTR_NAME -> optionKey = value
                    ATTR_VALUE -> optionValue = value
                    else ->
                      reportError(
                        "Unexpected attribute `$name`, expected `$ATTR_NAME` or `$ATTR_VALUE`",
                        parser,
                      )
                  }
                }
                if (optionKey.isEmpty() || optionValue.isEmpty()) {
                  reportError(
                    "Must specify both $ATTR_NAME and $ATTR_VALUE in <$TAG_OPTION>",
                    parser,
                  )
                } else {
                  // If it's a path?
                  addOption(idList, optionKey, optionValue, fileClients, issueClients)
                }
              }
            }
            else ->
              reportError(
                "Unsupported tag <`${parser.name}`>, expected one of `$TAG_LINT`, `$TAG_ISSUE`, `$TAG_IGNORE` or `$TAG_OPTION`",
                parser,
              )
          }
        } else if (eventType == XmlPullParser.END_TAG) {
          if (parser.name == TAG_ISSUE) {
            // Ensure that we don't keep using the client list for the next
            // issue too
            issueClients = null
            idString = ""
            idList = emptyList()
          }
        }
      }
    } catch (e: IOException) {
      client.log(e, null)
    } catch (e: XmlPullParserException) {
      val detail = e.message ?: ""
      // Error message contain per-process into (like a toString of the input stream)
      // so strip that out to ensure stable output
      val index = detail.indexOf(" (position:")
      val message =
        "Failed parsing ${configFile.name}: ${
            if (index != -1) detail.substring(0, index) else detail}"
      reportError(message, exception = e)
    }

    val clientMaps = clientIssueMaps
    if (clientMaps != null) {
      val list = mutableListOf<Map<String, IssueData>>()
      // First look for an *exact* client match, that way if you specify
      // say both "!gradle" and "studio", and you're running with client="studio",
      // it would match "studio", not "!gradle" if it happened to be first even
      // though it's a compatible match
      for ((key, map) in clientMaps.entries) {
        if (isApplicableClient(key, checkEquals = true, checkOther = false)) {
          list.add(map)
          // note that there could be more than one, so don't break here
          // (e.g. we'll have different maps for in="studio,gradle" and in="studio"
          // and both should match and be added here)
        }
      }
      list.add(issueMap)
      for ((key, map) in clientMaps.entries) {
        if (isApplicableClient(key, checkEquals = false, checkOther = true)) {
          list.add(map)
        }
      }
      issueMaps = list
    } else {
      issueMaps = listOf(issueMap)
    }
  }

  /**
   * Checks whether the given [client] name is applicable for the current [LintClient]. If
   * [checkEquals] is true, will check for exact matches. If [checkOther] is true, will also check
   * to see if the [LintClient] matches client strings like "!name" where the name is **not** the
   * name of the host, e.g. "studio" would match "!gradle". A null client name means "not specific
   * to a client" and will always match. Note also that the [client] string can be a comma separated
   * list, and this method will return true if **any** of the items match.
   */
  private fun isApplicableClient(
    client: String?,
    checkEquals: Boolean = true,
    checkOther: Boolean = true,
  ): Boolean {
    client ?: return true

    if (client.contains(',')) {
      for (c in client.splitToSequence(",")) {
        if (isApplicableClient(c.trim(), checkEquals = checkEquals, checkOther = checkOther)) {
          return true
        }
      }
    }

    val host = LintClient.clientName
    if (checkEquals && client.equals(host, ignoreCase = true)) {
      return true
    }

    if (
      checkOther && client.startsWith("!") && !client.regionMatches(1, host, 0, host.length, true)
    ) {
      return true
    }

    return false
  }

  /**
   * When parsing an XML file an element can reference multiple id's; this method sets the given
   * severity to all the referenced id's.
   */
  private fun addSeverity(
    idList: Iterable<String>,
    severity: Severity,
    fileClients: String?,
    issueClients: String?,
  ) {
    val issueMap = getOrCreateIssueMap(issueClients ?: fileClients)
    for (id in idList) {
      val data = issueMap[id] ?: IssueData().also { issueMap[id] = it }
      data.severity = severity
    }
  }

  /**
   * When parsing an XML file an element can reference multiple id's; this method adds the given
   * suppress path to all the referenced id's.
   */
  private fun addPaths(
    ids: Iterable<String>,
    n: Int,
    path: String,
    fileClients: String?,
    issueClients: String?,
  ) {
    val issueMap = getOrCreateIssueMap(issueClients ?: fileClients)
    for (id in ids) {
      val data = issueMap[id] ?: IssueData().also { issueMap[id] = it }
      val paths = data.paths ?: ArrayList<String>(n / 2 + 1).also { data.paths = it }
      paths.add(path)
    }
  }

  /**
   * When parsing an XML file an element can reference multiple id's; this method adds the given
   * regular expression pattern to all the referenced id's.
   */
  private fun addRegexp(
    parser: XmlPullParser,
    idList: String,
    ids: Iterable<String>,
    n: Int,
    regexp: String,
    fileClients: String?,
    issueClients: String?,
  ) {
    try {
      val issueMap = getOrCreateIssueMap(issueClients ?: fileClients)
      val pattern = Pattern.compile(regexp)
      for (id in ids) {
        val data = issueMap[id] ?: IssueData().also { issueMap[id] = it }
        val paths = data.patterns ?: ArrayList<Pattern>(n / 2 + 1).also { data.patterns = it }
        paths.add(pattern)
      }
    } catch (e: PatternSyntaxException) {
      reportError("Invalid pattern `$regexp` under `$idList`: ${e.description}", parser)
    }
  }

  private fun addOption(
    ids: Iterable<String>,
    key: String,
    value: String,
    fileClients: String? = null,
    issueClients: String? = null,
  ) {
    val issueMap = getOrCreateIssueMap(issueClients ?: fileClients)
    for (id in ids) {
      val data = issueMap[id] ?: IssueData().also { issueMap[id] = it }
      val options =
        data.options
          // LinkedHashMap: preserve lint.xml order
          ?: LinkedHashMap<String, String>().also { data.options = it }
      options[key] = value
    }
  }

  fun getCheckAllWarnings(): Boolean? {
    ensureInitialized()
    return checkAllWarnings
  }

  fun getIgnoreWarnings(): Boolean? {
    ensureInitialized()
    return ignoreWarnings
  }

  fun getWarningsAsErrors(): Boolean? {
    ensureInitialized()
    return warningsAsErrors
  }

  fun getFatalOnly(): Boolean? {
    ensureInitialized()
    return fatalOnly
  }

  fun getCheckTestSources(): Boolean? {
    ensureInitialized()
    return checkTestSources
  }

  fun getIgnoreTestSources(): Boolean? {
    ensureInitialized()
    return ignoreTestSources
  }

  fun getCheckGeneratedSources(): Boolean? {
    ensureInitialized()
    return checkGeneratedSources
  }

  fun getCheckDependencies(): Boolean? {
    ensureInitialized()
    return checkDependencies
  }

  fun getExplainIssues(): Boolean? {
    ensureInitialized()
    return explainIssues
  }

  fun getApplySuggestions(): Boolean? {
    ensureInitialized()
    return applySuggestions
  }

  fun getRemoveFixedBaselineIssues(): Boolean? {
    ensureInitialized()
    return removeFixedBaselineIssues
  }

  fun getAbortOnError(): Boolean? {
    ensureInitialized()
    return abortOnError
  }

  override fun getLintJars(): List<File> {
    ensureInitialized()
    val inheritedJars = parent?.getLintJars() ?: return lintJars ?: emptyList()
    val jars = lintJars ?: return inheritedJars
    return jars + inheritedJars
  }

  private fun writeConfig() {
    try {
      ensureInitialized()

      // Write the contents to a new file first such that we don't clobber the
      // existing file if some I/O error occurs.
      val file = File(configFile.parentFile, configFile.name + ".new")
      val writer: Writer = file.bufferedWriter()
      writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<")
      writer.write(TAG_LINT)
      fileClients?.let { writeAttribute(writer, ATTR_IN, it) }
      baselineFile?.let { baselineFile ->
        writer.write(" $ATTR_BASELINE=\"")
        val path = Project.getRelativePath(configFile.parentFile, baselineFile)
        writeAttribute(writer, ATTR_BASELINE, path.replace('\\', '/'))
      }
      checkAllWarnings?.let { writeAttribute(writer, "checkAllWarnings", it.toString()) }
      ignoreWarnings?.let { writeAttribute(writer, "ignoreWarnings", it.toString()) }
      warningsAsErrors?.let { writeAttribute(writer, "warningsAsErrors", it.toString()) }
      fatalOnly?.let { writeAttribute(writer, "fatalOnly", it.toString()) }
      checkTestSources?.let { writeAttribute(writer, "checkTestSources", it.toString()) }
      ignoreTestSources?.let { writeAttribute(writer, "ignoreTestSources", it.toString()) }
      checkGeneratedSources?.let { writeAttribute(writer, "checkGeneratedSources", it.toString()) }
      checkDependencies?.let { writeAttribute(writer, "checkDependencies", it.toString()) }
      explainIssues?.let { writeAttribute(writer, "explainIssues", it.toString()) }
      applySuggestions?.let { writeAttribute(writer, "applySuggestions", it.toString()) }
      removeFixedBaselineIssues?.let {
        writeAttribute(writer, "removeFixedBaselineIssues", it.toString())
      }
      abortOnError?.let { writeAttribute(writer, "abortOnError", it.toString()) }
      lintJars?.let {
        writeAttribute(
          writer,
          "lintJars",
          it.joinToString(";") { f ->
            val lintPath = f.path
            val xmlPath = configFile.parentFile?.path ?: ""
            if (
              lintPath.startsWith(xmlPath) &&
                lintPath != xmlPath &&
                lintPath[xmlPath.length] == File.separatorChar
            ) {
              lintPath.substring(xmlPath.length + 1).replace(File.separatorChar, '/')
            } else {
              lintPath.replace(File.separatorChar, '/')
            }
          },
        )
      }
      writer.write(">\n")
      for (issueMap in getIssueMaps()) {
        if (issueMap.isEmpty()) {
          continue
        }
        val client = clientIssueMaps?.entries?.firstOrNull { (_, v) -> v == issueMap }?.key

        // Process the maps in a stable sorted order such that if the
        // files are checked into version control with the project,
        // there are no random diffs just because hashing algorithms
        // differ:
        val ids = issueMap.keys.asSequence().sorted()
        for (id in ids) {
          val data = issueMap[id] ?: continue
          if (data.isEmpty()) {
            continue
          }
          writer.write("    <")
          writer.write(TAG_ISSUE)
          if (client != null) {
            writeAttribute(writer, ATTR_IN, client)
          }
          writeAttribute(writer, ATTR_ID, id)
          val severity = data.severity
          if (severity != null) {
            writeAttribute(writer, ATTR_SEVERITY, severity.name.lowercase(Locale.ROOT))
          }
          val regexps = data.patterns
          val paths = data.paths
          val options = data.options
          if (
            paths != null && paths.isNotEmpty() ||
              regexps != null && regexps.isNotEmpty() ||
              options != null && options.isNotEmpty()
          ) {
            writer.write('>'.toInt())
            writer.write('\n'.toInt())
            if (options != null) {
              // The options are kept in file order by LinkedHashMap
              for (option in options) {
                writer.write("        <")
                writer.write(TAG_OPTION)
                writeAttribute(writer, ATTR_NAME, option.key)
                writeAttribute(writer, ATTR_VALUE, option.value)
                writer.write(" />\n")
              }
            }

            // The paths are already kept in sorted order when they are modified
            // by ignore(...)
            if (paths != null) {
              for (path in paths) {
                writer.write("        <")
                writer.write(TAG_IGNORE)
                writeAttribute(writer, ATTR_PATH, path.replace('\\', '/'))
                writer.write(" />\n")
              }
            }
            if (regexps != null) {
              for (regexp in regexps) {
                writer.write("        <")
                writer.write(TAG_IGNORE)
                writeAttribute(writer, ATTR_REGEXP, regexp.pattern())
                writer.write(" />\n")
              }
            }
            writer.write("    </")
            writer.write(TAG_ISSUE)
            writer.write('>'.toInt())
            writer.write('\n'.toInt())
          } else {
            writer.write(" />\n")
          }
        }
      }
      writer.write("</lint>")
      writer.close()

      // Move file into place: move current version to lint.xml~ (removing the old ~ file
      // if it exists), then move the new version to lint.xml.
      val oldFile = File(configFile.parentFile, configFile.name + '~')
      if (oldFile.exists()) {
        oldFile.delete()
      }
      if (configFile.exists()) {
        configFile.renameTo(oldFile)
      }
      val ok = file.renameTo(configFile)
      if (ok && oldFile.exists()) {
        oldFile.delete()
      }
    } catch (e: Exception) {
      client.log(e, null)
    }
  }

  private fun writeAttribute(writer: Writer, name: String, value: String) {
    writer.write(' '.toInt())
    writer.write(name)
    writer.write('='.toInt())
    writer.write('"'.toInt())
    writer.write(value)
    writer.write('"'.toInt())
  }

  override fun ignore(context: Context, issue: Issue, location: Location?, message: String) {
    // This configuration only supports suppressing warnings on a per-file basis
    if (location != null) {
      ignore(issue, location.file)
    }
  }

  override fun ignore(issue: Issue, file: File) = ignore(issue.id, file)

  override fun ignore(issueId: String, file: File) {
    val issueMap = getPrimaryIssueMap()
    val path = Project.getRelativePath(configFile.parentFile, file)
    val data = issueMap[issueId] ?: IssueData().also { issueMap[issueId] = it }
    val paths = data.paths ?: ArrayList<String>().also { data.paths = it }
    paths.add(path)

    // Keep paths sorted alphabetically; makes XML output stable
    paths.sort()
    if (!bulkEditing) {
      writeConfig()
    }
  }

  override fun setSeverity(issue: Issue, severity: Severity?) {
    val issueMap = getPrimaryIssueMap()
    val id = issue.id
    val data = issueMap[id] ?: IssueData().also { issueMap[id] = it }
    data.severity = severity
    if (!bulkEditing) {
      writeConfig()
    }
  }

  override fun startBulkEditing() {
    bulkEditing = true
  }

  override fun finishBulkEditing() {
    bulkEditing = false
    writeConfig()
  }

  // Backing property for [baselineFile]
  private var _baselineFile: File? = null

  override var baselineFile: File?
    get() = _baselineFile
    set(value) {
      _baselineFile =
        if (value != null && !value.isAbsolute) {
          val dir = configFile.parentFile
          if (dir != null) {
            File(dir, value.path)
          } else {
            value
          }
        } else {
          value
        }
    }

  /**
   * Already validated this issue? We can encounter the same configuration multiple times when
   * searching up the parent tree. (We can't skip calling the parent because the parent references
   * can change over time.)
   */
  private var validated = false

  override fun validateIssueIds(
    client: LintClient,
    driver: LintDriver,
    project: Project?,
    registry: IssueRegistry,
    allowed: Set<String>,
  ) {
    parent?.validateIssueIds(client, driver, project, registry, allowed)
    if (validated) {
      return
    }
    validated = true
    for (map in getIssueMaps()) {
      if (map.isNotEmpty()) {
        map as? MutableMap<String, IssueData> ?: continue
        validateIssueIds(client, driver, project, registry, map, allowed)
      }
    }
  }

  private fun validateIssueIds(
    client: LintClient,
    driver: LintDriver,
    project: Project?,
    registry: IssueRegistry,
    map: MutableMap<String, IssueData>,
    allowed: Set<String>,
  ) {
    for (id in map.keys.toList()) {
      if (id == SUPPRESS_ALL) {
        // builtin special "id" which means all id's
        continue
      }
      val issue = registry.getIssue(id)
      if (issue == null) {
        // You can also configure issues by categories; don't flag these
        if (registry.isCategoryName(id)) {
          continue
        }

        // If this is an inherited configuration, it might be for an issue that is
        // not applied to all projects. Err on the side of caution here and don't
        // flag these.
        val dir = project?.dir
        val configDir = configFile.parentFile
        if (dir != null && configDir != null && FileUtil.isAncestor(configDir, dir, true)) {
          continue
        }

        if (allowed.contains(id)) {
          continue
        }

        reportNonExistingIssueId(client, driver, registry, project, id)
      } else if (issue.id != id) {
        // We're using an alias here in the configuration; map it over
        val data = map[id] ?: continue
        val merge = map[issue.id]
        if (merge == null) {
          map[issue.id] = data
          map.remove(id)
        } else {
          merge += data
        }
      }
    }
    parent?.validateIssueIds(client, driver, project, registry, allowed)
  }

  // For debugging only
  override fun toString(): String {
    val path = configurations.client.pathVariables.toPathString(configFile)
    return this.javaClass.simpleName + "(" + path + ")"
  }

  companion object {
    /** Default name of the configuration file. */
    const val CONFIG_FILE_NAME = "lint.xml"

    /** The root tag in a configuration file. */
    const val TAG_LINT = "lint"

    private const val TAG_ISSUE = "issue"
    private const val TAG_IGNORE = "ignore"
    private const val TAG_OPTION = "option"
    private const val ATTR_ID = "id"
    private const val ATTR_IN = "in"
    private const val ATTR_SEVERITY = "severity"
    private const val ATTR_PATH = "path"
    private const val ATTR_REGEXP = "regexp"
    private const val ATTR_NAME = "name"
    private const val ATTR_VALUE = "value"
    const val VALUE_ALL = "all"
    private const val ATTR_BASELINE = "baseline"
    private val RES_PATH_START = "res" + File.separatorChar
    private val RES_PATH_START_LEN = RES_PATH_START.length

    /**
     * Options we've already generated validation warnings for. We have to keep track of these such
     * that we don't spam the output with repeated warnings because these validations are done when
     * options are *read* rather when the XML file is parsed; this is necessary because at lint.xml
     * parsing time we still don't have access to the issue registries, and it's quite likely that
     * some of these options correspond to detectors loaded dynamically (e.g. 3rd party lint checks)
     * so we don't yet know the constraints.
     */
    var warnedOptions: MutableSet<Option>? = null

    /**
     * Creates a new [LintXmlConfiguration] for the given lint config file, not affiliated with a
     * project. This is used for global configurations.
     *
     * @param configurations the configuration manager
     * @param lintFile the lint file containing the configuration
     * @return a new configuration
     */
    @JvmStatic
    fun create(configurations: ConfigurationHierarchy, lintFile: File): LintXmlConfiguration {
      return LintXmlConfiguration(configurations, lintFile)
    }

    /**
     * Creates a new [LintXmlConfiguration] for the given lint config file contents (and associated
     * file handle), not affiliated with a project. This is used for global configurations.
     *
     * @param configurations the configuration manager
     * @param lintFile the lint file containing the configuration (which will not be read, but any
     *   parsing errors etc will be attributed to this path)
     * @param contents the actual contents of the lint file handle
     * @return a new configuration
     */
    @JvmStatic
    fun create(
      configurations: ConfigurationHierarchy,
      lintFile: File,
      contents: CharSequence,
    ): LintXmlConfiguration {
      val config = LintXmlConfiguration(configurations, lintFile)
      val parser = KXmlParser()
      parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
      parser.setInput(CharSequenceReader(contents))
      config.readConfig(parser)
      return config
    }
  }
}
