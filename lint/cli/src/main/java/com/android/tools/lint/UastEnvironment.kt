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

package com.android.tools.lint

import com.android.SdkConstants
import com.android.SdkConstants.EXT_JAR
import com.android.tools.lint.UastEnvironment.Module.Variant.Companion.toModuleVariant
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Project.DependencyKind
import com.intellij.core.CoreApplicationEnvironment
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.impl.ZipHandler
import com.intellij.pom.java.LanguageLevel
import java.io.File
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.config.addJavaSourceRoots
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.konan.library.KLIB_INTEROP_IR_PROVIDER_IDENTIFIER
import org.jetbrains.kotlin.library.CompilerSingleFileKlibResolveAllowingIrProvidersStrategy
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.platform.CommonPlatforms
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import org.jetbrains.kotlin.platform.wasm.WasmPlatforms
import org.jetbrains.kotlin.util.Logger
import org.jetbrains.uast.UastFacade

/** JVM system property to enable FIR UAST or K2 UAST, as per the new compiler name */
const val FIR_UAST_KEY = "lint.use.fir.uast"

@ApiStatus.Internal
fun useFirUast(): Boolean = System.getProperty(FIR_UAST_KEY, "false").toBoolean()

/**
 * This interface provides the setup and configuration needed to use VFS/PSI/UAST on the command
 * line.
 *
 * Basic usage:
 * 1. Create a configuration via [UastEnvironment.Configuration.create] and mutate it as needed.
 * 2. Create a project environment via [UastEnvironment.create]. You can create multiple
 *    environments in the same process (one for each "module").
 * 3. Call [analyzeFiles] to initialize PSI machinery and frontend-specific pre-computation.
 * 4. Analyze PSI/UAST.
 * 5. When finished, call [dispose].
 * 6. Once *all* [UastEnvironment]s are disposed, call [disposeApplicationEnvironment] to clean up
 *    some global resources, especially if running in a long-living daemon process.
 */
interface UastEnvironment {
  val projectDisposable: Disposable

  val coreAppEnv: CoreApplicationEnvironment

  val ideaProject: MockProject

  val kotlinCompilerConfig: CompilerConfiguration

  /** A configuration is just a container for the classpath, compiler flags, etc. */
  interface Configuration {
    companion object {
      /**
       * Creates a new [Configuration] that specifies project structure, classpath, compiler flags,
       * etc.
       */
      @JvmStatic
      @JvmOverloads
      fun create(
        enableKotlinScripting: Boolean = true,
        useFirUast: Boolean = useFirUast(),
      ): Configuration {
        return if (useFirUast) FirUastEnvironment.Configuration.create(enableKotlinScripting)
        else Fe10UastEnvironment.Configuration.create(enableKotlinScripting)
      }

      fun mergeRoots(
        modules: List<Module>,
        bootClassPaths: Iterable<File>?,
      ): Pair<Set<File>, Set<File>> {
        fun mergedFiles(prop: (Module) -> Collection<File>): MutableSet<File> =
          modules.flatMapTo(mutableSetOf(), prop)
        val sourceRoots = mergedFiles(Module::sourceRoots)
        val classPathRoots =
          mergedFiles(Module::classpathRoots).also { bootClassPaths?.let(it::addAll) }
        return sourceRoots to classPathRoots
      }

      internal val Configuration.isKMP: Boolean
        get() = modules.mapTo(mutableSetOf()) { it.variant }.size > 1
    }

    val modules: Collection<Module>
    val classPaths: Collection<File>

    fun addModules(modules: List<Module>, bootClassPaths: Iterable<File>? = null)

    val kotlinCompilerConfig: CompilerConfiguration

