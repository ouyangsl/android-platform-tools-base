/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.render.compose

import com.android.testutils.ImageDiffUtil
import com.android.testutils.TestUtils
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.io.path.absolutePathString
import kotlin.io.path.readLines
import java.nio.file.Paths

class PerfMainTest {
    private val tmpFolder = TemporaryFolder()
    private val gradleProject = GradleProjectRule(
        tmpFolder,
        "tools/base/standalone-render/compose-cli/testData/compose-application",
        "tools/external/gradle/gradle-8.2-bin.zip"
    )

    @JvmField
    @Rule
    val chain: RuleChain = RuleChain.outerRule(tmpFolder).around(gradleProject)

    private fun createSettingsFile(outputFolder: File, resultsFile: File, metaDataFolder: File, screenshots: List<ComposeScreenshot>): File {
        gradleProject.executeGradleTask(":app:assembleDebug")
        gradleProject.executeGradleTask(":app:bundleDebugClassesToCompileJar")
        gradleProject.executeGradleTask(":app:debugExtractClasspath", "--init-script", "initscript.gradle")

        val apk = gradleProject.projectRoot.resolve("app/build/outputs/apk/debug/app-debug.apk")
        val classPath = gradleProject.projectRoot.resolve("deps.txt").readLines()

        val composeRendering = ComposeRendering(
            TestUtils.getSdk().absolutePathString(),
            TestUtils.resolveWorkspacePath("prebuilts/studio/layoutlib").absolutePathString(),
            outputFolder.absolutePath,
            metaDataFolder.absolutePath,
            classPath,
            emptyList(),
            "com.example.composeapplication",
            apk.absolutePathString(),
            screenshots,
            resultsFile.absolutePath
        )

        val jsonSettings = tmpFolder.newFile()
        writeComposeRenderingToJson(jsonSettings.bufferedWriter(), composeRendering)
        return jsonSettings
    }

    @Test
    fun testSingleSmall() {
        val screenshots = listOf(
            ComposeScreenshot(
                "com.example.composeapplication.PreviewsKt.PreviewSmall",
                emptyList(),
                emptyMap(),
                "com.example.composeapplication.PreviewsKt.PreviewSmall_screenshot"
            )
        )
        commonTest(
            screenshots,
            "compose_cli_render_time_1_small",
            "compose_cli_render_memory_1_small",
            "small"
        )
    }

    @Test
    fun test10Small() {
        val screenshots = (0..9).map {
            ComposeScreenshot(
                "com.example.composeapplication.PreviewsKt.PreviewSmall",
                emptyList(),
                emptyMap(),
                "com.example.composeapplication.PreviewsKt.PreviewSmall_screenshot${it}"
            )
        }
        commonTest(
            screenshots,
            "compose_cli_render_time_10_small",
            "compose_cli_render_memory_10_small",
            "small"
        )
    }

    @Test
    fun test40Small() {
        val screenshots = (0..39).map {
            ComposeScreenshot(
                "com.example.composeapplication.PreviewsKt.PreviewSmall",
                emptyList(),
                emptyMap(),
                "com.example.composeapplication.PreviewsKt.PreviewSmall_screenshot${it}"
            )
        }
        commonTest(
            screenshots,
            "compose_cli_render_time_40_small",
            "compose_cli_render_memory_40_small",
            "small"
        )
    }

    @Test
    fun testSingleLarge() {
        val screenshots = listOf(
            ComposeScreenshot(
                "com.example.composeapplication.PreviewsKt.PreviewLarge",
                emptyList(),
                emptyMap(),
                "com.example.composeapplication.PreviewsKt.PreviewLarge_screenshot"
            )
        )
        commonTest(
            screenshots,
            "compose_cli_render_time_1_large",
            "compose_cli_render_memory_1_large",
            "large"
        )
    }

    @Test
    fun test10Large() {
        val screenshots = (0..9).map {
            ComposeScreenshot(
                "com.example.composeapplication.PreviewsKt.PreviewLarge",
                emptyList(),
                emptyMap(),
                "com.example.composeapplication.PreviewsKt.PreviewLarge_screenshot${it}"
            )
        }
        commonTest(
            screenshots,
            "compose_cli_render_time_10_large",
            "compose_cli_render_memory_10_large",
            "large"
        )
    }

