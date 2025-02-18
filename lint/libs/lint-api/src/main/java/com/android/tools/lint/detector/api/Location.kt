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

import com.android.ide.common.blame.SourcePosition
import com.android.ide.common.resources.ResourceItem
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.client.api.UastParser
import com.android.tools.lint.detector.api.Issue.IgnoredIdProvider
import com.android.utils.CharSequences.indexOf
import com.android.utils.CharSequences.lastIndexOf
import com.android.utils.CharSequences.startsWith
import java.io.File
import kotlin.math.max
import kotlin.math.min
import org.jetbrains.uast.UIdentifier
import org.jetbrains.uast.UMethod
import org.w3c.dom.Node

/** Location information for a warning */
open class Location
/**
 * (Private constructor, use one of the factory methods [Location.create], [Location.create], or
 * [Location.create].)
 *
 * Constructs a new location range for the given file, from start to end. If the length of the range
 * is not known, end may be null.
 *
 * @param file the associated file (but see the documentation for [Location.file] for more
 *   information on what the file represents)
 * @param start the starting position, or null
 * @param end the ending position, or null
 */
protected constructor(
  /**
   * Returns the file containing the warning. Note that the file *itself* may not yet contain the
   * error. When editing a file in the IDE for example, the tool could generate warnings in the
   * background even before the document is saved. However, the file is used as a identifying token
   * for the document being edited, and the IDE integration can map this back to error locations in
   * the editor source code.
   *
   * @return the file handle for the location
   */
  val file: File,
  /**
   * The start position of the range
   *
   * @return the start position of the range, or null
   */
  val start: Position?,
  /**
   * The end position of the range
   *
   * @return the end position of the range, may be null for an empty range
   */
  val end: Position?,
) {

  /**
   * The custom message for this location, if any. This is typically used for secondary locations,
   * to describe the significance of this alternate location. For example, for a duplicate id
   * warning, the primary location might say "This is a duplicate id", pointing to the second
   * occurrence of id declaration, and then the secondary location could point to the original
   * declaration with the custom message "Originally defined here".
   */
  var message: String? = null
    set(value) {
      field = value
      setSelfExplanatory(false)
    }

  /**
   * Returns the client data associated with this location - an optional field which can be used by
   * the creator of the [Location] to store temporary state associated with the location.
   */
  var clientData: Any? = null

  /**
   * Whether this location should be visible on its own. "Visible" here refers to whether the
   * location is shown in the IDE if the user navigates to the given location.
   *
   * For visible locations, especially those that appear far away from the primary location, it's
   * important that the error message make sense on its own. For example, for duplicate
   * declarations, usually the primary error message says something like "foo has already been
   * defined", and the secondary error message says "previous definition here". In something like a
   * text or HTML report, this makes sense -- you see the "foo has already been defined" error
   * message, and it also reports the locations of the previous error message. But if the secondary
   * error message is visible, the user may encounter that error first, and if that error message
   * just says "previous definition here", that doesn't make a lot of sense.
   *
   * This attribute is ignored for the primary location for an issue (e.g. the location passed to
   * [LintClient.report], and it applies for all the secondary locations linked from that location.
   */
  open var visible = true

  private var selfExplanatory = true

  /** Returns a secondary location associated with this location (if applicable), or null. */
  open var secondary: Location? = null

  /**
   * Returns the source element for this location provided it's of the given type, if applicable.
   */
  open var source: Any? = null

  /**
   * Returns the source element for this location if it was originally requested for a broader range
   * than the actual error. For example, if you ask for the location of a method, we'll return just
   * the range for the method name, but the [originalSource] will point to the [UMethod] itself,
   * instead of the [UIdentifier].
   */
  var originalSource: Any? = null

  /**
   * Sets a secondary location with the given message and returns the current location updated with
   * the given secondary location.
   *
   * @param secondary a secondary location associated with this location
   * @param message a message to be set on the secondary location
   * @param selfExplanatory if true, the message is itself self-explanatory; see
   *   [isSelfExplanatory]}
   * @return current location updated with the secondary location
   */
  @JvmOverloads
  fun withSecondary(
    secondary: Location,
    message: String,
    selfExplanatory: Boolean = false,
  ): Location {
    this.secondary = secondary
    secondary.message = message
    secondary.selfExplanatory = selfExplanatory
    return this
  }

  /** Sets a source (AST element, XML node) associated with this location. */
  fun withSource(source: Any): Location {
    this.source = source
    return this
  }

  /** Sets an original source (AST element, XML node) associated with this location. */
  fun withOriginalSource(source: Any): Location {
    this.originalSource = source
    return this
  }

  /**
   * Returns the source element for this location provided it's of the given type, if applicable
   *
   * @param clz the type of the source
   * @return the source element or null
   */
  fun <T> getSource(clz: Class<T>): T? {
    val source = source ?: return null
    if (clz.isAssignableFrom(source.javaClass)) {
      @Suppress("UNCHECKED_CAST") return source as T?
    }

    return null
  }

  /**
   * Sets the source element applicable for this location, if any
   *
   * @param source the source
   * @return this, for constructor chaining
   */
  fun setSource(source: Any?): Location {
    this.source = source
    return this
  }

  /**
   * Sets a custom message for this location. This is typically used for secondary locations, to
   * describe the significance of this alternate location. For example, for a duplicate id warning,
   * the primary location might say "This is a duplicate id", pointing to the second occurrence of
   * id declaration, and then the secondary location could point to the original declaration with
   * the custom message "Originally defined here".
   *
   * @param message the message to apply to this location
   * @param selfExplanatory if true, the message is itself self-explanatory; if false, it's just
   *   describing this particular location and the primary error message is necessary. Controls
   *   whether (for example) the IDE will include the original error message along with this
   *   location when showing the message.
   * @return this, for constructor chaining
   */
  open fun setMessage(message: String, selfExplanatory: Boolean): Location {
    this.message = message
    setSelfExplanatory(selfExplanatory)
    return this
  }

  /**
   * Whether this message is self-explanatory. If false, it's just describing this particular
   * location and the primary error message is necessary. Controls whether (for example) the IDE
   * will include the original error message along with this location when showing the message.
   *
   * @return whether this message is self explanatory.
   */
  fun isSelfExplanatory(): Boolean = selfExplanatory

  /**
   * Sets whether this message is self-explanatory. See [isSelfExplanatory].
   *
   * @param selfExplanatory whether this message is self explanatory.
   * @return this, for constructor chaining
   */
  open fun setSelfExplanatory(selfExplanatory: Boolean): Location {
    this.selfExplanatory = selfExplanatory
    return this
  }

  /**
   * Sets the client data associated with this location. This is an optional field which can be used
   * by the creator of the [Location] to store temporary state associated with the location.
   *
   * @param clientData the data to store with this location
   * @return this, for constructor chaining
   */
  open fun setData(clientData: Any?): Location {
    this.clientData = clientData
    return this
  }

  /** Returns true if this location fits on a single line. */
  fun isSingleLine(): Boolean {
    return start == null || end == null || start.sameLine(end)
  }

  /** Returns true if the other location is within the range of this location. */
  operator fun contains(other: Location): Boolean {
    val thisStart = this.start ?: DefaultPosition(0, 0, 0)
    val otherStart = other.start ?: DefaultPosition(0, 0, 0)
    val thisEnd = this.end ?: DefaultPosition(Int.MAX_VALUE, 0, Int.MAX_VALUE)
    val otherEnd = other.end ?: DefaultPosition(Int.MAX_VALUE, 0, Int.MAX_VALUE)
    return thisStart <= otherStart && thisEnd >= otherEnd
  }

  override fun toString(): String =
    "Location [file=${file.name}, start=$start, end=$end, message=$message]"

  /**
   * A [Handle] is a reference to a location. The point of a location handle is to be able to create
   * them cheaply, and then resolve them into actual locations later (if needed). This makes it
   * possible to for example delay looking up line numbers, for locations that are offset based.
   */
  interface Handle {
    /** Computes a full location for the given handle. */
    fun resolve(): Location

    /**
     * Checks whether there is a suppress directive (`tools:ignore`) for the given resource item.
     */
    fun isSuppressed(driver: LintDriver, issue: Issue): Boolean {
      val location = resolve()
      val source = location.source as? Node
      return driver.isSuppressed(null, issue, source)
    }

    /**
     * The client data associated with this location - an optional field which can be used by the
     * creator of the [Location] to store temporary state associated with the location.
     */
    var clientData: Any?
  }

  /** A default [Handle] implementation for simple file offsets. */
  class DefaultLocationHandle
  /**
   * Constructs a new [DefaultLocationHandle]
   *
   * @param context the context pointing to the file and its contents
   * @param startOffset the start offset within the file
   * @param endOffset the end offset within the file
   */
  (context: Context, private val startOffset: Int, private val endOffset: Int) : Handle {
    private val file: File = context.file
    private val contents: CharSequence = context.getContents() ?: ""
    override var clientData: Any? = null

    override fun resolve(): Location = create(file, contents, startOffset, endOffset)
  }

  open class ResourceItemHandle(
    protected val client: LintClient,
    val item: ResourceItem,
    protected val nameOnly: Boolean,
    protected val valueOnly: Boolean,
  ) : Handle {
    override fun resolve(): Location {
      if (item is LocationAware) {
        val location = item.getLocation()
        if ((nameOnly || valueOnly) && location.file.isFile) {
          // Normally the resource item location includes the whole element range. If
          // we've specifically requested the name or value parts, AND we have access
          // to the file, then try to compute it as in the normal (non-partial analysis)
          // case. (Fall through.)
        } else {
          return location
        }
      }
      val parser = client.xmlParser
      val location =
        when {
          valueOnly -> parser.getValueLocation(client, item)
          nameOnly -> parser.getNameLocation(client, item)
          else -> parser.getLocation(client, item)
        }
      return location ?: (item as? LocationAware)?.getLocation() ?: error(item)
    }

    /** Does this comma separated list contain the given id? */
    private fun String.containsId(id: String): Boolean {
      return this.contains(id) && this.split(",").any { it.trim() == id }
    }

    override fun isSuppressed(driver: LintDriver, issue: Issue): Boolean {
      if (item is IgnoredIdProvider) {
        val ignored = item.getIgnoredIds()
        return issue.getAliases().any { ignored.containsId(it) } || ignored.containsId(issue.id)
      }
      return super.isSuppressed(driver, issue)
    }

    override var clientData: Any?
      get() = null
      set(_) = Unit

    override fun toString(): String {
      return "Handle:$item"
    }
  }

  /**
   * Whether to look forwards, or backwards, or in both directions, when searching for a pattern in
   * the source code to determine the right position range for a given symbol.
   *
   * When dealing with bytecode for example, there are only line number entries within method
   * bodies, so when searching for the method declaration, we should only search backwards from the
   * first line entry in the method.
   */
  enum class SearchDirection {
    /** Only search forwards. */
    FORWARD,

    /** Only search backwards. */
    BACKWARD,

    /**
     * Search backwards from the current end of line (normally it's the beginning of the current
     * line)
     */
    EOL_BACKWARD,

    /**
     * Search both forwards and backwards from the given line, and prefer the match that is closest.
     */
    NEAREST,

    /**
     * Search both forwards and backwards from the end of the given line, and prefer the match that
     * is closest.
     */
    EOL_NEAREST,
  }

  /**
   * Extra information pertaining to finding a symbol in a source buffer, used by [Location.create]
   */
  class SearchHints
  private constructor(
    /** the direction to search for the nearest match in (provided `patternStart` is non null) */
    val direction: SearchDirection
  ) {

    /** Whether the matched pattern should be a whole word. */
    /** @return true if the pattern match should be for whole words only */
    var isWholeWord: Boolean = false
      private set

    /**
     * Whether the matched pattern should be a Java symbol (so for example, a match inside a comment
     * or string literal should not be used)
     */
    var isJavaSymbol: Boolean = false
      private set

    /**
     * Whether the matched pattern corresponds to a constructor; if so, look for some other possible
     * source aliases too, such as "super".
     */
    var isConstructor: Boolean = false
      private set

    /**
     * Indicates that pattern matches should apply to whole words only
     *
     * @return this, for constructor chaining
     */
    fun matchWholeWord(): SearchHints {
      isWholeWord = true

      return this
    }

    /**
     * Indicates that pattern matches should apply to Java symbols only
     *
     * @return this, for constructor chaining
     */
    fun matchJavaSymbol(): SearchHints {
      isJavaSymbol = true
      isWholeWord = true

      return this
    }

    /**
     * Indicates that pattern matches should apply to constructors. If so, look for some other
     * possible source aliases too, such as "super".
     *
     * @return this, for constructor chaining
     */
    fun matchConstructor(): SearchHints {
      isConstructor = true
      isWholeWord = true
      isJavaSymbol = true

      return this
    }

    companion object {

      /**
       * Constructs a new [SearchHints] object
       *
       * @param direction the direction to search in for the pattern
       * @return a new @link SearchHints} object
       */
      @JvmStatic fun create(direction: SearchDirection): SearchHints = SearchHints(direction)
    }
  }

  companion object {
    private const val SUPER_KEYWORD = "super"

    /**
     * Special marker location which means location not available, or not applicable, or filtered
     * out, etc. For example, the infrastructure may return [NONE] if you ask
     * [UastParser.getLocation] for an element which is not in the current file during an
     * incremental lint run in a single file.
     */
    @JvmField
    val NONE: Location =
      object : Location(File("NONE"), null, null) {
        override fun setMessage(message: String, selfExplanatory: Boolean): Location = this

        override fun setData(clientData: Any?): Location = this

        override fun setSelfExplanatory(selfExplanatory: Boolean): Location = this

        @Suppress("UNUSED_PARAMETER")
        override var visible: Boolean = false
          set(value) = Unit

        @Suppress("UNUSED_PARAMETER")
        override var secondary: Location? = null
          set(value) = Unit

        @Suppress("SetterBackingFieldAssignment")
        override var source: Any? = null
          set(_) {
            // Deliberately not allowing the source to be
            // overridden on the shared & static location
          }
      }

    /**
     * Creates a new location for the given file
     *
     * @param file the file to create a location for
     * @return a new location
     */
    @JvmStatic fun create(file: File): Location = Location(file, null, null)

    /**
     * Creates a new location for the given file and SourcePosition.
     *
     * @param file the file containing the positions
     * @param position the source position
     * @return a new location
     */
    @JvmStatic
    fun create(file: File, position: SourcePosition): Location {
      if (position == SourcePosition.UNKNOWN) {
        return Location(file, null, null)
      }
      return Location(
        file,
        DefaultPosition(position.startLine, position.startColumn, position.startOffset),
        DefaultPosition(position.endLine, position.endColumn, position.endOffset),
      )
    }

    /**
     * Creates a new location for the given file and starting and ending positions.
     *
     * @param file the file containing the positions
     * @param start the starting position
     * @param end the ending position
     * @return a new location
     */
    @JvmStatic
    fun create(file: File, start: Position, end: Position?): Location = Location(file, start, end)

    /**
     * Creates a new location for the given file, with the given contents, for the given offset
     * range.
     *
     * @param file the file containing the location
     * @param contents the current contents of the file
     * @param startOffset the starting offset
     * @param endOffset the ending offset
     * @return a new location
     */
    @Suppress("NAME_SHADOWING")
    @JvmStatic
    fun create(file: File, contents: CharSequence?, startOffset: Int, endOffset: Int): Location {
      if (startOffset < 0 || endOffset < startOffset) {
        throw IllegalArgumentException("Invalid offsets")
      }

      if (contents == null) {
        return Location(
          file,
          DefaultPosition(-1, -1, startOffset),
          DefaultPosition(-1, -1, endOffset),
        )
      }

      val size = contents.length
      var startOffset = startOffset
      var endOffset = endOffset
      endOffset = min(endOffset, size)
      startOffset = min(startOffset, endOffset)
      var lineOffset = findLineBeginFromOffset(startOffset, contents)
      var line = findLineFromOffset(lineOffset, contents)
      val start = DefaultPosition(line, startOffset - lineOffset, startOffset)
      for (offset in startOffset..size) {
        if (offset == endOffset) {
          val end = DefaultPosition(line, offset - lineOffset, offset)
          return Location(file, start, end)
        }
        val c = contents[offset]
        if (c == '\n') {
          lineOffset = offset + 1
          line++
        }
      }
      val end = DefaultPosition(line, size - lineOffset, size)
      return Location(file, start, end)
    }

    /**
     * Creates a new location for the given file, with the given contents, for the given line
     * number.
     *
     * @param file the file containing the location
     * @param contents the current contents of the file
     * @param line the line number (0-based) for the position
     * @return a new location
     */
    @JvmStatic
    fun create(file: File, contents: String, line: Int): Location =
      create(file, contents, line, null, null, null)

    /**
     * Creates a new location for the given file, with the given contents, for the given line
     * number.
     *
     * @param file the file containing the location
     * @param contents the current contents of the file
     * @param line the line number (0-based) for the position
     * @param patternStart an optional pattern to search for from the line match; if found, adjust
     *   the column and offsets to begin at the pattern start
     * @param patternEnd an optional pattern to search for behind the start pattern; if found,
     *   adjust the end offset to match the end of the pattern
     * @param hints optional additional information regarding the pattern search
     * @return a new location
     */
    @JvmStatic
    fun create(
      file: File,
      contents: CharSequence,
      line: Int,
      patternStart: String?,
      patternEnd: String?,
      hints: SearchHints?,
    ): Location {

      var targetLine = line
      var targetPattern = patternStart
      var offset = findLineOffset(targetLine, contents)
      if (offset == -1) {
        return create(file)
      }

      if (targetPattern != null) {
        var direction = SearchDirection.NEAREST
        if (hints != null) {
          direction = hints.direction
        }

        val index: Int
        if (direction == SearchDirection.BACKWARD) {
          index = findPreviousMatch(contents, offset, targetPattern, hints)
          targetLine = adjustLine(contents, targetLine, offset, index)
        } else if (direction == SearchDirection.EOL_BACKWARD) {
          var lineEnd = indexOf(contents, '\n', offset)
          if (lineEnd == -1) {
            lineEnd = contents.length
          }

          index = findPreviousMatch(contents, lineEnd, targetPattern, hints)
          targetLine = adjustLine(contents, targetLine, offset, index)
        } else if (direction == SearchDirection.FORWARD) {
          index = findNextMatch(contents, offset, targetPattern, hints)
          targetLine = adjustLine(contents, targetLine, offset, index)
        } else {
          assert(direction == SearchDirection.NEAREST || direction == SearchDirection.EOL_NEAREST)

          var lineEnd = indexOf(contents, '\n', offset)
          if (lineEnd == -1) {
            lineEnd = contents.length
          }
          offset = lineEnd

          val before = findPreviousMatch(contents, offset, targetPattern, hints)
          val after = findNextMatch(contents, offset, targetPattern, hints)

          if (before == -1) {
            index = after
            targetLine = adjustLine(contents, targetLine, offset, index)
          } else if (after == -1) {
            index = before
            targetLine = adjustLine(contents, targetLine, offset, index)
          } else {
            var newLinesBefore = 0
            for (i in before until offset) {
              if (contents[i] == '\n') {
                newLinesBefore++
              }
            }
            var newLinesAfter = 0
            for (i in offset until after) {
              if (contents[i] == '\n') {
                newLinesAfter++
              }
            }
            if (
              newLinesBefore < newLinesAfter ||
                newLinesBefore == newLinesAfter && offset - before < after - offset
            ) {
              index = before
              targetLine = adjustLine(contents, targetLine, offset, index)
            } else {
              index = after
              targetLine = adjustLine(contents, targetLine, offset, index)
            }
          }
        }

        if (index != -1) {
          var lineStart = contents.lastIndexOf('\n', index)
          if (lineStart == -1) {
            lineStart = 0
          } else {
            lineStart++ // was pointing to the previous line's CR, not line start
          }
          val column = index - lineStart
          if (patternEnd != null) {
            val end = indexOf(contents, patternEnd, offset + targetPattern.length)
            if (end != -1) {
              return Location(
                file,
                DefaultPosition(targetLine, column, index),
                DefaultPosition(targetLine, -1, end + patternEnd.length),
              )
            }
          } else if (hints != null && (hints.isJavaSymbol || hints.isWholeWord)) {
            if (hints.isConstructor && startsWith(contents, SUPER_KEYWORD, index)) {
              targetPattern = SUPER_KEYWORD
            }
            return Location(
              file,
              DefaultPosition(targetLine, column, index),
              DefaultPosition(
                targetLine,
                column + targetPattern.length,
                index + targetPattern.length,
              ),
            )
          }
          return Location(
            file,
            DefaultPosition(targetLine, column, index),
            DefaultPosition(targetLine, column, index + targetPattern.length),
          )
        }
      }

      val position = DefaultPosition(targetLine, -1, offset)
      return Location(file, position, position)
    }

    @JvmStatic
    private fun findPreviousMatch(
      contents: CharSequence,
      offset: Int,
      pattern: String,
      hints: SearchHints?,
    ): Int {
      var currentOffset = offset
      val loopDecrement = max(1, pattern.length)
      while (true) {
        val index = lastIndexOf(contents, pattern, currentOffset)
        if (index == -1) {
          return -1
        } else {
          if (isMatch(contents, index, pattern, hints)) {
            return index
          } else {
            currentOffset = index - loopDecrement
          }
        }
      }
    }

    @JvmStatic
    private fun findNextMatch(
      contents: CharSequence,
      offset: Int,
      pattern: String,
      hints: SearchHints?,
    ): Int {
      var currentOffset = offset
      var constructorIndex = -1
      if (hints != null && hints.isConstructor) {
        // Special condition: See if the call is referenced as "super" instead.
        assert(hints.isWholeWord)
        val index = indexOf(contents, SUPER_KEYWORD, currentOffset)
        if (index != -1 && isMatch(contents, index, SUPER_KEYWORD, hints)) {
          constructorIndex = index
        }
      }

      val loopIncrement = max(1, pattern.length)
      while (true) {
        val index = indexOf(contents, pattern, currentOffset)
        if (index == -1 || index == contents.length) {
          return constructorIndex
        } else {
          if (isMatch(contents, index, pattern, hints)) {
            if (constructorIndex != -1) {
              return min(constructorIndex, index)
            }
            return index
          } else {
            currentOffset = index + loopIncrement
          }
        }
      }
    }

    @JvmStatic
    private fun isMatch(
      contents: CharSequence,
      offset: Int,
      pattern: String,
      hints: SearchHints?,
    ): Boolean {
      if (!startsWith(contents, pattern, offset)) {
        return false
      }

      if (hints != null) {
        val prevChar: Char = if (offset > 0) contents[offset - 1] else '\n'
        val lastIndex = offset + pattern.length - 1
        val nextChar: Char = if (lastIndex < contents.length - 1) contents[lastIndex + 1] else '\n'

        if (hints.isWholeWord && (Character.isLetter(prevChar) || Character.isLetter(nextChar))) {
          return false
        }

        if (hints.isJavaSymbol) {
          if (
            Character.isJavaIdentifierPart(prevChar) || Character.isJavaIdentifierPart(nextChar)
          ) {
            return false
          }

          if (prevChar == '"') {
            return false
          }

          // TODO: Additional validation to see if we're in a comment, string, etc.
          // This will require lexing from the beginning of the buffer.
        }

        if (hints.isConstructor && SUPER_KEYWORD == pattern) {
          // Only looking for super(), not super.x, so assert that the next
          // non-space character is (
          var index = lastIndex + 1
          while (index < contents.length - 1) {
            val c = contents[index]
            if (c == '(') {
              break
            } else if (!Character.isWhitespace(c)) {
              return false
            }
            index++
          }
        }
      }

      return true
    }

    @JvmStatic
    private fun adjustLine(doc: CharSequence, line: Int, offset: Int, newOffset: Int): Int {
      if (newOffset == -1) {
        return line
      }

      return if (newOffset < offset) {
        line - countLines(doc, newOffset, offset)
      } else {
        line + countLines(doc, offset, newOffset)
      }
    }

    @JvmStatic
    private fun countLines(doc: CharSequence, start: Int, end: Int): Int {
      var lines = 0
      for (offset in start until end) {
        val c = doc[offset]
        if (c == '\n') {
          lines++
        }
      }

      return lines
    }

    /**
     * Reverses the secondary location list initiated by the given location
     *
     * @param location the first location in the list
     * @return the first location in the reversed list
     */
    @JvmStatic
    fun reverse(location: Location): Location {
      var currentLocation = location
      var next = currentLocation.secondary
      currentLocation.secondary = null
      while (next != null) {
        val nextNext = next.secondary
        next.secondary = currentLocation
        currentLocation = next
        next = nextNext
      }

      return currentLocation
    }

    /** Returns the offset of the first character on the given line. */
    private fun findLineBeginFromOffset(offset: Int, contents: CharSequence): Int {
      var i = offset - 1
      while (i >= 0) {
        if (contents[i] == '\n') {
          return i + 1
        }
        i--
      }
      return 0
    }

    private fun findLineOffset(targetLine: Int, contents: CharSequence): Int {
      var currentLine = 0
      var offset = 0

      while (currentLine < targetLine) {
        offset = indexOf(contents, '\n', offset)
        if (offset == -1) {
          return -1
        }
        currentLine++
        offset++
      }

      return offset
    }

    private fun findLineFromOffset(startOffset: Int, contents: CharSequence): Int {
      val size = contents.length
      val target = min(startOffset, size)
      var line = 0

      for (offset in 0..size) {
        if (offset == target) {
          return line
        }
        val c = contents[offset]
        if (c == '\n') {
          line++
        }
      }

      return line
    }
  }

  /** Interface implemented by classes that can provide a related location. */
  interface LocationAware {
    fun getLocation(): Location
  }
}
