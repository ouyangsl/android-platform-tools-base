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

package com.android.manifmerger

import com.android.ide.common.blame.SourceFile.UNKNOWN
import com.android.ide.common.blame.SourceFilePosition
import com.android.ide.common.blame.SourcePosition
import com.android.manifmerger.NavGraphExpander.expandNavGraphs
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

/** Tests for [NavGraphExpander]  */
class NavGraphExpanderTest {

    private val model = ManifestModel()
    private val fakeSourceFilePosition = SourceFilePosition(UNKNOWN, SourcePosition(0, 0, 0))
    private val actionRecorder: ActionRecorder = mock()
    private val mergingReportBuilder: MergingReport.Builder = mock {
        on { actionRecorder } doReturn actionRecorder
    }

    @Test
    fun testExpandNavGraphs() {

        val navigationId1 = "nav1"
        val navigationString1 =
                """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<navigation
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:app="http://schemas.android.com/apk/res-auto">
                    |    <include app:graph="@navigation/nav2" />
                    |    <deepLink app:uri="www.example.com"
                    |            android:autoVerify="true" />
                    |    <navigation>
                    |        <deepLink app:uri="http://www.example.com:120/foo/{placeholder}" />
                    |    </navigation>
                    |</navigation>""".trimMargin()

        val navigationId2 = "nav2"
        val navigationString2 =
                """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<navigation
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:app="http://schemas.android.com/apk/res-auto">
                    |    <deepLink app:uri="https://.*.example.com/.*/{placeholder}"
                    |              app:action="" />
                    |</navigation>""".trimMargin()

        val inputManifestString =
                """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<manifest
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    package="com.example.app1">
                    |    <application android:name="TheApp">
                    |        <activity android:name=".MainActivity">
                    |            <nav-graph android:value="@navigation/nav1" />
                    |        </activity>
                    |    </application>
                    |</manifest>""".trimMargin()

        val expectedOutputManifestString =
                """ |<?xml version="1.0" encoding="utf-8"?>
                    |<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    |    package="com.example.app1" >
                    |    <application android:name="com.example.app1.TheApp" >
                    |        <activity android:name="com.example.app1.MainActivity" >
                    |            <intent-filter android:autoVerify="true" >
                    |                <action android:name="android.intent.action.VIEW" />
                    |                <category android:name="android.intent.category.DEFAULT" />
                    |                <category android:name="android.intent.category.BROWSABLE" />
                    |                <data android:scheme="http" />
                    |                <data android:scheme="https" />
                    |                <data android:host="www.example.com" />
                    |                <data android:path="/" />
                    |            </intent-filter>
                    |            <intent-filter>
                    |                <action android:name="android.intent.action.VIEW" />
                    |                <category android:name="android.intent.category.DEFAULT" />
                    |                <category android:name="android.intent.category.BROWSABLE" />
                    |                <data android:scheme="http" />
                    |                <data android:host="www.example.com" />
                    |                <data android:port="120" />
                    |                <data android:pathPrefix="/foo/" />
                    |            </intent-filter>
                    |            <intent-filter>
                    |                <category android:name="android.intent.category.DEFAULT" />
                    |                <category android:name="android.intent.category.BROWSABLE" />
                    |                <data android:scheme="https" />
                    |                <data android:host="*.example.com" />
                    |                <data android:pathPattern="/.*/.*" />
                    |            </intent-filter>
                    |        </activity>
                    |    </application>
                    |</manifest>""".trimMargin()

        val xmlDocument = TestUtils.xmlDocumentFromString(UNKNOWN, inputManifestString, model)

        val loadedNavigationMap: Map<String, NavigationXmlDocument> =
                mapOf(
                        Pair(navigationId1, NavigationXmlLoader.load(UNKNOWN, navigationString1)),
                        Pair(navigationId2, NavigationXmlLoader.load(UNKNOWN, navigationString2)))

        val expandedXmlDocument =
                expandNavGraphs(xmlDocument, loadedNavigationMap, mergingReportBuilder)

        val expectedXmlDocument =
                TestUtils.xmlDocumentFromString(UNKNOWN, expectedOutputManifestString, model)

        assertThat(expandedXmlDocument.prettyPrint()).isEqualTo(expectedXmlDocument.prettyPrint())

        // test that actions are recorded with final NodeKeys.
        // first make lists of the intent-filters' final NodeKeys (including descendant nodes).
        val elementNodeKeys = mutableListOf<XmlNode.NodeKey>()
        val attributeNodeKeys = mutableListOf<XmlNode.NodeKey>()
        val intentFilterXmlElements =
                expandedXmlDocument
                        .rootNode
                        .getAllNodesByType(ManifestModel.NodeTypes.APPLICATION)
                        .flatMap { applicationXmlElement ->
                            applicationXmlElement
                                    .getAllNodesByType(ManifestModel.NodeTypes.ACTIVITY)
                        }
                        .flatMap { activityXmlElement ->
                            activityXmlElement
                                    .getAllNodesByType(ManifestModel.NodeTypes.INTENT_FILTER)
                        }
        for (intentFilterXmlElement in intentFilterXmlElements) {
            elementNodeKeys.add(intentFilterXmlElement.id)
            for (intentFilterXmlAttribute in intentFilterXmlElement.attributes) {
                attributeNodeKeys.add(intentFilterXmlAttribute.id)
            }
            for (childXmlElement in intentFilterXmlElement.mergeableElements) {
                elementNodeKeys.add(childXmlElement.id)
                for (childXmlAttribute in childXmlElement.attributes) {
                    attributeNodeKeys.add(childXmlAttribute.id)
                }
            }
        }
        // then check that the recorded NodeKeys match the final NodeKeys.
        val nodeRecordCaptor = argumentCaptor<Actions.NodeRecord>()
        val attributeRecordCaptor = argumentCaptor<Actions.AttributeRecord>()
        verify(actionRecorder, atLeast(1)).recordNodeAction(any(), nodeRecordCaptor.capture())
        verify(actionRecorder, atLeast(1))
                .recordAttributeAction(any(), attributeRecordCaptor.capture())
        assertThat(elementNodeKeys)
                .containsExactlyElementsIn(nodeRecordCaptor.allValues.map {it.targetId})
        assertThat(attributeNodeKeys)
                .containsExactlyElementsIn(attributeRecordCaptor.allValues.map {it.targetId})
    }

