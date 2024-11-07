package com.android.build.gradle.integration.testing;

import static com.android.build.gradle.integration.common.fixture.TestVersions.SUPPORT_LIB_VERSION;
import static com.android.build.gradle.integration.common.fixture.TestVersions.TEST_SUPPORT_LIB_VERSION;
import static com.android.build.gradle.integration.common.truth.ApkSubject.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType;
import com.android.build.gradle.integration.common.fixture.ModelContainerV2;
import com.android.build.gradle.integration.common.truth.ScannerSubject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.v2.ide.TestedTargetVariant;
import com.android.builder.model.v2.ide.Variant;
import com.android.testutils.apk.Apk;
import com.android.utils.FileUtils;

import com.google.common.collect.Iterables;
import com.google.common.truth.Truth;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Test for setup with 2 modules: app and test-app Checking the manifest merging for the test
 * modules.
 */
public class SeparateTestModuleTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("separateTestModule")
                    // Enforcing unique package names to prevent regressions. Remove when b/116109681 fixed.
                    .addGradleProperties(
                            BooleanOption.ENFORCE_UNIQUE_PACKAGE_NAMES.getPropertyName() + "=true")
                    .addGradleProperties(BooleanOption.USE_ANDROID_X.getPropertyName() + "=true")
                    .create();

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n"
                        + "dependencies {\n"
                        + "    implementation \"com.android.support:support-v4:"
                        + SUPPORT_LIB_VERSION
                        + "\"\n"
                        + "}\n");

        TestFileUtils.appendToFile(
                project.getSubproject("test").getBuildFile(),
                "\n"
                        + "android {\n"
                        + "  defaultConfig {\n"
                        + "    testInstrumentationRunner"
                        + " 'android.support.test.runner.AndroidJUnitRunner'\n"
                        + "    minSdkVersion 16\n"
                        + "    targetSdkVersion 16\n"
                        + "  }\n"
                        + "  dependencies {\n"
                        + "    // This dependency should be de-duplicated from the main app.\n"
                        + "    implementation 'com.android.support:support-v4:"
                        + SUPPORT_LIB_VERSION
                        + "'\n"
                        + "    implementation 'com.android.support.test:runner:"
                        + TEST_SUPPORT_LIB_VERSION
                        + "'\n"
                        + "  }\n"
                        + "}\n");
    }

    @Test
    public void checkDependencySubtraction() throws Exception {
        project.executor().run(":app:assembleDebug", ":test:assembleDebug");
        try (Apk main = project.getSubproject("app").getApk(ApkType.DEBUG);
                Apk test = project.getSubproject("test").getApk(ApkType.DEBUG)) {

            // Sanity check the test dependency is packaged
            assertThat(test).containsClass("Landroid/support/test/runner/AndroidJUnit4;");

            // Check that a class shared with a production dependency is correctly subtracted.
            // So it should be present in the main APK, but not the test APK.
            String GUARDED_BY = "Landroid/support/annotation/GuardedBy;";
            assertThat(main).containsClass(GUARDED_BY);
            assertThat(test).doesNotContainClass(GUARDED_BY);

            // Check that a resource shared with a production dependency is *not* subtracted
            String NOTIFICATION_ACTION = "layout/notification_action.xml";
            assertThat(main).containsResource(NOTIFICATION_ACTION);
            assertThat(test).containsResource(NOTIFICATION_ACTION);
        }
    }

    @Test
    public void checkInstrumentationReadFromBuildFile() throws Exception {
        GradleTestProject testProject = project.getSubproject("test");
        addInstrumentationToManifest();
        project.execute("clean", ":test:assembleDebug");

        assertThat(
                        testProject.file(
                                "build/intermediates/packaged_manifests/debug/processDebugManifest/AndroidManifest.xml"))
                .containsAllOf(
                        "package=\"com.example.android.testing.blueprint.test\"",
                        "android:name=\"android.support.test.runner.AndroidJUnitRunner\"",
                        "android:targetPackage=\"com.android.tests.basic\"");
    }

    @Test
    public void checkInstrumentationAdded() {
        GradleTestProject testProject = project.getSubproject("test");
        project.execute("clean", ":test:assembleDebug");

        assertThat(
                        testProject.file(
                                "build/intermediates/packaged_manifests/debug/processDebugManifest/AndroidManifest.xml"))
                .containsAllOf(
                        "package=\"com.example.android.testing.blueprint.test\"",
                        "<instrumentation",
                        "android:name=\"android.support.test.runner.AndroidJUnitRunner\"",
                        "android:targetPackage=\"com.android.tests.basic\"");
    }

    @Test
    public void checkModelContainsTestedApksToInstall() {
        ModelContainerV2.ModelInfo model =
                project.modelV2().fetchModels().getContainer().getProject(":test");

        Variant variant = Iterables.getFirst(model.getAndroidProject().getVariants(), null);
        Truth.assertThat(variant).isNotNull();
        TestedTargetVariant testedVariant = variant.getTestedTargetVariant();
        assertThat(testedVariant.getTargetProjectPath()).isEqualTo(":app");
        assertThat(testedVariant.getTargetVariant()).isEqualTo("debug");
    }

    /**
     * Check that there are no problems with assembling a non-debug variant on the test module that
     * corresponds to a non-debug variant on the app module
     */
    @Test
    public void checkVariantTesting() throws Exception {
        TestFileUtils.appendToFile(
                project.getSubproject(":app").getBuildFile(),
                "android {\n"
                        + "    buildTypes {\n"
                        + "        nodebug {\n"
                        + "            debuggable false\n"
                        + "            signingConfig signingConfigs.debug\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");

        TestFileUtils.appendToFile(
                project.getSubproject(":test").getBuildFile(),
                "android { buildTypes.create('nodebug') }\n");

        GradleBuildResult result = project.executor().run("clean", ":test:assembleNodebug");

        // Verify that the lintVital task does not run. It should not be created (b/127843764)
        assertThat(result.getTasks()).doesNotContain(":test:lintVitalNodebug");
    }

    // Test for b/324637676
    // Validate failure when library module is the test target
    @Test
    public void validateLibraryTestTarget() throws Exception {
        TestFileUtils.searchAndReplace(
                project.getSubproject(":test").getBuildFile(),
                "targetProjectPath ':app'",
                "targetProjectPath ':lib'");

        GradleBuildResult result = project.executor().expectFailure().run(":test:assembleDebug");
        ScannerSubject.assertThat(result.getStderr())
                .contains(
                        "The tested application ID could not be computed. Please ensure that the"
                                + " test module specifies a valid module in"
                                + " \"targetProjectPath\". Currently, library modules are not"
                                + " supported as a target project.");
    }

    private void addInstrumentationToManifest() throws IOException {
        GradleTestProject testProject = project.getSubproject("test");
        FileUtils.deleteIfExists(testProject.file("src/main/AndroidManifest.xml"));
        String manifestContent =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                    + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                    + "      <instrumentation"
                    + " android:name=\"android.test.InstrumentationTestRunner\"\n"
                    + "                       android:targetPackage=\"com.android.tests.basic\"\n"
                    + "                       android:handleProfiling=\"false\"\n"
                    + "                       android:functionalTest=\"false\"\n"
                    + "                       android:label=\"Tests for"
                    + " com.android.tests.basic\"/>\n"
                    + "</manifest>\n";
        Files.write(
                testProject.file("src/main/AndroidManifest.xml").toPath(),
                manifestContent.getBytes());
    }
}
