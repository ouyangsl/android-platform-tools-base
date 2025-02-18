def _impl(repository_ctx):
    s = "bazel_version = \"" + native.bazel_version + "\""
    repository_ctx.file("bazel_version.bzl", s)
    repository_ctx.file("BUILD", "")

# Helper rule for getting the bazel version. Required by com_google_absl.
bazel_version_repository = repository_rule(
    implementation = _impl,
    local = True,
)

# Bazel repository mapped to git repositories.
_git = [
    # TODO(b/340640065): Perfetto relies on a load() for @perfetto_cfg that
    # cannot be overridden with bzlmod.
    {
        "name": "perfetto",
        "path": "external/perfetto",
        "repo_mapping": {
            "@com_google_protobuf": "@com_google_protobuf",
        },
    },
    {
        "name": "perfetto_cfg",
        "path": "tools/base/bazel/perfetto_cfg",
        "build_file_content": "",
    },
    # TODO: Migrate users of @perfetto_repo to @perfetto
    {
        "name": "perfetto_repo",
        "build_file": "//tools/base/profiler:native/external/perfetto.BUILD",
        "path": "external/perfetto",
    },
    # TODO(b/340640065): Must be moved with @maven.
    {
        "name": "android_system_logging_repo",
        "build_file": "//tools/base/bazel:external/android_system_logging.BUILD",
        "path": "external/android/system/logging",
    },
]

# Vendor repository mapped to git repositories.
_vendor_git = [
    # Use the Android SDK specified by the ANDROID_HOME variable (specified in
    # platform_specific.bazelrc)
    {
        "name": "androidsdk",
        "build_tools_version": "30.0.3",
        "api_level": 34,
    },
    {
        "name": "skia_repo",
        "path": "external/skia",
        "repo_mapping": {
            "@freetype": "@freetype_repo",
            "@libpng": "@libpng_repo",
        },
    },
    {
        "name": "skia_user_config",
        "path": "tools/vendor/google/skia/external/skia-user-config",
    },
]

# Bazel repository mapped to archive files, containing the sources.
_archives = [
    {
        # Offical proto rules relies on a hardcoded "@com_google_protobuf", so we cannot
        # name this as protobuf-3.9.0 or similar.
        "name": "com_google_protobuf",
        "archive": "//prebuilts/tools/common/external-src-archives/protobuf/3.9.0:protobuf-3.9.0.tar.gz",
        "strip_prefix": "protobuf-3.9.0",
        "repo_mapping": {
            "@zlib": "@zlib_repo",
        },
    },
    # Perfetto Dependencies:
    # These are external dependencies to build Perfetto (from external/perfetto)
    {
        # https://github.com/google/perfetto/blob/063034c1deea22dced25d8714fd525e3a8a120d3/bazel/deps.bzl#L59
        "name": "perfetto-jsoncpp-1.0.0",
        "archive": "//prebuilts/tools/common/external-src-archives/jsoncpp/1.9.3:jsoncpp-1.9.3.tar.gz",
        "strip_prefix": "jsoncpp-1.9.3",
        "build_file": "@perfetto//bazel:jsoncpp.BUILD",
    },
    {
        "name": "perfetto-linenoise-c894b9e",
        "archive": "//prebuilts/tools/common/external-src-archives/linenoise/c894b9e:linenoise.git-c894b9e.tar.gz",
        "build_file": "@perfetto//bazel:linenoise.BUILD",
    },
    {
        "name": "perfetto-sqlite-amalgamation-3450300",
        "archive": "//prebuilts/tools/common/external-src-archives/sqlite-amalgamation/3450300:sqlite-amalgamation-3450300.zip",
        "strip_prefix": "sqlite-amalgamation-3450300",
        "build_file": "@perfetto//bazel:sqlite.BUILD",
    },
    {
        "name": "perfetto-sqlite-src-3450300",
        "archive": "//prebuilts/tools/common/external-src-archives/sqlite-src/3450300:sqlite-src-3450300.zip",
        "strip_prefix": "sqlite-src-3450300",
        "build_file": "@perfetto//bazel:sqlite.BUILD",
    },
    {
        "name": "perfetto-llvm-project-3b4c59c156919902c785ce3cbae0eee2ee53064d",
        "archive": "//prebuilts/tools/common/external-src-archives/perfetto-llvm/3b4c59c156919902c785ce3cbae0eee2ee53064d:llvm-3b4c59c156919902c785ce3cbae0eee2ee53064d.tgz",
        "strip_prefix": "llvm-project",
        "build_file": "@perfetto//bazel:llvm_demangle.BUILD",
    },
    # End Perfetto Dependencies.
]

