/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.lint.detector.api

import com.android.SdkConstants
import com.android.SdkConstants.ANDROIDX_APPCOMPAT_LIB_ARTIFACT
import com.android.SdkConstants.ANDROIDX_LEANBACK_ARTIFACT
import com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT
import com.android.support.AndroidxNameUtils
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.model.LintModelAndroidLibrary
import com.android.tools.lint.model.LintModelDependency
import com.android.tools.lint.model.LintModelJavaLibrary
import com.android.tools.lint.model.LintModelMavenName
import com.android.utils.XmlUtils
import com.google.common.collect.Lists
import java.io.File
import java.io.IOException

/** Lint project wrapping a library. */
open class LintModelModuleLibraryProject(
  client: LintClient,
  dir: File,
  referenceDir: File,
  val dependency: LintModelDependency?,
) : Project(client, dir, referenceDir) {

  init {
    reportIssues = false
    mergeManifests = true
    library = true
    directLibraries = mutableListOf()
  }

  fun setExternalLibrary(external: Boolean) {
    externalLibrary = external
  }

  fun setMavenCoordinates(mc: LintModelMavenName) {
    mavenCoordinates = mc
  }

  fun addDirectLibrary(project: Project) {
    directLibraries.add(project)
  }

  override fun initialize() {
    // Deliberately not calling super; that code is for ADT compatibility
  }

  override fun isLibrary(): Boolean = true

  override fun getManifestFiles(): List<File> = emptyList()

  override fun getProguardFiles(): List<File> = emptyList()

  override fun getResourceFolders(): List<File> = emptyList()

  override fun getAssetFolders(): List<File> = emptyList()

  override fun getJavaSourceFolders(): List<File> = emptyList()

  override fun getGeneratedSourceFolders(): List<File> = emptyList()

  override fun getTestSourceFolders(): List<File> = emptyList()

  override fun getJavaClassFolders(): List<File> = emptyList()

  override fun getJavaLibraries(includeProvided: Boolean): List<File> = emptyList()
}

/** Lint project wrapping a Java library (jar) */
open class LintModelModuleJavaLibraryProject(
  client: LintClient,
  dir: File,
  referenceDir: File,
  dependency: LintModelDependency?,
  private val javaLibrary: LintModelJavaLibrary,
) : LintModelModuleLibraryProject(client, dir, referenceDir, dependency) {

  override fun getJavaLibraries(includeProvided: Boolean): List<File> {
    if (!includeProvided && javaLibrary.provided) {
      return emptyList()
    }
    if (javaLibraries == null) {
      javaLibraries = Lists.newArrayList()
      javaLibraries.addAll(javaLibrary.jarFiles)
    }
    return javaLibraries
  }

  override fun getPartialResultsDir(): java.io.File? {
    return javaLibrary.partialResultsDir ?: super.getPartialResultsDir()
  }
}

/** Lint project wrapping an Android library (AAR) */
open class LintModelModuleAndroidLibraryProject(
  client: LintClient,
  dir: File,
  referenceDir: File,
  dependency: LintModelDependency?,
  private val androidLibrary: LintModelAndroidLibrary,
) : LintModelModuleLibraryProject(client, dir, referenceDir, dependency) {
  init {
    androidLibrary.manifest.let { manifest ->
      if (manifest.exists()) {
        try {
          val xml = manifest.readText()
          val document = XmlUtils.parseDocumentSilently(xml, true)
          document?.let { readManifest(it) }
        } catch (e: IOException) {
          client.log(e, "Could not read manifest %1\$s", manifest)
        }
      }
    }
  }

  override fun getBuildLibraryModel(): LintModelAndroidLibrary = androidLibrary

  override fun getManifestFiles(): List<File> {
    if (manifestFiles == null) {
      val manifest = androidLibrary.manifest
      manifestFiles =
        if (manifest.exists()) {
          listOf(manifest)
        } else {
          emptyList()
        }
    }
    return manifestFiles
  }

  override fun getProguardFiles(): List<File> {
    if (proguardFiles == null) {
      val proguardRules = androidLibrary.proguardRules
      proguardFiles =
        if (proguardRules.exists()) {
          listOf(proguardRules)
        } else {
          emptyList()
        }
    }
    return proguardFiles
  }

  override fun getResourceFolders(): List<File> {
    if (resourceFolders == null) {
      val folder = androidLibrary.resFolder
      resourceFolders =
        if (folder.exists()) {
          listOf(folder)
        } else {
          emptyList()
        }
    }
    return resourceFolders
  }

  override fun getAssetFolders(): List<File> {
    if (assetFolders == null) {
      val folder = androidLibrary.assetsFolder
      assetFolders =
        if (folder.exists()) {
          listOf(folder)
        } else {
          emptyList()
        }
    }
    return assetFolders
  }

  override fun getJavaLibraries(includeProvided: Boolean): List<File> {
    if (!includeProvided && androidLibrary.provided) {
      return emptyList()
    }

    return javaLibraries
      ?: run {
        val list = ArrayList<File>(androidLibrary.jarFiles.size)
        for (file in androidLibrary.jarFiles) {
          if (file.exists()) list.add(file)
        }

        javaLibraries = list
        list
      }
  }

  fun LintModelDependency.hasDependency(name: String): Boolean {
    if (artifactName == name) {
      return true
    }

    for (dependency in dependencies) {
      if (dependency.hasDependency(name)) {
        return true
      }
    }

    return false
  }

  override fun dependsOn(artifact: String): Boolean? {
    @Suppress("MoveVariableDeclarationIntoWhen") // used in super call as well
    val id = AndroidxNameUtils.getCoordinateMapping(artifact)
    return when (id) {
      ANDROIDX_APPCOMPAT_LIB_ARTIFACT -> {
        if (appCompat == null) {
          appCompat =
            dependency?.hasDependency(ANDROIDX_APPCOMPAT_LIB_ARTIFACT) == true ||
              dependency?.hasDependency(APPCOMPAT_LIB_ARTIFACT) == true
        }
        appCompat
      }
      ANDROIDX_LEANBACK_ARTIFACT -> {
        if (leanback == null) {
          leanback =
            dependency?.hasDependency(ANDROIDX_LEANBACK_ARTIFACT) == true ||
              dependency?.hasDependency(SdkConstants.LEANBACK_V17_ARTIFACT) == true
        }
        leanback
      }
      else -> if (dependency?.hasDependency(artifact) == true) true else super.dependsOn(id)
    }
  }

  override fun getPartialResultsDir(): java.io.File? {
    return androidLibrary.partialResultsDir ?: super.getPartialResultsDir()
  }
}
