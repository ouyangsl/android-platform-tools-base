/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.tools.lint.checks.AbstractCheckTest
import com.android.tools.lint.checks.AccessibilityDetector
import com.android.tools.lint.checks.ActionsXmlDetector
import com.android.tools.lint.checks.ApiDetector
import com.android.tools.lint.checks.ChromeOsDetector
import com.android.tools.lint.checks.HardcodedValuesDetector
import com.android.tools.lint.checks.IconDetector
import com.android.tools.lint.checks.InteroperabilityDetector
import com.android.tools.lint.checks.ObsoleteLayoutParamsDetector
import com.android.tools.lint.checks.SdCardDetector
import com.android.tools.lint.checks.SecureRandomGeneratorDetector
import com.android.tools.lint.checks.TooManyViewsDetector
import com.android.tools.lint.checks.TypoDetector
import com.android.tools.lint.checks.UnusedResourceDetector
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestIssueRegistry
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.checks.infrastructure.portablePath
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Location.Companion.create
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Severity
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.File.separator
import java.util.Locale
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.rules.TemporaryFolder

@Suppress("DEPRECATION")
class LintXmlConfigurationTest : AbstractCheckTest() {
  @get:Rule var temporaryFolder = TemporaryFolder()

  // Sample included via @sample in the KDoc for LintXmlConfiguration
  @Suppress("unused")
  fun sampleFile() =
    // Not indented plus .trimIndent() because the IDE support for
    // showing docs does not handle that very well
    """
<?xml version="1.0" encoding="UTF-8"?>
<lint lintJars="../checks/local.jar;../checks/custom.jar">
    <!-- The special id "all" matches all issues but is only consulted
         if there is no specific match -->
    <issue id="all" severity="ignore" />
    <!-- Possible severities: ignore, information, warning, error, fatal -->
    <issue id="ValidActionsXml" severity="error" />
    <issue id="ObsoleteLayoutParam">
        <!-- The <ignore> tag has two possible attributes: path and regexp (see below) -->
        <ignore path="res/layout-xlarge/activation.xml" />
        <!-- You can use globbing patterns in the path strings -->
        <ignore path="**/layout-x*/onclick.xml" />
        <ignore path="res/**/activation.xml" />
    </issue>
    <issue id="NewApi">
        <!-- You can also ignore via a regular expression, this is not only
            matched against the path but also the error message -->
        <ignore regexp="st.*gs" />
    </issue>
    <!-- The "in" attribute lets you specify that the element only
         applies in a particular tools, such as gradle, studio, etc; this
         can be a comma separated list -->
    <issue in="studio" id="NewerVersionAvailable" severity="error" />
    <!-- You can also use ! to specify that it does not apply in a tool  -->
    <issue in="!gradle" id="TrulyRandom" severity="error" />
    <issue id="UnknownNullness">
        <!-- For detectors that support it, you can also specify option values -->
        <option name="ignore-deprecated" value="true" />
    </issue>
    <issue id="TooManyViews">
        <option name="maxCount" value="20" />
    </issue>
</lint>
"""

  override fun setUp() {
    super.setUp()
    temporaryFolder.create()
  }

  fun testBasic() {
    val configuration =
      getConfiguration(
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <lint>
                <issue id="ObsoleteLayoutParam">
                    <ignore path="res/layout-xlarge/activation.xml" />
                </issue>
                <issue id="ValidActionsXml" severity="ignore" />
                <issue id="TrulyRandom" severity="error" />
                <issue id="SdCardPath,ContentDescription" severity="ignore" />
                <issue id="NewApi">
                    <ignore path="res/layout-xlarge" />
                </issue>
            </lint>
            """
          .trimIndent()
      )
    assertTrue(configuration.isEnabled(ObsoleteLayoutParamsDetector.ISSUE))
    assertFalse(configuration.isEnabled(SdCardDetector.ISSUE))
    assertFalse(configuration.isEnabled(ActionsXmlDetector.ISSUE))
    assertFalse(configuration.isEnabled(AccessibilityDetector.ISSUE))
    assertEquals(Severity.IGNORE, configuration.getSeverity(AccessibilityDetector.ISSUE))
    assertEquals(Severity.WARNING, AccessibilityDetector.ISSUE.defaultSeverity)
    assertEquals(Severity.WARNING, SecureRandomGeneratorDetector.ISSUE.defaultSeverity)
    assertEquals(Severity.ERROR, configuration.getSeverity(SecureRandomGeneratorDetector.ISSUE))
    assertEquals(Severity.IGNORE, configuration.getSeverity(ActionsXmlDetector.ISSUE))
  }

  fun testOptions() {
    val configuration =
      getConfiguration(
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <lint>
                <issue id="UnknownNullness">
                    <option name="ignore-deprecated" value="true" />
                </issue>
                <issue id="TooManyViews">
                    <option name="maxCount" value="20" />
                </issue>
                <issue id="TooDeepLayout">
                    <option name="maxDepth" value="5" />
                </issue>
                <issue id="NewApi">
                    <option name="allowed" value="api/list.xml" />
                </issue>
            </lint>
            """
          .trimIndent()
      )
    assertNull(configuration.getOption(InteroperabilityDetector.PLATFORM_NULLNESS, "unknown", null))
    assertEquals(
      "default",
      configuration.getOption(InteroperabilityDetector.PLATFORM_NULLNESS, "unknown", "default"),
    )
    assertEquals(
      true,
      configuration.getOptionAsBoolean(
        InteroperabilityDetector.PLATFORM_NULLNESS,
        "ignore-deprecated",
        false,
      ),
    )
    assertEquals(5, configuration.getOptionAsInt(TooManyViewsDetector.TOO_DEEP, "maxDepth", 1))
    val file = configuration.getOptionAsFile(ApiDetector.UNSUPPORTED, "allowed", null)
    assertNotNull(file)
    assertEquals(
      file!!.canonicalFile,
      File(configuration.configFile.parentFile, "api" + separator + "list.xml").canonicalFile,
    )

    configuration.startBulkEditing()
    // unset
    configuration.setBooleanOption(
      InteroperabilityDetector.PLATFORM_NULLNESS,
      "ignore-deprecated",
      null,
    )
    // replace
    configuration.setIntOption(TooManyViewsDetector.TOO_DEEP, "maxDepth", 5)
    // new
    configuration.setOption(ObsoleteLayoutParamsDetector.ISSUE, "except", "layoutEnd")
    configuration.setBooleanOption(AccessibilityDetector.ISSUE, "enforceDesc", true)
    configuration.finishBulkEditing()

    val updated = configuration.configFile.readText()
    assertEquals(
      """
            <?xml version="1.0" encoding="UTF-8"?>
            <lint>
                <issue id="ContentDescription">
                    <option name="enforceDesc" value="true" />
                </issue>
                <issue id="NewApi">
                    <option name="allowed" value="api/list.xml" />
                </issue>
                <issue id="ObsoleteLayoutParam">
                    <option name="except" value="layoutEnd" />
                </issue>
                <issue id="TooDeepLayout">
                    <option name="maxDepth" value="5" />
                </issue>
                <issue id="TooManyViews">
                    <option name="maxCount" value="20" />
                </issue>
            </lint>
            """
        .trimIndent(),
      updated,
    )
  }

