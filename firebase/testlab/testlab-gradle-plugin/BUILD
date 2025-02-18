load("//tools/base/bazel:gradle.bzl", "gradle_build", "gradle_test")
load("//tools/base/bazel:maven.bzl", "maven_repository")
load("//tools/base/build-system:hybrid_build.bzl", "GRADLE_PROPERTIES")

filegroup(
    name = "agp_gradle_build_files",
    #buildifier: disable=globs
    srcs = glob([
        "src/main/**/*.java",
        "src/main/**/*.kt",
        "src/main/resources/**",
    ]) + [
        "build.gradle",
    ],
    visibility = ["//visibility:private"],
)

filegroup(
    name = "agp_gradle_test_files",
    #buildifier: disable=globs
    srcs = glob([
        "src/test/**/*.java",
        "src/test/**/*.kt",
        "src/test/resources/**",
    ]) + [
        "build.gradle",
    ],
    visibility = ["//visibility:private"],
)

# keep sorted
FTL_GRADLE_PLUGIN_SOURCE_FILES = [
    ":agp_gradle_build_files",
    "//tools:agp_gradle_build_files",
    "//tools/base/build-system/builder-model:agp_gradle_build_files",
    "//tools/base/build-system/builder-test-api:agp_gradle_build_files",
    "//tools/base/build-system/gradle-api:agp_gradle_build_files",
    "//tools/base/common:agp_gradle_build_files",
    "//tools/buildSrc:agp_gradle_build_files",
    "//tools/buildSrc/base:agp_gradle_build_files",
]

maven_repository(
    name = "maven_dependencies",
    # keep sorted
    artifacts = [
        "//tools/base/utp/android-test-plugin-host-device-info-proto",
        "@maven//:com.google.api-client.google-api-client_1.22.0",
        "@maven//:com.google.apis.google-api-services-storage_v1-rev82-1.22.0",
        "@maven//:com.google.apis.google-api-services-testing_v1-rev82-1.22.0",
        "@maven//:com.google.apis.google-api-services-toolresults_v1beta3-rev82-1.22.0",
        "@maven//:com.google.http-client.google-http-client-jackson2",
        "@maven//:com.google.http-client.google-http-client_1.41.0",
        "@maven//:com.google.oauth-client.google-oauth-client_1.22.0",
        "@maven//:com.google.protobuf.protobuf-java-util",
        "@maven//:com.google.testing.platform.android-device-provider-local",
        "@maven//:org.junit.junit-bom_5.9.3",
    ],
    visibility = [
        "//tools/base/build-system/integration-test:__subpackages__",
    ],
)

maven_repository(
    name = "maven_test_dependencies",
    # keep sorted
    artifacts = [
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.apis.google-api-services-storage_v1-rev82-1.22.0",
        "@maven//:com.google.apis.google-api-services-testing_v1-rev82-1.22.0",
        "@maven//:com.google.apis.google-api-services-toolresults_v1beta3-rev82-1.22.0",
        "@maven//:org.jetbrains.kotlin.kotlin-test-junit_1.7.10",
        "@maven//:org.mockito.kotlin.mockito-kotlin_5.4.0",
    ],
)

gradle_build(
    name = "testlab-gradle-plugin",
    build_file = "//tools:agp_gradle_build_root_build_file",
    data = ["//prebuilts/studio/jdk:jdk11"] + FTL_GRADLE_PLUGIN_SOURCE_FILES,
    gradle_properties = GRADLE_PROPERTIES,
    output_file = "testlab-gradle-plugin.zip",
    output_file_source = "dist/repo.zip",
    repos = [
        ":maven_dependencies",
        "//tools/base:agp_artifacts",
        "//tools/base:agp_dependencies",
    ],
    tasks = [":base:firebase:testlab:testlab-gradle-plugin:zipPlugin"],
    visibility = [
        "//tools/base/build-system/integration-test:__subpackages__",
        "//tools/base/gmaven:__pkg__",
    ],
)

gradle_test(
    name = "testlab-gradle-plugin_test",
    build_file = "//tools:agp_gradle_build_root_build_file",
    data = [
        ":agp_gradle_test_files",
        "//prebuilts/studio/jdk:jdk11",
    ] + FTL_GRADLE_PLUGIN_SOURCE_FILES,
    gradle_properties = GRADLE_PROPERTIES,
    repos = [
        ":maven_dependencies",
        ":maven_test_dependencies",
        "//tools/base:agp_artifacts",
        "//tools/base:agp_dependencies",
    ],
    tasks = [":base:firebase:testlab:testlab-gradle-plugin:test"],
    test_output_dir = "build/base/firebase/testlab/testlab-gradle-plugin/build/test-results/test",
    visibility = ["//visibility:public"],
)
