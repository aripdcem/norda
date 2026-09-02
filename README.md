# Norda

**Walk. Run. Explore.** — Minimalist, offline-first outdoor navigation app
(Android, Kotlin, zero runtime dependencies).

Combines walk and run recording with a compass, offline maps, waypoints and
"Return to Start" navigation.

- Product scope, technical design and roadmap: **[docs/MVP.md](docs/MVP.md)**
- Process: SemVer, a tag + release for every app-affecting change,
  TDD (red → green → refactor). Details: MVP document, sections 13 and 15.

**Network rule:** the only class that touches the network is `TileDownloader`
(map pack download). Tracking, compass, navigation and GPX work without
internet; location data stays on the device, there is no telemetry.
Details: [docs/PRIVACY.md](docs/PRIVACY.md).

**Clean-track stance:** only **real GNSS points** that pass the quality gate
enter the recording; network/WiFi location never mixes into the track. It
may say "ready" later than assisted apps do — the GPS readiness line and the
satellite count on screen make the wait visible; in return, every point of
your track is real (the zero-difference validations in the field log:
`docs/SAHA.md`).

Field tour protocol (Phase 9 · RC): [docs/SAHA.md](docs/SAHA.md).

## Do the tests bite?

As MVP.md 13.1/5 requires, deliberate bugs are injected into the core, the
tests that fail are recorded, and all of them are reverted. Last round:
**v0.9.0** — 96 tests, **8/8 mutations caught**.

| Deliberate bug | Test(s) that bit |
|---|---|
| Δλ sign reversed in the bearing (the historical bug from the draft) | `bearingDueEastOnEquator` `bearingDueWestOnEquator` `bearingIstanbulToKaaba` `bearingAndDistanceToEastStart` |
| `yTile` boundary clamp removed | `latitudeIsClampedToProjectionDomain` |
| GPS accuracy threshold 30 m → 300 m | `poorAccuracyIsRejected` `evaluateNamesTheRejectionReason` `poorAccuracyCannotResume` `filterCountsFeedCalibration` |
| Elevation hysteresis 4 m → 0 | `flatNoiseAccumulatesNothing` |
| XML escaping removed from the GPX writer | `roundTripPreservesTrackAndWaypoints` |
| `waypoint` dropped from the migration path (repeat of the 0.7.0 bug) | `upgradeFromV1CreatesWaypointTable` |
| Negative battery "drain" reported while charging | `chargingDuringRecordingGivesNoDrain` |
| Return to Start bearing in the reverse direction | `bearingAndDistanceToEastStart` |

The round's own finding was closed as well: DDL leaking into the schema
baseline list was invisible to the parity test (both sides get polluted
together); `statementCountPerVersionIsFrozen`, which freezes the statement
count per version, was added.

License: [MIT](LICENSE). Map data: © OpenStreetMap contributors (ODbL).
