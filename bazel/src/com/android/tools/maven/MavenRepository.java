/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tools.maven;

import com.android.tools.json.GradleMetadataJsonReader;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingResult;
import org.apache.maven.model.resolution.ModelResolver;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.graph.transformer.JavaScopeDeriver;
import org.eclipse.aether.util.graph.transformer.JavaScopeSelector;
import org.eclipse.aether.util.graph.transformer.NoopDependencyGraphTransformer;
import org.eclipse.aether.util.graph.transformer.SimpleOptionalitySelector;

/**
 * Represents a Maven repository customized such that it can:
 *
 * <ul>
 *   <li>Perform dependency resolution without conflict resolution
 *   <li>Preserve edges/nodes that would otherwise be eliminated from dependency graph during
 *       conflict resolution (transitive edges/nodes are not preserved).
 * </ul>
 */
public class MavenRepository {

    private final DefaultServiceLocator serviceLocator;
    private final RepositorySystem system;
    private final DefaultRepositorySystemSession session;
    private final List<RemoteRepository> repositories;
    private final ModelBuilder modelBuilder;

    private static final List<String> API_MODULES =
            ImmutableList.of(
                    "com.android.tools.build:gradle-api",
                    "com.android.tools.build:gradle-settings-api");

    private static final List<String> DEPS_WITHOUT_GRADLE_MODULE =
            ImmutableList.of(
                    "org.testng:testng:module:7.3.0",
                    "com.google.android.apps.common.testing.accessibility.framework:accessibility-test-framework:module:3.1.2");

    public MavenRepository(String repoPath, List<RemoteRepository> repositories, boolean verbose) {
        serviceLocator = AetherUtils.newServiceLocator(verbose);
        system = AetherUtils.newRepositorySystem(serviceLocator);
        session = AetherUtils.newSession(system, repoPath);
        this.repositories = repositories;
        modelBuilder = (ModelBuilder) serviceLocator.getService(ModelBuilder.class);
    }

    /**
     * Performs dependency resolution for the given maven coordinates.
     *
     * <p>If resolveConflicts is true, then it also performs dependency conflict resolution in
     * verbose mode, which effectively reduces the dependency graph into a dependency tree (except
     * for the effects of verbose mode, which preserves some extra information about the unresolved
     * nodes that are directly reachable by conflict winners).
     *
     * <p>If resolveConflicts is false, the dependency graph is returned as-is, without any
     * dependency conflict resolution.
     */
    public DependencyNode resolveDependencies(List<String> coords, boolean resolveConflicts)
            throws DependencyResolutionException {
        List<Dependency> deps = new ArrayList<>();
        for (String coord : coords) {
            deps.add(new Dependency(new DefaultArtifact(coord), JavaScopes.COMPILE));
        }

        if (resolveConflicts) {
            checkRequestedDependenciesAreUnique(deps);
            session.setDependencyGraphTransformer(
                    new ConflictResolver(
                            // Skip explicitly requested version check.
                            new HighestVersionSelector(Collections.emptySet()),
                            new JavaScopeSelector(),
                            new SimpleOptionalitySelector(),
                            new JavaScopeDeriver()));
        } else {
            session.setDependencyGraphTransformer(new NoopDependencyGraphTransformer());
        }

        // Collect and resolve the transitive dependencies of the given artifacts. This
        // operation is a combination of collectDependencies() and resolveArtifacts().
        DependencyRequest request =
                new DependencyRequest()
                        .setCollectRequest(
                                new CollectRequest()
                                        .setDependencies(deps)
                                        .setRepositories(repositories));
        DependencyResult result = system.resolveDependencies(session, request);

        addImportDependencies(result, resolveConflicts);

        return result.getRoot();
    }

    private static void checkRequestedDependenciesAreUnique(List<Dependency> deps) {
        Multimap<Artifact, Dependency> map = Multimaps.index(deps, dependency -> dependency.getArtifact().setVersion(""));
        if (map.keySet().size() == deps.size()) {
            return;
        }
        StringBuilder errorBuilder = new StringBuilder("Multiple coordinates that only differ by version should be in DATA, not artifacts:\n");
        map.asMap().forEach( (key, value) -> {
            if (value.size() > 1) {
                errorBuilder.append("    ").append(key.toString()).append("\n");
                for (Dependency dependency : value) {
                    errorBuilder.append("        ")
                            .append(dependency.getArtifact().toString())
                            .append("\n");
                }
            }
        });
        errorBuilder.append("\nSee https://android.googlesource.com/platform/tools/base/+/mirror-goog-studio-main/bazel/README.md#fetching-new-maven-dependencies for more information");
        throw new IllegalArgumentException(errorBuilder.toString());
    }

    /**
     * Add the scope=import dependencies into the dependency graph. These dependencies are removed
     * from the Maven models when the effective Maven model is constructed from the raw Maven model.
     */
    private void addImportDependencies(DependencyResult result, boolean resolveConflicts) {
        result.getRoot()
                .accept(new ImportDependencyVisitor(result.getRoot(), this, resolveConflicts));
    }

