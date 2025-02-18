/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.integration.packaging

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.truth.ApkSubject.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.packaging.PackagingTests.checkZipAlign
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.testutils.truth.PathSubject.assertThat
import com.android.tools.build.apkzlib.zip.CompressionMethod
import com.android.tools.build.apkzlib.zip.ZFile
import com.android.utils.FileUtils
import com.google.common.truth.Expect
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class NoCompressTest {

    private val content = ByteArray(1000)

    @get:Rule
    var expect = Expect.create()

    @get:Rule
    var temporaryFolder = TemporaryFolder()

    @get:Rule
    var project = GradleTestProject.builder()
        .fromTestApp(
            MinimalSubProject.app("com.example.test")
                .appendToBuild(
                    """

                        android {
                            defaultConfig {
                                versionCode 1
                            }
                            aaptOptions {
                                noCompress = ['.no', '.Test', 'end', '.a(b)c', 'space name.txt', '.KoŃcówka']
                            }
                            testOptions {
                                unitTests {
                                    includeAndroidResources true
                                }
                            }
                        }
                    """.trimIndent()
                )
                .withFile("src/main/resources/jres.yes", content)
                .withFile("src/main/resources/jres.no", content)
                .withFile("src/main/resources/jres.variantApiNo", content)
                .withFile("src/main/resources/jres.jpg", content)
                .withFile("src/main/resources/jres.tflite", content)
                .withFile("src/main/assets/a.yes", content)
                .withFile("src/main/assets/a.no", content)
                .withFile("src/main/assets/a.variantApiNo", content)
                .withFile("src/main/assets/a_matching.Test", content)
                .withFile("src/main/assets/a_lower.test", content)
                .withFile("src/main/assets/a_upper.TEST", content)
                .withFile("src/main/assets/a_space name.txt", content)
                .withFile("src/main/assets/a_pl_matching.KoŃcówka", content)
                .withFile("src/main/assets/a_pl_upper.KOŃCÓWKA", content)
                .withFile("src/main/assets/a_pl_lower.końcówka", content)
                .withFile("src/main/assets/a_weird_chars.a(b)c", content)
                .withFile("src/main/assets/a_not_weird_chars.abc", content)
                .withFile("src/main/assets/a_not_weird_chars2.ac", content)
                .withFile("src/main/assets/a_not_weird_chars3.aa(b)c", content)
                .withFile("src/main/assets/a.jpg", content)
                .withFile("src/main/assets/a.tflite", content)
                .withFile("src/main/assets/a.webp", content)
                .withFile("src/main/res/raw/r_yes.yes", content)
                .withFile("src/main/res/raw/r_no.no", content)
                .withFile("src/main/res/raw/r_matching.Test", content)
                .withFile("src/main/res/raw/r_upper.TEST", content)
                .withFile("src/main/res/raw/r_lower.test", content)
                .withFile("src/main/res/raw/r_end_.noKeep", content)
                .withFile("src/main/res/raw/r_pl_matching.KoŃcówka", content)
                .withFile("src/main/res/raw/r_pl_upper.KOŃCÓWKA", content)
                .withFile("src/main/res/raw/r_pl_lower.końcówka", content)
                .withFile("src/main/res/raw/r_weird_chars.a(b)c", content)
                .withFile("src/main/res/raw/r_not_weird_chars.abc", content)
                .withFile("src/main/res/raw/r_not_weird_chars2.ac", content)
                .withFile("src/main/res/raw/r_not_weird_chars3.aa(b)c", content)
                .withFile("src/main/res/raw/r_jpg.jpg", content)
        )
        .create()

    @Test
    fun noCompressIsAccepted() {
        project.execute(":assembleDebug")
        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)
        assertThat(apk).exists()
        verifyCompression(apk.file.toFile())

        project.execute(":packageDebugUnitTestForUnitTest")
        val unitTestApk =
            FileUtils.join(
                project.getIntermediateFile(
                    InternalArtifactType.APK_FOR_LOCAL_TEST.getFolderName()
                ),
                "debugUnitTest",
                "packageDebugUnitTestForUnitTest",
                "apk-for-local-test.ap_"
            )
        assertThat(unitTestApk).exists()
        verifyCompression(unitTestApk, checkJavaResources = false)
    }

    @Test
    fun noCompressWithEmptyString() {
        TestFileUtils.searchAndReplace(
            project.buildFile,
            "noCompress = [",
            "noCompress = ['', "
        )
        project.execute(":assembleDebug")
        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)
        assertThat(apk).exists()
        val uncompressedPaths =
            apk.entries
                .map { FileUtils.toSystemIndependentPath(it.toString()).removePrefix("/") }
                .filter {
                    it.startsWith("jres.") || it.startsWith("assets/") || it.startsWith("res/")
                }
        Truth.assertThat(uncompressedPaths.size).isEqualTo(36)
        ZFile.openReadOnly(apk.file.toFile()).use {
            uncompressedPaths.forEach { path ->
                it.expectCompressionMethodOf(path).isEqualTo(CompressionMethod.STORE)
            }
        }
    }

    /** Regression test for Issue 233102273 */
    @Test
    fun noCompressWithMinifyEnabled() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                android {
                    buildTypes {
                        debug {
                            minifyEnabled true
                        }
                    }
                }
            """.trimIndent()
        )
        project.execute(":assembleDebug")
        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)
        assertThat(apk).exists()
        verifyCompression(apk.file.toFile())
        // check alignment as regression test for Issue 241469653
        checkZipAlign(apk)
    }

    @Test
    fun bundleNoCompressTest() {
        project.executor().run(":makeApkFromBundleForDebug")

        val extracted = temporaryFolder.newFile("base-master.apk")

        FileUtils.createZipFilesystem(project.getIntermediateFile("apks_from_bundle", "debug", "makeApkFromBundleForDebug", "bundle.apks").toPath()).use { apks ->
            extracted.outputStream().buffered().use {
                Files.copy(apks.getPath("splits/base-master.apk"), it)
            }
        }
        verifyCompression(extracted)
    }

    private fun verifyCompression(apk: File, checkJavaResources: Boolean = true) {
        ZFile.openReadOnly(apk).use { zf ->
            if (checkJavaResources) {
                zf.expectCompressionMethodOf("jres.yes").isEqualTo(CompressionMethod.DEFLATE)
                zf.expectCompressionMethodOf("jres.no").isEqualTo(CompressionMethod.STORE)
                zf.expectCompressionMethodOf("jres.jpg").isEqualTo(CompressionMethod.STORE)
                zf.expectCompressionMethodOf("jres.tflite").isEqualTo(CompressionMethod.STORE)
            }
            zf.expectCompressionMethodOf("assets/a.yes").isEqualTo(CompressionMethod.DEFLATE)
            zf.expectCompressionMethodOf("assets/a.no").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("assets/a_matching.Test").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("assets/a_lower.test").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("assets/a_upper.TEST").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("assets/a_space name.txt").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("assets/a_pl_matching.KoŃcówka").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("assets/a_pl_upper.KOŃCÓWKA").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("assets/a_pl_lower.końcówka").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("assets/a_weird_chars.a(b)c").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("assets/a_not_weird_chars.abc").isEqualTo(CompressionMethod.DEFLATE)
            zf.expectCompressionMethodOf("assets/a_not_weird_chars2.ac").isEqualTo(CompressionMethod.DEFLATE)
            zf.expectCompressionMethodOf("assets/a_not_weird_chars3.aa(b)c").isEqualTo(CompressionMethod.DEFLATE)
            zf.expectCompressionMethodOf("assets/a.jpg").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("assets/a.tflite").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("assets/a.webp").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("res/raw/r_yes.yes").isEqualTo(CompressionMethod.DEFLATE)
            zf.expectCompressionMethodOf("res/raw/r_no.no").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("res/raw/r_matching.Test").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("res/raw/r_upper.TEST").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("res/raw/r_lower.test").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("res/raw/r_end_.noKeep").isEqualTo(CompressionMethod.DEFLATE)
            zf.expectCompressionMethodOf("res/raw/r_pl_matching.KoŃcówka").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("res/raw/r_pl_upper.KOŃCÓWKA").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("res/raw/r_pl_lower.końcówka").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("res/raw/r_weird_chars.a(b)c").isEqualTo(CompressionMethod.STORE)
            zf.expectCompressionMethodOf("res/raw/r_not_weird_chars.abc").isEqualTo(CompressionMethod.DEFLATE)
            zf.expectCompressionMethodOf("res/raw/r_not_weird_chars2.ac").isEqualTo(CompressionMethod.DEFLATE)
            zf.expectCompressionMethodOf("res/raw/r_not_weird_chars3.aa(b)c").isEqualTo(CompressionMethod.DEFLATE)
            zf.expectCompressionMethodOf("res/raw/r_jpg.jpg").isEqualTo(CompressionMethod.STORE)
        }
    }

    private fun ZFile.expectCompressionMethodOf(path: String) =
        expect.that(get(path)?.centralDirectoryHeader?.compressionInfoWithWait?.method)
            .named("Compression method of $path")
}
