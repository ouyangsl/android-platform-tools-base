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

package com.android.manifmerger;

import static com.android.manifmerger.ManifestMergerTestUtil.loadTestData;
import static com.android.manifmerger.ManifestMergerTestUtil.transformParameters;
import static com.android.manifmerger.MergingReport.Record;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.android.annotations.Nullable;
import com.android.testutils.TestUtils;
import com.android.utils.PositionXmlParser;
import com.android.utils.StdLogger;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tests for the {@link ManifestMerger2} class */
@RunWith(Parameterized.class)
public class ManifestMerger2Test {

    private static final String TEST_DATA_DIRECTORY = "data2";

    @SuppressWarnings("SpellCheckingInspection")
    private static final String[] DATA_FILES =
            new String[] {
                "00_noop",
                "03_inject_attributes.xml",
                "03b_inject_attributes_warnings.xml",
                "03c_inject_attributes_warnings.xml",
                "03d_inject_attributes_no_warnings.xml",
                "05_inject_package.xml",
                "05_inject_package_placeholder.xml",
                "05_inject_package_placeholder_not_provided.xml",
                "05_inject_package_with_overlays.xml",
                "06_inject_attributes_with_specific_prefix.xml",
                "06b_inject_profileable_application_element_only_in_overlay.xml",
                "05_inject_applicationid_not_provided.xml",
                "07_no_package_provided.xml",
                "08_no_library_package_provided.xml",
                "08b_library_injection.xml",
                "08c_empty_library_package_provided.xml",
                "09_overlay_merge_children_with_identical_key.xml",
                "09_overlay_package_provided.xml",
                "09b_overlay_package_different.xml",
                "09c_overlay_package_not_provided.xml",
                "10_activity_merge",
                "11_activity_dup",
                "12_alias_dup",
                "13_service_dup",
                "13b_service_foreground_service_type_merge.xml",
                "14_receiver_dup",
                "15_provider_dup",
                "16_fqcn_merge",
                "17_fqcn_conflict",
                "18_fqcn_success",
                "20_uses_lib_merge",
                "21_uses_main_errors",
                "22_uses_lib_errors",
                "25_permission_merge",
                "26_permission_dup",
                "28_uses_perm_merge",
                "29_uses_perm_selector",
                "29b_uses_perm_invalidSelector",
                "29c_uses_perm_unsupportedSelector",
                "30_uses_sdk_ok",
                "32_uses_sdk_minsdk_ok",
                "33_uses_sdk_minsdk_conflict",
                "33b_uses_sdk_minsdk_override.xml",
                "33c_uses_sdk_minsdk_override_and_conflict.xml",
                "33e_uses_sdk_minsdk_wildcard.xml",
                "34_inject_uses_sdk_no_dup.xml",
                "36_uses_sdk_targetsdk_warning",
                "40_uses_feat_merge",
                "41_uses_feat_errors",
                "45_uses_feat_gles_once",
                "47_uses_feat_gles_conflict",
                "50_uses_conf_warning",
                "52_support_screens_warning",
                "54_compat_screens_warning",
                "56_support_gltext_warning",
                "60_merge_order",
                "65_override_app",
                "66_remove_app",
                "67_override_activities",
                "68_override_uses",
                "69_remove_uses",
                "70_expand_fqcns",
                "75_app_metadata_merge",
                "76_app_metadata_ignore",
                "77_app_metadata_conflict",
                "78_removeAll",
                "78b_removeAllActivities",
                "78c_removeAll_invalid.xml",
                "78d_removeAll_permissions.xml",
                "79_custom_node.xml",
                "80_remove_ns_in_nodes",
                "81_do_not_duplicate_xmlns_in_nodes",
                "82_remove_tools_nodes",
                "86_delimiter_separated_values",
                "87_delimiter_separated_values_errors",
                "89_remove_uses_permission_sdk_23.xml",
                "90_main_and_library_placeholder_replacement.xml",
                "91_tools_in_lib_but_not_main.xml",
                "92_do_not_duplicate_xmlns_when_injecting_into_added_application_node.xml",
                "93_expand_nav_graphs",
                "94_add_implicit_elements_no_duplicates.xml",
                "95_enforce_unique_package_name.xml",
                "96_enforce_unique_package_name_error.xml",
                "97_enforce_unique_package_name_same_as_app_error.xml",
                "98_fail_if_package_name_does_not_contain_dot.xml",
                "99_info_severity_on_non_unique_androidx_test_package.xml",
                "100_enforce_unique_package_name_warning.xml",
                "101_enforce_unique_package_name_same_as_app_warning.xml",
                "102_tools_node_operation_ordering.xml",
                "103_attribution_node.xml",
                "104_merging_privacy_sandbox_tagged_permissions.xml",
                "104b_merging_privacy_sandbox_tagged_permissions_all_untagged.xml",
                "105_ignore_use_embedded_dex_from_library.xml",
                "106_ignore_extract_native_libs_from_library.xml",
                "107_sdk_library.xml",
                "108_uses_sdk_library.xml",
                "109_activity_feature_flag_node_addition.xml",
                "109b_activity_feature_flag_node_matching",
                "109c_activity_feature_flag_node_single_incoming_match",
                "109d_activity_feature_flag_node_multiple_incoming_match",
                "109e_uses_sdk_feature_flag_node_validation.xml",
                "109f_dup_uses_sdk_feature_flag_node_validation.xml",
                "109g_dup_uses_sdk_node_validation.xml"
            };

