load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load(":deployer_test.bzl", "deployer_test")

package(default_testonly = True)

deployer_test(
    name = "DesugarTest",
    srcs = ["java/com/android/tools/deployer/DesugarTest.java"],
)

deployer_test(
    name = "FailureTest",
    srcs = ["java/com/android/tools/deployer/FailureTest.java"],
)

deployer_test(
    name = "KotlinTest",
    srcs = ["java/com/android/tools/deployer/KotlinTest.java"],
)

deployer_test(
    name = "SimpleTest",
    srcs = ["java/com/android/tools/deployer/SimpleTest.java"],
)

deployer_test(
    name = "RClassTest",
    srcs = ["java/com/android/tools/deployer/RClassTest.java"],
)

deployer_test(
    name = "JetPackComposeTest",
    srcs = ["java/com/android/tools/deployer/JetPackComposeTest.java"],
)

deployer_test(
    name = "StaticVarInitTest",
    srcs = ["java/com/android/tools/deployer/StaticVarInitTest.java"],
)

deployer_test(
    name = "VirtualTest",
    srcs = ["java/com/android/tools/deployer/VirtualTest.java"],
)

deployer_test(
    name = "CrashLogTest",
    srcs = ["java/com/android/tools/deployer/CrashLogTest.java"],
)

deployer_test(
    name = "LiveLiteralUpdateTest",
    srcs = ["java/com/android/tools/deployer/LiveLiteralUpdateTest.java"],
)

deployer_test(
    name = "LiveEditInstrumentationTest",
    srcs = ["java/com/android/tools/deployer/LiveEditInstrumentationTest.java"],
)

deployer_test(
    name = "LiveEditBasicTest",
    srcs = ["java/com/android/tools/deployer/LiveEditBasicTest.java"],
)

deployer_test(
    name = "LiveEditRecomposeCrashTest",
    srcs = ["java/com/android/tools/deployer/LiveEditRecomposeCrashTest.java"],
)

deployer_test(
    name = "LiveEditVersionCheckTest",
    srcs = ["java/com/android/tools/deployer/LiveEditVersionCheckTest.java"],
)

deployer_test(
    name = "LiveEditAccessorTest",
    srcs = ["java/com/android/tools/deployer/LiveEditAccessorTest.java"],
)

java_test(
    name = "D8DexSplitterTest",
    srcs = ["java/com/android/tools/deployer/D8DexSplitterTest.java"],
    data = [
        "//tools/base/deploy/test/data/apk1:apk",
        "//tools/base/deploy/test/data/apk2:apk",
    ],
    jvm_flags = [
        "-Dapk1.location=$(location //tools/base/deploy/test/data/apk1:apk)",
        "-Dapk2.location=$(location //tools/base/deploy/test/data/apk2:apk)",
    ],

    # Does not seem to be able to locate
    # the two data dependencies on Windows.
    tags = ["noci:studio-win"],
    deps = [
        "//tools/base/deploy/deployer:tools.deployer",
        "@maven//:junit.junit",
    ],
)

dex_library(
    name = "original_dex",
    # Test dex compiles with a non-release build.
    # Also make it desugar as much as possible with API 23.
    flags = [
        "--debug",
        "--min-api 23",
    ],
    jars = [":original_java_deploy.jar"],
)

java_binary(
    name = "original_java",
    srcs = glob([
        "data/java/*.java",
        "data/java_common/*.java",
    ]),
    create_executable = 0,
    deps = [
        ":original_kotlin",
        "//tools/base/fakeandroid:android-mock",
    ],
)

kotlin_library(
    name = "original_kotlin",
    testonly = True,  # Needed to be explicit for macro to pickup
    srcs = glob([
        "data/kotlin/**/*.kt",
        "data/kotlin/**/*.java",
    ]),
)

dex_library(
    name = "swapped_dex",
    flags = [
        "--file-per-class",
        "--debug",
        "--min-api 23",
    ],
    jars = [":swapped_java_deploy.jar"],
)

java_binary(
    name = "swapped_java",
    srcs = glob([
        "data/java_swap/*.java",
        "data/java_common/*.java",
    ]),
    create_executable = 0,
    deps = [
        ":swapped_kotlin",
        "//tools/base/fakeandroid:android-mock",
    ],
)

kotlin_library(
    name = "swapped_kotlin",
    testonly = True,  # Needed to be explicit for macro to pickup
    srcs = glob([
        "data/kotlin_swap/**/*.kt",
        "data/kotlin_swap/**/*.java",
    ]),
)
