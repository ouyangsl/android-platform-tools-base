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

package com.android.build.gradle.integration.application;

import static com.android.testutils.truth.PathSubject.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtilsV2;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.generators.ResValueGenerator;
import com.android.builder.model.v2.dsl.BuildType;
import com.android.builder.model.v2.dsl.ClassField;
import com.android.builder.model.v2.dsl.ProductFlavor;
import com.android.builder.model.v2.models.AndroidDsl;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Test for Res Values declared in build type, flavors, and variant and how they override each other
 */
public class ResValueTest {
    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestApp(HelloWorldApp.noBuildFile()).create();

    private static AndroidDsl dslModel;

    @BeforeClass
    public static void setUp() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "            apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "            android {\n"
                        + "                namespace \""
                        + HelloWorldApp.NAMESPACE
                        + "\"\n"
                        + "                compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "                buildToolsVersion \""
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "\"\n"
                        + "\n"
                        + "                defaultConfig {\n"
                        + "                    resValue \"string\", \"VALUE_DEFAULT\", \"1\"\n"
                        + "                    resValue \"string\", \"VALUE_DEBUG\",   \"1\"\n"
                        + "                    resValue \"string\", \"VALUE_FLAVOR\",  \"1\"\n"
                        + "                    resValue \"string\", \"VALUE_VARIANT\", \"1\"\n"
                        + "                }\n"
                        + "\n"
                        + "                buildTypes {\n"
                        + "                    debug {\n"
                        + "                        resValue \"string\", \"VALUE_DEBUG\",   \"100\"\n"
                        + "                        resValue \"string\", \"VALUE_VARIANT\", \"100\"\n"
                        + "                    }\n"
                        + "                }\n"
                        + "\n"
                        + "                flavorDimensions 'foo'\n"
                        + "                productFlavors {\n"
                        + "                    flavor1 {\n"
                        + "                        resValue \"string\", \"VALUE_DEBUG\",   \"10\"\n"
                        + "                        resValue \"string\", \"VALUE_FLAVOR\",  \"10\"\n"
                        + "                        resValue \"string\", \"VALUE_VARIANT\", \"10\"\n"
                        + "                    }\n"
                        + "                    flavor2 {\n"
                        + "                        resValue \"string\", \"VALUE_DEBUG\",   \"20\"\n"
                        + "                        resValue \"string\", \"VALUE_FLAVOR\",  \"20\"\n"
                        + "                        resValue \"string\", \"VALUE_VARIANT\", \"20\"\n"
                        + "                    }\n"
                        + "                }\n"
                        + "\n"
                        + "                applicationVariants.all { variant ->\n"
                        + "                    if (variant.buildType.name == \"debug\") {\n"
                        + "                        variant.resValue \"string\", \"VALUE_VARIANT\", \"1000\"\n"
                        + "                    }\n"
                        + "                }\n"
                        + "            }\n"
                        + "            ");

        project.execute(
                "clean",
                "generateFlavor1DebugResValue",
                "generateFlavor1ReleaseResValue",
                "generateFlavor2DebugResValue",
                "generateFlavor2ReleaseResValue");

