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

package com.android.build.api.apiTest.kotlin

import com.android.build.api.apiTest.VariantApiBaseTest
import com.android.build.gradle.options.BooleanOption
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import kotlin.test.assertNotNull

class TransformWithDirectoriesApiTest: VariantApiBaseTest(TestType.Script) {
        @Test
        fun androidApkTransformTest() {
            given {
                tasksToInvoke.add(":app:debugUpdateApkDir")
                addModule(":app") {
                    @Suppress("RemoveExplicitTypeArguments")
                    buildFile =
                        // language=kotlin
                        """
        plugins {
                id("com.android.application")
                kotlin("android")
        }
        import org.gradle.api.DefaultTask
        import org.gradle.api.file.DirectoryProperty
        import org.gradle.api.tasks.InputDirectory
        import org.gradle.api.tasks.TaskAction
        import org.gradle.api.provider.Property
        import org.gradle.api.tasks.Internal
        import com.android.build.api.artifact.SingleArtifact
        import org.gradle.api.tasks.OutputDirectory
        import com.android.utils.appendCapitalized
        import com.android.build.api.variant.BuiltArtifactsLoader

        abstract class UpdateDirArtifactTask: DefaultTask() {
            @get: InputDirectory
            abstract val  inputDir: DirectoryProperty

            @get: OutputDirectory
            abstract val outputDir: DirectoryProperty

            @get:Internal
            abstract val builtArtifactsLoader: Property<BuiltArtifactsLoader>

            @TaskAction
            fun taskAction() {
                println("inputDir = " + inputDir.get().asFile)
                println("outputDir = " + outputDir.get().asFile)

                // load all the artifacts produced by the APK generating task
                val artifacts = builtArtifactsLoader.get().load(inputDir.get())
                // for each of them, make a new version with the same file name and some content.
                artifacts?.elements?.forEach {
                     val inputFile = File(it.outputFile)
                        println("Input file: ${'$'}inputFile")
                        File(outputDir.get().asFile, inputFile.name).writeText("fileSize = " + inputFile.length())
                }
                // finally, save the metadata json file that contains the list of APK. Since we used the same
                // file names and write out the same number of files, we can just reuse the ones we loaded.
                // if you change the file name, or any of the properties of the BuiltArtifact, you must create a new
                // version of the BuiltArtifacts.
                artifacts?.save(outputDir.get())
            }
        }

        android {
            ${testingElements.addCommonAndroidBuildLogic()}
        }
        androidComponents {
            onVariants {
                val updateArtifact = project.tasks.register<UpdateDirArtifactTask>("${'$'}{it.name}UpdateApkDir"){
                    builtArtifactsLoader.set(it.artifacts.getBuiltArtifactsLoader())
                }
                it.artifacts.use(updateArtifact)
                    .wiredWithDirectories(
                        UpdateDirArtifactTask::inputDir,
                        UpdateDirArtifactTask::outputDir)
                .toTransform(SingleArtifact.APK)
            }
        }
    """.trimIndent()
                    testingElements.addManifest(this)
                }
            }
            withOptions(mapOf(BooleanOption.ENABLE_PROFILE_JSON to true))
            check {
                assertNotNull(this)
                assertThat(output).containsMatch(
                    "inputDir = .+?/app/build/intermediates/apk/debug/packageDebug"
                )
                assertThat(output).containsMatch(
                    "outputDir = .+?/app/build/outputs/apk/debug"
                )
                val outputDirLine = output.split("\n").find { it.startsWith("outputDir =") }
                val outputDir = outputDirLine!!.substringAfter(" = ")
                assertThat(File(outputDir).isDirectory).isTrue()
                assertThat(File(outputDir).list()).isNotEmpty()
                assertThat(output).contains("BUILD SUCCESSFUL")
            }
        }
    }

