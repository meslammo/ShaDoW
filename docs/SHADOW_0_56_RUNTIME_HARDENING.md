# SHADOW 0.56 Runtime Hardening

This release keeps the existing architecture and adds four concrete hardening points:

1. Android creates the private Shadow workspace before every embedded-Python entry point.
2. The Android identity surface is now a password-free owner session. Risky actions can still use normal execution confirmation; no passphrase prompt is used.
3. Chat and streaming no longer perform a health request before every message. The real request is attempted first, reducing avoidable latency and eliminating the false reconnect transition.
4. ShadowEngine persists turn/session state and LiveUpdateManager stages, validates, activates, and rolls back safe Python-side updates without resetting the active conversation.

## 150-Core model

The 150-Core catalog remains the runtime surface. CORE-001..012 continue to map to the existing TwelveCoreRuntime. CORE-013..150 remain available through shadow.supernice.runtime.CoreRuntime and are routed by Unified150Orchestrator.

A Core is available when it has a concrete contract handler. Real provider operations such as image generation additionally require a configured provider credential and live provider/device E2E evidence; deterministic handlers must not claim that external work occurred.

## Image generation

The Android UI already calls /v1/images. The backend supports OpenAI, Gemini, and Pollinations image providers. This build keeps that real provider path; it does not embed API keys in the APK.

## Online-only rule

The conversational brain remains cloud/online-only. Android TTS may remain a local playback fallback because that does not create an offline conversational brain.