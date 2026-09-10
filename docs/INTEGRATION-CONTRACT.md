# Client integration contract

The Android app, desktop shell and future device clients must not contain model provider secrets.

Client sends a `ShadowRequest` with text, session ID, device ID and context. The authenticated transport returns `ShadowResponse` with text, confidence, verification state, proposed actions and confirmation requirement.

Voice flow: microphone -> STT -> ShadowRequest -> runtime -> TTS.
Vision flow: camera/screen frame -> vision provider -> context -> runtime.
Device flow: runtime proposes action -> permission gate -> device adapter -> verification -> response.

This keeps one SHADOW brain while allowing Android, Windows, home and car adapters to evolve independently.
