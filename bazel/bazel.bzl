"""This module implements the iml_module rule."""

load(":coverage.bzl", "coverage_baseline", "coverage_java_test")
load(":functions.bzl", "create_option_file", "label_workspace_path")
load(":kotlin.bzl", "kotlin_compile")
load(":lint.bzl", "lint_test")
load(":merge_archives.bzl", "run_singlejar")

ImlModuleInfo = provider(
    doc = "Info produced by the iml_module rule.",
    fields = [
        "module_jars",
        "forms",
        "test_forms",
        "java_deps",
        "test_provider",
        "main_provider",
        "module_deps",
        "plugin_deps",
        "external_deps",
        "jvm_target",
        "names",
    ],
)

# This is a custom implementation of label "tags".
# A label of the form:
#   "//package/directory:rule[tag1, tag2]"
# Gets split up into a tuple containing the label, and the array of tags:
#   ("//package/directory:rule", ["tag1", "tag2"])
# Returns the split up tuple.
def _get_label_and_tags(label):
    if not label.endswith("]"):
        return label, []
    rfind = label.rfind("[")
    if rfind == -1:
        # buildifier: disable=print
        print("Malformed tagged label: " + label)
        return label, []
    return label[:rfind], [tag.strip() for tag in label[rfind + 1:-1].split(",")]

def relative_paths(ctx, files, roots):
    """Returns paths of the given files relative to the roots.

    If a file is not found to be inside the roots, it is ignored.

    Args:
        ctx: The Bazel context.
        files: A list of files
        roots: A list of strings representing directory paths relative to the package.
    Returns:
        The updated relative paths.
    """
    package_prefixes = ctx.attr.package_prefixes
    translated_package_prefixes = {root: prefix.replace(".", "/") for (root, prefix) in package_prefixes.items()}

    paths = []
    for file in files:
        for root in roots:
            path = label_workspace_path(ctx.label) + "/" + root
            if file.path.startswith(path):
                relpath = file.path[len(path) + 1:]
                if root in translated_package_prefixes:
                    relpath = translated_package_prefixes[root] + "/" + relpath
                paths.append((relpath, file))
    return paths

def resources_impl(ctx, name, roots, resources, resources_jar):
    """Creates a resource zip archive.

    Args:
        ctx: The bazel context.
        name: A name used for the argument file, "${name}.res.lst".
        roots: See relative_paths() function.
        resources: See 'files' in relative_paths() function.
        resources_jar: The output file.
    """
    zipper_args = ["c", resources_jar.path]
    zipper_files = "".join([k + "=" + v.path + "\n" for k, v in relative_paths(ctx, resources, roots)])
    zipper_list = create_option_file(ctx, name + ".res.lst", zipper_files)
    zipper_args.append("@" + zipper_list.path)
    ctx.actions.run(
        inputs = resources + [zipper_list],
        outputs = [resources_jar],
        executable = ctx.executable._zipper,
        arguments = zipper_args,
        progress_message = "Creating resources zip...",
        mnemonic = "zipper",
    )

