/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency

import com.android.testutils.TestInputsGenerator.jarWithClasses
import com.android.testutils.TestInputsGenerator.jarWithEmptyClasses
import com.android.testutils.TestInputsGenerator.jarWithEmptyEntries
import com.android.testutils.truth.ZipFileSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.URLClassLoader
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.assertFailsWith

class AarToClassTransformTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun collidingJavaResources_areIgnored() {
        val aar = temporaryFolder.newFile("example.aar")

        ZipOutputStream(aar.outputStream().buffered()).use { zos ->
            zos.putNextEntry(ZipEntry("classes.jar"))
            zos.write(jarWithEmptyEntries(listOf("collidingResource", "a/otherResource")))
            zos.putNextEntry(ZipEntry("libs/classes.jar"))
            zos.write(jarWithEmptyEntries(listOf("collidingResource")))
            zos.putNextEntry(ZipEntry("libs/other.jar"))
            zos.write(jarWithEmptyClasses(listOf("com/example/MyClass")))
        }

        val outputJar = temporaryFolder.newFolder().toPath().resolve("output.jar")

        ZipFile(aar).use { inputAar ->
            AarToClassTransform.mergeJars(
                outputJar = outputJar,
                inputAar = inputAar,
                forCompileUse = true,
                generateRClassJar = false
            )
        }

        assertThat(outputJar) { zip ->
            zip.contains("collidingResource")
            zip.contains("com/example/MyClass.class")
            zip.contains("a/otherResource")
        }
    }

    @Test
    fun collidingClasses_givesError() {
        val aar = temporaryFolder.newFile("example.aar")

        ZipOutputStream(aar.outputStream().buffered()).use { zos ->
            zos.putNextEntry(ZipEntry("classes.jar"))
            zos.write(jarWithEmptyClasses(listOf()))
            zos.putNextEntry(ZipEntry("libs/classes.jar"))
            zos.write(jarWithEmptyClasses(listOf("com/example/MyClass")))
            zos.putNextEntry(ZipEntry("libs/classes2.jar"))
            zos.write(jarWithEmptyClasses(listOf("com/example/MyClass")))
        }

        val outputJar = temporaryFolder.newFolder().toPath().resolve("output.jar")

        ZipFile(aar).use { inputAar ->
            val exception = assertFailsWith<IllegalStateException> {
                AarToClassTransform.mergeJars(
                    outputJar = outputJar,
                    inputAar = inputAar,
                    forCompileUse = true,
                    generateRClassJar = false
                )
            }
            assertThat(exception).hasMessageThat().contains("com/example/MyClass.class")
        }
    }

    @Test
    fun testOutputJarIsNotCompressed() {
        val aar = temporaryFolder.newFile("example.aar")

        ZipOutputStream(aar.outputStream().buffered()).use { zos ->
            zos.putNextEntry(ZipEntry("classes.jar"))
            zos.write(jarWithClasses(listOf(CompressibleClass::class.java)))
        }

        val outputJar = temporaryFolder.newFolder().toPath().resolve("output.jar")

        ZipFile(aar).use { inputAar ->
            AarToClassTransform.mergeJars(
                outputJar = outputJar,
                inputAar = inputAar,
                forCompileUse = true,
                generateRClassJar = false
            )
        }

        ZipFile(outputJar.toFile()).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                assertThat(entry.compressedSize).isAtLeast(entry.size)
            }
        }
    }

    @Test
    fun testRClassGeneration() {
        val aar = temporaryFolder.newFile("example.aar")

        ZipOutputStream(aar.outputStream().buffered()).use { zos ->
            zos.putNextEntry(ZipEntry("classes.jar"))
            zos.write(jarWithClasses(listOf(CompressibleClass::class.java)))
            zos.putNextEntry(ZipEntry("libs/myclasses.jar"))
            zos.write(jarWithEmptyClasses((1..500).map { "com/example/MyClass$it" }))
            zos.putNextEntry(ZipEntry("R.txt"))
            zos.write(this::class.java.getResource("large_R.txt")!!.readText().toByteArray())
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write(this::class.java.getResource("large_AndroidManifest.xml")!!.readText().toByteArray())
        }

        val outputJar = temporaryFolder.newFolder().toPath().resolve("output.jar")

        ZipFile(aar).use { inputAar ->
            AarToClassTransform.mergeJars(
                outputJar = outputJar,
                inputAar = inputAar,
                forCompileUse = true,
                generateRClassJar = true
            )
        }
        // Check classes and R classes copied
        ZipFile(outputJar.toFile()).use { zip ->
            val entries = zip.entries()
            var count = 0
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                assertThat(entry.compressedSize).isAtLeast(entry.size)
                count++
            }
            val e = zip.getEntry("p1/p2/R.class")
            assertThat(e.size).isGreaterThan(0)
            assertThat(count).isEqualTo(514)
        }
        // Check a generated R class subclass is loadable
        URLClassLoader(arrayOf(outputJar.toUri().toURL())).use { classloader ->
            val strings = classloader.loadClass("p1.p2.R${'$'}string")
            assertThat(strings.fields).hasLength(33)
        }

    }
}

private class CompressibleClass() {
    val foo = "foo"
    val bar = "bar"
    fun fooBar() = foo + bar
}
