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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_NAME
import com.android.ide.common.repository.AgpVersion
import com.android.resources.ResourceFolderType
import com.android.testutils.TestUtils
import com.android.tools.lint.ClassName
import com.android.tools.lint.LintCliClient
import com.android.tools.lint.UastEnvironment
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintClient
import com.android.tools.lint.checks.infrastructure.createXmlContext
import com.android.tools.lint.checks.infrastructure.platformPath
import com.android.tools.lint.checks.infrastructure.portablePath
import com.android.tools.lint.client.api.LintClient.Companion.CLIENT_UNIT_TESTS
import com.android.tools.lint.client.api.TYPE_BOOLEAN
import com.android.tools.lint.client.api.TYPE_BOOLEAN_WRAPPER
import com.android.tools.lint.client.api.TYPE_BYTE
import com.android.tools.lint.client.api.TYPE_BYTE_WRAPPER
import com.android.tools.lint.client.api.TYPE_CHAR
import com.android.tools.lint.client.api.TYPE_CHARACTER_WRAPPER
import com.android.tools.lint.client.api.TYPE_DOUBLE
import com.android.tools.lint.client.api.TYPE_DOUBLE_WRAPPER
import com.android.tools.lint.client.api.TYPE_FLOAT
import com.android.tools.lint.client.api.TYPE_FLOAT_WRAPPER
import com.android.tools.lint.client.api.TYPE_INT
import com.android.tools.lint.client.api.TYPE_INTEGER_WRAPPER
import com.android.tools.lint.client.api.TYPE_LONG
import com.android.tools.lint.client.api.TYPE_LONG_WRAPPER
import com.android.tools.lint.client.api.TYPE_SHORT
import com.android.tools.lint.client.api.TYPE_SHORT_WRAPPER
import com.android.utils.Pair
import com.android.utils.SdkUtils.escapePropertyValue
import com.android.utils.XmlUtils
import com.android.utils.iterator
import com.google.common.collect.Iterables
import com.google.common.io.Files
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.pom.java.LanguageLevel
import java.io.BufferedOutputStream
import java.io.File
import java.io.File.separator
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.Locale
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.w3c.dom.Document
import org.w3c.dom.Element

class LintUtilsTest : TestCase() {
  fun testPrintList() {
    assertEquals("bar, baz, foo", formatList(listOf("foo", "bar", "baz"), 3))
    assertEquals("foo, bar, baz", formatList(listOf("foo", "bar", "baz"), 3, false))
    assertEquals("foo, bar, baz", formatList(listOf("foo", "bar", "baz"), 5, false))

    assertEquals(
      "foo, bar, baz... (3 more)",
      formatList(listOf("foo", "bar", "baz", "4", "5", "6"), 3, false),
    )
    assertEquals(
      "foo... (5 more)",
      formatList(listOf("foo", "bar", "baz", "4", "5", "6"), 1, false),
    )
    assertEquals("foo, bar, baz", formatList(listOf("foo", "bar", "baz"), 0, false))

    assertEquals("foo, bar and baz", formatList(listOf("foo", "bar", "baz"), 0, false, true))
  }

  fun testIsDataBindingExpression() {
    assertTrue(isDataBindingExpression("@{foo}"))
    assertTrue(isDataBindingExpression("@={foo}"))
    assertFalse(isDataBindingExpression("@string/foo"))
    assertFalse(isDataBindingExpression("?string/foo"))
    assertFalse(isDataBindingExpression(""))
    assertFalse(isDataBindingExpression("foo"))
  }

  fun testDescribeCounts() {
    assertThat(describeCounts(0, 0, true, true)).isEqualTo("No errors or warnings")
    assertThat(describeCounts(0, 0, true, false)).isEqualTo("no errors or warnings")
    assertThat(describeCounts(0, 1, true, true)).isEqualTo("1 warning")
    assertThat(describeCounts(1, 0, true, true)).isEqualTo("1 error")
    assertThat(describeCounts(0, 2, true, true)).isEqualTo("2 warnings")
    assertThat(describeCounts(2, 0, true, true)).isEqualTo("2 errors")
    assertThat(describeCounts(2, 1, false, true)).isEqualTo("2 errors and 1 warning")
    assertThat(describeCounts(1, 2, false, true)).isEqualTo("1 error and 2 warnings")
    assertThat(describeCounts(5, 4, false, true)).isEqualTo("5 errors and 4 warnings")
    assertThat(describeCounts(2, 1, true, true)).isEqualTo("2 errors, 1 warning")
    assertThat(describeCounts(1, 2, true, true)).isEqualTo("1 error, 2 warnings")
    assertThat(describeCounts(5, 4, true, true)).isEqualTo("5 errors, 4 warnings")
  }

