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

import com.android.annotations.NonNull;

import java.util.stream.Stream;

public class ArchiveTreeStream {
    @NonNull
    public static Stream<ArchiveNode> preOrderStreamNoInnerArchiveExpansion(
            @NonNull ArchiveNode node) {
        return Stream.concat(
                Stream.of(node),
                node.getChildren().stream()
                        .flatMap(
                                child -> {
                                    if (child.getData() instanceof InnerArchiveEntry) {
                                        return Stream.of(child);
                                    } else {
                                        return preOrderStreamNoInnerArchiveExpansion(child);
                                    }
                                }));
    }

    @NonNull
    public static Stream<ArchiveNode> preOrderStream(@NonNull ArchiveNode node) {
        return Stream.concat(
                Stream.of(node),
                node.getChildren().stream().flatMap(ArchiveTreeStream::preOrderStream));
    }

    @NonNull
    public static <T> Stream<ArchiveNode> postOrderStream(@NonNull ArchiveNode node) {
        return Stream.concat(
                node.getChildren().stream().flatMap(ArchiveTreeStream::postOrderStream),
                Stream.of(node));
    }
}
