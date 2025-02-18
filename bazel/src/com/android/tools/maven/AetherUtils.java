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

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.validation.DefaultModelValidator;
import org.apache.maven.model.validation.ModelValidator;
import org.apache.maven.repository.internal.ArtifactDescriptorReaderDelegate;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;

/** Utility class for constructing Aether objects. */
public class AetherUtils {

    // Prevents instantiating utility class.
    private AetherUtils() {}

    public static DefaultServiceLocator newServiceLocator(boolean verbose) {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        locator.setServices(VersionRangeResolver.class, new CustomVersionRangeResolver(verbose));

        // Use a custom model builder that enables us to inject a custom model validator.
        locator.setServices(
                ModelBuilder.class,
                new DefaultModelBuilderFactory() {
                    @Override
                    protected ModelValidator newModelValidator() {
                        return new DefaultModelValidator(newModelVersionPropertiesProcessor()) {
                            @Override
                            public void validateEffectiveModel(
                                    org.apache.maven.model.Model m,
                                    ModelBuildingRequest request,
                                    ModelProblemCollector problems) {
                                // Some artifacts have models that cannot be validated by the
                                // maven-model-builder library. Here, we skip validation of those.
                                if (m.getModelVersion() == null) {
                                    // Some Maven artifacts (e.g., com.google.oboe:oboe:1.6.1) do
                                    // not put ModelVersion into their POM. The default model
                                    // validator requires it to be set to 4.0.0.
                                    return;  // Skip validation for this artifact
                                }

                                // Some artifacts have issues with the systemPath:
                                // Caused by:
                                // org.apache.maven.model.building.ModelBuildingException: ...
                                // problems were encountered while building the
                                // effective model for org.glassfish.jaxb:jaxb-runtime:2.2.11
                                // [ERROR] 'dependencyManagement.dependencies.dependency.systemPath'
                                // for com.sun:tools:jar must
                                // specify an absolute path but is ${tools.jar} @
                                DependencyManagement management = m.getDependencyManagement();
                                if (management != null) {
                                    for (Dependency dependency : management.getDependencies()) {
                                        String systemPath = dependency.getSystemPath();
                                        if (systemPath != null
                                                && systemPath.equals("${tools.jar}")) {
                                            return; // Skip validation this artifact.
                                        }
                                    }
                                }

                                // For all other artifacts, we delegate to the default model
                                // validator.
                                super.validateEffectiveModel(m, request, problems);
                            }
                        };
                    }
                }.newInstance());

        return locator;
    }

    public static RepositorySystem newRepositorySystem(DefaultServiceLocator locator) {
        // Note that, if any of the inputs transitively depend on an artifact using a version
        // range, the default version range resolver will not be able to resolve the version,
        // because our prebuilts repository does NOT have any maven-metadata.xml files.
        // In that case, we will have to write our own, custom, version range resolver,
        // then register it here.
        return checkNotNull(locator.getService(RepositorySystem.class));
    }

    public static DefaultRepositorySystemSession newSession(
            RepositorySystem system, String repoPath) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        // This is where the artifacts will be downloaded to. Since it points to our own local
        // maven repo, artifacts  that are already there will not be re-downloaded.
        session.setLocalRepositoryManager(
                system.newLocalRepositoryManager(session, new LocalRepository(repoPath)));

        session.setIgnoreArtifactDescriptorRepositories(true);
        session.setRepositoryListener(new CustomRepositoryListener(repoPath));

        // When this flag is false, conflict losers are removed from the dependency graph. E.g.,
        // even though common:28 depends on guava:28, it will not be in the dependency graph if
        // there is a conflict and guava:30 wins over guava:28.
        // When this flag is true, these nodes are kept in the dependency graph, but have a
        // special marker (NODE_DATA_WINNER).
        session.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);
        session.setConfigProperty(
                ArtifactDescriptorReaderDelegate.class.getName(),
                new HandleAarDescriptorReaderDelegate());

        return session;
    }
}
