#!/usr/bin/env bash
# Build the debug APK, install it on a USB-connected phone and launch it.
#
# Usage: android/scripts/run-on-device.sh [options]
#   --backend URL   Backend base URL baked into the build
#                   (default: http://<this machine's LAN IP>:3000/)
#   --usb           Tunnel the backend over the USB cable (adb reverse) and use
#                   http://127.0.0.1:3000/ — no Wi-Fi/firewall/LAN permission
#                   needed, but only works while the phone stays plugged in.
#   --serial ID     Target a specific device (default: the only one attached)
#   --grant         Pre-grant location/notification/nearby-devices permissions
#                   (skip the in-app permission prompts)
#   --reinstall     Uninstall first (wipes app data) — needed only if the
#                   installed copy was signed with a different key
#   --logs          Tail the app's logcat after launching
set -euo pipefail

PKG=com.flightradius.app
PORT=3000
ANDROID_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

backend="" usb=0 serial="${ANDROID_SERIAL:-}" grant=0 reinstall=0 logs=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --backend) backend="$2"; shift ;;
    --usb) usb=1 ;;
    --serial) serial="$2"; shift ;;
    --grant) grant=1 ;;
    --reinstall) reinstall=1 ;;
    --logs) logs=1 ;;
    -h|--help) awk 'NR>1 && /^#/ {sub(/^# ?/, ""); print; next} NR>1 {exit}' "$0"; exit 0 ;;
    *) echo "Unknown option: $1 (see --help)" >&2; exit 2 ;;
  esac
  shift
done

source "$(dirname "${BASH_SOURCE[0]}")/lib/device.sh"
command -v adb >/dev/null || die "adb not found (expected in $ANDROID_HOME/platform-tools)"

pick_device

# --- Backend URL ------------------------------------------------------------
if [[ $usb -eq 1 ]]; then
  "${ADB[@]}" reverse "tcp:$PORT" "tcp:$PORT" >/dev/null
  backend="http://127.0.0.1:$PORT/"
  echo "USB tunnel: phone 127.0.0.1:$PORT -> this machine :$PORT"
elif [[ -z "$backend" ]]; then
  ip="$(ip route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src") print $(i+1)}')"
  [[ -n "$ip" ]] || die "couldn't detect this machine's LAN IP; pass --backend URL or --usb"
  backend="http://$ip:$PORT/"
fi
echo "Backend URL: $backend"

# Cheap reachability check from this machine (/api/health would hit OpenSky).
check_url="$backend"; [[ $usb -eq 1 ]] && check_url="http://127.0.0.1:$PORT/"
if ! curl -fsS -m 3 "${check_url}api/settings/api" >/dev/null 2>&1; then
  echo "warning: backend not answering at ${check_url} — start it with:"
  echo "  docker compose -f web/docker-compose.yml up --build"
fi

# --- Build & install --------------------------------------------------------
echo "Building debug APK..."
(cd "$ANDROID_DIR" && ./gradlew -q :app:assembleDebug "-Pflightradius.backendBaseUrl=$backend")
apk="$ANDROID_DIR/app/build/outputs/apk/debug/app-debug.apk"

[[ $reinstall -eq 1 ]] && "${ADB[@]}" uninstall "$PKG" >/dev/null 2>&1 || true
echo "Installing..."
if ! out="$("${ADB[@]}" install -r "$apk" 2>&1)"; then
  grep -q INSTALL_FAILED_UPDATE_INCOMPATIBLE <<<"$out" &&
    die "installed copy has a different signature; rerun with --reinstall (wipes app data)"
  die "install failed: $out"
fi

if [[ $grant -eq 1 ]]; then
  perms=(ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION)
  (( sdk >= 33 )) && perms+=(POST_NOTIFICATIONS)
  (( sdk >= 37 )) && perms+=(ACCESS_LOCAL_NETWORK)
  for p in "${perms[@]}"; do
    "${ADB[@]}" shell pm grant "$PKG" "android.permission.$p" 2>/dev/null ||
      echo "  (could not grant $p)"
  done
  echo "Granted: ${perms[*]}"
fi

"${ADB[@]}" shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
echo "Launched FlightRadius on $model."
echo "Note: a backend URL saved earlier in Settings > Backend overrides the built-in one."

if [[ $logs -eq 1 ]]; then
  sleep 1
  pid="$("${ADB[@]}" shell pidof -s "$PKG" | tr -d '\r')"
  [[ -n "$pid" ]] || die "app is not running (crashed on start?) — check: adb -s $serial logcat -b crash"
  echo "--- logcat (Ctrl-C to stop) ---"
  exec "${ADB[@]}" logcat --pid="$pid"
fi
