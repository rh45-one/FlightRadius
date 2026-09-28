# FlightRadius — Android App Specification & Implementation Prompt

## 1. What FlightRadius is today

FlightRadius is a full-stack aircraft monitoring dashboard. It tracks live
aircraft via the OpenSky Network and computes real-time distances from the
user's current location to each tracked aircraft — essentially a personal
"flight radar" with proximity awareness.

### Current architecture (Docker web version)

```
web/
├── frontend/   React 18 + Vite + TypeScript + Tailwind + Zustand + React Router
├── backend/    Node.js + Express + TypeScript (REST API, port 3000)
└── nginx/      HTTPS reverse proxy (localhost:8443 → frontend, /api → backend)
```

### Backend REST API (`/api`)

| Endpoint | Method | Purpose |
|---|---|---|
| `/api/health` | GET | uptime, OpenSky reachability, cache size, location ingest status |
| `/api/aircraft/:icao24` | GET | telemetry for one aircraft by ICAO24 hex |
| `/api/aircraft/callsign/:callsign` | GET | telemetry for one aircraft by callsign |
| `/api/aircraft/validate-callsigns` | POST | `{callsigns[]}` → per-callsign `valid` / `no-data` |
| `/api/distance/aircraft` | POST | `{lat, lon, callsigns[], icao24s[]}` → ranked distance results |
| `/api/distance/fleets` | POST | `{lat, lon, fleets[{name, callsigns[]}]}` → per-group proximity ranking |
| `/api/distance/compute` | POST | combined distances + group proximity, returns `closest` |
| `/api/user/location` | POST | ingests `{latitude, longitude, accuracy_m, timestamp, source}` |
| `/api/app/state` | GET/POST | persisted app state (settings, UI prefs, aircraft list, fleet groups); secrets masked on read (`********`), mask sent back = keep existing |
| `/api/settings/api` | GET/POST | OpenSky API config; POST accepts empty strings to clear credentials |

### Telemetry model

```ts
AircraftTelemetry {
  icao24: string        // lowercase hex
  callsign: string|null // uppercase, trimmed
  latitude, longitude   // degrees
  altitude_m            // meters
  velocity_mps          // m/s
  heading_deg           // degrees
  last_contact          // unix seconds
}

DistanceResult {
  callsign, icao24?, distance_km, lat, lon, altitude_m, last_update
}
```

Distance uses Haversine (R = 6371 km), rounded to 2 decimals.

### Core features in the web app

- **Monitoring dashboard**: enable GPS location, then live distance cards per
  tracked aircraft, sorted by proximity; auto-refresh on a configurable
  interval (`refreshIntervalSec`, default 12s) and on GPS movement
  (debounced 5s); optional `?debug=true` overlay.
- **Aircraft tracking**: add by callsign or ICAO24; bulk-add modal with
  callsign validation against live OpenSky data.
- **Fleets**: named groups of callsigns (color, icon); per-group closest
  aircraft and ranked members.
- **Settings**: units (km/mi), refresh intervals, GPS accuracy mode, manual
  location override, `maxTrackedWarning` threshold, OpenSky API credentials
  (OAuth2 client credentials or basic auth), theme (dark/light), card
  density, time format.
- **Backend specifics worth knowing**:
  - OpenSky OAuth2 client-credentials flow with token caching; falls back
    to Basic auth, then anonymous.
  - 5-second server-side rate limiter + single in-flight dedup for
    `/states/all` calls; 10s TTL telemetry cache (10k entry cap).
  - App state persisted to `backend/data/app-state.json`.
  - OpenSky anonymous mode is heavily rate-limited — credentials strongly
    recommended.
  - An aircraft whose telemetry has null fields is skipped (`no-data`),
    not an error.

---

## 2. Prompt for Opus — build the Android app

> Copy everything below the line into Opus.

---

You are building **FlightRadius for Android** — a native Android port of an
existing aircraft-proximity monitoring web app. Read the attached
`docs/ANDROID_APP_PROMPT.md` §1 for the full REST API contract and domain
model, and explore `web/backend/src` for exact behavior (especially
`services/opensky.ts`, `services/distanceEngine.ts`, and the routes under
`src/routes/`). The existing backend is reused as-is — the app talks to it
over HTTP(S), exactly like the web frontend does.

### Goal

A premium-feeling Android app that monitors tracked aircraft and **alerts
the user when any tracked aircraft comes within a configurable radius** —
the "background radar that beeps" experience of speed-camera/radar-detector
apps. It must work reliably with the screen off, for hours, without draining
the battery or getting killed by the OS.

### Tech stack (use exactly this)

