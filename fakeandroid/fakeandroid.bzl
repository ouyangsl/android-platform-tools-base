def fake_android_test(name, srcs = [], deps = [], data = [], runtime_deps = [], tags = [], shard_count = None, size = None, jvm_flags = []):
    native.java_test(
        name = name,
        runtime_deps = runtime_deps + [
            "//tools/base/fakeandroid:art-runner",
            "//tools/base/testutils:tools.testutils",
        ],
        deps = deps + [
            "//tools/base/common:tools.common",
            "//tools/base/fakeandroid:app-launcher-dex",
            "//tools/base/fakeandroid:android-mock-dex",
            "@maven//:com.google.guava.guava",
            "@maven//:junit.junit",
        ],
        jvm_flags = jvm_flags + [
            "-Dtest.suite.jar=" + name + ".jar",
            "-Dart.location=/prebuilts/tools/linux-x86_64/art/bin/art",
            "-Dperfa.dex.location=$(location //tools/base/fakeandroid:app-launcher-dex)",
            "-Dandroid-mock.dex.location=$(location //tools/base/fakeandroid:android-mock-dex)",
            "-Dart.deps.location=prebuilts/tools/linux-x86_64/art/framework/",
            "-Dart.boot.location=prebuilts/tools/linux-x86_64/art/framework/x86_64/",
            "-Dart.lib64.location=prebuilts/tools/linux-x86_64/art/lib64",
        ],
        shard_count = shard_count,
        test_class = "com.android.testutils.JarTestSuite",
        visibility = ["//visibility:public"],
        size = size,
        data = data + [
            "//tools/base/fakeandroid:art-runner",
        ],
        srcs = srcs,
        tags = tags + ["noci:studio-win"],
    )
