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
import org.gradle.api.artifacts.DependencySubstitutions;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property.COORDINATES;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Property.GRADLE_PATH;
import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.*;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;

public class TestWithProjectSubstitutionCompileDep {
    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder()
            .fromTestProject("projectWithModules")
            .create();
    static ModelContainer<AndroidProject> modelContainer;

    @BeforeClass
    public static void setUp() throws Exception {
        Files.asCharSink(project.getSettingsFile(), Charsets.UTF_8)
                .write("include 'library', 'library2'");

        appendToFile(
                project.getBuildFile(),
                "\nallprojects {\n" +
                        "    configurations.all {\n" +
                        "        if (it.name.indexOf('RuntimeClasspath') < 0) { return }\n" +
                        "        resolutionStrategy.dependencySubstitution {\n" +
                        "            substitute module('com.google.guava:guava') with project(':library2')\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n");

        appendToFile(
                project.getSubproject("library").getBuildFile(),
                "\ndependencies {\n"
                        + "    compileOnly 'com.google.guava:guava:18.0'\n"
                        + "    runtimeOnly 'com.google.guava:guava:18.0'\n"
                        + "}\n");

        modelContainer = project.model().withFullDependencies().fetchAndroidProjects();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        modelContainer = null;
    }

    @Test
    public void testCompileDepConstraintFromProjectSubstitution() {
        Variant variant =
                AndroidProjectUtils.getVariantByName(
                        modelContainer.getOnlyModelMap().get(":library"), "debug");

        LibraryGraphHelper helper = new LibraryGraphHelper(modelContainer);
        DependencyGraphs compileGraph = variant.getMainArtifact().getDependencyGraphs();

        assertThat(helper.on(compileGraph).withType(JAVA).mapTo(COORDINATES))
                .named("compile classpath dependency of library")
                .doesNotContain("com.google.guava:guava:18.0@jar");

        assertThat(helper.on(compileGraph).withType(MODULE).mapTo(GRADLE_PATH))
                .named("compile classpath dependency of library")
                .contains(":library2");
    }
}
