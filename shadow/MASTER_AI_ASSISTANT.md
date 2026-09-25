# SHADOW — MASTER AI ASSISTANT
## Complete Architecture & Development Map

> **Truth rule:** This document explicitly separates code that exists, code that has passed CI, behavior verified on a real device, design-only work, and blocked dependencies. No component is marked COMPLETE without evidence.

## 1. Vision

SHADOW is a personal AI assistant / orchestration layer for Mohamed. It is not only a chatbot. The target runtime is an execution system that can understand a request, authenticate authority, plan, apply governance, execute through a suitable tool, verify the real result, learn within boundaries, and recover safely.

Primary operating rule:
- **Online = primary path.**
- **Online-only AI:** SHADOW requires a reachable cloud AI provider for conversational intelligence; no offline AI mode or fallback is exposed.
- The current build is client-independent: Android is only a UI/transport surface; external device control is not part of Shadow.

## 2. Authority and Identity

Authority hierarchy:

**Mohamed → MASTER AUTHORITY → SHADOW**

Identity model:
1. Voice identity identifies the speaker.
2. Secret passphrase strengthens identity for protected operations.
3. Authorization maps identity to permissions.
4. Risk policy decides whether execution is automatic, confirmed, or blocked.
5. Execution is allowed only after the required gate passes.

Other people must have independent identities and permissions. They never inherit Mohamed's authority automatically.

## 3. The 12 Core

### Core 01 — Identity & Authority
Determines who is speaking and what they are allowed to do.
- Inputs: voice, passphrase, identity context, session state.
- Outputs: authenticated identity, authority level, permissions.
- Current: GitHub OAuth storage and online voice/passphrase identity gates exist; full biometric proof remains separate.

### Core 02 — Intent & Understanding
Transforms language into `Intent + Goal + Constraints + Expected Result`.
- Current: cloud chat understands language; deterministic client routing exists in Android.
- Gap: a single formal intent schema is not yet the universal runtime contract.

### Core 03 — Planning
Breaks a request into executable steps with dependencies, risks, and expected outcomes.
- Current: development planning endpoint and unified planning/agent loop exist.
- Gap: universal planner-to-executor loop is **PARTIALLY IMPLEMENTED**.

### Core 04 — Tool Management
Discovers, authenticates, permits, evaluates, promotes/demotes, and revokes software tools/capabilities.
- Current: governed software Skill/Tool Registry exists.
- Gap: full dynamic software registry is **PARTIALLY IMPLEMENTED**.

### Core 05 — Execution
Routes an intent to Cloud AI, web/software tools, GitHub, image generation, speech, memory, and governed software capabilities.
- Current: cloud APIs + GitHub authorization bridge + governed software tools exist.
- Critical requirement: Chat must never be treated as execution evidence.

### Core 06 — Verification
Confirms the real result rather than trusting an HTTP success response.
- Current: governance concepts and local verification messaging exist.
- Gap: universal evidence adapters are **PARTIALLY IMPLEMENTED**.

### Core 07 — Risk & Governance
Applies LOW / MEDIUM / HIGH / CRITICAL risk gates.
- HIGH: explicit confirmation.
- CRITICAL: explicit confirmation + strong authentication + safe execution.
- Current: governance runtime exists.

### Core 08 — Memory & Learning
Uses the four evidence levels:
`Fact / Evidence / Interpretation / Conclusion`.
- Sensitive data must not be learned automatically.
- Current: memory concepts exist in runtime; full persistent governed memory is **PARTIALLY IMPLEMENTED**.

### Core 09 — Recovery & Rollback
Uses checkpoints, journal entries, transactions and rollback plans.
- Current: checkpoint/journal concepts exist.
- Gap: universal rollback adapters are **PARTIALLY IMPLEMENTED**.

### Core 10 — Communication
Handles text, voice, status, errors, notifications and online provider transitions.
- Current: text default, optional voice response, cloud TTS and local TTS fallback exist.
- Custom uploaded voice: **NEEDS VERIFICATION / NOT YET PROVEN INTEGRATED**.

### Core 11 — Development Agent
Lets SHADOW inspect, plan, modify, test and propose changes to its repository under governance.
- Current: GitHub writer + OAuth bridge exist.
- Branch creation was hardened in MOD-51.1.
- End-to-end self-development: **NEEDS VERIFICATION**.

