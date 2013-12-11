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

package com.android.tools.gradle.eclipse;

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.DOT_JAVA;
import static com.android.tools.gradle.eclipse.GradleImport.ANDROID_GRADLE_PLUGIN;
import static com.android.tools.gradle.eclipse.GradleImport.IMPORT_SUMMARY_TXT;
import static com.android.tools.gradle.eclipse.GradleImport.MAVEN_REPOSITORY;
import static com.android.tools.gradle.eclipse.GradleImport.NL;
import static com.android.tools.gradle.eclipse.ImportSummary.MSG_FOOTER;
import static com.android.tools.gradle.eclipse.ImportSummary.MSG_HEADER;
import static com.android.tools.gradle.eclipse.ImportSummary.MSG_MANIFEST;
import static com.android.tools.gradle.eclipse.ImportSummary.MSG_MISSING_REPO_1;
import static com.android.tools.gradle.eclipse.ImportSummary.MSG_MISSING_REPO_2;
import static com.android.tools.gradle.eclipse.ImportSummary.MSG_MOVED;
import static com.android.tools.gradle.eclipse.ImportSummary.MSG_REPLACED_JARS;
import static com.android.tools.gradle.eclipse.ImportSummary.MSG_REPLACED_LIBS;
import static com.android.tools.gradle.eclipse.ImportSummary.MSG_UNHANDLED;
import static com.google.common.base.Charsets.UTF_8;
import static java.io.File.separator;
import static java.io.File.separatorChar;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.SdkUtils;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

// TODO:
// -- Test what happens if we're missing a project.properties file
// -- Test what happens when we have a broken AndroidManifest file
// -- Test resolving classpath variables
// -- Test resolving workspace paths
// -- Test resolving compiler options (1.6 vs 1.7)
// -- Test what happens when you have jars in libs as well as in .classpath
// -- Test what happens with absolute paths in .classpath references
// -- Test proguard
// -- Test whether we can depend on a Java library which depends on another
//    Java library (e.g. through only .classpath dependency, no project.properties file)
// -- Resolve the gradle wrapper location issue, and hook up gradle-building these projects
// -- Test version extraction for libraries like joda-time, guava-11.0.1.jar, etc
// -- Test what happens if you depend on both play services and contain gcm.jar; should
//    not repeat play services dependency

public class GradleImportTest extends TestCase {
    private static File createProject(String name, String pkg) throws IOException {
        File dir = Files.createTempDir();
        return createProject(dir, name, pkg);
    }

