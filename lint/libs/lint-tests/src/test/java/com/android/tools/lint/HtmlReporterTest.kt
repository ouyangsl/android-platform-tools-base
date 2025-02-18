/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.testutils.TestUtils
import com.android.tools.lint.HtmlReporter.Companion.REPORT_PREFERENCE_PROPERTY
import com.android.tools.lint.checks.BuiltinIssueRegistry
import com.android.tools.lint.checks.DuplicateResourceDetector
import com.android.tools.lint.checks.HardcodedValuesDetector
import com.android.tools.lint.checks.IconDetector
import com.android.tools.lint.checks.InteroperabilityDetector
import com.android.tools.lint.checks.LogDetector
import com.android.tools.lint.checks.ManifestDetector
import com.android.tools.lint.checks.SdCardDetector
import com.android.tools.lint.checks.infrastructure.TestFiles.image
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.manifest
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.lint.checks.infrastructure.TestLintClient
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.checks.infrastructure.TestResultTransformer
import com.android.tools.lint.checks.infrastructure.dos2unix
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.client.api.LintRequest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestName

class HtmlReporterTest {
  @get:Rule val testName = TestName()

  @get:Rule var temporaryFolder = TemporaryFolder()

  private fun checkReportOutput(expected: String) {
    val rootDirectory = temporaryFolder.newFolder().canonicalFile
    val lint = TestLintTask.lint()
    val factory: () -> TestLintClient = {
      val client =
        object : TestLintClient() {
          override fun createDriver(registry: IssueRegistry, request: LintRequest): LintDriver {
            // Temporarily switch HardcodedValuesDetector.ISSUE to a custom
            // registry with an example vendor to test output of vendor info
            // (which we normally omit for built-in checks).
            // This also tests that it's listed in the "included additional" section.
            val testVendor = createTestVendor()
            HardcodedValuesDetector.ISSUE.vendor = testVendor
            // Also include a *disabled* extra issue, to make sure we don't
            // include these in the extra list (and that we *do* include
            // them in the disabled list.)
            SdCardDetector.ISSUE.vendor = testVendor
            SdCardDetector.ISSUE.setEnabledByDefault(false)

            return super.createDriver(registry, request)
          }
        }
      client.setLintTask(lint)
      client.flags.enabledIds.add(LogDetector.CONDITIONAL.id)
      client.flags.suppressedIds.add(ManifestDetector.MOCK_LOCATION.id)
      client.flags.isFullPath = false
      client.pathVariables.clear()
      client.pathVariables.add("TEST_ROOT", rootDirectory)
      client
    }

    val transformer = TestResultTransformer { output ->
      var report: String
      // Replace the timestamp to make golden file comparison work
      val timestampPrefix = "Check performed at "
      var begin = output.indexOf(timestampPrefix)
      assertTrue(begin != -1)
      begin += timestampPrefix.length
      val end = output.indexOf(" by ", begin)
      assertTrue(end != -1)
      report = output.substring(0, begin) + "\$DATE" + output.substring(end)

      report = report.dos2unix()

      // There's some (single) trailing space in the output, but
      // the IDE strips it out of the expected output's raw string literal:
      report = report.replace(" \n", "\n")

      report
    }

    val testName = javaClass.simpleName + "_" + testName.methodName
    lint
      // Set a custom directory such that lint doesn't delete the source directory after a run;
      // we need to access it after the test run for syntax highlighting in the reporting pass.
      .rootDirectory(rootDirectory)
      .testName(testName)
      .sdkHome(TestUtils.getSdk().toFile())
      .files(
        manifest(
            """
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="test.pkg">
                        <uses-sdk android:minSdkVersion="10" android:targetSdkVersion="31" />
                        <uses-sdk android:minSdkVersion="10" android:targetSdkVersion="31" />
                    </manifest>
                    """
          )
          .indented(),
        xml(
            "res/layout/main.xml",
            """
                    <Button xmlns:android="http://schemas.android.com/apk/res/android"
                        android:id="@+id/button1"
                        android:text="Fooo" />
                    """,
          )
          .indented(),
        xml(
            "res/layout/main2.xml",
            """
                    <Button xmlns:android="http://schemas.android.com/apk/res/android"
                        android:id="@+id/button1"
                        android:text="Bar" />
                    """,
          )
          .indented(),
        xml(
            "res/values/strings.xml",
            """
                    <resources>
                        <string name="app_name">App Name</string>
                    </resources>
                    """,
          )
          .indented(),
        xml(
            "res/values/strings2.xml",
            """
                    <resources>
                        <string name="app_name">App Name</string>
                    </resources>
                    """,
          )
          .indented(),
        image("res/drawable-hdpi/icon1.png", 48, 48).fill(-0xff00d7),
        image("res/drawable-hdpi/icon2.png", 49, 49).fill(-0xff00d7),
        image("res/drawable-hdpi/icon3.png", 49, 49).fill(-0xff00d7),
        image("res/drawable-hdpi/icon4.png", 49, 49).fill(-0xff00d7),
        java(
            """
                    package other.pkg;
                    public class AnnotationTest {
                        public Float error4;
                    }
                    """
          )
          .indented(),
      )
      .issues(
        ManifestDetector.MULTIPLE_USES_SDK,
        HardcodedValuesDetector.ISSUE,
        SdCardDetector.ISSUE,
        IconDetector.DUPLICATES_NAMES,
        // Not reported, but for the disabled-list
        ManifestDetector.MOCK_LOCATION,
        // Not reported, but disabled by default and enabled via flags (b/111035260)
        LogDetector.CONDITIONAL,
        // Issue which reports multiple linked locations to test the nested display
        // and secondary location offsets
        DuplicateResourceDetector.ISSUE,
        InteroperabilityDetector.PLATFORM_NULLNESS,
      )
      .clientFactory(factory)
      .testModes(TestMode.DEFAULT)
      .run()
      .expectHtml(expected, transformer)
    HardcodedValuesDetector.ISSUE.vendor = BuiltinIssueRegistry().vendor
    SdCardDetector.ISSUE.vendor = BuiltinIssueRegistry().vendor
    SdCardDetector.ISSUE.setEnabledByDefault(true)
  }

