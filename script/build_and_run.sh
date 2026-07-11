#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-run}"
APP_NAME="OmniFlow"
BUNDLE_ID="com.omniflow.macos"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DERIVED_DATA="$ROOT_DIR/build/apple-derived-data"
APP_BUNDLE="$DERIVED_DATA/Build/Products/Debug/OmniFlow.app"
APP_BINARY="$APP_BUNDLE/Contents/MacOS/OmniFlow"

pkill -x "$APP_NAME" >/dev/null 2>&1 || true

"$ROOT_DIR/gradlew" -p "$ROOT_DIR" :shared:linkDebugFrameworkMacosX64
xcodebuild \
  -project "$ROOT_DIR/appleApp/OmniFlow.xcodeproj" \
  -scheme "OmniFlow-macOS" \
  -configuration Debug \
  -derivedDataPath "$DERIVED_DATA" \
  build

open_app() {
  /usr/bin/open -n "$APP_BUNDLE"
}

case "$MODE" in
  run)
    open_app
    ;;
  --debug|debug)
    lldb -- "$APP_BINARY"
    ;;
  --logs|logs)
    open_app
    /usr/bin/log stream --info --style compact --predicate "process == \"$APP_NAME\""
    ;;
  --telemetry|telemetry)
    open_app
    /usr/bin/log stream --info --style compact --predicate "subsystem == \"$BUNDLE_ID\""
    ;;
  --verify|verify)
    open_app
    sleep 1
    pgrep -x "$APP_NAME" >/dev/null
    ;;
  *)
    echo "usage: $0 [run|--debug|--logs|--telemetry|--verify]" >&2
    exit 2
    ;;
esac
