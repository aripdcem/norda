#!/usr/bin/env bash
#
# Etiketin build.gradle.kts'deki versionName VE versionCode ile tuttuğunu
# doğrular.
#
# Sürüm numarası iki yerde duruyor: derleme dosyasında ve etikette. Ayrı
# düştüklerinde ortaya "v4.3" diye yayımlanmış ama içinde 4.2 yazan bir APK
# çıkar; bu ancak telefona kurup Ayarlar'a bakınca fark edilir. Yayın burada,
# daha ilk adımda durur.
#
# versionCode için formül (docs/MVP.md, 15.1): MAJOR×10000 + MINOR×100 +
# PATCH. Formül tutuyorsa monotonluk SemVer'den bedavaya gelir; elle yanlış
# artırılmış bir kod yayına inmez.
#
# Kullanım: check-tag.sh v4.2.0

set -euo pipefail

tag="${1:?kullanım: check-tag.sh <etiket>}"
expected="${tag#v}"

if [[ ! "$expected" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "::error::Etiket vX.Y.Z biçiminde olmalı: $tag"
  exit 1
fi

actual=$(grep -oE 'versionName[[:space:]]*=[[:space:]]*"[^"]+"' app/build.gradle.kts \
  | head -1 | cut -d'"' -f2)

if [ "$expected" != "$actual" ]; then
  echo "::error::Etiket ($tag) ile versionName ($actual) uyuşmuyor." \
       "app/build.gradle.kts içindeki versionCode/versionName'i güncelleyip etiketi yeniden atın."
  exit 1
fi

IFS='.' read -r major minor patch <<< "$expected"
expected_code=$((major * 10000 + minor * 100 + patch))
actual_code=$(grep -oE 'versionCode[[:space:]]*=[[:space:]]*[0-9]+' app/build.gradle.kts \
  | head -1 | grep -oE '[0-9]+$')

if [ "$expected_code" != "$actual_code" ]; then
  echo "::error::versionCode ($actual_code) formülle uyuşmuyor:" \
       "$tag → MAJOR×10000+MINOR×100+PATCH = $expected_code (MVP.md 15.1)."
  exit 1
fi

echo "Etiket, versionName ve versionCode tutuyor: $actual ($actual_code)"
