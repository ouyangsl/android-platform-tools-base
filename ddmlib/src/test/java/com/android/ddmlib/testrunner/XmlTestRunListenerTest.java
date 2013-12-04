/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.ddmlib.testrunner;

import com.android.ddmlib.testrunner.ITestRunListener.TestFailure;

import junit.framework.TestCase;

import org.xml.sax.InputSource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

/**
 * Unit tests for {@link XmlTestRunListener}.
 */
public class XmlTestRunListenerTest extends TestCase {

    private XmlTestRunListener mResultReporter;
    private ByteArrayOutputStream mOutputStream;
    private File mReportDir;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mOutputStream = new ByteArrayOutputStream();
        mResultReporter = new XmlTestRunListener() {
            @Override
            OutputStream createOutputResultStream(File reportDir) throws IOException {
                return mOutputStream;
            }

            @Override
            String getTimestamp() {
                return "ignore";
            }
        };
        // TODO: use mock file dir instead
        mReportDir = createTmpDir();
        mResultReporter.setReportDir(mReportDir);
    }

    private File createTmpDir() throws IOException {
        // create a temp file with unique name, then make it a directory
        File tmpDir = File.createTempFile("foo", "dir");
        tmpDir.delete();
        if (!tmpDir.mkdirs()) {
            throw new IOException("unable to create directory");
        }
        return tmpDir;
    }

    /**
     * Recursively delete given file and all its contents
     */
    private static void recursiveDelete(File rootDir) {
        if (rootDir.isDirectory()) {
            File[] childFiles = rootDir.listFiles();
            if (childFiles != null) {
                for (File child : childFiles) {
                    recursiveDelete(child);
                }
            }
        }
        rootDir.delete();
    }

    @Override
    protected void tearDown() throws Exception {
        if (mReportDir != null) {
            recursiveDelete(mReportDir);
        }
        super.tearDown();
    }

    /**
     * A simple test to ensure expected output is generated for test run with no tests.
     */
    public void testEmptyGeneration() {
        final String expectedOutput = "<?xml version='1.0' encoding='UTF-8' ?>" +
            "<testsuite name=\"test\" tests=\"0\" failures=\"0\" errors=\"0\" time=\"#TIMEVALUE#\" " +
            "timestamp=\"ignore\" hostname=\"localhost\"> " +
            "<properties />" +
            "</testsuite>";
        mResultReporter.testRunStarted("test", 1);
        mResultReporter.testRunEnded(1, Collections.<String, String> emptyMap());

        // because the timestamp is impossible to hardcode, look for the actual timestamp and
        // replace it in the expected string.
        String output = getOutput();
        String time = getTime(output);
        assertNotNull(time);

        String expectedTimedOutput = expectedOutput.replaceFirst("#TIMEVALUE#", time);
        assertEquals(expectedTimedOutput, output);
    }

    /**
     * A simple test to ensure expected output is generated for test run with performance test.
     */
    public void testPerformanceMetricGeneration() {

        Map<String, String> runMetrics = new TreeMap<String, String>();
        runMetrics.put("execution_time", "3000");
        runMetrics.put("java_pss", "1000");
        runMetrics.put("java_private_dirty", "2000");
        runMetrics.put("java_shared_dirty", "3000");
        runMetrics.put("native_pss", "1000");
        runMetrics.put("native_private_dirty", "2000");
        runMetrics.put("native_shared_dirty", "3000");
        runMetrics.put("native_pss", "1000");
        runMetrics.put("native_private_dirty", "2000");
        runMetrics.put("native_shared_dirty", "3000");

        final String expectedOutput = "<?xml version='1.0' encoding='UTF-8' ?>" +
                "<testsuite name=\"test\" tests=\"0\" failures=\"0\" errors=\"0\" time=\"#TIMEVALUE#\" " +
                "timestamp=\"ignore\" hostname=\"localhost\"> " +
                "<properties /> " +
                "<runMetrics execution_time=\"3000\" java_private_dirty=\"2000\" java_pss=\"1000\" " +
                "java_shared_dirty=\"3000\" native_private_dirty=\"2000\" native_pss=\"1000\" " +
                "native_shared_dirty=\"3000\" />" +
                "</testsuite>";
        mResultReporter.testRunStarted("test", 0);
        mResultReporter.testRunEnded(1, runMetrics);

        // because the timestamp is impossible to hardcode, look for the actual timestamp and
        // replace it in the expected string.
        String output = getOutput();
        String time = getTime(output);
        assertNotNull(time);

        String expectedTimedOutput = expectedOutput.replaceFirst("#TIMEVALUE#", time);
        assertEquals(expectedTimedOutput, output);
    }

    /**
     * A simple test to ensure expected output is generated for test run with a single passed test.
     */
    public void testSinglePass() {
        Map<String, String> emptyMap = Collections.emptyMap();
        final TestIdentifier testId = new TestIdentifier("FooTest", "testFoo");
        mResultReporter.testRunStarted("run", 1);
        mResultReporter.testStarted(testId);
        mResultReporter.testEnded(testId, emptyMap);
        mResultReporter.testRunEnded(3, emptyMap);
        String output =  getOutput();
        // TODO: consider doing xml based compare
        assertTrue(output.contains("tests=\"1\" failures=\"0\" errors=\"0\""));
        final String testCaseTag = String.format("<testcase name=\"%s\" classname=\"%s\"",
                testId.getTestName(), testId.getClassName());
        assertTrue(output.contains(testCaseTag));
    }

    /**
     * A simple test to ensure expected output is generated for test run with a single failed test.
     */
    public void testSingleFail() {
        Map<String, String> emptyMap = Collections.emptyMap();
        final TestIdentifier testId = new TestIdentifier("FooTest", "testFoo");
        final String trace = "this is a trace";
        mResultReporter.testRunStarted("run", 1);
        mResultReporter.testStarted(testId);
        mResultReporter.testFailed(TestFailure.FAILURE, testId, trace);
        mResultReporter.testEnded(testId, emptyMap);
        mResultReporter.testRunEnded(3, emptyMap);
        String output =  getOutput();
        // TODO: consider doing xml based compare
        assertTrue(output.contains("tests=\"1\" failures=\"1\" errors=\"0\""));
        final String testCaseTag = String.format("<testcase name=\"%s\" classname=\"%s\"",
                testId.getTestName(), testId.getClassName());
        assertTrue(output.contains(testCaseTag));
        final String failureTag = String.format("<failure>%s</failure>", trace);
        assertTrue(output.contains(failureTag));
    }

    /**
     * Gets the output produced, stripping it of extraneous whitespace characters.
     */
    private String getOutput() {
        String output = mOutputStream.toString();
        // ignore newlines and tabs whitespace
        output = output.replaceAll("[\\r\\n\\t]", "");
        // replace two ws chars with one
        return output.replaceAll("  ", " ");
    }

    /**
     * Returns the value if the time attribute from the given XML content
     *
     * Actual XPATH: /testsuite/@time
     *
     * @param xml XML content.
     * @return
     */
    private String getTime(String xml) {
        XPath xpath = XPathFactory.newInstance().newXPath();

        try {
            return xpath.evaluate("/testsuite/@time", new InputSource(new StringReader(xml)));
        } catch (XPathExpressionException e) {
            // won't happen.
        }

        return null;
    }
}
