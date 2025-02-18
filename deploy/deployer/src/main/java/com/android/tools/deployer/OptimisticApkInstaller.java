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
package com.android.tools.deployer;

import com.android.annotations.NonNull;
import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.model.Apk;
import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.deployer.model.App;
import com.android.tools.idea.protobuf.ByteString;
import com.android.utils.ILogger;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class OptimisticApkInstaller {

    private static final String DUMP_METRIC = "IWI_INSTALL_DUMP";
    private static final String DIFF_METRIC = "IWI_INSTALL_DIFF";
    private static final String EXTRACT_METRIC = "IWI_INSTALL_EXTRACT";
    private static final String UPDATE_METRIC = "IWI_INSTALL_UPDATE_OVERLAYS";

    private final Installer installer;
    private final AdbClient adb;
    private final DeploymentCacheDatabase cache;
    private final MetricsRecorder metrics;
    private final DeployerOption options;
    private final ILogger logger;

    public OptimisticApkInstaller(
            Installer installer,
            AdbClient adb,
            DeploymentCacheDatabase cache,
            MetricsRecorder metrics,
            DeployerOption options,
            ILogger logger) {
        this.installer = installer;
        this.adb = adb;
        this.cache = cache;
        this.metrics = metrics;
        this.options = options;
        this.logger = logger;
    }

    /**
     * Updates the overlay for package {@code packageName} by performing an overlay diff and
     * applying the resulting overlay update to the device. The inputs to the diff are the set of
     * currently installed APKs, the set of newly built APKs, and the current overlay contents.
     *
     * <p>The diff has three steps:
     *
     * <ul>
     *   <li>Diff installed APKs from {@link OverlayId#getInstalledApks()} with {@code apks} to
     *       produce a baseline diff
     *   <li>Compare the baseline diff to the set of current overlay files from {@link
     *       OverlayId#getOverlayContents()} to obtain the "files to add" set (files in diff but NOT
     *       in overlay)
     *   <li>Compare the current overlay files with the new APKs to obtain the * "files to delete"
     *       set (files in overlay but NOT in APKs)
     * </ul>
     *
     * The set of "files to add" are extracted from the APK and pushed to the device. The set of
     * "files to delete" are removed from the overlay.
     */
    public OverlayId install(@NonNull App app, List<String> userFlags) throws DeployerException {
        if (hasInstrumentedTests(app.getApks())) {
            throw DeployerException.runTestsNotSupported();
        }

        // We do not support the case where an app is intended to be sandboxed by the SDK Runtime.
        if (hasSdkLibrary(app.getApks())) {
            throw DeployerException.sdksNotSupported();
        }

        // If the user has specified additional package manager flags, we should use the package
        // manager.
        if (!userFlags.isEmpty()) {
            throw DeployerException.pmFlagsNotSupported();
        }

        try {
            return tracedInstall(app);
        } catch (DeployerException ex) {
            metrics.finish(ex.getError());
            throw ex;
        } catch (Exception ex) {
            DeployerException wrapper = DeployerException.runtimeException(ex);
            metrics.finish(wrapper.getError());
            throw wrapper;
        }
    }

    private OverlayId tracedInstall(@NonNull App app) throws DeployerException {
        final String deviceSerial = adb.getSerial();
        final String targetAbi = adb.getAbiForApks(app.getApks());
        final Deploy.Arch targetArch = AdbClient.getArchForAbi(targetAbi);

        metrics.start(DUMP_METRIC);
        DeploymentCacheDatabase.Entry entry = cache.get(deviceSerial, app.getAppId());

        // If we have no cache data or an install without OID file, we use the classic dump.
        if (entry == null || entry.getOverlayId().isBaseInstall()) {
            ApplicationDumper dumper = new ApplicationDumper(installer);
            List<Apk> deviceApks = dumper.dump(app.getApks()).apks;
            cache.store(deviceSerial, app.getAppId(), deviceApks, new OverlayId(deviceApks));
            entry = cache.get(deviceSerial, app.getAppId());
        }
        metrics.finish();

        metrics.start(DIFF_METRIC);
        final OverlayId overlayId = entry.getOverlayId();
        OverlayDiffer.Result diff =
                new OverlayDiffer(options.optimisticInstallSupport).diff(app.getApks(), overlayId);
        metrics.finish();

        metrics.start(EXTRACT_METRIC);
        List<ApkEntry> filesToAdd = filterIncompatibleNativeLibraries(targetAbi, diff.filesToAdd);
        Map<ApkEntry, ByteString> overlayFiles =
                new ApkEntryExtractor().extractFromEntries(filesToAdd);
        metrics.finish();

        metrics.start(UPDATE_METRIC);
        final OverlayId.Builder nextIdBuilder = OverlayId.builder(overlayId);

        Deploy.OverlayInstallRequest.Builder request =
                Deploy.OverlayInstallRequest.newBuilder()
                        .setPackageName(app.getAppId())
                        .setArch(targetArch)
                        .setExpectedOverlayId(overlayId.isBaseInstall() ? "" : overlayId.getSha());

        for (Map.Entry<ApkEntry, ByteString> file : overlayFiles.entrySet()) {
            request.addOverlayFiles(
                    Deploy.OverlayFile.newBuilder()
                            .setPath(file.getKey().getQualifiedPath())
                            .setContent(file.getValue()));
            nextIdBuilder.addOverlayFile(
                    file.getKey().getQualifiedPath(), file.getKey().getChecksum());
        }

        for (String file : diff.filesToRemove) {
            request.addDeletedFiles(file);
            nextIdBuilder.removeOverlayFile(file);
        }

        OverlayId nextOverlayId = nextIdBuilder.build();
        request.setOverlayId(nextOverlayId.getSha());

        Deploy.OverlayInstallResponse response;
        try {
            response = installer.overlayInstall(request.build());
        } catch (IOException ex) {
            throw DeployerException.installerIoException(ex);
        }

        if (response.getStatus() != Deploy.OverlayInstallResponse.Status.OK) {
            throw DeployerException.installFailed(response.getStatus(), "Overlay update failed");
        }

        metrics.finish();
        metrics.add(response.getAgentLogsList());

        return nextOverlayId;
    }

    private static boolean hasInstrumentedTests(List<Apk> apks) {
        return apks.stream().anyMatch(apk -> !apk.targetPackages.isEmpty());
    }

    private static boolean hasSdkLibrary(List<Apk> apks) {
        return apks.stream().anyMatch(apk -> !apk.sdkLibraries.isEmpty());
    }

    private static List<ApkEntry> filterIncompatibleNativeLibraries(
            String targetAbi, Collection<ApkEntry> entries) {
        return entries.stream()
                .filter(
                        entry -> {
                            Path overlayPath = Paths.get(entry.getName());
                            if (overlayPath.startsWith("lib")) {
                                String abi = overlayPath.getParent().getFileName().toString();
                                return targetAbi.equals(abi);
                            }
                            return true;
                        })
                .collect(Collectors.toList());
    }
}
