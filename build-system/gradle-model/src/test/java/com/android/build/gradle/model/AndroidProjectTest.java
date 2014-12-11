/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.model;

import static com.android.builder.core.BuilderConstants.DEBUG;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.Variant;
import com.google.common.collect.Maps;

import junit.framework.TestCase;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.UnknownModelException;
import org.gradle.tooling.model.GradleProject;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.Collection;
import java.util.Map;

public class AndroidProjectTest extends TestCase {

    private static final String FOLDER_TEST_SAMPLE = "samples";
    private static final String FOLDER_TEST_PROJECT = "test-projects";

    private static final String MODEL_VERSION = "1.0.0";

    private static final class ProjectData {
        AndroidProject model;
        File projectDir;

        static ProjectData create(File projectDir, AndroidProject model) {
            ProjectData projectData = new ProjectData();
            projectData.model = model;
            projectData.projectDir = projectDir;

            return projectData;
        }
    }

    private Map<String, ProjectData> getModelForMultiProject(String testFolder, String projectName)
            throws Exception {
        // Configure the connector and create the connection
        GradleConnector connector = GradleConnector.newConnector();

        File projectDir = new File(getTestDir(testFolder), projectName);
        connector.forProjectDirectory(projectDir);

        Map<String, ProjectData> map = Maps.newHashMap();

        ProjectConnection connection = connector.connect();

        try {
            // Query the default Gradle Model.
            GradleProject model = connection.getModel(GradleProject.class);
            assertNotNull("Model Object null-check", model);

            // Now get the children projects, recursively.
            for (GradleProject child : model.getChildren()) {
                String path = child.getPath();
                String name = path.substring(1);
                File childDir = new File(projectDir, name);

                GradleConnector childConnector = GradleConnector.newConnector();

                childConnector.forProjectDirectory(childDir);

                ProjectConnection childConnection = childConnector.connect();
                try {
                    AndroidProject androidProject = childConnection.getModel(AndroidProject.class);

                    assertNotNull("Model Object null-check for " + path, androidProject);
                    assertEquals("Model Name for " + path, name, androidProject.getName());
                    assertEquals("Model version", MODEL_VERSION, androidProject.getModelVersion());

                    map.put(path, ProjectData.create(childDir, androidProject));

                } catch (UnknownModelException e) {
                    // probably a Java-only project, ignore.
                } finally {
                    childConnection.close();
                }
            }
        } finally {
            connection.close();
        }

        return map;
<<<<<<< HEAD   (6ecba2 Merge "Avoiding relative path lookup for skins" into studio-)
    }

    public void testBasic() throws Exception {
        // Load the custom model for the project
        ProjectData projectData = getModelForProject(FOLDER_TEST_SAMPLE, "basic");

        AndroidProject model = projectData.model;

        assertFalse("Library Project", model.isLibrary());
        assertEquals("Compile Target", "android-21", model.getCompileTarget());
        assertFalse("Non empty bootclasspath", model.getBootClasspath().isEmpty());

        assertNotNull("aaptOptions not null", model.getAaptOptions());
        assertEquals("aaptOptions noCompress", 1, model.getAaptOptions().getNoCompress().size());
        assertTrue("aaptOptions noCompress", model.getAaptOptions().getNoCompress().contains("txt"));
        assertEquals("aaptOptions ignoreAssetsPattern", "!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~", model.getAaptOptions().getIgnoreAssets());
        assertFalse("aaptOptions getFailOnMissingConfigEntry", model.getAaptOptions().getFailOnMissingConfigEntry());

        JavaCompileOptions javaCompileOptions = model.getJavaCompileOptions();
        assertEquals("1.6", javaCompileOptions.getSourceCompatibility());
        assertEquals("1.6", javaCompileOptions.getTargetCompatibility());
    }

    public void testBasicSourceProviders() throws Exception {
        // Load the custom model for the project
        ProjectData projectData = getModelForProject(FOLDER_TEST_SAMPLE, "basic");

        AndroidProject model = projectData.model;
        File projectDir = projectData.projectDir;

        testDefaultSourceSets(model, projectDir);

        // test the source provider for the artifacts
        for (Variant variant : model.getVariants()) {
            AndroidArtifact artifact = variant.getMainArtifact();
            assertNull(artifact.getVariantSourceProvider());
            assertNull(artifact.getMultiFlavorSourceProvider());
        }
    }

    public void testBasicMultiFlavorsSourceProviders() throws Exception {
        // Load the custom model for the project
        ProjectData projectData = getModelForProject(FOLDER_TEST_PROJECT, "basicMultiFlavors");

        AndroidProject model = projectData.model;
        File projectDir = projectData.projectDir;

        testDefaultSourceSets(model, projectDir);

        // test the source provider for the flavor
        Collection<ProductFlavorContainer> productFlavors = model.getProductFlavors();
        assertEquals("Product Flavor Count", 4, productFlavors.size());

        for (ProductFlavorContainer pfContainer : productFlavors) {
            String name = pfContainer.getProductFlavor().getName();
            new SourceProviderTester(
                    model.getName(),
                    projectDir,
                    name,
                    pfContainer.getSourceProvider())
                .test();

            assertEquals(1, pfContainer.getExtraSourceProviders().size());
            SourceProviderContainer container = getSourceProviderContainer(
                    pfContainer.getExtraSourceProviders(), ARTIFACT_ANDROID_TEST);
            assertNotNull(container);

            new SourceProviderTester(
                    model.getName(),
                    projectDir,
                    ANDROID_TEST + StringHelper.capitalize(name),
                    container.getSourceProvider())
                .test();
        }

        // test the source provider for the artifacts
        for (Variant variant : model.getVariants()) {
            AndroidArtifact artifact = variant.getMainArtifact();
            assertNotNull(artifact.getVariantSourceProvider());
            assertNotNull(artifact.getMultiFlavorSourceProvider());
        }
    }

