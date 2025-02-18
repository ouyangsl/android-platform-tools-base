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

package com.android.tools.lint.model

import com.android.sdklib.AndroidVersion
import com.android.sdklib.SdkVersionInfo
import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_API
import com.android.testutils.truth.PathSubject
import com.android.tools.lint.checks.infrastructure.GradleModelMocker
import com.android.tools.lint.checks.infrastructure.GradleModelMockerTest
import com.android.tools.lint.checks.infrastructure.dos2unix
import com.android.tools.lint.checks.infrastructure.portablePath
import com.android.tools.lint.model.LintModelSerialization.LintModelSerializationFileAdapter
import com.android.tools.lint.model.LintModelSerialization.TargetFile
import com.android.utils.XmlUtils
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.io.IOException
import java.io.Reader
import java.io.StringReader
import java.io.StringWriter
import java.io.Writer
import java.nio.file.Files
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException

class LintModelSerializationTest {
  @get:Rule var temporaryFolder = TemporaryFolder()

  @Test
  fun testFlavors() {
    val mocker: GradleModelMocker =
      GradleModelMockerTest.createMocker(
        """
            buildscript {
                repositories {
                    jcenter()
                }
                dependencies {
                    classpath 'com.android.tools.build:gradle:4.0.0-beta01'
                }
            }

            apply plugin: 'com.android.application'
            apply plugin: 'kotlin-android'

            groupId = "com.android.tools.demo"

            android {
                compileSdkVersion 25
                defaultConfig {
                    applicationId "com.android.tools.test"
                    minSdkVersion 5
                    targetSdkVersion 16
                    versionCode 2
                    versionName "MyName"
                    resConfigs "mdpi"
                    resValue "string", "defaultConfigName", "Some DefaultConfig Data"
                    manifestPlaceholders = [ localApplicationId:"com.example.manifest_merger_example"]
                }
                flavorDimensions  "pricing", "releaseType"
                productFlavors {
                    beta {
                        dimension "releaseType"
                        resConfig "en"
                        resConfigs "nodpi", "hdpi"
                        versionNameSuffix "-beta"
                        applicationIdSuffix '.beta'
                        resValue "string", "VALUE_DEBUG",   "10"
                        resValue "string", "VALUE_FLAVOR",  "10"
                        resValue "string", "VALUE_VARIANT", "10"
                        manifestPlaceholders = [ localApplicationId:"com.example.manifest_merger_example.flavor"]
                    }
                    normal { dimension "releaseType" }
                    free { dimension "pricing" }
                    paid { dimension "pricing" }
                }

                buildFeatures {
                    viewBinding true
                }

                lintOptions {
                    quiet true
                    abortOnError false
                    ignoreWarnings true
                    absolutePaths false
                    checkAllWarnings true
                    warningsAsErrors true
                    disable 'TypographyFractions','TypographyQuotes'
                    enable 'RtlHardcoded','RtlCompat', 'RtlEnabled'
                    check 'NewApi', 'InlinedApi'
                    noLines true
                    showAll true
                    lintConfig file("default-lint.xml")
                    baseline file("baseline.xml")
                    warning 'FooBar'
                    informational 'LogConditional'
                    checkTestSources true
                    checkDependencies true
                }

                buildTypes {
                    debug {
                        resValue "string", "debugName", "Some Debug Data"
                        manifestPlaceholders = ["holder":"debug"]
                    }
                    release {
                        resValue "string", "releaseName1", "Some Release Data 1"
                        resValue "string", "releaseName2", "Some Release Data 2"
                    }
                }
            }

            dependencies {
                // Android libraries
                compile "com.android.support:appcompat-v7:25.0.1"
                compile "com.android.support.constraint:constraint-layout:1.0.0-beta3"
                // Java libraries
                implementation "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.0"
            }
            """
          .trimIndent(),
        temporaryFolder,
      )

    checkSerialization(
      mocker,
      mapOf(
        "module" to
          """
                <lint-module
                    format="1"
                    dir="＄ROOT"
                    name="test_project-build"
                    type="APP"
                    maven="com.android.tools.demo:test_project-build:"
                    agpVersion="4.0.0-beta01"
                    buildFolder="build"
                    javaSourceLevel="1.7"
                    compileTarget="android-25"
                    neverShrinking="true">
                  <lintOptions
                      lintConfig="default-lint.xml"
                      baselineFile="baseline.xml"
                      checkDependencies="true"
                      checkTestSources="true"
                      abortOnError="true"
                      absolutePaths="true"
                      checkReleaseBuilds="true"
                      explainIssues="true"
                      htmlReport="true"
                      xmlReport="true">
                    <severities>
                      <severity
                        id="FooBar"
                        severity="WARNING" />
                      <severity
                        id="LogConditional"
                        severity="INFORMATIONAL" />
                      <severity
                        id="RtlCompat"
                        severity="WARNING" />
                      <severity
                        id="RtlEnabled"
                        severity="WARNING" />
                      <severity
                        id="RtlHardcoded"
                        severity="WARNING" />
                      <severity
                        id="TypographyFractions"
                        severity="IGNORE" />
                      <severity
                        id="TypographyQuotes"
                        severity="IGNORE" />
                    </severities>
                  </lintOptions>
                  <variant name="freeBetaDebug"/>
                  <variant name="freeNormalDebug"/>
                  <variant name="paidBetaDebug"/>
                  <variant name="paidNormalDebug"/>
                  <variant name="freeBetaRelease"/>
                  <variant name="freeNormalRelease"/>
                  <variant name="paidBetaRelease"/>
                  <variant name="paidNormalRelease"/>
                </lint-module>
                """,
        "variant-freeBetaDebug" to
          """
                <variant
                    name="freeBetaDebug"
                    minSdkVersion="5"
                    targetSdkVersion="16"
                    debuggable="true"
                    resourceConfigurations="en,nodpi,hdpi,mdpi">
                  <buildFeatures
                      viewBinding="true"/>
                  <sourceProviders>
                    <sourceProvider
                        manifests="src/main/AndroidManifest.xml"
                        javaDirectories="src/main/java:src/main/kotlin"
                        resDirectories="src/main/res"
                        assetsDirectories="src/main/assets"/>
                    <sourceProvider
                        manifests="src/beta/AndroidManifest.xml"
                        javaDirectories="src/beta/java:src/beta/kotlin"
                        resDirectories="src/beta/res"
                        assetsDirectories="src/beta/assets"/>
                    <sourceProvider
                        manifests="src/free/AndroidManifest.xml"
                        javaDirectories="src/free/java:src/free/kotlin"
                        resDirectories="src/free/res"
                        assetsDirectories="src/free/assets"/>
                    <sourceProvider
                        manifests="src/freeBeta/AndroidManifest.xml"
                        javaDirectories="src/freeBeta/java:src/freeBeta/kotlin"
                        resDirectories="src/freeBeta/res"
                        assetsDirectories="src/freeBeta/assets"/>
                    <sourceProvider
                        manifests="src/debug/AndroidManifest.xml"
                        javaDirectories="src/debug/java:src/debug/kotlin"
                        resDirectories="src/debug/res"
                        assetsDirectories="src/debug/assets"
                        debugOnly="true"/>
                    <sourceProvider
                        manifests="src/freeBetaDebug/AndroidManifest.xml"
                        javaDirectories="src/freeBetaDebug/java:src/freeBetaDebug/kotlin"
                        resDirectories="src/freeBetaDebug/res"
                        assetsDirectories="src/freeBetaDebug/assets"
                        debugOnly="true"/>
                  </sourceProviders>
                  <testSourceProviders>
                    <sourceProvider
                        manifests="src/androidTest/AndroidManifest.xml"
                        javaDirectories="src/androidTest/java:src/androidTest/kotlin"
                        resDirectories="src/androidTest/res"
                        assetsDirectories="src/androidTest/assets"
                        androidTest="true"/>
                    <sourceProvider
                        manifests="src/test/AndroidManifest.xml"
                        javaDirectories="src/test/java:src/test/kotlin"
                        resDirectories="src/test/res"
                        assetsDirectories="src/test/assets"
                        unitTest="true"/>
                    <sourceProvider
                        manifests="src/androidTestBeta/AndroidManifest.xml"
                        javaDirectories="src/androidTestBeta/java:src/androidTestBeta/kotlin"
                        resDirectories="src/androidTestBeta/res"
                        assetsDirectories="src/androidTestBeta/assets"
                        androidTest="true"/>
                    <sourceProvider
                        manifests="src/testBeta/AndroidManifest.xml"
                        javaDirectories="src/testBeta/java:src/testBeta/kotlin"
                        resDirectories="src/testBeta/res"
                        assetsDirectories="src/testBeta/assets"
                        unitTest="true"/>
                    <sourceProvider
                        manifests="src/androidTestFree/AndroidManifest.xml"
                        javaDirectories="src/androidTestFree/java:src/androidTestFree/kotlin"
                        resDirectories="src/androidTestFree/res"
                        assetsDirectories="src/androidTestFree/assets"
                        androidTest="true"/>
                    <sourceProvider
                        manifests="src/testFree/AndroidManifest.xml"
                        javaDirectories="src/testFree/java:src/testFree/kotlin"
                        resDirectories="src/testFree/res"
                        assetsDirectories="src/testFree/assets"
                        unitTest="true"/>
                  </testSourceProviders>
                  <testFixturesSourceProviders>
                    <sourceProvider
                        manifests="src/testFixtures/AndroidManifest.xml"
                        javaDirectories="src/testFixtures/java:src/testFixtures/kotlin"
                        resDirectories="src/testFixtures/res"
                        assetsDirectories="src/testFixtures/assets"
                        testFixture="true"/>
                    <sourceProvider
                        manifests="src/testFixturesBeta/AndroidManifest.xml"
                        javaDirectories="src/testFixturesBeta/java:src/testFixturesBeta/kotlin"
                        resDirectories="src/testFixturesBeta/res"
                        assetsDirectories="src/testFixturesBeta/assets"
                        testFixture="true"/>
                    <sourceProvider
                        manifests="src/testFixturesFree/AndroidManifest.xml"
                        javaDirectories="src/testFixturesFree/java:src/testFixturesFree/kotlin"
                        resDirectories="src/testFixturesFree/res"
                        assetsDirectories="src/testFixturesFree/assets"
                        testFixture="true"/>
                  </testFixturesSourceProviders>
                  <resValues>
                    <resValue
                        type="string"
                        name="VALUE_DEBUG"
                        value="10" />
                    <resValue
                        type="string"
                        name="VALUE_FLAVOR"
                        value="10" />
                    <resValue
                        type="string"
                        name="VALUE_VARIANT"
                        value="10" />
                    <resValue
                        type="string"
                        name="debugName"
                        value="Some Debug Data" />
                    <resValue
                        type="string"
                        name="defaultConfigName"
                        value="Some DefaultConfig Data" />
                  </resValues>
                  <manifestPlaceholders>
                    <placeholder
                        name="holder"
                        value="debug" />
                    <placeholder
                        name="localApplicationId"
                        value="com.example.manifest_merger_example.flavor" />
                  </manifestPlaceholders>
                  <artifact
                      classOutputs="build/intermediates/javac/freeBetaDebug/classes:build/tmp/kotlin-classes/freeBetaDebug"
                      type="MAIN"
                      applicationId="com.android.tools.test">
                  </artifact>
                  <androidTestArtifact
                      classOutputs="instrumentation-classes"
                      applicationId="com.android.tools.test">
                  </androidTestArtifact>
                  <testFixturesArtifact
                      classOutputs="build/intermediates/javac/freeBetaDebugTestFixtures/classes:build/tmp/kotlin-classes/freeBetaDebugTestFixtures"
                      applicationId="com.android.tools.test">
                  </testFixturesArtifact>
                  <testArtifact
                      classOutputs="test-classes">
                  </testArtifact>
                </variant>
              """,
        "dependencies-freeBetaDebug-artifact" to
          """
                    <dependencies>
                      <compile
                          roots="com.android.support:appcompat-v7:25.0.1,com.android.support:support-v4:25.0.1,com.android.support:support-compat:25.0.1,com.android.support:support-media-compat:25.0.1,com.android.support:support-core-utils:25.0.1,com.android.support:support-core-ui:25.0.1,com.android.support:support-fragment:25.0.1,com.android.support:support-vector-drawable:25.0.1,com.android.support:animated-vector-drawable:25.0.1,com.android.support.constraint:constraint-layout:1.0.0-beta3,com.android.support:support-annotations:25.0.1,com.android.support.constraint:constraint-layout-solver:1.0.0-beta3,org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.0,org.jetbrains.kotlin:kotlin-stdlib:1.3.0,org.jetbrains.kotlin:kotlin-stdlib-common:1.3.0,org.jetbrains:annotations:13.0">
                        <dependency
                            name="com.android.support:appcompat-v7:25.0.1"
                            simpleName="com.android.support:appcompat-v7"/>
                        <dependency
                            name="com.android.support:support-v4:25.0.1"
                            simpleName="com.android.support:support-v4"/>
                        <dependency
                            name="com.android.support:support-compat:25.0.1"
                            simpleName="com.android.support:support-compat"/>
                        <dependency
                            name="com.android.support:support-media-compat:25.0.1"
                            simpleName="com.android.support:support-media-compat"/>
                        <dependency
                            name="com.android.support:support-core-utils:25.0.1"
                            simpleName="com.android.support:support-core-utils"/>
                        <dependency
                            name="com.android.support:support-core-ui:25.0.1"
                            simpleName="com.android.support:support-core-ui"/>
                        <dependency
                            name="com.android.support:support-fragment:25.0.1"
                            simpleName="com.android.support:support-fragment"/>
                        <dependency
                            name="com.android.support:support-vector-drawable:25.0.1"
                            simpleName="com.android.support:support-vector-drawable"/>
                        <dependency
                            name="com.android.support:animated-vector-drawable:25.0.1"
                            simpleName="com.android.support:animated-vector-drawable"/>
                        <dependency
                            name="com.android.support.constraint:constraint-layout:1.0.0-beta3"
                            simpleName="com.android.support.constraint:constraint-layout"/>
                        <dependency
                            name="com.android.support:support-annotations:25.0.1"
                            simpleName="com.android.support:support-annotations"/>
                        <dependency
                            name="com.android.support.constraint:constraint-layout-solver:1.0.0-beta3"
                            simpleName="com.android.support.constraint:constraint-layout-solver"/>
                        <dependency
                            name="org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.0"
                            simpleName="org.jetbrains.kotlin:kotlin-stdlib-jdk7"/>
                        <dependency
                            name="org.jetbrains.kotlin:kotlin-stdlib:1.3.0"
                            simpleName="org.jetbrains.kotlin:kotlin-stdlib"/>
                        <dependency
                            name="org.jetbrains.kotlin:kotlin-stdlib-common:1.3.0"
                            simpleName="org.jetbrains.kotlin:kotlin-stdlib-common"/>
                        <dependency
                            name="org.jetbrains:annotations:13.0"
                            simpleName="org.jetbrains:annotations"/>
                      </compile>
                      <package
                          roots="com.android.support:appcompat-v7:25.0.1,com.android.support:support-v4:25.0.1,com.android.support:support-compat:25.0.1,com.android.support:support-media-compat:25.0.1,com.android.support:support-core-utils:25.0.1,com.android.support:support-core-ui:25.0.1,com.android.support:support-fragment:25.0.1,com.android.support:support-vector-drawable:25.0.1,com.android.support:animated-vector-drawable:25.0.1,com.android.support.constraint:constraint-layout:1.0.0-beta3,com.android.support:support-annotations:25.0.1,com.android.support.constraint:constraint-layout-solver:1.0.0-beta3,org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.0,org.jetbrains.kotlin:kotlin-stdlib:1.3.0,org.jetbrains.kotlin:kotlin-stdlib-common:1.3.0,org.jetbrains:annotations:13.0">
                        <dependency
                            name="com.android.support:appcompat-v7:25.0.1"
                            simpleName="com.android.support:appcompat-v7"/>
                        <dependency
                            name="com.android.support:support-v4:25.0.1"
                            simpleName="com.android.support:support-v4"/>
                        <dependency
                            name="com.android.support:support-compat:25.0.1"
                            simpleName="com.android.support:support-compat"/>
                        <dependency
                            name="com.android.support:support-media-compat:25.0.1"
                            simpleName="com.android.support:support-media-compat"/>
                        <dependency
                            name="com.android.support:support-core-utils:25.0.1"
                            simpleName="com.android.support:support-core-utils"/>
                        <dependency
                            name="com.android.support:support-core-ui:25.0.1"
                            simpleName="com.android.support:support-core-ui"/>
                        <dependency
                            name="com.android.support:support-fragment:25.0.1"
                            simpleName="com.android.support:support-fragment"/>
                        <dependency
                            name="com.android.support:support-vector-drawable:25.0.1"
                            simpleName="com.android.support:support-vector-drawable"/>
                        <dependency
                            name="com.android.support:animated-vector-drawable:25.0.1"
                            simpleName="com.android.support:animated-vector-drawable"/>
                        <dependency
                            name="com.android.support.constraint:constraint-layout:1.0.0-beta3"
                            simpleName="com.android.support.constraint:constraint-layout"/>
                        <dependency
                            name="com.android.support:support-annotations:25.0.1"
                            simpleName="com.android.support:support-annotations"/>
                        <dependency
                            name="com.android.support.constraint:constraint-layout-solver:1.0.0-beta3"
                            simpleName="com.android.support.constraint:constraint-layout-solver"/>
                        <dependency
                            name="org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.0"
                            simpleName="org.jetbrains.kotlin:kotlin-stdlib-jdk7"/>
                        <dependency
                            name="org.jetbrains.kotlin:kotlin-stdlib:1.3.0"
                            simpleName="org.jetbrains.kotlin:kotlin-stdlib"/>
                        <dependency
                            name="org.jetbrains.kotlin:kotlin-stdlib-common:1.3.0"
                            simpleName="org.jetbrains.kotlin:kotlin-stdlib-common"/>
                        <dependency
                            name="org.jetbrains:annotations:13.0"
                            simpleName="org.jetbrains:annotations"/>
                      </package>
                    </dependencies>
                """,
        "dependencies-freeBetaDebug-testArtifact" to
          """
                <dependencies>
                </dependencies>
                """,
        "dependencies-freeBetaDebug-androidTestArtifact" to
          """
                <dependencies>
                </dependencies>
                """,
        "library_table-freeBetaDebug-artifact" to
          """
                <libraries>
                  <library
                      name="com.android.support:appcompat-v7:25.0.1"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support/appcompat-v7/25.0.1/jars/classes.jar"
                      resolved="com.android.support:appcompat-v7:25.0.1"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support/appcompat-v7/25.0.1"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support:support-v4:25.0.1"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-v4/25.0.1/jars/classes.jar"
                      resolved="com.android.support:support-v4:25.0.1"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-v4/25.0.1"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support:support-compat:25.0.1"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-compat/25.0.1/jars/classes.jar"
                      resolved="com.android.support:support-compat:25.0.1"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-compat/25.0.1"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support:support-media-compat:25.0.1"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-media-compat/25.0.1/jars/classes.jar"
                      resolved="com.android.support:support-media-compat:25.0.1"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-media-compat/25.0.1"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support:support-core-utils:25.0.1"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-core-utils/25.0.1/jars/classes.jar"
                      resolved="com.android.support:support-core-utils:25.0.1"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-core-utils/25.0.1"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support:support-core-ui:25.0.1"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-core-ui/25.0.1/jars/classes.jar"
                      resolved="com.android.support:support-core-ui:25.0.1"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-core-ui/25.0.1"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support:support-fragment:25.0.1"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-fragment/25.0.1/jars/classes.jar"
                      resolved="com.android.support:support-fragment:25.0.1"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-fragment/25.0.1"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support:support-vector-drawable:25.0.1"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-vector-drawable/25.0.1/jars/classes.jar"
                      resolved="com.android.support:support-vector-drawable:25.0.1"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-vector-drawable/25.0.1"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support:animated-vector-drawable:25.0.1"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support/animated-vector-drawable/25.0.1/jars/classes.jar"
                      resolved="com.android.support:animated-vector-drawable:25.0.1"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support/animated-vector-drawable/25.0.1"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support.constraint:constraint-layout:1.0.0-beta3"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support.constraint/constraint-layout/1.0.0-beta3/jars/classes.jar"
                      resolved="com.android.support.constraint:constraint-layout:1.0.0-beta3"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support.constraint/constraint-layout/1.0.0-beta3"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support:support-annotations:25.0.1"
                      jars="＄ROOT/caches/modules-2/files-2.1/com.android.support/support-annotations/25.0.1/9c6ef172e8de35fd8d4d8783e4821e57cdef7445/support-annotations-25.0.1.jar"
                      resolved="com.android.support:support-annotations:25.0.1"/>
                  <library
                      name="com.android.support.constraint:constraint-layout-solver:1.0.0-beta3"
                      jars="＄ROOT/caches/modules-2/files-2.1/com.android.support.constraint/constraint-layout-solver/1.0.0-beta3/9c6ef172e8de35fd8d4d8783e4821e57cdef7445/constraint-layout-solver-1.0.0-beta3.jar"
                      resolved="com.android.support.constraint:constraint-layout-solver:1.0.0-beta3"/>
                  <library
                      name="org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.0"
                      jars="＄ROOT/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-jdk7/1.3.0/9c6ef172e8de35fd8d4d8783e4821e57cdef7445/kotlin-stdlib-jdk7-1.3.0.jar"
                      resolved="org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.0"/>
                  <library
                      name="org.jetbrains.kotlin:kotlin-stdlib:1.3.0"
                      jars="＄ROOT/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/1.3.0/9c6ef172e8de35fd8d4d8783e4821e57cdef7445/kotlin-stdlib-1.3.0.jar"
                      resolved="org.jetbrains.kotlin:kotlin-stdlib:1.3.0"/>
                  <library
                      name="org.jetbrains.kotlin:kotlin-stdlib-common:1.3.0"
                      jars="＄ROOT/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-common/1.3.0/9c6ef172e8de35fd8d4d8783e4821e57cdef7445/kotlin-stdlib-common-1.3.0.jar"
                      resolved="org.jetbrains.kotlin:kotlin-stdlib-common:1.3.0"/>
                  <library
                      name="org.jetbrains:annotations:13.0"
                      jars="＄ROOT/caches/modules-2/files-2.1/org.jetbrains/annotations/13.0/9c6ef172e8de35fd8d4d8783e4821e57cdef7445/annotations-13.0.jar"
                      resolved="org.jetbrains:annotations:13.0"/>
                </libraries>
                """,
        "library_table-freeBetaDebug-testArtifact" to
          """
                <libraries>
                </libraries>
                """,
        "library_table-freeBetaDebug-androidTestArtifact" to
          """
                <libraries>
                </libraries>
                """,
        "variant-paidNormalRelease" to
          """
                    <variant
                        name="paidNormalRelease"
                        minSdkVersion="5"
                        targetSdkVersion="16"
                        resourceConfigurations="mdpi">
                      <buildFeatures
                          viewBinding="true"/>
                      <sourceProviders>
                        <sourceProvider
                            manifests="src/main/AndroidManifest.xml"
                            javaDirectories="src/main/java:src/main/kotlin"
                            resDirectories="src/main/res"
                            assetsDirectories="src/main/assets"/>
                        <sourceProvider
                            manifests="src/normal/AndroidManifest.xml"
                            javaDirectories="src/normal/java:src/normal/kotlin"
                            resDirectories="src/normal/res"
                            assetsDirectories="src/normal/assets"/>
                        <sourceProvider
                            manifests="src/paid/AndroidManifest.xml"
                            javaDirectories="src/paid/java:src/paid/kotlin"
                            resDirectories="src/paid/res"
                            assetsDirectories="src/paid/assets"/>
                        <sourceProvider
                            manifests="src/paidNormal/AndroidManifest.xml"
                            javaDirectories="src/paidNormal/java:src/paidNormal/kotlin"
                            resDirectories="src/paidNormal/res"
                            assetsDirectories="src/paidNormal/assets"/>
                        <sourceProvider
                            manifests="src/release/AndroidManifest.xml"
                            javaDirectories="src/release/java:src/release/kotlin"
                            resDirectories="src/release/res"
                            assetsDirectories="src/release/assets"/>
                        <sourceProvider
                            manifests="src/paidNormalRelease/AndroidManifest.xml"
                            javaDirectories="src/paidNormalRelease/java:src/paidNormalRelease/kotlin"
                            resDirectories="src/paidNormalRelease/res"
                            assetsDirectories="src/paidNormalRelease/assets"/>
                      </sourceProviders>
                      <testSourceProviders>
                        <sourceProvider
                            manifests="src/androidTest/AndroidManifest.xml"
                            javaDirectories="src/androidTest/java:src/androidTest/kotlin"
                            resDirectories="src/androidTest/res"
                            assetsDirectories="src/androidTest/assets"
                            androidTest="true"/>
                        <sourceProvider
                            manifests="src/test/AndroidManifest.xml"
                            javaDirectories="src/test/java:src/test/kotlin"
                            resDirectories="src/test/res"
                            assetsDirectories="src/test/assets"
                            unitTest="true"/>
                        <sourceProvider
                            manifests="src/androidTestNormal/AndroidManifest.xml"
                            javaDirectories="src/androidTestNormal/java:src/androidTestNormal/kotlin"
                            resDirectories="src/androidTestNormal/res"
                            assetsDirectories="src/androidTestNormal/assets"
                            androidTest="true"/>
                        <sourceProvider
                            manifests="src/testNormal/AndroidManifest.xml"
                            javaDirectories="src/testNormal/java:src/testNormal/kotlin"
                            resDirectories="src/testNormal/res"
                            assetsDirectories="src/testNormal/assets"
                            unitTest="true"/>
                        <sourceProvider
                            manifests="src/androidTestPaid/AndroidManifest.xml"
                            javaDirectories="src/androidTestPaid/java:src/androidTestPaid/kotlin"
                            resDirectories="src/androidTestPaid/res"
                            assetsDirectories="src/androidTestPaid/assets"
                            androidTest="true"/>
                        <sourceProvider
                            manifests="src/testPaid/AndroidManifest.xml"
                            javaDirectories="src/testPaid/java:src/testPaid/kotlin"
                            resDirectories="src/testPaid/res"
                            assetsDirectories="src/testPaid/assets"
                            unitTest="true"/>
                      </testSourceProviders>
                      <testFixturesSourceProviders>
                        <sourceProvider
                            manifests="src/testFixtures/AndroidManifest.xml"
                            javaDirectories="src/testFixtures/java:src/testFixtures/kotlin"
                            resDirectories="src/testFixtures/res"
                            assetsDirectories="src/testFixtures/assets"
                            testFixture="true"/>
                        <sourceProvider
                            manifests="src/testFixturesNormal/AndroidManifest.xml"
                            javaDirectories="src/testFixturesNormal/java:src/testFixturesNormal/kotlin"
                            resDirectories="src/testFixturesNormal/res"
                            assetsDirectories="src/testFixturesNormal/assets"
                            testFixture="true"/>
                        <sourceProvider
                            manifests="src/testFixturesPaid/AndroidManifest.xml"
                            javaDirectories="src/testFixturesPaid/java:src/testFixturesPaid/kotlin"
                            resDirectories="src/testFixturesPaid/res"
                            assetsDirectories="src/testFixturesPaid/assets"
                            testFixture="true"/>
                      </testFixturesSourceProviders>
                      <resValues>
                        <resValue
                            type="string"
                            name="defaultConfigName"
                            value="Some DefaultConfig Data" />
                        <resValue
                            type="string"
                            name="releaseName1"
                            value="Some Release Data 1" />
                        <resValue
                            type="string"
                            name="releaseName2"
                            value="Some Release Data 2" />
                      </resValues>
                      <manifestPlaceholders>
                        <placeholder
                            name="localApplicationId"
                            value="com.example.manifest_merger_example" />
                      </manifestPlaceholders>
                      <artifact
                          classOutputs="build/intermediates/javac/paidNormalRelease/classes:build/tmp/kotlin-classes/paidNormalRelease"
                          type="MAIN"
                          applicationId="com.android.tools.test">
                      </artifact>
                      <androidTestArtifact
                          classOutputs="instrumentation-classes"
                          applicationId="com.android.tools.test">
                      </androidTestArtifact>
                      <testFixturesArtifact
                          classOutputs="build/intermediates/javac/paidNormalReleaseTestFixtures/classes:build/tmp/kotlin-classes/paidNormalReleaseTestFixtures"
                          applicationId="com.android.tools.test">
                      </testFixturesArtifact>
                      <testArtifact
                          classOutputs="test-classes">
                      </testArtifact>
                    </variant>
                """,
        "dependencies-paidNormalRelease-artifact" to
          """
                    <dependencies>
                      <compile
                          roots="com.android.support:appcompat-v7:25.0.1,com.android.support:support-v4:25.0.1,com.android.support:support-compat:25.0.1,com.android.support:support-media-compat:25.0.1,com.android.support:support-core-utils:25.0.1,com.android.support:support-core-ui:25.0.1,com.android.support:support-fragment:25.0.1,com.android.support:support-vector-drawable:25.0.1,com.android.support:animated-vector-drawable:25.0.1,com.android.support.constraint:constraint-layout:1.0.0-beta3,com.android.support:support-annotations:25.0.1,com.android.support.constraint:constraint-layout-solver:1.0.0-beta3,org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.0,org.jetbrains.kotlin:kotlin-stdlib:1.3.0,org.jetbrains.kotlin:kotlin-stdlib-common:1.3.0,org.jetbrains:annotations:13.0">
                        <dependency
                            name="com.android.support:appcompat-v7:25.0.1"
                            simpleName="com.android.support:appcompat-v7"/>
                        <dependency
                            name="com.android.support:support-v4:25.0.1"
                            simpleName="com.android.support:support-v4"/>
                        <dependency
                            name="com.android.support:support-compat:25.0.1"
                            simpleName="com.android.support:support-compat"/>
                        <dependency
                            name="com.android.support:support-media-compat:25.0.1"
                            simpleName="com.android.support:support-media-compat"/>
                        <dependency
                            name="com.android.support:support-core-utils:25.0.1"
                            simpleName="com.android.support:support-core-utils"/>
                        <dependency
                            name="com.android.support:support-core-ui:25.0.1"
                            simpleName="com.android.support:support-core-ui"/>
                        <dependency
                            name="com.android.support:support-fragment:25.0.1"
                            simpleName="com.android.support:support-fragment"/>
                        <dependency
                            name="com.android.support:support-vector-drawable:25.0.1"
                            simpleName="com.android.support:support-vector-drawable"/>
                        <dependency
                            name="com.android.support:animated-vector-drawable:25.0.1"
                            simpleName="com.android.support:animated-vector-drawable"/>
                        <dependency
                            name="com.android.support.constraint:constraint-layout:1.0.0-beta3"
                            simpleName="com.android.support.constraint:constraint-layout"/>
                        <dependency
                            name="com.android.support:support-annotations:25.0.1"
                            simpleName="com.android.support:support-annotations"/>
                        <dependency
                            name="com.android.support.constraint:constraint-layout-solver:1.0.0-beta3"
                            simpleName="com.android.support.constraint:constraint-layout-solver"/>
                        <dependency
                            name="org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.0"
                            simpleName="org.jetbrains.kotlin:kotlin-stdlib-jdk7"/>
                        <dependency
                            name="org.jetbrains.kotlin:kotlin-stdlib:1.3.0"
                            simpleName="org.jetbrains.kotlin:kotlin-stdlib"/>
                        <dependency
                            name="org.jetbrains.kotlin:kotlin-stdlib-common:1.3.0"
                            simpleName="org.jetbrains.kotlin:kotlin-stdlib-common"/>
                        <dependency
                            name="org.jetbrains:annotations:13.0"
                            simpleName="org.jetbrains:annotations"/>
                      </compile>
                      <package
                          roots="com.android.support:appcompat-v7:25.0.1,com.android.support:support-v4:25.0.1,com.android.support:support-compat:25.0.1,com.android.support:support-media-compat:25.0.1,com.android.support:support-core-utils:25.0.1,com.android.support:support-core-ui:25.0.1,com.android.support:support-fragment:25.0.1,com.android.support:support-vector-drawable:25.0.1,com.android.support:animated-vector-drawable:25.0.1,com.android.support.constraint:constraint-layout:1.0.0-beta3,com.android.support:support-annotations:25.0.1,com.android.support.constraint:constraint-layout-solver:1.0.0-beta3,org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.0,org.jetbrains.kotlin:kotlin-stdlib:1.3.0,org.jetbrains.kotlin:kotlin-stdlib-common:1.3.0,org.jetbrains:annotations:13.0">
                        <dependency
                            name="com.android.support:appcompat-v7:25.0.1"
                            simpleName="com.android.support:appcompat-v7"/>
                        <dependency
                            name="com.android.support:support-v4:25.0.1"
                            simpleName="com.android.support:support-v4"/>
                        <dependency
                            name="com.android.support:support-compat:25.0.1"
                            simpleName="com.android.support:support-compat"/>
                        <dependency
                            name="com.android.support:support-media-compat:25.0.1"
                            simpleName="com.android.support:support-media-compat"/>
                        <dependency
                            name="com.android.support:support-core-utils:25.0.1"
                            simpleName="com.android.support:support-core-utils"/>
                        <dependency
                            name="com.android.support:support-core-ui:25.0.1"
                            simpleName="com.android.support:support-core-ui"/>
                        <dependency
                            name="com.android.support:support-fragment:25.0.1"
                            simpleName="com.android.support:support-fragment"/>
                        <dependency
                            name="com.android.support:support-vector-drawable:25.0.1"
                            simpleName="com.android.support:support-vector-drawable"/>
                        <dependency
                            name="com.android.support:animated-vector-drawable:25.0.1"
                            simpleName="com.android.support:animated-vector-drawable"/>
                        <dependency
                            name="com.android.support.constraint:constraint-layout:1.0.0-beta3"
                            simpleName="com.android.support.constraint:constraint-layout"/>
                        <dependency
                            name="com.android.support:support-annotations:25.0.1"
                            simpleName="com.android.support:support-annotations"/>
                        <dependency
                            name="com.android.support.constraint:constraint-layout-solver:1.0.0-beta3"
                            simpleName="com.android.support.constraint:constraint-layout-solver"/>
                        <dependency
                            name="org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.0"
                            simpleName="org.jetbrains.kotlin:kotlin-stdlib-jdk7"/>
                        <dependency
                            name="org.jetbrains.kotlin:kotlin-stdlib:1.3.0"
                            simpleName="org.jetbrains.kotlin:kotlin-stdlib"/>
                        <dependency
                            name="org.jetbrains.kotlin:kotlin-stdlib-common:1.3.0"
                            simpleName="org.jetbrains.kotlin:kotlin-stdlib-common"/>
                        <dependency
                            name="org.jetbrains:annotations:13.0"
                            simpleName="org.jetbrains:annotations"/>
                      </package>
                    </dependencies>
                """,
        "dependencies-paidNormalRelease-testArtifact" to
          """
                <dependencies>
                </dependencies>
                """,
        "dependencies-paidNormalRelease-androidTestArtifact" to
          """
                <dependencies>
                </dependencies>
                """,
        "library_table-paidNormalRelease-artifact" to
          """
                <libraries>
                  <library
                      name="com.android.support:appcompat-v7:25.0.1"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support/appcompat-v7/25.0.1/jars/classes.jar"
                      resolved="com.android.support:appcompat-v7:25.0.1"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support/appcompat-v7/25.0.1"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support:support-v4:25.0.1"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-v4/25.0.1/jars/classes.jar"
                      resolved="com.android.support:support-v4:25.0.1"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-v4/25.0.1"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support:support-compat:25.0.1"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-compat/25.0.1/jars/classes.jar"
                      resolved="com.android.support:support-compat:25.0.1"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-compat/25.0.1"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support:support-media-compat:25.0.1"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-media-compat/25.0.1/jars/classes.jar"
                      resolved="com.android.support:support-media-compat:25.0.1"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-media-compat/25.0.1"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support:support-core-utils:25.0.1"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-core-utils/25.0.1/jars/classes.jar"
                      resolved="com.android.support:support-core-utils:25.0.1"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-core-utils/25.0.1"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support:support-core-ui:25.0.1"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-core-ui/25.0.1/jars/classes.jar"
                      resolved="com.android.support:support-core-ui:25.0.1"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-core-ui/25.0.1"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support:support-fragment:25.0.1"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-fragment/25.0.1/jars/classes.jar"
                      resolved="com.android.support:support-fragment:25.0.1"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-fragment/25.0.1"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support:support-vector-drawable:25.0.1"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-vector-drawable/25.0.1/jars/classes.jar"
                      resolved="com.android.support:support-vector-drawable:25.0.1"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support/support-vector-drawable/25.0.1"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support:animated-vector-drawable:25.0.1"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support/animated-vector-drawable/25.0.1/jars/classes.jar"
                      resolved="com.android.support:animated-vector-drawable:25.0.1"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support/animated-vector-drawable/25.0.1"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support.constraint:constraint-layout:1.0.0-beta3"
                      jars="＄ROOT/build/intermediates/exploded-aar/com.android.support.constraint/constraint-layout/1.0.0-beta3/jars/classes.jar"
                      resolved="com.android.support.constraint:constraint-layout:1.0.0-beta3"
                      folder="＄ROOT/build/intermediates/exploded-aar/com.android.support.constraint/constraint-layout/1.0.0-beta3"
                      manifest="AndroidManifest.xml"
                      resFolder="res"
                      assetsFolder="assets"
                      lintJar="lint.jar"
                      publicResources="public.txt"
                      symbolFile="R.txt"
                      externalAnnotations="annotations.zip"
                      proguardRules="proguard.pro"/>
                  <library
                      name="com.android.support:support-annotations:25.0.1"
                      jars="＄ROOT/caches/modules-2/files-2.1/com.android.support/support-annotations/25.0.1/9c6ef172e8de35fd8d4d8783e4821e57cdef7445/support-annotations-25.0.1.jar"
                      resolved="com.android.support:support-annotations:25.0.1"/>
                  <library
                      name="com.android.support.constraint:constraint-layout-solver:1.0.0-beta3"
                      jars="＄ROOT/caches/modules-2/files-2.1/com.android.support.constraint/constraint-layout-solver/1.0.0-beta3/9c6ef172e8de35fd8d4d8783e4821e57cdef7445/constraint-layout-solver-1.0.0-beta3.jar"
                      resolved="com.android.support.constraint:constraint-layout-solver:1.0.0-beta3"/>
                  <library
                      name="org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.0"
                      jars="＄ROOT/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-jdk7/1.3.0/9c6ef172e8de35fd8d4d8783e4821e57cdef7445/kotlin-stdlib-jdk7-1.3.0.jar"
                      resolved="org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.0"/>
                  <library
                      name="org.jetbrains.kotlin:kotlin-stdlib:1.3.0"
                      jars="＄ROOT/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/1.3.0/9c6ef172e8de35fd8d4d8783e4821e57cdef7445/kotlin-stdlib-1.3.0.jar"
                      resolved="org.jetbrains.kotlin:kotlin-stdlib:1.3.0"/>
                  <library
                      name="org.jetbrains.kotlin:kotlin-stdlib-common:1.3.0"
                      jars="＄ROOT/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-common/1.3.0/9c6ef172e8de35fd8d4d8783e4821e57cdef7445/kotlin-stdlib-common-1.3.0.jar"
                      resolved="org.jetbrains.kotlin:kotlin-stdlib-common:1.3.0"/>
                  <library
                      name="org.jetbrains:annotations:13.0"
                      jars="＄ROOT/caches/modules-2/files-2.1/org.jetbrains/annotations/13.0/9c6ef172e8de35fd8d4d8783e4821e57cdef7445/annotations-13.0.jar"
                      resolved="org.jetbrains:annotations:13.0"/>
                </libraries>
                """,
        "library_table-paidNormalRelease-testArtifact" to
          """
                <libraries>
                </libraries>
                """,
        "library_table-paidNormalRelease-androidTestArtifact" to
          """
                <libraries>
                </libraries>
                """,
      ),
    )
  }