def _iml_module_jar_impl(
        ctx,
        name,
        roots,
        java_srcs,
        kotlin_srcs,
        form_srcs,
        resources,
        res_zips,
        output_jar,
        java_deps,
        java_runtime_deps,
        form_deps,
        exports,
        friend_jars,
        module_name):
    jars = []
    ijars = []
    sourcepath = []
    forms = []

    java_jar = ctx.actions.declare_file(name + ".java.jar") if java_srcs else None
    kotlin_jar = ctx.actions.declare_file(name + ".kotlin.jar") if kotlin_srcs else None
    kotlin_ijar = ctx.actions.declare_file(name + ".kotlin-ijar.jar") if kotlin_srcs else None
    full_ijar = ctx.actions.declare_file(name + ".merged-ijar-iml.jar")

    # Compiler args and JVM target.
    java_toolchain = ctx.attr.java_toolchain[java_common.JavaToolchainInfo]
    jvm_target = ctx.attr.jvm_target if ctx.attr.jvm_target else java_toolchain.target_version
    javac_opts = java_common.default_javac_opts(java_toolchain = java_toolchain) + ctx.attr.javacopts
    kotlinc_opts = list(ctx.attr.kotlinc_opts)
    if jvm_target == "8":
        kotlinc_opts += ["-jvm-target", "1.8"]
        kt_java_runtime = ctx.attr._kt_java_runtime_8[java_common.JavaRuntimeInfo]
    elif jvm_target == "11":
        # Ideally we use "--release 11" for javac too, but that is incompatible with "--add-exports".
        kotlinc_opts += ["-jvm-target", "11"]
        kt_java_runtime = ctx.attr._kt_java_runtime_11[java_common.JavaRuntimeInfo]
    elif jvm_target == "17":
        # Ideally we use "--release 17" for javac too, but that is incompatible with "--add-exports".
        kotlinc_opts += ["-jvm-target", "17"]
        kt_java_runtime = ctx.attr._kt_java_runtime_17[java_common.JavaRuntimeInfo]
    else:
        fail("JVM target " + jvm_target + " is not currently supported in iml_module")

    # Kotlin
    kotlin_providers = []
    if kotlin_srcs:
        kotlinc_opts.append("-Xcontext-receivers")  # Needed to use the Kotlin K2 analysis API (b/308454624).
        kotlin_providers.append(kotlin_compile(
            ctx = ctx,
            name = module_name,
            srcs = java_srcs + kotlin_srcs,
            deps = java_deps,
            friend_jars = friend_jars,
            out = kotlin_jar,
            out_ijar = kotlin_ijar,
            java_runtime = kt_java_runtime,
            kotlinc_opts = kotlinc_opts,
            transitive_classpath = False,  # Matches JPS.
        ))
        jars.append(kotlin_jar)
        ijars.append(kotlin_ijar)

    # Resources.
    if resources:
        resources_jar = ctx.actions.declare_file(name + ".res.jar")
        resources_impl(ctx, name, roots, resources, resources_jar)
        jars.append(resources_jar)
    if res_zips:
        jars += res_zips

    # Java
    if java_srcs:
        compiled_java = ctx.actions.declare_file(name + ".pjava.jar") if form_srcs else java_jar
        formc_input_jars = [compiled_java] + ([kotlin_jar] if kotlin_jar else [])

        java_provider = java_common.compile(
            ctx,
            source_files = java_srcs,
            output = compiled_java,
            deps = java_deps + kotlin_providers,
            javac_opts = javac_opts,
            java_toolchain = java_toolchain,
            sourcepath = sourcepath,
        )

        # Note: we exclude formc output from ijars, since formc does not generate APIs used downstream.
        ijars += java_provider.compile_jars.to_list()

        # Forms
        if form_srcs:
            forms += relative_paths(ctx, form_srcs, roots)

            # formc requires full compile jars (no ijars/hjars).
            form_dep_jars = depset(transitive = [
                java_common.make_non_strict(dep).full_compile_jars
                for dep in java_deps
            ])

            # Note: we explicitly include the bootclasspath from the current Java toolchain with
            # the classpath, because extracting it at runtime, when we are running in the
            # FormCompiler JVM, is not portable across JDKs (and made much harder on JDK9+).
            form_classpath = depset(transitive = [form_dep_jars, java_toolchain.bootclasspath])

            args = ctx.actions.args()
            args.add_joined("-cp", form_classpath, join_with = ":")
            args.add("-o", java_jar)
            args.add_all(form_srcs)
            args.add_all([k + "=" + v.path for k, v in form_deps])
            args.add_all(formc_input_jars)

            # To support persistent workers, arguments must come from a param file..
            args.use_param_file("@%s", use_always = True)
            args.set_param_file_format("multiline")

            ctx.actions.run(
                inputs = depset(
                    direct = [v for _, v in form_deps] + form_srcs + formc_input_jars,
                    transitive = [form_classpath],
                ),
                outputs = [java_jar],
                mnemonic = "formc",
                arguments = [args],
                executable = ctx.executable._formc,
                execution_requirements = {"supports-multiplex-workers": "1"},
            )

        jars.append(java_jar)

    if form_srcs and not java_srcs:
        fail("Forms only supported with java sources")

    run_singlejar(
        ctx = ctx,
        jars = jars,
        out = output_jar,
        allow_duplicates = True,  # TODO: Ideally we could be more strict here.
    )

    run_singlejar(
        ctx = ctx,
        jars = ijars,
        out = full_ijar,
        allow_duplicates = True,
    )

    main_provider = JavaInfo(
        output_jar = output_jar,
        compile_jar = full_ijar or output_jar,
        deps = java_deps,
        runtime_deps = java_runtime_deps,
    )
    main_provider = java_common.merge([main_provider] + exports)
    main_provider_no_deps = JavaInfo(
        output_jar = output_jar,
        compile_jar = full_ijar or output_jar,
    )
    return main_provider, main_provider_no_deps, forms

def merge_runfiles(deps):
    return depset(transitive = [
        dep[DefaultInfo].default_runfiles.files
        for dep in deps
        if dep[DefaultInfo].default_runfiles
    ])