    private static void testDefaultSourceSets(@NonNull AndroidProject model,
            @NonNull File projectDir) {
        ProductFlavorContainer defaultConfig = model.getDefaultConfig();

        // test the main source provider
        new SourceProviderTester(model.getName(), projectDir,
                "main", defaultConfig.getSourceProvider())
                .test();

        // test the main instrumentTest source provider
        SourceProviderContainer testSourceProviders = getSourceProviderContainer(
                defaultConfig.getExtraSourceProviders(), ARTIFACT_ANDROID_TEST);
        assertNotNull("InstrumentTest source Providers null-check", testSourceProviders);

        new SourceProviderTester(model.getName(), projectDir,
                ANDROID_TEST, testSourceProviders.getSourceProvider())
            .test();

        // test the source provider for the build types
        Collection<BuildTypeContainer> buildTypes = model.getBuildTypes();
        assertEquals("Build Type Count", 2, buildTypes.size());

        for (BuildTypeContainer btContainer : model.getBuildTypes()) {
            new SourceProviderTester(
                    model.getName(),
                    projectDir,
                    btContainer.getBuildType().getName(),
                    btContainer.getSourceProvider())
                .test();

            assertEquals(0, btContainer.getExtraSourceProviders().size());
        }
    }

    public void testBasicVariantDetails() throws Exception {
        // Load the custom model for the project
        ProjectData projectData = getModelForProject(FOLDER_TEST_SAMPLE, "basic", true /*cleanFirst*/);

        AndroidProject model = projectData.model;

        Collection<Variant> variants = model.getVariants();
        assertEquals("Variant Count", 2 , variants.size());

        // debug variant
        Variant debugVariant = getVariant(variants, DEBUG);
        assertNotNull("debug Variant null-check", debugVariant);
        new ProductFlavorTester(debugVariant.getMergedFlavor(), "Debug Merged Flavor")
                .setVersionCode(12)
                .setVersionName("2.0")
                .setMinSdkVersion(16)
                .setTargetSdkVersion(16)
                .setTestInstrumentationRunner("android.test.InstrumentationTestRunner")
                .setTestHandleProfiling(Boolean.FALSE)
                .setTestFunctionalTest(null)
            .test();

        // debug variant, tested.
        AndroidArtifact debugMainInfo = debugVariant.getMainArtifact();
        assertNotNull("Debug main info null-check", debugMainInfo);
        assertEquals("Debug package name", "com.android.tests.basic.debug",
                debugMainInfo.getApplicationId());
        assertTrue("Debug signed check", debugMainInfo.isSigned());
        assertEquals("Debug signingConfig name", "myConfig", debugMainInfo.getSigningConfigName());
        assertEquals("Debug sourceGenTask", "generateDebugSources", debugMainInfo.getSourceGenTaskName());
        assertEquals("Debug compileTask", "compileDebugSources", debugMainInfo.getCompileTaskName());

        Collection<AndroidArtifactOutput> debugMainOutputs = debugMainInfo.getOutputs();
        assertNotNull("Debug main output null-check", debugMainOutputs);
        assertEquals("Debug main output size", 1, debugMainOutputs.size());
        AndroidArtifactOutput debugMainOutput = debugMainOutputs.iterator().next();
        assertNotNull(debugMainOutput);
        assertNotNull(debugMainOutput.getMainOutputFile());
        assertNotNull(debugMainOutput.getAssembleTaskName());
        assertNotNull(debugMainOutput.getGeneratedManifest());
        assertEquals(12, debugMainOutput.getVersionCode());

        // check debug dependencies
        Dependencies debugDependencies = debugMainInfo.getDependencies();
        assertNotNull(debugDependencies);
        Collection<AndroidLibrary> debugLibraries = debugDependencies.getLibraries();
        assertNotNull(debugLibraries);
        assertEquals(1, debugLibraries.size());
        assertTrue(debugDependencies.getProjects().isEmpty());

        AndroidLibrary androidLibrary = debugLibraries.iterator().next();
        assertNotNull(androidLibrary);
        assertNotNull(androidLibrary.getBundle());
        assertNotNull(androidLibrary.getFolder());
        MavenCoordinates coord = androidLibrary.getResolvedCoordinates();
        assertNotNull(coord);
        assertEquals("com.google.android.gms:play-services:3.1.36",
                coord.getGroupId() + ":" + coord.getArtifactId() + ":" + coord.getVersion());


        Collection<JavaLibrary> javaLibraries = debugDependencies.getJavaLibraries();
        assertNotNull(javaLibraries);
        assertEquals(2, javaLibraries.size());

        Set<String> javaLibs = Sets.newHashSet(
                "com.android.support:support-v13:13.0.0",
                "com.android.support:support-v4:13.0.0"
        );

        for (JavaLibrary javaLib : javaLibraries) {
            coord = javaLib.getResolvedCoordinates();
            assertNotNull(coord);
            String lib = coord.getGroupId() + ":" + coord.getArtifactId() + ":" + coord.getVersion();
            assertTrue(javaLibs.contains(lib));
            javaLibs.remove(lib);
        }

        // this variant is tested.
        Collection<AndroidArtifact> debugExtraAndroidArtifacts = debugVariant.getExtraAndroidArtifacts();
        AndroidArtifact debugTestInfo = getAndroidArtifact(debugExtraAndroidArtifacts,
                ARTIFACT_ANDROID_TEST);
        assertNotNull("Test info null-check", debugTestInfo);
        assertEquals("Test package name", "com.android.tests.basic.debug.test",
                debugTestInfo.getApplicationId());
        assertTrue("Test signed check", debugTestInfo.isSigned());
        assertEquals("Test signingConfig name", "myConfig", debugTestInfo.getSigningConfigName());
        assertEquals("Test sourceGenTask", "generateDebugAndroidTestSources", debugTestInfo.getSourceGenTaskName());
        assertEquals("Test compileTask", "compileDebugAndroidTestSources", debugTestInfo.getCompileTaskName());

        Collection<File> generatedResFolders = debugTestInfo.getGeneratedResourceFolders();
        assertNotNull(generatedResFolders);
        // size 2 = rs output + resValue output
        assertEquals(2, generatedResFolders.size());

        Collection<AndroidArtifactOutput> debugTestOutputs = debugTestInfo.getOutputs();
        assertNotNull("Debug test output null-check", debugTestOutputs);
        assertEquals("Debug test output size", 1, debugTestOutputs.size());
        AndroidArtifactOutput debugTestOutput = debugTestOutputs.iterator().next();
        assertNotNull(debugTestOutput);
        assertNotNull(debugTestOutput.getMainOutputFile());
        assertNotNull(debugTestOutput.getAssembleTaskName());
        assertNotNull(debugTestOutput.getGeneratedManifest());

        // test the resValues and buildConfigFields.
        ProductFlavor defaultConfig = model.getDefaultConfig().getProductFlavor();
        Map<String, ClassField> buildConfigFields = defaultConfig.getBuildConfigFields();
        assertNotNull(buildConfigFields);
        assertEquals(2, buildConfigFields.size());

        assertEquals("true", buildConfigFields.get("DEFAULT").getValue());
        assertEquals("\"foo2\"", buildConfigFields.get("FOO").getValue());

        Map<String, ClassField> resValues = defaultConfig.getResValues();
        assertNotNull(resValues);
        assertEquals(1, resValues.size());

        assertEquals("foo", resValues.get("foo").getValue());

        // test on the debug build type.
        Collection<BuildTypeContainer> buildTypes = model.getBuildTypes();
        for (BuildTypeContainer buildTypeContainer : buildTypes) {
            if (buildTypeContainer.getBuildType().getName().equals(DEBUG)) {
                buildConfigFields = buildTypeContainer.getBuildType().getBuildConfigFields();
                assertNotNull(buildConfigFields);
                assertEquals(1, buildConfigFields.size());

                assertEquals("\"bar\"", buildConfigFields.get("FOO").getValue());

                resValues = buildTypeContainer.getBuildType().getResValues();
                assertNotNull(resValues);
                assertEquals(1, resValues.size());

                assertEquals("foo2", resValues.get("foo").getValue());
            }
        }

        // now test the merged flavor
        ProductFlavor mergedFlavor = debugVariant.getMergedFlavor();

        buildConfigFields = mergedFlavor.getBuildConfigFields();
        assertNotNull(buildConfigFields);
        assertEquals(2, buildConfigFields.size());

        assertEquals("true", buildConfigFields.get("DEFAULT").getValue());
        assertEquals("\"foo2\"", buildConfigFields.get("FOO").getValue());

        resValues = mergedFlavor.getResValues();
        assertNotNull(resValues);
        assertEquals(1, resValues.size());

        assertEquals("foo", resValues.get("foo").getValue());


        // release variant, not tested.
        Variant releaseVariant = getVariant(variants, "release");
        assertNotNull("release Variant null-check", releaseVariant);

        AndroidArtifact relMainInfo = releaseVariant.getMainArtifact();
        assertNotNull("Release main info null-check", relMainInfo);
        assertEquals("Release package name", "com.android.tests.basic",
                relMainInfo.getApplicationId());
        assertFalse("Release signed check", relMainInfo.isSigned());
        assertNull("Release signingConfig name", relMainInfo.getSigningConfigName());
        assertEquals("Release sourceGenTask", "generateReleaseSources", relMainInfo.getSourceGenTaskName());
        assertEquals("Release javaCompileTask", "compileReleaseSources", relMainInfo.getCompileTaskName());

        Collection<AndroidArtifactOutput> relMainOutputs = relMainInfo.getOutputs();
        assertNotNull("Rel Main output null-check", relMainOutputs);
        assertEquals("Rel Main output size", 1, relMainOutputs.size());
        AndroidArtifactOutput relMainOutput = relMainOutputs.iterator().next();
        assertNotNull(relMainOutput);
        assertNotNull(relMainOutput.getMainOutputFile());
        assertNotNull(relMainOutput.getAssembleTaskName());
        assertNotNull(relMainOutput.getGeneratedManifest());
        assertEquals(13, relMainOutput.getVersionCode());


        Collection<AndroidArtifact> releaseExtraAndroidArtifacts = releaseVariant.getExtraAndroidArtifacts();
        AndroidArtifact relTestInfo = getAndroidArtifact(releaseExtraAndroidArtifacts, ARTIFACT_ANDROID_TEST);
        assertNull("Release test info null-check", relTestInfo);

        // check release dependencies
        Dependencies releaseDependencies = relMainInfo.getDependencies();
        assertNotNull(releaseDependencies);
        Collection<AndroidLibrary> releaseLibraries = releaseDependencies.getLibraries();
        assertNotNull(releaseLibraries);
        assertEquals(3, releaseLibraries.size());

        // map for each aar we expect to find and how many local jars they each have.
        Map<String, Integer> aarLibs = Maps.newHashMapWithExpectedSize(3);
        aarLibs.put("com.android.support:support-v13:21.0.0", 1);
        aarLibs.put("com.android.support:support-v4:21.0.0", 1);
        aarLibs.put("com.google.android.gms:play-services:3.1.36", 0);
        for (AndroidLibrary androidLib : releaseLibraries) {
            assertNotNull(androidLib.getBundle());
            assertNotNull(androidLib.getFolder());
            coord = androidLib.getResolvedCoordinates();
            assertNotNull(coord);
            String lib = coord.getGroupId() + ":" + coord.getArtifactId() + ":" + coord.getVersion();

            Integer localJarCount = aarLibs.get(lib);
            assertNotNull("Check presence of " + lib, localJarCount);
            assertEquals("Check local jar count for " + lib,
                    localJarCount.intValue(), androidLib.getLocalJars().size());
            System.out.println(">>" + androidLib.getLocalJars());
            aarLibs.remove(lib);
        }

        assertTrue("check for missing libs", aarLibs.isEmpty());
    }

