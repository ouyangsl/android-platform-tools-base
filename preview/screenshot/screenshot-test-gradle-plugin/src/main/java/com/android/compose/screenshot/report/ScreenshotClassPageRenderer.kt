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

import java.io.IOException

internal class ScreenshotClassPageRenderer: PageRenderer<ClassTestResults>() {
    private val imagePanelRenderer = ImagePanelRenderer()

    override fun renderBreadcrumbs(htmlWriter: SimpleHtmlWriter) {
        htmlWriter.startElement("div")
            .attribute("class", "breadcrumbs")
            .startElement("a")
            .attribute("href", "index.html")
            .characters("all")
            .endElement()
            .characters(" > ")
            .startElement("a")
            .attribute(
                "href",
                String.format("%s.html", results.getPackageResults().getFilename())
            )
            .characters(results.getPackageResults().name)
            .endElement()
            .characters(String.format(" > %s", results.simpleName))
            .endElement()
    }

    override fun renderFailures(htmlWriter: SimpleHtmlWriter) {
        for (test in results.failures) {
            val testName = test.name
            htmlWriter.startElement("div")
                .attribute("class", "test")
                .startElement("a")
                .attribute("name", test.id.toString())
                .characters("")
                .endElement() //browsers don't understand <a name="..."/>
                .startElement("h3")
                .attribute("class", test.statusClass)
                .characters(testName)
                .endElement()
            if (test.screenshotImages != null) {
                imagePanelRenderer.render(test.screenshotImages!!, htmlWriter)
            }
            htmlWriter.endElement()
        }
    }

    override fun renderErrors(htmlWriter: SimpleHtmlWriter) {
        for (test in results.errors) {
            val testName = test.name
            htmlWriter.startElement("div")
                .attribute("class", "test")
                .startElement("a")
                .attribute("name", test.id.toString())
                .characters("")
                .endElement() //browsers don't understand <a name="..."/>
                .startElement("h3")
                .attribute("class", test.statusClass)
                .characters(testName)
                .endElement()
            if (test.screenshotImages != null) {
                imagePanelRenderer.render(test.screenshotImages!!, htmlWriter)
            }
            htmlWriter.endElement()
        }
    }

    fun renderTests(htmlWriter: SimpleHtmlWriter?) {
        // show test images even for a successful test so that users can view what images were saved or see diff if it was below threshold
        for (test in results.results) {
            htmlWriter!!.startElement("div")
                .attribute("class", "test")
                .startElement("a")
                .attribute("name", test.id.toString())
                .characters("")
                .endElement() //browsers don't understand <a name="..."/>
                .startElement("h3")
                .attribute("class", test.statusClass)
                .characters(test.name)
                .endElement()
            if (test.screenshotImages != null) {
                imagePanelRenderer.render(test.screenshotImages!!, htmlWriter)
            }
            htmlWriter.endElement()
        }
    }

    override fun registerTabs() {
        addErrorTab()
        addFailuresTab()
        addTab("Tests", object : ErroringAction<SimpleHtmlWriter>() {
            @Throws(IOException::class)
            override fun doExecute(objectToExecute: SimpleHtmlWriter) {
                renderTests(objectToExecute)
            }
        })
        //TODO: add variant tabs
    }
}
