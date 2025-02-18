/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.resources.aar;

import static com.android.SdkConstants.DOT_9PNG;
import static com.android.SdkConstants.FD_RES_RAW;
import static com.android.SdkConstants.JAR_PROTOCOL;
import static com.android.SdkConstants.JAR_SEPARATOR;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ProgressManagerAdapter;
import com.android.annotations.TestOnly;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.ide.common.util.PathString;
import com.android.io.CancellableFileIo;
import com.android.resources.ResourceType;
import com.android.resources.base.BasicResourceItem;
import com.android.resources.base.BasicResourceItemBase;
import com.android.resources.base.BasicValueResourceItemBase;
import com.android.resources.base.NamespaceResolver;
import com.android.resources.base.RepositoryConfiguration;
import com.android.resources.base.RepositoryLoader;
import com.android.resources.base.ResourceSerializationUtil;
import com.android.tools.environment.Logger;
import com.android.utils.Base128InputStream;
import com.android.utils.Base128OutputStream;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Repository of resources of the Android framework. Most client code should use
 * the ResourceRepositoryManager.getFrameworkResources method to obtain framework resources.
 *
 * <p>The repository can be loaded either from a res directory containing XML files, or from
 * framework_res.jar file, or from a binary cache file returned by the
 * {@link CachingData#getCacheFile()} method. This binary cache file can be created as a side effect
 * of loading the repository from a res directory.
 *
 * <p>Loading from framework_res.jar or a binary cache file is 3-4 times faster than loading
 * from res directory.
 *
 * @see FrameworkResJarCreator
 */
public final class FrameworkResourceRepository extends AarSourceResourceRepository {
  public static final String OVERLAYS_DIR = "overlays/";
  private static final ResourceNamespace ANDROID_NAMESPACE = ResourceNamespace.ANDROID;
  /** Mapping from languages to language groups, e.g. Romansh is mapped to Italian. */
  private static final Map<String, String> LANGUAGE_TO_GROUP = ImmutableMap.of("rm", "it");
  private static final String RESOURCES_TABLE_PREFIX = "resources_";
  private static final String RESOURCE_TABLE_SUFFIX = ".bin";
  private static final String COMPILED_9PNG_EXTENSION = ".compiled.9.png";

  private static final Logger LOG = Logger.getInstance(FrameworkResourceRepository.class);

  private final Set<String> myLanguageGroups = new TreeSet<>();
  private int myNumberOfLanguageGroupsLoadedFromCache;
  private final boolean myUseCompiled9Patches;
  private final String myResourceSubDir;

  private FrameworkResourceRepository(@NonNull RepositoryLoader<FrameworkResourceRepository> loader,
          boolean useCompiled9Patches) {
      this(loader, "", useCompiled9Patches);
  }

  private FrameworkResourceRepository(@NonNull RepositoryLoader<FrameworkResourceRepository> loader,
          @NonNull String overlaySubDir, boolean useCompiled9Patches) {
    super(loader, null);
    myResourceSubDir = overlaySubDir;
    myUseCompiled9Patches = useCompiled9Patches;
  }

  /**
   * Creates an Android framework resource repository.
   *
   * @param resourceDirectoryOrFile the res directory or a jar file containing resources of the Android framework
   * @param languagesToLoad the set of ISO 639 language codes, or null to load all available languages
   * @param cachingData data used to validate and create a persistent cache file
   * @param useCompiled9Patches whether to provide the compiled or non-compiled version of the framework 9-patches
   * @return the created resource repository
   */
  @NonNull
  public static FrameworkResourceRepository create(@NonNull Path resourceDirectoryOrFile, @Nullable Set<String> languagesToLoad,
                                                   @Nullable CachingData cachingData, boolean useCompiled9Patches) {
    long start = LOG.isDebugEnabled() ? System.currentTimeMillis() : 0;
    Set<String> languageGroups = languagesToLoad == null ? null : getLanguageGroups(languagesToLoad);

    Loader loader = new Loader(resourceDirectoryOrFile, languageGroups);
    FrameworkResourceRepository repository = new FrameworkResourceRepository(loader, useCompiled9Patches);

    repository.load(null, cachingData, loader, languageGroups, loader.myLoadedLanguageGroups);

    if (LOG.isDebugEnabled()) {
      String source = repository.getNumberOfLanguageGroupsLoadedFromOrigin() == 0 ?
                      "cache" :
                      repository.myNumberOfLanguageGroupsLoadedFromCache == 0 ?
                      resourceDirectoryOrFile.toString() :
                      "cache and " + resourceDirectoryOrFile;
      LOG.debug("Loaded from " + source + " with " + (repository.myLanguageGroups.size() - 1) + " languages in " +
                (System.currentTimeMillis() - start) / 1000. + " sec");
    }
    return repository;
  }

    /**
     * Creates an Android framework resource repository.
     *
     * @param resourceDirectoryOrFile the res directory or a jar file containing resources of the Android framework
     * @param overlayName the name of the overlay represented by this repository
     * @param languagesToLoad the set of ISO 639 language codes, or null to load all available languages
     * @param cachingData data used to validate and create a persistent cache file
     * @param useCompiled9Patches whether to provide the compiled or non-compiled version of the framework 9-patches
     * @return the created resource repository
     */
    @NonNull
    public static FrameworkResourceRepository createForOverlay(@NonNull Path resourceDirectoryOrFile,
            @NonNull String overlayName,
            @Nullable Set<String> languagesToLoad,
            @Nullable CachingData cachingData,
            boolean useCompiled9Patches) {
        long start = LOG.isDebugEnabled() ? System.currentTimeMillis() : 0;
        Set<String> languageGroups = languagesToLoad == null ? null : getLanguageGroups(languagesToLoad);

        String overlaySubDir = OVERLAYS_DIR + overlayName + "/";
        Loader loader = new Loader(resourceDirectoryOrFile, overlaySubDir, languageGroups);
        FrameworkResourceRepository repository = new FrameworkResourceRepository(loader, overlaySubDir, useCompiled9Patches);

        repository.load(null, cachingData, loader, languageGroups, loader.myLoadedLanguageGroups);

        if (LOG.isDebugEnabled()) {
            String source = repository.getNumberOfLanguageGroupsLoadedFromOrigin() == 0 ?
                            "cache" :
                            repository.myNumberOfLanguageGroupsLoadedFromCache == 0 ?
                            resourceDirectoryOrFile.toString() :
                            "cache and " + resourceDirectoryOrFile;
            LOG.debug("Loaded from " + source + " with " + (repository.myLanguageGroups.size() - 1) + " languages in " +
                      (System.currentTimeMillis() - start) / 1000. + " sec");
        }
        return repository;
    }

  /**
   * Checks if the repository contains resources for the given set of languages.
   *
   * @param languages the set of ISO 639 language codes to check
   * @return true if the repository contains resources for all requested languages
   */
  public boolean containsLanguages(@NonNull Set<String> languages) {
    for (String language : languages) {
      if (!myLanguageGroups.contains(getLanguageGroup(language))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Loads resources for requested languages that are not present in this resource repository.
   *
   * @param languagesToLoad the set of ISO 639 language codes, or null to load all available languages
   * @param cachingData data used to validate and create a persistent cache file
   * @return the new resource repository with additional resources, or this resource repository if it already contained
   *     all requested languages
   */
  @NonNull
  public FrameworkResourceRepository loadMissingLanguages(@Nullable Set<String> languagesToLoad, @Nullable CachingData cachingData) {
    @Nullable Set<String> languageGroups = languagesToLoad == null ? null : getLanguageGroups(languagesToLoad);
    if (languageGroups != null && myLanguageGroups.containsAll(languageGroups)) {
      return this; // The repository already contains all requested languages.
    }

    long start = LOG.isDebugEnabled() ? System.currentTimeMillis() : 0;
    Loader loader = new Loader(this, languageGroups);
    FrameworkResourceRepository newRepository = new FrameworkResourceRepository(loader, myResourceSubDir, myUseCompiled9Patches);

    newRepository.load(this, cachingData, loader, languageGroups, loader.myLoadedLanguageGroups);

    if (LOG.isDebugEnabled()) {
      String source = newRepository.getNumberOfLanguageGroupsLoadedFromOrigin() == getNumberOfLanguageGroupsLoadedFromOrigin() ?
                      "cache" :
                      newRepository.myNumberOfLanguageGroupsLoadedFromCache == myNumberOfLanguageGroupsLoadedFromCache ?
                      myResourceDirectoryOrFile.toString() :
                      "cache and " + myResourceDirectoryOrFile;
      LOG.debug("Loaded " + (newRepository.myLanguageGroups.size() - myLanguageGroups.size()) + " additional languages from " + source +
                " in " + (System.currentTimeMillis() - start) / 1000. + " sec");
    }
    return newRepository;
  }

  private void load(@Nullable FrameworkResourceRepository sourceRepository,
                    @Nullable CachingData cachingData,
                    @NonNull Loader loader,
                    @Nullable Set<String> languageGroups,
                    @NonNull Set<String> languageGroupsLoadedFromSourceRepositoryOrCache) {
    Map<String, String> stringCache = Maps.newHashMapWithExpectedSize(10000);
    Map<NamespaceResolver, NamespaceResolver> namespaceResolverCache = new HashMap<>();
    Set<RepositoryConfiguration> configurationsToTakeOver =
        sourceRepository == null ? ImmutableSet.of() : copyFromRepository(sourceRepository, stringCache, namespaceResolverCache);

    // If not loading from a jar file, try to load from a cache file first. A separate cache file is not used
    // when loading from framework_res.jar since it already contains data in the cache format. Loading from
    // framework_res.jar or a cache file is significantly faster than reading individual resource files.
    if (!loader.isLoadingFromZipArchive() && cachingData != null) {
      loadFromPersistentCache(cachingData, languageGroups, languageGroupsLoadedFromSourceRepositoryOrCache, stringCache,
                              namespaceResolverCache);
    }

    myLanguageGroups.addAll(languageGroupsLoadedFromSourceRepositoryOrCache);
    if (languageGroups == null || !languageGroupsLoadedFromSourceRepositoryOrCache.containsAll(languageGroups)) {
      loader.loadRepositoryContents(this);
    }

    myLoadedFromCache = myNumberOfLanguageGroupsLoadedFromCache == myLanguageGroups.size();

    populatePublicResourcesMap();
    freezeResources();
    takeOverConfigurations(configurationsToTakeOver);

    if (!loader.isLoadingFromZipArchive() && cachingData != null) {
      Executor executor = cachingData.getCacheCreationExecutor();
      if (executor != null && !languageGroupsLoadedFromSourceRepositoryOrCache.containsAll(myLanguageGroups)) {
        executor.execute(() -> createPersistentCache(cachingData, languageGroupsLoadedFromSourceRepositoryOrCache));
      }
    }
  }

  @Override
  @Nullable
  public String getPackageName() {
    return ANDROID_NAMESPACE.getPackageName();
  }

  @Override
  @NonNull
  public Set<ResourceType> getResourceTypes(@NonNull ResourceNamespace namespace) {
    return namespace == ANDROID_NAMESPACE ? Sets.immutableEnumSet(myResources.keySet()) : ImmutableSet.of();
  }

  /**
   * Copies resources from another FrameworkResourceRepository.
   *
   * @param sourceRepository the repository to copy resources from
   * @param stringCache the string cache to populate with the names of copied resources
   * @param namespaceResolverCache the namespace resolver cache to populate with namespace resolvers referenced by the copied resources
   * @return the {@link RepositoryConfiguration} objects referenced by the copied resources
   */
  @NonNull
  private Set<RepositoryConfiguration> copyFromRepository(@NonNull FrameworkResourceRepository sourceRepository,
                                                          @NonNull Map<String, String> stringCache,
                                                          @NonNull Map<NamespaceResolver, NamespaceResolver> namespaceResolverCache) {
    Collection<ListMultimap<String, ResourceItem>> resourceMaps = sourceRepository.myResources.values();

    // Copy resources from the source repository, get AarConfigurations that need to be taken over by this repository,
    // and pre-populate string and namespace resolver caches.
    Set<RepositoryConfiguration> sourceConfigurations = Sets.newIdentityHashSet();
    for (ListMultimap<String, ResourceItem> resourceMap : resourceMaps) {
      for (ResourceItem item : resourceMap.values()) {
        addResourceItem(item);

        sourceConfigurations.add(((BasicResourceItemBase)item).getRepositoryConfiguration());
        if (item instanceof BasicValueResourceItemBase) {
          ResourceNamespace.Resolver resolver = ((BasicValueResourceItemBase)item).getNamespaceResolver();
          NamespaceResolver namespaceResolver =
              resolver == ResourceNamespace.Resolver.EMPTY_RESOLVER ? NamespaceResolver.EMPTY : (NamespaceResolver)resolver;
          namespaceResolverCache.put(namespaceResolver, namespaceResolver);
        }
        String name = item.getName();
        stringCache.put(name, name);
      }
    }

    myNumberOfLanguageGroupsLoadedFromCache += sourceRepository.myNumberOfLanguageGroupsLoadedFromCache;
    return sourceConfigurations;
  }

  private void loadFromPersistentCache(@NonNull CachingData cachingData, @Nullable Set<String> languagesToLoad,
                                       @NonNull Set<String> loadedLanguages,
                                       @NonNull Map<String, String> stringCache,
                                       @Nullable Map<NamespaceResolver, NamespaceResolver> namespaceResolverCache) {
    CacheFileNameGenerator fileNameGenerator = new CacheFileNameGenerator((cachingData));
    Set<String> languages = languagesToLoad == null ? fileNameGenerator.getAllCacheFileLanguages() : languagesToLoad;

    for (String language : languages) {
      if (!loadedLanguages.contains(language)) {
        Path cacheFile = fileNameGenerator.getCacheFile(language);
        try (Base128InputStream stream = new Base128InputStream(cacheFile)) {
          byte[] header = ResourceSerializationUtil.getCacheFileHeader(s -> writeCacheHeaderContent(cachingData, language, s));
          if (!stream.validateContents(header)) {
            // Cache file header doesn't match.
            if (language.isEmpty()) {
              break; // Don't try to load language-specific resources if language-neutral ones could not be loaded.
            }
            continue;
          }
          loadFromStream(stream, stringCache, namespaceResolverCache);
          loadedLanguages.add(language);
          myNumberOfLanguageGroupsLoadedFromCache++;
        }
        catch (NoSuchFileException e) {
          // Cache file does not exist.
          if (language.isEmpty()) {
            break;  // Don't try to load language-specific resources if language-neutral ones could not be loaded.
          }
        }
        catch (Throwable e) {
          cleanupAfterFailedLoadingFromCache();
          loadedLanguages.clear();
          ProgressManagerAdapter.throwIfCancellation(e);
          LOG.warn("Failed to load from cache file " + cacheFile.toString(), e);
          break;
        }
      }
    }
  }

  @Override
  protected void cleanupAfterFailedLoadingFromCache() {
    super.cleanupAfterFailedLoadingFromCache();
    myNumberOfLanguageGroupsLoadedFromCache = 0;
  }

  private void createPersistentCache(@NonNull CachingData cachingData, @NonNull Set<String> languagesToSkip) {
    CacheFileNameGenerator fileNameGenerator = new CacheFileNameGenerator(cachingData);
    for (String language : myLanguageGroups) {
      if (!languagesToSkip.contains(language)) {
        Path cacheFile = fileNameGenerator.getCacheFile(language);
        byte[] header = ResourceSerializationUtil.getCacheFileHeader(stream -> writeCacheHeaderContent(cachingData, language, stream));
        ResourceSerializationUtil.createPersistentCache(
            cacheFile, header, stream -> writeToStream(stream, config -> language.equals(getLanguageGroup(config))));
      }
    }
  }

  private void writeCacheHeaderContent(@NonNull CachingData cachingData, @NonNull String language, @NonNull Base128OutputStream stream)
      throws IOException {
    writeCacheHeaderContent(cachingData, stream);
    stream.writeString(language);
  }

  /**
   * Returns the name of the resource table file containing resources for the given language.
   *
   * @param language the two-letter language abbreviation, or an empty string for language-neutral resources
   * @return the file name
   */
  static String getResourceTableNameForLanguage(@NonNull String language) {
    return language.isEmpty() ? "resources.bin" : RESOURCES_TABLE_PREFIX + language + RESOURCE_TABLE_SUFFIX;
  }

  @NonNull
  static String getLanguageGroup(@NonNull FolderConfiguration config) {
    LocaleQualifier locale = config.getLocaleQualifier();
    return locale == null ? "" : getLanguageGroup(Strings.nullToEmpty(locale.getLanguage()));
  }

  /**
   * Maps some languages to others effectively grouping languages together. For example, Romansh language
   * that has very few framework resources is grouped together with Italian.
   *
   * @param language the original language
   * @return the language representing the corresponding group of languages
   */
  @NonNull
  private static String getLanguageGroup(@NonNull String language) {
    return LANGUAGE_TO_GROUP.getOrDefault(language, language);
  }

  @NonNull
  private static Set<String> getLanguageGroups(@NonNull Set<String> languages) {
    Set<String> result = new TreeSet<>();
    result.add("");
    for (String language : languages) {
      result.add(getLanguageGroup(language));
    }
    return result;
  }

  @NonNull
  Set<String> getLanguageGroups() {
    Set<String> languages = new TreeSet<>();

    for (ListMultimap<String, ResourceItem> resourceMap : myResources.values()) {
      for (ResourceItem item : resourceMap.values()) {
        FolderConfiguration config = item.getConfiguration();
        languages.add(getLanguageGroup(config));
      }
    }

    return languages;
  }

  private int getNumberOfLanguageGroupsLoadedFromOrigin() {
    return myLanguageGroups.size() - myNumberOfLanguageGroupsLoadedFromCache;
  }

  @TestOnly
  int getNumberOfLanguageGroupsLoadedFromCache() {
    return myNumberOfLanguageGroupsLoadedFromCache;
  }

  @NonNull
  private String updateResourcePath(@NonNull String relativeResourcePath) {
    if (myUseCompiled9Patches && relativeResourcePath.endsWith(DOT_9PNG)) {
      return relativeResourcePath.substring(0, relativeResourcePath.length() - DOT_9PNG.length()) + COMPILED_9PNG_EXTENSION;
    }
    return relativeResourcePath;
  }

  @Override
  @NonNull
  public String getResourceUrl(@NonNull String relativeResourcePath) {
    return super.getResourceUrl(updateResourcePath(relativeResourcePath));
  }

  @Override
  @NonNull
  public PathString getSourceFile(@NonNull String relativeResourcePath, boolean forFileResource) {
    return super.getSourceFile(updateResourcePath(relativeResourcePath), forFileResource);
  }

  private static class Loader extends RepositoryLoader<FrameworkResourceRepository> {
    @NonNull private final List<String> myPublicFileNames = ImmutableList.of("public.xml", "public-final.xml", "public-staging.xml");
    @NonNull private final Set<String> myLoadedLanguageGroups;
    @NonNull private final String myResourceSubDir;
    @Nullable private Set<String> myLanguageGroups;

    Loader(@NonNull Path resourceDirectoryOrFile, @Nullable Set<String> languageGroups) {
        super(resourceDirectoryOrFile, null, ANDROID_NAMESPACE);
        myLanguageGroups = languageGroups;
        myLoadedLanguageGroups = new TreeSet<>();
        myResourceSubDir = "";
    }

    Loader(@NonNull Path resourceDirectoryOrFile, @NonNull String subDir, @Nullable Set<String> languageGroups) {
      super(resourceDirectoryOrFile, null, ANDROID_NAMESPACE);
      myLanguageGroups = languageGroups;
      myLoadedLanguageGroups = new TreeSet<>();
      myResourceSubDir = subDir;
    }

    Loader(@NonNull FrameworkResourceRepository sourceRepository, @Nullable Set<String> languageGroups) {
      super(sourceRepository.myResourceDirectoryOrFile, null, ANDROID_NAMESPACE);
      myLanguageGroups = languageGroups;
      myLoadedLanguageGroups = new TreeSet<>(sourceRepository.myLanguageGroups);
      myResourceSubDir = sourceRepository.myResourceSubDir;
    }

    public List<String> getPublicXmlFileNames() {
      return myPublicFileNames;
    }

    @Override
    protected void loadFromZip(@NonNull FrameworkResourceRepository repository) {
      try (ZipFile zipFile = new ZipFile(myResourceDirectoryOrFile.toFile())) {
        if (myLanguageGroups == null) {
          myLanguageGroups = readLanguageGroups(zipFile, myResourceSubDir);
        }

        Map<String, String> stringCache = Maps.newHashMapWithExpectedSize(10000);
        Map<NamespaceResolver, NamespaceResolver> namespaceResolverCache = new HashMap<>();

        for (String language : myLanguageGroups) {
          if (!myLoadedLanguageGroups.contains(language)) {
            String entryName = myResourceSubDir + getResourceTableNameForLanguage(language);
            ZipEntry zipEntry = zipFile.getEntry(entryName);
            if (zipEntry == null) {
              if (language.isEmpty()) {
                throw new IOException("\"" + entryName + "\" not found in " + myResourceDirectoryOrFile.toString());
              }
              else {
                continue; // Requested language may not be represented in the Android framework resources.
              }
            }

            try (Base128InputStream stream = new Base128InputStream(zipFile.getInputStream(zipEntry))) {
              repository.loadFromStream(stream, stringCache, namespaceResolverCache);
            }
          }
        }

        repository.populatePublicResourcesMap();
        repository.freezeResources();
      }
      catch (Exception e) {
        ProgressManagerAdapter.throwIfCancellation(e);
        LOG.error("Failed to load resources from " + myResourceDirectoryOrFile.toString(), e);
      }
    }

    @NonNull
    @Override
    public String getResourcePathPrefix() {
      if (isLoadingFromZipArchive()) {
        return portableFileName(myResourceDirectoryOrFile.toString())
               + JAR_SEPARATOR + myResourceSubDir + "res/";
      }
      else {
        return portableFileName(myResourceDirectoryOrFile.toString()) + '/';
      }
    }

    @NonNull
    @Override
    public String getResourceUrlPrefix() {
      if (isLoadingFromZipArchive()) {
        return JAR_PROTOCOL + "://" + portableFileName(myResourceDirectoryOrFile.toString())
               + JAR_SEPARATOR + myResourceSubDir + "res/";
      }
      else {
        return portableFileName(myResourceDirectoryOrFile.toString()) + '/';
      }
    }

    @NonNull
    private static Set<String> readLanguageGroups(@NonNull ZipFile zipFile, @NonNull String subDir) {
      ImmutableSortedSet.Builder<String> result = ImmutableSortedSet.naturalOrder();
      result.add("");
      String prefix = subDir + RESOURCES_TABLE_PREFIX;
      zipFile.stream().forEach(entry -> {
        String name = entry.getName();
        if (name.startsWith(prefix) && name.endsWith(RESOURCE_TABLE_SUFFIX) &&
            name.length() == prefix.length() + RESOURCE_TABLE_SUFFIX.length() + 2 &&
            Character.isLetter(name.charAt(prefix.length())) &&
            Character.isLetter(name.charAt(prefix.length() + 1))) {
          result.add(name.substring(prefix.length(), prefix.length() + 2));
        }
      });
      return result.build();
    }

    @Override
    public void loadRepositoryContents(@NonNull FrameworkResourceRepository repository) {
      super.loadRepositoryContents(repository);

      Set<String> languageGroups = myLanguageGroups == null ? repository.getLanguageGroups() : myLanguageGroups;
      repository.myLanguageGroups.addAll(languageGroups);
    }

    @Override
    public boolean isIgnored(@NonNull Path fileOrDirectory, @NonNull BasicFileAttributes attrs) {
      if (fileOrDirectory.equals(myResourceDirectoryOrFile)) {
        return false;
      }

      if (super.isIgnored(fileOrDirectory, attrs)) {
        return true;
      }

      String fileName = fileOrDirectory.getFileName().toString();
      if (attrs.isDirectory()) {
        if (fileName.startsWith("values-mcc") ||
            fileName.startsWith(FD_RES_RAW) && (fileName.length() == FD_RES_RAW.length() || fileName.charAt(FD_RES_RAW.length()) == '-')) {
          return true; // Mobile country codes and raw resources are not used by LayoutLib.
        }

        // Skip folders that don't belong to languages in myLanguageGroups or languages that were loaded earlier.
        if (myLanguageGroups != null || !myLoadedLanguageGroups.isEmpty()) {
          FolderConfiguration config = FolderConfiguration.getConfigForFolder(fileName);
          if (config == null) {
            return true;
          }
          String language = getLanguageGroup(config);
          if ((myLanguageGroups != null && !myLanguageGroups.contains(language)) || myLoadedLanguageGroups.contains(language)) {
            return true;
          }
          myFolderConfigCache.put(config.getQualifierString(), config);
        }
      }
      else if ((myPublicFileNames.contains(fileName) || fileName.equals("symbols.xml")) &&
               "values".equals(new PathString(fileOrDirectory).getParentFileName())) {
        return true; // Skip files that don't contain resources.
      }
      else if (fileName.endsWith(COMPILED_9PNG_EXTENSION)) {
        return true;
      }

      return false;
    }

    @Override
    protected final void addResourceItem(@NonNull BasicResourceItem item, @NonNull FrameworkResourceRepository repository) {
      repository.addResourceItem(item);
    }

    @Override
    @NonNull
    protected String getKeyForVisibilityLookup(@NonNull String resourceName) {
      // This class obtains names of public resources from public.xml where all resource names are preserved
      // in their original form. This is different from the superclass that obtains the names from public.txt
      // where the names are transformed by replacing dots, colons and dashes with underscores.
      return resourceName;
    }
  }

  /**
   * Redirects the {@link RepositoryConfiguration} inherited from another repository to point to this one, so that
   * the other repository can be garbage collected. This has to be done after this repository is fully loaded.
   *
   * @param sourceConfigurations the configurations to reparent
   */
  private void takeOverConfigurations(@NonNull Set<RepositoryConfiguration> sourceConfigurations) {
    for (RepositoryConfiguration configuration : sourceConfigurations) {
      configuration.transferOwnershipTo(this);
    }
  }

  private static class CacheFileNameGenerator {
    private final Path myLanguageNeutralFile;
    private final String myPrefix;
    private final String mySuffix;

    CacheFileNameGenerator(@NonNull CachingData cachingData) {
      myLanguageNeutralFile = cachingData.getCacheFile();
      String fileName = myLanguageNeutralFile.getFileName().toString();
      int dotPos = fileName.lastIndexOf('.');
      myPrefix = dotPos >= 0 ? fileName.substring(0, dotPos) : fileName;
      mySuffix = dotPos >= 0 ? fileName.substring(dotPos) : "";
    }

    @NonNull
    Path getCacheFile(@NonNull String language) {
      return language.isEmpty() ? myLanguageNeutralFile : myLanguageNeutralFile.resolveSibling(myPrefix + '_' + language + mySuffix);
    }

    /**
     * Determines language from a cache file name.
     *
     * @param cacheFileName the name of a cache file
     * @return the language of resources contained in the cache file, or null if {@code cacheFileName}
     *     doesn't match the pattern of cache file names.
     */
    @Nullable
    String getLanguage(@NonNull String cacheFileName) {
      if (!cacheFileName.startsWith(myPrefix) || !cacheFileName.endsWith(mySuffix)) {
        return null;
      }
      int baseLength = myPrefix.length() + mySuffix.length();
      if (cacheFileName.length() == baseLength) {
        return "";
      }
      if (cacheFileName.length() != baseLength + 3 || cacheFileName.charAt(myPrefix.length()) != '_') {
        return null;
      }
      String language = cacheFileName.substring(myPrefix.length() + 1, myPrefix.length() + 3);
      if (!isLowerCaseLatinLetter(language.charAt(0)) || !isLowerCaseLatinLetter(language.charAt(1))) {
        return null;
      }
      return language;
    }

    @NonNull
    public Set<String> getAllCacheFileLanguages() {
      Set<String> result = new TreeSet<>();
      try (Stream<Path> stream = CancellableFileIo.list(myLanguageNeutralFile.getParent())) {
        stream.forEach(file -> {
          String language = getLanguage(file.getFileName().toString());
          if (language != null) {
            result.add(language);
          }
        });
      }
      catch (IOException ignore) {
      }
      return result;
    }

    private static boolean isLowerCaseLatinLetter(char c) {
      return 'a' <= c && c <= 'z';
    }
  }
}