    public void testBasicSigningConfigs() throws Exception {
        // Load the custom model for the project
        ProjectData projectData = getModelForProject(FOLDER_TEST_SAMPLE, "basic");

        AndroidProject model = projectData.model;

        Collection<SigningConfig> signingConfigs = model.getSigningConfigs();
        assertNotNull("SigningConfigs null-check", signingConfigs);
        assertEquals("Number of signingConfig", 2, signingConfigs.size());

        SigningConfig debugSigningConfig = getSigningConfig(signingConfigs, DEBUG);
        assertNotNull("debug signing config null-check", debugSigningConfig);
        new SigningConfigTester(debugSigningConfig, DEBUG, true).test();

        SigningConfig mySigningConfig = getSigningConfig(signingConfigs, "myConfig");
        assertNotNull("myConfig signing config null-check", mySigningConfig);
        new SigningConfigTester(mySigningConfig, "myConfig", true)
                .setStoreFile(new File(projectData.projectDir, "debug.keystore"))
                .test();
    }

    public void testDensitySplitOutputs() throws Exception {
        // Load the custom model for the project
        ProjectData projectData = getModelForProject(FOLDER_TEST_SAMPLE, "densitySplit");

        AndroidProject model = projectData.model;

        Collection<Variant> variants = model.getVariants();
        assertEquals("Variant Count", 2 , variants.size());

        // get the main artifact of the debug artifact
        Variant debugVariant = getVariant(variants, DEBUG);
        assertNotNull("debug Variant null-check", debugVariant);
        AndroidArtifact debugMainArficat = debugVariant.getMainArtifact();
        assertNotNull("Debug main info null-check", debugMainArficat);

        // get the outputs.
        Collection<AndroidArtifactOutput> debugOutputs = debugMainArficat.getOutputs();
        assertNotNull(debugOutputs);
        assertEquals(5, debugOutputs.size());

        // build a map of expected outputs and their versionCode
        Map<String, Integer> expected = Maps.newHashMapWithExpectedSize(5);
        expected.put(null, 112);
        expected.put("mdpi", 212);
        expected.put("hdpi", 312);
        expected.put("xhdpi", 412);
        expected.put("xxhdpi", 512);

        assertEquals(5, debugOutputs.size());
        for (AndroidArtifactOutput output : debugOutputs) {
            assertEquals(OutputFile.FULL_SPLIT, output.getMainOutputFile().getOutputType());
            Collection<? extends OutputFile> outputFiles = output.getOutputs();
            assertEquals(1, outputFiles.size());
            assertNotNull(output.getMainOutputFile());

            String densityFilter = getFilter(output.getMainOutputFile(), OutputFile.DENSITY);
            Integer value = expected.get(densityFilter);
            // this checks we're not getting an unexpected output.
            assertNotNull("Check Valid output: " + (densityFilter == null ? "universal"
                            : densityFilter),
                    value);

            assertEquals(value.intValue(), output.getVersionCode());
            expected.remove(densityFilter);
}

        // this checks we didn't miss any expected output.
        assertTrue(expected.isEmpty());
    }

