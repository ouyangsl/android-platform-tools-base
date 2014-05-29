/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.internal.variant

import com.android.SdkConstants
import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.api.LibraryVariantImpl
import com.android.build.gradle.internal.coverage.JacocoInstrumentTask
import com.android.build.gradle.internal.coverage.JacocoPlugin
import com.android.build.gradle.internal.tasks.MergeFileTask
import com.android.build.gradle.tasks.ExtractAnnotations
import com.android.build.gradle.tasks.MergeResources
import com.android.builder.core.BuilderConstants
import com.android.builder.core.DefaultBuildType
import com.android.builder.core.VariantConfiguration
import com.android.builder.dependency.LibraryBundle
import com.android.builder.dependency.LibraryDependency
import com.android.builder.dependency.ManifestDependency
import com.android.builder.model.AndroidLibrary
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.tooling.BuildException

import static com.android.SdkConstants.FN_ANNOTATIONS_ZIP
import static com.android.SdkConstants.LIBS_FOLDER
import static com.android.build.gradle.BasePlugin.DIR_BUNDLES
import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES
import static com.android.builder.model.AndroidProject.FD_OUTPUTS

/**
 */
public class LibraryVariantFactory implements VariantFactory {

    @NonNull
    private final BasePlugin basePlugin
    @NonNull
    private final LibraryExtension extension

    public LibraryVariantFactory(@NonNull BasePlugin basePlugin,
            @NonNull LibraryExtension extension) {
        this.extension = extension
        this.basePlugin = basePlugin
    }

    @Override
    @NonNull
    public BaseVariantData createVariantData(@NonNull VariantConfiguration variantConfiguration) {
        return new LibraryVariantData(variantConfiguration)
    }

    @Override
    @NonNull
    public BaseVariant createVariantApi(@NonNull BaseVariantData variantData) {
        return basePlugin.getInstantiator().newInstance(LibraryVariantImpl.class, variantData)
    }

    @NonNull
    @Override
    public VariantConfiguration.Type getVariantConfigurationType() {
        return VariantConfiguration.Type.LIBRARY
    }

    @Override
    boolean isLibrary() {
        return true
    }