    @Test
    fun testCircularReferenceNavGraphException() {

        val navigationId1 = "nav1"
        val navigationString1 =
                """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<navigation
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:app="http://schemas.android.com/apk/res-auto">
                    |    <include app:graph="@navigation/nav2" />
                    |</navigation>""".trimMargin()

        val navigationId2 = "nav2"
        val navigationString2 =
                """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<navigation
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:app="http://schemas.android.com/apk/res-auto">
                    |    <include app:graph="@navigation/nav1" />
                    |    <deepLink app:uri="www.example.com" />
                    |</navigation>""".trimMargin()

        val inputManifestString =
                """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<manifest
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    package="com.example.app1">
                    |    <application android:name="TheApp">
                    |        <activity android:name=".MainActivity">
                    |            <nav-graph android:value="@navigation/nav1" />
                    |        </activity>
                    |    </application>
                    |</manifest>""".trimMargin()

        val xmlDocument = TestUtils.xmlDocumentFromString(UNKNOWN, inputManifestString, model)

        val loadedNavigationMap: Map<String, NavigationXmlDocument> =
                mapOf(
                        Pair(navigationId1, NavigationXmlLoader.load(UNKNOWN, navigationString1)),
                        Pair(navigationId2, NavigationXmlLoader.load(UNKNOWN, navigationString2)))

        expandNavGraphs(xmlDocument, loadedNavigationMap, mergingReportBuilder)

        // verify the error was recorded.
        verify(mergingReportBuilder).addMessage(
            any<SourceFilePosition>(),
            eq(MergingReport.Record.Severity.ERROR),
            eq(
                    "Illegal circular reference among navigation files when traversing " +
                            "navigation file references: nav1 > nav2 > nav1."
                )
        )
    }

