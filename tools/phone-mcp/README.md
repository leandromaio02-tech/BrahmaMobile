# Brahma Phone MCP

Gives an MCP client control of the Android device over **adb**: screenshots, UI tree,
tap/swipe/type/key, app launch, package listing, plus a raw shell escape hatch.

Everything runs as the adb shell user — that is what makes `input tap` and
`screencap` legal. Calling those binaries from an unprivileged app uid fails
(`SecurityException: INJECT_EVENTS`, screenshot `Status: -1`), which is why the
server shells out through adb instead.

## Tool list

| Tool | Purpose |
|---|---|
| `phone_status` | device model, Android version, battery |
| `phone_connect` | `adb connect host:port` |
| `phone_capture` | screenshot -> PNG path (view with an image tool) |
| `phone_ui_dump` | visible texts + clickable elements with tap coordinates |
| `phone_tap` / `phone_swipe` | touch input |
| `phone_type` / `phone_key` | text entry, keycodes (BACK, HOME, APP_SWITCH, VOLUME_UP, ...) |
| `phone_launch` / `phone_list_packages` / `phone_current_app` | app level |
| `phone_shell` | arbitrary `adb shell` command |

## Setup

```sh
cd tools/phone-mcp
npm install

# pair the device once (see ../../tools/phone-connect.sh)
sh ../phone-connect.sh

# register with Hermes (the prompt asks to enable all 12 tools -> Y)
printf 'Y\n' | hermes mcp add phone \
  --command "$(command -v node)" \
  --env PHONE_ADB_BIN="$(command -v adb)" \
  --args "$PWD/server.mjs"

hermes mcp test phone
```

Then run `/reload-mcp` in Hermes (or start a new session) and the `phone_*`
tools appear.

## Notes

- Set `PHONE_ADB_SERIAL` if more than one device is attached.
- Reconnecting after a reboot/adbd restart is just `adb connect <host:port>`;
  no pairing code needed again.
- Server source of truth lives here; `~/.hermes/mcp-servers/phone/` is the
  installed copy used by Hermes.
