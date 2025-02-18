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
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.StyleItemResourceValue;
import com.android.ide.common.rendering.api.StyleItemResourceValueImpl;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.tools.environment.Logger;
import com.android.utils.Base128InputStream;
import com.android.utils.Base128InputStream.StreamFormatException;
import com.android.utils.Base128OutputStream;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resource item representing a style resource.
 */
public final class BasicStyleResourceItem extends BasicValueResourceItemBase implements StyleResourceValue {
  private static final Logger LOG = Logger.getInstance(BasicStyleResourceItem.class);

  @Nullable private final String myParentStyle;
  /** Style items keyed by the namespace and the name of the attribute they define. */
  @NonNull private final Table<ResourceNamespace, String, StyleItemResourceValue> myStyleItemTable;

  /**
   * Initializes the resource.
   *
   * @param name the name of the resource
   * @param sourceFile the source file containing definition of the resource
   * @param visibility the visibility of the resource
   * @param parentStyle the parent style reference (package:type/entry)
   * @param styleItems the items of the style
   */
  public BasicStyleResourceItem(@NonNull String name,
                                @NonNull ResourceSourceFile sourceFile,
                                @NonNull ResourceVisibility visibility,
                                @Nullable String parentStyle,
                                @NonNull Collection<StyleItemResourceValue> styleItems) {
    super(ResourceType.STYLE, name, sourceFile, visibility);
    myParentStyle = parentStyle;
    ImmutableTable.Builder<ResourceNamespace, String, StyleItemResourceValue> tableBuilder = ImmutableTable.builder();
    Map<ResourceReference, StyleItemResourceValue> duplicateCheckMap = new HashMap<>();
    for (StyleItemResourceValue styleItem : styleItems) {
      ResourceReference attr = styleItem.getAttr();
      if (attr != null) {
        // Check for duplicate style item definitions. Such duplicate definitions are present in the framework resources.
        StyleItemResourceValue previouslyDefined = duplicateCheckMap.put(attr, styleItem);
        if (previouslyDefined == null) {
          tableBuilder.put(attr.getNamespace(), attr.getName(), styleItem);
        }
        else if (!previouslyDefined.equals(styleItem)) {
          LOG.warn("Conflicting definitions of \"" + styleItem.getAttrName() + "\" in style \"" + name + "\"");
        }
      }
    }
    myStyleItemTable = tableBuilder.build();
  }

  @Override
  @Nullable
  public String getParentStyleName() {
    return myParentStyle;
  }

  @Override
  @Nullable
  public StyleItemResourceValue getItem(@NonNull ResourceNamespace namespace, @NonNull String name) {
    return myStyleItemTable.get(namespace, name);
  }

  @Override
  @Nullable
  public StyleItemResourceValue getItem(@NonNull ResourceReference attr) {
    if (attr.getResourceType() != ResourceType.ATTR) {
      return null;
    }
    return myStyleItemTable.get(attr.getNamespace(), attr.getName());
  }

  @Override
  @NonNull
  public Collection<StyleItemResourceValue> getDefinedItems() {
    return myStyleItemTable.values();
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    BasicStyleResourceItem other = (BasicStyleResourceItem) obj;
    return Objects.equals(myParentStyle, other.myParentStyle) && myStyleItemTable.equals(other.myStyleItemTable);
  }

  @Override
  public void serialize(@NonNull Base128OutputStream stream,
                        @NonNull Object2IntMap<String> configIndexes,
                        @NonNull Object2IntMap<ResourceSourceFile> sourceFileIndexes,
                        @NonNull Object2IntMap<ResourceNamespace.Resolver> namespaceResolverIndexes) throws IOException {
    super.serialize(stream, configIndexes, sourceFileIndexes, namespaceResolverIndexes);
    stream.writeString(myParentStyle);
    stream.writeInt(myStyleItemTable.size());
    for (StyleItemResourceValue styleItem : myStyleItemTable.values()) {
      stream.writeString(styleItem.getAttrName());
      stream.writeString(styleItem.getValue());
      int index = namespaceResolverIndexes.getInt(styleItem.getNamespaceResolver());
      assert index >= 0;
      stream.writeInt(index);
    }
  }

  /**
   * Creates a BasicStyleResourceItem by reading its contents from the given stream.
   */
  @NonNull
  static BasicStyleResourceItem deserialize(@NonNull Base128InputStream stream,
                                            @NonNull String name,
                                            @NonNull ResourceVisibility visibility,
                                            @NonNull ResourceSourceFile sourceFile,
                                            @NonNull ResourceNamespace.Resolver resolver,
                                            @NonNull List<ResourceNamespace.Resolver> namespaceResolvers) throws IOException {
    LoadableResourceRepository repository = sourceFile.getRepository();
    ResourceNamespace namespace = repository.getNamespace();
    String libraryName = repository.getLibraryName();
    String parentStyle = stream.readString();
    int n = stream.readInt();
    List<StyleItemResourceValue> styleItems = n == 0 ? Collections.emptyList() : new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      String attrName = stream.readString();
      if (attrName == null) {
        throw StreamFormatException.invalidFormat();
      }
      String value = stream.readString();
      ResourceNamespace.Resolver itemResolver = namespaceResolvers.get(stream.readInt());
      StyleItemResourceValueImpl styleItem = new StyleItemResourceValueImpl(namespace, attrName, value, libraryName);
      styleItem.setNamespaceResolver(itemResolver);
      styleItems.add(styleItem);
    }
    BasicStyleResourceItem item = new BasicStyleResourceItem(name, sourceFile, visibility, parentStyle, styleItems);
    item.setNamespaceResolver(resolver);
    return item;
  }
}
