/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.integration.application

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.tasks.AarMetadataReader
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.FusedLibraryMergeArtifactTask
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Tests for [FusedLibraryMergeArtifactTask] */
internal class FusedLibraryMergeArtifactTaskTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @JvmField
    @Rule
    val project = createGradleProject {
        // Library dependency at depth 1 with no dependencies.
        subProject(":androidLib1") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                defaultCompileSdk()
                namespace = "com.example.androidLib1"
                minSdk = 12
                renderscriptTargetApi = 18
                renderscriptSupportModeEnabled = true
                buildFeatures {
                    renderScript = true
                }
                aarMetadata {
                    minCompileSdk = 12
                    minAgpVersion = "3.0.0"
                    minCompileSdkExtension = 2
                }
                sourceSets {
                    named("main") {
                        resourcesSrcDirs = listOf("src/main/resources")
                    }
                }
            }
            addFile("src/main/assets/android_lib_one_asset.txt", "androidLib1")
            addFile("src/main/assets/subdir/android_lib_one_asset_in_subdir.txt", "androidLib1")
            addFile("src/main/resources/my_java_resource.txt", "androidLib1")
            addFile("src/main/rs/com/example/androidLib1/ip.rsh",
                    "#pragma version(1)\n" +
                            "#pragma rs java_package_name(com.android.rs.image2)")
            addFile("src/main/rs/com/example/androidLib1/copy.rs",
                    "#include \"ip.rsh\"\n" +
                            "\n" +
                            "uchar4 __attribute__((kernel)) root(uchar4 v_in) {\n" +
                            "    return v_in;\n" +
                            "}\n")
        }
        // Library dependency at depth 0 with a dependency on androidLib1.
        subProject(":androidLib2") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                defaultCompileSdk()
                namespace = "com.example.androidLib2"
                minSdk = 19
                sourceSets {
                    named("main") {
                        resourcesSrcDirs = listOf("src/main/resources")
                    }
                }
            }
            dependencies {
                implementation(project(":androidLib1"))
            }
            addFile("src/main/assets/android_lib_two_asset.txt", "androidLib2")
        }
        // Library dependency at depth 0 with no dependencies
        subProject(":androidLib3") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                defaultCompileSdk()
                namespace = "com.example.androidLib3"
                minSdk = 18
                aarMetadata {
                    minCompileSdk = 18
                    minAgpVersion = "4.0.1"
                }
            }
            dependencies {
                implementation(project(":androidLib1"))
            }
        }
        subProject(":fusedLib1") {
            plugins.add(PluginType.FUSED_LIBRARY)
            androidFusedLibrary {
                namespace = "com.example.fusedLib1"
                minSdk = 19
            }
            dependencies {
                include(project(":androidLib3"))
                include(project(":androidLib2"))
                include(project(":androidLib1"))
            }
        }
        gradleProperties {
            set(BooleanOption.FUSED_LIBRARY_SUPPORT, true)
        }
    }

    @Test
    fun testAarMetadataMerging() {
        val fusedLib1 = project.getSubproject("fusedLib1")
        var fusedLibraryAar = getFusedLibraryAar()
        fusedLibraryAar?.let { aarFile ->
            ZipFile(aarFile).use {
                val mergedAarMetadata =
                        it.getEntry("META-INF/com/android/build/gradle/aar-metadata.properties")
                assertThat(mergedAarMetadata).isNotNull()
                val metadataContents = it.getInputStream(mergedAarMetadata)
                val aarMetadataReader = AarMetadataReader(metadataContents)
                // Value constant from AGP
                assertThat(aarMetadataReader.aarFormatVersion).isEqualTo("1.0")
                // Value constant from AGP
                assertThat(aarMetadataReader.aarMetadataVersion).isEqualTo("1.0")
                // Value from androidLib3
                assertThat(aarMetadataReader.minAgpVersion).isEqualTo("4.0.1")
                // Value from androidLib3
                assertThat(aarMetadataReader.minCompileSdk).isEqualTo("18")
                // Value from androidLib1
                assertThat(aarMetadataReader.minCompileSdkExtension).isEqualTo("2")
            }
        }

        TestFileUtils.searchAndReplace(fusedLib1.buildFile,
            "androidFusedLibrary {",
            "androidFusedLibrary {\n aarMetadata.minAgpVersion = \"8.4-alpha02\"\naarMetadata.minCompileSdk=9"
        )

        fusedLibraryAar = getFusedLibraryAar()
        fusedLibraryAar?.let { aarFile ->
            ZipFile(aarFile).use {
                val mergedAarMetadata =
                    it.getEntry("META-INF/com/android/build/gradle/aar-metadata.properties")
                assertThat(mergedAarMetadata).isNotNull()
                val metadataContents = it.getInputStream(mergedAarMetadata)
                val aarMetadataReader = AarMetadataReader(metadataContents)
                // Value constant from AGP
                assertThat(aarMetadataReader.aarFormatVersion).isEqualTo("1.0")
                // Value constant from AGP
                assertThat(aarMetadataReader.aarMetadataVersion).isEqualTo("1.0")
                // Value from aarMetadata DSL
                assertThat(aarMetadataReader.minAgpVersion).isEqualTo("8.4-alpha02")
                // Value from aarMetadata DSL
                assertThat(aarMetadataReader.minCompileSdk).isEqualTo("9")
                // Default value
                assertThat(aarMetadataReader.minCompileSdkExtension).isEqualTo("0")
            }
        }
    }

    @Test
    fun testAssetsMergingWithDuplicateAssets() {
        val androidLib3 = project.getSubproject("androidLib3")
        // Adds duplicate asset file in androidLib3, which should override the asset in androidLib1,
        // as androidLib3 is declared as a dependency in fusedLib1 before androidLib1 transitively
        // through androidLib2.
        val duplicateFile =
                FileUtils.join(androidLib3.projectDir,
                        "src",
                        "main",
                        "assets",
                        "android_lib_one_asset.txt")
        FileUtils.createFile(duplicateFile, "androidLib3")
        val fusedLibraryAar = getFusedLibraryAar()
        fusedLibraryAar?.let { aarFile ->
            ZipFile(aarFile).use { zip ->
                val mergedEntry = zip.getEntry("assets/android_lib_one_asset.txt")
                val mergedEntryContents = zip.getInputStream(mergedEntry)
                assertThat(String(mergedEntryContents.readBytes())).isEqualTo("androidLib3")
                assertThat(zip.entries()
                        .toList()
                        .map(ZipEntry::getName)).containsAtLeastElementsIn(
                        listOf(
                                "assets/android_lib_one_asset.txt",
                                "assets/android_lib_two_asset.txt",
                                "assets/subdir/android_lib_one_asset_in_subdir.txt"
                        )
                )
            }
        }
    }

    @Test
    fun testRenderscriptCreatedJniCopiesToFusedLibrary() {
        val fusedLibraryAar = getFusedLibraryAar()
        fusedLibraryAar?.let { aarFile ->
            ZipFile(aarFile).use { zip ->
                val jniEntries = zip.entries().toList()
                        .map { it.name }
                        .filter { it.startsWith("jni/") }
                        .filterNot { it.endsWith('/') }
                assertThat(jniEntries).containsAtLeastElementsIn(
                        listOf(
                                "jni/armeabi-v7a/librsjni_androidx.so",
                                "jni/armeabi-v7a/libRSSupport.so",
                                "jni/armeabi-v7a/librsjni.so",
                                "jni/armeabi-v7a/librs.copy.so",
                                "jni/x86_64/librsjni_androidx.so",
                                "jni/x86_64/libRSSupport.so",
                                "jni/x86_64/librsjni.so",
                                "jni/arm64-v8a/librsjni_androidx.so",
                                "jni/arm64-v8a/libRSSupport.so",
                                "jni/arm64-v8a/librsjni.so",
                                "jni/x86/librsjni_androidx.so",
                                "jni/x86/libRSSupport.so",
                                "jni/x86/librsjni.so",
                                "jni/x86/librs.copy.so"
                        )
                )
            }
        }
    }

    @Test
    fun testJavaResourcesMerge() {
        val aar = getFusedLibraryAar()
        val fusedLibraryClassesJar = File(temporaryFolder.newFolder(), SdkConstants.FN_CLASSES_JAR)
        ZipFile(aar!!).use {
            fusedLibraryClassesJar.writeBytes(
                it.getInputStream(ZipEntry(SdkConstants.FN_CLASSES_JAR)).readAllBytes())
        }
        ZipFile(fusedLibraryClassesJar).use { zip ->
            val entries = zip.entries().toList().map { it.toString() }
            assertThat(entries).contains("my_java_resource.txt")
        }
        val androidLib2 = project.getSubproject("androidLib2")
        androidLib2.let {
            FileUtils.createFile(
                    FileUtils.join(it.projectDir,
                            "src", "main", "resources", "my_java_resource.txt"),
                    "androidLib1"
            )
        }
        try {
            val result = project.executor().expectFailure().run(":fusedLib1:bundle")
            result.stderr.use { out ->
                assertThat(out).contains("2 files found with path 'my_java_resource.txt'")
            }
        } catch (e: Throwable) {
            if (e.stackTraceToString().contains("DirectoryNotEmptyException")) {
                // TODO(b/250872968) Debug this flakiness
                return
            }
            throw e
        }

    }

    private fun getFusedLibraryAar(): File? {
        project.executor().run(":fusedLib1:bundle")
        val fusedLib1 = project.getSubproject("fusedLib1")
        return FileUtils.join(fusedLib1.bundleDir, "bundle.aar")
    }
}
