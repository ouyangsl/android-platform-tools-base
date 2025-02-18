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
package com.android.tools.apk.analyzer.dex;

import static com.android.tools.apk.analyzer.dex.DexFiles.getDexFile;
import static org.junit.Assert.assertEquals;

import com.android.annotations.NonNull;
import com.android.testutils.TestResources;
import com.android.tools.apk.analyzer.dex.tree.DexElementNode;
import com.android.tools.apk.analyzer.dex.tree.DexPackageNode;
import com.android.tools.proguard.ProguardMap;
import com.android.tools.proguard.ProguardSeedsMap;
import com.android.tools.proguard.ProguardUsagesMap;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;

public class PackageTreeCreatorTest {
    @Test
    public void simpleMethodReferenceTree() throws IOException {
        Map<Path, DexBackedDexFile> dexMap = getDexMap("Test.dex");
        DexPackageNode packageTreeNode =
                new PackageTreeCreator(null, false).constructPackageTree(dexMap);

        StringBuffer sb = new StringBuffer(100);
        dumpTree(sb, packageTreeNode, 0, null, null);
        assertEquals(
                "root: 3,6,198\n" +
                "  Test: 3,3,174\n" +
                "    <init>(): 1,1,38\n" +
                "    java.lang.Integer get(): 1,1,42\n" +
                "    java.util.List getList(): 1,1,38\n" +
                "  ~java: 0,3,24\n" +
                "    ~lang: 0,2,16\n" +
                "      ~Integer: 0,1,8\n" +
                "        ~References: 0,1,8\n" +
                "          ~java.lang.Integer valueOf(int): 0,1,8\n" +
                "      ~Object: 0,1,8\n" +
                "        ~References: 0,1,8\n" +
                "          ~<init>(): 0,1,8\n" +
                "    ~util: 0,1,8\n" +
                "      ~Collections: 0,1,8\n" +
                "        ~References: 0,1,8\n" +
                "          ~java.util.List emptyList(): 0,1,8\n",
                sb.toString());
    }

    public static Map<Path, DexBackedDexFile> getDexMap(String s) throws IOException {
        Path path = getDexPath(s);
        DexBackedDexFile dexFile = getTestDexFile(path);
        TreeMap<Path, DexBackedDexFile> map = new TreeMap<>();
        map.put(path, dexFile);
        return map;
    }

    public static Path getDexPath(String s) {
        return TestResources.getFile("/" + s).toPath();
    }

    @Test
    public void fieldsAndMethodReferenceTree() throws IOException {
        Map<Path, DexBackedDexFile> dexMap = getDexMap("Test2.dex");
        DexPackageNode packageTreeNode =
                new PackageTreeCreator(null, false).constructPackageTree(dexMap);

        StringBuffer sb = new StringBuffer(100);
        dumpTree(sb, packageTreeNode, 0, null, null);
        assertEquals(
                "root: 6,11,532\n" +
                "  ~java: 0,4,40\n" +
                "    ~lang: 0,3,32\n" +
                "      ~Integer: 0,1,8\n" +
                "        ~References: 0,1,8\n" +
                "          ~java.lang.Integer valueOf(int): 0,1,8\n" +
                "      ~Object: 0,1,8\n" +
                "        ~References: 0,1,8\n" +
                "          ~<init>(): 0,1,8\n" +
                "      ~Boolean: 0,1,8\n" +
                "        ~References: 0,1,8\n" +
                "          ~java.lang.Boolean valueOf(boolean): 0,1,8\n" +
                "      ~annotation: 0,0,8\n" +
                "        ~RetentionPolicy: 0,0,8\n" +
                "          ~References: 0,0,8\n" +
                "            ~java.lang.annotation.RetentionPolicy RUNTIME: 0,0,8\n" +
                "    ~util: 0,1,8\n" +
                "      ~Collections: 0,1,8\n" +
                "        ~References: 0,1,8\n" +
                "          ~java.util.List emptyList(): 0,1,8\n" +
                "  Test2: 3,3,192\n" +
                "    <init>(): 1,1,52\n" +
                "    java.lang.Integer get(): 1,1,42\n" +
                "    java.util.List getList(): 1,1,38\n" +
                "    a aClassField: 0,0,10\n" +
                "    int aField: 0,0,10\n" +
                "  TestSubclass: 2,3,124\n" +
                "    ~References: 0,1,8\n" +
                "      ~java.util.List getList(): 0,1,8\n" +
                "    <init>(): 1,1,38\n" +
                "    java.util.List getAnotherList(): 1,1,38\n" +
                "  a: 1,1,110\n" +
                "    <init>(): 1,1,46\n" +
                "  SomeAnnotation: 0,0,66\n",
                sb.toString());
    }

