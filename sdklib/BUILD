load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:expand_template.bzl", "expand_template")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_library")
load(
    "//tools/base/common:version.bzl",
    "BASE_VERSION",
    "DEV_COMMANDLINE_TOOLS_VERSION",
    "RELEASE_COMMANDLINE_TOOLS_VERSION",
)
load("//tools/base/repository:xjc.bzl", "xsd_to_java")
load(":sdk.bzl", "combine_licenses", "sdk_java_binary", "sdk_package")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.sdklib",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.sdklib.iml"],
    jvm_target = "8",
    lint_baseline = "lint_baseline.xml",
    resources = ["src/main/resources"],
    test_data = ["//prebuilts/studio/sdk:platforms/latest"],
    test_resources = ["src/test/resources"],
    test_srcs = [
        "src/integrationTest/java",
        "src/test/java",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/device_validator:studio.android.sdktools.dvlib",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        "//tools/base/repository:studio.android.sdktools.repository",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/.idea/libraries:agp-version",
        "@intellij//:com.intellij.java",
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/adt/idea/.idea/libraries:guava-testlib[test]",
        "//tools/adt/idea/.idea/libraries:jimfs[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/device_validator:studio.android.sdktools.dvlib[module]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//tools/base/repository:studio.android.sdktools.repository[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
    ],
)

kotlin_library(
    name = "tools.sdklib.core",
    srcs = glob(
        [
            "src/main/java/**/*.java",
            "src/main/java/**/*.kt",
        ],
        exclude = [
            "src/main/java/**/tool/**/*.java",
        ],
    ),
    lint_baseline = "lint_baseline_core.xml",
    module_name = "sdklib",
    resource_strip_prefix = "tools/base/sdklib/src/main/resources",
    resources = glob(["src/main/resources/**"]),
    deps = [
        "//tools/base/common:tools.common",
        "//tools/base/device_validator:tools.dvlib",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "@maven//:com.google.code.gson.gson",
        "@maven//:org.apache.commons.commons-compress",
        "@maven//:org.apache.httpcomponents.httpcore",
        "@maven//:org.apache.httpcomponents.httpmime",
        "@maven//:org.glassfish.jaxb.jaxb-runtime",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect",
    ],
)

maven_library(
    name = "tools.sdklib",
    srcs = [],
    bundled_deps = [
        ":avdmanager_lib",
        ":sdkmanager_lib",
        ":tools.sdklib.core",
    ],
    coordinates = "com.android.tools:sdklib",
    description = "A library to parse and download the Android SDK.",
    notice = "NOTICE",
    version = BASE_VERSION,
    visibility = ["//visibility:public"],
    exports = [
        "//tools/base/repository:tools.repository",
    ],
    deps = [
        "//tools/base/common:tools.common",
        "//tools/base/device_validator:tools.dvlib",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "@maven//:com.google.code.gson.gson",
        "@maven//:org.apache.commons.commons-compress",
        "@maven//:org.apache.httpcomponents.httpcore",
        "@maven//:org.apache.httpcomponents.httpmime",
        "@maven//:org.glassfish.jaxb.jaxb-runtime",
    ],
)

kotlin_test(
    name = "tools.tests",
    srcs = glob([
        "src/test/java/**/*.kt",
        "src/test/java/**/*.java",
    ]),
    data = ["//prebuilts/studio/sdk:platforms/latest"],
    friends = [":tools.sdklib.core"],
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    lint_baseline = "lint_baseline_tests.xml",
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = ["//tools/base/testutils:tools.testutils"],
    deps = [
        ":avdmanager_lib",
        ":sdkmanager_lib",
        ":tools.sdklib.core",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/device_validator:tools.dvlib",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/repository:tools.testlib",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.guava.guava-testlib",
        "@maven//:com.google.jimfs.jimfs",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:org.apache.httpcomponents.httpcore",
        "@maven//:org.mockito.mockito-core",
    ],
)

java_library(
    name = "avdmanager_lib",
    srcs = glob(["src/main/java/**/tool/AvdManagerCli.java"]),
    deps = [
        ":tools.sdklib.core",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "@maven//:com.google.guava.guava",
    ],
)

java_library(
    name = "sdkmanager_lib",
    srcs = glob(["src/main/java/**/tool/sdkmanager/*.java"]),
    deps = [
        ":tools.sdklib.core",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/repository:tools.repository",
        "@maven//:com.google.guava.guava",
    ],
)

sdk_java_binary(
    name = "avdmanager",
    default_jvm_opts = {
        "linux": "-Dcom.android.sdkmanager.toolsdir=$APP_HOME",
        "mac": "-Dcom.android.sdkmanager.toolsdir=$APP_HOME",
        "win": "\"-Dcom.android.sdkmanager.toolsdir=%~dp0\\..\"",
    },
    main_class = "com.android.sdklib.tool.AvdManagerCli",
    runtime_deps = [":avdmanager_lib"],
)

