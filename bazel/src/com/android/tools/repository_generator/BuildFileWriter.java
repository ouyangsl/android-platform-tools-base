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

package com.android.tools.repository_generator;

import org.apache.commons.lang3.SystemUtils;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Utility for generating a BUILD file from a {@link ResolutionResult} object. */
public class BuildFileWriter {

    /**
     * The prefix for the repository, relative to the generated BUILD file. The full path for all
     * artifacts will be: $BUILD_FILE_PATH / repoPrefix / artifactPath
     */
    private final Path repoPrefix;

    /** The file writer for the generated BUILD file. */
    private final FileWriter fileWriter;

    /**
     * Names of rules already generated. Used to avoid generating the same BUILD rule more than
     * once.
     */
    private final Set<String> generatedRuleNames = new HashSet<>();

    public BuildFileWriter(Path repoPath, String filePath) throws IOException {
        // The relative path of the Maven repository with respect to the directory
        // that the generated BUILD file will be mounted at.
        // This assumes the following:
        //   The maven repo is at studio-main/prebuilts/tools/common/m2/repository
        //   The BUILD file will be mounted at studio-main/prebuilts/tools/m2/
        repoPrefix = Paths.get("repository");
        fileWriter = new FileWriter(filePath);
    }

    /**
     * Generates a BUILD file from the given {@link ResolutionResult} object.
     *
     * <p>Inside the generated BUILD file, it puts:
     *
     * <ul>
     *   <li>maven_import rules for {@link ResolutionResult#dependencies},
     *   <li>maven_artifact rules for {@link ResolutionResult#unresolvedDependencies},
     *   <li>maven_artifact rules for {@link ResolutionResult#parents},
     * </ul>
     */
    public void write(ResolutionResult result) throws Exception {
        fileWriter.append(
                "load(\"@//tools/base/bazel:maven.bzl\", \"maven_artifact\","
                        + " \"maven_import\")\n\n");
        fileWriter.append("# Bazel rules auto-generated from maven repo.");
        for (ResolutionResult.Dependency dep : result.dependencies) {
            write(result, dep, false);
        }
        for (ResolutionResult.Dependency dep : result.unresolvedDependencies) {
            write(result, dep, true);
        }
        for (ResolutionResult.Parent parent : result.parents) {
            write(parent);
        }
        fileWriter.close();
    }

