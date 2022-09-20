#!/bin/sh

function usage() {
  declare -r prog="${0##*/}"
}


# Make
(
  cd "$out_dir"
  "$cmake_bin" -DCMAKE_BUILD_TYPE=Release "$top/tools/idea/native/MacEnvReader"
  "$cmake_bin" --build .
)

verifyArchs "$out_dir/printenv"

# Copy to Dist
[[ -n "${dist_dir:-}" ]] || exit 0
cp "$out_dir/printenv"  "$dist_dir"
echo "Built $dist_dir/printenv"