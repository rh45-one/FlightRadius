# AGENTS.md

Project facts for agents working in this repo.

## Layout

- `web/` — Dockerized web app: React frontend + Express/TypeScript backend +
  nginx HTTPS proxy (`web/docker-compose.yml`).
- `web/backend/` — the backend all clients use; authoritative for OpenSky
  access and distance computation.
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
- `GET /api/health` triggers a **real OpenSky fetch** — treat it as a
  rate-limited call, not a cheap liveness probe.
- The backend serializes OpenSky `/states/all` fetches behind its own 5 s
  rate limiter with in-flight dedup; anonymous OpenSky access is additionally
  heavily rate-limited upstream. Never poll per-aircraft — batch via
  `POST /api/distance/compute` and `POST /api/distance/fleets`.

## Android

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
