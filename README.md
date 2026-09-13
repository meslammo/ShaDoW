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
- Explicit skill registry for phone use, web discovery, development and design.
- **Site & Building Design:** measured land/site inputs, room-layout concepts, area calculations and minimal DXF geometry export; designed as an assistant/CAD adapter layer, not a structural-engineering approval.
- No SHADOW credits, points, usage meter or subscription gate.

## Provider policy
SHADOW is designed to work locally wherever the device/runtime supports it. Online AI, image and cloud voice providers are optional and may have their own external costs. Provider credentials are never embedded in the APK.

## Development Agent
The agent can inspect project files, ZIPs and source, maintain project memory, produce a plan, require approval, and verify work. GitHub write access remains explicitly user-authorized rather than silently embedded in the app.

## Design workflow
1. Add land dimensions, photos, floor plans or project files through the SHADOW attachment flow.
2. Extract measurements/metadata and build a design brief.
3. Generate room/layout concepts and calculate areas.
4. Export simple DXF geometry for CAD workflows.
5. Compare the design against uploaded project material before proposing changes.
6. Keep final structural/code-compliance decisions with qualified professionals and applicable local requirements.

## Build
Android API 35, Java 17, Gradle 8.10.2, Android Gradle Plugin 8.6.1, Chaquopy 17.0.0, Python 3.11.
