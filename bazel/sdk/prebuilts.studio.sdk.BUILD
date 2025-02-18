load("//tools/base/bazel/sdk:sdk_utils.bzl", "platform_filegroup", "sdk_glob")

filegroup(
    name = "licenses",
    srcs = sdk_glob(
        include = ["licenses/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/latest",
    srcs = [":build-tools/35.0.0"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/35.0.0",
    srcs = glob(
        include = ["*/build-tools/35.0.0/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/34.0.0",
    srcs = glob(
        include = ["*/build-tools/34.0.0/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/33.0.1",
    srcs = glob(
        include = ["*/build-tools/33.0.1/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/30.0.3",
    srcs = sdk_glob(
        include = ["build-tools/30.0.3/**"],
    ),
    visibility = [
        "//tools/adt/idea/android/integration:__pkg__",
        "//tools/adt/idea/app-inspection/integration:__pkg__",
        "//tools/adt/idea/build-attribution:__pkg__",
        "//tools/adt/idea/compose-designer:__pkg__",
        "//tools/adt/idea/designer:__pkg__",
        "//tools/adt/idea/layout-inspector:__pkg__",
        "//tools/adt/idea/old-agp-tests:__pkg__",
        "//tools/adt/idea/profilers-integration:__pkg__",
        "//tools/adt/idea/project-system-gradle:__pkg__",
        "//tools/adt/idea/project-system-gradle-upgrade:__pkg__",
        "//tools/adt/idea/sync-perf-tests:__pkg__",
        "//tools/gradle-recipes:__pkg__",
    ],
)

filegroup(
    name = "build-tools/30.0.2",
    srcs = glob(
        include = ["*/build-tools/30.0.2/**"],
    ),
    visibility = [
        "//tools/adt/idea/old-agp-tests:__pkg__",
        "//tools/adt/idea/project-system-gradle-upgrade:__pkg__",
    ],
)

filegroup(
    name = "build-tools/29.0.2",
    srcs = sdk_glob(
        include = ["build-tools/29.0.2/**"],
    ),
    visibility = [
        "//prebuilts/studio/buildbenchmarks:__pkg__",
        "//tools/adt/idea/old-agp-tests:__pkg__",
        "//tools/adt/idea/project-system-gradle-upgrade:__pkg__",
        "//tools/adt/idea/sync-perf-tests:__pkg__",
        "//tools/base/build-system/previous-versions:__pkg__",
    ],
)

filegroup(
    name = "build-tools/28.0.3",
    srcs = sdk_glob(
        include = ["build-tools/28.0.3/**"],
    ),
    visibility = [
        "//prebuilts/studio/buildbenchmarks:__pkg__",
        "//tools/adt/idea/old-agp-tests:__pkg__",
        "//tools/adt/idea/sync-perf-tests:__pkg__",
        "//tools/vendor/google/android-ndk:__pkg__",
    ],
)

filegroup(
    name = "build-tools/28.0.0",
    srcs = sdk_glob(
        include = ["build-tools/28.0.0/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/27.0.3",
    srcs = sdk_glob(
        include = ["build-tools/27.0.3/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/27.0.1",
    srcs = sdk_glob(
        include = ["build-tools/27.0.1/**"],
    ),
    visibility = [
        "//tools/base/build-system/integration-test:__subpackages__",
    ],
)

filegroup(
    name = "build-tools/27.0.0",
    srcs = sdk_glob(
        include = ["build-tools/27.0.0/**"],
    ),
    visibility = [
        "//tools/base/build-system/integration-test:__subpackages__",
    ],
)

filegroup(
    name = "build-tools/26.0.2",
    srcs = sdk_glob(
        include = ["build-tools/26.0.2/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "build-tools/26.0.0",
    srcs = sdk_glob(
        include = ["build-tools/26.0.0/**"],
    ),
    visibility = [
        "//tools/base/build-system/gradle-core:__pkg__",
    ],
)

filegroup(
    name = "platform-tools",
    srcs = sdk_glob(
        include = ["platform-tools/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "platforms/latest_build_only",
    srcs = [":platforms/android-34_build_only"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "platforms/latest",
    srcs = [":platforms/android-34"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "platforms/latest-preview",
    srcs = [":platforms/android-34"],  # Currently there isn't a preview available
    visibility = ["//visibility:public"],
)

filegroup(
    name = "patcher/v4",
    srcs = glob(
        include = ["*/patcher/v4/**"],
    ),
    visibility = ["//visibility:public"],
)

# Use this target to compile against.
# Note: these stubbed classes will not be available at runtime.
java_import(
    name = "platforms/latest_jar",
    jars = ["@androidsdk//:platforms/android-34/android.jar"],
    neverlink = 1,
    visibility = [
        "//tools/adt/idea/streaming/screen-sharing-agent:__pkg__",
        "//tools/base/adb-proxy/reverse-daemon:__pkg__",
        "//tools/base/app-inspection/agent:__pkg__",
        "//tools/base/app-inspection/inspectors:__subpackages__",
        "//tools/base/deploy/agent/runtime:__pkg__",
        "//tools/base/dynamic-layout-inspector/agent:__subpackages__",
        "//tools/base/experimental/live-sql-inspector:__pkg__",
        "//tools/base/profiler/app:__pkg__",
    ],
)

# Use this target for tests that need the presence of the android classes during test runs.
# Note: these are stubbed classes.
java_import(
    name = "platforms/latest_runtime_jar",
    testonly = 1,
    jars = ["@androidsdk//:platforms/android-34/android.jar"],
    visibility = [
        "//tools/base/app-inspection/inspectors:__subpackages__",
        "//tools/base/dynamic-layout-inspector/agent:__subpackages__",
    ],
)

platform_filegroup(
    name = "platforms/android-35",
    visibility = ["//visibility:public"],
)

platform_filegroup(
    name = "platforms/android-34",
    visibility = ["//visibility:public"],
)

platform_filegroup(
    name = "platforms/android-33",
    visibility = ["//visibility:public"],
)

# Version-specific rule public while tests transition to platform 32
platform_filegroup(
    name = "platforms/android-32",
    visibility = ["//visibility:public"],
)

# Version-specific rule public while tests transition to platform 32
platform_filegroup(
    name = "platforms/android-31",
    visibility = ["//visibility:public"],
)

platform_filegroup(
    name = "platforms/android-30",
    #visibility = ["//visibility:private"],
    visibility = [
        "//tools/adt/idea/debuggers:__pkg__",
        "//tools/base/build-system/integration-test:__subpackages__",
    ],
)

platform_filegroup(
    name = "platforms/android-29",
    visibility = [
        "//prebuilts/studio/buildbenchmarks:__pkg__",
        "//tools/base/build-system/integration-test:__subpackages__",
    ],
)

platform_filegroup(
    name = "platforms/android-28",
    visibility = [
        "//prebuilts/studio/buildbenchmarks:__pkg__",
        "//tools/adt/idea/old-agp-tests:__pkg__",
        "//tools/vendor/google/lldb-integration-tests:__pkg__",
    ],
)

platform_filegroup(
    name = "platforms/android-27",
    # TODO: Restrict the visibility of this group. Although the comment above says "private", the default
    # visibility is public.
)

platform_filegroup(
    name = "platforms/android-25",
    visibility = [
        "//tools/adt/idea/android-uitests:__pkg__",
        "//tools/vendor/google/android-apk:__subpackages__",
    ],
)

platform_filegroup(
    name = "platforms/android-24",
    visibility = [
        "//tools/adt/idea/android-uitests:__pkg__",
        "//tools/base/build-system/gradle-core:__pkg__",
        "//tools/base/build-system/integration-test:__subpackages__",
        "//tools/data-binding:__pkg__",
    ],
)

platform_filegroup(
    name = "platforms/android-23",
    visibility = [
        "//tools/adt/idea/android-uitests:__pkg__",
        "//tools/base/build-system/integration-test:__subpackages__",
    ],
)

platform_filegroup(
    name = "platforms/android-21",
    visibility = ["//tools/base/build-system/integration-test:__subpackages__"],
)

platform_filegroup(
    name = "platforms/android-19",
    visibility = ["//tools/base/build-system/integration-test:__subpackages__"],
)

filegroup(
    name = "emulator",
    srcs = select({
        "//tools/base/bazel/platforms:macos-arm64": [":emulator-arm64"],
        "//conditions:default": [":emulator-x86_64"],
    }),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "emulator-x86_64",
    srcs = sdk_glob(include = ["emulator/**"]),
)

filegroup(
    name = "emulator-arm64",
    srcs = sdk_glob(include = ["emulator-arm64/**"]),
)

filegroup(
    name = "add-ons/addon-google_apis-google-latest",
    srcs = ["add-ons/addon-google_apis-google-24"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "add-ons/addon-google_apis-google-24",
    srcs = sdk_glob(["add-ons/addon-google_apis-google-24/**"]),
)

filegroup(
    name = "docs",
    srcs = sdk_glob(["docs/**"]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "ndk-bundle",
    srcs = sdk_glob(
        include = ["ndk-bundle/**"],
        exclude = [
            "ndk-bundle/platforms/android-19/**",
            "ndk-bundle/platforms/android-21/**",
        ],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "ndk",
    srcs = sdk_glob(
        include = ["ndk/27.0.12077973/**"],
        exclude = [
            "ndk/27.0.12077973/**/*.pyc",
            # Bazel can't handle paths with spaces in them.
            "ndk/27.0.12077973/toolchains/llvm/prebuilt/linux-x86_64/python3/lib/python3.11/site-packages/setuptools/command/launcher manifest.xml",
            "ndk/27.0.12077973/toolchains/llvm/prebuilt/linux-x86_64/python3/lib/python3.11/site-packages/setuptools/script (dev).tmpl",
            "ndk/27.0.12077973/toolchains/llvm/prebuilt/darwin-x86_64/python3/lib/python3.11/site-packages/setuptools/command/launcher manifest.xml",
            "ndk/27.0.12077973/toolchains/llvm/prebuilt/darwin-x86_64/python3/lib/python3.11/site-packages/setuptools/script (dev).tmpl",
        ],
    ),
    visibility = ["//visibility:public"],
)

# NDK r20b is used for AGP tests that require RenderScript support.
filegroup(
    name = "ndk-20",
    srcs = sdk_glob(
        include = ["ndk/20.1.5948944/**"],
        exclude = ["ndk/20.1.5948944/**/*.pyc"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "cmake",
    srcs = sdk_glob(
        include = ["cmake/**"],
        exclude = ["cmake/**/Help/**"],
    ),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "sources",
    srcs = sdk_glob(["sources/**"]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "instant-apps-sdk",
    srcs = sdk_glob(
        include = ["extras/google/instantapps/**"],
    ),
    visibility = ["//visibility:public"],
)
