load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.android-annotations",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.android-annotations.iml"],
    jvm_target = "8",
    lint_baseline = "lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
    ],
)

maven_library(
    name = "annotations",
    srcs = glob(["src/main/java/**"]),
    coordinates = "com.android.tools:annotations",
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/annotations",
    version = BASE_VERSION,
    visibility = ["//visibility:public"],
)
