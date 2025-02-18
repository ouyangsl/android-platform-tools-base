load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")
load("//tools/base/bazel:proto.bzl", "android_java_proto_library", "java_proto_library")

android_java_proto_library(
    name = "deploy-service-proto",
    srcs = glob(["proto/**/*.proto"]),
    grpc_support = True,
    java_deps = ["@maven//:io.grpc.grpc-all"],
    visibility = ["//visibility:public"],
)

java_binary(
    name = "deploy.service",
    srcs = glob([
        "src/main/**/*.java",
    ]),
    main_class = "com.android.deploy.service.DeployServiceMain",
    visibility = ["//visibility:public"],
    deps = [
        ":deploy-service-proto",
        "//tools/base/annotations",
        "//tools/base/bazel:studio-grpc",
        "//tools/base/bazel:studio-proto",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/deploy/deployer:tools.deployer",
        "//tools/base/deploy/proto:deploy_java_proto",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.protobuf.protobuf-java",
    ],
)

java_test(
    name = "service_test",
    srcs = glob([
        "src/test/java/com/android/deploy/service/*.java",
    ]),
    data = [
    ],
    jvm_flags = ["-Dtest.suite.jar=service_test.jar"],
    tags = [
        "noci:studio-win",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":deploy.service",
        ":deploy-service-proto",
        "//tools/base/annotations",
        "//tools/base/bazel:studio-grpc",
        "//tools/base/bazel:studio-proto",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/deploy/deployer:tools.deployer",
        "//tools/base/deploy/proto:deploy_java_proto",
        "//tools/base/testutils:studio.android.sdktools.testutils_testlib",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:org.mockito.mockito-core",
    ],
)
