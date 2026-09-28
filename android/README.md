# FlightRadius Android

Native Android client for FlightRadius: tracks aircraft via the existing
TypeScript backend (`web/backend`), which remains the authoritative source for
OpenSky access and distance calculation. The app never talks to OpenSky
directly — every cycle is one batched `POST /api/distance/compute` call.

## Architecture

Single-module app (`app/`), Kotlin + Jetpack Compose + Hilt:

```
app/src/main/java/com/flightradius/app/
  data/api        Retrofit API, DTOs, ApiError mapping, BackendUrl, LocalNetwork
  data/db         Room: aircraft, fleets, fleet memberships
  data/prefs      DataStore settings (SettingsRepository/AppSettings)
  data/repo       AircraftRepository, FleetRepository, FlightRadiusRepository
  domain          Pure logic: ProximityAlertEngine, SnapshotBuilder, Identifiers,
                  AlertText, EffectiveRadius, Backoff, Geo, UploadThrottle
  location        LocationRepository (GPS/manual, ref-counted owners)
  service         MonitoringService (FGS), MonitoringCycleRunner,
                  MonitoringController, MonitoringStateRepository,
                  ConnectivityMonitor, RadarChirpPlayer, BootReceiver,
                  AlertActionReceiver
  notifications   AlertNotifier (channels, actions)
  ui              Compose screens per feature + theme/format/components
  util/log        AppLog ring buffer (redacts secrets)
```

Backend API contract lives in `data/api/FlightRadiusApi.kt` + `Dtos.kt`.
The backend's additive per-aircraft fields `velocity_mps`, `heading_deg` and
`last_contact` drive speed/heading/age in the UI.

## Requirements

- JDK 21 (compile target is JVM 17)
- Android SDK with API 37 platform (`android-37.2`) + build tools
- Gradle is used via the wrapper — no local Gradle install needed

## Build & test

```bash
cd android
./gradlew :app:assembleDebug                 # debug APK -> app/build/outputs/apk/debug/
./gradlew :app:assembleRelease               # release APK -> app/build/outputs/apk/release/
./gradlew :app:testDebugUnitTest             # JVM unit tests
./gradlew :app:connectedDebugAndroidTest     # instrumented tests (device/emulator)
```

## Test on your phone

Start the backend (`docker compose -f web/docker-compose.yml up --build`),
plug the phone in with USB debugging enabled, then from the repo root:

```bash
android/scripts/run-on-device.sh          # phone on the same Wi-Fi as this machine
android/scripts/run-on-device.sh --usb    # backend tunnelled over the cable (adb reverse)
```

The script builds the debug APK with the backend URL baked in (default
`http://<this machine's LAN IP>:3000/`), installs it and launches it. `--usb`
needs no Wi-Fi, firewall rule or "Nearby devices" permission, but stops
working once you unplug. Wi-Fi mode keeps working unplugged as long as the
phone stays on the same network, which is what you want for screen-off
monitoring tests. Other options: `--grant` (pre-grant permissions),
`--logs` (tail logcat), `--serial ID`, `--backend URL`, `--reinstall`.
A backend URL saved in Settings → Backend URL takes precedence over the
built-in one.

## Backend URL

The backend base URL is resolved in this order:

1. Settings → Backend URL (runtime override, persisted in DataStore)
2. `flightradius.backendBaseUrl` Gradle property or `local.properties` entry
3. Built-in default `http://10.0.2.2:3000/` (emulator → host)

```bash
./gradlew -Pflightradius.backendBaseUrl=https://radius.example.com/ :app:assembleRelease
```

## Cleartext & TLS

- Debug builds allow `http://`.
- Release builds are HTTPS-only unless built with
  `-Pflightradius.allowCleartextInRelease=true` (LAN/dev only).
- Self-signed nginx certs: install the CA on the device — user-installed CAs
  are trusted by the app's network security config.

## Release signing

`keystore.properties` (repo root of `android/`, gitignored) may define
`storeFile`/`storePassword`/`keyAlias`/`keyPassword`; without it the release
falls back to the debug key — fine for local smoke tests, **not** for
distribution.

## Permissions

| Permission | Why |
|---|---|
| INTERNET | Backend API calls |
| ACCESS_NETWORK_STATE | Offline detection, connectivity-aware ticks |
| ACCESS_LOCAL_NETWORK (API 37) | Android 17 requires "Nearby devices" to reach LAN hosts — incl. the emulator's `10.0.2.2` |
| ACCESS_FINE/COARSE_LOCATION | GPS location mode |
| ACCESS_BACKGROUND_LOCATION | Boot-resume in GPS mode only (service restarts before UI) |
| POST_NOTIFICATIONS | Proximity alerts + persistent monitoring status |
| FOREGROUND_SERVICE(+_LOCATION, +_DATA_SYNC) | Foreground monitoring service, types `location|dataSync` |
| WAKE_LOCK | Cycle, heartbeat (manual mode), tick, high-priority wakelocks |
| RECEIVE_BOOT_COMPLETED | Optional resume-on-boot |
| VIBRATE | Alert haptics |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | Prompted only when high-priority mode is on |