  @Test
  fun testMissingTags() {
    tryParse(
      """
            <lint-module
                dir="root"
                name="test_project-build"
                type="APP"
                agpVersion="4.0.0-beta01"
                buildFolder="＄ROOT/build"
                javaSourceLevel="1.7"
                compileTarget="android-25">
            </lint-module>
            """,
      "Missing data at testfile.xml:11",
    )
  }

  @Test
  fun testMissingAttribute() {
    tryParse(
      """
            <lint-module
                dir="root"
                compileTarget="android-25">
            </lint-module>
            """,
      "Expected `name` attribute in <lint-module> tag at testfile.xml:4",
    )
  }

  @Test
  fun testUnexpectedTag() {
    tryParse(
      """
            <lint-module
                dir="root"
                name="test_project-build"
                type="APP"
                agpVersion="4.0.0-beta01"
                buildFolder="＄ROOT/build"
                javaSourceLevel="1.7"
                compileTarget="android-25">
                <foobar />
            </lint-module>
            """,
      "Unexpected tag `<foobar>` at testfile.xml:10",
    )
  }

  @Test
  fun testPathVariables() {
    val root = temporaryFolder.root
    fun String.cleanup() = replace(root.path, "＄ROOT").trim()
    val moduleFile = File(root, "module.xml")
    val folder1 = temporaryFolder.newFolder()
    val folder2 = temporaryFolder.newFolder()
    val file1 = File(folder1, "file1")
    file1.createNewFile()
    val file2 = File(folder2, "file2")
    file2.createNewFile()

    val pathVariables = PathVariables()
    pathVariables.add("SDK", folder1)
    pathVariables.add("GRADLE", folder2)

    LintModelSerializationFileAdapter(root, pathVariables).use { adapter ->
      assertEquals("module.xml", adapter.toPathString(moduleFile, root).cleanup())
      assertEquals("\$SDK/file1", adapter.toPathString(file1, root).portablePath())
      assertEquals("\$GRADLE/file2", adapter.toPathString(file2, root).portablePath())

      assertEquals(moduleFile, adapter.fromPathString("module.xml", root))
      assertEquals(file1, adapter.fromPathString("\$SDK/file1", root))
      assertEquals(file2, adapter.fromPathString("\$GRADLE/file2", root))
    }
  }

