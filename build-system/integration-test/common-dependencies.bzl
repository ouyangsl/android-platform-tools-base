"""
This module contains maven deps which are common between integration tests
and can be combined together
"""

# KGP version used in AGP tests
# when editing this consider making a copy below so that our recipe tests can use any versions of Kotlin
KGP_VERSION_FOR_TESTS = "2.0.20-RC2"

# KSP version used in AGP tests
KSP_VERSION_FOR_TESTS = "2.0.20-RC2-1.0.24"

# KGP dependencies used in AGP tests
KGP_FOR_TESTS = [
    "@maven//:org.jetbrains.kotlin.android.org.jetbrains.kotlin.android.gradle.plugin_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.compose-compiler-gradle-plugin_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.jvm.org.jetbrains.kotlin.jvm.gradle.plugin_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kapt.org.jetbrains.kotlin.kapt.gradle.plugin_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-android-extensions_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-android-extensions-runtime_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-annotation-processing-gradle_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-build-tools-impl_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-compiler_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-compose-compiler-plugin-embeddable_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-reflect_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-script-runtime_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-stdlib-common_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_" + KGP_VERSION_FOR_TESTS,
    "@maven//:org.jetbrains.kotlin.plugin.compose.org.jetbrains.kotlin.plugin.compose.gradle.plugin_" + KGP_VERSION_FOR_TESTS,
]

# Previous versions of KGP. This is used for Gradle recipe testing
KGP_1_9_22 = [
    "@maven//:org.jetbrains.kotlin.kapt.org.jetbrains.kotlin.kapt.gradle.plugin_1.9.22",
    "@maven//:org.jetbrains.kotlin.android.org.jetbrains.kotlin.android.gradle.plugin_1.9.22",
    "@maven//:org.jetbrains.kotlin.jvm.org.jetbrains.kotlin.jvm.gradle.plugin_1.9.22",
    "@maven//:org.jetbrains.kotlin.kotlin-android-extensions-runtime_1.9.22",
    "@maven//:org.jetbrains.kotlin.kotlin-compiler_1.9.22",
    "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.9.22",
    "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.9.22",
    "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.9.22",
    "@maven//:org.jetbrains.kotlin.kotlin-script-runtime_1.9.22",
    "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.9.22",
    "@maven//:org.jetbrains.kotlin.kotlin-annotation-processing-gradle_1.9.22",
    "@maven//:org.jetbrains.kotlin.kotlin-stdlib-common_1.9.22",
]

KGP_1_8_10 = [
    "@maven//:org.jetbrains.kotlin.android.org.jetbrains.kotlin.android.gradle.plugin_1.8.10",
    "@maven//:org.jetbrains.kotlin.jvm.org.jetbrains.kotlin.jvm.gradle.plugin_1.8.10",
    "@maven//:org.jetbrains.kotlin.kotlin-android-extensions-runtime_1.8.10",
    "@maven//:org.jetbrains.kotlin.kotlin-compiler_1.8.10",
    "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.8.10",
    "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.8.10",
    "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.8.10",
    "@maven//:org.jetbrains.kotlin.kotlin-script-runtime_1.8.10",
    "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.8.10",
    "@maven//:org.jetbrains.kotlin.kotlin-annotation-processing-gradle_1.8.10",
    "@maven//:org.jetbrains.kotlin.kotlin-stdlib-common_1.8.10",
]
