# Field tour protocol — Phase 9 (RC)

The walkable form of the matrix in MVP.md 13.3. Every tour is run in this
order; findings are reported with the template below and fixes flow out as
z-releases (v0.9.1, v0.9.2, …). Manual verification on the device sits
*beside* the unit tests, not in their place (13.1/6).

## Before the tour

1. Install the latest RC release (signed APK from Releases); verify the
   version under Settings → Apps.
2. Download a map pack (Maps screen) or import one.
3. Start with the battery near 100% and battery saver **off** (on the first
   tours; on later tours it is deliberately tried on).
4. **Wait for GPS to settle** (on Home since v0.9.6): the app starts warming
   up GPS while Home is open; start once the line above START turns from
   "Searching for GPS…" into "GPS ready · ± X m". Closing and reopening the
   app does not affect the chip — the lock happens on the chip side, and
   waiting is the only remedy. Indoor spaces and 2–3 min attempts are not
   enough for the GPS warm-up; if no point ever enters, the recording is
   discarded together with the reason.
5. **If the search drags on even in the open** (satellite count on screen
   since v0.9.8 — "satellites 0/9" = many seen, no lock): Norda injects no
   assistance data (aGPS) (no Play Services); if the assistance is stale, the
   raw GPS almanac is downloaded from the satellites, which can take 2–15
   min. Shortcut: open Google Maps and, once the blue dot sharpens, return to
   Norda — the system's GNSS assistance is refreshed and the lock usually
   arrives within seconds. If "satellites 0/0" persists in the open, the
   problem is the device's GNSS/antenna.

## Tour steps

| # | Area | Step | Expected |
|---|---|---|---|
| 1 | Permissions | Revoke the permissions in Settings → open the app → START | Denial reason on screen; on permanent denial the "Open settings" dialog |
| 2 | Permissions | Turn off the location service → START | "Turn on location / Start anyway" dialog; once turned on, fixes flow by themselves |
| 3 | Location | 10+ min walk recording in the open | Track smooth, no scratches; filter counters in Diagnostics reasonable |
| 4 | Location | Narrow street / right beside buildings section | Spikes are filtered; accuracy/teleport counters rise |
| 5 | Distance | Recording over a known distance (e.g. 400 m running track ×2) | Target deviation ≤ 3%; write the value into the report |
| 6 | Speed | Walk → run → stop transitions | Pace settles with reasonable delay |
| 7 | Auto-pause | Stand still for 5–10 min | Auto-pauses (≈20 s), distance/pace do not inflate; resumes with movement |
| 8 | Elevation | Known climb (or flat road) | On flat road ▲ ≈ 0; on the climb the profile is reasonable |
| 9 | Background | Turn the screen off, switch to another app, lock (30+ min) | Recording continues; the notification shows state · duration · distance |
| 10 | Battery | 30 min / 1 h / 2 h tours | Write the 🔋 %/h value from the History row into the report |
| 11 | Compass | Compass ↔ known bearing in a clean area | "True north" label; deviation reasonable (±5°) |
| 12 | Compass | Approach metal/a magnet | Disturbance warning appears, disappears when moving away |
| 13 | Return to Start | Walk away during a recording → Compass | Bearing + distance + ETA; "on course" ±5° |
| 14 | Waypoints | "+ Point" while recording, long-press on the map, rename/delete | Shows correctly on the map and the compass |
| 15 | GPX | Export → delete from history → import the same file | Track + waypoints come back exactly as they were |
| 16 | Offline | Airplane mode: map, recording, compass, RTS | Everything works with/without a pack |
| 17 | Map | Pan/zoom/cache, large pack | No stutter; grid when there is no pack |
| 18 | Recovery | Kill the app's process during a recording | Service comes back, recording continues; if not, recovery at launch |
| 19 | Recovery | Restart the device (reboot) during a recording | At launch the unfinished recording is recovered into History |
| 20 | Battery saver | 30 min recording with battery saver on | If there is data loss, into the report with a duration/interval note |

## Report template

