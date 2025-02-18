load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_library", "maven_repository")
load("//tools/base/preview/screenshot:version.bzl", "SCREENSHOT_TEST_PLUGIN_VERSION")
load(":compose-preview-renderer-maven-utils.bzl", "merge_deps")

kotlin_library(
    name = "tools.compose-preview-renderer",
    srcs = glob([
        "src/**/*.kt",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/adt/idea/preview-elements:tools.preview-elements",
        "//tools/base/multipreview-asm:tools.compose-preview-detector",
        "//tools/base/standalone-render/compose-cli-serialize:tools.compose-preview-renderer-model",
        "//tools/base/standalone-render/lib:tools.standalone-render.lib",
    ],
)

merge_deps(
    name = "bundled_deps",
    srcs = [
        ":tools.compose-preview-renderer",
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//tools/adt/idea/compose-designer:ui-animation-tooling-internal",
        "//tools/adt/idea/layoutlib:tools.layoutlib",
        "//tools/adt/idea/layoutlib-loader:tools.layoutlib-loader",
        "//tools/adt/idea/preview-elements:tools.preview-elements",
        "//tools/adt/idea/render-resources:tools.render-resources",
        "//tools/adt/idea/rendering:tools.rendering",
        "//tools/analytics-library/crash:tools.analytics-crash",
        "//tools/base/apkparser:tools.binary-resources",
        "//tools/base/apkparser/analyzer:tools.analyzer",
        "//tools/base/common:tools.common",
        "//tools/base/device_validator:tools.dvlib",
        "//tools/base/environment-services:tools.environment-services",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/multipreview-asm:tools.compose-preview-detector",
        "//tools/base/repository:tools.repository",
        "//tools/base/resource-repository:tools.resource-repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/standalone-render/compose-cli-serialize:tools.compose-preview-renderer-model",
        "//tools/base/standalone-render/lib:tools.standalone-render.lib",
        "//tools/base/zipflinger",
        "@maven//:com.google.code.gson.gson",
        "@maven//:com.google.guava.guava",
        "@maven//:jakarta.xml.bind.jakarta.xml.bind-api",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:org.glassfish.jaxb.jaxb-runtime",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
        "@maven//:org.ow2.asm.asm",
        "@maven//:org.ow2.asm.asm-commons",
    ],
)

java_import(
    name = "bundled_deps_java_library",
    jars = ["bundled_deps.jar"],
)

maven_library(
    name = "compose-preview-renderer",
    srcs = [],
    bundled_deps = [":bundled_deps_java_library"],
    coordinates = "com.android.tools.compose:compose-preview-renderer",
    description = "Compose screenshot rendering tools",
    module_name = "compose-preview-renderer",
    version = SCREENSHOT_TEST_PLUGIN_VERSION,
    visibility = ["//visibility:public"],
)

maven_repository(
    name = "compose_project_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:androidx.activity.activity-compose_1.8.0",
        "@maven//:androidx.annotation.annotation-jvm_1.6.0",
        "@maven//:androidx.collection.collection-ktx_1.4.0",
        "@maven//:androidx.compose.animation.animation_1.5.0",
        "@maven//:androidx.compose.compiler.compiler_1.5.1",
        "@maven//:androidx.compose.compose-bom_2024.09.00",
        "@maven//:androidx.compose.foundation.foundation-layout_1.5.0",
        "@maven//:androidx.compose.foundation.foundation_1.5.0",
        "@maven//:androidx.compose.material.material-icons-core_1.5.0",
        "@maven//:androidx.compose.material.material-ripple_1.5.0",
        "@maven//:androidx.compose.material.material_1.7.0",  # Compose BOM 2024.09.00
        "@maven//:androidx.compose.material3.material3_1.3.0",  # Compose BOM 2024.09.09
        "@maven//:androidx.compose.ui.ui-graphics_1.5.0",
        "@maven//:androidx.compose.ui.ui-test-manifest_1.7.0",  # Compose BOM 2024.09.00
        "@maven//:androidx.compose.ui.ui-tooling-preview_1.5.0",
        "@maven//:androidx.compose.ui.ui-tooling_1.7.0",  # Compose BOM 2024.09.00
        "@maven//:androidx.compose.ui.ui_1.5.0",
        "@maven//:androidx.core.core-ktx_1.12.0",
        "@maven//:androidx.lifecycle.lifecycle-common-java8_2.8.3",
        "@maven//:androidx.lifecycle.lifecycle-process_2.8.3",
        "@maven//:androidx.lifecycle.lifecycle-runtime-ktx_2.8.3",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel-ktx_2.8.3",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel-savedstate_2.8.3",
        "@maven//:androidx.lifecycle.lifecycle-viewmodel_2.8.3",
        "@maven//:com.android.application.com.android.application.gradle.plugin_8.2.0",
        "@maven//:com.android.tools.build.aapt2_8.2.0-10154469",
        "@maven//:org.jetbrains.kotlin.android.org.jetbrains.kotlin.android.gradle.plugin_1.9.0",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.9.0",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.6.10",  # This is for org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.0
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.8.20",
        "@maven//:org.jetbrains.kotlin.kotlin-script-runtime_1.9.0",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.8.20",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib_1.8.20",
    ],
)

# Performance tests for Compose CLI render
kotlin_test(
    name = "perfgate-compose-cli-render-tests",
    timeout = "eternal",
    srcs = glob(["perf/testSrc/**/*.kt"]),
    # keep sorted
    data = [
        ":compose-preview-renderer",
        ":compose_project_deps",
        "//prebuilts/studio/layoutlib:build.prop",
        "//prebuilts/studio/layoutlib/data",
        "//prebuilts/studio/layoutlib/data:framework_res.jar",
        "//prebuilts/studio/layoutlib/data:layoutlib",
        "//prebuilts/studio/layoutlib/data:native_libs",
        "//prebuilts/studio/layoutlib/data/fonts",
        "//prebuilts/studio/layoutlib/data/icu",
        "//prebuilts/studio/layoutlib/data/keyboards",
        "//prebuilts/studio/layoutlib/data/overlays",
        "//prebuilts/studio/layoutlib/data/res",
        "//prebuilts/studio/sdk:build-tools/34.0.0",
        "//prebuilts/studio/sdk:platforms/android-34",
        "//tools/base/build-system:gradle-distrib-8.2",  # This is actually "tools/external/gradle",
        "//tools/base/standalone-render/compose-cli/testData",
    ],
    tags = [
        "ci:perfgate-linux",
        "noci:studio-linux",
        "noci:studio-win",
    ],
    test_class = "com.android.tools.render.compose.PerfMainTest",
    deps = [
        ":tools.compose-preview-renderer",
        "//tools/base/perf-logger:studio.perf-logger_testlib",
        "//tools/base/standalone-render/compose-cli-serialize:tools.compose-preview-renderer-model",
        "//tools/base/testutils:tools.testutils",
        "@maven//:junit.junit",
    ],
)