    @Test
    fun testDuplicateNavigationFileWarning() {

        val navigationId1 = "nav1"
        val navigationString1 =
            """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<navigation
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:app="http://schemas.android.com/apk/res-auto">
                    |    <include app:graph="@navigation/nav2" />
                    |    <include app:graph="@navigation/nav3" />
                    |</navigation>""".trimMargin()

        val navigationId2 = "nav2"
        val navigationString2 =
            """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<navigation
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:app="http://schemas.android.com/apk/res-auto">
                    |    <include app:graph="@navigation/nav3" />
                    |    <deepLink app:uri="www.example.com" />
                    |</navigation>""".trimMargin()

        val navigationId3 = "nav3"
        val navigationString3 =
            """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<navigation
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:app="http://schemas.android.com/apk/res-auto">
                    |    <deepLink app:uri="www.example.com/foo" />
                    |</navigation>""".trimMargin()

        val inputManifestString =
            """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<manifest
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    package="com.example.app1">
                    |    <application android:name="TheApp">
                    |        <activity android:name=".MainActivity">
                    |            <nav-graph android:value="@navigation/nav1" />
                    |        </activity>
                    |    </application>
                    |</manifest>""".trimMargin()

        val xmlDocument = TestUtils.xmlDocumentFromString(UNKNOWN, inputManifestString, model)

        val loadedNavigationMap: Map<String, NavigationXmlDocument> =
            mapOf(
                Pair(navigationId1, NavigationXmlLoader.load(UNKNOWN, navigationString1)),
                Pair(navigationId2, NavigationXmlLoader.load(UNKNOWN, navigationString2)),
                Pair(navigationId3, NavigationXmlLoader.load(UNKNOWN, navigationString3))
            )

        expandNavGraphs(xmlDocument, loadedNavigationMap, mergingReportBuilder)

        // verify the warning was recorded.
        verify(mergingReportBuilder).addMessage(
            any<SourceFilePosition>(),
            eq(MergingReport.Record.Severity.WARNING),
            eq(
                "The navigation file with ID \"nav3\" is included multiple times in " +
                        "the navigation graph, but only deep links on the first instance will be " +
                        "triggered at runtime. Consider consolidating these instances into a " +
                        "single <include> at a higher level of your navigation graph hierarchy."
            )
        )
    }

    @Test
    fun testFindDeepLinks() {

        val navigationId1 = "nav1"
        val navigationString1 =
                """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<navigation
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:app="http://schemas.android.com/apk/res-auto">
                    |    <include app:graph="@navigation/nav2" />
                    |    <deepLink app:uri="http://www.example.com?param=foo#fragment" />
                    |</navigation>""".trimMargin()

        val navigationId2 = "nav2"
        val navigationString2 =
                """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<navigation
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:app="http://schemas.android.com/apk/res-auto">
                    |    <deepLink app:uri="www.example.com/foo/?param={bar}" />
                    |</navigation>""".trimMargin()

        val loadedNavigationMap: Map<String, NavigationXmlDocument> =
            mapOf(
                Pair(navigationId1, NavigationXmlLoader.load(UNKNOWN, navigationString1)),
                Pair(navigationId2, NavigationXmlLoader.load(UNKNOWN, navigationString2)))

        val deepLinks =
            NavGraphExpander.findDeepLinks(
                navigationId1,
                loadedNavigationMap,
                mergingReportBuilder,
                fakeSourceFilePosition
            )
        assertThat(deepLinks).containsExactly(
                DeepLink(
                    ImmutableList.of("http"),
                    "www.example.com",
                    -1,
                    "/",
                    "param=foo",
                    "fragment",
                    SourceFilePosition(UNKNOWN, SourcePosition(5, 4, 220, 5, 68, 284)),
                    false),
                DeepLink(
                    ImmutableList.of("http", "https"),
                    "www.example.com",
                    -1,
                    "/foo/",
                    "param=.*",
                    null,
                    SourceFilePosition(UNKNOWN, SourcePosition(4, 4, 175, 4, 59, 230)),
                    false))
    }

