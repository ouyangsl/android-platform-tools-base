/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.lint.annotations;

import static com.android.SdkConstants.DOT_KT;
import static java.io.File.pathSeparator;
import static java.io.File.pathSeparatorChar;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.LintCliClient;
import com.android.tools.lint.UastEnvironment;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Project;
import com.android.utils.SdkUtils;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.intellij.mock.MockProject;
import com.intellij.psi.PsiFile;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kotlin.io.FilesKt;
import kotlin.text.Charsets;

/**
 * The extract annotations driver is a command line interface to extracting annotations from a
 * source tree. It's similar to the gradle ExtractAnnotations task, but usable from the command line
 * and outside Gradle, for example to extract annotations from the Android framework itself (which
 * is not built with Gradle). It also allows other options only interesting for extracting platform
 * annotations, such as filtering all APIs and constants through an API allow list (such that we for
 * example can pull annotations from the main branch which has the latest metadata, but only expose
 * APIs that are actually in a released platform), as well as translating android.annotation
 * annotations into android.support.annotations.
 */
public class ExtractAnnotationsDriver {

    public static void main(String[] args) throws IOException {
        int status = new ExtractAnnotationsDriver().run(args);
        if (status != 0) {
            System.exit(status);
        }
    }

    private static void usage(PrintStream output) {
        output.println("Usage: " + ExtractAnnotationsDriver.class.getSimpleName() + " <flags>");
        output.println(
                " --sources <paths>       : Source directories or files to extract annotations from.");
        output.println(
                "                           Separate paths with "
                        + pathSeparator
                        + ", and you can use @ ");
        output.println(
                "                           as a filename prefix to have the filenames fed from a file");
        output.println(
                "--classpath <paths>      : Directories and .jar files to resolve symbols from");
        output.println(
                "--output <zip path>      : The .zip file to write the extracted annotations to, if any");
        output.println(
                "--proguard <path>        : The proguard.cfg file to write the keep rules to, if any");
        output.println();
        output.println("Optional flags:");
        output.println("--merge-zips <paths>     : Existing external annotation files to merge in");
        output.println("--quiet                  : Don't print summary information");
        output.println(
                "--rmtypedefs <folder>    : Remove typedef classes found in the given folder");
        output.println(
                "--allow-missing-types    : Don't fail even if some types can't be resolved");
        output.println(
                "--allow-errors           : Don't fail even if there are some compiler errors");
        output.println(
                "--api-filter <api.txt>   : A framework API definition to restrict included APIs to");
        output.println(
                "--hide-filtered          : If filtering out non-APIs, supply this flag to hide listing matches");
        output.println(
                "--skip-class-retention   : Don't extract annotations that have class retention");
        output.println(
                "--typedef-file <path>    : Write a packaging recipe description to the given file");
        output.println(
                "--source-roots <paths>   : Source directories to find classes.\n"
                        + "                           If not specified the roots are derived from the sources above");
        output.println(
                "--no-sort                : Do not sort the output alphabetically, output the\n"
                        + "                           extracted annotations in the order they are visited");
        output.println(
                "--strict-typedef-retention : Fail if encountering a typedef with incorrect retention");
    }

