"""Implements studio-linux CI scripts."""

import itertools
import pathlib
import shutil
import tempfile
from typing import List, Sequence
import zipfile

from tools.base.bazel.ci import bazel
from tools.base.bazel.ci import presubmit
from tools.base.bazel.ci import studio


_BASE_TARGETS = [
    '//prebuilts/studio/...',
    '//prebuilts/tools/...',
    '//tools/...',
]


_EXTRA_TARGETS = [
    '//tools/adt/idea/studio:android-studio',
    '//tools/adt/idea/studio:updater_deploy.jar',
    '//tools/vendor/google/aswb:aswb.linux.zip',
    '//tools/vendor/google/aswb:aswb.mac.zip',
    '//tools/vendor/google/aswb:aswb.mac_arm.zip',
    '//tools/adt/idea/native/installer:android-studio-bundle-data',
    '//tools/base/profiler/native/trace_processor_daemon',
    '//tools/base/deploy/deployer:deployer.runner_deploy.jar',
    '//tools/base/preview/screenshot:preview_screenshot_maven_repo.zip',
    '//tools/base/firebase/testlab/testlab-gradle-plugin:testlab-gradle-plugin.zip',
    '//tools/adt/idea/studio:test_studio',
    '//tools/vendor/google/game-tools/packaging:packaging-linux',
    '//tools/vendor/google/game-tools/packaging:packaging-win',
    '//tools/base/deploy/service:deploy.service_deploy.jar',
    '//tools/base/ddmlib:tools.ddmlib',
    '//tools/base/ddmlib:incfs',
    '//tools/base/lint/libs/lint-tests:lint-tests',
    '//tools/base/bazel:local_maven_repository_generator_deploy.jar',
    '//tools/base/build-system:documentation.zip',
    '//tools/vendor/google/adrt:android-studio-cros-skeleton.zip',
    '//tools/vendor/google/adrt:android-studio-nsis-prebuilt.zip',
    '//tools/vendor/google/asfp/studio:asfp_build_manifest.textproto',
    '//tools/vendor/google/asfp/studio:asfp.deb',
    '//tools/vendor/intel:android-studio-intel-haxm.zip',
    '//tools/vendor/google/ml:aiplugin',
    '//tools/vendor/google/ml:studiobot-dogfood-plugin',
    '//tools/adt/idea/aswb/aswb:aswb_bazel_zip',
]


