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

package com.android.build.gradle.integration.connected.feature

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.fixture.TEST_SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.build.gradle.integration.connected.utils.getEmulator
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class DynamicFeatureConnectedTest {

    private val build = MultiModuleTestProject.builder().apply {
        val app = MinimalSubProject.app("com.example.app")
            .appendToBuild(
                """android.dynamicFeatures = [':dynamicFeature']
                            |android.defaultConfig.versionCode = 4
                            |android.defaultConfig.targetSdk = 23""".trimMargin()
            )
            .withFile(
                "src/main/java/com/example/app/MyProductionClass.java",
                """package com.example.app;
                    |
                    |public class MyProductionClass {
                    |    public static int getThree() {
                    |        return 3;
                    |    };
                    |}""".trimMargin())
            .withFile(
                "src/main/res/values/strings.xml", """
                    |<resources>
                    |    <string name="df_title">Dynamic Feature Title</string>
                    |    <string name="app_title">App Title</string>
                    |</resources>
                """.trimMargin())
            .apply { replaceFile(TestSourceFile("src/main/AndroidManifest.xml",
                """<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    |        xmlns:dist="http://schemas.android.com/apk/distribution">
                    |    <dist:module dist:title="@string/app_title">
                    |    </dist:module>
                    |    <application />
                    |</manifest>""".trimMargin()))}

        val dynamicFeature = MinimalSubProject.dynamicFeature("com.example.app.dynamic.feature")
            .appendToBuild("android.defaultConfig.testInstrumentationRunner 'android.support.test.runner.AndroidJUnitRunner'")
            .apply { replaceFile(TestSourceFile("src/main/AndroidManifest.xml",
                """<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    |        xmlns:dist="http://schemas.android.com/apk/distribution">
                    |    <dist:module dist:onDemand="true" dist:title="@string/df_title">
                    |        <dist:fusing dist:include="true" />
                    |    </dist:module>
                    |    <application />
                    |</manifest>""".trimMargin()))}
            .withFile("src/main/java/com/example/dynamic/feature/FeatureProductionClass.java",
                """package com.example.dynamic.feature;
                    |
                    |public class FeatureProductionClass {
                    |    public static int getFour() { return 4; }
                    |}""".trimMargin())
            .withFile(
                "src/androidTest/java/com/example/dynamic/feature/test/MyTest.java",
                """package com.example.dynamic.feature.test;
                    |
                    |import android.support.test.runner.AndroidJUnit4;
                    |import com.example.app.MyProductionClass;
                    |import com.example.dynamic.feature.FeatureProductionClass;
                    |import org.junit.Assert;
                    |import org.junit.Test;
                    |import org.junit.internal.runners.JUnit4ClassRunner;
                    |import org.junit.runner.RunWith;
                    |import org.junit.runners.BlockJUnit4ClassRunner;
                    |
                    |@RunWith(AndroidJUnit4.class)
                    |public class MyTest {
                    |    @Test
                    |    public void useBaseClass() {
                    |        // Check both compiles and runs against a production class in
                    |        // the base feature
                    |        Assert.assertEquals(3, MyProductionClass.getThree());
                    |    }
                    |
                    |    @Test
                    |    public void useFeatureClass() {
                    |        // Check both compiles and runs against a production class in
                    |        // this dynamic feature
                    |        Assert.assertEquals(4, FeatureProductionClass.getFour());
                    |    }
                    |}
                """.trimMargin())

        subproject(":app", app)
        subproject(":dynamicFeature", dynamicFeature)
        dependency(dynamicFeature, app)
        androidTestDependency(
            dynamicFeature,
            "com.android.support.test:runner:$TEST_SUPPORT_LIB_VERSION"
        )
        androidTestDependency(
            dynamicFeature,
            "com.android.support.test:rules:$TEST_SUPPORT_LIB_VERSION"
        )
        androidTestDependency(
            dynamicFeature,
            "com.android.support:support-annotations:$SUPPORT_LIB_VERSION"
        )
    }
        .build()

    @get:Rule
    val project = GradleTestProject.builder()
            .fromTestApp(build)
            .create()

    companion object {
        @JvmField
        @ClassRule
        val emulator = getEmulator()
    }

    @Before
    @Throws(IOException::class, InterruptedException::class)
    fun setUp() {
        // fail fast if no response
        project.addAdbTimeout();
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        project.executor().run("uninstallAll")
    }

    @Test
    fun runTestInDynamicFeature() {
        project.executor().run(":dynamicFeature:connectedAndroidTest")
    }

    // Regression test for b/314731501
    @Test
    fun testInstallAppWithDynamicFeatureInstall() {
        project.executor().run(":app:installDebug")
    }

}