    @SuppressWarnings("MethodMayBeStatic")
    public int run(@NonNull String[] args) throws IOException {
        List<File> classpath = Lists.newArrayList();
        List<File> sources = Lists.newArrayList();
        List<File> mergePaths = Lists.newArrayList();
        List<File> apiFilters = null;
        List<File> rmTypeDefs = null;
        boolean verbose = true;
        boolean allowMissingTypes = false;
        boolean allowErrors = false;
        boolean listFiltered = true;
        boolean skipClassRetention = false;
        boolean strictTypedefRetention = false;
        boolean sortAnnotations = true;
        List<File> sourceRoots = null;
        boolean useK2Uast = false;

        File output = null;
        File proguard = null;
        File typedefFile = null;
        if (args.length == 1 && "--help".equals(args[0])) {
            usage(System.out);
            return -1;
        }
        if (args.length < 2) {
            usage(System.err);
            return -1;
        }
        for (int i = 0, n = args.length; i < n; i++) {
            String flag = args[i];

            switch (flag) {
                case "--quiet":
                    verbose = false;
                    continue;
                case "--allow-missing-types":
                    allowMissingTypes = true;
                    continue;
                case "--allow-errors":
                    allowErrors = true;
                    continue;
                case "--hide-filtered":
                    listFiltered = false;
                    continue;
                case "--skip-class-retention":
                    skipClassRetention = true;
                    continue;
                case "--no-sort":
                    sortAnnotations = false;
                    continue;
                case "--strict-typedef-retention":
                    strictTypedefRetention = true;
                    continue;
                case "--XuseK2Uast":
                    // Same CLI flag as Lint
                    useK2Uast = true;
                    continue;
            }
            if (i == n - 1) {
                usage(System.err);
            }
            String value = args[i + 1];
            i++;

            switch (flag) {
                case "--sources":
                    sources = getFiles(value);
                    if (sources == null) {
                        return -1;
                    }
                    break;
                case "--classpath":
                    classpath = getFiles(value);
                    if (classpath == null) {
                        return -1;
                    }
                    break;
                case "--merge-zips":
                    mergePaths = getFiles(value);
                    if (mergePaths == null) {
                        return -1;
                    }
                    break;

                case "--output":
                    output = new File(value);
                    if (output.exists()) {
                        if (output.isDirectory()) {
                            System.err.println(output + " is a directory");
                            return -1;
                        }
                        boolean deleted = output.delete();
                        if (!deleted) {
                            System.err.println("Could not delete previous version of " + output);
                            return -1;
                        }
                    } else if (output.getParentFile() != null && !output.getParentFile().exists()) {
                        System.err.println(output.getParentFile() + " does not exist");
                        return -1;
                    }
                    break;
                case "--proguard":
                    proguard = new File(value);
                    if (proguard.exists()) {
                        if (proguard.isDirectory()) {
                            System.err.println(proguard + " is a directory");
                            return -1;
                        }
                        boolean deleted = proguard.delete();
                        if (!deleted) {
                            System.err.println("Could not delete previous version of " + proguard);
                            return -1;
                        }
                    } else if (proguard.getParentFile() != null
                            && !proguard.getParentFile().exists()) {
                        System.err.println(proguard.getParentFile() + " does not exist");
                        return -1;
                    }
                    break;
                case "--typedef-file":
                    typedefFile = new File(value);
                    break;
                case "--api-filter":
                    if (apiFilters == null) {
                        apiFilters = Lists.newArrayList();
                    }
                    for (String path : Splitter.on(",").omitEmptyStrings().split(value)) {
                        File apiFilter = new File(path);
                        if (!apiFilter.isFile()) {
                            String message = apiFilter + " does not exist or is not a file";
                            System.err.println(message);
                            return -1;
                        }
                        apiFilters.add(apiFilter);
                    }
                    break;
                case "--rmtypedefs":
                    File classDir = new File(value);
                    if (!classDir.isDirectory()) {
                        System.err.println(classDir + " is not a directory");
                        return -1;
                    }
                    if (rmTypeDefs == null) {
                        rmTypeDefs = new ArrayList<>();
                    }
                    rmTypeDefs.add(classDir);
                    break;
                case "--source-roots":
                    sourceRoots = getFiles(value);
                    if (sourceRoots == null) {
                        return -1;
                    }
                    break;
                default:
                    System.err.println(
                            "Unknown flag " + flag + ": Use --help for usage information");
                    return -1;
            }
        }

        if (sources.isEmpty()) {
            System.err.println("Must specify at least one source path");
            return -1;
        }
        if (classpath.isEmpty()) {
            System.err.println(
                    "Must specify classpath pointing to at least android.jar or the framework");
            return -1;
        }
        if (output == null && proguard == null) {
            System.err.println(
                    "Must specify output path with --output or a proguard path with --proguard");
            return -1;
        }

        // API definition files
        ApiDatabase database = null;
        if (apiFilters != null && !apiFilters.isEmpty()) {
            try {
                List<String> lines = Lists.newArrayList();
                for (File file : apiFilters) {
                    lines.addAll(Files.readLines(file, Charsets.UTF_8));
                }
                database = new ApiDatabase(lines);
            } catch (IOException e) {
                System.err.println(
                        "Could not open API database "
                                + apiFilters
                                + ": "
                                + e.getLocalizedMessage());
                return -1;
            }
        }

        Extractor extractor =
                new Extractor(
                        database,
                        rmTypeDefs,
                        verbose,
                        !skipClassRetention,
                        strictTypedefRetention,
                        sortAnnotations);
        extractor.setListIgnored(listFiltered);

        UastEnvironment.Configuration config =
                UastEnvironment.Configuration.create(
                        /* enableKotlinScripting */ false, /* useFirUast */ useK2Uast);
        if (sourceRoots == null) {
            sourceRoots = findSourceRoots(sources);
            if (sourceRoots == null) {
                return -1;
            }
        }
        LintClient lintClient = new LintCliClient(LintClient.CLIENT_CLI);
        File dir = sourceRoots.stream().filter(File::isDirectory).findAny().orElse(null);
        if (dir == null) {
            return -1;
        }
        Project lintProject = Project.create(lintClient, dir, /* referenceDir */ dir);
        lintProject.getJavaSourceFolders().addAll(sourceRoots);
        lintProject.getJavaLibraries().addAll(classpath);
        List<UastEnvironment.Module> modules = new ArrayList<>();
        modules.add(
                new UastEnvironment.Module(
                        lintProject,
                        /* jdkHome */ null,
                        /* includeTests */ false,
                        /* includeTestFixtureSources */ false,
                        /* isUnitTest */ false));
        config.addModules(modules, /* bootClassPaths */ null);

        UastEnvironment env = UastEnvironment.create(config);
        MockProject project = env.getIdeaProject();

        List<File> allSourceFiles = Extractor.gatherSources(sources);
        List<? extends PsiFile> units = Extractor.createUnitsForFiles(project, allSourceFiles);

        List<File> ktFiles = new ArrayList<>();
        for (File file : allSourceFiles) {
            if (file.getPath().endsWith(DOT_KT)) {
                ktFiles.add(file);
            }
        }

        env.analyzeFiles(ktFiles);
        extractor.extractFromProjectSource(units);

        for (File jar : mergePaths) {
            extractor.mergeExisting(jar);
        }

        extractor.export(output, proguard);

        // Remove typedefs?
        if (typedefFile != null) {
            extractor.writeTypedefFile(typedefFile);
        }

        //noinspection VariableNotUsedInsideIf
        if (rmTypeDefs != null) {
            if (typedefFile != null) {
                Extractor.removeTypedefClasses(rmTypeDefs, typedefFile);
            } else {
                extractor.removeTypedefClasses();
            }
        }

        env.dispose();
        return 0;
    }