    private static final Multimap<Predicate<String>, ManifestMerger2.Invoker.Feature>
            TEST_CASE_FEATURES =
                    ImmutableSetMultimap
                            .<Predicate<String>, ManifestMerger2.Invoker.Feature>builder()
                            .put(
                                    testCaseIsNot("91_tools_in_lib_but_not_main.xml"),
                                    ManifestMerger2.Invoker.Feature.REMOVE_TOOLS_DECLARATIONS)
                            .put(
                                    testCaseIs(
                                            "92_do_not_duplicate_xmlns_when_injecting_into_added_application_node.xml"),
                                    ManifestMerger2.Invoker.Feature.DEBUGGABLE)
                            .put(
                                    testCaseIs("95_enforce_unique_package_name.xml"),
                                    ManifestMerger2.Invoker.Feature.ENFORCE_UNIQUE_PACKAGE_NAME)
                            .put(
                                    testCaseIs("96_enforce_unique_package_name_error.xml"),
                                    ManifestMerger2.Invoker.Feature.ENFORCE_UNIQUE_PACKAGE_NAME)
                            .put(
                                    testCaseIs("96b_enforce_unique_package_name_error.xml"),
                                    ManifestMerger2.Invoker.Feature.ENFORCE_UNIQUE_PACKAGE_NAME)
                            .put(
                                    testCaseIs("96b_enforce_unique_package_name_error.xml"),
                                    ManifestMerger2.Invoker.Feature
                                            .DISABLE_PACKAGE_NAME_UNIQUENESS_CHECK)
                            .put(
                                    testCaseIs(
                                            "97_enforce_unique_package_name_same_as_app_error.xml"),
                                    ManifestMerger2.Invoker.Feature.ENFORCE_UNIQUE_PACKAGE_NAME)
                            .build();

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> getParameters() {
        return transformParameters(DATA_FILES);
    }

    private final String fileName;

    public ManifestMerger2Test(String fileName) {
        this.fileName = fileName;
    }

    @Rule
    public Expect expect = Expect.create();

