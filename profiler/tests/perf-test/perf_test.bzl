load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/transport/test-framework:transport_test.bzl", "transport_test")

# Run an integration test that verifies profiler APIs.
#
# srcs: One or more test classes to run under this test.
# test_app: A target that represents a mock app (i.e. a collection of mock
#           Android activities.
def perf_test(
        name,
        srcs,
        test_app,
        deps = [],
        jvm_flags = [],
        data = [],
        tags = [],
        app_runtime_deps = [],
        size = None):
    # Copy the undexed version of the test app and transform its bytecode with
    # profiler hooks. This is how profilers work when targetting devices that
    # don't support jvmti.
    native.genrule(
        name = name + "_transformed_undexed",
        srcs = [test_app + "_undexed_deploy.jar"],
        outs = [name + "_transformed_undexed_deploy.jar"],
        cmd = select({
            "//tools/base/bazel/platforms:macos-x86_64": "cp ./$< ./$@",
            "@platforms//os:windows": "cp ./$< ./$@",
            "//conditions:default": "$(location //tools/base/profiler/tests/profiler-transform-main:profilers-transform-main) ./$< ./$@",
        }),
        executable = 1,
        tools = select({
            "//tools/base/bazel/platforms:macos-x86_64": [],
            "@platforms//os:windows": [],
            "//conditions:default": [
                "//tools/base/profiler/tests/profiler-transform-main:profilers-transform-main",
            ],
        }),
        tags = tags,
    )

    dex_library(
        name = name + "_transformed",
        jars = [name + "_transformed_undexed_deploy.jar"],
    )

    transport_test(
        name = name,
        srcs = srcs,
        deps = deps + [
            "//tools/base/profiler/tests/test-framework",
        ],
        app_dexes = [test_app],
        app_dexes_nojvmti = [
            ":profiler-service",
            name + "_transformed",
        ],
        app_runtime_deps = app_runtime_deps + [
            "//tools/base/profiler/app:perfa_java",
            "//tools/base/profiler/native/agent:libsupportjni.so",
        ],
        tags = tags,
        size = size,
        jvm_flags = jvm_flags,
    )
