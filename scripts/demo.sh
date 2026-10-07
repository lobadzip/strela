#!/usr/bin/env bash
# One command for a demo on the local network: builds the web app and the server, then runs them.
# Phones on the same Wi-Fi open the printed address; the Android app already points at it.
set -euo pipefail
cd "$(dirname "$0")/.."

./gradlew --quiet :composeApp:wasmJsBrowserDistribution :server:installDist

ip=$(ipconfig getifaddr en0 2>/dev/null || hostname -I 2>/dev/null | awk '{print $1}' || true)
echo
echo "  Strela is starting"
echo "  This computer:       http://localhost:8080"
[ -n "${ip:-}" ] && echo "  Phones on the Wi-Fi: http://$ip:8080"
echo

exec ./server/build/install/server/bin/server
