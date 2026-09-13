# SHADOW MOD-35 — Unified Free-First Stack

This change consolidates the agreed architecture into one provider-neutral stack.

## Included foundations
- Development Agent: project memory, planning, approval, file/ZIP/code understanding.
- Phone-use: Accessibility perception and semantic actions from MOD-33.
- Analysis: deterministic E.V.-style numerical analysis.
- Network: online/offline state and Starlink-as-Wi-Fi handling from MOD-34.
- Memory: persistent Android project memory plus Python memory modules.
- AI: existing provider-neutral orchestrator plus new local AI adapter contract.
- Voice: Android on-device recognition when available and local TTS fallback; cloud voice remains optional.
- Vision: provider-neutral image/audio perception routing without fabricated results.
- Skills: keep tools explicit, inspectable and confirmation-gated.
- GitHub: access must be user-authorized; never embed credentials in the APK.

## Free policy
SHADOW itself has no credits, points, usage meter or subscription gate. Local functions remain available without an online provider. Online model/image/voice services are optional external providers and may have their own costs.

## Security
No password cracking, credential guessing or unauthorized network access is part of SHADOW. Sensitive device actions remain confirmation-gated.

## Next runtime integration
The remaining provider-specific work is deliberately behind these contracts: an on-device LLM runtime/model, stronger local STT/TTS engines, multimodal vision/OCR, and the user-authorized GitHub bridge. These can be added without changing the chat UI or core tool policy.
