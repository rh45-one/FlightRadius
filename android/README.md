# FlightRadius Android

Native Android client for FlightRadius: tracks aircraft near you and alerts
when they come within a configurable radius. Flight data comes from one of
two sources (Settings → Flight data):

- **OpenSky (direct)** — the default. The app queries OpenSky itself,
  computes distances on-device and works anywhere with internet.
- **Own backend** — the self-hosted `web/backend` proxies OpenSky (shared
  with the web app); every cycle is one batched `POST /api/distance/compute`.

## Architecture

Single-module app (`app/`), Kotlin + Jetpack Compose + Hilt:

```
app/src/main/java/com/flightradius/app/
  data/api        Backend Retrofit API, DTOs, ApiError mapping, BackendUrl, LocalNetwork
  data/opensky    Direct OpenSky: client, OAuth2 token provider, state-vector
                  parser, CreditTracker, credit interceptors
  data/secure     CredentialStore + Keystore AES-GCM SecretCipher
  data/source     FlightDataSource (OpenSky direct / backend / selected)
  data/db         Room: aircraft, fleets, fleet memberships
  data/prefs      DataStore settings (SettingsRepository/AppSettings)
  data/repo       AircraftRepository, FleetRepository, FlightRadiusRepository
  domain          Pure logic: ProximityAlertEngine, SnapshotBuilder, Identifiers,
                  AlertText, EffectiveRadius, Backoff, Geo, UploadThrottle,
                  CallsignResolver, CreditPlanner, OpenSkyPricing, StateVector
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

## OpenSky credits

OpenSky meters `/states/all` in daily credits (anonymous 400, account with an
API client 4,000, active feeder 8,000). A worldwide query costs 4 credits; a
query for specific transponders, or a box of ≤ 25 sq°, costs 1. The app
spends as few as possible:

- **Tracked by ICAO24** → one icao24-filtered request per cycle (1 credit
  per 100 aircraft).
- **Tracked by callsign** → OpenSky can't filter by callsign, so
  `CallsignResolver` learns each callsign's transponder from an occasional
  global snapshot (validation when adding an aircraft doubles as that
  lookup) and then polls it by icao24. Unresolved callsigns are retried with
  back-off (10 min doubling to 1 h); a mapping is dropped when the airframe
  starts broadcasting another callsign or vanishes for 30 min. Global
  searches are skipped when the balance can't afford them.
- **Adaptive interval** (Settings → OpenSky credits, on by default):
  `CreditPlanner` stretches the remaining balance (minus a 20-credit
  reserve) evenly until the assumed refill at UTC midnight, never going
  below your configured interval. The balance comes from OpenSky's
  `X-Rate-Limit-Remaining` header (or the backend's
  `X-OpenSky-Credits-Remaining`).
- **Out of credits (429)** → no requests until `X-Rate-Limit-Retry-After-Seconds`
  has elapsed (15 min when OpenSky gives no hint); the Radar chip shows
  "Out of credits".

## OpenSky credentials

OpenSky only accepts OAuth2 client credentials (username/password login is
no longer supported). Create an API client on your OpenSky account page.

- **Direct mode**: enter the client ID and secret in Settings → OpenSky
  account. They're stored in their own DataStore file, encrypted with
  AES-256-GCM under a non-exportable Android Keystore key, excluded from
  cloud backup and device transfer (`res/xml/data_extraction_rules.xml`),
  and never logged. "Save & verify" fetches a token (free); a rejected client
  isn't retried every cycle until it changes or you press "Verify". Without
  credentials the app runs anonymously.
- **Backend mode**: the ID and secret are sent to the backend, which stores
  them in `web/backend/data/app-state.json`; nothing is kept on the device.

## How monitoring works

`MonitoringService` is a foreground service (types `location|dataSync`,
`START_STICKY`). Each cycle:

1. `MonitoringCycleRunner` resolves the current fix (GPS or manual) and asks
   the selected `FlightDataSource` for all tracked aircraft at once —
   never per-aircraft.
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
4. The next cycle runs after the credit-aware interval (see above; 10–600 s,
   default 15 s). Failures back off exponentially up to 5 min; a 429 waits
   for its retry-after hint.

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
- Settings → Import pulls `/api/app/state` **once** from a backend
  (one-way import; nothing is pushed back). Works in either data-source mode.
- OpenSky credentials: see "OpenSky credentials" above. `AppLog` redacts
  secrets/tokens/passwords/IDs.
- In direct mode the device location never leaves the phone: tracked-aircraft
  queries carry only transponder IDs, and distances are computed on-device.
  In backend mode fixes are posted to `/api/user/location`.

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
- **Out of credits / OpenSky down** → 429/502/504 (directly or passed
  through by the backend) map to the "Out of credits"/"OpenSky down"/
  "OpenSky timeout" chips; the app waits or backs off. Add an API client
  (4,000 credits/day) or keep "Stretch credits across the day" on. In backend
  mode, "Test connection" costs at most a 1-credit OpenSky probe.
- **"OpenSky login failed"** → the stored API client was rejected; re-enter
  it in Settings → OpenSky account.
- **Emulator GPS delivers no fix** (`dumpsys location` shows null) → switch
  Settings → Location to Manual and enter coordinates.