    @Override
    public void createTasks(@NonNull BaseVariantData variantData, @Nullable Task assembleTask) {
        LibraryVariantData libVariantData = variantData as LibraryVariantData
        VariantConfiguration variantConfig = variantData.variantConfiguration
        DefaultBuildType buildType = variantConfig.buildType

        String fullName = variantConfig.fullName
        String dirName = variantConfig.dirName
        Project project = basePlugin.project

        basePlugin.createAnchorTasks(variantData)

        basePlugin.createCheckManifestTask(variantData)

        // Add a task to create the res values
        basePlugin.createGenerateResValuesTask(variantData)

        // Add a task to process the manifest(s)
        basePlugin.createProcessManifestTask(variantData, DIR_BUNDLES)

        // Add a task to compile renderscript files.
        basePlugin.createRenderscriptTask(variantData)

        // Create a merge task to only merge the resources from this library and not
        // the dependencies. This is what gets packaged in the aar.
        MergeResources packageRes = basePlugin.basicCreateMergeResourcesTask(variantData,
                "package",
                "$project.buildDir/${FD_INTERMEDIATES}/$DIR_BUNDLES/${dirName}/res",
                false /*includeDependencies*/,
                false /*process9Patch*/)

        if (variantData.variantDependency.androidDependencies.isEmpty()) {
            // if there is no android dependencies, then we should use the packageRes task above
            // as the only res merging task.
            variantData.mergeResourcesTask = packageRes
        } else {
            // Add a task to merge the resource folders, including the libraries, in order to
            // generate the R.txt file with all the symbols, including the ones from
            // the dependencies.
            basePlugin.createMergeResourcesTask(variantData, false /*process9Patch*/)
        }

        // Add a task to merge the assets folders
        basePlugin.createMergeAssetsTask(variantData,
                "$project.buildDir/${FD_INTERMEDIATES}/$DIR_BUNDLES/${dirName}/assets",
                false /*includeDependencies*/)

        // Add a task to create the BuildConfig class
        basePlugin.createBuildConfigTask(variantData)

        // Add a task to generate resource source files, directing the location
        // of the r.txt file to be directly in the bundle.
        basePlugin.createProcessResTask(variantData,
                "$project.buildDir/${FD_INTERMEDIATES}/$DIR_BUNDLES/${dirName}",
                false /*generateResourcePackage*/,
                )

        // process java resources
        basePlugin.createProcessJavaResTask(variantData)

        basePlugin.createAidlTask(variantData, basePlugin.project.file(
                "$basePlugin.project.buildDir/${FD_INTERMEDIATES}/$DIR_BUNDLES/${dirName}/$SdkConstants.FD_AIDL"))

        // Add a compile task
        basePlugin.createCompileTask(variantData, null/*testedVariant*/)

        // Add NDK tasks
        basePlugin.createNdkTasks(variantData);

        // package the prebuilt native libs into the bundle folder
        Sync packageJniLibs = project.tasks.create(
                "package${fullName.capitalize()}JniLibs",
                Sync)
        packageJniLibs.dependsOn variantData.ndkCompileTask
        // package from 3 sources.
        packageJniLibs.from(variantConfig.jniLibsList).include("**/*.so")
        packageJniLibs.from(variantData.ndkCompileTask.soFolder).include("**/*.so")
        packageJniLibs.into(project.file(
                "$project.buildDir/${FD_INTERMEDIATES}/$DIR_BUNDLES/${dirName}/jni"))

        // package the renderscript header files files into the bundle folder
        Sync packageRenderscript = project.tasks.create(
                "package${fullName.capitalize()}Renderscript",
                Sync)
        // package from 3 sources. the order is important to make sure the override works well.
        packageRenderscript.from(variantConfig.renderscriptSourceList).include("**/*.rsh")
        packageRenderscript.into(project.file(
                "$project.buildDir/${FD_INTERMEDIATES}/$DIR_BUNDLES/${dirName}/$SdkConstants.FD_RENDERSCRIPT"))

        // merge consumer proguard files from different build types and flavors
        MergeFileTask mergeProGuardFileTask = project.tasks.create(
                "merge${fullName.capitalize()}ProguardFiles",
                MergeFileTask)
        mergeProGuardFileTask.conventionMapping.inputFiles = {
            project.files(variantConfig.getConsumerProguardFiles()).files }
        mergeProGuardFileTask.conventionMapping.outputFile = {
            project.file(
                    "$project.buildDir/${FD_INTERMEDIATES}/$DIR_BUNDLES/${dirName}/$LibraryBundle.FN_PROGUARD_TXT")
        }

        // copy lint.jar into the bundle folder
        Copy lintCopy = project.tasks.create(
                "copy${fullName.capitalize()}Lint",
                Copy)
        lintCopy.dependsOn basePlugin.lintCompile
        lintCopy.from("$project.buildDir/${FD_INTERMEDIATES}/lint/lint.jar")
        lintCopy.into("$project.buildDir/${FD_INTERMEDIATES}/$DIR_BUNDLES/$dirName")

        Zip bundle = project.tasks.create(
                "bundle${fullName.capitalize()}",
                Zip)

        def extract = variantData.variantDependency.annotationsPresent ? createExtractAnnotations(
                fullName, project, variantData) : null

        if (buildType.runProguard) {
            // run proguard on output of compile task
            basePlugin.createProguardTasks(variantData, null)

            // hack since bundle can't depend on variantData.obfuscationTask
            mergeProGuardFileTask.dependsOn variantData.obfuscationTask

            bundle.dependsOn packageRes, packageRenderscript, mergeProGuardFileTask,
                    lintCopy, packageJniLibs
            if (extract != null) {
                bundle.dependsOn(extract)
            }
        } else {
            final boolean instrumented = variantConfig.buildType.isTestCoverageEnabled()

            // if needed, instrument the code
            JacocoInstrumentTask jacocoTask = null
            Copy agentTask = null
            if (instrumented) {
                jacocoTask = project.tasks.create(
                        "instrument${variantConfig.fullName.capitalize()}", JacocoInstrumentTask)
                jacocoTask.dependsOn variantData.javaCompileTask
                jacocoTask.conventionMapping.jacocoClasspath = { project.configurations[JacocoPlugin.ANT_CONFIGURATION_NAME] }
                jacocoTask.conventionMapping.inputDir = { variantData.javaCompileTask.destinationDir }
                jacocoTask.conventionMapping.outputDir = { project.file("${project.buildDir}/${FD_INTERMEDIATES}/coverage-instrumented-classes/${variantConfig.dirName}") }

                agentTask = basePlugin.getJacocoAgentTask()
            }

            // package the local jar in libs/
            Sync packageLocalJar = project.tasks.create(
                    "package${fullName.capitalize()}LocalJar",
                    Sync)
            packageLocalJar.from(BasePlugin.getLocalJarFileList(variantData.variantDependency))
            if (instrumented) {
                packageLocalJar.dependsOn agentTask
                packageLocalJar.from(new File(agentTask.destinationDir, BasePlugin.FILE_JACOCO_AGENT))
            }
            packageLocalJar.into(project.file(
                    "$project.buildDir/${FD_INTERMEDIATES}/$DIR_BUNDLES/${dirName}/$LIBS_FOLDER"))

            // jar the classes.
            Jar jar = project.tasks.create("package${fullName.capitalize()}Jar", Jar);
            jar.dependsOn variantData.javaCompileTask, variantData.processJavaResourcesTask
            if (instrumented) {
                jar.dependsOn jacocoTask
                jar.from({ jacocoTask.getOutputDir() });
            } else {
                jar.from(variantData.javaCompileTask.outputs);
            }
            jar.from(variantData.processJavaResourcesTask.destinationDir)

            jar.destinationDir = project.file(
                    "$project.buildDir/${FD_INTERMEDIATES}/$DIR_BUNDLES/${dirName}")
            jar.archiveName = "classes.jar"

            String packageName = variantConfig.getPackageFromManifest()
            if (packageName == null) {
                throw new BuildException("Failed to read manifest", null)
            }
            packageName = packageName.replace('.', '/');

            jar.exclude(packageName + "/R.class")
            jar.exclude(packageName + "/R\$*.class")
            if (!extension.packageBuildConfig) {
                jar.exclude(packageName + "/Manifest.class")
                jar.exclude(packageName + "/Manifest\$*.class")
                jar.exclude(packageName + "/BuildConfig.class")
            }

            bundle.dependsOn packageRes, jar, packageRenderscript, packageLocalJar,
                    mergeProGuardFileTask, lintCopy, packageJniLibs

            if (extract != null) {
                // In case extract annotations strips out private typedef annotation classes
                jar.dependsOn extract
                bundle.dependsOn extract
            }
        }

        bundle.setDescription("Assembles a bundle containing the library in ${fullName.capitalize()}.");
        bundle.destinationDir = project.file("$project.buildDir/${FD_OUTPUTS}/aar")
        bundle.extension = BuilderConstants.EXT_LIB_ARCHIVE
        bundle.from(project.file("$project.buildDir/${FD_INTERMEDIATES}/$DIR_BUNDLES/${dirName}"))

        libVariantData.packageLibTask = bundle
        variantData.outputFile = bundle.archivePath

        if (assembleTask == null) {
            assembleTask = basePlugin.createAssembleTask(variantData)
        }
        assembleTask.dependsOn bundle
        variantData.assembleTask = assembleTask

        if (extension.defaultPublishConfig.equals(fullName)) {
            VariantHelper.setupDefaultConfig(project,
                    variantData.variantDependency.packageConfiguration)

            // add the artifact that will be published
            project.artifacts.add("default", bundle)

            basePlugin.assembleDefault.dependsOn variantData.assembleTask
        }

        // also publish the artifact with its full config name
        if (extension.publishNonDefault) {
            project.artifacts.add(variantData.variantDependency.publishConfiguration.name, bundle)
            bundle.classifier = variantData.variantDependency.publishConfiguration.name
        }


        // configure the variant to be testable.
        variantConfig.output = new LibraryBundle(
                bundle.archivePath,
                project.file("$project.buildDir/${FD_INTERMEDIATES}/$DIR_BUNDLES/${dirName}"),
                variantData.getName()) {

            @Override
            @Nullable
            String getProject() {
                return project.path
            }

            @Override
            @Nullable
            String getProjectVariant() {
                return variantData.getName()
            }

            @NonNull
            @Override
            List<LibraryDependency> getDependencies() {
                return variantConfig.directLibraries
            }

            @NonNull
            @Override
            List<? extends AndroidLibrary> getLibraryDependencies() {
                return variantConfig.directLibraries
            }

            @NonNull
            @Override
            List<ManifestDependency> getManifestDependencies() {
                return variantConfig.directLibraries
            }
        };
    }