### Core 12 — Orchestration
Coordinates all cores and maintains a single lifecycle state.
- Current: Android routing and governance pieces exist.
- Gap: a universal orchestration bus is **PARTIALLY IMPLEMENTED**.

## 4. 17 Gap Mechanisms

1. Command Priority + Timestamp + Scope — prevents unsafe command replacement.
2. Intent Contract — preserves goal, constraints and expected result.
3. Gap Hunter — asks only when missing information changes correctness or safety.
4. Risk / Blast Radius — estimates impact before execution.
5. Rollback — records before/after state and recovery plan.
6. Actual Result Verification — requires evidence of the real outcome.
7. Error Taxonomy — Auth, Permission, Network, Device, Tool, Logic, Data, Timeout, Unknown.
8. Retry Budget — bounded retries, timeout and progress checks.
9. Time / Deadline Management — understands urgency and availability windows.
10. Provenance / Trust / Freshness — cross-checks conflicting information.
11. Tool Lifecycle — version, compatibility, health and rollback.
12. Companion Lifecycle — Discover → Authenticate → Trust → Permission → Use → Evaluate → Promote/Demote → Revoke.
13. Learning Boundaries — separates evidence from interpretation and prevents uncontrolled memory growth.
14. Recovery / Emergency / Shutdown — checkpoint, journal and safe shutdown.
15. Fine-grained Permissions — minimum required capability only.
16. Sandbox / Testing — new tools and changes must be tested before promotion.
17. Auditability — important actions must be traceable.

## 5. Runtime State Machine

`Idle → Listening → Authenticating → Understanding → Planning → Executing → Verifying → Learning → Done/Failed/Paused`

Interrupts pause the active operation at a checkpoint. Resume reloads the checkpoint. Failure selects a bounded recovery path. High-risk requests stop at the governance gate until explicit confirmation is present.

## 6. Online-Only Architecture\n\n```text\nCLIENT (Android/Web/Future UI)\n  ↓\nMASTER ROUTE\n  ↓\nCLOUD ONLINE AI / WEB / SOFTWARE TOOLS\n  ↓\nGOVERNANCE\n  ↓\nEXECUTION\n  ↓\nVERIFICATION\n  ↓\nMEMORY / AUDIT\n```\n\nWhen all configured online AI providers fail, SHADOW reports the online failure and does not switch to a local AI/offline conversation engine. Android is a client surface only. Shadow does not control the host phone or external hardware, and there is no offline AI mode.\n\n## 7. Voice

Supported user controls:
- `رد كتابة` / `من غير صوت` → text only.
- `رد صوتي` → voice + text.

Voice configuration:
- Android TextToSpeech is the default client response path to reduce cloud TTS usage.
- Cloud TTS (`gpt-4o-mini-tts`) remains available as an explicit backend path.
- Think Hard / Deep Think are reasoning controls; Deep Think requests `xhigh` reasoning when the OpenAI provider/model supports it.

Custom uploaded voice / biometric voice identity remains **NOT PROVEN COMPLETE**.

## 8. Client Architecture — Android is UI/Transport Only

Primary components:
- `JarvisMainActivity` — UI, state indicators, command routing, voice controls.
- `ShadowCore` — client-side runtime surface; no external device-control capability is active.
- `ShadowCloudClient` — Cloud API gateway.
- `ShadowGithubAuth` — encrypted GitHub token storage through Android Keystore.
- CI — release build and runtime checks.

Required routing rule:

`Understand → Classify → Route`

Examples:
- Chat → Cloud Chat.
- Software capability/action → governed Cloud/Tool execution.
- GitHub request → GitHub Authorization / Development Agent.
- Image → Image API.
- Voice → Speech API.

A GitHub intent must never fall through to Chat.

## MOD-75 — Master Route Integration

The Android Voice 2 surface is now the transport/UI boundary for the master route. Typed and spoken input share one routing contract before execution.

Current contract:

`Input → Identity → Understanding → Master Route → Planning → Route Handler → Verification → Response`

Implemented in the Android app:
- `ShadowMasterOrchestrator` classifies requests into Chat, GitHub/Development, Image/Vision, and System; device-like requests are not active routes.
- GitHub/development requests are isolated from normal Chat routing.
- Device-control requests are not part of the active Shadow route; the app remains a client surface.
- Voice and text enter the same request route.
- Android TTS is the default client response path; cloud TTS is not called for every answer by default.

