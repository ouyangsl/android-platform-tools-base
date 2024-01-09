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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.AbstractResourceRepository;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceItemWithVisibility;
import com.android.ide.common.resources.ResourceVisitor;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Common superclass for {@link AarSourceResourceRepository} and {@link AarProtoResourceRepository}.
 */
public abstract class AbstractAarResourceRepository extends AbstractResourceRepository implements AarResourceRepository {
  @NonNull protected final ResourceNamespace myNamespace;
  @NonNull protected final Map<ResourceType, ListMultimap<String, ResourceItem>> myResources = new EnumMap<>(ResourceType.class);
  @NonNull private final Map<ResourceType, Set<ResourceItem>> myPublicResources = new EnumMap<>(ResourceType.class);
  @Nullable protected final String myLibraryName;

  AbstractAarResourceRepository(@NonNull ResourceNamespace namespace, @Nullable String libraryName) {
    myNamespace = namespace;
    myLibraryName = libraryName;
  }

  @Override
  @NonNull
  protected final ListMultimap<String, ResourceItem> getResourcesInternal(
    @NonNull ResourceNamespace namespace, @NonNull ResourceType resourceType) {
    if (!namespace.equals(myNamespace)) {
      return ImmutableListMultimap.of();
    }
    return myResources.getOrDefault(resourceType, ImmutableListMultimap.of());
  }

  @NonNull
  private ListMultimap<String, ResourceItem> getOrCreateMap(@NonNull ResourceType resourceType) {
    return myResources.computeIfAbsent(resourceType, type -> ArrayListMultimap.create());
  }

  protected final void addResourceItem(@NonNull ResourceItem item) {
    ListMultimap<String, ResourceItem> multimap = getOrCreateMap(item.getType());
    multimap.put(item.getName(), item);
  }

  /**
   * Populates the {@link #myPublicResources} map. Has to be called after {@link #myResources} has been populated.
   */
  protected final void populatePublicResourcesMap() {
    for (Map.Entry<ResourceType, ListMultimap<String, ResourceItem>> entry : myResources.entrySet()) {
      ResourceType resourceType = entry.getKey();
      ImmutableSet.Builder<ResourceItem> setBuilder = null;
      ListMultimap<String, ResourceItem> items = entry.getValue();
      for (ResourceItem item : items.values()) {
        if (((ResourceItemWithVisibility)item).getVisibility() == ResourceVisibility.PUBLIC) {
          if (setBuilder == null) {
            setBuilder = ImmutableSet.builder();
          }
          setBuilder.add(item);
        }
      }
      myPublicResources.put(resourceType, setBuilder == null ? ImmutableSet.of() : setBuilder.build());
    }
  }

  /**
   * Makes resource maps immutable.
   */
  protected void freezeResources() {
    for (Map.Entry<ResourceType, ListMultimap<String, ResourceItem>> entry : myResources.entrySet()) {
      myResources.put(entry.getKey(), ImmutableListMultimap.copyOf(entry.getValue()));
    }
  }

  @Override
  @NonNull
  public ResourceVisitor.VisitResult accept(@NonNull ResourceVisitor visitor) {
    if (visitor.shouldVisitNamespace(myNamespace)) {
      if (AbstractResourceRepository.acceptByResources(myResources, visitor) == ResourceVisitor.VisitResult.ABORT) {
        return ResourceVisitor.VisitResult.ABORT;
      }
    }

    return ResourceVisitor.VisitResult.CONTINUE;

  }

  @Override
  @NonNull
  public List<ResourceItem> getResources(@NonNull ResourceNamespace namespace, @NonNull ResourceType resourceType,
                                         @NonNull String resourceName) {
    ListMultimap<String, ResourceItem> map = getResourcesInternal(namespace, resourceType);
    List<ResourceItem> items = map.get(resourceName);
    return items == null ? ImmutableList.of() : items;
  }

  @Override
  @NonNull
  public ListMultimap<String, ResourceItem> getResources(@NonNull ResourceNamespace namespace, @NonNull ResourceType resourceType) {
    return getResourcesInternal(namespace, resourceType);
  }

  @Override
  @NonNull
  public Collection<ResourceItem> getPublicResources(@NonNull ResourceNamespace namespace, @NonNull ResourceType type) {
    if (!namespace.equals(myNamespace)) {
      return Collections.emptySet();
    }
    Set<ResourceItem> resourceItems = myPublicResources.get(type);
    return resourceItems == null ? Collections.emptySet() : resourceItems;
  }

  @Override
  @NonNull
  public final ResourceNamespace getNamespace() {
    return myNamespace;
  }

  @Override
  @Nullable
  public final String getLibraryName() {
    return myLibraryName;
  }

  @Override
  @NonNull
  public final String getDisplayName() {
    return myLibraryName == null ? "Android Framework" : myLibraryName;
  }

  @Override
  public final boolean containsUserDefinedResources() {
    return false;
  }
}
