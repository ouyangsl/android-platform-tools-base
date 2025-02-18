/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.ide.common.rendering.api.ArrayResourceValue
import com.android.ide.common.rendering.api.AttrResourceValue
import com.android.ide.common.rendering.api.DensityBasedResourceValue
import com.android.ide.common.rendering.api.PluralsResourceValue
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.rendering.api.StyleItemResourceValue
import com.android.ide.common.rendering.api.StyleResourceValue
import com.android.ide.common.rendering.api.StyleableResourceValue
import com.android.ide.common.rendering.api.TextResourceValue
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceRepository
import com.android.resources.ResourceType
import com.android.testutils.TestUtils
import com.android.tools.lint.checks.infrastructure.ProjectDescription
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.image
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestFiles.manifest
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.lint.checks.infrastructure.TestLintClient
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.checks.infrastructure.dos2unix
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.ResourceRepositoryScope
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Issue.IgnoredIdProvider
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.model.PathVariables
import com.intellij.psi.PsiMethod
import java.io.File
import java.util.EnumSet
import java.util.regex.Pattern
import org.jetbrains.uast.UCallExpression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LintResourceRepositoryTest {
  @get:Rule var temporaryFolder = TemporaryFolder()

  @Test
  fun testRepository() {
    checkRepository(
      xml(
          "res/values/test.xml",
          """
                    <resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
                        <string name="string1">String 1</string>
                        <string name="string2">String 2</string>
                        <string name="js_dialog_title" msgid="7464775045615023241">"På siden på \"<xliff:g id="TITLE">%s</xliff:g>\" står der:"</string>
                    </resources>
                    """,
        )
        .indented(),
      xml(
          "res/values-v11/values.xml",
          """
                <resources>
                    <dimen name="activity_horizontal_margin">16dp</dimen>
                </resources>
                """,
        )
        .indented(),
      xml(
        "res/values/styles.xml",
        """
                <resources>
                    <style name="Notification.Header" parent="">
                        <item name="paddingTop">@dimen/notification_header_padding_top</item>
                        <item name="paddingBottom">@dimen/notification_header_padding_bottom</item>
                        <item name="gravity">top</item>
                    </style>
                </resources>
                """,
      ),
      xml(
          "res/values/duplicates.xml",
          """
                <resources>
                    <item type="id" name="name" />
                    <dimen name="activity_horizontal_margin">16dp</dimen>
                    <dimen name="positive">16dp</dimen>
                    <dimen name="negative">-16dp</dimen>

                    <style name="MyStyle" parent="android:Theme.Holo.Light.DarkActionBar">
                        <item name="android:layout_margin">5dp</item>
                        <item name="android:layout_marginLeft">@dimen/positive</item>
                        <item name="android:layout_marginTop">@dimen/negative</item>
                        <item name="android:layout_marginBottom">-5dp</item>
                    </style>

                    <style name="MyStyle.Another">
                        <item name="android:layout_margin">5dp</item>
                    </style>

                    <string-array name="typography">
                        <item>\"Ages 1, 3-5\"</item>
                        <item>Age: 5 1/2+</item>
                    </string-array>
                    <eat-comment/>
                    <declare-styleable name="ContentFrame">
                        <attr name="content" format="reference" />
                        <attr name="contentId" format="reference" />
                        <attr name="windowSoftInputMode">
                            <flag name="stateUnspecified" value="0" />
                            <!-- Leave the soft input window as-is, in whatever state it
                            last was. -->
                            <flag name="stateUnchanged" value="1" />
                        </attr>
                        <attr name="fastScrollOverlayPosition">
                            <enum name="floating" value="0" />
                            <enum name="atThumb" value="1" />
                            <enum name="aboveThumb" value="2" />
                        </attr>
                    </declare-styleable>
                    <plurals name="my_plural">
                        <item quantity="one">@string/hello1</item>
                        <item quantity="few">@string/hello2</item>
                        <item quantity="other">@string/hello3</item>
                    </plurals>
                </resources>
                """,
        )
        .indented(),
      xml(
          "res/layout/activity_main.xml",
          """
                <androidx.constraintlayout.widget.ConstraintLayout
                        xmlns:android="http://schemas.android.com/apk/res/android"
                        xmlns:tools="http://schemas.android.com/tools"
                        xmlns:app="http://schemas.android.com/apk/res-auto"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        tools:context=".MainActivity"/>
                """,
        )
        .indented(),
      image("res/drawable/ic_launcher.png", 48, 48).fill(10, 10, 20, 20, -0xff0001),
      image("res/drawable-xhdpi-v4/ic_launcher2.png", 48, 48).fill(10, 10, 20, 20, -0xff0001),
    ) { name, repository, root ->
      val namespace = ResourceNamespace.TODO()
      assertEquals(
        name,
        "array, attr, dimen, drawable, id, layout, plurals, string, " + "style, styleable",
        repository.getResourceTypes(namespace).sorted().joinToString { it.getName() },
      )

      assertEquals(
        name,
        "js_dialog_title, string1, string2",
        repository.getResourceNames(namespace, ResourceType.STRING).sorted().joinToString { it },
      )

      assertEquals(
        "namespace:apk/res-auto\n" +
          "  @array/typography (value) config=default source=/app/res/values/duplicates.xml;  [\"Ages 1, 3-5\",Age: 5 1/2+]\n" +
          "  @attr/content (value) config=default source=/app/res/values/duplicates.xml;  []\n" +
          "  @attr/contentId (value) config=default source=/app/res/values/duplicates.xml;  []\n" +
          "  @attr/fastScrollOverlayPosition (value) config=default source=/app/res/values/duplicates.xml;  [floating=0,atThumb=1,aboveThumb=2]\n" +
          "  @attr/windowSoftInputMode (value) config=default source=/app/res/values/duplicates.xml;  [stateUnspecified=0,stateUnchanged=1]\n" +
          "  @dimen/activity_horizontal_margin (value) config=API 11 source=/app/res/values-v11/values.xml;  16dp\n" +
          "  @dimen/activity_horizontal_margin (value) config=default source=/app/res/values/duplicates.xml;  16dp\n" +
          "  @dimen/negative (value) config=default source=/app/res/values/duplicates.xml;  -16dp\n" +
          "  @dimen/positive (value) config=default source=/app/res/values/duplicates.xml;  16dp\n" +
          "  @drawable/ic_launcher (file) config=default source=/app/res/drawable/ic_launcher.png;  /app/res/drawable/ic_launcher.png\n" +
          "  @drawable/ic_launcher2 (file) config=X-High Density,API 4 source=/app/res/drawable-xhdpi-v4/ic_launcher2.png;  X-High Density\n" +
          "  @id/name (value) config=default source=/app/res/values/duplicates.xml;  \n" +
          "  @layout/activity_main (file) config=default source=/app/res/layout/activity_main.xml;  /app/res/layout/activity_main.xml\n" +
          "  @plurals/my_plural (value) config=default source=/app/res/values/duplicates.xml;  [one=@string/hello1,few=@string/hello2,other=@string/hello3]\n" +
          "  @string/js_dialog_title (value) config=default source=/app/res/values/test.xml;  På siden på \"\${TITLE}\" står der: raw:\"På siden på \\\"<xliff:g id=\"TITLE\">%s</xliff:g>\\\" står der:\"\n"
            .dos2unix() +
          "  @string/string1 (value) config=default source=/app/res/values/test.xml;  String 1\n" +
          "  @string/string2 (value) config=default source=/app/res/values/test.xml;  String 2\n" +
          "  @style/MyStyle (value) config=default source=/app/res/values/duplicates.xml;  parent=ResourceReference{namespace=apk/res/android, type=style, name=Theme.Holo.Light.DarkActionBar} [android:layout_margin,android:layout_marginLeft,android:layout_marginTop,android:layout_marginBottom]\n" +
          "  @style/MyStyle.Another (value) config=default source=/app/res/values/duplicates.xml;  parent=ResourceReference{namespace=apk/res-auto, type=style, name=MyStyle} [android:layout_margin]\n" +
          "  @style/Notification.Header (value) config=default source=/app/res/values/styles.xml;  parent=null [paddingTop,paddingBottom,gravity]\n" +
          "  @styleable/ContentFrame (value) config=default source=/app/res/values/duplicates.xml;  {[][][stateUnspecified=0,stateUnchanged=1][floating=0,atThumb=1,aboveThumb=2]}\n",
        repository.prettyPrint(root).dos2unix(),
      )

      fun indexInEscaped(s: String, char: Char, from: Int = 0): Int {
        var i = from
        val n = s.length
        while (i < n) {
          when (s[i]) {
            char -> return i
            '\\' -> i += 2
            else -> i++
          }
        }
        return -1
      }

      // Check persistence format

      if (repository is LintResourceRepository) {
        fun format(s: String): String {
          val sb = StringBuilder(s.length)
          var i = indexInEscaped(s, '+', 0)
          assertNotEquals(-1, i) // this method doesn't work right on empty
          sb.append(s.substring(0, i).split(",").joinToString(separator = "\n"))
          val n = s.length
          while (i < n) {
            val end = indexInEscaped(s, '+', i + 1)
            sb.append(s.substring(i, if (end == -1) n else end))
            sb.append('\n')
            if (end == -1) {
              break
            }
            i = end
          }

          return sb.toString()
        }

        // See [LintResourcePersistenceTest]; including it here since it's a more
        // complex set of resources.
        val expected =
          "http://schemas.android.com/apk/res-auto;;${"$"}ROOT/app/res/values/duplicates.xml\n" +
            "${"$"}ROOT/app/res/values-v11/values.xml\n" +
            "${"$"}ROOT/app/res/drawable-xhdpi-v4/ic_launcher2.png\n" +
            "${"$"}ROOT/app/res/drawable/ic_launcher.png\n" +
            "${"$"}ROOT/app/res/layout/activity_main.xml\n" +
            "${"$"}ROOT/app/res/values/test.xml\n" +
            "${"$"}ROOT/app/res/values/styles.xml\n" +
            "+array:typography,0,V40011027d,13001402f7,;\\\"Ages 1\\, 3-5\\\",Age\\: 5 1/2\\+,;\n" +
            "+attr:content,0,V80017033f,3200170369,;reference:;contentId,0,V800180372,340018039e,;reference:;fastScrollOverlayPosition,0,V8001f04b0,f00230575,;enum:floating:0,atThumb:1,aboveThumb:2,;windowSoftInputMode,0,V8001903a7,f001e04a7,;flags:stateUnspecified:0,stateUnchanged:1,;\n" +
            "+dimen:activity_horizontal_margin,0,V400020033,3900020068,;\"16dp\";activity_horizontal_margin,1,V400010010,3900010045,;\"16dp\";negative,0,V400040095,28000400b9,;\"-16dp\";positive,0,V40003006d,2700030090,;\"16dp\";\n" +
            "+drawable:ic_launcher,3,F;ic_launcher2,2,F;\n" +
            "+id:name,0,V400010010,220001002e,;\"\";\n" +
            "+layout:activity_main,4,F;\n" +
            "+plurals:my_plural,0,V400250593,e00290657,;one:@string/hello1,few:@string/hello2,other:@string/hello3,;\n" +
            "+string:js_dialog_title,5,V40003009e,840003011e,;\"På siden på \\\"\${TITLE}\\\" står der\\:\"\\\"På siden på \\\\\\\"<xliff\\:g id=\\\"TITLE\\\">%s</xliff\\:g>\\\\\\\" står der\\:\\\";string1,5,V400010044,2c0001006c,;\"String 1\";string2,5,V400020071,2c00020099,;\"String 2\";\n" +
            "+style:MyStyle,0,V4000600bf,c000b0210,;Dandroid\\:Theme.Holo.Light.DarkActionBar,android\\:layout_margin:5dp,android\\:layout_marginLeft:@dimen/positive,android\\:layout_marginTop:@dimen/negative,android\\:layout_marginBottom:-5dp,;MyStyle.Another,0,V4000d0216,c000f0277,;Nandroid\\:layout_margin:5dp,;Notification.Header,6,V1400020031,1c00060174,;EpaddingTop:@dimen/notification_header_padding_top,paddingBottom:@dimen/notification_header_padding_bottom,gravity:top,;\n" +
            "+styleable:ContentFrame,0,V40016030f,180024058e,;-content:reference:-contentId:reference:-windowSoftInputMode:flags:stateUnspecified:0,stateUnchanged:1,-fastScrollOverlayPosition:enum:floating:0,atThumb:1,aboveThumb:2,;\n"
        val actual = serialize(repository)
        assertEquals(expected, format(actual))
        val reserialized = serialize(deserialize(actual) as LintResourceRepository)
        assertEquals(expected, format(reserialized))
      }

      val findByActual = repository.getResources(namespace, ResourceType.STYLE, "MyStyle.Another")
      val findByField = repository.getResources(namespace, ResourceType.STYLE, "MyStyle_Another")
      assertEquals("style/MyStyle.Another", findByActual.joinToString { "${it.type}/${it.name}" })
      assertEquals("[]", findByField.toString())

      // Check details about resource values
      checkArrays(repository, namespace)
      checkPlurals(repository, namespace)
      checkStyles(repository, namespace)
      checkAttrs(repository, namespace)
      checkStrings(repository, namespace)
      checkStyleable(repository, namespace)
      checkDensity(repository, namespace, root)
    }
  }

  private fun checkArrays(repository: ResourceRepository, namespace: ResourceNamespace) {
    // Arrays
    val arrays =
      repository.getResources(ResourceReference(namespace, ResourceType.ARRAY, "typography"))
    assertEquals(1, arrays.size)
    val array = arrays.first()
    val arrayValue = array.resourceValue as ArrayResourceValue
    val arrayDescription = StringBuilder()
    for (i in 0 until arrayValue.elementCount) {
      val value = arrayValue.getElement(i)
      arrayDescription.append(value)
      arrayDescription.append("\n")
    }
    assertEquals("" + "\"Ages 1, 3-5\"\n" + "Age: 5 1/2+\n", arrayDescription.toString())
  }

  private fun checkPlurals(repository: ResourceRepository, namespace: ResourceNamespace) {
    // Plurals
    val plurals =
      repository.getResources(ResourceReference(namespace, ResourceType.PLURALS, "my_plural"))
    assertEquals(1, plurals.size)
    val plural = plurals.first()
    val pluralValue = plural.resourceValue as PluralsResourceValue
    val pluralDescription = StringBuilder()
    for (i in 0 until pluralValue.pluralsCount) {
      val quantity = pluralValue.getQuantity(i)
      val value = pluralValue.getValue(i)
      pluralDescription.append(quantity)
      pluralDescription.append(':')
      pluralDescription.append(value)
      pluralDescription.append("\n")
    }
    assertEquals(
      "" + "one:@string/hello1\n" + "few:@string/hello2\n" + "other:@string/hello3\n",
      pluralDescription.toString(),
    )

    // Lookup by quantity name
    assertEquals("@string/hello2", pluralValue.getValue("few"))
    // not defined:
    assertNull("", pluralValue.getValue("zero"))
  }

  private fun checkStyles(repository: ResourceRepository, namespace: ResourceNamespace) {
    val styles = repository.getResources(namespace, ResourceType.STYLE, "MyStyle")
    assertEquals(1, styles.size)
    val style = styles.first()
    val styleValue = style.resourceValue as StyleResourceValue
    assertEquals("android:Theme.Holo.Light.DarkActionBar", styleValue.parentStyleName)
    val styleDescription = StringBuilder()
    for (styleItem in styleValue.definedItems) {
      styleDescription.append(styleItem.attrName)
      styleDescription.append("\n")
    }
    assertEquals(
      "" +
        "android:layout_margin\n" +
        "android:layout_marginLeft\n" +
        "android:layout_marginTop\n" +
        "android:layout_marginBottom\n",
      styleDescription.toString(),
    )
  }

  private fun checkStrings(repository: ResourceRepository, namespace: ResourceNamespace) {
    val strings = repository.getResources(namespace, ResourceType.STRING, "string1")
    assertEquals(1, strings.size)
    val string = strings.first()
    val stringValue = string.resourceValue as TextResourceValue
    assertEquals("String 1", stringValue.value)
    assertEquals("String 1", stringValue.rawXmlValue)

    assertEquals("values/test.xml", TestLintClient().getDisplayPath(string))
  }

  private fun checkAttrs(repository: ResourceRepository, namespace: ResourceNamespace) {
    // Attrs without children
    val attrs1 = repository.getResources(namespace, ResourceType.ATTR, "contentId")
    assertEquals(1, attrs1.size)
    // String: copying since just substrings from total string
    val attr1 = attrs1.first()
    val value1 = attr1.resourceValue as AttrResourceValue
    val desc1 = StringBuilder()
    desc1.append("${value1.name}:${value1.formats.toSortedSet()}\n")
    for ((k, v) in value1.attributeValues.toSortedMap()) {
      desc1.append("  $k:$v\n")
    }
    assertEquals("contentId:[REFERENCE]\n", desc1.toString())

    // Attrs without children
    val attrs2 = repository.getResources(namespace, ResourceType.ATTR, "windowSoftInputMode")
    assertEquals(1, attrs2.size)
    // String: copying since just substrings from total string
    val attr2 = attrs2.first()
    val value2 = attr2.resourceValue as AttrResourceValue
    val desc2 = StringBuilder()
    desc2.append("${value2.name}:${value2.formats.toSortedSet()}\n")
    for ((k, v) in value2.attributeValues.toSortedMap()) {
      desc2.append("  $k:$v\n")
    }
    assertEquals(
      "" + "windowSoftInputMode:[FLAGS]\n" + "  stateUnchanged:1\n" + "  stateUnspecified:0\n",
      desc2.toString(),
    )
  }

  private fun checkStyleable(repository: ResourceRepository, namespace: ResourceNamespace) {
    val attrs = repository.getResources(namespace, ResourceType.STYLEABLE, "ContentFrame")
    assertEquals(1, attrs.size)
    // String: copying since just substrings from total string
    val attr = attrs.first()
    val styleValue = attr.resourceValue as StyleableResourceValue
    val styleDescription = StringBuilder()
    styleValue.allAttributes
      .sortedBy { it.name }
      .forEach { a ->
        styleDescription.append(
          "${a.name}:${a.value}:${a.formats.toSortedSet()}:${a.attributeValues.toSortedMap()}\n"
        )
      }

    assertEquals(
      "" +
        "content:null:[REFERENCE]:{}\n" +
        "contentId:null:[REFERENCE]:{}\n" +
        "fastScrollOverlayPosition:null:[ENUM]:{aboveThumb=2, atThumb=1, floating=0}\n" +
        "windowSoftInputMode:null:[FLAGS]:{stateUnchanged=1, stateUnspecified=0}\n",
      styleDescription.toString(),
    )
  }

  private fun checkDensity(
    repository: ResourceRepository,
    namespace: ResourceNamespace,
    root: File,
  ) {
    val drawables = repository.getResources(namespace, ResourceType.DRAWABLE, "ic_launcher2")
    assertEquals(1, drawables.size)
    // String: copying since just substrings from total string
    val drawable = drawables.first()
    val densityValue = drawable.resourceValue as DensityBasedResourceValue
    val description = StringBuilder()
    description.append(
      "${drawable.type.displayName}/${drawable.name}: ${densityValue.resourceDensity}: ${densityValue.value}"
    )
    assertEquals(
      "Drawable/ic_launcher2: X-High Density: /app/res/drawable-xhdpi-v4/ic_launcher2.png",
      description.toString().replace(root.path, "").dos2unix(),
    )

    // For file based resources the value is the path; make sure we
    // handle path normalization correct
    assertTrue(File(drawable.resourceValue!!.value!!).exists())

    // For non-density file based resources, should not get a density based resource value
    val nonDensityDrawables =
      repository.getResources(namespace, ResourceType.DRAWABLE, "ic_launcher")
    assertEquals(1, nonDensityDrawables.size)
    val nonDensityDrawable = nonDensityDrawables.first()
    assertTrue(nonDensityDrawable !is DensityBasedResourceValue)

    // For file based resources the value is the path; make sure we
    // handle path normalization correct
    assertTrue(File(nonDensityDrawable.resourceValue!!.value!!).exists())
  }

  private fun checkRepository(
    vararg files: TestFile,
    includeAgpRepository: Boolean = true,
    assertions: (String, ResourceRepository, File) -> Unit,
  ) {
    val root = temporaryFolder.root
    val desc = ProjectDescription(*files).name("app")
    val dir = TestLintTask().projects(desc).createProjects(root).first()
    val res = File(dir, "res")

    val client = LintCliClient(LintClient.CLIENT_UNIT_TESTS)
    val standardRepo =
      if (includeAgpRepository)
        TestLintClient.getResources(
          ResourceNamespace.RES_AUTO,
          null,
          listOf(Pair("app", listOf(res))),
          true,
        )
      else null

    val lintRepo =
      LintResourceRepository.createFromFolder(
        client,
        sequenceOf(res),
        null,
        null,
        ResourceNamespace.TODO(),
      )

    for (pair in
      sequenceOf(
        if (standardRepo != null)
          Pair("Backed by XML (using AGP resource repositories)", standardRepo)
        else null,
        Pair("Backed by serialization", deserialize(serialize(lintRepo))),
        Pair("Backed by XML (using lint's folder processor)", lintRepo),
      )) {
      pair ?: continue
      assertions(pair.first, pair.second, root)
    }
  }

  private fun serialize(repository: LintResourceRepository): String {
    val pathVariables = getPathVariables()
    return repository.serialize(pathVariables, temporaryFolder.root, sort = true)
  }

  private fun deserialize(s: String): ResourceRepository {
    return LintResourcePersistence.deserialize(s, getPathVariables(), temporaryFolder.root, null)
  }

  private fun getPathVariables(): PathVariables {
    val pathVariables = PathVariables()
    pathVariables.add("ROOT", temporaryFolder.root)
    return pathVariables
  }

  @Test
  fun testEmptyAndHiddenFiles() {
    checkRepository(
      xml(
          "res/values/test.xml",
          """
                    <resources>
                        <string name="string1">String 1</string>
                    </resources>
                    """,
        )
        .indented(),
      xml(
          "res/values/.ignore.xml",
          """
                    <resources>
                        <string name="ignore">Ignore</string>
                    </resources>
                    """,
        )
        .indented(),
      xml("res/values/empty.xml", ""),
      includeAgpRepository = false,
    ) { _, repository, root ->
      assertEquals(
        "namespace:apk/res-auto\n" +
          "  @string/string1 (value) config=default source=/app/res/values/test.xml;  String 1\n",
        repository.prettyPrint(root).dos2unix(),
      )
    }
  }

  @Test
  fun testLocation() {
    checkRepository(
      xml(
          "res/values/test.xml",
          """
        <resources>
            <!-- PREFIX --><string name="string1">String 1</string><!-- SUFFIX -->
        </resources>
        """,
        )
        .indented(),
      includeAgpRepository = false,
    ) { _, repository, root ->
      assertEquals(
        "namespace:apk/res-auto\n" +
          "  @string/string1 (value) config=default source=/app/res/values/test.xml;  String 1\n",
        repository.prettyPrint(root).dos2unix(),
      )

      if (repository is LintResourceRepository) {
        val client = LintCliClient(LintClient.CLIENT_UNIT_TESTS)
        val parser = client.xmlParser
        val item =
          repository.getResources(ResourceNamespace.TODO(), ResourceType.STRING, "string1").single()

        val location = parser.getLocation(client, item)
        location!!
        val codeWithLocation = location.getErrorLines(textProvider = { client.getSourceText(it) })!!
        assertEquals(
          """
          <!-- PREFIX --><string name="string1">String 1</string><!-- SUFFIX -->
                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          """
            .trimIndent(),
          codeWithLocation.trimIndent(),
        )
      }
    }
  }

  @Test
  fun testIgnore() {
    checkRepository(
      xml(
          "res/values/test.xml",
          """
        <resources xmlns:tools="http://schemas.android.com/tools" tools:ignore="SdCardPath, DuplicateResources">
            <string name="string1" tools:ignore="DuplicateString">String 1</string>
            <string name="string2">String 2</string>
        </resources>
        """,
        )
        .indented(),
      includeAgpRepository = false,
    ) { _, repository, root ->
      assertEquals(
        """
        namespace:apk/res-auto
          @string/string1 (value) config=default source=/app/res/values/test.xml;  String 1
            (ignores: SdCardPath,DuplicateResources,DuplicateString)
          @string/string2 (value) config=default source=/app/res/values/test.xml;  String 2
            (ignores: SdCardPath,DuplicateResources)
        """
          .trimIndent(),
        repository.prettyPrint(root).dos2unix().trim(),
      )
    }
  }

  @Test
  fun testCheckRecovery() {
    lint()
      .sdkHome(TestUtils.getSdk().toFile())
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">
                    <uses-sdk android:minSdkVersion="14" />
                    <application android:fullBackupContent="@xml/backup">
                        <service
                            android:process="@string/location_process"
                            android:enabled="@bool/enable_wearable_location_service">
                        </service>
                    </application>
                </manifest>
                """
          )
          .indented(),
        xml(
            "res/values/values.xml",
            """
                <resources>
                    <string name="location_process">Location Process</string>
                </resources>
                """,
          )
          .indented(),
        xml(
            "res/values/bools.xml",
            """
                <resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
                    <bool name="enable_wearable_location_service">true</bool>
                </resources>
                """,
          )
          .indented(),
        xml(
            "res/values-en-rUS/values.xml",
            """
                <resources>
                    <string name="location_process">Location Process (English)</string>
                </resources>
                """,
          )
          .indented(),
        xml(
            "res/values-watch/bools.xml",
            """
                <resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
                    <bool name="enable_wearable_location_service">false</bool>
                </resources>
                """,
          )
          .indented(),
        xml(
            "res/xml/backup.xml",
            """
                <full-backup-content>
                     <include domain="file" path="dd"/>
                     <exclude domain="file" path="dd/fo3o.txt"/>
                     <exclude domain="file" path="dd/ss/foo.txt"/>
                </full-backup-content>
                """,
          )
          .indented(),
        xml(
            "res/xml-mcc/backup.xml",
            """
                <full-backup-content>
                     <include domain="file" path="mcc"/>
                </full-backup-content>
                """,
          )
          .indented(),
        kotlin(
            """
                fun test() = TODO()
                """
          )
          .indented(),
      )
      .issues(RepositoryRecoveryDetector.ISSUE)
      .allowAbsolutePathsInMessages(true)
      // This behavior does not apply to the AGP resource repository so would fail
      // TestMode.RESOURCE_REPOSITORIES
      .testModes(TestMode.PARTIAL)
      .run()
      .expectMatches(
        Pattern.quote(
          "build/lint-resources.xml: Warning: Failed to deserialize cached resource repository.\n" +
            "This is an internal lint error which typically means that lint is being passed a\n" +
            "serialized file that was created with an older version of lint or with a different\n" +
            "set of path variable names. Attempting to gracefully recover.\n" +
            "The serialized content was:\n" +
            "mangled2\n" +
            "Stack: java.lang.StringIndexOutOfBoundsException: Index 8 out of bounds for length"
        ) + ".*\\) \\[LintWarning]\n" + "0 errors, 1 warnings"
      )
  }

  /** Detector used by [testCheckRecovery] */
  @SuppressWarnings("ALL")
  class RepositoryRecoveryDetector : Detector(), Detector.UastScanner, Detector.XmlScanner {
    override fun getApplicableMethodNames(): List<String> {
      return listOf("TODO")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
      // Put something in the partial results map so that checkPartialResults is called in the MERGE
      // phase.
      context.getPartialResults(ISSUE).map().put("K", "V")

      val project = context.project
      val client = context.project.client as LintCliClient

      // Write out a corrupt version of the resource repository.
      val mangled = "mangled1"
      val file = client.getSerializationFile(project, XmlFileType.RESOURCE_REPOSITORY)
      file.parentFile?.mkdirs()
      file.writeText(mangled)

      // Get the resource repository. This will NOT read the corrupt file because we are in
      // ANALYZE_ONLY mode, and the resource repository is an output file from this mode. Instead,
      // the resource repository will be created, and the file will be overwritten.
      val repository: ResourceRepository =
        client.getResources(project, ResourceRepositoryScope.PROJECT_ONLY)

      // Check that the resource repository has indeed been overwritten.
      assert(file.readText().length > 100) { "Expected a larger resource repository file" }

      // While we are here, check that the resource repository contents is correct.
      val resources = repository.prettyPrint(project.dir)
      assertEquals(
        """
                namespace:apk/res-auto
                  @bool/enable_wearable_location_service (value) config=Watch,API 20 source=/res/values-watch/bools.xml;  false
                  @bool/enable_wearable_location_service (value) config=default source=/res/values/bools.xml;  true
                  @string/location_process (value) config=default source=/res/values/values.xml;  Location Process
                  @string/location_process (value) config=en,US source=/res/values-en-rUS/values.xml;  Location Process (English)
                  @xml/backup (file) config=default source=/res/xml/backup.xml;  /res/xml/backup.xml
                  @xml/backup (file) config=mcc source=/res/xml-mcc/backup.xml;  /res/xml-mcc/backup.xml

                """
          .trimIndent(),
        resources.dos2unix(),
      )
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
      val project = context.project
      val client = context.project.client as LintCliClient

      val file = client.getSerializationFile(project, XmlFileType.RESOURCE_REPOSITORY)

      // The resource repository should still exist.
      assert(file.readText().length > 100) { "Expected a larger resource repository file" }

      // Write out a corrupt version of the resource repository.
      val mangled = "mangled2"
      file.parentFile?.mkdirs()
      file.writeText(mangled)

      // Try to deserialize the (corrupt) resource repository. This will log an error (which we'll
      // verify from testCheckRecovery) and then recreate the resource repository (which we'll
      // verify by pretty printing the resource repository and checking its contents with
      // assertEquals below.)
      val repository: ResourceRepository =
        client.getResources(project, ResourceRepositoryScope.PROJECT_ONLY)
      val resources = repository.prettyPrint(project.dir)
      assertEquals(
        """
                namespace:apk/res-auto
                  @bool/enable_wearable_location_service (value) config=Watch,API 20 source=/res/values-watch/bools.xml;  false
                  @bool/enable_wearable_location_service (value) config=default source=/res/values/bools.xml;  true
                  @string/location_process (value) config=default source=/res/values/values.xml;  Location Process
                  @string/location_process (value) config=en,US source=/res/values-en-rUS/values.xml;  Location Process (English)
                  @xml/backup (file) config=default source=/res/xml/backup.xml;  /res/xml/backup.xml
                  @xml/backup (file) config=mcc source=/res/xml-mcc/backup.xml;  /res/xml-mcc/backup.xml

                """
          .trimIndent(),
        resources.dos2unix(),
      )
    }

    companion object {
      @JvmField
      val ISSUE =
        Issue.create(
          id = "_ResourceRepositoryRecovery",
          briefDescription = "Lint check for testing out resource recovery",
          explanation =
            "Tests mangling the resource repository and making sure it's manually created",
          category = Category.TESTING,
          priority = 10,
          severity = Severity.WARNING,
          implementation =
            Implementation(RepositoryRecoveryDetector::class.java, EnumSet.of(Scope.JAVA_FILE)),
        )
    }
  }
}

fun ResourceRepository.prettyPrint(root: File? = null): String {
  val sb = StringBuilder(10000)
  prettyPrint(sb, root)
  return sb.toString()
}

fun ResourceRepository.prettyPrint(sb: StringBuilder, root: File? = null): String {
  val namespace = this.namespaces.first()
  sb.append("namespace:").append(namespace).append("\n")
  var seenLibrary = false
  for (type in ResourceType.values().sorted()) {
    val items = this.getResources(namespace, type).values()
    if (items.isEmpty()) {
      continue
    }
    for (item in
      items.sortedWith(compareBy({ it.name }, { it.configuration.toShortDisplayString() }))) {
      if (!seenLibrary) {
        item.libraryName?.let { sb.append("  library: ").append(it).append("\n") }
        seenLibrary = true
      }

      item.prettyPrint(sb, root)
      sb.append("\n")

      val ignoredIds = if (item is IgnoredIdProvider) item.getIgnoredIds() else ""
      if (ignoredIds.isNotEmpty()) {
        sb.append("    (ignores: ").append(ignoredIds).append(")\n")
      }
    }
  }

  return sb.toString()
}

fun ResourceItem.prettyPrint(sb: StringBuilder, root: File? = null) {
  sb.append("  @").append(type.getName()).append("/").append(name)
  if (isFileBased) {
    sb.append(" (file)")
  } else {
    sb.append(" (value)")
  }
  val config = configuration.toShortDisplayString()
  val file = source?.toFile()?.path
  val path = if (root != null && file != null) file.removePrefix(root.path) else file
  sb.append(" config=$config source=$path;  ")

  // Resource value
  resourceValue?.prettyPrint(sb, root) ?: sb.append("null")
}

fun ResourceValue.prettyPrint(sb: StringBuilder, root: File? = null) {
  val value = this
  when (value) {
    is TextResourceValue -> {
      sb.append(value.value)
      if (value.value != value.rawXmlValue) {
        sb.append(" raw:")
        sb.append(value.rawXmlValue)
      }
    }
    is ArrayResourceValue -> {
      sb.append("[")
      for (i in 0 until value.elementCount) {
        if (i > 0) {
          sb.append(",")
        }
        sb.append(value.elementAt(i))
      }
      sb.append("]")
    }
    is PluralsResourceValue -> {
      sb.append("[")
      for (i in 0 until value.pluralsCount) {
        if (i > 0) {
          sb.append(",")
        }
        sb.append(value.getQuantity(i))
        sb.append('=')
        sb.append(value.getValue(i))
      }
      sb.append("]")
    }
    is DensityBasedResourceValue -> {
      sb.append(value.resourceDensity)
    }
    is AttrResourceValue -> {
      val values = value.attributeValues
      // Include formats? sb.append("formats=" + value.formats + ", ")
      sb.append("[")
      var first = true
      for (v in values) {
        if (first) {
          first = false
        } else {
          sb.append(",")
        }
        sb.append(v.key)
        sb.append('=')
        sb.append(v.value)
      }
      sb.append("]")
    }
    is StyleResourceValue -> {
      sb.append("parent=" + value.parentStyle)
      sb.append(" ")
      sb.append("[")
      sb.append(value.definedItems.joinToString(",") { it.attrName })
      sb.append("]")
    }
    is StyleItemResourceValue -> {
      sb.append(value.attrName)
    }
    is StyleableResourceValue -> {
      sb.append("{")
      for (attr in value.allAttributes) {
        val values = attr.attributeValues
        // Include formats? sb.append("formats=" + attr.formats + ", ")
        sb.append("[")
        var first = true
        for (v in values) {
          if (first) {
            first = false
          } else {
            sb.append(",")
          }
          sb.append(v.key)
          sb.append('=')
          sb.append(v.value)
        }
        sb.append("]")
      }
      sb.append("}")
    }
    else -> {
      val v = value.value
      if (v != null && root != null) {
        sb.append(v.removePrefix(root.path))
      } else {
        sb.append(v)
      }
    }
  }
}
