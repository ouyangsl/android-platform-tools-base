#!/bin/bash
set -eu

# Creates the directory if it does not exist and returns its absolute path
function make_target_dir() {
  mkdir -p "$1" && realpath "$1"
}

# Expected arguments:
declare -r out_dir=$(make_target_dir "$1")
declare -r dist_dir=$(make_target_dir "$2")
declare -r build_number="$3"

declare -r script_dir=$(realpath "$(dirname "$0")")
declare -r top=$(realpath "$(dirname "$0")/../../..")

declare -r re_client="${top}/prebuilts/remoteexecution-client/latest"

(
  # unset RBE_ env variable from parent process
  unset $(env | cut -d= -f1 | grep 'RBE_')
  cd $out_dir

  declare -r cfg=$script_dir/android-studio.cfg
  echo "using configuration from $cfg"
  cat $cfg
  echo ""

  ${re_client}/bootstrap --re_proxy="${re_client}/reproxy" --cfg=$cfg

  # always shutdown at the end
  trap '${re_client}/bootstrap --shutdown --cfg=$cfg' EXIT

  (
    rm -rf re_files && mkdir re_files && cd re_files
    cp ${script_dir}/linux_tools.sh .
    mkdir -p ./tools/idea/native/ && cp -r ${top}/tools/idea/native/. ./tools/idea/native/

    ${re_client}/rewrapper  --labels=type=tool --exec_strategy=remote \
    --cfg=$cfg \
    --inputs=. \
    --output_files=dist/fsnotifier \
    -- ./linux_tools.sh out dist $build_number
  )
)

cp $out_dir/re_files/dist/fsnotifier $dist_dir/