    @Test
    public void processTestFiles() throws Exception {
        ManifestMergerTestUtil.TestFiles testFiles =
                loadTestData(TEST_DATA_DIRECTORY, fileName, getClass().getSimpleName());

        // Make list of optional features
        List<ManifestMerger2.Invoker.Feature> optionalFeatures = getFeaturesForTestCase();

        if (!testFiles.getFeatures().isEmpty()) {
            throw new RuntimeException(
                    "Unexpected features " + ImmutableMap.copyOf(testFiles.getFeatures()));
        }
        StdLogger stdLogger = new StdLogger(StdLogger.Level.VERBOSE);
        ManifestMerger2.Invoker invoker =
                ManifestMerger2.newMerger(
                                testFiles.getMain(),
                                stdLogger,
                                ManifestMerger2.MergeType.APPLICATION)
                        .addLibraryManifests(testFiles.getLibs())
                        .addFlavorAndBuildTypeManifests(testFiles.getOverlayFiles())
                        .addNavigationFiles(testFiles.getNavigationFiles())
                        .addDependencyFeatureNames(testFiles.getDependencyFeatureNames())
                        .withFeatures(
                                optionalFeatures.toArray(new ManifestMerger2.Invoker.Feature[0]))
                        .withFeatures(ManifestMerger2.Invoker.Feature.KEEP_GOING_AFTER_ERRORS);

        if (!Strings.isNullOrEmpty(testFiles.getPackageOverride())) {
            invoker.setOverride(
                    ManifestSystemProperty.Document.PACKAGE, testFiles.getPackageOverride());
        }

        for (Map.Entry<String, String> injectable : testFiles.getInjectAttributes().entrySet()) {
            ManifestSystemProperty manifestSystemProperty = getSystemProperty(injectable.getKey());
            if (manifestSystemProperty != null) {
                invoker.setOverride(manifestSystemProperty, injectable.getValue());
            } else {
                invoker.setPlaceHolderValue(injectable.getKey(), injectable.getValue());
            }
        }

        MergingReport mergeReport = invoker.merge();

        // this is obviously quite hacky, refine once merge output is better defined.
        boolean expectingError = isExpectingError(testFiles.getExpectedErrors());
        mergeReport.log(stdLogger);

        String xmlDocument = mergeReport.getMergedDocument(testFiles.getResultKind());
        assertNotNull(xmlDocument);
        stdLogger.info("Resulting manifest:");
        stdLogger.info(xmlDocument);

        if (testFiles.getActualResult() != null) {
            try (FileWriter writer = new FileWriter(testFiles.getActualResult())) {
                writer.append(xmlDocument);
            }
        }

        if (mergeReport.getResult().isSuccess() && expectingError) {
            fail("Did not get expected error : " + testFiles.getExpectedErrors());
        }

        for (Record record : mergeReport.getLoggingRecords()) {
            Logger.getAnonymousLogger().info("Returned log: " + record);
        }

        if (System.getProperty("UPDATE_TEST_SNAPSHOTS") != null) {
            if (!testFiles.getExpectedResult().equals(xmlDocument)) {
                Path fileToUpdate =
                        TestUtils.resolveWorkspacePath(
                                "tools/base/build-system/manifest-merger/src/test/java/com/android/manifmerger/"
                                        + testFiles.getTestDataRelativePath());
                String contents = Files.readString(fileToUpdate, StandardCharsets.UTF_8);
                int resultLocation;
                int resultTagIndex = contents.indexOf("\n@result\n");
                if (resultTagIndex > 0) {
                    resultLocation = resultTagIndex + "\n@result\n".length();
                } else if (contents.contains("\n@result-same-as-main\n")) {
                    int mainTagIndex = contents.indexOf("\n@main\n");
                    resultLocation = mainTagIndex + "\n@main\n".length();
                } else {
                    throw new RuntimeException("No result found");
                }
                int nextBlockLocation = contents.indexOf("\n@", resultLocation + 1);
                if (nextBlockLocation < 0) {
                    nextBlockLocation = contents.length();
                }
                String newContents =
                        contents.substring(0, resultLocation)
                                + "\n"
                                + xmlDocument
                                + "\n"
                                + contents.substring(nextBlockLocation);
                Files.writeString(fileToUpdate, newContents, StandardCharsets.UTF_8);
            }
        }
        String expectedResult = testFiles.getExpectedResult();
        String actualResult = xmlDocument.trim().replace("\r\n", "\n");
        expect.that(actualResult).isEqualTo(expectedResult.trim());
        expect.that(PositionXmlParser.parse(actualResult))
                .named("output xml can be parsed")
                .isNotNull();

        // process any warnings.
        compareExpectedAndActualErrors(mergeReport, testFiles.getExpectedErrors());
        expect.that(mergeReport.getResult().isSuccess())
                .named("mergeReport.getResult().isSuccess()")
                .isEqualTo(!expectingError);

    }

