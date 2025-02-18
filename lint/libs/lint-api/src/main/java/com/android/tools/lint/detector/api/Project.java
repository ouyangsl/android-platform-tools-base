/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tools.lint.detector.api;

import static com.android.SdkConstants.ANDROIDX_APPCOMPAT_LIB_ARTIFACT;
import static com.android.SdkConstants.ANDROID_LIBRARY;
import static com.android.SdkConstants.ANDROID_LIBRARY_REFERENCE_FORMAT;
import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT;
import static com.android.SdkConstants.ATTR_MIN_SDK_VERSION;
import static com.android.SdkConstants.ATTR_PACKAGE;
import static com.android.SdkConstants.ATTR_TARGET_SDK_VERSION;
import static com.android.SdkConstants.DOT_VERSIONS_DOT_TOML;
import static com.android.SdkConstants.FD_GRADLE;
import static com.android.SdkConstants.FD_GRADLE_WRAPPER;
import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_BUILD_GRADLE_DECLARATIVE;
import static com.android.SdkConstants.FN_BUILD_GRADLE_KTS;
import static com.android.SdkConstants.FN_GRADLE_PROPERTIES;
import static com.android.SdkConstants.FN_GRADLE_WRAPPER_PROPERTIES;
import static com.android.SdkConstants.FN_LOCAL_PROPERTIES;
import static com.android.SdkConstants.FN_PROJECT_PROGUARD_FILE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE_DECLARATIVE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE_KTS;
import static com.android.SdkConstants.OLD_PROGUARD_FILE;
import static com.android.SdkConstants.PROGUARD_CONFIG;
import static com.android.SdkConstants.PROJECT_PROPERTIES;
import static com.android.SdkConstants.TAG_USES_SDK;
import static com.android.SdkConstants.VALUE_FALSE;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.sdklib.AndroidTargetHash.PLATFORM_HASH_PREFIX;
import static java.io.File.separator;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.repository.AgpVersion;
import com.android.ide.common.repository.ResourceVisibilityLookup;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkVersionInfo;
import com.android.support.AndroidxNameUtils;
import com.android.tools.lint.client.api.CircularDependencyException;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.SdkInfo;
import com.android.tools.lint.client.api.UastParser;
import com.android.tools.lint.model.LintModelAndroidArtifact;
import com.android.tools.lint.model.LintModelAndroidLibrary;
import com.android.tools.lint.model.LintModelLibrary;
import com.android.tools.lint.model.LintModelMavenName;
import com.android.tools.lint.model.LintModelModule;
import com.android.tools.lint.model.LintModelModuleType;
import com.android.tools.lint.model.LintModelNamespacingMode;
import com.android.tools.lint.model.LintModelVariant;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Closeables;
import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.jetbrains.kotlin.config.LanguageVersionSettings;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** A project contains information about an Android project being scanned for Lint errors. */
public class Project {
    protected final LintClient client;
    protected final File dir;
    protected File referenceDir;
    private final File partialResultsDir;
    protected Configuration configuration;
    protected String pkg;
    protected Document dom;
    protected int buildSdk = -1;
    protected String buildTargetHash;
    protected IAndroidTarget target;

    protected ApiConstraint manifestMinSdks = ApiConstraint.ALL;
    protected AndroidVersion manifestMinSdk = AndroidVersion.DEFAULT;
    protected AndroidVersion manifestTargetSdk = AndroidVersion.DEFAULT;
    protected LanguageLevel javaLanguageLevel;
    protected LanguageVersionSettings kotlinLanguageLevel;

    protected boolean library;
    protected boolean externalLibrary;
    protected String name;
    protected String proguardPath;
    protected boolean mergeManifests = true;

    /** The SDK info, if any */
    protected SdkInfo sdkInfo;

    /**
     * If non null, specifies a non-empty list of specific files under this project which should be
     * checked.
     */
    protected List<File> files;

    protected List<File> proguardFiles;
    protected List<File> gradleFiles;
    protected List<File> tomlFiles;
    protected List<File> manifestFiles;
    protected List<File> javaSourceFolders;
    protected List<File> generatedSourceFolders;
    protected List<File> javaClassFolders;
    protected List<File> nonProvidedJavaLibraries;
    protected List<File> javaLibraries;
    protected Map<File, DependencyKind> klibs;
    protected List<File> testSourceFolders;
    protected List<File> instrumentationTestSourceFolders;
    protected List<File> unitTestSourceFolders;
    protected List<File> testLibraries;
    protected List<File> testFixturesSourceFolders;
    protected List<File> testFixturesLibraries;
    protected List<File> resourceFolders;
    protected List<File> generatedResourceFolders;
    protected List<File> assetFolders;
    protected List<Project> directLibraries;
    protected List<Project> allLibraries;
    protected boolean reportIssues = true;
    public Boolean gradleProject;
    protected Boolean appCompat;
    protected Boolean leanback;
    protected Set<Desugaring> desugaring;
    private Map<String, String> superClassMap;
    private ResourceVisibilityLookup resourceVisibility;
    private Document mergedManifest;
    private com.intellij.openapi.project.Project ideaProject;
    private Map<Object, Object> clientProperties;

    private CoreApplicationEnvironment env;

    /**
     * Creates a new {@link Project} for the given directory.
     *
     * @param client the tool running the lint check
     * @param dir the root directory of the project
     * @param referenceDir See {@link #getReferenceDir()}.
     * @return a new {@link Project}
     */
    @NonNull
    public static Project create(
            @NonNull LintClient client, @NonNull File dir, @NonNull File referenceDir) {
        return new Project(client, dir, referenceDir);
    }

    /**
     * Returns true if this project is a Gradle-based Android project
     *
     * @return true if this is a Gradle-based project
     */
    public boolean isGradleProject() {
        if (gradleProject == null) {
            gradleProject = client.isGradleProject(this);
        }

        return gradleProject;
    }

    /**
     * Returns true if this project is an Android project.
     *
     * @return true if this project is an Android project.
     */
    public boolean isAndroidProject() {
        return true;
    }

