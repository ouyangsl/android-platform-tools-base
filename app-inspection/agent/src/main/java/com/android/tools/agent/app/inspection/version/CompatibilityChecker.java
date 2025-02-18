/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.agent.app.inspection.version;

/**
 * Checks inspector compatibility: the minVersion provided by Android Studio against the version
 * file embedded in the app's APK META-INF; that app wasn't proguarded.
 */
public final class CompatibilityChecker {

    private static final String INSPECTION_PACKAGE = "androidx.inspection";

    private static final String PROGUARD_DETECTOR_CLASS = "ProguardDetection";

    private static final String ANDROIDX_PREFIX = "androidx.";

    private final VersionFileReader reader;
    private final ClassLoader classLoader;

    public CompatibilityChecker(ClassLoader classLoader) {
        this.classLoader = classLoader;
        reader = new VersionFileReader(classLoader);
    }

    /**
     * Compares the version of the library against the provided min_version string.
     *
     * <p>The version of the library is found inside a version file in the APK's META-INF directory.
     *
     * @param coordinate represents the minimum supported library artifact.
     * @return a VersionChecker.Result object containing the result of the check and any errors.
     */
    public CompatibilityCheckerResult checkCompatibility(LibraryCompatibility coordinate) {
        if (classLoader == null) {
            return new CompatibilityCheckerResult(
                    CompatibilityCheckerResult.Status.ERROR,
                    "Application classloader not found",
                    coordinate.artifact,
                    null);
        }
        String versionFile = coordinate.toVersionFileName();
        String minVersionString = coordinate.artifact.version;
        VersionFileReader.Result readResult = reader.readVersionFile(versionFile);
        switch (readResult.status) {
            case NOT_FOUND:
                if (anyExpectedClassesExists(coordinate.expectedClassNames)) {
                    if ("+".equals(coordinate.artifact.version)) {
                        return new CompatibilityCheckerResult(
                                CompatibilityCheckerResult.Status.COMPATIBLE,
                                null,
                                coordinate.artifact,
                                "+");
                    } else {
                        return new CompatibilityCheckerResult(
                                CompatibilityCheckerResult.Status.NOT_FOUND,
                                "Failed to find version file " + versionFile,
                                coordinate.artifact,
                                null);
                    }
                } else {
                    return new CompatibilityCheckerResult(
                            CompatibilityCheckerResult.Status.LIBRARY_NOT_FOUND,
                            "Failed to find library " + versionFile,
                            coordinate.artifact,
                            null);
                }
            case READ_ERROR:
                return new CompatibilityCheckerResult(
                        CompatibilityCheckerResult.Status.INCOMPATIBLE,
                        "Failed to read version file " + versionFile,
                        coordinate.artifact,
                        null);
        }
        Version version = Version.parseOrNull(readResult.versionString);
        if (version == null) {
            return new CompatibilityCheckerResult(
                    CompatibilityCheckerResult.Status.INCOMPATIBLE,
                    "Failed to parse version string "
                            + readResult.versionString
                            + " which is in "
                            + versionFile,
                    coordinate.artifact,
                    readResult.versionString);
        }
        Version minVersion = Version.parseOrNull(minVersionString);
        if (minVersion == null) {
            return new CompatibilityCheckerResult(
                    CompatibilityCheckerResult.Status.ERROR,
                    "Failed to parse provided min version " + minVersionString,
                    coordinate.artifact,
                    readResult.versionString);
        }
        if (minVersion.compareTo(version) > 0) {
            return new CompatibilityCheckerResult(
                    CompatibilityCheckerResult.Status.INCOMPATIBLE,
                    "Library version "
                            + readResult.versionString
                            + " does not satisfy the inspector's min version requirement "
                            + minVersionString,
                    coordinate.artifact,
                    readResult.versionString);
        }

        if (isProguarded(coordinate.artifact)) {
            return new CompatibilityCheckerResult(
                    CompatibilityCheckerResult.Status.PROGUARDED,
                    "Proguard run was detected on the inspected app",
                    coordinate.artifact,
                    readResult.versionString);
        }

        return new CompatibilityCheckerResult(
                CompatibilityCheckerResult.Status.COMPATIBLE,
                null,
                coordinate.artifact,
                readResult.versionString);
    }

    private boolean isProguarded(ArtifactCoordinate coordinate) {
        String groupId = coordinate.groupId;

        // Produce package name for `ProguardDetection` class, e.g. for following params:
        // mavenGroup = androidx.work, mavenArtifact = work-runtime, result will be:
        // androidx.inspection.work.runtime.

        // does group start with "androidx." ? if yes, we remove it
        int prefixCount = groupId.startsWith(ANDROIDX_PREFIX) ? ANDROIDX_PREFIX.length() : 0;
        groupId = groupId.substring(prefixCount);

        String packageName = getPackageName(coordinate, groupId);

        String className = packageName + "." + PROGUARD_DETECTOR_CLASS;

        try {
            classLoader.loadClass(className);
            return false;
        } catch (ClassNotFoundException e) {
            return true;
        }
    }

    private static String getPackageName(ArtifactCoordinate coordinate, String groupId) {
        String artifactId = getArtifactId(coordinate, groupId);
        String packageName = INSPECTION_PACKAGE + "." + groupId;

        return artifactId.isEmpty() ? packageName : packageName + "." + artifactId;
    }

    private static String getArtifactId(ArtifactCoordinate coordinate, String groupId) {
        // get first token in artifactId, e.g. work-runtime => work.
        // There may not always be a hyphen in the artifact, e.g. "compose.ui:ui" => "ui"
        String artifactPrefix = coordinate.artifactId.split("-")[0];

        // remove clashing term in the beginning of artifact and the end of group, e.g.:
        // groupId=work and artifactId=work-runtime => artifactId = "-runtime"
        String artifactId =
                groupId.endsWith(artifactPrefix)
                        ? coordinate.artifactId.substring(artifactPrefix.length())
                        : coordinate.artifactId;

        // remove "-" if artifactId now starts with it
        artifactId = artifactId.startsWith("-") ? artifactId.substring(1) : artifactId;

        // e.g. "foundation-layout" => "foundation.layout"
        return artifactId.replace('-', '.');
    }

    /**
     * Check if any of the specified classes exist in the app.
     *
     * <p>If a library changes a class name, a client could specify both the old and the new class
     * name. If any of those classes are present we assume the library is included in the app.
     *
     * @param expectedLibraryClassNames classes expected to be in a certain library.
     * @return true if any of the classes are present or no classes were specified.
     */
    private boolean anyExpectedClassesExists(String[] expectedLibraryClassNames) {
        if (expectedLibraryClassNames == null || expectedLibraryClassNames.length == 0) {
            // If no expected class names are specified, we would not know if the library is missing
            return true;
        }
        for (String className : expectedLibraryClassNames) {
            try {
                classLoader.loadClass(className);
                return true;
            } catch (ClassNotFoundException e) {
                // ignore...
            }
        }
        return false;
    }
}
