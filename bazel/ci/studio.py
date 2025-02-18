"""Implements shared functions for studio_* presubmit and postsubmit checks."""

import dataclasses
import enum
import json
import os
import pathlib
import shutil
from typing import Iterable, List, Sequence, Tuple
import uuid

from tools.base.bazel.ci import bazel
from tools.base.bazel.ci import errors


@dataclasses.dataclass(frozen=True,kw_only=True)
class BazelTestError(errors.CIError):
  """Represents an error originating from the bazel test."""

  exit_code: int

  def __str__(self) -> str:
    return f'Bazel test exited with code {self.exit_code}'


@dataclasses.dataclass(frozen=True,kw_only=True)
class CopyArtifactsError(errors.CIError):
  """Represents an error copying artifacts."""

  artifact: str
  exit_code: int = 1

  def __str__(self) -> str:
    return f'Failed to copy artifact: {self.artifact}'


class BuildType(enum.Enum):
  """Represents the type of build being run."""
  LOCAL      = 1
  PRESUBMIT  = 2
  POSTSUBMIT = 3

  @classmethod
  def from_build_number(cls, build_number: str) -> 'BuildType':
    """Returns the build type corresponding to the given build number."""
    if build_number == 'SNAPSHOT':
      return BuildType.LOCAL
    if build_number.startswith('P'):
      return BuildType.PRESUBMIT
    return BuildType.POSTSUBMIT


@dataclasses.dataclass(frozen=True)
class BazelTestResult:
  """Represents the output of a bazel test."""
  exit_code: int
  bes_path: pathlib.Path


def run_bazel_test(
    build_env: bazel.BuildEnv,
    flags: List[str] = [],
    targets: Sequence[str] = [],
) -> BazelTestResult:
  """Runs the bazel test invocation."""
  flags = flags.copy()
  dist_path = pathlib.Path(build_env.dist_dir)
  invocation_id = str(uuid.uuid4())

  build_type = BuildType.from_build_number(build_env.build_number)
  bes_path = dist_path / f'bazel-{build_env.build_number}.bes'

  worker_instances = '2' if build_type == BuildType.LOCAL else 'auto'
  if build_type == BuildType.POSTSUBMIT:
    flags.extend([
        '--bes_keywords=ab-postsubmit',
        '--nocache_test_results',
    ])

  if dist_path.exists():
    sponge_redirect_path = dist_path / 'upsalite_test_results.html'
    sponge_redirect_path.write_text(f'<head><meta http-equiv="refresh" content="0; url=\'https://fusion2.corp.google.com/invocations/{invocation_id}\'" /></head>')
    sponge_invocations_path = dist_path / 'sponge-invocations.txt'
    sponge_invocations_path.write_text(invocation_id)

  flags.extend([
      '--config=ci',
      '--config=ants',

      f'--invocation_id={invocation_id}',

      f'--build_event_binary_file={bes_path}',
      f'--build_metadata=ANDROID_BUILD_ID={build_env.build_number}',
      f'--build_metadata=ANDROID_TEST_INVESTIGATE="http://ab/tests/bazel/{invocation_id}"',
      f'--build_metadata=ab_build_id={build_env.build_number}',
      f'--build_metadata=ab_target={build_env.build_target_name}',
      f'--//tools/base/bazel/ci:ab_target={build_env.build_target_name}',

      f'--worker_max_instances={worker_instances}',

      '--experimental_enable_execution_graph_log',
      '--experimental_execution_graph_log_dep_type=all',
      # Experiment with setting kelloggs ATP Discriminator labels.
      f'--bes_keywords=b/{build_env.branch}',
      f'--bes_keywords=test_result.build_id/{build_env.build_number}',
      f'--bes_keywords=test_result.build_target/{build_env.build_target_name}',
      '--bes_keywords=atp_test_name/android_studio/bazel',
      '--bes_keywords=trigger/BUILD',
      '--build_metadata=cluster=bazel',
      '--build_metadata=run_target=bazel',
  ])

  target_file = dist_path / 'targets.txt'
  target_file.write_text('\n'.join(targets))
  result = build_env.bazel_test(*flags, '--target_pattern_file', str(target_file))

  return BazelTestResult(
      exit_code=result.returncode,
      bes_path=bes_path,
  )


