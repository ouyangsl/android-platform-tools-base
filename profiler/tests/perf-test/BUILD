load(":perf_test.bzl", "perf_test")
load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")

package(
    default_testonly = True,
)

dex_library(
    name = "profiler-service",
    jars = ["//tools/base/profiler/app:studio-profiler"],
)

perf_test(
    name = "MemoryTest",
    size = "medium",
    srcs = [
        "testSrcs/com/android/tools/profiler/memory/UnifiedPipelineJniTest.java",
        "testSrcs/com/android/tools/profiler/memory/UnifiedPipelineMemoryTest.java",
    ],
    app_runtime_deps = ["//tools/base/profiler/tests/test-app:libmemorynativetest.so"],
    test_app = "//tools/base/profiler/tests/test-app:test-app",
    deps = ["@maven//:com.google.truth.truth"],
)

perf_test(
    name = "EventTest",
    size = "large",
    srcs = ["testSrcs/com/android/tools/profiler/event/EventProfilerTest.java"],
    test_app = "//tools/base/profiler/tests/test-app:test-app",
    deps = ["@maven//:com.google.truth.truth"],
)

perf_test(
    name = "PerformanceTest",
    size = "large",
    srcs = ["testSrcs/com/android/tools/profiler/performance/LiveAllocationTest.java"],
    test_app = "//tools/base/profiler/tests/test-app:test-app",
    deps = [
        "//tools/base/perf-logger:studio.perf-logger_testlib",
        "@maven//:com.google.truth.truth",
    ],
)

coverage_java_test(
    name = "NativeBinarySizeTest",
    srcs = ["testSrcs/com/android/tools/profiler/performance/NativeBinarySizeTest.java"],
    data = [
        "//tools/base/transport:android/arm64-v8a/transport",
        "//tools/base/transport:android/armeabi-v7a/transport",
        "//tools/base/transport:android/x86/transport",
        "//tools/base/transport/native/agent:android/arm64-v8a/libjvmtiagent.so",
        "//tools/base/transport/native/agent:android/armeabi-v7a/libjvmtiagent.so",
        "//tools/base/transport/native/agent:android/x86/libjvmtiagent.so",
        "//tools/base/transport/native/agent:android/x86_64/libjvmtiagent.so",
    ],
    jvm_flags = ["-Dtest.suite.jar=NativeBinarySizeTest.jar"],
    tags = ["noci:studio-win"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        "//tools/base/perf-logger:studio.perf-logger_testlib",
        "//tools/base/testutils:studio.android.sdktools.testutils_testlib",
        "@maven//:com.google.guava.guava",
        "@maven//:junit.junit",
    ],
)