    /**
     * Returns the project model for this project if supported by the build system.
     *
     * @return the project model, or null
     */
    @Nullable
    public LintModelModule getBuildModule() {
        LintModelVariant variant = getBuildVariant();
        if (variant != null) {
            return variant.getModule();
        }
        return null;
    }

    /**
     * Returns the current selected variant, if any.
     *
     * <p>This can be used by incremental lint rules to warn about problems in the current context.
     * Lint rules should however strive to perform cross variant analysis and warn about problems in
     * any configuration.
     *
     * @return the selected variant, or null
     */
    @Nullable
    public LintModelVariant getBuildVariant() {
        return null;
    }

    /**
     * Returns the project model for this project if it corresponds to a library with a lint model.
     *
     * @return the library model, or null
     */
    @Nullable
    public LintModelAndroidLibrary getBuildLibraryModel() {
        return null;
    }

    /** Returns the set of desugaring operations in effect for this project. */
    public Set<Desugaring> getDesugaring() {
        if (desugaring == null) {
            desugaring = client.getDesugaring(this);
        }
        return desugaring;
    }

    /** Returns true if the given desugaring operation is in effect for this project. */
    public boolean isDesugaring(Desugaring type) {
        return getDesugaring().contains(type);
    }

    public boolean isCoreLibraryDesugaringEnabled() {
        LintModelVariant variant = getBuildVariant();
        return variant != null && variant.getBuildFeatures().getCoreLibraryDesugaringEnabled();
    }

    /**
     * Associate the given key and value data with this project. Used to store project specific
     * state without introducing external caching.
     */
    public void putClientProperty(@NonNull Object key, @Nullable Object value) {
        if (clientProperties == null) {
            clientProperties = new HashMap<>();
        }
        if (value != null) {
            clientProperties.put(key, value);
        } else {
            clientProperties.remove(key);
        }
    }

    /**
     * Retrieve the given key and value data associated with this project. Used to store project
     * specific state without introducing external caching.
     */
    @Nullable
    public <T> T getClientProperty(@NonNull Object key) {
        if (clientProperties == null) {
            return null;
        }
        //noinspection unchecked
        return (T) clientProperties.get(key);
    }

    /** Returns the corresponding IDE project. */
    @Nullable
    public com.intellij.openapi.project.Project getIdeaProject() {
        return ideaProject;
    }

    public void setIdeaProject(@Nullable com.intellij.openapi.project.Project ideaProject) {
        this.ideaProject = ideaProject;
    }

    /**
     * If this is a Gradle project with a valid Gradle model, return the version of the
     * model/plugin.
     *
     * @return the Gradle plugin version, or null if invalid or not a Gradle project
     */
    @Nullable
    public AgpVersion getGradleModelVersion() {
        LintModelModule gradleProjectModel = getBuildModule();
        if (gradleProjectModel != null) {
            return gradleProjectModel.getAgpVersion();
        } else {
            return null;
        }
    }

    /**
     * Returns the merged manifest of this project. Warning: context.project.mergedManifest will
     * only give you the merged manifest of the current module being analyzed, which could be a
     * library module. If you want the merged manifest of the final app module, you should use
     * conditional incidents or partial results, and call context.mainProject.mergedManifest. Note
     * that the file reference in the merged manifest isn't accurate; the merged manifest
     * accumulates information from a wide variety of locations.
     *
     * @return The merged manifest, if available.
     */
    @Nullable
    public Document getMergedManifest() {
        if (mergedManifest == null) {
            mergedManifest = client.getMergedManifest(this);
        }

        return mergedManifest;
    }

    /** Creates a new Project. Use one of the factory methods to create. */
    protected Project(
            @NonNull LintClient client,
            @NonNull File dir,
            @NonNull File referenceDir,
            File partialResultsDir) {
        this.client = client;
        this.dir = dir;
        this.referenceDir = referenceDir;
        this.partialResultsDir = partialResultsDir;
        initialize();
    }

    /** Creates a new Project. Use one of the factory methods to create. */
    protected Project(@NonNull LintClient client, @NonNull File dir, @NonNull File referenceDir) {
        this(client, dir, referenceDir, null);
    }

    protected void initialize() {
        // Default initialization: Use ADT/ant style project.properties file
        try {
            // Read properties file and initialize library state
            Properties properties = new Properties();
            File propFile = new File(dir, PROJECT_PROPERTIES);
            if (propFile.exists()) {
                BufferedInputStream is = new BufferedInputStream(new FileInputStream(propFile));
                try {
                    properties.load(is);
                    String value = properties.getProperty(ANDROID_LIBRARY);
                    library = VALUE_TRUE.equals(value);
                    String proguardPath = properties.getProperty(PROGUARD_CONFIG);
                    if (proguardPath != null) {
                        this.proguardPath = proguardPath;
                    }
                    mergeManifests =
                            !VALUE_FALSE.equals(properties.getProperty("manifestmerger.enabled"));
                    String target = properties.getProperty("target");
                    if (target != null) {
                        setBuildTargetHash(target);
                    }

                    if (VALUE_TRUE.equals(properties.getProperty("android_java8_libs"))) {
                        desugaring = Desugaring.FULL;
                    }

                    for (int i = 1; i < 1000; i++) {
                        String key = String.format(Locale.US, ANDROID_LIBRARY_REFERENCE_FORMAT, i);
                        String library = properties.getProperty(key);
                        if (library == null || library.isEmpty()) {
                            // No holes in the numbering sequence is allowed
                            break;
                        }

                        File libraryDir = new File(dir, library).getCanonicalFile();

                        if (directLibraries == null) {
                            directLibraries = new ArrayList<>();
                        }

                        // Adjust the reference dir to be a proper prefix path of the
                        // library dir
                        File libraryReferenceDir = referenceDir;
                        if (!libraryDir.getPath().startsWith(referenceDir.getPath())) {
                            // Symlinks etc might have been resolved, so do those to
                            // the reference dir as well
                            libraryReferenceDir = libraryReferenceDir.getCanonicalFile();
                            if (!libraryDir.getPath().startsWith(referenceDir.getPath())) {
                                File file = libraryReferenceDir;
                                while (file != null && !file.getPath().isEmpty()) {
                                    if (libraryDir.getPath().startsWith(file.getPath())) {
                                        libraryReferenceDir = file;
                                        break;
                                    }
                                    file = file.getParentFile();
                                }
                            }
                        }

                        try {
                            Project libraryPrj = client.getProject(libraryDir, libraryReferenceDir);
                            directLibraries.add(libraryPrj);
                            // By default, we don't report issues in inferred library projects.
                            // The driver will set report = true for those library explicitly
                            // requested.
                            libraryPrj.setReportIssues(false);
                        } catch (CircularDependencyException e) {
                            e.setProject(this);
                            e.setLocation(Location.create(propFile));
                            throw e;
                        }
                    }
                } finally {
                    try {
                        Closeables.close(is, true /* swallowIOException */);
                    } catch (IOException e) {
                        // cannot happen
                    }
                }
            }
        } catch (IOException ioe) {
            client.log(ioe, "Initializing project state");
        }

        if (directLibraries != null) {
            directLibraries = Collections.unmodifiableList(directLibraries);
        } else {
            directLibraries = Collections.emptyList();
        }
    }