def run_tests(
    build_env: bazel.BuildEnv,
    flags: Sequence[str],
    targets: Sequence[str],
  ) -> BazelTestResult:
  """Runs the bazel test invocation."""
  result = run_bazel_test(build_env, flags, targets)

  # If an uncommon exit code happens, copy extra worker logs.
  if result.exit_code not in {
      bazel.EXITCODE_SUCCESS,
      bazel.EXITCODE_TEST_FAILURES,
  }:
    copy_bazel_logs(build_env)

  collect_logs(build_env, result.bes_path)

  return result


def copy_bazel_logs(build_env: bazel.BuildEnv) -> None:
  """Copies bazel internal logs into output."""
  dest_path = pathlib.Path(build_env.dist_dir) / 'bazel_logs'
  dest_path.mkdir(parents=True, exist_ok=True)
  server_log = build_env.bazel_info('server_log').stdout.decode('utf-8').strip()
  shutil.copy2(server_log, dest_path / 'java.log')
  result = build_env.bazel_info('output_base')
  output_base = pathlib.Path(result.stdout.decode('utf-8').strip())
  worker_logs = output_base / 'bazel-workers'
  for path in worker_logs.glob('*.log'):
    shutil.copy2(path, dest_path / path.name)


def collect_logs(build_env: bazel.BuildEnv, bes_path: pathlib.Path) -> None:
  """Runs the log collector."""
  build_type = BuildType.from_build_number(build_env.build_number)
  dist_path = pathlib.Path(build_env.dist_dir)
  error_log_path = dist_path / 'logs/build_error.log'
  perfgate_data_path = dist_path / 'perfgate_data.zip'

  args = [
      '//tools/vendor/adt_infra_internal/rbe/logscollector:logs-collector',
      '--config=ci',
      '--config=remote-exec',
      '--',
      '-bes',
      str(bes_path),
      '-error_log',
      str(error_log_path),
      '-module_info',
      str(dist_path),
  ]
  if build_type == BuildType.POSTSUBMIT:
    args.append('-perfzip')
    args.append(perfgate_data_path)

  build_env.bazel_run(*args)


def copy_artifacts(
    build_env: bazel.BuildEnv,
    files: Iterable[Tuple[str,str]],
    missing_ok: bool = False,
) -> None:
  """Copies artifacts to the dist dir.

  Args:
    files: Iterable of tuples consisting of (src, dest).
           src is relative to the bazel-bin output and can be a glob.
           dest is relative to dist_dir and can be either a file or directory.
    missing_ok: If true, missing files will be ignored.
  """
  dist_path = pathlib.Path(build_env.dist_dir)
  result = build_env.bazel_info('--config=ci', 'bazel-bin')
  bin_path = pathlib.Path(result.stdout.decode('utf-8').strip())

  binary_sizes = {}
  for src_glob, dest in files:
    src_files = list(bin_path.glob(src_glob))
    if not src_files and not missing_ok:
      raise CopyArtifactsError(artifact=src_glob)
    for src in src_files:
      shutil.copy2(src, dist_path / dest)
      binary_sizes[f'{src}[bytes]'] = os.stat(src).st_size
  with open(dist_path / 'bloatbuster_report.binary_sizes.json', 'w') as f:
    json.dump(binary_sizes, f)


def is_build_successful(result: BazelTestResult) -> bool:
  """Returns True if the build portion of the bazel test was successful."""
  return result.exit_code in {
      bazel.EXITCODE_SUCCESS,
      # Test failures are handled elsewhere, so build is considered successful.
      bazel.EXITCODE_TEST_FAILURES,
      bazel.EXITCODE_NO_TESTS_FOUND,
  }
