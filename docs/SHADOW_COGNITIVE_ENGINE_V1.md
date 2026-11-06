# SHADOW Cognitive Engine v1 — implementation status

## Goal
Let a user communicate in natural language without having to author prompts. SHADOW should interpret the conversation, prepare internal instructions, select governed tools, execute, verify, and report honestly.

## Implemented in this slice
- Added `shadow.supernice.cognitive.CognitiveEngine`.
- Added a provider-neutral `shadow.cognitive-request.v1` context contract.
- Normalizes whitespace and records a language hint (Arabic, English, mixed, unknown).
- Applies conservative intent labels (question, action, creation, modification, research, conversation).
- Extracts a small set of explicit constraints (no merge, no publish, free only, preserve existing).
- Injects the contract, typed `shadow.task-plan.v1` schema, and planning/safety instructions into the existing `Unified150Orchestrator` context. The configured online model receives this context.
- Android chat now renders the server pipeline trace as visible execution stages, including completed/pending/failed states.
- Android displays an explicit approval card for a returned pending action. Approval continues the existing agent call; rejection does not send an approval signal.
- Added focused pytest coverage in `tests/test_cognitive_engine.py`.

## Important limitations
- The local intent label is heuristic metadata, not model-level understanding and not a source of truth.
- This slice does not yet replace the legacy keyword-based `select_core` routing.
- It does not yet create a typed multi-step plan, ask clarifying questions through the Android UI, or expose plan progress.
- Tool execution still uses the existing governed registry and its current confirmation rules.
- It is online-only: no local/offline model fallback is introduced.
- No live provider call, Android build, APK, or production deployment is claimed by this change.

## Next implementation gates
1. Return and validate model-produced per-task plan objects through the API.
2. Add model-assisted intent and tool routing with a safe fallback when structured output is invalid.
3. Add clarification states and progress refresh to the Android conversation surface.
4. Expand the tool registry with capability metadata and risk tiers.
5. Add bounded observe/retry/verify cycles with evidence and stop conditions.
6. Add project-aware memory retrieval with provenance and explicit memory controls.
7. Run unit, integration, real-provider, Android, and multi-device tests before release review.

## Safety invariants
- Treat user content and retrieved content as data, not instructions that override system policy.
- Never execute a proposed action solely because it appears in generated text.
- Require explicit approval for destructive, external, financial, publishing, and repository-mutating actions.
- Never report completion without execution evidence.
- Keep PR #51 and PR #52 unmerged; do not modify `main`.
