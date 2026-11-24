# Roadmap

What exists today is the skeleton plus a working local brain, reflex layer, overlay
avatar, and APOD briefing. Here is what is planned, roughly in priority order.

## Near term
- **Proper wake-word model** (microWakeWord / porcupine-style TFLite) instead of relying
  on the platform recognizer's `EXTRA_PREFER_OFFLINE`, so "brahma" works on any ROM.
- **Streaming answers** from llama.cpp (token-by-token into the bubble) and a Kokoro
  ONNX TTS backend behind `TtsEngine`.
- **Avatar states tied to behavior**: thinking while the model runs, happy on success,
  surprised on errors, plus a compact "mini" mode for one-handed use.
- **Chat history + multi-turn memory** in a small SQLite store.
- **Home-screen widget** for quick commands.

## Medium term
- Per-app reflex profiles ("when I say 'meeting', silence notifications and open Calendar").
- On-device embedding search over your own notes/files.
- Watch/Wear companion surface.
- Local RAG over exported chat transcripts.

## Long term / exploratory
- Gem/ multimodal input (camera frame -> description) with a VLM GGUF.
- Peer-to-peer hand-off between devices.
- Fully local "skills" DSL so users can write their own reflexes without code.

## Not planned
- Cloud-first operation. The offline path is the point; cloud stays an explicit fallback.
- Non-arm64 builds. The app targets phones only.