def _iml_module_impl(ctx):
    names = [iml.basename[:-4] for iml in ctx.files.iml_files if iml.basename.endswith(".iml")]

    # Prod dependencies.
    java_deps = []
    form_deps = []
    for this_dep in ctx.attr.deps:
        if ImlModuleInfo in this_dep:
            form_deps += this_dep[ImlModuleInfo].forms
        if JavaInfo in this_dep:
            java_deps.append(this_dep[JavaInfo])
    module_deps = []
    plugin_deps = []
    external_deps = []
    for dep in ctx.attr.deps:
        if ImlModuleInfo in dep:
            module_deps.append(dep)
            if ctx.attr.jvm_target:
                if not dep[ImlModuleInfo].jvm_target or int(dep[ImlModuleInfo].jvm_target) > int(ctx.attr.jvm_target):
                    fail("The module %s has a jvm_target of \"%s\", but depends on module %s with target \"%s\"" % (
                        ctx.attr.name,
                        ctx.attr.jvm_target,
                        dep[ImlModuleInfo].names[0],
                        dep[ImlModuleInfo].jvm_target,
                    ))
        elif hasattr(dep, "plugin_info"):
            plugin_deps.append(dep)
        elif hasattr(dep, "platform_info"):
            pass
        else:
            external_deps.append(dep)

    # Test dependencies (superset of prod).
    test_java_deps = []
    test_form_deps = []
    for this_dep in ctx.attr.test_deps:
        if JavaInfo in this_dep:
            test_java_deps.append(this_dep[JavaInfo])
        if ImlModuleInfo in this_dep:
            test_form_deps += this_dep[ImlModuleInfo].test_forms
            test_java_deps.append(this_dep[ImlModuleInfo].test_provider)

    # Runtime dependencies.
    java_runtime_deps = [dep[JavaInfo] for dep in ctx.attr.runtime_deps]

    # Exports.
    exports = []
    test_exports = []
    for export in ctx.attr.exports:
        if JavaInfo in export:
            exports.append(export[JavaInfo])
        if ImlModuleInfo in export:
            test_exports.append(export[ImlModuleInfo].test_provider)

    # Runfiles.
    # Note: the runfiles for test-only deps should technically not be in
    # the prod module, but it is simpler this way (and not very harmful).
    transitive_data = depset(
        direct = ctx.files.iml_files + ctx.files.data,
        transitive = [
            merge_runfiles(ctx.attr.deps),
            merge_runfiles(ctx.attr.test_deps),
            merge_runfiles(ctx.attr.runtime_deps),
        ],
    )
    runfiles = ctx.runfiles(transitive_files = transitive_data)

    # If multiple modules we use the label, otherwise use the exact module name
    module_name = names[0] if len(names) == 1 else ctx.label.name
    main_provider, main_provider_no_deps, main_forms = _iml_module_jar_impl(
        ctx = ctx,
        name = ctx.label.name,
        roots = ctx.attr.roots,
        java_srcs = ctx.files.java_srcs,
        kotlin_srcs = ctx.files.kotlin_srcs,
        form_srcs = ctx.files.form_srcs,
        resources = ctx.files.resources,
        res_zips = ctx.files.res_zips,
        output_jar = ctx.outputs.production_jar,
        java_deps = java_deps,
        java_runtime_deps = java_runtime_deps,
        form_deps = form_deps,
        exports = exports,
        friend_jars = [],
        module_name = module_name,
    )

    friend_jars = main_provider.compile_jars.to_list()
    for test_friend in ctx.attr.test_friends:
        friend_jars += test_friend[JavaInfo].compile_jars.to_list()

    test_provider, _, test_forms = _iml_module_jar_impl(
        ctx = ctx,
        name = ctx.label.name + "_test",
        roots = ctx.attr.test_roots,
        java_srcs = ctx.files.java_test_srcs,
        kotlin_srcs = ctx.files.kotlin_test_srcs,
        form_srcs = ctx.files.form_test_srcs,
        resources = ctx.files.test_resources,
        res_zips = [],
        output_jar = ctx.outputs.test_jar,
        java_deps = [main_provider_no_deps] + test_java_deps,
        java_runtime_deps = java_runtime_deps,
        form_deps = test_form_deps,
        exports = exports + test_exports,
        friend_jars = friend_jars,
        module_name = module_name,
    )

    iml_module_info = ImlModuleInfo(
        module_jars = ctx.outputs.production_jar,
        forms = main_forms,
        test_forms = test_forms,
        java_deps = java_deps,
        test_provider = test_provider,
        main_provider = main_provider,
        module_deps = depset(direct = module_deps),
        plugin_deps = depset(direct = plugin_deps),
        external_deps = depset(direct = external_deps),
        jvm_target = ctx.attr.jvm_target,
        names = names,
    )

    return [
        iml_module_info,
        main_provider,
        DefaultInfo(runfiles = runfiles),
    ]