  fun testEndsWith() {
    assertTrue(endsWith("Foo", ""))
    assertTrue(endsWith("Foo", "o"))
    assertTrue(endsWith("Foo", "oo"))
    assertTrue(endsWith("Foo", "Foo"))
    assertTrue(endsWith("Foo", "FOO"))
    assertTrue(endsWith("Foo", "fOO"))

    assertFalse(endsWith("Foo", "f"))
  }

  fun testStartsWith() {
    assertTrue(startsWith("FooBar", "Bar", 3))
    assertTrue(startsWith("FooBar", "BAR", 3))
    assertTrue(startsWith("FooBar", "Foo", 0))
    assertFalse(startsWith("FooBar", "Foo", 2))
  }

  fun testIsXmlFile() {
    assertTrue(isXmlFile(File("foo.xml")))
    assertTrue(isXmlFile(File("foo.Xml")))
    assertTrue(isXmlFile(File("foo.XML")))

    assertFalse(isXmlFile(File("foo.png")))
    assertFalse(isXmlFile(File("xml")))
    assertFalse(isXmlFile(File("xml.png")))
  }

  fun testEditDistance() {
    assertEquals(0, editDistance("kitten", "kitten"))

    // editing kitten to sitting has edit distance 3:
    //   replace k with s
    //   replace e with i
    //   append g
    assertEquals(3, editDistance("kitten", "sitting"))

    assertEquals(3, editDistance("saturday", "sunday"))
    assertEquals(1, editDistance("button", "bitton"))
    assertEquals(6, editDistance("radiobutton", "bitton"))

    assertEquals(6, editDistance("radiobutton", "bitton", 10))
    assertEquals(6, editDistance("radiobutton", "bitton", 6))
    assertEquals(Integer.MAX_VALUE, editDistance("radiobutton", "bitton", 3))

    assertTrue(isEditableTo("radiobutton", "bitton", 10))
    assertTrue(isEditableTo("radiobutton", "bitton", 6))
    assertFalse(isEditableTo("radiobutton", "bitton", 3))
  }

  fun testSplitPath() {
    assertTrue(
      arrayOf("/foo", "/bar", "/baz")
        .contentEquals(Iterables.toArray(splitPath("/foo:/bar:/baz"), String::class.java))
    )

    assertTrue(
      arrayOf("/foo", "/bar")
        .contentEquals(Iterables.toArray(splitPath("/foo;/bar"), String::class.java))
    )

    assertTrue(
      arrayOf("/foo", "/bar:baz")
        .contentEquals(Iterables.toArray(splitPath("/foo;/bar:baz"), String::class.java))
    )

    assertTrue(
      arrayOf("\\foo\\bar", "\\bar\\foo")
        .contentEquals(Iterables.toArray(splitPath("\\foo\\bar;\\bar\\foo"), String::class.java))
    )

    assertTrue(
      arrayOf("\${sdk.dir}\\foo\\bar", "\\bar\\foo")
        .contentEquals(
          Iterables.toArray(splitPath("\${sdk.dir}\\foo\\bar;\\bar\\foo"), String::class.java)
        )
    )

    assertTrue(
      arrayOf("\${sdk.dir}/foo/bar", "/bar/foo")
        .contentEquals(
          Iterables.toArray(splitPath("\${sdk.dir}/foo/bar:/bar/foo"), String::class.java)
        )
    )

    assertTrue(
      arrayOf("C:\\foo", "/bar")
        .contentEquals(Iterables.toArray(splitPath("C:\\foo:/bar"), String::class.java))
    )
  }

