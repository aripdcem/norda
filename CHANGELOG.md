# Changelog

Biçim [Keep a Changelog](https://keepachangelog.com/), sürümleme
[SemVer](https://semver.org/) — kurallar için `docs/MVP.md` 15. bölüm.

## [0.1.0] - 2026-08-25

### Added

- Faz 1 — Sensor Core: konum ve yön hata ayıklama ekranı (izin akışı, GPS fix
  bilgileri, rotation-vector yönü, başlangıç noktasına mesafe/kerteriz).
- `core/geo`: mesafe (haversine) + büyük daire kerterizi + açı aritmetiği,
  fiziksel sabitlere dayanan JVM testleriyle.
- CI: her itişte test + lint + debug APK; `v*` etiketinde `check-tag`
  doğrulamasıyla imzalı release APK + GitHub sürümü.
