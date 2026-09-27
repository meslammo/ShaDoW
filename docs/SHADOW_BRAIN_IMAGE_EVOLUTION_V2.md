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