    @Nullable
    private static String getFilter(@NonNull OutputFile outputFile, @NonNull String filterType) {
        for (FilterData filterData : outputFile.getFilters()) {
            if (filterData.getFilterType().equals(filterType)) {
                return filterData.getIdentifier();
            }
        }
        return null;
    }

    public void testDensityPureSplitOutputs() throws Exception {
        // Load the custom model for the project
        ProjectData projectData = getModelForProject(FOLDER_TEST_SAMPLE, "densitySplitInL");

        AndroidProject model = projectData.model;

        Collection<Variant> variants = model.getVariants();
        assertEquals("Variant Count", 2 , variants.size());

        // get the main artifact of the debug artifact
        Variant debugVariant = getVariant(variants, DEBUG);
        assertNotNull("debug Variant null-check", debugVariant);
        AndroidArtifact debugMainArtifact = debugVariant.getMainArtifact();
        assertNotNull("Debug main info null-check", debugMainArtifact);

        // get the outputs.
        Collection<AndroidArtifactOutput> debugOutputs = debugMainArtifact.getOutputs();
        assertNotNull(debugOutputs);

        // build a set of expected outputs
        Set<String> expected = Sets.newHashSetWithExpectedSize(5);
        expected.add(null);
        expected.add("mdpi");
        expected.add("hdpi");
        expected.add("xhdpi");
        expected.add("xxhdpi");

        assertEquals(1, debugOutputs.size());
        AndroidArtifactOutput output = debugOutputs.iterator().next();
        //assertEquals(5, output.getOutputs().size());
        for (OutputFile outputFile : output.getOutputs()) {
            String densityFilter = getFilter(outputFile, OutputFile.DENSITY);
            assertEquals(densityFilter == null ? OutputFile.MAIN : OutputFile.SPLIT,
                    outputFile.getOutputType());

            // with pure splits, all split have the same version code.
            assertEquals(12, output.getVersionCode());
            expected.remove(densityFilter);
        }

        // this checks we didn't miss any expected output.
        //assertTrue(expected.isEmpty());
    }

