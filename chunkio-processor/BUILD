load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.chunkio-processor",
    srcs = ["src"],
    iml_files = ["android.sdktools.chunkio-processor.iml"],
    jvm_target = "8",
    lint_baseline = "lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/chunkio:studio.android.sdktools.chunkio[module]",
    ],
)