Still required for full 12-Core completion:
- Universal cross-core event/event-bus state.
- Secure voice identity/voiceprint verification.
- Wake-word device accuracy validation can be performed later; it is not a build/routing prerequisite.
- Universal verification/evidence adapters.
- Universal rollback/recovery adapters.
- Device/Car/Smart-Home/Companion/Spatial integration is outside the active Shadow scope.

## 9. Cloud Backend

Repository: `meslammo/ShaDoW`

Backend: Node.js / Express

Railway service: `shadow-cloud-api`

Public domain: `shadow-cloud-api-production.up.railway.app`

Current configured model defaults:
- AI: `gpt-5-mini`
- Image: `gpt-image-1`
- TTS: `gpt-4o-mini-tts / onyx`

Endpoints:
- `/health` — service/configuration health.
- `/v1/chat` — online AI + web search.
- `/v1/development/plan` — safe development plan generation.
- `/v1/development/apply` — approved GitHub file application.
- `/v1/github/device/start` — GitHub Device Authorization start.
- `/v1/github/device/poll` — Device Authorization polling.
- `/v1/images` — image generation.
- `/v1/speech` — TTS.

## 10. Governance

Risk levels:
- LOW — safe automatic execution when policy allows.
- MEDIUM — contextual policy gate.
- HIGH — explicit confirmation.
- CRITICAL — explicit confirmation + strong authentication + isolated/safe execution.

Required supporting mechanisms:
- Journal
- Checkpoint
- Recovery
- Fail Closed

No execution is considered successful merely because an API returned 200.

## 11. Memory

```text
Fact → Evidence → Interpretation → Conclusion
```

Memory rules:
- Save facts only when there is evidence.
- Do not automatically retain sensitive information.
- Allow correction and deletion.
- Preserve provenance when possible.
- Never promote an unverified interpretation into a fact.

## 12. GitHub Development Agent

Target lifecycle:

`Understand → Search → Evaluate → Reuse → Integrate → Test → Execute → Verify → Commit/PR → Merge`

Safe implementation lifecycle:

```text
User Command
↓
Authenticate
↓
Understand
↓
Create isolated branch
↓
Modify allowlisted files
↓
Run tests
↓
Run CI
↓
Verify
↓
Create Pull Request
↓
Approval / Policy
↓
Merge
↓
Deploy
↓
Verify deployment
↓
Rollback if needed
```

MOD-51.1 adds automatic creation of a missing development branch from `main` before file writes.

Allowed prefixes:
- `app/`
- `backend/`
- `shadow/`
- `tests/`
- `.github/workflows/`

Blocked paths/terms include:
`.env`, `secrets`, `credentials`, `keystore`, `.pem`, `.key`.

Maximums:
- 20 files per apply request.
- 500,000 characters per file.

## 13. GitHub OAuth

Current implementation uses GitHub Device Authorization Flow:

`Phone → SHADOW → GitHub Authorization → OAuth Token → GitHub API`

The Android token is encrypted with Android Keystore.

The server requires `SHADOW_GITHUB_CLIENT_ID` to be configured. Without that variable, OAuth correctly fails closed as `github_oauth_not_configured` rather than routing to Chat.

Target proof:

`Phone → Authorization → Token → Repository Access → Branch → File Change → CI → PR`

End-to-end status: **NEEDS VERIFICATION / BLOCKED until OAuth Client ID is configured and the complete flow is tested.**

## 14. External Device/Companion Architecture — Deferred

Previous target categories (phone, watch, car, smart home, PC, TV, IoT, future devices) are not part of the current build.

Lifecycle:
`Discover → Authenticate → Trust → Permission → Use → Evaluate → Promote/Demote → Revoke`

Companion Registry stores identity, capabilities, health, trust and permissions. Capability Registry describes what each companion can actually do.

Status: **OUT OF CURRENT SCOPE**.

## 15. Web Discovery

Target flow:
`Search → Discover → Evaluate → Compare → Verify → Reuse → Integrate → Test`

New tools must pass:
`Discover → Sandbox → Security Scan → Test → Evaluate → Promote`

Full autonomous Tool Registry / promotion system: **PARTIALLY IMPLEMENTED**.

