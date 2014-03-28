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

import static org.mockito.Mockito.when;

import com.android.SdkConstants;
import com.android.utils.ILogger;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import junit.framework.TestCase;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Tests for {@link com.android.manifmerger.XmlDocument}
 */
public class XmlDocumentTest extends TestCase {

    @Mock ILogger mLogger;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
    }

    public void testMergeableElementsIdentification()
            throws ParserConfigurationException, SAXException, IOException {
        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" />\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(
                        getClass(), "testMergeableElementsIdentification()"), input);
        ImmutableList<XmlElement> mergeableElements = xmlDocument.getRootNode().getMergeableElements();
        assertEquals(2, mergeableElements.size());
    }

    public void testNamespaceEnabledElements()
            throws ParserConfigurationException, SAXException, IOException {
        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <android:application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "    <android:activity android:name=\"activityOne\" />\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(
                        getClass(), "testMergeableElementsIdentification()"), input);
        ImmutableList<XmlElement> mergeableElements = xmlDocument.getRootNode().getMergeableElements();
        assertEquals(2, mergeableElements.size());
        assertEquals(ManifestModel.NodeTypes.APPLICATION, mergeableElements.get(0).getType());
        assertEquals(ManifestModel.NodeTypes.ACTIVITY, mergeableElements.get(1).getType());
    }

    public void testMultipleNamespaceEnabledElements()
            throws ParserConfigurationException, SAXException, IOException {
        String input = ""
                + "<android:manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    xmlns:acme=\"http://acme.org/schemas\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <android:application android:label=\"@string/lib_name\" \n"
                + "         tools:node=\"replace\" />\n"
                + "    <acme:custom-tag android:label=\"@string/lib_name\" />\n"
                + "    <acme:application acme:label=\"@string/lib_name\" />\n"
                + "\n"
                + "</android:manifest>";

        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(
                        getClass(), "testMergeableElementsIdentification()"), input);
        ImmutableList<XmlElement> mergeableElements = xmlDocument.getRootNode().getMergeableElements();
        assertEquals(3, mergeableElements.size());
        assertEquals(ManifestModel.NodeTypes.APPLICATION, mergeableElements.get(0).getType());
        assertEquals(ManifestModel.NodeTypes.CUSTOM, mergeableElements.get(1).getType());
        assertEquals(ManifestModel.NodeTypes.CUSTOM, mergeableElements.get(2).getType());

    }

    public void testGetXmlNodeByTypeAndKey()
            throws ParserConfigurationException, SAXException, IOException {
        String input = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" />\n"
                + "\n"
                + "</manifest>";

        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testGetXmlNodeByTypeAndKey()"), input);
        assertTrue(xmlDocument.getRootNode().getNodeByTypeAndKey(
                ManifestModel.NodeTypes.ACTIVITY, "com.example.lib3.activityOne").isPresent());
        assertFalse(xmlDocument.getRootNode().getNodeByTypeAndKey(
                ManifestModel.NodeTypes.ACTIVITY, "noName").isPresent());
    }

    public void testSimpleMerge()
            throws ParserConfigurationException, SAXException, IOException {
        String main = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "</manifest>";
        String library = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" />\n"
                + "\n"
                + "</manifest>";

        XmlDocument mainDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testSimpleMerge()"), main);
        XmlDocument libraryDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testSimpleMerge()"), library);
        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(mLogger);
        Optional<XmlDocument> mergedDocument =
                mainDocument.merge(libraryDocument, mergingReportBuilder);

        assertTrue(mergedDocument.isPresent());
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        mergedDocument.get().write(byteArrayOutputStream);
        Logger.getAnonymousLogger().info(byteArrayOutputStream.toString());
        assertTrue(mergedDocument.get().getRootNode().getNodeByTypeAndKey(
                ManifestModel.NodeTypes.APPLICATION, null).isPresent());
        Optional<XmlElement> activityOne = mergedDocument.get()
                .getRootNode().getNodeByTypeAndKey(ManifestModel.NodeTypes.ACTIVITY,
                        "com.example.lib3.activityOne");
        assertTrue(activityOne.isPresent());
    }

    public void testDiff1()
            throws Exception {
        String main = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "</manifest>";
        String library = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" />\n"
                + "\n"
                + "</manifest>";

        XmlDocument mainDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testDiff1()"), main);
        XmlDocument libraryDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testDiff1()"), library);
        assertTrue(mainDocument.compareTo(libraryDocument).isPresent());
    }

    public void testDiff2()
            throws Exception {
        String main = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "    <activity android:name=\"activityOne\" />\n"
                + "\n"
                + "\n"
                + "</manifest>";
        String library = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" />\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "</manifest>";

        XmlDocument mainDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testDiff2()"), main);
        XmlDocument libraryDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testDiff2()"), library);
        assertFalse(mainDocument.compareTo(libraryDocument).isPresent());
    }

    public void testDiff3()
            throws Exception {
        String main = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "    <!-- some comment that should be ignored -->\n"
                + "    <activity android:name=\"activityOne\" />\n"
                + "\n"
                + "\n"
                + "</manifest>";
        String library = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <!-- some comment that should also be ignored -->\n"
                + "    <activity android:name=\"activityOne\" />\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "</manifest>";

        XmlDocument mainDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testDiff3()"), main);
        XmlDocument libraryDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testDiff3()"), library);
        assertFalse(mainDocument.compareTo(libraryDocument).isPresent());
    }

    public void testWriting() throws ParserConfigurationException, SAXException, IOException {
        String input = ""
                + "<manifest"
                + " xmlns:x=\"http://schemas.android.com/apk/res/android\""
                + " xmlns:y=\"http://schemas.android.com/apk/res/android/tools\""
                + " package=\"com.example.lib3\">\n"
                + "\n"
                + "    <application x:label=\"@string/lib_name\" y:node=\"replace\"/>\n"
                + "\n"
                + "</manifest>\n";

        XmlDocument xmlDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "testWriting()"), input);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        xmlDocument.write(byteArrayOutputStream);
        assertEquals(input, byteArrayOutputStream.toString());
    }

    public void testCustomElements()
            throws ParserConfigurationException, SAXException, IOException {
        String main = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <fantasy android:name=\"fantasyOne\" \n"
                + "         no-ns-attribute=\"no-ns\" >\n"
                + "    </fantasy>\n"
                + "    <application android:label=\"@string/lib_name\" />\n"
                + "\n"
                + "</manifest>";
        String library = ""
                + "<manifest\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:acme=\"http://acme.org/schemas\"\n"
                + "    package=\"com.example.lib3\">\n"
                + "\n"
                + "    <activity android:name=\"activityOne\" />\n"
                + "    <fantasy android:name=\"fantasyTwo\" \n"
                + "         no-ns-attribute=\"no-ns\" >\n"
                + "    </fantasy>\n"
                + "    <acme:another acme:name=\"anotherOne\" \n"
                + "         acme:ns-attribute=\"ns-value\" >\n"
                + "        <some-child acme:child-attr=\"foo\" /> \n"
                + "    </acme:another>\n"
                + "\n"
                + "</manifest>";

        XmlDocument mainDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "main"), main);
        XmlDocument libraryDocument = TestUtils.xmlDocumentFromString(
                new TestUtils.TestSourceLocation(getClass(), "library"), library);
        MergingReport.Builder mergingReportBuilder = new MergingReport.Builder(mLogger);
        Optional<XmlDocument> mergedDocument =
                mainDocument.merge(libraryDocument, mergingReportBuilder);

        assertTrue(mergedDocument.isPresent());
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        mergedDocument.get().write(byteArrayOutputStream);
        Logger.getAnonymousLogger().info(byteArrayOutputStream.toString());
        XmlElement rootNode = mergedDocument.get().getRootNode();
        assertTrue(rootNode.getNodeByTypeAndKey(
                ManifestModel.NodeTypes.APPLICATION, null).isPresent());
        Optional<XmlElement> activityOne = rootNode
                .getNodeByTypeAndKey(ManifestModel.NodeTypes.ACTIVITY,
                        "com.example.lib3.activityOne");
        assertTrue(activityOne.isPresent());

        boolean foundFantasyOne = false;
        boolean foundFantasyTwo = false;
        boolean foundAnother = false;
        NodeList childNodes = rootNode.getXml().getChildNodes();
        for (int i =0; i < childNodes.getLength(); i++) {
            Node item = childNodes.item(i);
            if (item.getNodeName().equals("fantasy")) {
                String name = ((Element) item).getAttributeNS(SdkConstants.ANDROID_URI, "name");
                if (name.equals("fantasyOne"))
                    foundFantasyOne = true;
                if (name.equals("fantasyTwo"))
                    foundFantasyTwo = true;
            }
            if (item.getNodeName().equals("acme:another")) {
                foundAnother = true;
            }
        }
        assertTrue(foundAnother);
        assertTrue(foundFantasyOne);
        assertTrue(foundFantasyTwo);
    }

}
