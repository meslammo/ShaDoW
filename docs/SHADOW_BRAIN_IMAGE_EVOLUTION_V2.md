# SHADOW MOD-117 — Brain + Image Evolution v2

Backend runtime target: Hatchable SHADOW Cloud 150 Core v49.

The Brain Engine now exposes a versioned Capability Registry, context fusion, deterministic self-evaluation, versioned skill records, runtime knowledge observations, continuous improvement records, safe registry auto-activation, and rollback metadata.

The Image Engine now uses a Shadow-native Visual Director for prompt understanding and visual planning, optional versioned visual presets, reference-image metadata validation, deterministic artifact/contract QA, and a fail-closed self-hosted image-edit adapter contract.

Provider policy: named external model providers are not an architectural requirement. The current live compatibility runtime may use the existing LLM7 adapter when no self-hosted model endpoint is configured; configuring SHADOW_LOCAL_MODEL_URL promotes the self-hosted endpoint without changing the Brain contract.

Key live endpoints:
- GET /api/v1/brain/status
- GET /api/v1/brain/capabilities
- POST /api/v1/brain/knowledge
- GET /api/v1/images/status
- POST /api/v1/images
- POST /api/v1/images/edit

Versioned release: Brain v2.0 / Central Intelligence v2.0 / Visual Evolution v2.0 / Hatchable v49.

## Image Engine v3.1 — Images 2.6 integration (2026-11-06)

The previous v2 note described an adapter-ready visual director. The current branch adds an actual credential-gated OpenAI Images 2.6 API client, a restored image-engine compatibility module, generation and editing routes, GIF/SVG/markup controls, and deterministic format-aware artifact QA. CORE-100 and CORE-101 can call the configured SHADOW backend API; without `SHADOW_IMAGE_API_BASE_URL`, the 150-Core runtime reports an unavailable adapter rather than claiming success.

The API key stays server-side. GPT Image 2.6 is used only when an OpenAI key is configured; otherwise standard PNG generation may use the existing explicit fallback. Advanced formats do not silently downgrade to PNG.

