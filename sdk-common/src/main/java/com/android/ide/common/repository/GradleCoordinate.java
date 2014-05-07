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
import com.google.common.base.Joiner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class represents a maven coordinate and allows for comparison at any level.
 * <p>
 * This class does not directly implement {@link java.lang.Comparable}; instead,
 * you should use one of the specific {@link java.util.Comparator} constants based
 * on what type of ordering you need.
 */
public class GradleCoordinate {

    /**
     * Maven coordinates take the following form: groupId:artifactId:packaging:classifier:version
     * where
     *   groupId is dot-notated alphanumeric
     *   artifactId is the name of the project
     *   packaging is optional and is jar/war/pom/aar/etc
     *   classifier is optional and provides filtering context
     *   version uniquely identifies a version.
     *
     * We only care about coordinates of the following form: groupId:artifactId:revision
     * where revision is a series of '.' separated numbers optionally terminated by a '+' character.
     */

    /**
     * List taken from <a href="http://maven.apache.org/pom.html#Maven_Coordinates">http://maven.apache.org/pom.html#Maven_Coordinates</a>
     */
    public enum ArtifactType {
        POM("pom"),
        JAR("jar"),
        MAVEN_PLUGIN("maven-plugin"),
        EJB("ejb"),
        WAR("war"),
        EAR("ear"),
        RAR("rar"),
        PAR("par"),
        AAR("aar");

        private final String myId;

        ArtifactType(String id) {
            myId = id;
        }

