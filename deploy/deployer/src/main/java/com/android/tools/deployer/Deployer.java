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

package com.android.tools.deployer;

import com.android.annotations.NonNull;
import com.android.sdklib.AndroidVersion;
import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.model.Apk;
import com.android.tools.deployer.model.ApkEntry;
import com.android.tools.deployer.model.App;
import com.android.tools.deployer.model.FileDiff;
import com.android.tools.deployer.tasks.Canceller;
import com.android.tools.deployer.tasks.Task;
import com.android.tools.deployer.tasks.TaskResult;
import com.android.tools.deployer.tasks.TaskRunner;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.tracer.Trace;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public class Deployer {

    public static final String BASE_DIRECTORY = Sites.deviceStudioFolder();
    public static final String INSTALLER_DIRECTORY = Sites.installerExecutableFolder();
    public static final String INSTALLER_TMP_DIRECTORY = Sites.installerTmpFolder();

    private final AdbClient adb;
    private final SqlApkFileDatabase dexDb;
    private final DeploymentCacheDatabase deployCache;
    private final Installer installer;
    private final TaskRunner runner;
    private final UIService service;
    private final MetricsRecorder metrics;
    private final ILogger logger;
    private final DeployerOption options;

    public Deployer(
            AdbClient adb,
            DeploymentCacheDatabase deployCache,
            SqlApkFileDatabase dexDb,
            TaskRunner runner,
            Installer installer,
            UIService service,
            MetricsRecorder metrics,
            ILogger logger,
            DeployerOption options) {
        this.adb = adb;
        this.deployCache = deployCache;
        this.dexDb = dexDb;
        this.runner = runner;
        this.installer = installer;
        this.service = service;
        this.metrics = metrics;
        this.logger = logger;
        this.options = options;
    }

    enum Tasks {
        CACHE,
        DUMP,
        DIFF,
        PREINSTALL,
        VERIFY,
        COMPARE,
        SWAP,
        APK_CHECK,

        // pipeline 2.0
        PARSE_APP_IDS,
        DEPLOY_CACHE_STORE,
        OPTIMISTIC_DUMP,
        VERIFY_DUMP,
        EXTRACT_APK_ENTRIES,
        COLLECT_SWAP_DATA,
        OPTIMISTIC_SWAP,
        OPTIMISTIC_INSTALL,
        ROOT_PUSH_INSTALL,

        // New DDMLib
        GET_PIDS,
        GET_ARCH,
        COMPUTE_FRESHINSTALL_OID,

        // Coroutine debugger
        INSTALL_COROUTINE_DEBUGGER
    }

    public enum InstallMode {
        DELTA, // If an application is already installed on the a device, send only what has changed.
        DELTA_NO_SKIP, // Delta install but don't skip installation should there be no changes.
        FULL // Send application full apk regardless of the device state.
    }

    /**
     * Information related to a swap or install.
     *
     * <p>Note that there is indication to success or failure of the operation. Failure is indicated
     * by {@link DeployerException} thus this object is created only on successful deployments.
     */
    public static class Result {

        public final boolean skippedInstall;
        public final boolean needsRestart;
        public final boolean coroutineDebuggerInstalled;
        public final @NonNull App app;

        public Result(boolean skippedInstall,
                boolean needsRestart,
                boolean coroutineDebuggerInstalled,
                @NonNull App app) {

            this.skippedInstall = skippedInstall;
            this.needsRestart = needsRestart;
            this.coroutineDebuggerInstalled = coroutineDebuggerInstalled;
            this.app = app;
        }
    }

    private static class InstallInfo {
        public final boolean skippedInstall;
        public final List<Apk> apks;

        public InstallInfo(boolean skippedInstall, List<Apk> apks) {
            this.skippedInstall = skippedInstall;
            this.apks = apks;
        }
    }

    /**
     * Persists the content of the provide APKs on the device. If the operation succeeds, the dex
     * files from the APKs are stored in the dex database.
     *
     * <p>The implementation of the persistence depends on target device API level and the values of
     * the passed-in flags useOptimisticSwap and useOptimisticInstall.
     *
     * <p>When installing on a device with an API level < 30 (pre-R), or if the useOptimisticSwap
     * flag is set to false, a standard install is performed.
     *
     * <p>If useOptimisticSwap is true, but useOptimisticInstall is false, a standard delta install
     * is performed, and the currently cached deployment information for the application/target
     * device is dropped.
     *
     * <p>If both flags are true, an optimistic install is attempted. If the optimistic install
     * fails for any reason, the deployment falls back to the pre-R install path.
     *
     * <p>Setting useOptimisticInstall to true should never impact the success of this method;
     * failures from optimistic installations are recorded in metrics but not thrown up to the top
     * level.
     */
    public Result install(@NonNull App app, InstallOptions installOptions, InstallMode installMode)
            throws DeployerException {
        try (Trace ignored = Trace.begin("install")) {
            Canceller canceller = installOptions.getCancelChecker();
            String sessionUID = UUID.randomUUID().toString();

            InstallInfo info;
            if (options.useRootPushInstall) {
                info = rootPushInstall(sessionUID, app, installOptions, installMode);
            } else if (supportsNewPipeline()) {
                info = optimisticInstall(sessionUID, app, installOptions, installMode);
            } else {
                info = packageManagerInstall(sessionUID, app, installOptions, installMode);
            }

            if (options.skipPostInstallTasks) {
                return new Result(info.skippedInstall, false, false, app);
            }

            Task<Boolean> installCoroutineDebugger = null;
            Task<List<Apk>> parsedApksTask = runner.create(info.apks);
            if (useCoroutineDebugger()) {
                installCoroutineDebugger =
                        runner.create(
                                Tasks.INSTALL_COROUTINE_DEBUGGER,
                                list -> installCoroutineDebugger(app),
                                parsedApksTask);
            }

            CachedDexSplitter splitter = new CachedDexSplitter(dexDb, new D8DexSplitter());
            runner.create(Tasks.CACHE, splitter::cache, parsedApksTask);
            ApkChecker checker = new ApkChecker(sessionUID, logger);
            runner.create(Tasks.APK_CHECK, checker::log, parsedApksTask);
            runner.runAsync(canceller);

            // we call get to make sure the coroutine debugger is installer before the app can start
            boolean coroutineDebuggerInstalled =
                    installCoroutineDebugger != null ? installCoroutineDebugger.get() : false;
            return new Result(info.skippedInstall, false, coroutineDebuggerInstalled, app);
        }
    }

    private InstallInfo packageManagerInstall(
            String deploySessionUID,
            @NonNull App app,
            InstallOptions installOptions,
            InstallMode installMode)
            throws DeployerException {
        logger.info("Deploying with package manager for install session %s", deploySessionUID);
        ApkInstaller apkInstaller = new ApkInstaller(adb, service, installer, logger);
        boolean skippedInstall =
                !apkInstaller.install(app, installOptions, installMode, metrics.getDeployMetrics());
        return new InstallInfo(skippedInstall, app.getApks());
    }

    private InstallInfo rootPushInstall(
            String deploySessionUID,
            @NonNull App app,
            InstallOptions installOptions,
            InstallMode installMode)
            throws DeployerException {
        logger.info("Deploying with root push for install session %s", deploySessionUID);
        Canceller canceller = installOptions.getCancelChecker();

        Task<Boolean> installSuccess =
                runner.create(
                        Tasks.ROOT_PUSH_INSTALL,
                        new RootPushApkInstaller(adb, installer, logger)::install,
                        runner.create(app));

        TaskResult result = runner.run(canceller);
        result.getMetrics().forEach(metrics::add);

        boolean skippedInstall = false;
        if (!result.isSuccess() || !installSuccess.get()) {
            logger.info("Deploying with package manager for install session %s", deploySessionUID);
            ApkInstaller apkInstaller = new ApkInstaller(adb, service, installer, logger);
            skippedInstall =
                    !apkInstaller.install(
                            app, installOptions, installMode, metrics.getDeployMetrics());
        }
        return new InstallInfo(skippedInstall, app.getApks());
    }

    private InstallInfo optimisticInstall(
            String deploySessionUID,
            @NonNull App app,
            InstallOptions installOptions,
            InstallMode installMode)
            throws DeployerException {
        Canceller canceller = installOptions.getCancelChecker();

        // Do not skip installs; we need to ensure overlays are properly cleared.
        installMode = installMode == InstallMode.DELTA ? InstallMode.DELTA_NO_SKIP : installMode;

        Task<App> taskApp = runner.create(app);
        Task<String> deviceSerial = runner.create(adb.getSerial());
        Task<String> packageName = runner.create(app.getAppId());
        Task<List<Apk>> apks = runner.create(app.getApks());
        boolean installSuccess = false;
        if (!options.optimisticInstallSupport.isEmpty()) {
            logger.info("Deploying with optimistic install for session %s", deploySessionUID);
            OptimisticApkInstaller apkInstaller =
                    new OptimisticApkInstaller(
                            installer, adb, deployCache, metrics, options, logger);
            Task<List<String>> userFlags = runner.create(installOptions.getUserFlags());
            Task<OverlayId> overlayId =
                    runner.create(
                            Tasks.OPTIMISTIC_INSTALL, apkInstaller::install, taskApp, userFlags);

            TaskResult result = runner.run(canceller);
            installSuccess = result.isSuccess();
            if (installSuccess) {
                runner.create(
                        Tasks.DEPLOY_CACHE_STORE,
                        deployCache::store,
                        deviceSerial,
                        packageName,
                        apks,
                        overlayId);
            } else {
                logger.info(
                        "Optimistic install session %s did not complete: %s",
                        deploySessionUID, result.getException());
            }
            result.getMetrics().forEach(metrics::add);
        }

        boolean skippedInstall = false;
        if (!installSuccess) {
            logger.info("Deploying with package manager for session %s", deploySessionUID);
            ApkInstaller apkInstaller = new ApkInstaller(adb, service, installer, logger);
            skippedInstall =
                    !apkInstaller.install(
                            app, installOptions, installMode, metrics.getDeployMetrics());
            runner.create(
                    Tasks.DEPLOY_CACHE_STORE, deployCache::invalidate, deviceSerial, packageName);
        }

        runner.runAsync(canceller);
        return new InstallInfo(skippedInstall, app.getApks());
    }

    public Result codeSwap(
            @NonNull App app, Map<Integer, ClassRedefiner> debuggerRedefiners, Canceller canceller)
            throws DeployerException {
        try (Trace ignored = Trace.begin("codeSwap")) {
            if (supportsNewPipeline()) {
                return optimisticSwap(app, false, debuggerRedefiners, canceller);
            } else {
                return swap(app, false, debuggerRedefiners, canceller);
            }
        }
    }

    public Result fullSwap(@NonNull App app, Canceller canceller) throws DeployerException {
        try (Trace ignored = Trace.begin("fullSwap")) {
            if (supportsNewPipeline() && options.useOptimisticResourceSwap) {
                return optimisticSwap(app, true, ImmutableMap.of(), canceller);
            } else {
                return swap(app, true, ImmutableMap.of(), canceller);
            }
        }
    }

    private boolean installCoroutineDebugger(@NonNull App app) {
        try {
            Deploy.Arch arch = AdbClient.getArchForAbi(adb.getAbiForApks(app.getApks()));
            Deploy.InstallCoroutineAgentResponse response =
                    installer.installCoroutineAgent(app.getAppId(), arch);
            return response.getStatus() == Deploy.InstallCoroutineAgentResponse.Status.OK;
        } catch (Exception e) {
            logger.warning(e.getMessage());
            return false;
        }
    }

    /** Returns true if coroutine debugger is enabled in DeployerOptions and API version is P+ */
    private boolean useCoroutineDebugger() {
        // --attach-agent was added on API 28. Furthermore before API 28 there is no guarantee
        // for the code_cache folder to be created during app install.
        return this.options.enableCoroutineDebugger
                && adb.getVersion().isGreaterOrEqualThan(AndroidVersion.VersionCodes.P);
    }

    private Result swap(
            @NonNull App app,
            boolean argRestart,
            Map<Integer, ClassRedefiner> debuggerRedefiners,
            Canceller canceller)
            throws DeployerException {

        if (!adb.getVersion().isGreaterOrEqualThan(AndroidVersion.VersionCodes.O)) {
            throw DeployerException.apiNotSupported();
        }

        String deploySessionUID = UUID.randomUUID().toString();
        logger.info("Deploy Apply " + (argRestart ? "" : "Code ") + "Session %s", deploySessionUID);

        // Inputs
        Task<Boolean> restart = runner.create(argRestart);
        Task<DexSplitter> splitter =
                runner.create(new CachedDexSplitter(dexDb, new D8DexSplitter()));

        Task<List<Apk>> newFiles = runner.create(app.getApks());

        // Get the list of files from the installed app
        Task<ApplicationDumper.Dump> dumps =
                runner.create(Tasks.DUMP, new ApplicationDumper(installer)::dump, newFiles);

        // Calculate the difference between them
        Task<List<FileDiff>> diffs =
                runner.create(
                        Tasks.DIFF,
                        (dump, newApks) -> new ApkDiffer().diff(dump.apks, newApks),
                        dumps,
                        newFiles);

        // Push and pre install the apks
        Task<String> sessionId =
                runner.create(
                        Tasks.PREINSTALL,
                        new ApkPreInstaller(adb, installer, logger)::preinstall,
                        dumps,
                        newFiles,
                        diffs);

        // Verify the changes are swappable and get only the dexes that we can change
        Task<List<FileDiff>> dexDiffs =
                runner.create(Tasks.VERIFY, new SwapVerifier()::verify, diffs, restart);

        // Compare the local vs remote dex files.
        Task<DexComparator.ChangedClasses> toSwap =
                runner.create(Tasks.COMPARE, new DexComparator()::compare, dexDiffs, splitter);

        // Do the swap
        ApkSwapper swapper = new ApkSwapper(installer, debuggerRedefiners, argRestart, adb, logger);
        runner.create(Tasks.SWAP, swapper::swap, swapper::error, dumps, sessionId, toSwap);

        TaskResult result = runner.run(canceller);
        result.getMetrics().forEach(metrics::add);
        // Update the database with the entire new apk. In the normal case this should
        // be a no-op because the dexes that were modified were extracted at comparison time.
        // However if the compare task doesn't get to execute we still update the database.
        // Note we artificially block this task until swap is done.
        if (result.isSuccess()) {
            runner.create(Tasks.CACHE, DexSplitter::cache, splitter, newFiles);

            ApkChecker checker = new ApkChecker(deploySessionUID, logger);
            runner.create(Tasks.APK_CHECK, checker::log, newFiles);

            // Wait only for swap to finish
            runner.runAsync(canceller);
        } else {
            throw result.getException();
        }

        boolean skippedInstall = sessionId.get().equals(ApkPreInstaller.SKIPPED_INSTALLATION);
        return new Result(skippedInstall, false, false, app);
    }

    private Result optimisticSwap(
            @NonNull App app,
            boolean argRestart,
            Map<Integer, ClassRedefiner> redefiners,
            Canceller canceller)
            throws DeployerException {

        if (!adb.getVersion().isGreaterOrEqualThan(AndroidVersion.VersionCodes.O)) {
            throw DeployerException.apiNotSupported();
        }

        String deploySessionUID = UUID.randomUUID().toString();
        logger.info(
                "Deploy Optimistic Apply "
                        + (argRestart ? "Changes." : "Code Changes.")
                        + " Session %s",
                deploySessionUID);

        // Inputs
        Task<Boolean> restart = runner.create(argRestart);
        Task<DexSplitter> splitter =
                runner.create(new CachedDexSplitter(dexDb, new D8DexSplitter()));
        Task<String> deviceSerial = runner.create(adb.getSerial());

        Task<List<Apk>> apks = runner.create(app.getApks());
        // Get the App info. Some from the APK, some from DDMLib.
        Task<String> packageName = runner.create(app.getAppId());
        Task<List<Integer>> pids = runner.create(Tasks.GET_PIDS, adb::getPids, packageName);
        Task<Deploy.Arch> arch = runner.create(Tasks.GET_ARCH, adb::getArch, pids);

        // Get the list of files from the installed app assuming deployment cache is correct.
        Task<DeploymentCacheDatabase.Entry> speculativeDump =
                runner.create(
                        Tasks.OPTIMISTIC_DUMP,
                        this::dumpWithCache,
                        packageName,
                        deviceSerial,
                        apks);

        // Calculate the difference between them speculating the deployment cache is correct.
        Task<List<FileDiff>> diffs =
                runner.create(Tasks.DIFF, new ApkDiffer()::specDiff, speculativeDump, apks);

        // Extract files from the APK for overlays. Currently only extract resources.
        Predicate<String> filter = file -> file.startsWith("res") || file.startsWith("assets");
        Task<Map<ApkEntry, ByteString>> extractedFiles =
                runner.create(
                        Tasks.EXTRACT_APK_ENTRIES,
                        new ApkEntryExtractor(filter)::extractFromDiffs,
                        diffs);

        // Verify the changes are swappable and get only the dexes that we can change
        Task<List<FileDiff>> dexDiffs =
                runner.create(Tasks.VERIFY, new SwapVerifier()::verify, apks, diffs, restart);

        // Compare the local vs remote dex files.
        Task<DexComparator.ChangedClasses> changedClasses =
                runner.create(Tasks.COMPARE, new DexComparator()::compare, dexDiffs, splitter);

        // Perform the swap.
        OptimisticApkSwapper swapper =
                new OptimisticApkSwapper(installer, redefiners, argRestart, options, metrics);
        Task<OptimisticApkSwapper.OverlayUpdate> overlayUpdate =
                runner.create(
                        Tasks.COLLECT_SWAP_DATA,
                        OptimisticApkSwapper.OverlayUpdate::new,
                        speculativeDump,
                        changedClasses,
                        extractedFiles);
        Task<OptimisticApkSwapper.SwapResult> swapResultTask =
                runner.create(
                        Tasks.OPTIMISTIC_SWAP,
                        swapper::optimisticSwap,
                        packageName,
                        pids,
                        arch,
                        overlayUpdate);

        TaskResult result = runner.run(canceller);
        result.getMetrics().forEach(metrics::add);

        if (!result.isSuccess()) {
            throw result.getException();
        }

        // Update the database with the entire new apk. In the normal case this should
        // be a no-op because the dexes that were modified were extracted at comparison time.
        // However if the compare task doesn't get to execute we still update the database.
        // Note we artificially block this task until swap is done.
        runner.create(Tasks.CACHE, DexSplitter::cache, splitter, apks);
        runner.create(
                Tasks.DEPLOY_CACHE_STORE,
                (serial, pkgName, files, swap) ->
                        deployCache.store(serial, app.getAppId(), app.getApks(), swap.overlayId),
                deviceSerial,
                packageName,
                apks,
                swapResultTask);

        ApkChecker checker = new ApkChecker(deploySessionUID, logger);
        runner.create(Tasks.APK_CHECK, checker::log, apks);

        // Wait only for swap to finish
        runner.runAsync(canceller);

        // TODO: May be notify user we IWI'ed.
        // deployResult.didIwi = true;
        boolean needsRestart = options.fastRestartOnSwapFail && !swapResultTask.get().hotswapSucceeded;
        return new Result(false, needsRestart, false, app);
    }

    private DeploymentCacheDatabase.Entry dumpWithCache(
            String packageName, String deviceSerial, List<Apk> apks) throws DeployerException {
        String serial = adb.getSerial();
        DeploymentCacheDatabase.Entry entry = deployCache.get(serial, packageName);
        if (entry != null && !entry.getOverlayId().isBaseInstall()) {
            return entry;
        }

        // If we have no cache data or an install without OID file, we use the classic dump.
        ApplicationDumper dumper = new ApplicationDumper(installer);
        List<Apk> deviceApks = dumper.dump(apks).apks;
        deployCache.store(serial, packageName, deviceApks, new OverlayId(deviceApks));
        return deployCache.get(serial, packageName);
    }

    public boolean supportsNewPipeline() {
        return options.useOptimisticSwap
                && adb.getVersion().getApiLevel() >= AndroidVersion.VersionCodes.R;
    }
}
