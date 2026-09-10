# SHADOW Cloud Backend — MOD-26.3

The Android app is cloud-first and never stores an OpenAI provider key. The backend owns the credential and proxies requests to the OpenAI Responses API.

## Deploy on Render

1. Create a Render Web Service from this GitHub repository.
2. Use the repository `render.yaml` blueprint, or Docker runtime with `backend/Dockerfile` and Docker context at repository root.
3. Set the secret environment variable `OPENAI_API_KEY` in Render. Do not commit it.
4. Keep `OPENAI_MODEL=gpt-5.6-luna` unless you intentionally choose another supported model.
5. Confirm `GET /health` returns `ok: true` and `online: true`.
6. The Android app is compiled with the expected backend URL `https://shadow-cloud-api.onrender.com`.

If Render assigns a different hostname, change `SHADOW_BACKEND_URL` in `app/build.gradle` and rebuild the APK.

## Security

The mobile APK contains no OpenAI secret. The server reads `OPENAI_API_KEY` from its environment only. Never paste the key into GitHub, the APK, or the Android settings screen.
