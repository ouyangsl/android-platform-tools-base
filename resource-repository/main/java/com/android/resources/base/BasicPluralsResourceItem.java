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
import com.android.ide.common.rendering.api.PluralsResourceValue;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.Arity;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.utils.Base128InputStream;
import com.android.utils.Base128InputStream.StreamFormatException;
import com.android.utils.Base128OutputStream;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * Resource item representing a plurals resource.
 */
public final class BasicPluralsResourceItem extends BasicValueResourceItemBase implements PluralsResourceValue {
  private final static String[] EMPTY_STRING_ARRAY = new String[0];
  @NonNull private final Arity[] myArities;
  @NonNull private final String[] myValues;
  private final int myDefaultIndex;

  /**
   * Initializes the resource.
   *
   * @param name the name of the resource
   * @param sourceFile the source file containing definition of the resource
   * @param visibility the visibility of the resource
   * @param quantityValues the values corresponding to quantities
   * @param defaultArity the default arity for the {@link #getValue()} method
   */
  public BasicPluralsResourceItem(@NonNull String name,
                                  @NonNull ResourceSourceFile sourceFile,
                                  @NonNull ResourceVisibility visibility,
                                  @NonNull Map<Arity, String> quantityValues,
                                  @Nullable Arity defaultArity) {
    this(name, sourceFile, visibility,
         quantityValues.keySet().toArray(Arity.EMPTY_ARRAY), quantityValues.values().toArray(EMPTY_STRING_ARRAY),
         getIndex(defaultArity, quantityValues.keySet()));
  }

  private BasicPluralsResourceItem(@NonNull String name,
                                   @NonNull ResourceSourceFile sourceFile,
                                   @NonNull ResourceVisibility visibility,
                                   @NonNull Arity[] arities,
                                   @NonNull String[] values,
                                   int defaultIndex) {
    super(ResourceType.PLURALS, name, sourceFile, visibility);
    assert arities.length == values.length;
    myArities = arities;
    myValues = values;
    assert values.length == 0 || defaultIndex < values.length;
    myDefaultIndex = defaultIndex;
  }

  private static int getIndex(@Nullable Arity arity, @NonNull Collection<Arity> arities) {
    if (arity == null || arities.isEmpty()) {
      return 0;
    }
    int index = 0;
    for (Arity ar : arities) {
      if (ar == arity) {
        return index;
      }
      index++;
    }
    throw new IllegalArgumentException();
  }

  @Override
  public int getPluralsCount() {
    return myArities.length;
  }

  @Override
  @NonNull
  public String getQuantity(int index) {
    return myArities[index].getName();
  }

  @Override
  @NonNull
  public String getValue(int index) {
    return myValues[index];
  }

  @Override
  @Nullable
  public String getValue(@NonNull String quantity) {
    for (int i = 0, n = myArities.length; i < n; i++) {
      if (quantity.equals(myArities[i].getName())) {
        return myValues[i];
      }
    }

    return null;
  }

  @Override
  @Nullable
  public String getValue() {
    return myValues.length == 0 ? null : myValues[myDefaultIndex];
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    BasicPluralsResourceItem other = (BasicPluralsResourceItem) obj;
    return Arrays.equals(myArities, other.myArities) && Arrays.equals(myValues, other.myValues);
  }

  @Override
  public void serialize(@NonNull Base128OutputStream stream,
                        @NonNull Object2IntMap<String> configIndexes,
                        @NonNull Object2IntMap<ResourceSourceFile> sourceFileIndexes,
                        @NonNull Object2IntMap<ResourceNamespace.Resolver> namespaceResolverIndexes) throws IOException {
    super.serialize(stream, configIndexes, sourceFileIndexes, namespaceResolverIndexes);
    int n = myArities.length;
    stream.writeInt(n);
    for (int i = 0; i < n; i++) {
      stream.writeInt(myArities[i].ordinal());
      stream.writeString(myValues[i]);
    }
    stream.writeInt(myDefaultIndex);
  }

  /**
   * Creates a BasicPluralsResourceItem by reading its contents from the given stream.
   */
  @NonNull
  static BasicPluralsResourceItem deserialize(@NonNull Base128InputStream stream,
                                              @NonNull String name,
                                              @NonNull ResourceVisibility visibility,
                                              @NonNull ResourceSourceFile sourceFile,
                                              @NonNull ResourceNamespace.Resolver resolver) throws IOException {
    int n = stream.readInt();
    Arity[] arities = n == 0 ? Arity.EMPTY_ARRAY : new Arity[n];
    String[] values = n == 0 ? EMPTY_STRING_ARRAY : new String[n];
    for (int i = 0; i < n; i++) {
      arities[i] = Arity.values()[stream.readInt()];
      values[i] = stream.readString();
    }
    int defaultIndex = stream.readInt();
    if (values.length != 0 && defaultIndex >= values.length) {
      throw StreamFormatException.invalidFormat();
    }
    BasicPluralsResourceItem item = new BasicPluralsResourceItem(name, sourceFile, visibility, arities, values, defaultIndex);
    item.setNamespaceResolver(resolver);
    return item;
  }
}
