#!/usr/bin/env bash
#
# Reads compileSdk from the build file and makes sure that platform is present
# on the runner.
#
# The version is not written a second time here. In the GitLab configuration
# the image tag (`android-sdk:35`) was pinned by hand and had to be bumped
# together with compileSdk; when that was forgotten the build broke for no
# apparent reason. The single source of truth is app/build.gradle.kts.

set -uo pipefail

compile_sdk=$(grep -oE 'compileSdk[[:space:]]*=[[:space:]]*[0-9]+' app/build.gradle.kts \
  | grep -oE '[0-9]+$' | head -1)

if [ -z "$compile_sdk" ]; then
  echo "::error::could not read compileSdk from app/build.gradle.kts"
  exit 1
fi
echo "compileSdk = $compile_sdk"

sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
sdkmanager="$sdk_root/cmdline-tools/latest/bin/sdkmanager"

if [ ! -x "$sdkmanager" ]; then
  echo "::notice::sdkmanager not found; continuing with the runner's preinstalled SDK"
  exit 0
fi

yes | "$sdkmanager" --licenses >/dev/null 2>&1 || true

# Returns quickly if the component is already installed. If it cannot be
# installed the run does not break here: when something is missing, the build
# step reports the real error far more clearly.
if ! "$sdkmanager" "platforms;android-$compile_sdk" "build-tools;$compile_sdk.0.0" >/dev/null; then
  echo "::warning::could not install platforms;android-$compile_sdk or build-tools;$compile_sdk.0.0"
fi
