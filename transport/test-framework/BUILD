load("//tools/base/transport/test-framework:transport_test.bzl", "transport_test")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# A test framework for creating integration tests that communicate across a
# host / device connection using the Transport API.
kotlin_library(
    name = "test-framework",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    lint_baseline = "lint_baseline.xml",
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/bazel:studio-grpc",
        "//tools/base/bazel:studio-proto",
        "//tools/base/fakeandroid",
        "//tools/base/transport/proto:transport_java_proto",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
    ],
)

transport_test(
    name = "SimpleTest",
    srcs = glob(["src/test/**/*.java"]),
    app_dexes = ["//tools/base/transport/test-framework/test-app:test-app"],
    deps = [
        "@maven//:com.google.truth.truth",
    ],
)

cc_library(
    name = "native_test_support",
    srcs = glob(["src/main/native/**/*.cc"]),
    copts = [
        "-Itools/base/profiler/native/agent/",
    ],
    linkstatic = 1,
    target_compatible_with = select({
        "@platforms//os:windows": ["@platforms//:incompatible"],
        "//conditions:default": [],
    }),
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/transport/native/agent",
        "//tools/base/transport/native/utils",
        "//tools/base/transport/proto:cc_proto",
    ],
    alwayslink = 1,
)

cc_binary(
    name = "libagentrule-jni.dylib",
    linkshared = 1,
    target_compatible_with = ["@platforms//os:macos"],
    visibility = ["//visibility:public"],
    deps = [
        ":native_test_support",
    ],
)

cc_binary(
    name = "libagentrule-jni.so",
    linkshared = 1,
    target_compatible_with = ["@platforms//os:linux"],
    visibility = ["//visibility:public"],
    deps = [
        ":native_test_support",
    ],
)
