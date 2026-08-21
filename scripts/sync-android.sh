#!/usr/bin/env bash
# Sync the canonical HTML5 game (repo root) into the Android app's web assets.
# Run this after changing index.html / styles.css / js/* so the APK embeds the
# latest version. The GitHub Actions workflow also runs it before building.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WWW="$ROOT/android/app/src/main/assets/www"

mkdir -p "$WWW/js"

cp "$ROOT/index.html" "$WWW/index.html"
cp "$ROOT/styles.css" "$WWW/styles.css"
cp "$ROOT"/js/*.js "$WWW/js/"

echo "Synced game into $WWW"
