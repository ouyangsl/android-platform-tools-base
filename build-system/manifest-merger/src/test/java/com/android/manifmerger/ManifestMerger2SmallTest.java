/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.manifmerger;

import static com.android.SdkConstants.ATTR_ON_DEMAND;
import static com.android.SdkConstants.DIST_URI;
import static com.android.SdkConstants.MANIFEST_ATTR_TITLE;
import static com.android.SdkConstants.TAG_MODULE;
import static com.android.manifmerger.ManifestMerger2.Invoker.Feature;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.TestCase.assertFalse;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.AndroidXConstants;
import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.ide.common.xml.XmlFormatPreferences;
import com.android.ide.common.xml.XmlFormatStyle;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.manifmerger.MergingReport.MergedManifestKind;
import com.android.testutils.MockLog;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.truth.Truth;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;

/** Tests for the {@link ManifestMergerTestUtil} class */
public class ManifestMerger2SmallTest {
    private final ManifestModel mModel = new ManifestModel();

    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private ActionRecorder mActionRecorder;

    @Test
    public void testValidationFailure() throws Exception {

        MockLog mockLog = new MockLog();
        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "        <activity android:name=\"activityOne\" "
                + "             tools:replace=\"exported\"/>\n"
                + "\n"
                + "</manifest>";

        File tmpFile = TestUtils.inputAsFile("ManifestMerger2Test_testValidationFailure", input);
        assertTrue(tmpFile.exists());

        try {
            MergingReport mergingReport = ManifestMerger2.newMerger(tmpFile, mockLog,
                    ManifestMerger2.MergeType.APPLICATION).merge();
            assertEquals(MergingReport.Result.ERROR, mergingReport.getResult());
            // check the log complains about the incorrect "tools:replace"
            assertStringPresenceInLogRecords(mergingReport, "tools:replace");
            assertNull(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        } finally {
            assertTrue(tmpFile.delete());
        }
    }

    @Test
    public void testToolsAnnotationRemoval() throws Exception {

        MockLog mockLog = new MockLog();
        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" "
                + "         tools:replace=\"label\"/>\n"
                + "\n"
                + "</manifest>";

        File tmpFile = TestUtils.inputAsFile("testToolsAnnotationRemoval", input);
        assertTrue(tmpFile.exists());

        try {
            MergingReport mergingReport = ManifestMerger2.newMerger(tmpFile, mockLog,
                    ManifestMerger2.MergeType.APPLICATION)
                    .withFeatures(Feature.REMOVE_TOOLS_DECLARATIONS)
                    .merge();
            assertEquals(MergingReport.Result.WARNING, mergingReport.getResult());
            // ensure tools annotation removal.
            Document xmlDocument =
                    parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            NodeList applications = xmlDocument.getElementsByTagName(SdkConstants.TAG_APPLICATION);
            assertTrue(applications.getLength() == 1);
            Node replace = applications.item(0).getAttributes()
                    .getNamedItemNS(SdkConstants.TOOLS_URI, "replace");
            assertNull(replace);
        } finally {
            assertTrue(tmpFile.delete());
        }
    }

    @Test
    public void testToolsAnnotationRemovalForLibraries() throws Exception {
        MockLog mockLog = new MockLog();
        String overlay = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.app1\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\">\n"
                + "       <activity tools:node=\"removeAll\">\n"
                + "        </activity>\n"
                + "    </application>"
                + "\n"
                + "</manifest>";

        File overlayFile = TestUtils.inputAsFile("testToolsAnnotationRemoval", overlay);
        assertTrue(overlayFile.exists());

        String libraryInput = ""
                + "<manifest\n"
                + "xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "package=\"com.example.app1\">\n"
                + "\n"
                + "<application android:name=\"TheApp\" >\n"
                + "    <!-- Activity to configure widget -->\n"
                + "    <activity\n"
                + "            android:icon=\"@drawable/widget_icon\"\n"
                + "            android:label=\"Configure Widget\"\n"
                + "            android:name=\"com.example.lib1.WidgetConfigurationUI\"\n"
                + "            android:theme=\"@style/Theme.WidgetConfigurationUI\" >\n"
                + "        <intent-filter >\n"
                + "            <action android:name=\"android.appwidget.action.APPWIDGET_CONFIGURE\" />\n"
                + "        </intent-filter>\n"
                + "    </activity>\n"
                + "</application>\n"
                + "\n"
                + "</manifest>";
        File libFile = TestUtils.inputAsFile("testToolsAnnotationRemoval", libraryInput);


        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(libFile, mockLog, ManifestMerger2.MergeType.LIBRARY)
                            .withFeatures(Feature.REMOVE_TOOLS_DECLARATIONS)
                            .addFlavorAndBuildTypeManifest(overlayFile)
                            .merge();
            assertEquals(MergingReport.Result.SUCCESS, mergingReport.getResult());
            // ensure tools annotation removal.
            Document xmlDocument =
                    parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            NodeList applications = xmlDocument.getElementsByTagName(SdkConstants.TAG_ACTIVITY);
            assertTrue(applications.getLength() == 0);
        } finally {
            assertTrue(overlayFile.delete());
            assertTrue(libFile.delete());
        }
    }

    @Test
    public void testToolsInLibrariesNotMain() throws Exception {
        // Test that BLAME merged document still created when tools: annotations
        // are used in library manifests but not in the main manifest.
        MockLog mockLog = new MockLog();
        String xml =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.app1\">\n"
                        + "\n"
                        + "    <application android:label=\"@string/lib_name\" />\n"
                        + "\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testToolsInLibrariesNotMain", xml);

        String libraryInput =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    package=\"com.example.lib1\">\n"
                        + "\n"
                        + "    <application android:label=\"@string/lib_name\">\n"
                        + "       <activity tools:node=\"removeAll\">\n"
                        + "        </activity>\n"
                        + "    </application>"
                        + "\n"
                        + "</manifest>";