    public void testAbiSplitOutputs() throws Exception {
        // Load the custom model for the project
        ProjectData projectData = getModelForProject(FOLDER_TEST_SAMPLE, "ndkSanAngeles");

        AndroidProject model = projectData.model;

        Collection<Variant> variants = model.getVariants();
        assertEquals("Variant Count", 2, variants.size());

        // get the main artifact of the debug artifact
        Variant debugVariant = getVariant(variants, DEBUG);
        assertNotNull("debug Variant null-check", debugVariant);
        AndroidArtifact debugMainArficat = debugVariant.getMainArtifact();
        assertNotNull("Debug main info null-check", debugMainArficat);

        // get the outputs.
        Collection<AndroidArtifactOutput> debugOutputs = debugMainArficat.getOutputs();
        assertNotNull(debugOutputs);
        assertEquals(3, debugOutputs.size());

        // build a map of expected outputs and their versionCode
        Map<String, Integer> expected = Maps.newHashMapWithExpectedSize(5);
        expected.put("armeabi-v7a", 1000123);
        expected.put("mips", 2000123);
        expected.put("x86", 3000123);

        assertEquals(3, debugOutputs.size());
        for (AndroidArtifactOutput output : debugOutputs) {
            Collection<? extends OutputFile> outputFiles = output.getOutputs();
            assertEquals(1, outputFiles.size());
            for (FilterData filterData : outputFiles.iterator().next().getFilters()) {
                if (filterData.getFilterType().equals(OutputFile.ABI)) {
                    String abiFilter = filterData.getIdentifier();
                    Integer value = expected.get(abiFilter);
                    // this checks we're not getting an unexpected output.
                    assertNotNull("Check Valid output: " + abiFilter, value);

                    assertEquals(value.intValue(), output.getVersionCode());
                    expected.remove(abiFilter);
                }
            }
        }

        // this checks we didn't miss any expected output.
        assertTrue(expected.isEmpty());
    }

    public void testMigrated() throws Exception {
        // Load the custom model for the project
        ProjectData projectData = getModelForProject(FOLDER_TEST_SAMPLE, "migrated");

        AndroidProject model = projectData.model;
        File projectDir = projectData.projectDir;

        assertNotNull("Model Object null-check", model);
        assertEquals("Model Name", "migrated", model.getName());
        assertFalse("Library Project", model.isLibrary());

        ProductFlavorContainer defaultConfig = model.getDefaultConfig();

        new SourceProviderTester(model.getName(), projectDir,
                "main", defaultConfig.getSourceProvider())
                .setJavaDir("src")
                .setResourcesDir("src")
                .setAidlDir("src")
                .setRenderscriptDir("src")
                .setResDir("res")
                .setAssetsDir("assets")
                .setManifestFile("AndroidManifest.xml")
                .test();

        SourceProviderContainer testSourceProviderContainer = getSourceProviderContainer(
                defaultConfig.getExtraSourceProviders(), ARTIFACT_ANDROID_TEST);
        assertNotNull("InstrumentTest source Providers null-check", testSourceProviderContainer);

        new SourceProviderTester(model.getName(), projectDir,
                ANDROID_TEST, testSourceProviderContainer.getSourceProvider())
                .setJavaDir("tests/java")
                .setResourcesDir("tests/resources")
                .setAidlDir("tests/aidl")
                .setJniDir("tests/jni")
                .setRenderscriptDir("tests/rs")
                .setResDir("tests/res")
                .setAssetsDir("tests/assets")
                .setManifestFile("tests/AndroidManifest.xml")
                .test();
    }