  fun testCommonParen1() {
    assertEquals(File("/a"), getCommonParent(File("/a/b/c/d/e"), File("/a/c")))
    assertEquals(File("/a"), getCommonParent(File("/a/c"), File("/a/b/c/d/e")))

    assertEquals(File("/"), getCommonParent(File("/foo/bar"), File("/bar/baz")))
    assertEquals(File("/"), getCommonParent(File("/foo/bar"), File("/")))
    assertNull(getCommonParent(File("C:\\Program Files"), File("F:\\")))
    assertNull(getCommonParent(File("C:/Program Files"), File("F:/")))

    assertEquals(File("/foo/bar/baz"), getCommonParent(File("/foo/bar/baz"), File("/foo/bar/baz")))
    assertEquals(File("/foo/bar"), getCommonParent(File("/foo/bar/baz"), File("/foo/bar")))
    assertEquals(File("/foo/bar"), getCommonParent(File("/foo/bar/baz"), File("/foo/bar/foo")))
    assertEquals(File("/foo"), getCommonParent(File("/foo/bar"), File("/foo/baz")))
    assertEquals(File("/foo"), getCommonParent(File("/foo/bar"), File("/foo/baz")))
    assertEquals(File("/foo/bar"), getCommonParent(File("/foo/bar"), File("/foo/bar/baz")))
  }

  fun testCommonParent2() {
    assertEquals(File("/"), getCommonParent(listOf(File("/foo/bar"), File("/bar/baz"))))
    assertEquals(File("/"), getCommonParent(listOf(File("/foo/bar"), File("/"))))
    assertNull(getCommonParent(listOf(File("C:\\Program Files"), File("F:\\"))))
    assertNull(getCommonParent(listOf(File("C:/Program Files"), File("F:/"))))

    assertEquals(File("/foo"), getCommonParent(listOf(File("/foo/bar"), File("/foo/baz"))))
    assertEquals(
      File("/foo"),
      getCommonParent(listOf(File("/foo/bar"), File("/foo/baz"), File("/foo/baz/f"))),
    )
    assertEquals(
      File("/foo/bar"),
      getCommonParent(listOf(File("/foo/bar"), File("/foo/bar/baz"), File("/foo/bar/foo2/foo3"))),
    )
  }

  fun testStripIdPrefix() {
    assertEquals("foo", stripIdPrefix("@+id/foo"))
    assertEquals("foo", stripIdPrefix("@id/foo"))
    assertEquals("foo", stripIdPrefix("foo"))
  }

  fun testIdReferencesMatch() {
    assertTrue(idReferencesMatch("@+id/foo", "@+id/foo"))
    assertTrue(idReferencesMatch("@id/foo", "@id/foo"))
    assertTrue(idReferencesMatch("@id/foo", "@+id/foo"))
    assertTrue(idReferencesMatch("@+id/foo", "@id/foo"))

    assertFalse(idReferencesMatch("@+id/foo", "@+id/bar"))
    assertFalse(idReferencesMatch("@id/foo", "@+id/bar"))
    assertFalse(idReferencesMatch("@+id/foo", "@id/bar"))
    assertFalse(idReferencesMatch("@+id/foo", "@+id/bar"))

    assertFalse(idReferencesMatch("@+id/foo", "@+id/foo1"))
    assertFalse(idReferencesMatch("@id/foo", "@id/foo1"))
    assertFalse(idReferencesMatch("@id/foo", "@+id/foo1"))
    assertFalse(idReferencesMatch("@+id/foo", "@id/foo1"))

    assertFalse(idReferencesMatch("@+id/foo1", "@+id/foo"))
    assertFalse(idReferencesMatch("@id/foo1", "@id/foo"))
    assertFalse(idReferencesMatch("@id/foo1", "@+id/foo"))
    assertFalse(idReferencesMatch("@+id/foo1", "@id/foo"))
  }

  fun testGetEncodedString() {
    checkEncoding("utf-8", false /*bom*/, "\n")
    checkEncoding("UTF-8", false /*bom*/, "\n")
    checkEncoding("UTF_16", false /*bom*/, "\n")
    checkEncoding("UTF-16", false /*bom*/, "\n")
    checkEncoding("UTF_16LE", false /*bom*/, "\n")

    // Try BOM's
    checkEncoding("utf-8", true /*bom*/, "\n")
    checkEncoding("UTF-8", true /*bom*/, "\n")
    checkEncoding("UTF_16", true /*bom*/, "\n")
    checkEncoding("UTF-16", true /*bom*/, "\n")
    checkEncoding("UTF_16LE", true /*bom*/, "\n")
    checkEncoding("UTF_32", true /*bom*/, "\n")
    checkEncoding("UTF_32LE", true /*bom*/, "\n")

    // Make sure this works for \r and \r\n as well
    checkEncoding("UTF-16", false /*bom*/, "\r")
    checkEncoding("UTF_16LE", false /*bom*/, "\r")
    checkEncoding("UTF-16", false /*bom*/, "\r\n")
    checkEncoding("UTF_16LE", false /*bom*/, "\r\n")
    checkEncoding("UTF-16", true /*bom*/, "\r")
    checkEncoding("UTF_16LE", true /*bom*/, "\r")
    checkEncoding("UTF_32", true /*bom*/, "\r")
    checkEncoding("UTF_32LE", true /*bom*/, "\r")
    checkEncoding("UTF-16", true /*bom*/, "\r\n")
    checkEncoding("UTF_16LE", true /*bom*/, "\r\n")
    checkEncoding("UTF_32", true /*bom*/, "\r\n")
    checkEncoding("UTF_32LE", true /*bom*/, "\r\n")
  }

