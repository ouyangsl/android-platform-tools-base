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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.apk.analyzer.dex.tree.*;
import com.android.tools.apk.analyzer.internal.SigUtils;
import com.android.tools.proguard.ProguardMap;
import com.android.tools.proguard.ProguardUsagesMap;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedClassDef;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedField;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedMethod;
import com.android.tools.smali.dexlib2.dexbacked.reference.DexBackedFieldReference;
import com.android.tools.smali.dexlib2.dexbacked.reference.DexBackedMethodReference;
import com.android.tools.smali.dexlib2.dexbacked.reference.DexBackedTypeReference;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.TypeReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import com.android.tools.smali.dexlib2.util.ReferenceUtil;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PackageTreeCreator {
    public static final String PARAMS_DELIMITER = ",";

    @Nullable private final ProguardMap proguardMap;
    @Nullable private final ProguardUsagesMap usagesMap;
    private final boolean addReferencesNode;

    public PackageTreeCreator(
            @Nullable ProguardMappings proguardMappings,
            boolean deobfuscateNames) {
        this(proguardMappings, deobfuscateNames, true);
    }

    public PackageTreeCreator(
            @Nullable ProguardMappings proguardMappings,
            boolean deobfuscateNames,
            boolean addReferencesNode) {
        proguardMap = (deobfuscateNames && proguardMappings != null) ? proguardMappings.map : null;
        usagesMap = proguardMappings == null ? null : proguardMappings.usage;
        this.addReferencesNode = addReferencesNode;
    }

    @NonNull
    private static Multimap<String, MethodReference> getAllMethodReferencesByClassName(
            @NonNull DexBackedDexFile dexFile) {
        Multimap<String, MethodReference> methodsByClass = ArrayListMultimap.create();
        for (int i = 0, m = dexFile.getMethodSection().size(); i < m; i++) {
            MethodReference methodRef = new DexBackedMethodReference(dexFile, i);
            methodsByClass.put(methodRef.getDefiningClass(), methodRef);
        }

        return methodsByClass;
    }

    @NonNull
    private static Multimap<String, FieldReference> getAllFieldReferencesByClassName(
            @NonNull DexBackedDexFile dexFile) {
        Multimap<String, FieldReference> fieldsByClass = ArrayListMultimap.create();
        for (int i = 0, m = dexFile.getFieldSection().size(); i < m; i++) {
            FieldReference fieldRef = new DexBackedFieldReference(dexFile, i);
            fieldsByClass.put(fieldRef.getDefiningClass(), fieldRef);
        }

        return fieldsByClass;
    }

    @NonNull
    private static Map<String, TypeReference> getAllTypeReferencesByClassName(
            @NonNull DexBackedDexFile dexFile) {
        HashMap<String, TypeReference> typesByName = new HashMap<>();
        for (int i = 0, m = dexFile.getTypeSection().size(); i < m; i++) {
            TypeReference typeRef = new DexBackedTypeReference(dexFile, i);
            typesByName.put(typeRef.getType(), typeRef);
        }

        return typesByName;
    }

    @NonNull
    public DexPackageNode constructPackageTree(@NonNull Map<Path, DexBackedDexFile> dexFiles) {
        DexPackageNode root = new DexPackageNode("root", null);
        for (Map.Entry<Path, DexBackedDexFile> dexFile : dexFiles.entrySet()) {
            constructPackageTree(root, dexFile.getKey(), dexFile.getValue());
        }
        return root;
    }

    @NonNull
    public DexPackageNode constructPackageTree(@NonNull DexBackedDexFile dexFile) {
        DexPackageNode root = new DexPackageNode("root", null);
        constructPackageTree(root, null, dexFile);
        return root;
    }

    public void constructPackageTree(
            @NonNull DexPackageNode root,
            @Nullable Path dexFilePath,
            @NonNull DexBackedDexFile dexFile) {
        //get all methods, fields and types referenced in this dex (includes defined)
        Multimap<String, MethodReference> methodRefsByClassName =
                getAllMethodReferencesByClassName(dexFile);
        Multimap<String, FieldReference> fieldRefsByClassName =
                getAllFieldReferencesByClassName(dexFile);
        Map<String, TypeReference> typeRefsByName = getAllTypeReferencesByClassName(dexFile);

        //remove methods and fields that are defined in this dex from the maps
        for (DexBackedClassDef classDef : dexFile.getClasses()) {
            for (DexBackedMethod method : classDef.getMethods()) {
                methodRefsByClassName.remove(classDef.getType(), method);
            }
            for (DexBackedField field : classDef.getFields()) {
                fieldRefsByClassName.remove(classDef.getType(), field);
            }
        }

        //add classes (and their methods and fields) defined in this file to the tree
        for (DexBackedClassDef classDef : dexFile.getClasses()) {
            TypeReference typeRef = typeRefsByName.get(classDef.getType());
            String className = decodeClassName(classDef.getType(), proguardMap);
            DexClassNode classNode = root.getOrCreateClass("", className, typeRef);
            classNode.setUserObject(dexFilePath);
            classNode.setDefined(true);
            classNode.setSize(classNode.getSize() + classDef.getSize());
            addMethods(classNode, classDef.getMethods(), dexFilePath);
            addFields(classNode, classDef.getFields(), dexFilePath);
        }

        //add method references which are not in a class defined in this dex file to the tree
        for (String className : methodRefsByClassName.keySet()) {
            TypeReference typeRef = typeRefsByName.get(className);
            String cleanClassName = decodeClassName(className, proguardMap);
            DexClassNode classNode = root.getOrCreateClass("", cleanClassName, typeRef);
            addMethods(classNode, methodRefsByClassName.get(className), dexFilePath);
        }

        //add field references which are not in a class defined in this dex file
        for (String className : fieldRefsByClassName.keySet()) {
            TypeReference typeRef = typeRefsByName.get(className);
            String cleanClassName = decodeClassName(className, proguardMap);
            DexClassNode classNode = root.getOrCreateClass("", cleanClassName, typeRef);
            addFields(classNode, fieldRefsByClassName.get(className), dexFilePath);
        }

        //add classes, methods and fields removed by Proguard
        if (usagesMap != null) {
            for (String className : usagesMap.getClasses()) {
                DexClassNode classNode = root.getOrCreateClass("", className, null);
                classNode.setDefined(false);
                classNode.setRemoved(true);
            }
            Multimap<String, String> removedMethodsByClass = usagesMap.getMethodsByClass();
            for (String className : removedMethodsByClass.keySet()) {
                DexClassNode classNode = root.getOrCreateClass("", className, null);
                for (String removedMethodName : removedMethodsByClass.get(className)) {
                    DexMethodNode methodNode = new DexMethodNode(removedMethodName, null);
                    methodNode.setDefined(false);
                    methodNode.setRemoved(true);
                    classNode.add(methodNode);
                }
            }
            Multimap<String, String> removedFieldsByClass = usagesMap.getFieldsByClass();
            for (String className : removedFieldsByClass.keySet()) {
                DexClassNode classNode = root.getOrCreateClass("", className, null);
                for (String removedFieldName : removedFieldsByClass.get(className)) {
                    DexFieldNode fieldNode = new DexFieldNode(removedFieldName, null);
                    fieldNode.setDefined(false);
                    fieldNode.setRemoved(true);
                    classNode.add(fieldNode);
                }
            }
        }

        root.update();
        root.sort(Comparator.comparing(DexElementNode::getMethodReferencesCount).reversed());
    }

    private void addMethods(
            @NonNull DexClassNode classNode,
            @NonNull Iterable<? extends MethodReference> methodRefs,
            Path dexFilePath) {
        for (MethodReference methodRef : methodRefs) {
            String methodName = decodeMethodName(methodRef, proguardMap);
            String returnType = decodeClassName(methodRef.getReturnType(), proguardMap);
            String params = decodeMethodParams(methodRef, proguardMap);
            String methodSig = returnType + " " + methodName + params;
            if (methodSig.startsWith("void <init>") || methodSig.startsWith("void <clinit>")) {
                methodSig = methodName + params;
            }
            if (methodRef instanceof DexBackedMethod) {
                DexMethodNode methodNode = getOrAddMethod(classNode, methodSig, methodRef);
                methodNode.setDefined(true);
                methodNode.setUserObject(dexFilePath);
                methodNode.setSize(methodNode.getSize() + ((DexBackedMethod) methodRef).getSize());
            } else if (methodRef instanceof DexBackedMethodReference) {
                final DexMethodNode methodNode;
                if (addReferencesNode) {
                    DexReferencesNode referencesNode = getOrCreateReferencesNode(classNode);
                    methodNode = getOrAddMethod(referencesNode, methodSig, methodRef);
                }
                else {
                    methodNode = getOrAddMethod(classNode, methodSig, methodRef);
                }
                methodNode.setSize(
                        methodNode.getSize() + ((DexBackedMethodReference)methodRef).getSize());
            }
        }
    }

    private void addFields(
            @NonNull DexClassNode classNode,
            @NonNull Iterable<? extends FieldReference> fieldRefs,
            Path dexFilePath) {
        for (FieldReference fieldRef : fieldRefs) {
            String fieldName = decodeFieldName(fieldRef, proguardMap);
            String fieldType = decodeClassName(fieldRef.getType(), proguardMap);
            String fieldSig = fieldType + " " + fieldName;
            if (fieldRef instanceof DexBackedField) {
                DexFieldNode fieldNode = getOrAddField(classNode, fieldSig, fieldRef);
                fieldNode.setDefined(true);
                fieldNode.setUserObject(dexFilePath);
                fieldNode.setSize(fieldNode.getSize() + ((DexBackedField) fieldRef).getSize());
            } else if (fieldRef instanceof DexBackedFieldReference) {
                final DexFieldNode fieldNode;
                if (addReferencesNode) {
                    DexReferencesNode referencesNode = getOrCreateReferencesNode(classNode);
                    fieldNode = getOrAddField(referencesNode, fieldSig, fieldRef);
                }
                else {
                    fieldNode = getOrAddField(classNode, fieldSig, fieldRef);
                }
                fieldNode.setSize(
                        fieldNode.getSize() + ((DexBackedFieldReference)fieldRef).getSize());
            }
        }
    }

    public static String decodeFieldName(
            @NonNull FieldReference fieldRef, @Nullable ProguardMap proguardMap) {
        String fieldName = fieldRef.getName();
        if (proguardMap != null) {
            String className = decodeClassName(fieldRef.getDefiningClass(), proguardMap);
            fieldName = proguardMap.getFieldName(className, fieldName);
        }
        return fieldName;
    }

    public static String decodeMethodParams(
            @NonNull MethodReference methodRef, @Nullable ProguardMap proguardMap) {
        Stream<String> params =
                methodRef
                        .getParameterTypes()
                        .stream()
                        .map(String::valueOf)
                        .map(SigUtils::signatureToName);
        if (proguardMap != null) {
            params = params.map(proguardMap::getClassName);
        }
        return "(" + params.collect(Collectors.joining(PARAMS_DELIMITER)) + ")";
    }

    public static String decodeMethodName(
            @NonNull MethodReference methodRef, @Nullable ProguardMap proguardMap) {
        if (proguardMap != null) {
            String className =
                    proguardMap.getClassName(
                            SigUtils.signatureToName(methodRef.getDefiningClass()));
            String methodName = methodRef.getName();
            String sigWithoutName =
                    ReferenceUtil.getMethodDescriptor(methodRef, true)
                            .substring(methodName.length());
            ProguardMap.Frame frame =
                    proguardMap.getFrame(className, methodName, sigWithoutName, null, -1);
            return frame.methodName;
        } else {
            return methodRef.getName();
        }
    }

    /**
     * Takes a JVM-style class name (e.g."Lcom/example/MyClass;"), optionally reverses obfuscation
     * using the supplied ProGuard mapping, and returns a fully qualified class name (dot-separated,
     * e.g. "com.example.MyClass").
     *
     * @param className JVM signature class name
     * @param proguardMap optional ProGuard mapping for deobfuscation
     * @return fully qualified java class name
     */
    public static String decodeClassName(
            @NonNull String className, @Nullable ProguardMap proguardMap) {
        className = SigUtils.signatureToName(className);
        if (proguardMap != null) {
            className = proguardMap.getClassName(className);
        }
        return className;
    }

    private static DexReferencesNode getOrCreateReferencesNode(DexClassNode classNode) {
        DexReferencesNode references = classNode.getChildByType(
                DexReferencesNode.NAME, DexReferencesNode.class);
        if (references == null) {
            references = new DexReferencesNode();
            classNode.insert(references, 0);
        }
        return references;
    }

    private DexMethodNode getOrAddMethod(DexElementNode parent, String sig, MethodReference ref) {
        DexMethodNode node = parent.getChildByType(sig, DexMethodNode.class);
        if (node == null) {
            node = new DexMethodNode(sig, ImmutableMethodReference.of(ref));
            parent.add(node);
        }
        return node;
    }

    private DexFieldNode getOrAddField(DexElementNode parent, String sig, FieldReference ref) {
        DexFieldNode node = parent.getChildByType(sig, DexFieldNode.class);
        if (node == null) {
            node = new DexFieldNode(sig, ImmutableFieldReference.of(ref));
            parent.add(node);
        }
        return node;
    }
}
