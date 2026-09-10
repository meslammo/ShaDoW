# SHADOW Gateway

Run with `uvicorn gateway.app:app --host 0.0.0.0 --port 8000` behind an HTTPS reverse proxy in production.

Set `SHADOW_GATEWAY_TOKEN` to require `Authorization: Bearer <token>`.

The Android client must use a stable HTTPS hostname in production. It must never depend on `localhost`, emulator-only `10.0.2.2`, or a hard-coded LAN IP.
