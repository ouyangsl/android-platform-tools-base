load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/bazel:proto.bzl", "maven_proto_library")
load("//tools/base/bazel:utils.bzl", "replace_manifest")
load("//tools/base/common:version.bzl", "BASE_VERSION")

maven_proto_library(
    name = "lint-checks-proto",
    srcs = glob(["libs/lint-checks/src/main/**/*.proto"]),
    coordinates = "com.android.tools:play-sdk-proto",
    description = "A protobuf message used for Google Play SDK Index",
    notice = "libs/lint-model/NOTICE",
    resource_strip_prefix = "tools/base/lint/libs/lint-checks/src/main/proto/",
    resources = ["libs/lint-checks/src/main/proto/index.proto"],
    version = BASE_VERSION,
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.lint-model",
    srcs = ["libs/lint-model/src/main/java"],
    iml_files = ["libs/lint-model/android.sdktools.lint-model.iml"],
    lint_baseline = "libs/lint-model/lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/base/build-system/builder-model:studio.android.sdktools.builder-model[module]",
        "//tools/adt/idea/.idea/libraries:kxml2",
    ],
)

maven_library(
    name = "tools.lint-model",
    # buildifier: disable=bad-recursive-glob
    srcs = glob([
        "libs/lint-model/src/main/java/**/*.kt",
        "libs/lint-model/src/main/java/**/*.java",
    ]),
    coordinates = "com.android.tools.lint:lint-model",
    description = "Lint build system model",
    module_name = "lint-model",
    notice = "libs/lint-model/NOTICE",
    resource_strip_prefix = "tools/base/lint/libs/lint-model",
    resources = glob(
        include = ["libs/lint-model/src/main/java/**"],
        exclude = [
            "libs/lint-model/src/main/java/**/*.java",
            "libs/lint-model/src/main/java/**/*.kt",
        ],
    ),
    version = BASE_VERSION,
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/sdk-common:tools.sdk-common",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.lint-api",
    srcs = ["libs/lint-api/src/main/java"],
    iml_files = ["libs/lint-api/android.sdktools.lint-api.iml"],
    lint_baseline = "libs/lint-api/lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/adt/idea/.idea/libraries:asm-tools",
        "//tools/base/annotations:studio.android.sdktools.android-annotations",
        "//tools/base/lint:studio.android.sdktools.lint-model",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        "//tools/base/build-system:studio.android.sdktools.manifest-merger",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/.idea/libraries:asm-tools",
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/base/lint:studio.android.sdktools.lint-model[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//tools/base/build-system:studio.android.sdktools.manifest-merger[module]",
    ],
)

maven_library(
    name = "tools.lint-api",
    # TODO: move resources out of java?
    # buildifier: disable=bad-recursive-glob
    srcs = glob([
        "libs/lint-api/src/main/java/**/*.kt",
        "libs/lint-api/src/main/java/**/*.java",
    ]),
    bundled_deps = [
        ":groovy_neverlink",
    ],
    coordinates = "com.android.tools.lint:lint-api",
    description = "API to build lint checks",
    module_name = "lint-api",
    notice = "libs/lint-api/NOTICE",
    resource_strip_prefix = "tools/base/lint/libs/lint-api",
    resources = glob(
        include = ["libs/lint-api/src/main/java/**"],
        exclude = [
            "libs/lint-api/src/main/java/**/*.java",
            "libs/lint-api/src/main/java/**/*.kt",
        ],
    ),
    version = BASE_VERSION,
    visibility = ["//visibility:public"],
    exports = [
        ":tools.lint-model",
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//prebuilts/tools/common/lint-psi/kotlin-compiler",
        "//prebuilts/tools/common/lint-psi/uast",
        "//tools/base/build-system:tools.manifest-merger",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "@maven//:commons-io.commons-io",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
        "@maven//:org.ow2.asm.asm",
        "@maven//:org.ow2.asm.asm-commons",
        "@maven//:org.ow2.asm.asm-tree",
    ],
    deps = [
        "//tools/base/build-system/builder-model",
        "@maven//:com.google.guava.guava",
    ],
)

