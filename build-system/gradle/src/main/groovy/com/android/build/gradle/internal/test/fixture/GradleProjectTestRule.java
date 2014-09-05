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

package com.android.build.gradle.internal.test.fixture;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.BasePlugin;
import com.android.io.StreamException;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.internal.project.ProjectPropertiesWorkingCopy;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
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
 * The test directory is always deleted if it already exist at the start of the test to ensure a
 * clean environment.
 */
public class GradleProjectTestRule implements TestRule {

    private static final String ANDROID_GRADLE_VERSION = "0.13.0";

    private File testDir;

    private File sourceDir;

    private File buildFile;

    private File ndkDir;

    private File sdkDir;

    public GradleProjectTestRule() {
        sdkDir = findSdkDir();
        ndkDir = findNdkDir();
    }

    /**
     * Recursively delete directory or file.
     *
     * @param root directory to delete
     */
    private static void deleteRecursive(File root) {
        if (root.exists()) {
            if (root.isDirectory()) {
                for (File file : root.listFiles()) {
                    deleteRecursive(file);
                }
            }
            assertTrue(root.delete());
        }
    }

    @Override
    public Statement apply(final Statement base, Description description) {
        testDir = new File("build/tmp/tests/" +
                description.getTestClass().getName());

        // Create separate directory based on test method name if @Rule is used.
        // getMethodName() is null if this rule is used as a @ClassRule.
        if (description.getMethodName() != null) {
            testDir = new File(testDir, description.getMethodName());
        }

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

                Files.write(
                        "buildscript {\n" +
                                "    repositories {\n" +
                                "        maven { url '" + getRepoDir().toString() + "' }\n" +
                                "    }\n" +
                                "    dependencies {\n" +
                                "        classpath 'com.android.tools.build:gradle:" + ANDROID_GRADLE_VERSION + "'\n" +
                                "    }\n" +
                                "}\n",
                        buildFile,
                        Charsets.UTF_8);

                createLocalProp(testDir, sdkDir, ndkDir);
                base.evaluate();
            }
        };
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
        execute(Collections.<String>emptyList(), tasks);
    }

    /**
     * Runs gradle on the project.  Throws exception on failure.
     *
     * @param arguments List of arguments for the gradle command.
     * @param tasks Variadic list of tasks to execute.
     */
    public void execute(List<String> arguments, String ... tasks) {
        GradleConnector connector = GradleConnector.newConnector();

        ProjectConnection connection = connector
                .useGradleVersion(BasePlugin.GRADLE_TEST_VERSION)
                .forProjectDirectory(testDir)
                .connect();
        try {
            List<String> args = Lists.newArrayListWithCapacity(2 + arguments.size());
            args.add("-i");
            args.add("-u");
            args.addAll(arguments);

            connection.newBuild().forTasks(tasks)
                    .withArguments(args.toArray(new String[args.size()])).run();
        } finally {
            connection.close();
        }
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
