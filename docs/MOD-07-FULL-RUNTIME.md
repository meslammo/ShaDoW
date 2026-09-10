# MOD-07 — Full Runtime Foundation

Implemented on `feature/shadow-full-runtime-mod07`.

## Runtime
- One client-facing service boundary.
- Observe/understand/plan/permission/execute/verify/learn pipeline contract.
- Provider-independent orchestration boundary.

## Capabilities
- Voice/STT/TTS contracts.
- Vision contract.
- Phone/device/home/car adapter contract.
- Tool registry with confirmation and fail-closed unknown tools.
- Capability matrix covering chat, search, files, voice, vision, screen, phone, notifications, location, devices, home, car, automation, memory and self-development.

## Security
- No provider secrets in Android client.
- Dangerous capabilities default to confirmation.
- Unknown tools are rejected.

## Next integration layers
1. Authenticated HTTPS gateway/WebSocket transport.
2. Android voice/camera/screen adapters.
3. Persistent memory and device registry wiring.
4. Real provider routing and verification.
5. Automation/self-development execution with rollback.
