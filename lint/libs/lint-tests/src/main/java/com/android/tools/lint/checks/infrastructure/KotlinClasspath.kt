/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.lint.checks.infrastructure

import com.intellij.openapi.application.PathManager
import java.io.File
import java.net.URI
import java.util.jar.JarFile

fun findKotlinStdlibPath(): List<File> =
  findFromRuntimeClassPath(::isKotlinStdLib).ifEmpty {
    // kotlin-stdlib might be in another jar, so use that.
    PathManager.getJarForClass(KotlinVersion::class.java)?.let { listOf(it.toFile()) }
      ?: error(
        "Did not find kotlin-stdlib-jdk8 in classpath: ${System.getProperty("java.class.path")}"
      )
  }

fun findFromRuntimeClassPath(accept: (File) -> Boolean): List<File> {
  val classPath: String = System.getProperty("java.class.path")
  val paths = mutableListOf<File>()
  for (path in classPath.split(File.pathSeparatorChar)) {
    val file = File(path)
    if (accept(file)) {
      paths.add(file.absoluteFile)
    }
  }
  // Handle running from the IDE in jar-manifest mode.
  if (paths.isEmpty()) {
    for (jar in classPath.split(File.pathSeparatorChar)) {
      try {
        val jarFile = File(jar)
        JarFile(jarFile).use {
          for (path in it.manifest.mainAttributes.getValue("Class-Path").split(" ")) {
            val file = File(URI(path).path)
            if (accept(file)) {
              if (!file.isAbsolute) {
                paths.add(File(jarFile.parentFile, file.path))
              } else {
                paths.add(file)
              }
            }
          }
        }
      } catch (e: Exception) {
        System.err.println("Could not load jar $jar: $e")
      }
    }
  }
  return paths
}

private fun isKotlinStdLib(file: File): Boolean {
  val name = file.name
  return name.startsWith("kotlin-stdlib") || name.startsWith("kotlin-reflect")
}
