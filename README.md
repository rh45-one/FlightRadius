# FlightRadius

FlightRadius is a Dockerized full-stack aircraft tracking dashboard. It uses
live OpenSky positions for telemetry and distance calculations.

## Location and Privacy

- Location is requested only after a user tap on the Monitoring dashboard.
- Coordinates live in browser state and localStorage only.
- Location is not persisted on the server.

To enable location, tap **Enable Location** in the Monitoring header and grant
permission in the browser prompt.

## OpenSky Data

Distance calculations use OpenSky aircraft positions. Callsigns are matched
against the live OpenSky state vectors and may return no data if a callsign
is not currently visible.

## Distance Computation

- Uses the Haversine formula with Earth radius 6371 km.
- Distances are rounded to 2 decimals.
- Expect typical accuracy within about 0.5 km for short to medium ranges.
- OpenSky data is real-time and may be delayed or rate limited.

## Refresh Logic

- Recomputes every `distanceUpdateIntervalSec` seconds.
- Also recomputes on GPS change when auto-refresh on movement is enabled.
- Rapid GPS updates are debounced by 5 seconds to avoid duplicate calls.

## Mobile Instructions

- Use HTTPS (the Docker setup exposes 8443) because mobile browsers require it
  for geolocation.
- Tap **Enable Location** from the dashboard; the browser prompt only appears
	on user interaction.

## Docker

```bash
docker compose up --build
```

- Frontend: https://localhost:8443
- Backend: http://localhost:3000

## Future Providers

The backend distance engine is provider-agnostic. OpenSky can be replaced
with FlightRadar or ADS-B Exchange without changing distance logic.