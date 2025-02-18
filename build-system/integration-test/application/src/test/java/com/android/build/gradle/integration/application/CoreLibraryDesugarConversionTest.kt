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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.Adb
import com.android.build.gradle.integration.common.fixture.DESUGAR_DEPENDENCY_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.apk.AndroidArchive
import com.android.testutils.apk.Dex
import com.android.testutils.truth.DexClassSubject
import com.android.utils.FileUtils
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcode.INVOKE_DIRECT;
import com.android.tools.smali.dexlib2.Opcode.INVOKE_DIRECT_RANGE;
import com.android.tools.smali.dexlib2.Opcode.INVOKE_INTERFACE;
import com.android.tools.smali.dexlib2.Opcode.INVOKE_INTERFACE_RANGE;
import com.android.tools.smali.dexlib2.Opcode.INVOKE_STATIC;
import com.android.tools.smali.dexlib2.Opcode.INVOKE_STATIC_RANGE;
import com.android.tools.smali.dexlib2.Opcode.INVOKE_SUPER;
import com.android.tools.smali.dexlib2.Opcode.INVOKE_SUPER_RANGE;
import com.android.tools.smali.dexlib2.Opcode.INVOKE_VIRTUAL;
import com.android.tools.smali.dexlib2.Opcode.INVOKE_VIRTUAL_RANGE;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.fail

