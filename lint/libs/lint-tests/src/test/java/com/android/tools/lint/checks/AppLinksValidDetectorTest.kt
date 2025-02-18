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
package com.android.tools.lint.checks

import com.android.tools.lint.checks.AppLinksValidDetector.Companion.APP_LINK_WARNING
import com.android.tools.lint.checks.AppLinksValidDetector.Companion.TEST_URL
import com.android.tools.lint.checks.AppLinksValidDetector.Companion.VALIDATION
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.XmlContext
import com.android.utils.XmlUtils
import com.google.common.truth.Truth.assertThat
import java.net.URL
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.w3c.dom.Element

class AppLinksValidDetectorTest : AbstractCheckTest() {
  override fun getDetector(): Detector {
    return AppLinksValidDetector()
  }

  fun testIntentFilterDataDeclaration() {
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >
                    <activity android:name="com.example.Activity">
                        <intent-filter>
                            <data android:scheme="https" android:host="example.com"/>
                            <data android:scheme="http" android:host="example.org"/>
                        </intent-filter>
                    </activity>
                    <receiver android:name="com.example.Receiver">
                        <intent-filter>
                            <data android:scheme="https"/>
                            <data
                                android:host="example.com"
                                android:path="/path"
                                android:scheme="https"
                                />
                        </intent-filter>
                    </receiver>
                    <service android:name="com.example.Service">
                        <intent-filter>
                            <data android:scheme="https"/>
                            <!-- Don't warn on only host and port -->
                            <data android:host="example.com" android:port="40"/>
                            <!-- But do warn if there's a path -->
                            <data android:host="example.com" android:port="41" android:path="/sub"/>
                        </intent-filter>
                    </service>
                    <provider android:name="com.example.Provider">
                        <intent-filter>
                            <data android:scheme="https"/>
                            <data android:host="example.com" android:mimeType="image/jpeg"/>
                        </intent-filter>
                    </provider>
                    <activity android:name="com.example.Activity2">
                        <intent-filter>
                            <data android:scheme="https" android:host="example.com"/>
                            <data
                                android:pathPrefix="/prefix"
                                android:path="/path"
                                android:pathPattern="/pattern/*"
                                />
                        </intent-filter>
                    </activity>
                    <activity android:name="com.example.Activity3">
                        <intent-filter>
                            <!-- Don't warn on only 1 data tag -->
                            <data android:scheme="https" android:host="example.com"/>
                        </intent-filter>
                    </activity>
                </manifest>
                """
          )
          .indented()
      )
      .issues(AppLinksValidDetector.INTENT_FILTER_UNIQUE_DATA_ATTRIBUTES)
      .run()
      .expect(
        """
          AndroidManifest.xml:5: Warning: Consider splitting data tag into multiple tags with individual attributes to avoid confusion [IntentFilterUniqueDataAttributes]
                      <data android:scheme="https" android:host="example.com"/>
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          AndroidManifest.xml:6: Warning: Consider splitting data tag into multiple tags with individual attributes to avoid confusion [IntentFilterUniqueDataAttributes]
                      <data android:scheme="http" android:host="example.org"/>
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          AndroidManifest.xml:12: Warning: Consider splitting data tag into multiple tags with individual attributes to avoid confusion [IntentFilterUniqueDataAttributes]
                      <data
                      ^
          AndroidManifest.xml:25: Warning: Consider splitting data tag into multiple tags with individual attributes to avoid confusion [IntentFilterUniqueDataAttributes]
                      <data android:host="example.com" android:port="41" android:path="/sub"/>
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          AndroidManifest.xml:36: Warning: Consider splitting data tag into multiple tags with individual attributes to avoid confusion [IntentFilterUniqueDataAttributes]
                      <data android:scheme="https" android:host="example.com"/>
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          AndroidManifest.xml:37: Warning: Consider splitting data tag into multiple tags with individual attributes to avoid confusion [IntentFilterUniqueDataAttributes]
                      <data
                      ^
          0 errors, 6 warnings
        """
      )
      .verifyFixes()
      .robot(true)
      .expectFixDiffs(
        """
        Autofix for AndroidManifest.xml line 5: Replace with <data android:scheme="https" />...:
        @@ -5 +5
        -             <data android:scheme="https" android:host="example.com"/>
        +             <data android:scheme="https" />
        +             <data android:host="example.com" />
        Autofix for AndroidManifest.xml line 6: Replace with <data android:scheme="http" />...:
        @@ -6 +6
        -             <data android:scheme="http" android:host="example.org"/>
        +             <data android:scheme="http" />
        +             <data android:host="example.org" />
        Autofix for AndroidManifest.xml line 12: Replace with <data android:scheme="https" />...:
        @@ -12 +12
        -             <data
        -                 android:host="example.com"
        -                 android:path="/path"
        -                 android:scheme="https"
        -                 />
        +             <data android:scheme="https" />
        +             <data android:host="example.com" />
        +             <data android:path="/path" />
        Autofix for AndroidManifest.xml line 25: Replace with <data android:host="example.com" android:port="41" />...:
        @@ -25 +25
        -             <data android:host="example.com" android:port="41" android:path="/sub"/>
        +             <data android:host="example.com" android:port="41" />
        +             <data android:path="/sub" />
        Autofix for AndroidManifest.xml line 36: Replace with <data android:scheme="https" />...:
        @@ -36 +36
        -             <data android:scheme="https" android:host="example.com"/>
        +             <data android:scheme="https" />
        +             <data android:host="example.com" />
        Autofix for AndroidManifest.xml line 37: Replace with <data android:path="/path" />...:
        @@ -37 +37
        -             <data
        -                 android:pathPrefix="/prefix"
        -                 android:path="/path"
        -                 android:pathPattern="/pattern/*"
        -                 />
        +             <data android:path="/path" />
        +             <data android:pathPrefix="/prefix" />
        +             <data android:pathPattern="/pattern/*" />
        """
      )
  }

  fun testDocumentationExampleIntentFilterUniqueDataAttributes() {
    // Tests custom android namespace
    lint()
      .files(
        manifest(
            """
                <manifest xmlns:alt-android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >
                    <activity alt-android:name="com.example.Activity">
                        <intent-filter>
                            <data alt-android:scheme="https" alt-android:host="example.com"/>
                            <data alt-android:scheme="http" alt-android:host="example.org"/>
                        </intent-filter>
                    </activity>
                </manifest>
                """
          )
          .indented()
      )
      .issues(AppLinksValidDetector.INTENT_FILTER_UNIQUE_DATA_ATTRIBUTES)
      .run()
      .expect(
        """
                AndroidManifest.xml:5: Warning: Consider splitting data tag into multiple tags with individual attributes to avoid confusion [IntentFilterUniqueDataAttributes]
                            <data alt-android:scheme="https" alt-android:host="example.com"/>
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                AndroidManifest.xml:6: Warning: Consider splitting data tag into multiple tags with individual attributes to avoid confusion [IntentFilterUniqueDataAttributes]
                            <data alt-android:scheme="http" alt-android:host="example.org"/>
                            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 2 warnings
                """
      )
      .verifyFixes()
      .robot(true)
      .expectFixDiffs(
        """
        Autofix for AndroidManifest.xml line 5: Replace with <data alt-android:scheme="https" />...:
        @@ -5 +5
        -             <data alt-android:scheme="https" alt-android:host="example.com"/>
        +             <data alt-android:scheme="https" />
        +             <data alt-android:host="example.com" />
        Autofix for AndroidManifest.xml line 6: Replace with <data alt-android:scheme="http" />...:
        @@ -6 +6
        -             <data alt-android:scheme="http" alt-android:host="example.org"/>
        +             <data alt-android:scheme="http" />
        +             <data alt-android:host="example.org" />
        """
      )
  }

  fun testWrongNamespace() {
    val expected =
      """
            AndroidManifest.xml:15: Error: Validation nodes should be in the tools: namespace to ensure they are removed from the manifest at build time [TestAppLink]
                        <validation />
                         ~~~~~~~~~~
            1 errors, 0 warnings
            """
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="test.pkg" >

                    <application>
                        <activity android:name=".MainActivity">
                            <intent-filter android:autoVerify="true">
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                                <data android:scheme="http"
                                    android:host="example.com"
                                    android:pathPrefix="/gizmos" />
                            </intent-filter>
                            <validation />
                        </activity>
                    </application>

                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(expected)
  }

  fun testMissingTestUrl() {
    val expected =
      """
            AndroidManifest.xml:15: Error: Expected testUrl attribute [AppLinkUrlError]
                        <tools:validation />
                        ~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="test.pkg" >

                    <application>
                        <activity android:name=".MainActivity">
                            <intent-filter android:autoVerify="true">
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                                <data android:scheme="http"
                                    android:host="example.com"
                                    android:pathPrefix="/gizmos" />
                            </intent-filter>
                            <tools:validation />
                        </activity>
                    </application>

                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(expected)
  }

  fun testBadTestUrl() {
    val expected =
      """
            AndroidManifest.xml:14: Error: Invalid test URL: no protocol: no-protocol [TestAppLink]
                        <tools:validation testUrl="no-protocol"/>
                                                   ~~~~~~~~~~~
            AndroidManifest.xml:15: Error: Invalid test URL: unknown protocol: unknown-protocol [TestAppLink]
                        <tools:validation testUrl="unknown-protocol://example.com/gizmos/foo/bar"/>
                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:16: Error: Invalid test URL: Invalid host: [FEDC:BA98:7654:3210:GEDC:BA98:7654:3210] [TestAppLink]
                        <tools:validation testUrl="http://[FEDC:BA98:7654:3210:GEDC:BA98:7654:3210]:80/index.html"/>
                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            3 errors, 0 warnings
            """
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"    package="test.pkg" >

                    <application>
                        <activity android:name=".MainActivity">
                            <intent-filter android:autoVerify="true">
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                                <data android:scheme="http"
                                    android:host="example.com"
                                    android:pathPrefix="/gizmos" />
                            </intent-filter>
                            <tools:validation testUrl="no-protocol"/>
                            <tools:validation testUrl="unknown-protocol://example.com/gizmos/foo/bar"/>
                            <tools:validation testUrl="http://[FEDC:BA98:7654:3210:GEDC:BA98:7654:3210]:80/index.html"/>
                        </activity>
                    </application>

                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(expected)
  }