    private List<ManifestMerger2.Invoker.Feature> getFeaturesForTestCase() {
        List<ManifestMerger2.Invoker.Feature> optionalFeatures = new ArrayList<>();
        optionalFeatures.add(ManifestMerger2.Invoker.Feature.KEEP_INTERMEDIARY_STAGES);

        TEST_CASE_FEATURES
                .asMap()
                .entrySet()
                .stream()
                .filter(e -> e.getKey().test(fileName))
                .forEach(e -> optionalFeatures.addAll(e.getValue()));

        return optionalFeatures;
    }

    private static boolean isExpectingError(String expectedOutput) throws IOException {
        StringReader stringReader = new StringReader(expectedOutput);
        try (BufferedReader reader = new BufferedReader(stringReader)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("ERROR")) {
                    return true;
                }
            }
            return false;
        }
    }

    private static void compareExpectedAndActualErrors(
            MergingReport mergeReport,
            String expectedOutput) throws IOException {

        StringReader stringReader = new StringReader(expectedOutput);
        List<Record> records = new ArrayList<>(mergeReport.getLoggingRecords());
        try (BufferedReader reader = new BufferedReader(stringReader)) {
            String line = reader.readLine();
            while (line != null) {
                String message = line;
                do {
                    line = reader.readLine();
                    if (line != null && line.startsWith("    ")) {
                        message = message + "\n" + line;
                    }
                } while (line != null && line.startsWith("    "));

                // next might generate an exception which will make the test fail when we
                // get unexpected error message.
                if (!message.isEmpty() && !findLineInRecords(message, records)) {

                    StringBuilder errorMessage = new StringBuilder();
                    dumpRecords(records, errorMessage);
                    errorMessage.append("Cannot find expected error : \n").append(message);
                    fail(errorMessage.toString());
                }
            }
        }
        // check that we do not have any unexpected error messages.
        if (!records.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append("Unexpected error message(s), see below:");
            dumpRecords(records, message);
            fail(message.toString());
        }
    }

    private static boolean findLineInRecords(String errorLine, List<Record> records) {
        String severity = errorLine.substring(0, errorLine.indexOf(':'));
        String message = errorLine.substring(errorLine.indexOf(':') + 1);
        for (Record record : records) {
            int indexOfSuggestions = record.getMessage().indexOf("\n\tSuggestion:");
            String messageRecord = indexOfSuggestions != -1
                    ? record.getMessage().substring(0, indexOfSuggestions)
                    : record.getMessage();
            Pattern pattern = Pattern.compile(message);
            Matcher matcher = pattern.matcher(messageRecord.replaceAll("\t", "    "));
            if (matcher.matches() && record.getSeverity() == Record.Severity.valueOf(severity)) {
                records.remove(record);
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static ManifestSystemProperty getSystemProperty(String name) {
        for (ManifestSystemProperty manifestElement : ManifestSystemProperty.getValues()) {
            if (ManifestSystemPropertyKt.toCamelCase(manifestElement).equals(name)) {
                return manifestElement;
            }
        }
        return null;
    }

    private static void dumpRecords(List<Record> records, StringBuilder stringBuilder) {
        stringBuilder.append("\n------------ Records : \n");
        for (Record record : records) {
            stringBuilder.append(record.toString());
            stringBuilder.append("\n");
        }
        stringBuilder.append("------------ End of records.\n");
    }

    private static Predicate<String> testCaseIs(String fileName) {
        return s -> Objects.equals(s, fileName);
    }

    private static Predicate<String> testCaseIsNot(String fileName) {
        return s -> !Objects.equals(s, fileName);
    }
}