  fun testInheritedFileBasePath() {
    // Regression test for https://issuetracker.google.com/191692647
    val folder = temporaryFolder.root
    val parentConfiguration =
      getConfiguration(
        """
            <lint>
                <issue id="UnknownNullness">
                    <option name="exceptions" value="exceptions.xml" />
                </issue>
                <issue id="NewApi">
                    <option name="allowed" value="api/list.xml" />
                </issue>
            </lint>
            """
          .trimIndent(),
        initialDir = File(folder, "parent"),
      )

    val configuration =
      getConfiguration(
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <lint>
                <issue id="UnknownNullness">
                    <option name="exceptions" value="new-exceptions.xml" />
                </issue>
            </lint>
            """
          .trimIndent(),
        initialDir = File(folder, "child"),
      )

    configuration.configurations.setParent(configuration, parentConfiguration)

    fun getPath(file: File?): String {
      file ?: return "null"
      return file.path.removePrefix(temporaryFolder.root.path).portablePath()
    }

    assertEquals(
      "/parent/api/list.xml", // not /child/api/list.xml
      getPath(configuration.getOptionAsFile(ApiDetector.UNSUPPORTED, "allowed")),
    )
    assertEquals(
      "/child/new-exceptions.xml",
      getPath(
        configuration.getOptionAsFile(InteroperabilityDetector.PLATFORM_NULLNESS, "exceptions")
      ),
    )
  }

  fun testClientFilters() {
    // Also tests the "hide" and "hidden" aliases for "ignore"
    val configuration =
      getConfiguration(
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <lint>
                <issue in="all2" id="ObsoleteLayoutParam" severity="informational" />
                <issue in="!studio" id="ObsoleteLayoutParam" severity="fatal" />
                <issue in="gradle" id="ObsoleteLayoutParam" severity="hidden" />
                <issue id="ValidActionsXml" severity="fatal" />
                <issue in="test" id="ValidActionsXml" severity="hide" />
                <issue id="NewApi" severity="fatal">
                <issue id="SdCardPath,ContentDescription" severity="hidden" />
                <issue in="studio,test" id="NewApi">
                    <ignore path="res/layout-xlarge" />
                </issue>
                <issue in="gradle, test" id="ValidActionsXml" severity="ignore" />
                <issue in="!studio" id="ContentDescription" severity="ignore" />
                <issue in="!test" id="InlinedApi" severity="ignore" />
            </lint>
            """
          .trimIndent()
      )
    assertEquals(LintClient.clientName, "test")
    assertEquals(Severity.IGNORE, configuration.getSeverity(ActionsXmlDetector.ISSUE))
    assertEquals(Severity.IGNORE, configuration.getSeverity(AccessibilityDetector.ISSUE))
    assertEquals(Severity.FATAL, configuration.getSeverity(ObsoleteLayoutParamsDetector.ISSUE))
    assertEquals(Severity.FATAL, configuration.getSeverity(ApiDetector.UNSUPPORTED))
    assertEquals(Severity.WARNING, configuration.getSeverity(ApiDetector.INLINED))

    // Test: when you specify maching client, none, other, the match wins
    // Test: when matching all, lower priority than any of those

    configuration.setSeverity(ApiDetector.INLINED, Severity.IGNORE)

    val updated = configuration.configFile.readText()
    assertEquals(
      """
            <?xml version="1.0" encoding="UTF-8"?>
            <lint>
                <issue in="test" id="ValidActionsXml" severity="ignore" />
                <issue in="studio,test" id="NewApi">
                    <ignore path="res/layout-xlarge" />
                </issue>
                <issue in="test" id="ValidActionsXml" severity="ignore" />
                <issue id="ContentDescription" severity="ignore" />
                <issue id="InlinedApi" severity="ignore" />
                <issue id="NewApi" severity="fatal" />
                <issue id="SdCardPath" severity="ignore" />
                <issue id="ValidActionsXml" severity="fatal" />
                <issue in="!studio" id="ContentDescription" severity="ignore" />
                <issue in="!studio" id="ObsoleteLayoutParam" severity="fatal" />
            </lint>
            """
        .trimIndent(),
      updated,
    )
  }

