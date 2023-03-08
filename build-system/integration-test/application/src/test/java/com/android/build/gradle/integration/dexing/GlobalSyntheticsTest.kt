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

package com.android.build.gradle.integration.dexing

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.getOutputDir
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.IntegerOption
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.apk.AndroidArchive
import com.google.common.io.Resources
import com.google.common.truth.Truth
import org.junit.Assume
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.nio.file.Files

@RunWith(Parameterized::class)
class GlobalSyntheticsTest(private val dexType: DexType) {

    enum class DexType {
        MONO, LEGACY, NATIVE
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "dexType_{0}")
        fun parameters() = listOf(DexType.MONO, DexType.LEGACY, DexType.NATIVE)

        const val recordGlobalDex = "Lcom/android/tools/r8/RecordTag;"
    }

    private val recordJarUrl = Resources.getResource(
        GlobalSyntheticsTest::class.java,
        "GlobalSyntheticsTest/record.jar"
    )

    private val recordJar2Url = Resources.getResource(
            GlobalSyntheticsTest::class.java,
            "GlobalSyntheticsTest/record2.jar"
    )

    private val mavenRepo = MavenRepoGenerator(
        listOf(
            MavenRepoGenerator.Library(
                "com.example:myjar:1", Resources.toByteArray(recordJar2Url))
        )
    )

    @get:Rule
    val project = GradleTestProject.builder()
        .withAdditionalMavenRepo(mavenRepo)
        .fromTestApp(
            MultiModuleTestProject.builder()
                .subproject("app", MinimalSubProject.app("com.example.app"))
                .subproject("lib", MinimalSubProject.lib("com.example.lib"))
                .build()
        ).create()

    private lateinit var app: GradleTestProject
    private lateinit var lib: GradleTestProject

    @Before
    fun setUp() {
        app = project.getSubproject("app")
        lib = project.getSubproject("lib")

        TestFileUtils.appendToFile(
            app.buildFile,
            """
                android {
                    compileOptions {
                        sourceCompatibility JavaVersion.VERSION_14
                        targetCompatibility JavaVersion.VERSION_14
                    }
                }

                dependencies {
                    implementation project(":lib")
                }
            """.trimIndent()
        )

        val minSdkVersion = if (dexType == DexType.NATIVE) 21 else 20

        TestFileUtils.appendToFile(
            app.buildFile,
            "\nandroid.defaultConfig.minSdkVersion = $minSdkVersion\n"
        )
        if (dexType == DexType.LEGACY) {
            TestFileUtils.appendToFile(
                app.buildFile,
                "android.defaultConfig.multiDexEnabled = true"
            )
        }
    }

    @Test
    fun testGlobalFromFileDep() {
        addFileDependencies(app)

        executor().run("assembleDebug")

        val recordGlobalFromFileDep = InternalArtifactType.GLOBAL_SYNTHETICS_FILE_LIB
            .getOutputDir(app.buildDir).resolve("debug/0_record.jar")
        Truth.assertThat(recordGlobalFromFileDep.exists()).isTrue()

        if (dexType == DexType.NATIVE) {
            val globalDex = InternalArtifactType.GLOBAL_SYNTHETICS_DEX.getOutputDir(app.buildDir)
                .resolve("debug/classes.dex")
            Truth.assertThat(globalDex.exists()).isTrue()
        }

        checkPackagedGlobal(recordGlobalDex)
    }

    @Test
    fun testGlobalFromExternalDep() {
        app.buildFile.appendText(
            """

                dependencies {
                    implementation 'com.example:myjar:1'
                }
            """.trimIndent()
        )

        executor().run("assembleDebug")

        if (dexType == DexType.NATIVE) {
            val globalDex = InternalArtifactType.GLOBAL_SYNTHETICS_DEX.getOutputDir(app.buildDir)
                .resolve("debug/classes.dex")
            Truth.assertThat(globalDex.exists()).isTrue()
        }

        checkPackagedGlobal(recordGlobalDex)
    }

    @Test
    fun testDeDupGlobal() {
        addFileDependencies(app)

        app.buildFile.appendText(
                """

                dependencies {
                    implementation 'com.example:myjar:1'
                }
            """.trimIndent()
        )

        executor().run("assembleDebug")
        checkPackagedGlobal(recordGlobalDex)
    }

    // basic check for disabling global synthetics generation
    @Test
    fun testDisableGlobalSynthetics() {
        addFileDependencies(app)

        val disableWithFlag = executor()
                .with(BooleanOption.ENABLE_GLOBAL_SYNTHETICS, false)
                .expectFailure()
                .run("assembleDebug")

        ScannerSubject.assertThat(disableWithFlag.stderr)
                .contains("Attempt to create a global synthetic for 'Record desugaring' without a global-synthetics consumer.")

        TestFileUtils.appendToFile(
                app.buildFile,
                """
                    android {
                        compileOptions {
                            sourceCompatibility JavaVersion.VERSION_13
                            targetCompatibility JavaVersion.VERSION_13
                        }
                    }
                """.trimIndent()
        )

        val disableWithDsl = executor().expectFailure().run("assembleDebug")

        ScannerSubject.assertThat(disableWithDsl.stderr)
                .contains("Attempt to create a global synthetic for 'Record desugaring' without a global-synthetics consumer.")
    }

    // Regression test for b/257488927
    @Test
    fun minSdkVersionConsistent() {
        Assume.assumeTrue(dexType == DexType.NATIVE)
        addFileDependencies(app)
        executor()
                .with(IntegerOption.IDE_TARGET_DEVICE_API, 24)
                .run("assembleDebug")
    }

    private fun addFileDependencies(app: GradleTestProject) {
        val recordJar = "record.jar"

        Files.write(
            app.projectDir.resolve(recordJar).toPath(),
            Resources.toByteArray(recordJarUrl)
        )
        app.buildFile.appendText(
            """

                dependencies {
                    implementation files('$recordJar')
                }
            """.trimIndent()
        )
    }

    private fun checkPackagedGlobal(global: String, expectedCount: Int = 1) {
        val apk = app.getApk(GradleTestProject.ApkType.DEBUG)

        // there should only be a single global synthetics of specific type in the apk
        val dexes = apk.allDexes.filter {
            AndroidArchive.checkValidClassName(global)
            it.classes.keys.contains(global)
        }
        Truth.assertThat(dexes.size).isEqualTo(expectedCount)
    }

    private fun executor() = project.executor()
}
