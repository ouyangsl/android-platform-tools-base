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

package com.android.build.gradle.tasks;

import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.build.api.variant.BuiltArtifacts;
import com.android.build.api.variant.FilterConfiguration;
import com.android.build.api.variant.impl.BuiltArtifactImpl;
import com.android.build.api.variant.impl.BuiltArtifactsImpl;
import com.android.build.api.variant.impl.FilterConfigurationImpl;
import com.android.build.api.variant.impl.VariantOutputConfigurationImpl;
import com.android.build.gradle.internal.scope.InternalArtifactType;
import com.android.builder.files.IncrementalRelativeFileSets;
import com.android.builder.files.RelativeFile;
import com.android.ide.common.resources.FileStatus;
import com.android.tools.build.apkzlib.zip.compress.Zip64NotSupportedException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Tests for {@link PackageAndroidArtifact} */
public class PackageAndroidArtifactTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testNormalZip() throws IOException {
        File zip = createZip64File(64000, 0);

        // exception should not be raised.
        ImmutableMap<RelativeFile, FileStatus> relativeFileFileStatus =
                IncrementalRelativeFileSets.fromZip(zip);
        assertThat(relativeFileFileStatus).hasSize(64000);
        relativeFileFileStatus.forEach(
                (relativeFile, fileStatus) -> assertThat(fileStatus).isEqualTo(FileStatus.NEW));
    }

    @Test
    public void testZip64File() throws IOException {
        expectedException.expect(Zip64NotSupportedException.class);
        File zip64 = createZip64File(66000, 0);

        // exception should be raised.
        ImmutableMap<RelativeFile, FileStatus> relativeFileFileStatus =
                IncrementalRelativeFileSets.fromZip(zip64);
        assertThat(relativeFileFileStatus).hasSize(66000);
        relativeFileFileStatus.forEach(
                (relativeFile, fileStatus) -> assertThat(fileStatus).isEqualTo(FileStatus.NEW));
    }

    @Test
    public void testZip64Copy() throws IOException {
        File zip64 = createZip64File(64000, 2000);
        File tmpFolder = temporaryFolder.newFolder();
        PackageAndroidArtifact.copyJavaResourcesOnly(tmpFolder, zip64);
        assertThat(tmpFolder.isDirectory()).isTrue();
        File[] files = tmpFolder.listFiles();
        assertThat(files).isNotNull();
        assertThat(files.length).isEqualTo(1);
        File outFolder = files[0];
        assertThat(outFolder.isDirectory()).isTrue();
        files = outFolder.listFiles();
        assertThat(files).isNotNull();
        assertThat(files.length).isEqualTo(1);
        File copiedZip = files[0];
        assertThat(copiedZip.exists()).isTrue();

        // check contents.
        try (ZipFile inZip = new ZipFile(copiedZip)) {
            assertThat(inZip.size()).isEqualTo(2000);
        }

        ImmutableMap<RelativeFile, FileStatus> relativeFileFileStatus =
                IncrementalRelativeFileSets.fromZip(copiedZip);
        assertThat(relativeFileFileStatus).hasSize(2000);
        relativeFileFileStatus.forEach(
                (relativeFile, fileStatus) -> {
                    assertThat(relativeFile.getRelativePath())
                            .doesNotContain(SdkConstants.DOT_CLASS);
                    assertThat(fileStatus).isEqualTo(FileStatus.NEW);
                });
    }

    @Test
    public void testFileNameUnique() {
        ImmutableList<BuiltArtifactImpl> outputFiles =
                ImmutableList.of(
                        BuiltArtifactImpl.make(
                                "/tmp/file_main.out",
                                -1,
                                "version_name",
                                new VariantOutputConfigurationImpl(false, ImmutableList.of()),
                                Collections.emptyMap()),
                        BuiltArtifactImpl.make(
                                "/tmp/file_xxhdpi.out",
                                -1,
                                "version_name",
                                new VariantOutputConfigurationImpl(
                                        false,
                                        ImmutableList.of(
                                                new FilterConfigurationImpl(
                                                        FilterConfiguration.FilterType.DENSITY,
                                                        "xxhdpi"))),
                                Collections.emptyMap()),
                        BuiltArtifactImpl.make(
                                "/tmp/filefr.out",
                                -1,
                                "version_name",
                                new VariantOutputConfigurationImpl(
                                        false,
                                        ImmutableList.of(
                                                new FilterConfigurationImpl(
                                                        FilterConfiguration.FilterType.LANGUAGE,
                                                        "fr"))),
                                Collections.emptyMap()),
                        BuiltArtifactImpl.make(
                                "/tmp/fileen.out",
                                -1,
                                "version_name",
                                new VariantOutputConfigurationImpl(
                                        false,
                                        ImmutableList.of(
                                                new FilterConfigurationImpl(
                                                        FilterConfiguration.FilterType.LANGUAGE,
                                                        "en"))),
                                Collections.emptyMap()));
        PackageAndroidArtifact.checkFileNameUniqueness(
                new BuiltArtifactsImpl(
                        BuiltArtifacts.METADATA_FILE_VERSION,
                        InternalArtifactType.LINKED_RESOURCES_BINARY_FORMAT.INSTANCE,
                        "com.app.example",
                        "debug",
                        outputFiles));
    }

    @Test
    public void testFileNameNotUnique() {
        ImmutableList<BuiltArtifactImpl> outputFiles =
                ImmutableList.of(
                        BuiltArtifactImpl.make(
                                "/tmp/file.out",
                                -1,
                                "version_name",
                                new VariantOutputConfigurationImpl(
                                        false,
                                        ImmutableList.of(
                                                new FilterConfigurationImpl(
                                                        FilterConfiguration.FilterType.LANGUAGE,
                                                        "fr"))),
                                Collections.emptyMap()),
                        BuiltArtifactImpl.make(
                                "/tmp/file.out",
                                -1,
                                "version_name",
                                new VariantOutputConfigurationImpl(
                                        false,
                                        ImmutableList.of(
                                                new FilterConfigurationImpl(
                                                        FilterConfiguration.FilterType.LANGUAGE,
                                                        "en"))),
                                Collections.emptyMap()));

        try {
            PackageAndroidArtifact.checkFileNameUniqueness(
                    new BuiltArtifactsImpl(
                            BuiltArtifacts.METADATA_FILE_VERSION,
                            InternalArtifactType.LINKED_RESOURCES_BINARY_FORMAT.INSTANCE,
                            "com.app.example",
                            "debug",
                            outputFiles));
        } catch (Exception e) {
            assertThat(e.getMessage())
                    .contains(
                            "\"file.out\", filters : "
                                    + "FilterConfiguration(filterType=LANGUAGE, identifier=fr):"
                                    + "FilterConfiguration(filterType=LANGUAGE, identifier=en)");
        }
    }

    private File createZip64File(int numClasses, int numResources) throws IOException {
        File zip64 = temporaryFolder.newFile();
        try (ZipOutputStream zipOut =
                new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zip64)))) {
            for (int i = 0; i < numClasses; i++) {
                zipOut.putNextEntry(new ZipEntry("entry-" + i + ".class"));
                zipOut.write(("entry-" + i).getBytes());
                zipOut.closeEntry();
            }
            for (int i = 0; i < numResources; i++) {
                zipOut.putNextEntry(new ZipEntry("entry-" + i));
                zipOut.write(("entry-" + i).getBytes());
                zipOut.closeEntry();
            }
        }
        return zip64;
    }
}
