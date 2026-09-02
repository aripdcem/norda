#!/usr/bin/env bash
#
# Verifies that the tag agrees with BOTH versionName AND versionCode in
# build.gradle.kts.
#
# The version number lives in two places: the build file and the tag. When
# they drift apart you end up with an APK published as "v4.3" that says 4.2
# inside; that is only noticed after installing it on a phone and looking at
# Settings. The release stops here, at the very first step.
#
# The formula for versionCode (docs/MVP.md, 15.1): MAJOR×10000 + MINOR×100 +
# PATCH. If the formula holds, monotonicity comes for free from SemVer; a
# hand-bumped wrong code never reaches a release.
#
# Usage: check-tag.sh v4.2.0

set -euo pipefail

tag="${1:?usage: check-tag.sh <tag>}"
expected="${tag#v}"

if [[ ! "$expected" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "::error::Tag must have the form vX.Y.Z: $tag"
  exit 1
fi

actual=$(grep -oE 'versionName[[:space:]]*=[[:space:]]*"[^"]+"' app/build.gradle.kts \
  | head -1 | cut -d'"' -f2)

if [ "$expected" != "$actual" ]; then
  echo "::error::Tag ($tag) and versionName ($actual) do not match." \
       "Update versionCode/versionName in app/build.gradle.kts and re-create the tag."
  exit 1
fi

IFS='.' read -r major minor patch <<< "$expected"
expected_code=$((major * 10000 + minor * 100 + patch))
actual_code=$(grep -oE 'versionCode[[:space:]]*=[[:space:]]*[0-9]+' app/build.gradle.kts \
  | head -1 | grep -oE '[0-9]+$')

if [ "$expected_code" != "$actual_code" ]; then
  echo "::error::versionCode ($actual_code) does not match the formula:" \
       "$tag → MAJOR×10000+MINOR×100+PATCH = $expected_code (MVP.md 15.1)."
  exit 1
fi

echo "Tag, versionName and versionCode agree: $actual ($actual_code)"