    /** Gets the namespacing mode used for this project */
    @NonNull
    private LintModelNamespacingMode getNamespacingMode() {
        LintModelVariant variant = getBuildVariant();
        if (variant != null) {
            return variant.getBuildFeatures().getNamespacingMode();
        } else {
            return LintModelNamespacingMode.DISABLED;
        }
    }

    private ResourceNamespace namespace;

    /** Returns the namespace for resources in this module/project */
    @NonNull
    public ResourceNamespace getResourceNamespace() {
        if (namespace == null) {
            String packageName = getPackage();
            if (packageName == null || getNamespacingMode() == LintModelNamespacingMode.DISABLED) {
                namespace = ResourceNamespace.RES_AUTO;
            } else {
                namespace = ResourceNamespace.fromPackageName(packageName);
            }
        }

        return namespace;
    }

    @Override
    public String toString() {
        return "Project [dir=" + dir + ']';
    }

    @Override
    public int hashCode() {
        return dir.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Project other = (Project) obj;
        //noinspection FileComparisons
        return dir.equals(other.dir);
    }

    /**
     * Adds the given file to the list of files which should be checked in this project. If no files
     * are added, the whole project will be checked.
     *
     * @param file the file to be checked
     */
    public void addFile(@NonNull File file) {
        if (files == null) {
            files = new ArrayList<>();
        }
        files.add(file);
    }

    /**
     * The list of files to be checked in this project. If null, the whole project should be
     * checked.
     *
     * @return the subset of files to be checked, or null for the whole project
     */
    @Nullable
    public List<File> getSubset() {
        return files;
    }

    /**
     * Returns the {@link UastParser.UastSourceList} for this project if the project provides a
     * specific definition for it; if not, lint will iterate the project folders on its own to
     * discover the source list.
     */
    @Nullable
    public UastParser.UastSourceList getUastSourceList(
            @NonNull LintDriver driver, @Nullable Project main) {
        return null;
    }

    /**
     * Returns the list of source folders for Java and Kotlin source files
     *
     * @return a list of source folders to search for .java and .kt files
     */
    @NonNull
    public List<File> getJavaSourceFolders() {
        if (javaSourceFolders == null) {
            javaSourceFolders = client.getJavaSourceFolders(this);
        }

        return javaSourceFolders;
    }

    @NonNull
    public List<File> getGeneratedSourceFolders() {
        if (generatedSourceFolders == null) {
            generatedSourceFolders = client.getGeneratedSourceFolders(this);
        }

        return generatedSourceFolders;
    }

    /**
     * Returns the list of output folders or jars for class files.
     *
     * @return a list of output folders or jars to search for .class files
     */
    @NonNull
    public List<File> getJavaClassFolders() {
        if (javaClassFolders == null) {
            javaClassFolders = client.getJavaClassFolders(this);
        }
        return javaClassFolders;
    }

    /**
     * Returns the list of Java libraries (typically .jar files) that this project depends on.
     *
     * @return a list of .jar files (or class folders) that this project depends on.
     */
    @NonNull
    public List<File> getJavaLibraries() {
        return getJavaLibraries(true);
    }

    /**
     * Returns the list of Java libraries (typically .jar files) that this project depends on. Note
     * that this refers to jar libraries, not Android library projects which are processed in a
     * separate pass with their own source and class folders.
     *
     * @param includeProvided If true, included provided libraries too (libraries that are not
     *     packaged with the app, but are provided for compilation purposes and are assumed to be
     *     present in the running environment)
     * @return a list of .jar files (or class folders) that this project depends on.
     */
    @NonNull
    public List<File> getJavaLibraries(boolean includeProvided) {
        if (includeProvided) {
            if (javaLibraries == null) {
                javaLibraries = client.getJavaLibraries(this, true);
            }
            return javaLibraries;
        } else {
            if (nonProvidedJavaLibraries == null) {
                nonProvidedJavaLibraries = client.getJavaLibraries(this, false);
            }
            return nonProvidedJavaLibraries;
        }
    }

    /** Returns the list of klibs that this project depends on. */
    @NonNull
    public Map<File, DependencyKind> getKlibs() {
        if (klibs == null) {
            klibs = new LinkedHashMap<>();
            for (var klib : client.getKlibs(this)) {
                klibs.put(klib, /* TODO ok? */ DependencyKind.Regular);
            }
        }
        return klibs;
    }

    /**
     * Returns the list of source folders for Java test source files
     *
     * @return a list of source folders to search for .java files
     */
    @NonNull
    public List<File> getTestSourceFolders() {
        if (testSourceFolders == null) {
            testSourceFolders = client.getTestSourceFolders(this);
        }

        return testSourceFolders;
    }