  fun testGetLocale() {
    assertNull(getLocale(""))
    assertNull(getLocale("values"))
    assertNull(getLocale("values-xlarge-port"))
    assertEquals("en", getLocale("values-en")!!.language)
    assertEquals("pt", getLocale("values-pt-rPT-nokeys")!!.language)
    assertEquals("pt", getLocale("values-b+pt+PT-nokeys")!!.language)
    assertEquals("zh", getLocale("values-zh-rCN-keyshidden")!!.language)
  }

  @Suppress("JoinDeclarationAndAssignment")
  fun testGetLocale2() {
    var xml: TestFile
    var context: XmlContext

    xml = TestFiles.xml("res/values/strings.xml", "<resources>\n</resources>\n")
    context = createXmlContext(xml.getContents()!!, File(xml.targetPath))
    assertNull(getLocale(context))
    dispose(context)

    xml = TestFiles.xml("res/values-no/strings.xml", "<resources>\n</resources>\n")
    context = createXmlContext(xml.getContents()!!, File(xml.targetPath))
    assertEquals("no", getLocale(context)!!.language)
    dispose(context)

    xml =
      TestFiles.xml(
        "res/values/strings.xml",
        "" +
          "<resources tools:locale=\"nb\" xmlns:tools=\"http://schemas.android.com/tools\">\n" +
          "</resources>\n",
      )
    context = createXmlContext(xml.getContents()!!, File(xml.targetPath))
    assertEquals("nb", getLocale(context)!!.language)
    dispose(context)

    // folder location wins over tools:locale wins
    xml =
      TestFiles.xml(
        "res/values-fr-rUS/strings.xml",
        "" +
          "<resources tools:locale=\"nb\" xmlns:tools=\"http://schemas.android.com/tools\">\n" +
          "</resources>\n",
      )
    context = createXmlContext(xml.getContents()!!, File(xml.targetPath))
    assertEquals("fr", getLocale(context)!!.language)
    dispose(context)

    UastEnvironment.disposeApplicationEnvironment()
  }

  private fun dispose(context: XmlContext) {
    (context.project.client as? LintCliClient)?.disposeProjects(listOf(context.project))
  }

  fun testGetLocaleAndRegion() {
    assertNull(getLocaleAndRegion(""))
    assertNull(getLocaleAndRegion("values"))
    assertNull(getLocaleAndRegion("values-xlarge-port"))
    assertEquals("en", getLocaleAndRegion("values-en"))
    assertEquals("pt-rPT", getLocaleAndRegion("values-pt-rPT-nokeys"))
    assertEquals("b+pt+PT", getLocaleAndRegion("values-b+pt+PT-nokeys"))
    assertEquals("zh-rCN", getLocaleAndRegion("values-zh-rCN-keyshidden"))
    assertEquals("ms", getLocaleAndRegion("values-ms-keyshidden"))
  }

  fun testComputeResourceName() {
    assertEquals("", computeResourceName("", "", null))
    assertEquals("foo", computeResourceName("", "foo", null))
    assertEquals("foo", computeResourceName("foo", "", null))
    assertEquals("prefix_name", computeResourceName("prefix_", "name", null))
    assertEquals("prefixName", computeResourceName("prefix", "name", null))
    assertEquals("PrefixName", computeResourceName("prefix", "Name", null))
    assertEquals("PrefixName", computeResourceName("prefix_", "Name", null))
    assertEquals("MyPrefixName", computeResourceName("myPrefix", "Name", null))
    assertEquals(
      "my_prefix_name",
      computeResourceName("myPrefix", "name", ResourceFolderType.LAYOUT),
    )
    assertEquals(
      "UnitTestPrefixContentFrame",
      computeResourceName("unit_test_prefix_", "ContentFrame", ResourceFolderType.VALUES),
    )
    assertEquals(
      "MyPrefixMyStyle",
      computeResourceName("myPrefix_", "MyStyle", ResourceFolderType.VALUES),
    )
  }

