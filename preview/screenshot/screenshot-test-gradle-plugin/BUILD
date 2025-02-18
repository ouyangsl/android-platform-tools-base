load("//tools/base/bazel:gradle.bzl", "gradle_build", "gradle_test")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load("//tools/base/bazel:maven.bzl", "maven_repository")
load("//tools/base/build-system:hybrid_build.bzl", "GRADLE_PROPERTIES")
load("//tools/base/preview/screenshot:version.bzl", "SCREENSHOT_TEST_PLUGIN_GRADLE_PROPERTIES")

filegroup(
    name = "agp_gradle_build_files",
    srcs = glob([
        "src/main/**/*.kt",
        "src/main/resources/**",
    ]) + [
        "build.gradle",
    ],
    visibility = ["//visibility:private"],
)

filegroup(
    name = "agp_gradle_test_files",
    srcs = glob([
        "src/test/**/*.kt",
        "src/test/resources/**",
    ]) + [
        "build.gradle",
    ],
    visibility = ["//visibility:private"],
)

# keep sorted
SCREENSHOT_TEST_GRADLE_PLUGIN_SOURCE_FILES = [
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
    name = "maven_test_dependencies",
    # keep sorted
    artifacts = [
        "//tools/base/multipreview-asm:compose-preview-detector",
        "//tools/base/standalone-render/compose-cli:compose-preview-renderer",
        "//tools/base/standalone-render/compose-cli-serialize:compose-preview-renderer-model",
        "//tools/base/testutils:tools.testutils",
        "@maven//:org.jetbrains.kotlin.kotlin-test-junit_1.7.10",
        "@maven//:org.mockito.kotlin.mockito-kotlin_5.4.0",
    ],
    visibility = [
        "//tools/base/build-system/integration-test:__subpackages__",
    ],
)

maven_repository(
    name = "maven_dependencies",
    # keep sorted
    artifacts = [
        "//tools/base/multipreview-asm:compose-preview-detector",
        "//tools/base/standalone-render/compose-cli-serialize:compose-preview-renderer-model",
    ],
)

gradle_build(
    name = "screenshot-test-gradle-plugin",
    build_file = "//tools:agp_gradle_build_root_build_file",
    data = ["//prebuilts/studio/jdk:jdk11"] + SCREENSHOT_TEST_GRADLE_PLUGIN_SOURCE_FILES,
    gradle_properties = GRADLE_PROPERTIES | SCREENSHOT_TEST_PLUGIN_GRADLE_PROPERTIES,
    output_file = "screenshot-test-gradle-plugin.zip",
    output_file_source = "dist/repo.zip",
    repos = [
        ":maven_dependencies",
        "//tools/base:agp_artifacts",
        "//tools/base:agp_dependencies",
    ],
    tasks = [":base:preview:screenshot:screenshot-test-gradle-plugin:zipPlugin"],
    visibility = [
        "//tools/base/build-system/integration-test:__subpackages__",
        "//tools/base/preview/screenshot:__pkg__",
    ],
)

gradle_test(
    name = "screenshot-test-gradle-plugin_test",
    build_file = "//tools:agp_gradle_build_root_build_file",
    data = [
        ":agp_gradle_test_files",
        "//prebuilts/studio/jdk:jdk11",
    ] + SCREENSHOT_TEST_GRADLE_PLUGIN_SOURCE_FILES,
    gradle_properties = GRADLE_PROPERTIES | SCREENSHOT_TEST_PLUGIN_GRADLE_PROPERTIES,
    repos = [
        ":maven_test_dependencies",
        "//tools/base:agp_artifacts",
        "//tools/base:agp_dependencies",
    ],
    tasks = [":base:preview:screenshot:screenshot-test-gradle-plugin:test"],
    test_output_dir = "build/base/preview/screenshot/screenshot-test-gradle-plugin/build/test-results/test",
    visibility = ["//visibility:public"],
)

kotlin_library(
    name = "lib",
    srcs = glob([
        "src/main/java/**/*.kt",
    ]),
    lint_baseline = "lint_baseline.xml",
    lint_custom_rules = [
        "@maven//:androidx.lint.lint-gradle",  # For gradle-specific lint checks
    ],
    deps = [
        "//tools/base/build-system:gradle-api",
        "//tools/base/build-system/gradle-core",
        "//tools/base/common:tools.common",
        "//tools/base/multipreview-asm:tools.compose-preview-detector",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/standalone-render/compose-cli-serialize:compose-preview-renderer-model",
        "//tools/base/testutils:tools.testutils",
        "@maven//:net.sf.kxml.kxml2",
    ],
)
