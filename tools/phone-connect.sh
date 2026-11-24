#!/bin/sh
# One-time pairing helper for the Brahma Phone MCP server.
#
# On the phone: Settings > System > Developer options > Wireless debugging.
#   1. "Pair device with pairing code"  -> host:port + 6-digit code
#   2. Main Wireless debugging screen  -> "IP address & Port" (different port)
#
# Then in Termux run this script. It never prints the code anywhere else.
set -e

if ! command -v adb >/dev/null 2>&1; then
    echo "adb not found on PATH." >&2
    exit 1
fi

echo "== Pair =="
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