- **Kotlin + Jetpack Compose** (Material 3, dynamic color optional)
- **Min SDK 26, target latest**; single-activity Compose architecture
- **Hilt** for DI, **Retrofit/OkHttp + kotlinx.serialization** for API,
  **DataStore** for preferences, **Room** for tracked aircraft/fleets
- **Fused Location Provider** (`play-services-location`)
- **WorkManager** is NOT sufficient for sub-minute alerts — use a
  **foreground service** for the monitoring loop (see below)
- Coil for any images; no other heavy deps without justification

### Must-have features

1. **Monitoring (home) screen**
   - Live list/map-adjacent card list of tracked aircraft sorted by
      distance, each showing callsign, distance (km/mi), altitude, speed,
      heading, and time since last contact. Cards subtly animate on rank
      changes. Distance ring gauge or radial indicator around each card is
      a nice premium touch.
   - Header shows location status (GPS fix age/accuracy), OpenSky status,
     and monitoring on/off state.
2. **Proximity alerting — the core feature**
   - A foreground service (`location` + `dataSync` service types) running a
     monitoring loop at the configured interval (respect the backend's 5s
     OpenSky rate limit — batch callsigns into `/api/distance/compute`,
     never poll per-aircraft).
   - Per-aircraft and per-fleet **alert radius** (e.g. "notify when
     aircraft X is within 25 km") plus a global default radius.
   - When an aircraft crosses inside its radius: post a **high-priority
     notification with sound + vibration** (dedicated notification channel,
     user-tunable), and optionally an in-app alarm banner. Cooldown per
     aircraft (e.g. re-alert at most every N minutes, or only on
     exit-and-re-enter hysteresis — implement hysteresis, it's the correct
     approach) so it doesn't spam.
   - Continuous/radar mode option: periodic short chirp whose rate
     increases as the closest aircraft approaches (like a Geiger
     counter/radar detector). Off by default.
   - Persistent foreground notification showing monitoring status and
     current closest aircraft + distance, with Stop/Pause action.
   - Handle `BOOT_COMPLETED` to optionally resume monitoring.
3. **Aircraft management**: add by callsign/ICAO24 (validate via
   `/api/aircraft/validate-callsigns`), bulk add, delete, per-aircraft
   alert radius override, notes.
4. **Fleets**: group aircraft; fleet-level alert radius; closest-member
   summary via `/api/distance/fleets`.
5. **Settings screen**: backend base URL (default configurable in build),
   OpenSky credentials (write-only — the backend masks them on read; never
   log them), units, monitoring interval, GPS accuracy mode, manual
   location override, global alert radius, notification sound/vibration,
   theme (dark/light/system). Store secrets in EncryptedSharedPreferences
   or DataStore — never plaintext, never in logs.
6. **Location**: GPS via fused provider with a fallback "manual location"
   mode. Runtime permission flow with graceful degradation (manual mode
   when denied). Post the location to `/api/user/location` like the web
   app does.

### Reliability requirements ("bug-proof")

- Every network call has timeouts, bounded retries with backoff, and
  surfaces errors as non-blocking UI state — **no crashes, ever**, on
  backend down, airplane mode, TLS failure, malformed payloads, or OpenSky
  rate-limiting. Treat every API field as potentially null/missing.
- Foreground service must handle `onDestroy`/task removal, wake-lock only
  around the fetch cycle, and stop cleanly.
- Notifications: request `POST_NOTIFICATIONS` on Android 13+ with a
  rationale; exact-alarm permission NOT required — don't use it.
- Battery: interval floor of 10s; doze-aware (defer work when dozing
  unless user enabled "high priority mode" using
  `setAndAllowWhileIdle`-style semantics via the service loop).
- Structured logging behind a debug flag; a debug overlay/log viewer
  reachable from settings (parity with the web app's `?debug=true`).
- Unit tests for the distance/alert-state logic (hysteresis, cooldown,
  ranking) and at least one instrumented service test.

### Premium feel

- Dark-first Material 3 theme matching the web app's aesthetic (dark
  theme is the default there), smooth `AnimatedContent`/list transitions,
  distance values that tick/count animate, haptic feedback on alert,
  polished empty states ("no aircraft tracked yet" with CTA), skeleton
  loaders, adaptive icon + themed monochrome icon.
- The alert experience should feel like a cockpit warning: full-screen
  in-app alert sheet with aircraft details, bearing, closing speed,
  dismiss/snooze actions.

### Deliverables

- `android/` Gradle project that builds a working debug + release APK,
  wired to the existing backend (document `backendBaseUrl` config).
- No changes to `web/` unless strictly required; if the API needs a small
  extension (e.g. a polling-optimized endpoint), propose it, keep it
  backward compatible.
- `android/README.md`: setup, permissions rationale, and how monitoring
  works.