No exact alarms, no full-screen intent.

## How monitoring works

`MonitoringService` is a foreground service (types `location|dataSync`,
`START_STICKY`). Each cycle:

1. `MonitoringCycleRunner` resolves the current fix (GPS or manual), builds a
   `POST /api/distance/compute` request for **all** tracked aircraft at once —
   the backend is rate-limited by OpenSky (~5 s), so the app must never poll
   per-aircraft.
2. `SnapshotBuilder` ranks observations, computes bearing/closing, resolves
   the effective radius per aircraft:
   **aircraft override → maximum fleet radius → global radius**.
3. `ProximityAlertEngine` evaluates zones with hysteresis + cooldown:
   - Enter: `d <= r` → alert (post notification + optional in-app sheet).
   - Exit: `d > r + max(r × 15%, 0.5 km)` → reset; boundary jitter can't re-alert.
   - Cooldown: ≥ 5 min between emitted alerts per aircraft.
   - Stale: `last_contact` older than 120 s is ignored for zone changes;
     10 min without fresh data resets the zone to OUTSIDE.
   - Snooze suppresses alerts for 15 min / 1 h (in-app sheet) or 30 min
    (notification action).
4. Failures back off exponentially up to 5 min; success resets to the
   configured interval (10–600 s, default 15 s).

### Power, doze, Android 15/17

- Every cycle holds a 90 s partial wakelock (`FlightRadius:cycle`).
- **Manual mode** also holds `FlightRadius:heartbeat` between polls
  (delay + 30 s) — the CPU can't suspend while no fused-location heartbeat is
  expected. Screen stays off but battery cost is higher; prefer GPS mode.
- Location callbacks take a 10 s `FlightRadius:tick` wakelock so the loop is
  always reached.
- Doze (device idle) defers cycles unless **high-priority mode** is on —
  that mode holds a re-acquired 30-min wakelock, an `setAndAllowWhileIdle`
  alarm backstop, and asks for the battery-optimization exemption.
- Boot-resume (`RECEIVE_BOOT_COMPLETED`) restarts monitoring only if it was
  on and the FGS can legally start. The FGS type is `location` when a
  location permission is granted, else `dataSync`. GPS-mode resume requires
  `ACCESS_BACKGROUND_LOCATION`. Android 15+ forbids starting a `dataSync`
  FGS from BOOT_COMPLETED, so manual-mode boot-resume posts a
  "Tap to resume monitoring" notification instead. Android 15+ also caps
  `dataSync` FGS at ~6 h per 24 h — on `onTimeout` the service switches to
  the `location` type when permission allows, otherwise it stops and posts
  a notification. After a boot-resume the radar chirp can't play (Android 17
  WIU restriction) — it only chirps while the app was user-started.
- On Android 17, LAN backends (private/link-local/CGNAT hosts, `.local`,
  single-label names — literal hosts only, no DNS) need `ACCESS_LOCAL_NETWORK`;
  the cycle surfaces a `LocalNetworkPermissionRequired` error + UI prompt
  instead of failing opaquely.

## Data ownership

- Room is the local source of truth for aircraft + fleets; monitoring state
  is process-local (`MonitoringStateRepository`).
- Settings → Import pulls `/api/app/state` **once** (one-way import; nothing
  is pushed back).
- OpenSky credentials are **write-only**: Settings sends them to the backend
  and clears the fields; they are never stored on-device or logged
  (`AppLog` redacts secrets/tokens/passwords/IDs).

## Debug console

Settings → Debug → "Open debug console": live `MonitoringState` dump (status,
cycles, latency, failures, next cycle, OpenSky status, doze, wakelocks,
high-priority, started-from-background, fix age) plus the structured,
redacted `AppLog` buffer with level filters, search, copy-all and clear.
Enable via the "Debug logging" switch (also shows the radar debug overlay).

## Troubleshooting

- **Backend unreachable** → check the URL (emulator needs `10.0.2.2`, not
  `localhost`); the docker compose stack publishes the backend on port 3000
  over plain HTTP (debug builds only). `adb reverse tcp:3000 tcp:3000` is a
  manual alternative when you'd rather point the app at `localhost`.
- **LAN permission** → Android 17: grant "Nearby devices" when prompted, or
  `adb shell pm grant com.flightradius.app android.permission.ACCESS_LOCAL_NETWORK`.
- **TLS errors** → release is HTTPS-only; rebuild with
  `-Pflightradius.allowCleartextInRelease=true` for http, or install your
  self-signed CA on the device.
- **OpenSky rate-limited/down** → the backend returns 429/502/504; the app
  maps them to the "Rate limited"/"OpenSky down"/"OpenSky timeout" chips and
  backs off. Note `/api/health` triggers a real OpenSky fetch — "Test
  connection" consumes backend rate limit.
- **Emulator GPS delivers no fix** (`dumpsys location` shows null) → switch
  Settings → Location to Manual and enter coordinates.
