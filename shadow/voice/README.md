# SHADOW Voice — MOD-57

The production voice path is server-authoritative. The Android client continues using `/v1/speech`; no OpenAI key is shipped in the APK.

## Voice modes

- Built-in fallback: `SHADOW_TTS_VOICE` (default `onyx`)
- Custom voice: `SHADOW_TTS_VOICE_ID=voice_...`
- Model: `SHADOW_TTS_MODEL=gpt-4o-mini-tts`
- Style: `SHADOW_TTS_INSTRUCTIONS`

When `SHADOW_TTS_VOICE_ID` is configured, the speech request sends the custom voice object `{ id: voice_... }`. Otherwise it uses the built-in voice.

OpenAI custom voices require an eligible account plus an audio sample and a consent recording. The sample/consent are intentionally not stored in Git or the Android app. The repository only carries the runtime contract. This keeps secrets and private audio out of source control.

The `/health` response exposes only whether a custom voice ID is configured, never the ID itself.