    @Test
    public void multiDexReferenceTree() throws IOException {
        Map<Path, DexBackedDexFile> dexMap = getDexMap("Test2.dex");
        Path path = getDexPath("Test.dex");
        DexBackedDexFile file = getDexFile(path);
        dexMap.put(path, file);
        DexPackageNode packageTreeNode =
                new PackageTreeCreator(null, false).constructPackageTree(dexMap);
        packageTreeNode.sort(Comparator.comparing(DexElementNode::getName));
        StringBuffer sb = new StringBuffer(100);
        dumpTree(sb, packageTreeNode, 0, null, null);
        assertEquals(
                "root: 9,14,730\n" +
                "  SomeAnnotation: 0,0,66\n" +
                "  Test: 3,3,174\n" +
                "    <init>(): 1,1,38\n" +
                "    java.lang.Integer get(): 1,1,42\n" +
                "    java.util.List getList(): 1,1,38\n" +
                "  Test2: 3,3,192\n" +
                "    <init>(): 1,1,52\n" +
                "    a aClassField: 0,0,10\n" +
                "    int aField: 0,0,10\n" +
                "    java.lang.Integer get(): 1,1,42\n" +
                "    java.util.List getList(): 1,1,38\n" +
                "  TestSubclass: 2,3,124\n" +
                "    ~References: 0,1,8\n" +
                "      ~java.util.List getList(): 0,1,8\n" +
                "    <init>(): 1,1,38\n" +
                "    java.util.List getAnotherList(): 1,1,38\n" +
                "  a: 1,1,110\n" +
                "    <init>(): 1,1,46\n" +
                "  ~java: 0,4,64\n" +
                "    ~lang: 0,3,48\n" +
                "      ~Boolean: 0,1,8\n" +
                "        ~References: 0,1,8\n" +
                "          ~java.lang.Boolean valueOf(boolean): 0,1,8\n" +
                "      ~Integer: 0,1,16\n" +
                "        ~References: 0,1,16\n" +
                "          ~java.lang.Integer valueOf(int): 0,1,16\n" +
                "      ~Object: 0,1,16\n" +
                "        ~References: 0,1,16\n" +
                "          ~<init>(): 0,1,16\n" +
                "      ~annotation: 0,0,8\n" +
                "        ~RetentionPolicy: 0,0,8\n" +
                "          ~References: 0,0,8\n" +
                "            ~java.lang.annotation.RetentionPolicy RUNTIME: 0,0,8\n" +
                "    ~util: 0,1,16\n" +
                "      ~Collections: 0,1,16\n" +
                "        ~References: 0,1,16\n" +
                "          ~java.util.List emptyList(): 0,1,16\n",
                sb.toString());
    }

    @Test
    public void proguardedReferenceTree() throws IOException, ParseException {
        Map<Path, DexBackedDexFile> dexMap = getDexMap("Test2.dex");
        Path mapPath = TestResources.getFile("/Test2_mapping.txt").toPath();
        ProguardMap map = new ProguardMap();
        map.readFromReader(Files.newBufferedReader(mapPath));

        mapPath = TestResources.getFile("/Test2_seeds.txt").toPath();
        ProguardSeedsMap seedsMap = ProguardSeedsMap.parse(Files.newBufferedReader(mapPath));

        mapPath = TestResources.getFile("/Test2_usage.txt").toPath();
        ProguardUsagesMap usageMap = ProguardUsagesMap.parse(Files.newBufferedReader(mapPath));

        ProguardMappings proguardMappings = new ProguardMappings(map, seedsMap, usageMap);
        DexPackageNode packageTreeNode =
                new PackageTreeCreator(proguardMappings, true).constructPackageTree(dexMap);

        StringBuffer sb = new StringBuffer(100);
        dumpTree(sb, packageTreeNode, 0, seedsMap, map);
        assertEquals(
                "O-root: 6,11,532\n" +
                "  ~java: 0,4,40\n" +
                "    ~lang: 0,3,32\n" +
                "      ~Integer: 0,1,8\n" +
                "        ~References: 0,1,8\n" +
                "          ~java.lang.Integer valueOf(int): 0,1,8\n" +
                "      ~Object: 0,1,8\n" +
                "        ~References: 0,1,8\n" +
                "          ~<init>(): 0,1,8\n" +
                "      ~Boolean: 0,1,8\n" +
                "        ~References: 0,1,8\n" +
                "          ~java.lang.Boolean valueOf(boolean): 0,1,8\n" +
                "      ~annotation: 0,0,8\n" +
                "        ~RetentionPolicy: 0,0,8\n" +
                "          ~References: 0,0,8\n" +
                "            ~java.lang.annotation.RetentionPolicy RUNTIME: 0,0,8\n" +
                "    ~util: 0,1,8\n" +
                "      ~Collections: 0,1,8\n" +
                "        ~References: 0,1,8\n" +
                "          ~java.util.List emptyList(): 0,1,8\n" +
                "  O-Test2: 3,3,192\n" +
                "    O-<init>(): 1,1,52\n" +
                "    O-java.lang.Integer get(): 1,1,42\n" +
                "    O-java.util.List getList(): 1,1,38\n" +
                "    O-AnotherClass aClassField: 0,0,10\n" +
                "    O-int aField: 0,0,10\n" +
                "  O-TestSubclass: 2,3,124\n" +
                "    ~References: 0,1,8\n" +
                "      ~java.util.List getList(): 0,1,8\n" +
                "    O-<init>(): 1,1,38\n" +
                "    O-java.util.List getAnotherList(): 1,1,38\n" +
                "  AnotherClass: 1,1,110\n" +
                "    <init>(): 1,1,46\n" +
                "    X-AnotherClass(int,TestSubclass): 0,0,0\n" +
                "  O-SomeAnnotation: 0,0,66\n" +
                "  X-RemovedSubclass: 0,0,0\n",
                sb.toString());
    }

