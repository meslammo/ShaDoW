# SHADOW Super Nice 150-Core

## Completion status

All 150 Core IDs are registered and now have a runtime handler. CORE-001..012 remain backed by the original TwelveCoreRuntime; the rest use deterministic builtin handlers unless a specialized adapter is explicitly supplied.

Deterministic builtins are deliberately honest: they validate the core contract, prepare a result envelope, and emit evidence. They do not pretend that an external model, network service, GitHub mutation, media job, or hardware action happened.

## Optional hardware policy

CORE-113..131 are treated as optional hardware/companion boundaries. They are disabled by default and never participate in application startup requirements.

Environment switch:

SHADOW_ENABLE_DEVICE_CORES=true

Without that switch, those cores return disabled_optional with startup_blocking=false. The Android app can run its online AI, memory, research, software and governance layers without a watch, car, smart-home hub, Bluetooth device, NFC target, or location integration.

## Runtime invariants

- Exactly 150 unique Core IDs: CORE-001 through CORE-150.
- A missing Core ID fails closed as unknown_core.
- Sensitive cores retain confirmation gates.
- Handler exceptions are converted to handler_error results instead of crashing the whole runtime.
- No builtin handler performs external I/O.
- No device integration is required for startup.

## Android integration

android_runtime.py exposes:

- supernice_status()
- supernice_execute(core_id, request, ...)

ShadowPythonRuntimeBridge exposes the matching status/execution entry points. The existing master routing surface remains compatible.

## Real execution boundary

Real execution is still performed only by the governed adapters: provider adapters for online AI, web/GitHub gateways for external resources, media providers for image/video/audio, and explicit device adapters for hardware. Those integrations are optional inputs to the 150-Core layer rather than startup dependencies.

## Verification

The dedicated test suite checks the 150-Core count, one handler per core, optional hardware non-blocking behavior, and preservation of the TwelveCoreRuntime root.