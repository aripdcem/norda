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
4. **GPS'in oturmasını bekle**: START'tan sonra ekrandaki "Fix bekleniyor… /
   GPS ± X m" yazısı kaybolup süre-mesafe akmaya başlayana dek dur. Kapalı
   alan ve 2–3 dk'lık denemeler GPS ısınmasına yetmez; hiç nokta girmezse
   kayıt nedeniyle birlikte atılır.

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
| — | 2026-08-25 | v0.9.3 | *Geçersiz deneme* (tur sayılmaz): tenis kortu içinde 2 dk koşu — GPS ısınmadan hiç nokta kabul edilmedi, kayıt tasarım gereği atıldı. Bulgu **F-4** → v0.9.4: GPS durumu artık kayıt ekranında canlı, atılma mesajı nedenli |
| 2 | — | — | Bekliyor. Öneri: güç tasarrufu **açık** ve/veya farklı ortam (dar sokak/orman); v0.9.4 ile, GPS oturduktan sonra |
| 3 | — | — | Bekliyor |

Kapı durumu: **1/3 temiz tur.**

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