  /** Check that the relative paths in variants are resolved against the project directory. */
  @Test
  fun testLintModelSerializationFileAdapterRootHandling() {
    val temp = temporaryFolder.newFolder()
    val projectDirectory = temp.resolve("projectDir").createDirectories()
    projectDirectory
      .resolve("src/main/")
      .createDirectories()
      .resolve("AndroidManifest.xml")
      .writeText("Fake Android manifest")
    val buildDirectory = temp.resolve("buildDir").createDirectories()
    val modelsDir = buildDirectory.resolve("intermediates/lint-models").createDirectories()
    modelsDir
      .resolve("module.xml")
      .writeText(
        """<lint-module
                    format="1"
                    dir="${projectDirectory.absolutePath}"
                    name="test_project-build"
                    type="APP"
                    maven="com.android.tools.demo:test_project-build:"
                    agpVersion="4.0.0-beta01"
                    buildFolder="${buildDirectory.absolutePath}"
                    javaSourceLevel="1.7"
                    compileTarget="android-25"
                    neverShrinking="true">
                  <lintOptions />
                  <variant name="debug"/>
                </lint-module>"""
      )
    modelsDir
      .resolve("debug.xml")
      .writeText(
        """<variant
                    name="debug"
                    minSdkVersion="5"
                    targetSdkVersion="16"
                    debuggable="true">
                  <buildFeatures />
                  <sourceProviders>
                    <sourceProvider
                        manifest="src/main/AndroidManifest.xml"
                        javaDirectories="src/main/java:src/main/kotlin"
                        resDirectories="src/main/res"
                        assetsDirectories="src/main/assets"/>
                  </sourceProviders>
                  <artifact
                      classOutputs="${buildDirectory.absolutePath}/intermediates/javac/freeBetaDebug/classes:${buildDirectory.absolutePath}/intermediates/kotlin-classes/freeBetaDebug"
                      type="MAIN"
                      applicationId="com.android.tools.test">
                  </artifact>
                </variant>"""
      )

    val module = LintModelSerialization.readModule(source = modelsDir, readDependencies = false)

    val manifestFile = module.defaultVariant()!!.sourceProviders.first().manifestFiles.first()
    assertWithMessage(
        "Source file should be resolved relative to the project directory, not the source directory"
      )
      .about(PathSubject.paths())
      .that(manifestFile.toPath())
      .hasContents("Fake Android manifest")
  }

