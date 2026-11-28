# J.A.R.V.I.S. — native Android AI assistant

Kotlin + Jetpack Compose + Material 3. This is a real Android application, not a WebView.

## Build

```sh
cd jarvis
gradle assembleDebug --no-daemon
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions builds the same project from `jarvis/` on pushes to the `jarvis` branch and publishes `JARVIS-debug.apk` on the rolling `jarvis-latest` release.

## Configure an AI provider

Open **Model center** and add an OpenAI-compatible provider:

- Base URL, e.g. `https://api.openai.com/v1`, `http://192.168.1.10:11434/v1`, or any compatible gateway.
- API key (stored in Android Keystore-backed EncryptedSharedPreferences; never logged).
- Model ID, e.g. `gpt-4o-mini`, `deepseek-chat`, or a local model name.
- Routing role: `fast`, `reasoning`, `coding`, `vision`, `speech`.
- Streaming, vision and tool capabilities are explicit; the app does not assume them.

Use **Test** and **Discover** to verify the endpoint. The router picks a provider by task role and falls back to another healthy provider.

## Permissions and honesty

- Microphone and notifications are requested at runtime.
- Screen capture starts only through the Android MediaProjection consent dialog and shows a persistent notification.
- Accessibility interaction is off until you enable it in Android settings; consequential actions show a preview and require confirmation unless you explicitly enable auto-confirm.
- Wake word (“Hey Jarvis”) uses a foreground service and is transparent; Android restrictions on permanent background listening still apply.
- Shizuku is optional and detected by installed package and API availability; root is never required.
- Only files you pick through the Storage Access Framework are read. The app never scans the device.

## Modules by package

`core` models, `data` Room/DataStore, `ai` provider router and agent, `voice` speech, `services` foreground/accessibility/projection/work, `ui` Compose screens.

## Limitations

- Local streaming transcription/video extraction depend on a configured compatible model.
- Protected content that Android blocks from capture cannot be analysed.
- Release signing requires your own keystore; the CI produces an installable debug APK.
