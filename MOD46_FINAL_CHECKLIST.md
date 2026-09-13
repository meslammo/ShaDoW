# MOD-46 Final Integration Checklist

Preserve and reuse existing SHADOW foundations.

## Integrated on this branch
- Workspace execution boundary with path containment, explicit approval, file hashing, and inspection.
- Recovery state contract for checkpoints and rollback reporting.
- Server-side GitHub bridge boundary; credentials remain outside Android/project files.
- Final integration gate documenting capability-gated hardware work.

## Must be CI/device verified before final release
- Wire executor into DevelopmentEngine and Android DevelopmentAgent.
- Connect server GitHub transport and authenticated branch/commit/PR operations.
- Wire recovery into failing test/build paths and verify rollback.
- Connect independent web discovery provider and evidence flow.
- End-to-end Android agent bridge.
- Live process indicators wired to real operations.
- Full automated test suite and production APK build.
- Physical device verification for voice, permissions, phone control, radar, and hardware-dependent features.

Nothing in this checklist is considered complete merely because a file exists; completion requires evidence from tests/builds/device validation.
