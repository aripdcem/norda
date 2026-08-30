# Saha turu protokolü — Faz 9 (RC)

MVP.md 13.3'teki matrisin yürünebilir hâli. Her tur bu sırayla koşulur;
bulgular aşağıdaki şablonla raporlanır ve düzeltmeler z-sürümü olarak akar
(v0.9.1, v0.9.2, …). Cihazda elle doğrulama birim testin *yanına* konur,
yerine değil (13.1/6).

## Tur öncesi

1. Son RC sürümünü kur (Releases'tan imzalı APK), sürümü Ayarlar →
   Uygulamalar'dan doğrula.
2. Bir harita paketi indir (Haritalar ekranı) ya da içe aktar.
3. Pil %100'e yakın, güç tasarrufu **kapalı** başlanır (ilk turlarda;
   sonraki turlarda bilerek açık denenir).
4. **GPS'in oturmasını bekle** (v0.9.6'dan beri Home'da): uygulama Home
   açıkken GPS'i ısıtmaya başlar; START'ın üstündeki satır "GPS aranıyor…"
   → "GPS hazır · ± X m" olunca başla. Uygulamayı kapatıp açmak çipi
   etkilemez — kilitlenme çip tarafındadır, beklemek tek çözümdür. Kapalı
   alan ve 2–3 dk'lık denemeler GPS ısınmasına yetmez; hiç nokta girmezse
   kayıt nedeniyle birlikte atılır.
5. **Arama açık havada da uzarsa** (v0.9.8'den beri uydu sayısı ekranda —
   "uydu 0/9" = görülen çok, kilit yok): Norda yardım verisi (aGPS)
   enjekte etmez (Play Services yok); yardım bayatsa ham GPS almanağı
   uydudan indirir, bu 2–15 dk sürebilir. Kestirme: Google Haritalar'ı
   açıp mavi nokta hassaslaşınca Norda'ya dön — sistemin GNSS yardımı
   tazelenir, kilit genelde saniyeler içinde gelir. "Uydu 0/0" açık havada
   sürüyorsa sorun cihazın GNSS/anteni demektir.

## Tur adımları

| # | Alan | Adım | Beklenen |
|---|---|---|---|
| 1 | Permissions | İzinleri Ayarlar'dan geri çek → uygulamayı aç → START | Ret nedeni ekranda; kalıcı rette "Ayarları aç" diyaloğu |
| 2 | Permissions | Konum servisini kapat → START | "Konumu aç / Yine de başlat" diyaloğu; açınca fix'ler kendiliğinden akar |
| 3 | Location | Açık alanda 10+ dk yürüyüş kaydı | İz düzgün, çizik yok; Tanılama'da filtre sayaçları makul |
| 4 | Location | Dar sokak / bina dibi bölüm | Sıçramalar filtrelenir; sayaçlarda doğruluk/ışınlama artar |
| 5 | Distance | Bilinen mesafede (ör. 400 m pist ×2) kayıt | Sapma ≤ %3 hedef; değeri rapora yaz |
| 6 | Speed | Yürü → koş → dur geçişleri | Tempo makul gecikmeyle oturur |
| 7 | Auto-pause | 5–10 dk hareketsiz bekle | Otomatik duraklar (≈20 sn), mesafe/tempo şişmez; hareketle sürer |
| 8 | Elevation | Bilinen tırmanış (ya da düz yol) | Düz yolda ▲ ≈ 0; tırmanışta profil makul |
| 9 | Background | Ekran kapat, başka uygulamaya geç, kilitle (30+ dk) | Kayıt sürer; bildirim durum·süre·mesafe gösterir |
| 10 | Battery | 30 dk / 1 sa / 2 sa turlar | Geçmiş satırındaki 🔋 %/sa değerini rapora yaz |
| 11 | Compass | Temiz alanda pusula ↔ bilinen yön | Gerçek kuzey etiketi; sapma makul (±5°) |
| 12 | Compass | Metale/mıknatısa yaklaş | Bozulma uyarısı girer, uzaklaşınca çıkar |
| 13 | Return to Start | Kayıttan uzaklaş → Pusula | Kerteriz + mesafe + ETA; "yön tutuyor" ±5° |
| 14 | Waypoints | Kayıtta "+ Nokta", haritada uzun basış, yeniden adlandır/sil | Haritada ve pusulada doğru görünür |
| 15 | GPX | Dışa aktar → geçmişi sil → aynı dosyayı içe al | İz + noktalar aynen geri gelir |
| 16 | Offline | Uçak modu: harita, kayıt, pusula, RTS | Hepsi paketle/paketsiz çalışır |
| 17 | Map | Pan/zoom/önbellek, büyük paket | Takılma yok; paket yokken ızgara |
| 18 | Recovery | Kayıt sırasında uygulamayı süreçten öldür | Servis geri gelir, kayıt sürer; olmadıysa açılışta kurtarma |
| 19 | Recovery | Kayıt sırasında yeniden başlat (reboot) | Açılışta yarım kayıt geçmişe kurtarılır |
| 20 | Battery saver | Güç tasarrufu açıkken 30 dk kayıt | Veri kaybı varsa süre/aralık notuyla rapora |

