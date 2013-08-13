/*
 * Copyright (C) 2013 The Android Open Source Project
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


package com.android.sdklib.build;

import static com.android.SdkConstants.EXT_BC;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.BuildToolInfo;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Compiles Renderscript files.
 */
public class RenderScriptProcessor {

    // ABI list, as pairs of (android-ABI, toolchain-ABI)
    private static final class Abi {

        @NonNull
        private final String mDevice;
        @NonNull
        private final String mToolchain;
        @NonNull
        private final BuildToolInfo.PathId mLinker;
        @NonNull
        private final String[] mLinkerArgs;

        Abi(@NonNull String device,
            @NonNull String toolchain,
            @NonNull BuildToolInfo.PathId linker,
            @NonNull String... linkerArgs) {

            mDevice = device;
            mToolchain = toolchain;
            mLinker = linker;
            mLinkerArgs = linkerArgs;
        }
    }

    private static final Abi[] ABIS = {
            new Abi("armeabi-v7a", "armv7-none-linux-gnueabi", BuildToolInfo.PathId.LD_ARM,
                    "-dynamic-linker", "/system/bin/linker", "-X", "-m", "armelf_linux_eabi"),
            new Abi("mips", "mipsel-unknown-linux", BuildToolInfo.PathId.LD_MIPS, "-EL"),
            new Abi("x86", "i686-unknown-linux", BuildToolInfo.PathId.LD_X86, "-m", "elf_i386") };

    public static final String RS_DEPS = "rsDeps";

    @NonNull
    private final List<File> mInputs;

    @NonNull
    private final List<File> mImportFolders;

    @NonNull
    private final File mBuildFolder;

    @NonNull
    private final File mSourceOutputDir;

    @NonNull
    private final File mResOutputDir;

    @NonNull
    private final File mObjOutputDir;

    @NonNull
    private final File mLibOutputDir;

    @NonNull
    private final File mBinFolder;

    @NonNull
    private final BuildToolInfo mBuildToolInfo;

    private final int mTargetApi;

    private final boolean mDebugBuild;

    private final int mOptimLevel;

    private final boolean mSupportMode;

    private final File mRsLib;
    private final File mLibClCore;

    public interface CommandLineLauncher {
        void launch(
                @NonNull File executable,
                @NonNull List<String> arguments,
                @NonNull Map<String, String> envVariableMap)
                throws IOException, InterruptedException;
    }

    public RenderScriptProcessor(
            @NonNull List<File> inputs,
            @NonNull List<File> importFolders,
            @NonNull File buildFolder,
            @NonNull File sourceOutputDir,
            @NonNull File resOutputDir,
            @NonNull File objOutputDir,
            @NonNull File libOutputDir,
            @NonNull File binFolder,
            @NonNull BuildToolInfo buildToolInfo,
            int targetApi,
            boolean debugBuild,
            int optimLevel,
            boolean supportMode) {
        mInputs = inputs;
        mImportFolders = importFolders;
        mBuildFolder = buildFolder;
        mSourceOutputDir = sourceOutputDir;
        mResOutputDir = resOutputDir;
        mObjOutputDir = objOutputDir;
        mLibOutputDir = libOutputDir;
        mBinFolder = binFolder;
        mBuildToolInfo = buildToolInfo;
        mTargetApi = targetApi;
        mDebugBuild = debugBuild;
        mOptimLevel = optimLevel;
        mSupportMode = supportMode;

        if (supportMode) {
            File rs = new File(mBuildToolInfo.getLocation(), "renderscript");
            mRsLib = new File(rs, "lib");
            mLibClCore = new File(mRsLib, "libclcore.bc");
        } else {
            mLibClCore = null;
            mRsLib = null;
        }
    }

    public void cleanOldOutput(@Nullable Collection<File> oldOutputs) {
        if (oldOutputs != null) {
            for (File file : oldOutputs) {
                file.delete();

                // if the file is a .bc and support mode is on we need to delete the
                // associated .o and .so
                if (mSupportMode && file.getName().endsWith(SdkConstants.DOT_BC)) {
                    String name = file.getName();
                    String objName = name.replaceAll("\\.bc", ".o");
                    String soName = "librs." + name.replaceAll("\\.bc", ".so");

                    for (Abi abi : ABIS) {
                        new File(new File(mObjOutputDir, abi.mDevice), objName).delete();
                        new File(new File(mLibOutputDir, abi.mDevice), soName).delete();
                    }
                }
            }
        }
    }

