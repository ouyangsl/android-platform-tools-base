load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.layoutlib-api",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.layoutlib-api.iml"],
    jvm_target = "8",
    lint_baseline = "lint_baseline.xml",
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/adt/idea/.idea/libraries:kxml2",
        "//tools/base/annotations:studio.android.sdktools.android-annotations",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:kxml2",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
    ],
)

maven_library(
    name = "tools.layoutlib-api",
    srcs = glob([
        "src/main/java/**/*.java",
    ]),
    coordinates = "com.android.tools.layoutlib:layoutlib-api",
    description = "Library to use the rendering library for Android layouts: layoutlib",
    notice = "NOTICE",
    pom_name = "Android Tools layoutlib-api",
    resource_strip_prefix = "tools/base/layoutlib-api",
    version = BASE_VERSION,
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:org.jetbrains.annotations",
    ],
)

coverage_java_test(
    name = "tools.tests",
    srcs = glob(["src/test/java/**"]),
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = ["//tools/base/testutils:tools.testutils"],
    deps = [
        ":tools.layoutlib-api",
        "//tools/base/common:tools.common",
        "@maven//:com.google.guava.guava",
        "@maven//:junit.junit",
    ],
)
