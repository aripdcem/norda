#!/usr/bin/env bash
#
# Copies the built APK under dist/, named with its version and commit, and
# writes its SHA-256 digest next to it.
#
# A meaningful name pays off: when the downloaded file sits there as
# "app-debug.apk", which version it is only becomes clear after installing it
# and looking at Settings. Here the name says it outright:
# norda-0.1.0-33-debug-1a2b3c4.apk
#
# Usage: collect-apk.sh <debug|release>

set -euo pipefail

variant="${1:-debug}"

version_name=$(grep -oE 'versionName[[:space:]]*=[[:space:]]*"[^"]+"' app/build.gradle.kts \
  | head -1 | cut -d'"' -f2)
version_code=$(grep -oE 'versionCode[[:space:]]*=[[:space:]]*[0-9]+' app/build.gradle.kts \
  | grep -oE '[0-9]+$' | head -1)

if [ -z "$version_name" ] || [ -z "$version_code" ]; then
  echo "::error::could not read versionName/versionCode from app/build.gradle.kts"
  exit 1
fi

short_sha=$(git rev-parse --short=7 HEAD 2>/dev/null || echo "${GITHUB_SHA:0:7}")

source_apk=$(find "app/build/outputs/apk/$variant" -maxdepth 1 -name '*.apk' 2>/dev/null | head -1)
if [ -z "$source_apk" ]; then
  echo "::error::no APK found under app/build/outputs/apk/$variant"
  exit 1
fi

# An unsigned release APK comes out as "app-release-unsigned.apk". The name
# must not hide that: an unsigned APK cannot be installed on a phone, and
# whoever downloads it should see so from the file name.
case "$(basename "$source_apk")" in
  *unsigned*) label="$variant-unsigned" ;;
  *)          label="$variant" ;;
esac

name="norda-$version_name-$version_code-$label-$short_sha"
mkdir -p dist
cp "$source_apk" "dist/$name.apk"
( cd dist && sha256sum "$name.apk" > "$name.apk.sha256" )

size=$(du -h "dist/$name.apk" | cut -f1)
digest=$(cut -d' ' -f1 < "dist/$name.apk.sha256")

if [ -n "${GITHUB_OUTPUT:-}" ]; then
  {
    echo "name=$name"
    echo "path=dist/$name.apk"
    echo "version=$version_name"
    echo "code=$version_code"
    echo "signed=$([ "$label" = "$variant" ] && echo true || echo false)"
  } >> "$GITHUB_OUTPUT"
fi

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    echo "### APK: \`$name.apk\`"
    echo
    echo "| | |"
    echo "|---|---|"
    echo "| Version | $version_name ($version_code) |"
    echo "| Type | $label |"
    echo "| Size | $size |"
    echo "| SHA-256 | \`$digest\` |"
  } >> "$GITHUB_STEP_SUMMARY"
fi

echo "$name.apk  ($size)"