  @Test
  fun testLintModelSerializationManifest() {
    val temp = temporaryFolder.newFolder()
    val projectDirectory = temp.resolve("projectDir").createDirectories()
    val buildDirectory = temp.resolve("buildDir").createDirectories()
    val modelsDir = buildDirectory.resolve("intermediates/lint-models").createDirectories()
    val mergedManifest =
      buildDirectory
        .resolve("intermediates/merged_manifest/debug")
        .createDirectories()
        .resolve("AndroidManifest.xml")
        .apply { writeText("Merged manifest") }
    val mergeReport =
      buildDirectory
        .resolve("outputs/reports/manifest/debug")
        .createDirectories()
        .resolve("ManifestMergeReport.xml")
        .apply { writeText("Manifest merge report") }
    modelsDir
      .resolve("module.xml")
      .writeText(
        """<lint-module
                    format="1"
                    dir="${projectDirectory.absolutePath}"
                    name="test_project-build"
                    type="APP"
                    maven="com.android.tools.demo:test_project-build:"
                    agpVersion="4.0.0-beta01"
                    buildFolder="${buildDirectory.absolutePath}"
                    javaSourceLevel="1.7"
                    compileTarget="android-25"
                    neverShrinking="true">
                  <lintOptions />
                  <variant name="debug"/>
                </lint-module>"""
      )
    val debugXml =
      """<variant
                    name="debug"
                    minSdkVersion="5"
                    targetSdkVersion="16"
                    debuggable="true"
                    mergedManifest="${mergedManifest.absolutePath}"
                    manifestMergeReport="${mergeReport.absolutePath}">
                  <buildFeatures />
                  <artifact
                    type="MAIN"
                    applicationId="com.android.tools.test">
                  </artifact>
                </variant>"""
    modelsDir.resolve("debug.xml").writeText(debugXml)

    val module = LintModelSerialization.readModule(source = modelsDir, readDependencies = false)

    val debugVariant = module.defaultVariant()!!

    assertWithMessage("Merged manifest is read correctly")
      .about(PathSubject.paths())
      .that(debugVariant.mergedManifest?.toPath())
      .hasContents("Merged manifest")

    assertWithMessage("Merged manifest is read correctly")
      .about(PathSubject.paths())
      .that(debugVariant.manifestMergeReport?.toPath())
      .hasContents("Manifest merge report")
  }

