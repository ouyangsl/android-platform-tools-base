load("@//tools/base/bazel/toolchains:cc_toolchain_config.bzl", "CLANG_LATEST", "cc_toolchain_config")
load("@bazel_skylib//rules:common_settings.bzl", "bool_flag")

package(default_visibility = ["//visibility:public"])

clang_latest_darwin = CLANG_LATEST["darwin"]

clang_latest_linux = CLANG_LATEST["k8"]

clang_latest_windows = CLANG_LATEST["x64_windows"]

cc_toolchain_suite(
    name = "toolchain",
    toolchains = {
        "k8|compiler": ":cc-compiler-k8",
        "k8": ":cc-compiler-k8",
        "darwin|compiler": ":cc-compiler-darwin",
        "darwin": ":cc-compiler-darwin",
        "darwin_arm64|compiler": ":cc-compiler-darwin",
        "darwin_arm64": ":cc-compiler-darwin",
        "x64_windows|clang-cl": ":cc-compiler-x64_windows-clang-cl",
        "x64_windows": ":cc-compiler-x64_windows-clang-cl",
    },
)

# The latest version of clang contains a BUILD.bazel file, meaning we cannot
# use glob() the same way. Instead, use the filegroups targets directly.
filegroup(
    name = "linux-files",
    srcs = [
        "//" + clang_latest_linux + ":includes",
        "//" + clang_latest_linux + ":binaries",
    ],
)

filegroup(
    name = "windows-files",
    srcs = glob([
        clang_latest_windows + "/bin/*",
        clang_latest_windows + "/lib/**/*",
        clang_latest_windows + "/include/**/*",
    ]),
)

filegroup(
    name = "darwin-files",
    srcs = glob([
        clang_latest_darwin + "/bin/*",
        clang_latest_darwin + "/lib/**/*",
        clang_latest_darwin + "/include/**/*",
    ]),
)

filegroup(
    name = "empty",
    srcs = [],
)

cc_toolchain(
    name = "cc-compiler-k8",
    all_files = ":linux-files",
    ar_files = ":linux-files",
    as_files = ":linux-files",
    compiler_files = ":linux-files",
    dwp_files = ":empty",
    linker_files = ":linux-files",
    objcopy_files = ":empty",
    strip_files = ":empty",
    supports_param_files = 1,
    toolchain_config = ":local_linux",
    toolchain_identifier = "local_linux",
)

cc_toolchain(
    name = "cc-compiler-darwin",
    all_files = ":darwin-files",
    ar_files = ":darwin-files",
    as_files = ":darwin-files",
    compiler_files = ":darwin-files",
    dwp_files = ":darwin-files",
    linker_files = ":darwin-files",
    objcopy_files = ":darwin-files",
    strip_files = ":darwin-files",
    supports_param_files = 0,
    toolchain_config = ":local_darwin",
    toolchain_identifier = "local_darwin",
)

cc_toolchain(
    name = "cc-compiler-x64_windows-clang-cl",
    all_files = ":windows-files",
    ar_files = ":windows-files",
    as_files = ":empty",
    compiler_files = ":windows-files",
    dwp_files = ":windows-files",
    linker_files = ":windows-files",
    objcopy_files = ":empty",
    strip_files = ":empty",
    supports_param_files = 1,
    toolchain_config = ":local_windows",
    toolchain_identifier = "local_windows",
)

