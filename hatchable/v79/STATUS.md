# SHADOW Hatchable v79 Source Snapshot

Project: SHADOW Cloud 150 Core
Hatchable project ID: proj_S2SFBUz6jXey
Hatchable live version: 79
Live URL: https://shadow-cloud-150.hatchable.site

## Runtime work shipped
- Advanced Image Engine no longer requires a self-hosted editor for edit/recolor/outpaint/restyle paths.
- Image QA now calls the real Semantic Vision provider chain: Hatchable AI -> LLM7 fallback -> self-hosted fallback.
- Visual comparison now attempts platform Semantic Vision before self-hosted/deterministic fallbacks.
- verified=true now requires actual Semantic Vision execution and semantic verification; artifact QA alone is not sufficient.
- Offline AI remains removed.

## Live verification
- Image generation: HTTP 200, renderer imagine-draw/generate-turbo, artifact QA passed.
- Semantic Vision: HTTP 200 route, but provider_setup_required because the Hatchable account/project has no Google/OpenAI/Anthropic credential.
- Image Editing: route reaches Hatchable ai.fetch and returns HTTP 412 from the gateway for missing Google credentials.
- Hatchable dry-run: 0 errors, 0 warnings.
- Deployment v79: live, 46 files, 26 deployed functions.

## Provider gate
The remaining gate is account-level provider setup in Hatchable Setup (Google, OpenAI, or Anthropic), or Builder + AI platform credit where available. No provider secret is stored in source.
