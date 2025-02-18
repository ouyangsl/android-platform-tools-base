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
package com.android.tools.lint.detector.api

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.VALUE_TRUE
import com.android.SdkConstants.XMLNS_PREFIX
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.LintFixPerformer
import com.android.tools.lint.detector.api.LintFix.Companion.create
import com.android.tools.lint.detector.api.LintFix.ReplaceString.Companion.INSERT_BEGINNING
import com.android.tools.lint.detector.api.LintFix.ReplaceString.Companion.INSERT_END
import com.android.utils.XmlUtils
import com.google.common.base.Splitter
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.util.text.nullize
import java.io.File
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min
import org.intellij.lang.annotations.RegExp
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.w3c.dom.Attr

/**
 * A **description** of a quickfix for a lint warning, which provides structured data for use by the
 * IDE to create an actual fix implementation. For example, a [LintFix] can state that it aims to
 * set a given attribute to a given value. When lint is running in the IDE, the quickfix machinery
 * will look at the [LintFix] objects and add an actual implementation which sets the attribute.
 *
 * The set of operations is quite limited at the moment; more will be added over time.
 */
open class LintFix
protected constructor(
  @field:Nls private var displayName: String? = null,
  @field:Nls private var familyName: String? = null,
  /**
   * A location range associated with this fix, if different from the associated incident's range.
   */
  open var range: Location? = null,
) {
  /** The display name, a short user-visible description of the fix */
  open fun getDisplayName(): String? = displayName

  /** Returns true if a custom display name was set on this group */
  fun hasDisplayName(): Boolean = displayName != null

  /**
   * The "family" name; the shared name to use to apply *all* fixes of the same family name in a
   * single go. For example, lint may have registered a quickfix to update library version from
   * "1.3" to "1.4", and the display name for this quickfix is "Update version from 1.3 to 1.4".
   * When lint is run on a file, there may be a handful of libraries that all are offered different
   * version updates. If the lint fix provides a shared family name for all of these, such as
   * "Update Dependencies", then the IDE will show this as a single action for the whole file and
   * allow a single click to invoke all the actions in a single go.
   */
  fun getFamilyName(): String? = familyName

  /**
   * Whether this fix can be applied by a robot, e.g. does not require human intervention. These
   * kinds of fixes can be automatically applied when lint is run in fix-mode where it applies all
   * the suggested (eligible) fixes.
   *
   * Examples of fixes which are not auto-fixable:
   *
   * (1) A fix which introduces a semantic change that may not be desirable. For example, lint may
   * warn that the use of an API is discouraged and offer a similar but not identical replacement;
   * in this case the developer needs to consider the implications of the suggestion.
   *
   * (2) A fix for a problem where just a part of the solution is offered as a fix, and there are
   * many other plausible paths a developer might take, such as lint telling you that you have too
   * many actions in the toolbar, and a fix is offered to move each action into a menu.
   */
  @JvmField var robot = false // unless explicitly marked as safe

  /**
   * Whether this fix is independent of other fixes getting applied.
   *
   * Lint can automatically apply all fixes which are independent in a single pass. An example of an
   * independent fix is removal of an unused import; removing one unused import does not invalidate
   * a warning (and fix) for another unused import. (Of course, it's possible that another fix will
   * introduce a new dependency on the formerly unused class, but this is rare.)
   *
   * However, if we have a duplicate declaration warning, we might put a fix on each one of the
   * duplicates to delete them; if we apply one, we wouldn't want to apply the other. In fix mode,
   * lint will only apply the first fix in a compilation unit that is not independent; it will then
   * need to re-analyze the compilation unit a second time, and if there are additional fixes found,
   * apply just the first such dependent fix, and so on. This means that for N fixes that are not
   * independent, it will reanalyze the file N times, which is obviously slower.
   */
  @JvmField var independent = false // unless explicitly marked as safe

  open fun autoFix(robot: Boolean, independent: Boolean): LintFix {
    this.robot = robot
    this.independent = independent
    return this
  }

  /**
   * Convenience method for [autoFix]: indicates that this fix can safely be applied in auto-fix
   * mode, in parallel with other fixes.
   *
   * @return this
   */
  fun autoFix(): LintFix {
    autoFix(robot = true, independent = true)
    return this
  }

  /** Builder for creating various types of fixes */
  class Builder {
    @Nls private var displayName: String? = null

    @Nls private var familyName: String? = null

    /**
     * Sets display name. If not supplied a default will be created based on the type of quickfix.
     *
     * @param displayName the display name
     * @return this
     */
    fun name(displayName: String?): Builder {
      this.displayName = displayName
      return this
    }

    /**
     * Sets display name and family name. If not supplied a default will be created based on the
     * type of quickfix.
     *
     * @param displayName the displayName
     * @param familyName the "family" name; the shared name to use to apply *all* fixes of the same
     *   family name in a single go.
     * @return this
     */
    fun name(displayName: String, familyName: String): Builder {
      this.displayName = displayName
      this.familyName = familyName
      return this
    }

    /**
     * Sets display name and family name. If not supplied a default will be created based on the
     * type of quickfix.
     *
     * @param displayName the displayName
     * @param useAsFamilyNameToo if true, use the display name as the family name too; this means
     *   that the display name is general and does not refer to specifics for a given listed issue
     * @return this
     */
    fun name(displayName: String, useAsFamilyNameToo: Boolean): Builder {
      this.displayName = displayName
      if (useAsFamilyNameToo) {
        familyName = displayName
      }
      return this
    }

    /**
     * Sets the family name.
     *
     * @param familyName the "family" name; the shared name to use to apply *all* fixes of the same
     *   family name in a single go.
     * @return this
     */
    fun sharedName(familyName: String?): Builder {
      this.familyName = familyName
      return this
    }

    /**
     * Sets the "family" name; the shared name to use to apply *all* fixes of the same family name
     * in a single go. For example, lint may have registered a quickfix to update library version
     * from "1.3" to "1.4", and the display name for this quickfix is "Update version from 1.3 to
     * 1.4". When lint is run on a file, there may be a handful of libraries that all are offered
     * different version updates. If the lint fix provides a shared family name for all of these,
     * such as "Update Dependencies", then the IDE will show this as a single action for the whole
     * file and allow a single click to invoke all the actions in a single go.
     *
     * @param familyName the family name
     * @return this
     */
    fun family(familyName: String?): Builder {
      this.familyName = familyName
      return this
    }

    /** Creates a group of fixes */
    fun group(): GroupBuilder {
      return GroupBuilder(displayName, familyName).type(GroupType.ALTERNATIVES)
    }

    /** Creates a number of alternatives fixes; alias for [group] */
    fun alternatives(): GroupBuilder {
      return group()
    }

    /**
     * Creates a composite fix: multiple lint fixes which will all be applied as a single unit.
     *
     * **NOTE:** Be careful combining multiple fixes that are potentially overlapping, such as
     * replace strings.
     *
     * Make sure the fixes are not conflicting with each other (in other words, that the resulting
     * edits are not overlapping).
     */
    fun composite(): GroupBuilder {
      return GroupBuilder(displayName, familyName).type(GroupType.COMPOSITE)
    }

    /**
     * Creates a composite fix: multiple lint fixes which will all be applied as a single unit.
     *
     * **NOTE:** Be careful combining multiple fixes that are potentially overlapping, such as
     * replace strings.
     *
     * Make sure the fixes are not conflicting with each other (in other words, that the resulting
     * edits are not overlapping).
     */
    fun composite(vararg fixes: LintFix?): LintFix {
      return GroupBuilder(displayName, familyName)
        .type(GroupType.COMPOSITE)
        .join(*fixes.filterNotNull().toTypedArray())
        .build()
    }

    /**
     * Creates a composite fix: multiple lint fixes which will all be applied as a single unit.
     *
     * **NOTE:** Be careful combining multiple fixes that are potentially overlapping, such as
     * replace strings.
     *
     * Make sure the fixes are not conflicting with each other (in other words, that the resulting
     * edits are not overlapping).
     */
    fun composite(fixes: List<LintFix?>): LintFix {
      return GroupBuilder(displayName, familyName)
        .type(GroupType.COMPOSITE)
        .join(*fixes.filterNotNull().toTypedArray())
        .build()
    }

    /**
     * Creates a fix list from a set of lint fixes. The IDE will show all of these as separate
     * options.
     *
     * @param fixes fixes to combine
     * @return a fix representing the list
     */
    fun group(vararg fixes: LintFix?): LintFix {
      return GroupBuilder(displayName, familyName)
        .join(*(fixes.filterNotNull().toTypedArray()))
        .build()
    }

    /**
     * Creates a fix list from a set of lint fixes. The IDE will show all of these as separate
     * options.
     *
     * Alias for [group]
     *
     * @param fixes fixes to combine
     * @return a fix representing the list
     */
    fun alternatives(vararg fixes: LintFix?): LintFix {
      val availableFixes = fixes.filterNotNull()
      return if (availableFixes.isEmpty()) {
        map().build()
      } else if (availableFixes.size == 1) {
        availableFixes[0]
      } else {
        group(*availableFixes.toTypedArray())
      }
    }

    /**
     * Replace a string or regular expression
     *
     * @return a string replace builder
     */
    fun replace(): ReplaceStringBuilder {
      return ReplaceStringBuilder(displayName, familyName)
    }

    /** Creates a new text file with the given contents */
    fun newFile(file: File, contents: String): CreateFileBuilder {
      return CreateFileBuilder(displayName, familyName).file(file).contents(contents)
    }

    /** Creates a new binary file with the given contents */
    fun newFile(file: File, contents: ByteArray): CreateFileBuilder {
      return CreateFileBuilder(displayName, familyName).file(file).contents(contents)
    }

    /** Deletes the given file or directory */
    fun deleteFile(file: File): CreateFileBuilder {
      return CreateFileBuilder(displayName, familyName).delete(file)
    }

    /**
     * Set or clear an attribute
     *
     * @return a set attribute builder
     */
    fun set(): SetAttributeBuilder {
      return SetAttributeBuilder(displayName, familyName)
    }

    /**
     * Clear an attribute
     *
     * @return a set attribute builder
     */
    fun unset(): SetAttributeBuilder {
      return SetAttributeBuilder(displayName, familyName).value(null)
    }

    /**
     * Sets a specific attribute
     *
     * @return a set attribute builder
     */
    operator fun set(namespace: String?, attribute: String, value: String?): SetAttributeBuilder {
      return SetAttributeBuilder(displayName, familyName)
        .namespace(namespace)
        .attribute(attribute)
        .value(value)
    }

    /**
     * Sets a specific attribute
     *
     * @return a set attribute builder
     */
    fun unset(namespace: String?, attribute: String): SetAttributeBuilder {
      return SetAttributeBuilder(displayName, familyName)
        .namespace(namespace)
        .attribute(attribute)
        .value(null)
    }

    /**
     * Renames a tag
     *
     * @return a replace string builder
     */
    fun renameTag(current: String, newName: String, range: Location? = null): GroupBuilder {
      val open =
        replace()
          .pattern("<($current)\\b")
          .with(newName)
          .apply { if (range != null) this.range(range) }
          .build()
      val close =
        replace()
          .pattern("</($current)\\s*>")
          .with(newName)
          .optional()
          .apply { if (range != null) this.range(range) }
          .build()
      return name("Replace with `<$newName>`").composite().add(open).add(close)
    }

    /**
     * Given an [attribute], change its namespace to [newNamespace] and/or its attribute name to
     * [newAttribute] and/or its value to [newValue]. (These all default to their current values, so
     * you only need to specify what you want to change.) Typically, if you're only changing the
     * value, use [set] directly; this method is primarily intended for when you want to change the
     * namespace and/or attribute name (and at the same time unset the old attribute.)
     */
    fun replaceAttribute(
      attribute: Attr,
      newNamespace: String? = attribute.namespaceURI.nullize(),
      newAttribute: String = attribute.localName ?: attribute.name,
      newValue: String = attribute.value,
    ): GroupBuilder {
      val prefix =
        if (newNamespace != null)
          attribute.lookupPrefix(newNamespace)
            ?: LintFixPerformer.suggestNamespacePrefix(newNamespace)
        else null
      return replaceAttribute(
        attribute.namespaceURI.nullize(),
        attribute.localName ?: attribute.name,
        newValue,
        newNamespace,
        newAttribute,
        prefix,
      )
    }

    /**
     * Given an [attribute] in the given [namespace] (where null means no namespace), change its
     * value to [newValue], its namespace to [newNamespace] and its attribute name to
     * [newAttribute]. (These all default to their current values, so you only need to specify what
     * you want to change.) Typically, if you're only changing the value, use [set] directly; this
     * method is primarily intended for when you want to change the namespace and/or attribute name
     * (and at the same time unset the old attribute.)
     */
    fun replaceAttribute(
      namespace: String?,
      attribute: String,
      newValue: String,
      newNamespace: String? = namespace,
      newAttribute: String = attribute,
      newNamespacePrefix: String? = LintFixPerformer.suggestNamespacePrefix(newNamespace),
    ): GroupBuilder {
      return composite()
        .name(
          displayName
            ?: if (!newNamespacePrefix.isNullOrBlank()) {
              "Update to `$newNamespacePrefix:$newAttribute`"
            } else if (newAttribute != attribute) {
              "Update to `$newAttribute`"
            } else if (namespace != null) {
              "Drop namespace prefix"
            } else {
              "Replace attribute"
            }
        )
        .add(unset(namespace, attribute).build())
        .add(set(newNamespace, newAttribute, newValue).build())
    }

    /** Provides a map with details for the quickfix implementation */
    fun map(): FixMapBuilder {
      return FixMapBuilder(displayName, familyName)
    }

    /**
     * Provides a map with details for the quickfix implementation, pre-initialized with the given
     * objects
     */
    private fun map(vararg args: Any?): FixMapBuilder {
      val builder = map()
      val map = builder.map
      assert(
        args.size % 2 == 0 // keys and values
      )
      var i = 0
      while (i < args.size) {
        val key = args[i].toString()
        val value = args[i + 1]
        if (value != null) {
          val previous = map.put(key, value)
          assert(
            previous == null // Clashing keys
          )
        }
        i += 2
      }
      return builder
    }

    /**
     * Passes one or more pieces of data; this will be transferred as a map behind the scenes. This
     * is a convenience wrapper around [map] and [FixMapBuilder.build]. Pairs with null values are
     * skipped.
     *
     * @return a fix
     */
    fun data(vararg args: Any?): LintFix {
      return map(*args).build()
    }

    /**
     * Creates a "fix" which can display a URL
     *
     * @return a fix builder
     */
    fun url(): UrlBuilder {
      return UrlBuilder(displayName, familyName, null)
    }

    /**
     * Creates a "fix" which displays a URL
     *
     * @return a fix builder
     */
    fun url(url: String): UrlBuilder {
      return UrlBuilder(displayName, familyName, url)
    }

    /**
     * Creates a fix which annotates the given element with the given annotation. The annotation is
     * provided as fully qualified source code, e.g. `
     * android.support.annotation.SuppressLint("id")`. If the replace parameter is true, the
     * annotation will replace an existing annotation with the same class name (should be set to
     * true unless you're dealing with a repeatable annotation).
     *
     * @return a fix builder
     */
    @Deprecated("Use annotate(String, Context?, PsiElement?, Boolean = true) instead.")
    @JvmOverloads
    fun annotate(source: String, replace: Boolean = true): AnnotateBuilder {
      return AnnotateBuilder(displayName, familyName, source, replace)
    }

    /**
     * Creates a fix with extra information about the location and context, to more accurately
     * pinpoint where the annotation should be placed. Sometimes we fail to correctly identify the
     * location. In this case, the location can be explicitly specified using `.range(location)`.
     */
    @JvmOverloads
    fun annotate(
      source: String,
      context: Context?,
      element: PsiElement?,
      replace: Boolean = true,
    ): AnnotateBuilder {
      return AnnotateBuilder(displayName, familyName, source, replace, context, element)
    }
  }

  /** Builder for creating an annotation fix */
  class AnnotateBuilder
  internal constructor(
    @field:Nls private val displayName: String?,
    @field:Nls private var familyName: String?,
    annotation: String,
    private val replace: Boolean,
    private val context: Context? = null,
    private val element: PsiElement? = null,
  ) {
    private val annotation: String = if (annotation.startsWith("@")) annotation else "@$annotation"
    private var range: Location? =
      if (context == null || element == null) null
      else {
        // If a location is not explicitly specified for a quickfix, it will be widened by
        // `LintDriver#Incident.ensureInitialized` and may contain undesirable elements,
        // like comments. Here we specify as specific a location as possible so that
        // annotations are placed before any code, but after comments.
        // This problem was noticed in b/249043377.
        var anchorElement =
          when (element) {
            is KtNamedFunction -> element.modifierList ?: element.funKeyword ?: element
            is PsiMethod -> element.modifierList
            is KtProperty -> element.modifierList ?: element.valOrVarKeyword
            is PsiField -> element.modifierList ?: element.typeElement ?: element
            else -> null
          }

        if (anchorElement is KtLightElement<*, *>) {
          anchorElement = (anchorElement as KtLightElement<*, *>).kotlinOrigin
        }

        context.getLocation(anchorElement ?: element).start?.let { start ->
          context.getLocation(element).end?.let { end ->
            extractOffsets(Location.create(context.file, start, end))
          }
        }
      }
    private var robot = false
    private var independent = false

    /**
     * Sets a location range to use for searching for the text or pattern. Useful if you want to
     * make a replacement that is larger than the error range highlighted as the problem range.
     */
    fun range(range: Location): AnnotateBuilder {
      this.range = extractOffsets(range)
      return this
    }

    /**
     * Convenience method for [autoFix]: indicates that this fix can safely be applied in auto-fix
     * mode, in parallel with other fixes.
     *
     * @return this
     */
    fun autoFix(): AnnotateBuilder {
      autoFix(robot = true, independent = true)
      return this
    }

    /**
     * Sets options related to auto-applying this fix.
     *
     * @param robot whether this fix can be applied by a robot, e.g. does not require human
     *   intervention
     * @param independent whether it is **not** the case that applying other fixes simultaneously
     *   can invalidate this fix
     * @return this
     */
    fun autoFix(robot: Boolean, independent: Boolean): AnnotateBuilder {
      this.robot = robot
      this.independent = independent
      return this
    }

    /** Creates a fix from this builder */
    fun build(): LintFix {
      val desc: String
      if (displayName != null) {
        desc = displayName
      } else {
        val index = annotation.indexOf('(')
        val last =
          if (index != -1) annotation.lastIndexOf('.', index) else annotation.lastIndexOf('.')
        val simpleName: String =
          if (last != -1) {
            if (index != -1) {
              "@" + annotation.substring(last + 1, index)
            } else {
              "@" + annotation.substring(last + 1)
            }
          } else {
            annotation
          }
        desc = "Annotate with $simpleName"
      }
      return AnnotateFix(desc, familyName, annotation, replace, range, robot, independent)
    }
  }

  /** Captures the various types of a [LintFixGroup] */
  enum class GroupType {
    /** This group represents a single fix where all the fixes should be applied as one */
    COMPOSITE,

    /** This group represents separate fix alternatives the user can choose between */
    ALTERNATIVES,
  }

  /** Builder for constructing a group of fixes */
  class GroupBuilder
  internal constructor(
    @field:Nls private var displayName: String?,
    @field:Nls private var familyName: String?,
  ) {
    private var type = GroupType.ALTERNATIVES
    private val list: MutableList<LintFix> = Lists.newArrayListWithExpectedSize(4)
    private var robot = false
    private var independent = false

    /**
     * Sets display name. If not supplied a default will be created based on the type of quickfix.
     *
     * @param displayName the display name
     * @return this
     */
    fun name(displayName: String?): GroupBuilder {
      this.displayName = displayName
      return this
    }

    /**
     * Sets display name and family name. If not supplied a default will be created based on the
     * type of quickfix.
     *
     * @param displayName the displayName
     * @param familyName the "family" name; the shared name to use to apply *all* fixes of the same
     *   family name in a single go.
     * @return this
     */
    fun name(displayName: String, familyName: String): GroupBuilder {
      this.displayName = displayName
      this.familyName = familyName
      return this
    }

    /**
     * Sets the family name.
     *
     * @param familyName the "family" name; the shared name to use to apply *all* fixes of the same
     *   family name in a single go.
     * @return this
     */
    fun sharedName(familyName: String?): GroupBuilder {
      this.familyName = familyName
      return this
    }

    /**
     * Sets options related to auto-applying this fix. Convenience method for setting both [robot]
     * and [independent]
     *
     * @param robot whether this fix can be applied by a robot, e.g. does not require human
     *   intervention
     * @param independent whether it is **not** the case that applying other fixes simultaneously
     *   can invalidate this fix
     * @return this
     */
    fun autoFix(robot: Boolean, independent: Boolean): GroupBuilder {
      this.robot = robot
      this.independent = independent
      for (fix in list) {
        fix.autoFix(robot, independent)
      }
      return this
    }

    /**
     * Convenience method for [autoFix]: indicates that this fix can safely be applied in auto-fix
     * mode, in parallel with other fixes.
     *
     * @return this
     */
    fun autoFix(): GroupBuilder {
      autoFix(robot = true, independent = true)
      return this
    }

    /** Adds the given fixes to this group */
    fun join(vararg fixes: LintFix): GroupBuilder {
      fixes.forEach { add(it) }
      return this
    }

    /** Adds the given fix to this group */
    fun add(fix: LintFix): GroupBuilder {
      if (type == GroupType.COMPOSITE && fix is LintFixGroup) {
        if (fix.type == GroupType.COMPOSITE) {
          // Composite: just join them
          fix.fixes.forEach { add(it) }
          return this
        } else {
          error("Cannot place alternatives-groups inside composite groups")
        }
      }
      list.add(fix)
      return this
    }

    fun type(type: GroupType): GroupBuilder {
      this.type = type
      return this
    }

    /**
     * Sets a location range to use for searching for the text or pattern. Useful if you want to
     * make a replacement that is larger than the error range highlighted as the problem range.
     */
    fun range(range: Location): GroupBuilder {
      var found = false
      for (fix in list) {
        if (fix.range == null) {
          fix.range = range
          found = true
        }
      }
      if (!found) {
        error(
          "All the nested fixes already have a custom range set. Make sure this method is called after adding " +
            "all the nested fixes. (If the fixes all have appropriate ranges setting it on the group is not necessary.)"
        )
      }

      return this
    }

    /** Construct a [LintFix] for this group of fixes */
    fun build(): LintFix {
      assert(list.isNotEmpty())
      if (list.size == 1) {
        val single = list[0]
        displayName?.let {
          single.displayName = it
          single.familyName = familyName
        }
        return single
      }
      return LintFixGroup(displayName, familyName, type, list, robot, independent)
    }
  }

  /** A builder for replacing strings */
  class ReplaceStringBuilder
  internal constructor(
    @field:Nls private var displayName: String?,
    @field:Nls private var familyName: String?,
  ) {
    private var newText: String? = null
    private var oldText: String? = null
    private var selectPattern: String? = null
    private var shortenNames = false
    private var reformat = false
    private var robot = false
    private var independent = false
    private var imports: MutableList<String>? = null
    private var repeatedly = false
    private var optional = false
    private var sortPriority = 0

    @RegExp private var oldPattern: String? = null
    private var patternFlags = 0
    private var range: Location? = null

    /**
     * Sets display name. If not supplied a default will be created based on the type of quickfix.
     *
     * @param displayName the display name
     * @return this
     */
    fun name(displayName: String?): ReplaceStringBuilder {
      this.displayName = displayName
      return this
    }

    /**
     * Sets display name and family name. If not supplied a default will be created based on the
     * type of quickfix.
     *
     * @param displayName the displayName
     * @param familyName the "family" name; the shared name to use to apply *all* fixes of the same
     *   family name in a single go.
     * @return this
     */
    fun name(displayName: String, familyName: String): ReplaceStringBuilder {
      this.displayName = displayName
      this.familyName = familyName
      return this
    }

    /**
     * Sets the family name.
     *
     * @param familyName the "family" name; the shared name to use to apply *all* fixes of the same
     *   family name in a single go.
     * @return this
     */
    fun sharedName(familyName: String?): ReplaceStringBuilder {
      this.familyName = familyName
      return this
    }

    /** Replaces the given pattern match (or the first group within it, if any) */
    fun pattern(@RegExp oldPattern: String?): ReplaceStringBuilder {
      return pattern(oldPattern, 0)
    }

    /**
     * Replaces the given pattern match (or the first group within it, if any), along with the given
     * [Pattern] flags.
     */
    fun pattern(@RegExp oldPattern: String?, flags: Int): ReplaceStringBuilder {
      if (oldPattern == null) {
        this.oldPattern = null
        return this
      }
      assert(oldText == null)
      assert(this.oldPattern == null)
      if (oldPattern.indexOf('(') == -1) {
        this.oldPattern = "($oldPattern)"
      } else {
        this.oldPattern = oldPattern
      }
      this.patternFlags = flags
      return this
    }

    /** Replaces the given literal text */
    fun text(oldText: String?): ReplaceStringBuilder {
      if (oldText == null) {
        this.oldText = null
        return this
      }
      assert(this.oldText == null) { "Should not call text, beginning or end more than once" }
      assert(oldPattern == null)
      this.oldText = oldText
      return this
    }

    /**
     * Sets a location range to use for searching for the text or pattern. Useful if you want to
     * make a replacement that is larger than the error range highlighted as the problem range.
     */
    fun range(range: Location): ReplaceStringBuilder {
      this.range = extractOffsets(range)
      return this
    }

    /**
     * Sets the sorting priority of this fix. This is *only* relevant when there are multiple
     * insertions at the same location; in that case, the sorting priority will be used to sort the
     * items. If no sorting priority is set, it will be the reverse insertion order. In other words,
     * if you have the fixes "at 0, insert foo", followed by "at 0, insert bar", the result will be
     * "foobar", since we *first* insert "foo" at 0, and inserting bar at 0 on that result would end
     * up as "foobar".
     */
    fun priority(priority: Int): ReplaceStringBuilder {
      this.sortPriority = priority
      return this
    }

    /** Replaces this entire range */
    fun all(): ReplaceStringBuilder {
      return this
    }

    /** Inserts into the beginning of the range */
    fun beginning(): ReplaceStringBuilder {
      oldText = INSERT_BEGINNING
      return this
    }

    /** Inserts after the end of the range */
    fun end(): ReplaceStringBuilder {
      oldText = INSERT_END
      return this
    }

    /**
     * Sets a pattern to select; if it contains parentheses, group(1) will be selected. To just set
     * the caret, use an empty group.
     */
    fun select(@RegExp selectPattern: String?): ReplaceStringBuilder {
      this.selectPattern = selectPattern
      return this
    }

    /**
     * The text to replace the old text or pattern with. Note that the special syntax \k<n> can be
     * used to reference the n-th group, if and only if this replacement is using [pattern]}.
     */
    fun with(newText: String?): ReplaceStringBuilder {
      assert(this.newText == null)
      this.newText = newText
      return this
    }

    /**
     * Adds one or more imports to add if necessary when performing the fix. Normally you don't need
     * to do this; it's better to use fully qualified names in your replacement code snippets; this
     * ensures that if there is a naming conflict (where the same name is already imported for a
     * different package) lint will leave the fully qualified name in place. However, in some
     * scenarios, separately listing the import is necessary, such as for example with using
     * extension methods.
     */
    fun imports(vararg imports: String): ReplaceStringBuilder {
      val existing = this.imports ?: mutableListOf<String>().also { this.imports = it }
      existing.addAll(imports)
      return this
    }

    /**
     * The IDE should simplify fully qualified names in the element after this fix has been run (off
     * by default)
     */
    fun shortenNames(): ReplaceStringBuilder {
      shortenNames = true
      return this
    }

    /**
     * Sets whether the IDE should simplify fully qualified names in the element after this fix has
     * been run (off by default)
     */
    fun shortenNames(shorten: Boolean): ReplaceStringBuilder {
      shortenNames = shorten
      return this
    }

    /** Whether the replaced range should be reformatted */
    fun reformat(reformat: Boolean): ReplaceStringBuilder {
      this.reformat = reformat
      return this
    }

    /**
     * Sets whether this fix can be applied by a robot, e.g. does not require human intervention.
     * These kinds of fixes can be automatically applied when lint is run in fix-mode where it
     * applies all the suggested (eligible) fixes.
     *
     * Examples of fixes which are not auto-fixable:
     * 1. A fix which introduces a semantic change that may not be desirable. For example, lint may
     *    warn that the use of an API is discouraged and offer a similar but not identical
     *    replacement; in this case the developer needs to consider the implications of the
     *    suggestion.
     * 2. A fix for a problem where just a part of the solution is offered as a fix, and there are
     *    many other plausible paths a developer might take, such as lint telling you that you have
     *    too many actions in the toolbar, and a fix is offered to move each action into a menu.
     *
     * @param robot whether this fix can be applied by a robot, e.g. does not require human
     *   intervention
     * @return this
     */
    fun robot(robot: Boolean): ReplaceStringBuilder {
      this.robot = robot
      return this
    }

    /**
     * Whether this fix is independent of other fixes getting applied.
     *
     * Lint can automatically apply all fixes which are independent in a single pass. An example of
     * an independent fix is removal of an unused import; removing one unused import does not
     * invalidate a warning (and fix) for another unused import. (Of course, it's possible that
     * another fix will introduce a new dependency on the formerly unused class, but this is rare.)
     *
     * However, if we have a duplicate declaration warning, we might put a fix on each one of the
     * duplicates to delete them; if we apply one, we wouldn't want to apply the other. In fix mode,
     * lint will only apply the first fix in a compilation unit that is not independent; it will
     * then need to re-analyze the compilation unit a second time, and if there are additional fixes
     * found, apply just the first such dependent fix, and so on. This means that for N fixes that
     * are not independent, it will reanalyze the file N times, which is obviously slower.
     *
     * @param independent whether it is **not** the case that applying other fixes simultaneously
     *   can invalidate this fix
     * @return this
     */
    fun independent(independent: Boolean): ReplaceStringBuilder {
      this.independent = independent
      return this
    }

    /**
     * Sets options related to auto-applying this fix. Convenience method for setting both [robot]
     * and [independent]
     *
     * @param robot whether this fix can be applied by a robot, e.g. does not require human
     *   intervention
     * @param independent whether it is **not** the case that applying other fixes simultaneously
     *   can invalidate this fix
     * @return this
     */
    fun autoFix(robot: Boolean, independent: Boolean): ReplaceStringBuilder {
      robot(robot)
      independent(independent)
      return this
    }

    /**
     * Convenience method for [autoFix]: indicates that this fix can safely be applied in auto-fix
     * mode, in parallel with other fixes.
     *
     * @return this
     */
    fun autoFix(): ReplaceStringBuilder {
      autoFix(robot = true, independent = true)
      return this
    }

    /**
     * Normally the replacement happens just once; the first occurrence. But with the `repeatedly`
     * flag set, it will repeat the search and perform repeated replacements throughout the entire
     * fix range -- e.g. in the same sense as "/g" in a sed command.
     */
    fun repeatedly(value: Boolean = true): ReplaceStringBuilder {
      repeatedly = value
      return this
    }

    /**
     * Normally the replacement is required to happen exactly once, or at least once (if
     * [repeatedly] is set). But in some cases the replacement can be optional. This typically
     * doesn't make sense for a quickfix on its own, but is useful in composite fixes. For example,
     * when renaming an XML element tag, we also want to rename the closing tag -- but some elements
     * don't have a closing tag. Instead of having to figure that up front, we'll just mark the
     * closing edit as optional.
     */
    fun optional(value: Boolean = true): ReplaceStringBuilder {
      optional = value
      return this
    }

    /** Constructs a [LintFix] for this string replacement */
    fun build(): LintFix {
      return ReplaceString(
        displayName,
        familyName,
        oldText,
        oldPattern,
        patternFlags,
        selectPattern,
        newText ?: "",
        shortenNames,
        reformat,
        imports ?: emptyList(),
        range,
        repeatedly,
        optional,
        robot,
        independent,
        sortPriority,
      )
    }
  }

  /** A builder for creating (or "un-creating", e.g. deleting) a file */
  class CreateFileBuilder
  internal constructor(
    @field:Nls private var displayName: String?,
    @field:Nls private var familyName: String?,
  ) {
    private var selectPattern: String? = null
    private var delete: Boolean = false
    private var file: File? = null
    private var binary: ByteArray? = null
    private var text: String? = null
    private var reformat = false
    private var robot = false
    private var independent = false

    /**
     * Sets display name and family name. If not supplied a default will be created based on the
     * type of quickfix.
     *
     * @param displayName the displayName
     * @param familyName the "family" name; the shared name to use to apply *all* fixes of the same
     *   family name in a single go.
     * @return this
     */
    fun name(displayName: String? = null, familyName: String? = null): CreateFileBuilder {
      this.displayName = displayName
      this.familyName = familyName
      return this
    }

    /** Sets the file to be created or deleted */
    fun file(file: File): CreateFileBuilder {
      assert(this.file == null)
      this.file = file
      return this
    }

    /** Marks the file for deletion */
    fun delete(file: File): CreateFileBuilder {
      assert(this.file == null && this.binary == null && this.text == null)
      this.file = file
      delete = true
      return this
    }

    /** Sets the text contents to be written to the file */
    fun contents(contents: String): CreateFileBuilder {
      assert(this.binary == null && !delete)
      this.text = contents
      return this
    }

    /** Sets the binary contents to be written to the file */
    fun contents(contents: ByteArray): CreateFileBuilder {
      assert(this.text == null && !delete)
      this.binary = contents
      return this
    }

    /**
     * Sets a pattern to select; if it contains parentheses, group(1) will be selected. To just set
     * the caret, use an empty group.
     */
    fun select(@RegExp selectPattern: String?): CreateFileBuilder {
      this.selectPattern = selectPattern
      return this
    }

    /** Open the file after creating it */
    fun open(): CreateFileBuilder {
      // See select() above" sets a pattern to select; if it contains parentheses, group(1) will be
      // selected.
      // To just set the caret, use an empty group.
      this.selectPattern = "()"
      return this
    }

    /** Whether the newly created file should be reformatted */
    fun reformat(reformat: Boolean): CreateFileBuilder {
      this.reformat = reformat
      return this
    }

    /**
     * Sets whether this fix can be applied by a robot, e.g. does not require human intervention.
     * These kinds of fixes can be automatically applied when lint is run in fix-mode where it
     * applies all the suggested (eligible) fixes.
     *
     * Examples of fixes which are not auto-fixable:
     * 1. A fix which introduces a semantic change that may not be desirable. For example, lint may
     *    warn that the use of an API is discouraged and offer a similar but not identical
     *    replacement; in this case the developer needs to consider the implications of the
     *    suggestion.
     * 2. A fix for a problem where just a part of the solution is offered as a fix, and there are
     *    many other plausible paths a developer might take, such as lint telling you that you have
     *    too many actions in the toolbar, and a fix is offered to move each action into a menu.
     *
     * @param robot whether this fix can be applied by a robot, e.g. does not require human
     *   intervention
     * @return this
     */
    fun robot(robot: Boolean): CreateFileBuilder {
      this.robot = robot
      return this
    }

    /**
     * Whether this fix is independent of other fixes getting applied.
     *
     * Lint can automatically apply all fixes which are independent in a single pass. An example of
     * an independent fix is removal of an unused import; removing one unused import does not
     * invalidate a warning (and fix) for another unused import. (Of course, it's possible that
     * another fix will introduce a new dependency on the formerly unused class, but this is rare.)
     *
     * However, if we have a duplicate declaration warning, we might put a fix on each one of the
     * duplicates to delete them; if we apply one, we wouldn't want to apply the other. In fix mode,
     * lint will only apply the first fix in a compilation unit that is not independent; it will
     * then need to re-analyze the compilation unit a second time, and if there are additional fixes
     * found, apply just the first such dependent fix, and so on. This means that for N fixes that
     * are not independent, it will reanalyze the file N times, which is obviously slower.
     *
     * @param independent whether it is **not** the case that applying other fixes simultaneously
     *   can invalidate this fix
     * @return this
     */
    fun independent(independent: Boolean): CreateFileBuilder {
      this.independent = independent
      return this
    }

    /**
     * Sets options related to auto-applying this fix. Convenience method for setting both [robot]
     * and [independent]
     *
     * @param robot whether this fix can be applied by a robot, e.g. does not require human
     *   intervention
     * @param independent whether it is **not** the case that applying other fixes simultaneously
     *   can invalidate this fix
     * @return this
     */
    fun autoFix(robot: Boolean, independent: Boolean): CreateFileBuilder {
      robot(robot)
      independent(independent)
      return this
    }

    /**
     * Convenience method for [autoFix]: indicates that this fix can safely be applied in auto-fix
     * mode, in parallel with other fixes.
     *
     * @return this
     */
    fun autoFix(): CreateFileBuilder {
      autoFix(robot = true, independent = true)
      return this
    }

    /** Constructs a [LintFix] for this file creation or deletion fix */
    fun build(): LintFix {
      return CreateFileFix(
        displayName,
        familyName,
        selectPattern,
        delete,
        file!!,
        binary,
        text,
        reformat,
        robot,
        independent,
      )
    }
  }

  /** Builder for creating a show-url fix */
  class UrlBuilder
  internal constructor(
    @field:Nls private var displayName: String?,
    @field:Nls private var familyName: String?,
    @field:NonNls private var url: String?,
  ) {
    fun url(@NonNls url: String): UrlBuilder {
      this.url = url
      return this
    }

    fun build(): LintFix {
      return ShowUrl(displayName ?: "Show $url", familyName, url!!)
    }
  }

  /** Builder for creating a set or clear attribute fix */
  class SetAttributeBuilder
  internal constructor(
    @field:Nls private var displayName: String?,
    @field:Nls private var familyName: String?,
  ) {
    private var attribute: String? = null
    private var namespace: String? = null
    private var value: String? = ""
    private var mark: Int? = null
    private var point: Int? = null
    private var robot = false
    private var independent = false
    private var range: Location? = null

    /**
     * Sets display name. If not supplied a default will be created based on the type of quickfix.
     *
     * @param displayName the display name
     * @return this
     */
    fun name(displayName: String?): SetAttributeBuilder {
      this.displayName = displayName
      return this
    }

    /**
     * Sets display name and family name. If not supplied a default will be created based on the
     * type of quickfix.
     *
     * @param displayName the displayName
     * @param familyName the "family" name; the shared name to use to apply *all* fixes of the same
     *   family name in a single go.
     * @return this
     */
    fun name(displayName: String, familyName: String): SetAttributeBuilder {
      this.displayName = displayName
      this.familyName = familyName
      return this
    }

    /**
     * Sets the family name.
     *
     * @param familyName the "family" name; the shared name to use to apply *all* fixes of the same
     *   family name in a single go.
     * @return this
     */
    fun sharedName(familyName: String?): SetAttributeBuilder {
      this.familyName = familyName
      return this
    }

    /**
     * Sets the namespace to the Android namespace (shortcut for [namespace] passing in
     * [ANDROID_URI]
     */
    fun android(): SetAttributeBuilder {
      assert(namespace == null)
      namespace = ANDROID_URI
      return this
    }

    /** Sets the namespace to the given namespace */
    fun namespace(namespace: String?): SetAttributeBuilder {
      assert(this.namespace == null)
      this.namespace = namespace
      return this
    }

    /**
     * Sets the value to the given value. Null means delete (though it's more natural to call
     * [remove]
     */
    fun value(value: String?): SetAttributeBuilder {
      this.value = value
      if (value != null && value.isEmpty()) {
        caret(0) // Setting to empty attribute normally means "let the user edit"
      }
      return this
    }

    /** Sets the attribute name. Should not include the prefix. */
    fun attribute(attribute: String): SetAttributeBuilder {
      assert(attribute.indexOf(':') == -1 || attribute.startsWith(XMLNS_PREFIX)) { attribute }
      assert(this.attribute == null)
      this.attribute = attribute
      return this
    }

    /** Removes the given attribute */
    fun remove(attribute: String): SetAttributeBuilder {
      assert(this.attribute == null)
      this.attribute = attribute
      value = null
      return this
    }

    /** Selects the newly inserted value */
    fun selectAll(): SetAttributeBuilder {
      // value must be set first
      point = XmlUtils.toXmlAttributeValue(value!!).length
      mark = 0
      return this
    }

    /**
     * Sets the value to TＯDＯ meant for values that aren't optional. You can also supply a prefix
     * and/or a suffix.
     *
     * @param prefix optional prefix to add before the TＯDＯ marker
     * @param suffix optional suffix to add after the TＯDＯ marker
     * @return a builder for TＯDＯ edits
     */
    @JvmOverloads
    fun todo(
      namespace: String?,
      attribute: String,
      prefix: String? = null,
      suffix: String? = null,
    ): SetAttributeBuilder {
      namespace(namespace)
      attribute(attribute)
      val sb = StringBuilder()
      if (prefix != null) {
        sb.append(prefix)
      }
      val start = sb.length
      sb.append(TODO)
      val end = sb.length
      if (suffix != null) {
        sb.append(suffix)
      }
      value(sb.toString())
      select(start, end)
      return this
    }

    /**
     * Sets a location range to use for searching for the element. Useful if you want to work on
     * elements outside the element marked as the problem range.
     */
    fun range(range: Location?): SetAttributeBuilder {
      this.range = if (range != null) extractOffsets(range) else null
      return this
    }

    /** Selects the value in the offset range (relative to value start) */
    fun select(start: Int, end: Int): SetAttributeBuilder {
      mark = min(start, end)
      point = max(start, end)
      return this
    }

    /**
     * Moves the caret to the given offset (relative to the position of the value text; can be
     * negative ([means not set][Integer.MIN_VALUE]
     */
    fun caret(valueStartDelta: Int): SetAttributeBuilder {
      point = valueStartDelta
      mark = point
      return this
    }

    /** Moves the caret to the beginning of the value after applying the new attribute */
    fun caretBegin(): SetAttributeBuilder {
      return caret(0)
    }

    /** Moves the caret to the end of the value after applying the new attribute */
    fun caretEnd(): SetAttributeBuilder {
      assert(
        value != null // must be set first
      )
      return caret(value!!.length)
    }

    /**
     * Sets whether this fix can be applied by a robot, e.g. does not require human intervention.
     * These kinds of fixes can be automatically applied when lint is run in fix-mode where it
     * applies all the suggested (eligible) fixes.
     *
     * Examples of fixes which are not auto-fixable:
     *
     * (1) A fix which introduces a semantic change that may not be desirable. For example, lint may
     * warn that the use of an API is discouraged and offer a similar but not identical replacement;
     * in this case the developer needs to consider the implications of the suggestion.
     *
     * (2) A fix for a problem where just a part of the solution is offered as a fix, and there are
     * many other plausible paths a developer might take, such as lint telling you that you have too
     * many actions in the toolbar, and a fix is offered to move each action into a menu.
     *
     * @param robot whether this fix can be applied by a robot, e.g. does not require human
     *   intervention
     * @return this
     */
    fun robot(robot: Boolean): SetAttributeBuilder {
      this.robot = robot
      return this
    }

    /**
     * Whether this fix is independent of other fixes getting applied.
     *
     * Lint can automatically apply all fixes which are independent in a single pass. An example of
     * an independent fix is removal of an unused import; removing one unused import does not
     * invalidate a warning (and fix) for another unused import. (Of course, it's possible that
     * another fix will introduce a new dependency on the formerly unused class, but this is rare.)
     *
     * However, if we have a duplicate declaration warning, we might put a fix on each one of the
     * duplicates to delete them; if we apply one, we wouldn't want to apply the other. In fix mode,
     * lint will only apply the first fix in a compilation unit that is not independent; it will
     * then need to re-analyze the compilation unit a second time, and if there are additional fixes
     * found, apply just the first such dependent fix, and so on. This means that for N fixes that
     * are not independent, it will reanalyze the file N times, which is obviously slower.
     *
     * @param independent whether it is **not** the case that applying other fixes simultaneously
     *   can invalidate this fix
     * @return this
     */
    fun independent(independent: Boolean): SetAttributeBuilder {
      this.independent = independent
      return this
    }

    /**
     * Sets options related to auto-applying this fix. Convenience method for setting both [robot]
     * and [independent]
     *
     * @param robot whether this fix can be applied by a robot, e.g. does not require human
     *   intervention
     * @param independent whether it is **not** the case that applying other fixes simultaneously
     *   can invalidate this fix
     * @return this
     */
    fun autoFix(robot: Boolean, independent: Boolean): SetAttributeBuilder {
      robot(robot)
      independent(independent)
      return this
    }

    /**
     * Convenience method for [autoFix]: indicates that this fix can safely be applied in auto-fix
     * mode, in parallel with other fixes.
     *
     * @return this
     */
    fun autoFix(): SetAttributeBuilder {
      autoFix(robot = true, independent = true)
      return this
    }

    /** Constructs a [LintFix] for this attribute operation */
    fun build(): LintFix {
      return SetAttribute(
        displayName,
        familyName,
        namespace,
        attribute!!,
        value,
        range,
        point,
        mark,
        robot,
        independent,
      )
    }
  }

  class FixMapBuilder
  internal constructor(
    @field:Nls private val displayName: String?,
    @field:Nls private val familyName: String?,
  ) {
    /**
     * Values are limited to strings, files, list of strings, list of files, ints and booleans.
     * Throwables can also be in there, but those are only allowed within lint unit tests.
     */
    internal val map: MutableMap<String, Any> = Maps.newHashMapWithExpectedSize(4)

    /** Puts the given value into the map using the given key */
    fun put(key: String, value: String?): FixMapBuilder {
      if (value == null) {
        return this
      }
      assert(!map.containsKey(key))
      map[key] = value
      return this
    }

    /** Puts the given value into the map using the given key */
    fun put(key: String, value: PsiMethod?): FixMapBuilder {
      if (value == null) {
        return this
      }
      assert(!map.containsKey(key))
      map[key] = value
      return this
    }

    /**
     * Puts the given value into the map using the given key. This is only intended for the lint
     * test infrastructure; exceptions cannot be persisted.
     */
    fun put(key: String, throwable: Throwable?): FixMapBuilder {
      if (throwable == null) {
        return this
      }
      assert(!map.containsKey(key))
      map[key] = throwable
      return this
    }

    /** Puts the given value into the map using the given key */
    fun put(key: String, value: Int): FixMapBuilder {
      assert(!map.containsKey(key))
      map[key] = value
      return this
    }

    /** Puts the given value into the map using the given key */
    fun put(key: String, value: Boolean): FixMapBuilder {
      assert(!map.containsKey(key))
      map[key] = value
      return this
    }

    /** Puts the given value into the map using the given key */
    fun put(key: String, value: List<String?>): FixMapBuilder {
      assert(!map.containsKey(key))
      map[key] = value
      return this
    }

    /** Constructs a [LintFix] with this map data */
    fun build(): LintFix {
      return DataMap(displayName, familyName, map)
    }
  }

  /**
   * General map storage for quickfix data; clients can look up via map keys or types of values
   *
   * This class/API is **only** intended for IDE use. Lint checks should be accessing the builder
   * class instead - [create].
   */
  class DataMap(displayName: String?, familyName: String?, private val map: Map<String, Any>) :
    LintFix(displayName, familyName) {
    /** Returns true if this map contains a fix with the given key */
    fun hasKey(key: String): Boolean {
      return map.containsKey(key)
    }

    /** Returns the value for the given String key */
    operator fun get(key: String): Any? {
      return map[key]
    }

    /** Returns the keys */
    fun keys(): Set<String> {
      return map.keys
    }

    override fun toString(): String {
      return map.toString()
    }

    @Contract("_, !null -> !null")
    fun getString(key: String, defaultValue: String?): String? {
      val value = map[key]
      return value?.toString() ?: defaultValue
    }

    fun getStringList(key: String): List<String>? {
      val value = map[key]
      if (value is List<*>) {
        @Suppress("UNCHECKED_CAST") return value as List<String>?
      } else if (value is String) {
        // from XML persistence
        return Splitter.on(",").splitToList(value)
      }
      return null
    }

    @Contract("_, !null -> !null")
    fun getFile(key: String, defaultValue: File?): File? {
      val value = map[key]
      if (value != null) {
        if (value is File) {
          return value
        } else if (value is String) {
          return File(value)
        }
      }
      return defaultValue
    }

    fun getInt(key: String, defaultValue: Int): Int {
      val value = map[key]
      if (value != null) {
        if (value is Number) {
          return value.toInt()
        } else if (value is String) {
          try {
            return value.toInt()
          } catch (ignore: NumberFormatException) {
            // fall through
          }
        }
      }
      return defaultValue
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
      val value = map[key]
      if (value != null) {
        if (value is Boolean) {
          return value
        } else if (value is String) {
          return VALUE_TRUE == value
        }
      }
      return defaultValue
    }

    fun getMethod(key: String): PsiMethod? {
      val value = map[key]
      return if (value is PsiMethod) {
        value
      } else null
    }

    fun getThrowable(key: String): Throwable? {
      val value = map[key]
      return if (value is Throwable) {
        value
      } else null
    }

    fun getApiConstraint(key: String): ApiConstraint? {
      val value = map[key]
      if (value is ApiConstraint) {
        return value
      } else if (value is String) {
        // from XML persistence
        return ApiConstraint.deserialize(value)
      }
      return null
    }
  }

  /** A URL to be offered to be shown as a "fix". */
  class ShowUrl(
    displayName: String,
    familyName: String?,
    val url: String,
    val onUrlOpen: (() -> Unit)? = null,
  ) : LintFix(displayName, familyName)

  /** An annotation to add to the element */
  class AnnotateFix
  internal constructor(
    displayName: String?,
    familyName: String?,
    /** The annotation source code */
    val annotation: String,
    /**
     * If true replace the previous occurrence of the same annotation. Should be used unless you're
     * dealing with a repeatable annotation.
     */
    val replace: Boolean,
    /**
     * A location range for the source region where the fix will operate. Useful when the fix is
     * applying in a wider range than the highlighted problem range.
     */
    range: Location?,
    robot: Boolean,
    independent: Boolean,
  ) : LintFix(displayName, familyName, range) {
    init {
      this.robot = robot
      this.independent = independent
    }
  }

  /**
   * A list of quickfixes
   *
   * This class/API is **only** intended for IDE use. Lint checks should be accessing the builder
   * class instead - [create].
   */
  class LintFixGroup(
    displayName: String?,
    familyName: String?,
    /** The type of group */
    val type: GroupType,
    /** A list of fixes */
    val fixes: List<LintFix>,
    robot: Boolean,
    independent: Boolean,
  ) : LintFix(displayName, familyName) {
    init {
      this.robot = robot
      this.independent = independent
      if (displayName == null && type == GroupType.COMPOSITE && LintClient.isUnitTest) {
        error(
          "You should explicitly set a display name for composite group actions; " +
            "unlike string replacement, set attribute, etc. it cannot produce a good default on its own"
        )
      }
    }

    override var range: Location? = null
      set(value) {
        field = value
        error("Groups can't define an error range; should be set on each member")
      }

    @Nls
    override fun getDisplayName(): String? {
      // For composites, we can display the name of one of the actions
      val displayName = super.getDisplayName()
      if (displayName == null && type == GroupType.COMPOSITE) {
        return fixes.mapNotNull { it.getDisplayName() }.joinToString(" and ")
      }
      return displayName
    }

    override fun autoFix(robot: Boolean, independent: Boolean): LintFix {
      for (fix in fixes) {
        fix.autoFix(robot, independent)
      }
      return super.autoFix(robot, independent)
    }
  }

  /**
   * Convenience class for the common scenario of suggesting a fix which involves setting an XML
   * attribute.
   *
   * This class/API is **only** intended for IDE use. Lint checks should be accessing the builder
   * class instead - [create].
   */
  class SetAttribute(
    displayName: String?,
    familyName: String?,
    /** The namespace */
    val namespace: String?,
    /** The local attribute name */
    val attribute: String,
    /** The value (or null to delete the attribute) */
    val value: String?,
    /**
     * A location range for the source region where the fix will operate. Useful when the fix is
     * applying in a wider range than the highlighted problem range.
     */
    range: Location?,
    /**
     * The caret location to show, OR null if not set. If [mark] is set, the end of the selection
     * too.
     */
    val point: Int?,
    /** The selection anchor, OR null if not set */
    val mark: Int?,
    robot: Boolean,
    independent: Boolean,
  ) : LintFix(displayName, familyName, range) {
    init {
      this.robot = robot
      this.independent = independent
    }

    override fun getDisplayName(): String {
      return super.getDisplayName()
        ?: if (value != null) {
          if (value.isEmpty() || point != null && point > 0) { // point > 0: value is partial?
            "Set $attribute"
          } else {
            "Set $attribute=\"$value\""
          }
        } else {
          "Delete $attribute"
        }
    }
  }

  /**
   * Convenience class for the common scenario of suggesting a fix which involves replacing a static
   * string or regular expression with a replacement string
   *
   * This class/API is **only** intended for IDE use. Lint checks should be accessing the builder
   * class instead - [create].
   */
  class ReplaceString(
    displayName: String?,
    familyName: String?,
    /**
     * The string literal to replace, or [INSERT_BEGINNING] or [INSERT_END] to leave the old text
     * alone and insert the "replacement" text at the beginning or the end
     */
    val oldString: String?,
    /**
     * The regex to replace. Will always have at least one group, which should be the replacement
     * range.
     */
    @RegExp val oldPattern: String?,
    /** [java.util.regex.Pattern] flags to use with [oldPattern], or 0 */
    val patternFlags: Int,
    /** Pattern to select; if it contains parentheses, group(1) will be selected */
    val selectPattern: String?,
    /** The replacement string. */
    val replacement: String,
    /** Whether symbols should be shortened after replacement */
    val shortenNames: Boolean,
    /** Whether the modified text range should be reformatted */
    val reformat: Boolean,
    /**
     * Additional imports to add. Normally, the replacement string should use fully qualified names
     * and lint will automatically handle replacing these with imported symbols when possible (if
     * [shortenNames] is true), but for example for extension methods where you cannot use fully
     * qualified names in place, reference these here.
     */
    val imports: List<String>,
    /**
     * A location range to use for searching for the text or pattern. Useful if you want to make a
     * replacement that is larger than the error range highlighted as the problem range.
     */
    range: Location?,
    /**
     * Normally the replacement happens just once; the first occurrence. But with the global flag
     * set, it will repeat the search and perform repeated replacements throughout the entire fix
     * range -- e.g. in the same sense as "/g" in a sed command.
     */
    val globally: Boolean,
    /**
     * Normally the replacement is required to happen exactly once, or at least once (if [globally]
     * is set). But in some cases the replacement can be optional. This typically doesn't make sense
     * for a quickfix on its own, but is useful in composite fixes. For example, when renaming an
     * XML element tag, we also want to rename the closing tag -- but some elements don't have a
     * closing tag. Instead of having to figure that up front, we'll just mark the closing edit as
     * optional.
     */
    val optional: Boolean,
    robot: Boolean,
    independent: Boolean,
    /**
     * The sorting priority of this fix. This is *only* relevant when there are multiple insertions
     * at the same location; in that case, the sorting priority will be used to sort the items.
     */
    val sortPriority: Int,
  ) : LintFix(displayName, familyName, range) {
    init {
      this.robot = robot
      this.independent = independent
    }

    /** Return display name */
    override fun getDisplayName(): String {
      val displayName = super.getDisplayName()
      return if (displayName != null) {
        displayName
      } else {
        if (replacement.isEmpty()) {
          return if (oldString != null) {
            "Delete \"$oldString\""
          } else "Delete"
        }
        var preview = replacement
        val lineIndex = preview.indexOf('\n')
        if (lineIndex != -1) {
          preview = preview.substring(0, lineIndex) + "..."
        }
        "Replace with $preview"
      }
    }

    /**
     * If this [ReplaceString] specified a regular expression in [oldPattern], and the replacement
     * string [replacement] specifies one or more "back references" (with `(?<name>)` with the
     * syntax `\k<name>` then this method will substitute in the matching group). Note that "target"
     * is a reserved name, used to identify the range that should be completed.
     */
    fun expandBackReferences(matcher: Matcher): String {
      return expandBackReferences(replacement, matcher)
    }

    companion object {
      /**
       * Special marker signifying that we don't want to actually replace any text in the element,
       * just insert the "replacement" at the beginning of the range
       */
      const val INSERT_BEGINNING = "_lint_insert_begin_"

      /**
       * Special marker signifying that we don't want to actually replace any text in the element,
       * just insert the "replacement" at the end of the range
       */
      const val INSERT_END = "_lint_insert_end_"

      /**
       * Given a matched regular expression and a back reference expression, this method produces
       * the expression with back references substituted in.
       */
      @JvmStatic
      fun expandBackReferences(replacement: String, matcher: Matcher): String {
        if (!replacement.contains("\\k<")) {
          return replacement
        }
        val sb = StringBuilder()
        var begin = 0
        while (true) {
          var end = replacement.indexOf("\\k<", begin)
          if (end == -1) {
            sb.append(replacement.substring(begin))
            break
          } else {
            val next = replacement.indexOf('>', end + 3)
            if (next != -1 && Character.isDigit(replacement[end + 3])) {
              sb.append(replacement, begin, end)
              val groupString = replacement.substring(end + 3, next)
              val group = groupString.toInt()
              if (group <= matcher.groupCount()) {
                sb.append(matcher.group(group))
              } else {
                error(
                  "Invalid backreference $group in `$replacement`: there are only ${matcher.groupCount()} matches in this matcher, $matcher"
                )
              }
              begin = next + 1
            } else {
              end += 3
              sb.append(replacement, begin, end)
              begin = end
            }
          }
        }
        return sb.toString()
      }
    }
  }

  /**
   * Fix descriptor for creating or deleting a file. This class/API is **only** intended for IDE
   * use. Lint checks should be accessing the builder class instead - [create].
   */
  class CreateFileFix(
    displayName: String?,
    familyName: String?,
    /** Pattern to select; if it contains parentheses, group(1) will be selected */
    val selectPattern: String?,
    val delete: Boolean,
    val file: File,
    val binary: ByteArray?,
    val text: String?,
    val reformat: Boolean,
    robot: Boolean,
    independent: Boolean,
  ) : LintFix(displayName, familyName, Location.create(file)) {
    init {
      this.robot = robot
      this.independent = independent
    }

    override fun getDisplayName(): String {
      return super.getDisplayName()
        ?: return if (delete) {
          "Delete ${file.name}"
        } else {
          "Create ${file.name}"
        }
    }
  }

  companion object {
    /** Marker inserted in various places to indicate that something is expected from the user */
    const val TODO = "TODO"

    /** Creates a new Quickfix Builder */
    @JvmStatic
    fun create(): Builder {
      return Builder()
    }

    /**
     * Convenience wrapper which checks whether the given fix is a map, and if so returns the value
     * stored by its key
     */
    @Contract("_, _, !null -> !null")
    @JvmStatic
    fun getString(fix: LintFix?, key: String, defaultValue: String?): String? {
      return if (fix is DataMap) {
        fix.getString(key, defaultValue)
      } else defaultValue
    }

    /**
     * Convenience wrapper which checks whether the given fix is a map, and if so returns the value
     * stored by its key
     */
    @JvmStatic
    fun getStringList(fix: LintFix?, key: String): List<String>? {
      return if (fix is DataMap) {
        fix.getStringList(key)
      } else null
    }

    @JvmStatic
    fun getThrowable(fix: LintFix?, key: String): Throwable? {
      return if (fix is DataMap) {
        fix.getThrowable(key)
      } else null
    }

    /**
     * Convenience wrapper which checks whether the given fix is a map, and if so returns the value
     * stored by its key
     */
    @JvmStatic
    fun getInt(fix: LintFix?, key: String, defaultValue: Int): Int {
      return if (fix is DataMap) {
        fix.getInt(key, defaultValue)
      } else defaultValue
    }

    /**
     * Convenience wrapper which checks whether the given fix is a map, and if so returns the value
     * stored by its key
     */
    @JvmStatic
    @Contract("_, _, !null -> !null")
    fun getApiConstraint(
      fix: LintFix?,
      key: String,
      defaultValue: ApiConstraint? = ApiConstraint.UNKNOWN,
    ): ApiConstraint? {
      return if (fix is DataMap) {
        fix.getApiConstraint(key) ?: defaultValue
      } else defaultValue
    }

    /**
     * Convenience wrapper which checks whether the given fix is a map, and if so returns the value
     * stored by its key
     */
    @JvmStatic
    fun getBoolean(fix: LintFix?, key: String, defaultValue: Boolean): Boolean {
      return if (fix is DataMap) {
        fix.getBoolean(key, defaultValue)
      } else defaultValue
    }

    /**
     * Convenience wrapper which checks whether the given fix is a map, and if so returns the value
     * stored by its key
     */
    @JvmStatic
    fun getMethod(fix: LintFix?, key: String): PsiMethod? {
      return if (fix is DataMap) {
        fix.getMethod(key)
      } else null
    }

    /**
     * Creates a copy of the given location range which only holds on to the starting and ending
     * offsets, to help reduce active memory usage in the IDE; see b/151240516
     */
    private fun extractOffsets(range: Location): Location {
      val start = range.start
      val end = range.end
      return if (start != null && end != null) {
        Location.create(
          range.file,
          DefaultPosition(-1, -1, start.offset),
          DefaultPosition(-1, -1, end.offset),
        )
      } else {
        val pos = DefaultPosition(-1, -1, 0)
        Location.create(range.file, pos, pos)
      }
    }
  }
}
