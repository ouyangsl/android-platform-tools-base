load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/common:version.bzl", "BUILD_VERSION")

maven_library(
    name = "aaptcompiler",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    coordinates = "com.android.tools.build:aaptcompiler",
    lint_baseline = "lint_baseline.xml",
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/build-system/aaptcompiler",
    version = BUILD_VERSION,
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/build-system/aapt2-proto",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.protobuf.protobuf-java",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
    ],
)

kotlin_test(
    name = "tests",
    srcs = glob([
        "src/test/java/**/*.kt",
    ]),
    friends = [":aaptcompiler"],
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    lint_baseline = "lint_baseline_tests.xml",
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":aaptcompiler",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
    ],
)

filegroup(
    name = "agp_gradle_build_files",
    srcs = glob([
        "src/main/**/*.java",
        "src/main/**/*.kt",
        "src/main/resources/**",
    ]) + [
        "NOTICE",
        "build.gradle",
    ],
    visibility = ["//tools/base/build-system:__pkg__"],
)
