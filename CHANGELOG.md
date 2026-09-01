# Changelog

Biçim [Keep a Changelog](https://keepachangelog.com/), sürümleme
[SemVer](https://semver.org/) — kurallar için `docs/MVP.md` 15. bölüm.

## [1.0.3] - 2026-09-01

### Fixed

- F-12 (matris 20 turu: güç tasarrufu açık, 33 dk — 61 nokta, 20:28'lik
  boşluk; fix'ler yalnız cihaz uyanıkken aktı, 1:24'lük telefon görüşmesi
  54 noktalık kümeyle birebir): güç tasarrufunda sistem ekran kapalıyken
  konumu durdurabiliyor ve kullanıcı bunu yürüyüşün sonunda öğreniyordu.
  Başlat'a basınca güç tasarrufu açıksa artık konum-kapalı diyaloğunun
  ikizi çıkar: "Güç tasarrufu açık. Ekran kapalıyken sistem GPS'i
  durdurabilir — iz seyrek kaydolabilir." → Pil ayarları / Yine de
  başlat. Mod desteklenmeye devam eder; F-5'in kayıt-sırası görünürlüğü
  başlangıç anına da taşındı.

## [1.0.2] - 2026-08-30

### Fixed

- F-11 (iki saha kanıtı — 2t: 12,7 m/s, gece turu: 12,85 m/s): ilk fix
  "oturma" sırasında 13–26 m sapık gelebiliyor ve çapa yapıldığı için
  hayalet mesafe, sıçrayan nokta reddedilse bile bir sonraki noktayla
  yine sayılıyordu. İki parça çözüm: (1) hız tavanı sahayla 10 m/s'ye
  kalibre edildi (MVP 5.2'nin öngördüğü kalibrasyon — yürüyüş/koşu
  ürününde 36 km/h üstü hareket koşu değildir); (2) oturma kapısı: ilk
  fix çapa değil adaydır, ikinci fix'le fiziksel tutarlılık doğrulanana
  dek kayda girmez; çift ışınlama verirse suçlu ilk fix'tir ve aday
  değiştirilir. Gece verisinde beklenen etki: 25,7 m'lik hayalet
  başlangıç hiç doğmazdı. Bedel tek fix'lik gecikme (~1–2 sn).
- `RecordingSession.onFix` artık bu çağrıda kayda giren noktaları
  (kalıcılaştırma bayraklarıyla) liste olarak döndürür: doğrulanan aday +
  fix aynı çağrıda dönebilir; rakım bayrağı noktaya aittir.

## [1.0.1] - 2026-08-30

### Fixed

- F-10 (saha: "Pusulaya girip çıkınca kilit anında geldi"): pusulanın
  ağ-sağlayıcı isteği, birçok cihazda GNSS motoruna kaba konum tohumlayıp
  kilidi dakikalardan saniyelere indiriyor. Aynı tohumlama artık bilinçli:
  Home ısıtması ve kayıt servisi ağ sağlayıcısını YALNIZ tohum olarak
  dinler — ağ fix'i sağlayıcı süzgeciyle ne göstergeye ne ize girebilir
  (temiz iz duruşu aynen korunur) ve ilk gerçek GPS fix'iyle bırakılır
  (pil kuralı). MVP.md 11'e duruş cümlesi eklendi.

## [1.0.0] - 2026-08-29

### MVP tamam — "Yürü. Koş. Keşfet. Yolunu kaybetme."

- Üç temiz saha turu tamamlandı (`docs/SAHA.md` günlüğü): belgedeki kapsam
  sahada doğrulandı. Kapı turunda mesafe çapraz doğrulaması **sıfır fark**
  verdi (5613,7 ↔ 5613,7 m), yükseklik birebir, 1738 nokta = kabul sayacı.
- Kapsam: Walk/Run kaydı (filtreli GPS, otomatik duraklatma, histerezisli
  yükseklik), foreground kayıt + çökme kurtarması, elle çizilen offline
  harita (kendi MBTiles paket hattıyla), pusula (gerçek kuzey + manyetik
  bozulma uyarısı), Return to Start, waypoint'ler, GPX içe/dışa (gömülü
  telemetri raporuyla), aktivite başına pil ölçümü, GPS hazırlık ve uydu
  görünürlüğü.
- Duruş: sıfır çalışma zamanı bağımlılığı; konum cihazda kalır, telemetri
  yok; ağa dokunan tek sınıf harita paketi indiricisi. 102 JVM testi;
  dokuz saha bulgusunun dokuzu da tur döngüsü içinde kapatıldı (F-1…F-9).

## [0.9.8] - 2026-08-27

### Added

- F-9 ("sürekli GPS arıyor" raporu): uydu görünürlüğü. Home'daki hazırlık
  satırı fix yokken "GPS aranıyor… · uydu 0/7" (fix'te/görülen) der;
  Tanılama'nın konum bölümü fix olsun olmasın "uydu: X fix'te / Y görülen"
  satırı taşır. Görülen 0 = gökyüzü yok (kapalı alan); görülen çok ama
  fix'te 0 = kilitlenemiyor. GPS'in NEDEN oturmadığı artık ekrandan okunur.

## [0.9.7] - 2026-08-27

### Fixed

- F-7: yüklü sürüm ekranda görünmüyordu — Home'un altında artık "v0.9.7"
  yazar (GPX'teki `app` özniteliğiyle birlikte sürüm hem ekranda hem
  dosyada).
- F-8: kayıt sürerken Home'daki düğme "BAŞLAT" diyor, yeni kayıt
  başlatılıyormuş gibi algılanıyordu (teknikte açılmıyordu — servis
  korumalı). Düğme kayıt sürerken "KAYDA DÖN" olur, tip seçimi kilitlenir
  ve dokunuş izin/konum diyaloglarına girmeden doğrudan kayda döner.

## [0.9.6] - 2026-08-27

### Fixed

- F-6 (Saha Turu 2): GPS çipi ancak biri istediğinde aramaya başlar; Home'da
  beklerken kimse istemediği için START sonrası 1+ dakikalık kör bekleyiş
  yaşanıyordu (uygulamayı yeniden başlatmak çipi etkilemez). Home açıkken
  GPS artık ön-ısıtılır — kullanıcı tip seçerken kilitlenir — ve START'ın
  üstünde hazırlık satırı görünür: "GPS aranıyor…" → "GPS ± X m" →
  "GPS hazır · ± X m". Ekrandan ayrılınca dinleme bırakılır (pil kuralı);
  kayıt sürerken servis zaten dinler.

### Added

- GPX raporuna `app` özniteliği: dosya hangi sürümle yazıldığını taşır.

## [0.9.5] - 2026-08-25

### Fixed

- F-5 (kort denemesinin ikinci yarısı: güç tasarrufu açıktı): birçok cihaz
  bu modda GPS'i kısar; uygulama bunu görüyor ama söylemiyordu. GPS
  oturmamışken durum satırına "· güç tasarrufu açık" eklenir; boş kaydın
  atılma mesajı "Güç tasarrufu açıktı — GPS'i kısıtlamış olabilir."
  notunu taşır. Mod engellenmez — saha matrisindeki güç-tasarrufu
  senaryosu desteklenmeye devam eder, yalnızca görünür kılınır.

## [0.9.4] - 2026-08-25

### Fixed

- F-4 (saha denemesi: kort içinde 2 dk koşu, hiç nokta kabul edilmedi):
  GPS oturmadan geçen süre ekranda görünmüyordu — kayıt sessizce boş kalıp
  atılıyor, neden anlaşılamıyordu. Kayda nokta girmemişken durum satırı
  artık canlı GPS kalitesini gösterir: "Fix bekleniyor…" ya da
  "GPS ± X m". Boş kaydın atılma mesajı da nedenli: "GPS hiç fix vermedi"
  ↔ "doğruluk hiç 30 m eşiğinin altına inmedi (en iyi ± X m)". Filtre
  eşiği bilinçli olarak değişmedi. Çekirdeğe 2 yeni JVM testi
  (kabul öncesi kalite gözlemi; bilinmeyen doğruluk kaliteye sayılmaz).

## [0.9.3] - 2026-08-25

### Added

- F-3 (saha iş akışı): tur telemetrisi artık GPX'in içinde gider. Dışa
  aktarılan dosyaya `extensions`/`norda:report` bloğu gömülür: uygulama
  özeti (mesafe, aktif süre, ▲▼), pil (başı/sonu) ve filtre sayaçları.
  GPX'i paylaşmak = tur raporunu paylaşmak; elle not gerekmez. Diğer
  araçlar bloğu yok sayar; filtre sayaçları yalnız son kayıt o aktiviteyse
  eklenir (eski ize yanlış sayaç gömülmez). İçe aktarmada blok veri
  sayılmaz — istatistikler noktalardan yeniden hesaplanır. Çekirdekte 3
  yeni JVM testi (gidiş-dönüş, bloksuz dosya, bilinmeyen pil/sayaç).

## [0.9.2] - 2026-08-25

### Fixed

- F-2 (Saha Turu 1 kullanışlılık bulgusu): filtre sayaçları yalnız kayıt
  sürerken okunabiliyordu — sahada bulmak ve not almak zordu. Kayıt bitince
  son kaydın sayaçları kalıcı saklanır ve Tanılama'da "KAYIT FİLTRESİ
  (SON KAYIT)" olarak görünmeye devam eder; tur raporu eve dönünce
  yazılabilir. Boş/atılan kayıtta da saklanır — "kayıt neden boş kaldı"nın
  cevabı çoğu zaman bu sayaçlardadır.

## [0.9.1] - 2026-08-25

### Fixed

- F-1 (Saha Turu 1 analizi): geçmişteki pil oranının paydası aktif kayıt
  süresiydi; GPS duraklatmada da açık kaldığından pil duvar saatiyle akar
  ve oran şişiyordu (turda 4,2 %/sa görünen gerçekte 2,8 %/sa). Payda artık
  kayıt başı→sonu duvar saati. Turun gerçek verisi (%2 / 42:46) çekirdeğe
  regresyon testi olarak girdi.

### Notlar

- Tur 1 GPX'i üretim kod yollarıyla yeniden hesaplandı: mesafe, süre ve
  yükseklik (tam 135,0 m) uygulamayla birebir tuttu; elle duraklatmada ara
  mesafenin sayılmaması (510 m'lik mola yürüyüşü) sahada doğrulandı.

## [0.9.0] - 2026-08-25

### Added

- Faz 9 — Release Candidate açılışı: saha kanıtı + Play hazırlığı.
- "Testler diş geçiriyor mu" turu (MVP 13.1/5) ilk kez koşuldu ve README'ye
  tablo olarak işlendi: 8 kasıtlı hata, 8'i de yakalandı. Turun kendi
  bulgusu da kapatıldı — şema taban listesine sızan DDL'yi parite testi
  göremiyordu; sürüm başına ifade sayısını donduran altın-tablo testi
  eklendi (96 test).
- `docs/PRIVACY.md` (TR+EN): konum cihazda kalır, ağ yalnız harita paketi
  indirir — Play'in konum izni için gizlilik politikası şartına hazır.
- `docs/SAHA.md`: 20 adımlık saha turu protokolü + rapor şablonu; üç temiz
  tur v1.0.0 kapısıdır.
- `check-tag` artık versionCode formülünü de doğrular
  (MAJOR×10000+MINOR×100+PATCH) — 15.1'deki "monotonluğu CI doğrular"
  sözü yerine geldi; iki ret dalı da yerelde kanıtlı.

### Changed

- Pusula ve tanılama ekranları konum sağlayıcısına kapalıyken de kayıt
  olur (0.8.0'daki servis düzeltmesiyle aynı desen): ekran açıkken konum
  açılırsa fix'ler kendiliğinden akmaya başlar.

## [0.8.0] - 2026-08-25

### Added

- Faz 8 — Polish: kalite çıtası her ekranda.
- Pil ölçümü: kayıt başında ve sonunda pil yüzdesi saklanır (şema sürümü 3,
  göçlü); geçmiş satırı tüketimi ve süre yeterliyse %/saat oranını gösterir.
  Ölçüm kirliyse (seviye okunamadı, kayıtta şarj, 5 dk'dan kısa süre) sayı
  uydurulmaz — hiç gösterilmez. Saf `Battery` modülü, 5 JVM testiyle.
- Filtre kalibrasyonu: GPS filtresi artık ret NEDENİ üretir (doğruluk /
  titreme / ışınlama / zaman) ve kayıt bunları sayar; tanılama ekranı kayıt
  sürerken canlı sayaçları gösterir. Saha verisiyle eşik ayarının ham
  verisi bu — 4 yeni JVM testi.
- İzin UX (Home): ret nedeni ekranda kalır; kalıcı ret "Ayarları aç"
  diyaloğuna götürür (tanılama ekranındaki akışla aynı dil).

### Changed

- Konum kapalıyken START, kaydın boş kalacağını baştan söyler: "Konumu aç"
  ya da "Yine de başlat". Servis sağlayıcıya artık kapalıyken de kayıt olur —
  konum sonradan açılırsa fix'ler kendiliğinden akmaya başlar.
- Hiç nokta girmemiş kayıt geçmişe gürültü olarak yazılmaz: bitirmede ve
  kurtarmada silinir, kullanıcıya söylenir.
- Erişilebilirlik: pusula kadranı ve kayıt haritası ekran okuyucuya süs
  olarak işaretlendi (değerler zaten metinlerde); tüm yazı boyutları sp,
  dokunma hedefleri ≥ 48 dp doğrulandı.

## [0.7.1] - 2026-08-25

### Fixed

- 0.7.0 çökmesi: `waypoint` tablosu şemada hiç yaratılmamıştı — DAO'su olan
  ama `CREATE`'i olmayan tablo. Pusula, kayıt, harita ve nokta ekranları
  açılır açılmaz "no such table: waypoint" ile kapanıyordu. DDL ve göç planı
  artık saf `Schema` modülünde (şema sürümü 2): eski kurulum göçle, yeni
  kurulum sıfırdan aynı şemaya varır; 5 yeni JVM testi — göç zinciri ile
  sıfırdan kurulumun parite değişmezi dahil — bunu kalıcı korur.

## [0.7.0] - 2026-08-25

### Added

- Faz 7 — Waypoints + GPX: veri döngüsü kapandı.
- Waypoint'ler: kayıt sırasında "+ Nokta" (son kabul edilen konuma) ya da
  haritaya uzun basarak (ad diyaloğuyla); sınırsız sayıda. Haritada altın
  baklava + ad; pusulada en yakın nokta içi boş baklava, alt satırda en
  yakın iki noktanın kerteriz + mesafesi. Nokta listesi: dokun → yeniden
  adlandır, uzun bas → sil. Varsayılan ad "Nokta N" — boş İLK numara,
  silinen numara yeniden kullanılır.
- GPX dışa aktarma (iz ekranından, SAF): tek dosyada `trk` + tüm `wpt`'ler;
  rakım yalnız geçerliyse yazılır (0.0 nöbetçisi sızmaz).
- GPX içe aktarma (geçmişten): `trkpt`'ler aktivite olarak (mesafe/süre/
  yükseklik saf çekirdekle hesaplanır), `wpt`'ler nokta olarak; bozuk girdi
  satır atlanarak tolere edilir.
- Çekirdeğe `Gpx` (yazıcı + ayrıştırıcı) ve `WaypointNaming`, 9 yeni JVM
  testiyle (gidiş-dönüş, XML kaçışı, bozuk girdi, boş numara seçimi).

## [0.6.0] - 2026-08-25

### Added

- Faz 6 — Navigation: Pusula ekranı ve Return to Start. Elle çizilen kadran
  (gerçek kuzeye göre döner, üst gösterge sabit), başlangıç yönünde altın
  baklava; alt satırda kerteriz + mesafe + tempo biliniyorsa tahmini süre
  ve yönlendirme ("12° sağa" / "yön tutuyor", ±5°).
- Gerçek kuzey: sapma `GeomagneticField`'den, 1 km yol alınmadan yeniden
  hesaplanmaz ve kalıcı saklanır — sonraki açılışta anında hazır. Sapma
  bilinmiyorsa etiket "Manyetik" der.
- Manyetik bozulma uyarısı: ölçülen alan şiddeti beklenenle karşılaştırılır
  (%25 giriş / kesintisiz 2,5 sn / %15 çıkış histerezisi). Durum önceliği:
  bozulma > kalibrasyon.
- Çekirdeğe 3 saf modül, 11 yeni JVM testiyle: `Smoothing` (zaman sabitli,
  sin/cos üzerinden — 50 Hz ↔ 16 Hz aynı his), `DisturbanceDetector`,
  `ReturnToStart` (kerteriz + mesafe + ETA + işaretli dönüş açısı).
- Pusula, Home'dan ve kayıt ekranından tek dokunuş uzaklıkta. Başlangıç
  noktası: aktif kaydın ilk noktası; kayıt yoksa son aktivitenin başlangıcı.

## [0.5.0] - 2026-08-25

### Added

- Faz 5 — Offline Maps: kendi karo paketleme hattı devrede. `map-pack.yml`
  elle tetiklenir (id + ad + bbox + zoom), paketi üretir, `maps/<id>-vN`
  etiketiyle Release'e asar ve `docs/maps/index.json`'u günceller.
- Haritalar ekranı artık `index.json`'daki paketleri listeler ve indirir:
  ilerleme yüzdesi, SHA-256 doğrulaması (tutmazsa dosya silinir), yarım
  indirme asla geçerli paket gibi görünmez (`.part` + adlandırma).
- `TileDownloader`: ağa dokunan TEK sınıf; `INTERNET` izni yalnız bunun için
  eklendi. Elle içe aktarma çevrimdışı yedek yol olarak duruyor.
- `core/io/Digests`: SHA-256, NIST test vektörleriyle doğrulanmış.
- `tools/make-map-pack.py` (bölge parametreli üretici, eski test üretecinin
  yerine) + `tools/update-map-index.py`.

### Notlar

- Üretim adımı şimdilik prosedürel kartografya kullanır; gerçek OSM
  kartografyası geldiğinde yalnız map-pack'in "Paketi üret" adımı değişir —
  paket biçimi, yayın ve indirme zinciri aynı kalır.

## [0.4.0] - 2026-08-25

### Added

- Faz 4 — Custom MapView: elle çizilen offline raster harita. Yalnız
  viewport'u kesen karolar çizilir; MBTiles okuma (TMS çevirmesiyle), LRU
  bitmap önbelleği, arka planda karo çözme, pan/çift dokunuş/iki parmakla
  zoom. Paket yokken prosedürel ızgara — iz ve imleç paketsiz de görünür.
- `core/map/WebMercator`: projeksiyon + karo matematiği, bilinen sabit
  noktalarla 8 JVM testi (İstanbul z10 karosu, TMS çevirmesi, sınır
  enleminde kayan nokta sabitlemesi).
- Kayıt ekranında canlı harita: iz ve konum imleci haritada, otomatik takip.
- Geçmişte satıra dokunmak izi haritada açar (ekrana sığdırılmış).
- Haritalar ekranı: `.mbtiles` içe aktarma (SAF), paket listesi, silme,
  OSM atfı. İndirme + `index.json` Faz 5'te.
- `tools/make-test-map.py`: yalnız stdlib ile prosedürel test paketi üreteci
  (İstanbul z8–13, ~324 KB) — hizalama ve TMS hataları ızgarada anında belli
  olur.

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
