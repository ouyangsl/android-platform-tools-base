/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.BasePlugin;
import com.android.builder.model.AndroidProject;
import com.android.io.StreamException;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.internal.project.ProjectPropertiesWorkingCopy;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * JUnit4 test rule for integration test.
 *
 * This rule create a gradle project in a temporary directory.
 * It can be use with the @Rule or @ClassRule annotations.  Using this class with @Rule will create
 * a gradle project in separate directories for each unit test, whereas using it with @ClassRule
 * creates a single gradle project.
 *
 * The test directory is always deleted if it already exists at the start of the test to ensure a
 * clean environment.
 */
public class GradleTestProject implements TestRule {

    public static class Builder {
        private static final File SAMPLE_PROJECT_DIR = new File("samples");

        private static final File TEST_PROJECT_DIR = new File("test-projects");

        private String name;

        private File projectDir;

        /**
         * Create a GradleTestProject.
         */
        public GradleTestProject create()  {
            return new GradleTestProject(name, projectDir);
        }

        /**
         * Set the name of the project.
         *
         * Necessary if you have multiple projects in a test class.
         */
        public Builder withName(@NonNull String name) {
            this.name = name;
            return this;
        }

        /**
         * Create GradleTestProjectBase from an existing sample project.
         */
        public Builder fromSample(@NonNull String project) {
            projectDir = new File(SAMPLE_PROJECT_DIR, project);
            return this;
        }

        /**
         * Create GradleTestProject from an existing test project.
         */
        public Builder fromTestProject(@NonNull String project) {
            projectDir = new File(TEST_PROJECT_DIR, project);
            return this;
        }
    }

    private static final String DEFAULT_TEST_PROJECT_NAME = "project";

    public static final int DEFAULT_COMPILE_SDK_VERSION = 21;

    public static final String DEFAULT_BUILD_TOOL_VERSION = "21.0.1";

    private static final String ANDROID_GRADLE_VERSION = "0.14.3";

    private String name;

    private File outDir;

    private File testDir;

    private File sourceDir;

    private File buildFile;

    private File ndkDir;

    private File sdkDir;

    private ByteArrayOutputStream stdout = new ByteArrayOutputStream();

    @Nullable
    private File projectSourceDir;

    public GradleTestProject() {
        this(null, null);
    }