  fun testIsModelOlderThan() {
    val project: Project = mock()

    assertTrue(isModelOlderThan(project, 0, 0, 0, true))
    assertFalse(isModelOlderThan(project, 0, 0, 0, false))

    whenever(project.gradleModelVersion).thenReturn(AgpVersion.parse("0.10.4"))
    assertTrue(isModelOlderThan(project, 0, 10, 5))
    assertTrue(isModelOlderThan(project, 0, 11, 0))
    assertTrue(isModelOlderThan(project, 0, 11, 4))
    assertTrue(isModelOlderThan(project, 1, 0, 0))

    whenever(project.gradleModelVersion).thenReturn(AgpVersion.parse("0.11.0"))

    assertTrue(isModelOlderThan(project, 1, 0, 0))
    assertFalse(isModelOlderThan(project, 0, 11, 0))
    assertFalse(isModelOlderThan(project, 0, 10, 4))

    whenever(project.gradleModelVersion).thenReturn(AgpVersion.parse("0.11.5"))

    assertTrue(isModelOlderThan(project, 1, 0, 0))
    assertFalse(isModelOlderThan(project, 0, 11, 0))

    whenever(project.gradleModelVersion).thenReturn(AgpVersion.parse("1.0.0"))

    assertTrue(isModelOlderThan(project, 1, 0, 1))
    assertFalse(isModelOlderThan(project, 1, 0, 0))
    assertFalse(isModelOlderThan(project, 0, 11, 0))
  }

  fun testFindSubstring() {
    assertEquals("foo", findSubstring("foo", null, null))
    assertEquals("foo", findSubstring("foo  ", null, "  "))
    assertEquals("foo", findSubstring("  foo", "  ", null))
    assertEquals("foo", findSubstring("[foo]", "[", "]"))
  }

  fun testGetFormattedParameters() {
    assertEquals(
      listOf("foo", "bar"),
      getFormattedParameters("Prefix %1\$s Divider %2\$s Suffix", "Prefix foo Divider bar Suffix"),
    )
  }

  fun testEscapePropertyValue() {
    assertEquals("foo", escapePropertyValue("foo"))
    assertEquals("\\  foo  ", escapePropertyValue("  foo  "))
    assertEquals("c\\:/foo/bar", escapePropertyValue("c:/foo/bar"))
    assertEquals("\\!\\#\\:\\\\a\\\\b\\\\c", escapePropertyValue("!#:\\a\\b\\c"))
    assertEquals(
      "foofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoo\\#foofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoo",
      escapePropertyValue(
        "foofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoo#foofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoo"
      ),
    )
  }

  fun testGetAutoBoxedType() {
    assertEquals(TYPE_INTEGER_WRAPPER, getAutoBoxedType(TYPE_INT))
    assertEquals(TYPE_INT, getPrimitiveType(TYPE_INTEGER_WRAPPER))

    val pairs =
      arrayOf(
        TYPE_BOOLEAN,
        TYPE_BOOLEAN_WRAPPER,
        TYPE_BYTE,
        TYPE_BYTE_WRAPPER,
        TYPE_CHAR,
        TYPE_CHARACTER_WRAPPER,
        TYPE_DOUBLE,
        TYPE_DOUBLE_WRAPPER,
        TYPE_FLOAT,
        TYPE_FLOAT_WRAPPER,
        TYPE_INT,
        TYPE_INTEGER_WRAPPER,
        TYPE_LONG,
        TYPE_LONG_WRAPPER,
        TYPE_SHORT,
        TYPE_SHORT_WRAPPER,
      )

    var i = 0
    while (i < pairs.size) {
      val primitive = pairs[i]
      val autoBoxed = pairs[i + 1]
      assertEquals(autoBoxed, getAutoBoxedType(primitive))
      assertEquals(primitive, getPrimitiveType(autoBoxed))
      i += 2
    }
  }

