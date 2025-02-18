/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.integration.common.fixture;

import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import org.junit.runners.model.InitializationError;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

/**
 * Allows project files to be modified, but stores their original content, so it can be restored for
 * the next test.
 */
public class TemporaryProjectModification implements Closeable {

    /**
     * The type of file change event.
     */
    enum FileChangeType {
        CHANGED, ADDED, REMOVED
    }

    public interface FileProvider {
        /** Creates a File object from a relative path. */
        File file(String path);
    }

    /**
     * A file change event and the associated original file content.
     */
    private static class FileEvent {
        private final FileChangeType type;
        private final String fileContent;

        private FileEvent(
                FileChangeType type, String fileContent) {
            this.type = type;
            this.fileContent = fileContent;
        }

        public FileChangeType getType() {
            return type;
        }

        public String getFileContent() {
            return fileContent;
        }

        /**
         * Creates a {@link FileChangeType#CHANGED} FileEvent with a given original file content.
         * @param content the original file content
         * @return a FileEvent instance
         */
        static FileEvent changed(@NonNull String content) {
            return new FileEvent(FileChangeType.CHANGED, content);
        }

        /**
         * Creates a {@link FileChangeType#REMOVED} FileEvent with a given original file content.
         * @param content the original file content
         * @return a FileEvent instance
         */
        static FileEvent removed(@NonNull String content) {
            return new FileEvent(FileChangeType.REMOVED, content);
        }

        /**
         * Creates a {@link FileChangeType#ADDED} FileEvent.
         * @return a FileEvent instance
         */
        static FileEvent added() {
            return new FileEvent(FileChangeType.ADDED, null);
        }
    }

    /**
     * An optional file provider that converts from string path to {@link File}. This is required to
     * run file events.
     */
    @Nullable private final FileProvider fileProvider;

    /** Map of file change event. Key is relative path, value is the change event data */
    private final Map<String, FileEvent> mFileEvents;

    public TemporaryProjectModification(@Nullable FileProvider fileProvider) {
        this.fileProvider = fileProvider;
        mFileEvents = Maps.newHashMap();
    }

    private TemporaryProjectModification(
            @Nullable FileProvider fileProvider, Map<String, FileEvent> parentMap) {
        this.fileProvider = fileProvider;
        mFileEvents = parentMap;
    }

    /** Creates a delegate with a new file provider that uses the same map as the parent. */
    public TemporaryProjectModification delegate(@Nullable FileProvider fileProvider) {
        return new TemporaryProjectModification(fileProvider, mFileEvents);
    }

    /**
     * Runs a test that mutates the project in a reversible way, and returns the project to its
     * original state after the callback has been run.
     *
     * @param fileProvider the object that will convert the relative path to absolute
     * @param test The test to run.
     * @throws InitializationError if the project modification infrastructure fails.
     * @throws Exception passed through if the test throws an exception.
     */
    public static void doTest(@NonNull FileProvider fileProvider, @NonNull ModifiedProjectTest test)
            throws Exception {
        TemporaryProjectModification modifiedProject =
                new TemporaryProjectModification(fileProvider);
        try {
            test.runTest(modifiedProject);
        } finally {
            modifiedProject.close();
        }
    }

    @FunctionalInterface
    public interface ModifiedProjectTest {
        void runTest(TemporaryProjectModification modifiedProject) throws Exception;
    }

    @SuppressWarnings("SameParameterValue") // Helper function for future tests.
    public void replaceFile(@NonNull String relativePath, @NonNull final String content)
            throws IOException, InterruptedException {
        modifyFile(relativePath, input -> content);
    }

    public void replaceInFile(
            @NonNull String relativePath,
            @NonNull final String search,
            @NonNull final String replace)
            throws IOException, InterruptedException {
        modifyFile(relativePath, input -> input.replaceAll(search, replace));
    }

    public void removeFile(@NonNull String relativePath) throws IOException, InterruptedException {
        File file = getFile(relativePath);

        String currentContent = Files.toString(file, Charsets.UTF_8);

        // We can modify multiple times, but we only want to store the original.
        if (!mFileEvents.containsKey(relativePath)) {
            mFileEvents.put(relativePath, FileEvent.removed(currentContent));
        }

        FileUtils.delete(file);
        TestUtils.waitForFileSystemTick();
    }

    public void addFile(@NonNull String relativePath, @NonNull String content)
            throws IOException, InterruptedException {
        File file = getFile(relativePath);

        if (file.exists()) {
            throw new RuntimeException("File already exists: " + file);
        }

        FileUtils.mkdirs(file.getParentFile());

        mFileEvents.put(relativePath, FileEvent.added());

        Files.asCharSink(file, Charsets.UTF_8).write(content);
        TestUtils.waitForFileSystemTick();
    }

    public void addDir(@NonNull String relativePath) throws IOException, InterruptedException {
        File file = getFile(relativePath);

        if (file.exists()) {
            throw new RuntimeException("File already exists: " + file);
        }

        FileUtils.mkdirs(file);
        mFileEvents.put(relativePath, FileEvent.added());

        TestUtils.waitForFileSystemTick();
    }

    public void appendToFile(
            @NonNull String relativePath,
            @NonNull String toAppend) throws IOException, InterruptedException {
        modifyFile(relativePath, input -> input + "\n" + toAppend + "\n");
    }

    public void modifyFile(
            @NonNull String relativePath, @NonNull Function<String, String> modification)
            throws IOException, InterruptedException {
        File file = getFile(relativePath);

        String currentContent = Files.toString(file, Charsets.UTF_8);

        // We can modify multiple times, but we only want to store the original.
        if (!mFileEvents.containsKey(relativePath)) {
            mFileEvents.put(relativePath, FileEvent.changed(currentContent));
        }

        String newContent = modification.apply(currentContent);

        if (newContent == null) {
            assertTrue("File should have been deleted", file.delete());
        } else {
            Files.asCharSink(file, Charsets.UTF_8).write(newContent);
        }
        TestUtils.waitForFileSystemTick();
    }

    /** Returns the project back to its original state. */
    @Override
    public void close() throws IOException {
        for (Map.Entry<String, FileEvent> entry : mFileEvents.entrySet()) {
            FileEvent fileEvent = entry.getValue();
            switch (fileEvent.getType()) {
                case REMOVED:
                case CHANGED:
                    Files.asCharSink(getFile(entry.getKey()), Charsets.UTF_8)
                            .write(fileEvent.getFileContent());
                    break;
                case ADDED:
                    // it's fine if the file was already removed somehow.
                    FileUtils.deleteIfExists(fileProvider.file(entry.getKey()));
            }
        }

        mFileEvents.clear();
        try {
            TestUtils.waitForFileSystemTick();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private File getFile(@NonNull String relativePath) {
        Preconditions.checkNotNull(fileProvider, "Cannot do file changes without a FileProvider");
        return fileProvider.file(relativePath.replace('/', File.separatorChar));
    }
}
