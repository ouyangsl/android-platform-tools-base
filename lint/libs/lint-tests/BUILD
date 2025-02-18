load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")
load("//tools/base/lint/libs/lint-tests:lint_tests.bzl", "lint_test")

maven_library(
    name = "lint-tests",
    # buildifier: disable=bad-recursive-glob
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    coordinates = "com.android.tools.lint:lint-tests",
    description = "API to build lint check tests",
    module_name = "lint-tests",
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/lint/libs/lint-tests",
    version = BASE_VERSION,
    visibility = ["//visibility:public"],
    exports = [
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
    ],
    deps = [
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//prebuilts/tools/common/lint-psi/kotlin-compiler",
        "//prebuilts/tools/common/lint-psi/uast",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/lint/cli",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "@maven//:junit.junit",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:org.codehaus.groovy.groovy",
        "@maven//:org.ow2.asm.asm",
        "@maven//:org.ow2.asm.asm-tree",
    ],
)

lint_test(
    name = "tests",
    useK2 = False,
)

lint_test(
    name = "tests_k2",
    useK2 = True,
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.lint.testinfrastructure",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.lint.testinfrastructure.iml"],
    lint_baseline = "testinfrastructure_lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/lint:studio.android.sdktools.lint-api[module]",
        "//tools/base/lint:studio.android.sdktools.lint-model[module]",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module]",
        "//tools/base/lint/cli:studio.android.sdktools.lint.cli[module]",
        "//tools/adt/idea/.idea/libraries:junit4",
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.lint.tests",
    iml_files = ["android.sdktools.lint.tests.iml"],
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    # The cannonical test target for this is :tests (//tools/base/lint/libs/lint-tests:tests)
    # as that runs with the correct dependencies. This iml_module is only defined for development
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/tools/common/m2:groovy-3.0.9",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/base/lint/cli:studio.android.sdktools.lint.cli[module, test]",
        "//tools/base/lint:studio.android.sdktools.lint-api[module, test]",
        "//tools/base/lint/libs/lint-tests:studio.android.sdktools.lint.testinfrastructure[module, test]",
        "//tools/base/apkparser/analyzer:studio.android.sdktools.analyzer[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/analytics-library/tracker:analytics-tracker[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:truth-java8-extension[test]",
        "//tools/analytics-library/shared:analytics-shared[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin[test]",
        "//tools/base/common:studio.android.sdktools.common.testfixtures[module, test]",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module, test]",
        "@intellij//:intellij-sdk[test]",
        "@intellij//:com.intellij.java",
        "//tools/base/lint/libs/lint-tests:liblint-checks-proto",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//tools/adt/idea/.idea/libraries:agp-version[test]",
    ],
)

# managed by go/iml_to_build
jvm_import(
    name = "liblint-checks-proto",
    jars = ["//tools/base/lint:liblint-checks-proto.lib.jar"],
    visibility = ["//visibility:public"],
)
