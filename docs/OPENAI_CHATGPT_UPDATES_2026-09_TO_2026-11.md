# OpenAI / ChatGPT capability update — September 1 to November 6, 2026

**Research cut-off:** 2026-11-06  
**Primary sources:** OpenAI ChatGPT Release Notes, OpenAI API Changelog, OpenAI Images 2.6 announcement, and the Image Generation API guide.

This document separates three things:
1. A feature announced in ChatGPT (product UI).
2. A capability exposed through an OpenAI API.
3. A SHADOW implementation that has actually been wired and tested.

A model name in this catalog does not grant API access, plan eligibility, region availability, trusted-access approval, or billing credits.

## Implemented in this SHADOW change

### GPT Image 2.6
- Fast generation model: `gpt-image-2.6-flare`.
- Precision / consistency model: `gpt-image-2.6-sunburst`.
- Image generation: `POST /v1/images`.
- Image editing: `POST /v1/images/edits`.
- Quality: low, medium, high, xhigh, max, auto.
- Complexity: low, medium, high.
- Raster formats: PNG, JPEG, WebP.
- SVG generation: static vector output, one result only; no SVG editing.
- Animated GIF generation/editing: 2–16 frames, 20–2000 ms per frame in 10 ms increments.
- Markup-guided image edits and optional alpha mask input.
- Transparent background is supported with PNG or WebP.
- Custom image dimensions are validated before sending; width/height must be multiples of 16 and satisfy published aspect-ratio and pixel limits.
- The SHADOW visual engine uses Images 2.6 when `OPENAI_API_KEY` is configured. It preserves the existing Gemini/self-hosted paths as explicit alternatives; it does not claim OpenAI generation when credentials are absent.

### SHADOW wiring
- `shared/openai-image-2-6.js`: shared request validation, generation/edit API client, error normalization.
- `hatchable/v79/api/lib/image-engine.js`: restored compatibility adapter imported by the existing visual engine; routes to OpenAI Images 2.6 when configured.
- `hatchable/v79/api/lib/image-engine-v3.js`: image engine v3.1 with quality, complexity, SVG/GIF, markup and edit controls.
- `hatchable/v79/api/v1/images.js` and `images/edit.js`: public image routes.
- `backend/server.mjs`: `POST /v1/images` and `POST /v1/images/edits`.
- `shadow/supernice/openai_capabilities.py`: versioned model and capability catalog.
- CORE-100 / CORE-101: route to the configured SHADOW image API through `SHADOW_IMAGE_API_BASE_URL`; no API URL means an honest adapter-unavailable response.
- Unified150Orchestrator routes image creation to CORE-100, edits to CORE-101, and image understanding to CORE-099.

## OpenAI model catalog in this release window

| Model | Publicly documented role | Access note |
|---|---|---|
| `gpt-6-astra` | Hardest end-to-end reasoning, coding, research, computer use | API model |
| `gpt-6-sol` | General reasoning and professional work | API model |
| `gpt-6-luna` | Lower-cost reasoning and Decisions | API model |
| `gpt-6.1-sol` | Coding, computer use and professional work | API model |
| `gpt-6.1-terra` | Visual reasoning, computer use and writing | API model |
| `gpt-image-2.6-flare` | Fast image generation and edits | API model |
| `gpt-image-2.6-sunburst` | Higher-precision image generation and edits | API model |
| `gpt-live-1` | Full-duplex realtime audio | API model |
| `gpt-6-rosalind` | Life-sciences reasoning and research tools | Trusted-access program |
| `gpt-6-cyber` | Authorized vulnerability investigation, safe reproduction and fix verification | Approved defenders only |

SHADOW's default OpenAI chat model is GPT-6.1 Sol; semantic vision defaults to GPT-6.1 Terra. GPT-6 Astra remains a selectable high-capability option. GPT-6 Cyber and GPT-6 Rosalind are catalogued as restricted and are not enabled by this code change.

## Release timeline and capability map

### September 2026
- **Sep 3 — GPT-6 Astra:** new high-capability reasoning model for complex work, coding, research, computer use and document creation; long-running tool calls and mid-turn steering were also introduced in the Responses API.
- **Sep 8 — Images 2.5:** ChatGPT gained Sketch, templates, image comments and shareable prompts. API introduced Flare and Sunburst, including xhigh/max quality. GPT-Rosalind research entered its trusted-access program.
- **Sep 9–10 — Voice and Agents:** ChatGPT Voice could use stronger reasoning models; GPT-Live-1 became generally available in the API; the Agents API public beta added durable sessions, streaming, context compaction, custom tools/MCP and hosted or self-hosted sandbox options.
- **Sep 10 — Connected data:** Data plugin and Box/Dropbox/SharePoint Library integrations expanded connected-file workflows and citations. API key creation governance and key expiration controls were added.
- **Sep 15 — API key governance:** organization/project-level controls can restrict who creates API keys; expiry and maximum lifetime controls are available.
- **Sep 17–23 — Work and learning:** ChatGPT for Word, multiple connected accounts, Voice with plugins, voice-to-Work handoff, GPT-6 Sol/Luna in Work/Codex, and interactive flashcards.
- **Sep 25 — Visual input fix:** an image-encoding issue affecting GPT-6 Sol/Luna image understanding was fixed; visual workflows should be re-evaluated after the correction.
- **Sep 25–29 — Security and agents:** account Security History, GPT-6.1 Sol, multi-agent delegation, Agents API computer use in an OpenAI-hosted browser, and Astra Ultrafast.
- **Sep 29 — Work automation:** Codex Cloud isolated task workspaces, Pages/Spaces collaboration, Dots persistent agents, Meetings notes, repository Code Review and Codex Security Cloud were announced/rolled out. These are product/plan-dependent surfaces; they are not all public API endpoints.