  /**
   * Check that special references to output files "stderr" and "stdout" are not turned into actual
   * files. Regression test for https://issuetracker.google.com/174480831.
   */
  @Test
  fun testSpecialHandlingOfStderrAndStdout() {
    val temp = temporaryFolder.newFolder()
    val projectDirectory = temp.resolve("projectDir").createDirectories()
    val buildDirectory = temp.resolve("buildDir").createDirectories()
    val modelsDir = buildDirectory.resolve("intermediates/lint-models").createDirectories()
    modelsDir
      .resolve("module.xml")
      .writeText(
        """
                <lint-module
                    format="1"
                    dir="${projectDirectory.absolutePath}"
                    name="test_project-build"
                    type="APP"
                    maven="com.android.tools.demo:test_project-build:"
                    agpVersion="4.0.0-beta01"
                    buildFolder="${buildDirectory.absolutePath}"
                    javaSourceLevel="1.7"
                    compileTarget="android-25"
                    neverShrinking="true">
                    <lintOptions
                      abortOnError="true"
                      absolutePaths="true"
                      checkReleaseBuilds="true"
                      explainIssues="true"
                      textReport="true"
                      textOutput="stderr"
                      xmlOutput="stdout"
                      htmlOutput="relative"
                      htmlReport="true"
                      xmlReport="true"/>
                    <variant name="debug"/>
                </lint-module>
                """
          .trimIndent()
      )
    modelsDir
      .resolve("debug.xml")
      .writeText(
        """
                <variant
                    name="debug"
                    minSdkVersion="5"
                    targetSdkVersion="16"
                    debuggable="true">
                  <buildFeatures />
                  <sourceProviders>
                    <sourceProvider
                        manifest="src/main/AndroidManifest.xml"
                        javaDirectories="src/main/java:src/main/kotlin"
                        resDirectories="src/main/res"
                        assetsDirectories="src/main/assets"/>
                  </sourceProviders>
                  <artifact
                      classOutputs="${buildDirectory.absolutePath}/intermediates/javac/freeBetaDebug/classes:${buildDirectory.absolutePath}/intermediates/kotlin-classes/freeBetaDebug"
                      type="MAIN"
                      applicationId="com.android.tools.test">
                  </artifact>
                </variant>
                """
          .trimIndent()
      )

    val module = LintModelSerialization.readModule(source = modelsDir, readDependencies = false)

    val lintOptions = module.lintOptions
    val textOutput = lintOptions.textOutput
    assertEquals("stderr", textOutput!!.path)
    assertEquals("stdout", lintOptions.xmlOutput!!.path)
    assertEquals("relative", lintOptions.htmlOutput!!.name)
    assertTrue(lintOptions.htmlOutput!!.isAbsolute)
  }