java_library(
    name = "groovy_neverlink",
    neverlink = 1,
    exports = ["@maven//:org.codehaus.groovy.groovy"],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.lint-checks",
    srcs = ["libs/lint-checks/src/main/java"],
    iml_files = ["libs/lint-checks/android.sdktools.lint-checks.iml"],
    lint_baseline = "libs/lint-checks/lint_baseline.xml",
    resources = ["libs/lint-checks/src/main/resources"],
    visibility = ["//visibility:public"],
    exports = ["//tools/base/lint:studio.android.sdktools.lint-api"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/base/lint:studio.android.sdktools.lint-api[module]",
        "//tools/base/lint:liblint-checks-proto",
    ],
)

maven_library(
    name = "tools.lint-checks",
    # buildifier: disable=bad-recursive-glob
    srcs = glob([
        "libs/lint-checks/src/main/java/**/*.kt",
        "libs/lint-checks/src/main/java/**/*.java",
    ]),
    coordinates = "com.android.tools.lint:lint-checks",
    description = "Checks for Android Lint",
    kotlinc_opts = [
        # Needed to use the Kotlin K2 analysis API (b/308454624)
        "-Xcontext-receivers",
    ],
    lint_baseline = "libs/lint-checks/lint_baseline.xml",
    module_name = "lint-checks",
    notice = "libs/lint-checks/src/main/resources/NOTICE",
    resource_strip_prefix = "tools/base/lint/libs/lint-checks/src/main/resources",
    resources = glob(
        include = ["libs/lint-checks/src/main/resources/**"],
        exclude = ["libs/lint-checks/src/main/resources/NOTICE"],
    ),
    version = BASE_VERSION,
    visibility = ["//visibility:public"],
    exports = [
        ":tools.lint-api",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
    ],
    deps = [
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//prebuilts/tools/common/lint-psi/kotlin-compiler",
        "//prebuilts/tools/common/lint-psi/uast",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/lint:lint-checks-proto",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "@maven//:com.google.code.gson.gson",
        "@maven//:com.google.guava.guava",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:org.ow2.asm.asm-analysis",
    ],
)

maven_library(
    name = "tools.lint-gradle",
    # buildifier: disable=bad-recursive-glob
    srcs = glob([
        "libs/lint-gradle/src/main/java/**/*.kt",
        "libs/lint-gradle/src/main/java/**/*.java",
    ]),
    coordinates = "com.android.tools.lint:lint-gradle",
    description = "Gradle integration for Android Lint",
    lint_baseline = "libs/lint-gradle/lint_baseline.xml",
    module_name = "lint-gradle",
    notice = "libs/lint-gradle/NOTICE",
    version = BASE_VERSION,
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//prebuilts/tools/common/lint-psi/kotlin-compiler",
        "//prebuilts/tools/common/lint-psi/uast",
        "//tools/base/common:tools.common",
        "//tools/base/lint/cli",
        "//tools/base/lint/libs/lint-typedef-remover",
        "@maven//:org.codehaus.groovy.groovy",
    ],
)

kotlin_test(
    name = "tests",
    # buildifier: disable=bad-recursive-glob
    srcs = glob([
        "libs/lint-gradle/src/test/java/**/*.kt",
        "libs/lint-gradle/src/test/java/**/*.java",
    ]),
    data = [
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/annotations",
    ],
    jvm_flags = [
        "-Dtest.suite.jar=tests.jar",
        "-Duser.home=/tmp",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.lint-api",
        ":tools.lint-checks",
        ":tools.lint-gradle",
        ":tools.lint-model",
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//prebuilts/tools/common/lint-psi/kotlin-compiler",
        "//prebuilts/tools/common/lint-psi/uast",
        "//tools/base/annotations",
        "//tools/base/build-system:tools.manifest-merger",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/lint/cli",
        "//tools/base/lint/libs/lint-tests",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.code.gson.gson",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:org.codehaus.groovy.groovy",
        "@maven//:org.jetbrains.annotations",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib",
        "@maven//:org.mockito.mockito-core",
        "@maven//:org.ow2.asm.asm-commons",
        "@maven//:org.ow2.asm.asm-tree",
    ],
)

