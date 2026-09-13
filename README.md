# SHADOW MOD-35 — Unified Free-First Runtime

## Current architecture
- Native Android phone/device action layer with Accessibility-based phone-use.
- Embedded Python runtime via Chaquopy.
- Persistent project memory and safe file/ZIP/code understanding.
- Deterministic E.V.-style analysis engine.
- Online/offline orchestration with local-safe fallback.
- Local-first voice and perception contracts; Android on-device speech is used when available.
- Starlink-aware connectivity: Starlink is treated as the upstream Internet connection delivered by Wi-Fi.
- Confirmation-gated sensitive actions and fail-closed security.
- No SHADOW credits, points, usage meter or subscription gate.

## Provider policy
SHADOW is designed to work locally wherever the device/runtime supports it. Online AI, image and cloud voice providers are optional and may have their own external costs. Provider credentials are never embedded in the APK.

## Development Agent
The agent can inspect project files, ZIPs and source, maintain project memory, produce a plan, require approval, and verify work. GitHub write access remains explicitly user-authorized rather than silently embedded in the app.

## Build
Android API 35, Java 17, Gradle 8.10.2, Android Gradle Plugin 8.6.1, Chaquopy 17.0.0, Python 3.11.
