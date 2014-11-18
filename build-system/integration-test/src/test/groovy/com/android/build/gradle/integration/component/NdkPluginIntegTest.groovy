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

package com.android.build.gradle.integration.component

import com.android.build.gradle.integration.common.category.DeviceTests
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldJniApp
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category

import java.util.zip.ZipFile

import static org.junit.Assert.assertNotNull

/**
 * Basic integration test for native plugin.
 */
class NdkPluginIntegTest {

    @ClassRule
    public static GradleTestProject project = GradleTestProject.builder().create();

    @BeforeClass
    public static void setup() {
        new HelloWorldJniApp(true /* useCppSource */).writeSources(project.testDir)
        project.getBuildFile() << """
apply plugin: 'com.android.model.application'

model {
    android {
        compileSdkVersion $GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
        buildToolsVersion "$GradleTestProject.DEFAULT_BUILD_TOOL_VERSION"
    }
    android.ndk {
        moduleName "hello-jni"
    }
    android.buildTypes {
        debug {
            jniDebuggable true
        }
    }
}
"""
    }

    @Test
    public void assemble() {
        project.execute("assemble");
    }

    @Test
    public void assembleRelease() {
        project.execute("assembleRelease");
        ZipFile apk = new ZipFile(
                project.file("build/outputs/apk/${project.name}-release-unsigned.apk"));

        // Verify .so are built for all platform.
        assertNotNull(apk.getEntry("lib/x86/libhello-jni.so"));
        assertNotNull(apk.getEntry("lib/mips/libhello-jni.so"));
        assertNotNull(apk.getEntry("lib/armeabi/libhello-jni.so"));
        assertNotNull(apk.getEntry("lib/armeabi-v7a/libhello-jni.so"));
    }

    @Test
    public void assembleDebug() {
        project.execute("assembleDebug");
        ZipFile apk = new ZipFile(
                project.file("build/outputs/apk/${project.name}-debug.apk"));

        // Verify .so are built for all platform.
        assertNotNull(apk.getEntry("lib/x86/libhello-jni.so"));
        assertNotNull(apk.getEntry("lib/x86/gdbserver"));
        assertNotNull(apk.getEntry("lib/x86/gdb.setup"));
        assertNotNull(apk.getEntry("lib/mips/libhello-jni.so"));
        assertNotNull(apk.getEntry("lib/mips/gdbserver"));
        assertNotNull(apk.getEntry("lib/mips/gdb.setup"));
        assertNotNull(apk.getEntry("lib/armeabi/libhello-jni.so"));
        assertNotNull(apk.getEntry("lib/armeabi/gdbserver"));
        assertNotNull(apk.getEntry("lib/armeabi/gdb.setup"));
        assertNotNull(apk.getEntry("lib/armeabi-v7a/libhello-jni.so"));
        assertNotNull(apk.getEntry("lib/armeabi-v7a/gdbserver"));
        assertNotNull(apk.getEntry("lib/armeabi-v7a/gdb.setup"));
    }

    @Test
    @Category(DeviceTests.class)
    public void connnectedAndroidTest() {
        project.execute("connectedAndroidTest");
    }
}
