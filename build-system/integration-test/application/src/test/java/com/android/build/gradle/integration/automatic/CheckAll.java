/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.integration.automatic;

import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor.ConfigurationCaching;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder;
import com.android.build.gradle.integration.common.fixture.TestProjectPaths;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.options.BooleanOption;
import com.android.testutils.AssumeUtil;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * Test case that executes "standard" gradle tasks in all our tests projects.
 *
 * <p>You can run only one test like this:
 *
 * <p>{@code ./gradlew :base:build-system:integration-test:application:automaticTest
 * --tests=*[abiPureSplits]}
 */
@RunWith(FilterableParameterized.class)
public class CheckAll {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        List<Object[]> parameters = Lists.newArrayList();

        File[] testProjects = TestProjectPaths.getTestProjectDir().listFiles();
        checkState(testProjects != null);

        for (File testProject : testProjects) {
            if (!isValidProjectDirectory(testProject)) {
                continue;
            }

            parameters.add(new Object[] {testProject.getName()});
        }

        return parameters;
    }

    private static boolean isValidProjectDirectory(File testProject) {
        if (!testProject.isDirectory()) {
            return false;
        }
        // RenderScript support is removed after NDK r23 LTS
        if (testProject.getName().equals("renderscriptNdk")) {
            return false;
        }

        File buildGradle = new File(testProject, "build.gradle");
        File settingsGradle = new File(testProject, "settings.gradle");

        return buildGradle.exists() || settingsGradle.exists();
    }

    @Rule public GradleTestProject project;

    public CheckAll(String projectName) {
        GradleTestProjectBuilder builder =
                GradleTestProject.builder()
                        .fromTestProject(projectName)
                        .withConfigurationCaching(ConfigurationCaching.ON)
                        .addGradleProperties(
                                BooleanOption.USE_ANDROID_X.getPropertyName() + "=true");
        if (FLAKY_OOM_TESTS.contains(projectName)) {
            this.project = builder.withHeap("2048m").create();
        } else {
            this.project = builder.create();
        }
    }

    @Test
    public void assembleAndLint() throws Exception {
        AssumeUtil.assumeNotWindows(); // b/73306170
        Assume.assumeTrue(canAssemble(project));
        project.executor()
                .withEnableInfoLogging(false)
                .run("assembleDebug", "assembleAndroidTest", "lint");
    }

    private static boolean canAssemble(@NonNull GradleTestProject project) {
        return !BROKEN_ALWAYS_ASSEMBLE.contains(project.getName());
    }

    private static final ImmutableSet<String> PROJECTS_TO_RUN_WITH_FAIL_ON_WARNING_DISABLED =
            ImmutableSet.of();

    private static final ImmutableSet<String> BROKEN_ALWAYS_ASSEMBLE =
            ImmutableSet.of(
                    // These require ndk.dir, but that's deprecated
                    "ndkJniLib",
                    "vulkan",
                    "ndkSanAngeles",
                    "combinedAbiDensitySplits",

                    // Don't work with the version of the NDK that's in ndk-bundle. Enable after
                    // moving all test projects off ndk-bundle and onto ndk.
                    "prefabApp",
                    "prefabPublishing",

                    // Data binding projects are tested in
                    // tools/base/build-system/integration-test/databinding
                    "databindingIncremental",
                    "databindingAndDagger",
                    "databinding",
                    "databindingAndKotlin",
                    "databindingAndJetifier",
                    "databindingMultiModule",
                    "databindingWithDynamicFeatures",

                    // Requires ml models to be in place
                    "mlModelBinding",

                    // TODO(b/160392650): lint namespace support
                    "namespacedApp",

                    // These are all right:
                    "genFolderApi", // Has a required injectable property
                    "ndkJniPureSplitLib", // Doesn't build until externalNativeBuild {} is added.
                    "duplicateNameImport", // Fails on purpose.
                    "filteredOutBuildType", // assembleDebug does not exist as debug build type is
                    // removed.
                    "projectWithLocalDeps", // Doesn't have a build.gradle, not much to check
                    // anyway.
                    "externalBuildPlugin", // Not an Android Project.
                    "lintKotlin", // deliberately contains lint errors (missing baseline file)
                    "lintBaseline", // deliberately contains lint errors
                    "lintStandalone", // Not an Android project
                    "lintStandaloneVital", // Not an Android project
                    "lintStandaloneCustomRules", // Not an Android project
                    "lintCustomRules", // contains integ test for lint itself
                    "lintCustomLocalAndPublishRules", // contains integ test for lint itself
                    "simpleCompositeBuild", // broken composite build project.
                    "multiCompositeBuild", // too complex composite build project to setup
                    "sourceDependency", // not set up fully, just used for sync tests
                    "kotlinMultiplatform" // kotlin multiplatform project has its own assemble
                    // tests.
                    );

    // These tests have flaky OOM errors and need larger max heap (b/359524825)
    private static final ImmutableSet<String> FLAKY_OOM_TESTS =
            ImmutableSet.of(
                    "lintDeps",
                    "testFixturesKotlinApp",
                    "composeHelloWorld",
                    "lintLibrarySkipDeps",
                    "testFixturesApp",
                    "lintLibraryModel",
                    "BasicRenderScript",
                    "navigation",
                    "compileRClasses",
                    "kotlinApp");
}
