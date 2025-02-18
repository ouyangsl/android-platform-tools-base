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
package com.android.tools.apk.analyzer.internal;

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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.annotations.NonNull;
import com.android.tools.apk.analyzer.ArchiveContext;
import com.android.tools.apk.analyzer.ArchiveEntry;
import com.android.tools.apk.analyzer.ArchiveNode;
import com.android.tools.apk.analyzer.ArchiveTreeStructure;
import com.android.tools.apk.analyzer.PathUtils;

import com.google.archivepatcher.explainer.EntryExplanation;
import com.google.archivepatcher.explainer.PatchExplainer;
import com.google.archivepatcher.generator.bsdiff.BsDiffDeltaGenerator;
import com.google.archivepatcher.shared.DeflateCompressor;
import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.swing.tree.DefaultMutableTreeNode;

public class ApkFileByFileDiffParser {

    @NonNull
    public static DefaultMutableTreeNode createTreeNode(
            @NonNull ArchiveContext oldFile, @NonNull ArchiveContext newFile)
            throws IOException, InterruptedException {
        return createTreeNode(oldFile, newFile, (count, size) -> {});
    }

    @NonNull
    public static DefaultMutableTreeNode createTreeNode(
            @NonNull ArchiveContext oldFile,
            @NonNull ArchiveContext newFile,
            OnProgressListener onProgressListener)
            throws IOException, InterruptedException {
        ArchiveNode oldRoot = ArchiveTreeStructure.create(oldFile);
        ArchiveNode newRoot = ArchiveTreeStructure.create(newFile);
        BsDiffDeltaGenerator generator = new ListeningDiffGenerator(onProgressListener);
        PatchExplainer explainer = new PatchExplainer(new DeflateCompressor(), generator);
        Map<String, Long> pathsToDiffSize = new HashMap<>();
        List<EntryExplanation> explanationList =
                explainer.explainPatch(
                        oldFile.getArchive().getPath().toFile(),
                        newFile.getArchive().getPath().toFile());
        for (EntryExplanation explanation : explanationList) {
            String path = new String(explanation.getPath().getData(), UTF_8);
            pathsToDiffSize.put(path, explanation.getCompressedSizeInPatch());
        }
        return createTreeNode(oldRoot, newRoot, pathsToDiffSize);
    }

    @VisibleForTesting
    @NonNull
    static DefaultMutableTreeNode createTreeNode(
            ArchiveNode oldFile, ArchiveNode newFile, Map<String, Long> pathsToDiffSize)
            throws IOException {
        if (oldFile == null && newFile == null) {
            throw new IllegalArgumentException("Both old and new files are null");
        }

        DefaultMutableTreeNode node = new DefaultMutableTreeNode();

        long oldSize = 0;
        long newSize = 0;
        long patchSize = 0;

        HashSet<String> childrenInOldFile = new HashSet<>();
        final ArchiveEntry data = oldFile == null ? newFile.getData() : oldFile.getData();
        final String name =
                data.getPath().getFileName() != null
                        ? PathUtils.fileNameWithTrailingSeparator(data.getPath())
                        : PathUtils.fileNameWithTrailingSeparator(data.getArchive().getPath());
        if (oldFile != null) {
            if (!oldFile.getChildren().isEmpty()) {
                for (ArchiveNode oldChild : oldFile.getChildren()) {
                    ArchiveNode newChild = null;
                    if (newFile != null) {
                        for (ArchiveNode archiveNode : newFile.getChildren()) {
                            if (archiveNode
                                    .getData()
                                    .getPath()
                                    .getFileName()
                                    .toString()
                                    .equals(
                                            oldChild.getData()
                                                    .getPath()
                                                    .getFileName()
                                                    .toString())) {
                                newChild = archiveNode;
                                break;
                            }
                        }
                    }

                    childrenInOldFile.add(oldChild.getData().getPath().getFileName().toString());
                    DefaultMutableTreeNode childNode =
                            createTreeNode(oldChild, newChild, pathsToDiffSize);
                    node.add(childNode);

                    ApkDiffEntry entry = (ApkDiffEntry) childNode.getUserObject();
                    oldSize += entry.getOldSize();
                    newSize += entry.getNewSize();
                    patchSize += entry.getSize();
                }

                if (Files.size(oldFile.getData().getPath()) > 0) {
                    // This is probably a zip inside the apk, and we should use it's size
                    oldSize = Files.size(oldFile.getData().getPath());
                } else if (oldFile.getParent() == null) {
                    oldSize = Files.size(oldFile.getData().getArchive().getPath());
                }
            } else {
                oldSize += Files.size(oldFile.getData().getPath());
            }
        }
        if (newFile != null) {
            if (!newFile.getChildren().isEmpty()) {
                for (ArchiveNode newChild : newFile.getChildren()) {
                    if (childrenInOldFile.contains(
                            newChild.getData().getPath().getFileName().toString())) {
                        continue;
                    }

                    DefaultMutableTreeNode childNode =
                            createTreeNode(null, newChild, pathsToDiffSize);
                    node.add(childNode);

                    ApkDiffEntry entry = (ApkDiffEntry) childNode.getUserObject();
                    oldSize += entry.getOldSize();
                    newSize += entry.getNewSize();
                    patchSize += entry.getSize();
                }

                if (Files.size(newFile.getData().getPath()) > 0) {
                    // This is probably a zip inside the apk, and we should use it's size
                    newSize = Files.size(newFile.getData().getPath());
                    String relPath =
                            newFile.getData()
                                    .getArchive()
                                    .getContentRoot()
                                    .relativize(newFile.getData().getPath())
                                    .toString();
                    if (pathsToDiffSize.containsKey(relPath)) {
                        patchSize = pathsToDiffSize.get(relPath);
                    }
                } else if (newFile.getParent() == null) {
                    newSize = Files.size(newFile.getData().getArchive().getPath());
                }
            } else {
                newSize += Files.size(newFile.getData().getPath());
                String relPath =
                        newFile.getData()
                                .getArchive()
                                .getContentRoot()
                                .relativize(newFile.getData().getPath())
                                .toString();
                if (pathsToDiffSize.containsKey(relPath)) {
                    patchSize = pathsToDiffSize.get(relPath);
                }
            }
        }

        node.setUserObject(
                new ApkFileByFileEntry(name, oldFile, newFile, oldSize, newSize, patchSize));
        ApkEntry.sort(node);
        return node;
    }

    private static class ListeningDiffGenerator extends BsDiffDeltaGenerator {

        private final OnProgressListener myListener;

        private int myCount = 0;

        private long mySize = 0;

        private ListeningDiffGenerator(OnProgressListener listener) {
            myListener = listener;
        }

        @Override
        public void generateDelta(File oldBlob, File newBlob, OutputStream deltaOut)
                throws IOException, InterruptedException {
            super.generateDelta(oldBlob, newBlob, deltaOut);
            myCount++;
            mySize += oldBlob.length();
            myListener.onProgress(myCount, mySize);
        }
    }

    public interface OnProgressListener {

        void onProgress(int count, long size);
    }
}
