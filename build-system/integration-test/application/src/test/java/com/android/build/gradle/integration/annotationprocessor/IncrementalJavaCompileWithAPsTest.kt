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

package com.android.build.gradle.integration.annotationprocessor

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleBuildResult
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.DEFAULT_COMPILE_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_CONSTRAINT_LAYOUT_VERSION
import com.android.build.gradle.integration.common.fixture.TestProject
import com.android.build.gradle.integration.common.fixture.app.BuildFileBuilder
import com.android.build.gradle.integration.common.fixture.app.JavaSourceFileBuilder
import com.android.build.gradle.integration.common.fixture.app.LayoutFileBuilder
import com.android.build.gradle.integration.common.fixture.app.ManifestFileBuilder
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.TestUtils
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Integration test for the incrementality of annotation processing and Java compilation.
 */
@RunWith(FilterableParameterized::class)
class IncrementalJavaCompileWithAPsTest(
    private val withKapt: Boolean,
    private val withIncrementalAPs: Boolean
) {

    companion object {

        @Parameterized.Parameters(name = "kapt_{0}_incrementalAPs_{1}")
        @JvmStatic
        fun parameters() = listOf(
            arrayOf(false, false),
            arrayOf(false, true),
            arrayOf(true, false),
            arrayOf(true, true)
        )

        private const val APP_MODULE = ":app"
        private const val ANNOTATIONS_MODULE = ":annotations"
        private const val PROCESSOR_MODULE = ":processor"

        private const val SOURCE_DIR = "src/main/java"
        private const val GENERATED_SOURCE_DIR = "build/generated/ap_generated_sources/debug/out"
        private const val GENERATED_SOURCE_KAPT_DIR = "build/generated/source/kapt/debug"
        private const val COMPILED_CLASSES_DIR =
            "build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"

        private const val NAMESPACE = "com.example.app"
        private const val MAIN_ACTIVITY = "MainActivity"

        private const val ANNOTATED_PACKAGE = "com.example.annotated"
        private const val ANNOTATION_1_CLASS_1 = "Annotation1Class1"
        private const val ANNOTATION_1_CLASS_2 = "Annotation1Class2"
        private const val ANNOTATION_2_CLASS_1 = "Annotation2Class1"
        private const val ANNOTATION_2_CLASS_2 = "Annotation2Class2"

        private const val GENERATED_PACKAGE = "com.example.generated"
        private const val ANNOTATION_1_GENERATED_CLASS = "Annotation1GeneratedClass"
        private const val ANNOTATION_2_GENERATED_CLASS = "Annotation2GeneratedClass"

        private const val ANNOTATION_PACKAGE = "com.example.annotations"
        private const val ANNOTATION_1 = "Annotation1"
        private const val ANNOTATION_2 = "Annotation2"

        private const val PROCESSOR_PACKAGE = "com.example.processor"
        private const val AGGREGATING_PROCESSOR = "AggregatingProcessor"
        private const val ANNOTATION_1_PROCESSOR = "Annotation1Processor"
        private const val ANNOTATION_2_PROCESSOR = "Annotation2Processor"

        private const val CLEAN_TASK = "$APP_MODULE:clean"
        private const val KAPT_TASK = "$APP_MODULE:kaptDebugKotlin"
        private const val COMPILE_TASK = "$APP_MODULE:compileDebugJavaWithJavac"
    }

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(setUpTestProject())
            .withKotlinGradlePlugin(withKapt)
            // Enforcing unique package names to prevent regressions. Remove when b/116109681 fixed.
            .addGradleProperties("${BooleanOption.ENFORCE_UNIQUE_PACKAGE_NAMES.propertyName}=true")
            .addGradleProperties("${BooleanOption.USE_ANDROID_X.propertyName}=true")
            .create()

    private fun setUpTestProject(): TestProject {
        return MultiModuleTestProject.builder()
            .subproject(APP_MODULE, setUpApp())
            .subproject(ANNOTATIONS_MODULE, setUpAnnotationLib())
            .subproject(PROCESSOR_MODULE, setUpProcessorLib())
            .build()
    }

    private fun setUpApp(): MinimalSubProject {
        val packagePath = NAMESPACE.replace('.', '/')
        val layoutName = "activity_main"
        val helloTextId = "helloTextId"
        val app = MinimalSubProject.app(NAMESPACE)

        app.withFile(
            "src/main/res/layout/$layoutName.xml",
            with(LayoutFileBuilder()) {
                addTextView(helloTextId)
                build()
            }
        )

        app.withFile(
            "src/main/AndroidManifest.xml",
            with(ManifestFileBuilder()) {
                addApplicationTag(MAIN_ACTIVITY)
                build()
            })

        app.withFile(
            "build.gradle",
            with(BuildFileBuilder()) {
                plugin = app.plugin
                compileSdkVersion = DEFAULT_COMPILE_SDK_VERSION
                minSdkVersion = "23"
                namespace = NAMESPACE
                addDependency(
                    dependency = "'androidx.appcompat:appcompat:1.6.1'"
                )
                addDependency(
                    dependency = "'com.android.support.constraint:constraint-layout:" +
                            "$SUPPORT_LIB_CONSTRAINT_LAYOUT_VERSION'"
                )
                addDependency("compileOnly", "project('$ANNOTATIONS_MODULE')")
                addDependency("annotationProcessor", "project('$PROCESSOR_MODULE')")
                build()
            }
        )

        // Add classes that have annotations
        val annotatedPackagePath = ANNOTATED_PACKAGE.replace('.', '/')
        val annotatedClasses =
            mapOf(
                ANNOTATION_1_CLASS_1 to ANNOTATION_1,
                ANNOTATION_1_CLASS_2 to ANNOTATION_1,
                ANNOTATION_2_CLASS_1 to ANNOTATION_2,
                ANNOTATION_2_CLASS_2 to ANNOTATION_2
            )
        for ((annotatedClass, annotation) in annotatedClasses) {
            app.withFile(
                "src/main/java/$annotatedPackagePath/$annotatedClass.java",
                with(JavaSourceFileBuilder(ANNOTATED_PACKAGE)) {
                    addClass(
                        """
                    @$ANNOTATION_PACKAGE.$annotation
                    public class $annotatedClass {
                    }
                    """.trimIndent()
                    )
                    build()
                })
        }

        // Add the main activity class that references the generated classes
        app.withFile(
            "src/main/java/$packagePath/$MAIN_ACTIVITY.java",
            with(JavaSourceFileBuilder(NAMESPACE)) {
                addImports("androidx.appcompat.app.AppCompatActivity", "android.os.Bundle")
                addClass(
                    """
                    public class $MAIN_ACTIVITY extends AppCompatActivity {

                        @Override
                        protected void onCreate(Bundle savedInstanceState) {
                            super.onCreate(savedInstanceState);
                            setContentView(R.layout.$layoutName);

                            android.widget.TextView textView = findViewById(R.id.$helloTextId);
                            String text = new $GENERATED_PACKAGE.$ANNOTATION_1_GENERATED_CLASS().toString()
                                    + "\n" + new $GENERATED_PACKAGE.$ANNOTATION_2_GENERATED_CLASS().toString();
                            textView.setText(text);
                        }
                    }
                    """.trimIndent()
                )
                build()
            })

        return app
    }

    private fun setUpAnnotationLib(): MinimalSubProject {
        val packagePath = ANNOTATION_PACKAGE.replace('.', '/')
        val annotationLib = MinimalSubProject.javaLibrary()

        val annotations = listOf(ANNOTATION_1, ANNOTATION_2)
        for (annotation in annotations) {
            annotationLib.withFile(
                "src/main/java/$packagePath/$annotation.java",
                with(JavaSourceFileBuilder(ANNOTATION_PACKAGE)) {
                    addClass(
                        """
                    public @interface $annotation {
                    }""".trimIndent()
                    )
                    build()
                })
        }

        return annotationLib
    }

    private fun setUpProcessorLib(): MinimalSubProject {
        val packagePath = PROCESSOR_PACKAGE.replace('.', '/')
        val processorLib = MinimalSubProject.javaLibrary()

        processorLib.withFile(
            "src/main/java/$packagePath/$AGGREGATING_PROCESSOR.java",
            with(JavaSourceFileBuilder(PROCESSOR_PACKAGE)) {
                addAggregatingProcessorClass(this, AGGREGATING_PROCESSOR)
                build()
            })
        val processors =
            mapOf(
                ANNOTATION_1_PROCESSOR to Pair(ANNOTATION_1, ANNOTATION_1_GENERATED_CLASS),
                ANNOTATION_2_PROCESSOR to Pair(ANNOTATION_2, ANNOTATION_2_GENERATED_CLASS)
            )
        for ((processor, annotationInfo) in processors) {
            val annotation = annotationInfo.first
            val generatedClass = annotationInfo.second
            processorLib.withFile(
                "src/main/java/$packagePath/$processor.java",
                with(JavaSourceFileBuilder(PROCESSOR_PACKAGE)) {
                    addClass(
                        """
                        public class $processor extends $AGGREGATING_PROCESSOR {

                            public $processor() {
                                super($ANNOTATION_PACKAGE.$annotation.class.getName(), "$GENERATED_PACKAGE", "$generatedClass");
                            }
                        }
                        """.trimIndent()
                    )
                    build()
                }
            )
        }

        processorLib.withFile(
            "build.gradle",
            with(BuildFileBuilder()) {
                plugin = processorLib.plugin
                addDependency(dependency = "project('$ANNOTATIONS_MODULE')")
                build()
            }
        )

        processorLib.withFile(
            "src/main/resources/META-INF/services/javax.annotation.processing.Processor",
            """
            $PROCESSOR_PACKAGE.$ANNOTATION_1_PROCESSOR
            $PROCESSOR_PACKAGE.$ANNOTATION_2_PROCESSOR
            """.trimIndent()
        )

        if (withIncrementalAPs) {
            processorLib.withFile(
                "src/main/resources/META-INF/gradle/incremental.annotation.processors",
                """
                $PROCESSOR_PACKAGE.$ANNOTATION_1_PROCESSOR,aggregating
                $PROCESSOR_PACKAGE.$ANNOTATION_2_PROCESSOR,aggregating
                """.trimIndent()
            )
        }

        return processorLib
    }

    /**
     * Adds an aggregating annotation processor that generates a registry of all the classes
     * annotated with the given annotation.
     *
     * See https://docs.gradle.org/current/userguide/java_plugin.html
     * #sec:incremental_annotation_processing
     */
    private fun addAggregatingProcessorClass(
        builder: JavaSourceFileBuilder,
        @Suppress("SameParameterValue") processorName: String
    ) {
        builder.addImports(
            "java.io.IOException",
            "java.io.UncheckedIOException",
            "java.io.Writer",
            "java.util.Collections",
            "java.util.HashSet",
            "java.util.List",
            "java.util.Set",
            "java.util.function.Function",
            "java.util.stream.Collectors",
            "javax.annotation.processing.AbstractProcessor",
            "javax.annotation.processing.RoundEnvironment",
            "javax.lang.model.SourceVersion",
            "javax.lang.model.element.Element",
            "javax.lang.model.element.TypeElement",
            "javax.tools.JavaFileObject"
        )
        builder.addClass(
            """
            public class $processorName extends AbstractProcessor {

                private String annotationFullClassName;
                private String generatedPackageName;
                private String generatedClassName;

                AggregatingProcessor(
                        String annotationFullClassName,
                        String generatedPackageName,
                        String generatedClassName) {
                    this.annotationFullClassName = annotationFullClassName;
                    this.generatedPackageName = generatedPackageName;
                    this.generatedClassName = generatedClassName;
                }

                @Override
                public Set<String> getSupportedAnnotationTypes() {
                    Set<String> set = new HashSet<>(1);
                    set.add(annotationFullClassName);
                    return Collections.unmodifiableSet(set);
                }

                @Override
                public SourceVersion getSupportedSourceVersion() {
                    return SourceVersion.latestSupported();
                }

                @Override
                public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                    TypeElement annotation = processingEnv.getElementUtils().getTypeElement(annotationFullClassName);
                    if (!annotations.contains(annotation)) {
                        return false;
                    }
                    try {
                        generateSourceCode(annotation, roundEnv);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    return true;
                }

                private void generateSourceCode(TypeElement annotation, RoundEnvironment roundEnv) throws IOException {
                    JavaFileObject generatedFile = processingEnv.getFiler().createSourceFile(generatedPackageName + "." + generatedClassName);
                    try (Writer writer = generatedFile.openWriter()) {
                        writer.write("package " + generatedPackageName + ";\n");
                        writer.write("\n");
                        writer.write("public class " + generatedClassName + " {\n");
                        writer.write("\n");
                        writer.write("\t@Override\n");
                        writer.write("\tpublic String toString() {");
                        writer.write("\n");
                        writer.write("\t\tStringBuilder greetings = new StringBuilder();\n");
                        writer.write("\t\tgreetings.append(\"Hello. This message comes from generated code! \");\n");
                        writer.write("\t\tgreetings.append(\"The following types are annotated with @" + annotationFullClassName  + ": \");\n");

                        // Sort the annotated types to ensure deterministic output
                        List<String> sortedAnnotatedTypes =
                                roundEnv.getElementsAnnotatedWith(annotation).stream()
                                        .map((Function<Element, String>) element ->
                                                ((TypeElement) element).getQualifiedName().toString())
                                        .sorted().collect(Collectors.toList());
                        for (String annotatedType : sortedAnnotatedTypes) {
                            writer.write("\t\tgreetings.append(\"" + annotatedType + "; \");\n");
                        }

                        writer.write("\t\treturn greetings.toString();\n");
                        writer.write("\t}\n");
                        writer.write("}\n");
                    }
                }
            }
            """.trimIndent()
        )
    }

    // Original source files. There are 4 annotated source files: 2 source files are annotated with
    // annotation 1, and the other 2 source files are annotated with annotation 2. We will use the
    // following source file to trigger a change for testing.
    private lateinit var annotation1Class1JavaFile: File

    // Generated source files: The first one is generated from source files annotated with
    // annotation 1, and the second one is generated from source files annotated with annotation 2.
    private lateinit var annotation1GeneratedJavaFile: File
    private lateinit var annotation2GeneratedJavaFile: File

    // Compiled classes of original and generated source files
    private lateinit var mainActivityClass: File
    private lateinit var annotation1Class1: File
    private lateinit var annotation1Class2: File
    private lateinit var annotation2Class1: File
    private lateinit var annotation2Class2: File
    private lateinit var annotation1GeneratedClass: File
    private lateinit var annotation2GeneratedClass: File

    // Timestamps of generated source files and compiled classes
    private var timestamps: MutableMap<File, Long> = mutableMapOf()

    @Before
    fun setUp() {
        val appDir = project.getSubproject(APP_MODULE).projectDir
        val appBuildFile = project.getSubproject(APP_MODULE).buildFile

        val generatedSourceDir = if (withKapt)
            GENERATED_SOURCE_KAPT_DIR else GENERATED_SOURCE_DIR

        val mainActivityPackagePath = NAMESPACE.replace('.', '/')
        val annotatedPackagePath = ANNOTATED_PACKAGE.replace('.', '/')
        val generatedPackagePath = GENERATED_PACKAGE.replace('.', '/')

        annotation1Class1JavaFile =
                File("$appDir/$SOURCE_DIR/$annotatedPackagePath/$ANNOTATION_1_CLASS_1.java")

        annotation1GeneratedJavaFile =
                File("$appDir/$generatedSourceDir/$generatedPackagePath/$ANNOTATION_1_GENERATED_CLASS.java")
        annotation2GeneratedJavaFile =
                File("$appDir/$generatedSourceDir/$generatedPackagePath/$ANNOTATION_2_GENERATED_CLASS.java")

        mainActivityClass =
                File("$appDir/$COMPILED_CLASSES_DIR/$mainActivityPackagePath/$MAIN_ACTIVITY.class")
        annotation1Class1 =
                File("$appDir/$COMPILED_CLASSES_DIR/$annotatedPackagePath/$ANNOTATION_1_CLASS_1.class")
        annotation1Class2 =
                File("$appDir/$COMPILED_CLASSES_DIR/$annotatedPackagePath/$ANNOTATION_1_CLASS_2.class")
        annotation2Class1 =
                File("$appDir/$COMPILED_CLASSES_DIR/$annotatedPackagePath/$ANNOTATION_2_CLASS_1.class")
        annotation2Class2 =
                File("$appDir/$COMPILED_CLASSES_DIR/$annotatedPackagePath/$ANNOTATION_2_CLASS_2.class")
        annotation1GeneratedClass =
                File("$appDir/$COMPILED_CLASSES_DIR/$generatedPackagePath/$ANNOTATION_1_GENERATED_CLASS.class")
        annotation2GeneratedClass =
                File("$appDir/$COMPILED_CLASSES_DIR/$generatedPackagePath/$ANNOTATION_2_GENERATED_CLASS.class")

        if (withKapt) {
            TestFileUtils.searchAndReplace(
                appBuildFile,
                "apply plugin: 'com.android.application'",
                """
                apply plugin: 'com.android.application'
                apply plugin: 'kotlin-android'
                apply plugin: 'kotlin-kapt'
                """.trimIndent()
            )
            TestFileUtils.searchAndReplace(appBuildFile, "annotationProcessor", "kapt")
            TestFileUtils.appendToFile(
                appBuildFile,
                """
                android.kotlinOptions.jvmTarget = '1.8'
                tasks.withType(org.jetbrains.kotlin.gradle.tasks.KaptGenerateStubs.class).configureEach {
                    compilerOptions {
                        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
                    }
                }
                """.trimIndent()
            )
        }
    }

    private fun recordTimestamps() {
        timestamps[annotation1GeneratedJavaFile] = annotation1GeneratedJavaFile.lastModified()
        timestamps[annotation2GeneratedJavaFile] = annotation2GeneratedJavaFile.lastModified()
        timestamps[mainActivityClass] = mainActivityClass.lastModified()
        timestamps[annotation1Class1] = annotation1Class1.lastModified()
        timestamps[annotation1Class2] = annotation1Class2.lastModified()
        timestamps[annotation2Class1] = annotation2Class1.lastModified()
        timestamps[annotation2Class2] = annotation2Class2.lastModified()
        timestamps[annotation1GeneratedClass] = annotation1GeneratedClass.lastModified()
        timestamps[annotation2GeneratedClass] = annotation2GeneratedClass.lastModified()

        // This is to avoid the flakiness of timestamp checks, adding it here to be safe, maybe at
        // some point it can be removed
        TestUtils.waitForFileSystemTick()
    }

    /**
     * Runs a full (non-incremental) build to generate source files and compile both original and
     * generated source files.
     */
    private fun runFullBuild(): GradleBuildResult {
        val result = project.executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
            .run(CLEAN_TASK, COMPILE_TASK)
        recordTimestamps()
        return result
    }

    /** Runs an incremental build. */
    private fun runIncrementalBuild()
            : GradleBuildResult {
        return project.executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
            .run(COMPILE_TASK)
    }

    private fun assertFileHasChanged(file: File) {
        assertThat(file).isNewerThan(timestamps[file]!!)
    }

    private fun assertFileHasNotChanged(file: File) {
        assertThat(file).wasModifiedAt(timestamps[file]!!)
    }

    @Test
    fun `change original source file`() {
        val fullBuildResult = runFullBuild()

        // Source files should be generated, and both original and generated source files should be
        // compiled. Checking this once in this test is good enough, the other tests don't need to
        // repeat this check.
        assertThat(annotation1GeneratedJavaFile).exists()
        assertThat(annotation2GeneratedJavaFile).exists()
        assertThat(mainActivityClass).exists()
        assertThat(annotation1Class1).exists()
        assertThat(annotation1Class2).exists()
        assertThat(annotation2Class1).exists()
        assertThat(annotation2Class2).exists()
        assertThat(annotation1GeneratedClass).exists()
        assertThat(annotation2GeneratedClass).exists()

        // Check the tasks' status. Checking this once in this test is good enough, the other tests
        // don't need to repeat this check.
        if (withKapt) {
            assertThat(fullBuildResult.getTask(KAPT_TASK)).didWork()
        } else {
            assertThat(fullBuildResult.findTask(KAPT_TASK)).isNull()
        }
        assertThat(fullBuildResult.getTask(COMPILE_TASK)).didWork()

        // Change an original source file that has annotation 1
        TestFileUtils.searchAndReplace(
            annotation1Class1JavaFile,
            ANNOTATION_1_CLASS_1,
            "$ANNOTATION_1_CLASS_1 /* dummy comment to trigger change */"
        )

        val result = runIncrementalBuild()

        /*
         * EXPECTATION: If all of the annotation processors are incremental, annotation processing
         * should be incremental.
         */
        // The relevant generated source files should be re-generated always
        assertFileHasChanged(annotation1GeneratedJavaFile)

        // If all of the annotation processors are incremental, the irrelevant generated source
        // files should not be re-generated
        if (withIncrementalAPs) {
            // EXPECTATION-NOT-MET: This is a limitation of Gradle and Kapt.
            assertFileHasChanged(annotation2GeneratedJavaFile)
        } else {
            assertFileHasChanged(annotation2GeneratedJavaFile)
        }

        /*
         * EXPECTATION: If (1) Kapt is used, or (2) all of the annotation processors are
         * incremental, compilation should be incremental.
         */
        val incrementalMode = withKapt || withIncrementalAPs

        // This is the case when JavaCompile performs both annotation processing and compilation
        val annotationProcessingByJavaCompile = !withKapt

        // The relevant original source files should be recompiled always
        assertFileHasChanged(annotation1Class1)

        // In incremental mode, the irrelevant original source files should not be recompiled
        if (incrementalMode) {
            // MainActivity references Annotation1GeneratedClass and Annotation1GeneratedClass
            assertFileHasChanged(mainActivityClass)
            assertFileHasNotChanged(annotation1Class2)
            assertFileHasNotChanged(annotation2Class1)
            assertFileHasNotChanged(annotation2Class2)
        } else {
            assertFileHasChanged(mainActivityClass)
            assertFileHasChanged(annotation1Class2)
            assertFileHasChanged(annotation2Class1)
            assertFileHasChanged(annotation2Class2)
        }

        // In incremental mode, the re-generated source files should not be recompiled since their
        // contents haven't changed, and they also do not directly or transitively reference the
        // changed original source file
        if (incrementalMode) {
            if (annotationProcessingByJavaCompile) {
                // EXPECTATION-NOT-MET: As documented at
                // https://docs.gradle.org/current/userguide/java_plugin.html
                // #sec:incremental_annotation_processing, "Gradle will always recompile any files
                // the processor generates".
                assertFileHasChanged(annotation1GeneratedClass)
                assertFileHasChanged(annotation2GeneratedClass)
            } else {
                assertFileHasNotChanged(annotation1GeneratedClass)
                assertFileHasNotChanged(annotation2GeneratedClass)
            }
        } else {
            assertFileHasChanged(annotation1GeneratedClass)
            assertFileHasChanged(annotation2GeneratedClass)
        }

        // Check the tasks' status
        if (withKapt) {
            assertThat(result.getTask(KAPT_TASK)).didWork()
        } else {
            assertThat(result.findTask(KAPT_TASK)).isNull()
        }
        assertThat(result.getTask(COMPILE_TASK)).didWork()
    }

    @Test
    fun `delete generated source file`() {
        runFullBuild()

        // Delete the generated source file for annotation 1
        FileUtils.delete(annotation1GeneratedJavaFile)

        val result = runIncrementalBuild()

        /*
         * EXPECTATION: If all of the annotation processors are incremental, annotation processing
         * should be incremental.
         */
        // The relevant generated source files should be re-generated always
        assertFileHasChanged(annotation1GeneratedJavaFile)

        // If all of the annotation processors are incremental, the irrelevant generated source
        // files should not be re-generated
        if (withIncrementalAPs) {
            // EXPECTATION-NOT-MET: This is a limitation of Gradle and Kapt.
            assertFileHasChanged(annotation2GeneratedJavaFile)
        } else {
            assertFileHasChanged(annotation2GeneratedJavaFile)
        }

        /*
         * EXPECTATION: If (1) Kapt is used, or (2) all of the annotation processors are
         * incremental, compilation should be incremental.
         */
        val incrementalMode = withKapt || withIncrementalAPs

        // This is the case when JavaCompile performs both annotation processing and compilation
        val annotationProcessingByJavaCompile = !withKapt

        // None of the original source files are changed, so in incremental mode, none of them
        // should be recompiled
        if (incrementalMode) {
            if (annotationProcessingByJavaCompile) {
                // EXPECTATION-NOT-MET: This is a limitation of Gradle.
                assertFileHasChanged(mainActivityClass)
                assertFileHasChanged(annotation1Class1)
                assertFileHasChanged(annotation1Class2)
                assertFileHasChanged(annotation2Class1)
                assertFileHasChanged(annotation2Class2)
            } else {
                assertFileHasNotChanged(mainActivityClass)
                assertFileHasNotChanged(annotation1Class1)
                assertFileHasNotChanged(annotation1Class2)
                assertFileHasNotChanged(annotation2Class1)
                assertFileHasNotChanged(annotation2Class2)
            }
        } else {
            assertFileHasChanged(mainActivityClass)
            assertFileHasChanged(annotation1Class1)
            assertFileHasChanged(annotation1Class2)
            assertFileHasChanged(annotation2Class1)
            assertFileHasChanged(annotation2Class2)
        }

        // In incremental mode, the re-generated source files should not be recompiled since their
        // contents haven't changed, and they also do not directly or transitively reference the
        // deleted generated source file
        if (incrementalMode) {
            if (annotationProcessingByJavaCompile) {
                // EXPECTATION-NOT-MET: As documented at
                // https://docs.gradle.org/current/userguide/java_plugin.html
                // #sec:incremental_annotation_processing, "Gradle will always recompile any files
                // the processor generates".
                assertFileHasChanged(annotation1GeneratedClass)
                assertFileHasChanged(annotation2GeneratedClass)
            } else {
                assertFileHasNotChanged(annotation1GeneratedClass)
                assertFileHasNotChanged(annotation2GeneratedClass)
            }
        } else {
            assertFileHasChanged(annotation1GeneratedClass)
            assertFileHasChanged(annotation2GeneratedClass)
        }

        // Check the tasks' status
        if (withKapt) {
            assertThat(result.getTask(KAPT_TASK)).didWork()
        } else {
            assertThat(result.findTask(KAPT_TASK)).isNull()
        }
        if (incrementalMode) {
            if (annotationProcessingByJavaCompile) {
                assertThat(result.getTask(COMPILE_TASK)).didWork()
            } else {
                assertThat(result.getTask(COMPILE_TASK)).wasUpToDate()
            }
        } else {
            assertThat(result.getTask(COMPILE_TASK)).didWork()
        }
    }

    @Test
    fun `delete compiled class`() {
        runFullBuild()

        // Delete the compiled class of an original source file that has annotation 1
        FileUtils.delete(annotation1Class1)

        val result = runIncrementalBuild()

        /*
         * EXPECTATION: None of the generated source files are changed, so annotation processing
         * should be UP-TO-DATE.
         */
        // This is the case when JavaCompile performs both annotation processing and
        // compilation
        val annotationProcessingByJavaCompile = !withKapt

        // None of the generated source files should be re-generated
        if (annotationProcessingByJavaCompile) {
            // EXPECTATION-NOT-MET: This is a limitation of Gradle.
            assertFileHasChanged(annotation1GeneratedJavaFile)
            assertFileHasChanged(annotation2GeneratedJavaFile)
        } else {
            assertFileHasNotChanged(annotation1GeneratedJavaFile)
            assertFileHasNotChanged(annotation2GeneratedJavaFile)
        }

        /*
         * EXPECTATION: If (1) Kapt is used, or (2) all of the annotation processors are
         * incremental, compilation should be incremental.
         */
        val incrementalMode = withKapt || withIncrementalAPs

        // The relevant original source files should be recompiled always
        assertFileHasChanged(annotation1Class1)

        // In incremental mode, the irrelevant original source files should not be recompiled
        if (incrementalMode) {
            // EXPECTATION-NOT-MET: This is a limitation of Gradle.
            assertFileHasChanged(mainActivityClass)
            assertFileHasChanged(annotation1Class2)
            assertFileHasChanged(annotation2Class1)
            assertFileHasChanged(annotation2Class2)
        } else {
            assertFileHasChanged(mainActivityClass)
            assertFileHasChanged(annotation1Class2)
            assertFileHasChanged(annotation2Class1)
            assertFileHasChanged(annotation2Class2)
        }

        // In incremental mode, the re-generated source files should not be recompiled since their
        // contents haven't changed, and they also do not directly or transitively reference the
        // deleted compiled class
        if (incrementalMode) {
            // EXPECTATION-NOT-MET: This is a limitation of Gradle.
            assertFileHasChanged(annotation1GeneratedClass)
            assertFileHasChanged(annotation2GeneratedClass)
        } else {
            assertFileHasChanged(annotation1GeneratedClass)
            assertFileHasChanged(annotation2GeneratedClass)
        }

        // Check the tasks' status
        if (withKapt) {
            assertThat(result.getTask(KAPT_TASK)).wasUpToDate()
        } else {
            assertThat(result.findTask(KAPT_TASK)).isNull()
        }
        assertThat(result.getTask(COMPILE_TASK)).didWork()
    }
}
