load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.device-provisioner",
    srcs = ["src/main"],
    iml_files = ["android.sdktools.device-provisioner.iml"],
    test_srcs = ["src/test"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/adblib:studio.android.sdktools.adblib[module]",
        "//tools/base/adblib-tools:studio.android.sdktools.adblib.tools[module]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module]",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/analytics-library/shared:analytics-shared[module]",
        "//tools/adt/idea/.idea/libraries:emulator-proto",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
    ],
)