  fun testResolveManifestName() {
    assertEquals(
      "test.pkg.TestActivity",
      resolveManifestName(
        getElementWithNameValue(
          "" +
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    package=\"test.pkg\">\n" +
            "    <application>\n" +
            "        <activity android:name=\".TestActivity\" />\n" +
            "    </application>\n" +
            "</manifest>\n",
          ".TestActivity",
        ),
        null,
      ),
    )

    assertEquals(
      "test.pkg.TestActivity",
      resolveManifestName(
        getElementWithNameValue(
          "" +
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    package=\"test.pkg\">\n" +
            "    <application>\n" +
            "        <activity android:name=\"TestActivity\" />\n" +
            "    </application>\n" +
            "</manifest>\n",
          "TestActivity",
        ),
        null,
      ),
    )

    assertEquals(
      "test.pkg.TestActivity",
      resolveManifestName(
        getElementWithNameValue(
          "" +
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    package=\"test.pkg\">\n" +
            "    <application>\n" +
            "        <activity android:name=\"test.pkg.TestActivity\" />\n" +
            "    </application>\n" +
            "</manifest>\n",
          "test.pkg.TestActivity",
        ),
        null,
      ),
    )

    assertEquals(
      "test.pkg.TestActivity.Bar",
      resolveManifestName(
        getElementWithNameValue(
          "" +
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    package=\"test.pkg\">\n" +
            "    <application>\n" +
            "        <activity android:name=\"test.pkg.TestActivity\$Bar\" />\n" +
            "    </application>\n" +
            "</manifest>\n",
          "test.pkg.TestActivity\$Bar",
        ),
        null,
      ),
    )
  }

  fun testGetFileNameWithParent() {
    assertThat(
        getFileNameWithParent(
          TestLintClient(),
          File("tmp" + separator + "foo" + separator + "bar.baz"),
        )
      )
      .isEqualTo("foo/bar.baz")
    assertThat(
        getFileNameWithParent(
          LintCliClient(CLIENT_UNIT_TESTS),
          File("tmp" + separator + "foo" + separator + "bar.baz"),
        )
      )
      .isEqualTo("foo/bar.baz")
  }

  fun testResolvePlaceholders() {
    assertEquals("test", resolvePlaceHolders(null, "test"))
    assertEquals("test", resolvePlaceHolders(null, "test"))
    assertEquals("", resolvePlaceHolders(null, "\${test}", null, ""))
    assertNull(resolvePlaceHolders(null, "\${test}"))
    assertEquals("Test", resolvePlaceHolders(null, "\${test}", mapOf("test" to "Test"), null))
    assertEquals(
      "FirstSecond",
      resolvePlaceHolders(null, "\${abc}\${def}", mapOf("abc" to "First", "def" to "Second"), ""),
    )
    assertEquals(
      " First Second ",
      resolvePlaceHolders(null, " \${abc} \${def} ", mapOf("abc" to "First", "def" to "Second"), ""),
    )
  }

  fun testJavaKeyword() {
    assertThat(isJavaKeyword("")).isFalse()
    assertThat(isJavaKeyword("iff")).isFalse()
    assertThat(isJavaKeyword("if")).isTrue()
    assertThat(isJavaKeyword("true")).isTrue()
    assertThat(isJavaKeyword("false")).isTrue()
  }

  fun testIsParent() {
    fun file(path: String): File {
      return File(path.platformPath())
    }

    assertTrue(isParent(file("foo"), file("foo"), strict = false))
    assertFalse(isParent(file("foo"), file("foo"), strict = true))
    assertTrue(isParent(file("/tmp/foo/"), file("/tmp/foo"), strict = false))
    assertTrue(isParent(file("/tmp/foo"), file("/tmp/foo/"), strict = false))
    assertFalse(isParent(file("/tmp/foo"), file("/tmp/foo/"), strict = true))
    assertTrue(isParent(file("/tmp/foo"), file("/tmp/foo/bar")))
    assertFalse(isParent(file("/tmp/foo"), file("/tmp/fooo")))
  }

  fun testGetFileUri() {
    val pwd = System.getProperty("user.dir")
    val uri = getFileUri(File("$pwd${separator}foo"))
    assertTrue(uri.startsWith("file://"))
    assertTrue(uri.endsWith("/foo"))
  }

