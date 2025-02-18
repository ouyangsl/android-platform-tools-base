load("//tools/adt/idea/android/integration:build_defs.bzl", "INTEGRATION_TEST_SYSTEM_IMAGE")
load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")

tags = [
    "noci:studio-win",
]

kotlin_library(
    name = "debugger",
    srcs = glob(["src/**/*.kt"]),
    tags = tags,
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:org.jetbrains.kotlin",
        "@maven//:org.jetbrains.kotlinx.kotlinx-cli-jvm",
    ],
)

# A library with all tested classes
kotlin_library(
    name = "test-classes",
    # exclude these from coverage by marking them as testonly
    testonly = True,
    srcs = glob(["resources/src/**/*.kt"]),
    kotlinc_opts = ["-Xsam-conversions=class"],
    tags = tags,
    deps = [
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core",
    ],
)

dex_library(
    name = "test-classes-dex",
    flags = ["--min-api 26"],
    jars = [
        ":test-classes-binary_deploy.jar",
    ],
    tags = tags,
    visibility = ["//visibility:public"],
)

# A library with all tested classes so we can create a "*_deploy.jar" that contains all dependencies
java_binary(
    name = "test-classes-binary",
    # must be marked testonly because it depends on :test-classes
    testonly = True,
    main_class = "MainKt",
    tags = tags,
    runtime_deps = [
        ":test-classes",
    ],
)

filegroup(
    name = "idea-deps",
    srcs = [
        ":test-classes-binary_deploy.jar",
        ":test-classes-dex.jar",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/tools/linux-x86_64/art",
        "//prebuilts/tools/linux-x86_64/art:art_deps",
    ],
    tags = tags,
)

# Test target
kotlin_test(
    name = "tests",
    srcs = glob(["testSrc/**/*.kt"]),
    data = glob(["resources/**"]) + [
        ":test-classes-binary_deploy.jar",
        ":test-classes-dex.jar",
        "//prebuilts/studio/sdk:emulator",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/tools/linux-x86_64/art",
        "//prebuilts/tools/linux-x86_64/art:art_deps",
        INTEGRATION_TEST_SYSTEM_IMAGE,
    ],
    friends = [":debugger"],
    jvm_flags = [
        "-Dtest.suite.jar=tests.jar",
    ],
    tags = tags,
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":debugger",
        "//tools/adt/idea/as-driver:as-driver.utils_testlib",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:org.jetbrains.kotlin.kotlin-test",
    ],
)

# A command line program that updates the golden files.
java_binary(
    name = "update-golden",
    data = glob(["resources/**"]) + [
        ":test-classes-binary_deploy.jar",
        ":test-classes-dex.jar",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/tools/linux-x86_64/art",
        "//prebuilts/tools/linux-x86_64/art:art_deps",
    ],
    jvm_flags = [
        "--add-opens=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED",
    ],
    main_class = "com.android.tools.debuggertests.UpdateGoldenKt",
    tags = tags,
    runtime_deps = [
        ":debugger",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.debugger-tests",
    # do not sort: must match IML order
    srcs = ["src"],
    iml_files = ["debugger-tests.iml"],
    tags = tags,
    test_srcs = [
        "resources/src",
        "testSrc",
    ],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/adt/idea/as-driver:as-driver.utils[module, test]",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//prebuilts/tools/common/m2:kotlinx-cli-jvm-0.3.1",
    ],
)
