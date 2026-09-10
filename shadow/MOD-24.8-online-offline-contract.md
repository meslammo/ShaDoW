# MOD-24.8 — SHADOW online/offline runtime contract

- Online mode: user-configured OpenAI Responses API credentials are encrypted at rest with Android Keystore and loaded only into the live Python process.
- Offline mode: native Android actions, local memory, local conversation fallback, speech recognition, and Android TTS remain available without network access.
- Failover: an online provider/network failure returns through the deterministic local-safe path instead of crashing the app.
- Security: provider credentials are not committed to the repository or baked into the APK.
- Tool execution remains permission/confirmation gated by the existing runtime boundary.