    public Task createExtractAnnotations(
            String fullName, Project project, BaseVariantData variantData) {
        VariantConfiguration config = variantData.variantConfiguration
        String dirName = config.dirName

        ExtractAnnotations task = project.tasks.create(
                "extract${fullName.capitalize()}Annotations",
                ExtractAnnotations)
        task.description =
                "Extracts Android annotations for the ${fullName} variant into the archive file"
        task.group = org.gradle.api.plugins.BasePlugin.BUILD_GROUP
        task.plugin = basePlugin
        task.variant = variantData
        task.destinationDir = project.file("$project.buildDir/${FD_INTERMEDIATES}/$DIR_BUNDLES/${dirName}")
        task.output = new File(task.destinationDir, FN_ANNOTATIONS_ZIP)
        task.classDir = project.file("$project.buildDir/${FD_INTERMEDIATES}/classes/${variantData.variantConfiguration.dirName}")
        task.source = variantData.getJavaSources()
        task.encoding = extension.compileOptions.encoding
        task.sourceCompatibility = extension.compileOptions.sourceCompatibility
        task.classpath = project.files(basePlugin.getAndroidBuilder().getCompileClasspath(config))
        task.dependsOn variantData.javaCompileTask

        // Setup the boot classpath just before the task actually runs since this will
        // force the sdk to be parsed. (Same as in compileTask)
        task.doFirst {
            task.bootClasspath = basePlugin.getAndroidBuilder().getBootClasspath()
        }

        return task
    }
}