    @Test
    public void sortReferenceTree() throws IOException {
        Map<Path, DexBackedDexFile> dexMap = getDexMap("Test2.dex");
        DexPackageNode packageTreeNode =
                new PackageTreeCreator(null, false).constructPackageTree(dexMap);

        StringBuffer sb = new StringBuffer(100);
        packageTreeNode.sort(Comparator.comparing(DexElementNode::getMethodDefinitionsCount));
        dumpTree(sb, packageTreeNode, 0, null, null);
        assertEquals(
                "root: 6,11,532\n" +
                "  ~java: 0,4,40\n" +
                "    ~lang: 0,3,32\n" +
                "      ~Integer: 0,1,8\n" +
                "        ~References: 0,1,8\n" +
                "          ~java.lang.Integer valueOf(int): 0,1,8\n" +
                "      ~Object: 0,1,8\n" +
                "        ~References: 0,1,8\n" +
                "          ~<init>(): 0,1,8\n" +
                "      ~Boolean: 0,1,8\n" +
                "        ~References: 0,1,8\n" +
                "          ~java.lang.Boolean valueOf(boolean): 0,1,8\n" +
                "      ~annotation: 0,0,8\n" +
                "        ~RetentionPolicy: 0,0,8\n" +
                "          ~References: 0,0,8\n" +
                "            ~java.lang.annotation.RetentionPolicy RUNTIME: 0,0,8\n" +
                "    ~util: 0,1,8\n" +
                "      ~Collections: 0,1,8\n" +
                "        ~References: 0,1,8\n" +
                "          ~java.util.List emptyList(): 0,1,8\n" +
                "  SomeAnnotation: 0,0,66\n" +
                "  a: 1,1,110\n" +
                "    <init>(): 1,1,46\n" +
                "  TestSubclass: 2,3,124\n" +
                "    ~References: 0,1,8\n" +
                "      ~java.util.List getList(): 0,1,8\n" +
                "    <init>(): 1,1,38\n" +
                "    java.util.List getAnotherList(): 1,1,38\n" +
                "  Test2: 3,3,192\n" +
                "    a aClassField: 0,0,10\n" +
                "    int aField: 0,0,10\n" +
                "    <init>(): 1,1,52\n" +
                "    java.lang.Integer get(): 1,1,42\n" +
                "    java.util.List getList(): 1,1,38\n",
                sb.toString());

        sb.setLength(0);
        packageTreeNode.sort(Comparator.comparing(DexElementNode::getName));
        dumpTree(sb, packageTreeNode, 0, null, null);
        assertEquals(
                "root: 6,11,532\n" +
                "  SomeAnnotation: 0,0,66\n" +
                "  Test2: 3,3,192\n" +
                "    <init>(): 1,1,52\n" +
                "    a aClassField: 0,0,10\n" +
                "    int aField: 0,0,10\n" +
                "    java.lang.Integer get(): 1,1,42\n" +
                "    java.util.List getList(): 1,1,38\n" +
                "  TestSubclass: 2,3,124\n" +
                "    ~References: 0,1,8\n" +
                "      ~java.util.List getList(): 0,1,8\n" +
                "    <init>(): 1,1,38\n" +
                "    java.util.List getAnotherList(): 1,1,38\n" +
                "  a: 1,1,110\n" +
                "    <init>(): 1,1,46\n" +
                "  ~java: 0,4,40\n" +
                "    ~lang: 0,3,32\n" +
                "      ~Boolean: 0,1,8\n" +
                "        ~References: 0,1,8\n" +
                "          ~java.lang.Boolean valueOf(boolean): 0,1,8\n" +
                "      ~Integer: 0,1,8\n" +
                "        ~References: 0,1,8\n" +
                "          ~java.lang.Integer valueOf(int): 0,1,8\n" +
                "      ~Object: 0,1,8\n" +
                "        ~References: 0,1,8\n" +
                "          ~<init>(): 0,1,8\n" +
                "      ~annotation: 0,0,8\n" +
                "        ~RetentionPolicy: 0,0,8\n" +
                "          ~References: 0,0,8\n" +
                "            ~java.lang.annotation.RetentionPolicy RUNTIME: 0,0,8\n" +
                "    ~util: 0,1,8\n" +
                "      ~Collections: 0,1,8\n" +
                "        ~References: 0,1,8\n" +
                "          ~java.util.List emptyList(): 0,1,8\n",
                sb.toString());
        sb.setLength(0);
        packageTreeNode.sort(Comparator.comparing(DexElementNode::getMethodReferencesCount));
        dumpTree(sb, packageTreeNode, 0, null, null);
        assertEquals(
                "root: 6,11,532\n" +
                "  SomeAnnotation: 0,0,66\n" +
                "  a: 1,1,110\n" +
                "    <init>(): 1,1,46\n" +
                "  Test2: 3,3,192\n" +
                "    a aClassField: 0,0,10\n" +
                "    int aField: 0,0,10\n" +
                "    <init>(): 1,1,52\n" +
                "    java.lang.Integer get(): 1,1,42\n" +
                "    java.util.List getList(): 1,1,38\n" +
                "  TestSubclass: 2,3,124\n" +
                "    ~References: 0,1,8\n" +
                "      ~java.util.List getList(): 0,1,8\n" +
                "    <init>(): 1,1,38\n" +
                "    java.util.List getAnotherList(): 1,1,38\n" +
                "  ~java: 0,4,40\n" +
                "    ~util: 0,1,8\n" +
                "      ~Collections: 0,1,8\n" +
                "        ~References: 0,1,8\n" +
                "          ~java.util.List emptyList(): 0,1,8\n" +
                "    ~lang: 0,3,32\n" +
                "      ~annotation: 0,0,8\n" +
                "        ~RetentionPolicy: 0,0,8\n" +
                "          ~References: 0,0,8\n" +
                "            ~java.lang.annotation.RetentionPolicy RUNTIME: 0,0,8\n" +
                "      ~Boolean: 0,1,8\n" +
                "        ~References: 0,1,8\n" +
                "          ~java.lang.Boolean valueOf(boolean): 0,1,8\n" +
                "      ~Integer: 0,1,8\n" +
                "        ~References: 0,1,8\n" +
                "          ~java.lang.Integer valueOf(int): 0,1,8\n" +
                "      ~Object: 0,1,8\n" +
                "        ~References: 0,1,8\n" +
                "          ~<init>(): 0,1,8\n",
                sb.toString());
    }

    @NonNull
    public static DexBackedDexFile getTestDexFile(@NonNull Path dexPath) throws IOException {
        return getDexFile(Files.readAllBytes(dexPath));
    }

    private static void dumpTree(
            StringBuffer sb,
            @NonNull DexElementNode node,
            int depth,
            ProguardSeedsMap seeds,
            ProguardMap map) {
        sb.append(" ".repeat(Math.max(0, depth * 2)));
        if (node.isRemoved()) {
            sb.append("X-");
        } else if (node.isSeed(seeds, map, true)) {
            sb.append("O-");
        } else if (!node.isDefined()) {
            sb.append("~");
        }
        sb.append(node.getName());
        sb.append(": ");
        sb.append(node.getMethodDefinitionsCount());
        sb.append(',');
        sb.append(node.getMethodReferencesCount());
        sb.append(',');
        sb.append(node.getSize());
        sb.append('\n');

        for (int i = 0; i < node.getChildCount(); i++) {
            dumpTree(sb, node.getChildAt(i), depth + 1, seeds, map);
        }
    }
}
