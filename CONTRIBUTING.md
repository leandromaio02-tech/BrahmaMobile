# Contributing to Brahma Mobile

Thanks for stopping by. This is a from-scratch Android app: Kotlin + Jetpack Compose,
`minSdk 26`, `arm64-v8a` only, and the whole build runs in GitHub Actions — no PC needed.

## Quick start

```sh
git clone https://github.com/leandromaio02-tech/BrahmaMobile.git
cd BrahmaMobile
```

Open it in Android Studio, or build headless (Gradle 8.7, JDK 17):

```sh
gradle assembleDebug --no-daemon
# -> app/build/outputs/apk/debug/app-debug.apk
```

Every push runs `.github/workflows/build.yml` and publishes the APK as the
`brahma-mobile-debug` artifact.

## Where things live

| Area | Path |
|---|---|
| Avatar (Three.js/WebView) | `app/src/main/assets/avatar/index.html` |
| Floating overlay service | `app/src/main/java/com/brahma/mobile/overlay/` |
| Brain (llama.cpp + cloud + rules) | `app/src/main/java/com/brahma/mobile/brain/` |
| Wake word / STT / barge-in / TTS | `app/src/main/java/com/brahma/mobile/voice/` |
| <50 ms regex reflexes | `app/src/main/java/com/brahma/mobile/reflex/ReflexEngine.kt` |
| Agent tools | `app/src/main/java/com/brahma/mobile/tools/` |
| Settings UI | `app/src/main/java/com/brahma/mobile/ui/MainScreen.kt` |
| NASA APOD wallpaper | `app/src/main/java/com/brahma/mobile/wallpaper/` |
| Browser playground | `docs/index.html` |

## Guidelines

- **Keep the reflex path fast.** Anything that touches the network or a model belongs
  behind `Brain`/`ToolRegistry`, never inside `ReflexEngine.tryHandle`.
- **Stay low-RAM.** The target is 4–6 GB phones. Avoid extra dependencies; the model
  loads at 2048 context with 2–4 threads on CPU only.
- **No secrets in the repo.** API keys are entered at runtime and stored in
  SharedPreferences.
- Add a short note in your PR about what you verified. If you can, run the real build
  (`gradle assembleDebug`) rather than assuming it compiles.

## Reporting bugs

Use the bug-report template. Include phone model, Android version, whether the offline
model is loaded, and the exact voice command or UI action. Screen recordings help a lot.

## Ideas

Check `ROADMAP.md` before starting something big, and open a discussion first if it
changes the architecture (new engine, new voice stack, new UI shell).
