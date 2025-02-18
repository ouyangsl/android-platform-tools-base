"""Module for running bazel-diff."""

import logging
import pathlib
import time
from typing import Sequence

from tools.base.bazel.ci import bazel


def generate_hash_file(
    build_env: bazel.BuildEnv,
    external_repos: Sequence[str],
    output_path: pathlib.Path,
    deps_output_path: pathlib.Path | None = None,
):
  """Generates the hash file for the current build."""
  start = time.time()
  args = [
      '//tools/base/bazel:bazel-diff',
      '--',
      '--verbose',
      'generate-hashes',
      '--bazelPath',
      build_env.bazel_path,
      '--workspacePath',
      build_env.workspace_dir,
      '--fineGrainedHashExternalRepos',
      ','.join(external_repos),
  ]
  if deps_output_path:
    args.extend(['--depEdgesFile', deps_output_path])
  args.append(str(output_path))
  build_env.bazel_run(
      *args,
      timeout=600,
  )
  end = time.time()
  logging.info('generate-hashes took %d seconds', end - start)


def get_impacted_targets(
    build_env: bazel.BuildEnv,
    starting_hashes_path: str,
    final_hashes_path: str,
    dep_edges_path: pathlib.Path,
    output_path: pathlib.Path,
) -> None:
  """Generates the list of impacted targets given base and current hash file paths."""
  start = time.time()
  build_env.bazel_run(
      '//tools/base/bazel:bazel-diff',
      '--',
      '--verbose',
      'get-impacted-targets',
      '--startingHashes',
      starting_hashes_path,
      '--finalHashes',
      final_hashes_path,
      '--depEdgesFile',
      str(dep_edges_path),
      '--output',
      str(output_path),
      timeout=300,
  )
  end = time.time()
  logging.info('get-impacted-targets took %d seconds', end - start)