## 16. Recovery

Example:

```text
Shadow modifies code
↓
Checkpoint
↓
Change
↓
CI
↓
Failure
↓
Diagnose
↓
Bounded repair attempt
↓
Failure again
↓
Rollback
↓
Report
```

Infinite loops are prohibited by retry budgets and progress checks.

## 17. Error System

| Class | Detection | Recovery | Retry | User message |
|---|---|---|---|---|
| Auth | identity/token rejected | re-authenticate | limited | authorization required |
| Permission | action denied | request/adjust permission | limited | permission required |
| Network | connectivity failure | local fallback | bounded | online unavailable |
| Device | device action failed | re-check capability | bounded | device action failed |
| Tool | adapter failure | quarantine/retry | bounded | tool unavailable |
| Logic | invalid plan/state | re-plan | bounded | plan needs correction |
| Data | invalid/missing data | re-fetch/validate | bounded | data could not be verified |
| Timeout | deadline exceeded | checkpoint/retry | bounded | operation timed out |
| Unknown | unclassified | fail closed + log | minimal | operation failed safely |

## 18. Version Timeline

- MOD-48.6 / v1.5.0 — Online-first behavior, text/voice response preference. Offline fallback was later removed by MOD-76.
- MOD-48.7 / v1.5.1 — Cloud error hardening and TTS input limits.
- MOD-48.8.2 / v1.5.2 — Local phone execution bridge.
- MOD-50.9 / v1.5.4 — GitHub OAuth module and scope/status hardening.
- MOD-50.10 / v1.5.5 — OAuth test release; install conflict occurred because release debug signatures can differ.
- MOD-50.11 — Side-by-side installable GitHub routing test.
- MOD-51.1 — Development Agent branch creation hardening.
- MOD-51.2 — This master architecture map.

## 19. Current Status Matrix

| Component | Status | Evidence Needed | Next Step |
|---|---|---|---|
| Android UI | IMPLEMENTED | CI + device test | continue device validation |
| Online primary path | IMPLEMENTED | backend health + device test | monitor |
| Offline AI | REMOVED | N/A | online-only provider path |
| External device control | OUT OF SCOPE | N/A | no device integration in current build |
| Governance | IMPLEMENTED | runtime/CI tests | expand evidence adapters |
| Cloud Chat | IMPLEMENTED | Railway + API response | billing/credits must remain valid |
| Web search | IMPLEMENTED | live API test | verify fresh-source behavior |
| Image generation | IMPLEMENTED | live API test | verify credits |
| TTS | IMPLEMENTED | live API/device test | custom voice remains separate |
| GitHub OAuth code | IMPLEMENTED | backend configured + device | configure Client ID and test |
| GitHub routing | NEEDS VERIFICATION | device test with mixed Arabic/English command | prove no Chat fallback |
| GitHub token storage | IMPLEMENTED | Android Keystore test | verify upgrade path |
| Development branch creation | IMPLEMENTED | authenticated GitHub test | continue E2E |
| Development file writer | IMPLEMENTED | authenticated write test | add PR/CI orchestration |
| PR/CI/merge automation | PARTIALLY IMPLEMENTED | E2E test | implement controlled PR lifecycle |
| Voiceprint identity | NOT IMPLEMENTED | real biometric/voiceprint test | design secure verifier |
| Custom uploaded voice | NOT IMPLEMENTED / NEEDS VERIFICATION | real TTS integration | integrate only after provider support is verified |
| Companion / spatial control | OUT OF SCOPE | N/A | defer until after AI platform completion |
| Web Tool Discovery | PARTIALLY IMPLEMENTED | sandbox test | implement promotion policy |
| Universal rollback | PARTIALLY IMPLEMENTED | failure injection | build adapters |

## 20. Final Master Diagram

```text
MOHAMED
   ↓
MASTER AUTHORITY
   ↓
SHADOW
   ├── Identity
   ├── Understanding
   ├── Planning
   ├── Governance
   ├── Memory
   ├── Execution
   ├── Verification
   ├── Recovery
   ├── Communication
   ├── Development Agent
   └── Orchestration
          ↓
   ┌──────┼──────────┐
   ↓      ↓          ↓
 PHONE   CLOUD      GITHUB
   ↓      ↓          ↓
LOCAL   AI/WEB   DEVELOPMENT
RUNTIME     \       /
             \     /
            COMPANIONS
         ├── Watch
         ├── Car
         ├── Smart Home
         ├── PC
         ├── TV
         └── Future Devices

UNIVERSAL EXECUTION LOOP
UNDERSTAND
↓
AUTHENTICATE
↓
PLAN
↓
RISK CHECK
↓
EXECUTE
↓
VERIFY
↓
LEARN
↓
DONE

Cross-cutting:
CHECKPOINT • JOURNAL • RECOVERY • ROLLBACK • AUDIT
```