_ARTIFACTS = [
    ('tools/adt/idea/studio/android-studio.linux.zip', 'artifacts'),
    ('tools/adt/idea/studio/android-studio.win.zip', 'artifacts'),
    ('tools/adt/idea/studio/android-studio.mac.zip', 'artifacts'),
    ('tools/adt/idea/studio/android-studio.mac_arm.zip', 'artifacts'),
    ('tools/adt/idea/studio/android-studio_build_manifest.textproto', 'artifacts'),
    ('tools/adt/idea/studio/android-studio_update_message.html', 'artifacts'),
    ('tools/adt/idea/studio/updater_deploy.jar', 'artifacts/android-studio-updater.jar'),
    ('tools/adt/idea/native/installer/android-studio-bundle-data.zip', 'artifacts'),
    ('tools/vendor/google/adrt/android-studio-cros-skeleton.zip', 'artifacts'),
    ('tools/vendor/google/adrt/android-studio-nsis-prebuilt.zip', 'artifacts'),
    ('tools/vendor/intel/android-studio-intel-haxm.zip', 'artifacts'),
    ('tools/vendor/google/asfp/studio/asfp_build_manifest.textproto', 'artifacts'),
    ('tools/vendor/google/asfp/studio/asfp.deb', 'artifacts'),
    ('tools/vendor/google/aswb/android-studio-with-blaze-channel.deb', 'artifacts'),
    ('tools/vendor/google/aswb/android-studio-with-blaze.mac.zip', 'artifacts'),
    ('tools/vendor/google/aswb/android-studio-with-blaze.mac_arm.zip', 'artifacts'),
    ('tools/vendor/google/skia/skiaparser.zip', 'artifacts'),
    ('tools/vendor/google/skia/skia_test_support.zip', 'artifacts'),
    ('tools/vendor/google/ml/aiplugin*.zip', 'artifacts'),
    ('tools/vendor/google/ml/studiobot-dogfood-plugin*.zip', 'artifacts'),

    ('tools/base/sdklib/commandlinetools_*.zip', 'artifacts'),
    ('tools/base/ddmlib/tools.ddmlib.jar', 'artifacts/ddmlib.jar'),
    ('tools/base/annotations/annotations.jar', 'artifacts'),
    ('tools/base/common/tools.common.jar', 'artifacts'),
    ('tools/base/ddmlib/libincfs.jar', 'artifacts'),
    ('tools/base/lint/libs/lint-tests/lint-tests.jar', 'artifacts'),
    ('tools/base/deploy/deployer/deployer.runner_deploy.jar', 'artifacts/deployer.jar'),
    ('tools/base/profiler/native/trace_processor_daemon/trace_processor_daemon', 'artifacts'),
    ('tools/vendor/google/game-tools/packaging/game-tools-linux.tar.gz', 'artifacts'),
    ('tools/vendor/google/game-tools/packaging/game-tools-win.zip', 'artifacts'),
    ('tools/base/deploy/service/deploy.service_deploy.jar', 'artifacts'),
    ('tools/base/gmaven/gmaven.zip', 'artifacts/gmaven_repo.zip'),
    ('tools/base/build-system/documentation.zip', 'artifacts/android_gradle_plugin_reference_docs.zip'),
    ('tools/base/firebase/testlab/testlab-gradle-plugin/testlab-gradle-plugin.zip', 'artifacts'),
    ('tools/base/preview/screenshot/preview_screenshot_maven_repo.zip', 'artifacts'),
    ('tools/adt/idea/aswb/aswb/aswb_bazel.zip', 'artifacts'),
]


def studio_linux(build_env: bazel.BuildEnv) -> None:
  """Runs studio-linux target."""
  setup_environment(build_env)
  test_tag_filters = '-noci:studio-linux,-qa_smoke,-qa_fast,-qa_unreliable,-perfgate-release'

  flags = build_flags(
      build_env,
      test_tag_filters=test_tag_filters,
  )

  targets = _BASE_TARGETS

  build_type = studio.BuildType.from_build_number(build_env.build_number)
  if build_type == studio.BuildType.POSTSUBMIT:
    presubmit.generate_and_upload_hash_file(build_env)
    targets += _EXTRA_TARGETS

  if build_type == studio.BuildType.PRESUBMIT:
    result = presubmit.find_test_targets(
        build_env,
        _BASE_TARGETS,
        test_tag_filters,
    )
    # iml_to_build_consistency_test is included so that there is always a test
    # target to run.
    targets = result.targets + ['//tools/base/bazel:iml_to_build_consistency_test']
    flags.extend(result.flags)
    flags.extend(presubmit.generate_runs_per_test_flags(build_env))

  result = studio.run_tests(build_env, flags, targets)
  copy_agp_supported_versions(build_env)
  if studio.is_build_successful(result):
    copy_artifacts(
        build_env,
        missing_ok=(build_type == studio.BuildType.PRESUBMIT),
    )
    if result.exit_code != bazel.EXITCODE_NO_TESTS_FOUND:
      return

  raise studio.BazelTestError(exit_code=result.exit_code)


def studio_linux_large(build_env: bazel.BuildEnv) -> None:
  """Runs studio-linux-large target."""
  setup_environment(build_env)
  flags = build_flags(
      build_env,
      test_tag_filters='ci:studio-linux-large',
  )
  result = studio.run_tests(build_env, flags, _BASE_TARGETS)
  if studio.is_build_successful(result):
    return

  raise studio.BazelTestError(exit_code=result.exit_code)


