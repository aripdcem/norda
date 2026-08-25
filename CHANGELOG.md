# Changelog

Biçim [Keep a Changelog](https://keepachangelog.com/), sürümleme
[SemVer](https://semver.org/) — kurallar için `docs/MVP.md` 15. bölüm.

## [0.3.0] - 2026-08-25

### Added

- Faz 3 — Foreground Tracking: kayıt artık `TrackingService`'in malı; ekran
  kapansa da, başka uygulamaya geçilse de sürer. Kalıcı bildirim durum ·
  süre · mesafe gösterir ve kayda geri götürür.
- Sistem servisi öldürüp yeniden başlatırsa (START_STICKY) kayıt diskteki
  yarım aktiviteden devralınır (`RecordingSession.prime`, testli): mesafe ve
  süre korunur, yükseklik saklanan rakımlardan aynı histerezisle kurulur.
- Bildirim izni (Android 13+) konumla birlikte istenir; reddi kaydı
  engellemez.

### Changed

- Kayıt ekranı artık yalnızca gösterge; geri tuşu kaydı bitirmez, kayıt
  arka planda sürer. Bitirme yalnız Bitir düğmesiyle.
- Home, servis kayıttayken kurtarma çalıştırmaz (canlı kayıt "yarım"
  sanılıp kapatılmaz).

## [0.2.0] - 2026-08-25

### Added

- Faz 2 — Activity Engine: Walk/Run kaydı. Home (tip seç + BAŞLAT), kayıt
  ekranı (süre, mesafe, canlı tempo, yükseklik ▲▼, duraklat/devam/bitir) ve
  geçmiş listesi (uzun basış siler).
- Kayıt çekirdeği saf Kotlin'de, 37 yeni JVM testiyle: GPS filtresi,
  duraklatma bilinçli kronometre, otomatik duraklat/devam (20 sn / 8 m),
  histerezisli yükseklik kazanımı/kaybı (4 m), istatistikler ve kayıt durum
  makinesi (`RecordingSession`).
- SQLite kalıcılığı (WAL, ince katman): fix başına tek INSERT; süreç ölümüyle
  yarım kalan kayıt açılışta geçmişe kurtarılır.
- Faz 1 tanılama ekranı Home'dan erişilebilir durumda korundu.

### Notlar

- Kayıt bu fazda ekran açıkken sürer; arka plan kaydı Faz 3'te foreground
  service ile geliyor.

## [0.1.1] - 2026-08-25

### Changed

- Release iş akışı imzalama secret'larını `ANDROID_*` adlarından okur; alias
  boşsa `norda`, anahtar parolası boşsa depo parolası varsayılır.
- İlk imzalı APK yayını.

## [0.1.0] - 2026-08-25

### Added

- Faz 1 — Sensor Core: konum ve yön hata ayıklama ekranı (izin akışı, GPS fix
  bilgileri, rotation-vector yönü, başlangıç noktasına mesafe/kerteriz).
- `core/geo`: mesafe (haversine) + büyük daire kerterizi + açı aritmetiği,
  fiziksel sabitlere dayanan JVM testleriyle.
- CI: her itişte test + lint + debug APK; `v*` etiketinde `check-tag`
  doğrulamasıyla imzalı release APK + GitHub sürümü.
