/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.screenshot

import com.android.tools.render.compose.ImagePathOrMessage
import com.android.tools.render.compose.ScreenshotError
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import org.kxml2.io.KXmlSerializer
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

const val ACTUAL = "actual"
const val CLASSNAME = "classname"
const val COLON = ":"
const val DIFF = "diff"
const val TIME = "time"
const val ERROR = "error"
const val ERRORS = "errors"
const val FAILURE = "failure"
const val FAILURES = "failures"
const val FEATURE = "http://xmlpull.org/v1/doc/features.html#indent-output"
const val REFERENCE = "reference"
const val NAME = "name"
const val PERIOD = "."
const val PROPERTIES = "properties"
const val PROPERTY = "property"
const val SKIPPED = "skipped"
const val SUCCESS = "success"
const val TEST_RESULT_PB_FILE_NAME = "test-result.pb"
const val TEST_RESULT_XML_FILE_NAME = "TEST-results.xml"
const val TESTCASE = "testcase"
const val TESTS = "tests"
const val TESTSUITE = "testsuite"
const val VALUE = "value"
const val ZERO = "zero"

val NAMESPACE = null

/*
  ScreenshotError object may contain multiple errors, this method returns one of them to show to the user.
*/
fun getFirstError(screenshotError: ScreenshotError?): String {
    if (screenshotError != null) {
        if (screenshotError.message.isNotEmpty()) return screenshotError.message
        if (screenshotError.stackTrace.isNotEmpty()) return screenshotError.stackTrace
        for (problem in screenshotError.problems) {
            if (!problem.stackTrace.isNullOrEmpty()) return problem.stackTrace!!
        }
        for (brokenClass in screenshotError.brokenClasses) {
            if (brokenClass.stackTrace.isNotEmpty()) return "Rendering failed with issue. Broken class ${brokenClass.className}: ${brokenClass.stackTrace}"
        }
        if (screenshotError.missingClasses.isNotEmpty()) return "Rendering failed with issue: Missing class(es): ${screenshotError.missingClasses.joinToString(", ")}"
    }
    return "Rendering failed"
}

fun saveResults(
    previewResults: List<PreviewResult>,
    filePath: String,
    xmlProperties: List<String>? = null,
) {
    val stream = createOutputResultStream(filePath)
    val serializer = KXmlSerializer()
    serializer.setOutput(stream, Charsets.UTF_8.name())
    serializer.startDocument(Charsets.UTF_8.name(), null)
    serializer.setFeature(
        FEATURE, true
    )
    printTestResults(serializer, previewResults, xmlProperties)
    serializer.endDocument()
}

/*
 * Save test results as {@link TestSuiteResult}s
 *
 * This is temporary for reading test results to the test matrix in Studio.
 */
fun saveTestSuiteResults(
    testSuiteResults: List<TestSuiteResult>,
    filePath: String,
) {
    val pbFile = File(filePath)
    pbFile.outputStream().use {
        for (testSuiteResult in testSuiteResults) {
            testSuiteResult.writeTo(it)
        }
    }
}

@Throws(IOException::class)
private fun printTestResults(
    serializer: KXmlSerializer,
    previewResults: List<PreviewResult>,
    xmlProperties: List<String>?
) {
    serializer.startTag(NAMESPACE, TESTSUITE)
    val runName = previewResults.first().previewName
    val name = getTestNameWithoutSuffix(runName).substringBeforeLast(PERIOD)
    if (name.isNotEmpty()) {
        serializer.attribute(NAMESPACE, NAME, name)
    }
    serializer.attribute(
        NAMESPACE, TESTS, previewResults.size.toString()
    )
    serializer.attribute(
        NAMESPACE, FAILURES,
        previewResults.filter { it.responseCode == 1 }.size.toString()
    )
    serializer.attribute(NAMESPACE, ERRORS, previewResults.filter { it.responseCode == 2 }.size.toString())
    serializer.attribute(NAMESPACE, SKIPPED, ZERO)
    serializer.startTag(NAMESPACE, PROPERTIES)
    for ((key, value) in getPropertiesAttributes(xmlProperties).entries) {
        serializer.startTag(NAMESPACE, PROPERTY)
        serializer.attribute(NAMESPACE, NAME, key)
        serializer.attribute(NAMESPACE, VALUE, value)
        serializer.endTag(NAMESPACE, PROPERTY)
    }
    serializer.endTag(NAMESPACE, PROPERTIES)
    for (previewResult in previewResults) {
        printTest(serializer, previewResult)
    }
    serializer.endTag(NAMESPACE, TESTSUITE)
}

