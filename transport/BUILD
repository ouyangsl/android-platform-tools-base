load("//tools/adt/idea/studio:studio.bzl", "studio_data")
load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary")

cc_binary(
    name = "transport_main",
    visibility = ["//visibility:public"],
    deps = [":transport_main_lib"],
)

cc_library(
    name = "transport_main_lib",
    srcs = ["native/main/transport.cc"],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
        "-Itools/base/echo-sample",
        "-Itools/base/dynamic-layout-inspector/foreground-process-detection/native",
    ],
    linkopts = ANDROID_LINKOPTS + [
        "-ldl",  # Needed for dladdr referenced in TermincationService
    ],
    target_compatible_with = select({
        "@platforms//os:windows": ["@platforms//:incompatible"],
        "//conditions:default": [],
    }),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/dynamic-layout-inspector/foreground-process-detection:foreground_process_tracker",
        "//tools/base/echo-sample:echo",
        "//tools/base/profiler/native/perfd",
        "//tools/base/transport/native/daemon",
        "//tools/base/transport/proto:cc_proto",
        "@gflags_repo//:gflags_nothreads",
    ],
)

android_cc_binary(
    name = "android",
    # Cross compiling from windows doesn't produce a stripped binary.
    binary = select({
        "@platforms//os:windows": ":transport_main",
        "//conditions:default": ":transport_main.stripped",
    }),
    filename = "transport",
    visibility = ["//visibility:public"],
)

studio_data(
    name = "transport-bundle",
    files = [
        ":android",
        "//tools/base/transport/native/agent:android",
    ],
    mappings = {
        "tools/base/transport/android/": "transport/",
        "tools/base/transport/native/agent/android/": "transport/native/agent/",
    },
    visibility = ["//visibility:public"],
)