sdk_java_binary(
    name = "sdkmanager",
    default_jvm_opts = {
        "linux": "-Dcom.android.sdklib.toolsdir=$APP_HOME",
        "mac": "-Dcom.android.sdklib.toolsdir=$APP_HOME",
        "win": "\"-Dcom.android.sdklib.toolsdir=%~dp0\\..\"",
    },
    main_class = "com.android.sdklib.tool.sdkmanager.SdkManagerCli",
    runtime_deps = [":sdkmanager_lib"],
)

sdk_java_binary(
    name = "resourceshrinker",
    main_class = "com.android.build.shrinker.ResourceShrinkerCli",
    runtime_deps = ["//tools/base/build-system/shrinker"],
)

sdk_java_binary(
    name = "d8",
    main_class = "com.android.tools.r8.D8",
    runtime_deps = ["//prebuilts/r8"],
)

sdk_java_binary(
    name = "r8",
    main_class = "com.android.tools.r8.R8",
    runtime_deps = ["//prebuilts/r8"],
)

sdk_java_binary(
    name = "retrace",
    main_class = "com.android.tools.r8.retrace.Retrace",
    runtime_deps = ["//prebuilts/r8"],
)

expand_template(
    name = "source_properties",
    out = "source.properties",
    substitutions = select({
        "//tools/base/bazel:release": {
            "{VERSION}": RELEASE_COMMANDLINE_TOOLS_VERSION,
        },
        "//conditions:default": {
            "{VERSION}": DEV_COMMANDLINE_TOOLS_VERSION,
        },
    }),
    template = "tools_source.properties",
)

sdk_package(
    name = "commandlinetools",
    binaries = [
        ":avdmanager",
        ":sdkmanager",
        ":d8",
        ":r8",
        ":retrace",
        ":resourceshrinker",
        "//tools/base/apkparser/cli:apkanalyzer",
        # b/135688047
        # "//tools/base/jobb:jobb",
        "//tools/base/lint/cli:lint",
        "//tools/base/misc/screenshot2:screenshot2",
        "//tools/base/profgen/profgen-cli:profgen",
    ],
    sourceprops = ":source.properties",
    visibility = ["//visibility:public"],
)

test_suite(
    name = "commandlinetoolstest",
    tests = ["//tools/base/sdklib/src/integrationTest:integration-test.test"],
)

xsd_to_java(
    name = "sdk-common-2",
    binding_deps = ["//tools/base/repository:repo-common-02.xjb"],
    catalog = "src/main/resources/xsd/catalog.xml",
    custom_binding = "src/main/resources/xsd/sdk-common-custom-02.xjb",
    episode = "sdk-common-02.xjb",
    package = "com.android.sdklib.repository.generated.common.v2",
    src_location = "tools/base/sdklib/src/main/java",
    xsd = "src/main/resources/xsd/sdk-common-02.xsd",
    deps = [
        ":tools.sdklib.core",
        "//tools/base/repository:tools.repository.lib",
    ],
)

xsd_to_java(
    name = "sdk-repository-2",
    binding_deps = [
        "//tools/base/repository:repo-common-02.xjb",
        "//tools/base/repository:generic-02.xjb",
        ":sdk-common-02.xjb",
    ],
    catalog = "src/main/resources/xsd/catalog.xml",
    package = "com.android.sdklib.repository.generated.repository.v2",
    src_location = "tools/base/sdklib/src/main/java",
    xsd = "src/main/resources/xsd/sdk-repository-02.xsd",
    deps = [
        ":tools.sdklib.core",
        "//tools/base/repository:tools.repository.lib",
    ],
)

xsd_to_java(
    name = "sdk-addon-2",
    binding_deps = [
        "//tools/base/repository:repo-common-02.xjb",
        ":sdk-common-02.xjb",
    ],
    catalog = "src/main/resources/xsd/catalog.xml",
    package = "com.android.sdklib.repository.generated.addon.v2",
    src_location = "tools/base/sdklib/src/main/java",
    xsd = "src/main/resources/xsd/sdk-addon-02.xsd",
    deps = [
        ":tools.sdklib.core",
        "//tools/base/repository:tools.repository.lib",
    ],
)

xsd_to_java(
    name = "sdk-sysimg-2",
    binding_deps = [
        "//tools/base/repository:repo-common-02.xjb",
        ":sdk-common-02.xjb",
    ],
    catalog = "src/main/resources/xsd/catalog.xml",
    package = "com.android.sdklib.repository.generated.sysimg.v2",
    src_location = "tools/base/sdklib/src/main/java",
    xsd = "src/main/resources/xsd/sdk-sys-img-02.xsd",
    deps = [
        ":tools.sdklib.core",
        "//tools/base/repository:tools.repository.lib",
    ],
)

