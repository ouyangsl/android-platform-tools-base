load("//tools/base/intellij-bazel:intellij.bzl", "local_platform", "remote_platform", "setup_platforms")

def setup_intellij_platforms():
    setup_platforms([
        # For AOSP we switch to a "remote" IntelliJ distribution, since our forked prebuilts
        # at prebuilts/studio/intellij-sdk are not yet available in AOSP. This remote distribution
        # does not contain our IntelliJ patches, but that is OK (even preferred) for the
        # JetBrains/android use case. Note: the 'plugins' must be specified explicitly for
        # remote platforms, unlike for local platforms. Probably this could be simplified.
        remote_platform(
            name = "studio-sdk",
            url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/241.15989.150/ideaIC-241.15989.150.zip",
            sha256 = "910a9c8161d8c3729aa30db17edcc1295ab9d8565d60cd1f0b44884824d7c9c2",
            plugins = [
                "Coverage",
                "Git4Idea",
                "HtmlTools",
                "JUnit",
                "Subversion",
                "TestNG-J",
                "com.intellij.completion.ml.ranking",
                "com.intellij.configurationScript",
                "com.intellij.copyright",
                "com.intellij.dev",
                "com.intellij.gradle",
                "com.intellij.java",
                "com.intellij.java-i18n",
                "com.intellij.java.ide",
                "com.intellij.platform.images",
                "com.intellij.plugins.eclipsekeymap",
                "com.intellij.plugins.netbeanskeymap",
                "com.intellij.plugins.visualstudiokeymap",
                "com.intellij.properties",
                "com.intellij.rml.dfa",
                "com.intellij.tasks",
                "com.intellij.turboComplete",
                "com.jetbrains.performancePlugin",
                "com.jetbrains.sh",
                "hg4idea",
                "intellij.webp",
                "org.editorconfig.editorconfigjetbrains",
                "org.intellij.groovy",
                "org.intellij.intelliLang",
                "org.intellij.plugins.markdown",
                "org.jetbrains.debugger.streams",
                "org.jetbrains.idea.maven.model",
                "org.jetbrains.idea.maven.server.api",
                "org.jetbrains.idea.reposearch",
                "org.jetbrains.java.decompiler",
                "org.jetbrains.kotlin",
                "org.jetbrains.plugins.clangConfig",
                "org.jetbrains.plugins.clangFormat",
                "org.jetbrains.plugins.github",
                "org.jetbrains.plugins.gitlab",
                "org.jetbrains.plugins.gradle",
                "org.jetbrains.plugins.terminal",
                "org.jetbrains.plugins.textmate",
                "org.jetbrains.plugins.yaml",
                "org.toml.lang",
            ],
        ),
        remote_platform(
            name = "intellij_ce_2023_3",
            url = "https://www.jetbrains.com/intellij-repository/releases/com/jetbrains/intellij/idea/ideaIC/233.11799.241/ideaIC-233.11799.241.zip",
            sha256 = "eb9993202e137a3cbea81fcd42235ed5519055ab1aa58c3b0b724fb68c22f6d3",
        ),
        remote_platform(
            name = "intellij_ce_2024_1",
            url = "https://www.jetbrains.com/intellij-repository/snapshots/com/jetbrains/intellij/idea/ideaIC/241.14494.17-EAP-SNAPSHOT/ideaIC-241.14494.17-EAP-SNAPSHOT.zip",
            sha256 = "0a18e02b611562b4e81f2f5af570975c153874154ad17fdb026eae3b6818e422",
        ),
    ])