    @NonNull
    public List<File> getUnitTestSourceFolders() {
        return Collections.emptyList();
    }

    @NonNull
    public List<File> getInstrumentationTestSourceFolders() {
        return Collections.emptyList();
    }

    /**
     * Returns the list of Java libraries (typically .jar files) that the tests depend on.
     *
     * @return a list of .jar files (or class folders) that the tests depends on.
     */
    @NonNull
    public List<File> getTestLibraries() {
        if (testLibraries == null) {
            testLibraries = client.getTestLibraries(this);
        }

        return testLibraries;
    }

    @NonNull
    public List<File> getTestFixturesSourceFolders() {
        return Collections.emptyList();
    }

    @NonNull
    public List<File> getTestFixturesLibraries() {
        return Collections.emptyList();
    }

    /**
     * Returns the resource folders.
     *
     * @return a list of files pointing to the resource folders, which might be empty if the project
     *     does not provide any resources.
     */
    @NonNull
    public List<File> getResourceFolders() {
        if (resourceFolders == null) {
            resourceFolders = client.getResourceFolders(this);
        }

        return resourceFolders;
    }

    @NonNull
    public List<File> getGeneratedResourceFolders() {
        if (generatedResourceFolders == null) {
            generatedResourceFolders = client.getGeneratedResourceFolders(this);
        }

        return generatedResourceFolders;
    }

    /**
     * Returns the asset folders.
     *
     * @return a list of files pointing to the asset folders, which might be empty if the project
     *     does not provide any resources.
     */
    @NonNull
    public List<File> getAssetFolders() {
        if (assetFolders == null) {
            assetFolders = client.getAssetFolders(this);
        }

        return assetFolders;
    }

    /**
     * Returns the relative path of a given file relative to the user specified directory (which is
     * often the project directory but sometimes a higher up directory when a directory tree is
     * being scanned
     *
     * @param file the file under this project to check
     * @return the path relative to the reference directory (often the project directory)
     */
    @NonNull
    public String getDisplayPath(@NonNull File file) {
        return client.getDisplayPath(file, this, TextFormat.TEXT);
    }

    /**
     * Returns the relative path of a given file within the current project.
     *
     * @param file the file under this project to check
     * @return the path relative to the project
     */
    @NonNull
    public String getRelativePath(@NonNull File file) {
        return getRelativePath(dir, file);
    }

    /**
     * Returns the relative path of a given file within the given root directory.
     *
     * @param root the root/base folder
     * @param file the file under the root folder to check
     * @return the path relative to the project
     */
    @NonNull
    public static String getRelativePath(@Nullable File root, @NonNull File file) {
        String path = file.getPath();
        if (root == null) {
            return path;
        }
        String referencePath = root.getPath();
        if (path.startsWith(referencePath)) {
            int length = referencePath.length();
            if (path.length() > length && path.charAt(length) == File.separatorChar) {
                length++;
            }

            return path.substring(length);
        }

        return path;
    }

    /**
     * Returns the project root directory
     *
     * @return the dir
     */
    @NonNull
    public File getDir() {
        return dir;
    }

    /**
     * Returns the original user supplied directory where the lint search started. For example, if
     * you run lint against {@code /tmp/foo}, and it finds a project to lint in {@code
     * /tmp/foo/dev/src/project1}, then the {@code dir} is {@code /tmp/foo/dev/src/project1} and the
     * {@code referenceDir} is {@code /tmp/foo/}.
     *
     * @return the reference directory, never null
     */
    @NonNull
    public File getReferenceDir() {
        return referenceDir;
    }

    /**
     * Returns the partial results directory for the project, or null if unset. All per-project
     * serialized files will be written to this directory, if set (not just the partial results
     * file). Note that Gradle build variants for a project may provide a more specific partial
     * results directory.
     *
     * @return the partial results directory for the project, or null if unset
     */
    @Nullable
    public File getPartialResultsDir() {
        return partialResultsDir;
    }

    /**
     * Gets the configuration associated with this project
     *
     * @param driver the current driver, if any
     * @return the configuration associated with this project
     */
    @NonNull
    public Configuration getConfiguration(@Nullable LintDriver driver) {
        if (configuration == null) {
            configuration = client.getConfiguration(this, driver);
            configuration.setFileLevel(false);
        }
        return configuration;
    }

    /**
     * Returns the application package specified by the manifest. In Gradle projects, this
     * corresponds to the `namespace` declaration. If you want the application ID (or the package
     * generated in the merged manifest), use {@link #getApplicationId()} instead.
     *
     * @return the application package, or null if unknown
     */
    @Nullable
    public String getPackage() {
        return pkg;
    }

    /**
     * Returns the application id, if known
     *
     * @return the application id, if known
     */
    @Nullable
    public String getApplicationId() {
        LintModelVariant variant = getBuildVariant();
        if (variant != null && variant.getArtifact() instanceof LintModelAndroidArtifact) {
            return ((LintModelAndroidArtifact) variant.getArtifact()).getApplicationId();
        }

        return getPackage();
    }

    /**
     * Returns the minimum API level for the project. This does not include any SDK extensions the
     * user has declared; for that, use {@link #getMinSdkVersions()}} instead.
     *
     * @return the minimum API level or {@link AndroidVersion#DEFAULT} if unknown
     * @see #getMinSdkVersions() to get a vector including SDK extensions
     */
    @NonNull
    public AndroidVersion getMinSdkVersion() {
        return manifestMinSdk == null ? AndroidVersion.DEFAULT : manifestMinSdk;
    }

    /**
     * Returns the minimum API levels for the project (the `minSdkVersion`, along with any SDK
     * extensions required to be present.)
     *
     * @return the minimum API level or {@link ApiConstraint#UNKNOWN} if unknown
     */
    @NonNull
    public ApiConstraint getMinSdkVersions() {
        return manifestMinSdks;
    }