    @Deprecated("Pass real module structure instead of merging them", ReplaceWith("addModules()"))
    fun addSourceRoots(sourceRoots: List<File>) {
      // Note: the Kotlin compiler would normally add KotlinSourceRoots to the configuration
      // too, to be used by KotlinCoreEnvironment when computing the set of KtFiles to
      // analyze. However, Lint already computes the list of KtFiles on its own in LintDriver.
      kotlinCompilerConfig.addJavaSourceRoots(sourceRoots)
      if (Configuration::class.java.desiredAssertionStatus()) {
        for (root in sourceRoots) {
          // The equivalent assertion in JavaCoreProjectEnvironment.addSourcesToClasspath
          // happens too late to be useful.
          assert(root.extension != EXT_JAR) {
            "Jar files should be added as classpath roots, not as source roots: $root"
          }
        }
      }
    }

    @Deprecated(
      "Pass real module structure through [addModules] instead of merging them",
      ReplaceWith("addModules()"),
    )
    fun addClasspathRoots(classpathRoots: List<File>) {
      kotlinCompilerConfig.addJvmClasspathRoots(classpathRoots)
    }

    // Defaults to LanguageLevel.HIGHEST.
    var javaLanguageLevel: LanguageLevel?

    // Defaults to LanguageVersionSettingsImpl.DEFAULT.
    var kotlinLanguageLevel: LanguageVersionSettings
      get() = kotlinCompilerConfig.languageVersionSettings
      set(value) {
        kotlinCompilerConfig.languageVersionSettings = value
      }
  }

  companion object {
    /**
     * Creates a new [UastEnvironment] suitable for analyzing both Java and Kotlin code. You must
     * still call [UastEnvironment.analyzeFiles] before doing anything with PSI/UAST. When finished
     * using the environment, call [UastEnvironment.dispose].
     */
    @JvmStatic
    fun create(config: Configuration): UastEnvironment {
      return when (config) {
        is FirUastEnvironment.Configuration -> FirUastEnvironment.create(config)
        is Fe10UastEnvironment.Configuration -> Fe10UastEnvironment.create(config)
        else -> throw UnsupportedOperationException()
      }
    }

    /**
     * Disposes the global application environment, which is created implicitly by the first
     * [UastEnvironment]. Only call this once *all* [UastEnvironment]s have been disposed.
     */
    @JvmStatic
    fun disposeApplicationEnvironment() {
      // Note: if we later decide to keep the app env alive forever in the Gradle daemon, we
      // should still clear some caches between builds (see CompileServiceImpl.clearJarCache).
      val appEnv = KotlinCoreEnvironment.applicationEnvironment ?: return
      Disposer.dispose(appEnv.parentDisposable)
      checkApplicationEnvironmentDisposed()
      ZipHandler.clearFileAccessorCache()
      // https://youtrack.jetbrains.com/issue/KTIJ-24467
      UastFacade.clearCachedPlugin()
    }

    @JvmStatic
    fun checkApplicationEnvironmentDisposed() {
      check(KotlinCoreEnvironment.applicationEnvironment == null)
    }

    @JvmStatic
    fun kotlinLibrary(path: String): KotlinLibrary =
      CompilerSingleFileKlibResolveAllowingIrProvidersStrategy(
          listOf(KLIB_INTEROP_IR_PROVIDER_IDENTIFIER)
        )
        .resolve(org.jetbrains.kotlin.konan.file.File(path), logger)

    @JvmStatic
    fun CompilerConfiguration.getKlibPaths(): List<String> =
      get(JVMConfigurationKeys.KLIB_PATHS) ?: listOf()

    private val logger =
      object : Logger {
        override fun error(message: String) = kotlin.error(message)

        override fun fatal(message: String) = kotlin.error(message)

        override fun log(message: String) {}

        override fun warning(message: String) {}
      }
  }

  /** Analyzes the given files so that PSI/UAST resolve works correctly. */
  fun analyzeFiles(ktFiles: List<File>)

  fun dispose() {
    Disposer.dispose(projectDisposable)
  }