_iml_module_ = rule(
    attrs = {
        "iml_files": attr.label_list(
            allow_files = True,
            allow_empty = False,
            mandatory = True,
        ),
        "java_srcs": attr.label_list(allow_files = True),
        "kotlin_srcs": attr.label_list(allow_files = True),
        "kotlin_use_compose": attr.bool(),
        "kotlin_use_ir": attr.bool(),
        "form_srcs": attr.label_list(allow_files = True),
        "java_test_srcs": attr.label_list(allow_files = True),
        "kotlin_test_srcs": attr.label_list(allow_files = True),
        "form_test_srcs": attr.label_list(allow_files = True),
        "jvm_target": attr.string(),
        "javacopts": attr.string_list(),
        "kotlinc_opts": attr.string_list(),
        "resources": attr.label_list(allow_files = True),
        "manifests": attr.label_list(allow_files = True),
        "res_zips": attr.label_list(allow_files = True),
        "test_resources": attr.label_list(allow_files = True),
        "package_prefixes": attr.string_dict(),
        "test_class": attr.string(),
        "exports": attr.label_list(providers = [[JavaInfo], [ImlModuleInfo]]),
        "roots": attr.string_list(),
        "test_roots": attr.string_list(),
        # TODO(b/218538628): Add proper support for native libs; right now they are effectively data deps.
        "deps": attr.label_list(providers = [[JavaInfo], [ImlModuleInfo], [CcInfo]]),
        "runtime_deps": attr.label_list(providers = [JavaInfo]),
        "test_deps": attr.label_list(providers = [[JavaInfo], [ImlModuleInfo], [CcInfo]]),
        "test_friends": attr.label_list(providers = [JavaInfo]),
        "data": attr.label_list(allow_files = True),
        "java_toolchain": attr.label(),
        "_kt_java_runtime_8": attr.label(
            # We need this to be able to target JRE 8 in Kotlin, because
            # Kotlinc does not support the --release 8 Javac option.
            # see https://youtrack.jetbrains.com/issue/KT-29974
            default = Label("//prebuilts/studio/jdk:jdk_runtime"),
            providers = [java_common.JavaRuntimeInfo],
            cfg = "exec",
        ),
        "_kt_java_runtime_11": attr.label(
            # We need this to be able to target JRE 11 in Kotlin, because
            # Kotlinc does not support the --release 11 Javac option.
            # see https://youtrack.jetbrains.com/issue/KT-29974
            default = Label("//prebuilts/studio/jdk/jdk11:jdk11_runtime"),
            providers = [java_common.JavaRuntimeInfo],
            cfg = "exec",
        ),
        "_kt_java_runtime_17": attr.label(
            # We need this to be able to target JRE 17 in Kotlin, because
            # Kotlinc does not support the --release 17 Javac option.
            # see https://youtrack.jetbrains.com/issue/KT-29974
            default = Label("//prebuilts/studio/jdk/jdk17:jdk17_runtime"),
            providers = [java_common.JavaRuntimeInfo],
            cfg = "exec",
        ),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "exec",
            executable = True,
        ),
        "_singlejar": attr.label(
            default = Label("@bazel_tools//tools/jdk:singlejar"),
            cfg = "exec",
            executable = True,
        ),
        "_kotlinc": attr.label(
            default = Label("//tools/base/bazel:kotlinc"),
            cfg = "exec",
            executable = True,
        ),
        "_compose_plugin": attr.label(
            default = Label("//prebuilts/tools/common/m2:compose-compiler-hosted"),
            cfg = "exec",
            allow_single_file = [".jar"],
        ),
        "_jvm_abi_gen": attr.label(
            default = Label("//prebuilts/tools/common/m2:jvm-abi-gen-plugin"),
            cfg = "exec",
            allow_single_file = [".jar"],
        ),
        "_kotlin": attr.label(
            default = Label("@maven//:org.jetbrains.kotlin.kotlin-stdlib"),
            allow_files = True,
        ),
        "_formc": attr.label(
            executable = True,
            cfg = "exec",
            default = Label("//tools/base/bazel:formc"),
            allow_files = True,
        ),
    },
    fragments = ["java"],
    outputs = {
        "production_jar": "%{name}.jar",
        "test_jar": "%{name}_test.jar",
    },
    implementation = _iml_module_impl,
)

def _iml_test_module_impl(ctx):
    runfiles = ctx.attr.iml_module[DefaultInfo].default_runfiles
    return [
        ctx.attr.iml_module[ImlModuleInfo].test_provider,  # JavaInfo.
        DefaultInfo(runfiles = runfiles),
    ]

_iml_test_module_ = rule(
    attrs = {
        "iml_module": attr.label(providers = [ImlModuleInfo]),
    },
    implementation = _iml_test_module_impl,
)

