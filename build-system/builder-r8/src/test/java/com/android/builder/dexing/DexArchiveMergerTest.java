/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.builder.dexing;

import static com.android.builder.dexing.DexArchiveTestUtil.PACKAGE;
import static com.android.builder.dexing.DexArchiveTestUtil.createClasses;
import static com.android.testutils.truth.DexSubject.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;

import static org.junit.Assert.fail;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.testutils.TestClassesGenerator;
import com.android.testutils.TestInputsGenerator;
import com.android.testutils.TestUtils;
import com.android.testutils.apk.Dex;
import com.android.utils.FileUtils;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.truth.Truth;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * Testing the dex archive merger. It takes one or more dex archives as input, and outputs one or
 * more DEX files.
 */
public class DexArchiveMergerTest {

    @ClassRule public static TemporaryFolder allTestsTemporaryFolder = new TemporaryFolder();

    private static final String BIG_CLASS = "BigClass";
    private static Path bigDexArchive;

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass
    public static void setUp() throws Exception {
        Path inputRoot = allTestsTemporaryFolder.getRoot().toPath().resolve("big_class");
        DexArchiveTestUtil.createClassWithMethodDescriptors(inputRoot, BIG_CLASS, 65524);

        bigDexArchive = allTestsTemporaryFolder.getRoot().toPath().resolve("big_dex_archive");
        Path globalSynthetics =
                allTestsTemporaryFolder.getRoot().toPath().resolve("global_synthetics");
        DexArchiveTestUtil.convertClassesToDexArchive(inputRoot, bigDexArchive, globalSynthetics);
    }

