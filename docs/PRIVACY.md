# Norda — Gizlilik Politikası / Privacy Policy

_Son güncelleme / Last updated: 2026-08-25_

## Türkçe

Norda, çevrimdışı çalışmak üzere tasarlanmış bir outdoor navigasyon
uygulamasıdır. Gizlilik duruşu tek cümledir: **konum verin cihazınızda
kalır.**

- **Toplanan veri:** Kayıt sırasında GPS konumları, rakım ve pil yüzdesi
  yalnızca cihazdaki SQLite veritabanına yazılır. Hesap yoktur, bulut
  yoktur, telemetri/analitik/reklam SDK'sı yoktur.
- **Ağ erişimi:** Uygulamada ağa dokunan tek bileşen harita paketi
  indiricisidir (`TileDownloader`); yalnızca GitHub Releases üzerinden
  yayımlanan harita paketlerini ve paket listesini indirir. Bu isteklerde
  kimlik, konum ya da kullanım verisi gönderilmez. Kayıt, pusula,
  navigasyon ve GPX tamamen çevrimdışı çalışır.
- **İzinler:** Konum (kayıt ve pusula için), bildirim (kayıt sürerken
  kalıcı bildirim için). İkisi de yalnız bu amaçlarla kullanılır.
- **Paylaşım:** Veri ancak siz dışa aktarırsanız (GPX) cihazdan çıkar ve
  nereye gideceğini siz seçersiniz.
- **Silme:** Aktiviteler ve noktalar uygulama içinden silinebilir;
  uygulamayı kaldırmak tüm veriyi kaldırır.

## English

Norda is an offline-first outdoor navigation app. Its privacy stance is a
single sentence: **your location data stays on your device.**

- **Data collected:** While recording, GPS positions, altitude and battery
  percentage are written only to the on-device SQLite database. There are
  no accounts, no cloud, no telemetry/analytics/ads SDKs.
- **Network access:** The only component that touches the network is the
  map package downloader (`TileDownloader`); it fetches map packages and
  their index from GitHub Releases only. No identity, location or usage
  data is sent. Recording, compass, navigation and GPX work fully offline.
- **Permissions:** Location (recording and compass), notifications (the
  persistent notification while recording). Both are used only for those
  purposes.
- **Sharing:** Data leaves the device only when you export it (GPX), to a
  destination you choose.
- **Deletion:** Activities and waypoints can be deleted in-app;
  uninstalling removes all data.

İletişim / Contact: <https://github.com/aripdcem/norda/issues>
