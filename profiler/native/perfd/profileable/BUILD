load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary")

cc_library(
    name = "profileable_detector",
    srcs = ["profileable_detector.cc"],
    hdrs = ["profileable_detector.h"],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/profiler/native/perfd/common:trace_manager",
        "//tools/base/transport/native/daemon",
        "//tools/base/transport/native/utils",
    ],
)

cc_test(
    name = "profileable_detector_test",
    srcs = [
        "profileable_detector_test.cc",
    ],
    copts = [
        "-Itools/base/profiler/native",
    ],
    linkstatic = 1,
    deps = [
        ":profileable_detector",
        "//tools/base/transport/native/utils",
        "@googletest//:gtest_main",
    ],
)
