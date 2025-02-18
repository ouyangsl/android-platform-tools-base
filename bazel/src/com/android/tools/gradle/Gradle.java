package com.android.tools.gradle;

import com.android.annotations.NonNull;
import com.android.testutils.RepoLinker;
import com.android.testutils.TestUtils;
import com.android.utils.FileUtils;
import com.google.common.base.MoreObjects;
import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;

/**
 * A wrapper to easily call gradle from Java. To use it:
 *
 * <pre>
 *     // Create the wrapper with the distribution to use the project and where to write stuff.
 *     Gradle gradle = new Gradle(project_dir, scratch_dir, gradle_distribution);
 *     // Add local maven repo zips that will be available to the build.
 *     gradle.addRepo(path_to_zip);
 *     // Run a task
 *     gradle.run("assembleDebug");
 *     // Clean the scratch dir and close the daemon
 *     gradle.close();
 * </pre>
 */
public class Gradle implements Closeable {

    @NonNull private final File outDir;
    @NonNull private final File distribution;
    private final File javaHome;
    @NonNull private final File project;
    @NonNull private final List<String> arguments;
    @NonNull private final File repoDir;
    @NonNull private final Set<File> usedGradleUserHomes = new HashSet<>();
    private final boolean useInitScript;

    public Gradle(@NonNull File project, @NonNull File outDir, @NonNull File distribution, File javaHome)
            throws IOException {
        this(project, outDir, distribution, javaHome, true);
    }

    public Gradle(
            @NonNull File project,
            @NonNull File outDir,
            @NonNull File distribution,
            File javaHome,
            boolean useInitScript)
            throws IOException {
        this.project = project;
        this.outDir = outDir;
        this.distribution = distribution;
        this.javaHome = javaHome;
        this.arguments = new LinkedList<>();
        this.useInitScript = useInitScript;

        repoDir = getRepoDir().getAbsoluteFile();

        FileUtils.cleanOutputDir(outDir);
        Files.createDirectories(getBuildDir().toPath());
        if (useInitScript) {
            File initScript = getInitScript().getAbsoluteFile();
            createInitScript(initScript, repoDir);
        }
    }

    public void addRepo(@NonNull File repo) throws Exception {
        addRepo(repo, getRepoDir());
    }

    public static void addRepo(@NonNull File repo, @NonNull File repoDir) throws Exception {
        String name = repo.getName();
        if (name.endsWith(".zip")) {
            unzip(repo, repoDir);
        } else if (name.endsWith(".manifest")) {
            List<String> artifacts = Files.readAllLines(repo.toPath());
            new RepoLinker().link(repoDir.toPath(), artifacts);
        } else {
            throw new IllegalArgumentException("Unknown repo type " + name);
        }
    }

    public void addArgument(@NonNull String argument) {
        arguments.add(argument);
    }

    public void withProfiler() {
        arguments.add("--profile");
    }

    // We need to control YourKit from the init script, because otherwise it's going to write the
    // profiling data on JVM shutdown (using a shutdown hook), and that is not guaranteed to wait.
    public void withYourKit(@NonNull File libraryPath, @NonNull File settings) throws IOException {
        String samplingSettings = new String(Files.readAllBytes(settings.toPath()));
        String content =
                "initscript {\n"
                        + "  dependencies {\n"
                        + "    classpath files(\""
                        + libraryPath.getAbsolutePath().replace("\\", "/")
                        + "\")\n"
                        + "  }\n"
                        + "}\n"
                        + "\n"
                        + "rootProject {\n"
                        + "  def controller = com.yourkit.api.controller.v2.Controller.newBuilder().self().build()\n"
                        + "  def cpusettings = new com.yourkit.api.controller.v2.CpuProfilingSettings()\n"
                        + "  def settings = '''\\\n"
                        + samplingSettings
                        + "'''\n"
                        + "\n"
                        + "  task startProfiling {\n"
                        + "    doLast {\n"
                        + "      cpusettings.setSettings(settings)\n"
                        + "      controller.startSampling(cpusettings)\n"
                        + "    }\n"
                        + "  }\n"
                        + "\n"
                        + "  task captureProfileSnapshot {\n"
                        + "    doLast {\n"
                        + "      controller.stopCpuProfiling()\n"
                        + "      controller.capturePerformanceSnapshot()\n"
                        + "    }\n"
                        + "  }\n"
                        + "}";
        try (FileWriter writer = new FileWriter(getInitScript(), true)) {
            writer.write(content);
        }
    }

