# MOD-126 — SHADOW Full-System Audit & Capability Integration Plan

**Status:** Phase 0 repository audit started; implementation and full test suite are not yet complete.  
**Base:** `main` at `3f330452f63d269dfd56516007c205a2dda1a83c`  
**Working branch:** `mod126-full-audit-chat-updates-voice-image-20261023`  
**Scope:** conversation behavior, current AI capability updates, 12-Core → 150-Core integration, tools, image generation/editing, optional voice playback, regression/security tests.  
**Invariant:** do not rewrite the app, remove existing capabilities, or claim external execution without a verifiable receipt.

## 1. Evidence observed during initial repository inspection

- The repository default branch is `main`; the inspected head is `3f330452f63d269dfd56516007c205a2dda1a83c` (MOD-125 snapshot).
- `README.md` describes the online-only Android assistant, one command surface, the original TwelveCoreRuntime root, a 150-Core catalog, cloud AI providers, image/video/audio adapters, and an optional Android TTS response path.
- `docs/SHADOW_150_CORE_AUDIT_2026-09-25.md` explicitly reports that all 150 IDs have handlers/contracts but 0/150 had been proven by a real end-to-end production path on the audited base; the production cloud path was `backend/ai-router.mjs` + `backend/tool-registry.mjs`, not `SuperNiceMasterPipeline` as its authoritative loop. Re-check this claim against current code and CI before changing status.
- `tests/test_supernice_150_core.py` includes exact-count/uniqueness, per-Core handler smoke, optional device-Core behavior, TwelveCoreRuntime root preservation, and a 150-Core self-test. Passing contract tests must not be labeled production E2E.
- `app/src/main/java/com/shadow/mobile/ChatGPTStyleMainActivity.java` already has a persisted `voice_replies_enabled` preference defaulting to false, a settings/menu toggle, a manual read-aloud action, and an automatic `speak(reply.answer)` call guarded by that preference. Verify every response path, lifecycle behavior, and user-visible setting before changing it.
- The Android activity routes image requests to `cloud.generateImage(text)`; the complete image flow, returned artifact handling, provider configuration, editing, and test coverage still require a dedicated audit. Existing docs mention provider-gated vision/image editing and must be compared to the current backend before any claim of availability.
- Existing CI workflow `.github/workflows/verify-runtime.yml` runs Python compileall and pytest. This alone does not prove Android APK build, live provider credentials, external image generation, or device-level voice behavior.

## 2. Required behavior contract

### Conversation and intent routing
- Greetings and ordinary conversation must receive a direct, natural reply; no visible internal plan/checklist for a simple greeting.
- Distinguish chat, factual question, current-news/update request, image creation/editing, code/development action, tool request, and high-impact operation.
- Tool execution must be explicit, permission-governed, observable, and followed by verification.
- Keep internal orchestration traces out of normal user-facing responses; expose diagnostics only in an explicit diagnostics view or when requested.
- Maintain one shared conversation surface for typed and spoken input.

### Updates / research
- Support user-triggered updates across AI/models, SHADOW project status, technology, and cybersecurity.
- Return at most 3–5 relevant items, with source URL, publication/event date where available, retrieval time, practical impact, and uncertainty status.
- Deduplicate, reject stale/unverified claims, and treat fetched content as untrusted data, never as instructions.
- Scheduled monitoring and alerts are out of scope until a real scheduler, delivery channel, failure handling, and delivery tests exist.

### Voice
- Default response speech remains OFF.
- User can enable/disable automatic speech in settings; preference persists across launches.
- Keep manual “read aloud” available even when automatic speech is off.
- Ensure all response types (normal text, tool results, image result captions/errors, streamed/final response) respect the same preference; avoid double-speaking, speaking internal traces, or reading long raw JSON.
- Allow user-selectable supported voice profile/language and speech rate where the platform/provider genuinely supports it; show unavailable voice/language accurately.
- Test interruption/stop, activity lifecycle, TTS initialization failure, empty response, and fallback behavior.

### Image generation and editing
- Audit request detection, prompt validation, provider selection, credential readiness, request timeout/size limits, content policy checks, binary/base64 decoding, image display, saving/sharing, error handling, and result provenance.
- Distinguish generation from image analysis and image editing; route each intent to the correct tool.
- Never report an image was created unless a real artifact was returned and rendered/saved successfully.
- Add the complete source/config/test assets actually required by the repository; never commit API keys, sample secrets, generated binaries, or fake provider success.
- Verify configured providers with authorized credentials in CI/staging. Without credentials, test explicit `provider_setup_required` behavior and mark live provider E2E as blocked, not passed.

