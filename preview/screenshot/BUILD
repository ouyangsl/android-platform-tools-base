load("//tools/base/bazel:maven.bzl", "maven_repository")
load("//tools/base/bazel:utils.bzl", "merged_zip")

maven_repository(
    name = "maven_dependencies",
    # keep sorted
    artifacts = [
        "//tools/base/common:tools.common",
        "//tools/base/multipreview-asm:compose-preview-detector",
        "//tools/base/preview/screenshot/screenshot-validation-junit-engine",
        "//tools/base/standalone-render/compose-cli:compose-preview-renderer",
        "//tools/base/standalone-render/compose-cli-serialize:compose-preview-renderer-model",
    ],
)

# This includes transitive maven dependencies.
# Use :preview_screenshot_maven_repo for release.
merged_zip(
    name = "preview_screenshot_maven_repo_all",
    srcs = [
        ":maven_dependencies.zip",
        "//tools/base/preview/screenshot/screenshot-test-gradle-plugin:screenshot-test-gradle-plugin.zip",
    ],
    visibility = [
        "//tools/base/build-system/integration-test:__subpackages__",
    ],
)

maven_repository(
    name = "maven_dependencies_no_transitive_deps",
    # keep sorted
    artifacts = [
        "//tools/base/preview/screenshot/screenshot-validation-junit-engine",
        "//tools/base/standalone-render/compose-cli:compose-preview-renderer",
        "//tools/base/standalone-render/compose-cli-serialize:compose-preview-renderer-model",
    ],
    include_transitive_deps = False,
)

merged_zip(
    name = "preview_screenshot_maven_repo",
    srcs = [
        ":maven_dependencies_no_transitive_deps.zip",
        "//tools/base/preview/screenshot/screenshot-test-gradle-plugin:screenshot-test-gradle-plugin.zip",
    ],
    visibility = [
        "//tools/base/build-system/integration-test:__subpackages__",
    ],
)
