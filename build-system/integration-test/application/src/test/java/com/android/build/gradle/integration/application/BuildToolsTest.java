package com.android.build.gradle.integration.application;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainerV2;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.core.ToolsRevisionUtils;
import com.android.builder.model.v2.ide.SyncIssue;
import com.android.builder.model.v2.models.AndroidDsl;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import static com.android.build.gradle.integration.common.utils.AssumeBuildToolsUtil.assumeBuildToolsGreaterThan;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests to ensure that changing the build tools version in the build.gradle will trigger
 * re-execution of some tasks even if no source file change was detected.
 */
public class BuildToolsTest {

    private static final List<String> COMMON_TASKS =
            ImmutableList.of(
                    ":compileDebugAidl",
                    ":mergeDebugResources",
                    ":processDebugResources",
                    ":compileReleaseAidl",
                    ":mergeReleaseResources",
                    ":processReleaseResources");

    private static final List<String> JAVAC_TASKS =
            ImmutableList.<String>builder()
                    .addAll(COMMON_TASKS)
                    .add(":dexBuilderDebug")
                    .add(":dexBuilderRelease")
                    .add(":mergeDexDebug")
                    .add(":mergeExtDexDebug")
                    .add(":mergeDexRelease")
                    .add(":mergeExtDexRelease")
                    .build();

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestApp(HelloWorldApp.noBuildFile()).create();

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    namespace \""
                        + HelloWorldApp.NAMESPACE
                        + "\"\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion '"
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "'\n"
                        + "    buildFeatures.aidl true\n"
                        + "}\n");

        // Add an Aidl file so that it's not skipped due to no-source.
        File aidlDir = project.file("src/main/aidl/com/example/helloworld");
        FileUtils.mkdirs(aidlDir);
        TestFileUtils.appendToFile(
                new File(aidlDir, "MyRect.aidl"),
                "" + "package com.example.helloworld;\n" + "parcelable MyRect;\n");
    }

    @Test
    public void nullBuild() throws IOException, InterruptedException {
        project.executor()
                .with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false)
                .run("assemble");
        GradleBuildResult result =
                project.executor()
                        .with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false)
                        .run("assemble");

        assertThat(result.getUpToDateTasks()).containsAllIn(JAVAC_TASKS);
    }

    @Test
    public void invalidateBuildTools() throws IOException, InterruptedException {
        // We need at least 2 valid versions of the build tools for this test.
        assumeBuildToolsGreaterThan(ToolsRevisionUtils.MIN_BUILD_TOOLS_REV);

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    namespace \""
                        + HelloWorldApp.NAMESPACE
                        + "\"\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion '"
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "'\n"
                        + "}\n");

        project.executor().run("assemble");

        String otherBuildToolsVersion = ToolsRevisionUtils.MIN_BUILD_TOOLS_REV.toString();
        // Sanity check:
        assertThat(otherBuildToolsVersion)
                .isNotEqualTo(GradleTestProject.DEFAULT_BUILD_TOOL_VERSION);

        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion '"
                        + otherBuildToolsVersion
                        + "'\n"
                        + "}\n");

        GradleBuildResult result = project.executor().run("assemble");

        assertThat(result.getDidWorkTasks()).containsAllIn(COMMON_TASKS);
    }

    @Test
    public void buildToolsInModel() throws IOException {
        AndroidDsl androidDsl = project.modelV2()
                .fetchModels()
                .getContainer()
                .getProject()
                .getAndroidDsl();

        assertThat(androidDsl.getBuildToolsVersion())
                .named("Build Tools Version")
                .isEqualTo(GradleTestProject.DEFAULT_BUILD_TOOL_VERSION);
    }

    @Test
    public void buildToolsSyncIssue() throws IOException {
        TestFileUtils.searchAndReplace(
                project.getBuildFile(),
                "buildToolsVersion '" + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION + "'",
                "buildToolsVersion '30.0.2'");
        ModelContainerV2 container =
                project.modelV2().ignoreSyncIssues().fetchModels().getContainer();
        Collection<com.android.builder.model.v2.ide.SyncIssue> syncIssues = container.getProject()
                .getIssues()
                .getSyncIssues();
        assertThat(syncIssues).hasSize(1);
        com.android.builder.model.v2.ide.SyncIssue singleSyncIssue =
                (com.android.builder.model.v2.ide.SyncIssue) syncIssues.toArray()[0];
        assertThat(singleSyncIssue.getSeverity()).isEqualTo(SyncIssue.SEVERITY_WARNING);
        assertThat(singleSyncIssue.getType()).isEqualTo(SyncIssue.TYPE_BUILD_TOOLS_TOO_LOW);
    }
}
