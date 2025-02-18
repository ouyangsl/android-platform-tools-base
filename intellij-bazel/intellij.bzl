"""
 A set of rules to develop stand alone IJ plguins.

This initially is implemented as a bridge to Android Studio rules
"""

load("//tools/adt/idea/studio:studio.bzl", "LINUX", "PluginInfo", "studio_plugin")
load("//tools/base/bazel:functions.bzl", "create_option_file")

PlatformConfigInfo = provider(fields = ["platform"])

def _impl(ctx):
    return PlatformConfigInfo(platform = ctx.build_setting_value)

intellij_platform_setting = rule(
    implementation = _impl,
    build_setting = config.string(flag = True),
)

def _intellij_platform_transition_impl(settings, attr):
    return [{"//tools/base/intellij-bazel:intellij_platform": p} for p in attr.platforms]

intellij_platform_transition = transition(
    implementation = _intellij_platform_transition_impl,
    inputs = [],
    outputs = ["//tools/base/intellij-bazel:intellij_platform"],
)

def _stamp(ctx, args, srcs, src, out):
    args.add("--stamp")
    args.add(src)
    args.add(out)
    ctx.actions.run(
        inputs = srcs + [src],
        outputs = [out],
        executable = ctx.executable._stamper,
        arguments = [args],
        progress_message = "Stamping %s" % src.basename,
        mnemonic = "stamper",
    )

def _platform_intellij_plugin_impl(ctx):
    info = ctx.attr.plugin[PluginInfo]
    files = LINUX.get(info.plugin_files)
    new_files = {}
    for path, file in files.items():
        new_files[path] = file
        if path.endswith(".jar"):
            stamped_jar = ctx.actions.declare_file(ctx.attr.name + ".stamped." + path.replace("/", "_"))
            args = ctx.actions.args()

            args.add("--entry", "META-INF/plugin.xml")
            args.add("--optional_entry")
            args.add("--build_txt", ctx.file._build_txt)
            args.add("--overwrite_since_until_builds")
            _stamp(ctx, args, [ctx.file._build_txt], file, stamped_jar)

            new_files[path] = stamped_jar
    return [PluginInfo(
        directory = info.directory,
        plugin_files = struct(
            linux = new_files,
        ),
        plugin_metadata = info.plugin_metadata,
        module_deps = info.module_deps,
        lib_deps = info.lib_deps,
        licenses = info.licenses,
        overwrite_plugin_version = info.overwrite_plugin_version,
        platform = info.platform,
    )]

_platform_intellij_plugin = rule(
    attrs = {
        "plugin": attr.label(
            providers = [PluginInfo],
        ),
        "_stamper": attr.label(
            default = Label("//tools/adt/idea/studio:stamper"),
            cfg = "exec",
            executable = True,
        ),
        "_build_txt": attr.label(
            default = Label("@intellij//:build-txt"),
            allow_single_file = True,
        ),
    },
    implementation = _platform_intellij_plugin_impl,
)

def _intellij_plugin_impl(ctx):
    default_files = []
    for platform_plugin in ctx.attr.plugin:
        info = platform_plugin[PluginInfo]
        platform_provider = info.platform[PlatformConfigInfo]
        if platform_provider.platform not in ctx.attr.platforms:
            continue
        files = LINUX.get(info.plugin_files)
        map = []
        inputs = []
        for path, file in files.items():
            if not path.startswith("plugins/"):
                error = "File %s, expected to be in the plugins directory." % path
                fail(error)
            path = path[len("plugins/"):]

            map.append((path, file))
            inputs.append(file)

        out = ctx.actions.declare_file(ctx.attr.name + "-" + platform_provider.platform + ".zip")
        zipper_files = [r + "=" + (f.path if f else "") + "\n" for r, f in map]
        zipper_list = create_option_file(ctx, out.basename + ".res.lst", "".join(zipper_files))
        ctx.actions.run(
            inputs = inputs + [zipper_list],
            outputs = [out],
            executable = ctx.executable._zipper,
            arguments = ["c", out.path, "@" + zipper_list.path],
            progress_message = "Creating %s zip..." % ctx.attr.name,
            mnemonic = "zipper",
        )
        default_files.append(out)

    return [DefaultInfo(files = depset(default_files))]

_intellij_plugin = rule(
    attrs = {
        "plugin": attr.label(
            providers = [PluginInfo],
            cfg = intellij_platform_transition,
        ),
        "platforms": attr.string_list(),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "exec",
            executable = True,
        ),
    },
    implementation = _intellij_plugin_impl,
)

def intellij_plugin(name, plugin_id, platforms, **kwargs):
    studio_plugin(
        name = plugin_id,
        **kwargs
    )
    _platform_intellij_plugin(
        name = "%s.platform" % plugin_id,
        plugin = plugin_id,
    )
    _intellij_plugin(
        name = name,
        plugin = ":%s.platform" % plugin_id,
        platforms = select({
            "@platforms//os:windows": ["studio-sdk"],
            "//conditions:default": platforms,
        }),
        visibility = ["@bazel_tools//tools/whitelists/function_transition_whitelist"] + kwargs.get("visibility", []),
    )

