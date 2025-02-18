/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk;
import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainerV2;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.v2.dsl.BuildType;
import com.android.builder.model.v2.models.AndroidDsl;
import com.android.utils.FileUtils;
import com.google.common.io.Files;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.junit.Rule;
import org.junit.Test;

public class PostprocessingTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
                    .create();

    @Test
    public void model_NoSyncErrors() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.buildTypes.release { postprocessing.removeUnusedCode = true; minifyEnabled = true\n }");

        ModelContainerV2 container =
                project.modelV2().ignoreSyncIssues().fetchModels().getContainer();

        assertTrue(container.getProject().getIssues().getSyncIssues().isEmpty());
    }

    @Test
    public void model_correctValue() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.buildTypes.release { postprocessing.removeUnusedCode = true\n }");

        ModelContainerV2 container =
                project.modelV2().ignoreSyncIssues().fetchModels().getContainer();

        assertTrue(container.getProject().getIssues().getSyncIssues().isEmpty());

        AndroidDsl androidDsl = container.getProject().getAndroidDsl();
        BuildType release =
                androidDsl.getBuildTypes().stream()
                        .filter(v -> v.getName().equals("release"))
                        .findFirst()
                        .orElse(null);

        assertThat(release).named("release variant").isNotNull();
        assertThat(release.isMinifyEnabled()).named("isMinifyEnabled()").isTrue();
    }

    @Test
    public void features_oldDsl() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.buildTypes.release {\n"
                        + "minifyEnabled true\n"
                        + "proguardFiles android.getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'\n"
                        + "}\n");

        Files.asCharSink(project.file("proguard-rules.pro"), StandardCharsets.UTF_8)
                .write("-printconfiguration build/proguard-config.txt");

        project.executor().run("assembleRelease");

        String proguardConfiguration =
                Files.toString(project.file("build/proguard-config.txt"), StandardCharsets.UTF_8);

        assertThat(proguardConfiguration).contains("-dontoptimize");
        assertThat(proguardConfiguration).doesNotContain("-dontshrink");
        assertThat(proguardConfiguration).doesNotContain("-dontobfuscate");
    }

    @Test
    public void features_newDsl_removeCode() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.buildTypes.release.postprocessing {\n"
                        + "removeUnusedCode true\n"
                        + "proguardFile 'proguard-rules.pro'\n"
                        + "}\n");

        Files.asCharSink(project.file("proguard-rules.pro"), StandardCharsets.UTF_8)
                .write(
                        "-keep class com.example.helloworld.HelloWorld$DataClass\n"
                                + "-printconfiguration build/proguard-config.txt");

        TestFileUtils.addMethod(
                project.getMainSrcDir("java/com/example/helloworld/HelloWorld.java"),
                "public HelloWorld() {\n"
                        + "    new DataClass();\n"
                        + "}\n"
                        + "static class DataClass {}\n"
                        + "static class OtherClassToRemove {}\n");

        project.executor().run("assembleRelease");

        assertThatApk(project.getApk(GradleTestProject.ApkType.RELEASE))
                .doesNotContainClass("Lcom/example/helloworld/HelloWorld$OtherDataClassToRemove;");
        assertThatApk(project.getApk(GradleTestProject.ApkType.RELEASE))
                .containsClass("Lcom/example/helloworld/HelloWorld;");
        assertThatApk(project.getApk(GradleTestProject.ApkType.RELEASE))
                .containsClass("Lcom/example/helloworld/HelloWorld$DataClass;");
        assertThat(project.file("build/proguard-config.txt")).contains("-dontoptimize");
    }

    @Test
    public void features_newDsl_allOptions() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android.buildTypes.release.postprocessing {\n"
                        + "removeUnusedCode true\n"
                        + "optimizeCode true\n"
                        + "obfuscate true\n"
                        + "proguardFile 'proguard-rules.pro'\n"
                        + "}\n");

        Files.asCharSink(project.file("proguard-rules.pro"), StandardCharsets.UTF_8)
                .write("-printconfiguration build/proguard-config.txt");

        TestFileUtils.addMethod(
                project.getMainSrcDir("java/com/example/helloworld/HelloWorld.java"),
                "public HelloWorld() {\n"
                        + "    System.out.println(new DataClass());\n"
                        + "}\n"
                        + "static class DataClass {}\n"
                        + "static class OtherClassToRemove {}\n");

        project.executor().run("assembleRelease");
        assertThatApk(project.getApk(GradleTestProject.ApkType.RELEASE))
                .containsClass("Lcom/example/helloworld/HelloWorld;");
        assertThatApk(project.getApk(GradleTestProject.ApkType.RELEASE))
                .doesNotContainClass("Lcom/example/helloworld/HelloWorld$OtherDataClassToRemove;");
        assertThat(project.file("build/proguard-config.txt")).doesNotContain("-dontoptimize");
        File mappingFile =
                FileUtils.find(project.file("build/outputs/mapping"), "mapping.txt").get();
        assertThat(mappingFile).contains("com.example.helloworld.HelloWorld$DataClass");
    }

    @Test
    public void configFilesValidation_oldDefaults() throws Exception {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "android {\n"
                        + "    buildTypes.release.postprocessing {\n"
                        + "        removeUnusedCode true\n"
                        + "        proguardFile getDefaultProguardFile('proguard-android.txt')\n"
                        + "    }\n"
                        + "}\n");

        GradleBuildResult result = project.executor().expectFailure().run("assembleRelease");

        assertThat(result.getFailureMessage()).contains("proguard-android.txt ");
        assertThat(result.getFailureMessage()).contains("new postprocessing DSL");
    }
}
