package(default_visibility = ["//visibility:public"])

load("//tools/base/bazel:proto.bzl", "cc_grpc_proto_library", "java_proto_library")

cc_grpc_proto_library(
    name = "cc_trace_processor_service_proto",
    srcs = glob(["**/*.proto"]),
    grpc_support = 1,
    include_prefix = "proto",
)

# The following expose the TPD protos to Studio.
java_proto_library(
    name = "java_proto",
    srcs = glob(["**/*.proto"]),
    grpc_support = True,
    java_deps = ["@maven//:io.grpc.grpc-all"],
)