def setup_intellij_platforms(specs):
    all_plugins = {}
    for target, name, plugins in specs:
        for id in plugins:
            all_plugins[id] = id

    redirects = {}
    for target, name, plugins in specs:
        native.config_setting(
            name = name,
            flag_values = {"@//tools/base/intellij-bazel:intellij_platform": name},
        )

        for id in all_plugins.keys():
            if id not in redirects:
                redirects[id] = {}
            redirects[id].update({name: target + "-plugin-" + id})

        api = {
            "intellij-sdk": "",
            "build-txt": "-build-txt",
            "product-info": "-product-info",
            "test-framework": "-test-framework",
            "vm-options": "-vm-options",
            "updater": "-updater",
        }

        for alias, suffix in api.items():
            if alias not in redirects:
                redirects[alias] = {}
            redirects[alias].update({name: target + suffix})

    for name, actuals in redirects.items():
        native.alias(
            name = name,
            actual = select(actuals),
            visibility = ["//visibility:public"],
        )

def _intellij_remote_platform_impl(ctx):
    if not ctx.attr.sha256:
        fail("Downloading without a fixed sha256 is not supported.")

    ctx.download_and_extract(
        url = ctx.attr.url,
        sha256 = ctx.attr.sha256,
        stripPrefix = ctx.attr.top_level_dir,
    )
    ctx.file("WORKSPACE", "workspace(name = \"{name}\")\n".format(name = ctx.name))

    content = "load(':spec.bzl', 'SPEC')\n"
    content += "load('@//tools/adt/idea/studio:studio.bzl', 'intellij_platform_import')\n"
    content += "intellij_platform_import(\n"
    content += "    name = '" + ctx.name + "',\n"
    content += "    spec = SPEC,\n"
    content += ")\n"
    ctx.file("BUILD.bazel", content)
    ctx.execute([ctx.path(ctx.attr.cmd)], quiet = False)

intellij_remote_platform = repository_rule(
    attrs = {
        "sha256": attr.string(),
        "url": attr.string(),
        "cmd": attr.label(),
        "srcs": attr.label_list(),
        "top_level_dir": attr.string(),
    },
    implementation = _intellij_remote_platform_impl,
)

def plugins(spec):
    return spec.plugin_jars.keys()

def _normalize(name):
    return name.replace("-", "_")

# Represents a platform that is checked into the source tree.
#
# Args:
#       name: The name of this platform, to be used in the platforms attribute of intellij_plugin
#       target: The local target where this platform is checked in
#       spec: A reference to the .bzl file that contains the specification of this platform.
def local_platform(name, target, spec):
    return struct(
        name = name,
        target = target,
        spec = spec,
    )

# Represents a platform that is download dynamically
#
# Args:
#       name: The name of this platform, to be used in the platforms attribute of intellij_plugin
#       url: Where to download this platform from
#       sha256: The file's sha256.
#       top_level_dir: Optional in case the archive contains a top-level directory that contains all files in the archive.
#       export_plugins: If true, all plugins in the IDE archive will be available through @intellij//:PLUGIN_NAME.
#                      (Note that setting this to true will always download the IDE.)
#
def remote_platform(name, sha256, url, top_level_dir = None, export_plugins = False):
    return struct(
        name = name,
        sha256 = sha256,
        url = url,
        top_level_dir = top_level_dir,
        export_plugins = export_plugins,
    )

def setup_platforms(repos):
    content = "load('@//tools/base/intellij-bazel:intellij.bzl', 'setup_intellij_platforms')\n"
    targets = []
    for repo in repos:
        if hasattr(repo, "url"):
            intellij_remote_platform(
                name = repo.name,
                cmd = "//tools/adt/idea/studio:mkspec.py",
                srcs = [
                    "//tools/adt/idea/studio:mkspec.py",
                    "//tools/adt/idea/studio:intellij.py",
                ],
                sha256 = repo.sha256,
                url = repo.url,
                top_level_dir = repo.top_level_dir,
            )
            if repo.export_plugins:
                content += "load('" + "@" + repo.name + "//:spec.bzl" + "', " + _normalize(repo.name) + " = 'SPEC')\n"
                targets.append((repo.name, "@" + repo.name + "//:" + repo.name, _normalize(repo.name) + ".plugin_jars.keys()"))
            else:
                targets.append((repo.name, "@" + repo.name + "//:" + repo.name, "[]"))
        elif hasattr(repo, "target"):
            content += "load('" + repo.spec + "', " + _normalize(repo.name) + " = 'SPEC')\n"
            targets.append((repo.name, repo.target, _normalize(repo.name) + ".plugin_jars.keys()"))

    content += "\nsetup_intellij_platforms([\n"
    for name, target, plugins in targets:
        content += "    (\n"
        content += "        '" + target + "',\n"
        content += "        '" + name + "',\n"
        content += "        " + plugins + ",\n"
        content += "    ),\n"
    content += "])\n"

    native.new_local_repository(
        name = "intellij",
        build_file_content = content,
        path = "tools/base/intellij-bazel/intellij",
    )