def iml_module(
        name,
        srcs = [],
        package_prefixes = {},
        test_srcs = [],
        exclude = [],
        resources = [],
        res_zips = [],
        test_resources = [],
        deps = [],
        runtime_deps = [],
        test_friends = [],
        visibility = [],
        exports = [],
        jvm_target = None,
        javacopts = [],
        javacopts_from_jps = [],
        enable_tests = True,
        test_data = [],
        test_flaky = False,
        test_jvm_flags = [],
        test_timeout = "moderate",
        test_class = "com.android.testutils.JarTestSuite",
        test_shard_count = None,
        split_test_targets = None,
        tags = None,
        test_tags = None,
        test_agents = [],
        iml_files = None,
        data = [],
        test_main_class = None,
        lint_baseline = None,
        lint_enabled = True,
        lint_timeout = None,
        exec_properties = {},
        kotlin_use_compose = False,
        generate_k2_tests = False):
    """A macro corresponding to an IntelliJ module.

    Generates the following targets:
    * "${NAME}": A java library target from srcs.
    * "${NAME}_testlib": A java library from test_srcs.
    * "${NAME}_tests": A java test to run tests from the _testlib target.
    * "${NAME}_tests__${split-test}": If using split_test_targets (optional).

    Instantiating this rule looks similar to an .iml definition.

    Args:
        name: The name of the module used to generate rules.
        srcs: A list of directories containing the sources (.iml use directories as oposed to files)
        package_prefixes: placeholder
        test_srcs: The directories with the test sources.
        exclude: glob() exclude for excluding srcs, test_srcs, resources, and test_resources.
        resources: The directories with the production resources.
        res_zips: Resource zip achives.
        test_resources: The directories with the test resources.
        deps: A tag enhanced list of dependency tags. These dependencies can contain a
              list of tags after the label, e.g., //target[module, test]. Supported tags:
                * module: It treats the dependency as a module dependency, making
                          production and test sources depend on each other correctly.
                * test:   This dependency is included and available for test sources only.
        runtime_deps: Runtime dependencies.
        test_friends: Kotlin test friends.
        visibility: Visibility for all generated targets.
        exports: See java_* rules.
        jvm_target: Determines the java toolchain selected.
        javacopts: See java_* rules.
        javacopts_from_jps: See java_* rules.
        enable_tests: If true, creates the test target.
        test_data: Test runtime dependencies.
        test_flaky: See https://bazel.build/reference/be/common-definitions#test.flaky.
        test_jvm_flags: JVM flags passed to the test process.
        test_timeout: See https://bazel.build/reference/be/common-definitions#test.timeout.
        test_class: See https://bazel.build/reference/be/java#java_test_args.
                            Almost every test module is expected to have a class extending `IdeaTestSuiteBase` class.
                            This class sets up the test environment for the code in the test target and allows to
                            customize access to additional data for the testing code.
        test_shard_count: Number of shards to use for testing, should not be used with split_test_targets.
        split_test_targets: A dict indicating split test targets to create, should not be used with test_shard_count.
                            Each split target runs a subset of tests matching a package name or FQCN.
                            If a test target does not define a `test_filter`, it will run the set of tests that
                            excludes all the other filters. If a test target defines a `test_filter` which
                            is a subset of another test filter, the test target will exclude those tests.
                            For example:
                            `{"A": {"test_filter": "x.y"}, "B": {"test_filter": "x.y.z"}}`
                            Split test target A will automatically exclude "x.y.z".
                            Targets may specify the following common attributes: `data`, `shard_count`, and
                            `tags`. For definitions of these attributes, see
                            https://docs.bazel.build/versions/master/be/common-definitions.html
        tags: Tags applied to ${NAME} target.
        test_tags: Tags applied to the test target.
        test_agents: Adds a testing agent to the java test process.
        iml_files: See impl.
        data: Production runtime dependencies.
        test_main_class: See See https://bazel.build/reference/be/java#java_test_args.
        lint_baseline: See impl.
        lint_enabled: enable or disable Lint checks
        lint_timeout: See impl.
        exec_properties: See https://bazel.build/reference/be/common-definitions#common.exec_properties
        kotlin_use_compose: See impl.
        generate_k2_tests: Creates an additional test target to use the kotlin k2 plugin.
    """
    prod_deps = []
    test_deps = []
    for dep in deps:
        label, label_tags = _get_label_and_tags(dep)
        if "test" not in label_tags:
            prod_deps.append(label)
        test_deps.append(label)

    srcs = split_srcs(srcs, resources, exclude)
    split_test_srcs = split_srcs(test_srcs, test_resources, exclude)

    # if jvm_target is specified, use JDK that compiles to that target
    # otherwise use default JDK, controlled by `java_language_version_17` flag
    if jvm_target == "8":
        java_toolchain = "//prebuilts/studio/jdk/jdk17:java8_compile_toolchain"
    elif jvm_target == "11":
        java_toolchain = "//prebuilts/studio/jdk/jdk17:java11_compile_toolchain"
    else:
        java_toolchain = "//prebuilts/studio/jdk/jdk17:java17_compile_toolchain"

    _iml_module_(
        name = name,
        tags = tags,
        visibility = visibility,
        java_srcs = srcs.javas,
        kotlin_srcs = srcs.kotlins,
        kotlin_use_compose = kotlin_use_compose,
        form_srcs = srcs.forms,
        resources = srcs.resources,
        res_zips = res_zips,
        roots = srcs.roots,
        java_test_srcs = split_test_srcs.javas,
        kotlin_test_srcs = split_test_srcs.kotlins,
        form_test_srcs = split_test_srcs.forms,
        test_resources = split_test_srcs.resources,
        test_roots = split_test_srcs.roots,
        package_prefixes = package_prefixes,
        jvm_target = jvm_target,
        java_toolchain = java_toolchain,
        javacopts = javacopts + javacopts_from_jps,
        iml_files = iml_files,
        exports = exports,
        deps = prod_deps,
        runtime_deps = runtime_deps,
        test_deps = test_deps,
        test_friends = test_friends,
        data = data,
        test_class = test_class,
    )

    if srcs.javas + srcs.kotlins:
        coverage_baseline(
            name = name,
            srcs = srcs.javas + srcs.kotlins,
            jar = name + ".jar",
            tags = tags,
        )

    _iml_test_module_(
        name = name + "_testlib",
        tags = tags,
        iml_module = ":" + name,
        testonly = True,
        visibility = visibility,
    )

    lint_srcs = srcs.javas + srcs.kotlins + srcs.resources
    if lint_baseline and not lint_srcs:
        fail("lint_baseline set for iml_module that has no sources")

    if lint_srcs and lint_enabled:
        lint_tags = tags if tags else []
        if "no_windows" not in lint_tags:
            lint_tags.append("no_windows")

        lint_test(
            name = name + "_lint_test",
            srcs = lint_srcs,
            baseline = lint_baseline,
            deps = prod_deps,
            custom_rules = ["//tools/base/lint:studio-checks.lint-rules.jar"],
            external_annotations = ["//tools/base/external-annotations:annotations.zip"],
            tags = lint_tags,
            timeout = lint_timeout if lint_timeout else None,
        )

    if not test_srcs:
        return

    # The default test_class (JarTestSuite) comes from testutils, so we add testutils as a runtime dep.
    test_utils = [] if name == "studio.android.sdktools.testutils" else ["//tools/base/testutils:studio.android.sdktools.testutils_testlib"]

    # Run java agents in tests
    for test_agent in test_agents:
        test_data = test_data + [test_agent]
        test_jvm_flags = test_jvm_flags + ["-javaagent:$(location " + test_agent + ")"]

    if enable_tests:
        test_tags = tags + test_tags if tags and test_tags else (tags if tags else test_tags)
        _gen_tests(
            name = name,
            split_test_targets = split_test_targets,
            test_flaky = test_flaky,
            test_shard_count = test_shard_count,
            test_tags = test_tags,
            test_data = test_data,
            runtime_deps = [":" + name + "_testlib"] + test_utils,
            jvm_flags = test_jvm_flags + ["-Dtest.suite.jar=" + name + "_test.jar"],
            main_class = test_main_class,
            test_class = test_class,
            timeout = test_timeout,
            exec_properties = exec_properties,
            visibility = visibility,
        )

        if generate_k2_tests:
            _gen_tests(
                name = name + "_k2",
                split_test_targets = split_test_targets,
                test_flaky = test_flaky,
                test_shard_count = test_shard_count,
                test_tags = (test_tags or []) + ["kotlin-plugin-k2"],
                test_data = test_data,
                runtime_deps = [":" + name + "_testlib"] + test_utils,
                jvm_flags = test_jvm_flags + [
                    "-Dtest.suite.jar=" + name + "_test.jar",
                    "-Didea.kotlin.plugin.use.k2=true",
                ],
                main_class = test_main_class,
                test_class = test_class,
                timeout = test_timeout,
                exec_properties = exec_properties,
                visibility = visibility,
            )

    else:
        if test_tags:
            fail("enable_tests is False but test_tags was specified.")
        if test_data:
            fail("enable_tests is False but test_data was specified.")
        if test_flaky:
            fail("enable_tests is False but test_flaky was specified.")
        if test_jvm_flags:
            fail("enable_tests is False but test_jvm_flags was specified.")
        if test_shard_count:
            fail("enable_tests is False but test_shard_count was specified.")
        if split_test_targets:
            fail("enable_tests is False but split_test_targets was specified.")
        if test_agents:
            fail("enable_tests is False but test_agents was specified.")

