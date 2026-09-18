# SHADOW — Unified Personal AI Agent

SHADOW is a personal, unified Android AI assistant with one command surface. There is no visible Local Chat or Offline Chat mode.

## Runtime
- Online-first multi-provider AI routing: OpenAI -> xAI/Grok -> DeepSeek -> internal fallback.
- One Android master-routing contract (MOD-75): understand -> classify -> route -> execute -> verify.
- Agent loop: understand -> plan -> route -> execute -> observe -> verify -> continue.
- Tool registry for calculator, public web search/fetch, public GitHub reading, durable memory, workspace files, and Android actions.
- Android-native device execution with confirmation gates for sensitive actions.
- Embedded Python 12-Core governance runtime via Chaquopy.
- Durable non-secret memory with PostgreSQL preferred and file fallback.
- ChatGPT-style voice interaction: voice and live text share the same conversation surface; Android TTS is the default client response path to avoid unnecessary cloud TTS calls.
- Cloud TTS remains available in the backend as an optional path.
- GitHub development gateway with explicit authorization and allowlisted repository paths.
- Companion lifecycle and integration boundaries are retained for future watch/car/smart-home adapters.

## Single interaction path
All user input — typed or spoken — enters the same master route. Voice is a transport, not a separate AI mode:

`Input -> Identity -> Understanding -> Master Route -> Planning -> Execution/Chat -> Verification -> Response`

Sensitive actions remain governed by explicit identity/approval rules. Offline behavior remains an internal fallback only.

## Offline behavior
Offline is an internal fallback, not a separate user-facing mode. Core local capabilities continue when online AI is unavailable, and online routing resumes automatically when a provider becomes reachable.

## Security
Secrets are not embedded in the APK. Durable memory rejects credential-like content. Web fetch blocks obvious private/local hosts. Mutating development actions require explicit approval and remain constrained by an allowlist.

## External dependencies
Online AI, cloud TTS and GitHub OAuth depend on external provider credentials/permissions. Their absence does not disable the internal Android command surface.

## Build
Android API 35, Java 17, Gradle 8.10.2, Android Gradle Plugin 8.6.1, Chaquopy 17.0.0, Python 3.11.

See `shadow/MASTER_AI_ASSISTANT.md` for the capability and release contract.
