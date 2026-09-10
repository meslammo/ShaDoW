# SHADOW Build Roadmap

## v0.42.0 — integrated runtime release candidate
- [x] MOD-01 Audit and mapping
- [x] MOD-02 Repository reorganization
- [x] MOD-03 Core foundation
- [x] MOD-04 Brain and reasoning foundation
- [x] MOD-05 AI orchestration + provider routing
- [x] MOD-06 Android client foundation
- [x] MOD-07 Unified runtime/capability contracts
- [x] MOD-08 Persistent memory + retrieval wiring
- [x] MOD-09 Authenticated HTTPS/WebSocket gateway
- [x] MOD-10 Android voice + local phone actions
- [x] MOD-11 Device registry + Home/Car domain boundaries
- [x] MOD-12 Autonomy + scheduler + controlled self-development
- [x] MOD-13 Production audit + rollback + fail-closed permissions
- [x] MOD-16 Concrete action execution + model tool-calling
- [x] MOD-17 Final integrated Android surface with Assistant / Devices / Home / Car / Settings tabs

## Intentionally deferred external integrations
- Smart Home vendor/LAN gateway credentials and live device discovery.
- Car/Vespa vendor API, tracker credentials and vehicle-specific controls.
- These are adapter endpoints, not missing product architecture. When the real device/API exists, it plugs into the existing Home/Car domain boundary without redesigning the brain.

A checkbox means the SHADOW code boundary is implemented and covered by the repository verification suite; an external integration is only marked live when its real endpoint/device is connected and verified.
