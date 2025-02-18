/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.apk.analyzer;

import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.testutils.TestResources;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;

import com.google.common.primitives.Longs;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ArchiveTreeStructureTest {
    private ILogger logger = new StdLogger(StdLogger.Level.VERBOSE);
    private ArchiveContext archiveContext;
    private ArchiveNode root;

    @Before
    public void setup() throws IOException {
        archiveContext =
                Archives.open(
                        TestResources.getFile("/test_with_error_injection.apk").toPath(), logger);
        root = ArchiveTreeStructure.create(archiveContext);
    }

    @After
    public void tearDown() throws IOException {
        archiveContext.close();
    }

    @Test
    public void create() {
        String actual = dumpTree(root, n -> n.getData().getSummaryDisplayString());
        String expected =
                "/\n"
                    + "/res/\n"
                    + "/res/anim/\n"
                    + "/res/anim/fade.xml\n"
                    + "/instant-run.zip\n"
                    + "/instant-run.zip/instant-run/\n"
                    + "/instant-run.zip/instant-run/classes1.dex\n"
                    + "/instant-run-truncated.zip\n"
                    + "/instant-run-truncated.zip/ - Error processing entry: java.io.EOFException\n"
                    + "/bar.jar\n"
                    + "/bar.jar/ - Error processing entry: java.util.zip.ZipError: No valid"
                    + " contents inside\n"
                    + "/AndroidManifest.xml";
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void updateFileInfo() {
        ArchiveTreeStructure.updateFileInfo(root, ApkSizeCalculator.getDefault());
        String actual =
                dumpTree(
                        root,
                        n -> {
                            ArchiveEntry entry = n.getData();
                            return String.format(
                                    Locale.US,
                                    "%1$-10d %2$-15s %3$s",
                                    entry.getRawFileSize(),
                                    getCompressedString(n),
                                    entry.getSummaryDisplayString());
                        });
        String expected =
                "251                        /\n"
                    + "6                          /res/\n"
                    + "6                          /res/anim/\n"
                    + "6          Uncompressed    /res/anim/fade.xml\n"
                    + "150                        /instant-run.zip\n"
                    + "2                          /instant-run.zip/instant-run/\n"
                    + "2          Uncompressed    /instant-run.zip/instant-run/classes1.dex\n"
                    + "75                         /instant-run-truncated.zip\n"
                    + "0          Uncompressed    /instant-run-truncated.zip/ - Error processing"
                    + " entry: java.io.EOFException\n"
                    + "9                          /bar.jar\n"
                    + "0          Uncompressed    /bar.jar/ - Error processing entry:"
                    + " java.util.zip.ZipError: No valid contents inside\n"
                    + "11         Compressed      /AndroidManifest.xml";
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void updateDownloadFileSizes() {
        ArchiveTreeStructure.updateDownloadFileSizes(root, ApkSizeCalculator.getDefault());

        String actual =
                dumpTree(
                        root,
                        n -> {
                            ArchiveEntry entry = n.getData();
                            return String.format(
                                    Locale.US,
                                    "%1$-10d %2$s",
                                    entry.getDownloadFileSize(),
                                    entry.getSummaryDisplayString());
                        });
        String expected =
                "349        /\n"
                    + "26         /res/\n"
                    + "26         /res/anim/\n"
                    + "26         /res/anim/fade.xml\n"
                    + "171        /instant-run.zip\n"
                    + "22         /instant-run.zip/instant-run/\n"
                    + "22         /instant-run.zip/instant-run/classes1.dex\n"
                    + "94         /instant-run-truncated.zip\n"
                    + "0          /instant-run-truncated.zip/ - Error processing entry:"
                    + " java.io.EOFException\n"
                    + "29         /bar.jar\n"
                    + "0          /bar.jar/ - Error processing entry: java.util.zip.ZipError: No"
                    + " valid contents inside\n"
                    + "29         /AndroidManifest.xml";
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void sort() {
        ArchiveTreeStructure.updateFileInfo(root, ApkSizeCalculator.getDefault());
        ArchiveTreeStructure.sort(
                root,
                (o1, o2) ->
                        Longs.compare(
                                o2.getData().getRawFileSize(), o1.getData().getRawFileSize()));
        String actual =
                dumpTree(
                        root,
                        n -> {
                            ArchiveEntry entry = n.getData();
                            return String.format(
                                    Locale.US,
                                    "%1$-10d %2$s",
                                    entry.getRawFileSize(),
                                    entry.getSummaryDisplayString());
                        });
        String expected =
                "251        /\n"
                    + "150        /instant-run.zip\n"
                    + "2          /instant-run.zip/instant-run/\n"
                    + "2          /instant-run.zip/instant-run/classes1.dex\n"
                    + "75         /instant-run-truncated.zip\n"
                    + "0          /instant-run-truncated.zip/ - Error processing entry:"
                    + " java.io.EOFException\n"
                    + "11         /AndroidManifest.xml\n"
                    + "9          /bar.jar\n"
                    + "0          /bar.jar/ - Error processing entry: java.util.zip.ZipError: No"
                    + " valid contents inside\n"
                    + "6          /res/\n"
                    + "6          /res/anim/\n"
                    + "6          /res/anim/fade.xml";
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void checkCollisions() throws IOException {
        archiveContext =
                Archives.open(TestResources.getFile("/test_collision.apk").toPath(), logger);
        root = ArchiveTreeStructure.create(archiveContext);

        ArchiveTreeStructure.updateFileInfo(root, ApkSizeCalculator.getDefault());
        ArchiveTreeStructure.sort(
                root,
                (o1, o2) ->
                        Longs.compare(
                                o2.getData().getRawFileSize(), o1.getData().getRawFileSize()));
        String actual =
                dumpTree(
                        root,
                        n -> {
                            ArchiveEntry entry = n.getData();
                            return String.format(
                                    Locale.US,
                                    "%1$-10d %2$s",
                                    entry.getRawFileSize(),
                                    entry.getSummaryDisplayString());
                        });
        String expected =
                "10814      /\n"
                        + "7778       /res/\n"
                        + "7778       /res/ic_launcher_round.webp\n"
                        + "3036       /assets/\n"
                        + "3036       /assets/plugin.apk\n"
                        + "2898       /assets/plugin.apk/res/\n"
                        + "2898       /assets/plugin.apk/res/ic_launcher_round.webp";
        assertThat(actual).isEqualTo(expected);
    }

    private static String dumpTree(
            @NonNull ArchiveNode root, @NonNull Function<ArchiveNode, String> mapper) {
        return ArchiveTreeStream.preOrderStream(root).map(mapper).collect(Collectors.joining("\n"));
    }

    private String getCompressedString(ArchiveNode node) {
        if (!node.isLeaf()) {
            return "";
        }
        return node.getData().isFileCompressed() ? "Compressed" : "Uncompressed";
    }
}