  @Test
  fun testLintModelSerializationWithPartialResultsDir() {
    val temp = temporaryFolder.newFolder()
    val projectDirectory = temp.resolve("projectDir").createDirectories()
    val buildDirectory = temp.resolve("buildDir").createDirectories()
    val modelsDir = buildDirectory.resolve("intermediates/lint-models").createDirectories()
    val partialResultsDir = buildDirectory.resolve("intermediates/lint_partial_results/debug/out")
    modelsDir
      .resolve("module.xml")
      .writeText(
        """<lint-module
                    format="1"
                    dir="${projectDirectory.absolutePath}"
                    name="test_project-build"
                    type="APP"
                    maven="com.android.tools.demo:test_project-build:"
                    agpVersion="4.0.0-beta01"
                    buildFolder="${buildDirectory.absolutePath}"
                    javaSourceLevel="1.7"
                    compileTarget="android-25"
                    neverShrinking="true">
                  <lintOptions />
                  <variant name="debug"/>
                </lint-module>"""
      )
    modelsDir
      .resolve("debug.xml")
      .writeText(
        """<variant
                    name="debug"
                    minSdkVersion="5"
                    targetSdkVersion="16"
                    debuggable="true"
                    partialResultsDir="${partialResultsDir.absolutePath}">
                  <buildFeatures />
                  <artifact
                    type="MAIN"
                    applicationId="com.android.tools.test">
                  </artifact>
                </variant>"""
      )

    val module = LintModelSerialization.readModule(modelsDir, readDependencies = false)

    val debugVariant1 = module.defaultVariant()!!

    assertWithMessage("partialResultsDir is read correctly")
      .about(PathSubject.paths())
      .that(debugVariant1.partialResultsDir?.toPath())
      .isEqualTo(partialResultsDir)

    // Now delete the original model files, serialize the model, and read it back to check that
    // partialResultsDir
    // is written correctly.
    modelsDir.listFiles()?.forEach { Files.delete(it.toPath()) }

    LintModelSerialization.writeModule(
      module,
      modelsDir,
      listOf(debugVariant1),
      writeDependencies = false,
    )

    val debugVariant2 =
      LintModelSerialization.readModule(modelsDir, readDependencies = false).defaultVariant()!!

    assertWithMessage("partialResultsDir is written and read correctly")
      .about(PathSubject.paths())
      .that(debugVariant2.partialResultsDir?.toPath())
      .isEqualTo(partialResultsDir)
  }

