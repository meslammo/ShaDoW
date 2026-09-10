# SHADOW v0.50.0 — Embedded Runtime Release

## What is actually inside the APK
- Native Android action layer for safe phone/device actions.
- The repository's Python SHADOW runtime is packaged into the APK with Chaquopy and runs in-process; no localhost server, separate Python process, or LAN address is required.
- Runtime pipeline: observe → understand → plan → permission check → execute → verify → learn.
- Python memory, goals/tasks, decision/verification, audit, device registry, Home/Car domains, tool registry, autonomy and self-development layers are packaged with the app.
- Arabic/English Android speech recognition and TTS.
- Persistent local memory for Android commands and the Python runtime's memory store.
- Home and Car are first-class domains in the product and runtime. They report READY / NOT CONNECTED until a real endpoint is configured; the APK does not fake a live device connection.
- Cloud model providers remain optional. Provider secrets are not embedded in the APK.

## Runtime boundary
The Android app is now a unified runtime host: native Android capabilities execute locally, while the actual Python SHADOW brain/runtime executes inside the same application process. The previous client-only boundary is removed.

## Build
Android API 35, Java 17, Gradle 8.10.2, Android Gradle Plugin 8.6.1, Chaquopy 17.0.0, Python 3.11.

CI also compiles the Python tree, builds the APK from clean state, verifies the APK signature, calculates SHA-256, and checks that the embedded Python/Chaquopy payload is present before publishing the artifact.

## Security
Dangerous runtime tools remain fail-closed and confirmation-gated. No API/provider secret is committed into the Android source.
