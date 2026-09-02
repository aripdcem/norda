# Changelog

Format follows [Keep a Changelog](https://keepachangelog.com/), versioning
follows [SemVer](https://semver.org/) — see `docs/MVP.md` section 15 for
the rules.

## [1.0.4] - 2026-09-02

### Changed

- The project is going international: every comment and KDoc in the app
  sources, the developer-facing exception and deprecation messages, and
  the fallback file name for an imported map pack without a name
  (`pack-<timestamp>.mbtiles`) are now English. No behavior change; the
  user interface was already bilingual (English base, Turkish locale) and
  is untouched. Documentation, tooling, workflows and release notes made
  the same switch outside the APK.

## [1.0.3] - 2026-09-01

### Fixed

- F-12 (matrix step 20 tour: battery saver on, 33 min — 61 points, a 20:28
  gap; fixes flowed only while the device was awake, a 1:24 phone call
  matched a 54-point cluster exactly): with battery saver on, the system
  can stop location while the screen is off, and the user only found out
  at the end of the walk. Pressing START with battery saver on now brings
  up a twin of the location-off dialog: "Battery saver is on. While the
  screen is off the system can stop GPS — the track may come out sparse."
  → Battery settings / Start anyway. The mode remains supported; F-5's
  during-recording visibility now extends to the moment of starting as
  well.

## [1.0.2] - 2026-08-30

### Fixed

- F-11 (two field proofs — Tour 2 (repeat): 12.7 m/s, night tour:
  12.85 m/s): the first fix could land 13–26 m off while "settling", and
  because it was used as the anchor, the phantom distance was still
  counted against the next point even when the jumping point itself was
  rejected. Two-part fix: (1) the speed cap was calibrated against the
  field to 10 m/s (the calibration MVP 5.2 anticipated — in a walk/run
  product, movement above 36 km/h is not running); (2) settling gate: the
  first fix is a candidate, not an anchor, and does not enter the
  recording until the second fix confirms physical consistency; if that
  yields a double teleport, the first fix is the culprit and the candidate
  is replaced. Expected effect on the night data: the 25.7 m phantom start
  would never have been born. The cost is a one-fix delay (~1–2 s).
- `RecordingSession.onFix` now returns the points that entered the
  recording in this call (with their persistence flags) as a list: a
  confirmed candidate + the fix can come back in the same call; the
  altitude flag belongs to the point.

## [1.0.1] - 2026-08-30

### Fixed

- F-10 (field: "Going into the Compass and back out brought the GPS lock
  instantly"): the compass's network-provider request seeds the GNSS
  engine with a coarse position on many devices, cutting the GPS lock from
  minutes to seconds. The same seeding is now deliberate: the Home warm-up
  and the recording service listen to the network provider ONLY as a
  seed — thanks to the provider filter, a network fix can enter neither
  the display nor the track (the clean-track stance is preserved
  unchanged), and it is released with the first real GPS fix (battery
  rule). A stance sentence was added to MVP.md 11.

## [1.0.0] - 2026-08-29

### MVP complete — "Walk. Run. Explore. Don't lose your way."

- Three clean field tours completed (`docs/SAHA.md` log): the scope in the
  document was verified in the field. On the gate tour the distance
  cross-validation gave **zero difference** (5613.7 ↔ 5613.7 m), elevation
  matched exactly, 1738 points = the accepted counter.
- Scope: Walk/Run recording (filtered GPS, auto-pause, elevation with
  hysteresis), foreground recording + crash recovery, hand-drawn offline
  map (with its own MBTiles pack pipeline), compass (true north + magnetic
  disturbance warning), Return to Start, waypoints, GPX import/export
  (with an embedded telemetry report), per-activity battery measurement,
  GPS readiness and satellite visibility.
- Stance: zero runtime dependencies; location stays on the device, no
  telemetry; the map pack downloader is the only class that touches the
  network. 102 JVM tests; all nine field findings were closed within the
  tour loop (F-1…F-9).

## [0.9.8] - 2026-08-27

### Added

- F-9 ("keeps searching for GPS" report): satellite visibility. While
  there is no fix, the readiness line on Home reads "Searching for GPS… ·
  satellites 0/7" (in fix/seen); the location section of Diagnostics
  carries a "satellites: X in fix / Y seen" line whether or not there is
  a fix. Seen 0 = no sky (indoors); many seen but 0 in fix = cannot lock.
  WHY GPS is not settling can now be read off the screen.

## [0.9.7] - 2026-08-27

### Fixed

- F-7: the installed version was not visible on screen — the bottom of
  Home now reads "v0.9.7" (together with the `app` attribute in the GPX,
  the version is both on screen and in the file).
- F-8: while a recording was running, the button on Home said "START",
  which came across as starting a new recording (technically none was
  opened — the service is guarded). While a recording is running the
  button becomes "BACK TO RECORDING", the type selection is locked, and a
  tap returns straight to the recording without going through the
  permission/location dialogs.

## [0.9.6] - 2026-08-27

### Fixed

- F-6 (Field Tour 2): the GPS chip only starts searching once someone asks
  for it; nobody was asking while waiting on Home, so there was a blind
  wait of 1+ minute after START (restarting the app does not affect the
  chip). GPS is now pre-warmed while Home is open — it acquires its lock
  while the user picks the type — and a readiness line appears above
  START: "Searching for GPS…" → "GPS ± X m" → "GPS ready · ± X m".
  Listening stops when leaving the screen (battery rule); while a
  recording is running the service is already listening.

### Added

- `app` attribute in the GPX report: the file carries which version wrote
  it.

## [0.9.5] - 2026-08-25

### Fixed

- F-5 (second half of the tennis-court trial: battery saver was on): many
  devices throttle GPS in this mode; the app could see it but did not say
  so. While GPS has not settled, "· battery saver on" is appended to the
  status line; the discard message for an empty recording carries the
  note "Battery saver was on — it can restrict GPS." The mode is not
  blocked — the battery-saver scenario in the field matrix remains
  supported, it is merely made visible.

## [0.9.4] - 2026-08-25

### Fixed

- F-4 (field trial: a 2 min run inside a tennis court, not a single point
  accepted): the time spent before GPS settled was not visible on screen —
  the recording silently stayed empty and was discarded, and the reason
  could not be understood. While no point has entered the recording, the
  status line now shows live GPS quality: "Waiting for a fix…" or
  "GPS ± X m". The discard message for an empty recording now gives the
  reason too: "GPS never delivered a fix" ↔ "GPS accuracy never got under
  the 30 m threshold (best ± X m)". The filter threshold was deliberately
  left unchanged. 2 new JVM tests in the core (quality observation before
  acceptance; unknown accuracy does not count as quality).

## [0.9.3] - 2026-08-25

### Added

- F-3 (field workflow): tour telemetry now travels inside the GPX. An
  `extensions`/`norda:report` block is embedded in the exported file: the
  app summary (distance, active time, ▲▼), battery (start/end) and the
  filter counters. Sharing the GPX = sharing the tour report; no manual
  notes needed. Other tools ignore the block; the filter counters are
  added only if the last recording is that activity (no wrong counters get
  embedded in an old track). On import the block does not count as data —
  the statistics are recomputed from the points. 3 new JVM tests in the
  core (round trip, file without the block, unknown battery/counters).

## [0.9.2] - 2026-08-25

### Fixed

- F-2 (Field Tour 1 usability finding): the filter counters could only be
  read while a recording was running — hard to find and note down in the
  field. When a recording finishes, the last recording's counters are
  stored persistently and stay visible in Diagnostics as "RECORDING FILTER
  (LAST)"; the tour report can be written after getting home. They are
  stored for empty/discarded recordings too — the answer to "why did the
  recording stay empty" is most often in these counters.

## [0.9.1] - 2026-08-25

### Fixed

- F-1 (Field Tour 1 analysis): the denominator of the battery rate in
  History was the active recording time; since GPS stays on during pauses
  too, the battery drains by wall-clock time and the rate was inflated
  (the 4.2 %/h shown on the tour was really 2.8 %/h). The denominator is
  now the wall-clock time from recording start→end. The tour's real data
  (2% / 42:46) went into the core as a regression test.

### Notes

- The Tour 1 GPX was recomputed through the production code paths:
  distance, time and elevation (exactly 135.0 m) matched the app exactly;
  the exclusion of intervening distance during a manual pause (the 510 m
  break walk) was verified in the field.

## [0.9.0] - 2026-08-25

### Added

- Phase 9 — Release Candidate opens: field proof + Play preparation.
- The "do the tests bite" round (MVP 13.1/5) was run for the first time
  and recorded in the README as a table: 8 deliberate bugs, all 8 caught.
  The round's own finding was closed too — the parity test could not see
  DDL leaking into the schema baseline list; a golden-table test that
  freezes the statement count per version was added (96 tests).
- `docs/PRIVACY.md` (TR+EN): location stays on the device, the network
  only downloads map packs — ready for Play's privacy-policy requirement
  for the location permission.
- `docs/SAHA.md`: 20-step field tour protocol + report template; three
  clean tours are the v1.0.0 gate.
- `check-tag` now also verifies the versionCode formula
  (MAJOR×10000+MINOR×100+PATCH) — the "CI verifies monotonicity" promise
  in 15.1 is fulfilled; both rejection branches proven locally.

### Changed

- The Compass and Diagnostics screens now register with the location
  provider even while it is off (same pattern as the 0.8.0 service fix):
  if location is turned on while the screen is open, fixes start flowing
  on their own.

## [0.8.0] - 2026-08-25

### Added

- Phase 8 — Polish: the quality bar on every screen.
- Battery measurement: the battery percentage is stored at the start and
  end of a recording (schema version 3, with migration); the History row
  shows the drain and, if the duration is long enough, the %/h rate. If
  the measurement is dirty (level unreadable, charging during the
  recording, duration under 5 min) no number is made up — nothing is
  shown. Pure `Battery` module, with 5 JVM tests.
- Filter calibration: the GPS filter now produces a rejection REASON
  (accuracy / jitter / teleport / time) and the recording counts them; the
  diagnostics screen shows live counters while a recording is running.
  This is the raw data for tuning the thresholds with field data — 4 new
  JVM tests.
- Permission UX (Home): the denial reason stays on screen; a permanent
  denial leads to the "Open settings" dialog (same language as the flow
  on the diagnostics screen).

### Changed

- With location off, START says up front that the recording will stay
  empty: "Turn on location" or "Start anyway". The service now registers
  with the provider even while it is off — if location is turned on
  later, fixes start flowing on their own.
- A recording that never received a point is not written to History as
  noise: it is deleted on finish and on recovery, and the user is told.
- Accessibility: the compass dial and the recording map are marked as
  decorative for the screen reader (the values are already in the text);
  all font sizes in sp, touch targets ≥ 48 dp verified.

## [0.7.1] - 2026-08-25

### Fixed

- 0.7.0 crash: the `waypoint` table was never created in the schema — a
  table with a DAO but no `CREATE`. The Compass, recording, map and
  waypoint screens closed with "no such table: waypoint" the moment they
  opened. The DDL and migration plan now live in the pure `Schema` module
  (schema version 2): an existing install arrives at the same schema via
  migration, a fresh install from scratch; 5 new JVM tests — including
  the parity invariant between the migration chain and a from-scratch
  install — keep this permanently protected.

## [0.7.0] - 2026-08-25

### Added

- Phase 7 — Waypoints + GPX: the data loop is closed.
- Waypoints: during a recording via "+ Point" (at the last accepted
  position) or by long-pressing the map (with a name dialog); unlimited in
  number. On the map a gold diamond + name; on the compass the nearest
  waypoint as a hollow diamond, with the bearing + distance of the two
  nearest waypoints on the bottom line. Waypoint list: tap → rename,
  long-press → delete. Default name "Point N" — the FIRST free number; a
  deleted number is reused.
- GPX export (from the track screen, SAF): `trk` + all `wpt`s in a single
  file; altitude is written only when valid (the 0.0 sentinel does not
  leak).
- GPX import (from History): `trkpt`s as an activity (distance/time/
  elevation computed by the pure core), `wpt`s as waypoints; malformed
  input is tolerated by skipping the line.
- `Gpx` (writer + parser) and `WaypointNaming` in the core, with 9 new JVM
  tests (round trip, XML escaping, malformed input, free-number
  selection).

## [0.6.0] - 2026-08-25

### Added

- Phase 6 — Navigation: the Compass screen and Return to Start. A
  hand-drawn dial (rotates relative to true north, the top index stays
  fixed), a gold diamond in the direction of the start; on the bottom line
  the bearing + distance + an estimated time if the pace is known, and
  guidance ("12° right" / "on course", ±5°).
- True north: declination from `GeomagneticField`, not recomputed until
  1 km has been travelled, and stored persistently — ready instantly on
  the next launch. If the declination is unknown the label reads
  "Magnetic".
- Magnetic disturbance warning: the measured field strength is compared
  with the expected one (25% entry / 2.5 s uninterrupted / 15% exit
  hysteresis). Status priority: disturbance > calibration.
- 3 pure modules in the core, with 11 new JVM tests: `Smoothing`
  (time-constant based, via sin/cos — 50 Hz ↔ 16 Hz feel the same),
  `DisturbanceDetector`, `ReturnToStart` (bearing + distance + ETA +
  signed turn angle).
- The Compass is one tap away from Home and from the recording screen.
  Start point: the first point of the active recording; if there is no
  recording, the start of the last activity.

## [0.5.0] - 2026-08-25

### Added

- Phase 5 — Offline Maps: our own tile packaging pipeline is live.
  `map-pack.yml` is triggered manually (id + name + bbox + zoom), builds
  the pack, attaches it to a Release under the `maps/<id>-vN` tag and
  updates `docs/maps/index.json`.
- The Maps screen now lists and downloads the packs in `index.json`:
  progress percentage, SHA-256 verification (the file is deleted on
  mismatch), a half-finished download never looks like a valid pack
  (`.part` + renaming).
- `TileDownloader`: the ONLY class that touches the network; the
  `INTERNET` permission was added solely for it. Manual import remains as
  the offline fallback path.
- `core/io/Digests`: SHA-256, verified against the NIST test vectors.
- `tools/make-map-pack.py` (region-parameterised generator, replacing the
  old test generator) + `tools/update-map-index.py`.

### Notes

- The build step uses procedural cartography for now; when real OSM
  cartography arrives, only map-pack's "Build the pack" step changes — the
  pack format, publishing and download chain stay the same.

## [0.4.0] - 2026-08-25

### Added

- Phase 4 — Custom MapView: a hand-drawn offline raster map. Only the
  tiles intersecting the viewport are drawn; MBTiles reading (with TMS
  conversion), LRU bitmap cache, background tile decoding, pan/double
  tap/two-finger zoom. A procedural grid when there is no pack — the track
  and cursor are visible without a pack too.
- `core/map/WebMercator`: projection + tile math, 8 JVM tests with known
  fixed points (Istanbul z10 tile, TMS conversion, floating-point clamping
  at the boundary latitude).
- Live map on the recording screen: the track and position cursor on the
  map, auto-follow.
- Tapping a row in History opens the track on the map (fitted to the
  screen).
- Maps screen: `.mbtiles` import (SAF), pack list, deletion, OSM
  attribution. Download + `index.json` come in Phase 5.
- `tools/make-test-map.py`: a stdlib-only procedural test pack generator
  (Istanbul z8–13, ~324 KB) — alignment and TMS errors show up instantly
  on the grid.

## [0.3.0] - 2026-08-25

### Added

- Phase 3 — Foreground Tracking: the recording now belongs to
  `TrackingService`; it continues even when the screen turns off or the
  user switches to another app. The persistent notification shows
  status · time · distance and leads back to the recording.
- If the system kills and restarts the service (START_STICKY), the
  recording is taken over from the half-finished activity on disk
  (`RecordingSession.prime`, tested): distance and time are preserved,
  elevation is rebuilt from the stored altitudes with the same hysteresis.
- The notification permission (Android 13+) is requested together with
  location; denying it does not block recording.

### Changed

- The recording screen is now display only; the back button does not
  finish the recording, the recording continues in the background.
  Finishing happens only via the Finish button.
- Home does not run recovery while the service is recording (a live
  recording is not mistaken for "half-finished" and closed).

## [0.2.0] - 2026-08-25

### Added

- Phase 2 — Activity Engine: Walk/Run recording. Home (pick a type +
  START), the recording screen (time, distance, live pace, elevation ▲▼,
  pause/resume/finish) and the History list (long-press deletes).
- The recording core in pure Kotlin, with 37 new JVM tests: GPS filter,
  pause-aware stopwatch, auto-pause/resume (20 s / 8 m), elevation
  gain/loss with hysteresis (4 m), statistics and the recording state
  machine (`RecordingSession`).
- SQLite persistence (WAL, thin layer): a single INSERT per fix; a
  recording left half-finished by process death is recovered into History
  on launch.
- The Phase 1 diagnostics screen is kept reachable from Home.

### Notes

- In this phase the recording runs while the screen is on; background
  recording comes in Phase 3 with the foreground service.

## [0.1.1] - 2026-08-25

### Changed

- The release workflow reads the signing secrets from the `ANDROID_*`
  names; if the alias is empty `norda` is assumed, if the key password is
  empty the store password is assumed.
- First signed APK release.

## [0.1.0] - 2026-08-25

### Added

- Phase 1 — Sensor Core: a location and heading debugging screen
  (permission flow, GPS fix details, rotation-vector heading,
  distance/bearing to the start point).
- `core/geo`: distance (haversine) + great-circle bearing + angle
  arithmetic, with JVM tests grounded in physical constants.
- CI: test + lint + debug APK on every push; on a `v*` tag, a signed
  release APK + GitHub release after `check-tag` verification.