  fun testValidation1() {
    val expected =
      """
            AndroidManifest.xml:17: Error: Test URL did not match path prefix /gizmos, path literal /literal/path [TestAppLink]
                        <tools:validation testUrl="http://example.com/notmatch/foo/bar"/>
                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:18: Error: Test URL did not match host example.com [TestAppLink]
                        <tools:validation testUrl="http://notmatch.com/gizmos/foo/bar"/>
                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:19: Error: Test URL did not match scheme http [TestAppLink]
                        <tools:validation testUrl="https://example.com/gizmos/foo/bar"/>
                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            3 errors, 0 warnings
            """
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="test.pkg" >

                    <application>
                        <activity android:name=".MainActivity">
                            <intent-filter android:autoVerify="true">
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                                <data android:scheme="http"
                                    android:host="example.com"
                                    android:pathPrefix="/gizmos" />
                                <data android:path="/literal/path" />
                            </intent-filter>
                            <tools:validation testUrl="http://example.com/gizmos/foo/bar"/>
                            <tools:validation testUrl="http://example.com/notmatch/foo/bar"/>
                            <tools:validation testUrl="http://notmatch.com/gizmos/foo/bar"/>
                            <tools:validation testUrl="https://example.com/gizmos/foo/bar"/>
                            <tools:validation testUrl="http://example.com/literal/path"/>
                        </activity>
                    </application>

                </manifest>
                """,
          )
          .indented()
      )
      .issues(TEST_URL)
      .run()
      .expect(expected)
  }

  fun testValidation2() {
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher" >
                        <activity android:name=".MainActivity">
                            <intent-filter android:autoVerify="true">
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                                <data android:scheme="http" />
                                <data android:scheme="https" />
                                <data android:host="www.twitter.com" />
                                <data android:host="twitter.com" />
                                <data android:host="*.twitter.com" />
                                <data android:host="*twitter.com" />
                                <data android:pathPattern="/vioside/.*" />
                            </intent-filter>
                            <tools:validation testUrl="https://twitter.com/vioside/status/761453456683069440" />
                            <tools:validation testUrl="https://www.twitter.com/vioside/status/761453456683069440" />
                        </activity>
                    </application>

                </manifest>
                """,
          )
          .indented()
      )
      .issues(TEST_URL)
      .run()
      .expectClean()
  }

  fun testValidation_hostPortPairs() {
    val expected =
      """
      AndroidManifest.xml:19: Error: Test URL did not match any of host+port example.com:8000, host+port twitter.com:8001 [TestAppLink]
                  <tools:validation testUrl="https://example.com:8001/" />
                                             ~~~~~~~~~~~~~~~~~~~~~~~~~
      1 errors, 0 warnings
      """
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher" >
                        <activity android:name=".MainActivity">
                            <intent-filter android:autoVerify="true">
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                                <data android:scheme="https" />
                                <data android:host="example.com" android:port="8000" />
                                <data android:host="twitter.com" android:port="8001" />
                            </intent-filter>
                            <tools:validation testUrl="https://example.com:8000/" />
                            <tools:validation testUrl="https://twitter.com:8001/" />
                            <tools:validation testUrl="https://example.com:8001/" />
                        </activity>
                    </application>
                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(expected)
  }

  fun testUrlMatchingWithOnlyPort() {
    val expected =
      """
      AndroidManifest.xml:9: Error: The port must be specified in the same <data> element as the host [AppLinkUrlError]
                      <data android:port="8000" />
                                          ~~~~
      1 errors, 0 warnings
      """
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="test.pkg" >
                    <application>
                        <activity android:name=".MainActivity">
                            <intent-filter>
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="http"/>
                                <data android:port="8000" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                            <tools:validation testUrl="http://:8000" />
                        </activity>
                    </application>
                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(expected)
  }

  fun testHostWildcardMatching() {
    val expected =
      """
            AndroidManifest.xml:15: Error: Test URL did not match host *.example.com [TestAppLink]
                        <tools:validation testUrl="http://example.com/path/foo/bar"/>
                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="test.pkg" >

                    <application>
                        <activity android:name=".MainActivity">
                            <intent-filter android:autoVerify="true">
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                                <data android:scheme="http"
                                    android:host="*.example.com"
                                    android:pathPrefix="/path" />
                            </intent-filter>
                            <tools:validation testUrl="http://example.com/path/foo/bar"/>
                            <tools:validation testUrl="http://.example.com/path/foo/bar"/>
                            <tools:validation testUrl="http://www.example.com/path/foo/bar"/>
                        </activity>
                    </application>

                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(expected)
  }

  fun testPortMatching() {
    val expected =
      """
      AndroidManifest.xml:38: Error: Test URL did not match host example.com or did not match host+port example.com:85 or did not match host+port android.com:86 [TestAppLink]
                  <tools:validation testUrl="http://android.com/path/foo/bar"/>
                                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
      1 errors, 0 warnings
      """
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="test.pkg" >

                    <application>
                        <activity android:name=".MainActivity">
                            <intent-filter android:autoVerify="true">
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                                <data android:scheme="http"
                                      android:host="example.com"
                                      android:pathPrefix="/path" />
                            </intent-filter>
                            <intent-filter android:autoVerify="true">
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                                <data android:scheme="http"
                                      android:host="example.com"
                                      android:port="85"
                                      android:pathPrefix="/path" />
                            </intent-filter>
                            <intent-filter android:autoVerify="true">
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                                <data android:scheme="http"
                                      android:host="android.com"
                                      android:port="86"
                                      android:pathPrefix="/path" />
                            </intent-filter>
                            <tools:validation testUrl="http://example.com/path/foo/bar"/>
                            <tools:validation testUrl="http://example.com:80/path/foo/bar"/>
                            <tools:validation testUrl="http://example.com/path/foo/bar"/>
                            <tools:validation testUrl="http://example.com:85/path/foo/bar"/>
                            <tools:validation testUrl="http://android.com:86/path/foo/bar"/>
                            <tools:validation testUrl="http://android.com/path/foo/bar"/>
                        </activity>
                    </application>

                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(expected)
  }

  fun testHostAndPortCombination() {
    // Host and port must be specified on the same element
    val expected =
      """
            AndroidManifest.xml:11: Error: The port must be specified in the same <data> element as the host [AppLinkUrlError]
                            <data android:port="80" />
                                                ~~
            1 errors, 0 warnings
            """
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg" >
                    <application>
                        <activity android:name=".MainActivity">
                            <intent-filter android:autoVerify="true">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="http"/>
                                <data android:host="example.com"
                                      android:port="81" />
                                <data android:host="example.com" />
                                <data android:port="80" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(expected)
  }

  fun testValidation_fileImplicitScheme() {
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
          <manifest xmlns:android="http://schemas.android.com/apk/res/android"
              xmlns:tools="http://schemas.android.com/tools"
              package="com.example.helloworld" >

              <application
                  android:allowBackup="true"
                  android:icon="@mipmap/ic_launcher" >
                  <activity android:name=".MainActivity">
                      <intent-filter>
                          <action android:name="android.intent.action.VIEW" />
                          <category android:name="android.intent.category.DEFAULT" />
                          <category android:name="android.intent.category.BROWSABLE" />
                          <data android:mimeType="application/pdf" />
                      </intent-filter>
                      <tools:validation testUrl="file://example.pdf" />
                  </activity>
              </application>
          </manifest>
          """,
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testValidPortNumber() {
    // Port numbers must be in the valid range
    val expected =
      """
            AndroidManifest.xml:8: Error: not a valid port number [AppLinkUrlError]
                                  android:port="-1" />
                                                ~~
            AndroidManifest.xml:10: Error: not a valid port number [AppLinkUrlError]
                                  android:port="128000" />
                                                ~~~~~~
            2 errors, 0 warnings
            """
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg" >
                    <application>
                        <activity android:name=".MainActivity">
                            <intent-filter>
                                <data android:scheme="http"/>
                                <data android:host="example.com"
                                      android:port="-1" />
                                <data android:host="example.com"
                                      android:port="128000" />
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(expected)
  }

  fun testNonEmpty() {
    // Attributes are not allowed to be empty
    val expected =
      """
            AndroidManifest.xml:6: Error: android:scheme cannot be empty [AppLinkUrlError]
                            <data android:scheme=""
                                  ~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:7: Error: android:host cannot be empty [AppLinkUrlError]
                                  android:host=""
                                  ~~~~~~~~~~~~~~~
            AndroidManifest.xml:8: Error: android:port cannot be empty [AppLinkUrlError]
                                  android:port=""
                                  ~~~~~~~~~~~~~~~
            AndroidManifest.xml:9: Error: android:pathPrefix cannot be empty [AppLinkUrlError]
                                  android:pathPrefix=""
                                  ~~~~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:10: Error: android:path cannot be empty [AppLinkUrlError]
                                  android:path=""
                                  ~~~~~~~~~~~~~~~
            AndroidManifest.xml:11: Error: android:pathPattern cannot be empty [AppLinkUrlError]
                                  android:pathPattern=""
                                  ~~~~~~~~~~~~~~~~~~~~~~
            6 errors, 0 warnings
            """
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg" >
                    <application>
                        <activity android:name=".MainActivity">
                            <intent-filter>
                                <data android:scheme=""
                                      android:host=""
                                      android:port=""
                                      android:pathPrefix=""
                                      android:path=""
                                      android:pathPattern=""
                                      android:mimeType=""/>
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(expected)
  }

  fun testNoTrailingSchemeColon() {
    // There should be no trailing colons for schemes
    val expected =
      """
            AndroidManifest.xml:6: Error: Don't include trailing colon in the scheme declaration [AppLinkUrlError]
                            <data android:scheme="http:"/>
                                                  ~~~~~
            AndroidManifest.xml:7: Error: Don't include trailing colon in the scheme declaration [AppLinkUrlError]
                            <data android:scheme="https:"/>
                                                  ~~~~~~
            2 errors, 0 warnings
            """
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg" >
                    <application>
                        <activity android:name=".MainActivity">
                            <intent-filter>
                                <data android:scheme="http:"/>
                                <data android:scheme="https:"/>
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(expected)
  }

  fun testWrongHostnameWildcard() {
    // Wildcard can only be at the beginning
    val expected =
      """
            AndroidManifest.xml:7: Error: The host wildcard (*) can only be the first character [AppLinkUrlError]
                            <data android:host="example.*.com"
                                                ~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg" >
                    <application>
                        <activity android:name=".MainActivity">
                            <intent-filter>
                                <data android:scheme="http"/>
                                <data android:host="example.*.com"
                 />
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(expected)
  }

  fun testLowerCase() {
    // Scheme, host and mime type are all case sensitive and should only use lower case
    val expected =
      """
            AndroidManifest.xml:7: Error: Scheme matching is case sensitive and should only use lower-case characters [AppLinkUrlError]
                            <data android:scheme="HTTP"
                                                  ~~~~
            AndroidManifest.xml:8: Error: Host matching is case sensitive and should only use lower-case characters [AppLinkUrlError]
                                  android:host="Example.Com"
                                                ~~~~~~~~~~~
            AndroidManifest.xml:12: Error: Mime-type matching is case sensitive and should only use lower-case characters [AppLinkUrlError]
                                  android:mimeType="MimeType"/>
                                                    ~~~~~~~~
            3 errors, 0 warnings
            """
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg" >
                    <application>
                        <activity android:name=".MainActivity">
                            <intent-filter>
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="HTTP"
                                      android:host="Example.Com"
                                      android:pathPrefix="/Foo"
                                      android:path="/Foo"
                                      android:pathPattern="/Foo"
                                      android:mimeType="MimeType"/>
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(expected)
  }

  fun testPathsBeginWithSlash() {
    // Paths should begin with /
    val expected =
      """
            AndroidManifest.xml:9: Error: android:pathPrefix attribute should start with /, but it is samplePrefix [AppLinkUrlError]
                                  android:pathPrefix="samplePrefix"
                                                      ~~~~~~~~~~~~
            AndroidManifest.xml:10: Error: android:path attribute should start with /, but it is samplePath [AppLinkUrlError]
                                  android:path="samplePath"
                                                ~~~~~~~~~~
            AndroidManifest.xml:11: Error: android:pathPattern attribute should start with / or .*, but it is samplePattern [AppLinkUrlError]
                                  android:pathPattern="samplePattern"/>
                                                       ~~~~~~~~~~~~~
            3 errors, 0 warnings
            """
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg" >
                    <application>
                        <activity android:name=".MainActivity">
                            <intent-filter android:autoVerify="true">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="http"
                                      android:host="example.com"
                                      android:pathPrefix="samplePrefix"
                                      android:path="samplePath"
                                      android:pathPattern="samplePattern"/>
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(expected)
      .expectFixDiffs(
        """
            Fix for AndroidManifest.xml line 9: Replace with /samplePrefix:
            @@ -9 +9
            -                       android:pathPrefix="samplePrefix"
            +                       android:pathPrefix="/samplePrefix"
            Fix for AndroidManifest.xml line 10: Replace with /samplePath:
            @@ -10 +10
            -                       android:path="samplePath"
            +                       android:path="/samplePath"
            """
      )
  }

  fun testSuppressWithOldId() {
    // Make sure that the ignore-issue mechanism works for both the current and the
    // previous issue id
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="test.pkg" >
                    <application>
                        <activity android:name=".MainActivity">
                            <intent-filter>
                                <data android:scheme="http"
                                      android:host="example.com"
                                      android:pathPattern="foo"
                                      tools:ignore="AppLinkUrlError"/>
                            </intent-filter>
                            <intent-filter>
                                <data android:scheme="http"
                                      android:host="example.com"
                                      android:pathPattern="foo"
                                      tools:ignore="GoogleAppIndexingUrlError"/>
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testWrongPathPrefix() {
    val expected =
      """
            AndroidManifest.xml:18: Error: android:pathPrefix attribute should start with /, but it is gizmos [AppLinkUrlError]
                                android:pathPrefix="gizmos" />
                                                    ~~~~~~
            1 errors, 0 warnings
            """
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >
                            <intent-filter android:autoVerify="true" android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="http"
                                    android:host="example.com"
                                    android:pathPrefix="gizmos" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                        <meta-data android:name="com.google.android.gms.version" android:value="@integer/google_play_services_version" />
                    </application>

                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(expected)
  }

  fun testWrongPort() {
    val expected =
      """
            AndroidManifest.xml:18: Error: not a valid port number [AppLinkUrlError]
                                android:port="ABCD"
                                              ~~~~
            1 errors, 0 warnings
            """
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >
                            <intent-filter android:autoVerify="true" android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="http"
                                    android:host="example.com"
                                    android:port="ABCD"
                                    android:pathPrefix="/gizmos" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                        <meta-data android:name="com.google.android.gms.version" android:value="@integer/google_play_services_version" />
                    </application>

                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(expected)
  }

  fun testSchemeAndHostMissing() {
    val expected =
      """
      AndroidManifest.xml:16: Error: At least one host must be specified [AppLinkUrlError]
                      <data android:pathPrefix="/gizmos" />
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
      AndroidManifest.xml:16: Error: At least one scheme must be specified [AppLinkUrlError]
                      <data android:pathPrefix="/gizmos" />
                      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
      2 errors, 0 warnings
      """
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >
                            <intent-filter android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:pathPrefix="/gizmos" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(expected)
      .verifyFixes()
      .window(1)
      .expectFixDiffs(
        """
                Fix for AndroidManifest.xml line 16: Set host:
                @@ -18 +18

                -                 <data android:pathPrefix="/gizmos" />
                +                 <data
                +                     android:host="[TODO]|"
                +                     android:pathPrefix="/gizmos" />
                Fix for AndroidManifest.xml line 16: Set scheme:
                @@ -18 +18

                -                 <data android:pathPrefix="/gizmos" />
                +                 <data
                +                     android:pathPrefix="/gizmos"
                +                     android:scheme="[TODO]|" />
                """
      )
  }

  fun testHostAndPathWithNoScheme() {
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >
                            <intent-filter android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                                <data android:host="example.com" />
                                <data android:path="/gizmos" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(
        """
        AndroidManifest.xml:18: Error: At least one scheme must be specified [AppLinkUrlError]
                        <data android:host="example.com" />
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """
      )
      .verifyFixes()
      .window(1)
      .expectFixDiffs(
        """
        Fix for AndroidManifest.xml line 18: Set scheme:
        @@ -21 +21

        -                 <data android:host="example.com" />
        +                 <data
        +                     android:host="example.com"
        +                     android:scheme="http[TODO]|" />
                          <data android:path="/gizmos" />
        """
      )
  }

  fun testMultiData() {
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >
                            <intent-filter android:autoVerify="true" android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="http" />
                                <data android:host="example.com" />
                                <data android:pathPrefix="/gizmos" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                        <meta-data android:name="com.google.android.gms.version" android:value="@integer/google_play_services_version" />
                    </application>

                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testMultiIntent() {
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                            <intent-filter android:autoVerify="true" android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="http"
                                    android:host="example.com"
                                    android:pathPrefix="/gizmos" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                        <meta-data android:name="com.google.android.gms.version" android:value="@integer/google_play_services_version" />
                    </application>

                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testMultiIntentWithError() {
    val expected =
      """
            AndroidManifest.xml:20: Error: At least one host must be specified [AppLinkUrlError]
                            <data android:scheme="http"
                            ^
            1 errors, 0 warnings
            """
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                            <intent-filter android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="http"
                                    android:pathPrefix="/gizmos" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                        <meta-data android:name="com.google.android.gms.version" android:value="@integer/google_play_services_version" />
                    </application>

                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(expected)
      .verifyFixes()
      .window(1)
      .expectFixDiffs(
        """
                Fix for AndroidManifest.xml line 20: Set host:
                @@ -24 +24
                                  <data
                +                     android:host="[TODO]|"
                                      android:pathPrefix="/gizmos"
                """
      )
  }

  fun testNotExported() {
    val expected =
      """
      AndroidManifest.xml:6: Error: Activity supporting ACTION_VIEW is not exported [AppLinkUrlError]
              <activity android:name=".MainActivity"
              ^
      1 errors, 0 warnings
      """
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:theme="@style/AppTheme" >
                        <activity android:name=".MainActivity"
                            android:exported="false"
                            android:theme="@style/FullscreenTheme" >
                            <intent-filter android:autoVerify="true" android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="http"
                                    android:host="example1.com"
                                    android:pathPrefix="/gizmos" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                            <intent-filter android:autoVerify="true" android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="http"
                                    android:host="example2.com"
                                    android:pathPrefix="/gizmos" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                        <activity android:name=".SecondActivity" android:exported="true">
                            <intent-filter android:autoVerify="true" android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="http"
                                    android:host="example1.com"
                                    android:pathPrefix="/gizmos" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(expected)
  }

  fun testOkWithResource() {
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                          package="com.example.helloworld">

                    <application
                            android:allowBackup="true"
                            android:icon="@mipmap/ic_launcher"
                            android:label="@string/app_name"
                            android:theme="@style/AppTheme" >
                        <activity
                                android:name=".FullscreenActivity"
                                android:configChanges="orientation|keyboardHidden|screenSize"
                                android:label="@string/title_activity_fullscreen"
                                android:theme="@style/FullscreenTheme" >
                            <intent-filter android:autoVerify="true" android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="http"
                                      android:host="example.com"
                                      android:pathPrefix="@string/path_prefix"
                                      android:port="@string/port"/>
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>

                        <meta-data android:name="com.google.android.gms.version" android:value="@integer/google_play_services_version" />
                    </application>

                </manifest>
                """,
          )
          .indented(),
        xml(
            "res/values/appindexing_strings.xml",
            """
                <resources>
                    <string name="path_prefix">/pathprefix</string>
                    <string name="port">8080</string>
                </resources>
                """,
          )
          .indented(),
      )
      .incremental("AndroidManifest.xml")
      .run()
      .expectClean()
  }

  fun testWrongWithResource() {
    val expected =
      """
            AndroidManifest.xml:18: Error: android:pathPrefix attribute should start with /, but it is pathprefix [AppLinkUrlError]
                                  android:pathPrefix="@string/path_prefix"
                                                      ~~~~~~~~~~~~~~~~~~~
            AndroidManifest.xml:19: Error: not a valid port number [AppLinkUrlError]
                                  android:port="@string/port"/>
                                                ~~~~~~~~~~~~
            2 errors, 0 warnings
            """
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                          package="com.example.helloworld">

                    <application
                            android:allowBackup="true"
                            android:icon="@mipmap/ic_launcher"
                            android:label="@string/app_name"
                            android:theme="@style/AppTheme" >
                        <activity
                                android:name=".FullscreenActivity"
                                android:configChanges="orientation|keyboardHidden|screenSize"
                                android:label="@string/title_activity_fullscreen"
                                android:theme="@style/FullscreenTheme" >
                            <intent-filter android:autoVerify="true" android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="http"
                                      android:host="example.com"
                                      android:pathPrefix="@string/path_prefix"
                                      android:port="@string/port"/>
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>

                        <meta-data android:name="com.google.android.gms.version" android:value="@integer/google_play_services_version" />
                    </application>

                </manifest>
                """,
          )
          .indented(),
        xml(
            "res/values/appindexing_wrong_strings.xml",
            """
                <resources>
                    <string name="path_prefix">pathprefix</string>
                    <string name="port">gizmos</string>
                </resources>
                """,
          )
          .indented(),
      )
      .incremental("AndroidManifest.xml")
      .run()
      .expect(expected)
  }

  fun testNoUri() {
    val expectedCheckMessages =
      """
      AndroidManifest.xml:16: Error: VIEW actions require a URI [AppLinkUrlError]
                      <data />
                      ~~~~~~~~
      1 errors, 0 warnings
      """
    val expectedFixDiff =
      """
      Fix for AndroidManifest.xml line 16: Set scheme:
      @@ -18 +18
      -                 <data />
      +                 <data android:scheme="[TODO]|" />
      Fix for AndroidManifest.xml line 16: Set mimeType:
      @@ -18 +18
      -                 <data />
      +                 <data android:mimeType="[TODO]|" />
      """
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >
                            <intent-filter android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(expectedCheckMessages)
      .expectFixDiffs(expectedFixDiff)
  }

  fun testImplicitSchemeBecauseOfMimeType() {
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >
                            <intent-filter android:label="@string/title_activity_fullscreen">
                                <data android:mimeType="mimetype" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                  </application>
                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testViewWithMimeType() {
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >
                            <intent-filter android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:mimeType="mimetype" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                  </application>
                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testDataMissing() {
    val expected =
      """
            AndroidManifest.xml:14: Error: Missing data element [AppLinkUrlError]
                        <intent-filter android:label="@string/title_activity_fullscreen">
                        ^
            1 errors, 0 warnings
            """
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >
                            <intent-filter android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                        <meta-data android:name="com.google.android.gms.version" android:value="@integer/google_play_services_version" />
                    </application>

                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(expected)
  }

  fun testNotBrowsable() {
    val expected =
      """
            AndroidManifest.xml:24: Error: Activity supporting ACTION_VIEW is not set as BROWSABLE [AppLinkUrlError]
                        <intent-filter android:label="@string/title_activity_fullscreen">
                        ^
            1 errors, 0 warnings
            """
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".MainActivity"
                            android:label="@string/app_name" >
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />

                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>

                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >
                            <intent-filter android:label="@string/title_activity_fullscreen">
                                <action android:name="android.intent.action.VIEW" />
                                <data android:scheme="http"
                                    android:host="example.com"
                                    android:pathPrefix="/gizmos" />
                                <category android:name="android.intent.category.DEFAULT" />
                            </intent-filter>
                        </activity>
                        <meta-data android:name="com.google.android.gms.version" android:value="@integer/google_play_services_version" />
                    </application>

                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(expected)
  }

  fun testDataBinding() {
    // When using data binding don't give incorrect validation messages such as
    // uppercase usage, missing slash prefix etc.
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="test.pkg" >

                    <application>
                        <activity android:name=".MainActivity">
                            <intent-filter android:autoVerify="true">
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                                <data android:scheme="@={Schemes.default}"
                                    android:host="@{Hosts.lookup}"
                                    android:pathPrefix="@{Prefixes.lookup}" />
                            </intent-filter>
                            <tools:validation testUrl="http://example.com/gizmos/foo/bar"/>
                        </activity>
                    </application>

                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun test37343746() {
    // Regression test for https://issuetracker.google.com/issues/37343746
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="test.pkg" >

                    <application>
                        <activity android:name=".MainActivity">
                            <intent-filter>
                                 <action android:name="android.intent.action.PROVIDER_CHANGED"/>
                                 <data android:scheme="content"/>
                                 <data android:host="＄{applicationId}.provider"/>
                                 <data android:path="/beep/boop"/>
                                 <data android:mimeType="*/*"/>
                             </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun test79995047() {
    // Regression test for https://issuetracker.google.com/issues/79995047
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="test.pkg" >

                    <application>
                        <activity android:name=".MainActivity">
                            <intent-filter>
                                <!-- Following https://developer.android.com/guide/topics/providers/content-provider-basics#MIMETypeReference -->
                                <data android:mimeType="vnd.android.cursor.item/vnd.＄{applicationId}.item" /> <!-- OK -->
                            </intent-filter>
                            <intent-filter>
                                <data android:mimeType="vnd.android.cursor.item/vnd.＄{placeholder}.item" /> <!-- WARN -->
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """,
          )
          .indented(),
        gradle(
            """
                android {
                    defaultConfig {
                        manifestPlaceholders = [ placeholder:"ABC"]
                    }
                }
                """
          )
          .indented(),
      )
      .run()
      .expect(
        """
            src/main/AndroidManifest.xml:12: Error: Mime-type matching is case sensitive and should only use lower-case characters (without placeholders, value is vnd.android.cursor.item/vnd.ABC.item) [AppLinkUrlError]
                            <data android:mimeType="vnd.android.cursor.item/vnd.＄{placeholder}.item" /> <!-- WARN -->
                                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            1 errors, 0 warnings
            """
      )
  }

  fun suggestAddHost_whenCustomSchemeAndPathArePresent() {
    // Regression test for https://issuetracker.google.com/62810553
    val expected =
      """
    AndroidManifest.xml:8: Error: At least one host must be specified [AppLinkUrlError]
                    <data android:scheme="myscheme" android:pathPrefix="/path/to/there"/> <!-- Missing host -->
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    1 errors, 0 warnings
    """
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="test.pkg" >

                    <application>
                        <activity android:name=".MainActivity">
                            <intent-filter>
                                <data android:scheme="myscheme" android:pathPrefix="/path/to/there"/> <!-- Missing host -->
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(expected)
  }

  fun test68322249() {
    // Regression test for https://issuetracker.google.com/issues/68322249
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:tools="http://schemas.android.com/tools"
                    package="test.pkg" >

                    <application>
                        <activity android:name=".MainActivity">
                            <intent-filter android:autoVerify="true">
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                                <data
                                    android:scheme="http"
                                    android:host="example.com"
                                    android:pathPrefix="＄{DEEP_LINK_PREFIX}" />
                            </intent-filter>
                        </activity>
                    </application>

                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testStaticValidation() {
    // Usage outside of lint
    val document =
      XmlUtils.parseDocument(
        """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:tools="http://schemas.android.com/tools"
                package="test.pkg" >

                <application>
                    <activity android:name=".MainActivity">
                        <intent-filter android:autoVerify="true">
                            <data android:scheme="http"
                                android:host="example.com"
                                android:pathPrefix="/gizmos" />
                            <data android:path="/literal/path" />
                        </intent-filter>
                        <tools:validation testUrl="http://example.com/gizmos/foo/bar"/>
                        <tools:validation testUrl="http://example.com/notmatch/foo/bar"/>
                        <tools:validation testUrl="http://notmatch.com/gizmos/foo/bar"/>
                        <tools:validation testUrl="https://example.com/gizmos/foo/bar"/>
                        <tools:validation testUrl="http://example.com/literal/path"/>
                    </activity>
                </application>

            </manifest>
            """,
        true,
      )
    val root = document.documentElement
    val application = XmlUtils.getFirstSubTag(root)
    val activity = XmlUtils.getFirstSubTag(application)
    assertThat(activity).isNotNull()

    val detector = AppLinksValidDetector()
    fun createUriInfos(
      activity: Element,
      context: XmlContext,
    ): List<AppLinksValidDetector.UriInfo> =
      detector.checkActivityIntentFiltersAndGetUriInfos(activity, context)

    fun testElement(testUrl: URL, infos: List<AppLinksValidDetector.UriInfo>): String? =
      detector.checkTestUrlMatchesAtLeastOneInfo(testUrl, infos)

    val infos =
      createUriInfos(
        activity!!,
        mock<XmlContext>().apply {
          whenever(getLocation(any())).thenReturn(mock())
          whenever(client).thenReturn(mock())
          whenever(driver).thenReturn(mock())
          whenever(project).thenReturn(mock())
        },
      )
    assertThat(testElement(URL("http://example.com/literal/path"), infos)).isNull() // success
    assertThat(testElement(URL("http://example.com/gizmos/foo/bar"), infos)).isNull() // success
    assertThat(testElement(URL("https://example.com/gizmos/foo/bar"), infos))
      .isEqualTo("Test URL did not match scheme http")
    assertThat(testElement(URL("http://example.com/notmatch/foo/bar"), infos))
      .isEqualTo("Test URL did not match path prefix /gizmos, path literal /literal/path")
    assertThat(testElement(URL("http://notmatch.com/gizmos/foo/bar"), infos))
      .isEqualTo("Test URL did not match host example.com")
  }

  fun testAutoVerifyMissingAttributes() {
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >

                            <intent-filter android:autoVerify="true"> <!-- Fine -->
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />

                                <data android:scheme="http" />
                                <data android:scheme="https" />

                                <data android:host="example.com" />
                                <data android:pathPrefix="/gizmos" />
                            </intent-filter>

                            <intent-filter android:autoVerify="true"> <!-- Missing VIEW -->
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />

                                <data android:scheme="http" />
                                <data android:scheme="https" />

                                <data android:host="example.com" />
                                <data android:pathPrefix="/gizmos" />
                            </intent-filter>

                            <intent-filter android:autoVerify="true"> <!-- Missing DEFAULT -->
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.BROWSABLE" />

                                <data android:scheme="http" />
                                <data android:scheme="https" />

                                <data android:host="example.com" />
                                <data android:pathPrefix="/gizmos" />
                            </intent-filter>

                            <intent-filter android:autoVerify="true"> <!-- Missing BROWSABLE -->
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />

                                <data android:scheme="http" />
                                <data android:scheme="https" />

                                <data android:host="example.com" />
                                <data android:pathPrefix="/gizmos" />
                            </intent-filter>

                            <intent-filter android:autoVerify="true"> <!-- Has custom scheme, but missing http -->
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />

                                <data android:scheme="other" />

                                <data android:host="example.com" />
                                <data android:pathPrefix="/gizmos" />
                            </intent-filter>

                            <intent-filter android:autoVerify="true"> <!-- Has no scheme -->
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />

                                <data android:host="example.com" />
                                <data android:pathPrefix="/gizmos" />
                            </intent-filter>

                            <intent-filter android:autoVerify="true"> <!-- Missing host -->
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />

                                <data android:scheme="http" />
                                <data android:scheme="https" />
                            </intent-filter>

                            <intent-filter android:autoVerify="true"> <!-- No data tags at all -->
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(
        """
          AndroidManifest.xml:27: Error: Missing required elements/attributes for Android App Links [AppLinkUrlError]
                      <intent-filter android:autoVerify="true"> <!-- Missing VIEW -->
                      ^
          AndroidManifest.xml:38: Error: Missing required elements/attributes for Android App Links [AppLinkUrlError]
                      <intent-filter android:autoVerify="true"> <!-- Missing DEFAULT -->
                      ^
          AndroidManifest.xml:49: Error: Activity supporting ACTION_VIEW is not set as BROWSABLE [AppLinkUrlError]
                      <intent-filter android:autoVerify="true"> <!-- Missing BROWSABLE -->
                      ^
          AndroidManifest.xml:49: Error: Missing required elements/attributes for Android App Links [AppLinkUrlError]
                      <intent-filter android:autoVerify="true"> <!-- Missing BROWSABLE -->
                      ^
          AndroidManifest.xml:60: Error: Missing required elements/attributes for Android App Links [AppLinkUrlError]
                      <intent-filter android:autoVerify="true"> <!-- Has custom scheme, but missing http -->
                      ^
          AndroidManifest.xml:76: Error: At least one scheme must be specified [AppLinkUrlError]
                          <data android:host="example.com" />
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          AndroidManifest.xml:80: Error: Missing required elements/attributes for Android App Links [AppLinkUrlError]
                      <intent-filter android:autoVerify="true"> <!-- Missing host -->
                      ^
          AndroidManifest.xml:89: Error: Missing data element [AppLinkUrlError]
                      <intent-filter android:autoVerify="true"> <!-- No data tags at all -->
                      ^
          8 errors, 0 warnings
        """
      )
  }

  fun testAddAutoVerifySuggestion() {
    lint()
      .issues(APP_LINK_WARNING)
      .files(
        xml(
            "AndroidManifest.xml",
            """
          <manifest xmlns:android="http://schemas.android.com/apk/res/android"
              package="com.example.helloworld" >

              <application>
                  <activity android:name=".FullscreenActivity">
                      <intent-filter> <!-- We expect a warning here -->
                          <action android:name="android.intent.action.VIEW" />
                          <category android:name="android.intent.category.DEFAULT" />
                          <category android:name="android.intent.category.BROWSABLE" />

                          <data android:scheme="http" />
                          <data android:scheme="https" />

                          <data android:host="example.com" />
                          <data android:pathPrefix="/gizmos" />
                      </intent-filter>

                      <intent-filter> <!-- Missing VIEW -->
                          <category android:name="android.intent.category.DEFAULT" />
                          <category android:name="android.intent.category.BROWSABLE" />

                          <data android:scheme="http" />
                          <data android:scheme="https" />

                          <data android:host="example.com" />
                          <data android:pathPrefix="/gizmos" />
                      </intent-filter>

                      <intent-filter> <!-- Missing DEFAULT -->
                          <action android:name="android.intent.action.VIEW" />
                          <category android:name="android.intent.category.BROWSABLE" />

                          <data android:scheme="http" />
                          <data android:scheme="https" />

                          <data android:host="example.com" />
                          <data android:pathPrefix="/gizmos" />
                      </intent-filter>

                      <intent-filter> <!-- Missing BROWSABLE -->
                          <action android:name="android.intent.action.VIEW" />
                          <category android:name="android.intent.category.DEFAULT" />

                          <data android:scheme="http" />
                          <data android:scheme="https" />

                          <data android:host="example.com" />
                          <data android:pathPrefix="/gizmos" />
                      </intent-filter>

                      <intent-filter> <!-- Has custom scheme, missing http -->
                          <action android:name="android.intent.action.VIEW" />
                          <category android:name="android.intent.category.DEFAULT" />
                          <category android:name="android.intent.category.BROWSABLE" />

                          <data android:scheme="other" />

                          <data android:host="example.com" />
                          <data android:pathPrefix="/gizmos" />
                      </intent-filter>

                      <intent-filter> <!-- Has no scheme -->
                          <action android:name="android.intent.action.VIEW" />
                          <category android:name="android.intent.category.DEFAULT" />
                          <category android:name="android.intent.category.BROWSABLE" />

                          <data android:host="example.com" />
                          <data android:pathPrefix="/gizmos" />
                      </intent-filter>

                      <intent-filter> <!-- Missing host -->
                          <action android:name="android.intent.action.VIEW" />
                          <category android:name="android.intent.category.DEFAULT" />
                          <category android:name="android.intent.category.BROWSABLE" />

                          <data android:scheme="http" />
                          <data android:scheme="https" />
                      </intent-filter>

                      <intent-filter android:autoVerify="false"> <!-- We would usually expect a warning here, but it has autoVerify="false" -->
                          <action android:name="android.intent.action.VIEW" />
                          <category android:name="android.intent.category.DEFAULT" />
                          <category android:name="android.intent.category.BROWSABLE" />

                          <data android:scheme="http" />
                          <data android:scheme="https" />

                          <data android:host="example.com" />
                          <data android:pathPrefix="/gizmos" />
                      </intent-filter>
                  </activity>
              </application>
          </manifest>
          """,
          )
          .indented()
      )
      .run()
      .expect(
        """
        AndroidManifest.xml:6: Warning: This intent filter has the format of an Android App Link but is missing the autoVerify attribute; add android:autoVerify="true" to ensure your domain will be validated and enable App Link-related Lint warnings. If you do not want clicked URLs to bring the user to your app, remove the android.intent.category.BROWSABLE category, or set android:autoVerify="false" to make it clear this is not intended to be an Android App Link. [AppLinkWarning]
                    <intent-filter> <!-- We expect a warning here -->
                     ~~~~~~~~~~~~~
        0 errors, 1 warnings
        """
      )
      .expectFixDiffs(
        """
      Fix for AndroidManifest.xml line 6: Set autoVerify="true":
      @@ -7 +7
      -             <intent-filter> <!-- We expect a warning here -->
      +             <intent-filter android:autoVerify="true" > <!-- We expect a warning here -->
      """
      )
  }

  fun test365376495() {
    // Regression test for b365376495
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity android:name=".FullscreenActivity">

                            <intent-filter>
                                <action android:name="com.google.android.apps.gmm.GENERIC_WEBVIEW_NOTIFICATION" />
                                <data android:scheme="http" />
                                <data android:scheme="https" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testPathMatcherOrdering() {
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >

                            <intent-filter android:autoVerify="true">
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />

                                <data android:scheme="http" />
                                <data android:scheme="https"
                                      android:host="example.com"
                                      android:pathPrefix="/gizmos"
                                      android:pathAdvancedPattern="[A-Z]gizmos"
                                      android:pathPattern=".*gizmos"
                                      android:path="/gizmos"
                                      android:pathSuffix="gizmos" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """,
          )
          .indented()
      )
      .issues(AppLinksValidDetector.INTENT_FILTER_UNIQUE_DATA_ATTRIBUTES)
      .run()
      .expectFixDiffs(
        """
        Autofix for AndroidManifest.xml line 21: Replace with <data android:scheme="https" />...:
        @@ -21 +21
        -                 <data android:scheme="https"
        -                       android:host="example.com"
        -                       android:pathPrefix="/gizmos"
        -                       android:pathAdvancedPattern="[A-Z]gizmos"
        -                       android:pathPattern=".*gizmos"
        -                       android:path="/gizmos"
        -                       android:pathSuffix="gizmos" />
        +                 <data android:scheme="https" />
        +                 <data android:host="example.com" />
        +                 <data android:path="/gizmos" />
        +                 <data android:pathPrefix="/gizmos" />
        +                 <data android:pathPattern=".*gizmos" />
        +                 <data android:pathAdvancedPattern="[A-Z]gizmos" />
        +                 <data android:pathSuffix="gizmos" />
      """
      )
  }

  fun testPattern_startWithSlashOk() {
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >

                            <intent-filter android:autoVerify="true">
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />

                                <data android:scheme="http" />
                                <data android:host="example.com" />
                                <data android:pathPattern="/gizmos" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testPattern_startWithDotStarOk() {
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >

                            <intent-filter android:autoVerify="true">
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />

                                <data android:scheme="http" />
                                <data android:host="example.com" />
                                <data android:pathPattern=".*gizmos" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testSuffixValidation() {
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >

                            <intent-filter android:autoVerify="true">
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />

                                <data android:scheme="http" />
                                <data android:host="example.com" />
                                <data android:pathSuffix="" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(
        """
        AndroidManifest.xml:22: Error: android:pathSuffix cannot be empty [AppLinkUrlError]
                        <data android:pathSuffix="" />
                              ~~~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
      """
      )
      .expectFixDiffs("")
  }

  fun testAdvancedPatternValidation() {
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >

                            <intent-filter android:autoVerify="true">
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />

                                <data android:scheme="http" />
                                <data android:host="example.com" />
                                <data android:pathAdvancedPattern="" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(
        """
        AndroidManifest.xml:21: Error: android:pathAdvancedPattern cannot be empty [AppLinkUrlError]
                        <data android:pathAdvancedPattern="" />
                              ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
      """
      )
  }

  fun testPortByItselfNotCounted() {
    // This test makes sure that a port by itself cannot be counted as a "host" (which could happen
    // due to host-port pairings).
    // Compare with the test below. Here, we should get a "Missing required elements/attributes for
    // Android App Links" error.
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >

                            <intent-filter android:autoVerify="true">
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />

                                <data android:scheme="http" />
                                <data android:port="8080" />
                                <data android:path="/path" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expect(
        """
          AndroidManifest.xml:15: Error: Missing required elements/attributes for Android App Links [AppLinkUrlError]
                      <intent-filter android:autoVerify="true">
                      ^
          AndroidManifest.xml:20: Error: At least one host must be specified [AppLinkUrlError]
                          <data android:scheme="http" />
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          AndroidManifest.xml:21: Error: The port must be specified in the same <data> element as the host [AppLinkUrlError]
                          <data android:port="8080" />
                                              ~~~~
          3 errors, 0 warnings
      """
      )
  }

  fun testHostByItselfIsCounted() {
    // Compare with the test above.
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application
                        android:allowBackup="true"
                        android:icon="@mipmap/ic_launcher"
                        android:label="@string/app_name"
                        android:theme="@style/AppTheme" >
                        <activity
                            android:name=".FullscreenActivity"
                            android:configChanges="orientation|keyboardHidden|screenSize"
                            android:label="@string/title_activity_fullscreen"
                            android:theme="@style/FullscreenTheme" >

                            <intent-filter android:autoVerify="true">
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />

                                <data android:scheme="http" />
                                <data android:host="example.com" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """,
          )
          .indented()
      )
      .run()
      .expectClean()
  }

  fun testSameMessage() {
    assertThat(
        AppLinksValidDetector()
          .sameMessage(VALIDATION, new = "VIEW actions require a URI", old = "Missing URL")
      )
      .isTrue()
  }

  fun test_queryParameter() {
    lint()
      .files(
        gradle(
          """
          apply plugin: 'com.android.application'

          android {
              compileSdkVersion 35

              defaultConfig {
                  minSdkVersion 30
                  targetSdkVersion 35
              }
          }
        """
        ),
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >
                    <uses-sdk android:compileSdkVersion="35" android:minSdkVersion="31" android:targetSdkVersion="35" />

                    <application>
                        <activity android:name=".FullscreenActivity" android:exported="true">

                            <intent-filter android:autoVerify="true">
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />

                                <data android:scheme="http" />
                                <data android:host="example.com" />
                                <data android:path="/gizmos?queryParam=1&amp;otherParam=2" />
                                <data android:pathPrefix="/gizmos?queryParam" />
                                <data android:pathSuffix="/gizmos?queryParam" />
                                <data android:pathPattern="/gizmos?queryParam" />
                                <data android:pathAdvancedPattern="/gizmos?queryParam" />
                            </intent-filter>
                            <intent-filter>
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />

                                <data android:scheme="custom" />
                                <data android:host="example.com" />
                                <data android:path="/gizmos?queryParam=1&amp;otherParam=2" />
                                <data android:pathPrefix="/gizmos?queryParam" />
                                <data android:pathSuffix="/gizmos?queryParam" />
                                <data android:pathPattern="/gizmos?queryParam" />
                                <data android:pathAdvancedPattern="/gizmos?queryParam" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """,
          )
          .indented(),
      )
      .run()
      .expect(
        """
        src/main/AndroidManifest.xml:15: Error: App link matching does not support query parameters or fragments, unless using <uri-relative-filter-group> (introduced in Android 15) [AppLinkUrlError]
                        <data android:path="/gizmos?queryParam=1&amp;otherParam=2" />
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/main/AndroidManifest.xml:16: Error: App link matching does not support query parameters or fragments, unless using <uri-relative-filter-group> (introduced in Android 15) [AppLinkUrlError]
                        <data android:pathPrefix="/gizmos?queryParam" />
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/main/AndroidManifest.xml:17: Error: App link matching does not support query parameters or fragments, unless using <uri-relative-filter-group> (introduced in Android 15) [AppLinkUrlError]
                        <data android:pathSuffix="/gizmos?queryParam" />
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/main/AndroidManifest.xml:18: Error: App link matching does not support query parameters or fragments, unless using <uri-relative-filter-group> (introduced in Android 15) [AppLinkUrlError]
                        <data android:pathPattern="/gizmos?queryParam" />
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/main/AndroidManifest.xml:18: Error: pathPattern does not support ? as a Regex character [AppLinkUrlError]
                        <data android:pathPattern="/gizmos?queryParam" />
                                                   ~~~~~~~~~~~~~~~~~~
        src/main/AndroidManifest.xml:19: Error: App link matching does not support query parameters or fragments, unless using <uri-relative-filter-group> (introduced in Android 15) [AppLinkUrlError]
                        <data android:pathAdvancedPattern="/gizmos?queryParam" />
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/main/AndroidManifest.xml:19: Error: pathAdvancedPattern does not support ? as a Regex character [AppLinkUrlError]
                        <data android:pathAdvancedPattern="/gizmos?queryParam" />
                                                           ~~~~~~~~~~~~~~~~~~
        src/main/AndroidManifest.xml:31: Error: pathPattern does not support ? as a Regex character [AppLinkUrlError]
                        <data android:pathPattern="/gizmos?queryParam" />
                                                   ~~~~~~~~~~~~~~~~~~
        src/main/AndroidManifest.xml:32: Error: pathAdvancedPattern does not support ? as a Regex character [AppLinkUrlError]
                        <data android:pathAdvancedPattern="/gizmos?queryParam" />
                                                           ~~~~~~~~~~~~~~~~~~
        9 errors, 0 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for src/main/AndroidManifest.xml line 15: Replace with <uri-relative-filter-group>...:
        @@ -15 +15
        -                 <data android:path="/gizmos?queryParam=1&amp;otherParam=2" />
        +                 <uri-relative-filter-group>
        +                     <data android:path="/gizmos" />
        +                     <data android:query="otherParam=2" />
        +                     <data android:query="queryParam=1" />
        +                 </uri-relative-filter-group>
        Fix for src/main/AndroidManifest.xml line 16: Replace with <uri-relative-filter-group>...:
        @@ -16 +16
        -                 <data android:pathPrefix="/gizmos?queryParam" />
        +                 <uri-relative-filter-group>
        +                     <data android:pathPrefix="/gizmos" />
        +                     <data android:query="queryParam" />
        +                 </uri-relative-filter-group>
        Fix for src/main/AndroidManifest.xml line 17: Replace with <uri-relative-filter-group>...:
        @@ -17 +17
        -                 <data android:pathSuffix="/gizmos?queryParam" />
        +                 <uri-relative-filter-group>
        +                     <data android:pathSuffix="/gizmos" />
        +                     <data android:query="queryParam" />
        +                 </uri-relative-filter-group>
        Fix for src/main/AndroidManifest.xml line 18: Replace with <uri-relative-filter-group>...:
        @@ -18 +18
        -                 <data android:pathPattern="/gizmos?queryParam" />
        +                 <uri-relative-filter-group>
        +                     <data android:pathPattern="/gizmos" />
        +                     <data android:query="queryParam" />
        +                 </uri-relative-filter-group>
        Fix for src/main/AndroidManifest.xml line 19: Replace with <uri-relative-filter-group>...:
        @@ -19 +19
        -                 <data android:pathAdvancedPattern="/gizmos?queryParam" />
        +                 <uri-relative-filter-group>
        +                     <data android:pathAdvancedPattern="/gizmos" />
        +                     <data android:query="queryParam" />
        +                 </uri-relative-filter-group>
        """
      )
  }

  fun test_fragment() {
    lint()
      .files(
        gradle(
          """
          apply plugin: 'com.android.application'

          android {
              compileSdkVersion 35

              defaultConfig {
                  minSdkVersion 30
                  targetSdkVersion 35
              }
          }
        """
        ),
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >
                    <uses-sdk android:minSdkVersion="31" android:targetSdkVersion="35" />

                    <application>
                        <activity android:name=".FullscreenActivity" android:exported="true">

                            <intent-filter android:autoVerify="true">
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />

                                <data android:scheme="http" />
                                <data android:host="example.com" />
                                <data android:path="/gizmos#fragment=1&amp;otherFragment=2" />
                                <data android:pathPrefix="/gizmos#fragment!" /> <!-- Special character -->
                                <data android:pathSuffix="/gizmos#fragment" />
                                <data android:pathPattern="/gizmos#fragment" />
                                <data android:pathAdvancedPattern="/gizmos#fragment" />
                            </intent-filter>
                            <intent-filter>
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />

                                <data android:scheme="custom" />
                                <data android:host="example.com" />
                                <data android:path="/gizmos#fragment=1&amp;otherFragment=2" />
                                <data android:pathPrefix="/gizmos#fragment" />
                                <data android:pathSuffix="/gizmos#fragment" />
                                <data android:pathPattern="/gizmos#fragment" />
                                <data android:pathAdvancedPattern="/gizmos#fragment" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """,
          )
          .indented(),
      )
      .run()
      .expect(
        """
        src/main/AndroidManifest.xml:15: Error: App link matching does not support query parameters or fragments, unless using <uri-relative-filter-group> (introduced in Android 15) [AppLinkUrlError]
                        <data android:path="/gizmos#fragment=1&amp;otherFragment=2" />
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/main/AndroidManifest.xml:16: Error: App link matching does not support query parameters or fragments, unless using <uri-relative-filter-group> (introduced in Android 15) [AppLinkUrlError]
                        <data android:pathPrefix="/gizmos#fragment!" /> <!-- Special character -->
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/main/AndroidManifest.xml:17: Error: App link matching does not support query parameters or fragments, unless using <uri-relative-filter-group> (introduced in Android 15) [AppLinkUrlError]
                        <data android:pathSuffix="/gizmos#fragment" />
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/main/AndroidManifest.xml:18: Error: App link matching does not support query parameters or fragments, unless using <uri-relative-filter-group> (introduced in Android 15) [AppLinkUrlError]
                        <data android:pathPattern="/gizmos#fragment" />
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/main/AndroidManifest.xml:19: Error: App link matching does not support query parameters or fragments, unless using <uri-relative-filter-group> (introduced in Android 15) [AppLinkUrlError]
                        <data android:pathAdvancedPattern="/gizmos#fragment" />
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        5 errors, 0 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for src/main/AndroidManifest.xml line 15: Replace with <uri-relative-filter-group>...:
        @@ -15 +15
        -                 <data android:path="/gizmos#fragment=1&amp;otherFragment=2" />
        +                 <uri-relative-filter-group>
        +                     <data android:path="/gizmos" />
        +                     <data android:fragment="fragment=1&otherFragment=2" />
        +                 </uri-relative-filter-group>
        Fix for src/main/AndroidManifest.xml line 16: Replace with <uri-relative-filter-group>...:
        @@ -16 +16
        -                 <data android:pathPrefix="/gizmos#fragment!" /> <!-- Special character -->
        +                 <uri-relative-filter-group>
        +                     <data android:pathPrefix="/gizmos" />
        +                     <data android:fragment="fragment!" />
        +                 </uri-relative-filter-group> <!-- Special character -->
        Fix for src/main/AndroidManifest.xml line 17: Replace with <uri-relative-filter-group>...:
        @@ -17 +17
        -                 <data android:pathSuffix="/gizmos#fragment" />
        +                 <uri-relative-filter-group>
        +                     <data android:pathSuffix="/gizmos" />
        +                     <data android:fragment="fragment" />
        +                 </uri-relative-filter-group>
        Fix for src/main/AndroidManifest.xml line 18: Replace with <uri-relative-filter-group>...:
        @@ -18 +18
        -                 <data android:pathPattern="/gizmos#fragment" />
        +                 <uri-relative-filter-group>
        +                     <data android:pathPattern="/gizmos" />
        +                     <data android:fragment="fragment" />
        +                 </uri-relative-filter-group>
        Fix for src/main/AndroidManifest.xml line 19: Replace with <uri-relative-filter-group>...:
        @@ -19 +19
        -                 <data android:pathAdvancedPattern="/gizmos#fragment" />
        +                 <uri-relative-filter-group>
        +                     <data android:pathAdvancedPattern="/gizmos" />
        +                     <data android:fragment="fragment" />
        +                 </uri-relative-filter-group>
        """
      )
  }

  fun test_queryParamAndFragment() {
    lint()
      .files(
        gradle(
          """
          apply plugin: 'com.android.application'

          android {
              compileSdkVersion 35

              defaultConfig {
                  minSdkVersion 30
                  targetSdkVersion 35
              }
          }
        """
        ),
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >
                    <uses-sdk android:minSdkVersion="31" android:targetSdkVersion="35" />

                    <application>
                        <activity android:name=".FullscreenActivity" android:exported="true">

                            <intent-filter android:autoVerify="true">
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />

                                <data android:scheme="http" />
                                <data android:host="example.com" />
                                <data android:path="/gizmos?queryParam#fragment" />
                                <data android:path="/gizmos#fragment?queryParam" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """,
          )
          .indented(),
      )
      .run()
      .expect(
        """
        src/main/AndroidManifest.xml:15: Error: App link matching does not support query parameters or fragments, unless using <uri-relative-filter-group> (introduced in Android 15) [AppLinkUrlError]
                        <data android:path="/gizmos?queryParam#fragment" />
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/main/AndroidManifest.xml:16: Error: App link matching does not support query parameters or fragments, unless using <uri-relative-filter-group> (introduced in Android 15) [AppLinkUrlError]
                        <data android:path="/gizmos#fragment?queryParam" />
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        2 errors, 0 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for src/main/AndroidManifest.xml line 15: Replace with <uri-relative-filter-group>...:
        @@ -15 +15
        -                 <data android:path="/gizmos?queryParam#fragment" />
        +                 <uri-relative-filter-group>
        +                     <data android:path="/gizmos" />
        +                     <data android:query="queryParam" />
        +                     <data android:fragment="fragment" />
        +                 </uri-relative-filter-group>
        Fix for src/main/AndroidManifest.xml line 16: Replace with <uri-relative-filter-group>...:
        @@ -16 +16
        -                 <data android:path="/gizmos#fragment?queryParam" />
        +                 <uri-relative-filter-group>
        +                     <data android:path="/gizmos" />
        +                     <data android:query="queryParam" />
        +                     <data android:fragment="fragment" />
        +                 </uri-relative-filter-group>
        """
      )
  }

  fun test_queryParamAndFragment_customAndroidNs() {
    lint()
      .files(
        gradle(
          """
          apply plugin: 'com.android.application'

          android {
              compileSdkVersion 35

              defaultConfig {
                  minSdkVersion 30
                  targetSdkVersion 35
              }
          }"""
        ),
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android-ns="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >
                    <uses-sdk android-ns:minSdkVersion="31" android-ns:targetSdkVersion="35" />

                    <application>
                        <activity android-ns:name=".FullscreenActivity" android-ns:exported="true">

                            <intent-filter android-ns:autoVerify="true">
                                <action android-ns:name="android.intent.action.VIEW" />
                                <category android-ns:name="android.intent.category.DEFAULT" />
                                <category android-ns:name="android.intent.category.BROWSABLE" />

                                <data android-ns:scheme="http" />
                                <data android-ns:host="example.com" />
                                <data android-ns:path="/gizmos?queryParam#fragment" />
                                <data android-ns:path="/gizmos#fragment?queryParam" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """,
          )
          .indented(),
      )
      .run()
      .expect(
        """
        src/main/AndroidManifest.xml:15: Error: App link matching does not support query parameters or fragments, unless using <uri-relative-filter-group> (introduced in Android 15) [AppLinkUrlError]
                        <data android-ns:path="/gizmos?queryParam#fragment" />
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/main/AndroidManifest.xml:16: Error: App link matching does not support query parameters or fragments, unless using <uri-relative-filter-group> (introduced in Android 15) [AppLinkUrlError]
                        <data android-ns:path="/gizmos#fragment?queryParam" />
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        2 errors, 0 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for src/main/AndroidManifest.xml line 15: Replace with <uri-relative-filter-group>...:
        @@ -15 +15
        -                 <data android-ns:path="/gizmos?queryParam#fragment" />
        +                 <uri-relative-filter-group>
        +                     <data android-ns:path="/gizmos" />
        +                     <data android-ns:query="queryParam" />
        +                     <data android-ns:fragment="fragment" />
        +                 </uri-relative-filter-group>
        Fix for src/main/AndroidManifest.xml line 16: Replace with <uri-relative-filter-group>...:
        @@ -16 +16
        -                 <data android-ns:path="/gizmos#fragment?queryParam" />
        +                 <uri-relative-filter-group>
        +                     <data android-ns:path="/gizmos" />
        +                     <data android-ns:query="queryParam" />
        +                     <data android-ns:fragment="fragment" />
        +                 </uri-relative-filter-group>
        """
      )
  }

  fun test_queryParameter_andFragment_compileSdkVersionBelowAndroidV() {
    lint()
      .files(
        gradle(
          """
          apply plugin: 'com.android.application'

          android {
              compileSdkVersion 34

              defaultConfig {
                  minSdkVersion 30
                  targetSdkVersion 35
              }
          }
        """
        ),
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application>
                        <activity android:name=".FullscreenActivity" android:exported="true">

                            <intent-filter android:autoVerify="true">
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />

                                <data android:scheme="http" />
                                <data android:host="example.com" />
                                <data android:path="/gizmos?queryParam" />
                                <data android:path="/gizmos#fragment" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """,
          )
          .indented(),
      )
      .run()
      .expect(
        """
        src/main/AndroidManifest.xml:14: Error: App link matching does not support query parameters or fragments, unless using <uri-relative-filter-group> (introduced in Android 15) [AppLinkUrlError]
                        <data android:path="/gizmos?queryParam" />
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/main/AndroidManifest.xml:15: Error: App link matching does not support query parameters or fragments, unless using <uri-relative-filter-group> (introduced in Android 15) [AppLinkUrlError]
                        <data android:path="/gizmos#fragment" />
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        2 errors, 0 warnings
        """
      )
      .expectFixDiffs("")
  }

  fun test_queryParameter_andFragment_targetSdkVersionBelowAndroidV() {
    lint()
      .files(
        gradle(
          """
          apply plugin: 'com.android.application'

          android {
              compileSdkVersion 35

              defaultConfig {
                  minSdkVersion 30
                  targetSdkVersion 34
              }
          }
        """
        ),
        xml(
            "AndroidManifest.xml",
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >

                    <application>
                        <activity android:name=".FullscreenActivity" android:exported="true">

                            <intent-filter android:autoVerify="true">
                                <action android:name="android.intent.action.VIEW" />
                                <category android:name="android.intent.category.DEFAULT" />
                                <category android:name="android.intent.category.BROWSABLE" />

                                <data android:scheme="http" />
                                <data android:host="example.com" />
                                <data android:path="/gizmos?queryParam" />
                                <data android:path="/gizmos#fragment" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """,
          )
          .indented(),
      )
      .run()
      .expect(
        """
        src/main/AndroidManifest.xml:14: Error: App link matching does not support query parameters or fragments, unless using <uri-relative-filter-group> (introduced in Android 15) [AppLinkUrlError]
                        <data android:path="/gizmos?queryParam" />
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        src/main/AndroidManifest.xml:15: Error: App link matching does not support query parameters or fragments, unless using <uri-relative-filter-group> (introduced in Android 15) [AppLinkUrlError]
                        <data android:path="/gizmos#fragment" />
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        2 errors, 0 warnings
        """
      )
      .expectFixDiffs("")
  }

  fun test_queryParameter_andFragment_insideUriRelativeFilterGroup() {
    // TODO(b/370997994): Allow <data> tags inside <uri-relative-filter-group> to be visited, then
    // enable this test.
    //    lint()
    //      .files(
    //        xml(
    //          "AndroidManifest.xml",
    //          """
    //                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
    //                    package="com.example.helloworld" >
    //                    <uses-sdk android:minSdkVersion="31" android:targetSdkVersion="35" />
    //
    //                    <application>
    //                        <activity android:name=".FullscreenActivity" android:exported="true">
    //
    //                            <intent-filter android:autoVerify="true">
    //                                <action android:name="android.intent.action.VIEW" />
    //                                <category android:name="android.intent.category.DEFAULT" />
    //                                <category android:name="android.intent.category.BROWSABLE" />
    //
    //                                <data android:scheme="http" />
    //                                <data android:host="example.com" />
    //                                <uri-relative-filter-group>
    //                                    <data android:path="/gizmos?queryParam" />
    //                                    <data android:path="/gizmos#fragment" />
    //                                </uri-relative-filter-group>
    //                            </intent-filter>
    //                        </activity>
    //                    </application>
    //                </manifest>
    //                """,
    //        )
    //          .indented()
    //      )
    //      .run()
    //      .expect("")
    //      .expectFixDiffs("")
  }

  // TODO(b/375352603): Re-enable this test.
  /*fun test_splitToWebAndCustomSchemes() {
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >
                    <uses-sdk android:minSdkVersion="31" android:targetSdkVersion="34" />

                <application>
                    <activity android:name=".SplitWebAndCustomActivity" android:exported="true">
                        <intent-filter android:autoVerify="true" android:order="-1" android:priority="-1">
                            <action android:name="android.intent.action.VIEW" />
                            <category android:name="android.intent.category.DEFAULT" />
                            <category android:name="android.intent.category.BROWSABLE" />
                            <uri-relative-filter-group>
                                <data android:path="/path" />
                                <data android:query="queryparam=value" />
                            </uri-relative-filter-group>
                            <data android:scheme="http" />
                            <data android:scheme="custom" />
                            <data android:host="library.com" />
                            <data android:path="@string/path" />
                            <data android:path="/&lt;&amp;&apos;'" />
                            <data android:path='/single"quote' />
                            <data android:path="" />
                            <!-- Test having tags underneath the host elements as well -->
                            <action android:name="android.intent.action.SEND"/>
                            <uri-relative-filter-group>
                                <data android:path="/path" />
                                <data android:query="queryparam=value" />
                            </uri-relative-filter-group>
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
          """,
          )
          .indented(),
        xml(
            "res/values/strings.xml",
            """
                <resources>
                    <string name="path">/path</string>
                </resources>
                """,
          )
          .indented(),
      )
      .issues(APP_LINK_SPLIT_TO_WEB_AND_CUSTOM)
      .run()
      .expect(
        """
        AndroidManifest.xml:7: Error: Split your http(s) and custom schemes into separate intent filters [AppLinkSplitToWebAndCustom]
                    <intent-filter android:autoVerify="true" android:order="-1" android:priority="-1">
                    ^
        1 errors, 0 warnings
      """
      )
      .expectFixDiffs(
        """
      Fix for AndroidManifest.xml line 7: Replace with <intent-filter android:autoVerify="true" android:order="-1" android:priority="-1">...:
      @@ -15 +15
      +                 <action android:name="android.intent.action.SEND" />
      +                 <uri-relative-filter-group>
      +                     <data android:path="/path" />
      +                     <data android:query="queryparam=value" />
      +                 </uri-relative-filter-group>
      @@ -16 +21
      -                 <data android:scheme="custom" />
      @@ -18 +22
      -                 <data android:path="@string/path" />
      @@ -20 +23
      -                 <data android:path='/single"quote' />
      -                 <data android:path="" />
      -                 <!-- Test having tags underneath the host elements as well -->
      -                 <action android:name="android.intent.action.SEND"/>
      +                 <data android:path="/single"quote" />
      +                 <data android:path="@string/path" />
      +             </intent-filter>
      +             <intent-filter android:order="-1" android:priority="-1">
      +                 <action android:name="android.intent.action.VIEW" />
      +                 <category android:name="android.intent.category.DEFAULT" />
      +                 <category android:name="android.intent.category.BROWSABLE" />
      @@ -28 +34
      +                 <action android:name="android.intent.action.SEND" />
      +                 <uri-relative-filter-group>
      +                     <data android:path="/path" />
      +                     <data android:query="queryparam=value" />
      +                 </uri-relative-filter-group>
      +                 <data android:scheme="custom" />
      +                 <data android:host="library.com" />
      +                 <data android:path="/&lt;&amp;&apos;'" />
      +                 <data android:path="/single"quote" />
      +                 <data android:path="@string/path" />
      """
      )
  }

  fun test_splitToWebAndCustomSchemes_customAndroidNs() {
    lint()
      .files(
        xml(
            "AndroidManifest.xml",
            """
            <manifest xmlns:android-ns="http://schemas.android.com/apk/res/android"
                    package="com.example.helloworld" >
                    <uses-sdk android-ns:minSdkVersion="31" android-ns:targetSdkVersion="34" />

                <application>
                    <activity android-ns:name=".SplitWebAndCustomActivity" android-ns:exported="true">
                        <intent-filter android-ns:autoVerify="true" android-ns:order="-1" android-ns:priority="-1">
                            <action android-ns:name="android.intent.action.VIEW" />
                            <category android-ns:name="android.intent.category.DEFAULT" />
                            <category android-ns:name="android.intent.category.BROWSABLE" />
                            <data android-ns:scheme="http" />
                            <data android-ns:scheme="custom" />
                            <data android-ns:host="library.com" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
          """,
          )
          .indented()
      )
      .run()
      .expect(
        """
        AndroidManifest.xml:7: Error: Split your http(s) and custom schemes into separate intent filters [AppLinkSplitToWebAndCustom]
                    <intent-filter android-ns:autoVerify="true" android-ns:order="-1" android-ns:priority="-1">
                    ^
        1 errors, 0 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for AndroidManifest.xml line 7: Replace with <intent-filter android-ns:autoVerify="true" android-ns:order="-1" android-ns:priority="-1">...:
        @@ -12 +12
        +                 <data android-ns:host="library.com" />
        +             </intent-filter>
        +             <intent-filter android-ns:order="-1" android-ns:priority="-1">
        +                 <action android-ns:name="android.intent.action.VIEW" />
        +                 <category android-ns:name="android.intent.category.DEFAULT" />
        +                 <category android-ns:name="android.intent.category.BROWSABLE" />
        """
      )
  }*/
}
