# SHADOW v0.42.0

Integrated Android release surface for the SHADOW runtime.

## What is in this release
- Local-first Android assistant with Arabic/English text input, STT and TTS.
- Real local phone action adapter plus safe calculator, time, memory and device tools.
- Gateway client for `/health` and `/v1/request` with bearer authentication.
- Runtime tool-calling loop with permission-gated execution and audit events.
- Persistent memory, device registry, autonomy/scheduler and controlled self-development layers.
- Dedicated **ASSISTANT**, **DEVICES**, **HOME**, **CAR** and **SETTINGS** tabs.
- Home and Car are modeled as first-class runtime domains now. Their real endpoints are deliberately not fabricated: until a real Smart Home gateway or vehicle API is supplied, the adapters report `READY / NOT CONNECTED / NOT CONFIGURED` and control remains fail-closed.

## Build

Android SDK API 35 + Java 17 + Gradle 8.10.2. The canonical Android project is `app/` and CI produces `SHADOW-v0.42.0-FINAL-debug-apk`.

## Security boundary

No model provider secret is embedded in the Android source. Unknown capabilities are denied by default. Sensitive actions require confirmation or an explicit automation grant. Adapter error payloads cannot be reported as successful actions.

## Release truth

This release is the complete SHADOW software boundary that can run without inventing credentials or pretending unavailable physical integrations exist. Home/Car become live by supplying their real adapter endpoint/device later; no brain or UI redesign is required.
