/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.integration.library

import com.android.build.gradle.integration.common.fixture.DEFAULT_MIN_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.generateAarWithContent
import com.google.common.truth.Truth
import org.gradle.internal.impldep.org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.charset.Charset

class FusedLibraryTest {

    private val androidLib1 = MinimalSubProject.lib("com.example.androidLib1").also {
        it.appendToBuild("""
        dependencies {
            implementation 'junit:junit:4.12'
            implementation project(":androidLib3")
        }
        """.trimIndent())
        it.addFile(
                "src/main/res/values/strings.xml",
                """<resources>
                <string name="string_from_android_lib_1">androidLib2</string>
              </resources>"""
        )
    }
    private val androidLib2 = MinimalSubProject.lib("com.example.androidLib2")
    private val androidLib3 = MinimalSubProject.lib("com.example.androidLib3")
        .appendToBuild(
            "group = \"fusedlib\"\n" +
                    "version = \"1.0.0\"\n")

    private val mavenRepo = mavenRepo()

    fun mavenRepo() : MavenRepoGenerator {
        val remoteDepA = generateAarWithContent("com.remotedep.remoteaar.a",
            resources = mapOf("values/strings.xml" to
                    // language=XML
                    """<?xml version="1.0" encoding="utf-8"?>
                    <resources>
                    <string name="remote_b_string">Remote String from remoteaar a</string>
                    </resources>""".trimIndent().toByteArray(Charset.defaultCharset())
            ),
        )
        val remoteDepB = generateAarWithContent(
            "com.remotedep.remoteaar.b",
            resources = mapOf(
                "values/strings.xml" to
                        // language=XML
                        """<?xml version="1.0" encoding="utf-8"?>
                    <resources>
                    <string name="remote_b_string">Remote String from remoteaar b</string>
                    </resources>""".trimIndent().toByteArray(Charset.defaultCharset())
            ),
        )
        return MavenRepoGenerator(
            listOf(
                MavenRepoGenerator.Library(
                    "com.remotedep:remoteaar-a:1",
                    "aar",
                    remoteDepA,
                    "com.remotedep:remoteaar-b:1"
                ),
                MavenRepoGenerator.Library(
                    "com.remotedep:remoteaar-b:1",
                    "aar",
                    remoteDepB
                )
            )
        )
    }

    private val fusedLibrary = MinimalSubProject.fusedLibrary("com.example.fusedLib1").also {
        it.appendToBuild(
                """
                apply plugin: 'maven-publish'

                dependencies {
                    include project(":androidLib1")
                    include project(":androidLib2")
                    include 'com.remotedep:remoteaar-a:1'
                }

                androidFusedLibrary {
                    minSdk = $DEFAULT_MIN_SDK_VERSION
                }


                publishing {
                    publications {
                        release(MavenPublication) {
                            groupId = "$FUSED_LIBRARY_GROUP"
                            artifactId = "$FUSED_LIBRARY_ARTIFACT_NAME"
                            version = "$FUSED_LIBRARY_VERSION"
                            from(components["fusedLibraryComponent"])
                        }
                    }
                    repositories {
                        maven {
                            name = "myrepo"
                            url = uri(layout.buildDirectory.dir('$FUSED_LIBRARY_REPO_NAME'))
                        }
                    }
                }
                """.trimIndent()
        )
    }

    @JvmField
    @Rule
    val project = GradleTestProject.builder()
            .fromTestApp(
                    MultiModuleTestProject.builder()
                            .subproject("androidLib1", androidLib1)
                            .subproject("androidLib2", androidLib2)
                            .subproject("androidLib3", androidLib3)
                            .subproject("fusedLib1", fusedLibrary)
                            .build()
            )
            .withAdditionalMavenRepo(mavenRepo)
            .addGradleProperties("${BooleanOption.FUSED_LIBRARY_SUPPORT.propertyName}=true")
            .create()

    @Test
    fun checkAarNoPublishing() {
        executor().run(":fusedLib1:assemble")
        val fusedLib1BuildDir = project.getSubproject(":fusedLib1").buildDir
        File(fusedLib1BuildDir, "bundle/bundle.aar").also { aarFile ->
            Truth.assertThat(aarFile.exists()).isTrue()
        }
    }

    @Test
    fun checkAarPublishing() {
        val fusedLibProject = project.getSubproject(":fusedLib1")

        executor()
            .run(
                "generatePomFileForMavenPublication",
                "generateMetadataFileForMavenPublication",
                "publishReleasePublicationToMyrepoRepository"
            )
        fusedLibProject.buildDir.resolve("publications/maven")
            .also { publicationDir ->
                val pom = File(publicationDir, "pom-default.xml")
                assertExpectedPomDependencies(pom)
                Truth.assertThat(File(publicationDir, "module.json").exists()).isTrue()
            }
        fusedLibProject.buildDir.resolve(FUSED_LIBRARY_REPO_NAME).also { repoPath ->
            val publishedLibRepoDir = repoPath.resolve(
                "$FUSED_LIBRARY_GROUP/${FUSED_LIBRARY_ARTIFACT_NAME}/$FUSED_LIBRARY_VERSION"
            )
            assertThat(
                publishedLibRepoDir.resolve(
                    "$FUSED_LIBRARY_ARTIFACT_NAME-${FUSED_LIBRARY_VERSION}.aar")
                    .exists()
            ).isTrue()

            assertExpectedPomDependencies(
                publishedLibRepoDir.resolve(
                    "$FUSED_LIBRARY_ARTIFACT_NAME-${FUSED_LIBRARY_VERSION}.pom")
            )
        }
    }

    private fun assertExpectedPomDependencies(pom: File) {
        Truth.assertThat(pom.exists()).isTrue()
        val xmlMavenPomReader = MavenXpp3Reader()
        pom.inputStream().use { inStream ->
            val parsedPom = xmlMavenPomReader.read(inStream)
            assertThat(parsedPom.dependencies.map {
                "${it.groupId}:${it.artifactId}:${it.version} scope:${it.scope}"
            })
                .containsExactly(
                    "junit:junit:4.12 scope:runtime",
                    "org.hamcrest:hamcrest-core:1.3 scope:runtime",
                    "fusedlib:androidLib3:1.0.0 scope:runtime",
                    "com.remotedep:remoteaar-b:1 scope:runtime"
                )
        }
    }

    private fun executor(): GradleTaskExecutor {
        return project.executor()
    }

    companion object {
        private const val FUSED_LIBRARY_GROUP = "my-company"
        private const val FUSED_LIBRARY_ARTIFACT_NAME = "my-fused-library"
        private const val FUSED_LIBRARY_VERSION = "1.0"
        private const val FUSED_LIBRARY_REPO_NAME = "repo"
    }
}