    private ModelBuildingResult getModelBuildingResult(Artifact artifact) {
        try {
            File pomFile = getPomFile(artifact);
            File moduleFile = getModuleFile(artifact, pomFile);
            getVariantJars(moduleFile, artifact);

            ModelBuildingRequest buildingRequest = new DefaultModelBuildingRequest();
            // Java version is determined from system properties.
            buildingRequest.setSystemProperties(System.getProperties());
            buildingRequest.setPomFile(pomFile);
            buildingRequest.setProcessPlugins(true);
            buildingRequest.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);

            ModelResolver modelResolver = newDefaultModelResolver();
            buildingRequest.setModelResolver(modelResolver);

            return modelBuilder.build(buildingRequest);
        } catch (Exception e) {
            // Rethrow as runtime exception to simplify call site. We have no intention to catch
            // these errors and handle them gracefully.
            throw new RuntimeException(e);
        }
    }

    /** Returns the maven model for the given artifact. */
    public Model getMavenModel(Artifact artifact) {
        ModelBuildingResult result = getModelBuildingResult(artifact);
        return result.getEffectiveModel();
    }

    public Model getRawMavenModel(Artifact artifact) {
        ModelBuildingResult result = getModelBuildingResult(artifact);
        return result.getRawModel();
    }

    /** Returns the pom file for the given artifact. */
    public File getPomFile(Artifact artifact) throws Exception {
        Artifact pomArtifact =
                new DefaultArtifact(
                        artifact.getGroupId(),
                        artifact.getArtifactId(),
                        "pom",
                        artifact.getVersion());
        ArtifactRequest request = new ArtifactRequest(pomArtifact, repositories, null);
        pomArtifact = system.resolveArtifact(session, request).getArtifact();
        return pomArtifact.getFile();
    }

    private File getModuleFile(Artifact artifact, File pomFile) throws Exception {

        // published-with-gradle-metadata suggests module was published with a Gradle metadata model
        String content = Files.readString(pomFile.toPath());
        if (!content.contains("published-with-gradle-metadata")) {
            return null;
        }
        Artifact moduleArtifact =
                new DefaultArtifact(
                        artifact.getGroupId(),
                        artifact.getArtifactId(),
                        "module",
                        artifact.getVersion());

        if (DEPS_WITHOUT_GRADLE_MODULE.contains(moduleArtifact.toString())) {
            return null;
        }

        try {
            ArtifactRequest request = new ArtifactRequest(moduleArtifact, repositories, null);
            moduleArtifact = system.resolveArtifact(session, request).getArtifact();
            return moduleArtifact.getFile();
        } catch (ArtifactResolutionException e) {
            System.out.println(e);
            System.out.println(
                    "Module metadata file for"
                            + artifact.toString()
                            + " does not exist. "
                            + "Consider adding it to DEPS_WITHOUT_GRADLE_MODULE in MavenRepository.java file.");
            return null;
        }
    }

    private static String removePrefix(final String s, final String prefix) {
        if (s != null && prefix != null && s.startsWith(prefix)) {
            return s.substring(prefix.length());
        }
        return s;
    }

    private static String removeSuffix(final String s, final String suffix) {
        if (s != null && suffix != null && s.endsWith(suffix)) {
            return s.substring(0, s.length() - suffix.length());
        }
        return s;
    }

    private void getVariantJars(File moduleFile, Artifact artifact) {
        try {
            // get the jars for all the variants specified in the gradle module file
            if (moduleFile != null) {
                String moduleContent = Files.readString(moduleFile.toPath());
                Set<String> urls = GradleMetadataJsonReader.readVariantUrls(moduleContent);

                // The module file does not contain coordinates for each variant's artifact
                // This code attempts to extract the classfier from the url provided
                // e.g. for url = kotlin-gradle-plugin-1.8.0-Beta-gradle71.jar
                // classifier = gradle71
                for (String url : urls) {
                    String fileExtension = url.substring(url.lastIndexOf(".") + 1);
                    String prefix = artifact.getArtifactId() + "-" + artifact.getVersion();
                    String suffix = "." + fileExtension;

                    String gradleVariant = removeSuffix(removePrefix(url, prefix), suffix);
                    // if it's the main variant or javadoc variant, skip it
                    if (gradleVariant.length() == 0 || gradleVariant.contains("javadoc")) {
                        continue;
                    }
                    // only download sources for our API modules eg.
                    // 'com.android.tools.build:gradle-api:8.1.0'
                    if (gradleVariant.contains("sources") && !isApiModule(artifact)) {
                        continue;
                    }

                    Artifact jarArtifact =
                            new DefaultArtifact(
                                    artifact.getGroupId(),
                                    artifact.getArtifactId(),
                                    gradleVariant.substring(1),
                                    fileExtension,
                                    artifact.getVersion());

                    ArtifactRequest request = new ArtifactRequest(jarArtifact, repositories, null);
                    system.resolveArtifact(session, request).getArtifact();
                }
            }
        } catch (Exception e) {
            // do nothing
        }
    }

    private boolean isApiModule(Artifact artifact) {
        String coordinates = artifact.getGroupId() + ":" + artifact.getArtifactId();
        return API_MODULES.contains(coordinates);
    }

    /** Creates and returns a new DefaultModelResolver instance. */
    private ModelResolver newDefaultModelResolver() throws Exception {
        // We use reflection to access this internal class.
        Constructor<?> constructor =
                Class.forName("org.apache.maven.repository.internal.DefaultModelResolver")
                        .getConstructors()[0];
        constructor.setAccessible(true);
        return (ModelResolver)
                constructor.newInstance(
                        session,
                        null,
                        null,
                        serviceLocator.getService(ArtifactResolver.class),
                        serviceLocator.getService(VersionRangeResolver.class),
                        serviceLocator.getService(RemoteRepositoryManager.class),
                        repositories);
    }
}