def iml_test(
        name,
        module,
        runtime_deps = [],
        tags = [],
        tags_linux = [],
        tags_mac = [],
        tags_windows = [],
        **kwargs):
    """Potentially generates separate _iml_test rules for each OS.

    Args:
        name: base name of the test.
        module: name of the module.
        runtime_deps: optional libraries to make available to the final
            test at runtime only.
        tags: optional list of tags to categorize the tests. These are
            applied to each operating system (so adding "manual" here
            will prevent the test from running on all operating
            systems).
        tags_linux: tags specific to Linux.
        tags_mac: tags specific to macOS.
        tags_windows: tags specific to Windows.
        **kwargs: all other arguments.
    """

    # If nothing OS-specific was provided, then produce only one rule
    if len(tags_linux) == 0 and len(tags_mac) == 0 and len(tags_windows) == 0:
        _iml_test(
            name = name,
            module = module,
            runtime_deps = runtime_deps,
            tags = tags,
            **kwargs
        )
        return

    all_platform_info = [
        struct(
            suffix = "_linux",
            tags = tags_linux,
            target_condition = "@platforms//os:linux",
        ),
        struct(
            suffix = "_mac",
            tags = tags_mac,
            target_condition = "@platforms//os:osx",
        ),
        struct(
            suffix = "_windows",
            tags = tags_windows,
            target_condition = "@platforms//os:windows",
        ),
    ]
    test_names = []
    for platform_info in all_platform_info:
        test_name = name + platform_info.suffix
        test_names.append(test_name)
        all_tags = list(platform_info.tags) + tags

        # Tests tagged with "manual" will still run when triggered
        # through a test_suite, which is why the test target itself
        # needs to be tagged with a proper target_compatible_with field.
        compatibility = ["@platforms//:incompatible"] if "manual" in all_tags else []
        _iml_test(
            name = test_name,
            module = module,
            runtime_deps = runtime_deps,
            tags = all_tags,
            target_compatible_with = select({
                platform_info.target_condition: compatibility,
                "//conditions:default": ["@platforms//:incompatible"],
            }),
            **kwargs
        )
    native.test_suite(
        name = name,
        tags = tags,
        tests = test_names,
    )

