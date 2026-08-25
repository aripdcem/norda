# Norda — Walk. Run. Explore.

**Minimalist Outdoor Navigation — MVP ve Teknik Tasarım**

rev 2 · 25 Ağustos 2026

Bu belge, yürüyüş ve koşu takibini pusula, offline harita, waypoint'ler ve
"Return to Start" navigasyonu ile birleştiren minimalist bir Android uygulaması
için ürün kapsamını, ekranları, teknik mimariyi, veri modelini, GPS/sensör
işleme yaklaşımını, süreç kurallarını ve geliştirme yol haritasını tanımlar.

İlk taslak (Drive, 25 Ağustos 2026) verilen kararlarla yeniden yazılmıştır;
**projenin tek doğru kaynağı bu dosyadır.** Norda, iki mevcut uygulamanın
birleşiminden doğar: [WalkRun](https://github.com/aripdcem/WalkRun) (kayıt
motoru) ve [Compass](https://github.com/aripdcem/compass) (yön bulma). Her iki
depo da test edilmiş modül kaynağı olarak kullanılır — Norda bir yeniden yazma
değil, bir taşıma projesidir.

| Alan | Karar |
|---|---|
| Platform | Android; iOS ilk aşamada yok (çekirdek saf tutulur, kapı açık kalır) |
| Dil | Kotlin |
| Paket adı | `com.aripd.norda` |
| UI | Android Views + XML + Canvas (AppCompat/Material/Compose yok) |
| Harici bağımlılık | Çalışma zamanında **0** — yalnız Android SDK + Kotlin stdlib |
| Location | `android.location.LocationManager` / GNSS (Play Services yok) |
| Compass | `SensorManager` (rotation vector; yedek: ivmeölçer + manyetometre) |
| Storage | SQLite (ince katman kuralıyla, bkz. 8. bölüm) |
| Tracking | Foreground service (`location` türü) |
| Harita | Custom offline raster-tile renderer + **kendi karo paketleme hattı** (GitHub Actions) |
| Routing | MVP'de yok |
| Cloud / Hesap | Yok |
| SDK | `minSdk 26` (Android 8.0) · `targetSdk 35` (Android 15, kenardan kenara) |
| Sürümleme | SemVer; her değişikliğe etiket + release (15. bölüm) |
| Geliştirme | TDD — kırmızı → yeşil → refactor (13. bölüm) |

## 1. Ürün tanımı ve konumlandırma

Norda bir "koşu uygulaması + pusula" değil, **minimalist, offline-first bir
outdoor navigasyon ürünüdür**. Temel vaat: kullanıcı internetsiz bir ortamda
yürür/koşar, konumunu ve yönünü görür, aktivitesini kaydeder ve gerektiğinde
başlangıç noktasına geri döner.

> **Yürü. Koş. Keşfet. Yolunu kaybetme.**

Klasik koşu uygulamalarında aktivite metriği ana üründür; Norda'da **yön bulma
birinci sınıf özelliktir**. Trekking, hiking, trail running ve doğa
yürüyüşlerinde "başladığım yere nasıl dönerim?" sorusuna internet gerektirmeden
cevap vermek ürünün kimliğidir.

MVP'nin odak noktası:

- Çok hızlı aktivite başlatma: uygulamayı aç → START.
- GPS tabanlı yürüyüş/koşu kaydı; canlı rota, mesafe, süre, tempo, yükseklik.
- Gerçek zamanlı pusula — gerçek kuzey ve manyetik bozulma uyarısıyla.
- Offline harita: kendi hattımızda üretilen bölge paketleri.
- Waypoint'ler ve GPX alışverişi: veri kullanıcınındır, taşınabilir.
- Return to Start: başlangıca kuş uçuşu yön + mesafe + tahmini süre.
- Hesap, sosyal ağ, bulut, routing, kalori, Strava **yok**.

## 2. MVP kapsamı

| Özellik | MVP | Not |
|---|---|---|
| Walking | Evet | Aktivite tipi |
| Running | Evet | Aktivite tipi |
| GPS tracking | Evet | GNSS tabanlı, filtrelenmiş |
| Distance | Evet | Kabul edilmiş noktalar arası jeodezik mesafe |
| Duration | Evet | Duraklatmalar hariç aktif süre |
| Speed / Pace | Evet | Konuma dayalı; hareket filtresiyle |
| Elevation gain/loss | Evet | GNSS altitude + histerezisli birikim (5.4); barometrik iyileştirme sonra |
| Auto-pause | Evet | 20 sn hareketsizlik → duraklat; çapadan 8 m → devam (5.5) |
| Live route | Evet | Harita üzerinde Canvas katmanı |
| Compass | Evet | Rotation vector; zaman sabitli yumuşatma (6. bölüm) |
| Gerçek kuzey | Evet | `GeomagneticField` sapması; harita ile aynı çerçeve |
| Manyetik bozulma uyarısı | Evet | Alan şiddeti karşılaştırması, histerezisli (6.3) |
| Offline map | Evet | Raster karo; MBTiles paketleri (7. bölüm) |
| Map paket indirme | Evet | Kendi hatta üretilen bölge paketleri (7.2) |
| Return to Start | Evet | Kuş uçuşu kerteriz + mesafe + ETA (10. bölüm) |
| Waypoints | Evet | Adlandırılmış noktalar; haritada ve pusulada (2.1) |
| GPX import/export | Evet | `<trk>` + `<wpt>` birlikte; SAF ile dosya alışverişi |
| Activity history | Evet | Yerel SQLite |
| Serbest alan seçerek indirme | Sonraki sürüm | MVP'de hazır bölge paketleri (7.2) |
| Breadcrumb navigation | Sonraki sürüm | Kaydedilen izi ters yönde takip |
| Gün ışığı bütçesi | Sonraki sürüm | Batış saati × dönüş temposu uyarısı (Compass `Sun.kt`) |
| Gece modu | Sonraki sürüm | Kırmızı palet, alacakaranlıkta otomatik (Compass) |
| Sesli anonslar / rekorlar / haftalık özet | Sonraki sürüm | WalkRun mirası |
| Turn-by-turn routing | Hayır | Kapsam dışı |
| Account / Cloud / Social | Hayır | Kapsam dışı |
| Kalori | Hayır | Güvenilirliği düşük metrik ürünün merkezine alınmaz |
| Strava | Hayır | Ağ kuralıyla çelişir (11. bölüm); WalkRun'daki kod istenirse sonra |
| AI / öneriler / hava durumu / saat–nabız | Hayır | Kapsam dışı |

### 2.1 Waypoint davranışı

- Waypoint her zaman eklenebilir: kayıt sırasında tek dokunuşla (araç, kamp,
  su kaynağı, yol ayrımı) ya da haritada uzun basarak.
- Yeni nokta adı "Nokta N" — N, listedeki boş ilk numara (Compass kuralı);
  yeniden adlandırılabilir, silinebilir.
- Sayı sınırı yok (Compass'taki 8 sınırı ekran yerleşiminden geliyordu ve
  taşınmıyor); liste ekranında mesafeye göre sıralama.
- Haritada işaret, pusulada kerteriz ve mesafe olarak görünür.
- GPX `<wpt>` olarak dışa/içe aktarılır.

## 3. Ekranlar ve kullanıcı akışı

Alt gezinme: **Home · Compass · Activities · Maps** (+ Settings dişlisi).

### 3.1 Home

```
  HOME
  ────────────────────────
  247° SW          0.0 km

        [ START ]

     Walk        Run
  ────────────────────────
```

Mümkün olduğunca boş. Küçük bir yön göstergesi + tek dokunuşla START.

### 3.2 Activity (kayıt ekranı)

```
  ┌──────────────────────┐
  │                      │
  │     OFFLINE MAP      │
  │        ───●          │
  │       /              │
  │      /       ◆ Kamp  │
  ├──────────────────────┤
  │ 5.82 km              │
  │ 6:14 /km       38:21 │
  │ ▲ 124 m   ▼ 96 m     │
  │                      │
  │ [+WPT] [PAUSE] [STOP]│
  └──────────────────────┘
```

Harita ekranın ana görselidir; iz ve waypoint'ler üzerinde çizilir. Metrikler
sınırlı sayıdadır: mesafe, tempo, süre, yükseklik kazanımı/kaybı. `+WPT`
bulunduğun yeri waypoint yapar. Otomatik duraklatma durumu açıkça gösterilir
("otomatik duraklatıldı").

### 3.3 Compass

```
        N
      ╱ ▲ ╲
     │ 247° │
  W ◀│  SW  │▶ E
     │      │
      ╲    ╱
        S
  ── Start 312° · 3.2 km
  ── Kamp   18° · 640 m
```

Cihaz yönü (device heading) ile hedef kerterizi (target bearing) ayrı
kavramlardır ve ekranda karışmaz. Alt satırlar: başlangıç noktası ve en yakın
waypoint'lerin kerteriz + mesafesi. Gerçek kuzey etikette belirtilir; sapma
bilinmiyorsa "manyetik" yazar.

### 3.4 Activities

```
  ACTIVITIES
  Bugün ────────────────
  Running   5.82 km  38:21  ▲124 m
  Dün ──────────────────
  Walking   3.41 km  52:18  ▲83 m
```

Aya göre gruplu liste → detay ekranı (iz haritası, metrikler, GPX dışa aktar,
sil).

### 3.5 Maps

```
  OFFLINE MAPS
  İndirilenler ─────────
  İstanbul   482 MB  ✓
  Uludağ     126 MB  ↓ %40
  Kullanılabilir ────────
  Kaçkar     210 MB  [İNDİR]
  ──────────────────────
  © OpenStreetMap contributors
```

Paket listesi depodaki `index.json`'dan gelir (7.2). İndirme SHA-256 ile
doğrulanır; paketler tek tek silinebilir. Atıf bu ekranda ve Hakkında'da
görünür. Serbest alan seçimi MVP sonrasıdır.

### 3.6 Return to Start

```
  RETURN TO START
       ↖
  312° NW · 3.21 km · ~38 dk
```

Kayıt sırasında her ekrandan bir dokunuş uzaklıkta. Kuş uçuşu kerteriz +
mesafe + son tempo penceresinden tahmini süre. Bu bir yol ağı rotası değildir;
bilinçli tasarım kararıdır (routing engine yok, offline çalışır, harita paketi
olmasa bile çalışır).

## 4. Teknik mimari

```
com.aripd.norda
│
├── core/                    saf Kotlin — Android import'u YASAK, tamamı JVM'de test edilir
│   ├── geo/                 mesafe, kerteriz, açı aritmetiği        ← WalkRun + Compass Geo birleşimi
│   ├── track/               TrackPoint, GpsFilter, AutoPauseDetector,
│   │                        PauseAwareStopwatch, Stats, Elevation   ← WalkRun (+Elevation yeni)
│   ├── nav/                 ReturnToStart, WaypointLogic            ← Compass Geo + Waypoints mantığı
│   ├── heading/             Smoothing, Declination modeli           ← Compass
│   ├── map/                 MapProjection (WebMercator), TileMath   ← yeni (saf matematik)
│   └── io/                  Gpx (trk+wpt), satır↔model mapper'lar   ← WalkRun Gpx/GpxImport
│
├── location/                GpsLocationSource (LocationManager sarmalı)
├── compasshw/               sensör kaydı, rotation vector, kalibrasyon/bozulma uyarıları
├── tracking/                TrackingService (foreground), bildirim
├── map/                     CustomMapView, TileStore, TileCache, TileDownloader
├── storage/                 AppDatabase, ActivityDao, WaypointDao, MapDao (ince katman)
└── ui/                      HomeView, ActivityView, CompassView, ActivitiesView,
                             MapsView, SettingsActivity, Palette
```

### 4.1 Hazır modül eşlemesi

| Norda modülü | Kaynak | Durum |
|---|---|---|
| `GpsFilter` | WalkRun | Testli; eşikler başlangıç değeri (5.2) |
| `Geo` (mesafe) | WalkRun | Testli |
| `Geo` (kerteriz, açı, birleştirme) | Compass | Testli (fiziksel sabitlerle) |
| `PauseAwareStopwatch` | WalkRun | Testli |
| `AutoPauseDetector` | WalkRun | Testli |
| `Stats` (mesafe/tempo) | WalkRun | Testli; yükseklik kazanımı **yeni** |
| `Smoothing` (zaman sabitli) | Compass | Testli (50 Hz ↔ 16 Hz aynı his) |
| Bozulma algılama (`Disturbance`) | Compass | Testli, sahada doğrulanmış |
| `ReturnToStart` kerterizi | Compass `Geo` | Testli; 10. bölümdeki düzeltilmiş formül |
| ETA | WalkRun `currentPace` penceresi | Testli |
| Çökme kurtarma deseni | WalkRun | SQLite + WAL'a uyarlanır (8.3) |
| İz çizimi | WalkRun `TrackView` | Harita katmanına uyarlanır |
| GPX | WalkRun `Gpx` + `GpxImport` | Testli; `<wpt>` desteği eklenir |
| Waypoint adlandırma/temizlik | Compass `Waypoints` | Testli; depo SQLite'a taşınır |
| MapProjection · TileStore · TileCache · TileDownloader · CustomMapView | — | **Yeni: MVP'nin tek büyük yatırımı** |

Kural: **taşınan her modül testleriyle birlikte gelir; testsiz port birleşmez.**

## 5. GPS / Location Engine

```
GNSS / LocationManager
        │
        ▼
  Accuracy filtresi ──► Hareket/mesafe filtresi ──► Hız tavanı
        │
        ▼
    TrackPoint ──► SQLite (WAL) ──► Statistics ──► UI / harita katmanı
```

### 5.1 TrackPoint

`timestamp, latitude, longitude, altitude, accuracy, speed, bearing` —
saf model, Android bağımlılığı yok.

### 5.2 Filtre başlangıç değerleri (WalkRun'dan, testli)

| Filtre | Değer | Gerekçe |
|---|---|---|
| Doğruluk tavanı | 30 m | Daha kötü fix kaydedilmez; `accuracy ≤ 0` (bildirmeyen cihaz) kabul edilir |
| Hız tavanı | 15 m/s | Fiziksel olarak anlamsız sıçrama ("ışınlanma") atılır |
| Kıpırdama eşiği | 2 m | Durağan GPS titremesi mesafe saymaz |

Bunlar sabit kural değil **başlangıç değerleridir**; 13. bölümdeki saha
matrisi (orman, dar sokak, sinyal kaybı, yavaş dik tırmanış) ile kalibre
edilir. Duraklatmadayken (elle ya da otomatik) nokta üretimi ve mesafe birikimi
durur. İstatistikler her zaman filtrelenmiş veriyle hesaplanır.

### 5.3 Örnekleme

Başlangıç: ~1 sn aralık. "Her saniye kaydet" sabit kuralı yerine adaptif
yaklaşım hedeflenir; gerçek değerler cihaz ve pil testleriyle belirlenir.
Mesafe süzgeci `LocationManager`'a **verilmez** — Compass'ta ölçülen tuzak:
mesafe süzgeci konunca sabit duran telefona GPS hiç fix göndermiyor.

### 5.4 Yükseklik kazanımı/kaybı (yeni modül)

GNSS dikey hatası yatayın 2–3 katıdır; ham farkları toplamak düz yolda bile
yüzlerce metre hayalet tırmanış üretir. **Histerezisli birikim**: yükseklik,
son demirlenen değerden eşik kadar (başlangıç: 4 m; saha ile 3–5 m aralığında
kalibre) net ayrışmadıkça kazanım/kayıp yazılmaz; ayrışınca fark tek hamlede
işlenir ve demir güncellenir. Test: sabit yükseklikte gürültülü seri → kazanım
0; bilinen merdiven profili → beklenen toplam. Barometrik iyileştirme MVP
sonrası.

### 5.5 Otomatik duraklatma (WalkRun `AutoPauseDetector`, testli)

- Kabul edilmiş fix gelmeden **20 sn** geçerse (fix'ler gelmeye devam ederken)
  → otomatik duraklat; süre saymaz.
- Duraklatmadayken çapa noktasından **8 m** uzaklaşan, doğruluk şartını
  (≤ 30 m) geçen fix → otomatik devam.
- Elle duraklat/devam dedektörü sıfırlar. Durum ekranda açıkça görünür.

### 5.6 Foreground service

START ile `TrackingService` (tür: `location`) başlar; kalıcı bildirim gösterir.
Ekran kapalıyken ve uygulama arkadayken kayıt sürer. Süreç ölümünde kayıt
kaybolmaz (8.3). Android 10+ service type, Android 13+ bildirim izni ve güncel
Play politikaları hedef SDK ile birlikte geliştirme sırasında yeniden doğrulanır.

## 6. Compass / Heading Engine

### 6.1 Yön okuma

Öncelik `TYPE_ROTATION_VECTOR`; cihazda yoksa ivmeölçer + manyetometre
(`getRotationMatrix`). `remapCoordinateSystem` ile eksenler ekran yönüne
eşlenir — dikey ve yatay yerleşimde aynı kod çalışır.

### 6.2 Yumuşatma (Compass `Smoothing`, testli)

Açı doğrudan değil `sin`/`cos` bileşenleri üzerinden süzülür (359°→0°
geçişinde ibre tam tur atmasın). Saklanan şey katsayı değil **zaman
sabitidir** (0.35 / 0.17 / 0.08 sn); katsayı her örnekte gerçek aralıktan
hesaplanır, örnekleme hızı değişse de his sabit kalır.

### 6.3 Gerçek kuzey ve manyetik bozulma

- Sapma `GeomagneticField(lat, lon, alt, time).declination` ile; `gerçek =
  manyetik + sapma`. Sapma önbelleğe alınır (yüzlerce km'de bir derece oynar;
  1 km yol alınmadan yeniden hesaplanmaz), sonraki açılışta anında hazır.
- Harita, kerteriz ve pusula **aynı kuzey çerçevesini** kullanır; sapma
  bilinmiyorsa etiket "manyetik" der, hedefler manyetik çerçevede saklanır
  (izin sonradan verilirse kilitlenen fiziksel yön kaymasın — Compass dersi).
- Bozulma uyarısı: ölçülen toplam alan şiddeti beklenenle
  (`getFieldStrength()`) karşılaştırılır; %25'i aşan sapma kesintisiz 2.5 sn
  sürerse uyarı, %15'in altında kalkar. Uyarı önceliği: bozulma > kalibrasyon >
  eğim (bozulma hiçbir görsel ipucu vermez, en tehlikelisidir).

### 6.4 Üç açı, üç kavram

| Değer | Anlam |
|---|---|
| Device heading | Cihazın baktığı yön |
| Target bearing | Mevcut konumdan hedefe (start, waypoint) yön |
| Relative angle | İkisinin farkı — "12° sağa" |

## 7. Offline Harita Motoru

Zero-dependency hedefinin en zor bölümü. MVP çözümü: vector map engine yazmak
yerine **raster XYZ karoları** kullanan küçük bir custom renderer + karoları
üreten **kendi paketleme hattımız**.

### 7.1 Renderer

- Web Mercator projeksiyonu, XYZ karo matematiği (`core/map`te saf Kotlin,
  JVM testli: lat/lon ↔ tile/piksel dönüşümleri bilinen sabit noktalarla).
- Yalnızca viewport'u kesen karolar yüklenir ve çizilir; bitmap önbelleği
  (LRU); pan/zoom; kare başına tahsis yok (Compass `onDraw` disiplini).
- İz, waypoint ve konum imleci karoların üstünde ayrı katman.
- MapLibre MVP'de **kullanılmaz**. Gerekçe: MapLibre bir istemci render
  kütüphanesidir (vector tile çizer) ve tek başına paketleme hattı değildir;
  custom raster renderer sıfır bağımlılık kimliğini korur ve belgedeki ölçüm
  ("harita motoru ürünü yapmıyoruz, outdoor tracker'ı doğruluyoruz") için
  yeterlidir. Faz 4–5 ölçümünden sonra vector tile + MapLibre'ye geçiş, "tek
  kontrollü bağımlılık" olarak yeniden değerlendirilebilir (kazanımlar: ~10×
  küçük paketler, keskin yazı, stil/gece haritası; bedel: bağımlılık + entegrasyon).

### 7.2 Kendi karo paketleme hattı (GitHub Actions)

Public OSM karo sunucuları toplu indirmeyi kullanım politikasıyla yasaklar;
Norda hiçbir canlı karo sunucusuna bağımlı olmaz. Karolar bizim hattımızda
üretilir:

```
map-pack.yml  (workflow_dispatch: bölge adı + bbox + zoom aralığı)
  1. OSM bölge özeti indirilir (Geofabrik .pbf — ODbL)
  2. Vector tile üretimi (tilemaker / Planetiler)
  3. CI'da rasterize (headless MapLibre render aracı / tileserver-gl konteyneri)
  4. Raster .mbtiles paketi + SHA-256
  5. GitHub Release'e asset olarak yayın (etiket: maps/<bölge>-vN)
  6. docs/maps/index.json güncellenir (commit)
```

- 2–3. adımdaki araçlar **derleme zamanı** araçlarıdır; uygulamaya hiçbir
  bağımlılık girmez (sıfır bağımlılık ilkesi çalışma zamanı içindir).
- Yeni bölge istemek = Actions'ta `map-pack.yml`'i elle tetiklemek; paket
  Release'e düşer, telefonda listede belirir. Harita hattı için secret
  gerekmez (`GITHUB_TOKEN` yeter); imzalama secret'ları 15. bölümde.
- `index.json` şeması: `[{id, name, bbox, minZoom, maxZoom, sizeBytes,
  sha256, url, version}]` — uygulama bunu okur, paketi indirir, doğrular.
- Zoom aralığı başlangıcı: z8–z15 (şehir ölçeği ~yüzlerce MB, dağ bölgesi
  ~onlarca MB); boyutlar ilk paketlerle ölçülüp ayarlanır.
- **Atıf zorunlu**: "© OpenStreetMap contributors" Maps ekranında ve
  Hakkında'da; ODbL lisans notu README'de.

### 7.3 Paket saklama

Her paket ayrı bir `.mbtiles` dosyasıdır (standart MBTiles şeması: `metadata` +
`tiles(zoom_level, tile_column, tile_row, tile_data)`). Dikkat: MBTiles satırı
**TMS** düzenindedir — `tile_row = 2^z − 1 − y`; renderer bu çevirmeyi yapar
ve testi vardır. Standart şema sayesinde paketler masaüstü araçlarla da
açılıp doğrulanabilir. Paketler uygulama veritabanından ayrıdır: silinip
yeniden indirilebilir, aktivite verisi etkilenmez.

## 8. Veri modeli

### 8.1 app.db (SQLite, WAL)

```
activity                      track_point                   waypoint
─────────────────────         ─────────────────────         ─────────────────────
id INTEGER PK                 id INTEGER PK                 id INTEGER PK
type TEXT (WALK|RUN)          activity_id INTEGER FK        name TEXT
start_time INTEGER            timestamp INTEGER             latitude REAL
end_time INTEGER NULL         latitude REAL                 longitude REAL
distance_m REAL               longitude REAL                altitude REAL NULL
duration_ms INTEGER           altitude REAL                 created_at INTEGER
elevation_gain_m REAL         accuracy REAL                 activity_id INTEGER NULL
elevation_loss_m REAL         speed REAL
                              bearing REAL

schema_version (PRAGMA user_version) — göçler sürüm numarasıyla
```

Harita paketleri app.db'de değil, paket başına `.mbtiles` dosyasında (7.3);
app.db yalnız indirilen paketlerin metadata'sını tutar.

### 8.2 İnce katman kuralı

`android.database` JVM birim testlerinde yoktur (Compass'ın `org.json`'u tam
bu yüzden dışladığı gibi). Kural: **SQL'e dokunan katman aptal kalır** — DAO
yalnız okur/yazar; filtre, istatistik, yükseklik, kerteriz, karo matematiği,
GPX üretimi/ayrıştırması saf çekirdekte JVM testlidir. Satır↔model
dönüştürücüler saf fonksiyondur ve testlidir; DAO'lar için küçük bir
instrumented duman testi seti sonradan eklenir.

### 8.3 Çökmeye dayanıklılık (WalkRun standardı)

Fix başına tek `INSERT`, WAL modu. `end_time NULL` aktivite = yarım kalmış
kayıt; açılışta bulunur ve kayda devam edilir ya da geçmişe kurtarılır.
13. bölümdeki recovery senaryoları (process kill, reboot) bunu doğrular.

## 9. Return to Start matematiği

Başlangıç `S(φs, λs)`, mevcut konum `C(φc, λc)`. **Mevcut konumdan başlangıca**
kerteriz:

```
Δλ      = λs − λc
y       = sin(Δλ) · cos(φs)
x       = cos(φc) · sin(φs) − sin(φc) · cos(φs) · cos(Δλ)
bearing = atan2(y, x)          → 0–360°'ye normalize edilir
distance = jeodezik mesafe (WGS84)
eta      = distance × son tempo penceresi (currentPace)
```

Akıl sağlaması: ekvatorda başlangıç tam doğudaysa (`λs > λc`) → `Δλ > 0`,
`y > 0`, `x = 0` → kerteriz 90° (doğu). ✓

> Not: İlk taslakta `Δλ = λ2 − λ1` (ters işaret) yazıyordu; bu, doğu–batı
> aynalanmış kerteriz verir (aynı örnekte 270°). Bu belge düzeltilmiş formülü
> içerir. Uygulamada bu satır elle yazılmaz: Compass `Geo.kt`'nin test edilmiş
> büyük daire kerterizi port edilir — testleri "İstanbul'dan Kâbe 152.0°" gibi
> fiziksel sabitlere bağlıdır ve bu sınıf hatayı anında yakalar.

Sınırlama bilinçlidir: bu bir yol ağı rotası değildir. Routing engine
gerektirmez, offline çalışır, harita paketi olmasa bile çalışır, teknik riski
düşüktür, outdoor kullanıcıya hemen değer verir.

## 10. GPX alışverişi

- **Dışa**: aktivite → `<trk>/<trkseg>/<trkpt>` (`ele`, `time` ile);
  waypoint'ler → `<wpt name=...>`. Tek dosyada iz + noktalar. SAF
  (`ACTION_CREATE_DOCUMENT`) ile kaydedilir — WalkRun deseni.
- **İçe**: `<trkpt>`'ler aktivite olarak (`lat/lon/ele/time`), `<wpt>`'ler
  waypoint olarak. Bozuk girdi satır atlanarak tolere edilir, testli.
- "Route sharing" MVP'de ayrı özellik değildir: GPX dosyası paylaşmak zaten
  route sharing'dir.

## 11. İzinler ve ağ kuralı

| İzin | Amaç |
|---|---|
| `ACCESS_FINE_LOCATION` | GPS/GNSS kaydı, koordinatlar |
| `ACCESS_COARSE_LOCATION` | Platform gerekliliği (fine ile birlikte); sapma için yeterli |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION` | Ekran kapalıyken kayıt |
| `POST_NOTIFICATIONS` | Servis bildirimi (Android 13+) |
| `INTERNET` | **Yalnız harita paketi indirme** |

**Ağ kuralı:** ağa dokunan tek sınıf `TileDownloader`'dır; tracking, pusula,
navigasyon ve GPX internetsiz çalışır. Bu cümle README'de yazar — Compass'ın
"izin yok = doğrulanabilir gizlilik" duruşunun Norda karşılığıdır. Konum verisi
varsayılan olarak cihazda kalır; hiçbir telemetri yoktur.

## 12. Geliştirme yol haritası

Her faz tamamlandığında minor sürüm etiketi atılır; faz içindeki her
uygulama-etkileyen `main` birleşimi kendi sürümünü alır (15. bölüm).

| Faz | Sürüm | İçerik | Çıktı |
|---|---|---|---|
| 1 · Sensor Core | v0.1.0 | Proje iskeleti, CI + release hattı (`check-tag`), izin akışı, GPS + heading debug ekranı, cihaz doğrulaması | İlk etiketten imzalı APK; sensörler sahada doğrulanmış |
| 2 · Activity Engine | v0.2.0 | Start/Pause/Resume/Stop, TrackPoint + filtreler, auto-pause, mesafe/süre/tempo/yükseklik, SQLite, geçmiş | Walk/Run kaydeden uygulama |
| 3 · Foreground Tracking | v0.3.0 | TrackingService, bildirim, ekran kapalı kayıt, kurtarma, pil/battery-saver testleri | Cebe koy, yürü, güven |
| 4 · Custom MapView | v0.4.0 | Web Mercator, XYZ matematiği, pan/zoom, karo önbelleği, iz katmanı — CI'dan üretilmiş küçük test paketiyle | Haritada canlı rota |
| 5 · Offline Maps | v0.5.0 | `map-pack.yml` hattı, Maps ekranı (index.json, indirme, SHA-256, silme), atıf | Uçak modunda harita |
| 6 · Navigation | v0.6.0 | Return to Start (kerteriz + mesafe + ETA), göreli ok, gerçek kuzey + bozulma uyarısı | "Yolunu kaybetme" sözü tutuluyor |
| 7 · Waypoints + GPX | v0.7.0 | Waypoint ekleme/liste/haritada-pusulada gösterim, GPX içe/dışa (`trk`+`wpt`) | Veri döngüsü kapanır |
| 8 · Polish | v0.8.0 | Pil ölçümü, filtre kalibrasyonu, izin UX, hata yönetimi, erişilebilirlik | Kalite çıtası her ekranda |
| 9 · Release Candidate | v0.9.x | 13. bölüm saha matrisi tur tur; düzeltmeler z-sürümleri | Saha kanıtı + Play hazırlığı |
| MVP | **v1.0.0** | Bu belgedeki kapsam sahada doğrulanmış | Norda ayakta |

### Sprint 1 somut görevleri (Faz 1)

- `com.aripd.norda` Kotlin projesi; `minSdk 26` / `targetSdk 35`; kenardan
  kenara çizim (Compass çözümü).
- CI: her itişte test + lint + debug APK (`ci.yml`); etikette imzalı release
  (`release.yml` + `check-tag.sh`) — Compass'tan uyarlanır.
- `ACCESS_FINE_LOCATION` izin akışı; ret/kalıcı ret durumları.
- `LocationManager` GPS güncellemeleri; debug ekranında lat/lon/accuracy/speed.
- Rotation vector heading; debug ekranında açı + doğruluk bayrağı.
- Farklı Android cihazlarda doğrulama; `v0.1.0` etiketi.

Sprint sonu hedefi: *telefonu al → uygulamayı aç → konum ve yön ekranda,
CI yeşil, ilk imzalı APK Releases'ta.*

## 13. Test stratejisi

### 13.1 TDD çalışma kuralları

1. Davranış önce **kırmızı testle** gelir: kırmızı → yeşil → refactor.
2. Çekirdek saftır; testler cihazsız, JVM'de, saniyeler içinde koşar. SQL'e ve
   Android'e dokunan katmanlar incedir (8.2).
3. Mümkün olan her yerde **fiziksel sabitlere karşı test**: kendi çıktını
   değil, jeodeziyi/dünyayı doğrula (ekvator kerterizi 90°, İstanbul→Kâbe
   152.0°, bilinen karo koordinatları, düz seride kazanım 0). 9. bölümdeki
   formül hatası bu kuralın gerekçesidir.
4. Taşınan her modül testleriyle gelir; testsiz port birleşmez.
5. **"Testler diş geçiriyor mu"** geleneği (Compass): kasıtlı hata sokulup
   hangi testlerin düştüğü README'de tablo olarak tutulur.
6. Sensör, titreşim ve pil konularında cihazda elle doğrulama, birim testin
   yerine değil **yanına** konur; sürüm notunda işaretlenir (Compass dersi:
   `dumpsys` de API dönüş değerleri de yalan söyleyebilir).

### 13.2 Birim test alanları

Filtreler, istatistikler, yükseklik histerezisi, auto-pause kararları,
stopwatch, yumuşatma, bozulma histerezisi, kerteriz/mesafe/ETA, Web Mercator
ve karo matematiği (TMS çevirme dahil), GPX üretim/ayrıştırma, satır↔model
mapper'lar, waypoint adlandırma.

### 13.3 Saha test matrisi

| Alan | Test |
|---|---|
| Location | Açık alan, şehir, dar sokak, orman |
| Accuracy | Farklı cihazlarda GPS accuracy dağılımı |
| Distance | Bilinen mesafelerle karşılaştırma |
| Speed | Yürüme / koşma / durma geçişleri |
| Auto-pause | 5–10 dk duraklamada distance/pace davranışı |
| Elevation | Bilinen tırmanış profiliyle karşılaştırma; düz yolda ~0 |
| Compass | Manyetik parazitli ve temiz ortamlar; cepte/elde |
| Background | Screen off / başka uygulama / kilit ekranı |
| Battery | 30 dk / 1 saat / 2 saat tracking |
| Offline | Uçak modu + indirilen paket; paket yokken Return to Start |
| Map | Pan / zoom / önbellek / büyük paket |
| Recovery | Process kill, reboot, servis kesintisi |
| Permissions | Reddet / geri al / ayarlardan değiştir |

## 14. Non-functional gereksinimler

| Kriter | Hedef |
|---|---|
| Startup | Home mümkün olduğunca hızlı açılır |
| Tracking | Uzun aktivitede veri kaybı yok |
| Battery | GPS/sensör örneklemesi gereksiz yere maksimumda tutulmaz; sensörler yalnız gerektiğinde kayıtlı |
| Offline | Tracking, pusula, navigasyon ve GPX internetsiz çalışır |
| Storage | Harita paketleri ayrı yönetilir; aktivite verisi bağımsız |
| Privacy | Konum verisi cihazda kalır; telemetri yok; ağ kuralı (11. bölüm) |
| Dependency | Çalışma zamanında harici bağımlılık yok |
| Reliability | Süreç kesintisi sonrası veri bütünlüğü korunur |
| Release | Yayımlanan sürüm değişmez; kırmızı test = yayın yok |

## 15. Süreç: sürümleme ve yayın

### 15.1 SemVer haritası

- 1.0.0 öncesi `0.y.z`: **y** = yeni özellik (tipik olarak faz tamamlanması),
  **z** = düzeltme. `1.0.0` = bu belgedeki kapsam sahada doğrulanmış.
- Sonrası: **MAJOR** kırıcı değişiklik (veri biçimi, izinler), **MINOR**
  özellik, **PATCH** düzeltme.
- `versionCode = MAJOR×10000 + MINOR×100 + PATCH`; monotonluğu CI doğrular.
- Uygulamayı etkileyen her `main` birleşimi bir sürümdür; yalnız-belge
  değişiklikleri sürüm almaz.

### 15.2 Yayın akışı (Compass'tan)

```
1. versionCode/versionName yükselt → commit
2. git tag vX.Y.Z && git push origin vX.Y.Z
3. release.yml: check-tag (etiket ↔ versionName) → test + lint →
   imzalı APK + SHA-256 → GitHub Release (otomatik commit notlarıyla)
```

- `ci.yml`: her itişte test + lint + debug APK + rapor artifact'ları; Gradle
  wrapper doğrulaması.
- Betikler `.github/scripts/` altında ve yerelde de koşar (`check-tag.sh`,
  `collect-apk.sh`, `android-sdk.sh`).
- İmzalama: anahtar depoya girmez; Actions secrets — `KEYSTORE_BASE64`,
  `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Secret yoksa koşu
  kırılmaz: APK imzasız üretilir ve adında belirtilir.
- Yayımlanan sürüm **değişmezdir**: aynı sürümün dosyaları üzerine yazılmaz
  (WalkRun CI'ının mutable release modeli taşınmaz).

### 15.3 Commit disiplini

Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:` …) +
`CHANGELOG.md` (Keep a Changelog). Sürüm notları anlamlı olur; y/z yükseltme
kararı commit'lerden okunur. Araç gerektirmez — disiplindir.

## 16. Teknik riskler

| Risk | Seviye | Yaklaşım |
|---|---|---|
| Offline map renderer | Yüksek | Raster ile başla; MapLibre kapısını Faz 4–5 ölçümünden sonra değerlendir (7.1) |
| Karo hattı kurulum maliyeti | Yüksek | Faz 4'te küçük test paketiyle başla; rasterize aracını erken seç ve CI'da kanıtla (7.2) |
| GPS accuracy | Yüksek | Test edilmiş filtreler + saha matrisi |
| Battery drain | Yüksek | Örnekleme + sensör yaşam döngüsü; Compass ölçüm kültürü |
| Background kısıtları | Yüksek | Foreground service + güncel Android/Play kuralları |
| Compass interference | Orta | Rotation vector + bozulma uyarısı + kalibrasyon UX (Compass'tan hazır) |
| Büyük paket depolama | Orta | Paket başına ayrı `.mbtiles`; ölçüp ayarla |
| Routing beklentisi | Orta | MVP'de yapma; Return to Start sınırını açıkça anlat |

## 17. MVP sonrası yol haritası

| Özellik | Değer |
|---|---|
| Breadcrumb navigation | Kaydedilen izi ters yönde takip — Return to Start'ın güçlü devamı, offline |
| Serbest alan seçerek indirme | Paket listesinin ötesinde dikdörtgen alan seçimi |
| Gün ışığı bütçesi | Batış saati (Compass `Sun.kt`) × dönüş temposu → "karanlığa kalma" uyarısı |
| Gece modu | Kırmızı palet, alacakaranlıkta otomatik (Compass `Palette` + `Sun`) |
| Koordinat al/paylaş | `geo:`/metin/harita bağlantısı ayrıştırıcı (Compass, 5 biçim, testli) |
| Yükseklik profili | Aktivite detayında kesit grafiği |
| Sesli anonslar | Kilometre + split temposu (WalkRun, TTS) |
| Rekorlar + haftalık özet | En uzun, en hızlı 1/5/10 km; haftalık toplamlar (WalkRun) |
| Barometrik yükseklik | Basınç sensörüyle kazanım hassasiyeti |
| Offline routing | Yol ağı üzerinde navigasyon |
| Wear OS · Cloud yedek (ops.) · iOS | Daha sonra; iOS çekirdek saf kaldığı sürece arayüz portudur |
| Kalori · Strava | İstenirse WalkRun modülleri hazır |

## Kaynaklar

- Android: [LocationManager](https://developer.android.com/reference/android/location/LocationManager) ·
  [SensorManager](https://developer.android.com/reference/android/hardware/SensorManager) ·
  [Konum izinleri](https://developer.android.com/develop/sensors-and-location/location/permissions) ·
  [FGS türleri](https://developer.android.com/develop/background-work/services/fgs/service-types) ·
  [Play arka plan konum politikası](https://support.google.com/googleplay/android-developer/answer/9799150)
- Harita: [OSM tile kullanım politikası](https://operations.osmfoundation.org/policies/tiles/) ·
  [ODbL / atıf](https://www.openstreetmap.org/copyright) ·
  [MBTiles spesifikasyonu](https://github.com/mapbox/mbtiles-spec) ·
  [Geofabrik özetleri](https://download.geofabrik.de/) ·
  [tilemaker](https://github.com/systemed/tilemaker) · [Planetiler](https://github.com/onthegomap/planetiler) ·
  [MapLibre Native](https://github.com/maplibre/maplibre-native)
- Miras kod: [WalkRun](https://github.com/aripdcem/WalkRun) · [Compass](https://github.com/aripdcem/compass)
