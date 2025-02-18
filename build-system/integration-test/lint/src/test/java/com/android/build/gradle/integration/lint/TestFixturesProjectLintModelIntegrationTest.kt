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

package com.android.build.gradle.integration.lint

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.utils.FileUtils
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class TestFixturesProjectLintModelIntegrationTest(private val lintAnalysisPerComponent: Boolean) {

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder().fromTestProject("testFixturesApp").create()

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "lintAnalysisPerComponent_{0}")
        fun params() = listOf(true, false)
    }

    private fun setUpProject(publishJavaLib: Boolean, publishAndroidLib: Boolean) {
        project.getSubproject(":app").buildFile.appendText(
            """
                android {
                    testBuildType "release"
                    lint {
                        disable "GradleDependency"
                        enable 'StopShip'
                        abortOnError false
                        checkDependencies true
                    }
                }
            """.trimIndent()
        )
        TestFileUtils.searchAndReplace(
            project.getSubproject(":app")
                .file("src/testFixtures/java/com/example/app/testFixtures/AppInterfaceTester.java"),
            "public class AppInterfaceTester {",
            "// STOPSHIP\n" + "public class AppInterfaceTester {"
        )

        project.getSubproject(":lib").buildFile.appendText(
            """
                android {
                    testBuildType "release"
                }
                dependencies {
                    testFixturesApi 'androidx.annotation:annotation:1.1.0'
                }
            """.trimIndent()
        )
        TestFileUtils.searchAndReplace(
            project.getSubproject(":lib")
                .file("src/testFixtures/java/com/example/lib/testFixtures/LibResourcesTester.java"),
            "public void test() {",
            """
                @androidx.annotation.RequiresPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                public void methodWithUnavailablePermission() {
                }

                public void test() {
                    methodWithUnavailablePermission();
            """.trimIndent()
        )
        TestFileUtils.appendToFile(project.file("gradle.properties"), "android.useAndroidX=true")

        if (publishJavaLib) {
            TestFileUtils.searchAndReplace(
                project.getSubproject(":app").buildFile,
                "project(\":javaLib\")",
                "'com.example.javaLib:javaLib:1.0'"
            )
        }

        if (publishAndroidLib) {
            TestFileUtils.searchAndReplace(
                project.getSubproject(":app").buildFile,
                "project(\":lib\")",
                "'com.example.lib:lib:1.0'"
            )

            TestFileUtils.searchAndReplace(
                project.getSubproject(":lib2").buildFile,
                "project(\":lib\")",
                "'com.example.lib:lib:1.0'"
            )
        }

        if (publishJavaLib) {
            TestFileUtils.appendToFile(project.getSubproject(":javaLib").buildFile,
                """
                publishing {
                    repositories {
                        maven {
                            url = uri("../testrepo")
                        }
                    }
                    publications {
                        release(MavenPublication) {
                            from components.java
                            groupId = 'com.example.javaLib'
                            artifactId = 'javaLib'
                            version = '1.0'
                        }
                    }
                }

                // required for testFixtures publishing
                group = 'com.example.javaLib'
            """.trimIndent()
            )
        }

        if (publishAndroidLib) {
            TestFileUtils.appendToFile(project.getSubproject(":lib").buildFile,
                """
                android {
                    publishing {
                        singleVariant("release")
                    }
                }

                afterEvaluate {
                    publishing {
                        repositories {
                            maven {
                                url = uri("../testrepo")
                            }
                        }
                        publications {
                            release(MavenPublication) {
                                from components.release
                                groupId = 'com.example.lib'
                                artifactId = 'lib'
                                version = '1.0'
                            }
                        }
                    }
                }

                // required for testFixtures publishing
                group = 'com.example.lib'
            """.trimIndent()
            )
        }

        if (publishJavaLib) {
            project.executor()
                .run(":javaLib:publish")
        }

        if (publishAndroidLib) {
            project.executor()
                .run(":lib:publish")
        }
    }

    @Test
    fun `check lint model for project with local and library module testFixtures`() {
        setUpProject(
            publishJavaLib = false,
            publishAndroidLib = false,
        )
        getExecutor().run(":app:lintRelease")
        if (lintAnalysisPerComponent) {
            checkLintModels(
                project = project,
                lintModelDir = project.getSubproject("app").intermediatesDir.toPath()
                    .resolve("incremental/lintAnalyzeReleaseTestFixtures"),
                modelSnapshotResourceRelativePath = "perComponent/testFixturesApp/localTestFixtures",
                "release-artifact-dependencies.xml",
                "release-artifact-libraries.xml",
                "release.xml",
                "module.xml",
            )
        } else {
            checkLintModels(
                project = project,
                lintModelDir = project.getSubproject("app").intermediatesDir.toPath()
                    .resolve("lint_report_lint_model/release/generateReleaseLintReportModel"),
                modelSnapshotResourceRelativePath = "testFixturesApp/localTestFixtures",
                "release-androidTestArtifact-dependencies.xml",
                "release-androidTestArtifact-libraries.xml",
                "release-artifact-dependencies.xml",
                "release-artifact-libraries.xml",
                "release-testArtifact-dependencies.xml",
                "release-testArtifact-libraries.xml",
                "release-testFixturesArtifact-dependencies.xml",
                "release-testFixturesArtifact-libraries.xml",
                "release.xml",
                "module.xml",
            )
        }
    }

    @Test
    fun `check lint model for project with local and external library testFixtures`() {
        setUpProject(
            publishJavaLib = true,
            publishAndroidLib = true,
        )
        getExecutor().run(":app:lintRelease")
        if (lintAnalysisPerComponent) {
            checkLintModels(
                project = project,
                lintModelDir = project.getSubproject("app").intermediatesDir.toPath()
                    .resolve("incremental/lintAnalyzeReleaseTestFixtures"),
                modelSnapshotResourceRelativePath = "perComponent/testFixturesApp/publishedTestFixtures",
                "release-artifact-dependencies.xml",
                "release-artifact-libraries.xml",
                "release.xml",
                "module.xml",
            )
        } else {
            checkLintModels(
                project = project,
                lintModelDir = project.getSubproject("app").intermediatesDir.toPath()
                    .resolve("lint_report_lint_model/release/generateReleaseLintReportModel"),
                modelSnapshotResourceRelativePath = "testFixturesApp/publishedTestFixtures",
                "release-androidTestArtifact-dependencies.xml",
                "release-androidTestArtifact-libraries.xml",
                "release-artifact-dependencies.xml",
                "release-artifact-libraries.xml",
                "release-testArtifact-dependencies.xml",
                "release-testArtifact-libraries.xml",
                "release-testFixturesArtifact-dependencies.xml",
                "release-testFixturesArtifact-libraries.xml",
                "release.xml",
                "module.xml",
            )
        }
    }

    private fun getExecutor(): GradleTaskExecutor {
        return project.executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
            .with(BooleanOption.LINT_ANALYSIS_PER_COMPONENT, lintAnalysisPerComponent)
    }
}
