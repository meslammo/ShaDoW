"""MOD-35: local-first perception router.
The Android layer supplies audio/image bytes; providers can be plugged in later.
"""
from __future__ import annotations
from dataclasses import dataclass

@dataclass(frozen=True)
class PerceptionResult:
    kind: str
    text: str
    local: bool = True
    provider: str = "local-contract"

class LocalPerceptionRouter:
    def inspect_image(self, data: bytes, mime_type: str = "image/jpeg") -> PerceptionResult:
        if not data:
            return PerceptionResult("image", "No image data received.")
        return PerceptionResult("image", f"Image received ({mime_type}, {len(data)} bytes). Vision model adapter ready; no fabricated visual claims.")

    def inspect_audio(self, data: bytes, sample_rate: int = 16000) -> PerceptionResult:
        if not data:
            return PerceptionResult("audio", "No audio data received.")
        return PerceptionResult("audio", f"Audio received ({sample_rate} Hz, {len(data)} bytes). Route to on-device STT when available.")