    private void write(ResolutionResult result, ResolutionResult.Dependency dep, boolean isConflictLoser)
            throws IOException {
        if (dep.file == null) return;

        Map<String, String> coord_parts = parseCoord(dep.coord);
        String classifier = coord_parts.getOrDefault("classifier", "default");
        if (classifier.equals("sources")) {
            // Do not treat source jars as regular dependencies. They can only be artifacts
            // attached to other non-source dependencies.
            return;
        }

        // Deduce the repo path of the artifact from the file.
        Path artifactRepoPath = Paths.get(dep.file).getParent();

        // All deps, conflict loser or winner, must have a maven_artifact() rule that includes the artifact
        // version in the rule's name.
        String mavenArtifactRuleName = getMavenArtifactRuleName(dep.coord);
        if (generatedRuleNames.add(mavenArtifactRuleName)) {
            fileWriter.append("\n");
            fileWriter.append("maven_artifact(\n");
            fileWriter.append(String.format("    name = \"%s\",\n", mavenArtifactRuleName));
            fileWriter.append(
                    String.format("    pom = \"%s/%s\",\n", repoPrefix, pathToString(dep.pomPath)));
            fileWriter.append(
                    String.format("    repo_root_path = \"%s\",\n", pathToString(repoPrefix)));
            fileWriter.append(
                    String.format("    repo_path = \"%s\",\n", pathToString(artifactRepoPath)));
            if (dep.parentCoord != null) {
                String parentRuleName = getMavenArtifactRuleName(dep.parentCoord);
                fileWriter.append(String.format("    parent = \"%s\",\n", parentRuleName));
            }
            Stream<String> originalDepRuleNamesStream =
                    Arrays.stream(dep.originalDependencies)
                            .map(BuildFileWriter::getMavenArtifactRuleName);

            if (!isConflictLoser) {
                // b/201683107: There can be some conflict losers that map to the same
                // mavenArtifactRuleName as this conflict winner, but with a different
                // set of dependencies because they have a different 'exclusions' section
                // in their incoming dependency edge.
                // We define the 'deps' of the generated maven_artifact rule to be the
                // union of the dependencies of all such nodes, so here we merge all of those
                // dependencies.
                // Example:
                //   kotlin-gradle-plugin:1.4.32 (conflict loser)
                //      -> semver4j (conflict loser)
                //          -> antlr4-runtime:4.5.2-1
                //   kotlin-gradle-plugin:1.5.31 (conflict winner)
                //      -> semver4j:nodeps (conflict winner)
                //          -> no deps, because kotlin-gradle-pluin:1.5.31 has exclusions=*:*
                //             in its semver4j:nodeps dependency declaration.
                // The dependency graph ends up having two nodes for "semver4j":
                //   semver4j        (conflict loser), deps = ["antlr4-runtime:4.5.2-1"]
                //   semver4j:nodeps (conflict winner), deps = []
                // And we merge the deps section to make sure the "antlr4-runtime:4.5.2-1"
                // is included in the deps list.
                for (ResolutionResult.Dependency unresolvedDep : result.unresolvedDependencies) {
                    if (mavenArtifactRuleName.equals(getMavenArtifactRuleName(unresolvedDep.coord))) {
                        originalDepRuleNamesStream = Stream.concat(
                                originalDepRuleNamesStream,
                                Arrays.stream(unresolvedDep.originalDependencies)
                                    .map(BuildFileWriter::getMavenArtifactRuleName));
                    }
                }
            }
            String[] originalDepRuleNames = originalDepRuleNamesStream.distinct().toArray(String[]::new);

            if (originalDepRuleNames.length != 0) {
                fileWriter.append("    deps = [\n");
                for (String dependency : originalDepRuleNames) {
                    fileWriter.append(String.format("        \"%s\",\n", dependency));
                }
                fileWriter.append("    ],\n");
            }
            fileWriter.append("    visibility = [\"//visibility:public\"],\n");
            fileWriter.append(")\n");
        }

        // Any dependency that is not a conflict loser (i.e., never entered into a conflict, or won a conflict) gets
        // a maven_import() rule that does not have the artifact version in the rule name.
        if (!isConflictLoser) {
            String ruleName = getMavenImportRuleName(dep.coord);
            fileWriter.append("\n");
            fileWriter.append("maven_import(\n");
            fileWriter.append(String.format("    name = \"%s\",\n", ruleName));
            // TODO: Implement classifiers only if we really need them.
            fileWriter.append("    classifiers = [],\n");
            if (dep.parentCoord != null) {
                String parentRuleName = getMavenArtifactRuleName(dep.parentCoord);
                fileWriter.append(String.format("    parent = \"%s\",\n", parentRuleName));
            }
            fileWriter.append("    jars = [\n");
            if (dep.file.endsWith(".jar")) {
                fileWriter.append(
                        String.format("        \"%s/%s\"\n", repoPrefix, pathToString(dep.file)));
            }
            fileWriter.append("    ],\n");
            for (Map.Entry<String, List<String>> scopedDeps : dep.directDependencies.entrySet()) {
                String scope = scopedDeps.getKey();
                List<String> deps = scopedDeps.getValue();
                if (!deps.isEmpty()) {
                    switch (scope) {
                        case "compile":
                            fileWriter.append("    exports = [\n");
                            for (String d : deps) {
                                fileWriter.append(
                                        String.format(
                                                "        \"%s\",\n", getMavenImportRuleName(d)));
                            }
                            fileWriter.append("    ],\n");
                            break;
                        case "runtime":
                            fileWriter.append("    deps = [\n");
                            for (String d : deps) {
                                fileWriter.append(
                                        String.format(
                                                "        \"%s\",\n", getMavenImportRuleName(d)));
                            }
                            fileWriter.append("    ],\n");
                            break;
                        case "import":
                            // Ignore dependencies of scope=import. They are not required by the maven_import rules,
                            // and are only used when generating maven_artifact rules.
                            break;
                        default:
                            throw new IllegalStateException("Scope " + scope + " is not supported");
                    }
                }
            }
            // Original dependencies use version numbers in their rule names.
            String[] originalDepRuleNames =
                    Arrays.stream(dep.originalDependencies)
                            .map(BuildFileWriter::getMavenArtifactRuleName)
                            .distinct()
                            .toArray(String[]::new);
            if (originalDepRuleNames.length != 0) {
                fileWriter.append("    original_deps = [\n");
                for (String originalDependency : originalDepRuleNames) {
                    fileWriter.append(String.format("        \"%s\",\n", originalDependency));
                }
                fileWriter.append("    ],\n");
            } else {
                fileWriter.append("    original_deps = [],\n");
            }
            fileWriter.append(
                    String.format("    pom = \"%s/%s\",\n", repoPrefix, pathToString(dep.pomPath)));
            fileWriter.append(String.format("    repo_root_path = \"%s\",\n", repoPrefix));
            fileWriter.append(
                    String.format("    repo_path = \"%s\",\n", pathToString(artifactRepoPath)));
            if (dep.srcjar != null) {
                fileWriter.append(
                        String.format(
                                "    srcjar = \"%s/%s\",\n", repoPrefix, pathToString(dep.srcjar)));
            }
            if (dep.declaredExclusions != null && !dep.declaredExclusions.isEmpty()) {
                fileWriter.append("    deps_with_exclusions = [\n");

                for (String child : dep.declaredExclusions.keySet()) {
                    fileWriter.append(String.format("        \"%s\",\n", child));
                }
                fileWriter.append("     ],\n");
            }
            if (dep.exclusionsForParent != null && !dep.exclusionsForParent.isEmpty()) {
                fileWriter.append("    exclusions_for_parents = {\n");

                for (Map.Entry<String, List<String>> exclusions :
                        dep.exclusionsForParent.entrySet()) {
                    fileWriter.append(String.format("        \"%s\": [\n", exclusions.getKey()));
                    for (String depToExclude : exclusions.getValue()) {
                        fileWriter.append(String.format("            \"%s\",\n", depToExclude));
                    }
                    fileWriter.append("        ],\n");
                }
                fileWriter.append("    },\n");
            }
            fileWriter.append("    visibility = [\"//visibility:public\"],\n");
            fileWriter.append(")\n");
            if (!generatedRuleNames.add(ruleName)) {
                throw new RuntimeException("Rule already exists: " + ruleName);
            }
        }
    }

