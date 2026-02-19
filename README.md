# FlightRadius

FlightRadius is a Dockerized full-stack aircraft tracking dashboard. It uses
mock aircraft positions while the OpenSky integration remains a swap-in
provider for later.

## Location and Privacy

- Location is requested only after a user tap on the Monitoring dashboard.
- Coordinates live in browser state and localStorage only.
- Location is not persisted on the server.

To enable location, tap **Enable Location** in the Monitoring header and grant
permission in the browser prompt.

## Mock Aircraft Data

Mock positions live at [backend/src/mock/aircraft_positions.json](backend/src/mock/aircraft_positions.json).
The backend distance engine computes distances from this dataset for callsigns
and fleet groups.

You can test distance results by adding tracked callsigns (or bulk-importing
fleet groups) that match entries in the mock dataset.

## Distance Computation

- Uses the Haversine formula with Earth radius 6371 km.
- Distances are rounded to 2 decimals.
- Expect typical accuracy within about 0.5 km for short to medium ranges.
- Mock data is static and does not reflect real aircraft movement.

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

The backend distance engine is provider-agnostic. Mock data can be replaced
with OpenSky, FlightRadar, or ADS-B Exchange without changing distance logic.