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

package com.android.build.gradle.internal.ndk;

import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.internal.core.Abi;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Test for DefaultNdkInfo. */
public class DefaultNdkInfoTest {
    private static final Object[] ALL_FALLBACK_ABIS =
            Arrays.stream(Abi.values())
                    .map(it -> it.getTag())
                    .filter(it -> !it.equals("riscv64"))
                    .toArray();
    private static final Object[] ALL_32_BITS_ABIS =
            new Object[] {
                Abi.ARMEABI.getTag(), Abi.ARMEABI_V7A.getTag(), Abi.X86.getTag(), Abi.MIPS.getTag()
            };
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File ndkFolder;
    private File abiListFile;

    @Before
    public void setUp() throws IOException {
        ndkFolder = temporaryFolder.newFolder();
        abiListFile = new File(ndkFolder, "meta/abis.json");
        Files.createParentDirs(abiListFile);
    }

    @Test
    public void testMissingAbiFile() throws IOException {
        NdkInfo info = new DefaultNdkInfo(ndkFolder);
        assertThat(info.getSupportedAbis()).containsExactly(ALL_FALLBACK_ABIS);
        assertThat(info.getSupported32BitsAbis()).containsExactly(ALL_32_BITS_ABIS);
        assertThat(info.getDefaultAbis()).containsExactly(ALL_FALLBACK_ABIS);
        assertThat(info.getDefault32BitsAbis()).containsExactly(ALL_32_BITS_ABIS);
    }

    @Test
    public void testParseFailureResultInDefaultAbiList() throws IOException {
        Files.asCharSink(abiListFile, Charsets.UTF_8).write("invalid json file\n");
        NdkInfo info = new DefaultNdkInfo(ndkFolder);
        assertThat(info.getSupportedAbis()).containsExactly(ALL_FALLBACK_ABIS);
        assertThat(info.getSupported32BitsAbis()).containsExactly(ALL_32_BITS_ABIS);
        assertThat(info.getDefaultAbis()).containsExactly(ALL_FALLBACK_ABIS);
        assertThat(info.getDefault32BitsAbis()).containsExactly(ALL_32_BITS_ABIS);
    }

    @Test
    public void testWithAbiListFile() throws IOException {
        Files.asCharSink(abiListFile, Charsets.UTF_8)
                .write(
                        "{\n"
                                + "    \"armeabi\": {\n"
                                + "        \"deprecated\" : false\n"
                                + "    }\n"
                                + "}");
        NdkInfo info = new DefaultNdkInfo(ndkFolder);
        assertThat(info.getSupportedAbis()).containsExactly(Abi.ARMEABI.getTag());
        assertThat(info.getSupported32BitsAbis()).containsExactly(Abi.ARMEABI.getTag());
        assertThat(info.getDefaultAbis()).containsExactly(Abi.ARMEABI.getTag());
        assertThat(info.getDefault32BitsAbis()).containsExactly(Abi.ARMEABI.getTag());
    }

    @Test
    public void testWithDefaultSettingsInAbiListFile() throws IOException {
        Files.asCharSink(abiListFile, Charsets.UTF_8)
                .write(
                        "{\n"
                                + "    \"armeabi\": {\n"
                                + "        \"default\" : true,\n"
                                + "        \"deprecated\" : false\n"
                                + "    },\n"
                                + "    \"armeabi-v7a\": {\n"
                                + "    },\n"
                                + "    \"mips\": {\n"
                                + "        \"default\" : false,\n"
                                + "        \"deprecated\" : false\n"
                                + "    },\n"
                                + "    \"x86\": {\n"
                                + "        \"default\" : true,\n"
                                + "        \"deprecated\" : true\n"
                                + "    }\n"
                                + "}");
        NdkInfo info = new DefaultNdkInfo(ndkFolder);
        assertThat(info.getSupportedAbis())
                .containsExactly(
                        Abi.ARMEABI.getTag(),
                        Abi.ARMEABI_V7A.getTag(),
                        Abi.MIPS.getTag(),
                        Abi.X86.getTag());
        assertThat(info.getSupported32BitsAbis())
                .containsExactly(
                        Abi.ARMEABI.getTag(),
                        Abi.ARMEABI_V7A.getTag(),
                        Abi.MIPS.getTag(),
                        Abi.X86.getTag());
        assertThat(info.getDefaultAbis())
                .containsExactly(Abi.ARMEABI.getTag(), Abi.ARMEABI_V7A.getTag());
        assertThat(info.getDefault32BitsAbis())
                .containsExactly(Abi.ARMEABI.getTag(), Abi.ARMEABI_V7A.getTag());
    }

    @Test
    public void testErrorHandling() throws IOException {
        Files.asCharSink(abiListFile, Charsets.UTF_8)
                .write(
                        "{\n"
                                + "    \"armeabi\": {\n"
                                + "        \"unknown\" : 42\n" // unknown fields are ignored.
                                // missing 'deprecated' field default to false.
                                + "    },\n"
                                + "    \"invalid\": { }\n" // invalid ABI is ignored.
                                + "\n"
                                + "}");
        NdkInfo info = new DefaultNdkInfo(ndkFolder);
        assertThat(info.getSupportedAbis()).containsExactly(Abi.ARMEABI.getTag());
        assertThat(info.getSupported32BitsAbis()).containsExactly(Abi.ARMEABI.getTag());
        assertThat(info.getDefaultAbis()).containsExactly(Abi.ARMEABI.getTag());
        assertThat(info.getDefault32BitsAbis()).containsExactly(Abi.ARMEABI.getTag());
    }

    @Test
    public void testMultipleAbiWithAbiListFile() throws IOException {
        Files.asCharSink(abiListFile, Charsets.UTF_8)
                .write(
                        "{\n"
                                + "    \"armeabi-v7a\": {\n"
                                + "        \"deprecated\" : false\n"
                                + "    },\n"
                                + "    \"x86_64\": {\n"
                                + "        \"deprecated\" : false\n"
                                + "    },\n"
                                + "    \"mips\": {\n"
                                + "        \"deprecated\" : true\n"
                                + "    },\n"
                                + "    \"mips64\": {\n"
                                + "        \"deprecated\" : true\n"
                                + "    }\n"
                                + "}");
        NdkInfo info = new DefaultNdkInfo(ndkFolder);
        assertThat(info.getSupportedAbis())
                .containsExactly(
                        Abi.ARMEABI_V7A.getTag(),
                        Abi.X86_64.getTag(),
                        Abi.MIPS.getTag(),
                        Abi.MIPS64.getTag());
        assertThat(info.getSupported32BitsAbis())
                .containsExactly(Abi.ARMEABI_V7A.getTag(), Abi.MIPS.getTag());
        assertThat(info.getDefaultAbis())
                .containsExactly(Abi.ARMEABI_V7A.getTag(), Abi.X86_64.getTag());
        assertThat(info.getDefault32BitsAbis()).containsExactly(Abi.ARMEABI_V7A.getTag());
    }
}
