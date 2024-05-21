#!/usr/bin/env bash
set -eu

# Install everything under /tmp/android-sdk.
rm -rf /tmp/android-sdk
mkdir /tmp/android-sdk
cd /tmp/android-sdk

# Install the SDK command-line tools (which includes the SDK manager).
if [[ "$(uname)" == "Linux" ]]; then
    TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
elif [[ "$(uname)" == "Darwin" ]]; then
    TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip"
else
    echo "Unrecognized OS: $OS_NAME"
    exit 1
fi
curl -L "$TOOLS_URL" -o tools.zip
unzip tools.zip

# According to https://developer.android.com/tools/sdkmanager, the command-line tools should be
# moved under a directory named 'latest' in order to detect the SDK root correctly.
mkdir latest
mv cmdline-tools/* latest
mv latest cmdline-tools

# Accept all licenses to allow the downloads below.
yes | ./cmdline-tools/latest/bin/sdkmanager --licenses

# Use the SDK manager to install the Android SDK, NDK, etc. required for the Android Studio build.
# The build-tools version comes from tools/base/bazel/repositories.bzl.
# The NDK version comes from tools/base/bazel/platform_specific.bazelrc.
# The platform version comes from //prebuilts/studio/sdk:platforms/latest.
./cmdline-tools/latest/bin/sdkmanager --install 'build-tools;30.0.3'
./cmdline-tools/latest/bin/sdkmanager --install 'platforms;android-34'
./cmdline-tools/latest/bin/sdkmanager --install 'ndk;20.1.5948944'
