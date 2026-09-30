#!/usr/bin/env bash
# Shared device-picking helper, sourced by run-on-device.sh and build-apk.sh.
#
# Expects the caller to have set: serial (may be empty), ANDROID_HOME on PATH.
# pick_device sets: serial, ADB (array), sdk, model. Emulators are skipped.

die() { echo "error: $*" >&2; exit 1; }

pick_device() {
  if [[ -z "$serial" ]]; then
    mapfile -t lines < <(adb devices | tail -n +2 | grep -v '^emulator-' | awk 'NF')
    [[ ${#lines[@]} -eq 0 ]] && die "no phone detected. On the phone: Settings > About phone >
  tap 'Build number' 7x, then Developer options > enable 'USB debugging'.
  Plug it in and accept the 'Allow USB debugging?' prompt."
    for l in "${lines[@]}"; do
      case "$(awk '{print $2}' <<<"$l")" in
        unauthorized) die "phone is unauthorized — unlock it and accept the 'Allow USB debugging?' prompt, then rerun." ;;
        no) die "adb has no USB permission for the phone. Fix once with:
  sudo apt install android-sdk-platform-tools-common && adb kill-server
  then unplug/replug the phone." ;;
      esac
    done
    [[ ${#lines[@]} -gt 1 ]] && die "several devices attached; pick one with --serial:
$(printf '  %s\n' "${lines[@]}")"
    serial="$(awk '{print $1}' <<<"${lines[0]}")"
  fi
  ADB=(adb -s "$serial")
  "${ADB[@]}" get-state >/dev/null 2>&1 || die "device $serial is not ready"

  sdk="$("${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
  model="$("${ADB[@]}" shell getprop ro.product.model | tr -d '\r')"
  (( sdk >= 26 )) || die "$model runs API $sdk; FlightRadius needs Android 8.0 (API 26)+"
  echo "Device: $model ($serial), API $sdk"
}
