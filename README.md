# FlightRadius

FlightRadius is a Dockerized full-stack aircraft monitoring dashboard. It
fetches live aircraft telemetry from OpenSky and computes real-time distance
from your current location to each tracked aircraft.

Please note FlightRadius is still under development. Users may encounter bugs, unfinished functionality implementations, or security flaws. 

## Index

- [Quick start](#quick-start)
- [Architecture overview](#architecture-overview)
- [Data sources](#data-sources)
- [API credentials](#api-credentials)
- [Location and privacy](#location-and-privacy)
- [Distance computation](#distance-computation)
- [Refresh logic](#refresh-logic)
- [Debug tools](#debug-tools)
- [Configuration](#configuration)
- [Troubleshooting](#troubleshooting)

## Quick start

### Requirements

- Docker and Docker Compose

### Run with Docker

```bash
docker compose up --build
```

- Frontend: https://localhost:8443
- Backend API: http://localhost:3000

If you are testing on mobile, use HTTPS. The Docker setup exposes 8443 to
support geolocation requirements on modern browsers.

## Architecture overview

- Frontend: React + Vite
- Backend: Node.js + Express
- Data: OpenSky Network (live state vectors)
- Storage: Browser localStorage for UI state; backend JSON for app state

## Data sources

OpenSky Network provides live state vectors used for:

- Aircraft telemetry (position, altitude, velocity)
- Distance calculations from your current location

OpenSky can be rate limited or delayed, and a tracked callsign might not be
visible at a given moment.

## API credentials

OpenSky works in anonymous mode with strict rate limits. For higher limits
and stability, configure credentials.

You can enter API settings in the UI:

1. Open Settings in the web app.
2. Fill in the OpenSky API fields and save.

These settings are persisted in the backend app state.

### Fields and where they are stored

- API base URL, auth URL, username, password, client ID, client secret
- Saved in [backend/data/app-state.json](backend/data/app-state.json)

If you want to set these directly, edit the JSON file and restart the
containers.

## Location and privacy

- Location is requested only after a user action on the Monitoring dashboard.
- Coordinates live in browser state and localStorage only.
- Location is not persisted on the server.

To enable location, click **Enable Location** in the Monitoring header and
grant permission in the browser prompt.

## Distance computation

- Uses the Haversine formula with Earth radius 6371 km.
- Distances are rounded to 2 decimals.
- Typical accuracy is within about 0.5 km for short to medium ranges.

## Refresh logic

- Recomputes every `distanceUpdateIntervalSec` seconds.
- Also recomputes on GPS movement when auto-refresh is enabled.
- GPS updates are debounced by 5 seconds to avoid duplicate calls.

## Debug tools

Open the Monitoring page with the `debug` query flag to show debug overlays
and logs:

- Example: `https://localhost:8443/monitoring?debug=true`

The debug panel shows the latest distance map and the card overlay shows the
raw distance value used for rendering.

## Configuration

Key settings are managed in the web UI and persisted in backend state.

- Location mode: GPS or manual
- Distance units: km or mi
- Refresh intervals and GPS accuracy

## Troubleshooting

### No distances visible

- Confirm location permission is granted.
- Verify OpenSky callsigns or ICAO24 identifiers are valid.
- Check browser console for `DISTANCE API RAW` logs when debug is enabled.

### Geolocation not working on mobile

- Use HTTPS (https://localhost:8443).
- Ensure the browser permission prompt was accepted.

## Future providers

The backend distance engine is provider-agnostic. OpenSky can be replaced
with other ADS-B sources without changing distance logic.
