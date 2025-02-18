load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:utils.bzl", "fileset")
load("//tools/base/common:version.bzl", "BUILD_VERSION")

fileset(
    name = "resources",
    srcs = glob(["src/main/resources/**"]),
    mappings = {
        "src/main/resources/": "",
    },
)

kotlin_library(
    name = "builder",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    lint_baseline = "lint_baseline.xml",
    module_name = "builder",
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/build-system/builder",
    resources = [":resources"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools/apkzlib",
        "//tools/base/build-system:tools.apksig",
        "//tools/base/build-system:tools.manifest-merger",
        "//tools/base/build-system/profile",
        "//tools/base/zipflinger",
        "@maven//:com.squareup.javawriter",
    ],
    deps = [
        "//tools/analytics-library/protos/src/main/proto",
        "//tools/analytics-library/tracker:tools.analytics-tracker",
        "//tools/base/build-system/builder-model",
        "//tools/base/build-system/builder-test-api:tools.builder-test-api",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/signflinger",
        "@maven//:commons-codec.commons-codec",
        "@maven//:javax.inject.javax.inject",
        "@maven//:org.bouncycastle.bcpkix-jdk18on",
        "@maven//:org.bouncycastle.bcprov-jdk18on",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
        "@maven//:org.ow2.asm.asm-commons",
    ],
)

kotlin_library(
    name = "fixtures",
    srcs = glob([
        "src/testFixtures/java/**/*.kt",
        "src/testFixtures/java/**/*.java",
    ]),
    resources = glob(["src/testFixtures/resources/**"]),
    visibility = ["//visibility:public"],  # contains com.android.repository.testframework
    deps = [
        ":builder",
    ],
)

kotlin_test(
    name = "tests",
    srcs = glob([
        "src/test/java/**/*.kt",
        "src/test/java/**/*.java",
    ]),
    data = [
        "//prebuilts/studio/sdk:build-tools/latest",
        "//tools/base/build-system:android_platform_for_agp_unit_tests",
        "//tools/base/build-system/aapt2:aapt2_for_tests",
    ],
    friends = [":builder"],
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    lint_baseline = "lint_baseline_tests.xml",
    resources = glob(["src/test/resources/**"]),
    tags = [
        "slow",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":builder",
        ":fixtures",
        "//prebuilts/r8",
        "//tools/apkzlib",
        "//tools/base/annotations",
        "//tools/base/build-system:tools.apksig",
        "//tools/base/build-system:tools.manifest-merger",
        "//tools/base/build-system/builder-model",
        "//tools/base/build-system/builder-test-api:tools.builder-test-api",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/repository:tools.testlib",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/signflinger",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/zipflinger",
        "@maven//:com.android.tools.desugar_jdk_libs",
        "@maven//:com.android.tools.desugar_jdk_libs_configuration",
        "@maven//:com.google.jimfs.jimfs",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:nl.jqno.equalsverifier.equalsverifier",
        "@maven//:org.bouncycastle.bcpkix-jdk18on",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
        "@maven//:org.jetbrains.kotlin.kotlin-test",
        "@maven//:org.mockito.mockito-core",
        "@maven//:org.ow2.asm.asm",
    ],
)

java_library(
    name = "benchmarks_utils",
    srcs = [
        "src/benchmarks/java/com/android/builder/benchmarks/StopWatch.java",
        "src/benchmarks/java/com/android/builder/benchmarks/TreeCreator.java",
        "src/benchmarks/java/com/android/builder/benchmarks/Utils.java",
        "src/benchmarks/java/com/android/builder/benchmarks/ZipCreator.java",
    ],
    deps = [
        "//tools/base/annotations",
    ],
)

java_test(
    name = "testBenchmarkAdd",
    size = "large",
    srcs = [
        "src/benchmarks/java/com/android/builder/benchmarks/BenchmarkAdd.java",
    ],
    jvm_flags = ["-Dtest.suite.jar=testBenchmarkAdd.jar"],
    tags = [
        "manual",  # b/303906718
    ],
    target_compatible_with = ["@platforms//:incompatible"],  # b/303906718
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":benchmarks_utils",
        "//tools/apkzlib",
        "//tools/base/annotations",
        "//tools/base/build-system/builder",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/zipflinger",
        "@maven//:com.google.guava.guava",
        "@maven//:junit.junit",
    ],
)

java_test(
    name = "testBenchmarkJarMerge",
    size = "large",
    srcs = [
        "src/benchmarks/java/com/android/builder/benchmarks/BenchmarkJarMerge.java",
    ],
    jvm_flags = ["-Dtest.suite.jar=testBenchmarkJarMerge.jar"],
    tags = [
        "manual",  # b/303906718
    ],
    target_compatible_with = ["@platforms//:incompatible"],  # b/303906718
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":benchmarks_utils",
        "//tools/apkzlib",
        "//tools/base/annotations",
        "//tools/base/build-system/builder",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/zipflinger",
        "@maven//:com.google.guava.guava",
        "@maven//:junit.junit",
    ],
)

java_test(
    name = "testBenchmarkList",
    size = "large",
    srcs = [
        "src/benchmarks/java/com/android/builder/benchmarks/BenchmarkList.java",
    ],
    jvm_flags = ["-Dtest.suite.jar=testBenchmarkList.jar"],
    tags = [
        "manual",  # b/303906718
    ],
    target_compatible_with = ["@platforms//:incompatible"],  # b/303906718
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":benchmarks_utils",
        "//tools/apkzlib",
        "//tools/base/annotations",
        "//tools/base/build-system/builder",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/zipflinger",
        "@maven//:com.google.guava.guava",
        "@maven//:junit.junit",
    ],
)

java_test(
    name = "testBenchmarkFolderMerge",
    size = "large",
    srcs = [
        "src/benchmarks/java/com/android/builder/benchmarks/BenchmarkFolderMerge.java",
    ],
    jvm_flags = ["-Dtest.suite.jar=testBenchmarkAdd.jar"],
    tags = [
        "manual",  # b/303906718
    ],
    target_compatible_with = ["@platforms//:incompatible"],  # b/303906718
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":benchmarks_utils",
        "//tools/apkzlib",
        "//tools/base/annotations",
        "//tools/base/build-system/builder",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/zipflinger",
        "@maven//:com.google.guava.guava",
        "@maven//:junit.junit",
    ],
)

filegroup(
    name = "agp_gradle_build_files",
    srcs = glob([
        "src/main/**/*.java",
        "src/main/**/*.kt",
        "src/main/resources/**",
    ]) + [
        "NOTICE",
        "build.gradle",
        "//prebuilts/r8",
    ],
    visibility = ["//tools/base/build-system:__pkg__"],
)