xsd_to_java(
    name = "sdk-sites-list-4",
    binding_deps = ["//tools/base/repository:sources/repo-sites-common-1.xjb"],
    catalog = "src/main/resources/xsd/catalog.xml",
    package = "com.android.sdklib.repository.sources.generated.v4",
    src_location = "tools/base/sdklib/src/main/java",
    xsd = "src/main/resources/xsd/sources/sdk-sites-list-4.xsd",
    deps = [
        ":tools.sdklib.core",
        "//tools/base/repository:tools.repository.lib",
    ],
)

xsd_to_java(
    name = "sdk-common-3",
    binding_deps = ["//tools/base/repository:repo-common-02.xjb"],
    catalog = "src/main/resources/xsd/catalog.xml",
    custom_binding = "src/main/resources/xsd/sdk-common-custom-03.xjb",
    episode = "sdk-common-03.xjb",
    package = "com.android.sdklib.repository.generated.common.v3",
    src_location = "tools/base/sdklib/src/main/java",
    xsd = "src/main/resources/xsd/sdk-common-03.xsd",
    deps = [
        ":tools.sdklib.core",
        "//tools/base/repository:tools.repository.lib",
    ],
)

xsd_to_java(
    name = "sdk-repository-3",
    binding_deps = [
        "//tools/base/repository:repo-common-02.xjb",
        "//tools/base/repository:generic-02.xjb",
        ":sdk-common-03.xjb",
    ],
    catalog = "src/main/resources/xsd/catalog.xml",
    package = "com.android.sdklib.repository.generated.repository.v3",
    src_location = "tools/base/sdklib/src/main/java",
    xsd = "src/main/resources/xsd/sdk-repository-03.xsd",
    deps = [
        ":tools.sdklib.core",
        "//tools/base/repository:tools.repository.lib",
    ],
)

xsd_to_java(
    name = "sdk-addon-3",
    binding_deps = [
        "//tools/base/repository:repo-common-02.xjb",
        ":sdk-common-03.xjb",
    ],
    catalog = "src/main/resources/xsd/catalog.xml",
    package = "com.android.sdklib.repository.generated.addon.v3",
    src_location = "tools/base/sdklib/src/main/java",
    xsd = "src/main/resources/xsd/sdk-addon-03.xsd",
    deps = [
        ":tools.sdklib.core",
        "//tools/base/repository:tools.repository.lib",
    ],
)

xsd_to_java(
    name = "sdk-sysimg-3",
    binding_deps = [
        "//tools/base/repository:repo-common-02.xjb",
        ":sdk-common-03.xjb",
    ],
    catalog = "src/main/resources/xsd/catalog.xml",
    package = "com.android.sdklib.repository.generated.sysimg.v3",
    src_location = "tools/base/sdklib/src/main/java",
    xsd = "src/main/resources/xsd/sdk-sys-img-03.xsd",
    deps = [
        ":tools.sdklib.core",
        "//tools/base/repository:tools.repository.lib",
    ],
)

xsd_to_java(
    name = "sdk-sysimg-4",
    binding_deps = [
        "//tools/base/repository:repo-common-02.xjb",
        ":sdk-common-03.xjb",
    ],
    catalog = "src/main/resources/xsd/catalog.xml",
    package = "com.android.sdklib.repository.generated.sysimg.v4",
    src_location = "tools/base/sdklib/src/main/java",
    xsd = "src/main/resources/xsd/sdk-sys-img-04.xsd",
    deps = [
        ":tools.sdklib.core",
        "//tools/base/repository:tools.repository.lib",
    ],
)

xsd_to_java(
    name = "sdk-sites-list-5",
    binding_deps = ["//tools/base/repository:sources/repo-sites-common-1.xjb"],
    catalog = "src/main/resources/xsd/catalog.xml",
    package = "com.android.sdklib.repository.sources.generated.v5",
    src_location = "tools/base/sdklib/src/main/java",
    xsd = "src/main/resources/xsd/sources/sdk-sites-list-5.xsd",
    deps = [
        ":tools.sdklib.core",
        "//tools/base/repository:tools.repository.lib",
    ],
)

xsd_to_java(
    name = "sdk-sites-list-6",
    binding_deps = ["//tools/base/repository:sources/repo-sites-common-1.xjb"],
    catalog = "src/main/resources/xsd/catalog.xml",
    package = "com.android.sdklib.repository.sources.generated.v6",
    src_location = "tools/base/sdklib/src/main/java",
    xsd = "src/main/resources/xsd/sources/sdk-sites-list-6.xsd",
    deps = [
        ":tools.sdklib.core",
        "//tools/base/repository:tools.repository.lib",
    ],
)