## 21. Roadmap to SHADOW 1.0

### Phase 1 — Fix GitHub Routing
Goal: prove every GitHub intent bypasses Chat.
Tests: Arabic, English, mixed Arabic/English, voice transcription.
Definition of Done: GitHub intent always enters GitHub route or explicit OAuth error; never Chat.

### Phase 2 — Complete GitHub OAuth
Goal: Device Authorization works from the phone.
Dependency: valid `SHADOW_GITHUB_CLIENT_ID`.
Definition of Done: token obtained and encrypted on device.

### Phase 3 — Development Agent E2E
Goal: authenticated file change from Shadow to GitHub.
Definition of Done: branch + allowlisted file change + confirmed commit.

### Phase 4 — CI + PR + Rollback
Goal: safe development lifecycle.
Definition of Done: change → CI → PR → controlled merge → deployment verification → rollback path.

### Phase 5 — Web Discovery
Goal: verified discovery and reuse of external resources.

### Phase 6 — Tool Registry
Goal: lifecycle management and promotion/demotion.

### Phase 7 — Software Capability Registry
Goal: governed software tools and skills.

### Phase 8 — Voice Identity + Passphrase
Goal: secure primary identity.

### Phase 9 — Custom Voice
Goal: integrate a supported custom voice without weakening identity security.

### Phase 10 — AI Reliability & Long-Running Tasks
Goal: robust governed long-running software tasks.

### Phase 11 — Advanced Memory + Learning
Goal: evidence-based, bounded personal learning.

### Phase 12 — SHADOW 1.0
Definition of Done: all critical flows pass device and cloud E2E tests with evidence, rollback and audit.

## 22. What SHADOW Is Today vs What SHADOW Must Become

**Today:** a real client + Cloud foundation with online-only AI, governance primitives, software tools, GitHub OAuth code, and a guarded Development Agent.

**Must become:** a unified personal AI operating/orchestration layer with proven identity, universal intent routing, verified execution, governed memory, dynamic software tools/skills, complete GitHub self-development, web discovery, recovery, automation, and multimodal intelligence. External device control is not required for this platform milestone. The user-facing assistant identity is SHADOW; legacy Jarvis-named implementation classes are internal compatibility code only.

## 23. Next Immediate Action

**Fix GitHub Routing → Complete OAuth → Prove End-to-End Self-Development.**

The immediate implementation must never hide a GitHub failure behind Chat. If OAuth is unavailable, SHADOW must say so explicitly and remain fail-closed. External device integration is not an implementation prerequisite.


## MOD-75.27 — Master Lifecycle Wiring

The Android master route now publishes lifecycle events to a bounded event bus. A lifecycle bridge fans those events into the existing governance journal, project/development memory, companion/device state boundaries, and the embedded Python runtime handoff.

### Reasoning UI
- **Normal:** existing dark SHADOW surface.
- **Think Hard:** black background + red interactive controls/effects; OpenAI reasoning effort `high`.
- **Deep Think:** black background + red interactive controls/effects; OpenAI reasoning effort `xhigh` when supported.
- **Stop Deep Think:** returns the UI to the normal surface and reasoning effort to `none`.

### Wake word
- `Hey Shadow` → `hey_shadow.onnx` is the sole wake phrase.
- Shared assets: `melspectrogram.onnx` + `embedding_model.onnx`.
- The build fetches the wake assets at pinned commits and prints SHA-256 hashes.
- Physical microphone validation is a later device test and does not block build, routing, or integration.


## Current Scope Decision — AI First

The current Shadow build is intentionally independent of external devices. Phone-control, car-control, smart-home control, wearable/companion control, and spatial-hardware integrations are removed from the active architecture so they cannot delay completion of the online AI platform. Android remains only a client surface for interaction, voice, images, status, and authentication.