  fun testClientFiltersFileLevel() {
    val configuration =
      getConfiguration(
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <lint in="studio">
                <issue id="ObsoleteLayoutParam" severity="fatal" />
                <issue in="test" id="ValidActionsXml" severity="fatal" />
            </lint>
            """
          .trimIndent()
      )
    assertEquals(LintClient.clientName, "test")
    assertEquals(Severity.WARNING, configuration.getSeverity(ObsoleteLayoutParamsDetector.ISSUE))
    assertEquals(Severity.FATAL, configuration.getSeverity(ActionsXmlDetector.ISSUE))
  }

  fun testAllIds() {
    val configuration =
      getConfiguration(
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <lint>
                <issue id="all" severity="ignore" />
                <issue id="ValidActionsXml" severity="error" />
            </lint>
            """
          .trimIndent()
      )
    assertEquals(Severity.IGNORE, configuration.getSeverity(AccessibilityDetector.ISSUE))
    assertEquals(Severity.IGNORE, configuration.getSeverity(SecureRandomGeneratorDetector.ISSUE))
    assertEquals(Severity.IGNORE, configuration.getSeverity(HardcodedValuesDetector.ISSUE))
    assertEquals(Severity.ERROR, configuration.getSeverity(ActionsXmlDetector.ISSUE))
  }

  fun testNoFlags() {
    val configuration =
      getConfiguration(
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <lint>
            </lint>
            """
          .trimIndent()
      )
    assertThat(configuration.getAbortOnError()).isNull()
  }

  fun testFlags() {
    val configuration =
      getConfiguration(
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <lint
                checkAllWarnings='true'
                ignoreWarnings='true'
                warningsAsErrors='true'
                fatalOnly='true'
                checkTestSources='true'
                ignoreTestSources='true'
                checkGeneratedSources='true'
                checkDependencies='true'
                explainIssues='true'
                removeFixedBaselineIssues='true'
                abortOnError='true'
                allowSuppress='true'
            >
            </lint>
            """
          .trimIndent()
      )
    assertThat(configuration.getCheckAllWarnings()).isTrue()
    assertThat(configuration.getIgnoreWarnings()).isTrue()
    assertThat(configuration.getWarningsAsErrors()).isTrue()
    assertThat(configuration.getFatalOnly()).isTrue()
    assertThat(configuration.getCheckTestSources()).isTrue()
    assertThat(configuration.getIgnoreTestSources()).isTrue()
    assertThat(configuration.getCheckGeneratedSources()).isTrue()
    assertThat(configuration.getCheckDependencies()).isTrue()
    assertThat(configuration.getExplainIssues()).isTrue()
    assertThat(configuration.getRemoveFixedBaselineIssues()).isTrue()
    assertThat(configuration.getAbortOnError()).isTrue()
  }

  fun testPathIgnore() {
    val projectDir = getProjectDir(mOnclick, mOnclick2, mOnclick3)
    val client: LintClient = createClient()
    val project = Project.create(client, projectDir, projectDir)
    val request = LintRequest(client, emptyList())
    val driver = LintDriver(TestIssueRegistry(), client, request)
    val plainFile = File(projectDir, "res" + separator + "layout" + separator + "onclick.xml")
    assertTrue(plainFile.exists())
    val largeFile =
      File(projectDir, "res" + separator + "layout-xlarge" + separator + "onclick.xml")
    assertTrue(largeFile.exists())
    val windowsFile =
      File(projectDir, "res" + separator + "layout-xlarge" + separator + "activation.xml")
    assertTrue(windowsFile.exists())
    val stringsFile = File(projectDir, "res" + separator + "values" + separator + "strings.xml")
    val plainContext = Context(driver, project, project, plainFile, null)
    val largeContext = Context(driver, project, project, largeFile, null)
    val windowsContext = Context(driver, project, project, windowsFile, null)
    val plainLocation = create(plainFile)
    val largeLocation = create(largeFile)
    val windowsLocation = create(windowsFile)
    val stringsLocation = create(stringsFile)
    assertEquals(Severity.WARNING, ObsoleteLayoutParamsDetector.ISSUE.defaultSeverity)
    assertEquals(Severity.ERROR, ApiDetector.UNSUPPORTED.defaultSeverity)
    val configuration =
      getConfiguration(
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <lint>
                <issue id="all">
                    <ignore path="res/values/strings.xml" />
                </issue>
                <issue id="ObsoleteLayoutParam">
                    <ignore path="res/layout-xlarge/onclick.xml" />
                    <ignore path="res\layout-xlarge\activation.xml" />
                </issue>
                <issue id="NewApi">
                    <ignore path="res/layout-xlarge" />
                </issue>
                <issue in="gradle, test" id="ValidActionsXml">
                    <ignore path="res/layout" />
                </issue>
                <issue in="!studio" id="ContentDescription">
                    <ignore path="res/layout" />
                </issue>
                <issue in="!test" id="InlinedApi">
                    <ignore path="res/layout" />
                </issue>
            </lint>
            """
          .trimIndent()
      )

    assertTrue(configuration.isIgnored(plainContext, ActionsXmlDetector.ISSUE, plainLocation, ""))
    assertTrue(
      configuration.isIgnored(plainContext, AccessibilityDetector.ISSUE, plainLocation, "")
    )
    assertFalse(configuration.isIgnored(plainContext, ApiDetector.INLINED, plainLocation, ""))

    assertFalse(configuration.isIgnored(plainContext, ApiDetector.UNSUPPORTED, plainLocation, ""))
    assertFalse(
      configuration.isIgnored(plainContext, ObsoleteLayoutParamsDetector.ISSUE, plainLocation, "")
    )
    assertTrue(
      configuration.isIgnored(
        windowsContext,
        ObsoleteLayoutParamsDetector.ISSUE,
        windowsLocation,
        "",
      )
    )
    assertTrue(configuration.isIgnored(largeContext, ApiDetector.UNSUPPORTED, largeLocation, ""))
    assertTrue(
      configuration.isIgnored(largeContext, ObsoleteLayoutParamsDetector.ISSUE, largeLocation, "")
    )
    assertTrue(
      configuration.isIgnored(plainContext, ObsoleteLayoutParamsDetector.ISSUE, stringsLocation, "")
    )
    assertTrue(configuration.isIgnored(plainContext, ApiDetector.UNSUPPORTED, stringsLocation, ""))
  }

  fun testPatternIgnore() {
    val projectDir = getProjectDir(mOnclick, mOnclick2, mOnclick3)
    val client: LintClient = createClient()
    val project = Project.create(client, projectDir, projectDir)
    val request = LintRequest(client, emptyList())
    val driver = LintDriver(TestIssueRegistry(), client, request)
    val plainFile = File(projectDir, "res" + separator + "layout" + separator + "onclick.xml")
    assertTrue(plainFile.exists())
    val largeFile =
      File(projectDir, "res" + separator + "layout-xlarge" + separator + "onclick.xml")
    assertTrue(largeFile.exists())
    val windowsFile =
      File(projectDir, "res" + separator + "layout-xlarge" + separator + "activation.xml")
    assertTrue(windowsFile.exists())
    val stringsFile = File(projectDir, "res" + separator + "values" + separator + "strings.xml")
    val plainContext = Context(driver, project, project, plainFile, null)
    val largeContext = Context(driver, project, project, largeFile, null)
    val windowsContext = Context(driver, project, project, windowsFile, null)
    val plainLocation = create(plainFile)
    val largeLocation = create(largeFile)
    val windowsLocation = create(windowsFile)
    val stringsLocation = create(stringsFile)
    assertEquals(Severity.WARNING, ObsoleteLayoutParamsDetector.ISSUE.defaultSeverity)
    assertEquals(Severity.ERROR, ApiDetector.UNSUPPORTED.defaultSeverity)
    val configuration =
      getConfiguration(
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <lint>
                <issue id="all">
                    <ignore regexp="st.*gs" />
                </issue>
                <issue id="ObsoleteLayoutParam">
                    <ignore regexp="x.*onclick" />
                    <ignore regexp="res/.*layout.*/activation.xml" />
                </issue>
                <issue id="UnusedResources">
                    <ignore regexp="R\.font.*">
                </issue>
            </lint>
            """
          .trimIndent()
      )
    assertFalse(configuration.isIgnored(plainContext, ApiDetector.UNSUPPORTED, plainLocation, ""))
    assertFalse(
      configuration.isIgnored(plainContext, ObsoleteLayoutParamsDetector.ISSUE, plainLocation, "")
    )
    assertTrue(
      configuration.isIgnored(
        windowsContext,
        ObsoleteLayoutParamsDetector.ISSUE,
        windowsLocation,
        "",
      )
    )
    assertTrue(
      configuration.isIgnored(largeContext, ObsoleteLayoutParamsDetector.ISSUE, largeLocation, "")
    )
    assertTrue(configuration.isIgnored(plainContext, ApiDetector.UNSUPPORTED, stringsLocation, ""))
    assertTrue(
      configuration.isIgnored(plainContext, ObsoleteLayoutParamsDetector.ISSUE, stringsLocation, "")
    )
    // Regression test for https://issuetracker.google.com/131851821
    // Lint reports error about unused font resource in the library project
    assertTrue(
      configuration.isIgnored(
        plainContext,
        UnusedResourceDetector.ISSUE,
        plainLocation,
        "The resource R.font. appears to be unused",
      )
    )
  }