## Rapor şablonu

```
Sürüm: v0.9.x · Cihaz: <model, Android sürümü>
Tur tarihi/süresi: … · Hava/ortam: …

Adım | Sonuç (✓ / ✗ + not)
...

Ölçümler:
- Mesafe: bilinen … m ↔ uygulama … m (sapma %…)
- Pil: 🔋 …% (…%/sa) — ekran kapalı/açık oranı: …
- Filtre sayaçları (Tanılama; v0.9.2'den beri tur bittikten sonra da
  "SON KAYIT" olarak okunur): kabul … · doğruluk … · titreme … ·
  ışınlama … · zaman …
- Yükseklik: beklenen ▲… ↔ uygulama ▲…
```

Bulgu → issue ya da doğrudan mesaj; her düzeltme kendi z-sürümüyle çıkar,
tur o sürümle tekrarlanır. Üç temiz tur = v1.0.0 kapısı.

> Kısa yol (v0.9.3+): turu bitir → izi GPX olarak dışa aktar → dosyayı
> paylaş. Özet, pil ve filtre sayaçları dosyanın içinde (`norda:report`)
> gelir; şablondaki ölçümlerden yalnız bilinen-mesafe karşılaştırması elle
> yazılır.

## Tur günlüğü

| Tur | Tarih | Sürüm | Sonuç |
|---|---|---|---|
| 1 | 2026-08-25 | v0.9.0 | **Temiz** — sorun yok. Ölçümler: 2,98 km · 28:16 aktif (42:46 duvar) · ▲135 m · 🔋 %2. GPX dışa aktarımı çekirdekle yeniden hesaplandı: 962 nokta + 1 wpt; mesafe ✓ (ham 3513,7 − duraklatma sıçramaları 531,2 ≈ 2982,5), yükseklik ✓ (tam 135,0), süre ✓. Analiz bulgusu **F-1** → v0.9.1 |
| — | 2026-08-25 | v0.9.3 | *Geçersiz deneme* (tur sayılmaz): tenis kortu içinde 2 dk koşu, **güç tasarrufu AÇIK**. Kök neden F-2 sayaçlarıyla kanıtlı: **hepsi 0** — GPS uygulamaya hiç fix iletmedi (doğruluk sorunu değil); boş kayıt tasarım gereği atıldı. Bulgular **F-4** → v0.9.4 (GPS durumu ekranda canlı, atılma mesajı nedenli) ve **F-5** → v0.9.5 (güç tasarrufu açıkken bu, durum satırında ve atılma mesajında söylenir) |
| 2 | 2026-08-27 | v0.9.5 | *Bulgulu* (tekrar gerekli): kayıt hattı kusursuz — çapraz doğrulama birebir: mesafe 3513,2 ↔ 3513,2 m (duraklatma sıçraması 13,5 m doğru dışlandı), ▲123/▼144 birebir, 1086 nokta = kabul sayacı, doğruluk/ışınlama reti 0. Beyan veriyle tutarlı: gidiş 1677 m · 7:54/km koşu, dönüş 1850 m · 9:42/km yürüyüş. 🔋 %6 (~11 %/sa, ekran açık — Tur 1'in 4 katı; ekran maliyeti matrisle ayrışacak). Bulgu **F-6**: GPS edinimi — START sonrası 1+ dk kör bekleyiş, uygulama yeniden başlatmaları etkisiz → v0.9.6 ile tekrar |
| 2t | 2026-08-27 | **v0.9.8** | **Temiz** — çapraz doğrulama 0,1 m hassas: ham 1692,9 − elle-duraklatma sıçraması 22,6 = 1670,3 ↔ uygulama 1670,4 m; ▲141/▼92 birebir; 532 nokta = kabul sayacı. 23,5 dk'lık elle mola doğru dışlandı; iki ~35 sn'lik oto-duraklatma tasarım gereği mesafeye dahil. Doğruluk reti 1, ışınlama 0. 🔋 %3 (~4,0 %/sa). Rapor `app="0.9.8"` taşıdı (F-3+F-6 sahada ✓). Not: GPS kilidi Haritalar açıldıktan sonra geldi — nedensellik doğrulanamadı; uydu satırı (F-9) sonraki turlarda cevabı verecek. İlk fix'in 2. saniyesinde 12,7 m/s'lik tek oturma sıçraması filtre tavanının altında kaldı (etki ~13 m, gözlem olarak not edildi) |
| 3 | 2026-08-29 | v0.9.8 | **Temiz** — 57:17, güç tasarrufu kapalı. Çapraz doğrulama **SIFIR fark**: 5613,7 ↔ 5613,7 m (elle mola yok; 4 mikro boşluk ≤7 sn); ▲202/▼189 birebir; 1738 nokta = kabul sayacı; doğruluk/ışınlama/zaman reti 0. 🔋 %9 (~9,4 %/sa, **Norda tek başına, ekran çoğunlukla KAPALI** — yalnız 4-5 kısa saat bakışı; Strava paralel koşmadı. Ekran-kapalı için yüksekçe → **izlemede (B-1)**: olası katkılar uzun edinim dönemi [GNSS kilide dek tam güçte], aGPS'siz çip eforu, %99 üst bandı doğrusuzluğu. Güç-tasarrufu turu + bir normal tur üçgenler; 9-10 bandında kalırsa v1.0.x'te örnekleme iyileştirmesi tartışılır). Edinim: uydu satırı sahada ilk görevini yaptı — kullanıcı "GPS 3/22"yi (fix'te/görülen) izleyip kilitle başladı; 22 uydu görülmesi gökyüzünün mükemmel, edinimin aGPS'siz karakterden yavaş olduğunu kesinleştirdi. Kilit öncesi yüründüğü bölüm tasarım gereği kayıtsız (yalnız kaliteli gerçek GPS yazılır; Strava aynı aralığı ağ noktasıyla doldurur). **Strava çapraz doğrulaması:** aynı GPX Strava'da 5,62 km · 56:36 — Norda 5,61 km, nokta aralığı 56:36 → üç kaynak mutabık ✓ |

| + | 2026-08-30 | v1.0.0 | *1.0 sonrası doğrulama turu, temiz*: SIFIR fark 4621,3 ↔ 4621,3 m; ▲124/▼159 birebir; 1421 nokta = kabul; ret 0/0/0. 🔋 %3 (~4,0 %/sa; ekran çoğunlukla kapalı, tek uygulama, güç tasarrufu kapalı) → **B-1 güncellendi:** normal bant ~4 %/sa (2,8 · 4,0 · 4,0); Tur 3'ün 9,4'ü aykırı değer (uzun edinim + ekran şüpheli). Bulgu **F-10**: 2:30 kilit beklenirken Pusula'ya girip çıkmak kilidi ANINDA getirdi — pusulanın ağ-sağlayıcı isteği GNSS'i tohumluyor (Haritalar kestirmesiyle aynı mekanizma, ikinci saha kanıtı; edinim boşluğu aktif−nokta aralığı = 2:25 ile beyan birebir) → v1.0.1 |

Kapı durumu: **3/3 temiz tur — v1.0.0 KESİLDİ (29 Ağu).**

> Matris 20 (güç tasarrufu açık, uzun tur) v1.0.0 sonrası tamamlanacak;
> bulgu çıkarsa v1.0.x olarak akar. Temiz pil ölçümü için Strava'sız tur
> önerilir.

### Bulgular

- **F-1** (Tur 1 analizi, giderildi → v0.9.1): geçmişteki pil oranı aktif
  süreye bölünüyordu; GPS duraklatmada da açık kaldığından pil duvar
  saatiyle akar. Turda 4,2 %/sa görünen gerçekte 2,8 %/sa idi. Payda artık
  kayıt başı→sonu duvar saati; saha verisi regresyon testi olarak çekirdeğe
  eklendi.
- **F-2** (Tur 1 sonrası, giderildi → v0.9.2): filtre sayaçları yalnız
  kayıt sürerken görünüyordu — sahada bulması ve not alması zor. Kayıt
  bitince son kaydın sayaçları kalıcı saklanır (süreç ölümüne dayanır) ve
  Tanılama'da "KAYIT FİLTRESİ (SON KAYIT)" olarak görünmeye devam eder;
  boş/atılan kayıtta da yazılır ("kayıt neden boş"un cevabı çoğu zaman
  buradadır).
- **F-3** (Tur 1 sonrası, giderildi → v0.9.3): tur raporu için hiçbir şeyi
  elle not etmeye gerek kalmasın diye telemetri GPX'in içinde gider:
  uygulama özeti + pil + filtre sayaçları `extensions`/`norda:report`
  bloğuna gömülür. GPX'i paylaşmak = tur raporunu paylaşmak.
- **F-4** (kort denemesi, giderildi → v0.9.4): GPS oturmadan geçen süre
  ekranda görünmüyordu — 2 dk'lık deneme sessizce boş kalıp atıldı ve
  neden anlaşılamadı. Kayda nokta girmemişken durum satırı canlı GPS
  kalitesini gösterir ("Fix bekleniyor…" / "GPS ± X m"); atılma mesajı
  nedenli: "hiç fix gelmedi" ↔ "doğruluk hiç eşiğin altına inmedi
  (en iyi ± X m)". Filtre eşiği değişmedi — 30 m kalite kapısı bilinçli.
- **F-5** (aynı deneme, giderildi → v0.9.5): denemede güç tasarrufu da
  açıktı ve birçok cihaz bu modda GPS'i kısar — uygulama bunu görüyor
  (`isPowerSaveMode`) ama söylemiyordu. Artık GPS oturmamışken durum
  satırına "· güç tasarrufu açık" eklenir; boş kaydın atılma mesajı da
  "Güç tasarrufu açıktı — GPS'i kısıtlamış olabilir." notunu taşır.
  Mod engellenmez: matristeki 20. adım (güç tasarrufu açıkken kayıt)
  desteklenen bir senaryodur, yalnızca görünür kılınır.
- **F-10** (1.0 sonrası tur, giderildi → v1.0.1): kilit beklerken Pusula
  ekranına girip çıkmak kilidi anında getirdi — pusula, sapma için ağ
  sağlayıcısını da dinler ve bu istek birçok cihazda GNSS motoruna kaba
  konum tohumlayıp kilidi dakikalardan saniyelere indirir (Haritalar
  kestirmesiyle aynı mekanizma; iki bağımsız saha kanıtı). Aynı tohumlama
  artık bilinçli: Home ısıtması ve kayıt servisi ağı YALNIZ tohum olarak
  dinler — ağ fix'i sağlayıcı süzgeciyle ne göstergeye ne ize girebilir
  (temiz iz duruşu korunur) ve ilk gerçek GPS fix'iyle bırakılır (pil
  kuralı).
- **F-9** (0.9.7 sonrası "sürekli GPS arıyor" raporu → v0.9.8): "GPS
  aranıyor…" tek başına nedeni söylemiyordu — gökyüzü mü yok, kilit mi
  gelmiyor, cihaz mı arızalı ayırt edilemiyordu. Uydu görünürlüğü eklendi
  (`GnssStatus`): Home satırı "GPS aranıyor… · uydu 0/7" der, Tanılama'nın
  konum bölümü "uydu: 0 fix'te / 7 görülen" satırı taşır. Okuma: görülen 0
  = gökyüzü/anten yok (kapalı alan); görülen çok–fix'te 0 = kilitlenemiyor
  (bekle ya da cihaz aGPS'i bayat); fix'te ≥4 = fix an meselesi. Not:
  Tanılama'daki hızlı "fix" ağ sağlayıcısındandır, kayda giremez — kayıt
  yalnız gerçek GPS ister.
- **F-7** (saha kullanımı, giderildi → v0.9.7): yüklü sürüm ekranda hiçbir
  yerde görünmüyordu — "hangi sürümdesin" sorusu Ayarlar'a gidiyordu.
  Home'un altında artık "v0.9.7" yazar; GPX raporundaki `app` özniteliğiyle
  birlikte sürüm hem ekranda hem dosyada.
- **F-8** (saha kullanımı, giderildi → v0.9.7): kayıt sürerken Home'a
  dönünce düğme hâlâ "BAŞLAT" diyordu — yeni kayıt başlatılıyormuş gibi
  algılanıyordu (teknikte yeni kayıt açılmıyordu, servis korumalı). Düğme
  kayıt sürerken "KAYDA DÖN" olur, tip seçimi kilitlenir, dokunuş izin/konum
  diyaloglarına girmeden doğrudan kayda döner.
- **F-6** (Tur 2, giderildi → v0.9.6): GPS çipi ancak biri istediğinde
  aramaya başlar; Home'da beklerken kimse istemiyordu → START sonrası 1+
  dk kör bekleyiş, uygulama yeniden başlatmaları etkisiz (çip tarafı).
  Tanılama'daki "fix" ağ sağlayıcısından geldiği için yanıltıcıydı.
  Düzeltme: Home açıkken GPS ön-ısıtılır (ekrandan ayrılınca bırakılır) ve
  START'ın üstünde hazırlık satırı görünür: "GPS aranıyor…" → "GPS hazır ·
  ± X m". GPX raporuna `app` sürümü de eklendi — dosya artık hangi sürümle
  yazıldığını söyler.