def _iml_test(
        name,
        module,
        runtime_deps = [],
        **kwargs):
    native.java_test(
        name = name,
        runtime_deps = runtime_deps + [module + "_testlib"],
        **kwargs
    )

def _gen_tests(
        name,
        split_test_targets,
        test_flaky = None,
        test_shard_count = None,
        test_tags = None,
        test_data = None,
        visibility = [],
        **kwargs):
    """Generates potentially-split test target(s).

    If split_test_targets is True, generates split test targets as per
    _gen_split_tests. Otherwise, generates a single test target.

    Args:
        name: The base name of the test.
        split_test_targets: A dict of names to split_test_target definitions.
        test_flaky: Whether the generated test should be marked flaky. Only valid for single tests.
        test_shard_count: Shard count for the generated test. Only valid for single tests.
        test_tags: optional list of tags to include for test targets.
        test_data: optional list of data to include for test targets.
        visibility: Target visibility.
        **kwargs: Additional arguments passed to java_test().
    """

    if split_test_targets and test_flaky:
        fail("must use the Flaky attribute per split_test_target")
    if split_test_targets and test_shard_count:
        fail("test_shard_count and split_test_targets should not both be specified")

    if split_test_targets:
        _gen_split_tests(
            name = name,
            split_test_targets = split_test_targets,
            test_tags = test_tags,
            test_data = test_data,
            visibility = visibility,
            **kwargs
        )
    else:
        coverage_java_test(
            name = name + "_tests",
            flaky = test_flaky,
            shard_count = test_shard_count,
            tags = test_tags,
            data = test_data,
            visibility = visibility,
            **kwargs
        )

def _gen_split_tests(
        name,
        split_test_targets,
        test_tags = None,
        test_data = None,
        timeout = None,
        exec_properties = None,
        jvm_flags = [],
        visibility = [],
        **kwargs):
    """Generates split test targets.

    A new test target is generated for each split_test_target, a test_suite containing all
    split targets, and a test target which does not perform any splitting. The non-split target is
    only to be used for local development with the bazel `--test_filter` flag, since this flag
    does not work on split test targets. The test_suite will only contain split tests which do not
    use the 'manual' tag.

    Args:
        name: The base name of the test.
        split_test_targets: A dict of names to split_test_target definitions.
        test_tags: optional list of tags to include for test targets.
        test_data: optional list of data to include for test targets.
        timeout: optional timeout that applies to this split test only (overriding target level).
        exec_properties: See https://bazel.build/reference/be/common-definitions#common-attributes
        jvm_flags: Extra flags passed to java_test().
        visibility: Target visibility.
        **kwargs: Extra arguments passed to java_test().
    """

    # create a _tests__all target for local development with all test sources
    # primarily useful if users want to specify a --test_filter themselves
    coverage_java_test(
        name = name + "_tests__all",
        data = test_data + _get_unique_split_data(split_test_targets),
        tags = ["manual"],
        exec_properties = exec_properties,
        jvm_flags = jvm_flags,
        visibility = visibility,
        **kwargs
    )
    split_tests = []
    for split_name in split_test_targets:
        test_name = name + "_tests__" + split_name
        split_target = split_test_targets[split_name]
        shard_count = split_target.get("shard_count")
        tags = list(split_target.get("tags", default = []))
        data = list(split_target.get("data", default = []))
        additional_jvm_args = list(split_target.get("additional_jvm_args", default = []))
        split_timeout = split_target.get("timeout", default = timeout)
        flaky = split_target.get("flaky")
        if "manual" not in tags:
            split_tests.append(test_name)
        if test_data:
            data += test_data
        if test_tags:
            tags += test_tags

        test_jvm_flags = []
        test_jvm_flags.extend(jvm_flags)
        test_jvm_flags.extend(additional_jvm_args)
        test_jvm_flags.extend(_gen_split_test_jvm_flags(split_name, split_test_targets))
        test_exec_properties = split_target.get("exec_properties", default = exec_properties)

        coverage_java_test(
            name = test_name,
            shard_count = shard_count,
            timeout = split_timeout,
            flaky = flaky,
            data = data,
            tags = tags,
            exec_properties = test_exec_properties,
            jvm_flags = test_jvm_flags,
            visibility = visibility,
            **kwargs
        )
    native.test_suite(
        name = name + "_tests",
        tags = ["manual"] if test_tags and "manual" in test_tags else [],
        tests = split_tests,
        visibility = visibility,
    )

