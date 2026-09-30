# AGENTS.md

Project facts for agents working in this repo.

## Layout

- `web/` — Dockerized web app: React frontend + Express/TypeScript backend +
  nginx HTTPS proxy (`web/docker-compose.yml`).
- `web/backend/` — the web app's backend; optional for Android (which talks
  to OpenSky directly by default). Passes OpenSky 429s through with
  `Retry-After` and exposes the balance as `X-OpenSky-Credits-Remaining`.
- `android/` — native Android app (Kotlin + Jetpack Compose + Hilt). See `android/README.md`.
- `docs/` — cross-cutting design docs.

## Backend commands (run in `web/backend/`)

```bash
npm install
npm run build      # tsc -> dist/
npm test           # vitest unit tests
PORT=3001 node dist/index.js   # run on a custom port
```

Notes:

- `web/backend/.env` holds OpenSky credentials — dotenv loads it at runtime.
  **Never print, copy, commit or log its contents.** New secrets go through
  the secrets tooling, not the conversation.
- `GET /api/health` triggers a real (1-credit) OpenSky probe when
  `OPENSKY_ENABLED=true` — not a free liveness check.
- The backend serializes OpenSky `/states/all` fetches behind its own 5 s
  rate limiter with in-flight dedup; anonymous OpenSky access is additionally
  heavily rate-limited upstream. Never poll per-aircraft — batch via
  `POST /api/distance/compute` and `POST /api/distance/fleets`.

## Android

Tooling note: in agent sessions the file-read tool has served stale copies
of files edited by other processes; verify with `cat`/`grep` via the shell
before editing if content looks inconsistent.

Environment (this machine):

- `ANDROID_HOME=/home/hugo/Android/Sdk` (SDK has API 37.2 platform +
  `android-37.0` Google APIs x86_64 system image)
- JDK 21 (compile target JVM 17)
- Gradle via the wrapper (`android/gradlew`, Gradle 9.7.x, AGP 9.4.x)

```bash
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest     :app:assembleRelease :app:connectedDebugAndroidTest
```

Emulator:

```bash
$ANDROID_HOME/emulator/emulator -avd flightradius_api37     -no-window -no-audio -gpu swiftshader_indirect &
adb -s emulator-5554 wait-for-device
```

Physical phone: `android/scripts/run-on-device.sh [--usb] [--grant] [--logs]`
(builds debug with the host LAN IP or an adb-reverse URL, installs, launches).

- Backend URL: `flightradius.backendBaseUrl` Gradle property /
  `local.properties`, default `http://10.0.2.2:3000/`; runtime override in
  Settings → Backend URL. Release is HTTPS-only unless
  `-Pflightradius.allowCleartextInRelease=true`.
- Android 17 (API 37): LAN backends need `ACCESS_LOCAL_NETWORK`
  (`adb shell pm grant com.flightradius.app android.permission.ACCESS_LOCAL_NETWORK`);
  the emulator's `10.0.2.2` counts as LAN.

## OpenSky facts (verified against the REST docs, 2026-09)

- Auth: OAuth2 client credentials only; Basic username/password is **no
  longer accepted**. Tokens last 30 min.
- `/states/*` credit bucket per day: anonymous 400, standard 4,000, active
  feeder 8,000 (licensed 14,400/hour). Cost per `/states/all` call: 1 credit
  for a bbox ≤ 25 sq° or an icao24-only ("serial") query, 2 for ≤ 100 sq°,
  3 for ≤ 400 sq°, 4 for > 400 sq° / global. `/states/own` is free.
- Headers: `X-Rate-Limit-Remaining`; on exhaustion 429 +
  `X-Rate-Limit-Retry-After-Seconds`. Time resolution 5 s (auth) / 10 s (anon).
- `extended=1` adds state index 17 = ADS-B emitter category (0/1 unknown,
  2 light, 3 small, 4 large, 5 high-vortex large, 6 heavy, 7 high perf,
  8 rotorcraft, 9 glider, 10 lighter-than-air, 12 ultralight, 14 UAV, …).
- Backend cost per compute: icao24s → one filtered request (1 credit);
  callsigns → a global snapshot (4 credits, reused for 10 s).
- Android direct mode: icao24 polling + `CallsignResolver` (callsign →
  icao24 via occasional global searches) + `CreditPlanner` adaptive interval.
  OpenSky's refill boundary is undocumented; we assume UTC midnight.

## Roadmap (agreed 2026-09-28)

Decisions: direct OpenSky data source is the default, self-hosted backend
stays optional; redesign = Apple-like neutral base + refined FlightRadius
cyan accent; include home-screen widget, Spanish localisation and a map view
(MapLibre); no drone/Remote ID work for now; commit on `main`, never push
without asking.

1. [done 2026-09-29] Direct data source + credit budget.
2. [done 2026-09-30] Design system + restyle (tokens, Inter, grouped lists,
   onboarding, one-glance radar dial). Open: increased-contrast check,
   in-app theme switch (HIG says follow system), vendored HIG text licence.
3. Nearby airspace alerts (classifier: category → aircraft-DB lookup table
   → unknown; rules by radius/class/altitude; grouped notifications; radar
   scope + map view).
4. Premium extras (Live Update notification, Glance widget, shared-element
   transitions, es/en).
5. Field test, battery profiling, Play policy prep, release signing, CI.
