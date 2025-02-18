/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.repository.util;

import static com.android.repository.testframework.FakePackage.FakeLocalPackage;
import static com.android.repository.testframework.FakePackage.FakeRemotePackage;

import com.android.repository.Revision;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.manager.RepoManagerImpl;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.testframework.FakeDependency;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.testutils.file.InMemoryFileSystems;
import com.android.utils.PathUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import junit.framework.TestCase;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

/**
 * Tests for {@link InstallerUtil}.
 */
public class InstallerUtilTest extends TestCase {

    /** Simple case: a package requires itself, even if has no dependencies set. */
    public void testNoDeps() {
        RemotePackage r1 = new FakeRemotePackage("r1");

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        assertEquals(
                request,
                InstallerUtil.computeRequiredPackages(
                        request,
                        new RepositoryPackages(
                                ImmutableList.of(new FakeLocalPackage("l1")),
                                ImmutableList.of(r1, new FakeRemotePackage("r2"))),
                        progress));
        progress.assertNoErrorsOrWarnings();
    }

    /** Simple chain of dependencies, r1->r2->r3. Should be returned in reverse order. */
    public void testSimple() {
        FakeRemotePackage r1 = new FakeRemotePackage("r1");
        r1.setDependencies(ImmutableList.of(new FakeDependency("r2")));
        FakeRemotePackage r2 = new FakeRemotePackage("r2");
        r2.setDependencies(ImmutableList.of(new FakeDependency("r3")));
        FakeRemotePackage r3 = new FakeRemotePackage("r3");

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        ImmutableList<RemotePackage> expected = ImmutableList.of(r3, r2, r1);
        assertEquals(
                expected,
                InstallerUtil.computeRequiredPackages(
                        request,
                        new RepositoryPackages(
                                ImmutableList.of(new FakeLocalPackage("l1")),
                                ImmutableList.of(r1, r2, r3)),
                        progress));
        progress.assertNoErrorsOrWarnings();
    }

    /** Simple dependencies, with isSoft = null (the default). */
    public void testSoftIsNull() {
        FakeRemotePackage r1 = new FakeRemotePackage("r1");
        r1.setDependencies(ImmutableList.of(new FakeDependency("r2", 1, 0, 0, null)));
        FakeRemotePackage r2 = new FakeRemotePackage("r2");

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        ImmutableList<RemotePackage> expected = ImmutableList.of(r2, r1);
        assertEquals(
                expected,
                InstallerUtil.computeRequiredPackages(
                        request,
                        new RepositoryPackages(
                                ImmutableList.of(new FakeLocalPackage("l1")),
                                ImmutableList.of(r1, r2)),
                        progress));
        progress.assertNoErrorsOrWarnings();
    }

