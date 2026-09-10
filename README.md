# SHADOW v0.39 — Phase 13 Android APK Client

Native Android client for the Phase 13 gateway transport. The phone holds only the gateway URL/token and sends requests to `/health` and `/v1/request`. It does not receive tool credentials or execute SHADOW tools locally.

## Build
Open `mobile/android` in Android Studio or run `gradle assembleDebug` with Android SDK API 35 installed.

## Phone test
If the gateway runs on the same phone use `http://127.0.0.1:8787`. If it runs on a PC on the same Wi-Fi use the PC LAN address, e.g. `http://192.168.1.10:8787`.

## CI
GitHub Actions workflow: `.github/workflows/build-apk.yml`. Run it manually and download the generated APK artifact.
