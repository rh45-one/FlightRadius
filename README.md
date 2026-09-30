# FlightRadius

FlightRadius tracks live aircraft and tells you how far away they are from
where you are. Pick the callsigns or transponder (ICAO24) addresses you care
about and get live distances, bearings and speeds, plus an alert when one
comes within a radius you choose.

Data comes from the [OpenSky Network](https://opensky-network.org/).

> **Status:** under active development. Expect bugs, unfinished features and
> rough edges, and don't expose the web stack to the public internet (the
> backend has no authentication).

## Clients

| Client | What it is | Docs |
|---|---|---|
| **Android app** (`android/`) | Native Kotlin + Jetpack Compose app. Monitors in the background with a foreground service and raises proximity alerts. Queries OpenSky directly by default, so it works anywhere with internet. | [android/README.md](android/README.md) |
| **Web app** (`web/`) | React dashboard + Node/Express backend + nginx HTTPS proxy, run with Docker. The backend proxies OpenSky. | [below](#web-app) |

The Android app can optionally use the web backend instead of talking to
OpenSky itself, and can import your aircraft and fleets from it.

## Repository layout

```
android/   Native Android app (Gradle project)
web/
  backend/   Node.js + Express API (OpenSky access, distance computation)
  frontend/  React + Vite + TypeScript + Tailwind
  nginx/     HTTPS reverse proxy config and certs
  docker-compose.yml
docs/      Design notes
AGENTS.md  Project facts for coding agents (commands, API constraints)
```

## Android app

Quick start (JDK 21 and the Android SDK required; see
[android/README.md](android/README.md) for details):

```bash
cd android
./gradlew :app:assembleDebug          # APK in app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest      # unit tests
```

To build, install and launch on a USB-connected phone:

```bash
android/scripts/run-on-device.sh          # backend reached over your LAN
android/scripts/run-on-device.sh --usb    # backend tunnelled over the cable
```

Highlights: live distance/bearing/closing-speed cards, per-aircraft and
per-fleet alert radii, hysteresis and cooldown so alerts don't spam, optional
radar-style chirp, Doze-aware scheduling, resume after reboot, and OpenSky
credit budgeting so a free account can monitor all day. The full
description, permissions rationale and troubleshooting live in
[android/README.md](android/README.md).

## Web app

### Requirements

- Docker and Docker Compose

### Run

```bash
docker compose -f web/docker-compose.yml up --build
```

- Frontend: https://localhost:8443 (self-signed certificate)
- Backend API: http://localhost:3000

### Architecture

- Frontend: React + Vite, state in Zustand, UI state in browser localStorage
- Backend: Node.js + Express; app state persisted to
  `web/backend/data/app-state.json`
- nginx terminates HTTPS on 8443 (required for browser geolocation) and
  proxies `/api` to the backend

### Using it

- Add aircraft by callsign or ICAO24 (single or bulk with validation) and
  group them into fleets.
- Click **Enable Location** on the Monitoring page and grant the browser
  prompt, or set a manual location in Settings.
- Distances refresh every `distanceUpdateIntervalSec` seconds and on GPS
  movement (debounced by 5 s).
- Add `?debug=true` to the Monitoring URL for debug overlays, e.g.
  `https://localhost:8443/monitoring?debug=true`.

### Backend API

| Endpoint | Purpose |
|---|---|
| `GET /api/health` | Uptime, OpenSky reachability (1-credit probe), cache size |
| `GET /api/aircraft/:icao24`, `/callsign/:callsign` | Telemetry for one aircraft |
| `POST /api/aircraft/validate-callsigns` | Which callsigns are currently live |
| `POST /api/distance/aircraft`, `/fleets`, `/compute` | Batched distances and fleet proximity |
| `POST /api/user/location` | Ingest the client's location |
| `GET/POST /api/app/state` | Persisted settings, aircraft and fleets (secrets masked on read) |
| `GET/POST /api/settings/api` | OpenSky configuration status |

Distances use the Haversine formula (Earth radius 6371 km), rounded to two
decimals. Results include optional `velocity_mps`, `heading_deg` and
`last_contact`. OpenSky rate-limit errors are passed through as HTTP 429
with `Retry-After`, and the remaining credit balance is exposed in the
`X-OpenSky-Credits-Remaining` header.

Backend development (in `web/backend/`):

```bash
npm install
npm run build
npm test
```

## OpenSky credentials and limits

OpenSky works anonymously but with a small daily credit budget. For more
headroom, create an **API client** on your OpenSky account page and use its
client ID and secret. (Username/password login is no longer supported by
OpenSky.)

| Account | `/states` credits per day |
|---|---|
| Anonymous | 400 |
| Standard account | 4,000 |
| Active feeder | 8,000 |

A worldwide query costs 4 credits; a query for specific transponders or a
small area costs 1.

- **Web app:** enter the client ID and secret in Settings. They are stored in
  `web/backend/data/app-state.json` on the server, or can be set via the
  backend environment (`web/backend/.env`). Never commit that file.
- **Android app:** enter them in Settings → OpenSky account. They are
  encrypted on the device and never leave it (except to OpenSky).

## Privacy

- **Web:** your location lives in browser state and localStorage and is sent
  to your own backend only to compute distances.
- **Android (direct mode):** your location never leaves the phone; queries
  contain only transponder IDs and distances are computed on-device.

## Troubleshooting

### No distances visible

- Confirm location permission is granted (or use a manual location).
- Check that the callsigns or ICAO24 identifiers are valid and the aircraft
  is currently broadcasting. A callsign can be absent from OpenSky at any
  moment.
- If OpenSky is out of credits you will see rate-limit errors; add an API
  client or wait for the daily refill.

### Geolocation not working on mobile browsers

- Browsers only allow geolocation on HTTPS, so open
  `https://<server-ip>:8443`, where `<server-ip>` is the LAN address of the
  machine running Docker. `localhost` on a phone points at the phone itself.
- Accept the self-signed certificate warning, then the location prompt.
- Both devices must be on the same network, and the server's firewall must
  allow port 8443.

### Android app can't reach the backend

- Use `http://<server-ip>:3000/` (not `localhost`); the emulator uses
  `10.0.2.2`. Or skip the backend entirely: direct OpenSky mode is the
  default. More in [android/README.md](android/README.md#troubleshooting).

## Extending

The distance engine is provider-agnostic: OpenSky can be replaced with
another ADS-B source without changing the distance logic.

## License

See [LICENCE](LICENCE).