    @Test
    fun testDuplicateDeepLinkNavGraphException() {

        val navigationId1 = "nav1"
        val navigationString1 =
                """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<navigation
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:app="http://schemas.android.com/apk/res-auto">
                    |    <include app:graph="@navigation/nav2" />
                    |    <deepLink app:uri="http://www.example.com"
                    |        app:action="android.intent.action.APP_ACTION"
                    |        app:mimeType="app/image/jpg" />
                    |</navigation>""".trimMargin()

        val navigationId2 = "nav2"
        val navigationString2 =
                """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<navigation
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:app="http://schemas.android.com/apk/res-auto">
                    |    <deepLink app:uri="http://www.example.com"
                    |        app:action="android.intent.action.APP_ACTION"
                    |        app:mimeType="app/image/jpg" />
                    |</navigation>""".trimMargin()

        val inputManifestString =
                """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<manifest
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    package="com.example.app1">
                    |    <application android:name="TheApp">
                    |        <activity android:name=".MainActivity">
                    |            <nav-graph android:value="@navigation/nav1" />
                    |        </activity>
                    |    </application>
                    |</manifest>""".trimMargin()

        val xmlDocument = TestUtils.xmlDocumentFromString(UNKNOWN, inputManifestString, model)

        val loadedNavigationMap: Map<String, NavigationXmlDocument> =
                mapOf(
                        Pair(navigationId1, NavigationXmlLoader.load(UNKNOWN, navigationString1)),
                        Pair(navigationId2, NavigationXmlLoader.load(UNKNOWN, navigationString2)))

        expandNavGraphs(xmlDocument, loadedNavigationMap, mergingReportBuilder)

        // verify the error was recorded.
        verify(mergingReportBuilder).addMessage(
            any<SourceFilePosition>(),
            eq(MergingReport.Record.Severity.ERROR),
            eq(
                        "Multiple destinations found with a deep link containing " +
                                "uri:http://www.example.com/, " +
                                "action:android.intent.action.APP_ACTION, " +
                                "mimeType:app/image/jpg."))
    }

    @Test
    fun testDeepLinksDifferInQueryGoToOneIntentFilter() {
        val navigationString1 =
            """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<navigation
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:app="http://schemas.android.com/apk/res-auto">
                    |    <include app:graph="@navigation/nav2" />
                    |    <deepLink app:uri="http://www.example.com?foo=bar" />
                    |</navigation>""".trimMargin()

        val navigationString2 =
            """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<navigation
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:app="http://schemas.android.com/apk/res-auto">
                    |    <deepLink app:uri="http://www.example.com" />
                    |</navigation>""".trimMargin()

        val expectedOutputManifestString =
            """ |<?xml version="1.0" encoding="utf-8"?>
                    |<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    |    package="com.example.app1" >
                    |    <application android:name="com.example.app1.TheApp" >
                    |        <activity android:name="com.example.app1.MainActivity" >
                    |            <intent-filter>
                    |                <action android:name="android.intent.action.VIEW" />
                    |                <category android:name="android.intent.category.DEFAULT" />
                    |                <category android:name="android.intent.category.BROWSABLE" />
                    |                <data android:scheme="http" />
                    |                <data android:host="www.example.com" />
                    |                <data android:path="/" />
                    |            </intent-filter>
                    |        </activity>
                    |    </application>
                    |</manifest>""".trimMargin()

        validateDeepLinksGoToOneIntentFilter(
            expectedOutputManifestString,
            navigationString1,
            navigationString2)
    }