```
Version: v0.9.x · Device: <model, Android version>
Tour date/duration: … · Weather/environment: …

Step | Result (✓ / ✗ + note)
...

Measurements:
- Distance: known … m ↔ app … m (deviation …%)
- Battery: 🔋 …% (…%/h) — screen off/on ratio: …
- Filter counters (Diagnostics; since v0.9.2 also readable after the tour
  ends as "RECORDING FILTER (LAST)"): accepted … · accuracy … · jitter … ·
  teleport … · time …
- Elevation: expected ▲… ↔ app ▲…
```

Finding → issue or a direct message; every fix ships as its own z-release and
the tour is repeated on that release. Three clean tours = the v1.0.0 gate.

> Shortcut (v0.9.3+): finish the tour → export the track as GPX → share the
> file. Summary, battery and filter counters come inside the file
> (`norda:report`); of the measurements in the template only the
> known-distance comparison is written by hand.

## Tour log

| Tour | Date | Version | Result |
|---|---|---|---|
| 1 | 2026-08-25 | v0.9.0 | **Clean** — no issues. Measurements: 2.98 km · 28:16 active (42:46 wall clock) · ▲135 m · 🔋 2%. The GPX export was recomputed with the core: 962 points + 1 wpt; distance ✓ (raw 3513.7 − pause spikes 531.2 ≈ 2982.5), elevation ✓ (exactly 135.0), duration ✓. Analysis finding **F-1** → v0.9.1 |
| — | 2026-08-25 | v0.9.3 | *Invalid attempt* (does not count as a tour): 2 min run inside a tennis court, **battery saver ON**. Root cause proven by the F-2 counters: **all 0** — GPS never delivered a fix to the app (not an accuracy problem); the empty recording was discarded by design. Findings **F-4** → v0.9.4 (GPS state live on screen, discard message states the reason) and **F-5** → v0.9.5 (when battery saver is on, this is said in the status line and in the discard message) |
| 2 | 2026-08-27 | v0.9.5 | *With findings* (repeat required): the recording pipeline was flawless — cross-validation matched exactly: distance 3513.2 ↔ 3513.2 m (a 13.5 m pause spike correctly excluded), ▲123/▼144 exact, 1086 points = accepted counter, accuracy/teleport rejections 0. The user's report is consistent with the data: out 1677 m · 7:54/km run, back 1850 m · 9:42/km walk. 🔋 6% (~11 %/h, screen on — 4× Tour 1; the screen cost will be separated out with the matrix). Finding **F-6**: GPS acquisition — 1+ min of blind waiting after START, app restarts ineffective → repeat with v0.9.6 |
| 2r (repeat) | 2026-08-27 | **v0.9.8** | **Clean** — cross-validation accurate to 0.1 m: raw 1692.9 − manual-pause spike 22.6 = 1670.3 ↔ app 1670.4 m; ▲141/▼92 exact; 532 points = accepted counter. The 23.5 min manual break was correctly excluded; two ~35 s auto-pauses are included in the distance by design. Accuracy rejections 1, teleport 0. 🔋 3% (~4.0 %/h). The report carried `app="0.9.8"` (F-3+F-6 ✓ in the field). Note: the GPS lock came after Google Maps was opened — causality could not be verified; the satellite line (F-9) will give the answer on the next tours. A single settling spike of 12.7 m/s in the 2nd second of the first fix stayed under the filter ceiling (impact ~13 m, noted as an observation) |
| 3 | 2026-08-29 | v0.9.8 | **Clean** — 57:17, battery saver off. Cross-validation **ZERO difference**: 5613.7 ↔ 5613.7 m (no manual break; 4 micro gaps ≤7 s); ▲202/▼189 exact; 1738 points = accepted counter; accuracy/teleport/time rejections 0. 🔋 9% (~9.4 %/h, **Norda alone, screen mostly OFF** — only 4-5 short glances at the clock; Strava did not run in parallel. Highish for screen-off → **watch item (B-1)**: possible contributors are the long acquisition period [full power until the GNSS lock], chip effort without aGPS, non-linearity of the 99% upper band. A battery-saver tour + one normal tour will triangulate; if it stays in the 9-10 band, a sampling improvement is discussed for v1.0.x). Acquisition: the satellite line did its first job in the field — the user watched "GPS 3/22" (in fix/seen) and started with the lock; seeing 22 satellites established that the sky was perfect and that the acquisition is slow by its aGPS-less nature. The section walked before the lock is unrecorded by design (only quality real GPS is written; Strava fills the same interval with a network point). **Strava cross-validation:** the same GPX in Strava 5.62 km · 56:36 — Norda 5.61 km, point span 56:36 → three sources agree ✓ |

