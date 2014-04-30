/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.sdklib.repository.remote;

import com.android.annotations.NonNull;
import com.android.sdklib.internal.repository.IDescription;
import com.android.sdklib.internal.repository.IListDescription;
import com.android.sdklib.repository.descriptors.IPkgDesc;

import java.util.Locale;


/**
 * This class provides information on a remote package available for download
 * via a remote SDK repository server.
 */
public class RemotePkgInfo implements IListDescription, Comparable<RemotePkgInfo> {

    /** Information on the package provided by the remote server. */
    @NonNull
    private final IPkgDesc mPkgDesc;

    /** Source identifier of the package. */
    @NonNull
    private final IDescription mSourceUri;

    public RemotePkgInfo(@NonNull IPkgDesc pkgDesc, @NonNull IDescription sourceUri) {
        mPkgDesc = pkgDesc;
        mSourceUri = sourceUri;
    }

    /** Information on the package provided by the remote server. */
    @NonNull
    public IPkgDesc getDesc() {
        return mPkgDesc;
    }

    /**
     * Returns the source identifier of the remote package.
     * This is an opaque object that can return its own description.
     */
    @NonNull
    public IDescription getSourceUri() {
        return mSourceUri;
    }

    //---- Ordering ----

    /**
     * Compares 2 packages by comparing their {@link IPkgDesc}.
     * The source is not used in the comparison.
     */
    @Override
    public int compareTo(@NonNull RemotePkgInfo o) {
        return mPkgDesc.compareTo(o.mPkgDesc);
    }

    /**
     * The remote package hash code is based on the underlying {@link IPkgDesc}.
     * The source is not used in the hash code.
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((mPkgDesc == null) ? 0 : mPkgDesc.hashCode());
        return result;
    }

    /**
     * Compares 2 packages by comparing their {@link IPkgDesc}.
     * The source is not used in the comparison.
     */
    @Override
    public boolean equals(Object obj) {
        return (obj instanceof RemotePkgInfo) && this.compareTo((RemotePkgInfo) obj) == 0;
    }

    /** String representation for debugging purposes. */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("<RemotePkgInfo Source:").append(mSourceUri.getShortDescription());
        builder.append(' ').append(mPkgDesc.toString()).append('>');
        return builder.toString();
    }

  @Override
  public String getListDescription() {
      // TODO *temporary WIP*
      // Inject all the meta-data needed to properly reconstruct the display,
      // e.g. especially the new list-display. Code should be common with LocalPkgInfo.
      StringBuilder sb = new StringBuilder();

      IPkgDesc d = getDesc();

      sb.append(d.getType().toString().toLowerCase(Locale.US));

      if (d.hasTag()) {
          assert d.getTag() != null;
          sb.append(' ').append(d.getTag().getDisplay());
      }

      if (d.hasPath()) {
          sb.append(' ').append(d.getPath());
      }

      if (d.hasVendorId()) {
          sb.append(", by").append(d.getVendorId());
      }

      if (d.hasAndroidVersion()) {
          assert d.getAndroidVersion() != null;
          sb.append(", API").append(d.getAndroidVersion().getApiString());
      }

      if (d.hasFullRevision()) {
          sb.append(", ").append(d.getFullRevision());
      }

      if (d.hasMajorRevision()) {
          sb.append(", ").append(d.getMajorRevision());
      }

      return sb.toString();
  }
}
