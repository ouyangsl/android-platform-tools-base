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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.util.PathString;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.utils.Base128InputStream;
import com.android.utils.Base128OutputStream;
import com.android.utils.HashCodes;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Base class for value resource items. */
public abstract class BasicValueResourceItemBase extends BasicResourceItemBase {
  @NonNull private final ResourceSourceFile mySourceFile;
  @NonNull private ResourceNamespace.Resolver myNamespaceResolver = ResourceNamespace.Resolver.EMPTY_RESOLVER;

  /**
   * Initializes the resource.
   *
   * @param type the type of the resource
   * @param name the name of the resource
   * @param sourceFile the source file containing definition of the resource
   * @param visibility the visibility of the resource
   */
  public BasicValueResourceItemBase(@NonNull ResourceType type,
                                    @NonNull String name,
                                    @NonNull ResourceSourceFile sourceFile,
                                    @NonNull ResourceVisibility visibility) {
    super(type, name, visibility);
    mySourceFile = sourceFile;
  }

  @Override
  @Nullable
  public String getValue() {
    return null;
  }

  @Override
  public final boolean isFileBased() {
    return false;
  }

  @Override
  @NonNull
  public final RepositoryConfiguration getRepositoryConfiguration() {
    return mySourceFile.getConfiguration();
  }

  @Override
  @NonNull
  public final ResourceNamespace.Resolver getNamespaceResolver() {
    return myNamespaceResolver;
  }

  public final void setNamespaceResolver(@NonNull ResourceNamespace.Resolver resolver) {
    myNamespaceResolver = resolver;
  }

  @Override
  @Nullable
  public final PathString getSource() {
    return getOriginalSource();
  }

  @Override
  @Nullable
  public final PathString getOriginalSource() {
    String sourcePath = mySourceFile.getRelativePath();
    return sourcePath == null ? null : getRepository().getOriginalSourceFile(sourcePath, false);
  }

  @NonNull
  public final ResourceSourceFile getSourceFile() {
    return mySourceFile;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    BasicValueResourceItemBase other = (BasicValueResourceItemBase)obj;
    return Objects.equals(mySourceFile, other.mySourceFile);
  }

  @Override
  public int hashCode() {
    return HashCodes.mix(super.hashCode(), Objects.hashCode(mySourceFile));
  }

  @Override
  public void serialize(@NonNull Base128OutputStream stream,
                        @NonNull Object2IntMap<String> configIndexes,
                        @NonNull Object2IntMap<ResourceSourceFile> sourceFileIndexes,
                        @NonNull Object2IntMap<ResourceNamespace.Resolver> namespaceResolverIndexes) throws IOException {
    super.serialize(stream, configIndexes, sourceFileIndexes, namespaceResolverIndexes);
    int index = sourceFileIndexes.getInt(mySourceFile);
    assert index >= 0;
    stream.writeInt(index);
    index = namespaceResolverIndexes.getInt(myNamespaceResolver);
    assert index >= 0;
    stream.writeInt(index);
  }

  /**
   * Creates a resource item by reading its contents from the given stream.
   */
  @NonNull
  static BasicValueResourceItemBase deserialize(@NonNull Base128InputStream stream,
                                                @NonNull ResourceType resourceType,
                                                @NonNull String name,
                                                @NonNull ResourceVisibility visibility,
                                                @NonNull List<RepositoryConfiguration> configurations,
                                                @NonNull List<ResourceSourceFile> sourceFiles,
                                                @NonNull List<ResourceNamespace.Resolver> namespaceResolvers) throws IOException {
    ResourceSourceFile sourceFile = sourceFiles.get(stream.readInt());
    ResourceNamespace.Resolver resolver = namespaceResolvers.get(stream.readInt());

    switch (resourceType) {
      case ARRAY:
        return BasicArrayResourceItem.deserialize(stream, name, visibility, sourceFile, resolver);

      case ATTR:
        return BasicAttrResourceItem.deserialize(stream, name, visibility, sourceFile, resolver);

      case PLURALS:
        return BasicPluralsResourceItem.deserialize(stream, name, visibility, sourceFile, resolver);

      case STYLE:
        return BasicStyleResourceItem.deserialize(stream, name, visibility, sourceFile, resolver, namespaceResolvers);

      case STYLEABLE:
        return BasicStyleableResourceItem.deserialize(
            stream, name, visibility, sourceFile, resolver, configurations, sourceFiles, namespaceResolvers);

      default:
        return BasicValueResourceItem.deserialize(stream, resourceType, name, visibility, sourceFile, resolver);
    }
  }
}