    @Test
    public void test_monoDex_twoDexMerging() throws Exception {
        Path fstArchive =
                DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                        temporaryFolder.getRoot().toPath().resolve("fst"), "A");
        Path sndArchive =
                DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                        temporaryFolder.getRoot().toPath().resolve("snd"), "B");

        Path output = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.mergeMonoDex(ImmutableList.of(fstArchive, sndArchive), output);

        Dex outputDex = new Dex(output.resolve("classes.dex"));

        assertThat(outputDex).containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses("A", "B"));
    }

    @Test
    public void test_monoDex_manyDexMerged() throws Exception {
        List<Path> archives = Lists.newArrayList();
        List<String> expectedClasses = Lists.newArrayList();
        for (int i = 0; i < 100; i++) {
            archives.add(
                    DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                            temporaryFolder.getRoot().toPath().resolve("A" + i), "A" + i));
            expectedClasses.add("A" + i);
        }

        Path output = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.mergeMonoDex(archives, output);

        Dex outputDex = new Dex(output.resolve("classes.dex"));
        assertThat(outputDex)
                .containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses(expectedClasses));
    }

    @Test
    public void test_monoDex_exactLimit() throws Exception {
        Path inputRoot = temporaryFolder.getRoot().toPath().resolve("classes");
        DexArchiveTestUtil.createClassWithMethodDescriptors(inputRoot, "A", 9);

        Path dexArchive = temporaryFolder.getRoot().toPath().resolve("output");
        Path globalSynthetics = temporaryFolder.getRoot().toPath().resolve("global_synthetics");
        DexArchiveTestUtil.convertClassesToDexArchive(inputRoot, dexArchive, globalSynthetics);

        Path outputDex = temporaryFolder.getRoot().toPath().resolve("output_dex");
        DexArchiveTestUtil.mergeMonoDex(ImmutableList.of(dexArchive, bigDexArchive), outputDex);

        Dex finalDex = new Dex(outputDex.resolve("classes.dex"));
        assertThat(finalDex)
                .containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses(BIG_CLASS, "A"));
    }

    @Test
    public void test_monoDex_aboveLimit() throws Exception {
        Path inputRoot = temporaryFolder.getRoot().toPath().resolve("classes");
        DexArchiveTestUtil.createClassWithMethodDescriptors(inputRoot, "B", 10);

        Path dexArchive = temporaryFolder.getRoot().toPath().resolve("output");
        Path globalSynthetics = temporaryFolder.getRoot().toPath().resolve("global_synthetics");
        DexArchiveTestUtil.convertClassesToDexArchive(inputRoot, dexArchive, globalSynthetics);

        Path outputDex = temporaryFolder.getRoot().toPath().resolve("output_dex");
        try {
            DexArchiveTestUtil.mergeMonoDex(ImmutableList.of(dexArchive, bigDexArchive), outputDex);
            fail("Too many methods for mono-dex. Merging should fail.");
        } catch (Exception e) {
            Truth.assertThat(Throwables.getStackTraceAsString(e))
                    .contains("The number of method references in a .dex file cannot exceed 64K");
        }
    }

    @Test
    public void test_legacyMultiDex_mergeTwo() throws Exception {
        Path fstArchive =
                DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                        temporaryFolder.getRoot().toPath().resolve("fst"), "A");
        Path sndArchive =
                DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                        temporaryFolder.getRoot().toPath().resolve("snd"), "B");

        Path output = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.mergeLegacyDex(
                ImmutableList.of(fstArchive, sndArchive),
                output,
                generateMainDexListRules(ImmutableSet.of(PACKAGE + ".A")),
                getLibraryFiles());

        Dex outputDex = new Dex(output.resolve("classes.dex"));
        assertThat(outputDex).containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses("A"));

        Dex secondaryDex = new Dex(output.resolve("classes2.dex"));
        assertThat(secondaryDex).containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses("B"));
    }

    @Test
    public void test_legacyMultiDex_allInMainDex() throws Exception {
        Path fstArchive =
                DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                        temporaryFolder.getRoot().toPath().resolve("fst"), "A");
        Path sndArchive =
                DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                        temporaryFolder.getRoot().toPath().resolve("snd"), "B");

        Path output = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.mergeLegacyDex(
                ImmutableList.of(fstArchive, sndArchive),
                output,
                generateMainDexListRules(ImmutableSet.of(PACKAGE + ".A", PACKAGE + ".B")),
                getLibraryFiles());

        Dex outputDex = new Dex(output.resolve("classes.dex"));
        assertThat(outputDex).containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses("A", "B"));

        assertThat(output.resolve("classes2.dex")).doesNotExist();
    }

    @Test
    public void test_legacyMultiDex_multipleSecondary() throws Exception {
        Path inputRoot = temporaryFolder.getRoot().toPath().resolve("classes");
        DexArchiveTestUtil.createClassWithMethodDescriptors(inputRoot, "A", 1);
        DexArchiveTestUtil.createClassWithMethodDescriptors(inputRoot, "B", 10);

        Path dexArchive = temporaryFolder.getRoot().toPath().resolve("output");
        Path globalSynthetics = temporaryFolder.getRoot().toPath().resolve("global_synthetics");
        DexArchiveTestUtil.convertClassesToDexArchive(inputRoot, dexArchive, globalSynthetics);

        Path outputDex = temporaryFolder.getRoot().toPath().resolve("output_dex");
        DexArchiveTestUtil.mergeLegacyDex(
                ImmutableList.of(dexArchive, bigDexArchive),
                outputDex,
                generateMainDexListRules(ImmutableSet.of(PACKAGE + ".A")),
                getLibraryFiles());

        Dex primaryDex = new Dex(outputDex.resolve("classes.dex"));
        assertThat(primaryDex).containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses("A"));

        assertThat(outputDex.resolve("classes2.dex")).exists();
        assertThat(outputDex.resolve("classes3.dex")).exists();
        assertThat(outputDex.resolve("classes4.dex")).doesNotExist();
    }

    @Test
    public void test_nativeMultiDex_mergeTwo() throws Exception {
        Path fstArchive =
                DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                        temporaryFolder.getRoot().toPath().resolve("fst"), "A");
        Path sndArchive =
                DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                        temporaryFolder.getRoot().toPath().resolve("snd"), "B");

        Path output = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.mergeNativeDex(ImmutableList.of(fstArchive, sndArchive), output);

        Dex outputDex = new Dex(output.resolve("classes.dex"));
        assertThat(outputDex).containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses("A", "B"));
    }

    @Test
    public void test_nativeMultiDex_multipleDexes() throws Exception {
        Path inputRoot = temporaryFolder.getRoot().toPath().resolve("classes");
        DexArchiveTestUtil.createClassWithMethodDescriptors(inputRoot, "A", 10);

        Path dexArchive = temporaryFolder.getRoot().toPath().resolve("output");
        Path globalSynthetics = temporaryFolder.getRoot().toPath().resolve("global_synthetics");
        DexArchiveTestUtil.convertClassesToDexArchive(inputRoot, dexArchive, globalSynthetics);

        Path outputDex = temporaryFolder.getRoot().toPath().resolve("output_dex");
        DexArchiveTestUtil.mergeNativeDex(ImmutableList.of(dexArchive, bigDexArchive), outputDex);

        assertThat(outputDex.resolve("classes.dex")).exists();
        assertThat(outputDex.resolve("classes2.dex")).exists();
        assertThat(outputDex.resolve("classes3.dex")).doesNotExist();
    }

    @Test
    public void test_nativeMultiDex_empty_sections() throws Exception {
        // regression test for - http://b.android.com/250705
        int NUM_CLASSES = 2;
        Path inputRoot = temporaryFolder.getRoot().toPath().resolve("classes");
        for (int i = 0; i < NUM_CLASSES; i++) {
            Path classFile = inputRoot.resolve(PACKAGE + "/" + "A" + i + SdkConstants.DOT_CLASS);
            Files.createDirectories(classFile.getParent());

            ClassWriter cw = new ClassWriter(0);
            cw.visit(
                    Opcodes.V1_6,
                    Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER,
                    PACKAGE + "/" + "A" + i,
                    null,
                    "java/lang/Object",
                    null);
            cw.visitEnd();

            Files.write(classFile, cw.toByteArray());
        }

        Path dexArchive = temporaryFolder.getRoot().toPath().resolve("output");
        Path globalSynthetics = temporaryFolder.getRoot().toPath().resolve("global_synthetics");
        DexArchiveTestUtil.convertClassesToDexArchive(inputRoot, dexArchive, globalSynthetics);

        Path outputDex = temporaryFolder.getRoot().toPath().resolve("output_dex");
        DexArchiveTestUtil.mergeNativeDex(ImmutableList.of(dexArchive), outputDex);

        Dex dex = new Dex(outputDex.resolve("classes.dex").toFile());

        Truth.assertThat(dex.getFieldCount()).named("number of fields").isEqualTo(0);
        Truth.assertThat(dex.getMethodCount()).named("number of methods").isEqualTo(0);
    }

    @Test
    public void test_multidex_orderOfInputsDoesNotChangeOutput() throws Exception {
        List<Path> archives = createArchives(temporaryFolder.getRoot().toPath().resolve("_"), 30);

        Path bigArchive = temporaryFolder.getRoot().toPath().resolve("big_archive");
        FileUtils.copyDirectory(bigDexArchive.toFile(), bigArchive.toFile());
        archives.add(bigArchive);

        Path output = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.mergeNativeDex(archives, output);
        byte[] classesDex = Files.readAllBytes(output.resolve("classes.dex"));
        byte[] classes2Dex = Files.readAllBytes(output.resolve("classes2.dex"));

        for (int i = 0; i < 5; i++) {
            Path newOutput = temporaryFolder.getRoot().toPath().resolve("output" + i);
            DexArchiveTestUtil.mergeNativeDex(archives, newOutput);

            Truth.assertThat(classesDex)
                    .isEqualTo(Files.readAllBytes(output.resolve("classes.dex")));
            Truth.assertThat(classes2Dex)
                    .isEqualTo(Files.readAllBytes(output.resolve("classes2.dex")));

            Collections.shuffle(archives);
        }
    }

    @Test
    public void test_monoDex_orderOfInputsDoesNotChangeOutput() throws Exception {
        List<Path> archives = createArchives(temporaryFolder.getRoot().toPath(), 10);

        Path output = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.mergeMonoDex(archives, output);
        byte[] classesDex = Files.readAllBytes(output.resolve("classes.dex"));

        for (int i = 0; i < 5; i++) {
            Path newOutput = temporaryFolder.getRoot().toPath().resolve("output" + i);
            DexArchiveTestUtil.mergeMonoDex(archives, newOutput);
            Truth.assertThat(classesDex)
                    .isEqualTo(Files.readAllBytes(output.resolve("classes.dex")));

            Collections.shuffle(archives);
        }
    }

    @Test
    public void testWindowsSmokeTest() throws Exception {
        FileSystem fs = Jimfs.newFileSystem(Configuration.windows());

        Set<String> classNames = ImmutableSet.of("A", "B", "C");
        Path classesInput = fs.getPath("tmp\\input_classes");
        DexArchiveTestUtil.createClasses(classesInput, classNames);
        Path dexArchive = fs.getPath("tmp\\dex_archive");
        Path globalSynthetics = fs.getPath("tmp\\global_synthetics");
        DexArchiveTestUtil.convertClassesToDexArchive(classesInput, dexArchive, globalSynthetics);

        Path output = fs.getPath("tmp\\out");
        DexArchiveTestUtil.mergeMonoDex(ImmutableList.of(dexArchive), output);

        Dex outputDex = new Dex(output.resolve("classes.dex"));

        assertThat(outputDex)
                .containsExactlyClassesIn(DexArchiveTestUtil.getDexClasses("A", "B", "C"));
    }

    @Test
    public void testStringsAbove64k() throws Exception {
        int numClasses = 16;
        Path inputRoot = temporaryFolder.getRoot().toPath().resolve("classes");
        for (int i = 0; i < numClasses; i++) {
            String className = PACKAGE + "/" + "A" + i;
            Path classFile = inputRoot.resolve(className + SdkConstants.DOT_CLASS);
            Files.createDirectories(classFile.getParent());

            Files.write(
                    classFile,
                    TestClassesGenerator.classWithStrings(className, (65536 / numClasses) + 1));
        }

        Path dexArchive = temporaryFolder.getRoot().toPath().resolve("output");
        Path globalSynthetics = temporaryFolder.getRoot().toPath().resolve("global_synthetics");
        DexArchiveTestUtil.convertClassesToDexArchive(inputRoot, dexArchive, globalSynthetics);

        Path outputDex = temporaryFolder.getRoot().toPath().resolve("output_dex");
        DexArchiveTestUtil.mergeNativeDex(ImmutableList.of(dexArchive), outputDex);
    }

    @Test
    public void testD8DuplicateClassError() throws Exception {
        // create 2 classes with the same name and package
        int numClasses = 2;
        List<Path> dexArchives = new ArrayList<>();
        List<Path> globalSynthetics = new ArrayList<>();
        for (int i = 0; i < numClasses; i++) {
            Path classes = temporaryFolder.newFolder("classes" + i).toPath();
            TestInputsGenerator.dirWithEmptyClasses(classes, Collections.singletonList("test/A"));
            dexArchives.add(temporaryFolder.getRoot().toPath().resolve("output" + i));
            globalSynthetics.add(
                    temporaryFolder.getRoot().toPath().resolve("global_synthetics" + i));
            DexArchiveTestUtil.convertClassesToDexArchive(
                    classes, dexArchives.get(i), globalSynthetics.get(i));
        }

        Path outputDex = temporaryFolder.getRoot().toPath().resolve("output_dex");

        try {
            DexArchiveTestUtil.mergeMonoDex(dexArchives, outputDex);
            fail("dex merging should fail when there are classes with same name and package");
        } catch (DexArchiveMergerException e) {
            Truth.assertThat(e.getMessage()).contains("is defined multiple times");
        } catch (Exception e) {
            Truth.assertThat(Throwables.getStackTraceAsString(e))
                    .contains("Multiple dex files define");
        }
    }

    @Test
    public void testMergingWithChecksum() throws Exception {
        // Compile A.class and record the CRC.
        Path firstDir = temporaryFolder.getRoot().toPath().resolve("fst");
        Path classesInput = firstDir.resolve("input");
        createClasses(classesInput, Sets.newHashSet("A"));
        Path classFile =
                Iterators.getOnlyElement(
                        Files.walk(classesInput).filter(Files::isRegularFile).iterator());
        CRC32 crc = new CRC32();
        crc.update(Files.readAllBytes(classFile));
        String crcHexMatcher = ".*" + Long.toHexString(crc.getValue()) + ".*";
        Path fstArchive = firstDir.resolve("dex_archive.jar");
        Path fstGlobalSynthetics = firstDir.resolve("globalSynthetics");
        DexArchiveTestUtil.convertClassesToDexArchive(
                classesInput, fstArchive, fstGlobalSynthetics);

        // Compile B.class.
        Path sndDir = temporaryFolder.getRoot().toPath().resolve("snd");
        classesInput = sndDir.resolve("input");
        createClasses(classesInput, Sets.newHashSet("B"));
        Path sndArchive = sndDir.resolve("dex_archive.jar");
        Path sndGlobalSynthetics = sndDir.resolve("globalSynthetics");
        DexArchiveTestUtil.convertClassesToDexArchive(
                classesInput, sndArchive, sndGlobalSynthetics);

        // The combined dex file should contains the checksum.
        Path output = temporaryFolder.getRoot().toPath().resolve("output");
        DexArchiveTestUtil.mergeMonoDex(ImmutableList.of(fstArchive, sndArchive), output);
        Dex dex = new Dex(output.resolve("classes.dex"));
        assertThat(dex).containsString(crcHexMatcher);
    }

    @NonNull
    private List<Path> createArchives(@NonNull Path archivePath, int numClasses) throws Exception {
        List<Path> archives = Lists.newArrayList();
        for (int i = 0; i < numClasses; i++) {
            archives.add(
                    DexArchiveTestUtil.createClassesAndConvertToDexArchive(
                            archivePath.resolve("A" + i), "A" + i));
        }
        return archives;
    }

    @NonNull
    private List<String> generateMainDexListRules(@NonNull Set<String> mainDexClasses) {
        List<String> rules = Lists.newArrayList();
        for (String eachClass : mainDexClasses) {
            rules.add("-keep class " + eachClass + " { *; }");
        }
        return rules;
    }

    @NonNull
    private Collection<Path> getLibraryFiles() {
        List<Path> files = Lists.newArrayList();
        files.add(TestUtils.resolvePlatformPath("android.jar", TestUtils.TestType.AGP));
        return files;
    }
}
