#!/usr/bin/env bash
# Start the local Android emulator (if needed), build + install the debug APK
# and launch it — for quick GUI checks on this machine.
#
# Usage: android/scripts/run-on-emulator.sh [options]
#   --avd NAME        AVD to boot (default: flightradius_api37)
#   --headless        Boot without a window (CI-style; default shows a window)
#   --cold            Cold boot (ignore the saved quick-boot snapshot)
#   --dark | --light  Set the emulator's system appearance before launching
#   --font-scale N    System font scale, e.g. 1.3 or 2.0 (1.0 resets)
#   --location LAT,LON  Fake GPS fix, e.g. --location 40.4168,-3.7038
#   --backend URL     Backend base URL baked into the build
#                     (default: the build default, http://10.0.2.2:3000/)
#   --grant           Pre-grant location/notification/local-network permissions
#   --reinstall       Uninstall first (wipes app data)
#   --no-build        Skip the Gradle build; install the existing debug APK
#   --logs            Tail the app's logcat after launching
#   --stop            Shut down the running emulator and exit
set -euo pipefail

PKG=com.flightradius.app
ANDROID_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

avd=flightradius_api37 headless=0 cold=0 night="" font="" location="" backend=""
grant=0 reinstall=0 build=1 logs=0 stop=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --avd) avd="$2"; shift ;;
    --headless) headless=1 ;;
    --cold) cold=1 ;;
    --dark) night=yes ;;
    --light) night=no ;;
    --font-scale) font="$2"; shift ;;
    --location) location="$2"; shift ;;
    --backend) backend="$2"; shift ;;
    --grant) grant=1 ;;
    --reinstall) reinstall=1 ;;
    --no-build) build=0 ;;
    --logs) logs=1 ;;
    --stop) stop=1 ;;
    -h|--help) awk 'NR>1 && /^#/ {sub(/^# ?/, ""); print; next} NR>1 {exit}' "$0"; exit 0 ;;
    *) echo "Unknown option: $1 (see --help)" >&2; exit 2 ;;
  esac
  shift
done

die() { echo "error: $*" >&2; exit 1; }
command -v adb >/dev/null || die "adb not found (expected in $ANDROID_HOME/platform-tools)"
command -v emulator >/dev/null || die "emulator not found (expected in $ANDROID_HOME/emulator)"

running_emulator() { adb devices | awk '/^emulator-[0-9]+\tdevice/ {print $1; exit}'; }

if [[ $stop -eq 1 ]]; then
  serial="$(running_emulator)"
  [[ -n "$serial" ]] || { echo "No emulator running."; exit 0; }
  adb -s "$serial" emu kill >/dev/null
  for _ in $(seq 1 30); do
    adb devices | grep -q "^$serial[[:space:]]" || break; sleep 1
  done
  echo "Stopped $serial."
  exit 0
fi

# --- Boot the emulator ------------------------------------------------------
serial="$(running_emulator)"
if [[ -z "$serial" ]]; then
  emulator -list-avds | grep -qx "$avd" ||
    die "AVD '$avd' not found. Available: $(emulator -list-avds | tr '\n' ' ')"
  [[ -r /dev/kvm && -w /dev/kvm ]] ||
    echo "warning: no access to /dev/kvm — the emulator will be very slow (add yourself to the 'kvm' group)."
  args=(-avd "$avd" -no-audio -no-boot-anim)
  [[ $headless -eq 1 ]] && args+=(-no-window -gpu swiftshader_indirect)
  [[ $cold -eq 1 ]] && args+=(-no-snapshot-load)
  log="${TMPDIR:-/tmp}/flightradius-emulator.log"
  echo "Starting emulator '$avd' (log: $log)..."
  setsid nohup emulator "${args[@]}" >"$log" 2>&1 &
  for _ in $(seq 1 60); do
    serial="$(running_emulator)"; [[ -n "$serial" ]] && break; sleep 2
  done
  [[ -n "$serial" ]] || die "emulator did not come up; see $log"
fi
ADB=(adb -s "$serial")

echo -n "Waiting for $serial to finish booting"
for _ in $(seq 1 120); do
  [[ "$("${ADB[@]}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]] && break
  echo -n "."; sleep 2
done
echo
[[ "$("${ADB[@]}" shell getprop sys.boot_completed | tr -d '\r')" == 1 ]] || die "boot timed out"
sdk="$("${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
echo "Emulator: $serial, API $sdk"

# --- Device tweaks for GUI checks -------------------------------------------
[[ -n "$night" ]] && "${ADB[@]}" shell cmd uimode night "$night" >/dev/null && echo "Appearance: night=$night"
[[ -n "$font" ]] && "${ADB[@]}" shell settings put system font_scale "$font" && echo "Font scale: $font"
if [[ -n "$location" ]]; then
  IFS=, read -r lat lon <<<"$location"
  [[ -n "$lat" && -n "$lon" ]] || die "--location expects LAT,LON"
  "${ADB[@]}" emu geo fix "$lon" "$lat" >/dev/null && echo "GPS fix: $lat, $lon"
fi

# --- Build & install --------------------------------------------------------
apk="$ANDROID_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [[ $build -eq 1 ]]; then
  echo "Building debug APK..."
  gradle_args=(-q :app:assembleDebug)
  [[ -n "$backend" ]] && gradle_args+=("-Pflightradius.backendBaseUrl=$backend")
  (cd "$ANDROID_DIR" && ./gradlew "${gradle_args[@]}")
fi
[[ -f "$apk" ]] || die "no APK at $apk (run without --no-build)"

[[ $reinstall -eq 1 ]] && "${ADB[@]}" uninstall "$PKG" >/dev/null 2>&1 || true
echo "Installing..."
out="$("${ADB[@]}" install -r "$apk" 2>&1)" || die "install failed: $out"

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

"${ADB[@]}" shell am force-stop "$PKG"
"${ADB[@]}" shell am start -n "$PKG/.MainActivity" >/dev/null
echo "Launched FlightRadius on $serial. Stop the emulator with: $0 --stop"

if [[ $logs -eq 1 ]]; then
  sleep 1
  pid="$("${ADB[@]}" shell pidof -s "$PKG" | tr -d '\r')"
  [[ -n "$pid" ]] || die "app is not running (crashed on start?) — check: adb -s $serial logcat -b crash"
  echo "--- logcat (Ctrl-C to stop) ---"
  exec "${ADB[@]}" logcat --pid="$pid"
fi
