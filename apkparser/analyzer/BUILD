load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:coverage.bzl", "coverage_java_library", "coverage_java_test")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.analyzer",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.analyzer.iml"],
    lint_baseline = "lint_baseline.xml",
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//prebuilts/tools/common/archive-patcher:explainer",
        "//prebuilts/tools/common/archive-patcher:generator",
        "//prebuilts/tools/common/archive-patcher:shared",
        "//tools/adt/idea/.idea/libraries:google-dexlib2",
        "//tools/adt/idea/.idea/libraries:google-baksmali",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:jimfs[test]",
        "//tools/base/apkparser:studio.android.sdktools.binary-resources[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/base/zipflinger:studio.android.sdktools.zipflinger[module]",
    ],
)

coverage_java_library(
    name = "tools.analyzer",
    srcs = glob(["src/main/java/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/tools/common/archive-patcher:explainer",
        "//prebuilts/tools/common/archive-patcher:generator",
        "//prebuilts/tools/common/archive-patcher:shared",
        "//tools/base/annotations",
        "//tools/base/apkparser:tools.binary-resources",
        "//tools/base/common:tools.common",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/zipflinger",
        "@maven//:com.android.tools.smali.smali-baksmali",
        "@maven//:com.android.tools.smali.smali-dexlib2",
        "@maven//:com.google.guava.guava",
    ],
)

# Sepeare for now as the archive-patcher jars are not shipped to maven.
maven_library(
    name = "tools.analyzer.maven",
    srcs = [],
    bundled_deps = [
        ":tools.analyzer",
    ],
    coordinates = "com.android.tools.apkparser:apkanalyzer",
    description = "Library for parsing and analyzing information from APK files",
    notice = "NOTICE",
    pom_name = "Apk Analyzer library",
    resource_strip_prefix = "tools/base/apkparser/analyzer",
    version = BASE_VERSION,
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/apkparser:tools.binary-resources",
        "//tools/base/common:tools.common",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "@maven//:com.android.tools.smali.smali-baksmali",
        "@maven//:com.android.tools.smali.smali-dexlib2",
        "@maven//:com.google.guava.guava",
    ],
)

coverage_java_test(
    name = "tools.tests",
    srcs = glob(["src/test/java/**"]),
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.analyzer",
        "//tools/base/annotations",
        "//tools/base/apkparser:tools.binary-resources",
        "//tools/base/common:tools.common",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.android.tools.smali.smali-dexlib2",
        "@maven//:com.google.jimfs.jimfs",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
    ],
)
