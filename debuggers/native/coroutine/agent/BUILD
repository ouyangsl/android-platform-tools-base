load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary")

cc_binary(
    name = "coroutine_debugger_agent.so",
    srcs = [
        "agent.cc",
        "jni_utils.cc",
        "jni_utils.h",
    ],
    copts = ANDROID_COPTS,
    linkopts = ANDROID_LINKOPTS,
    linkshared = 1,
    visibility = ["//visibility:public"],
    deps = [
        "@slicer//:slicer",
        "//tools/base/transport/native/jvmti",
    ],
)

android_cc_binary(
    name = "coroutine_debugger_agent",
    binary = ":coroutine_debugger_agent.so.stripped",
    filename = "coroutine_debugger_agent.so",
    visibility = ["//visibility:public"],
)