kotlin_library(
    name = "studio-checks",
    # buildifier: disable=bad-recursive-glob
    srcs = glob([
        "studio-checks/src/main/java/**/*.kt",
        "studio-checks/src/main/java/**/*.java",
    ]),
    lint_baseline = "studio-checks/lint_baseline.xml",
    lint_classpath = ["//tools/base/lint/cli"],
    visibility = ["//visibility:public"],
    deps = [
        ":tools.lint-api",
        ":tools.lint-checks",
        ":tools.lint-model",
        "//prebuilts/tools/common/lint-psi/uast",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
    ],
)

replace_manifest(
    name = "studio-checks.lint-rules",
    manifest = "studio-checks/MANIFEST.MF",
    original_jar = ":studio-checks",
    visibility = ["//visibility:public"],
)

kotlin_test(
    name = "studio-checks-tests",
    # buildifier: disable=bad-recursive-glob
    srcs = glob([
        "studio-checks/src/test/java/**/*.kt",
        "studio-checks/src/test/java/**/*.java",
    ]),
    jvm_flags = [
        "-Dtest.suite.jar=studio-checks-tests.jar",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    visibility = ["//visibility:public"],
    deps = [
        ":studio-checks",
        ":tools.lint-api",
        ":tools.lint-checks",
        "//tools/base/lint/libs/lint-tests",
        "//tools/base/testutils:tools.testutils",
        "@maven//:junit.junit",
    ],
)

exports_files(
    srcs = ["studio-checks/empty_baseline.xml"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.lint.studio-checks",
    srcs = ["studio-checks/src/main/java"],
    iml_files = ["studio-checks/android.sdktools.lint.studio-checks.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/lint:studio.android.sdktools.lint-api[module]",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module]",
        "@intellij//:org.jetbrains.kotlin",
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.lint.studio-checks.tests",
    iml_files = ["studio-checks/android.sdktools.lint.studio-checks.tests.iml"],
    test_srcs = ["studio-checks/src/test/java"],
    # The canonical test target for this is :studio-checks-tests (//tools/base/lint:studio-checks-tests)
    # as that runs with the correct dependencies. This iml_module is only defined for development.
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/lint/cli:studio.android.sdktools.lint.cli[module]",
        "//tools/base/lint:studio.android.sdktools.lint-api[module]",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module]",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/base/lint/libs/lint-tests:studio.android.sdktools.lint.testinfrastructure[module, test]",
        "//tools/base/lint:studio.android.sdktools.lint.studio-checks[module, test]",
        "@intellij//:intellij-sdk[test]",
        "@intellij//:com.intellij.java",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.lint.gradle",
    srcs = ["libs/lint-gradle/src/main/java"],
    iml_files = ["libs/lint-gradle/android.sdktools.lint.gradle.iml"],
    lint_baseline = "lint_baseline.xml",
    test_tags = ["manual"],  # src/main/java is TEST scoped but contains no runnable tests
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/lint/cli:studio.android.sdktools.lint.cli[module]",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module]",
        "//tools/base/lint/libs/lint-typedef-remover:studio.android.sdktools.lint.typedef-remover[module]",
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.lint.gradle.test",
    iml_files = ["libs/lint-gradle/android.sdktools.lint.gradle.test.iml"],
    test_data = [
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/annotations",
    ],
    test_srcs = ["libs/lint-gradle/src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/base/lint/cli:studio.android.sdktools.lint.cli[module, test]",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module, test]",
        "//tools/base/lint:studio.android.sdktools.lint.gradle[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/lint/libs/lint-tests:studio.android.sdktools.lint.testinfrastructure[module, test]",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "@intellij//:intellij-sdk[test]",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:truth[test]",
    ],
)

# managed by go/iml_to_build
jvm_import(
    name = "liblint-checks-proto",
    jars = ["//tools/base/lint:liblint-checks-proto.lib.jar"],
    visibility = ["//visibility:public"],
)