@Throws(IOException::class)
private fun printTest(serializer: KXmlSerializer, result: PreviewResult) {
    serializer.startTag(NAMESPACE, TESTCASE)
    val testWithoutSuffix = getTestNameWithoutSuffix(result.previewName)

    val lastPeriod = testWithoutSuffix.lastIndexOf(".")
    serializer.attribute(NAMESPACE, NAME, result.previewName.substring(lastPeriod+ 1))
    serializer.attribute(
        NAMESPACE, CLASSNAME, testWithoutSuffix.substringBeforeLast(
            PERIOD
        ))
    serializer.attribute(NAMESPACE, TIME, result.durationInSeconds.toString())
    when (result.responseCode) {
        0 -> printImages(serializer, SUCCESS, result.message!!, result)
        1 -> printImages(serializer, FAILURE, result.message!!, result)
        2 -> printImages(serializer, ERROR, result.message!!, result)
    }

    serializer.endTag(NAMESPACE, TESTCASE)
}


private fun printImages(
    serializer: KXmlSerializer,
    tag: String,
    stack: String,
    result: PreviewResult
) {
    serializer.startTag(NAMESPACE, tag)
    serializer.text(stack)
    serializer.endTag(NAMESPACE, tag)
    serializer.startTag(NAMESPACE, PROPERTIES)
    serializer.startTag(NAMESPACE, PROPERTY)
    serializer.attribute(NAMESPACE, NAME, REFERENCE)
    when (val ref = result.referenceImage) {
        is ImagePathOrMessage.ImagePath -> {
            serializer.attribute(NAMESPACE, VALUE, ref.path)
        }

        is ImagePathOrMessage.ErrorMessage -> {
            serializer.attribute(NAMESPACE, VALUE, ref.message)
        }
    }
    serializer.endTag(NAMESPACE, PROPERTY)
    serializer.startTag(NAMESPACE, PROPERTY)
    serializer.attribute(NAMESPACE, NAME, ACTUAL)
    when (val actual = result.actualImage) {
        is ImagePathOrMessage.ImagePath -> {
            serializer.attribute(NAMESPACE, VALUE, actual.path)
        }

        is ImagePathOrMessage.ErrorMessage -> {
            serializer.attribute(NAMESPACE, VALUE, actual.message)
        }
    }
    serializer.endTag(NAMESPACE, PROPERTY)

    serializer.startTag(NAMESPACE, PROPERTY)
    serializer.attribute(NAMESPACE, NAME, DIFF)
    when (val diff = result.diffImage) {
        is ImagePathOrMessage.ImagePath -> {
            serializer.attribute(NAMESPACE, VALUE, diff.path)
        }

        is ImagePathOrMessage.ErrorMessage -> {
            serializer.attribute(NAMESPACE, VALUE, diff.message)
        }
    }
    serializer.endTag(NAMESPACE, PROPERTY)

    serializer.endTag(NAMESPACE, PROPERTIES)
}

private fun getPropertiesAttributes(xmlProperties: List<String>?): Map<String, String> {
    if (xmlProperties == null)
        return mapOf()
    val propertyMap = mutableMapOf<String, String>()
    for (p in xmlProperties) {
        val pair = p.split(COLON)
        propertyMap[pair[0]] = pair[1]
    }
    return propertyMap
}

/**
 * Creates the output stream to use for test results. Exposed for mocking.
 */
@Throws(IOException::class)
private fun createOutputResultStream(reportFilePath: String): OutputStream {
    val reportFile = File(reportFilePath)
    return BufferedOutputStream(FileOutputStream(reportFile))
}

private fun getTestNameWithoutSuffix(testName: String): String {
    return testName.substringBefore("_")
}