@RunWith(FilterableParameterized::class)
class CoreLibraryDesugarConversionTest(val minSdkVersion: Int) {

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPluginWithMinSdkVersion("com.android.application", minSdkVersion))
        .create()

    @get:Rule
    var adb = Adb()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
            project.buildFile,
            """
                android {
                    compileOptions {
                        sourceCompatibility JavaVersion.VERSION_1_8
                        targetCompatibility JavaVersion.VERSION_1_8
                        coreLibraryDesugaringEnabled true
                    }
                }
                android.defaultConfig.multiDexEnabled = true
                dependencies {
                    coreLibraryDesugaring "$DESUGAR_DEPENDENCY"
                }
            """.trimIndent())

        // add a function with desugar library parameter, which is called from application
        TestFileUtils.addMethod(
            FileUtils.join(project.mainSrcDir,"com/example/helloworld/HelloWorld.java"),
            """
                public static int[] getNumbers() {
                    int[] numbers = new int[3];
                    java.util.Arrays.setAll(numbers, index -> index * 5);
                    return numbers;
                }
            """.trimIndent())

        // Add a function with desugar library parameter (a java.time class in this case), which
        // is called from application regression test for b/150774053, make sure it works when
        // minSdkVersion is 24.
        TestFileUtils.addMethod(
            FileUtils.join(project.mainSrcDir,"com/example/helloworld/HelloWorld.java"),
            """
                public static void useConversion(java.time.ZonedDateTime zonedDateTime) {
                    android.view.textclassifier.TextClassification.Request.Builder builder = null;
                    builder.setReferenceTime(zonedDateTime);
                }
            """.trimIndent())

        // add a function with desugar library parameter, which is called from android platform
        TestFileUtils.addMethod(
            FileUtils.join(project.mainSrcDir,"com/example/helloworld/HelloWorld.java"),
            """
                @Override
                public void onGetDirectActions(android.os.CancellationSignal cancellationSignal,
                    java.util.function.Consumer<java.util.List<android.app.DirectAction>> callback) {
                    callback.accept(java.util.Collections.singletonList(new android.app.DirectAction.Builder("1").build()));
                }
            """.trimIndent())

        TestFileUtils.addMethod(
            FileUtils.join(project.projectDir, "src/androidTest/java/com/example/helloworld/HelloWorldTest.java"),
            """
                @Test
                public void testGetNumbers() {
                    Assert.assertEquals(5, HelloWorld.getNumbers()[1]);
                }
            """.trimIndent())

        TestFileUtils.addMethod(
            FileUtils.join(project.projectDir, "src/androidTest/java/com/example/helloworld/HelloWorldTest.java"),
            """
                @Test
                public void testGetTime() {
                    Assert.assertEquals("GMT", HelloWorld.getTime());
                }
            """.trimIndent())

        TestFileUtils.addMethod(
            FileUtils.join(project.projectDir, "src/androidTest/java/com/example/helloworld/HelloWorldTest.java"),
            """
                @Test
                public void testGetDirectActions() {
                    rule.getActivity().onGetDirectActions(new android.os.CancellationSignal(),
                        x -> x.forEach(it -> System.out.println(it.getId())));
                }
            """.trimIndent())
    }

    //TODO(bingran) remove once we enable connected tests in presubmit/postsubmit
    @Test
    fun testBytecodeOfFunctionWithDesugaredLibraryParam() {
        project.executor().run("clean", "assembleDebug")
        val apk = project.getApk(GradleTestProject.ApkType.DEBUG)
        val programClass = "Lcom/example/helloworld/HelloWorld;"
        val dex = getDexWithSpecificClass(programClass, apk.allDexes)
            ?: fail("Failed to find the dex with class name $programClass")

        DexClassSubject.assertThat(dex.classes[programClass])
            .hasMethodThatInvokes(
                "useConversion",
                findMethodThatInvokes(
                    dex,
                    "Lj$/time/TimeConversions;",
                    "convert",
                    "Ljava/time/ZonedDateTime;",
                    listOf("Lj$/time/ZonedDateTime;")))

        // Consumer and IntUnaryOperator are desugared up to 23 so conversion doesn't exist for 24 and above
        Assume.assumeTrue(minSdkVersion < 24)

        DexClassSubject.assertThat(dex.classes[programClass])
            .hasMethodThatInvokes(
                "getNumbers",
                findMethodThatInvokes(
                    dex,
                    "Lj$/util/function/IntUnaryOperator\$Wrapper;",
                    "convert",
                    "Ljava/util/function/IntUnaryOperator;",
                    listOf("Lj$/util/function/IntUnaryOperator;")))
	DexClassSubject.assertThat(dex.classes[programClass])
            .hasMethodThatInvokesMethod(
                "onGetDirectActions",
                "convert",
                listOf("Ljava/util/function/Consumer;"),
                "Lj$/util/function/Consumer;")
    }

    private fun getDexWithSpecificClass(className: String, dexes: Collection<Dex>) : Dex? =
        dexes.find {
            AndroidArchive.checkValidClassName(className)
            it.classes.keys.contains(className)
        }

    private fun findMethodThatInvokes(
            dex: Dex,
            definingClass: String,
            name: String,
            returnType: String,
            parameterTypes: List<String>): String {
        val seen = mutableListOf<String>()
        val method = dex.classes
            .flatMap { (_, clazz) -> clazz.methods }
            .filter { it.implementation != null }
            .find {
                it.implementation!!.instructions
                    .map { getInvokedMethod(it) }
                    .filter { it != null }
                    .map { it as MethodReference }
                    .filter { seen.add(getMethodReference(it)) }
                    .any { it.definingClass.equals(definingClass)
                        && it.name.equals(name)
                        && it.returnType.equals(returnType)
                        && it.parameterTypes.equals(parameterTypes) }
            }
            ?: fail(
                "Failed to find a method that invokes "
                    + "${getMethodReference(definingClass, name, returnType, parameterTypes)}, "
                    + "found: ${seen.joinToString()}")
        return getMethodReference(method)
    }

    private fun getMethodReference(reference: MethodReference): String {
        return getMethodReference(
            reference.definingClass, reference.name, reference.returnType, reference.parameterTypes)
    }

    private fun getMethodReference(
            definingClass: String,
            name: String,
            returnType: String,
            parameterTypes: List<out CharSequence>): String {
        return "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"
    }

    private fun getInvokedMethod(instruction: Instruction): MethodReference? {
        val opcode = instruction.opcode
        val isInvoke =
            opcode == INVOKE_VIRTUAL
                || opcode == INVOKE_SUPER
                || opcode == INVOKE_DIRECT
                || opcode == INVOKE_STATIC
                || opcode == INVOKE_INTERFACE
        val isInvokeRange =
            opcode == INVOKE_VIRTUAL_RANGE
                || opcode == INVOKE_SUPER_RANGE
                || opcode == INVOKE_DIRECT_RANGE
                || opcode == INVOKE_STATIC_RANGE
                || opcode == INVOKE_INTERFACE_RANGE
        if (isInvoke) {
          return (instruction as Instruction35c).reference as MethodReference
        }
        if (isInvokeRange) {
          return (instruction as Instruction3rc).reference as MethodReference
        }
        return null
    }

    companion object {
        @Parameterized.Parameters(name = "minSdkVersion_{0}")
        @JvmStatic
        fun params() = listOf(21, 24)

        private const val DESUGAR_DEPENDENCY
                = "com.android.tools:desugar_jdk_libs:$DESUGAR_DEPENDENCY_VERSION"
    }
}
