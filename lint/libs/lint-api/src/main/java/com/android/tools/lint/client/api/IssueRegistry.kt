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

import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Platform
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.VersionChecks.Companion.SDK_INT_VERSION_DATA
import com.android.tools.lint.detector.api.editDistance
import com.android.tools.lint.detector.api.isApplicableTo
import com.google.common.collect.Maps
import com.google.common.collect.Sets
import java.util.Collections
import java.util.EnumSet

/** Registry which provides a list of checks to be performed on an Android project */
abstract class IssueRegistry protected constructor() {
  /**
   * The vendor providing this lint check. For all the built in checks, this will report the Android
   * Open Source Project, but for checks provided from libraries, this should point to the library
   * author/vendor. Lint will include this in lint reports such that bug reports (and compliments!)
   * can be directed to the right place.
   *
   * (This is a val with a default null implementation instead of an abstract method for backwards
   * compatibility; we don't want to cause class load errors when loading older registries in newer
   * lint.)
   */
  open val vendor: Vendor? = null

  init {
    LintClient.ensureClientNameInitialized()
  }

  /**
   * The Lint API version this issue registry's checks were compiled. You should return
   * [CURRENT_API].
   */
  open val api: Int = -1

  /**
   * The minimum API version this issue registry works with. Normally the same as [api], but if you
   * have tested it with older version and it works, you can return that level.
   *
   * See [com.android.tools.lint.detector.api.describeApi] for API level to version mappings.
   */
  open val minApi: Int
    get() {
      return api
    }

  /**
   * The maximum API level that this lint check works for. This allows an issue registry to provide
   * issues for older versions of lint than a certain version (and then presumably provide a second
   * issue registry with [minApi] set to the next API level with an alternative implementation that
   * works on newer versions).
   *
   * See [com.android.tools.lint.detector.api.describeApi] for API level to version mappings.
   */
  open val maxApi: Int
    get() {
      return Integer.MAX_VALUE
    }

  /**
   * The list of issues that can be found by all known detectors (including those that may be
   * disabled!)
   */
  abstract val issues: List<Issue>

  /**
   * The issue id's from any issues that have been deleted from this registry. This is here such
   * that when an issue no longer applies and is no longer registered, any existing mentions of the
   * issue id in baselines, lint.xml files etc are gracefully handled.
   */
  open val deletedIssues: List<String> = emptyList()

  /**
   * Whether this issue registry is up to date. Normally true but for example for custom rules
   * loaded from disk, may return false if the underlying file is updated or deleted.
   */
  open val isUpToDate: Boolean = true

  /**
   * Get an approximate issue count for a given scope. This is just an optimization, so the number
   * does not have to be accurate.
   *
   * @param scope the scope set
   * @return an approximate ceiling of the number of issues expected for a given scope set
   */
  protected open fun getIssueCapacity(scope: EnumSet<Scope>): Int = 20

  /**
   * Returns all available issues of a given scope (regardless of whether they are actually enabled
   * for a given configuration etc)
   *
   * @param scope the applicable scope set
   * @return a list of issues
   */
  protected open fun getIssuesForScope(scope: EnumSet<Scope>): List<Issue> {
    var list: List<Issue>? = scopeIssues[scope]
    if (list == null) {
      val issues = issues
      if (scope == Scope.ALL) {
        list = issues
      } else {
        list = ArrayList(getIssueCapacity(scope))
        for (issue in issues) {
          // Determine if the scope matches
          if (issue.implementation.isAdequate(scope)) {
            list.add(issue)
          }
        }
      }
      scopeIssues[scope] = list
    }

    return list
  }

