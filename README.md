# Norda

**Walk. Run. Explore.** — Minimalist, offline-first outdoor navigasyon
uygulaması (Android, Kotlin, sıfır çalışma zamanı bağımlılığı).

Yürüyüş ve koşu kaydını pusula, offline harita, waypoint'ler ve "Return to
Start" navigasyonuyla birleştirir. [WalkRun](https://github.com/aripdcem/WalkRun)
ve [Compass](https://github.com/aripdcem/compass) projelerinin test edilmiş
modüllerinin birleşiminden doğar.

- Ürün kapsamı, teknik tasarım ve yol haritası: **[docs/MVP.md](docs/MVP.md)**
- Süreç: SemVer, her uygulama-etkileyen değişikliğe etiket + release,
  TDD (kırmızı → yeşil → refactor). Ayrıntı: MVP belgesi, 13. ve 15. bölümler.

**Ağ kuralı:** ağa dokunan tek sınıf `TileDownloader`'dır (harita paketi
indirme). Tracking, pusula, navigasyon ve GPX internetsiz çalışır; konum
verisi cihazda kalır, telemetri yoktur.

Lisans: [MIT](LICENSE). Harita verisi: © OpenStreetMap contributors (ODbL).
