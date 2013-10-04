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
package com.android.ide.common.repository;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class represents a maven coordinate and allows for comparison at any level.
 */
public class GradleCoordinate implements Comparable<GradleCoordinate> {

  /**
   * Maven coordinates take the following form: groupId:artifactId:packaging:classifier:version
   * where
   *   groupId is dot-notated alphanumeric
   *   artifactId is the name of the project
   *   packaging is optional and is jar/war/pom/aar/etc
   *   classifier is optional and provides filtering context
   *   version uniquely identifies a version.
   *
   * We only care about coordinates of the following form: groupId:artifactId:MajorRevision.MinorRevision.(MicroRevision|+)
   *                                                   or: groupId:artifactId:+
   */

  public static final int PLUS_REV           = Integer.MAX_VALUE;

  private final String myGroupId;
  private final String myArtifactId;

  private final int myMajorRevision;
  private final int myMinorRevision;
  private final int myMicroRevision;

  private final boolean myIsAnyRevision;

  private static final Pattern MAVEN_PATTERN = Pattern.compile("([\\w\\d\\.-]+):([\\w\\d\\.-]+):([\\d+\\.]+|\\+)");
  private static final Pattern REVISION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+|\\+)");

  /**
   * Constructor
   * @param groupId
   * @param artifactId
   * @param majorRevision
   * @param minorRevision
   * @param microRevision
   */
  public GradleCoordinate(String groupId, String artifactId, int majorRevision, int minorRevision, int microRevision) {
    myGroupId = groupId;
    myArtifactId = artifactId;
    myMajorRevision = majorRevision;
    myMinorRevision = minorRevision;
    myMicroRevision = microRevision;

    // If the major revision is "+" then we'll accept any revision
    myIsAnyRevision = (myMajorRevision == PLUS_REV);
  }

  /**
   * Create a GradleCoordinate from a string of the form groupId:artifactId:MajorRevision.MinorRevision.(MicroRevision|+)
   * @param coordinateString the string to parse
   * @return a coordinate object or null if the given string was malformed.
   */
  @Nullable
  public static GradleCoordinate parseCoordinateString(@NonNull String coordinateString) {
    if (coordinateString == null) {
      return null;
    }

    Matcher matcher = MAVEN_PATTERN.matcher(coordinateString);
    if (!matcher.matches()) {
      return null;
    }

    String groupId = matcher.group(1);
    String artifactId = matcher.group(2);
    String revision = matcher.group(3);

    int majorRevision;
    int minorRevision;
    int microRevision;
    if (revision.equals("+")) {
      majorRevision = minorRevision = microRevision = PLUS_REV;
    } else {
      matcher = REVISION_PATTERN.matcher(revision);
      if (!matcher.matches()) {
        return null;
      }
      try {
        majorRevision = Integer.parseInt(matcher.group(1));
        minorRevision = Integer.parseInt(matcher.group(2));
        String microRevisionString = matcher.group(3);
        if (microRevisionString.equals("+")) {
          microRevision = PLUS_REV;
        } else {
          microRevision = Integer.parseInt(microRevisionString);
        }
      } catch (Exception e) {
        return null;
      }
    }
    return new GradleCoordinate(groupId, artifactId, majorRevision, minorRevision, microRevision);
  }

  @Override
  public String toString() {
    String micro = (myMicroRevision == PLUS_REV) ? "+" : Integer.toString(myMicroRevision);
    String revision;
    if (myIsAnyRevision) {
      revision = "+";
    } else {
      revision = String.format(Locale.US, "%d.%d.%s", myMajorRevision, myMinorRevision, micro);
    }
    return String.format(Locale.US, "%s:%s:%s", myGroupId, myArtifactId, revision);
  }

  @Nullable
  public String getGroupId() {
    return myGroupId;
  }

  @Nullable
  public String getArtifactId() {
    return myArtifactId;
  }

  @Nullable
  public String getId() {
    if (myGroupId == null || myArtifactId == null) {
      return null;
    }

    return String.format("%s:%s", myGroupId, myArtifactId);
  }

  public int getMajorRevision() {
    return myMajorRevision;
  }

  public int getMinorRevision() {
    return myMinorRevision;
  }

  public int getMicroRevision() {
    return myMicroRevision;
  }

  public String getFullRevision() {
    String micro = (myMicroRevision == PLUS_REV) ? "+" : Integer.toString(myMicroRevision);
    return String.format(Locale.US, "%d.%d.%s", myMajorRevision, myMinorRevision, micro);
  }

  /**
   * Returns true if and only if the given coordinate refers to the same group and artifact.
   * @param o the coordinate to compare with
   * @return true iff the other group and artifact match the group and artifact of this coordinate.
   */
  public boolean isSameArtifact(@NonNull GradleCoordinate o) {
    return o.myGroupId.equals(myGroupId) && o.myArtifactId.equals(myArtifactId);
  }

  @Override
  public boolean equals(@NonNull Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GradleCoordinate that = (GradleCoordinate)o;

    if (myMajorRevision != that.myMajorRevision) return false;
    if (myMicroRevision != that.myMicroRevision) return false;
    if (myMinorRevision != that.myMinorRevision) return false;
    if (!myArtifactId.equals(that.myArtifactId)) return false;
    if (!myGroupId.equals(that.myGroupId)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myGroupId.hashCode();
    result = 31 * result + myArtifactId.hashCode();
    result = 31 * result + myMajorRevision;
    result = 31 * result + myMinorRevision;
    result = 31 * result + myMicroRevision;
    return result;
  }

  @Override
  public int compareTo(@NonNull GradleCoordinate that) {
    // Make sure we're comparing apples to apples. If not, compare artifactIds
    if (!this.isSameArtifact(that)) {
      return this.myArtifactId.compareTo(that.myArtifactId);
    }

    // Specific version should beat "any version"
    if (myIsAnyRevision) {
      return -1;
    } else if (that.myIsAnyRevision) {
      return 1;
    }

    if (this.myMajorRevision != that.myMajorRevision) {
      return this.myMajorRevision - that.myMajorRevision;
    } else if (this.myMinorRevision != that.myMinorRevision) {
      return this.myMinorRevision - that.myMinorRevision;
    } else if (this.myMicroRevision != that.myMicroRevision) {
      return this.myMicroRevision - that.myMicroRevision;
    }
    return 0;
  }
}