  fun testPatternIgnore2() {
    // 177044619: Test globbing and patterns
    val projectDir =
      getProjectDir(
        java(
          "src/main/java/com/domain/android/lever/RepositoryShould.java",
          "" + "package com.domain.android.lever;\n" + "class RepositoryShould() { }",
        ),
        java(
          "src/androidTest/java/com/domain/android/lever2/RepositoryShould2.java",
          "" + "package com.domain.android.lever2;\n" + "class RepositoryShould2() { }",
        ),
      )

    val client: LintClient = createClient()
    val project = Project.create(client, projectDir, projectDir)
    val request = LintRequest(client, emptyList())
    val driver = LintDriver(TestIssueRegistry(), client, request)
    val plainFile =
      File(projectDir, "src/androidTest/java/com/domain/android/lever2/RepositoryShould2.java")
    val context = Context(driver, project, project, plainFile, null)
    val location = create(plainFile)
    val message = "This is the message"

    val configDir = File(projectDir, "configdir")
    configDir.mkdirs()

    val configuration1 =
      getConfiguration(
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <lint>
                <issue id="ObsoleteLayoutParam" severity="error">
                    <ignore regexp=".*/src/androidTest"/>
                </issue>
            </lint>
            """
          .trimIndent()
      )
    assertTrue(
      configuration1.isIgnored(context, ObsoleteLayoutParamsDetector.ISSUE, location, message)
    )

    val configuration2 =
      getConfiguration(
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <lint>
                <issue id="ObsoleteLayoutParam" severity="error">
                    <ignore regexp=".*/src/androidTest/.*"/>
                </issue>
            </lint>
            """
          .trimIndent()
      )
    assertTrue(
      configuration2.isIgnored(context, ObsoleteLayoutParamsDetector.ISSUE, location, message)
    )

    val configuration3 =
      getConfiguration(
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <lint>
                <issue id="ObsoleteLayoutParam" severity="error">
                    <ignore path="*/src/androidTest*"/>
                </issue>
            </lint>
            """
          .trimIndent()
      )
    assertTrue(
      configuration3.isIgnored(context, ObsoleteLayoutParamsDetector.ISSUE, location, message)
    )
  }

  fun testGlobbing() {
    val projectDir = getProjectDir(mOnclick, mOnclick2, mOnclick3)
    val client: LintClient = createClient()
    val project = Project.create(client, projectDir, projectDir)
    val request = LintRequest(client, emptyList())
    val driver = LintDriver(TestIssueRegistry(), client, request)
    val plainFile = File(projectDir, "res" + separator + "layout" + separator + "onclick.xml")
    assertTrue(plainFile.exists())
    val largeFile =
      File(projectDir, "res" + separator + "layout-xlarge" + separator + "onclick.xml")
    assertTrue(largeFile.exists())
    val windowsFile =
      File(projectDir, "res" + separator + "layout-xlarge" + separator + "activation.xml")
    assertTrue(windowsFile.exists())
    val plainContext = Context(driver, project, project, plainFile, null)
    val largeContext = Context(driver, project, project, largeFile, null)
    val windowsContext = Context(driver, project, project, windowsFile, null)
    val plainLocation = create(plainFile)
    val largeLocation = create(largeFile)
    val windowsLocation = create(windowsFile)
    assertEquals(Severity.WARNING, ObsoleteLayoutParamsDetector.ISSUE.defaultSeverity)
    assertEquals(Severity.ERROR, ApiDetector.UNSUPPORTED.defaultSeverity)
    val configuration =
      getConfiguration(
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <lint>
                <issue id="ObsoleteLayoutParam">
                    <ignore path="**/layout-x*/onclick.xml" />
                    <ignore path="app/res/*/activation.xml" />
                    <ignore path="**/res2/**" />
                </issue>
            </lint>
            """
          .trimIndent()
      )
    assertFalse(configuration.isIgnored(plainContext, ApiDetector.UNSUPPORTED, plainLocation, ""))
    assertFalse(
      configuration.isIgnored(plainContext, ObsoleteLayoutParamsDetector.ISSUE, plainLocation, "")
    )
    assertTrue(
      configuration.isIgnored(
        windowsContext,
        ObsoleteLayoutParamsDetector.ISSUE,
        windowsLocation,
        "",
      )
    )
    assertTrue(
      configuration.isIgnored(largeContext, ObsoleteLayoutParamsDetector.ISSUE, largeLocation, "")
    )
    val res2 =
      File(
        projectDir,
        "something" + separator + "res2" + separator + "something" + separator + "something2.xml",
      )
    val res2Location = create(res2)
    val res2Context = Context(driver, project, project, res2, null)
    assertTrue(
      configuration.isIgnored(res2Context, ObsoleteLayoutParamsDetector.ISSUE, res2Location, "")
    )
  }

