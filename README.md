# Norda

**Walk. Run. Explore.** — Minimalist, offline-first outdoor navigasyon
uygulaması (Android, Kotlin, sıfır çalışma zamanı bağımlılığı).

Yürüyüş ve koşu kaydını pusula, offline harita, waypoint'ler ve "Return to
Start" navigasyonuyla birleştirir.

- Ürün kapsamı, teknik tasarım ve yol haritası: **[docs/MVP.md](docs/MVP.md)**
- Süreç: SemVer, her uygulama-etkileyen değişikliğe etiket + release,
  TDD (kırmızı → yeşil → refactor). Ayrıntı: MVP belgesi, 13. ve 15. bölümler.

**Ağ kuralı:** ağa dokunan tek sınıf `TileDownloader`'dır (harita paketi
indirme). Tracking, pusula, navigasyon ve GPX internetsiz çalışır; konum
verisi cihazda kalır, telemetri yoktur. Ayrıntı: [docs/PRIVACY.md](docs/PRIVACY.md).

Saha turu protokolü (Faz 9 · RC): [docs/SAHA.md](docs/SAHA.md).

## Testler diş geçiriyor mu?

MVP.md 13.1/5 gereği çekirdeğe kasıtlı hatalar sokulur, hangi testlerin
düştüğü kaydedilir ve hepsi geri alınır. Son tur: **v0.9.0** — 96 test,
**8/8 mutasyon yakalandı**.

| Kasıtlı hata | Isıran test(ler) |
|---|---|
| Kerterizde Δλ işareti ters (taslaktaki tarihi hata) | `bearingDueEastOnEquator` `bearingDueWestOnEquator` `bearingIstanbulToKaaba` `bearingAndDistanceToEastStart` |
| `yTile` sınır kelepçesi kaldırıldı | `latitudeIsClampedToProjectionDomain` |
| GPS doğruluk eşiği 30 m → 300 m | `poorAccuracyIsRejected` `evaluateNamesTheRejectionReason` `poorAccuracyCannotResume` `filterCountsFeedCalibration` |
| Yükseklik histerezisi 4 m → 0 | `flatNoiseAccumulatesNothing` |
| GPX yazıcıda XML kaçışı kaldırıldı | `roundTripPreservesTrackAndWaypoints` |
| `waypoint` göç yolundan düşürüldü (0.7.0 hatasının tekrarı) | `upgradeFromV1CreatesWaypointTable` |
| Şarjda negatif pil "tüketimi" raporlandı | `chargingDuringRecordingGivesNoDrain` |
| Return to Start kerterizi ters yön | `bearingAndDistanceToEastStart` |

Turun kendi bulgusu da kapatıldı: şema taban listesine sızan DDL'yi parite
testi göremiyordu (iki taraf birlikte kirleniyor); sürüm başına ifade
sayısını donduran `statementCountPerVersionIsFrozen` eklendi.

Lisans: [MIT](LICENSE). Harita verisi: © OpenStreetMap contributors (ODbL).
