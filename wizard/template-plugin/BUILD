load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "studio.intellij.android.wizardTemplate.plugin",
    srcs = ["src"],
    iml_files = ["intellij.android.wizardTemplate.plugin.iml"],
    lint_baseline = "lint_baseline.xml",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
    ],
)