    /**
     * Returns the minimum API <b>level</b> requested by the manifest, or -1 if not specified. Use
     * {@link #getMinSdkVersion()} to get a full version if you need to check if the platform is a
     * preview platform etc.
     *
     * @return the minimum API level or -1 if unknown
     */
    public int getMinSdk() {
        AndroidVersion version = getMinSdkVersion();
        return version == AndroidVersion.DEFAULT ? -1 : version.getApiLevel();
    }

    /**
     * Returns the target API level for the project
     *
     * @return the target API level or {@link AndroidVersion#DEFAULT} if unknown
     */
    @NonNull
    public AndroidVersion getTargetSdkVersion() {
        return manifestTargetSdk == AndroidVersion.DEFAULT ? getMinSdkVersion() : manifestTargetSdk;
    }

    /** Returns the expected language level for Java source files in this project */
    @NonNull
    public LanguageLevel getJavaLanguageLevel() {
        if (javaLanguageLevel == null) {
            javaLanguageLevel = client.getJavaLanguageLevel(this);
        }

        return javaLanguageLevel;
    }

    /** Sets the expected language level for Kotlin source files in this project */
    public void setKotlinLanguageLevel(@NonNull LanguageVersionSettings kotlinLanguageLevel) {
        this.kotlinLanguageLevel = kotlinLanguageLevel;
    }

    /** Returns the expected language level for Kotlin source files in this project */
    @NonNull
    public LanguageVersionSettings getKotlinLanguageLevel() {
        if (kotlinLanguageLevel == null) {
            kotlinLanguageLevel = client.getKotlinLanguageLevel(this);
        }

        return kotlinLanguageLevel;
    }

    /**
     * Returns the target API <b>level</b> specified by the manifest, or -1 if not specified. Use
     * {@link #getTargetSdkVersion()} to get a full version if you need to check if the platform is
     * a preview platform etc.
     *
     * @return the target API level or -1 if unknown
     */
    public int getTargetSdk() {
        AndroidVersion version = getTargetSdkVersion();
        return version == AndroidVersion.DEFAULT ? -1 : version.getApiLevel();
    }

    /**
     * Returns the compile SDK version used to build the project, or null if not known. This is the
     * string name of the compileSdkVersion. If you want the numeric API level, use {@link
     * #getBuildTargetHash()} instead, or to get the actual
     *
     * @return the compileSdkVersion or -1 if unknown
     */
    public int getBuildSdk() {
        return buildSdk;
    }

    /**
     * Returns the target API used to build the project, or null if not known. Note that this is
     * returning a String rather than a {@link AndroidVersion} since it may refer to either a {@link
     * AndroidTargetHash} for a platform or for an add-on, and {@link AndroidVersion} can only
     * express platform versions.
     *
     * @return the build target API or null if unknown
     */
    @Nullable
    public String getBuildTargetHash() {
        return buildTargetHash;
    }

    /**
     * Sets the build target hash to be used for this project. This is only intended for lint
     * internal usage.
     *
     * @param buildTargetHash the target hash
     */
    public void setBuildTargetHash(String buildTargetHash) {
        this.buildTargetHash = buildTargetHash;

        AndroidVersion version = AndroidTargetHash.getPlatformVersion(buildTargetHash);
        if (version != null) {
            buildSdk = version.getFeatureLevel();
        } else {
            // The platform sometimes passes in the wrong target hash; try to account for that
            if (buildTargetHash.indexOf('-') == -1) {
                version =
                        AndroidTargetHash.getPlatformVersion(
                                PLATFORM_HASH_PREFIX + buildTargetHash);
            }
            if (version == null) {
                client.log(
                        Severity.WARNING,
                        null,
                        "Unexpected build target format: %1$s",
                        buildTargetHash);
            }
        }
    }

    /**
     * Returns the target used to build the project, or null if not known
     *
     * @return the build target, or null
     */
    @Nullable
    public IAndroidTarget getBuildTarget() {
        if (target == null) {
            target = client.getCompileTarget(this);
        }

        return target;
    }

    /**
     * Returns the manifest document that the project metadata like {@link #manifestMinSdk} was
     * initially read from.
     */
    @Nullable
    public Document getManifestDom() {
        return dom;
    }

    /**
     * Initialized the manifest state from the given manifest model
     *
     * @param document the DOM document for the manifest XML document
     */
    public void readManifest(@NonNull Document document) {
        dom = document;
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        if (pkg == null) {
            // if we read multiple manifests for a project, only look at the first one.
            // (This helps when there are feature modules inlined into the main project
            // with their own packages.)
            String packageAttribute = root.getAttribute(ATTR_PACKAGE);
            if (!"".equals(packageAttribute)) {
                pkg = packageAttribute;
            }
        }

        // Initialize minSdk and targetSdk
        NodeList usesSdks = root.getElementsByTagName(TAG_USES_SDK);
        if (usesSdks.getLength() > 0) {
            Element element = (Element) usesSdks.item(0);

            String minSdk = null;
            if (element.hasAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION)) {
                minSdk = element.getAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION);
            }
            if (minSdk != null && !minSdk.isEmpty()) {
                // If the minSdk version is a number we don't need to look up
                // SDK targets to resolve code names (and computing the target list
                // is expensive)
                AndroidVersion version = SdkVersionInfo.getVersion(minSdk, null);
                if (version == null) {
                    version = AndroidVersion.DEFAULT;
                }
                manifestMinSdk = version;
            }

