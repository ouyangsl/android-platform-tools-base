package com.android.build.gradle.integration.dependencies;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property.GRADLE_PATH;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.MODULE;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;

public class TestWithProjectSubstitution {
    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();
    static ModelContainer<AndroidProject> modelContainer;

    @BeforeClass
    public static void setUp() throws Exception {
        Files.asCharSink(project.getSettingsFile(), Charsets.UTF_8)
                .write("include 'library', 'library2', 'library3'");

        appendToFile(
                project.getBuildFile(),
                "\nallprojects {\n" +
                        "    configurations.all {\n" +
                        "        resolutionStrategy.dependencySubstitution {\n" +
                        "            substitute module('com.example:library3') with project(':library3')\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n");

        appendToFile(
                project.getSubproject("library").getBuildFile(),
                "\ndependencies {\n"
                        + "    implementation project(':library2')\n"
                        + "}\n");

        appendToFile(
                project.getSubproject("library2").getBuildFile(),
                "\ndependencies {\n"
                        + "    implementation 'com.example:library3:1.0'\n"
                        + "}\n");
        modelContainer = project.model().withFullDependencies().fetchAndroidProjects();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        modelContainer = null;
    }

    @Test
    public void checkNoCompileDependencyUpwardLeak() {
        Variant variant =
                AndroidProjectUtils.getVariantByName(
                        modelContainer.getOnlyModelMap().get(":library"), "debug");

        LibraryGraphHelper helper = new LibraryGraphHelper(modelContainer);
        DependencyGraphs compileGraph = variant.getMainArtifact().getDependencyGraphs();

        assertThat(helper.on(compileGraph).withType(MODULE).mapTo(GRADLE_PATH))
                .named("compile classpath dependency of library")
                .doesNotContain(":library3");
    }
}