  fun testMessagePatternIgnore() {
    val projectDir = getProjectDir(mOnclick)
    val client: LintClient = createClient()
    val project = Project.create(client, projectDir, projectDir)
    val request = LintRequest(client, emptyList())
    val driver = LintDriver(TestIssueRegistry(), client, request)
    val file = File(projectDir, "res" + separator + "layout" + separator + "onclick.xml")
    assertTrue(file.exists())
    val plainContext = Context(driver, project, project, file, null)
    val location = create(file)
    assertEquals(Severity.WARNING, ObsoleteLayoutParamsDetector.ISSUE.defaultSeverity)
    val configuration =
      getConfiguration(
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <lint>
                <issue id="ObsoleteLayoutParam">
                    <ignore regexp="sample_icon\.gif" />
                    <ignore regexp="javax\.swing" />
                </issue>
            </lint>
            """
          .trimIndent()
      )
    assertFalse(
      configuration.isIgnored(
        plainContext,
        ObsoleteLayoutParamsDetector.ISSUE,
        location,
        "Missing the following drawables in drawable-hdpi: some_random.gif (found in drawable-mdpi)",
      )
    )
    assertTrue(
      configuration.isIgnored(
        plainContext,
        ObsoleteLayoutParamsDetector.ISSUE,
        location,
        "Missing the following drawables in drawable-hdpi: sample_icon.gif (found in drawable-mdpi)",
      )
    )
    assertFalse(
      configuration.isIgnored(
        plainContext,
        ObsoleteLayoutParamsDetector.ISSUE,
        location,
        "Invalid package reference in library; not included in Android: java.awt. Referenced from test.pkg.LibraryClass.",
      )
    )
    assertTrue(
      configuration.isIgnored(
        plainContext,
        ObsoleteLayoutParamsDetector.ISSUE,
        location,
        "Invalid package reference in library; not included in Android: javax.swing. Referenced from test.pkg.LibraryClass.",
      )
    )
  }

  fun testCategories() {
    val configuration =
      getConfiguration(
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <lint>
                <issue id="Interoperability" severity="fatal" />
                <issue id="UnsupportedChromeOsHardware" severity="error" />
                <issue id="Chrome OS" severity="warning" />
                <issue id="ValidActionsXml" severity="error" />
            </lint>
            """
          .trimIndent()
      )
    // Inherit from ChromeOS category
    assertEquals(
      Severity.WARNING,
      configuration.getSeverity(ChromeOsDetector.PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE),
    )
    // Inherit from nested Interoperability category
    assertEquals(
      Severity.FATAL,
      configuration.getSeverity(InteroperabilityDetector.PLATFORM_NULLNESS),
    )
    // Make sure issue which has issue-specific severity uses that instead of inherited category
    assertEquals(
      Severity.ERROR,
      configuration.getSeverity(ChromeOsDetector.UNSUPPORTED_CHROME_OS_HARDWARE),
    )
  }

