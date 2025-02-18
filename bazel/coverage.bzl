def coverage_java_test(name, data = [], jvm_flags = [], visibility = None, test_excluded_packages = {}, **kwargs):
    jacoco_jvm_agent = "//prebuilts/tools/common/jacoco:agent"

    jacoco_jvm_flag = "-javaagent:$(location " + jacoco_jvm_agent + ")=destfile=$$TEST_UNDECLARED_OUTPUTS_DIR/coverage/" + name + "_tests_jacoco.exec"

    # the test needs to be visible to the results workspace it can use the deploy jar
    if visibility == None:
        visibility = ["@results//:__pkg__"]
    elif "//visibility:public" not in visibility:
        visibility += ["@results//:__pkg__"]

    native.java_test(
        name = name,
        data = data + select({
            "//tools/base/bazel:agent_coverage": [jacoco_jvm_agent],
            "//conditions:default": [],
        }),
        #TODO Change to --release 8 once b/262636623 is fixed
        javacopts = kwargs.pop("javacopts", []) + ["-source", "8", "-target", "8"],
        jvm_flags = jvm_flags + select({
            "//tools/base/bazel:agent_coverage": [jacoco_jvm_flag],
            "//conditions:default": [],
        }),
        visibility = visibility,
        **kwargs
    )

def coverage_baseline(name, srcs, jar = None, tags = None):
    # some rules produce multiple jars under their base name so this lets us overload if necessary
    if not jar:
        jar = name
    if tags == None:
        tags = []

    native.alias(
        name = name + "_coverage.baseline.jar",
        actual = jar,
        visibility = ["@baseline//:__pkg__"],
    )

    cov_sources_tags = tags + ([] if "coverage-sources" in tags else ["coverage-sources"])
    native.genrule(
        name = name + "_coverage.baseline.srcs",
        srcs = srcs,
        outs = [name + ".coverage.baseline.srcs"],
        target_compatible_with = ["@platforms//os:linux"],
        tags = cov_sources_tags,
        # using `$(SRCS)` breaks things (some new tests don't get captured), but it's unclear why
        cmd = "printf '$(RULEDIR)/%s\n' {} | sed -e 's%^$(BINDIR)/%%' >$@".format(" ".join(srcs)),
    )

    native.genrule(
        name = name + "_coverage.baseline.srcs.filtered",
        tools = ["@cov//:ignore_files_filter"],
        srcs = [name + "_coverage.baseline.srcs"],
        outs = [name + ".coverage.baseline.srcs.filtered"],
        tags = tags,
        target_compatible_with = ["@platforms//os:linux"],
        cmd = "python3 $(location @cov//:ignore_files_filter) <$< >$@",
        visibility = ["@baseline//:__pkg__"],
    )

    native.genrule(
        name = name + "_coverage.baseline.exempt_markers",
        srcs = srcs,
        outs = [name + ".coverage.baseline.exempt_markers"],
        tags = tags + ["coverage-exempt"],
        target_compatible_with = ["@platforms//os:linux"],
        # grep has an exit code of 1 if no results are found so use `true` to avoid failure
        cmd = "grep -rn '//[[:blank:]]*@coverage:' $(SRCS) >$@ || true",
        visibility = ["@baseline//:__pkg__"],
    )

def coverage_java_library(name, srcs = [], tags = [], **kwargs):
    native.java_library(
        name = name,
        srcs = srcs,
        javacopts = kwargs.pop("javacopts", []) + ["--release", "8"],
        tags = tags,
        **kwargs
    )

    coverage_baseline(
        name = name,
        srcs = srcs,
        tags = tags,
    )

def coverage_android_local_test(
        name,
        data = [],
        jvm_flags = [],
        visibility = None,
        test_excluded_packages = {},
        **kwargs):
    jacoco_jvm_agent = "//prebuilts/tools/common/jacoco:agent"

    jacoco_jvm_flag = "-javaagent:$(location " + jacoco_jvm_agent + ")=destfile=$$TEST_UNDECLARED_OUTPUTS_DIR/coverage/" + name + "_tests_jacoco.exec"

    # the test needs to be visible to the results workspace it can use the deploy jar
    if visibility == None:
        visibility = ["@results//:__pkg__"]
    elif "//visibility:public" not in visibility:
        visibility += ["@results//:__pkg__"]

    native.android_local_test(
        name = name,
        data = data + select({
            "//tools/base/bazel:agent_coverage": [jacoco_jvm_agent],
            "//conditions:default": [],
        }),
        #TODO Change to --release 8 once b/262636623 is fixed
        javacopts = kwargs.pop("javacopts", []) + ["-source", "8", "-target", "8"],
        jvm_flags = jvm_flags + select({
            "//tools/base/bazel:agent_coverage": [jacoco_jvm_flag],
            "//conditions:default": [],
        }) + ["-Djava.security.manager=allow"],
        visibility = visibility,
        **kwargs
    )
