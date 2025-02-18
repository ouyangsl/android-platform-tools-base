load("//tools/base/fakeandroid:fakeandroid.bzl", "fake_android_test")

def deployer_test(name, srcs):
    fake_android_test(
        name = name,
        size = "medium",
        srcs = srcs + native.glob(
            include = ["java/com/android/tools/deployer/*.java"],
            exclude = ["java/com/android/tools/deployer/*Test.java"],
        ),
        data = [
            ":original_dex",
            ":swapped_dex",
            "//tools/base/deploy/agent/runtime:live_edit_dex",
            "//tools/base/deploy/agent/native:libswap.so",
            "//tools/base/deploy/installer:install-server",
            "//tools/base/deploy/test/data/apk1:apk",
            "//tools/base/deploy/test/data/apk2:apk",
        ],
        jvm_flags = [
            # Location of the inital test app.
            "-Dapp.dex.location=$(location :original_dex)",

            # Location of the inital test app.
            "-Dliveedit.app.dex.location=$(location //tools/base/deploy/agent/runtime:live_edit_dex)",

            # Location of the dex files to be swapped in.
            "-Dapp.swap.dex.location=$(location :swapped_dex)",

            # Location of the original Java classes.
            "-Djava.original.class.location=$(location :original_java)",

            # Location of the original Kotlin classes.
            "-Dkotlin.original.class.location=$(location :original_kotlin)",

            # Location of the swapped Java classes.
            "-Djava.swapped.class.location=$(location :swapped_java)",

            # Location of the swapped Kotlin classes.
            "-Dkotlin.swapped.class.location=$(location :swapped_kotlin)",

            # JVMTI Agent for the host.
            "-Dswap.agent.location=$(location //tools/base/deploy/agent/native:libswap.so)",

            # Install server for communcation with the agent.
            "-Dinstall.server.location=$(location //tools/base/deploy/installer:install-server)",

            # APKs for testing the DexArchiveComparator
            "-Dapk1.location=$(location //tools/base/deploy/test/data/apk1:apk)",
            "-Dapk2.location=$(location //tools/base/deploy/test/data/apk2:apk)",
        ],
        # Live Edit uses JNI which does not compile on Windows because of log target (sys/time.h)
        tags = ["noci:studio-win"],
        deps = [
            ":original_java",
            ":original_kotlin",
            ":swapped_java",
            ":swapped_kotlin",
            "//tools/base/bazel:langtools",
            "//tools/base/bazel:studio-proto",
            "//tools/base/deploy/deployer:tools.deployer",
            "//tools/base/deploy/agent/runtime:live_edit_dex",
            "//tools/base/deploy/proto:deploy_java_proto",
            "//tools/base/fakeandroid",
            "@maven//:com.google.protobuf.protobuf-java",
        ],
    )
