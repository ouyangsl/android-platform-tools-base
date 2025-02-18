/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.resources.base;

import static com.android.SdkConstants.ANDROID_NS_NAME;
import static com.android.SdkConstants.ATTR_FORMAT;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INDEX;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.ATTR_QUANTITY;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.ATTR_VALUE;
import static com.android.SdkConstants.DOT_AAR;
import static com.android.SdkConstants.DOT_JAR;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.DOT_ZIP;
import static com.android.SdkConstants.FD_RES_VALUES;
import static com.android.SdkConstants.JAR_PROTOCOL;
import static com.android.SdkConstants.JAR_SEPARATOR;
import static com.android.SdkConstants.NEW_ID_PREFIX;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.PREFIX_THEME_REF;
import static com.android.SdkConstants.TAG_ATTR;
import static com.android.SdkConstants.TAG_EAT_COMMENT;
import static com.android.SdkConstants.TAG_ENUM;
import static com.android.SdkConstants.TAG_FLAG;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_JAVA_SYMBOL;
import static com.android.SdkConstants.TAG_PUBLIC;
import static com.android.SdkConstants.TAG_PUBLIC_GROUP;
import static com.android.SdkConstants.TAG_RESOURCES;
import static com.android.SdkConstants.TAG_SKIP;
import static com.android.SdkConstants.TAG_STAGING_PUBLIC_GROUP;
import static com.android.SdkConstants.TAG_STAGING_PUBLIC_GROUP_FINAL;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.ide.common.resources.AndroidAaptIgnoreKt.ANDROID_AAPT_IGNORE;
import static com.android.ide.common.resources.ResourceItem.ATTR_EXAMPLE;
import static com.android.ide.common.resources.ResourceItem.XLIFF_G_TAG;
import static com.android.ide.common.resources.ResourceItem.XLIFF_NAMESPACE_PREFIX;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.ProgressManagerAdapter;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.ide.common.rendering.api.DensityBasedResourceValue;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.StyleItemResourceValue;
import com.android.ide.common.rendering.api.StyleItemResourceValueImpl;
import com.android.ide.common.resources.AndroidAaptIgnore;
import com.android.ide.common.resources.PatternBasedFileFilter;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourcesUtil;
import com.android.ide.common.resources.ValueResourceNameValidator;
import com.android.ide.common.resources.ValueXmlHelper;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.util.PathString;
import com.android.io.CancellableFileIo;
import com.android.resources.Arity;
import com.android.resources.Density;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.tools.environment.Logger;
import com.android.utils.SdkUtils;
import com.android.utils.XmlUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public abstract class RepositoryLoader<T extends LoadableResourceRepository> implements FileFilter {
  private static final Logger LOG = Logger.getInstance(RepositoryLoader.class);
  /** The set of attribute formats that is used when no formats are explicitly specified and the attribute is not a flag or enum. */
  private final Set<AttributeFormat> DEFAULT_ATTR_FORMATS = Sets.immutableEnumSet(
      AttributeFormat.BOOLEAN,
      AttributeFormat.COLOR,
      AttributeFormat.DIMENSION,
      AttributeFormat.FLOAT,
      AttributeFormat.FRACTION,
      AttributeFormat.INTEGER,
      AttributeFormat.REFERENCE,
      AttributeFormat.STRING);
  private final PatternBasedFileFilter myFileFilter
    = new PatternBasedFileFilter(new AndroidAaptIgnore(System.getenv(ANDROID_AAPT_IGNORE)));

  @NonNull private final Map<ResourceType, Set<String>> myPublicResources = new EnumMap<>(ResourceType.class);
  @NonNull private final ListMultimap<String, BasicAttrResourceItem> myAttrs = ArrayListMultimap.create();
  @NonNull private final ListMultimap<String, BasicAttrResourceItem> myAttrCandidates = ArrayListMultimap.create();
  @NonNull private final ListMultimap<String, BasicStyleableResourceItem> myStyleables = ArrayListMultimap.create();
  @NonNull protected ResourceVisibility myDefaultVisibility = ResourceVisibility.PRIVATE;
  /** Cache of FolderConfiguration instances, keyed by qualifier strings (see {@link FolderConfiguration#getQualifierString()}). */
  @NonNull protected final Map<String, FolderConfiguration> myFolderConfigCache = new HashMap<>();
  @NonNull private final Map<FolderConfiguration, RepositoryConfiguration> myConfigCache = new HashMap<>();
  @NonNull private final ValueResourceXmlParser myParser = new ValueResourceXmlParser();
  @NonNull private final XmlTextExtractor myTextExtractor = new XmlTextExtractor();
  @NonNull private final ResourceUrlParser myUrlParser = new ResourceUrlParser();
  // Used to keep track of resources defined in the current value resource file.
  @NonNull private final Table<ResourceType, String, BasicValueResourceItemBase> myValueFileResources =
      Tables.newCustomTable(new EnumMap<>(ResourceType.class), LinkedHashMap::new);
  @NonNull protected final Path myResourceDirectoryOrFile;
  @NonNull private final PathString myResourceDirectoryOrFilePath;
  private final boolean myLoadingFromZipArchive;

  @NonNull private final ResourceNamespace myNamespace;
  @Nullable private final Collection<PathString> myResourceFilesAndFolders;
  @Nullable protected ZipFile myZipFile;

  public RepositoryLoader(@NonNull Path resourceDirectoryOrFile, @Nullable Collection<PathString> resourceFilesAndFolders,
                          @NonNull ResourceNamespace namespace) {
    myResourceDirectoryOrFile = resourceDirectoryOrFile;
    myResourceDirectoryOrFilePath = new PathString(myResourceDirectoryOrFile);
    myLoadingFromZipArchive = isZipArchive(resourceDirectoryOrFile);
    myNamespace = namespace;
    myResourceFilesAndFolders = resourceFilesAndFolders;
  }

  @NonNull
  public final Path getResourceDirectoryOrFile() {
    return myResourceDirectoryOrFile;
  }

  public final boolean isLoadingFromZipArchive() {
    return myLoadingFromZipArchive;
  }

  @NonNull
  public final ResourceNamespace getNamespace() {
    return myNamespace;
  }

  public void loadRepositoryContents(@NonNull T repository) {
    if (myLoadingFromZipArchive) {
      loadFromZip(repository);
    }
    else {
      loadFromResFolder(repository);
    }
  }

  public List<String> getPublicXmlFileNames() {
    return ImmutableList.of("public.xml");
  }

  protected void loadFromZip(@NonNull T repository) {
    try (ZipFile zipFile = new ZipFile(myResourceDirectoryOrFile.toFile())) {
      myZipFile = zipFile;
      loadPublicResourceNames();
      boolean shouldParseResourceIds = !loadIdsFromRTxt();

      zipFile.stream().forEach(zipEntry -> {
        if (!zipEntry.isDirectory()) {
          PathString path = new PathString(zipEntry.getName());
          loadResourceFile(path, repository, shouldParseResourceIds);
        }
      });
    }
    catch (Exception e) {
      ProgressManagerAdapter.throwIfCancellation(e);
      LOG.error("Failed to load resources from " + myResourceDirectoryOrFile.toString(), e);
    }
    finally {
      myZipFile = null;
    }

    finishLoading(repository);
  }

  protected void loadFromResFolder(@NonNull T repository) {
    try {
      if (CancellableFileIo.notExists(myResourceDirectoryOrFile)) {
        return; // Don't report errors if the resource directory doesn't exist. This happens in some tests.
      }

      loadPublicResourceNames();
      boolean shouldParseResourceIds = !loadIdsFromRTxt();

      List<Path> sourceFilesAndFolders = myResourceFilesAndFolders == null ?
                                         ImmutableList.of(myResourceDirectoryOrFile) :
                                         myResourceFilesAndFolders
                                                 .stream()
                                                 .map(PathString::toPath)
                                                 .collect(Collectors.toList());
      List<PathString> resourceFiles = findResourceFiles(sourceFilesAndFolders);
      for (PathString file : resourceFiles) {
        loadResourceFile(file, repository, shouldParseResourceIds);
      }
    }
    catch (Exception e) {
      ProgressManagerAdapter.throwIfCancellation(e);
      LOG.error("Failed to load resources from " + myResourceDirectoryOrFile.toString(), e);
    }

    finishLoading(repository);
  }

  protected final void loadResourceFile(@NonNull PathString file, @NonNull T repository, boolean shouldParseResourceIds) {
    String folderName = file.getParentFileName();
    if (folderName != null) {
      FolderInfo folderInfo = FolderInfo.create(folderName, myFolderConfigCache);
      if (folderInfo != null) {
        RepositoryConfiguration configuration = getConfiguration(repository, folderInfo.configuration);
        loadResourceFile(file, folderInfo, configuration, shouldParseResourceIds);
      }
    }
  }

  protected void finishLoading(@NonNull T repository) {
    processAttrsAndStyleables();
  }

  @NonNull
  public final String getSourceFileProtocol() {
    if (myLoadingFromZipArchive) {
      return JAR_PROTOCOL;
    }
    else {
      return "file";
    }
  }

  @NonNull
  public String getResourcePathPrefix() {
    if (myLoadingFromZipArchive) {
      return portableFileName(myResourceDirectoryOrFile.toString()) + JAR_SEPARATOR + "res/";
    }
    else {
      return portableFileName(myResourceDirectoryOrFile.toString()) + '/';
    }
  }

  @NonNull
  public String getResourceUrlPrefix() {
    if (myLoadingFromZipArchive) {
      return JAR_PROTOCOL + "://" + portableFileName(myResourceDirectoryOrFile.toString()) + JAR_SEPARATOR + "res/";
    }
    else {
      return portableFileName(myResourceDirectoryOrFile.toString()) + '/';
    }
  }

  /**
   * A hook for loading resource IDs from a R.txt file. This implementation does nothing but subclasses may override.
   *
   * @return true if the IDs were successfully loaded from R.txt
   */
  protected boolean loadIdsFromRTxt() {
    return false;
  }

  @Override
  public boolean isIgnored(@NonNull Path fileOrDirectory, @NonNull BasicFileAttributes attrs) {
    if (fileOrDirectory.equals(myResourceDirectoryOrFile)) {
      return false;
    }

    return myFileFilter.isIgnored(fileOrDirectory.toString(), attrs.isDirectory());
  }

  /**
   * Loads names of the public resources and populates {@link #myPublicResources}.
   */
  protected void loadPublicResourceNames() {
    Path valuesFolder = myResourceDirectoryOrFile.resolve(FD_RES_VALUES);
    List<String> fileNames = getPublicXmlFileNames();
    for (String fileName : fileNames) {
      Path publicXmlFile = valuesFolder.resolve(fileName);

      try (InputStream stream = new BufferedInputStream(CancellableFileIo.newInputStream(publicXmlFile))) {
        CommentTrackingXmlPullParser parser = new CommentTrackingXmlPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(stream, UTF_8.name());

        String groupTag = null;
        ResourceType groupType = null;
        ResourceType lastType = null;
        String lastTypeName = "";
        while (true) {
          int event = parser.nextToken();
          if (event == XmlPullParser.START_TAG) {
            if (parser.getName().equals(TAG_PUBLIC)) {
              String name = null;
              String typeName = groupType == null ? null : groupType.getName();
              for (int i = 0, n = parser.getAttributeCount(); i < n; i++) {
                String attribute = parser.getAttributeName(i);

                if (attribute.equals(ATTR_NAME)) {
                  name = parser.getAttributeValue(i);
                  if (typeName != null) {
                    // Skip attributes other than "type" and "name".
                    break;
                  }
                }
                else if (attribute.equals(ATTR_TYPE)) {
                  typeName = parser.getAttributeValue(i);
                }
              }

              if (name != null && !name.startsWith("__removed") && (typeName != null || groupType != null) &&
                  (parser.getLastComment() == null || !containsWord(parser.getLastComment(), "@hide"))) {
                ResourceType type;
                if (groupType != null) {
                  type = groupType;
                }
                else {
                  if (typeName.equals(lastTypeName)) {
                    type = lastType;
                  }
                  else {
                    type = ResourceType.fromXmlValue(typeName);
                    lastType = type;
                    lastTypeName = typeName;
                  }
                }

                if (type != null) {
                  addPublicResourceName(type, name);
                }
                else {
                  LOG.error("Public resource declaration \"" + name + "\" of type " + typeName + " points to unknown resource type.");
                }
              }
            }
            else if (isPublicGroupTag(parser.getName())) {
              groupTag = parser.getName();
              String typeName = parser.getAttributeValue(null, ATTR_TYPE);
              groupType = typeName == null ? null : ResourceType.fromXmlValue(typeName);
            }
          }
          else if (event == XmlPullParser.END_TAG) {
            if (groupTag != null && groupTag.equals(parser.getName())) {
              groupTag = null;
              groupType = null;
            }
          }
          else if (event == XmlPullParser.END_DOCUMENT) {
            break;
          }
        }
      }
      catch (NoSuchFileException e) {
        // There is no public.xml. This not considered an error.
      }
      catch (Exception e) {
        ProgressManagerAdapter.throwIfCancellation(e);
        LOG.error("Can't read and parse " + publicXmlFile, e);
      }
    }
  }

  private boolean isPublicGroupTag(@NonNull String tag) {
    return tag.equals(TAG_PUBLIC_GROUP) ||
           tag.equals(TAG_STAGING_PUBLIC_GROUP) ||
           tag.equals(TAG_STAGING_PUBLIC_GROUP_FINAL);
  }

  protected final void addPublicResourceName(ResourceType type, String name) {
    Set<String> names = myPublicResources.computeIfAbsent(type, t -> new HashSet<>());
    names.add(name);
  }

  /**
   * Checks if the given text contains the given word.
   */
  private static boolean containsWord(@NonNull String text, @SuppressWarnings("SameParameterValue") @NonNull String word) {
    int end = 0;
    while (true) {
      int start = text.indexOf(word, end);
      if (start < 0) {
        return false;
      }
      end = start + word.length();
      if ((start == 0 || Character.isWhitespace(text.charAt(start))) &&
          (end == text.length() || Character.isWhitespace(text.charAt(end)))) {
        return true;
      }
    }
  }

  @NonNull
  private List<PathString> findResourceFiles(@NonNull List<Path> filesOrFolders) {
    ResourceFileCollector fileCollector = new ResourceFileCollector(this);
    for (Path file : filesOrFolders) {
      try {
        CancellableFileIo.walkFileTree(file, fileCollector);
      }
      catch (IOException e) {
        // All IOExceptions are logged by ResourceFileCollector.
      }
    }
    for (IOException e : fileCollector.ioErrors) {
      LOG.error("Error loading resources from " + myResourceDirectoryOrFile.toString(), e);
    }
    Collections.sort(fileCollector.resourceFiles); // Make sure that the files are in canonical order.
    return fileCollector.resourceFiles;
  }

  @NonNull
  protected final RepositoryConfiguration getConfiguration(@NonNull T repository, @NonNull FolderConfiguration folderConfiguration) {
    RepositoryConfiguration repositoryConfiguration = myConfigCache.get(folderConfiguration);
    if (repositoryConfiguration != null) {
      return repositoryConfiguration;
    }

    repositoryConfiguration = new RepositoryConfiguration(repository, folderConfiguration);
    myConfigCache.put(folderConfiguration, repositoryConfiguration);
    return repositoryConfiguration;
  }

  private void loadResourceFile(@NonNull PathString file, @NonNull FolderInfo folderInfo, @NonNull RepositoryConfiguration configuration,
                                boolean shouldParseResourceIds) {
    if (folderInfo.resourceType == null) {
      if (isXmlFile(file)) {
        parseValueResourceFile(file, configuration);
      }
    }
    else {
      if (shouldParseResourceIds && folderInfo.isIdGenerating && isXmlFile(file)) {
        parseIdGeneratingResourceFile(file, configuration);
      }

      BasicFileResourceItem item = createFileResourceItem(file, folderInfo.resourceType, configuration);
      addResourceItem(item);
    }
  }

  protected static boolean isXmlFile(@NonNull PathString file) {
    return isXmlFile(file.getFileName());
  }

  protected static boolean isXmlFile(@NonNull String filename) {
    return SdkUtils.endsWithIgnoreCase(filename, DOT_XML);
  }

  @SuppressWarnings("unchecked")
  private void addResourceItem(@NonNull BasicResourceItemBase item) {
    addResourceItem(item, (T)item.getRepository());
  }

  protected abstract void addResourceItem(@NonNull BasicResourceItem item, @NonNull T repository);

  protected final void parseValueResourceFile(@NonNull PathString file, @NonNull RepositoryConfiguration configuration) {
    try (InputStream stream = getInputStream(file)) {
      ResourceSourceFile sourceFile = createResourceSourceFile(file, configuration);
      myParser.setInput(stream, null);

      int event;
      do {
        event = myParser.nextToken();
        int depth = myParser.getDepth();
        if (event == XmlPullParser.START_TAG) {
          if (myParser.getPrefix() != null) {
            continue;
          }
          String tagName = myParser.getName();
          assert depth <= 2; // Deeper tags should be consumed by the createResourceItem method.
          if (depth == 1) {
            if (!tagName.equals(TAG_RESOURCES)) {
              break;
            }
          }
          else if (depth > 1) {
            ResourceType resourceType = getResourceType(tagName, file);
            if (resourceType != null && resourceType != ResourceType.PUBLIC) {
              String resourceName = myParser.getAttributeValue(null, ATTR_NAME);
              if (resourceName != null) {
                validateResourceName(resourceName, resourceType, file);
                BasicValueResourceItemBase item = createResourceItem(resourceType, resourceName, sourceFile);
                addValueResourceItem(item);
              } else {
                // Skip the subtags when the tag of a valid resource type doesn't have a name.
                skipSubTags();
              }
            }
            else {
              skipSubTags();
            }
          }
        }
      } while (event != XmlPullParser.END_DOCUMENT);
    }
    // KXmlParser throws RuntimeException for an undefined prefix and an illegal attribute name.
    catch (IOException | XmlPullParserException | XmlSyntaxException | RuntimeException e) {
      // However if this is cancellation we rethrow.
      ProgressManagerAdapter.throwIfCancellation(e);
      handleParsingError(file, e);
    }

    addValueFileResources();
  }

  @NonNull
  protected ResourceSourceFile createResourceSourceFile(@NonNull PathString file, @NonNull RepositoryConfiguration configuration) {
    return new ResourceSourceFileImpl(getResRelativePath(file), configuration);
  }

  private void addValueResourceItem(@NonNull BasicValueResourceItemBase item) {
    ResourceType resourceType = item.getType();
    // Add attr and styleable resources to intermediate maps to post-process them in the processAttrsAndStyleables
    // method after all resources are loaded.
    if (resourceType == ResourceType.ATTR) {
      addAttr((BasicAttrResourceItem)item, myAttrs);
    }
    else if (resourceType == ResourceType.STYLEABLE) {
      myStyleables.put(item.getName(), (BasicStyleableResourceItem)item);
    }
    else {
      // For compatibility with resource merger code we add value resources first to a file-specific map,
      // then move them to the global resource table. In case when there are multiple definitions of
      // the same resource in a single XML file, this algorithm preserves only the last definition.
      myValueFileResources.put(resourceType, item.getName(), item);
    }
  }

  protected final void addValueFileResources() {
    for (BasicValueResourceItemBase item : myValueFileResources.values()) {
      addResourceItem(item);
    }
    myValueFileResources.clear();
  }

  protected final void parseIdGeneratingResourceFile(@NonNull PathString file, @NonNull RepositoryConfiguration configuration) {
    try (InputStream stream = getInputStream(file)) {
      ResourceSourceFile sourceFile = createResourceSourceFile(file, configuration);
      XmlPullParser parser = new KXmlParser();
      parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
      parser.setInput(stream, null);

      int event;
      do {
        event = parser.nextToken();
        if (event == XmlPullParser.START_TAG) {
          int numAttributes = parser.getAttributeCount();
          for (int i = 0; i < numAttributes; i++) {
            String idValue = parser.getAttributeValue(i);
            if (idValue.startsWith(NEW_ID_PREFIX) && idValue.length() > NEW_ID_PREFIX.length()) {
              String resourceName = idValue.substring(NEW_ID_PREFIX.length());
              addIdResourceItem(resourceName, sourceFile);
            }
          }
        }
      } while (event != XmlPullParser.END_DOCUMENT);
    }
    // KXmlParser throws RuntimeException for an undefined prefix and an illegal attribute name.
    catch (IOException | XmlPullParserException | RuntimeException e) {
      // However if this is cancellation we rethrow.
      ProgressManagerAdapter.throwIfCancellation(e);
      handleParsingError(file, e);
    }

    addValueFileResources();
  }

  protected void handleParsingError(@NonNull PathString file, @NonNull Exception e) {
    LOG.warn("Failed to parse " + file.toString(), e);
  }

  @NonNull
  protected InputStream getInputStream(@NonNull PathString file) throws IOException {
    if (myZipFile == null) {
      Path path = file.toPath();
      Preconditions.checkArgument(path != null);
      return new BufferedInputStream(CancellableFileIo.newInputStream(path));
    }
    else {
      ProgressManagerAdapter.checkCanceled();
      ZipEntry entry = myZipFile.getEntry(file.getPortablePath());
      if (entry == null) {
        throw new NoSuchFileException(file.getPortablePath());
      }
      return new BufferedInputStream(myZipFile.getInputStream(entry));
    }
  }

  protected final void addIdResourceItem(@NonNull String resourceName, @NonNull ResourceSourceFile sourceFile) {
    ResourceVisibility visibility = getVisibility(ResourceType.ID, resourceName);
    BasicValueResourceItem item = new BasicValueResourceItem(ResourceType.ID, resourceName, sourceFile, visibility, null);
    if (!resourceAlreadyDefined(item)) { // Don't create duplicate ID resources.
      addValueResourceItem(item);
    }
  }

  @NonNull
  private BasicFileResourceItem createFileResourceItem(
      @NonNull PathString file, @NonNull ResourceType resourceType, @NonNull RepositoryConfiguration configuration) {
    String resourceName = SdkUtils.fileNameToResourceName(file.getFileName());
    ResourceVisibility visibility = getVisibility(resourceType, resourceName);
    Density density = null;
    if (DensityBasedResourceValue.isDensityBasedResourceType(resourceType)) {
      DensityQualifier densityQualifier = configuration.getFolderConfiguration().getDensityQualifier();
      if (densityQualifier != null) {
        density = densityQualifier.getValue();
      }
    }
    return createFileResourceItem(file, resourceType, resourceName, configuration, visibility, density);
  }

  @NonNull
  protected final BasicFileResourceItem createFileResourceItem(@NonNull PathString file,
                                                               @NonNull ResourceType type,
                                                               @NonNull String name,
                                                               @NonNull RepositoryConfiguration configuration,
                                                               @NonNull ResourceVisibility visibility,
                                                               @Nullable Density density) {
    String relativePath = getResRelativePath(file);
    return density == null ?
           new BasicFileResourceItem(type, name, configuration, visibility, relativePath) :
           new BasicDensityBasedFileResourceItem(type, name, configuration, visibility, relativePath, density);
  }

  @NonNull
  private BasicValueResourceItemBase createResourceItem(
      @NonNull ResourceType type, @NonNull String name, @NonNull ResourceSourceFile sourceFile)
      throws IOException, XmlPullParserException, XmlSyntaxException {
    switch (type) {
      case ARRAY:
        return createArrayItem(name, sourceFile);

      case ATTR:
        return createAttrItem(name, sourceFile);

      case PLURALS:
        return createPluralsItem(name, sourceFile);

      case STRING:
        return createStringItem(type, name, sourceFile, true);

      case STYLE:
        return createStyleItem(name, sourceFile);

      case STYLEABLE:
        return createStyleableItem(name, sourceFile);

      case ANIMATOR:
      case DRAWABLE:
      case INTERPOLATOR:
      case LAYOUT:
      case MENU:
      case MIPMAP:
      case TRANSITION:
        return createFileReferenceItem(type, name, sourceFile);

      default:
        return createStringItem(type, name, sourceFile, false);
    }
  }

  @NonNull
  private BasicArrayResourceItem createArrayItem(@NonNull String name, @NonNull ResourceSourceFile sourceFile)
      throws IOException, XmlPullParserException, XmlSyntaxException {
    String indexValue = myParser.getAttributeValue(TOOLS_URI, ATTR_INDEX);
    ResourceNamespace.Resolver namespaceResolver = myParser.getNamespaceResolver();
    List<String> values = new ArrayList<>();
    forSubTags(TAG_ITEM, () -> {
      String text = myTextExtractor.extractText(myParser, false);
      values.add(text);
    });
    int index = 0;
    if (indexValue != null) {
      try {
        index = Integer.parseUnsignedInt(indexValue);
      }
      catch (NumberFormatException e) {
        throw new XmlSyntaxException(
            "The value of the " + namespaceResolver.prefixToUri(TOOLS_URI) + ':' + ATTR_INDEX + " attribute is not a valid number.",
            myParser, getDisplayName(sourceFile));
      }
      if (index >= values.size()) {
        throw new XmlSyntaxException(
            "The value of the " + namespaceResolver.prefixToUri(TOOLS_URI) + ':' + ATTR_INDEX + " attribute is out of bounds.",
            myParser, getDisplayName(sourceFile));
      }
    }
    ResourceVisibility visibility = getVisibility(ResourceType.ARRAY, name);
    BasicArrayResourceItem item = new BasicArrayResourceItem(name, sourceFile, visibility, values, index);
    item.setNamespaceResolver(namespaceResolver);
    return item;
  }

  @NonNull
  private BasicAttrResourceItem createAttrItem(@NonNull String name, @NonNull ResourceSourceFile sourceFile)
      throws IOException, XmlPullParserException, XmlSyntaxException {
    ResourceNamespace.Resolver namespaceResolver = myParser.getNamespaceResolver();
    ResourceNamespace attrNamespace;
    myUrlParser.parseResourceUrl(name);
    if (myUrlParser.hasNamespacePrefix(ANDROID_NS_NAME)) {
      attrNamespace = ResourceNamespace.ANDROID;
    } else {
      String prefix = myUrlParser.getNamespacePrefix();
      attrNamespace = ResourceNamespace.fromNamespacePrefix(prefix, myNamespace, myParser.getNamespaceResolver());
      if (attrNamespace == null) {
        throw new XmlSyntaxException("Undefined prefix of attr resource name \"" + name + "\"", myParser, getDisplayName(sourceFile));
      }
    }
    name = myUrlParser.getName();

    String description = myParser.getLastComment();
    String groupName = myParser.getAttrGroupComment();
    String formatString = myParser.getAttributeValue(null, ATTR_FORMAT);
    Set<AttributeFormat> formats =
      Strings.isNullOrEmpty(formatString) ? EnumSet.noneOf(AttributeFormat.class) : AttributeFormat.parse(formatString);

    // The average number of enum or flag values is 7 for Android framework, so start with small maps.
    Map<String, Integer> valueMap = Maps.newHashMapWithExpectedSize(8);
    Map<String, String> descriptionMap = Maps.newHashMapWithExpectedSize(8);
    forSubTags(null, () -> {
      if (myParser.getPrefix() == null) {
        String tagName = myParser.getName();
        AttributeFormat format =
            tagName.equals(TAG_ENUM) ? AttributeFormat.ENUM : tagName.equals(TAG_FLAG) ? AttributeFormat.FLAGS : null;
        if (format != null) {
          formats.add(format);
          String valueName = myParser.getAttributeValue(null, ATTR_NAME);
          if (valueName != null) {
            String valueDescription = myParser.getLastComment();
            if (valueDescription != null) {
              descriptionMap.put(valueName, valueDescription);
            }
            String value = myParser.getAttributeValue(null, ATTR_VALUE);
            Integer numericValue = null;
            if (value != null) {
              try {
                // Integer.decode/parseInt can't deal with hex value > 0x7FFFFFFF so we use Long.decode instead.
                numericValue = Long.decode(value).intValue();
              }
              catch (NumberFormatException ignored) {
              }
            }
            valueMap.put(valueName, numericValue);
          }
        }
      }
    });

    BasicAttrResourceItem item;
    if (attrNamespace.equals(myNamespace)) {
      ResourceVisibility visibility = getVisibility(ResourceType.ATTR, name);
      item = new BasicAttrResourceItem(name, sourceFile, visibility, description, groupName, formats, valueMap, descriptionMap);
    }
    else {
      item = new BasicForeignAttrResourceItem(attrNamespace, name, sourceFile, description, groupName, formats, valueMap, descriptionMap);
    }

    item.setNamespaceResolver(namespaceResolver);
    return item;
  }

  @NonNull
  private BasicPluralsResourceItem createPluralsItem(@NonNull String name, @NonNull ResourceSourceFile sourceFile)
      throws IOException, XmlPullParserException, XmlSyntaxException {
    String defaultQuantity = myParser.getAttributeValue(TOOLS_URI, ATTR_QUANTITY);
    ResourceNamespace.Resolver namespaceResolver = myParser.getNamespaceResolver();
    EnumMap<Arity, String> values = new EnumMap<>(Arity.class);
    forSubTags(TAG_ITEM, () -> {
      String quantityValue = myParser.getAttributeValue(null, ATTR_QUANTITY);
      if (quantityValue != null) {
        Arity quantity = Arity.getEnum(quantityValue);
        if (quantity != null) {
          String text = myTextExtractor.extractText(myParser, false);
          values.put(quantity, text);
        }
      }
    });
    Arity defaultArity = null;
    if (defaultQuantity != null) {
      defaultArity = Arity.getEnum(defaultQuantity);
      if (defaultArity == null || !values.containsKey(defaultArity)) {
        throw new XmlSyntaxException(
            "Invalid value of the " + namespaceResolver.prefixToUri(TOOLS_URI) + ':' + ATTR_QUANTITY + " attribute.", myParser,
            getDisplayName(sourceFile));
      }
    }
    ResourceVisibility visibility = getVisibility(ResourceType.PLURALS, name);
    BasicPluralsResourceItem item = new BasicPluralsResourceItem(name, sourceFile, visibility, values, defaultArity);
    item.setNamespaceResolver(namespaceResolver);
    return item;
  }

  @NonNull
  private BasicValueResourceItem createStringItem(
      @NonNull ResourceType type, @NonNull String name, @NonNull ResourceSourceFile sourceFile, boolean withRowXml)
      throws IOException, XmlPullParserException {
    ResourceNamespace.Resolver namespaceResolver = myParser.getNamespaceResolver();
    String text = type == ResourceType.ID ? null : myTextExtractor.extractText(myParser, withRowXml);
    String rawXml = type == ResourceType.ID ? null : myTextExtractor.getRawXml();
    assert withRowXml || rawXml == null; // Text extractor doesn't extract raw XML unless asked to do it.
    ResourceVisibility visibility = getVisibility(type, name);
    BasicValueResourceItem item = rawXml == null ?
                                  new BasicValueResourceItem(type, name, sourceFile, visibility, text) :
                                  new BasicTextValueResourceItem(type, name, sourceFile, visibility, text, rawXml);
    item.setNamespaceResolver(namespaceResolver);
    return item;
  }

  @NonNull
  private BasicStyleResourceItem createStyleItem(@NonNull String name, @NonNull ResourceSourceFile sourceFile)
      throws IOException, XmlPullParserException {
    ResourceNamespace.Resolver namespaceResolver = myParser.getNamespaceResolver();
    String parentStyle = myParser.getAttributeValue(null, ATTR_PARENT);
    if (parentStyle != null && !parentStyle.isEmpty()) {
      myUrlParser.parseResourceUrl(parentStyle);
      parentStyle = myUrlParser.getQualifiedName();
    }
    List<StyleItemResourceValue> styleItems = new ArrayList<>();
    forSubTags(TAG_ITEM, () -> {
      ResourceNamespace.Resolver itemNamespaceResolver = myParser.getNamespaceResolver();
      String itemName = myParser.getAttributeValue(null, ATTR_NAME);
      if (itemName != null) {
        String text = myTextExtractor.extractText(myParser, false);
        StyleItemResourceValueImpl styleItem =
            new StyleItemResourceValueImpl(myNamespace, itemName, text, sourceFile.getRepository().getLibraryName());
        styleItem.setNamespaceResolver(itemNamespaceResolver);
        styleItems.add(styleItem);
      }
    });
    ResourceVisibility visibility = getVisibility(ResourceType.STYLE, name);
    BasicStyleResourceItem item = new BasicStyleResourceItem(name, sourceFile, visibility, parentStyle, styleItems);
    item.setNamespaceResolver(namespaceResolver);
    return item;
  }

  @NonNull
  private BasicStyleableResourceItem createStyleableItem(@NonNull String name, @NonNull ResourceSourceFile sourceFile)
      throws IOException, XmlPullParserException {
    ResourceNamespace.Resolver namespaceResolver = myParser.getNamespaceResolver();
    List<AttrResourceValue> attrs = new ArrayList<>();
    forSubTags(TAG_ATTR, () -> {
      String attrName = myParser.getAttributeValue(null, ATTR_NAME);
      if (attrName != null) {
        try {
          BasicAttrResourceItem attr = createAttrItem(attrName, sourceFile);
          // Mimic behavior of AAPT2 and put an attr reference inside a styleable resource.
          attrs.add(attr.getFormats().isEmpty() ? attr : attr.createReference());

          // Don't create top-level attr resources in a foreign namespace, or for attr references in the res-auto namespace.
          // The second condition is determined by the fact that the attr in the res-auto namespace may have an explicit definition
          // outside of this resource repository.
          if (attr.getNamespace().equals(myNamespace) && (myNamespace != ResourceNamespace.RES_AUTO || !attr.getFormats().isEmpty())) {
            addAttr(attr, myAttrCandidates);
          }
        }
        catch (XmlSyntaxException e) {
          LOG.error(e);
        }
      }
    });
    // AAPT2 treats all styleable resources as public.
    // See https://android.googlesource.com/platform/frameworks/base/+/master/tools/aapt2/ResourceParser.cpp#1539
    BasicStyleableResourceItem item = new BasicStyleableResourceItem(name, sourceFile, ResourceVisibility.PUBLIC, attrs);
    item.setNamespaceResolver(namespaceResolver);
    return item;
  }

  private static void addAttr(@NonNull BasicAttrResourceItem attr, @NonNull ListMultimap<String, BasicAttrResourceItem> map) {
    List<BasicAttrResourceItem> attrs = map.get(attr.getName());
    int i = findResourceWithSameNameAndConfiguration(attr, attrs);
    if (i >= 0) {
      // Found a matching attr definition.
      BasicAttrResourceItem existing = attrs.get(i);
      if (!attr.getFormats().isEmpty()) {
        if (existing.getFormats().isEmpty()) {
          attrs.set(i, attr); // Use the new attr since it contains more information than the existing one.
        }
        else if (!attr.getFormats().equals(existing.getFormats())) {
          // Both, the existing and the new attr contain formats, but they are not the same.
          // Assign union of formats to both attr definitions.
          if (attr.getFormats().containsAll(existing.getFormats())) {
            existing.setFormats(attr.getFormats());
          }
          else if (existing.getFormats().containsAll(attr.getFormats())) {
            attr.setFormats(existing.getFormats());
          }
          else {
            Set<AttributeFormat> formats = EnumSet.copyOf(attr.getFormats());
            formats.addAll(existing.getFormats());
            formats = ImmutableSet.copyOf(formats);
            attr.setFormats(formats);
            existing.setFormats(formats);
          }
        }
      }
      if (existing.getFormats().isEmpty() && !attr.getFormats().isEmpty()) {
        attrs.set(i, attr); // Use the new attr since it contains more information than the existing one.
      }
    }
    else {
      attrs.add(attr);
    }
  }

  /**
   * Adds attr definitions from {@link #myAttrs}, and attr definition candidates from {@link #myAttrCandidates}
   * if they don't match the attr definitions present in {@link #myAttrs}.
   */
  private void processAttrsAndStyleables() {
    for (BasicAttrResourceItem attr : myAttrs.values()) {
      addAttrWithAdjustedFormats(attr);
    }

    for (BasicAttrResourceItem attr : myAttrCandidates.values()) {
      List<BasicAttrResourceItem> attrs = myAttrs.get(attr.getName());
      int i = findResourceWithSameNameAndConfiguration(attr, attrs);
      if (i < 0) {
        addAttrWithAdjustedFormats(attr);
      }
    }

    // Resolve attribute references where it can be done without loosing any data to reduce resource memory footprint.
    for (BasicStyleableResourceItem styleable : myStyleables.values()) {
      addResourceItem(resolveAttrReferences(styleable));
    }
  }

  /**
   * Returns a styleable with attr references replaced by attr definitions returned by
   * the {@link BasicStyleableResourceItem#getCanonicalAttr} method.
   */
  @NonNull
  public static BasicStyleableResourceItem resolveAttrReferences(@NonNull BasicStyleableResourceItem styleable) {
    ResourceRepository repository = styleable.getRepository();
    List<AttrResourceValue> attributes = styleable.getAllAttributes();
    List<AttrResourceValue> resolvedAttributes = null;
    for (int i = 0; i < attributes.size(); i++) {
      AttrResourceValue attr = attributes.get(i);
      AttrResourceValue canonicalAttr = BasicStyleableResourceItem.getCanonicalAttr(attr, repository);
      if (canonicalAttr != attr) {
        if (resolvedAttributes == null) {
          resolvedAttributes = new ArrayList<>(attributes.size());
          for (int j = 0; j < i; j++) {
            resolvedAttributes.add(attributes.get(j));
          }
        }
        resolvedAttributes.add(canonicalAttr);
      }
      else if (resolvedAttributes != null) {
        resolvedAttributes.add(attr);
      }
    }

    if (resolvedAttributes != null) {
      ResourceNamespace.Resolver namespaceResolver = styleable.getNamespaceResolver();
      styleable =
          new BasicStyleableResourceItem(styleable.getName(), styleable.getSourceFile(), styleable.getVisibility(), resolvedAttributes);
      styleable.setNamespaceResolver(namespaceResolver);
    }
    return styleable;
  }

  private void addAttrWithAdjustedFormats(@NonNull BasicAttrResourceItem attr) {
    if (attr.getFormats().isEmpty()) {
      attr = new BasicAttrResourceItem(attr.getName(), attr.getSourceFile(), attr.getVisibility(), attr.getDescription(),
                                       attr.getGroupName(), DEFAULT_ATTR_FORMATS, Collections.emptyMap(), Collections.emptyMap());
    }
    addResourceItem(attr);
  }

  /**
   * Checks if resource with the same name, type and configuration has already been defined.
   *
   * @param resource the resource to check
   * @return true if a matching resource already exists
   */
  private static boolean resourceAlreadyDefined(@NonNull BasicResourceItemBase resource) {
    ResourceRepository repository = resource.getRepository();
    List<ResourceItem> items = repository.getResources(resource.getNamespace(), resource.getType(), resource.getName());
    return findResourceWithSameNameAndConfiguration(resource, items) >= 0;
  }

  private static int findResourceWithSameNameAndConfiguration(@NonNull ResourceItem resource, @NonNull List<? extends ResourceItem> items) {
    for (int i = 0; i < items.size(); i++) {
      ResourceItem item = items.get(i);
      if (item.getConfiguration().equals(resource.getConfiguration())) {
        return i;
      }
    }
    return -1;
  }

  @NonNull
  private BasicValueResourceItem createFileReferenceItem(
      @NonNull ResourceType type, @NonNull String name, @NonNull ResourceSourceFile sourceFile)
      throws IOException, XmlPullParserException {
    ResourceNamespace.Resolver namespaceResolver = myParser.getNamespaceResolver();
    String text = myTextExtractor.extractText(myParser, false).trim();
    if (!text.isEmpty() && !text.startsWith(PREFIX_RESOURCE_REF) && !text.startsWith(PREFIX_THEME_REF)) {
      text = text.replace('/', File.separatorChar);
    }
    ResourceVisibility visibility = getVisibility(type, name);
    BasicValueResourceItem item = new BasicValueResourceItem(type, name, sourceFile, visibility, text);
    item.setNamespaceResolver(namespaceResolver);
    return item;
  }

  @Nullable
  private ResourceType getResourceType(@NonNull String tagName, @NonNull PathString file) throws XmlSyntaxException {
    ResourceType type = ResourceType.fromXmlTagName(tagName);

    if (type == null) {
      if (TAG_EAT_COMMENT.equals(tagName) || TAG_SKIP.equals(tagName)) {
        return null;
      }

      if (TAG_JAVA_SYMBOL.equals(tagName)) {
        // java-symbol is only used within framework and does not provide any public
        // information so we can safely ignore it.
        return null;
      }

      if (tagName.equals(TAG_ITEM)) {
        String typeAttr = myParser.getAttributeValue(null, ATTR_TYPE);
        if (typeAttr != null) {
          type = ResourceType.fromClassName(typeAttr);
          if (type != null) {
            return type;
          }

          LOG.warn("Unrecognized type attribute \"" + typeAttr + "\" at " + getDisplayName(file) + " line " + myParser.getLineNumber());
        }
      }
      else {
        LOG.warn("Unrecognized tag name \"" + tagName + "\" at " + getDisplayName(file) + " line " + myParser.getLineNumber());
      }
    }

    return type;
  }

  /**
   * If {@code tagName} is null, calls {@code subtagVisitor.visitTag()} for every subtag of the current tag.
   * If {@code tagName} is not null, calls {@code subtagVisitor.visitTag()} for every subtag of the current tag
   * which name doesn't have a prefix and matches {@code tagName}.
   */
  private void forSubTags(@Nullable String tagName, @NonNull XmlTagVisitor subtagVisitor) throws IOException, XmlPullParserException {
    int elementDepth = myParser.getDepth();
    int event;
    do {
      event = myParser.nextToken();
      if (event == XmlPullParser.START_TAG && (tagName == null || tagName.equals(myParser.getName()) && myParser.getPrefix() == null)) {
        subtagVisitor.visitTag();
      }
    } while (event != XmlPullParser.END_DOCUMENT && (event != XmlPullParser.END_TAG || myParser.getDepth() > elementDepth));
  }

  /**
   * Skips all subtags of the current tag. When the method returns, the parser is positioned at the end tag
   * of the current element.
   */
  private void skipSubTags() throws IOException, XmlPullParserException {
    int elementDepth = myParser.getDepth();
    int event;
    do {
      event = myParser.nextToken();
    } while (event != XmlPullParser.END_DOCUMENT && (event != XmlPullParser.END_TAG || myParser.getDepth() > elementDepth));
  }

  private void validateResourceName(@NonNull String resourceName, @NonNull ResourceType resourceType, @NonNull PathString file)
      throws XmlSyntaxException {
    String error = ValueResourceNameValidator.getErrorText(resourceName, resourceType);
    if (error != null) {
      throw new XmlSyntaxException(error, myParser, getDisplayName(file));
    }
  }

  @NonNull
  private String getDisplayName(@NonNull PathString file) {
    return file.isAbsolute() ? file.getNativePath() : file.getPortablePath() + " in " + myResourceDirectoryOrFile.toString();
  }

  @NonNull
  private String getDisplayName(@NonNull ResourceSourceFile sourceFile) {
    String relativePath = sourceFile.getRelativePath();
    Preconditions.checkArgument(relativePath != null);
    return getDisplayName(new PathString(relativePath));
  }

  @NonNull
  protected final ResourceVisibility getVisibility(@NonNull ResourceType resourceType, @NonNull String resourceName) {
    Set<String> names = myPublicResources.get(resourceType);
    return names != null && names.contains(getKeyForVisibilityLookup(resourceName)) ? ResourceVisibility.PUBLIC : myDefaultVisibility;
  }

  /**
   * Transforms the given resource name to a key for lookup in myPublicResources.
   */
  @NonNull
  protected String getKeyForVisibilityLookup(@NonNull String resourceName) {
    // In public.txt all resource names are transformed by replacing dots, colons and dashes with underscores.
    return ResourcesUtil.resourceNameToFieldName(resourceName);
  }

  @NonNull
  protected final String getResRelativePath(@NonNull PathString file) {
    if (file.isAbsolute()) {
      return myResourceDirectoryOrFilePath.relativize(file).getPortablePath();
    }

    // The path is already relative, drop the first "res" segment.
    assert file.getNameCount() != 0;
    assert file.segment(0).equals("res");
    return file.subpath(1, file.getNameCount()).getPortablePath();
  }

  private static boolean isZipArchive(@NonNull Path resourceDirectoryOrFile) {
    String filename = resourceDirectoryOrFile.getFileName().toString();
    return SdkUtils.endsWithIgnoreCase(filename, DOT_AAR) ||
           SdkUtils.endsWithIgnoreCase(filename, DOT_JAR) ||
           SdkUtils.endsWithIgnoreCase(filename, DOT_ZIP);
  }

  @NonNull
  public static String portableFileName(@NonNull String fileName) {
    return fileName.replace(File.separatorChar, '/');
  }

  private interface XmlTagVisitor {
    /** Is called when the parser is positioned at a {@link XmlPullParser#START_TAG}. */
    void visitTag() throws IOException, XmlPullParserException;
  }

  /**
   * Information about a resource folder.
   */
  protected static class FolderInfo {
    @NonNull public final ResourceFolderType folderType;
    @NonNull public final FolderConfiguration configuration;
    @Nullable public final ResourceType resourceType;
    public final boolean isIdGenerating;

    private FolderInfo(@NonNull ResourceFolderType folderType,
                       @NonNull FolderConfiguration configuration,
                       @Nullable ResourceType resourceType,
                       boolean isIdGenerating) {
      this.configuration = configuration;
      this.resourceType = resourceType;
      this.folderType = folderType;
      this.isIdGenerating = isIdGenerating;
    }

    /**
     * Returns a FolderInfo for the given folder name.
     *
     * @param folderName the name of a resource folder
     * @param folderConfigCache the cache of FolderConfiguration objects keyed by qualifier strings
     * @return the FolderInfo object, or null if folderName is not a valid name of a resource folder
     */
    @Nullable
    public static FolderInfo create(@NonNull String folderName, @NonNull Map<String, FolderConfiguration> folderConfigCache) {
      ResourceFolderType folderType = ResourceFolderType.getFolderType(folderName);
      if (folderType == null) {
        return null;
      }

      String qualifier = FolderConfiguration.getQualifier(folderName);
      FolderConfiguration config = folderConfigCache.computeIfAbsent(qualifier, FolderConfiguration::getConfigForQualifierString);
      if (config == null) {
        return null;
      }
      config.normalizeByRemovingRedundantVersionQualifier();

      ResourceType resourceType;
      boolean isIdGenerating;
      if (folderType == ResourceFolderType.VALUES) {
        resourceType = null;
        isIdGenerating = false;
      }
      else {
        resourceType = FolderTypeRelationship.getNonIdRelatedResourceType(folderType);
        isIdGenerating = FolderTypeRelationship.isIdGeneratingFolderType(folderType);
      }

      return new FolderInfo(folderType, config, resourceType, isIdGenerating);
    }
  }

  private static class ResourceFileCollector implements FileVisitor<Path> {
    @NonNull final List<PathString> resourceFiles = new ArrayList<>();
    @NonNull final List<IOException> ioErrors = new ArrayList<>();
    @NonNull final FileFilter fileFilter;

    private ResourceFileCollector(@NonNull FileFilter filter) {
      fileFilter = filter;
    }

    @Override
    @NonNull
    public FileVisitResult preVisitDirectory(@NonNull Path dir, @NonNull BasicFileAttributes attrs) {
      if (fileFilter.isIgnored(dir, attrs)) {
        return FileVisitResult.SKIP_SUBTREE;
      }
      return FileVisitResult.CONTINUE;
    }

    @Override
    @NonNull
    public FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs) {
      if (fileFilter.isIgnored(file, attrs)) {
        return FileVisitResult.SKIP_SUBTREE;
      }
      resourceFiles.add(new PathString(file));
      return FileVisitResult.CONTINUE;
    }

    @Override
    @NonNull
    public FileVisitResult visitFileFailed(@NonNull Path file, @NonNull IOException exc) {
      ioErrors.add(exc);
      return FileVisitResult.CONTINUE;
    }

    @Override
    @NonNull
    public FileVisitResult postVisitDirectory(@NonNull Path dir, @Nullable IOException exc) {
      return FileVisitResult.CONTINUE;
    }
  }

  private static class XmlTextExtractor {
    @NonNull private final StringBuilder text = new StringBuilder();
    @NonNull private final StringBuilder rawXml = new StringBuilder();
    @NonNull private final Deque<Boolean> textInclusionState = new ArrayDeque<>();
    private boolean nontrivialRawXml;

    @NonNull
    String extractText(@NonNull XmlPullParser parser, boolean withRawXml) throws IOException, XmlPullParserException {
      text.setLength(0);
      rawXml.setLength(0);
      textInclusionState.clear();
      nontrivialRawXml = false;

      int elementDepth = parser.getDepth();
      int event;
      loop:
      do {
        event = parser.nextToken();
        switch (event) {
          case XmlPullParser.START_TAG: {
            String tagName = parser.getName();
            if (XLIFF_G_TAG.equals(tagName) && isXliffNamespace(parser.getNamespace())) {
              boolean includeNestedText = getTextInclusionState();
              String example = parser.getAttributeValue(null, ATTR_EXAMPLE);
              if (example != null) {
                text.append('(').append(example).append(')');
                includeNestedText = false;
              }
              else {
                String id = parser.getAttributeValue(null, ATTR_ID);
                if (id != null && !id.equals("id")) {
                  text.append('$').append('{').append(id).append('}');
                  includeNestedText = false;
                }
              }
              textInclusionState.addLast(includeNestedText);
            }
            if (withRawXml) {
              nontrivialRawXml = true;
              rawXml.append('<');
              String prefix = parser.getPrefix();
              if (prefix != null) {
                rawXml.append(prefix).append(':');
              }
              rawXml.append(tagName);
              int numAttr = parser.getAttributeCount();
              for (int i = 0; i < numAttr; i++) {
                rawXml.append(' ');
                String attributePrefix = parser.getAttributePrefix(i);
                if (attributePrefix != null) {
                  rawXml.append(attributePrefix).append(':');
                }
                rawXml.append(parser.getAttributeName(i)).append('=').append('"');
                XmlUtils.appendXmlAttributeValue(rawXml, parser.getAttributeValue(i));
                rawXml.append('"');
              }
              rawXml.append('>');
            }
            break;
          }

          case XmlPullParser.END_TAG: {
            if (parser.getDepth() <= elementDepth) {
              break loop;
            }
            String tagName = parser.getName();
            if (withRawXml) {
              rawXml.append('<').append('/');
              String prefix = parser.getPrefix();
              if (prefix != null) {
                rawXml.append(prefix).append(':');
              }
              rawXml.append(tagName).append('>');
            }
            if (XLIFF_G_TAG.equals(tagName) && isXliffNamespace(parser.getNamespace())) {
              textInclusionState.removeLast();
            }
            break;
          }

          case XmlPullParser.ENTITY_REF:
          case XmlPullParser.TEXT: {
            String textPiece = parser.getText();
            if (getTextInclusionState()) {
              text.append(textPiece);
            }
            if (withRawXml) {
              rawXml.append(textPiece);
            }
            break;
          }

          case XmlPullParser.CDSECT: {
            String textPiece = parser.getText();
            if (getTextInclusionState()) {
              text.append(textPiece);
            }
            if (withRawXml) {
              nontrivialRawXml = true;
              rawXml.append("<![CDATA[").append(textPiece).append("]]>");
            }
            break;
          }
        }
      } while (event != XmlPullParser.END_DOCUMENT);

      return ValueXmlHelper.unescapeResourceString(text.toString(), false, true);
    }

    private boolean getTextInclusionState() {
      return textInclusionState.isEmpty() || textInclusionState.getLast();
    }

    @Nullable
    String getRawXml() {
      return nontrivialRawXml ? rawXml.toString() : null;
    }

    private static boolean isXliffNamespace(@Nullable String namespaceUri) {
      return namespaceUri != null && namespaceUri.startsWith(XLIFF_NAMESPACE_PREFIX);
    }
  }

  private static class XmlSyntaxException extends Exception {
    XmlSyntaxException(@NonNull String error, @NonNull XmlPullParser parser, @NonNull String filename) {
      super(error + " at " + filename + " line " + parser.getLineNumber());
    }
  }
}