    public void startYourKitProfiling() throws IOException {
        run("startProfiling");
    }

    public void captureYourKitSnapshot() throws IOException {
        run("captureProfileSnapshot");
    }

    @NonNull
    private File getInitScript() {
        return new File(outDir, "init.script");
    }

    public void run(String task) throws IOException {
        run(Collections.singletonList(task), System.out, System.err);
    }

    public void run(List<String> tasks) throws IOException {
        run(tasks, System.out, System.err);
    }

    public void run(List<String> tasks, OutputStream out, OutputStream err) throws IOException {
        run(tasks, out, err, getDefaultGradleUserHome().getAbsoluteFile());
    }

    public void run(List<String> tasks, OutputStream out, OutputStream err, File homeDir)
            throws IOException {
        usedGradleUserHomes.add(homeDir);
        File buildDir = getBuildDir().getAbsoluteFile();
        File androidDir = new File(outDir, "_android").getAbsoluteFile();
        Files.createDirectories(androidDir.toPath());
        // gradle tries to write into .m2 so we pass it a tmp one.
        Path tmpLocalMaven = getLocalMavenRepo();

        HashMap<String, String> env = new HashMap<>();

        env.put("ANDROID_SDK_ROOT", new File(TestUtils.getRelativeSdk()).getAbsolutePath());
        env.put("BUILD_DIR", buildDir.getAbsolutePath());
        env.put("ANDROID_PREFS_ROOT", androidDir.getAbsolutePath());

        // needed when running against older AGP versions
        // ANDROID_HOME: old name of ANDROID_SDK_ROOT
        // ANDROID_SDK_HOME: old name of ANDROID_PREFS_ROOT
        env.put("ANDROID_HOME", new File(TestUtils.getRelativeSdk()).getAbsolutePath());
        env.put("ANDROID_SDK_HOME", androidDir.getAbsolutePath());

        // On windows it is needed to set a few more environment variables
        // Variable should be set only if not null to avoid exceptions in Gradle such as
        // "java.lang.IllegalArgumentException: Cannot encode a null string."
        putIfNotNull(env, "SystemRoot", System.getenv("SystemRoot"));
        putIfNotNull(env, "TEMP", System.getenv("TEMP"));
        putIfNotNull(env, "TMP", System.getenv("TMP"));

        List<String> arguments = new ArrayList<>();
        arguments.add("--offline");
        if (useInitScript) {
            arguments.add("--init-script");
            arguments.add(getInitScript().getAbsolutePath());
        }
        arguments.add("-PinjectedMavenRepo=" + repoDir.getAbsolutePath());
        arguments.add("-Dmaven.repo.local=" + tmpLocalMaven.toAbsolutePath().toString());

        // Workaround for issue https://github.com/gradle/gradle/issues/5188
        System.setProperty("gradle.user.home", "");

        arguments.addAll(this.arguments);

        ProjectConnection projectConnection = getProjectConnection(homeDir, project, distribution);
        try {
            BuildLauncher launcher =
                    projectConnection
                            .newBuild()
                            .setEnvironmentVariables(env)
                            .withArguments(arguments)
                            .forTasks(tasks.toArray(new String[0]));
            if (javaHome != null) {
                launcher.setJavaHome(canonicalizeJavaHome(javaHome));
            }
            launcher.setStandardOutput(out);
            launcher.setStandardError(err);
            launcher.run();
        } catch (Exception e) {
            throw new RuntimeException("Gradle invocation failed: " + this.toString(), e);
        } finally {
            projectConnection.close();
        }
    }

    private File canonicalizeJavaHome(File javaHome) throws IOException {
        // Bazel uses a symlink tree of files, follow a known file back to the real location
        return javaHome.toPath().resolve("release").toRealPath().getParent().toFile();
    }