  fun testWriteLintXml() {
    val configuration =
      getConfiguration(
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <lint lintJars="foo/lint.jar">
              <issue id="ObsoleteLayoutParam">
                  <ignore path="res/layout-xlarge/activation.xml" />
                  <ignore path="res\layout-xlarge\activation2.xml" />
                  <ignore regexp="res/.*/activation2.xml" />
              </issue>
              <issue id="ValidActionsXml" severity="ignore" />
              <issue id="SdCardPath" severity="ignore" /></lint>
            """
          .trimIndent(),
        projectLevel = true,
        create = { f ->
          val lintJar = File(f.parentFile!!, "foo/lint.jar")
          lintJar.parentFile!!.mkdirs()
          lintJar.createNewFile()
        },
      )
    configuration.startBulkEditing()
    configuration.setSeverity(TypoDetector.ISSUE, Severity.ERROR)
    configuration.ignore(TypoDetector.ISSUE, File("foo/bar/Baz.java"))
    configuration.finishBulkEditing()
    val updated = configuration.configFile.readText()
    assertEquals(
      """
            <?xml version="1.0" encoding="UTF-8"?>
            <lint lintJars="foo/lint.jar">
                <issue id="ObsoleteLayoutParam">
                    <ignore path="res/layout-xlarge/activation.xml" />
                    <ignore path="res/layout-xlarge/activation2.xml" />
                    <ignore regexp="res/.*/activation2.xml" />
                </issue>
                <issue id="SdCardPath" severity="ignore" />
                <issue id="Typos" severity="error">
                    <ignore path="foo/bar/Baz.java" />
                </issue>
                <issue id="ValidActionsXml" severity="ignore" />
            </lint>
            """
        .trimIndent(),
      updated,
    )
  }

  private fun getConfiguration(
    xml: String,
    projectLevel: Boolean = false,
    create: (File) -> Unit = {},
    initialDir: File? = null,
  ): LintXmlConfiguration {
    val client: LintClient = createClient()
    val dir = initialDir ?: temporaryFolder.root
    dir.mkdirs()
    val lintFile = File(dir, "lintconfig.xml")
    lintFile.writeText(xml)
    create(lintFile)
    val configuration = client.configurations.getConfigurationForFile(lintFile)
    configuration.fileLevel = !projectLevel
    return configuration as LintXmlConfiguration
  }

  private fun getProjectDir(vararg testFiles: TestFile): File {
    val root = temporaryFolder.root
    lint().files(*testFiles).createProjects(root)
    return File(root, "app")
  }

  override fun getDetector(): Detector {
    // Sample detector
    return SdCardDetector()
  }

  // Tests for a structure that looks like a gradle project with
  // multiple resource folders.
  fun testResourcePathIgnore() {
    val configuration =
      getConfiguration(
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <lint>
              <issue id="ObsoleteLayoutParam">
                  <ignore path="res/layout/onclick.xml" />
                  <ignore path="res/layout-xlarge/activation.xml" />
                  <ignore path="res\layout-xlarge\activation2.xml" />
                  <ignore path="res/layout-land" />
              </issue>
            </lint>
            """
          .trimIndent()
      )
    val projectDir = getProjectDir(mOnclick4, mOnclick5, mOnclick6, mOnclick7)
    val client: LintClient =
      object : TestLintClient() {
        override fun getResourceFolders(project: Project): List<File> {
          return listOf(
            File(project.dir, "src" + separator + "main" + separator + "res"),
            File(project.dir, "generated-res"),
          )
        }
      }
    val project = Project.create(client, projectDir, projectDir)
    val request = LintRequest(client, emptyList())
    val driver = LintDriver(TestIssueRegistry(), client, request)
    // Main resource dir => src/main/res
    val resourceDir = File(projectDir, "src" + separator + "main" + separator + "res")
    val plainFile = File(resourceDir, "layout" + separator + "onclick.xml")
    assertTrue(plainFile.exists())

    // Generated resource dir
    val resourceDir2 = File(projectDir, "generated-res")
    val largeFile = File(resourceDir2, "layout-xlarge" + separator + "activation.xml")
    assertTrue(largeFile.exists())
    val windowsFile = File(resourceDir2, "layout-xlarge" + separator + "activation2.xml")
    assertTrue(windowsFile.exists())
    val landscapeFile = File(resourceDir2, "layout-land" + separator + "foo.xml")
    assertTrue(landscapeFile.exists())
    val plainContext = Context(driver, project, project, plainFile, null)
    val largeContext = Context(driver, project, project, largeFile, null)
    val windowsContext = Context(driver, project, project, windowsFile, null)
    val landscapeContext = Context(driver, project, project, landscapeFile, null)
    val landscapeLocation = create(landscapeFile)
    assertTrue(
      String.format(Locale.US, "File `%s` was not ignored", plainFile.path),
      configuration.isIgnored(
        plainContext,
        ObsoleteLayoutParamsDetector.ISSUE,
        create(plainFile),
        "",
      ),
    )
    assertTrue(
      configuration.isIgnored(
        largeContext,
        ObsoleteLayoutParamsDetector.ISSUE,
        create(largeFile),
        "",
      )
    )
    assertTrue(
      configuration.isIgnored(
        windowsContext,
        ObsoleteLayoutParamsDetector.ISSUE,
        create(windowsFile),
        "",
      )
    )
    // directory allowlist
    assertTrue(
      configuration.isIgnored(
        landscapeContext,
        ObsoleteLayoutParamsDetector.ISSUE,
        landscapeLocation,
        "",
      )
    )
  }