cc_toolchain_config(
    name = "local_linux",
    abi_libc_version = "local",
    abi_version = "local",
    compile_flags = [
        "-U_FORTIFY_SOURCE",
        "-fstack-protector",
        "-Wall",
        "-Wthread-safety",
        "-Wself-assign",
        "-fcolor-diagnostics",
        "-fno-omit-frame-pointer",
    ],
    compiler = "compiler",
    coverage_compile_flags = ["--coverage"],
    coverage_link_flags = ["--coverage"],
    cpu = "k8",
    cxx_builtin_include_directories = [
        "/usr/local/include",
        clang_latest_linux + "/lib/clang/19/include",
        "/usr/include/x86_64-linux-gnu",
        "/usr/include",
        "/usr/include/c++/8.0.1",
        "/usr/include/x86_64-linux-gnu/c++/8.0.1",
        "/usr/include/c++/8.0.1/backward",
    ],
    cxx_flags = [
        "-std=c++17",
        "-stdlib=libc++",
    ],
    dbg_compile_flags = ["-g"],
    host_system_name = "local",
    link_flags = [
        "-l:libc++.a",
        "-l:libc++abi.a",
        "-Wl,-no-as-needed",
        "-Wl,-z,relro,-z,now",
        "-lm",
    ],
    link_libs = [],
    opt_compile_flags = [
        "-g0",
        "-O2",
        "-D_FORTIFY_SOURCE=1",
        "-DNDEBUG",
        "-ffunction-sections",
        "-fdata-sections",
        "-fno-exceptions",
    ],
    opt_link_flags = ["-Wl,--gc-sections"],
    target_libc = "local",
    target_system_name = "local",
    tool_paths = {
        "ar": clang_latest_linux + "/bin/llvm-ar",
        "ld": clang_latest_linux + "/bin/ld.lld",
        "cpp": clang_latest_linux + "/bin/clang++",
        "gcc": clang_latest_linux + "/bin/clang",
        "dwp": "None",
        "gcov": "None",
        "nm": clang_latest_linux + "/bin/llvm-nm",
        "objcopy": clang_latest_linux + "/bin/llvm-objcopy",
        "objdump": clang_latest_linux + "/bin/llvm-objdump",
        "strip": clang_latest_linux + "/bin/llvm-strip",
    },
    toolchain_identifier = "local",
    unfiltered_compile_flags = [
        "-no-canonical-prefixes",
        "-Wno-builtin-macro-redefined",
        "-D__DATE__=\"redacted\"",
        "-D__TIMESTAMP__=\"redacted\"",
        "-D__TIME__=\"redacted\"",
    ],
)

cc_toolchain_config(
    name = "local_darwin",
    abi_libc_version = "local",
    abi_version = "local",
    compile_flags = [
        "-D_FORTIFY_SOURCE=1",
        "-fstack-protector",
        "-fcolor-diagnostics",
        "-Wall",
        "-Wthread-safety",
        "-Wself-assign",
        # Needed for Apple's non-conforming code
        # (https://www.mail-archive.com/llvm-bugs@lists.llvm.org/msg49229.html)
        "-Wno-elaborated-enum-base",
        "-fno-omit-frame-pointer",
    ] + select({
        "@platforms//cpu:arm64": ["--target=arm64-apple-macos11"],
        "@platforms//cpu:x86_64": ["--target=x86_64-apple-macos11"],
    }),
    compiler = "compiler",
    coverage_compile_flags = [],  # ?
    coverage_link_flags = [],  # ?
    cpu = "darwin",
    cxx_builtin_include_directories = [
        clang_latest_darwin + "/include/c++/v1",
        clang_latest_darwin + "/lib64/clang/14.0.0/include",
        "/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/usr/include/",
        "/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/System/Library/Frameworks",
        "/usr/include",
    ],
    cxx_flags = [
        "-std=c++17",
        "-stdlib=libc++",
    ],
    dbg_compile_flags = ["-g"],
    host_system_name = "local",
    link_flags = [
        "-lc++",
        "-framework",
        "CoreFoundation",
        "-headerpad_max_install_names",
        "-no-canonical-prefixes",
        "-undefined",
        "dynamic_lookup",
        "-L/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/usr/lib",
        "-F/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/System/Library/Frameworks",
    ] + select({
        "@platforms//cpu:arm64": [
            "--target=arm64-apple-macos11",
        ],
        "@platforms//cpu:x86_64": [
            "--target=x86_64-apple-macos11",
        ],
    }),
    link_libs = [],  # ?
    opt_compile_flags = [
        "-g0",
        "-O2",
        "-DNDEBUG",
        "-ffunction-sections",
        "-fdata-sections",
        "-fno-exceptions",
    ],
    opt_link_flags = [],  # ?
    target_libc = "macosx",
    target_system_name = "local",
    tool_paths = {
        "ar": clang_latest_darwin + "/bin/llvm-ar",
        "cpp": clang_latest_darwin + "/bin/clang++",
        "dwp": "/bin/false",
        "gcc": clang_latest_darwin + "/bin/clang",
        "gcov": clang_latest_darwin + "/bin/llvm-cov",
        "ld": clang_latest_darwin + "/bin/ld64.lld",
        "nm": clang_latest_darwin + "/bin/llvm-nm",
        "objcopy": clang_latest_darwin + "/bin/llvm-objcopy",
        "objdump": clang_latest_darwin + "/bin/llvm-objdump",
        "strip": clang_latest_darwin + "/bin/llvm-strip",
    },
    toolchain_identifier = "local_darwin",
    unfiltered_compile_flags = [
        "-no-canonical-prefixes",
        "-Wno-builtin-macro-redefined",
        "-D__DATE__=\"redacted\"",
        "-D__TIMESTAMP__=\"redacted\"",
        "-D__TIME__=\"redacted\"",
        "-idirafter/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/usr/include/",
        "-F/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/System/Library/Frameworks",
    ],
)

