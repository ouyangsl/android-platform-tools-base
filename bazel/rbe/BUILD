# Relies on remote execution (RBE) to test the docker container specified
# in the exec_properties of //tools/base/bazel/platforms:linux. This test is
# not guaranteed to pass on a regular linux machine.
java_test(
    name = "docker-image-test",
    srcs = ["DockerImageTest.java"],
    data = [
        "//prebuilts/studio/jdk:jdk_runtime",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//tools/base/build-system/aapt2:aapt2_for_tests",
    ],
    jvm_flags = ["-Djava.security.debug=all"],
    tags = [
        "noci:studio-win",
    ],
    test_class = "DockerImageTest",
    deps = [
        "@maven//:junit.junit",
    ],
)

py_test(
    name = "windows_path_test",
    srcs = ["windows_path_test.py"],
    tags = [
        "noci:studio-linux"
    ],
    deps = [
        "@absl-py//absl/testing:absltest",
    ],
)

# CPU, disk and memory benchmark baseline tests to measure performace of RBE
# machines. See go/adt-perfgate.
java_test(
    name = "perfgate-baseline-tests",
    srcs = ["PerfgateMetrics.java"],
    # The memory test stresses memory usage, thus the jvm memory arg.
    jvm_flags = [
        "-Xms10000M",
        "-Xmx10000M",
    ],
    test_class = "PerfgateMetrics",
    deps = [
        "//tools/base/perf-logger:studio.perf-logger_testlib",
        "@maven//:junit.junit",
    ],
)
