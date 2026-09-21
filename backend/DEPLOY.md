# SHADOW Cloud Backend — MOD-26.3

The Android app is cloud-first and never stores provider keys. The backend owns the credential and proxies requests to the OpenAI Responses API.

## Deploy on Render

1. Create a Render Web Service from this GitHub repository.
2. Use the repository `render.yaml` blueprint, or Docker runtime with `backend/Dockerfile` and Docker context at repository root.
3. Set the secret environment variable `OPENAI_API_KEY` in Render. Do not commit it.
4. Recommended model defaults for the current stack are: OpenAI `gpt-5.6`, xAI `grok-4.6`, DeepSeek `deepseek-v4-pro`, Mistral `mistral-medium-latest`, Anthropic `claude-sonnet-5`, Gemini `gemini-3.8-flash`.
5. Optional media models: Gemini `gemini-3.1-flash-image` (images), `veo-3.1-generate-preview` (video), `gemini-3.5-transcribe` (specialized transcription), `gemini-3.1-flash-tts-preview` (TTS fallback).
6. Confirm `GET /health` returns `ok: true` and `online: true`.
7. The Android app is compiled with the expected backend URL `https://shadow-cloud-api-production.up.railway.app`.

If Render assigns a different hostname, change `SHADOW_BACKEND_URL` in `app/build.gradle` and rebuild the APK.

## Security

The mobile APK contains no OpenAI secret. The server reads `OPENAI_API_KEY` from its environment only. Never paste the key into GitHub, the APK, or the Android settings screen.

<!-- MOD-26.4: CI trigger after cloud wiring -->