    private GradleTestProject(@Nullable String name, @Nullable File projectSourceDir) {
        sdkDir = findSdkDir();
        ndkDir = findNdkDir();
        String buildDir = System.getenv("PROJECT_BUILD_DIR");
        outDir = (buildDir == null) ? new File("build/tests") : new File(buildDir, "tests");
        this.name = (name == null) ? DEFAULT_TEST_PROJECT_NAME : name;
        this.projectSourceDir = projectSourceDir;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Recursively delete directory or file.
     *
     * @param root directory to delete
     */
    private static void deleteRecursive(File root) {
        if (root.exists()) {
            if (root.isDirectory()) {
                File files[] = root.listFiles();
                if (files != null) {
                    for (File file : files) {
                        deleteRecursive(file);
                    }
                }
            }
            assertTrue(root.delete());
        }
    }

    private static void copyRecursive(File src, File dest) {
        if (src.isDirectory()) {
            // If directory does not exists, create it
            if (!dest.exists()) {
                assertTrue(dest.mkdir());
            }

            // Recursively copy each file in directory.
            for (String file : src.list()) {
                File srcFile = new File(src, file);
                File destFile = new File(dest, file);
                copyRecursive(srcFile, destFile);
            }
        } else {
            try {
                Files.copy(src, dest);
            } catch (Exception ignore) {
            }
        }
    }

    @Override
    public Statement apply(final Statement base, Description description) {
        testDir = new File(outDir, description.getTestClass().getName());

        // Create separate directory based on test method name if @Rule is used.
        // getMethodName() is null if this rule is used as a @ClassRule.
        if (description.getMethodName() != null) {
            testDir = new File(testDir, description.getMethodName());
        }
        testDir = new File(testDir, name);

        buildFile = new File(testDir, "build.gradle");
        sourceDir = new File(testDir, "src");

        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                if (testDir.exists()) {
                    deleteRecursive(testDir);
                }
                assertTrue(testDir.mkdirs());
                assertTrue(sourceDir.mkdirs());

                if (projectSourceDir != null) {
                    copyRecursive(projectSourceDir, testDir);
                } else {
                    Files.write(
                            "buildscript {\n" +
                                    "    repositories {\n" +
                                    "        maven { url '" + getRepoDir().toString() + "' }\n" +
                                    "    }\n" +
                                    "    dependencies {\n" +
                                    "        classpath \"com.android.tools.build:gradle:" + ANDROID_GRADLE_VERSION + "\"\n" +
                                    "    }\n" +
                                    "}\n",
                            buildFile,
                            Charsets.UTF_8);
                }

                createLocalProp(testDir, sdkDir, ndkDir);
                base.evaluate();
            }
        };
    }

    /**
     * Return the name of the test project.
     */
    public String getName() {
        return name;
    }

    /**
     * Return the directory containing the test project.
     */
    public File getTestDir() {
        return testDir;
    }

    /**
     * Return the build.gradle of the test project.
     */
    public File getBuildFile() {
        return buildFile;
    }

    /**
     * Return the directory containing the source files of the test project.
     */
    public File getSourceDir() {
        return sourceDir;
    }

    /**
     * Return the output directory from Android plugins.
     */
    public File getOutputDir() {
        return new File(testDir,
                Joiner.on(File.separator).join("build", AndroidProject.FD_OUTPUTS));
    }

    public File getOutputFile(String path) {
        return new File(getOutputDir(), path);
    }

    /**
     * Return the output APK File from the application plugin for the given dimension.
     */
    public File getApk(String ... dimensions) {
        // TODO: Add overload for tests and splits.
        List<String> dimensionList = Lists.newArrayListWithExpectedSize(1 + dimensions.length);
        dimensionList.add(getName());
        dimensionList.addAll(Arrays.asList(dimensions));
        return getOutputFile("apk/" + Joiner.on("-").join(dimensionList) + ".apk");
    }

    /**
     * Returns the SDK dir
     */
    public File getSdkDir() {
        return sdkDir;
    }

    /**
     * Returns the NDK dir
     */
    public File getNdkDir() {
        return ndkDir;
    }

    /**
     * Return the directory of the repository containing the necessary plugins for testing.
     */
    private File getRepoDir() {
        CodeSource source = getClass().getProtectionDomain().getCodeSource();
        assert (source != null);
        URL location = source.getLocation();
        try {
            File dir = new File(location.toURI());
            assertTrue(dir.getPath(), dir.exists());

            File f = dir.getParentFile().getParentFile().getParentFile().getParentFile()
                    .getParentFile().getParentFile().getParentFile();
            return new File(f, "out" + File.separator + "repo");
        } catch (URISyntaxException e) {
            fail(e.getLocalizedMessage());
        }
        return null;
    }

    /**
     * Runs gradle on the project.  Throws exception on failure.
     *
     * @param tasks Variadic list of tasks to execute.
     */
    public void execute(String ... tasks) {
        execute(Collections.<String>emptyList(), false, tasks);
    }

    public void execute(@NonNull List<String> arguments, String ... tasks) {
        execute(arguments, false, tasks);
    }

    /**
     * Runs gradle on the project, and returns the project model.  Throws exception on failure.
     *
     * @param tasks Variadic list of tasks to execute.
     *
     * @return the AndroidProject model for the project.
     */
    public AndroidProject executeAndReturnModel(String ... tasks) {
        return execute(Collections.<String>emptyList(), true, tasks);
    }

    /**
     * Runs gradle on the project.  Throws exception on failure.
     *
     * @param arguments List of arguments for the gradle command.
     * @param returnModel whether the model should be queried and returned.
     * @param tasks Variadic list of tasks to execute.
     *
     * @return the model, if <var>returnModel</var> was true, null otherwise
     */
    @Nullable
    private AndroidProject execute(
            @NonNull List<String> arguments,
            boolean returnModel,
            @NonNull String ... tasks) {
        ProjectConnection connection = getProjectConnection();
        try {
            List<String> args = Lists.newArrayListWithCapacity(2 + arguments.size());
            args.add("-i");
            args.add("-u");
            args.addAll(arguments);

            connection.newBuild().forTasks(tasks)
                    .setStandardOutput(stdout)
                    .withArguments(args.toArray(new String[args.size()])).run();

            if (returnModel) {
                return connection.getModel(AndroidProject.class);
            }
        } finally {
            connection.close();
        }

        return null;
    }

    /**
     * Returns the project model
     */
    @NonNull
    public AndroidProject getModel() {
        ProjectConnection connection = getProjectConnection();
        try {
            return connection.getModel(AndroidProject.class);
        } finally {
            connection.close();
        }
    }

    /**
     * Return the stdout from all execute command.
     */
    public ByteArrayOutputStream getStdout() {
        return stdout;
    }

    /**
     * Create a File object.  getTestDir will be the base directory if a relative path is supplied.
     *
     * @param path Full path of the file.  May be a relative path.
     */
    public File file(String path) {
        File result = new File(path);
        if (result.isAbsolute()) {
            return result;
        } else {
            return new File(testDir, path);
        }
    }

    /**
     * Returns the SDK folder as built from the Android source tree.
     */
    private static File findSdkDir() {
        String androidHome = System.getenv("ANDROID_HOME");
        if (androidHome != null) {
            File f = new File(androidHome);
            if (f.isDirectory()) {
                return f;
            } else {
                System.out.println("Failed to find SDK in ANDROID_HOME=" + androidHome);
            }
        }
        return null;
    }

    /**
     * Returns the NDK folder as built from the Android source tree.
     */
    private static File findNdkDir() {
        String androidHome = System.getenv("ANDROID_NDK_HOME");
        if (androidHome != null) {
            File f = new File(androidHome);
            if (f.isDirectory()) {
                return f;
            } else {
                System.out.println("Failed to find NDK in ANDROID_NDK_HOME=" + androidHome);
            }
        }
        return null;
    }

    /**
     * Returns a Gradle project Connection
     */
    @NonNull
    private ProjectConnection getProjectConnection() {
        GradleConnector connector = GradleConnector.newConnector();

        return connector
                .useGradleVersion(BasePlugin.GRADLE_TEST_VERSION)
                .forProjectDirectory(testDir)
                .connect();
    }

    private static File createLocalProp(
            @NonNull File project,
            @NonNull File sdkDir,
            @Nullable File ndkDir) throws IOException, StreamException {
        ProjectPropertiesWorkingCopy localProp = ProjectProperties.create(
                project.getAbsolutePath(), ProjectProperties.PropertyType.LOCAL);
        localProp.setProperty(ProjectProperties.PROPERTY_SDK, sdkDir.getAbsolutePath());
        if (ndkDir != null) {
            localProp.setProperty(ProjectProperties.PROPERTY_NDK, ndkDir.getAbsolutePath());
        }
        localProp.save();

        return (File) localProp.getFile();
    }
}
