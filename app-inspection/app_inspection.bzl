load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load("//tools/base/bazel:merge_archives.bzl", "merge_jars")
load("//tools/base/bazel:proto.bzl", "ProtoPackageInfo", "android_java_proto_library", "java_proto_library")
load("//tools/base/bazel:utils.bzl", "java_jarjar")

def _impl(ctx):
    args = [ctx.file.jar.path] + [ctx.attr.proto_file_name + ":" + ctx.outputs.out.path]

    # Action to call the script.
    ctx.actions.run(
        inputs = [ctx.file.jar],
        outputs = [ctx.outputs.out],
        arguments = args,
        progress_message = "unzipping into %s" % ctx.outputs.out.short_path,
        executable = ctx.executable._unzip_tool,
    )
    return ProtoPackageInfo(
        proto_src = [ctx.outputs.out],
        proto_paths = [ctx.outputs.out.dirname],
    )

_unpack_app_inspection_proto = rule(
    implementation = _impl,
    attrs = {
        "jar": attr.label(allow_single_file = True),
        "proto_file_name": attr.string(mandatory = True),
        "out": attr.output(mandatory = True),
        "_unzip_tool": attr.label(
            executable = True,
            cfg = "host",
            allow_files = True,
            default = Label("//tools/base/bazel:unzipper"),
        ),
    },
)

# rule that unpack and compile proto from a prebuilt artifact provided by androidx
def app_inspection_proto(name, jar, proto_file_name, visibility = None):
    unpack_name = name + "-proto"
    _unpack_app_inspection_proto(
        name = unpack_name,
        proto_file_name = proto_file_name,
        jar = jar,
        out = proto_file_name,
        visibility = visibility,
    )
    java_proto_library(
        name = name + "-nojarjar",
        srcs = [":" + unpack_name],
        grpc_support = True,
        visibility = visibility,
    )
    android_java_proto_library(
        name = name,
        srcs = [":" + unpack_name],
        grpc_support = True,
        visibility = visibility,
    )

# Rule that encapsulates all of the intermediate steps in the building
# of an inspector jar.
#
# This macro expands into several rules named after the *name* of this rule:
#   name-sources_undexed[.jar]
#   name-bundled[.jar]
#   name-bundled_dexed[.jar]
#   name (the final rule that puts everything together)
#
# The resulting jar is named out.jar if out is provided. Otherwise name.jar.
#
# bundle_srcs represents dependencies that need to be bundled with the
# inspector (via jarjar) because they are needed during runtime.
#
# nojarjar_deps contains dependencies that will be included without jarjaring.
# These deps will be able to interact directly with the classes in the app or
# library code (e.g. kotlin.*, kotlinx.coroutines.*) that are renamed in the
# inspector by jarjar.
def app_inspection_jar(
        name,
        proto,
        inspection_resources,
        inspection_resource_strip_prefix,
        bundle_srcs = [],
        out = "",
        d8_flags = [],
        nojarjar_deps = [],
        **kwargs):
    kotlin_library(
        name = name + "-sources_undexed",
        lint_enabled = False,
        **kwargs
    )

    bundle_srcs.append(":" + name + "-sources_undexed")
    java_jarjar(
        name = name + "-bundled",
        srcs = bundle_srcs,
        rules = "//tools/base/app-inspection:jarjar_rules.txt",
    )

    dex_library(
        name = name + "-bundled_dexed",
        flags = d8_flags,
        jars = [
            ":" + name + "-bundled",
            "//tools/base/bazel:studio-proto",
            proto,
        ] + nojarjar_deps,
    )

    native.java_library(
        name = name + "_inspection_resources",
        resource_strip_prefix = inspection_resource_strip_prefix,
        resources = inspection_resources,
    )

    output_name = out
    if (out == ""):
        output_name = name
    merge_jars(
        name = name,
        out = output_name,
        jars = [
            ":" + name + "-bundled_dexed",
            ":" + name + "_inspection_resources",
        ],
    )