  fun testErrorHandling() {
    // Checks that lint.xml can be applied in different folders in a hierarchical way
    val invalidXml =
      // language=TEXT since this is broken source
      """
            <?xml version="1.0" encoding="UTF-8"?>
            <lint
                <issue id="SdCardPath" severity="ignore" />
            </lint>
            """
        .trimIndent()
    lint()
      .files(
        kotlin(
            """
                package test.pkg1.subpkg1

                class MyTest {
                    val s: String = "/sdcard/mydir"
                }
                """
          )
          .indented(),
        xml(
            // parsing error
            "src/main/kotlin/test/lint.xml",
            invalidXml,
          )
          .indented(),
        xml( // missing id attribute
            "src/main/kotlin/test/pkg1/lint.xml",
            """
                <!-- lint config file -->
                <lint lintJars='notallowed.jar'>
                    <!-- line before -->
                    <issue severity="error">
                        <ignore enabled='false' />
                        <unsupported />
                        <option name="only_name" desc="desc" />
                    </issue>
                    <!-- line after -->
                    <issue id="SdCardPath" severity="warrning" />
                    <ignore />
                    <option />
                    <issue id="NonexistentId" severity="fatal" other="other" />
                    <issue id="Correctness" severity="warning" />
                </lint>
                """,
          )
          .indented(),
        // Trigger src/main/java source sets
        gradle(""),
      )
      .skipTestModes(TestMode.PARTIAL)
      .allowCompilationErrors()
      .run()
      .expect(
        """
            src/main/kotlin/test/pkg1/lint.xml:2: Warning: lintJar can only be specified for lint.xml files at the module level or higher [LintWarning]
            <lint lintJars='notallowed.jar'>
            ^
            src/main/kotlin/test/lint.xml:3: Warning: Failed parsing lint.xml: name expected [LintWarning]
                <issue id="SdCardPath" severity="ignore" />
                ^
            src/main/kotlin/test/pkg1/lint.xml:4: Warning: Missing required issue id attribute [LintWarning]
                <issue severity="error">
                ^
            src/main/kotlin/test/pkg1/lint.xml:5: Warning: Missing required attribute path or regexp [LintWarning]
                    <ignore enabled='false' />
                    ^
            src/main/kotlin/test/pkg1/lint.xml:5: Warning: Unexpected attribute enabled, expected path or regexp [LintWarning]
                    <ignore enabled='false' />
                    ^
            src/main/kotlin/test/pkg1/lint.xml:6: Warning: Unsupported tag <unsupported>, expected one of lint, issue, ignore or option [LintWarning]
                    <unsupported />
                    ^
            src/main/kotlin/test/pkg1/lint.xml:7: Warning: Must specify both name and value in <option> [LintWarning]
                    <option name="only_name" desc="desc" />
                    ^
            src/main/kotlin/test/pkg1/lint.xml:7: Warning: Unexpected attribute desc, expected name or value [LintWarning]
                    <option name="only_name" desc="desc" />
                    ^
            src/main/kotlin/test/pkg1/lint.xml:10: Warning: Unknown severity warrning [LintWarning]
                <issue id="SdCardPath" severity="warrning" />
                ^
            src/main/kotlin/test/pkg1/lint.xml:11: Warning: <ignore> tag should be nested within <issue> [LintWarning]
                <ignore />
                ^
            src/main/kotlin/test/pkg1/lint.xml:12: Warning: <option> tag should be nested within <issue> [LintWarning]
                <option />
                ^
            src/main/kotlin/test/pkg1/lint.xml:13: Warning: Unexpected attribute other, expected id, in or severity [LintWarning]
                <issue id="NonexistentId" severity="fatal" other="other" />
                ^
            src/main/kotlin/test/pkg1/subpkg1/MyTest.kt:4: Warning: Do not hardcode "/sdcard/"; use Environment.getExternalStorageDirectory().getPath() instead [SdCardPath]
                val s: String = "/sdcard/mydir"
                                 ~~~~~~~~~~~~~
            0 errors, 13 warnings
            """
      )
  }

  fun testSeverityImpliesIgnore() {
    val projectDir =
      getProjectDir(
        image("src/main/res/drawable/abc.png", 48, 48),
        source("build/generated/R.java", "class R { };"),
      )
    // Let's say you ignore a specific path for "all". If you also
    // deliberately enable a check in the same lint.xml file, that
    // should act as an "unignore", so you'll have to specifically
    // repeat the ignore for that path
    val configuration =
      getConfiguration(
        """
            <lint>
                <issue id="all">
                    <ignore path="src/"/>
                 </issue>
                <issue id="SdCardPath" severity="error"/>
                <issue id="UnusedResources" severity="error">
                    <ignore path="src/" />
                </issue>
            </lint>
            """
          .trimIndent(),
        // Place the configuration file in the project directory to make sure that
        // relative paths are resolved relative to the config file
        initialDir = projectDir,
      )
    val client: LintClient = TestLintClient()
    val project = Project.create(client, projectDir, projectDir)
    val request = LintRequest(client, emptyList())
    val driver = LintDriver(TestIssueRegistry(), client, request)
    val drawable = File(projectDir, "src/main/res/drawable/abc.png")
    assertTrue(drawable.exists())
    val generatedR = File(projectDir, "build/generated/R.java")
    assertTrue(generatedR.exists())
    val drawableContext = Context(driver, project, project, drawable, null)
    val generatedRContext = Context(driver, project, project, generatedR, null)
    assertTrue(
      configuration.isIgnored(
        drawableContext,
        ObsoleteLayoutParamsDetector.ISSUE,
        create(drawable),
        "",
      )
    )
    assertFalse(
      configuration.isIgnored(
        generatedRContext,
        ObsoleteLayoutParamsDetector.ISSUE,
        create(generatedR),
        "",
      )
    )
    assertFalse(
      configuration.isIgnored(drawableContext, SdCardDetector.ISSUE, create(drawable), "")
    )
    assertTrue(
      configuration.isIgnored(drawableContext, UnusedResourceDetector.ISSUE, create(drawable), "")
    )
  }

  fun testIgnoreRelativePath() {
    val projectDir =
      getProjectDir(
        image("src/main/res/drawable/abc.png", 48, 48),
        source("build/generated/R.java", "class R { };"),
      )
    // Let's say you ignore a specific path for "all". If you also
    // deliberately enable a check in the same lint.xml file, that
    // should act as an "unignore", so you'll have to specifically
    // repeat the ignore for that path
    val configuration =
      getConfiguration(
        """
            <lint>
                <issue id="ObsoleteLayoutParam" severity="error">
                    <ignore path="drawable/" />
                </issue>
            </lint>
            """
          .trimIndent(),
        // Place the configuration file in somewhere in the project to make sure
        // relative paths are resolved relative to the config file
        initialDir = File(projectDir, "src/main/res"),
      )
    val client: LintClient = TestLintClient()
    val project = Project.create(client, projectDir, projectDir)
    val request = LintRequest(client, emptyList())
    val driver = LintDriver(TestIssueRegistry(), client, request)
    val drawable = File(projectDir, "src/main/res/drawable/abc.png")
    assertTrue(drawable.exists())
    val generatedR = File(projectDir, "build/generated/R.java")
    assertTrue(generatedR.exists())
    val drawableContext = Context(driver, project, project, drawable, null)
    val generatedRContext = Context(driver, project, project, generatedR, null)
    assertTrue(
      configuration.isIgnored(
        drawableContext,
        ObsoleteLayoutParamsDetector.ISSUE,
        create(drawable),
        "",
      )
    )
    assertFalse(
      configuration.isIgnored(
        generatedRContext,
        ObsoleteLayoutParamsDetector.ISSUE,
        create(generatedR),
        "",
      )
    )
  }