    private void write(ResolutionResult.Parent parent) throws IOException {
        // Generate rule for parent, if a parent exists, and if it's not created already.
        String ruleName = getMavenArtifactRuleName(parent.coord);
        if (!generatedRuleNames.add(ruleName)) return;

        fileWriter.append("\n");
        fileWriter.append("maven_artifact(\n");
        fileWriter.append(String.format("    name = \"%s\",\n", ruleName));
        fileWriter.append(
                String.format("    pom = \"%s/%s\",\n", repoPrefix, pathToString(parent.pomPath)));
        fileWriter.append(String.format("    repo_root_path = \"%s\",\n", repoPrefix));
        // Deduce the repo path of the artifact from the pom file.
        Path artifactRepoPath = Paths.get(parent.pomPath).getParent();
        fileWriter.append(
                String.format("    repo_path = \"%s\",\n", pathToString(artifactRepoPath)));
        if (parent.parentCoord != null) {
            String parentRuleName = getMavenArtifactRuleName(parent.parentCoord);
            fileWriter.append(String.format("    parent = \"%s\",\n", parentRuleName));
        }
        fileWriter.append(")\n");
    }

    /** Parses a Maven coordinate into a map whose keys are the names of the coordinate fields. */
    public static Map<String, String> parseCoord(String coord) {
        String[] pieces = coord.split(":");
        String group = pieces[0];
        String artifact = pieces[1];

        if (pieces.length == 3) {
            String version = pieces[2];
            HashMap<String, String> result = new HashMap<>();
            result.put("group", group);
            result.put("artifact", artifact);
            result.put("version", version);
            return result;
        } else if (pieces.length == 4) {
            String packaging = pieces[2];
            String version = pieces[3];
            HashMap<String, String> result = new HashMap<>();
            result.put("group", group);
            result.put("artifact", artifact);
            result.put("packaging", packaging);
            result.put("version", version);
            return result;
        } else if (pieces.length == 5) {
            String packaging = pieces[2];
            String classifier = pieces[3];
            String version = pieces[4];
            HashMap<String, String> result = new HashMap<>();
            result.put("group", group);
            result.put("artifact", artifact);
            result.put("packaging", packaging);
            result.put("classifier", classifier);
            result.put("version", version);
            return result;
        } else {
            throw new RuntimeException("Could not parse maven coordinate: " + coord);
        }
    }

    /**
     * Converts the given Maven coordinate into a string that can be used as a Bazel rule name.
     * Do not call this method directly. Instead, use getMavenArtifactRuleName or getMavenImportRuleName.
     *
     *
     * @param coord the Maven coordinate for which to generate a Bazel rule name
     * @param useVersion If true, the version field in the given coordinate will also be a part of
     *     the generated rule name.
     * @param useClassifier If true, the classifier field in the given coordinate (if exists) will
     *     also be a part of the generated rule name.
     */
    public static String ruleNameFromCoord(String coord, boolean useVersion, boolean useClassifier) {
        Map<String, String> parts = parseCoord(coord);

        String ruleName;
        if (useClassifier && parts.containsKey("classifier")) {
            ruleName =
                    String.join(
                            ".",
                            new String[] {
                                parts.get("group"), parts.get("artifact"), parts.get("classifier")
                            });
        } else {
            ruleName = String.join(".", new String[] {parts.get("group"), parts.get("artifact")});
        }

        if (useVersion) {
            return ruleName + "_" + parts.get("version");
        } else {
            return ruleName;
        }
    }

    public static String getMavenArtifactRuleName(String coord) {
        return ruleNameFromCoord(coord, true, false);
    }

    public static String getMavenImportRuleName(String coord) {
        return ruleNameFromCoord(coord, false, true);
    }

    /** Converts path to forward slash separated string. */
    private static String pathToString(Path path) {
        if (!SystemUtils.IS_OS_WINDOWS) {
            return path.toString();
        }

        return path.toString().replaceAll("\\\\", "/");
    }

    /** Converts a string that represents a path into forward slash separated string. */
    private static String pathToString(String input) {
      return pathToString(Paths.get(input));
    }
}
