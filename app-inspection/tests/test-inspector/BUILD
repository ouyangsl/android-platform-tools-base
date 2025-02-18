load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary", "dex_library", "jni_library")
load("//tools/base/bazel:merge_archives.bzl", "merge_jars")

java_library(
    name = "test-inspector-sources_undexed",
    srcs = glob(["src/**/*.java"]),
    deps = [
        "//prebuilts/tools/common/m2:androidx.inspection.inspection.1.0.0",
        "//tools/base/app-inspection/tests/test-inspector/api:test-inspector-api",
        "//tools/base/fakeandroid:android-mock",
        "@maven//:androidx.annotation.annotation",
    ],
)

# D8 doesn't not pull the resources into the output jar. We first build the dex code first,
# then we build the resources jar seperately and finally merge them back together.
# If more targets start needing to include resources in their dex_library targets,
# we should definitely move this as part of dex_library instead.
dex_library(
    name = "test-inspector-sources_dexed",
    jars = [
        ":test-inspector-sources_undexed",
        "//tools/base/app-inspection/tests/test-inspector/api:test-inspector-api",
    ],
)

java_library(
    name = "test-inspector-resources",
    resource_strip_prefix = "tools/base/app-inspection/tests/test-inspector",
    resources = glob(["META-INF/**"]) +  ## this need only to run tests on the linux host
                select({
                    "@platforms//os:windows": [],
                    "//conditions:default": [":hostlib"],
                }),
)

merge_jars(
    name = "test-inspector_merged",
    out = "test-inspector_merged.jar",
    jars = [
        ":test-inspector-sources_dexed",
        ":test-inspector-resources",
    ] + select({
        "@platforms//os:windows": [],
        "//conditions:default": [":inspectorjni"],
    }),
)

java_import(
    name = "test-inspector",
    jars = [":test-inspector_merged.jar"],
    visibility = ["//visibility:public"],
)

genrule(
    name = "hostlib",
    srcs = [
        ":libtest_jni.so",  # a filegroup with multiple files in it ==> $(locations)
    ],
    outs = ["lib/host/libtest_jni.so"],
    cmd = "cp $(locations :libtest_jni.so) $@",
)

cc_binary(
    name = "libtest_jni.so",
    srcs = ["native/test_jni.cpp"],
    copts = ANDROID_COPTS,
    linkopts = ANDROID_LINKOPTS,
    linkshared = True,
    visibility = ["//visibility:public"],
    deps = ["//prebuilts/studio/jdk:jni_headers"],
)

jni_library(
    name = "inspectorjni",
    target_compatible_with = select({
        "@platforms//os:windows": ["@platforms//:incompatible"],
        "//conditions:default": [],
    }),
    visibility = ["//visibility:public"],
    deps = select({
        "@platforms//os:windows": [],
        "//conditions:default": [":libtest_jni.so.stripped"],
    }),
)
