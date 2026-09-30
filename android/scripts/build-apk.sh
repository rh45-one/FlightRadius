#!/usr/bin/env bash
# Build an installable FlightRadius APK and optionally install it on a phone.
#
# Usage: android/scripts/build-apk.sh [options]
#   (default)       Release build (R8-shrunk), arm64-v8a only, into android/dist/.
#                   Without android/keystore.properties it is signed with the
#                   debug key: fine for personal installs, NOT for the Play Store.
#   --debug         Build the debug variant instead of release
#   --abi LIST      Comma-separated ABIs to include (default: arm64-v8a, which
#                   covers any phone of the last ~8 years)
#   --universal     Include all ABIs (bigger: the map library ships 4 native builds)
#   --install       Install on the USB-connected phone (adb install -r; a real,
#                   permanent install that survives reboots and unplugging)
#   --serial ID     Target a specific device (default: the only phone attached)
#   --launch        With --install: start the app afterwards
#   --grant         With --install: pre-grant location/notification/nearby-devices
#                   permissions
#   --reinstall     With --install: uninstall first (wipes app data) — needed only
#                   if the installed copy was signed with a different key
#
# No backend URL is baked in: the app defaults to direct OpenSky mode, and
# release builds only allow HTTPS backends (set one in Settings if you use it).
# Output: android/dist/FlightRadius-<version>-<release|debug>-<abi|universal>.apk
# You can also copy that file to the phone and open it (allow "install unknown apps").
set -euo pipefail

PKG=com.flightradius.app
ANDROID_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

variant=release abis="arm64-v8a" universal=0 install=0 launch=0 grant=0 reinstall=0
serial="${ANDROID_SERIAL:-}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --debug) variant=debug ;;
    --abi) abis="$2"; shift ;;
    --universal) universal=1 ;;
    --install) install=1 ;;
    --serial) serial="$2"; shift ;;
    --launch) launch=1 ;;
    --grant) grant=1 ;;
    --reinstall) reinstall=1 ;;
    -h|--help) awk 'NR>1 && /^#/ {sub(/^# ?/, ""); print; next} NR>1 {exit}' "$0"; exit 0 ;;
    *) echo "Unknown option: $1 (see --help)" >&2; exit 2 ;;
  esac
  shift
done

source "$(dirname "${BASH_SOURCE[0]}")/lib/device.sh"
[[ $install -eq 0 && ( $launch -eq 1 || $grant -eq 1 || $reinstall -eq 1 ) ]] &&
  die "--launch/--grant/--reinstall only make sense with --install"
abis="${abis// /}"
[[ $universal -eq 1 ]] && abis=""

# Pick (and validate) the phone before the slow build so mistakes fail fast.
if [[ $install -eq 1 ]]; then
  command -v adb >/dev/null || die "adb not found (expected in $ANDROID_HOME/platform-tools)"
  pick_device
  if [[ -n "$abis" ]]; then
    device_abis="$("${ADB[@]}" shell getprop ro.product.cpu.abilist | tr -d '\r')"
    ok=0
    for a in ${abis//,/ }; do
      [[ ",$device_abis," == *",$a,"* ]] && ok=1
    done
    [[ $ok -eq 1 ]] || die "$model supports ABIs [$device_abis] but the build only contains [$abis].
  Rebuild with --abi <one of those> or --universal."
  fi
fi

# --- Build -------------------------------------------------------------------
if [[ $variant == release ]]; then
  task=:app:assembleRelease
  if [[ -f "$ANDROID_DIR/keystore.properties" ]]; then
    echo "Signing: release key (keystore.properties)."
  else
    echo "Signing: debug key (no keystore.properties) — fine for personal installs, not for the Play Store."
  fi
else
  task=:app:assembleDebug
fi
gradle_args=(-q "$task")
[[ -n "$abis" ]] && gradle_args+=("-Pflightradius.abis=$abis")
echo "Building $variant APK (${abis:-all ABIs})..."
(cd "$ANDROID_DIR" && ./gradlew "${gradle_args[@]}")

built="$ANDROID_DIR/app/build/outputs/apk/$variant/app-$variant.apk"
[[ -f "$built" ]] || die "expected APK not found: $built"

aapt2="$(ls -d "$ANDROID_HOME"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -n 1)"
[[ -x "$aapt2" ]] || die "aapt2 not found under $ANDROID_HOME/build-tools"
version="$("$aapt2" dump badging "$built" | sed -n "s/^package:.* versionName='\([^']*\)'.*/\1/p" | head -n 1)"
[[ -n "$version" ]] || die "could not read versionName from $built"

abi_label="universal"
[[ -n "$abis" ]] && abi_label="${abis//,/+}"
mkdir -p "$ANDROID_DIR/dist"
apk="$ANDROID_DIR/dist/FlightRadius-$version-$variant-$abi_label.apk"
cp "$built" "$apk"
echo "APK: $apk ($(du -h "$apk" | cut -f1))"

# --- Install -----------------------------------------------------------------
[[ $install -eq 1 ]] || exit 0

[[ $reinstall -eq 1 ]] && "${ADB[@]}" uninstall "$PKG" >/dev/null 2>&1 || true
echo "Installing..."
if ! out="$("${ADB[@]}" install -r "$apk" 2>&1)"; then
  grep -q INSTALL_FAILED_UPDATE_INCOMPATIBLE <<<"$out" &&
    die "the installed copy was signed with a different key (e.g. a debug build vs this one).
  Rerun with --reinstall to uninstall it first (this wipes the app's data)."
  die "install failed: $out"
fi
echo "Installed FlightRadius $version on $model."

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

if [[ $launch -eq 1 ]]; then
  "${ADB[@]}" shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
  echo "Launched FlightRadius."
fi