  /**
   * Creates a list of detectors applicable to the given scope, and with the given configuration.
   *
   * @param configuration the configuration to look up which issues are enabled etc from
   * @param scope the scope for the analysis, to filter out detectors that require wider analysis
   *   than is currently being performed
   * @param platforms the platforms applying to this analysis
   * @param scopeToDetectors an optional map which (if not null) will be filled by this method to
   *   contain mappings from each scope to the applicable detectors for that scope
   * @param client the client to report errors to
   * @return a list of new detector instances
   */
  internal fun createDetectors(
    driver: LintDriver,
    project: Project,
    configuration: Configuration,
    scope: EnumSet<Scope>,
    platforms: EnumSet<Platform>,
    scopeToDetectors: MutableMap<Scope, MutableList<Detector>>?,
  ): List<Detector> {

    val issues = getIssuesForScope(scope)
    if (issues.isEmpty()) {
      return emptyList()
    }

    val detectorClasses = HashSet<Class<out Detector>>()
    val detectorToScope = HashMap<Class<out Detector>, EnumSet<Scope>>()

    for (issue in issues) {
      if (!platforms.isApplicableTo(issue)) {
        continue
      }

      val implementation = issue.implementation
      val detectorClass: Class<out Detector> = implementation.detectorClass
      val issueScope = implementation.scope
      if (!detectorClasses.contains(detectorClass)) {
        // Determine if the issue is enabled
        if (!configuration.isEnabled(issue)) {
          continue
        }

        //noinspection ExpensiveAssertion
        assert(implementation.isAdequate(scope)) // Ensured by getIssuesForScope above
        detectorClasses.add(detectorClass)
      }

      if (scopeToDetectors != null) {
        val s = detectorToScope[detectorClass]
        if (s == null) {
          detectorToScope[detectorClass] = issueScope
        } else if (!s.containsAll(issueScope)) {
          val union = EnumSet.copyOf(s)
          union.addAll(issueScope)
          detectorToScope[detectorClass] = union
        }
      }
    }

    val detectors = ArrayList<Detector>(detectorClasses.size)
    for (clz in detectorClasses) {
      try {
        val detector = clz.getDeclaredConstructor().newInstance()
        detectors.add(detector)

        if (scopeToDetectors != null) {
          val union = detectorToScope[clz] ?: continue
          for (s in union) {
            var list: MutableList<Detector>? = scopeToDetectors[s]
            if (list == null) {
              list = ArrayList()
              scopeToDetectors[s] = list
            }
            list.add(detector)
          }
        }
      } catch (t: Throwable) {
        LintDriver.handleDetectorError(
          driver,
          t,
          "Can't initialize detector ${clz.name}.\n",
          null,
          project,
        )
      }
    }

    return detectors
  }

  /**
   * Returns true if the given id represents a valid issue id
   *
   * @param id the id to be checked
   * @return true if the given id is valid
   */
  fun isIssueId(id: String): Boolean {
    return getIssue(id) != null
  }

  /**
   * Returns true if the given category is a valid category
   *
   * @param name the category name to be checked
   * @return true if the given string is a valid category
   */
  fun isCategoryName(name: String): Boolean {
    return Category.getCategory(name) != null
  }

  /**
   * Returns the available categories
   *
   * @return an iterator for all the categories, never null
   */
  fun getCategories(): List<Category> {
    var categories = this.categories
    if (categories == null) {
      categories = Collections.unmodifiableList(createCategoryList())
      this.categories = categories
      if (cacheable()) {
        cachedCategories = categories
      }
    }

    return categories!!
  }

  private fun createCategoryList(): List<Category> {
    val categorySet = Sets.newHashSetWithExpectedSize<Category>(20)
    for (issue in issues) {
      val element = issue.category
      categorySet.add(element)
      var parent = element.parent
      while (parent != null) {
        categorySet.add(parent)
        parent = parent.parent
      }
    }
    val sorted = ArrayList(categorySet)
    sorted.sort()
    return sorted
  }

  /**
   * Returns the issue for the given id, or null if it's not a valid id
   *
   * @param id the id to be checked
   * @return the corresponding issue, or null
   */
  fun getIssue(id: String): Issue? {
    var map = idToIssue
    if (map == null) {
      map = createIdToIssueMap()
      this.idToIssue = map
      if (cacheable()) {
        cachedIdToIssue = map
      }
    }

    return map[id]
  }

  /**
   * Given an issue id for an unknown issue, return any issues that appear to be spelled similarly.
   */
  fun getIdSpellingSuggestions(id: String): List<String> {
    val maxDistance = if (id.length >= 4) 2 else 1

    val matches = mutableSetOf<String>()

    // Look for case insensitive matches

    for (issue in issues) {
      val matchWith = issue.id
      val distance = editDistance(id, matchWith, maxDistance)
      if (distance <= maxDistance) {
        matches.add(matchWith)
      }
      if (matchWith.equals(id, ignoreCase = true)) {
        matches.add(matchWith)
      }
    }

    // If no matches, look for containment. This will catch cases where for example
    // an id is namespaced and you didn't supply that namespace or vice versa.
    if (matches.isEmpty()) {
      for (issue in issues) {
        val matchWith = issue.id
        if (matchWith.contains(id) || id.contains(matchWith)) {
          matches.add(matchWith)
        }
      }
    }

    if (matches.isEmpty()) {
      return emptyList()
    }

    return matches.asSequence().sorted().toList()
  }