def _get_unique_split_data(split_test_targets):
    """Returns all split_test_target 'data' dependencies without duplicates."""
    data = []
    for split_name in split_test_targets:
        split_data = split_test_targets[split_name].get("data", default = [])
        for d in split_data:
            if d not in data:
                data.append(d)
    return data

def _gen_split_test_jvm_flags(split_name, split_test_targets):
    """Generates jvm_flags for a split test target.

    Args:
        split_name: The name of the split_test_target to generate.
        split_test_targets: All the defined split_test_targets.
    Returns:
        The test jvm_flags with test_filter and test_exclude_filter defined
        based on the test_filter given to each split_test_target.
    """
    jvm_flags = []
    split_target = split_test_targets[split_name]
    test_filter = split_target.get("test_filter")
    _validate_split_test_filter(test_filter)
    if test_filter:
        jvm_flags.append("-Dtest_filter=\"(" + test_filter + ")\"")

    excludes = _gen_split_test_excludes(split_name, split_test_targets)
    if excludes:
        jvm_flags.append("-Dtest_exclude_filter=\"(" + "|".join(excludes) + ")\"")
    return jvm_flags

def _validate_split_test_filter(test_filter):
    """Validates the test_filter matches a package or FQCN format."""
    if not test_filter:
        return
    if test_filter.startswith("."):
        # Allow trailing packages, e.g. ".gradle", which could for example match
        # against "test.subpackage1.gradle" AND "test.subpackage2.gradle"
        test_filter = test_filter[1:]
    for split in test_filter.split("."):
        if not (split.isalnum()):
            fail("invalid test_filter '%s'. Must be package name or FQCN" % test_filter)

def _gen_split_test_excludes(split_name, split_test_targets):
    """Generates a list of test exclude filters.

    These are used to exclude tests from running when other split_test_targets define
    a 'test_filter' that is a subset of another test_filter. e.g.,
    {
      "A": {"test_filter": "com.bar"},
      "B": {"test_filter": "com.bar.MyTest"},
    }
    The split_target A will generate an excludes ["com.bar.MyTest"], and
    split_target B will generate no excludes.

    If a split_test_target has no 'test_filter', it will generate a
    list of excludes based on all the other test filters.

    Args:
        split_name: The name of the split_test_target to generate excludes for.
        split_test_targets: All the defined split_test_targets.
    Returns:
        A list of exclude filters based on the 'test_filter' of other
        split_test_targets.
    """
    split_target = split_test_targets[split_name]
    test_filter = split_target.get("test_filter")
    excludes = []
    for other_split_name in split_test_targets:
        # pass over the split_test_target we're generating excludes for
        if split_name == other_split_name:
            continue

        other = split_test_targets[other_split_name]
        other_test_filter = other.get("test_filter")
        if not other_test_filter:
            if not test_filter:
                fail("Cannot have more than one split_test_targets without a 'test_filter'.")
            continue

        # empty test filter, always exclude other test filters
        if not test_filter:
            excludes.append(other_test_filter)
            continue

        if other_test_filter.startswith(test_filter):
            excludes.append(other_test_filter)

    return excludes

def split_srcs(src_dirs, res_dirs, exclude):
    """Returns a struct splitting java, kotlin, and other source files.

    Args:
        src_dirs: A list of directories containing sources.
        res_dirs: A list of directories containing resources.
        exclude: A list of excludes passed to glob() for src_dirs and res_dirs.
    Returns:
        A struct representing groups of source files.
    """
    roots = src_dirs + res_dirs
    exts = ["java", "kt", "groovy", "DS_Store", "form", "flex"]
    excludes = []
    for root in roots:
        excludes += [root + "/**/*." + ext for ext in exts]

    resources = native.glob(
        include = [src + "/**" for src in roots],
        exclude = excludes,
    )
    groovies = native.glob([src + "/**/*.groovy" for src in src_dirs], exclude)
    if groovies:
        fail("Groovy is not supported")

    javas = native.glob([src + "/**/*.java" for src in src_dirs], exclude)
    kotlins = native.glob([src + "/**/*.kt" for src in src_dirs], exclude)
    forms = native.glob([src + "/**/*.form" for src in src_dirs], exclude)

    return struct(
        roots = roots,
        resources = resources,
        javas = javas,
        kotlins = kotlins,
        forms = forms,
    )