        @Nullable
        public static ArtifactType getArtifactType(@Nullable String name) {
            if (name != null) {
                for (ArtifactType type : ArtifactType.values()) {
                    if (type.myId.equalsIgnoreCase(name)) {
                        return type;
                    }
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return myId;
        }
    }

    /**
     * A single component of a revision number: either a number, a string or a list of
     * components separated by dashes.
     */
    public abstract static class RevisionComponent implements Comparable<RevisionComponent> {
        public abstract int asInteger();
    }

    public static class NumberComponent extends RevisionComponent {
        private final int myNumber;

        public NumberComponent(int number) {
            myNumber = number;
        }

        @Override
        public String toString() {
            return Integer.toString(myNumber);
        }

        @Override
        public int asInteger() {
            return myNumber;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof NumberComponent && ((NumberComponent) o).myNumber == myNumber;
        }

        @Override
        public int hashCode() {
            return myNumber;
        }

        @Override
        public int compareTo(RevisionComponent o) {
            if (o instanceof NumberComponent) {
                return myNumber - ((NumberComponent) o).myNumber;
            }
            if (o instanceof StringComponent) {
                return 1;
            }
            if (o instanceof ListComponent) {
                return 1; // 1.0.x > 1-1
            }
            return 0;
        }
    }

    public static class StringComponent extends RevisionComponent {
        private final String myString;

        public StringComponent(String string) {
            this.myString = string;
        }

        @Override
        public String toString() {
            return myString;
        }

        @Override
        public int asInteger() {
            return 0;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof StringComponent && ((StringComponent) o).myString.equals(myString);
        }

        @Override
        public int hashCode() {
            return myString.hashCode();
        }

        @Override
        public int compareTo(RevisionComponent o) {
            if (o instanceof NumberComponent) {
                return -1;
            }
            if (o instanceof StringComponent) {
                return myString.compareTo(((StringComponent) o).myString);
            }
            if (o instanceof ListComponent) {
                return -1;  // 1-sp < 1-1
            }
            return 0;
        }
    }

    private static class PlusComponent extends RevisionComponent {
        @Override
        public String toString() {
            return "+";
        }

        @Override
        public int asInteger() {
            return PLUS_REV_VALUE;
        }

        @Override
        public int compareTo(RevisionComponent o) {
            throw new UnsupportedOperationException(
                    "Please use a specific comparator that knows how to handle +");
        }
    }

    /**
     * A list of components separated by dashes.
     */
    public static class ListComponent extends RevisionComponent {
        private final List<RevisionComponent> myItems = new ArrayList<RevisionComponent>();
        private boolean myClosed = false;

        public static ListComponent of(RevisionComponent... components) {
            ListComponent result = new ListComponent();
            for (RevisionComponent component : components) {
                result.add(component);
            }
            return result;
        }

        public void add(RevisionComponent component) {
            myItems.add(component);
        }

        @Override
        public int asInteger() {
            return 0;
        }

        @Override
        public int compareTo(RevisionComponent o) {
            if (o instanceof NumberComponent) {
                return -1;  // 1-1 < 1.0.x
            }
            if (o instanceof StringComponent) {
                return 1;  // 1-1 > 1-sp
            }
            if (o instanceof ListComponent) {
                ListComponent rhs = (ListComponent) o;
                for (int i = 0; i < myItems.size() && i < rhs.myItems.size(); i++) {
                    int rc = myItems.get(i).compareTo(rhs.myItems.get(i));
                    if (rc != 0) return rc;
                }
                return myItems.size() - rhs.myItems.size();
            }
            return 0;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ListComponent && ((ListComponent) o).myItems.equals(myItems);
        }

        @Override
        public int hashCode() {
            return myItems.hashCode();
        }

        @Override
        public String toString() {
            return Joiner.on("-").join(myItems);
        }
    }

    public static final PlusComponent PLUS_REV = new PlusComponent();
    public static final int PLUS_REV_VALUE = -1;

    private final String myGroupId;

    private final String myArtifactId;

    private final ArtifactType myArtifactType;

    private final List<RevisionComponent> myRevisions = new ArrayList<RevisionComponent>(3);

    private static final Pattern MAVEN_PATTERN =
            Pattern.compile("([\\w\\d\\.-]+):([\\w\\d\\.-]+):([^:@]+)(@[\\w-]+)?");

    /**
     * Constructor
     */
    public GradleCoordinate(@NonNull String groupId, @NonNull String artifactId,
            @NonNull RevisionComponent... revisions) {
        this(groupId, artifactId, Arrays.asList(revisions), null);
    }

    /**
     * Constructor
     */
    public GradleCoordinate(@NonNull String groupId, @NonNull String artifactId,
                            @NonNull int... revisions) {
        this(groupId, artifactId, createComponents(revisions), null);
    }

    private static List<RevisionComponent> createComponents(int[] revisions) {
        List<RevisionComponent> result = new ArrayList<RevisionComponent>(revisions.length);
        for (int revision : revisions) {
            if (revision == PLUS_REV_VALUE) {
                result.add(PLUS_REV);
            } else {
                result.add(new NumberComponent(revision));
            }
        }
        return result;
    }

    /**
     * Constructor
     */
    public GradleCoordinate(@NonNull String groupId, @NonNull String artifactId,
            @NonNull List<RevisionComponent> revisions, @Nullable ArtifactType type) {
        myGroupId = groupId;
        myArtifactId = artifactId;
        myRevisions.addAll(revisions);

        myArtifactType = type;
    }

    /**
     * Create a GradleCoordinate from a string of the form groupId:artifactId:MajorRevision.MinorRevision.(MicroRevision|+)
     *
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
        String typeString = matcher.group(4);
        ArtifactType type = null;

        if (typeString != null) {
            // Strip off the '@' symbol and try to convert
            type = ArtifactType.getArtifactType(typeString.substring(1));
        }

        List<RevisionComponent> revisions = parseRevisionNumber(revision);

        return new GradleCoordinate(groupId, artifactId, revisions, type);
    }

    private static List<RevisionComponent> parseRevisionNumber(String revision) {
        List<RevisionComponent> components = new ArrayList<RevisionComponent>();
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < revision.length(); i++) {
            char c = revision.charAt(i);
            if (c == '.') {
                flushBuffer(components, buffer, true);
            } else if (c == '+') {
                if (buffer.length() > 0) {
                    flushBuffer(components, buffer, true);
                }
                components.add(PLUS_REV);
                break;
            } else if (c == '-') {
                flushBuffer(components, buffer, false);
                int last = components.size() - 1;
                if (last == -1) {
                    components.add(ListComponent.of(new NumberComponent(0)));
                } else if (!(components.get(last) instanceof ListComponent)) {
                    components.set(last, ListComponent.of(components.get(last)));
                }
            } else {
                buffer.append(c);
            }
        }
        if (buffer.length() > 0 || components.size() == 0) {
            flushBuffer(components, buffer, true);
        }
        return components;
    }

    private static void flushBuffer(List<RevisionComponent> components, StringBuilder buffer,
                                    boolean closeList) {
        RevisionComponent newComponent;
        if (buffer.length() == 0) {
            newComponent = new NumberComponent(0);
        } else {
            try {
                newComponent = new NumberComponent(Integer.parseInt(buffer.toString()));
            } catch(NumberFormatException e) {
                newComponent = new StringComponent(buffer.toString());
            }
        }
        buffer.setLength(0);
        if (components.size() > 0 &&
               components.get(components.size() - 1) instanceof ListComponent) {
            ListComponent component = (ListComponent) components.get(components.size() - 1);
            if (!component.myClosed) {
                component.add(newComponent);
                if (closeList) {
                    component.myClosed = true;
                }
                return;
            }
        }
        components.add(newComponent);
    }

    @Override
    public String toString() {
        String s = String.format(Locale.US, "%s:%s:%s", myGroupId, myArtifactId, getFullRevision());
        if (myArtifactType != null) {
            s += "@" + myArtifactType.toString();
        }
        return s;
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

    @Nullable
    public ArtifactType getType() {
        return myArtifactType;
    }

    public boolean acceptsGreaterRevisions() {
        return myRevisions.get(myRevisions.size() - 1) == PLUS_REV;
    }

    public String getFullRevision() {
        StringBuilder revision = new StringBuilder();
        for (RevisionComponent component : myRevisions) {
            if (revision.length() > 0) {
                revision.append('.');
            }
            revision.append(component.toString());
        }

        return revision.toString();
    }

    /**
     * Returns the major version (X in X.2.3), which can be {@link #PLUS_REV}, or Integer.MIN_VALUE
     * if it is not available
     */
    public int getMajorVersion() {
        return myRevisions.isEmpty() ? Integer.MIN_VALUE : myRevisions.get(0).asInteger();
    }

    /**
     * Returns the minor version (X in 1.X.3), which can be {@link #PLUS_REV}, or Integer.MIN_VALUE
     * if it is not available
     */
    public int getMinorVersion() {
        return myRevisions.size() < 2 ? Integer.MIN_VALUE : myRevisions.get(1).asInteger();
    }

    /**
     * Returns the major version (X in 1.2.X), which can be {@link #PLUS_REV}, or Integer.MIN_VALUE
     * if it is not available
     */
    public int getMicroVersion() {
        return myRevisions.size() < 3 ? Integer.MIN_VALUE : myRevisions.get(2).asInteger();
    }

    /**
     * Returns true if and only if the given coordinate refers to the same group and artifact.
     *
     * @param o the coordinate to compare with
     * @return true iff the other group and artifact match the group and artifact of this
     * coordinate.
     */
    public boolean isSameArtifact(@NonNull GradleCoordinate o) {
        return o.myGroupId.equals(myGroupId) && o.myArtifactId.equals(myArtifactId);
    }

    @Override
    public boolean equals(@NonNull Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        GradleCoordinate that = (GradleCoordinate) o;

        if (!myRevisions.equals(that.myRevisions)) {
            return false;
        }
        if (!myArtifactId.equals(that.myArtifactId)) {
            return false;
        }
        if (!myGroupId.equals(that.myGroupId)) {
            return false;
        }
        if ((myArtifactType == null) != (that.myArtifactType == null)) {
            return false;
        }
        if (myArtifactType != null && !myArtifactType.equals(that.myArtifactType)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = myGroupId.hashCode();
        result = 31 * result + myArtifactId.hashCode();
        for (RevisionComponent component : myRevisions) {
            result = 31 * result + component.hashCode();
        }
        if (myArtifactType != null) {
            result = 31 * result + myArtifactType.hashCode();
        }
        return result;
    }

    /**
     * Comparator which compares Gradle versions - and treats a + version as lower
     * than a specific number in the same place. This is typically useful when trying
     * to for example order coordinates by "most specific".
     */
    public static final Comparator<GradleCoordinate> COMPARE_PLUS_LOWER =
            new GradleCoordinateComparator(-1);

    /**
     * Comparator which compares Gradle versions - and treats a + version as higher
     * than a specific number. This is typically useful when seeing if a dependency
     * is met, e.g. if you require version 0.7.3, comparing it with 0.7.+ would consider
     * 0.7.+ higher and therefore satisfying the version requirement.
     */
    public static final Comparator<GradleCoordinate> COMPARE_PLUS_HIGHER =
            new GradleCoordinateComparator(1);

    private static class GradleCoordinateComparator implements Comparator<GradleCoordinate> {
        private final int myPlusResult;

        private GradleCoordinateComparator(int plusResult) {
            myPlusResult = plusResult;
        }

        @Override
        public int compare(@NonNull GradleCoordinate a, @NonNull GradleCoordinate b) {
            // Make sure we're comparing apples to apples. If not, compare artifactIds
            if (!a.isSameArtifact(b)) {
                return a.myArtifactId.compareTo(b.myArtifactId);
            }

            int sizeA = a.myRevisions.size();
            int sizeB = b.myRevisions.size();
            int common = Math.min(sizeA, sizeB);
            for (int i = 0; i < common; ++i) {
                RevisionComponent revision1 = a.myRevisions.get(i);
                if (revision1 instanceof PlusComponent) return myPlusResult;
                RevisionComponent revision2 = b.myRevisions.get(i);
                if (revision2 instanceof PlusComponent) return -myPlusResult;
                int delta = revision1.compareTo(revision2);
                if (delta != 0) {
                    return delta;
                }
            }
            return sizeA < sizeB ? -1 : sizeB < sizeA ? 1 : 0;
        }
    }
}
