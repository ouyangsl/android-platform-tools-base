"""Implements studio-win CI script."""

import pathlib
import tempfile

from tools.base.bazel.ci import bazel
from tools.base.bazel.ci import presubmit
from tools.base.bazel.ci import studio


def studio_win(build_env: bazel.BuildEnv):
  """Runs Windows pre/postsubmit tests."""
  # If DIST_DIR does not exist, create one.
  if not build_env.dist_dir:
    build_env.dist_dir = tempfile.mkdtemp('dist-dir')
  dist_path = pathlib.Path(build_env.dist_dir)

  targets = [
      '//prebuilts/studio/...',
      '//prebuilts/tools/...',
      '//tools/...',
      '-//tools/vendor/google3/aswb/...',
      '-//tools/vendor/google/aswb/...',
      '-//tools/adt/idea/aswb/...',
  ]
  extra_targets = [
      '//tools/base/profiler/native/trace_processor_daemon',
      '//tools/adt/idea/studio:android-studio',
      '//tools/vendor/google/skia:skiaparser.zip',
      '//tools/vendor/google/skia:skia_test_support.zip',
  ]
  test_tag_filters = '-noci:studio-win,-qa_smoke,-qa_fast,-qa_unreliable,-perfgate-release'

  profile_path = dist_path / f'winprof{build_env.build_number}.json.gz'
  flags = [
      # TODO(b/173153395) Switch back to dynamic after Bazel issue is resolved.
      # See https://github.com/bazelbuild/bazel/issues/22482
      '--config=remote-exec',
      f'--profile={profile_path}',

      f'--test_tag_filters={test_tag_filters}',

      '--tool_tag=studio_win.cmd',
  ]

  build_type = studio.BuildType.from_build_number(build_env.build_number)
  if build_type == studio.BuildType.POSTSUBMIT:
    presubmit.generate_and_upload_hash_file(build_env)
    targets += extra_targets

  if build_type == studio.BuildType.PRESUBMIT:
    result = presubmit.find_test_targets(
        build_env,
        targets,
        test_tag_filters,
    )
    # ci_test is included so that there is always a test target to run.
    targets = result.targets + ['//tools/base/bazel/ci:ci_test']
    flags.extend(result.flags)
    flags.extend(presubmit.generate_runs_per_test_flags(build_env))

  test_result = studio.run_tests(build_env, flags, targets)

  studio.copy_artifacts(
      build_env,
      [
          ('tools/vendor/google/skia/skiaparser.zip', ''),
          ('tools/vendor/google/skia/skia_test_support.zip', ''),
          ('tools/base/profiler/native/trace_processor_daemon/trace_processor_daemon.exe', ''),
      ],
      missing_ok=(build_type == studio.BuildType.PRESUBMIT),
  )

  build_env.bazel_shutdown()

  if test_result.exit_code in {
      bazel.EXITCODE_SUCCESS,
      bazel.EXITCODE_TEST_FAILURES,
  }:
    return

  raise studio.BazelTestError(exit_code=test_result.exit_code)