  @Test
  fun testBasic() {
    // NOTE: If you change the output, please validate it manually in
    //  http://validator.w3.org/#validate_by_input
    // before updating the following

    checkReportOutput(
      """<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">

<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<title>Lint Report</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/icon?family=Material+Icons">
 <link rel="stylesheet" href="https://code.getmdl.io/1.2.1/material.blue-indigo.min.css" />
<link rel="stylesheet" href="http://fonts.googleapis.com/css?family=Roboto:300,400,500,700" type="text/css">
<script defer src="https://code.getmdl.io/1.2.0/material.min.js"></script>
<style>
${HtmlReporter.cssStyles}</style>
<script language="javascript" type="text/javascript">
<!--
function reveal(id) {
if (document.getElementById) {
document.getElementById(id).style.display = 'block';
document.getElementById(id+'Link').style.display = 'none';
}
}
function hideid(id) {
if (document.getElementById) {
document.getElementById(id).style.display = 'none';
}
}
//-->
</script>
</head>
<body class="mdl-color--grey-100 mdl-color-text--grey-700 mdl-base">
<div class="mdl-layout mdl-js-layout mdl-layout--fixed-header">
  <header class="mdl-layout__header">
    <div class="mdl-layout__header-row">
      <span class="mdl-layout-title">Lint Report: 2 errors and 4 warnings</span>
      <div class="mdl-layout-spacer"></div>
      <nav class="mdl-navigation mdl-layout--large-screen-only">Check performed at ＄DATE by Lint Unit Tests</nav>
    </div>
  </header>
  <div class="mdl-layout__drawer">
    <span class="mdl-layout-title">Issue Types</span>
    <nav class="mdl-navigation">
      <a class="mdl-navigation__link" href="#overview"><i class="material-icons">dashboard</i>Overview</a>
      <a class="mdl-navigation__link" href="#DuplicateDefinition"><i class="material-icons error-icon">error</i>Duplicate definitions of resources (1)</a>
      <a class="mdl-navigation__link" href="#MultipleUsesSdk"><i class="material-icons error-icon">error</i>Multiple <code>&lt;uses-sdk></code> elements in the manifest (1)</a>
      <a class="mdl-navigation__link" href="#IconDuplicates"><i class="material-icons warning-icon">warning</i>Duplicated icons under different names (1)</a>
      <a class="mdl-navigation__link" href="#HardcodedText"><i class="material-icons warning-icon">warning</i>Hardcoded text (2)</a>
      <a class="mdl-navigation__link" href="#UnknownNullness"><i class="material-icons warning-icon">warning</i>Unknown nullness (1)</a>
    </nav>
  </div>
  <main class="mdl-layout__content">
    <div class="mdl-layout__tab-panel is-active">
<a name="overview"></a>
<section class="section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp" id="OverviewCard" style="display: block;">
            <div class="mdl-card mdl-cell mdl-cell--12-col">
  <div class="mdl-card__title">
    <h2 class="mdl-card__title-text">Overview</h2>
  </div>
              <div class="mdl-card__supporting-text">
<table class="overview">
<tr><td class="countColumn"></td><td class="categoryColumn"><a href="#Correctness">Correctness</a>
</td></tr>
<tr>
<td class="countColumn">1</td><td class="issueColumn"><i class="material-icons error-icon">error</i>
<a href="#DuplicateDefinition">DuplicateDefinition</a>: Duplicate definitions of resources</td></tr>
<tr>
<td class="countColumn">1</td><td class="issueColumn"><i class="material-icons error-icon">error</i>
<a href="#MultipleUsesSdk">MultipleUsesSdk</a>: Multiple <code>&lt;uses-sdk></code> elements in the manifest</td></tr>
<tr><td class="countColumn"></td><td class="categoryColumn"><a href="#Usability:Icons">Usability:Icons</a>
</td></tr>
<tr>
<td class="countColumn">1</td><td class="issueColumn"><i class="material-icons warning-icon">warning</i>
<a href="#IconDuplicates">IconDuplicates</a>: Duplicated icons under different names</td></tr>
<tr><td class="countColumn"></td><td class="categoryColumn"><a href="#Internationalization">Internationalization</a>
</td></tr>
<tr>
<td class="countColumn">2</td><td class="issueColumn"><i class="material-icons warning-icon">warning</i>
<a href="#HardcodedText">HardcodedText</a>: Hardcoded text</td></tr>
<tr><td class="countColumn"></td><td class="categoryColumn"><a href="#Interoperability:Kotlin Interoperability">Interoperability:Kotlin Interoperability</a>
</td></tr>
<tr>
<td class="countColumn">1</td><td class="issueColumn"><i class="material-icons warning-icon">warning</i>
<a href="#UnknownNullness">UnknownNullness</a>: Unknown nullness</td></tr>
<tr><td></td><td class="categoryColumn"><a href="#ExtraIssues">Included Additional Checks (1)</a>
</td></tr>
<tr><td></td><td class="categoryColumn"><a href="#MissingIssues">Disabled Checks (2)</a>
</td></tr>
</table>
<br/>              </div>
              <div class="mdl-card__actions mdl-card--border">
<button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="OverviewCardLink" onclick="hideid('OverviewCard');">
Dismiss</button>            </div>
            </div>
          </section>
<a name="Correctness"></a>
<a name="DuplicateDefinition"></a>
<section class="section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp" id="DuplicateDefinitionCard" style="display: block;">
            <div class="mdl-card mdl-cell mdl-cell--12-col">
  <div class="mdl-card__title">
    <h2 class="mdl-card__title-text">Duplicate definitions of resources</h2>
  </div>
              <div class="mdl-card__supporting-text">
<div class="issue">
<div class="warningslist">
<span class="location"><a href="app/res/values/strings2.xml">res/values/strings2.xml</a>:2</span>: <span class="message"><code>app_name</code> has already been defined in this folder</span><br /><pre class="errorlines">
<span class="lineno"> 1 </span><span class="tag">&lt;resources></span>
<span class="caretline"><span class="lineno"> 2 </span>    <span class="tag">&lt;string</span><span class="attribute"> </span><span class="error"><span class="attribute">name</span>=<span class="value">"app_name"</span></span>>App Name<span class="tag">&lt;/string></span>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span>
<span class="lineno"> 3 </span><span class="tag">&lt;/resources></span></pre>

<ul><span class="location"><a href="app/res/values/strings.xml">res/values/strings.xml</a>:2</span>: <span class="message">Previously defined here</span><br /><pre class="errorlines">
<span class="lineno"> 1 </span><span class="tag">&lt;resources></span>
<span class="caretline"><span class="lineno"> 2 </span>    <span class="tag">&lt;string</span><span class="attribute"> </span><span class="error"><span class="attribute">name</span>=<span class="value">"app_name"</span></span>>App Name<span class="tag">&lt;/string></span>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span>
<span class="lineno"> 3 </span><span class="tag">&lt;/resources></span></pre>
</ul></div>
<div class="metadata"><div class="explanation" id="explanationDuplicateDefinition" style="display: none;">
You can define a resource multiple times in different resource folders; that's how string translations are done, for example. However, defining the same resource more than once in the same resource folder is likely an error, for example attempting to add a new resource without realizing that the name is already used, and so on.<br/>To suppress this error, use the issue id "DuplicateDefinition" as explained in the <a href="#SuppressInfo">Suppressing Warnings and Errors</a> section.<br/>
<br/></div>
</div>
</div>
<div class="chips">
<span class="mdl-chip">
    <span class="mdl-chip__text">DuplicateDefinition</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Correctness</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Error</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Priority 6/10</span>
</span>
</div>
              </div>
              <div class="mdl-card__actions mdl-card--border">
<button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="explanationDuplicateDefinitionLink" onclick="reveal('explanationDuplicateDefinition');">
Explain</button><button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="DuplicateDefinitionCardLink" onclick="hideid('DuplicateDefinitionCard');">
Dismiss</button>            </div>
            </div>
          </section><a name="MultipleUsesSdk"></a>
<section class="section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp" id="MultipleUsesSdkCard" style="display: block;">
            <div class="mdl-card mdl-cell mdl-cell--12-col">
  <div class="mdl-card__title">
    <h2 class="mdl-card__title-text">Multiple &lt;uses-sdk> elements in the manifest</h2>
  </div>
              <div class="mdl-card__supporting-text">
<div class="issue">
<div class="warningslist">
<span class="location"><a href="app/AndroidManifest.xml">AndroidManifest.xml</a>:4</span>: <span class="message">There should only be a single <code>&lt;uses-sdk></code> element in the manifest: merge these together</span><br /><pre class="errorlines">
<span class="lineno"> 1 </span><span class="tag">&lt;manifest</span><span class="attribute"> </span><span class="prefix">xmlns:</span><span class="attribute">android</span>=<span class="value">"http://schemas.android.com/apk/res/android"</span>
<span class="lineno"> 2 </span>    <span class="attribute">package</span>=<span class="value">"test.pkg"</span>>
<span class="lineno"> 3 </span>    <span class="tag">&lt;uses-sdk</span><span class="attribute"> </span><span class="prefix">android:</span><span class="attribute">minSdkVersion</span>=<span class="value">"10"</span> <span class="prefix">android:</span><span class="attribute">targetSdkVersion</span>=<span class="value">"31"</span> />
<span class="caretline"><span class="lineno"> 4 </span>    <span class="tag">&lt;</span><span class="error"><span class="tag">uses-sdk</span></span><span class="attribute"> </span><span class="prefix">android:</span><span class="attribute">minSdkVersion</span>=<span class="value">"10"</span> <span class="prefix">android:</span><span class="attribute">targetSdkVersion</span>=<span class="value">"31"</span> />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span>
<span class="lineno"> 5 </span><span class="tag">&lt;/manifest></span></pre>

<ul><span class="location"><a href="app/AndroidManifest.xml">AndroidManifest.xml</a>:3</span>: <span class="message">Also appears here</span><br /><pre class="errorlines">
<span class="lineno"> 1 </span><span class="tag">&lt;manifest</span><span class="attribute"> </span><span class="prefix">xmlns:</span><span class="attribute">android</span>=<span class="value">"http://schemas.android.com/apk/res/android"</span>
<span class="lineno"> 2 </span>    <span class="attribute">package</span>=<span class="value">"test.pkg"</span>>
<span class="caretline"><span class="lineno"> 3 </span>    <span class="tag">&lt;</span><span class="error"><span class="tag">uses-sdk</span></span><span class="attribute"> </span><span class="prefix">android:</span><span class="attribute">minSdkVersion</span>=<span class="value">"10"</span> <span class="prefix">android:</span><span class="attribute">targetSdkVersion</span>=<span class="value">"31"</span> />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span>
<span class="lineno"> 4 </span>    <span class="tag">&lt;uses-sdk</span><span class="attribute"> </span><span class="prefix">android:</span><span class="attribute">minSdkVersion</span>=<span class="value">"10"</span> <span class="prefix">android:</span><span class="attribute">targetSdkVersion</span>=<span class="value">"31"</span> />
<span class="lineno"> 5 </span><span class="tag">&lt;/manifest></span></pre>
</ul></div>
<div class="metadata"><div class="explanation" id="explanationMultipleUsesSdk" style="display: none;">
The <code>&lt;uses-sdk></code> element should appear just once; the tools will <b>not</b> merge the contents of all the elements so if you split up the attributes across multiple elements, only one of them will take effect. To fix this, just merge all the attributes from the various elements into a single &lt;uses-sdk> element.<br/><div class="moreinfo">More info: <a href="https://developer.android.com/guide/topics/manifest/uses-sdk-element.html">https://developer.android.com/guide/topics/manifest/uses-sdk-element.html</a>
</div>To suppress this error, use the issue id "MultipleUsesSdk" as explained in the <a href="#SuppressInfo">Suppressing Warnings and Errors</a> section.<br/>
<br/></div>
</div>
</div>
<div class="chips">
<span class="mdl-chip">
    <span class="mdl-chip__text">MultipleUsesSdk</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Correctness</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Error</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Priority 6/10</span>
</span>
</div>
              </div>
              <div class="mdl-card__actions mdl-card--border">
<button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="explanationMultipleUsesSdkLink" onclick="reveal('explanationMultipleUsesSdk');">
Explain</button><button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="MultipleUsesSdkCardLink" onclick="hideid('MultipleUsesSdkCard');">
Dismiss</button>            </div>
            </div>
          </section>
<a name="Usability:Icons"></a>
<a name="IconDuplicates"></a>
<section class="section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp" id="IconDuplicatesCard" style="display: block;">
            <div class="mdl-card mdl-cell mdl-cell--12-col">
  <div class="mdl-card__title">
    <h2 class="mdl-card__title-text">Duplicated icons under different names</h2>
  </div>
              <div class="mdl-card__supporting-text">
<div class="issue">
<div class="warningslist">
<span class="location"><a href="app/res/drawable-hdpi/icon4.png">res/drawable-hdpi/icon4.png</a></span>: <span class="message">The following unrelated icon files have identical contents: icon2.png, icon3.png, icon4.png</span><br />
<ul><span class="location"><a href="app/res/drawable-hdpi/icon3.png">res/drawable-hdpi/icon3.png</a></span>: <span class="message">&lt;No location-specific message></span><br /><span class="location"><a href="app/res/drawable-hdpi/icon2.png">res/drawable-hdpi/icon2.png</a></span>: <span class="message">&lt;No location-specific message></span><br /></ul><table>
<tr><td><a href="app/res/drawable-hdpi/icon2.png"><img border="0" align="top" src="app/res/drawable-hdpi/icon2.png" /></a>
</td><td><a href="app/res/drawable-hdpi/icon3.png"><img border="0" align="top" src="app/res/drawable-hdpi/icon3.png" /></a>
</td><td><a href="app/res/drawable-hdpi/icon4.png"><img border="0" align="top" src="app/res/drawable-hdpi/icon4.png" /></a>
</td></tr><tr><th>hdpi</th><th>hdpi</th><th>hdpi</th></tr>
</table>
</div>
<div class="metadata"><div class="explanation" id="explanationIconDuplicates" style="display: none;">
If an icon is repeated under different names, you can consolidate and just use one of the icons and delete the others to make your application smaller. However, duplicated icons usually are not intentional and can sometimes point to icons that were accidentally overwritten or accidentally not updated.<br/>To suppress this error, use the issue id "IconDuplicates" as explained in the <a href="#SuppressInfo">Suppressing Warnings and Errors</a> section.<br/>
<br/></div>
</div>
</div>
<div class="chips">
<span class="mdl-chip">
    <span class="mdl-chip__text">IconDuplicates</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Icons</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Usability</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Warning</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Priority 3/10</span>
</span>
</div>
              </div>
              <div class="mdl-card__actions mdl-card--border">
<button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="explanationIconDuplicatesLink" onclick="reveal('explanationIconDuplicates');">
Explain</button><button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="IconDuplicatesCardLink" onclick="hideid('IconDuplicatesCard');">
Dismiss</button>            </div>
            </div>
          </section>
<a name="Internationalization"></a>
<a name="HardcodedText"></a>
<section class="section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp" id="HardcodedTextCard" style="display: block;">
            <div class="mdl-card mdl-cell mdl-cell--12-col">
  <div class="mdl-card__title">
    <h2 class="mdl-card__title-text">Hardcoded text</h2>
  </div>
              <div class="mdl-card__supporting-text">
<div class="issue">
<div class="warningslist">
<span class="location"><a href="app/res/layout/main.xml">res/layout/main.xml</a>:3</span>: <span class="message">Hardcoded string "Fooo", should use <code>@string</code> resource</span><br /><pre class="errorlines">
<span class="lineno"> 1 </span><span class="tag">&lt;Button</span><span class="attribute"> </span><span class="prefix">xmlns:</span><span class="attribute">android</span>=<span class="value">"http://schemas.android.com/apk/res/android"</span>
<span class="lineno"> 2 </span>    <span class="prefix">android:</span><span class="attribute">id</span>=<span class="value">"@+id/button1"</span>
<span class="caretline"><span class="lineno"> 3 </span>    <span class="warning"><span class="prefix">android:</span><span class="attribute">text</span>=<span class="value">"Fooo"</span></span> />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span></pre>

<span class="location"><a href="app/res/layout/main2.xml">res/layout/main2.xml</a>:3</span>: <span class="message">Hardcoded string "Bar", should use <code>@string</code> resource</span><br /><pre class="errorlines">
<span class="lineno"> 1 </span><span class="tag">&lt;Button</span><span class="attribute"> </span><span class="prefix">xmlns:</span><span class="attribute">android</span>=<span class="value">"http://schemas.android.com/apk/res/android"</span>
<span class="lineno"> 2 </span>    <span class="prefix">android:</span><span class="attribute">id</span>=<span class="value">"@+id/button1"</span>
<span class="caretline"><span class="lineno"> 3 </span>    <span class="warning"><span class="prefix">android:</span><span class="attribute">text</span>=<span class="value">"Bar"</span></span> />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span></pre>

</div>
<div class="metadata"><div class="explanation" id="explanationHardcodedText" style="display: none;">
Hardcoding text attributes directly in layout files is bad for several reasons:<br/>
<br/>
* When creating configuration variations (for example for landscape or portrait) you have to repeat the actual text (and keep it up to date when making changes)<br/>
<br/>
* The application cannot be translated to other languages by just adding new translations for existing string resources.<br/>
<br/>
There are quickfixes to automatically extract this hardcoded string into a resource lookup.<br/>To suppress this error, use the issue id "HardcodedText" as explained in the <a href="#SuppressInfo">Suppressing Warnings and Errors</a> section.<br/>
<br/></div>
</div>
</div>
<div class="vendor">
Vendor: AOSP Unit Tests<br/>
Identifier: mylibrary-1.0<br/>
Contact: lint@example.com<br/>
Feedback: <a href="https://example.com/lint/file-new-bug.html">https://example.com/lint/file-new-bug.html</a><br/>
</div>
<div class="chips">
<span class="mdl-chip">
    <span class="mdl-chip__text">HardcodedText</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Internationalization</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Warning</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Priority 5/10</span>
</span>
</div>
              </div>
              <div class="mdl-card__actions mdl-card--border">
<button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="explanationHardcodedTextLink" onclick="reveal('explanationHardcodedText');">
Explain</button><button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="HardcodedTextCardLink" onclick="hideid('HardcodedTextCard');">
Dismiss</button>            </div>
            </div>
          </section>
<a name="Interoperability:Kotlin Interoperability"></a>
<a name="UnknownNullness"></a>
<section class="section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp" id="UnknownNullnessCard" style="display: block;">
            <div class="mdl-card mdl-cell mdl-cell--12-col">
  <div class="mdl-card__title">
    <h2 class="mdl-card__title-text">Unknown nullness</h2>
  </div>
              <div class="mdl-card__supporting-text">
<div class="issue">
<div class="warningslist">
<span class="location"><a href="app/src/other/pkg/AnnotationTest.java">src/other/pkg/AnnotationTest.java</a>:3</span>: <span class="message">Unknown nullability; explicitly declare as <code>@Nullable</code> or <code>@NonNull</code> to improve Kotlin interoperability; see <a href="https://developer.android.com/kotlin/interop#nullability_annotations">https://developer.android.com/kotlin/interop#nullability_annotations</a></span><br /><pre class="errorlines">
<span class="lineno"> 1 </span><span class="keyword">package</span> other.pkg;
<span class="lineno"> 2 </span><span class="keyword">public</span> <span class="keyword">class</span> AnnotationTest {
<span class="caretline"><span class="lineno"> 3 </span>    <span class="keyword">public</span> <span class="warning">Float</span> error4:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span>
<span class="lineno"> 4 </span>}</pre>

</div>
<div class="metadata"><div class="explanation" id="explanationUnknownNullness" style="display: none;">
To improve referencing this code from Kotlin, consider adding explicit nullness information here with either <code>@NonNull</code> or <code>@Nullable</code>.<br/><br/>
This check can be configured via the following options:<br/><br/>
<div class="options">
<b>ignore-deprecated</b> (default is false):<br/>
Whether to ignore classes and members that have been annotated with <code>@Deprecated</code>.<br/>
<br/>
Normally this lint check will flag all unannotated elements, but by setting this option to <code>true</code> it will skip any deprecated elements.<br/>
<br/>
To configure this option, use a `lint.xml` file in the project or source folder using an <code>&lt;option&gt;</code> block like the following:
<pre class="errorlines">
<span class="lineno"> 1 </span><span class="tag">&lt;lint></span>
<span class="lineno"> 2 </span>    <span class="tag">&lt;issue</span><span class="attribute"> id</span>=<span class="value">"UnknownNullness"</span>>
<span class="caretline"><span class="lineno"> 3 </span>        <span class="tag">&lt;option</span><span class="attribute"> name</span>=<span class="warning"><span class="value">"ignore-deprecated"</span> <span class="attribute">value</span>=<span class="value">"false"</span></span> />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span>
<span class="lineno"> 4 </span>    <span class="tag">&lt;/issue></span>
<span class="lineno"> 5 </span><span class="tag">&lt;/lint></span>
</pre>
</div><div class="moreinfo">More info: <a href="https://developer.android.com/kotlin/interop#nullability_annotations">https://developer.android.com/kotlin/interop#nullability_annotations</a>
</div>Note: This issue has an associated quickfix operation in Android Studio and IntelliJ IDEA.<br>
To suppress this error, use the issue id "UnknownNullness" as explained in the <a href="#SuppressInfo">Suppressing Warnings and Errors</a> section.<br/>
<br/></div>
</div>
</div>
<div class="chips">
<span class="mdl-chip">
    <span class="mdl-chip__text">UnknownNullness</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Kotlin Interoperability</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Interoperability</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Warning</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Priority 6/10</span>
</span>
</div>
              </div>
              <div class="mdl-card__actions mdl-card--border">
<button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="explanationUnknownNullnessLink" onclick="reveal('explanationUnknownNullness');">
Explain</button><button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="UnknownNullnessCardLink" onclick="hideid('UnknownNullnessCard');">
Dismiss</button>            </div>
            </div>
          </section>
<a name="ExtraIssues"></a>
<section class="section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp" id="ExtraIssuesCard" style="display: block;">
            <div class="mdl-card mdl-cell mdl-cell--12-col">
  <div class="mdl-card__title">
    <h2 class="mdl-card__title-text">Included Additional Checks</h2>
  </div>
              <div class="mdl-card__supporting-text">
This card lists all the extra checks run by lint, provided from libraries,
build configuration and extra flags. This is included to help you verify
whether a particular check is included in analysis when configuring builds.
(Note that the list does not include the hundreds of built-in checks into lint,
only additional ones.)
<div id="IncludedIssues" style="display: none;"><br/><br/><div class="issue">
<div class="id">HardcodedText<div class="issueSeparator"></div>
</div>
<div class="metadata"><div class="explanation">
Hardcoding text attributes directly in layout files is bad for several reasons:<br/>
<br/>
* When creating configuration variations (for example for landscape or portrait) you have to repeat the actual text (and keep it up to date when making changes)<br/>
<br/>
* The application cannot be translated to other languages by just adding new translations for existing string resources.<br/>
<br/>
There are quickfixes to automatically extract this hardcoded string into a resource lookup.<br/><div class="vendor">
Vendor: AOSP Unit Tests<br/>
Identifier: mylibrary-1.0<br/>
Contact: lint@example.com<br/>
Feedback: <a href="https://example.com/lint/file-new-bug.html">https://example.com/lint/file-new-bug.html</a><br/>
</div>
<br/>
<br/></div>
</div>
</div>
</div>
              </div>
              <div class="mdl-card__actions mdl-card--border">
<button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="IncludedIssuesLink" onclick="reveal('IncludedIssues');">
List Issues</button><button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="ExtraIssuesCardLink" onclick="hideid('ExtraIssuesCard');">
Dismiss</button>            </div>
            </div>
          </section>
<a name="MissingIssues"></a>
<section class="section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp" id="MissingIssuesCard" style="display: block;">
            <div class="mdl-card mdl-cell mdl-cell--12-col">
  <div class="mdl-card__title">
    <h2 class="mdl-card__title-text">Disabled Checks</h2>
  </div>
              <div class="mdl-card__supporting-text">
One or more issues were not run by lint, either
because the check is not enabled by default, or because
it was disabled with a command line flag or via one or
more <code>lint.xml</code> configuration files in the project directories.
<div id="SuppressedIssues" style="display: none;"><br/><br/><div class="issue">
<div class="id">MockLocation<div class="issueSeparator"></div>
</div>
<div class="metadata">Disabled By: Command line flag<br/>
<div class="explanation">
Using a mock location provider (by requiring the permission <code>android.permission.ACCESS_MOCK_LOCATION</code>) should <b>only</b> be done in debug builds (or from tests). In Gradle projects, that means you should only request this permission in a test or debug source set specific manifest file.<br/>
<br/>
To fix this, create a new manifest file in the debug folder and move the <code>&lt;uses-permission></code> element there. A typical path to a debug manifest override file in a Gradle project is src/debug/AndroidManifest.xml.<br/>Note: This issue has an associated quickfix operation in Android Studio and IntelliJ IDEA.<br>
<br/>
<br/></div>
</div>
</div>
<div class="issue">
<div class="id">SdCardPath<div class="issueSeparator"></div>
</div>
<div class="metadata">Disabled By: Default<br/>
<div class="explanation">
Your code should not reference the <code>/sdcard</code> path directly; instead use <code>Environment.getExternalStorageDirectory().getPath()</code>.<br/>
<br/>
Similarly, do not reference the <code>/data/data/</code> path directly; it can vary in multi-user scenarios. Instead, use <code>Context.getFilesDir().getPath()</code>.<br/><div class="moreinfo">More info: <a href="https://developer.android.com/training/data-storage#filesExternal">https://developer.android.com/training/data-storage#filesExternal</a>
</div><br/>
<br/></div>
</div>
</div>
</div>
              </div>
              <div class="mdl-card__actions mdl-card--border">
<button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="SuppressedIssuesLink" onclick="reveal('SuppressedIssues');">
List Missing Issues</button><button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="MissingIssuesCardLink" onclick="hideid('MissingIssuesCard');">
Dismiss</button>            </div>
            </div>
          </section>
<a name="SuppressInfo"></a>
<section class="section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp" id="SuppressCard" style="display: block;">
            <div class="mdl-card mdl-cell mdl-cell--12-col">
  <div class="mdl-card__title">
    <h2 class="mdl-card__title-text">Suppressing Warnings and Errors</h2>
  </div>
              <div class="mdl-card__supporting-text">
Lint errors can be suppressed in a variety of ways:<br/>
<br/>
1. With a <code>@SuppressLint</code> annotation in the Java code<br/>
2. With a <code>tools:ignore</code> attribute in the XML file<br/>
3. With a //noinspection comment in the source code<br/>
4. With ignore flags specified in the <code>build.gradle</code> file, as explained below<br/>
5. With a <code>lint.xml</code> configuration file in the project<br/>
6. With a <code>lint.xml</code> configuration file passed to lint via the --config flag<br/>
7. With the --ignore flag passed to lint.<br/>
<br/>
To suppress a lint warning with an annotation, add a <code>@SuppressLint("id")</code> annotation on the class, method or variable declaration closest to the warning instance you want to disable. The id can be one or more issue id's, such as <code>"UnusedResources"</code> or <code>{"UnusedResources","UnusedIds"}</code>, or it can be <code>"all"</code> to suppress all lint warnings in the given scope.<br/>
<br/>
To suppress a lint warning with a comment, add a <code>//noinspection id</code> comment on the line before the statement with the error.<br/>
<br/>
To suppress a lint warning in an XML file, add a <code>tools:ignore="id"</code> attribute on the element containing the error, or one of its surrounding elements. You also need to define the namespace for the tools prefix on the root element in your document, next to the <code>xmlns:android</code> declaration:<br/>
<code>xmlns:tools="http://schemas.android.com/tools"</code><br/>
<br/>
To suppress a lint warning in a <code>build.gradle</code> file, add a section like this:<br/>

<pre>
android {
    lintOptions {
        disable 'TypographyFractions','TypographyQuotes'
    }
}
</pre>
<br/>
Here we specify a comma separated list of issue id's after the disable command. You can also use <code>warning</code> or <code>error</code> instead of <code>disable</code> to change the severity of issues.<br/>
<br/>
To suppress lint warnings with a configuration XML file, create a file named <code>lint.xml</code> and place it at the root directory of the module in which it applies.<br/>
<br/>
The format of the <code>lint.xml</code> file is something like the following:<br/>

<pre>
&lt;?xml version="1.0" encoding="UTF-8"?>
&lt;lint>
    &lt;!-- Ignore everything in the test source set -->
    &lt;issue id="all">
        &lt;ignore path="/*/test//*" />
    &lt;/issue>

    &lt;!-- Disable this given check in this project -->
    &lt;issue id="IconMissingDensityFolder" severity="ignore" />

    &lt;!-- Ignore the ObsoleteLayoutParam issue in the given files -->
    &lt;issue id="ObsoleteLayoutParam">
        &lt;ignore path="res/layout/activation.xml" />
        &lt;ignore path="res/layout-xlarge/activation.xml" />
        &lt;ignore regexp="(foo|bar)/.java" />
    &lt;/issue>

    &lt;!-- Ignore the UselessLeaf issue in the given file -->
    &lt;issue id="UselessLeaf">
        &lt;ignore path="res/layout/main.xml" />
    &lt;/issue>

    &lt;!-- Change the severity of hardcoded strings to "error" -->
    &lt;issue id="HardcodedText" severity="error" />
&lt;/lint>
</pre>
<br/>
To suppress lint checks from the command line, pass the --ignore flag with a comma separated list of ids to be suppressed, such as:<br/>
<code>＄ lint --ignore UnusedResources,UselessLeaf /my/project/path</code><br/>
<br/>
For more information, see <a href="https://developer.android.com/studio/write/lint.html#config">https://developer.android.com/studio/write/lint.html#config</a><br/>

            </div>
            </div>
          </section>    </div>
  </main>
</div>
</body>
</html>"""
    )
  }