def studio_linux_very_flaky(build_env: bazel.BuildEnv) -> None:
  """Runs studio-linux_very_flaky target."""
  setup_environment(build_env)
  flags = build_flags(
      build_env,
      test_tag_filters='ci:studio-linux_very_flaky',
  )
  flags.append('--build_tests_only')

  result = studio.run_tests(build_env, flags, _BASE_TARGETS + _EXTRA_TARGETS)
  if studio.is_build_successful(result):
    return

  raise studio.BazelTestError(exit_code=result.exit_code)


def studio_linux_k2(build_env: bazel.BuildEnv) -> None:
  """Runs studio-linux-k2 target."""
  setup_environment(build_env)
  flags = build_flags(
      build_env,
      test_tag_filters='-noci:studio-linux,-qa_smoke,-qa_fast,-qa_unreliable,-perfgate-release,-no_k2,-kotlin-plugin-k2',
  )
  flags.extend([
      '--bes_keywords=k2',
      '--jvmopt=-Didea.kotlin.plugin.use.k2=true',
      '--jvmopt=-Dlint.use.fir.uast=true',
  ])
  result = studio.run_tests(build_env, flags, _BASE_TARGETS)
  copy_agp_supported_versions(build_env)
  if studio.is_build_successful(result) and result.exit_code != bazel.EXITCODE_NO_TESTS_FOUND:
    return

  raise studio.BazelTestError(exit_code=result.exit_code)


def setup_environment(build_env: bazel.BuildEnv) -> None:
  """Sets up the environment for the build."""
  # If DIST_DIR does not exist, create one.
  if not build_env.dist_dir:
    build_env.dist_dir = tempfile.mkdtemp('dist-dir')


def build_flags(
    build_env: bazel.BuildEnv,
    *,
    test_tag_filters: str = '',
  ) -> List[str]:
  """Returns the flags to use for testing."""
  dist_path = pathlib.Path(build_env.dist_dir)
  as_build_number = build_env.build_number
  if as_build_number.startswith('P'):
    as_build_number = '0' + as_build_number[1:]
  profile_path = dist_path / f'profile-{build_env.build_number}.json.gz'

  return [
      # TODO(b/173153395) Switch back to dynamic after Bazel issue is resolved.
      # See https://github.com/bazelbuild/bazel/issues/22482
      '--config=remote-exec',

      '--build_manual_tests',

      f'--define=meta_android_build_number={build_env.build_number}',

      f'--profile={profile_path}',

      f'--test_tag_filters={test_tag_filters}',

      '--tool_tag=studio_linux.sh',
      f'--embed_label={as_build_number}',

      '--jobs=500',
  ]


def copy_agp_supported_versions(build_env: bazel.BuildEnv) -> None:
  """Copies the agp-supported-versions file to the dist directory."""
  workspace_path = pathlib.Path(build_env.workspace_dir)
  dist_path = pathlib.Path(build_env.dist_dir)
  shutil.copy2(
      workspace_path / 'tools/base/build-system/supported-versions.properties',
      dist_path / 'agp-supported-versions.properties',
  )


def write_owners_zip(build_env: bazel.BuildEnv) -> None:
  """Writes all OWNERS files to an owners.zip file."""
  workspace_path = pathlib.Path(build_env.workspace_dir)
  dist_path = pathlib.Path(build_env.dist_dir)

  with zipfile.ZipFile(dist_path / 'owners.zip', 'w') as owners_zip:
    owners_paths = itertools.chain(
        workspace_path.glob('tools/**/OWNERS'),
        workspace_path.glob('prebuilts/**/OWNERS'),
    )
    for path in owners_paths:
      owners_zip.write(path, arcname=path.relative_to(workspace_path))


def copy_artifacts(
    build_env: bazel.BuildEnv,
    missing_ok: bool = False,
) -> None:
  """Copies artifacts to the dist directory."""
  dist_path = pathlib.Path(build_env.dist_dir)
  (dist_path / 'artifacts').mkdir(parents=True, exist_ok=True)
  studio.copy_artifacts(build_env, _ARTIFACTS, missing_ok)
  write_owners_zip(build_env)