## 3. Audit matrix (must be populated with evidence)

For every Core CORE-001..CORE-150, record:
- catalog registration and unique ID;
- handler contract and actual dispatch target;
- direct unit/contract test;
- production routing path (or explicit not-integrated state);
- required adapter/provider and readiness state;
- authorization/approval behavior;
- success/failure/timeout evidence;
- cross-layer E2E status;
- regression risk and next action.

For every layer/tool, record the same evidence-based statuses:
- Android UI and conversation state;
- Android/cloud request contracts;
- identity/session and permissions;
- online model routing/provider health;
- intent classification and orchestrator;
- memory save/recall/retention/deletion/provenance;
- web search/fetch and source validation;
- GitHub/development gateway and approval;
- image generation, vision, and image editing;
- STT, optional TTS/read-aloud, voice profile/preferences;
- file/workspace operations;
- governance, audit log, recovery, and response delivery;
- build, CI, deployment, and live smoke tests.

Use only these status labels: **PASS-UNIT**, **PASS-INTEGRATION**, **PASS-E2E**, **BLOCKED-CONFIG**, **FAIL**, **NOT-TESTED**, **OUT-OF-SCOPE**. A test result must link to its command/log/artifact or commit. A Core contract smoke test is not production E2E evidence.

## 4. Execution sequence

1. **Baseline and safety:** confirm branch/head, inspect open PRs and workflows, record clean baseline, preserve main and the existing 12-Core root.
2. **Static map:** inventory Core registry, dispatch, backend production route, tools, Android UI, provider adapters, image and voice paths, config/env variables, tests.
3. **Reproduce greeting failure:** trace typed greeting from Android/cloud request through intent/router/provider to final UI. Add a regression test that a greeting yields a concise natural reply and does not expose an execution plan.
4. **Core-by-Core audit:** execute existing contract tests, map all 150 IDs to real dispatch, then separately test production-connected cores. Do not mass-generate placeholder implementations.
5. **Tool audit:** schema validation, auth/approval, timeout, unsafe URLs/paths, malformed arguments, tool errors, retries/limits, evidence receipts, and no false success.
6. **Image audit and repair:** inspect actual generation/editing code and all required assets; add missing source/config/tests; run deterministic tests and provider-backed staging tests only when credentials are authorized.
7. **Voice audit and repair:** preserve speech OFF by default; verify persisted toggle, manual playback, all reply paths, interruption and TTS fallback; fix only evidence-backed gaps.
8. **Updates feature:** implement on-demand source-backed update retrieval after confirming available search adapters; include freshness, citations, deduplication, 3–5 item cap, and injection resistance.
9. **Cross-layer verification:** Python tests, Node/backend tests, Android unit tests, Android Gradle build/APK, CI, then authorized staging E2E. Track unrun tests explicitly.
10. **Review and delivery:** commit changes on this branch, report exact SHA, changed files, test commands/results, blockers, and create a draft PR for review. Do not merge or deploy production without separate explicit approval.

## 5. Acceptance gates

- Greeting regression passes and ordinary conversation no longer displays internal plans.
- 150/150 Core contracts are enumerated and tested; production E2E is counted separately per actual path.
- No Core is called production-ready based solely on a deterministic builtin handler.
- Tool execution is permission-gated and never reports success without an execution receipt.
- Image generation/editing has correct routing, real artifact verification, explicit provider-not-configured behavior, and tests.
- Automatic speech is OFF by default and user-controlled; manual read-aloud remains available.
- Update results cite sources and distinguish published facts from analysis.
- Existing Android/API contracts and 12-Core root remain compatible.
- Python, Node, Android, and CI results are reported separately; no fabricated green status.
- No secrets or credentials are committed.
- Production deployment is not performed as part of an unreviewed code change.

## 6. Current status

This document records the initial audit plan and the evidence observed from GitHub. It does **not** claim that implementation, all 150 production paths, image-provider E2E, or Android voice behavior have been fully tested. The next work item is the source-level baseline map and the greeting/voice/image regression coverage, followed by targeted code changes.