| + | 2026-08-30 | v1.0.0 | *Post-1.0 verification tour, clean*: ZERO difference 4621.3 ↔ 4621.3 m; ▲124/▼159 exact; 1421 points = accepted; rejections 0/0/0. 🔋 3% (~4.0 %/h; screen mostly off, single app, battery saver off) → **B-1 updated:** normal band ~4 %/h (2.8 · 4.0 · 4.0); Tour 3's 9.4 is an outlier (long acquisition + screen suspected). Finding **F-10**: after waiting 2:30 for a lock, entering and leaving Compass brought the lock INSTANTLY — the compass's network-provider request seeds the GNSS (the same mechanism as the Google Maps shortcut, second field evidence; the acquisition gap of active − point span = 2:25 matches the user's report exactly) → v1.0.1 |

| + | 2026-08-30 | v1.0.1 | *Night tour (4:45 warm-up walk + 30:05 run), clean — **F-10 confirmed in the field:** the wait for a lock dropped from 2:30 to seconds (the user's report; seeding happened by itself, no shortcut). Cross-validation again ZERO difference: 4776.2 ↔ 4776.2 m; ▲174/▼182 exact; 1372 points = accepted; jitter 344 (natural for walking/standing at ~1 s cadence), teleport 1, accuracy/time 0. 🔋 3% / 36:02 ≈ 5.0 %/h (the B-1 band holds at ~4–5; percentage granularity on a short tour is ±1). **Second-device comparison (first time):** the Strava recording of the friend who ran along captured only the run — run segment Norda 4281.2 ↔ Strava 4379.7 m (difference −2.25%; Strava's unfiltered raw total inflates slightly, the direction is as expected), route agreement median 2.1 m / p90 6.4 m. Finding **F-11**: settling spike for the SECOND time (12.85 m/s, 25.7 m in the 2nd s of the first fix; 12.7 m/s on 2r) → v1.0.2. Watch item **Y-1**: elevation compared against an independent reference for the first time — where Norda's raw GPS sees a ~35 m band on the run section, Strava's DEM-corrected track gives a ~17 m band; raw GPS vertical noise reads the gain high, DEM/baro correction is a post-MVP candidate; data will be collected over the tours* |

| + | 2026-09-01 | v1.0.2 | *Matrix 20 (battery saver ON, 33 min open-air walk) — step completed, finding **F-12**: the system STOPPED location while the screen was off. 61 points remained from the 33:28 tour (at normal cadence it would be ~1300); 6 gaps >5 s totalling 31:39, the longest **20:28**. Fixes flowed only while the device was awake: the moment of START, glances at the screen and the 1:24 phone call at 13:12 — a 54-point cluster coinciding exactly with the user's report, the very proof of "awake CPU = flowing GPS". GPS lock fast (F-10 ✓ for the third time); first fix clean, no settling spike (first field tour of the F-11 gate). Cross-validation again ZERO difference: 2074.7 ↔ 2074.7 m — but 91% of the distance is the straight line of the gap legs: recorded 2.07 km, a lower bound of the real path (~2.6–3 km). 🔋 2% / 33:31 ≈ 3.6 %/h (the battery gain from battery saver is marginal, the data loss heavy). **Y-1 gathered evidence:** ▲132/▼133 on a flat seaside walk — including the absurd ele=115 m of a single fix at 12:51 (its surroundings 40 m); with sparse+poor fixes, vertical noise dominates the gain. The seaside was later confirmed by the user's report too, and a calibration gift fell out of it: median ele at the water's edge 39 m (10th–90th percentile: 38–43) — since the WGS84 geoid separation in Istanbul is ~+37 m, this is sea level itself: the device reports ELLIPSOID height, as `getAltitude` documents, and applies no geoid correction. A constant offset does not affect ▲/▼, but absolute altitude reads ~37 m high on screen/in the GPX; the 115 fix means a ~75 m single-fix vertical error. The future fix is two layers (post-MVP): geoid separation for absolute altitude, DEM/baro for gain. → v1.0.3* |

| + | 2026-09-02 | v1.0.3 | *First tour of the free period (brisk walk 6.27 km, 58:52 point span / 61:38 active), clean — no finding in the recording pipeline. Cross-validation ZERO difference: 6268.6 ↔ 6268.6 m; ▲215/▼244 exact; 1885 points = accepted. Acquisition 2:46 (the user's report "~2 min" + the Finish tail): the recording was started while leaving an indoor space — with the sky blocked, network seeding cannot change the physics (F-10 solves almanac starvation, not the wall), expected by design. **The indoor passage (reported at 09:03–09:05) matches the data exactly:** point density thinned 33→12/min, a 48 s gap was bridged with a 102 m straight line (2.13 m/s, plausible) — inside, the device went quiet instead of producing bad fixes (accuracy rejections 1 over the whole tour); filter+bridge carried the passage gracefully. A below-ceiling settling jitter at the start: first step 8.73 m/s (the tour's maximum, impact ~10 m) — the documented residual band of the F-11 gate; teleport rejections 1. 🔋 6% / 61:38 ≈ 5.8 %/h — the start was at 100%: upper-band non-linearity suspected (B-1 note: tours starting from a full charge may read high), band recording continues* |

Gate status: **3/3 clean tours — v1.0.0 CUT (Aug 29).**

> The matrix was completed on September 1: the last step, 20 (battery saver
> on), surfaced F-12 and was closed with v1.0.3 — all 20 steps have been run
> in the field.

### Findings

- **F-1** (Tour 1 analysis, fixed → v0.9.1): the battery rate in History was
  divided by active time; since GPS stays on during pauses too, battery
  drains by wall clock. What showed as 4.2 %/h on the tour was really
  2.8 %/h. The denominator is now the wall clock from recording start→end;
  the field data was added to the core as a regression test.
- **F-2** (after Tour 1, fixed → v0.9.2): the filter counters were visible
  only while a recording was running — hard to find and note down in the
  field. When a recording ends, the last recording's counters are stored
  persistently (surviving process death) and stay visible in Diagnostics as
  "RECORDING FILTER (LAST)"; they are written for an empty/discarded
  recording too (the answer to "why is the recording empty" is usually
  found here).
- **F-3** (after Tour 1, fixed → v0.9.3): so that nothing needs to be noted
  by hand for the tour report, telemetry travels inside the GPX: the app
  summary + battery + filter counters are embedded in the
  `extensions`/`norda:report` block. Sharing the GPX = sharing the tour
  report.
- **F-4** (court attempt, fixed → v0.9.4): the time passing while GPS had not
  settled was not visible on screen — the 2 min attempt silently stayed
  empty, was discarded, and the reason could not be understood. While no
  point has entered the recording, the status line shows live GPS quality
  ("Waiting for a fix…" / "GPS ± X m"); the discard message states the
  reason: "GPS never delivered a fix" ↔ "GPS accuracy never got under the
  threshold (best ± X m)". The filter threshold did not change — the 30 m
  quality gate is deliberate.
- **F-5** (same attempt, fixed → v0.9.5): battery saver was on during the
  attempt as well, and many devices throttle GPS in this mode — the app could
  see this (`isPowerSaveMode`) but did not say so. Now, while GPS has not
  settled, "· battery saver on" is appended to the status line; the empty
  recording's discard message also carries the note
  "Battery saver was on — it can restrict GPS." The mode is not blocked:
  step 20 of the matrix (recording with battery saver on) is a supported
  scenario; it is only made visible.
- **F-10** (post-1.0 tour, fixed → v1.0.1): entering and leaving the Compass
  screen while waiting for a lock brought the lock instantly — the compass
  also listens to the network provider for declination, and on many devices
  this request seeds the GNSS engine with a coarse position, cutting the lock
  from minutes to seconds (the same mechanism as the Google Maps shortcut;
  two independent pieces of field evidence). The same seeding is now
  deliberate: the Home warm-up and the recording service listen to the
  network ONLY as a seed — a provider filter keeps a network fix out of both
  the indicator and the track (the clean-track stance is preserved), and the
  seed is released with the first real GPS fix (battery rule). Confirmed in
  the field on the night tour: lock in seconds instead of 2:30.
- **F-11** (2r + night tour, fixed → v1.0.2): during first-fix "settling" the
  first fix came in 13–26 m off on two tours (12.7 and 12.85 m/s — just
  under the old 15 m/s ceiling), and because it was made the anchor, the
  phantom distance was still counted with the next point even when the
  jumping point was rejected. Two-part fix: the speed ceiling was calibrated
  to 10 m/s with field data (the calibration MVP 5.2 foresaw; in a
  walking/running product, movement above 36 km/h is not running), and the
  first fix is now a CANDIDATE, not an anchor — it does not enter the
  recording until physical consistency is confirmed by the second fix; if
  that yields a double teleport, the culprit is the first fix and the
  candidate is replaced. Expected effect on the night data: 4776.2 → 4750.5 m
  (the 25.7 m phantom start would never have been born). The cost is a
  one-fix delay (~1–2 s).
- **F-12** (matrix 20 tour, fixed → v1.0.3): in battery saver the system
  (documented Android 9+ behavior) can stop the location service while the
  screen is off: on the 33 min tour fixes flowed only while the device was
  awake — 61 points, a single 20:28 gap; the 1:24 phone call coincided
  exactly with a 54-point cluster (awake CPU = flowing GPS). F-5 said this in
  the status line; now, if battery saver is on when START is pressed, a twin
  of the location-off dialog appears: "Battery saver is on. While the screen
  is off the system can stop GPS — the track may come out sparse." →
  Battery settings / Start anyway. The mode remains supported (the matrix 20
  scenario); data loss is no longer a surprise but a conscious choice. Gap
  legs continue to count in the distance as a straight line — an honest
  lower bound of the real path.
- **F-9** ("keeps searching for GPS" report after 0.9.7 → v0.9.8):
  "Searching for GPS…" alone did not say why — no sky, a lock not arriving,
  or a faulty device could not be told apart. Satellite visibility was added
  (`GnssStatus`): the Home line says "Searching for GPS… · satellites 0/7",
  and the location section of Diagnostics carries a
  "satellites: 0 in fix / 7 seen" line. How to read it: 0 seen = no
  sky/antenna (indoors); many seen–0 in fix = cannot lock (wait, or the
  device's aGPS is stale); ≥4 in fix = a fix is moments away. Note: the quick
  "fix" in Diagnostics comes from the network provider and cannot enter the
  recording — recording wants real GPS only.
- **F-7** (field use, fixed → v0.9.7): the installed version was not shown
  anywhere on screen — the question "which version are you on" went to
  Settings. The bottom of Home now reads "v0.9.7"; together with the `app`
  attribute in the GPX report, the version is both on screen and in the file.
- **F-8** (field use, fixed → v0.9.7): returning to Home while a recording
  was running, the button still said "START" — it was perceived as starting a
  new recording (technically no new recording was opened; the service is
  protected). While a recording is running the button becomes
  "BACK TO RECORDING", the type selection is locked, and a tap returns
  straight to the recording without entering the permission/location dialogs.
- **F-6** (Tour 2, fixed → v0.9.6): the GPS chip only starts searching when
  someone asks for it; while waiting on Home nobody was asking → 1+ min of
  blind waiting after START, app restarts ineffective (chip side). The "fix"
  in Diagnostics was misleading because it came from the network provider.
  Fix: GPS is pre-warmed while Home is open (released on leaving the screen)
  and a readiness line appears above START: "Searching for GPS…" →
  "GPS ready · ± X m". The `app` version was also added to the GPX report —
  the file now says which version wrote it.
