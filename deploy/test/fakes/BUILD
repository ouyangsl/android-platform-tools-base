load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "select_android")

cc_library(
    name = "fakes_lib",
    srcs = [
        "fake_agent.cc",
    ],
    hdrs = [
        "fake_agent.h",
    ],
    copts = ANDROID_COPTS,
    linkopts = ANDROID_LINKOPTS,
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/deploy/common:common_lib",
        "//tools/base/deploy/common:log",
        "//tools/base/deploy/proto:cc_proto",
    ],
)