    @Test
    fun test40Large() {
        val screenshots = (0..39).map {
            ComposeScreenshot(
                "com.example.composeapplication.PreviewsKt.PreviewLarge",
                emptyList(),
                emptyMap(),
                "com.example.composeapplication.PreviewsKt.PreviewLarge_screenshot${it}"
            )
        }
        commonTest(
            screenshots,
            "compose_cli_render_time_40_large",
            "compose_cli_render_memory_40_large",
            "large"
        )
    }

    private fun commonTest(
        screenshots: List<ComposeScreenshot>,
        timeMetricName: String,
        memoryMetricName: String,
        goldenName: String,
    ) {
        val outputFolder = tmpFolder.newFolder()
        val metaDatafolder = tmpFolder.newFolder()
        val resultsFile = tmpFolder.newFile("results.json")
        val jsonSettings = createSettingsFile(outputFolder, resultsFile, metaDatafolder, screenshots)

        computeAndRecordMetric(timeMetricName, memoryMetricName) {
            val metric = ComposeRenderingMetric()
            metric.beforeTest()
            runComposeCliRender(jsonSettings)
            metric.afterTest()
            val result = readComposeRenderingResultJson(resultsFile.bufferedReader())
            assertNull(result.globalError)
            result.screenshotResults.forEach {
                assertNull(it.error)
            }
            screenshots.forEach { screenshot ->
                val imageName = "${screenshot.previewId.substringAfterLast(".")}_0.png"
                val relativeImagePath = (screenshot.methodFQN.substringBeforeLast(".").replace(".", "/")) + "/" + imageName
                val imageFile = Paths.get(outputFolder.absolutePath, relativeImagePath).toFile()
                val img = ImageIO.read(imageFile)
                ImageDiffUtil.assertImageSimilar(
                    TestUtils.resolveWorkspacePathUnchecked("tools/base/standalone-render/compose-cli/testData/goldens/$goldenName.png"),
                    img
                )
            }
            metric
        }

        // Stop the daemon otherwise it could keep the lock on the temporary folder
        gradleProject.executeGradleTask("--stop")
    }
}

/** We want to filter out the error that we are aware of and that does not affect rendering. */
private val ALLOWED_ERRORS = listOf(
    "WARNING: A terminally deprecated method in java.lang.System has been called",
    "WARNING: System::setSecurityManager has been called by com.android.tools.rendering.security.RenderSecurityManager",
    "WARNING: Please consider reporting this to the maintainers of com.android.tools.rendering.security.RenderSecurityManager",
    "WARNING: System::setSecurityManager will be removed in a future release",
    "Tracing Skia with Perfetto is not supported in this environment (host build?)",
)

private fun runComposeCliRender(settingsFile: File): String {
    val javaHome = System.getProperty("java.home")
    val layoutlibJar = TestUtils.resolveWorkspacePath("prebuilts/studio/layoutlib/data/layoutlib-mvn.jar")
    val composeCliRenderFolder = TestUtils.resolveWorkspacePath("tools/base/standalone-render/compose-cli")
    val command = listOf("$javaHome/bin/java", "-Dlayoutlib.thread.profile.timeoutms=10000", "-Djava.security.manager=allow", "-cp", "compose-preview-renderer.jar:${layoutlibJar.absolutePathString()}", "com.android.tools.render.compose.MainKt", settingsFile.absolutePath)
    val procBuilder = ProcessBuilder(command)
        .directory(composeCliRenderFolder.toFile())
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
    // We have to specify JAVA_HOME
    procBuilder.environment()["JAVA_HOME"] = javaHome
    val proc = procBuilder.start()
    proc.waitFor(5, TimeUnit.MINUTES)
    val error = proc
        .errorStream
        .bufferedReader()
        .readLines()
        .filter { line -> ALLOWED_ERRORS.none { line.startsWith(it) } }
        .joinToString("\n")
    if (error.isNotEmpty()) {
        val commandStr = command.joinToString(" ")
        throw AssertionError("Error while rendering Compose previews \"$commandStr\":\n$error")
    }
    return proc.inputStream.bufferedReader().readText()
}