    @Test
    fun testDuplicateDeepLinkWithQueryNavGraphException() {

        val navigationId1 = "nav1"
        val navigationString1 =
            """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<navigation
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:app="http://schemas.android.com/apk/res-auto">
                    |    <include app:graph="@navigation/nav2" />
                    |    <deepLink app:uri="http://www.example.com?foo={bar}" />
                    |</navigation>""".trimMargin()

        val navigationId2 = "nav2"
        val navigationString2 =
            """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<navigation
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:app="http://schemas.android.com/apk/res-auto">
                    |    <deepLink app:uri="www.example.com?foo={qwe}" />
                    |</navigation>""".trimMargin()

        val inputManifestString =
            """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<manifest
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    package="com.example.app1">
                    |    <application android:name="TheApp">
                    |        <activity android:name=".MainActivity">
                    |            <nav-graph android:value="@navigation/nav1" />
                    |        </activity>
                    |    </application>
                    |</manifest>""".trimMargin()

        val xmlDocument = TestUtils.xmlDocumentFromString(UNKNOWN, inputManifestString, model)

        val loadedNavigationMap: Map<String, NavigationXmlDocument> =
            mapOf(
                Pair(navigationId1, NavigationXmlLoader.load(UNKNOWN, navigationString1)),
                Pair(navigationId2, NavigationXmlLoader.load(UNKNOWN, navigationString2)))

        expandNavGraphs(xmlDocument, loadedNavigationMap, mergingReportBuilder)

        // verify the error was recorded.
        verify(mergingReportBuilder).addMessage(
            any<SourceFilePosition>(),
            eq(MergingReport.Record.Severity.ERROR),
            eq(
                "Multiple destinations found with a deep link containing " +
                        "uri:http://www.example.com/?foo=.*, action:android.intent.action.VIEW."))
    }

    @Test
    fun testDeepLinksDifferInFragmentGoToOneIntentFilter() {
        val navigationString1 =
            """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<navigation
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:app="http://schemas.android.com/apk/res-auto">
                    |    <include app:graph="@navigation/nav2" />
                    |    <deepLink app:uri="http://www.example.com#fragment" />
                    |</navigation>""".trimMargin()

        val navigationString2 =
            """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<navigation
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:app="http://schemas.android.com/apk/res-auto">
                    |    <deepLink app:uri="http://www.example.com" />
                    |</navigation>""".trimMargin()

        val expectedOutputManifestString =
            """ |<?xml version="1.0" encoding="utf-8"?>
                    |<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    |    package="com.example.app1" >
                    |    <application android:name="com.example.app1.TheApp" >
                    |        <activity android:name="com.example.app1.MainActivity" >
                    |            <intent-filter>
                    |                <action android:name="android.intent.action.VIEW" />
                    |                <category android:name="android.intent.category.DEFAULT" />
                    |                <category android:name="android.intent.category.BROWSABLE" />
                    |                <data android:scheme="http" />
                    |                <data android:host="www.example.com" />
                    |                <data android:path="/" />
                    |            </intent-filter>
                    |        </activity>
                    |    </application>
                    |</manifest>""".trimMargin()

        validateDeepLinksGoToOneIntentFilter(
            expectedOutputManifestString,
            navigationString1,
            navigationString2)
    }

    @Test
    fun testLinksDifferOnlyInSchemeGoToOneIntentFilter() {
        val navigationString1 =
            """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<navigation
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:app="http://schemas.android.com/apk/res-auto">
                    |    <include app:graph="@navigation/nav2" />
                    |    <deepLink app:uri="http://www.example.com" />
                    |</navigation>""".trimMargin()

        val navigationString2 =
            """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<navigation
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:app="http://schemas.android.com/apk/res-auto">
                    |    <deepLink app:uri="https://www.example.com" />
                    |</navigation>""".trimMargin()

        val expectedOutputManifestString =
            """ |<?xml version="1.0" encoding="utf-8"?>
                    |<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    |    package="com.example.app1" >
                    |    <application android:name="com.example.app1.TheApp" >
                    |        <activity android:name="com.example.app1.MainActivity" >
                    |            <intent-filter>
                    |                <action android:name="android.intent.action.VIEW" />
                    |                <category android:name="android.intent.category.DEFAULT" />
                    |                <category android:name="android.intent.category.BROWSABLE" />
                    |                <data android:scheme="http" />
                    |                <data android:scheme="https" />
                    |                <data android:host="www.example.com" />
                    |                <data android:path="/" />
                    |            </intent-filter>
                    |        </activity>
                    |    </application>
                    |</manifest>""".trimMargin()

        validateDeepLinksGoToOneIntentFilter(
            expectedOutputManifestString,
            navigationString1,
            navigationString2)
    }