    public void build(@NonNull CommandLineLauncher launcher)
            throws IOException, InterruptedException {

        // get the env var
        Map<String, String> env = Maps.newHashMap();
        if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_DARWIN) {
            env.put("DYLD_LIBRARY_PATH", mBuildToolInfo.getLocation().getAbsolutePath());
        } else if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_LINUX) {
            env.put("LD_LIBRARY_PATH", mBuildToolInfo.getLocation().getAbsolutePath());
        }

        doMainCompilation(launcher, env);

        if (mSupportMode) {
            createSupportFiles(launcher, env);
        }
    }

    private void doMainCompilation(@NonNull CommandLineLauncher launcher,
            @NonNull Map<String, String> env)
            throws IOException, InterruptedException {
        if (mInputs.isEmpty()) {
            return;
        }

        String renderscript = mBuildToolInfo.getPath(BuildToolInfo.PathId.LLVM_RS_CC);
        if (renderscript == null || !new File(renderscript).isFile()) {
            throw new IllegalStateException(BuildToolInfo.PathId.LLVM_RS_CC + " is missing");
        }

        String rsPath = mBuildToolInfo.getPath(BuildToolInfo.PathId.ANDROID_RS);
        String rsClangPath = mBuildToolInfo.getPath(BuildToolInfo.PathId.ANDROID_RS_CLANG);

        // the renderscript compiler doesn't expect the top res folder,
        // but the raw folder directly.
        File rawFolder = new File(mResOutputDir, SdkConstants.FD_RES_RAW);

        // compile all the files in a single pass
        ArrayList<String> command = Lists.newArrayListWithExpectedSize(25);

        if (mDebugBuild) {
            command.add("-g");
        }

        command.add("-O");
        command.add(Integer.toString(mOptimLevel));

        // add all import paths
        command.add("-I");
        command.add(rsPath);
        command.add("-I");
        command.add(rsClangPath);

        for (File importPath : mImportFolders) {
            if (importPath.isDirectory()) {
                command.add("-I");
                command.add(importPath.getAbsolutePath());
            }
        }

        command.add("-d");
        command.add(new File(mBinFolder, RS_DEPS).getAbsolutePath());
        command.add("-MD");

        // source output
        command.add("-p");
        command.add(mSourceOutputDir.getAbsolutePath());

        // res output
        command.add("-o");
        command.add(rawFolder.getAbsolutePath());

        command.add("-target-api");
        command.add(Integer.toString(mTargetApi < 11 ? 11 : mTargetApi));

        // input files
        for (File sourceFile : mInputs) {
            command.add(sourceFile.getAbsolutePath());
        }

        launcher.launch(new File(renderscript), command, env);
    }

    private void createSupportFiles(@NonNull CommandLineLauncher launcher,
            @NonNull Map<String, String> env) throws IOException, InterruptedException {
        // get the generated BC files.
        File rawFolder = new File(mResOutputDir, SdkConstants.FD_RES_RAW);

        SourceSearcher searcher = new SourceSearcher(Collections.singletonList(rawFolder), EXT_BC);
        FileGatherer fileGatherer = new FileGatherer();
        searcher.search(fileGatherer);

        for (File bcFile : fileGatherer.getFiles()) {
            String name = bcFile.getName();
            String objName = name.replaceAll("\\.bc", ".o");
            String soName = "librs." + name.replaceAll("\\.bc", ".so");

            for (Abi abi : ABIS) {
                File objFile = createSupportObjFile(bcFile, abi, objName, launcher, env);
                createSupportLibFile(objFile, abi, soName, launcher, env);
            }
        }
    }

    private File createSupportObjFile(
            @NonNull File bcFile,
            @NonNull Abi abi,
            @NonNull String objName,
            @NonNull CommandLineLauncher launcher,
            @NonNull Map<String, String> env) throws IOException, InterruptedException {


        // make sure the dest folder exist
        File abiFolder = new File(mObjOutputDir, abi.mDevice);
        abiFolder.mkdirs();

        File exe = new File(mBuildToolInfo.getPath(BuildToolInfo.PathId.BCC_COMPAT));

        List<String> args = Lists.newArrayListWithExpectedSize(10);

        args.add("-O" + Integer.toString(mOptimLevel));

        File outFile = new File(abiFolder, objName);
        args.add("-o");
        args.add(outFile.getAbsolutePath());

        args.add("-fPIC");
        args.add("-shared");

        args.add("-rt-path");
        args.add(mLibClCore.getAbsolutePath());

        args.add("-mtriple");
        args.add(abi.mToolchain);

        args.add(bcFile.getAbsolutePath());

        launcher.launch(exe, args, env);

        return outFile;
    }

    private void createSupportLibFile(
            @NonNull File objFile,
            @NonNull Abi abi,
            @NonNull String soName,
            @NonNull CommandLineLauncher launcher,
            @NonNull Map<String, String> env) throws IOException, InterruptedException {

        // make sure the dest folder exist
        File abiFolder = new File(mLibOutputDir, abi.mDevice);
        abiFolder.mkdirs();

        File intermediatesFolder = new File(mRsLib, "intermediates");
        File intermediatesAbiFolder = new File(intermediatesFolder, abi.mDevice);
        File packagedFolder = new File(mRsLib, "packaged");
        File packagedAbiFolder = new File(packagedFolder, abi.mDevice);

        List<String> args = Lists.newArrayListWithExpectedSize(25);

        args.add("--eh-frame-hdr");
        Collections.addAll(args, abi.mLinkerArgs);
        args.add("-shared");
        args.add("-Bsymbolic");
        args.add("-z");
        args.add("noexecstack");
        args.add("-z");
        args.add("relro");
        args.add("-z");
        args.add("now");

        File outFile = new File(abiFolder, soName);
        args.add("-o");
        args.add(outFile.getAbsolutePath());

        args.add("-L" + intermediatesAbiFolder.getAbsolutePath());
        args.add("-L" + packagedAbiFolder.getAbsolutePath());

        args.add("-soname");
        args.add(soName);

//        args.add("-rpath");


        args.add(objFile.getAbsolutePath());
        args.add(new File(intermediatesAbiFolder, "libcompiler_rt.a").getAbsolutePath());

        args.add("-lRSSupport");
        args.add("-lm");
        args.add("-lc");

        File exe = new File(mBuildToolInfo.getPath(abi.mLinker));

        launcher.launch(exe, args, env);
    }
}