        File libFile = TestUtils.inputAsFile("testToolsInLibrariesNotMain", libraryInput);

        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .withFeatures(Feature.REMOVE_TOOLS_DECLARATIONS)
                            .addLibraryManifest(libFile)
                            .merge();
            assertNotNull(mergingReport.getMergedDocument(MergedManifestKind.BLAME));
        } finally {
            assertTrue(inputFile.delete());
            assertTrue(libFile.delete());
        }
    }

    @Test
    public void testToolsAnnotationPresence() throws Exception {

        MockLog mockLog = new MockLog();
        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" "
                + "         tools:replace=\"label\"/>\n"
                + "\n"
                + "</manifest>";

        File tmpFile = TestUtils.inputAsFile("testToolsAnnotationRemoval", input);
        assertTrue(tmpFile.exists());

        try {
            MergingReport mergingReport = ManifestMerger2.newMerger(tmpFile, mockLog,
                    ManifestMerger2.MergeType.LIBRARY)
                    .merge();
            assertEquals(MergingReport.Result.WARNING, mergingReport.getResult());
            // ensure tools annotation removal.
            Document xmlDocument =
                    parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            NodeList applications = xmlDocument.getElementsByTagName(SdkConstants.TAG_APPLICATION);
            assertTrue(applications.getLength() == 1);
            Node replace = applications.item(0).getAttributes()
                    .getNamedItemNS(SdkConstants.TOOLS_URI, "replace");
            assertNotNull(replace);
            assertEquals("tools:replace value not correct", "label", replace.getNodeValue());
        } finally {
            assertTrue(tmpFile.delete());
        }
    }


    @Test
    public void testPackageOverride() throws Exception {
        String xml = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\""
                + "    package=\"com.foo.old\" >\n"
                + "    <activity android:name=\"activityOne\"/>\n"
                + "</manifest>";

        XmlDocument refDocument =
                TestUtils.xmlDocumentFromString(
                        TestUtils.sourceFile(getClass(), "testPackageOverride#xml"), xml, mModel);

        ManifestSystemProperty.Document.PACKAGE.addTo(mActionRecorder, refDocument, "com.bar.new");
        // verify the package value was overridden.
        assertEquals("com.bar.new", refDocument.getRootNode().getXml().getAttribute("package"));
    }

    @Test
    public void testMissingPackageOverride() throws Exception {
        String xml = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity android:name=\"activityOne\"/>\n"
                + "</manifest>";

        XmlDocument refDocument =
                TestUtils.xmlDocumentFromString(
                        TestUtils.sourceFile(getClass(), "testMissingPackageOverride#xml"),
                        xml,
                        mModel);

        ManifestSystemProperty.Document.PACKAGE.addTo(mActionRecorder, refDocument, "com.bar.new");
        // verify the package value was added.
        assertEquals("com.bar.new", refDocument.getRootNode().getXml().getAttribute("package"));
    }

    @Test
    public void testAddingSystemProperties() throws Exception {
        String xml = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity android:name=\"activityOne\"/>\n"
                + "</manifest>";

        XmlDocument document =
                TestUtils.xmlDocumentFromString(
                        TestUtils.sourceFile(getClass(), "testAddingSystemProperties#xml"),
                        xml,
                        mModel);

        ManifestSystemProperty.Manifest.VERSION_CODE.addTo(mActionRecorder, document, "101");
        assertEquals("101",
                document.getXml().getDocumentElement().getAttribute("android:versionCode"));

        ManifestSystemProperty.Manifest.VERSION_NAME.addTo(mActionRecorder, document, "1.0.1");
        assertEquals("1.0.1",
                document.getXml().getDocumentElement().getAttribute("android:versionName"));

        ManifestSystemProperty.UsesSdk.MIN_SDK_VERSION.addTo(mActionRecorder, document, "10");
        Element usesSdk = (Element) document.getXml().getElementsByTagName("uses-sdk").item(0);
        assertNotNull(usesSdk);
        assertEquals("10", usesSdk.getAttribute("android:minSdkVersion"));

        ManifestSystemProperty.UsesSdk.TARGET_SDK_VERSION.addTo(mActionRecorder, document, "14");
        usesSdk = (Element) document.getXml().getElementsByTagName("uses-sdk").item(0);
        assertNotNull(usesSdk);
        assertEquals("14", usesSdk.getAttribute("android:targetSdkVersion"));

        ManifestSystemProperty.UsesSdk.MAX_SDK_VERSION.addTo(mActionRecorder, document, "16");
        usesSdk = (Element) document.getXml().getElementsByTagName("uses-sdk").item(0);
        assertNotNull(usesSdk);
        assertEquals("16", usesSdk.getAttribute("android:maxSdkVersion"));
    }

    @Test
    public void testAddingSystemProperties_withDifferentPrefix() throws Exception {
        String xml = ""
                + "<manifest\n"
                + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity t:name=\"activityOne\"/>\n"
                + "</manifest>";

        XmlDocument document =
                TestUtils.xmlDocumentFromString(
                        TestUtils.sourceFile(getClass(), "testAddingSystemProperties#xml"),
                        xml,
                        mModel);

        ManifestSystemProperty.Manifest.VERSION_CODE.addTo(mActionRecorder, document, "101");
        // using the non namespace aware API to make sure the prefix is the expected one.
        assertEquals("101",
                document.getXml().getDocumentElement().getAttribute("t:versionCode"));
    }

    @Test
    public void testOverridingSystemProperties() throws Exception {
        String xml = ""
                + "<manifest versionCode=\"34\" versionName=\"3.4\"\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <uses-sdk minSdkVersion=\"9\" targetSdkVersion=\".9\"/>\n"
                + "    <activity android:name=\"activityOne\"/>\n"
                + "</manifest>";

        XmlDocument document =
                TestUtils.xmlDocumentFromString(
                        TestUtils.sourceFile(getClass(), "testAddingSystemProperties#xml"),
                        xml,
                        mModel);
        // check initial state.
        assertEquals("34", document.getXml().getDocumentElement().getAttribute("versionCode"));
        assertEquals("3.4", document.getXml().getDocumentElement().getAttribute("versionName"));
        Element usesSdk = (Element) document.getXml().getElementsByTagName("uses-sdk").item(0);
        assertNotNull(usesSdk);
        assertEquals("9", usesSdk.getAttribute("minSdkVersion"));
        assertEquals(".9", usesSdk.getAttribute("targetSdkVersion"));

        ManifestSystemProperty.Manifest.VERSION_CODE.addTo(mActionRecorder, document, "101");
        assertEquals("101",
                document.getXml().getDocumentElement().getAttribute("android:versionCode"));

        ManifestSystemProperty.Manifest.VERSION_NAME.addTo(mActionRecorder, document, "1.0.1");
        assertEquals("1.0.1",
                document.getXml().getDocumentElement().getAttribute("android:versionName"));

        ManifestSystemProperty.UsesSdk.MIN_SDK_VERSION.addTo(mActionRecorder, document, "10");
        usesSdk = (Element) document.getXml().getElementsByTagName("uses-sdk").item(0);
        assertNotNull(usesSdk);
        assertEquals("10", usesSdk.getAttribute("android:minSdkVersion"));

        ManifestSystemProperty.UsesSdk.TARGET_SDK_VERSION.addTo(mActionRecorder, document, "14");
        usesSdk = (Element) document.getXml().getElementsByTagName("uses-sdk").item(0);
        assertNotNull(usesSdk);
        assertEquals("14", usesSdk.getAttribute("android:targetSdkVersion"));
    }

    @Test
    public void testPlaceholderSubstitution() throws Exception {
        String xml =
                ""
                        + "<manifest package=\"foo.bar\" versionCode=\"34\" versionName=\"3.4\"\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <activity android:name=\".activityOne\" android:label=\"${labelName}\"/>\n"
                        + "</manifest>";

        Map<String, Object> placeholders = ImmutableMap.of("labelName", "injectedLabelName");
        MockLog mockLog = new MockLog();
        File inputFile = TestUtils.inputAsFile("testPlaceholderSubstitution", xml);
        try {
            MergingReport mergingReport = ManifestMerger2
                    .newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                    .setPlaceHolderValues(placeholders)
                    .merge();

            assertTrue(mergingReport.getResult().isSuccess());
            Document document = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            assertNotNull(document);
            Optional<Element> activityOne =
                    getElementByTypeAndKey(document, "activity", "foo.bar.activityOne");
            assertTrue(activityOne.isPresent());
            Attr label = activityOne.get().getAttributeNodeNS(SdkConstants.ANDROID_URI, "label");
            assertNotNull(label);
            assertEquals("injectedLabelName", label.getValue());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            inputFile.delete();
        }
    }

    @Test
    public void testApplicationIdSubstitution() throws Exception {
        String xml = ""
                + "<manifest package=\"foo\" versionCode=\"34\" versionName=\"3.4\"\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity android:name=\"${applicationId}.activityOne\"/>\n"
                + "</manifest>";

        MockLog mockLog = new MockLog();
        File inputFile = TestUtils.inputAsFile("testPlaceholderSubstitution", xml);
        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .setOverride(ManifestSystemProperty.Document.PACKAGE, "foo.bar")
                            .merge();

            assertTrue(mergingReport.getResult().isSuccess());
            Document document = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            assertEquals(
                    "foo.bar",
                    document.getElementsByTagName("manifest")
                            .item(0)
                            .getAttributes()
                            .getNamedItem("package")
                            .getNodeValue());
            Optional<Element> activityOne =
                    getElementByTypeAndKey(document, "activity", "foo.bar.activityOne");
            assertTrue(activityOne.isPresent());
            assertArrayEquals(
                    new Object[] {"activity#foo.bar.activityOne", "manifest"},
                    mergingReport
                            .getActions()
                            .getNodeKeys()
                            .stream()
                            .map(XmlNode.NodeKey::toString)
                            .sorted()
                            .toArray());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            inputFile.delete();
        }
    }

    @Test
    public void testNoApplicationIdValueProvided() throws Exception {
        String xml =
                ""
                        + "<manifest package=\"foo.bar\" versionCode=\"34\" versionName=\"3.4\"\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <activity android:name=\"${applicationId}.activityOne\"/>\n"
                        + "</manifest>";

        MockLog mockLog = new MockLog();
        File inputFile = TestUtils.inputAsFile("testPlaceholderSubstitution", xml);
        try {
            MergingReport mergingReport = ManifestMerger2
                    .newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                    .merge();

            assertTrue(mergingReport.getResult().isSuccess());
            assertNotNull(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            Document document = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            assertEquals(
                    "foo.bar",
                    document.getElementsByTagName("manifest")
                            .item(0)
                            .getAttributes()
                            .getNamedItem("package")
                            .getNodeValue());
            Optional<Element> activityOne =
                    getElementByTypeAndKey(document, "activity", "foo.bar.activityOne");
            assertTrue(activityOne.isPresent());
            assertArrayEquals(
                    new Object[] {"activity#foo.bar.activityOne", "manifest"},
                    mergingReport
                            .getActions()
                            .getNodeKeys()
                            .stream()
                            .map(XmlNode.NodeKey::toString)
                            .sorted()
                            .toArray());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            inputFile.delete();
        }
    }

    @Test
    public void testNoFqcnsExtraction() throws Exception {
        String xml = ""
                + "<manifest\n"
                + "    package=\"com.foo.example\""
                + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity t:name=\"activityOne\"/>\n"
                + "    <activity t:name=\"com.foo.bar.example.activityTwo\"/>\n"
                + "    <activity t:name=\"com.foo.example.activityThree\"/>\n"
                + "    <application t:name=\".applicationOne\" "
                + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testFcqnsExtraction", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport = ManifestMerger2
                .newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        assertEquals("com.foo.example.activityOne",
                xmlDocument.getElementsByTagName("activity").item(0).getAttributes()
                        .item(0).getNodeValue());
        assertEquals("com.foo.bar.example.activityTwo",
                xmlDocument.getElementsByTagName("activity").item(1).getAttributes()
                        .item(0).getNodeValue());
        assertEquals("com.foo.example.activityThree",
                xmlDocument.getElementsByTagName("activity").item(2).getAttributes()
                        .item(0).getNodeValue());
        assertEquals("com.foo.example.applicationOne",
                xmlDocument.getElementsByTagName("application").item(0).getAttributes()
                        .getNamedItemNS("http://schemas.android.com/apk/res/android", "name")
                        .getNodeValue());
        assertEquals("com.foo.example.myBackupAgent",
                xmlDocument.getElementsByTagName("application").item(0).getAttributes()
                        .getNamedItemNS("http://schemas.android.com/apk/res/android", "backupAgent")
                        .getNodeValue());
    }

    @Test
    public void testFqcnsExtraction() throws Exception {
        String xml =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.example\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <activity t:name=\"activityOne\"/>\n"
                        + "    <activity t:name=\"com.foo.bar.example.activityTwo\"/>\n"
                        + "    <activity t:name=\"com.foo.example.activityThree\"/>\n"
                        + "    <activity t:name=\"com.foo.example\"/>"
                        + "    <application t:name=\".applicationOne\" "
                        + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testFcqnsExtraction", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport = ManifestMerger2
                .newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                .withFeatures(Feature.EXTRACT_FQCNS)
                .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        assertEquals(
                ".activityOne",
                xmlDocument
                        .getElementsByTagName("activity")
                        .item(0)
                        .getAttributes()
                        .item(0)
                        .getNodeValue());
        assertEquals(
                "com.foo.bar.example.activityTwo",
                xmlDocument
                        .getElementsByTagName("activity")
                        .item(1)
                        .getAttributes()
                        .item(0)
                        .getNodeValue());
        assertEquals(
                ".activityThree",
                xmlDocument
                        .getElementsByTagName("activity")
                        .item(2)
                        .getAttributes()
                        .item(0)
                        .getNodeValue());
        assertEquals(
                "com.foo.example",
                xmlDocument
                        .getElementsByTagName("activity")
                        .item(3)
                        .getAttributes()
                        .item(0)
                        .getNodeValue());
        assertEquals(
                ".applicationOne",
                xmlDocument
                        .getElementsByTagName("application")
                        .item(0)
                        .getAttributes()
                        .getNamedItemNS("http://schemas.android.com/apk/res/android", "name")
                        .getNodeValue());
        assertEquals(
                ".myBackupAgent",
                xmlDocument
                        .getElementsByTagName("application")
                        .item(0)
                        .getAttributes()
                        .getNamedItemNS("http://schemas.android.com/apk/res/android", "backupAgent")
                        .getNodeValue());
    }

    /**
     * Test to ensure that the fully qualified class name extraction uses the namespace specified by
     * {@link ManifestMerger2.Invoker#setNamespace(String)} if it's called.
     *
     * <p>In this test, the "com.foo.bar.example" namespace specified via the manifest's package
     * attribute is overridden by the "com.foo.example" namespace set on the Invoker.
     */
    @Test
    public void testFqcnsExtractionWithNamespace() throws Exception {
        String xml =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.bar.example\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <activity t:name=\"activityOne\"/>\n"
                        + "    <activity t:name=\"com.foo.bar.example.activityTwo\"/>\n"
                        + "    <activity t:name=\"com.foo.example.activityThree\"/>\n"
                        + "    <activity t:name=\"com.foo.example\"/>"
                        + "    <application t:name=\".applicationOne\" "
                        + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testFcqnsExtraction", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .setNamespace("com.foo.example")
                        .withFeatures(Feature.EXTRACT_FQCNS)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        assertEquals(".activityOne",
                xmlDocument.getElementsByTagName("activity").item(0).getAttributes()
                        .item(0).getNodeValue());
        assertEquals("com.foo.bar.example.activityTwo",
                xmlDocument.getElementsByTagName("activity").item(1).getAttributes()
                        .item(0).getNodeValue());
        assertEquals(".activityThree",
                xmlDocument.getElementsByTagName("activity").item(2).getAttributes()
                        .item(0).getNodeValue());
        assertEquals(
                "com.foo.example",
                xmlDocument
                        .getElementsByTagName("activity")
                        .item(3)
                        .getAttributes()
                        .item(0)
                        .getNodeValue());
        assertEquals(".applicationOne",
                xmlDocument.getElementsByTagName("application").item(0).getAttributes()
                        .getNamedItemNS("http://schemas.android.com/apk/res/android", "name")
                        .getNodeValue());
        assertEquals(".myBackupAgent",
                xmlDocument.getElementsByTagName("application").item(0).getAttributes()
                        .getNamedItemNS("http://schemas.android.com/apk/res/android", "backupAgent")
                        .getNodeValue());
    }

    @Test
    public void testNoPlaceholderReplacement() throws Exception {
        String xml = ""
                + "<manifest\n"
                + "    package=\"${applicationId}\""
                + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity t:name=\"activityOne\"/>\n"
                + "    <application t:name=\".applicationOne\" "
                + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testNoPlaceHolderReplacement", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport = ManifestMerger2
                .newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                .withFeatures(Feature.NO_PLACEHOLDER_REPLACEMENT)
                .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        assertEquals("${applicationId}",
                xmlDocument.getElementsByTagName("manifest")
                        .item(0).getAttributes().getNamedItem("package").getNodeValue());
    }

    @Test
    public void testReplaceInputStream() throws Exception {
        // This test is identical to testNoPlaceholderReplacement but instead
        // of reading from a string, we test the ManifestMerger's ability to
        // supply a custom input stream
        final String xml = ""
                + "<manifest\n"
                + "    package=\"${applicationId}\""
                + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity t:name=\"activityOne\"/>\n"
                + "    <application t:name=\".applicationOne\" "
                + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                + "</manifest>";
        String staleContent = "<manifest />";

        // Note: disk content is wrong/stale; make sure we read the live content instead
        File inputFile = TestUtils.inputAsFile("testNoPlaceHolderReplacement", staleContent);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport = ManifestMerger2
                .newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                .withFeatures(Feature.NO_PLACEHOLDER_REPLACEMENT)
                .withFileStreamProvider(new ManifestMerger2.FileStreamProvider() {
                    @Override
                    protected InputStream getInputStream(@NonNull File file)
                            throws FileNotFoundException {
                        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
                    }
                })
                .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        assertEquals("${applicationId}",
                xmlDocument.getElementsByTagName("manifest")
                        .item(0).getAttributes().getNamedItem("package").getNodeValue());
    }

    @Test
    public void testOverlayOnMainMerge() throws Exception {
        String xmlInput = ""
                     + "<manifest\n"
                     + "    package=\"com.foo.example\""
                     + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                     + "    <activity t:name=\"activityOne\"/>\n"
                     + "    <application t:name=\".applicationOne\" "
                     + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                     + "</manifest>";

        String xmlToMerge = ""
                     + "<manifest\n"
                     + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                     + "    <application>\n"
                     + "        <activity t:name=\"activityTwo\"/>\n"
                     + "    </application>\n"
                     + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testOverlayOnMainMerge1", xmlInput);
        File overlayFile = TestUtils.inputAsFile("testOverlayOnMainMerge2", xmlToMerge);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .withFeatures(Feature.EXTRACT_FQCNS, Feature.NO_PLACEHOLDER_REPLACEMENT)
                        .addFlavorAndBuildTypeManifest(overlayFile)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        assertEquals("com.foo.example", xmlDocument.getElementsByTagName("manifest")
          .item(0).getAttributes().getNamedItem("package").getNodeValue());

        NodeList activityList = xmlDocument.getElementsByTagName("activity");
        assertEquals(
                ".activityOne",
                activityList.item(0).getAttributes().getNamedItem("t:name").getNodeValue());
        assertEquals(
                ".activityTwo",
                activityList.item(1).getAttributes().getNamedItem("t:name").getNodeValue());
    }

    @Test
    public void testOverlayOnOverlayMerge() throws Exception {
        String xmlInput =
                ""
                        + "<manifest\n"
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <activity t:name=\"activityOne\"/>\n"
                        + "    <application t:name=\".applicationOne\" "
                        + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                        + "</manifest>";

        String xmlToMerge =
                ""
                        + "<manifest\n"
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application>\n"
                        + "        <activity t:name=\"activityTwo\"/>\n"
                        + "    </application>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testOverlayOnOverlayMerge1", xmlInput);
        File overlayFile = TestUtils.inputAsFile("testOverlayOnOverlayMerge2", xmlToMerge);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .withFeatures(Feature.EXTRACT_FQCNS, Feature.NO_PLACEHOLDER_REPLACEMENT)
                        .addFlavorAndBuildTypeManifest(overlayFile)
                        .asType(XmlDocument.Type.OVERLAY)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));

        NodeList activityList = xmlDocument.getElementsByTagName("activity");
        assertEquals(".activityOne", activityList.item(0).getAttributes().getNamedItem("t:name").getNodeValue());
        assertEquals(".activityTwo", activityList.item(1).getAttributes().getNamedItem("t:name").getNodeValue());
    }

    @Test
    public void testMergeWithImpliedPreviewTargetSdk() throws Exception {
        // sdk version "foo" is acting as a preview code.
        String xmlInput =
                ""
                        + "<manifest\n"
                        + "    package=\"foo.main\""
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <uses-sdk android:minSdkVersion=\"foo\"\n"
                        + "        android:targetSdkVersion=\"24\"/>\n"
                        + "</manifest>";

        String xmlToMerge =
                ""
                        + "<manifest\n"
                        + "    package=\"lib\""
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <uses-sdk android:minSdkVersion=\"foo\"/>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testImpliedTargetSdk1", xmlInput);
        File libFile = TestUtils.inputAsFile("testImpliedTargetSdk2", xmlToMerge);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .addLibraryManifest(libFile)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document document = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        assertEquals(
                "foo",
                document.getElementsByTagName(SdkConstants.TAG_USES_SDK)
                        .item(0)
                        .getAttributes()
                        .getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_MIN_SDK_VERSION)
                        .getNodeValue());
        assertEquals(
                "24",
                document.getElementsByTagName(SdkConstants.TAG_USES_SDK)
                        .item(0)
                        .getAttributes()
                        .getNamedItemNS(
                                SdkConstants.ANDROID_URI, SdkConstants.ATTR_TARGET_SDK_VERSION)
                        .getNodeValue());
    }


    /**
     * If android:hasCode is set in a lower priority manifest, but not in the higher priority
     * overlay, we want the hasCode setting from the lower priority manifest to be merged in, so
     * that users only have to set it once in the main manifest if all their variants have no code.
     * This test matches the existing behavior.
     */
    @Test
    public void testWhenHasCodeIsFalseInMainAndUnspecifiedInOverlay() throws Exception {
        String input =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.bar\""
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application android:hasCode=\"false\"/>\n"
                        + "</manifest>";

        String overlay =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application/>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testHasCode1Input", input);
        File overlayFile = TestUtils.inputAsFile("testHasCode1Overlay", overlay);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .addFlavorAndBuildTypeManifest(overlayFile)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));

        NodeList applications = xmlDocument.getElementsByTagName(SdkConstants.TAG_APPLICATION);
        assertEquals(1, applications.getLength());
        Node application = applications.item(0);
        // verify hasCode is false.
        NamedNodeMap applicationAttributes = application.getAttributes();
        assertThat(
                        Boolean.parseBoolean(
                                applicationAttributes
                                        .getNamedItemNS(
                                                SdkConstants.ANDROID_URI,
                                                SdkConstants.ATTR_HAS_CODE)
                                        .getNodeValue()))
                .isFalse();
    }

    /**
     * If android:hasCode is set in lower and higher priority manifests, we want the hasCode setting
     * to be merged with an OR merging policy, to allow for a module with some variants having code
     * and some not.
     */
    @Test
    public void testWhenHasCodeIsFalseInMainAndTrueInOverlay() throws Exception {
        String input =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.bar\""
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application android:hasCode=\"false\"/>\n"
                        + "</manifest>";

        String overlay =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application android:hasCode=\"true\"/>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testHasCode2Input", input);
        File overlayFile = TestUtils.inputAsFile("testHasCode2Overlay", overlay);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .addFlavorAndBuildTypeManifest(overlayFile)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));

        NodeList applications = xmlDocument.getElementsByTagName(SdkConstants.TAG_APPLICATION);
        assertEquals(1, applications.getLength());
        Node application = applications.item(0);
        // verify hasCode is true.
        NamedNodeMap applicationAttributes = application.getAttributes();
        assertThat(
                        Boolean.parseBoolean(
                                applicationAttributes
                                        .getNamedItemNS(
                                                SdkConstants.ANDROID_URI,
                                                SdkConstants.ATTR_HAS_CODE)
                                        .getNodeValue()))
                .isTrue();
    }

    /** android:hasCode should never be merged from a library (or feature) module. */
    @Test
    public void testThatHasCodeFromLibraryIsNotMerged() throws Exception {
        String input =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.bar\""
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application/>\n"
                        + "</manifest>";

        String library =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.baz\""
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application android:hasCode=\"false\"/>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testHasCode3Input", input);
        File libraryFile = TestUtils.inputAsFile("testHasCode3Library", library);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .addLibraryManifest(libraryFile)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));

        NodeList applications = xmlDocument.getElementsByTagName(SdkConstants.TAG_APPLICATION);
        assertEquals(1, applications.getLength());
        Node application = applications.item(0);
        // verify hasCode is unspecified.
        NamedNodeMap applicationAttributes = application.getAttributes();
        assertThat(
                        applicationAttributes.getNamedItemNS(
                                SdkConstants.ANDROID_URI, SdkConstants.ATTR_HAS_CODE))
                .isNull();
    }

    /** dist:module should be merged from an overlay module. */
    @Test
    public void testThatDistModuleFromOverlayIsMerged() throws Exception {
        String input =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.bar\""
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application android:hasCode=\"false\"/>\n"
                        + "</manifest>";

        String overlay =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:dist=\"http://schemas.android.com/apk/distribution\">\n"
                        + "    <dist:module dist:onDemand=\"true\" dist:title=\"foo\">\n"
                        + "        <dist:fusing dist:include=\"true\" />\n"
                        + "    </dist:module>\n"
                        + "    <application/>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testDistModule1Input", input);
        File overlayFile = TestUtils.inputAsFile("testDistModule1Overlay", overlay);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .addFlavorAndBuildTypeManifest(overlayFile)
                        .merge();

        assertThat(mergingReport.getResult().isSuccess()).isTrue();
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));

        NodeList modules = xmlDocument.getElementsByTagNameNS(DIST_URI, TAG_MODULE);
        assertThat(modules.getLength()).isEqualTo(1);
        Node module = modules.item(0);
        // verify module is as expected
        NamedNodeMap moduleAttributes = module.getAttributes();
        assertThat(
                        Boolean.parseBoolean(
                                moduleAttributes
                                        .getNamedItemNS(DIST_URI, ATTR_ON_DEMAND)
                                        .getNodeValue()))
                .isTrue();
        assertThat(moduleAttributes.getNamedItemNS(DIST_URI, MANIFEST_ATTR_TITLE).getNodeValue())
                .isEqualTo("foo");
        NodeList moduleChildNodes = module.getChildNodes();
        // moduleChildNodes.getLength() is 3 because of 2 child attributes and 1 child element.
        assertThat(moduleChildNodes.getLength()).isEqualTo(3);
    }

    /** dist:module should never be merged from a library (or feature) module. */
    @Test
    public void testThatDistModuleFromLibraryIsNotMerged() throws Exception {
        String input =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.bar\""
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application/>\n"
                        + "</manifest>";

        String library =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.baz\""
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:dist=\"http://schemas.android.com/apk/distribution\">\n"
                        + "    <dist:module dist:onDemand=\"true\" dist:title=\"foo\">\n"
                        + "        <dist:fusing dist:include=\"true\" />\n"
                        + "    </dist:module>\n"
                        + "    <application/>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testDistModule2Input", input);
        File libraryFile = TestUtils.inputAsFile("testDistModule2Library", library);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .addLibraryManifest(libraryFile)
                        .merge();

        assertThat(mergingReport.getResult().isSuccess()).isTrue();
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));

        NodeList modules = xmlDocument.getElementsByTagNameNS(DIST_URI, TAG_MODULE);
        assertThat(modules.getLength()).isEqualTo(0);
    }

    @Test
    public void testAddingTestOnlyAttribute() throws Exception {
        String xml = ""
                + "<manifest\n"
                + "    package=\"com.foo.bar\""
                + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <activity t:name=\"activityOne\"/>\n"
                + "    <application t:name=\".applicationOne\" "
                + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testNoPlaceHolderReplacement", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport = ManifestMerger2
                .newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                .withFeatures(Feature.TEST_ONLY)
                .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        String xmlText = mergingReport.getMergedDocument(MergedManifestKind.MERGED);
        Document xmlDocument = parse(xmlText);
        assertEquals("true",
                xmlDocument.getElementsByTagName(SdkConstants.TAG_APPLICATION)
                        .item(0).getAttributes()
                        .getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEST_ONLY)
                        .getNodeValue());
    }

    @Test
    public void testAddingDebuggableAttribute() throws Exception {
        String xml =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.bar\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <activity t:name=\"activityOne\"/>\n"
                        + "    <application t:name=\".applicationOne\" "
                        + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testAddingDebuggableAttribute", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .withFeatures(Feature.DEBUGGABLE)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        String xmlText = mergingReport.getMergedDocument(MergedManifestKind.MERGED);
        Document xmlDocument = parse(xmlText);
        assertEquals(
                "true",
                xmlDocument
                        .getElementsByTagName(SdkConstants.TAG_APPLICATION)
                        .item(0)
                        .getAttributes()
                        .getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_DEBUGGABLE)
                        .getNodeValue());
    }

    @Test
    public void testAddingMultiDexApplicationWhenMissing() throws Exception {
        doTestAddingMultiDexApplicationWhenMissing(true);
        doTestAddingMultiDexApplicationWhenMissing(false);
    }

    private void doTestAddingMultiDexApplicationWhenMissing(boolean useAndroidX) throws Exception {
        String xml =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.bar\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <activity t:name=\"activityOne\"/>\n"
                        + "    <application"
                        + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testAddingDebuggableAttribute", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .withFeatures(
                                useAndroidX
                                        ? Feature.ADD_ANDROIDX_MULTIDEX_APPLICATION_IF_NO_NAME
                                        : Feature.ADD_SUPPORT_MULTIDEX_APPLICATION_IF_NO_NAME)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        String xmlText = mergingReport.getMergedDocument(MergedManifestKind.MERGED);
        Document xmlDocument = parse(xmlText);
        assertEquals(
                useAndroidX
                        ? AndroidXConstants.MULTI_DEX_APPLICATION.newName()
                        : AndroidXConstants.MULTI_DEX_APPLICATION.oldName(),
                xmlDocument
                        .getElementsByTagName(SdkConstants.TAG_APPLICATION)
                        .item(0)
                        .getAttributes()
                        .getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                        .getNodeValue());
    }

    @Test
    public void testAddingMultiDexApplicationNotAddedWhenPresent() throws Exception {
        String xml =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.bar\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <activity t:name=\"activityOne\"/>\n"
                        + "    <application t:name=\".applicationOne\" "
                        + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testAddingDebuggableAttribute", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .withFeatures(Feature.ADD_ANDROIDX_MULTIDEX_APPLICATION_IF_NO_NAME)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        String xmlText = mergingReport.getMergedDocument(MergedManifestKind.MERGED);
        Document xmlDocument = parse(xmlText);
        assertEquals(
                "com.foo.bar.applicationOne",
                xmlDocument
                        .getElementsByTagName(SdkConstants.TAG_APPLICATION)
                        .item(0)
                        .getAttributes()
                        .getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                        .getNodeValue());
    }

    @Test
    public void testInternetPermissionAdded() throws Exception {
        String xml =
                ""
                        + "<manifest\n"
                        + "    package=\"foo.bar\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <activity t:name=\"activityOne\"/>\n"
                        + "    <uses-permission t:name=\"android.permission.RECEIVE_SMS\"/>\n"
                        + "    <application t:name=\".applicationOne\" "
                        + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testInternetPermissionAdded", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .withFeatures(Feature.ADVANCED_PROFILING)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        NodeList nodes = xmlDocument.getElementsByTagName("uses-permission");
        assertEquals(2, nodes.getLength());
        assertEquals(
                "android.permission.RECEIVE_SMS",
                nodes.item(0).getAttributes().getNamedItem("t:name").getNodeValue());
        assertEquals(
                "android.permission.INTERNET",
                nodes.item(1).getAttributes().getNamedItem("t:name").getNodeValue());
    }

    @Test
    public void testInternetPermissionNotDupped() throws Exception {
        String xml =
                ""
                        + "<manifest\n"
                        + "    package=\"foo.bar\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <uses-permission t:name=\"android.permission.INTERNET\"/>\n"
                        + "    <uses-permission t:name=\"android.permission.RECEIVE_SMS\"/>\n"
                        + "    <activity t:name=\"activityOne\"/>\n"
                        + "    <application t:name=\".applicationOne\" "
                        + "         t:backupAgent=\"com.foo.example.myBackupAgent\"/>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testInternetPermissionNotDupped", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .withFeatures(Feature.ADVANCED_PROFILING)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        NodeList nodes = xmlDocument.getElementsByTagName("uses-permission");
        assertEquals(2, nodes.getLength());
        assertEquals(
                "android.permission.INTERNET",
                nodes.item(0).getAttributes().getNamedItem("t:name").getNodeValue());
        assertEquals(
                "android.permission.RECEIVE_SMS",
                nodes.item(1).getAttributes().getNamedItem("t:name").getNodeValue());
    }

    @Test
    public void testMissingApplicationInManifest() throws Exception {
        String xml =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.example\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <activity t:name=\"activityOne\"/>\n"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("testMissingApplication", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());
        Document xmlDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));

        Truth.assertThat(xmlDocument.getElementsByTagName(SdkConstants.TAG_APPLICATION).getLength())
                .isEqualTo(1);
    }

    @Test
    public void testFeatureSplitValidation() throws Exception {
        File inputFile = TestUtils.inputAsFile("testFeatureSplitOption", "</manifest>\n");
        MockLog mockLog = new MockLog();
        ManifestMerger2.Invoker invoker =
                ManifestMerger2.newMerger(
                        inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION);
        validateFeatureName(invoker, "_split12", false);
        validateFeatureName(invoker, ":split12", false);
        validateFeatureName(invoker, "split12", true);
        validateFeatureName(invoker, "split-12", false);
        validateFeatureName(invoker, "split_12", true);
        validateFeatureName(invoker, "foosplit_12", true);
        validateFeatureName(invoker, "SPLIT", true);
        validateFeatureName(invoker, "_SPLIT", false);
    }

    @Test
    public void testFeatureMetadataMinSdkStripped() throws Exception {
        String xml =
                ""
                        + "<manifest\n"
                        + "    package=\"com.foo.example\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application t:name=\".applicationOne\">\n"
                        + "        <activity t:name=\"activityOne\"/>\n"
                        + "    </application>\n"
                        + "    <uses-sdk\n"
                        + "        t:minSdkVersion=\"22\"\n"
                        + "        t:targetSdkVersion=\"27\" />"
                        + "</manifest>";

        File inputFile = TestUtils.inputAsFile("featureMetadataMinSdkStripped", xml);

        MockLog mockLog = new MockLog();
        MergingReport mergingReport =
                ManifestMerger2.newMerger(inputFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                        .setFeatureName("dynamic_split")
                        .withFeatures(Feature.ADD_DYNAMIC_FEATURE_ATTRIBUTES)
                        .merge();

        assertTrue(mergingReport.getResult().isSuccess());

        Document mergedDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));

        assertEquals(
                "22",
                mergedDocument
                        .getElementsByTagName(SdkConstants.TAG_USES_SDK)
                        .item(0)
                        .getAttributes()
                        .getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_MIN_SDK_VERSION)
                        .getNodeValue());

        Document bundleDocument = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));

        assertEquals(
                "22",
                bundleDocument
                        .getElementsByTagName(SdkConstants.TAG_USES_SDK)
                        .item(0)
                        .getAttributes()
                        .getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_MIN_SDK_VERSION)
                        .getNodeValue());
    }

    @Test
    public void testMainAppWithDynamicFeature() throws Exception {
        MockLog mockLog = new MockLog();
        String app =
                ""
                        + "<manifest\n"
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    package=\"com.example.app1\">\n"
                        + "\n"
                        + "    <application t:name=\".applicationOne\" tools:replace=\"t:name\">\n"
                        + "    </application>"
                        + "\n"
                        + "</manifest>";
        File appFile =
                TestUtils.inputAsFile("testMainAppWithDynamicFeatureForInstantAppManifest", app);

        String featureInput =
                ""
                        + "<manifest\n"
                        + "    package=\"com.example.feature\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application t:name=\".applicationOne\">\n"
                        + "        <activity t:name=\"activityOne\" t:splitName=\"feature\" />\n"
                        + "    </application>\n"
                        + "</manifest>";

        File libFile =
                TestUtils.inputAsFile(
                        "testMainAppWithDynamicFeatureForInstantAppManifest", featureInput);
        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    appFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .addLibraryManifest(libFile)
                            .merge();
            assertEquals(MergingReport.Result.SUCCESS, mergingReport.getResult());
            // ensure tools annotation removal.
            Document xmlDocument =
                    parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            // the feature should still be in the merged manifest as it is stripped during
            // packaged manifest production.
            assertEquals(
                    "feature",
                    xmlDocument
                            .getElementsByTagName(SdkConstants.TAG_ACTIVITY)
                            .item(0)
                            .getAttributes()
                            .getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SPLIT_NAME)
                            .getNodeValue());
            // the target sandbox is only set during INSTANT_APP production.
            assertNull(
                    "targetSandboxVersion should be empty",
                    xmlDocument
                            .getDocumentElement()
                            .getAttributes()
                            .getNamedItemNS(
                                    SdkConstants.ANDROID_URI,
                                    SdkConstants.ATTR_TARGET_SANDBOX_VERSION));
        } finally {
            assertTrue(appFile.delete());
            assertTrue(libFile.delete());
        }
    }

    @Test
    public void testMainAppWithDynamicFeatureAndTargetSandboxVersionSetForInstantAppManifest()
            throws Exception {
        MockLog mockLog = new MockLog();
        String app =
                ""
                        + "<manifest\n"
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    t:targetSandboxVersion = \"1\"\n"
                        + "    package=\"com.example.app1\">\n"
                        + "\n"
                        + "    <application t:name=\".applicationOne\" tools:replace=\"t:name\">\n"
                        + "    </application>"
                        + "\n"
                        + "</manifest>";
        File appFile =
                TestUtils.inputAsFile("testMainAppWithDynamicFeatureForInstantAppManifest", app);

        String featureInput =
                ""
                        + "<manifest\n"
                        + "    package=\"com.example.feature\""
                        + "    xmlns:t=\"http://schemas.android.com/apk/res/android\">\n"
                        + "    <application t:name=\".applicationOne\">\n"
                        + "        <activity t:name=\"activityOne\" t:splitName=\"feature\" />\n"
                        + "    </application>\n"
                        + "</manifest>";

        File libFile =
                TestUtils.inputAsFile(
                        "testMainAppWithDynamicFeatureForInstantAppManifest", featureInput);
        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    appFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .addLibraryManifest(libFile)
                            .merge();
            assertEquals(MergingReport.Result.SUCCESS, mergingReport.getResult());
            // ensure tools annotation removal.
            Document xmlDocument =
                    parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            assertEquals(
                    "1",
                    xmlDocument
                            .getDocumentElement()
                            .getAttributeNS(
                                    SdkConstants.ANDROID_URI,
                                    SdkConstants.ATTR_TARGET_SANDBOX_VERSION));
        } finally {
            assertTrue(appFile.delete());
            assertTrue(libFile.delete());
        }
    }

    @Test
    public void testAutomaticallyHandlingAttributeConflicts() throws Exception {
        MockLog mockLog = new MockLog();
        String overlay =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.app1\">\n"
                        + "\n"
                        + "    <application android:label=\"@string/lib_name\">\n"
                        + "        <meta-data\n"
                        + "            android:name=\"com.google.android.wearable.standalone\"\n"
                        + "            android:value=\"true\" />\n"
                        + "    </application>"
                        + "\n"
                        + "</manifest>";

        File overlayFile =
                TestUtils.inputAsFile("testAutomaticallyHandlingAttributeConflicts", overlay);
        assertTrue(overlayFile.exists());

        String libraryInput =
                ""
                        + "<manifest\n"
                        + "xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "package=\"com.example.app1\">\n"
                        + "\n"
                        + "<application android:name=\"TheApp\" >\n"
                        + "        <meta-data\n"
                        + "            android:name=\"com.google.android.wearable.standalone\"\n"
                        + "            android:value=\"false\" />\n"
                        + "</application>\n"
                        + "\n"
                        + "</manifest>";
        File libFile =
                TestUtils.inputAsFile("testAutomaticallyHandlingAttributeConflicts", libraryInput);

        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(libFile, mockLog, ManifestMerger2.MergeType.LIBRARY)
                            .withFeatures(Feature.HANDLE_VALUE_CONFLICTS_AUTOMATICALLY)
                            .addFlavorAndBuildTypeManifest(overlayFile)
                            .merge();
            assertNotEquals(MergingReport.Result.ERROR, mergingReport.getResult());
            Document xmlDocument =
                    parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            Element meta =
                    (Element) xmlDocument.getElementsByTagName(SdkConstants.TAG_META_DATA).item(0);
            String standalone = meta.getAttributeNS(SdkConstants.ANDROID_URI, "value");
            assertEquals("true", standalone);
        } finally {
            assertTrue(overlayFile.delete());
            assertTrue(libFile.delete());
        }
    }

    @Test
    public void testToolsCommentRemovalForLibraries() throws Exception {
        MockLog mockLog = new MockLog();
        String overlay =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    package=\"com.example.app1\">\n"
                        + "\n"
                        + "    <application android:label=\"@string/lib_name\">\n"
                        + "       <!-- This comment should NOT be removed 1 -->\n"
                        + "       <!-- This comment should NOT be removed 2 -->\n"
                        + "       <meta-data android:name=\"foo\"/>"
                        + "       <!-- This comment should be removed 1 -->\n"
                        + "       <!-- This comment should be removed 2 -->\n"
                        + "       <meta-data android:name=\"bar\" tools:node=\"remove\"/>"
                        + "       <!-- This comment should be removed 3 -->\n"
                        + "       <!-- This comment should be removed 4 -->\n"
                        + "       <activity tools:node=\"removeAll\">\n"
                        + "       </activity>\n"
                        + "    </application>"
                        + "\n"
                        + "</manifest>";

        File overlayFile = TestUtils.inputAsFile("testToolsAnnotationRemoval", overlay);
        assertTrue(overlayFile.exists());

        String libraryInput =
                ""
                        + "<manifest\n"
                        + "xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "package=\"com.example.app1\">\n"
                        + "\n"
                        + "<application android:name=\"TheApp\" >\n"
                        + "    <meta-data android:name=\"foo\"/>"
                        + "    <meta-data android:name=\"bar\"/>"
                        + "    <!-- Activity to configure widget -->\n"
                        + "    <activity\n"
                        + "            android:icon=\"@drawable/widget_icon\"\n"
                        + "            android:label=\"Configure Widget\"\n"
                        + "            android:name=\"com.example.lib1.WidgetConfigurationUI\"\n"
                        + "            android:theme=\"@style/Theme.WidgetConfigurationUI\" >\n"
                        + "        <intent-filter >\n"
                        + "            <action android:name=\"android.appwidget.action.APPWIDGET_CONFIGURE\" />\n"
                        + "        </intent-filter>\n"
                        + "    </activity>\n"
                        + "</application>\n"
                        + "\n"
                        + "</manifest>";
        File libFile = TestUtils.inputAsFile("testToolsCommentRemoval", libraryInput);

        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(libFile, mockLog, ManifestMerger2.MergeType.LIBRARY)
                            .withFeatures(Feature.REMOVE_TOOLS_DECLARATIONS)
                            .addFlavorAndBuildTypeManifest(overlayFile)
                            .merge();
            assertThat(mergingReport.getResult()).isEqualTo(MergingReport.Result.SUCCESS);
            String mergedDocument = mergingReport.getMergedDocument(MergedManifestKind.MERGED);
            assertThat(mergedDocument).contains("This comment should NOT be removed");
            assertThat(mergedDocument).doesNotContain("This comment should be removed");
        } finally {
            assertThat(overlayFile.delete()).named("Overlay was deleted").isTrue();
            assertThat(libFile.delete()).named("Lib file was deleted").isTrue();
        }
    }

    @Test
    public void testFailIfPackageNamehasNoDot_application() throws Exception {

        MockLog mockLog = new MockLog();
        String input =
                "<manifest\n"
                        + "        xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "        package=\"example\">\n"
                        + "    <uses-sdk\n"
                        + "            android:minSdkVersion=\"21\"\n"
                        + "            android:targetSdkVersion=\"24\" />\n"
                        + "    <activity android:name=\"com.example.ActivityOne\" />\n"
                        + "</manifest>";

        File tmpFile = TestUtils.inputAsFile("ManifestMerger2Test_failBecauseOfNoDot", input);
        assertTrue(tmpFile.exists());

        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    tmpFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .merge();
            assertEquals(MergingReport.Result.ERROR, mergingReport.getResult());
            assertStringPresenceInLogRecords(mergingReport, "Package name 'example' at position ");
            assertStringPresenceInLogRecords(
                    mergingReport, " should contain at least one '.' (dot) character");
            assertNull(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        } finally {
            assertTrue(tmpFile.delete());
        }
    }

    @Test
    public void testSuccessIfPackageNamehasNoDot_library() throws Exception {

        MockLog mockLog = new MockLog();
        String input =
                "<manifest\n"
                        + "        xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "        package=\"example\">\n"
                        + "    <uses-sdk\n"
                        + "            android:minSdkVersion=\"21\"\n"
                        + "            android:targetSdkVersion=\"24\" />\n"
                        + "    <activity android:name=\"com.example.ActivityOne\" />\n"
                        + "</manifest>";

        File tmpFile = TestUtils.inputAsFile("ManifestMerger2Test_failBecauseOfNoDot", input);
        assertTrue(tmpFile.exists());

        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(tmpFile, mockLog, ManifestMerger2.MergeType.LIBRARY)
                            .merge();
            assertEquals(MergingReport.Result.SUCCESS, mergingReport.getResult());
            assertNotNull(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
        } finally {
            assertTrue(tmpFile.delete());
        }
    }

    @Test
    public void testRemoveSplitFromDynamicFeature() throws Exception {

        MockLog mockLog = new MockLog();
        String featureMainManifestText =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"foo.bar\" split=\"feature1\">\n"
                        + "        <activity android:name=\"foo.bar.MainActivity\" />\n"
                        + "</manifest>";

        File featureManifest = TestUtils.inputAsFile("FeatureManifest", featureMainManifestText);
        assertTrue(featureManifest.exists());

        String featureOverlayManifestText =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    split=\"feature1\">\n"
                        + "</manifest>";

        File featureOverlayManifest =
                TestUtils.inputAsFile("FeatureOverlayManifest", featureOverlayManifestText);
        assertTrue(featureOverlayManifest.exists());

        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    featureManifest, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .addFlavorAndBuildTypeManifest(featureOverlayManifest)
                            .setFeatureName("feature1")
                            .merge();
            assertEquals(MergingReport.Result.WARNING, mergingReport.getResult());
            assertStringPresenceInLogRecords(
                    mergingReport, "Attribute 'split' was removed from FeatureManifest");
            assertStringPresenceInLogRecords(
                    mergingReport, "Attribute 'split' was removed from FeatureOverlayManifest");
            assertStringPresenceInLogRecords(
                    mergingReport,
                    "The Android Gradle plugin includes it for you when building your project.");
            assertStringPresenceInLogRecords(
                    mergingReport,
                    "See https://d.android.com/r/studio-ui/dynamic-delivery/dynamic-feature-manifest for details");
            assertNotNull(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            assertThat(mergingReport.getMergedDocument(MergedManifestKind.MERGED))
                    .doesNotContain("split");
        } finally {
            assertTrue(featureManifest.delete());
        }
    }

    @Test
    public void testExtractNativeLibs_notInjected() throws Exception {
        MockLog mockLog = new MockLog();
        String input =
                "<manifest\n"
                        + "        xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "        package=\"foo.bar\">\n"
                        + "    <application/>\n"
                        + "</manifest>";

        File tmpFile = TestUtils.inputAsFile("ManifestMerger2Test_noExtractNativeLibs", input);
        assertTrue(tmpFile.exists());

        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    tmpFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .merge();
            assertEquals(MergingReport.Result.SUCCESS, mergingReport.getResult());
            String mergedDocument = mergingReport.getMergedDocument(MergedManifestKind.MERGED);
            assertThat(mergedDocument).doesNotContain("extractNativeLibs");
        } finally {
            assertTrue(tmpFile.delete());
        }
    }

    @Test
    public void testExtractNativeLibs_injected_true() throws Exception {
        MockLog mockLog = new MockLog();
        String input =
                "<manifest\n"
                        + "        xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "        package=\"foo.bar\">\n"
                        + "    <application/>\n"
                        + "</manifest>";

        File tmpFile = TestUtils.inputAsFile("ManifestMerger2Test_extractNativeLibsFalse", input);
        assertTrue(tmpFile.exists());

        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    tmpFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .setOverride(
                                    ManifestSystemProperty.Application.EXTRACT_NATIVE_LIBS, "true")
                            .merge();
            assertEquals(MergingReport.Result.SUCCESS, mergingReport.getResult());
            String mergedDocument = mergingReport.getMergedDocument(MergedManifestKind.MERGED);
            assertThat(mergedDocument).contains("android:extractNativeLibs=\"true\"");
        } finally {
            assertTrue(tmpFile.delete());
        }
    }

    @Test
    public void testExtractNativeLibs_injected_false() throws Exception {
        MockLog mockLog = new MockLog();
        String input =
                "<manifest\n"
                        + "        xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "        package=\"foo.bar\">\n"
                        + "    <application/>\n"
                        + "</manifest>";

        File tmpFile = TestUtils.inputAsFile("ManifestMerger2Test_extractNativeLibsFalse", input);
        assertTrue(tmpFile.exists());

        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    tmpFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .setOverride(
                                    ManifestSystemProperty.Application.EXTRACT_NATIVE_LIBS, "false")
                            .merge();
            assertEquals(MergingReport.Result.SUCCESS, mergingReport.getResult());
            String mergedDocument = mergingReport.getMergedDocument(MergedManifestKind.MERGED);
            assertThat(mergedDocument).contains("android:extractNativeLibs=\"false\"");
        } finally {
            assertTrue(tmpFile.delete());
        }
    }

    @Test
    public void testExtractNativeLibs_notReplaced() throws Exception {
        MockLog mockLog = new MockLog();
        String input =
                "<manifest\n"
                        + "        xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "        package=\"foo.bar\">\n"
                        + "    <application android:extractNativeLibs=\"true\"/>\n"
                        + "</manifest>";

        File tmpFile = TestUtils.inputAsFile("ManifestMerger2Test_extractNativeLibsTrue", input);
        assertTrue(tmpFile.exists());

        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    tmpFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .setOverride(
                                    ManifestSystemProperty.Application.EXTRACT_NATIVE_LIBS, "false")
                            .merge();
            assertEquals(MergingReport.Result.WARNING, mergingReport.getResult());
            assertStringPresenceInLogRecords(
                    mergingReport, "android:extractNativeLibs should not be specified");
            String mergedDocument = mergingReport.getMergedDocument(MergedManifestKind.MERGED);
            assertThat(mergedDocument).contains("android:extractNativeLibs=\"true\"");
            assertThat(mergedDocument).doesNotContain("android:extractNativeLibs=\"false\"");
        } finally {
            assertTrue(tmpFile.delete());
        }
    }

    @Test
    public void testNavigationJson_mergedCorrectly() throws Exception {
        MockLog mockLog = new MockLog();
        String input =
                "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.app1\">\n"
                        + "    <application android:name=\"app1\">\n"
                        + "        <activity android:name=\".MainActivity\">\n"
                        + "            <nav-graph android:value=\"@navigation/nav1\" />\n"
                        + "        </activity>\n"
                        + "    </application>\n"
                        + "</manifest>";

        String navJson1 =
                "[\n"
                        + "  {\n"
                        + "    \"name\": \"nav1\",\n"
                        + "    \"navigationXmlIds\": [\"nav2\"],\n"
                        + "    \"deepLinks\": []\n"
                        + "  }\n"
                        + "]";
        String navJson2 =
                "[\n"
                        + "  {\n"
                        + "    \"name\": \"nav2\",\n"
                        + "    \"navigationXmlIds\": [],\n"
                        + "    \"deepLinks\": [\n"
                        + "      {\n"
                        + "        \"schemes\": [\n"
                        + "          \"http\",\n"
                        + "          \"https\"\n"
                        + "        ],\n"
                        + "        \"host\": \"www.example.com\",\n"
                        + "        \"port\": -1,\n"
                        + "        \"path\": \"/nav2_foo\",\n"
                        + "        \"sourceFilePosition\": {\n"
                        + "          \"mSourceFile\": {\n"
                        + "            \"mSourceFile\": {\n"
                        + "              \"path\": \"\"\n"
                        + "            },\n"
                        + "            \"mDescription\": \"nav1\"\n"
                        + "          },\n"
                        + "          \"mSourcePosition\": {\n"
                        + "            \"mStartLine\": 0,\n"
                        + "            \"mStartColumn\": 0,\n"
                        + "            \"mStartOffset\": 0,\n"
                        + "            \"mEndLine\": 0,\n"
                        + "            \"mEndColumn\": 0,\n"
                        + "            \"mEndOffset\": 0\n"
                        + "          }\n"
                        + "        },\n"
                        + "        \"isAutoVerify\": false,\n"
                        + "        \"action\": \"android.intent.action.VIEW\"\n"
                        + "      }\n"
                        + "    ]\n"
                        + "  }\n"
                        + "]";

        File tmpFile = TestUtils.inputAsFile("ManifestMerger2Test_navigationJson", input);
        File nav1 = TestUtils.inputAsFile("nav1.json", navJson1);
        File nav2 = TestUtils.inputAsFile("nav2.json", navJson2);
        assertTrue(tmpFile.exists());

        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    tmpFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .addNavigationJsons(Lists.newArrayList(nav1, nav2))
                            .merge();
            assertEquals(MergingReport.Result.SUCCESS, mergingReport.getResult());
            String mergedDocument = mergingReport.getMergedDocument(MergedManifestKind.MERGED);
            assertThat(mergedDocument).contains("/nav2_foo");
        } finally {
            assertTrue(tmpFile.delete());
            assertTrue(nav1.delete());
            assertTrue(nav2.delete());
        }
    }

    @Test
    public void testPackageElementMerge() throws Exception {
        MockLog mockLog = new MockLog();
        String appInput =
                "<manifest\n"
                        + "   xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.app1\">\n"
                        + "  <queries>\n"
                        + "    <package android:name=\"some.package\" />\n"
                        + "    <intent>\n"
                        + "      <action android:name=\"some.action\" />\n"
                        + "      <category android:name=\"some.category\" />\n"
                        + "      <data android:scheme=\"http\" android:host=\"google\" />\n"
                        + "    </intent>\n"
                        + "  </queries>\n"
                        + "</manifest>";

        File appFile = TestUtils.inputAsFile("testPackageElementMergeApp", appInput);
        assertTrue(appFile.exists());

        String libraryInput =
                "<manifest\n"
                        + "   xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.lib1\">\n"
                        + "  <queries>\n"
                        + "    <package android:name=\"some.other.package\" />\n"
                        + "    <intent>\n"
                        + "      <action android:name=\"some.otherAction\" />\n"
                        + "      <category android:name=\"some.otherCategory\" />\n"
                        + "      <data android:scheme=\"http\" android:host=\"google\" />\n"
                        + "    </intent>\n"
                        + "  </queries>\n"
                        + "</manifest>";
        File libFile = TestUtils.inputAsFile("testPackageElementMergeLib", libraryInput);

        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    appFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .addLibraryManifest(libFile)
                            .merge();
            assertThat(mergingReport.getResult()).isEqualTo(MergingReport.Result.SUCCESS);
            Document xmlDocument =
                    parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            NodeList packageList = xmlDocument.getElementsByTagName(SdkConstants.TAG_PACKAGE);
            assertThat(packageList.getLength()).isEqualTo(2);
            assertEquals(
                    "some.package",
                    packageList
                            .item(0)
                            .getAttributes()
                            .getNamedItem("android:name")
                            .getNodeValue());
            assertEquals(
                    "some.other.package",
                    packageList
                            .item(1)
                            .getAttributes()
                            .getNamedItem("android:name")
                            .getNodeValue());
        } finally {
            assertThat(appFile.delete()).named("appFile was deleted").isTrue();
            assertThat(libFile.delete()).named("libFile file was deleted").isTrue();
        }
    }

    @Test
    public void testRemoveNavGraphs() throws Exception {
        MockLog mockLog = new MockLog();
        String libInput =
                "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.lib1\">\n"
                        + "    <application android:name=\"lib1\">\n"
                        + "        <activity android:name=\".MainActivity\">\n"
                        + "            <nav-graph android:value=\"@navigation/nav1\" />\n"
                        + "        </activity>\n"
                        + "    </application>\n"
                        + "</manifest>";
        File libFile = TestUtils.inputAsFile("testRemoveNavGraphs", libInput);

        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(libFile, mockLog, ManifestMerger2.MergeType.LIBRARY)
                            .withFeatures(Feature.MAKE_AAPT_SAFE)
                            .merge();
            assertThat(mergingReport.getResult()).isEqualTo(MergingReport.Result.SUCCESS);
            // check that MERGED manifest has <nav-graph> but AAPT_SAFE manifest doesn't
            Document mergedDoc = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            NodeList mergedNavGraphs = mergedDoc.getElementsByTagName(SdkConstants.TAG_NAV_GRAPH);
            assertThat(mergedNavGraphs.getLength()).isEqualTo(1);
            Document aaptDoc = parse(mergingReport.getMergedDocument(MergedManifestKind.AAPT_SAFE));
            NodeList aaptNavGraphs = aaptDoc.getElementsByTagName(SdkConstants.TAG_NAV_GRAPH);
            assertThat(aaptNavGraphs.getLength()).isEqualTo(0);
        } finally {
            assertThat(libFile.delete()).named("libFile file was deleted").isTrue();
        }
    }

    @Test
    public void testFixNoDotPackage() throws Exception {
        MockLog mockLog = new MockLog();
        String libInput =
                "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"withoutdots\">\n"
                        + "    <application android:name=\"lib1\">\n"
                        + "        <activity android:name=\".MainActivity\">\n"
                        + "        </activity>\n"
                        + "    </application>\n"
                        + "</manifest>";
        File libFile = TestUtils.inputAsFile("testRemoveNavGraphs", libInput);

        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(libFile, mockLog, ManifestMerger2.MergeType.LIBRARY)
                            .withFeatures(Feature.MAKE_AAPT_SAFE)
                            .merge();
            assertThat(mergingReport.getResult()).isEqualTo(MergingReport.Result.SUCCESS);
            // check that MERGED manifest has the original package, but the aapt safe one has
            // the suffix appended
            Document mergedDoc = parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            Document aaptDoc = parse(mergingReport.getMergedDocument(MergedManifestKind.AAPT_SAFE));
            assertThat(mergedDoc.getDocumentElement().getAttribute("package"))
                    .isEqualTo("withoutdots");
            assertThat(aaptDoc.getDocumentElement().getAttribute("package"))
                    .isEqualTo("withoutdots.for.verification");
        } finally {
            assertThat(libFile.delete()).named("libFile file was deleted").isTrue();
        }
    }

    @Test
    public void testAAPTWillNotBeGeneratedIfNoChanges() throws Exception {
        MockLog mockLog = new MockLog();
        String libInput =
                "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.lib1\">\n"
                        + "    <application android:name=\"lib1\">\n"
                        + "        <activity android:name=\".MainActivity\"/>\n"
                        + "    </application>\n"
                        + "</manifest>";
        File libFile = TestUtils.inputAsFile("testRemoveNavGraphs", libInput);

        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(libFile, mockLog, ManifestMerger2.MergeType.LIBRARY)
                            .withFeatures(Feature.MAKE_AAPT_SAFE)
                            .merge();
            assertThat(mergingReport.getResult()).isEqualTo(MergingReport.Result.SUCCESS);
            assertNull(mergingReport.getMergedDocument(MergedManifestKind.AAPT_SAFE));
            assertTrue(mergingReport.isAaptSafeManifestUnchanged());
        } finally {
            assertThat(libFile.delete()).named("libFile file was deleted").isTrue();
        }
    }

    /**
     * Tests related to provider tag which can have different behaviors depending on its location in
     * the xml tree.
     *
     * <p>For provider in application xml element, an "android:name" attribute is used as the key
     * for merging. For provider in queries xml element, there are no key.
     */
    @Test
    public void testProviderTags() throws Exception {
        MockLog mockLog = new MockLog();
        String appInput =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    package=\"com.example.app1\">\n"
                        + "\n"
                        + "    <application android:label=\"@string/lib_name\">\n"
                        + "       <provider android:name=\"provider1\" "
                        + " android:authorities=\"content://com.example.app.provider/table1\"/>\n"
                        + "    </application>"
                        + "    <queries>\n"
                        + "      <intent>\n"
                        + "          <action android:name=\"android.intent.action.WHATEVER\" />\n"
                        + "      </intent>\n"
                        + "    </queries>"
                        + "\n"
                        + "</manifest>";

        File appFile = TestUtils.inputAsFile("providerTagsHandlingLibApp", appInput);
        assertTrue(appFile.exists());

        String lib1Input =
                ""
                        + "<manifest\n"
                        + "xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "package=\"com.example.lib1\">\n"
                        + "\n"
                        + "  <queries>\n"
                        + "      <provider android:authority=\"some.authority\" />\n"
                        + "      <intent>\n"
                        + "          <data android:mimeType=\"text/plain\" />\n"
                        + "      </intent>\n"
                        + "  </queries>\n"
                        + "\n"
                        + "</manifest>";
        File lib1File = TestUtils.inputAsFile("providerTagsHandlingLib", lib1Input);

        String lib2Input =
                ""
                        + "<manifest\n"
                        + "xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "package=\"com.example.lib2\">\n"
                        + "\n"
                        + "    <application>\n"
                        + "       <provider android:name=\"com.example.app1.provider1\" "
                        + " android:directBootAware=\"true\"/>\n"
                        + "    </application>"
                        + "\n"
                        + "</manifest>";
        File lib2File = TestUtils.inputAsFile("providerTagsHandlingLib", lib2Input);

        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    appFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .addLibraryManifest(lib1File)
                            .addLibraryManifest(lib2File)
                            .merge();
            assertThat(mergingReport.getResult()).isEqualTo(MergingReport.Result.SUCCESS);
            Document mergedDocument =
                    parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            NodeList mergedQueries = mergedDocument.getElementsByTagName(SdkConstants.TAG_QUERIES);
            assertThat(mergedQueries.getLength()).isEqualTo(1);
            NodeList queriesProviders =
                    ((Element) mergedQueries.item(0))
                            .getElementsByTagName(SdkConstants.TAG_PROVIDER);
            assertThat(queriesProviders.getLength()).isEqualTo(1);
            assertThat(
                            queriesProviders
                                    .item(0)
                                    .getAttributes()
                                    .getNamedItem("android:authority")
                                    .getNodeValue())
                    .isEqualTo("some.authority");

            // ensure intents are not merged since they are not equal.
            NodeList queriesIntents =
                    ((Element) mergedQueries.item(0)).getElementsByTagName(SdkConstants.TAG_INTENT);
            assertThat(queriesIntents.getLength()).isEqualTo(2);

            NodeList applications =
                    mergedDocument.getElementsByTagName(SdkConstants.TAG_APPLICATION);
            assertThat(applications.getLength()).isEqualTo(1);
            NodeList appProviders =
                    ((Element) applications.item(0))
                            .getElementsByTagName(SdkConstants.TAG_PROVIDER);
            assertThat(appProviders.getLength()).isEqualTo(1);
            Truth.assertThat(
                            appProviders
                                    .item(0)
                                    .getAttributes()
                                    .getNamedItem("android:name")
                                    .getNodeValue())
                    .isEqualTo("com.example.app1.provider1");
        } finally {
            assertThat(appFile.delete()).named("Overlay was deleted").isTrue();
            assertThat(lib1File.delete()).named("Lib1 file was deleted").isTrue();
            assertThat(lib2File.delete()).named("Lib1 file was deleted").isTrue();
        }
    }

    /** Test related to property tag. */
    @Test
    public void testPropertyTags() throws Exception {
        MockLog mockLog = new MockLog();
        String appInput =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    package=\"com.example.app1\">\n"
                        + "\n"
                        + "    <application android:label=\"@string/lib_name\">\n"
                        + "       <property android:name=\"appProperty\" />\n"
                        + "    </application>"
                        + "\n"
                        + "</manifest>";

        File appFile = TestUtils.inputAsFile("providerTagsHandlingLibApp", appInput);
        assertTrue(appFile.exists());

        String libInput =
                ""
                        + "<manifest\n"
                        + "xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "package=\"com.example.lib1\">\n"
                        + "\n"
                        + "    <application>\n"
                        + "       <property android:name=\"libProperty\" "
                        + " android:directBootAware=\"true\"/>\n"
                        + "    </application>"
                        + "\n"
                        + "</manifest>";
        File libFile = TestUtils.inputAsFile("providerTagsHandlingLib", libInput);

        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    appFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .addLibraryManifest(libFile)
                            .merge();
            assertThat(mergingReport.getResult()).isEqualTo(MergingReport.Result.SUCCESS);
            Document mergedDocument =
                    parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));

            NodeList applications =
                    mergedDocument.getElementsByTagName(SdkConstants.TAG_APPLICATION);
            assertThat(applications.getLength()).isEqualTo(1);
            NodeList appProperties =
                    ((Element) applications.item(0))
                            .getElementsByTagName(SdkConstants.TAG_PROPERTY);
            assertThat(appProperties.getLength()).isEqualTo(2);
            Truth.assertThat(
                            appProperties
                                    .item(0)
                                    .getAttributes()
                                    .getNamedItem("android:name")
                                    .getNodeValue())
                    .isEqualTo("com.example.app1.appProperty");
            Truth.assertThat(
                            appProperties
                                    .item(1)
                                    .getAttributes()
                                    .getNamedItem("android:name")
                                    .getNodeValue())
                    .isEqualTo("com.example.lib1.libProperty");
        } finally {
            assertThat(appFile.delete()).named("Overlay was deleted").isTrue();
        }
    }

    @Test
    public void testDifferentIntentMerging() throws Exception {
        String appInput =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    package=\"com.example.app1\">\n"
                        + "\n"
                        + "    <application android:label=\"@string/lib_name\">\n"
                        + "       <provider android:name=\"provider1\" "
                        + " android:authorities=\"content://com.example.app.provider/table1\"/>\n"
                        + "    </application>"
                        + "<queries>\n"
                        + "    <intent>\n"
                        + "        <action android:name=\"android.intent.action.SEND\" />\n"
                        + "        <data android:mimeType=\"image/jpeg\" />\n"
                        + "    </intent>\n"
                        + "</queries>\n"
                        + "\n"
                        + "</manifest>";

        String libInput =
                ""
                        + "<manifest\n"
                        + "xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "package=\"com.example.lib1\">\n"
                        + "\n"
                        + "<queries>\n"
                        + "    <intent>\n"
                        + "        <action android:name=\"android.intent.action.SEND\" />\n"
                        + "    </intent>\n"
                        + "</queries>\n"
                        + "\n"
                        + "</manifest>";
        testIntentMerging(appInput, libInput, 2);
    }

    @Test
    public void testSameIntentMerging() throws Exception {
        String appInput =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    package=\"com.example.app1\">\n"
                        + "\n"
                        + "    <application android:label=\"@string/lib_name\">\n"
                        + "       <provider android:name=\"provider1\" "
                        + " android:authorities=\"content://com.example.app.provider/table1\"/>\n"
                        + "    </application>"
                        + "<queries>\n"
                        + "    <intent>\n"
                        + "        <action android:name=\"android.intent.action.SEND\" />\n"
                        + "        <data android:mimeType=\"image/jpeg\" />\n"
                        + "    </intent>"
                        + "</queries>\n"
                        + "\n"
                        + "</manifest>";

        String libInput =
                ""
                        + "<manifest\n"
                        + "xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "package=\"com.example.lib1\">\n"
                        + "\n"
                        + "<queries>\n"
                        + "    <intent>\n"
                        + "        <action android:name=\"android.intent.action.SEND\" />\n"
                        + "        <data android:mimeType=\"image/jpeg\" />\n"
                        + "    </intent>"
                        + "</queries>\n"
                        + "\n"
                        + "</manifest>";
        testIntentMerging(appInput, libInput, 1);
    }

    @Test
    public void testIntentWithDifferentMimeTypeMerging() throws Exception {
        String appInput =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    package=\"com.example.app1\">\n"
                        + "\n"
                        + "    <application android:label=\"@string/lib_name\">\n"
                        + "       <provider android:name=\"provider1\" "
                        + " android:authorities=\"content://com.example.app.provider/table1\"/>\n"
                        + "    </application>"
                        + "<queries>\n"
                        + "    <intent>\n"
                        + "        <action android:name=\"android.intent.action.SEND\" />\n"
                        + "        <data android:mimeType=\"image/jpeg\" />\n"
                        + "    </intent>"
                        + "</queries>\n"
                        + "\n"
                        + "</manifest>";

        String libInput =
                ""
                        + "<manifest\n"
                        + "xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "package=\"com.example.lib1\">\n"
                        + "\n"
                        + "<queries>\n"
                        + "    <intent>\n"
                        + "        <action android:name=\"android.intent.action.SEND\" />\n"
                        + "        <data android:mimeType=\"image/png\" />\n"
                        + "    </intent>"
                        + "</queries>\n"
                        + "\n"
                        + "</manifest>";
        testIntentMerging(appInput, libInput, 2);
    }

    @Test
    public void testDifferentDataIntentMerging() throws Exception {
        String appInput =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    package=\"com.example.app1\">\n"
                        + "\n"
                        + "    <application android:label=\"@string/lib_name\">\n"
                        + "       <provider android:name=\"provider1\" "
                        + " android:authorities=\"content://com.example.app.provider/table1\"/>\n"
                        + "    </application>"
                        + "<queries>\n"
                        + "    <intent>\n"
                        + "        <action android:name=\"android.intent.action.SEND\" />\n"
                        + "        <data android:mimeType=\"image/jpeg\" />\n"
                        + "    </intent>"
                        + "</queries>\n"
                        + "\n"
                        + "</manifest>";

        String libInput =
                ""
                        + "<manifest\n"
                        + "xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "package=\"com.example.lib1\">\n"
                        + "\n"
                        + "<queries>\n"
                        + "    <intent>\n"
                        + "        <action android:name=\"android.intent.action.SEND\" />\n"
                        + "        <data android:scheme=\"content\" />\n"
                        + "    </intent>"
                        + "</queries>\n"
                        + "\n"
                        + "</manifest>";

        testIntentMerging(appInput, libInput, 2);
    }

    @Test
    public void testAndroidExportedAttributeWithIntentFilterInActivity() throws Exception {
        MockLog mockLog = new MockLog();
        String appInput =
                ""
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.myapplication\">\n"
                        + "    <uses-sdk\n"
                        + "        android:minSdkVersion=\"31\"\n"
                        + "        android:targetSdkVersion=\"31\" />"
                        + "    <application\n"
                        + "        android:label=\"@string/app_name\">\n"
                        + "        <activity\n"
                        + "            android:name=\".MainActivity\"\n"
                        + "            android:label=\"@string/app_name\"\n"
                        + "            android:theme=\"@style/Theme.MyApplication.NoActionBar\">\n"
                        + "            <intent-filter>\n"
                        + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                        + "\n"
                        + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>";
        File appFile = TestUtils.inputAsFile("appFile", appInput);
        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    appFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .merge();
            assertThat(mergingReport.getResult()).isEqualTo(MergingReport.Result.ERROR);
            assertNull(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            String loggingRecordsString = mergingReport.getLoggingRecords().toString();
            assertThat(loggingRecordsString)
                    .contains(
                            "android:exported needs to be explicitly specified for element <activity#com.example.myapplication.MainActivity>.");
            assertThat(loggingRecordsString).contains(".xml:7:9-16:20 Error");
        } finally {
            assertThat(appFile.delete()).named("appFile was deleted").isTrue();
        }
    }

    @Test
    public void testAndroidExportedAttributeWithIntentFilterInActivityAlias() throws Exception {
        MockLog mockLog = new MockLog();
        String appInput =
                ""
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.myapplication\">\n"
                        + "    <uses-sdk\n"
                        + "        android:minSdkVersion=\"31\"\n"
                        + "        android:targetSdkVersion=\"31\" />"
                        + "    <application\n"
                        + "        android:label=\"@string/app_name\">\n"
                        + "        <activity\n"
                        + "            android:name=\".MyActivity\"\n"
                        + "            android:label=\"@string/app_name\"\n"
                        + "            android:theme=\"@style/Theme.MyApplication.NoActionBar\">\n"
                        + "        </activity>\n"
                        + "        <activity-alias\n"
                        + "            android:name=\".MainActivity\"\n"
                        + "            android:targetActivity=\".MyActivity\">\n"
                        + "            <intent-filter>\n"
                        + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                        + "\n"
                        + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity-alias>\n"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>";
        File appFile = TestUtils.inputAsFile("appFile", appInput);
        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    appFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .merge();
            assertThat(mergingReport.getResult()).isEqualTo(MergingReport.Result.ERROR);
            assertNull(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            String loggingRecordsString = mergingReport.getLoggingRecords().toString();
            assertThat(loggingRecordsString)
                    .contains(
                            "android:exported needs to be explicitly specified for element <activity-alias#com.example.myapplication.MainActivity>.");
            assertThat(loggingRecordsString).contains(".xml:12:9-20:26 Error");
        } finally {
            assertThat(appFile.delete()).named("appFile was deleted").isTrue();
        }
    }

    @Test
    public void testCloneAndTransformUpdate() throws Exception {
        String appInput =
                ""
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.myapplication\">\n"
                        + "    <uses-sdk\n"
                        + "        android:minSdkVersion=\"31\"\n"
                        + "        android:targetSdkVersion=\"31\" />"
                        + "    <application\n"
                        + "        android:label=\"@string/app_name\">\n"
                        + "         <service android:description=\"string resource\"\n"
                        + "                  android:name=\".MainActivity\">\n"
                        + "                  <intent-filter>\n"
                        + "                        <action android:name=\"android.intent.action.MAIN\" />\n"
                        + "                        <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                        + "                  </intent-filter>\n"
                        + "         </service>\n"
                        + "    </application>\n"
                        + "</manifest>";
        Pair<Document, Boolean> output =
                DomMergeUtils.cloneAndTransform(
                        parse(appInput),
                        nodeToTransform -> {
                            if (nodeToTransform.getNodeName().equals("service")) {
                                ((Element) nodeToTransform).setAttribute("foo", "bar");
                                return true;
                            }
                            return false;
                        },
                        nodeToRemove -> nodeToRemove.getNodeName().equals("action"));
        assertTrue(output.getSecond()); // document updated
        String docString =
                XmlPrettyPrinter.prettyPrint(
                        output.getFirst(),
                        XmlFormatPreferences.defaults(),
                        XmlFormatStyle.get(output.getFirst().getDocumentElement()),
                        null,
                        false);
        assertThat(docString).contains("foo=\"bar\"");
        assertThat(docString).doesNotContain("<action");
    }

    @Test
    public void testCloneAndTransformUnchanged() throws Exception {
        String appInput =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.myapplication\" >\n"
                        + "\n"
                        + "</manifest>";
        Pair<Document, Boolean> output =
                DomMergeUtils.cloneAndTransform(
                        parse(appInput), nodeToTransform -> false, nodeToRemove -> false);
        assertFalse(output.getSecond()); // document unchanged
        String docString =
                XmlPrettyPrinter.prettyPrint(
                        output.getFirst(),
                        XmlFormatPreferences.defaults(),
                        XmlFormatStyle.get(output.getFirst().getDocumentElement()),
                        "\n",
                        false);
        assertThat(appInput).isEqualTo(docString);
    }

    @Test
    public void testAndroidExportedAttributeWithIntentFilterInService() throws Exception {
        MockLog mockLog = new MockLog();
        String appInput =
                ""
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.myapplication\">\n"
                        + "    <uses-sdk\n"
                        + "        android:minSdkVersion=\"31\"\n"
                        + "        android:targetSdkVersion=\"31\" />"
                        + "    <application\n"
                        + "        android:label=\"@string/app_name\">\n"
                        + "         <service android:description=\"string resource\"\n"
                        + "                  android:name=\".MainActivity\">\n"
                        + "                  <intent-filter>\n"
                        + "                        <action android:name=\"android.intent.action.MAIN\" />\n"
                        + "\n"
                        + "                        <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                        + "                  </intent-filter>\n"
                        + "         </service>\n"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>";
        File appFile = TestUtils.inputAsFile("appFile", appInput);
        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    appFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .merge();
            assertThat(mergingReport.getResult()).isEqualTo(MergingReport.Result.ERROR);
            assertNull(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            String loggingRecordsString = mergingReport.getLoggingRecords().toString();
            assertThat(loggingRecordsString)
                    .contains(
                            "android:exported needs to be explicitly specified for element <service#com.example.myapplication.MainActivity>.");
            assertThat(loggingRecordsString).contains(".xml:7:10-14:20 Error");
        } finally {
            assertThat(appFile.delete()).named("appFile was deleted").isTrue();
        }
    }

    @Test
    public void testAndroidExportedAttributeWithIntentFilterInReceiver() throws Exception {
        MockLog mockLog = new MockLog();
        String appInput =
                ""
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.myapplication\">\n"
                        + "    <uses-sdk\n"
                        + "        android:minSdkVersion=\"31\"\n"
                        + "        android:targetSdkVersion=\"31\" />"
                        + "    <application\n"
                        + "        android:label=\"@string/app_name\">\n"
                        + "         <receiver android:directBootAware=\"true\"\n"
                        + "                  android:name=\".MainActivity\">\n"
                        + "                  <intent-filter>\n"
                        + "                        <action android:name=\"android.intent.action.MAIN\" />\n"
                        + "\n"
                        + "                        <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                        + "                  </intent-filter>\n"
                        + "         </receiver>\n"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>";
        File appFile = TestUtils.inputAsFile("appFile", appInput);
        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    appFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .merge();
            assertThat(mergingReport.getResult()).isEqualTo(MergingReport.Result.ERROR);
            assertNull(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            String loggingRecordsString = mergingReport.getLoggingRecords().toString();
            assertThat(loggingRecordsString)
                    .contains(
                            "android:exported needs to be explicitly specified for element <receiver#com.example.myapplication.MainActivity>.");
            assertThat(loggingRecordsString).contains(".xml:7:10-14:21 Error");
        } finally {
            assertThat(appFile.delete()).named("appFile was deleted").isTrue();
        }
    }

    @Test
    public void testAndroidExportedAttributeWithIntentFilter2() throws Exception {
        MockLog mockLog = new MockLog();
        String appInput =
                ""
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                        + "    package=\"com.example.myapplication\">\n"
                        + "    <uses-sdk\n"
                        + "        android:minSdkVersion=\"16\"\n"
                        + "        android:targetSdkVersion=\"31\" />"
                        + "    <application\n"
                        + "        android:label=\"@string/app_name\">\n"
                        + "        <activity\n"
                        + "            android:name=\".MainActivity\"\n"
                        + "            android:label=\"@string/app_name\"\n"
                        + "            android:theme=\"@style/Theme.MyApplication.NoActionBar\"\n"
                        + "            android:exported=\"true\">\n"
                        + "            <intent-filter>\n"
                        + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                        + "\n"
                        + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "        <activity\n"
                        + "            android:name=\".MyActivity\"\n"
                        + "            android:label=\"@string/app_name\"\n"
                        + "            android:theme=\"@style/Theme.MyApplication.NoActionBar\">\n"
                        + "        </activity>\n"
                        + "        <activity-alias\n"
                        + "             android:exported=\"true\""
                        + "            android:name=\".OtherMainActivity\"\n"
                        + "            android:targetActivity=\".MyActivity\">\n"
                        + "            <intent-filter>\n"
                        + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                        + "\n"
                        + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity-alias>\n"
                        + "         <service android:description=\"string resource\"\n"
                        + "             android:exported=\"true\""
                        + "             android:name=\".MainActivity\">\n"
                        + "             <intent-filter>\n"
                        + "                 <action android:name=\"android.intent.action.MAIN\" />\n"
                        + "\n"
                        + "                 <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                        + "            </intent-filter>\n"
                        + "         </service>\n"
                        + "         <receiver android:directBootAware=\"true\"\n"
                        + "             android:name=\".MainActivity\">"
                        + "             <intent-filter tools:node=\"remove\">\n"
                        + "                 <action android:name=\"android.intent.action.MAIN\" />\n"
                        + "\n"
                        + "                 <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                        + "            </intent-filter>\n"
                        + "         </receiver>"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>";
        File appFile = TestUtils.inputAsFile("appFile", appInput);
        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    appFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .withFeatures(Feature.REMOVE_TOOLS_DECLARATIONS)
                            .merge();
            System.out.println(mergingReport.getLoggingRecords());
            assertThat(mergingReport.getResult()).isEqualTo(MergingReport.Result.WARNING);
        } finally {
            assertThat(appFile.delete()).named("appFile was deleted").isTrue();
        }
    }

    @Test
    public void testIntentFilterLessThanAndroidS() throws Exception {
        MockLog mockLog = new MockLog();
        String appInput =
                ""
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.myapplication\">\n"
                        + "    <uses-sdk\n"
                        + "        android:minSdkVersion=\"16\"\n"
                        + "        android:targetSdkVersion=\"29\" />"
                        + "    <application\n"
                        + "        android:label=\"@string/app_name\">\n"
                        + "        <activity\n"
                        + "            android:name=\".MainActivity\"\n"
                        + "            android:label=\"@string/app_name\"\n"
                        + "            android:theme=\"@style/Theme.MyApplication.NoActionBar\">\n"
                        + "            <intent-filter>\n"
                        + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                        + "\n"
                        + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "         <service android:description=\"string resource\"\n"
                        + "             android:name=\".MainActivity\">\n"
                        + "             <intent-filter>\n"
                        + "                 <action android:name=\"android.intent.action.MAIN\" />\n"
                        + "\n"
                        + "                 <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                        + "            </intent-filter>\n"
                        + "         </service>\n"
                        + "         <receiver android:directBootAware=\"true\"\n"
                        + "             android:name=\".MainActivity\">"
                        + "             <intent-filter>\n"
                        + "                 <action android:name=\"android.intent.action.MAIN\" />\n"
                        + "\n"
                        + "                 <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                        + "            </intent-filter>\n"
                        + "         </receiver>"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>";
        File appFile = TestUtils.inputAsFile("appFile", appInput);
        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    appFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .merge();
            assertThat(mergingReport.getResult()).isEqualTo(MergingReport.Result.SUCCESS);
        } finally {
            assertThat(appFile.delete()).named("appFile was deleted").isTrue();
        }
    }

    @Test
    public void testAndroidExportedAttributeNoIntentFilter() throws Exception {
        MockLog mockLog = new MockLog();
        String appInput =
                ""
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.myapplication\">\n"
                        + "    <uses-sdk\n"
                        + "        android:minSdkVersion=\"31\"\n"
                        + "        android:targetSdkVersion=\"31\" />"
                        + "    <application\n"
                        + "        android:label=\"@string/app_name\">\n"
                        + "        <activity\n"
                        + "            android:name=\".MainActivity\"\n"
                        + "            android:label=\"@string/app_name\"\n"
                        + "            android:theme=\"@style/Theme.MyApplication.NoActionBar\">\n"
                        + "        </activity>\n"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>";
        File appFile = TestUtils.inputAsFile("appFile", appInput);
        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    appFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .merge();
            assertThat(mergingReport.getResult()).isEqualTo(MergingReport.Result.SUCCESS);
            Document xmlDocument =
                    parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            NodeList activityNode = xmlDocument.getElementsByTagName(SdkConstants.TAG_ACTIVITY);
            NodeList intentFilter =
                    ((Element) activityNode.item(0))
                            .getElementsByTagName(SdkConstants.TAG_INTENT_FILTER);
            assertThat(intentFilter.getLength()).isEqualTo(0);
            assertTrue(
                    activityNode
                                    .item(0)
                                    .getAttributes()
                                    .getNamedItemNS(
                                            SdkConstants.ANDROID_URI, SdkConstants.ATTR_EXPORTED)
                            == null);
        } finally {
            assertThat(appFile.delete()).named("appFile was deleted").isTrue();
        }
    }

    @Test
    public void testAndroidExportedAttributeWithAPILessThan30() throws Exception {
        MockLog mockLog = new MockLog();
        String appInput =
                ""
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.myapplication\">\n"
                        + "    <uses-sdk\n"
                        + "        android:minSdkVersion=\"29\"\n"
                        + "        android:targetSdkVersion=\"29\" />"
                        + "    <application\n"
                        + "        android:label=\"@string/app_name\">\n"
                        + "        <activity\n"
                        + "            android:name=\".MainActivity\"\n"
                        + "            android:label=\"@string/app_name\"\n"
                        + "            android:theme=\"@style/Theme.MyApplication.NoActionBar\">\n"
                        + "            <intent-filter>\n"
                        + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                        + "\n"
                        + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                        + "            </intent-filter>\n"
                        + "        </activity>\n"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>";
        File appFile = TestUtils.inputAsFile("appFile", appInput);
        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    appFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .merge();
            assertThat(mergingReport.getResult()).isEqualTo(MergingReport.Result.SUCCESS);
            Document xmlDocument =
                    parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            NodeList activityNode = xmlDocument.getElementsByTagName(SdkConstants.TAG_ACTIVITY);
            NodeList intentFilter =
                    ((Element) activityNode.item(0))
                            .getElementsByTagName(SdkConstants.TAG_INTENT_FILTER);
            assertThat(intentFilter.getLength()).isEqualTo(1);
            assertTrue(
                    activityNode
                                    .item(0)
                                    .getAttributes()
                                    .getNamedItemNS(
                                            SdkConstants.ANDROID_URI, SdkConstants.ATTR_EXPORTED)
                            == null);
        } finally {
            assertThat(appFile.delete()).named("appFile was deleted").isTrue();
        }
    }

    private void testIntentMerging(String appInput, String libInput, int expectedIntentCount)
            throws Exception {
        MockLog mockLog = new MockLog();

        File appFile = TestUtils.inputAsFile("testIntentMergingApp", appInput);
        assertTrue(appFile.exists());

        File lib1File = TestUtils.inputAsFile("testIntentMergingLib", libInput);

        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    appFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .addLibraryManifest(lib1File)
                            .merge();
            assertThat(mergingReport.getResult()).isEqualTo(MergingReport.Result.SUCCESS);
            Document mergedDocument =
                    parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            NodeList mergedQueries = mergedDocument.getElementsByTagName(SdkConstants.TAG_QUERIES);
            assertThat(mergedQueries.getLength()).isEqualTo(1);

            NodeList queriesIntents =
                    ((Element) mergedQueries.item(0)).getElementsByTagName(SdkConstants.TAG_INTENT);
            assertThat(queriesIntents.getLength()).isEqualTo(expectedIntentCount);
        } finally {
            assertThat(appFile.delete()).named("Overlay was deleted").isTrue();
            assertThat(lib1File.delete()).named("Lib1 file was deleted").isTrue();
        }
    }

    @Test
    public void testMergingWithOverlayTags() throws Exception {
        String appInput =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.app1\">\n"
                        + "\n"
                        + "    <overlay\n"
                        + "        android:targetPackage=\"xxx.yyy\"\n"
                        + "        android:targetName=\"Foo\" />\n"
                        + "\n"
                        + "</manifest>";

        String libInput =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.lib1\">\n"
                        + "\n"
                        + "    <overlay\n"
                        + "        android:targetPackage=\"xxx.yyy\"\n"
                        + "        android:targetName=\"Bar\" />\n"
                        + "\n"
                        + "</manifest>";

        MockLog mockLog = new MockLog();

        File appFile = TestUtils.inputAsFile("testMergingWithOverlayTagsApp", appInput);
        assertTrue(appFile.exists());

        File libFile = TestUtils.inputAsFile("testMergingWithOverlayTagsLib", libInput);
        assertTrue(libFile.exists());

        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                            appFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .addLibraryManifest(libFile)
                            .merge();
            assertThat(mergingReport.getResult()).isEqualTo(MergingReport.Result.SUCCESS);
            Document mergedDocument =
                    parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            NodeList mergedOverlays = mergedDocument.getElementsByTagName("overlay");
            // We expect 2 overlay nodes in the merged manifest because the overlay nodes from the
            // app and lib should both be included in the merged manifest.
            assertThat(mergedOverlays.getLength()).isEqualTo(2);
        } finally {
            assertThat(appFile.delete()).named("appFile was deleted").isTrue();
            assertThat(libFile.delete()).named("libFile was deleted").isTrue();
        }
    }

    @Test
    public void testWrongManifestError() throws Exception {
        String manifestWithBug =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.app1\">\n"
                        + "    <uses-sdk android=\"31\" >\n" // uses-sdk tag is not closed
                        + "</manifest>";
        File appFile = TestUtils.inputAsFile("testWrongManifestError", manifestWithBug);
        assertTrue(appFile.exists());

        try {

            ManifestMerger2.newMerger(appFile, new MockLog(), ManifestMerger2.MergeType.APPLICATION)
                    .withFeatures(Feature.DISABLE_MINSDKLIBRARY_CHECK)
                    .merge();
            fail("Merge operation must fail with exception");
        } catch (ManifestMerger2.MergeFailureException e) {
            assertThat(e.getMessage()).startsWith("Error parsing");
        } finally {
            assertThat(appFile.delete()).named("appFile was deleted").isTrue();
        }
    }

    @Test
    public void testDisableMinSdkLibraryFlag() throws Exception {
        String appInput =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.app1\">\n"
                        + "    <uses-sdk android:minSdkVersion=\"31\" />\n"
                        + "</manifest>";

        String libInput =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.lib1\">\n"
                        + "    <uses-sdk android:minSdkVersion=\"32\" />\n"
                        + "</manifest>";

        MockLog mockLog = new MockLog();

        File appFile = TestUtils.inputAsFile("testDisableMinSdkLibraryFlagApp", appInput);
        assertTrue(appFile.exists());

        File libFile = TestUtils.inputAsFile("testDisableMinSdkLibraryFlagLib", libInput);
        assertTrue(libFile.exists());

        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    appFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .addLibraryManifest(libFile)
                            .withFeatures(Feature.DISABLE_MINSDKLIBRARY_CHECK)
                            .merge();
            assertThat(mergingReport.getResult()).isEqualTo(MergingReport.Result.SUCCESS);
        } finally {
            assertThat(appFile.delete()).named("appFile was deleted").isTrue();
            assertThat(libFile.delete()).named("libFile was deleted").isTrue();
        }
    }

    @Test
    public void testRemoveTargetSdkFromLibraryManifest() throws Exception {
        String libInput =
                "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.lib1\">\n"
                        + "    <uses-sdk android:targetSdkVersion=\"32\" />\n"
                        + "</manifest>";
        File libFile = TestUtils.inputAsFile("testDisableMinSdkLibraryFlagLib", libInput);
        assertTrue(libFile.exists());

        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    libFile, new MockLog(), ManifestMerger2.MergeType.LIBRARY)
                            .merge();
            assertThat(mergingReport.getResult()).isEqualTo(MergingReport.Result.SUCCESS);
            Document mergedDocument =
                    parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            assertEquals(
                    0,
                    mergedDocument
                            .getElementsByTagName(SdkConstants.TAG_USES_SDK)
                            .item(0)
                            .getAttributes()
                            .getLength());
        } finally {
            assertThat(libFile.delete()).named("libFile was deleted").isTrue();
        }
    }

    @Test
    public void testRemoveTargetSdkFromLibraryManifestWhenOverride() throws Exception {
        String libInput =
                "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.lib1\">\n"
                        + "    <uses-sdk/>\n"
                        + "</manifest>";

        File libFile = TestUtils.inputAsFile("testDisableMinSdkLibraryFlagLib", libInput);
        assertTrue(libFile.exists());
        try {
            ManifestMerger2.Invoker invoker =
                    ManifestMerger2.newMerger(
                            libFile, new MockLog(), ManifestMerger2.MergeType.LIBRARY);

            invoker.setOverride(ManifestSystemProperty.UsesSdk.TARGET_SDK_VERSION, "32");
            MergingReport mergingReport = invoker.merge();
            assertThat(mergingReport.getResult()).isEqualTo(MergingReport.Result.SUCCESS);
            Document mergedDocument =
                    parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            assertEquals(
                    0,
                    mergedDocument
                            .getElementsByTagName(SdkConstants.TAG_USES_SDK)
                            .item(0)
                            .getAttributes()
                            .getLength());
        } finally {
            assertThat(libFile.delete()).named("libFile was deleted").isTrue();
        }
    }

    @Test
    public void testSingleWordAppPackageNamesNotAllowed() throws Exception {
        MockLog mockLog = new MockLog();
        String input =
                "<manifest\n"
                        + "        xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "        package=\"foo\">\n"
                        + "    <application/>\n"
                        + "</manifest>";

        File tmpFile = TestUtils.inputAsFile(
                "ManifestMerger2Test_testSingleWordAppPackageNamesNotAllowed", input);
        assertTrue(tmpFile.exists());

        try {
            MergingReport mergingReport =
                    ManifestMerger2.newMerger(
                                    tmpFile, mockLog, ManifestMerger2.MergeType.APPLICATION)
                            .merge();
            assertEquals(MergingReport.Result.ERROR, mergingReport.getResult());
        } finally {
            assertTrue(tmpFile.delete());
        }
    }

    @Test
    public void testLocaleConfigIsAddedWhenRequested() throws Exception {
        String appInput =
                ""
                        + "<manifest\n"
                        + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                        + "    package=\"com.example.app1\">\n"
                        + "    <application/>\n"
                        + "</manifest>";

        File appFile = TestUtils.inputAsFile("testLocaleConfigIsAdded", appInput);
        assertTrue(appFile.exists());
        try {
            ManifestMerger2.Invoker invoker =
                    ManifestMerger2.newMerger(
                            appFile, new MockLog(), ManifestMerger2.MergeType.APPLICATION);

            invoker.setGeneratedLocaleConfigAttribute("_generated_res_locale_config");
            MergingReport mergingReport = invoker.merge();
            assertThat(mergingReport.getResult()).isEqualTo(MergingReport.Result.SUCCESS);
            Document mergedDocument =
                    parse(mergingReport.getMergedDocument(MergedManifestKind.MERGED));
            assertEquals(
                    "_generated_res_locale_config",
                    mergedDocument
                            .getElementsByTagName(SdkConstants.TAG_APPLICATION)
                            .item(0)
                            .getAttributes()
                            .getNamedItemNS(
                                    SdkConstants.ANDROID_URI, SdkConstants.ATTR_LOCALE_CONFIG)
                            .getNodeValue());
        } finally {
            assertThat(appFile.delete()).named("appFile was deleted").isTrue();
        }
    }

    public static void validateFeatureName(
            ManifestMerger2.Invoker invoker, String featureName, boolean isValid) throws Exception {
        try {
            invoker.setFeatureName(featureName);
        } catch (IllegalArgumentException e) {
            if (isValid) {
                fail("Unexpected exception throw " + e.getMessage());
            }
            assertTrue(e.getMessage().contains("FeatureName"));
            return;
        }
        if (!isValid) {
            fail("Expected Exception not thrown");
        }
    }

    public static Optional<Element> getElementByTypeAndKey(Document xmlDocument, String nodeType, String key) {
        NodeList elementsByTagName = xmlDocument.getElementsByTagName(nodeType);
        for (int i = 0; i < elementsByTagName.getLength(); i++) {
            Node item = elementsByTagName.item(i);
            Node name = item.getAttributes().getNamedItemNS(SdkConstants.ANDROID_URI, "name");
            if ((name == null && key == null) || (name != null && key.equals(name.getNodeValue()))) {
                return Optional.of((Element) item);
            }
        }
        return Optional.absent();
    }

    private static void assertStringPresenceInLogRecords(MergingReport mergingReport, String s) {
        for (MergingReport.Record record : mergingReport.getLoggingRecords()) {
            if (record.toString().contains(s)) {
                return;
            }
        }
        // failed, dump the records
        for (MergingReport.Record record : mergingReport.getLoggingRecords()) {
            Logger.getAnonymousLogger().info(record.toString());
        }
        fail("could not find " + s + " in logging records");
    }

    public static Document parse(String xml)
            throws IOException, SAXException, ParserConfigurationException {
        return XmlUtils.parseDocument(xml, true /* namespaceAware */);
    }
}
