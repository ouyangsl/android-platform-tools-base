load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

maven_library(
    name = "android-test-plugin-host-retention",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    coordinates = "com.android.tools.utp:android-test-plugin-host-retention",
    resource_strip_prefix = "tools/base/utp/android-test-plugin-host-retention/src/main/resources/",
    resources = glob(["src/main/resources/**"]),
    version = BASE_VERSION,
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/common:tools.common",
        "//tools/base/emulator/proto",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
        "@maven//:com.google.guava.guava",
        "@maven//:commons-io.commons-io",
        "@maven//:io.grpc.grpc-core",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core",
        "@maven//:com.google.code.gson.gson",
        "@maven//:com.google.protobuf.protobuf-java",
        "//tools/base/utp/utp-common",
        "//tools/base/utp/android-test-plugin-host-retention-proto",
        # Don't change dependency order or the test fails. Those plugins bundle kotlinx
        # coroutine jar which may be different version.
        "@maven//:com.google.testing.platform.android-device-provider-local",
        "@maven//:io.grpc.grpc-netty",
        "@maven//:io.grpc.grpc-protobuf",
        "@maven//:io.grpc.grpc-stub",
        "@maven//:com.google.testing.platform.launcher",  # CompileOnly should not be in the pom
    ],
)

kotlin_test(
    name = "tests",
    srcs = glob([
        "src/test/java/**/*.kt",
        "src/test/java/**/*.java",
    ]),
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    resources = glob(["src/test/resources/**"]),
    tags = ["noci:studio-win"],  # b/249357577
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        # Don't change dependency order or the test fails. This launcher jar
        # should be loaded first.
        "@maven//:com.google.testing.platform.launcher",
        # Test dependencies.
        ":android-test-plugin-host-retention",
        "@maven//:io.grpc.grpc-stub",
        "@maven//:io.grpc.grpc-testing",
        "//tools/base/common:tools.common",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.protobuf.protobuf-java",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:org.mockito.kotlin.mockito-kotlin",
        "@maven//:org.mockito.mockito-core",
    ],
)

filegroup(
    name = "agp_gradle_build_files",
    srcs = glob([
        "src/main/**/*.java",
        "src/main/**/*.kt",
        "src/main/resources/**",
    ]) + [
        "build.gradle",
    ],
    visibility = ["//tools/base/utp:__pkg__"],
)
