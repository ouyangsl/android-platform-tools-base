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
package com.android.compose.screenshot.report

import com.android.tools.render.compose.ImagePathOrMessage
import com.google.common.io.Closeables
import org.gradle.api.GradleException
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.ParseException
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathFactory

/**
 * Custom test reporter based on Gradle's DefaultTestReport
 */
class TestReport(private val resultDir: File, private val reportDir: File) {

    private val htmlRenderer: HtmlReportRenderer = HtmlReportRenderer()

    init {
        htmlRenderer.requireResource(javaClass.getResource("report.js")!!)
        htmlRenderer.requireResource(javaClass.getResource("base-style.css")!!)
        htmlRenderer.requireResource(javaClass.getResource("style.css")!!)
    }

    fun generateScreenshotTestReport(): CompositeTestResults {
        val model: AllTestResults = loadModel()
        generateFilesForScreenshotTest(model)
        return model
    }

    private fun loadModel(): AllTestResults {
        val model = AllTestResults()
        if (resultDir.exists()) {
            val files = resultDir.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.getName().startsWith("TEST-") && file.getName().endsWith(".xml")) {
                        mergeFromFile(file, model)
                    }
                }
            }
        }
        return model
    }

    private fun mergeFromFile(
        file: File,
        model: AllTestResults
    ) {
        var inputStream: InputStream? = null
        try {
            inputStream = FileInputStream(file)
            val document: Document = try {
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                    InputSource(inputStream)
                )
            } finally {
                inputStream.close()
            }
            var projectName: String? = null
            var flavorName: String? = null
            val propertiesList = document.getElementsByTagName("properties")
            for (i in 0 until propertiesList.length) {
                val properties = propertiesList.item(i) as Element
                val xPath = XPathFactory.newInstance().newXPath()
                projectName = xPath.evaluate("property[@name='project']/@value", properties)
                flavorName = xPath.evaluate("property[@name='flavor']/@value", properties)
            }
            val testCases = document.getElementsByTagName("testcase")
            for (i in 0 until testCases.length) {
                val testCase = testCases.item(i) as Element
                val className = testCase.getAttribute("classname")
                val testName = testCase.getAttribute("name")
                val timeString = testCase.getAttribute("time")
                var duration =
                    if (timeString.isNotBlank()) parse(timeString) else BigDecimal.valueOf(0)
                duration = duration.multiply(BigDecimal.valueOf(1000))
                val failures = testCase.getElementsByTagName("failure")
                val errors = testCase.getElementsByTagName("error")

                // block to parse screenshot test images/texts
                var ssImages: ScreenshotTestImages? = null
                val imagePropertyList = testCase.getElementsByTagName("properties")
                var referenceImagePathOrMessage: ImagePathOrMessage?
                var actualImagePathOrMessage: ImagePathOrMessage?
                var diffImagePathOrMessage: ImagePathOrMessage?
                for (j in 0 until imagePropertyList.length) {
                    val image = imagePropertyList.item(j) as Element
                    val xPath = XPathFactory.newInstance().newXPath()
                    val ref = xPath.evaluate("property[@name='reference']/@value", image)
                    val actual = xPath.evaluate("property[@name='actual']/@value", image)
                    val diff = xPath.evaluate("property[@name='diff']/@value", image)
                    referenceImagePathOrMessage = if (isImage(ref)) {
                        ImagePathOrMessage.ImagePath(ref)
                    } else {
                        ImagePathOrMessage.ErrorMessage(ref)
                    }

                    actualImagePathOrMessage = if (isImage(actual)) {
                        ImagePathOrMessage.ImagePath(actual)
                    } else {
                        ImagePathOrMessage.ErrorMessage(actual)
                    }

                    diffImagePathOrMessage = if (isImage(diff)) {
                        ImagePathOrMessage.ImagePath(diff)
                    } else {
                        ImagePathOrMessage.ErrorMessage(diff)
                    }

                    ssImages = ScreenshotTestImages(
                            referenceImagePathOrMessage,
                            actualImagePathOrMessage,
                            diffImagePathOrMessage
                        )
                }
                val testResult: TestResult = model.addTest(
                    className, testName, duration.toLong(),
                    projectName!!, flavorName!!, ssImages
                )
                for (j in 0 until failures.length) {
                    val failure = failures.item(j) as Element
                    testResult.addFailure(
                        failure.getAttribute("message"), failure.textContent,
                        projectName, flavorName
                    )
                }
                for (j in 0 until errors.length) {
                    val error = errors.item(j) as Element
                    testResult.addError(error.textContent, projectName, flavorName)
                }
                if (testCase.getElementsByTagName("skipped").length > 0) {
                    testResult.ignored(projectName, flavorName)
                }
            }
            val ignoredTestCases = document.getElementsByTagName("ignored-testcase")
            for (i in 0 until ignoredTestCases.length) {
                val testCase = ignoredTestCases.item(i) as Element
                val className = testCase.getAttribute("classname")
                val testName = testCase.getAttribute("name")
                model.addTest(className, testName, 0, projectName!!, flavorName!!, null)
                    .ignored(projectName, flavorName)
            }
            val suiteClassName = document.documentElement.getAttribute("name")
            if (suiteClassName.isNotBlank()) {
                model.addTestClass(suiteClassName)
                // TODO handle tool failures
            }
        } catch (e: Exception) {
            throw GradleException(String.format("Could not load test results from '%s'.", file), e)
        } finally {
            try {
                Closeables.close(inputStream, true /* swallowIOException */)
            } catch (e: IOException) {
                // cannot happen
            }
        }
    }

    private fun isImage(path: String?): Boolean {
        return path != null && path.endsWith(".png")
    }

    private fun generateFilesForScreenshotTest(
        model: AllTestResults,
    ) {
        try {
            generatePage(
                model,
                OverviewPageRenderer(),
                File(reportDir, "index.html")
            )
            for (packageResults in model.getPackages()) {
                generatePage(
                    packageResults,
                    PackagePageRenderer(),
                    File(reportDir, packageResults.getFilename() + ".html")
                )
                for (classResults in packageResults.getClasses()) {
                    generatePage(
                        classResults!!,
                        ScreenshotClassPageRenderer(),
                        File(reportDir, classResults.getFilename() + ".html")
                    )
                }
            }
        } catch (e: Exception) {
            throw GradleException(
                String.format(
                    "Could not generate test report to '%s'.",
                    reportDir
                ), e
            )
        }
    }

    @Throws(Exception::class)
    private fun <T : CompositeTestResults> generatePage(
        model: T, renderer: PageRenderer<T>,
        outputFile: File
    ) {
        htmlRenderer.renderer(renderer).writeTo(model, outputFile)
    }

    /**
     * Regardless of the default locale, comma ('.') is used as decimal separator
     *
     * @param source
     * @return
     * @throws java.text.ParseException
     */
    @Throws(ParseException::class)
    fun parse(source: String?): BigDecimal {
        val symbols = DecimalFormatSymbols()
        symbols.setDecimalSeparator('.')
        val format = DecimalFormat("#.#", symbols)
        format.isParseBigDecimal = true
        return format.parse(source) as BigDecimal
    }
}
