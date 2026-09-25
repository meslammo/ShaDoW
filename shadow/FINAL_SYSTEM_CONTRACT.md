# SHADOW — Unified System Contract

## Product behavior

SHADOW exposes one command surface. There is no Local Chat or Offline Chat mode. The runtime requires online AI for conversational intelligence, routes tools when useful, executes authorized device actions, and verifies results. When online AI is unavailable, it fails clearly instead of switching to an offline AI engine.

## Execution pipeline

`Listen/Input -> Understand -> Plan -> Route -> Authorize when required -> Tool/Device execution -> Observe -> Verify -> Continue -> Respond`

A provider failure (credits, 429, timeout, network, outage, invalid response) may move to the next configured online provider. Provider order is OpenAI -> xAI/Grok -> DeepSeek. If all online providers fail, return a clear online-unavailable error.

## Integrated tool surface

- arithmetic calculator
- public web search
- public web fetch with private-host blocking
- public GitHub repository/file reader
- durable non-secret memory search/save/forget
- SHADOW workspace file read/write with path constraints
- Android action requests and client continuation
- GitHub development write gateway with explicit approval and allowlisted paths
- Android-native camera/flashlight/app/device adapters already present in the mobile layer

## Identity and voice

Biometric voiceprint is not required by the product. The voice surface uses an original Jarvis-inspired AI delivery profile. Sensitive actions may still require explicit authorization/passphrase/session controls.

## Safety boundaries

Secrets, access tokens, private keys, password/passphrase material and credential-like text must not be stored in durable memory. Mutating operations require explicit authorization. Workspace file operations are confined to the SHADOW workspace. Web fetch rejects obvious private/local hosts.

## Persistence

PostgreSQL is the preferred memory store when `DATABASE_URL` is available. A local JSON storage fallback may be used for durable memory storage when PostgreSQL is unavailable; this is storage resilience, not an offline AI mode. Deployment infrastructure must still provide durable database storage for production-grade persistence.

## Honest capability state

The health endpoint reports configured providers and capability boundaries. A capability is not considered operational merely because a source file or adapter exists; provider credentials, external permissions, device permissions and successful runtime verification determine actual availability.

## Release gate

A release candidate is considered final only after backend syntax checks, Android release build, APK signature verification, artifact generation, and real-phone smoke testing. The CI artifact is a candidate until those checks are recorded.
