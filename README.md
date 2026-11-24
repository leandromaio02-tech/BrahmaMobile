# Brahma Mobile

An embodied 3D AI companion for Android — a phone-sized version of the Brahma Sol idea.
Kotlin + Jetpack Compose, `minSdk 26`, `arm64-v8a` only, no local PC required.

## What actually works

| Feature | Implementation | Notes |
|---|---|---|
| Floating 3D avatar | Three.js scene in a WebView, hosted by a foreground service overlay | Draggable via handle, tap to listen, idle animation, blinking, eye drift, 5 expressions. `three.min.js` is vendored in assets, so the avatar works offline. |
| Offline brain | `dev.ffmpegkit-maintained:llama-android:0.1.1` (prebuilt llama.cpp `b9878`, CPU/NEON, arm64) | Real GGUF inference. You supply the model (see below). Without a model it falls back to a deterministic offline rules brain. |
| Cloud fallback | Gemini 1.5 Flash / OpenRouter via OkHttp | Only used when the toggle is on and a key is set. |
| Voice | Offline-preferred Android SpeechRecognizer (`EXTRA_PREFER_OFFLINE`), wake word on partial results, amplitude-based barge-in, Android TTS | On-device STT availability depends on the ROM/GApps. Barge-in uses mic RMS, no model. |
| Instant reflexes | Regex -> Android Intents / system services, no coroutines or network | Apps, YouTube, volume, brightness, torch, alarms/timers, calls, SMS, battery, time, Wi-Fi/BT, web search, files, Termux, UI clicks. |
| Agent tools | `ToolRegistry` | File read/list/write, reminders via AlarmManager, DuckDuckGo instant answers, Termux `RUN_COMMAND`, Accessibility-based screen read/click. |
| Daily briefing | WorkManager + NASA APOD (DEMO_KEY) | Sets wallpaper and stores a short briefing, refreshed once a day. |

**Honest limits**
- Kokoro ONNX is **not** bundled. The working speech path is Android TTS (neural on most modern ROMs); `TtsEngine` is the single swap point if you want to add Kokoro later.
- The wake word uses the platform's on-device recognizer with `EXTRA_PREFER_OFFLINE`. Some ROMs have no offline recognizer; in that case it falls back to network STT or you can use the MIC button.
- The debug APK is signed with Android's standard debug key — installable by sideload, not for Play Store.
- llama.cpp needs a GGUF file (400 MB–2 GB). It is not shipped in the APK.

## Build

Every push to any branch runs `.github/workflows/build.yml`:

- JDK 17 (Temurin), Android SDK platform 34 / build-tools 34.0.0, Gradle 8.7 downloaded directly (no wrapper jar needed on a phone)
- `gradle assembleDebug --no-daemon --stacktrace`
- artifact: **`brahma-mobile-debug`** → `app-debug.apk`

## Phone-only: push the project and get the APK

### 1. Create an empty GitHub repo
In a browser: <https://github.com/new> — name it `BrahmaMobile`, leave it empty (no README, no license).

### 2. If Hermes runs on your phone (Termux/PRoot)
The project already exists at `/root/BrahmaMobile`. In Termux:

```sh
cd /root/BrahmaMobile
pkg update && pkg install -y git
./push.sh https://github.com/YOUR_USERNAME/BrahmaMobile.git
```

If you prefer a token instead of a credential prompt:

```sh
GH_TOKEN=github_pat_xxx ./push.sh https://github.com/YOUR_USERNAME/BrahmaMobile.git
```

Create the PAT at GitHub → Settings → Developer settings → Personal access tokens (scope: `repo`). Do not paste it into chat.

### 3. If the files are not already on the phone
Every file is printed in the chat response. Put them in one folder on the phone
(a code editor / Termux `nano` / GitHub web "Add file" flow), then run the same
`cd <folder> && ./push.sh <repo-url>`.

### 4. Watch the build
GitHub → your repo → **Actions** tab → latest "Build Brahma Mobile APK" run.
When it is green, open the run, scroll to **Artifacts**, and download
`brahma-mobile-debug`. It is a `.zip`; unzip it with any file manager to get
`app-debug.apk`. On the phone itself you can open the artifact link in the
GitHub app/browser and unzip with Termux or ZArchiver.

### 5. Install
Open the APK. Android will ask to allow installs from unknown sources for your
file manager/browser — allow it.

## First-run setup on the phone

Grant, from the Brahma Mobile settings screen:
1. **Microphone** — critical.
2. **Draw over other apps** — critical for the floating bubble.
3. **Notifications** — needed for the foreground service and reminders (Android 13+).
4. **Brahma Reflex (Accessibility)** — needed for UI click/screen-read tools.
5. **Modify system settings** — only needed for brightness control.
6. On Android 12+ also allow **Alarms & reminders** (exact alarm access), and disable **battery optimization** for Brahma. On MIUI/ColorOS/OneUI also allow autostart, floating windows, and "lock in recents".
7. For Termux reflexes: in Termux → Settings → enable **Allow external apps** (the app sends `com.termux.RUN_COMMAND`).

Tap **SHOW** in the app to launch the bubble. Drag it by the "DRAG ME" bar.

## Enable the real offline brain

1. On the phone, download a Q4_K_M GGUF, for example
   `qwen2.5-1.5b-instruct-q4_k_m.gguf` (≈1 GB) or the 3B version (≈2 GB).
   Source: <https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF>
2. In Brahma Mobile → Brain → **IMPORT GGUF FROM PHONE** and pick the file.
   It is copied to `Android/data/com.brahma.mobile/files/models/` and loaded
   with 2048-token context, CPU only, 2–4 threads.
3. The header should then say `On-device · llama.cpp`. On a 4–6 GB phone use
   the 1.5B/0.5B model; 3B needs roughly 2.5–3.5 GB free RAM.

## Cloud fallback

In Brain: switch **Cloud fallback** on, pick Gemini or OpenRouter, and paste a key.
The key is stored only in the app's SharedPreferences. Local processing is always
preferred when a model is loaded and the cloud toggle is off.

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
.github/workflows/build.yml
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/com/brahma/mobile/...
app/src/main/res/...
app/src/main/assets/avatar/index.html
app/src/main/assets/avatar/three.min.js
push.sh
README.md
```

## Credits / licenses

App code written from scratch for this project. Third parties: Three.js (MIT),
llama.cpp (MIT, via `llama-android` MIT), OkHttp (Apache-2.0), AndroidX (Apache-2.0).
NASA APOD data is public-domain courtesy of NASA.
