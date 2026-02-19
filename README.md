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