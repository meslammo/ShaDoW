# SHADOW MOD-119 — Complete Image Engine 1-7 + Brain Engine 8-23

Live runtime: Hatchable SHADOW Cloud 150 Core v54
- URL: https://shadow-cloud-150.hatchable.site
- API: https://shadow-cloud-150.hatchable.site/api
- Online-only brain; offline AI path remains removed.

## Image Engine 1-7
1. Self-hosted Vision adapter boundary: OCR/object/scene/reference semantics when SHADOW_LOCAL_VISION_URL is configured.
2. Self-hosted Renderer adapter boundary: SHADOW_LOCAL_IMAGE_RENDER_URL.
3. Self-hosted Editing adapter boundary: SHADOW_LOCAL_IMAGE_EDIT_URL; edit/outpaint/restyle/recolor fail closed without it.
4. Control layers: pose/depth/edge/segmentation/mask/layout/perspective, forwarded as structured adapter payloads.
5. Visual identity registry: reusable identities and deterministic identity fingerprints; full image-embedding identity becomes available through an authorized semantic adapter.
6. Visual comparison: exact artifact verification plus optional semantic compare.
7. Bounded generation/repair loop: render -> QA -> compare/semantic inspect -> repair passes.

## Brain Engine 8-23
8. Model router and capability-aware route.
9. Self-hosted brain adapter boundary: SHADOW_LOCAL_MODEL_URL.
10. Deep memory tables + deterministic embeddings + optional SHADOW_LOCAL_EMBEDDING_URL.
11. Task decomposition.
12. Governed tool planner / execution boundary.
13. Independent structural verification, with semantic verification available through vision adapter.
14. Bounded self-repair with sandbox manifest.
15. Incremental SSE streaming contract.
16. Multimodal input normalization.
17. Speech adapter boundary: STT/TTS/wake-word; VAD/barge-in remain client runtime concerns.
18. File intelligence classification and routing for PDF/DOCX/spreadsheets/images/audio/code/text.
19. Conversation session memory/context store.
20. User/project/task context fusion boundary.
21. Provider resilience: concurrency cap, retry, circuit-open behavior, provider health history.
22. Governance: Mohamed is principal; source mutations require explicit human approval.
23. Evolution sandbox: staging/test manifest; production source mutation is not automatic.

## Verification
The v54 live smoke tests returned HTTP 200 for runtime status, architecture, chat, image generation, file intelligence, governance, identity, and exact visual comparison. Streaming remains an SSE response surface; Hatchable's direct function tester does not expose the streamed body even when the endpoint is reachable.

## Model-dependent note
The following are adapter-ready, not falsely marked as active local models because no authorized endpoints are configured in the project:
- SHADOW_LOCAL_MODEL_URL
- SHADOW_LOCAL_VISION_URL
- SHADOW_LOCAL_IMAGE_RENDER_URL
- SHADOW_LOCAL_IMAGE_EDIT_URL
- SHADOW_LOCAL_EMBEDDING_URL

No secrets are stored in source.
