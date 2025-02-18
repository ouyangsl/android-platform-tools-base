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

package com.android.tools.lint.detector.api

import com.android.tools.lint.client.api.CompositeIssueRegistry
import com.android.tools.lint.client.api.Configuration
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.TextFormat.RAW
import java.util.EnumSet

/**
 * An issue is a potential bug in an Android application. An issue is discovered by a [Detector],
 * and has an associated [Severity].
 *
 * Issues and detectors are separate classes because a detector can discover multiple different
 * issues as it's analyzing code, and we want to be able to different severities for different
 * issues, the ability to suppress one but not other issues from the same detector, and so on.
 */
class Issue
private constructor(
  /**
   * Returns the unique id of this issue. These should not change over time since they are used to
   * persist the names of issues suppressed by the user etc. It is typically a single camel-cased
   * word.
   *
   * @return the associated fixed id, never null and always unique
   */
  val id: String,
  private val briefDescription: String,
  private val explanation: String,

  /**
   * The primary category of the issue
   *
   * @return the primary category of the issue, never null
   */
  val category: Category,

  /**
   * Returns a priority, in the range 1-10, with 10 being the most severe and 1 the least
   *
   * @return a priority from 1 to 10
   */
  val priority: Int,

  /**
   * Returns the default severity of the issues found by this detector (some tools may allow the
   * user to specify custom severities for detectors).
   *
   * Note that even though the normal way for an issue to be disabled is for the [Configuration] to
   * return [Severity.IGNORE], there is a [isEnabledByDefault] method which can be used to turn off
   * issues by default. This is done rather than just having the severity as the only attribute on
   * the issue such that an issue can be configured with an appropriate severity (such as
   * [Severity.ERROR]) even when issues are disabled by default for example because they are
   * experimental or not yet stable.
   *
   * @return the severity of the issues found by this detector
   */
  val defaultSeverity: Severity,

  /**
   * Set of platforms where this issue applies. For example, if the analysis is being run on an
   * Android project, lint will include all checks that either don't specify any platforms, or
   * includes the android scope.
   */
  platforms: EnumSet<Platform>,

  /**
   * If non-null, this issue can **only** be suppressed with one of the given annotations: not
   * with @Suppress, not with @SuppressLint, not with lint.xml, not with lintOptions{} and not with
   * baselines.
   *
   * These suppress names can take various forms:
   * * Valid qualified names in Kotlin and Java (identifier characters and dots). Represents
   *   suppress annotation. Examples include android.annotation.SuppressLint, java.lang.Suppress and
   *   kotlin.Suppress (which all happen to be looked at by default by lint.)
   * * Simple name (no dots): XML suppress attribute in the tools namespace
   * * HTTP URL followed by colon and then name: namespace and attribute for XML suppress attribute.
   *   For example, http://schemas.android.com/tools:ignore represents "ignore" in the tools
   *   namespace (which happens to be the default Lint already looks for.)
   */
  val suppressNames: Collection<String>?,

  /**
   * The implementation for the given issue. This is typically done by IDEs that can offer a
   * replacement for a given issue which performs better or in some other way works better within
   * the IDE.
   */
  var implementation: Implementation,
) : Comparable<Issue> {
  // TODO revise below once Kotlin supports union
  private var moreInfoUrls: Any? /* null | String | MutableList<String> */ = null
  private var enabledByDefault = true

  /**
   * Set of platforms where this issue applies. For example, if the analysis is being run on an
   * Android project, lint will include all checks that either don't specify any platform scopes, or
   * includes the android scope.
   */
  var platforms: EnumSet<Platform> = platforms
    private set

  /**
   * Whether we're analyzing Android sources. Note that within an Android project there may be
   * non-Android libraries, but this flag indicates whether there's *any* Android in this project.
   *
   * This is a convenience property around [platforms].
   */
  fun setAndroidSpecific(value: Boolean): Issue {
    if (value) {
      platforms =
        if (platforms.isEmpty()) {
          Platform.ANDROID_SET
        } else {
          val new = EnumSet.copyOf(platforms)
          new.add(Platform.ANDROID)
          new
        }
    } else {
      platforms =
        if (platforms == Platform.ANDROID_SET) {
          Platform.UNSPECIFIED
        } else {
          val new = EnumSet.copyOf(platforms)
          new.remove(Platform.ANDROID)
          new
        }
    }

    return this
  }

  /**
   * Whether we're analyzing Android sources. Note that within an Android project there may be
   * non-Android libraries, but this flag indicates whether there's *any* Android in this project.
   *
   * This is a convenience property around [platforms].
   */
  fun isAndroidSpecific(): Boolean {
    return platforms.contains(Platform.ANDROID)
  }

  private var aliases: List<String> = emptyList()

  /**
   * Sets previous names for this issue; this is useful when you for various reasons have to rename
   * or combine multiple issues into one; by declaring it here, lint can more gracefully handle
   * existing incidents listed in baselines etc.
   */
  fun setAliases(aliases: List<String>?): Issue {
    assert(this.aliases.isEmpty()) // calling more than once is probably not intentional
    this.aliases = aliases ?: emptyList()
    return this
  }

  /** Returns any names for this issue; see [setAliases]. */
  fun getAliases(): List<String> = aliases

  private var options: List<Option> = emptyList()

  /** Sets options associated with this issue. */
  fun setOptions(options: List<Option>): Issue {
    // (We're using setter instead of property to allow chaining as part of issue registration.)
    assert(this.options.isEmpty()) // calling more than once is probably not intentional
    this.options = options
    for (option in options) {
      option.issue = this
    }
    return this
  }

  /** Returns any options configurable for this issue; see [setOptions]. */
  fun getOptions(): List<Option> = options

  /** A link (a URL string) to more information, or null. */
  val moreInfo: List<String>
    @Suppress("UNCHECKED_CAST")
    get() =
      when (val urls = moreInfoUrls) {
        null -> emptyList()
        is String -> listOf(urls)
        is List<*> -> urls as List<String>
        else -> throw IllegalStateException("Unexpected `moreInfoUrls` of $urls")
      }

  init {
    assert(briefDescription.isNotEmpty())
    assert(explanation.isNotEmpty())
  }

  /**
   * Briefly (in a couple of words) describes these errors
   *
   * @return a brief summary of the issue, never null, never empty
   */
  fun getBriefDescription(format: TextFormat): String {
    val trimmed = briefDescription.trimIndent()
    // For convenience allow line wrapping in explanation raw strings
    // by "escaping" the newline, e.g. ending the line with \
    val message = trimmed.replace("\\\n", "")
    return RAW.convertTo(message, format)
  }

  /**
   * Describes the error found by this rule, e.g. "Buttons must define contentDescriptions".
   * Preferably the explanation should also contain a description of how the problem should be
   * solved. Additional info can be provided via [moreInfo].
   *
   * @param format the format to write the format as
   * @return an explanation of the issue, never null, never empty
   */
  fun getExplanation(format: TextFormat): String {
    val trimmed = explanation.trimIndent()
    // For convenience allow line wrapping in explanation raw strings
    // by "escaping" the newline, e.g. ending the line with \
    val message = trimmed.replace("\\\n", "")
    return RAW.convertTo(message, format)
  }

  /**
   * Adds a more info URL string
   *
   * @param moreInfoUrl url string
   * @return this, for constructor chaining
   */
  fun addMoreInfo(moreInfoUrl: String): Issue {
    // Nearly all issues supply at most a single URL, so don't bother with
    // lists wrappers for most of these issues
    @Suppress("UNCHECKED_CAST")
    when (val existing = moreInfoUrls) {
      null -> moreInfoUrls = moreInfoUrl
      is String -> moreInfoUrls = mutableListOf(existing, moreInfoUrl)
      is MutableList<*> -> (existing as MutableList<String>).add(moreInfoUrl)
      else -> throw IllegalStateException("Unexpected `moreInfoUrls`: $existing")
    }
    return this
  }

  /**
   * Returns whether this issue should be enabled by default, unless the user has explicitly
   * disabled it.
   *
   * @return true if this issue should be enabled by default
   */
  fun isEnabledByDefault(): Boolean {
    return enabledByDefault
  }

  /**
   * Sorts the detectors alphabetically by id. This is intended to make it convenient to store
   * settings for detectors in a fixed order. It is not intended as the order to be shown to the
   * user; for that, a tool embedding lint might consider the priorities, categories, severities etc
   * of the various detectors.
   *
   * @param other the [Issue] to compare this issue to
   */
  override fun compareTo(other: Issue): Int {
    return id.compareTo(other.id)
  }

  /**
   * Sets whether this issue is enabled by default.
   *
   * @param enabledByDefault whether the issue should be enabled by default
   * @return this, for constructor chaining
   */
  fun setEnabledByDefault(enabledByDefault: Boolean): Issue {
    this.enabledByDefault = enabledByDefault
    return this
  }

  override fun toString(): String {
    return id
  }

  override fun equals(other: Any?): Boolean = this === other || other is Issue && id == other.id

  override fun hashCode(): Int {
    return id.hashCode()
  }

  /**
   * The registry providing this issue, if known. Note that this is typically the original registry
   * providing the issue; there may be other issue registries providing this issue as well (for
   * example, [CompositeIssueRegistry]), so it is not the case that each issue returned by
   * registry.getIssues() will point back to the same registry. (Note that lint will set this on its
   * own after loading an issue registry.)
   *
   * All the built-in issues typically point to [IssueRegistry.AOSP_VENDOR].
   */
  var registry: IssueRegistry? = null

  /**
   * The vendor providing this fix. You do not need to set this for every issue; as long as the
   * [IssueRegistry] associated with this issue provides a vendor, lint will find and use it.
   */
  var vendor: Vendor? = null

  companion object {
    /**
     * Creates a new issue. The description strings can use some simple markup; see the
     * [TextFormat.RAW] documentation for details.
     *
     * @param id the fixed id of the issue
     * @param briefDescription short summary (typically 5-6 words or less), typically describing the
     *   **problem** rather than the **fix** (e.g. "Missing minSdkVersion")
     * @param explanation a full explanation of the issue, with suggestions for how to fix it
     * @param category the associated category, if any
     * @param priority the priority, a number from 1 to 10 with 10 being most important/severe
     * @param severity the default severity of the issue
     * @param implementation the default implementation for this issue
     * @return a new [Issue]
     */
    @JvmStatic
    fun create(
      id: String,
      briefDescription: String,
      explanation: String,
      category: Category,
      priority: Int,
      severity: Severity,
      implementation: Implementation,
    ): Issue =
      Issue(
        id,
        briefDescription,
        explanation,
        category,
        priority,
        severity,
        platformsFromImplementation(implementation),
        null,
        implementation,
      )

    /**
     * Creates a new issue. The description strings can use some simple markup; see the
     * [TextFormat.RAW] documentation for details.
     *
     * @param id the fixed id of the issue
     * @param briefDescription short summary (typically 5-6 words or less), typically describing the
     *   **problem** rather than the **fix** (e.g. "Missing minSdkVersion")
     * @param explanation a full explanation of the issue, with suggestions for
     * @param implementation the default implementation for this issue
     * @param moreInfo additional information URL how to fix it
     * @param category the associated category, if any
     * @param priority the priority, a number from 1 to 10 with 10 being most important/severe
     * @param severity the default severity of the issue
     * @param androidSpecific true if this issue only applies to Android, false if it does not apply
     *   to Android at all, and null if not specified or should run on all platforms. Convenience
     *   for specifying platforms=[ANDROID].
     * @param platforms Set of platform scopes where this issue applies.
     * @return a new [Issue]
     */
    fun create(
      id: String,
      briefDescription: String,
      explanation: String,
      implementation: Implementation,
      moreInfo: String? = null,
      category: Category = Category.CORRECTNESS,
      priority: Int = 5,
      severity: Severity = Severity.WARNING,
      enabledByDefault: Boolean = true,
      androidSpecific: Boolean? = null,
      platforms: EnumSet<Platform>? = null,
      suppressAnnotations: Collection<String>? = null,
    ): Issue =
      Issue(
          id,
          briefDescription,
          explanation,
          category,
          priority,
          severity,
          platforms
            ?: androidSpecific?.let(::platformsFromAndroidSpecificFlag)
            ?: platformsFromImplementation(implementation),
          // Use a mutable list here such that we can add id's to it if we allow
          // suppressing with the specific id using special -Xdisable flags; see
          // FlagConfiguration.validateDisablingAllowed
          suppressAnnotations?.toMutableList(),
          implementation,
        )
        .apply {
          if (moreInfo != null) addMoreInfo(moreInfo)
          if (!enabledByDefault) setEnabledByDefault(false)
        }

    private fun platformsFromAndroidSpecificFlag(specific: Boolean): EnumSet<Platform> =
      if (specific) Platform.ANDROID_SET else Platform.JDK_SET

    private fun platformsFromImplementation(impl: Implementation): EnumSet<Platform> =
      when {
        scopeImpliesAndroid(impl.scope) -> Platform.ANDROID_SET
        else -> Platform.UNSPECIFIED
      }

    private fun scopeImpliesAndroid(scope: EnumSet<Scope>): Boolean {
      return scope.contains(Scope.MANIFEST) ||
        scope.contains(Scope.RESOURCE_FILE) ||
        scope.contains(Scope.BINARY_RESOURCE_FILE) ||
        scope.contains(Scope.ALL_RESOURCE_FILES)
    }
  }

  /** Interface implemented by classes which can provide a list of ignored id's. */
  interface IgnoredIdProvider {
    /**
     * Returns a comma separated list of issue id's to be ignored. This string list can be specified
     * in a number of places, such as in `tools:ignore` XML attributes, in `//noinspection` comments
     * in source files that use // as line comments, etc.
     */
    fun getIgnoredIds(): String
  }
}