  private fun createIdToIssueMap(): Map<String, Issue> {
    val issues = issues
    val map = Maps.newHashMapWithExpectedSize<String, Issue>(issues.size + 20)
    for (issue in issues) {
      map[issue.id] = issue
      issue.getAliases().forEach { alias ->
        map[alias] = issue
        renamedIds[alias] = issue.id
      }
    }

    for (id in deletedIssues) {
      deletedIds.add(id)
    }

    map[PARSER_ERROR.id] = PARSER_ERROR
    map[LINT_ERROR.id] = LINT_ERROR
    map[LINT_WARNING.id] = LINT_WARNING
    map[BASELINE_USED.id] = BASELINE_USED
    map[BASELINE_FIXED.id] = BASELINE_FIXED
    map[UNKNOWN_ISSUE_ID.id] = UNKNOWN_ISSUE_ID
    map[CANNOT_ENABLE_HIDDEN.id] = CANNOT_ENABLE_HIDDEN
    map[OBSOLETE_LINT_CHECK.id] = OBSOLETE_LINT_CHECK
    map[SDK_INT_VERSION_DATA.id] = SDK_INT_VERSION_DATA
    return map
  }

  @Volatile private var categories: List<Category>?

  @Volatile private var idToIssue: Map<String, Issue>?
  private var scopeIssues: MutableMap<EnumSet<Scope>, List<Issue>>

  /**
   * Whether this issue registry has state that is cacheable. Issue registries which include project
   * specific state (such as custom checks for example) are not.
   */
  protected open fun cacheable(): Boolean = false

  init {
    @Suppress("LeakingThis")
    if (cacheable()) {
      // In the IDE, cache across incremental runs; here, lint is never run in parallel
      scopeIssues = cachedScopeIssues
      idToIssue = cachedIdToIssue
      categories = cachedCategories
    } else {
      // Outside of the IDE, typically in Gradle, we don't want this caching since
      // lint can run in parallel and this caching can be incorrect;
      // see for example issue 77891711
      scopeIssues = Maps.newHashMap()
      idToIssue = null
      categories = null
    }
  }

