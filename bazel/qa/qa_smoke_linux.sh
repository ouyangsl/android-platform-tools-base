#!/bin/bash -x
# Invoked by Android Build Launchcontrol for continuous builds.

# Expected arguments:
readonly out_dir="$1"
readonly dist_dir="$2"
readonly build_number="$3"

readonly script_dir="$(dirname "$0")"
readonly script_name="$(basename "$0")"

readonly lsb_release="$(grep -oP '(?<=DISTRIB_CODENAME=).*' /etc/lsb-release)"

# Invalidate local cache to avoid picking up obsolete test result xmls
"${script_dir}/../bazel" clean --async  --expunge

#Have crostini tests run locally and one at a time
if [[ $lsb_release == "crostini" ]]; then
  # The BAZEL_* variable (credentials) is configured on the ChromeBot Host.
  export GOOGLE_APPLICATION_CREDENTIALS=$BAZEL_GOOGLE_APPLICATION_CREDENTIALS
  # don't use any remote cached items, some items built on Linux may not be compatible. b/172365127
  config_options="--config=local --config=rcache --config=ants --bes_timeout=1h"
  target_filters=qa_smoke,ui_test,-qa_unreliable,-requires_emulator,-no_crostini

  # Temp workaround for b/159371003
  # Check running processes
  ps -ef
  readonly counter="$(ps -ef | grep -c 'at-spi-bus-launcher')"
  # these accessibiluty daemons keep on accumulating with each test execution
  # and ultimately cause OOM failures https://paste.googleplex.com/4715109898256384
  # manually kill them off for now
  ps -ef | grep "at-spi-bus-launcher" | awk '{print $2}' | xargs kill -9
  ps -ef | grep "at-spi2/accessibility.conf" | awk '{print $2}' | xargs kill -9
  ps -ef | grep "/usr/bin/dbus-daemon --syslog-only" | awk '{print $2}' | xargs kill -9

  # Generate a UUID for use as the bazel invocation id
  readonly test_invocation_id="$(uuidgen)"

  # Run the tests one at a time after all dependencies get built
  # Also limit # of jobs running, this should be based in available resources
  "${script_dir}/../bazel" \
    --max_idle_secs=60 \
    test \
    --keep_going \
    ${config_options} \
    ${bazel_flags} \
    --test_strategy=exclusive \
    --jobs=4 \
    --worker_verbose=true \
    --invocation_id=${test_invocation_id} \
    --define=meta_android_build_number=${build_number} \
    --build_event_binary_file="${dist_dir:-/tmp}/bazel-${build_number}.bes" \
    --build_tag_filters=${target_filters} \
    --build_metadata=ab_build_id="${build_number}" \
    --build_metadata=ab_target="qa-chromeos_smoke" \
    --test_tag_filters=${target_filters} \
    --tool_tag=${script_name} \
    --strategy=Javac=local \
    --strategy=kotlinc=local \
    -- \
    //tools/adt/idea/android-uitests/...

  readonly bazel_status_no_emu=$?

  if [[ -d "${dist_dir}" ]]; then
    echo "<head><meta http-equiv=\"refresh\" content=\"0; URL='https://fusion2.corp.google.com/invocations/${test_invocation_id}'\" /></head>" > "${dist_dir}"/upsalite_test_results.html
  fi

  if [[ -d "${dist_dir}" ]]; then
    readonly testlogs_dir="$("${script_dir}/../bazel" info --config=release bazel-testlogs)"
    readonly bazel_status=$?
    if [[ ! -z "$testlogs_dir" ]]; then
      mkdir "${dist_dir}"/testlogs
      (mv "${testlogs_dir}"/* "${dist_dir}"/testlogs/)
      echo "Remove any empty file in testlogs"
      find  "${dist_dir}"/testlogs/ -size  0 -print0 |xargs -0 rm --
    else
      exit $bazel_status
    fi
  fi

else #Executes normally on linux as before
  config_options="--config=ci --config=remote-exec"

  # Generate a UUID for use as the bazel invocation id
  readonly invocation_id="$(uuidgen)"

  # The smoke tests are ran in 2 groups.
  # The first group is all the tests that do not use an Android emulator.
  # The second group is all the tests that use the Android emulator.
  # We need to run in 2 groups because the 2 sets of tests run with different
  # options.

  # Run Bazel tests - no emulator tests should run here
  target_filters=qa_smoke,ui_test,-qa_unreliable,-requires_emulator
  "${script_dir}/../bazel" \
    --max_idle_secs=60 \
    test \
    --keep_going \
    ${config_options} --config=ants \
    --invocation_id=${invocation_id} \
    --define=meta_android_build_number=${build_number} \
    --build_tag_filters=${target_filters} \
    --test_tag_filters=${target_filters} \
    --build_metadata=ab_build_id="${build_number}" \
    --build_metadata=ab_target="qa-smoke" \
    --tool_tag=${script_name} \
    --flaky_test_attempts=//tools/adt/idea/android-uitests:.*@2 \
    -- \
    //tools/adt/idea/android-uitests/...

  readonly bazel_status_no_emu=$?

  if [[ -d "${dist_dir}" ]]; then
    echo "<head><meta http-equiv=\"refresh\" content=\"0; URL='https://fusion2.corp.google.com/invocations/${invocation_id}'\" /></head>" > "${dist_dir}"/upsalite_test_results.html
  fi

  if [[ -d "${dist_dir}" ]]; then
    readonly testlogs_dir="$("${script_dir}/../bazel" info bazel-testlogs ${config_options})"
    readonly bazel_status=$?
    if [[ ! -z "$testlogs_dir" ]]; then
      mkdir "${dist_dir}"/testlogs
      (mv "${testlogs_dir}"/* "${dist_dir}"/testlogs/)
      echo "Remove any empty file in testlogs"
      find  "${dist_dir}"/testlogs/ -size  0 -print0 |xargs -0 rm --
    else
      exit $bazel_status
    fi
  fi
fi

# See http://docs.bazel.build/versions/master/guide.html#what-exit-code-will-i-get
# Exit with status 0 if all of the above tests' exit codes is 0, 3, or 4.
for test_exit_code in "${bazel_status_no_emu}"; do
  case $test_exit_code in
    [034])
      # Exit code 0: successful test run
      # Exit code 3: tests failed or timed out. We ignore test failures for
      # manual review
      # Exit code 4: No tests found. This can happen if all tests are moved out
      # of the reliable group.
      ;;
    *)
      exit $test_exit_code
  esac
done

exit 0
