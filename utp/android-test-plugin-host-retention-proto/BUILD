load("//tools/base/bazel:proto.bzl", "INTELLIJ_PLATFORM_PROTO_VERSION", "android_java_proto_library", "maven_proto_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

maven_proto_library(
    name = "android-test-plugin-host-retention-proto",
    srcs = glob(["src/main/proto/*.proto"]),
    coordinates = "com.android.tools.utp:android-test-plugin-host-retention-proto",
    description = "A protobuf message used in UTP plugin to take a device snapshot on test failures.",
    java_exports = [
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
    ],
    notice = "NOTICE",
    protoc_version = INTELLIJ_PLATFORM_PROTO_VERSION,
    resource_strip_prefix = "tools/base/utp/android-test-plugin-host-retention-proto/src/main/proto",
    resources = glob(["src/main/proto/*.proto"]),
    version = BASE_VERSION,
    visibility = ["//visibility:public"],
)

android_java_proto_library(
    name = "libstudio.android-test-plugin-host-retention-proto",
    srcs = glob(["src/main/proto/*.proto"]),
    visibility = ["//visibility:public"],
)