            if (element.hasAttributeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION)) {
                String targetSdk = element.getAttributeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION);
                if (!targetSdk.isEmpty()) {
                    AndroidVersion version = SdkVersionInfo.getVersion(targetSdk, null);
                    if (version == null) {
                        version = AndroidVersion.DEFAULT;
                    }
                    manifestTargetSdk = version;
                }
            } else {
                manifestTargetSdk = manifestMinSdk;
            }

            manifestMinSdks = ApiConstraint.Companion.getFromUsesSdk(element);
            if (manifestMinSdks == null) {
                manifestMinSdks = ApiConstraint.ALL;
            }
        }
    }

    /**
     * Returns true if this project is an Android library project
     *
     * @return true if this project is an Android library project
     */
    public boolean isLibrary() {
        return library;
    }

    /**
     * Returns true if this project is an external library (typically an AAR library), as opposed to
     * a local library we have source for
     *
     * @return true if this is an external library
     */
    public boolean isExternalLibrary() {
        return externalLibrary;
    }

    protected LintModelMavenName mavenCoordinates = null;

    /**
     * Returns the Maven coordinate of this project, if known.
     *
     * @return the maven coordinate, or null
     */
    @Nullable
    public LintModelMavenName getMavenCoordinate() {
        if (mavenCoordinates == null) {
            LintModelModule model = getBuildModule();
            if (model != null) {
                mavenCoordinates = model.getMavenName();
            }
        }
        return mavenCoordinates;
    }

    /**
     * Returns the list of library projects referenced by this project
     *
     * @return the list of library projects referenced by this project, never null
     */
    @NonNull
    public List<Project> getDirectLibraries() {
        return directLibraries != null ? directLibraries : Collections.emptyList();
    }

    /**
     * Sets the list of library projects referenced by this project. This should only be set during
     * project initialization.
     *
     * @param libraries the new library list.
     */
    public void setDirectLibraries(@NonNull List<Project> libraries) {
        directLibraries = libraries;
    }

    /**
     * Returns the transitive closure of the library projects for this project
     *
     * @return the transitive closure of the library projects for this project
     */
    @NonNull
    public List<Project> getAllLibraries() {
        if (allLibraries == null) {
            if (directLibraries.isEmpty()) {
                allLibraries = Collections.emptyList();
                return allLibraries;
            }

            List<Project> all = new ArrayList<>();
            Set<Project> seen = Sets.newHashSet();
            Set<Project> path = Sets.newHashSet();
            seen.add(this);
            path.add(this);
            addLibraryProjects(all, seen, path);
            allLibraries = all;
        }

        return allLibraries;
    }

    /**
     * Adds this project's library project and their library projects recursively into the given
     * collection of projects
     *
     * @param collection the collection to add the projects into
     * @param seen full set of projects we've processed
     * @param path the current path of library dependencies followed
     */
    private void addLibraryProjects(
            @NonNull Collection<Project> collection,
            @NonNull Set<Project> seen,
            @NonNull Set<Project> path) {
        for (Project library : directLibraries) {
            if (seen.contains(library)) {
                if (path.contains(library)) {
                    client.log(
                            Severity.WARNING,
                            null,
                            "Internal lint error: cyclic library dependency for %1$s",
                            library);
                }
                continue;
            }
            collection.add(library);
            seen.add(library);
            path.add(library);
            // Recurse
            library.addLibraryProjects(collection, seen, path);
            path.remove(library);
        }
    }

    /** The type of artifact produced by this Android project. */
    @NonNull
    public LintModelModuleType getType() {
        LintModelModule buildModule = getBuildModule();
        if (buildModule != null) {
            return buildModule.getType();
        }
        if (isLibrary()) {
            return LintModelModuleType.LIBRARY;
        } else {
            return LintModelModuleType.APP;
        }
    }

    /**
     * Whether this project is a base application with dynamic features.
     *
     * @return true if this is an application project that has any dynamic features, false in all
     *     other cases.
     */
    public boolean hasDynamicFeatures() {
        return false;
    }

    /**
     * Gets the SDK info for the current project.
     *
     * @return the SDK info for the current project, never null
     */
    @NonNull
    public SdkInfo getSdkInfo() {
        if (sdkInfo == null) {
            sdkInfo = client.getSdkInfo(this);
        }

        return sdkInfo;
    }

    /**
     * Gets the paths to the manifest files in this project, if any exists. The manifests should be
     * provided such that the main manifest comes first, then any flavor versions, then any build
     * types.
     *
     * @return the path to the manifest file, or null if it does not exist
     */
    @NonNull
    public List<File> getManifestFiles() {
        if (manifestFiles == null) {
            File manifestFile = new File(dir, ANDROID_MANIFEST_XML);
            if (manifestFile.exists()) {
                manifestFiles = Collections.singletonList(manifestFile);
            } else {
                manifestFiles = Collections.emptyList();
            }
        }

        return manifestFiles;
    }

    /**
     * Returns the proguard files configured for this project, if any
     *
     * @return the proguard files, if any
     */
    @NonNull
    public List<File> getProguardFiles() {
        if (proguardFiles == null) {
            List<File> files = new ArrayList<>();
            if (proguardPath != null) {
                Splitter splitter = Splitter.on(CharMatcher.anyOf(":;"));
                for (String path : splitter.split(proguardPath)) {
                    if (path.contains("${")) {
                        // Don't analyze the global/user proguard files
                        continue;
                    }
                    File file = new File(path);
                    if (!file.isAbsolute()) {
                        file = new File(getDir(), path);
                    }
                    if (file.exists()) {
                        files.add(file);
                    }
                }
            }
            if (files.isEmpty()) {
                File file = new File(getDir(), OLD_PROGUARD_FILE);
                if (file.exists()) {
                    files.add(file);
                }
                file = new File(getDir(), FN_PROJECT_PROGUARD_FILE);
                if (file.exists()) {
                    files.add(file);
                }
            }
            proguardFiles = files;
        }
        return proguardFiles;
    }

    /** Returns the .properties files to be analyzed in the project */
    @NonNull
    public List<File> getPropertyFiles() {
        List<File> propertyFiles = new ArrayList<>(2);
        File local = new File(dir, FN_LOCAL_PROPERTIES);
        if (local.isFile()) {
            propertyFiles.add(local);
        }
        File gradle = new File(dir, FN_GRADLE_PROPERTIES);
        if (gradle.isFile()) {
            propertyFiles.add(gradle);
        }
        File wrapper = new File(dir, FD_GRADLE_WRAPPER + separator + FN_GRADLE_WRAPPER_PROPERTIES);
        if (wrapper.isFile()) {
            propertyFiles.add(wrapper);
        }
        return propertyFiles;
    }

    /**
     * Returns the Gradle build script files configured for this project, if any
     *
     * @return the Gradle files, if any
     */
    @NonNull
    public List<File> getGradleBuildScripts() {
        if (gradleFiles == null) {
            if (isGradleProject()) {
                gradleFiles = Lists.newArrayListWithExpectedSize(2);
                File build = new File(dir, FN_BUILD_GRADLE);
                if (build.exists()) {
                    gradleFiles.add(build);
                }
                build = new File(dir, FN_BUILD_GRADLE_KTS);
                if (build.exists()) {
                    gradleFiles.add(build);
                }
                build = new File(dir, FN_BUILD_GRADLE_DECLARATIVE);
                if (build.exists()) {
                    gradleFiles.add(build);
                }
                File settings = new File(dir, FN_SETTINGS_GRADLE);
                if (settings.exists()) {
                    gradleFiles.add(settings);
                }
                settings = new File(dir, FN_SETTINGS_GRADLE_KTS);
                if (settings.exists()) {
                    gradleFiles.add(settings);
                }
                settings = new File(dir, FN_SETTINGS_GRADLE_DECLARATIVE);
                if (settings.exists()) {
                    gradleFiles.add(settings);
                }
            } else {
                gradleFiles = Collections.emptyList();
            }
        }

        return gradleFiles;
    }

    @NonNull
    public List<File> getTomlFiles() {
        if (tomlFiles == null) {
            if (isGradleProject()) {
                // Gradle version catalogs? These files don't belong to any one module (in fact
                // they sit outside the individual modules), and we don't want to just go
                // and include ../gradle from the projects since that means we'd repeat the
                // same TOML warnings for each project. Instead, we process them here, but just
                // once.
                tomlFiles = new ArrayList<>(3);
                File rootDir = client.getRootDir();
                if (rootDir != null && isDesignatedTomlModule(rootDir)) {
                    File gradle = new File(rootDir, FD_GRADLE);
                    if (gradle.isDirectory()) {
                        File[] catalogs = gradle.listFiles();
                        if (catalogs != null) {
                            for (File catalog : catalogs) {
                                if (catalog.getPath().endsWith(DOT_VERSIONS_DOT_TOML)) {
                                    tomlFiles.add(catalog);
                                }
                            }
                        }
                    }
                }
            } else {
                tomlFiles = Collections.emptyList();
            }
        }

        return tomlFiles;
    }

    /**
     * TOML files aren't actually part of this project; we went looking outside (from the root
     * project). This means that we'd potentially end up repeating analysis (and reporting) on the
     * same files over and over, from each project. We should only do this once. For now, we're
     * assigning the responsibility to the first module alphabetically in the root directory.
     *
     * <p>(**This is a workaround until the catalog files are provided from the lint model
     * instead.**)
     */
    private boolean isDesignatedTomlModule(File root) {
        File[] moduleDirs = root.listFiles();
        if (moduleDirs != null) {
            Arrays.sort(moduleDirs);
            for (File moduleDir : moduleDirs) {
                if (new File(moduleDir, FN_BUILD_GRADLE).exists()
                        || new File(moduleDir, FN_BUILD_GRADLE_KTS).exists()
                        || new File(moduleDir, FN_BUILD_GRADLE_DECLARATIVE).exists()) {
                    return dir.getPath().equalsIgnoreCase(moduleDir.getPath());
                }
            }
        }
        // No directories match, just fall back to assigning the responsibility to the app module;
        // there's usually exactly one.
        return getType() == LintModelModuleType.APP;
    }

    /**
     * Returns the name of the project
     *
     * @return the name of the project, never null
     */
    @NonNull
    public String getName() {
        if (name == null) {
            name = client.getProjectName(this);
        }

        return name;
    }

    /**
     * Sets the name of the project
     *
     * @param name the name of the project, never null
     */
    public void setName(@NonNull String name) {
        assert !name.isEmpty();
        this.name = name;
    }

    /**
     * Sets whether lint should report issues in this project. See {@link #getReportIssues()} for a
     * full description of what that means.
     *
     * @param reportIssues whether lint should report issues in this project
     */
    public void setReportIssues(boolean reportIssues) {
        this.reportIssues = reportIssues;
    }

    /**
     * Returns whether lint should report issues in this project.
     *
     * <p>If a user specifies a project and its library projects for analysis, then those library
     * projects are all "included", and all errors found in all the projects are reported. But if
     * the user is only running lint on the main project, we shouldn't report errors in any of the
     * library projects. We still need to <b>consider</b> them for certain types of checks, such as
     * determining whether resources found in the main project are unused, so the detectors must
     * still get a chance to look at these projects. The {@code #getReportIssues()} attribute is
     * used for this purpose.
     *
     * @return whether lint should report issues in this project
     */
    public boolean getReportIssues() {
        return reportIssues;
    }

    /**
     * Returns whether manifest merging is in effect
     *
     * @return true if manifests in library projects should be merged into main projects
     */
    public boolean isMergingManifests() {
        return mergeManifests;
    }

    /**
     * Returns true if this project depends on the given artifact. Note that the project doesn't
     * have to be a Gradle project; the artifact is just an identifier for name a specific library,
     * such as com.android.support:support-v4 to identify the support library.
     *
     * <p>Note that for an AndroidX library, this method will treat both the pre-AndroidX and the
     * post-AndroidX package migration as the same, so this method cannot be used to distinguish
     * between depending on an old support library artifact or the corresponding new AndroidX
     * artifact.
     *
     * @param artifact the Gradle/Maven name of a library
     * @return true if the library is installed, false if it is not, and null if we're not sure
     */
    @Nullable
    public Boolean dependsOn(@NonNull String artifact) {
        artifact = AndroidxNameUtils.getCoordinateMapping(artifact);

        if (ANDROIDX_APPCOMPAT_LIB_ARTIFACT.equals(artifact)
                || APPCOMPAT_LIB_ARTIFACT.equals(artifact)) {
            if (appCompat == null) {
                UastParser parser = client.getUastParser(this);
                // Using classpath is most accurate, but isn't available until parsing services have
                // been initialized
                if (parser.getPrepared()) {
                    JavaEvaluator evaluator = parser.getEvaluator();
                    PsiClass activity =
                            evaluator.findClass("androidx.appcompat.app.AppCompatActivity");
                    if (activity != null) {
                        appCompat = true;
                    } else {
                        activity = evaluator.findClass("android.support.v7.app.AppCompatActivity");
                        appCompat = activity != null;
                    }
                } else {
                    for (File file : getJavaLibraries(true)) {
                        String name = file.getName();
                        if (name.startsWith("appcompat")
                                || name.equals("android-support-v4.jar")
                                || name.startsWith("support-v4-")
                                || name.startsWith("legacy-support-")) {
                            // Not cached; we want accurate result from code lookup once it's
                            // available: appCompat = true; break;
                            return true;
                        }
                    }
                    for (Project dependency : getDirectLibraries()) {
                        Boolean b = dependency.dependsOn(artifact);
                        if (b != null && b) {
                            return true;
                        }
                    }
                    return false;
                }
            }

            return appCompat;
        }

        return null;
    }

    private List<String> mCachedApplicableDensities;

    /**
     * Returns the set of applicable densities for this project. If null, there are no density
     * restrictions and all densities apply.
     *
     * @return the list of specific densities that apply in this project, or null if all densities
     *     apply
     */
    @Nullable
    public List<String> getApplicableDensities() {
        if (mCachedApplicableDensities == null) {
            // Use the gradle API to set up relevant densities. For example, if the
            // build.gradle file contains this:
            // android {
            //     defaultConfig {
            //         resConfigs "nodpi", "hdpi"
            //     }
            // }
            // ...then we should only enforce hdpi densities, not all these others!
            LintModelVariant variant = getBuildVariant();
            if (variant != null) {
                Set<String> relevantDensities = Sets.newHashSet();
                for (String densityName : variant.getResourceConfigurations()) {
                    Density density = Density.getEnum(densityName);
                    if (density != null
                            && density.isRecommended()
                            && density != Density.NODPI
                            && density != Density.ANYDPI) {
                        relevantDensities.add(densityName);
                    }
                }
                if (!relevantDensities.isEmpty()) {
                    mCachedApplicableDensities =
                            Lists.newArrayListWithExpectedSize(relevantDensities.size());
                    for (String density : relevantDensities) {
                        String folder = ResourceFolderType.DRAWABLE.getName() + '-' + density;
                        mCachedApplicableDensities.add(folder);
                    }
                    Collections.sort(mCachedApplicableDensities);
                } else {
                    mCachedApplicableDensities = Collections.emptyList();
                }
            } else {
                mCachedApplicableDensities = Collections.emptyList();
            }
        }

        return mCachedApplicableDensities.isEmpty() ? null : mCachedApplicableDensities;
    }

    /**
     * Returns a super class map for this project. The keys and values are internal class names
     * (e.g. java/lang/Integer, not java.lang.Integer).
     *
     * @return a map, possibly empty but never null
     */
    @NonNull
    public Map<String, String> getSuperClassMap() {
        if (superClassMap == null) {
            superClassMap = client.createSuperClassMap(this);
        }

        return superClassMap;
    }

    /**
     * Returns a shared {@link ResourceVisibilityLookup}
     *
     * @return a shared provider for looking up resource visibility
     */
    @NonNull
    public ResourceVisibilityLookup getResourceVisibility() {
        if (resourceVisibility == null) {
            LintModelVariant variant = getBuildVariant();
            if (variant != null) {
                Collection<LintModelLibrary> libraries =
                        variant.getArtifact().getDependencies().getAll();
                List<ResourceVisibilityLookup> list = new ArrayList<>(libraries.size());
                for (LintModelLibrary library : libraries) {
                    if (library instanceof LintModelAndroidLibrary) {
                        LintModelAndroidLibrary l = (LintModelAndroidLibrary) library;
                        ResourceVisibilityLookup lookup = createLibraryVisibilityLookup(l);
                        list.add(lookup);
                    }
                }
                resourceVisibility = ResourceVisibilityLookup.create(list);
            } else if (getBuildLibraryModel() != null) {
                LintModelAndroidLibrary library = getBuildLibraryModel();
                resourceVisibility = createLibraryVisibilityLookup(library);
            } else {
                resourceVisibility = ResourceVisibilityLookup.NONE;
            }
        }

        return resourceVisibility;
    }

    @NonNull
    private static ResourceVisibilityLookup createLibraryVisibilityLookup(
            LintModelAndroidLibrary androidLibrary) {
        File publicResources = androidLibrary.getPublicResources();
        File symbolFile = androidLibrary.getSymbolFile();
        LintModelMavenName c = androidLibrary.getResolvedCoordinates();
        String key = c.getGroupId() + ":" + c.getArtifactId() + ":" + c.getVersion();
        return ResourceVisibilityLookup.create(publicResources, symbolFile, key);
    }

    /**
     * Returns the associated client
     *
     * @return the client
     */
    @NonNull
    public LintClient getClient() {
        return client;
    }

    public void setEnv(CoreApplicationEnvironment env) {
        this.env = env;
    }

    public CoreApplicationEnvironment getEnv() {
        return env;
    }

    /**
     * For KMP projects See <a
     * href="https://github.com/JetBrains/kotlin/blob/master/analysis/project-structure/src/org/jetbrains/kotlin/analysis/project/structure/KtModule.kt#L33-L41">...</a>
     */
    public enum DependencyKind {
        Regular,
        DependsOn
    }

    private final Map<Project, DependencyKind> dependencyKind = new HashMap<>();

    public void setDependencyKind(@NonNull Project lib, @NonNull DependencyKind kind) {
        dependencyKind.put(lib, kind);
    }

    @NonNull
    public DependencyKind getDependencyKind(@NonNull Project lib) {
        return dependencyKind.getOrDefault(lib, DependencyKind.Regular);
    }
}
