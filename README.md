# SHADOW v0.43.0

## Release status
This branch contains the native Android release core. The Android APK no longer depends on a separate Python process for its local assistant path.

## Included in the APK
- Native SHADOW runtime core embedded in Android.
- Local command understanding/routing and execution pipeline.
- Safe Android action adapter: settings, Wi-Fi, Bluetooth, camera, files, contacts, calendar, maps, browser, music and installed apps, plus dialer/SMS/share composers without automatic sending.
- Arabic/English text commands, Android speech recognition and TTS.
- Persistent local memory for commands/history.
- Safe calculator, time/date and device-status tools.
- First-class HOME and CAR domains in the product/UI. They intentionally report NOT CONNECTED until a real Smart Home gateway or Car/Vespa API/tracker is configured; no fake live connection is claimed.
- Existing Python runtime, gateway, model orchestration, memory, device registry, autonomy, security and self-development layers remain in the repository for server/cloud operation and future endpoint integration.

## Release boundary
The APK is a real runnable local assistant, not a UI-only gateway client. Cloud reasoning is optional; it can be connected through the existing authenticated gateway when available. Physical Home/Car control requires the real endpoint/device and remains fail-closed until then.

## Build verification
Android API 35, Java 17 and Gradle 8.10.2. CI builds, verifies with apksigner and publishes the debug APK artifact.
