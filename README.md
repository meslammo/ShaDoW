# SHADOW — Unified Personal AI Agent

SHADOW is a personal, unified Android AI assistant with one command surface. There is no visible Local Chat or Offline Chat mode.

## Runtime
- Online-first multi-provider AI routing: OpenAI -> xAI/Grok -> DeepSeek -> internal offline fallback.
- Agent loop: understand -> plan -> route -> execute -> observe -> verify -> continue.
- Tool registry for calculator, public web search/fetch, public GitHub reading, durable memory, workspace files, and Android actions.
- Android-native device execution with confirmation gates for sensitive actions.
- Embedded Python 12-Core governance runtime via Chaquopy.
- Durable non-secret memory with PostgreSQL preferred and file fallback.
- Original Jarvis-inspired AI voice delivery; biometric voiceprint is not required.
- GitHub development gateway with explicit authorization and allowlisted repository paths.
- Companion lifecycle and integration boundaries are retained for future watch/car/smart-home adapters.

## Offline behavior
Offline is an internal fallback, not a separate user-facing mode. Core local capabilities continue when online AI is unavailable, and online routing resumes automatically when a provider becomes reachable.

## Security
Secrets are not embedded in the APK. Durable memory rejects credential-like content. Web fetch blocks obvious private/local hosts. Mutating development actions require explicit approval and remain constrained by an allowlist.

## External dependencies
Online AI, cloud TTS and GitHub OAuth depend on external provider credentials/permissions. Their absence does not disable the internal Android command surface.

## Build
Android API 35, Java 17, Gradle 8.10.2, Android Gradle Plugin 8.6.1, Chaquopy 17.0.0, Python 3.11.

Current release candidate: `1.8.1-mod73-unified-runtime`

See `shadow/FINAL_SYSTEM_CONTRACT.md` for the capability and release contract.
