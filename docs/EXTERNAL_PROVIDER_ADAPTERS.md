# SHADOW — Real External Capability Adapters

## Providers wired in code

The backend now has credential-gated adapters for OpenAI, xAI/Grok, DeepSeek, Mistral, Anthropic, Gemini, plus a self-hosted OpenAI-compatible endpoint.

Mistral documents chat completions at `POST /v1/chat/completions`; Gemini documents `generateContent` with tools and multimodal inputs; DeepSeek documents `/chat/completions` with function tools. citeturn182185search2turn182185search0turn197144search0

## Routing

With `SHADOW_FREE_FIRST=true`:
`Local -> Mistral -> Gemini -> DeepSeek -> OpenAI -> xAI -> Anthropic`

With `SHADOW_FREE_FIRST=false`, commercial providers are tried first.

The local route has no commercial API key requirement; it becomes operational only when a reachable OpenAI-compatible inference endpoint is configured.

## Tool execution

Mistral, Anthropic and Gemini enter the same SHADOW tool-execution loop. Tool execution remains behind the existing governed tool registry and device approval path. Provider adapters never receive SHADOW secrets.


## OpenAI models and Images 2.6 (2026-11-06)

The current documented defaults are GPT-6.1 Sol for general chat and GPT-6.1 Terra for semantic vision. GPT-6 Astra remains available for the hardest reasoning workflows. The model/access catalog is maintained in `shadow/supernice/openai_capabilities.py`.

Image generation/editing is implemented through `shared/openai-image-2-6.js`:
- Fast generation: `gpt-image-2.6-flare`.
- Precision generation/editing: `gpt-image-2.6-sunburst`.
- Backend routes: `POST /v1/images` and `POST /v1/images/edits`.
- Supports quality/complexity, raster formats, SVG generation, GIF animation and markup-guided edits.
- `OPENAI_IMAGE_MODEL` chooses the default image model. Keep the key server-side.

The model catalog records GPT-6 Cyber as approved-defenders-only and GPT-6 Rosalind as trusted-access-only. A catalog entry is not an entitlement and does not activate either restricted model.

## Reality boundary

A provider is **live** only after its legitimate credential or endpoint exists and a real request succeeds. A code adapter alone is not a live integration.

External providers keep their own authentication, rate limits, availability and pricing. SHADOW does not bypass them.

## Configuration

Use `backend/.env.example` as the variable template. API key values must remain in deployment secrets, not Git.

## Verification

The repository validates syntax and core/runtime behavior in CI. A provider-specific smoke test should only run when that provider is intentionally configured in the deployment environment.
