"""MOD-35: local-first voice contract.
Android's on-device SpeechRecognizer/TTS remain the first implementation.
Optional open local engines can implement these contracts later without changing UI.
"""
from __future__ import annotations

class LocalVoice:
    def __init__(self, language: str = "ar-EG"):
        self.language = language

    def status(self) -> dict[str, object]:
        return {"language": self.language, "stt": "android-on-device-when-available", "tts": "android-local-when-available", "cloud": "optional"}