        dslModel = project.modelV2().fetchModels().getContainer().getProject().getAndroidDsl();
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
        dslModel = null;
    }

    @Test
    public void buildFlavor1Debug() {
        String expected =
                ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources>\n"
                        + "\n"
                        + "    <!-- Automatically generated file. DO NOT MODIFY -->\n"
                        + "\n"
                        + "    <!-- Value from build type: debug -->\n"
                        + "    <string name=\"VALUE_DEBUG\" translatable=\"false\">100</string>\n"
                        + "    <!-- Value from the variant -->\n"
                        + "    <string name=\"VALUE_VARIANT\" translatable=\"false\">1000</string>\n"
                        + "    <!-- Value from product flavor: flavor1 -->\n"
                        + "    <string name=\"VALUE_FLAVOR\" translatable=\"false\">10</string>\n"
                        + "    <!-- Value from default config. -->\n"
                        + "    <string name=\"VALUE_DEFAULT\" translatable=\"false\">1</string>\n"
                        + "\n"
                        + "</resources>";
        checkBuildConfig(expected, "flavor1/debug");
    }

    @Test
    public void modelFlavor1() {
        Map<String, String> map = Maps.newHashMap();
        map.put("string/VALUE_FLAVOR", "10");
        map.put("string/VALUE_DEBUG", "10");
        map.put("string/VALUE_VARIANT", "10");
        checkFlavor(dslModel, "flavor1", map);
    }

    @Test
    public void buildFlavor2Debug() throws IOException {
        String expected =
                ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources>\n"
                        + "\n"
                        + "    <!-- Automatically generated file. DO NOT MODIFY -->\n"
                        + "\n"
                        + "    <!-- Value from build type: debug -->\n"
                        + "    <string name=\"VALUE_DEBUG\" translatable=\"false\">100</string>\n"
                        + "    <!-- Value from the variant -->\n"
                        + "    <string name=\"VALUE_VARIANT\" translatable=\"false\">1000</string>\n"
                        + "    <!-- Value from product flavor: flavor2 -->\n"
                        + "    <string name=\"VALUE_FLAVOR\" translatable=\"false\">20</string>\n"
                        + "    <!-- Value from default config. -->\n"
                        + "    <string name=\"VALUE_DEFAULT\" translatable=\"false\">1</string>\n"
                        + "\n"
                        + "</resources>";
        checkBuildConfig(expected, "flavor2/debug");
    }

    @Test
    public void modelFlavor2() {
        Map<String, String> map = Maps.newHashMap();
        map.put("string/VALUE_FLAVOR", "20");
        map.put("string/VALUE_DEBUG", "20");
        map.put("string/VALUE_VARIANT", "20");
        checkFlavor(dslModel, "flavor2", map);
    }

    @Test
    public void buildFlavor1Release() {
        String expected =
                ""
                        + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources>\n"
                        + "\n"
                        + "    <!-- Automatically generated file. DO NOT MODIFY -->\n"
                        + "\n"
                        + "    <!-- Value from product flavor: flavor1 -->\n"
                        + "    <string name=\"VALUE_DEBUG\" translatable=\"false\">10</string>\n"
                        + "    <!-- Value from product flavor: flavor1 -->\n"
                        + "    <string name=\"VALUE_FLAVOR\" translatable=\"false\">10</string>\n"
                        + "    <!-- Value from product flavor: flavor1 -->\n"
                        + "    <string name=\"VALUE_VARIANT\" translatable=\"false\">10</string>\n"
                        + "    <!-- Value from default config. -->\n"
                        + "    <string name=\"VALUE_DEFAULT\" translatable=\"false\">1</string>\n"
                        + "\n"
                        + "</resources>";
        checkBuildConfig(expected, "flavor1/release");
    }

    @Test
    public void modelDebug() {
        Map<String, String> map = Maps.newHashMap();
        map.put("string/VALUE_DEBUG", "100");
        map.put("string/VALUE_VARIANT", "100");
        checkBuildType(dslModel, "debug", map);
    }

    @Test
    public void buildFlavor2Release() {
        String expected =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<resources>\n"
                        + "\n"
                        + "    <!-- Automatically generated file. DO NOT MODIFY -->\n"
                        + "\n"
                        + "    <!-- Value from product flavor: flavor2 -->\n"
                        + "    <string name=\"VALUE_DEBUG\" translatable=\"false\">20</string>\n"
                        + "    <!-- Value from product flavor: flavor2 -->\n"
                        + "    <string name=\"VALUE_FLAVOR\" translatable=\"false\">20</string>\n"
                        + "    <!-- Value from product flavor: flavor2 -->\n"
                        + "    <string name=\"VALUE_VARIANT\" translatable=\"false\">20</string>\n"
                        + "    <!-- Value from default config. -->\n"
                        + "    <string name=\"VALUE_DEFAULT\" translatable=\"false\">1</string>\n"
                        + "\n"
                        + "</resources>";
        checkBuildConfig(expected, "flavor2/release");
    }

    @Test
    public void modelRelease() {
        Map<String, String> map = Maps.newHashMap();
        checkBuildType(dslModel, "release", map);
    }

    @Test
    public void modelDefaultConfig() {
        Map<String, String> map = Maps.newHashMap();
        map.put("string/VALUE_DEFAULT", "1");
        map.put("string/VALUE_FLAVOR", "1");
        map.put("string/VALUE_DEBUG", "1");
        map.put("string/VALUE_VARIANT", "1");

        checkMaps(map, dslModel.getDefaultConfig().getResValues(), "DefaultConfig");
    }

    private static void checkBuildConfig(@NonNull String expected, @NonNull String variantDir) {
        File outputFile =
                new File(
                        project.getProjectDir(),
                        "build/generated/res/resValues/"
                                + variantDir
                                + "/values/"
                                + ResValueGenerator.RES_VALUE_FILENAME_XML);
        assertThat(outputFile).isFile();
        assertThat(outputFile).contentWithUnixLineSeparatorsIsExactly(expected);
    }

    private static void checkFlavor(
            @NonNull AndroidDsl androidDsl,
            @NonNull final String flavorName,
            @Nullable Map<String, String> valueMap) {
        ProductFlavor productFlavor =
                AndroidProjectUtilsV2.getProductFlavor(androidDsl, flavorName);
        assertNotNull(flavorName + " flavor null-check", productFlavor);
        checkMaps(valueMap, productFlavor.getResValues(), flavorName);
    }

    private static void checkBuildType(
            @NonNull AndroidDsl androidDsl,
            @NonNull final String buildTypeName,
            @Nullable Map<String, String> valueMap) {
        BuildType buildType = AndroidProjectUtilsV2.getBuildType(androidDsl, buildTypeName);
        assertNotNull(buildTypeName + " flavor null-check", buildType);
        checkMaps(valueMap, buildType.getResValues(), buildTypeName);
    }

    private static void checkMaps(
            @Nullable Map<String, String> valueMap,
            @Nullable Map<String, ClassField> value,
            @NonNull String name) {
        assertNotNull(value);

        // check the map against the expected one.
        assertEquals(valueMap.keySet(), value.keySet());
        for (String key : valueMap.keySet()) {
            ClassField field = value.get(key);
            assertNotNull(name + ": expected field " + key, field);
            assertEquals(name + ": check Value of " + key, valueMap.get(key), field.getValue());
        }
    }
}
