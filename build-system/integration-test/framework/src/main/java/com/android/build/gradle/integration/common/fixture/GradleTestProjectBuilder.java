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

package com.android.build.gradle.integration.common.fixture;

import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_COMPILE_SDK_VERSION;
import static com.android.build.gradle.integration.common.fixture.GradleTestProject.DEFAULT_TEST_PROJECT_NAME;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.BazelIntegrationTestsSuite;
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile;
import com.android.build.gradle.integration.common.utils.SdkHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.testutils.MavenRepoGenerator;
import com.android.testutils.TestUtils;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class GradleTestProjectBuilder implements GradleOptionBuilder<GradleTestProjectBuilder> {

    public static final Path DEFAULT_PROFILE_DIR = Paths.get("build", "android-profile");

    @Nullable private String name;
    @Nullable private String rootProjectName = null;
    @Nullable private TestProject testProject = null;
    @Nullable private String targetGradleVersion = null;
    @Nullable private File targetGradleInstallation = null;
    @Nullable private String compileSdkVersion;

    @NonNull
    private List<String> gradleProperties =
            Lists.newArrayList(
                    "org.gradle.java.installations.paths="
                            + TestUtils.getJava17Jdk().toString().replace("\\", "/"));

    @Nullable private Path profileDirectory;
    private boolean withDependencyChecker = true;
    // Indicates if we need to create a project without setting cmake.dir in local.properties.
    private boolean withCmakeDirInLocalProp = false;
    // NDK symlink path is relative to the test's BUILD_DIR. A full path example of
    // this is like this on bazel:
    // /private/var/tmp/_bazel/cd7382de6c57d974eabcf5c1270266ca/sandbox
    //   /darwin-sandbox/9/execroot/__main__/_tmp/85abb3fc831caa67a0715d2cf3ce5967/
    // The resulting symlink path is always in the form */ndk/
    private String ndkSymlinkPath = ".";
    @Nullable String cmakeVersion;
    @Nullable private File androidSdkDir;
    @Nullable private File androidNdkDir;
    @Nullable private String sideBySideNdkVersion = null;
    @Nullable private File gradleDistributionDirectory;
    @Nullable private File gradleBuildCacheDirectory;
    @Nullable private String kotlinVersion;

    private boolean withSdk = true;
    private boolean withAndroidGradlePlugin = true;
    @Nullable
    private String withExtraPluginClasspath;
    private boolean withKotlinGradlePlugin = false;
    private boolean withKspGradlePlugin = false;

    private boolean withAndroidxPrivacySandboxLibraryPlugin = false;
    private boolean withBuiltInKotlinSupport = false;
    private boolean withPluginManagementBlock = false;
    private boolean withDependencyManagementBlock = true;
    // list of included builds, relative to the main projectDir
    private List<String> withIncludedBuilds = Lists.newArrayList();

    /** Whether or not to output the log of the last build result when a test fails */
    private boolean outputLogOnFailure = true;
    private MavenRepoGenerator additionalMavenRepo;

    /** Create a GradleTestProject. */
    @NonNull
    public GradleTestProject create() {

        // Use a specific Jdk to run Gradle, which might be different from the one running the test
        // class
        String jdkVersionForGradle = System.getProperty("gradle.java.version");
        if (jdkVersionForGradle != null && jdkVersionForGradle.equals("17")) {
            gradleProperties.add(
                    "org.gradle.java.home="
                            + TestUtils.getJava17Jdk().toString().replace("\\", "/"));
        }

        if (androidSdkDir == null && withSdk) {
            androidSdkDir = SdkHelper.findSdkDir();
        }

        if (androidNdkDir == null) {
            String envCustomAndroidNdkHome =
                    Strings.emptyToNull(System.getenv().get("CUSTOM_ANDROID_NDK_ROOT"));
            if (envCustomAndroidNdkHome != null) {
                androidNdkDir = new File(envCustomAndroidNdkHome);
                Preconditions.checkState(
                        androidNdkDir.isDirectory(),
                        "CUSTOM_ANDROID_NDK_ROOT must point to a directory, %s is not a directory",
                        androidNdkDir.getAbsolutePath());
            } else {
                if (sideBySideNdkVersion != null) {
                    androidNdkDir =
                            TestUtils.runningFromBazel()
                                    ? new File(
                                            BazelIntegrationTestsSuite.NDK_SIDE_BY_SIDE_ROOT
                                                    .toFile(),
                                            sideBySideNdkVersion)
                                    : new File(
                                            new File(
                                                    androidSdkDir,
                                                    SdkConstants.FD_NDK_SIDE_BY_SIDE),
                                            sideBySideNdkVersion);
                } else {
                    androidNdkDir =
                            TestUtils.runningFromBazel()
                                    ? BazelIntegrationTestsSuite.NDK_IN_TMP.toFile()
                                    : new File(androidSdkDir, SdkConstants.FD_NDK);
                }
            }
        }

        if (gradleDistributionDirectory == null) {
            gradleDistributionDirectory =
                    TestUtils.resolveWorkspacePath("tools/external/gradle").toFile();
        }

        if (kotlinVersion == null) {
            kotlinVersion = TestUtils.KOTLIN_VERSION_FOR_TESTS;
        }

        return new GradleTestProject(
                (name != null ? name : DEFAULT_TEST_PROJECT_NAME),
                rootProjectName,
                testProject,
                targetGradleVersion,
                targetGradleInstallation,
                withDependencyChecker,
                gradleOptionDelegate.getAsGradleOptions(),
                gradleProperties,
                (compileSdkVersion != null ? compileSdkVersion : DEFAULT_COMPILE_SDK_VERSION),
                profileDirectory,
                cmakeVersion,
                withCmakeDirInLocalProp,
                ndkSymlinkPath,
                GradleTestProject.APPLY_DEVICEPOOL_PLUGIN,
                withSdk,
                withAndroidGradlePlugin,
                withKotlinGradlePlugin,
                withKspGradlePlugin,
                withAndroidxPrivacySandboxLibraryPlugin,
                withExtraPluginClasspath,
                withBuiltInKotlinSupport,
                withPluginManagementBlock,
                withDependencyManagementBlock,
                withIncludedBuilds,
                null,
                additionalMavenRepo,
                androidSdkDir,
                androidNdkDir,
                gradleDistributionDirectory,
                gradleBuildCacheDirectory,
                kotlinVersion,
                outputLogOnFailure);
    }

    public GradleTestProjectBuilder withAdditionalMavenRepo(
            @Nullable MavenRepoGenerator mavenRepo) {
        additionalMavenRepo = mavenRepo;
        return this;
    }


    /**
     * Set the name of the project.
     *
     * <p>Necessary if you have multiple projects in a test class.
     */
    public GradleTestProjectBuilder withName(@NonNull String name) {
        this.name = name;
        return this;
    }

    /**
     * Set the name of the root project in settings.gradle
     *
     * <p>Necessary if you have more than one project in the same tests and you want to control the
     * name of the project while they are different folders. The name of the root project is used
     * for the capabilities of published subproject's artifacts
     */
    public GradleTestProjectBuilder withRootProjectName(@NonNull String name) {
        this.rootProjectName = name;
        return this;
    }

    public GradleTestProjectBuilder withAndroidSdkDir(File androidSdkDir) {
        this.androidSdkDir = androidSdkDir;
        return this;
    }

    public GradleTestProjectBuilder withGradleDistributionDirectory(
            File gradleDistributionDirectory) {
        this.gradleDistributionDirectory = gradleDistributionDirectory;
        return this;
    }

    /**
     * Sets a custom directory for the Gradle build cache (not the Android Gradle build cache). The
     * path can be absolute or relative to projectDir.
     */
    public GradleTestProjectBuilder withGradleBuildCacheDirectory(
            @NonNull File gradleBuildCacheDirectory) {
        this.gradleBuildCacheDirectory = gradleBuildCacheDirectory;
        return this;
    }

    public GradleTestProjectBuilder setTargetGradleVersion(@Nullable String targetGradleVersion) {
        this.targetGradleVersion = targetGradleVersion;
        return this;
    }

    public GradleTestProjectBuilder setTargetGradleInstallation(@Nullable File targetGradleInstallation) {
        this.targetGradleInstallation = targetGradleInstallation;
        return this;
    }

    public GradleTestProjectBuilder withKotlinVersion(String kotlinVersion) {
        this.kotlinVersion = kotlinVersion;
        return this;
    }

    public GradleTestProjectBuilder withPluginManagementBlock(boolean withPluginManagementBlock) {
        this.withPluginManagementBlock = withPluginManagementBlock;
        return this;
    }

    public GradleTestProjectBuilder withDependencyManagementBlock(
            boolean withDependencyManagementBlock) {
        this.withDependencyManagementBlock = withDependencyManagementBlock;
        return this;
    }

    public GradleTestProjectBuilder withSdk(boolean withSdk) {
        this.withSdk = withSdk;
        return this;
    }

    public GradleTestProjectBuilder withAndroidGradlePlugin(boolean withAndroidGradlePlugin) {
        this.withAndroidGradlePlugin = withAndroidGradlePlugin;
        return this;
    }

    public GradleTestProjectBuilder withExtraPluginClasspath(String classpath) {
        this.withExtraPluginClasspath = classpath;
        return this;
    }

    public GradleTestProjectBuilder withBuiltInKotlinSupport(boolean withBuiltInKotlinSupport) {
        this.withBuiltInKotlinSupport = withBuiltInKotlinSupport;
        return this;
    }

    public GradleTestProjectBuilder withKotlinGradlePlugin(boolean withKotlinGradlePlugin) {
        this.withKotlinGradlePlugin = withKotlinGradlePlugin;
        return this;
    }

    public GradleTestProjectBuilder withKspGradlePlugin(boolean withKspGradlePlugin) {
        this.withKspGradlePlugin = withKspGradlePlugin;
        return this;
    }

    public GradleTestProjectBuilder withAndroidxPrivacySandboxLibraryPlugin(boolean withAndroidxPrivacySandboxLibraryPlugin) {
        this.withAndroidxPrivacySandboxLibraryPlugin = withAndroidxPrivacySandboxLibraryPlugin;
        return this;
    }

    public GradleTestProjectBuilder withIncludedBuilds(String relativePath) {
        withIncludedBuilds.add(relativePath);
        return this;
    }

    public GradleTestProjectBuilder withIncludedBuilds(String... relativePaths) {
        withIncludedBuilds.addAll(Arrays.asList(relativePaths));
        return this;
    }

    /** Create GradleTestProject from a TestProject. */
    public GradleTestProjectBuilder fromTestApp(@NonNull TestProject testProject) {
        this.testProject = testProject;
        return this;
    }

    /** Create GradleTestProject from an existing test project. */
    public GradleTestProjectBuilder fromTestProject(@NonNull String project) {
        GradleProject app = new EmptyTestApp();
        if (name == null) {
            name = project;
        }

        File projectDir = TestProjectPaths.getTestProjectDir(project);
        addAllFiles(app, projectDir);
        return fromTestApp(app);
    }

    public GradleTestProjectBuilder fromDir(@NonNull File dir) {
        Preconditions.checkArgument(
                dir.isDirectory(), "%s is not a directory", dir.getAbsolutePath());
        GradleProject app = new EmptyTestApp();
        addAllFiles(app, dir);
        return fromTestApp(app);
    }

    /** Create GradleTestProject from a data binding integration test. */
    public GradleTestProjectBuilder fromDataBindingIntegrationTest(
            @NonNull String project, boolean useAndroidX) {
        GradleProject app = new EmptyTestApp();
        name = project;
        // compute the root folder of the checkout, based on test-projects.
        String suffix = useAndroidX ? "" : "-support";
        File parentDir =
                TestUtils.resolveWorkspacePath("tools/data-binding/integration-tests" + suffix)
                        .toFile();

        File projectDir = new File(parentDir, project);
        if (!projectDir.exists()) {
            throw new RuntimeException("Project " + project + " not found in " + projectDir + ".");
        }
        addAllFiles(app, projectDir);
        return fromTestApp(app);
    }

    /** Add a new file to the project. */
    public GradleTestProjectBuilder addFile(@NonNull TestSourceFile file) {
        return addFiles(Lists.newArrayList(file));
    }

    /** Add a new file to the project. */
    public GradleTestProjectBuilder addFiles(@NonNull List<TestSourceFile> files) {
        if (!(this.testProject instanceof GradleProject)) {
            throw new IllegalStateException("addFile is only for GradleProject");
        }
        GradleProject app = (GradleProject) this.testProject;
        for (TestSourceFile file : files) {
            app.addFile(file);
        }
        return this;
    }

    /** Add gradle properties. */
    public GradleTestProjectBuilder addGradleProperties(@NonNull String property) {
        gradleProperties.add(property);
        return this;
    }

    /** Adds a Gradle property that is a [BooleanOption]. */
    @NonNull
    public GradleTestProjectBuilder addGradleProperty(
            @NonNull BooleanOption booleanOption, boolean value) {
        addGradleProperties(booleanOption.getPropertyName() + "=" + value);
        return this;
    }

    public GradleTestProjectBuilder withDependencyChecker(
            boolean dependencyChecker) {
        this.withDependencyChecker = dependencyChecker;
        return this;
    }

    public GradleTestProjectBuilder withCompileSdkVersion(@Nullable String compileSdkVersion) {
        this.compileSdkVersion = compileSdkVersion;
        return this;
    }

    public GradleTestProjectBuilder dontOutputLogOnFailure() {
        this.outputLogOnFailure = false;
        return this;
    }

    public GradleTestProjectBuilder setSideBySideNdkVersion(String sideBySideNdkVersion) {
        this.sideBySideNdkVersion = sideBySideNdkVersion;
        return this;
    }

    /**
     * Enable profile output generation. Typically used in benchmark tests. By default, places the
     * outputs in build/android-profile.
     */
    public GradleTestProjectBuilder enableProfileOutput() {
        this.profileDirectory = DEFAULT_PROFILE_DIR;
        return this;
    }

    /** Enables setting cmake.dir in local.properties */
    public GradleTestProjectBuilder setWithCmakeDirInLocalProp(boolean withCmakeDirInLocalProp) {
        this.withCmakeDirInLocalProp = withCmakeDirInLocalProp;
        return this;
    }

    /** Enables setting ndk.symlinkdir in local.properties */
    public GradleTestProjectBuilder setWithNdkSymlinkDirInLocalProp(String ndkSymlinkPath) {
        this.ndkSymlinkPath = ndkSymlinkPath;
        return this;
    }

    /** Sets the cmake version to use */
    public GradleTestProjectBuilder setCmakeVersion(@NonNull String cmakeVersion) {
        this.cmakeVersion = cmakeVersion;
        return this;
    }

    private final GradleOptionBuilderDelegate gradleOptionDelegate =
            new GradleOptionBuilderDelegate(null);

    @Override
    public GradleTestProjectBuilder withHeap(@Nullable String heapSize) {
        gradleOptionDelegate.withHeap(heapSize);
        return this;
    }

    @Override
    public GradleTestProjectBuilder withMetaspace(@Nullable String metaspaceSize) {
        gradleOptionDelegate.withMetaspace(metaspaceSize);
        return this;
    }

    @Override
    public GradleTestProjectBuilder withConfigurationCaching(
            @NonNull BaseGradleExecutor.ConfigurationCaching configurationCaching) {
        gradleOptionDelegate.withConfigurationCaching(configurationCaching);
        return this;
    }

    private static class EmptyTestApp extends GradleProject {
        @Override
        public boolean containsFullBuildScript() {
            return true;
        }
    }

    /** Add all files in a directory to an GradleProject. */
    private static void addAllFiles(GradleProject app, File projectDir) {
        try {
            for (String filePath : TestFileUtils.listFiles(projectDir.toPath())) {
                app.addFile(
                        new TestSourceFile(
                                filePath, Files.toByteArray(new File(projectDir, filePath))));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


}