  @Test
  fun testCodenameApiLevelWithHeuristics() {
    val temp = temporaryFolder.newFolder()
    val projectDirectory = temp.resolve("projectDir").createDirectories()
    val buildDirectory = temp.resolve("buildDir").createDirectories()
    val modelsDir = buildDirectory.resolve("intermediates/lint-models").createDirectories()
    val mergedManifest =
      buildDirectory
        .resolve("intermediates/merged_manifest/debug")
        .createDirectories()
        .resolve("AndroidManifest.xml")
        .apply { writeText("Merged manifest") }
    val mergeReport =
      buildDirectory
        .resolve("outputs/reports/manifest/debug")
        .createDirectories()
        .resolve("ManifestMergeReport.xml")
        .apply { writeText("Manifest merge report") }
    modelsDir
      .resolve("module.xml")
      .writeText(
        """<lint-module
                    format="1"
                    dir="${projectDirectory.absolutePath}"
                    name="test_project-build"
                    type="APP"
                    maven="com.android.tools.demo:test_project-build:"
                    agpVersion="4.0.0-beta01"
                    buildFolder="${buildDirectory.absolutePath}"
                    javaSourceLevel="1.7"
                    compileTarget="android-25"
                    neverShrinking="true">
                  <lintOptions />
                  <variant name="debug"/>
                </lint-module>"""
      )
    val currentPreviewFirstLetter = SdkVersionInfo.getBuildCode(HIGHEST_KNOWN_API)!!.first()
    val futurePreviewPlus1 =
      AndroidVersion(HIGHEST_KNOWN_API, (currentPreviewFirstLetter + 1) + "CodeName")
    val futurePreviewPlus2 =
      AndroidVersion(HIGHEST_KNOWN_API + 1, (currentPreviewFirstLetter + 2) + "CodeName")
    val debugXml =
      """<variant
                    name="debug"
                    minSdkVersion="${futurePreviewPlus1.codename}"
                    targetSdkVersion="${futurePreviewPlus2.codename}"
                    debuggable="true"
                    mergedManifest="${mergedManifest.absolutePath}"
                    manifestMergeReport="${mergeReport.absolutePath}">
                  <buildFeatures />
                  <artifact
                    type="MAIN"
                    applicationId="com.android.tools.test">
                  </artifact>
                </variant>"""
    modelsDir.resolve("debug.xml").writeText(debugXml)

    val module = LintModelSerialization.readModule(source = modelsDir, readDependencies = false)

    val debugVariant = module.defaultVariant()!!

    val minSdkVersion = debugVariant.minSdkVersion!!
    assertEquals(futurePreviewPlus1, minSdkVersion)
    val targetSdkVersion = debugVariant.targetSdkVersion!!
    assertEquals(futurePreviewPlus2, targetSdkVersion)
  }

