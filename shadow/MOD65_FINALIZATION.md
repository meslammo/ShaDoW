# SHADOW MOD-65 — Finalization / Hardware + Provider Boundary

## Implemented

- Verified Android flashlight ON/OFF execution through `CameraManager.setTorchMode` with real device-state/error handling.
- Kept online-first / offline-fallback behavior.
- Kept governed local execution and sensitive-action approval gates.
- Kept discovery, integration, companion lifecycle, rollback journal, and final runtime diagnostics.
- Android version: `1.7.1-mod65`.

## Explicit non-fake boundaries

### True voiceprint
The app does **not** treat SpeechRecognizer text as biometric identity. A real speaker-embedding provider is still required for biometric verification. The adapter fails closed until that provider is configured.

### Cloud AI
The cloud gateway is ready, but actual online AI requires a working provider configuration/credits on Railway. If unavailable, Shadow falls back to the deterministic offline path.

### Custom Shadow voice
The TTS gateway supports a custom voice ID, but a real custom voice cannot be claimed active without a valid provider-side voice configuration and eligible voice setup.

### Hardware companions
The companion registry is ready for authenticated adapters. Actual watch/car/smart-home control requires the corresponding device protocol/API credentials and adapter implementation.

## Goal of MOD-65

Close the remaining software-side execution gap without fabricating capabilities that depend on external provider credentials, biometric models, or physical hardware.