  class Module(
    internal val project: Project,
    internal val jdkHome: File?,
    includeTests: Boolean,
    includeTestFixtureSources: Boolean,
    isUnitTest: Boolean,
  ) {

    enum class Variant {
      UNKNOWN, // e.g. test project
      COMMON,
      JVM,
      ANDROID,
      NATIVE,
      JS,
      WASM;

      companion object {
        fun String.toModuleVariant(): Variant {
          // https://kotlinlang.org/docs/multiplatform-dsl-reference.html#targets
          // https://kotlinlang.org/docs/multiplatform-hierarchy.html#target-shortcuts
          return when {
            startsWith("common") -> COMMON
            startsWith("jvm") -> JVM
            startsWith("android") -> {
              // androidNative v.s. everything else
              if (endsWith("Native")) NATIVE else ANDROID
            }
            startsWith("ios") -> NATIVE
            startsWith("linux") -> NATIVE
            startsWith("macos") -> NATIVE
            startsWith("mingw") -> NATIVE
            startsWith("tvos") -> NATIVE
            startsWith("js") -> JS
            startsWith("wasm") -> WASM
            else -> UNKNOWN
          }
        }

        fun Variant.toTargetPlatform(): TargetPlatform {
          return when (this) {
            COMMON -> CommonPlatforms.defaultCommonPlatform
            NATIVE -> NativePlatforms.unspecifiedNativePlatform
            JS -> JsPlatforms.defaultJsPlatform
            WASM -> WasmPlatforms.Default
            else -> JvmPlatforms.defaultJvmPlatform
          }
        }
      }
    }

    val variant: Variant
      get() =
        if (project.isAndroidProject) {
          Variant.ANDROID
        } else {
          // From AGP model's build variant
          project.buildVariant?.name?.toModuleVariant()
            // From the module name in project.xml
            ?: project.name.toModuleVariant()
        }

    val sourceRoots: Set<File> =
      with(project) {
        // Note that there could be duplicates here since we're including multiple library
        // dependencies that could have the same dependencies (e.g. lib1 and lib2 both
        // referencing guava.jar)
        setFrom(
          javaSourceFolders.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(project.dir.takeIf { it.isDirectory }),
          unitTestSourceFolders.takeIf { includeTests },
          instrumentationTestSourceFolders.takeIf { includeTests },
          testSourceFolders.takeIf { includeTests },
          generatedSourceFolders,
          testFixturesSourceFolders.takeIf { includeTestFixtureSources },
        )
      }

    val classpathRoots: Set<File> =
      with(project) {
        setFrom(
          javaLibraries,
          testLibraries.takeIf { includeTests },
          testFixturesLibraries.takeIf { includeTestFixtureSources },

          // Don't include all class folders:
          //  files.addAll(project.getJavaClassFolders());
          // These are the outputs from the sources and generated sources, which we will
          // parse directly with PSI/UAST anyway. Including them here leads lint to do
          // a lot more work (e.g. when resolving symbols it looks at both .java and .class
          // matches).
          // However, we *do* need them for libraries; otherwise, type resolution into
          // compiled libraries will not work; see
          // https://issuetracker.google.com/72032121
          // (We also enable this for unit tests where there is no actual compilation;
          // here, the presence of class files is simulating binary-only access
          when {
            isLibrary || isUnitTest -> javaClassFolders
            // As of 3.4, R.java is in a special jar file
            isGradleProject -> javaClassFolders.filter { it.name == SdkConstants.FN_R_CLASS_JAR }
            else -> null
          },
        )
      }

    val gradleBuildScripts: Collection<File>
      get() = project.gradleBuildScripts

    val directDependencies: Sequence<Pair<Project, DependencyKind>>
      get() = project.directLibraries.asSequence().map { it to project.getDependencyKind(it) }

    val allRoots: Sequence<File>
      get() = sourceRoots.asSequence() + classpathRoots.asSequence()

    val klibs: Map<File, DependencyKind>
      get() = project.klibs

    val name
      get() = project.name

    val kotlinLanguageLevel: LanguageVersionSettings
      get() = project.kotlinLanguageLevel
  }
}

// Return set of merged elements in the order they appear
private fun <T> setFrom(vararg cols: Collection<T>?): Set<T> =
  cols.asSequence().filterNotNull().flatMapTo(mutableSetOf()) { it }
