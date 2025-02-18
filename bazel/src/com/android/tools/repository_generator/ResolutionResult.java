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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Represents the dependency resolution result */
public class ResolutionResult {
    /** Dependencies in the dependency tree obtained after conflict resolution. */
    public List<Dependency> dependencies = new ArrayList<>();

    /** Dependencies in the dependency graph that are eliminated after conflict resolution. */
    public List<Dependency> unresolvedDependencies = new ArrayList<>();

    /** Transitive parents of all dependencies. */
    public List<Parent> parents = new ArrayList<>();

    public static class Dependency {

        public String coord;
        public String file;
        public String pomPath;
        public String parentCoord;
        public String srcjar;
        public String[] originalDependencies;
        public Map<String, List<String>> directDependencies;
        public Map<String, String> conflictResolution;
        public final Map<String, List<String>> declaredExclusions;
        public Map<String, List<String>> exclusionsForParent = new HashMap<>();

        public Dependency(
                String coord,
                String file,
                String pomPath,
                String parentCoord,
                String srcjar,
                String[] originalDependencies,
                Map<String, List<String>> directDependencies,
                Map<String, String> conflictResolution,
                Map<String, List<String>> exclusions) {
            this.coord = coord;
            this.file = file;
            this.pomPath = pomPath;
            this.parentCoord = parentCoord;
            this.srcjar = srcjar;
            this.originalDependencies = originalDependencies;
            this.directDependencies = directDependencies;
            this.conflictResolution = conflictResolution;
            this.declaredExclusions = exclusions;

            if (this.originalDependencies == null) {
                this.originalDependencies = new String[0];
            }
            if (this.directDependencies == null) {
                this.directDependencies = new HashMap<>();
            }
        }
    }

    public static class Parent {
        public String coord;
        public String pomPath;
        public String parentCoord;

        public Parent(String coord, String pomPath, String parentCoord) {
            this.coord = coord;
            this.pomPath = pomPath;
            this.parentCoord = parentCoord;
        }
    }

    public void finishBuilding() {
        dependencies.sort(Comparator.comparing(d -> d.coord));
        unresolvedDependencies.sort(Comparator.comparing(d -> d.coord));
        parents.sort(Comparator.comparing(d -> d.coord));
        Map<String, Dependency> dependenciesByCoord = new HashMap<>();
        for (Dependency d : dependencies) {
            String[] segments = d.coord.split(":");
            dependenciesByCoord.put(segments[0] + "." + segments[1], d);
        }
        // Exclusion information is only stored in the Dependencies that declared them at this,
        // but we also need to add information to the build rules for the affected children.
        // Add the relevant information to those Dependencies here.
        for (Dependency parentDep : dependencies) {
            String[] segments = parentDep.coord.split(":");
            String parentCoord = segments[0] + "." + segments[1];
            if (parentDep.declaredExclusions != null && !parentDep.declaredExclusions.isEmpty()) {
                for (Map.Entry<String, List<String>> entry :
                        parentDep.declaredExclusions.entrySet()) {
                    Dependency childDep = dependenciesByCoord.get(entry.getKey());
                    childDep.exclusionsForParent.put(parentCoord, entry.getValue());
                }
            }
        }
    }

    public void addDependency(Dependency dependency) {
        for (Dependency existingDependency : dependencies) {
            if (existingDependency.coord.equals(dependency.coord)) {
                mergeDependencies(existingDependency, dependency);
                return;
            }
        }

        dependencies.add(dependency);
    }

    public void addUnresolvedDependency(Dependency dependency) {
        for (Dependency existingDependency : unresolvedDependencies) {
            if (existingDependency.coord.equals(dependency.coord)) {
                mergeDependencies(existingDependency, dependency);
                return;
            }
        }

        unresolvedDependencies.add(dependency);
    }

    /**
     * Existing and other represent the same dependency, but one of them might
     * be missing some dependencies due to Maven dependency exclusions.
     *
     * If other has more dependencies than existing, then overwrites existing's
     * dependencies with other's dependencies.
     *
     * Ideally, we would want to merge, but Aether conflict resolution does not
     * merge, so we prefer to be consistent with Aether.
     */
    private void mergeDependencies(Dependency existing, Dependency other) {
        for (Map.Entry<String, List<String>> entry : other.directDependencies.entrySet()) {
            if (!existing.directDependencies.containsKey(entry.getKey())
                    || existing.directDependencies.get(entry.getKey()).size()
                            < entry.getValue().size()) {
                existing.directDependencies.put(entry.getKey(), entry.getValue());
            }
        }

        if (existing.originalDependencies.length < other.originalDependencies.length) {
            existing.originalDependencies = other.originalDependencies;
        }
    }
}