  fun testMatchElements() {
    /** Find an element under [element] whose id or name or position descriptor matches [id] */
    fun findElement(element: Element, id: String, parents: String = ""): Element? {
      if (element.getAttribute(ATTR_ID) == id) {
        return element
      }
      if (element.getAttribute(ATTR_NAME) == id) {
        return element
      }
      var number = 1
      for (child in element) {
        val desc = "$parents<${child.tagName}:$number>"
        if (desc == id) {
          return child
        }
        val found = findElement(child, id, desc)
        if (found != null) {
          return found
        }
        number++
      }

      return null
    }

    /**
     * Given two XML documents and two id's, look up the nodes, then test the matching method
     * ([matchXmlElement]) to make sure that the match for the first id returns the same element as
     * the one found by id search.
     */
    fun testMatch(
      sourceDocument: Document,
      sourceId: String,
      targetDocument: Document,
      targetId: String,
    ) {
      val source = findElement(sourceDocument.documentElement, sourceId)!!
      val target = findElement(targetDocument.documentElement, targetId)!!

      assertSame(target, matchXmlElement(source, targetDocument))
    }

    /** Creates an XML DOM document from the given XML. */
    fun xml(@Language("XML") xml: String): Document {
      return XmlUtils.parseDocumentSilently(xml, false)!!
    }

    // The document we're going to search for equivalent elements from
    val doc1 =
      xml(
        """
                <root>
                    <extra/>
                    <child>
                        <grandchild id="1"/>
                        <grandchild id="2"/>
                    </child>
                    <child>
                        <grandchild id="3"/>
                        <duplicate/> <!-- first -->
                        <grandchild id="4"/>
                        <duplicate/> <!-- second -->
                        <grandchild name="n1"/>
                        <grandchild name="n2"/>
                        <nomatch id="nomatch"/>
                    </child>
                </root>
                """
      )

    // The document we're trying to find matches in
    val doc2 =
      xml(
        """
                <root>
                    <child2>
                        <grandchild name="n2"/> <!-- wrong path -->
                    </child2>
                    <child>
                        <!-- comment here -->
                        <grandchild id="1"/>
                    </child>
                    <child>
                        <unrelated/>
                        <grandchild id="3"/>
                        <grandchild id="2"/>
                        <grandchild id="4"/>
                        <anchor id="anchor" />
                        <duplicate/> <!-- first -->
                        <duplicate/> <!-- second -->
                        <grandchild name="n2"/>
                        <grandchild name="n1"/>
                    </child>
                </root>
                """
      )

    // ID matching
    testMatch(doc1, "1", doc2, "1")
    testMatch(doc1, "2", doc2, "2")
    testMatch(doc1, "3", doc2, "3")
    testMatch(doc1, "4", doc2, "4")

    // Name matching
    testMatch(doc1, "n1", doc2, "n1")

    // Ordinal matching
    testMatch(
      doc1,
      "n2",
      doc2,
      // Don't search for n2 in the target since there's an identically named incorrect
      // match in the wrong parent path
      "<child:3><grandchild:8>",
    )
    testMatch(doc1, "<child:3><duplicate:4>", doc2, "<child:3><duplicate:7>")

    // Trivial matching
    assertSame(doc2.documentElement, matchXmlElement(doc1.documentElement, doc2))

    // Make sure we gracefully handle case where no match is found
    val missing = findElement(doc1.documentElement, "nomatch")!!
    assertNull(matchXmlElement(missing, doc2))
  }