    public void testRenamedApk() throws Exception {
        // Load the custom model for the project
        ProjectData projectData = getModelForProject(FOLDER_TEST_SAMPLE, "renamedApk");

        AndroidProject model = projectData.model;
        File projectDir = projectData.projectDir;

        assertNotNull("Model Object null-check", model);
        assertEquals("Model Name", "renamedApk", model.getName());

        Collection<Variant> variants = model.getVariants();
        assertEquals("Variant Count", 2 , variants.size());

        File buildDir = new File(projectDir, "build");

        for (Variant variant : variants) {
            AndroidArtifact mainInfo = variant.getMainArtifact();
            assertNotNull(
                    "Null-check on mainArtifactInfo for " + variant.getDisplayName(),
                    mainInfo);

            AndroidArtifactOutput output = mainInfo.getOutputs().iterator().next();

            assertEquals("Output file for " + variant.getName(),
                    new File(buildDir, variant.getName() + ".apk"),
                    output.getMainOutputFile().getOutputFile());
        }
    }

    public void testFilteredOutBuildType() throws Exception {
        // Load the custom model for the project
        ProjectData projectData = getModelForProject(FOLDER_TEST_SAMPLE, "filteredOutBuildType");

        AndroidProject model = projectData.model;

        assertEquals("Variant Count", 1, model.getVariants().size());
        Variant variant = model.getVariants().iterator().next();
        assertEquals("Variant name", "release", variant.getBuildType());
    }

    public void testFilteredOutVariants() throws Exception {
        // Load the custom model for the project
        ProjectData projectData = getModelForProject(FOLDER_TEST_SAMPLE, "filteredOutVariants");

        AndroidProject model = projectData.model;

        Collection<Variant> variants = model.getVariants();
        // check we have the right number of variants:
        // arm/cupcake, arm/gingerbread, x86/gingerbread, mips/gingerbread
        // all 4 in release and debug
        assertEquals("Variant Count", 8, variants.size());

        for (Variant variant : variants) {
            List<String> flavors = variant.getProductFlavors();
            assertFalse("check ignored x86/cupcake", flavors.contains("x68") && flavors.contains("cupcake"));
            assertFalse("check ignored mips/cupcake", flavors.contains("mips") && flavors.contains("cupcake"));
        }
    }

    public void testFlavors() throws Exception {
        // Load the custom model for the project
        ProjectData projectData = getModelForProject(FOLDER_TEST_SAMPLE, "flavors");

        AndroidProject model = projectData.model;
        File projectDir = projectData.projectDir;

        assertNotNull("Model Object null-check", model);
        assertEquals("Model Name", "flavors", model.getName());
        assertFalse("Library Project", model.isLibrary());

        ProductFlavorContainer defaultConfig = model.getDefaultConfig();

        new SourceProviderTester(model.getName(), projectDir,
                "main", defaultConfig.getSourceProvider())
                .test();

        SourceProviderContainer testSourceProviderContainer = getSourceProviderContainer(
                defaultConfig.getExtraSourceProviders(), ARTIFACT_ANDROID_TEST);
        assertNotNull("InstrumentTest source Providers null-check", testSourceProviderContainer);

        new SourceProviderTester(model.getName(), projectDir,
                ANDROID_TEST, testSourceProviderContainer.getSourceProvider())
                .test();

        Collection<BuildTypeContainer> buildTypes = model.getBuildTypes();
        assertEquals("Build Type Count", 2, buildTypes.size());

        Collection<Variant> variants = model.getVariants();
        assertEquals("Variant Count", 8, variants.size());

        Variant f1faDebugVariant = getVariant(variants, "f1FaDebug");
        assertNotNull("f1faDebug Variant null-check", f1faDebugVariant);
        new ProductFlavorTester(f1faDebugVariant.getMergedFlavor(), "F1faDebug Merged Flavor")
                .test();
        new VariantTester(f1faDebugVariant, projectDir, "flavors-f1-fa-debug.apk").test();
=======
>>>>>>> BRANCH (96128f Merge "Move most AndroidProjectTest to the new integ-tests.")
    }

    public void testTicTacToe() throws Exception {
        Map<String, ProjectData> map = getModelForMultiProject(FOLDER_TEST_SAMPLE, "tictactoe");

        ProjectData libModelData = map.get(":lib");
        assertNotNull("lib module model null-check", libModelData);
        assertTrue("lib module library flag", libModelData.model.isLibrary());

        ProjectData appModelData = map.get(":app");
        assertNotNull("app module model null-check", appModelData);

        Collection<Variant> variants = appModelData.model.getVariants();
        Variant debugVariant = getVariant(variants, DEBUG);
        assertNotNull("debug variant null-check", debugVariant);

        Dependencies dependencies = debugVariant.getMainArtifact().getDependencies();
        assertNotNull(dependencies);

        Collection<AndroidLibrary> libs = dependencies.getLibraries();
        assertNotNull(libs);
        assertEquals(1, libs.size());

        AndroidLibrary androidLibrary = libs.iterator().next();
        assertNotNull(androidLibrary);

        assertEquals("Dependency project path", ":lib", androidLibrary.getProject());

        // TODO: right now we can only test the folder name efficiently
        assertTrue(androidLibrary.getFolder().getPath().endsWith("/tictactoe/lib/unspecified"));
    }

