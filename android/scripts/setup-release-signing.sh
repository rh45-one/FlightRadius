#!/usr/bin/env bash
# Create the release signing key and (optionally) upload it as GitHub secrets.
#
# Usage: android/scripts/setup-release-signing.sh [options]
#   --github        Also upload (an existing key is uploaded as is, never regenerated) ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD,
#                   ANDROID_KEY_ALIAS and ANDROID_KEY_PASSWORD with `gh secret set`
#                   (needs an authenticated gh; values go over stdin, never argv)
#   --repo OWNER/NAME  Repository for --github (default: this checkout's repo)
#   --force         Replace an existing release.jks / keystore.properties. The old
#                   files are renamed to *.bak-<UTC timestamp>, never deleted
#                   (DANGEROUS anyway: apps signed with the old key can't be
#                   updated by the new one)
#
# Creates android/release.jks (RSA 4096, 10000 days, alias "flightradius") and
# android/keystore.properties (chmod 600); both are git-ignored. The generated
# password is never printed. Once keystore.properties exists, build-apk.sh and
# `./gradlew assembleRelease` sign with it automatically.
# Env: FR_SIGNING_DIR overrides the output directory (default: android/), for tests.
set -euo pipefail

ANDROID_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${FR_SIGNING_DIR:-$ANDROID_DIR}"
ALIAS=flightradius

github=0 force=0 repo=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --github) github=1 ;;
    --repo) repo="$2"; shift ;;
    --force) force=1 ;;
    -h|--help) awk 'NR>1 && /^#/ {sub(/^# ?/, ""); print; next} NR>1 {exit}' "$0"; exit 0 ;;
    *) echo "Unknown option: $1 (see --help)" >&2; exit 2 ;;
  esac
  shift
done

die() { echo "error: $*" >&2; exit 1; }
command -v keytool >/dev/null || die "keytool not found (install a JDK)"
command -v openssl >/dev/null || die "openssl not found"
if [[ $github -eq 1 ]]; then
  command -v gh >/dev/null || die "gh not found (needed for --github)"
  [[ -n "$repo" ]] || repo="$(gh repo view --json nameWithOwner -q .nameWithOwner)"
fi

jks="$OUT_DIR/release.jks"
props="$OUT_DIR/keystore.properties"
prop() { sed -n "s/^$1=//p" "$props" | head -n 1; }

export FR_STORE_PASS FR_KEY_PASS
if [[ $force -eq 0 && ( -e "$jks" || -e "$props" ) ]]; then
  # An existing key is never overwritten; with --github it is just uploaded.
  [[ $github -eq 1 && -f "$jks" && -f "$props" ]] ||
    die "$jks or $props already exists. Refusing to overwrite a signing key (use --github to upload the existing one, --force to replace it)."
  FR_STORE_PASS="$(prop storePassword)"
  FR_KEY_PASS="$(prop keyPassword)"
  ALIAS="$(prop keyAlias)"
  echo "Using the existing key in $OUT_DIR"
else
  mkdir -p "$OUT_DIR"
  # Never delete a signing key: with --force the old files are renamed, so the
  # key stays recoverable (losing it means installed apps can't be updated).
  stamp="$(date -u +%Y%m%dT%H%M%SZ)"
  for f in "$jks" "$props"; do
    if [[ -e "$f" ]]; then
      mv "$f" "$f.bak-$stamp"
      echo "Moved existing $f to $f.bak-$stamp"
    fi
  done
  # Nothing created below may ever be group/world readable, not even briefly.
  umask 077
  FR_STORE_PASS="$(openssl rand -base64 32)"
  FR_KEY_PASS="$FR_STORE_PASS"

  keytool -genkeypair -v -keystore "$jks" -alias "$ALIAS" -keyalg RSA -keysize 4096 \
    -validity 10000 -dname "CN=FlightRadius" \
    -storepass:env FR_STORE_PASS -keypass:env FR_KEY_PASS >/dev/null 2>&1 ||
    die "keytool failed to generate the key"

  {
    echo "storeFile=release.jks"
    echo "storePassword=$FR_STORE_PASS"
    echo "keyAlias=$ALIAS"
    echo "keyPassword=$FR_KEY_PASS"
  } > "$props"
  chmod 600 "$props" "$jks"
  echo "Created $jks and $props"
fi

if [[ -z "${FR_SIGNING_DIR:-}" ]]; then
  for f in "$jks" "$props"; do
    git -C "$ANDROID_DIR" check-ignore -q "$f" || die "$f is NOT git-ignored — fix .gitignore before committing anything"
  done
  echo "Both files are git-ignored."
fi

if [[ $github -eq 1 ]]; then
  base64 -w0 "$jks" | gh secret set ANDROID_KEYSTORE_BASE64 --repo "$repo"
  printf '%s' "$FR_STORE_PASS" | gh secret set ANDROID_KEYSTORE_PASSWORD --repo "$repo"
  printf '%s' "$ALIAS" | gh secret set ANDROID_KEY_ALIAS --repo "$repo"
  printf '%s' "$FR_KEY_PASS" | gh secret set ANDROID_KEY_PASSWORD --repo "$repo"
  echo "Uploaded the 4 signing secrets to $repo."
else
  echo "Not uploaded. For GitHub releases rerun with --github (uploads this existing key)."
fi

cat <<'MSG'

!! BACK UP android/release.jks and android/keystore.properties somewhere safe
!! (password manager). If you lose them, installed apps can never be updated.

Phones with the current build (debug key) need one uninstall before installing a
release-signed APK.
MSG