# Needed for grpc.
# TODO(b/340640065): These binds currently use canonical repository names, which is not recommended.
# Binds should be removed entirely.
_binds = {
    "protobuf_clib": "@com_google_protobuf//:protoc_lib",
    "nanopb": "@@_main~_repo_rules~nanopb_repo//:nanopb",
    "madler_zlib": "@@_main~_repo_rules~zlib_repo//:zlib",
    "protobuf_headers": "@com_google_protobuf//:protobuf_headers",
    "protoc": "@com_google_protobuf//:protoc",
}

def _local_archive_impl(ctx):
    """Implementation of local_archive rule."""

    # Extract archive to the root of the repository.
    path = ctx.path(ctx.attr.archive)
    ctx.extract(path, "", ctx.attr.strip_prefix)

    # Set up WORKSPACE to create @{name}// repository:
    ctx.file("WORKSPACE", 'workspace(name = "{}")\n'.format(ctx.name))

    # Link optional BUILD file:
    if ctx.attr.build_file:
        ctx.delete("BUILD.bazel")
        ctx.symlink(ctx.attr.build_file, "BUILD.bazel")
    elif ctx.attr.build_file_content:
        ctx.file(
            "BUILD.bazel",
            content = ctx.attr.build_file_content,
        )

# We're using a custom repository_rule instead of a regular macro (calling
# http_archive for example) because we need access to the repository_ctx object
# in order to proper resolve the path of the archives we want to extract to
# set up the repos.
#
# http_archive works nicely with absolute paths and urls, but fails to resolve
# path to labels or proper resolve relative paths to the workspace root.
local_archive = repository_rule(
    implementation = _local_archive_impl,
    attrs = {
        "archive": attr.label(
            mandatory = True,
            allow_single_file = True,
            doc = "Label for the archive that contains the target.",
        ),
        "strip_prefix": attr.string(doc = "Optional path prefix to strip from the extracted files."),
        "build_file": attr.label(
            allow_single_file = True,
            doc = "Optional label for a BUILD file to be used when setting the repository.",
        ),
        "build_file_content": attr.string(),
    },
)

def _vendor_repository_impl(repository_ctx):
    setup_vendor = repository_ctx.os.environ["SETUP_VENDOR"] in ["1", "True", "TRUE"] if "SETUP_VENDOR" in repository_ctx.os.environ else True
    s = ""
    if setup_vendor:
        s = repository_ctx.read(repository_ctx.path(repository_ctx.attr.bzl))
    else:
        s = "def " + repository_ctx.attr.function + "(): pass"
    repository_ctx.file("vendor.bzl", s)
    repository_ctx.file("BUILD", "")

# Helper rule for getting conditional workspace execution. Required for AOSP builds
vendor_repository = repository_rule(
    implementation = _vendor_repository_impl,
    environ = ["SETUP_VENDOR"],
    local = True,
    attrs = {
        "bzl": attr.label(doc = "Relative path to the bzl to load."),
        "function": attr.string(doc = "The function to import."),
    },
)

def setup_vendor_repos():
    _setup_git_repos(_vendor_git)

def setup_external_repositories(prefix = ""):
    _setup_git_repos(_git, prefix)
    _setup_archive_repos(prefix)
    _setup_binds()

def _setup_git_repos(repos, prefix = ""):
    for _repo in repos:
        repo = dict(_repo)
        if repo["name"] == "androidsdk":
            native.android_sdk_repository(**repo)
        else:
            repo["path"] = prefix + repo["path"]
            if "build_file" in repo:
                repo["build_file"] = prefix + repo["build_file"]
                native.new_local_repository(**repo)
            elif "build_file_content" in repo:
                native.new_local_repository(**repo)
            else:
                native.local_repository(**repo)

def _setup_archive_repos(prefix = ""):
    for _repo in _archives:
        repo = dict(_repo)
        local_archive(**repo)

def _setup_binds():
    for name, actual in _binds.items():
        native.bind(name = name, actual = actual)