  @Test
  fun testCodenameApiLevelWithAndroidSdk() {
    val codename = "Whatever"
    val apiLevel = 42

    val temp = temporaryFolder.newFolder()
    val projectDirectory = temp.resolve("projectDir").createDirectories()
    val buildDirectory = temp.resolve("buildDir").createDirectories()
    val modelsDir = buildDirectory.resolve("intermediates/lint-models").createDirectories()
    val mergedManifest =
      buildDirectory
        .resolve("intermediates/merged_manifest/debug")
        .createDirectories()
        .resolve("AndroidManifest.xml")
        .apply { writeText("Merged manifest") }
    val mergeReport =
      buildDirectory
        .resolve("outputs/reports/manifest/debug")
        .createDirectories()
        .resolve("ManifestMergeReport.xml")
        .apply { writeText("Manifest merge report") }
    modelsDir
      .resolve("module.xml")
      .writeText(
        """<lint-module
                    format="1"
                    dir="${projectDirectory.absolutePath}"
                    name="test_project-build"
                    type="APP"
                    maven="com.android.tools.demo:test_project-build:"
                    agpVersion="4.0.0-beta01"
                    buildFolder="${buildDirectory.absolutePath}"
                    javaSourceLevel="1.7"
                    compileTarget="android-25"
                    neverShrinking="true">
                  <lintOptions />
                  <variant name="debug"/>
                </lint-module>"""
      )
    val debugXml =
      """<variant
                    name="debug"
                    minSdkVersion="$codename"
                    targetSdkVersion="$codename"
                    debuggable="true"
                    mergedManifest="${mergedManifest.absolutePath}"
                    manifestMergeReport="${mergeReport.absolutePath}">
                  <buildFeatures />
                  <artifact
                    type="MAIN"
                    applicationId="com.android.tools.test">
                  </artifact>
                </variant>"""
    modelsDir.resolve("debug.xml").writeText(debugXml)

    val sdkHome = temporaryFolder.newFolder("sdk")
    val pkg = File(sdkHome, "platforms/android-$codename/package.xml")
    pkg.parentFile.mkdirs()
    pkg.writeText(
      // language=XML
      """
            <ns2:repository xmlns:ns2="http://schemas.android.com/repository/android/common/02" xmlns:ns11="http://schemas.android.com/sdk/android/repo/repository2/03">
                <license id="license-CF56B611" type="text"/>
                <localPackage path="platforms;android-$codename" obsolete="false">
                    <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns11:platformDetailsType">
                        <api-level>$apiLevel</api-level>
                        <codename>$codename</codename>
                        <extension-level>1</extension-level>
                        <base-extension>true</base-extension>
                    </type-details>
                    <revision>
                        <major>3</major>
                    </revision>
                    <display-name>Android SDK Platform $codename, rev 3</display-name>
                    <uses-license ref="license-CF56B611"/>
                </localPackage>
            </ns2:repository>
            """
        .trimIndent()
    )

    val module =
      LintModelSerialization.readModule(
        source = modelsDir,
        pathVariables = PathVariables().apply { add("ANDROID_HOME", sdkHome) },
        readDependencies = false,
      )

    val debugVariant = module.defaultVariant()
    val minSdkVersion = debugVariant?.minSdkVersion!!
    assertEquals("API $apiLevel, $codename preview", minSdkVersion.toString())
  }

  // ----------------------------------------------------------------------------------
  // Test infrastructure below this line
  // ----------------------------------------------------------------------------------

  private fun tryParse(@Language("XML") xml: String, expectedErrors: String? = null) {
    try {
      val reader = StringReader(xml)
      LintModelSerialization.readModule(
        LintModelSerializationStringAdapter(reader = { _, _, _ -> reader })
      )
      if (expectedErrors != null) {
        fail("Expected failure, got valid module instead")
      }
    } catch (error: Throwable) {
      val message = error.message ?: error.toString()
      if (expectedErrors != null) {
        assertThat(expectedErrors).isEqualTo(message)
      } else {
        fail(message)
      }
    }
  }

  private fun checkSerialization(mocker: GradleModelMocker, expectedXml: Map<String, String>) {
    val path = mocker.projectDir.path
    fun String.cleanup() = replace(path, "＄ROOT").dos2unix().trim()

    // Test lint model stuff
    val module = mocker.getLintModule()
    val xml = writeModule(module)

    // Make sure all the generated XML is valid
    for ((_, s) in xml) {
      assertValidXml(s)
    }
    val remainingExpectedXml = expectedXml.toMutableMap()

    for (fileType in TargetFile.values()) {
      for (variant in module.variants) {
        for (artifactName in
          listOf("artifact", "testArtifact", "androidTestArtifact", "testFixturesArtifact")) {
          val mapKey = getMapKey(fileType, variant.name, artifactName)
          val writtenXml: String = xml[mapKey] ?: continue
          assertValidXml(writtenXml)
          val expected = remainingExpectedXml.remove(mapKey) ?: continue
          try {
            assertEquals(expected.trimIndent().trim(), writtenXml.cleanup())
            assertThat(writtenXml.cleanup()).isEqualTo(expected.trimIndent().trim())
          } catch (e: Exception) {
            println("FAIL: ${e.message}")
            throw e
          }
        }
      }
    }
    assertThat(remainingExpectedXml).isEmpty()

    val newModule =
      LintModelSerialization.readModule(
        LintModelSerializationStringAdapter(
          reader = { target, variantName, artifact ->
            val contents = xml[getMapKey(target, variantName, artifact)]!!
            StringReader(contents)
          }
        )
      )
    val newXml = writeModule(newModule)
    for ((key, contents) in xml) {
      assertEquals(
        "XML parsed and written back out does not match original for file " + key,
        contents,
        newXml[key],
      )
    }
  }

  private fun assertValidXml(xml: String) {
    try {
      val document = XmlUtils.parseDocument(xml, false)
      assertNotNull(document)
      assertNoTextNodes(document.documentElement)
    } catch (e: IOException) {
      fail(e.message)
    } catch (e: SAXException) {
      fail(e.message)
    }
  }

  private fun getMapKey(
    target: TargetFile,
    variantName: String = "",
    artifactName: String = "",
  ): String {
    //noinspection DefaultLocale
    val key = StringBuilder(target.name.lowercase())
    if (variantName.isNotEmpty() && target != TargetFile.MODULE) {
      key.append("-")
      key.append(variantName)
      if (
        artifactName.isNotEmpty() &&
          (target == TargetFile.DEPENDENCIES || target == TargetFile.LIBRARY_TABLE)
      ) {
        key.append("-")
        key.append(artifactName)
      }
    }

    return key.toString()
  }

  private fun writeModule(module: LintModelModule): Map<String, String> {
    val map = mutableMapOf<String, StringWriter>()
    LintModelSerialization.writeModule(
      module,
      LintModelSerializationStringAdapter(
        writer = { target, variantName, artifactName ->
          val key = getMapKey(target, variantName, artifactName)
          map[key] ?: StringWriter().also { map[key] = it }
        }
      ),
    )
    return map.mapValues { it.value.toString() }
  }

  private fun writeVariant(variant: LintModelVariant): String {
    val writer = StringWriter()
    LintModelSerialization.writeVariant(
      variant,
      LintModelSerializationStringAdapter(writer = { _, _, _ -> writer }),
    )
    return writer.toString()
  }

  private class LintModelSerializationStringAdapter(
    override val root: File? = null,
    private val reader: (TargetFile, String, String) -> Reader = { _, _, _ ->
      StringReader("<error>")
    },
    private val writer: (TargetFile, String, String) -> Writer = { _, _, _ -> StringWriter() },
    override val pathVariables: PathVariables = PathVariables(),
  ) : LintModelSerialization.LintModelSerializationAdapter {
    override fun file(target: TargetFile, variantName: String, artifactName: String): File {
      return if (variantName.isNotEmpty()) File("variant-$variantName.xml")
      else File("testfile.xml")
    }

    override fun getReader(target: TargetFile, variantName: String, artifactName: String) =
      reader(target, variantName, artifactName)

    override fun getWriter(target: TargetFile, variantName: String, artifactName: String) =
      writer(target, variantName, artifactName)
  }

  private fun assertNoTextNodes(element: Element) {
    var curr = element.firstChild
    while (curr != null) {
      val nodeType = curr.nodeType
      if (nodeType == Node.ELEMENT_NODE) {
        assertNoTextNodes(curr as Element)
      } else if (nodeType == Node.TEXT_NODE) {
        val text = curr.nodeValue
        if (text.isNotBlank()) {
          fail("Found unexpected text " + text.trim { it <= ' ' } + " in the document")
        }
      }
      curr = curr.nextSibling
    }
  }

  private fun File.createDirectories(): File {
    Files.createDirectories(toPath())
    return this
  }
}