    public void testFlavorLib() throws Exception {
        Map<String, ProjectData> map = getModelForMultiProject(FOLDER_TEST_SAMPLE, "flavorlib");

        ProjectData appModelData = map.get(":app");
        assertNotNull("Module app null-check", appModelData);
        AndroidProject model = appModelData.model;

        assertFalse("Library Project", model.isLibrary());

        Collection<Variant> variants = model.getVariants();
        Collection<ProductFlavorContainer> productFlavors = model.getProductFlavors();

        ProductFlavorContainer flavor1 = getProductFlavor(productFlavors, "flavor1");
        assertNotNull(flavor1);

        Variant flavor1Debug = getVariant(variants, "flavor1Debug");
        assertNotNull(flavor1Debug);

        Dependencies dependencies = flavor1Debug.getMainArtifact().getDependencies();
        assertNotNull(dependencies);
        Collection<AndroidLibrary> libs = dependencies.getLibraries();
        assertNotNull(libs);
        assertEquals(1, libs.size());
        AndroidLibrary androidLibrary = libs.iterator().next();
        assertNotNull(androidLibrary);
        assertEquals(":lib1", androidLibrary.getProject());
        // TODO: right now we can only test the folder name efficiently
        assertTrue(androidLibrary.getFolder().getPath(), androidLibrary.getFolder().getPath().endsWith("/flavorlib/lib1/unspecified"));

        ProductFlavorContainer flavor2 = getProductFlavor(productFlavors, "flavor2");
        assertNotNull(flavor2);

        Variant flavor2Debug = getVariant(variants, "flavor2Debug");
        assertNotNull(flavor2Debug);

        dependencies = flavor2Debug.getMainArtifact().getDependencies();
        assertNotNull(dependencies);
        libs = dependencies.getLibraries();
        assertNotNull(libs);
        assertEquals(1, libs.size());
        androidLibrary = libs.iterator().next();
        assertNotNull(androidLibrary);
        assertEquals(":lib2", androidLibrary.getProject());
        // TODO: right now we can only test the folder name efficiently
        assertTrue(androidLibrary.getFolder().getPath(), androidLibrary.getFolder().getPath().endsWith("/flavorlib/lib2/unspecified"));
    }

    public void testFlavoredLib() throws Exception {
        Map<String, ProjectData> map = getModelForMultiProject(FOLDER_TEST_SAMPLE, "flavoredlib");

        ProjectData appModelData = map.get(":app");
        assertNotNull("Module app null-check", appModelData);
        AndroidProject model = appModelData.model;

        assertFalse("Library Project", model.isLibrary());

        Collection<Variant> variants = model.getVariants();
        Collection<ProductFlavorContainer> productFlavors = model.getProductFlavors();

        ProductFlavorContainer flavor1 = getProductFlavor(productFlavors, "flavor1");
        assertNotNull(flavor1);

        Variant flavor1Debug = getVariant(variants, "flavor1Debug");
        assertNotNull(flavor1Debug);

        Dependencies dependencies = flavor1Debug.getMainArtifact().getDependencies();
        assertNotNull(dependencies);
        Collection<AndroidLibrary> libs = dependencies.getLibraries();
        assertNotNull(libs);
        assertEquals(1, libs.size());
        AndroidLibrary androidLibrary = libs.iterator().next();
        assertNotNull(androidLibrary);
        assertEquals(":lib", androidLibrary.getProject());
        assertEquals("flavor1Release", androidLibrary.getProjectVariant());
        // TODO: right now we can only test the folder name efficiently
        assertTrue(androidLibrary.getFolder().getPath(), androidLibrary.getFolder().getPath().endsWith("/flavoredlib/lib/unspecified/flavor1Release"));

        ProductFlavorContainer flavor2 = getProductFlavor(productFlavors, "flavor2");
        assertNotNull(flavor2);

        Variant flavor2Debug = getVariant(variants, "flavor2Debug");
        assertNotNull(flavor2Debug);

        dependencies = flavor2Debug.getMainArtifact().getDependencies();
        assertNotNull(dependencies);
        libs = dependencies.getLibraries();
        assertNotNull(libs);
        assertEquals(1, libs.size());
        androidLibrary = libs.iterator().next();
        assertNotNull(androidLibrary);
        assertEquals(":lib", androidLibrary.getProject());
        assertEquals("flavor2Release", androidLibrary.getProjectVariant());
        // TODO: right now we can only test the folder name efficiently
        assertTrue(androidLibrary.getFolder().getPath(), androidLibrary.getFolder().getPath().endsWith("/flavoredlib/lib/unspecified/flavor2Release"));
    }