    /** Request r1 and r1. The latest version of r1 is installed so only r2 is returned. */
    public void testLocalInstalled() {
        RemotePackage r1 = new FakeRemotePackage("r1");
        RemotePackage r2 = new FakeRemotePackage("r2");

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1, r2);
        ImmutableList<RemotePackage> expected = ImmutableList.of(r2);
        assertEquals(
                expected,
                InstallerUtil.computeRequiredPackages(
                        request,
                        new RepositoryPackages(
                                ImmutableList.of(new FakeLocalPackage("r1")),
                                ImmutableList.of(r1, r2)),
                        progress));
        progress.assertNoErrorsOrWarnings();
    }

    /**
     * Request r1 and r2. r1 is installed but there is an update for it available, and so both r1
     * and r2 are returned.
     */
    public void testLocalInstalledWithUpdate() {
        FakeRemotePackage r1 = new FakeRemotePackage("r1");
        r1.setRevision(new Revision(2));
        RemotePackage r2 = new FakeRemotePackage("r2");

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1, r2);
        List<RemotePackage> result =
                InstallerUtil.computeRequiredPackages(
                        request,
                        new RepositoryPackages(
                                ImmutableList.of(new FakeLocalPackage("l1")),
                                ImmutableList.of(r1, r2)),
                        progress);
        assertTrue(result.get(0).equals(r1) || result.get(1).equals(r1));
        assertTrue(result.get(0).equals(r2) || result.get(1).equals(r2));
        progress.assertNoErrorsOrWarnings();
    }

    /**
     * Dependency chain r1->r2->r3, but r3 is already installed with sufficient version, and so is
     * not returned.
     */
    public void testLocalSatisfies() {
        FakeRemotePackage r1 = new FakeRemotePackage("r1");
        r1.setDependencies(ImmutableList.of(new FakeDependency("r2")));
        FakeRemotePackage r2 = new FakeRemotePackage("r2");
        r2.setDependencies(ImmutableList.of(new FakeDependency("r3", 1, 1, 1)));
        FakeRemotePackage r3 = new FakeRemotePackage("r3");

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        ImmutableList<RemotePackage> expected = ImmutableList.of(r2, r1);
        FakeLocalPackage r3_2 = new FakeLocalPackage("r3");
        r3_2.setRevision(new Revision(2));
        assertEquals(
                expected,
                InstallerUtil.computeRequiredPackages(
                        request,
                        new RepositoryPackages(
                                ImmutableList.of(r3_2), ImmutableList.of(r1, r2, r3)),
                        progress));
        progress.assertNoErrorsOrWarnings();
    }

    /**
     * Dependency chain r1->r2->r3, but r3 is already installed with no required version specified,
     * and so is not returned.
     */
    public void testLocalSatisfiesNoVersion() {
        FakeRemotePackage r1 = new FakeRemotePackage("r1");
        r1.setDependencies(ImmutableList.of(new FakeDependency("r2")));
        FakeRemotePackage r2 = new FakeRemotePackage("r2");
        r2.setDependencies(ImmutableList.of(new FakeDependency("r3")));
        FakeRemotePackage r3 = new FakeRemotePackage("r3");

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        ImmutableList<RemotePackage> expected = ImmutableList.of(r2, r1);
        assertEquals(
                expected,
                InstallerUtil.computeRequiredPackages(
                        request,
                        new RepositoryPackages(
                                ImmutableList.of(new FakeLocalPackage("r3")),
                                ImmutableList.of(r1, r2, r3)),
                        progress));
        progress.assertNoErrorsOrWarnings();
    }

    /**
     * Dependency chain r1->r2->r3, and r3 is installed but doesn't meet the version requirement.
     */
    public void testUpdateNeeded() {
        FakeRemotePackage r1 = new FakeRemotePackage("r1");
        r1.setDependencies(ImmutableList.of(new FakeDependency("r2")));
        FakeRemotePackage r2 = new FakeRemotePackage("r2");
        r2.setDependencies(ImmutableList.of(new FakeDependency("r3", 2, null, null)));
        FakeRemotePackage r3 = new FakeRemotePackage("r3");
        r3.setRevision(new Revision(2));

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        ImmutableList<RemotePackage> expected = ImmutableList.of(r3, r2, r1);
        assertEquals(
                expected,
                InstallerUtil.computeRequiredPackages(
                        request,
                        new RepositoryPackages(
                                ImmutableList.of(new FakeLocalPackage("r3")),
                                ImmutableList.of(r1, r2, r3)),
                        progress));
        progress.assertNoErrorsOrWarnings();
    }

    /** r1->{r2, r3}. All should be returned, with r1 last. */
    public void testMulti() {
        FakeRemotePackage r1 = new FakeRemotePackage("r1");
        r1.setDependencies(ImmutableList.of(new FakeDependency("r2"), new FakeDependency("r3")));
        FakeRemotePackage r2 = new FakeRemotePackage("r2");
        FakeRemotePackage r3 = new FakeRemotePackage("r3");

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        List<RemotePackage> result =
                InstallerUtil.computeRequiredPackages(
                        request,
                        new RepositoryPackages(ImmutableList.of(), ImmutableList.of(r1, r2, r3)),
                        progress);
        assertTrue(result.get(0).equals(r2) || result.get(1).equals(r2));
        assertTrue(result.get(0).equals(r3) || result.get(1).equals(r3));
        assertEquals(r1, result.get(2));
        progress.assertNoErrorsOrWarnings();
    }

    /** r1->{r2, r3}->r4. All should be returned, with r4 first and r1 last. */
    public void testDagDeps() {
        FakeRemotePackage r1 = new FakeRemotePackage("r1");
        r1.setDependencies(ImmutableList.of(new FakeDependency("r2"), new FakeDependency("r3")));
        FakeRemotePackage r2 = new FakeRemotePackage("r2");
        r2.setDependencies(ImmutableList.of(new FakeDependency("r4")));
        FakeRemotePackage r3 = new FakeRemotePackage("r3");
        r3.setDependencies(ImmutableList.of(new FakeDependency("r4")));
        RemotePackage r4 = new FakeRemotePackage("r4");

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        List<RemotePackage> result =
                InstallerUtil.computeRequiredPackages(
                        request,
                        new RepositoryPackages(
                                ImmutableList.of(), ImmutableList.of(r1, r2, r3, r4)),
                        progress);
        assertEquals(r4, result.get(0));
        assertTrue(result.get(1).equals(r2) || result.get(2).equals(r2));
        assertTrue(result.get(1).equals(r3) || result.get(2).equals(r3));
        assertEquals(r1, result.get(3));
        progress.assertNoErrorsOrWarnings();
    }

    /** r1->r2->r3->r1. All should be returned, in undefined order. */
    public void testCycle() {
        FakeRemotePackage r1 = new FakeRemotePackage("r1");
        r1.setDependencies(ImmutableList.of(new FakeDependency("r2")));
        FakeRemotePackage r2 = new FakeRemotePackage("r2");
        r2.setDependencies(ImmutableList.of(new FakeDependency("r3")));
        FakeRemotePackage r3 = new FakeRemotePackage("r3");
        r3.setDependencies(ImmutableList.of(new FakeDependency("r1")));

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        Set<RemotePackage> expected = Sets.newHashSet(r1, r2, r3);
        // Don't have any guarantee of order in this case.
        assertEquals(
                expected,
                Sets.newHashSet(
                        InstallerUtil.computeRequiredPackages(
                                request,
                                new RepositoryPackages(
                                        ImmutableList.of(), ImmutableList.of(r1, r2, r3)),
                                progress)));
        progress.assertNoErrorsOrWarnings();
    }

    /** r1->r2->r3->r4->r3. All should be returned, with [r2, r1] last. */
    public void testCycle2() {
        FakeRemotePackage r1 = new FakeRemotePackage("r1");
        r1.setDependencies(ImmutableList.of(new FakeDependency("r2")));

        FakeRemotePackage r2 = new FakeRemotePackage("r2");
        r2.setDependencies(ImmutableList.of(new FakeDependency("r3")));
        FakeRemotePackage r3 = new FakeRemotePackage("r3");
        r3.setDependencies(ImmutableList.of(new FakeDependency("r4")));
        FakeRemotePackage r4 = new FakeRemotePackage("r4");
        r4.setDependencies(ImmutableList.of(new FakeDependency("r3")));

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        List<RemotePackage> result =
                InstallerUtil.computeRequiredPackages(
                        request,
                        new RepositoryPackages(
                                ImmutableList.of(), ImmutableList.of(r1, r2, r3, r4)),
                        progress);
        assertTrue(result.get(0).equals(r3) || result.get(1).equals(r3));
        assertTrue(result.get(0).equals(r4) || result.get(1).equals(r4));
        assertEquals(r2, result.get(2));
        assertEquals(r1, result.get(3));
        progress.assertNoErrorsOrWarnings();
    }

    /** {r1, r2}->r3. Request both r1 and r2. All should be returned, with r3 first. */
    public void testMultiRequest() {
        FakeRemotePackage r1 = new FakeRemotePackage("r1");
        r1.setDependencies(ImmutableList.of(new FakeDependency("r3")));
        FakeRemotePackage r2 = new FakeRemotePackage("r2");
        r2.setDependencies(ImmutableList.of(new FakeDependency("r3")));
        FakeRemotePackage r3 = new FakeRemotePackage("r3");

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1, r2);
        List<RemotePackage> result =
                InstallerUtil.computeRequiredPackages(
                        request,
                        new RepositoryPackages(ImmutableList.of(), ImmutableList.of(r1, r2, r3)),
                        progress);
        assertEquals(r3, result.get(0));
        assertTrue(result.get(1).equals(r1) || result.get(2).equals(r1));
        assertTrue(result.get(1).equals(r2) || result.get(2).equals(r2));

        progress.assertNoErrorsOrWarnings();
    }

    /** r2->r1. Request both r1 and r2. [r1, r2] should be returned. */
    public void testRequestDependency() {
        FakeRemotePackage r1 = new FakeRemotePackage("r1");
        FakeRemotePackage r2 = new FakeRemotePackage("r2");
        r2.setDependencies(ImmutableList.of(new FakeDependency("r1")));

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1, r2);
        List<RemotePackage> result =
                InstallerUtil.computeRequiredPackages(
                        request,
                        new RepositoryPackages(ImmutableList.of(), ImmutableList.of(r1, r2)),
                        progress);
        assertEquals(ImmutableList.of(r1, r2), result);

        progress.assertNoErrorsOrWarnings();
    }

    /** {r1, r2}->r3. Request both r1 and r2. R3 is installed, so only r1 and r2 are returned. */
    public void testMultiRequestSatisfied() {
        FakeRemotePackage r1 = new FakeRemotePackage("r1");
        r1.setDependencies(ImmutableList.of(new FakeDependency("r3")));
        FakeRemotePackage r2 = new FakeRemotePackage("r2");
        r1.setDependencies(ImmutableList.of(new FakeDependency("r3")));
        FakeLocalPackage r3 = new FakeLocalPackage("r3");

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1, r2);
        List<RemotePackage> result =
                InstallerUtil.computeRequiredPackages(
                        request,
                        new RepositoryPackages(ImmutableList.of(r3), ImmutableList.of(r1, r2)),
                        progress);
        assertEquals(2, result.size());
        assertTrue(result.get(0).equals(r1) || result.get(1).equals(r1));
        assertTrue(result.get(0).equals(r2) || result.get(1).equals(r2));

        progress.assertNoErrorsOrWarnings();
    }

    /**
     * {r1, r2}->r3. Request both r1 and r2. R3 is installed, but r2 requires an update. All should
     * be returned, with r3 before r2.
     */
    public void testMultiRequestHalfSatisfied() {
        FakeRemotePackage r1 = new FakeRemotePackage("r1");
        r1.setDependencies(ImmutableList.of(new FakeDependency("r3")));
        FakeRemotePackage r2 = new FakeRemotePackage("r2");
        r2.setDependencies(ImmutableList.of(new FakeDependency("r3", 2, 0, 0)));
        FakeRemotePackage r3 = new FakeRemotePackage("r3");
        r3.setRevision(new Revision(2));
        FakeLocalPackage l3 = new FakeLocalPackage("r3");

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1, r2);
        List<RemotePackage> result =
                InstallerUtil.computeRequiredPackages(
                        request,
                        new RepositoryPackages(ImmutableList.of(l3), ImmutableList.of(r1, r2, r3)),
                        progress);

        assertTrue(result.get(0).equals(r3) || result.get(1).equals(r3));
        assertTrue(result.contains(r1));
        assertTrue(result.get(1).equals(r2) || result.get(2).equals(r2));

        progress.assertNoErrorsOrWarnings();
    }

    /** r1->r2 with a soft dependency, with r2 not installed. Only r1 should be returned. */
    public void testSoftNotInstalled() {
        FakeRemotePackage r1 = new FakeRemotePackage("r1");
        RemotePackage r2 = new FakeRemotePackage("r2");
        r1.setDependencies(ImmutableList.of(new FakeDependency("r2", 2, 0, 0, true)));

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        ImmutableList<RemotePackage> expected = ImmutableList.of(r1);
        assertEquals(
                expected,
                InstallerUtil.computeRequiredPackages(
                        request,
                        new RepositoryPackages(ImmutableList.of(), ImmutableList.of(r1, r2)),
                        progress));
        progress.assertNoErrorsOrWarnings();
    }

    /**
     * r1->r2 with a soft dependency, with r2 installed but already at the requested version. Only
     * r1 should be returned.
     */
    public void testSoftInstalledSameVersion() {
        FakeRemotePackage r1 = new FakeRemotePackage("r1");
        r1.setDependencies(ImmutableList.of(new FakeDependency("r2", 2, 0, 0, true)));
        FakeRemotePackage r2 = new FakeRemotePackage("r2");
        r2.setRevision(new Revision(2));

        FakeLocalPackage localR2 = new FakeLocalPackage("r2");
        localR2.setRevision(new Revision(2));

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        ImmutableList<RemotePackage> expected = ImmutableList.of(r1);
        assertEquals(
                expected,
                InstallerUtil.computeRequiredPackages(
                        request,
                        new RepositoryPackages(ImmutableList.of(localR2), ImmutableList.of(r1, r2)),
                        progress));
        progress.assertNoErrorsOrWarnings();
    }

    /**
     * r1->r2 with a soft dependency, with r2 installed and not at the requested version. r1 and r2
     * should be returned.
     */
    public void testSoftInstalledLowerVersion() {
        FakeRemotePackage r1 = new FakeRemotePackage("r1");
        r1.setDependencies(ImmutableList.of(new FakeDependency("r2", 3, 0, 0, true)));
        FakeRemotePackage r2 = new FakeRemotePackage("r2");
        r2.setRevision(new Revision(3));

        FakeLocalPackage localR2 = new FakeLocalPackage("r2");
        localR2.setRevision(new Revision(2));

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        ImmutableList<RemotePackage> expected = ImmutableList.of(r2, r1);
        assertEquals(
                expected,
                InstallerUtil.computeRequiredPackages(
                        request,
                        new RepositoryPackages(ImmutableList.of(localR2), ImmutableList.of(r1, r2)),
                        progress));
        progress.assertNoErrorsOrWarnings();
    }

    /** r1->bogus. Null should be returned, and there should be an error. */
    public void testBogus() {
        FakeRemotePackage r1 = new FakeRemotePackage("r1");
        r1.setDependencies(ImmutableList.of(new FakeDependency("bogus")));
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        assertNull(
                InstallerUtil.computeRequiredPackages(
                        request,
                        new RepositoryPackages(ImmutableList.of(), ImmutableList.of(r1)),
                        progress));
        assertFalse(progress.getWarnings().isEmpty());
    }

    /**
     * r1->r2->r3. r3 is installed, but a higher version is required and not available. Null should
     * be returned, and there should be an error.
     */
    public void testUpdateNotAvailable() {
        FakeRemotePackage r1 = new FakeRemotePackage("r1");
        r1.setDependencies(ImmutableList.of(new FakeDependency("r2")));
        FakeRemotePackage r2 = new FakeRemotePackage("r2");
        r2.setDependencies(ImmutableList.of(new FakeDependency("r3", 4, null, null)));
        FakeRemotePackage r3 = new FakeRemotePackage("r3");
        r3.setRevision(new Revision(2));

        FakeProgressIndicator progress = new FakeProgressIndicator();
        ImmutableList<RemotePackage> request = ImmutableList.of(r1);
        assertNull(
                InstallerUtil.computeRequiredPackages(
                        request,
                        new RepositoryPackages(
                                ImmutableList.of(new FakeLocalPackage("r3")),
                                ImmutableList.of(r1, r2, r3)),
                        progress));
        assertFalse(progress.getWarnings().isEmpty());
    }

    public void testInstallInChild() {
        Path sdkRoot = InMemoryFileSystems.createInMemoryFileSystemAndFolder("sdk");
        InMemoryFileSystems.recordExistingFile(
                sdkRoot.resolve("foo/package.xml"),
                "<repo:repository\n"
                        + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                        + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                        + "    <localPackage path=\"foo\">\n"
                        + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                        + "        <revision>\n"
                        + "            <major>3</major>\n"
                        + "        </revision>\n"
                        + "        <display-name>The first Android platform ever</display-name>\n"
                        + "    </localPackage>\n"
                        + "</repo:repository>");
        RepoManager mgr = new RepoManagerImpl();
        mgr.setLocalPath(sdkRoot);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        mgr.loadSynchronously(0, progress, null, null);
        assertFalse(InstallerUtil.checkValidPath(sdkRoot.resolve("foo/bar"), mgr, progress));
        assertFalse(progress.getWarnings().isEmpty());
    }

    public void testInstallInParent() {
        Path sdkRoot = InMemoryFileSystems.createInMemoryFileSystemAndFolder("sdk");
        InMemoryFileSystems.recordExistingFile(
                sdkRoot.resolve("foo/bar/package.xml"),
                "<repo:repository\n"
                        + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                        + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                        + "    <localPackage path=\"foo;bar\">\n"
                        + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                        + "        <revision>\n"
                        + "            <major>3</major>\n"
                        + "        </revision>\n"
                        + "        <display-name>The first Android platform ever</display-name>\n"
                        + "    </localPackage>\n"
                        + "</repo:repository>");
        RepoManager mgr = new RepoManagerImpl();
        mgr.setLocalPath(sdkRoot);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        mgr.loadSynchronously(0, progress, null, null);
        assertFalse(InstallerUtil.checkValidPath(sdkRoot.resolve("foo"), mgr, progress));
        assertFalse(progress.getWarnings().isEmpty());
    }

    public void testInstallSeparately() {
        Path sdkRoot = InMemoryFileSystems.createInMemoryFileSystemAndFolder("sdk");
        InMemoryFileSystems.recordExistingFile(
                sdkRoot.resolve("foo2/package.xml"),
                "<repo:repository\n"
                        + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                        + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                        + "    <localPackage path=\"foo2\">\n"
                        + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                        + "        <revision>\n"
                        + "            <major>3</major>\n"
                        + "        </revision>\n"
                        + "        <display-name>The first Android platform ever</display-name>\n"
                        + "    </localPackage>\n"
                        + "</repo:repository>");
        RepoManager mgr = new RepoManagerImpl();
        mgr.setLocalPath(sdkRoot);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        mgr.loadSynchronously(0, progress, null, null);
        assertTrue(InstallerUtil.checkValidPath(sdkRoot.resolve("foo"), mgr, progress));
        progress.assertNoErrorsOrWarnings();
    }

    public void testInstallSeparately2() {
        Path sdkRoot = InMemoryFileSystems.createInMemoryFileSystemAndFolder("sdk");
        InMemoryFileSystems.recordExistingFile(
                sdkRoot.resolve("foo/package.xml"),
                "<repo:repository\n"
                        + "        xmlns:repo=\"http://schemas.android.com/repository/android/generic/01\"\n"
                        + "        xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                        + "    <localPackage path=\"foo\">\n"
                        + "        <type-details xsi:type=\"repo:genericDetailsType\"/>\n"
                        + "        <revision>\n"
                        + "            <major>3</major>\n"
                        + "        </revision>\n"
                        + "        <display-name>The first Android platform ever</display-name>\n"
                        + "    </localPackage>\n"
                        + "</repo:repository>");
        RepoManager mgr = new RepoManagerImpl();
        mgr.setLocalPath(sdkRoot);
        FakeProgressIndicator progress = new FakeProgressIndicator();
        mgr.loadSynchronously(0, progress, null, null);
        assertTrue(InstallerUtil.checkValidPath(sdkRoot.resolve("foo2"), mgr, progress));
        progress.assertNoErrorsOrWarnings();
    }

    private static void zipDirectory(Path outZip, Path root, boolean includeDirectoryEntries)
            throws IOException {
        try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(outZip.toFile());
                Stream<Path> walk = Files.walk(root)) {
            walk.forEach(
                    path -> {
                        try {
                            if (!includeDirectoryEntries && Files.isDirectory(path)) return;
                            ZipArchiveEntry archiveEntry =
                                    (ZipArchiveEntry)
                                            out.createArchiveEntry(
                                                    path.toFile(),
                                                    root.relativize(path).toString());
                            out.putArchiveEntry(archiveEntry);
                            if (Files.isSymbolicLink(path)) {
                                archiveEntry.setUnixMode(
                                        UnixStat.LINK_FLAG | archiveEntry.getUnixMode());
                                out.write(
                                        path.getParent()
                                                .relativize(Files.readSymbolicLink(path))
                                                .toString()
                                                .getBytes());
                            } else if (!Files.isDirectory(path)) {
                                out.write(Files.readAllBytes(path));
                            }
                            out.closeArchiveEntry();
                        } catch (Exception e) {
                            fail();
                        }
                    });
        }
    }

    public void testUnzip() throws Exception {
        Path root = Files.createTempDirectory("InstallerUtilTest");
        Path outRoot = Files.createTempDirectory("InstallerUtilTest");
        try {
            Path file1 = root.resolve("foo");
            Files.write(file1, "content".getBytes());
            Path dir1 = root.resolve("bar");
            Files.createDirectories(dir1);
            Path file2 = dir1.resolve("baz");
            Files.write(file2, "content2".getBytes());
            Files.createSymbolicLink(root.resolve("link1"), dir1);
            Files.createSymbolicLink(root.resolve("link2"), file2);
            Path file3 = root.resolve("qux");
            Files.write(file3, "content3".getBytes());

            Files.createSymbolicLink(outRoot.resolve("qux"), outRoot.resolve("qux.target"));

            Path outZip = outRoot.resolve("out.zip");
            zipDirectory(outZip, root, true);
            Path unzipped = outRoot.resolve("unzipped");
            Files.createDirectories(unzipped);
            InstallerUtil.unzip(outZip, unzipped, 0, new FakeProgressIndicator(true));
            assertEquals("content", new String(Files.readAllBytes(unzipped.resolve("foo"))));
            Path resultDir = unzipped.resolve("bar");
            Path resultFile2 = resultDir.resolve("baz");
            assertEquals("content2", new String(Files.readAllBytes(resultFile2)));
            Path resultLink = unzipped.resolve("link1");
            assertTrue(Files.isDirectory(resultLink));
            assertTrue(Files.isSymbolicLink(resultLink));
            assertTrue(Files.isSameFile(resultLink, resultDir));
            Path resultLink2 = unzipped.resolve("link2");
            assertEquals("content2", new String(Files.readAllBytes(resultLink2)));
            assertTrue(Files.isSymbolicLink(resultLink2));
            assertTrue(Files.isSameFile(resultLink2, resultFile2));
            Path resultFile3 = unzipped.resolve("qux");
            assertFalse(Files.isSymbolicLink(resultFile3));
            assertEquals("content3", new String(Files.readAllBytes(resultFile3)));
        }
        finally {
            PathUtils.deleteRecursivelyIfExists(root);
            PathUtils.deleteRecursivelyIfExists(outRoot);
        }
    }
    public void testUnzipSymlink() throws Exception {
        Path tmp = Files.createTempDirectory("InstallerUtilTest_testUnzipSymlink");
        Path outDir = tmp.resolve("out");
        Files.createDirectory(outDir);
        Path root = tmp.resolve("to_zip");
        Files.createDirectory(root);
        Path dir1 = root.resolve("dir1");
        Files.createDirectory(dir1);
        Path file = dir1.resolve("file.txt");
        Files.write(file, "content".getBytes());
        Path dir2 = root.resolve("dir2");
        Files.createDirectory(dir2);
        Path link = dir2.resolve("link");
        Files.createSymbolicLink(link, file);
        Path outZip = outDir.resolve("zip with symlink.zip");
        zipDirectory(outZip, root, false);
        Path unzipped = tmp.resolve("unzipped");
        Files.createDirectories(unzipped);

        InstallerUtil.unzip(outZip, unzipped, 0, new FakeProgressIndicator(true));

        Path resultFile = unzipped.resolve("dir1/file.txt");
        assertEquals("content", new String(Files.readAllBytes(resultFile)));
        Path resultLink = unzipped.resolve("dir2/link");
        assertTrue(Files.isRegularFile(resultLink));
        assertTrue(Files.isSymbolicLink(resultLink));
        assertTrue(Files.isSameFile(resultLink, resultFile));
        assertEquals("content", new String(Files.readAllBytes(resultLink)));
    }
}
