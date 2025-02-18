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
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.utils.Base128InputStream;
import com.android.utils.Base128OutputStream;
import com.android.utils.HashCodes;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.io.IOException;
import java.util.Objects;

/**
 * Resource item representing a value resource, e.g. a string or a color.
 */
public class BasicValueResourceItem extends BasicValueResourceItemBase {
  @Nullable private final String myValue;

  /**
   * Initializes the resource.
   *
   * @param type the type of the resource
   * @param name the name of the resource
   * @param sourceFile the source file containing definition of the resource
   * @param visibility the visibility of the resource
   * @param value the value associated with the resource
   */
  public BasicValueResourceItem(@NonNull ResourceType type,
                                @NonNull String name,
                                @NonNull ResourceSourceFile sourceFile,
                                @NonNull ResourceVisibility visibility,
                                @Nullable String value) {
    super(type, name, sourceFile, visibility);
    myValue = value;
  }

  @Override
  @Nullable
  public String getValue() {
    return myValue;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    BasicValueResourceItem other = (BasicValueResourceItem) obj;
    return Objects.equals(myValue, other.myValue);
  }

  @Override
  public int hashCode() {
    return HashCodes.mix(super.hashCode(), Objects.hashCode(myValue));
  }

  @Override
  public void serialize(@NonNull Base128OutputStream stream,
                        @NonNull Object2IntMap<String> configIndexes,
                        @NonNull Object2IntMap<ResourceSourceFile> sourceFileIndexes,
                        @NonNull Object2IntMap<ResourceNamespace.Resolver> namespaceResolverIndexes) throws IOException {
    super.serialize(stream, configIndexes, sourceFileIndexes, namespaceResolverIndexes);
    stream.writeString(myValue);
    String rawXmlValue = getRawXmlValue();
    stream.writeString(Objects.equals(rawXmlValue, myValue) ? null : rawXmlValue);
  }

  /**
   * Creates a BasicValueResourceItem by reading its contents from the given stream.
   */
  @NonNull
  static BasicValueResourceItem deserialize(@NonNull Base128InputStream stream,
                                            @NonNull ResourceType resourceType,
                                            @NonNull String name,
                                            @NonNull ResourceVisibility visibility,
                                            @NonNull ResourceSourceFile sourceFile,
                                            @NonNull ResourceNamespace.Resolver resolver) throws IOException {
    String value = stream.readString();
    String rawXmlValue = stream.readString();
    BasicValueResourceItem item = rawXmlValue == null ?
                                  new BasicValueResourceItem(resourceType, name, sourceFile, visibility, value) :
                                  new BasicTextValueResourceItem(resourceType, name, sourceFile, visibility, value, rawXmlValue);
    item.setNamespaceResolver(resolver);
    return item;
  }
}