  companion object {
    @Volatile private var cachedCategories: List<Category>? = null

    @Volatile private var cachedIdToIssue: Map<String, Issue>? = null
    private var cachedScopeIssues: MutableMap<EnumSet<Scope>, List<Issue>> = Maps.newHashMap()

    private val EMPTY_IMPLEMENTATION =
      Implementation(Detector::class.java, EnumSet.noneOf(Scope::class.java))

    private var deletedIds = mutableSetOf<String>()
    private var renamedIds = mutableMapOf<String, String>()

    /**
     * Returns true if the given [id] used to be a valid id in lint but has since been deleted or
     * renamed.
     */
    fun isDeletedIssueId(id: String): Boolean = deletedIds.contains(id)

    /**
     * If the given issue has been renamed (which involves deleting the previous id) return its new
     * name.
     */
    fun getNewId(id: String): String? = renamedIds[id]

    /**
     * Issue reported by lint (not a specific detector) when it cannot even parse an XML file prior
     * to analysis.
     */
    @JvmField // temporarily
    val PARSER_ERROR =
      Issue.create(
        id = "ParserError",
        briefDescription = "Parser Errors",
        explanation =
          """
                Lint will ignore any files that contain fatal parsing errors. These may \
                contain other errors, or contain code which affects issues in other files.""",
        category = Category.LINT,
        priority = 10,
        severity = Severity.ERROR,
        implementation = EMPTY_IMPLEMENTATION,
      )

    /**
     * Issue reported by lint for various other issues which prevents lint from running normally
     * when it's not necessarily an error in the user's code base.
     */
    @JvmField // temporarily
    val LINT_ERROR =
      Issue.create(
        id = "LintError",
        briefDescription = "Lint Failure",
        explanation =
          """
                This issue type represents a problem running lint itself. Examples include \
                failure to find bytecode for source files (which means certain detectors \
                could not be run), parsing errors in lint configuration files, etc.

                These errors are not errors in your own code, but they are shown to make it \
                clear that some checks were not completed.
                """,
        category = Category.LINT,
        priority = 10,
        severity = Severity.ERROR,
        implementation = EMPTY_IMPLEMENTATION,
      )

    /**
     * Issue reported by lint for various other issues which may prevent lint from running normally
     * when it's not necessarily an error in the user's code base. Similar to [LINT_WARNING] but
     * intended for lower severity problems which may or may not be significant.
     */
    @JvmField // temporarily
    val LINT_WARNING =
      Issue.create(
        id = "LintWarning",
        briefDescription = "Lint Failure",
        explanation =
          """
                This issue type represents a problem running lint itself. Examples include \
                unsupported tags in configuration files, etc.

                These errors are not errors in your own code, but they are shown to make it \
                clear that some checks were not completed.
                """,
        category = Category.LINT,
        priority = 5,
        severity = Severity.WARNING,
        implementation = EMPTY_IMPLEMENTATION,
      )

    /** Lint is configured with references to an issue id that it does not recognize. */
    @JvmField
    val UNKNOWN_ISSUE_ID =
      Issue.create(
        id = "UnknownIssueId",
        briefDescription = "Unknown Lint Issue Id",
        explanation =
          """
                Lint will report this issue if it is configured with an issue id it does \
                not recognize in for example Gradle files or `lint.xml` configuration files.
                """,
        category = Category.LINT,
        priority = 1,
        severity = Severity.WARNING,
        implementation = EMPTY_IMPLEMENTATION,
      )

    /**
     * When lint runs in partial analysis mode, any issues that are turned off in a library cannot
     * be re-enabled in the main project.
     */
    @JvmField
    val CANNOT_ENABLE_HIDDEN =
      Issue.create(
        id = "CannotEnableHidden",
        briefDescription = "Issue Already Disabled",
        explanation =
          """
                Any issues that are specifically disabled in a library cannot be re-enabled \
                in a dependent project. To fix this you need to also enable the issue in \
                the library project.

                (This also applies for issues that are off by default; they cannot just be \
                enabled in a dependent project; they must also be enabled in all the \
                libraries the project depends on.)
                """,
        category = Category.LINT,
        priority = 1,
        severity = Severity.WARNING,
        implementation = EMPTY_IMPLEMENTATION,
      )

    /**
     * Issue reported by lint for various other issues which prevents lint from running normally
     * when it's not necessarily an error in the user's code base.
     */
    @JvmField
    val BASELINE_USED =
      Issue.create(
        id = "LintBaseline",
        briefDescription = "Baseline Applied",
        explanation =
          """
          Lint can be configured with a "baseline"; a set of current issues found \
          in a codebase, which future runs of lint will silently ignore. Only new \
          issues not found in the baseline are reported.

          Note that while opening files in the IDE, baseline issues are not \
          filtered out; the purpose of baselines is to allow you to get started \
          using lint and break the build on all newly introduced errors, without \
          having to go back and fix the entire codebase up front. However, when \
          you open up existing files you still want to be aware of and fix issues \
          as you come across them.

          This issue type is used to emit an informational-only warning if any \
          issues were filtered out due to baseline matching. That way, you don't \
          have a false sense of security if you forgot that you've checked in a \
          baseline file.
          """,
        category = Category.LINT,
        priority = 10,
        severity = Severity.INFORMATIONAL,
        implementation = EMPTY_IMPLEMENTATION,
      )

    /**
     * Reported when there are entries in the baseline that no longer seem to be reported, and is
     * likely fixed (or the issue has been disabled).
     */
    @JvmField
    val BASELINE_FIXED =
      Issue.create(
        id = "LintBaselineFixed",
        briefDescription = "Baselined Issues Fixed",
        explanation =
          """
          If a lint baseline describes a problem which is no longer reported, \
          then the problem has either been fixed, or perhaps the issue type has \
          been disabled. In any case, the entry can be removed from the baseline \
          (such that if the issue is reintroduced at some point, lint will \
          complain rather than just silently starting to match the old baseline \
          entry again.)
          """,
        category = Category.LINT,
        priority = 10,
        severity = Severity.INFORMATIONAL,
        implementation = EMPTY_IMPLEMENTATION,
      )

    /**
     * Issue reported by lint when it encounters old lint checks that haven't been updated to the
     * latest APIs.
     */
    @JvmField // temporarily
    val OBSOLETE_LINT_CHECK =
      Issue.create(
        id = "ObsoleteLintCustomCheck",
        briefDescription = "Obsolete custom lint check",
        explanation =
          """
                Lint can be extended with "custom checks": additional checks implemented \
                by developers and libraries to for example enforce specific API usages \
                required by a library or a company coding style guideline.

                The Lint APIs are not yet stable, so these checks may either cause a \
                performance degradation, or stop working, or provide wrong results.

                This warning flags custom lint checks that are found to be using obsolete \
                APIs and will need to be updated to run in the current lint environment.

                It may also flag issues found to be using a **newer** version of the API, \
                meaning that you need to use a newer version of lint (or Android Studio \
                or Gradle plugin etc) to work with these checks.""",
        category = Category.LINT,
        priority = 10,
        severity = Severity.WARNING,
        implementation = EMPTY_IMPLEMENTATION,
      )

    /** Vendor used for the built-in lint checks. */
    val AOSP_VENDOR =
      Vendor(
        vendorName = "Android Open Source Project",
        feedbackUrl = "https://issuetracker.google.com/issues/new?component=192708",
        contact = "https://groups.google.com/g/lint-dev",
      )

    /** Reset the registry such that it recomputes its available issues. */
    fun reset() {
      synchronized(IssueRegistry::class.java) {
        cachedIdToIssue = null
        cachedCategories = null
        cachedScopeIssues = Maps.newHashMap()
      }
    }
  }
}
