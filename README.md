# Brahma Mobile

[![build](https://img.shields.io/github/actions/workflow/status/leandromaio02-tech/BrahmaMobile/build.yml?branch=main)](https://github.com/leandromaio02-tech/BrahmaMobile/actions)
[![license](https://img.shields.io/badge/license-MIT-6C5CE7)](LICENSE)
[![platform](https://img.shields.io/badge/Android-12%2B%20%C2%B7%20arm64--v8a-6C5CE7)](https://developer.android.com/ndk/guides/abis)

An embodied 3D AI companion for Android — the phone-sized version of the Brahma Sol idea.
Kotlin + Jetpack Compose, `minSdk 26`, arm64 only, low-RAM friendly, and **no PC required**:
every push builds a signed debug APK in GitHub Actions.

**[📲 One-tap install (always the newest APK)](https://github.com/leandromaio02-tech/BrahmaMobile/releases/download/latest/BrahmaMobile-debug.apk)** ·
**[▶ Try the avatar in your browser](https://leandromaio02-tech.github.io/BrahmaMobile/)** ·
**[⬇ Build history](https://github.com/leandromaio02-tech/BrahmaMobile/actions)** ·
**[💬 Discussions](https://github.com/leandromaio02-tech/BrahmaMobile/discussions)** ·
**[🛣 Roadmap](ROADMAP.md)** ·
**[🤝 Contributing](CONTRIBUTING.md)**

The playground is the same Three.js scene that ships inside the app — expressions,
idle animation, blinking, eye tracking, speech synthesis, drag-to-spin, and a working
simulation of the reflex command layer. No install needed.

> First time? Enable it once in the repo: **Settings → Pages → Build and deployment →
> Source: "Deploy from a branch" → Branch `main`, folder `/docs` → Save.** The page then
> lives at the link above.

## What actually works

| Feature | Implementation | Notes |
|---|---|---|
| Floating 3D avatar | Three.js scene in a WebView, hosted by a foreground service overlay | Draggable via handle, tap to listen, idle animation, blinking, eye drift, 5 expressions. `three.min.js` is vendored in assets, so the avatar works offline. |
| Offline brain | `dev.ffmpegkit-maintained:llama-android:0.1.1` (prebuilt llama.cpp `b9878`, CPU/NEON, arm64) | Real GGUF inference. You supply the model (see below). Without one it falls back to a deterministic offline rules brain. |
| Cloud fallback | Gemini 1.5 Flash / OpenRouter via OkHttp | Only used when the toggle is on and a key is set. |
| Voice | Offline-preferred Android SpeechRecognizer (`EXTRA_PREFER_OFFLINE`), wake word on partial results, amplitude-based barge-in, Android TTS | On-device STT availability depends on the ROM/GApps. Barge-in uses mic RMS — no model. |
| Instant reflexes | Regex → Android Intents / system services, no coroutines or network | Apps, YouTube, volume, brightness, torch, alarms/timers, calls, SMS, battery, time, Wi-Fi/BT, web search, files, Termux, UI clicks. |
| Agent tools | `ToolRegistry` | File read/list/write, reminders via AlarmManager, DuckDuckGo instant answers, Termux `RUN_COMMAND`, Accessibility screen read/click. |
| Daily briefing | WorkManager + NASA APOD (DEMO_KEY) | Sets the wallpaper and stores a short briefing, refreshed daily. |

**Honest limits**
- Kokoro ONNX is **not** bundled. Android TTS is the working path; `TtsEngine` is the single swap point if you want Kokoro later.
- The wake word leans on the platform's on-device recognizer with `EXTRA_PREFER_OFFLINE`. ROMs without one fall back to network STT, or use the MIC button.
- Debug-signed APK: sideload only, not Play Store.
- llama.cpp needs a GGUF (400 MB–2 GB); it is not shipped in the APK.

## Build

Every push runs `.github/workflows/build.yml`: JDK 17, Android SDK platform 34 /
build-tools 34.0.0, Gradle 8.7 downloaded directly (no wrapper jar), then
`gradle assembleDebug --no-daemon --stacktrace`. The artifact **`brahma-mobile-debug`**
is the zip containing `app-debug.apk`.

<details>
<summary>Run it yourself in Termux / PRoot (phone-only)</summary>

```sh
cd /root/BrahmaMobile
pkg update && pkg install -y git
./push.sh https://github.com/YOUR_USERNAME/BrahmaMobile.git
```

Set `GH_TOKEN=<pat>` in the same command to skip the credential prompt. Never paste a
token into chat.
</details>

## Install the APK

**One tap, always current:**

```
https://github.com/leandromaio02-tech/BrahmaMobile/releases/download/latest/BrahmaMobile-debug.apk
```

Open that on your phone and install it. Every push rebuilds the app and replaces this
exact asset, so the link never goes stale — bookmark it or add it to your home screen.

Manual route (same file): [Actions tab](https://github.com/leandromaio02-tech/BrahmaMobile/actions)
→ latest green **Build Brahma Mobile APK** run → `brahma-mobile-debug` artifact → unzip.

Then:

1. Open the APK; allow installs from unknown sources for your browser/file manager.
2. Grant permissions from the in-app buttons: **Microphone**, **Draw over other apps**,
   **Notifications**, **Brahma Reflex (Accessibility)**. Brightness also needs
   **Modify system settings**; reminders need **Alarms & reminders** on Android 12+.
3. Disable battery optimization; on MIUI/ColorOS/OneUI also allow autostart, floating
   windows, and "lock in recents".
4. Tap **SHOW**, drag the bubble by the "DRAG ME" bar.

## Enable the real offline brain

1. Download a Q4_K_M GGUF on the phone, e.g.
   [`qwen2.5-1.5b-instruct-q4_k_m.gguf`](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF)
   (~1 GB) or the 3B version (~2 GB).
2. In the app: **Brain → IMPORT GGUF FROM PHONE**, pick the file. It copies to
   `Android/data/com.brahma.mobile/files/models/` and loads with 2048 context, CPU only,
   2–4 threads.
3. Header should read `On-device · llama.cpp`. On 4–6 GB phones use 0.5B/1.5B;
   3B wants roughly 2.5–3.5 GB free RAM.

Cloud fallback: toggle **Cloud fallback**, pick Gemini or OpenRouter, paste a key.
Keys live only in SharedPreferences; local inference is always preferred when a model
is loaded and the cloud toggle is off.

## Reflex command examples

```
open WhatsApp          play lo-fi beats on YouTube
volume 40              volume up / mute
brightness 60          brightness down
flashlight on          flashlight off
alarm in 10 minutes    alarm at 07:30
call mom               text mom see you soon
search android 15      remind me to stretch in 5 minutes
read file notes.txt    list files
run uptime in termux   click Send
what's on screen       what time is it
```

## Project layout

```
.github/workflows/build.yml      CI: build + upload APK
.github/ISSUE_TEMPLATE/          bug report + feature request templates
docs/index.html                  interactive browser playground (GitHub Pages / jsDelivr)
app/src/main/assets/avatar/      Three.js avatar (index.html + vendored three.min.js)
app/src/main/java/com/brahma/mobile/
  overlay/                       floating bubble service
  brain/                         llama.cpp engine, cloud engine, rules fallback
  voice/                         wake word, STT, barge-in, TTS
  reflex/                        <50 ms regex commands + accessibility service
  tools/                         file / reminder / web / Termux / UI tools
  ui/                            Compose settings screen
  wallpaper/                     NASA APOD fetch + daily worker
push.sh                          phone-friendly git push helper
```

## Credits

App code written from scratch. Third parties: [Three.js](https://threejs.org) (MIT),
[llama.cpp](https://github.com/ggml-org/llama.cpp) via the
[llama-android](https://github.com/ffmpegkit-maintained/llama-android) AAR (MIT),
OkHttp and AndroidX (Apache-2.0). NASA APOD imagery is public-domain courtesy of NASA.

Licensed under [MIT](LICENSE). Contributions welcome — see [CONTRIBUTING.md](CONTRIBUTING.md).