    private static File createProject(File dir, String name, String pkg) throws IOException {
        createDotProject(dir, name, true);
        File src = new File("src");
        File gen = new File("gen");

        createSampleJavaSource(dir, "src", pkg, "MyActivity");
        createSampleJavaSource(dir, "gen", pkg, "R");

        createClassPath(dir,
                new File("bin", "classes"),
                Arrays.<File>asList(src, gen),
                Collections.<File>emptyList());
        createProjectProperties(dir, "android-17", null, null, null,
                Collections.<File>emptyList());
        createAndroidManifest(dir, pkg, 8, 16, null);

        createDefaultStrings(dir);
        createDefaultIcon(dir);

        return dir;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testBasic() throws Exception {
        File projectDir = createProject("test1", "test.pkg");

        // Add some files in there that we are ignoring
        new File(projectDir, "ic_launcher-web.png").createNewFile();
        new File(projectDir, "Android.mk").createNewFile();
        new File(projectDir, "build.properties").createNewFile();

        // Project being imported
        assertEquals(""
                + ".classpath\n"
                + ".project\n"
                + "Android.mk\n"
                + "AndroidManifest.xml\n"
                + "build.properties\n"
                + "gen\n"
                + "  test\n"
                + "    pkg\n"
                + "      R.java\n"
                + "ic_launcher-web.png\n"
                + "project.properties\n"
                + "res\n"
                + "  drawable\n"
                + "    ic_launcher.xml\n"
                + "  values\n"
                + "    strings.xml\n"
                + "src\n"
                + "  test\n"
                + "    pkg\n"
                + "      MyActivity.java\n",
                fileTree(projectDir, true));

        File imported = checkProject(projectDir, ""
                + MSG_HEADER
                + MSG_UNHANDLED
                + "* Android.mk\n"
                + "* build.properties\n"
                + "* ic_launcher-web.png\n"
                + MSG_MOVED
                + "* AndroidManifest.xml => test1/src/main/AndroidManifest.xml\n"
                + "* res/ => test1/src/main/res/\n"
                + "* src/ => test1/src/main/java/\n"
                + MSG_FOOTER,
                true /* checkBuild */);

        // Imported contents
        assertEquals(""
                + "build.gradle\n"
                + "import-summary.txt\n"
                + "settings.gradle\n"
                + "test1\n"
                + "  build.gradle\n"
                + "  src\n"
                + "    main\n"
                + "      AndroidManifest.xml\n"
                + "      java\n"
                + "        test\n"
                + "          pkg\n"
                + "            MyActivity.java\n"
                + "      res\n"
                + "        drawable\n"
                + "          ic_launcher.xml\n"
                + "        values\n"
                + "          strings.xml\n",
                fileTree(imported, true));

        deleteDir(projectDir);
        deleteDir(imported);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testMoveRsAndAidl() throws Exception {
        File projectDir = createProject("test1", "test.pkg");
        createSampleAidlFile(projectDir, "src", "test.pkg");
        createSampleRsFile(projectDir, "src", "test.pkg");

        // Project being imported
        assertEquals(""
                + ".classpath\n"
                + ".project\n"
                + "AndroidManifest.xml\n"
                + "gen\n"
                + "  test\n"
                + "    pkg\n"
                + "      R.java\n"
                + "project.properties\n"
                + "res\n"
                + "  drawable\n"
                + "    ic_launcher.xml\n"
                + "  values\n"
                + "    strings.xml\n"
                + "src\n"
                + "  test\n"
                + "    pkg\n"
                + "      IHardwareService.aidl\n"
                + "      MyActivity.java\n"
                + "      latency.rs\n",
                fileTree(projectDir, true));

        File imported = checkProject(projectDir, ""
                + MSG_HEADER
                + MSG_MOVED
                + "* AndroidManifest.xml => test1/src/main/AndroidManifest.xml\n"
                + "* res/ => test1/src/main/res/\n"
                + "* src/ => test1/src/main/java/\n"
                + "* src/test/pkg/IHardwareService.aidl => test1/src/main/aidl/test/pkg/IHardwareService.aidl\n"
                + "* src/test/pkg/latency.rs => test1/src/main/rs/latency.rs\n"
                + MSG_FOOTER,
                true /* checkBuild */);

        // Imported contents
        assertEquals(""
                + "build.gradle\n"
                + "import-summary.txt\n"
                + "settings.gradle\n"
                + "test1\n"
                + "  build.gradle\n"
                + "  src\n"
                + "    main\n"
                + "      AndroidManifest.xml\n"
                + "      aidl\n"
                + "        test\n"
                + "          pkg\n"
                + "            IHardwareService.aidl\n"
                + "      java\n"
                + "        test\n"
                + "          pkg\n"
                + "            MyActivity.java\n"
                + "      res\n"
                + "        drawable\n"
                + "          ic_launcher.xml\n"
                + "        values\n"
                + "          strings.xml\n"
                + "      rs\n"
                + "        latency.rs\n",
                fileTree(imported, true));

        deleteDir(projectDir);
        deleteDir(imported);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testLibraries() throws Exception {
        File root = Files.createTempDir();
        File app = createLibrary(root, "test.lib2.pkg");

        // ADT Directory structure created by the above:
        assertEquals(""
                + "App\n"
                + "  .classpath\n"
                + "  .gitignore\n"
                + "  .project\n"
                + "  AndroidManifest.xml\n"
                + "  gen\n"
                + "    test\n"
                + "      pkg\n"
                + "        R.java\n"
                + "  project.properties\n"
                + "  res\n"
                + "    drawable\n"
                + "      ic_launcher.xml\n"
                + "    values\n"
                + "      strings.xml\n"
                + "  src\n"
                + "    test\n"
                + "      pkg\n"
                + "        MyActivity.java\n"
                + "Lib1\n"
                + "  .classpath\n"
                + "  .project\n"
                + "  AndroidManifest.xml\n"
                + "  gen\n"
                + "    test\n"
                + "      lib\n"
                + "        pkg\n"
                + "          R.java\n"
                + "  project.properties\n"
                + "  src\n"
                + "    test\n"
                + "      lib\n"
                + "        pkg\n"
                + "          MyLibActivity.java\n"
                + "Lib2\n"
                + "  .classpath\n"
                + "  .project\n"
                + "  AndroidManifest.xml\n"
                + "  gen\n"
                + "    test\n"
                + "      lib2\n"
                + "        pkg\n"
                + "          R.java\n"
                + "  project.properties\n"
                + "  src\n"
                + "    test\n"
                + "      lib2\n"
                + "        pkg\n"
                + "          MyLib2Activity.java\n"
                + "subdir1\n"
                + "  subdir2\n"
                + "    JavaLib\n"
                + "      .classpath\n"
                + "      .gitignore\n"
                + "      .project\n"
                + "      src\n"
                + "        test\n"
                + "          lib2\n"
                + "            pkg\n"
                + "              Utilities.java\n",
                fileTree(root, true));

        File imported = checkProject(app, ""
                + MSG_HEADER
                + MSG_MANIFEST
                + MSG_UNHANDLED
                + "From App:\n"
                + "* .gitignore\n"
                + "From JavaLib:\n"
                + "* .gitignore\n"
                + MSG_MOVED
                + "In JavaLib:\n"
                + "* src/ => javaLib/src/main/java/\n"
                + "In Lib2:\n"
                + "* AndroidManifest.xml => lib2/src/main/AndroidManifest.xml\n"
                + "* src/ => lib2/src/main/java/\n"
                + "In App:\n"
                + "* AndroidManifest.xml => app/src/main/AndroidManifest.xml\n"
                + "* res/ => app/src/main/res/\n"
                + "* src/ => app/src/main/java/\n"
                + MSG_FOOTER,
                true /* checkBuild */);

        // Imported project
        assertEquals(""
                + "app\n"
                + "  build.gradle\n"
                + "  src\n"
                + "    main\n"
                + "      AndroidManifest.xml\n"
                + "      java\n"
                + "        test\n"
                + "          pkg\n"
                + "            MyActivity.java\n"
                + "      res\n"
                + "        drawable\n"
                + "          ic_launcher.xml\n"
                + "        values\n"
                + "          strings.xml\n"
                + "build.gradle\n"
                + "import-summary.txt\n"
                + "javaLib\n"
                + "  build.gradle\n"
                + "  src\n"
                + "    main\n"
                + "      java\n"
                + "        test\n"
                + "          lib2\n"
                + "            pkg\n"
                + "              Utilities.java\n"
                + "lib2\n"
                + "  build.gradle\n"
                + "  src\n"
                + "    main\n"
                + "      AndroidManifest.xml\n"
                + "      java\n"
                + "        test\n"
                + "          lib2\n"
                + "            pkg\n"
                + "              MyLib2Activity.java\n"
                + "settings.gradle\n",
                fileTree(imported, true));

        // Let's peek at some of the key files to make sure we codegen'ed the right thing
        assertEquals(""
                + "buildscript {\n"
                + "    repositories {\n"
                + "        " + MAVEN_REPOSITORY + "\n"
                + "    }\n"
                + "}\n"
                + "apply plugin: 'java'\n",
                Files.toString(new File(imported, "javaLib" + separator + "build.gradle"), UTF_8)
                        .replace(NL, "\n"));
        assertEquals(""
                + "buildscript {\n"
                + "    repositories {\n"
                + "        " + MAVEN_REPOSITORY + "\n"
                + "    }\n"
                + "    dependencies {\n"
                + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                + "    }\n"
                + "}\n"
                + "apply plugin: 'android'\n"
                + "\n"
                + "repositories {\n"
                + "    " + MAVEN_REPOSITORY + "\n"
                + "}\n"
                + "\n"
                + "android {\n"
                + "    compileSdkVersion 17\n"
                + "    buildToolsVersion \"19.0.1\"\n"
                + "\n"
                + "    defaultConfig {\n"
                + "        minSdkVersion 8\n"
                + "        targetSdkVersion 16\n"
                + "    }\n"
                + "\n"
                + "    buildTypes {\n"
                + "        release {\n"
                + "            runProguard false\n"
                + "            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'\n"
                + "        }\n"
                + "    }\n"
                + "}\n"
                + "\n"
                + "dependencies {\n"
                + "}",
                Files.toString(new File(imported, "app" + separator + "build.gradle"), UTF_8)
                        .replace(NL,"\n"));
        assertEquals(""
                + "buildscript {\n"
                + "    repositories {\n"
                + "        " + MAVEN_REPOSITORY + "\n"
                + "    }\n"
                + "    dependencies {\n"
                + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                + "    }\n"
                + "}\n"
                + "apply plugin: 'android-library'\n"
                + "\n"
                + "repositories {\n"
                + "    " + MAVEN_REPOSITORY + "\n"
                + "}\n"
                + "\n"
                + "android {\n"
                + "    compileSdkVersion 18\n"
                + "    buildToolsVersion \"19.0.1\"\n"
                + "\n"
                + "    defaultConfig {\n"
                + "        minSdkVersion 8\n"
                + "        targetSdkVersion 8\n"
                + "    }\n"
                + "\n"
                + "    release {\n"
                + "        runProguard false\n"
                + "        proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'\n"
                + "    }\n"
                + "}\n"
                + "\n"
                + "dependencies {\n"
                + "}",
                Files.toString(new File(imported, "lib2" + separator + "build.gradle"), UTF_8)
                        .replace(NL, "\n"));
        assertEquals(""
                + "include ':javaLib'\n"
                + "include ':lib2'\n"
                + "include ':app'\n",
                Files.toString(new File(imported, "settings.gradle"), UTF_8)
                        .replace(NL, "\n"));

        deleteDir(root);
        deleteDir(imported);
    }

    // Used by testLibraries
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static File createLibrary(File root, String lib2Pkg) throws IOException {
        // Plain Java library, used by Library 1 and App
        String javaLibName = "JavaLib";
        String javaLibRelative = "subdir1" + separator + "subdir2" + separator + javaLibName;
        File javaLib = new File(root, javaLibRelative);
        javaLib.mkdirs();
        String javaLibPkg = "test.lib2.pkg";
        createDotProject(javaLib, javaLibName, false);
        File javaLibSrc = new File("src");
        createSampleJavaSource(javaLib, "src", javaLibPkg, "Utilities");
        createClassPath(javaLib,
                new File("bin"),
                Collections.singletonList(javaLibSrc),
                Collections.<File>emptyList());

        // Make Android library 1

        String lib1Name = "Lib1";
        File lib1 = new File(root, lib1Name);
        lib1.mkdirs();
        String lib1Pkg = "test.lib.pkg";
        createDotProject(lib1, lib1Name, true);
        File lib1Src = new File("src");
        File lib1Gen = new File("gen");
        createSampleJavaSource(lib1, "src", lib1Pkg, "MyLibActivity");
        createSampleJavaSource(lib1, "gen", lib1Pkg, "R");
        createClassPath(lib1,
                new File("bin", "classes"),
                Arrays.<File>asList(lib1Src, lib1Gen),
                Collections.<File>emptyList());
        createProjectProperties(lib1, "android-19", null, true, null,
                Collections.<File>singletonList(new File(".." + separator + javaLibRelative)));
        createAndroidManifest(lib1, lib1Pkg, -1, -1, "");

        String lib2Name = "Lib2";
        File lib2 = new File(root, lib2Name);
        lib2.mkdirs();
        createDotProject(lib2, lib2Name, true);
        File lib2Src = new File("src");
        File lib2Gen = new File("gen");
        createSampleJavaSource(lib2, "src", lib2Pkg, "MyLib2Activity");
        createSampleJavaSource(lib2, "gen", lib2Pkg, "R");
        createClassPath(lib2,
                new File("bin", "classes"),
                Arrays.<File>asList(lib2Src, lib2Gen),
                Collections.<File>emptyList());
        createProjectProperties(lib2, "android-18", null, true, null,
                Collections.<File>singletonList(new File(".." + separator + lib1Name)));
        createAndroidManifest(lib2, lib2Pkg, 7, -1, "");

        // Main app project, depends on library1, library2 and java lib
        String appName = "App";
        File app = new File(root, appName);
        app.mkdirs();
        String appPkg = "test.pkg";
        createDotProject(app, appName, true);
        File appSrc = new File("src");
        File appGen = new File("gen");
        createSampleJavaSource(app, "src", appPkg, "MyActivity");
        createSampleJavaSource(app, "gen", appPkg, "R");
        createClassPath(app,
                new File("bin", "classes"),
                Arrays.<File>asList(appSrc, appGen),
                Collections.<File>emptyList());
        createProjectProperties(app, "android-17", null, null, null,
                Arrays.<File>asList(
                        new File(".." + separator + lib1Name),
                        new File(".." + separator + lib2Name),
                        new File(".." + separator + javaLibRelative)));
        createAndroidManifest(app, appPkg, 8, 16, null);
        createDefaultStrings(app);
        createDefaultIcon(app);

        // Add some files in there that we are ignoring
        new File(app, ".gitignore").createNewFile();
        new File(javaLib, ".gitignore").createNewFile();
        return app;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testReplaceJar() throws Exception {
        // Add in some well known jars and make sure they get migrated as dependencies
        File projectDir = createProject("test1", "test.pkg");
        File libs = new File(projectDir, "libs");
        libs.mkdirs();
        new File(libs, "android-support-v4.jar").createNewFile();
        new File(libs, "android-support-v7-gridlayout.jar").createNewFile();
        new File(libs, "android-support-v7-appcompat.jar").createNewFile();

        File imported = checkProject(projectDir, ""
                + MSG_HEADER
                + MSG_REPLACED_JARS
                + "android-support-v4.jar => com.android.support:support-v4:+\n"
                + "android-support-v7-appcompat.jar => com.android.support:appcompat-v7:+\n"
                + "android-support-v7-gridlayout.jar => com.android.support:gridlayout-v7:+\n"
                + MSG_MOVED
                + "* AndroidManifest.xml => test1/src/main/AndroidManifest.xml\n"
                + "* res/ => test1/src/main/res/\n"
                + "* src/ => test1/src/main/java/\n"
                + MSG_FOOTER,
                true /* checkBuild */);

        // Imported contents
        assertEquals(""
                + "build.gradle\n"
                + "import-summary.txt\n"
                + "settings.gradle\n"
                + "test1\n"
                + "  build.gradle\n"
                + "  src\n"
                + "    main\n"
                + "      AndroidManifest.xml\n"
                + "      java\n"
                + "        test\n"
                + "          pkg\n"
                + "            MyActivity.java\n"
                + "      res\n"
                + "        drawable\n"
                + "          ic_launcher.xml\n"
                + "        values\n"
                + "          strings.xml\n",
                fileTree(imported, true));

        assertEquals(""
                + "buildscript {\n"
                + "    repositories {\n"
                + "        " + MAVEN_REPOSITORY + "\n"
                + "    }\n"
                + "    dependencies {\n"
                + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                + "    }\n"
                + "}\n"
                + "apply plugin: 'android'\n"
                + "\n"
                + "repositories {\n"
                + "    " + MAVEN_REPOSITORY + "\n"
                + "}\n"
                + "\n"
                + "android {\n"
                + "    compileSdkVersion 17\n"
                + "    buildToolsVersion \"19.0.1\"\n"
                + "\n"
                + "    defaultConfig {\n"
                + "        minSdkVersion 8\n"
                + "        targetSdkVersion 16\n"
                + "    }\n"
                + "\n"
                + "    buildTypes {\n"
                + "        release {\n"
                + "            runProguard false\n"
                + "            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'\n"
                + "        }\n"
                + "    }\n"
                + "}\n"
                + "\n"
                + "dependencies {\n"
                + "    compile 'com.android.support:support-v4:+'\n"
                + "    compile 'com.android.support:appcompat-v7:+'\n"
                + "    compile 'com.android.support:gridlayout-v7:+'\n"
                + "}",
                Files.toString(new File(imported, "test1" + separator + "build.gradle"), UTF_8)
                        .replace(NL, "\n"));

        deleteDir(projectDir);
        deleteDir(imported);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testOptions() throws Exception {
        // Check options like turning off jar replacement and leaving module names capitalized
        File projectDir = createProject("Test1", "test.pkg");
        File libs = new File(projectDir, "libs");
        libs.mkdirs();
        new File(libs, "android-support-v4.jar").createNewFile();
        new File(libs, "android-support-v7-gridlayout.jar").createNewFile();
        new File(libs, "android-support-v7-appcompat.jar").createNewFile();

        File imported = checkProject(projectDir, ""
                + MSG_HEADER
                + MSG_MOVED
                + "* AndroidManifest.xml => Test1/src/main/AndroidManifest.xml\n"
                + "* res/ => Test1/src/main/res/\n"
                + "* src/ => Test1/src/main/java/\n"
                + MSG_FOOTER,
                true /* checkBuild */,
                new ImportCustomizer() {
                    @Override
                    public void customize(GradleImport importer) {
                        importer.setGradleNameStyle(false);
                        importer.setReplaceJars(false);
                        importer.setReplaceLibs(false);
                    }
                });

        // Imported contents
        assertEquals(""
                + "Test1\n"
                + "  build.gradle\n"
                + "  src\n"
                + "    main\n"
                + "      AndroidManifest.xml\n"
                + "      java\n"
                + "        test\n"
                + "          pkg\n"
                + "            MyActivity.java\n"
                + "      res\n"
                + "        drawable\n"
                + "          ic_launcher.xml\n"
                + "        values\n"
                + "          strings.xml\n"
                + "build.gradle\n"
                + "import-summary.txt\n"
                + "settings.gradle\n",
                fileTree(imported, true));

        assertEquals(""
                + "buildscript {\n"
                + "    repositories {\n"
                + "        " + MAVEN_REPOSITORY + "\n"
                + "    }\n"
                + "    dependencies {\n"
                + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                + "    }\n"
                + "}\n"
                + "apply plugin: 'android'\n"
                + "\n"
                + "repositories {\n"
                + "    " + MAVEN_REPOSITORY + "\n"
                + "}\n"
                + "\n"
                + "android {\n"
                + "    compileSdkVersion 17\n"
                + "    buildToolsVersion \"19.0.1\"\n"
                + "\n"
                + "    defaultConfig {\n"
                + "        minSdkVersion 8\n"
                + "        targetSdkVersion 16\n"
                + "    }\n"
                + "\n"
                + "    buildTypes {\n"
                + "        release {\n"
                + "            runProguard false\n"
                + "            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'\n"
                + "        }\n"
                + "    }\n"
                + "}\n"
                + "\n"
                + "dependencies {\n"
                + "    compile files('libs/android-support-v4.jar')\n"
                + "    compile files('libs/android-support-v7-appcompat.jar')\n"
                + "    compile files('libs/android-support-v7-gridlayout.jar')\n"
                + "}",
                Files.toString(new File(imported, "test1" + separator + "build.gradle"), UTF_8)
                        .replace(NL, "\n"));

        deleteDir(projectDir);
        deleteDir(imported);
    }


    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testReplaceSourceLibraryProject() throws Exception {
        // Make a library project which looks like it can just be replaced by a project

        File root = Files.createTempDir();
        // Pretend lib2 is ActionBarSherlock; it should then be stripped out and replaced
        // by a set of dependencies
        File app = createLibrary(root, "com.actionbarsherlock");

        File imported = checkProject(app, ""
                + MSG_HEADER
                + MSG_MANIFEST
                + MSG_UNHANDLED
                + "From App:\n"
                + "* .gitignore\n"
                + "From JavaLib:\n"
                + "* .gitignore\n"
                + MSG_REPLACED_LIBS
                + "Lib2 =>\n"
                + "    com.actionbarsherlock:actionbarsherlock:4.4.0@aar\n"
                + "    com.android.support:support-v4:+\n"
                + MSG_MOVED
                // TODO: The summary should describe the library!!
                + "In JavaLib:\n"
                + "* src/ => javaLib/src/main/java/\n"
                + "In App:\n"
                + "* AndroidManifest.xml => app/src/main/AndroidManifest.xml\n"
                + "* res/ => app/src/main/res/\n"
                + "* src/ => app/src/main/java/\n"
                + MSG_FOOTER,
                false /* checkBuild */);

        // Imported project; note how lib2 is gone
        assertEquals(""
                + "app\n"
                + "  build.gradle\n"
                + "  src\n"
                + "    main\n"
                + "      AndroidManifest.xml\n"
                + "      java\n"
                + "        test\n"
                + "          pkg\n"
                + "            MyActivity.java\n"
                + "      res\n"
                + "        drawable\n"
                + "          ic_launcher.xml\n"
                + "        values\n"
                + "          strings.xml\n"
                + "build.gradle\n"
                + "import-summary.txt\n"
                + "javaLib\n"
                + "  build.gradle\n"
                + "  src\n"
                + "    main\n"
                + "      java\n"
                + "        test\n"
                + "          lib2\n"
                + "            pkg\n"
                + "              Utilities.java\n"
                + "settings.gradle\n",
                fileTree(imported, true));

        assertEquals(""
                + "buildscript {\n"
                + "    repositories {\n"
                + "        " + MAVEN_REPOSITORY + "\n"
                + "    }\n"
                + "    dependencies {\n"
                + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                + "    }\n"
                + "}\n"
                + "apply plugin: 'android'\n"
                + "\n"
                + "repositories {\n"
                + "    " + MAVEN_REPOSITORY + "\n"
                + "}\n"
                + "\n"
                + "android {\n"
                + "    compileSdkVersion 17\n"
                + "    buildToolsVersion \"19.0.1\"\n"
                + "\n"
                + "    defaultConfig {\n"
                + "        minSdkVersion 8\n"
                + "        targetSdkVersion 16\n"
                + "    }\n"
                + "\n"
                + "    buildTypes {\n"
                + "        release {\n"
                + "            runProguard false\n"
                + "            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'\n"
                + "        }\n"
                + "    }\n"
                + "}\n"
                + "\n"
                + "dependencies {\n"
                + "    compile 'com.actionbarsherlock:actionbarsherlock:4.4.0@aar'\n"
                + "    compile 'com.android.support:support-v4:+'\n"
                + "}",
                Files.toString(new File(imported, "app" + separator + "build.gradle"), UTF_8)
                        .replace(NL, "\n"));

        deleteDir(root);
        deleteDir(imported);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testMissingRepositories() throws Exception {
        File projectDir = createProject("test1", "test.pkg");
        final File sdkLocation = Files.createTempDir(); // fake

        File imported = checkProject(projectDir, ""
                + MSG_HEADER
                + MSG_MOVED
                + "* AndroidManifest.xml => test1/src/main/AndroidManifest.xml\n"
                + "* res/ => test1/src/main/res/\n"
                + "* src/ => test1/src/main/java/\n"
                + MSG_MISSING_REPO_1
                + sdkLocation.getPath() + "\n"
                + MSG_MISSING_REPO_2
                + MSG_FOOTER,
                true /* checkBuild */, new ImportCustomizer() {
            @Override
            public void customize(GradleImport importer) {
                importer.setSdkLocation(sdkLocation);
            }
        });

        deleteDir(projectDir);
        deleteDir(imported);
        deleteDir(sdkLocation);
    }

    // --- Unit test infrastructure from this point on ----

    interface ImportCustomizer {
        void customize(GradleImport importer);
    }

    private static File checkProject(File adtProjectDir,
            String expectedSummary, boolean checkBuild) throws Exception {
        return checkProject(adtProjectDir, expectedSummary, checkBuild, null);
    }

    private static File checkProject(File adtProjectDir,
            String expectedSummary, boolean checkBuild,
            ImportCustomizer customizer) throws Exception {
        File destDir = Files.createTempDir();
        assertTrue(GradleImport.isAdtProjectDir(adtProjectDir));
        List<File> projects = Collections.singletonList(adtProjectDir);
        GradleImport importer = new GradleImport();
        if (customizer != null) {
            customizer.customize(importer);
        }
        importer.importProjects(projects);

        // TODO: Find the gradle wrapper resources and assign to them here?
        // importer.setGradleWrapperLocation(<derived location>
        //        new File("$AOSP/tools/base/templates/gradle/wrapper"));
        importer.exportProject(destDir, false);
        String summary = Files.toString(new File(destDir, IMPORT_SUMMARY_TXT), UTF_8);
        summary = summary.replace("\r", "");
        summary = summary.replace(separatorChar, '/');
        assertEquals(expectedSummary, summary);

        if (checkBuild) {
            assertBuildsCleanly(destDir, true);
        }

        return destDir;
    }

    private static boolean isWindows() {
        return SdkUtils.startsWithIgnoreCase(System.getProperty("os.name"), "windows");
    }

    public static void assertBuildsCleanly(File base, boolean allowWarnings) throws Exception {
        File gradlew = new File(base, "gradlew" + (isWindows() ? ".bat" : ""));
        if (!gradlew.exists()) {
            // Not using a wrapper; can't easily test building (we don't have a gradle prebuilt)
            return;
        }
        File pwd = base.getAbsoluteFile();
        Process process = Runtime.getRuntime().exec(new String[]{gradlew.getPath(),
                "assembleDebug"}, null, pwd);
        int exitCode = process.waitFor();
        byte[] stdout = ByteStreams.toByteArray(process.getInputStream());
        byte[] stderr = ByteStreams.toByteArray(process.getErrorStream());
        String errors = new String(stderr, UTF_8);
        String output = new String(stdout, UTF_8);
        int expectedExitCode = 0;
        if (output.contains("BUILD FAILED") && errors.contains(
                "Could not find any version that matches com.android.tools.build:gradle:")) {
            // We ignore this assertion. We got here because we are using a version of the
            // Android Gradle plug-in that is not available in Maven Central yet.
            expectedExitCode = 1;
        } else {
            assertTrue(output + "\n" + errors, output.contains("BUILD SUCCESSFUL"));
            if (!allowWarnings) {
                assertEquals(output + "\n" + errors, "", errors);
            }
        }
        assertEquals(expectedExitCode, exitCode);
        System.out.println("Built project successfully; output was:\n" + output);
    }

    private static String fileTree(File file, boolean includeDirs) {
        StringBuilder sb = new StringBuilder(1000);
        appendFiles(sb, includeDirs, file, 0);
        return sb.toString();
    }

    private static void appendFiles(StringBuilder sb, boolean includeDirs, File file, int depth) {
        // Skip wrapper, since it may or may not be present for unit tests
        if (depth == 1) {
            String name = file.getName();
            if (name.equals(".gradle")
                    || name.equals("gradle")
                    || name.equals("gradlew")
                    || name.equals("gradlew.bat")) {
                return;
            }
        } else if (depth == 2 && file.getName().equals("build")) { // Skip output
            return;
        }

        boolean isDirectory = file.isDirectory();
        if (depth > 0 && (!isDirectory || includeDirs)) {
            for (int i = 0; i < depth - 1; i++) {
                sb.append("  ");
            }
            sb.append(file.getName());
            sb.append("\n");
        }

        if (isDirectory) {
            File[] children = file.listFiles();
            if (children != null) {
                Arrays.sort(children);
                for (File child : children) {
                    appendFiles(sb, includeDirs, child, depth + 1);
                }
            }
        }
    }

    private static void createDotProject(
            @NonNull File projectDir,
            String name,
            boolean addAndroidNature) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<projectDescription>\n"
                + "\t<name>").append(name).append("</name>\n"
                + "\t<comment></comment>\n"
                + "\t<projects>\n"
                + "\t</projects>\n"
                + "\t<buildSpec>\n"
                + "\t\t<buildCommand>\n"
                + "\t\t\t<name>com.android.ide.eclipse.adt.ResourceManagerBuilder</name>\n"
                + "\t\t\t<arguments>\n"
                + "\t\t\t</arguments>\n"
                + "\t\t</buildCommand>\n"
                + "\t\t<buildCommand>\n"
                + "\t\t\t<name>com.android.ide.eclipse.adt.PreCompilerBuilder</name>\n"
                + "\t\t\t<arguments>\n"
                + "\t\t\t</arguments>\n"
                + "\t\t</buildCommand>\n"
                + "\t\t<buildCommand>\n"
                + "\t\t\t<name>org.eclipse.jdt.core.javabuilder</name>\n"
                + "\t\t\t<arguments>\n"
                + "\t\t\t</arguments>\n"
                + "\t\t</buildCommand>\n"
                + "\t\t<buildCommand>\n"
                + "\t\t\t<name>com.android.ide.eclipse.adt.ApkBuilder</name>\n"
                + "\t\t\t<arguments>\n"
                + "\t\t\t</arguments>\n"
                + "\t\t</buildCommand>\n"
                + "\t</buildSpec>\n"
                + "\t<natures>\n");
        if (addAndroidNature) {
            sb.append("\t\t<nature>com.android.ide.eclipse.adt.AndroidNature</nature>\n");
        }
        sb.append("\t\t<nature>org.eclipse.jdt.core.javanature</nature>\n"
                + "\t</natures>\n"
                + "</projectDescription>\n");
        Files.write(sb.toString(), new File(projectDir, ".project"), UTF_8);
    }

    private static void createClassPath(
            @NonNull File projectDir,
            @Nullable File output,
            @NonNull List<File> sources,
            @NonNull List<File> jars) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<classpath>\n");
        for (File source : sources) {
            sb.append("\t<classpathentry kind=\"src\" path=\"").append(source.getPath()).
                    append("\"/>\n");
        }
        sb.append("\t<classpathentry kind=\"con\" path=\"com.android.ide.eclipse.adt.ANDROID_FRAMEWORK\"/>\n"
                + "\t<classpathentry exported=\"true\" kind=\"con\" path=\"com.android.ide.eclipse.adt.LIBRARIES\"/>\n"
                + "\t<classpathentry exported=\"true\" kind=\"con\" path=\"com.android.ide.eclipse.adt.DEPENDENCIES\"/>\n");
        for (File jar : jars) {
            sb.append("<classpathentry exported=\"true\" kind=\"lib\" path=\"").append(jar.getPath()).append("\"/>\n");
        }
        if (output != null) {
            sb.append("\t<classpathentry kind=\"output\" path=\"").append(output.getPath()).append("\"/>\n");
        }
        sb.append("</classpath>");
        Files.write(sb.toString(), new File(projectDir, ".classpath"), UTF_8);
    }

    private static void createProjectProperties(
            @NonNull File projectDir,
            @Nullable String target,
            Boolean mergeManifest,
            Boolean isLibrary,
            @Nullable String proguardConfig,
            @NonNull List<File> libraries) throws IOException {
        StringBuilder sb = new StringBuilder();

        sb.append("# This file is automatically generated by Android Tools.\n"
                + "# Do not modify this file -- YOUR CHANGES WILL BE ERASED!\n"
                + "#\n"
                + "# This file must be checked in Version Control Systems.\n"
                + "#\n"
                + "# To customize properties used by the Ant build system edit\n"
                + "# \"ant.properties\", and override values to adapt the script to your\n"
                + "# project structure.\n"
                + "#\n");
        if (proguardConfig != null) {
            sb.append("# To enable ProGuard to shrink and obfuscate your code, uncomment this "
                    + "(available properties: sdk.dir, user.home):\n");
            // TODO: When using this, escape proguard properly
            sb.append(proguardConfig);
            sb.append("\n");
        }

        if (target != null) {
            String escaped = escapeProperty("target", target);
            sb.append("# Project target.\n").append(escaped).append("\n");
        }

        if (mergeManifest != null) {
            String escaped = escapeProperty("manifestmerger.enabled", Boolean.toString(mergeManifest));
            sb.append(escaped).append("\n");
        }

        if (isLibrary != null) {
            String escaped = escapeProperty("android.library", Boolean.toString(isLibrary));
            sb.append(escaped).append("\n");
        }

        for (int i = 0, n = libraries.size(); i < n; i++) {
            String path = libraries.get(i).getPath();
            String escaped = escapeProperty("android.library.reference." + Integer.toString(i), path);
            sb.append(escaped).append("\n");
        }


        // Slow, stupid implementation, but is 100% compatible with Java's property file implementation


        Files.write(sb.toString(), new File(projectDir, "project.properties"), UTF_8);
    }

    private static  String escapeProperty(@NonNull String key, @NonNull String value)
            throws IOException {
        Properties properties = new Properties();
        properties.setProperty(key, value);
        StringWriter writer = new StringWriter();
        properties.store(writer, null);
        return writer.toString();
    }

    private static void createAndroidManifest(
            @NonNull File projectDir,
            @NonNull String packageName,
            int minSdkVersion,
            int targetSdkVersion,
            @Nullable String customApplicationBlock) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"").append(packageName).append("\"\n"
                + "    android:versionCode=\"1\"\n"
                + "    android:versionName=\"1.0\" >\n"
                + "\n");
        if (minSdkVersion != -1 || targetSdkVersion != -1) {
            sb.append("    <uses-sdk\n");
            if (minSdkVersion >= 1) {
                sb.append("        android:minSdkVersion=\"8\"\n");
            }
            if (targetSdkVersion >= 1) {
                sb.append("        android:targetSdkVersion=\"16\"\n");
            }
            sb.append("     />\n");
            sb.append("\n");
        }
        if (customApplicationBlock != null) {
            sb.append(customApplicationBlock);
        } else {
            sb.append(""
                    + "    <application\n"
                    + "        android:allowBackup=\"true\"\n"
                    + "        android:icon=\"@drawable/ic_launcher\"\n"
                    + "        android:label=\"@string/app_name\"\n"
                    + "    >\n"
                    + "    </application>\n");
        }

        sb.append("\n"
                + "</manifest>\n");
        Files.write(sb.toString(), new File(projectDir, ANDROID_MANIFEST_XML), UTF_8);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static File createSourceFile(@NonNull File projectDir, String relative,
            String contents) throws IOException {
        File file = new File(projectDir, relative.replace('/', separatorChar));
        file.getParentFile().mkdirs();
        Files.write(contents, file, UTF_8);
        return file;
    }

    private static File createSampleJavaSource(@NonNull File projectDir, String src, String pkg,
            String name) throws IOException {
        return createSourceFile(projectDir, src + '/' + pkg.replace('.','/') + '/' + name +
                DOT_JAVA, ""
                + "package " + pkg + ";\n"
                + "public class " + name + " {\n"
                + "}\n");
    }

    private static File createSampleAidlFile(@NonNull File projectDir, String src, String pkg)
            throws IOException {
        return createSourceFile(projectDir, src + '/' + pkg.replace('.','/') +
                "/IHardwareService.aidl", ""
                + "package " + pkg + ";\n"
                + "\n"
                + "/** {@hide} */\n"
                + "interface IHardwareService\n"
                + "{\n"
                + "    // Vibrator support\n"
                + "    void vibrate(long milliseconds);\n"
                + "    void vibratePattern(in long[] pattern, int repeat, IBinder token);\n"
                + "    void cancelVibrate();\n"
                + "\n"
                + "    // flashlight support\n"
                + "    boolean getFlashlightEnabled();\n"
                + "    void setFlashlightEnabled(boolean on);\n"
                + "    void enableCameraFlash(int milliseconds);\n"
                + "\n"
                + "    // sets the brightness of the backlights (screen, keyboard, button) 0-255\n"
                + "    void setBacklights(int brightness);\n"
                + "\n"
                + "    // for the phone\n"
                + "    void setAttentionLight(boolean on);\n"
                + "}");
    }

    private static File createSampleRsFile(@NonNull File projectDir, String src, String pkg)
            throws IOException {
        return createSourceFile(projectDir, src + '/' + pkg.replace('.', '/') + '/' + "latency.rs",
                ""
                        + "#pragma version(1)\n"
                        + "#pragma rs java_package_name(com.android.rs.cpptests)\n"
                        + "#pragma rs_fp_relaxed\n"
                        + "\n"
                        + "void root(const uint32_t *v_in, uint32_t *v_out) {\n"
                        + "\n"
                        + "}");
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void createDefaultStrings(File dir) throws IOException {
        File strings = new File(dir, "res" + separator + "values" + separator + "strings.xml");
        strings.getParentFile().mkdirs();
        Files.write(""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<resources>\n"
                + "\n"
                + "    <string name=\"app_name\">Unit Test</string>\n"
                + "\n"
                + "</resources>", strings, UTF_8);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void createDefaultIcon(File dir) throws IOException {
        File strings = new File(dir, "res" + separator + "drawable" + separator +
                "ic_launcher.xml");
        strings.getParentFile().mkdirs();
        Files.write(""
                + "<shape xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <solid android:color=\"#00000000\"/>\n"
                + "    <stroke android:width=\"1dp\" color=\"#ff000000\"/>\n"
                + "    <padding android:left=\"1dp\" android:top=\"1dp\"\n"
                + "        android:right=\"1dp\" android:bottom=\"1dp\" />\n"
                + "</shape>", strings, UTF_8);
    }

    private static void deleteDir(File root) {
        if (root.exists()) {
            File[] files = root.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDir(file);
                    } else {
                        boolean deleted = file.delete();
                        assert deleted : file;
                    }
                }
            }
            boolean deleted = root.delete();
            assert deleted : root;
        }
    }
}
