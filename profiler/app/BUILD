load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:merge_archives.bzl", "merge_jars")
load("//tools/base/bazel:utils.bzl", "java_jarjar")

package(default_visibility = ["//visibility:public"])

filegroup(
    name = "supportlib-srcs",
    srcs = glob(["supportlib/src/main/java/com/android/tools/profiler/support/*.java"]),
    visibility = ["//visibility:private"],
)

filegroup(
    name = "echo-sample-srcs",
    srcs = glob(["echo-sample/src/main/java/com/android/tools/agent/echo/*.java"]),
    visibility = ["//visibility:private"],
)

filegroup(
    name = "common-srcs",
    srcs = glob([
        "common/src/main/java/com/android/tools/profiler/support/cpu/*.java",
        "common/src/main/java/com/android/tools/profiler/support/event/*.java",
        "common/src/main/java/com/android/tools/profiler/support/memory/*.java",
        "common/src/main/java/com/android/tools/profiler/support/profilers/*.java",
        "common/src/main/java/com/android/tools/profiler/support/util/*.java",
    ]),
    visibility = ["//visibility:private"],
)

filegroup(
    name = "perfa-srcs",
    srcs = glob(["perfa/src/main/java/com/android/tools/profiler/support/*.java"]),
    visibility = ["//visibility:private"],
)

java_library(
    name = "studio-profiler-core",
    srcs = [
        ":common-srcs",
        ":echo-sample-srcs",
        ":supportlib-srcs",
    ],
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "@maven//:androidx.annotation.annotation",
    ],
)

merge_jars(
    name = "studio-profiler",
    out = "studio-profiler.jar",
    jars = ["libstudio-profiler-core.jar"] + select({
        "@platforms//os:windows": [],
        "//conditions:default": ["//tools/base/profiler/native/agent:supportjni"],
    }),
)

java_library(
    name = "perfa_java_core",
    srcs = [
        ":common-srcs",
        ":echo-sample-srcs",
        ":perfa-srcs",
    ],
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "@maven//:androidx.annotation.annotation",
    ],
)

java_jarjar(
    name = "perfa_java",
    srcs = [
        ":perfa_java_core",
    ],
    rules = "//tools/base/bazel:jarjar_rules.txt",
)

dex_library(
    name = "perfa",
    jars = [
        ":perfa_java",
        "//prebuilts/tools/common/m2:androidx.inspection.inspection.1.0.0",
        "//tools/base/app-inspection/agent",
    ],
)
