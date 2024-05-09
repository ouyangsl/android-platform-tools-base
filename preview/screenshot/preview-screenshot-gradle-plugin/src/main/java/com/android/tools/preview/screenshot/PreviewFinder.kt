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

package com.android.tools.preview.screenshot

import com.android.tools.preview.multipreview.BaseAnnotationRepresentation
import com.android.tools.preview.multipreview.MethodRepresentation
import com.android.tools.preview.multipreview.MultipreviewSettings
import com.android.tools.preview.multipreview.ParameterRepresentation
import com.android.tools.preview.multipreview.buildMultipreview
import com.android.tools.render.compose.ComposeRendering
import com.android.tools.render.compose.ComposeScreenshot
import com.android.tools.render.compose.readComposeScreenshotsJson
import com.android.tools.render.compose.writeComposeRenderingToJson
import com.android.tools.render.compose.writeComposeScreenshotsToJson
import java.io.File
import java.io.File.separatorChar
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.objectweb.asm.Type
import java.util.jar.JarFile

fun configureInput (
    classPath: List<String>,
    projectClassPath: List<String>,
    fontsPath: String?,
    layoutlibPath: String,
    outputFolder: String,
    metaDataFolder: String,
    namespace: String,
    resourceApkPath: String,
    cliToolArgumentsFile: File,
    previewsFile: File,
    resultsFilePath: String
) {
    if (!File(outputFolder).exists()) {
        Files.createDirectories(Path.of(outputFolder))
    }
    val previews = readComposeScreenshotsJson(previewsFile.reader())
    val composeRendering = ComposeRendering(
        fontsPath,
        layoutlibPath,
        outputFolder,
        metaDataFolder,
        classPath,
        projectClassPath,
        namespace,
        resourceApkPath,
        previews,
        resultsFilePath
    )
    writeComposeRenderingToJson(cliToolArgumentsFile.writer(), composeRendering)
}

fun findPreviewsAndSerialize(classPath: List<String>, outputFile: Path, testDirectories: List<File>, testJars: List<File>) {
    val settings = MultipreviewSettings(
        "androidx.compose.ui.tooling.preview.Preview",
        "androidx.compose.ui.tooling.preview.PreviewParameter"
    )
    val multipreview = buildMultipreview(settings, classPath) {
        val className = getClassName(it)
        classExistsIn(className, testDirectories, testJars)
    }
    val previews =  multipreview.methods.flatMap { method ->
        multipreview.getAnnotations(method).map { baseAnnotation ->
            method to baseAnnotation
        }
    }
    serializePreviews(previews, outputFile)
}

// TODO: move this check to MethodFilter interface b/330334806
fun classExistsIn(className: String, dirs: List<File>, jars: List<File>): Boolean {
    val classFilePath = className.replace('.', '/') + ".class"
    for (dir in dirs) {
        val potentialClassFile = File(dir, classFilePath)
        if (potentialClassFile.exists()) {
            return true
        }
    }
    for (jar in jars) {
        JarFile(jar).use {
            if (it.getJarEntry(classFilePath) != null) {
                return true
            }
        }
    }
    return false
}

private fun getClassName(fqcn: String): String {
    if (fqcn.contains("$")) {
        return fqcn.substring(0, fqcn.indexOf('$'))
    }
    return fqcn.substring(0, fqcn.lastIndexOf("."))
}

private fun serializePreviews(
    previews: List<Pair<MethodRepresentation, BaseAnnotationRepresentation>>,
    outputFile: Path
) {
    val fileWriter = Files.newBufferedWriter(outputFile)
    val composeScreenshots = previews.map { (method, annotation) ->
        ComposeScreenshot(
            method.methodFqn,
            convertListMap(method.parameters),
            convertMap(annotation.parameters),
            calcImageName(method, annotation)
        )
    }
    writeComposeScreenshotsToJson(fileWriter, composeScreenshots.sortedBy { it.imageName })
}

private fun convertListMap(parameters: List<ParameterRepresentation>): List<Map<String, String>> {
    return parameters.map { convertMap(it.annotationParameters) }
}

private fun convertMap(map: Map<String, Any?>): Map<String, String> =
    map.map { (key, value) ->
        key to (if (key == "provider") (value as Type).className else value.toString())
    }.toMap().toSortedMap()


private fun calcHexString(digest: ByteArray): String {
    val hexString = digest.joinToString("") { eachByte -> "%02x".format(eachByte) }
    return if (hexString.length >= 8) {
        hexString.substring(0,8)
    } else {
        hexString
    }
}

private fun calcImageName(method: MethodRepresentation, annotation: BaseAnnotationRepresentation): String {
    val digest = MessageDigest.getInstance("SHA-1")
    annotation.parameters.keys.sorted().forEach {
        digest.update(it.toByteArray(StandardCharsets.UTF_8))
        digest.update(annotation.parameters[it].toString().toByteArray(StandardCharsets.UTF_8))
    }
    val previewSettingsHash = calcHexString(digest.digest())
    method.parameters.forEach { param ->
        param.annotationParameters.keys.sorted().forEach {
            digest.update(it.toByteArray(StandardCharsets.UTF_8))
            digest.update(param.annotationParameters[it].toString().toByteArray(StandardCharsets.UTF_8))
        }
    }
    val methodParamHash = calcHexString(digest.digest())
    return "${method.methodFqn}_${previewSettingsHash}_$methodParamHash"
}