    @Test
    fun testLinksDifferInSchemeAndQueryGoToOneIntentFilter() {
        val navigationString1 =
            """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<navigation
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:app="http://schemas.android.com/apk/res-auto">
                    |    <include app:graph="@navigation/nav2" />
                    |    <deepLink app:uri="http://www.example.com" />
                    |</navigation>""".trimMargin()

        val navigationString2 =
            """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<navigation
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:app="http://schemas.android.com/apk/res-auto">
                    |    <deepLink app:uri="https://www.example.com" />
                    |    <include app:graph="@navigation/nav3" />
                    |</navigation>""".trimMargin()

        val navigationString3 =
            """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<navigation
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    xmlns:app="http://schemas.android.com/apk/res-auto">
                    |    <deepLink app:uri="www.example.com?foo=bar" />
                    |</navigation>""".trimMargin()

        val expectedOutputManifestString =
            """ |<?xml version="1.0" encoding="utf-8"?>
                    |<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    |    package="com.example.app1" >
                    |    <application android:name="com.example.app1.TheApp" >
                    |        <activity android:name="com.example.app1.MainActivity" >
                    |            <intent-filter>
                    |                <action android:name="android.intent.action.VIEW" />
                    |                <category android:name="android.intent.category.DEFAULT" />
                    |                <category android:name="android.intent.category.BROWSABLE" />
                    |                <data android:scheme="http" />
                    |                <data android:scheme="https" />
                    |                <data android:host="www.example.com" />
                    |                <data android:path="/" />
                    |            </intent-filter>
                    |        </activity>
                    |    </application>
                    |</manifest>""".trimMargin()

        validateDeepLinksGoToOneIntentFilter(
            expectedOutputManifestString,
            navigationString1,
            navigationString2,
            navigationString3)
    }

    private fun validateDeepLinksGoToOneIntentFilter(
        expectedOutputManifestString: String,
        vararg navigationStrings: String
    ) {
        val inputManifestString =
            """"|<?xml version="1.0" encoding="UTF-8"?>
                    |<manifest
                    |    xmlns:android="http://schemas.android.com/apk/res/android"
                    |    package="com.example.app1">
                    |    <application android:name="TheApp">
                    |        <activity android:name=".MainActivity">
                    |            <nav-graph android:value="@navigation/nav1" />
                    |        </activity>
                    |    </application>
                    |</manifest>""".trimMargin()

        val xmlDocument = TestUtils.xmlDocumentFromString(UNKNOWN, inputManifestString, model)

        val loadedNavigationMap: MutableMap<String, NavigationXmlDocument> = mutableMapOf()

        navigationStrings.forEachIndexed { index, navigationString ->
            loadedNavigationMap["nav${index + 1}"] = NavigationXmlLoader.load(UNKNOWN, navigationString)
        }

        val expandedXmlDocument =
            expandNavGraphs(xmlDocument, loadedNavigationMap, mergingReportBuilder)

        val expectedXmlDocument =
            TestUtils.xmlDocumentFromString(UNKNOWN, expectedOutputManifestString, model)

        assertThat(expandedXmlDocument.prettyPrint()).isEqualTo(expectedXmlDocument.prettyPrint())

        // verify no error was recorded.
        verify(mergingReportBuilder, never()).addMessage(
            any<SourceFilePosition>(),
            eq(MergingReport.Record.Severity.ERROR),
            any())
    }
}