    public void testMultiproject() throws Exception {
        Map<String, ProjectData> map = getModelForMultiProject(FOLDER_TEST_SAMPLE, "multiproject");

        ProjectData baseLibModelData = map.get(":baseLibrary");
        assertNotNull("Module app null-check", baseLibModelData);
        AndroidProject model = baseLibModelData.model;

        Collection<Variant> variants = model.getVariants();
        assertEquals("Variant count", 2, variants.size());

        Variant variant = getVariant(variants, "release");
        assertNotNull("release variant null-check", variant);

        AndroidArtifact mainInfo = variant.getMainArtifact();
        assertNotNull("Main Artifact null-check", mainInfo);

        Dependencies dependencies = mainInfo.getDependencies();
        assertNotNull("Dependencies null-check", dependencies);

        Collection<String> projects = dependencies.getProjects();
        assertNotNull("project dep list null-check", projects);
        assertEquals("project dep count", 1, projects.size());
        assertEquals("dep on :util check", ":util", projects.iterator().next());

        Collection<JavaLibrary> javaLibraries = dependencies.getJavaLibraries();
        assertNotNull("jar dep list null-check", javaLibraries);
        // TODO these are jars coming from ':util' They shouldn't be there.
        assertEquals("jar dep count", 2, javaLibraries.size());
    }


    public void testCustomArtifact() throws Exception {
        // Load the custom model for the projects
        Map<String, ProjectData> map = getModelForMultiProject(FOLDER_TEST_PROJECT,
                "customArtifactDep");

        ProjectData appModelData = map.get(":app");
        assertNotNull("Module app null-check", appModelData);
        AndroidProject model = appModelData.model;

        Collection<Variant> variants = model.getVariants();
        assertEquals("Variant count", 2, variants.size());

        Variant variant = getVariant(variants, "release");
        assertNotNull("release variant null-check", variant);

        AndroidArtifact mainInfo = variant.getMainArtifact();
        assertNotNull("Main Artifact null-check", mainInfo);

        Dependencies dependencies = mainInfo.getDependencies();
        assertNotNull("Dependencies null-check", dependencies);

        Collection<String> projects = dependencies.getProjects();
        assertNotNull("project dep list null-check", projects);
        assertTrue("project dep empty check", projects.isEmpty());

        Collection<JavaLibrary> javaLibraries = dependencies.getJavaLibraries();
        assertNotNull("jar dep list null-check", javaLibraries);
        assertEquals("jar dep count", 1, javaLibraries.size());
    }

    public void testLocalJarInLib() throws Exception {
        Map<String, ProjectData> map = getModelForMultiProject(FOLDER_TEST_SAMPLE, "localJars");

        ProjectData libModelData = map.get(":baseLibrary");
        assertNotNull("Module app null-check", libModelData);
        AndroidProject model = libModelData.model;

        Collection<Variant> variants = model.getVariants();

        Variant releaseVariant = getVariant(variants, "release");
        assertNotNull(releaseVariant);

        Dependencies dependencies = releaseVariant.getMainArtifact().getDependencies();
        assertNotNull(dependencies);

        Collection<JavaLibrary> javaLibraries = dependencies.getJavaLibraries();
        assertNotNull(javaLibraries);

        //  com.google.guava:guava:11.0.2
        //  \--- com.google.code.findbugs:jsr305:1.3.9
        //  + the local jar
        assertEquals(3, javaLibraries.size());
    }


    /**
     * Returns the root dir for the gradle plugin project
     */
    private File getRootDir() {
        CodeSource source = getClass().getProtectionDomain().getCodeSource();
        if (source != null) {
            URL location = source.getLocation();
            try {
                File dir = new File(location.toURI());
                assertTrue(dir.getPath(), dir.exists());

                File f;
                if (System.getenv("IDE_MODE") != null) {
                    f = dir.getParentFile().getParentFile().getParentFile();
                    f = new File(f, "build-system");
                } else {
                    f = dir.getParentFile().getParentFile().getParentFile().getParentFile().getParentFile().getParentFile().getParentFile();
                    f = new File(f, "tools" + File.separator + "base" + File.separator + "build-system");
                }
                return f;
            } catch (URISyntaxException e) {
                fail(e.getLocalizedMessage());
            }
        }

        fail("Fail to get the tools/build folder");
        return null;
    }

    /**
     * Returns the root folder for the tests projects.
     */
    private File getTestDir(@NonNull String testFolder) {
        File rootDir = getRootDir();
        return new File(new File(rootDir, "integration-test"), testFolder);
    }

    @Nullable
    private static Variant getVariant(
            @NonNull Collection<Variant> items,
            @NonNull String name) {
        for (Variant item : items) {
            if (name.equals(item.getName())) {
                return item;
            }
        }

        return null;
    }

    @Nullable
    private static ProductFlavorContainer getProductFlavor(
            @NonNull Collection<ProductFlavorContainer> items,
            @NonNull String name) {
        for (ProductFlavorContainer item : items) {
            assertNotNull("ProductFlavorContainer list item null-check:" + name, item);
            assertNotNull("ProductFlavorContainer.getProductFlavor() list item null-check: " + name, item.getProductFlavor());
            assertNotNull("ProductFlavorContainer.getProductFlavor().getName() list item null-check: " + name, item.getProductFlavor().getName());
            if (name.equals(item.getProductFlavor().getName())) {
                return item;
            }
        }

        return null;
    }
}
