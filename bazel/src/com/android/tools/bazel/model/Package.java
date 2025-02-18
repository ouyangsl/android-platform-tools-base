/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.bazel.model;

import com.android.tools.bazel.parser.BuildParser;
import com.android.tools.bazel.parser.Tokenizer;
import com.android.tools.bazel.parser.ast.Build;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class Package {

    private final String name;
    private final Workspace workspace;
    private Map<String, BazelRule> rules = Maps.newLinkedHashMap();
    private Build buildFile;

    public Package(Workspace workspace, String name) {
        this.workspace = workspace;
        this.name = name;
    }

    public Build getBuildFile() throws IOException {
        if (buildFile == null) {
            File file = findBuildFile();
            Tokenizer tokenizer = new Tokenizer(file);
            BuildParser parser = new BuildParser(tokenizer);
            buildFile = parser.parse();
        }
        return buildFile;
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    @NotNull
    public File findBuildFile() {
        return buildFile(getPackageDir());
    }

    @NotNull
    private static File buildFile(@NotNull File dir) {
        // Use test files if found.
        for (int i = 0; i < Workspace.BUILD_FILES.length; i++) {
            File file = new File(dir, Workspace.BUILD_FILES[i]);
            if (i == Workspace.BUILD_FILES.length - 1 || file.exists()) {
                return file;
            }
        }
        throw new IllegalStateException("BUILD_FILES must not be empty.");
    }

    public void generate(Workspace.GenerationListener listener) throws IOException {
        if (workspace == null) return;

        boolean hasRules = false;
        for (BazelRule rule : rules.values()) {
            if (rule.isExport()) {
                hasRules = true;
                break;
            }
        }
        if (!hasRules) return;

        File dir = getPackageDir();
        File build = buildFile(dir);
        for (BazelRule rule : rules.values()) {
            if (!rule.isEmpty() && rule.isExport() && rule.shouldUpdate()) {
                rule.update();
            }
        }

        if (buildFile != null) {
            buildFile.hideNotUpdatedManagedStatements();
            File tmp = File.createTempFile("BUILD", "test");
            boolean keepFile = false;
            try {
                try (FileOutputStream fileOutputStream = new FileOutputStream(tmp);
                        OutputStreamWriter outputStreamWriter =
                                new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8);
                        PrintWriter writer = new PrintWriter(outputStreamWriter)) {
                    buildFile.write(writer);
                }
                String before = Files.asCharSource(build, StandardCharsets.UTF_8).read();
                String after = Files.asCharSource(tmp, StandardCharsets.UTF_8).read();
                if (!before.equals(after)) {
                    if (listener.packageUpdated(name)) {
                        Files.copy(tmp, build);
                    } else {
                        keepFile = true;
                        listener.error(
                                "diff " + build.getAbsolutePath() + " " + tmp.getAbsolutePath());
                    }
                }
            } finally {
                if (!keepFile) {
                    if (!tmp.delete()) {
                        listener.error("Failed to delete " + tmp.getPath());
                    }
                }
            }
        }
    }

    public ImmutableSet<BazelRule> getRules() {
        return ImmutableSet.copyOf(rules.values());
    }

    @NotNull
    public File getPackageDir() {
        return new File(workspace.getDirectory(), name);
    }

    public String getRelativePath(File file) {
        return getPackageDir().toPath().relativize(file.toPath()).toString().replace("\\", "/");
    }

    public String getName() {
        return name;
    }

    public void addRule(BazelRule rule) {
        if (rules.get(rule.getName().toLowerCase(Locale.US)) != null) {
            throw new IllegalStateException("Package \"" + this.name + "\", cannot add rule:\n" + rule.toString());
        }
        rules.put(rule.getName().toLowerCase(Locale.US), rule);
    }

    public String getQualifiedName() {
        return workspace.getReference() + "//" + getName();
    }
}