    /** Creates a unique jar and adds it to buildscript classpath. */
    public void modifyBuildscriptClasspath() throws IOException {
        File classpathJar = new File(project, "_changing_buildscript_classpath.jar");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(classpathJar))) {
            zos.putNextEntry(new ZipEntry("resource.txt"));
            UUID uuid = UUID.randomUUID();
            zos.write(uuid.toString().getBytes());
            zos.closeEntry();
        }
        String toAppend =
                "\nallprojects {\n"
                        + "  buildscript {\n"
                        + "    dependencies {\n"
                        + "      classpath(files(\""
                        + FileUtils.toSystemIndependentPath(classpathJar.getAbsolutePath())
                        + "\"))\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n";
        try (FileWriter writer = new FileWriter(getInitScript().getAbsoluteFile(), true)) {
            writer.append(toAppend);
        }
    }

    private static void putIfNotNull(HashMap<String, String> env, String key, String val) {
        if (val != null) {
            env.put(key, val);
        }
    }

    @Override
    public void close() throws IOException {
        // Shut down the daemon so it doesn't hold the lock on any of the files.
        // Note that this is internal Gradle API, but is used by Studio and Intellij so is
        // relatively stable.
        // Because this circumvents the connector we must set gradle.user.home for it to work
        for (File gradleUserHome : usedGradleUserHomes) {
            System.setProperty("gradle.user.home", gradleUserHome.getAbsolutePath());
            DefaultGradleConnector.close();
        }

        maybeCopyProfiles();

        try {
            FileUtils.cleanOutputDir(outDir);
        } catch (Exception e) {
            // Allow this to fail, as it will be cleaned up by the next run (b/77804450)
            // This is an issue on windows without the sandbox and with stricter file locking
            System.err.println(
                    "Failed to cleanup output directory. Will be cleaned up at next invocation");
        }
    }

    private void maybeCopyProfiles() throws IOException {
        Path profiles = project.toPath().resolve("build").resolve("reports").resolve("profile");
        if (!Files.isDirectory(profiles)) {
            return;
        }
        Path destination = TestUtils.getTestOutputDir().resolve("gradle_profiles");
        copyDirectory(profiles, destination);
    }

    private static void copyDirectory(Path from, Path to) throws IOException {
        Files.walkFileTree(
                from,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                            throws IOException {
                        Files.createDirectory(to.resolve(from.relativize(dir)));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        Files.copy(file, to.resolve(from.relativize(file)));
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    private File getDefaultGradleUserHome() {
        return new File(outDir, "_home");
    }

    public File getBuildDir() {
        return new File(outDir, "_build");
    }

    public Path getLocalMavenRepo() {
        return outDir.toPath().resolve("_tmp_local_maven");
    }

    public File getRepoDir() {
        return new File(outDir, "_repo");
    }

    private static void createInitScript(File initScript, File repoDir) throws IOException {
        String content =
                "settingsEvaluated { settings ->\n"
                        + "settings.pluginManagement {\n"
                        + "  repositories {\n"
                        + "    maven { url '"
                        + repoDir.toURI().toString()
                        + "'}\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n"
                        + "allprojects {\n"
                        + "  buildscript {\n"
                        + "    repositories {\n"
                        + "       maven { url '"
                        + repoDir.toURI().toString()
                        + "'}\n"
                        + "    }\n"
                        + "  }\n"
                        + "  repositories {\n"
                        + "    maven {\n"
                        + "      url '"
                        + repoDir.toURI().toString()
                        + "'\n"
                        + "      metadataSources {\n"
                        + "        mavenPom()\n"
                        + "        artifact()\n"
                        + "      }\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n";

        try (FileWriter writer = new FileWriter(initScript)) {
            writer.write(content);
        }
    }

    /** Unzips archive to the specified location. Existing files are overwritten. */
    public static void unzip(File zip, File out) throws IOException {
        byte[] buffer = new byte[1024];
        try (FileInputStream fis = new FileInputStream(zip);
                BufferedInputStream bis = new BufferedInputStream(fis);
                ZipInputStream zis = new ZipInputStream(bis)) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                String fileName = zipEntry.getName();
                File newFile = new File(out, fileName);
                if (!fileName.endsWith("/")) {
                    if (newFile.exists()) {
                        newFile.delete();
                    }
                    newFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zipEntry = zis.getNextEntry();
            }
        }
    }

    @NonNull
    private static ProjectConnection getProjectConnection(
            File home, File projectDirectory, File distribution) {
        return GradleConnector.newConnector()
                .useDistribution(distribution.toURI())
                .useGradleUserHomeDir(home)
                .forProjectDirectory(projectDirectory)
                .connect();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("outDir", outDir)
                .add("distribution", distribution)
                .add("project", project)
                .add("arguments", arguments)
                .toString();
    }
}