  fun testWriteConfig() {
    // Make sure that when we have a placeholder configuration in a project (which we
    // expect at the root of a project which does not specify a lint.xml), attempting to
    // update a configuration setting will create and write a file in that directory
    // as we expect
    checkConfiguration(
      check = { _, project, configuration ->
        assertNull(configuration.parent)
        assertFalse(configuration.fileLevel)
        assertEquals("ProjectPlaceholderConfiguration", configuration.javaClass.simpleName)
        val dir = configuration.dir
        println(dir)
        assertNull(configuration.getDefinedSeverity(ApiDetector.UNSUPPORTED))
        val file = File(project.dir, "lint.xml")
        assertFalse(file.exists())
        configuration.setSeverity(ApiDetector.UNSUPPORTED, Severity.FATAL)
        configuration.ignore(IconDetector.DUPLICATES_NAMES, File("name.xml"))
        assertSame(Severity.FATAL, configuration.getDefinedSeverity(ApiDetector.UNSUPPORTED))
        assertEquals(
          """
                <?xml version="1.0" encoding="UTF-8"?>
                <lint>
                    <issue id="IconDuplicates">
                        <ignore path="name.xml" />
                    </issue>
                    <issue id="NewApi" severity="fatal" />
                </lint>
                    """
            .trimIndent(),
          file.readText(),
        )
      }
    )
  }

  fun testVirtualConfigFile1() {
    // Construct lint XML configuration for a virtual file directly
    @Language("XML")
    val contents: CharSequence =
      """
            <?xml version="1.0" encoding="UTF-8"?>
            <lint>
                <issue id="IconDuplicates" severity="ignore"/>
                <issue id="NewApi" severity="fatal" />
            </lint>
        """
        .trimIndent()
    val file = File("/tmp/nonexistent")
    val client = TestLintClient()

    val configuration = LintXmlConfiguration.create(client.configurations, file, contents)
    assertEquals(Severity.FATAL, configuration.getSeverity(ApiDetector.UNSUPPORTED))
    assertEquals(Severity.IGNORE, configuration.getSeverity(IconDetector.DUPLICATES_NAMES))
  }

  fun testVirtualConfigFile2() {
    // Construct lint XML configuration via a custom lint client which overrides the known
    // path handle. This checks that the configuration machinery uses the LintClient
    // I/O abstractions; this is useful for configurations inserted into the middle
    // of the hierarchy, not just global configurations.
    // See issue 266017093: Allow for LintXmlConfiguration creation without a real File
    @Language("XML")
    val contents =
      """
            <?xml version="1.0" encoding="UTF-8"?>
            <lint>
                <issue id="IconDuplicates" severity="ignore"/>
                <issue id="NewApi" severity="fatal" />
            </lint>
        """
        .trimIndent()

    val configFile = File("/tmp/nonexistent")
    fun isConfigFile(file: File): Boolean = file.path.equals(configFile.path, ignoreCase = true)
    val client =
      object : TestLintClient() {
        override fun fileExists(
          file: File,
          requireFile: Boolean,
          requireDirectory: Boolean,
        ): Boolean {
          if (isConfigFile(file)) {
            return true
          }
          return super.fileExists(file)
        }

        override fun readBytes(file: File): ByteArray {
          if (isConfigFile(file)) {
            return contents.toByteArray(Charsets.UTF_8)
          }
          return super.readBytes(file)
        }
      }

    val configuration = LintXmlConfiguration.create(client.configurations, configFile)
    assertEquals(Severity.FATAL, configuration.getSeverity(ApiDetector.UNSUPPORTED))
    assertEquals(Severity.IGNORE, configuration.getSeverity(IconDetector.DUPLICATES_NAMES))
  }

  /**
   * Test support for reading and writing configurations; this builds on top of a normal lint run in
   * order to make it easy to set up configurations etc without having to manually configure a lot
   * of state, as used to be the case.
   */
  private fun checkConfiguration(
    projectName: String? = null,
    check: (LintDriver, Project, Configuration) -> Unit = { _, _, _ -> },
    expected: String = "No warnings.",
    vararg testFiles: TestFile,
  ) {
    lint()
      .files(
        *testFiles,
        // To make sure we have at least one project
        kotlin(
            """
                package test.pkg1.subpkg1
                class PlaceholderFile
                """
          )
          .indented(),
        // Trigger src/main/java source sets
        gradle(""),
      )
      .clientFactory {
        object : com.android.tools.lint.checks.infrastructure.TestLintClient() {
          override fun getConfiguration(project: Project, driver: LintDriver?): Configuration {
            // Make sure we don't pick up the special TestConfiguration; we want the
            // real configuration lint would create in production
            return configurations.getConfigurationForProject(project)
          }
        }
      }
      .checkProjects(
        object : TestLintTask.ProjectInspector {
          override fun inspect(driver: LintDriver, knownProjects: List<Project>) {
            val project =
              knownProjects.find { it.name == projectName || projectName == null }
                ?: run {
                  if (projectName != null) {
                    assertNotNull("Did not find project $projectName")
                  } else {
                    fail("Did not find any projects")
                  }
                  error("Can't reach here")
                }

            val configuration = project.getConfiguration(driver)
            check(driver, project, configuration)
          }
        }
      )
      .testModes(TestMode.DEFAULT)
      .run()
      .expect(expected)
  }

  private val mOnclick = xml("res/layout/onclick.xml", LAYOUT_XML)
  private val mOnclick2 = xml("res/layout-xlarge/onclick.xml", LAYOUT_XML)
  private val mOnclick3 = xml("res/layout-xlarge/activation.xml", LAYOUT_XML)
  private val mOnclick4 = xml("src/main/res/layout/onclick.xml", LAYOUT_XML)
  private val mOnclick5 = xml("generated-res/layout-xlarge/activation.xml", LAYOUT_XML)
  private val mOnclick6 = xml("generated-res/layout-xlarge/activation2.xml", LAYOUT_XML)
  private val mOnclick7 = xml("generated-res/layout-land/foo.xml", LAYOUT_XML)

  companion object {
    // Sample code
    @Language("XML")
    private val LAYOUT_XML =
      """

            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical" />
            """
        .trimIndent()
  }
}
