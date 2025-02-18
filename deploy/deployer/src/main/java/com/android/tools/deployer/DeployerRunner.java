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

import com.android.adblib.AdbSession;
import com.android.adblib.tools.AdbLibSessionFactoryKt;
import com.android.annotations.NonNull;
import com.android.ddmlib.AdbInitOptions;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.tools.deployer.model.App;
import com.android.tools.deployer.tasks.Canceller;
import com.android.tools.deployer.tasks.TaskRunner;
import com.android.tools.tracer.Trace;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DeployerRunner {

    private static final int SUCCESS = 0;

    // These values are > 1000 in order to prevent collision with the DeployerException.Error
    // ordinal that is returned if a DeployerException is thrown during deployment.
    private static final int ERR_SPECIFIED_DEVICE_NOT_FOUND = 1002;
    private static final int ERR_NO_MATCHING_DEVICE = 1003;
    private static final int ERR_BAD_ARGS = 1004;

    private static final String DEX_DB_PATH = "/tmp/studio_dex.db";
    private static final String DEPLOY_DB_PATH = "/tmp/studio_deploy.db";

    private static final InstallOptions STUDIO_DEFAULTS =
            InstallOptions.builder().setAllowDebuggable().build();
    private static final InstallOptions MOBILE_INSTALL_DEFAULTS =
            InstallOptions.builder().setAllowDebuggable().setAllowDowngrade().build();
    private final InstallOptions defaultInstallOptions;
    private final DeploymentCacheDatabase cacheDb;
    private final SqlApkFileDatabase dexDb;
    private final MetricsRecorder metrics;
    private final UIService service;

    // Run it from bazel with the following command:
    // bazel run :deployer.runner INSTALL --device=<target device> <package name> <apk 1> <apk 2>
    // ... <apk N>
    public static void main(String[] args) {
        Trace.start();
        Trace.begin("main");

        DeployerRunner runner =
                new DeployerRunner(
                        MOBILE_INSTALL_DEFAULTS,
                        new File(DEPLOY_DB_PATH),
                        new File(DEX_DB_PATH),
                        new AlwaysYesService());

        // When used from CLI, we use adblib to install.
        String[] parameters = Arrays.copyOf(args, args.length + 1);
        parameters[args.length] = DeployRunnerParameters.PARAMETER_CREATE_ADBLIB_SESSION;

        int errorCode = runner.run(parameters);
        Trace.end();
        Trace.flush();
        System.exit(errorCode);
    }

    public DeployerRunner(File deployCacheFile, File databaseFile, UIService service) {
        this(STUDIO_DEFAULTS, deployCacheFile, databaseFile, service);
    }

    private DeployerRunner(
            InstallOptions defaultInstallOptions,
            File deployCacheFile,
            File databaseFile,
            UIService service) {
        this.defaultInstallOptions = defaultInstallOptions;
        this.cacheDb = new DeploymentCacheDatabase(deployCacheFile);
        this.dexDb = new SqlApkFileDatabase(databaseFile, null);
        this.service = service;
        this.metrics = new MetricsRecorder();
    }

    @VisibleForTesting
    public DeployerRunner(
            DeploymentCacheDatabase cacheDb, SqlApkFileDatabase dexDb, UIService service) {
        this.defaultInstallOptions = STUDIO_DEFAULTS;
        this.cacheDb = cacheDb;
        this.dexDb = dexDb;
        this.service = service;
        this.metrics = new MetricsRecorder();
    }

    public int run(String[] args) {
        if (args.length < 3) {
            // The values for --user come directly from the framework's package manager, and is
            // passed directly through to pm.
            System.out.println(
                    "Usage: {install | codeswap | fullswap} [--device=<serial>] [--user=<user"
                            + " id>|all|current] [--adb=<path>] packageName baseApk [splitApk1,"
                            + " splitApk2, ...]");
            return ERR_BAD_ARGS;
        }

        try {
            DeployRunnerParameters parameters = DeployRunnerParameters.parse(args);
            ILogger logger = new StdLogger(parameters.getLogLevel());
            Map<String, IDevice> devices =
                    waitForDevices(
                            parameters.getAdbExecutablePath(),
                            parameters.getTargetDevices(),
                            parameters.getJdwpClientSupport(),
                            logger);

            if (devices.isEmpty()) {
                logger.error(null, "No device connected to ddmlib");
                return ERR_NO_MATCHING_DEVICE;
            }

            for (String expectedDevice : parameters.getTargetDevices()) {
                if (!devices.containsKey(expectedDevice)) {
                    logger.error(null, "Could not find specified device: %s", expectedDevice);
                    return ERR_SPECIFIED_DEVICE_NOT_FOUND;
                }
            }

            for (IDevice device : devices.values()) {
                int status = run(device, parameters, logger);
                if (status != SUCCESS) {
                    logger.error(null, "Error deploying to device: %s", device.getName());
                    return status;
                }
            }

            return SUCCESS;
        } finally {
            AndroidDebugBridge.terminate();
        }
    }

    // Left in to support how DeployService calls us.
    public int run(IDevice device, String[] args, ILogger logger) {
        return run(device, DeployRunnerParameters.parse(args), logger);
    }

    private int run(IDevice device, DeployRunnerParameters parameters, ILogger logger) {
        EnumSet<ChangeType> optimisticInstallSupport = EnumSet.noneOf(ChangeType.class);
        if (parameters.isOptimisticInstall()) {
            optimisticInstallSupport.add(ChangeType.DEX);
            optimisticInstallSupport.add(ChangeType.NATIVE_LIBRARY);
        }

        metrics.getDeployMetrics().clear();

        // Use an adblib connection. This is piggybagging on the adb server guaranteed to be
        // spawned by DDMLIB.
        AdbSession session = null;
        if (parameters.getCreateAdblibSession()) {
            session =
                    AdbLibSessionFactoryKt.createSocketConnectSession(
                            AndroidDebugBridge::getSocketAddress,
                            new DeployerRunnerLoggerFactory(parameters.getLogLevel()));
        }

        AdbClient adb = new AdbClient(device, logger, session);
        Installer installer =
                new AdbInstaller(
                        parameters.getInstallersPath(), adb, metrics.getDeployMetrics(), logger);
        ExecutorService service = Executors.newFixedThreadPool(5);
        TaskRunner runner = new TaskRunner(service);
        DeployerOption deployerOption =
                new DeployerOption.Builder()
                        .setUseOptimisticSwap(true)
                        .setUseOptimisticResourceSwap(true)
                        .setUseStructuralRedefinition(true)
                        .setUseVariableReinitialization(true)
                        .setFastRestartOnSwapFail(false)
                        .setOptimisticInstallSupport(optimisticInstallSupport)
                        .enableCoroutineDebugger(true)
                        .setAllowAssumeVerified(device.getVersion().isGreaterOrEqualThan(35))
                        .skipPostInstallTasks(parameters.getSkipPostInstallTasks())
                        .useRootPushInstall(parameters.getUseRootPushInstall())
                        .build();

        Deployer deployer =
                new Deployer(
                        adb,
                        cacheDb,
                        dexDb,
                        runner,
                        installer,
                        this.service,
                        metrics,
                        logger,
                        deployerOption);
        final Deployer.Result deployResult;
        App app = App.fromPaths(parameters.getApplicationId(), parameters.getApks());
        try {
            if (parameters.getCommands().contains(DeployRunnerParameters.Command.INSTALL)) {
                InstallOptions.Builder options = defaultInstallOptions.toBuilder();

                if (device.supportsFeature(IDevice.HardwareFeature.EMBEDDED)) {
                    options.setGrantAllPermissions();
                }

                Deployer.InstallMode installMode = Deployer.InstallMode.DELTA;
                if (parameters.isForceFullInstall()) {
                    installMode = Deployer.InstallMode.FULL;
                }

                if (parameters.getTargetUserId() != null) {
                    options.setInstallOnUser(parameters.getTargetUserId());
                }

                options.setAssumeVerified(deployerOption.allowAssumeVerified);

                deployResult = deployer.install(app, options.build(), installMode);
            } else if (parameters.getCommands().contains(DeployRunnerParameters.Command.FULLSWAP)) {
                deployResult = deployer.fullSwap(app, Canceller.NO_OP);
            } else if (parameters.getCommands().contains(DeployRunnerParameters.Command.CODESWAP)) {
                deployResult = deployer.codeSwap(app, ImmutableMap.of(), Canceller.NO_OP);
            } else {
                throw new RuntimeException("UNKNOWN command");
            }
            runner.run(Canceller.NO_OP);
            if (parameters.getCommands().contains(DeployRunnerParameters.Command.ACTIVATE)) {
                DeployRunnerParameters.Component component = parameters.getComponentToActivate();
                assert component != null;
                Activator activator = new Activator(deployResult.app, logger);
                activator.activate(
                        component.type, component.name, new LoggerReceiver(logger), device);
            }
        } catch (DeployerException e) {
            String commands =
                    parameters.getCommands().stream()
                            .map(String::valueOf)
                            .map(String::toLowerCase)
                            .collect(Collectors.joining(","));
            logger.error(e, "Not possible to execute " + commands);
            logger.warning(e.getDetails());
            return e.getError().ordinal();
        } finally {
            service.shutdown();
        }
        return SUCCESS;
    }

    public List<DeployMetric> getMetrics() {
        return metrics.getDeployMetrics();
    }

    private Map<String, IDevice> waitForDevices(
            String adbExecutablePath,
            List<String> deviceSerials,
            boolean jdwpClientSupport,
            ILogger logger) {
        try (Trace unused = Trace.begin("waitForDevices()")) {
            int expectedDevices = deviceSerials.isEmpty() ? 1 : deviceSerials.size();
            CountDownLatch latch = new CountDownLatch(expectedDevices);
            ConcurrentHashMap<String, IDevice> devices = new ConcurrentHashMap<>();

            AndroidDebugBridge.IDeviceChangeListener listener =
                    new AndroidDebugBridge.IDeviceChangeListener() {
                        @Override
                        public void deviceConnected(@NonNull IDevice device) {
                            final String serial = device.getSerialNumber();
                            logger.info("Found device with serial: " + serial);
                            if (deviceSerials.isEmpty() || deviceSerials.contains(serial)) {
                                devices.put(serial, device);
                                latch.countDown();
                            }
                        }

                        @Override
                        public void deviceDisconnected(IDevice device) {}

                        @Override
                        public void deviceChanged(IDevice device, int changeMask) {}
                    };

            if (jdwpClientSupport) {
                AndroidDebugBridge.init(AdbInitOptions.DEFAULT);
            } else {
                AndroidDebugBridge.init(
                        AdbInitOptions.builder()
                                .setClientSupportEnabled(false)
                                .useJdwpProxyService(false)
                                .build());
            }

            AndroidDebugBridge.addDeviceChangeListener(listener);

            // This needs to be done *after* we add the listener, or else we risk missing devices.
            AndroidDebugBridge bridge;
            if (adbExecutablePath == null) {
                bridge = AndroidDebugBridge.createBridge(5, TimeUnit.SECONDS);
            } else {
                // The value of forceNewBridge doesn't really matter here, since the bridge is only
                // going to exist for a single deployment.
                bridge =
                        AndroidDebugBridge.createBridge(
                                adbExecutablePath, true, 5, TimeUnit.SECONDS);
            }
            if (bridge == null) {
                logger.error(null, "Could not create debug bridge");
                return Collections.emptyMap();
            }

            try {
                if (latch.await(30, TimeUnit.SECONDS)) {
                    return devices;
                }
                return Collections.emptyMap();
            } catch (InterruptedException e) {
                return Collections.emptyMap();
            } finally {
                AndroidDebugBridge.removeDeviceChangeListener(listener);
            }
        }
    }

    // MI has no way for users to respond to a prompt; just always proceed.
    static class AlwaysYesService implements UIService {
        @Override
        public boolean prompt(String result) {
            return true;
        }

        @Override
        public void message(String message) {}
    }
}
