# SHADOW — Unified Personal AI Agent

SHADOW is a personal, unified Android AI assistant with one command surface. There is no visible Local Chat or Offline Chat mode.

## Runtime
- Online-only multi-provider AI routing with a free-first order: local/self-hosted -> Mistral -> Gemini -> DeepSeek -> OpenAI -> xAI -> Anthropic.
- One Android master-routing contract (MOD-75): understand -> classify -> route -> execute -> verify.
- Agent loop: understand -> plan -> route -> execute -> observe -> verify -> continue.
- Tool registry for calculator, public web search/fetch, public GitHub reading, durable memory, workspace files, and Android actions.
- Embedded Python 12-Core governance remains the root authority.
- Super Nice 150-Core now has a concrete handler for every registered Core; no Core is left in the old adapter_required dead-end state.
- Durable non-secret memory with PostgreSQL preferred and file fallback.
- ChatGPT-style voice interaction: voice and live text share the same conversation surface; Android TTS is the default client response path to avoid unnecessary cloud TTS calls.
- Cloud TTS remains available in the backend as an optional path.
- GitHub development gateway with explicit authorization and allowlisted repository paths.
- Companion, device and spatial capabilities are isolated as optional adapters.

## Single interaction path
All user input — typed or spoken — enters the same master route. Voice is a transport, not a separate AI mode:

Input -> Identity -> Understanding -> Master Route -> Planning -> Core Execution/Chat -> Verification -> Response

Sensitive actions remain governed by explicit identity/approval rules. There is no offline AI mode or offline AI fallback.

## Super Nice 150-Core completion
- CORE-001..012 remain backed by the original TwelveCoreRuntime.
- CORE-013..150 have executable deterministic builtins that validate contracts, prepare outputs, and report evidence without faking network/device/provider execution.
- Hardware/companion cores CORE-113..131 are disabled by default through SHADOW_ENABLE_DEVICE_CORES; they never block startup.
- Real web, GitHub, media, provider and hardware side effects remain behind their existing governed adapters.
- The runtime reports registered_handlers=150 and startup_blocking_integrations=[] when the optional hardware layer is disabled.
- Android exposes the 150-Core status and single-core execution bridge through ShadowPythonRuntimeBridge.

## Security
Secrets are not embedded in the APK. Durable memory rejects credential-like content. Web fetch blocks obvious private/local hosts. Mutating development actions require explicit approval and remain constrained by an allowlist. Builtin Core handlers are deterministic and do not perform external I/O.

## External dependencies
Online AI, cloud TTS and GitHub OAuth depend on external provider credentials/permissions. Their absence does not disable the internal Android command surface. Hardware integrations are optional and disabled by default.

## Build
Android API 35, Java 17, Gradle 8.10.2, Android Gradle Plugin 8.6.1, Chaquopy 17.0.0, Python 3.11.

Current Android build: 1.8.3-mod76-online-only.
Reasoning UI: Think Hard / Deep Think uses a black background with red controls/effects. Hey Shadow is the sole SHADOW wake phrase.

See shadow/MASTER_AI_ASSISTANT.md and docs/SUPER_NICE_150_CORE.md for the capability and release contracts.

## MOD-78 — Full 12-Step Master Pipeline
The Android and cloud surfaces now expose a unified 35-phase platform contract built on the existing 150-Core runtime. The execution loop preserves the 12-Core root and follows: Understand -> Route -> Memory -> Online Brain -> Governance -> Tools/Execution -> Verify -> Audit -> Recovery/Delivery.

- Android menu: **⚡ Full 35-Phase Master**.
- Platform status: **GET /v1/platform/status**.
- Python: `supernice_run(...)` / unified `Unified150Orchestrator`.
- Cloud: POST `/v1/master/run`.
- Cloud STT: POST `/v1/transcribe`.
- Cloud vision: POST `/v1/vision`.
- Provider/device side effects remain credential/permission gated and are never reported as successful without evidence.

## MOD-94 — Final E2E capability wiring
The current release routes requests through a task-aware multi-model gateway and keeps one SHADOW command surface.

Current recommended provider defaults:
- OpenAI: `gpt-5.6`
- xAI/Grok: `grok-4.6`
- DeepSeek: `deepseek-v4-pro`
- Mistral: `mistral-medium-latest`
- Anthropic: `claude-sonnet-5`
- Gemini: `gemini-3.8-flash`

Media adapters:
- STT: OpenAI cloud transcription with Gemini 3.8 audio-understanding fallback.
- TTS: OpenAI cloud TTS with Gemini TTS fallback; Android TTS remains the local playback fallback.
- Vision: image/camera -> cloud vision analysis.
- Images: OpenAI or Gemini image generation.
- Video: Gemini Veo 3.1 endpoint.
- Web: search + fetch + multi-source research tool.
- Memory: PostgreSQL-first durable memory with evidence/provenance metadata.
- Development: guarded GitHub apply -> PR -> CI verification pipeline.
- Phone: Accessibility control primitives for navigation, clicking, typing, long-press and swipe.

Provider credentials are still external deployment secrets. A missing provider is reported as unconfigured; SHADOW never pretends it ran an external operation.