    private static final String SEP_JAVA_SEP = File.separator + "java" + File.separator;
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("package\\s+(.*)\\s*;");

    private static List<File> findSourceRoots(@NonNull List<File> sources) {
        List<File> roots = Lists.newArrayList();
        for (File sourceFile : sources) {
            if (sourceFile.isDirectory()) {
                if (!roots.contains(sourceFile)) {
                    roots.add(sourceFile);
                }
                continue;
            }

            String path = sourceFile.getPath();
            if (!(path.endsWith(SdkConstants.DOT_JAVA) || path.endsWith(DOT_KT))) {
                continue;
            }

            int index = path.indexOf(SEP_JAVA_SEP);
            if (index != -1) {
                File root = new File(path.substring(0, index + SEP_JAVA_SEP.length()));
                if (!roots.contains(root)) {
                    roots.add(root);
                }
                continue;
            }

            try {
                String source = FilesKt.readText(sourceFile, Charsets.UTF_8);
                Matcher matcher = PACKAGE_PATTERN.matcher(source);
                boolean foundPackage = matcher.find();
                if (!foundPackage) {
                    System.err.println("Couldn't find package declaration in " + sourceFile);
                    return null;
                }
                String pkg = matcher.group(1).trim();
                int end = path.lastIndexOf(File.separatorChar);
                if (end != -1) {
                    String relative = pkg.replace('.', File.separatorChar);
                    if (SdkUtils.endsWith(path, end, relative)) {
                        String rootPath = path.substring(0, end - relative.length());
                        File root = new File(rootPath);
                        if (!roots.contains(root)) {
                            roots.add(root);
                        }
                    } else {
                        System.err.println(
                                "File found in a folder that doesn't appear to match the package "
                                        + "declaration: package="
                                        + pkg
                                        + " and file path="
                                        + path);
                        return null;
                    }
                }
            } catch (Exception e) {
                System.err.println("Couldn't access " + sourceFile);
                return null;
            }
        }

        return roots;
    }

    @Nullable
    private static List<File> getFiles(String value) {
        List<File> files = Lists.newArrayList();
        Splitter splitter = Splitter.on(pathSeparatorChar).omitEmptyStrings().trimResults();
        for (String path : splitter.split(value)) {
            if (path.startsWith("@")) {
                // Special syntax for providing files in a list
                File sourcePath = new File(path.substring(1));
                if (!sourcePath.exists()) {
                    System.err.println(sourcePath + " does not exist");
                    return null;
                }
                try {
                    for (String line : FilesKt.readLines(sourcePath, Charsets.UTF_8)) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            File file = new File(line);
                            if (!file.exists()) {
                                // Some files don't have newlines
                                for (String l : Splitter.on(CharMatcher.whitespace()).split(line)) {
                                    if (!l.isEmpty()) {
                                        file = new File(l);
                                        if (!file.exists()) {
                                            System.err.println(
                                                    "Warning: Could not find file "
                                                            + l
                                                            + " listed in "
                                                            + sourcePath);
                                        }
                                        files.add(file);
                                    }
                                }
                            }
                            files.add(file);
                        }
                    }
                    continue;
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }
            File file = new File(path);
            if (!file.exists()) {
                System.err.println(file + " does not exist");
                return null;
            }
            files.add(file);
        }

        return files;
    }
}
