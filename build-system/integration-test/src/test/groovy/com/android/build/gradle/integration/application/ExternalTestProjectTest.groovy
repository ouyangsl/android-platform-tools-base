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

package com.android.build.gradle.integration.application
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import org.junit.BeforeClass
import org.junit.ClassRule
/**
 * Check that a project can depend on a jar dependency published by another app project.
 */
class ExternalTestProjectTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder().create()

    private static File app2BuildFile

    @BeforeClass
    public static void setup() {
        File rootFile = project.getTestDir()
        new File(rootFile, "settings.gradle") << """
include ':app1'
include ':app2'
"""
        // app1 module
        File app1 = new File(rootFile, "app1")
        File app1Src = new File(app1, "src")
        new HelloWorldApp().writeSources(app1Src)
        new File(app1, "build.gradle") << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
}

task testJar(type: Jar, dependsOn: 'assembleRelease') {

}

configurations {
    testLib
}

artifacts {
    testLib testJar
}

"""
        // app2 module
        File app2 = new File(rootFile, "app2")
        File app2Src = new File(app2, "src")
        new HelloWorldApp().writeSources(app2Src)
        app2BuildFile = new File(app2, "build.gradle")
    }

//    @Test
    public void testExtraJarDependency() {
        app2BuildFile << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
}

dependencies {
    compile project(path: ':app1', configuration: 'testLib')
}
"""

        project.execute('clean', 'app2:assembleDebug')
    }

//    @Test
    void testApkDependency() {
        app2BuildFile << """
apply plugin: 'com.android.application'

android {
    compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
    buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
}

dependencies {
    compile project(path: ':app1')
}
"""
        project.execute('clean', 'app2:assembleDebug')
    }
}