  companion object {
    private fun checkEncoding(encoding: String, writeBom: Boolean, lineEnding: String) {
      val sb = StringBuilder()

      // Norwegian extra vowel characters such as "latin small letter a with ring above"
      val value = "\u00e6\u00d8\u00e5"
      val expected =
        ("First line." +
          lineEnding +
          "Second line." +
          lineEnding +
          "Third line." +
          lineEnding +
          value +
          lineEnding)
      sb.append(expected)
      val file = File.createTempFile("getEncodingTest$encoding$writeBom", ".txt")
      file.deleteOnExit()
      val stream = BufferedOutputStream(FileOutputStream(file))
      val writer = OutputStreamWriter(stream, encoding)

      if (writeBom) {
        val normalized = encoding.lowercase(Locale.US).replace("-", "_")
        when (normalized) {
          "utf_8" -> {
            stream.write(0xef)
            stream.write(0xbb)
            stream.write(0xbf)
          }
          "utf_16" -> {
            stream.write(0xfe)
            stream.write(0xff)
          }
          "utf_16le" -> {
            stream.write(0xff)
            stream.write(0xfe)
          }
          "utf_32" -> {
            stream.write(0x0)
            stream.write(0x0)
            stream.write(0xfe)
            stream.write(0xff)
          }
          "utf_32le" -> {
            stream.write(0xff)
            stream.write(0xfe)
            stream.write(0x0)
            stream.write(0x0)
          }
          else -> fail("Can't write BOM for encoding $encoding")
        }
      }
      writer.write(sb.toString())
      writer.close()

      val s = getEncodedString(LintCliClient(CLIENT_UNIT_TESTS), file, true).toString()
      assertEquals(expected, s)

      val seq = getEncodedString(LintCliClient(CLIENT_UNIT_TESTS), file, false)
      if (encoding.equals("utf-8", ignoreCase = true)) {
        assertFalse(seq is String)
      }
      assertEquals(expected, seq.toString())
    }

    @JvmStatic
    fun parse(
      @Language("JAVA") javaSource: String,
      relativePath: File?,
    ): Pair<JavaContext, Disposable> {
      var path = relativePath
      if (path == null) {
        val className = ClassName(javaSource)
        val pkg = className.packageName!!
        val name = className.className!!
        path = File("src$separator$pkg$separator$name.java")
      }

      return parse(java(path.path.portablePath(), javaSource))
    }

    @JvmStatic
    fun parseKotlin(
      @Language("Kt") kotlinSource: String,
      relativePath: File?,
    ): Pair<JavaContext, Disposable> {
      var path = relativePath
      if (path == null) {
        val className = ClassName(kotlinSource)
        val pkg = className.packageName
        val name = className.className
        assert(pkg != null)
        assert(name != null)
        path = File("src$separator$pkg$separator$name.kt")
      }

      return parse(kotlin(path.path.portablePath(), kotlinSource))
    }

    @JvmStatic
    fun parse(vararg testFiles: TestFile): Pair<JavaContext, Disposable> {
      return parse(testFiles = testFiles, javaLanguageLevel = null)
    }

    @JvmStatic
    fun parse(
      vararg testFiles: TestFile = emptyArray(),
      javaLanguageLevel: LanguageLevel? = null,
      kotlinLanguageLevel: LanguageVersionSettings? = null,
      library: Boolean = false,
      android: Boolean = true,
    ): Pair<JavaContext, Disposable> {
      val temp = Files.createTempDir()
      val temporaryFolder = TemporaryFolder(temp)
      temporaryFolder.create()
      val parsed =
        com.android.tools.lint.checks.infrastructure.parseFirst(
          javaLanguageLevel = javaLanguageLevel,
          kotlinLanguageLevel = kotlinLanguageLevel,
          library = library,
          android = android,
          sdkHome = TestUtils.getSdk().toFile(),
          temporaryFolder = temporaryFolder,
          testFiles = testFiles,
        )
      val disposable = Disposable {
        Disposer.dispose(parsed.second)
        temp.deleteRecursively()
      }
      return Pair.of(parsed.first, disposable)
    }

    @JvmStatic
    fun parseAll(vararg testFiles: TestFile): Pair<List<JavaContext>, Disposable> {
      val temp = Files.createTempDir()
      val temporaryFolder = TemporaryFolder(temp)
      temporaryFolder.create()
      val parsed =
        com.android.tools.lint.checks.infrastructure.parse(
          javaLanguageLevel = null,
          kotlinLanguageLevel = null,
          library = false,
          sdkHome = TestUtils.getSdk().toFile(),
          android = true,
          temporaryFolder = temporaryFolder,
          testFiles = testFiles,
        )
      val disposable = Disposable {
        Disposer.dispose(parsed.second)
        temp.deleteRecursively()
      }
      return Pair.of(parsed.first, disposable)
    }

    private fun getElementWithNameValue(
      @Language("XML") xml: String,
      activityName: String,
    ): Element {
      val document = XmlUtils.parseDocumentSilently(xml, true)
      assertNotNull(document)
      val root = document!!.documentElement
      assertNotNull(root)
      for (application in getChildren(root)) {
        for (element in getChildren(application)) {
          val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
          if (activityName == name) {
            return element
          }
        }
      }

      fail("Didn't find $activityName")
      throw AssertionError("Didn't find $activityName")
    }
  }
}
