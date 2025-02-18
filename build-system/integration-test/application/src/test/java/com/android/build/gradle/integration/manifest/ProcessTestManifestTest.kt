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

package com.android.build.gradle.integration.manifest

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.ApkSubject.getManifestContent
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class ProcessTestManifestTest {
    @JvmField @Rule
    var project: GradleTestProject = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.library"))
        .create()

    @Test
    fun testInstrumentationApkTargetSdk() {
        project.buildFile.appendText("""
            android {
                flavorDimensions "targetSdk"

                productFlavors {
                    sdk30 {
                        dimension "targetSdk"
                    }

                    sdk32 {
                        dimension "targetSdk"
                    }
                }
            }
            androidComponents {
                beforeVariants(selector().withFlavor("targetSdk", "sdk30"), { variant ->
                    variant.androidTest?.targetSdk = 30
                })

                beforeVariants(selector().withFlavor("targetSdk", "sdk32"), { variant ->
                    variant.androidTest?.targetSdk = 32
                })
            }
        """.trimIndent())

        project.executor().run("assembleAndroidTest")
        val sdk30ManifestFile = project.file("build/intermediates/packaged_manifests/sdk30DebugAndroidTest/processSdk30DebugAndroidTestManifest/AndroidManifest.xml")
        assertThat(sdk30ManifestFile).contains("android:targetSdkVersion=\"30\"")

        val sdk32ManifestFile = project.file("build/intermediates/packaged_manifests/sdk32DebugAndroidTest/processSdk32DebugAndroidTestManifest/AndroidManifest.xml")
        assertThat(sdk32ManifestFile).contains("android:targetSdkVersion=\"32\"")
    }

    @Test
    fun testInstrumentationApkTargetSdkPreview() {
        project.buildFile.appendText("""
            androidComponents {
                beforeVariants(selector().withBuildType("debug"), { variant ->
                    variant.androidTest?.targetSdk = 32
                })
                beforeVariants(selector().withBuildType("debug"), { variant ->
                    variant.androidTest?.targetSdkPreview = "M"
                })
            }
        """.trimIndent())

        project.executor().run("assembleDebugAndroidTest")
        val debugManifestFile = project.file("build/intermediates/packaged_manifests/debugAndroidTest/processDebugAndroidTestManifest/AndroidManifest.xml")
        assertThat(debugManifestFile).contains("android:targetSdkVersion=\"M\"")
    }

    @Test
    fun build() {
        project.buildFile.appendText("""
            import com.android.build.api.variant.AndroidVersion

            androidComponents {
                beforeVariants(selector().all(), { variant ->
                    variant.minSdk = 21
                    variant.maxSdk = 29
                    variant.targetSdk = 22
                })
            }

            android.packagingOptions.jniLibs.useLegacyPackaging = false
        """.trimIndent())
        FileUtils.createFile(
            project.file("src/androidTest/java/com/example/helloworld/TestReceiver.java"),
            """
                package com.example.helloworld;

                import android.content.BroadcastReceiver;
                import android.content.Context;
                import android.content.Intent;

                public class TestReceiver extends BroadcastReceiver {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                    }
                }
            """.trimIndent()
        )

        FileUtils.createFile(
            project.file("src/main/java/com/example/helloworld/MainReceiver.java"),
            """
                package com.example.helloworld;

                import android.content.BroadcastReceiver;
                import android.content.Context;
                import android.content.Intent;

                public class MainReceiver extends BroadcastReceiver {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                    }
                }
            """.trimIndent()
        )

        FileUtils.createFile(
            project.file("src/androidTest/AndroidManifest.xml"),
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application>
                        <receiver android:name="com.example.helloworld.TestReceiver" />
                    </application>
                </manifest>
            """.trimIndent()
        )

        // Replace android manifest with the one containing a receiver reference
        project.file("src/main/AndroidManifest.xml").delete()
        FileUtils.createFile(
            project.file("src/main/AndroidManifest.xml"),
            """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                            android:versionCode="1"
                            android:versionName="1.0">

                    <application android:label="@string/app_name">
                        <activity android:name=".HelloWorld"
                                  android:label="@string/app_name">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>

                        <receiver android:name="com.example.helloworld.MainReceiver" />
                    </application>
                </manifest>
            """.trimIndent()
        )

        project.executor().run("assembleDebugAndroidTest")

        val manifestContent = getManifestContent(project.testApk.file)
        assertManifestContentContainsString(manifestContent, "com.example.helloworld.TestReceiver")
        assertManifestContentContainsString(manifestContent, "com.example.helloworld.MainReceiver")
        assertManifestContentContainsString(manifestContent, "A: http://schemas.android.com/apk/res/android:minSdkVersion(0x0101020c)=21")
        assertManifestContentContainsString(manifestContent, "A: http://schemas.android.com/apk/res/android:targetSdkVersion(0x01010270)=22")
        assertManifestContentContainsString(manifestContent, "A: http://schemas.android.com/apk/res/android:maxSdkVersion(0x01010271)=29")
        assertManifestContentContainsString(
            manifestContent,
            "A: http://schemas.android.com/apk/res/android:extractNativeLibs(0x010104ea)=false"
        )
        assertManifestContentContainsString(
            manifestContent,
            "http://schemas.android.com/apk/res/android:debuggable(0x0101000f)=true"
        )

        // The manifest shouldn't contain android:debuggable if we set the testBuildType to release.
        project.buildFile.appendText("\n\nandroid.testBuildType \"release\"\n\n")
        project.executor().run("assembleReleaseAndroidTest")
        val releaseManifestContent =
            getManifestContent(project.getApk(GradleTestProject.ApkType.ANDROIDTEST_RELEASE).file)
        assertManifestContentDoesNotContainString(releaseManifestContent, "android:debuggable")
    }

    @Test
    fun testDebuggingFlagCanBeSet() {
        project.buildFile.appendText("""
            android {
                testBuildType = "release"
            }
            androidComponents {
                beforeVariants(selector().withBuildType("release"), { variantBuilder ->
                    variantBuilder.deviceTests.get("AndroidTest").debuggable = true
                })
                onVariants(selector().withBuildType("release"), { variant ->
                    if (!variant.deviceTests.get("AndroidTest").debuggable) {
                        throw new RuntimeException("DeviceTest.debuggable value not set to true")
                    }
                })
            }
        """.trimIndent())
        project.buildFile.appendText("\n\nandroid.testBuildType \"release\"\n\n")
        project.executor().run("assembleReleaseAndroidTest")
        val releaseManifestContent =
            getManifestContent(project.getApk(GradleTestProject.ApkType.ANDROIDTEST_RELEASE).file)
        assertManifestContentContainsString(releaseManifestContent, "android:debuggable")
    }

    @Test
    fun testManifestOverlays() {
        project.buildFile.appendText("""
            android {
                flavorDimensions "app", "recents"

                productFlavors {
                    flavor1 {
                        dimension "app"
                    }

                    flavor2 {
                        dimension "recents"
                    }
                }
            }
        """.trimIndent())
        FileUtils.createFile(
            project.file("src/androidTest/AndroidManifest.xml"),
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application>
                        <receiver android:name="com.example.helloworld.TestReceiver" />
                    </application>
                </manifest>
            """.trimIndent()
        )
        FileUtils.createFile(
            project.file("src/androidTestFlavor1/AndroidManifest.xml"),
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application
                        android:allowBackup="true">
                    </application>
                </manifest>
            """.trimIndent()
        )
        FileUtils.createFile(
            project.file("src/androidTestFlavor2/AndroidManifest.xml"),
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application
                        android:supportsRtl="true">
                    </application>
                </manifest>
            """.trimIndent()
        )
        FileUtils.createFile(
            project.file("src/androidTestDebug/AndroidManifest.xml"),
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application
                        android:isGame="false">
                    </application>
                </manifest>
            """.trimIndent()
        )
        project.executor().run("assembleFlavor1Flavor2DebugAndroidTest")
        val manifestContent = project.file("build/intermediates/packaged_manifests/flavor1Flavor2DebugAndroidTest/processFlavor1Flavor2DebugAndroidTestManifest/AndroidManifest.xml")
        // merged from androidTestDebug
        assertThat(manifestContent).contains("android:isGame=\"false\"")
        // merged from androidTestFlavor2
        assertThat(manifestContent).contains("android:supportsRtl=\"true\"")
        // merged from androidTestFlavor1
        assertThat(manifestContent).contains("android:allowBackup=\"true\"")
    }

    @Test
    fun testNonUniqueNamespaces() {
        project.buildFile.appendText("""
            android {
                namespace "allowedNonUnique"
            }
        """.trimIndent())
        FileUtils.createFile(
                project.file("src/androidTest/AndroidManifest.xml"),
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application
                        android:isGame="false">
                    </application>
                </manifest>
            """.trimIndent())
        val result = project.executor().run("processDebugAndroidTestManifest")
        result.stdout.use {
            assertThat(it).doesNotContain("Namespace 'allowedNonUnique.test' used in:")
        }
    }

    @Test
    fun testWarningForExtractNativeLibsAttribute() {
        FileUtils.createFile(
            project.file("src/androidTest/AndroidManifest.xml"),
            """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application android:extractNativeLibs="true"/>
                </manifest>
            """.trimIndent())
        val result = project.executor().run("assembleDebugAndroidTest")
        result.stdout.use {
            assertThat(it).contains("android:extractNativeLibs should not be specified")
        }
    }

    @Test
    fun testUnitTestManifestPlaceholdersFromTestedVariant() {
        project.buildFile.appendText("""
            android {
                buildTypes {
                    release {
                        manifestPlaceholders = ["label": "unit test from tested variant"]
                    }
                }
                testOptions {
                    unitTests {
                        includeAndroidResources = true
                    }
                }
            }
        """.trimIndent())
        project.file("src/main/AndroidManifest.xml").delete()
        FileUtils.createFile(
                project.file("src/main/AndroidManifest.xml"),
                """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                            android:versionCode="1"
                            android:versionName="1.0">

                    <application android:label="${'$'}{label}">
                        <activity android:name=".HelloWorld"
                                  android:label="@string/app_name">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>

                        <receiver android:name="com.example.helloworld.MainReceiver" />
                    </application>
                </manifest>
            """.trimIndent()
        )
        val result = project.executor().run("processReleaseUnitTestManifest")
        assertTrue { result.failedTasks.isEmpty()}
        val manifestFile = project.file("build/intermediates/packaged_manifests/releaseUnitTest/processReleaseUnitTestManifest/AndroidManifest.xml")
        assertThat(manifestFile).contains("android:label=\"unit test from tested variant\"")
    }

    @Test
    fun testUnitTestManifestPlaceholdersFromVariantApi() {
        project.buildFile.appendText("""
            android {
                testOptions {
                    unitTests {
                        includeAndroidResources = true
                    }
                }
            }
            androidComponents {
                onVariants(selector().all(), { variant ->
                    variant.unitTest.manifestPlaceholders["label"] = "unit test from tested variant"
                })
            }
        """.trimIndent())
        project.file("src/main/AndroidManifest.xml").delete()
        FileUtils.createFile(
            project.file("src/main/AndroidManifest.xml"),
            """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                            android:versionCode="1"
                            android:versionName="1.0">

                    <application android:label="${'$'}{label}">
                        <activity android:name=".HelloWorld"
                                  android:label="@string/app_name">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>

                        <receiver android:name="com.example.helloworld.MainReceiver" />
                    </application>
                </manifest>
            """.trimIndent()
        )
        val result = project.executor().run("processReleaseUnitTestManifest")
        assertTrue { result.failedTasks.isEmpty()}
        val manifestFile = project.file("build/intermediates/packaged_manifests/releaseUnitTest/processReleaseUnitTestManifest/AndroidManifest.xml")
        assertThat(manifestFile).contains("android:label=\"unit test from tested variant\"")
    }

    @Test
    fun testUnitTestManifestContainsTargetSdkVersion() {
        project.buildFile.appendText("""
            android {
                testOptions {
                    unitTests {
                        includeAndroidResources = true
                    }
                }
            }
            androidComponents {
                beforeVariants(selector().all(), { variant ->
                    variant.targetSdk = 22
                })
            }
        """.trimIndent())
        val result = project.executor().run("processReleaseUnitTestManifest")
        assertTrue { result.failedTasks.isEmpty()}
        val manifestFile = project.file("build/intermediates/packaged_manifests/releaseUnitTest/processReleaseUnitTestManifest/AndroidManifest.xml")
        assertThat(manifestFile).contains("android:targetSdkVersion=\"22\"")
    }

    @Test
    fun testLibraryUnitTestManifestContainsTargetSdkVersionFromOptions() {
        project.buildFile.appendText("""
            android {
                testOptions {
                    targetSdk = 22
                    unitTests {
                        includeAndroidResources = true
                    }
                }
            }
        """.trimIndent())
        val result = project.executor().run("processReleaseUnitTestManifest")
        assertTrue { result.failedTasks.isEmpty()}
        val manifestFile = project.file("build/intermediates/packaged_manifests/releaseUnitTest/processReleaseUnitTestManifest/AndroidManifest.xml")
        assertThat(manifestFile).contains("android:targetSdkVersion=\"22\"")
    }

    /**
     * Test that the testNamespace is used to create the fully qualified namespace when
     * ".TestActivity" is used as a class shorthand in the androidTest manifest.
     */
    @Test
    fun testClassShorthandInTestManifest() {
        FileUtils.createFile(
            project.file("src/androidTest/AndroidManifest.xml"),
            """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">

                    <application>
                        <activity
                            android:name=".TestActivity"
                            android:exported="true" />
                    </application>
                </manifest>
            """.trimIndent()
        )

        project.file("src/main/AndroidManifest.xml").delete()
        FileUtils.createFile(
            project.file("src/main/AndroidManifest.xml"),
            """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                            android:versionCode="1"
                            android:versionName="1.0">

                    <application android:label="@string/app_name">
                        <activity android:name=".HelloWorld"
                                  android:label="@string/app_name">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
            """.trimIndent()
        )

        project.executor().run("assembleDebugAndroidTest")

        val manifestContent = getManifestContent(project.testApk.file)
        assertManifestContentContainsString(manifestContent, "com.example.helloworld.HelloWorld")
        assertManifestContentContainsString(
            manifestContent,
            "com.example.helloworld.test.TestActivity"
        )
    }

    private fun assertManifestContentContainsString(
        manifestContent: Iterable<String>,
        stringToAssert: String
    ) {
        manifestContent.forEach { if (it.trim().contains(stringToAssert)) return }
        fail("Cannot find $stringToAssert in ${manifestContent.joinToString(separator = "\n")}")
    }

    private fun assertManifestContentDoesNotContainString(
        manifestContent: Iterable<String>,
        stringToAssert: String
    ) {
        manifestContent.forEach {
            if (it.trim().contains(stringToAssert)) {
                fail("$stringToAssert found in ${manifestContent.joinToString(separator = "\n")}")
            }
        }
    }
}
