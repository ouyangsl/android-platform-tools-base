load("@bazel_skylib//lib:dicts.bzl", "dicts")
load(
    "@bazel_toolchains//rules/exec_properties:exec_properties.bzl",
    "create_rbe_exec_properties_dict",
)

package(default_visibility = ["//visibility:public"])

# Hack to select on the host platform. It should be avoided whenever possible.
# https://bazel.build/docs/configurable-attributes#what_if_i_really_really_want_to_select_on_the_platform
config_setting(
    name = "windows_host",
    values = {
        "host_platform": "//tools/base/bazel/platforms:windows",
    },
    visibility = [
        "//tools/adt/idea/streaming/integration/event-logger:__pkg__",
        "//tools/adt/idea/streaming/integration/languages:__pkg__",
        "//tools/adt/idea/streaming/testSrc/testApp/src/main:__pkg__",
    ],
)

config_setting(
    name = "macos-arm64",
    constraint_values = [
        "@platforms//os:macos",
        "@platforms//cpu:aarch64",
    ],
)

config_setting(
    name = "macos-x86_64",
    constraint_values = [
        "@platforms//os:macos",
        "@platforms//cpu:x86_64",
    ],
)

platform(
    name = "linux",
    constraint_values = [
        "@platforms//cpu:x86_64",
        "@platforms//os:linux",
        "@bazel_tools//tools/cpp:clang",
    ],
    exec_properties = dicts.add(
        create_rbe_exec_properties_dict(
            container_image = "docker://gcr.io/google.com/android-studio-alphasource/rbe-ubuntu22-as@sha256:bc96d9c2b2508641c1bc300ed38b1157b3dde53f77e2b0358740da0196795d36",
            docker_add_capabilities = "SYS_NICE",
            docker_network = "off",
            docker_privileged = True,  # This is required to support emulators/nested KVM.
            docker_use_urandom = True,
            labels = {
                "machine-size": "default",
                "image-family": "default",
            },
            os_family = "Linux",
        ),
        # cache-silo-key: this is essentially a "salt" added to the remote cache key.
        # Change it to a new value when we want to create a new cache from scratch.
        {"cache-silo-key": "20211108"},
    ),
)

platform(
    name = "windows",
    constraint_values = [
        "@platforms//cpu:x86_64",
        "@platforms//os:windows",
        "@bazel_tools//tools/cpp:clang-cl",
    ],
    exec_properties = dicts.add(
        create_rbe_exec_properties_dict(
            container_image = "docker://us-central1-docker.pkg.dev/google.com/android-studio-alphasource/rbe/windows-2022-test@sha256:eaa19f67a1b92739a3d87e64bfa005bbedcbd217869efdd8c08d4070ac0ab52a",
            docker_network = "off",
            labels = {
                "machine-size": "default",
                "image-family": "2022",
            },
            os_family = "Windows",
        ),
        # cache-silo-key: this is essentially a "salt" added to the remote cache key.
        # Change it to a new value when we want to create a new cache from scratch.
        {"cache-silo-key": "20240716"},
    ),
)

platform(
    name = "mac-arm64",
    constraint_values = [
        "@platforms//os:macos",
        "@platforms//cpu:aarch64",
        "@bazel_tools//tools/cpp:clang",
    ],
)

platform(
    name = "mac-x86_64",
    constraint_values = [
        "@platforms//os:macos",
        "@platforms//cpu:x86_64",
        "@bazel_tools//tools/cpp:clang",
    ],
)

platform(
    name = "macos",
    constraint_values = [
        "@platforms//os:macos",
        "@bazel_tools//tools/cpp:clang",
    ],
    # N.B. we pick the CPU based on the host (x86_64 or arm64).
    parents = ["@local_config_platform//:host"],
)