  @Test
  fun testCustomizations() {
    // NOTE: If you change the output, please validate it manually in
    //  http://validator.w3.org/#validate_by_input
    // before updating the following

    val prev = System.getProperty(REPORT_PREFERENCE_PROPERTY)
    try {
      System.setProperty(
        REPORT_PREFERENCE_PROPERTY,
        "maxIncidents=1,theme=darcula,window=1,underlineErrors=false",
      )
      HtmlReporter.initializePreferences()
      checkReportOutput(
        """<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">

<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<title>Lint Report</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/icon?family=Material+Icons">
 <link rel="stylesheet" href="https://code.getmdl.io/1.2.1/material.blue-indigo.min.css" />
<link rel="stylesheet" href="http://fonts.googleapis.com/css?family=Roboto:300,400,500,700" type="text/css">
<script defer src="https://code.getmdl.io/1.2.0/material.min.js"></script>
<style>
${HtmlReporter.cssStyles}</style>
<script language="javascript" type="text/javascript">
<!--
function reveal(id) {
if (document.getElementById) {
document.getElementById(id).style.display = 'block';
document.getElementById(id+'Link').style.display = 'none';
}
}
function hideid(id) {
if (document.getElementById) {
document.getElementById(id).style.display = 'none';
}
}
//-->
</script>
</head>
<body class="mdl-color--grey-100 mdl-color-text--grey-700 mdl-base">
<div class="mdl-layout mdl-js-layout mdl-layout--fixed-header">
  <header class="mdl-layout__header">
    <div class="mdl-layout__header-row">
      <span class="mdl-layout-title">Lint Report: 2 errors and 4 warnings</span>
      <div class="mdl-layout-spacer"></div>
      <nav class="mdl-navigation mdl-layout--large-screen-only">Check performed at ＄DATE by Lint Unit Tests</nav>
    </div>
  </header>
  <div class="mdl-layout__drawer">
    <span class="mdl-layout-title">Issue Types</span>
    <nav class="mdl-navigation">
      <a class="mdl-navigation__link" href="#overview"><i class="material-icons">dashboard</i>Overview</a>
      <a class="mdl-navigation__link" href="#DuplicateDefinition"><i class="material-icons error-icon">error</i>Duplicate definitions of resources (1)</a>
      <a class="mdl-navigation__link" href="#MultipleUsesSdk"><i class="material-icons error-icon">error</i>Multiple <code>&lt;uses-sdk></code> elements in the manifest (1)</a>
      <a class="mdl-navigation__link" href="#IconDuplicates"><i class="material-icons warning-icon">warning</i>Duplicated icons under different names (1)</a>
      <a class="mdl-navigation__link" href="#HardcodedText"><i class="material-icons warning-icon">warning</i>Hardcoded text (2)</a>
      <a class="mdl-navigation__link" href="#UnknownNullness"><i class="material-icons warning-icon">warning</i>Unknown nullness (1)</a>
    </nav>
  </div>
  <main class="mdl-layout__content">
    <div class="mdl-layout__tab-panel is-active">
<a name="overview"></a>
<section class="section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp" id="OverviewCard" style="display: block;">
            <div class="mdl-card mdl-cell mdl-cell--12-col">
  <div class="mdl-card__title">
    <h2 class="mdl-card__title-text">Overview</h2>
  </div>
              <div class="mdl-card__supporting-text">
<table class="overview">
<tr><td class="countColumn"></td><td class="categoryColumn"><a href="#Correctness">Correctness</a>
</td></tr>
<tr>
<td class="countColumn">1</td><td class="issueColumn"><i class="material-icons error-icon">error</i>
<a href="#DuplicateDefinition">DuplicateDefinition</a>: Duplicate definitions of resources</td></tr>
<tr>
<td class="countColumn">1</td><td class="issueColumn"><i class="material-icons error-icon">error</i>
<a href="#MultipleUsesSdk">MultipleUsesSdk</a>: Multiple <code>&lt;uses-sdk></code> elements in the manifest</td></tr>
<tr><td class="countColumn"></td><td class="categoryColumn"><a href="#Usability:Icons">Usability:Icons</a>
</td></tr>
<tr>
<td class="countColumn">1</td><td class="issueColumn"><i class="material-icons warning-icon">warning</i>
<a href="#IconDuplicates">IconDuplicates</a>: Duplicated icons under different names</td></tr>
<tr><td class="countColumn"></td><td class="categoryColumn"><a href="#Internationalization">Internationalization</a>
</td></tr>
<tr>
<td class="countColumn">2</td><td class="issueColumn"><i class="material-icons warning-icon">warning</i>
<a href="#HardcodedText">HardcodedText</a>: Hardcoded text</td></tr>
<tr><td class="countColumn"></td><td class="categoryColumn"><a href="#Interoperability:Kotlin Interoperability">Interoperability:Kotlin Interoperability</a>
</td></tr>
<tr>
<td class="countColumn">1</td><td class="issueColumn"><i class="material-icons warning-icon">warning</i>
<a href="#UnknownNullness">UnknownNullness</a>: Unknown nullness</td></tr>
<tr><td></td><td class="categoryColumn"><a href="#ExtraIssues">Included Additional Checks (1)</a>
</td></tr>
<tr><td></td><td class="categoryColumn"><a href="#MissingIssues">Disabled Checks (2)</a>
</td></tr>
</table>
<br/>              </div>
              <div class="mdl-card__actions mdl-card--border">
<button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="OverviewCardLink" onclick="hideid('OverviewCard');">
Dismiss</button>            </div>
            </div>
          </section>
<a name="Correctness"></a>
<a name="DuplicateDefinition"></a>
<section class="section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp" id="DuplicateDefinitionCard" style="display: block;">
            <div class="mdl-card mdl-cell mdl-cell--12-col">
  <div class="mdl-card__title">
    <h2 class="mdl-card__title-text">Duplicate definitions of resources</h2>
  </div>
              <div class="mdl-card__supporting-text">
<div class="issue">
<div class="warningslist">
<span class="location"><a href="app/res/values/strings2.xml">res/values/strings2.xml</a>:2</span>: <span class="message"><code>app_name</code> has already been defined in this folder</span><br /><pre class="errorlines">
<span class="lineno"> 1 </span><span class="tag">&lt;resources></span>
<span class="caretline"><span class="lineno"> 2 </span>    <span class="tag">&lt;string</span><span class="attribute"> </span><span class="error"><span class="attribute">name</span>=<span class="value">"app_name"</span></span>>App Name<span class="tag">&lt;/string></span>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span>
<span class="lineno"> 3 </span><span class="tag">&lt;/resources></span></pre>

<ul><span class="location"><a href="app/res/values/strings.xml">res/values/strings.xml</a>:2</span>: <span class="message">Previously defined here</span><br /><pre class="errorlines">
<span class="lineno"> 1 </span><span class="tag">&lt;resources></span>
<span class="caretline"><span class="lineno"> 2 </span>    <span class="tag">&lt;string</span><span class="attribute"> </span><span class="error"><span class="attribute">name</span>=<span class="value">"app_name"</span></span>>App Name<span class="tag">&lt;/string></span>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span>
<span class="lineno"> 3 </span><span class="tag">&lt;/resources></span></pre>
</ul></div>
<div class="metadata"><div class="explanation" id="explanationDuplicateDefinition" style="display: none;">
You can define a resource multiple times in different resource folders; that's how string translations are done, for example. However, defining the same resource more than once in the same resource folder is likely an error, for example attempting to add a new resource without realizing that the name is already used, and so on.<br/>To suppress this error, use the issue id "DuplicateDefinition" as explained in the <a href="#SuppressInfo">Suppressing Warnings and Errors</a> section.<br/>
<br/></div>
</div>
</div>
<div class="chips">
<span class="mdl-chip">
    <span class="mdl-chip__text">DuplicateDefinition</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Correctness</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Error</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Priority 6/10</span>
</span>
</div>
              </div>
              <div class="mdl-card__actions mdl-card--border">
<button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="explanationDuplicateDefinitionLink" onclick="reveal('explanationDuplicateDefinition');">
Explain</button><button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="DuplicateDefinitionCardLink" onclick="hideid('DuplicateDefinitionCard');">
Dismiss</button>            </div>
            </div>
          </section><a name="MultipleUsesSdk"></a>
<section class="section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp" id="MultipleUsesSdkCard" style="display: block;">
            <div class="mdl-card mdl-cell mdl-cell--12-col">
  <div class="mdl-card__title">
    <h2 class="mdl-card__title-text">Multiple &lt;uses-sdk> elements in the manifest</h2>
  </div>
              <div class="mdl-card__supporting-text">
<div class="issue">
<div class="warningslist">
<span class="location"><a href="app/AndroidManifest.xml">AndroidManifest.xml</a>:4</span>: <span class="message">There should only be a single <code>&lt;uses-sdk></code> element in the manifest: merge these together</span><br /><pre class="errorlines">
<span class="lineno"> 3 </span>    <span class="tag">&lt;uses-sdk</span><span class="attribute"> </span><span class="prefix">android:</span><span class="attribute">minSdkVersion</span>=<span class="value">"10"</span> <span class="prefix">android:</span><span class="attribute">targetSdkVersion</span>=<span class="value">"31"</span> />
<span class="caretline"><span class="lineno"> 4 </span>    <span class="tag">&lt;</span><span class="error"><span class="tag">uses-sdk</span></span><span class="attribute"> </span><span class="prefix">android:</span><span class="attribute">minSdkVersion</span>=<span class="value">"10"</span> <span class="prefix">android:</span><span class="attribute">targetSdkVersion</span>=<span class="value">"31"</span> />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span>
<span class="lineno"> 5 </span><span class="tag">&lt;/manifest></span></pre>

<ul><span class="location"><a href="app/AndroidManifest.xml">AndroidManifest.xml</a>:3</span>: <span class="message">Also appears here</span><br /><pre class="errorlines">
<span class="lineno"> 2 </span>    <span class="attribute">package</span>=<span class="value">"test.pkg"</span>>
<span class="caretline"><span class="lineno"> 3 </span>    <span class="tag">&lt;</span><span class="error"><span class="tag">uses-sdk</span></span><span class="attribute"> </span><span class="prefix">android:</span><span class="attribute">minSdkVersion</span>=<span class="value">"10"</span> <span class="prefix">android:</span><span class="attribute">targetSdkVersion</span>=<span class="value">"31"</span> />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span>
<span class="lineno"> 4 </span>    <span class="tag">&lt;uses-sdk</span><span class="attribute"> </span><span class="prefix">android:</span><span class="attribute">minSdkVersion</span>=<span class="value">"10"</span> <span class="prefix">android:</span><span class="attribute">targetSdkVersion</span>=<span class="value">"31"</span> />
</pre>
</ul></div>
<div class="metadata"><div class="explanation" id="explanationMultipleUsesSdk" style="display: none;">
The <code>&lt;uses-sdk></code> element should appear just once; the tools will <b>not</b> merge the contents of all the elements so if you split up the attributes across multiple elements, only one of them will take effect. To fix this, just merge all the attributes from the various elements into a single &lt;uses-sdk> element.<br/><div class="moreinfo">More info: <a href="https://developer.android.com/guide/topics/manifest/uses-sdk-element.html">https://developer.android.com/guide/topics/manifest/uses-sdk-element.html</a>
</div>To suppress this error, use the issue id "MultipleUsesSdk" as explained in the <a href="#SuppressInfo">Suppressing Warnings and Errors</a> section.<br/>
<br/></div>
</div>
</div>
<div class="chips">
<span class="mdl-chip">
    <span class="mdl-chip__text">MultipleUsesSdk</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Correctness</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Error</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Priority 6/10</span>
</span>
</div>
              </div>
              <div class="mdl-card__actions mdl-card--border">
<button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="explanationMultipleUsesSdkLink" onclick="reveal('explanationMultipleUsesSdk');">
Explain</button><button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="MultipleUsesSdkCardLink" onclick="hideid('MultipleUsesSdkCard');">
Dismiss</button>            </div>
            </div>
          </section>
<a name="Usability:Icons"></a>
<a name="IconDuplicates"></a>
<section class="section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp" id="IconDuplicatesCard" style="display: block;">
            <div class="mdl-card mdl-cell mdl-cell--12-col">
  <div class="mdl-card__title">
    <h2 class="mdl-card__title-text">Duplicated icons under different names</h2>
  </div>
              <div class="mdl-card__supporting-text">
<div class="issue">
<div class="warningslist">
<span class="location"><a href="app/res/drawable-hdpi/icon4.png">res/drawable-hdpi/icon4.png</a></span>: <span class="message">The following unrelated icon files have identical contents: icon2.png, icon3.png, icon4.png</span><br />
<ul><span class="location"><a href="app/res/drawable-hdpi/icon3.png">res/drawable-hdpi/icon3.png</a></span>: <span class="message">&lt;No location-specific message></span><br /><span class="location"><a href="app/res/drawable-hdpi/icon2.png">res/drawable-hdpi/icon2.png</a></span>: <span class="message">&lt;No location-specific message></span><br /></ul><table>
<tr><td><a href="app/res/drawable-hdpi/icon2.png"><img border="0" align="top" src="app/res/drawable-hdpi/icon2.png" /></a>
</td><td><a href="app/res/drawable-hdpi/icon3.png"><img border="0" align="top" src="app/res/drawable-hdpi/icon3.png" /></a>
</td><td><a href="app/res/drawable-hdpi/icon4.png"><img border="0" align="top" src="app/res/drawable-hdpi/icon4.png" /></a>
</td></tr><tr><th>hdpi</th><th>hdpi</th><th>hdpi</th></tr>
</table>
</div>
<div class="metadata"><div class="explanation" id="explanationIconDuplicates" style="display: none;">
If an icon is repeated under different names, you can consolidate and just use one of the icons and delete the others to make your application smaller. However, duplicated icons usually are not intentional and can sometimes point to icons that were accidentally overwritten or accidentally not updated.<br/>To suppress this error, use the issue id "IconDuplicates" as explained in the <a href="#SuppressInfo">Suppressing Warnings and Errors</a> section.<br/>
<br/></div>
</div>
</div>
<div class="chips">
<span class="mdl-chip">
    <span class="mdl-chip__text">IconDuplicates</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Icons</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Usability</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Warning</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Priority 3/10</span>
</span>
</div>
              </div>
              <div class="mdl-card__actions mdl-card--border">
<button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="explanationIconDuplicatesLink" onclick="reveal('explanationIconDuplicates');">
Explain</button><button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="IconDuplicatesCardLink" onclick="hideid('IconDuplicatesCard');">
Dismiss</button>            </div>
            </div>
          </section>
<a name="Internationalization"></a>
<a name="HardcodedText"></a>
<section class="section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp" id="HardcodedTextCard" style="display: block;">
            <div class="mdl-card mdl-cell mdl-cell--12-col">
  <div class="mdl-card__title">
    <h2 class="mdl-card__title-text">Hardcoded text</h2>
  </div>
              <div class="mdl-card__supporting-text">
<div class="issue">
<div class="warningslist">
<span class="location"><a href="app/res/layout/main.xml">res/layout/main.xml</a>:3</span>: <span class="message">Hardcoded string "Fooo", should use <code>@string</code> resource</span><br /><pre class="errorlines">
<span class="lineno"> 2 </span>    <span class="prefix">android:</span><span class="attribute">id</span>=<span class="value">"@+id/button1"</span>
<span class="caretline"><span class="lineno"> 3 </span>    <span class="warning"><span class="prefix">android:</span><span class="attribute">text</span>=<span class="value">"Fooo"</span></span> />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span></pre>

<br/><b>NOTE: 1 results omitted.</b><br/><br/></div>
<div class="metadata"><div class="explanation" id="explanationHardcodedText" style="display: none;">
Hardcoding text attributes directly in layout files is bad for several reasons:<br/>
<br/>
* When creating configuration variations (for example for landscape or portrait) you have to repeat the actual text (and keep it up to date when making changes)<br/>
<br/>
* The application cannot be translated to other languages by just adding new translations for existing string resources.<br/>
<br/>
There are quickfixes to automatically extract this hardcoded string into a resource lookup.<br/>To suppress this error, use the issue id "HardcodedText" as explained in the <a href="#SuppressInfo">Suppressing Warnings and Errors</a> section.<br/>
<br/></div>
</div>
</div>
<div class="vendor">
Vendor: AOSP Unit Tests<br/>
Identifier: mylibrary-1.0<br/>
Contact: lint@example.com<br/>
Feedback: <a href="https://example.com/lint/file-new-bug.html">https://example.com/lint/file-new-bug.html</a><br/>
</div>
<div class="chips">
<span class="mdl-chip">
    <span class="mdl-chip__text">HardcodedText</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Internationalization</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Warning</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Priority 5/10</span>
</span>
</div>
              </div>
              <div class="mdl-card__actions mdl-card--border">
<button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="explanationHardcodedTextLink" onclick="reveal('explanationHardcodedText');">
Explain</button><button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="HardcodedTextCardLink" onclick="hideid('HardcodedTextCard');">
Dismiss</button>            </div>
            </div>
          </section>
<a name="Interoperability:Kotlin Interoperability"></a>
<a name="UnknownNullness"></a>
<section class="section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp" id="UnknownNullnessCard" style="display: block;">
            <div class="mdl-card mdl-cell mdl-cell--12-col">
  <div class="mdl-card__title">
    <h2 class="mdl-card__title-text">Unknown nullness</h2>
  </div>
              <div class="mdl-card__supporting-text">
<div class="issue">
<div class="warningslist">
<span class="location"><a href="app/src/other/pkg/AnnotationTest.java">src/other/pkg/AnnotationTest.java</a>:3</span>: <span class="message">Unknown nullability; explicitly declare as <code>@Nullable</code> or <code>@NonNull</code> to improve Kotlin interoperability; see <a href="https://developer.android.com/kotlin/interop#nullability_annotations">https://developer.android.com/kotlin/interop#nullability_annotations</a></span><br /><pre class="errorlines">
<span class="lineno"> 2 </span><span class="keyword">public</span> <span class="keyword">class</span> AnnotationTest {
<span class="caretline"><span class="lineno"> 3 </span>    <span class="keyword">public</span> <span class="warning">Float</span> error4:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span>
<span class="lineno"> 4 </span>}</pre>

</div>
<div class="metadata"><div class="explanation" id="explanationUnknownNullness" style="display: none;">
To improve referencing this code from Kotlin, consider adding explicit nullness information here with either <code>@NonNull</code> or <code>@Nullable</code>.<br/><br/>
This check can be configured via the following options:<br/><br/>
<div class="options">
<b>ignore-deprecated</b> (default is false):<br/>
Whether to ignore classes and members that have been annotated with <code>@Deprecated</code>.<br/>
<br/>
Normally this lint check will flag all unannotated elements, but by setting this option to <code>true</code> it will skip any deprecated elements.<br/>
<br/>
To configure this option, use a `lint.xml` file in the project or source folder using an <code>&lt;option&gt;</code> block like the following:
<pre class="errorlines">
<span class="lineno"> 2 </span>    <span class="tag">&lt;issue</span><span class="attribute"> id</span>=<span class="value">"UnknownNullness"</span>>
<span class="caretline"><span class="lineno"> 3 </span>        <span class="tag">&lt;option</span><span class="attribute"> name</span>=<span class="warning"><span class="value">"ignore-deprecated"</span> <span class="attribute">value</span>=<span class="value">"false"</span></span> />&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span>
<span class="lineno"> 4 </span>    <span class="tag">&lt;/issue></span></pre>
</div><div class="moreinfo">More info: <a href="https://developer.android.com/kotlin/interop#nullability_annotations">https://developer.android.com/kotlin/interop#nullability_annotations</a>
</div>Note: This issue has an associated quickfix operation in Android Studio and IntelliJ IDEA.<br>
To suppress this error, use the issue id "UnknownNullness" as explained in the <a href="#SuppressInfo">Suppressing Warnings and Errors</a> section.<br/>
<br/></div>
</div>
</div>
<div class="chips">
<span class="mdl-chip">
    <span class="mdl-chip__text">UnknownNullness</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Kotlin Interoperability</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Interoperability</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Warning</span>
</span>
<span class="mdl-chip">
    <span class="mdl-chip__text">Priority 6/10</span>
</span>
</div>
              </div>
              <div class="mdl-card__actions mdl-card--border">
<button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="explanationUnknownNullnessLink" onclick="reveal('explanationUnknownNullness');">
Explain</button><button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="UnknownNullnessCardLink" onclick="hideid('UnknownNullnessCard');">
Dismiss</button>            </div>
            </div>
          </section>
<a name="ExtraIssues"></a>
<section class="section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp" id="ExtraIssuesCard" style="display: block;">
            <div class="mdl-card mdl-cell mdl-cell--12-col">
  <div class="mdl-card__title">
    <h2 class="mdl-card__title-text">Included Additional Checks</h2>
  </div>
              <div class="mdl-card__supporting-text">
This card lists all the extra checks run by lint, provided from libraries,
build configuration and extra flags. This is included to help you verify
whether a particular check is included in analysis when configuring builds.
(Note that the list does not include the hundreds of built-in checks into lint,
only additional ones.)
<div id="IncludedIssues" style="display: none;"><br/><br/><div class="issue">
<div class="id">HardcodedText<div class="issueSeparator"></div>
</div>
<div class="metadata"><div class="explanation">
Hardcoding text attributes directly in layout files is bad for several reasons:<br/>
<br/>
* When creating configuration variations (for example for landscape or portrait) you have to repeat the actual text (and keep it up to date when making changes)<br/>
<br/>
* The application cannot be translated to other languages by just adding new translations for existing string resources.<br/>
<br/>
There are quickfixes to automatically extract this hardcoded string into a resource lookup.<br/><div class="vendor">
Vendor: AOSP Unit Tests<br/>
Identifier: mylibrary-1.0<br/>
Contact: lint@example.com<br/>
Feedback: <a href="https://example.com/lint/file-new-bug.html">https://example.com/lint/file-new-bug.html</a><br/>
</div>
<br/>
<br/></div>
</div>
</div>
</div>
              </div>
              <div class="mdl-card__actions mdl-card--border">
<button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="IncludedIssuesLink" onclick="reveal('IncludedIssues');">
List Issues</button><button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="ExtraIssuesCardLink" onclick="hideid('ExtraIssuesCard');">
Dismiss</button>            </div>
            </div>
          </section>
<a name="MissingIssues"></a>
<section class="section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp" id="MissingIssuesCard" style="display: block;">
            <div class="mdl-card mdl-cell mdl-cell--12-col">
  <div class="mdl-card__title">
    <h2 class="mdl-card__title-text">Disabled Checks</h2>
  </div>
              <div class="mdl-card__supporting-text">
One or more issues were not run by lint, either
because the check is not enabled by default, or because
it was disabled with a command line flag or via one or
more <code>lint.xml</code> configuration files in the project directories.
<div id="SuppressedIssues" style="display: none;"><br/><br/><div class="issue">
<div class="id">MockLocation<div class="issueSeparator"></div>
</div>
<div class="metadata">Disabled By: Command line flag<br/>
<div class="explanation">
Using a mock location provider (by requiring the permission <code>android.permission.ACCESS_MOCK_LOCATION</code>) should <b>only</b> be done in debug builds (or from tests). In Gradle projects, that means you should only request this permission in a test or debug source set specific manifest file.<br/>
<br/>
To fix this, create a new manifest file in the debug folder and move the <code>&lt;uses-permission></code> element there. A typical path to a debug manifest override file in a Gradle project is src/debug/AndroidManifest.xml.<br/>Note: This issue has an associated quickfix operation in Android Studio and IntelliJ IDEA.<br>
<br/>
<br/></div>
</div>
</div>
<div class="issue">
<div class="id">SdCardPath<div class="issueSeparator"></div>
</div>
<div class="metadata">Disabled By: Default<br/>
<div class="explanation">
Your code should not reference the <code>/sdcard</code> path directly; instead use <code>Environment.getExternalStorageDirectory().getPath()</code>.<br/>
<br/>
Similarly, do not reference the <code>/data/data/</code> path directly; it can vary in multi-user scenarios. Instead, use <code>Context.getFilesDir().getPath()</code>.<br/><div class="moreinfo">More info: <a href="https://developer.android.com/training/data-storage#filesExternal">https://developer.android.com/training/data-storage#filesExternal</a>
</div><br/>
<br/></div>
</div>
</div>
</div>
              </div>
              <div class="mdl-card__actions mdl-card--border">
<button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="SuppressedIssuesLink" onclick="reveal('SuppressedIssues');">
List Missing Issues</button><button class="mdl-button mdl-js-button mdl-js-ripple-effect" id="MissingIssuesCardLink" onclick="hideid('MissingIssuesCard');">
Dismiss</button>            </div>
            </div>
          </section>
<a name="SuppressInfo"></a>
<section class="section--center mdl-grid mdl-grid--no-spacing mdl-shadow--2dp" id="SuppressCard" style="display: block;">
            <div class="mdl-card mdl-cell mdl-cell--12-col">
  <div class="mdl-card__title">
    <h2 class="mdl-card__title-text">Suppressing Warnings and Errors</h2>
  </div>
              <div class="mdl-card__supporting-text">
Lint errors can be suppressed in a variety of ways:<br/>
<br/>
1. With a <code>@SuppressLint</code> annotation in the Java code<br/>
2. With a <code>tools:ignore</code> attribute in the XML file<br/>
3. With a //noinspection comment in the source code<br/>
4. With ignore flags specified in the <code>build.gradle</code> file, as explained below<br/>
5. With a <code>lint.xml</code> configuration file in the project<br/>
6. With a <code>lint.xml</code> configuration file passed to lint via the --config flag<br/>
7. With the --ignore flag passed to lint.<br/>
<br/>
To suppress a lint warning with an annotation, add a <code>@SuppressLint("id")</code> annotation on the class, method or variable declaration closest to the warning instance you want to disable. The id can be one or more issue id's, such as <code>"UnusedResources"</code> or <code>{"UnusedResources","UnusedIds"}</code>, or it can be <code>"all"</code> to suppress all lint warnings in the given scope.<br/>
<br/>
To suppress a lint warning with a comment, add a <code>//noinspection id</code> comment on the line before the statement with the error.<br/>
<br/>
To suppress a lint warning in an XML file, add a <code>tools:ignore="id"</code> attribute on the element containing the error, or one of its surrounding elements. You also need to define the namespace for the tools prefix on the root element in your document, next to the <code>xmlns:android</code> declaration:<br/>
<code>xmlns:tools="http://schemas.android.com/tools"</code><br/>
<br/>
To suppress a lint warning in a <code>build.gradle</code> file, add a section like this:<br/>

<pre>
android {
    lintOptions {
        disable 'TypographyFractions','TypographyQuotes'
    }
}
</pre>
<br/>
Here we specify a comma separated list of issue id's after the disable command. You can also use <code>warning</code> or <code>error</code> instead of <code>disable</code> to change the severity of issues.<br/>
<br/>
To suppress lint warnings with a configuration XML file, create a file named <code>lint.xml</code> and place it at the root directory of the module in which it applies.<br/>
<br/>
The format of the <code>lint.xml</code> file is something like the following:<br/>

<pre>
&lt;?xml version="1.0" encoding="UTF-8"?>
&lt;lint>
    &lt;!-- Ignore everything in the test source set -->
    &lt;issue id="all">
        &lt;ignore path="/*/test//*" />
    &lt;/issue>

    &lt;!-- Disable this given check in this project -->
    &lt;issue id="IconMissingDensityFolder" severity="ignore" />

    &lt;!-- Ignore the ObsoleteLayoutParam issue in the given files -->
    &lt;issue id="ObsoleteLayoutParam">
        &lt;ignore path="res/layout/activation.xml" />
        &lt;ignore path="res/layout-xlarge/activation.xml" />
        &lt;ignore regexp="(foo|bar)/.java" />
    &lt;/issue>

    &lt;!-- Ignore the UselessLeaf issue in the given file -->
    &lt;issue id="UselessLeaf">
        &lt;ignore path="res/layout/main.xml" />
    &lt;/issue>

    &lt;!-- Change the severity of hardcoded strings to "error" -->
    &lt;issue id="HardcodedText" severity="error" />
&lt;/lint>
</pre>
<br/>
To suppress lint checks from the command line, pass the --ignore flag with a comma separated list of ids to be suppressed, such as:<br/>
<code>＄ lint --ignore UnusedResources,UselessLeaf /my/project/path</code><br/>
<br/>
For more information, see <a href="https://developer.android.com/studio/write/lint.html#config">https://developer.android.com/studio/write/lint.html#config</a><br/>

            </div>
            </div>
          </section>    </div>
  </main>
</div>
</body>
</html>"""
      )
    } finally {
      if (prev != null) {
        System.setProperty(REPORT_PREFERENCE_PROPERTY, prev)
      } else {
        System.clearProperty(REPORT_PREFERENCE_PROPERTY)
      }
      HtmlReporter.initializePreferences()
    }
  }
}