cc_toolchain_config(
    name = "local_windows",
    abi_libc_version = "local",
    abi_version = "local",
    compile_flags = [
        "/DCOMPILER_MSVC",
        "/DNOMINMAX",
        "/D_WIN32_WINNT=0x0601",
        "/D_CRT_SECURE_NO_DEPRECATE",
        "/D_CRT_SECURE_NO_WARNINGS",
        "/bigobj",
        "/Zm500",
        "/EHsc",
        "/wd4351",
        "/wd4291",
        "/wd4250",
        "/wd4996",
    ],
    compiler = "clang-cl",
    cpu = "x64_windows",
    cxx_builtin_include_directories = [
        clang_latest_windows + "/lib/clang/19/include",
        "C:\\Program Files (x86)\\Microsoft Visual Studio\\2022\\BuildTools\\VC\\Tools\\MSVC\\14.40.33807\\include",
        "C:\\Program Files (x86)\\Windows Kits\\10\\include\\10.0.19041.0\\ucrt",
        "C:\\Program Files (x86)\\Windows Kits\\10\\include\\10.0.19041.0\\um",
        "C:\\Program Files (x86)\\Windows Kits\\10\\include\\10.0.19041.0\\shared",
        "C:\\Program Files (x86)\\Windows Kits\\10\\include\\10.0.19041.0\\winrt",
        "C:\\botcode",
    ],
    cxx_flags = ["/std:c++17"],
    host_system_name = "local",
    link_flags = ["/MACHINE:X64"],
    link_libs = [
        "C:\\Program Files (x86)\\Microsoft Visual Studio\\2022\\BuildTools\\VC\\Tools\\MSVC\\14.40.33807\\lib\\x64",
        "C:\\Program Files (x86)\\Windows Kits\\10\\lib\\10.0.19041.0\\ucrt\\x64",
        "C:\\Program Files (x86)\\Windows Kits\\10\\lib\\10.0.19041.0\\um\\x64",
    ],
    target_libc = "msvcrt",
    target_system_name = "local",
    tmp_path = "C:\\Users\\ContainerAdministrator\\AppData\\Local\\Temp",
    tool_paths = {
        "ar": clang_latest_windows + "/bin/llvm-lib.exe",
        "cpp": clang_latest_windows + "/bin/clang-cl.exe",
        "gcc": clang_latest_windows + "/bin/clang-cl.exe",
        "gcov": "wrapper/bin/msvc_nop.bat",
        "ld": clang_latest_windows + "/bin/lld-link.exe",
        "nm": "wrapper/bin/msvc_nop.bat",
        "objcopy": "wrapper/bin/msvc_nop.bat",
        "objdump": "wrapper/bin/msvc_nop.bat",
        "strip": "wrapper/bin/msvc_nop.bat",
        "ml": clang_latest_windows + "/bin/llvm-ml.exe",
    },
    toolchain_identifier = "local_windows",
    unfiltered_compile_flags = [
        "-no-canonical-prefixes",
    ],
)

toolchain(
    name = "cc-toolchain-darwin",
    exec_compatible_with = [
        "@platforms//os:osx",
    ],
    target_compatible_with = [
        "@platforms//os:osx",
    ],
    target_settings = [
        ":enable_legacy_toolchains",
    ],
    toolchain = ":cc-compiler-darwin",
    toolchain_type = "@bazel_tools//tools/cpp:toolchain_type",
)

toolchain(
    name = "cc-toolchain-x64_linux",
    exec_compatible_with = [
        "@platforms//cpu:x86_64",
        "@platforms//os:linux",
    ],
    target_compatible_with = [
        "@platforms//cpu:x86_64",
        "@platforms//os:linux",
    ],
    target_settings = [
        ":enable_legacy_toolchains",
    ],
    toolchain = ":cc-compiler-k8",
    toolchain_type = "@bazel_tools//tools/cpp:toolchain_type",
)

toolchain(
    name = "cc-toolchain-x64_windows-clang-cl",
    exec_compatible_with = [
        "@platforms//cpu:x86_64",
        "@platforms//os:windows",
    ],
    target_compatible_with = [
        "@platforms//cpu:x86_64",
        "@platforms//os:windows",
    ],
    target_settings = [
        ":enable_legacy_toolchains",
    ],
    toolchain = ":cc-compiler-x64_windows-clang-cl",
    toolchain_type = "@bazel_tools//tools/cpp:toolchain_type",
)

bool_flag(
    name = "enable_legacy",
    build_setting_default = False,
)

config_setting(
    name = "enable_legacy_toolchains",
    flag_values = {":enable_legacy": "True"},
)
