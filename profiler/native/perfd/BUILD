load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary")

filegroup(
    name = "samplers_h",
    srcs = glob([
        "samplers/cpu_thread_sampler.h",
        "samplers/cpu_usage_sampler.h",
        "samplers/memory_usage_sampler.h",
        "samplers/sampler.h",
    ]),
)

filegroup(
    name = "samplers_cc",
    srcs = glob([
        "samplers/cpu_thread_sampler.cc",
        "samplers/cpu_usage_sampler.cc",
        "samplers/memory_usage_sampler.cc",
        "samplers/sampler.cc",
    ]),
)

cc_library(
    name = "perfd_internal_headers",
    hdrs = [
        "profiler_service.h",
        "sessions/session.h",
        "sessions/sessions_manager.h",
        ":samplers_h",
    ],
    copts = [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler",
    ],
    target_compatible_with = select({
        "@platforms//os:windows": ["@platforms//:incompatible"],
        "//conditions:default": [],
    }),
    visibility = ["//visibility:public"],
)

cc_library(
    name = "perfd_commands",
    hdrs = [
        "commands/begin_session.h",
    ],
    visibility = ["//visibility:public"],
)

cc_library(
    name = "perfd",
    srcs = [
        "commands/begin_session.cc",
        "commands/begin_session.h",
        "commands/discover_profileable.cc",
        "commands/discover_profileable.h",
        "commands/end_session.cc",
        "commands/end_session.h",
        "commands/get_cpu_core_config.cc",
        "commands/get_cpu_core_config.h",
        "common_profiler_component.h",
        "perfd.cc",
        "perfd.h",
        "profiler_service.cc",
        "profiler_service.h",
        "sessions/session.cc",
        "sessions/session.h",
        "sessions/sessions_manager.cc",
        "sessions/sessions_manager.h",
        ":samplers_cc",
        ":samplers_h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler/native/perfd",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/profiler/native/perfd/common:trace_commands",
        "//tools/base/profiler/native/perfd/cpu:profiler_cpu",
        "//tools/base/profiler/native/perfd/event:profiler_event",
        "//tools/base/profiler/native/perfd/graphics:profiler_graphics",
        "//tools/base/profiler/native/perfd/memory:profiler_memory",
        "//tools/base/profiler/native/perfd/profileable:profileable_detector",
        "//tools/base/transport/native/daemon",
        "//tools/base/transport/proto:cc_proto",
    ],
)

cc_test(
    name = "perfd_test",
    srcs = [
        "samplers/cpu_thread_sampler_test.cc",
        "samplers/cpu_usage_sampler_test.cc",
        "samplers/memory_usage_sampler_test.cc",
        "samplers/sampler_test.cc",
        "sessions/session_test.cc",
        "sessions/sessions_manager_test.cc",
    ],
    copts = [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler/native/perfd",
    ],
    data = [
        "//tools/base/profiler/native/testdata:perfd/cpu",
    ],
    linkstatic = 1,
    deps = [
        ":perfd",
        "//tools/base/profiler/native/perfd/common:perfd_atrace_tests",
        "//tools/base/profiler/native/test:testutils",
        "//tools/base/transport/native/daemon",
        "//tools/base/transport/proto:cc_proto",
        "@googletest//:gtest_main",
    ],
)
