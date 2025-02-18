"""Continuous Integration scripts."""

load("@bazel_skylib//rules:common_settings.bzl", "bool_flag", "string_flag")

string_flag(
    name = "ab_target",
    build_setting_default = "",
    visibility = ["//tools/base/bazel/ci:__subpackages__"],
)

bool_flag(
    name = "is_ci",
    build_setting_default = False,
    visibility = ["//tools/base/bazel/ci:__subpackages__"],
)

py_library(
    name = "bazel",
    srcs = ["bazel.py"],
)

py_library(
    name = "bazel_diff",
    srcs = ["bazel_diff.py"],
)

py_library(
    name = "fake_build_env",
    testonly = 1,
    srcs = ["fake_build_env.py"],
    deps = [":bazel"],
)

py_library(
    name = "fake_gce",
    testonly = 1,
    srcs = ["fake_gce.py"],
    deps = [
        ":fake_build_env",
        ":gce",
        "@absl-py//absl/testing:absltest",
    ],
)

py_library(
    name = "gce",
    srcs = ["gce.py"],
)

py_library(
    name = "errors",
    srcs = ["errors.py"],
)

py_library(
    name = "presubmit",
    srcs = ["presubmit.py"],
    deps = [
        ":bazel",
        ":bazel_diff",
        ":gce",
    ],
)

py_library(
    name = "query_checks",
    srcs = ["query_checks.py"],
    deps = [
        ":bazel",
        ":errors",
    ],
)

py_library(
    name = "studio",
    srcs = ["studio.py"],
    deps = [
        ":bazel",
        ":errors",
    ],
)

py_library(
    name = "studio_linux",
    srcs = ["studio_linux.py"],
    deps = [
        ":bazel",
        ":presubmit",
        ":studio",
    ],
)

py_library(
    name = "studio_win",
    srcs = ["studio_win.py"],
    deps = [
        ":bazel",
        ":presubmit",
        ":studio",
    ],
)

py_library(
    name = "studio_mac",
    srcs = ["studio_mac.py"],
    deps = [
        ":bazel",
        ":studio",
    ],
)

py_library(
    name = "ci_lib",
    srcs = ["ci.py"],
    deps = [
        ":bazel",
        ":errors",
        ":query_checks",
        ":studio_linux",
        ":studio_mac",
        ":studio_win",
    ],
)

py_binary(
    name = "ci",
    srcs = ["ci.py"],
    data = glob(["data/*"]),
    deps = [":ci_lib"],
)

py_test(
    name = "ci_test",
    srcs = ["ci_test.py"],
    deps = [
        ":ci_lib",
        "@absl-py//absl/testing:absltest",
    ],
)

py_test(
    name = "gce_test",
    srcs = ["gce_test.py"],
    deps = [
        ":fake_build_env",
        ":fake_gce",
        ":gce",
        "@absl-py//absl/testing:absltest",
    ],
)

py_test(
    name = "presubmit_test",
    srcs = ["presubmit_test.py"],
    deps = [
        ":bazel",
        ":bazel_diff",
        ":fake_build_env",
        ":fake_gce",
        ":gce",
        ":presubmit",
        "@absl-py//absl/testing:absltest",
        "@absl-py//absl/testing:parameterized",
    ],
)