### October 2026
- **Oct 2–7 — Finance, audio and Intelligent UI:** Finances expanded to Free/Go in the US; audio uploads enabled transcription and summarization in supported paid workspaces; GPT-6 Intelligent UI combined text, visuals and interactive UI, with progressive answers.
- **Oct 8–9 — Performance and sandboxes:** Ultrafast for GPT-6.1 Sol; prewarmed hosted sandboxes; API Decisions beta with GPT-6 Luna.
- **Oct 15–16 — Voice, artifacts and slides:** GPT-Live-1.1 and Quip rolled out in ChatGPT Voice; native Decisions delegation in Live; prompt-to-voice and new voices in the API; hosted Agents sandboxes gained Artifact SDK document/spreadsheet/presentation skills; ChatGPT Slides launched to eligible workspaces.
- **Oct 20–22 — Privacy and structured decisions:** locked chats; 12-hour prompt caching; Decisions API generally available with GPT-6 Luna, structured inputs, images and up to 400 questions per request.
- **Oct 23–30 — Agent operations:** scheduled automations; GPT-6.1 Terra; iCloud Mail/Calendar connections; API credits for eligible plans; Dots texting beta; live tool updates, background compaction, opt-in time awareness and automatic review in the Agents API.

### November 2026
- **Nov 2 — GPT-Live SIP:** outbound SIP calling became available to API organizations.
- **Nov 4 — Images 2.6:** faster generation, more consistent edits, GIF creation in ChatGPT, reusable saved reference images, API complexity controls, SVG output and markup-guided edits.
- **Nov 5 — GPT-6 Cyber:** an approved-defender capability for authorized vulnerability research and fix validation. It is not a general-purpose public model entitlement.

## What SHADOW should build next from these releases

These are tracked as catalogued or design-target capabilities, not claimed as completed by the image upgrade:
1. Intelligent UI response schema: text, charts, diagrams, cards and safe interactive widgets.
2. Durable multi-agent sessions, explicit subagent delegation and traceable work.
3. Governed browser/computer-use adapter with per-domain approvals and visible user confirmation.
4. GPT-Live-1 realtime audio bridge, custom voice setup, SIP only where the user configures a provider.
5. Decisions-style structured question batches for fast classification and evaluation.
6. Scheduled automations with explicit scopes, pause/cancel, audit log and user-visible run history.
7. Artifact generation for DOCX/XLSX/PPTX/PDF with reviewable output.
8. Locked/private conversations, named reference images and user-controlled visual memory.
9. Security-history and continuous repository scanning with reviewable patches.
10. Connector permissions, per-source citations, and account separation.

## Safety and operational boundaries
- Do not imitate ChatGPT's proprietary UI or internal model implementation; build independent SHADOW features using documented APIs and original UI.
- Never bypass provider authentication, plan entitlements, billing, rate limits, safety systems or trusted-access restrictions.
- GPT-6 Cyber must not be enabled merely by adding its model ID. Keep SHADOW's defensive scanning and Docker sandbox separate until approved API access and authorized test boundaries are confirmed.
- The image endpoints return real artifacts only when the provider confirms success. Artifact validation is distinct from semantic vision verification.
- Missing API keys or service URLs must produce an explicit unavailable status, not a fake success or silent model substitution.
- Keep the 12-Core governance root and the 150-Core registry intact.

## Environment
- `OPENAI_API_KEY`: server-side secret, required for OpenAI API calls.
- `OPENAI_MODEL=gpt-6.1-sol`: default chat model.
- `SHADOW_VISION_MODEL=gpt-6.1-terra`: default visual reasoning model.
- `OPENAI_IMAGE_MODEL=gpt-image-2.6-flare`: default fast image model; choose `gpt-image-2.6-sunburst` for precision.
- `SHADOW_IMAGE_API_BASE_URL`: optional SHADOW backend URL used by CORE-100/101.
- `SHADOW_IMAGE_API_TOKEN`: optional bearer token for a protected SHADOW image API gateway.

## Official sources
- ChatGPT release notes: https://help.openai.com/en/articles/6825453-chatgpt-release-notes
- OpenAI API changelog: https://developers.openai.com/api/docs/changelog
- Images 2.6 announcement: https://openai.com/index/introducing-images-2-6/
- Image generation API guide: https://developers.openai.com/api/docs/guides/image-generation
