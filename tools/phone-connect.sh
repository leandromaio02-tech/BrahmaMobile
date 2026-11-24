#!/bin/sh
# Helpers for getting the Brahma Phone MCP server onto the device.
#
# Usage:
#   sh tools/phone-connect.sh                # full flow: pair, then connect
#   sh tools/phone-connect.sh --connect-only # just try adb connect (no pairing)
#
# Enable first: Settings > System > Developer options > Wireless debugging.
set -e

if ! command -v adb >/dev/null 2>&1; then
    echo "adb not found on PATH." >&2
    exit 1
fi

if [ "$1" = "--connect-only" ]; then
    printf "Connection host:port (Wireless debugging -> 'IP address & Port'): "
    read -r CONN
    adb connect "$CONN"
    adb devices
    exit 0
fi

echo "== Pair =="
echo "Tip: use split-screen (Recents -> app icon -> Split screen) so the pairing"
echo "sheet stays alive while you type into Termux."
printf "Pairing host:port (e.g. 127.0.0.1:41234): "
read -r PAIR
printf "Pairing code: "
read -r CODE
adb pair "$PAIR" "$CODE"

echo
echo "== Connect =="
printf "Connection host:port (from the Wireless debugging screen): "
read -r CONN
adb connect "$CONN"

echo
adb devices
echo
echo "Paired. In Hermes run /reload-mcp (or start a new session) to get the"
echo "phone_status / phone_capture / phone_tap ... tools."
