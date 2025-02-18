#!/bin/bash -x
# Invoked by Android Build Launchcontrol for continuous builds.

readonly BAZEL_EXITCODE_TEST_FAILURES=3
export BAZEL_INVOCATION_TIMEOUT="2h"

readonly dist_dir="$1"
readonly build_number="$2"

if [[ $build_number =~ ^[0-9]+$ ]]; then
  readonly postsubmit=true
fi

readonly script_dir="$(dirname "$0")"

collect_and_exit() {
  local -r exit_code=$1

  if [[ -d "${dist_dir}" ]]; then
    "${script_dir}/bazel" \
    run //tools/vendor/adt_infra_internal/rbe/logscollector:logs-collector \
    --config=rcache \
    -- \
    -bes "${dist_dir}/bazel-${build_number}.bes" \
    -error_log "$DIST_DIR/logs/build_error.log"
  fi

  if [[ $? -ne 0 ]]; then
    echo "Bazel logs-collector failed!"
    exit $?
  fi

  # Test failures in CI are displayed by other systems. (context: b/192362688)
  if [[ $exit_code == $BAZEL_EXITCODE_TEST_FAILURES ]]; then
    exit 0
  fi
  exit $exit_code
}

# Clean up existing results so obsolete data cannot cause issues
# --max_idle_secs is only effective at bazel server startup time so it needs to be in the first call
"${script_dir}/bazel" --max_idle_secs=60 clean --async || exit $?

# Generate a UUID for use as the bazel invocation id
readonly invocation_id="$(uuidgen)"

if [[ -d "${dist_dir}" ]]; then
  # Link to test results
  echo "<head><meta http-equiv=\"refresh\" content=\"0; URL='https://fusion2.corp.google.com/invocations/${invocation_id}'\" /></head>" > "${dist_dir}"/upsalite_test_results.html
  echo "${invocation_id}" > "${dist_dir}/sponge-invocations.txt"
fi

declare -a extra_test_flags
if [[ $postsubmit ]]; then
    extra_test_flags+=(--bes_keywords=ab-postsubmit)
    extra_test_flags+=(--nocache_test_results)
fi

# Generate baseline coverage file lists
"${script_dir}/bazel" \
  build \
  --config=rcache \
  --build_tag_filters="coverage-sources" \
  --build_metadata=ab_build_id="${build_number}" \
  --build_metadata=ab_target=studio-coverage \
  --build_metadata=cov_phase=baseline-coverage-sources \
  -- \
  //tools/... \
  || exit $?

# Run Bazel with coverage instrumentation
"${script_dir}/bazel" \
  test \
  --config=ci --config=remote-exec --config=ants \
  --invocation_id=${invocation_id} \
  --tool_tag="studio_coverage.sh" \
  --build_event_binary_file="${dist_dir:-/tmp}/bazel-${build_number}.bes" \
  --profile="${dist_dir:-/tmp}/profile-${build_number}.json.gz" \
  --build_metadata=ANDROID_BUILD_ID="${build_number}" \
  --build_metadata=ANDROID_TEST_INVESTIGATE="http://ab/tests/bazel/${invocation_id}" \
  --build_metadata=ab_build_id="${build_number}" \
  --build_metadata=ab_target=studio-coverage \
  --build_metadata=cov_phase=tests-and-baseline \
  --jvmopt="-Dstudio.is.coverage.build=true" \
  ${auth_options} \
  --test_tag_filters=-perfgate,-perfgate-release \
  --define agent_coverage=true \
  --remote_download_regex=".*.coverage.baseline.srcs" \
  "${extra_test_flags[@]}" \
  -- \
  @cov//:all.suite \
  @baseline//... \
  || collect_and_exit $?

# Generate another UUID for the report invocation
readonly report_invocation_id="$(uuidgen)"

if [[ -d "${dist_dir}" ]]; then
  # Link to test results
  echo "<head><meta http-equiv=\"refresh\" content=\"0; URL='https://fusion2.corp.google.com/invocations/${report_invocation_id}'\" /></head>" > "${dist_dir}"/upsalite_build_report_results.html
fi

# Build the lcov file
"${script_dir}/bazel" \
  build \
  --config=rcache \
  --config=release \
  --invocation_id=${report_invocation_id} \
  --jobs=HOST_CPUS*.5 \
  --build_metadata=ab_build_id="${build_number}" \
  --build_metadata=ab_target=studio-coverage \
  --build_metadata=cov_phase=generate-lcov \
  ${auth_options} \
  -- \
  @cov//:all.lcov \
  || exit $?

readonly lcov_path="./bazel-bin/external/cov/all/lcov"

if [[ -d "${dist_dir}" ]]; then
  # Copy the report to ab/ outputs
  mkdir "${dist_dir}/coverage" || exit $?
  cp -pv ${lcov_path} "${dist_dir}/coverage" || exit $?
fi

collect_and_exit 0
