load("//tools/base/bazel:proto.bzl", "android_java_proto_library", "cc_grpc_proto_library")

package(default_visibility = ["//visibility:public"])

android_java_proto_library(
    name = "transport_java_proto",
    srcs = glob(["**/*.proto"]),
    grpc_support = True,
    java_deps = [
        "@maven//:io.grpc.grpc-all",
        "@perfetto_repo//:perfetto_config_java_proto",
    ],
    proto_deps = [
        "@perfetto_repo//:perfetto_config_java_proto_srcs",
    ],
)

cc_grpc_proto_library(
    name = "cc_proto",
    srcs = glob(["**/*.proto"]),
    grpc_support = True,
    target_compatible_with = select({
        "@platforms//os:windows": ["@platforms//:incompatible"],
        "//conditions:default": [],
    }),
    include_prefix = "proto",
    deps = ["@perfetto_repo//:cc_proto"],
)
