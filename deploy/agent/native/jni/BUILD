package(default_visibility = ["//tools/base/deploy/agent/native:all"])

load("//tools/base/bazel:android.bzl", "select_android")

cc_library(
    name = "agent_jni",
    srcs = [
        "jni_class.cc",
        "jni_object.cc",
        "jni_util.cc",
    ],
    hdrs = [
        "jni_class.h",
        "jni_object.h",
        "jni_util.h",
    ],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/deploy/common:common_lib",
    ],
)
