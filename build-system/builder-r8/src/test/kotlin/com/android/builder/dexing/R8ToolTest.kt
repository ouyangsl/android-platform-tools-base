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

package com.android.builder.dexing

import com.android.builder.dexing.testdata.ClassWithAssertions
import com.android.testutils.TestClassesGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestUtils
import com.android.testutils.apk.Dex
import com.android.testutils.truth.DexSubject.assertThat
import com.android.testutils.truth.DexSubject.assertThatDex
import com.android.testutils.truth.PathSubject.assertThat
import com.android.testutils.truth.ZipFileSubject.assertThat
import com.android.tools.r8.CompilationFailedException
import com.android.utils.Pair
import com.android.utils.FileUtils
import com.android.zipflinger.ZipArchive
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.fail

/**
 * Sanity test that make sure we can invoke R8 with some basic configurations.
 */
class R8ToolTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val emptyProguardOutputFiles by lazy {
        val fakeOutput = tmp.newFolder().resolve("fake_output.txt").toPath()
        ProguardOutputFiles(fakeOutput, fakeOutput, fakeOutput, fakeOutput, fakeOutput)
    }

    private val emptyJavaResources by lazy {
        tmp.root.toPath().resolve("java_resources.jar").also {
            TestInputsGenerator.jarWithEmptyClasses(it, listOf())
        }
    }

    @Test
    fun testClassesFromDir() {
        val proguardConfig = ProguardConfig(listOf(), null, listOf(), emptyProguardOutputFiles)
        val mainDexConfig = MainDexListConfig(listOf(), listOf())
        val toolConfig = ToolConfig(
                minSdkVersion = 21,
                isDebuggable = true,
                disableTreeShaking = true,
                disableDesugaring = true,
                disableMinification = true,
                r8OutputType = R8OutputType.DEX
        )

        val classes = tmp.newFolder().toPath()
        TestInputsGenerator.dirWithEmptyClasses(classes, listOf("test/A", "test/B"))

        val output = tmp.newFolder().toPath()
        val javaRes = tmp.root.resolve("res.jar").toPath()
        runR8(
            listOf(classes),
            output,
            emptyJavaResources,
            javaRes,
            bootClasspath,
            emptyList(),
            toolConfig,
            proguardConfig,
            mainDexConfig,
            resourceShrinkingConfig = null,
            NoOpMessageReceiver(),
            featureClassJars = listOf(),
            featureJavaResourceJars = listOf(),
            featureDexDir = null,
            featureJavaResourceOutputDir = null
        )

        assertThat(getDexFileCount(output)).isEqualTo(1)
    }

    @Test
    fun testClassesFromJar() {
        val proguardConfig = ProguardConfig(listOf(), null, listOf(), emptyProguardOutputFiles)
        val mainDexConfig = MainDexListConfig(listOf(), listOf())
        val toolConfig = ToolConfig(
                minSdkVersion = 21,
                isDebuggable = true,
                disableTreeShaking = true,
                disableDesugaring = true,
                disableMinification = true,
                r8OutputType = R8OutputType.DEX
        )

        val classes = tmp.newFolder().toPath().resolve("classes.jar")
        TestInputsGenerator.jarWithEmptyClasses(classes, listOf("test/A", "test/B"))

        val output = tmp.newFolder().toPath()
        val javaRes = tmp.root.resolve("res.jar").toPath()
        runR8(
            listOf(classes),
            output,
            emptyJavaResources,
            javaRes,
            bootClasspath,
            emptyList(),
            toolConfig,
            proguardConfig,
            mainDexConfig,
            resourceShrinkingConfig = null,
            NoOpMessageReceiver(),
            featureClassJars = listOf(),
            featureJavaResourceJars = listOf(),
            featureDexDir = null,
            featureJavaResourceOutputDir = null
        )
        assertThat(getDexFileCount(output)).isEqualTo(1)
    }

    @Test
    fun testClassesAndResources() {
        val proguardConfig = ProguardConfig(listOf(), null, listOf(), emptyProguardOutputFiles)
        val mainDexConfig = MainDexListConfig(listOf(), listOf())
        val toolConfig = ToolConfig(
            minSdkVersion = 21,
            isDebuggable = true,
            disableTreeShaking = true,
            disableDesugaring = true,
            disableMinification = true,
            r8OutputType = R8OutputType.DEX
        )

        val classes = tmp.newFolder().toPath().resolve("classes.jar")
        ZipOutputStream(classes.toFile().outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("test/A.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "A"));
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("test/B.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "B"));
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("res.txt"))
            zip.closeEntry()
        }

        val output = tmp.newFolder().toPath()
        val javaRes = tmp.root.resolve("res.jar").toPath()
        runR8(
            listOf(classes),
            output,
            classes,
            javaRes,
            bootClasspath,
            emptyList(),
            toolConfig,
            proguardConfig,
            mainDexConfig,
            resourceShrinkingConfig = null,
            NoOpMessageReceiver(),
            featureClassJars = listOf(),
            featureJavaResourceJars = listOf(),
            featureDexDir = null,
            featureJavaResourceOutputDir = null
        )
        assertThat(getDexFileCount(output)).isEqualTo(1)

        assertThat(javaRes) { it.contains("res.txt") }

        // check Java resources are compressed
        ZipFile(javaRes.toFile()).use { zip ->
            for (entry in zip.entries()) {
                assertThat(entry.method).named("entry is compressed").isEqualTo(ZipEntry.DEFLATED)
            }
        }
    }

    @Test
    fun testClassesAndResources_fullR8() {
        val proguardConfig = ProguardConfig(listOf(), null, listOf(), emptyProguardOutputFiles)
        val mainDexConfig = MainDexListConfig(listOf(), listOf())
        val toolConfig = ToolConfig(
            minSdkVersion = 21,
            isDebuggable = true,
            disableTreeShaking = true,
            disableDesugaring = true,
            disableMinification = true,
            r8OutputType = R8OutputType.DEX
        )

        val classes = tmp.newFolder().toPath().resolve("classes.jar")
        ZipOutputStream(classes.toFile().outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("test/A.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "A"));
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("test/B.class"))
            zip.write(TestClassesGenerator.emptyClass("test", "B"));
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("res.txt"))
            zip.closeEntry()
        }

        val output = tmp.newFolder().toPath()
        val javaRes = tmp.root.resolve("res.jar").toPath()
        runR8(
            listOf(classes),
            output,
            classes,
            javaRes,
            bootClasspath,
            emptyList(),
            toolConfig,
            proguardConfig,
            mainDexConfig,
            resourceShrinkingConfig = null,
            NoOpMessageReceiver(),
            true,
            featureClassJars = listOf(),
            featureJavaResourceJars = listOf(),
            featureDexDir = null,
            featureJavaResourceOutputDir = null
        )
        assertThat(getDexFileCount(output)).isEqualTo(1)
        assertThat(javaRes) { it.contains("res.txt") }
    }

    @Test
    fun testMainDexList() {
        val proguardConfig = ProguardConfig(listOf(), null, listOf(), emptyProguardOutputFiles)
        val toolConfig = ToolConfig(
                minSdkVersion = 19,
                isDebuggable = true,
                disableTreeShaking = true,
                disableDesugaring = true,
                disableMinification = true,
                r8OutputType = R8OutputType.DEX
        )

        val classes = tmp.newFolder().toPath().resolve("classes.jar")
        TestInputsGenerator.dirWithEmptyClasses(classes, listOf("test/A", "test/B"))

        val mainDexList = tmp.newFile().toPath()
        Files.write(mainDexList, listOf("test/A.class"))
        val mainDexConfig = MainDexListConfig(
                mainDexRulesFiles = listOf(),
                mainDexListFiles = listOf(mainDexList),
                mainDexRules = listOf())

        val output = tmp.newFolder().toPath()
        val javaRes = tmp.root.resolve("res.jar").toPath()
        runR8(
            listOf(classes),
            output,
            emptyJavaResources,
            javaRes,
            bootClasspath,
            emptyList(),
            toolConfig,
            proguardConfig,
            mainDexConfig,
            resourceShrinkingConfig = null,
            NoOpMessageReceiver(),
            featureClassJars = listOf(),
            featureJavaResourceJars = listOf(),
            featureDexDir = null,
            featureJavaResourceOutputDir = null
        )
        assertThat(getDexFileCount(output)).isEqualTo(2)
    }

    @Test
    fun testMainDexListRules() {
        val proguardConfig = ProguardConfig(listOf(), null, listOf(), emptyProguardOutputFiles)
        val toolConfig = ToolConfig(
                minSdkVersion = 19,
                isDebuggable = true,
                disableTreeShaking = true,
                disableDesugaring = true,
                disableMinification = true,
                r8OutputType = R8OutputType.DEX
        )

        val classes = tmp.newFolder().toPath().resolve("classes.jar")
        TestInputsGenerator.dirWithEmptyClasses(classes, listOf("test/A", "test/B"))

        val mainDexRules = tmp.newFile().toPath()
        Files.write(mainDexRules, listOf("-keep class test.A"))
        val mainDexConfig = MainDexListConfig(listOf(mainDexRules), listOf())

        val output = tmp.newFolder().toPath()
        val javaRes = tmp.root.resolve("res.jar").toPath()
        runR8(
            listOf(classes),
            output,
            emptyJavaResources,
            javaRes,
            bootClasspath,
            emptyList(),
            toolConfig,
            proguardConfig,
            mainDexConfig,
            resourceShrinkingConfig = null,
            NoOpMessageReceiver(),
            featureClassJars = listOf(),
            featureJavaResourceJars = listOf(),
            featureDexDir = null,
            featureJavaResourceOutputDir = null
        )
        assertThat(getDexFileCount(output)).isEqualTo(2)
    }

    @Test
    fun testKeepRules() {
        val mainDexConfig = MainDexListConfig(listOf(), listOf())
        val toolConfig = ToolConfig(
                minSdkVersion = 21,
                isDebuggable = true,
                disableTreeShaking = false,
                disableDesugaring = true,
                disableMinification = false,
                r8OutputType = R8OutputType.DEX
        )

        val classes = tmp.newFolder().toPath().resolve("classes.jar")
        TestInputsGenerator.dirWithEmptyClasses(classes, listOf("test/A", "test/B"))

        val proguardRules = tmp.newFile().toPath()
        Files.write(proguardRules, listOf("-keep class test.A"))
        val proguardConfig = ProguardConfig(listOf(proguardRules), null, listOf(), emptyProguardOutputFiles)

        val output = tmp.newFolder().toPath()
        val javaRes = tmp.root.resolve("res.jar").toPath()
        runR8(
            listOf(classes),
            output,
            emptyJavaResources,
            javaRes,
            bootClasspath,
            emptyList(),
            toolConfig,
            proguardConfig,
            mainDexConfig,
            resourceShrinkingConfig = null,
            NoOpMessageReceiver(),
            featureClassJars = listOf(),
            featureJavaResourceJars = listOf(),
            featureDexDir = null,
            featureJavaResourceOutputDir = null
        )
        assertThat(getDexFileCount(output)).isEqualTo(1)
        assertThatDex(output.resolve("classes.dex").toFile()).containsClass("Ltest/A;")
        assertThatDex(output.resolve("classes.dex").toFile()).doesNotContainClasses("Ltest/B;")
    }

    @Test
    fun testProguardMapping() {
        val mainDexConfig = MainDexListConfig(listOf(), listOf())
        val toolConfig = ToolConfig(
                minSdkVersion = 21,
                isDebuggable = true,
                disableTreeShaking = true,
                disableDesugaring = true,
                disableMinification = true,
                r8OutputType = R8OutputType.DEX
        )

        val testClasses = tmp.newFolder().toPath().resolve("testClasses.jar")
        TestInputsGenerator.pathWithClasses(
                testClasses,
                listOf(ExampleClasses.TestClass::class.java))

        val programClasses = tmp.newFolder().toPath().resolve("programClasses.jar")
        TestInputsGenerator.pathWithClasses(
                programClasses,
                listOf(ExampleClasses::class.java, ExampleClasses.ProgramClass::class.java))

        val programClasspath = mutableListOf(programClasses)

        val proguardInputMapping = tmp.newFile("space in name.txt").toPath()
        Files.write(
                proguardInputMapping,
                listOf(
                        "com.android.builder.dexing.ExampleClasses\$ProgramClass -> foo.Bar:",
                        "  1:1:void method():42:42 -> baz"))
        val proguardConfig =
                ProguardConfig(
                        listOf(),
                        proguardInputMapping,
                        listOf(),
                        ProguardOutputFiles(
                            tmp.root.toPath().resolve("mapping.txt"),
                            tmp.root.toPath().resolve("seeds.txt"),
                            tmp.root.toPath().resolve("usage.txt"),
                            tmp.root.toPath().resolve("configuration.txt"),
                            tmp.root.toPath().resolve("missing_rules.txt"),
                        )
                )

        val output = tmp.newFolder().toPath()
        val javaRes = tmp.root.resolve("res.jar").toPath()
        runR8(
            listOf(testClasses),
            output,
            emptyJavaResources,
            javaRes,
            bootClasspath,
            programClasspath,
            toolConfig,
            proguardConfig,
            mainDexConfig,
            resourceShrinkingConfig = null,
            NoOpMessageReceiver(),
            featureClassJars = listOf(),
            featureJavaResourceJars = listOf(),
            featureDexDir = null,
            featureJavaResourceOutputDir = null
        )
        assertThat(getDexFileCount(output)).isEqualTo(1)
        assertThatDex(output.resolve("classes.dex").toFile())
            .containsClass("Lcom/android/builder/dexing/ExampleClasses\$TestClass;")
            .that()
            .hasMethodThatInvokes("test", "Lfoo/Bar;->baz()V")
        assertThat(Files.exists(proguardConfig.proguardOutputFiles.proguardMapOutput)).isTrue()
    }

    @Test
    fun testUsageAndSeeds() {
        val mainDexConfig = MainDexListConfig(listOf(), listOf())
        val toolConfig = ToolConfig(
            minSdkVersion = 21,
            isDebuggable = true,
            disableTreeShaking = false,
            disableDesugaring = true,
            disableMinification = false,
            r8OutputType = R8OutputType.DEX
        )
        val classes = tmp.newFolder().toPath().resolve("classes.jar")
        TestInputsGenerator.dirWithEmptyClasses(classes, listOf("test/A", "test/B"))
        val output = tmp.newFolder().toPath()
        val javaRes = tmp.root.resolve("res.jar").toPath()

        val proguardSeedsOutput = tmp.root.toPath().resolve("seeds.txt")
        val proguardUsageOutput = tmp.root.toPath().resolve("usage.txt")
        val proguardConfigurationOutput = tmp.root.toPath().resolve("configuration.txt")
        val proguardConfig =
            ProguardConfig(
                listOf(),
                null,
                listOf(),
                ProguardOutputFiles(
                    tmp.root.toPath().resolve("mapping.txt"),
                    proguardSeedsOutput,
                    proguardUsageOutput,
                    proguardConfigurationOutput,
                    tmp.root.toPath().resolve("missing_rules.txt"),
                )
            )
        runR8(
            listOf(classes),
            output,
            emptyJavaResources,
            javaRes,
            bootClasspath,
            emptyList(),
            toolConfig,
            proguardConfig,
            mainDexConfig,
            resourceShrinkingConfig = null,
            NoOpMessageReceiver(),
            featureClassJars = listOf(),
            featureJavaResourceJars = listOf(),
            featureDexDir = null,
            featureJavaResourceOutputDir = null
        )
        assertThat(Files.exists(proguardSeedsOutput)).isTrue()
        assertThat(Files.exists(proguardUsageOutput)).isTrue()
        assertThat(Files.exists(proguardConfigurationOutput)).isTrue()
    }

    @Test
    fun testErrorReporting() {
        val mainDexConfig = MainDexListConfig(listOf(), listOf())
        val toolConfig = ToolConfig(
            minSdkVersion = 21,
            isDebuggable = true,
            disableTreeShaking = false,
            disableDesugaring = true,
            disableMinification = false,
            r8OutputType = R8OutputType.DEX
        )

        val proguardRules = tmp.newFile().toPath()
        Files.write(proguardRules, listOf("wrongRuleExample"))
        val proguardConfig = ProguardConfig(listOf(proguardRules), null, listOf(), emptyProguardOutputFiles)

        val output = tmp.newFolder().toPath()
        val javaRes = tmp.root.resolve("res.jar").toPath()
        val messages = mutableListOf<String>()
        val toolNameTags = mutableListOf<String>()

        try {
            runR8(
                listOf(),
                output,
                emptyJavaResources,
                javaRes,
                bootClasspath,
                emptyList(),
                toolConfig,
                proguardConfig,
                mainDexConfig,
                resourceShrinkingConfig = null,
                { message ->
                    messages.add(message.text)
                    toolNameTags.add(message.toolName!!)
                },
                featureClassJars = listOf(),
                featureJavaResourceJars = listOf(),
                featureDexDir = null,
                featureJavaResourceOutputDir = null
            )
            fail("Parsing proguard configuration should fail.")
        } catch (e: Throwable){
            assertThat(messages.single()).contains("Expected char '-' at")
            assertThat(messages.single()).contains("1:1")
            assertThat(messages.single()).contains("wrongRuleExample")
            assertThat(toolNameTags).containsExactly("R8")
        }
    }

    @Test
    fun testMultiReleaseFromDir() {
        val proguardConfig = ProguardConfig(listOf(), null, listOf(), emptyProguardOutputFiles)
        val mainDexConfig = MainDexListConfig(listOf(), listOf())
        val toolConfig = ToolConfig(
            minSdkVersion = 21,
            isDebuggable = true,
            disableTreeShaking = true,
            disableDesugaring = true,
            disableMinification = true,
            r8OutputType = R8OutputType.DEX
        )

        val classes = tmp.newFolder().toPath()
        TestInputsGenerator.dirWithEmptyClasses(classes, listOf("test/A", "test/B"))
        classes.resolve("META-INF/versions/9/test/C.class").also {
            it.parent.toFile().mkdirs()
            it.toFile().writeText("malformed class file")
        }

        val output = tmp.newFolder().toPath()
        val javaRes = tmp.root.resolve("res.jar").toPath()
        runR8(
            listOf(classes),
            output,
            emptyJavaResources,
            javaRes,
            bootClasspath,
            emptyList(),
            toolConfig,
            proguardConfig,
            mainDexConfig,
            resourceShrinkingConfig = null,
            NoOpMessageReceiver(),
            featureClassJars = listOf(),
            featureJavaResourceJars = listOf(),
            featureDexDir = null,
            featureJavaResourceOutputDir = null
        )

        assertThatDex(output.resolve("classes.dex").toFile())
            .containsExactlyClassesIn(listOf("Ltest/A;", "Ltest/B;"))
    }

    @Test
    fun testFeatureJars() {
        val proguardConfig = ProguardConfig(listOf(), null, listOf(), emptyProguardOutputFiles)
        val mainDexConfig = MainDexListConfig(listOf(), listOf())
        val toolConfig = ToolConfig(
            minSdkVersion = 21,
            isDebuggable = true,
            disableTreeShaking = true,
            disableDesugaring = true,
            disableMinification = true,
            r8OutputType = R8OutputType.DEX
        )

        val classes = tmp.newFolder().toPath().resolve("classes.jar")
        TestInputsGenerator.jarWithEmptyClasses(classes, listOf("test/A", "test/B"))

        val featureClassesJar = tmp.newFolder().toPath().resolve("feature1.jar")
        TestInputsGenerator.jarWithEmptyClasses(featureClassesJar, listOf("test/C", "test/D"))

        val emptyFeatureClassesJar = tmp.newFolder().toPath().resolve("feature2.jar")
        ZipArchive(emptyFeatureClassesJar).use {}
        assertThat(emptyFeatureClassesJar).exists()

        val javaResJar = tmp.newFolder().toPath().resolve("base.jar")
        TestInputsGenerator.writeJarWithTextEntries(javaResJar, Pair.of("foo.txt", "foo"))

        val featureJavaResJar = tmp.newFolder().toPath().resolve("feature1.jar")
        TestInputsGenerator.writeJarWithTextEntries(featureJavaResJar, Pair.of("bar.txt", "bar"))

        val emptyFeatureJavaResJar = tmp.newFolder().toPath().resolve("feature2.jar")
        ZipArchive(emptyFeatureJavaResJar).use {}
        assertThat(emptyFeatureJavaResJar).exists()

        val output = tmp.newFolder().toPath()
        val javaRes = tmp.root.resolve("res.jar").toPath()
        val featureDexDir = tmp.newFolder().toPath()
        val featureJavaResourceOutputDir = tmp.newFolder().toPath()
        runR8(
            listOf(classes),
            output,
            javaResJar,
            javaRes,
            bootClasspath,
            emptyList(),
            toolConfig,
            proguardConfig,
            mainDexConfig,
            resourceShrinkingConfig = null,
            NoOpMessageReceiver(),
            featureClassJars = listOf(featureClassesJar, emptyFeatureClassesJar),
            featureJavaResourceJars = listOf(featureJavaResJar, emptyFeatureJavaResJar),
            featureDexDir = featureDexDir,
            featureJavaResourceOutputDir = featureJavaResourceOutputDir
        )
        assertThat(getDexFileCount(output)).isEqualTo(1)
        val feature1DexOutput = featureDexDir.resolve("feature1")
        assertThat(feature1DexOutput).exists()
        assertThat(getDexFileCount(feature1DexOutput)).isEqualTo(1)
        val feature2DexOutput = featureDexDir.resolve("feature2")
        assertThat(feature2DexOutput).exists()
        assertThat(getDexFileCount(feature2DexOutput)).isEqualTo(0)
        assertThat(javaRes).exists()
        assertThat(ZipArchive.listEntries(javaRes).keys).containsExactly("foo.txt")
        val feature1JavaResOutput = featureJavaResourceOutputDir.resolve("feature1.jar")
        assertThat(feature1JavaResOutput).exists()
        assertThat(ZipArchive.listEntries(feature1JavaResOutput).keys).containsExactly("bar.txt")
        val feature2JavaResOutput = featureJavaResourceOutputDir.resolve("feature2.jar")
        assertThat(feature2JavaResOutput).exists()
        assertThat(ZipArchive.listEntries(feature2JavaResOutput)).isEmpty()
    }

    @Test
    fun testAssertionsGeneration() {
        val testClass = ClassWithAssertions::class.java
        val proguardConfig = ProguardConfig(
            listOf(),
            null,
            listOf(
                "-keep class ${testClass.name} { public void foo(); }",
                "-dontwarn ${testClass.name}"),
            emptyProguardOutputFiles
        )
        val mainDexConfig = MainDexListConfig(listOf(), listOf())
        val debuggableToolConfig = ToolConfig(
            minSdkVersion = 21,
            isDebuggable = true,
            disableTreeShaking = false,
            disableDesugaring = true,
            disableMinification = true,
            r8OutputType = R8OutputType.DEX
        )

        val classes = tmp.newFolder().toPath().resolve("classes.jar")
        TestInputsGenerator.pathWithClasses(classes, listOf(testClass))

        val output = tmp.newFolder().toPath()
        val javaRes = tmp.root.resolve("res.jar").toPath()
        runR8(
            listOf(classes),
            output,
            emptyJavaResources,
            javaRes,
            bootClasspath,
            emptyList(),
            debuggableToolConfig,
            proguardConfig,
            mainDexConfig,
            resourceShrinkingConfig = null,
            NoOpMessageReceiver(),
            featureClassJars = listOf(),
            featureJavaResourceJars = listOf(),
            featureDexDir = null,
            featureJavaResourceOutputDir = null
        )
        val className = testClass.name.replace('.', '/')
        val dex = Dex(output.toFile().walk().filter { it.extension == "dex" }.single())

        assertThat(dex).containsClass("L$className;")
            .that()
            .hasMethodThatInvokes("foo", "Ljava/lang/AssertionError;-><init>()V")

        val releaseToolConfig = debuggableToolConfig.copy(isDebuggable = false)
        FileUtils.cleanOutputDir(output.toFile())
        runR8(
            listOf(classes),
            output,
            emptyJavaResources,
            javaRes,
            bootClasspath,
            emptyList(),
            releaseToolConfig,
            proguardConfig,
            mainDexConfig,
            resourceShrinkingConfig = null,
            NoOpMessageReceiver(),
            featureClassJars = listOf(),
            featureJavaResourceJars = listOf(),
            featureDexDir = null,
            featureJavaResourceOutputDir = null
        )
        assertThat(dex).containsClass("L$className;")
            .that()
            .hasMethodThatDoesNotInvoke(
                "foo",
                "Ljava/lang/AssertionError;-><init>(Ljava/lang/Object;)V"
            )
    }

    @Test
    fun testMissingRulesGenerated() {
        val missingRules = tmp.newFile()
        val proguardConfig = ProguardConfig(listOf(), null, listOf(),
                ProguardOutputFiles(
                        tmp.newFile().toPath(),
                        tmp.newFile().toPath(),
                        tmp.newFile().toPath(),
                        tmp.newFile().toPath(),
                        missingRules.toPath()
                )
        )
        val mainDexConfig = MainDexListConfig(listOf(), listOf())
        val toolConfig = ToolConfig(
                minSdkVersion = 21,
                isDebuggable = false,
                disableTreeShaking = true,
                disableDesugaring = true,
                disableMinification = true,
                r8OutputType = R8OutputType.DEX
        )

        val classes = tmp.newFolder().toPath().resolve("classes.jar").also {
            val classToWrite = TestClassesGenerator.classWithEmptyMethods(
                    "A", "foo:()Ltest/B;", "bar:()Ltest/C;")
            ZipOutputStream(it.toFile().outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("test/A.class"))
                zip.write(classToWrite)
                zip.closeEntry()
            }
        }

        val output = tmp.newFolder().toPath()
        val javaRes = tmp.root.resolve("res.jar").toPath()
        try {
            runR8(
                listOf(classes),
                output,
                emptyJavaResources,
                javaRes,
                bootClasspath,
                emptyList(),
                toolConfig,
                proguardConfig,
                mainDexConfig,
                resourceShrinkingConfig = null,
                NoOpMessageReceiver(),
                featureClassJars = listOf(),
                featureJavaResourceJars = listOf(),
                featureDexDir = null,
                featureJavaResourceOutputDir = null
            )
        } catch (ignored: CompilationFailedException) {
            assertThat(missingRules).containsAllOf("-dontwarn test.B", "-dontwarn test.C")
        }
    }

    private fun getDexFileCount(dir: Path): Long =
        Files.list(dir).filter { it.toString().endsWith(".dex") }.count()

    companion object {
        val bootClasspath =
            listOf(TestUtils.resolvePlatformPath("android.jar", TestUtils.TestType.AGP))
    }
}
