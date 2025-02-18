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

package com.android.build.gradle.integration.api;

import static com.android.build.gradle.internal.scope.InternalArtifactType.COMPILE_LIBRARY_CLASSES_JAR;
import static com.android.testutils.truth.DexSubject.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;
import static com.android.testutils.truth.ZipFileSubject.assertThat;

import com.android.build.gradle.integration.common.category.SmokeTests;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.truth.ScannerSubjectUtils;
import com.android.build.gradle.integration.common.truth.TruthHelper;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.Dex;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth8;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import kotlin.Unit;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** test to verify the hook to register custom pre-javac compilers */
@Category(SmokeTests.class)
public class BytecodeGenerationHooksTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("bytecodeGenerationHooks").create();

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void buildApp() throws Exception {
        GradleBuildResult result = project.executor().run("clean", "app:assembleDebug");

        // check that the app's dex file contains the App class.
        Apk apk = project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk.getFile()).isFile();
        Optional<Dex> dexOptional = apk.getMainDexFile();
        Truth8.assertThat(dexOptional).isPresent();
        //noinspection OptionalGetWithoutIsPresent
        final Dex dexFile = dexOptional.get();
        assertThat(dexFile).containsClasses("Lcom/example/bytecode/App;");
        assertThat(dexFile).containsClasses("Lcom/example/bytecode/PostJavacApp;");

        // also verify that the kotlin module files are present in the intermediate classes.jar
        // published by the library

        File classesJar =
                project.file(
                        "library/build/intermediates/"
                                + COMPILE_LIBRARY_CLASSES_JAR.INSTANCE.getFolderName()
                                + "/debug/bundleLibCompileToJarDebug/classes.jar");
        assertThat(classesJar).isFile();
        assertThat(
                classesJar,
                it -> {
                    it.contains("META-INF/lib.kotlin_module");
                    it.contains("META-INF/post-lib.kotlin_module");
                });

        File resDir =
                project.file("library/build/intermediates/java_res/debug/processDebugJavaRes/out");
        assertThat(resDir).exists();
        assertThat(FileUtils.join(resDir, "META-INF", "lib.kotlin_module")).isFile();
        assertThat(FileUtils.join(resDir, "META-INF", "post-lib.kotlin_module")).isFile();

        // verify the compile classpath
        checkDependencies(
                result,
                "BytecodeGeneratingTask(:app:generateBytecodeFordebug): ",
                true,
                "library/build/intermediates/"
                        + COMPILE_LIBRARY_CLASSES_JAR.INSTANCE.getFolderName()
                        + "/debug/bundleLibCompileToJarDebug/classes.jar",
                "jar/build/libs/jar.jar");

        // verify source folders
        checkSourceFolders(result, "SourceFoldersApi(:app:debug): ", "app/src/custom/java");
    }

    @Test
    public void buildAppTest() throws Exception {
        GradleBuildResult result = project.executor().run("clean", "app:assembleAndroidTest");

        final GradleTestProject appProject = project.getSubproject("app");

        Apk apk = appProject.getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG);
        assertThat(apk.getFile()).isFile();

        // also verify that the app's jar used by test compilation contains the kotlin module files
        File classesJar =
                appProject.getIntermediateFile(
                        InternalArtifactType.COMPILE_APP_CLASSES_JAR.INSTANCE.getFolderName()
                                + "/debug/bundleDebugClassesToCompileJar/classes.jar");
        assertThat(classesJar).isFile();
        assertThat(
                classesJar,
                it -> {
                    it.contains("META-INF/app.kotlin_module");
                    it.contains("META-INF/post-app.kotlin_module");
                });

        // verify the compile classpath
        checkDependencies(
                result,
                "BytecodeGeneratingTask(:app:generateBytecodeFordebugAndroidTest): ",
                true,
                "app/build/intermediates/"
                        + InternalArtifactType.COMPILE_APP_CLASSES_JAR.INSTANCE.getFolderName()
                        + "/debug/bundleDebugClassesToCompileJar/classes.jar",
                "library/build/intermediates/"
                        + COMPILE_LIBRARY_CLASSES_JAR.INSTANCE.getFolderName()
                        + "/debug/bundleLibCompileToJarDebug/classes.jar",
                "jar/build/libs/jar.jar");
    }


    @Test
    public void buildAppUnitTest() throws IOException, InterruptedException {
        GradleBuildResult result =
                project.executor()
                        .run("clean", "app:testDebugUnitTest");

        // verify the compile classpath
        checkDependencies(
                result,
                "BytecodeGeneratingTask(:app:generateBytecodeFordebugUnitTest): ",
                false,
                "app/build/intermediates/"
                        + InternalArtifactType.COMPILE_APP_CLASSES_JAR.INSTANCE.getFolderName()
                        + "/debug/bundleDebugClassesToCompileJar/classes.jar",
                "library/build/intermediates/"
                        + COMPILE_LIBRARY_CLASSES_JAR.INSTANCE.getFolderName()
                        + "/debug/bundleLibCompileToJarDebug/classes.jar",
                "jar/build/libs/jar.jar");
    }

    @Test
    public void buildLibrary() throws RuntimeException {
        project.execute("clean", "lib:assembleDebug");

        project.getSubproject("library")
                .getAar(
                        "debug",
                        it -> {
                            try {
                                assertThat(
                                        Objects.requireNonNull(it.getEntryAsFile("classes.jar")),
                                        classes -> {
                                            classes.contains("com/example/bytecode/Lib.class");
                                            classes.contains(
                                                    "com/example/bytecode/PostJavacLib.class");
                                            classes.contains("META-INF/lib.kotlin_module");
                                            classes.contains("META-INF/post-lib.kotlin_module");
                                        });
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
    }

    @Test
    public void buildLibTest() throws IOException, InterruptedException {
        GradleBuildResult result =
                project.executor()
                        .run("clean", "lib:assembleAndroidTest");

        // verify the compile classpath
        checkDependencies(
                result,
                "BytecodeGeneratingTask(:library:generateBytecodeFordebugAndroidTest): ",
                true,
                "library/build/intermediates/"
                        + COMPILE_LIBRARY_CLASSES_JAR.INSTANCE.getFolderName()
                        + "/debug/bundleLibCompileToJarDebug/classes.jar");
    }

    @Test
    public void buildLibUnitTest() throws IOException, InterruptedException {
        project.execute("clean", "lib:testDebugUnitTest");
    }

    @Test
    public void buildTestApp() throws IOException, InterruptedException {
        GradleBuildResult result = project.executor().run("clean", "test:assembleDebug");

        // verify the compile classpath
        checkDependencies(
                result,
                "BytecodeGeneratingTask(:test:generateBytecodeFordebug): ",
                true,
                "app/build/intermediates/"
                        + InternalArtifactType.COMPILE_APP_CLASSES_JAR.INSTANCE.getFolderName()
                        + "/debug/bundleDebugClassesToCompileJar/classes.jar",
                "jar/build/libs/jar.jar",
                "library/build/intermediates/"
                        + COMPILE_LIBRARY_CLASSES_JAR.INSTANCE.getFolderName()
                        + "/debug/bundleLibCompileToJarDebug/classes.jar");
    }

    private void checkDependencies(
            GradleBuildResult result, String prefix, boolean exactly, String... dependencies) {
        String projectDir = project.getProjectDir().getAbsolutePath();

        ImmutableList.Builder<String> listBuilder = new ImmutableList.Builder<>();
        ScannerSubjectUtils.forEachLine(
                result.getStdout(),
                line -> {
                    if (line.startsWith(prefix)) {
                        String s = line.substring(prefix.length());
                        if (s.contains(projectDir)) {
                            listBuilder.add(s);
                        }
                    }
                    return Unit.INSTANCE;
                });
        List<String> lines = listBuilder.build();

        List<String> deps =
                Arrays.stream(dependencies)
                        .map(
                                s ->
                                        new File(projectDir, FileUtils.toSystemDependentPath(s))
                                                .getAbsolutePath())
                        .collect(Collectors.toList());

        if (exactly) {
            TruthHelper.assertThat(lines).containsExactlyElementsIn(deps);
        } else {
            TruthHelper.assertThat(lines).containsAtLeastElementsIn(deps);
        }
    }

    private void checkSourceFolders(
            GradleBuildResult result, String prefix, String... dependencies) {
        ImmutableList.Builder<String> listBuilder = new ImmutableList.Builder<String>();
        ScannerSubjectUtils.forEachLine(
                result.getStdout(),
                it -> {
                    if (it.startsWith(prefix)) {
                        listBuilder.add(it.substring(prefix.length()));
                    }
                    return Unit.INSTANCE;
                });
        List<String> lines = listBuilder.build();

        File projectDir = project.getProjectDir();

        List<String> deps =
                Arrays.stream(dependencies)
                        .map(
                                s ->
                                        new File(projectDir, FileUtils.toSystemDependentPath(s))
                                                .getAbsolutePath())
                        .collect(Collectors.toList());

        TruthHelper.assertThat(lines).containsAtLeastElementsIn(deps);
    }
}
