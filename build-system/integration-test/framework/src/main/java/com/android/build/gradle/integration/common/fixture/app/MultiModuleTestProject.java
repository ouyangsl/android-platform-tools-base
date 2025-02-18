package com.android.build.gradle.integration.common.fixture.app;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.BuildSrcProject;
import com.android.build.gradle.integration.common.fixture.GradleProject;
import com.android.build.gradle.integration.common.fixture.TestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

/** A TestProject containing multiple TestProject as modules. */
public class MultiModuleTestProject implements TestProject {

    private final ImmutableMap<String, TestProject> subprojects;
    @Nullable private final BuildSrcProject buildSrcProject;

    /**
     * Creates a MultiModuleTestProject.
     *
     * @param subprojects a map with gradle project path as key and the corresponding TestProject as
     *     value.
     */
    public MultiModuleTestProject(@NonNull Map<String, ? extends TestProject> subprojects) {
        this(subprojects, null);
    }

    public MultiModuleTestProject(
            @NonNull Map<String, ? extends TestProject> subprojects,
            @Nullable BuildSrcProject buildSrcProject) {
        this.subprojects = ImmutableMap.copyOf(subprojects);
        this.buildSrcProject = buildSrcProject;
    }

    /**
     * Creates a MultiModuleTestProject with multiple subproject of the same TestProject.
     *
     * @param baseName Base name of the subproject. Actual project name will be baseName + index.
     * @param subproject A TestProject.
     * @param count Number of subprojects to create.
     */
    public MultiModuleTestProject(
            @NonNull String baseName, @NonNull TestProject subproject, int count) {
        ImmutableMap.Builder<String, TestProject> builder = ImmutableMap.builder();
        for (int i = 0; i < count; i++) {
            builder.put(baseName + i, subproject);
        }
        subprojects = builder.build();
        buildSrcProject = null;
    }

    /** Return the test project with the given project path. */
    public TestProject getSubproject(String subprojectPath) {
        return subprojects.get(subprojectPath);
    }

    @Override
    public void write(
            @NonNull final File projectDir,
            @Nullable final String buildScriptContent,
            @NonNull String projectRepoScript)
            throws IOException {
        for (Map.Entry<String, ? extends TestProject> entry : subprojects.entrySet()) {
            String subprojectPath = entry.getKey();
            TestProject subproject = entry.getValue();
            File subprojectDir = new File(projectDir, convertGradlePathToDirectory(subprojectPath));
            if (!subprojectDir.exists()) {
                subprojectDir.mkdirs();
                assert subprojectDir.isDirectory();
            }
            subproject.write(subprojectDir, null, projectRepoScript);
        }

        if (buildSrcProject != null) {
            File subprojectDir = new File(projectDir, "buildSrc");
            if (!subprojectDir.exists()) {
                subprojectDir.mkdirs();
            }
            // buildSrc requires the repo declaration.
            buildSrcProject.write(
                    subprojectDir,
                    "dependencies { implementation \"com.android.tools.build:gradle-api:"
                            + com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION
                            + "\" }\n",
                    projectRepoScript);
        }
        StringBuilder builder = new StringBuilder();
        for (String subprojectName : subprojects.keySet()) {
            builder.append("include '").append(subprojectName).append("'\n");
        }

        TestFileUtils.appendToFile(new File(projectDir, "settings.gradle"), builder.toString());

        Files.asCharSink(new File(projectDir, "build.gradle"), Charset.defaultCharset())
                .write(buildScriptContent);
    }

    @Override
    public boolean containsFullBuildScript() {
        return false;
    }

    private static String convertGradlePathToDirectory(String gradlePath) {
        return gradlePath.replace(":", "/");
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private BiMap<String, GradleProject> projects = HashBiMap.create();
        private BuildSrcProject buildSrcProject = null;

        /**
         * Adds a buildSrc project to the current test project. Can only be called once.
         *
         * @param buildSrcProject the [BuildSrcProject] to use as buildSrc
         * @return itself.
         */
        @NonNull
        public Builder buildSrcProject(@NonNull BuildSrcProject buildSrcProject) {
            if (this.buildSrcProject != null) {
                throw new IllegalStateException("Test project can only have one buildSrc project");
            }
            this.buildSrcProject = buildSrcProject;
            return this;
        }

        @NonNull
        public Builder subproject(@NonNull GradleProject testProject) {
            return subproject(checkNotNull(testProject.getPath()), testProject);
        }

        @NonNull
        public Builder subproject(@NonNull String name, @NonNull GradleProject testProject) {
            projects.put(name, testProject);
            return this;
        }

        @NonNull
        public Builder dependency(@NonNull GradleProject from, @NonNull String to) {
            String snippet = "\ndependencies {\n    " + "implementation '" + to + "'\n}\n";
            from.replaceFile(from.getFile("build.gradle").appendContent(snippet));
            return this;
        }

        @NonNull
        public Builder fileDependency(@NonNull GradleProject from, @NonNull String file) {
            String snippet = "\ndependencies {\n    " + "implementation files('" + file + "')\n}\n";
            from.replaceFile(from.getFile("build.gradle").appendContent(snippet));
            return this;
        }

        @NonNull
        public Builder unitTestDependency(@NonNull GradleProject from, @NonNull String to) {
            String snippet = "\ndependencies {\n    " + "testImplementation '" + to + "'\n}\n";
            from.replaceFile(from.getFile("build.gradle").appendContent(snippet));
            return this;
        }

        @NonNull
        public Builder androidTestDependency(@NonNull GradleProject from, @NonNull String to) {
            String snippet =
                    "\ndependencies {\n    " + "androidTestImplementation '" + to + "'\n}\n";
            from.replaceFile(from.getFile("build.gradle").appendContent(snippet));
            return this;
        }

        @NonNull
        public Builder dependency(@NonNull GradleProject from, @NonNull GradleProject to) {
            return dependency("implementation", from, to);
        }

        @NonNull
        public Builder androidTestDependency(
                @NonNull GradleProject from, @NonNull GradleProject to) {
            return dependency("androidTestImplementation", from, to);
        }

        @NonNull
        public Builder dependency(
                @NonNull String configuration,
                @NonNull GradleProject from,
                @NonNull GradleProject to) {
            String snippet =
                    "\ndependencies {\n    "
                            + configuration
                            + " project('"
                            + projects.inverse().get(to)
                            + "')\n}\n";
            from.replaceFile(from.getFile("build.gradle").appendContent(snippet));
            return this;
        }

        @NonNull
        public MultiModuleTestProject build() {
            return new MultiModuleTestProject(projects, buildSrcProject);
        }
    }
}
